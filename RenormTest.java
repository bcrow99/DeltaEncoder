import java.math.BigInteger;
import java.util.*;

public class RenormTest
{
	static int[] makeFreqForSize(int nTotal, int alphabetSize)
	{
		int[] base = new int[alphabetSize];
		int sum = 0;
		for (int k = 0; k < alphabetSize; k++) { base[k] = Math.max(1, (int) Math.round(nTotal * Math.pow(0.5, k) * 0.5)); sum += base[k]; }
		base[alphabetSize - 1] += (nTotal - sum);
		if (base[alphabetSize - 1] < 1) base[alphabetSize - 1] = 1;
		return base;
	}

	static int estimateNeededBits(int[] freq)
	{
		// log2(multinomial coefficient) + safety margin
		long n = 0; for (int f : freq) n += f;
		double bits = lgammaBits(n + 1);
		for (int f : freq) bits -= lgammaBits(f + 1);
		return (int) Math.ceil(bits) + 128;
	}
	static double lgammaBits(long n) { double b = 0; for (long k = 2; k < n; k++) b += Math.log(k) / Math.log(2); return b; }

	public static void main(String[] args) throws Exception
	{
		BigInteger tNum = BigInteger.valueOf(1), tDen = BigInteger.valueOf(20); // target = 1/20

		int[] sizes = {5, 8, 12, 16, 20, 30, 50, 64, 100, 256, 1024};
		for (int n : sizes)
		{
			int alpha = Math.max(3, Math.min(16, n / 4));
			int[] freq = makeFreqForSize(n, alpha);
			int actualN = 0; for (int f : freq) actualN += f;
			ArrayList<Integer> pool = new ArrayList<>();
			for (int s = 0; s < freq.length; s++) for (int c = 0; c < freq[s]; c++) pool.add(s);
			Collections.shuffle(pool, new Random(7));
			int[] src = new int[actualN];
			for (int i = 0; i < actualN; i++) src[i] = pool.get(i);

			int neededBits = estimateNeededBits(freq);

			long t0 = System.nanoTime();
			RenormalizingSteerCoder.SteerResult r = RenormalizingSteerCoder.steerEncode(
				src, freq, tNum, tDen, 2_000_000L, System.nanoTime() + 60_000_000_000L, neededBits);
			long t1 = System.nanoTime();

			if (r == null)
			{
				System.out.printf("n=%4d alpha=%2d bits=%5d : FAILED (%.1f ms)%n", actualN, alpha, neededBits, (t1 - t0) / 1e6);
				continue;
			}

			int[] decoded = RenormalizingSteerCoder.steerDecode(r.orderings, freq, tNum, tDen, actualN, neededBits);
			boolean matches = Arrays.equals(decoded, src);
			System.out.printf("n=%4d alpha=%2d bits=%5d : SUCCESS  backtracks=%-6d  time=%7.1f ms  decode_matches=%s%n",
				actualN, alpha, neededBits, r.backtracks, (t1 - t0) / 1e6, matches);

			if (!matches)
			{
				System.out.println("  MISMATCH DETECTED -- first difference:");
				for (int i = 0; i < actualN; i++)
					if (decoded[i] != src[i]) { System.out.println("    index " + i + ": expected " + src[i] + " got " + decoded[i]); break; }
			}
		}
	}
}
