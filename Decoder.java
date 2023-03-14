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

public class Decoder
{
	BufferedImage image;
	JMenuItem     apply_item;
	
	JDialog       shift_dialog;
	JTextField    shift_value;
	
	ImageCanvas   image_canvas;
	
	int    xdim, ydim;
	String filename;
	int    [] channel_type, set_sum, channel_sum;
	int    [] channel_min;
	double [] channel_rate, set_rate;
	String [] type_string;
	String [] set_string;
	String [] channel_string;
	
	int    pixel_shift = 3;
	long   file_length = 0;
	boolean initialized = false;
	
	public static void main(String[] args)
	{
		if (args.length != 1)
		{
			System.out.println("Usage: java Decoder <filename>");
			System.exit(0);
		}
	    String prefix      = new String("");
	   
		String filename     = new String(args[0]);
		String java_version = System.getProperty("java.version");
		String os           = System.getProperty("os.name");
		String os_version   = System.getProperty("os.version");
		String machine      = System.getProperty("os.arch");
		//System.out.println("Current java version is " + java_version);
		//System.out.println("Current os is " + os + " " + os_version + " on " + machine);
		//System.out.println("Image file is " + filename);
		Decoder decoder = new Decoder(prefix + filename);
	}

	public Decoder(String _filename)
	{
		filename = _filename;
		xdim = 640;
		ydim = 480;
		
		String [] channel_string = new String[6];
		channel_string[0] = new String("blue");
		channel_string[1] = new String("green");
		channel_string[2] = new String("red");
		channel_string[3] = new String("blue-green");
		channel_string[4] = new String("red-green");
		channel_string[5] = new String("red-blue");
		
		String [] compression_string = new String[7];
		compression_string[0] = new String("strings");
		compression_string[1] = new String("zipped strings");
		compression_string[2] = new String("compressed_strings");
		compression_string[3] = new String("zipped compressed strings");
		compression_string[4] = new String("zipped deltas");
		compression_string[5] = new String("zipped pixels");
		compression_string[6] = new String("mixed");
		
		Hashtable <Integer, int[]> image_table = new Hashtable<Integer, int[]>();
	
		int [] blue, green, red, blue_green, red_green, red_blue;
		
		// So the compiler doesn't complain about possible null assignments.
		blue       = new int[1];
	    green      = new int[1];
	    red        = new int[1];
        blue_green = new int[1];
        red_green  = new int[1]; 
        red_blue   = new int[1];
		try
		{
			File file              = new File(filename);
			try
			{
			    DataInputStream in = new DataInputStream(new FileInputStream(file));
			    xdim = in.readShort();
			    ydim = in.readShort();
			    System.out.println("Xdim from file is " + xdim + ", ydim is " + ydim);
			    
			    // Now we initialize them.
			    blue       = new int[xdim * ydim];
			    green      = new int[xdim * ydim];
			    red        = new int[xdim * ydim];
		        blue_green = new int[xdim * ydim];
		        red_green  = new int[xdim * ydim]; 
		        red_blue   = new int[xdim * ydim];
		        
		        
		        int [] delta = new int[xdim * ydim];
		        int  [] string_table = new int[1];
			    int [] key_table = new int[1];
			    int [] delta_key_table = new int[1];
			    
			    pixel_shift = in.readByte();
			    System.out.println("Pixel shift is " + pixel_shift);
			    System.out.println();
			    
			    for(int i = 0; i < 3; i++)
			    {
			        int channel = in.readByte();
			        System.out.println("Channel is " + channel_string[channel]);
			  
			        int channel_min = in.readInt();
			        int init_value = in.readInt();
			        int delta_min  = in.readInt();
			        //System.out.println("Init value is " + init_value + ", delta minimum is " + delta_min);
			    
			        int compression = in.readByte();
			        //System.out.println("Compression type is " + compression_string[compression]);
			        
			        byte compression_bit_type = in.readByte();
			    
			        int table_length = 0;
			    
			        table_length = in.readShort();
			        //System.out.println("String table length is " +  table_length);
			    
			        string_table = new int[table_length];
			        for(int j = 0; j < table_length; j++)
			        {
			    	    string_table[j] = in.readInt();
			        }
			        //System.out.println("Read string table.");
			   
			        table_length = in.readShort();
			        //System.out.println("Key table length is " +  table_length);
			    
			        key_table = new int[table_length];
			        for(int j = 0; j < table_length; j++)
			        {
			    	    key_table[j] = in.readInt();
			        }
			        System.out.println("Read key table.");
			        
			        int histogram_length = in.readInt();
			        
			        table_length = in.readShort();
			        //System.out.println("Key table length is " +  table_length);
			    
			        delta_key_table = new int[table_length];
			        for(int j = 0; j < table_length; j++)
			        {
			    	    delta_key_table[j] = in.readInt();
			        }
			        System.out.println("Read key table.");
			        
			        int delta_histogram_length = in.readInt();
			        
			        
			        int bitstring_length = 0;
			        int packed_length    = in.readInt();
			        int compressed_length = in.readInt();
			        //System.out.println("Bitstring length is " + bitstring_length);
			        
			        int data_length = in.readInt();
			    
			        byte [] data = new byte[data_length];
			        in.read(data, 0, data_length);
			    
			        if(compression == 0)
				    {
			        	bitstring_length = packed_length;
			        	
				        byte remainder = data[data.length - 1];
				        int remainder_length = (data.length - 1) * 8 - remainder;
				        
				    	System.out.println("Decompressing packed strings.");
				    	
				    	// We don't need to know the bitstring length to unpack delta strings
				    	// if we know the image dimensions.  Still good way to check if data
				    	// has been corrupted.
				    	if(bitstring_length != remainder_length)
				    		System.out.println("Length in header and remainder disagree.");
				    
					    int number_unpacked = DeltaMapper.unpackStrings2(data, string_table, delta);
					    if(number_unpacked != xdim * ydim)
					    	System.out.println("Number of values unpacked does not agree with image dimensions.");
					    
						for(int j = 1; j < delta.length; j++)
						     delta[j] += delta_min;
						
						int [] result = DeltaMapper.getValuesFromDeltas(delta, xdim , ydim, init_value);
						if(channel > 2)
							for(int j = 0; j < result.length; j++)
								result[j] += channel_min;
						
						image_table.put(channel, result);
						System.out.println("Putting data for " + channel_string[channel] + " in image table.");
				    }
			        else if(compression == 1)
				    {
			        	bitstring_length = packed_length;
				    	System.out.println("Decompressing zipped strings.");
				    	Inflater inflater = new Inflater();
					    inflater.setInput(data, 0, data_length);
					    byte [] string   = new byte[xdim * ydim * 8];
					    int string_length = 0;
					    try
					    {
					        string_length = inflater.inflate(string);
					    }
					    catch(Exception e)
					    {
					    	System.out.println(e.toString());
					    }
					    inflater.end();
					    
					    
				        byte remainder = string[string_length - 1];
				        int remainder_length = (string_length - 1) * 8 - remainder;
				        if(bitstring_length != remainder_length)
				    		System.out.println("Length in header and remainder disagree.");
					    
						int number_unpacked = DeltaMapper.unpackStrings2(string, string_table, delta);
						if(number_unpacked != xdim * ydim)
					    	System.out.println("Number of values unpacked does not agree with image dimensions.");
						
						for(int j = 1; j < delta.length; j++)
						     delta[j] += delta_min;
						
						int [] result = DeltaMapper.getValuesFromDeltas(delta, xdim , ydim, init_value);
						
						if(channel > 2)
							for(int j = 0; j < result.length; j++)
								result[j] += channel_min;
						System.out.println("Putting data for " + channel_string[channel] + " in image table.");
						image_table.put(channel, result);
						System.out.println();
						
				    }
			        else if(compression == 2)
				    {
			        	bitstring_length = compressed_length;
				    	System.out.println("Decompressing compressed strings.");
				        byte remainder = data[data.length - 1];
				        int remainder_length = (data.length - 1) * 8 - remainder;
				        if(bitstring_length != remainder_length)
				    		System.out.println("Length in header and remainder disagree.");
				        byte [] string = new byte[xdim * ydim * 8];
				        byte bit_type = DeltaMapper.checkStringType(data, bitstring_length);
				        if(bit_type != compression_bit_type)
				        {
				        	System.out.println("Bit type in header does not agree with bit type in data.");
				        }
				        int string_length = 0;
				        if(compression_bit_type == 0)
				        	string_length = DeltaMapper.decompressZeroStrings(data, bitstring_length - 1, string);	
				        else
				        	string_length = DeltaMapper.decompressOneStrings(data, bitstring_length - 1, string);
				        // The string length might include extra trailing bits, 0 to # of iterations.
				        // Not sure if any of the original bits get corrupted.  Doesn't seem to mess
				        // anything up, unless it has something to do with getting the bit type bit wrong.
				        if(string_length != packed_length)
				        {
				        	System.out.println("Length of string returned from recursion is " + string_length);
				        	System.out.println("Bit type is " + bit_type);
				        	System.out.println("Compression is " + compression);
				        	System.out.println("Original string length is " + packed_length);
				        }
						int number_unpacked = DeltaMapper.unpackStrings2(string, string_table, delta);
						if(number_unpacked != xdim * ydim)
					    	System.out.println("Number of values unpacked does not agree with image dimensions.");
						
						for(int j = 1; j < delta.length; j++)
						     delta[j] += delta_min;
						
						int [] result = DeltaMapper.getValuesFromDeltas(delta, xdim , ydim, init_value);
						
						if(channel > 2)
							for(int j = 0; j < result.length; j++)
								result[j] += channel_min;
						
						System.out.println("Putting data for " + channel_string[channel] + " in image table.");
						image_table.put(channel, result);
						System.out.println();
			        }
			        else if(compression == 3)
				    {
			        	bitstring_length = compressed_length;
			        	System.out.println("Decompressing zipped compressed strings.");
				    	Inflater inflater = new Inflater();
				    	inflater.setInput(data);
					    byte [] string   = new byte[xdim * ydim * 8];
					    byte [] decompressed_string = new byte[xdim * ydim * 8];
					    int string_length = 0;
					    try
					    {
					        string_length = inflater.inflate(string);
					        byte remainder = string[string_length - 1];
					        int remainder_length = (string_length - 1) * 8 - remainder;
					        if(bitstring_length != remainder_length)
					    		System.out.println("Length in header and remainder disagree.");
					        
					        byte bit_type = DeltaMapper.checkStringType(string, bitstring_length);
					        if(bit_type != compression_bit_type)
					        {
					        	System.out.println("Bit type in header disagrees with bit type in data.");
					        	System.out.println("Bit type in header is " + compression_bit_type);
					        	System.out.println("Bit type in data is " + bit_type);
					        }
					        if(compression_bit_type == 0)
					        	string_length = DeltaMapper.decompressZeroStrings(string, bitstring_length - 1, decompressed_string);	
					        else
					        	string_length = DeltaMapper.decompressOneStrings(string, bitstring_length - 1, decompressed_string);
					        
					        // The string length might include extra trailing bits, 0 to # of iterations.
					        // Not sure if any of the original bits get corrupted.  Doesn't seem to mess
					        // anything up.
					        if(string_length != packed_length)
					        {
					        	System.out.println("Length of string returned from recursion is " + string_length);
					        	System.out.println("Bit type is " + bit_type);
					        	System.out.println("Compression is " + compression);
					        	System.out.println("Original string length is " + packed_length);
					        }
					        int number_unpacked = DeltaMapper.unpackStrings2(decompressed_string, string_table, delta);
							if(number_unpacked != xdim * ydim)
						    	System.out.println("Number of values unpacked does not agree with image dimensions.");
							for(int j = 1; j < delta.length; j++)
							     delta[j] += delta_min;
							int [] result = DeltaMapper.getValuesFromDeltas(delta, xdim , ydim, init_value);
							if(channel > 2)
								for(int j = 0; j < result.length; j++)
									result[j] += channel_min;
							
							System.out.println("Putting data for " + channel_string[channel] + " in image table.");
							image_table.put(channel, result);
							System.out.println();
					    }
					    
					    catch(Exception e)
					    {
					    	System.out.println(e.toString());
					    }
					    inflater.end();
				    }
			        else if(compression == 4)
			        {
			        	System.out.println("Decompressing zipped deltas.");
			        	
			        	Inflater inflater = new Inflater();
				    	inflater.setInput(data);
				    	byte [] bytes  = new byte[xdim * ydim];
				    	int     length = 0;
					    try
					    {
					        length = inflater.inflate(bytes);
					        if(length != xdim * ydim)
					        	System.out.println("Length does not agree with image dimensions.");
					        else
					        {
					        	if(delta_histogram_length > 255)
					        	{
					        		System.out.println("Channel is " + channel_string[channel]);
					        		System.out.println("Remapping values.");
					        	    Hashtable <Integer,Integer> inverse_table = new Hashtable<Integer, Integer>();
					        	    for(int j = 0; j < delta_key_table.length; j++)
					        		    inverse_table.put(j, key_table[j]);
					        	    for(int j = 1; j < delta.length; j++)
					        	    {
					        		    int key = (int)bytes[j];
					        		    if(key < 0)
					        		    	key += 256;
					        		    delta[j] = inverse_table.get(key);
					        	    }	
					        	}
					        	else
					        	{
					        	    for(int j = 0; j < bytes.length; j++)
					        	    {
					        		    delta[j] = (int)bytes[j];
					        		    if(delta[j] < 0)
					        			    delta[j] += 256;
					        	    }
					        	}
					        }
					    }
					    catch(Exception e)
					    {
					    	System.out.println(e.toString());
					    }
					    inflater.end();
				    	
				    	for(int j = 1; j < delta.length; j++)
						     delta[j] += delta_min;
						int [] result = DeltaMapper.getValuesFromDeltas(delta, xdim , ydim, init_value);
						image_table.put(channel, result);
			        }
			        else if(compression == 5)
			        {
			        	System.out.println("Decompressing zipped pixels.");
			        	Inflater inflater = new Inflater();
				    	inflater.setInput(data);
					    
				    	int []  result = new int[xdim * ydim];
				    	byte [] bytes  = new byte[xdim * ydim];
				    	
                        int     length = 0;
					    try
					    {
					        length = inflater.inflate(bytes);
					        if(length != xdim * ydim)
					        	System.out.println("Length does not agree with image dimensions.");
					        else
					        {
					        	for(int j = 0; j < bytes.length; j++)
					        	{
					        		result[j] = (int)bytes[j];
					        		if(result[j] < 0)
					        			result[j] += 256;
					        	}
					        	
					        	int [] final_result = new int[result.length];
					        	for(int j = 0; j < result.length; j++)
					        	{
					        	    final_result[j] = result[j];	
					        	}
					        	
					        	if(histogram_length  > 256)
					        	{
					        		System.out.println("Channel is " + channel_string[channel]);
					        		System.out.println("Remapping values.");
					        	    Hashtable <Integer,Integer> inverse_table = new Hashtable<Integer, Integer>();
					        	    for(int j = 0; j < key_table.length; j++)
					        		    inverse_table.put(j, key_table[j]);
					        	    for(int j = 0; j < result.length; j++)
					        	    {
					        		    int key = result[j];
					        		    final_result[j] = inverse_table.get(key);
					        	    }
					        	}
					        	if(channel > 2)
					        	{
					        		System.out.println("Channel min is " + channel_min);
					        		System.out.println();
					        		for(int j = 0; j < result.length; j++)
					        			final_result[j] += channel_min;
					        	}
					        	image_table.put(channel, final_result);
					        }
					    }
					    catch(Exception e)
					    {
					    	System.out.println(e.toString());
					    }
					    inflater.end();
			        }
			    }
			    in.close();
			    
			    // Figure out what channels we have.
			    if(image_table.containsKey(0))
			    {
			        blue = image_table.get(0);	
			        if(image_table.containsKey(1))
			        {
			        	green = image_table.get(1);
			        	if(image_table.containsKey(2))
			        	{
			        		// Have all the channels we need.
			        		red = image_table.get(2);
			        	}
			        	else
			        	{
			        	    if(image_table.contains(4))
			        	    {
			        	        red_green = image_table.get(4);	
			        	        red       = DeltaMapper.getSum(red_green, green);
			        	    }
			        	    else if(image_table.contains(5))
			        	    {
			        	    	red_blue = image_table.get(5);	
			        	        red      = DeltaMapper.getSum(red_blue, blue);	
			        	    }
			        	    else
			        	        System.out.println("Table does not contain complete set.");
			        	}	
			        }
			        else
			        {
			        	if(image_table.containsKey(2))
				        {
			        		// We have a blue and red channel, we need a green channel.
			        		red = image_table.get(2);	
			        		if(image_table.containsKey(3))
			        		{
			        		    blue_green = image_table.get(3);
			        		    for(int i = 0; i < blue_green.length; i++)
			        		    	blue_green[i] = - blue_green[i];
			        		    green = DeltaMapper.getSum(blue_green, blue);
			        		}
			        		else if(image_table.containsKey(4))
			        		{
			        			red_green = image_table.get(4);
			        		    for(int i = 0; i < red_green.length; i++)
			        		    	red_green[i] = - red_green[i];
			        		    green = DeltaMapper.getSum(red_green, red);    	
			        		}
			        		else
			        		{
			        			System.out.println("Image table does not contain complete set.");
			        			for(int i = 0; i < 6; i++)
			        			{
			        				if(image_table.containsKey(i))
			        				{
			        					System.out.println("Image table contains " + channel_string[i]);
			        				}
			        			}
			        			System.out.println();	
			        		}
				        }
			        	else
			        	{
			        		// Blue, but no red or green channel.
			        		if(image_table.containsKey(3) && image_table.containsKey(5))
			        		{
			        		    blue_green = image_table.get(3);
			        		    for(int i = 0; i < blue_green.length; i++)
			        		    	blue_green[i] = -blue_green[i];
			        		    green = DeltaMapper.getSum(blue_green, blue); 
			        		    red_blue = image_table.get(5);
			        		    
			        		    red   = DeltaMapper.getSum(red_blue, blue); 
			        		    for(int i = 0; i < red.length; i++)
			        		    	if(red[i] > 255)
			        		    	{
			        		    		red[i] = 255;
			        		    		System.out.println("Value out of bounds.");
			        		    	}
			        		    
			        		}
			        		else if(image_table.containsKey(4)&& image_table.containsKey(5) )
			        		{
			        		    red_blue = image_table.get(5);
			        		    red = DeltaMapper.getSum(red_blue, blue);
			        		    red_green = image_table.get(4);
			        		    for(int i = 0; i < red_green.length; i++)
			        		    	red_green[i] = -red_green[i];
			        		    green = DeltaMapper.getSum(red_green, red);
			        		}
			        		else
			        		{
			        			System.out.println("Image table does not contain complete set.");
			        			for(int i = 0; i < 6; i++)
			        			{
			        				if(image_table.containsKey(i))
			        				{
			        					System.out.println("Image table contains " + channel_string[i]);
			        				}
			        			}
			        			System.out.println();
			        		}
			        	}
			        }
			    }
			    else if(image_table.containsKey(1))
			    {
			    	// We have the green channel but not the blue channel.
			        green = image_table.get(1);
			        if(image_table.containsKey(2))
			        {
			        	red = image_table.get(2);	
			        	if(image_table.containsKey(3))
			        	{
			        	    blue_green = image_table.get(3);
			        	    blue       = DeltaMapper.getSum(blue_green, green);
			        	    for(int i = 0; i < blue.length; i++)
		        		    	if(blue[i] > 255)
		        		    	{
		        		    		System.out.println("Value out of bounds: " + blue[i]);
		        		    		blue[i] = 255;
		        		    	}
			        	}
			        	else if(image_table.containsKey(5))
			        	{
			        		red_blue = image_table.get(5);
			        		for(int j = 0; j < red_blue.length; j++)
				            	red_blue[j] = -red_blue[j];
			        	    blue = DeltaMapper.getSum(red_blue, red);  
			        	}
			        	else
		        	        System.out.println("Table does not contain complete set.");
			        }
			        else
			        {
			        	// We have the green channel, but not blue or red.
			        	if(image_table.containsKey(3) && image_table.containsKey(4))
			        	{
			        		for(int i = 0; i < green.length; i++)
			        		{
			        			if(green[i] > 255)
			        				System.out.println("Green value too large.");
			        			else if(green[i] < 0)
			        			{
			        				System.out.println("Green value negative.");
			        			}
			        		}
			        	    blue_green = image_table.get(3);
			        	    blue       = DeltaMapper.getSum(blue_green, green);
			        	    red_green  = image_table.get(4);
			        	    red        = DeltaMapper.getSum(red_green, green);     
			        	}
			        	else if(image_table.containsKey(4) && image_table.containsKey(5))
			        	{
			        	    red_green = image_table.get(4);
			        	    red  = DeltaMapper.getSum(red_green, green);
			        	    red_blue  = image_table.get(5);
			        	    for(int j = 0; j < red_blue.length; j++)
				            	red_blue[j] = -red_blue[j];
			        	    blue = DeltaMapper.getSum(red_blue, red);
			        	   
			        	}
			        	else
			        		System.out.println("Table does not contain complete set.");	
			        }
			    }
			    else if(image_table.containsKey(2))
			    {
			        // We have the red channel but not blue or green.
			        red = image_table.get(2);	
			        if(image_table.containsKey(3) && image_table.containsKey(4))
			        {
			            red_green = image_table.get(4);
			            for(int j = 0; j < red_green.length; j++)
			            	red_green[j] = -red_green[j];
			            green      = DeltaMapper.getSum(red_green, red);
			            blue_green = 	image_table.get(3);
			            blue       = DeltaMapper.getSum(blue_green, green);
			        }
			        else if(image_table.containsKey(4) && image_table.containsKey(5))
			        {
			            red_blue = image_table.get(5);	
			            for(int j = 0; j < red_blue.length; j++)
			            	red_blue[j] = -red_blue[j];
			            blue     = DeltaMapper.getSum(red_blue, red);
			            
			            red_green = image_table.get(4);
			            for(int j = 0; j < red_green.length; j++)
			            	red_green[j] = -red_green[j];
			            green      = DeltaMapper.getSum(red_green, red);
			        }
			        else
	        	        System.out.println("Table does not contain complete set.");
			    }
			}
			catch(Exception e)
			{
				System.out.println(e.toString());
			}
			
			for(int i = 0; i < red.length; i++)
			{
			    if(red[i] < 0)
			    {
			    	//System.out.println("Negative value: " + red[i]);
			    }
			    else if(red[i] > 255)
			    	System.out.println("Value out of bounds.");
			}
			image = new BufferedImage(xdim, ydim, BufferedImage.TYPE_INT_RGB);
			for(int i = 0; i < ydim; i++)
			{
			    for(int j = 0; j < xdim; j++)
			    {
			    	int _blue = blue[i * xdim + j];
			    	_blue <<= pixel_shift;
			    	
			    	int _green = green[i * xdim + j];
			    	_green <<= pixel_shift;
			    
			    	int _red   = red[i * xdim + j]; 
			    	_red <<= pixel_shift;
			    
			    	int pixel = 0;
			    	pixel |= _blue << 16;
	                pixel |= _green << 8;    
	                pixel |= _red;	
	                
			    	image.setRGB(j, i, pixel);
			    }
			}

			JFrame frame = new JFrame("Decoder");
		    WindowAdapter window_handler = new WindowAdapter()
			{
			    public void windowClosing(WindowEvent event)
			    {
			        System.exit(0);
			    }
			};
			frame.addWindowListener(window_handler);
			    
			image_canvas = new ImageCanvas();
			image_canvas.setSize(xdim, ydim);
			frame.getContentPane().add(image_canvas, BorderLayout.CENTER);
			
				
			frame.pack();
			frame.setLocation(400, 200);
			frame.setVisible(true);
		} 
		catch (Exception e)
		{
			System.out.println(e.getMessage());
			System.exit(1);
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