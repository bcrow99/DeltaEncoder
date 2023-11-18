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

public class AdaptiveDecoder2
{
	BufferedImage image;
	ImageCanvas   image_canvas;
	
	public static void main(String[] args)
	{
		if (args.length != 1)
		{
			System.out.println("Usage: java AdaptiveDecoder2 <filename>");
			System.exit(0);
		}
		
	    String prefix      = new String("");
		String filename     = new String(args[0]);
		AdaptiveDecoder2 Decoder2 = new AdaptiveDecoder2(prefix + filename);
	}
	
	public AdaptiveDecoder2(String filename)
	{
		ArrayList channel_list = new ArrayList();
		
		String [] channel_string = new String[6];
		channel_string[0] = new String("blue");
		channel_string[1] = new String("green");
		channel_string[2] = new String("red");
		channel_string[3] = new String("blue-green");
		channel_string[4] = new String("red-green");
		channel_string[5] = new String("red-blue");
		
        int xdim = 0;
		int ydim = 0;
		int pixel_shift = 0;
		int [] blue, green, red, blue_green, red_green, red_blue;
		blue       = new int[1];
	    green      = new int[1];
	    red        = new int[1];
        blue_green = new int[1];
        red_green  = new int[1]; 
        red_blue   = new int[1];
        
		try
		{
			File file              = new File(filename);
			DataInputStream in = new DataInputStream(new FileInputStream(file));
			
			byte set_id = in.readByte();
			//System.out.println("Read byte for set id for " + set_id);
			
			xdim = in.readShort();
			ydim = in.readShort();
			
			System.out.println("Read short xdim " + xdim);
			System.out.println("Read short ydim " + ydim);
			
			blue       = new int[xdim * ydim];
			green      = new int[xdim * ydim];
			red        = new int[xdim * ydim];
		    blue_green = new int[xdim * ydim];
		    red_green  = new int[xdim * ydim]; 
		    red_blue   = new int[xdim * ydim];
		        
		        
		    int [] delta = new int[xdim * ydim];
		    int  [] string_table = new int[1];
			    
			    
	       pixel_shift = in.readByte();
		   System.out.println("Read byte pixel shift " + pixel_shift);
		   //System.out.println();
			    
		   for(int i = 0; i < 3; i++)
		   {
			   int channel = in.readByte();
			   //System.out.println("Read byte channel id " + channel_string[channel]);
			  
			   int channel_min = in.readInt();
			   int init_value = in.readInt();
			   int delta_min  = in.readInt();
			   System.out.println("Read in channel min " + channel_min);
			   System.out.println("Read int init value " + init_value);
			   System.out.println("Read int delta minimum is " + delta_min); 
			   int table_length = 0;
			    
			   table_length = in.readShort();
			   System.out.println("Read short string table length " +  table_length);
			    
			   string_table = new int[table_length];
			   for(int j = 0; j < table_length; j++)
			    	string_table[j] = in.readInt();
			   System.out.println("Read ints string table.");
			   
			   // Length of the concatenated segments.
			   int channel_bit_length = in.readInt();
			   System.out.println("Read int bit length of concatenated strings is " + channel_bit_length);
			   
			   // Number of segments
			   int n = in.readInt(); 
			   System.out.println("Read int number of segments " + n );
			   
			   int max_segment_length = in.readInt();
			   System.out.println("Read int max segment byte length " + max_segment_length);
			   
			   int byte_length = channel_bit_length / 8;
			   if(channel_bit_length % 8 != 0)
				   byte_length++;
			   
			   byte [] string = new byte[byte_length * 2];
			   int     offset = 0;
			   
			   for(int j = 0; j < n; j++)
			   {
			       short extra_bits = 0;
			       int segment_byte_length = 0;
				   if(max_segment_length < 8192)
				   {
					   System.out.println("Byte length was packed.");
				       short packed_segment_length = in.readShort();
				       extra_bits = (short)(packed_segment_length & 0x0007);
				       segment_byte_length = (packed_segment_length >> 3); 
				   }
				   else if(max_segment_length < Short.MAX_VALUE)
				   {
					   segment_byte_length = in.readShort();
					   extra_bits          = in.readByte();
				   }
				   else
				   {
					   segment_byte_length = in.readInt();
					   extra_bits          = in.readByte();   
				   }
				   
				   System.out.println("Extra bits is " + extra_bits);
				   System.out.println("Segment byte length is " + segment_byte_length);
				 
				   byte [] segment          = new byte[segment_byte_length];
			       in.read(segment, 0, segment_byte_length);
			       System.out.println("Read bytes segment data.");
			       
			       int segment_bit_length = (segment.length - 1) * 8 - extra_bits;
			       int iterations  = (int)segment[segment_byte_length - 1];
			       int string_type = 0;
			       if(iterations < 0)
			       {
			    	   iterations  = -iterations;
			    	   string_type = 1;
			       }
			       if(iterations == 0)
			       {
			    	   for(int k = 0; k < segment.length - 1; k++) 
			    	       string[offset + k] = segment[k];
			    	   offset += segment.length - 1;
			       }
			       else
			       {
			    	   int decompression_length = 0;
			    	   byte [] decompressed_segment = new byte[max_segment_length];
			    	 
			    	   if(string_type == 0)
			    		   decompression_length = DeltaMapper.decompressZeroStrings(segment, segment_bit_length, decompressed_segment);   
			    	   else
			    		   decompression_length = DeltaMapper.decompressOneStrings(segment, segment_bit_length, decompressed_segment);
			    	   
			    	   byte_length = decompression_length / 8;
			    	   if(decompression_length % 8 != 0)
			    	   {
			    		   if(j == n - 1)
			    			   byte_length++;
			    		   else
			    		   {
			    			   System.out.println("Uneven segment at index " + j);
			    			   System.out.println("Iterations is " + iterations);
			    			   System.out.println("String type is " + string_type);
			    			   System.out.println();
			    		   }
			    	   }
			    	   
			    	   System.arraycopy(decompressed_segment, 0, string, offset, byte_length);
			    	   offset += byte_length; 
			       }
			    }
			   
			    int number_unpacked = DeltaMapper.unpackStrings2(string, string_table, delta);
			    for(int j = 1; j < delta.length; j++)
			       delta[j] += delta_min;
			    int [] current_channel = DeltaMapper.getValuesFromDeltas3(delta, xdim , ydim, init_value);
			    if(channel > 2)
			       for(int j = 0; j < current_channel.length; j++)
						current_channel[j] += channel_min;
				channel_list.add(current_channel);
				System.out.println();
			} 
		    in.close();
		    
		   
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
			    red_green = (int [])channel_list.get(2);
			    green     = DeltaMapper.getDifference(red, red_green);
			}
			else if(set_id == 2)
			{ 
				blue       = (int [])channel_list.get(0);
			    red        = (int [])channel_list.get(1);
			    blue_green = (int [])channel_list.get(2);
			    green      = DeltaMapper.getDifference(blue, blue_green);
			}
			else if(set_id == 3)
			{ 
				blue       = (int [])channel_list.get(0);
				blue_green = (int [])channel_list.get(1);
				green      = DeltaMapper.getDifference(blue, blue_green);
				red_green  = (int [])channel_list.get(2);
				red = DeltaMapper.getSum(red_green, green);
			}
			else if(set_id == 4)
			{ 
				blue       = (int [])channel_list.get(0);
				blue_green = (int [])channel_list.get(1);
				green      = DeltaMapper.getDifference(blue, blue_green);
			    red_blue   = (int [])channel_list.get(2);
			    red        = DeltaMapper.getSum(blue, red_blue);
			}
			else if(set_id == 5)
			{
				green   = (int [])channel_list.get(0);
				red     = (int [])channel_list.get(1);
				blue_green = (int [])channel_list.get(2);
				blue = DeltaMapper.getSum(blue_green, green);
			}
			else if(set_id == 6)
			{
				red     = (int [])channel_list.get(0);
				blue_green = (int [])channel_list.get(1);
			    red_green = (int [])channel_list.get(2);
			    for(int i = 0; i < red_green.length; i++)
			    	red_green[i] = -red_green[i];
			    green = DeltaMapper.getSum(red_green, red);
			    blue = DeltaMapper.getSum(blue_green, green);	
			}
			else if(set_id == 7)
			{
				green   = (int [])channel_list.get(0);
				blue_green = (int [])channel_list.get(1);
			    blue = DeltaMapper.getSum(green, blue_green);
			    red_green = (int [])channel_list.get(2);
			    red  = DeltaMapper.getSum(green, red_green);
			}
			else if(set_id == 8)
			{
				green     = (int [])channel_list.get(0);
			    red_green = (int [])channel_list.get(1);
			    red       = DeltaMapper.getSum(green, red_green);
			    red_blue  = (int [])channel_list.get(1);
			    blue      = DeltaMapper.getDifference(red, red_blue);
			}
			else if(set_id == 9)
			{
				red       = (int [])channel_list.get(0);
			    red_green = (int [])channel_list.get(1);
			    green     = DeltaMapper.getDifference(red, red_green); 
			    red_blue  = (int [])channel_list.get(2);
			    blue      = DeltaMapper.getDifference(red, red_blue);
			}
		}
		catch(Exception e)
		{
			System.out.println(e.toString());
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
		
		JFrame frame = new JFrame("AdaptiveDecoder2");
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
	    
	class ImageCanvas extends Canvas
    {
        public synchronized void paint(Graphics g)
        {
            g.drawImage(image, 0, 0, this);
        }
    }   
}