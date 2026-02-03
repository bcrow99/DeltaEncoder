import java.util.*;
import java.util.zip.*;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.lang.Math.*;
import java.math.*;

public class TestOffset
{
	public static void main(String[] args)
	{
		TestOffset test = new TestOffset();
	}	
	
	public TestOffset()
	{
		int xdim = 100;
		int ydim = 1;
		
		int size = xdim * ydim;
		byte [] message = new byte [size];
		
		Random random = new Random();
		
		double min = Double.MAX_VALUE;
		double max = Double.MIN_VALUE;
		ArrayList <Double> random_list = new ArrayList <Double> ();
		for(int i = 0; i < size; i++)
		{
			double value = random.nextGaussian(.5, .15);
			if(value < min)
				min = value;
			if(value > max)
				max = value;
			random_list.add(value);
		}
		
		double range = max - min;
		
		double range_factor = 1. / range;
		
		for(int i = 0; i < size; i++)
		{
			double value = random_list.get(i);
			value -= min;
			value *= range_factor;
			value *= 255;
			
			message[i] = (byte)value;
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
	   
	    for(int i = 0; i < 5; i++)
	    {
	    	    if(i == 0)
	    	        System.out.println("Frequency table in symbol order.");
	    	    else if(i == 1)
	    	    	    System.out.println("Frequency table in ascending order.");
	    	    else if(i == 2)
    	    	        System.out.println("Frequency table in descending order."); 
	    	    else if(i == 3)
	    	        System.out.println("Frequency table in first exhausted order.");
	    	    else if(i == 4)
	    	        System.out.println("Frequency table in last exhausted order.");
	        
	    	    long start = System.nanoTime();
	    
	    	    BigInteger [] offset = {BigInteger.ZERO, BigInteger.ONE};
	    	    if(i == 0)
	            offset = CodeMapper.getArithmeticOffset(message, symbol_table, f);
	    	    else
	    	    	    offset = CodeMapper.getArithmeticOffset(message, symbol_table, f, i);
	        
	        long stop = System.nanoTime();
		    long time = stop - start;
		    System.out.println("It took " + (time / 1000000) + " ms to calculate offset.");
	        
		    BigDecimal location = CodeMapper.getNormalFraction(offset[0], offset[1]);
	        System.out.println("Location in probabalistic space is " + String.format("%.4f", location));
	        
	        int precision = location.precision();
	        System.out.println("Number of location bits is " + (precision + 32));
	        System.out.println();
	 
	    }
        
	}
}