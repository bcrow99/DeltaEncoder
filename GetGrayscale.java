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

public class GetGrayscale
{
	public static void main(String[] args)
	{
		
		if (args.length != 1)
		{
			System.out.println("Usage: java GetGrayscale <orientation>");
			System.exit(0);
		}
		
		if(args[0].equals("h"))
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
	            ImageIO.write(grayscale, "jpg", new File("C:/Users/bcrow/Desktop/horizontal.jpg")); 
	        } 
	        catch(IOException e) 
	        {  
	            e.printStackTrace(); 
	        }  
		}
		else if(args[0].equals("v"))
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
	            ImageIO.write(grayscale, "jpg", new File("C:/Users/bcrow/Desktop/vertical.jpg")); 
	        } 
	        catch(IOException e) 
	        {  
	            e.printStackTrace(); 
	        }        	
		}
		else
		{
			System.out.println("Orientation not supported.");
		}
	}
}