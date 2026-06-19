import java.util.*;
import java.math.*;

public class ArithmeticMapper
{
	// The following methods re-use one frequency table to reduce overhead,
	// but that decreases overall compression.
	public static ArrayList getSerialOffset(byte[] src, int [] frequency, int n)
	{
		int [] f = frequency.clone();
		
	    int [] s = new int[256];
	    int m = 0;
		for(int i = 0; i < 256; i++)
		{
			s[i]  = m;
			m   += f[i];
		}
		
		BigInteger [] offset = {BigInteger.ZERO, BigInteger.ONE}; 
		BigInteger [] range  = {BigInteger.ONE, BigInteger.ONE};
		
		for(int i = 0; i < n; i++)
	    {
	    	    int j = src[i];
	    	    if(j < 0)
	    	    	    j += 256;
	    	    
	    	    BigInteger [] addend = {range[0], range[1]};
	    	    addend[0] = addend[0].multiply(BigInteger.valueOf(s[j]));
	    	    addend[1] = addend[1].multiply(BigInteger.valueOf(m));
	    	    
	    	    BigInteger gcd = addend[0].gcd(addend[1]);
	    	    if(gcd.compareTo(BigInteger.ONE) == 1)
	    	    {
	    	    	    addend[0] = addend[0].divide(gcd);
			    addend[1] = addend[1].divide(gcd);
	    	    }
	    	    
	    	    
	    	    offset[0] = offset[0].multiply(addend[1]);
	    	    addend[0] = addend[0].multiply(offset[1]);
	    	    offset[1] = offset[1].multiply(addend[1]);
	    	    offset[0] = offset[0].add(addend[0]);
	    	    
	    	    
	    	    gcd = offset[0].gcd(offset[1]);
	    	    if(gcd.compareTo(BigInteger.ONE) == 1)
	    	    {
	    	    	    offset[0] = offset[0].divide(gcd);
			    	offset[1] = offset[1].divide(gcd);
	    	    }
			
	    	    
	    	    range[0] = range[0].multiply(BigInteger.valueOf(f[j]));
	    	    range[1] = range[1].multiply(BigInteger.valueOf(m));
	    	    
           
	    	    gcd = range[0].gcd(range[1]);
	    	    if(gcd.compareTo(BigInteger.ONE) == 1)
	    	    {
	    	    	    range[0] = range[0].divide(gcd);
			    	range[1] = range[1].divide(gcd);
	    	    }
	    	    
	    	    f[j]--;
	    	    m--;
	    	    for(int k = j + 1; k < s.length; k++)
	    	        s[k]--;
	    }
	
		ArrayList result = new ArrayList();
		result.add(offset);
		result.add(f);
        return result;	
	}	
	
	public static ArrayList getSerialValues(BigInteger [] v, int [] frequency, int n)
	{
	    byte [] value = new byte[n];
	   
        ArrayList <ArrayList <Integer>> arithmetic_list = new ArrayList <ArrayList <Integer>> ();
		
		int m = 0;
		for(int i = 0; i < frequency.length; i++)
		{
		    ArrayList <Integer> list = new ArrayList <Integer> ();
		    
		    if(frequency[i] != 0)
		    {
		        list.add(i);
		        list.add(frequency[i]);
		        list.add(m);
		    
		        arithmetic_list.add(list);
		    
		        m += frequency[i];
		    }
		}
		
		int [] frequency2 = frequency.clone();
	    
		BigInteger [] offset = {BigInteger.ZERO, BigInteger.ONE};
		BigInteger [] range  = {BigInteger.ONE, BigInteger.ONE};
		BigInteger [] w      = {v[0], v[1]};
		
		for(int i = 0; i < n; i++)
		{
			if(offset[0].compareTo(BigInteger.ZERO) != 0)
			{
				w[0] = v[0];
				w[1] = v[1];
			    w[0] = w[0].multiply(offset[1]);
			    w[0] = w[0].subtract(offset[0].multiply(w[1]));
			    w[1] = w[1].multiply(offset[1]);
			    
			    BigInteger gcd = w[0].gcd(w[1]);
	    	        if(gcd.compareTo(BigInteger.ONE) == 1)
	    	        {
	    	    	        w[0] = w[0].divide(gcd);
			       	w[1] = w[1].divide(gcd);
	    	        }
			}
			
			int j = arithmetic_list.size() / 2;
		    ArrayList <Integer> list = arithmetic_list.get(j);
			
		    int f = list.get(1);
		    int s = list.get(2);
				
			BigInteger a = range[0].multiply(BigInteger.valueOf(s));
		    	BigInteger b = w[0];
		    	BigInteger c = range[0].multiply(BigInteger.valueOf(s + f));
		    	BigInteger d = range[1].multiply(BigInteger.valueOf(m));
		    	    
		    	a = a.multiply(w[1]);
		    	b = b.multiply(d);
		    	c = c.multiply(w[1]);
		    	    	
			if(a.compareTo(b) > 0)
            {
			    int k = j / 2;
                while(a.compareTo(b) > 0) 
                	{
                	    j -= k;
                	    
                	    list = arithmetic_list.get(j);
                	    f    = list.get(1);
                	    s    = list.get(2);
                	    a    = range[0].multiply(BigInteger.valueOf(s));
                	    a    = a.multiply(w[1]);
                	    
                	    k /= 2;
                	    if(k == 0)
                	    	    k = 1;
                	}
                
                // Check if we passed value.
                c = range[0].multiply(BigInteger.valueOf(s + f));
       	        c = c.multiply(w[1]);
       	        if(c.compareTo(b) <= 0)
                {
                    while(c.compareTo(b) <= 0)
                    {
                	    	    j++;
                	    	    list = arithmetic_list.get(j);
                        	f    = list.get(1);
                        	s    = list.get(2);
                	    	    
                	    	    c = range[0].multiply(BigInteger.valueOf(s + f));
	                	    c = c.multiply(w[1]);
                	    }
                } 
             }
			 else if(c.compareTo(b) <= 0)
             {
				int size = arithmetic_list.size();
			    int k = (size - j) / 2;
                	
			    while(c.compareTo(b) <= 0) 
                	{
                	    j += k;
                	     
                	    list = arithmetic_list.get(j);
                	    f    = list.get(1);
                	    s    = list.get(2);
                	    c    = range[0].multiply(BigInteger.valueOf(s + f));
                	    c    = c.multiply(w[1]);
                	    
                	    k /= 2;
                	    if(k == 0)
                	    	    k = 1;
                	}
                	
			    // Check if we passed value.
			    a = range[0].multiply(BigInteger.valueOf(s));
        	        a = a.multiply(w[1]);
        	        if(a.compareTo(b) > 0)
            	    {
            	        while(a.compareTo(b) > 0)
            	        {
            	    	        j--; 
            	    	        list = arithmetic_list.get(j);
                        	f    = list.get(1);
                        	s    = list.get(2);
            	    	        a    = range[0].multiply(BigInteger.valueOf(s));
            	    	        a = a.multiply(w[1]);
            	        }
            	    }
            }
			
			BigInteger [] addend = {range[0].multiply(BigInteger.valueOf(s)), range[1].multiply(BigInteger.valueOf(m))};
				
			offset[0] = offset[0].multiply(addend[1]);
			offset[0] = offset[0].add(addend[0].multiply(offset[1]));
		    offset[1] = offset[1].multiply(addend[1]);
		        
		    BigInteger gcd = offset[0].gcd(offset[1]);
			if(gcd.compareTo(BigInteger.ONE) == 1)
			{
				offset[0] = offset[0].divide(gcd);
				offset[1] = offset[1].divide(gcd);;
			}
		   
		    range[0] = range[0].multiply(BigInteger.valueOf(f));
		    range[1] = range[1].multiply(BigInteger.valueOf(m));
		       
		    gcd = range[0].gcd(range[1]);
    	        if(gcd.compareTo(BigInteger.ONE) == 1)
    	        {
    	    	        range[0] = range[0].divide(gcd);
		    	    range[1] = range[1].divide(gcd);
    	        }
		   
    	        for(int p = j + 1; p < arithmetic_list.size(); p++)
	    	    {
	    	        	ArrayList <Integer> list2 = arithmetic_list.get(p);
	    	        	s = list2.get(2);
	    	        	s--;
	    	        	list2.set(2, s);
	    	        	arithmetic_list.set(p, list2);	
	    	    }
    	          
    	        f--;
	    	    m--;   
	    	    if(f != 0)
	    	    {
	    	        list.set(1, f);
	    	        arithmetic_list.set(j,  list);
	    	    }
	    	    else
	    	        	arithmetic_list.remove(j);
	    	     
	    	    int k    = list.get(0);
	    	    frequency2[k]--;
		    value[i] = (byte)k;	
		}
		
		ArrayList result = new ArrayList();
		result.add(value);
		result.add(frequency2);
	    return result;
	}
	
	// Requires a < b.
	public static long gcd(long a, long b) 
	{
		if (b == 0) 
			return a;
		return gcd(b, a % b);
	}
	
	// This method uses a renormalization technique suggested by Moffet to produce an approximation of the offset/range.
	// It produces a bit string that can be divided by the smallest power of two larger than the bit string value to get the approximation.
	// We think the problem with this is it doesn't appear to produce a set of start bits and then repeating bits that resolve to a pair of integers.
	// The reduction in precision means there is a limit on how many values can be produced from a single frequency table.
	public static ArrayList getNormalRangeQuotient(byte[] src, Hashtable <Integer, Integer> table, int [] frequency)
	{
		int [] f = frequency.clone();
		
	    int [] s = new int[f.length];
		
	    int    m = 0;
		for(int i = 0; i < f.length; i++)
		{
			s[i] = m;
			m    += f[i];
		}
		
		byte [] bit_buffer = new byte[src.length * 2];
		
		int     bit_offset       = 0;
		int     byte_offset      = 0;
		int     bits_outstanding = 0;
	
		long [] offset = {1L, 4L};
		long [] range  = {1L, 2L};
		
		int n = src.length;
		
		for(int i = 0; i < n; i++)
	    {
	    	    int j = src[i];
	    	    if(j < 0)
	    	    	    j += 256;
	    	    j = table.get(j);
	    	   
	    	    long [] addend = {range[0], range[1]};
	    	    addend[0] *= s[j];
	    	    addend[1] *= m;
	    	    
	    	   
	    	    long gcd = gcd(addend[0], addend[1]);
	    	    if(gcd > 1)
	    	    {
	    	    	    addend[0] /= gcd;
	    	    	    addend[1] /= gcd;
	    	    }
	    	   
	    	    
	    	    offset[0] *= addend[1];
	    	    addend[0] *= offset[1];
	    	    offset[1] *= addend[1];
	    	    offset[0] += addend[0];
	    	     
	    	    
	    	    gcd = gcd(offset[0], offset[1]);
	    	    if(gcd > 1)
	    	    {
	    	        offset[0] /= gcd;
    	    	        offset[1] /= gcd;
	    	    }
	    	   
	    	    
			range[0] *= f[j];
			range[1] *= m;
			
			
			gcd = gcd(range[0], range[1]);
    	        if(gcd > 1)
    	        {
    	            range[0] /= gcd;
	    	        range[1] /= gcd;
    	        }
    	      
    	        double p = offset[0];
    	        p       /= offset[1];
    	        double r = range[0];
    	        r       /= range[1];
    	        while(r <= .25)
    	        {
    	        	    if(p + r <= .5) 
    	        	    {
    	        	        bit_offset++;
    	        	        if(bit_offset == 8)
    	        	        {
    	        	    	        byte_offset++;
    	        	    	        bit_offset = 0;
    	        	        }
    	        	        int value = 1;
    	        	        while(bits_outstanding > 0)
    	        	        {
    	        	        	    int position = byte_offset * 8 + bit_offset;
    	        	        	    SegmentMapper.setBit(bit_buffer, position, value);
    	        	        	    bit_offset++;
    	    	        	        if(bit_offset == 8)
    	    	        	        {
    	    	        	    	        byte_offset++;
    	    	        	    	        bit_offset = 0;
    	    	        	        } 
    	    	        	        bits_outstanding--;
    	        	        }
    	        	    }
    	        	    else if(p >= .5)
    	        	    {
    	        	    	    int value = 1;
    	        	    	    int position = byte_offset * 8 + bit_offset;
    	        	    	    SegmentMapper.setBit(bit_buffer, position, value);
    	        	    	    while(bits_outstanding > 0)
    	        	    	    {
    	        	    	        bit_offset++;
        	        	        if(bit_offset == 8)
        	        	        {
        	        	    	        byte_offset++;
        	        	    	        bit_offset = 0;
        	        	        }
        	        	        bits_outstanding--;
    	        	    	    }
    	        	    	    p = p - .5;   
    	        	    }
    	        	    else
    	        	    {
    	        	    	    bits_outstanding++;
    	        	    	    p = p - .25;
    	        	    }
    	        	    p *= 2;
    	        	    r *= 2;  
    	        }
            
	    	    f[j]--;
	    	    m--;
	    	    for(int k = j + 1; k < s.length; k++)
	    	        s[k]--;
	    	    
	    	    offset[0] = 1;
	    	    offset[1] = 4;
	    	    range[0]  = 1;
	    	    range[1]  = 2;
	    	   
	    }
	
	    byte [] bits = new byte[byte_offset + 1];
	    for(int i = 0; i < bits.length; i++)
	    	    bits[i] = bit_buffer[i];
	    int extra_bits = 0;
	    if(bit_offset != 0)
	    	    extra_bits = 8 - bit_offset;
	    int bitlength = bits.length * 8 - extra_bits;
		
	    ArrayList result = new ArrayList();
	    result.add(bits);
	    result.add(bitlength);
	    
	    return result;
	}

    // Continued-fraction helpers for the optimal method for getting interval values.
    /**
     * Full continued-fraction expansion of n/d.
     * e.g. 7/5 → [1, 2, 2]
     */
    private static ArrayList <BigInteger> cfExpand(BigInteger numerator, BigInteger denominator) 
    {
        ArrayList <BigInteger> terms = new ArrayList <BigInteger>();
        while(!denominator.equals(BigInteger.ZERO)) 
        {
            BigInteger[] quotient_remainder = numerator.divideAndRemainder(denominator);
            terms.add(quotient_remainder[0]);
            numerator = denominator;
            denominator = quotient_remainder[1];
        }
        return terms;
    }

    /**
     * Evaluate a continued fraction [a0; a1, a2, …] → {p, q}.
     * Returns a two-element array {numerator, denominator}.
     */
    private static BigInteger[] cfToFraction(ArrayList <BigInteger> terms) 
    {
        BigInteger p = BigInteger.ONE;
        BigInteger q = BigInteger.ZERO;
        for (int i = terms.size() - 1; i >= 0; i--) 
        {
            BigInteger a = terms.get(i);
            BigInteger newP = a.multiply(p).add(q);
            q = p;
            p = newP;
        }
        return new BigInteger[]{p, q};
    }

    /**
     * Return the fraction p/q with the smallest denominator strictly inside
     * the open interval (loN/loD, hiN/hiD).
     *
     * Algorithm (O(log max(loD, hiD)) — pure calculation, no search):
     *   1. Expand both endpoints as continued fractions.
     *   2. Walk terms in lockstep while they agree; collect shared prefix.
     *   3. At the first differing term append min(a_lo, a_hi) + 1.
     *   4. Boundary fix: if that candidate lands exactly on the upper
     *      endpoint (excluded), descend one more level into lo's CF.
     *   5. Evaluate the truncated CF to get p/q.
     *
     * Returns {numerator, denominator}.
     */
    public static BigInteger[] simplestFractionInInterval(BigInteger loN, BigInteger loD,BigInteger hiN, BigInteger hiD) 
    {
        ArrayList <BigInteger> loCf = cfExpand(loN, loD);
        ArrayList <BigInteger> hiCf = cfExpand(hiN, hiD);

        // Pad the shorter list with zeros
        int maxLen = Math.max(loCf.size(), hiCf.size());
        while(loCf.size() < maxLen) 
        	    loCf.add(BigInteger.ZERO);
        while(hiCf.size() < maxLen) 
        	    hiCf.add(BigInteger.ZERO);

        ArrayList<BigInteger> shared = new ArrayList <BigInteger>();

        for(int i = 0; i < maxLen; i++) 
        {
            BigInteger a = loCf.get(i);
            BigInteger b = hiCf.get(i);

            if (a.equals(b)) 
            {
                shared.add(a);
            } 
            else 
            {
                // Take min(a, b) + 1 as the diverging term
                BigInteger c = a.min(b).add(BigInteger.ONE);
                shared.add(c);

                // Boundary fix: check if we landed exactly on hi (excluded)
                BigInteger[] pq = cfToFraction(shared);
                BigInteger g = pq[0].gcd(pq[1]);
                BigInteger p = pq[0].divide(g);
                BigInteger q = pq[1].divide(g);

                if(p.multiply(hiD).equals(hiN.multiply(q))) 
                {
                    // Exactly at upper bound — descend one level into lo's CF
                    shared.remove(shared.size() - 1);
                    shared.add(a);   // lo's actual diverging term
                    // Original lo_cf (before padding) has size loCf.size() - (maxLen - loCf.size() padding)
                    // We need the unpadded lo CF length; reconstruct from cfExpand
                    ArrayList <BigInteger> loCfOrig = cfExpand(loN, loD);
                    if(i + 1 < loCfOrig.size()) 
                    {
                        shared.add(loCfOrig.get(i + 1).add(BigInteger.ONE));
                    } 
                    else 
                    {
                        shared.add(BigInteger.TWO);
                    }
                }

                break;
            }
        }

        BigInteger[] pq = cfToFraction(shared);
        BigInteger g = pq[0].gcd(pq[1]);
        return new BigInteger[]{pq[0].divide(g), pq[1].divide(g)};
    }

    // ------------------------------------------------------------------
    // Actual method.
    // ------------------------------------------------------------------

    /**
     * Arithmetic encode {@code src} and return the simplest fraction
     * (smallest denominator) within the valid encoding interval.
     *
     * @param src       Raw bytes to encode.
     * @param frequency frequency[i] = count of byte value i (256 entries).
     * @return          Two-element array {numerator, denominator}.
     */
    public static BigInteger[] getIntervalValue(byte[] src, int[] frequency) 
    {
        int[] f = frequency.clone();
        int   n = src.length;

        // Build cumulative-frequency table
        int[] s = new int[f.length];
        int   m = 0;
        for (int i = 0; i < f.length; i++) {
            s[i] = m;
            m   += f[i];
        }

        // Track interval as two reduced rationals: offset and range
        BigInteger offN = BigInteger.ZERO, offD = BigInteger.ONE;  // 0/1
        BigInteger rngN = BigInteger.ONE,  rngD = BigInteger.ONE;  // 1/1

        for (int i = 0; i < n; i++) 
        {
            int j = src[i];
            if (j < 0) j += 256;   // treat byte as unsigned

            // addend = range * s[j] / m
            BigInteger addN = rngN.multiply(BigInteger.valueOf(s[j]));
            BigInteger addD = rngD.multiply(BigInteger.valueOf(m));
            BigInteger g = addN.gcd(addD);
            if (g.compareTo(BigInteger.ONE) > 0) 
            {
                addN = addN.divide(g);
                addD = addD.divide(g);
            }

            // offset += addend
            offN = offN.multiply(addD).add(addN.multiply(offD));
            offD = offD.multiply(addD);
            g = offN.gcd(offD);
            if (g.compareTo(BigInteger.ONE) > 0) 
            {
                offN = offN.divide(g);
                offD = offD.divide(g);
            }

            // range *= f[j] / m
            rngN = rngN.multiply(BigInteger.valueOf(f[j]));
            rngD = rngD.multiply(BigInteger.valueOf(m));
            g = rngN.gcd(rngD);
            if (g.compareTo(BigInteger.ONE) > 0) 
            {
                rngN = rngN.divide(g);
                rngD = rngD.divide(g);
            }

            // Adaptive update
            f[j]--;
            m--;
            for (int k = j + 1; k < s.length; k++) 
            {
                s[k]--;
            }
        }

        // Bring offset and range to a common denominator
        if (!offD.equals(rngD)) 
        {
            offN = offN.multiply(rngD);
            rngN = rngN.multiply(offD);
            BigInteger commonD = offD.multiply(rngD);
            offD = commonD;
            rngD = commonD;
        }

        // Upper bound of the valid interval (exclusive)
        BigInteger hiN = offN.add(rngN);
        BigInteger hiD = offD;

        return simplestFractionInInterval(offN, offD, hiN, hiD);
    }
    
    
    // Ordering the probabilistic space does not seem to significantly affect the computational efficiency or compression rate.
    // Would need to do an exhaustive search through all the possible tables before drawing a definite conclusion.
    
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

    // Method with order table.
    public static BigInteger[] getIntervalValue(byte[] src, int[] frequency, byte [] order) 
    {
        //int[] f = frequency.clone();
        //int   n = src.length;
    	
      	int [] f = new int[frequency.length];
		int    n = src.length;
	   
		// Reorder frequency table.
		for(int i = 0; i < order.length; i++)
		{
			int j = (int) order[i];
			if(j < 0)
				j += 256;
			f[j]  = frequency[i];
		}

        // Build cumulative-frequency table
        int[] s = new int[f.length];
        int   m = 0;
        for (int i = 0; i < f.length; i++) 
        {
            s[i] = m;
            m   += f[i];
        }

        // Track interval as two reduced rationals: offset and range
        BigInteger offN = BigInteger.ZERO, offD = BigInteger.ONE;  // 0/1
        BigInteger rngN = BigInteger.ONE,  rngD = BigInteger.ONE;  // 1/1

        for (int i = 0; i < n; i++) 
        {
            int j = src[i];
            if (j < 0) 
            	    j += 256; 
            // Use reordered frequency.
    	        j = (int) order[j];
    	        if(j < 0)
    	    	        j += 256;

            // addend = range * s[j] / m
            BigInteger addN = rngN.multiply(BigInteger.valueOf(s[j]));
            BigInteger addD = rngD.multiply(BigInteger.valueOf(m));
            BigInteger g = addN.gcd(addD);
            if (g.compareTo(BigInteger.ONE) > 0) 
            {
                addN = addN.divide(g);
                addD = addD.divide(g);
            }

            // offset += addend
            offN = offN.multiply(addD).add(addN.multiply(offD));
            offD = offD.multiply(addD);
            g = offN.gcd(offD);
            if (g.compareTo(BigInteger.ONE) > 0) 
            {
                offN = offN.divide(g);
                offD = offD.divide(g);
            }

            // range *= f[j] / m
            rngN = rngN.multiply(BigInteger.valueOf(f[j]));
            rngD = rngD.multiply(BigInteger.valueOf(m));
            g = rngN.gcd(rngD);
            if (g.compareTo(BigInteger.ONE) > 0) 
            {
                rngN = rngN.divide(g);
                rngD = rngD.divide(g);
            }

            // Adaptive update
            f[j]--;
            m--;
            for (int k = j + 1; k < s.length; k++) 
            {
                s[k]--;
            }
        }

        // Bring offset and range to a common denominator
        if (!offD.equals(rngD)) 
        {
            offN = offN.multiply(rngD);
            rngN = rngN.multiply(offD);
            BigInteger commonD = offD.multiply(rngD);
            offD = commonD;
            rngD = commonD;
        }

        // Upper bound of the valid interval (exclusive)
        BigInteger hiN = offN.add(rngN);
        BigInteger hiD = offD;

        return simplestFractionInInterval(offN, offD, hiN, hiD);
    }


    // Used by an alternative method for getting an interval value below.
    public static ArrayList <BigInteger> getPrimeFactors(BigInteger n)
	{
	    ArrayList <BigInteger> factors = new ArrayList<BigInteger>();
	    
	    if(n.equals(BigInteger.ONE))
	    	    return factors;
	    else if(n.isProbablePrime(100))
	    {
	    	    factors.add(n);
	    	    return factors;
	    }
	    else
	    {
	        BigInteger divisor = BigInteger.TWO;
	        while(n.mod(divisor).equals(BigInteger.ZERO))
	        {
	    	        factors.add(divisor);
                n = n.divide(divisor);    
	        }
	    
	        divisor = BigInteger.valueOf(3);
	        while(divisor.multiply(divisor).compareTo(n) <= 0) 
	        {
                if(n.mod(divisor).equals(BigInteger.ZERO)) 
                {
                    factors.add(divisor);
                    n = n.divide(divisor);
                } 
                else 
                    divisor = divisor.nextProbablePrime();
            }
	    
            if(n.compareTo(BigInteger.ONE) == 1) 
                factors.add(n);
	    
	        return factors;
	    }
	}
    
    
    // This is a slower version that produces sub-optimal results,
    // but is easier to understand.  It uses a search mechanism instead
    // of continued fraction expansion to find a simpler fraction than
    // the offset.
    public static BigInteger [] getIntervalValue2(byte[] src, int [] frequency)
    {
    	int [] f = frequency.clone();
    	
        int [] s = new int[f.length];
    	
    	int m = 0;
    	for(int i = 0; i < f.length; i++)
    	{
    		s[i] = m;
    		m    += f[i];
    	}
    	
    	BigInteger [] offset = new BigInteger[2];
    	offset[0]            = BigInteger.ZERO;
    	offset[1]            = BigInteger.ONE;
    	
    	BigInteger [] range  = new BigInteger[2];
    	range[0]             = BigInteger.ONE;
    	range[1]             = BigInteger.ONE;
    	
    	int    n       = src.length;
        
    	for(int i = 0; i < n; i++)
        {
        	    int j = src[i];
        	    if(j < 0)
        	    	    j += 256;
        	   
        	    BigInteger [] addend = new BigInteger[] {range[0], range[1]};
        	    
        	    BigInteger factor = BigInteger.ONE;
        	    factor            = factor.valueOf(s[j]);
        	    addend[0]         = addend[0].multiply(factor);
        	    factor            = factor.valueOf(m);
        	    addend[1]         = addend[1].multiply(factor);
        	    
        	    BigInteger gcd = addend[0].gcd(addend[1]);
        	    if(gcd.compareTo(BigInteger.ONE) == 1)
        	    {
        	    	    addend[0] = addend[0].divide(gcd);
    		    addend[1] = addend[1].divide(gcd);
        	    }
        	   
        	    offset[0] = offset[0].multiply(addend[1]);
        	    addend[0] = addend[0].multiply(offset[1]);
        	    offset[1] = offset[1].multiply(addend[1]);
        	    offset[0] = offset[0].add(addend[0]);
        	    
        	   
        	    gcd = offset[0].gcd(offset[1]);
        	    if(gcd.compareTo(BigInteger.ONE) == 1)
        	    {
        	    	    offset[0] = offset[0].divide(gcd);
    		    	offset[1] = offset[1].divide(gcd);
        	    }
    		
            factor   = factor.valueOf(f[j]);
        	    range[0] = range[0].multiply(factor);
        	    factor   = factor.valueOf(m);
        	    range[1] = range[1].multiply(factor);
        	    
        	   
        	    gcd = range[0].gcd(range[1]);
        	    if(gcd.compareTo(BigInteger.ONE) == 1)
        	    {
        	    	    range[0] = range[0].divide(gcd);
    		    	range[1] = range[1].divide(gcd);
        	    }
        	   
        	    
        	    f[j]--;
        	    m--;
        	    for(int k = j + 1; k < s.length; k++)
        	    {
        	    	    s[k]--;
        	    }
        }
    	
    	
    	if(offset[1].compareTo(range[1]) != 0)
    	{	
    	    BigInteger range_factor  = offset[1];
    	    BigInteger offset_factor = range[1];	
    		offset[0] = offset[0].multiply(offset_factor);
    		offset[1] = offset[1].multiply(offset_factor);
    				
    		range[0] = range[0].multiply(range_factor);
    		range[1] = range[1].multiply(range_factor);	
    	}

    	
    	BigInteger delimiter = offset[0].add(range[0]);
    	BigInteger gcd       = offset[1].gcd(offset[0]);
    	
    	ArrayList <BigInteger> factor_list = getPrimeFactors(gcd);
    	
    	
    	BigInteger factor = BigInteger.ONE;
    	
    	BigInteger maximum_range = BigInteger.valueOf(10000 * 1);
    	BigInteger minimum_range = BigInteger.valueOf(512);
    	int j = factor_list.size() - 1;
    	while(range[0].divide(factor).compareTo(maximum_range) == 1 && j >= 0)
    	{
    		BigInteger next_factor = factor_list.get(j);
    		factor = factor.multiply(next_factor);
    		j--;
    	}
    	
    	if(factor.compareTo(BigInteger.ONE) != 0)
    	{
    		offset[0] = offset[0].divide(factor);
    	    offset[1] = offset[1].divide(factor);
    	    range[0]  = range[0].divide(factor);
    	    range[1]  = offset[1];
    	    delimiter = offset[0].add(range[0]);
    	}
         
    	// If the offset pair had no common divisor,
    	// the range numerator is 1.
    	if(range[0].compareTo(minimum_range) == -1)
    	{
    		factor = BigInteger.TWO;
    		while(range[0].multiply(factor).compareTo(minimum_range) == - 1)
    			factor = factor.multiply(BigInteger.TWO);
    		
    		offset[0] = offset[0].multiply(factor);
    	    offset[1] = offset[1].multiply(factor);
    	    range[0]  = range[0].multiply(factor);
    	    range[1]  = offset[1];
    	    delimiter = offset[0].add(range[0]);
    	}
    	
    	gcd                      = offset[0].gcd(offset[1]);
    	BigInteger    max_gcd    = gcd;  
        BigInteger largest_index = BigInteger.ZERO;
        
        BigInteger [] value = new BigInteger[] {offset[0], offset[1]};
        
        // Because of the way we reduced the range of our values,
     	// we know the range can now be represented as an int, although
     	// the offset and delimiter probably still require BigIntegers.	
     	j = range[0].intValue();
     	int k = 0;
     	for(int i = 1; i < j; i++)
     	{
     		value[0]  = value[0].add(BigInteger.ONE);
         	gcd = value[0].gcd(value[1]);
            if(gcd.compareTo(max_gcd) == 1)
            {
         	    max_gcd = gcd;
         	    k = i;
            }	
     	}
     		
     	largest_index = BigInteger.valueOf(k);
     		
        value[0] = offset[0].add(largest_index);
        value[0] = value[0].divide(max_gcd);
        value[1] = value[1].divide(max_gcd);
             
       
        return value;
        
    }

    // This version uses a binary search to find the value that fits in the current interval. 
    public static byte [] getArithmeticValues(BigInteger [] v, int [] frequency, int n)
    {
        byte [] value = new byte[n];
       
        ArrayList <ArrayList <Integer>> arithmetic_list = new ArrayList <ArrayList <Integer>> ();
    	
    	int m = 0;
    	for(int i = 0; i < frequency.length; i++)
    	{
    	    ArrayList <Integer> list = new ArrayList <Integer> ();
    	    
    	    if(frequency[i] != 0)
    	    {
    	        list.add(i);
    	        list.add(frequency[i]);
    	        list.add(m);
    	    
    	        arithmetic_list.add(list);
    	    
    	        m += frequency[i];
    	    }
    	}
        
    	BigInteger [] offset = {BigInteger.ZERO, BigInteger.ONE};
    	BigInteger [] range  = {BigInteger.ONE, BigInteger.ONE};
    	BigInteger [] w      = {v[0], v[1]};
    	
    	for(int i = 0; i < n; i++)
    	{
    		if(offset[0].compareTo(BigInteger.ZERO) != 0)
    		{
    			w[0] = v[0];
    			w[1] = v[1];
    		    w[0] = w[0].multiply(offset[1]);
    		    w[0] = w[0].subtract(offset[0].multiply(w[1]));
    		    w[1] = w[1].multiply(offset[1]);
    		    
    		    BigInteger gcd = w[0].gcd(w[1]);
        	        if(gcd.compareTo(BigInteger.ONE) == 1)
        	        {
        	    	        w[0] = w[0].divide(gcd);
    		       	w[1] = w[1].divide(gcd);
        	        }
    		}
    		
    		int j = arithmetic_list.size() / 2;
    	    ArrayList <Integer> list = arithmetic_list.get(j);
    		
    	    int f = list.get(1);
    	    int s = list.get(2);
    			
    		BigInteger a = range[0].multiply(BigInteger.valueOf(s));
    	    	BigInteger b = w[0];
    	    	BigInteger c = range[0].multiply(BigInteger.valueOf(s + f));
    	    	BigInteger d = range[1].multiply(BigInteger.valueOf(m));
    	    	    
    	    	a = a.multiply(w[1]);
    	    	b = b.multiply(d);
    	    	c = c.multiply(w[1]);
    	    	    	
    		if(a.compareTo(b) > 0)
            {
    		    int k = j / 2;
                while(a.compareTo(b) > 0) 
                	{
                	    j -= k;
                	    
                	    list = arithmetic_list.get(j);
                	    f    = list.get(1);
                	    s    = list.get(2);
                	    a    = range[0].multiply(BigInteger.valueOf(s));
                	    a    = a.multiply(w[1]);
                	    
                	    k /= 2;
                	    if(k == 0)
                	    	    k = 1;
                	}
                
                // Check if we passed value.
                c = range[0].multiply(BigInteger.valueOf(s + f));
       	        c = c.multiply(w[1]);
       	        if(c.compareTo(b) <= 0)
                {
                    while(c.compareTo(b) <= 0)
                    {
                	    	    j++;
                	    	    list = arithmetic_list.get(j);
                        	f    = list.get(1);
                        	s    = list.get(2);
                	    	    
                	    	    c = range[0].multiply(BigInteger.valueOf(s + f));
                    	    c = c.multiply(w[1]);
                	    }
                } 
             }
    		     else if(c.compareTo(b) <= 0)
             {
    			    int size = arithmetic_list.size();
    		        int k = (size - j) / 2;
                	
    		        while(c.compareTo(b) <= 0) 
                	{
                	    j += k;
                	     
                	    list = arithmetic_list.get(j);
                	    f    = list.get(1);
                	    s    = list.get(2);
                	    c    = range[0].multiply(BigInteger.valueOf(s + f));
                	    c    = c.multiply(w[1]);
                	    
                	    k /= 2;
                	    if(k == 0)
                	    	    k = 1;
                	}
                	
    		        // Check if we passed value.
    		        a = range[0].multiply(BigInteger.valueOf(s));
        	        a = a.multiply(w[1]);
        	        if(a.compareTo(b) > 0)
            	    {
            	        while(a.compareTo(b) > 0)
            	        {
            	    	        j--; 
            	    	        list = arithmetic_list.get(j);
                        	f    = list.get(1);
                        	s    = list.get(2);
            	    	        a    = range[0].multiply(BigInteger.valueOf(s));
            	    	        a = a.multiply(w[1]);
            	        }
            	    }
            }
    		
    		    BigInteger [] addend = {range[0].multiply(BigInteger.valueOf(s)), range[1].multiply(BigInteger.valueOf(m))};
    			
    		    offset[0] = offset[0].multiply(addend[1]);
    		    offset[0] = offset[0].add(addend[0].multiply(offset[1]));
    	        offset[1] = offset[1].multiply(addend[1]);
    	        
    	        BigInteger gcd = offset[0].gcd(offset[1]);
    		    if(gcd.compareTo(BigInteger.ONE) == 1)
    		    {
    			    offset[0] = offset[0].divide(gcd);
    			    offset[1] = offset[1].divide(gcd);;
    		    }
    	   
    	        range[0] = range[0].multiply(BigInteger.valueOf(f));
    	        range[1] = range[1].multiply(BigInteger.valueOf(m));
    	       
    	        gcd = range[0].gcd(range[1]);
    	        if(gcd.compareTo(BigInteger.ONE) == 1)
    	        {
    	    	        range[0] = range[0].divide(gcd);
    	    	    range[1] = range[1].divide(gcd);
    	        }
    	   
    	        for(int p = j + 1; p < arithmetic_list.size(); p++)
        	    {
        	        	ArrayList <Integer> list2 = arithmetic_list.get(p);
        	        	s = list2.get(2);
        	        	s--;
        	        	list2.set(2, s);
        	        	arithmetic_list.set(p, list2);	
        	    }
    	          
    	        f--;
        	    m--;   
        	    if(f != 0)
        	    {
        	        list.set(1, f);
        	        arithmetic_list.set(j,  list);
        	    }
        	    else
        	        	arithmetic_list.remove(j);
        	    int k    = list.get(0);
    	        value[i] = (byte)k;	
    	    }
    	
        return value;
    }
    
    // A version of the method that uses an order table.
    public static byte [] getArithmeticValues(BigInteger [] v, int [] frequency, int n, byte [] order)
    {
    	    // Reorder frequency table.
    		int [] frequency2 = new int[frequency.length];
    		byte[] inverse_order = new byte[order.length];
    		for(int i = 0; i < order.length; i++)
    		{
    			int j = order[i];
    			if(j < 0)
    				j += 256;
    						
    			frequency2[j]    = frequency[i];
    			inverse_order[j] = (byte) i;
    		}
    		    
    	
        byte [] value = new byte[n];
       
        ArrayList <ArrayList <Integer>> arithmetic_list = new ArrayList <ArrayList <Integer>> ();
    	
      	int m = 0;
    	    for(int i = 0; i < frequency.length; i++)
    	    {
    	        ArrayList <Integer> list = new ArrayList <Integer> ();
    	    
    	        if(frequency2[i] != 0)
    	        {
    	            list.add(i);
    	            list.add(frequency2[i]);
    	            list.add(m);
    	    
    	            arithmetic_list.add(list);
    	    
    	            m += frequency2[i];
    	        }
    	    }
        
      	BigInteger [] offset = {BigInteger.ZERO, BigInteger.ONE};
      	BigInteger [] range  = {BigInteger.ONE, BigInteger.ONE};
      	BigInteger [] w      = {v[0], v[1]};
    	
    	    for(int i = 0; i < n; i++)
    	    {
    		    if(offset[0].compareTo(BigInteger.ZERO) != 0)
    		    {
    			    w[0] = v[0];
    			    w[1] = v[1];
    		        w[0] = w[0].multiply(offset[1]);
    		        w[0] = w[0].subtract(offset[0].multiply(w[1]));
    		        w[1] = w[1].multiply(offset[1]);
    		    
    		        BigInteger gcd = w[0].gcd(w[1]);
        	        if(gcd.compareTo(BigInteger.ONE) == 1)
        	        {
        	    	        w[0] = w[0].divide(gcd);
    		       	    w[1] = w[1].divide(gcd);
        	        }
    		    }
    		
    		    int j = arithmetic_list.size() / 2;
    	        ArrayList <Integer> list = arithmetic_list.get(j);
    		
    	        int f = list.get(1);
    	        int s = list.get(2);
    			
    		    BigInteger a = range[0].multiply(BigInteger.valueOf(s));
    	      	BigInteger b = w[0];
    	    	    BigInteger c = range[0].multiply(BigInteger.valueOf(s + f));
    	    	    BigInteger d = range[1].multiply(BigInteger.valueOf(m));
    	    	    
    	    	    a = a.multiply(w[1]);
    	       	b = b.multiply(d);
    	      	c = c.multiply(w[1]);
    	    	    	
    		    if(a.compareTo(b) > 0)
            {
    		        int k = j / 2;
                while(a.compareTo(b) > 0) 
                	{
                	    j -= k;
                	    
                	    list = arithmetic_list.get(j);
                	    f    = list.get(1);
                	    s    = list.get(2);
                	    a    = range[0].multiply(BigInteger.valueOf(s));
                	    a    = a.multiply(w[1]);
                	    
                	    k /= 2;
                	    if(k == 0)
                	    	    k = 1;
                	}
                
                // Check if we passed value.
                c = range[0].multiply(BigInteger.valueOf(s + f));
       	        c = c.multiply(w[1]);
       	        if(c.compareTo(b) <= 0)
                {
                    while(c.compareTo(b) <= 0)
                    {
                	    	    j++;
                	    	    list = arithmetic_list.get(j);
                        	f    = list.get(1);
                        	s    = list.get(2);
                	    	    
                	    	    c = range[0].multiply(BigInteger.valueOf(s + f));
                    	    c = c.multiply(w[1]);
                	    }
                } 
             }
    		     else if(c.compareTo(b) <= 0)
             {
    			    int size = arithmetic_list.size();
    		        int k = (size - j) / 2;
                	
    		        while(c.compareTo(b) <= 0) 
                	{
                	    j += k;
                	     
                	    list = arithmetic_list.get(j);
                	    f    = list.get(1);
                	    s    = list.get(2);
                	    c    = range[0].multiply(BigInteger.valueOf(s + f));
                	    c    = c.multiply(w[1]);
                	    
                	    k /= 2;
                	    if(k == 0)
                	    	    k = 1;
                	}
                	
    		        // Check if we passed value.
    		        a = range[0].multiply(BigInteger.valueOf(s));
        	        a = a.multiply(w[1]);
        	        if(a.compareTo(b) > 0)
            	    {
            	        while(a.compareTo(b) > 0)
            	        {
            	    	        j--; 
            	    	        list = arithmetic_list.get(j);
                        	f    = list.get(1);
                        	s    = list.get(2);
            	    	        a    = range[0].multiply(BigInteger.valueOf(s));
            	    	        a = a.multiply(w[1]);
            	        }
            	    }
            }
    		
    		    BigInteger [] addend = {range[0].multiply(BigInteger.valueOf(s)), range[1].multiply(BigInteger.valueOf(m))};
    			
    		    offset[0] = offset[0].multiply(addend[1]);
    		    offset[0] = offset[0].add(addend[0].multiply(offset[1]));
    	        offset[1] = offset[1].multiply(addend[1]);
    	        
    	        BigInteger gcd = offset[0].gcd(offset[1]);
    		    if(gcd.compareTo(BigInteger.ONE) == 1)
    		    {
    			    offset[0] = offset[0].divide(gcd);
    			    offset[1] = offset[1].divide(gcd);;
    		    }
    	   
    	        range[0] = range[0].multiply(BigInteger.valueOf(f));
    	        range[1] = range[1].multiply(BigInteger.valueOf(m));
    	       
    	        gcd = range[0].gcd(range[1]);
    	        if(gcd.compareTo(BigInteger.ONE) == 1)
    	        {
    	    	        range[0] = range[0].divide(gcd);
    	    	    range[1] = range[1].divide(gcd);
    	        }
    	   
    	        for(int p = j + 1; p < arithmetic_list.size(); p++)
        	    {
        	        	ArrayList <Integer> list2 = arithmetic_list.get(p);
        	        	s = list2.get(2);
        	        	s--;
        	        	list2.set(2, s);
        	        	arithmetic_list.set(p, list2);	
        	    }
    	          
    	        f--;
        	    m--;   
        	    if(f != 0)
        	    {
        	        list.set(1, f);
        	        arithmetic_list.set(j,  list);
        	    }
        	    else
        	        	arithmetic_list.remove(j);
        	    int k    = list.get(0);
        	    // Get original order.
	    	    k = inverse_order[k];
	        if(k < 0)
	            k += 256;
            
		    value[i] = (byte)k;		
    	    }
    	
        return value;
    }


    // Slower version that uses a linear search.
    public static byte [] getArithmeticValues2(BigInteger [] v, int [] frequency, int n)
    {
        byte [] value = new byte[n];
    
        ArrayList <ArrayList <Integer>> arithmetic_list = new ArrayList <ArrayList <Integer>> ();
 	
 	   int m = 0;
 	   for(int i = 0; i < frequency.length; i++)
 	   {
 		   if(frequency[i] != 0)
 		   {
 	           ArrayList <Integer> list = new ArrayList <Integer> ();
 	           list.add(i);
 	           list.add(frequency[i]);
 	           list.add(m);
 	           arithmetic_list.add(list);
 	           m += frequency[i];
 		   }
 	   }
     
 	   BigInteger [] offset = {BigInteger.ZERO, BigInteger.ONE};
 	   BigInteger [] range  = {BigInteger.ONE, BigInteger.ONE};
 	   BigInteger [] w      = {v[0], v[1]};
 	
 	   for(int i = 0; i < n; i++)
 	   {
 		   if(offset[0].compareTo(BigInteger.ZERO) != 0)
 		   {
 			   w[0] = v[0];
 			   w[1] = v[1];
 		       w[0] = w[0].multiply(offset[1]);
 		       w[0] = w[0].subtract(offset[0].multiply(w[1]));
 		       w[1] = w[1].multiply(offset[1]);
 		    
 		       BigInteger gcd = w[0].gcd(w[1]);
     	       if(gcd.compareTo(BigInteger.ONE) == 1)
     	       {
     	    	        w[0] = w[0].divide(gcd);
 		       	    w[1] = w[1].divide(gcd);
     	       }
 		   }
 		
 		   // Start j at middle of list.
 		   int j = arithmetic_list.size() / 2;
 	       ArrayList <Integer> list = arithmetic_list.get(j);
 		
 	       int f = list.get(1);
 	       int s = list.get(2);
 			
 		   BigInteger a = range[0].multiply(BigInteger.valueOf(s));
 	    	   BigInteger b = w[0];
 	       BigInteger c = range[0].multiply(BigInteger.valueOf(s + f));
 	    	   BigInteger d = range[1].multiply(BigInteger.valueOf(m));
 	    	    
 	    	   a = a.multiply(w[1]);
 	       b = b.multiply(d);
 	    	   c = c.multiply(w[1]);
 	    	    	
 		   if(a.compareTo(b) > 0)
           {
               while(a.compareTo(b) > 0) 
               {
             	   j--;
             	   list = arithmetic_list.get(j);
             	   f    = list.get(1);
             	   s    = list.get(2);
             	   a    = range[0].multiply(BigInteger.valueOf(s));
             	   a    = a.multiply(w[1]);
               }
          }
 		  else if(c.compareTo(b) <= 0)
          {
             	while(c.compareTo(b) <= 0) 
             	{
             	    j++;
             	    list = arithmetic_list.get(j);
             	    f    = list.get(1);
             	    s    = list.get(2);
             	    c    = range[0].multiply(BigInteger.valueOf(s + f));
             	    c    = c.multiply(w[1]);
             	}
         }
 		
 	     // Reset offset and range.
 		BigInteger [] addend = {range[0].multiply(BigInteger.valueOf(s)), range[1].multiply(BigInteger.valueOf(m))};
			
	 	offset[0] = offset[0].multiply(addend[1]);
	 	offset[0] = offset[0].add(addend[0].multiply(offset[1]));
	 	offset[1] = offset[1].multiply(addend[1]);
	 	        
	 	BigInteger gcd = offset[0].gcd(offset[1]);
	    if(gcd.compareTo(BigInteger.ONE) == 1)
	 	{
	 		offset[0] = offset[0].divide(gcd);
	 		offset[1] = offset[1].divide(gcd);;
	 	}
	 	   
	 	range[0] = range[0].multiply(BigInteger.valueOf(f));
	 	range[1] = range[1].multiply(BigInteger.valueOf(m));
	 	       
	 	gcd = range[0].gcd(range[1]);
	 	if(gcd.compareTo(BigInteger.ONE) == 1)
	 	{
	 	    	range[0] = range[0].divide(gcd);
	 	    	range[1] = range[1].divide(gcd);
	 	}
	 	   
	 	// Reset sums.
	 	for(int p = j + 1; p < arithmetic_list.size(); p++)
	    {
	     	 ArrayList <Integer> list2 = arithmetic_list.get(p);
	     	 s = list2.get(2);
	         s--;
	     	 list2.set(2, s);
	     	 arithmetic_list.set(p, list2);	
	     }
	 	          
	 	 f--;
	     m--;   
	     if(f != 0)
	     {
	     	list.set(1, f);
	     	arithmetic_list.set(j,  list);
	     }
	     else
	     	 arithmetic_list.remove(j);
	     	     
	     int k    = list.get(0);
	 	 value[i] = (byte)k;		
 	  }
      return value;
   }
 
}