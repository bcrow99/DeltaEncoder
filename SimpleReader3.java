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

public class SimpleReader3
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
    int  seed_delta_min[] = new int[3];
    int  dilated_delta_min[] = new int[3];
    byte type[]       = new byte[3];
    byte compressed[] = new byte[3];
    int  length[]     = new int[3];
    int  compressed_length[] = new int[3];
    byte channel_iterations[] = new byte[3];
    
	public static void main(String[] args)
	{
		if (args.length != 1)
		{
			System.out.println("Usage: java SimpleReader3 <filename>");
			System.exit(0);
		}
	
		SimpleReader3 reader = new SimpleReader3(args[0]);
	}
	
	public SimpleReader3(String filename)
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
		    
		    if(delta_type > 7)
		    {
		    	System.out.println("Delta type not supported.");
		    	System.exit(0);
		    }
		    
		    for(int i = 0; i < 3; i++)
		    {
		    	int j = channel_id[i];
		    	min[i] = in.readInt();
		    	init[i] = in.readInt();
		    	
		    	if(delta_type < 7)
		    	{
		    	    delta_min[i] = in.readInt();
		    	    length[i] = in.readInt();
		    	    compressed_length[i] = in.readInt();
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
		    	}
		    	else
		    	{
		    		seed_delta_min[i] = in.readInt();
		    		dilated_delta_min[i] = in.readInt();
		    		
		    		ArrayList channel_table_list = new ArrayList();
		    		
		    		int seed_table_length = in.readShort();
				    int [] seed_table = new int[seed_table_length];
				    int max_byte_value  = Byte.MAX_VALUE * 2 + 1;
				    if(seed_table.length <= max_byte_value)
				    {
				        for(int k = 0; k < seed_table_length; k++)
				        {
				    	    seed_table[k] = in.readByte();
				    	    if(seed_table[k] < 0)
	            			    seed_table[k] = max_byte_value + 1 + seed_table[k];
				        }
				    }
				    else
				    {
					    for(int k = 0; k < seed_table_length; k++)
				    	    seed_table[k] = in.readShort();
				    }
				    
				    channel_table_list.add(seed_table);
				    
				    int dilated_table_length = in.readShort();
				    int [] dilated_table = new int[dilated_table_length];
				    
				    if(dilated_table.length <= max_byte_value)
				    {
				        for(int k = 0; k < dilated_table_length; k++)
				        {
				    	    dilated_table[k] = in.readByte();
				    	    if(dilated_table[k] < 0)
	            			    dilated_table[k] = max_byte_value + 1 + dilated_table[k];
				        }
				    }
				    else
				    {
					    for(int k = 0; k < dilated_table_length; k++)
				    	    dilated_table[k] = in.readShort();
				    }
		    		
                    channel_table_list.add(dilated_table);
                    table_list.add(channel_table_list);
		    	}
				
				if(delta_type == 5 || delta_type == 6)
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
				    int  size = 0;
				    
				    if(iterations == 0)
				        size = StringMapper.unpackStrings2(map_string, map_table, map);
				    else if(iterations < 16)
				    {
				    	byte [] decompressed_string = StringMapper.decompressZeroStrings(map_string);
				    	size = StringMapper.unpackStrings2(decompressed_string, map_table, map);		
				    }
				    else
				    {
				    	byte [] decompressed_string = StringMapper.decompressOneStrings(map_string);
				    	size = StringMapper.unpackStrings2(decompressed_string, map_table, map);
				    }
				    
				    if(increment != 0)
				        for(int k = 0; k < map.length; k++)
				    	    map[k] += increment;
				    map_list.add(map);
				}
				else if(delta_type == 7)
				{
					ArrayList channel_map_list = new ArrayList();
					
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
				
				    int  size = 0;
				    
				    if(iterations == 0 || iterations == 16)
				        size = StringMapper.unpackStrings2(map_string, map_table, map);
				    else if(iterations < 16)
				    {
				    	byte [] decompressed_string = StringMapper.decompressZeroStrings(map_string);
				    	size = StringMapper.unpackStrings2(decompressed_string, map_table, map);		
				    }
				    else
				    {
				    	byte [] decompressed_string = StringMapper.decompressOneStrings(map_string);
				    	size = StringMapper.unpackStrings2(decompressed_string, map_table, map);
				    }
				    
				    // Probably don't need to do this even if it's non-zero.
				    // Only an issue if it's negative.
				    if(increment != 0)
				        for(int k = 0; k < map.length; k++)
				    	    map[k] += increment;
				    
				    channel_map_list.add(map);
				    
				    
				    map_table_length = in.readShort();
					map_table        = new int[map_table_length];	
					for(int k = 0; k < map_table_length; k++)
						map_table[k] = in.readShort();
					
					
					byte_length = in.readInt();
					map_string    = new byte[byte_length];
				    in.read(map_string, 0, byte_length); 
				    increment  = in.readByte();
				    
				    dimension = in.readInt();
				    map = new byte[dimension];
				    
				    iterations = StringMapper.getIterations(map_string);
	
				    size = 0;
				    
				    if(iterations == 0 || iterations == 16)
				        size = StringMapper.unpackStrings2(map_string, map_table, map);
				    else if(iterations < 16)
				    {
				    	byte [] decompressed_string = StringMapper.decompressZeroStrings(map_string);
				    	size = StringMapper.unpackStrings2(decompressed_string, map_table, map);		
				    }
				    else
				    {
				    	byte [] decompressed_string = StringMapper.decompressOneStrings(map_string);
				    	size = StringMapper.unpackStrings2(decompressed_string, map_table, map);
				    }
				    
				   
				    if(increment != 0)
				        for(int k = 0; k < map.length; k++)
				    	    map[k] += increment;
				    channel_map_list.add(map);
				    
				    map_list.add(channel_map_list);
				}
				
				if(delta_type != 7)
				{
					/*
				    int string_length = in.readInt();
			        int zipped_length = in.readInt();
			        byte [] zipped_string    = new byte[zipped_length];
		            in.read(zipped_string, 0, zipped_length);
		            byte[] string     = new byte[string_length];
		            Inflater inflater = new Inflater();
		            inflater.setInput(zipped_string, 0, zipped_length);
		            int unzipped_length = inflater.inflate(string);
		            if(unzipped_length != string_length)
		        	    System.out.println("Unzipped string not expected length.");
		            string_list.add(string);
		            */
					int number_of_segments = in.readInt();
					System.out.println("Number of segments is " + number_of_segments);
					int string_length      = in.readInt();
					byte[] string          = new byte[string_length];
					in.read(string, 0, string_length);
					string_list.add(string);
				}
				else if(delta_type == 7)
				{
					ArrayList channel_string_list = new ArrayList();
					
					int string_length = in.readInt();
			        int zipped_length = in.readInt();
			        byte [] zipped_string    = new byte[zipped_length];
		            in.read(zipped_string, 0, zipped_length);
		            byte[] string     = new byte[string_length];
		            Inflater inflater = new Inflater();
		            inflater.setInput(zipped_string, 0, zipped_length);
		            int unzipped_length = inflater.inflate(string);
		            if(unzipped_length != string_length)
		        	    System.out.println("Unzipped string not expected length.");
		            channel_string_list.add(string);
		            
		            string_length = in.readInt();
			        zipped_length = in.readInt();
			        zipped_string    = new byte[zipped_length];
		            in.read(zipped_string, 0, zipped_length);
		            string     = new byte[string_length];
		            inflater = new Inflater();
		            inflater.setInput(zipped_string, 0, zipped_length);
		            unzipped_length = inflater.inflate(string);
		            if(unzipped_length != string_length)
		        	    System.out.println("Unzipped string not expected length.");
		            channel_string_list.add(string);
		            string_list.add(channel_string_list);
				}
		    }
		    long stop = System.nanoTime();
		    long time = stop - start;
		    System.out.println("It took " + (time / 1000000) + " ms to read file.");
		    
		   
		    //int cores = Runtime.getRuntime().availableProcessors();
		    //System.out.println("There are " + cores + " processors available.");
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
			
			image = new BufferedImage(xdim, ydim, BufferedImage.TYPE_INT_RGB);	
			
			if(pixel_shift > 0)
			{
				Thread [] shift_thread = new Thread[3];
				for(int i = 0; i < 3; i++)
				{
			        if(i == 0)
					{
				    	shift_thread[i] = new Thread(new Shifter(blue, pixel_shift));
				    	shift_thread[i].start();
				    }
					else if(i == 1)
					{
						shift_thread[i] = new Thread(new Shifter(green, pixel_shift));
					    shift_thread[i].start();	 
					}
					else
					{
						shift_thread[i] = new Thread(new Shifter(red, pixel_shift));
				    	shift_thread[i].start();	
					}
				}
				for(int i = 0; i < 3; i++)
				    	shift_thread[i].join();
			}
			
			for(int i = 0; i < ydim; i++)
			{
				for(int j = 0; j < xdim; j++)
				{
					int k     = i * xdim + j;
					int pixel = 0;
					
					pixel |= blue[k] << 16;
					pixel |= green[k] << 8;
					pixel |= red[k];
				    image.setRGB(j, i, pixel);
				}
			}
			
			stop = System.nanoTime();
			time = stop - start;
			System.out.println("It took " + (time / 1000000) + " ms to assemble and load rgb files.");
			
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
			if(delta_type != 7)
			{
			    byte [] string    = (byte [])string_list.get(i);
			    int [] table      = (int [])table_list.get(i);
		    	
			    int iterations    = StringMapper.getIterations(string);
			    if(iterations < 16 && iterations != 0)
			    	string = StringMapper.decompressZeroStrings(string);
			    else if(iterations > 16)
			    	string = StringMapper.decompressOneStrings(string);
			
	    	    int number_unpacked = 0;
	    	    int delta[] = new int[1];
	    	    if(pixel_quant == 0)
	    	    {
	    		    delta = new int[xdim * ydim];
	    		    number_unpacked = StringMapper.unpackStrings2(string, table, delta);
	    		
			        for(int j = 1; j < delta.length; j++)
			           delta[j] += delta_min[i];
	    	    }
	    	    else
	    	    {
	    		    double factor = pixel_quant;
		            factor       /= 10;
		            _xdim = xdim - (int)(factor * (xdim / 2 - 2));
		            _ydim = ydim - (int)(factor * (ydim / 2 - 2));
		            delta = new int[_xdim * _ydim];
		            number_unpacked = StringMapper.unpackStrings2(string, table, delta);
			        for(int j = 1; j < delta.length; j++)
			           delta[j] += delta_min[i];	
	    	    }
	    	
	    	    int current_xdim = 0;
    		    int current_ydim = 0;
    		
    		    if(pixel_quant == 0)
    		    {
    		        current_xdim = xdim;
    		        current_ydim = ydim;
    		    }
    		    else
    		    {
    		        current_xdim = _xdim;
    		        current_ydim = _ydim;
    		    }
	    	
    		    int[] current_channel = new int[1];
    		    if(delta_type == 0)
    			    current_channel = DeltaMapper.getValuesFromHorizontalDeltas(delta, current_xdim , current_ydim, init[i]);
    		    else if(delta_type == 1)
    			    current_channel = DeltaMapper.getValuesFromVerticalDeltas(delta, current_xdim , current_ydim, init[i]);
    		    else if(delta_type == 2)
    			    current_channel = DeltaMapper.getValuesFromAverageDeltas(delta, current_xdim , current_ydim, init[i]);
    		    else if(delta_type == 3)
    			    current_channel = DeltaMapper.getValuesFromPaethDeltas(delta, current_xdim , current_ydim, init[i]);
    		    else if(delta_type == 4)
    			    current_channel = DeltaMapper.getValuesFromGradientDeltas(delta, current_xdim , current_ydim, init[i]);
    		    else if(delta_type == 5)
    		    {
    			    byte [] map = (byte [])map_list.get(i);
			        current_channel = DeltaMapper.getValuesFromMixedDeltas(delta, current_xdim , current_ydim, init[i], map);
    		    }
    		    else if(delta_type == 6)
    		    {
    			    byte [] map = (byte [])map_list.get(i);
			        current_channel = DeltaMapper.getValuesFromIdealDeltas(delta, current_xdim , current_ydim, init[i], map);
    		    }
    		    
    		    if(channel_id[i] > 2)
    	            for(int j = 0; j < current_channel.length; j++)
    				    current_channel[j] += min[i];	
        		
        		if(pixel_quant == 0)
                	channel_array[i] = current_channel;
                else
                {
                	int [] resized_channel = ResizeMapper.resize(current_channel, _xdim, xdim, ydim);
    		        channel_array[i] = resized_channel;		
                }
			}
    		else if(delta_type == 7)
    		{
    			ArrayList channel_string_list = (ArrayList)string_list.get(i);
    			ArrayList channel_table_list  = (ArrayList)table_list.get(i);
    			ArrayList channel_map_list    = (ArrayList)map_list.get(i);
    			
    			byte [] seed_string = (byte [])channel_string_list.get(0);
    			int []  seed_table  = (int [])channel_table_list.get(0);
    			byte [] seed_map    = (byte [])channel_map_list.get(0);
    			
    			int iterations = StringMapper.getIterations(seed_string);
    			if(iterations < 16 && iterations != 0)
    				seed_string = StringMapper.decompressZeroStrings(seed_string);
    			else if(iterations > 16)
    				seed_string = StringMapper.decompressOneStrings(seed_string);
    			
    			int [] seed_delta = new int[seed_map.length]; 
    			int number_unpacked = StringMapper.unpackStrings2(seed_string, seed_table, seed_delta);
    			for(int j = 0; j < seed_delta.length; j++)
			           seed_delta[j] += seed_delta_min[i];
    			
    			// We probably want a function that takes parameters as arrays.
    			// Switching back forth with lists is time consuming and tedious.
    		    ArrayList seed_delta_list = new ArrayList();
    		    ArrayList seed_map_list   = new ArrayList();
    		    for(int j = 0; j < seed_delta.length; j++)
    		    {
    		    	seed_delta_list.add(seed_delta[j]);
    		    	seed_map_list.add(seed_map[j]);
    		    }
    			
    			byte [] dilated_string = (byte [])channel_string_list.get(1);
    			int []  dilated_table  = (int [])channel_table_list.get(1);
    			byte [] dilated_map    = (byte [])channel_map_list.get(1);
    			
    			iterations = StringMapper.getIterations(dilated_string);
    			if(iterations < 16 && iterations != 0)
    				dilated_string = StringMapper.decompressZeroStrings(dilated_string);
    			else if(iterations > 16)
    				dilated_string = StringMapper.decompressOneStrings(dilated_string);
    			
    			int [] dilated_delta = new int[dilated_map.length]; 
    			number_unpacked = StringMapper.unpackStrings2(dilated_string, dilated_table, dilated_delta);
    			for(int j = 0; j < dilated_delta.length; j++)
			           dilated_delta[j] += dilated_delta_min[i];
    			
    		    ArrayList dilated_delta_list = new ArrayList();
    		    ArrayList dilated_map_list = new ArrayList();
    		    for(int j = 0; j < dilated_delta.length; j++)
    		    {
    		    	dilated_delta_list.add(dilated_delta[j]);
    		    	dilated_map_list.add(dilated_map[j]);
    		    }
    			
    		    ArrayList parameter_list = new ArrayList();
    		    parameter_list.add(0);
    		    parameter_list.add(seed_delta_list);
    		    parameter_list.add(seed_map_list);
    		    parameter_list.add(dilated_delta_list);
    		    parameter_list.add(dilated_map_list);
    		    
	    	    int current_xdim = 0;
    		    int current_ydim = 0;
    		
    		    if(pixel_quant == 0)
    		    {
    		        current_xdim = xdim;
    		        current_ydim = ydim;
    		    }
    		    else
    		    {
    		    	double factor = pixel_quant;
		            factor       /= 10;
		            current_xdim  = xdim - (int)(factor * (xdim / 2 - 2));
		            current_ydim  = ydim - (int)(factor * (ydim / 2 - 2));
    		    }
    		    
    		    int [] channel = DeltaMapper.getValuesFromIdealDeltas2(parameter_list, current_xdim, current_ydim, init[i]);
		       
    		    if(channel_id[i] > 2)
    	            for(int j = 0; j < channel.length; j++)
    				    channel[j] += min[i];	
        		
        		if(pixel_quant == 0)
                	channel_array[i] = channel;
                else
                {
                	int [] resized_channel = ResizeMapper.resize(channel, current_xdim, xdim, ydim);
    		        channel_array[i] = resized_channel;	
                }
    		}
    		else
    		{
    			 System.out.println("Delta type " + delta_type + " not supported.");
    			 System.exit(0);
    		}	
		}
	} 
}