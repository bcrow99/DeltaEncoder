import java.util.*;
import java.math.*;

//version 3.0

/*
 * Changes in this version:
 *
 *   1. getSerialOffset/getSerialValues removed: this was a "sample-once,
 *      re-use one 256-entry frequency table across the whole stream"
 *      encoding scheme, as an alternative to segmenting the data into
 *      pieces and giving each its own table. Simple segmentation turned
 *      out to work better in practice, so this pairing (and the raw-
 *      ArrayList usage that came with it -- removing it also eliminated
 *      every rawtypes compiler warning in this file) is no longer needed.
 *
 *   2. getArithmeticOffsetAndRange removed: returned a 4-element
 *      {offN,offD,rngN,rngD} array (raw offset AND range, unsimplified),
 *      a different shape from every decoder in this file, which all
 *      expect the simplified 2-element {numerator,denominator} pairing
 *      that getIntervalValue/getIntervalValueFenwick produce. Nothing
 *      remaining in this file consumed its output.
 *
 *   3. getArithmeticValues2 removed: the linear-search decoder variant.
 *
 *   4. gcd(long,long) removed: this was ONLY ever called from inside the
 *      Fenwick methods' now-superseded manual pre-reduction step (compute
 *      gcd(sj,m) via plain longs before ever constructing a BigInteger,
 *      to keep the numbers involved smaller going in) -- once those
 *      methods were refactored to build a FractionMapper.BigFraction
 *      directly instead, and its constructor handles reduction on its
 *      own, this became dead code: called nowhere except recursively by
 *      itself. Found by tracing call sites directly, not by a compiler
 *      warning -- javac has no built-in "unused method" diagnostic, so
 *      this is exactly the kind of thing that can go unnoticed after a
 *      refactor unless someone goes looking for it specifically. Note
 *      this was NOT present in the pre-refactor (version 1.0) code --
 *      it became dead as a side effect of the version 2.0 BigFraction
 *      refactor, not something inherited from before it.
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
	// Ordering the probabilistic space does not seem to significantly affect
	// the computational efficiency or compression rate. Would need to do an
	// exhaustive search through all the possible tables before drawing a
	// definite conclusion. (Restored verbatim from the pre-refactor version --
	// unrelated to the BigFraction change, not touched by it.)
	// =========================================================================

	// This method returns a table of the indices of a frequency table in ascending order, greatest last.
	public static byte [] getAscendingTable(int frequency[])
	{
		ArrayList <Double>          list  = new ArrayList <Double>();
		Hashtable <Double, Integer> table = new Hashtable <Double, Integer>();
		int                         n     = frequency.length;

		for(int i = 0; i < n; i++)
		{
			double key = frequency[i];
			while (table.containsKey(key))
				key += .001;
			table.put(key, i);
			list.add(key);
		}

		Collections.sort(list);

		byte [] ascending_table = new byte[n];

		for(int i = 0; i < n; i++)
		{
			double key         = list.get(i);
			int    j           = table.get(key);
			ascending_table[i] = (byte)j;
		}
		return ascending_table;
	}

	//This method returns a table of the indices of a frequency table in descending order, greatest first.
	public static byte [] getDescendingTable(int frequency[])
	{
		ArrayList <Double>          list  = new ArrayList <Double>();
		Hashtable <Double, Integer> table = new Hashtable <Double, Integer>();
		int       n                       = frequency.length;

		for(int i = 0; i < n; i++)
		{
			double key = frequency[i];
			while (table.containsKey(key))
				key += .001;
			table.put(key, i);
			list.add(key);
		}

		Collections.sort(list, Comparator.reverseOrder());

		byte [] descending_table = new byte[n];

		for(int i = 0; i < n; i++)
		{
			double key          = list.get(i);
			int    j            = table.get(key);
			descending_table[j] = (byte) i;
		}
		return descending_table;
	}

	//This method returns a table of the indices of a frequency table in the order that a value is exhausted first.
	public static byte [] getFirstTable(byte[] src, int [] frequency)
	{
		ArrayList <Integer> exhausted_list = new ArrayList <Integer>();

		for(int i = 0; i < frequency.length; i++)
		{
			if(frequency[i] == 0)
				exhausted_list.add(i);
		}

		int [] f = frequency.clone();

		for(int i = 0; i < src.length; i++)
	    {
	    	    int j = src[i];
	    	    if(j < 0)
	    	    	    j += 256;
	    	    f[j]--;
	    	    if(f[j] == 0)
	    	    	    exhausted_list.add(j);
	    }

		byte [] first_table = new byte[frequency.length];
		for(int i = 0; i < frequency.length; i++)
		{
			int j = exhausted_list.get(i);
			first_table[i] = (byte)j;
		}

        return first_table;
	}

	// This method returns a table of the indices of a frequency table in the order that a value is exhausted last.
	public static byte [] getLastTable(byte[] src, int [] frequency)
	{
        ArrayList <Integer> exhausted_list = new ArrayList <Integer>();

		for(int i = 0; i < frequency.length; i++)
		{
			if(frequency[i] == 0)
				exhausted_list.add(i);
		}
		int [] f = frequency.clone();

		for(int i = 0; i < src.length; i++)
	    {
	    	    int j = src[i];
	    	    if(j < 0)
	    	    	    j += 256;
	    	    f[j]--;
	    	    if(f[j] == 0)
	    	    	    exhausted_list.add(j);
	    }

		byte [] last_table = new byte[frequency.length];
		int k = 0;
		for(int i = frequency.length - 1; i >= 0; i--)
		{
			int j = exhausted_list.get(i);
			last_table[k++] = (byte)j;
		}

	    return last_table;
	}

	public static ArrayList <byte []> getTableSeries(byte[] src, int [] frequency)
	{
		ArrayList <byte []> result = new ArrayList <byte[]> ();

		ArrayList <Double>          list  = new ArrayList <Double>();
		Hashtable <Double, Integer> table = new Hashtable <Double, Integer>();
		int       n                       = frequency.length;

		for(int i = 0; i < n; i++)
		{
			double key = frequency[i];
			while (table.containsKey(key))
				key += .001;
			table.put(key, i);
			list.add(key);
		}

		Collections.sort(list, Comparator.reverseOrder());

		byte [] descending_table = new byte[n];

		for(int i = 0; i < n; i++)
		{
			double key          = list.get(i);
			int    j            = table.get(key);
			descending_table[j] = (byte) i;
		}

        ArrayList <Integer> exhausted_list = new ArrayList <Integer>();

		for(int i = 0; i < frequency.length; i++)
		{
			if(frequency[i] == 0)
				exhausted_list.add(i);
		}
		int [] f = frequency.clone();

		for(int i = 0; i < src.length; i++)
	    {
	    	    int j = src[i];
	    	    if(j < 0)
	    	    	    j += 256;
	    	    f[j]--;
	    	    if(f[j] == 0)
	    	    	    exhausted_list.add(j);
	    }

		byte [] last_table = new byte[frequency.length];
		int k = 0;
		for(int i = frequency.length - 1; i >= 0; i--)
		{
			int j = exhausted_list.get(i);
			last_table[k++] = (byte)j;
		}

		int  length = descending_table.length;
		byte least  = descending_table[length - 1];

		int least_place = 0;
		for(int i = 0; i < last_table.length; i++)
		{
		    if(last_table[i] == least)
		    {
		    	    least_place = i;
		    	    break;
		    }
		}

		byte [] init_table = last_table.clone();
		result.add(init_table);

		boolean done = false;
		while(!done)
		{
		    if(least_place == 0)
		    	    done = true;
		    else
		    {
		    	    byte down               = last_table[least_place - 1];
		    	    last_table[least_place] = down;
		    	    last_table[least_place - 1] = least;
		    	    least_place--;
		    	    byte [] current_table = last_table.clone();
		    	    result.add(current_table);
		    	    if(least_place == 0)
    		    	        done = true;
		    }
		}

		return result;
	}


	public static ArrayList <byte []> getTableSeries2(byte[] src, int [] frequency)
	{
		ArrayList <byte []> result = new ArrayList <byte[]> ();

		ArrayList <Double>          list  = new ArrayList <Double>();
		Hashtable <Double, Integer> table = new Hashtable <Double, Integer>();
		int       n                       = frequency.length;

		for(int i = 0; i < n; i++)
		{
			double key = frequency[i];
			while (table.containsKey(key))
				key += .001;
			table.put(key, i);
			list.add(key);
		}

		Collections.sort(list, Comparator.reverseOrder());

		byte [] descending_table = new byte[n];

		for(int i = 0; i < n; i++)
		{
			double key          = list.get(i);
			int    j            = table.get(key);
			descending_table[j] = (byte) i;
		}

        ArrayList <Integer> exhausted_list = new ArrayList <Integer>();

		for(int i = 0; i < frequency.length; i++)
		{
			if(frequency[i] == 0)
				exhausted_list.add(i);
		}
		int [] f = frequency.clone();

		for(int i = 0; i < src.length; i++)
	    {
	    	    int j = src[i];
	    	    if(j < 0)
	    	    	    j += 256;
	    	    f[j]--;
	    	    if(f[j] == 0)
	    	    	    exhausted_list.add(j);
	    }

		byte [] last_table = new byte[frequency.length];
		int k = 0;
		for(int i = frequency.length - 1; i >= 0; i--)
		{
			int j = exhausted_list.get(i);
			last_table[k++] = (byte)j;
		}

		int  length = descending_table.length;
		byte least  = descending_table[length - 1];

		int least_place = 0;
		for(int i = 0; i < last_table.length; i++)
		{
		    if(last_table[i] == least)
		    {
		    	    least_place = i;
		    	    break;
		    }
		}

		byte [] init_table = last_table.clone();
		result.add(init_table);

		boolean done = false;
		while(!done)
		{
		    if(least_place == last_table.length - 1)
		    	    done = true;
		    else
		    {
		    	    byte up = last_table[least_place + 1];
		    	    last_table[least_place] = up;
		    	    last_table[least_place + 1] = least;
		    	    least_place++;
		    	    byte [] current_table = last_table.clone();
		    	    result.add(current_table);
		    	    if(least_place == last_table.length - 1)
    		    	        done = true;
		    }
		}

		return result;
	}

	public static ArrayList <byte []> getTableSeries3(byte[] src, int [] frequency)
	{
		ArrayList <byte []> result = new ArrayList <byte[]> ();

		ArrayList <Double>          list  = new ArrayList <Double>();
		Hashtable <Double, Integer> table = new Hashtable <Double, Integer>();
		int       n                       = frequency.length;

		for(int i = 0; i < n; i++)
		{
			double key = frequency[i];
			while (table.containsKey(key))
				key += .001;
			table.put(key, i);
			list.add(key);
		}

		Collections.sort(list, Comparator.reverseOrder());

		byte [] descending_table = new byte[n];

		for(int i = 0; i < n; i++)
		{
			double key          = list.get(i);
			int    j            = table.get(key);
			descending_table[j] = (byte) i;
		}

        ArrayList <Integer> exhausted_list = new ArrayList <Integer>();

		for(int i = 0; i < frequency.length; i++)
		{
			if(frequency[i] == 0)
				exhausted_list.add(i);
		}
		int [] f = frequency.clone();

		for(int i = 0; i < src.length; i++)
	    {
	    	    int j = src[i];
	    	    if(j < 0)
	    	    	    j += 256;
	    	    f[j]--;
	    	    if(f[j] == 0)
	    	    	    exhausted_list.add(j);
	    }

		byte [] last_table = new byte[frequency.length];
		int k = 0;
		for(int i = frequency.length - 1; i >= 0; i--)
		{
			int j = exhausted_list.get(i);
			last_table[k++] = (byte)j;
		}

		int  length = descending_table.length;
		byte greatest  = descending_table[0];

		int greatest_place = 0;
		for(int i = 0; i < last_table.length; i++)
		{
		    if(last_table[i] == greatest)
		    {
		    	    greatest_place = i;
		    	    break;
		    }
		}

		byte [] init_table = last_table.clone();
		result.add(init_table);

		boolean done = false;
		while(!done)
		{
		    if(greatest_place == 0)
		    	    done = true;
		    else
		    {
		    	    byte down               = last_table[greatest_place - 1];
		    	    last_table[greatest_place] = down;
		    	    last_table[greatest_place - 1] = greatest;
		    	    greatest_place--;
		    	    byte [] current_table = last_table.clone();
		    	    result.add(current_table);
		    	    if(greatest_place == 0)
    		    	        done = true;
		    }
		}

		return result;
	}

	public static ArrayList <byte []> getTableSeries4(byte[] src, int [] frequency)
	{
		ArrayList <byte []> result = new ArrayList <byte[]> ();

		ArrayList <Double>          list  = new ArrayList <Double>();
		Hashtable <Double, Integer> table = new Hashtable <Double, Integer>();
		int       n                       = frequency.length;

		for(int i = 0; i < n; i++)
		{
			double key = frequency[i];
			while (table.containsKey(key))
				key += .001;
			table.put(key, i);
			list.add(key);
		}

		Collections.sort(list, Comparator.reverseOrder());

		byte [] descending_table = new byte[n];

		for(int i = 0; i < n; i++)
		{
			double key          = list.get(i);
			int    j            = table.get(key);
			descending_table[j] = (byte) i;
		}

        ArrayList <Integer> exhausted_list = new ArrayList <Integer>();

		for(int i = 0; i < frequency.length; i++)
		{
			if(frequency[i] == 0)
				exhausted_list.add(i);
		}
		int [] f = frequency.clone();

		for(int i = 0; i < src.length; i++)
	    {
	    	    int j = src[i];
	    	    if(j < 0)
	    	    	    j += 256;
	    	    f[j]--;
	    	    if(f[j] == 0)
	    	    	    exhausted_list.add(j);
	    }

		byte [] last_table = new byte[frequency.length];
		int k = 0;
		for(int i = frequency.length - 1; i >= 0; i--)
		{
			int j = exhausted_list.get(i);
			last_table[k++] = (byte)j;
		}

		int  length = descending_table.length;
		byte greatest  = descending_table[0];

		int greatest_place = 0;
		for(int i = 0; i < last_table.length; i++)
		{
		    if(last_table[i] == greatest)
		    {
		    	    greatest_place = i;
		    	    break;
		    }
		}

		byte [] init_table = last_table.clone();
		result.add(init_table);

		boolean done = false;
		while(!done)
		{
		    if(greatest_place == last_table.length - 1)
		    	    done = true;
		    else
		    {
		    	    byte up               = last_table[greatest_place + 1];
		    	    last_table[greatest_place] = up;
		    	    last_table[greatest_place + 1] = greatest;
		    	    greatest_place++;

		    	    byte [] current_table = last_table.clone();
		    	    result.add(current_table);
		    	    if(greatest_place == last_table.length - 1)
    		    	       done = true;
		    }
		}

		return result;
	}


  	/**
  	 * Produces a random permutation of symbol indices -- a probabilistic-space
  	 * baseline that carries no information about the data, for comparison
  	 * against frequency-driven orderings like Last and Descending.
  	 */
  	public static byte[] getRandomTable(int frequency[])
  	{
  		int n = frequency.length;
  		byte[] table = new byte[n];
  		for (int i = 0; i < n; i++)
  			table[i] = (byte) i;

  		java.util.Random rand = new java.util.Random();
  		for (int i = n - 1; i > 0; i--)
  		{
  			int j = rand.nextInt(i + 1);
  			byte tmp = table[i];
  			table[i] = table[j];
  			table[j] = tmp;
  		}
  		return table;
  	}

  	public static byte[] getRandomTable(int frequency[], long seed)
  	{
  		int n = frequency.length;
  		byte[] table = new byte[n];
  		for (int i = 0; i < n; i++)
  			table[i] = (byte) i;

  		java.util.Random rand = new java.util.Random(seed);
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
  	 * Builds the random rank->symbol table via getRandomTable(frequency, seed)
  	 * and inverts it to the symbol->rank shape expected by the order
  	 * parameter of getIntervalValue/getArithmeticValues. Both the encoder
  	 * (search) and decoder (reconstruction from a stored seed) call this same
  	 * helper rather than each inverting getRandomTable's output separately,
  	 * so they are guaranteed to derive the identical order table from a given
  	 * (frequency, seed) pair.
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
  	 * Byte-seed convenience overload. Widens the byte (interpreted as
  	 * unsigned, 0-255) to a long before delegating, so a decoder that only
  	 * stores this single byte reconstructs exactly the same table the
  	 * encoder derived when searching over byte-sized seeds.
  	 */
  	public static byte[] getRandomOrderTable(int[] frequency, byte seed)
  	{
  		return getRandomOrderTable(frequency, (long) (seed & 0xFF));
  	}

  	/**
  	 * Short-seed convenience overload. Widens the short to a long via sign
  	 * extension before delegating, matching how DataOutputStream.writeShort /
  	 * DataInputStream.readShort round-trip a short's bit pattern exactly, so
  	 * both sides reconstruct the identical table from a given seed.
  	 */
  	public static byte[] getRandomOrderTable(int[] frequency, short seed)
  	{
  		return getRandomOrderTable(frequency, (long) seed);
  	}


	// =========================================================================
	// Encoder/decoder pair re-using one frequency table across the full
	// byte-value range (0-255) to reduce overhead, at some cost to overall
	// compression. Refactored to use BigFraction internally; return/param
	// shapes unchanged (still BigInteger[2] at the ArrayList/parameter
	// boundary) for compatibility with existing callers.
	// =========================================================================


	// Requires a < b.

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

	// =========================================================================
	// Order-table variants: same core arithmetic as above, plus a reordered
	// frequency table so a different symbol occupies each rank position.
	// =========================================================================

	public static BigInteger[] getIntervalValue(byte[] src, int[] frequency, byte[] order)
	{
		int[] f = new int[frequency.length];
		int n = src.length;

		for (int i = 0; i < order.length; i++)
		{
			int j = (int) order[i];
			if (j < 0) j += 256;
			f[j] = frequency[i];
		}

		int[] s = new int[f.length];
		int m = 0;
		for (int i = 0; i < f.length; i++) { s[i] = m; m += f[i]; }

		FractionMapper.BigFraction off = FractionMapper.BigFraction.ZERO;
		FractionMapper.BigFraction rng = FractionMapper.BigFraction.ONE;

		for (int i = 0; i < n; i++)
		{
			int j = src[i];
			if (j < 0) j += 256;
			j = (int) order[j];
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

	// A version of the method that uses an order table.
	public static byte[] getArithmeticValues(BigInteger[] v, int[] frequency, int n, byte[] order)
	{
		int[] frequency2 = new int[frequency.length];
		byte[] inverse_order = new byte[order.length];
		for (int i = 0; i < order.length; i++)
		{
			int j = order[i];
			if (j < 0) j += 256;
			frequency2[j] = frequency[i];
			inverse_order[j] = (byte) i;
		}

		FractionMapper.BigFraction target = new FractionMapper.BigFraction(v[0], v[1]);
		byte[] value = new byte[n];

		ArrayList<ArrayList<Integer>> arithmetic_list = new ArrayList<>();
		int m = 0;
		for (int i = 0; i < frequency.length; i++)
		{
			if (frequency2[i] != 0)
			{
				ArrayList<Integer> list = new ArrayList<>();
				list.add(i); list.add(frequency2[i]); list.add(m);
				arithmetic_list.add(list);
				m += frequency2[i];
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
			k = inverse_order[k];
			if (k < 0) k += 256;
			value[i] = (byte) k;
		}
		return value;
	}

	// Slower version that uses a linear search.

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
	// Cheap approximate-offset scorer for order-table search (hill climbing /
	// annealing). Does not modify any existing encode/decode path.
	// =========================================================================

	/**
	 * Cheap approximate offset for order-table search. Runs the same long-based
	 * E1/E2/E3 renormalization as getIntervalValueFast, with an order-table
	 * remap like getIntervalValue(..., order), but instead of packing bits into
	 * a byte stream for storage, captures the leading ~52 bits directly and
	 * returns them as a double in [0, 1). Not intended for round-trip
	 * encode/decode -- only as a fast scorer during hill-climbing/annealing.
	 *
	 * Precision note: for any segment large enough to emit more than ~52 bits
	 * total (true of essentially all real segments), the interval has already
	 * collapsed well past double precision, so this agrees with the exact
	 * BigInteger offset from getIntervalValue(src, frequency, order) to full
	 * double precision.
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
	 * Keeps only the leading MAX_BITS bits appended to it -- enough for full
	 * double precision -- and discards the rest. Used only by
	 * getApproxOffsetFastOrdered; not a general-purpose bit buffer.
	 */
	private static final class LeadingBits
	{
		static final int MAX_BITS = 52; // matches double's mantissa precision
		long accum = 0L;
		int  count = 0;

		void append(int bit)
		{
			if (count < MAX_BITS)
			{
				accum = (accum << 1) | bit;
				count++;
			}
		}

		double toApproxOffset()
		{
			return (count == 0) ? 0.0 : (double) accum / (double) (1L << count);
		}
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
