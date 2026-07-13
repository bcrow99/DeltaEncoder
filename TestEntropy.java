import java.util.*;
import java.util.zip.*;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.lang.Math.*;
import java.math.*;

public class TestEntropy
{
	public static void main(String[] args)
	{
		TestEntropy test = new TestEntropy();
	}	
	
	public TestEntropy()
	{
		int xdim = 640;
		int ydim = 480;
		
		int     size  = xdim * ydim;
		int [] image = new int [size];
		
		for(int i = 0; i < ydim; i++)
		{
			for(int j = 0; j < xdim / 2; j++)
			{
				image[i * xdim + j] = 3;
			}
			for(int j = xdim / 2; j < xdim / 2 + xdim / 4; j++)
			{
				image[i * xdim + j] = 2;
			}
			for(int j = xdim / 2 + xdim / 4; j < xdim / 2 + xdim / 4 + xdim /8; j++)
			{
				image[i * xdim + j] = 1;
			}
		}
		
		/*
		Random random = new Random();
		
		double mean = 64;
		double std_dev = 64;
		
		
		double min = Double.MAX_VALUE;
		double max = Double.MIN_VALUE;
		ArrayList <Double> random_list = new ArrayList <Double> ();
		for(int i = 0; i < size; i++)
		{
			double value = mean + std_dev * random.nextGaussian();
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
			value *= 5;
			
			image[i] = (int)value;
		}
		*/
		
	    boolean [] isSymbol = new boolean[256];
	    int     [] freq     = new int[256];
	    
	    for(int i = 0; i < image.length; i++)
	    {
	    	    int j = image[i];
	    	    if(j < 0)
	    	    	    j += 256;
	    	    isSymbol[j] = true;
	    	    freq[j]++;
	    }
	    
	    int number_of_symbols = 0;
	    for(int i = 0; i < 256; i++)
	    {
	    	    if(isSymbol[i]) 
	    	    	    number_of_symbols++; 
	    }
	    
	    int [] f = new int[number_of_symbols];
	    
	    int j = 0;
	    for(int i = 0; i < 256; i++)
	    {
	    	    if(isSymbol[i])
	    	    {
	    	    	    f[j] = freq[i];
	    	    	    j++;
	    	    }
	    }
	    
	    double int_bitlength = CodeMapper.getShannonLimit(f);
	    int_bitlength = java.lang.Math.floor(int_bitlength);
	    
        ArrayList string_list = StringMapper.getStringList(image);
       
        byte [] byte_string = (byte [])string_list.get(3);
        
        int iterations = StringMapper.getIterations(byte_string);
        if(iterations == 0 || iterations == 16)
        	    System.out.println("String was not compressed.");
        else
        	    System.out.println("String was compressed.");
        
        int [] string = new int[byte_string.length];
        for(int i = 0; i < byte_string.length; i++)
        {
        	    string[i] = byte_string[i];
        	    if(string[i] < 0)
        	    	    string[i] += 256;
        }
        
        for(int i = 0; i < 256; i++)
	    {
        	    isSymbol[i] = false;
        	    freq[i]     = 0;
	    }
	    
	    for(int i = 0; i < string.length; i++)
	    {
	    	    j = string[i];
	    	    isSymbol[j] = true;
	    	    freq[j]++;
	    }
	    
	    number_of_symbols = 0;
	    for(int i = 0; i < 256; i++)
	    {
	    	    if(isSymbol[i]) 
	    	    	    number_of_symbols++; 
	    }
	    
	    f = new int[number_of_symbols];
	    
	    j = 0;
	    for(int i = 0; i < 256; i++)
	    {
	    	    if(isSymbol[i])
	    	    {
	    	    	    f[j] = freq[i];
	    	    	    j++;
	    	    }
	    }
	    
	    double string_bitlength = CodeMapper.getShannonLimit(f);
	    string_bitlength = java.lang.Math.floor(string_bitlength);
	   
	    if(string_bitlength < int_bitlength)
	    {
	    	    System.out.println("Shannon number less for strings than integers.");
	    	    int difference = (int)(int_bitlength - string_bitlength);
	    	    System.out.println("Difference is " + difference);
	    }
	    else
	    {
	      	System.out.println("Shannon number less for integers than strings.");
	      	int difference = (int)(string_bitlength - int_bitlength);
	      	System.out.println("Difference is " + difference);
	    }
	}
}