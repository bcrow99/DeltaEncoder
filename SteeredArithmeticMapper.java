import java.math.BigInteger;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Steers an arithmetic-coding interval's offset to land exactly on a
 * target fraction, using a per-step (rather than one fixed, whole-
 * sequence) ordering choice -- ported from the Python prototype
 * (steered_arithmetic.py) to take advantage of Java's true concurrent
 * threads (no GIL) for the search.
 *
 * Deliberately ignores compression efficiency by design: the per-step
 * orderings needed to steer the offset are the expensive part, and this
 * class does not try to minimize their cost, only to demonstrate the
 * steering itself works correctly, end to end, at a scale (up to 32x32 =
 * 1024 symbols) meant to actually exercise multi-threading.
 *
 * ============================================================================
 * HOW THIS WORKS (see steered_arithmetic.py's module docstring for the full
 * derivation -- summarized here)
 * ============================================================================
 * Standard "sampling without replacement" arithmetic coding narrows a
 * current interval [off, off+rng) at each step by splitting it into
 * consecutive slices, one per remaining symbol, each proportional to that
 * symbol's remaining count -- the width of the real next symbol's slice
 * doesn't depend on ordering, but WHERE that slice sits within the parent
 * interval does. Treating the target fraction as though it were being
 * decoded: at each step, the target's position rescaled into the current
 * interval's own [0,1) coordinate ("residual") must fall inside the real
 * next symbol's slice. A slice starting at cumulative count s_j works if
 * s_j <= residual*m < s_j+f_j, and s_j must be an ACHIEVABLE SUBSET SUM of
 * the other remaining symbols' counts. Multiple candidates often exist;
 * try each (closest-to-centered first), recurse, backtrack on dead ends.
 * Success through every step guarantees the final interval contains the
 * target exactly.
 *
 * ============================================================================
 * WHAT GETS STORED (the expensive part)
 * ============================================================================
 * For each step, the complete ordering of that step's remaining distinct
 * symbol values is stored explicitly, since the decoder needs the full
 * ordering (not just the winning s_j) to correctly locate which symbol's
 * slice the residual falls into once multiple symbols share equal counts.
 */
public class SteeredArithmeticMapper
{
	// This returns a boolean map of the possible sums that can be produced
	// from a frequency table, using all or none of those frequencies.
	// Bitset works better than an array of booleans because it saves memory
	// (1 bit instead of 1 byte per value) and speeds up processing by allowing
	// the use of bitwise operations.
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

	// =========================================================================
	// Symbol with a non-zero frequency along with
	// s_j values (cumulative frequency sum before the symbol)
	// and other non-zero frequency symbols in the alphabet.
	// =========================================================================
	private static final class Candidate
	{
		// Symbol with a non-zero frequency.
		final int   symbol;

		// Remaining symbol values with a non-zero frequency, excluding symbol.
		final int[] other_symbol;

		// Candidate values for s_j: the achievable sums (of some subset of
		// other_symbol's remaining counts) that place the target's current
		// position (the residual) inside the resulting interval for `symbol`.
		// Ordered by encoding cost, cheapest first: for each candidate, the
		// number of other_symbol values it implies come before `symbol` is
		// reconstructed, and candidates are ranked by log2(C(k-1, that count))
		// -- smallest when that count is near 0 or near k-1, largest near
		// the midpoint. This favors choices that are cheap to store later
		// (a near-empty or near-full "before" group needs few bits to
		// specify), not any property of where the resulting interval sits.
		final int[] sum;

		Candidate(int symbol, int[] sum, int[] other_symbol)
		{
			this.symbol       = symbol;
			this.sum          = sum;
			this.other_symbol = other_symbol;
		}
	}

	/** Reconstructs one concrete ordering (permutation) consistent with a
	 *  chosen s_j: finds an EXACT subset of otherSyms summing to sJ via
	 *  proper subset-sum DP with backtracking (not greedy -- greedy
	 *  largest-first can fail to find a valid subset even when one
	 *  exists, e.g. counts=[5,4,3] targeting 7: greedy takes 5 first and
	 *  gets stuck, even though {4,3} works). The subset found is placed
	 *  before realSymbol; the rest placed after. */
	public static int[] orderingForChoicePublic(int realSymbol, int[] otherSyms, int[] f, int sJ)
	{
		return orderingForChoice(realSymbol, otherSyms, f, sJ);
	}

	private static int[] orderingForChoice(int realSymbol, int[] otherSyms, int[] f, int sJ)
	{
		int k = otherSyms.length;
		int[] counts = new int[k];
		for (int i = 0; i < k; i++) counts[i] = f[otherSyms[i]];

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
			throw new IllegalStateException("subset-sum reconstruction failed: sJ=" + sJ
				+ " not reachable from counts=" + Arrays.toString(counts) + " (candidate generation bug)");

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

		int[] result = new int[k + 1];
		int p = 0;
		for (int i = 0; i < k; i++) if (used[i]) result[p++] = otherSyms[i];
		result[p++] = realSymbol;
		for (int i = 0; i < k; i++) if (!used[i]) result[p++] = otherSyms[i];
		return result;
	}

	public static final class SteerResult
	{
		public final int[][] orderings;
		public final FractionMapper.BigFraction off, rng;
		public final long backtracks;
		SteerResult(int[][] orderings, FractionMapper.BigFraction off, FractionMapper.BigFraction rng, long backtracks)
		{ this.orderings = orderings; this.off = off; this.rng = rng; this.backtracks = backtracks; }
	}

	/** Cheap, direct replay decode -- no search needed, since the stored
	 *  orderings fully determine which symbol's slice the (already-known)
	 *  target falls into at each step. */
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

	// =========================================================================
	// Candidate search: tries s_j values in order of CHEAPEST-TO-ENCODE
	// first (before-group size closest to 0 or k-1, where
	// log2(C(k-1,pos)) is smallest), rather than "closest to centered"
	// within the resulting slice. An earlier centering-based heuristic
	// was tried and measured directly against this one across multiple
	// random seeds and segment sizes: this cost-based ordering was BOTH
	// cheaper AND more reliable (fewer backtracks) every time, never a
	// tradeoff either way, so the centering version was removed rather
	// than kept as an alternative.
	// =========================================================================
	private static double log2(double x) { return Math.log(x) / Math.log(2); }
	private static double log2Factorial(int n) { double b = 0; for (int k = 2; k <= n; k++) b += log2(k); return b; }
	public static double log2BinomialCoeff(int n, int k)
	{
		if (k < 0 || k > n) return 0;
		return log2Factorial(n) - log2Factorial(k) - log2Factorial(n - k);
	}

	/** Same subset-sum DP as orderingForChoice, but only reports HOW MANY
	 *  items end up in the "before" group -- used, together with
	 *  log2BinomialCoeff, to score candidates by encoding cost. Public
	 *  since RenormalizingSteerCoder's fixed-precision search reuses this
	 *  same cost function rather than duplicating it. */
	public static int reconstructBeforeCount(int[] counts, int sJ)
	{
		int k = counts.length;
		boolean[][] reachable = new boolean[k + 1][sJ + 1];
		reachable[0][0] = true;
		for (int i = 0; i < k; i++)
		{
			int c = counts[i];
			for (int s = 0; s <= sJ; s++)
			{
				if (!reachable[i][s]) continue;
				reachable[i + 1][s] = true;
				if (s + c <= sJ) reachable[i + 1][s + c] = true;
			}
		}
		if (!reachable[k][sJ]) return 0;
		boolean[] used = new boolean[k];
		int s = sJ, count = 0;
		for (int i = k - 1; i >= 0; i--)
		{
			int c = counts[i];
			if (s - c >= 0 && reachable[i][s - c]) { used[i] = true; s -= c; count++; }
		}
		return count;
	}

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

	/** Backtracking search: finds a sequence of per-step orderings steering
	 *  the final interval to contain `target` exactly. Returns null if no
	 *  path is found within maxBacktracks or deadlineNanos (whichever
	 *  comes first) -- callers running several attempts in parallel should
	 *  treat null as "this attempt didn't succeed in budget," not
	 *  necessarily "impossible." */
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

	// =========================================================================
	// COMBINADIC (combinatorial number system): maps a k-subset of
	// {0,...,n-1} to/from a unique integer in [0, C(n,k)). Used to encode
	// the "before" subset in log2(C(k-1,pos)) bits instead of a flat
	// (k-1)-bit mask. Self-tested as a full bijection for small n.
	// =========================================================================
	public static long binomial(int n, int k)
	{
		if (k < 0 || k > n) return 0;
		if (k > n - k) k = n - k;
		long result = 1;
		for (int i = 0; i < k; i++) result = result * (n - i) / (i + 1);
		return result;
	}

	public static long combinadicEncode(int[] sortedChosen)
	{
		long rank = 0;
		for (int i = 0; i < sortedChosen.length; i++) rank += binomial(sortedChosen[i], i + 1);
		return rank;
	}

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

	// =========================================================================
	// SIMPLEST FRACTION IN INTERVAL: finds the fraction with the smallest
	// denominator strictly inside (loN/loD, hiN/hiD), via iterative
	// floor/reciprocal (Stern-Brocot) descent. Ported from a debugged,
	// validated ArithmeticMapper.java (the user's own codebase) rather than
	// reimplemented from scratch here -- an earlier from-scratch attempt at
	// this exact algorithm had real bugs (every test case landed outside
	// the target interval) and was abandoned in favor of the cruder "use
	// the interval's lower bound directly" shortcut previously used in
	// encodeCSequence. This replaces that shortcut, tightening the
	// c-sequence codeword toward the true minimum.
	//
	// Written iteratively rather than recursively (accumulating floor
	// terms and combining them afterward) since the recursive form can
	// require depth proportional to the number of continued-fraction
	// terms -- unbounded in principle for adversarial inputs.
	//
	// NOTE: finds a fraction STRICTLY between lo and hi (open interval),
	// whereas our actual valid interval is half-open [off, off+rng) --
	// meaning in the rare case where `off` itself happens to already be
	// the simplest possible fraction, this won't select it (a fraction
	// arbitrarily close to it, just above, will be chosen instead). This
	// is a negligible, bounded loss in practice, not a correctness issue:
	// any fraction actually returned is still checked to fall strictly
	// inside (and therefore within) the valid interval, so decode remains
	// correct either way.
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
			BigInteger newLoN = hiD,  newLoD = hiFracN;
			BigInteger newHiN = loD,  newHiD = loFracN;
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

	/** Floor division n/d for d &gt; 0 (BigInteger.divide() truncates toward zero, not floor). */
	private static BigInteger floorDiv(BigInteger n, BigInteger d)
	{
		BigInteger[] qr = n.divideAndRemainder(d);
		if (qr[1].signum() != 0 && n.signum() < 0)
			return qr[0].subtract(BigInteger.ONE);
		return qr[0];
	}

	// =========================================================================
	// C-SEQUENCE CODER: ordinary, NON-steered exact arithmetic coding of a
	// plain integer sequence. Used to optimally encode "which symbol is
	// real at each step" -- mathematically identical to running normal
	// arithmetic coding on the original data itself, since a symbol's
	// probability of being real is exactly its own remaining count over
	// the remaining total. This is NOT extra steering overhead; it's the
	// same baseline cost any compressor pays for the data.
	//
	// NOTE: uses the interval's own lower bound as the codeword rather
	// than searching for the absolute simplest fraction within it --
	// simpler to get right; leaves a small, bounded number of extra bits
	// on the table versus the true optimum (see the "rounding tax"
	// discussion elsewhere in this project). A verified simplest-
	// fraction-in-interval implementation would let this be tightened.
	// =========================================================================
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

		// Tighten the codeword using the debugged simplestFractionInInterval
		// (see the section above) instead of returning `off` directly --
		// finds the fraction with the smallest denominator anywhere within
		// the valid interval, not just its lower bound.
		BigInteger hiN = offN.multiply(rngD).add(rngN.multiply(offD));
		BigInteger hiD = offD.multiply(rngD);
		return simplestFractionInInterval(offN, offD, hiN, hiD);
	}

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

	// =========================================================================
	// COMPRESSED FORMAT: real, working serialization combining
	// arithmetic-coded "which symbol" (encodeCSequence) with combinadic-
	// encoded "which subset before it" (binomial/combinadicEncode) --
	// verified end to end (compress -> decompress -> steerDecode
	// reproduces the original data exactly) at 32x32 (1024-symbol) scale.
	// =========================================================================
	private static final class BitWriter
	{
		byte[] buf = new byte[64];
		int bitLen = 0;
		void ensure(int extraBits) { int needed=(bitLen+extraBits+7)/8; if(needed>buf.length) buf=Arrays.copyOf(buf, Math.max(needed, buf.length*2)); }
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
		byte[] toBytes() { return Arrays.copyOf(buf, (bitLen + 7) / 8); }
	}

	private static final class BitReader
	{
		final byte[] buf; int bitPos = 0;
		BitReader(byte[] buf) { this.buf = buf; }
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

	private static int bitsNeeded(int numValues) { return numValues <= 1 ? 0 : 32 - Integer.numberOfLeadingZeros(numValues - 1); }

	public static final class Compressed
	{
		public final BigInteger cSeqN, cSeqD;
		public final byte[] subsetData;
		public final int n;
		Compressed(BigInteger n_, BigInteger d_, byte[] subsetData, int n)
		{ this.cSeqN = n_; this.cSeqD = d_; this.subsetData = subsetData; this.n = n; }

		public int totalBits()
		{
			int cBytes = (cSeqN.signum()==0?1:(cSeqN.bitLength()+1+7)/8) + (cSeqD.signum()==0?1:(cSeqD.bitLength()+1+7)/8);
			return cBytes * 8 + subsetData.length * 8;
		}
	}

	/** Compresses orderings (from steerEncode) plus the original data into a
	 *  compact Compressed object. */
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

	/** Decompresses back into the exact orderings[][] shape steerDecode
	 *  (the ORIGINAL, untouched method) expects. */
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

}
