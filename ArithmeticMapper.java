import java.util.*;
import java.math.*;

//version 4.0

/*
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

		FractionMapper.BigFraction off = FractionMapper.BigFraction.ZERO;
		FractionMapper.BigFraction rng = FractionMapper.BigFraction.ONE;

		for (int i = 0; i < n; i++)
		{
			int j = src[i];
			if (j < 0) j += 256;

			off = off.add(rng.multiply(FractionMapper.BigFraction.of(s[j], m)));
			rng = rng.multiply(FractionMapper.BigFraction.of(f[j], m));

			f[j]--;
			m--;
			for (int k = j + 1; k < s.length; k++) s[k]--;
		}

		FractionMapper.BigFraction hi = off.add(rng);
		return simplestFractionInInterval(off.n, off.d, hi.n, hi.d);
	}

	// This version uses a binary search to find the value that fits in the current interval.
	public static byte[] getArithmeticValues(BigInteger[] v, int[] frequency, int n)
	{
		FractionMapper.BigFraction target = new FractionMapper.BigFraction(v[0], v[1]);
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

		FractionMapper.BigFraction offset = FractionMapper.BigFraction.ZERO;
		FractionMapper.BigFraction range = FractionMapper.BigFraction.ONE;

		for (int i = 0; i < n; i++)
		{
			FractionMapper.BigFraction w = target.subtract(offset);

			int j = arithmetic_list.size() / 2;
			ArrayList<Integer> list = arithmetic_list.get(j);
			int f = list.get(1);
			int s = list.get(2);

			FractionMapper.BigFraction a = range.multiply(FractionMapper.BigFraction.of(s, m));
			FractionMapper.BigFraction c = range.multiply(FractionMapper.BigFraction.of(s + f, m));

			if (a.gt(w))
			{
				int k = j / 2;
				while (a.gt(w))
				{
					j -= k;
					list = arithmetic_list.get(j);
					f = list.get(1); s = list.get(2);
					a = range.multiply(FractionMapper.BigFraction.of(s, m));
					k /= 2;
					if (k == 0) k = 1;
				}
				c = range.multiply(FractionMapper.BigFraction.of(s + f, m));
				if (c.le(w))
				{
					while (c.le(w))
					{
						j++;
						list = arithmetic_list.get(j);
						f = list.get(1); s = list.get(2);
						c = range.multiply(FractionMapper.BigFraction.of(s + f, m));
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
					c = range.multiply(FractionMapper.BigFraction.of(s + f, m));
					k /= 2;
					if (k == 0) k = 1;
				}
				a = range.multiply(FractionMapper.BigFraction.of(s, m));
				if (a.gt(w))
				{
					while (a.gt(w))
					{
						j--;
						list = arithmetic_list.get(j);
						f = list.get(1); s = list.get(2);
						a = range.multiply(FractionMapper.BigFraction.of(s, m));
					}
				}
			}

			offset = offset.add(range.multiply(FractionMapper.BigFraction.of(s, m)));
			range = range.multiply(FractionMapper.BigFraction.of(f, m));

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

		FractionMapper.BigFraction off = FractionMapper.BigFraction.ZERO;
		FractionMapper.BigFraction rng = FractionMapper.BigFraction.ONE;

		for (int i = 0; i < n; i++)
		{
			int j = src[i]; if (j < 0) j += 256;
			int sj = (j > 0) ? fenwickQuery(bit, j - 1) : 0;

			off = off.add(rng.multiply(FractionMapper.BigFraction.of(sj, m)));
			rng = rng.multiply(FractionMapper.BigFraction.of(f[j], m));

			fenwickUpdate(bit, j, -1);
			f[j]--; m--;
		}

		FractionMapper.BigFraction hi = off.add(rng);
		return simplestFractionInInterval(off.n, off.d, hi.n, hi.d);
	}

	/** Decoder: same as getArithmeticValues but O(log 256) symbol search and updates via Fenwick tree. */
	public static byte[] getArithmeticValuesFenwick(BigInteger[] v, int[] frequency, int n)
	{
		int[] f = frequency.clone();
		int[] bit = fenwickBuild(f);
		int m = 0; for (int fv : f) m += fv;

		FractionMapper.BigFraction target = new FractionMapper.BigFraction(v[0], v[1]);
		FractionMapper.BigFraction offset = FractionMapper.BigFraction.ZERO;
		FractionMapper.BigFraction range = FractionMapper.BigFraction.ONE;

		byte[] value = new byte[n];

		for (int i = 0; i < n; i++)
		{
			FractionMapper.BigFraction w = target.subtract(offset);

			// target = w / range, scaled by m -- find which symbol's
			// cumulative range contains this position via Fenwick search
			FractionMapper.BigFraction scaledFrac = w.divide(range).multiply(m);
			long scaledLong = scaledFrac.n.divide(scaledFrac.d).longValue();
			int targetIdx = (int) Math.min(Math.max(scaledLong, 0L), (long) (m - 1));
			int j = fenwickFind(bit, targetIdx);
			while (j < 255 && f[j] == 0) j++;

			value[i] = (byte) j;
			int sj = (j > 0) ? fenwickQuery(bit, j - 1) : 0;

			offset = offset.add(range.multiply(FractionMapper.BigFraction.of(sj, m)));
			range = range.multiply(FractionMapper.BigFraction.of(f[j], m));

			fenwickUpdate(bit, j, -1);
			f[j]--; m--;
		}

		return value;
	}

	// =========================================================================
	// Fast renormalization-based arithmetic coder (no BigInteger). Untouched
	// by the BigFraction refactor above -- uses plain longs throughout, no
	// fractions at all.
	// =========================================================================

	public static byte[] getIntervalValueFast(byte[] src, int[] frequency)
	{
		int[] f = frequency.clone();
		int   n = src.length;

		int[] s = new int[f.length];
		int   m = 0;
		for (int i = 0; i < f.length; i++) { s[i] = m; m += f[i]; }

		final long TOP  = 0x100000000L;
		final long HALF = 0x80000000L;
		final long QTR  = 0x40000000L;
		final long TQTR = 0xC0000000L;

		long low     = 0L;
		long high    = TOP;
		int  pending = 0;

		byte[] buf     = new byte[n * 2 + 16];
		int    bit_pos = 0;

		for (int i = 0; i < n; i++)
		{
			int j = src[i];
			if (j < 0) j += 256;

			long range    = high - low;
			long new_low  = low + (range * s[j]) / m;
			long new_high = (s[j] + f[j] == m)
			                ? high
			                : low + (range * (long)(s[j] + f[j])) / m;
			low  = new_low;
			high = new_high;

			for (;;)
			{
				if (high <= HALF)
				{
					fastWriteBit(buf, bit_pos++, 0);
					for (int p = 0; p < pending; p++) fastWriteBit(buf, bit_pos++, 1);
					pending = 0;
					low  <<= 1;
					high <<= 1;
				}
				else if (low >= HALF)
				{
					fastWriteBit(buf, bit_pos++, 1);
					for (int p = 0; p < pending; p++) fastWriteBit(buf, bit_pos++, 0);
					pending = 0;
					low  = (low  - HALF) << 1;
					high = (high - HALF) << 1;
				}
				else if (low >= QTR && high <= TQTR)
				{
					pending++;
					low  = (low  - QTR) << 1;
					high = (high - QTR) << 1;
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
			fastWriteBit(buf, bit_pos++, 0);
			for (int p = 0; p < pending; p++) fastWriteBit(buf, bit_pos++, 1);
		}
		else
		{
			fastWriteBit(buf, bit_pos++, 1);
			for (int p = 0; p < pending; p++) fastWriteBit(buf, bit_pos++, 0);
		}

		int    bit_length  = bit_pos;
		int    byte_length = (bit_length + 7) / 8;
		byte[] result      = new byte[4 + byte_length];
		result[0] = (byte)(bit_length >>> 24);
		result[1] = (byte)(bit_length >>> 16);
		result[2] = (byte)(bit_length >>>  8);
		result[3] = (byte) bit_length;
		System.arraycopy(buf, 0, result, 4, byte_length);
		return result;
	}

	public static byte[] getArithmeticValuesFast(byte[] encoded, int[] frequency, int n)
	{
		int bit_length = ((encoded[0] & 0xFF) << 24)
		               | ((encoded[1] & 0xFF) << 16)
		               | ((encoded[2] & 0xFF) <<  8)
		               |  (encoded[3] & 0xFF);

		int[] f = frequency.clone();

		int[] s = new int[f.length];
		int   m = 0;
		for (int i = 0; i < f.length; i++) { s[i] = m; m += f[i]; }

		final long TOP  = 0x100000000L;
		final long HALF = 0x80000000L;
		final long QTR  = 0x40000000L;
		final long TQTR = 0xC0000000L;
		final long MASK = 0xFFFFFFFFL;

		long low     = 0L;
		long high    = TOP;
		int  bit_ptr = 0;

		long code = 0L;
		for (int b = 0; b < 32; b++)
		{
			int bit = (bit_ptr < bit_length) ? fastReadBit(encoded, 4, bit_ptr++) : 0;
			code = (code << 1) | bit;
		}

		byte[] value = new byte[n];

		for (int i = 0; i < n; i++)
		{
			long range  = high - low;
			long scaled = (code - low) * m / range;
			if (scaled < 0)  scaled = 0;
			if (scaled >= m) scaled = m - 1;

			int j = findFastSymbol(s, (int) scaled);
			while (j < f.length - 1 && f[j] == 0) j++;

			long new_low  = low + (range * s[j]) / m;
			long new_high = (s[j] + f[j] == m)
			                ? high
			                : low + (range * (long)(s[j] + f[j])) / m;

			while (code >= new_high && j < f.length - 1)
			{
				j++;
				while (j < f.length - 1 && f[j] == 0) j++;
				new_low  = low + (range * s[j]) / m;
				new_high = (s[j] + f[j] == m) ? high : low + (range * (long)(s[j] + f[j])) / m;
			}
			while (code < new_low && j > 0)
			{
				j--;
				while (j > 0 && f[j] == 0) j--;
				new_low  = low + (range * s[j]) / m;
				new_high = (s[j] + f[j] == m) ? high : low + (range * (long)(s[j] + f[j])) / m;
			}

			value[i] = (byte) j;

			low  = new_low;
			high = new_high;

			for (;;)
			{
				if (high <= HALF)
				{
					low  <<= 1;
					high <<= 1;
					int bit = (bit_ptr < bit_length) ? fastReadBit(encoded, 4, bit_ptr++) : 0;
					code = ((code << 1) | bit) & MASK;
				}
				else if (low >= HALF)
				{
					low  = (low  - HALF) << 1;
					high = (high - HALF) << 1;
					int bit = (bit_ptr < bit_length) ? fastReadBit(encoded, 4, bit_ptr++) : 0;
					code = (((code - HALF) << 1) | bit) & MASK;
				}
				else if (low >= QTR && high <= TQTR)
				{
					low  = (low  - QTR) << 1;
					high = (high - QTR) << 1;
					int bit = (bit_ptr < bit_length) ? fastReadBit(encoded, 4, bit_ptr++) : 0;
					code = (((code - QTR) << 1) | bit) & MASK;
				}
				else break;
			}

			f[j]--;
			m--;
			for (int k = j + 1; k < s.length; k++) s[k]--;
		}

		return value;
	}

	private static void fastWriteBit(byte[] buf, int pos, int bit)
	{
		if (bit != 0)
			buf[pos >> 3] |= (byte)(1 << (pos & 7));
	}

	private static int fastReadBit(byte[] buf, int data_byte_offset, int pos)
	{
		int abs = data_byte_offset * 8 + pos;
		return (buf[abs >> 3] >> (abs & 7)) & 1;
	}

	private static int findFastSymbol(int[] s, int target)
	{
		int lo = 0, hi = s.length - 1;
		while (lo < hi)
		{
			int mid = (lo + hi + 1) >> 1;
			if (s[mid] <= target) lo = mid;
			else                  hi = mid - 1;
		}
		return lo;
	}

	// =========================================================================
	// Fenwick-tree accelerated fast arithmetic coder.
	// Same 32-bit renormalization as getIntervalValueFast/getArithmeticValuesFast
	// but O(log 256) cumulative frequency updates instead of O(256).
	// =========================================================================

	public static byte[] getIntervalValueFastFenwick(byte[] src, int[] frequency)
	{
		int[] f   = frequency.clone();
		int   n   = src.length;
		int[] bit = fenwickBuild(f);
		int   m   = 0; for (int v : f) m += v;

		final long TOP  = 0x100000000L;
		final long HALF = 0x80000000L;
		final long QTR  = 0x40000000L;
		final long TQTR = 0xC0000000L;

		long low = 0L, high = TOP;
		int  pending = 0;

		byte[] buf     = new byte[n * 2 + 16];
		int    bit_pos = 0;

		for (int i = 0; i < n; i++)
		{
			int j = src[i]; if (j < 0) j += 256;

			int sj     = (j > 0) ? fenwickQuery(bit, j - 1) : 0;
			int sj_fj  = fenwickQuery(bit, j);

			long range    = high - low;
			long new_low  = low + (range * sj) / m;
			long new_high = (sj_fj == m) ? high : low + (range * (long)sj_fj) / m;
			low = new_low; high = new_high;

			for (;;)
			{
				if (high <= HALF) {
					fastWriteBit(buf, bit_pos++, 0);
					for (int p = 0; p < pending; p++) fastWriteBit(buf, bit_pos++, 1);
					pending = 0; low <<= 1; high <<= 1;
				} else if (low >= HALF) {
					fastWriteBit(buf, bit_pos++, 1);
					for (int p = 0; p < pending; p++) fastWriteBit(buf, bit_pos++, 0);
					pending = 0; low = (low - HALF) << 1; high = (high - HALF) << 1;
				} else if (low >= QTR && high <= TQTR) {
					pending++; low = (low - QTR) << 1; high = (high - QTR) << 1;
				} else break;
			}

			fenwickUpdate(bit, j, -1);
			f[j]--; m--;
		}

		pending++;
		if (low < QTR) {
			fastWriteBit(buf, bit_pos++, 0);
			for (int p = 0; p < pending; p++) fastWriteBit(buf, bit_pos++, 1);
		} else {
			fastWriteBit(buf, bit_pos++, 1);
			for (int p = 0; p < pending; p++) fastWriteBit(buf, bit_pos++, 0);
		}

		int bit_length = bit_pos, byte_length = (bit_length + 7) / 8;
		byte[] result = new byte[4 + byte_length];
		result[0] = (byte)(bit_length >>> 24); result[1] = (byte)(bit_length >>> 16);
		result[2] = (byte)(bit_length >>> 8);  result[3] = (byte) bit_length;
		System.arraycopy(buf, 0, result, 4, byte_length);
		return result;
	}

	public static byte[] getArithmeticValuesFastFenwick(byte[] encoded, int[] frequency, int n)
	{
		int bit_length = ((encoded[0] & 0xFF) << 24) | ((encoded[1] & 0xFF) << 16)
		               | ((encoded[2] & 0xFF) <<  8) |  (encoded[3] & 0xFF);

		int[] f   = frequency.clone();
		int[] bit = fenwickBuild(f);
		int   m   = 0; for (int fv : f) m += fv;

		final long TOP  = 0x100000000L;
		final long HALF = 0x80000000L;
		final long QTR  = 0x40000000L;
		final long TQTR = 0xC0000000L;
		final long MASK = 0xFFFFFFFFL;

		long low = 0L, high = TOP;
		int  bit_ptr = 0;

		long code = 0L;
		for (int b = 0; b < 32; b++) {
			int bt = (bit_ptr < bit_length) ? fastReadBit(encoded, 4, bit_ptr++) : 0;
			code = (code << 1) | bt;
		}

		byte[] value = new byte[n];

		for (int i = 0; i < n; i++)
		{
			long range  = high - low;
			long scaled = (code - low) * m / range;
			if (scaled < 0) scaled = 0; if (scaled >= m) scaled = m - 1;

			int j = fenwickFind(bit, (int)scaled);
			while (j < f.length - 1 && f[j] == 0) j++;

			int sj    = (j > 0) ? fenwickQuery(bit, j - 1) : 0;
			int sj_fj = fenwickQuery(bit, j);

			long new_low  = low + (range * sj) / m;
			long new_high = (sj_fj == m) ? high : low + (range * (long)sj_fj) / m;

			// Same boundary-nudge fix as getArithmeticValuesFast -- see that
			// method's history for the full explanation. Verify and nudge j
			// using Fenwick queries instead of direct array access.
			while (code >= new_high && j < f.length - 1)
			{
				j++;
				while (j < f.length - 1 && f[j] == 0) j++;
				sj    = (j > 0) ? fenwickQuery(bit, j - 1) : 0;
				sj_fj = fenwickQuery(bit, j);
				new_low  = low + (range * sj) / m;
				new_high = (sj_fj == m) ? high : low + (range * (long)sj_fj) / m;
			}
			while (code < new_low && j > 0)
			{
				j--;
				while (j > 0 && f[j] == 0) j--;
				sj    = (j > 0) ? fenwickQuery(bit, j - 1) : 0;
				sj_fj = fenwickQuery(bit, j);
				new_low  = low + (range * sj) / m;
				new_high = (sj_fj == m) ? high : low + (range * (long)sj_fj) / m;
			}

			value[i] = (byte) j;

			low = new_low; high = new_high;

			for (;;)
			{
				if (high <= HALF) {
					low <<= 1; high <<= 1;
					int bt = (bit_ptr < bit_length) ? fastReadBit(encoded, 4, bit_ptr++) : 0;
					code = ((code << 1) | bt) & MASK;
				} else if (low >= HALF) {
					low = (low - HALF) << 1; high = (high - HALF) << 1;
					int bt = (bit_ptr < bit_length) ? fastReadBit(encoded, 4, bit_ptr++) : 0;
					code = (((code - HALF) << 1) | bt) & MASK;
				} else if (low >= QTR && high <= TQTR) {
					low = (low - QTR) << 1; high = (high - QTR) << 1;
					int bt = (bit_ptr < bit_length) ? fastReadBit(encoded, 4, bit_ptr++) : 0;
					code = (((code - QTR) << 1) | bt) & MASK;
				} else break;
			}

			fenwickUpdate(bit, j, -1);
			f[j]--; m--;
		}

		return value;
	}
}
