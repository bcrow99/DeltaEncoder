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

public class Test
{
	BufferedImage original_image;
	BufferedImage extracted_image;
	ImageCanvas   image_canvas;
	
	int           x, y;
	int           image_x_offset, image_y_offset;
	int           canvas_x_offset, canvas_y_offset;
	int           image_xdim, image_ydim;
	int           extracted_xdim, extracted_ydim;
	int           scaled_xdim, scaled_ydim;
	int           canvas_xdim, canvas_ydim;
	
	int           width, height;
	
	int []        pixel;
	
	ArrayList     channel_list;
	
	public static void main(String[] args)
	{
		if (args.length != 1)
		{
			System.out.println("Usage: java Test <filename>");
			System.exit(0);
		}
		
		//String prefix       = new String("");
		String prefix       = new String("C:/Users/Brian Crowley/Desktop/");
		String filename     = new String(args[0]);
		
		Test writer = new Test(prefix + filename);
	}

	public Test(String filename)
	{
		try
		{
			File file = new File(filename);
			original_image = ImageIO.read(file);
			image_xdim = original_image.getWidth();
			image_ydim = original_image.getHeight();
			
			System.out.println("Xdim is " + image_xdim);
			System.out.println("Xdim = " + image_xdim + ", ydim = " + image_ydim);
			
			
			int power_of_two_xdim = 2;
			while(power_of_two_xdim < image_xdim)
				power_of_two_xdim *= 2;
			
			System.out.println("Nearest power of two xdim is " + power_of_two_xdim);
			
			int power_of_two_ydim = 2;
			while(power_of_two_ydim < image_ydim)
				power_of_two_ydim *= 2;
			
			System.out.println("Nearest power of two ydim is " + power_of_two_ydim);
			System.out.println();
		  
			JFrame frame = new JFrame("Displaying " + filename);
			    
			WindowAdapter window_handler = new WindowAdapter()
			{
			    public void windowClosing(WindowEvent event)
			    {
			        System.exit(0);
			    }
			};
			frame.addWindowListener(window_handler);
			 
			image_canvas = new ImageCanvas();
			
			extracted_xdim = 440;
			extracted_ydim = 364;
			image_canvas.setSize(extracted_xdim, extracted_ydim);
			
			pixel = new int[extracted_xdim * extracted_ydim];
			
			extracted_image = original_image.getSubimage(0,  0,  extracted_xdim,  extracted_ydim);
			
			PixelGrabber pixel_grabber = new PixelGrabber(extracted_image, 0, 0, extracted_xdim, extracted_ydim, pixel, 0, extracted_xdim);
	        try
	        {
	            pixel_grabber.grabPixels();
	        }
	        catch(InterruptedException e)
	        {
	            System.err.println(e.toString());
	        }
	        if((pixel_grabber.getStatus() & ImageObserver.ABORT) != 0)
	        {
	            System.err.println("Error grabbing pixels.");
	            System.exit(1);
	        }
	       
	        int [] alpha = new int[extracted_xdim * extracted_ydim];
	        int [] blue  = new int[extracted_xdim * extracted_ydim];
	        int [] green = new int[extracted_xdim * extracted_ydim];
	        int [] red   = new int[extracted_xdim * extracted_ydim];
	        for(int i = 0; i < extracted_xdim * extracted_ydim; i++)
			{
		        alpha[i] = (pixel[i] >> 24) & 0xff;
			    blue[i]  = (pixel[i] >> 16) & 0xff;
			    green[i] = (pixel[i] >> 8) & 0xff; 
	            red[i]   =  pixel[i] & 0xff; 
			}
	        channel_list = new ArrayList();
	        channel_list.add(blue);
	        channel_list.add(green);
	        channel_list.add(red);
	        
	        int [] blue_wavelets = WaveletMapper.forward_transform(blue, extracted_xdim);
	        int [] blue2         = WaveletMapper.inverse_transform(blue_wavelets, extracted_xdim);
	        int [] pixel         = WaveletMapper.getPixel(blue, blue, blue, extracted_xdim);
	        extracted_image.setRGB(0, 0, extracted_xdim, extracted_ydim, pixel, 0, extracted_xdim);
	        
	        
			JPanel image_panel = new JPanel(new BorderLayout());
			
			image_panel.add(image_canvas, BorderLayout.CENTER);
		
			
			frame.getContentPane().add(image_panel);
			frame.pack();
			frame.setLocation(10, 10);
			frame.setSize(extracted_xdim, extracted_ydim);
			frame.setVisible(true);	
			 
		} 
		catch (Exception e)
		{
			System.out.println(e.getMessage());
			System.exit(1);
		}
	}
	
	class ImageCanvas extends Canvas
    {
        public synchronized void paint(Graphics g)
        {
        	g.drawImage(extracted_image, 0, 0, this);
        }
    }
}