import java.util.*;
import java.util.zip.*;
import java.lang.Math.*;
import java.math.*;

//version 1.0

public class DeltaMapper
{
	public static int[] getDifference(int src1[], int src2[])
	{
		int length = src1.length;
		if(src2.length != length)
			throw new IllegalArgumentException("getDifference: src1.length (" + length + ") != src2.length (" + src2.length + ")");

		int[] difference = new int[length];
		for(int i = 0; i < length; i++)
			difference[i] = src1[i] - src2[i];
		return(difference);
	}

	public static int[] getSum(int src1[], int src2[])
	{
		int length = src1.length;
		if(src2.length != length)
			throw new IllegalArgumentException("getSum: src1.length (" + length + ") != src2.length (" + src2.length + ")");

		int[] sum = new int[length];
		for(int i = 0; i < length; i++)
			sum[i] = src1[i] + src2[i];
		return(sum);
	}

	public static int[] shift(int src[], int shift)
	{
		int length = src.length;
		int[] shifted_value = new int[length];

		if(shift < 0)
		{
			for(int i = 0; i < src.length; i++)
				shifted_value[i] = src[i] >> -shift;
		}
		else
		{
			for(int i = 0; i < src.length; i++)
				shifted_value[i] = src[i] << shift;
		}

		return(shifted_value);
	}

	public static int[] getPixel(int[] blue, int[] green, int[] red, int xdim, int pixel_shift)
	{
		int ydim = blue.length / xdim;
		int[] pixel = new int[blue.length];

		int blue_shift  = pixel_shift + 16;
		int green_shift = pixel_shift + 8;
		int red_shift   = pixel_shift;

		int k = 0;
		for(int i = 0; i < ydim; i++)
		{
			for(int j = 0; j < xdim; j++)
			{
				pixel[k] = (blue[k] << blue_shift) + (green[k] << green_shift) + (red[k] << red_shift);
				k++;
			}
		}
		return pixel;
	}

	// -------------------------------------------------------------------------
	// Unified frequency estimator for delta types 0-4.
	//   0 = horizontal   (left neighbour)
	//   1 = vertical     (above neighbour)
	//   2 = average      (left + above) / 2
	//   3 = MED          (median-edge detector)
	//   4 = directional  (four-way edge-directed)
	// All types sample the same interior region: rows 1..ydim-1, cols 1..xdim-2,
	// matching the coverage of the individual frequency methods they replace.
	// -------------------------------------------------------------------------
	public static int[] getFrequency(int[] src, int xdim, int ydim, int delta_type)
	{
		ArrayList<Integer> delta_list = new ArrayList<Integer>();

		for (int i = 1; i < ydim; i++)
		{
			int k = i * xdim + 1;
			for (int j = 1; j < xdim - 1; j++)
			{
				int delta;

				if (delta_type == 0)
				{
					delta = src[k] - src[k - 1];
				}
				else if (delta_type == 1)
				{
					delta = src[k] - src[k - xdim];
				}
				else if (delta_type == 2)
				{
					delta = src[k] - (src[k - 1] + src[k - xdim]) / 2;
				}
				else if (delta_type == 3)
				{
					int a = src[k - 1];
					int b = src[k - xdim];
					int c = src[k - xdim - 1];

					int pred;
					if (c >= Math.max(a, b))
						pred = Math.min(a, b);
					else if (c <= Math.min(a, b))
						pred = Math.max(a, b);
					else
						pred = a + b - c;

					delta = src[k] - pred;
				}
				else // delta_type == 4
				{
					int a = src[k - 1];
					int b = src[k - xdim];
					int c = src[k - xdim - 1];
					int d = src[k - xdim + 1];

					int h_edge  = Math.abs(b - c) + Math.abs(b - d);
					int v_edge  = Math.abs(a - c) + Math.abs(a - b);
					int dl_edge = Math.abs(c - d);
					int dr_edge = Math.abs(a - d);

					int pred;
					if (h_edge >= v_edge && h_edge >= dl_edge && h_edge >= dr_edge)
						pred = b;
					else if (v_edge >= dl_edge && v_edge >= dr_edge)
						pred = a;
					else if (dl_edge >= dr_edge)
						pred = d;
					else
						pred = c;

					delta = src[k] - pred;
				}

				delta_list.add(delta);
				k++;
			}
		}

		int delta_min = Integer.MAX_VALUE;
		int delta_max = Integer.MIN_VALUE;
		int size = delta_list.size();
		for (int i = 0; i < size; i++)
		{
			int current_delta = delta_list.get(i);
			if (current_delta < delta_min) delta_min = current_delta;
			if (current_delta > delta_max) delta_max = current_delta;
		}

		int range = delta_max - delta_min;
		int[] frequency = new int[range + 1];
		for (int i = 0; i < size; i++)
		{
			int current_value = delta_list.get(i);
			current_value -= delta_min;
			frequency[current_value]++;
		}

		return frequency;
	}

	// =========================================================================
	// Compact Huffman map encoder / decoder.
	//
	// Encodes a byte[] map whose values are in 0..n_sym-1 (n_sym must be a
	// power of 2: 4, 8, or 16) using canonical Huffman coding.
	//
	// Output format:
	//   header : n_sym code lengths, packed as 4 bits each (2 per byte,
	//            low nibble first), total (n_sym/2) bytes.
	//   data   : MSB-first Huffman bit stream, zero-padded to a full byte.
	//
	// The caller stores bit_count_out[0] (total payload bits) alongside the
	// encoded array so the decoder knows where the bit stream ends.
	// =========================================================================
	public static byte[] encodeMapHuffman(byte[] map, int n_sym, int[] bit_count_out)
	{
		// Count symbol frequencies.
		int[] freq = new int[n_sym];
		for (byte b : map) freq[b & 0xFF]++;

		// Build Huffman code lengths.
		int[] len = huffmanLengths(freq, n_sym);

		// Find max code length.
		int max_len = 0;
		for (int l : len) if (l > max_len) max_len = l;

		// Assign canonical codes.
		int[] codes = huffmanCodes(len, n_sym, max_len);

		// Count total payload bits.
		int total_bits = 0;
		for (int s = 0; s < n_sym; s++) total_bits += freq[s] * len[s];
		bit_count_out[0] = total_bits;

		// Build output: header nibbles + bit stream.
		int    header = n_sym / 2;
		byte[] out    = new byte[header + (total_bits + 7) / 8];

		// Write code lengths as 4-bit nibbles (low nibble first).
		for (int s = 0; s < n_sym; s++)
			out[s >> 1] |= (len[s] & 0xF) << ((s & 1) << 2);

		// Write Huffman bit stream (MSB first within each byte).
		int bit_pos = 0;
		for (byte b : map)
		{
			int s    = b & 0xFF;
			int code = codes[s];
			int clen = len[s];
			for (int bit = clen - 1; bit >= 0; bit--)
			{
				if (((code >> bit) & 1) == 1)
					out[header + (bit_pos >> 3)] |= 1 << (7 - (bit_pos & 7));
				bit_pos++;
			}
		}

		return out;
	}

	public static byte[] decodeMapHuffman(byte[] encoded, int n_sym, int map_length, int bit_count)
	{
		int header = n_sym / 2;

		// Read code lengths from nibbles.
		int[] len = new int[n_sym];
		for (int s = 0; s < n_sym; s++)
			len[s] = (encoded[s >> 1] >> ((s & 1) << 2)) & 0xF;

		// Find max code length and rebuild canonical codes.
		int max_len = 0;
		for (int l : len) if (l > max_len) max_len = l;
		int[] codes = huffmanCodes(len, n_sym, max_len);

		// Decode bit stream.
		byte[] map    = new byte[map_length];
		int    bit_pos = 0;

		for (int q = 0; q < map_length; q++)
		{
			int acc = 0, acc_len = 0;
			outer:
			while (true)
			{
				int byte_idx = header + (bit_pos >> 3);
				acc     = (acc << 1) | ((encoded[byte_idx] >> (7 - (bit_pos & 7))) & 1);
				acc_len++;
				bit_pos++;
				for (int s = 0; s < n_sym; s++)
					if (len[s] == acc_len && codes[s] == acc)
						{ map[q] = (byte) s; break outer; }
			}
		}

		return map;
	}

	// Build Huffman code lengths from a frequency array via a greedy tree.
	private static int[] huffmanLengths(int[] freq, int n_sym)
	{
		int[] len  = new int[n_sym];
		int   used = 0;
		for (int f : freq) if (f > 0) used++;

		if (used <= 1)
		{
			for (int s = 0; s < n_sym; s++) if (freq[s] > 0) { len[s] = 1; break; }
			return len;
		}

		// Nodes 0..n_sym-1 are leaves; n_sym.. are internal.
		long[]    node_freq = new long[2 * n_sym];
		int[]     parent    = new int  [2 * n_sym];
		boolean[] active    = new boolean[2 * n_sym];

		for (int s = 0; s < n_sym; s++)
		{
			node_freq[s] = freq[s];
			active[s]    = freq[s] > 0;
			parent[s]    = -1;
		}

		int next = n_sym;
		while (true)
		{
			int m1 = -1, m2 = -1;
			for (int n = 0; n < next; n++)
			{
				if (!active[n]) continue;
				if (m1 == -1 || node_freq[n] < node_freq[m1]) { m2 = m1; m1 = n; }
				else if (m2 == -1 || node_freq[n] < node_freq[m2]) m2 = n;
			}
			if (m2 == -1) break;

			node_freq[next] = node_freq[m1] + node_freq[m2];
			parent[m1]  = next; active[m1]   = false;
			parent[m2]  = next; active[m2]   = false;
			active[next] = true; parent[next] = -1;
			next++;
		}

		int root = next - 1;
		for (int s = 0; s < n_sym; s++)
		{
			if (freq[s] == 0) continue;
			int depth = 0, node = s;
			while (node != root) { depth++; node = parent[node]; }
			len[s] = depth;
		}
		return len;
	}

	// Assign canonical Huffman codes from code lengths.
	private static int[] huffmanCodes(int[] len, int n_sym, int max_len)
	{
		if (max_len == 0) return new int[n_sym];

		// Count symbols at each length.
		int[] bl_count = new int[max_len + 1];
		for (int s = 0; s < n_sym; s++) bl_count[len[s]]++;
		bl_count[0] = 0;

		// First code at each length (standard canonical assignment).
		int[] next_code = new int[max_len + 2];
		int   code      = 0;
		for (int bits = 1; bits <= max_len; bits++)
		{
			code = (code + bl_count[bits - 1]) << 1;
			next_code[bits] = code;
		}

		// Assign codes to symbols in order (canonical: sorted by symbol index).
		int[] codes = new int[n_sym];
		for (int s = 0; s < n_sym; s++)
			if (len[s] > 0) codes[s] = next_code[len[s]]++;

		return codes;
	}

	public static int[] getIdealFrequency(int src[], int xdim, int ydim)
	{
		ArrayList<Integer> delta_list = new ArrayList<Integer>();

		for(int i = 1; i < ydim; i++)
		{
			int k = i * xdim + 1;
			for(int j = 1; j < xdim - 1; j++)
			{
				int a = src[k - 1];
				int b = src[k - xdim];
				int c = src[k - xdim - 1];
				int d = src[k - xdim + 1];
				int e = src[k];

				int delta_a = Math.abs(a - e);
				int delta_b = Math.abs(b - e);
				int delta_c = Math.abs(c - e);
				int delta_d = Math.abs(d - e);

				int delta;
				if(delta_a <= delta_b && delta_a <= delta_c && delta_a <= delta_d)
					delta = a - e;
				else if(delta_b <= delta_c && delta_b <= delta_d)
					delta = b - e;
				else if(delta_c <= delta_d)
					delta = c - e;
				else
					delta = d - e;
				delta_list.add(delta);

				k++;
			}
		}

		int delta_min = Integer.MAX_VALUE;
		int delta_max = Integer.MIN_VALUE;
		int size = delta_list.size();
		for(int i = 0; i < size; i++)
		{
			int current_delta = delta_list.get(i);
			if(current_delta < delta_min) delta_min = current_delta;
			if(current_delta > delta_max) delta_max = current_delta;
		}

		int range = delta_max - delta_min;
		int[] frequency = new int[range + 1];
		for(int i = 0; i < size; i++)
		{
			int current_value = delta_list.get(i);
			current_value -= delta_min;
			frequency[current_value]++;
		}

		return frequency;
	}

	// Same result as getIdealFrequency (identical histogram, element for
	// element), but counts directly into an int array in a single pass
	// instead of first collecting every delta in an ArrayList<Integer>.
	// Every delta is a difference of two values in src, so it lies within
	// +/-(src max - src min); the histogram is sized for that range up
	// front, then trimmed to the deltas that actually occur.
	public static int[] getIdealFrequency2(int src[], int xdim, int ydim)
	{
		// No interior pixels (xdim < 3 or ydim < 2): match what
		// getIdealFrequency returns in that case.
		if(xdim < 3 || ydim < 2)
			return new int[2];

		int src_min = src[0], src_max = src[0];
		for(int v : src)
		{
			if(v < src_min) src_min = v;
			if(v > src_max) src_max = v;
		}
		int span = src_max - src_min;

		int[] count = new int[2 * span + 1];
		for(int i = 1; i < ydim; i++)
		{
			int k = i * xdim + 1;
			for(int j = 1; j < xdim - 1; j++)
			{
				count[idealDelta(src, k, xdim) + span]++;
				k++;
			}
		}

		int lo = 0, hi = count.length - 1;
		while(count[lo] == 0) lo++;
		while(count[hi] == 0) hi--;
		return java.util.Arrays.copyOfRange(count, lo, hi + 1);
	}

	// The per-pixel delta used by getIdealFrequency/getIdealFrequency2: the
	// difference from whichever causal neighbour (left, above, above-left,
	// above-right) is closest, with ties broken in that order.
	private static int idealDelta(int src[], int k, int xdim)
	{
		int a = src[k - 1];
		int b = src[k - xdim];
		int c = src[k - xdim - 1];
		int d = src[k - xdim + 1];
		int e = src[k];

		int delta_a = Math.abs(a - e);
		int delta_b = Math.abs(b - e);
		int delta_c = Math.abs(c - e);
		int delta_d = Math.abs(d - e);

		if(delta_a <= delta_b && delta_a <= delta_c && delta_a <= delta_d)
			return a - e;
		else if(delta_b <= delta_c && delta_b <= delta_d)
			return b - e;
		else if(delta_c <= delta_d)
			return c - e;
		else
			return d - e;
	}

	// Frequency estimate for getIdealDeltasFromValues8: for each interior
	// pixel, the delta from the closest of the 8 predictors. Returns
	// [delta_frequency, map_frequency (8 entries, one per predictor)].
	public static ArrayList<int[]> getIdealFrequency8(int src[], int xdim, int ydim)
	{
		ArrayList<Integer> delta_list = new ArrayList<Integer>();
		int[] map_freq = new int[8];

		for (int i = 1; i < ydim; i++)
		{
			for (int j = 1; j < xdim - 1; j++)
			{
				int k = i * xdim + j;
				int a = src[k - 1];
				int b = src[k - xdim];
				int c = src[k - xdim - 1];
				int d = src[k - xdim + 1];
				int e = src[k];

				int[] pred = {
					a,           //  0: left
					(a + c) / 2, //  1: avg(left, above-left)
					c,           //  2: above-left
					(c + b) / 2, //  3: avg(above-left, above)
					b,           //  4: above
					(b + d) / 2, //  5: avg(above, above-right)
					d,           //  6: above-right
					(d + a) / 2  //  7: avg(above-right, left)
				};

				int best_abs = Integer.MAX_VALUE, best_idx = 0, best_delta = 0;
				for (int n = 0; n < 8; n++)
				{
					int delta     = e - pred[n];
					int abs_delta = Math.abs(delta);
					if (abs_delta < best_abs) { best_abs = abs_delta; best_idx = n; best_delta = delta; }
				}
				delta_list.add(best_delta);
				map_freq[best_idx]++;
			}
		}

		int delta_min = Integer.MAX_VALUE, delta_max = Integer.MIN_VALUE;
		int size = delta_list.size();
		for (int i = 0; i < size; i++)
		{
			int v = delta_list.get(i);
			if (v < delta_min) delta_min = v;
			if (v > delta_max) delta_max = v;
		}
		int[] delta_freq = new int[delta_max - delta_min + 1];
		for (int i = 0; i < size; i++) delta_freq[delta_list.get(i) - delta_min]++;

		ArrayList<int[]> result = new ArrayList<int[]>();
		result.add(delta_freq);
		result.add(map_freq);
		return result;
	}

	// Returns [delta_frequency, map_frequency (16 entries, one per predictor)].
	public static ArrayList<int[]> getIdealFrequency16(int src[], int xdim, int ydim)
	{
		ArrayList<Integer> delta_list = new ArrayList<Integer>();
		int[] map_freq = new int[16];

		for (int i = 1; i < ydim; i++)
		{
			for (int j = 1; j < xdim - 1; j++)
			{
				int k = i * xdim + j;
				int a = src[k - 1];
				int b = src[k - xdim];
				int c = src[k - xdim - 1];
				int d = src[k - xdim + 1];
				int e = src[k];

				int med;
				if (c >= Math.max(a, b))      med = Math.min(a, b);
				else if (c <= Math.min(a, b)) med = Math.max(a, b);
				else                          med = a + b - c;

				int[] pred = {
					a, c, b, d,
					(a+c)>>1, (c+b)>>1, (b+d)>>1, (d+a)>>1,
					(a+b)>>1, (c+d)>>1,
					(a+b+c+d)>>2, med,
					(a+b+c)>>2, (a+b+d)>>2, (a+c+d)>>2, (b+c+d)>>2
				};

				int best_abs   = Integer.MAX_VALUE;
				int best_delta = 0;
				int best_n     = 0;
				for (int n = 0; n < 16; n++)
				{
					int delta     = e - pred[n];
					int abs_delta = Math.abs(delta);
					if (abs_delta < best_abs) { best_abs = abs_delta; best_delta = delta; best_n = n; }
				}
				delta_list.add(best_delta);
				map_freq[best_n]++;
			}
		}

		int delta_min = Integer.MAX_VALUE, delta_max = Integer.MIN_VALUE;
		int size = delta_list.size();
		for (int i = 0; i < size; i++)
		{
			int v = delta_list.get(i);
			if (v < delta_min) delta_min = v;
			if (v > delta_max) delta_max = v;
		}
		int[] delta_freq = new int[delta_max - delta_min + 1];
		for (int i = 0; i < size; i++) delta_freq[delta_list.get(i) - delta_min]++;

		ArrayList<int[]> result = new ArrayList<int[]>();
		result.add(delta_freq);
		result.add(map_freq);
		return result;
	}

	public static ArrayList<int[]> getMedScanlineFrequency(int src[], int xdim, int ydim)
	{
		ArrayList<Integer> delta_list = new ArrayList<Integer>();
		byte[] map = new byte[ydim - 1];

		// Pass 1: choose best filter per row using Shannon entropy
		// Filters: 0=horizontal, 1=vertical, 2=average, 3=MED
		for (int i = 1; i < ydim; i++)
		{
			int[][] delta = new int[4][xdim - 2];

			int k = i * xdim + 1;
			for (int j = 1; j < xdim - 1; j++)
			{
				delta[0][j - 1] = src[k] - src[k - 1];
				delta[1][j - 1] = src[k] - src[k - xdim];
				delta[2][j - 1] = src[k] - (src[k - 1] + src[k - xdim]) / 2;

				int a = src[k - 1];
				int b = src[k - xdim];
				int c = src[k - xdim - 1];

				int pred;
				if (c >= Math.max(a, b))
					pred = Math.min(a, b);
				else if (c <= Math.min(a, b))
					pred = Math.max(a, b);
				else
					pred = a + b - c;

				delta[3][j - 1] = src[k] - pred;
				k++;
			}

			int[] limit = new int[4];
			for (int j = 0; j < 4; j++)
			{
				int[] current_delta = delta[j];

				int delta_min = current_delta[0];
				int delta_max = current_delta[0];
				for (k = 1; k < current_delta.length; k++)
				{
					if (current_delta[k] < delta_min)
						delta_min = current_delta[k];
					else if (current_delta[k] > delta_max)
						delta_max = current_delta[k];
				}

				for (k = 0; k < current_delta.length; k++)
					current_delta[k] -= delta_min;
				int range = delta_max - delta_min;
				int[] frequency = new int[range + 1];
				for (k = 0; k < current_delta.length; k++)
					frequency[current_delta[k]]++;
				double shannon_limit = CodeMapper.getShannonLimit(frequency);
				limit[j] = (int) Math.floor(shannon_limit);
			}

			int value = limit[0];
			int index = 0;
			for (k = 1; k < 4; k++)
			{
				if (limit[k] < value)
				{
					value = limit[k];
					index = k;
				}
			}
			map[i - 1] = (byte) index;
		}

		// Pass 2: collect deltas using chosen filter per row
		for (int i = 1; i < ydim; i++)
		{
			int k = i * xdim + 1;
			byte m = map[i - 1];

			if (m == 0)
			{
				for (int j = 1; j < xdim - 1; j++) { delta_list.add(src[k] - src[k - 1]); k++; }
			}
			else if (m == 1)
			{
				for (int j = 1; j < xdim - 1; j++) { delta_list.add(src[k] - src[k - xdim]); k++; }
			}
			else if (m == 2)
			{
				for (int j = 1; j < xdim - 1; j++) { delta_list.add(src[k] - (src[k-1] + src[k-xdim]) / 2); k++; }
			}
			else if (m == 3)
			{
				for (int j = 1; j < xdim - 1; j++)
				{
					int a = src[k - 1];
					int b = src[k - xdim];
					int c = src[k - xdim - 1];

					int pred;
					if (c >= Math.max(a, b))
						pred = Math.min(a, b);
					else if (c <= Math.min(a, b))
						pred = Math.max(a, b);
					else
						pred = a + b - c;

					delta_list.add(src[k] - pred);
					k++;
				}
			}
		}

		int delta_min = Integer.MAX_VALUE;
		int delta_max = Integer.MIN_VALUE;
		int size = delta_list.size();
		for (int i = 0; i < size; i++)
		{
			int current_delta = delta_list.get(i);
			if (current_delta < delta_min) delta_min = current_delta;
			if (current_delta > delta_max) delta_max = current_delta;
		}

		int range = delta_max - delta_min;
		int[] delta_frequency = new int[range + 1];
		for (int i = 0; i < size; i++)
		{
			int current_value = delta_list.get(i);
			current_value -= delta_min;
			delta_frequency[current_value]++;
		}

		int[] map_frequency = new int[4];
		for (int i = 0; i < map.length; i++)
			map_frequency[map[i]]++;

		ArrayList<int[]> result = new ArrayList<int[]>();
		result.add(delta_frequency);
		result.add(map_frequency);
		return result;
	}

	public static ArrayList<int[]> getScanline2Frequency(int src[], int xdim, int ydim)
	{
		ArrayList<Integer> delta_list = new ArrayList<Integer>();
		byte[] map = new byte[ydim - 1];

		for(int i = 1; i < ydim; i++)
		{
			int[][] delta = new int[4][xdim - 2];

			int k = i * xdim + 1;
			for(int j = 1; j < xdim - 1; j++)
			{
				delta[0][j - 1] = src[k] - src[k - 1];
				delta[1][j - 1] = src[k] - src[k - xdim];
				delta[2][j - 1] = src[k] - (src[k - 1] + src[k - xdim]) / 2;
				delta[3][j - 1] = src[k] - (src[k - 1] + src[k - xdim + 1]) / 2;
				k++;
			}

			int[] limit = new int[4];
			for(int j = 0; j < 4; j++)
			{
				int[] current_delta = delta[j];

				int delta_min = current_delta[0];
				int delta_max = current_delta[0];
				for(k = 1; k < current_delta.length; k++)
				{
					if(current_delta[k] < delta_min) delta_min = current_delta[k];
					else if(current_delta[k] > delta_max) delta_max = current_delta[k];
				}

				for(k = 0; k < current_delta.length; k++)
					current_delta[k] -= delta_min;
				int range = delta_max - delta_min;
				int[] frequency = new int[range + 1];
				for(k = 0; k < current_delta.length; k++)
					frequency[current_delta[k]]++;
				double shannon_limit = CodeMapper.getShannonLimit(frequency);
				limit[j] = (int) Math.floor(shannon_limit);
			}

			int value = limit[0];
			int index = 0;
			for(k = 1; k < 4; k++)
			{
				if(limit[k] < value) { value = limit[k]; index = k; }
			}
			map[i - 1] = (byte) index;
		}

		for(int i = 1; i < ydim; i++)
		{
			int k = i * xdim + 1;
			byte m = map[i - 1];

			if(m == 0)
			{
				for(int j = 1; j < xdim - 1; j++) { delta_list.add(src[k] - src[k - 1]); k++; }
			}
			else if(m == 1)
			{
				for(int j = 1; j < xdim - 1; j++) { delta_list.add(src[k] - src[k - xdim]); k++; }
			}
			else if(m == 2)
			{
				for(int j = 1; j < xdim - 1; j++) { delta_list.add(src[k] - (src[k-1] + src[k-xdim]) / 2); k++; }
			}
			else if(m == 3)
			{
				for(int j = 1; j < xdim - 1; j++) { delta_list.add(src[k] - (src[k-1] + src[k-xdim+1]) / 2); k++; }
			}
		}

		int delta_min = Integer.MAX_VALUE;
		int delta_max = Integer.MIN_VALUE;
		int size = delta_list.size();
		for(int i = 0; i < size; i++)
		{
			int current_delta = delta_list.get(i);
			if(current_delta < delta_min) delta_min = current_delta;
			if(current_delta > delta_max) delta_max = current_delta;
		}

		int range = delta_max - delta_min;
		int[] frequency = new int[range + 1];
		for(int i = 0; i < size; i++)
		{
			int current_value = delta_list.get(i);
			current_value -= delta_min;
			frequency[current_value]++;
		}

		int[] map_frequency = new int[4];
		for(int i = 0; i < map.length; i++)
			map_frequency[map[i]]++;

		ArrayList<int[]> result = new ArrayList<int[]>();
		result.add(frequency);
		result.add(map_frequency);
		return result;
	}

	public static ArrayList<int[]> getMixedDeltas4Frequency(int src[], int xdim, int ydim)
	{
		ArrayList<Integer> delta_list = new ArrayList<Integer>();
		byte[] map = new byte[ydim - 1];

		// Pass 1: choose best filter per row using Shannon entropy
		// Filters: 0=horizontal, 1=average, 2=MED, 3=directional
		for (int i = 1; i < ydim; i++)
		{
			int[][] delta = new int[4][xdim - 2];

			int k = i * xdim + 1;
			for (int j = 1; j < xdim - 1; j++)
			{
				int a = src[k - 1];
				int b = src[k - xdim];
				int c = src[k - xdim - 1];
				int d = src[k - xdim + 1];

				delta[0][j - 1] = src[k] - a;
				delta[1][j - 1] = src[k] - (a + b) / 2;

				int med_pred;
				if (c >= Math.max(a, b))
					med_pred = Math.min(a, b);
				else if (c <= Math.min(a, b))
					med_pred = Math.max(a, b);
				else
					med_pred = a + b - c;
				delta[2][j - 1] = src[k] - med_pred;

				int h_edge  = Math.abs(b - c) + Math.abs(b - d);
				int v_edge  = Math.abs(a - c) + Math.abs(a - b);
				int dl_edge = Math.abs(c - d);
				int dr_edge = Math.abs(a - d);

				int dir_pred;
				if (h_edge >= v_edge && h_edge >= dl_edge && h_edge >= dr_edge)
					dir_pred = b;
				else if (v_edge >= dl_edge && v_edge >= dr_edge)
					dir_pred = a;
				else if (dl_edge >= dr_edge)
					dir_pred = d;
				else
					dir_pred = c;
				delta[3][j - 1] = src[k] - dir_pred;

				k++;
			}

			int[] limit = new int[4];
			for (int j = 0; j < 4; j++)
			{
				int[] current_delta = delta[j];

				int delta_min = current_delta[0];
				int delta_max = current_delta[0];
				for (k = 1; k < current_delta.length; k++)
				{
					if (current_delta[k] < delta_min) delta_min = current_delta[k];
					else if (current_delta[k] > delta_max) delta_max = current_delta[k];
				}

				for (k = 0; k < current_delta.length; k++)
					current_delta[k] -= delta_min;
				int range = delta_max - delta_min;
				int[] frequency = new int[range + 1];
				for (k = 0; k < current_delta.length; k++)
					frequency[current_delta[k]]++;
				double shannon_limit = CodeMapper.getShannonLimit(frequency);
				limit[j] = (int) Math.floor(shannon_limit);
			}

			int value = limit[0];
			int index = 0;
			for (k = 1; k < 4; k++)
			{
				if (limit[k] < value) { value = limit[k]; index = k; }
			}
			map[i - 1] = (byte) index;
		}

		// Pass 2: collect deltas using chosen filter per row
		for (int i = 1; i < ydim; i++)
		{
			int k  = i * xdim + 1;
			byte m = map[i - 1];

			if (m == 0)
			{
				for (int j = 1; j < xdim - 1; j++) { delta_list.add(src[k] - src[k - 1]); k++; }
			}
			else if (m == 1)
			{
				for (int j = 1; j < xdim - 1; j++) { delta_list.add(src[k] - (src[k-1] + src[k-xdim]) / 2); k++; }
			}
			else if (m == 2)
			{
				for (int j = 1; j < xdim - 1; j++)
				{
					int a = src[k - 1];
					int b = src[k - xdim];
					int c = src[k - xdim - 1];

					int pred;
					if (c >= Math.max(a, b))
						pred = Math.min(a, b);
					else if (c <= Math.min(a, b))
						pred = Math.max(a, b);
					else
						pred = a + b - c;

					delta_list.add(src[k] - pred);
					k++;
				}
			}
			else if (m == 3)
			{
				for (int j = 1; j < xdim - 1; j++)
				{
					int a = src[k - 1];
					int b = src[k - xdim];
					int c = src[k - xdim - 1];
					int d = src[k - xdim + 1];

					int h_edge  = Math.abs(b - c) + Math.abs(b - d);
					int v_edge  = Math.abs(a - c) + Math.abs(a - b);
					int dl_edge = Math.abs(c - d);
					int dr_edge = Math.abs(a - d);

					int pred;
					if (h_edge >= v_edge && h_edge >= dl_edge && h_edge >= dr_edge)
						pred = b;
					else if (v_edge >= dl_edge && v_edge >= dr_edge)
						pred = a;
					else if (dl_edge >= dr_edge)
						pred = d;
					else
						pred = c;

					delta_list.add(src[k] - pred);
					k++;
				}
			}
		}

		int delta_min = Integer.MAX_VALUE;
		int delta_max = Integer.MIN_VALUE;
		int size      = delta_list.size();
		for (int i = 0; i < size; i++)
		{
			int current_delta = delta_list.get(i);
			if (current_delta < delta_min) delta_min = current_delta;
			if (current_delta > delta_max) delta_max = current_delta;
		}

		int range = delta_max - delta_min;
		int[] delta_frequency = new int[range + 1];
		for (int i = 0; i < size; i++)
		{
			int current_value = delta_list.get(i);
			current_value -= delta_min;
			delta_frequency[current_value]++;
		}

		int[] map_frequency = new int[4];
		for (int i = 0; i < map.length; i++)
			map_frequency[map[i]]++;

		ArrayList<int[]> result = new ArrayList<int[]>();
		result.add(delta_frequency);
		result.add(map_frequency);
		return result;
	}

	// =========================================================================
	// Delta encoders / decoders
	// =========================================================================

	public static ArrayList getHorizontalDeltasFromValues(int src[], int xdim, int ydim)
	{
		int[] dst = new int[xdim * ydim];
		int sum = 0;
		int init_value = src[0];
		// init_value is updated below as the column-0 predictor; the decoder
		// needs the original src[0], so that is what gets returned.
		int original_init_value = src[0];
		int value = init_value;

		int k = 0;
		for(int i = 0; i < ydim; i++)
		{
			if(i == 0)
				dst[k++] = 0;
			else
			{
				int delta = src[k] - init_value;
				dst[k++] = delta;
				init_value += delta;
				sum += Math.abs(delta);
				value = init_value;
			}

			for(int j = 1; j < xdim; j++)
			{
				int delta = src[k] - value;
				value += delta;
				sum += Math.abs(delta);
				dst[k++] = delta;
			}
		}

		ArrayList result = new ArrayList();
		result.add(sum);
		result.add(dst);
		result.add(original_init_value);
		return result;
	}

	public static int[] getValuesFromHorizontalDeltas(int src[], int xdim, int ydim, int init_value)
	{
		int[] dst = new int[xdim * ydim];

		int k = 0;
		int value = init_value;
		for(int i = 0; i < ydim; i++)
		{
			if(i != 0)
				value += src[k];
			int current_value = value;
			dst[k++] = current_value;
			for(int j = 1; j < xdim; j++)
			{
				current_value += src[k];
				dst[k++] = current_value;
			}
		}
		return dst;
	}

	public static ArrayList getVerticalDeltasFromValues(int src[], int xdim, int ydim)
	{
		int[] dst = new int[xdim * ydim];
		int init_value = src[0];
		int value = init_value;
		int delta = 0;
		int sum = 0;

		int k = 0;
		for(int i = 0; i < ydim; i++)
		{
			for(int j = 0; j < xdim; j++)
			{
				if(i == 0)
				{
					if(j == 0)
						dst[k++] = 0;
					else
					{
						delta = src[k] - value;
						value += delta;
						dst[k++] = delta;
						sum += Math.abs(delta);
					}
				}
				else
				{
					delta = src[k] - src[k - xdim];
					dst[k++] = delta;
					sum += Math.abs(delta);
				}
			}
		}

		ArrayList result = new ArrayList();
		result.add(sum);
		result.add(dst);
		result.add(init_value);
		return result;
	}

	public static int[] getValuesFromVerticalDeltas(int src[], int xdim, int ydim, int init_value)
	{
		int[] dst = new int[xdim * ydim];
		dst[0] = init_value;
		int value = init_value;

		for(int i = 1; i < xdim; i++)
		{
			value += src[i];
			dst[i] = value;
		}

		for(int i = 1; i < ydim; i++)
		{
			for(int j = 0; j < xdim; j++)
			{
				int index = i * xdim + j;
				dst[index] = dst[index - xdim] + src[index];
			}
		}

		return dst;
	}

	public static ArrayList getAverageDeltasFromValues(int src[], int xdim, int ydim)
	{
		int[] dst = new int[xdim * ydim];
		int sum = 0;
		int init_value = src[0];

		int k = 0;
		dst[k++] = 0;
		for(int i = 1; i < xdim; i++)
		{
			int delta = src[k] - src[k - 1];
			dst[k++] = delta;
			sum += Math.abs(delta);
		}

		for(int i = 1; i < ydim; i++)
		{
			int delta = src[k] - src[k - xdim];
			dst[k++] = delta;
			sum += Math.abs(delta);

			for(int j = 1; j < xdim; j++)
			{
				delta = src[k] - (src[k - 1] + src[k - xdim]) / 2;
				dst[k++] = delta;
				sum += Math.abs(delta);
			}
		}

		ArrayList result = new ArrayList();
		result.add(sum);
		result.add(dst);
		result.add(init_value);
		return result;
	}

	public static int[] getValuesFromAverageDeltas(int src[], int xdim, int ydim, int init_value)
	{
		int[] dst = new int[xdim * ydim];
		int k = 0;
		dst[k++] = init_value;

		for(int i = 1; i < xdim; i++)
		{
			int value = dst[k - 1] + src[k];
			dst[k++] = value;
		}

		for(int i = 1; i < ydim; i++)
		{
			int value = dst[k - xdim] + src[k];
			dst[k++] = value;
			for(int j = 1; j < xdim; j++)
			{
				value = (dst[k - 1] + dst[k - xdim]) / 2 + src[k];
				dst[k++] = value;
			}
		}

		return dst;
	}

	public static ArrayList getPaethDeltasFromValues(int src[], int xdim, int ydim)
	{
		int[] dst = new int[xdim * ydim];
		int init_value = src[0];
		// The original src[0] (init_value changes below).
		int original_init_value = src[0];
		int value = init_value;
		int delta = 0;
		int sum = 0;
		int k = 0;

		for(int i = 0; i < ydim; i++)
		{
			if(i == 0)
			{
				for(int j = 0; j < xdim; j++)
				{
					if(j == 0)
						dst[k++] = 0;
					else
					{
						delta = src[k] - value;
						value += delta;
						dst[k++] = delta;
						sum += Math.abs(delta);
					}
				}
			}
			else
			{
				for(int j = 0; j < xdim; j++)
				{
					if(j == 0)
					{
						delta = src[k] - init_value;
						init_value = src[k];
						dst[k++] = delta;
						sum += Math.abs(delta);
					}
					else
					{
						int a = src[k - 1];
						int b = src[k - xdim];
						int c = src[k - xdim - 1];
						int d = a + b - c;

						int delta_a = Math.abs(a - d);
						int delta_b = Math.abs(b - d);
						int delta_c = Math.abs(c - d);

						if(delta_a <= delta_b && delta_a <= delta_c)
							delta = src[k] - src[k - 1];
						else if(delta_b <= delta_c)
							delta = src[k] - src[k - xdim];
						else
							delta = src[k] - src[k - xdim - 1];

						dst[k++] = delta;
						sum += Math.abs(delta);
					}
				}
			}
		}
		ArrayList result = new ArrayList();
		result.add(sum);
		result.add(dst);
		result.add(original_init_value);
		return result;
	}

	public static int[] getValuesFromPaethDeltas(int src[], int xdim, int ydim, int init_value)
	{
		int[] dst = new int[xdim * ydim];
		dst[0] = init_value;
		int value = init_value;

		for(int i = 1; i < xdim; i++)
		{
			value += src[i];
			dst[i] = value;
		}

		for(int i = 1; i < ydim; i++)
		{
			for(int j = 0; j < xdim; j++)
			{
				if(j == 0)
				{
					init_value += src[i * xdim];
					dst[i * xdim] = init_value;
					value = init_value;
				}
				else
				{
					int a = dst[i * xdim + j - 1];
					int b = dst[(i - 1) * xdim + j];
					int c = dst[(i - 1) * xdim + j - 1];
					int d = a + b - c;

					int delta_a = Math.abs(a - d);
					int delta_b = Math.abs(b - d);
					int delta_c = Math.abs(c - d);

					if(delta_a <= delta_b && delta_a <= delta_c)
						dst[i * xdim + j] = a + src[i * xdim + j];
					else if(delta_b <= delta_c)
						dst[i * xdim + j] = b + src[i * xdim + j];
					else
						dst[i * xdim + j] = c + src[i * xdim + j];
				}
			}
		}
		return dst;
	}

	public static ArrayList getMedDeltasFromValues(int src[], int xdim, int ydim)
	{
		int[] dst = new int[xdim * ydim];
		int init_value = src[0];
		int sum = 0;
		int k = 0;

		dst[k++] = 0;
		for (int j = 1; j < xdim; j++)
		{
			int delta = src[k] - src[k - 1];
			dst[k++] = delta;
			sum += Math.abs(delta);
		}

		for (int i = 1; i < ydim; i++)
		{
			int delta = src[k] - src[k - xdim];
			dst[k++] = delta;
			sum += Math.abs(delta);

			for (int j = 1; j < xdim; j++)
			{
				int a = src[k - 1];
				int b = src[k - xdim];
				int c = src[k - xdim - 1];

				int pred;
				if (c >= Math.max(a, b))
					pred = Math.min(a, b);
				else if (c <= Math.min(a, b))
					pred = Math.max(a, b);
				else
					pred = a + b - c;

				delta = src[k] - pred;
				dst[k++] = delta;
				sum += Math.abs(delta);
			}
		}

		ArrayList result = new ArrayList();
		result.add(sum);
		result.add(dst);
		result.add(init_value);
		return result;
	}

	public static int[] getValuesFromMedDeltas(int src[], int xdim, int ydim, int init_value)
	{
		int[] dst = new int[xdim * ydim];
		int k = 0;

		dst[k++] = init_value;
		for (int j = 1; j < xdim; j++)
		{
			dst[k] = dst[k - 1] + src[k];
			k++;
		}

		for (int i = 1; i < ydim; i++)
		{
			dst[k] = dst[k - xdim] + src[k];
			k++;

			for (int j = 1; j < xdim; j++)
			{
				int a = dst[k - 1];
				int b = dst[k - xdim];
				int c = dst[k - xdim - 1];

				int pred;
				if (c >= Math.max(a, b))
					pred = Math.min(a, b);
				else if (c <= Math.min(a, b))
					pred = Math.max(a, b);
				else
					pred = a + b - c;

				dst[k] = pred + src[k];
				k++;
			}
		}

		return dst;
	}

	public static ArrayList getDirectionalDeltasFromValues(int src[], int xdim, int ydim)
	{
		int[] dst        = new int[xdim * ydim];
		int   init_value = src[0];
		int   sum        = 0;
		int   k          = 0;

		dst[k++] = 0;
		for (int j = 1; j < xdim; j++)
		{
			int delta = src[k] - src[k - 1];
			dst[k++]  = delta;
			sum      += Math.abs(delta);
		}

		for (int i = 1; i < ydim; i++)
		{
			int delta = src[k] - src[k - xdim];
			dst[k++]  = delta;
			sum      += Math.abs(delta);

			for (int j = 1; j < xdim - 1; j++)
			{
				int a = src[k - 1];
				int b = src[k - xdim];
				int c = src[k - xdim - 1];
				int d = src[k - xdim + 1];

				int h_edge  = Math.abs(b - c) + Math.abs(b - d);
				int v_edge  = Math.abs(a - c) + Math.abs(a - b);
				int dl_edge = Math.abs(c - d);
				int dr_edge = Math.abs(a - d);

				int pred;
				if (h_edge >= v_edge && h_edge >= dl_edge && h_edge >= dr_edge)
					pred = b;
				else if (v_edge >= dl_edge && v_edge >= dr_edge)
					pred = a;
				else if (dl_edge >= dr_edge)
					pred = d;
				else
					pred = c;

				delta    = src[k] - pred;
				dst[k++] = delta;
				sum     += Math.abs(delta);
			}

			// Last column: no above-right, fall back to MED
			{
				int a = src[k - 1];
				int b = src[k - xdim];
				int c = src[k - xdim - 1];

				int pred;
				if (c >= Math.max(a, b))
					pred = Math.min(a, b);
				else if (c <= Math.min(a, b))
					pred = Math.max(a, b);
				else
					pred = a + b - c;

				delta    = src[k] - pred;
				dst[k++] = delta;
				sum     += Math.abs(delta);
			}
		}

		ArrayList result = new ArrayList();
		result.add(sum);
		result.add(dst);
		result.add(init_value);
		return result;
	}

	public static int[] getValuesFromDirectionalDeltas(int src[], int xdim, int ydim, int init_value)
	{
		int[] dst = new int[xdim * ydim];
		int   k   = 0;

		dst[k++] = init_value;
		for (int j = 1; j < xdim; j++)
		{
			dst[k] = dst[k - 1] + src[k];
			k++;
		}

		for (int i = 1; i < ydim; i++)
		{
			dst[k] = dst[k - xdim] + src[k];
			k++;

			for (int j = 1; j < xdim - 1; j++)
			{
				int a = dst[k - 1];
				int b = dst[k - xdim];
				int c = dst[k - xdim - 1];
				int d = dst[k - xdim + 1];

				int h_edge  = Math.abs(b - c) + Math.abs(b - d);
				int v_edge  = Math.abs(a - c) + Math.abs(a - b);
				int dl_edge = Math.abs(c - d);
				int dr_edge = Math.abs(a - d);

				int pred;
				if (h_edge >= v_edge && h_edge >= dl_edge && h_edge >= dr_edge)
					pred = b;
				else if (v_edge >= dl_edge && v_edge >= dr_edge)
					pred = a;
				else if (dl_edge >= dr_edge)
					pred = d;
				else
					pred = c;

				dst[k] = pred + src[k];
				k++;
			}

			// Last column: MED
			{
				int a = dst[k - 1];
				int b = dst[k - xdim];
				int c = dst[k - xdim - 1];

				int pred;
				if (c >= Math.max(a, b))
					pred = Math.min(a, b);
				else if (c <= Math.min(a, b))
					pred = Math.max(a, b);
				else
					pred = a + b - c;

				dst[k] = pred + src[k];
				k++;
			}
		}

		return dst;
	}

	public static ArrayList getGradientDeltasFromValues(int src[], int xdim, int ydim)
	{
		int[] dst        = new int[xdim * ydim];
		int[] gradient   = new int[4];
		int   init_value = src[0];
		// The original src[0] (init_value changes below).
		int   original_init_value = src[0];
		int   sum        = 0;
		int   delta      = 0;
		int   k          = 0;

		dst[k++] = 0;
		for(int i = 1; i < xdim; i++)
		{
			delta = src[k] - src[k - 1];
			dst[k++] = delta;
			sum += Math.abs(delta);
		}

		delta = src[k] - init_value;
		dst[k++] = delta;
		init_value += delta;
		sum += Math.abs(delta);

		for(int i = 1; i < xdim; i++)
		{
			delta = src[k] - src[k - 1];
			dst[k++] = delta;
			sum += Math.abs(delta);
		}

		for(int i = 2; i < ydim; i++)
		{
			delta = src[k] - init_value;
			dst[k++] = delta;
			init_value += delta;
			sum += Math.abs(delta);

			for(int j = 1; j < xdim - 1; j++)
			{
				int a = src[k - 1];
				int b = src[k - xdim];
				int c = src[k - xdim - 1];
				int d = src[k - xdim + 1];
				int e = src[k - 2 * xdim - 1];

				gradient[0] = Math.abs(a - e);
				gradient[1] = Math.abs(c - d);
				gradient[2] = Math.abs(a - d);
				gradient[3] = Math.abs(b - e);

				int max_value = gradient[0];
				int max_index = 0;
				for(int m = 1; m < 4; m++)
				{
					if(gradient[m] > max_value) { max_value = gradient[m]; max_index = m; }
				}

				if(max_index == 0)      delta = src[k] - src[k - 1];
				else if(max_index == 1) delta = src[k] - src[k - xdim];
				else if(max_index == 2) delta = src[k] - src[k - xdim - 1];
				else                    delta = src[k] - src[k - xdim + 1];
				dst[k++] = delta;
				sum += Math.abs(delta);
			}

			delta = src[k] - src[k - 1];
			dst[k++] = delta;
			sum += Math.abs(delta);
		}

		ArrayList result = new ArrayList();
		result.add(sum);
		result.add(dst);
		result.add(original_init_value);
		return result;
	}

	public static int[] getValuesFromGradientDeltas(int src[], int xdim, int ydim, int init_value)
	{
		int[] dst      = new int[xdim * ydim];
		int[] gradient = new int[4];
		int   k        = 0;

		dst[k++] = init_value;
		for(int i = 1; i < xdim; i++) { dst[k] = dst[k-1] + src[k]; k++; }

		init_value += src[k];
		dst[k++] = init_value;
		for(int i = 1; i < xdim; i++) { dst[k] = dst[k-1] + src[k]; k++; }

		for(int i = 2; i < ydim; i++)
		{
			init_value += src[k];
			dst[k++] = init_value;

			for(int j = 1; j < xdim - 1; j++)
			{
				int a = dst[k - 1];
				int b = dst[k - xdim];
				int c = dst[k - xdim - 1];
				int d = dst[k - xdim + 1];
				int e = dst[k - 2 * xdim - 1];

				gradient[0] = Math.abs(a - e);
				gradient[1] = Math.abs(c - d);
				gradient[2] = Math.abs(a - d);
				gradient[3] = Math.abs(b - e);

				int max_value = gradient[0];
				int max_index = 0;
				for(int m = 1; m < 4; m++)
				{
					if(gradient[m] > max_value) { max_value = gradient[m]; max_index = m; }
				}

				if(max_index == 0)      dst[k] = dst[k-1]        + src[k];
				else if(max_index == 1) dst[k] = dst[k-xdim]     + src[k];
				else if(max_index == 2) dst[k] = dst[k-xdim-1]   + src[k];
				else                    dst[k] = dst[k-xdim+1]   + src[k];
				k++;
			}

			dst[k] = dst[k-1] + src[k];
			k++;
		}
		return dst;
	}

	public static ArrayList getGradientDeltasFromValues2(int src[], int xdim, int ydim)
	{
		int[] dst      = new int[xdim * ydim];
		int[] gradient = new int[4];
		int   init_value = src[0];
		// The original src[0] (init_value changes below).
		int   original_init_value = src[0];
		int   sum = 0;
		int   k   = 0;

		dst[k++] = 0;
		for(int i = 1; i < xdim; i++)
		{
			int delta = src[k] - src[k - 1];
			dst[k++] = delta;
			sum += Math.abs(delta);
		}

		for(int i = 1; i < ydim; i++)
		{
			int delta = src[k] - init_value;
			dst[k++] = delta;
			init_value += delta;
			sum += Math.abs(delta);

			delta = src[k] - src[k - 1];
			dst[k++] = delta;
			sum += Math.abs(delta);

			for(int j = 2; j < xdim - 1; j++)
			{
				int a = src[k - 1];
				int b = src[k - xdim];
				int c = src[k - xdim - 1];
				int d = src[k - xdim + 1];
				int e = src[k - xdim - 2];

				gradient[0] = Math.abs(c - b);
				gradient[1] = Math.abs(c - a);
				gradient[2] = Math.abs(a - b);
				gradient[3] = Math.abs(a - e);

				int max_value = gradient[0];
				int max_index = 0;
				for(int m = 1; m < 4; m++)
				{
					if(gradient[m] > max_value) { max_value = gradient[m]; max_index = m; }
				}

				if(max_index == 0)      delta = src[k] - src[k - 1];
				else if(max_index == 1) delta = src[k] - src[k - xdim];
				else if(max_index == 2) delta = src[k] - src[k - xdim - 1];
				else                    delta = src[k] - src[k - xdim + 1];
				dst[k++] = delta;
				sum += Math.abs(delta);
			}

			delta = src[k] - src[k - 1];
			dst[k++] = delta;
			sum += Math.abs(delta);
		}

		ArrayList result = new ArrayList();
		result.add(sum);
		result.add(dst);
		result.add(original_init_value);
		return result;
	}

	public static int[] getValuesFromGradientDeltas2(int src[], int xdim, int ydim, int init_value)
	{
		int[] dst      = new int[xdim * ydim];
		int[] gradient = new int[4];
		int   k        = 0;

		dst[k++] = init_value;
		for(int i = 1; i < xdim; i++) { dst[k] = dst[k-1] + src[k]; k++; }

		for(int i = 1; i < ydim; i++)
		{
			init_value += src[k];
			dst[k++] = init_value;
			dst[k]   = dst[k-1] + src[k];
			k++;

			for(int j = 2; j < xdim - 1; j++)
			{
				int a = dst[k - 1];
				int b = dst[k - xdim];
				int c = dst[k - xdim - 1];
				int d = dst[k - xdim + 1];
				int e = dst[k - xdim - 2];

				gradient[0] = Math.abs(c - b);
				gradient[1] = Math.abs(c - a);
				gradient[2] = Math.abs(a - b);
				gradient[3] = Math.abs(a - e);

				int max_value = gradient[0];
				int max_index = 0;
				for(int m = 1; m < 4; m++)
				{
					if(gradient[m] > max_value) { max_value = gradient[m]; max_index = m; }
				}

				if(max_index == 0)      dst[k] = dst[k-1]      + src[k];
				else if(max_index == 1) dst[k] = dst[k-xdim]   + src[k];
				else if(max_index == 2) dst[k] = dst[k-xdim-1] + src[k];
				else                    dst[k] = dst[k-xdim+1] + src[k];
				k++;
			}

			dst[k] = dst[k-1] + src[k];
			k++;
		}
		return dst;
	}

	public static ArrayList getMixedDeltasFromValues(int src[], int xdim, int ydim)
	{
		byte[] map = new byte[ydim - 1];

		for (int i = 1; i < ydim; i++)
		{
			int[][] delta = new int[4][xdim - 2];

			int k = i * xdim + 1;
			for (int j = 1; j < xdim - 1; j++)
			{
				delta[0][j - 1] = src[k] - src[k - 1];
				delta[1][j - 1] = src[k] - src[k - xdim];
				delta[2][j - 1] = src[k] - (src[k - 1] + src[k - xdim]) / 2;

				int a = src[k - 1];
				int b = src[k - xdim];
				int c = src[k - xdim - 1];

				int pred;
				if (c >= Math.max(a, b))
					pred = Math.min(a, b);
				else if (c <= Math.min(a, b))
					pred = Math.max(a, b);
				else
					pred = a + b - c;

				delta[3][j - 1] = src[k] - pred;
				k++;
			}

			int[] limit = new int[4];
			for (int j = 0; j < 4; j++)
			{
				int[] current_delta = delta[j];

				int delta_min = current_delta[0];
				int delta_max = current_delta[0];
				for (k = 1; k < current_delta.length; k++)
				{
					if (current_delta[k] < delta_min)      delta_min = current_delta[k];
					else if (current_delta[k] > delta_max) delta_max = current_delta[k];
				}

				for (k = 0; k < current_delta.length; k++)
					current_delta[k] -= delta_min;
				int range = delta_max - delta_min;
				int[] frequency = new int[range + 1];
				for (k = 0; k < current_delta.length; k++)
					frequency[current_delta[k]]++;
				double shannon_limit = CodeMapper.getShannonLimit(frequency);
				limit[j] = (int) Math.floor(shannon_limit);
			}

			int value = limit[0];
			int index = 0;
			for (k = 1; k < 4; k++)
			{
				if (limit[k] < value) { value = limit[k]; index = k; }
			}
			map[i - 1] = (byte) index;
		}

		int[] dst       = new int[xdim * ydim];
		int init_value  = src[0];
		// The original src[0] (init_value changes below).
		int original_init_value = src[0];
		int sum         = 0;
		int k           = 0;

		dst[k++] = 0;
		int delta = src[k] - init_value;
		int value = src[k];
		dst[k++] = delta;
		sum += Math.abs(delta);

		for (int i = 2; i < xdim; i++)
		{
			delta = src[k] - value;
			value = src[k];
			dst[k++] = delta;
			sum += Math.abs(delta);
		}

		for (int i = 1; i < ydim; i++)
		{
			delta      = src[k] - init_value;
			init_value = src[k];
			dst[k++]   = delta;
			sum       += Math.abs(delta);

			byte m = map[i - 1];

			if (m == 0)
			{
				for (int j = 1; j < xdim - 1; j++) { delta = src[k] - src[k-1]; dst[k++] = delta; }
				delta = src[k] - src[k-1]; dst[k++] = delta; sum += Math.abs(delta);
			}
			else if (m == 1)
			{
				for (int j = 1; j < xdim - 1; j++) { delta = src[k] - src[k-xdim]; dst[k++] = delta; }
				delta = src[k] - src[k-1]; dst[k++] = delta; sum += Math.abs(delta);
			}
			else if (m == 2)
			{
				for (int j = 1; j < xdim - 1; j++) { delta = src[k] - (src[k-1]+src[k-xdim])/2; dst[k++] = delta; }
				delta = src[k] - src[k-1]; dst[k++] = delta; sum += Math.abs(delta);
			}
			else if (m == 3)
			{
				for (int j = 1; j < xdim - 1; j++)
				{
					int a = src[k - 1];
					int b = src[k - xdim];
					int c = src[k - xdim - 1];

					int pred;
					if (c >= Math.max(a, b))
						pred = Math.min(a, b);
					else if (c <= Math.min(a, b))
						pred = Math.max(a, b);
					else
						pred = a + b - c;

					delta = src[k] - pred;
					dst[k++] = delta;
					sum += Math.abs(delta);
				}
				delta = src[k] - src[k-1]; dst[k++] = delta;
			}
		}

		ArrayList result = new ArrayList();
		result.add(sum);
		result.add(dst);
		result.add(map);
		result.add(original_init_value);
		return result;
	}

	public static int[] getValuesFromMixedDeltas(int[] src, int xdim, int ydim, int init_value, byte[] map)
	{
		int[] dst = new int[xdim * ydim];
		int k     = 0;
		int value = init_value;

		dst[k++] = init_value;
		for (int i = 1; i < xdim; i++) { value += src[k]; dst[k++] = value; }

		for (int i = 1; i < ydim; i++)
		{
			init_value += src[k];
			dst[k++] = init_value;

			int m = map[i - 1];
			if (m == 0)
			{
				for (int j = 1; j < xdim - 1; j++) { value = dst[k-1] + src[k]; dst[k++] = value; }
			}
			else if (m == 1)
			{
				for (int j = 1; j < xdim - 1; j++) { value = dst[k-xdim] + src[k]; dst[k++] = value; }
			}
			else if (m == 2)
			{
				for (int j = 1; j < xdim - 1; j++) { value = (dst[k-xdim]+dst[k-1])/2 + src[k]; dst[k++] = value; }
			}
			else if (m == 3)
			{
				for (int j = 1; j < xdim - 1; j++)
				{
					int a = dst[k - 1];
					int b = dst[k - xdim];
					int c = dst[k - xdim - 1];

					int pred;
					if (c >= Math.max(a, b))
						pred = Math.min(a, b);
					else if (c <= Math.min(a, b))
						pred = Math.max(a, b);
					else
						pred = a + b - c;

					value = pred + src[k];
					dst[k++] = value;
				}
			}
			value = dst[k-1] + src[k];
			dst[k++] = value;
		}

		return dst;
	}

	public static ArrayList getMixedDeltasFromValues2(int src[], int xdim, int ydim)
	{
		byte[] map = new byte[ydim - 1];

		for(int i = 1; i < ydim; i++)
		{
			int[] sum = new int[4];

			for(int j = 1; j < xdim - 1; j++)
			{
				int k = i * xdim + j;
				sum[0] += Math.abs(src[k] - src[k - 1]);
				sum[1] += Math.abs(src[k] - src[k - xdim]);
				sum[2] += Math.abs(src[k] - (src[k-1] + src[k-xdim]) / 2);
				sum[3] += Math.abs(src[k] - (src[k-1] + src[k-xdim+1]) / 2);
			}

			int value = sum[0];
			int index = 0;
			for(int k = 1; k < 4; k++)
			{
				if(sum[k] < value) { value = sum[k]; index = k; }
			}
			map[i - 1] = (byte) index;
		}

		int[] dst       = new int[xdim * ydim];
		int init_value  = src[0];
		// The original src[0] (init_value changes below).
		int original_init_value = src[0];
		int value       = init_value;
		int sum         = 0;

		for(int i = 0; i < ydim; i++)
		{
			if(i == 0)
			{
				for(int j = 0; j < xdim; j++)
				{
					if(j == 0)
						dst[j] = 0;
					else
					{
						int delta = src[j] - value;
						value += delta;
						dst[j] = delta;
					}
				}
			}
			else
			{
				int k = i * xdim;
				int delta = src[k] - init_value;
				init_value = src[k];
				dst[k] = delta;
				k++;

				int m = map[i - 1];

				for(int j = 1; j < xdim - 1; j++)
				{
					if(m == 0)      delta = src[k] - src[k - 1];
					else if(m == 1) delta = src[k] - src[k - xdim];
					else if(m == 2) delta = src[k] - (src[k-1] + src[k-xdim]) / 2;
					else            delta = src[k] - (src[k-1] + src[k-xdim+1]) / 2; // m == 3

					dst[k++] = delta;
					sum += Math.abs(delta);
				}

				delta = src[k] - src[k - 1];
				dst[k++] = delta;
				sum += Math.abs(delta);
			}
		}

		ArrayList result = new ArrayList();
		result.add(sum);
		result.add(dst);
		result.add(map);
		result.add(original_init_value);
		return result;
	}

	public static int[] getValuesFromMixedDeltas2(int[] src, int xdim, int ydim, int init_value, byte[] map)
	{
		int[] dst = new int[xdim * ydim];
		dst[0] = init_value;
		int value = init_value;

		for(int i = 1; i < xdim; i++) { value += src[i]; dst[i] = value; }

		for(int i = 1; i < ydim; i++)
		{
			byte m = map[i - 1];
			for(int j = 0; j < xdim; j++)
			{
				int k = i * xdim + j;
				if(j == 0)
				{
					init_value += src[k];
					dst[k] = init_value;
				}
				else if(j < xdim - 1)
				{
					if(m == 0)      value = dst[k - 1];
					else if(m == 1) value = dst[k - xdim];
					else if(m == 2) value = (dst[k-1] + dst[k-xdim]) / 2;
					else if(m == 3) value = (dst[k-1] + dst[k-xdim+1]) / 2;

					value += src[k];
					dst[k] = value;
				}
				else
				{
					value = dst[k-1] + src[k];
					dst[k] = value;
				}
			}
		}
		return dst;
	}

	public static ArrayList getMixedDeltasFromValues3(int src[], int xdim, int ydim)
	{
		byte[] line_map = new byte[ydim - 1];
		int m = 0;
		for(int i = 1; i < ydim; i++)
		{
			int[] sum = new int[5];

			for(int j = 1; j < xdim - 1; j++)
			{
				int k = i * xdim + j;
				sum[0] += Math.abs(src[k] - src[k - 1]);
				sum[1] += Math.abs(src[k] - src[k - xdim]);
				sum[2] += Math.abs(src[k] - src[k - xdim - 1]);
				sum[3] += Math.abs(src[k] - (src[k-1] + src[k-xdim]) / 2);
				sum[4] += Math.abs(src[k] - (src[k-1] + src[k-xdim+1]) / 2);
			}

			ArrayList key_list = new ArrayList();
			Hashtable<Double, Integer> delta_table = new Hashtable<Double, Integer>();

			double current_key = sum[0];
			double addend = 0.00000001;

			key_list.add(current_key);
			delta_table.put(current_key, 0);
			for(int k = 1; k < 5; k++)
			{
				current_key = sum[k];
				if(key_list.contains(current_key)) { current_key += addend; addend *= 2.; }
				key_list.add(current_key);
				delta_table.put(current_key, k);
			}

			Collections.sort(key_list);

			double first_key  = (double) key_list.get(0);
			int first_type    = delta_table.get(first_key);
			double second_key = (double) key_list.get(1);
			int second_type   = delta_table.get(second_key);

			ArrayList type_list = new ArrayList();
			type_list.add(first_type);
			type_list.add(second_type);

			if     (type_list.contains(0) && type_list.contains(1)) line_map[m++] = (byte) 0;
			else if(type_list.contains(0) && type_list.contains(2)) line_map[m++] = (byte) 1;
			else if(type_list.contains(0) && type_list.contains(3)) line_map[m++] = (byte) 2;
			else if(type_list.contains(0) && type_list.contains(4)) line_map[m++] = (byte) 3;
			else if(type_list.contains(1) && type_list.contains(2)) line_map[m++] = (byte) 4;
			else if(type_list.contains(1) && type_list.contains(3)) line_map[m++] = (byte) 5;
			else if(type_list.contains(1) && type_list.contains(4)) line_map[m++] = (byte) 6;
			else if(type_list.contains(2) && type_list.contains(3)) line_map[m++] = (byte) 7;
			else if(type_list.contains(2) && type_list.contains(4)) line_map[m++] = (byte) 8;
			else                                                      line_map[m++] = (byte) 9;
		}

		byte[] pixel_map = new byte[(xdim - 2) * (ydim - 1)];
		int n = 0;
		for(int i = 1; i < ydim; i++)
		{
			m = line_map[i - 1];
			int[] value = new int[2];
			for(int j = 1; j < xdim - 1; j++)
			{
				int k = i * xdim + j;
				if(m == 0 || m == 1)
				{
					value[0] += Math.abs(src[k] - src[k-1]);
					value[1] += Math.abs(src[k] - src[k-xdim]);
				}
				else if(m == 2)
				{
					value[0] += Math.abs(src[k] - src[k-1]);
					value[1] += Math.abs(src[k] - (src[k-1]+src[k-xdim])/2);
				}
				else if(m == 3)
				{
					value[0] += Math.abs(src[k] - src[k-1]);
					value[1] += Math.abs(src[k] - (src[k-1]+src[k-xdim+1])/2);
				}
				else if(m == 4)
				{
					value[0] += Math.abs(src[k] - src[k-xdim]);
					value[1] += Math.abs(src[k] - src[k-xdim-1]);
				}
				else if(m == 5)
				{
					value[0] += Math.abs(src[k] - src[k-xdim]);
					value[1] += Math.abs(src[k] - (src[k-1]+src[k-xdim])/2);
				}
				else if(m == 6)
				{
					value[0] += Math.abs(src[k] - src[k-xdim]);
					value[1] += Math.abs(src[k] - (src[k-1]+src[k-xdim+1])/2);
				}
				else if(m == 7)
				{
					value[0] += Math.abs(src[k] - src[k-xdim-1]);
					value[1] += Math.abs(src[k] - (src[k-1]+src[k-xdim])/2);
				}
				else if(m == 8)
				{
					value[0] += Math.abs(src[k] - src[k-xdim-1]);
					value[1] += Math.abs(src[k] - (src[k-1]+src[k-xdim+1])/2);
				}
				else
				{
					value[0] += Math.abs(src[k] - (src[k-1]+src[k-xdim])/2);
					value[1] += Math.abs(src[k] - (src[k-1]+src[k-xdim+1])/2);
				}
				pixel_map[n++] = (value[0] <= value[1]) ? (byte)0 : (byte)1;
			}
		}

		int[] dst      = new int[xdim * ydim];
		int init_value = src[0];
		// The original src[0] (init_value changes below).
		int original_init_value = src[0];
		int value      = init_value;
		int sum        = 0;
		int p          = 0;

		for(int i = 0; i < ydim; i++)
		{
			if(i == 0)
			{
				for(int j = 0; j < xdim; j++)
				{
					if(j == 0) dst[j] = 0;
					else { int delta = src[j] - value; value += delta; dst[j] = delta; }
				}
			}
			else
			{
				int k = i * xdim;
				int delta = src[k] - init_value;
				init_value = src[k];
				dst[k] = delta;
				k++;

				m = line_map[i - 1];

				for(int j = 1; j < xdim - 1; j++)
				{
					n = pixel_map[p++];
					if(m == 0)      delta = (n==0) ? src[k]-src[k-1]   : src[k]-src[k-xdim];
					else if(m == 1) delta = (n==0) ? src[k]-src[k-1]   : src[k]-src[k-xdim-1];
					else if(m == 2) delta = (n==0) ? src[k]-src[k-1]   : src[k]-(src[k-1]+src[k-xdim])/2;
					else if(m == 3) delta = (n==0) ? src[k]-src[k-1]   : src[k]-(src[k-1]+src[k-xdim+1])/2;
					else if(m == 4) delta = (n==0) ? src[k]-src[k-xdim]: src[k]-src[k-xdim-1];
					else if(m == 5) delta = (n==0) ? src[k]-src[k-xdim]: src[k]-(src[k-1]+src[k-xdim])/2;
					else if(m == 6) delta = (n==0) ? src[k]-src[k-xdim]: src[k]-(src[k-1]+src[k-xdim+1])/2;
					else if(m == 7) delta = (n==0) ? src[k]-src[k-xdim-1]: src[k]-(src[k-1]+src[k-xdim])/2;
					else if(m == 8) delta = (n==0) ? src[k]-src[k-xdim-1]: src[k]-(src[k-1]+src[k-xdim+1])/2;
					else            delta = (n==0) ? src[k]-(src[k-1]+src[k-xdim])/2 : src[k]-(src[k-1]+src[k-xdim+1])/2;
					dst[k++] = delta;
					sum += Math.abs(delta);
				}

				delta = src[k] - src[k-1];
				dst[k++] = delta;
				sum += Math.abs(delta);
			}
		}

		ArrayList result = new ArrayList();
		result.add(sum);
		result.add(dst);
		result.add(line_map);
		result.add(pixel_map);
		result.add(original_init_value);
		return result;
	}

	public static int[] getValuesFromMixedDeltas3(int[] src, int xdim, int ydim, int init_value, byte[] line_map, byte[] pixel_map)
	{
		int[] dst = new int[xdim * ydim];
		dst[0] = init_value;
		int value = init_value;

		for(int i = 1; i < xdim; i++) { value += src[i]; dst[i] = value; }

		int p = 0;
		for(int i = 1; i < ydim; i++)
		{
			byte m = line_map[i - 1];
			for(int j = 0; j < xdim; j++)
			{
				int k = i * xdim + j;
				if(j == 0)
				{
					init_value += src[k];
					dst[k] = init_value;
				}
				else if(j < xdim - 1)
				{
					int n = pixel_map[p++];
					if(m == 0)      value = (n==0) ? dst[k-1]     : dst[k-xdim];
					else if(m == 1) value = (n==0) ? dst[k-1]     : dst[k-xdim-1];
					else if(m == 2) value = (n==0) ? dst[k-1]     : (dst[k-1]+dst[k-xdim])/2;
					else if(m == 3) value = (n==0) ? dst[k-1]     : (dst[k-1]+dst[k-xdim+1])/2;
					else if(m == 4) value = (n==0) ? dst[k-xdim]  : dst[k-xdim-1];
					else if(m == 5) value = (n==0) ? dst[k-xdim]  : (dst[k-1]+dst[k-xdim])/2;
					else if(m == 6) value = (n==0) ? dst[k-xdim]  : (dst[k-1]+dst[k-xdim+1])/2;
					else if(m == 7) value = (n==0) ? dst[k-xdim-1]: (dst[k-1]+dst[k-xdim])/2;
					else if(m == 8) value = (n==0) ? dst[k-xdim-1]: (dst[k-1]+dst[k-xdim+1])/2;
					else            value = (n==0) ? (dst[k-1]+dst[k-xdim])/2 : (dst[k-1]+dst[k-xdim+1])/2;
					value += src[k];
					dst[k] = value;
				}
				else
				{
					value = dst[k-1] + src[k];
					dst[k] = value;
				}
			}
		}
		return dst;
	}

	public static ArrayList getMixedDeltasFromValues4(int src[], int xdim, int ydim)
	{
		byte[] map = new byte[ydim - 1];

		for (int i = 1; i < ydim; i++)
		{
			int[][] delta = new int[4][xdim - 2];

			int k = i * xdim + 1;
			for (int j = 1; j < xdim - 1; j++)
			{
				int a = src[k - 1];
				int b = src[k - xdim];
				int c = src[k - xdim - 1];
				int d = src[k - xdim + 1];

				delta[0][j - 1] = src[k] - a;
				delta[1][j - 1] = src[k] - (a + b) / 2;

				int med_pred;
				if (c >= Math.max(a, b))
					med_pred = Math.min(a, b);
				else if (c <= Math.min(a, b))
					med_pred = Math.max(a, b);
				else
					med_pred = a + b - c;
				delta[2][j - 1] = src[k] - med_pred;

				int h_edge  = Math.abs(b - c) + Math.abs(b - d);
				int v_edge  = Math.abs(a - c) + Math.abs(a - b);
				int dl_edge = Math.abs(c - d);
				int dr_edge = Math.abs(a - d);

				int dir_pred;
				if (h_edge >= v_edge && h_edge >= dl_edge && h_edge >= dr_edge)
					dir_pred = b;
				else if (v_edge >= dl_edge && v_edge >= dr_edge)
					dir_pred = a;
				else if (dl_edge >= dr_edge)
					dir_pred = d;
				else
					dir_pred = c;
				delta[3][j - 1] = src[k] - dir_pred;

				k++;
			}

			int[] limit = new int[4];
			for (int j = 0; j < 4; j++)
			{
				int[] current_delta = delta[j];

				int delta_min = current_delta[0];
				int delta_max = current_delta[0];
				for (k = 1; k < current_delta.length; k++)
				{
					if (current_delta[k] < delta_min)      delta_min = current_delta[k];
					else if (current_delta[k] > delta_max) delta_max = current_delta[k];
				}

				for (k = 0; k < current_delta.length; k++)
					current_delta[k] -= delta_min;
				int range = delta_max - delta_min;
				int[] frequency = new int[range + 1];
				for (k = 0; k < current_delta.length; k++)
					frequency[current_delta[k]]++;
				double shannon_limit = CodeMapper.getShannonLimit(frequency);
				limit[j] = (int) Math.floor(shannon_limit);
			}

			int value = limit[0];
			int index = 0;
			for (k = 1; k < 4; k++)
			{
				if (limit[k] < value) { value = limit[k]; index = k; }
			}
			map[i - 1] = (byte) index;
		}

		int[] dst       = new int[xdim * ydim];
		int init_value  = src[0];
		// The original src[0] (init_value changes below).
		int original_init_value = src[0];
		int sum         = 0;
		int k           = 0;

		dst[k++] = 0;
		int delta = src[k] - init_value;
		int value = src[k];
		dst[k++] = delta;
		sum += Math.abs(delta);

		for (int i = 2; i < xdim; i++)
		{
			delta = src[k] - value;
			value = src[k];
			dst[k++] = delta;
			sum += Math.abs(delta);
		}

		for (int i = 1; i < ydim; i++)
		{
			delta      = src[k] - init_value;
			init_value = src[k];
			dst[k++]   = delta;
			sum       += Math.abs(delta);

			byte m = map[i - 1];

			if (m == 0)
			{
				for (int j = 1; j < xdim - 1; j++) { delta = src[k]-src[k-1]; dst[k++] = delta; sum += Math.abs(delta); }
				delta = src[k] - src[k-1]; dst[k++] = delta; sum += Math.abs(delta);
			}
			else if (m == 1)
			{
				for (int j = 1; j < xdim - 1; j++) { delta = src[k]-(src[k-1]+src[k-xdim])/2; dst[k++] = delta; sum += Math.abs(delta); }
				delta = src[k] - src[k-1]; dst[k++] = delta; sum += Math.abs(delta);
			}
			else if (m == 2)
			{
				for (int j = 1; j < xdim - 1; j++)
				{
					int a = src[k - 1];
					int b = src[k - xdim];
					int c = src[k - xdim - 1];

					int pred;
					if (c >= Math.max(a, b))
						pred = Math.min(a, b);
					else if (c <= Math.min(a, b))
						pred = Math.max(a, b);
					else
						pred = a + b - c;

					delta = src[k] - pred;
					dst[k++] = delta;
					sum += Math.abs(delta);
				}
				delta = src[k] - src[k-1]; dst[k++] = delta; sum += Math.abs(delta);
			}
			else if (m == 3)
			{
				for (int j = 1; j < xdim - 1; j++)
				{
					int a = src[k - 1];
					int b = src[k - xdim];
					int c = src[k - xdim - 1];
					int d = src[k - xdim + 1];

					int h_edge  = Math.abs(b - c) + Math.abs(b - d);
					int v_edge  = Math.abs(a - c) + Math.abs(a - b);
					int dl_edge = Math.abs(c - d);
					int dr_edge = Math.abs(a - d);

					int pred;
					if (h_edge >= v_edge && h_edge >= dl_edge && h_edge >= dr_edge)
						pred = b;
					else if (v_edge >= dl_edge && v_edge >= dr_edge)
						pred = a;
					else if (dl_edge >= dr_edge)
						pred = d;
					else
						pred = c;

					delta = src[k] - pred;
					dst[k++] = delta;
					sum += Math.abs(delta);
				}
				// Last column: MED fallback
				{
					int a = src[k - 1];
					int b = src[k - xdim];
					int c = src[k - xdim - 1];

					int pred;
					if (c >= Math.max(a, b))
						pred = Math.min(a, b);
					else if (c <= Math.min(a, b))
						pred = Math.max(a, b);
					else
						pred = a + b - c;

					delta = src[k] - pred;
					dst[k++] = delta;
					sum += Math.abs(delta);
				}
			}
		}

		ArrayList result = new ArrayList();
		result.add(sum);
		result.add(dst);
		result.add(map);
		result.add(original_init_value);
		return result;
	}

	public static int[] getValuesFromMixedDeltas4(int[] src, int xdim, int ydim, int init_value, byte[] map)
	{
		int[] dst   = new int[xdim * ydim];
		int   k     = 0;
		int   value = init_value;

		dst[k++] = init_value;
		for (int i = 1; i < xdim; i++) { value += src[k]; dst[k++] = value; }

		for (int i = 1; i < ydim; i++)
		{
			init_value += src[k];
			dst[k++] = init_value;

			int m = map[i - 1];

			if (m == 0)
			{
				for (int j = 1; j < xdim - 1; j++) { value = dst[k-1] + src[k]; dst[k++] = value; }
			}
			else if (m == 1)
			{
				for (int j = 1; j < xdim - 1; j++) { value = (dst[k-1]+dst[k-xdim])/2 + src[k]; dst[k++] = value; }
			}
			else if (m == 2)
			{
				for (int j = 1; j < xdim - 1; j++)
				{
					int a = dst[k - 1];
					int b = dst[k - xdim];
					int c = dst[k - xdim - 1];

					int pred;
					if (c >= Math.max(a, b))
						pred = Math.min(a, b);
					else if (c <= Math.min(a, b))
						pred = Math.max(a, b);
					else
						pred = a + b - c;

					value = pred + src[k];
					dst[k++] = value;
				}
			}
			else if (m == 3)
			{
				for (int j = 1; j < xdim - 1; j++)
				{
					int a = dst[k - 1];
					int b = dst[k - xdim];
					int c = dst[k - xdim - 1];
					int d = dst[k - xdim + 1];

					int h_edge  = Math.abs(b - c) + Math.abs(b - d);
					int v_edge  = Math.abs(a - c) + Math.abs(a - b);
					int dl_edge = Math.abs(c - d);
					int dr_edge = Math.abs(a - d);

					int pred;
					if (h_edge >= v_edge && h_edge >= dl_edge && h_edge >= dr_edge)
						pred = b;
					else if (v_edge >= dl_edge && v_edge >= dr_edge)
						pred = a;
					else if (dl_edge >= dr_edge)
						pred = d;
					else
						pred = c;

					value = pred + src[k];
					dst[k++] = value;
				}
				// Last column: MED fallback
				{
					int a = dst[k - 1];
					int b = dst[k - xdim];
					int c = dst[k - xdim - 1];

					int pred;
					if (c >= Math.max(a, b))
						pred = Math.min(a, b);
					else if (c <= Math.min(a, b))
						pred = Math.max(a, b);
					else
						pred = a + b - c;

					value = pred + src[k];
					dst[k++] = value;
				}
				continue;
			}

			value = dst[k-1] + src[k];
			dst[k++] = value;
		}

		return dst;
	}

	// =========================================================================
	// Scanline (4) -- per-row selection from 16 predictors
	// =========================================================================

	private static int pred16(int a, int b, int c, int d, int p)
	{
		switch (p)
		{
			case  0: return a;                                                              // left
			case  1: return b;                                                              // above
			case  2: return c;                                                              // above-left
			case  3: return d;                                                              // above-right
			case  4: return (a + b) >> 1;                                                  // avg left+above
			case  5: return (b + c) >> 1;                                                  // avg above+above-left
			case  6: return (a + c) >> 1;                                                  // avg left+above-left
			case  7: return (b + d) >> 1;                                                  // avg above+above-right
			case  8: return (c + d) >> 1;                                                  // avg above-left+above-right
			case  9: return (a + b + c + d + 2) >> 2;                                     // avg all four
			case 10: return a + b - c;                                                     // gradient (lossless JPEG)
			case 11: {                                                                      // MED
				if (c >= Math.max(a, b)) return Math.min(a, b);
				if (c <= Math.min(a, b)) return Math.max(a, b);
				return a + b - c;
			}
			case 12: return (a * 3 + b + 2) >> 2;                                         // weighted 3:1 left:above
			case 13: return (a + b * 3 + 2) >> 2;                                         // weighted 1:3 left:above
			case 14: return (a * 3 + d + 2) >> 2;                                         // weighted left+above-right
			case 15: return (b * 3 + a + 2) >> 2;                                         // weighted above+left
			default: return a;
		}
	}

	public static ArrayList<int[]> getMixedDeltas16Frequency(int[] src, int xdim, int ydim)
	{
		int[] delta_freq = new int[511];
		int[] map_freq   = new int[16];

		for (int row = 0; row < ydim; row++)
		{
			int best_pred = 0, best_sad = Integer.MAX_VALUE;
			for (int p = 0; p < 16; p++)
			{
				int sad = 0;
				for (int col = 0; col < xdim; col++)
				{
					int k = row * xdim + col;
					if (k == 0) continue;
					int a = (col > 0)                    ? src[k - 1]        : 0;
					int b = (row > 0)                    ? src[k - xdim]     : 0;
					int c = (row > 0 && col > 0)         ? src[k - xdim - 1] : 0;
					int d = (row > 0 && col < xdim - 1) ? src[k - xdim + 1] : 0;
					sad += Math.abs(src[k] - pred16(a, b, c, d, p));
				}
				if (sad < best_sad) { best_sad = sad; best_pred = p; }
			}
			map_freq[best_pred]++;
			for (int col = 0; col < xdim; col++)
			{
				int k = row * xdim + col;
				if (k == 0) continue;
				int a = (col > 0)                    ? src[k - 1]        : 0;
				int b = (row > 0)                    ? src[k - xdim]     : 0;
				int c = (row > 0 && col > 0)         ? src[k - xdim - 1] : 0;
				int d = (row > 0 && col < xdim - 1) ? src[k - xdim + 1] : 0;
				int delta = src[k] - pred16(a, b, c, d, best_pred);
				int idx   = delta + 255;
				if (idx >= 0 && idx < 511) delta_freq[idx]++;
			}
		}
		ArrayList<int[]> result = new ArrayList<>();
		result.add(delta_freq);
		result.add(map_freq);
		return result;
	}

	public static ArrayList getMixedDeltasFromValues16Rows(int[] src, int xdim, int ydim)
	{
		int[]  dst = new int[xdim * ydim];
		byte[] map = new byte[ydim];   // one entry per row, value 0-15

		dst[0] = 0;
		for (int row = 0; row < ydim; row++)
		{
			int best_pred = 0, best_sad = Integer.MAX_VALUE;
			for (int p = 0; p < 16; p++)
			{
				int sad = 0;
				for (int col = 0; col < xdim; col++)
				{
					int k = row * xdim + col;
					if (k == 0) continue;
					int a = (col > 0)                    ? src[k - 1]         : 0;
					int b = (row > 0)                    ? src[k - xdim]      : 0;
					int c = (row > 0 && col > 0)         ? src[k - xdim - 1]  : 0;
					int d = (row > 0 && col < xdim - 1) ? src[k - xdim + 1]  : 0;
					sad += Math.abs(src[k] - pred16(a, b, c, d, p));
				}
				if (sad < best_sad) { best_sad = sad; best_pred = p; }
			}
			map[row] = (byte) best_pred;
			for (int col = 0; col < xdim; col++)
			{
				int k = row * xdim + col;
				if (k == 0) continue;
				int a = (col > 0)                    ? src[k - 1]         : 0;
				int b = (row > 0)                    ? src[k - xdim]      : 0;
				int c = (row > 0 && col > 0)         ? src[k - xdim - 1]  : 0;
				int d = (row > 0 && col < xdim - 1) ? src[k - xdim + 1]  : 0;
				dst[k] = src[k] - pred16(a, b, c, d, best_pred);
			}
		}
		int total = 0;
		for (int v : dst) total += Math.abs(v);

		ArrayList result = new ArrayList();
		result.add(total);
		result.add(dst);
		result.add(map);
		result.add(src[0]);
		return result;
	}

	public static int[] getValuesFromMixedDeltas16Rows(int[] src, int xdim, int ydim, int init_value, byte[] map)
	{
		int[] dst = new int[xdim * ydim];
		dst[0] = init_value;

		for (int row = 0; row < ydim; row++)
		{
			int p = map[row] & 0xF;
			for (int col = 0; col < xdim; col++)
			{
				int k = row * xdim + col;
				if (k == 0) continue;
				int a = (col > 0)                    ? dst[k - 1]         : 0;
				int b = (row > 0)                    ? dst[k - xdim]      : 0;
				int c = (row > 0 && col > 0)         ? dst[k - xdim - 1]  : 0;
				int d = (row > 0 && col < xdim - 1) ? dst[k - xdim + 1]  : 0;
				dst[k] = src[k] + pred16(a, b, c, d, p);
			}
		}
		return dst;
	}

	// =========================================================================
	// Bilateral smoothing -- preserves edges, suppresses noise.
	// threshold 0 = no-op; range sigma = threshold^2.
	// =========================================================================
	public static int[] bilateralSmooth(int[] src, int xdim, int ydim, int threshold)
	{
		if (threshold == 0) return src.clone();

		double sigma_r = threshold * threshold;  // quadratic: 1,4,9,16,25 for threshold 1-5
		double sigma_s = 1.5;                // spatial sigma (fixed, 5x5 kernel)
		int    radius  = 2;

		// Range weight lookup: indexed by absolute intensity difference 0-255
		double[] rw = new double[256];
		double   r2 = 2.0 * sigma_r * sigma_r;
		for (int d = 0; d < 256; d++) rw[d] = Math.exp(-(d * d) / r2);

		// Spatial weight kernel
		int    ksize = 2 * radius + 1;
		double[][]  sw = new double[ksize][ksize];
		double s2 = 2.0 * sigma_s * sigma_s;
		for (int dy = -radius; dy <= radius; dy++)
			for (int dx = -radius; dx <= radius; dx++)
				sw[dy + radius][dx + radius] = Math.exp(-(dx * dx + dy * dy) / s2);

		int[] dst = new int[src.length];
		for (int row = 0; row < ydim; row++)
		{
			for (int col = 0; col < xdim; col++)
			{
				int    center = src[row * xdim + col];
				double sum_w  = 0.0, sum_v = 0.0;
				for (int dy = -radius; dy <= radius; dy++)
				{
					int ny = row + dy;
					if (ny < 0 || ny >= ydim) continue;
					for (int dx = -radius; dx <= radius; dx++)
					{
						int nx = col + dx;
						if (nx < 0 || nx >= xdim) continue;
						int    v = src[ny * xdim + nx];
						double w = sw[dy + radius][dx + radius] * rw[Math.abs(v - center)];
						sum_w += w; sum_v += w * v;
					}
				}
				dst[row * xdim + col] = (int) Math.round(sum_v / sum_w);
			}
		}
		return dst;
	}


	// =========================================================================
	// Anisotropic diffusion (Perona-Malik) -- iterative edge-preserving smooth.
	// threshold 0 = no-op; iterations = threshold, K = threshold*3+5 (8-35).
	// lambda = 0.25 (stability limit for 4-directional scheme).
	// =========================================================================
	public static int[] anisotropicSmooth(int[] src, int xdim, int ydim, int threshold)
	{
		if (threshold == 0) return src.clone();

		int    iterations = threshold;
		double K2         = (threshold * 3.0 + 5.0) * (threshold * 3.0 + 5.0);
		double lambda     = 0.25;

		// Conductance lookup: c[d+255] = exp(-d^2/K^2) for d in [-255,255]
		double[] c = new double[511];
		for (int d = -255; d <= 255; d++) c[d + 255] = Math.exp(-(d * d) / K2);

		double[] img  = new double[src.length];
		double[] next = new double[src.length];
		for (int i = 0; i < src.length; i++) img[i] = src[i];

		for (int iter = 0; iter < iterations; iter++)
		{
			for (int row = 0; row < ydim; row++)
			{
				for (int col = 0; col < xdim; col++)
				{
					int    k  = row * xdim + col;
					double v  = img[k];
					double dN = (row > 0)        ? img[k - xdim] - v : 0;
					double dS = (row < ydim - 1) ? img[k + xdim] - v : 0;
					double dE = (col < xdim - 1) ? img[k + 1]    - v : 0;
					double dW = (col > 0)        ? img[k - 1]    - v : 0;
					int iN = Math.max(0, Math.min(510, (int)(dN + 255.5)));
					int iS = Math.max(0, Math.min(510, (int)(dS + 255.5)));
					int iE = Math.max(0, Math.min(510, (int)(dE + 255.5)));
					int iW = Math.max(0, Math.min(510, (int)(dW + 255.5)));
					next[k] = v + lambda * (c[iN]*dN + c[iS]*dS + c[iE]*dE + c[iW]*dW);
				}
			}
			double[] tmp = img; img = next; next = tmp;
		}

		int[] dst = new int[src.length];
		for (int i = 0; i < src.length; i++)
			dst[i] = Math.max(0, Math.min(255, (int) Math.round(img[i])));
		return dst;
	}

	// =========================================================================

	// Each row maps local predictor index 0-7 to a pred16 index.
	// Add new rows here to define new variants; variant 0 is the default.
	public static final int[][] FILTER_SETS_8 = {
		// variant 0: spread coverage -- directional anchors + best composites
		{ 0, 1, 2, 3, 4, 10, 9, 5 },
		// variant 1: averaging focus -- left, above, avg(l,a), avg-all-4, gradient, MED, weighted blends
		{ 0, 1, 4, 9, 10, 11, 12, 13 },
		// variant 2: variant 1 with weighted 1:3 swapped for avg(above, above-right)
		{ 0, 1, 4, 9, 10, 11, 12, 7 },
	};

	public static int pred8(int a, int b, int c, int d, int p, int variant)
	{
		return pred16(a, b, c, d, FILTER_SETS_8[variant][p]);
	}

	public static ArrayList<int[]> getMixedDeltas8Frequency(int[] src, int xdim, int ydim, int variant)
	{
		int[] delta_freq = new int[511];
		int[] map_freq   = new int[8];

		for (int row = 0; row < ydim; row++)
		{
			int best_pred = 0, best_sad = Integer.MAX_VALUE;
			for (int p = 0; p < 8; p++)
			{
				int sad = 0;
				for (int col = 0; col < xdim; col++)
				{
					int k = row * xdim + col;
					if (k == 0) continue;
					int a = (col > 0)                    ? src[k - 1]        : 0;
					int b = (row > 0)                    ? src[k - xdim]     : 0;
					int c = (row > 0 && col > 0)         ? src[k - xdim - 1] : 0;
					int d = (row > 0 && col < xdim - 1) ? src[k - xdim + 1] : 0;
					sad += Math.abs(src[k] - pred8(a, b, c, d, p, variant));
				}
				if (sad < best_sad) { best_sad = sad; best_pred = p; }
			}
			map_freq[best_pred]++;
			for (int col = 0; col < xdim; col++)
			{
				int k = row * xdim + col;
				if (k == 0) continue;
				int a = (col > 0)                    ? src[k - 1]        : 0;
				int b = (row > 0)                    ? src[k - xdim]     : 0;
				int c = (row > 0 && col > 0)         ? src[k - xdim - 1] : 0;
				int d = (row > 0 && col < xdim - 1) ? src[k - xdim + 1] : 0;
				int delta = src[k] - pred8(a, b, c, d, best_pred, variant);
				int idx   = delta + 255;
				if (idx >= 0 && idx < 511) delta_freq[idx]++;
			}
		}
		ArrayList<int[]> result = new ArrayList<>();
		result.add(delta_freq);
		result.add(map_freq);
		return result;
	}

	public static ArrayList getMixedDeltasFromValues8Rows(int[] src, int xdim, int ydim, int variant)
	{
		int[]  dst = new int[xdim * ydim];
		byte[] map = new byte[ydim];

		dst[0] = 0;
		for (int row = 0; row < ydim; row++)
		{
			int best_pred = 0, best_sad = Integer.MAX_VALUE;
			for (int p = 0; p < 8; p++)
			{
				int sad = 0;
				for (int col = 0; col < xdim; col++)
				{
					int k = row * xdim + col;
					if (k == 0) continue;
					int a = (col > 0)                    ? src[k - 1]        : 0;
					int b = (row > 0)                    ? src[k - xdim]     : 0;
					int c = (row > 0 && col > 0)         ? src[k - xdim - 1] : 0;
					int d = (row > 0 && col < xdim - 1) ? src[k - xdim + 1] : 0;
					sad += Math.abs(src[k] - pred8(a, b, c, d, p, variant));
				}
				if (sad < best_sad) { best_sad = sad; best_pred = p; }
			}
			map[row] = (byte) best_pred;
			for (int col = 0; col < xdim; col++)
			{
				int k = row * xdim + col;
				if (k == 0) continue;
				int a = (col > 0)                    ? src[k - 1]        : 0;
				int b = (row > 0)                    ? src[k - xdim]     : 0;
				int c = (row > 0 && col > 0)         ? src[k - xdim - 1] : 0;
				int d = (row > 0 && col < xdim - 1) ? src[k - xdim + 1] : 0;
				dst[k] = src[k] - pred8(a, b, c, d, best_pred, variant);
			}
		}
		int total = 0;
		for (int v : dst) total += Math.abs(v);

		ArrayList result = new ArrayList();
		result.add(total);
		result.add(dst);
		result.add(map);
		result.add(src[0]);
		return result;
	}

	public static int[] getValuesFromMixedDeltas8Rows(int[] src, int xdim, int ydim,
	                                                   int init_value, byte[] map, int variant)
	{
		int[] dst = new int[xdim * ydim];
		dst[0] = init_value;

		for (int row = 0; row < ydim; row++)
		{
			int p = map[row] & 0x7;
			for (int col = 0; col < xdim; col++)
			{
				int k = row * xdim + col;
				if (k == 0) continue;
				int a = (col > 0)                    ? dst[k - 1]        : 0;
				int b = (row > 0)                    ? dst[k - xdim]     : 0;
				int c = (row > 0 && col > 0)         ? dst[k - xdim - 1] : 0;
				int d = (row > 0 && col < xdim - 1) ? dst[k - xdim + 1] : 0;
				dst[k] = src[k] + pred8(a, b, c, d, p, variant);
			}
		}
		return dst;
	}

	// =========================================================================
	// Ideal delta helpers (pixel-map variants)
	// =========================================================================

	public static ArrayList getIdealDeltasFromValues(int src[], int xdim, int ydim)
	{
		int[] dst  = new int[xdim * ydim];
		byte[] map = new byte[xdim * (ydim - 1)];

		int init_value = src[0];
		int m = 0;

		for(int i = 1; i < ydim - 1; i++)
		{
			for(int j = 0; j < xdim; j++)
			{
				int k = i * xdim + j;
				if(j == 0)
				{
					map[m++] = (Math.abs(src[k]-src[k-xdim]) <= Math.abs(src[k]-src[k-xdim+1])) ? (byte)1 : (byte)3;
				}
				else if(j < xdim - 1)
				{
					int da = Math.abs(src[k]-src[k-1]);
					int db = Math.abs(src[k]-src[k-xdim]);
					int dc = Math.abs(src[k]-src[k-xdim-1]);
					int dd = Math.abs(src[k]-src[k-xdim+1]);
					if     (da<=db && da<=dc && da<=dd) map[m++] = 0;
					else if(db<=dc && db<=dd)           map[m++] = 1;
					else if(dc<=dd)                     map[m++] = 2;
					else                                map[m++] = 3;
				}
				else
				{
					int da = Math.abs(src[k]-src[k-1]);
					int db = Math.abs(src[k]-src[k-xdim]);
					int dc = Math.abs(src[k]-src[k-xdim-1]);
					if     (da<=db && da<=dc) map[m++] = 0;
					else if(db<=dc)           map[m++] = 1;
					else                      map[m++] = 2;
				}
			}
		}

		int k = xdim * (ydim - 1);
		for(int j = 0; j < xdim; j++)
		{
			if(j == 0)
			{
				map[m++] = (Math.abs(src[k]-src[k-xdim]) <= Math.abs(src[k]-src[k-xdim+1])) ? (byte)1 : (byte)3;
			}
			else if(j < xdim - 1)
			{
				int da = Math.abs(src[k]-src[k-1]);
				int db = Math.abs(src[k]-src[k-xdim]);
				int dc = Math.abs(src[k]-src[k-xdim-1]);
				int dd = Math.abs(src[k]-src[k-xdim+1]);
				if     (da<=db && da<=dc && da<=dd) map[m++] = 0;
				else if(db<=dc && db<=dd)           map[m++] = 1;
				else if(dc<=dd)                     map[m++] = 2;
				else                                map[m++] = 3;
			}
			else
			{
				int da = Math.abs(src[k]-src[k-1]);
				int db = Math.abs(src[k]-src[k-xdim]);
				int dc = Math.abs(src[k]-src[k-xdim-1]);
				if     (da<=db && da<=dc) map[m++] = 0;
				else if(db<=dc)           map[m++] = 1;
				else                      map[m++] = 2;
			}
			k++;
		}

		k = 0; m = 0;
		int delta = 0;
		for(int i = 0; i < ydim; i++)
		{
			if(i == 0)
			{
				for(int j = 0; j < xdim; j++)
				{
					if(j == 0) dst[k++] = delta;
					else { delta = src[k] - src[k-1]; dst[k++] = delta; }
				}
			}
			else
			{
				for(int j = 0; j < xdim; j++)
				{
					int n = map[m++];
					if(n == 0)      delta = src[k] - src[k-1];
					else if(n == 1) delta = src[k] - src[k-xdim];
					else if(n == 2) delta = src[k] - src[k-xdim-1];
					else            delta = src[k] - src[k-xdim+1];
					dst[k++] = delta;
				}
			}
		}

		ArrayList result = new ArrayList();
		result.add(0);
		result.add(dst);
		result.add(map);
		result.add(init_value);
		return result;
	}

	public static int[] getValuesFromIdealDeltas(int[] src, int xdim, int ydim, int init_value, byte[] map)
	{
		int[] dst = new int[xdim * ydim];
		int k = 0;
		dst[k++] = init_value;

		for(int i = 1; i < xdim; i++) { dst[k] = dst[k-1] + src[k]; k++; }

		int m = 0;
		for(int i = 1; i < ydim; i++)
		{
			for(int j = 0; j < xdim; j++)
			{
				int n = map[m++];
				if(n == 0)      dst[k] = dst[k-1]      + src[k];
				else if(n == 1) dst[k] = dst[k-xdim]   + src[k];
				else if(n == 2) dst[k] = dst[k-xdim-1] + src[k];
				else            dst[k] = dst[k-xdim+1] + src[k];
				k++;
			}
		}
		return dst;
	}

	public static ArrayList getIdealDeltasFromValues2(int src[], int xdim, int ydim)
	{
		int[] dst  = new int[xdim * ydim];
		byte[] map = new byte[(xdim - 2) * (ydim - 1)];
		int init_value = src[0];

		int m = 0;
		for(int i = 1; i < ydim - 1; i++)
		{
			for(int j = 1; j < xdim - 1; j++)
			{
				int k = i * xdim + j;
				map[m++] = (Math.abs(src[k]-src[k-1]) <= Math.abs(src[k]-src[k-xdim])) ? (byte)0 : (byte)1;
			}
		}

		int k = 0; m = 0; int sum = 0;
		for(int i = 0; i < ydim; i++)
		{
			if(i == 0)
			{
				dst[k++] = 0;
				for(int j = 1; j < xdim; j++) { int delta = src[k]-src[k-1]; dst[k++] = delta; sum += Math.abs(delta); }
			}
			else
			{
				int delta = src[k]-src[k-xdim]; dst[k++] = delta; sum += Math.abs(delta);
				for(int j = 1; j < xdim - 1; j++)
				{
					int n = map[m++];
					delta = (n==0) ? src[k]-src[k-1] : src[k]-src[k-xdim];
					dst[k++] = delta; sum += Math.abs(delta);
				}
				delta = src[k]-src[k-1]; dst[k++] = delta; sum += Math.abs(delta);
			}
		}

		ArrayList result = new ArrayList();
		result.add(sum); result.add(dst); result.add(map); result.add(init_value);
		return result;
	}

	public static int[] getValuesFromIdealDeltas2(int[] src, int xdim, int ydim, int init_value, byte[] map)
	{
		int[] dst = new int[xdim * ydim];
		int k = 0;
		dst[k++] = init_value;
		for(int i = 1; i < xdim; i++) { dst[k] = dst[k-1] + src[k]; k++; }

		int m = 0;
		for(int i = 1; i < ydim; i++)
		{
			init_value += src[k]; dst[k++] = init_value;
			for(int j = 1; j < xdim - 1; j++)
			{
				int n = map[m++];
				if(n == 0)      dst[k] = dst[k-1]               + src[k];
				else if(n == 1) dst[k] = dst[k-xdim]            + src[k];
				else            dst[k] = (dst[k-1]+dst[k-xdim]) / 2 + src[k];
				k++;
			}
			dst[k] = dst[k-1] + src[k]; k++;
		}
		return dst;
	}

	public static ArrayList getIdealDeltasFromValues3(int src[], int xdim, int ydim)
	{
		int[] dst  = new int[xdim * ydim];
		byte[] map = new byte[(xdim - 2) * (ydim - 1)];
		int init_value = src[0];

		int m = 0;
		for(int i = 1; i < ydim - 1; i++)
		{
			for(int j = 1; j < xdim - 1; j++)
			{
				int k = i * xdim + j;
				int[] delta = { src[k]-src[k-1], src[k]-src[k-xdim], src[k]-(src[k-1]+src[k-xdim])/2 };
				int value = Math.abs(delta[0]); int index = 0;
				for(int n = 1; n < 3; n++) { if(Math.abs(delta[n]) < value) { value = Math.abs(delta[n]); index = n; } }
				map[m++] = (byte) index;
			}
		}

		int k = 0; m = 0; int sum = 0;
		for(int i = 0; i < ydim; i++)
		{
			if(i == 0)
			{
				dst[k++] = 0;
				for(int j = 1; j < xdim; j++) { int delta = src[k]-src[k-1]; dst[k++] = delta; sum += Math.abs(delta); }
			}
			else
			{
				int delta = src[k]-src[k-xdim]; dst[k++] = delta; sum += Math.abs(delta);
				for(int j = 1; j < xdim - 1; j++)
				{
					int n = map[m++];
					if(n == 0)      delta = src[k]-src[k-1];
					else if(n == 1) delta = src[k]-src[k-xdim];
					else            delta = src[k]-(src[k-1]+src[k-xdim])/2;
					dst[k++] = delta; sum += Math.abs(delta);
				}
				delta = src[k]-src[k-1]; dst[k++] = delta; sum += Math.abs(delta);
			}
		}

		ArrayList result = new ArrayList();
		result.add(sum); result.add(dst); result.add(map); result.add(init_value);
		return result;
	}

	public static int[] getValuesFromIdealDeltas3(int[] src, int xdim, int ydim, int init_value, byte[] map)
	{
		int[] dst = new int[xdim * ydim];
		int k = 0;
		dst[k++] = init_value;
		for(int i = 1; i < xdim; i++) { dst[k] = dst[k-1] + src[k]; k++; }

		int m = 0;
		for(int i = 1; i < ydim; i++)
		{
			init_value += src[k]; dst[k++] = init_value;
			for(int j = 1; j < xdim - 1; j++)
			{
				int n = map[m++];
				if(n == 0)      dst[k] = dst[k-1]               + src[k];
				else if(n == 1) dst[k] = dst[k-xdim]            + src[k];
				else            dst[k] = (dst[k-1]+dst[k-xdim]) / 2 + src[k];
				k++;
			}
			dst[k] = dst[k-1] + src[k]; k++;
		}
		return dst;
	}

	public static ArrayList getIdealDeltasFromValues4(int src[], int xdim, int ydim)
	{
		int[] dst  = new int[xdim * ydim];
		byte[] map = new byte[(xdim - 2) * (ydim - 1)];
		int init_value = src[0];

		int m = 0;
		for(int i = 1; i < ydim - 1; i++)
		{
			for(int j = 1; j < xdim - 1; j++)
			{
				int k = i * xdim + j;
				int[] delta = {
					src[k]-src[k-1], src[k]-src[k-xdim],
					src[k]-(src[k-1]+src[k-xdim])/2, src[k]-(src[k-1]+src[k-xdim+1])/2
				};
				int value = Math.abs(delta[0]); int index = 0;
				for(int n = 1; n < 4; n++) { if(Math.abs(delta[n]) < value) { value = Math.abs(delta[n]); index = n; } }
				map[m++] = (byte) index;
			}
		}

		int k = 0; m = 0; int sum = 0;
		for(int i = 0; i < ydim; i++)
		{
			if(i == 0)
			{
				dst[k++] = 0;
				for(int j = 1; j < xdim; j++) { int delta = src[k]-src[k-1]; dst[k++] = delta; sum += Math.abs(delta); }
			}
			else
			{
				int delta = src[k]-src[k-xdim]; dst[k++] = delta; sum += Math.abs(delta);
				for(int j = 1; j < xdim - 1; j++)
				{
					int n = map[m++];
					if(n == 0)      delta = src[k]-src[k-1];
					else if(n == 1) delta = src[k]-src[k-xdim];
					else if(n == 2) delta = src[k]-(src[k-1]+src[k-xdim])/2;
					else            delta = src[k]-(src[k-1]+src[k-xdim+1])/2;
					dst[k++] = delta; sum += Math.abs(delta);
				}
				delta = src[k]-src[k-1]; dst[k++] = delta; sum += Math.abs(delta);
			}
		}

		ArrayList result = new ArrayList();
		result.add(sum); result.add(dst); result.add(map); result.add(init_value);
		return result;
	}

	public static int[] getValuesFromIdealDeltas4(int[] src, int xdim, int ydim, int init_value, byte[] map)
	{
		int[] dst = new int[xdim * ydim];
		int k = 0;
		dst[k++] = init_value;
		for(int i = 1; i < xdim; i++) { dst[k] = dst[k-1] + src[k]; k++; }

		int m = 0;
		for(int i = 1; i < ydim; i++)
		{
			init_value += src[k]; dst[k++] = init_value;
			for(int j = 1; j < xdim - 1; j++)
			{
				int n = map[m++];
				if(n == 0)      dst[k] = dst[k-1]                    + src[k];
				else if(n == 1) dst[k] = dst[k-xdim]                 + src[k];
				else if(n == 2) dst[k] = (dst[k-1]+dst[k-xdim])  / 2 + src[k];
				else            dst[k] = (dst[k-1]+dst[k-xdim+1]) / 2 + src[k];
				k++;
			}
			dst[k] = dst[k-1] + src[k]; k++;
		}
		return dst;
	}

	public static ArrayList getIdealDeltasFromValues5(int src[], int xdim, int ydim)
	{
		int[] dst  = new int[xdim * ydim];
		byte[] map = new byte[(xdim - 2) * (ydim - 1)];
		int init_value = src[0];

		int m = 0;
		for(int i = 1; i < ydim - 1; i++)
		{
			for(int j = 1; j < xdim - 1; j++)
			{
				int k = i * xdim + j;
				int[] delta = {
					src[k]-src[k-1], src[k]-src[k-xdim], src[k]-src[k-xdim-1],
					src[k]-(src[k-1]+src[k-xdim])/2, src[k]-(src[k-1]+src[k-xdim+1])/2
				};
				int value = Math.abs(delta[0]); int index = 0;
				for(int n = 1; n < 5; n++) { if(Math.abs(delta[n]) < value) { value = Math.abs(delta[n]); index = n; } }
				map[m++] = (byte) index;
			}
		}

		int k = 0; m = 0; int sum = 0;
		for(int i = 0; i < ydim; i++)
		{
			if(i == 0)
			{
				dst[k++] = 0;
				for(int j = 1; j < xdim; j++) { int delta = src[k]-src[k-1]; dst[k++] = delta; sum += Math.abs(delta); }
			}
			else
			{
				int delta = src[k]-src[k-xdim]; dst[k++] = delta; sum += Math.abs(delta);
				for(int j = 1; j < xdim - 1; j++)
				{
					int n = map[m++];
					if(n == 0)      delta = src[k]-src[k-1];
					else if(n == 1) delta = src[k]-src[k-xdim];
					else if(n == 2) delta = src[k]-src[k-xdim-1];
					else if(n == 3) delta = src[k]-(src[k-1]+src[k-xdim])/2;
					else            delta = src[k]-(src[k-1]+src[k-xdim+1])/2;
					dst[k++] = delta; sum += Math.abs(delta);
				}
				delta = src[k]-src[k-1]; dst[k++] = delta; sum += Math.abs(delta);
			}
		}

		ArrayList result = new ArrayList();
		result.add(sum); result.add(dst); result.add(map); result.add(init_value);
		return result;
	}

	public static int[] getValuesFromIdealDeltas5(int[] src, int xdim, int ydim, int init_value, byte[] map)
	{
		int[] dst = new int[xdim * ydim];
		int k = 0;
		dst[k++] = init_value;
		for(int i = 1; i < xdim; i++) { dst[k] = dst[k-1] + src[k]; k++; }

		int m = 0;
		for(int i = 1; i < ydim; i++)
		{
			init_value += src[k]; dst[k++] = init_value;
			for(int j = 1; j < xdim - 1; j++)
			{
				int n = map[m++];
				if(n == 0)      dst[k] = dst[k-1]                     + src[k];
				else if(n == 1) dst[k] = dst[k-xdim]                  + src[k];
				else if(n == 2) dst[k] = dst[k-xdim-1]                + src[k];
				else if(n == 3) dst[k] = (dst[k-1]+dst[k-xdim])   / 2 + src[k];
				else            dst[k] = (dst[k-1]+dst[k-xdim+1]) / 2 + src[k];
				k++;
			}
			dst[k] = dst[k-1] + src[k]; k++;
		}
		return dst;
	}

	public static ArrayList getIdealDeltasFromValues6(int src[], int xdim, int ydim)
	{
		int[] dst  = new int[xdim * ydim];
		byte[] map = new byte[(xdim - 2) * (ydim - 1)];
		int init_value = src[0];

		int m = 0;
		for(int i = 1; i < ydim - 1; i++)
		{
			for(int j = 1; j < xdim - 1; j++)
			{
				int k = i * xdim + j;
				double[] delta = {
					Math.abs(src[k]-src[k-1]),
					Math.abs(src[k]-(src[k-1]+src[k-xdim-1])/2),
					Math.abs(src[k]-src[k-xdim-1]),
					Math.abs(src[k]-(src[k-xdim-1]+src[k-xdim+1])/2),
					Math.abs(src[k]-src[k-xdim+1]),
					Math.abs(src[k]-(src[k-1]+src[k-xdim+1])/2)
				};

				double addend = 0.00000001;
				Hashtable<Double, Byte> delta_table = new Hashtable<Double, Byte>();
				ArrayList key_list = new ArrayList();
				key_list.add(delta[0]); delta_table.put(delta[0], (byte)0);
				for(k = 1; k < 6; k++)
				{
					if(key_list.contains(delta[k])) { delta[k] += addend; addend *= 2.; }
					key_list.add(delta[k]); delta_table.put(delta[k], (byte)k);
				}
				Collections.sort(key_list);
				map[m++] = delta_table.get((double)key_list.get(0));
			}
		}

		int k = 0; m = 0; int sum = 0;
		for(int i = 0; i < ydim; i++)
		{
			if(i == 0)
			{
				dst[k++] = 0;
				for(int j = 1; j < xdim; j++) { int delta = src[k]-src[k-1]; dst[k++] = delta; sum += Math.abs(delta); }
			}
			else
			{
				int delta = src[k]-src[k-xdim]; dst[k++] = delta; sum += Math.abs(delta);
				for(int j = 1; j < xdim - 1; j++)
				{
					int n = map[m++];
					if(n == 0)      delta = src[k]-src[k-1];
					else if(n == 1) delta = src[k]-(src[k-1]+src[k-xdim-1])/2;
					else if(n == 2) delta = src[k]-src[k-xdim-1];
					else if(n == 3) delta = src[k]-(src[k-xdim-1]+src[k-xdim+1])/2;
					else if(n == 4) delta = src[k]-src[k-xdim+1];
					else            delta = src[k]-(src[k-1]+src[k-xdim+1])/2;
					dst[k++] = delta; sum += Math.abs(delta);
				}
				delta = src[k]-src[k-1]; dst[k++] = delta; sum += Math.abs(delta);
			}
		}

		ArrayList result = new ArrayList();
		result.add(sum); result.add(dst); result.add(map); result.add(init_value);
		return result;
	}

	public static int[] getValuesFromIdealDeltas6(int[] src, int xdim, int ydim, int init_value, byte[] map)
	{
		int[] dst = new int[xdim * ydim];
		int k = 0;
		dst[k++] = init_value;
		for(int i = 1; i < xdim; i++) { dst[k] = dst[k-1] + src[k]; k++; }

		int m = 0;
		for(int i = 1; i < ydim; i++)
		{
			init_value += src[k]; dst[k++] = init_value;
			for(int j = 1; j < xdim - 1; j++)
			{
				int n = map[m++];
				if(n == 0)      dst[k] = dst[k-1]                         + src[k];
				else if(n == 1) dst[k] = (dst[k-1]+dst[k-xdim-1])   / 2  + src[k];
				else if(n == 2) dst[k] = dst[k-xdim-1]                    + src[k];
				else if(n == 3) dst[k] = (dst[k-xdim-1]+dst[k-xdim+1])/2 + src[k];
				else if(n == 4) dst[k] = dst[k-xdim+1]                    + src[k];
				else            dst[k] = (dst[k-1]+dst[k-xdim+1])   / 2  + src[k];
				k++;
			}
			dst[k] = dst[k-1] + src[k]; k++;
		}
		return dst;
	}

	public static ArrayList getIdealDeltasFromValues8(int src[], int xdim, int ydim)
	{
		int[] dst  = new int[xdim * ydim];
		byte[] map = new byte[(xdim - 2) * (ydim - 1)];
		int init_value = src[0];

		int m = 0;
		for(int i = 1; i < ydim - 1; i++)
		{
			for(int j = 1; j < xdim - 1; j++)
			{
				int k = i * xdim + j;
				double[] delta = {
					Math.abs(src[k]-src[k-1]),
					Math.abs(src[k]-(src[k-1]+src[k-xdim-1])/2),
					Math.abs(src[k]-src[k-xdim-1]),
					Math.abs(src[k]-(src[k-xdim-1]+src[k-xdim])/2),
					Math.abs(src[k]-src[k-xdim]),
					Math.abs(src[k]-(src[k-xdim]+src[k-xdim+1])/2),
					Math.abs(src[k]-src[k-xdim+1]),
					Math.abs(src[k]-(src[k-xdim+1]+src[k-1])/2)
				};

				double addend = 0.00000001;
				Hashtable<Double, Byte> delta_table = new Hashtable<Double, Byte>();
				ArrayList key_list = new ArrayList();
				key_list.add(delta[0]); delta_table.put(delta[0], (byte)0);
				for(k = 1; k < 8; k++)
				{
					if(key_list.contains(delta[k])) { delta[k] += addend; addend *= 2.; }
					key_list.add(delta[k]); delta_table.put(delta[k], (byte)k);
				}
				Collections.sort(key_list);
				map[m++] = delta_table.get((double)key_list.get(0));
			}
		}

		int k = 0; m = 0; int sum = 0;
		for(int i = 0; i < ydim; i++)
		{
			if(i == 0)
			{
				dst[k++] = 0;
				for(int j = 1; j < xdim; j++) { int delta = src[k]-src[k-1]; dst[k++] = delta; sum += Math.abs(delta); }
			}
			else
			{
				int delta = src[k]-src[k-xdim]; dst[k++] = delta; sum += Math.abs(delta);
				for(int j = 1; j < xdim - 1; j++)
				{
					int n = map[m++];
					if(n == 0)      delta = src[k]-src[k-1];
					else if(n == 1) delta = src[k]-(src[k-1]+src[k-xdim-1])/2;
					else if(n == 2) delta = src[k]-src[k-xdim-1];
					else if(n == 3) delta = src[k]-(src[k-xdim-1]+src[k-xdim])/2;
					else if(n == 4) delta = src[k]-src[k-xdim];
					else if(n == 5) delta = src[k]-(src[k-xdim]+src[k-xdim+1])/2;
					else if(n == 6) delta = src[k]-src[k-xdim+1];
					else            delta = src[k]-(src[k-xdim+1]+src[k-1])/2;
					dst[k++] = delta; sum += Math.abs(delta);
				}
				delta = src[k]-src[k-1]; dst[k++] = delta; sum += Math.abs(delta);
			}
		}

		ArrayList result = new ArrayList();
		result.add(sum); result.add(dst); result.add(map); result.add(init_value);
		return result;
	}

	public static int[] getValuesFromIdealDeltas8(int[] src, int xdim, int ydim, int init_value, byte[] map)
	{
		int[] dst = new int[xdim * ydim];
		int k = 0;
		dst[k++] = init_value;
		for(int i = 1; i < xdim; i++) { dst[k] = dst[k-1] + src[k]; k++; }

		int m = 0;
		for(int i = 1; i < ydim; i++)
		{
			init_value += src[k]; dst[k++] = init_value;
			for(int j = 1; j < xdim - 1; j++)
			{
				int n = map[m++];
				if(n == 0)      dst[k] = dst[k-1]                          + src[k];
				else if(n == 1) dst[k] = (dst[k-1]+dst[k-xdim-1])    / 2  + src[k];
				else if(n == 2) dst[k] = dst[k-xdim-1]                     + src[k];
				else if(n == 3) dst[k] = (dst[k-xdim-1]+dst[k-xdim]) / 2  + src[k];
				else if(n == 4) dst[k] = dst[k-xdim]                       + src[k];
				else if(n == 5) dst[k] = (dst[k-xdim]+dst[k-xdim+1]) / 2  + src[k];
				else if(n == 6) dst[k] = dst[k-xdim+1]                     + src[k];
				else            dst[k] = (dst[k-xdim+1]+dst[k-1])    / 2  + src[k];
				k++;
			}
			dst[k] = dst[k-1] + src[k]; k++;
		}
		return dst;
	}


	// -------------------------------------------------------------------------
	// 16-option ideal delta encoder/decoder all predictors are causal.
	//
	// Predictor set (a=left, b=above, c=above-left, d=above-right):
	//
	//   0  a                    8  (a+b)>>1
	//   1  c                    9  (c+d)>>1
	//   2  b                   10  (a+b+c+d)>>2
	//   3  d                   11  MED(a,b,c)
	//   4  (a+c)>>1            12  (a+b+c)>>2
	//   5  (c+b)>>1            13  (a+b+d)>>2
	//   6  (b+d)>>1            14  (a+c+d)>>2
	//   7  (d+a)>>1            15  (b+c+d)>>2
	//
	// Map covers rows 1..ydim-1, cols 1..xdim-2.
	// Map entries stored as raw bytes (value 0-15, no bit-packing).
	// -------------------------------------------------------------------------
	public static ArrayList getIdealDeltasFromValues16(int src[], int xdim, int ydim)
	{
		int[]  dst        = new int[xdim * ydim];
		// Map covers all non-first rows, interior columns.
		byte[] map        = new byte[(xdim - 2) * (ydim - 1)];
		int    init_value = src[0];
		int    sum        = 0;
		int    m          = 0;

		// Pass 1: choose best of 16 causal predictors for each map pixel.
		for (int i = 1; i < ydim; i++)
		{
			for (int j = 1; j < xdim - 1; j++)
			{
				int k = i * xdim + j;
				int a = src[k - 1];           // left
				int b = src[k - xdim];        // above
				int c = src[k - xdim - 1];    // above-left
				int d = src[k - xdim + 1];    // above-right
				int e = src[k];

				int med;
				if (c >= Math.max(a, b))      med = Math.min(a, b);
				else if (c <= Math.min(a, b)) med = Math.max(a, b);
				else                          med = a + b - c;

				int[] pred = {
					a,                   //  0: left
					c,                   //  1: above-left
					b,                   //  2: above
					d,                   //  3: above-right
					(a + c) >> 1,        //  4: avg(left, above-left)
					(c + b) >> 1,        //  5: avg(above-left, above)
					(b + d) >> 1,        //  6: avg(above, above-right)
					(d + a) >> 1,        //  7: avg(above-right, left)
					(a + b) >> 1,        //  8: avg(left, above)
					(c + d) >> 1,        //  9: avg(above-left, above-right)
					(a + b + c + d) >> 2, // 10: all-four average
					med,                  // 11: MED predictor
					(a + b + c) >> 2,     // 12: three-way (a, b, c)
					(a + b + d) >> 2,     // 13: three-way (a, b, d)
					(a + c + d) >> 2,     // 14: three-way (a, c, d)
					(b + c + d) >> 2      // 15: three-way (b, c, d)
				};

				int best_abs = Integer.MAX_VALUE;
				int best_idx = 0;
				for (int n = 0; n < 16; n++)
				{
					int abs_delta = Math.abs(e - pred[n]);
					if (abs_delta < best_abs) { best_abs = abs_delta; best_idx = n; }
				}
				map[m++] = (byte) best_idx;
			}
		}

		// Pass 2: compute deltas.
		int k = 0;
		m = 0;

		// Row 0: horizontal deltas.
		dst[k++] = 0;
		for (int j = 1; j < xdim; j++)
		{
			int delta = src[k] - src[k - 1];
			dst[k++] = delta;
			sum += Math.abs(delta);
		}

		// Rows 1..ydim-1: col 0 vertical, interior map-driven, last col horizontal.
		for (int i = 1; i < ydim; i++)
		{
			int delta = src[k] - src[k - xdim];
			dst[k++] = delta;
			sum += Math.abs(delta);

			for (int j = 1; j < xdim - 1; j++)
			{
				int n = map[m++] & 0xFF;
				int a = src[k - 1];
				int b = src[k - xdim];
				int c = src[k - xdim - 1];
				int d = src[k - xdim + 1];

				int med;
				if (c >= Math.max(a, b))      med = Math.min(a, b);
				else if (c <= Math.min(a, b)) med = Math.max(a, b);
				else                          med = a + b - c;

				int pred_val;
				switch (n)
				{
					case  0: pred_val = a;              break;
					case  1: pred_val = c;              break;
					case  2: pred_val = b;              break;
					case  3: pred_val = d;              break;
					case  4: pred_val = (a + c) >> 1;  break;
					case  5: pred_val = (c + b) >> 1;  break;
					case  6: pred_val = (b + d) >> 1;  break;
					case  7: pred_val = (d + a) >> 1;  break;
					case  8: pred_val = (a + b) >> 1;  break;
					case  9: pred_val = (c + d) >> 1;  break;
					case 10: pred_val = (a + b + c + d) >> 2; break;
					case 11: pred_val = med;            break;
					case 12: pred_val = (a + b + c) >> 2; break;
					case 13: pred_val = (a + b + d) >> 2; break;
					case 14: pred_val = (a + c + d) >> 2; break;
					default: pred_val = (b + c + d) >> 2; break; // 15
				}

				delta = src[k] - pred_val;
				dst[k++] = delta;
				sum += Math.abs(delta);
			}

			int delta2 = src[k] - src[k - 1];
			dst[k++] = delta2;
			sum += Math.abs(delta2);
		}

		ArrayList result = new ArrayList();
		result.add(sum);
		result.add(dst);
		result.add(map);
		result.add(init_value);
		return result;
	}

	// All predictors are causal so reconstruction is exact for every pixel.
	public static int[] getValuesFromIdealDeltas16(int[] src, int xdim, int ydim, int init_value, byte[] map)
	{
		int[] dst = new int[xdim * ydim];
		int   k   = 0;
		int   m   = 0;

		// Row 0: horizontal cumsum.
		dst[k++] = init_value;
		for (int j = 1; j < xdim; j++) { dst[k] = dst[k - 1] + src[k]; k++; }

		// Rows 1..ydim-1: col 0 vertical, interior map-driven, last col horizontal.
		for (int i = 1; i < ydim; i++)
		{
			dst[k] = dst[k - xdim] + src[k]; k++;

			for (int j = 1; j < xdim - 1; j++)
			{
				int n = map[m++] & 0xFF;
				int a = dst[k - 1];           // left        [decoded]
				int b = dst[k - xdim];        // above       [decoded]
				int c = dst[k - xdim - 1];    // above-left  [decoded]
				int d = dst[k - xdim + 1];    // above-right [decoded]

				int med;
				if (c >= Math.max(a, b))      med = Math.min(a, b);
				else if (c <= Math.min(a, b)) med = Math.max(a, b);
				else                          med = a + b - c;

				int pred_val;
				switch (n)
				{
					case  0: pred_val = a;              break;
					case  1: pred_val = c;              break;
					case  2: pred_val = b;              break;
					case  3: pred_val = d;              break;
					case  4: pred_val = (a + c) >> 1;  break;
					case  5: pred_val = (c + b) >> 1;  break;
					case  6: pred_val = (b + d) >> 1;  break;
					case  7: pred_val = (d + a) >> 1;  break;
					case  8: pred_val = (a + b) >> 1;  break;
					case  9: pred_val = (c + d) >> 1;  break;
					case 10: pred_val = (a + b + c + d) >> 2; break;
					case 11: pred_val = med;            break;
					case 12: pred_val = (a + b + c) >> 2; break;
					case 13: pred_val = (a + b + d) >> 2; break;
					case 14: pred_val = (a + c + d) >> 2; break;
					default: pred_val = (b + c + d) >> 2; break; // 15
				}

				dst[k] = pred_val + src[k]; k++;
			}

			dst[k] = dst[k - 1] + src[k]; k++;
		}

		return dst;
	}

	// =========================================================================
	// Adaptive predictor -- no map; decided from the causal neighbours.
	//
	// With pa = |b-c| and pb = |a-c|: left (a) if pb > 2*pa, above (b) if
	// pa > 2*pb, otherwise MED.
	// =========================================================================
	private static int adaptivePred(int a, int b, int c, int d)
	{
		int pa = Math.abs(b - c);   // vertical gradient at above-left corner
		int pb = Math.abs(a - c);   // horizontal gradient at above-left corner
		if (pb > pa * 2) return a;  // left
		if (pa > pb * 2) return b;  // above
		// otherwise MED
		if (c >= Math.max(a, b)) return Math.min(a, b);
		if (c <= Math.min(a, b)) return Math.max(a, b);
		return a + b - c;
	}

	public static ArrayList getAdaptiveDeltasFromValues(int[] src, int xdim, int ydim)
	{
		int[] dst        = new int[xdim * ydim];
		int   init_value = src[0];
		int   sum        = 0;
		int   k          = 0;

		// Row 0: horizontal.
		dst[k++] = 0;
		for (int j = 1; j < xdim; j++)
		{
			int delta = src[k] - src[k - 1];
			dst[k++]  = delta;
			sum      += Math.abs(delta);
		}

		// Rows 1..ydim-1.
		for (int i = 1; i < ydim; i++)
		{
			// Col 0: vertical.
			int delta = src[k] - src[k - xdim];
			dst[k++]  = delta;
			sum      += Math.abs(delta);

			// Cols 1..xdim-2: adaptive rule.
			for (int j = 1; j < xdim - 1; j++)
			{
				int a = src[k - 1], b = src[k - xdim];
				int c = src[k - xdim - 1], d = src[k - xdim + 1];
				delta    = src[k] - adaptivePred(a, b, c, d);
				dst[k++] = delta;
				sum     += Math.abs(delta);
			}

			// Last col: horizontal.
			int delta2 = src[k] - src[k - 1];
			dst[k++]   = delta2;
			sum       += Math.abs(delta2);
		}

		ArrayList result = new ArrayList();
		result.add(sum);
		result.add(dst);
		result.add(init_value);
		return result;
	}

	public static int[] getValuesFromAdaptiveDeltas(int[] src, int xdim, int ydim, int init_value)
	{
		int[] dst = new int[xdim * ydim];
		int   k   = 0;

		// Row 0: horizontal cumsum.
		dst[k++] = init_value;
		for (int j = 1; j < xdim; j++) { dst[k] = dst[k - 1] + src[k]; k++; }

		// Rows 1..ydim-1.
		for (int i = 1; i < ydim; i++)
		{
			// Col 0: vertical.
			dst[k] = dst[k - xdim] + src[k]; k++;

			// Cols 1..xdim-2: adaptive rule.
			for (int j = 1; j < xdim - 1; j++)
			{
				int a = dst[k - 1], b = dst[k - xdim];
				int c = dst[k - xdim - 1], d = dst[k - xdim + 1];
				dst[k] = adaptivePred(a, b, c, d) + src[k]; k++;
			}

			// Last col: horizontal.
			dst[k] = dst[k - 1] + src[k]; k++;
		}

		return dst;
	}

	// Frequency distribution for the adaptive predictor (used by init() for
	// auto-selection).  Covers all pixels so the estimate is comparable to
	// the other types in total_delta_sum.
	public static int[] getAdaptiveFrequency(int[] src, int xdim, int ydim)
	{
		ArrayList<Integer> delta_list = new ArrayList<Integer>();
		int k = 1;  // skip pixel 0 (delta = 0)

		// Row 0: horizontal.
		for (int j = 1; j < xdim; j++) { delta_list.add(src[k] - src[k - 1]); k++; }

		// Rows 1..ydim-1.
		for (int i = 1; i < ydim; i++)
		{
			delta_list.add(src[k] - src[k - xdim]); k++;  // col 0: vertical
			for (int j = 1; j < xdim - 1; j++)
			{
				int a = src[k-1], b = src[k-xdim];
				int c = src[k-xdim-1], d = src[k-xdim+1];
				delta_list.add(src[k] - adaptivePred(a, b, c, d)); k++;
			}
			delta_list.add(src[k] - src[k - 1]); k++;  // last col: horizontal
		}

		int mn = Integer.MAX_VALUE, mx = Integer.MIN_VALUE;
		for (int v : delta_list) { if (v < mn) mn = v; if (v > mx) mx = v; }
		int[] freq = new int[mx - mn + 1];
		for (int v : delta_list) freq[v - mn]++;
		return freq;
	}

	// =========================================================================
	// Delta list utilities
	// =========================================================================

	public static ArrayList getDeltaListFromValues(int src[], int xdim, int ydim)
	{
		ArrayList delta_list = new ArrayList();

		int k = 0;
		for(int i = 0; i < ydim; i++)
		{
			for(int j = 0; j < xdim; j++)
			{
				int size;
				if(i == 0 || i == ydim - 1)
					size = (j == 0 || j == xdim - 1) ? 3 : 5;
				else
					size = (j == 0 || j == xdim - 1) ? 5 : 8;

				int[] value    = new int[size];
				int[] location = new int[size];

				if(i == 0)
				{
					if(j == 0)
					{
						value[0]=src[k]-src[k+1];       location[0]=4;
						value[1]=src[k+xdim];           location[1]=6;
						value[2]=src[k+xdim+1];         location[2]=7;
					}
					else if(j == xdim - 1)
					{
						value[0]=src[k]-src[k-1];       location[0]=3;
						value[1]=src[k+xdim-1];         location[1]=5;
						value[2]=src[k+xdim];           location[2]=6;
					}
					else
					{
						value[0]=src[k]-src[k-1];       location[0]=3;
						value[1]=src[k]-src[k+1];       location[1]=4;
						value[2]=src[k]-src[k+xdim-1];  location[2]=5;
						value[3]=src[k]-src[k+xdim];    location[3]=6;
						value[4]=src[k]-src[k+xdim+1];  location[4]=7;
					}
				}
				else if(i == ydim - 1)
				{
					if(j == 0)
					{
						value[0]=src[k]-src[k-xdim];    location[0]=1;
						value[1]=src[k-xdim+1];         location[1]=2;
						value[2]=src[k+1];               location[2]=4;
					}
					else if(j == xdim - 1)
					{
						value[0]=src[k]-src[k-xdim-1];  location[0]=0;
						value[1]=src[k-xdim];           location[1]=1;
						value[2]=src[k-1];               location[2]=3;
					}
					else
					{
						value[0]=src[k]-src[k-xdim-1];  location[0]=0;
						value[1]=src[k]-src[k-xdim];    location[1]=1;
						value[2]=src[k]-src[k-xdim+1];  location[2]=2;
						value[3]=src[k]-src[k-1];       location[3]=3;
						value[4]=src[k]-src[k+1];       location[4]=4;
					}
				}
				else
				{
					if(j == 0)
					{
						value[0]=src[k]-src[k-xdim];    location[0]=1;
						value[1]=src[k]-src[k-xdim+1];  location[1]=2;
						value[2]=src[k]-src[k+1];       location[2]=4;
						value[3]=src[k]-src[k+xdim];    location[3]=6;
						value[4]=src[k]-src[k+xdim+1];  location[4]=7;
					}
					else if(j == xdim - 1)
					{
						value[0]=src[k]-src[k-xdim-1];  location[0]=0;
						value[1]=src[k]-src[k-xdim];    location[1]=1;
						value[2]=src[k]-src[k-1];       location[2]=3;
						value[3]=src[k]-src[k+xdim-1];  location[3]=5;
						value[4]=src[k]-src[k+xdim];    location[4]=6;
					}
					else
					{
						value[0]=src[k]-src[k-xdim-1];  location[0]=0;
						value[1]=src[k]-src[k-xdim];    location[1]=1;
						value[2]=src[k]-src[k-xdim+1];  location[2]=2;
						value[3]=src[k]-src[k-1];       location[3]=3;
						value[4]=src[k]-src[k+1];       location[4]=4;
						value[5]=src[k]-src[k+xdim-1];  location[5]=5;
						value[6]=src[k]-src[k+xdim];    location[6]=6;
						value[7]=src[k]-src[k+xdim+1];  location[7]=7;
					}
				}

				double[] delta = new double[size];
				for(int m = 0; m < size; m++) delta[m] = Math.abs(value[m]);

				double addend = 0.00000001;
				Hashtable<Double, ArrayList> delta_table = new Hashtable<Double, ArrayList>();
				ArrayList key_list = new ArrayList();

				for(int m = 0; m < size; m++)
				{
					if(key_list.contains(delta[m])) { delta[m] += addend; addend *= 2; }
					key_list.add(delta[m]);
					ArrayList list = new ArrayList();
					list.add(value[m]); list.add(location[m]);
					delta_table.put(delta[m], list);
				}

				Collections.sort(key_list);
				int[][] table = new int[size][2];
				for(int m = 0; m < size; m++)
				{
					double key = (double) key_list.get(m);
					ArrayList current = delta_table.get(key);
					table[m][0] = (int) current.get(0);
					table[m][1] = (int) current.get(1);
				}

				delta_list.add(table);
				k++;
			}
		}

		return delta_list;
	}

	public static int[] getIdealDeltasFromList(ArrayList delta_list)
	{
		int[] ideal_delta = new int[delta_list.size()];
		for(int i = 0; i < delta_list.size(); i++)
		{
			int[][] table = (int[][]) delta_list.get(i);
			ideal_delta[i] = table[0][0];
		}
		return ideal_delta;
	}

	public static int getIdealDeltaSum(ArrayList delta_list)
	{
		int sum = 0;
		for(int i = 0; i < delta_list.size(); i++)
		{
			int[][] table = (int[][]) delta_list.get(i);
			sum += Math.abs(table[0][0]);
		}
		return sum;
	}

	public static int getWorstlDeltaSum(ArrayList delta_list)
	{
		int sum = 0;
		for(int i = 0; i < delta_list.size(); i++)
		{
			int[][] table = (int[][]) delta_list.get(i);
			sum += Math.abs(table[table.length - 1][0]);
		}
		return sum;
	}

	// =========================================================================
	// Spatial helpers
	// =========================================================================

	public static ArrayList getNeighbors(int[] src, int x, int y, int xdim, int ydim)
	{
		ArrayList neighbors = new ArrayList();
		if(y > 0)
		{
			if(x > 0) neighbors.add(src[(y-1)*xdim+x-1]);
			neighbors.add(src[(y-1)*xdim+x]);
			if(x < xdim-1) neighbors.add(src[(y-1)*xdim+x+1]);
		}
		if(x > 0) neighbors.add(src[y*xdim+x-1]);
		if(x < xdim-1) neighbors.add(src[y*xdim+x+1]);
		if(y < ydim-1)
		{
			if(x > 0) neighbors.add(src[(y+1)*xdim+x-1]);
			neighbors.add(src[(y+1)*xdim+x]);
			if(x < xdim-1) neighbors.add(src[(y+1)*xdim+x+1]);
		}
		return neighbors;
	}

	public static int getLocationType(int x, int y, int xdim, int ydim)
	{
		if(y == 0)
		{
			if(x == 0)         return 1;
			if(x < xdim-1)    return 2;
			return 3;
		}
		if(y < ydim-1)
		{
			if(x == 0)         return 4;
			if(x < xdim-1)    return 5;
			return 6;
		}
		if(x == 0)             return 7;
		if(x < xdim-1)        return 8;
		return 9;
	}

	public static int getLocationIndex(int location_type, int location)
	{
		switch(location_type)
		{
			case 1:
				if(location==4) return 0; if(location==6) return 1; if(location==7) return 2; break;
			case 2:
				if(location==3) return 0; if(location==4) return 1; if(location==5) return 2;
				if(location==6) return 3; if(location==7) return 4; break;
			case 3:
				if(location==3) return 0; if(location==5) return 1; if(location==6) return 2; break;
			case 4:
				if(location==1) return 0; if(location==2) return 1; if(location==4) return 2;
				if(location==6) return 3; if(location==7) return 4; break;
			case 5:
				if(location==0) return 0; if(location==1) return 1; if(location==2) return 2;
				if(location==3) return 3; if(location==4) return 4; if(location==5) return 5;
				if(location==6) return 6; if(location==7) return 7; break;
			case 6:
				if(location==0) return 0; if(location==1) return 1; if(location==3) return 2;
				if(location==5) return 3; if(location==6) return 4; break;
			case 7:
				if(location==1) return 0; if(location==2) return 1; if(location==4) return 2; break;
			case 8:
				if(location==0) return 0; if(location==1) return 1; if(location==2) return 2;
				if(location==3) return 3; if(location==4) return 4; break;
			case 9:
				if(location==0) return 0; if(location==1) return 1; if(location==3) return 2; break;
		}
		return -1;
	}

	public static int getNeighborIndex(int x, int y, int xdim, int location)
	{
		int k = y * xdim + x;
		if(location==0) return k-xdim-1;
		if(location==1) return k-xdim;
		if(location==2) return k-xdim+1;
		if(location==3) return k-1;
		if(location==4) return k+1;
		if(location==5) return k+xdim-1;
		if(location==6) return k+xdim;
		if(location==7) return k+xdim+1;
		return k;
	}

	public static int getInverseLocation(int location)
	{
		return 7 - location;
	}

	// =========================================================================
	// Block map (delta type 13): the interior is split into block x block
	// squares, and each uses one predictor from its channel's set. Row 0 is
	// horizontal, column 0 vertical and the last column horizontal, as in
	// frame map 2. The map is [block size, n, n predictor ids (PREDICTOR_TABLE,
	// see "Predictor tables" below), then one entry per block, row by row:
	// an index 0..n-1 into the ids]. Each channel's map carries its own ids,
	// so the channels may use different predictors. Some predictors use the
	// previous channel (in coding order) at the same pixel, so a channel is
	// coded and decoded with the one before it: see PixelSource.
	// With a named set (BLOCK_SET_NAMES) a block takes the predictor with
	// the smallest sum of |delta|; surveyBlockSets chooses sets and blocks
	// by estimated coded size instead.
	// =========================================================================

	public static final int BLOCK_MIN = 4, BLOCK_MAX = 32, BLOCK_DEFAULT = 8;

	// Block shapes, a block map's first byte: BLOCK_MIN..BLOCK_MAX is a
	// square of that size; SHAPE_FIRST + i is BLOCK_SHAPES[i], width x height
	// (width 0: the whole row). Rows of pixels -- "lines" -- are the 1-high
	// ones. Blocks are numbered row by row either way.
	public static final int SHAPE_FIRST = 64;
	public static final int[][] BLOCK_SHAPES = { {16, 4}, {32, 2}, {64, 1}, {256, 1}, {0, 1}, {4, 16}, {1, 64} };

	public static int blockWidth(int block, int xdim)
	{
		if(block < SHAPE_FIRST) return block;
		int w = BLOCK_SHAPES[block - SHAPE_FIRST][0];
		return (w == 0) ? Math.max(1, xdim - 2) : w;
	}

	public static int blockHeight(int block) { return (block < SHAPE_FIRST) ? block : BLOCK_SHAPES[block - SHAPE_FIRST][1]; }

	public static boolean validShape(int block)
	{
		return (block >= BLOCK_MIN && block <= BLOCK_MAX) || (block >= SHAPE_FIRST && block < SHAPE_FIRST + BLOCK_SHAPES.length);
	}

	// "8" for a square, "16x4", "row" for a whole-row line.
	public static String shapeName(int block)
	{
		if(block < SHAPE_FIRST) return String.valueOf(block);
		int[] s = BLOCK_SHAPES[block - SHAPE_FIRST];
		return (s[0] == 0) ? "row" : s[0] + "x" + s[1];
	}

	// Named sets, as PREDICTOR_TABLE ids: the first 4, 8, 16 and 32 of one
	// greedy order (by estimated coded size, with the map's cost) over every
	// block of 7 test images at Colour Resolution 0 and 3, block size 8.
	public static final String[] BLOCK_SET_NAMES = {"Top 4", "Top 8", "Top 16", "Top 32"};
	private static final int[] BLOCK_ORDER = { 243, 21, 167, 127, 209, 37, 0, 231, 171, 1, 39, 149, 141, 172, 205, 28, 44, 173, 122, 206, 208, 184, 177, 86, 213, 67, 182, 150, 27, 137, 30, 121 };
	private static final int[] BLOCK_SET_SIZES = {4, 8, 16, 32};

	public static int blockPredictor(int id, int a, int b, int c, int d)
	{
		switch(id)
		{
			case  0: return a;
			case  1: return b;
			case  2: return c;
			case  3: return d;
			case  4: return (a + b) >> 1;
			case  5: return (b + c) >> 1;
			case  6: return (a + c) >> 1;
			case  7: return (b + d) >> 1;
			case  8: return (c + d) >> 1;
			case  9: return (a + b + c + d + 2) >> 2;
			case 10: return a + b - c;
			case 11: if(c >= Math.max(a, b)) return Math.min(a, b); if(c <= Math.min(a, b)) return Math.max(a, b); return a + b - c;
			case 12: return (a * 3 + b + 2) >> 2;
			case 13: return (a + b * 3 + 2) >> 2;
			case 14: return (a * 3 + d + 2) >> 2;
			case 15: return (b * 3 + a + 2) >> 2;
			case 16: return (a + d) >> 1;
			case 17: return a + ((b - c) >> 1);
			case 18: return b + ((a - c) >> 1);
			case 19: return a + d - b;
			case 20: return (a + b + d + 1) / 3;
			case 21: return b + ((d - c) >> 1);
			// 22-27: selection predictors.
			case 22: { int p = a + b - c, pa = Math.abs(p - a), pb = Math.abs(p - b), pc = Math.abs(p - c);
			           return (pa <= pb && pa <= pc) ? a : (pb <= pc) ? b : c; }                 // Paeth (PNG)
			case 23: return median(a, b, c);
			case 24: return median(a, b, d);
			case 25: return median(a, d, a + d - b);                                           // MED along the up-right diagonal
			case 26: return (Math.abs(b - c) <= Math.abs(a - c)) ? a : b;                     // the flatter direction
			case 27: { int h = Math.abs(b - c), v = Math.abs(a - c), g = Math.abs(a - b);     // ... of three: left, up, up-right
			           return (h <= v && h <= g) ? a : (v <= g) ? b : d; }
			default: return b + ((d - c) >> 1);
		}
	}

	private static int median(int x, int y, int z) { return Math.max(Math.min(x, y), Math.min(Math.max(x, y), z)); }

	public static int blockSetSize(int set) { return BLOCK_SET_SIZES[set]; }

	// ---- Predictor ids --------------------------------------------------------
	//
	// A predictor id (one byte) is an index into PREDICTOR_TABLE, below.

	public static final int PRIMITIVES = 22;   // the primitives with only a, b, c, d (blockPredictor 0-21)

	public static final String[] PRIMITIVE_NAMES = {
		"a", "b", "c", "d", "(a+b)/2", "(b+c)/2", "(a+c)/2", "(b+d)/2",
		"(c+d)/2", "(a+b+c+d)/4", "a+b-c", "MED", "(3a+b)/4", "(a+3b)/4", "(3a+d)/4", "(3b+a)/4",
		"(a+d)/2", "a+(b-c)/2", "b+(a-c)/2", "a+d-b", "(a+b+d)/3", "b+(d-c)/2" };

	// A readable name: "MED", or "avg(MED, a)" for an average.
	public static String predictorName(int id) { return PREDICTOR_TABLE.name(id); }

	// The predictor ids of a named set (BLOCK_SET_NAMES).
	public static int[] getSetIds(int set) { return Arrays.copyOf(BLOCK_ORDER, BLOCK_SET_SIZES[set]); }

	// The predictor ids a block map uses (map[2..1+n]).
	public static int[] getMapIds(byte[] map)
	{
		int[] ids = new int[map[1] & 0xFF];
		for(int p = 0; p < ids.length; p++) ids[p] = map[2 + p] & 0xFF;
		return ids;
	}

	// Where a block map's entries start: after the block size, n and the n ids.
	public static int mapHeaderLength(byte[] map) { return 2 + (map[1] & 0xFF); }

	// A block map's predictors described: the named set they match, or "n predictors".
	public static String describeMapSet(byte[] map)
	{
		int[] ids = getMapIds(map);
		for(int set = 0; set < BLOCK_SET_NAMES.length; set++) if(Arrays.equals(ids, getSetIds(set))) return BLOCK_SET_NAMES[set];
		return ids.length + " predictors";
	}

	// ---- Choosing the block size and predictor set -----------------------

	// Block sizes findBestBlock may pick.
	public static final int[] BLOCK_SEARCH_SIZES = {4, 6, 8, 12, 16, 24, 32};

	// Bytes the three channels take as block map deltas (Context coded,
	// packContextDeltas) plus maps (writeMap), as the writers save them.
	public static long getBlockMapBytes(int[][] channel, int xdim, int ydim, int block, int set)
	{
		return getBlockMapBytes(pixelSources(channel, xdim, ydim, PREDICTOR_TABLE), ydim, block, set);
	}

	private static long getBlockMapBytes(PixelSource[] ps, int ydim, int block, int set)
	{
		try
		{
			int xdim = ps[0].xdim;
			int[][] d = new int[3][]; byte[][] m = new byte[3][];
			for(int i = 0; i < 3; i++)
			{
				ArrayList result = getBlockDeltasFromValues(ps[i], ydim, block, getSetIds(set));
				d[i] = (int[]) result.get(1); m[i] = (byte[]) result.get(2);
			}
			long total = 0;
			for(int i = 0; i < 3; i++)
			{
				total += packContextDeltas(d[i], Arrays.copyOf(d, i), xdim).length;
				java.io.ByteArrayOutputStream b = new java.io.ByteArrayOutputStream();
				writeMap(new java.io.DataOutputStream(b), 13, m[i], (i > 0) ? m[i - 1] : null, xdim);
				total += b.size();
			}
			return total;
		}
		catch(java.io.IOException e) { throw new java.io.UncheckedIOException(e); }
	}

	// The block size and predictor set that code the three channels
	// smallest (actual sizes): every set at sizes 4, 8 and 16, then the best
	// set at the untried sizes next to its best. Returns {block size, set};
	// bytes[set][index in BLOCK_SEARCH_SIZES] gets the sizes tried (-1 for
	// the others). Runs the trials in parallel, as many at once as memory
	// allows.
	public static int[] findBestBlock(int[][] channel, int xdim, int ydim, long[][] bytes)
	{
		PixelSource[] ps = pixelSources(channel, xdim, ydim, PREDICTOR_TABLE);
		long  task   = 28L * xdim * ydim;   // deltas, maps and the Context coder's arrays for 3 channels
		int   n_sets = BLOCK_SET_NAMES.length;
		int[] first  = {0, 2, 4};   // 4, 8, 16
		for(long[] row : bytes) Arrays.fill(row, -1);
		boundedParallel(n_sets * first.length, task, t ->
		{
			int set = t / first.length, s = first[t % first.length];
			bytes[set][s] = getBlockMapBytes(ps, ydim, BLOCK_SEARCH_SIZES[s], set);
		});
		int best_set = 0, best_s = first[0];
		for(int set = 0; set < n_sets; set++)
			for(int s : first) if(bytes[set][s] < bytes[best_set][best_s]) { best_set = set; best_s = s; }

		final int bs = best_set;
		int[] next = {best_s - 1, best_s + 1};
		boundedParallel(2, task, t ->
		{
			int s = next[t];
			if(s >= 0 && s < BLOCK_SEARCH_SIZES.length && bytes[bs][s] < 0)
				bytes[bs][s] = getBlockMapBytes(ps, ydim, BLOCK_SEARCH_SIZES[s], bs);
		});
		for(int s = 0; s < BLOCK_SEARCH_SIZES.length; s++) if(bytes[bs][s] >= 0 && bytes[bs][s] < bytes[bs][best_s]) best_s = s;
		return new int[] {BLOCK_SEARCH_SIZES[best_s], bs};
	}

	// body(0..n-1) on up to as many threads as there are cores and as the
	// free heap allows, each task needing about task_bytes. Big images run
	// fewer tasks at once instead of running out of memory.
	static void boundedParallel(int n, long task_bytes, java.util.function.IntConsumer body)
	{
		Runtime rt   = Runtime.getRuntime();
		long    free = rt.maxMemory() - (rt.totalMemory() - rt.freeMemory());
		int     p    = (int) Math.max(1, Math.min(Math.min(n, rt.availableProcessors()), (long) (free * 0.6) / Math.max(1, task_bytes)));
		if(p == 1) { for(int i = 0; i < n; i++) body.accept(i); return; }
		java.util.concurrent.atomic.AtomicInteger next = new java.util.concurrent.atomic.AtomicInteger();
		Throwable[] failed = new Throwable[1];
		Thread[] worker = new Thread[p];
		for(int w = 0; w < p; w++)
		{
			worker[w] = new Thread(() ->
			{
				try { for(int i; (i = next.getAndIncrement()) < n && failed[0] == null; ) body.accept(i); }
				catch(Throwable e) { synchronized(failed) { if(failed[0] == null) failed[0] = e; } }
			});
			worker[w].start();
		}
		for(Thread t : worker)
			try { t.join(); } catch(InterruptedException e) { Thread.currentThread().interrupt(); throw new RuntimeException(e); }
		if(failed[0] instanceof RuntimeException) throw (RuntimeException) failed[0];
		if(failed[0] instanceof Error) throw (Error) failed[0];
		if(failed[0] != null) throw new RuntimeException(failed[0]);
	}

	// Each channel's PixelSource, with the channel before it as reference.
	static PixelSource[] pixelSources(int[][] channel, int xdim, int ydim, PredictorTable table)
	{
		PixelSource[] ps = new PixelSource[channel.length];
		java.util.stream.IntStream.range(0, channel.length).parallel().forEach(i ->
			ps[i] = new PixelSource(channel[i], (i > 0) ? channel[i - 1] : null, xdim, ydim, table));
		return ps;
	}

	// findBestBlock's results as a table, the chosen one marked *.
	public static String getBlockTable(long[][] bytes, int[] best)
	{
		StringBuilder t = new StringBuilder("Block map, bytes coded (Context-coded deltas + maps, 3 channels):\n");
		t.append(String.format("  %-18s", "set \\ block"));
		for(int b : BLOCK_SEARCH_SIZES) t.append(String.format(" %9d ", b));
		t.append('\n');
		for(int set = 0; set < BLOCK_SET_NAMES.length; set++)
		{
			t.append(String.format("  %-18s", BLOCK_SET_NAMES[set]));
			for(int s = 0; s < BLOCK_SEARCH_SIZES.length; s++)
			{
				boolean chosen = set == best[1] && BLOCK_SEARCH_SIZES[s] == best[0];
				t.append((bytes[set][s] < 0) ? String.format(" %9s ", "-") : String.format(" %9d", bytes[set][s]) + (chosen ? "*" : " "));
			}
			t.append('\n');
		}
		return t.toString();
	}

	// ---- Predictor tables -------------------------------------------------------
	//
	// A block map's ids index PREDICTOR_TABLE: 255 predictors, each a
	// primitive or the average of two, (P_i + P_j + 1) >> 1 (see
	// predictor.txt for each one and how they were chosen). Primitives 0-21
	// are blockPredictor's on a, b, c, d; the rest are:
	//
	//   selection   22 Paeth, 23 median(a,b,c), 24 median(a,b,d),
	//               25 median(a,d,a+d-b), 26 switch(a,b), 27 switch(a,b,d)
	//               (blockPredictor);
	//   cross       28-35: a predictor plus the previous channel's error
	//               under it, P + (R - P_R), R the previous channel (coded
	//               first) at the same pixel: P = a, b, d, MED, (a+b)/2,
	//               a+b-c; then MED and (a+b)/2 with half the error. On the
	//               first channel they are just P;
	//   reach       36 2a-aa, 37 2b-bb (aa two to the left, bb two up),
	//               38 GAP (CALIC's gradient-adjusted predictor), 39 GAP with
	//               thresholds an eighth as large (for coarser colour);
	//   clamped     40 a+b-c and 41 a+d-b, clamped to the range of a, b, c, d;
	//   bias        42-45: MED, (a+b)/2, a and b plus their mean error so far
	//               in the pixel's gradient context (as JPEG-LS corrects
	//               bias; 729 contexts, counts halved at 64). The mean is of
	//               every earlier pixel in raster order, whatever predictor
	//               it was coded with, so the decoder keeps the same counts.
	//
	// Beyond a, b, c and d these need the channel's earlier pixels and the
	// previous channel: see PixelSource.

	public static final int SELECTION_FIRST = 22, CROSS_FIRST = 28, REACH_FIRST = 36, CLAMP_FIRST = 40, BIAS_FIRST = 42, ALL_PRIMITIVES = 46;
	public static final String[] GROUP_NAMES = {"selection", "cross-channel", "longer reach", "clamped", "bias-corrected"};
	public static final int[] GROUP_FIRST = {SELECTION_FIRST, CROSS_FIRST, REACH_FIRST, CLAMP_FIRST, BIAS_FIRST, ALL_PRIMITIVES};
	public static final String[] EXTRA_PRIMITIVE_NAMES = {
		"Paeth", "median(a,b,c)", "median(a,b,d)", "median(a,d,a+d-b)", "switch(a,b)", "switch(a,b,d)",
		"a+(R-Ra)", "b+(R-Rb)", "d+(R-Rd)", "MED+(R-MED_R)", "(a+b)/2+(R-..)", "a+b-c+(R-..)", "MED+(R-MED_R)/2", "(a+b)/2+(R-..)/2",
		"2a-aa", "2b-bb", "GAP", "GAP/8",
		"clamp(a+b-c)", "clamp(a+d-b)",
		"MED+bias", "(a+b)/2+bias", "a+bias", "b+bias"};
	private static final int[] CROSS_BASE = {0, 1, 3, 11, 4, 10, 11, 4};   // the last two with half the error
	private static final int[] BIAS_BASE  = {11, 4, 0, 1};

	public static String primitiveName(int p) { return (p < PRIMITIVES) ? PRIMITIVE_NAMES[p] : EXTRA_PRIMITIVE_NAMES[p - PRIMITIVES]; }

	public static final class PredictorTable
	{
		public  final String name;
		private final int[]  first, second;   // primitives; first == second for a primitive alone
		public  final int    primitives;      // primitives its entries use: 0..primitives-1

		PredictorTable(String name, int[] first, int[] second)
		{
			this.name   = name;
			this.first  = first;
			this.second = second;
			int m = 0;
			for(int k = 0; k < first.length; k++) m = Math.max(m, Math.max(first[k], second[k]));
			primitives = m + 1;
		}

		public int size() { return first.length; }
		public int first(int id)  { return first[id]; }
		public int second(int id) { return second[id]; }
		public boolean alone(int id) { return first[id] == second[id]; }
		public String name(int id)
		{
			return alone(id) ? primitiveName(first[id]) : "avg(" + primitiveName(first[id]) + ", " + primitiveName(second[id]) + ")";
		}
		// Whether id is primitive p or an average with p.
		public boolean uses(int id, int p) { return first[id] == p || second[id] == p; }
		// This table without the given primitives (alone or in averages), ids in the same order.
		public PredictorTable without(String name, int... primitives)
		{
			ArrayList<Integer> keep = new ArrayList<Integer>();
			for(int id = 0; id < size(); id++)
			{
				boolean drop = false;
				for(int p : primitives) drop |= uses(id, p);
				if(!drop) keep.add(id);
			}
			int[] i = new int[keep.size()], j = new int[keep.size()];
			for(int k = 0; k < i.length; k++) { i[k] = first[keep.get(k)]; j[k] = second[keep.get(k)]; }
			return new PredictorTable(name, i, j);
		}
		// Its prediction from the primitives' values.
		int predict(int id, int[] prim) { int i = first[id], j = second[id]; return (i == j) ? prim[i] : (prim[i] + prim[j] + 1) >> 1; }
	}

	public static PredictorTable pairTable(String name, int[] pairs)
	{
		int[] i = new int[pairs.length / 2], j = new int[pairs.length / 2];
		for(int k = 0; k < i.length; k++) { i[k] = pairs[2 * k]; j[k] = pairs[2 * k + 1]; }
		return new PredictorTable(name, i, j);
	}

	// PREDICTOR_TABLE, as pairs of primitives (equal for a primitive alone),
	// 255 of them (ids are one byte), chosen by measurement on 7 test images
	// at Colour Resolution 0 and 3 (see predictor.txt):
	//   - 31 primitives. Of the 44 tried, 13 were dropped together because
	//     removing them (alone and in averages) cost under 0.03%: 10 a+b-c,
	//     12 (3a+b)/4, 14 (3a+d)/4, 17 a+(b-c)/2, 20 (a+b+d)/3, 22 Paeth,
	//     26 and 27 the switches, 33 a+b-c+(R-..), 38 GAP, 41 clamp(a+d-b),
	//     43 (a+b)/2+bias and 44 a+bias (15 and 21 were already out);
	//   - 224 averages of two of them: the most used (share of blocks) of
	//     all 465 in a survey over every pair, 97% of their use.
	static final int[] PREDICTOR_PAIRS = {
		0,0, 1,1, 2,2, 3,3, 4,4, 5,5, 6,6, 7,7, 8,8, 9,9, 11,11, 13,13,
		16,16, 18,18, 19,19, 23,23, 24,24, 25,25, 28,28, 29,29, 30,30, 31,31, 32,32, 34,34,
		35,35, 36,36, 37,37, 39,39, 40,40, 42,42, 45,45, 2,0, 2,1, 3,0, 3,1, 4,0,
		4,3, 5,0, 5,1, 5,2, 5,3, 5,4, 6,0, 6,1, 6,2, 6,3, 6,4, 7,0,
		7,1, 7,4, 7,5, 7,6, 8,0, 8,1, 8,4, 9,4, 9,6, 11,0, 11,1, 11,7,
		11,8, 13,0, 16,0, 16,1, 16,3, 16,4, 16,5, 16,6, 16,7, 16,8, 16,9, 16,11,
		18,0, 18,1, 18,3, 18,4, 18,5, 18,6, 18,7, 18,8, 18,9, 18,13, 18,16, 19,3,
		19,5, 19,6, 19,7, 19,8, 19,18, 23,0, 23,1, 23,2, 23,3, 23,4, 23,5, 23,6,
		24,0, 24,1, 24,4, 24,5, 24,6, 24,7, 24,9, 24,18, 24,23, 25,1, 25,3, 25,5,
		25,6, 28,0, 28,5, 28,6, 28,7, 28,8, 28,16, 29,1, 29,4, 29,5, 29,6, 29,7,
		29,8, 29,16, 29,28, 30,6, 30,7, 30,16, 30,25, 30,28, 30,29, 31,0, 31,5, 31,6,
		31,7, 31,11, 31,16, 32,4, 32,5, 32,6, 32,7, 32,8, 32,9, 32,16, 32,28, 32,29,
		32,30, 34,0, 34,2, 34,3, 34,6, 34,7, 34,8, 34,16, 34,23, 34,25, 34,28, 34,29,
		34,30, 34,31, 35,0, 35,1, 35,2, 35,3, 35,4, 35,5, 35,6, 35,7, 35,8, 35,9,
		35,16, 35,23, 35,24, 35,28, 35,29, 35,30, 35,32, 36,2, 36,4, 36,6, 36,11, 36,16,
		36,25, 37,5, 37,7, 37,8, 37,9, 37,13, 37,16, 37,18, 37,19, 37,25, 39,0, 39,1,
		39,2, 39,3, 39,4, 39,5, 39,6, 39,7, 39,8, 39,9, 39,11, 39,13, 39,16, 39,18,
		39,19, 39,23, 39,24, 39,25, 39,28, 39,29, 39,30, 39,31, 39,32, 39,34, 39,35, 39,36,
		39,37, 40,0, 40,1, 40,3, 40,7, 40,11, 40,16, 40,19, 40,25, 40,34, 40,36, 40,37,
		40,39, 42,0, 42,1, 42,3, 42,4, 42,6, 42,7, 42,16, 42,18, 42,23, 42,29, 42,34,
		42,35, 42,36, 42,37, 42,39, 42,40, 45,0, 45,3, 45,4, 45,5, 45,6, 45,7, 45,16,
		45,23, 45,34, 45,39 };

	public static final PredictorTable PREDICTOR_TABLE = pairTable("predictors", PREDICTOR_PAIRS);
	public static final int PREDICTORS = PREDICTOR_TABLE.size();   // 255

	// The values a table's primitives are computed from at pixel k: the
	// channel v (the encoder's source, or the decoder's output so far), the
	// previous channel ref (null for the first), and the bias counts.
	// prepare(k) must be called for each interior pixel in raster order
	// before its prediction, and commit(k) once v[k] is known; the encoder
	// does this for the whole channel up front (the constructor), the
	// decoder as it goes.
	public static final class PixelSource
	{
		final int[]     v, ref;
		final int       xdim;
		final short[][] corr;          // bias primitives' corrections, [s][k] (|corr| <= the channel's range)
		final int[][]   sum, count;    // [s][context]
		int             context;       // the bias context of the pixel between prepare and commit

		// Encoder: v is the whole channel.
		public PixelSource(int[] v, int[] ref, int xdim, int ydim, PredictorTable table)
		{
			this(v, ref, xdim, table, true);
			if(corr != null)
				for(int y = 1; y < ydim; y++)
					for(int x = 1; x < xdim - 1; x++) { prepare(y * xdim + x); commit(y * xdim + x); }
		}

		// Decoder: v fills in as it decodes.
		PixelSource(int[] v, int[] ref, int xdim, PredictorTable table, boolean unused)
		{
			this.v = v; this.ref = ref; this.xdim = xdim;
			boolean bias = table.primitives > BIAS_FIRST;
			corr    = bias ? new short[BIAS_BASE.length][v.length] : null;
			sum     = bias ? new int[BIAS_BASE.length][729] : null;
			count   = bias ? new int[BIAS_BASE.length][729] : null;
		}

		void prepare(int k)
		{
			if(corr == null) return;
			int a = v[k - 1], b = v[k - xdim], c = v[k - xdim - 1], d = v[k - xdim + 1];
			int t = (gradientLevel(d - b) + 4) * 81 + (gradientLevel(b - c) + 4) * 9 + gradientLevel(c - a) + 4;
			context = t;
			for(int s = 0; s < BIAS_BASE.length; s++)
			{
				int n = count[s][t], e = sum[s][t];
				corr[s][k] = (short) ((n == 0) ? 0 : (e >= 0) ? (e + n / 2) / n : -((-e + n / 2) / n));
			}
		}

		void commit(int k)
		{
			if(corr == null) return;
			int a = v[k - 1], b = v[k - xdim], c = v[k - xdim - 1], d = v[k - xdim + 1], t = context;
			for(int s = 0; s < BIAS_BASE.length; s++)
			{
				sum[s][t] += v[k] - blockPredictor(BIAS_BASE[s], a, b, c, d);
				if(++count[s][t] == 64) { sum[s][t] /= 2; count[s][t] = 32; }
			}
		}

		// Primitive q (>= CROSS_FIRST) at interior pixel k with neighbours a, b, c, d.
		int extended(int q, int k, int a, int b, int c, int d)
		{
			if(q < REACH_FIRST)
			{
				int s = q - CROSS_FIRST, base = CROSS_BASE[s], p = blockPredictor(base, a, b, c, d);
				if(ref == null) return p;
				int e = ref[k] - blockPredictor(base, ref[k - 1], ref[k - xdim], ref[k - xdim - 1], ref[k - xdim + 1]);
				return (s >= 6) ? p + (e >> 1) : p + e;
			}
			if(q < CLAMP_FIRST)
			{
				int x = k % xdim, y = k / xdim;
				int aa = (x >= 2) ? v[k - 2] : a, bb = (y >= 2) ? v[k - 2 * xdim] : b;
				if(q == REACH_FIRST)     return 2 * a - aa;
				if(q == REACH_FIRST + 1) return 2 * b - bb;
				int nne = (y >= 2) ? v[k - 2 * xdim + 1] : d;
				return (q == REACH_FIRST + 2) ? gap(a, aa, b, bb, c, d, nne, 80, 32, 8) : gap(a, aa, b, bb, c, d, nne, 10, 4, 1);
			}
			if(q < BIAS_FIRST)
			{
				int lo = Math.min(Math.min(a, b), Math.min(c, d)), hi = Math.max(Math.max(a, b), Math.max(c, d));
				int p = (q == CLAMP_FIRST) ? a + b - c : a + d - b;
				return Math.max(lo, Math.min(hi, p));
			}
			int s = q - BIAS_FIRST;
			return blockPredictor(BIAS_BASE[s], a, b, c, d) + corr[s][k];
		}

		// The table's primitives at interior pixel k, into prim.
		void primitives(int k, int np, int[] prim)
		{
			int a = v[k - 1], b = v[k - xdim], c = v[k - xdim - 1], d = v[k - xdim + 1];
			int m = Math.min(np, CROSS_FIRST);
			for(int q = 0; q < m; q++) prim[q] = blockPredictor(q, a, b, c, d);
			for(int q = m; q < np; q++) prim[q] = extended(q, k, a, b, c, d);
		}

		// One primitive at interior pixel k.
		int primitive(int q, int k)
		{
			int a = v[k - 1], b = v[k - xdim], c = v[k - xdim - 1], d = v[k - xdim + 1];
			return (q < CROSS_FIRST) ? blockPredictor(q, a, b, c, d) : extended(q, k, a, b, c, d);
		}

		// Prediction of table id at interior pixel k.
		int predict(PredictorTable table, int id, int k)
		{
			int i = table.first(id), j = table.second(id), p = primitive(i, k);
			return (i == j) ? p : (p + primitive(j, k) + 1) >> 1;
		}
	}

	// JPEG-LS gradient levels: 0, then +-1 for 1-2, +-2 for 3-6, +-3 for 7-20, +-4 beyond.
	private static int gradientLevel(int g)
	{
		int m = Math.abs(g), l = (m == 0) ? 0 : (m < 3) ? 1 : (m < 7) ? 2 : (m < 21) ? 3 : 4;
		return (g < 0) ? -l : l;
	}

	// CALIC's GAP from W = a, WW = aa, N = b, NN = bb, NW = c, NE = d, NNE,
	// with the thresholds t1 > t2 > t3 (80, 32, 8 for 8-bit values).
	private static int gap(int w, int ww, int n, int nn, int nw, int ne, int nne, int t1, int t2, int t3)
	{
		int dh = Math.abs(w - ww) + Math.abs(n - nw) + Math.abs(n - ne);
		int dv = Math.abs(w - nw) + Math.abs(n - nn) + Math.abs(ne - nne);
		if(dv - dh > t1) return w;
		if(dh - dv > t1) return n;
		int p = (2 * (w + n) + ne - nw) >> 2;
		if(dv - dh > t2)      p = (p + w) >> 1;
		else if(dv - dh > t3) p = (3 * p + w) >> 2;
		else if(dh - dv > t2) p = (p + n) >> 1;
		else if(dh - dv > t3) p = (3 * p + n) >> 2;
		return p;
	}

	// ---- Surveying predictor sets ----------------------------------------------
	//
	// Any of the PREDICTORS can be in a block map's set, and each channel has
	// its own set. surveyBlockSets picks the block size and the sets, ranking
	// predictors by an estimate of their coded size rather than by |delta|:
	//
	//   1. A cost per delta value (DeltaCost): -log2 of its frequency in the
	//      deltas a reference set (Top 32) gives, per coding context
	//      (getContext, with the deltas already chosen for the channels
	//      before as context). In 1/256 bits.
	//   2. For a block size, each predictor's estimated bits in each block
	//      (blockCostTable): the sum of the costs of its deltas.
	//   3. Per channel, predictors in greedy order (greedyCostOrder): first
	//      the one with the smallest total, then each time the one that
	//      saves the most estimated bits when every block uses its cheapest
	//      predictor so far -- less what it adds to the map (the entropy of
	//      the block counts, and a byte for its id). The order stops when no
	//      predictor saves anything.
	//   4. The real coded size -- Context-coded deltas plus the map
	//      (writeMap) -- for the first n of that order, at n = SURVEY_COUNTS
	//      and the whole order, until two in a row are larger than the best.
	//      Blocks use their cheapest predictor by the estimate
	//      (assignByCost), not the smallest |delta|.
	//   5. Block sizes 4, 8 and 16, then the untried sizes next to the best.
	//
	// The block size is shared by the channels (a map's context coding uses
	// the previous channel's map, entry for entry). getBlockDeltasByCost
	// makes the same deltas and maps from the surveyed ids, so a writer can
	// redo them without surveying again.

	public static final int[] SURVEY_COUNTS = {1, 2, 3, 4, 5, 6, 8, 10, 12, 16, 20, 24, 32, 40, 48, 64, 80, 96, 128, 160, 192, 255};
	static final int SURVEY_REFERENCE_SET = 3;          // Top 32
	static final int SURVEY_ID_COST       = 8 * 256;    // a predictor id in the map header, 1/256 bits
	static final int SURVEY_SAMPLE_BLOCKS = 1 << 17;    // blocks the greedy order is found on, at most

	// floor(x + 0.5) of 256 * log2(r), as the survey's costs are rounded.
	private static int cost256(double r) { return (int) Math.floor(256 * Math.log(r) / Math.log(2) + 0.5); }

	// Estimated cost of each delta value of a channel in each coding
	// context, from the reference deltas ref (1/256 bits; adaptive counts
	// start at 1 and go up by 32, as the Context coder's do).
	public static final class DeltaCost
	{
		final int     lo, range;
		final int[][] cost;      // [context][delta - lo]
		final int[]   context;   // each pixel's context

		public DeltaCost(int[] src, int[] ref, int[][] previous, int xdim, int ydim)
		{
			int min = Integer.MAX_VALUE, max = Integer.MIN_VALUE;
			for(int v : src) { if(v < min) min = v; if(v > max) max = v; }
			int span = max - min;
			lo    = -2 * span - 2;
			range = 4 * span + 5;
			context = new int[src.length];
			long[][] count = new long[CONTEXTS][range];
			long[]   total = new long[CONTEXTS];
			for(int y = 1; y < ydim; y++)
				for(int x = 1; x < xdim - 1; x++)
				{
					int k = y * xdim + x, c = getContext(ref, previous, k, xdim);
					context[k] = c;
					count[c][index(ref[k])]++;
					total[c]++;
				}
			cost = new int[CONTEXTS][range];
			for(int c = 0; c < CONTEXTS; c++)
				for(int v = 0; v < range; v++)
					cost[c][v] = cost256((32.0 * total[c] + range) / (32.0 * count[c][v] + 1));
		}

		int index(int delta) { return Math.max(0, Math.min(range - 1, delta - lo)); }
	}

	// Estimated bits (1/256) of each predictor in each block, as
	// [block * n + q] for the ids given (n of them), or for every id
	// (ids == null, n = PREDICTORS); blocks row by row as in the map.
	// The channel and what its primitives need are in ps.
	public static int[] blockCostTable(PixelSource ps, int ydim, int block, DeltaCost dc, PredictorTable table, int[] ids)
	{
		int bh = blocksDown(ydim, block);
		int[] rows = new int[bh];
		for(int by = 0; by < bh; by++) rows[by] = by;
		return blockCostRows(ps, ydim, block, dc, table, ids, rows);
	}

	// As above, for the given block rows only: est[(r * bw + bx) * n + q]
	// for rows[r].
	static int[] blockCostRows(PixelSource ps, int ydim, int block, DeltaCost dc, PredictorTable table, int[] ids, int[] rows)
	{
		int   n  = (ids == null) ? table.size() : ids.length;
		int[] pid = new int[n];
		for(int q = 0; q < n; q++) pid[q] = (ids == null) ? q : ids[q];
		int   bw = blocksAcross(ps.xdim, block);
		int[] est = new int[rows.length * bw * n];
		java.util.stream.IntStream.range(0, rows.length).parallel().forEach(r ->
			blockCostRow(ps, ydim, block, dc, table, pid, rows[r], est, r * bw * n, new int[table.primitives]));
		return est;
	}

	// One block row's estimates, [bx * n + q] from offset.
	private static void blockCostRow(PixelSource ps, int ydim, int block, DeltaCost dc, PredictorTable table, int[] pid, int by, int[] est, int offset, int[] prim)
	{
		int[] src = ps.v;
		int   xdim = ps.xdim, n = pid.length, np = table.primitives;
		int   bh_ = blockHeight(block), bw_ = blockWidth(block, xdim);
		int   y0 = 1 + by * bh_, y1 = Math.min(ydim, y0 + bh_);
		for(int y = y0; y < y1; y++)
			for(int x = 1; x < xdim - 1; x++)
			{
				int   k = y * xdim + x, s = src[k];
				int[] cc = dc.cost[dc.context[k]];
				ps.primitives(k, np, prim);
				int base = offset + (x - 1) / bw_ * n;
				for(int q = 0; q < n; q++) est[base + q] += cc[dc.index(s - table.predict(pid[q], prim))];
			}
	}

	// Block maps for prefixes of ids (the first counts[c] of them), each
	// block taking the first id of the prefix with the smallest estimate,
	// as assignByCost does. One pass over the block rows, holding one row's
	// estimates at a time, so large images don't need the whole table.
	static byte[][] prefixMaps(PixelSource ps, int ydim, int block, DeltaCost dc, PredictorTable table, int[] ids, int[] counts)
	{
		int bw = blocksAcross(ps.xdim, block), bh = blocksDown(ydim, block), n = ids.length;
		byte[][] map = new byte[counts.length][];
		for(int c = 0; c < counts.length; c++)
		{
			int m = counts[c];
			map[c] = new byte[2 + m + bw * bh];
			map[c][0] = (byte) block; map[c][1] = (byte) m;
			for(int q = 0; q < m; q++) map[c][2 + q] = (byte) ids[q];
		}
		java.util.stream.IntStream.range(0, bh).parallel().forEach(by ->
		{
			int[] est = new int[bw * n];
			blockCostRow(ps, ydim, block, dc, table, ids, by, est, 0, new int[table.primitives]);
			for(int bx = 0; bx < bw; bx++)
			{
				int best = 0, c = 0;
				for(int q = 0; q < n && c < counts.length; q++)
				{
					if(est[bx * n + q] < est[bx * n + best]) best = q;
					while(c < counts.length && counts[c] == q + 1) { map[c][2 + counts[c] + by * bw + bx] = (byte) best; c++; }
				}
			}
		});
		return map;
	}

	// round(256 * c * log2 c) for c = 0..n: n log2 n - sum c log2 c is the
	// map's size (bits) when n blocks are split into groups of size c.
	private static long[] nlog2nTable(int n)
	{
		long[] t = new long[n + 1];
		for(int c = 2; c <= n; c++) t[c] = (long) Math.floor(256.0 * c * Math.log(c) / Math.log(2) + 0.5);
		return t;
	}

	// Predictor ids in greedy order over a blockCostTable of every id (see
	// the section comment); stops when no predictor saves anything.
	public static int[] greedyCostOrder(int[] est, int blocks)
	{
		return greedyCostOrder(est, blocks, PREDICTORS);
	}

	// As above, over a table of P predictors.
	public static int[] greedyCostOrder(int[] est, int blocks, final int P)
	{
		return greedyCostOrder(est, blocks, P, SURVEY_ID_COST);
	}

	// As above, with the cost of an id in the map header given (scaled down
	// when est covers only a sample of the blocks).
	public static int[] greedyCostOrder(int[] est, int blocks, final int P, final long id_cost)
	{
		long[] NL = nlog2nTable(blocks);
		long[] total = new long[P];
		for(int b = 0; b < blocks; b++) for(int p = 0; p < P; p++) total[p] += est[b * P + p];
		int first = 0;
		for(int p = 1; p < P; p++) if(total[p] < total[first]) first = p;

		ArrayList<Integer> order = new ArrayList<Integer>();
		order.add(first);
		boolean[] chosen = new boolean[P]; chosen[first] = true;
		int[]  cur = new int[blocks], assign = new int[blocks];
		for(int b = 0; b < blocks; b++) cur[b] = est[b * P + first];
		long[] count = new long[P]; count[0] = blocks;
		while(order.size() < Math.min(P, 255))   // a set has at most 255 ids
		{
			final int n = order.size();
			long map = NL[blocks];
			for(int q = 0; q < n; q++) map -= NL[(int) count[q]];
			final long map_old = map;
			long[] gain = new long[P];
			java.util.stream.IntStream.range(0, P).parallel().forEach(p ->
			{
				if(chosen[p]) return;
				long[] moved_from = new long[n];
				long   save = 0;
				int    moved = 0;
				for(int b = 0; b < blocks; b++)
				{
					int e = est[b * P + p];
					if(e < cur[b]) { save += cur[b] - e; moved_from[assign[b]]++; moved++; }
				}
				long map_new = NL[blocks] - NL[moved];
				for(int q = 0; q < n; q++) map_new -= NL[(int) (count[q] - moved_from[q])];
				gain[p] = save - (map_new - map_old) - id_cost;
			});
			int best = -1;
			for(int p = 0; p < P; p++) if(!chosen[p] && (best < 0 || gain[p] > gain[best])) best = p;
			if(gain[best] <= 0) break;
			chosen[best] = true;
			order.add(best);
			for(int b = 0; b < blocks; b++)
			{
				int e = est[b * P + best];
				if(e < cur[b]) { cur[b] = e; count[assign[b]]--; assign[b] = n; count[n]++; }
			}
		}
		int[] ids = new int[order.size()];
		for(int k = 0; k < ids.length; k++) ids[k] = order.get(k);
		return ids;
	}

	// A block map for the ids: each block uses the first of them with the
	// smallest estimate in est (a blockCostTable of every id of the table,
	// stride = its size, with all = true; else of the ids, stride ids.length).
	private static byte[] assignByCost(int[] est, boolean all, int stride, int[] ids, int xdim, int ydim, int block)
	{
		int    bw = blocksAcross(xdim, block), bh = blocksDown(ydim, block), n = ids.length;
		int    head = 2 + n;
		byte[] map = new byte[head + bw * bh];
		map[0] = (byte) block; map[1] = (byte) n;
		for(int q = 0; q < n; q++) map[2 + q] = (byte) ids[q];
		for(int b = 0; b < bw * bh; b++)
		{
			int best = 0;
			for(int q = 1; q < n; q++)
				if(est[b * stride + (all ? ids[q] : q)] < est[b * stride + (all ? ids[best] : best)]) best = q;
			map[head + b] = (byte) best;
		}
		return map;
	}

	// The block deltas for a given block map (its block size, ids and
	// choices); the same list as getBlockDeltasFromValues.
	// The block deltas for a given block map (its block size, ids and
	// choices), with the map's ids in the given table and the channel (and
	// what its primitives need) in ps; the same list as getBlockDeltasFromValues.
	public static ArrayList getBlockDeltasFromMap(PixelSource ps, int ydim, byte[] map, PredictorTable table)
	{
		return getBlockDeltasFromMap(ps, ydim, map, table, getMapIds(map));
	}

	// As above, with the set's ids given (tables over 256 ids, for research,
	// can't be read back from the map's one-byte ids).
	static ArrayList getBlockDeltasFromMap(PixelSource ps, int ydim, byte[] map, PredictorTable table, int[] ids)
	{
		int[] src = ps.v;
		int   xdim = ps.xdim;
		int   block = map[0] & 0xFF, bw = blocksAcross(xdim, block), head = mapHeaderLength(map);
		int   bh_ = blockHeight(block), bw_ = blockWidth(block, xdim);
		int[] dst = new int[xdim * ydim];
		blockEdges(src, dst, xdim, ydim);
		// Rows in parallel: the predictions read only the source (and bias
		// corrections computed up front).
		java.util.stream.IntStream.range(1, ydim).parallel().forEach(y ->
		{
			int row = head + (y - 1) / bh_ * bw;
			for(int x = 1; x < xdim - 1; x++)
			{
				int k = y * xdim + x;
				dst[k] = src[k] - ps.predict(table, ids[map[row + (x - 1) / bw_] & 0xFF], k);
			}
		});
		int sum = 0;
		for(int v : dst) sum += Math.abs(v);
		ArrayList result = new ArrayList();
		result.add(sum);
		result.add(dst);
		result.add(map);
		result.add(src[0]);
		return result;
	}

	// The cost model for channel i at a block size: reference deltas from
	// SURVEY_REFERENCE_SET, context from the chosen deltas of the channels
	// before.
	static DeltaCost channelCost(PixelSource ps, int ydim, int block, int[][] chosen_before)
	{
		int[] ref = (int[]) getBlockDeltasFromValues(ps, ydim, block, getSetIds(SURVEY_REFERENCE_SET)).get(1);
		return new DeltaCost(ps.v, ref, chosen_before, ps.xdim, ydim);
	}

	// Each channel's block deltas (lists as getBlockDeltasFromValues) for
	// its predictor ids, blocks assigned by estimated cost as the survey
	// does. Channels in order: each one's costs use the deltas of the ones
	// before.
	public static ArrayList[] getBlockDeltasByCost(int[][] channel, int xdim, int ydim, int block, int[][] ids)
	{
		return getBlockDeltasByCost(channel, xdim, ydim, block, ids, PREDICTOR_TABLE);
	}

	// As above, with the ids in the given table.
	public static ArrayList[] getBlockDeltasByCost(int[][] channel, int xdim, int ydim, int block, int[][] ids, PredictorTable table)
	{
		ArrayList[] result = new ArrayList[channel.length];
		int[][] delta = new int[channel.length][];
		PixelSource[] ps = pixelSources(channel, xdim, ydim, table);
		for(int i = 0; i < channel.length; i++)
		{
			DeltaCost dc  = channelCost(ps[i], ydim, block, Arrays.copyOf(delta, i));
			byte[]    map = prefixMaps(ps[i], ydim, block, dc, table, ids[i], new int[] {ids[i].length})[0];
			result[i] = getBlockDeltasFromMap(ps[i], ydim, map, table, ids[i]);
			delta[i]  = (int[]) result[i].get(1);
		}
		return result;
	}

	// The result of surveyBlockSets: the block size, each channel's predictor
	// ids, the coded bytes, and a printable report.
	public static final class BlockSurvey
	{
		public int     block;
		public int[][] ids = new int[3][];
		public long    bytes;
		public String  report;
	}

	public static BlockSurvey surveyBlockSets(int[][] channel, int xdim, int ydim)
	{
		return surveyBlockSets(channel, xdim, ydim, PREDICTOR_TABLE);
	}

	public static BlockSurvey surveyBlockSets(int[][] channel, int xdim, int ydim, boolean survey_shapes)
	{
		return surveyBlockSets(channel, xdim, ydim, PREDICTOR_TABLE, survey_shapes);
	}

	// As above, choosing from the ids of the given table.
	public static BlockSurvey surveyBlockSets(int[][] channel, int xdim, int ydim, PredictorTable table)
	{
		return surveyBlockSets(channel, xdim, ydim, table, false);
	}

	// As above; survey_shapes also tries the rectangles and lines.
	public static BlockSurvey surveyBlockSets(int[][] channel, int xdim, int ydim, PredictorTable table, boolean survey_shapes)
	{
		TreeMap<Integer, BlockSurvey> tried = new TreeMap<Integer, BlockSurvey>();
		StringBuilder log = new StringBuilder();
		// The block sizes are independent: surveyed at the same time (each
		// ends with a long serial stretch of real coding).
		PixelSource[] ps = pixelSources(channel, xdim, ydim, table);
		long task = 44L * xdim * ydim + 4L * table.size() * Math.min(SURVEY_SAMPLE_BLOCKS, blocksAcross(xdim, 4) * ((ydim - 1 + 3) / 4));
		int[] first = {4, 8, 16};
		BlockSurvey[] got = new BlockSurvey[first.length];
		StringBuilder[] logs = new StringBuilder[first.length];
		boundedParallel(first.length, task, t -> { logs[t] = new StringBuilder(); got[t] = surveyBlockSize(ps, ydim, first[t], table, logs[t]); });
		for(int t = 0; t < first.length; t++) { tried.put(first[t], got[t]); log.append(logs[t]); }
		int best = bestSurvey(tried);
		int s = Arrays.binarySearch(BLOCK_SEARCH_SIZES, best);
		ArrayList<Integer> next = new ArrayList<Integer>();
		for(int t : new int[] {s - 1, s + 1})
			if(t >= 0 && t < BLOCK_SEARCH_SIZES.length && !tried.containsKey(BLOCK_SEARCH_SIZES[t])) next.add(BLOCK_SEARCH_SIZES[t]);
		BlockSurvey[] more = new BlockSurvey[next.size()];
		StringBuilder[] more_log = new StringBuilder[next.size()];
		boundedParallel(next.size(), task, t -> { more_log[t] = new StringBuilder(); more[t] = surveyBlockSize(ps, ydim, next.get(t), table, more_log[t]); });
		for(int t = 0; t < more.length; t++) { tried.put(next.get(t), more[t]); log.append(more_log[t]); }
		// Then the rectangles and lines (BLOCK_SHAPES), if asked for.
		if(survey_shapes)
		{
			int[] shape = new int[BLOCK_SHAPES.length];
			for(int t = 0; t < shape.length; t++) shape[t] = SHAPE_FIRST + t;
			BlockSurvey[] sg = new BlockSurvey[shape.length];
			StringBuilder[] sl = new StringBuilder[shape.length];
			boundedParallel(shape.length, task, t -> { sl[t] = new StringBuilder(); sg[t] = surveyBlockSize(ps, ydim, shape[t], table, sl[t]); });
			for(int t = 0; t < shape.length; t++) { tried.put(shape[t], sg[t]); log.append(sl[t]); }
		}
		BlockSurvey result = tried.get(bestSurvey(tried));

		StringBuilder r = new StringBuilder("Predictor survey: bytes coded (Context-coded deltas + maps) for the first n predictors\n");
		r.append("in each channel's greedy order (by estimated bits); * marks the n kept for the channel.\n");
		r.append(log);
		r.append("Block size:");
		for(Map.Entry<Integer, BlockSurvey> e : tried.entrySet()) r.append(String.format("  %s -> %,d", shapeName(e.getKey()), e.getValue().bytes));
		r.append(String.format("   chose %s (%,d bytes)\n", shapeName(result.block), result.bytes));
		for(int i = 0; i < 3; i++)
		{
			r.append(String.format("Channel %d, %d predictors:", i, result.ids[i].length));
			for(int k = 0; k < result.ids[i].length; k++) r.append((k % 6 == 0) ? "\n    " : ", ").append(table.name(result.ids[i][k]));
			r.append('\n');
		}
		result.report = r.toString();
		return result;
	}

	// The survey at one block size only (for measuring tables).
	public static BlockSurvey surveyAtBlockSize(int[][] channel, int xdim, int ydim, int block, PredictorTable table)
	{
		return surveyBlockSize(pixelSources(channel, xdim, ydim, table), ydim, block, table, new StringBuilder());
	}

	// Which of the primitives past 21 a survey's sets use, by group
	// (GROUP_NAMES): for each primitive, the ids chosen (alone
	// or averaged) and the share of blocks using them; then each group's
	// share of blocks and the primitives nobody chose.
	public static String describeGroupUse(BlockSurvey survey, ArrayList[] coded)
	{
		PredictorTable table = PREDICTOR_TABLE;
		long total = 0;
		long[][] blocks = new long[3][table.size()];
		for(int i = 0; i < 3; i++)
		{
			byte[] map = (byte[]) coded[i].get(2);
			for(int k = mapHeaderLength(map); k < map.length; k++) { blocks[i][survey.ids[i][map[k] & 0xFF]]++; total++; }
		}
		StringBuilder r = new StringBuilder("Predictors past the basic 22 in the surveyed sets (share of all blocks):\n");
		for(int g = 0; g < GROUP_NAMES.length; g++)
		{
			int lo = GROUP_FIRST[g], hi = GROUP_FIRST[g + 1];
			long any = 0;
			for(int i = 0; i < 3; i++)
				for(int pid : survey.ids[i])
					for(int p = lo; p < hi; p++) if(table.uses(pid, p)) { any += blocks[i][pid]; break; }
			StringBuilder used = new StringBuilder(), unused = new StringBuilder();
			for(int p = lo; p < hi; p++)
			{
				boolean in_table = false;
				for(int id = 0; id < table.size(); id++) in_table |= table.uses(id, p);
				if(!in_table) continue;
				long alone = 0, avg = 0; int chosen = 0;
				for(int i = 0; i < 3; i++)
					for(int pid : survey.ids[i])
						if(table.uses(pid, p))
						{
							chosen++;
							if(table.alone(pid)) alone += blocks[i][pid]; else avg += blocks[i][pid];
						}
				String name = primitiveName(p);
				if(chosen == 0) unused.append(unused.length() == 0 ? "" : ", ").append(name);
				else used.append(String.format("      %-20s %2d ids chosen, %5.2f%% alone, %5.2f%% in averages%n", name, chosen, 100.0 * alone / total, 100.0 * avg / total));
			}
			if(used.length() == 0) r.append(String.format("  %s: unused.%n", GROUP_NAMES[g]));
			else
			{
				r.append(String.format("  %s: used, in %.2f%% of blocks.%n", GROUP_NAMES[g], 100.0 * any / total)).append(used);
				if(unused.length() > 0) r.append("      unused: ").append(unused).append('\n');
			}
		}
		return r.toString();
	}

	private static int bestSurvey(TreeMap<Integer, BlockSurvey> tried)
	{
		int best = -1;
		for(Map.Entry<Integer, BlockSurvey> e : tried.entrySet()) if(best < 0 || e.getValue().bytes < tried.get(best).bytes) best = e.getKey();
		return best;
	}

	private static BlockSurvey surveyBlockSize(PixelSource[] ps, int ydim, int block, PredictorTable table, StringBuilder log)
	{
		try
		{
			int xdim = ps[0].xdim;
			BlockSurvey result = new BlockSurvey();
			result.block = block;
			int[][]  delta = new int[3][];
			byte[][] map   = new byte[3][];
			int      bw = blocksAcross(xdim, block), bh = blocksDown(ydim, block), blocks = bw * bh;
			// Big images: the greedy order is found on every step-th block row
			// (at most SURVEY_SAMPLE_BLOCKS blocks), with the id's cost scaled to
			// match; the maps are then made over every block.
			int   step = (blocks > SURVEY_SAMPLE_BLOCKS) ? (blocks + SURVEY_SAMPLE_BLOCKS - 1) / SURVEY_SAMPLE_BLOCKS : 1;
			int[] rows = new int[(bh + step - 1) / step];
			for(int r = 0; r < rows.length; r++) rows[r] = r * step;
			int   sampled = rows.length * bw;
			log.append(String.format("  block %s%s:\n", shapeName(block), (step > 1) ? String.format(" (order from %,d of %,d blocks)", sampled, blocks) : ""));
			for(int i = 0; i < 3; i++)
			{
				DeltaCost dc    = channelCost(ps[i], ydim, block, Arrays.copyOf(delta, i));
				int[]     est   = blockCostRows(ps[i], ydim, block, dc, table, null, rows);
				int[]     order = greedyCostOrder(est, sampled, table.size(), (long) SURVEY_ID_COST * sampled / blocks);
				ArrayList<Integer> counts = new ArrayList<Integer>();
				for(int c : SURVEY_COUNTS) if(c < order.length) counts.add(c);
				counts.add(order.length);
				byte[][] prefix = null;
				if(step > 1)
				{
					est = null;
					prefix = prefixMaps(ps[i], ydim, block, dc, table, order, counts.stream().mapToInt(Integer::intValue).toArray());
				}

				long best_bytes = Long.MAX_VALUE; int worse = 0;
				StringBuilder line = new StringBuilder(String.format("    channel %d (order %d):", i, order.length));
				for(int c = 0; c < counts.size(); c++)
				{
					int       n   = counts.get(c);
					int[]     ids = Arrays.copyOf(order, n);
					byte[]    mp  = (prefix != null) ? prefix[c] : assignByCost(est, true, table.size(), ids, xdim, ydim, block);
					ArrayList r   = getBlockDeltasFromMap(ps[i], ydim, mp, table, ids);
					int[]  d = (int[]) r.get(1);
					byte[] m = (byte[]) r.get(2);
					java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
					writeMap(new java.io.DataOutputStream(bytes), 13, m, (i > 0) ? map[i - 1] : null, xdim);
					long size = bytes.size() + packContextDeltas(d, Arrays.copyOf(delta, i), xdim).length;
					line.append(String.format(" %d:%,d", n, size));
					if(size < best_bytes) { best_bytes = size; result.ids[i] = ids; delta[i] = d; map[i] = m; worse = 0; line.append('*'); }
					else if(++worse == 2) break;
				}
				result.bytes += best_bytes;
				log.append(line).append('\n');
			}
			return result;
		}
		catch(java.io.IOException e) { throw new java.io.UncheckedIOException(e); }
	}

	// Blocks across the interior (columns 1..xdim-2) and down it (rows
	// 1..ydim-1) for a block shape (see BLOCK_SHAPES).
	private static int blocksAcross(int xdim, int block) { int w = blockWidth(block, xdim); return (xdim - 2 + w - 1) / w; }
	private static int blocksDown(int ydim, int block)   { int h = blockHeight(block);      return (ydim - 1 + h - 1) / h; }

	// Block deltas with a set of predictor ids: each block takes the one
	// with the smallest sum of |delta|. ref is the previous channel (null
	// for the first). Returns [sum, deltas, map, init value].
	public static ArrayList getBlockDeltasFromValues(int[] src, int[] ref, int xdim, int ydim, int block, int[] ids)
	{
		return getBlockDeltasFromValues(new PixelSource(src, ref, xdim, ydim, PREDICTOR_TABLE), ydim, block, ids);
	}

	// As above, with the channel (and what its primitives need) in ps.
	static ArrayList getBlockDeltasFromValues(PixelSource ps, int ydim, int block, int[] ids)
	{
		int[]  src = ps.v;
		int    xdim = ps.xdim;
		int    bw = blocksAcross(xdim, block), bh = blocksDown(ydim, block), n = ids.length;
		byte[] map = new byte[2 + n + bw * bh];
		map[0] = (byte) block; map[1] = (byte) n;
		for(int p = 0; p < n; p++) map[2 + p] = (byte) ids[p];
		// Each primitive the set uses, once per pixel.
		boolean[] need = new boolean[PREDICTOR_TABLE.primitives];
		for(int id : ids) { need[PREDICTOR_TABLE.first(id)] = true; need[PREDICTOR_TABLE.second(id)] = true; }
		int m = 0; for(boolean b : need) if(b) m++;
		int[] used = new int[m]; m = 0;
		for(int q = 0; q < need.length; q++) if(need[q]) used[m++] = q;
		java.util.stream.IntStream.range(0, bh).parallel().forEach(by ->
		{
			long[] sums = new long[bw * n];
			int[]  prim = new int[PREDICTOR_TABLE.primitives];
			int    bh_ = blockHeight(block), bw_ = blockWidth(block, xdim);
			int    y0 = 1 + by * bh_, y1 = Math.min(ydim, y0 + bh_);
			for(int y = y0; y < y1; y++)
				for(int x = 1; x < xdim - 1; x++)
				{
					int k = y * xdim + x, base = (x - 1) / bw_ * n;
					for(int q : used) prim[q] = ps.primitive(q, k);
					for(int p = 0; p < n; p++) sums[base + p] += Math.abs(src[k] - PREDICTOR_TABLE.predict(ids[p], prim));
				}
			for(int bx = 0; bx < bw; bx++)
			{
				int best = 0;
				for(int p = 1; p < n; p++) if(sums[bx * n + p] < sums[bx * n + best]) best = p;
				map[2 + n + by * bw + bx] = (byte) best;
			}
		});
		return getBlockDeltasFromMap(ps, ydim, map, PREDICTOR_TABLE, ids);
	}

	// Inverse of getBlockDeltasFromValues (and of the survey's deltas): ref
	// is the previous channel's values (as decoded, before any minimum is
	// added back), or null for the first channel.
	public static int[] getValuesFromBlockDeltas(int[] delta, int xdim, int ydim, int init_value, byte[] map, int[] ref)
	{
		PredictorTable table = PREDICTOR_TABLE;
		int   block = map[0] & 0xFF, bw = blocksAcross(xdim, block), head = mapHeaderLength(map);
		int   bh_ = blockHeight(block), bw_ = blockWidth(block, xdim);
		int[] ids   = getMapIds(map);
		int[] dst   = new int[xdim * ydim];
		PixelSource ps = new PixelSource(dst, ref, xdim, table, false);
		dst[0] = init_value;
		for(int x = 1; x < xdim; x++) dst[x] = dst[x - 1] + delta[x];
		for(int y = 1; y < ydim; y++)
		{
			int k = y * xdim;
			dst[k] = dst[k - xdim] + delta[k];
			int row = head + (y - 1) / bh_ * bw;
			for(int x = 1; x < xdim - 1; x++)
			{
				k++;
				ps.prepare(k);
				dst[k] = delta[k] + ps.predict(table, ids[map[row + (x - 1) / bw_] & 0xFF], k);
				ps.commit(k);
			}
			k++;
			dst[k] = dst[k - 1] + delta[k];
		}
		return dst;
	}

	// Row 0 horizontal, column 0 vertical, last column horizontal.
	private static void blockEdges(int[] src, int[] dst, int xdim, int ydim)
	{
		for(int x = 1; x < xdim; x++) dst[x] = src[x] - src[x - 1];
		for(int y = 1; y < ydim; y++)
		{
			dst[y * xdim]            = src[y * xdim] - src[(y - 1) * xdim];
			dst[y * xdim + xdim - 1] = src[y * xdim + xdim - 1] - src[y * xdim + xdim - 2];
		}
	}

	// =========================================================================
	// Shared by the writers and readers: quantizing, choosing and undoing
	// the delta type, recombining channel sets, and writing/reading maps.
	// =========================================================================

	public static final String[] SET_NAMES = {
		"blue, green, red", "blue, red, red-green", "blue, red, blue-green",
		"blue, blue-green, red-green", "blue, blue-green, red-blue",
		"green, red, blue-green", "red, blue-green, red-green",
		"green, blue-green, red-green", "green, red-green, red-blue",
		"red, red-green, red-blue"};

	public static final String[] DELTA_TYPE_NAMES = {
		"horizontal", "vertical", "average", "med", "directional", "adaptive",
		"scanline (1)", "scanline (2)", "scanline (3)", "scanline (4)", "scanline (5)",
		"frame map (1)", "frame map (2)", "block map"};

	public static final int DELTA_TYPES = DELTA_TYPE_NAMES.length;

	// Smallest image the writers accept (the delta types need an interior).
	public static final int MIN_DIM = 4;

	// Size after Pixel Resolution (pixel_quant 0-10) resizing. Images under
	// MIN_DIM in either direction are not resized.
	public static int[] getQuantizedSize(int xdim, int ydim, int pixel_quant)
	{
		if(pixel_quant == 0 || xdim < MIN_DIM || ydim < MIN_DIM)
			return new int[] {xdim, ydim};
		double factor = pixel_quant / 10.0;
		return new int[] {xdim - (int)(factor * (xdim / 2 - 2)), ydim - (int)(factor * (ydim / 2 - 2))};
	}

	// Right-shifts a channel by pixel_shift, rounding to nearest (clamped so
	// the result never reconstructs past 255). Returns a new array: the
	// channel passed in is often the stored source data and must not change.
	public static int[] quantizeChannel(int[] channel, int pixel_shift)
	{
		if(pixel_shift == 0) return channel.clone();
		int half = 1 << (pixel_shift - 1);
		int[] rounded = new int[channel.length];
		for(int k = 0; k < channel.length; k++)
			rounded[k] = Math.min(channel[k] + half, 255);
		return shift(rounded, -pixel_shift);
	}

	// Deltas for delta_type 0-13. Returns the encoder's list: [sum, deltas,
	// map, init_value] for types 6-13, [sum, deltas, init_value] otherwise.
	// block, block_set and ref (the previous channel, null for the first)
	// are used by the block map (13) only.
	public static ArrayList getDeltas(int[] src, int xdim, int ydim, int delta_type, int variant, int block, int block_set, int[] ref)
	{
		switch(delta_type)
		{
			case 0:  return getHorizontalDeltasFromValues(src, xdim, ydim);
			case 1:  return getVerticalDeltasFromValues(src, xdim, ydim);
			case 2:  return getAverageDeltasFromValues(src, xdim, ydim);
			case 3:  return getMedDeltasFromValues(src, xdim, ydim);
			case 4:  return getDirectionalDeltasFromValues(src, xdim, ydim);
			case 5:  return getAdaptiveDeltasFromValues(src, xdim, ydim);
			case 6:  return getMixedDeltasFromValues(src, xdim, ydim);
			case 7:  return getMixedDeltasFromValues2(src, xdim, ydim);
			case 8:  return getMixedDeltasFromValues4(src, xdim, ydim);
			case 9:  return getMixedDeltasFromValues16Rows(src, xdim, ydim);
			case 10: return getMixedDeltasFromValues8Rows(src, xdim, ydim, variant);
			case 11: return getIdealDeltasFromValues8(src, xdim, ydim);
			case 12: return getIdealDeltasFromValues16(src, xdim, ydim);
			case 13: return getBlockDeltasFromValues(src, ref, xdim, ydim, block, getSetIds(block_set));
			default: throw new IllegalArgumentException("delta_type " + delta_type);
		}
	}

	public static boolean hasMap(int delta_type) { return delta_type >= 6; }

	// Inverse of getDeltas. map is ignored for types 0-5; ref (the previous
	// channel as decoded, before any minimum is added back; null for the
	// first) is used by the block map only, so its channels decode in order.
	public static int[] getValuesFromDeltas(int[] delta, int xdim, int ydim, int init_value, int delta_type, byte[] map, int variant, int[] ref)
	{
		switch(delta_type)
		{
			case 0:  return getValuesFromHorizontalDeltas(delta, xdim, ydim, init_value);
			case 1:  return getValuesFromVerticalDeltas(delta, xdim, ydim, init_value);
			case 2:  return getValuesFromAverageDeltas(delta, xdim, ydim, init_value);
			case 3:  return getValuesFromMedDeltas(delta, xdim, ydim, init_value);
			case 4:  return getValuesFromDirectionalDeltas(delta, xdim, ydim, init_value);
			case 5:  return getValuesFromAdaptiveDeltas(delta, xdim, ydim, init_value);
			case 6:  return getValuesFromMixedDeltas(delta, xdim, ydim, init_value, map);
			case 7:  return getValuesFromMixedDeltas2(delta, xdim, ydim, init_value, map);
			case 8:  return getValuesFromMixedDeltas4(delta, xdim, ydim, init_value, map);
			case 9:  return getValuesFromMixedDeltas16Rows(delta, xdim, ydim, init_value, map);
			case 10: return getValuesFromMixedDeltas8Rows(delta, xdim, ydim, init_value, map, variant);
			case 11: return getValuesFromIdealDeltas8(delta, xdim, ydim, init_value, map);
			case 12: return getValuesFromIdealDeltas16(delta, xdim, ydim, init_value, map);
			case 13: return getValuesFromBlockDeltas(delta, xdim, ydim, init_value, map, ref);
			default: throw new IllegalArgumentException("delta_type " + delta_type);
		}
	}

	// The six candidate channels: blue, green, red, then blue-green,
	// red-green and red-blue. Each difference channel is shifted by its
	// minimum so it starts at 0; min[i] holds that minimum (and the
	// minimum of the colour channels, unshifted).
	public static int[][] getCandidateChannels(int[] blue, int[] green, int[] red, int[] min)
	{
		int[][] c = {blue, green, red, getDifference(blue, green), getDifference(red, green), getDifference(red, blue)};
		for(int i = 0; i < 6; i++)
		{
			int m = Integer.MAX_VALUE;
			for(int v : c[i]) if(v < m) m = v;
			min[i] = m;
			if(i > 2) for(int k = 0; k < c[i].length; k++) c[i][k] -= m;
		}
		return c;
	}

	// Rebuilds blue, green and red from the three channels of set_id (see
	// getChannels), with the difference channels' minimums already added
	// back. The arrays passed in are not changed.
	public static int[][] getBlueGreenRed(int set_id, int[] c0, int[] c1, int[] c2)
	{
		int[] blue, green, red;
		switch(set_id)
		{
			case 0:  blue = c0; green = c1; red = c2; break;
			case 1:  blue = c0; red = c1; green = getDifference(red, c2); break;
			case 2:  blue = c0; red = c1; green = getDifference(blue, c2); break;
			case 3:  blue = c0; green = getDifference(blue, c1); red = getSum(c2, green); break;
			case 4:  blue = c0; green = getDifference(blue, c1); red = getSum(blue, c2); break;
			case 5:  green = c0; red = c1; blue = getSum(c2, green); break;
			case 6:  red = c0; green = getDifference(red, c2); blue = getSum(c1, green); break;
			case 7:  green = c0; blue = getSum(green, c1); red = getSum(green, c2); break;
			case 8:  green = c0; red = getSum(green, c1); blue = getDifference(red, c2); break;
			case 9:  red = c0; green = getDifference(red, c1); blue = getDifference(red, c2); break;
			default: throw new IllegalArgumentException("set_id " + set_id);
		}
		return new int[][] {blue, green, red};
	}

	// ---- Tables and maps on disk ---------------------------------------------

	// StringMapper rank table: short length, then one unsigned byte per
	// entry if the length is at most 255, otherwise one short per entry.
	public static void writeTable(java.io.DataOutputStream out, int[] table) throws java.io.IOException
	{
		out.writeShort(table.length);
		if(table.length <= 255) for(int v : table) out.writeByte(v);
		else                    for(int v : table) out.writeShort(v);
	}

	public static int[] readTable(java.io.DataInputStream in) throws java.io.IOException
	{
		int   n     = in.readShort();
		int[] table = new int[n];
		if(n <= 255) for(int k = 0; k < n; k++) table[k] = in.readUnsignedByte();
		else         for(int k = 0; k < n; k++) table[k] = in.readShort();
		return table;
	}

	// A delta-type map. Types 6-8 (values 0-3): int length, int packed
	// length, then 4 values per byte, low bits first. Types 9-12: a flag
	// byte, then one of
	//   0: a unary-string map -- int length, table, int min, int bit length,
	//      first value as a byte, then the string;
	//   1: an arithmetic-coded map -- int length, short K, K pairs of (value
	//      byte, int count), int coded length, coded bytes;
	//   2: a context-coded map (see mapContext) -- int length, byte K (values
	//      are 0..K-1), int coded length, coded bytes (only for K <= 32);
	//   3: a neighbour-coded map (see mapToNeighbours; format version 2) --
	//      int length, short K, int coded length, coded bytes.
	// A block map (type 13) starts with its block size, the number of
	// predictors n and their n ids (map[0..1+n], one byte each); the entries
	// after them are coded as above.
	// The writer keeps whichever is smallest. previous is the map of the
	// channel before (null for the first channel) and xdim the row width of
	// the deltas; the reader must pass the same.
	public static void writeMap(java.io.DataOutputStream out, int delta_type, byte[] map, byte[] previous, int xdim) throws java.io.IOException
	{
		if(delta_type <= 8)
		{
			byte[] packed = new byte[(map.length + 3) / 4];
			for(int q = 0; q < map.length; q++)
				packed[q >> 2] |= (byte)((map[q] & 3) << ((q & 3) << 1));
			out.writeInt(map.length); out.writeInt(packed.length); out.write(packed);
			return;
		}
		int width = mapWidth(delta_type, xdim);
		if(delta_type == 13)
		{
			// Block size, n, the n predictor ids, then the entries.
			int head = mapHeaderLength(map);
			out.write(map, 0, head);
			width    = blocksAcross(xdim, map[0] & 0xFF);
			map      = Arrays.copyOfRange(map, head, map.length);
			previous = (previous == null) ? null : Arrays.copyOfRange(previous, mapHeaderLength(previous), previous.length);
		}
		byte[][] form = {mapToString(map), mapToArithmetic(map), mapToContext(map, previous, width), mapToNeighbours(map, previous, width)};
		int best = 0;
		for(int f = 1; f < form.length; f++) if(form[f] != null && form[f].length < form[best].length) best = f;
		out.writeByte(best);
		out.write(form[best]);
	}

	public static byte[] readMap(java.io.DataInputStream in, int delta_type, byte[] previous, int xdim) throws java.io.IOException
	{
		if(delta_type <= 8)
		{
			int    n      = in.readInt();
			byte[] packed = new byte[in.readInt()];
			in.readFully(packed);
			byte[] map = new byte[n];
			for(int q = 0; q < n; q++) map[q] = (byte)((packed[q >> 2] >> ((q & 3) << 1)) & 3);
			return map;
		}
		if(delta_type == 13)
		{
			int   block = in.readUnsignedByte();
			int   n     = in.readUnsignedByte();
			if(n < 1 || n > PREDICTORS) throw new java.io.IOException("Block map with " + n + " predictors: not one this version reads.");
			int[] ids = new int[n];
			for(int p = 0; p < n; p++)
			{
				ids[p] = in.readUnsignedByte();
				if(ids[p] >= PREDICTORS) throw new java.io.IOException("Block map with predictor id " + ids[p] + ": not one this version reads.");
			}
			if(!validShape(block))
				throw new java.io.IOException("Block map with block shape " + block + ": not one this version reads.");
			byte[] body = readMapForms(in, (previous == null) ? null : Arrays.copyOfRange(previous, mapHeaderLength(previous), previous.length), blocksAcross(xdim, block));
			int    head = 2 + ids.length;
			byte[] map  = new byte[body.length + head];
			map[0] = (byte) block; map[1] = (byte) ids.length;
			for(int p = 0; p < ids.length; p++) map[2 + p] = (byte) ids[p];
			System.arraycopy(body, 0, map, head, body.length);
			for(int q = head; q < map.length; q++)
				if((map[q] & 0xFF) >= ids.length) throw new java.io.IOException("Block map entry " + (map[q] & 0xFF) + " with " + ids.length + " predictors.");
			return map;
		}
		return readMapForms(in, previous, mapWidth(delta_type, xdim));
	}

	private static byte[] readMapForms(java.io.DataInputStream in, byte[] previous, int width) throws java.io.IOException
	{
		int coding = in.readByte();
		int n      = in.readInt();
		if(coding == 3)
		{
			int    K     = in.readShort();
			byte[] coded = new byte[in.readInt()];
			in.readFully(coded);
			int[]  value = new int[n];
			// Each context needs the values before it, so the token before
			// is turned into its value first (the decoder asks in order).
			int[] token = ArithmeticMapper.getArithmeticValuesContext(coded, n, K + 3, NEIGHBOUR_CONTEXTS, (k, t) ->
			{
				if(k > 0) value[k - 1] = neighbourValue(t[k - 1], value, previous, k - 1, width);
				return neighbourContext(value, previous, k, width);
			});
			byte[] map = new byte[n];
			for(int q = 0; q < n; q++)
			{
				if(q == n - 1) value[q] = neighbourValue(token[q], value, previous, q, width);
				map[q] = (byte) value[q];
			}
			return map;
		}
		if(coding == 2)
		{
			int    K     = in.readUnsignedByte();
			byte[] coded = new byte[in.readInt()];
			in.readFully(coded);
			int[] symbol = ArithmeticMapper.getArithmeticValuesContext(coded, n, K, K * K * K * 3,
				(k, s) -> mapContext(s, previous, k, width, K));
			byte[] map = new byte[n];
			for(int q = 0; q < n; q++) map[q] = (byte) symbol[q];
			return map;
		}
		if(coding == 1)
		{
			int   K    = in.readShort();
			int[] freq = new int[256];
			int   only = 0;
			for(int q = 0; q < K; q++) { only = in.readUnsignedByte(); freq[only] = in.readInt(); }
			byte[] coded = new byte[in.readInt()];
			in.readFully(coded);
			if(K > 1) return ArithmeticMapper.getArithmeticValuesFastFenwick(coded, freq, n);
			byte[] map = new byte[n];
			Arrays.fill(map, (byte) only);
			return map;
		}
		int[]  table = readTable(in);
		int    min   = in.readInt();
		int    bits  = in.readInt();
		int    first = in.readUnsignedByte();
		byte[] str   = new byte[StringMapper.getBytelength(bits)];
		in.readFully(str);
		byte[] unpacked = StringMapper.decompressStrings(str);
		int[]  value    = StringMapper.unpackStrings(unpacked, table, n, StringMapper.getBitlength(unpacked));
		byte[] map = new byte[n];
		for(int q = 0; q < n; q++) map[q] = (byte)(value[q] + min);
		map[0] = (byte) first;   // getStringList doesn't keep the first value
		return map;
	}

	private static byte[] mapToString(byte[] map) throws java.io.IOException
	{
		int[] value = new int[map.length];
		for(int q = 0; q < map.length; q++) value[q] = map[q] & 0xFF;
		ArrayList list  = StringMapper.getStringList(value, false);
		int[]     table = (int[]) list.get(2);
		byte[]    str   = (byte[]) list.get(3);
		int       bits  = StringMapper.getBitlength(str);
		java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
		java.io.DataOutputStream out = new java.io.DataOutputStream(bytes);
		out.writeInt(map.length); writeTable(out, table); out.writeInt((int) list.get(0)); out.writeInt(bits);
		out.writeByte(map[0]); out.write(str, 0, StringMapper.getBytelength(bits));
		return bytes.toByteArray();
	}

	// Row width of a map: one entry per row for the scanline types (9, 10),
	// one per interior pixel for the frame maps (11, 12). (The block map's
	// width depends on its block size; see blocksAcross.)
	private static int mapWidth(int delta_type, int xdim)
	{
		return (delta_type <= 10) ? 1 : xdim - 2;
	}

	// Context of map entry k: its left and upper neighbours, the entry at
	// the same place in the previous channel's map, and whether the upper-
	// left neighbour equals the left one, the upper one, or neither.
	// Measured with MapTest: 13-24% smaller than the string or arithmetic
	// forms. Missing neighbours count as 0.
	private static int mapContext(int[] m, byte[] previous, int k, int width, int K)
	{
		int x     = k % width;
		int left  = (x > 0) ? m[k - 1] : 0;
		int up    = (k >= width) ? m[k - width] : 0;
		int ul    = (x > 0 && k >= width) ? m[k - width - 1] : 0;
		int prev  = (previous != null) ? previous[k] & 0xFF : 0;
		int shape = (ul == left) ? 0 : (ul == up) ? 1 : 2;
		return ((left * K + up) * K + prev) * 3 + shape;
	}

	private static final int CONTEXT_MAP_MAX = 32;

	// Form 3, the neighbour form: each entry coded as "same as the left
	// entry" (0), "same as the one above" (1), "same as the previous
	// channel's map here" (2), or 3 + its value, the first that applies.
	// The context (NEIGHBOUR_CONTEXTS) is which of those neighbours, and the
	// upper-left one, agree with each other. Any number of values works, so
	// it suits large predictor sets; it measured 0.05-0.3% smaller than the
	// other forms on block maps (MapIdeaTest). Missing neighbours never match.
	private static final int NEIGHBOUR_CONTEXTS = 32;

	private static int neighbour(int[] v, byte[] previous, int k, int width, int which)
	{
		int x = k % width;
		switch(which)
		{
			case 0:  return (x > 0) ? v[k - 1] : -1;
			case 1:  return (k >= width) ? v[k - width] : -1;
			case 2:  return (previous != null) ? previous[k] & 0xFF : -1;
			default: return (x > 0 && k >= width) ? v[k - width - 1] : -1;
		}
	}

	private static int neighbourContext(int[] v, byte[] previous, int k, int width)
	{
		int L = neighbour(v, previous, k, width, 0), U = neighbour(v, previous, k, width, 1);
		int V = neighbour(v, previous, k, width, 2), D = neighbour(v, previous, k, width, 3);
		int c = 0;
		if(L >= 0 && L == U) c |= 1;
		if(V >= 0 && V == L) c |= 2;
		if(V >= 0 && V == U) c |= 4;
		if(D >= 0 && D == L) c |= 8;
		if(D >= 0 && D == U) c |= 16;
		return c;
	}

	private static int neighbourValue(int token, int[] v, byte[] previous, int k, int width)
	{
		return (token < 3) ? neighbour(v, previous, k, width, token) : token - 3;
	}

	private static byte[] mapToNeighbours(byte[] map, byte[] previous, int width) throws java.io.IOException
	{
		int n = map.length, K = 1;
		int[] v = new int[n];
		for(int q = 0; q < n; q++) { v[q] = map[q] & 0xFF; K = Math.max(K, v[q] + 1); }
		int[] token = new int[n], context = new int[n];
		for(int k = 0; k < n; k++)
		{
			int s = v[k];
			int L = neighbour(v, previous, k, width, 0), U = neighbour(v, previous, k, width, 1), V = neighbour(v, previous, k, width, 2);
			token[k]   = (L >= 0 && s == L) ? 0 : (U >= 0 && s == U) ? 1 : (V >= 0 && s == V) ? 2 : 3 + s;
			context[k] = neighbourContext(v, previous, k, width);
		}
		byte[] coded = ArithmeticMapper.getIntervalValueContext(token, K + 3, context, NEIGHBOUR_CONTEXTS);
		java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
		java.io.DataOutputStream out = new java.io.DataOutputStream(bytes);
		out.writeInt(n); out.writeShort(K); out.writeInt(coded.length); out.write(coded);
		return bytes.toByteArray();
	}

	private static byte[] mapToContext(byte[] map, byte[] previous, int width) throws java.io.IOException
	{
		int K = 1;
		for(byte b : map) K = Math.max(K, (b & 0xFF) + 1);
		if(previous != null) for(byte b : previous) K = Math.max(K, (b & 0xFF) + 1);
		// Its K^3 x 3 contexts, each with K counts, are all set up at once
		// (ArithmeticMapper.getIntervalValueContext), so it is only offered
		// for up to CONTEXT_MAP_MAX values; larger sets use the other forms.
		if(K > CONTEXT_MAP_MAX) return null;
		int[] symbol  = new int[map.length];
		for(int q = 0; q < map.length; q++) symbol[q] = map[q] & 0xFF;
		int[] context = new int[map.length];
		for(int q = 0; q < map.length; q++) context[q] = mapContext(symbol, previous, q, width, K);
		byte[] coded = ArithmeticMapper.getIntervalValueContext(symbol, K, context, K * K * K * 3);
		java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
		java.io.DataOutputStream out = new java.io.DataOutputStream(bytes);
		out.writeInt(map.length); out.writeByte(K); out.writeInt(coded.length); out.write(coded);
		return bytes.toByteArray();
	}

	private static byte[] mapToArithmetic(byte[] map) throws java.io.IOException
	{
		int[] freq = new int[256];
		for(byte b : map) freq[b & 0xFF]++;
		int K = 0;
		for(int v : freq) if(v > 0) K++;
		java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
		java.io.DataOutputStream out = new java.io.DataOutputStream(bytes);
		out.writeInt(map.length);
		out.writeShort(K);
		for(int v = 0; v < 256; v++) if(freq[v] > 0) { out.writeByte(v); out.writeInt(freq[v]); }
		if(K <= 1) out.writeInt(0);
		else { byte[] coded = ArithmeticMapper.getIntervalValueFastFenwick(map, freq); out.writeInt(coded.length); out.write(coded); }
		return bytes.toByteArray();
	}

	// ---- Context coding of deltas ------------------------------------------
	//
	// For the Context entropy type: the deltas are coded directly, as ranks
	// (most frequent value = 0), with ArithmeticMapper's context coder. The
	// context of a pixel combines how busy its neighbourhood is -- |left| +
	// |above| + |above-left| + |above-right| of the deltas already coded,
	// in 12 buckets -- with the size of the deltas at the same pixel in the
	// channels coded before it (5 buckets), so channels must be decoded in
	// order. Measured with ModelTest: 4-9.5% smaller than the Adaptive type.

	private static final int[] ACTIVITY_LIMIT = {0, 1, 2, 3, 5, 7, 10, 14, 20, 28, 40, 60};
	private static final int[] CROSS_LIMIT    = {0, 1, 3, 6, 12};
	public  static final int   CONTEXTS       = ACTIVITY_LIMIT.length * CROSS_LIMIT.length;

	// Context of pixel k of delta d (row width xdim); previous holds the
	// deltas of the channels coded before this one.
	public static int getContext(int[] d, int[][] previous, int k, int xdim)
	{
		int x = k % xdim, activity = 0, cross = 0;
		if(x > 0) activity += Math.abs(d[k - 1]);
		if(k >= xdim)
		{
			activity += Math.abs(d[k - xdim]);
			if(x > 0)        activity += Math.abs(d[k - xdim - 1]);
			if(x < xdim - 1) activity += Math.abs(d[k - xdim + 1]);
		}
		for(int[] p : previous) cross += Math.abs(p[k]);
		return bucket(activity, ACTIVITY_LIMIT) * CROSS_LIMIT.length + bucket(cross, CROSS_LIMIT);
	}

	private static int bucket(int v, int[] limit)
	{
		int b = 0;
		while(b + 1 < limit.length && v >= limit[b + 1]) b++;
		return b;
	}

	// A channel's deltas, context coded: int min, the rank table (value ->
	// rank, see writeTable), int coded length, coded bytes. The number of
	// deltas is not stored (it is xdim * ydim).
	public static byte[] packContextDeltas(int[] delta, int[][] previous, int xdim) throws java.io.IOException
	{
		int min = Integer.MAX_VALUE, max = Integer.MIN_VALUE;
		for(int v : delta) { if(v < min) min = v; if(v > max) max = v; }
		int[] count = new int[max - min + 1];
		for(int v : delta) count[v - min]++;

		// Rank by count, most frequent first (ties: smaller value first).
		Integer[] order = new Integer[count.length];
		for(int v = 0; v < count.length; v++) order[v] = v;
		Arrays.sort(order, (a, b) -> (count[a] != count[b]) ? count[b] - count[a] : a - b);
		int[] rank = new int[count.length];
		for(int r = 0; r < order.length; r++) rank[order[r]] = r;

		int[] symbol  = new int[delta.length];
		int[] context = new int[delta.length];
		for(int k = 0; k < delta.length; k++)
		{
			symbol[k]  = rank[delta[k] - min];
			context[k] = getContext(delta, previous, k, xdim);
		}
		byte[] coded = ArithmeticMapper.getIntervalValueContext(symbol, rank.length, context, CONTEXTS);

		java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
		java.io.DataOutputStream out = new java.io.DataOutputStream(bytes);
		out.writeInt(min);
		writeTable(out, rank);
		out.writeInt(coded.length);
		out.write(coded);
		return bytes.toByteArray();
	}

	public static int[] readContextDeltas(java.io.DataInputStream in, int n, int[][] previous, int xdim) throws java.io.IOException
	{
		int    min   = in.readInt();
		int[]  rank  = readTable(in);
		byte[] coded = new byte[in.readInt()];
		in.readFully(coded);

		int[] value = new int[rank.length];
		for(int v = 0; v < rank.length; v++) value[rank[v]] = v + min;

		// Deltas are filled in as the decoder asks for each context.
		int[] delta = new int[n];
		int[] done  = {0};
		int[] symbol = ArithmeticMapper.getArithmeticValuesContext(coded, n, rank.length, CONTEXTS, (k, s) ->
		{
			while(done[0] < k) { delta[done[0]] = value[s[done[0]]]; done[0]++; }
			return getContext(delta, previous, k, xdim);
		});
		for(int k = done[0]; k < n; k++) delta[k] = value[symbol[k]];
		return delta;
	}

	public static int[] getChannels(int set_id)
	{
		int[] channel = new int[3];
		if(set_id==0)      { channel[0]=0; channel[1]=1; channel[2]=2; }
		else if(set_id==1) { channel[0]=0; channel[1]=2; channel[2]=4; }
		else if(set_id==2) { channel[0]=0; channel[1]=2; channel[2]=3; }
		else if(set_id==3) { channel[0]=0; channel[1]=3; channel[2]=4; }
		else if(set_id==4) { channel[0]=0; channel[1]=3; channel[2]=5; }
		else if(set_id==5) { channel[0]=1; channel[1]=2; channel[2]=3; }
		else if(set_id==6) { channel[0]=2; channel[1]=3; channel[2]=4; }
		else if(set_id==7) { channel[0]=1; channel[1]=3; channel[2]=4; }
		else if(set_id==8) { channel[0]=1; channel[1]=4; channel[2]=5; }
		else if(set_id==9) { channel[0]=2; channel[1]=4; channel[2]=5; }
		return channel;
	}
}
