import java.util.*;
import java.util.zip.*;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
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
		
		byte [] message = new byte[20];
	    
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
        
		
        /*
		int xdim = 256;
		int ydim = 2;
		
		byte [] message = new byte [xdim * ydim];
		
		for(int i = 0; i < 1; i++)
		{
		    for(int j = 0; j < xdim; j++)
		    {
			    message[i * xdim + j] = (byte)(8 - j % 8);
			}
		}
		
		for(int i = 1; i < 2; i++)
		{
		    for(int j = 0; j < xdim; j++)
		    {
			    message[i * xdim + j] = (byte)(j % 8);
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
	    
	    
	    double bitlength = CodeMapper.getShannonLimit(f);
	    
	    System.out.println("Number of message bytes is " + message.length);
	    System.out.println("Shannon number of bits is " + String.format("%.1f", bitlength));
	    System.out.println();
	    
	  
	    ArrayList result = CodeMapper.getRangeQuotient3(message, 0, 10, symbol_table, f);
	    
	    BigInteger [] v  = (BigInteger [])result.get(0);
	    int        [] f2 = (int [])result.get(1);
	    
	    
	    BigInteger a = v[0];
	    BigInteger b = v[1];
	  
	    BigDecimal location = CodeMapper.getNormalFraction(a, b);
	    System.out.println("Location of message in probabilistic space returned by getRangeQuotient3 is " + String.format("%.8f", location));
	    
	    try
		{
			long start = System.nanoTime();
			byte [] decoded_message = CodeMapper.getMessage(v, inverse_table, f, sum, 10);
			long stop = System.nanoTime();
			long time = stop - start;
			//System.out.println("It took " + (time / 1000000) + " ms to decode message.");
			
			System.out.println("Decoded message from getMessage:");
		    for(int i = 0; i < decoded_message.length; i++)
		    	    System.out.print(decoded_message[i] + " ");
		    System.out.println();
		    System.out.println();
		}
		catch(Exception e)
	    {
	    	    System.out.println("Exception decoding message:");
	    	    System.out.println(e.toString());
	    	    System.out.println();
	    }
	    
	    result = CodeMapper.getRangeQuotient3(message, 10, 10, symbol_table, f2);
	    v  = (BigInteger [])result.get(0);
	    int [] f3 = (int [])result.get(1);
	    
	    a = v[0];
	    b = v[1];
	    
	    location = CodeMapper.getNormalFraction(a, b);
	    System.out.println("Location of message in probabilistic space returned by getRangeQuotient3 is " + String.format("%.8f", location));
	  
	    try
		{
			long start = System.nanoTime();
			
			sum = 0;
			for(int i = 0; i < f2.length; i++)
			{
				sum += f2[i];
			}
			
			byte [] decoded_message = CodeMapper.getMessage(v, inverse_table, f2, sum, 10);
			long stop = System.nanoTime();
			long time = stop - start;
			//System.out.println("It took " + (time / 1000000) + " ms to decode message.");
			
			System.out.println("Decoded message from getMessage:");
		    for(int i = 0; i < decoded_message.length; i++)
		    	    System.out.print(decoded_message[i] + " ");
		    System.out.println();
		    System.out.println();
		}
		catch(Exception e)
	    {
	    	    System.out.println("Exception decoding message:");
	    	    System.out.println(e.toString());
	    }
		
	 
	    /*
	    long start = System.nanoTime();
	    
	    ArrayList result = CodeMapper.getNormalRangeQuotient(message, symbol_table, f);
	    long stop = System.nanoTime();
	    long time = stop - start;
	    System.out.println("It took " + (time / 1000000) + " ms to get normal range quotient.");
	    
	    byte [] bit_buffer = (byte[])result.get(0);
	    int     bit_length = (int)result.get(1);
	    
	    long x = 0;
	    long y = 1;
	    
	    for(int position = bit_length - 1; position >= 0; position--)
	    {
	    	    int value = SegmentMapper.getBit(bit_buffer, position);
	    	    if(value == 1)
	    	    	    x += y;
	    	    y *= 2;
	    }
	    
	    double location = x;
	    location /= y;
	    System.out.println("The probabilistic location is " + location);
	    
	    
	    long [] location = {x, y};
	    
	    try
		{
			System.out.println("Starting to decode message.");
			start = System.nanoTime();
			//byte [] decoded_message = CodeMapper.getMessage(location, inverse_table, f, sum, message.length);
			
			byte [] decoded_message = CodeMapper.getMessage4(location, inverse_table, f);
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