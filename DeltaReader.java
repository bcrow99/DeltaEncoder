import java.awt.*;
import java.awt.image.*;
import java.io.*;
import java.awt.event.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.zip.*;
import javax.imageio.*;
import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

public class DeltaReader
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
					short map_table_length = in.readShort();
					int[] map_table = new int[map_table_length];
					for (int k = 0; k < map_table_length; k++)
						map_table[k] = in.readShort();
					int byte_length = in.readInt();

					byte[] map_string = new byte[byte_length];
					in.read(map_string, 0, byte_length);

					byte increment = in.readByte();
					int dimension = in.readInt();
					byte[] map = new byte[dimension];
					byte iterations = StringMapper.getIterations(map_string);
					
					System.out.println("Iterations of map string is " + iterations);

					int size = 0;
					if (iterations == 0 || iterations == 16)
						size = StringMapper.unpackStrings2(map_string, map_table, map);
					else
					{
						byte[] decompressed_string = StringMapper.decompressStrings2(map_string);	
						size = StringMapper.unpackStrings2(decompressed_string, map_table, map);
					}
					
					if (increment != 0)
						for (int k = 0; k < map.length; k++)
							map[k] += increment;
					map_list.add(map);
				}
				
				int number_of_segments = in.readInt();
				if (number_of_segments == 1)
				{
					int n           = in.readInt();
					
				    byte init_value = in.readByte();
				    
				    byte max_delta  = in.readByte();
				   
				    int compressed_length   = in.readInt();
				    
				    byte [] compressed_delta = new byte[compressed_length];
				    in.read(compressed_delta, 0, compressed_length);
				   
				    byte [] decompressed_delta = StringMapper.decompressStrings2(compressed_delta);
				    
				    int packed_delta_length = decompressed_delta.length - 1;
				    byte [] packed_delta    = new byte[packed_delta_length];
				    for(int k = 0; k < packed_delta_length; k++)
				    	    packed_delta[k] = decompressed_delta[k];
				    System.out.println();
				    
				    byte [] code_length = CodeMapper.unpackLengthTable(n, init_value, max_delta, packed_delta);
				   
					int [] code = CodeMapper.getCanonicalCode(code_length);
					
					int m = in.readInt();
					
					int []  rank_table = new int[m];
					byte [] rank       = new byte[m];
					in.read(rank, 0, m);
					for(int k = 0; k < m; k++)
					{
						if(rank[k] >= 0)
							rank_table[k] = rank[k];
						else
					       rank_table[k]  = rank[k] + 256;
					}
					
					int p = in.readInt();
					
					int packed_string_length = in.readInt();
					
				    byte [] packed_string = new byte[packed_string_length];
					in.read(packed_string, 0, packed_string_length);
					
					ArrayList pack_list = new ArrayList();
					pack_list.add(packed_string);
					pack_list.add(packed_string.length * 8);
					pack_list.add(rank_table);
					pack_list.add(code);
					pack_list.add(code_length);
					pack_list.add(p);
					
                    byte [] string = CodeMapper.unpackCode(pack_list);
					string_list.add(string);
				} 
				else
				{
					int total_length = 0;
					
					int[] segment_length = new int[number_of_segments];
					byte[] segment_info = new byte[number_of_segments];
					
					int number_of_zipped_bytes = in.readInt();
					if(number_of_zipped_bytes == 0)
					{
					    in.read(segment_info, 0, number_of_segments);
					    //System.out.println("Segment data was not zipped.");
					}
					else
					{
						//System.out.println("Segment data was zipped.");
						byte[] zipped_data = new byte[number_of_zipped_bytes];
						in.read(zipped_data, 0, number_of_zipped_bytes);
						Inflater inflater = new Inflater();
						inflater.setInput(zipped_data, 0, number_of_zipped_bytes);
						int unzipped_length = inflater.inflate(segment_info);
						if(unzipped_length != number_of_segments)
							System.out.println("Unzipped data not expected length.");	
					}
                     
					int max_bytelength = in.readInt();
					if (max_bytelength <= Byte.MAX_VALUE * 2 + 1)
					{
						//System.out.println("Max segment length is an unsigned byte.");
						byte[] segment_length_bytes = new byte[number_of_segments];
						
						number_of_zipped_bytes = in.readInt();
						if(number_of_zipped_bytes == 0)
						{
						    //System.out.println("Did not zip byte lengths.");
						    in.read(segment_length_bytes, 0, number_of_segments);
						}
						else
						{
							//System.out.println("Zipped byte lengths.");
							byte[] zipped_data = new byte[number_of_zipped_bytes];
							in.read(zipped_data, 0, number_of_zipped_bytes);
							Inflater inflater = new Inflater();
							inflater.setInput(zipped_data, 0, number_of_zipped_bytes);
							int unzipped_length = inflater.inflate(segment_length_bytes);
							if(unzipped_length != number_of_segments)
								System.out.println("Unzipped data not expected length.");	
						}
						
						for (int k = 0; k < number_of_segments; k++)
						{
							int current_length = segment_length_bytes[k];
							if (current_length < 0)
								current_length += 256;
							total_length += current_length;
							segment_length[k] = current_length;
						}
					} 
					else if (max_bytelength <= Short.MAX_VALUE * 2 + 1)
					{
						//System.out.println("Max segment length is an unsigned short.");
						
						byte[] segment_length_bytes = new byte[2 * number_of_segments];
						
						number_of_zipped_bytes = in.readInt();
						if(number_of_zipped_bytes == 0)
						{
						    //System.out.println("Did not zip short lengths.");
						    in.read(segment_length_bytes, 0, 2 * number_of_segments);
						}
						else
						{
							//System.out.println("Zipped short lengths.");
							byte[] zipped_data = new byte[number_of_zipped_bytes];
							in.read(zipped_data, 0, number_of_zipped_bytes);
							Inflater inflater = new Inflater();
							inflater.setInput(zipped_data, 0, number_of_zipped_bytes);
							int unzipped_length = inflater.inflate(segment_length_bytes);
							if(unzipped_length != number_of_segments * 2)
								System.out.println("Unzipped lengths not expected length.");	
						}
						
						for(int k = 0; k < number_of_segments; k++)
						{
							int a = (int)segment_length_bytes[2 * k];
							if(a < 0)
								a += 256;
							int b = (int)segment_length_bytes[2 * k + 1];
							if(b < 0)
								b += 256;
							b   <<= 8;
							
							segment_length[k] = a | b;
							total_length     += segment_length[k];
						}
					} 
					else
					{
						//System.out.println("Max segment length is an int.");
						byte[] segment_length_bytes = new byte[4 * number_of_segments];
						
						number_of_zipped_bytes = in.readInt();
						if(number_of_zipped_bytes == 0)
						{
						    //System.out.println("Did not zip int lengths.");
						    in.read(segment_length_bytes, 0, 4 * number_of_segments);
						}
						else
						{
							//System.out.println("Zipped int lengths.");
							byte[] zipped_data = new byte[number_of_zipped_bytes];
							in.read(zipped_data, 0, number_of_zipped_bytes);
							Inflater inflater = new Inflater();
							inflater.setInput(zipped_data, 0, number_of_zipped_bytes);
							int unzipped_length = inflater.inflate(segment_length_bytes);
							if(unzipped_length != number_of_segments * 4)
								System.out.println("Unzipped lengths not expected length.");	
						}
						
						for(int k = 0; k < number_of_segments; k++)
						{
							int a = (int)segment_length_bytes[2 * k];
							if(a < 0)
								a += 256;
							int b = (int)segment_length_bytes[2 * k + 1];
							if(b < 0)
								b += 256;
							b   <<= 8;
							int c = (int)segment_length_bytes[2 * k + 2];
							if(c < 0)
								c += 256;
							c   <<= 16;
							int d = (int)segment_length_bytes[2 * k + 3];
							if(d < 0)
								d += 256;
							d   <<= 24;
							
							segment_length[k] = a | b | c | d;
							total_length     += segment_length[k];
						}
					}
					
					int zipped_length = in.readInt();
					int packed_length = in.readInt();
					byte [] packed_segments = new byte[packed_length];
					if(zipped_length == 0)
						 in.read(packed_segments, 0, packed_length);	
					else
					{
						byte[] zipped_data = new byte[zipped_length];
						in.read(zipped_data, 0, zipped_length);
						Inflater inflater = new Inflater();
						inflater.setInput(zipped_data, 0, zipped_length);
						int unzipped_length = inflater.inflate(packed_segments);
						if(unzipped_length != packed_length)
							System.out.println("Unzipped segments not expected length.");		
					}
					
					ArrayList <byte []> original_segments = SegmentMapper.unpackSegments2(packed_segments, segment_length, segment_info);
					byte string_data                      = channel_iterations[i];
					int odd_bits                          = compressed_length[i] % 8;
					if(odd_bits != 0)
					{
						byte extra_bits = (byte)(8 - odd_bits);
						extra_bits    <<= 5;
						string_data    |= extra_bits;
					}
				    byte [] restored_string               = SegmentMapper.restore(original_segments, string_data);
					string_list.add(restored_string);
				}
			}

			long stop = System.nanoTime();
			long time = stop - start;
			System.out.println("It took " + (time / 1000000) + " ms to read file.");
			

			int cores = Runtime.getRuntime().availableProcessors();
			System.out.println("There are " + cores + " processors available.");
			start = System.nanoTime();

			// Simplest possible threading.
			Thread [] decompression_thread = new Thread[3]; for(int i = 0; i < 3; i++) 
			{
			    decompression_thread[i] = new Thread(new Decompressor(i));
			    decompression_thread[i].start(); 
			} 
			for(int i = 0; i < 3; i++)
			    decompression_thread[i].join();

			
			// This higher level abstraction makes the program more opaque and
			// does not improve the performance.
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

			BufferedImage image = new BufferedImage(xdim, ydim, BufferedImage.TYPE_INT_RGB);

			start = System.nanoTime();
			if (set_id == 0)
			{
				if (pixel_quant == 0)
				{
					int[] pixel = DeltaMapper.getPixel(channel_array[0], channel_array[1], channel_array[2], xdim,
							pixel_shift);
					image.setRGB(0, 0, xdim, ydim, pixel, 0, xdim);
				} 
				else
				{
					int[] resized_blue = ResizeMapper.resize(channel_array[0], intermediate_xdim, xdim, ydim);
					int[] resized_green = ResizeMapper.resize(channel_array[1], intermediate_xdim, xdim, ydim);
					int[] resized_red = ResizeMapper.resize(channel_array[2], intermediate_xdim, xdim, ydim);
					int[] pixel = DeltaMapper.getPixel(resized_blue, resized_green, resized_red, xdim, pixel_shift);
					image.setRGB(0, 0, xdim, ydim, pixel, 0, xdim);
				}
			} 
			else if (set_id == 1)
			{
				if (pixel_quant == 0)
				{
					int[] green = DeltaMapper.getDifference(channel_array[1], channel_array[2]);
					int[] pixel = DeltaMapper.getPixel(channel_array[0], green, channel_array[1], xdim, pixel_shift);
					image.setRGB(0, 0, xdim, ydim, pixel, 0, xdim);
				} 
				else
				{
					int[] blue = channel_array[0];
					int[] green = DeltaMapper.getDifference(channel_array[1], channel_array[2]);
					int[] red = channel_array[1];

					int[] resized_blue = ResizeMapper.resize(blue, intermediate_xdim, xdim, ydim);
					int[] resized_green = ResizeMapper.resize(green, intermediate_xdim, xdim, ydim);
					int[] resized_red = ResizeMapper.resize(red, intermediate_xdim, xdim, ydim);
					int[] pixel = DeltaMapper.getPixel(resized_blue, resized_green, resized_red, xdim, pixel_shift);
					image.setRGB(0, 0, xdim, ydim, pixel, 0, xdim);
				}
			} 
			else if (set_id == 2)
			{
				if (pixel_quant == 0)
				{
					int[] green = DeltaMapper.getDifference(channel_array[0], channel_array[2]);
					int[] pixel = DeltaMapper.getPixel(channel_array[0], green, channel_array[1], xdim, pixel_shift);
					image.setRGB(0, 0, xdim, ydim, pixel, 0, xdim);
				} 
				else
				{
					int[] blue = channel_array[0];
					int[] green = DeltaMapper.getDifference(channel_array[0], channel_array[2]);
					int[] red = channel_array[1];

					int[] resized_blue = ResizeMapper.resize(blue, intermediate_xdim, xdim, ydim);
					int[] resized_green = ResizeMapper.resize(green, intermediate_xdim, xdim, ydim);
					int[] resized_red = ResizeMapper.resize(red, intermediate_xdim, xdim, ydim);
					int[] pixel = DeltaMapper.getPixel(resized_blue, resized_green, resized_red, xdim, pixel_shift);
					image.setRGB(0, 0, xdim, ydim, pixel, 0, xdim);
				}
			} 
			else if (set_id == 3)
			{
				if (pixel_quant == 0)
				{
					int[] green = DeltaMapper.getDifference(channel_array[0], channel_array[1]);
					int[] red = DeltaMapper.getSum(channel_array[2], green);
					int[] pixel = DeltaMapper.getPixel(channel_array[0], green, red, xdim, pixel_shift);
					image.setRGB(0, 0, xdim, ydim, pixel, 0, xdim);
				} 
				else
				{
					int[] blue = channel_array[0];
					int[] green = DeltaMapper.getDifference(channel_array[0], channel_array[1]);
					int[] red = DeltaMapper.getSum(channel_array[2], green);

					int[] resized_blue = ResizeMapper.resize(blue, intermediate_xdim, xdim, ydim);
					int[] resized_green = ResizeMapper.resize(green, intermediate_xdim, xdim, ydim);
					int[] resized_red = ResizeMapper.resize(red, intermediate_xdim, xdim, ydim);
					int[] pixel = DeltaMapper.getPixel(resized_blue, resized_green, resized_red, xdim, pixel_shift);
					image.setRGB(0, 0, xdim, ydim, pixel, 0, xdim);
				}
			} 
			else if (set_id == 4)
			{
				if (pixel_quant == 0)
				{
					int[] green = DeltaMapper.getDifference(channel_array[0], channel_array[1]);
					int[] red = DeltaMapper.getSum(channel_array[0], channel_array[2]);
					int[] pixel = DeltaMapper.getPixel(channel_array[0], green, red, xdim, pixel_shift);
					image.setRGB(0, 0, xdim, ydim, pixel, 0, xdim);
				} 
				else
				{
					int[] blue = channel_array[0];
					int[] green = DeltaMapper.getDifference(channel_array[0], channel_array[1]);
					int[] red = DeltaMapper.getSum(channel_array[0], channel_array[2]);

					int[] resized_blue = ResizeMapper.resize(blue, intermediate_xdim, xdim, ydim);
					int[] resized_green = ResizeMapper.resize(green, intermediate_xdim, xdim, ydim);
					int[] resized_red = ResizeMapper.resize(red, intermediate_xdim, xdim, ydim);
					int[] pixel = DeltaMapper.getPixel(resized_blue, resized_green, resized_red, xdim, pixel_shift);
					image.setRGB(0, 0, xdim, ydim, pixel, 0, xdim);
				}
			} 
			else if (set_id == 5)
			{
				if (pixel_quant == 0)
				{
					int[] blue = DeltaMapper.getSum(channel_array[2], channel_array[0]);
					int[] pixel = DeltaMapper.getPixel(blue, channel_array[0], channel_array[1], xdim, pixel_shift);
					image.setRGB(0, 0, xdim, ydim, pixel, 0, xdim);
				} 
				else
				{
					int[] blue = DeltaMapper.getSum(channel_array[2], channel_array[0]);
					int[] green = channel_array[0];
					int[] red = channel_array[1];

					int[] resized_blue = ResizeMapper.resize(blue, intermediate_xdim, xdim, ydim);
					int[] resized_green = ResizeMapper.resize(green, intermediate_xdim, xdim, ydim);
					int[] resized_red = ResizeMapper.resize(red, intermediate_xdim, xdim, ydim);
					int[] pixel = DeltaMapper.getPixel(resized_blue, resized_green, resized_red, xdim, pixel_shift);
					image.setRGB(0, 0, xdim, ydim, pixel, 0, xdim);
				}
			} 
			else if (set_id == 6)
			{
				if (pixel_quant == 0)
				{
					for (int i = 0; i < channel_array[2].length; i++)
						channel_array[2][i] = -channel_array[2][i];
					int[] green = DeltaMapper.getSum(channel_array[2], channel_array[0]);
					int[] blue = DeltaMapper.getSum(channel_array[1], green);
					int[] pixel = DeltaMapper.getPixel(blue, green, channel_array[0], xdim, pixel_shift);
					image.setRGB(0, 0, xdim, ydim, pixel, 0, xdim);
				} 
				else
				{
					for (int i = 0; i < channel_array[2].length; i++)
						channel_array[2][i] = -channel_array[2][i];
					int[] green = DeltaMapper.getSum(channel_array[2], channel_array[0]);
					int[] blue = DeltaMapper.getSum(channel_array[1], green);
					int[] red = channel_array[0];

					int[] resized_blue = ResizeMapper.resize(blue, intermediate_xdim, xdim, ydim);
					int[] resized_green = ResizeMapper.resize(green, intermediate_xdim, xdim, ydim);
					int[] resized_red = ResizeMapper.resize(red, intermediate_xdim, xdim, ydim);
					int[] pixel = DeltaMapper.getPixel(resized_blue, resized_green, resized_red, xdim, pixel_shift);
					image.setRGB(0, 0, xdim, ydim, pixel, 0, xdim);
				}
			} 
			else if (set_id == 7)
			{
				if (pixel_quant == 0)
				{
					int[] blue = DeltaMapper.getSum(channel_array[0], channel_array[1]);
					int[] red = DeltaMapper.getSum(channel_array[0], channel_array[2]);
					int[] pixel = DeltaMapper.getPixel(blue, channel_array[0], red, xdim, pixel_shift);
					image.setRGB(0, 0, xdim, ydim, pixel, 0, xdim);
				} 
				else
				{
					int[] blue = DeltaMapper.getSum(channel_array[0], channel_array[1]);
					int[] green = channel_array[0];
					int[] red = DeltaMapper.getSum(channel_array[0], channel_array[2]);

					int[] resized_blue = ResizeMapper.resize(blue, intermediate_xdim, xdim, ydim);
					int[] resized_green = ResizeMapper.resize(channel_array[0], intermediate_xdim, xdim, ydim);
					int[] resized_red = ResizeMapper.resize(red, intermediate_xdim, xdim, ydim);

					int[] pixel = DeltaMapper.getPixel(resized_blue, resized_green, resized_red, xdim, pixel_shift);
					image.setRGB(0, 0, xdim, ydim, pixel, 0, xdim);
				}
			} 
			else if (set_id == 8)
			{
				if (pixel_quant == 0)
				{
					int[] red = DeltaMapper.getSum(channel_array[0], channel_array[1]);
					int[] blue = DeltaMapper.getDifference(red, channel_array[2]);
					int[] pixel = DeltaMapper.getPixel(blue, channel_array[0], red, xdim, pixel_shift);
					image.setRGB(0, 0, xdim, ydim, pixel, 0, xdim);
				} 
				else
				{
					int[] red = DeltaMapper.getSum(channel_array[0], channel_array[1]);
					int[] blue = DeltaMapper.getDifference(red, channel_array[2]);

					int[] resized_blue = ResizeMapper.resize(blue, intermediate_xdim, xdim, ydim);
					int[] resized_green = ResizeMapper.resize(channel_array[0], intermediate_xdim, xdim, ydim);
					int[] resized_red = ResizeMapper.resize(red, intermediate_xdim, xdim, ydim);

					int[] pixel = DeltaMapper.getPixel(resized_blue, resized_green, resized_red, xdim, pixel_shift);
					image.setRGB(0, 0, xdim, ydim, pixel, 0, xdim);
				}
			} 
			else if (set_id == 9)
			{
				if (pixel_quant == 0)
				{
					int[] blue = DeltaMapper.getDifference(channel_array[0], channel_array[2]);
					int[] green = DeltaMapper.getDifference(channel_array[0], channel_array[1]);

					int[] pixel = DeltaMapper.getPixel(blue, green, channel_array[0], xdim, pixel_shift);
					image.setRGB(0, 0, xdim, ydim, pixel, 0, xdim);
				} 
				else
				{
					int[] blue = DeltaMapper.getDifference(channel_array[0], channel_array[2]);
					int[] green = DeltaMapper.getDifference(channel_array[0], channel_array[1]);
					int[] red = channel_array[0];

					int[] resized_blue = ResizeMapper.resize(blue, intermediate_xdim, xdim, ydim);
					int[] resized_green = ResizeMapper.resize(channel_array[0], intermediate_xdim, xdim, ydim);
					int[] resized_red = ResizeMapper.resize(red, intermediate_xdim, xdim, ydim);

					int[] pixel = DeltaMapper.getPixel(resized_blue, resized_green, resized_red, xdim, pixel_shift);
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
			frame.setVisible(true);
		} 
		catch (Exception e)
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
			int[] channel_id = DeltaMapper.getChannels(set_id);

			if (delta_type < 8)
			{
				byte[] string  = (byte[]) string_list.get(i);
				int[] table    = (int[]) table_list.get(i);
				int iterations = StringMapper.getIterations(string);
				int bitlength  = StringMapper.getBitlength(string);

				if (channel_iterations[i] != iterations)
					System.out.println(
							"Iterations appended to string does not agree with channel " + i + " information.");
				if (compressed_length[i] != bitlength)
					System.out.println(
							"Bit length appended to string does not agree with channel " + i + " information.");
                /*
				if (iterations < 16 && iterations != 0)
					string = StringMapper.decompressZeroStrings(string);
				else if (iterations > 16)
					string = StringMapper.decompressOneStrings(string);
				*/
				
				string =  StringMapper.decompressStrings2(string);

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
					current_channel = DeltaMapper.getValuesFromIdealDeltas2(delta, current_xdim, current_ydim, init[i], map);
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