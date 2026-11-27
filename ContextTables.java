import java.io.*;
import java.util.Arrays;

// ContextTables: the Context tables coding of a channel's deltas
// (GenericWriter's and NativeEncoder's NativeWriter's "Context tables"):
// fixed tables chosen by each delta's context, in place of the context
// aware coding's adaptive models. The deltas are counted once, by context,
// the counts written as tables, and the deltas coded with them by rANS
// (range asymmetric numeral systems), which decodes a symbol with a table
// lookup and a multiply, and no division. Smaller than the Huffman codings,
// a little bigger than context aware, and several times faster to decode.
//
// Version 2 (written): each delta's context is the channel's own, cheap to
// work out (ownContext): the activity bucket of its neighbours' deltas (the
// context aware coding's buckets) and its second opinion, the median edge
// detector's distance from the prediction, as a gradient level (9 levels):
// OWN_CONTEXTS of them. No other channel is used, so the channels (and the
// bands) decode independently, all at the same time. Version 1 (still
// read): the context aware coding's contexts (DeltaMapper.MixedModels: with
// the earlier channels' deltas too), one model's, chosen, with its cross
// buckets merged or not, by the coded size: a little smaller on some
// images, about three times slower to decode.
//
// The symbols are the deltas' ranks (as the context aware coding's: the
// most frequent value is 0). Each context's table is its symbols'
// probabilities to a step of a factor of 2 (steps 1) or of the square root
// of 2 (steps 2: chosen by the coded size), as levels 1 to LEVEL_RANGE
// times steps (0: a symbol that isn't there), from which coder and decoder
// make the same frequencies, out of 2^precision (normalize). See code for
// the layout.
/**
 * The Context tables coding of a channel's deltas (see the comment above).
 */
public class ContextTables
{
	/** Not made: static methods only. */
	private ContextTables() { }

	/**
	 * The coding's version, the Context tables format's ('T'): 2, the
	 * channel's own contexts; and the one before, 1, the context aware
	 * coding's (still read).
	 */
	static final int VERSION = 2, VERSION_MIXED = 1;
	/**
	 * The model byte of version 2: the channel's own contexts (version 1's
	 * are the context aware coding's models, 0 to 2).
	 */
	static final int OWN = 3;
	/**
	 * The number of the channel's own contexts: the activity buckets times
	 * the opinion levels.
	 */
	static final int OWN_CONTEXTS = DeltaMapper.CONTEXTS / DeltaMapper.CROSS_LIMIT.length * DeltaMapper.MIX_OPINION_LEVELS;
	/**
	 * Version 1's number of contexts (each model's: the context aware
	 * coding's with the second opinions).
	 */
	static final int CONTEXTS = DeltaMapper.CONTEXTS * DeltaMapper.MIX_OPINION_LEVELS;
	/**
	 * The number of contexts merged: without the cross buckets.
	 */
	static final int MERGED_CONTEXTS = CONTEXTS / DeltaMapper.CROSS_LIMIT.length;
	/**
	 * The smallest precision, in bits, the frequencies are out of; more if
	 * there are more symbols (precision), at most MAX_PRECISION.
	 */
	static final int MIN_PRECISION = 12, MAX_PRECISION = 16;
	/**
	 * A table's levels span this many powers of 2 (the least likely symbol
	 * written as about 2^-LEVEL_RANGE of the likeliest).
	 */
	static final int LEVEL_RANGE = 12;
	/**
	 * The weight of each level (0 to LEVEL_RANGE steps), for steps 1 and 2:
	 * 16 times 2^((level - 1) / steps), rounded (0 for level 0).
	 */
	static final int[][] WEIGHT =
	{
		null,
		{0, 16, 32, 64, 128, 256, 512, 1024, 2048, 4096, 8192, 16384, 32768},
		{0, 16, 23, 32, 45, 64, 91, 128, 181, 256, 362, 512, 724, 1024, 1448, 2048, 2896, 4096, 5793, 8192, 11585, 16384, 23170, 32768, 46341}
	};
	/**
	 * rANS's state is kept from RANS_LOW up to 256 times it (32 bits),
	 * renormalized a byte at a time.
	 */
	static final long RANS_LOW = 1L << 23;

	/**
	 * A delta's own context (version 2): the activity bucket
	 * (DeltaMapper.MIX_ACTIVITY_TABLE) of its neighbours' deltas (left, above,
	 * above left and above right, those there are), times the opinion
	 * levels, plus its second opinion inside the image (row 1 on, but the
	 * first and last columns): the gradient level of the median edge
	 * detector's prediction less the rebuilding's (DeltaMapper.med,
	 * gradientLevel); elsewhere level 0. The middle level is 0.
	 *
	 * @param d          the deltas (read below k only)
	 * @param v          the values (read below k only)
	 * @param k          the pixel
	 * @param x          its column
	 * @param y          its row
	 * @param xdim       the width
	 * @param prediction the rebuilding's prediction at k (used inside the image only)
	 * @return the context
	 */
	static int ownContext(int[] d, int[] v, int k, int x, int y, int xdim, int prediction)
	{
		if(y >= 1 && x >= 1 && x < xdim - 1)
			return interiorContext(Math.abs(d[k - 1]) + Math.abs(d[k - xdim]) + Math.abs(d[k - xdim - 1]) + Math.abs(d[k - xdim + 1]),
			                       v[k - 1], v[k - xdim], v[k - xdim - 1], prediction);
		int a = (x > 0) ? Math.abs(d[k - 1]) : 0;
		if(y > 0)
		{
			a += Math.abs(d[k - xdim]);
			if(x > 0) a += Math.abs(d[k - xdim - 1]);
			if(x < xdim - 1) a += Math.abs(d[k - xdim + 1]);
		}
		return DeltaMapper.MIX_ACTIVITY_TABLE[Math.min(a, DeltaMapper.MIX_ACTIVITY_TABLE.length - 1)] * DeltaMapper.MIX_OPINION_LEVELS
		       + DeltaMapper.MIX_OPINION_LEVELS / 2;
	}

	/**
	 * ownContext inside the image, from its activity and the values left,
	 * above and above left.
	 *
	 * @param activity   the sum of the neighbours' deltas' sizes
	 * @param w          the value to the left
	 * @param n          the value above
	 * @param nw         the value above left
	 * @param prediction the rebuilding's prediction
	 * @return the context
	 */
	static int interiorContext(int activity, int w, int n, int nw, int prediction)
	{
		return DeltaMapper.MIX_ACTIVITY_TABLE[Math.min(activity, DeltaMapper.MIX_ACTIVITY_TABLE.length - 1)] * DeltaMapper.MIX_OPINION_LEVELS
		       + DeltaMapper.MIX_OPINION_LEVELS / 2 + DeltaMapper.gradientLevel(DeltaMapper.med(w, n, nw) - prediction);
	}

	/**
	 * A context, merged (version 1): its cross bucket left out (a context is (activity
	 * bucket * cross buckets + cross bucket) * opinion levels + opinion level).
	 *
	 * @param c the context
	 * @return the merged context
	 */
	static int merged(int c)
	{
		int levels = DeltaMapper.MIX_OPINION_LEVELS;
		return c / (DeltaMapper.CROSS_LIMIT.length * levels) * levels + c % levels;
	}

	/**
	 * The precision, in bits, for a number of symbols: MIN_PRECISION, or
	 * more, so that the frequencies are out of at least twice as many (each
	 * symbol there has a frequency of at least 1).
	 *
	 * @param symbols the number of symbols
	 * @return the precision
	 */
	static int precision(int symbols)
	{
		int bits = MIN_PRECISION;
		while(bits < MAX_PRECISION && (1 << bits) < 2 * symbols) bits++;
		return bits;
	}

	/**
	 * A context's frequencies from its levels: each symbol's level's weight,
	 * scaled to sum to 2^precision, each at least 1 (a symbol at level 0
	 * gets 0); what's over or under goes to or from the likeliest (the first
	 * with the largest frequency), or, if that would take it under 1, a unit
	 * at a time from the symbols in order.
	 *
	 * @param level     the context's symbols' levels
	 * @param steps     the steps a power of 2 (1 or 2)
	 * @param precision the precision
	 * @return the frequencies
	 */
	static int[] normalize(int[] level, int steps, int precision)
	{
		int[] w = WEIGHT[steps], f = new int[level.length];
		long sum = 0;
		for(int e : level) sum += w[e];
		long total = 1L << precision, given = 0;
		int big = 0;
		for(int s = 0; s < level.length; s++)
		{
			if(level[s] == 0) continue;
			f[s] = (int) Math.max(1, w[level[s]] * total / sum);
			given += f[s];
			if(f[s] > f[big]) big = s;
		}
		long left = total - given;
		if(f[big] + left >= 1) f[big] += (int) left;
		else
			while(left < 0)
				for(int s = 0; s < f.length && left < 0; s++)
					if(f[s] > 1) { f[s]--; left++; }
		return f;
	}

	/**
	 * A context's levels from its counts (up to its last symbol there): the
	 * likeliest's the top, LEVEL_RANGE times steps; each other's the highest
	 * whose weight's lower midpoint (on a linear scale, between it and the
	 * level below) count / most reaches, as a fraction of the top's weight;
	 * at least 1.
	 *
	 * @param count the counts
	 * @param last  the last symbol counted
	 * @param steps the steps a power of 2
	 * @return the levels (last + 1 of them)
	 */
	static int[] levels(int[] count, int last, int steps)
	{
		int[] w = WEIGHT[steps], level = new int[last + 1];
		int top = LEVEL_RANGE * steps;
		long most = 0;
		for(int s = 0; s <= last; s++) most = Math.max(most, count[s]);
		for(int s = 0; s <= last; s++)
		{
			if(count[s] == 0) continue;
			int e = top;
			while(e > 1 && 2L * count[s] * w[top] < (long) (w[e - 1] + w[e]) * most) e--;
			level[s] = e;
		}
		return level;
	}

	/**
	 * The tables' levels as written: each level but 0 (written as 0) as 1
	 * plus its difference from the last level before it in its table that
	 * isn't 0 (the first's from the top), zigzagged (0, -1, 1, -2, ... as
	 * 0, 1, 2, 3, ...). The symbols are in order of their counts over the
	 * channel, so a table's levels mostly fall slowly, and the differences
	 * take fewer bits than the levels (about a fifth fewer).
	 *
	 * @param level the tables' levels, one table after another
	 * @param start where each table starts in level, and, last, the end
	 * @param top   the top level
	 * @return the levels as written
	 */
	static int[] chain(int[] level, int[] start, int top)
	{
		int[] out = new int[level.length];
		for(int t = 0; t + 1 < start.length; t++)
			for(int i = start[t], before = top; i < start[t + 1]; i++)
			{
				if(level[i] == 0) continue;
				int diff = level[i] - before;
				out[i] = 1 + ((diff >= 0) ? 2 * diff : -2 * diff - 1);
				before = level[i];
			}
		return out;
	}

	/**
	 * chain undone.
	 *
	 * @param out   the levels as written
	 * @param start where each table starts, and, last, the end
	 * @param top   the top level
	 * @return the levels
	 * @throws IOException if one isn't a level (1 to top)
	 */
	static int[] unchain(int[] out, int[] start, int top) throws IOException
	{
		int[] level = new int[out.length];
		for(int t = 0; t + 1 < start.length; t++)
			for(int i = start[t], before = top; i < start[t + 1]; i++)
			{
				if(out[i] == 0) continue;
				int z = out[i] - 1, e = before + (((z & 1) == 0) ? z / 2 : -(z + 1) / 2);
				if(e < 1 || e > top) throw new IOException("bad Context tables coding: a level of " + e);
				level[i] = before = e;
			}
		return level;
	}

	/**
	 * log2(f) in fixed point, 16 bits after the point, rounded down (for
	 * comparing the codings' sizes: exactly the same in Java, Python and C).
	 *
	 * @param f a number, at least 1
	 * @return its log2 times 2^16
	 */
	static long log2Fixed(long f)
	{
		int whole = 63 - Long.numberOfLeadingZeros(f);
		long y = (whole >= 30) ? f >>> (whole - 30) : f << (30 - whole);   // f / 2^whole, 30 bits after the point: 1 to 2
		long r = (long) whole << 16;
		for(int i = 15; i >= 0; i--)
		{
			y = (y * y) >>> 30;
			if(y >= (1L << 31)) { y >>>= 1; r |= 1L << i; }
		}
		return r;
	}

	/**
	 * The tables for one choice of contexts and steps, as code makes them.
	 */
	static final class Tables
	{
		/** Each context's frequencies (null for one not used). */
		final int[][] freq;
		/** The tables as written (see code). */
		final byte[] bytes;

		/**
		 * The tables for these counts.
		 *
		 * @param count     the counts, by context and symbol
		 * @param steps     the steps a power of 2
		 * @param precision the precision
		 */
		Tables(int[][] count, int steps, int precision)
		{
			int contexts = count.length, top = LEVEL_RANGE * steps;
			freq = new int[contexts][];
			LengthCoding.BitWriter bits = new LengthCoding.BitWriter(1024);
			int[][] level = new int[contexts][];
			int total = 0, used = 0;
			for(int c = 0; c < contexts; c++)
			{
				int last = -1;
				for(int s = 0; s < count[c].length; s++) if(count[c][s] > 0) last = s;
				LengthCoding.writeGamma(bits, last + 2);   // (1: not used)
				if(last < 0) continue;
				level[c] = levels(count[c], last, steps);
				freq[c] = normalize(level[c], steps, precision);
				total += level[c].length;
				used++;
			}
			int[] all = new int[total], start = new int[used + 1];
			for(int c = 0, at = 0, t = 0; c < contexts; c++)
			{
				if(level[c] == null) continue;
				System.arraycopy(level[c], 0, all, at, level[c].length);
				start[t++] = at;
				at += level[c].length;
			}
			start[used] = total;
			LengthCoding.writeLengthSymbols(bits, chain(all, start, top), 2 * top);
			bytes = bits.toByteArray();
		}

		/**
		 * The coded size with these tables, in bits times 2^16: the tables'
		 * bytes, and the symbols' -log2 of their probabilities, in fixed
		 * point.
		 *
		 * @param count     the counts
		 * @param precision the precision
		 * @return the size
		 */
		long size(int[][] count, int precision)
		{
			long size = (8L * bytes.length) << 16, whole = (long) precision << 16;
			for(int c = 0; c < count.length; c++)
			{
				if(freq[c] == null) continue;
				for(int s = 0; s < freq[c].length; s++)
					if(count[c][s] > 0) size += count[c][s] * (whole - log2Fixed(freq[c][s]));
			}
			return size;
		}
	}

	/**
	 * Version 1's contexts, the context aware coding's (with the second
	 * opinions, from the prediction): its models with no symbols, for their
	 * contexts alone.
	 *
	 * @param previous the deltas of the channels coded before this one
	 * @param xdim     the width
	 * @param n        the number of deltas
	 * @return the models
	 */
	static DeltaMapper.MixedModels contextModels(int[][] previous, int xdim, int n)
	{
		return new DeltaMapper.MixedModels(0, previous, xdim, n, true);
	}

	/**
	 * A channel's deltas Context tables coded, version 2 (see the comment at
	 * the top). Each delta's rank is counted in its own context; the tables
	 * are made with steps 1 and 2 and the coded size worked out
	 * (Tables.size), and the smaller used (1 if the same). The ranks are then
	 * rANS coded, the last first, so that the decoder reads them first to
	 * last. Layout: int the smallest delta, the rank table
	 * (DeltaMapper.writeTable), byte the model (OWN; version 1: the context
	 * aware coding's model, 0 to 2), byte 0 (version 1: 1 if its contexts
	 * are merged), byte the steps, byte the precision, int the tables'
	 * length, the tables, int the coded length, and the coded bytes: rANS's
	 * final state (4 bytes, the low first), then the bytes it gave out, in
	 * the order the decoder reads them. The tables: a bit stream (most
	 * significant bit first) of each context's number of levels (its last
	 * symbol + 1; 0 for a context not used) plus 1, in gamma code
	 * (LengthCoding.writeGamma), then the used contexts' levels, one table after another,
	 * as chain writes them (LengthCoding.writeLengthSymbols, up to twice
	 * the top level).
	 *
	 * @param delta  the deltas
	 * @param value  the channel's values, as the decoder rebuilds them (the
	 *               predictions are value - delta)
	 * @param offset where the deltas' first value is in value
	 * @param xdim   the width
	 * @return the coded bytes
	 * @throws IOException never in practice (a byte stream)
	 */
	static byte[] code(int[] delta, int[] value, int offset, int xdim) throws IOException
	{
		int n = delta.length;
		int[] min = new int[1], rank = DeltaMapper.rankTable(delta, min);
		int symbols = rank.length, precision = precision(symbols);

		// The contexts and the counts.
		int[] v = Arrays.copyOfRange(value, offset, offset + n);
		char[] ctx = new char[n];
		int[][] count = new int[OWN_CONTEXTS][symbols];
		for(int y = 0, k = 0; k < n; y++)
			for(int x = 0; x < xdim; x++, k++)
			{
				ctx[k] = (char) ownContext(delta, v, k, x, y, xdim, v[k] - delta[k]);
				count[ctx[k]][rank[delta[k] - min[0]]]++;
			}

		// The smaller.
		Tables best = null;
		int steps = 1;
		for(int st = 1; st <= 2; st++)
		{
			Tables t = new Tables(count, st, precision);
			if(best == null || t.size(count, precision) < best.size(count, precision)) { best = t; steps = st; }
		}

		// The coding, from the last.
		int[][] cum = cumulative(best.freq);
		byte[] buf = new byte[2 * n + 16];
		int p = buf.length;
		long x = RANS_LOW;
		for(int k = n - 1; k >= 0; k--)
		{
			int c = ctx[k], s = rank[delta[k] - min[0]];
			long f = best.freq[c][s];
			long x_max = ((RANS_LOW >> precision) << 8) * f;
			while(x >= x_max) { buf[--p] = (byte) x; x >>>= 8; }
			x = ((x / f) << precision) + (x % f) + cum[c][s];
		}
		for(int b = 3; b >= 0; b--) buf[--p] = (byte) (x >>> (8 * b));

		ByteArrayOutputStream bytes = new ByteArrayOutputStream(buf.length - p + best.bytes.length + 64);
		DataOutputStream out = new DataOutputStream(bytes);
		out.writeInt(min[0]);
		DeltaMapper.writeTable(out, rank);
		out.writeByte(OWN);
		out.writeByte(0);
		out.writeByte(steps);
		out.writeByte(precision);
		out.writeInt(best.bytes.length);
		out.write(best.bytes);
		out.writeInt(buf.length - p);
		out.write(buf, p, buf.length - p);
		return bytes.toByteArray();
	}

	/**
	 * Each context's cumulative frequencies (the sum of those before each
	 * symbol).
	 *
	 * @param freq each context's frequencies (null for one not used)
	 * @return the cumulative ones (null where freq is)
	 */
	static int[][] cumulative(int[][] freq)
	{
		int[][] cum = new int[freq.length][];
		for(int c = 0; c < freq.length; c++)
		{
			if(freq[c] == null) continue;
			cum[c] = new int[freq[c].length];
			for(int s = 1; s < freq[c].length; s++) cum[c][s] = cum[c][s - 1] + freq[c][s - 1];
		}
		return cum;
	}

	/**
	 * A channel's coding as code wrote it, read by read.
	 */
	static final class TablesChannel
	{
		/** The delta value of each rank. */
		int[]   value;
		/** The model whose contexts are used: OWN (version 2), or the context aware coding's (version 1). */
		int     model;
		/** Whether its contexts are merged (version 1). */
		boolean merged;
		/** The tables' steps a power of 2. */
		int     steps;
		/** The precision. */
		int     precision;
		/** The tables. */
		byte[]  tables;
		/** The coded bytes. */
		byte[]  code;

		/** An empty one, for read to fill in. */
		TablesChannel() { }
	}

	/**
	 * Reads a channel's coding code wrote.
	 *
	 * @param in the stream, at the start of it
	 * @return the coding
	 * @throws IOException if the stream fails or isn't what code writes
	 */
	static TablesChannel read(DataInputStream in) throws IOException
	{
		TablesChannel h = new TablesChannel();
		int min = in.readInt();
		int[] rank = DeltaMapper.readTable(in);
		h.value = new int[rank.length];
		for(int v = 0; v < rank.length; v++) h.value[rank[v]] = v + min;
		h.model     = in.readUnsignedByte();
		int merge   = in.readUnsignedByte();
		h.steps     = in.readUnsignedByte();
		h.precision = in.readUnsignedByte();
		if(h.model > OWN || merge > ((h.model == OWN) ? 0 : 1) || h.steps < 1 || h.steps > 2 || h.precision < MIN_PRECISION || h.precision > MAX_PRECISION)
			throw new IOException("bad Context tables coding: model " + h.model + ", merged " + merge + ", steps " + h.steps + ", precision " + h.precision);
		h.merged = merge == 1;
		int length = in.readInt();
		if(length < 0) throw new IOException("bad Context tables coding");
		h.tables = new byte[length];
		in.readFully(h.tables);
		length = in.readInt();
		if(length < 4) throw new IOException("bad Context tables coding");
		h.code = new byte[length];
		in.readFully(h.code);
		return h;
	}

	/**
	 * Decodes a channel's Context tables coding a pixel at a time, inside the
	 * rebuilding (DeltaMapper.DeltaDecoder: the contexts' second opinions
	 * need the rebuilding's predictions; and, version 1, the earlier
	 * channels' deltas, which DeltaMapper.MixedModels reads as they are
	 * decoded, a row or two ahead).
	 */
	static final class TablesDecoder implements DeltaMapper.DeltaDecoder
	{
		/** Version 1's contexts (null for version 2's, the channel's own). */
		private final DeltaMapper.MixedModels mm;
		/** The width. */
		private final int xdim;
		/** The rebuilding's prediction at the pixel, and its column and row (version 2). */
		private int prediction, column, row;
		/** The model whose contexts are used, and the precision. */
		private final int model, precision;
		/** Whether its contexts are merged. */
		private final boolean merged;
		/** The delta value of each rank. */
		private final int[] value;
		/** Each context's frequencies and cumulative frequencies (null for one not used). */
		private final int[][] freq, cum;
		/** Each context's lookup: the symbol at each of the 2^precision counts (null for one not used). */
		private final char[][] lookup;
		/** The coded bytes. */
		private final byte[] code;
		/** The next coded byte. */
		private int at;
		/** rANS's state. */
		private long x;

		/**
		 * A decoder at the start of a channel's coding.
		 *
		 * @param h        the coding
		 * @param previous the deltas of the channels before this one (filled in as they decode; version 1 only)
		 * @param xdim     the width
		 * @param n        the number of deltas
		 * @throws IOException if the tables aren't what code writes
		 */
		TablesDecoder(TablesChannel h, int[][] previous, int xdim, int n) throws IOException
		{
			this.xdim = xdim;
			mm = (h.model == OWN) ? null : contextModels(previous, xdim, n);
			if(mm != null && h.model >= mm.m) throw new IOException("bad Context tables coding: model " + h.model + " of " + mm.m);
			model = h.model; merged = h.merged; precision = h.precision; value = h.value;
			int contexts = (mm == null) ? OWN_CONTEXTS : merged ? MERGED_CONTEXTS : CONTEXTS, symbols = value.length, top = LEVEL_RANGE * h.steps;
			LengthCoding.BitReader bits = new LengthCoding.BitReader(h.tables);
			int[] length = new int[contexts];
			int total = 0, used = 0;
			for(int c = 0; c < contexts; c++)
			{
				length[c] = LengthCoding.readGamma(bits) - 1;
				if(length[c] > symbols) throw new IOException("bad Context tables coding: a table longer than the symbols");
				total += length[c];
				if(length[c] > 0) used++;
			}
			int[] start = new int[used + 1];
			for(int c = 0, at = 0, t = 0; c < contexts; c++) if(length[c] > 0) { start[t++] = at; at += length[c]; }
			start[used] = total;
			int[] all = unchain(LengthCoding.readLengthSymbols(bits, total, 2 * top), start, top);
			if(bits.overrun()) throw new IOException("bad Context tables coding: the tables end too soon");
			freq = new int[contexts][];
			lookup = new char[contexts][];
			for(int c = 0, a = 0; c < contexts; a += length[c], c++)
			{
				if(length[c] == 0) continue;
				int[] level = Arrays.copyOfRange(all, a, a + length[c]);
				if(level[level.length - 1] == 0) throw new IOException("bad Context tables coding: a table ends with a symbol that isn't there");
				freq[c] = normalize(level, h.steps, precision);
				lookup[c] = new char[1 << precision];
				for(int s = 0, t = 0; s < freq[c].length; t += freq[c][s], s++) Arrays.fill(lookup[c], t, t + freq[c][s], (char) s);
			}
			cum = cumulative(freq);
			code = h.code;
			x = (code[0] & 0xffL) | ((code[1] & 0xffL) << 8) | ((code[2] & 0xffL) << 16) | ((code[3] & 0xffL) << 24);
			at = 4;
		}

		@Override public void next(int k, int[] d, int[] v) { decode(k, d, v); }

		@Override public void next(int k, int[] d, int[] v, int prediction)
		{
			if(mm != null) mm.prediction = prediction;
			this.prediction = prediction;
			decode(k, d, v);
		}

		/**
		 * Decodes delta k into d[k]: its context, its symbol from the
		 * context's lookup, and rANS's state moved on (and renormalized).
		 *
		 * @param k the pixel (0, 1, 2, ... in turn)
		 * @param d the deltas (filled below k)
		 * @param v the values (filled below k)
		 */
		private void decode(int k, int[] d, int[] v)
		{
			int c;
			if(mm == null)
			{
				c = ownContext(d, v, k, column, row, xdim, prediction);
				if(++column == xdim) { column = 0; row++; }
			}
			else
			{
				c = mm.context(k, d, v, model);
				if(merged) c = merged(c);
			}
			char[] look = lookup[c];
			if(look == null) throw new UncheckedIOException(new IOException("bad Context tables coding: a context with no table"));
			int low = (int) (x & ((1 << precision) - 1));
			int s = look[low];
			x = freq[c][s] * (x >>> precision) + low - cum[c][s];
			while(x < RANS_LOW) x = (x << 8) | ((at < code.length) ? code[at++] & 0xff : 0);
			d[k] = value[s];
		}

		@Override public void finish() throws IOException
		{
			if(at != code.length || x != RANS_LOW) throw new IOException("bad Context tables coding: it doesn't end where it should");
		}

		/**
		 * The channel (version 2) decoded and rebuilt from its average
		 * deltas (ContextTables.average), as next would, a pixel at a time,
		 * but with rANS's state in locals and the interior's contexts from
		 * the left value and delta carried along.
		 *
		 * @param ydim the band's height
		 * @param init the first value
		 * @param d    gets the deltas (xdim * ydim)
		 * @param v    gets the values (xdim * ydim)
		 * @throws IOException if the coding isn't what code writes
		 */
		void average(int ydim, int init, int[] d, int[] v) throws IOException
		{
			try
			{
				// Row 0 and each row's first and last columns as next does them.
				next(0, d, v, init);
				v[0] = init;
				for(int c = 1; c < xdim; c++) { next(c, d, v, v[c - 1]); v[c] = v[c - 1] + d[c]; }
				for(int y = 1, k = xdim; y < ydim; y++)
				{
					next(k, d, v, v[k - xdim]);
					v[k] = v[k - xdim] + d[k];
					k++;
					long x = this.x;
					int at = this.at, mask = (1 << precision) - 1, end = k + xdim - 2;
					int w = v[k - 1], dw = d[k - 1];
					for(; k < end; k++)
					{
						int n = v[k - xdim], p = (w + n) / 2;
						int c = interiorContext(Math.abs(dw) + Math.abs(d[k - xdim]) + Math.abs(d[k - xdim - 1]) + Math.abs(d[k - xdim + 1]),
						                        w, n, v[k - xdim - 1], p);
						char[] look = lookup[c];
						if(look == null) throw new IOException("bad Context tables coding: a context with no table");
						int low = (int) (x & mask), s = look[low];
						x = freq[c][s] * (x >>> precision) + low - cum[c][s];
						while(x < RANS_LOW) x = (x << 8) | ((at < code.length) ? code[at++] & 0xff : 0);
						d[k] = dw = value[s];
						v[k] = w = p + dw;
					}
					this.x = x;
					this.at = at;
					if(xdim > 1)
					{
						column = xdim - 1;   // (where next's own count would be)
						row    = y;
						int p = (v[k - 1] + v[k - xdim]) / 2;
						next(k, d, v, p);
						v[k] = p + d[k];
						k++;
					}
				}
			}
			catch(UncheckedIOException e) { throw e.getCause(); }
			finish();
		}
	}

	/**
	 * A band's channel decoded (version 2: the channel's own contexts) and
	 * rebuilt from its average deltas (DeltaMapper.getAverageDeltasFromValues'
	 * prediction), a pixel at a time, into dst from offset on.
	 *
	 * @param h      the coding
	 * @param xdim   the width
	 * @param ydim   the band's height
	 * @param init   the first value
	 * @param dst    gets the values
	 * @param offset where the band's first value goes in dst
	 * @throws IOException if the coding isn't what code writes
	 */
	static void average(TablesChannel h, int xdim, int ydim, int init, int[] dst, int offset) throws IOException
	{
		int n = xdim * ydim;
		int[] d = new int[n], v = new int[n];
		new TablesDecoder(h, null, xdim, n).average(ydim, init, d, v);
		System.arraycopy(v, 0, dst, offset, n);
	}

	/**
	 * A band's channel's deltas decoded with its values and predictions known
	 * (the writers' check: the coding decodes back to the deltas, given what
	 * the decoder would have; version 2).
	 *
	 * @param h      the coding
	 * @param value  the channel's values
	 * @param offset where the band's first value is in value
	 * @param delta  the deltas it was coded from (for the predictions, value - delta)
	 * @param xdim   the width
	 * @return the deltas decoded
	 * @throws IOException if the coding isn't what code writes
	 */
	static int[] decodeKnown(TablesChannel h, int[] value, int offset, int[] delta, int xdim) throws IOException
	{
		TablesDecoder dec = new TablesDecoder(h, null, xdim, delta.length);
		int[] d = new int[delta.length], v = Arrays.copyOfRange(value, offset, offset + delta.length);
		try { for(int k = 0; k < d.length; k++) dec.next(k, d, v, v[k] - delta[k]); }
		catch(UncheckedIOException e) { throw e.getCause(); }
		dec.finish();
		return d;
	}
}
