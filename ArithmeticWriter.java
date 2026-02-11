import java.util.*;
import java.util.zip.*;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.lang.Math.*;
import java.math.*;
import java.util.concurrent.*;

public class ArithmeticWriter
{
	BigDecimal [][] arithmetic_offset;
	int        [][] frequency;
	
	int        number_of_processors;
	int        number_of_segments;
	int        segment_length;
	int        xdim, ydim;
	int        table_type;
	
	public static void main(String[] args)
	{
		ArithmeticWriter writer = new ArithmeticWriter();
	}	
	
	public ArithmeticWriter()
	{
		xdim = 16;
		ydim = 16;
		
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
		
	    number_of_processors = Runtime.getRuntime().availableProcessors();
		System.out.println("There are " + number_of_processors + " processors available.");
		
		int number_of_lines_per_processor = ydim / number_of_processors;
		number_of_segments                = number_of_lines_per_processor;
		
		int n              = number_of_processors;
		arithmetic_offset  = new BigDecimal[n][number_of_segments];
		frequency          = new int[n][256];
		segment_length     = message.length / (n * number_of_segments);
		byte [][] segment  = new byte[n][number_of_segments * segment_length];
		
		int k = 0;
		for(int i = 0; i < n; i++)
		{
			for(int j = 0; j < segment_length * number_of_segments; j++)
				segment[i][j] = message[k++];
		}
		
		long start = System.nanoTime();
		
		
		Thread [] encoder_thread = new Thread[n]; 
		for(int i = 0; i < n; i++) 
		{
		    encoder_thread[i] = new Thread(new ArithmeticEncoder(segment[i], i));
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
		
		int [][]        scale         = new int[number_of_processors][number_of_segments];
	    BigInteger [][] unscaled_value = new BigInteger[number_of_processors][number_of_segments];
		
	    
		System.out.println("Offsets:");
	    for(int i = 0; i < n; i++)
	    {
	    	    for(int j = 0; j < number_of_segments; j++)
	    	    {
	    	    	    System.out.print(String.format("%.6f", arithmetic_offset[i][j]) + " ");
	    	    	    scale[i][j]           = arithmetic_offset[i][j].scale();
	    	    	    unscaled_value[i][j] = arithmetic_offset[i][j].unscaledValue();
	    	    }
	    	    System.out.println();
	    }
	    
	    int min_scale = scale[0][0];
	    BigInteger min_unscaled_value = unscaled_value[0][0];
	    
	    for(int i = 0; i < n; i++)
	    {
	    	    for(int j = 0; j < number_of_segments; j++)
	    	    {
	    	    	    if(scale[i][j] < min_scale)
	    	    	    	    min_scale = scale[i][j];
	    	    	    if(unscaled_value[i][j].compareTo(min_unscaled_value) == -1)
			        	    min_unscaled_value = unscaled_value[i][j];  
	    	    }
	    }
	    
	    //System.out.println("Min scale is " + min_scale);
	    //System.out.println("Min unscaled value is " + min_unscaled_value);
	    
	    try
		{
			DataOutputStream out = new DataOutputStream(new FileOutputStream(new File("foo")));
			out.writeInt(number_of_processors);
			out.writeInt(number_of_segments);
			out.writeInt(segment_length);
			
			out.writeInt(min_scale);
			
			/*
			byte [] min_byte_array = min_unscaled_value.toByteArray();
			out.writeInt(min_byte_array.length);
	        out.write(min_byte_array, 0, min_byte_array.length);
			*/
			
			for(int i = 0; i < n; i++)
			{
				for(int j = 0; j < number_of_segments; j++)
				{
					int delta = scale[i][j] - min_scale;
					out.writeInt(delta);
					
					/*
					BigInteger delta2 = unscaled_value[i][j].subtract(min_unscaled_value);
					System.out.println("Unscaled value is     " + unscaled_value[i][j]);
					System.out.println("Min unscaled value is " + min_unscaled_value);
					System.out.println("Delta is              " + delta2);
					System.out.println();
					
					System.out.println("Bit length of unscaled value is     " + unscaled_value[i][j].bitLength());
					System.out.println("Bit length of min unscaled value is " + min_unscaled_value.bitLength());
					System.out.println("Bit length of delta is              " + delta2.bitLength());
					System.out.println();
					byte [] byte_array = delta2.toByteArray();
					*/
					byte [] byte_array = unscaled_value[i][j].toByteArray();
			        out.writeInt(byte_array.length);
			        out.write(byte_array, 0, byte_array.length);
				}
			}
			
			/*
			for(int i = 0; i < n; i++)
			{
				int [] current_table = frequency[i];
				for(int j = 0; j < 256; j++)
					out.writeInt(current_table[j]);
			}
			*/
			
			out.close();
			
			
		}
	    catch(Exception e)
	    {
	    	    System.out.println("Exception writing file:");
	    	    System.out.println(e.toString());
	    }
	    
	    long stop = System.nanoTime();
	    long time = stop - start;
	    System.out.println("It took " + (time / 1000000) + " ms to get offsets and frequency tables and write file.");
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
		    ArrayList  result = CodeMapper.getArithmeticOffsetList(src, number_of_segments);
		    
		    BigDecimal [] offset = (BigDecimal [])result.get(0);
		    
		    int []     freq   = (int [])result.get(1);  
		    
		    arithmetic_offset[index] = offset;
		    frequency[index]         = freq;
		}
	}

}