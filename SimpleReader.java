import java.awt.*;
import java.awt.image.*;
import java.io.*;
import java.awt.event.*;
import java.util.*;
import java.util.zip.*;
import javax.swing.*;

public class SimpleReader
{
	int  xdim              = 0;
	int  ydim              = 0;
	int  intermediate_xdim = 0;
	int  intermediate_ydim = 0;
	int  pixel_shift       = 0;
	int  pixel_quant       = 0;
	int  set_id            = 0;
	byte delta_type        = 0;

	ArrayList string_list = new ArrayList();
	ArrayList table_list  = new ArrayList();
	ArrayList map_list    = new ArrayList();
	int[][] channel_array = new int[3][0];
	int  min[]            = new int[3];
	int  init[]           = new int[3];
	int  delta_min[]      = new int[3];
	byte type[]           = new byte[3];
	byte compressed[]     = new byte[3];
	int  length[]         = new int[3];
	int  compressed_length[] = new int[3];
	byte channel_iterations[] = new byte[3];
	
	int [][] resize_array = new int[3][0];

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
			File file          = new File(filename);
			DataInputStream in = new DataInputStream(new FileInputStream(file));
			xdim               = in.readShort();
			ydim               = in.readShort();
			pixel_shift        = in.readByte();
			pixel_quant        = in.readByte();
			set_id             = in.readByte();
			delta_type         = in.readByte();
			//int[] channel_id   = DeltaMapper.getChannels(set_id);

			if (delta_type > 7)
			{
				System.out.println("Delta type " + delta_type + " not supported.");
				System.exit(0);
			}
			System.out.println("Set id is " + set_id);

			long start = System.nanoTime();
			for (int i = 0; i < 3; i++)
			{
				//int j                 = channel_id[i];
				min[i]                = in.readInt();
				init[i]               = in.readInt();
				delta_min[i]          = in.readInt();
				length[i]             = in.readInt();
				compressed_length[i]  = in.readInt();
				channel_iterations[i] = in.readByte();

				int table_length = in.readShort();
				int[] table = new int[table_length];
				int max_byte_value = Byte.MAX_VALUE * 2 + 1;
				if(table.length <= max_byte_value)
				{
					for (int k = 0; k < table_length; k++)
					{
						table[k] = in.readByte();
						if (table[k] < 0)
							table[k] = max_byte_value + 1 + table[k];
					}
				} 
				else
				{
					for (int k = 0; k < table_length; k++)
						table[k] = in.readShort();
				}
				table_list.add(table);

				if (delta_type == 5 || delta_type == 6 || delta_type == 7)
				{
					int map_length        = in.readInt();
					int packed_map_length = in.readInt();	
					byte [] packed_map    = new byte[packed_map_length];
					in.read(packed_map, 0, packed_map_length);
					byte [] map           = SegmentMapper.unpackBits(packed_map, map_length, 2);
					map_list.add(map);
				}
				
				int string_length = compressed_length[i] / 8;
				if(compressed_length[i] % 8 != 0)
					string_length++;
				string_length++;
				byte [] string = new byte[string_length];
				
				int zipped_length = in.readInt();
				byte [] zipped_data = new byte[zipped_length];
				in.read(zipped_data, 0, zipped_length);
				
				Inflater inflater = new Inflater();
				inflater.setInput(zipped_data, 0, zipped_length);
				int unzipped_length = inflater.inflate(string);
				if(unzipped_length != string_length)
				{
					System.out.println("Unzipped data not expected length.");	
				}
				inflater.end();
				string_list.add(string);
			}
            in.close();
            
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

			
			start = System.nanoTime();
			
			BufferedImage image   = new BufferedImage(xdim, ydim, BufferedImage.TYPE_INT_RGB);
            int[][]       channel = new int[3][0];
           
            if(set_id == 0)
            {
            	    channel[0] = channel_array[0];
            	    channel[1] = channel_array[1];
            	    channel[2] = channel_array[2];
            }
            else if(set_id == 1)
            {
            	    channel[0] = channel_array[0];
            	    channel[1] = DeltaMapper.getDifference(channel_array[1], channel_array[2]);
            	    channel[2] = channel_array[1];
            }
            else if(set_id == 2)
            {
            	    channel[0] = channel_array[0];
        	        channel[1] = DeltaMapper.getDifference(channel_array[0], channel_array[2]);
        	        channel[2] = channel_array[1];    
            }
            else if(set_id == 3)
            {
            	    channel[0] = channel_array[0];
    	            channel[1] = DeltaMapper.getDifference(channel_array[0], channel_array[1]);
    	            channel[2] = DeltaMapper.getSum(channel_array[2], channel[1]);    
            }
            else if(set_id == 4)
			{
				channel[0] = channel_array[0];
				channel[1] = DeltaMapper.getDifference(channel_array[0], channel_array[1]);
				channel[2] = DeltaMapper.getSum(channel_array[0], channel_array[2]);
			}
            else if(set_id == 5) 
            {
            	    channel[0] = DeltaMapper.getSum(channel_array[2], channel_array[0]);
            	    channel[1] = channel_array[0];
            	    channel[2] = channel_array[1];
            }
            else if(set_id == 6)
            {
            	    for (int i = 0; i < channel_array[2].length; i++)
					channel_array[2][i] = -channel_array[2][i];
				channel[1] = DeltaMapper.getSum(channel_array[2], channel_array[0]);
				channel[0] = DeltaMapper.getSum(channel_array[1], channel[1]);
				channel[2] = channel_array[0];    
            }
            else if(set_id == 7)
            {
              	channel[0] = DeltaMapper.getSum(channel_array[0], channel_array[1]);
				channel[1] = channel_array[0];
				channel[2] = DeltaMapper.getSum(channel_array[0], channel_array[2]);
            }
            else if(set_id == 8)
            {
            	    channel[2] = DeltaMapper.getSum(channel_array[0], channel_array[1]);
            	    channel[0] = DeltaMapper.getDifference(channel[2], channel_array[2]);
            	    channel[1] = channel_array[0];
            }
            else if(set_id == 9)
            {
            	    channel[0] = DeltaMapper.getDifference(channel_array[0], channel_array[2]);
            	    channel[1] = DeltaMapper.getDifference(channel_array[0], channel_array[1]);
            	    channel[2] = channel_array[0];
            }
            
            if(pixel_quant == 0)
            {
            	    int[] pixel = DeltaMapper.getPixel(channel[0], channel[1], channel[2], xdim, pixel_shift);
				image.setRGB(0, 0, xdim, ydim, pixel, 0, xdim);
            }
            else
            {
            	    if(xdim > 600) 
            	    {
            	    	    // If the image is larger it might make sense to try to use all the processors,
            	    	    // and do the resizing in segments with each channel done sequentially.
              	    Thread [] resize_thread = new Thread[3]; 
				    for(int i = 0; i < 3; i++) 
				    {
				        resize_thread[i] = new Thread(new Resizer(channel[i], intermediate_xdim, xdim, ydim, i));
				        resize_thread[i].start(); 
				    } 
				    for(int i = 0; i < 3; i++)
				        resize_thread[i].join();
				
				    int[] pixel = DeltaMapper.getPixel(resize_array[0], resize_array[1], resize_array[2], xdim, pixel_shift);
				    image.setRGB(0, 0, xdim, ydim, pixel, 0, xdim);
            	    }
            	    else
            	    {
				    // If the image is small enough, serial processing is faster than parallel processing.
            	    	    channel[0] = ResizeMapper.resize(channel[0], intermediate_xdim, xdim, ydim);
            	    	    channel[1] = ResizeMapper.resize(channel[1], intermediate_xdim, xdim, ydim);
            	    	    channel[2] = ResizeMapper.resize(channel[2], intermediate_xdim, xdim, ydim);
            	    	    
            	    	    int[] pixel = DeltaMapper.getPixel(channel[0], channel[1], channel[2], xdim, pixel_shift);
				    image.setRGB(0, 0, xdim, ydim, pixel, 0, xdim);
            	    }
            }
            
			stop = System.nanoTime();
			time = stop - start;
			System.out.println("It took " + (time / 1000000) + " ms to assemble and load rgb files.");

			JFrame frame = new JFrame("Simple Reader");
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
		catch (Exception e)
		{
			System.out.println(e.toString());
		}
	}
	
	class Resizer implements Runnable
	{
		int [] src;
		int    xdim, new_xdim, new_ydim, i;
		
		public Resizer(int [] src, int xdim, int new_xdim, int new_ydim, int i)
		{
			this.src      = src;
			this.xdim     = xdim;
			this.new_xdim = new_xdim;
			this.new_ydim = new_ydim;
			this.i        = i;	
		}
		
		public void run()
		{
			int[] dst       = ResizeMapper.resize(src, xdim, new_xdim, new_ydim);	
			resize_array[i] = dst;
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
			int[] channel_id = DeltaMapper.getChannels(set_id);

			if (delta_type < 8)
			{
				byte[] string  = (byte[]) string_list.get(i);
				int[] table    = (int[]) table_list.get(i);
				int iterations = StringMapper.getIterations(string);
				int bitlength  = StringMapper.getBitlength(string);
				string         =  StringMapper.decompressStrings2(string);
				
				if(channel_iterations[i] != iterations)
					System.out.println("Iterations appended to string does not agree with channel " + i + " information.");
				if(compressed_length[i] != bitlength)
					System.out.println("Bit length appended to string does not agree with channel " + i + " information.");
				
				int[] delta;
				int   number_unpacked = 0;
				int   current_xdim    = 0;
				int   current_ydim    = 0;
				
				if (pixel_quant == 0)
				{
					delta = new int[xdim * ydim];
					number_unpacked = StringMapper.unpackStrings2(string, table, delta);

					for (int j = 1; j < delta.length; j++)
						delta[j] += delta_min[i];

					current_xdim = xdim;
					current_ydim = ydim;
				} 
				else
				{
					double factor = pixel_quant;
					factor /= 10;
					intermediate_xdim = xdim - (int) (factor * (xdim / 2 - 2));
					intermediate_ydim = ydim - (int) (factor * (ydim / 2 - 2));
					delta = new int[intermediate_xdim * intermediate_ydim];
					number_unpacked = StringMapper.unpackStrings2(string, table, delta);
					for (int j = 1; j < delta.length; j++)
						delta[j] += delta_min[i];

					current_xdim = intermediate_xdim;
					current_ydim = intermediate_ydim;
				}

				int[] current_channel = new int[1];
				if (delta_type == 0)
					current_channel = DeltaMapper.getValuesFromHorizontalDeltas(delta, current_xdim, current_ydim, init[i]);
				else if (delta_type == 1)
					current_channel = DeltaMapper.getValuesFromVerticalDeltas(delta, current_xdim, current_ydim, init[i]);
				else if (delta_type == 2)
					current_channel = DeltaMapper.getValuesFromAverageDeltas(delta, current_xdim, current_ydim, init[i]);
				else if (delta_type == 3)
					current_channel = DeltaMapper.getValuesFromPaethDeltas(delta, current_xdim, current_ydim, init[i]);
				else if (delta_type == 4)
					current_channel = DeltaMapper.getValuesFromGradientDeltas(delta, current_xdim, current_ydim, init[i]);
				else if (delta_type == 5)
				{
					byte[] map = (byte[]) map_list.get(i);
					current_channel = DeltaMapper.getValuesFromMixedDeltas(delta, current_xdim, current_ydim, init[i], map);
				} 
				else if (delta_type == 6)
				{
					byte[] map = (byte[]) map_list.get(i);
					current_channel = DeltaMapper.getValuesFromMixedDeltas2(delta, current_xdim, current_ydim, init[i], map);
				} 
				else if (delta_type == 7)
				{
					byte[] map = (byte[]) map_list.get(i);
					current_channel = DeltaMapper.getValuesFromIdealDeltas(delta, current_xdim, current_ydim, init[i], map);
				}

				if (channel_id[i] > 2)
					for (int j = 0; j < current_channel.length; j++)
						current_channel[j] += min[i];

				channel_array[i] = current_channel;
			} 
			else
			{
				System.out.println("Delta type " + delta_type + " not supported.");
				System.exit(0);
			}
		}
	}
}