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
	    System.out.println("Number of shannon bits is " + String.format("%.1f", bitlength));
	    
	    
	    ArrayList <BigInteger []> result = CodeMapper.getQuotientList(message, 10);
	    //System.out.println("Size of list is " + result.size());
	    
	    
	   
	    int number_of_bits = 0;
	    for(int i = 0; i < result.size(); i++)
	    {
	    	    BigInteger [] v = result.get(i);
	    	    number_of_bits += v[0].bitLength();
	    	    number_of_bits += v[1].bitLength();
	    	    
	    }
	    
	    System.out.println("Number of arithmetic encoding bits is " + number_of_bits);
	    System.out.println();
	  
	    for(int i = 0; i < result.size(); i++)
	    {
	    	   BigInteger [] v = result.get(i);  
	    	   ArrayList result2 = CodeMapper.getMessage2(v, inverse_table, f, 10);
	    	   
	    	   byte [] message2 = (byte [])result2.get(0);
	    	   int  [] f2       = (int [])result2.get(1);
	    	   f                = f2;
	    	   
	    	   System.out.println("Decoded message from getMessage2:");
		   for(j = 0; j < message2.length; j++)
			    	System.out.print(message2[j] + " ");
		   System.out.println();
	    }
	}
}