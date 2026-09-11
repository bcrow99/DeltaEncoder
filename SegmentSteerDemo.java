import java.math.BigInteger;
import java.util.*;
import java.util.concurrent.*;

/**
 * Compresses a square (dimension x dimension) segment of symbol data via
 * SteeredArithmeticMapper's target-steering scheme, using true concurrent
 * Java threads (up to a configurable core count) to try several candidate
 * target fractions in parallel within a time budget -- since not every
 * (histogram, sequence, target) combination admits an exact steering path
 * (confirmed empirically in the Python prototype this was ported from),
 * trying several candidates concurrently and taking whichever succeeds
 * first makes good use of true hardware parallelism where Python's GIL
 * would have serialized the same attempts.
 *
 * compressSegment() returns the winning target, its per-step orderings,
 * and timing/cost stats. decompressSegment() replays them (cheap, no
 * search) to reconstruct the original data, and the demo verifies the
 * result matches exactly.
 */
public class SegmentSteerDemo
{
	public static final class CompressResult
	{
		public final boolean success;
		public final FractionMapper.BigFraction target;
		public final int[][] orderings;
		public final long backtracks;
		public final long elapsedMillis;
		public final double costBits;
		CompressResult(boolean success, FractionMapper.BigFraction target, int[][] orderings,
		               long backtracks, long elapsedMillis, double costBits)
		{
			this.success = success; this.target = target; this.orderings = orderings;
			this.backtracks = backtracks; this.elapsedMillis = elapsedMillis; this.costBits = costBits;
		}
	}

	/**
	 * Compresses a `dimension x dimension` segment (flattened, row-major,
	 * values 0..alphabetSize-1) by racing several candidate target
	 * fractions across up to `maxThreads` concurrent Java threads, each
	 * with its own backtracking search, stopping as soon as any one
	 * succeeds or the overall time budget expires.
	 *
	 * @param segment       flattened dimension*dimension symbol values
	 * @param freq          histogram over the alphabet (index = symbol value)
	 * @param candidateTargets  target fractions to try (tried across threads)
	 * @param maxThreads    hardware threads to use (bounded by available cores)
	 * @param budgetSeconds overall wall-clock budget across all attempts
	 * @param maxBacktracksPerAttempt  per-attempt backtrack ceiling
	 */
	public static CompressResult compressSegment(int[] segment, int[] freq,
	                                              List<FractionMapper.BigFraction> candidateTargets,
	                                              int maxThreads, double budgetSeconds,
	                                              long maxBacktracksPerAttempt) throws InterruptedException
	{
		int nThreads = Math.max(1, Math.min(maxThreads, Runtime.getRuntime().availableProcessors()));
		nThreads = Math.min(nThreads, Math.max(1, candidateTargets.size()));
		ExecutorService pool = Executors.newFixedThreadPool(nThreads);

		long startNanos = System.nanoTime();
		long deadlineNanos = startNanos + (long) (budgetSeconds * 1_000_000_000L);

		List<Future<Object[]>> futures = new ArrayList<>();
		for (FractionMapper.BigFraction target : candidateTargets)
		{
			futures.add(pool.submit(() -> {
				SteeredArithmeticMapper.SteerResult r =
					SteeredArithmeticMapper.steerEncodeCheapBiased(segment, freq, target, maxBacktracksPerAttempt, deadlineNanos);
				return new Object[]{ target, r };
			}));
		}

		Object[] winner = null;
		// poll futures as they complete; cancel the rest as soon as one succeeds
		while (!futures.isEmpty())
		{
			for (Iterator<Future<Object[]>> it = futures.iterator(); it.hasNext(); )
			{
				Future<Object[]> fut = it.next();
				if (fut.isDone())
				{
					it.remove();
					try
					{
						Object[] res = fut.get();
						if (res[1] != null) { winner = res; }
					}
					catch (ExecutionException e)
					{
						// A genuinely unexpected failure (not just "this
						// target wasn't reachable in budget," which returns
						// null normally) -- report concisely and keep
						// waiting on the other concurrent attempts.
						System.out.println("  [attempt threw unexpectedly] " + e.getCause());
					}
				}
			}
			if (winner != null) break;
			if (System.nanoTime() > deadlineNanos) break;
			Thread.sleep(20);
		}
		pool.shutdownNow();

		long elapsedMillis = (System.nanoTime() - startNanos) / 1_000_000;
		if (winner == null)
			return new CompressResult(false, null, null, -1, elapsedMillis, -1);

		FractionMapper.BigFraction target = (FractionMapper.BigFraction) winner[0];
		SteeredArithmeticMapper.SteerResult r = (SteeredArithmeticMapper.SteerResult) winner[1];
		double bits = SteeredArithmeticMapper.serializedOrderingBits(r.orderings);
		return new CompressResult(true, target, r.orderings, r.backtracks, elapsedMillis, bits);
	}

	public static int[] decompressSegment(int[][] orderings, int[] freq, FractionMapper.BigFraction target, int n)
	{
		return SteeredArithmeticMapper.steerDecode(orderings, freq, target, n);
	}

	// =========================================================================
	// Demo / self-test
	// =========================================================================
	public static void main(String[] args) throws Exception
	{
		int dimension = (args.length >= 1) ? Integer.parseInt(args[0]) : 8;
		double budgetSeconds = (args.length >= 2) ? Double.parseDouble(args[1]) : 60.0;
		int maxThreads = (args.length >= 3) ? Integer.parseInt(args[2]) : 16;

		int n = dimension * dimension;
		System.out.println("Segment dimension: " + dimension + "x" + dimension + " (" + n + " symbols)");
		System.out.println("Time budget: " + budgetSeconds + "s   max threads: " + maxThreads
			+ "   available cores: " + Runtime.getRuntime().availableProcessors());

		// Build a realistic delta-like symbol distribution (geometric-ish,
		// resembling real image delta magnitudes) and a pseudo-random
		// sequence drawn from it, matching the style used throughout this
		// exploration.
		Random rng = new Random(11);
		int alphabetSize = 16;
		int[] freq = new int[alphabetSize];
		int[] weights = new int[alphabetSize];
		int totalWeight = 0;
		for (int k = 0; k < alphabetSize; k++) { weights[k] = Math.max(1, (int) Math.round(200 * Math.pow(0.6, k))); totalWeight += weights[k]; }
		// scale weights down/up so they sum close to n, then adjust
		int[] segment = new int[n];
		ArrayList<Integer> pool = new ArrayList<>();
		for (int k = 0; k < alphabetSize; k++)
		{
			int count = Math.max(1, (int) Math.round((double) weights[k] / totalWeight * n));
			for (int c = 0; c < count; c++) pool.add(k);
		}
		while (pool.size() < n) pool.add(rng.nextInt(alphabetSize));
		while (pool.size() > n) pool.remove(pool.size() - 1);
		Collections.shuffle(pool, rng);
		for (int i = 0; i < n; i++) { segment[i] = pool.get(i); freq[segment[i]]++; }

		int distinctUsed = 0;
		for (int f : freq) if (f > 0) distinctUsed++;
		System.out.println("Alphabet size (used): " + distinctUsed);
		System.out.println("Histogram: " + Arrays.toString(freq));

		// Candidate targets: a family of simple fractions (denominators up
		// to 20), mirroring the table used in the original GetOffset.java
		// exploration this grew out of.
		List<FractionMapper.BigFraction> candidates = new ArrayList<>();
		int[] numer = {1, 3, 1, 2, 1, 3, 2, 3, 4, 1};
		int[] denom = {20, 20, 5, 5, 2, 5, 3, 4, 5, 4};
		for (int k = 0; k < numer.length; k++) candidates.add(FractionMapper.BigFraction.of(numer[k], denom[k]));

		System.out.println("\nTrying " + candidates.size() + " candidate targets across up to "
			+ Math.min(maxThreads, candidates.size()) + " threads...");

		long t0 = System.nanoTime();
		CompressResult result = compressSegment(segment, freq, candidates, maxThreads, budgetSeconds, 5_000_000L);
		long t1 = System.nanoTime();

		if (!result.success)
		{
			System.out.println("\nNo candidate target succeeded within the time budget ("
				+ (t1 - t0) / 1_000_000 + " ms elapsed).");
			return;
		}

		System.out.println("\nSUCCESS");
		System.out.println("  target used:      " + result.target + "  (" + result.target.toDouble() + ")");
		System.out.println("  backtracks:        " + result.backtracks);
		System.out.println("  elapsed:           " + result.elapsedMillis + " ms");
		System.out.println("  serialized cost:   " + String.format("%.1f", result.costBits) + " bits ("
			+ String.format("%.1f", result.costBits / 8) + " bytes)");
		System.out.println("  original size:     " + (n * 8) + " bits (" + n + " bytes, if 1 byte/symbol)");
		System.out.println("  overhead ratio:    " + String.format("%.2fx", result.costBits / (n * 8)));

		int[] decoded = decompressSegment(result.orderings, freq, result.target, n);
		boolean matches = Arrays.equals(decoded, segment);
		System.out.println("  decoded matches original exactly: " + matches);
		if (!matches)
		{
			System.out.println("  MISMATCH -- first difference at index: ");
			for (int i = 0; i < n; i++) if (decoded[i] != segment[i]) { System.out.println("    index " + i + ": expected " + segment[i] + " got " + decoded[i]); break; }
		}
	}
}
