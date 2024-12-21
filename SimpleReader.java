import java.awt.*;
import java.awt.image.*;
import java.io.*;
import java.awt.event.*;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.zip.*;
import javax.imageio.*;
import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

public class SimpleReader
{
	BufferedImage image;
	ImageCanvas   image_canvas;
	
	int xdim        = 0;
	int ydim        = 0;
	int _xdim       = 0;
	int _ydim       = 0;
	int pixel_shift = 0;
	int pixel_quant = 0;
	int set_id      = 0;
	byte delta_type = 0;
	
	ArrayList string_list = new ArrayList();
	ArrayList table_list  = new ArrayList();
	ArrayList map_list    = new ArrayList();
	
	int [][] channel_array = new int[3][0];
	
	int  min[]        = new int[3];
    int  init[]       = new int[3];
    int  delta_min[]  = new int[3];
    byte type[]       = new byte[3];
    byte compressed[] = new byte[3];
    int  length[]     = new int[3];
    int  compressed_length[] = new int[3];
    byte channel_iterations[] = new byte[3];
    
	public static void main(String[] args)
	{
		if (args.length != 1)
		{
			System.out.println("Usage: java SimpleReader <filename>");
			System.exit(0);
		}
	
		SimpleReader reader = new SimpleReader(args[0]);
	}
	
	public SimpleReader(String filename)
	{
		try
		{
			long start = System.nanoTime();
			File file          = new File(filename);
			DataInputStream in = new DataInputStream(new FileInputStream(file));
			xdim               = in.readShort();
			ydim               = in.readShort();
	        pixel_shift        = in.readByte();
		    pixel_quant        = in.readByte();
		    set_id             = in.readByte();
		    delta_type         = in.readByte();
		    int [] channel_id  = DeltaMapper.getChannels(set_id);
		    
		    if(delta_type != 2)
		    {
		    	System.out.println("Delta type not supported.");
		    	System.exit(0);
		    }
		    
		    for(int i = 0; i < 3; i++)
		    {
		    	int j = channel_id[i];
		    	min[i] = in.readInt();
		    	init[i] = in.readInt();
		    	
	    	    delta_min[i]          = in.readInt();
	    	    length[i]             = in.readInt();
	    	    compressed_length[i]  = in.readInt();
	    	    channel_iterations[i] = in.readByte();
	    	    
			    int table_length = in.readShort();
			    int [] table = new int[table_length];
			    int max_byte_value  = Byte.MAX_VALUE * 2 + 1;
			    if(table.length <= max_byte_value)
			    {
			        for(int k = 0; k < table_length; k++)
			        {
			    	    table[k] = in.readByte();
			    	    if(table[k] < 0)
            			    table[k] = max_byte_value + 1 + table[k];
			        }
			    }
			    else
			    {
				    for(int k = 0; k < table_length; k++)
			    	    table[k] = in.readShort();
			    }
			    table_list.add(table);
			    
				int number_of_segments = in.readInt();
				//System.out.println("Number of segments is " + number_of_segments);
				if(number_of_segments == 1)
				{
					int     string_length = in.readInt();
					byte [] string        = new byte[string_length];
			        int     zipped_length = in.readInt();
			        
			        
			        if(zipped_length == 0)
			        	in.read(string, 0, string_length);   
			        else  
			        {
			        	byte [] zipped_string    = new byte[zipped_length];
			            in.read(zipped_string, 0, zipped_length);
			            Inflater inflater = new Inflater();
			            inflater.setInput(zipped_string, 0, zipped_length);
			            int unzipped_length = inflater.inflate(string);
			            if(unzipped_length != string_length)
			        	    System.out.println("Unzipped string not expected length.");    	
			        }
			        
			        string_list.add(string);
			        //System.out.println("Added unsegmented string to string list for channel " + i);
				}
				else
				{
					ArrayList compressed_string_list = new ArrayList();
					int max_bytelength               = in.readInt();
					
				    for(int k = 0; k < number_of_segments; k++)	
				    {
				    	int string_length = 0;
				    	if(max_bytelength <= Byte.MAX_VALUE * 2 + 1)
				    	{
				    	    string_length = in.readByte();
				    	    if(string_length < 0)
				    	    	string_length += Byte.MAX_VALUE * 2 + 2;
				    	}
				    	else if(max_bytelength <= Short.MAX_VALUE * 2 + 1)
				    	{
				    		string_length = in.readShort();
				    		if(string_length < 0)
				    	    	string_length += Short.MAX_VALUE * 2 + 2;
				    	}
				    	else
				    		string_length = in.readInt();
				    	byte [] current_string = new byte[string_length];
				    	in.read(current_string, 0, string_length);
				        compressed_string_list.add(current_string);    	
				    }
				    
				    ArrayList decompressed_string_list = new ArrayList();
				    for(int k = 0; k < compressed_string_list.size(); k++)					    
				    {
				    	byte [] current_string = (byte[])compressed_string_list.get(k);
				    	int     string_type    = StringMapper.getType(current_string);
				    	int     iterations     = StringMapper.getIterations(current_string);
				    	
				    	if(iterations == 0 || iterations == 16)
				    		decompressed_string_list.add(current_string);	
				    	else
				    	{
				    		if(string_type == 0)
					    	{
					    	    byte [] decompressed_string = StringMapper.decompressZeroStrings(current_string);
					    	    decompressed_string_list.add(decompressed_string);
					    	}
					    	else
					    	{
					    	    byte [] decompressed_string = StringMapper.decompressOneStrings(current_string);
					    	    decompressed_string_list.add(decompressed_string);
					    	}	
				    	}
				    }
				    
				    // Create buffer to put concatenated strings.
				    int string_length = 0;
				    for(int k = 0; k < decompressed_string_list.size(); k++)
				    {
				        byte [] current_string = (byte [])decompressed_string_list.get(k);
				        string_length += current_string.length - 1;
				    }
				    // Add byte for odd bits.
				    string_length++; 
				    byte [] string = new byte[string_length];
				    
				    // Concatenate strings less trailing byte with individual string information.
				    
				    int offset = 0;
				    int total_bitlength = 0;
				    for(int k = 0; k < decompressed_string_list.size(); k++)
					{
						byte [] segment = (byte [])decompressed_string_list.get(k);
						for(int m = 0; m < segment.length - 1; m++)
							string[offset + m] = segment[m];	
						offset += segment.length - 1;
						int segment_bitlength = StringMapper.getBitlength(segment);
						total_bitlength += segment_bitlength;
					}
					
					if(total_bitlength % 8 != 0)
					{
						int modulus = total_bitlength % 8;
						byte extra_bits = (byte)(8 - modulus);
						extra_bits <<= 5;
						string[string.length - 1] = extra_bits;
					}
					string[string.length - 1] |= channel_iterations[i];
				    string_list.add(string);
				}
		    }
		    
		    long stop = System.nanoTime();
		    long time = stop - start;
		    System.out.println("It took " + (time / 1000000) + " ms to read file.");
		    
		    int cores = Runtime.getRuntime().availableProcessors();
		    System.out.println("There are " + cores + " processors available.");
		    start = System.nanoTime();
		    
		    Thread [] decompression_thread = new Thread[3];
		    for(int i = 0; i < 3; i++)
		    {
		    	decompression_thread[i] = new Thread(new Decompressor(i));
		    	decompression_thread[i].start();
		    }
		    for(int i = 0; i < 3; i++)
		    	decompression_thread[i].join();
		    stop = System.nanoTime();
		    time = stop - start;
		    
		    System.out.println("It took " + (time / 1000000) + " ms to process data.");
		    System.out.println("Set id is " + set_id);
		    
		    start = System.nanoTime();
		    
		   
		    int [] blue  = new int[xdim * ydim];
		    int [] green = new int[xdim * ydim];
		    int [] red   = new int[xdim * ydim];
		    
		    if(set_id == 0)
			{
		    	blue  = channel_array[0];
		    	green = channel_array[1];
		    	red   = channel_array[2];
		    }
			else if(set_id == 1)
			{ 
				blue  = channel_array[0];
				red   = channel_array[1];
				green = DeltaMapper.getDifference(red, channel_array[2]);
		    }
			else if(set_id == 2)
			{ 
				blue  = channel_array[0];
				red   = channel_array[1];
				green = DeltaMapper.getDifference(blue, channel_array[2]);
			}
			else if(set_id == 3)
			{ 
				blue  = channel_array[0];
				green = DeltaMapper.getDifference(blue, channel_array[1]);
				red   = DeltaMapper.getSum(channel_array[2], green);
			}
			else if(set_id == 4)
			{ 
				blue  = channel_array[0];
				green = DeltaMapper.getDifference(blue, channel_array[1]);
				red   = DeltaMapper.getSum(blue, channel_array[2]);
			}
		    else if(set_id == 5)
			{
		    	green = channel_array[0];
		    	red   = channel_array[1];
		    	blue  = DeltaMapper.getSum(channel_array[2], green);
			}
			else if(set_id == 6)
			{
				red   = channel_array[0];
				for(int i = 0; i < channel_array[2].length; i++)
					channel_array[2][i] = -channel_array[2][i];
				green = DeltaMapper.getSum(channel_array[2], red);
				blue  = DeltaMapper.getSum(channel_array[1], green);
			}
			else if(set_id == 7)
			{
				green = channel_array[0];
				blue  = DeltaMapper.getSum(green, channel_array[1]);
				red   = DeltaMapper.getSum(green, channel_array[2]);
			}
			else if(set_id == 8)
			{
				green = channel_array[0];
				red   = DeltaMapper.getSum(green, channel_array[1]);
				blue  = DeltaMapper.getDifference(red, channel_array[2]);
		    }
			else if(set_id == 9)
			{
				red   = channel_array[0];
				green = DeltaMapper.getDifference(red, channel_array[1]); 
				blue  = DeltaMapper.getDifference(red, channel_array[2]);
			}
			
			stop = System.nanoTime();
			time = stop - start;
			System.out.println("It took " + (time / 1000000) + " ms to assemble files.");
			
			start = System.nanoTime();
			image = new BufferedImage(xdim, ydim, BufferedImage.TYPE_INT_RGB);
			int blue_shift  = 16 + pixel_shift;
			int green_shift = 8 + pixel_shift;
			int red_shift   = pixel_shift;
			
			for(int i = 0; i < ydim; i++)
			{
				for(int j = 0; j < xdim; j++)
				{
					int k     = i * xdim + j;
					int pixel = (blue[k] << blue_shift) + (green[k] << green_shift) + (red[k] << red_shift);
				    image.setRGB(j, i, pixel);
				}
			}
			stop = System.nanoTime();
			time = stop - start;
			System.out.println("It took " + (time / 1000000) + " ms to load image.");
			
			JFrame frame = new JFrame("Delta Reader");
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
		catch(Exception e)
		{
			System.out.println(e.toString());
		}
	}
	    
	class ImageCanvas extends Canvas
    {
        public synchronized void paint(Graphics g)
        {
            g.drawImage(image, 0, 0, this);
        }
    } 
	
	// This doesn't seem to work.
	/*
	class Adder implements Runnable
	{
		int [] pixel1;
		int [] pixel2;
		int [] pixel3;
		
		public Adder(int[] pixel1, int [] pixel2, int [] pixel3)
		{
			this.pixel1 = pixel1;
			this.pixel2 = pixel2;
			this.pixel2 = pixel3;
		}
		
		public void run()
		{
			pixel3 = DeltaMapper.getSum(pixel1, pixel2);
		}
	}
	*/
	
	
	class Shifter implements Runnable
	{
		int pixel_shift;
		int [] pixel;
		
		public Shifter(int[] pixel, int pixel_shift)
		{
			this.pixel = pixel;
			this.pixel_shift = pixel_shift;
		}
		
		public void run()
		{
		    for(int i = 0; i < pixel.length; i++)
		        pixel[i] <<= pixel_shift;
		}
	}
	
	class Shifter2 implements Runnable
	{
		int pixel_shift;
		int place_shift;
		
		int [] pixel;
		
		public Shifter2(int[] pixel, int pixel_shift, int place_shift)
		{
			this.pixel = pixel;
			this.pixel_shift = pixel_shift;
			this.place_shift = place_shift;
		}
		
		public void run()
		{
			int shift = pixel_shift + place_shift;
		    for(int i = 0; i < pixel.length; i++)
		    {
		        pixel[i] <<= shift;
		    }
		}
	}
	
	class Decompressor implements Runnable 
	{ 
		int i;
		public Decompressor(int i)
		{
		    this.i = i;	
		}
		
		public void run()
		{
			int [] channel_id = DeltaMapper.getChannels(set_id);
			byte [] string    = (byte [])string_list.get(i);
		    int [] table      = (int [])table_list.get(i);
		    int iterations    = StringMapper.getIterations(string);
		    if(iterations < 16 && iterations != 0)
		    	string = StringMapper.decompressZeroStrings(string);
		    else if(iterations > 16)
		    	string = StringMapper.decompressOneStrings(string);
		
    	    if(pixel_quant == 0)
    	    {
    		    int [] delta = new int[xdim * ydim];
    		    int number_unpacked = StringMapper.unpackStrings2(string, table, delta);
    		
		        for(int j = 1; j < delta.length; j++)
		           delta[j] += delta_min[i];
		        int[] current_channel = DeltaMapper.getValuesFromAverageDeltas(delta, xdim , ydim, init[i]);
		        if(channel_id[i] > 2)
		            for(int j = 0; j < current_channel.length; j++)
					    current_channel[j] += min[i];
		        channel_array[i] = current_channel;
    	    }
    	    else
    	    {
    		    double factor = pixel_quant;
	            factor       /= 10;
	            int intermediate_xdim = xdim - (int)(factor * (xdim / 2 - 2));
	            int intermediate_ydim = ydim - (int)(factor * (ydim / 2 - 2));
	            int [] delta = new int[intermediate_xdim * intermediate_ydim];
	            int number_unpacked = StringMapper.unpackStrings2(string, table, delta);
		        for(int j = 1; j < delta.length; j++)
		           delta[j] += delta_min[i];
		        int[] current_channel = DeltaMapper.getValuesFromAverageDeltas(delta, intermediate_xdim , intermediate_ydim, init[i]);
		        if(channel_id[i] > 2)
		            for(int j = 0; j < current_channel.length; j++)
					    current_channel[j] += min[i];
		        int [] resized_channel = ResizeMapper.resize(current_channel, intermediate_xdim, xdim, ydim);
		        channel_array[i]       = resized_channel;
    	    }
		}
	} 
}