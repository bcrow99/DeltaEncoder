import java.util.*;
import java.math.*;

//version 4.0

/*
 * The order-table coders (getAscendingTable, getRandomOrderTable, the
 * order-table overloads of getIntervalValue/getArithmeticValues, etc.)
 * live in SteeredArithmeticMapper.
 */
public class ArithmeticMapper
{
	/**
	 * Exact rational arithmetic. A minimal local copy of FractionMapper's
	 * BigFraction with only what this file uses, so this file doesn't
	 * depend on FractionMapper.java.
	 */
	public static final class BigFraction
	{
		public final BigInteger n, d; // invariant: d > 0, gcd(|n|,d) == 1 (or n==0, d==1)

		public BigFraction(BigInteger numerator, BigInteger denominator)
		{
			if (numerator.signum() == 0 && denominator.signum() == 0)
				throw new ArithmeticException("0/0 is an indeterminate form");

			if (denominator.signum() < 0)
			{
				numerator   = numerator.negate();
				denominator = denominator.negate();
			}

			if (numerator.signum() == 0)
			{
				denominator = BigInteger.ONE;
			}
			else
			{
				BigInteger divisor = denominator.gcd(numerator);
				if (!divisor.equals(BigInteger.ONE))
				{
					numerator   = numerator.divide(divisor);
					denominator = denominator.divide(divisor);
				}
			}

			this.n = numerator;
			this.d = denominator;
		}

		public static BigFraction of(long n, long d) { return new BigFraction(BigInteger.valueOf(n), BigInteger.valueOf(d)); }
		public static final BigFraction ZERO = BigFraction.of(0, 1);
		public static final BigFraction ONE  = BigFraction.of(1, 1);

		// Operand size (in bits) above which parallelMultiply is used. Only
		// helps on multi-core machines; see FractionMapper for details.
		private static final int PARALLEL_MULTIPLY_THRESHOLD_BITS = 4000;

		private static BigInteger smartMultiply(BigInteger a, BigInteger b)
		{
			if (a.bitLength() >= PARALLEL_MULTIPLY_THRESHOLD_BITS && b.bitLength() >= PARALLEL_MULTIPLY_THRESHOLD_BITS)
				return a.parallelMultiply(b);
			return a.multiply(b);
		}

		public BigFraction add(BigFraction fraction)      { return new BigFraction(smartMultiply(n, fraction.d).add(smartMultiply(fraction.n, d)), smartMultiply(d, fraction.d)); }
		public BigFraction subtract(BigFraction fraction) { return new BigFraction(smartMultiply(n, fraction.d).subtract(smartMultiply(fraction.n, d)), smartMultiply(d, fraction.d)); }
		public BigFraction multiply(BigFraction fraction) { return new BigFraction(smartMultiply(n, fraction.n), smartMultiply(d, fraction.d)); }
		public BigFraction divide(BigFraction fraction)   { return new BigFraction(smartMultiply(n, fraction.d), smartMultiply(d, fraction.n)); }
		public BigFraction multiply(long k)               { return new BigFraction(smartMultiply(n, BigInteger.valueOf(k)), d); }

		public int compareTo(BigFraction fraction) { return smartMultiply(n, fraction.d).compareTo(smartMultiply(fraction.n, d)); }
		public boolean le(BigFraction fraction) { return compareTo(fraction) <= 0; }
		public boolean gt(BigFraction fraction) { return compareTo(fraction) > 0; }

		@Override public String toString() { return n + "/" + d; }
	}


	// =========================================================================
	// simplestFractionInInterval: the fraction with the smallest denominator
	// in [lo, hi). lo and hi need not share a denominator.
	// =========================================================================
	public static BigInteger[] simplestFractionInInterval(BigInteger loN, BigInteger loD, BigInteger hiN, BigInteger hiD)
	{
		// The interval is half-open: any point in [off, off+rng) decodes,
		// including off itself. The search below only looks strictly inside
		// (lo, hi), so the result is compared against lo (reduced) at the
		// end; otherwise a simple lo would lose to a more complex fraction.
		BigInteger origLoN = loN, origLoD = loD;

		ArrayList<BigInteger> floors = new ArrayList<BigInteger>();

		BigInteger p, q;
		while (true)
		{
			BigInteger flo = floorDiv(loN, loD);
			BigInteger candidate = flo.add(BigInteger.ONE);

			if (candidate.multiply(hiD).compareTo(hiN) < 0)
			{
				p = candidate;
				q = BigInteger.ONE;
				break;
			}

			BigInteger loFracN = loN.subtract(flo.multiply(loD));
			BigInteger hiFracN = hiN.subtract(flo.multiply(hiD));

			if (loFracN.equals(BigInteger.ZERO))
			{
				BigInteger k = hiD.divide(hiFracN).add(BigInteger.ONE);
				p = flo.multiply(k).add(BigInteger.ONE);
				q = k;
				break;
			}

			floors.add(flo);
			BigInteger newLoN = hiD, newLoD = hiFracN;
			BigInteger newHiN = loD, newHiD = loFracN;
			loN = newLoN; loD = newLoD; hiN = newHiN; hiD = newHiD;
		}

		for (int i = floors.size() - 1; i >= 0; i--)
		{
			BigInteger flo = floors.get(i);
			BigInteger newP = flo.multiply(p).add(q);
			q = p;
			p = newP;
		}

		BigInteger g = p.gcd(q);
		p = p.divide(g); q = q.divide(g);

		BigInteger loG = origLoN.gcd(origLoD);
		BigInteger loReducedN = origLoN.divide(loG), loReducedD = origLoD.divide(loG);
		if (loReducedD.compareTo(q) <= 0)
			return new BigInteger[]{ loReducedN, loReducedD };
		return new BigInteger[]{ p, q };
	}

	private static BigInteger floorDiv(BigInteger n, BigInteger d)
	{
		BigInteger[] qr = n.divideAndRemainder(d);
		if (qr[1].signum() != 0 && n.signum() < 0)
			return qr[0].subtract(BigInteger.ONE);
		return qr[0];
	}

	/**
	 * Arithmetic encode {@code src} using adaptive frequencies and return the
	 * simplest fraction (smallest denominator) within the valid encoding interval.
	 */
	public static BigInteger[] getIntervalValue(byte[] src, int[] frequency)
	{
		int[] f = frequency.clone();
		int n = src.length;

		int[] s = new int[f.length];
		int m = 0;
		for (int i = 0; i < f.length; i++) { s[i] = m; m += f[i]; }

		BigFraction off = BigFraction.ZERO;
		BigFraction rng = BigFraction.ONE;

		for (int i = 0; i < n; i++)
		{
			int j = src[i];
			if (j < 0) j += 256;

			off = off.add(rng.multiply(BigFraction.of(s[j], m)));
			rng = rng.multiply(BigFraction.of(f[j], m));

			f[j]--;
			m--;
			for (int k = j + 1; k < s.length; k++) s[k]--;
		}

		BigFraction hi = off.add(rng);
		return simplestFractionInInterval(off.n, off.d, hi.n, hi.d);
	}

	// This version uses a binary search to find the value that fits in the current interval.
	public static byte[] getArithmeticValues(BigInteger[] v, int[] frequency, int n)
	{
		BigFraction target = new BigFraction(v[0], v[1]);
		byte[] value = new byte[n];

		ArrayList<ArrayList<Integer>> arithmetic_list = new ArrayList<>();
		int m = 0;
		for (int i = 0; i < frequency.length; i++)
		{
			if (frequency[i] != 0)
			{
				ArrayList<Integer> list = new ArrayList<>();
				list.add(i); list.add(frequency[i]); list.add(m);
				arithmetic_list.add(list);
				m += frequency[i];
			}
		}

		BigFraction offset = BigFraction.ZERO;
		BigFraction range = BigFraction.ONE;

		for (int i = 0; i < n; i++)
		{
			BigFraction w = target.subtract(offset);

			int j = arithmetic_list.size() / 2;
			ArrayList<Integer> list = arithmetic_list.get(j);
			int f = list.get(1);
			int s = list.get(2);

			BigFraction a = range.multiply(BigFraction.of(s, m));
			BigFraction c = range.multiply(BigFraction.of(s + f, m));

			if (a.gt(w))
			{
				int k = j / 2;
				while (a.gt(w))
				{
					j -= k;
					list = arithmetic_list.get(j);
					f = list.get(1); s = list.get(2);
					a = range.multiply(BigFraction.of(s, m));
					k /= 2;
					if (k == 0) k = 1;
				}
				c = range.multiply(BigFraction.of(s + f, m));
				if (c.le(w))
				{
					while (c.le(w))
					{
						j++;
						list = arithmetic_list.get(j);
						f = list.get(1); s = list.get(2);
						c = range.multiply(BigFraction.of(s + f, m));
					}
				}
			}
			else if (c.le(w))
			{
				int size = arithmetic_list.size();
				int k = (size - j) / 2;
				while (c.le(w))
				{
					j += k;
					list = arithmetic_list.get(j);
					f = list.get(1); s = list.get(2);
					c = range.multiply(BigFraction.of(s + f, m));
					k /= 2;
					if (k == 0) k = 1;
				}
				a = range.multiply(BigFraction.of(s, m));
				if (a.gt(w))
				{
					while (a.gt(w))
					{
						j--;
						list = arithmetic_list.get(j);
						f = list.get(1); s = list.get(2);
						a = range.multiply(BigFraction.of(s, m));
					}
				}
			}

			offset = offset.add(range.multiply(BigFraction.of(s, m)));
			range = range.multiply(BigFraction.of(f, m));

			for (int p = j + 1; p < arithmetic_list.size(); p++)
			{
				ArrayList<Integer> list2 = arithmetic_list.get(p);
				int s2 = list2.get(2);
				s2--;
				list2.set(2, s2);
				arithmetic_list.set(p, list2);
			}

			f--;
			m--;
			if (f != 0)
			{
				list.set(1, f);
				arithmetic_list.set(j, list);
			}
			else
				arithmetic_list.remove(j);

			int k = list.get(0);
			value[i] = (byte) k;
		}
		return value;
	}

	private static int[] fenwickBuild(int[] frequency)
	{
		int[] bit = new int[257];
		for (int i = 0; i < 256; i++)
			if (frequency[i] > 0) fenwickUpdate(bit, i, frequency[i]);
		return bit;
	}

	private static void fenwickUpdate(int[] bit, int i, int delta)
	{
		for (i += 1; i <= 256; i += i & -i) bit[i] += delta;
	}

	private static int fenwickQuery(int[] bit, int i)
	{
		int sum = 0;
		for (i += 1; i > 0; i -= i & -i) sum += bit[i];
		return sum;
	}

	private static int fenwickFind(int[] bit, int target)
	{
		int pos = 0;
		for (int b = 8; b >= 0; b--)
		{
			int nxt = pos + (1 << b);
			if (nxt <= 256 && bit[nxt] <= target) { target -= bit[nxt]; pos = nxt; }
		}
		return pos;
	}

	/** Encoder: same as getIntervalValue but O(log 256) adaptive updates via Fenwick tree. */
	public static BigInteger[] getIntervalValueFenwick(byte[] src, int[] frequency)
	{
		int[] f = frequency.clone();
		int n = src.length;
		int[] bit = fenwickBuild(f);
		int m = 0; for (int v : f) m += v;

		BigFraction off = BigFraction.ZERO;
		BigFraction rng = BigFraction.ONE;

		for (int i = 0; i < n; i++)
		{
			int j = src[i]; if (j < 0) j += 256;
			int sj = (j > 0) ? fenwickQuery(bit, j - 1) : 0;

			off = off.add(rng.multiply(BigFraction.of(sj, m)));
			rng = rng.multiply(BigFraction.of(f[j], m));

			fenwickUpdate(bit, j, -1);
			f[j]--; m--;
		}

		BigFraction hi = off.add(rng);
		return simplestFractionInInterval(off.n, off.d, hi.n, hi.d);
	}

	/** Decoder: same as getArithmeticValues but O(log 256) symbol search and updates via Fenwick tree. */
	public static byte[] getArithmeticValuesFenwick(BigInteger[] v, int[] frequency, int n)
	{
		int[] f = frequency.clone();
		int[] bit = fenwickBuild(f);
		int m = 0; for (int fv : f) m += fv;

		BigFraction target = new BigFraction(v[0], v[1]);
		BigFraction offset = BigFraction.ZERO;
		BigFraction range = BigFraction.ONE;

		byte[] value = new byte[n];

		for (int i = 0; i < n; i++)
		{
			BigFraction w = target.subtract(offset);

			// target = w / range, scaled by m -- find which symbol's
			// cumulative range contains this position via Fenwick search
			BigFraction scaledFrac = w.divide(range).multiply(m);
			long scaledLong = scaledFrac.n.divide(scaledFrac.d).longValue();
			int targetIdx = (int) Math.min(Math.max(scaledLong, 0L), (long) (m - 1));
			int j = fenwickFind(bit, targetIdx);
			while (j < 255 && f[j] == 0) j++;

			value[i] = (byte) j;
			int sj = (j > 0) ? fenwickQuery(bit, j - 1) : 0;

			offset = offset.add(range.multiply(BigFraction.of(sj, m)));
			range = range.multiply(BigFraction.of(f[j], m));

			fenwickUpdate(bit, j, -1);
			f[j]--; m--;
		}

		return value;
	}

	// =========================================================================
	// Fast arithmetic coder (no BigInteger), using the range coder below.
	//
	// The counts start as the exact byte counts and each byte is counted
	// down as it is coded. Rounding the interval to a multiple of
	// total/range costs a small fraction of a bit per thousand bytes.
	//
	// Output: the coded bytes, with no length header. The caller stores the
	// number of bytes coded, n; the decoder reads zeros past the end.
	//
	// The ...Fast and ...FastFenwick methods are the same coder (running
	// totals in a Fenwick tree); both names are kept for callers.
	//
	// Limits: the total count (bytes in the block) must stay under 2^30.
	// The rounding loss stays under about 0.01% for totals below 2^24.
	// =========================================================================

	public static byte[] getIntervalValueFast(byte[] src, int[] frequency)
	{
		return getIntervalValueFastFenwick(src, frequency);
	}

	public static byte[] getArithmeticValuesFast(byte[] encoded, int[] frequency, int n)
	{
		return getArithmeticValuesFastFenwick(encoded, frequency, n);
	}

	public static byte[] getIntervalValueFastFenwick(byte[] src, int[] frequency)
	{
		int[] f   = frequency.clone();
		int[] bit = fenwickBuild(f);
		int   m   = 0; for (int v : f) m += v;

		RangeEncoder encoder = new RangeEncoder(src.length);
		for (int i = 0; i < src.length; i++)
		{
			int j  = src[i] & 0xFF;
			int sj = (j > 0) ? fenwickQuery(bit, j - 1) : 0;
			encoder.encode(sj, f[j], m);
			fenwickUpdate(bit, j, -1);
			f[j]--; m--;
		}
		return encoder.finish();
	}

	public static byte[] getArithmeticValuesFastFenwick(byte[] encoded, int[] frequency, int n)
	{
		int[] f   = frequency.clone();
		int[] bit = fenwickBuild(f);
		int   m   = 0; for (int v : f) m += v;

		RangeDecoder decoder = new RangeDecoder(encoded);
		byte[] value = new byte[n];
		for (int i = 0; i < n; i++)
		{
			int target = decoder.target(m);
			int j  = fenwickFind(bit, target);
			int sj = (j > 0) ? fenwickQuery(bit, j - 1) : 0;
			decoder.decode(sj, f[j]);
			value[i] = (byte) j;
			fenwickUpdate(bit, j, -1);
			f[j]--; m--;
		}
		return value;
	}

	// ---- Range coder --------------------------------------------------------
	//
	// Byte-oriented, with LZMA-style carry propagation. low holds the bottom
	// of the interval in its low 40 bits (plus a possible carry into bit 40);
	// range stays between 2^32 and 2^40, shifting out a byte when it drops
	// below 2^32. The top byte of low is held in `cache` until it can no
	// longer change, along with a count of following 0xFF bytes that a
	// carry would also change.

	private static final long RANGE_TOP    = 1L << 40;
	private static final long RANGE_BOTTOM = 1L << 32;

	private static final class RangeEncoder
	{
		byte[] out;
		int    size = 0;
		long   low = 0, range = RANGE_TOP - 1;
		int    cache = 0;
		long   cache_size = 1;

		RangeEncoder(int n) { out = new byte[Math.max(16, n + n / 4 + 16)]; }

		// Narrows the interval to [start, start + count) out of total.
		void encode(long start, long count, long total)
		{
			long r = range / total;
			low  += start * r;
			range = count * r;
			while (range < RANGE_BOTTOM)
			{
				range <<= 8;
				shiftLow();
			}
		}

		void shiftLow()
		{
			if (low < 0xFF00000000L || low >= RANGE_TOP)
			{
				int carry = (int) (low >>> 40);
				int temp  = cache;
				do
				{
					put(temp + carry);
					temp = 0xFF;
				}
				while (--cache_size != 0);
				cache = (int) ((low >>> 32) & 0xFF);
			}
			cache_size++;
			low = (low & 0xFFFFFFFFL) << 8;
		}

		void put(int b)
		{
			if (size == out.length) out = Arrays.copyOf(out, out.length * 2);
			out[size++] = (byte) b;
		}

		byte[] finish()
		{
			for (int k = 0; k < 6; k++) shiftLow();
			return Arrays.copyOf(out, size);
		}
	}

	private static final class RangeDecoder
	{
		final byte[] in;
		int  pos = 1;             // the first byte written is always the initial cache
		long code = 0, range = RANGE_TOP - 1, r;

		RangeDecoder(byte[] in)
		{
			this.in = in;
			for (int k = 0; k < 5; k++) code = (code << 8) | next();
		}

		int next() { return (pos < in.length) ? (in[pos++] & 0xFF) : 0; }

		// Where the code falls, as a count out of total. Must be followed by
		// decode() with the interval that holds it.
		int target(long total)
		{
			r = range / total;
			long t = code / r;
			return (int) Math.min(t, total - 1);
		}

		void decode(long start, long count)
		{
			code -= start * r;
			range = count * r;
			while (range < RANGE_BOTTOM)
			{
				range <<= 8;
				code = (code << 8) | next();
			}
		}
	}

	// =========================================================================
	// Adaptive arithmetic coder (context-modeled, no stored table).
	//
	// Uses the RangeEncoder/RangeDecoder above, but learns the counts as it
	// goes (the decoder learns them the same way), so no table is stored.
	// Counts are kept per context: 2^ADAPTIVE_CONTEXT_BITS contexts, chosen
	// by the top bits of the previous byte. Every count starts at 1; each
	// coded byte adds ADAPTIVE_INCREMENT. If a context's total passes
	// ADAPTIVE_LIMIT its counts are halved (staying >= 1), keeping totals
	// well inside the coder's precision.
	//
	// Tested best on the sample images: 16 contexts (top 4 bits), increment
	// 2. 256 contexts lost on small images (too little data to learn them);
	// larger increments overweighted the first bytes in each context.
	//
	// Output: the coded bytes, with no length header. The caller stores the
	// number of bytes coded, n; the decoder reads zeros past the end.
	// =========================================================================

	public static final int ADAPTIVE_CONTEXT_BITS = 4;
	public static final int ADAPTIVE_INCREMENT    = 2;
	public static final int ADAPTIVE_LIMIT        = 1 << 22;

	public static byte[] getIntervalValueAdaptive(byte[] src)
	{
		return getIntervalValueAdaptive(src, ADAPTIVE_CONTEXT_BITS, ADAPTIVE_INCREMENT, ADAPTIVE_LIMIT);
	}

	public static byte[] getArithmeticValuesAdaptive(byte[] encoded, int n)
	{
		return getArithmeticValuesAdaptive(encoded, n, ADAPTIVE_CONTEXT_BITS, ADAPTIVE_INCREMENT, ADAPTIVE_LIMIT);
	}

	// One context's counts, with a Fenwick tree over them for the running
	// totals.
	private static final class AdaptiveModel
	{
		final int[] f   = new int[256];
		final int[] bit = new int[257];
		int total;
		final int increment, limit;

		AdaptiveModel(int increment, int limit)
		{
			this.increment = increment;
			this.limit     = limit;
			Arrays.fill(f, 1);
			rebuild();
		}

		void rebuild()
		{
			Arrays.fill(bit, 0);
			total = 0;
			for (int s = 0; s < 256; s++)
			{
				total += f[s];
				for (int i = s + 1; i <= 256; i += i & -i) bit[i] += f[s];
			}
		}

		// Total count of the byte values below s.
		int start(int s)
		{
			int sum = 0;
			for (int i = s; i > 0; i -= i & -i) sum += bit[i];
			return sum;
		}

		// The byte value whose interval holds target (0 <= target < total).
		int find(int target)
		{
			int pos = 0;
			for (int b = 8; b >= 0; b--)
			{
				int nxt = pos + (1 << b);
				if (nxt <= 256 && bit[nxt] <= target) { target -= bit[nxt]; pos = nxt; }
			}
			return pos;
		}

		void update(int s)
		{
			f[s]  += increment;
			total += increment;
			for (int i = s + 1; i <= 256; i += i & -i) bit[i] += increment;
			if (total > limit)
			{
				for (int k = 0; k < 256; k++) f[k] = (f[k] + 1) >>> 1;
				rebuild();
			}
		}
	}

	private static AdaptiveModel[] adaptiveModels(int context_bits, int increment, int limit)
	{
		AdaptiveModel[] model = new AdaptiveModel[1 << context_bits];
		for (int k = 0; k < model.length; k++) model[k] = new AdaptiveModel(increment, limit);
		return model;
	}

	public static byte[] getIntervalValueAdaptive(byte[] src, int context_bits, int increment, int limit)
	{
		AdaptiveModel[] model = adaptiveModels(context_bits, increment, limit);
		RangeEncoder encoder = new RangeEncoder(src.length);
		int previous = 0;
		for (int i = 0; i < src.length; i++)
		{
			int j = src[i] & 0xFF;
			AdaptiveModel m = model[previous >>> (8 - context_bits)];
			encoder.encode(m.start(j), m.f[j], m.total);
			m.update(j);
			previous = j;
		}
		return encoder.finish();
	}

	public static byte[] getArithmeticValuesAdaptive(byte[] encoded, int n, int context_bits, int increment, int limit)
	{
		AdaptiveModel[] model = adaptiveModels(context_bits, increment, limit);
		RangeDecoder decoder = new RangeDecoder(encoded);
		byte[] value = new byte[n];
		int previous = 0;
		for (int i = 0; i < n; i++)
		{
			AdaptiveModel m = model[previous >>> (8 - context_bits)];
			int j = m.find(decoder.target(m.total));
			decoder.decode(m.start(j), m.f[j]);
			value[i] = (byte) j;
			m.update(j);
			previous = j;
		}
		return value;
	}

	// =========================================================================
	// Context-adaptive coder: like the adaptive coder above, but over any
	// alphabet (n_symbols) and with the context of each symbol chosen by the
	// caller rather than by the previous byte. Each context has its own
	// counts, starting at 1, +CONTEXT_INCREMENT per symbol coded, halved
	// (keeping them >= 1) when a context's total passes CONTEXT_LIMIT.
	//
	// The decoder can't be handed the contexts up front -- they usually
	// depend on symbols not yet decoded -- so it asks a ContextFunction for
	// each one as it goes.
	//
	// Output: the range coder's bytes; the caller stores the symbol count.
	// =========================================================================

	public static final int CONTEXT_INCREMENT = 32;
	public static final int CONTEXT_LIMIT     = 1 << 16;

	// The context (0..n_contexts-1) of symbol k; symbol[0..k-1] are decoded.
	public interface ContextFunction { int context(int k, int[] symbol); }

	public static byte[] getIntervalValueContext(int[] symbol, int n_symbols, int[] context, int n_contexts)
	{
		ContextModel[] model   = contextModels(n_contexts, n_symbols);
		RangeEncoder   encoder = new RangeEncoder(symbol.length / 2);
		for(int k = 0; k < symbol.length; k++)
		{
			ContextModel m = model[context[k]];
			int s = symbol[k];
			encoder.encode(m.start(s), m.f[s], m.total);
			m.update(s);
		}
		return encoder.finish();
	}

	public static int[] getArithmeticValuesContext(byte[] coded, int n, int n_symbols, int n_contexts, ContextFunction function)
	{
		ContextModel[] model   = contextModels(n_contexts, n_symbols);
		RangeDecoder   decoder = new RangeDecoder(coded);
		int[]          symbol  = new int[n];
		for(int k = 0; k < n; k++)
		{
			ContextModel m = model[function.context(k, symbol)];
			int s = m.find(decoder.target(m.total));
			decoder.decode(m.start(s), m.f[s]);
			symbol[k] = s;
			m.update(s);
		}
		return symbol;
	}

	private static ContextModel[] contextModels(int n_contexts, int n_symbols)
	{
		ContextModel[] model = new ContextModel[n_contexts];
		for(int c = 0; c < n_contexts; c++) model[c] = new ContextModel(n_symbols);
		return model;
	}

	// One context's counts over n symbols, with a Fenwick tree for the
	// running totals.
	private static final class ContextModel
	{
		final int[] f, bit;
		final int   n, top;
		int total;

		ContextModel(int n)
		{
			this.n = n;
			f   = new int[n];
			bit = new int[n + 1];
			top = Integer.highestOneBit(n);
			Arrays.fill(f, 1);
			rebuild();
		}

		void rebuild()
		{
			Arrays.fill(bit, 0);
			total = 0;
			for(int s = 0; s < n; s++)
			{
				total += f[s];
				for(int i = s + 1; i <= n; i += i & -i) bit[i] += f[s];
			}
		}

		int start(int s)
		{
			int sum = 0;
			for(int i = s; i > 0; i -= i & -i) sum += bit[i];
			return sum;
		}

		int find(int target)
		{
			int pos = 0;
			for(int b = top; b > 0; b >>= 1)
			{
				int next = pos + b;
				if(next <= n && bit[next] <= target) { target -= bit[next]; pos = next; }
			}
			return pos;
		}

		void update(int s)
		{
			f[s]  += CONTEXT_INCREMENT;
			total += CONTEXT_INCREMENT;
			for(int i = s + 1; i <= n; i += i & -i) bit[i] += CONTEXT_INCREMENT;
			if(total > CONTEXT_LIMIT)
			{
				for(int k = 0; k < n; k++) f[k] = (f[k] + 1) >>> 1;
				rebuild();
			}
		}
	}

	// =========================================================================
	// Blocks and frequency tables, shared by the writers and readers.
	// =========================================================================

	// Splits src into n blocks of length/n bytes, the last one taking the
	// remainder.
	public static byte[][] getBlocks(byte[] src, int n)
	{
		int[]    length = getBlockLengths(src.length, n);
		byte[][] block  = new byte[n][];
		int      pos    = 0;
		for(int k = 0; k < n; k++) { block[k] = Arrays.copyOfRange(src, pos, pos + length[k]); pos += length[k]; }
		return block;
	}

	public static int[] getBlockLengths(int length, int n)
	{
		int[] block_length = new int[n];
		Arrays.fill(block_length, length / n);
		block_length[n - 1] += length % n;
		return block_length;
	}

	public static byte[] joinBlocks(byte[][] block)
	{
		int length = 0;
		for(byte[] b : block) length += b.length;
		byte[] dst = new byte[length];
		int pos = 0;
		for(byte[] b : block) { System.arraycopy(b, 0, dst, pos, b.length); pos += b.length; }
		return dst;
	}

	// Byte counts of src (256 entries).
	public static int[] getFrequency(byte[] src)
	{
		int[] frequency = new int[256];
		for(byte b : src) frequency[b & 0xFF]++;
		return frequency;
	}

	// Frequency tables are stored as n*256 counts, little-endian, 1, 2 or 4
	// bytes each (type 0, 1, 2: the smallest that holds the largest count),
	// then Deflated.
	public static int getFrequencyType(int[][] frequency)
	{
		int max = 0;
		for(int[] row : frequency) for(int v : row) if(v > max) max = v;
		return (max <= 255) ? 0 : (max <= 65535) ? 1 : 2;
	}

	public static byte[] deflateFrequencies(int[][] frequency, int type, int level)
	{
		int    width = (type == 0) ? 1 : (type == 1) ? 2 : 4;
		byte[] raw   = new byte[frequency.length * 256 * width];
		for(int k = 0; k < frequency.length; k++)
			for(int m = 0; m < 256; m++)
				for(int b = 0; b < width; b++)
					raw[(k * 256 + m) * width + b] = (byte)(frequency[k][m] >> (8 * b));
		return CodeMapper.deflate(raw, level);
	}

	public static int[][] inflateFrequencies(byte[] zipped, int n, int type) throws java.util.zip.DataFormatException
	{
		int     width     = (type == 0) ? 1 : (type == 1) ? 2 : 4;
		byte[]  raw       = CodeMapper.inflate(zipped, n * 256 * width);
		int[][] frequency = new int[n][256];
		for(int k = 0; k < n; k++)
			for(int m = 0; m < 256; m++)
			{
				int v = 0;
				for(int b = 0; b < width; b++) v |= (raw[(k * 256 + m) * width + b] & 0xFF) << (8 * b);
				frequency[k][m] = v;
			}
		return frequency;
	}

	// A set of tables as the writers store them: int n, int type, int
	// Deflated length, Deflated bytes.
	public static byte[] packFrequencies(int[][] frequency, int level)
	{
		int    type   = getFrequencyType(frequency);
		byte[] zipped = deflateFrequencies(frequency, type, level);
		java.nio.ByteBuffer buffer = java.nio.ByteBuffer.allocate(12 + zipped.length);
		buffer.putInt(frequency.length).putInt(type).putInt(zipped.length).put(zipped);
		return buffer.array();
	}

	public static int[][] readFrequencies(java.io.DataInputStream in) throws java.io.IOException, java.util.zip.DataFormatException
	{
		int    n      = in.readInt();
		int    type   = in.readInt();
		byte[] zipped = new byte[in.readInt()];
		in.readFully(zipped);
		return inflateFrequencies(zipped, n, type);
	}
}
