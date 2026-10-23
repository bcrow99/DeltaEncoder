import java.util.*;
import java.math.*;

/**
 * Arithmetic and range coders for byte strings and symbol streams.
 * <p>
 * Four families of coders, from slowest and most exact to fastest:
 * <ul>
 * <li><b>Exact</b> ({@link #getIntervalValue}, {@link #getArithmeticValues} and
 *     their Fenwick versions): rational arithmetic with {@link BigFraction}; the
 *     message becomes the simplest fraction in its final interval, returned as
 *     a numerator and denominator.</li>
 * <li><b>Fast</b> ({@link #getIntervalValueFast}, {@link #getArithmeticValuesFast}):
 *     the same static model, counted down as each byte is coded, with a 40-bit
 *     integer range coder instead of fractions.</li>
 * <li><b>Adaptive</b> ({@link #getIntervalValueAdaptive},
 *     {@link #getArithmeticValuesAdaptive}): no stored table; counts are learned
 *     as bytes are coded, in contexts chosen by the previous byte.</li>
 * <li><b>Context</b> ({@link #getIntervalValueContext},
 *     {@link #getArithmeticValuesContext}): adaptive counts over any alphabet,
 *     with each symbol's context chosen by the caller.</li>
 * </ul>
 * It also has helpers the writers and readers share: splitting data into
 * blocks, and storing frequency tables.
 * <p>
 * The order-table coders ({@code getAscendingTable}, {@code getRandomOrderTable},
 * the order-table overloads of {@code getIntervalValue}/{@code getArithmeticValues},
 * etc.) live in {@code SteeredArithmeticMapper}.
 * <p>
 * Version 4.0.
 */
public class ArithmeticMapper
{
	/** Not used: all of this class's methods are static. */
	public ArithmeticMapper() {}

	/**
	 * Exact rational arithmetic. A minimal local copy of FractionMapper's
	 * BigFraction with only what this file uses, so this file doesn't
	 * depend on FractionMapper.java.
	 */
	public static final class BigFraction
	{
		/**
		 * The numerator and denominator. Invariant: d &gt; 0, gcd(|n|, d) == 1
		 * (or n == 0, d == 1).
		 */
		public final BigInteger n, d; // invariant: d > 0, gcd(|n|,d) == 1 (or n==0, d==1)

		/**
		 * A fraction, reduced to lowest terms with a positive denominator.
		 *
		 * @param numerator   the numerator
		 * @param denominator the denominator (should not be zero; only 0/0 is
		 *                    checked)
		 * @throws ArithmeticException if both are zero
		 */
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

		/**
		 * The fraction n/d.
		 *
		 * @param n the numerator
		 * @param d the denominator
		 * @return n/d in lowest terms
		 */
		public static BigFraction of(long n, long d) { return new BigFraction(BigInteger.valueOf(n), BigInteger.valueOf(d)); }
		/**
		 * The fraction 0/1.
		 */
		public static final BigFraction ZERO = BigFraction.of(0, 1);
		/**
		 * The fraction 1/1.
		 */
		public static final BigFraction ONE  = BigFraction.of(1, 1);

		/**
		 * Operand size (in bits) above which parallelMultiply is used. Only helps on
		 * multi-core machines; see FractionMapper for details.
		 */
		private static final int PARALLEL_MULTIPLY_THRESHOLD_BITS = 4000;

		/**
		 * {@code BigInteger.parallelMultiply} (Java 19+), or null on older Java.
		 */
		private static final java.lang.invoke.MethodHandle PARALLEL_MULTIPLY = findParallelMultiply();

		/**
		 * Looks up {@code BigInteger.parallelMultiply}.
		 *
		 * @return a handle to it, or null if this Java doesn't have it
		 */
		private static java.lang.invoke.MethodHandle findParallelMultiply()
		{
			try { return java.lang.invoke.MethodHandles.publicLookup().findVirtual(BigInteger.class, "parallelMultiply",
				java.lang.invoke.MethodType.methodType(BigInteger.class, BigInteger.class)); }
			catch (Throwable t) { return null; }
		}

		/**
		 * a * b, using parallelMultiply when both operands are large and it is
		 * available.
		 *
		 * @param a a factor
		 * @param b a factor
		 * @return the product
		 */
		private static BigInteger smartMultiply(BigInteger a, BigInteger b)
		{
			if (a.bitLength() >= PARALLEL_MULTIPLY_THRESHOLD_BITS && b.bitLength() >= PARALLEL_MULTIPLY_THRESHOLD_BITS)
			{
				// BigInteger.parallelMultiply is Java 19+; on older Java this is a plain multiply.
				if (PARALLEL_MULTIPLY != null)
					try { return (BigInteger) PARALLEL_MULTIPLY.invokeExact(a, b); }
					catch (Throwable t) { throw new RuntimeException(t); }
			}
			return a.multiply(b);
		}

		/**
		 * Adds.
		 *
		 * @param fraction the addend
		 * @return this + fraction
		 */
		public BigFraction add(BigFraction fraction)      { return new BigFraction(smartMultiply(n, fraction.d).add(smartMultiply(fraction.n, d)), smartMultiply(d, fraction.d)); }
		/**
		 * Subtracts.
		 *
		 * @param fraction the subtrahend
		 * @return this - fraction
		 */
		public BigFraction subtract(BigFraction fraction) { return new BigFraction(smartMultiply(n, fraction.d).subtract(smartMultiply(fraction.n, d)), smartMultiply(d, fraction.d)); }
		/**
		 * Multiplies.
		 *
		 * @param fraction the multiplier
		 * @return this * fraction
		 */
		public BigFraction multiply(BigFraction fraction) { return new BigFraction(smartMultiply(n, fraction.n), smartMultiply(d, fraction.d)); }
		/**
		 * Divides.
		 *
		 * @param fraction the divisor (not zero)
		 * @return this / fraction
		 */
		public BigFraction divide(BigFraction fraction)   { return new BigFraction(smartMultiply(n, fraction.d), smartMultiply(d, fraction.n)); }
		/**
		 * Multiplies by an integer.
		 *
		 * @param k the multiplier
		 * @return this * k
		 */
		public BigFraction multiply(long k)               { return new BigFraction(smartMultiply(n, BigInteger.valueOf(k)), d); }

		/**
		 * Compares this with another fraction.
		 *
		 * @param fraction the fraction to compare with
		 * @return negative, zero or positive as this is less than, equal to or
		 *         greater than fraction
		 */
		public int compareTo(BigFraction fraction) { return smartMultiply(n, fraction.d).compareTo(smartMultiply(fraction.n, d)); }
		/**
		 * Less than or equal.
		 *
		 * @param fraction the fraction to compare with
		 * @return whether this &lt;= fraction
		 */
		public boolean le(BigFraction fraction) { return compareTo(fraction) <= 0; }
		/**
		 * Greater than.
		 *
		 * @param fraction the fraction to compare with
		 * @return whether this &gt; fraction
		 */
		public boolean gt(BigFraction fraction) { return compareTo(fraction) > 0; }

		/**
		 * The fraction as text.
		 *
		 * @return the fraction as "n/d"
		 */
		@Override public String toString() { return n + "/" + d; }
	}


	/**
	 * The fraction with the smallest denominator in the half-open interval
	 * [lo, hi), found from the continued fractions of the endpoints. lo and hi
	 * need not share a denominator. This is how the exact coders pick the
	 * shortest value that identifies the message.
	 *
	 * @param loN numerator of lo
	 * @param loD denominator of lo (positive)
	 * @param hiN numerator of hi
	 * @param hiD denominator of hi (positive)
	 * @return {numerator, denominator} of the simplest fraction, in lowest terms
	 */
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

	/**
	 * Division rounding toward negative infinity.
	 *
	 * @param n the dividend
	 * @param d the divisor (positive)
	 * @return floor(n / d)
	 */
	private static BigInteger floorDiv(BigInteger n, BigInteger d)
	{
		BigInteger[] qr = n.divideAndRemainder(d);
		if (qr[1].signum() != 0 && n.signum() < 0)
			return qr[0].subtract(BigInteger.ONE);
		return qr[0];
	}

	/**
	 * Arithmetic codes src with exact rational arithmetic. The model is the byte
	 * counts given, counted down as each byte is coded (so the last bytes cost
	 * less); the result is the simplest fraction (smallest denominator) in the
	 * final interval.
	 *
	 * @param src       the bytes to code
	 * @param frequency the count of each byte value (256 entries) in src
	 * @return {numerator, denominator} of the coded value
	 * @see #getArithmeticValues
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

	/**
	 * Decodes a value from {@link #getIntervalValue}, finding each byte's
	 * interval with a binary search over the values still in use.
	 *
	 * @param v         {numerator, denominator} of the coded value
	 * @param frequency the count of each byte value (256 entries), as given to
	 *                  the encoder
	 * @param n         the number of bytes coded
	 * @return the decoded bytes
	 */
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

	/**
	 * A Fenwick (binary indexed) tree over 256 counts, for running totals in
	 * O(log 256).
	 *
	 * @param frequency the counts (256 entries)
	 * @return the tree, 257 entries (index 0 unused)
	 */
	private static int[] fenwickBuild(int[] frequency)
	{
		int[] bit = new int[257];
		for (int i = 0; i < 256; i++)
			if (frequency[i] > 0) fenwickUpdate(bit, i, frequency[i]);
		return bit;
	}

	/**
	 * Adds delta to count i.
	 *
	 * @param bit   the tree
	 * @param i     the byte value
	 * @param delta the change
	 */
	private static void fenwickUpdate(int[] bit, int i, int delta)
	{
		for (i += 1; i <= 256; i += i & -i) bit[i] += delta;
	}

	/**
	 * A running total.
	 *
	 * @param bit the tree
	 * @param i   the byte value
	 * @return the total of counts 0..i
	 */
	private static int fenwickQuery(int[] bit, int i)
	{
		int sum = 0;
		for (i += 1; i > 0; i -= i & -i) sum += bit[i];
		return sum;
	}

	/**
	 * The byte value whose cumulative interval holds target.
	 *
	 * @param bit    the tree
	 * @param target a position, 0 &lt;= target &lt; total
	 * @return the first value whose running total passes target
	 */
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
	/**
	 * The same coder as {@link #getIntervalValue}, with running totals kept in a
	 * Fenwick tree (O(log 256) per byte instead of O(256)).
	 *
	 * @param src       the bytes to code
	 * @param frequency the count of each byte value (256 entries) in src
	 * @return {numerator, denominator} of the coded value
	 * @see #getArithmeticValuesFenwick
	 */
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
	/**
	 * The same decoder as {@link #getArithmeticValues}, finding each byte by a
	 * Fenwick-tree search (O(log 256) per byte).
	 *
	 * @param v         {numerator, denominator} of the coded value
	 * @param frequency the count of each byte value (256 entries), as given to
	 *                  the encoder
	 * @param n         the number of bytes coded
	 * @return the decoded bytes
	 */
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

	/**
	 * Fast arithmetic coder (no BigInteger), using the range coder.
	 * <p>
	 * The counts start as the exact byte counts and each byte is counted down as
	 * it is coded. Rounding the interval to a multiple of total/range costs a
	 * small fraction of a bit per thousand bytes.
	 * <p>
	 * Output: the coded bytes, with no length header. The caller stores the
	 * number of bytes coded, n; the decoder reads zeros past the end.
	 * <p>
	 * Limits: the total count (bytes in the block) must stay under 2<sup>30</sup>.
	 * The rounding loss stays under about 0.01% for totals below 2<sup>24</sup>.
	 * <p>
	 * The same coder as {@link #getIntervalValueFastFenwick}; both names are kept
	 * for callers.
	 *
	 * @param src       the bytes to code
	 * @param frequency the count of each byte value (256 entries) in src
	 * @return the coded bytes
	 * @see #getArithmeticValuesFast
	 */
	public static byte[] getIntervalValueFast(byte[] src, int[] frequency)
	{
		return getIntervalValueFastFenwick(src, frequency);
	}

	/**
	 * Decodes {@link #getIntervalValueFast}'s output. The same decoder as
	 * {@link #getArithmeticValuesFastFenwick}.
	 *
	 * @param encoded   the coded bytes
	 * @param frequency the count of each byte value (256 entries), as given to
	 *                  the encoder
	 * @param n         the number of bytes coded
	 * @return the decoded bytes
	 */
	public static byte[] getArithmeticValuesFast(byte[] encoded, int[] frequency, int n)
	{
		return getArithmeticValuesFastFenwick(encoded, frequency, n);
	}

	/**
	 * The fast coder (see {@link #getIntervalValueFast}), with running totals in
	 * a Fenwick tree.
	 *
	 * @param src       the bytes to code
	 * @param frequency the count of each byte value (256 entries) in src
	 * @return the coded bytes
	 */
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

	/**
	 * Decodes {@link #getIntervalValueFastFenwick}'s output.
	 *
	 * @param encoded   the coded bytes
	 * @param frequency the count of each byte value (256 entries), as given to
	 *                  the encoder
	 * @param n         the number of bytes coded
	 * @return the decoded bytes
	 */
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

	/**
	 * The range coder's range starts just below 2<sup>40</sup>.
	 */
	private static final long RANGE_TOP    = 1L << 40;
	/**
	 * The range coder shifts out a byte when the range drops below 2<sup>32</sup>.
	 */
	private static final long RANGE_BOTTOM = 1L << 32;

	/**
	 * Byte-oriented range encoder, with LZMA-style carry propagation.
	 * <p>
	 * low holds the bottom of the interval in its low 40 bits (plus a possible
	 * carry into bit 40); range stays between 2<sup>32</sup> and 2<sup>40</sup>,
	 * shifting out a byte when it drops below 2<sup>32</sup>. The top byte of low
	 * is held in {@code cache} until it can no longer change, along with a count
	 * of following 0xFF bytes that a carry would also change.
	 */
	static final class RangeEncoder
	{
		/**
		 * The bytes written so far (grown as needed).
		 */
		byte[] out;
		/**
		 * The number of bytes in out.
		 */
		int    size = 0;
		/**
		 * The bottom of the interval, and its width.
		 */
		long   low = 0, range = RANGE_TOP - 1;
		/**
		 * The byte held back until a carry can no longer change it.
		 */
		int    cache = 0;
		/**
		 * The held byte plus the 0xFF bytes after it.
		 */
		long   cache_size = 1;

		/**
		 * An encoder with an empty interval [0, 2<sup>40</sup>).
		 *
		 * @param n the expected output size in bytes (a hint; the buffer grows)
		 */
		RangeEncoder(int n) { out = new byte[Math.max(16, n + n / 4 + 16)]; }

		/**
		 * Narrows the interval to [start, start + count) out of total.
		 *
		 * @param start the total count of the symbols below this one
		 * @param count this symbol's count (positive)
		 * @param total the total count (under 2<sup>30</sup>)
		 */
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

		/**
		 * Shifts the top byte of low out (into the cache, or the output once it can
		 * no longer change).
		 */
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

		/**
		 * Appends a byte to the output, growing the buffer as needed.
		 *
		 * @param b the byte to write
		 */
		void put(int b)
		{
			if (size == out.length) out = Arrays.copyOf(out, out.length * 2);
			out[size++] = (byte) b;
		}

		/**
		 * Flushes the interval.
		 *
		 * @return the coded bytes
		 */
		byte[] finish()
		{
			for (int k = 0; k < 6; k++) shiftLow();
			return Arrays.copyOf(out, size);
		}
	}

	/**
	 * The decoder for {@link RangeEncoder}. Reads zeros past the end of its input.
	 */
	static final class RangeDecoder
	{
		/**
		 * The coded bytes.
		 */
		final byte[] in;
		/**
		 * The next byte to read; the first byte written is always the initial cache.
		 */
		int  pos = 1;             // the first byte written is always the initial cache
		/**
		 * The code's offset within the interval, the interval's width, and range/total from the last {@link #target}.
		 */
		long code = 0, range = RANGE_TOP - 1, r;

		/**
		 * A decoder reading the first five code bytes.
		 *
		 * @param in the coded bytes
		 */
		RangeDecoder(byte[] in)
		{
			this.in = in;
			for (int k = 0; k < 5; k++) code = (code << 8) | next();
		}

		/**
		 * The next input byte.
		 *
		 * @return the next input byte, or 0 past the end
		 */
		int next() { return (pos < in.length) ? (in[pos++] & 0xFF) : 0; }

		/**
		 * Where the code falls, as a count out of total. Must be followed by
		 * {@link #decode} with the interval that holds it.
		 *
		 * @param total the total count
		 * @return the position, 0 to total - 1
		 */
		int target(long total)
		{
			r = range / total;
			long t = code / r;
			return (int) Math.min(t, total - 1);
		}

		/**
		 * Narrows the interval to the symbol found.
		 *
		 * @param start the total count of the symbols below it
		 * @param count its count
		 */
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

	/**
	 * The adaptive coder's contexts: 2<sup>ADAPTIVE_CONTEXT_BITS</sup> of them,
	 * chosen by the top bits of the previous byte.
	 */
	public static final int ADAPTIVE_CONTEXT_BITS = 4;
	/**
	 * What each coded byte adds to its count in the adaptive coder.
	 */
	public static final int ADAPTIVE_INCREMENT    = 2;
	/**
	 * A context's total above which the adaptive coder halves its counts.
	 */
	public static final int ADAPTIVE_LIMIT        = 1 << 22;

	/**
	 * Adaptive arithmetic coder (context-modeled, no stored table).
	 * <p>
	 * Uses the range coder, but learns the counts as it goes (the decoder learns
	 * them the same way), so no table is stored. Counts are kept per context:
	 * 2<sup>{@link #ADAPTIVE_CONTEXT_BITS}</sup> contexts, chosen by the top bits
	 * of the previous byte. Every count starts at 1; each coded byte adds
	 * {@link #ADAPTIVE_INCREMENT}. If a context's total passes
	 * {@link #ADAPTIVE_LIMIT} its counts are halved (staying &gt;= 1), keeping
	 * totals well inside the coder's precision.
	 * <p>
	 * Tested best on the sample images: 16 contexts (top 4 bits), increment 2.
	 * 256 contexts lost on small images (too little data to learn them); larger
	 * increments overweighted the first bytes in each context.
	 * <p>
	 * Output: the coded bytes, with no length header. The caller stores the
	 * number of bytes coded, n; the decoder reads zeros past the end.
	 *
	 * @param src the bytes to code
	 * @return the coded bytes
	 * @see #getArithmeticValuesAdaptive(byte[], int)
	 */
	public static byte[] getIntervalValueAdaptive(byte[] src)
	{
		return getIntervalValueAdaptive(src, ADAPTIVE_CONTEXT_BITS, ADAPTIVE_INCREMENT, ADAPTIVE_LIMIT);
	}

	/**
	 * Decodes {@link #getIntervalValueAdaptive(byte[])}'s output.
	 *
	 * @param encoded the coded bytes
	 * @param n       the number of bytes coded
	 * @return the decoded bytes
	 */
	public static byte[] getArithmeticValuesAdaptive(byte[] encoded, int n)
	{
		return getArithmeticValuesAdaptive(encoded, n, ADAPTIVE_CONTEXT_BITS, ADAPTIVE_INCREMENT, ADAPTIVE_LIMIT);
	}

	/**
	 * One context's counts over the 256 byte values, with a Fenwick tree over
	 * them for the running totals.
	 */
	private static final class AdaptiveModel
	{
		/**
		 * Each byte value's count.
		 */
		final int[] f   = new int[256];
		/**
		 * Fenwick tree over f (index 0 unused).
		 */
		final int[] bit = new int[257];
		/**
		 * The total of f.
		 */
		int total;
		/**
		 * What each coded byte adds, and the total that triggers halving.
		 */
		final int increment, limit;

		/**
		 * A model with every count at 1.
		 *
		 * @param increment what each coded byte adds to its count
		 * @param limit     the total above which the counts are halved
		 */
		AdaptiveModel(int increment, int limit)
		{
			this.increment = increment;
			this.limit     = limit;
			Arrays.fill(f, 1);
			rebuild();
		}

		/**
		 * Rebuilds the tree and total from f.
		 */
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

		/**
		 * A running total.
		 *
		 * @param s a byte value
		 * @return the total count of the byte values below s
		 */
		int start(int s)
		{
			int sum = 0;
			for (int i = s; i > 0; i -= i & -i) sum += bit[i];
			return sum;
		}

		/**
		 * Finds a byte value by position.
		 *
		 * @param target a position, 0 &lt;= target &lt; total
		 * @return the byte value whose interval holds target
		 */
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

		/**
		 * Counts one more s, halving the counts if the total passes the limit.
		 *
		 * @param s the byte value just coded
		 */
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

	/**
	 * The adaptive coder's models.
	 *
	 * @param context_bits log2 of the number of contexts
	 * @param increment    what each coded byte adds to its count
	 * @param limit        the total above which a context's counts are halved
	 * @return a fresh model for each context
	 */
	private static AdaptiveModel[] adaptiveModels(int context_bits, int increment, int limit)
	{
		AdaptiveModel[] model = new AdaptiveModel[1 << context_bits];
		for (int k = 0; k < model.length; k++) model[k] = new AdaptiveModel(increment, limit);
		return model;
	}

	/**
	 * The adaptive coder (see {@link #getIntervalValueAdaptive(byte[])}) with its
	 * settings given.
	 *
	 * @param src          the bytes to code
	 * @param context_bits log2 of the number of contexts (0 to 8)
	 * @param increment    what each coded byte adds to its count
	 * @param limit        the total above which a context's counts are halved
	 * @return the coded bytes
	 */
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

	/**
	 * Decodes {@link #getIntervalValueAdaptive(byte[], int, int, int)}'s output;
	 * the settings must match the encoder's.
	 *
	 * @param encoded      the coded bytes
	 * @param n            the number of bytes coded
	 * @param context_bits log2 of the number of contexts
	 * @param increment    what each coded byte adds to its count
	 * @param limit        the total above which a context's counts are halved
	 * @return the decoded bytes
	 */
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

	/**
	 * What each coded symbol adds to its count in the context-adaptive coder.
	 */
	public static final int CONTEXT_INCREMENT = 32;
	/**
	 * A context's total above which the context-adaptive coder halves its counts.
	 */
	public static final int CONTEXT_LIMIT     = 1 << 16;

	/**
	 * Supplies the decoder with each symbol's context as it goes.
	 * <p>
	 * The decoder can't be handed the contexts up front -- they usually depend on
	 * symbols not yet decoded -- so it asks for each one in turn.
	 */
	public interface ContextFunction
	{
		/**
		 * The context of symbol k.
		 *
		 * @param k      the symbol's index
		 * @param symbol the symbols; symbol[0..k-1] are decoded
		 * @return its context, 0 to n_contexts - 1
		 */
		int context(int k, int[] symbol);
	}

	/**
	 * Context-adaptive coder: like the adaptive coder, but over any alphabet
	 * (n_symbols) and with the context of each symbol chosen by the caller rather
	 * than by the previous byte. Each context has its own counts, starting at 1,
	 * +{@link #CONTEXT_INCREMENT} per symbol coded, halved (keeping them &gt;= 1)
	 * when a context's total passes {@link #CONTEXT_LIMIT}.
	 * <p>
	 * Output: the range coder's bytes; the caller stores the symbol count.
	 *
	 * @param symbol     the symbols, each 0 to n_symbols - 1
	 * @param n_symbols  the alphabet size
	 * @param context    each symbol's context, 0 to n_contexts - 1
	 * @param n_contexts the number of contexts
	 * @return the coded bytes
	 * @see #getArithmeticValuesContext
	 */
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

	/**
	 * Decodes {@link #getIntervalValueContext}'s output.
	 *
	 * @param coded      the coded bytes
	 * @param n          the number of symbols coded
	 * @param n_symbols  the alphabet size
	 * @param n_contexts the number of contexts
	 * @param function   gives each symbol's context from the symbols decoded
	 *                   before it (it must return what the encoder was given)
	 * @return the decoded symbols
	 */
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

	/**
	 * The context coder's models.
	 *
	 * @param n_contexts the number of contexts
	 * @param n_symbols  the alphabet size
	 * @return a fresh model for each context
	 */
	private static ContextModel[] contextModels(int n_contexts, int n_symbols)
	{
		ContextModel[] model = new ContextModel[n_contexts];
		for(int c = 0; c < n_contexts; c++) model[c] = new ContextModel(n_symbols);
		return model;
	}

	/**
	 * One context's counts over n symbols, with a Fenwick tree for the running
	 * totals.
	 */
	private static final class ContextModel
	{
		/**
		 * Each symbol's count, and the Fenwick tree over them (index 0 unused).
		 */
		final int[] f, bit;
		/**
		 * The alphabet size, and its highest one bit (for the tree search).
		 */
		final int   n, top;
		/**
		 * The total of f.
		 */
		int total;

		/**
		 * A model with every count at 1.
		 *
		 * @param n the alphabet size
		 */
		ContextModel(int n)
		{
			this.n = n;
			f   = new int[n];
			bit = new int[n + 1];
			top = Integer.highestOneBit(n);
			Arrays.fill(f, 1);
			rebuild();
		}

		/**
		 * Rebuilds the tree and total from f.
		 */
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

		/**
		 * A running total.
		 *
		 * @param s a symbol
		 * @return the total count of the symbols below s
		 */
		int start(int s)
		{
			int sum = 0;
			for(int i = s; i > 0; i -= i & -i) sum += bit[i];
			return sum;
		}

		/**
		 * Finds a symbol by position.
		 *
		 * @param target a position, 0 &lt;= target &lt; total
		 * @return the symbol whose interval holds target
		 */
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

		/**
		 * Counts one more s (+{@link ArithmeticMapper#CONTEXT_INCREMENT}), halving
		 * the counts if the total passes {@link ArithmeticMapper#CONTEXT_LIMIT}.
		 *
		 * @param s the symbol just coded
		 */
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

	/**
	 * Splits src into n blocks of length/n bytes, the last one taking the
	 * remainder.
	 *
	 * @param src the bytes to split
	 * @param n   the number of blocks
	 * @return the blocks
	 * @see #joinBlocks
	 */
	public static byte[][] getBlocks(byte[] src, int n)
	{
		int[]    length = getBlockLengths(src.length, n);
		byte[][] block  = new byte[n][];
		int      pos    = 0;
		for(int k = 0; k < n; k++) { block[k] = Arrays.copyOfRange(src, pos, pos + length[k]); pos += length[k]; }
		return block;
	}

	/**
	 * The block lengths {@link #getBlocks} uses.
	 *
	 * @param length the total length
	 * @param n      the number of blocks
	 * @return n lengths: length/n each, the last one taking the remainder
	 */
	public static int[] getBlockLengths(int length, int n)
	{
		int[] block_length = new int[n];
		Arrays.fill(block_length, length / n);
		block_length[n - 1] += length % n;
		return block_length;
	}

	/**
	 * Concatenates blocks (the inverse of {@link #getBlocks}).
	 *
	 * @param block the blocks
	 * @return their bytes, in order
	 */
	public static byte[] joinBlocks(byte[][] block)
	{
		int length = 0;
		for(byte[] b : block) length += b.length;
		byte[] dst = new byte[length];
		int pos = 0;
		for(byte[] b : block) { System.arraycopy(b, 0, dst, pos, b.length); pos += b.length; }
		return dst;
	}

	/**
	 * Byte counts.
	 *
	 * @param src the bytes to count
	 * @return the count of each byte value (256 entries)
	 */
	public static int[] getFrequency(byte[] src)
	{
		int[] frequency = new int[256];
		for(byte b : src) frequency[b & 0xFF]++;
		return frequency;
	}

	/**
	 * How wide stored frequency tables need to be. Frequency tables are stored as
	 * n*256 counts, little-endian, 1, 2 or 4 bytes each (type 0, 1, 2: the
	 * smallest that holds the largest count), then Deflated.
	 *
	 * @param frequency the tables
	 * @return 0, 1 or 2 for 1-, 2- or 4-byte counts
	 */
	public static int getFrequencyType(int[][] frequency)
	{
		int max = 0;
		for(int[] row : frequency) for(int v : row) if(v > max) max = v;
		return (max <= 255) ? 0 : (max <= 65535) ? 1 : 2;
	}

	/**
	 * Stores frequency tables at the given width, Deflated.
	 *
	 * @param frequency the tables (256 counts each)
	 * @param type      the width, from {@link #getFrequencyType}
	 * @param level     the Deflate level (0 to 9)
	 * @return the Deflated bytes
	 * @see #inflateFrequencies
	 */
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

	/**
	 * Reads frequency tables stored by {@link #deflateFrequencies}.
	 *
	 * @param zipped the Deflated bytes
	 * @param n      the number of tables
	 * @param type   the width they were stored at
	 * @return the tables (256 counts each)
	 * @throws java.util.zip.DataFormatException if the data is not valid Deflate
	 *         or is too short
	 */
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

	/**
	 * A set of tables as the writers store them: int n, int type, int Deflated
	 * length, Deflated bytes.
	 *
	 * @param frequency the tables (256 counts each)
	 * @param level     the Deflate level (0 to 9)
	 * @return the packed bytes
	 * @see #readFrequencies
	 */
	public static byte[] packFrequencies(int[][] frequency, int level)
	{
		int    type   = getFrequencyType(frequency);
		byte[] zipped = deflateFrequencies(frequency, type, level);
		java.nio.ByteBuffer buffer = java.nio.ByteBuffer.allocate(12 + zipped.length);
		buffer.putInt(frequency.length).putInt(type).putInt(zipped.length).put(zipped);
		return buffer.array();
	}

	/**
	 * Reads tables stored by {@link #packFrequencies}.
	 *
	 * @param in the stream
	 * @return the tables (256 counts each)
	 * @throws java.io.IOException if the stream can't be read
	 * @throws java.util.zip.DataFormatException if the tables are not valid
	 *         Deflate
	 */
	public static int[][] readFrequencies(java.io.DataInputStream in) throws java.io.IOException, java.util.zip.DataFormatException
	{
		int    n      = in.readInt();
		int    type   = in.readInt();
		byte[] zipped = new byte[in.readInt()];
		in.readFully(zipped);
		return inflateFrequencies(zipped, n, type);
	}
}
