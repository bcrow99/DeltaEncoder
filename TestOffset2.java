import java.util.*;
import java.util.zip.*;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.lang.Math.*;
import java.math.*;

public class TestOffset2
{
	BigDecimal []   arithmetic_offset;
	int        [][] frequency;
	
	public static void main(String[] args)
	{
		TestOffset2 test = new TestOffset2();
	}	
	
	public TestOffset2()
	{
		int xdim = 256;
		int ydim = 48;
		
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
	   
	    int number_of_processors = Runtime.getRuntime().availableProcessors();
		System.out.println("There are " + number_of_processors + " processors available.");
		
		int n             = number_of_processors;
		arithmetic_offset = new BigDecimal[n];
		frequency         = new int[n][256];
		
		int segment_length = message.length / n;
		
		
		long start = System.nanoTime();
		Thread [] encoder_thread = new Thread[n]; 
		for(int i = 0; i < n; i++) 
		{
			byte [] segment   = new byte[segment_length];
			int offset        = segment_length * i;
			int stop          = offset + segment_length;
			int k = 0;
			for(j = offset; j < stop; j++)
			    segment[k++] = message[j];
		    encoder_thread[i] = new Thread(new ArithmeticEncoder(segment, i));
		    encoder_thread[i].start(); 
		} 
		
		for(int i = 0; i < n; i++)
		{
			try
			{
		        encoder_thread[i].join();
			}
			catch(Exception e)
			{
				System.out.println("Exception waiting for thread to finish:");
				System.out.println(e.toString());
			}
		}
		
		long stop = System.nanoTime();
	    long time = stop - start;
	    System.out.println("It took " + (time / 1000000) + " ms to get offsets and frequency tables.");
	    
	    System.out.println("Offsets:");
	    for(int i = 0; i < n; i++)
	    	    System.out.print(String.format("%.6f", arithmetic_offset[i]) + " ");
	    System.out.println();
	}
	
	class ArithmeticEncoder implements Runnable
	{
		byte [] src;
		int     index;

		public ArithmeticEncoder(byte [] src, int index)
		{
			this.src   = src;
			this.index = index;
		}

		public void run()
		{
		    ArrayList  result = CodeMapper.getArithmeticOffset2(src);
		    BigDecimal offset = (BigDecimal)result.get(0);
		    int []     freq   = (int [])result.get(1);  
		    
		    arithmetic_offset[index] = offset;
		    frequency[index]         = freq;
		}
	}
}