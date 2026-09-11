import java.math.BigInteger;
import java.util.*;

/**
 * Fixed-precision renormalizing version of SteeredArithmeticMapper.
 *
 * The exact-BigInteger version tracks the interval as fractions that grow
 * without bound -- by the end of a long sequence the numerator/
 * denominator can run to thousands of digits, and every operation
 * (multiply, gcd, compare) gets slower as those numbers grow. Real
 * arithmetic coders avoid this via renormalization: once the leading
 * bits of low and high agree, those bits are locked in forever and can
 * be shifted out, keeping every operation on fixed-width integers
 * regardless of sequence length.
 *
 * ============================================================================
 * WHY THIS DOESN'T NEED THE USUAL "PENDING BITS" COMPLEXITY
 * ============================================================================
 * A normal encoder defers emitting bits during underflow (when [low,high)
 * straddles the middle without yet agreeing on a leading bit) because it
 * doesn't know what future symbols will resolve those bits to. We don't
 * have that problem: the target is fully known in advance. So instead of
 * tracking low/high alone and deciding what to *emit*, this tracks low,
 * high, AND a `code` register exactly the way an arithmetic DECODER does
 * -- code is fed target's own precomputed bits -- E1/E2/E3 all just shift
 * in "the next bit of target", uniformly, with no pending-bit ambiguity
 * at all, since a decoder never needs to defer anything.
 *
 * At each step, candidates for s_j (cumulative count before the real
 * next symbol, exactly as in the exact-fraction version) are chosen so
 * that `code` -- target's value, reinterpreted in the current shrinking
 * window -- falls inside that symbol's resulting sub-range. If a
 * sequence of such choices succeeds all the way through, the standard
 * correctness of arithmetic decoding guarantees the corresponding
 * encoding's final interval contains the target exactly -- same
 * guarantee as the exact-fraction version, now on fixed-width integers.
 */
public class RenormalizingSteerCoder
{
	// 31-bit working precision, comfortably inside `long` for the
	// multiplications below (range * count where range < 2^31 and count
	// can be up to a few thousand stays far under 2^63).
	static final int PRECISION_BITS = 31;
	static final long TOP    = 1L << PRECISION_BITS;
	static final long HALF   = TOP >> 1;
	static final long QUARTER = TOP >> 2;
	static final long THREE_QUARTER = HALF + QUARTER;
	static final long MASK   = TOP - 1;

	/** Precomputes target's binary expansion (target assumed in [0,1),
	 *  given as an exact fraction) out to `nBits` bits, via repeated
	 *  doubling -- done once, up front, independent of sequence length. */
	public static boolean[] targetBits(BigInteger num, BigInteger den, int nBits)
	{
		boolean[] bits = new boolean[nBits];
		BigInteger rem = num.mod(den);
		for (int i = 0; i < nBits; i++)
		{
			rem = rem.shiftLeft(1);
			if (rem.compareTo(den) >= 0) { bits[i] = true; rem = rem.subtract(den); }
			else bits[i] = false;
		}
		return bits;
	}

	private static long readBit(boolean[] bits, int pos) { return (pos < bits.length && bits[pos]) ? 1L : 0L; }

	/** Mutable coder state, cheap to copy for backtracking (fixed-size
	 *  fields only -- no BigInteger, no growing allocations). */
	static final class State implements Cloneable
	{
		long low, high, code;
		int bitPos; // next unread index into targetBits (feeds `code`)
		boolean[] targetBits;

		State(boolean[] targetBits)
		{
			this.targetBits = targetBits;
			low = 0; high = MASK;
			code = 0;
			for (int i = 0; i < PRECISION_BITS; i++) code = (code << 1) | readBit(targetBits, i);
			bitPos = PRECISION_BITS;
		}

		@Override public State clone()
		{
			try { return (State) super.clone(); }
			catch (CloneNotSupportedException e) { throw new AssertionError(e); }
		}

		/** Narrows [low,high) to the sub-range [sJ, sJ+fJ) out of m, then
		 *  renormalizes (E1/E2/E3), reading fresh target bits into `code`
		 *  exactly as a real decoder reads fresh input bits -- uniformly,
		 *  with no pending-bit distinction needed. */
		void narrowAndRenormalize(long sJ, long fJ, long m)
		{
			long range = high - low + 1;
			long newHigh = low + (range * (sJ + fJ)) / m - 1;
			long newLow  = low + (range * sJ) / m;
			low = newLow; high = newHigh;

			while (true)
			{
				if (high < HALF)
				{
					// top bit is 0 on both ends -- fall through to shift below
				}
				else if (low >= HALF)
				{
					low -= HALF; high -= HALF; code -= HALF;
				}
				else if (low >= QUARTER && high < THREE_QUARTER)
				{
					low -= QUARTER; high -= QUARTER; code -= QUARTER;
				}
				else break;

				low = low << 1;
				high = (high << 1) | 1;
				code = (code << 1) | readBit(targetBits, bitPos);
				bitPos++;
			}
		}
	}

	private static final class Candidate
	{
		final long[] values;
		final int[] otherSyms;
		Candidate(long[] values, int[] otherSyms) { this.values = values; this.otherSyms = otherSyms; }
	}

	/** Candidate search: same window/subset-sum logic as before, but
	 *  ordered by encoding cost (cheapest first) via
	 *  SteeredArithmeticMapper.reconstructBeforeCount/log2BinomialCoeff,
	 *  rather than by closeness to the center of the resulting window --
	 *  mirroring the fix already made to the exact-fraction version,
	 *  where the cost-based ordering measured consistently better (fewer
	 *  backtracks, smaller output) than centering, never a tradeoff. */
	private static Candidate getCandidate(int realSymbol, int[] f, long m, State st)
	{
		ArrayList<Integer> otherList = new ArrayList<>();
		for (int s = 0; s < f.length; s++) if (f[s] > 0 && s != realSymbol) otherList.add(s);
		int[] otherSyms = new int[otherList.size()];
		int[] otherCounts = new int[otherList.size()];
		for (int i = 0; i < otherSyms.length; i++) { otherSyms[i] = otherList.get(i); otherCounts[i] = f[otherList.get(i)]; }
		int kMinus1 = otherSyms.length;

		BitSet sums = SteeredArithmeticMapper.possibleSubsetSum(otherCounts);
		long fJ = f[realSymbol];
		long range = st.high - st.low + 1;
		long value = st.code - st.low; // target's position within the current window, in [0, range)
		if (value < 0 || value >= range)
			return new Candidate(new long[0], otherSyms); // code has drifted outside the window: no valid choice here

		// s_j must satisfy: low + floor(range*s_j/m) <= code <= low + floor(range*(s_j+f_j)/m) - 1
		long sHi = (value * m) / range;               // loose upper bound
		long sLo = Math.max(0, sHi - fJ - 2);           // loose lower bound, with slack for integer rounding
		ArrayList<Long> cand = new ArrayList<>();
		for (long s = sLo; s <= sHi + 1 && s <= sums.length(); s++)
		{
			if (s < 0 || s > Integer.MAX_VALUE || !sums.get((int) s)) continue;
			long lo = st.low + (range * s) / m;
			long hi = st.low + (range * (s + fJ)) / m - 1;
			if (lo <= st.code && st.code <= hi) cand.add(s);
		}
		cand.sort((a, b) -> {
			int posA = SteeredArithmeticMapper.reconstructBeforeCount(otherCounts, (int)(long) a);
			int posB = SteeredArithmeticMapper.reconstructBeforeCount(otherCounts, (int)(long) b);
			double costA = SteeredArithmeticMapper.log2BinomialCoeff(kMinus1, posA);
			double costB = SteeredArithmeticMapper.log2BinomialCoeff(kMinus1, posB);
			return Double.compare(costA, costB);
		});
		long[] values = new long[cand.size()];
		for (int i = 0; i < values.length; i++) values[i] = cand.get(i);
		return new Candidate(values, otherSyms);
	}

	public static final class SteerResult
	{
		public final int[][] orderings;
		public final long backtracks;
		SteerResult(int[][] orderings, long backtracks) { this.orderings = orderings; this.backtracks = backtracks; }
	}

	/** Backtracking search, structurally identical to
	 *  SteeredArithmeticMapper.steerEncode() but using fixed-precision
	 *  State instead of exact Fracs. */
	public static SteerResult steerEncode(int[] src, int[] freq, BigInteger targetNum, BigInteger targetDen,
	                                       long maxBacktracks, long deadlineNanos, int targetBitCount)
	{
		int n = src.length;
		boolean[] tBits = targetBits(targetNum, targetDen, targetBitCount);
		int[][] orderings = new int[n][];

		final class Frame
		{
			int[] f; long m; State st; Candidate cand; int idx;
			Frame(int[] f, long m, State st, Candidate cand) { this.f = f; this.m = m; this.st = st; this.cand = cand; this.idx = 0; }
		}

		ArrayDeque<Frame> stack = new ArrayDeque<>();
		int[] f0 = Arrays.copyOf(freq, freq.length);
		long m0 = 0; for (int c : f0) m0 += c;
		State st0 = new State(tBits);
		int i = 0;
		Candidate cand0 = getCandidate(src[0], f0, m0, st0);
		stack.push(new Frame(f0, m0, st0, cand0));
		long backtracks = 0;

		while (true)
		{
			if (i == n) break;
			if (stack.isEmpty() || backtracks > maxBacktracks || System.nanoTime() > deadlineNanos)
				return null;

			Frame top = stack.peek();
			if (top.idx >= top.cand.values.length)
			{
				stack.pop();
				i--;
				backtracks++;
				if (stack.isEmpty()) return null;
				continue;
			}

			long sJ = top.cand.values[top.idx];
			top.idx++;
			int j = src[i];
			long fJ = top.f[j];
			orderings[i] = SteeredArithmeticMapper.orderingForChoicePublic(j, top.cand.otherSyms, top.f, (int) sJ);

			State newSt = top.st.clone();
			newSt.narrowAndRenormalize(sJ, fJ, top.m);
			int[] f2 = Arrays.copyOf(top.f, top.f.length);
			f2[j]--;
			i++;
			if (i == n) return new SteerResult(orderings, backtracks);
			if (tBits.length - newSt.bitPos < 4) return null; // ran out of precomputed target bits; caller should retry with more
			Candidate newCand = getCandidate(src[i], f2, top.m - 1, newSt);
			stack.push(new Frame(f2, top.m - 1, newSt, newCand));
		}
		return new SteerResult(orderings, backtracks);
	}

	/** Cheap, direct replay decode (mirrors SteeredArithmeticMapper.steerDecode). */
	public static int[] steerDecode(int[][] orderings, int[] freq, BigInteger targetNum, BigInteger targetDen, int n, int targetBitCount)
	{
		boolean[] tBits = targetBits(targetNum, targetDen, targetBitCount);
		int[] f = Arrays.copyOf(freq, freq.length);
		long m = 0; for (int c : f) m += c;
		State st = new State(tBits);
		int[] decoded = new int[n];
		for (int i = 0; i < n; i++)
		{
			int[] perm = orderings[i];
			long range = st.high - st.low + 1;
			long cum = 0; int chosen = -1; long sJ = -1, fJ = -1;
			for (int sym : perm)
			{
				long fSym = f[sym];
				long lo = st.low + (range * cum) / m;
				long hi = st.low + (range * (cum + fSym)) / m - 1;
				if (lo <= st.code && st.code <= hi) { chosen = sym; sJ = cum; fJ = fSym; break; }
				cum += fSym;
			}
			if (chosen < 0) throw new IllegalStateException("decode failed at step " + i);
			st.narrowAndRenormalize(sJ, fJ, m);
			f[chosen]--;
			m--;
			decoded[i] = chosen;
		}
		return decoded;
	}
}
