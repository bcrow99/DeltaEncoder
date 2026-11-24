import java.io.*;
import java.util.Arrays;

// ContextTables: the Context tables coding of a channel's deltas
// (GenericWriter's and NativeEncoder's NativeWriter's "Context tables"). The
// context aware coding's contexts (DeltaMapper.MixedModels: the activity,
// the earlier channels' deltas and a second opinion), with fixed tables in
// place of its adaptive models: the deltas are counted once, by context,
// the counts written as tables, and the deltas coded with them by rANS
// (range asymmetric numeral systems), which decodes a symbol with a table
// lookup and a multiply, and no division. Smaller than the Huffman codings,
// a little bigger than context aware (whose models adapt, and mix three
// opinions), and several times faster to decode.
//
// The symbols are the deltas' ranks (as the context aware coding's: the
// most frequent value is 0). One model's contexts are used: model 0, 1 or,
// after the first channel, 2 (MIX_OPINION_LEVELS times CONTEXTS of them),
// or the same with the earlier channels' cross buckets merged (merged:
// fewer tables, for a small image). Each context's table is its symbols'
// probabilities to a step of a factor of 2 (steps 1) or of the square root
// of 2 (steps 2), as levels 1 to LEVEL_RANGE times steps (0: a symbol that
// isn't there), from which coder and decoder make the same frequencies,
// out of 2^precision (normalize). The model, the merging and the steps are
// chosen by the coded size. See code for the layout.
/**
 * The Context tables coding of a channel's deltas (see the comment above).
 */
public class ContextTables
{
	/** Not made: static methods only. */
	private ContextTables() { }

	/**
	 * The coding's version, the Context tables format's ('T').
	 */
	static final int VERSION = 1;
	/**
	 * The number of contexts (each model's: the context aware coding's with
	 * the second opinions).
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
	 * A context, merged: its cross bucket left out (a context is (activity
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
			NeighbourMapper.BitWriter bits = new NeighbourMapper.BitWriter(1024);
			int[][] level = new int[contexts][];
			int total = 0, used = 0;
			for(int c = 0; c < contexts; c++)
			{
				int last = -1;
				for(int s = 0; s < count[c].length; s++) if(count[c][s] > 0) last = s;
				writeGamma(bits, last + 2);   // (1: not used)
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
			NeighbourMapper.writeLengthSymbols(bits, chain(all, start, top), 2 * top);
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
	 * Writes v (at least 1) in Elias's gamma code: its bit count less 1 as
	 * 0 bits, then v.
	 *
	 * @param bits the bit stream
	 * @param v    the number
	 */
	static void writeGamma(NeighbourMapper.BitWriter bits, int v)
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
	static int readGamma(NeighbourMapper.BitReader bits) throws IOException
	{
		int n = 1;
		while(bits.read(1) == 0) if(++n > 24) throw new IOException("bad Context tables coding: a table's length");
		return (n == 1) ? 1 : (1 << (n - 1)) | bits.read(n - 1);
	}

	/**
	 * The context aware coding's contexts (with the second opinions, from
	 * the prediction, value - delta): its models with no symbols, for their
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
	 * A channel's deltas Context tables coded (see the comment at the top).
	 * Each delta's rank is counted in each model's context; for each model,
	 * merged or not, and steps (in that order) the tables are made and the
	 * coded size worked out (Tables.size), and the first smallest is used.
	 * The ranks are then rANS coded, the last first, so that the decoder
	 * reads them first to last. Layout: int the smallest delta, the rank
	 * table (DeltaMapper.writeTable), byte the model, byte 1 if merged (else
	 * 0), byte the steps, byte the precision, int the tables' length, the
	 * tables, int the coded length, and the coded bytes: rANS's final state
	 * (4 bytes, the low first), then the bytes it gave out, in the order the
	 * decoder reads them. The tables: a bit stream (most significant bit
	 * first) of each context's number of levels (its last symbol + 1; 0 for
	 * a context not used) plus 1, in gamma code (writeGamma), then the used
	 * contexts' levels, one table after another, as chain writes them
	 * (NeighbourMapper.writeLengthSymbols, up to twice the top level).
	 *
	 * @param delta    the deltas
	 * @param value    the band's values, as the decoder rebuilds them
	 * @param previous the deltas of the channels coded before this one
	 * @param xdim     the width
	 * @return the coded bytes
	 * @throws IOException never in practice (a byte stream)
	 */
	static byte[] code(int[] delta, int[] value, int[][] previous, int xdim) throws IOException
	{
		int n = delta.length;
		int[] min = new int[1], rank = DeltaMapper.rankTable(delta, min);
		int symbols = rank.length, precision = precision(symbols);

		// Each model's counts, by context, and merged.
		DeltaMapper.MixedModels mm = contextModels(previous, xdim, n);
		int models = mm.m;
		int[][][][] count = new int[models][2][][];
		for(int j = 0; j < models; j++) { count[j][0] = new int[CONTEXTS][symbols]; count[j][1] = new int[MERGED_CONTEXTS][symbols]; }
		for(int k = 0; k < n; k++)
		{
			mm.prediction = value[k] - delta[k];
			mm.contexts(k, delta, value);
			int s = rank[delta[k] - min[0]];
			for(int j = 0; j < models; j++) count[j][0][mm.ctx[j]][s]++;
		}
		for(int j = 0; j < models; j++)
			for(int c = 0; c < CONTEXTS; c++)
				for(int s = 0; s < symbols; s++) count[j][1][merged(c)][s] += count[j][0][c][s];

		// The smallest.
		int model = 0, merge = 0, steps = 1;
		Tables best = null;
		long smallest = Long.MAX_VALUE;
		for(int j = 0; j < models; j++)
			for(int g = 0; g <= 1; g++)
				for(int st = 1; st <= 2; st++)
				{
					Tables t = new Tables(count[j][g], st, precision);
					long size = t.size(count[j][g], precision);
					if(size < smallest) { smallest = size; best = t; model = j; merge = g; steps = st; }
				}

		// Its contexts again (kept this time), then the coding, from the last.
		char[] ctx = new char[n];
		mm = contextModels(previous, xdim, n);
		for(int k = 0; k < n; k++)
		{
			mm.prediction = value[k] - delta[k];
			mm.contexts(k, delta, value);
			ctx[k] = (char) ((merge == 1) ? merged(mm.ctx[model]) : mm.ctx[model]);
		}
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
		out.writeByte(model);
		out.writeByte(merge);
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
		/** The model whose contexts are used. */
		int     model;
		/** Whether its contexts are merged. */
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
		if(h.model > 2 || merge > 1 || h.steps < 1 || h.steps > 2 || h.precision < MIN_PRECISION || h.precision > MAX_PRECISION)
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
	 * need the rebuilding's predictions; and the earlier channels' deltas,
	 * which DeltaMapper.MixedModels reads as they are decoded, a row or two
	 * ahead).
	 */
	static final class TablesDecoder implements DeltaMapper.DeltaDecoder
	{
		/** The contexts. */
		private final DeltaMapper.MixedModels mm;
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
		 * @param previous the deltas of the channels before this one (filled in as they decode)
		 * @param xdim     the width
		 * @param n        the number of deltas
		 * @throws IOException if the tables aren't what code writes
		 */
		TablesDecoder(TablesChannel h, int[][] previous, int xdim, int n) throws IOException
		{
			mm = contextModels(previous, xdim, n);
			if(h.model >= mm.m) throw new IOException("bad Context tables coding: model " + h.model + " of " + mm.m);
			model = h.model; merged = h.merged; precision = h.precision; value = h.value;
			int contexts = merged ? MERGED_CONTEXTS : CONTEXTS, symbols = value.length, top = LEVEL_RANGE * h.steps;
			NeighbourMapper.BitReader bits = new NeighbourMapper.BitReader(h.tables);
			int[] length = new int[contexts];
			int total = 0, used = 0;
			for(int c = 0; c < contexts; c++)
			{
				length[c] = readGamma(bits) - 1;
				if(length[c] > symbols) throw new IOException("bad Context tables coding: a table longer than the symbols");
				total += length[c];
				if(length[c] > 0) used++;
			}
			int[] start = new int[used + 1];
			for(int c = 0, at = 0, t = 0; c < contexts; c++) if(length[c] > 0) { start[t++] = at; at += length[c]; }
			start[used] = total;
			int[] all = unchain(NeighbourMapper.readLengthSymbols(bits, total, 2 * top), start, top);
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

		@Override public void next(int k, int[] d, int[] v, int prediction) { mm.prediction = prediction; decode(k, d, v); }

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
			int c = mm.context(k, d, v, model);
			if(merged) c = merged(c);
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
	}

	/**
	 * A band's channel's deltas decoded with its values and predictions known
	 * (the writers' check: the coding decodes back to the deltas, given what
	 * the decoder would have).
	 *
	 * @param h        the coding
	 * @param value    the band's values
	 * @param delta    the deltas it was coded from (for the predictions, value - delta)
	 * @param previous the deltas of the channels coded before this one
	 * @param xdim     the width
	 * @return the deltas decoded
	 * @throws IOException if the coding isn't what code writes
	 */
	static int[] decodeKnown(TablesChannel h, int[] value, int[] delta, int[][] previous, int xdim) throws IOException
	{
		TablesDecoder dec = new TablesDecoder(h, previous, xdim, delta.length);
		int[] d = new int[delta.length];
		try { for(int k = 0; k < d.length; k++) dec.next(k, d, value, value[k] - delta[k]); }
		catch(UncheckedIOException e) { throw e.getCause(); }
		dec.finish();
		return d;
	}
}
