import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.BitSet;

/**
 * Exact rational arithmetic (BigFraction) plus conversion to and from
 * repeating-decimal digit strings.
 */
public class FractionMapper
{
	public static final class BigFraction implements Comparable<BigFraction>
	{
		public final BigInteger n, d; // invariant: d > 0, gcd(|n|,d) == 1 (or n==0, d==1)

		public BigFraction(BigInteger numerator, BigInteger denominator)
		{
			// 0/0 is indeterminate, not just "zero with an unusual
			// denominator" -- and this single check also catches
			// infinity-infinity, 0*infinity, and infinity/infinity for
			// free, since add/subtract/multiply/divide all reduce those
			// three forms to this exact same raw (0,0) pair before this
			// constructor ever sees it (verified directly: e.g.
			// (1/0).subtract(1/0) computes n = 1*0 - 1*0 = 0, d = 0*0 = 0).
			if (numerator.signum() == 0 && denominator.signum() == 0)
				throw new ArithmeticException("0/0 is an indeterminate form");

			if (denominator.signum() < 0)
			{
				// This moves the negative sign to the numerator if the numerator was positive,
				// and makes it a simple positive fraction if the numerator was negative.
				numerator   = numerator.negate();
				denominator = denominator.negate();
			}

			if (numerator.signum() == 0)
			{
				// No matter what denominator was passed to the constructor,
				// we turn it into a one if the numerator was zero.
				denominator = BigInteger.ONE;
			}
			else
			{
				// gcd() is order-independent regardless of which argument
				// is bigger, so this works whether or not this is a
				// "proper" fraction (numerator < denominator).
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
		public static final BigFraction HALF = BigFraction.of(1, 2);

		// Threshold (in bits) above which we ask the JDK to attempt a
		// parallelMultiply instead of a plain sequential multiply.
		// parallelMultiply itself already has its own internal fallback
		// to plain multiply for small operands (see its javadoc: "For
		// smaller integers parallelMultiply computes the result in the
		// calling thread as if by calling multiply") -- this is a
		// coarser, cheaper pre-check in front of that, so clearly-small
		// operands (the overwhelming majority of calls in this codebase)
		// skip even the cost of asking parallelMultiply to make that
		// determination itself. Only benefits machines with more than
		// one core -- on a single-core machine parallelMultiply has
		// nothing to parallelize onto and this threshold is moot. The
		// right value is hardware- and workload-dependent; this default
		// is a starting point, not a measured optimum -- tune it against
		// real multi-core hardware if this path matters for performance.
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
		public BigFraction negate()                       { return new BigFraction(n.negate(), d); }
		public BigFraction abs()                          { return n.signum() < 0 ? negate() : this; }

		/** True for both +infinity (1/0) and -infinity (-1/0). */
		public boolean isInfinite() { return d.signum() == 0; }

		@Override
		public int compareTo(BigFraction fraction)
		{
			// Plain cross-multiplication (n*fraction.d vs fraction.n*d) breaks
			// once either side is infinite, since anything times a zero
			// denominator is 0 -- e.g. comparing 1/0 to -1/0 would compute
			// 1*0=0 vs -1*0=0 and wrongly call them equal. Handle infinite
			// operands explicitly first.
			boolean thisInfinite = isInfinite(), otherInfinite = fraction.isInfinite();
			if (thisInfinite || otherInfinite)
			{
				if (thisInfinite && otherInfinite) return Integer.compare(n.signum(), fraction.n.signum());
				if (thisInfinite) return n.signum();          // +infinity > any finite, -infinity < any finite
				return -fraction.n.signum();
			}
			return smartMultiply(n, fraction.d).compareTo(smartMultiply(fraction.n, d));
		}

		public boolean lt(BigFraction fraction) { return compareTo(fraction) < 0; }
		public boolean le(BigFraction fraction) { return compareTo(fraction) <= 0; }
		public boolean gt(BigFraction fraction) { return compareTo(fraction) > 0; }
		public boolean eq(BigFraction fraction) { return this.equals(fraction); }

		@Override
		public boolean equals(Object other)
		{
			// A plain field comparison is correct here (rather than the usual
			// cross-multiplication needed for fraction equality, e.g. checking
			// n1*d2 == n2*d1) because the constructor already normalizes every
			// instance to one canonical (n, d) pair per value -- reduced via
			// GCD, denominator always positive, zero and the 1/0, -1/0
			// "infinities" each collapsing to a single fixed representation.
			// Since no value has more than one valid field-pair, matching
			// fields is equivalent to matching value. This depends on that
			// normalization invariant staying intact if the constructor ever
			// changes.
			if (this == other) return true;
			if (!(other instanceof BigFraction)) return false;
			BigFraction fraction = (BigFraction) other;
			return n.equals(fraction.n) && d.equals(fraction.d);
		}

		@Override
		public int hashCode() { return java.util.Objects.hash(n, d); }

		@Override public String toString() { return n + "/" + d; }
		public double toDouble() { return new java.math.BigDecimal(n).divide(new java.math.BigDecimal(d), 40, java.math.RoundingMode.HALF_EVEN).doubleValue(); }
	}

	/**
	 * Computes the decimal expansion of a/b's FRACTIONAL PART -- any
	 * integer part is dropped (via a % b up front), so an improper
	 * fraction like 5/4 correctly reduces to generating the digits of
	 * 1/4's remainder, rather than corrupting a multi-digit intermediate
	 * quotient into a single decimal-digit slot. Sign is discarded (both
	 * a and b are treated as magnitudes) -- callers wanting a signed
	 * result should track the sign themselves.
	 *
	 * @return a 2-element ArrayList: [0] = static (non-repeating) digits,
	 *         [1] = repeating digits. Either or both may be "" -- both
	 *         empty means a/b is a whole number: no fractional part at
	 *         all (e.g. 3/1 or 8/4). Only [1] empty means the decimal
	 *         terminates (e.g. "125", "" for 1/8 = 0.125). Neither empty
	 *         means a mixed case (e.g. "1", "6" for 1/6 = 0.1(6)).
	 * @throws ArithmeticException if b == 0
	 */
	public static ArrayList<String> getDecimalDigits(int a, int b)
	{
		if (b == 0)
			throw new ArithmeticException("division by zero");

		a = Math.abs(a);
		b = Math.abs(b);

		int remainder = a % b;

		ArrayList<String> result = new ArrayList<>();
		if (remainder == 0)
		{
			result.add("");
			result.add("");
			return result;
		}

		StringBuilder digits = new StringBuilder();
		// maps a remainder value to the digit-position at which it was
		// first seen, so we can find exactly where the cycle starts
		// once (if) a remainder repeats.
		HashMap<Integer, Integer> seenAt = new HashMap<>();

		while (remainder != 0 && !seenAt.containsKey(remainder))
		{
			seenAt.put(remainder, digits.length());
			remainder *= 10;
			int digit = remainder / b;
			digits.append((char) ('0' + digit));
			remainder = remainder % b;
		}

		String staticDigits, repeatingDigits;
		if (remainder == 0)
		{
			staticDigits = digits.toString();
			repeatingDigits = "";
		}
		else
		{
			int cycleStart = seenAt.get(remainder);
			staticDigits = digits.substring(0, cycleStart);
			repeatingDigits = digits.substring(cycleStart);
		}

		result.add(staticDigits);
		result.add(repeatingDigits);
		return result;
	}

	/**
	 * Inverse of getDecimalDigits: reconstructs the fractional-part value
	 * 0.staticDigits(repeatingDigits repeating) as an exact BigFraction.
	 *
	 * Two genuinely different formulas depending on whether there's a
	 * repeating part at all -- these don't unify into one, since setting
	 * r=0 in the repeating-case formula would divide by (10^0 - 1) = 0:
	 *
	 *   No repeating part (r=0): value = S / 10^s
	 *     (s=0 too means both empty -- a whole number, value 0)
	 *
	 *   Has a repeating part (r>0): value = (S*(10^r - 1) + R) / (10^s * (10^r - 1))
	 *     derived from: 0.S(R) = S/10^s + R/(10^s*(10^r-1))
	 *     e.g. 1/6 = 0.1(6): S=1,s=1,R=6,r=1 -> (1*9+6)/(10*9) = 15/90 = 1/6.
	 *
	 * Leading zeros in either string are preserved correctly since length
	 * comes from String.length(), not from the parsed integer value (so
	 * "05" correctly contributes length 2, not 1).
	 */
	public static BigFraction getRationalNumber(String staticDigits, String repeatingDigits)
	{
		int s = staticDigits.length();
		int r = repeatingDigits.length();
		BigInteger S = staticDigits.isEmpty() ? BigInteger.ZERO : new BigInteger(staticDigits);

		if (r == 0)
		{
			BigInteger denominator = BigInteger.TEN.pow(s);
			return new BigFraction(S, denominator);
		}
		else
		{
			BigInteger R = new BigInteger(repeatingDigits);
			BigInteger nines = BigInteger.TEN.pow(r).subtract(BigInteger.ONE);
			BigInteger numerator = S.multiply(nines).add(R);
			BigInteger denominator = BigInteger.TEN.pow(s).multiply(nines);
			return new BigFraction(numerator, denominator);
		}
	}

	/** A digit block paired with its explicit length -- needed because a
	 *  plain BitSet can't distinguish "100" from "1" (both have the same
	 *  highest set bit), silently losing trailing zeros otherwise. */
	public static final class BinaryDigits
	{
		public final BitSet bits;
		public final int length;
		public BinaryDigits(BitSet bits, int length) { this.bits = bits; this.length = length; }
	}

	/** Binary analogue of getDecimalDigits: computes the binary expansion
	 *  of a/b's FRACTIONAL PART (integer part and sign dropped, same as
	 *  the decimal version) as a static block and, if the expansion
	 *  doesn't terminate, a repeating block. Bit 0 of each BitSet is the
	 *  FIRST (most significant) digit of that block, working left to
	 *  right -- the reverse of BigInteger's own bit numbering.
	 *
	 *  @return a 2-element array: [0] = static digits, [1] = repeating
	 *          digits (length 0 for either means that block is empty,
	 *          exactly as with getDecimalDigits's empty strings).
	 *  @throws ArithmeticException if b == 0
	 */
	public static BinaryDigits[] getBinaryDigits(BigInteger a, BigInteger b)
	{
		if (b.signum() == 0)
			throw new ArithmeticException("division by zero");

		a = a.abs(); b = b.abs();
		BigInteger remainder = a.mod(b);

		if (remainder.signum() == 0)
			return new BinaryDigits[]{ new BinaryDigits(new BitSet(), 0), new BinaryDigits(new BitSet(), 0) };

		BitSet digits = new BitSet();
		HashMap<BigInteger, Integer> seenAt = new HashMap<>();
		int pos = 0;
		while (remainder.signum() != 0 && !seenAt.containsKey(remainder))
		{
			seenAt.put(remainder, pos);
			remainder = remainder.multiply(BigInteger.TWO);
			if (remainder.compareTo(b) >= 0) { digits.set(pos); remainder = remainder.subtract(b); }
			pos++;
		}

		if (remainder.signum() == 0)
			return new BinaryDigits[]{ new BinaryDigits(digits, pos), new BinaryDigits(new BitSet(), 0) };
		else
		{
			int cycleStart = seenAt.get(remainder);
			// BitSet.get(from, to) conveniently returns a NEW, re-indexed-to-0
			// BitSet for that range -- exactly what the repeating block needs.
			BitSet staticBits = digits.get(0, cycleStart);
			BitSet repeatingBits = digits.get(cycleStart, pos);
			return new BinaryDigits[]{ new BinaryDigits(staticBits, cycleStart), new BinaryDigits(repeatingBits, pos - cycleStart) };
		}
	}

	/** long overload of getBinaryDigits -- identical algorithm, long arithmetic. */
	public static BinaryDigits[] getBinaryDigits(long a, long b)
	{
		if (b == 0)
			throw new ArithmeticException("division by zero");

		a = Math.abs(a); b = Math.abs(b);
		long remainder = a % b;

		if (remainder == 0)
			return new BinaryDigits[]{ new BinaryDigits(new BitSet(), 0), new BinaryDigits(new BitSet(), 0) };

		BitSet digits = new BitSet();
		HashMap<Long, Integer> seenAt = new HashMap<>();
		int pos = 0;
		while (remainder != 0 && !seenAt.containsKey(remainder))
		{
			seenAt.put(remainder, pos);
			remainder *= 2;
			if (remainder >= b) { digits.set(pos); remainder -= b; }
			pos++;
		}

		if (remainder == 0)
			return new BinaryDigits[]{ new BinaryDigits(digits, pos), new BinaryDigits(new BitSet(), 0) };
		else
		{
			int cycleStart = seenAt.get(remainder);
			BitSet staticBits = digits.get(0, cycleStart);
			BitSet repeatingBits = digits.get(cycleStart, pos);
			return new BinaryDigits[]{ new BinaryDigits(staticBits, cycleStart), new BinaryDigits(repeatingBits, pos - cycleStart) };
		}
	}

	/** Bit i of `bits` is the i-th digit of the block, most significant
	 *  first -- the reverse of BigInteger's own bit numbering, so the
	 *  conversion flips the index. */
	private static BigInteger bitsToValue(BitSet bits, int length)
	{
		BigInteger value = BigInteger.ZERO;
		for (int i = 0; i < length; i++)
			if (bits.get(i)) value = value.setBit(length - 1 - i);
		return value;
	}

	/** Inverse of getBinaryDigits (either overload -- a BinaryDigits object
	 *  doesn't remember whether it came from long or BigInteger division,
	 *  so one method serves both). Same two-formula structure as the
	 *  decimal getRationalNumber, with 2 in place of 10. */
	public static BigFraction getRationalNumber(BinaryDigits staticDigits, BinaryDigits repeatingDigits)
	{
		int s = staticDigits.length;
		int r = repeatingDigits.length;
		BigInteger S = bitsToValue(staticDigits.bits, s);

		if (r == 0)
		{
			return new BigFraction(S, BigInteger.ONE.shiftLeft(s));
		}
		else
		{
			BigInteger R = bitsToValue(repeatingDigits.bits, r);
			BigInteger ones = BigInteger.ONE.shiftLeft(r).subtract(BigInteger.ONE); // 2^r - 1
			BigInteger numerator = S.multiply(ones).add(R);
			BigInteger denominator = BigInteger.ONE.shiftLeft(s).multiply(ones);
			return new BigFraction(numerator, denominator);
		}
	}
}
