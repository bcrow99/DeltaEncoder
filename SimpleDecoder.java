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

public class SimpleDecoder
{
	BufferedImage image;
	JMenuItem     apply_item;
	
	JDialog       shift_dialog;
	JTextField    shift_value;
	
	ImageCanvas   image_canvas;
	
	int    xdim, ydim;
	String filename;
	
	int [] original_pixel;
	int [] alpha;
	int [] red;
	int [] green;
	int [] blue;
	
	byte   [] delta_bytes, delta_strings, zipped_strings; 
	byte   [] compressed_strings, zipped_compressed_strings;
	short  [] init, delta_min;
	int    [] channel_type, set_sum, channel_sum;
	double [] channel_rate, set_rate;
	String [] type_string;
	String [] set_string;
	String [] channel_string;
	int    [] shifted_blue, shifted_green, shifted_red;
	int    [] shifted_blue_green, shifted_red_green, shifted_red_blue;
	
	ArrayList lut_list;
	
	
	int    pixel_shift = 3;
	long   file_length = 0;
	boolean initialized = false;
	
	public static void main(String[] args)
	{
		if (args.length != 1)
		{
			System.out.println("Usage: java SimpleDecoder <filename>");
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
		SimpleDecoder decoder = new SimpleDecoder(prefix + filename);
	}

	public SimpleDecoder(String _filename)
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
		
		String [] compression_string = new String[5];
		compression_string[0] = new String("zipped bytes");
		compression_string[1] = new String("strings");
		compression_string[2] = new String("zipped strings");
		compression_string[3] = new String("compressed_strings");
		compression_string[4] = new String("zipped compressed strings");
		
		try
		{
			File file              = new File(filename);
			try
			{
			    DataInputStream in = new DataInputStream(new FileInputStream(file));
			    xdim = in.readShort();
			    ydim = in.readShort();
			    System.out.println("Xdim from file is " + xdim + ", ydim is " + ydim);
			    
			    int channel = in.readByte();
			    System.out.println("Channel is " + channel_string[channel]);
			    
			    int init_value = in.readShort();
			    int delta_min  = in.readShort();
			    System.out.println("Init value is " + init_value + ", delta minimum is " + delta_min);
			    
			   
			    int compression = in.readByte();
			    System.out.println("Compression type is " + compression_string[compression]);
			    
			    
			}
			catch(Exception e)
			{
				System.out.println(e.toString());
			}
			
			
			image = new BufferedImage(xdim, ydim, BufferedImage.TYPE_INT_RGB);
			   
			for(int i = 0; i < xdim; i++)
			{
			    for(int j = 0; j < ydim; j++)
			    {
			    	int pixel = 0;
			    	
			    	pixel |= 128 << 16;
	                pixel |= 128 << 8;    
	                pixel |= 128;	
			    	image.setRGB(i, j, pixel);
			    }
			}
               
			JFrame frame = new JFrame("Simple Decoder");
		    WindowAdapter window_handler = new WindowAdapter()
			{
			    public void windowClosing(WindowEvent event)
			    {
			        System.exit(0);
			    }
			};
			frame.addWindowListener(window_handler);
			    
			image_canvas = new ImageCanvas();
			image_canvas.setSize(xdim, ydim);
			frame.getContentPane().add(image_canvas, BorderLayout.CENTER);
			
				
			frame.pack();
			frame.setLocation(400, 200);
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
            g.drawImage(image, 0, 0, this);
        }
    }
}