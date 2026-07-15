import java.awt.*;
import java.awt.image.*;
import java.awt.geom.*;
import java.io.*;
import java.awt.event.*;
import java.util.*;
import java.util.zip.*;
import java.lang.Math.*;
import javax.imageio.*;
import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

public class GetNoise
{
	public static void main(String[] args)
	{
		
		if (args.length != 1)
		{
			System.out.println("Usage: java GetNoise <type>");
			System.exit(0);
		}
		
		if(!args[0].equals("r") && !args[0].equals("g"))
		{
			System.out.println("Type not supported.");
			System.exit(0);	
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
		
		if(args[0].equals("r"))
		{
		    int xdim = 512;
		    int ydim = 256;
		    
		    BufferedImage grayscale = new BufferedImage(xdim, ydim, BufferedImage.TYPE_INT_RGB);
		   
		    for(int i = 0; i < ydim; i++)
		    {
		    	    for(int j = 0; j < xdim; j++)
		    	    {
		    	    	    int gray_value = j / 2;
		    	    	    
		    	    	    int rgb_value = ((gray_value&0x0ff)<<16)|((gray_value&0x0ff)<<8)|(gray_value&0x0ff);
		            	grayscale.setRGB(j, i, rgb_value);
		    	    }
		    }
		    
		    try 
	        {  
	            ImageIO.write(grayscale, "jpg", new File("C:/Users/bcrow/Desktop/random.jpg")); 
	        } 
	        catch(IOException e) 
	        {  
	            e.printStackTrace(); 
	        }  
		}
		else if(args[0].equals("g"))
		{
			int xdim = 256;
		    int ydim = 512;
		    
		    BufferedImage grayscale = new BufferedImage(xdim, ydim, BufferedImage.TYPE_INT_RGB);
		   
		    for(int i = 0; i < xdim; i++)
		    {
		    	    for(int j = 0; j < ydim; j++)
		    	    {
		    	    	    int gray_value = j / 2;
		    	    	    
		    	    	    int rgb_value = ((gray_value&0x0ff)<<16)|((gray_value&0x0ff)<<8)|(gray_value&0x0ff);
		            	grayscale.setRGB(i, j, rgb_value);
		    	    }
		    }
		    
		    try 
	        {  
	            ImageIO.write(grayscale, "jpg", new File("C:/Users/bcrow/Desktop/gaussian.jpg")); 
	        } 
	        catch(IOException e) 
	        {  
	            e.printStackTrace(); 
	        }        	
		}
	}
}