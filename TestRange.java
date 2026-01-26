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
		int xdim = 10;
		int ydim = 1;
		
	    /*
		byte [] message = new byte [xdim * ydim];
		
		for(int i = 0; i < ydim; i++)
		{
		    for(int j = 0; j < xdim; j++)
		    {
			    message[i * xdim + j] = (byte)(j);
			}
		}
		*/
		
		
        byte [] message = new byte[10];
	   
        /*
	    message[0] = 2;
	    message[1] = 2;
	    message[2] = 0;
	    message[3] = 1;
	    message[4] = 2;
	    message[5] = 3;
	    message[6] = 0;
	    message[7] = 1;
	    message[8] = 2;
	    message[9] = 3;
	    
	    message[10] = 0;
		message[11] = 1;
		message[12] = 0;
		message[13] = 1;
	  
		message[14] = 0;
		message[15] = 1;
		message[16] = 2;
		message[17] = 3;
		message[18] = 0;
		message[19] = 1;
		*/
        
        
        message[0] = 3;
		message[1] = 3;
		message[2] = 3;
		message[3] = 3;
	  
		message[4] = 2;
		message[5] = 2;
		message[6] = 2;
		
		message[7] = 1;
		message[8] = 1;
		
		message[9] = 0;
		/*
        message[0] = 0;
		message[1] = 0;
		message[2] = 0;
		message[3] = 0;
	  
		message[4] = 1;
		message[5] = 1;
		message[6] = 1;
		
		message[7] = 2;
		message[8] = 2;
		
		message[9] = 3;
        */
        
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
	    System.out.println();
	    
	    
	    long start = System.nanoTime();
	    
	    BigInteger [] v = CodeMapper.getRangeQuotient(message, symbol_table, f);
	    
	    BigDecimal location = CodeMapper.getNormalFraction(v[0], v[1]);
	    System.out.println("Location in probalistic space is " + location);
	    
	    long stop = System.nanoTime();
		long time = stop - start;
		System.out.println("It took " + (time / 1000000) + " ms to calculate quotients.");
		
		int number_of_bits = v[0].bitLength();
	    number_of_bits    += v[1].bitLength();
	    System.out.println("Number of arithmetic encoding bits is " + number_of_bits);
	    System.out.println();
	    
	    byte [] message2 = CodeMapper.getMessage(v, inverse_table, f);
	    System.out.println("Decoded message:");
	    for(int i = 0; i < message2.length; i++)
	    	    System.out.print(message2[i] + " ");
        System.out.println();
	    /*
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
	    	   System.out.println("Message length is " + message2.length);
	    	   int  [] f2       = (int [])result2.get(1);
	    	   f                = f2;
	    	   
	    	  
	    	   System.out.println("Decoded message from getMessage2:");
		   for(j = 0; j < message2.length; j++)
			    	System.out.print(message2[j] + " ");
		   System.out.println();
		  
	    }
	    stop = System.nanoTime();
	    time = stop - start;
	    System.out.println("It took " + (time / 1000000) + " ms to decode message.");
	    */
	}
}