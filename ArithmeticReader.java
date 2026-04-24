import java.awt.*;
import java.awt.image.*;
import java.io.*;
import java.awt.event.*;
import java.util.*;
import java.util.concurrent.*;
import java.math.*;
import java.util.zip.*;
import javax.imageio.*;
import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

public class ArithmeticReader
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
	
	ArrayList <int [][]>        frequency_list = new ArrayList <int [][]> ();
	ArrayList <BigInteger [][]> offset_list   = new ArrayList <BigInteger [][]> ();
	ArrayList <byte [][]>       segment_list  = new ArrayList <byte [][]> ();
	
	int [][] resize_array = new int[3][0];
	       
	public static void main(String[] args)
	{
		if (args.length != 1)
		{
			System.out.println("Usage: java ArithmeticReader <filename>");
			System.exit(0);
		}

		ArithmeticReader reader = new ArithmeticReader(args[0]);
	}

	public ArithmeticReader(String filename)
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
			int[] channel_id   = DeltaMapper.getChannels(set_id);

			if (delta_type > 7)
			{
				System.out.println("Delta type not supported.");
				System.exit(0);
			}
			System.out.println("Set id is " + set_id);

			long start = System.nanoTime();
			for (int i = 0; i < 3; i++)
			{
				// System.out.println("Getting channel " + i);
				int j                 = channel_id[i];
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
				
				
				int number_of_segments = in.readInt();
				int length_type        = in.readInt();
				int freq_zipped_length = in.readInt();
			
				byte [] freq_zipped_data = new byte[freq_zipped_length];
				in.read(freq_zipped_data, 0, freq_zipped_length);
				
				int number_of_bytes = 0;
				
				if(length_type == 0)
					number_of_bytes = number_of_segments * 256;
				else if(length_type == 1)
					number_of_bytes = number_of_segments * 256 * 2;
				else if(length_type == 2)
					number_of_bytes = number_of_segments * 256 * 4;
				
				byte [] frequency_bytes = new byte[number_of_bytes];
				
				Inflater inflater = new Inflater();
				inflater.setInput(freq_zipped_data, 0, freq_zipped_length);
				int unzipped_length = inflater.inflate(frequency_bytes);
			
				int [][] frequency = new int [number_of_segments][256];
				
				if(length_type == 0)
				{
					for(int k = 0; k < number_of_segments; k++)
					{
						for(int m = 0; m < 256; m++)
						{
							frequency[k][m] = frequency_bytes[k * 256 + m];
							if(frequency[k][m] < 0)
								frequency[k][m] += 256;
						}
					}
				}
				else if(length_type == 1)
				{
					for(int k = 0; k < number_of_segments; k++)
					{
						for(int m = 0; m < 256; m++)
						{
							int a = frequency_bytes[k * 512 + 2 * m];
							if(a < 0)
								a += 256;
							int b = frequency_bytes[k * 512 + 2 * m + 1];
							if(b < 0)
								b += 256;
							b <<= 8;
							
							frequency[k][m] = a | b;
						}
					}	
				}
				else if(length_type == 2)
				{
					for(int k = 0; k < number_of_segments; k++)
					{
						for(int m = 0; m < 256; m++)
						{
							int a = frequency_bytes[k * 1024 + 4 * m];
							if(a < 0)
								a += 256;
							
							int b = frequency_bytes[k * 1024 + 4 * m + 1];
							if(b < 0)
								b += 256;
							b <<= 8;
							
							int c = frequency_bytes[k * 1024 + 4 * m + 2];
							if(c < 0)
								c += 256;
							c <<= 16;
							
							int d = frequency_bytes[k * 1024 + 4 * m + 3];
							if(c < 0)
								c += 256;
							c <<= 24;
							
							frequency[k][m] = a | b | c | d;
						}
					}		
				}
				
				frequency_list.add(frequency);
				
				BigInteger [][] offset = new BigInteger[number_of_segments][2];
				for(int k = 0; k < number_of_segments; k++)
				{
					int length = in.readInt();
					byte [] byte_array = new byte[length];
					in.read(byte_array, 0, length);
					
					offset[k][0] = new BigInteger(byte_array);
					
					length = in.readInt();
					byte_array = new byte[length];
					in.read(byte_array, 0, length);
					
					offset[k][1] = new BigInteger(byte_array);
				}
				
				offset_list.add(offset);
				
				byte [][] segment = new byte[number_of_segments][0];
				segment_list.add(segment);
			}

			long stop = System.nanoTime();
			long time = stop - start;
			System.out.println("It took " + (time / 1000000) + " ms to read file.");
			
			
			start = System.nanoTime();
			
			
			int number_of_processors = Runtime.getRuntime().availableProcessors();
            for(int i = 0; i < 3; i++)
            {
            	    int string_length = compressed_length[i] / 8;
				if(compressed_length[i] % 8 != 0)
					string_length++;
				string_length++;
				byte [] string = new byte[string_length];  
				
				int [][] frequency = frequency_list.get(i);
				BigInteger [][] offset = offset_list.get(i);
				
				int number_of_segments = frequency.length;
				
                int segment_length = string.length / number_of_segments;
				
				int odd_segment_length = segment_length + string.length % number_of_segments;
				
				
				
				Thread [] decoder_thread = new Thread[number_of_segments]; 
				for(int j = 0; j < number_of_segments; j++) 
				{
					int n = segment_length;
					if(j == number_of_segments - 1)
						n = odd_segment_length;
				    decoder_thread[j] = new Thread(new Decoder(offset[j], frequency[j], n, i, j));
				    decoder_thread[j].start(); 
				} 
				for(int j = 0; j < number_of_segments; j++)
				    decoder_thread[j].join();	
				
				byte [][] segment = segment_list.get(i);
				for(int j = 0; j < segment.length; j++)
				{
					for(int k = 0; k < segment[j].length; k++)
					{
					    string[j * segment_length + k] = segment[j][k];	
					}
				}
				
				string_list.add(string);
            }
            
			stop = System.nanoTime();
			time = stop - start;
			System.out.println("It took " + (time / 1000000) + " ms to get arithmetic values.");
			
			start = System.nanoTime();

			Thread [] decompression_thread = new Thread[3]; 
			for(int i = 0; i < 3; i++) 
			{
			    decompression_thread[i] = new Thread(new Decompressor(i));
			    decompression_thread[i].start(); 
			} 
			for(int i = 0; i < 3; i++)
			    decompression_thread[i].join();

			
			//ExecutorService executorService = Executors.newFixedThreadPool(3);
			//for (int i = 0; i < 3; i++)
			//{
			//	executorService.submit(new Decompressor(i));
			//}
			//executorService.shutdown();
			//executorService.awaitTermination(Long.MAX_VALUE, TimeUnit.NANOSECONDS);
			
			
			stop = System.nanoTime();
			time = stop - start;

			System.out.println("It took " + (time / 1000000) + " ms to process data.");

			BufferedImage image  = new BufferedImage(xdim, ydim, BufferedImage.TYPE_INT_RGB);
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
				    int[] resized_blue  = ResizeMapper.resize(channel[0], intermediate_xdim, xdim, ydim);
				    int[] resized_green = ResizeMapper.resize(channel[1], intermediate_xdim, xdim, ydim);
				    int[] resized_red   = ResizeMapper.resize(channel[2], intermediate_xdim, xdim, ydim);
				    
				    int[] pixel         = DeltaMapper.getPixel(resized_blue, resized_green, resized_red, xdim, pixel_shift);
				    image.setRGB(0, 0, xdim, ydim, pixel, 0, xdim);
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
			frame.setExtendedState(JFrame.NORMAL);
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
	
	class Decoder implements Runnable
	{
	    BigInteger [] offset; 
	    int [] frequency;
	    int n, i, j;
	    
	    public Decoder(BigInteger [] offset, int [] frequency, int n, int i, int j)
		{
			this.offset    = offset;
			this.frequency = frequency;
			this.n         = n;
			this.i         = i;
			this.j         = j;
		}
	    
	    public void run()
	    {
	    	    byte [][] segment = segment_list.get(i);
	    	    segment[j] = CodeMapper.getArithmeticValues(offset, frequency, n);
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