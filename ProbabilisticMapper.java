import java.math.BigInteger;
import java.util.*;

/**
 * Experiments with the ordering of arithmetic coding's probability space.
 * <p>
 * <b>Steering.</b> Standard "sampling without replacement" arithmetic coding
 * narrows the interval [off, off + rng) at each step by splitting it into
 * consecutive slices, one per remaining symbol, each proportional to that
 * symbol's remaining count. The width of the real next symbol's slice doesn't
 * depend on the order of the slices, but where it sits does. So the order can
 * be chosen at each step to steer the final interval onto a chosen target
 * fraction: treating the target as though it were being decoded, its position
 * rescaled into the current interval (the "residual") must fall inside the
 * real symbol's slice. A slice starting at cumulative count s<sub>j</sub>
 * works if s<sub>j</sub> &lt;= residual * m &lt; s<sub>j</sub> + f<sub>j</sub>,
 * and s<sub>j</sub> must be a sum of some subset of the other remaining
 * symbols' counts. {@link #steerEncode} tries the candidates, recursing and
 * backtracking on dead ends; success through every step guarantees the final
 * interval contains the target exactly. It was ported from a Python prototype
 * (steered_arithmetic.py); {@code SegmentSteerDemo} runs several targets on
 * concurrent threads.
 * <p>
 * <b>What gets stored.</b> Each step's complete ordering of the remaining
 * symbols, since the decoder needs the whole ordering, not just the winning
 * s<sub>j</sub>, to find the slice the residual falls in. Compression
 * efficiency is deliberately not the goal: storing the orderings is the
 * expensive part. {@link #compress} stores them as compactly as this class
 * knows how (the real symbol by plain arithmetic coding, the symbols before
 * it as a combinadic rank).
 * <p>
 * <b>Order tables.</b> Fixed (whole-sequence) orderings of the probability
 * space -- by frequency, by when each value is used up, or random -- and
 * exact arithmetic coders that use them. Ordering the probability space has
 * not been found to affect compression or speed significantly; an exhaustive
 * search through all the possible tables would be needed to say for sure.
 * <p>
 * In source order: subset sums and orderings, the steering search, storing
 * the orderings, order tables, and the order-table coders.
 */
public class ProbabilisticMapper
{
	/** Not used: all of this class's methods are static. */
	public ProbabilisticMapper() {}

	// ---- Subset sums and orderings ---------------------------------------------------

	/**
	 * The sums that some subset of the counts can make, including the empty sum
	 * 0. A BitSet is used for its memory (one bit per sum) and fast or.
	 *
	 * @param frequency the counts
	 * @return bit s is set if some subset of the counts sums to s
	 */
	public static BitSet possibleSubsetSum(int[] frequency)
	{
		int sum = 0;
		for(int i = 0; i < frequency.length; i++)
			sum += frequency[i];

		// Include the trival sum 0, which is always possible.
		// This makes the index for each sum the same as the sum.
		int    possible_sums = sum + 1;
		BitSet possible      = new BitSet(possible_sums);
		possible.set(0);

		for(int i = 0; i < frequency.length; i++)
		{
			int k = frequency[i];

			// If the frequency of a value is 0 it won't change the
			// value of the current accumulated sum, which has already
			// been set as possible.
			if(k == 0)
				continue;

			// Create a new boolean map and set all the previous possible sums
			// incremented by the current frequency.
			BitSet shifted = new BitSet(possible_sums);
			for(int j = possible.nextSetBit(0); j >= 0; j = possible.nextSetBit(j + 1))
				if (j + k <= sum)
					shifted.set(j + k);

			// Logically or the new possible sums with the ones that were already set.
			possible.or(shifted);
		}

		return possible;
	}

	/**
	 * The subset-sum search shared by {@link #orderingForChoice} and
	 * {@link #reconstructBeforeCount}: an exact subset of counts summing to sJ,
	 * found by dynamic programming with backtracking (not greedily -- largest
	 * first can miss a subset that exists: for counts {5, 4, 3} and 7, it takes 5
	 * and gets stuck, though {4, 3} works). Where there is a choice, later items
	 * are taken first.
	 *
	 * @param counts the counts to choose from
	 * @param sJ     the sum wanted
	 * @return which counts are in the subset, or null if no subset sums to sJ
	 */
	private static boolean[] subsetBefore(int[] counts, int sJ)
	{
		int k = counts.length;

		// reachable[i][s] = true if some subset of counts[0..i-1] sums to s
		boolean[][] reachable = new boolean[k + 1][sJ + 1];
		reachable[0][0] = true;
		for (int i = 0; i < k; i++)
		{
			int c = counts[i];
			for (int s = 0; s <= sJ; s++)
			{
				if (!reachable[i][s]) continue;
				reachable[i + 1][s] = true; // don't take item i
				if (s + c <= sJ) reachable[i + 1][s + c] = true; // take item i
			}
		}
		if (!reachable[k][sJ])
			return null;

		boolean[] used = new boolean[k];
		int s = sJ;
		for (int i = k - 1; i >= 0; i--)
		{
			int c = counts[i];
			// prefer taking item i if doing so still leaves a reachable state
			// (keeps this deterministic and simple; either valid choice works)
			if (s - c >= 0 && reachable[i][s - c]) { used[i] = true; s -= c; }
			// else: don't take item i (reachable[i][s] must hold by invariant)
		}
		return used;
	}

	/**
	 * One concrete ordering consistent with a chosen s<sub>j</sub>: an exact
	 * subset of the other symbols whose counts sum to sJ (see
	 * {@link #subsetBefore}) is placed before the real symbol, and the rest after
	 * it.
	 *
	 * @param realSymbol the symbol being coded
	 * @param otherSyms  the other symbols still remaining
	 * @param f          the remaining count of each symbol
	 * @param sJ         the chosen start of the real symbol's slice
	 * @return the ordering: the subset, the real symbol, then the rest
	 * @throws IllegalStateException if no subset of the counts sums to sJ (a bug
	 *         in how the candidates were made)
	 */
	public static int[] orderingForChoice(int realSymbol, int[] otherSyms, int[] f, int sJ)
	{
		int k = otherSyms.length;
		int[] counts = new int[k];
		for (int i = 0; i < k; i++) counts[i] = f[otherSyms[i]];

		boolean[] used = subsetBefore(counts, sJ);
		if (used == null)
			throw new IllegalStateException("subset-sum reconstruction failed: sJ=" + sJ
				+ " not reachable from counts=" + Arrays.toString(counts) + " (candidate generation bug)");

		int[] result = new int[k + 1];
		int p = 0;
		for (int i = 0; i < k; i++) if (used[i]) result[p++] = otherSyms[i];
		result[p++] = realSymbol;
		for (int i = 0; i < k; i++) if (!used[i]) result[p++] = otherSyms[i];
		return result;
	}

	/**
	 * How many symbols {@link #orderingForChoice} would put before the real one
	 * for a given s<sub>j</sub>. Used, with {@link #log2BinomialCoeff}, to rank
	 * candidates by the cost of storing them.
	 *
	 * @param counts the other symbols' remaining counts
	 * @param sJ     the chosen start of the real symbol's slice
	 * @return the size of the "before" group, or 0 if no subset sums to sJ
	 */
	public static int reconstructBeforeCount(int[] counts, int sJ)
	{
		boolean[] used = subsetBefore(counts, sJ);
		if (used == null) return 0;
		int count = 0;
		for (boolean u : used) if (u) count++;
		return count;
	}

	// ---- The steering search ---------------------------------------------------------

	/**
	 * A symbol to be coded, with the starts its slice could take (the candidate
	 * s<sub>j</sub> values) and the other symbols still remaining.
	 */
	private static final class Candidate
	{
		/**
		 * The symbol being coded (its remaining count is not zero).
		 */
		final int   symbol;

		/**
		 * The other symbols still remaining (count not zero).
		 */
		final int[] other_symbol;

		/**
		 * The candidate values for s<sub>j</sub>: sums of some subset of
		 * other_symbol's remaining counts that place the residual inside symbol's
		 * slice, cheapest to store first (see {@link ProbabilisticMapper#getCandidate}).
		 */
		final int[] sum;

		/**
		 * A candidate set for one step.
		 *
		 * @param symbol       the symbol being coded
		 * @param sum          the candidate starts, cheapest first
		 * @param other_symbol the other symbols remaining
		 */
		Candidate(int symbol, int[] sum, int[] other_symbol)
		{
			this.symbol       = symbol;
			this.sum          = sum;
			this.other_symbol = other_symbol;
		}
	}

	/**
	 * The base-2 logarithm.
	 *
	 * @param x a positive number
	 * @return log<sub>2</sub>(x)
	 */
	private static double log2(double x) { return Math.log(x) / Math.log(2); }

	/**
	 * The base-2 logarithm of a factorial.
	 *
	 * @param n a count
	 * @return log<sub>2</sub>(n!)
	 */
	private static double log2Factorial(int n) { double b = 0; for (int k = 2; k <= n; k++) b += log2(k); return b; }

	/**
	 * log<sub>2</sub> of the binomial coefficient C(n, k): the bits needed to say
	 * which k of n things were chosen.
	 *
	 * @param n the number of things
	 * @param k the number chosen
	 * @return log<sub>2</sub> C(n, k), or 0 if k is out of range
	 */
	public static double log2BinomialCoeff(int n, int k)
	{
		if (k < 0 || k > n) return 0;
		return log2Factorial(n) - log2Factorial(k) - log2Factorial(n - k);
	}

	/**
	 * The candidate starts for the real symbol's slice: the subset sums s of the
	 * other symbols' counts with s / m &lt;= residual &lt; (s + f<sub>j</sub>) / m,
	 * cheapest to store first.
	 * <p>
	 * Candidates are ranked by log<sub>2</sub> C(k - 1, before), where before is
	 * the number of symbols the choice puts before the real one: smallest when
	 * before is near 0 or k - 1. An earlier heuristic, trying the slice that left
	 * the residual nearest its middle first, was measured against this one over
	 * several seeds and segment sizes: this ranking was both cheaper and more
	 * reliable (fewer backtracks) every time, so the other was removed.
	 *
	 * @param realSymbol the symbol being coded
	 * @param f          the remaining count of each symbol
	 * @param m          the total remaining count
	 * @param residual   the target's position within the current interval
	 * @return the candidates
	 */
	private static Candidate getCandidate(int realSymbol, int[] f, long m, FractionMapper.BigFraction residual)
	{
		ArrayList<Integer> otherList = new ArrayList<>();
		for (int s = 0; s < f.length; s++) if (f[s] > 0 && s != realSymbol) otherList.add(s);
		int[] otherSyms = new int[otherList.size()];
		int[] otherCounts = new int[otherList.size()];
		for (int i = 0; i < otherSyms.length; i++) { otherSyms[i] = otherList.get(i); otherCounts[i] = f[otherList.get(i)]; }
		int kMinus1 = otherSyms.length;

		BitSet sums = possibleSubsetSum(otherCounts);
		long fJ = f[realSymbol];

		ArrayList<Long> cand = new ArrayList<>();
		for (int s = sums.nextSetBit(0); s >= 0; s = sums.nextSetBit(s + 1))
		{
			FractionMapper.BigFraction lo = FractionMapper.BigFraction.of(s, m);
			FractionMapper.BigFraction hi = FractionMapper.BigFraction.of(s + fJ, m);
			if (lo.le(residual) && residual.lt(hi)) cand.add((long) s);
		}

		cand.sort((a, b) -> {
			int posA = reconstructBeforeCount(otherCounts, (int)(long) a);
			int posB = reconstructBeforeCount(otherCounts, (int)(long) b);
			double costA = log2BinomialCoeff(kMinus1, posA);
			double costB = log2BinomialCoeff(kMinus1, posB);
			return Double.compare(costA, costB);
		});
		int[] values = new int[cand.size()];
		for (int i = 0; i < values.length; i++) values[i] = (int)(long) cand.get(i);
		return new Candidate(realSymbol, values, otherSyms);
	}

	/**
	 * A successful steering: each step's ordering, the final interval, and how
	 * many dead ends the search backed out of.
	 */
	public static final class SteerResult
	{
		/**
		 * Each step's ordering of the remaining symbols.
		 */
		public final int[][] orderings;
		/**
		 * The final interval, [off, off + rng); it contains the target.
		 */
		public final FractionMapper.BigFraction off, rng;
		/**
		 * How many dead ends the search backed out of.
		 */
		public final long backtracks;
		/**
		 * A result, as steerEncode makes it.
		 *
		 * @param orderings  each step's ordering
		 * @param off        the final interval's start
		 * @param rng        its width
		 * @param backtracks dead ends backed out of
		 */
		SteerResult(int[][] orderings, FractionMapper.BigFraction off, FractionMapper.BigFraction rng, long backtracks)
		{ this.orderings = orderings; this.off = off; this.rng = rng; this.backtracks = backtracks; }
	}

	/**
	 * Finds a sequence of per-step orderings that steers the final interval to
	 * contain target exactly, by backtracking search over the candidates
	 * ({@link #getCandidate}).
	 * <p>
	 * Callers running several attempts in parallel should treat null as "this
	 * attempt didn't succeed in budget", not necessarily "impossible".
	 *
	 * @param src           the symbols to code
	 * @param freq          the count of each symbol in src
	 * @param target        the fraction to steer onto
	 * @param maxBacktracks give up after this many dead ends
	 * @param deadlineNanos give up when {@link System#nanoTime} passes this
	 * @return the orderings and final interval, or null if no path was found in
	 *         budget
	 * @see #steerDecode
	 */
	public static SteerResult steerEncode(int[] src, int[] freq, FractionMapper.BigFraction target, long maxBacktracks, long deadlineNanos)
	{
		int n = src.length;
		int[][] orderings = new int[n][];

		final class Frame
		{
			int[] f; int m; FractionMapper.BigFraction off, rng, residual; Candidate cand; int idx;
			Frame(int[] f, int m, FractionMapper.BigFraction off, FractionMapper.BigFraction rng, FractionMapper.BigFraction residual, Candidate cand)
			{ this.f=f; this.m=m; this.off=off; this.rng=rng; this.residual=residual; this.cand=cand; this.idx=0; }
		}

		ArrayDeque<Frame> stack = new ArrayDeque<>();
		int[] f0 = Arrays.copyOf(freq, freq.length);
		int m0 = 0; for (int c : f0) m0 += c;
		int i = 0;
		Candidate cand0 = getCandidate(src[0], f0, m0, target);
		stack.push(new Frame(f0, m0, FractionMapper.BigFraction.ZERO, FractionMapper.BigFraction.ONE, target, cand0));
		long backtracks = 0;

		while (true)
		{
			if (i == n) break;
			if (stack.isEmpty() || backtracks > maxBacktracks || System.nanoTime() > deadlineNanos)
				return null;

			Frame top = stack.peek();
			if (top.idx >= top.cand.sum.length)
			{
				stack.pop();
				i--;
				backtracks++;
				if (stack.isEmpty()) return null;
				continue;
			}

			int sJ = top.cand.sum[top.idx];
			top.idx++;
			int j = src[i];
			int fJ = top.f[j];
			orderings[i] = orderingForChoice(j, top.cand.other_symbol, top.f, sJ);

			FractionMapper.BigFraction newOff = top.off.add(top.rng.multiply(FractionMapper.BigFraction.of(sJ, top.m)));
			FractionMapper.BigFraction newRng = top.rng.multiply(FractionMapper.BigFraction.of(fJ, top.m));
			FractionMapper.BigFraction lo = FractionMapper.BigFraction.of(sJ, top.m), hi = FractionMapper.BigFraction.of(sJ + fJ, top.m);
			FractionMapper.BigFraction newResidual = top.residual.subtract(lo).divide(hi.subtract(lo));
			int[] f2 = Arrays.copyOf(top.f, top.f.length);
			f2[j]--;
			i++;
			if (i == n) return new SteerResult(orderings, newOff, newRng, backtracks);
			Candidate newCand = getCandidate(src[i], f2, top.m - 1, newResidual);
			stack.push(new Frame(f2, top.m - 1, newOff, newRng, newResidual, newCand));
		}
		return new SteerResult(orderings, FractionMapper.BigFraction.ZERO, FractionMapper.BigFraction.ONE, backtracks);
	}

	/**
	 * Decodes a steered sequence: a direct replay, with no search, since the
	 * stored orderings say which symbol's slice the (known) target falls in at
	 * each step.
	 *
	 * @param orderings each step's ordering, from {@link #steerEncode}
	 * @param freq      the count of each symbol, as given to the encoder
	 * @param target    the target fraction
	 * @param n         the number of symbols
	 * @return the symbols
	 * @throws IllegalStateException if the target falls in no slice at some step
	 */
	public static int[] steerDecode(int[][] orderings, int[] freq, FractionMapper.BigFraction target, int n)
	{
		int[] f = Arrays.copyOf(freq, freq.length);
		int m = 0; for (int c : f) m += c;
		FractionMapper.BigFraction residual = target;
		int[] decoded = new int[n];
		for (int i = 0; i < n; i++)
		{
			int[] perm = orderings[i];
			int cum = 0, chosen = -1, sJ = -1;
			FractionMapper.BigFraction targetPos = residual.multiply(m);
			for (int sym : perm)
			{
				int fSym = f[sym];
				if (FractionMapper.BigFraction.of(cum, 1).le(targetPos) && targetPos.lt(FractionMapper.BigFraction.of(cum + fSym, 1)))
				{ chosen = sym; sJ = cum; break; }
				cum += fSym;
			}
			if (chosen < 0) throw new IllegalStateException("decode failed at step " + i);
			int fJ = f[chosen];
			FractionMapper.BigFraction lo = FractionMapper.BigFraction.of(sJ, m), hi = FractionMapper.BigFraction.of(sJ + fJ, m);
			residual = residual.subtract(lo).divide(hi.subtract(lo));
			f[chosen]--;
			m--;
			decoded[i] = chosen;
		}
		return decoded;
	}

	/**
	 * The bits needed to store every step's ordering as a plain permutation:
	 * the sum of log<sub>2</sub>(k!) over the steps.
	 *
	 * @param orderings each step's ordering
	 * @return the size in bits
	 */
	public static double serializedOrderingBits(int[][] orderings)
	{
		double total = 0;
		for (int[] perm : orderings)
		{
			int k = perm.length;
			double bits = 0;
			for (int v = 2; v <= k; v++) bits += Math.log(v) / Math.log(2);
			total += bits;
		}
		return total;
	}

	// ---- Storing the orderings ------------------------------------------------------

	/**
	 * The binomial coefficient C(n, k), exactly while it fits in a long.
	 *
	 * @param n the number of things
	 * @param k the number chosen
	 * @return C(n, k), or 0 if k is out of range
	 */
	public static long binomial(int n, int k)
	{
		if (k < 0 || k > n) return 0;
		if (k > n - k) k = n - k;
		long result = 1;
		for (int i = 0; i < k; i++) result = result * (n - i) / (i + 1);
		return result;
	}

	/**
	 * The combinatorial number system (combinadic): maps a k-subset of
	 * {0, ..., n - 1} to a unique integer in [0, C(n, k)). Used to store the
	 * "before" group in log<sub>2</sub> C(k - 1, pos) bits instead of a (k - 1)-bit
	 * mask.
	 *
	 * @param sortedChosen the subset, in increasing order
	 * @return its rank
	 * @see #combinadicDecode
	 */
	public static long combinadicEncode(int[] sortedChosen)
	{
		long rank = 0;
		for (int i = 0; i < sortedChosen.length; i++) rank += binomial(sortedChosen[i], i + 1);
		return rank;
	}

	/**
	 * The subset with a given combinadic rank.
	 *
	 * @param rank the rank, from {@link #combinadicEncode}
	 * @param k    the size of the subset
	 * @return the subset, in increasing order
	 */
	public static int[] combinadicDecode(long rank, int k)
	{
		int[] result = new int[k];
		long r = rank;
		for (int pos = k; pos >= 1; pos--)
		{
			int c = pos - 1;
			while (binomial(c + 1, pos) <= r) c++;
			result[pos - 1] = c;
			r -= binomial(c, pos);
		}
		return result;
	}

	/**
	 * Exact arithmetic coding of a plain sequence of symbols (not steered). Used to
	 * store which symbol is real at each step: this costs exactly what ordinary
	 * arithmetic coding of the data would, since a symbol's chance of being real
	 * is its remaining count over the remaining total. The coded value is the
	 * simplest fraction in the final interval
	 * ({@link ArithmeticMapper#simplestFractionInInterval}).
	 *
	 * @param src  the symbols
	 * @param freq the count of each symbol in src
	 * @return {numerator, denominator} of the coded value
	 * @see #decodeCSequence
	 */
	public static BigInteger[] encodeCSequence(int[] src, int[] freq)
	{
		int[] f = Arrays.copyOf(freq, freq.length);
		BigInteger m = BigInteger.valueOf(Arrays.stream(f).asLongStream().sum());
		BigInteger offN = BigInteger.ZERO, offD = BigInteger.ONE, rngN = BigInteger.ONE, rngD = BigInteger.ONE;

		for (int j : src)
		{
			int s = 0;
			for (int k = 0; k < j; k++) s += f[k];
			BigInteger sBI = BigInteger.valueOf(s), fJ = BigInteger.valueOf(f[j]);

			// off and rng may have DIFFERENT denominators (each reduced
			// independently below), so combining them needs a proper
			// cross-multiplication -- NOT a same-denominator shortcut
			// (an earlier version of this exact code had that bug: it
			// worked on a tiny 5-symbol test by coincidence, then
			// produced an off value far outside [0,1) at real scale).
			BigInteger newOffN = offN.multiply(rngD).multiply(m).add(rngN.multiply(sBI).multiply(offD));
			BigInteger newOffD = offD.multiply(rngD).multiply(m);
			BigInteger newRngN = rngN.multiply(fJ);
			BigInteger newRngD = rngD.multiply(m);

			BigInteger g1 = newOffN.gcd(newOffD); if (g1.signum() != 0 && !g1.equals(BigInteger.ONE)) { newOffN = newOffN.divide(g1); newOffD = newOffD.divide(g1); }
			BigInteger g2 = newRngN.gcd(newRngD); if (g2.signum() != 0 && !g2.equals(BigInteger.ONE)) { newRngN = newRngN.divide(g2); newRngD = newRngD.divide(g2); }
			offN = newOffN; offD = newOffD; rngN = newRngN; rngD = newRngD;

			f[j]--;
			m = m.subtract(BigInteger.ONE);
		}

		// The codeword: the fraction with the smallest denominator anywhere
		// in the final interval, not just its lower bound.
		BigInteger hiN = offN.multiply(rngD).add(rngN.multiply(offD));
		BigInteger hiD = offD.multiply(rngD);
		return ArithmeticMapper.simplestFractionInInterval(offN, offD, hiN, hiD);
	}

	/**
	 * Decodes {@link #encodeCSequence}'s value.
	 *
	 * @param codeN the numerator of the coded value
	 * @param codeD its denominator
	 * @param freq  the count of each symbol, as given to the encoder
	 * @param n     the number of symbols
	 * @return the symbols
	 * @throws IllegalStateException if the value falls in no symbol's slice
	 */
	public static int[] decodeCSequence(BigInteger codeN, BigInteger codeD, int[] freq, int n)
	{
		int[] f = Arrays.copyOf(freq, freq.length);
		BigInteger m = BigInteger.valueOf(Arrays.stream(f).asLongStream().sum());
		int[] decoded = new int[n];
		for (int i = 0; i < n; i++)
		{
			BigInteger scaledN = codeN.multiply(m);
			int cum = 0, chosen = -1;
			for (int j = 0; j < f.length; j++)
			{
				if (f[j] == 0) continue;
				BigInteger loN = BigInteger.valueOf(cum), hiN = BigInteger.valueOf(cum + f[j]);
				if (loN.multiply(codeD).compareTo(scaledN) <= 0 && scaledN.compareTo(hiN.multiply(codeD)) < 0)
				{ chosen = j; break; }
				cum += f[j];
			}
			if (chosen < 0) throw new IllegalStateException("c-sequence decode failed at step " + i);

			BigInteger cumN = BigInteger.valueOf(cum);
			BigInteger newN = codeN.multiply(m).subtract(cumN.multiply(codeD));
			BigInteger newD = codeD.multiply(BigInteger.valueOf(f[chosen]));
			BigInteger g = newN.gcd(newD); if (g.signum() != 0 && !g.equals(BigInteger.ONE)) { newN = newN.divide(g); newD = newD.divide(g); }
			codeN = newN; codeD = newD;

			f[chosen]--;
			m = m.subtract(BigInteger.ONE);
			decoded[i] = chosen;
		}
		return decoded;
	}

	/**
	 * Writes bits, most significant first, into a growing buffer.
	 */
	private static final class BitWriter
	{
		/** An empty writer. */
		BitWriter() {}

		/**
		 * The bits written, packed into bytes.
		 */
		byte[] buf = new byte[64];
		/**
		 * The number of bits written.
		 */
		int bitLen = 0;
		/**
		 * Grows the buffer to take more bits.
		 *
		 * @param extraBits the bits about to be written
		 */
		void ensure(int extraBits) { int needed=(bitLen+extraBits+7)/8; if(needed>buf.length) buf=Arrays.copyOf(buf, Math.max(needed, buf.length*2)); }
		/**
		 * Writes the low bits of a value, most significant first.
		 *
		 * @param value   the value
		 * @param numBits how many of its low bits to write
		 */
		void writeBits(long value, int numBits)
		{
			ensure(numBits);
			for (int b = numBits - 1; b >= 0; b--)
			{
				int bit = (int) ((value >>> b) & 1);
				int byteIdx = bitLen / 8, bitIdx = bitLen % 8;
				if (bit != 0) buf[byteIdx] |= (byte) (1 << (7 - bitIdx));
				bitLen++;
			}
		}
		/**
		 * The bits written so far.
		 *
		 * @return the bits written, in as many bytes as they need
		 */
		byte[] toBytes() { return Arrays.copyOf(buf, (bitLen + 7) / 8); }
	}

	/**
	 * Reads bits written by {@link BitWriter}; past the end it fails.
	 */
	private static final class BitReader
	{
		/** The bits. */
		final byte[] buf;

		/** The next bit to read. */
		int bitPos = 0;
		/**
		 * A reader starting at the first bit.
		 *
		 * @param buf the bits
		 */
		BitReader(byte[] buf) { this.buf = buf; }
		/**
		 * Reads a value written by {@link BitWriter#writeBits}.
		 *
		 * @param numBits how many bits it has
		 * @return the value
		 */
		long readBits(int numBits)
		{
			long value = 0;
			for (int i = 0; i < numBits; i++)
			{
				int byteIdx = bitPos / 8, bitIdx = bitPos % 8;
				int bit = (buf[byteIdx] >> (7 - bitIdx)) & 1;
				value = (value << 1) | bit;
				bitPos++;
			}
			return value;
		}
	}

	/**
	 * The width of a field.
	 *
	 * @param numValues how many values a field must hold
	 * @return the bits needed: ceil(log<sub>2</sub>(numValues)), 0 for one value
	 */
	private static int bitsNeeded(int numValues) { return numValues <= 1 ? 0 : 32 - Integer.numberOfLeadingZeros(numValues - 1); }

	/**
	 * Orderings as {@link #compress} stores them: the c-sequence (which symbol is
	 * real at each step) as a fraction, and each step's "before" group as bits.
	 */
	public static final class Compressed
	{
		/**
		 * The c-sequence's coded value, numerator and denominator.
		 */
		public final BigInteger cSeqN, cSeqD;
		/**
		 * Each step's position and "before" group, as bits.
		 */
		public final byte[] subsetData;
		/**
		 * The number of steps (symbols).
		 */
		public final int n;
		/**
		 * Compressed orderings, as compress makes them.
		 *
		 * @param n_         the c-sequence's numerator
		 * @param d_         its denominator
		 * @param subsetData the steps' bits
		 * @param n          the number of steps
		 */
		Compressed(BigInteger n_, BigInteger d_, byte[] subsetData, int n)
		{ this.cSeqN = n_; this.cSeqD = d_; this.subsetData = subsetData; this.n = n; }

		/**
		 * The stored size: the c-sequence's numerator and denominator as signed
		 * whole bytes (as {@code BigInteger.toByteArray} gives them), plus the subset
		 * bits.
		 *
		 * @return the size in bits
		 */
		public int totalBits()
		{
			int cBytes = (cSeqN.signum()==0?1:(cSeqN.bitLength()+1+7)/8) + (cSeqD.signum()==0?1:(cSeqD.bitLength()+1+7)/8);
			return cBytes * 8 + subsetData.length * 8;
		}
	}

	/**
	 * Stores orderings from {@link #steerEncode} compactly: the real symbols by
	 * {@link #encodeCSequence}, and at each step the real symbol's position and
	 * the combinadic rank of the symbols before it.
	 *
	 * @param orderings each step's ordering
	 * @param src       the symbols coded
	 * @param freq      the count of each symbol in src
	 * @return the compressed orderings
	 * @see #decompress
	 */
	public static Compressed compress(int[][] orderings, int[] src, int[] freq)
	{
		BigInteger[] cCode = encodeCSequence(src, freq);

		BitWriter w = new BitWriter();
		int n = orderings.length;
		for (int i = 0; i < n; i++)
		{
			int[] table = orderings[i];
			int k = table.length;
			if (k <= 1) continue;

			int realSymbol = src[i];
			int[] remainingAlphabet = Arrays.copyOf(table, k);
			Arrays.sort(remainingAlphabet);

			int posInTable = -1;
			for (int t = 0; t < k; t++) if (table[t] == realSymbol) { posInTable = t; break; }

			int[] otherSyms = new int[k - 1];
			int p = 0;
			for (int v : remainingAlphabet) if (v != realSymbol) otherSyms[p++] = v;

			int pos = posInTable;
			int[] beforeIdx = new int[pos];
			int bi = 0;
			for (int t = 0; t < posInTable; t++)
			{
				int val = table[t];
				int idx = Arrays.binarySearch(otherSyms, val);
				beforeIdx[bi++] = idx;
			}
			Arrays.sort(beforeIdx);

			w.writeBits(pos, bitsNeeded(k));
			long rank = combinadicEncode(beforeIdx);
			long total = binomial(k - 1, pos);
			w.writeBits(rank, bitsNeeded((int) Math.min(total, Integer.MAX_VALUE)));
		}
		return new Compressed(cCode[0], cCode[1], w.toBytes(), n);
	}

	/**
	 * Rebuilds the orderings {@link #compress} stored, in the form
	 * {@link #steerDecode} takes.
	 *
	 * @param c    the compressed orderings
	 * @param freq the count of each symbol, as given to the encoder
	 * @return each step's ordering
	 */
	public static int[][] decompress(Compressed c, int[] freq)
	{
		int[] src = decodeCSequence(c.cSeqN, c.cSeqD, freq, c.n);

		BitReader r = new BitReader(c.subsetData);
		int[] f = Arrays.copyOf(freq, freq.length);
		int[][] orderings = new int[c.n][];

		for (int i = 0; i < c.n; i++)
		{
			ArrayList<Integer> remainingList = new ArrayList<>();
			for (int s = 0; s < f.length; s++) if (f[s] > 0) remainingList.add(s);
			int k = remainingList.size();
			int[] remainingAlphabet = new int[k];
			for (int t = 0; t < k; t++) remainingAlphabet[t] = remainingList.get(t);

			int realSymbol = src[i];

			if (k <= 1)
			{
				orderings[i] = remainingAlphabet;
				if (k == 1) f[remainingAlphabet[0]]--;
				continue;
			}

			int[] otherSyms = new int[k - 1];
			int p = 0;
			for (int v : remainingAlphabet) if (v != realSymbol) otherSyms[p++] = v;

			int pos = (int) r.readBits(bitsNeeded(k));
			long total = binomial(k - 1, pos);
			long rank = r.readBits(bitsNeeded((int) Math.min(total, Integer.MAX_VALUE)));
			int[] beforeIdx = combinadicDecode(rank, pos);

			HashSet<Integer> beforeVals = new HashSet<>();
			for (int idx : beforeIdx) beforeVals.add(otherSyms[idx]);

			int[] table = new int[k];
			int t2 = 0;
			for (int v : otherSyms) if (beforeVals.contains(v)) table[t2++] = v;
			table[t2++] = realSymbol;
			for (int v : otherSyms) if (!beforeVals.contains(v)) table[t2++] = v;
			orderings[i] = table;

			f[realSymbol]--;
		}
		return orderings;
	}

	// ---- Order tables: fixed orderings of the probability space ----------------------

	/**
	 * The symbols in ascending order of frequency, least frequent first (equal
	 * counts in symbol order).
	 *
	 * @param frequency the count of each symbol (at most 256 symbols)
	 * @return table[rank] = symbol (a rank-to-symbol table)
	 */
	public static byte [] getAscendingTable(int frequency[])
	{
		int n = frequency.length;
		Integer[] order = new Integer[n];
		for (int i = 0; i < n; i++) order[i] = i;
		Arrays.sort(order, (a, b) -> (frequency[a] != frequency[b]) ? Integer.compare(frequency[a], frequency[b]) : Integer.compare(a, b));

		byte [] ascending_table = new byte[n];
		for (int i = 0; i < n; i++)
			ascending_table[i] = (byte) (int) order[i];
		return ascending_table;
	}

	/**
	 * Each symbol's rank in descending order of frequency, most frequent first
	 * (among equal counts, the later symbol first). Note that, unlike
	 * {@link #getAscendingTable}, this is a symbol-to-rank table: the form the
	 * order parameter of {@link #getIntervalValue(byte[], int[], byte[])} takes.
	 *
	 * @param frequency the count of each symbol (at most 256 symbols)
	 * @return table[symbol] = rank
	 */
	public static byte [] getDescendingTable(int frequency[])
	{
		int n = frequency.length;
		Integer[] order = new Integer[n];
		for (int i = 0; i < n; i++) order[i] = i;
		Arrays.sort(order, (a, b) -> (frequency[a] != frequency[b]) ? Integer.compare(frequency[b], frequency[a]) : Integer.compare(b, a));

		byte [] descending_table = new byte[n];
		for (int i = 0; i < n; i++)
			descending_table[order[i]] = (byte) i;
		return descending_table;
	}

	/**
	 * The symbols in the order they are used up while coding src: first those
	 * that never occur (in symbol order), then each as its last occurrence is
	 * reached.
	 *
	 * @param src       the data
	 * @param frequency the count of each symbol in src
	 * @return the symbols, in that order
	 */
	private static ArrayList <Integer> exhaustionOrder(byte[] src, int [] frequency)
	{
		ArrayList <Integer> exhausted_list = new ArrayList <Integer>();
		for (int i = 0; i < frequency.length; i++)
			if (frequency[i] == 0)
				exhausted_list.add(i);

		int [] f = frequency.clone();
		for (int i = 0; i < src.length; i++)
		{
			int j = src[i] & 0xFF;
			f[j]--;
			if (f[j] == 0)
				exhausted_list.add(j);
		}
		return exhausted_list;
	}

	/**
	 * The symbols in the order they are used up while coding src, first used up
	 * first (see {@link #exhaustionOrder}).
	 *
	 * @param src       the data
	 * @param frequency the count of each symbol in src (at most 256 symbols)
	 * @return table[rank] = symbol (a rank-to-symbol table)
	 */
	public static byte [] getFirstTable(byte[] src, int [] frequency)
	{
		ArrayList <Integer> exhausted_list = exhaustionOrder(src, frequency);
		byte [] first_table = new byte[frequency.length];
		for (int i = 0; i < frequency.length; i++)
			first_table[i] = (byte) (int) exhausted_list.get(i);
		return first_table;
	}

	/**
	 * The symbols in the reverse of the order they are used up while coding src,
	 * last used up first (see {@link #exhaustionOrder}).
	 *
	 * @param src       the data
	 * @param frequency the count of each symbol in src (at most 256 symbols)
	 * @return table[rank] = symbol (a rank-to-symbol table)
	 */
	public static byte [] getLastTable(byte[] src, int [] frequency)
	{
		ArrayList <Integer> exhausted_list = exhaustionOrder(src, frequency);
		byte [] last_table = new byte[frequency.length];
		int k = 0;
		for (int i = frequency.length - 1; i >= 0; i--)
			last_table[k++] = (byte) (int) exhausted_list.get(i);
		return last_table;
	}

	/**
	 * A series of tables that starts from {@link #getLastTable} and moves one
	 * symbol a place at a time to one end: the least or the most frequent symbol
	 * (as {@link #getAscendingTable} ranks them), to the front or to the back.
	 *
	 * @param src       the data
	 * @param frequency the count of each symbol in src (at most 256 symbols)
	 * @param greatest  move the most frequent symbol (else the least frequent)
	 * @param to_front  move it to the front (else to the back)
	 * @return the tables (rank-to-symbol), the starting table first and one more
	 *         for each place the symbol moves
	 */
	public static ArrayList <byte []> getTableSeries(byte[] src, int [] frequency, boolean greatest, boolean to_front)
	{
		byte [] ascending_table = getAscendingTable(frequency);
		byte    moving          = greatest ? ascending_table[ascending_table.length - 1] : ascending_table[0];
		byte [] table           = getLastTable(src, frequency);

		int place = 0;
		for (int i = 0; i < table.length; i++)
		{
			if (table[i] == moving)
			{
				place = i;
				break;
			}
		}

		ArrayList <byte []> result = new ArrayList <byte []> ();
		result.add(table.clone());
		int end = to_front ? 0 : table.length - 1;
		while (place != end)
		{
			int next     = to_front ? place - 1 : place + 1;
			table[place] = table[next];
			table[next]  = moving;
			place        = next;
			result.add(table.clone());
		}
		return result;
	}

	/**
	 * {@link #getTableSeries(byte[], int[], boolean, boolean)}, moving the
	 * least frequent symbol to the front.
	 *
	 * @param src       the data
	 * @param frequency the count of each symbol in src (at most 256 symbols)
	 * @return the tables (rank-to-symbol)
	 */
	public static ArrayList <byte []> getTableSeries(byte[] src, int [] frequency)
	{
		return getTableSeries(src, frequency, false, true);
	}

	/**
	 * {@link #getTableSeries(byte[], int[], boolean, boolean)}, moving the
	 * least frequent symbol to the back.
	 *
	 * @param src       the data
	 * @param frequency the count of each symbol in src (at most 256 symbols)
	 * @return the tables (rank-to-symbol)
	 */
	public static ArrayList <byte []> getTableSeries2(byte[] src, int [] frequency)
	{
		return getTableSeries(src, frequency, false, false);
	}

	/**
	 * {@link #getTableSeries(byte[], int[], boolean, boolean)}, moving the
	 * most frequent symbol to the front.
	 *
	 * @param src       the data
	 * @param frequency the count of each symbol in src (at most 256 symbols)
	 * @return the tables (rank-to-symbol)
	 */
	public static ArrayList <byte []> getTableSeries3(byte[] src, int [] frequency)
	{
		return getTableSeries(src, frequency, true, true);
	}

	/**
	 * {@link #getTableSeries(byte[], int[], boolean, boolean)}, moving the
	 * most frequent symbol to the back.
	 *
	 * @param src       the data
	 * @param frequency the count of each symbol in src (at most 256 symbols)
	 * @return the tables (rank-to-symbol)
	 */
	public static ArrayList <byte []> getTableSeries4(byte[] src, int [] frequency)
	{
		return getTableSeries(src, frequency, true, false);
	}

	/**
	 * The symbols 0 to n - 1, shuffled (Fisher-Yates).
	 *
	 * @param n    the number of symbols
	 * @param rand the random numbers to shuffle with
	 * @return a random permutation, rank-to-symbol
	 */
	private static byte[] shuffled(int n, java.util.Random rand)
	{
		byte[] table = new byte[n];
		for (int i = 0; i < n; i++)
			table[i] = (byte) i;
		for (int i = n - 1; i > 0; i--)
		{
			int j = rand.nextInt(i + 1);
			byte tmp = table[i];
			table[i] = table[j];
			table[j] = tmp;
		}
		return table;
	}

	/**
	 * A random permutation of the symbols -- a baseline that carries no
	 * information about the data, for comparison with frequency-driven orderings
	 * like {@link #getLastTable} and {@link #getDescendingTable}.
	 *
	 * @param frequency the count of each symbol (only its length is used)
	 * @return table[rank] = symbol
	 */
	public static byte[] getRandomTable(int frequency[])
	{
		return shuffled(frequency.length, new java.util.Random());
	}

	/**
	 * As {@link #getRandomTable(int[])}, repeatably from a seed.
	 *
	 * @param frequency the count of each symbol (only its length is used)
	 * @param seed      the seed for {@link java.util.Random}
	 * @return table[rank] = symbol
	 */
	public static byte[] getRandomTable(int frequency[], long seed)
	{
		return shuffled(frequency.length, new java.util.Random(seed));
	}

	/**
	 * A random order table from a seed: {@link #getRandomTable(int[], long)}'s
	 * permutation inverted to the symbol-to-rank form the order parameter of
	 * {@link #getIntervalValue(byte[], int[], byte[])} takes. The encoder (when
	 * searching seeds) and the decoder (from a stored seed) both call this, so
	 * they derive the same table.
	 *
	 * @param frequency the count of each symbol (only its length is used)
	 * @param seed      the seed
	 * @return table[symbol] = rank
	 */
  	public static byte[] getRandomOrderTable(int[] frequency, long seed)
  	{
  		byte[] rank_to_symbol = getRandomTable(frequency, seed);
  		byte[] symbol_to_rank = new byte[rank_to_symbol.length];
  		for (int rank = 0; rank < rank_to_symbol.length; rank++)
  		{
  			int symbol = rank_to_symbol[rank] & 0xFF;
  			symbol_to_rank[symbol] = (byte) rank;
  		}
  		return symbol_to_rank;
  	}

	/**
	 * As {@link #getRandomOrderTable(int[], long)}, with a one-byte seed (taken as
	 * unsigned, 0 to 255), so a decoder that stores only that byte rebuilds the
	 * same table.
	 *
	 * @param frequency the count of each symbol (only its length is used)
	 * @param seed      the seed
	 * @return table[symbol] = rank
	 */
  	public static byte[] getRandomOrderTable(int[] frequency, byte seed)
  	{
  		return getRandomOrderTable(frequency, (long) (seed & 0xFF));
  	}

	/**
	 * As {@link #getRandomOrderTable(int[], long)}, with a two-byte seed (sign
	 * extended, matching how {@code writeShort}/{@code readShort} round-trip it).
	 *
	 * @param frequency the count of each symbol (only its length is used)
	 * @param seed      the seed
	 * @return table[symbol] = rank
	 */
  	public static byte[] getRandomOrderTable(int[] frequency, short seed)
  	{
  		return getRandomOrderTable(frequency, (long) seed);
  	}

	// ---- Order-table coders ----------------------------------------------------------

	/**
	 * {@link ArithmeticMapper#getIntervalValue} with the probability space
	 * reordered: symbol v takes the slice at position order[v].
	 *
	 * @param src       the bytes to code
	 * @param frequency the count of each byte value in src
	 * @param order     table[symbol] = position (a symbol-to-rank table)
	 * @return {numerator, denominator} of the coded value
	 * @see #getArithmeticValues(BigInteger[], int[], int, byte[])
	 */
	public static BigInteger[] getIntervalValue(byte[] src, int[] frequency, byte[] order)
	{
		int[] f = new int[frequency.length];
		for (int i = 0; i < order.length; i++)
			f[order[i] & 0xFF] = frequency[i];
		byte[] reordered = new byte[src.length];
		for (int i = 0; i < src.length; i++)
			reordered[i] = order[src[i] & 0xFF];
		return ArithmeticMapper.getIntervalValue(reordered, f);
	}

	/**
	 * Decodes {@link #getIntervalValue(byte[], int[], byte[])}'s value.
	 *
	 * @param v         {numerator, denominator} of the coded value
	 * @param frequency the count of each byte value, as given to the encoder
	 * @param n         the number of bytes coded
	 * @param order     the order table the encoder used
	 * @return the decoded bytes
	 */
	public static byte[] getArithmeticValues(BigInteger[] v, int[] frequency, int n, byte[] order)
	{
		int[]  f             = new int[frequency.length];
		byte[] inverse_order = new byte[order.length];
		for (int i = 0; i < order.length; i++)
		{
			int j = order[i] & 0xFF;
			f[j]             = frequency[i];
			inverse_order[j] = (byte) i;
		}
		byte[] reordered = ArithmeticMapper.getArithmeticValues(v, f, n);
		byte[] value     = new byte[n];
		for (int i = 0; i < n; i++)
			value[i] = inverse_order[reordered[i] & 0xFF];
		return value;
	}

	/**
	 * A fast approximate coded value, for scoring order tables in a search (hill
	 * climbing, annealing) rather than for coding. Runs integer arithmetic coding
	 * with E1/E2/E3 (bit-plus-follow) renormalization over the reordered
	 * probability space, as {@link #getIntervalValue(byte[], int[], byte[])}
	 * does exactly, and keeps only the leading 52 bits.
	 * <p>
	 * For any segment that codes to more than about 52 bits (essentially all
	 * real ones), this agrees with the exact value to full double precision.
	 *
	 * @param src       the bytes to code
	 * @param frequency the count of each byte value in src
	 * @param order     table[symbol] = position
	 * @return the coded value as a double in [0, 1)
	 */
	public static double getApproxOffsetFastOrdered(byte[] src, int[] frequency, byte[] order)
	{
		int[] f = new int[frequency.length];
		for (int i = 0; i < order.length; i++)
		{
			int j = order[i];
			if (j < 0) j += 256;
			f[j] = frequency[i];
		}
		int n = src.length;

		int[] s = new int[f.length];
		int   m = 0;
		for (int i = 0; i < f.length; i++) { s[i] = m; m += f[i]; }

		final long TOP  = 0x100000000L;
		final long HALF = 0x80000000L;
		final long QTR  = 0x40000000L;
		final long TQTR = 0xC0000000L;

		long low = 0L, high = TOP;
		int  pending = 0;

		LeadingBits bits = new LeadingBits();

		for (int i = 0; i < n; i++)
		{
			int j = src[i];
			if (j < 0) j += 256;
			j = order[j];
			if (j < 0) j += 256;

			long range    = high - low;
			long new_low  = low + (range * s[j]) / m;
			long new_high = (s[j] + f[j] == m) ? high : low + (range * (long)(s[j] + f[j])) / m;
			low  = new_low;
			high = new_high;

			for (;;)
			{
				if (high <= HALF)
				{
					bits.append(0);
					for (int p = 0; p < pending; p++) bits.append(1);
					pending = 0;
					low <<= 1; high <<= 1;
				}
				else if (low >= HALF)
				{
					bits.append(1);
					for (int p = 0; p < pending; p++) bits.append(0);
					pending = 0;
					low = (low - HALF) << 1; high = (high - HALF) << 1;
				}
				else if (low >= QTR && high <= TQTR)
				{
					pending++;
					low = (low - QTR) << 1; high = (high - QTR) << 1;
				}
				else break;
			}

			f[j]--;
			m--;
			for (int k = j + 1; k < s.length; k++) s[k]--;
		}

		pending++;
		if (low < QTR)
		{
			bits.append(0);
			for (int p = 0; p < pending; p++) bits.append(1);
		}
		else
		{
			bits.append(1);
			for (int p = 0; p < pending; p++) bits.append(0);
		}

		return bits.toApproxOffset();
	}

	/**
	 * Keeps only the leading {@link #MAX_BITS} bits appended to it -- enough for
	 * full double precision -- and discards the rest. Used only by
	 * {@link ProbabilisticMapper#getApproxOffsetFastOrdered}.
	 */
	private static final class LeadingBits
	{
		/** An empty buffer. */
		LeadingBits() {}

		/**
		 * The bits kept: a double's mantissa precision.
		 */
		static final int MAX_BITS = 52; // matches double's mantissa precision
		/**
		 * The bits kept, as an integer.
		 */
		long accum = 0L;
		/**
		 * How many bits are kept.
		 */
		int  count = 0;

		/**
		 * Appends a bit, if fewer than {@link #MAX_BITS} are kept.
		 *
		 * @param bit 0 or 1
		 */
		void append(int bit)
		{
			if (count < MAX_BITS)
			{
				accum = (accum << 1) | bit;
				count++;
			}
		}

		/**
		 * The value of the bits kept.
		 *
		 * @return the bits kept, as a binary fraction in [0, 1)
		 */
		double toApproxOffset()
		{
			return (count == 0) ? 0.0 : (double) accum / (double) (1L << count);
		}
	}
}
