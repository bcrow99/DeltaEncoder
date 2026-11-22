import java.io.*;
import java.util.*;

// NeighbourMapper: the neighbour Huffman coding of a channel's deltas
// (HuffmanWriter2's; GenericWriter's and NativeEncoder's NativeWriter's
// "Neighbour Huffman"). Each delta's code table is chosen by how busy its
// neighbourhood is: the size of the deltas already coded to its left and
// above (activity, a weighted sum of six of them), in one of CONTEXTS - 1
// steps. The decoder knows the same neighbours, so it makes the same choice:
// the coding says nothing about which table is used where. For the quiet
// parts, where most deltas are 0 and a Huffman code's one bit a delta at the
// least would be too much: a run mode (where the neighbourhood is all zeros,
// the number of zero deltas that follow, to the row's end at most, coded as
// one symbol), and pairs (in contexts 1 to PAIR_CONTEXTS, two deltas as one
// symbol). Each channel has its own tables, made from its own deltas; their
// code lengths are coded compactly (writeLengths), with no Deflate, so the
// coding doesn't depend on a zlib. See neighbourCode for the layout.
/**
 * The neighbour Huffman coding of a channel's deltas (see the comment above).
 */
public class NeighbourMapper
{
	/** Not made: static methods only. */
	private NeighbourMapper() { }


	/**
	 * The number of contexts: 0 (run mode: the neighbourhood all zeros) and 1
	 * to CONTEXTS - 1 by the neighbourhood's activity (context).
	 */
	static final int   CONTEXTS = 16;
	/**
	 * Where contexts 2 to CONTEXTS - 1 start: context c (above 0) is 1 plus
	 * the number of these the activity reaches (about half a power of 2
	 * apart).
	 */
	static final int[] ACTIVITY_STEPS = {2, 3, 4, 6, 8, 12, 16, 23, 32, 46, 64, 91, 128, 181};
	/**
	 * Contexts 1 to PAIR_CONTEXTS code two deltas at a time (a pair symbol).
	 */
	static final int   PAIR_CONTEXTS = 6;
	/**
	 * A pair symbol's deltas: -PAIR_RANGE to PAIR_RANGE, or PAIR_ESCAPE for
	 * anything else (then coded from the escape table).
	 */
	static final int   PAIR_RANGE = 3;
	/**
	 * The pair symbols' escape: the delta is coded after, from the escape table.
	 */
	static final int   PAIR_ESCAPE = 2 * PAIR_RANGE + 1;
	/**
	 * The values a pair symbol's delta takes (2 PAIR_RANGE + 1, and the escape).
	 */
	static final int   PAIR_WIDTH = PAIR_ESCAPE + 1;
	/**
	 * Run lengths' size classes: 0 for none, else the length's bit count (a
	 * run is at most a row, under 65,536).
	 */
	static final int   RUN_SYMBOLS = 18;
	/**
	 * The tables, one set per channel of each band: the run lengths' size
	 * classes.
	 */
	static final int   RUN_TABLE = 0;
	/**
	 * The tables: single deltas in context c are table c (1 to CONTEXTS - 1);
	 * pairs in context c are table PAIR_TABLE + c (1 to PAIR_CONTEXTS).
	 */
	static final int   PAIR_TABLE = CONTEXTS - 1;
	/**
	 * The tables: the delta that stops a run.
	 */
	static final int   STOP_TABLE = PAIR_TABLE + PAIR_CONTEXTS + 1;
	/**
	 * The tables: the pairs' escaped deltas.
	 */
	static final int   ESCAPE_TABLE = STOP_TABLE + 1;
	/**
	 * How many tables.
	 */
	static final int   TABLES = ESCAPE_TABLE + 1;
	/**
	 * The longest code a table may have.
	 */
	static final int   MAX_CODE_LENGTH = 20;
	/**
	 * The code lengths' coding (writeLengths): lengths 0 to MAX_CODE_LENGTH
	 * as they are, then three more symbols: the last length again, 3 to 6
	 * times (2 bits more); 3 to 10 zeros (3 bits); 11 to 138 zeros (7 bits).
	 */
	static final int   LENGTH_REPEAT = MAX_CODE_LENGTH + 1, LENGTH_ZEROS = LENGTH_REPEAT + 1, LENGTH_MANY_ZEROS = LENGTH_ZEROS + 1, LENGTH_SYMBOLS = LENGTH_MANY_ZEROS + 1;
	/**
	 * The longest code in the code lengths' own code (its lengths are
	 * written in 4 bits each).
	 */
	static final int   MAX_LENGTH_CODE_LENGTH = 15;
	/**
	 * How many bits the decoder's lookup tables take at once; longer codes
	 * are found from the canonical code (Table.decode).
	 */
	static final int   LOOKUP_BITS = 10;

	/**
	 * A delta's neighbourhood activity: how big the deltas already coded near
	 * it are. Inside the image, 2 |left| + 2 |above| + |above left| + |above
	 * right| + |two left| + |two above| (on the second row, above again for two
	 * above); on the first row 8 |left|, in the first column 8 |above|, in
	 * the second and last columns 4 (|left| + |above|). 0 only if they're all
	 * 0 (run mode).
	 *
	 * @param d    the deltas (d[0] is 0)
	 * @param k    the delta's index (at least 1)
	 * @param x    its column
	 * @param y    its row
	 * @param xdim the width
	 * @return the activity
	 */
	static int activity(int[] d, int k, int x, int y, int xdim)
	{
		if(y == 0) return 8 * Math.abs(d[k - 1]);
		if(x == 0) return 8 * Math.abs(d[k - xdim]);
		if(x == 1 || x == xdim - 1) return 4 * (Math.abs(d[k - 1]) + Math.abs(d[k - xdim]));
		int two_above = (y >= 2) ? d[k - 2 * xdim] : d[k - xdim];
		return 2 * Math.abs(d[k - 1]) + 2 * Math.abs(d[k - xdim]) + Math.abs(d[k - xdim - 1]) + Math.abs(d[k - xdim + 1])
		       + Math.abs(d[k - 2]) + Math.abs(two_above);
	}

	/**
	 * The context of an activity: 0 for 0, else 1 plus the number of
	 * ACTIVITY_STEPS it reaches.
	 *
	 * @param a the activity
	 * @return the context, 0 to CONTEXTS - 1
	 */
	static int context(int a)
	{
		if(a == 0) return 0;
		int c = 1;
		while(c - 1 < ACTIVITY_STEPS.length && a >= ACTIVITY_STEPS[c - 1]) c++;
		return c;
	}

	/**
	 * A delta's value in a pair symbol: delta + PAIR_RANGE, or PAIR_ESCAPE.
	 *
	 * @param delta the delta
	 * @return its value
	 */
	private static int pairValue(int delta)
	{
		return (delta >= -PAIR_RANGE && delta <= PAIR_RANGE) ? delta + PAIR_RANGE : PAIR_ESCAPE;
	}

	/**
	 * A channel's deltas coded with neighbour-chosen Huffman tables (see the
	 * comment at the top). The deltas are walked in order, and at each one,
	 * from its neighbourhood's activity: in run mode (activity 0), the run of
	 * zero deltas from it to the end of the row at most (its length's size
	 * class from the run table, then the length's bits below its top bit as
	 * they are), then, if the run stopped before the row's end, the delta
	 * that stopped it (stop table); in contexts 1 to PAIR_CONTEXTS, if the
	 * next delta is in the same row, the two as one pair symbol (pair table),
	 * then any escaped ones (escape table); otherwise the delta (its
	 * context's table). The single, stop and escape tables code delta - lo,
	 * lo the smallest delta. Layout: int lo, int the number of delta values
	 * (hi - lo + 1), int the number of bytes that follow, and those bytes: a
	 * bit stream (most significant bit first) of the tables' code lengths
	 * (writeLengths), then the codes. Delta 0 isn't coded (the band's first
	 * value is written instead) and is taken as 0.
	 *
	 * @param delta the deltas (not changed)
	 * @param xdim  the width
	 * @return the coded bytes
	 * @throws IOException never in practice (a byte stream)
	 */
	static byte[] neighbourCode(int[] delta, int xdim) throws IOException
	{
		int n = delta.length;
		int[] d = delta.clone();
		d[0] = 0;
		int lo = 0, hi = 0;
		if(n > 1)
		{
			lo = hi = d[1];
			for(int k = 2; k < n; k++) { if(d[k] < lo) lo = d[k]; else if(d[k] > hi) hi = d[k]; }
		}
		int values = hi - lo + 1;

		// The symbols counted, the tables made, then the symbols coded.
		int[][] frequency = new int[TABLES][];
		for(int t = 0; t < TABLES; t++) frequency[t] = new int[tableSize(t, values)];
		walk(d, xdim, lo, frequency, null);
		Table[] table = new Table[TABLES];
		int[][] length = new int[TABLES][];
		for(int t = 0; t < TABLES; t++) { length[t] = codeLengths(frequency[t], MAX_CODE_LENGTH); table[t] = new Table(length[t]); }
		BitWriter bits = new BitWriter(n / 2 + 1024);
		writeLengths(bits, length);
		walk(d, xdim, lo, table, bits);
		byte[] code = bits.toByteArray();

		ByteArrayOutputStream bytes = new ByteArrayOutputStream(code.length + 12);
		DataOutputStream out = new DataOutputStream(bytes);
		out.writeInt(lo);
		out.writeInt(values);
		out.writeInt(code.length);
		out.write(code);
		return bytes.toByteArray();
	}

	/**
	 * A table's number of symbols.
	 *
	 * @param t      the table
	 * @param values the number of delta values
	 * @return its size
	 */
	static int tableSize(int t, int values)
	{
		if(t == RUN_TABLE) return RUN_SYMBOLS;
		if(t > PAIR_TABLE && t <= PAIR_TABLE + PAIR_CONTEXTS) return PAIR_WIDTH * PAIR_WIDTH;
		return values;
	}

	/**
	 * neighbourCode's walk through the deltas: counts the symbols into
	 * frequency (if bits is null) or codes them with the tables into bits.
	 *
	 * @param d         the deltas (d[0] is 0)
	 * @param xdim      the width
	 * @param lo        the smallest delta
	 * @param frequency the counts, a table's symbols each (counting), or null
	 * @param bits      the bit stream (coding), or null
	 */
	private static void walk(int[] d, int xdim, int lo, int[][] frequency, BitWriter bits)
	{
		walk(d, xdim, lo, frequency, null, bits);
	}

	/**
	 * neighbourCode's walk through the deltas, coding with the tables.
	 *
	 * @param d     the deltas (d[0] is 0)
	 * @param xdim  the width
	 * @param lo    the smallest delta
	 * @param table the tables
	 * @param bits  the bit stream
	 */
	private static void walk(int[] d, int xdim, int lo, Table[] table, BitWriter bits)
	{
		walk(d, xdim, lo, null, table, bits);
	}

	/**
	 * neighbourCode's walk through the deltas: each symbol counted into
	 * frequency (if it's not null), or coded with table into bits.
	 *
	 * @param d         the deltas (d[0] is 0)
	 * @param xdim      the width
	 * @param lo        the smallest delta
	 * @param frequency the counts, or null
	 * @param table     the tables, or null
	 * @param bits      the bit stream, or null
	 */
	private static void walk(int[] d, int xdim, int lo, int[][] frequency, Table[] table, BitWriter bits)
	{
		boolean counting = frequency != null;
		int n = d.length;
		for(int k = 1; k < n; )
		{
			int y = k / xdim, x = k - y * xdim, end = (y + 1) * xdim;
			int a = activity(d, k, x, y, xdim);
			if(a == 0)
			{
				// Run mode: the zeros that follow, to the row's end at most,
				// then the delta that stopped them.
				int r = 0;
				while(k + r < end && d[k + r] == 0) r++;
				int size = 32 - Integer.numberOfLeadingZeros(r);
				if(counting) frequency[RUN_TABLE][size]++;
				else
				{
					table[RUN_TABLE].write(bits, size);
					if(size > 1) bits.write(r & ((1 << (size - 1)) - 1), size - 1);
				}
				k += r;
				if(k < end)
				{
					if(counting) frequency[STOP_TABLE][d[k] - lo]++;
					else         table[STOP_TABLE].write(bits, d[k] - lo);
					k++;
				}
				continue;
			}
			int c = context(a);
			if(c <= PAIR_CONTEXTS && k + 1 < end)
			{
				// Two deltas as one symbol, then any escaped one.
				int u = pairValue(d[k]), v = pairValue(d[k + 1]);
				int t = PAIR_TABLE + c, s = u * PAIR_WIDTH + v;
				if(counting) frequency[t][s]++;
				else         table[t].write(bits, s);
				for(int j = 0; j < 2; j++)
				{
					if((j == 0 ? u : v) != PAIR_ESCAPE) continue;
					if(counting) frequency[ESCAPE_TABLE][d[k + j] - lo]++;
					else         table[ESCAPE_TABLE].write(bits, d[k + j] - lo);
				}
				k += 2;
				continue;
			}
			if(counting) frequency[c][d[k] - lo]++;
			else         table[c].write(bits, d[k] - lo);
			k++;
		}
	}

	/**
	 * A channel's coding as neighbourCode wrote it, read by neighbourRead.
	 */
	static final class NeighbourChannel
	{
		/** The smallest delta. */
		int    lo;
		/** The number of delta values. */
		int    values;
		/** The bit stream. */
		byte[] code;

		/** An empty one, for neighbourRead to fill in. */
		NeighbourChannel() { }
	}

	/**
	 * Reads a channel's coding neighbourCode wrote.
	 *
	 * @param in the stream, at the start of it
	 * @return the coding
	 * @throws IOException if the stream fails or isn't what neighbourCode writes
	 */
	static NeighbourChannel neighbourRead(DataInputStream in) throws IOException
	{
		NeighbourChannel h = new NeighbourChannel();
		h.lo     = in.readInt();
		h.values = in.readInt();
		int length = in.readInt();
		if(h.values < 1 || length < 0) throw new IOException("bad neighbour Huffman coding");
		h.code = new byte[length];
		in.readFully(h.code);
		return h;
	}

	/**
	 * A channel's deltas from its neighbour Huffman coding: neighbourCode's
	 * walk, the other way.
	 *
	 * @param h    the coding
	 * @param xdim the width
	 * @param ydim the band's height
	 * @return the deltas (delta 0 is 0)
	 * @throws IOException if the coding isn't what neighbourCode writes
	 */
	static int[] neighbourDecode(NeighbourChannel h, int xdim, int ydim) throws IOException
	{
		int n = xdim * ydim, lo = h.lo;
		BitReader bits = new BitReader(h.code);
		int[][] length = readLengths(bits, h.values);
		Table[] table = new Table[TABLES];
		for(int t = 0; t < TABLES; t++) table[t] = new Table(length[t]);
		Table run_table = table[RUN_TABLE], stop_table = table[STOP_TABLE], escape_table = table[ESCAPE_TABLE];
		int[] d = new int[n];
		for(int k = 1; k < n; )
		{
			int y = k / xdim, x = k - y * xdim, end = (y + 1) * xdim;
			int a = activity(d, k, x, y, xdim);
			if(a == 0)
			{
				int size = run_table.read(bits);
				int r = (size <= 1) ? size : (1 << (size - 1)) | bits.read(size - 1);
				if(r > end - k) throw new IOException("bad neighbour Huffman coding: a run past the row's end");
				k += r;   // (the zeros are there already)
				if(k < end) d[k++] = stop_table.read(bits) + lo;
				continue;
			}
			int c = context(a);
			if(c <= PAIR_CONTEXTS && k + 1 < end)
			{
				int s = table[PAIR_TABLE + c].read(bits);
				int u = s / PAIR_WIDTH, v = s - u * PAIR_WIDTH;
				d[k]     = (u == PAIR_ESCAPE) ? escape_table.read(bits) + lo : u - PAIR_RANGE;
				d[k + 1] = (v == PAIR_ESCAPE) ? escape_table.read(bits) + lo : v - PAIR_RANGE;
				k += 2;
				continue;
			}
			d[k++] = table[c].read(bits) + lo;
		}
		if(bits.overrun()) throw new IOException("bad neighbour Huffman coding: it ends too soon");
		return d;
	}

	// ---- Code lengths ---------------------------------------------------------

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
	 * Writes the tables' code lengths (all TABLES of them, one after another,
	 * each of its tableSize), with writeLengthSymbols (lengths up to
	 * MAX_CODE_LENGTH).
	 *
	 * @param bits   the bit stream
	 * @param length the tables' code lengths
	 */
	static void writeLengths(BitWriter bits, int[][] length)
	{
		int total = 0;
		for(int[] l : length) total += l.length;
		int[] all = new int[total];
		for(int t = 0, at = 0; t < length.length; at += length[t].length, t++) System.arraycopy(length[t], 0, all, at, length[t].length);
		writeLengthSymbols(bits, all, MAX_CODE_LENGTH);
	}

	/**
	 * Reads the tables' code lengths writeLengths wrote.
	 *
	 * @param bits   the bit stream
	 * @param values the number of delta values
	 * @return the tables' code lengths
	 * @throws IOException if they aren't what writeLengths writes
	 */
	static int[][] readLengths(BitReader bits, int values) throws IOException
	{
		int[][] length = new int[TABLES][];
		int total = 0;
		for(int t = 0; t < TABLES; t++) { length[t] = new int[tableSize(t, values)]; total += length[t].length; }
		int[] all = readLengthSymbols(bits, total, MAX_CODE_LENGTH);
		for(int t = 0, at = 0; t < TABLES; at += length[t].length, t++) System.arraycopy(all, at, length[t], 0, length[t].length);
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
	 * the neighbour Huffman tables' (writeLengths); with
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

	// ---- Tables and bits ------------------------------------------------------

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
	}
}
