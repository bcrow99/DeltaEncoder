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

public class HuffmanReader
{
	int xdim        = 0;
	int ydim        = 0;
	int pixel_shift = 0;
	int pixel_quant = 0;
	int set_id      = 0;
	byte delta_type = 0;
	
	ArrayList string_list = new ArrayList();
	ArrayList table_list  = new ArrayList();
	ArrayList map_list    = new ArrayList();
	ArrayList code_list   = new ArrayList();
	ArrayList code_length_list = new ArrayList();
	
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
			System.out.println("Usage: java HuffmanReader <filename>");
			System.exit(0);
		}
	
		HuffmanReader reader = new HuffmanReader(args[0]);
	}
	
	public HuffmanReader(String filename)
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
		    	int j        = channel_id[i];
		    	min[i]       = in.readInt();
		    	init[i]      = in.readInt();
	    	    delta_min[i] = in.readInt();
	    	    length[i]    = in.readInt();
			    int table_length = in.readShort();
			    int [] table = new int[table_length];
			    
			    if(table.length <= Byte.MAX_VALUE * 2 + 1)
			    {
			    	byte [] buffer = new byte[table.length];
			    	in.read(buffer, 0, table.length);
			    	for(int k = 0; k < buffer.length; k++)
			    	{
			    		table[k] = buffer[k];
			    		if(table[k] < 0)
            			    table[k] += Byte.MAX_VALUE * 2 + 2;
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
				
			    int n           = in.readInt();
			    byte init_value = in.readByte();
			    byte max_delta  = in.readByte();
			    byte packed_delta_length = in.readByte();
			    byte [] packed_delta = new byte[packed_delta_length];
			    in.read(packed_delta, 0, packed_delta_length);
			    
			    byte [] code_length = CodeMapper.unpackLengthTable(n, init_value, max_delta, packed_delta);
			    
				int [] code = CodeMapper.getCanonicalCode(code_length);
				code_list.add(code);
				code_length_list.add(code_length);
			    
			    int string_length = in.readInt();
			    byte [] string = new byte[string_length];
				in.read(string, 0, string_length);
				string_list.add(string);
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
			System.out.println("It took " + (time / 1000000) + " ms to assemble rgb files.");
		    
		  
			
			BufferedImage image = new BufferedImage(xdim, ydim, BufferedImage.TYPE_INT_RGB);	
			
			start = System.nanoTime();
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
			System.out.println("It took " + (time / 1000000) + " ms to load rgb files.");
			
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
			
			if(delta_type < 8)
			{
			    byte [] string      = (byte [])string_list.get(i);
			    int  [] table       = (int [])table_list.get(i);
			    int  [] code        = (int [])code_list.get(i);
			    byte [] code_length = (byte [])code_length_list.get(i);
			 
	    	    int [] delta;
	    	    int current_xdim = 0;
	    	    int current_ydim = 0;
	    	    if(pixel_quant == 0)
	    	    {
	    		    delta = new int[xdim * ydim];
	    		    
	    		    int number_unpacked  =  CodeMapper.unpackCode(string, table, code, code_length, length[i], delta);
	    		    
	    		    delta[0] = 0;
			        for(int j = 1; j < delta.length; j++)
			           delta[j] += delta_min[i];
			        
			        current_xdim = xdim;
			        current_ydim = ydim;
	    	    }
	    	    else
	    	    {
	    		    double factor = pixel_quant;
		            factor       /= 10;
		            int intermediate_xdim = xdim - (int)(factor * (xdim / 2 - 2));
		            int intermediate_ydim = ydim - (int)(factor * (ydim / 2 - 2));
		            delta = new int[intermediate_xdim * intermediate_ydim];
		            
		            int number_unpacked  =  CodeMapper.unpackCode(string, table, code, code_length, length[i], delta);
		            delta[0] = 0;
			        for(int j = 1; j < delta.length; j++)
			           delta[j] += delta_min[i];	
			        
			        current_xdim = intermediate_xdim;
			        current_ydim = intermediate_ydim; 
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
			        current_channel = DeltaMapper.getValuesFromMixedDeltas2(delta, current_xdim , current_ydim, init[i], map);
    		    }
    		    else if(delta_type == 7)
    		    {
    			    byte [] map = (byte [])map_list.get(i);
			        current_channel = DeltaMapper.getValuesFromIdealDeltas2(delta, current_xdim , current_ydim, init[i], map);
    		    }
    		    
    		    if(channel_id[i] > 2)
    	            for(int j = 0; j < current_channel.length; j++)
    				    current_channel[j] += min[i];	
        		
        		if(pixel_quant == 0)
                	channel_array[i] = current_channel;
                else
                {
                	int [] resized_channel = ResizeMapper.resize(current_channel, current_xdim, xdim, ydim);
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