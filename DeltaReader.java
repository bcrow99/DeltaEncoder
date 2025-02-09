import java.awt.*;
import java.awt.image.*;
import java.io.*;
import java.awt.event.*;
import java.awt.geom.AffineTransform;
import java.util.*;
import java.util.zip.*;
import javax.swing.*;

public class DeltaReader
{
	int xdim        = 0;
	int ydim        = 0;
	int pixel_shift = 0;
	int pixel_quant = 0;
	int set_id      = 0;
	byte delta_type = 0;
	int intermediate_xdim = 0;
	int intermediate_ydim = 0;
	
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
    int  compressed_length[]  = new int[3];
    byte channel_iterations[] = new byte[3];
    
    BufferedImage display_image;
    
	public static void main(String[] args)
	{
		if (args.length != 1)
		{
			System.out.println("Usage: java DeltaReader <filename>");
			System.exit(0);
		}
	
		DeltaReader reader = new DeltaReader(args[0]);
	}
	
	public DeltaReader(String filename)
	{
		try
		{
			File file          = new File(filename);
			DataInputStream in = new DataInputStream(new FileInputStream(file));
			xdim               = in.readShort();
			ydim               = in.readShort();
	        pixel_shift        = in.readByte();
		    pixel_quant        = in.readByte();
		    set_id             = in.readByte();
		    delta_type         = in.readByte();
		    int [] channel_id  = DeltaMapper.getChannels(set_id);
		    
		    if(delta_type > 7)
		    {
		    	System.out.println("Delta type not supported.");
		    	System.exit(0);
		    }
		    System.out.println("Set id is " + set_id);
		    
		    
		    long start = System.nanoTime();
		    for(int i = 0; i < 3; i++)
		    {
		    	int j                 = channel_id[i];
		    	min[i]                = in.readInt();
		    	init[i]               = in.readInt();
	    	    delta_min[i]          = in.readInt();
	    	    length[i]             = in.readInt();
	    	    compressed_length[i]  = in.readInt();
	    	    channel_iterations[i] = in.readByte();
	    	    
			    int    table_length = in.readShort();
			    int [] table        = new int[table_length];
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
			    
			    if(delta_type == 5 || delta_type == 6 || delta_type == 7)
				{
					short  map_table_length = in.readShort();
					int [] map_table        = new int[map_table_length];	
					for(int k = 0; k < map_table_length; k++)
						map_table[k] = in.readShort();
					int byte_length = in.readInt();
					
					byte [] map_string    = new byte[byte_length];
				    in.read(map_string, 0, byte_length); 
				    
				    byte increment  = in.readByte();
				    int dimension = in.readInt();
				    byte [] map = new byte[dimension];
				    byte iterations = StringMapper.getIterations(map_string);

				    int size = 0;
				    if(iterations == 0 || iterations == 16)
				        size = StringMapper.unpackStrings2(map_string, map_table, map);
				    else
				    {
				    	byte [] decompressed_string = StringMapper.decompressStrings(map_string);
				    	size = StringMapper.unpackStrings2(decompressed_string, map_table, map);
				    }
				    if(increment != 0)
				        for(int k = 0; k < map.length; k++)
				    	    map[k] += increment;
				    map_list.add(map);
				}
				int number_of_segments = in.readInt();
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
				}
				else
				{
					ArrayList compressed_string_list = new ArrayList();
					int max_bytelength               = in.readInt();
					
					if(max_bytelength <= Byte.MAX_VALUE * 2 + 1)
			    	{
						byte [] segment_length = new byte[number_of_segments];
						byte [] segment_info   = new byte[number_of_segments];
						in.read(segment_length, 0, number_of_segments);
						in.read(segment_info, 0, number_of_segments);
						for(int k = 0; k < number_of_segments; k++)
						{
							int current_length = segment_length[k];
							if(current_length < 0)
								current_length += Byte.MAX_VALUE * 2 + 2;
							byte [] current_segment = new byte[current_length + 1];
							in.read(current_segment, 0, current_length);
							current_segment[current_length] = segment_info[k];
							compressed_string_list.add(current_segment);
						}
			    	}
					else if(max_bytelength <= Short.MAX_VALUE * 2 + 1)
					{
						for(int k = 0; k < number_of_segments; k++)
						{
							int current_length = in.readShort();
				    		if(current_length < 0)
				    	    	current_length += Short.MAX_VALUE * 2 + 2;	
				    		byte [] current_segment = new byte[current_length];
					    	in.read(current_segment, 0, current_length);
					        compressed_string_list.add(current_segment);
						}
					}
					else
					{
						int current_length = in.readInt();	
						byte [] current_segment = new byte[current_length];
				    	in.read(current_segment, 0, current_length);
				        compressed_string_list.add(current_segment);
					}
				    
				    ArrayList decompressed_string_list = new ArrayList();
				    for(int k = 0; k < compressed_string_list.size(); k++)					    
				    {
				    	byte [] current_string = (byte[])compressed_string_list.get(k);
				    	int     iterations     = StringMapper.getIterations(current_string);
				    	if(iterations == 0 || iterations == 16)
				    		decompressed_string_list.add(current_string);	
				    	else
				    	{
				    		byte [] decompressed_string = StringMapper.decompressStrings(current_string);
				    	    decompressed_string_list.add(decompressed_string);
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
		    
		    BufferedImage image = new BufferedImage(xdim, ydim, BufferedImage.TYPE_INT_RGB);
		    
		    start = System.nanoTime();
		    
		    int [] blue, green, red;
		    
		    if(set_id == 0)
			{
		    	blue  = channel_array[0];
		    	green = channel_array[1];
		    	red   = channel_array[2];
		    	if(pixel_quant == 0)
		    	{
		    	    int [] pixel = DeltaMapper.getPixel(blue, green, red, xdim, pixel_shift);
				    image.setRGB(0, 0, xdim, ydim, pixel, 0, xdim);
		    	}
		    }
			else if(set_id == 1)
			{ 
				blue  = channel_array[0];
		    	green = DeltaMapper.getDifference(channel_array[1], channel_array[2]);;
		    	red   = channel_array[1];
				if(pixel_quant == 0)
				{
				    int [] pixel = DeltaMapper.getPixel(blue, green, red, xdim, pixel_shift);
				    image.setRGB(0, 0, xdim, ydim, pixel, 0, xdim);
				}
				else
				{
					channel_array[0] = blue;
					channel_array[1] = green;
					channel_array[2] = red;
				}
		    }
			else if(set_id == 2)
			{ 
				blue  = channel_array[0];
				green = DeltaMapper.getDifference(channel_array[0], channel_array[2]);
				red   = channel_array[1];
				if(pixel_quant == 0)
				{
				    int [] pixel = DeltaMapper.getPixel(blue, green, red, xdim, pixel_shift);
				    image.setRGB(0, 0, xdim, ydim, pixel, 0, xdim);
				}
				else
				{
					channel_array[0] = blue;
					channel_array[1] = green;
					channel_array[2] = red;
				}
			}
			else if(set_id == 3)
			{ 
				blue  = channel_array[0];
				green = DeltaMapper.getDifference(channel_array[0], channel_array[1]);
			    red   = DeltaMapper.getSum(channel_array[2], green);
				if(pixel_quant == 0)
				{
				    int [] pixel = DeltaMapper.getPixel(blue, green, red, xdim, pixel_shift);
				    image.setRGB(0, 0, xdim, ydim, pixel, 0, xdim);
				}
				else
				{
					channel_array[0] = blue;
					channel_array[1] = green;
					channel_array[2] = red;
				}
			}
			else if(set_id == 4)
			{ 
				blue  = channel_array[0];
				green = DeltaMapper.getDifference(channel_array[0], channel_array[1]);
			    red   = DeltaMapper.getSum(channel_array[0], channel_array[2]);
				if(pixel_quant == 0)
				{
				    int [] pixel = DeltaMapper.getPixel(blue, green, red, xdim, pixel_shift);
				    image.setRGB(0, 0, xdim, ydim, pixel, 0, xdim);
				}
				else
				{
					channel_array[0] = blue;
					channel_array[1] = green;
					channel_array[2] = red;
				}
			}
		    else if(set_id == 5)
			{
		    	blue  = DeltaMapper.getSum(channel_array[2], channel_array[0]);
		    	green = channel_array[0];
		    	red   = channel_array[1];
		    	if(pixel_quant == 0)
		    	{
		    	    int [] pixel = DeltaMapper.getPixel(blue, green, red, xdim, pixel_shift);
				    image.setRGB(0, 0, xdim, ydim, pixel, 0, xdim);
		    	}
		    	else
				{
					channel_array[0] = blue;
					channel_array[1] = green;
					channel_array[2] = red;
				}
			}
			else if(set_id == 6)
			{
				for(int i = 0; i < channel_array[2].length; i++)
				    channel_array[2][i] = -channel_array[2][i];
				green = DeltaMapper.getSum(channel_array[2], channel_array[0]);
			    blue  = DeltaMapper.getSum(channel_array[1], green);
			    red   = channel_array[0];
				if(pixel_quant == 0)
				{
				    int [] pixel = DeltaMapper.getPixel(blue, green, red, xdim, pixel_shift);
				    image.setRGB(0, 0, xdim, ydim, pixel, 0, xdim);
				}
				else
				{
					channel_array[0] = blue;
					channel_array[1] = green;
					channel_array[2] = red;
				}
			}
			else if(set_id == 7)
			{
				blue  = DeltaMapper.getSum(channel_array[0], channel_array[1]);
				green = channel_array[0];
			    red   = DeltaMapper.getSum(channel_array[0], channel_array[2]);
				if(pixel_quant == 0)
				{
				    int [] pixel = DeltaMapper.getPixel(blue, green, red, xdim, pixel_shift);
				    image.setRGB(0, 0, xdim, ydim, pixel, 0, xdim);
				}
				else
				{
					channel_array[0] = blue;
					channel_array[1] = green;
					channel_array[2] = red;
				}
			}
			else if(set_id == 8)
			{
				red   = DeltaMapper.getSum(channel_array[0], channel_array[1]);
				blue  = DeltaMapper.getDifference(red, channel_array[2]);
				green = channel_array[0];
				if(pixel_quant == 0)
				{
				    int [] pixel = DeltaMapper.getPixel(blue, green, red, xdim, pixel_shift);
				    image.setRGB(0, 0, xdim, ydim, pixel, 0, xdim);
				}
				else
				{
					channel_array[0] = blue;
					channel_array[1] = green;
					channel_array[2] = red;
				}
		    }
			else if(set_id == 9)
			{
				blue  = DeltaMapper.getDifference(channel_array[0], channel_array[2]);
			    green = DeltaMapper.getDifference(channel_array[0], channel_array[1]); 
			    red   = channel_array[0];
				
				if(pixel_quant == 0)
				{	
				    int [] pixel = DeltaMapper.getPixel(blue, green, red, xdim, pixel_shift);
				    image.setRGB(0, 0, xdim, ydim, pixel, 0, xdim);
				}
				else
				{
					channel_array[0] = blue;
					channel_array[1] = green;
					channel_array[2] = red;
				}
			}
		    
		    if(pixel_quant != 0)
		    {
		        Thread [] resize_thread = new Thread[3];
		        for(int i = 0; i < 3; i++)
		        {
		    	    resize_thread[i] = new Thread(new Resizer(i));
		    	    resize_thread[i].start();
		        }
		        for(int i = 0; i < 3; i++)
		    	    resize_thread[i].join();
		        blue  = channel_array[0];
		        green = channel_array[1];
		        red   = channel_array[2];
		        int [] pixel = DeltaMapper.getPixel(blue, green, red, xdim, pixel_shift);
			    image.setRGB(0, 0, xdim, ydim, pixel, 0, xdim);
		    }
		    
		    stop = System.nanoTime();
			time = stop - start;
			System.out.println("It took " + (time / 1000000) + " ms to assemble and load rgb files.");
			
			
			Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
			int	screen_xdim      = (int)screenSize.getWidth();
			int	screen_ydim      = (int)screenSize.getHeight();
			int canvas_xdim, canvas_ydim;
			double scale = 1.;
			if(xdim <= (screen_xdim - 20) && ydim <= (screen_ydim - 200))
			{
				canvas_xdim = xdim;
				canvas_ydim = ydim;
				scale       = 1.;
			}
			else if(xdim <= (screen_xdim - 20))
			{
				canvas_ydim = screen_ydim - 200; 
				scale       = canvas_ydim;
				scale      /= ydim;
				canvas_xdim = (int)(scale * xdim);
			}
			else if(ydim <= (screen_ydim - 200))
	        {
				canvas_xdim = screen_xdim - 20; 
				scale       = canvas_xdim;
				scale      /= ydim;
				canvas_ydim = (int)(scale * ydim);
	        }
			else
			{
				double xscale = screen_xdim - 20;
				xscale       /= xdim;
				double yscale = screen_ydim - 200;
				yscale       /= ydim;
					    
				if(xscale <= yscale)
					scale = xscale;
				else
					scale = yscale;
					    
				canvas_xdim = (int)(scale * xdim);
				canvas_ydim = (int)(scale * ydim);
			}
				
			if(scale == 1.)
				display_image = image;
			else
			{
				AffineTransform scaling_transform = new AffineTransform();
				scaling_transform.scale(scale, scale);
				AffineTransformOp scale_op = new AffineTransformOp(scaling_transform, AffineTransformOp.TYPE_BILINEAR);
				display_image              = new BufferedImage(canvas_xdim, canvas_ydim, image.getType());
				display_image              = scale_op.filter(image, display_image);
			}
			
			Canvas image_canvas = new Canvas()
			{
				public synchronized void paint(Graphics g)
		        {
		            g.drawImage(display_image, 0, 0, this);
		        }	
			};
			
			image_canvas.setSize(canvas_xdim, canvas_ydim);
			
			JFrame frame = new JFrame("Delta Reader");
			WindowAdapter window_handler = new WindowAdapter()
			{
				public void windowClosing(WindowEvent event)
				{
				    System.exit(0);
				}
			};
			frame.addWindowListener(window_handler);
				    
			
			frame.getContentPane().add(image_canvas, BorderLayout.CENTER);		
			frame.pack();
			frame.setLocation(10, 10);
			frame.setVisible(true);
		}
		catch(Exception e)
		{
			System.out.println(e.toString());
		}
	}
	
	class Resizer implements Runnable 
	{ 
		int i;
		public Resizer(int i)
		{
		    this.i = i;	
		}
		
		public void run()
		{
			int [] channel         = channel_array[i];
			int [] resized_channel = ResizeMapper.resize(channel, intermediate_xdim, xdim, ydim);
			channel_array[i]       = resized_channel; 
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
			if(length[i] != compressed_length[i])
			    string = StringMapper.decompressStrings(string);
			//int iterations    = StringMapper.getIterations(string);
			//int bitlength     = StringMapper.getBitlength(string);
			//if(channel_iterations[i] != iterations)
			//    System.out.println("Iterations appended to string does not agree with channel " + i + " information.");
			//if(compressed_length[i] != bitlength)
			//	System.out.println("Bit length appended to string does not agree with channel " + i + " information.");
			   
			
			int [] delta;
    	    int    current_xdim;
    	    int    current_ydim;
    	    if(pixel_quant == 0)
    	    {
    		    delta = new int[xdim * ydim];
    		    int number_unpacked = StringMapper.unpackStrings2(string, table, delta);
    		
		        for(int j = 1; j < delta.length; j++)
		           delta[j] += delta_min[i];

		        current_xdim = xdim;
		        current_ydim = ydim;
    	    }
    	    else
    	    {
    		    double factor = pixel_quant;
	            factor       /= 10;
	            intermediate_xdim = xdim - (int)(factor * (xdim / 2 - 2));
	            intermediate_ydim = ydim - (int)(factor * (ydim / 2 - 2));
	            delta = new int[intermediate_xdim * intermediate_ydim];
	            int number_unpacked = StringMapper.unpackStrings2(string, table, delta);
		        for(int j = 1; j < delta.length; j++)
		           delta[j] += delta_min[i];	
		        
		        current_xdim = intermediate_xdim;
		        current_ydim = intermediate_ydim; 
    	    }
    	
		    if(delta_type == 0)
		    	channel_array[i] = DeltaMapper.getValuesFromHorizontalDeltas(delta, current_xdim , current_ydim, init[i]);
		    else if(delta_type == 1)
		    	channel_array[i] = DeltaMapper.getValuesFromVerticalDeltas(delta, current_xdim , current_ydim, init[i]);
		    else if(delta_type == 2)
		    	channel_array[i] = DeltaMapper.getValuesFromAverageDeltas(delta, current_xdim , current_ydim, init[i]);
		    else if(delta_type == 3)
		    	channel_array[i] = DeltaMapper.getValuesFromPaethDeltas(delta, current_xdim , current_ydim, init[i]);
		    else if(delta_type == 4)
		    	channel_array[i] = DeltaMapper.getValuesFromGradientDeltas(delta, current_xdim , current_ydim, init[i]);
		    else if(delta_type == 5)
		    {
			    byte [] map = (byte [])map_list.get(i);
			    channel_array[i] = DeltaMapper.getValuesFromMixedDeltas(delta, current_xdim , current_ydim, init[i], map);
		    }
		    else if(delta_type == 6)
		    {
			    byte [] map = (byte [])map_list.get(i);
			    channel_array[i] = DeltaMapper.getValuesFromMixedDeltas2(delta, current_xdim , current_ydim, init[i], map);
		    }
		    else if(delta_type == 7)
		    {
			    byte [] map = (byte [])map_list.get(i);
			    channel_array[i] = DeltaMapper.getValuesFromIdealDeltas2(delta, current_xdim , current_ydim, init[i], map);
		    }
		    
		    if(channel_id[i] > 2)
	            for(int j = 0; j < channel_array[i].length; j++)
	            	channel_array[i][j] += min[i];	    
		}
	} 
}