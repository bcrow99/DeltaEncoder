import java.util.*;
import java.util.zip.*;
import java.lang.Math.*;
import java.math.*;

public class TestRange
{
	public static void main(String[] args)
	{
		TestRange test = new TestRange();
	}	
	
	public TestRange()
	{
		/*
		byte [] message = new byte[10];
	    
		message[0] = 0;
		message[1] = 1;
		message[2] = 0;
		message[3] = 1;
	  
		message[4] = 0;
		message[5] = 1;
		message[6] = 2;
		message[7] = 3;
		message[8] = 0;
		message[9] = 1;
		
		
	    message[10] = 2;
	    message[11] = 2;
	    message[12] = 0;
	    message[13] = 1;
	    message[14] = 2;
	    message[15] = 3;
	    message[16] = 0;
	    message[17] = 1;
	    message[18] = 2;
	    message[19] = 3;
	    */
		
	   
		int xdim = 160;
		int ydim = 120;
		
		byte [] message = new byte [xdim * ydim];
		
		for(int i = 0; i < ydim; i++)
		{
		    for(int j = 0; j < xdim; j++)
		    {
			    message[i * xdim + j] = (byte)(j);
			}
		}
		
		
	    boolean [] isSymbol = new boolean[256];
	    int     [] freq     = new int[256];
	    
	    int sum = 0;
	    for(int i = 0; i < message.length; i++)
	    {
	    	    int j = message[i];
	    	    if(j < 0)
	    	    	    j += 256;
	    	    isSymbol[j] = true;
	    	    freq[j]++;
	    	    sum++;
	    }
	    
	    int number_of_symbols = 0;
	    for(int i = 0; i < 256; i++)
	    {
	    	    if(isSymbol[i]) 
	    	    	    number_of_symbols++; 
	    }
	    
	    Hashtable <Integer, Integer> symbol_table =  new Hashtable <Integer, Integer>();
	    Hashtable <Integer, Integer> inverse_table = new Hashtable <Integer, Integer>();
	    int [] f = new int[number_of_symbols];
	    
	    int j = 0;
	    for(int i = 0; i < 256; i++)
	    {
	    	    if(isSymbol[i])
	    	    {
	    	    	    symbol_table.put(i, j);
	    	    	    inverse_table.put(j,  i);
	    	    	    f[j] = freq[i];
	    	    	    j++;
	    	    }
	    }
	 
	    long start = System.nanoTime();
	    BigInteger [] location = CodeMapper.getRangeQuotient(message, symbol_table, f);
	    long stop = System.nanoTime();
		long time = stop - start;
		System.out.println("It took " + (time / 1000000) + " ms to get probabalistic location.");
		System.out.println();
		/*
		double location = value[0];
		location       /= value[1];
		System.out.println("Location of message in probabilistic space returned by getRangeQuotient is " + location);
		*/
		try
		{
			System.out.println("Starting to decode message.");
			start = System.nanoTime();
			//byte [] decoded_message = CodeMapper.getMessage(location, inverse_table, f, sum, message.length);
			
			byte [] decoded_message = CodeMapper.getMessage(location, inverse_table, f);
			stop = System.nanoTime();
			time = stop - start;
			System.out.println("It took " + (time / 1000000) + " ms to decode message.");
		
			/*
			System.out.println("Decoded message from getMessage:");
		    for(int i = 0; i < decoded_message.length; i++)
		    	    System.out.print(decoded_message[i] + " ");
		    System.out.println();
		    */
		}
		catch(Exception e)
	    {
	    	    System.out.println("Exception decoding message:");
	    	    System.out.println(e.toString());
	    }
		
		/*
		int length1        = value[0].bitLength();
	    int length2        = value[1].bitLength();
        System.out.println("Numerator has bit length " + length1);
	    System.out.println("Denominator has bit length " + length2);
	    
	    int bitlength1 = 8 * message.length;
	    int bitlength2 = length1 + length2;
	   
	   
	    double compression = bitlength2;
	    compression       /= bitlength1;
	    
	    System.out.println("Ratio of message bitlength and quotient bitlength is " + compression);
	    
	    
	
	    BigInteger a = value[0];
	    BigInteger b = value[1];
	    
	    
	    
	    BigInteger k = BigInteger.TWO;
	    for(int i = 1; i < b.bitLength(); i++)
	    {
	    	    k = k.multiply(BigInteger.TWO);
	    }
	    
	    BigInteger n = a.multiply(k);
	    n = n.divide(b);
	    
	    BigInteger m = n.mod(b);
	    if(m.compareTo(k.divide(BigInteger.TWO)) == 1)
	    {
	    	    n = n.add(BigInteger.ONE);
	    }
	    
	    System.out.println("Length of normalized fraction was " + n);
	   
	    try
	    {
	    	    BigDecimal location = new BigDecimal(n);
	        BigDecimal divisor  = new BigDecimal(k);
	        location            = location.divide(divisor);
	     
	        System.out.println("Location of message in probabilistic space returned by getRangeQuotient is " + location);
	    }
	    catch(Exception e)
	    {
	    	    System.out.println("Exception dividing numerator by denominator to get decimal value:");
	    	    System.out.println(e.toString());
	    	    System.out.println("Numerator is " + n);
	    	    System.out.println("Denominator is " + k);
	    }
		
		try
		{
			start = System.nanoTime();
			byte [] decoded_message = CodeMapper.getMessage(value, inverse_table, f, sum, message.length);
			stop = System.nanoTime();
			time = stop - start;
			System.out.println("It took " + (time / 1000000) + " ms to decode message.");
			
			System.out.println("Decoded message from getMessage:");
		    for(int i = 0; i < decoded_message.length; i++)
		    	    System.out.print(decoded_message[i] + " ");
		    System.out.println();
		    
		   
		}
		catch(Exception e)
	    {
	    	    System.out.println("Exception decoding message:");
	    	    System.out.println(e.toString());
	    }
	    */
	}

}