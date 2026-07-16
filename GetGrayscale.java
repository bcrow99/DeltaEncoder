import java.awt.image.*;
import java.io.*;
import javax.imageio.*;
import java.awt.Graphics2D;
import java.awt.geom.AffineTransform;

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
		    	    	    int rgb_value  = ((gray_value & 0x0ff) << 16) | ((gray_value & 0x0ff) << 8) |(gray_value & 0x0ff);
		            	grayscale.setRGB(j, i, rgb_value);
		    	    }
		    }
		    
		    try 
	        {  
	            ImageIO.write(grayscale, "png", new File("C:/Users/bcrow/Desktop/horizontal.png")); 
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
		    	    	    
		    	    	    int rgb_value = ((gray_value & 0x0ff) << 16) | ((gray_value & 0x0ff) << 8) | (gray_value&0x0ff);
		            	grayscale.setRGB(i, j, rgb_value);
		    	    }
		    }
		    
		    try 
	        {  
	            ImageIO.write(grayscale, "png", new File("C:/Users/bcrow/Desktop/vertical.png")); 
	        } 
	        catch(IOException e) 
	        {  
	            e.printStackTrace(); 
	        }        	
		}
		else if(args[0].equals("f") || args[0].equals("b"))
		{
			int xdim = 2048;
		    int ydim = 2048;
		    
		    BufferedImage grayscale = new BufferedImage(xdim, ydim, BufferedImage.TYPE_INT_RGB);
		   
		    for(int i = 0; i < ydim; i++)
		    {
		    	    for(int j = 0; j < xdim; j++)
		    	    {
		    	    	    int gray_value = j / 8;
		    	    	    int rgb_value  = ((gray_value & 0x0ff) << 16) | ((gray_value & 0x0ff) << 8) | (gray_value & 0x0ff);
		            	grayscale.setRGB(j, i, rgb_value);
		    	    }
		    }
			
		    if(args[0].equals("b"))	
		    {
		    	    double degree = -45.;
		    	    double rad = Math.toRadians(degree);
		        double sin = Math.abs(Math.sin(rad));
		        double cos = Math.abs(Math.cos(rad));
		        int new_xdim = (int) Math.floor(xdim * cos + ydim * sin);
		        int new_ydim = (int) Math.floor(ydim * cos + xdim * sin);
		    	    
		        BufferedImage rotated = new BufferedImage(new_xdim, new_ydim, grayscale.getType());
		        
		       
		        Graphics2D g2d = rotated.createGraphics();
		        //g2d.setColor(java.awt.Color.WHITE); // or whatever background you want
		        //g2d.fillRect(0, 0, new_xdim, new_ydim);
		        
		        AffineTransform at = new AffineTransform();
		        at.translate((new_xdim - xdim) / 2.0, (new_ydim - ydim) / 2.0); // Center the image
		        at.rotate(rad, xdim / 2.0, ydim / 2.0); 
		        
		        g2d.setTransform(at);
		        g2d.drawImage(grayscale, 0, 0, null);
		        
		        int extracted_xdim = 1024;
		        int extracted_ydim = 1024;
		        
		        int x_margin = (new_xdim - extracted_xdim) / 2;
		        int y_margin = (new_ydim - extracted_ydim) / 2;
		      
		        BufferedImage extracted = rotated.getSubimage(x_margin,  y_margin,  extracted_xdim,  extracted_ydim);
		        	
		        g2d.dispose();
		    	    
		        
			    try 
		        {  
		            ImageIO.write(extracted, "png", new File("C:/Users/bcrow/Desktop/back_diagonal.png")); 
		        } 
		        catch(IOException e) 
		        {  
		            e.printStackTrace(); 
		        }  
		    }
		    else
		    {
		      	double degree = 45.;
	    	        double rad = Math.toRadians(degree);
	            double sin = Math.abs(Math.sin(rad));
	            double cos = Math.abs(Math.cos(rad));
	            int new_xdim = (int) Math.floor(xdim * cos + ydim * sin);
	            int new_ydim = (int) Math.floor(ydim * cos + xdim * sin);
	    	    
	            BufferedImage rotated = new BufferedImage(new_xdim, new_ydim, grayscale.getType());
	        
	       
	            Graphics2D g2d = rotated.createGraphics();
	            //g2d.setColor(java.awt.Color.WHITE); // or whatever background you want
	            //g2d.fillRect(0, 0, new_xdim, new_ydim);
	        
	            AffineTransform at = new AffineTransform();
	            at.translate((new_xdim - xdim) / 2.0, (new_ydim - ydim) / 2.0); // Center the image
	            at.rotate(rad, xdim / 2.0, ydim / 2.0); 
	        
	            g2d.setTransform(at);
	            g2d.drawImage(grayscale, 0, 0, null);
	        
	            int extracted_xdim = 1024;
	            int extracted_ydim = 1024;
	        
	            int x_margin = (new_xdim - extracted_xdim) / 2;
	            int y_margin = (new_ydim - extracted_ydim) / 2;
	      
	            BufferedImage extracted = rotated.getSubimage(x_margin,  y_margin,  extracted_xdim,  extracted_ydim);
	        	
	            g2d.dispose();
	    	    
		        try 
	            {  
	                ImageIO.write(extracted, "png", new File("C:/Users/bcrow/Desktop/forward_diagonal.png")); 
	            } 
	            catch(IOException e) 
	            {  
	                e.printStackTrace(); 
	            }     
		    }	
		}
		else
		{
			System.out.println("Orientation not supported.");
		}
	}
}