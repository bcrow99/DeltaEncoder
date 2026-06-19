import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;

public class FractionMapper
{
	public static boolean isProbablePrime(int n)
	{
		return BigInteger.valueOf(n).isProbablePrime(100);
	}
	
	public static boolean isProbablePrime(long n)
	{
		return BigInteger.valueOf(n).isProbablePrime(100);
	}
	
	public static ArrayList <Integer> getPrimeFactors(int n)
	{
	    ArrayList <Integer> factors = new ArrayList <Integer> ();
	    
	    if(n == 1)
	    	    return factors;
	    else if(isProbablePrime(n))
	    {
	    	    factors.add(n);
	    	    return factors;
	    }
	    else
	    {
	        int divisor = 2;
	    
	        while(n % divisor == 0)
	        {
	    	        factors.add(divisor);
	    	        n /= divisor;
	        }
	  
	        divisor = 3;
	        while(divisor * divisor <= n) 
	        {
                if(n % divisor == 0) 
                {
                    factors.add(divisor);
                    n /= divisor;
                } 
                else 
                {
                    divisor += 2;
                }
            }
	    
            if (n > 1) 
                factors.add(n);
            
            return factors;
        }
	}
	
	public static ArrayList <Long> getPrimeFactors(long n)
	{
	    ArrayList <Long> factors = new ArrayList<Long>();
	    if(n == 1)
	    	    return factors;
	    else if(isProbablePrime(n))
	    {
	    	    factors.add(n);
	    	    return factors;
	    }
	    else
	    {
	        long divisor = 2;
	    
	        while(n % divisor == 0)
	        {
	    	        factors.add(divisor);
	    	        n /= divisor;
	        }
	    
	        divisor = 3;
	        while(divisor * divisor <= n) 
	        {
                if(n % divisor == 0) 
                {
                    factors.add(divisor);
                    n /= divisor;
                } 
                else 
                    divisor += 2;
            }
        
	        if (n > 1) 
                factors.add(n);
        
	        return factors;
	    }
	}
	
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
	
	public static ArrayList <BigInteger> getPrimes(BigInteger limit)
	{
		ArrayList <BigInteger> primes = new ArrayList <BigInteger> ();
		
		BigInteger prime = BigInteger.TWO;
		while(prime.compareTo(limit) == -1)
		{
			primes.add(prime);
			BigInteger next_prime = prime.nextProbablePrime();
			prime = next_prime;
		}
		
		return primes;
	}
	
	public static ArrayList <BigInteger> getPrimes(BigInteger init, BigInteger limit)
	{
		ArrayList <BigInteger> primes = new ArrayList <BigInteger> ();
		
		BigInteger prime = init;
		while(prime.compareTo(limit) == -1)
		{
			primes.add(prime);
			BigInteger next_prime = prime.nextProbablePrime();
			prime = next_prime;
		}
		
		return primes;
	}
	
	// Requires a < b.
	public static int gcd(int a, int b) 
	{
		if (b == 0) 
			return a;
		return gcd(b, a % b);
	}
	
	public static long gcd(long a, long b) 
	{
		if (b == 0) 
			return a;
		return gcd(b, a % b);
	}
		
    public static int [] getDigits(long a, long b)
	{
		ArrayList <Long> prime_factors = getPrimeFactors(b);
		
		long gcd = gcd(a, b);
		if(gcd > 1)
			b /= gcd;
		
		int c = 0;
		
		long q = 1;
		for(int i = 0; i < prime_factors.size(); i++)
		{
			long factor = prime_factors.get(i);
			if(factor == 2 || factor == 5)
				c++;
			else
				q *= factor;
		}
		
		int        d = 1;
		BigInteger k = BigInteger.TEN;
		BigInteger m = BigInteger.valueOf(q);
		
		while(k.compareTo(m) == -1)
		{
			d++;
			k = k.multiply(BigInteger.TEN);
		}
		k = k.subtract(BigInteger.ONE);
		
		while(k.mod(m).compareTo(BigInteger.ZERO) != 0)
		{
			d++;
			k = BigInteger.TEN.pow(d).subtract(BigInteger.ONE);
		}
		
		int [] digits = {c, d};
		return digits;
	}
	
	public static int [] getDigits(BigInteger a, BigInteger b)
	{
		BigInteger gcd = a.gcd(b);
		
		if(gcd.compareTo(BigInteger.ONE) == 1)	
		    b = b.divide(gcd);
		
		ArrayList <BigInteger> prime_factors = getPrimeFactors(b);
		
		int c = 0;
		
		int two_multiple = 0;
		int five_multiple = 0;
		BigInteger q = BigInteger.ONE;
		for(int i = 0; i < prime_factors.size(); i++)
		{
			BigInteger factor = prime_factors.get(i);
			
			if(factor.compareTo(BigInteger.TWO) == 0)
				two_multiple++;
			else if(factor.compareTo(BigInteger.valueOf(5)) == 0)
				five_multiple++;
			else
				q = q.multiply(factor);
		}
		
		if(two_multiple > five_multiple)
			c = two_multiple;
		else
			c = five_multiple;
		
		int        d = 1;
		BigInteger k = BigInteger.TEN;
		
		
		while(k.compareTo(q) < 1)
		{
			d++;
			k = BigInteger.TEN.pow(d).subtract(BigInteger.ONE);
		}
		
		while(k.mod(q).compareTo(BigInteger.ZERO) != 0)
		{
			d++;
			k = BigInteger.TEN.pow(d).subtract(BigInteger.ONE);
		}
		
		int [] digits = {c, d};
		return digits;
	}
	
	public static ArrayList long_divide(BigInteger a, BigInteger b)
	{
		ArrayList result = new ArrayList();	
		
		StringBuffer fraction = new StringBuffer();
		fraction.append('0');
		fraction.append('.');
		
		ArrayList <BigInteger> quotient_list = new ArrayList <BigInteger>();
		ArrayList <BigInteger> remainder_list = new ArrayList <BigInteger>();
		
		quotient_list.add(BigInteger.ZERO);
		remainder_list.add(a);
		
		a = a.multiply(BigInteger.TEN);
		while(a.compareTo(b) == -1)
		{
			fraction.append('0');
			quotient_list.add(BigInteger.ZERO);
			remainder_list.add(a);
			a = a.multiply(BigInteger.TEN);	
		}
		
		BigInteger c = a.divide(b);
		BigInteger d = a.mod(b);
		String c_string = String.valueOf(c);
		fraction.append(c_string);
		
		if(d.compareTo(BigInteger.ZERO) == 0)
		{
			int number_of_start_digits     = c_string.length();
			int number_of_repeating_digits = 0;
			
			result.add(fraction.toString());
			result.add(number_of_start_digits);
			result.add(number_of_repeating_digits);
			return result;	
		}
		
		if(remainder_list.contains(d))
		{
		    int index            = remainder_list.indexOf(d);	
		    int number_of_digits = fraction.length() - 2;
		    
		    if(!quotient_list.contains(c))
		    {
		    	    int number_of_start_digits = index; 
		    	    int number_of_repeating_digits = number_of_digits - number_of_start_digits;
		    	    result.add(fraction.toString());
				result.add(number_of_start_digits);
				result.add(number_of_repeating_digits);
				return result;	
		    }
		    else
		    {
		    	    int number_of_repeating_digits = number_of_digits - index;
				int number_of_start_digits     = number_of_digits - number_of_repeating_digits;    
				result.add(fraction.toString());
				result.add(number_of_start_digits);
				result.add(number_of_repeating_digits);
				return result;	
		    }
		}
		
		quotient_list.add(c);
		remainder_list.add(d);
		
		while(d.compareTo(BigInteger.ZERO) != 0)
		{
			a = d.multiply(BigInteger.TEN);
			c = a.divide(b);
			d = a.mod(b);
			
			c_string = String.valueOf(c);
			fraction.append(c_string);
			
			if(d.compareTo(BigInteger.ZERO) == 0)
			{
				int number_of_start_digits = fraction.length() - 2;
				int number_of_repeating_digits = 0;
				
				result.add(fraction.toString());
				result.add(number_of_start_digits);
				result.add(number_of_repeating_digits);
				return result;
			}
			if(remainder_list.contains(d))
			{
			    int index            = remainder_list.indexOf(d);	
			    int number_of_digits = fraction.length() - 2;
			    
			    if(!quotient_list.contains(c))
			    {
			    	    int number_of_start_digits = index; 
			    	    int number_of_repeating_digits = number_of_digits - number_of_start_digits;
			    	    result.add(fraction.toString());
					result.add(number_of_start_digits);
					result.add(number_of_repeating_digits);
					return result;	
			    }
			    else
			    {
			    	    int number_of_repeating_digits = number_of_digits - index;
					int number_of_start_digits     = number_of_digits - number_of_repeating_digits;    
					result.add(fraction.toString());
					result.add(number_of_start_digits);
					result.add(number_of_repeating_digits);
					return result;	
			    }
			}
			quotient_list.add(c);
			remainder_list.add(d);	
		}
		
		return result;
	}
	
	public static ArrayList long_divide(int a, int b)
	{
		
		ArrayList result = new ArrayList();
		
		StringBuffer fraction = new StringBuffer();
		fraction.append('0');
		fraction.append('.');
		
		ArrayList <Integer> quotient_list = new ArrayList <Integer>();
		ArrayList <Integer> remainder_list = new ArrayList <Integer>();
		
		
		quotient_list.add(0);
		remainder_list.add(a);
		
		a *= 10;
		while(a < b)
		{
			fraction.append('0');
			quotient_list.add(0);
			remainder_list.add(a);	
			a *= 10;
		}
		
		int c = a / b;
		int d = a % b;
		String c_string = String.valueOf(c);
		fraction.append(c_string);
		
		if(d == 0)
		{
			int number_of_start_digits = c_string.length();
			int number_of_repeating_digits = 0;
			
			result.add(fraction.toString());
			result.add(number_of_start_digits);
			result.add(number_of_repeating_digits);
			return result;
		}
		
		if(remainder_list.contains(d))
		{
		    int index            = remainder_list.indexOf(d);	
		    int number_of_digits = fraction.length() - 2;
		    
		    if(!quotient_list.contains(c))
		    {
		    	    int number_of_start_digits = index; 
		    	    int number_of_repeating_digits = number_of_digits - number_of_start_digits;
		    	    result.add(fraction.toString());
				result.add(number_of_start_digits);
				result.add(number_of_repeating_digits);
				return result;	
		    }
		    else
		    {
		    	    int number_of_repeating_digits = number_of_digits - index;
				int number_of_start_digits     = number_of_digits - number_of_repeating_digits;    
				result.add(fraction.toString());
				result.add(number_of_start_digits);
				result.add(number_of_repeating_digits);
				return result;	
		    }
		}
		
		quotient_list.add(c);
		remainder_list.add(d);
		
		while(d != 0)
		{
			a = d * 10;
			c = a / b;
			d = a % b;
			
			c_string = String.valueOf(c);
			fraction.append(c_string);
			
			if(d == 0)
			{
				int number_of_start_digits = fraction.length() - 2;
				int number_of_repeating_digits = 0;
				
				result.add(fraction.toString());
				result.add(number_of_start_digits);
				result.add(number_of_repeating_digits);
				return result;
			}
			if(remainder_list.contains(d))
			{
			    int index            = remainder_list.indexOf(d);	
			    int number_of_digits = fraction.length() - 2;
			    
			    if(!quotient_list.contains(c))
			    {
			    	    int number_of_start_digits = index; 
			    	    int number_of_repeating_digits = number_of_digits - number_of_start_digits;
			    	    result.add(fraction.toString());
					result.add(number_of_start_digits);
					result.add(number_of_repeating_digits);
					return result;	
			    }
			    else
			    {
			    	    int number_of_repeating_digits = number_of_digits - index;
					int number_of_start_digits     = number_of_digits - number_of_repeating_digits;    
					result.add(fraction.toString());
					result.add(number_of_start_digits);
					result.add(number_of_repeating_digits);
					return result;	
			    }
			    
			   
			}
			quotient_list.add(c);
			remainder_list.add(d);
		}
		
		return result;
	}
	
	public static ArrayList long_divide(long a, long b)
	{
		
		ArrayList result = new ArrayList();
		
		StringBuffer fraction = new StringBuffer();
		fraction.append('0');
		fraction.append('.');
		
		ArrayList <Long> quotient_list = new ArrayList <Long>();
		ArrayList <Long> remainder_list = new ArrayList <Long>();
		
		
		quotient_list.add(0L);
		remainder_list.add(a);
		
		a *= 10;
		while(a < b)
		{
			fraction.append('0');
			quotient_list.add(0L);
			remainder_list.add(a);	
			a *= 10;
		}
		
		long c = a / b;
		long d = a % b;
		String c_string = String.valueOf(c);
		fraction.append(c_string);
		
		if(d == 0L)
		{
			int number_of_start_digits = c_string.length();
			int number_of_repeating_digits = 0;
			
			result.add(fraction.toString());
			result.add(number_of_start_digits);
			result.add(number_of_repeating_digits);
			return result;
		}
		
		if(remainder_list.contains(d))
		{
		    int index            = remainder_list.indexOf(d);	
		    int number_of_digits = fraction.length() - 2;
		    
		    if(!quotient_list.contains(c))
		    {
		    	    int number_of_start_digits = index; 
		    	    int number_of_repeating_digits = number_of_digits - number_of_start_digits;
		    	    result.add(fraction.toString());
				result.add(number_of_start_digits);
				result.add(number_of_repeating_digits);
				return result;	
		    }
		    else
		    {
		    	    int number_of_repeating_digits = number_of_digits - index;
				int number_of_start_digits     = number_of_digits - number_of_repeating_digits;    
				result.add(fraction.toString());
				result.add(number_of_start_digits);
				result.add(number_of_repeating_digits);
				return result;	
		    }
		}
		
		quotient_list.add(c);
		remainder_list.add(d);
		
		while(d != 0)
		{
			a = d * 10;
			c = a / b;
			d = a % b;
			
			c_string = String.valueOf(c);
			fraction.append(c_string);
			
			if(d == 0)
			{
				int number_of_start_digits = fraction.length() - 2;
				int number_of_repeating_digits = 0;
				
				result.add(fraction.toString());
				result.add(number_of_start_digits);
				result.add(number_of_repeating_digits);
				return result;
			}
			if(remainder_list.contains(d))
			{
			    int index            = remainder_list.indexOf(d);	
			    int number_of_digits = fraction.length() - 2;
			    
			    if(!quotient_list.contains(c))
			    {
			    	    int number_of_start_digits = index; 
			    	    int number_of_repeating_digits = number_of_digits - number_of_start_digits;
			    	    result.add(fraction.toString());
					result.add(number_of_start_digits);
					result.add(number_of_repeating_digits);
					return result;	
			    }
			    else
			    {
			    	    int number_of_repeating_digits = number_of_digits - index;
					int number_of_start_digits     = number_of_digits - number_of_repeating_digits;    
					result.add(fraction.toString());
					result.add(number_of_start_digits);
					result.add(number_of_repeating_digits);
					return result;	
			    }
			    
			   
			}
			quotient_list.add(c);
			remainder_list.add(d);
		}
		
		return result;
	}
	
	
	public static int [] getRatio(String decimal, int start_digits, int repeating_digits)
	{
		if(repeating_digits == 0)
		{
			double a = Double.parseDouble(decimal);
			double b = Math.pow(10, start_digits);
			double c = a * b;
			
			int numerator   = (int)c;
			int denominator = (int)b;
		    
			int gcd      = gcd(numerator, denominator);
		    numerator   /= gcd;
		    denominator /= gcd;
		    int [] ratio = {numerator, denominator};
		    
		    return ratio;
		}
		else if(start_digits == 0)
		{
			double a = Double.parseDouble(decimal);
			double b = Math.pow(10, repeating_digits);
			double c = a * b;
			
			int numerator  = (int)c;
			int denominator = (int)(b - 1);
			
			int gcd      = gcd(numerator, denominator);
		    numerator   /= gcd;
		    denominator /= gcd;
			
		    int [] ratio = {numerator, denominator};
		    return ratio;	
		}
		else
		{
			double a = Double.parseDouble(decimal);
			double b = Math.pow(10, start_digits);
			double c = Math.pow(10, start_digits + repeating_digits);
			
			double d = Math.floor(a * b);
			double e = a * c;
			
			double f = e - d;
			double g = c - b;
			
			int numerator   = (int)f;
			int denominator = (int)g;
		
			int gcd      = gcd(numerator, denominator);
			numerator   /= gcd;
			denominator /= gcd;
			int [] ratio = {numerator, denominator};
			
		    return ratio;	
		}
	}
	
	public static BigInteger [] getRatio2(String decimal, int start_digits, int repeating_digits)
	{
		BigInteger [] ratio = new BigInteger[2];
		ratio[0] = BigInteger.ONE;
		ratio[1] = BigInteger.TWO;
		if(repeating_digits == 0)
		{
			BigDecimal a = new BigDecimal(decimal);
			BigDecimal b = new BigDecimal(Math.pow(10, start_digits));
			a            = a.multiply(b);
		    
		    BigInteger numerator   = a.toBigInteger();
		    BigInteger denominator = b.toBigInteger();
		    
		    BigInteger gcd = numerator.gcd(denominator);
		    numerator      = numerator.divide(gcd);
		    denominator    = denominator.divide(gcd);
		    
		    ratio[0] = numerator;
		    ratio[1] = denominator;
		    
		    return ratio;
		}
		else if(start_digits == 0)
		{
			BigDecimal a = new BigDecimal(decimal);
			
			BigDecimal b = new BigDecimal(Math.pow(10, repeating_digits));
			a            = a.multiply(b);
			
		    BigInteger numerator   = a.toBigInteger();
		    BigInteger denominator = b.toBigInteger();
		    denominator            = denominator.subtract(BigInteger.ONE);
		    
		    BigInteger gcd = numerator.gcd(denominator);
		    numerator      = numerator.divide(gcd);
		    denominator    = denominator.divide(gcd);
			
		    ratio[0] = numerator;
		    ratio[1] = denominator;
			
		    return ratio;	
		}
		else
		{
		    BigDecimal a = new BigDecimal(decimal);
		    BigDecimal b = BigDecimal.TEN;
		    for(int i = 1; i < start_digits; i++)
		    	    b = b.multiply(BigDecimal.TEN);
		    BigDecimal c = BigDecimal.TEN;
		    for(int i = 1; i < start_digits + repeating_digits; i++)
	    	        c = c.multiply(BigDecimal.TEN);
		  
		    BigDecimal d = a.multiply(b);
		    BigDecimal e = a.multiply(c);
		    
		    BigInteger f = d.toBigInteger();
		    BigInteger g = e.toBigInteger();
		    BigInteger numerator = g.subtract(f);
		    
		    BigDecimal h = c.subtract(b);
		    BigInteger denominator = h.toBigInteger();
			
		    BigInteger gcd = numerator.gcd(denominator);
		    numerator      = numerator.divide(gcd);
	        denominator    = denominator.divide(gcd);
		    
			ratio[0] = numerator;
			ratio[1] = denominator;
			
		    return ratio;	
		}
	}
}
	
	