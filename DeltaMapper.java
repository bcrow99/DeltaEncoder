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
	// squares, and each uses the predictor from its set (BLOCK_SETS) with
	// the smallest sum of |delta| over the square. Row 0 is horizontal,
	// column 0 vertical and the last column horizontal, as in frame map 2.
	// The map is [block size, set, then one entry per block, row by row].
	// Measured with FrameTest and BlockSetTest: 1-3% smaller than scanline
	// 4 on most images; block sizes 8-16 do about equally well, and the
	// Scanline 16 and Neighbours 8 sets best (a set without the plain
	// neighbours, and one of gradients only, both did worse). The Blends 32
	// set (SetSearch) adds another 0.5% lossless, 1% at Colour Resolution 3.
	// =========================================================================

	public static final int BLOCK_MIN = 4, BLOCK_MAX = 32, BLOCK_DEFAULT = 8;

	public static final String[] BLOCK_SET_NAMES = {"Scanline 16", "No Neighbours 16", "Neighbours 8", "Basic 4", "Blends 32"};

	// Predictors, as ids for blockPredictor (a = left, b = above,
	// c = above-left, d = above-right):
	//   0 a            1 b            2 c            3 d
	//   4 (a+b)/2      5 (b+c)/2      6 (a+c)/2      7 (b+d)/2
	//   8 (c+d)/2      9 (a+b+c+d)/4 10 a+b-c       11 MED(a,b,c)
	//  12 (3a+b)/4    13 (a+3b)/4    14 (3a+d)/4    15 (3b+a)/4
	//  16 (a+d)/2     17 a+(b-c)/2   18 b+(a-c)/2   19 a+d-b
	//  20 (a+b+d)/3   21 b+(d-c)/2
	// A set entry of 32 or more is the average of two of these:
	// 32 * (i + 1) + j means (P_i + P_j + 1) / 2 (see setPredictor).
	private static final int[][] BLOCK_SETS = {
		// Scanline 16: the scanline 4 set (pred16).
		{ 0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15 },
		// No Neighbours 16: only averages, blends and gradients -- no plain
		// neighbour, which a flat or smoothly varying block rarely wants.
		{ 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19 },
		// Neighbours 8: the four neighbours, MED, gradient and two averages
		// about as good as Scanline 16 with a cheaper map.
		{ 0, 1, 2, 3, 11, 4, 10, 9 },
		// Basic 4: MED, gradient, and two averages -- a cheap map.
		{ 11, 10, 4, 9 },
		// Blends 32: picked by SetSearch from all 253 predictors and
		// averages of two, greedily, on 7 test images at Colour Resolution
		// 0 and 3 (tested leave-one-out: 0.5% smaller than the best of the
		// sets above lossless, 1.1% at Colour Resolution 3; a few
		// flat, synthetic images do better with the sets above).
		{ 11, 564, 37, 275, 1, 165, 46, 267, 102, 169, 368, 7, 178, 3, 0, 176,
		  66, 75, 6, 145, 50, 69, 168, 403, 307, 181, 101, 38, 80, 561, 231, 4 },
	};

	// Prediction for a set entry: a predictor, or the average of two.
	public static int setPredictor(int entry, int a, int b, int c, int d)
	{
		if(entry < 32) return blockPredictor(entry, a, b, c, d);
		return (blockPredictor(entry / 32 - 1, a, b, c, d) + blockPredictor(entry % 32, a, b, c, d) + 1) >> 1;
	}

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
			default: return b + ((d - c) >> 1);
		}
	}

	public static int blockSetSize(int set) { return BLOCK_SETS[set].length; }

	// ---- Choosing the block size and predictor set -----------------------

	// Block sizes findBestBlock may pick.
	public static final int[] BLOCK_SEARCH_SIZES = {4, 6, 8, 12, 16, 24, 32};

	// Bytes the three channels take as block map deltas (Context coded,
	// packContextDeltas) plus maps (writeMap), as the writers save them.
	public static long getBlockMapBytes(int[][] channel, int xdim, int ydim, int block, int set)
	{
		try
		{
			int[][] d = new int[3][]; byte[][] m = new byte[3][];
			for(int i = 0; i < 3; i++)
			{
				ArrayList result = getDeltas(channel[i], xdim, ydim, 13, 0, block, set);
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
	// the others). Runs the trials in parallel.
	public static int[] findBestBlock(int[][] channel, int xdim, int ydim, long[][] bytes)
	{
		int   n_sets = BLOCK_SET_NAMES.length;
		int[] first  = {0, 2, 4};   // 4, 8, 16
		for(long[] row : bytes) Arrays.fill(row, -1);
		java.util.stream.IntStream.range(0, n_sets * first.length).parallel().forEach(t ->
		{
			int set = t / first.length, s = first[t % first.length];
			bytes[set][s] = getBlockMapBytes(channel, xdim, ydim, BLOCK_SEARCH_SIZES[s], set);
		});
		int best_set = 0, best_s = first[0];
		for(int set = 0; set < n_sets; set++)
			for(int s : first) if(bytes[set][s] < bytes[best_set][best_s]) { best_set = set; best_s = s; }

		final int bs = best_set;
		int[] next = {best_s - 1, best_s + 1};
		java.util.stream.IntStream.range(0, 2).parallel().forEach(t ->
		{
			int s = next[t];
			if(s >= 0 && s < BLOCK_SEARCH_SIZES.length && bytes[bs][s] < 0)
				bytes[bs][s] = getBlockMapBytes(channel, xdim, ydim, BLOCK_SEARCH_SIZES[s], bs);
		});
		for(int s = 0; s < BLOCK_SEARCH_SIZES.length; s++) if(bytes[bs][s] >= 0 && bytes[bs][s] < bytes[bs][best_s]) best_s = s;
		return new int[] {BLOCK_SEARCH_SIZES[best_s], bs};
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

	private static int blocksAcross(int xdim, int block) { return (xdim - 2 + block - 1) / block; }

	public static ArrayList getBlockDeltasFromValues(int[] src, int xdim, int ydim, int block, int set)
	{
		int[]  dst = new int[xdim * ydim];
		int[]  ids = BLOCK_SETS[set];
		int    bw  = blocksAcross(xdim, block), bh = (ydim - 1 + block - 1) / block;
		byte[] map = new byte[2 + bw * bh];
		map[0] = (byte) block; map[1] = (byte) set;
		boolean blends = false;
		for(int e : ids) if(e >= 32) blends = true;
		int[] prim = new int[22];
		blockEdges(src, dst, xdim, ydim);
		for(int by = 0; by < bh; by++)
			for(int bx = 0; bx < bw; bx++)
			{
				int  y0 = 1 + by * block, y1 = Math.min(ydim, y0 + block);
				int  x0 = 1 + bx * block, x1 = Math.min(xdim - 1, x0 + block);
				long[] sums = new long[ids.length];
				for(int y = y0; y < y1; y++)
					for(int x = x0; x < x1; x++)
					{
						int k = y * xdim + x, a = src[k - 1], b = src[k - xdim], c = src[k - xdim - 1], d = src[k - xdim + 1];
						if(blends)
						{
							// Each primitive once, then the set's entries from them.
							for(int q = 0; q < 22; q++) prim[q] = blockPredictor(q, a, b, c, d);
							for(int p = 0; p < ids.length; p++)
							{
								int e = ids[p];
								int v = (e < 32) ? prim[e] : (prim[e / 32 - 1] + prim[e % 32] + 1) >> 1;
								sums[p] += Math.abs(src[k] - v);
							}
						}
						else
							for(int p = 0; p < ids.length; p++) sums[p] += Math.abs(src[k] - blockPredictor(ids[p], a, b, c, d));
					}
				int best = 0;
				for(int p = 1; p < ids.length; p++) if(sums[p] < sums[best]) best = p;
				map[2 + by * bw + bx] = (byte) best;
				for(int y = y0; y < y1; y++)
					for(int x = x0; x < x1; x++)
					{
						int k = y * xdim + x;
						dst[k] = src[k] - setPredictor(ids[best], src[k - 1], src[k - xdim], src[k - xdim - 1], src[k - xdim + 1]);
					}
			}
		int sum = 0;
		for(int v : dst) sum += Math.abs(v);

		ArrayList result = new ArrayList();
		result.add(sum);
		result.add(dst);
		result.add(map);
		result.add(src[0]);
		return result;
	}

	public static int[] getValuesFromBlockDeltas(int[] delta, int xdim, int ydim, int init_value, byte[] map)
	{
		int   block = map[0], bw = blocksAcross(xdim, block);
		int[] ids   = BLOCK_SETS[map[1]];
		int[] dst   = new int[xdim * ydim];
		dst[0] = init_value;
		for(int x = 1; x < xdim; x++) dst[x] = dst[x - 1] + delta[x];
		for(int y = 1; y < ydim; y++)
		{
			int k = y * xdim;
			dst[k] = dst[k - xdim] + delta[k];
			int row = 2 + (y - 1) / block * bw;
			for(int x = 1; x < xdim - 1; x++)
			{
				k++;
				int id = ids[map[row + (x - 1) / block]];
				dst[k] = delta[k] + setPredictor(id, dst[k - 1], dst[k - xdim], dst[k - xdim - 1], dst[k - xdim + 1]);
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
	public static ArrayList getDeltas(int[] src, int xdim, int ydim, int delta_type, int variant)
	{
		return getDeltas(src, xdim, ydim, delta_type, variant, BLOCK_DEFAULT, 0);
	}

	// block and block_set are used by the block map (13) only.
	public static ArrayList getDeltas(int[] src, int xdim, int ydim, int delta_type, int variant, int block, int block_set)
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
			case 13: return getBlockDeltasFromValues(src, xdim, ydim, block, block_set);
			default: throw new IllegalArgumentException("delta_type " + delta_type);
		}
	}

	public static boolean hasMap(int delta_type) { return delta_type >= 6; }

	// Inverse of getDeltas. map is ignored for types 0-5.
	public static int[] getValuesFromDeltas(int[] delta, int xdim, int ydim, int init_value, int delta_type, byte[] map, int variant)
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
			case 13: return getValuesFromBlockDeltas(delta, xdim, ydim, init_value, map);
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
	//      are 0..K-1), int coded length, coded bytes.
	// A block map (type 13) starts with its block size and predictor set
	// bytes (map[0], map[1]); the rest is coded as above.
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
			// Block size and predictor set, then the entries.
			out.writeByte(map[0]); out.writeByte(map[1]);
			width    = blocksAcross(xdim, map[0]);
			map      = Arrays.copyOfRange(map, 2, map.length);
			previous = (previous == null) ? null : Arrays.copyOfRange(previous, 2, previous.length);
		}
		byte[][] form = {mapToString(map), mapToArithmetic(map), mapToContext(map, previous, width)};
		int best = 0;
		for(int f = 1; f < 3; f++) if(form[f].length < form[best].length) best = f;
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
			byte block = in.readByte(), set = in.readByte();
			if(set < 0 || set >= BLOCK_SET_NAMES.length || block < BLOCK_MIN || block > BLOCK_MAX)
				throw new java.io.IOException("Block map with predictor set " + set + " and block size " + block + ": not one this version reads.");
			byte[] body = readMapForms(in, (previous == null) ? null : Arrays.copyOfRange(previous, 2, previous.length), blocksAcross(xdim, block));
			byte[] map  = new byte[body.length + 2];
			map[0] = block; map[1] = set;
			System.arraycopy(body, 0, map, 2, body.length);
			return map;
		}
		return readMapForms(in, previous, mapWidth(delta_type, xdim));
	}

	private static byte[] readMapForms(java.io.DataInputStream in, byte[] previous, int width) throws java.io.IOException
	{
		int coding = in.readByte();
		int n      = in.readInt();
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
		int prev  = (previous != null) ? previous[k] : 0;
		int shape = (ul == left) ? 0 : (ul == up) ? 1 : 2;
		return ((left * K + up) * K + prev) * 3 + shape;
	}

	private static byte[] mapToContext(byte[] map, byte[] previous, int width) throws java.io.IOException
	{
		int K = 1;
		for(byte b : map) K = Math.max(K, (b & 0xFF) + 1);
		if(previous != null) for(byte b : previous) K = Math.max(K, (b & 0xFF) + 1);
		int[] symbol  = new int[map.length];
		for(int q = 0; q < map.length; q++) symbol[q] = map[q];
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
