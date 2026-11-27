import java.io.*;
import java.util.*;

// LengthCoding: the table machinery the table codings share. Neighbour
// Huffman (NeighbourMapper), Context tables (ContextTables) and Huffman
// (HuffmanWriter's code lengths) all write a model as code lengths, or, for
// Context tables, levels, which are code lengths in steps of a bit or half a
// bit: -log2 of a probability, rounded. They're written compactly the same
// way (writeLengthSymbols: a length, a repeat of the last, or a run of zeros,
// each Huffman coded, with no Deflate), with the same bit streams (BitWriter,
// BitReader, most significant bit first), the same canonical Huffman code
// (Table: codes in order of length, then of symbol, decoded LOOKUP_BITS at a
// time), the same length-limited code lengths (codeLengths), and Elias's
// gamma code (writeGamma). A canonical Huffman table is the special case of
// a Context tables (rANS) table whose frequencies are all powers of 2: a code
// of length L takes 2^(precision - L) of its slots. As native/LengthCoding.c
// in C, length_coding.py in Python.
/**
 * The table machinery the table codings share (see the comment above).
 */
public class LengthCoding
{
	/** Not made: static methods only. */
	private LengthCoding() { }

	/**
	 * The longest code a Table reads (the neighbour Huffman tables' longest:
	 * NeighbourMapper.MAX_CODE_LENGTH).
	 */
	static final int MAX_CODE_LENGTH = 20;
	/**
	 * The longest code in the code lengths' own code (writeLengthSymbols:
	 * its lengths are written in 4 bits each).
	 */
	static final int MAX_LENGTH_CODE_LENGTH = 15;
	/**
	 * How many bits a Table's decoder looks up at once; longer codes are
	 * found from the canonical code (Table.read).
	 */
	static final int LOOKUP_BITS = 10;

	// ---- Code lengths -------------------------------------------------------------

	/**
	 * Huffman code lengths for these counts, none longer than limit: 0 for the
	 * symbols not used, 1 for the only one if there's one. The code is made
	 * the usual way (the two least frequent merged, again and again; ties go
	 * to the symbols first, then the merged ones in the order made, symbols
	 * in order of count, then value), then, if any code is too long, the
	 * lengths are brought within limit as JPEG does it (its Annex K.3) and
	 * given out again, shortest first, in the order of the first lengths
	 * (then of value).
	 *
	 * @param frequency the counts
	 * @param limit     the longest code allowed
	 * @return the lengths
	 */
	static int[] codeLengths(int[] frequency, int limit)
	{
		int size = frequency.length;
		int[] length = new int[size];
		int used = 0;
		for(int s = 0; s < size; s++) if(frequency[s] > 0) used++;
		if(used == 0) return length;
		if(used == 1)
		{
			for(int s = 0; s < size; s++) if(frequency[s] > 0) length[s] = 1;
			return length;
		}

		// The symbols by count, then value; then the merging, from two queues:
		// the symbols in that order, and the merged nodes in the order made.
		long[] key = new long[used];
		for(int s = 0, j = 0; s < size; s++) if(frequency[s] > 0) key[j++] = ((long) frequency[s] << 32) | s;
		Arrays.sort(key);
		int nodes = 2 * used - 1;
		long[] weight = new long[nodes];
		int[]  parent = new int[nodes];
		for(int j = 0; j < used; j++) weight[j] = key[j] >>> 32;
		int leaf = 0, merged = used;
		for(int next = used; next < nodes; next++)
		{
			int[] two = new int[2];
			for(int m = 0; m < 2; m++)
			{
				if(leaf < used && (merged >= next || weight[leaf] <= weight[merged])) two[m] = leaf++;
				else two[m] = merged++;
			}
			weight[next] = weight[two[0]] + weight[two[1]];
			parent[two[0]] = parent[two[1]] = next;
		}
		int[] depth = new int[nodes];
		int longest = 0;
		for(int v = nodes - 2; v >= 0; v--)
		{
			depth[v] = depth[parent[v]] + 1;
			if(v < used && depth[v] > longest) longest = depth[v];
		}
		for(int j = 0; j < used; j++) length[(int) (key[j] & 0xffffffffL)] = depth[j];
		if(longest <= limit) return length;

		// Too long: the number of codes of each length brought within limit
		// (JPEG's Annex K.3), then the lengths given out again.
		int[] count = new int[longest + 1];
		for(int s = 0; s < size; s++) if(length[s] > 0) count[length[s]]++;
		for(int i = longest; i > limit; i--)
			while(count[i] > 0)
			{
				int j = i - 2;
				while(count[j] == 0) j--;
				count[i] -= 2;
				count[i - 1]++;
				count[j + 1] += 2;
				count[j]--;
			}
		long[] order = new long[used];
		for(int s = 0, j = 0; s < size; s++) if(length[s] > 0) order[j++] = ((long) length[s] << 32) | s;
		Arrays.sort(order);
		for(int j = 0, l = 1; j < used; j++)
		{
			while(count[l] == 0) l++;
			length[(int) (order[j] & 0xffffffffL)] = l;
			count[l]--;
		}
		return length;
	}

	/**
	 * Code lengths (0 to max_length each) written compactly: coded with the
	 * code lengths' own symbols (max_length + 4 of them: a length, 0 to
	 * max_length; the last length again, 3 to 6 times, 2 bits more; 3 to 10
	 * zeros, 3 bits more; 11 to 138 zeros, 7 bits more), themselves Huffman
	 * coded: first that code's lengths, 4 bits each, then the symbols, each
	 * followed by its extra bits. A run of a length is written as the
	 * length, then repeats of 6 (the last fewer), with any one or two left
	 * over as lengths; zeros as runs of 138 or fewer, then 10 or fewer, then
	 * any one or two left over as zeros. With max_length MAX_CODE_LENGTH,
	 * the neighbour Huffman tables' (NeighbourMapper.writeLengths); with
	 * CodeMapper.REGULAR_MAX_CODE_LENGTH, the Huffman coding's code lengths
	 * (packLengths).
	 *
	 * @param bits       the bit stream
	 * @param all        the code lengths
	 * @param max_length the longest length there can be
	 */
	static void writeLengthSymbols(BitWriter bits, int[] all, int max_length)
	{
		int repeat = max_length + 1, zeros = repeat + 1, many_zeros = zeros + 1, symbols = many_zeros + 1;
		int total = all.length;

		// The symbols (and their extra bits), then their code.
		int[] symbol = new int[total], extra = new int[total];
		int m = 0;
		for(int i = 0; i < total; )
		{
			int v = all[i], run = 1;
			while(i + run < total && all[i + run] == v) run++;
			i += run;
			if(v == 0)
			{
				while(run >= 11) { int r = Math.min(run, 138); symbol[m] = many_zeros; extra[m++] = r - 11; run -= r; }
				if(run >= 3)     { symbol[m] = zeros; extra[m++] = run - 3; run = 0; }
				while(run > 0)   { symbol[m++] = 0; run--; }
			}
			else
			{
				symbol[m++] = v;
				run--;
				while(run >= 3) { int r = Math.min(run, 6); symbol[m] = repeat; extra[m++] = r - 3; run -= r; }
				while(run > 0)  { symbol[m++] = v; run--; }
			}
		}
		int[] frequency = new int[symbols];
		for(int j = 0; j < m; j++) frequency[symbol[j]]++;
		int[] code_length = codeLengths(frequency, MAX_LENGTH_CODE_LENGTH);
		for(int s = 0; s < symbols; s++) bits.write(code_length[s], 4);
		Table table = new Table(code_length);
		for(int j = 0; j < m; j++)
		{
			table.write(bits, symbol[j]);
			if(symbol[j] == repeat)          bits.write(extra[j], 2);
			else if(symbol[j] == zeros)      bits.write(extra[j], 3);
			else if(symbol[j] == many_zeros) bits.write(extra[j], 7);
		}
	}

	/**
	 * Reads total code lengths writeLengthSymbols wrote.
	 *
	 * @param bits       the bit stream
	 * @param total      how many
	 * @param max_length the longest length there can be (as written)
	 * @return the code lengths
	 * @throws IOException if they aren't what writeLengthSymbols writes
	 */
	static int[] readLengthSymbols(BitReader bits, int total, int max_length) throws IOException
	{
		int repeat = max_length + 1, zeros = repeat + 1, symbols = zeros + 2;
		int[] code_length = new int[symbols];
		for(int s = 0; s < symbols; s++) code_length[s] = bits.read(4);
		Table table = new Table(code_length);
		int[] all = new int[total];
		for(int i = 0, last = -1; i < total; )
		{
			int s = table.read(bits), run;
			if(s <= max_length)  { all[i++] = s; last = s; continue; }
			if(s == repeat)      { if(last < 0) throw new IOException("bad code lengths"); run = 3 + bits.read(2); s = last; }
			else if(s == zeros)  { run = 3 + bits.read(3); s = 0; last = 0; }
			else                 { run = 11 + bits.read(7); s = 0; last = 0; }
			if(i + run > total || bits.overrun()) throw new IOException("bad code lengths");
			for(int j = 0; j < run; j++) all[i++] = s;
		}
		return all;
	}

	/**
	 * A code length table written compactly, on its own (writeLengthSymbols:
	 * no Deflate, so it doesn't depend on a zlib): the Huffman coding's code
	 * lengths (HuffmanWriter.huffmanCode).
	 *
	 * @param lengths    the code lengths
	 * @param max_length the longest length there can be
	 * @return the bytes (the last filled out with 0 bits)
	 */
	public static byte[] packLengths(byte[] lengths, int max_length)
	{
		int[] all = new int[lengths.length];
		for(int s = 0; s < all.length; s++) all[s] = lengths[s];
		BitWriter bits = new BitWriter(64);
		writeLengthSymbols(bits, all, max_length);
		return bits.toByteArray();
	}

	/**
	 * Reads a code length table packLengths wrote.
	 *
	 * @param packed     the bytes
	 * @param size       how many lengths
	 * @param max_length the longest length there can be (as written)
	 * @return the code lengths
	 * @throws IOException if they aren't what packLengths writes
	 */
	public static byte[] unpackLengths(byte[] packed, int size, int max_length) throws IOException
	{
		BitReader bits = new BitReader(packed);
		int[] all = readLengthSymbols(bits, size, max_length);
		if(bits.overrun()) throw new IOException("bad code lengths: they end too soon");
		byte[] lengths = new byte[size];
		for(int s = 0; s < size; s++) lengths[s] = (byte) all[s];
		return lengths;
	}

	// ---- Tables and bits ----------------------------------------------------------

	/**
	 * A canonical Huffman code (codes given out in order of length, then of
	 * symbol), for coding and for decoding.
	 */
	static final class Table
	{
		/** Each symbol's code length (0: not used). */
		final int[] length;
		/** Each symbol's code. */
		final int[] code;
		/** The decoder's lookup: the next LOOKUP_BITS bits to (symbol &lt;&lt; 5) | length; 0 for a longer code. */
		int[] lookup;
		/** For longer codes: the first code of each length, the number of codes of each length, and where they start in sorted. */
		int[] first, count, start;
		/** The symbols in code order. */
		int[] sorted;
		/** The longest code. */
		int longest;

		/**
		 * The code for these lengths.
		 *
		 * @param length each symbol's code length
		 */
		Table(int[] length)
		{
			this.length = length;
			int size = length.length;
			code = new int[size];
			int max = 0;
			for(int l : length) max = Math.max(max, l);
			longest = max;
			count = new int[max + 2];
			for(int l : length) if(l > 0) count[l]++;
			first = new int[max + 2];
			start = new int[max + 2];
			int next = 0, at = 0;
			for(int l = 1; l <= max; l++)
			{
				next = (next + count[l - 1]) << 1;
				first[l] = next;
				start[l] = at;
				at += count[l];
			}
			sorted = new int[at];
			int[] fill = Arrays.copyOf(first, max + 2), place = Arrays.copyOf(start, max + 2);
			for(int s = 0; s < size; s++)
			{
				int l = length[s];
				if(l == 0) continue;
				code[s] = fill[l]++;
				sorted[place[l]++] = s;
			}
		}

		/**
		 * Writes a symbol's code.
		 *
		 * @param bits the bit stream
		 * @param s    the symbol
		 */
		void write(BitWriter bits, int s)
		{
			bits.write(code[s], length[s]);
		}

		/**
		 * Reads a symbol.
		 *
		 * @param bits the bit stream
		 * @return the symbol
		 * @throws IOException if the bits are no code of this table's
		 */
		int read(BitReader bits) throws IOException
		{
			if(lookup == null) makeLookup();
			int e = lookup[bits.peek(LOOKUP_BITS)];
			if(e != 0) { bits.skip(e & 31); return e >>> 5; }
			// A longer code: from the canonical code, a length at a time.
			int all = bits.peek(MAX_CODE_LENGTH);
			for(int l = LOOKUP_BITS + 1; l <= longest; l++)
			{
				int c = all >>> (MAX_CODE_LENGTH - l), i = c - first[l];
				if(i >= 0 && i < count[l]) { bits.skip(l); return sorted[start[l] + i]; }
			}
			throw new IOException("bad neighbour Huffman coding: no such code");
		}

		/**
		 * Makes the decoder's lookup table.
		 */
		private void makeLookup()
		{
			lookup = new int[1 << LOOKUP_BITS];
			for(int s = 0; s < length.length; s++)
			{
				int l = length[s];
				if(l == 0 || l > LOOKUP_BITS) continue;
				int from = code[s] << (LOOKUP_BITS - l), to = from + (1 << (LOOKUP_BITS - l));
				for(int i = from; i < to; i++) lookup[i] = (s << 5) | l;
			}
		}
	}

	/**
	 * Writes bits, most significant first, into a growing byte array.
	 */
	static final class BitWriter
	{
		/** The bytes so far. */
		private byte[] buffer;
		/** How many of them. */
		private int    size;
		/** The bits not yet written out (the low bit_count of them). */
		private long   bits;
		/** How many. */
		private int    bit_count;

		/**
		 * An empty one.
		 *
		 * @param capacity the bytes to start with
		 */
		BitWriter(int capacity) { buffer = new byte[Math.max(16, capacity)]; }

		/**
		 * Writes the low count bits of value.
		 *
		 * @param value the bits
		 * @param count how many (0 to 24)
		 */
		void write(int value, int count)
		{
			bits = (bits << count) | (value & ((1L << count) - 1));
			bit_count += count;
			while(bit_count >= 8)
			{
				bit_count -= 8;
				if(size == buffer.length) buffer = Arrays.copyOf(buffer, 2 * size);
				buffer[size++] = (byte) (bits >>> bit_count);
			}
		}

		/**
		 * The bytes, the last filled out with 0 bits.
		 *
		 * @return the bytes
		 */
		byte[] toByteArray()
		{
			if(bit_count > 0) write(0, 8 - bit_count);
			return Arrays.copyOf(buffer, size);
		}
	}

	/**
	 * Reads bits, most significant first; past the end, 0 bits.
	 */
	static final class BitReader
	{
		/** The bytes. */
		private final byte[] buffer;
		/** The next byte. */
		private int  at;
		/** Bits read in and not used (the low bit_count of them). */
		private long bits;
		/** How many. */
		private int  bit_count;
		/** How many 0 bytes were made up past the end. */
		private int  past_end;

		/**
		 * One reading these bytes.
		 *
		 * @param buffer the bytes
		 */
		BitReader(byte[] buffer) { this.buffer = buffer; }

		/**
		 * The next count bits, not used up.
		 *
		 * @param count how many (at most 24)
		 * @return them
		 */
		int peek(int count)
		{
			while(bit_count < count)
			{
				int b;
				if(at < buffer.length) b = buffer[at++] & 0xff;
				else { b = 0; past_end++; }
				bits = (bits << 8) | b;
				bit_count += 8;
			}
			return (int) (bits >>> (bit_count - count)) & ((1 << count) - 1);
		}

		/**
		 * Uses up count bits (peeked already).
		 *
		 * @param count how many
		 */
		void skip(int count) { bit_count -= count; }

		/**
		 * The next count bits.
		 *
		 * @param count how many (at most 24)
		 * @return them
		 */
		int read(int count)
		{
			if(count == 0) return 0;
			int v = peek(count);
			bit_count -= count;
			return v;
		}

		/**
		 * Whether bits past the end were used (the stream was too short).
		 *
		 * @return true if they were
		 */
		boolean overrun() { return 8 * past_end > bit_count; }
	}	/**
	 * Writes v (at least 1) in Elias's gamma code: its bit count less 1 as
	 * 0 bits, then v.
	 *
	 * @param bits the bit stream
	 * @param v    the number
	 */
	static void writeGamma(BitWriter bits, int v)
	{
		int n = 32 - Integer.numberOfLeadingZeros(v);
		if(n > 1) bits.write(0, n - 1);
		bits.write(v, n);
	}

	/**
	 * Reads a number writeGamma wrote.
	 *
	 * @param bits the bit stream
	 * @return the number
	 * @throws IOException if it's too long to be one
	 */
	static int readGamma(BitReader bits) throws IOException
	{
		int n = 1;
		while(bits.read(1) == 0) if(++n > 24) throw new IOException("bad coding: a gamma code too long");
		return (n == 1) ? 1 : (1 << (n - 1)) | bits.read(n - 1);
	}
}
