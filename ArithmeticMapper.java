import java.util.*;
import java.math.*;

//version 4.0

/*
 * Added after version 4.0:
 *   - getIntervalValueAdaptive / getArithmeticValuesAdaptive, an adaptive,
 *     context-modeled version of the fast coder that stores no frequency
 *     table (see the section at the end).
 *   - The fast coders (getIntervalValueFast/-Fenwick and
 *     getArithmeticValuesFast/-Fenwick) and the adaptive coder now use a
 *     byte-oriented range coder instead of the bit-at-a-time coder: two to
 *     three times faster, nearly the same size, and a different output
 *     format (no 4-byte length header; older files don't decode). The
 *     exact BigInteger coders are unchanged.
 *
 * Changes in this version:
 *
 *   1. All order-table-related code moved out to SteeredArithmeticMapper,
 *      to keep this file focused on the core (canonical-order) codec:
 *      getAscendingTable, getDescendingTable, getFirstTable, getLastTable,
 *      getTableSeries (1-4), getRandomTable (2 overloads),
 *      getRandomOrderTable (3 overloads), the order-table overloads of
 *      getIntervalValue and getArithmeticValues, and
 *      getApproxOffsetFastOrdered (plus its private LeadingBits helper).
 *      None of this was in active use -- kept for possible future use
 *      rather than deleted outright. simplestFractionInInterval stays
 *      here (still used by the core getIntervalValue/getIntervalValueFenwick),
 *      and SteeredArithmeticMapper already has its own independent copy
 *      for encodeCSequence, so the moved order-table methods call that
 *      one directly now that they live in the same file -- no cross-file
 *      dependency introduced.
 *
 *   2. Cleaned up two orphaned leftover comments spotted while doing this
 *      move: a stale section-header comment for the already-removed
 *      getSerialOffset/getSerialValues pairing, and a stale "Requires a
 *      < b." doc comment left over from the already-removed gcd(long,long)
 *      -- both were never cleaned up when those methods were removed in
 *      version 3.0, and this version's edits happened to pass right by
 *      them.
 *
 * (Prior versions' fixes/changes -- simplestFractionInInterval's Stern-
 * Brocot rewrite, the getArithmeticValuesFast/getArithmeticValuesFastFenwick
 * boundary nudge fix, and the version 2.0 BigFraction refactor of the
 * remaining slow/exact methods -- remain in place, unmodified by this
 * version.)
 */
public class ArithmeticMapper
{
	/**
	 * Exact rational arithmetic, local to this file so ArithmeticMapper
	 * doesn't depend on FractionMapper.java for now. A minimal copy of
	 * BigFraction, carrying only what this file actually
	 * uses (add/subtract/multiply/divide/compareTo/gt/le, plus the
	 * ZERO/ONE constants and the parallelMultiply threshold logic) --
	 * matching the "keep this file simple" preference this reorganization
	 * is already following, rather than pulling in the full feature set
	 * (negate/abs/isInfinite/equals/hashCode/lt/eq/toDouble) that this
	 * file has no use for.
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

		// Threshold (in bits) above which we ask the JDK to attempt a
		// parallelMultiply instead of a plain sequential multiply. Only
		// benefits machines with more than one core; see FractionMapper's
		// own copy of this same logic for the fuller discussion of why.
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
	// simplestFractionInInterval: UNCHANGED from the prior version. This
	// method already takes separate (loN,loD) / (hiN,hiD) pairs and cross-
	// multiplies correctly regardless of whether they share a denominator
	// -- it does not have the "assumed same denominator" bug class the
	// other methods in this file had, so there's nothing for a BigFraction
	// refactor to fix here.
	// =========================================================================
	public static BigInteger[] simplestFractionInInterval(BigInteger loN, BigInteger loD, BigInteger hiN, BigInteger hiD)
	{
		// The interval is half-open [lo, hi): lo itself is always a valid,
		// includable point (matching how off/off+rng are used everywhere
		// in this codebase -- any point in [off, off+rng) decodes
		// correctly, including off itself). The search below only looks
		// STRICTLY inside (lo, hi), so it must be compared against lo
		// itself (reduced to lowest terms) at the end -- without this, a
		// lo that's already simple gets needlessly passed over in favor
		// of a far more complex fraction found strictly between lo and hi.
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
	// Fast arithmetic coder (no BigInteger).
	//
	// A byte-oriented range coder: the interval is kept as a 40-bit range and
	// narrowed a whole byte at a time, with carries propagated into the bytes
	// already written, instead of the older 32-bit coder that decided and
	// wrote one bit at a time (with runs of pending bits). Same model -- the
	// counts start as the exact byte counts and each byte is counted down as
	// it is coded -- and nearly the same size (the interval is rounded to a
	// multiple of total/range, which costs a small fraction of a bit per
	// thousand bytes), but two to three times faster.
	//
	// The output format changed with this version: the coded bytes, with no
	// length header -- the caller stores the number of bytes coded, n, and
	// the decoder reads zeros past the end. Older files coded with the
	// bit-at-a-time coder don't decode with it.
	//
	// getIntervalValueFast / getArithmeticValuesFast and the ...Fenwick
	// versions are now the same coder (both keep the running totals in a
	// Fenwick tree); the names are kept so callers don't change.
	//
	// Limits: the total count (the number of bytes in the block) must stay
	// under 2^30. The rounding loss grows with the total, but stays under
	// about 0.01% for totals below 2^24 (16 million bytes).
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
	// low holds the bottom of the interval in its low 40 bits (plus a
	// possible carry into bit 40); range is kept between 2^32 and 2^40 by
	// shifting out a byte whenever it drops below 2^32. The top byte of low
	// isn't written until it can no longer change: it's held in `cache`,
	// along with a count of 0xFF bytes after it that a carry would also
	// change (the standard carry-propagation scheme used by LZMA's coder).

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
	// Same range coder as the fast coder above, but instead of
	// being given the byte counts up front, the coder learns them as it goes,
	// and the decoder learns them the same way -- so nothing has to be stored
	// besides the coded bits. It keeps a separate set of counts for each of
	// 2^ADAPTIVE_CONTEXT_BITS contexts, chosen by the top bits of the previous
	// byte, since the next byte depends noticeably on the one before it. Every
	// byte value starts at count 1 in every context; each coded byte adds
	// ADAPTIVE_INCREMENT to its count. If a context's total ever passes
	// ADAPTIVE_LIMIT its counts are halved (keeping them >= 1) -- a safety
	// limit that keeps the totals well inside the coder's precision.
	//
	// Settings chosen by testing on the sample images: 16 contexts (top 4
	// bits), increment 2. Fewer contexts learn faster but capture less; a
	// full 256-value context lost on small images, where there isn't enough
	// data to learn 256 sets of counts. Larger increments made the first
	// bytes seen in each context count for too much.
	//
	// Output: the coded bytes, with no length header -- the caller stores
	// the number of bytes coded, n, and the decoder reads zeros past the end.
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
}
