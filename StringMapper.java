import java.util.ArrayList;
import java.util.Arrays;

/**
 * Unary bit strings and their compression.
 * <p>
 * Values are coded as unary strings by frequency rank ({@link #getRankTable},
 * {@link #packStrings}): rank k is k one bits and a terminating zero, so the
 * most frequent value costs one bit. The strings can then be shortened by
 * repeated bit-pair transforms ({@link #compressStrings}), which pay off when
 * the string is dominated by runs of zeros or of ones.
 * <p>
 * <b>The string format.</b> A string is its data bits, least significant bit
 * first within each byte, followed by one trailing data byte:
 * <ul>
 * <li>bits 0-3: how many compression passes were applied (0 to 15);</li>
 * <li>bit 4: the transform used, 0 for zero-bit ({@link #compressZeroBits}),
 *     1 for one-bit ({@link #compressOneBits});</li>
 * <li>bits 5-7: how many bits of the last data byte are unused.</li>
 * </ul>
 * In source order (the method details below follow it): the string format,
 * bit counts, unary strings, and bit-pair compression.
 *
 * @author Brian Crowley
 * @version 1.0
 */
public class StringMapper
{
	/**
	 * The share of the bits a compression must save for {@link #compressStrings}
	 * to keep it (default 0.10, 10%). A marginal reduction can raise the Huffman
	 * entropy of the result and hurt the overall compression.
	 */
	public static double compress_threshold = 0.10;  // default 10% savings required

	/** Not used: all of this class's methods are static. */
	public StringMapper() {}

	// ---- The string format: data bits, then a trailing data byte (see the class description) 

	/**
	 * The bytes a string needs for its data bits, including the trailing data
	 * byte.
	 *
	 * @param bitlength the number of data bits
	 * @return the length of the string in bytes
	 */
	public static int getBytelength(int bitlength)
	{
		int bytelength = bitlength / 8;
		if (bitlength % 8 != 0) bytelength++;
		return bytelength + 1;   // +1 for the trailing data byte
	}

	/**
	 * The number of data bits in a string, from its trailing data byte.
	 *
	 * @param string a string, with its trailing data byte
	 * @return the number of data bits
	 */
	public static int getBitlength(byte[] string)
	{
		byte last_byte  = string[string.length - 1];
		byte extra_bits = (byte)((last_byte >> 5) & 7);
		return (string.length - 1) * 8 - extra_bits;
	}

	/**
	 * Writes a string's trailing data byte.
	 *
	 * @param type       the transform, 0 (zero-bit) or 1 (one-bit)
	 * @param iterations the number of compression passes (0 to 15)
	 * @param bitlength  the number of data bits
	 * @param string     the string; its last byte is overwritten
	 */
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

	/**
	 * The low five bits of the trailing data byte: the number of compression
	 * passes, plus 16 if the one-bit transform was used (see {@link #getType}).
	 *
	 * @param string a string, with its trailing data byte
	 * @return passes + 16 * type
	 */
	public static byte getIterations(byte[] string)
	{
		return (byte)(string[string.length - 1] & 31);
	}

	/**
	 * Sets the low five bits of the trailing data byte, keeping the unused-bit
	 * count in the top three.
	 *
	 * @param iterations the number of passes, plus 16 for the one-bit transform
	 * @param string     the string; its last byte is changed
	 */
	public static void setIterations(int iterations, byte[] string)
	{
		byte mask = SegmentMapper.getLeadingMask(3);
		string[string.length - 1] &= mask;
		string[string.length - 1] |= (byte) iterations;
	}

	/**
	 * The transform a string was compressed with.
	 *
	 * @param string a string, with its trailing data byte
	 * @return 0 for the zero-bit transform, 1 for the one-bit transform
	 */
	public static byte getType(byte[] string)
	{
		byte iterations = (byte)(string[string.length - 1] & 31);
		return (iterations > 15) ? (byte) 1 : (byte) 0;
	}

	// ---- Bit counts ------------------------------------------------------------------

	/**
	 * The number of zero bits in each byte value.
	 *
	 * @return 256 counts, table[b] = zero bits in b
	 */
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

	/**
	 * The share of zero bits in the first bit_length bits of a string.
	 *
	 * @param string     the bits
	 * @param bit_length how many bits to count
	 * @return zero bits / bit_length
	 */
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

	/**
	 * As {@link #getZeroRatio(byte[], int)}, with the table of zero-bit counts
	 * given (so it isn't rebuilt on every call).
	 *
	 * @param string     the bits
	 * @param bit_length how many bits to count
	 * @param table      {@link #getBitTable}'s result
	 * @return zero bits / bit_length
	 */
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

	// ---- Unary strings: values coded by frequency rank -------------------------------

	/**
	 * A histogram of byte values (taken as unsigned, 0 to 255), from the smallest
	 * value present to the largest.
	 *
	 * @param src the bytes (at least one)
	 * @return [min (Integer), histogram (int[]: histogram[v - min] is the count
	 *         of v), range (Integer: max - min + 1)]
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
	 * A histogram of int values, from the smallest value present to the largest.
	 *
	 * @param src the values (at least one)
	 * @return [min (Integer), histogram (int[]: histogram[v - min] is the count
	 *         of v), range (Integer: max - min + 1)]
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
	 * A rank table from a histogram: the most frequent entry gets rank 0, the next
	 * rank 1, and so on. Among equal counts the later entry ranks first, as in
	 * earlier versions (which made the same tables whenever no count was shared
	 * by more than 1,000 entries).
	 *
	 * @param src a histogram
	 * @return rank[i] for each entry i of the histogram
	 */
	public static int[] getRankTable(int src[])
	{
		int n = src.length;
		Integer[] order = new Integer[n];
		for (int i = 0; i < n; i++)
			order[i] = i;
		// Largest count first; equal counts keep the old order (later bin first).
		Arrays.sort(order, (a, b) -> (src[a] != src[b]) ? Integer.compare(src[b], src[a]) : Integer.compare(b, a));
		int[] rank = new int[n];
		for (int k = 0; k < n; k++)
			rank[order[k]] = k;
		return rank;
	}

	/**
	 * Packs values into a unary string by rank: a value v of rank k = table[v] is
	 * k one bits and then a zero, except the last rank (table.length - 1), which
	 * needs no terminating zero. The trailing data byte records no compression
	 * passes, with the type set by which bit is more common.
	 *
	 * @param src   the values, each an index into table
	 * @param table the rank table, from {@link #getRankTable}
	 * @return the string, with its trailing data byte
	 * @see #unpackStrings
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

		// +1 for the trailing data byte written by setData().
		byte[] dst = new byte[getBytelength(bitlength)];

		// The bits go in from the low bit of each byte up, gathered in acc
		// (held of them) and written out a byte at a time. A value of rank k
		// is k ones and then a zero (already there), or just the ones for the
		// last rank.
		int  max_length = table.length - 1;
		long acc  = 0;
		int  held = 0, j = 0;
		for (int i : src)
		{
			int k = table[i];
			int ones = k;
			for (; ones >= 32; ones -= 32)
			{
				acc |= 0xFFFFFFFFL << held;
				held += 32;
				for (; held >= 8; held -= 8, acc >>>= 8) dst[j++] = (byte) acc;
			}
			acc  |= ((1L << ones) - 1) << held;
			held += ones + ((k != max_length) ? 1 : 0);
			for (; held >= 8; held -= 8, acc >>>= 8) dst[j++] = (byte) acc;
		}
		if (held > 0)
			dst[j] = (byte) acc;

		double zero_ratio = getZeroRatio(dst, bitlength);
		int type = (zero_ratio < .5) ? 1 : 0;
		setData(type, 0, bitlength, dst);
		return dst;
	}

	/**
	 * Unpacks a unary string made by {@link #packStrings}. bitlength is capped at
	 * the length the string's own data byte records. A malformed string prints
	 * the exception and returns what was unpacked before it.
	 *
	 * @param src       the string (uncompressed), with its trailing data byte
	 * @param table     the rank table it was packed with
	 * @param size      the number of values
	 * @param bitlength the number of data bits
	 * @return the values
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

		// Bit by bit, "length" counts from 1: a one adds to it (up to
		// max_length), a zero ends the value (rank length - 1), and a one when
		// it's already max_length ends the last rank's value. Here the bits
		// are taken up to 56 at a time, and each run of zeros or ones in them
		// at once.
		int length   = 1;
		int dst_byte = 0;
		int i        = 0;

		try
		{
			while (dst_byte < size && i < bitlength)
			{
				long x     = bitsFrom(src, i);
				int  avail = Math.min(56, bitlength - i);
				int  used  = 0;
				while (used < avail && dst_byte < size)
				{
					if ((x & 1) == 0)
					{
						// z zeros: the first ends the value under way, each
						// other one is a value of rank 0.
						int z = Math.min(Long.numberOfTrailingZeros(x), avail - used);
						dst[dst_byte++] = inverse_table[length - 1];
						length = 1;
						for (int k = 1; k < z && dst_byte < size; k++)
							dst[dst_byte++] = inverse_table[0];
						x >>>= z;
						used += z;
					}
					else
					{
						int ones = Math.min(Long.numberOfTrailingZeros(~x), avail - used);
						x >>>= ones;
						used += ones;
						if (max_length < 1)
							continue;   // one value: ones mean nothing
						while (ones > 0 && dst_byte < size)
						{
							if (length < max_length)
							{
								int m = Math.min(max_length - length, ones);
								length += m;
								ones   -= m;
							}
							else
							{
								dst[dst_byte++] = inverse_table[length];
								length = 1;
								ones--;
							}
						}
					}
				}
				i += used;
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

	/**
	 * Up to 64 bits of a string from bit i on, the first in the low bit
	 * (zeros past the end of src).
	 *
	 * @param src the string
	 * @param i   the first bit
	 * @return the bits
	 */
	private static long bitsFrom(byte[] src, int i)
	{
		int  start = i >>> 3, shift = i & 7;
		long x     = 0;
		for (int j = 0; j < 8 && start + j < src.length; j++)
			x |= (long) (src[start + j] & 0xFF) << (8 * j);
		x >>>= shift;
		if (shift != 0 && start + 8 < src.length)
			x |= (long) (src[start + 8] & 0xFF) << (64 - shift);
		return x;
	}

	/**
	 * Unary-codes a set of values by their frequency rank, and compresses the
	 * string if that saves enough ({@link #compressStrings}).
	 * <p>
	 * Note: this changes value in place. value[0] is overwritten with range / 2,
	 * and the other values have the minimum subtracted.
	 *
	 * @param value the values (changed, see above)
	 * @return [min (Integer), bit length of the uncompressed string (Integer),
	 *         rank table (int[]), the string (byte[], compressed or not)]
	 */
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

	/**
	 * As {@link #getStringList(int[])}, with compression optional.
	 * <p>
	 * Note: this changes value in place. value[0] is overwritten with range / 2,
	 * and the other values have the minimum subtracted.
	 *
	 * @param value    the values (changed, see above)
	 * @param compress whether to try {@link #compressStrings} on the string
	 * @return [min (Integer), bit length of the uncompressed string (Integer),
	 *         rank table (int[]), the string (byte[])]
	 */
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

	// ---- Bit-pair compression: zero-bit and one-bit transforms, applied repeatedly ---
	//
	// Each transform, and each undoing of one, is a machine with two states:
	// 0 at the start of a pair, 1 halfway through one (its first bit read).
	// stepRule says what one input bit does: the bits it writes and the next
	// state; an input ending halfway through a pair finishes with its lone
	// bit's (endRule). The methods below run them a byte at a time through
	// tables made from those rules (BYTE_STEP), a bit at a time only at the
	// end of the input: the same bits as going a bit at a time throughout.

	/** The transforms, as indices into the tables: compressZeroBits. */
	private static final int COMPRESS_ZERO   = 0;
	/** compressOneBits. */
	private static final int COMPRESS_ONE    = 1;
	/** decompressZeroBits. */
	private static final int DECOMPRESS_ZERO = 2;
	/** decompressOneBits. */
	private static final int DECOMPRESS_ONE  = 3;

	/**
	 * What one input bit does in each transform and state: the bits written
	 * (low 16 bits, the first in the lowest), how many (the next 8 bits) and
	 * the next state (the top 8 bits). [transform][state][bit]
	 */
	private static final int[][][] BIT_STEP  = new int[4][2][2];

	/** The same for a whole input byte (its bits from the low one up). [transform][state][byte] */
	private static final int[][][] BYTE_STEP = new int[4][2][256];

	/**
	 * getCompressionAmount's count for a whole byte, from each "previous"
	 * (low 8 bits: the amount + 128; the next 8 bits: the next "previous").
	 * [transform_type][previous][byte]
	 */
	private static final int[][][] AMOUNT    = new int[2][2][256];

	static
	{
		for (int t = 0; t < 4; t++)
			for (int state = 0; state < 2; state++)
				for (int bit = 0; bit < 2; bit++)
					BIT_STEP[t][state][bit] = stepRule(t, state, bit);
		for (int t = 0; t < 4; t++)
			for (int state = 0; state < 2; state++)
				for (int b = 0; b < 256; b++)
				{
					int s = state, pattern = 0, count = 0;
					for (int j = 0; j < 8; j++)
					{
						int step = BIT_STEP[t][s][(b >> j) & 1];
						pattern |= (step & 0xFFFF) << count;
						count   += (step >>> 16) & 0xFF;
						s        = step >>> 24;
					}
					BYTE_STEP[t][state][b] = pattern | (count << 16) | (s << 24);
				}
		for (int type = 0; type < 2; type++)
			for (int previous = 0; previous < 2; previous++)
				for (int b = 0; b < 256; b++)
				{
					int p = previous, sum = 0;
					for (int j = 0; j < 8; j++)
					{
						int step = amountRule(type, p, (b >> j) & 1);
						sum += (step & 0xFF) - 128;
						p    = step >>> 8;
					}
					AMOUNT[type][previous][b] = (sum + 128) | (p << 8);
				}
	}

	/**
	 * One input bit of a transform: in state 0 a pair's first bit only moves
	 * to state 1 (a bit that can't start a pair is written at once), in state
	 * 1 the pair is complete.
	 * <pre>
	 * compressZeroBits    "00" -&gt; "0", "01" -&gt; "11", "1" -&gt; "10"
	 * compressOneBits     "11" -&gt; "1", "10" -&gt; "01", "0" -&gt; "00"
	 * decompressZeroBits  "0" -&gt; "00", "11" -&gt; "01", "10" -&gt; "1"
	 * decompressOneBits   "1" -&gt; "11", "00" -&gt; "0", "01" -&gt; "10"
	 * </pre>
	 *
	 * @param transform COMPRESS_ZERO, COMPRESS_ONE, DECOMPRESS_ZERO or DECOMPRESS_ONE
	 * @param state     0 or 1
	 * @param bit       the input bit
	 * @return as BIT_STEP's entries
	 */
	private static int stepRule(int transform, int state, int bit)
	{
		// The bit that starts a pair (rather than standing alone).
		int pair_start = (transform == COMPRESS_ZERO || transform == DECOMPRESS_ONE) ? 0 : 1;
		int pattern, count;
		if (state == 0)
		{
			if (bit == pair_start)
				return 1 << 24;
			count = 2;
			pattern = (transform == COMPRESS_ZERO) ? 1 : (transform == DECOMPRESS_ONE) ? 3 : 0;   // "10", "11", "00"
			return pattern | (count << 16);
		}
		switch (transform)
		{
			case COMPRESS_ZERO:   pattern = bit == 1 ? 3 : 0; count = bit == 1 ? 2 : 1; break;   // "01" -> "11", "00" -> "0"
			case COMPRESS_ONE:    pattern = bit == 1 ? 1 : 2; count = bit == 1 ? 1 : 2; break;   // "11" -> "1",  "10" -> "01"
			case DECOMPRESS_ZERO: pattern = bit == 1 ? 2 : 1; count = bit == 1 ? 2 : 1; break;   // "11" -> "01", "10" -> "1"
			default:              pattern = bit == 1 ? 1 : 0; count = bit == 1 ? 2 : 1; break;   // "01" -> "10", "00" -> "0"
		}
		return pattern | (count << 16);
	}

	/**
	 * The bit a transform writes for an input ending halfway through a pair
	 * (its lone last bit): compressZeroBits "0" -&gt; "1", compressOneBits
	 * "1" -&gt; "0", decompressZeroBits "1" -&gt; "0", decompressOneBits
	 * "0" -&gt; "1".
	 *
	 * @param transform COMPRESS_ZERO, COMPRESS_ONE, DECOMPRESS_ZERO or DECOMPRESS_ONE
	 * @return the bit
	 */
	private static int endRule(int transform)
	{
		return (transform == COMPRESS_ZERO || transform == DECOMPRESS_ONE) ? 1 : 0;
	}

	/**
	 * Runs a transform over size bits of src into dst (zeroed first). The
	 * undoing ones stop early rather than write a one into dst's last byte;
	 * as the bit-at-a-time code did, zeros past dst's end are only counted.
	 *
	 * @param transform COMPRESS_ZERO, COMPRESS_ONE, DECOMPRESS_ZERO or DECOMPRESS_ONE
	 * @param src       the input bits
	 * @param size      the number of input bits
	 * @param dst       the output
	 * @param lone      if not null, gets 1 in lone[0] if the input ended in a lone bit (else 0)
	 * @return the number of output bits
	 */
	private static int transformBits(int transform, byte[] src, int size, byte[] dst, int[] lone)
	{
		Arrays.fill(dst, (byte) 0);
		int[][] table   = BYTE_STEP[transform];
		boolean undoing = transform == DECOMPRESS_ZERO || transform == DECOMPRESS_ONE;
		int     limit   = dst.length - 1;   // where the undoing ones stop
		long    acc     = 0;                // output bits waiting (held of them)
		int     held    = 0, out = 0, state = 0, i = 0;

		// Whole bytes, while no stop could come in one (a byte writes at most 2).
		for (; i + 8 <= size && (!undoing || out + 3 < limit); i += 8)
		{
			int step = table[state][src[i >>> 3] & 0xFF];
			acc  |= (long) (step & 0xFFFF) << held;
			held += (step >>> 16) & 0xFF;
			state = step >>> 24;
			for (; held >= 8; held -= 8, acc >>>= 8, out++) if (out < dst.length) dst[out] = (byte) acc;
		}
		// The rest a bit at a time, checking for the stop before each one.
		boolean stopped = false;
		for (; i < size && !stopped; i++)
		{
			int step  = BIT_STEP[transform][state][(src[i >>> 3] >> (i & 7)) & 1];
			int count = (step >>> 16) & 0xFF;
			state     = step >>> 24;
			for (int j = 0; j < count; j++)
			{
				int bit = (step >> j) & 1;
				if (undoing && bit == 1 && out >= limit) { stopped = true; break; }
				acc  |= (long) bit << held;
				held++;
				for (; held >= 8; held -= 8, acc >>>= 8, out++) if (out < dst.length) dst[out] = (byte) acc;
			}
		}
		if (!stopped && state == 1)
		{
			int bit = endRule(transform);
			if (!(undoing && bit == 1 && out >= limit))
			{
				acc |= (long) bit << held;
				held++;
				for (; held >= 8; held -= 8, acc >>>= 8, out++) if (out < dst.length) dst[out] = (byte) acc;
			}
		}
		if (held > 0 && out < dst.length)
			dst[out] = (byte) acc;
		if (lone != null)
			lone[0] = (!stopped && state == 1) ? 1 : 0;
		return out * 8 + held;
	}

	/**
	 * One bit of getCompressionAmount's count, its four cases.
	 *
	 * @param type     0 for the zero-bit transform, 1 for the one-bit
	 * @param previous the count's "previous" (0 or 1)
	 * @param k        the bit
	 * @return the amount + 128 (low 8 bits) and the next "previous" (the next 8)
	 */
	private static int amountRule(int type, int previous, int k)
	{
		int amount;
		if (type == 0)
		{
			if      (k != 0 && previous != 0) amount = 1;
			else if (k != 0)                { amount = 0; previous = 1; }
			else if (previous != 0)         { amount = 0; previous = 0; }
			else                            { amount = -1; previous = 1; }
		}
		else
		{
			if      (k == 0 && previous == 0) amount = 1;
			else if (k == 0)                { amount = 0; previous = 0; }
			else if (previous == 0)         { amount = 0; previous = 1; }
			else                            { amount = -1; previous = 0; }
		}
		return (amount + 128) | (previous << 8);
	}

	/**
	 * One pass of the zero-bit transform. Reading the input bits in order: "00"
	 * becomes "0", "01" becomes "11", "1" becomes "10", and a lone "0" at the end
	 * becomes "1". It shortens strings where pairs of zeros outnumber lone ones.
	 *
	 * @param src  the input bits
	 * @param size the number of input bits
	 * @param dst  the output, zeroed here; up to twice the input's length
	 * @return {the number of output bits, 1 if the input ended in a lone zero
	 *         (else 0)}
	 * @see #decompressZeroBits
	 */
	public static int[] compressZeroBits(byte src[], int size, byte dst[])
	{
		int[] result = new int[2];
		try
		{
			int[] lone = new int[1];
			result[0] = transformBits(COMPRESS_ZERO, src, size, dst, lone);
			result[1] = lone[0];
		}
		catch (Exception e)
		{
			System.out.println(e.toString());
			System.out.println("Exiting compressZeroBits with an exception.");
			e.printStackTrace();
		}
		return result;
	}

	/**
	 * Undoes one pass of {@link #compressZeroBits}: "0" becomes "00", "11"
	 * becomes "01", "10" becomes "1", and a lone "1" at the end becomes "0".
	 * Stops early rather than overflow dst.
	 *
	 * @param src  the input bits
	 * @param size the number of input bits
	 * @param dst  the output, zeroed here
	 * @return the number of output bits
	 */
	public static int decompressZeroBits(byte src[], int size, byte dst[])
	{
		return transformBits(DECOMPRESS_ZERO, src, size, dst, null);
	}

	/**
	 * One pass of the one-bit transform, the mirror of
	 * {@link #compressZeroBits}: "11" becomes "1", "10" becomes "01", "0" becomes
	 * "00", and a lone "1" at the end becomes "0". It shortens strings where pairs
	 * of ones outnumber lone zeros.
	 *
	 * @param src  the input bits
	 * @param size the number of input bits
	 * @param dst  the output, zeroed here; up to twice the input's length
	 * @return {the number of output bits, 1 if the input ended in a lone one
	 *         (else 0)}
	 * @see #decompressOneBits
	 */
	public static int[] compressOneBits(byte src[], int size, byte dst[])
	{
		int[] result = new int[2];
		int[] lone = new int[1];
		result[0] = transformBits(COMPRESS_ONE, src, size, dst, lone);
		result[1] = lone[0];
		return result;
	}

	/**
	 * Undoes one pass of {@link #compressOneBits}: "1" becomes "11", "01" becomes
	 * "10", "00" becomes "0", and a lone "0" at the end becomes "1". Stops early
	 * rather than overflow dst.
	 *
	 * @param src  the input bits
	 * @param size the number of input bits
	 * @param dst  the output, zeroed here
	 * @return the number of output bits
	 */
	public static int decompressOneBits(byte src[], int size, byte dst[])
	{
		return transformBits(DECOMPRESS_ONE, src, size, dst, null);
	}

	/**
	 * The change in length one pass of a transform would make, without making
	 * it. For the zero-bit transform each lone one costs a bit and each "00"
	 * saves one; for the one-bit transform each lone zero costs a bit and each
	 * "11" saves one.
	 *
	 * @param string         the bits
	 * @param bit_length     the number of bits
	 * @param transform_type 0 for zero-bit, 1 for one-bit
	 * @return the change in bits: negative when the pass would shorten the string
	 */
	public static int getCompressionAmount(byte[] string, int bit_length, int transform_type)
	{
		int[][] table    = AMOUNT[(transform_type == 0) ? 0 : 1];
		int     type     = (transform_type == 0) ? 0 : 1;
		int     previous = (type == 0) ? 1 : 0;
		int     amount   = 0;
		int     byte_length = bit_length / 8;
		for (int i = 0; i < byte_length; i++)
		{
			int step = table[previous][string[i] & 0xFF];
			amount  += (step & 0xFF) - 128;
			previous = step >>> 8;
		}
		for (int i = byte_length * 8; i < bit_length; i++)
		{
			int step = amountRule(type, previous, (string[byte_length] >> (i & 7)) & 1);
			amount  += (step & 0xFF) - 128;
			previous = step >>> 8;
		}
		return amount;
	}

	/**
	 * Compresses a string with repeated bit-pair transforms. It picks the
	 * transform (zero-bit or one-bit) that one pass would shorten more, then
	 * repeats it while each pass still saves bits, up to 15 passes, recording the
	 * transform and the number of passes in the trailing data byte.
	 *
	 * @param src a string, with its trailing data byte
	 * @return the compressed string, or a copy of src if no pass saves bits or
	 *         the total saving is under {@link #compress_threshold} of the
	 *         original length
	 * @see #decompressStrings
	 */
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

	/**
	 * Undoes {@link #compressStrings}.
	 *
	 * @param string a string, compressed or not
	 * @return the uncompressed string (with no passes recorded), or string
	 *         itself if it was not compressed
	 */
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
}
