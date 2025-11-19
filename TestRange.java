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
	    message[0]      = 4;
	    message[1]      = 1;
	    message[2]      = 0;
	    message[3]      = 1;
	    message[4]      = 1;
	    message[5]      = 1;
	    message[6]      = 0;
	    message[7]      = 1;
	    message[8]      = 1;
	    message[9]      = 12;
	    
	    /*
	    message[10]     = 3;
	    message[11]     = 1;
	    message[12]     = 0;
	    message[13]     = 1;
	    message[14]     = 0;
	    message[15]     = 1;
	    message[16]     = 0;
	    message[17]     = 1;
	    message[18]     = 0;
	    message[19]     = 2;
		
	    
		byte [] message = new byte[2];
		message[0] = 0;
		message[1] = 1;
	    
	   
	    int [] f = new int [] {5, 5};
	    int    sum    = 10;
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
	 
	    long [] value   = CodeMapper.getRangeQuotient(message, symbol_table, f, sum);
	    double location = value[0];
	    location       /= value[1];
	    System.out.println("Location of message in probabilistic space returned by getRangeQuotient is " + location);
	    
	    byte [] decoded_message = CodeMapper.getMessage(value, inverse_table, f, sum, message.length);
	    System.out.println("Decoded message from getMessage with longs:");
	    for(int i = 0; i < decoded_message.length; i++)
    	      System.out.print(decoded_message[i] + " ");
        System.out.println();
        System.out.println();
	    
        /*
	    BigInteger [] value2 = CodeMapper.getRangeQuotient2(message, f, sum);
	    BigDecimal location2 = new BigDecimal(value2[0]);
	    BigDecimal divisor   = new BigDecimal(value2[1]);
	    location2 = location2.divide(divisor);
	    System.out.println("Location of message in probabilistic space returned by getRangeQuotient2 is " + location2);
	   
	    
	    byte [] decoded_message = CodeMapper.getMessage(value2, f, sum, message.length);
	    System.out.println("Decoded message from getMessage with BigIntegers:");
	    for(int i = 0; i < decoded_message.length; i++)
	    	    System.out.print(decoded_message[i] + " ");
	    System.out.println();
	    */
	   
	}
}