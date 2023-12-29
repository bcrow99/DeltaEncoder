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

public class DeltaReader
{
	BufferedImage image;
	ImageCanvas   image_canvas;
	
	public static void main(String[] args)
	{
		if (args.length != 1)
		{
			System.out.println("Usage: java DeltaReader <filename>");
			System.exit(0);
		}
		
	    String prefix      = new String("");
		String filename     = new String(args[0]);
		DeltaReader Decoder2 = new DeltaReader(prefix + filename);
	}
	
	public DeltaReader(String filename)
	{
		String [] channel_string = new String[6];
		channel_string[0] = new String("blue");
		channel_string[1] = new String("green");
		channel_string[2] = new String("red");
		channel_string[3] = new String("blue-green");
		channel_string[4] = new String("red-green");
		channel_string[5] = new String("red-blue");
		
        int xdim        = 0;
		int ydim        = 0;
		int pixel_shift = 0;
		int pixel_quant = 0;
		int set_id      = 0;
		
        
		try
		{
			File file              = new File(filename);
			DataInputStream in = new DataInputStream(new FileInputStream(file));
			
			
			
			xdim = in.readShort();
			ydim = in.readShort();
			
			System.out.println("Read short xdim " + xdim);
			System.out.println("Read short ydim " + ydim);
			       
	        pixel_shift = in.readByte();
		    System.out.println("Read byte pixel shift " + pixel_shift);
		   
		    pixel_quant = in.readByte();
		    System.out.println("Read byte pixel quant " + pixel_quant);
		   
		    set_id = in.readByte();
		    System.out.println("Read byte set id " + set_id);
		    System.out.println();
		   
		    int  channel_id[] = DeltaMapper.getChannels(set_id);
		    int  min[]        = new int[3];
		    int  init[]       = new int[3];
		    int  delta_min[]  = new int[3];
		    byte type[]       = new byte[3];
		    byte compressed[] = new byte[3];
		    int  length[]     = new int[3];
		    int  compressed_length[] = new int[3];
		    
		    ArrayList string_list = new ArrayList();
		    ArrayList table_list  = new ArrayList();
		    
		    for(int i = 0; i < 3; i++)
		    {
		    	int j = channel_id[i];
		    	System.out.println("Reading data for channel " + channel_string[j]);
		    	min[i] = in.readInt();
		    	System.out.println("Read int channel min " + min[i]);
		    	init[i] = in.readInt();
		    	System.out.println("Read int channel init " + init[i]);
		    	delta_min[i] = in.readInt();
		    	System.out.println("Read int channel delta min " + delta_min[i]);
		    	type[i] = in.readByte();
		    	System.out.println("Read channel string type " + type[i]);
		    	compressed[i] = in.readByte();
		    	System.out.println("Read if the string was compressed " + compressed[i]);
		    	length[i] = in.readInt();
		    	System.out.println("Read bit length of string " + length[i]);
		    	if(compressed[i] != 0)
		    	{
		    		compressed_length[i] = in.readInt();
		    		System.out.println("Read bit length of compressed string " + compressed_length[i]);
		    	}
		    	
				int table_length = in.readShort();
				System.out.println("Read short table length " +  table_length);
				    
				int [] table = new int[table_length];
				for(int k = 0; k < table_length; k++)
				    	table[k] = in.readInt();
				System.out.println("Read ints string table.");
				
				table_list.add(table);
				
				int string_length = in.readInt();
				System.out.println("Read short string byte length " +  string_length);
                byte [] string    = new byte[string_length];
			    in.read(string, 0, string_length);
			    string_list.add(string);
		    	
		    	System.out.println();
		    }
		    
		    ArrayList channel_list = new ArrayList();
		    
		    for(int i = 0; i < 3; i++)
		    {
		    	byte [] string = (byte [])string_list.get(i);
		    	
		    	if(compressed[i] != 0)
		    	{
		    		byte [] decompressed_string = new byte[xdim * ydim];
		    		int decompressed_length = 0;
		    	    if(type[i] == 0)
		    	        decompressed_length = DeltaMapper.decompressZeroStrings(string, compressed_length[i], decompressed_string);
		    	    else
		    	        decompressed_length = DeltaMapper.decompressOneStrings(string, compressed_length[i], decompressed_string);
		    	    if(decompressed_length != length[i])
	    	        	System.out.println("Length returned from decompress was different from expected length.");
		    	    
		    	    // Not really necessary, but we'll clip the decompressed string.
		    	    int byte_length = decompressed_length / 8;
		    	    if(decompressed_length % 8 != 0)
		    	        byte_length++;
		    	    string = new byte[byte_length];
		    	    for(int j = 0; j < byte_length; j++)
		    	        string[j] = decompressed_string[j];
		    	}
		    	
		    	int [] table = (int [])table_list.get(i);
		    	if(pixel_quant == 0)
		    	{
		    		int [] delta = new int[xdim * ydim];
		    	    int number_unpacked = DeltaMapper.unpackStrings2(string, table, delta);
				    for(int j = 1; j < delta.length; j++)
				       delta[j] += delta_min[i];
				    int [] current_channel = DeltaMapper.getValuesFromDeltas3(delta, xdim , ydim, init[i]);
				    if(channel_id[i] > 2)
				       for(int j = 0; j < current_channel.length; j++)
							current_channel[j] += min[i];
					channel_list.add(current_channel);
		    	}
		    	else
		    	{
		    		double factor = pixel_quant;
			        factor       /= 10;
			        int _xdim = xdim - (int)(factor * (xdim / 2 - 2));
			        int _ydim = ydim - (int)(factor * (ydim / 2 - 2));
			        int [] delta = new int[_xdim * _ydim];
			        int number_unpacked = DeltaMapper.unpackStrings2(string, table, delta);
				    for(int j = 1; j < delta.length; j++)
				       delta[j] += delta_min[i];
				    int [] current_channel = DeltaMapper.getValuesFromDeltas3(delta, _xdim , _ydim, init[i]);
				    if(channel_id[i] > 2)
				       for(int j = 0; j < current_channel.length; j++)
							current_channel[j] += min[i];
				    int [] resized_channel = ResizeMapper.resize(current_channel, _xdim, xdim, ydim);
				    channel_list.add(resized_channel);
		    	}
		    }
		    
		    
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
			
			JFrame frame = new JFrame("DeltaReader");
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