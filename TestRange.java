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
	    
		
		/*byte [] message = new byte[10];
	    
	    message[0] = 4;
	    message[1] = 1;
	    message[2] = 0;
	    message[3] = 1;
	    message[4] = 1;
	    message[5] = 1;
	    message[6] = 0;
	    message[7] = 1;
	    message[8] = 1;
	    message[9] = 127;
	    */
	   
		byte [] message = new byte [640 * 2];
		
		for(int i = 0; i < 640; i++)
		{
			for(int j = 0; j < 2; j++)
			{
			    message[i] = (byte)(i * j % 256);
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
	 
	   
	    BigInteger [] value = CodeMapper.getRangeQuotient(message, symbol_table, f, sum);
	    System.out.println("Got here.");
	    /*
	    BigDecimal location = new BigDecimal(value[0]);
	    BigDecimal divisor  = new BigDecimal(value[1]);
	    location            = location.divide(divisor);
	    System.out.println("Location of message in probabilistic space returned by getRangeQuotient is " + location);
	   
	    
	    byte [] decoded_message = CodeMapper.getMessage(value, inverse_table, f, sum, message.length);
	    System.out.println("Decoded message from getMessage:");
	    for(int i = 0; i < decoded_message.length; i++)
	    	    System.out.print(decoded_message[i] + " ");
	    System.out.println();
	    */
	   
	}
}