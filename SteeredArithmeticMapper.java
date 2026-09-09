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
	// =========================================================================
	// Exact fraction arithmetic reduced via GCD at every operation to keep numbers 
	// as small as possible.  
	// =========================================================================
	public static final class Frac
	{
		public final BigInteger n, d; 

		public Frac(BigInteger numerator, BigInteger denominator)
		{
			// We might want to check if the denominator is zero and
			// throw an exception, but it also might be useful as an 
			// equivalence for infinity.
			
			if(denominator.signum() < 0) 
			{ 
				// This moves the negative sign to the numerator if the numerator was positive,
				// and makes it a simple positive fraction if the numerator was negative.
				numerator   = numerator.negate(); 
				denominator = denominator.negate(); 
			}
			
			if(numerator.signum() == 0) 
			{ 
				// No matter what denominator was passed to the constructor,
				// we turn it into a one if the numerator was zero.
				denominator = BigInteger.ONE; 
			}
			else
			{
				// We'll assume this is a proper fraction and use the denominator
				// to get the greatest common divisor, but it will still work if
				// the numerator is actually larger since java will check which
				// is greater when it does the operation.
				BigInteger divisor = denominator.gcd(numerator);
				if(!divisor.equals(BigInteger.ONE)) 
				{ 
					numerator   = numerator.divide(divisor); 
					denominator = denominator.divide(divisor); 
				}
			}
			
			this.n   = numerator; 
			this.d = denominator;
		}

		public static Frac of(long n, long d) 
		{ 
			return new Frac(BigInteger.valueOf(n), BigInteger.valueOf(d)); 
		}
		
		public static final Frac ZERO = Frac.of(0, 1);
		public static final Frac ONE  = Frac.of(1, 1);
		public static final Frac HALF = Frac.of(1, 2);

		public Frac add(Frac frac) 
		{ 
			return new Frac(n.multiply(frac.d).add(frac.n.multiply(d)), d.multiply(frac.d)); 
		}
		
		public Frac sub(Frac frac) 
		{ 
			return new Frac(n.multiply(frac.d).subtract(frac.n.multiply(d)), d.multiply(frac.d)); 
		}
		
		public Frac mul(Frac frac) 
		{ 
			return new Frac(n.multiply(frac.n), d.multiply(frac.d)); 
		}
		
		public Frac div(Frac frac) 
		{ 
			return new Frac(n.multiply(frac.d), d.multiply(frac.n)); 
		}
		
		public Frac mul(long k)  
		{ 
			return new Frac(n.multiply(BigInteger.valueOf(k)), d); 
		}
		
		public Frac negate()     
		{ 
			return new Frac(n.negate(), d); 
		}
		
		public Frac abs()        
		{ 
			return n.signum() < 0 ? negate() : this; 
		}

		public int compareTo(Frac frac) 
		{ 
			return n.multiply(frac.d).compareTo(frac.n.multiply(d)); 
		}
		
		public boolean lt(Frac frac)  
		{ 
			return compareTo(frac) < 0; 
		}
		
		public boolean le(Frac frac)  
		{
			return compareTo(frac) <= 0; 
		}
		
		public boolean gt(Frac frac)  
		{ 
			return compareTo(frac) > 0; 
		}
		
		public boolean eq(Frac frac)  
		{ 
			return n.equals(frac.n) && d.equals(frac.d); 
		}

		@Override public String toString() 
		{ return n + "/" + d; 
		}
		
		public double toDouble() 
		{ 
			return new java.math.BigDecimal(n).divide(new java.math.BigDecimal(d), 40, java.math.RoundingMode.HALF_EVEN).doubleValue(); 
		}
	}

	// =========================================================================
	// Achievable subset sums: which totals are reachable by choosing some
	// subset of `counts` (each used whole or not at all). BitSet-backed DP,
	// O(K * S) where K = counts.length, S = sum(counts) -- fast even for
	// S up to a few thousand.
	// =========================================================================
	/*
	public static BitSet achievableSubsetSums(int[] counts)
	{
		int total = 0;
		for (int c : counts) total += c;
		BitSet reachable = new BitSet(total + 1);
		reachable.set(0);
		for (int c : counts)
		{
			if (c == 0) continue;
			BitSet shifted = new BitSet(total + 1);
			for (int i = reachable.nextSetBit(0); i >= 0; i = reachable.nextSetBit(i + 1))
				if (i + c <= total) shifted.set(i + c);
			reachable.or(shifted);
		}
		return reachable;
	}
    */

	// This returns a boolean map of the possible sums that can be produced
	// by a frequency table, using all or none of those frequencies. 
	// Bitset works better than an array of booleans because it saves memory 
	// (1 bit instead of 1 byte per value) and speeds up processing by allowing 
	// the use of bitwise operations.
	public static BitSet achievableSubsetSums(int[] frequency)
	{
		int sum = 0;
		for(int i = 0; i < frequency.length; i++)
			sum += frequency[i];

		BitSet reachable = new BitSet(sum + 1);
		reachable.set(0);

		for(int i = 0; i < frequency.length; i++)
		{
			int k = frequency[i];
			if(k == 0) 
				continue;
			BitSet shifted = new BitSet(sum + 1);
			for(int j = reachable.nextSetBit(0); j >= 0; j = reachable.nextSetBit(j + 1))
				if (j + k <= sum)
					shifted.set(j + k);
			reachable.or(shifted);
		}

		return reachable;
	}
    
	// =========================================================================
	// Candidate s_j values (cumulative count before the real next symbol)
	// consistent with the target's residual falling inside its slice,
	// ordered closest-to-centered first (keeps future steps more flexible).
	// The residual is defined as (target - offset) / range.
	// =========================================================================
	private static final class Candidates
	{
		final int[] values;      // candidate s_j values, best first
		final int[] otherSyms;   // the OTHER remaining distinct symbol values (excluding realSymbol)
		Candidates(int[] values, int[] otherSyms) 
		{ 
			this.values = values; 
			this.otherSyms = otherSyms; 
		}
	}

	private static Candidates getCandidates(int realSymbol, int[] f, int m, Frac residual)
	{
		ArrayList<Integer> otherList = new ArrayList<>();
		for (int s = 0; s < f.length; s++)
			if (f[s] > 0 && s != realSymbol) otherList.add(s);
		int[] otherSyms = new int[otherList.size()];
		int[] otherCounts = new int[otherList.size()];
		for (int i = 0; i < otherSyms.length; i++) { otherSyms[i] = otherList.get(i); otherCounts[i] = f[otherList.get(i)]; }

		BitSet sums = achievableSubsetSums(otherCounts);
		int fJ = f[realSymbol];
		// required range for s_j: residual*m - f_j < s_j <= residual*m
		Frac targetPos = residual.mul(m);
		// s_j must be an integer in (targetPos - f_j, targetPos]; scan the
		// achievable-sum bitset over the (small) integer window that could
		// possibly satisfy this, rather than every reachable sum overall.
		BigInteger tpFloorBI = targetPos.n.divide(targetPos.d); // floor-ish; refine with exact compare below
		int hi = tpFloorBI.intValue() + 1;
		int lo = hi - fJ - 1;
		ArrayList<Integer> cand = new ArrayList<>();
		for (int s = Math.max(0, lo); s <= hi && s <= sums.length(); s++)
		{
			if (!sums.get(s)) continue;
			Frac sFrac = Frac.of(s, 1);
			// condition: targetPos - f_j < s <= targetPos
			if (sFrac.gt(targetPos.sub(Frac.of(fJ, 1))) && sFrac.le(targetPos))
				cand.add(s);
		}
		// order by closeness of resulting position-within-slice to center (1/2)
		cand.sort((a, b) -> {
			Frac loA = Frac.of(a, m), hiA = Frac.of(a + fJ, m);
			Frac posA = residual.sub(loA).div(hiA.sub(loA));
			Frac scoreA = posA.sub(Frac.HALF).abs();
			Frac loB = Frac.of(b, m), hiB = Frac.of(b + fJ, m);
			Frac posB = residual.sub(loB).div(hiB.sub(loB));
			Frac scoreB = posB.sub(Frac.HALF).abs();
			return scoreA.compareTo(scoreB);
		});
		int[] values = new int[cand.size()];
		for (int i = 0; i < values.length; i++) values[i] = cand.get(i);
		return new Candidates(values, otherSyms);
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
		public final Frac off, rng;
		public final long backtracks;
		SteerResult(int[][] orderings, Frac off, Frac rng, long backtracks)
		{ this.orderings = orderings; this.off = off; this.rng = rng; this.backtracks = backtracks; }
	}

	/** Backtracking search: finds a sequence of per-step orderings steering
	 *  the final interval to contain `target` exactly. Returns null if no
	 *  path is found within maxBacktracks or deadlineNanos (whichever
	 *  comes first) -- callers running several attempts in parallel should
	 *  treat null as "this attempt didn't succeed in budget," not
	 *  necessarily "impossible." */
	public static SteerResult steerEncode(int[] src, int[] freq, Frac target, long maxBacktracks, long deadlineNanos)
	{
		int n = src.length;
		int[][] orderings = new int[n][];

		// explicit stack frames
		final class Frame
		{
			int[] f; int m; Frac off, rng, residual; Candidates cand; int idx;
			Frame(int[] f, int m, Frac off, Frac rng, Frac residual, Candidates cand)
			{ this.f=f; this.m=m; this.off=off; this.rng=rng; this.residual=residual; this.cand=cand; this.idx=0; }
		}

		ArrayDeque<Frame> stack = new ArrayDeque<>();
		int[] f0 = Arrays.copyOf(freq, freq.length);
		int m0 = 0; for (int c : f0) m0 += c;
		int i = 0;
		Candidates cand0 = getCandidates(src[0], f0, m0, target);
		stack.push(new Frame(f0, m0, Frac.ZERO, Frac.ONE, target, cand0));
		long backtracks = 0;

		while (true)
		{
			if (i == n)
			{
				Frame last = null; // shouldn't reach here with empty handling below
				break;
			}
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

			int sJ = top.cand.values[top.idx];
			top.idx++;
			int j = src[i];
			int fJ = top.f[j];
			orderings[i] = orderingForChoice(j, top.cand.otherSyms, top.f, sJ);

			Frac newOff = top.off.add(top.rng.mul(Frac.of(sJ, top.m)));
			Frac newRng = top.rng.mul(Frac.of(fJ, top.m));
			Frac lo = Frac.of(sJ, top.m), hi = Frac.of(sJ + fJ, top.m);
			Frac newResidual = top.residual.sub(lo).div(hi.sub(lo));
			int[] f2 = Arrays.copyOf(top.f, top.f.length);
			f2[j]--;
			i++;
			if (i == n) return new SteerResult(orderings, newOff, newRng, backtracks);
			Candidates newCand = getCandidates(src[i], f2, top.m - 1, newResidual);
			stack.push(new Frame(f2, top.m - 1, newOff, newRng, newResidual, newCand));
		}
		return null; // unreachable
	}

	/** Cheap, direct replay decode -- no search needed, since the stored
	 *  orderings fully determine which symbol's slice the (already-known)
	 *  target falls into at each step. */
	public static int[] steerDecode(int[][] orderings, int[] freq, Frac target, int n)
	{
		int[] f = Arrays.copyOf(freq, freq.length);
		int m = 0; for (int c : f) m += c;
		Frac residual = target;
		int[] decoded = new int[n];
		for (int i = 0; i < n; i++)
		{
			int[] perm = orderings[i];
			int cum = 0, chosen = -1, sJ = -1;
			Frac targetPos = residual.mul(m);
			for (int sym : perm)
			{
				int fSym = f[sym];
				if (Frac.of(cum, 1).le(targetPos) && targetPos.lt(Frac.of(cum + fSym, 1)))
				{ chosen = sym; sJ = cum; break; }
				cum += fSym;
			}
			if (chosen < 0) throw new IllegalStateException("decode failed at step " + i);
			int fJ = f[chosen];
			Frac lo = Frac.of(sJ, m), hi = Frac.of(sJ + fJ, m);
			residual = residual.sub(lo).div(hi.sub(lo));
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
}
