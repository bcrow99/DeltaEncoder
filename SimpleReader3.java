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

public class SimpleReader3
{
	BufferedImage image;
	ImageCanvas   image_canvas;
	
	int xdim        = 0;
	int ydim        = 0;
	int pixel_shift = 0;
	int pixel_quant = 0;
	int set_id      = 0;
	byte delta_type = 0;
	
	ArrayList string_list = new ArrayList();
    ArrayList table_list  = new ArrayList();
    ArrayList map_list    = new ArrayList();
	
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
			System.out.println("Usage: java SimpleReader3 <filename>");
			System.exit(0);
		}
		SimpleReader3 reader = new SimpleReader3(args[0]);
	}
	
	public SimpleReader3(String filename)
	{
		String [] channel_string = new String[6];
		channel_string[0] = new String("blue");
		channel_string[1] = new String("green");
		channel_string[2] = new String("red");
		channel_string[3] = new String("blue-green");
		channel_string[4] = new String("red-green");
		channel_string[5] = new String("red-blue");
		
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
		   
		    int  channel_id[] = DeltaMapper.getChannels(set_id);
		    for(int i = 0; i < 3; i++)
		    {
		    	int j                 = channel_id[i];
		    	min[i]                = in.readInt();
		    	init[i]               = in.readInt();
		    	delta_min[i]          = in.readInt();
		    	length[i]             = in.readInt();
		    	compressed_length[i]  = in.readInt();
		    	channel_iterations[i] = in.readByte();
				int table_length      = in.readShort();
				int max_byte_value    = Byte.MAX_VALUE * 2 + 1;   
				int [] table          = new int[table_length];
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
				
				if(delta_type == 5)
				{
					short  map_table_length = in.readShort();
					int [] map_table        = new int[map_table_length];	
					for(int k = 0; k < map_table_length; k++)
						map_table[k] = in.readShort();
					short byte_length = in.readShort();
					byte [] map_string    = new byte[byte_length];
				    in.read(map_string, 0, byte_length);
				  
				    byte increment  = in.readByte();
				    short dimension = in.readShort();
				    byte [] map = new byte[dimension];
				    
				    byte iterations = StringMapper.getIterations(map_string);
				    int  size = 0;
				    if(iterations == 0)
				    {
				        size = StringMapper.unpackStrings2(map_string, map_table, map);
				    }
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
				    
				    if(size != dimension)
				    {
				    	System.out.println("Expected size was " + dimension);
				    	System.out.println("Actual size was " + size);
				    }
				    
				    for(int k = 0; k < map.length; k++)
				    	map[k] += increment;
				    
				    map_list.add(map);
				}
				
				int string_length = in.readInt();
			    int zipped_length = in.readInt();
			    byte [] zipped_string  = new byte[zipped_length];
		        in.read(zipped_string, 0, zipped_length);
		        byte[] string     = new byte[string_length];
		        Inflater inflater = new Inflater();
		        inflater.setInput(zipped_string, 0, zipped_length);
		        int unzipped_length = inflater.inflate(string);
		        if(unzipped_length != string_length)
		        	System.out.println("Unzipped string not expected length.");
		        string_list.add(string);
		    }
		    
		    
		    long stop = System.nanoTime();
		    long time = stop - start;
		    
		    System.out.println("It took " + (time / 1000000) + " ms to read file.");
		    
		    
		    
		    ArrayList channel_list = new ArrayList();
		    
		    start = System.nanoTime();
		    
		    for(int i = 0; i < 3; i++)
		    {
		    	byte [] string = (byte [])string_list.get(i);
		    	byte iterations = (byte)(string[string.length - 1] & 31);
		    	
		    	if(iterations != 0)
		    	{
		    		byte [] decompressed_string = new byte[xdim * ydim * 4];
		    		int decompressed_length = 0;
		    	    if(iterations < 16)
		    	        decompressed_length = StringMapper.decompressZeroStrings(string, compressed_length[i], decompressed_string);
		    	    else
		    	        decompressed_length = StringMapper.decompressOneStrings(string, compressed_length[i], decompressed_string);
		    	    int byte_length = decompressed_length / 8;
		    	    if(decompressed_length % 8 != 0)
		    	        byte_length++;
		    	    
		    	    string = new byte[byte_length + 1];
		    	    for(int j = 0; j < byte_length; j++)
		    	        string[j] = decompressed_string[j];
		    	}
		    	
		    	int [] table = (int [])table_list.get(i);
		    	int [] delta = new int[1];
		    	if(pixel_quant == 0)
		    	{
		    		delta = new int[xdim * ydim];
		    	    int number_unpacked = StringMapper.unpackStrings2(string, table, delta);
				    for(int j = 1; j < delta.length; j++)
				       delta[j] += delta_min[i];
		    	
				    if(delta_type == 0)
				    {
				        int [] current_channel = DeltaMapper.getValuesFromHorizontalDeltas(delta, xdim , ydim, init[i]);
				        if(channel_id[i] > 2)
				           for(int j = 0; j < current_channel.length; j++)
							    current_channel[j] += min[i];
					    channel_list.add(current_channel);
				    }
				    else if(delta_type == 1)
				    {
				        int [] current_channel = DeltaMapper.getValuesFromVerticalDeltas(delta, xdim , ydim, init[i]);
				        if(channel_id[i] > 2)
				           for(int j = 0; j < current_channel.length; j++)
							    current_channel[j] += min[i];
					    channel_list.add(current_channel);
				    }
				    else if(delta_type == 2)
				    {
				        int [] current_channel = DeltaMapper.getValuesFromAverageDeltas(delta, xdim , ydim, init[i]);
				        if(channel_id[i] > 2)
				           for(int j = 0; j < current_channel.length; j++)
							    current_channel[j] += min[i];
					    channel_list.add(current_channel);
				    }
				    else if(delta_type == 3)
				    {
				        int [] current_channel = DeltaMapper.getValuesFromPaethDeltas(delta, xdim , ydim, init[i]);
				        if(channel_id[i] > 2)
				           for(int j = 0; j < current_channel.length; j++)
							    current_channel[j] += min[i];
					    channel_list.add(current_channel);
				    }
				    else if(delta_type == 4)
				    {
				        int [] current_channel = DeltaMapper.getValuesFromGradientDeltas(delta, xdim , ydim, init[i]);
				        if(channel_id[i] > 2)
				           for(int j = 0; j < current_channel.length; j++)
							    current_channel[j] += min[i];
					    channel_list.add(current_channel);
				    }
				    else if(delta_type == 5)
				    {
				        byte [] map = (byte [])map_list.get(i);
				        int [] current_channel = DeltaMapper.getValuesFromMixedDeltas(delta, xdim , ydim, init[i], map);
				        if(channel_id[i] > 2)
					           for(int j = 0; j < current_channel.length; j++)
								    current_channel[j] += min[i];
						channel_list.add(current_channel);
				    }
				    else
				    {
				    	System.out.println("Delta type " + delta_type + " not supported.");
				    }
		    	}
		    	else
		    	{
		    		double factor = pixel_quant;
			        factor       /= 10;
			        int _xdim = xdim - (int)(factor * (xdim / 2 - 2));
			        int _ydim = ydim - (int)(factor * (ydim / 2 - 2));
			        delta = new int[_xdim * _ydim];
			        
			        int number_unpacked = StringMapper.unpackStrings2(string, table, delta);
				    for(int j = 1; j < delta.length; j++)
				       delta[j] += delta_min[i];
				    
				    if(delta_type == 0)
				    {
				        int [] current_channel = DeltaMapper.getValuesFromHorizontalDeltas(delta, _xdim , _ydim, init[i]);
				        if(channel_id[i] > 2)
				           for(int j = 0; j < current_channel.length; j++)
							    current_channel[j] += min[i];
				        int [] resized_channel = ResizeMapper.resize(current_channel, _xdim, xdim, ydim);
					    channel_list.add(resized_channel);
				    }
				    else if(delta_type == 1)
				    {
				        int [] current_channel = DeltaMapper.getValuesFromVerticalDeltas(delta, _xdim , _ydim, init[i]);
				        if(channel_id[i] > 2)
				           for(int j = 0; j < current_channel.length; j++)
							    current_channel[j] += min[i];
				        int [] resized_channel = ResizeMapper.resize(current_channel, _xdim, xdim, ydim);
					    channel_list.add(resized_channel);
				    }
				    else if(delta_type == 2)
				    {
				        int [] current_channel = DeltaMapper.getValuesFromAverageDeltas(delta, _xdim , _ydim, init[i]);
				        if(channel_id[i] > 2)
				           for(int j = 0; j < current_channel.length; j++)
							    current_channel[j] += min[i];
				        int [] resized_channel = ResizeMapper.resize(current_channel, _xdim, xdim, ydim);
					    channel_list.add(resized_channel);
				    }
				    if(delta_type == 3)
				    {
				    	int [] current_channel = DeltaMapper.getValuesFromPaethDeltas(delta, _xdim , _ydim, init[i]);
				        if(channel_id[i] > 2)
				           for(int j = 0; j < current_channel.length; j++)
							   current_channel[j] += min[i];
				        int [] resized_channel = ResizeMapper.resize(current_channel, _xdim, xdim, ydim);
				        channel_list.add(resized_channel);	
				    }
				    else if(delta_type == 4)
				    {
				    	int [] current_channel = DeltaMapper.getValuesFromGradientDeltas(delta, _xdim , _ydim, init[i]);
				        if(channel_id[i] > 2)
				           for(int j = 0; j < current_channel.length; j++)
							   current_channel[j] += min[i];
				        int [] resized_channel = ResizeMapper.resize(current_channel, _xdim, xdim, ydim);
				        channel_list.add(resized_channel);	
				    }
				    else if(delta_type == 5)
				    {
				        byte [] map = (byte [])map_list.get(i);
				        int [] current_channel = DeltaMapper.getValuesFromMixedDeltas(delta, _xdim , _ydim, init[i], map);
				        if(channel_id[i] > 2)
				           for(int j = 0; j < current_channel.length; j++)
							   current_channel[j] += min[i];
				        int [] resized_channel = ResizeMapper.resize(current_channel, _xdim, xdim, ydim);
				        channel_list.add(resized_channel);
				    }
		    	}
		    }
		    
		    stop = System.nanoTime();
		    time = stop - start;
		    
		    System.out.println("It took " + (time / 1000000) + " ms to decompress data.");
		    
		    start = System.nanoTime();
		    
		    
		    int [] blue = new int[xdim * ydim];
		    int [] green = new int[xdim * ydim];
		    int [] red = new int[xdim * ydim];
		    if(set_id == 0)
			{
				blue  = (int [])channel_list.get(0);
				green = (int [])channel_list.get(1);
				red   = (int [])channel_list.get(2);
		    }
			else if(set_id == 1)
			{ 
				blue      = (int [])channel_list.get(0);
				red       = (int [])channel_list.get(1);
				int []red_green = (int [])channel_list.get(2);
				green     = DeltaMapper.getDifference(red, red_green);
		    }
			else if(set_id == 2)
			{ 
				blue       = (int [])channel_list.get(0);
				red        = (int [])channel_list.get(1);
				int [] blue_green = (int [])channel_list.get(2);
				green      = DeltaMapper.getDifference(blue, blue_green);
			}
			else if(set_id == 3)
			{ 
				blue       = (int [])channel_list.get(0);
				int [] blue_green = (int [])channel_list.get(1);
				green      = DeltaMapper.getDifference(blue, blue_green);
				int [] red_green  = (int [])channel_list.get(2);
				red = DeltaMapper.getSum(red_green, green);
			}
			else if(set_id == 4)
			{ 
				blue       = (int [])channel_list.get(0);
				int [] blue_green = (int [])channel_list.get(1);
				green      = DeltaMapper.getDifference(blue, blue_green);
				int [] red_blue   = (int [])channel_list.get(2);
				red        = DeltaMapper.getSum(blue, red_blue);
			}
		    else if(set_id == 5)
			{
		    	green   = (int [])channel_list.get(0);
		    	red     = (int [])channel_list.get(1);
		    	int [] blue_green = (int [])channel_list.get(2);
		    	blue = DeltaMapper.getSum(blue_green, green);
			}
			else if(set_id == 6)
			{
				red     = (int [])channel_list.get(0);
				int [] blue_green = (int [])channel_list.get(1);
				int [] red_green = (int [])channel_list.get(2);
				for(int i = 0; i < red_green.length; i++)
					red_green[i] = -red_green[i];
				green = DeltaMapper.getSum(red_green, red);
				blue = DeltaMapper.getSum(blue_green, green);	
			}
			else if(set_id == 7)
			{
				green   = (int [])channel_list.get(0);
				int [] blue_green = (int [])channel_list.get(1);
				blue = DeltaMapper.getSum(green, blue_green);
				int [] red_green = (int [])channel_list.get(2);
				red  = DeltaMapper.getSum(green, red_green);
			}
			else if(set_id == 8)
			{
				green     = (int [])channel_list.get(0);
				int [] red_green = (int [])channel_list.get(1);
				red       = DeltaMapper.getSum(green, red_green);
				int [] red_blue  = (int [])channel_list.get(1);
				blue      = DeltaMapper.getDifference(red, red_blue);
		    }
			else if(set_id == 9)
			{
				red       = (int [])channel_list.get(0);
				int [] red_green = (int [])channel_list.get(1);
				green     = DeltaMapper.getDifference(red, red_green); 
				int [] red_blue  = (int [])channel_list.get(2);
				blue      = DeltaMapper.getDifference(red, red_blue);
			}
			
		    
			image = new BufferedImage(xdim, ydim, BufferedImage.TYPE_INT_RGB);	
		
			for(int i = 0; i < ydim; i++)
			{
				for(int j = 0; j < xdim; j++)
				{
					int k = i * xdim + j;
					
					int pixel = 0;
					
					blue[k] <<= pixel_shift;
					pixel |= blue[k] << 16;
					
					green[k] <<= pixel_shift;
					pixel |= green[k] << 8;
					
					red[k] <<= pixel_shift;
					pixel |= red[k];
				
				    image.setRGB(j, i, pixel);
				}
			}
			stop = System.nanoTime();
			time = stop - start; 
			System.out.println("It took " + (time / 1000000) + " ms assemble rgb files.");
			
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
	
	
}
