import java.util.ArrayList;
import java.util.Hashtable;
import java.util.Collections;

/**
 * This is a class with methods to analyze and process bit strings.
 *
 * @author Brian Crowley
 * @version 1.0
 */
public class StringMapper
{
	/**
	 * Creates a histogram from a byte array and returns a list including the
	 * histogram and some other key information.
	 */
	public static ArrayList getHistogram(byte src[])
	{
		int[] value = new int[src.length];
		for (int i = 0; i < src.length; i++)
			value[i] = (src[i] < 0) ? src[i] + 256 : src[i];

		int min = value[0], max = value[0];
		for (int i = 0; i < src.length; i++)
		{
			if (value[i] > max) max = value[i];
			if (value[i] < min) min = value[i];
		}

		int range = max - min + 1;
		int[] histogram = new int[range];
		for (int i = 0; i < src.length; i++)
			histogram[value[i] - min]++;

		ArrayList histogram_list = new ArrayList();
		histogram_list.add(min);
		histogram_list.add(histogram);
		histogram_list.add(range);
		return histogram_list;
	}

	/**
	 * Creates a histogram from an int array and returns a list including the
	 * histogram and some other key information.
	 */
	public static ArrayList getHistogram(int src[])
	{
		int min = src[0], max = src[0];
		for (int i = 0; i < src.length; i++)
		{
			if (src[i] > max) max = src[i];
			if (src[i] < min) min = src[i];
		}

		int range = max - min + 1;
		int[] histogram = new int[range];
		for (int i = 0; i < src.length; i++)
			histogram[src[i] - min]++;

		ArrayList histogram_list = new ArrayList();
		histogram_list.add(min);
		histogram_list.add(histogram);
		histogram_list.add(range);
		return histogram_list;
	}

	/**
	 * Creates a rank table from most popular to least popular from a histogram.
	 */
	public static int[] getRankTable(int src[])
	{
		ArrayList list    = new ArrayList();
		Hashtable table   = new Hashtable();
		for (int i = 0; i < src.length; i++)
		{
			double key = src[i];
			while (table.containsKey(key))
				key += .001;
			table.put(key, i);
			list.add(key);
		}
		Collections.sort(list);

		int[] rank = new int[src.length];
		int k = -1;
		for (int i = src.length - 1; i >= 0; i--)
		{
			double key = (double) list.get(i);
			int j = (int) table.get(key);
			rank[j] = ++k;
		}
		return rank;
	}

	/**
	 * Packs an int array into a unary bit string using the rank table.
	 * Allocates the output buffer and sets the trailing data byte internally.
	 */
	public static byte[] packStrings(int[] src, int[] table)
	{
		// Compute the total bit length of the packed output.
		int bitlength = 0;
		for (int i = 0; i < src.length; i++)
		{
			if (table[src[i]] != table.length - 1)
				bitlength += table[src[i]] + 1;
			else
				bitlength += table.length - 1;
		}

		// Byte length needed for the bit data, rounded up.
		int bytelength = bitlength / 8;
		if (bitlength % 8 != 0)
			bytelength++;

		// +1 for the trailing data byte written by setData().
		byte[] dst = new byte[bytelength + 1];

		int max_length = table.length - 1;
		int[] mask = {1, 3, 7, 15, 31, 63, 127, 255};

		int start = 0;
		int stop  = 0;
		int j     = 0;

		for (int i : src)
		{
			int k = table[i];
			if (k == 0)
			{
				start++;
				if (start == 8)
				{
					j++;
					start = 0;
				}
			}
			else
			{
				stop = (start + k + 1) % 8;
				if (k == max_length)
					stop = (stop > 0) ? stop - 1 : 7;

				if (k <= 7)
				{
					dst[j] = (byte)((dst[j] | (mask[k - 1] << start)) & 0xFF);
					if (stop <= start)
					{
						j++;
						if (stop != 0)
							dst[j] = (byte)((dst[j] | (mask[k - 1] >> (8 - start))) & 0xFF);
					}
				}
				else
				{
					dst[j] = (byte)((dst[j] | (mask[7] << start)) & 0xFF);
					int m = (k - 8) / 8;
					for (int n = 0; n < m; n++)
					{
						j++;
						dst[j] = (byte) mask[7];
					}
					j++;
					if (start != 0)
						dst[j] = (byte)((dst[j] | (mask[7] >> (8 - start))) & 0xFF);
					if (k % 8 != 0)
					{
						m = k % 8 - 1;
						dst[j] = (byte)((dst[j] | (mask[m] << start)) & 0xFF);
						if (stop <= start)
						{
							j++;
							if (stop != 0)
								dst[j] = (byte)((dst[j] | (mask[m] >> (8 - start))) & 0xFF);
						}
					}
					else if (stop <= start && k != max_length)
						j++;
				}
				start = stop;
			}
		}

		double zero_ratio = getZeroRatio(dst, bitlength);
		int type = (zero_ratio < .5) ? 1 : 0;
		setData(type, 0, bitlength, dst);
		return dst;
	}

	/**
	 * Unpacks a unary bit string into an int array using the rank table.
	 */
	public static int[] unpackStrings(byte[] src, int[] table, int size, int bitlength)
	{
		int n          = table.length;
		int max_length = n - 1;
		int[] dst      = new int[size];

		int[] inverse_table = new int[n];
		for (int i = 0; i < n; i++)
			inverse_table[table[i]] = i;

		// Cap bitlength to the actual bits encoded in src.  A trailing-zero edge
		// case in compressZeroBits can leave the decompressed length one bit off
		// from what was stored in channel_compressed_length, causing an AIOOBE.
		bitlength = Math.min(bitlength, getBitlength(src));

		int length    = 1;
		int src_byte  = 0;
		int dst_byte  = 0;
		int bit       = 0;
		int bits_read = 0;

		try
		{
			while (dst_byte < size && bits_read < bitlength)
			{
				int non_zero = src[src_byte] & (1 << bit);
				if (non_zero != 0 && length < max_length)
					length++;
				else if (non_zero == 0)
				{
					dst[dst_byte++] = inverse_table[length - 1];
					length = 1;
				}
				else if (length == max_length)
				{
					dst[dst_byte++] = inverse_table[length];
					length = 1;
				}
				bit++;
				bits_read++;
				if (bit == 8)
				{
					bit = 0;
					src_byte++;
				}
			}
		}
		catch (Exception e)
		{
			System.out.println(e);
			System.out.println("Exiting unpackStrings with an exception.");
			e.printStackTrace();
		}
		return dst;
	}

	// -----------------------------------------------------------------------
	// Zero-bit compression (stop bits = zeros)
	// -----------------------------------------------------------------------

	public static int[] compressZeroBits(byte src[], int size, byte dst[])
	{
		int[] result = new int[2];
		for (int i = 0; i < dst.length; i++) dst[i] = 0;
		int current_byte = 0, current_bit = 0;
		byte mask = 0x01;
		dst[0] = 0;

		int i = 0, j = 0, k = 0;
		try
		{
			for (i = 0; i < size; i++)
			{
				if ((src[k] & (mask << j)) == 0 && i < size - 1)
				{
					i++; j++;
					if (j == 8) { j = 0; k++; }
					if ((src[k] & (mask << j)) == 0)
					{
						current_bit++;
						if (current_bit == 8) { current_byte++; current_bit = 0; }
					}
					else
					{
						dst[current_byte] |= (byte) mask << current_bit;
						current_bit++;
						if (current_bit == 8) { current_byte++; current_bit = dst[current_byte] = 0; }
						dst[current_byte] |= (byte) mask << current_bit;
						current_bit++;
						if (current_bit == 8) { current_byte++; current_bit = 0; }
					}
				}
				else if ((src[k] & (mask << j)) == 0 && i == size - 1)
				{
					dst[current_byte] |= (byte) mask << current_bit;
					current_bit++;
					if (current_bit == 8) { current_byte++; current_bit = 0; }
					result[1] = 1;
				}
				else
				{
					dst[current_byte] |= (byte) mask << current_bit;
					current_bit++;
					if (current_bit == 8) { current_byte++; current_bit = 0; }
					current_bit++;
					if (current_bit == 8) { current_byte++; current_bit = 0; }
				}
				j++;
				if (j == 8) { j = 0; k++; }
			}
		}
		catch (Exception e)
		{
			System.out.println(e.toString());
			System.out.println("Exiting compressZeroBits with an exception.");
			e.printStackTrace();
		}
		result[0] = current_byte * 8 + current_bit;
		return result;
	}

	public static int decompressZeroBits(byte src[], int size, byte dst[])
	{
		for (int i = 0; i < dst.length; i++) dst[i] = 0;
		int current_byte = 0, current_bit = 0;
		byte mask = 0x01;

		int i = 0, j = 0, k = 0;
		for (i = 0; i < size; i++)
		{
			if ((src[k] & (mask << j)) != 0 && i < size - 1)
			{
				i++; j++;
				if (j == 8) { j = 0; k++; }
				if ((src[k] & (mask << j)) != 0)
				{
					// "11" → "01"
					current_bit++;
					if (current_bit == 8) { current_byte++; current_bit = 0; }
					if (current_byte >= dst.length - 1) break;
					dst[current_byte] |= (byte) mask << current_bit;
					current_bit++;
					if (current_bit == 8) { current_byte++; current_bit = 0; }
				}
				else
				{
					// "10" → "1"
					if (current_byte >= dst.length - 1) break;
					dst[current_byte] |= (byte) mask << current_bit;
					current_bit++;
					if (current_bit == 8) { current_byte++; current_bit = 0; }
				}
			}
			else if ((src[k] & (mask << j)) != 0 && i == size - 1)
			{
				// "1" at end → "0"
				current_bit++;
				if (current_bit == 8) { current_byte++; current_bit = 0; }
			}
			else
			{
				// "0" → "00"
				current_bit++;
				if (current_bit == 8) { current_byte++; current_bit = 0; }
				current_bit++;
				if (current_bit == 8) { current_byte++; current_bit = 0; }
			}
			j++;
			if (j == 8) { j = 0; k++; }
		}
		return current_byte * 8 + current_bit;
	}

	// -----------------------------------------------------------------------
	// One-bit compression (run bits = ones)
	// -----------------------------------------------------------------------

	public static int[] compressOneBits(byte src[], int size, byte dst[])
	{
		int[] result = new int[2];
		for (int i = 0; i < dst.length; i++) dst[i] = 0;
		int current_byte = 0, current_bit = 0;
		byte mask = 0x01;

		int i = 0, j = 0, k = 0;
		for (i = 0; i < size; i++)
		{
			if ((src[k] & (mask << j)) != 0 && i < size - 1)
			{
				i++; j++;
				if (j == 8) { j = 0; k++; }
				if ((src[k] & (mask << j)) != 0)
				{
					dst[current_byte] |= (byte) mask << current_bit;
					current_bit++;
					if (current_bit == 8) { current_byte++; current_bit = 0; }
				}
				else
				{
					current_bit++;
					if (current_bit == 8) { current_byte++; current_bit = 0; }
					dst[current_byte] |= (byte) mask << current_bit;
					current_bit++;
					if (current_bit == 8) { current_byte++; current_bit = 0; }
				}
			}
			else if ((src[k] & (mask << j)) != 0 && i == size - 1)
			{
				current_bit++;
				if (current_bit == 8) { current_byte++; current_bit = 0; }
				result[1] = 1;
			}
			else
			{
				current_bit++;
				if (current_bit == 8) { current_byte++; current_bit = 0; }
				current_bit++;
				if (current_bit == 8) { current_byte++; current_bit = 0; }
			}
			j++;
			if (j == 8) { j = 0; k++; }
		}
		result[0] = current_byte * 8 + current_bit;
		return result;
	}

	public static int decompressOneBits(byte src[], int size, byte dst[])
	{
		for (int i = 0; i < dst.length; i++) dst[i] = 0;
		int current_byte = 0, current_bit = 0;
		byte mask = 0x01;

		int i = 0, j = 0, k = 0;
		for (i = 0; i < size; i++)
		{
			if ((src[k] & (mask << j)) == 0 && i < size - 1)
			{
				i++; j++;
				if (j == 8) { j = 0; k++; }
				if ((src[k] & (mask << j)) == 0)
				{
					// "00" → "0"
					current_bit++;
					if (current_bit == 8) { current_byte++; current_bit = 0; }
				}
				else
				{
					// "01" → "10"
					if (current_byte >= dst.length - 1) break;
					dst[current_byte] |= (byte) mask << current_bit;
					current_bit++;
					if (current_bit == 8) { current_byte++; current_bit = 0; }
					current_bit++;
					if (current_bit == 8) { current_byte++; current_bit = 0; }
				}
			}
			else if ((src[k] & (mask << j)) == 0 && i == size - 1)
			{
				// "0" at end → "1"
				if (current_byte >= dst.length - 1) break;
				dst[current_byte] |= (byte) mask << current_bit;
				current_bit++;
				if (current_bit == 8) { current_byte++; current_bit = 0; }
			}
			else
			{
				// "1" → "11"
				if (current_byte >= dst.length - 1) break;
				dst[current_byte] |= (byte) mask << current_bit;
				current_bit++;
				if (current_bit == 8) { current_byte++; current_bit = 0; }
				if (current_byte >= dst.length - 1) break;
				dst[current_byte] |= (byte) mask << current_bit;
				current_bit++;
				if (current_bit == 8) { current_byte++; current_bit = 0; }
			}
			j++;
			if (j == 8) { j = 0; k++; }
		}
		return current_byte * 8 + current_bit;
	}

	// -----------------------------------------------------------------------
	// High-level compress / decompress (single method each)
	// -----------------------------------------------------------------------

	public static byte[] compressStrings(byte[] src)
	{
		int    bit_length  = getBitlength(src);
		int    zero_amount = getCompressionAmount(src, bit_length, 0);
		int    one_amount  = getCompressionAmount(src, bit_length, 1);
		int    limit       = 15;

		// Choose the transform type that offers compression.
		int transform_type = (zero_amount <= one_amount) ? 0 : 1;

		if (transform_type == 0 && zero_amount >= 0)
			return src.clone();
		if (transform_type == 1 && one_amount >= 0)
			return src.clone();

		byte[] buffer1 = new byte[src.length * 2 + 16];
		byte[] buffer2 = new byte[src.length * 2 + 16];

		int[] result;
		int   compressed_length;
		int   amount;

		if (transform_type == 0)
		{
			result            = compressZeroBits(src, bit_length, buffer1);
			compressed_length = result[0];
			amount            = getCompressionAmount(buffer1, compressed_length, 0);
		}
		else
		{
			result            = compressOneBits(src, bit_length, buffer1);
			compressed_length = result[0];
			amount            = getCompressionAmount(buffer1, compressed_length, 1);
		}

		int iterations = 1;
		while (amount < 0 && iterations < limit)
		{
			int previous_length = compressed_length;
			if (iterations % 2 == 1)
			{
				if (transform_type == 0)
					result = compressZeroBits(buffer1, previous_length, buffer2);
				else
					result = compressOneBits(buffer1, previous_length, buffer2);
				compressed_length = result[0];
				iterations++;
				amount = getCompressionAmount(buffer2, compressed_length, transform_type);
			}
			else
			{
				if (transform_type == 0)
					result = compressZeroBits(buffer2, previous_length, buffer1);
				else
					result = compressOneBits(buffer2, previous_length, buffer1);
				compressed_length = result[0];
				iterations++;
				amount = getCompressionAmount(buffer1, compressed_length, transform_type);
			}
		}

		int    bytelength = getBytelength(compressed_length);
		byte[] dst        = new byte[bytelength];
		if (iterations % 2 == 0)
			System.arraycopy(buffer2, 0, dst, 0, bytelength - 1);
		else
			System.arraycopy(buffer1, 0, dst, 0, bytelength - 1);
		setData(transform_type, iterations, compressed_length, dst);

		// Only return the compressed result if savings exceed the threshold.
		// A marginal reduction can increase Huffman entropy and hurt overall
		// compression.
		if (compressed_length < bit_length - (int)(bit_length * compress_threshold))
			return dst;
		else
			return src.clone();
	}

	public static double compress_threshold = 0.05;  // default 5% savings required

	public static byte[] decompressStrings(byte[] string)
	{
		int iterations = getIterations(string);
		if (iterations == 0 || iterations == 16)
			return string;

		int    bitlength  = getBitlength(string);
		int    type       = getType(string);
		int    bytelength = getBytelength(bitlength);

		// getIterations returns actual_iterations + (type==1 ? 16 : 0),
		// so mask out the type bit to get the true loop count.
		iterations = iterations & 15;

		byte[] buffer1 = new byte[bytelength * 2 + 16];
		byte[] buffer2 = new byte[bytelength * 2 + 16];

		int uncompressed_length;
		if (type == 0)
			uncompressed_length = decompressZeroBits(string, bitlength, buffer1);
		else
			uncompressed_length = decompressOneBits(string, bitlength, buffer1);

		// Track which buffer holds the current data rather than relying on
		// iterations%2 parity, which gives the wrong source buffer for odd
		// stored iteration counts >= 3.
		boolean inBuffer1 = true;
		iterations--;
		while (iterations > 0)
		{
			int previous_length = uncompressed_length;
			int need            = getBytelength(previous_length * 2 + 16);
			if (inBuffer1)
			{
				// source = buffer1, output = buffer2
				if (buffer2.length < need) buffer2 = new byte[need];
				if (type == 0)
					uncompressed_length = decompressZeroBits(buffer1, previous_length, buffer2);
				else
					uncompressed_length = decompressOneBits(buffer1, previous_length, buffer2);
			}
			else
			{
				// source = buffer2, output = buffer1
				if (buffer1.length < need) buffer1 = new byte[need];
				if (type == 0)
					uncompressed_length = decompressZeroBits(buffer2, previous_length, buffer1);
				else
					uncompressed_length = decompressOneBits(buffer2, previous_length, buffer1);
			}
			inBuffer1 = !inBuffer1;
			iterations--;
		}

		int    out_bytelength = getBytelength(uncompressed_length);
		byte[] dst            = new byte[out_bytelength];
		if (inBuffer1)
			System.arraycopy(buffer1, 0, dst, 0, out_bytelength - 1);
		else
			System.arraycopy(buffer2, 0, dst, 0, out_bytelength - 1);
		setData(type, 0, uncompressed_length, dst);
		return dst;
	}

	// -----------------------------------------------------------------------
	// getStringList
	// -----------------------------------------------------------------------

	public static ArrayList getStringList(int[] value)
	{
		ArrayList string_list = new ArrayList();

		ArrayList histogram_list = getHistogram(value);
		int       min_value      = (int)   histogram_list.get(0);
		int[]     histogram      = (int[]) histogram_list.get(1);
		int       value_range    = (int)   histogram_list.get(2);
		int[]     string_table   = getRankTable(histogram);

		value[0] = value_range / 2;
		for (int i = 1; i < value.length; i++)
			value[i] -= min_value;

		byte[] string    = packStrings(value, string_table);
		int    bitlength = getBitlength(string);
		byte[] compressed_string = compressStrings(string);

		string_list.add(min_value);
		string_list.add(bitlength);
		string_list.add(string_table);
		string_list.add(compressed_string);
		return string_list;
	}

	public static ArrayList getStringList(int[] value, boolean compress)
	{
		ArrayList string_list = new ArrayList();

		ArrayList histogram_list = getHistogram(value);
		int       min_value      = (int)   histogram_list.get(0);
		int[]     histogram      = (int[]) histogram_list.get(1);
		int       value_range    = (int)   histogram_list.get(2);
		int[]     string_table   = getRankTable(histogram);

		value[0] = value_range / 2;
		for (int i = 1; i < value.length; i++)
			value[i] -= min_value;

		byte[] string    = packStrings(value, string_table);
		int    bitlength = getBitlength(string);

		string_list.add(min_value);
		string_list.add(bitlength);
		string_list.add(string_table);
		string_list.add(compress ? compressStrings(string) : string);
		return string_list;
	}

	// -----------------------------------------------------------------------
	// Utility methods
	// -----------------------------------------------------------------------

	public static int getBitlength(byte[] string)
	{
		byte last_byte  = string[string.length - 1];
		byte extra_bits = (byte)((last_byte >> 5) & 7);
		return (string.length - 1) * 8 - extra_bits;
	}

	public static byte getIterations(byte[] string)
	{
		return (byte)(string[string.length - 1] & 31);
	}

	public static void setIterations(int iterations, byte[] string)
	{
		byte mask = SegmentMapper.getLeadingMask(3);
		string[string.length - 1] &= mask;
		string[string.length - 1] |= (byte) iterations;
	}

	public static byte getType(byte[] string)
	{
		byte iterations = (byte)(string[string.length - 1] & 31);
		return (iterations > 15) ? (byte) 1 : (byte) 0;
	}

	public static int getBytelength(int bitlength)
	{
		int bytelength = bitlength / 8;
		if (bitlength % 8 != 0) bytelength++;
		return bytelength + 1;   // +1 for the trailing data byte
	}

	public static void setData(int type, int iterations, int bitlength, byte[] string)
	{
		if (type == 1) iterations += 16;
		int  odd_bits   = bitlength % 8;
		byte extra_bits = 0;
		if (odd_bits != 0) extra_bits = (byte)(8 - odd_bits);
		extra_bits <<= 5;
		string[string.length - 1]  = (byte) iterations;
		string[string.length - 1] |= extra_bits;
	}

	public static int[] getBitTable()
	{
		int[] table = new int[256];
		byte value = 0, mask = 1;
		for (int i = 0; i < 256; i++)
		{
			int sum = 0;
			for (int j = 0; j < 8; j++)
				if ((value & (mask << j)) == 0) sum++;
			table[i] = sum;
			value++;
		}
		return table;
	}

	public static double getZeroRatio(byte[] string, int bit_length)
	{
		int[] table      = getBitTable();
		int   byte_length = bit_length / 8;
		int   zero_sum   = 0, one_sum = 0;

		for (int i = 0; i < byte_length; i++)
		{
			int j = (int) string[i];
			if (j < 0) j += 256;
			zero_sum += table[j];
			one_sum  += 8 - table[j];
		}

		byte mask      = 1;
		int  remainder = bit_length % 8;
		for (int i = 0; i < remainder; i++)
		{
			if ((string[byte_length] & (mask << i)) == 0) zero_sum++;
			else                                           one_sum++;
		}
		return (double) zero_sum / (zero_sum + one_sum);
	}

	public static double getZeroRatio(byte[] string, int bit_length, int[] table)
	{
		int byte_length = bit_length / 8;
		int zero_sum    = 0, one_sum = 0;

		for (int i = 0; i < byte_length; i++)
		{
			int j = (int) string[i];
			if (j < 0) j += 256;
			zero_sum += table[j];
			one_sum  += 8 - table[j];
		}

		byte mask      = 1;
		int  remainder = bit_length % 8;
		for (int i = 0; i < remainder; i++)
		{
			if ((string[byte_length] & (mask << i)) == 0) zero_sum++;
			else                                           one_sum++;
		}
		return (double) zero_sum / (zero_sum + one_sum);
	}

	public static int getCompressionAmount(byte[] string, int bit_length, int transform_type)
	{
		int positive = 0, negative = 0;
		byte mask       = 1;
		int  byte_length = bit_length / 8;

		if (transform_type == 0)
		{
			int previous = 1;
			for (int i = 0; i < byte_length; i++)
				for (int j = 0; j < 8; j++)
				{
					int k = string[i] & (mask << j);
					if      (k != 0 && previous != 0) positive++;
					else if (k != 0)                  previous = 1;
					else if (k == 0 && previous != 0) previous = 0;
					else                             { negative++; previous = 1; }
				}
			int remainder = bit_length % 8;
			for (int i = 0; i < remainder; i++)
			{
				int j = string[byte_length] & (mask << i);
				if      (j != 0 && previous != 0) positive++;
				else if (j != 0)                  previous = 1;
				else if (j == 0 && previous != 0) previous = 0;
				else                            { negative++; previous = 1; }
			}
		}
		else
		{
			int previous = 0;
			for (int i = 0; i < byte_length; i++)
				for (int j = 0; j < 8; j++)
				{
					int k = string[i] & (mask << j);
					if      (k == 0 && previous == 0) positive++;
					else if (k == 0)                  previous = 0;
					else if (k != 0 && previous == 0) previous = 1;
					else                            { negative++; previous = 0; }
				}
			int remainder = bit_length % 8;
			for (int i = 0; i < remainder; i++)
			{
				int j = string[byte_length] & (mask << i);
				if      (j == 0 && previous == 0) positive++;
				else if (j == 0)                  previous = 0;
				else if (j != 0 && previous == 0) previous = 1;
				else                            { negative++; previous = 0; }
			}
		}
		return positive - negative;
	}
}
