import java.util.*;
import java.util.zip.*;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.lang.Math.*;
import java.math.*;

public class FactorWriter
{
	public static void main(String[] args)
	{
		FactorWriter test = new FactorWriter();
	}	
	
	public FactorWriter()
	{
		
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
	    
		/* 
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
	     
	    
	    
		int k = 5;
		
		byte [] message = new byte [640 * k];
		
		for(int i = 0; i < 640; i++)
		{
			for(int j = 0; j < k; j++)
			{
			    message[i] = (byte)(i % 9);
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
	 
	    int  [] factor_number = CodeMapper.getCommonFactorNumber(message, symbol_table, f, sum);
	   
	    try
		{
			DataOutputStream out = new DataOutputStream(new FileOutputStream(new File("foo")));
            
			out.writeInt(factor_number.length);
			for(int i = 0; i < factor_number.length; i++)
			    out.writeInt(factor_number[i]);
			out.flush();
			out.close();
		}
	    catch(Exception e)
	    {
	    	System.out.println(e.toString());
	    }
	    System.out.println("Finished writing file.");
	}
}