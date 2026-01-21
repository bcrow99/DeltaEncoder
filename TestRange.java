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
		int xdim = 256;
		int ydim = 2;
		
	
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
	    
	    double bitlength = CodeMapper.getShannonLimit(f);
	    
	    System.out.println("Number of message bytes is " + message.length);
	    System.out.println("Number of shannon bits is " + String.format("%.1f", bitlength));
	    System.out.println();
	    
	    long start = System.nanoTime();
	    ArrayList <BigInteger []> result = CodeMapper.getQuotientList(message, xdim);
	    int number_of_bits = 0;
	    for(int i = 0; i < result.size(); i++)
	    {
	    	    BigInteger [] v = result.get(i);
	    	    number_of_bits += v[0].bitLength();
	    	    number_of_bits += v[1].bitLength();
	    	    
	    }
	    long stop = System.nanoTime();
		long time = stop - start;
		System.out.println("It took " + (time / 1000000) + " ms to calculate quotients.");
	    System.out.println("Number of arithmetic encoding bits is " + number_of_bits);
	    System.out.println();
	  
	    start = System.nanoTime();
	    for(int i = 0; i < result.size(); i++)
	    {
	    	   BigInteger [] v = result.get(i);  
	    	   ArrayList result2 = CodeMapper.getMessage2(v, inverse_table, f, 10);
	    	   
	    	   byte [] message2 = (byte [])result2.get(0);
	    	   int  [] f2       = (int [])result2.get(1);
	    	   f                = f2;
	    	   
	    	   /*
	    	   System.out.println("Decoded message from getMessage2:");
		   for(j = 0; j < message2.length; j++)
			    	System.out.print(message2[j] + " ");
		   System.out.println();
		   */
	    }
	    stop = System.nanoTime();
	    time = stop - start;
	    System.out.println("It took " + (time / 1000000) + " ms to decode message.");
	}
}