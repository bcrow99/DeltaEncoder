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
			    
			    int table_length = in.readByte();
			    System.out.println("String table length is " +  table_length);
			    
			    byte [] table = new byte[table_length];
			    in.read(table, 0, table_length);
			    
			    int [] string_table = new int[table.length];
			    for(int i = 0; i < table.length; i++)
			    	string_table[i] = table[i];
			    System.out.println("Read table.");
			    
			    int data_length = in.readInt();
			    System.out.println("Data length is " +  data_length);
			    
			    byte [] data = new byte[data_length];
			    in.read(data, 0, data_length);
			    
			    System.out.println("Read data.");
			    in.close();
			    
			    if(compression == 2)
			    {
			    	System.out.println("Decompressing zipped packed strings.");
			    	Inflater inflater = new Inflater();
				    inflater.setInput(data, 0, data_length);
				    byte [] strings   = new byte[data_length * 5];
				    
				    try
				    {
				        int string_length = inflater.inflate(strings);
				        System.out.println("String length of unzipped strings is " + string_length);
				    }
				    catch(Exception e)
				    {
				    	System.out.println(e.toString());
				    }
				    
				    int [] delta = new int[xdim * ydim];
					int number_of_ints = DeltaMapper.unpackStrings2(strings, string_table, delta);
					for(int i = 1; i < delta.length; i++)
					     delta[i] += delta_min;
					
					int [] dst = DeltaMapper.getValuesFromDeltas(delta, xdim , ydim, init_value);
					
					image = new BufferedImage(xdim, ydim, BufferedImage.TYPE_INT_RGB);
					
					System.out.println("Buffer size is " + dst.length);
					System.out.println("Dimension product is " + (xdim * ydim));
					   
					for(int i = 0; i < ydim; i++)
					{
					    for(int j = 0; j < xdim; j++)
					    {
					    	int value = dst[i * xdim + j]; 
					    	value <<= pixel_shift;
					    	
					    	
					    	int pixel = 0;
					    	
					    	pixel |= value << 16;
			                pixel |= value << 8;    
			                pixel |= value;	
			                
					    	image.setRGB(j, i, pixel);
					    }
					}
			    }
			    else if(compression == 4)
			    {
			    	System.out.println("Decompressing zipped compressed strings.");
			    	Inflater inflater = new Inflater();
				    inflater.setInput(data, 0, data_length);
				    byte [] strings            = new byte[data_length * 5];
				    byte [] compressed_strings = new byte[data_length * 5];
				    
				    try
				    {
				        int byte_length = inflater.inflate(compressed_strings);
				        System.out.println("Byte length of unzipped strings is " + byte_length);
				        
				    }
				    catch(Exception e)
				    {
				    	System.out.println(e.toString());
				    }	
			    }
			    else
			    {
			    	image = new BufferedImage(xdim, ydim, BufferedImage.TYPE_INT_RGB);
					   
					for(int i = 0; i < xdim; i++)
					{
					    for(int j = 0; j < ydim; j++)
					    {
					    	int pixel = 0;
					    	
					    	pixel |= 128 << 16;
			                pixel |= 128 << 8;    
			                pixel |= 128;	
					    	image.setRGB(j, i, pixel);
					    }
					}
			    }
			    
			    
			}
			catch(Exception e)
			{
				System.out.println(e.toString());
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