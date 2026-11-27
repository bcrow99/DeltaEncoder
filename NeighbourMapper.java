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
// symbol). A single delta is coded with its sign flipped where its
// neighbours' (left + above) is negative (version 2: flipped), so a delta
// going the way its neighbours go is always coded as positive, and a table
// can give its short codes to one side. Version 3 adds a second opinion (as
// the context aware coding's): how far the median edge detector, from the
// pixel values around, is from the rebuilding's prediction (opinion, 0 to 4
// either way). It picks the tables too (activity and opinion), flips a delta
// (pairs too) when it's negative, and breaks the run mode when it isn't 0.
// The decoder needs the values (and the prediction) as it goes, so version 3
// decodes a pixel at a time inside the rebuilding (NeighbourDecoder); 1 and 2
// decode a channel's deltas first (neighbourDecode). Each channel has its own
// tables, made from its own deltas; their code lengths are coded compactly
// (writeLengths), with no Deflate, so the coding doesn't depend on a zlib.
// See neighbourCode for the layout.
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
	 * classes. Then the single deltas' (singleTable), the pairs'
	 * (pairTable), the delta that stops a run (stopTable), the pairs'
	 * escaped deltas (escapeTable): tables(levels) in all, levels the number
	 * of opinion levels the tables are split by (1 in versions 1 and 2,
	 * OPINION_LEVELS in 3).
	 */
	static final int   RUN_TABLE = 0;
	/**
	 * Version 3's opinion levels, the size of a second opinion (opinion):
	 * 0 to OPINION_LEVELS - 1.
	 */
	static final int   OPINION_LEVELS = 5;
	/**
	 * The longest code a table may have.
	 */
	static final int   MAX_CODE_LENGTH = LengthCoding.MAX_CODE_LENGTH;
	/**
	 * The coding's versions, which are those of the files that hold it
	 * (HuffmanWriter2's and GenericWriter's Neighbour Huffman formats): 3,
	 * with the second opinion (opinion), which neighbourCode writes and
	 * NeighbourDecoder reads; 2, each single delta's sign flipped where its
	 * neighbours' is negative (flipped), and 1, the first, without, which
	 * neighbourDecode still reads.
	 */
	static final int   VERSION = 3, VERSION_FLIPPED = 2, VERSION_UNFLIPPED = 1;

	/**
	 * Single deltas in context c (1 to CONTEXTS - 1) at opinion level l.
	 *
	 * @param c the context
	 * @param l the opinion level (0 to levels - 1)
	 * @return the table
	 */
	static int singleTable(int c, int l) { return 1 + l * (CONTEXTS - 1) + c - 1; }

	/**
	 * Pairs in context c (1 to PAIR_CONTEXTS) at opinion level l.
	 *
	 * @param c      the context
	 * @param l      the opinion level (0 to levels - 1)
	 * @param levels the number of opinion levels (1, or OPINION_LEVELS)
	 * @return the table
	 */
	static int pairTable(int c, int l, int levels) { return 1 + levels * (CONTEXTS - 1) + l * PAIR_CONTEXTS + c - 1; }

	/**
	 * The table of the delta that stops a run.
	 *
	 * @param levels the number of opinion levels
	 * @return the table
	 */
	static int stopTable(int levels) { return 1 + levels * (CONTEXTS - 1 + PAIR_CONTEXTS); }

	/**
	 * The table of the pairs' escaped deltas.
	 *
	 * @param levels the number of opinion levels
	 * @return the table
	 */
	static int escapeTable(int levels) { return stopTable(levels) + 1; }

	/**
	 * How many tables.
	 *
	 * @param levels the number of opinion levels
	 * @return the number of tables
	 */
	static int tables(int levels) { return stopTable(levels) + 2; }

	/**
	 * The number of opinion levels a version's tables are split by.
	 *
	 * @param version the coding's version
	 * @return 1, or OPINION_LEVELS for version 3
	 */
	static int levels(int version) { return (version >= 3) ? OPINION_LEVELS : 1; }

	/**
	 * A pixel's second opinion (version 3): how far the median edge detector
	 * (DeltaMapper.med, from left, above and above-left) is from the
	 * rebuilding's prediction p, as a JPEG-LS gradient level
	 * (DeltaMapper.gradientLevel: 0; 1 to 2, 3 to 6, 7 to 20, beyond: +-1 to
	 * +-4). Only inside the image (rows 1 on, columns 1 to xdim - 2: where
	 * the rebuilding has a prediction of its own); 0 elsewhere.
	 *
	 * @param v    the values (filled below the pixel)
	 * @param at   the pixel's index in v
	 * @param x    its column
	 * @param y    its row
	 * @param xdim the width
	 * @param p    the prediction at it
	 * @return the opinion, -4 to 4
	 */
	static int opinion(int[] v, int at, int x, int y, int xdim, int p)
	{
		if(y < 1 || x < 1 || x > xdim - 2) return 0;
		return DeltaMapper.gradientLevel(DeltaMapper.med(v[at - 1], v[at - xdim], v[at - xdim - 1]) - p);
	}

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
	 * Whether a single delta's sign is flipped in the coding (version 2): if
	 * its neighbours', left + above (0 for one off the image), is negative.
	 * About as many deltas then go each way, but those going the way their
	 * neighbours go (most of them, with the average deltas) are all coded as
	 * positive: about 1 to 3% smaller with the average deltas, a few tenths
	 * of a percent with LS (its fit has taken most of that out).
	 *
	 * @param d    the deltas (d[0] is 0)
	 * @param k    the delta's index (at least 1)
	 * @param x    its column
	 * @param y    its row
	 * @param xdim the width
	 * @return whether it's flipped
	 */
	static boolean flipped(int[] d, int k, int x, int y, int xdim)
	{
		return ((x > 0) ? d[k - 1] : 0) + ((y > 0) ? d[k - xdim] : 0) < 0;
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
	 * then any escaped ones (escape table); otherwise the delta, its sign
	 * flipped if its neighbours' is negative (flipped), from its context's
	 * table. Version 3 (this one) also takes each pixel's second opinion
	 * (opinion, from the values, the prediction value - delta): run mode
	 * only if it's 0 as well (else context 1); a pair or single delta from
	 * the table of its context and the opinion's size (pairTable,
	 * singleTable), a pair flipped (both deltas, escapes too) if the opinion
	 * is negative, a single delta if it's negative, or, if it's 0, if its
	 * neighbours' is. The single, stop and escape tables code delta - lo, lo the
	 * smallest delta either way round (lo = -max(|smallest|, |largest|), so
	 * that a flipped delta is in range too). Layout: int lo, int the number of delta values
	 * (-2 lo + 1), int the number of bytes that follow, and those bytes: a
	 * bit stream (most significant bit first) of the tables' code lengths
	 * (writeLengths), then the codes. Delta 0 isn't coded (the band's first
	 * value is written instead) and is taken as 0.
	 *
	 * @param delta  the deltas (not changed)
	 * @param value  the channel's values, as the decoder rebuilds them
	 * @param offset where the deltas' first value is in value
	 * @param xdim   the width
	 * @return the coded bytes
	 * @throws IOException never in practice (a byte stream)
	 */
	static byte[] neighbourCode(int[] delta, int[] value, int offset, int xdim) throws IOException
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
		// Either way round: a flipped delta is in range too.
		hi = Math.max(-lo, hi);
		lo = -hi;
		int values = hi - lo + 1;

		// Each pixel's second opinion, then the symbols counted, the tables
		// made, then the symbols coded.
		int[] opinion = new int[n];
		for(int k = xdim; k < n; k++)
		{
			int x = k % xdim;
			opinion[k] = opinion(value, offset + k, x, k / xdim, xdim, value[offset + k] - delta[k]);
		}
		int tables = tables(OPINION_LEVELS);
		int[][] frequency = new int[tables][];
		for(int t = 0; t < tables; t++) frequency[t] = new int[tableSize(t, values, OPINION_LEVELS)];
		walk(d, opinion, xdim, lo, frequency, null, null);
		LengthCoding.Table[] table = new LengthCoding.Table[tables];
		int[][] length = new int[tables][];
		for(int t = 0; t < tables; t++) { length[t] = LengthCoding.codeLengths(frequency[t], MAX_CODE_LENGTH); table[t] = new LengthCoding.Table(length[t]); }
		LengthCoding.BitWriter bits = new LengthCoding.BitWriter(n / 2 + 1024);
		writeLengths(bits, length);
		walk(d, opinion, xdim, lo, null, table, bits);
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
	 * @param levels the number of opinion levels (levels(version))
	 * @return its size
	 */
	static int tableSize(int t, int values, int levels)
	{
		if(t == RUN_TABLE) return RUN_SYMBOLS;
		if(t >= pairTable(1, 0, levels) && t < stopTable(levels)) return PAIR_WIDTH * PAIR_WIDTH;
		return values;
	}

	/**
	 * neighbourCode's walk through the deltas: each symbol counted into
	 * frequency (if it's not null), or coded with table into bits.
	 *
	 * @param d         the deltas (d[0] is 0)
	 * @param opinion   each pixel's second opinion
	 * @param xdim      the width
	 * @param lo        the smallest delta
	 * @param frequency the counts, or null
	 * @param table     the tables, or null
	 * @param bits      the bit stream, or null
	 */
	private static void walk(int[] d, int[] opinion, int xdim, int lo, int[][] frequency, LengthCoding.Table[] table, LengthCoding.BitWriter bits)
	{
		boolean counting = frequency != null;
		int n = d.length, stop_table = stopTable(OPINION_LEVELS), escape_table = escapeTable(OPINION_LEVELS);
		for(int k = 1; k < n; )
		{
			int y = k / xdim, x = k - y * xdim, end = (y + 1) * xdim;
			int a = activity(d, k, x, y, xdim), o = opinion[k];
			if(a == 0 && o == 0)
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
					if(counting) frequency[stop_table][d[k] - lo]++;
					else         table[stop_table].write(bits, d[k] - lo);
					k++;
				}
				continue;
			}
			int c = (a == 0) ? 1 : context(a), l = Math.abs(o);
			if(c <= PAIR_CONTEXTS && k + 1 < end)
			{
				// Two deltas as one symbol (flipped if the opinion is
				// negative), then any escaped one.
				int d0 = (o < 0) ? -d[k] : d[k], d1 = (o < 0) ? -d[k + 1] : d[k + 1];
				int u = pairValue(d0), v = pairValue(d1);
				int t = pairTable(c, l, OPINION_LEVELS), s = u * PAIR_WIDTH + v;
				if(counting) frequency[t][s]++;
				else         table[t].write(bits, s);
				for(int j = 0; j < 2; j++)
				{
					if((j == 0 ? u : v) != PAIR_ESCAPE) continue;
					int e = (j == 0) ? d0 : d1;
					if(counting) frequency[escape_table][e - lo]++;
					else         table[escape_table].write(bits, e - lo);
				}
				k += 2;
				continue;
			}
			boolean flip = (o != 0) ? o < 0 : flipped(d, k, x, y, xdim);
			int e = flip ? -d[k] : d[k], t = singleTable(c, l);
			if(counting) frequency[t][e - lo]++;
			else         table[t].write(bits, e - lo);
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
		/** The coding's version (VERSION, VERSION_FLIPPED or VERSION_UNFLIPPED). */
		int    version;

		/** An empty one, for neighbourRead to fill in. */
		NeighbourChannel() { }
	}

	/**
	 * Reads a channel's coding neighbourCode wrote (this version's).
	 *
	 * @param in the stream, at the start of it
	 * @return the coding
	 * @throws IOException if the stream fails or isn't what neighbourCode writes
	 */
	static NeighbourChannel neighbourRead(DataInputStream in) throws IOException
	{
		return neighbourRead(in, VERSION);
	}

	/**
	 * Reads a channel's coding of a version (the file's: VERSION,
	 * VERSION_FLIPPED or VERSION_UNFLIPPED).
	 *
	 * @param in      the stream, at the start of it
	 * @param version the coding's version
	 * @return the coding
	 * @throws IOException if the stream fails or isn't what neighbourCode writes
	 */
	static NeighbourChannel neighbourRead(DataInputStream in, int version) throws IOException
	{
		NeighbourChannel h = new NeighbourChannel();
		h.version = version;
		h.lo     = in.readInt();
		h.values = in.readInt();
		int length = in.readInt();
		if(h.values < 1 || length < 0) throw new IOException("bad neighbour Huffman coding");
		h.code = new byte[length];
		in.readFully(h.code);
		return h;
	}

	/**
	 * A channel's deltas from its neighbour Huffman coding of version 1 or
	 * 2 (versions 3 on decode inside the rebuilding: NeighbourDecoder):
	 * neighbourCode's walk then, the other way (the single deltas' signs
	 * flipped back in version 2).
	 *
	 * @param h    the coding
	 * @param xdim the width
	 * @param ydim the band's height
	 * @return the deltas (delta 0 is 0)
	 * @throws IOException if the coding isn't what neighbourCode wrote
	 */
	static int[] neighbourDecode(NeighbourChannel h, int xdim, int ydim) throws IOException
	{
		if(h.version >= 3) throw new IOException("neighbour Huffman version " + h.version + " decodes with the values (NeighbourDecoder)");
		int n = xdim * ydim, lo = h.lo;
		boolean flip = h.version >= VERSION_FLIPPED;
		LengthCoding.BitReader bits = new LengthCoding.BitReader(h.code);
		int[][] length = readLengths(bits, h.values, 1);
		LengthCoding.Table[] table = new LengthCoding.Table[tables(1)];
		for(int t = 0; t < table.length; t++) table[t] = new LengthCoding.Table(length[t]);
		LengthCoding.Table run_table = table[RUN_TABLE], stop_table = table[stopTable(1)], escape_table = table[escapeTable(1)];
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
				int s = table[pairTable(c, 0, 1)].read(bits);
				int u = s / PAIR_WIDTH, v = s - u * PAIR_WIDTH;
				d[k]     = (u == PAIR_ESCAPE) ? escape_table.read(bits) + lo : u - PAIR_RANGE;
				d[k + 1] = (v == PAIR_ESCAPE) ? escape_table.read(bits) + lo : v - PAIR_RANGE;
				k += 2;
				continue;
			}
			int e = table[singleTable(c, 0)].read(bits) + lo;
			d[k] = (flip && flipped(d, k, x, y, xdim)) ? -e : e;
			k++;
		}
		if(bits.overrun()) throw new IOException("bad neighbour Huffman coding: it ends too soon");
		return d;
	}

	/**
	 * Decodes a channel's neighbour Huffman coding of version 3 a pixel at
	 * a time, inside the rebuilding (DeltaMapper.DeltaDecoder: the rebuilding
	 * gives it the prediction at each pixel that has one, which the second
	 * opinion needs): neighbourCode's walk, the other way, a symbol at a time,
	 * a run's zeros, its stop and a pair's second delta given out at the
	 * pixels after.
	 */
	static final class NeighbourDecoder implements DeltaMapper.DeltaDecoder
	{
		/** The tables. */
		private final LengthCoding.Table[] table;
		/** The run, stop and escape tables. */
		private final LengthCoding.Table run_table, stop_table, escape_table;
		/** The bit stream. */
		private final LengthCoding.BitReader bits;
		/** The width, the smallest delta, and where the deltas' first value is in the values. */
		private final int xdim, lo, offset;
		/** The zeros still to come of a run. */
		private int zeros;
		/** The pixel where a run's stop comes, or -1. */
		private int stop_at = -1;
		/** The pixel where a pair's second delta comes, or -1; and it. */
		private int second_at = -1, second;

		/**
		 * A decoder at the start of a channel's coding.
		 *
		 * @param h      the coding (version 3)
		 * @param xdim   the width
		 * @param offset where the deltas' first value is in the values the
		 *               rebuilding gives (0 for a band of its own)
		 * @throws IOException if the coding's tables aren't what neighbourCode writes
		 */
		NeighbourDecoder(NeighbourChannel h, int xdim, int offset) throws IOException
		{
			if(h.version < 3) throw new IOException("neighbour Huffman version " + h.version + " decodes on its own (neighbourDecode)");
			this.xdim = xdim; this.lo = h.lo; this.offset = offset;
			bits = new LengthCoding.BitReader(h.code);
			int[][] length = readLengths(bits, h.values, OPINION_LEVELS);
			table = new LengthCoding.Table[length.length];
			for(int t = 0; t < table.length; t++) table[t] = new LengthCoding.Table(length[t]);
			run_table    = table[RUN_TABLE];
			stop_table   = table[stopTable(OPINION_LEVELS)];
			escape_table = table[escapeTable(OPINION_LEVELS)];
		}

		@Override public void next(int k, int[] d, int[] v) { decode(k, d, v, false, 0); }

		@Override public void next(int k, int[] d, int[] v, int prediction) { decode(k, d, v, true, prediction); }

		/**
		 * Decodes delta k into d[k]; a bad code's IOException is passed on
		 * unchecked (the rebuilding loops pass on what their decoders throw).
		 *
		 * @param k          the pixel (0, 1, 2, ... in turn)
		 * @param d          the deltas (filled below k)
		 * @param v          the values (filled below offset + k)
		 * @param predicted  whether there's a prediction at k
		 * @param prediction the prediction at k
		 */
		private void decode(int k, int[] d, int[] v, boolean predicted, int prediction)
		{
			try { step(k, d, v, predicted, prediction); }
			catch(IOException e) { throw new UncheckedIOException(e); }
		}

		/**
		 * decode's work.
		 *
		 * @param k          the pixel
		 * @param d          the deltas
		 * @param v          the values
		 * @param predicted  whether there's a prediction at k
		 * @param prediction the prediction at k
		 * @throws IOException if a code isn't one of its table's
		 */
		private void step(int k, int[] d, int[] v, boolean predicted, int prediction) throws IOException
		{
			if(k == 0)          { d[0] = 0; return; }
			if(k == second_at)  { d[k] = second; second_at = -1; return; }
			if(zeros > 0)       { d[k] = 0; zeros--; return; }
			if(k == stop_at)    { d[k] = stop_table.read(bits) + lo; stop_at = -1; return; }
			int y = k / xdim, x = k - y * xdim, end = (y + 1) * xdim;
			int a = activity(d, k, x, y, xdim), o = predicted ? opinion(v, offset + k, x, y, xdim, prediction) : 0;
			if(a == 0 && o == 0)
			{
				int size = run_table.read(bits);
				int r = (size <= 1) ? size : (1 << (size - 1)) | bits.read(size - 1);
				if(r > end - k) r = end - k;   // (a bad coding: caught by finish)
				if(r == 0) d[k] = stop_table.read(bits) + lo;
				else
				{
					d[k] = 0;
					zeros = r - 1;
					stop_at = (k + r < end) ? k + r : -1;
				}
				return;
			}
			int c = (a == 0) ? 1 : context(a), l = Math.abs(o);
			if(c <= PAIR_CONTEXTS && k + 1 < end)
			{
				int s = table[pairTable(c, l, OPINION_LEVELS)].read(bits);
				int u = s / PAIR_WIDTH, w = s - u * PAIR_WIDTH;
				int d0 = (u == PAIR_ESCAPE) ? escape_table.read(bits) + lo : u - PAIR_RANGE;
				int d1 = (w == PAIR_ESCAPE) ? escape_table.read(bits) + lo : w - PAIR_RANGE;
				d[k]      = (o < 0) ? -d0 : d0;
				second    = (o < 0) ? -d1 : d1;
				second_at = k + 1;
				return;
			}
			int e = table[singleTable(c, l)].read(bits) + lo;
			boolean flip = (o != 0) ? o < 0 : flipped(d, k, x, y, xdim);
			d[k] = flip ? -e : e;
		}

		@Override public void finish() throws IOException
		{
			if(bits.overrun()) throw new IOException("bad neighbour Huffman coding: it ends too soon");
		}
	}

	/**
	 * A band's channel decoded (version 3) and rebuilt from its average
	 * deltas (as DeltaMapper.getValuesFromAverageDeltas: each pixel from
	 * (left + above) / 2, left or above on the edges), into dst from offset
	 * on, a pixel at a time.
	 *
	 * @param h      the coding
	 * @param xdim   the width
	 * @param ydim   the band's height
	 * @param init   its first value
	 * @param dst    gets the values
	 * @param offset where they start in dst
	 * @throws IOException if the coding isn't what neighbourCode writes
	 */
	static void neighbourAverage(NeighbourChannel h, int xdim, int ydim, int init, int[] dst, int offset) throws IOException
	{
		NeighbourDecoder dec = new NeighbourDecoder(h, xdim, offset);
		int[] d = new int[xdim * ydim];
		for(int y = 0, k = 0; y < ydim; y++)
			for(int x = 0; x < xdim; x++, k++)
			{
				int p;
				if(k == 0)      p = init;
				else if(y == 0) p = dst[offset + k - 1];
				else if(x == 0) p = dst[offset + k - xdim];
				else            p = (dst[offset + k - 1] + dst[offset + k - xdim]) / 2;
				dec.step(k, d, dst, true, p);
				dst[offset + k] = (k == 0) ? init : p + d[k];
			}
		dec.finish();
	}

	/**
	 * A band's channel's deltas decoded (version 3) with its values and
	 * predictions known (the writers' check: the coding decodes back to the
	 * deltas, given what the decoder would have).
	 *
	 * @param h      the coding
	 * @param value  the channel's values
	 * @param offset where the band's first value is in value
	 * @param delta  the deltas it was coded from (for the predictions, value - delta)
	 * @param xdim   the width
	 * @return the deltas decoded (delta 0 is 0)
	 * @throws IOException if the coding isn't what neighbourCode writes
	 */
	static int[] neighbourDecodeKnown(NeighbourChannel h, int[] value, int offset, int[] delta, int xdim) throws IOException
	{
		NeighbourDecoder dec = new NeighbourDecoder(h, xdim, offset);
		int[] d = new int[delta.length];
		for(int k = 0; k < d.length; k++) dec.step(k, d, value, true, value[offset + k] - delta[k]);
		dec.finish();
		return d;
	}

	// ---- Code lengths (written with LengthCoding) ------------------------------

	/**
	 * Writes the tables' code lengths (all of them, one after another, each
	 * of its tableSize), with writeLengthSymbols (lengths up to
	 * MAX_CODE_LENGTH).
	 *
	 * @param bits   the bit stream
	 * @param length the tables' code lengths
	 */
	static void writeLengths(LengthCoding.BitWriter bits, int[][] length)
	{
		int total = 0;
		for(int[] l : length) total += l.length;
		int[] all = new int[total];
		for(int t = 0, at = 0; t < length.length; at += length[t].length, t++) System.arraycopy(length[t], 0, all, at, length[t].length);
		LengthCoding.writeLengthSymbols(bits, all, MAX_CODE_LENGTH);
	}

	/**
	 * Reads the tables' code lengths writeLengths wrote.
	 *
	 * @param bits   the bit stream
	 * @param values the number of delta values
	 * @param levels the number of opinion levels (levels(version))
	 * @return the tables' code lengths
	 * @throws IOException if they aren't what writeLengths writes
	 */
	static int[][] readLengths(LengthCoding.BitReader bits, int values, int levels) throws IOException
	{
		int tables = tables(levels);
		int[][] length = new int[tables][];
		int total = 0;
		for(int t = 0; t < tables; t++) { length[t] = new int[tableSize(t, values, levels)]; total += length[t].length; }
		int[] all = LengthCoding.readLengthSymbols(bits, total, MAX_CODE_LENGTH);
		for(int t = 0, at = 0; t < tables; at += length[t].length, t++) System.arraycopy(all, at, length[t], 0, length[t].length);
		return length;
	}
}
