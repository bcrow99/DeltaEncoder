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
		byte [] message = new byte[10];
	    
	    message[0] = 0;
	    message[1] = 1;
	    message[2] = 0;
	    message[3] = 1;
	    
	    
	    message[4] = 1;
	    message[5] = 1;
	    message[6] = 0;
	    message[7] = 1;
	    message[8] = 1;
	    message[9] = 127;
	    
	    /*
	    message[10] = 4;
	    message[11] = 1;
	    
	   
	    message[12] = 0;
	    message[13] = 1;
	    message[14] = 1;
	    message[15] = 127;
	    message[16] = 0;
	    message[17] = 1;
	    message[18] = 1;
	    message[19] = 127;
	    
	   
		
		int k = 1;
		
		byte [] message = new byte [640 * k];
		
		for(int i = 0; i < 50; i++)
		{
			for(int j = 0; j < k; j++)
			{
			    message[i] = (byte)(i % 2);
			}
		}
		*/
		
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
	    BigInteger [] value = CodeMapper.getRangeQuotient(message, symbol_table, f, sum);
	    long stop = System.nanoTime();
		long time = stop - start;
		System.out.println("It took " + (time / 1000000) + " ms to produce v.");
		int length1        = value[0].bitLength();
	    int length2        = value[1].bitLength();
	    System.out.println("Numerator has bit length " + length1);
	    System.out.println("Denominator has bit length " + length2);
		
	    /*
	    BigDecimal location = new BigDecimal(value[0]);
	    try
	    {
	        BigDecimal divisor = new BigDecimal(value[1]);
	        location           = location.divide(divisor);
	     
	        System.out.println("Location of message in probabilistic space returned by getRangeQuotient is " + location);
	    }
	    catch(Exception e)
	    {
	    	    System.out.println("Exception dividing numerator by denominator:");
	    	    System.out.println(e.toString());
	    	    System.out.println("Numerator is " + value[0]);
	    	    System.out.println("Denominator is " + value[1]);
	    }
	    
		
		try
		{
			byte [] decoded_message = CodeMapper.getMessage2(value, inverse_table, f, sum, message.length);
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