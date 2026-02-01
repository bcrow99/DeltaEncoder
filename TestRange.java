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
		int xdim = 20;
		int ydim = 1;
		byte [] message = new byte [xdim * ydim];
		
		/*
		for(int i = 0; i < ydim; i++)
		{
		    for(int j = 0; j < xdim; j++)
		    {
			    message[i * xdim + j] = (byte)(j % 8);
			}
		}
		
		
		message[0] = 0;
		message[1] = 1;
	    message[2] = 1;
		message[3] = 2;
	    message[4] = 2;
		message[5] = 2;
		message[6] = 3;
		message[7] = 3;
		message[8] = 3;
		message[9] = 3;
		  
		message[10] = 4;
		message[11] = 4;
		message[12] = 4;
		message[13] = 4;
		message[14] = 4;
		message[15] = 5;
		message[16] = 5;
		message[17] = 5;
		message[18] = 5;
		message[19] = 5;
		
		
        byte [] message = new byte[20];
	   
         
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
	    */
	    
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
        message[20] = 3;
		message[21] = 3;
		message[22] = 3;
		message[23] = 3;
	  
		message[24] = 2;
		message[25] = 2;
		message[26] = 2;
		
		message[27] = 1;
		message[28] = 1;
		
		message[29] = 0;
		
		
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
	    
	    
	    /*
	    BigInteger [] v = CodeMapper.getRangeQuotient2(message, symbol_table, f, 4);
        
	    BigDecimal location = CodeMapper.getNormalFraction(v[0], v[1]);
        System.out.println("Location in probabalistic space is " + String.format("%.4f", location));
 
	    int number_of_bits = v[0].bitLength();
	
	    BigInteger delta = v[1].subtract(v[0]);
        //number_of_bits    += v[1].bitLength();
	    number_of_bits    += delta.bitLength();
	
	    System.out.println("Offset      " + v[0]);
	    System.out.println("Denominator " + v[1]);
	    System.out.println("Delta       " + delta);
	
        System.out.println("Number of arithmetic encoding bits is " + number_of_bits);
        System.out.println();
	    */
	    
	    BigInteger [] offset = CodeMapper.getRangeQuotient(message, symbol_table, f);
	    int number_of_bits = offset[0].bitLength();
	    number_of_bits    += offset[1].bitLength();
	    System.out.println("Number of bits for offset is " + number_of_bits);
	    System.out.println();
	    
	    for(int i = 0; i < 5; i++)
	    {
	    	    if(i == 0)
	    	        System.out.println("Frequency table in symbol order.");
	    	    else if(i == 1)
	    	    	    System.out.println("Frequency table in descending order.");
	    	    else if(i == 2)
    	    	        System.out.println("Frequency table in ascending order."); 
	    	    else if(i == 3)
	    	        System.out.println("Frequency table in first exhausted order.");
	    	    else if(i == 4)
	    	        System.out.println("Frequency table in last exhausted order.");
	        
	    	    long start = System.nanoTime();
	    
	        BigInteger [] v = CodeMapper.getRangeQuotient2(message, symbol_table, f, i);
	        
	        long stop = System.nanoTime();
		    long time = stop - start;
		    System.out.println("It took " + (time / 1000000) + " ms to calculate quotient.");
	        
		    BigDecimal location = CodeMapper.getNormalFraction(v[0], v[1]);
	        System.out.println("Location in probabalistic space is " + String.format("%.4f", location));
	 
		    number_of_bits = v[0].bitLength();
		
		    BigInteger delta = v[1].subtract(v[0]);
	        number_of_bits    += v[1].bitLength();
		    //number_of_bits    += delta.bitLength();
		
		    System.out.println("Value       " + v[0]);
		    System.out.println("Denominator " + v[1]);
		    System.out.println("Delta       " + delta);
		
	        System.out.println("Number of arithmetic encoding bits is " + number_of_bits);
	        System.out.println();
	    }
	    
	    /*
	    byte [] message2 = CodeMapper.getMessage(v, inverse_table, f, order);
	    System.out.println("Decoded message:");
	    for(int i = 0; i < message2.length; i++)
	    	    System.out.print(message2[i] + " ");
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