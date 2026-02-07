import java.util.*;
import java.util.zip.*;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.lang.Math.*;
import java.math.*;

public class TestOffset2
{
	public static void main(String[] args)
	{
		TestOffset2 test = new TestOffset2();
	}	
	
	public TestOffset2()
	{
		int xdim = 256;
		int ydim = 100;
		
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
	   
	    long start = System.nanoTime();
	    ArrayList result = CodeMapper.getArithmeticOffsetList(message, ydim);
	    long stop = System.nanoTime();
	    long time = stop - start;
	    System.out.println("It took " + (time / 1000000) + " ms to get list of offsets and frequency tables.");
	    ArrayList <BigDecimal> offset_list = (ArrayList <BigDecimal>)result.get(0);
	    
	    System.out.println("Printing offsets:");
	    for(int i = 0; i < ydim; i++)
	    {
	    	    BigDecimal offset = offset_list.get(i);
	    	    System.out.print(String.format("%.6f", offset) + " ");
	    }
	    System.out.println();
        
	    /*
	    BigDecimal location = CodeMapper.getNormalFraction(offset[0], offset[1]);
        System.out.println("Location in probabilistic space is " + String.format("%.4f", location));
        
        int precision = location.precision();
        System.out.println("Number of location bits is " + (precision + 32));
        System.out.println();
        */
        
	}
}