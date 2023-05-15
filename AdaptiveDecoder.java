import java.awt.*;
import java.awt.image.*;
import java.io.*;
import java.awt.event.*;
import java.util.*;
import java.util.zip.*;
import javax.imageio.*;
import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

public class AdaptiveDecoder
{
	BufferedImage image;
	JMenuItem     apply_item;
	
	JDialog       shift_dialog;
	JTextField    shift_value;
	
	ImageCanvas   image_canvas;
	
	int    xdim, ydim;
	String filename;
	int    [] channel_type, set_sum, channel_sum;
	int    [] channel_min;
	double [] channel_rate, set_rate;
	String [] type_string;
	String [] set_string;
	String [] channel_string;
	
	int    pixel_shift = 3;
	long   file_length = 0;
	boolean initialized = false;
	
	public static void main(String[] args)
	{
		if (args.length != 1)
		{
			System.out.println("Usage: java AdaptiveDecoder <filename>");
			System.exit(0);
		}
	    String prefix      = new String("");
	    //String prefix       = new String("C:/Users/Brian Crowley/Desktop/");
		String filename     = new String(args[0]);
		String java_version = System.getProperty("java.version");
		String os           = System.getProperty("os.name");
		String os_version   = System.getProperty("os.version");
		String machine      = System.getProperty("os.arch");
		//System.out.println("Current java version is " + java_version);
		//System.out.println("Current os is " + os + " " + os_version + " on " + machine);
		//System.out.println("Image file is " + filename);
		AdaptiveDecoder decoder = new AdaptiveDecoder(prefix + filename);
	}
	
	public AdaptiveDecoder(String _filename)
	{
		filename = _filename;
		xdim = 640;
		ydim = 480;
		
		String [] channel_string = new String[6];
		channel_string[0] = new String("blue");
		channel_string[1] = new String("green");
		channel_string[2] = new String("red");
		channel_string[3] = new String("blue-green");
		channel_string[4] = new String("red-green");
		channel_string[5] = new String("red-blue");
		
		String [] compression_string = new String[2];
		compression_string[0] = new String("segmented packed strings");
		compression_string[1] = new String("segmented compressed strings");
		
		
		Hashtable <Integer, int[]> image_table = new Hashtable<Integer, int[]>();
	
		int [] blue, green, red, blue_green, red_green, red_blue;
		
		// So the compiler doesn't complain about possible null assignments.
		blue       = new int[1];
	    green      = new int[1];
	    red        = new int[1];
        blue_green = new int[1];
        red_green  = new int[1]; 
        red_blue   = new int[1];
		try
		{
			File file              = new File(filename);
			DataInputStream in = new DataInputStream(new FileInputStream(file));
			xdim = in.readShort();
			ydim = in.readShort();
			System.out.println("Xdim from file is " + xdim + ", ydim is " + ydim);
			    
			// Now we initialize them.
			blue       = new int[xdim * ydim];
			green      = new int[xdim * ydim];
			red        = new int[xdim * ydim];
		    blue_green = new int[xdim * ydim];
		    red_green  = new int[xdim * ydim]; 
		    red_blue   = new int[xdim * ydim];
		        
		        
		    int [] delta = new int[xdim * ydim];
		    int  [] string_table = new int[1];
			    
			    
	       pixel_shift = in.readByte();
		   System.out.println("Pixel shift is " + pixel_shift);
		   System.out.println();
			    
		   for(int i = 0; i < 3; i++)
		   {
			   int channel = in.readByte();
			   System.out.println("Channel is " + channel_string[channel]);
			  
			   int channel_min = in.readInt();
			   int init_value = in.readInt();
			   int delta_min  = in.readInt();
			   //System.out.println("Init value is " + init_value + ", delta minimum is " + delta_min);
			    
			   int compression = in.readByte();
			   System.out.println("Compression type is " + compression_string[compression]);
			        
			   byte compression_bit_type = in.readByte();
			    
			   int table_length = 0;
			    
			   table_length = in.readShort();
			   //System.out.println("String table length is " +  table_length);
			    
			   string_table = new int[table_length];
			   for(int j = 0; j < table_length; j++)
			   {
			    	string_table[j] = in.readInt();
			   }
			   //System.out.println("Read string table.");
			   
			   int bitstring_length = 0;
			   int packed_length    = in.readInt();
			   int compressed_length = in.readInt();
			    //System.out.println("Bitstring length is " + bitstring_length);
			        
			}    
	    }
		catch(Exception e)
	    {
	    	System.out.println("e.toString()");
	    }
		image = new BufferedImage(xdim, ydim, BufferedImage.TYPE_INT_RGB);	
		/*
		for(int i = 0; i < ydim; i++)
		{
			for(int j = 0; j < xdim; j++)
			{
			    int _blue = blue[i * xdim + j];
			    _blue <<= pixel_shift;
			    	
			    int _green = green[i * xdim + j];
			    _green <<= pixel_shift;
			    
			    int _red   = red[i * xdim + j]; 
			    _red <<= pixel_shift;
			    	
			    int pixel = 0;
			    pixel |= _blue << 16;
	            pixel |= _green << 8;    
	            pixel |= _red;	
	                
			    image.setRGB(j, i, pixel);
			}
		}
        */
		
		JFrame frame = new JFrame("AdaptiveDecoder");
		WindowAdapter window_handler = new WindowAdapter()
		{
			public void windowClosing(WindowEvent event)
			{
			    System.exit(0);
			}
		};
		frame.addWindowListener(window_handler);
			    
		ImageCanvas image_canvas = new ImageCanvas();
		image_canvas.setSize(xdim, ydim);
		frame.getContentPane().add(image_canvas, BorderLayout.CENTER);
			
				
		frame.pack();
		frame.setLocation(400, 200);
		frame.setVisible(true);
	}
	    
	class ImageCanvas extends Canvas
    {
        public synchronized void paint(Graphics g)
        {
            g.drawImage(image, 0, 0, this);
        }
    }
	    
}