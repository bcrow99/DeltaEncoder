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
		
		int xdim = 512;
		int ydim = 256;
		int size = xdim * ydim * 3;
		
		
		Random random = new Random();
		ArrayList <Integer> random_list = new ArrayList <Integer> ();
		
		if(args[0].equals("r"))
		{
		    for(int i = 0; i < size; i++)	
		    {
		    	    int value = random.nextInt(256);
		    	    random_list.add(value);
		    }
		}
		else
		{
			double mean    = 128;
			double std_dev = 42;
			
			for(int i = 0; i < size; i++)
			{
			    double value = mean + std_dev * random.nextGaussian();
			    if(value < 0.)
				    value = 0;
			    else if(value > 255.)
				    value = 255;
			    random_list.add((int)value);	
			}
		}
		
		BufferedImage noise = new BufferedImage(xdim, ydim, BufferedImage.TYPE_INT_RGB);
		
		int k = 0;
	    for(int i = 0; i < ydim; i++)
	    {
	    	    for(int j = 0; j < xdim; j++)
	    	    {
	    	    	    int blue  = random_list.get(k++);
	    	    	    int green = random_list.get(k++);
	    	    	    int red   = random_list.get(k++);
	    	    	    
	    	    	    int rgb_value = ((blue & 0x0ff) << 16) |((green & 0x0ff) << 8) | (red & 0x0ff);
	            	noise.setRGB(j, i, rgb_value);
	    	    }
	    }

		if(args[0].equals("r"))
		{
		    try 
	        {  
	            ImageIO.write(noise, "png", new File("C:/Users/bcrow/Desktop/random.png")); 
	        } 
	        catch(IOException e) 
	        {  
	            e.printStackTrace(); 
	        }  
		}
		else if(args[0].equals("g"))
		{
		    try 
	        {  
	            ImageIO.write(noise, "png", new File("C:/Users/bcrow/Desktop/gaussian.png")); 
	        } 
	        catch(IOException e) 
	        {  
	            e.printStackTrace(); 
	        }        	
		}
	}
}