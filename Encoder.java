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

public class Encoder
{
	BufferedImage original_image;
	BufferedImage image;
	JMenuItem     apply_item;
	
	JDialog       shift_dialog;
	JTextField    shift_value;
	
	ImageCanvas   image_canvas;
	
	int    xdim, ydim;
	String filename;
	
	int [] original_pixel;
	int [] alpha;
	int [] red;
	int [] green;
	int [] blue;
	
	byte   [] delta_bytes, delta_strings, zipped_strings; 
	byte   [] compressed_strings, zipped_compressed_strings;
	byte   [] channel_type;
	int    [] channel_init, channel_min;
	int    [] set_sum, channel_sum, channel_length, channel_delta_min;
	double [] set_rate, channel_rate;
	String [] type_string;
	String [] set_string;
	String [] channel_string;
	int    [] shifted_blue, shifted_green, shifted_red;
	int    [] shifted_blue_green, shifted_red_green, shifted_red_blue;
	
	ArrayList channel_table, channel_data;
	
	int green_init;
	int green_sum;
	int green_delta_min;
	int [] green_table;

	int    green_string_length;
	int    green_compressed_string_length;
	ArrayList green_data;
	byte   green_type;
	
	int    pixel_shift = 3;
	long   file_length = 0;
	boolean initialized = false;
	
	public static void main(String[] args)
	{
		if (args.length != 1)
		{
			System.out.println("Usage: java Encoder <filename>");
			System.exit(0);
		}
	    //String prefix      = new String("");
	    String prefix       = new String("C:/Users/Brian Crowley/Desktop/");
		String filename     = new String(args[0]);
		String java_version = System.getProperty("java.version");
		String os           = System.getProperty("os.name");
		String os_version   = System.getProperty("os.version");
		String machine      = System.getProperty("os.arch");
		//System.out.println("Current java version is " + java_version);
		//System.out.println("Current os is " + os + " " + os_version + " on " + machine);
		//System.out.println("Image file is " + filename);
		Encoder encoder = new Encoder(prefix + filename);
	}

	public Encoder(String _filename)
	{
		filename = _filename;
		try
		{
			File file              = new File(filename);
			file_length            = file.length();
			original_image         = ImageIO.read(file);
			int raster_type        = original_image.getType();
			ColorModel color_model = original_image.getColorModel();
			int number_of_channels = color_model.getNumColorComponents();
			int number_of_bits     = color_model.getPixelSize();
			xdim                   = original_image.getWidth();
			ydim                   = original_image.getHeight();
		    original_pixel         = new int[xdim * ydim];
		    alpha                  = new int[xdim * ydim];
			blue                   = new int[xdim * ydim];
		    green                  = new int[xdim * ydim];
		    red                    = new int[xdim * ydim];
		    
			if(raster_type == BufferedImage.TYPE_3BYTE_BGR)
			{
				int[]          pixel = new int[xdim * ydim];
				PixelGrabber pixel_grabber = new PixelGrabber(original_image, 0, 0, xdim, ydim, original_pixel, 0, xdim);
		        try
		        {
		            pixel_grabber.grabPixels();
		        }
		        catch(InterruptedException e)
		        {
		            System.err.println(e.toString());
		        }
		        if((pixel_grabber.getStatus() & ImageObserver.ABORT) != 0)
		        {
		            System.err.println("Error grabbing pixels.");
		            System.exit(1);
		        }
		        
		        for(int i = 0; i < xdim * ydim; i++)
				{
			        alpha[i] = (original_pixel[i] >> 24) & 0xff;
				    blue[i]  = (original_pixel[i] >> 16) & 0xff;
				    green[i] = (original_pixel[i] >> 8) & 0xff; 
		            red[i]   = original_pixel[i] & 0xff; 
				}
			    image = new BufferedImage(xdim, ydim, BufferedImage.TYPE_INT_RGB);
			   
			    for(int i = 0; i < xdim; i++)
			    {
			    	for(int j = 0; j < ydim; j++)
			    	{
			    		image.setRGB(i, j, original_pixel[j * xdim + i]);
			    	}
			    }
               
			    JFrame frame = new JFrame("Encoder");
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
				
				JMenuBar menu_bar = new JMenuBar();

				JMenu     file_menu  = new JMenu("File");
				
				apply_item                 = new JMenuItem("Apply");
				ApplyHandler apply_handler = new ApplyHandler();
				apply_item.addActionListener(apply_handler);
				file_menu.add(apply_item);
				
				JMenuItem reload_item        = new JMenuItem("Reload");
				ReloadHandler reload_handler = new ReloadHandler();
				reload_item.addActionListener(reload_handler);
				file_menu.add(reload_item);
				
				JMenuItem save_item      = new JMenuItem("Save");
				SaveHandler save_handler = new SaveHandler();
				save_item.addActionListener(save_handler);
				file_menu.add(save_item);
				
				
				JMenu settings_menu = new JMenu("Settings");
				
				JMenuItem shift_item = new JMenuItem("Pixel Shift");
				ActionListener shift_handler = new ActionListener()
				{
					public void actionPerformed(ActionEvent e)
					{
						Point location_point = frame.getLocation();
						int x = (int) location_point.getX();
						int y = (int) location_point.getY();
						x += xdim;
						shift_dialog.setLocation(x, y);
						shift_dialog.pack();
						shift_dialog.setVisible(true);
					}
				};
				shift_item.addActionListener(shift_handler);
				shift_dialog = new JDialog(frame, "Pixel Shift");
				JPanel shift_panel = new JPanel(new BorderLayout());
				JSlider shift_slider = new JSlider();
				shift_slider.setMinimum(0);
				shift_slider.setMaximum(7);
				shift_slider.setValue(pixel_shift);
				shift_value = new JTextField(3);
				shift_value.setText(" " + pixel_shift + " ");
				ChangeListener shift_slider_handler = new ChangeListener()
				{
					public void stateChanged(ChangeEvent e)
					{
						JSlider slider = (JSlider) e.getSource();
						pixel_shift = slider.getValue();
						shift_value.setText(" " + pixel_shift + " ");
						if(slider.getValueIsAdjusting() == false)
						{
							apply_item.doClick();
						}
					}
				};
				shift_slider.addChangeListener(shift_slider_handler);
				shift_panel.add(shift_slider, BorderLayout.CENTER);
				shift_panel.add(shift_value, BorderLayout.EAST);
				shift_dialog.add(shift_panel);
				settings_menu.add(shift_item);
				
				
				menu_bar.add(file_menu);
				menu_bar.add(settings_menu);
				
				frame.setJMenuBar(menu_bar);
				
				frame.pack();
				frame.setLocation(400, 200);
				frame.setVisible(true);
			} 
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
	
	class ApplyHandler implements ActionListener
	{
		int    [] new_alpha, new_blue, new_green, new_red, new_pixel;
		int       pixel_length;
		double    file_ratio;
		
		public ApplyHandler()
		{
			new_alpha    = new int[xdim * ydim]; 
		    new_blue     = new int[xdim * ydim];
		    shifted_blue = new int[xdim * ydim];
		     
		    new_green    = new int[xdim * ydim];
		    shifted_green = new int[xdim * ydim];
		     
		    new_red     = new int[xdim * ydim];
		    shifted_red = new int[xdim * ydim];
		    new_pixel   = new int[xdim * ydim];
		    
		    // Allocate extra memory to allow for expansion as well as compression.
		    delta_strings             = new byte[xdim * ydim * 2];
		    zipped_strings            = new byte[xdim * ydim * 8];
		    compressed_strings        = new byte[xdim * ydim * 8];
		    zipped_compressed_strings = new byte[xdim * ydim * 8];
		    
		    // The 4 different kinds of compressions we evaluate per channel.
		    // Huffman encoding usually offers some amount of compression unless
		    // the blocks are so small the extra overhead becomes significant.
		    // We're using png files to get a ratio for simple zip encoding, another alternative.
		    type_string    = new String[5];
			type_string[0] = new String("packed strings");
			type_string[1] = new String("zipped packed strings");
			type_string[2] = new String("compressed strings");
			type_string[3] = new String("zipped compressed strings");
			
			// If a frame is divided into blocks, there 
			// might be multiple types of compression.
			type_string[4] = new String("mixed");
		  
		    // The delta sum for each channel.
		    channel_sum  = new int[6];
		    
		    // The different minimum compression rates per channel.
		    channel_rate = new double[6];
		    
		    // The type of compression that produces the minimum result.
		    channel_type = new byte[6];
		    
		    // The length of the bit string produced.
		    channel_length = new int[6];
		    
		    // The look up tables used to pack/unpack strings.
		    channel_table = new ArrayList();
		    
		    // Actual data.
		    channel_data = new ArrayList();
		    
		    green_data = new ArrayList();
		    
		    for(int i = 0; i < 6; i++)
		    {
		    	ArrayList data_list = new ArrayList();
		    	channel_data.add(data_list);
		    }
		    
		    // The input for the delta values.
		    channel_init = new int[6];
		    
		    // The value we subtract from frame values so the
		    // least value is 0.
		    channel_min = new int[6];
		    
		    // The value we subtract from deltas so the
		    // least value is 0.
		    channel_delta_min = new int[6];
		    
		    
		    channel_string    = new String[6];
		    channel_string[0] = new String("blue");
		    channel_string[1] = new String("green");
		    channel_string[2] = new String("red");
		    channel_string[3] = new String("blue-green");
		    channel_string[4] = new String("red-green");
		    channel_string[5] = new String("red-blue");
		    
		    // The delta sum for each set of channels that can produce an image.
		    set_sum       = new int[10];
		    set_rate      = new double[10];
		    set_string    = new String[10]; 
		    set_string[0] = new String("blue, green, and red.");
		    set_string[1] = new String("blue, red, and red-green.");
		    set_string[2] = new String("blue, red, and blue-green.");
		    set_string[3] = new String("blue, blue-green, and red-green.");
		    set_string[4] = new String("blue, blue-green, and red-blue.");
		    set_string[5] = new String("green, red, and blue-green.");
		    set_string[6] = new String("red, blue-green, and red-green.");
		    set_string[7] = new String("green, blue-green, and red-green.");
		    set_string[8] = new String("green, red-green, and red-blue.");
		    set_string[9] = new String("red, red-green, red-blue.");
		    
		    pixel_length = xdim * ydim * 8;
		    file_ratio   = file_length * 8;
		    file_ratio  /= pixel_length * 3;
		}
		
		public void actionPerformed(ActionEvent event)
		{
		    System.out.println("Pixel shift is " + pixel_shift);
		    System.out.println("File compression rate is " + String.format("%.4f", file_ratio));
		    System.out.println();
		    
		    for(int i = 0; i < xdim * ydim; i++)
		    {
		        shifted_blue[i]  = blue[i]  >> pixel_shift;
		        shifted_green[i] = green[i] >> pixel_shift;
			    shifted_red[i]   = red[i]   >> pixel_shift; 
		    }
		    
		    // The minimum reference channel value is usually 0,
		    // unless there's an unusual distribution of values.
		    // Not sure we really need to account for the minimum in the reference frames,
		    // and it might confuse the difference frames if we make different adjustments
		    // to different reference frames.  Probably why subtracting it worked before is 
		    // it's almost always 0.
		    
		    int blue_min  = Integer.MAX_VALUE;
		    int green_min = Integer.MAX_VALUE;
		    int red_min   = Integer.MAX_VALUE;
		    for(int i = 0; i < xdim * ydim; i++)
		    {
		        if(shifted_blue[i] < blue_min)
		            blue_min = shifted_blue[i];
		        if(shifted_green[i] < green_min)
		            green_min = shifted_green[i];
		        if(shifted_red[i] < red_min)
		            red_min = shifted_red[i];
		    }
		    channel_min[0]  = (short)blue_min;
		    channel_init[0] = (short)shifted_blue[0];
		    channel_min[1]  = (short)green_min;
		    channel_init[1] = (short)shifted_green[0];
		    channel_min[2]  = (short)red_min;
		    channel_init[2] = (short)shifted_red[0];
		    
		    int [] delta, string_table, histogram;
		    ArrayList histogram_list;
		    byte [] string, clipped_string;
		    int string_length, string_array_length;
		    
		    //*************************************************************************
		    
		    /*
		    delta = DeltaMapper.getDeltasFromValues(shifted_blue, xdim, ydim, channel_init[0]);
		    if(delta[0] == 0)
		    {
		    	//System.out.println("Delta type is horizontal.");
		    	channel_sum[0] = DeltaMapper.getHorizontalDeltaSum(delta, xdim, ydim);
		    }
		    else
		    {
		    	//System.out.println("Delta type is vertical.");
		    	channel_sum[0] = DeltaMapper.getVerticalDeltaSum(delta, xdim, ydim);
		    }
		    histogram_list       = DeltaMapper.getHistogram(delta);
		    channel_delta_min[0] = (int)histogram_list.get(0);
			histogram            = (int[])histogram_list.get(1);
			string_table         = DeltaMapper.getRandomTable(histogram);
			channel_table.add(string_table);
			
			for(int i = 1; i < delta.length; i++)
				delta[i] -= channel_delta_min[0];
			string             = new byte[xdim * ydim * 2];
	    	string_length      = 0;
			string_length      = DeltaMapper.packStrings2(delta, string_table, string);
			channel_length[0]  = string_length;
			channel_rate[0]    = string_length;
			channel_rate[0]   /= pixel_length;
			channel_type[0]    = 0;
			string_array_length = string_length / 8;
			byte remainder = (byte)(string_length % 8);
			if(remainder != 0)
				string_array_length++;
			clipped_string = new byte[string_array_length + 1];
			for(int i = 0; i < string_array_length; i++)
				clipped_string[i] = string[i];
			
			if(remainder == 0)
				clipped_string[string_array_length] = 0;	
			else
			{
				 byte bits                           = 8;
				 int null_bits                       = bits - remainder;
				 clipped_string[string_array_length] = (byte)null_bits;
			}
			ArrayList data_list = (ArrayList)channel_data.get(0);
			
			// Add packed string.
			data_list.add(clipped_string);
			
			byte [] zipped_string = new byte[xdim * ydim * 2];
		    Deflater deflater = new Deflater(Deflater.HUFFMAN_ONLY);	
	    	deflater.setInput(clipped_string);
	    	deflater.finish();
	    	int zipped_string_length = deflater.deflate(zipped_string);
	    	deflater.end();
	    	byte [] clipped_zipped_string = new byte[zipped_string_length];
	    	for(int i = 0; i < zipped_string_length; i++)
	    		clipped_zipped_string[i] = zipped_string[i];
	    	
	    	
	    	// Add zipped string;
	    	data_list.add(clipped_zipped_string);
	    	
	    	
	    	double zero_one_ratio = xdim * ydim;
	        if(histogram.length > 1)
	        {
				int min_value = Integer.MAX_VALUE;
				for(int i = 0; i < histogram.length; i++)
					 if(histogram[i] < min_value)
						min_value = histogram[i];
				zero_one_ratio -= min_value;
	        }	
		    zero_one_ratio  /= string_length;
			byte [] compressed_string = new byte[xdim * ydim * 4];	
			
			if(zero_one_ratio > .5)
			{
				System.out.println("Compressing zeros.");
				channel_length[0] = DeltaMapper.compressZeroStrings(string, string_length, compressed_string);
			}
			else
			{
				System.out.println("Compressing ones.");
				channel_length[0] =  DeltaMapper.compressOneStrings(string, string_length, compressed_string);
			}
			string_array_length = channel_length[0] / 8;
			if(channel_length[0] % 8 != 0)
				string_array_length++;
			byte [] clipped_compressed_string = new byte[string_array_length + 1];
			for(int i = 0; i < string_array_length; i++)
				clipped_compressed_string[i] = compressed_string[i];
			remainder = (byte)(channel_length[0] % 8);
			if(remainder == 0)
				clipped_compressed_string[string_array_length] = 0;	
			else
			{
				 byte bits                           = 8;
				 int null_bits                       = bits - remainder;
				 clipped_compressed_string[string_array_length] = (byte)null_bits;
			}
			
			//3
			data_list.add(clipped_compressed_string);
	    	
			channel_type[0] = 2;
	    	*/
			
		    green_init = shifted_green[0];
		    delta = DeltaMapper.getDeltasFromValues(shifted_green, xdim, ydim, channel_init[1]);
		    if(delta[0] == 0)
		    {
		    	System.out.println("Delta type is horizontal.");
		    	green_sum = DeltaMapper.getHorizontalDeltaSum(delta, xdim, ydim);
		    }
		    else
		    {
		        System.out.println("Delta type is vertical.");
		    	green_sum = DeltaMapper.getVerticalDeltaSum(delta, xdim, ydim);
		    }
		    histogram_list       = DeltaMapper.getHistogram(delta);
		    //channel_delta_min[1] = (int)histogram_list.get(0);
		    green_delta_min = (int)histogram_list.get(0);
		    System.out.println("Minimum value for green deltas is " + green_delta_min);
			histogram            = (int[])histogram_list.get(1);
			green_table = DeltaMapper.getRandomTable(histogram);
			//string_table         = DeltaMapper.getRandomTable(histogram);
			//channel_table.add(string_table);
			for(int i = 1; i < delta.length; i++)
				//delta[i] -= channel_delta_min[1];
				delta[i] -= green_delta_min;
			string              = new byte[xdim * ydim * 2];
	    	string_length       = 0;
			string_length       = DeltaMapper.packStrings2(delta, green_table, string);
			
			/*
			channel_length[1]   = string_length;
			channel_rate[1]     = string_length;
			channel_rate[1]    /= pixel_length;
			channel_type[1]     = 0;
			*/
			
			green_string_length = string_length;
			string_array_length = string_length / 8;
			byte remainder = (byte)(string_length % 8);
			if(remainder != 0)
				string_array_length++;
			clipped_string = new byte[string_array_length + 1];
			for(int i = 0; i < string_array_length; i++)
				clipped_string[i] = string[i];
			
			if(remainder == 0)
				clipped_string[string_array_length] = 0;	
			else
			{
				 byte bits                           = 8;
				 int null_bits                       = bits - remainder;
				 clipped_string[string_array_length] = (byte)null_bits;
			}
            //data_list = (ArrayList)channel_data.get(1);
			
			// Add packed string.
			//data_list.add(clipped_string);
			
			green_data.add(clipped_string);
			
			byte [] zipped_string = new byte[xdim * ydim * 2];
		    Deflater deflater = new Deflater(Deflater.HUFFMAN_ONLY);	
	    	deflater.setInput(clipped_string);
	    	deflater.finish();
	    	int zipped_string_length = deflater.deflate(zipped_string);
	    	deflater.end();
	    	byte [] clipped_zipped_string = new byte[zipped_string_length];
	    	for(int i = 0; i < zipped_string_length; i++)
	    		clipped_zipped_string[i] = zipped_string[i];
	    	
	    	// Add zipped string;
	    	//data_list.add(clipped_zipped_string);
	    	green_data.add(clipped_zipped_string);
	    	System.out.println("Byte length of clipped zipped string is " + clipped_zipped_string.length);
	    	System.out.println("Remainder was " + remainder);
	    	
	    	
	    	double zero_one_ratio = xdim * ydim;
	        if(histogram.length > 1)
	        {
				int min_value = Integer.MAX_VALUE;
				for(int i = 0; i < histogram.length; i++)
					 if(histogram[i] < min_value)
						min_value = histogram[i];
				zero_one_ratio -= min_value;
	        }	
		    zero_one_ratio  /= string_length;
			byte[] compressed_string = new byte[xdim * ydim * 4];	
			
			if(zero_one_ratio > .5)
			{
				System.out.println("Compressing zeros.");
				green_compressed_string_length = DeltaMapper.compressZeroStrings(string, string_length, compressed_string);
			}
			else
			{
				System.out.println("Compressing ones.");
				green_compressed_string_length =  DeltaMapper.compressOneStrings(string, string_length, compressed_string);
			}
			string_array_length = green_compressed_string_length / 8;
			if(green_compressed_string_length % 8 != 0)
				string_array_length++;
			byte [] clipped_compressed_string = new byte[string_array_length + 1];
			for(int i = 0; i < string_array_length; i++)
				clipped_compressed_string[i] = compressed_string[i];
			remainder = (byte)(green_compressed_string_length % 8);
			if(remainder == 0)
			{
				clipped_compressed_string[string_array_length] = 0;	
				System.out.println("Null bits is 0.");
			}
			else
			{
				 byte bits                           = 8;
				 int null_bits                       = bits - remainder;
				 clipped_compressed_string[string_array_length] = (byte)null_bits;
				 System.out.println("Null bits is " + null_bits);
			}
			
			//data_list.add(clipped_compressed_string);
			green_data.add(clipped_compressed_string);
			System.out.println("Byte length of green compressed string is " + clipped_compressed_string.length);
	    	
			byte [] zipped_compressed_string = new byte[xdim * ydim * 4];	
			// This seem to be the only case where LZW helps, and not much.
			deflater = new Deflater(Deflater.HUFFMAN_ONLY);
			
		    deflater.setInput(clipped_compressed_string);
			int zipped_compressed_length = deflater.deflate(zipped_compressed_string);
			deflater.end();
			System.out.println("Byte length of zipped compressed string is " + zipped_compressed_length);
			
			byte [] clipped_zipped_compressed_string = new byte[zipped_compressed_length];
			for(int i = 0; i < zipped_compressed_length; i++)
				clipped_zipped_compressed_string[i] = zipped_compressed_string[i];
			green_data.add(clipped_zipped_compressed_string);
			
			green_type = 3;
			//channel_type[1] = 2;
		    
		    
		    
		    
			//*******************************************************************************
			/*
			delta = DeltaMapper.getDeltasFromValues(shifted_green, xdim, ydim, channel_init[1]);
		    if(delta[0] == 0)
		    {
		    	//System.out.println("Delta type is horizontal.");
		    	channel_sum[1] = DeltaMapper.getHorizontalDeltaSum(delta, xdim, ydim);
		    }
		    else
		    {
		    	//System.out.println("Delta type is vertical.");
		    	channel_sum[1] = DeltaMapper.getVerticalDeltaSum(delta, xdim, ydim);
		    }
		    histogram_list       = DeltaMapper.getHistogram(delta);
		    //channel_delta_min[1] = (int)histogram_list.get(0);
		    green_delta_min = (int)histogram_list.get(0);
		    System.out.println("Minimum value for green deltas is " + channel_delta_min[1]);
			histogram            = (int[])histogram_list.get(1);
			string_table         = DeltaMapper.getRandomTable(histogram);
			channel_table.add(string_table);
			for(int i = 1; i < delta.length; i++)
				delta[i] -= channel_delta_min[1];
			string              = new byte[xdim * ydim * 2];
	    	string_length       = 0;
			string_length       = DeltaMapper.packStrings2(delta, string_table, string);
			channel_length[1]   = string_length;
			channel_rate[1]     = string_length;
			channel_rate[1]    /= pixel_length;
			channel_type[1]     = 0;
			string_array_length = string_length / 8;
			remainder = (byte)(string_length % 8);
			if(remainder != 0)
				string_array_length++;
			clipped_string = new byte[string_array_length + 1];
			for(int i = 0; i < string_array_length; i++)
				clipped_string[i] = string[i];
			
			if(remainder == 0)
				clipped_string[string_array_length] = 0;	
			else
			{
				 byte bits                           = 8;
				 int null_bits                       = bits - remainder;
				 clipped_string[string_array_length] = (byte)null_bits;
			}
            data_list = (ArrayList)channel_data.get(1);
			
			// Add packed string.
			data_list.add(clipped_string);
			
			zipped_string = new byte[xdim * ydim * 2];
		    deflater = new Deflater(Deflater.HUFFMAN_ONLY);	
	    	deflater.setInput(clipped_string);
	    	deflater.finish();
	    	zipped_string_length = deflater.deflate(zipped_string);
	    	deflater.end();
	    	clipped_zipped_string = new byte[zipped_string_length];
	    	for(int i = 0; i < zipped_string_length; i++)
	    		clipped_zipped_string[i] = zipped_string[i];
	    	
	    	// Add zipped string;
	    	data_list.add(clipped_zipped_string);
	    	System.out.println("Byte length of clipped zipped string is " + clipped_zipped_string.length);
	    	System.out.println("Remainder was " + remainder);
	    	
	    	
	    	zero_one_ratio = xdim * ydim;
	        if(histogram.length > 1)
	        {
				int min_value = Integer.MAX_VALUE;
				for(int i = 0; i < histogram.length; i++)
					 if(histogram[i] < min_value)
						min_value = histogram[i];
				zero_one_ratio -= min_value;
	        }	
		    zero_one_ratio  /= string_length;
			compressed_string = new byte[xdim * ydim * 4];	
			
			if(zero_one_ratio > .5)
			{
				System.out.println("Compressing zeros.");
				channel_length[1] = DeltaMapper.compressZeroStrings(string, string_length, compressed_string);
			}
			else
			{
				System.out.println("Compressing ones.");
				channel_length[1] =  DeltaMapper.compressOneStrings(string, string_length, compressed_string);
			}
			string_array_length = channel_length[1] / 8;
			if(channel_length[1] % 8 != 0)
				string_array_length++;
			clipped_compressed_string = new byte[string_array_length + 1];
			for(int i = 0; i < string_array_length; i++)
				clipped_compressed_string[i] = compressed_string[i];
			remainder = (byte)(channel_length[1] % 8);
			if(remainder == 0)
			{
				clipped_compressed_string[string_array_length] = 0;	
				System.out.println("Null bits is 0.");
			}
			else
			{
				 byte bits                           = 8;
				 int null_bits                       = bits - remainder;
				 clipped_compressed_string[string_array_length] = (byte)null_bits;
				 System.out.println("Null bits is " + null_bits);
			}
			
			data_list.add(clipped_compressed_string);
	    	
			System.out.println("Green channel length is " + channel_length[1]);
			channel_type[1] = 2;
			
			//**********************************************************************************
			
			delta = DeltaMapper.getDeltasFromValues(shifted_red, xdim, ydim, channel_init[2]);
		    if(delta[0] == 0)
		    {
		    	//System.out.println("Delta type is horizontal.");
		    	channel_sum[2] = DeltaMapper.getHorizontalDeltaSum(delta, xdim, ydim);
		    }
		    else
		    {
		    	//System.out.println("Delta type is vertical.");
		    	channel_sum[2] = DeltaMapper.getVerticalDeltaSum(delta, xdim, ydim);
		    }
		   
		    histogram_list       = DeltaMapper.getHistogram(delta);
		    channel_delta_min[2] = (int)histogram_list.get(0);
			histogram            = (int[])histogram_list.get(1);
			string_table         = DeltaMapper.getRandomTable(histogram);
			channel_table.add(string_table);
			for(int i = 1; i < delta.length; i++)
				delta[i] -= channel_delta_min[2];
			 
			string            = new byte[xdim * ydim * 2];
	    	string_length     = 0;
			string_length     = DeltaMapper.packStrings2(delta, string_table, string);
			channel_length[2] = string_length;
			channel_rate[2]   = string_length;
			channel_rate[2]  /= pixel_length;
			channel_type[2]   = 0;
			string_array_length = string_length / 8;
			remainder = (byte)(string_length % 8);
			if(remainder != 0)
				string_array_length++;
			clipped_string = new byte[string_array_length + 1];
			for(int i = 0; i < string_array_length; i++)
				clipped_string[i] = string[i];
			
			if(remainder == 0)
				clipped_string[string_array_length] = 0;	
			else
			{
				 byte bits                           = 8;
				 int null_bits                       = bits - remainder;
				 clipped_string[string_array_length] = (byte)null_bits;
			}
            data_list = (ArrayList)channel_data.get(2);
			
			// Add packed string.
			data_list.add(clipped_string);
			
			zipped_string = new byte[xdim * ydim * 2];
		    deflater = new Deflater(Deflater.HUFFMAN_ONLY);	
	    	deflater.setInput(clipped_string);
	    	deflater.finish();
	    	zipped_string_length = deflater.deflate(zipped_string);
	    	deflater.end();
	    	clipped_zipped_string = new byte[zipped_string_length];
	    	for(int i = 0; i < zipped_string_length; i++)
	    		clipped_zipped_string[i] = zipped_string[i];
	    	
	    	// Add zipped string;
	    	data_list.add(clipped_zipped_string);
			
	    	zero_one_ratio = xdim * ydim;
	        if(histogram.length > 1)
	        {
				int min_value = Integer.MAX_VALUE;
				for(int i = 0; i < histogram.length; i++)
					 if(histogram[i] < min_value)
						min_value = histogram[i];
				zero_one_ratio -= min_value;
	        }	
		    zero_one_ratio  /= string_length;
			compressed_string = new byte[xdim * ydim * 4];	
			
			if(zero_one_ratio > .5)
			{
				System.out.println("Compressing zeros.");
				channel_length[2] = DeltaMapper.compressZeroStrings(string, string_length, compressed_string);
			}
			else
			{
				System.out.println("Compressing ones.");
				channel_length[2] =  DeltaMapper.compressOneStrings(string, string_length, compressed_string);
			}
			string_array_length = channel_length[2] / 8;
			if(channel_length[2] % 8 != 0)
				string_array_length++;
			clipped_compressed_string = new byte[string_array_length + 1];
			for(int i = 0; i < string_array_length; i++)
				clipped_compressed_string[i] = compressed_string[i];
			remainder = (byte)(channel_length[1] % 8);
			if(remainder == 0)
				clipped_compressed_string[string_array_length] = 0;	
			else
			{
				 byte bits                           = 8;
				 int null_bits                       = bits - remainder;
				 clipped_compressed_string[string_array_length] = (byte)null_bits;
			}
			data_list.add(clipped_compressed_string);
	    	
			
			
			// The difference frames will usually contain negative numbers,
			// unless they are perfectly correlated with the other frame.
			// We'll go ahead and set the least values to 0. Worth checking if it
			// works anyway.
			
			
			
			
			/*
		    shifted_blue_green = DeltaMapper.getDifference(shifted_blue, shifted_green);
	        int blue_green_min = 0;
		    for(int i = 0; i < xdim * ydim; i++)
		    	if(shifted_blue_green[i] < blue_green_min)
	            	blue_green_min = shifted_blue_green[i];
		    if(blue_green_min != 0)
		        for(int i = 0; i < xdim * ydim; i++)
		    	    shifted_blue_green[i] -= blue_green_min;
		    
		    
		    
		    
		    
		    
		    
		    
		    
		    
		    channel_min[3]  = (short)blue_green_min;
		    channel_init[3] = (short)shifted_blue_green[0]; 
		    delta = DeltaMapper.getDeltasFromValues(shifted_blue_green, xdim, ydim, channel_init[3]);
		    if(delta[0] == 0)
		    {
		    	//System.out.println("Delta type is horizontal.");
		    	channel_sum[3] = DeltaMapper.getHorizontalDeltaSum(delta, xdim, ydim);
		    }
		    else
		    {
		    	//System.out.println("Delta type is vertical.");
		    	channel_sum[3] = DeltaMapper.getVerticalDeltaSum(delta, xdim, ydim);
		    }
		    histogram_list       = DeltaMapper.getHistogram(delta);
		    channel_delta_min[3] = (int)histogram_list.get(0);
			histogram            = (int[])histogram_list.get(1);
			string_table         = DeltaMapper.getRandomTable(histogram);
			channel_table.add(string_table);
			
			for(int i = 1; i < delta.length; i++)
				delta[i] -= channel_delta_min[3];
			string             = new byte[xdim * ydim * 2];
	    	string_length      = 0;
			string_length      = DeltaMapper.packStrings2(delta, string_table, string);
			channel_length[3]  = string_length;
			channel_rate[3]    = string_length;
			channel_rate[3]   /= pixel_length;
			channel_type[3]    = 0;
			string_array_length = string_length / 8;
			remainder = (byte)(string_length % 8);
			if(remainder != 0)
				string_array_length++;
			clipped_string = new byte[string_array_length + 1];
			for(int i = 0; i < string_array_length; i++)
				clipped_string[i] = string[i];
			
			if(remainder == 0)
				clipped_string[string_array_length] = 0;	
			else
			{
				 byte bits                           = 8;
				 int null_bits                       = bits - remainder;
				 clipped_string[string_array_length] = (byte)null_bits;
			}
            data_list = (ArrayList)channel_data.get(3);
			
			// Add packed string.
			data_list.add(clipped_string);
			
			zipped_string = new byte[xdim * ydim * 2];
		    deflater = new Deflater(Deflater.HUFFMAN_ONLY);	
	    	deflater.setInput(clipped_string);
	    	deflater.finish();
	    	zipped_string_length = deflater.deflate(zipped_string);
	    	deflater.end();
	    	clipped_zipped_string = new byte[zipped_string_length];
	    	for(int i = 0; i < zipped_string_length; i++)
	    		clipped_zipped_string[i] = zipped_string[i];
	    	
	    	// Add zipped string;
	    	data_list.add(clipped_zipped_string);
			
		    shifted_red_green = DeltaMapper.getDifference(shifted_red, shifted_green);
	        int red_green_min = 0;
		    for(int i = 0; i < xdim * ydim; i++)
		    	if(shifted_red_green[i] < red_green_min)
		    		red_green_min = shifted_red_green[i];
		    if(red_green_min != 0)
		        for(int i = 0; i < xdim * ydim; i++)
		    	    shifted_red_green[i] -= red_green_min;
		    
		    channel_min[4]  = (short)red_green_min;
		    channel_init[4] = (short)shifted_red_green[0]; 
		    delta = DeltaMapper.getDeltasFromValues(shifted_red_green, xdim, ydim, channel_init[4]);
		    if(delta[0] == 0)
		    {
		    	//System.out.println("Delta type is horizontal.");
		    	channel_sum[4] = DeltaMapper.getHorizontalDeltaSum(delta, xdim, ydim);
		    }
		    else
		    {
		    	//System.out.println("Delta type is vertical.");
		    	channel_sum[4] = DeltaMapper.getVerticalDeltaSum(delta, xdim, ydim);
		    }
		    histogram_list       = DeltaMapper.getHistogram(delta);
		    channel_delta_min[4] = (int)histogram_list.get(0);
			histogram            = (int[])histogram_list.get(1);
			string_table         = DeltaMapper.getRandomTable(histogram);
			channel_table.add(string_table);
			
			for(int i = 1; i < delta.length; i++)
				delta[i] -= channel_delta_min[4];
			string             = new byte[xdim * ydim * 2];
	    	string_length      = 0;
			string_length      = DeltaMapper.packStrings2(delta, string_table, string);
			channel_length[4]  = string_length;
			channel_rate[4]    = string_length;
			channel_rate[4]   /= pixel_length;
			channel_type[4]    = 0;
			string_array_length = string_length / 8;
			remainder = (byte)(string_length % 8);
			if(remainder != 0)
				string_array_length++;
			clipped_string = new byte[string_array_length + 1];
			for(int i = 0; i < string_array_length; i++)
				clipped_string[i] = string[i];
			
			if(remainder == 0)
				clipped_string[string_array_length] = 0;	
			else
			{
				 byte bits                           = 8;
				 int null_bits                       = bits - remainder;
				 clipped_string[string_array_length] = (byte)null_bits;
			}
            data_list = (ArrayList)channel_data.get(4);
			
			// Add packed string.
			data_list.add(clipped_string);
			
			zipped_string = new byte[xdim * ydim * 2];
		    deflater = new Deflater(Deflater.HUFFMAN_ONLY);	
	    	deflater.setInput(clipped_string);
	    	deflater.finish();
	    	zipped_string_length = deflater.deflate(zipped_string);
	    	deflater.end();
	    	clipped_zipped_string = new byte[zipped_string_length];
	    	for(int i = 0; i < zipped_string_length; i++)
	    		clipped_zipped_string[i] = zipped_string[i];
	    	
	    	// Add zipped string;
	    	data_list.add(clipped_zipped_string);
		    
		    shifted_red_blue = DeltaMapper.getDifference(shifted_red, shifted_blue);
	        int red_blue_min = 0;
		    for(int i = 0; i < xdim * ydim; i++)
		    	if(shifted_red_blue[i] < red_blue_min)
		    		red_blue_min = shifted_red_blue[i];
		    if(red_blue_min != 0)
		        for(int i = 0; i < xdim * ydim; i++)
		    	    shifted_red_blue[i] -= red_blue_min;
		    channel_min[5]  = red_blue_min;
		    channel_init[5] = shifted_red_blue[0];
		    delta = DeltaMapper.getDeltasFromValues(shifted_red_blue, xdim, ydim, channel_init[5]);
		    if(delta[0] == 0)
		    {
		    	//System.out.println("Delta type is horizontal.");
		    	channel_sum[5] = DeltaMapper.getHorizontalDeltaSum(delta, xdim, ydim);
		    }
		    else
		    {
		    	//System.out.println("Delta type is vertical.");
		    	channel_sum[5] = DeltaMapper.getVerticalDeltaSum(delta, xdim, ydim);
		    }
		    histogram_list       = DeltaMapper.getHistogram(delta);
		    channel_delta_min[5] = (int)histogram_list.get(0);
			histogram            = (int[])histogram_list.get(1);
			string_table         = DeltaMapper.getRandomTable(histogram);
			channel_table.add(string_table);
			
			for(int i = 1; i < delta.length; i++)
				delta[i] -= channel_delta_min[5];
			string             = new byte[xdim * ydim * 2];
	    	string_length      = 0;
			string_length      = DeltaMapper.packStrings2(delta, string_table, string);
			channel_length[5]  = string_length;
			channel_rate[5]    = string_length;
			channel_rate[5]   /= pixel_length;
			channel_type[5]    = 0;
			string_array_length = string_length / 8;
			remainder = (byte)(string_length % 8);
			if(remainder != 0)
				string_array_length++;
			clipped_string = new byte[string_array_length + 1];
			for(int i = 0; i < string_array_length; i++)
				clipped_string[i] = string[i];
			
			if(remainder == 0)
				clipped_string[string_array_length] = 0;	
			else
			{
				 byte bits                           = 8;
				 int null_bits                       = bits - remainder;
				 clipped_string[string_array_length] = (byte)null_bits;
			}
            data_list = (ArrayList)channel_data.get(5);
			
			// Add packed string.
			data_list.add(clipped_string);
			
			zipped_string = new byte[xdim * ydim * 2];
		    deflater = new Deflater(Deflater.HUFFMAN_ONLY);	
	    	deflater.setInput(clipped_string);
	    	deflater.finish();
	    	zipped_string_length = deflater.deflate(zipped_string);
	    	deflater.end();
	    	clipped_zipped_string = new byte[zipped_string_length];
	    	for(int i = 0; i < zipped_string_length; i++)
	    		clipped_zipped_string[i] = zipped_string[i];
	    	
	    	
	    	
	    	// Add zipped string;
	    	data_list.add(clipped_zipped_string);
	    	*/
	    	
	    	
		    
			//System.out.println("************************************************************************");
		    /*
			set_sum[0] = channel_sum[0] + channel_sum[1] + channel_sum[2];
			set_sum[1] = channel_sum[0] + channel_sum[4] + channel_sum[2];
			set_sum[2] = channel_sum[0] + channel_sum[3] + channel_sum[2];
			set_sum[3] = channel_sum[0] + channel_sum[1] + channel_sum[4];
			set_sum[4] = channel_sum[0] + channel_sum[3] + channel_sum[5];
			set_sum[5] = channel_sum[3] + channel_sum[1] + channel_sum[2];
			set_sum[6] = channel_sum[3] + channel_sum[4] + channel_sum[2];
			set_sum[7] = channel_sum[3] + channel_sum[1] + channel_sum[4];
			set_sum[8] = channel_sum[5] + channel_sum[1] + channel_sum[4];
			set_sum[9] = channel_sum[5] + channel_sum[4] + channel_sum[2];
			
			set_rate[0] = (channel_rate[0] + channel_rate[1] + channel_rate[2]) / 3;
			set_rate[1] = (channel_rate[0] + channel_rate[4] + channel_rate[2]) / 3;
			set_rate[2] = (channel_rate[0] + channel_rate[3] + channel_rate[2]) / 3;
			set_rate[3] = (channel_rate[0] + channel_rate[1] + channel_rate[4]) / 3;
			set_rate[4] = (channel_rate[0] + channel_rate[3] + channel_rate[5]) / 3;
			set_rate[5] = (channel_rate[3] + channel_rate[1] + channel_rate[2]) / 3;
			set_rate[6] = (channel_rate[3] + channel_rate[4] + channel_rate[2]) / 3;
			set_rate[7] = (channel_rate[3] + channel_rate[1] + channel_rate[4]) / 3;
			set_rate[8] = (channel_rate[5] + channel_rate[1] + channel_rate[4]) / 3;
			set_rate[9] = (channel_rate[5] + channel_rate[4] + channel_rate[2]) / 3;
			
			
			int min_index     = 0;
			int min_delta_sum = Integer.MAX_VALUE;
			for(int i = 0; i < 6; i++)
			{
				if(channel_sum[i] < min_delta_sum)
				{
				    min_delta_sum = channel_sum[i];
				    min_index = i;
				}
			}
			System.out.println("A channel with the lowest delta sum is " + channel_string[min_index]);
			System.out.println("The compression rate is " + String.format("%.4f", channel_rate[min_index]));
			System.out.println();
			*/
	    	
			// This code produces an image that should be exactly the same
		    // as the processed image.  
		    for(int i = 0; i < xdim * ydim; i++)
			{
		         new_alpha[i] = alpha[i];
		         
			     new_blue[i]    = blue[i];
			     new_blue[i]  >>= pixel_shift;
		         new_blue[i]  <<= pixel_shift;
		        
			    
			     new_green[i]   = green[i];
			     new_green[i] >>= pixel_shift;
		         new_green[i] <<= pixel_shift;
		        
	            
			     new_red[i]     = red[i];
	             new_red[i]   >>= pixel_shift;
		         new_red[i]   <<= pixel_shift;
			}
		   
		    for(int i = 0; i < xdim * ydim; i++)
		    {
		        // Not initalizing the alpha channel.
	            new_pixel[i] = 0;
	          
	            new_pixel[i] |= new_blue[i] << 16;
	            new_pixel[i] |= new_green[i] << 8;    
	            new_pixel[i] |= new_red[i];	
		    }
		     
			for(int i = 0; i < xdim; i++)
			{
			    for(int j = 0; j < ydim; j++)
			    {
			    	image.setRGB(i, j, new_pixel[j * xdim + i]);
			    }	
			} 
		    image_canvas.repaint();
		    
		    initialized = true;
		}
	}
	
	class ReloadHandler implements ActionListener
	{
		public void actionPerformed(ActionEvent event)
		{
			
			for(int i = 0; i < xdim; i++)
		    {
		    	for(int j = 0; j < ydim; j++)
		    	{
		    		image.setRGB(i, j, original_pixel[j * xdim + i]);
		    	}
		    	
		    } 
			System.out.println("Reloaded original image.");
			image_canvas.repaint();
		}
	}
	
	class SaveHandler implements ActionListener
	{
		public void actionPerformed(ActionEvent event)
		{
			if(!initialized)
				apply_item.doClick();
			File file = new File("foo");
		    try
		    {
		        DataOutputStream out = new DataOutputStream(new FileOutputStream(file));
		        
		        // Dimensions of frame.
		        out.writeShort(xdim);
		        out.writeShort(ydim);
		        
		        // Channel 0-5.
		        out.writeByte(1);
		        
		        // Pixel shift
		        out.writeByte(pixel_shift);
		     
		        // Init value for deltas.
		        out.writeInt(green_init);
		        System.out.println("Init value for deltas is " + green_init);
		        
		        // Minimum_value for deltas.
		        out.writeInt(green_delta_min);
		        System.out.println("Minimum value for deltas is " + green_delta_min);
		        
		        // Compression type 0-4.
		        out.writeByte(green_type);
		        
		        // The length of the table used for
		        // string packing/unpacking.
		        out.writeShort(green_table.length);
		        System.out.println("String table length is " + green_table.length);
		        
		        // The table itself.
		        for(int k = 0; k < green_table.length; k++)
		            out.writeInt(green_table[k]);
		       
		        // The length of the bitstring
		        
		        if(green_type < 2)
		        {
		            out.writeInt(green_string_length);
		            System.out.println("Bit length is " + green_string_length);
		        }
		        else
		        {
		        	out.writeInt(green_compressed_string_length);
		            System.out.println("Bit length is " + green_compressed_string_length);	
		        }
		       
		        byte[] data = (byte[])green_data.get(green_type);
		        System.out.println("Length of data is " + data.length);
		        
		        // The length of the data.
		        out.writeInt(data.length);
		        System.out.println("Data length is " + data.length);
		        
		        // The data itself.
		        out.write(data, 0, data.length);
		        
		        out.close();
		    }
		    catch(Exception e)
		    {
			    System.out.println(e.toString());
		    }
		    
			/*
			int min_index     = 0;
			int min_delta_sum = Integer.MAX_VALUE;
			for(int i = 0; i < 10; i++)
			{
				if(set_sum[i] < min_delta_sum)
				{
				    min_delta_sum = set_sum[i];
				    min_index = i;
				}
			}
			System.out.println("Saving:");
			System.out.println("A set of channels with the lowest delta sum is " + set_string[min_index]);
			System.out.println("The compression rate is " + String.format("%.4f", set_rate[min_index]));
			
			int [] channel = DeltaMapper.getChannels(min_index);
			*/
			
		    /*
			for(int i = 0; i < 3; i++)
			{
				// Index for channel, 0-5.
				int j = channel[i];
				
				// Index for compression type, 0-4.
				//byte type = channel_type[j];
				byte type = 2;
				
				int delta_min = channel_delta_min[j];
				
				int init      = channel_init[j];
			
				int length    = channel_length[j];
				
				int []  string_table = (int [])channel_table.get(j);
				
				ArrayList data_list = (ArrayList)channel_data.get(j);
				byte [] data         = (byte [])data_list.get(2);
				
				System.out.println();
				
				if(i == 0)
				{
					System.out.println(channel_string[j] + " has " + String.format("%.4f", channel_rate[j]) + " compression with " + type_string[type]);
					System.out.println("Data array has length " + data.length + " and bitlength " + length);
					System.out.println("The minimum delta value is " + delta_min);
				    File file = new File("foo");
				    try
				    {
				        DataOutputStream out = new DataOutputStream(new FileOutputStream(file));
				        
				        // Dimensions of frame.
				        out.writeShort(xdim);
				        out.writeShort(ydim);
				        
				        // Channel 0-5.
				        out.writeByte(j);
				        
				        // Pixel shift
				        out.writeByte(pixel_shift);
				     
				        // Init value for deltas.
				        out.writeInt(channel_init[j]);
				        System.out.println("Init value for deltas is " + channel_init[j]);
				        
				        // Minimum_value for deltas.
				        out.writeInt(channel_delta_min[j]);
				        System.out.println("Minimum value for deltas is " + channel_delta_min[j]);
				        
				        // Compression type 0-4.
				        out.writeByte(type);
				        
				        // The length of the table used for
				        // string packing/unpacking.
				        out.writeShort(string_table.length);
				        System.out.println("String table length is " + string_table.length);
				        
				        // The table itself.
				        for(int k = 0; k < string_table.length; k++)
				            out.writeInt(string_table[k]);
				       
				        // The length of the bitstring
				        out.writeInt(channel_length[j]);
				        System.out.println("Bit length is " + channel_length[j]);
				       
				        // The length of the data.
				        out.writeInt(data.length);
				        System.out.println("Data length is " + data.length);
				        
				        // The data itself.
				        out.write(data, 0, data.length);
				        
				        out.close();
				    }
				    catch(Exception e)
				    {
					    System.out.println(e.toString());
				    }
				}
			}
			*/
		}
	}
}