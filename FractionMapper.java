import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.BitSet;

/**
 * Exact rational arithmetic ({@link BigFraction}), and conversion between
 * fractions and their repeating decimal or binary digits.
 * <p>
 * BigFraction is a wheel: besides the ordinary fractions it has one infinity
 * (any x/0 with x not 0) and bottom (0/0, the indeterminate element), so
 * division by zero and expressions like infinity - infinity have defined
 * results instead of throwing.
 * <p>
 * Version 2.2.
 */
public class FractionMapper
{
	/** Not used: all of this class's methods are static. */
	public FractionMapper() {}

	/**
	 * An exact fraction, always in one canonical form: reduced to lowest
	 * terms with a positive denominator, 0 as 0/1, infinity as 1/0 and bottom
	 * as 0/0. Because of that, equal values have equal fields.
	 */
	public static final class BigFraction implements Comparable<BigFraction>
	{
		/** The numerator and denominator, in the canonical form above. */
		public final BigInteger n, d;

		/**
		 * A fraction, put in canonical form.
		 * <p>
		 * 0/0 is bottom, the wheel's indeterminate element: equal only to
		 * itself. It is what add, subtract, multiply and divide naturally
		 * produce for infinity - infinity, 0 * infinity and infinity /
		 * infinity, so those have a defined value. Any other x/0 is infinity:
		 * (a:0) ~ (c:0) for any nonzero a and c, including a and -a, so there
		 * is one infinity, not +infinity and -infinity.
		 *
		 * @param numerator   the numerator
		 * @param denominator the denominator
		 */
		public BigFraction(BigInteger numerator, BigInteger denominator)
		{
			if (numerator.signum() == 0 && denominator.signum() == 0)
			{
				this.n = BigInteger.ZERO;
				this.d = BigInteger.ZERO;
				return;
			}

			if (denominator.signum() == 0)
			{
				this.n = BigInteger.ONE;
				this.d = BigInteger.ZERO;
				return;
			}

			if (denominator.signum() < 0)
			{
				// The sign goes to the numerator.
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
		 * @return n/d in canonical form
		 */
		public static BigFraction of(long n, long d) { return new BigFraction(BigInteger.valueOf(n), BigInteger.valueOf(d)); }

		/** The fraction 0/1. */
		public static final BigFraction ZERO = BigFraction.of(0, 1);

		/** The fraction 1/1. */
		public static final BigFraction ONE  = BigFraction.of(1, 1);

		/** The fraction 1/2. */
		public static final BigFraction HALF = BigFraction.of(1, 2);

		/**
		 * Operand size (in bits) above which a multiply asks the JDK for
		 * parallelMultiply. parallelMultiply already falls back to a plain
		 * multiply for small operands; this cheaper check in front of it lets
		 * the small operands (most calls) skip even that. It only helps on
		 * machines with more than one core. The value is a starting point,
		 * not a measured optimum.
		 */
		private static final int PARALLEL_MULTIPLY_THRESHOLD_BITS = 4000;

		/** {@code BigInteger.parallelMultiply} (Java 19+), or null on older Java. */
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
		 * a * b, with parallelMultiply when both are large and it is available.
		 *
		 * @param a a factor
		 * @param b a factor
		 * @return the product
		 */
		private static BigInteger smartMultiply(BigInteger a, BigInteger b)
		{
			if (a.bitLength() >= PARALLEL_MULTIPLY_THRESHOLD_BITS && b.bitLength() >= PARALLEL_MULTIPLY_THRESHOLD_BITS)
			{
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
		 * Divides. Dividing a nonzero value by zero gives infinity.
		 *
		 * @param fraction the divisor
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
		 * Negates (infinity and bottom are their own negatives).
		 *
		 * @return -this
		 */
		public BigFraction negate()                       { return new BigFraction(n.negate(), d); }

		/**
		 * The absolute value.
		 *
		 * @return |this|
		 */
		public BigFraction abs()                          { return n.signum() < 0 ? negate() : this; }

		/**
		 * Whether this is the infinity (1/0): one element, not +infinity and
		 * -infinity.
		 *
		 * @return whether this is infinity
		 */
		public boolean isInfinite() { return d.signum() == 0 && n.signum() != 0; }

		/**
		 * Whether this is bottom (0/0), the wheel's indeterminate element.
		 *
		 * @return whether this is bottom
		 */
		public boolean isBottom() { return d.signum() == 0 && n.signum() == 0; }

		/**
		 * Compares two finite fractions. A wheel has no total order once
		 * infinity or bottom is involved -- it is a circle (the projective
		 * line, where infinity and -infinity are the same point) plus a point
		 * off to the side -- so those throw.
		 *
		 * @param fraction the fraction to compare with
		 * @return negative, zero or positive as this is less than, equal to or
		 *         greater than fraction
		 * @throws ArithmeticException if either is infinity or bottom
		 */
		@Override
		public int compareTo(BigFraction fraction)
		{
			if (isInfinite() || isBottom() || fraction.isInfinite() || fraction.isBottom())
				throw new ArithmeticException("cannot order infinity or bottom (0/0) against anything -- "
					+ "a wheel has no total order once either value is infinite or indeterminate");
			return smartMultiply(n, fraction.d).compareTo(smartMultiply(fraction.n, d));
		}

		/**
		 * Less than.
		 *
		 * @param fraction the fraction to compare with
		 * @return whether this &lt; fraction
		 * @throws ArithmeticException if either is infinity or bottom
		 */
		public boolean lt(BigFraction fraction) { return compareTo(fraction) < 0; }

		/**
		 * Less than or equal.
		 *
		 * @param fraction the fraction to compare with
		 * @return whether this &lt;= fraction
		 * @throws ArithmeticException if either is infinity or bottom
		 */
		public boolean le(BigFraction fraction) { return compareTo(fraction) <= 0; }

		/**
		 * Greater than.
		 *
		 * @param fraction the fraction to compare with
		 * @return whether this &gt; fraction
		 * @throws ArithmeticException if either is infinity or bottom
		 */
		public boolean gt(BigFraction fraction) { return compareTo(fraction) > 0; }

		/**
		 * Equal (as {@link #equals}; unlike compareTo, works for infinity and
		 * bottom).
		 *
		 * @param fraction the fraction to compare with
		 * @return whether they are the same value
		 */
		public boolean eq(BigFraction fraction) { return this.equals(fraction); }

		/**
		 * Equal values: a plain field comparison, since every value has
		 * exactly one canonical (n, d) pair (see the constructor).
		 *
		 * @param other an object
		 * @return whether it is a BigFraction of the same value
		 */
		@Override
		public boolean equals(Object other)
		{
			if (this == other) return true;
			if (!(other instanceof BigFraction)) return false;
			BigFraction fraction = (BigFraction) other;
			return n.equals(fraction.n) && d.equals(fraction.d);
		}

		/**
		 * A hash consistent with equals.
		 *
		 * @return the hash
		 */
		@Override
		public int hashCode() { return java.util.Objects.hash(n, d); }

		/**
		 * The fraction as text: "n/d", the infinity sign for infinity, the up tack
		 * (&#x22A5;) for bottom.
		 *
		 * @return the text
		 */
		@Override public String toString()
		{
			if (isBottom()) return "\u22A5";    // bottom (indeterminate)
			if (isInfinite()) return "\u221E";  // infinity
			return n + "/" + d;
		}

		/**
		 * The nearest double: Double.POSITIVE_INFINITY for infinity, NaN for
		 * bottom. The quotient is taken to 40 significant digits, so very
		 * small values (an arithmetic coder's interval widths, say) keep their
		 * precision.
		 *
		 * @return the value as a double
		 */
		public double toDouble()
		{
			if (isBottom()) return Double.NaN;
			if (isInfinite()) return Double.POSITIVE_INFINITY;
			return new java.math.BigDecimal(n).divide(new java.math.BigDecimal(d), new java.math.MathContext(40, java.math.RoundingMode.HALF_EVEN)).doubleValue();
		}
	}

	// ---- Decimal digits --------------------------------------------------------

	/**
	 * The decimal expansion of a/b's fractional part: any integer part is
	 * dropped (a % b), so 5/4 gives the digits of 1/4, and the signs are
	 * dropped too (callers wanting a signed result track the sign
	 * themselves). Works in long arithmetic, so remainder * 10 can't overflow
	 * for any int b, and Integer.MIN_VALUE is handled.
	 *
	 * @param a the numerator
	 * @param b the denominator
	 * @return [static (non-repeating) digits, repeating digits]. Both empty:
	 *         a/b is a whole number (e.g. 3/1 or 8/4). Only the repeating
	 *         digits empty: the decimal terminates ("125", "" for 1/8).
	 *         Neither empty: "1", "6" for 1/6 = 0.1(6).
	 * @throws ArithmeticException if b == 0
	 * @see #getRationalNumber(String, String)
	 */
	public static ArrayList<String> getDecimalDigits(int a, int b)
	{
		if (b == 0)
			throw new ArithmeticException("division by zero");

		long divisor   = Math.abs((long) b);
		long remainder = Math.abs((long) a) % divisor;

		ArrayList<String> result = new ArrayList<>();
		if (remainder == 0)
		{
			result.add("");
			result.add("");
			return result;
		}

		StringBuilder digits = new StringBuilder();
		// Each remainder's digit position when first seen, so the cycle's
		// start is known once (if) a remainder repeats.
		HashMap<Long, Integer> seenAt = new HashMap<>();

		while (remainder != 0 && !seenAt.containsKey(remainder))
		{
			seenAt.put(remainder, digits.length());
			remainder *= 10;
			int digit = (int) (remainder / divisor);
			digits.append((char) ('0' + digit));
			remainder = remainder % divisor;
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
	 * Inverse of {@link #getDecimalDigits}: the value 0.S(R), S the static
	 * digits and R the repeating ones, as an exact fraction. With no
	 * repeating part it is S / 10<sup>s</sup>; with one it is
	 * (S (10<sup>r</sup> - 1) + R) / (10<sup>s</sup> (10<sup>r</sup> - 1)),
	 * s and r the digit counts (e.g. 1/6 = 0.1(6): (1 * 9 + 6) / (10 * 9) =
	 * 1/6). The two don't unify, since r = 0 would divide by 10<sup>0</sup> - 1.
	 * Leading zeros count, since the lengths come from the strings.
	 *
	 * @param staticDigits    the non-repeating digits (may be empty)
	 * @param repeatingDigits the repeating digits (may be empty)
	 * @return the fraction, in [0, 1)
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

	// ---- Binary digits ---------------------------------------------------------

	/**
	 * A block of binary digits with its length: a BitSet alone can't tell
	 * "100" from "1". Bit 0 is the block's first (most significant) digit.
	 */
	public static final class BinaryDigits
	{
		/** The digits, first digit at bit 0. */
		public final BitSet bits;

		/** The number of digits. */
		public final int length;

		/**
		 * A block of digits.
		 *
		 * @param bits   the digits, first digit at bit 0
		 * @param length the number of digits
		 */
		public BinaryDigits(BitSet bits, int length) { this.bits = bits; this.length = length; }
	}

	/**
	 * The binary analogue of {@link #getDecimalDigits}: the expansion of
	 * a/b's fractional part (integer part and signs dropped) as a static
	 * block and, if it doesn't terminate, a repeating block.
	 *
	 * @param a the numerator
	 * @param b the denominator
	 * @return [static digits, repeating digits] (length 0 for an empty block)
	 * @throws ArithmeticException if b == 0
	 * @see #getRationalNumber(BinaryDigits, BinaryDigits)
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
			remainder = remainder.multiply(BigInteger.valueOf(2));
			if (remainder.compareTo(b) >= 0) { digits.set(pos); remainder = remainder.subtract(b); }
			pos++;
		}

		if (remainder.signum() == 0)
			return new BinaryDigits[]{ new BinaryDigits(digits, pos), new BinaryDigits(new BitSet(), 0) };
		else
		{
			int cycleStart = seenAt.get(remainder);
			// BitSet.get(from, to) returns a new BitSet re-indexed from 0.
			BitSet staticBits = digits.get(0, cycleStart);
			BitSet repeatingBits = digits.get(cycleStart, pos);
			return new BinaryDigits[]{ new BinaryDigits(staticBits, cycleStart), new BinaryDigits(repeatingBits, pos - cycleStart) };
		}
	}

	/**
	 * As {@link #getBinaryDigits(BigInteger, BigInteger)}, in long arithmetic.
	 * Doubling the remainder can't overflow: instead of comparing 2r with b
	 * it compares r with b - r, and the new remainder (r - (b - r) or r + r)
	 * is below b. Long.MIN_VALUE has no positive long, so either argument
	 * being MIN_VALUE goes to the BigInteger version.
	 *
	 * @param a the numerator
	 * @param b the denominator
	 * @return [static digits, repeating digits] (length 0 for an empty block)
	 * @throws ArithmeticException if b == 0
	 */
	public static BinaryDigits[] getBinaryDigits(long a, long b)
	{
		if (b == 0)
			throw new ArithmeticException("division by zero");
		if (a == Long.MIN_VALUE || b == Long.MIN_VALUE)
			return getBinaryDigits(BigInteger.valueOf(a), BigInteger.valueOf(b));

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
			long rest = b - remainder;
			if (remainder >= rest) { digits.set(pos); remainder -= rest; }
			else                   remainder += remainder;
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

	/**
	 * A block's value as an integer: bit i of bits is the i-th digit, most
	 * significant first -- the reverse of BigInteger's bit numbering.
	 *
	 * @param bits   the digits
	 * @param length the number of digits
	 * @return the value
	 */
	private static BigInteger bitsToValue(BitSet bits, int length)
	{
		BigInteger value = BigInteger.ZERO;
		for (int i = 0; i < length; i++)
			if (bits.get(i)) value = value.setBit(length - 1 - i);
		return value;
	}

	/**
	 * Inverse of either getBinaryDigits: the same formulas as the decimal
	 * {@link #getRationalNumber(String, String)}, with 2 in place of 10.
	 *
	 * @param staticDigits    the non-repeating block
	 * @param repeatingDigits the repeating block
	 * @return the fraction, in [0, 1)
	 */
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
