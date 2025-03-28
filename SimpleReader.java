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
		    	int j   = channel_id[i];
		    	min[i]  = in.readInt();
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
					//System.out.println("Number of segments is " + number_of_segments);
					
					if(max_bytelength <= Byte.MAX_VALUE * 2 + 1)
			    	{
						byte [] segment_length = new byte[number_of_segments];
						byte [] segment_info   = new byte[number_of_segments];
						in.read(segment_length, 0, number_of_segments);
						in.read(segment_info, 0, number_of_segments);
						for(int k = 0; k < number_of_segments; k++)
						{
							int current_length = segment_length[k] - 1;
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
					
					
				    
				    /*
				    int number_of_processors = Runtime.getRuntime().availableProcessors();
				    //System.out.println("There are " + number_of_processors + " processors available.");
				    
				    
				    int segment_length = number_of_segments / number_of_processors;
				    
				    int last_segment_length = segment_length + number_of_segments % number_of_processors;
				    
				    Thread [] segment_thread = new Thread[number_of_processors];
				    
				    ArrayList [] compressed_segments = new ArrayList[number_of_processors];
				    ArrayList [] decompressed_segments = new ArrayList[number_of_processors];
				    
				    int n = 0;
				    for(int k = 0; k < number_of_processors - 1; k++)
				    {
				    	compressed_segments[k] = new ArrayList();
				    	decompressed_segments[k] = new ArrayList();
				        for(int m = 0; m < segment_length; m++)
				        {
				        	byte [] current_segment = (byte [])compressed_string_list.get(n++);
				        	compressed_segments[k].add(current_segment);
				        }
				        segment_thread[k] = new Thread(new SegmentDecompressor(compressed_segments[k], decompressed_segments[k]));
				        segment_thread[k].start();
				    }
				    int m = number_of_processors - 1;
				    compressed_segments[m] = new ArrayList();
			    	decompressed_segments[m] = new ArrayList();
				    for(int k = 0; k < last_segment_length; k++)
				    {
				    	byte [] current_segment = (byte [])compressed_string_list.get(n++);
			        	compressed_segments[m].add(current_segment); 
				    }
				    segment_thread[m] = new Thread(new SegmentDecompressor(compressed_segments[m], decompressed_segments[m]));
			        segment_thread[m].start();
				    
				    for(int k = 0; k < number_of_processors; k++)
				    	segment_thread[k].join();
				    
				    
				    ArrayList decompressed_string_list = new ArrayList();
				    
				    
				    for(int k = 0; k < number_of_processors; k++)
				    {
				    	int size = decompressed_segments[k].size();
				    	for(m = 0; m < size; m++)
				    	{
				    		byte [] current_segment = (byte[])decompressed_segments[k].get(m);
				    		decompressed_string_list.add(current_segment);
				    	}
				    }
				    
				    int size = decompressed_string_list.size();
				    */
				    
				    ArrayList decompressed_string_list = new ArrayList();
				    int size                           = compressed_string_list.size();
				    for(int k = 0; k < size; k++)					    
				    {
				    	byte [] current_string = (byte[])compressed_string_list.get(k);
				    	int     iterations     = StringMapper.getIterations(current_string);
				    	
				    	if(iterations == 0 || iterations == 16)
				    		decompressed_string_list.add(current_string);	
				    	else
				    	{
				    		if(iterations < 16)
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
				    for(int k = 0; k < size; k++)
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
				    for(int k = 0; k < size; k++)
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

				/*
				green = channel_array[0];
				Thread [] sum_thread = new Thread[2];
				sum_thread[0] = new Thread(new Adder(green, channel_array[1], blue));
				sum_thread[1] = new Thread(new Adder(green, channel_array[2], red));
				sum_thread[0].start();
				sum_thread[1].start();
				sum_thread[0].join();
				sum_thread[1].join();
				*/
			  
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
			
			BufferedImage image = new BufferedImage(xdim, ydim, BufferedImage.TYPE_INT_RGB);
			int blue_shift  = pixel_shift + 16;
			int green_shift = pixel_shift + 8;
			int red_shift   = pixel_shift;
			
			int k = 0;
			for(int i = 0; i < ydim; i++)
			{
				for(int j = 0; j < xdim; j++)
				{
				    image.setRGB(j, i, (blue[k] << blue_shift) + (green[k] << green_shift) + (red[k] << red_shift));
				    k++;
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
				    
			Canvas image_canvas = new Canvas()
			{
				public synchronized void paint(Graphics g)
		        {
		            g.drawImage(image, 0, 0, this);
		        }	
			};
			
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
	
	class Adder implements Runnable
	{
		int [] pixel1, pixel2, pixel3;
		
		public Adder(int[] pixel1, int [] pixel2, int [] pixel3)
		{
			this.pixel1 = pixel1;
			this.pixel2 = pixel2;
			this.pixel3 = pixel3;
		}
		
		public void run()
		{
			int length = pixel1.length;
		    for(int i = 0; i < length; i++)
		        pixel3[i] = pixel1[i] + pixel2[i];
		}
	}
	
	class SegmentDecompressor implements Runnable
	{
		ArrayList compressed_segments, decompressed_segments;
		
		public SegmentDecompressor(ArrayList compressed_segments, ArrayList decompressed_segments)
		{
			this.compressed_segments   = compressed_segments;
			this.decompressed_segments = decompressed_segments;
		}
		
		public void run()
		{
		    int size = compressed_segments.size();
		    for(int i = 0; i < size; i++)
		    {
		    	byte [] current_segment = (byte [])compressed_segments.get(i);
		    	int iterations = StringMapper.getIterations(current_segment);
		    	if(iterations == 0 || iterations == 16)
		    	    decompressed_segments.add(current_segment);
		    	else
		    	{
		    		if(iterations < 16)
			    	{
			    	    byte [] decompressed_segment = StringMapper.decompressZeroStrings(current_segment);
			    	    decompressed_segments.add(decompressed_segment);
			    	}
			    	else
			    	{
			    	    byte [] decompressed_segment = StringMapper.decompressOneStrings(current_segment);
			    	    decompressed_segments.add(decompressed_segment);
			    	}		
		    	}
		    }
		}
	}
	
	// This is the only threaded method that improves the speed of the computation.
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