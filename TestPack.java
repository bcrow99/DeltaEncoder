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
import java.math.*;
import java.lang.Math.*;

public class TestPack
{
	BufferedImage original_image;
	BufferedImage image;
	JMenuItem     apply_item;
	
	
	JDialog       segment_dialog;
	JTextField    segment_value;
	
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
	byte   [] channel_compression_type, channel_bit_type;
	int    [] channel_init, channel_min;
	int    [] channel_min_length, channel_length, channel_zipped_length;
	int    [] channel_compressed_length, channel_zipped_compressed_length;
	int    [] set_sum, channel_sum,channel_delta_min;
	double [] set_rate, channel_rate;
	String [] type_string;
	String [] set_string;
	String [] channel_string;
	int    [] shifted_blue, shifted_green, shifted_red;
	int    [] shifted_blue_green, shifted_red_green, shifted_red_blue;
	
	ArrayList channel_table, channel_data, channel_src, channel_string_length;
	
	double  file_ratio;
	int     min_set_id  = 0;
	int     pixel_shift = 0;
	long    file_length = 0;
	boolean initialized = false;
	boolean debug       = false;
	
	
	public static void main(String[] args)
	{
		if (args.length != 1)
		{
			System.out.println("Usage: java TestPack <filename>");
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
		TestPack encoder = new TestPack(prefix + filename);
	}

	public TestPack(String _filename)
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
		    
		    // PNG files do not always have this raster type.
		    
			if(raster_type != BufferedImage.TYPE_3BYTE_BGR)
			{
				System.out.println("File raster type not supported.");
			}
			else	
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
               
			    JFrame frame = new JFrame("TestPack");
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
				
				
				JMenu settings_menu = new JMenu("Settings");
				
				JMenuItem shift_item = new JMenuItem("Pixel Shift");
				ActionListener shift_handler = new ActionListener()
				{
					public void actionPerformed(ActionEvent e)
					{
						Point location_point = frame.getLocation();
						int x = (int) location_point.getX();
						int y = (int) location_point.getY();
						y -= 50;
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
				frame.setLocation(20, 600);
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
		    
		   
		    // The delta sum for each channel.
		    channel_sum  = new int[6];
		    
		    // The different compression rates per channel.
		    channel_rate = new double[6];
		    
		    // The lengths of the packed strings.
		    channel_length            = new int[6];
		    channel_compressed_length = new int[6];
		    channel_min_length        = new int[6];
		    channel_compression_type  = new byte[6];
		    channel_bit_type          = new byte[6];
		    
		    // The look up tables used to pack/unpack strings.
		    channel_table = new ArrayList();
		    
		    // The actual strings.
		    channel_data = new ArrayList();
		    
		    // The lengths of the strings.
		    channel_string_length = new ArrayList();
		    
		    // The original channel data.
		    channel_src  = new ArrayList();
		    
		    
		   
		   
		    
		    // The input for the delta values.
		    channel_init = new int[6];
		    
		    // The value we subtract from difference 
		    // frame values so the least value is 0.
		    // Also keeping it for reference frames,
		    // but not subtracting it.
		    channel_min = new int[6];
		    
		    // The value we subtract from deltas so the
		    // least value is 0.
		    channel_delta_min = new int[6];
		    
		    type_string       = new String[2];
		    type_string[0]    = new String("packed strings");
		    type_string[1]    = new String("compressed strings");
		    		
		    
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
			// Clear all array lists.
			channel_src.clear();
		    channel_table.clear();
		    
			
			//System.out.println("Pixel shift is " + pixel_shift);
		    //System.out.println("File compression rate is " + String.format("%.4f", file_ratio));
		    //System.out.println();
		    
		    for(int i = 0; i < xdim * ydim; i++)
		    {
		        shifted_blue[i]  = blue[i]  >> pixel_shift;
		        shifted_green[i] = green[i] >> pixel_shift;
			    shifted_red[i]   = red[i]   >> pixel_shift; 
		    }
		    
		    // The minimum reference channel value is usually 0,
		    // unless there's an unusual distribution of values.
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
		    
		    // The difference frames usually contain negative numbers,
		    // so we'll subtract the minimum to make them all positive.
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
		    
		    shifted_red_blue = DeltaMapper.getDifference(shifted_red, shifted_blue);
	        int red_blue_min = 0;
		    for(int i = 0; i < xdim * ydim; i++)
		    	if(shifted_red_blue[i] < red_blue_min)
		    		red_blue_min = shifted_red_blue[i];
		    if(red_blue_min != 0)
		        for(int i = 0; i < xdim * ydim; i++)
		    	    shifted_red_blue[i] -= red_blue_min;
		    channel_min[5]  = (short)red_blue_min;
		    channel_init[5] = (short)shifted_red_blue[0]; 
		    
		    channel_src.add(shifted_blue);
		    channel_src.add(shifted_green);
		    channel_src.add(shifted_red);
		    channel_src.add(shifted_blue_green);
		    channel_src.add(shifted_red_green);
		    channel_src.add(shifted_red_blue);
		    
		    for(int i = 0; i < 6; i++)
		    {
		    	int [] src = (int [])channel_src.get(i);
		    	channel_sum[i] = DeltaMapper.getPaethSum2(src, xdim, ydim, 20);
		    }
		    
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
		    
			min_set_id = min_index;
			System.out.println("A set with the lowest delta sum is " + set_string[min_index]);
			System.out.println("Pixel shift is " + pixel_shift);
			System.out.println();
			
			
			//min_set_id = 7;
			
			int [] channel = DeltaMapper.getChannels(min_set_id);
			channel_table.clear();
			channel_data.clear();
		    
			for(int i = 0; i < 3; i++)
			{
				int       j      = channel[i];
				int []    src    = (int [])channel_src.get(j);
				ArrayList result = DeltaMapper.getDeltasFromValues3(src, xdim, ydim);
				int []    delta  = (int [])result.get(1);
			
				
				ArrayList histogram_list = DeltaMapper.getHistogram(delta);
			    channel_delta_min[j]     = (int)histogram_list.get(0);
			    int [] histogram         = (int[])histogram_list.get(1);
			    int value_range          = (int)histogram_list.get(2);
				int [] string_table = DeltaMapper.getRankTable(histogram);
				channel_table.add(string_table);
				
				for(int k = 1; k < delta.length; k++)
					delta[k] -= channel_delta_min[j];
				//delta[0] = value_range / 2;
				
				int predicted_length = DeltaMapper.getStringLength(delta);
				
				int string_byte_length = predicted_length / 8;
				if(predicted_length % 8 != 0)
					string_byte_length++;
				
				byte [] string    = new byte[2 * string_byte_length];
				byte [] string2   = new byte[2 * string_byte_length];
				channel_length[j] = DeltaMapper.packStrings2(delta, string_table, string);
				//System.out.println("Bit length from pack strings is " + channel_length[j]);
				
				
				ArrayList frequency_list = new ArrayList();
			    int n              = histogram.length;
			    for(int k = 0; k < n; k++)
			    	frequency_list.add(histogram[k]);
			    Collections.sort(frequency_list, Comparator.reverseOrder());
			    
			    int [] frequency = new int[n];
			    for(int k = 0; k < n; k++)
			    {
			    	//System.out.println(k + "->" + (int)frequency_list.get(k));
			    	frequency[k] = (int)frequency_list.get(k);
			    }
			    
			    int [] length  = DeltaMapper.getUnaryLength(n);
			    int max_length = length[n - 1];
			    
			    if(max_length > 57)
			    {
			    	 boolean useBigIntegers = true;
				     BigInteger [] code = DeltaMapper.getUnaryCode(n, useBigIntegers);	
				     int bit_length =  DeltaMapper.packCode(delta, string_table, code, length, string2);
				     channel_string_length.add(bit_length);
				     
				     System.out.println("Using big integers.");
				     //System.out.println("Bit length from pack code is " + bit_length);
				     System.out.println();
			    }
			    else
			    {
			    	long [] code   = DeltaMapper.getUnaryCode(n);
			    	
			    	int bit_length =  DeltaMapper.packCode(delta, string_table, code, length, string2);
			    	channel_string_length.add(bit_length);
			    	
			    	System.out.println("Using longs.");
			    	//System.out.println("Bit length from pack code is " + bit_length);
			    	System.out.println();
			    	
			    }
			    
			    channel_data.add(string2);
			}
		 
			
			/*
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
			*/

			if(min_set_id == 0)
			{
				byte [] string       = (byte [])channel_data.get(0);
				int  [] string_table = (int [])channel_table.get(0);
				int  string_length   = (int)channel_string_length.get(0);
				int  [] delta        =  new int[xdim * ydim];
				
				int n = string_table.length;
				int [] length = DeltaMapper.getUnaryLength(n);
				long [] code  = DeltaMapper.getUnaryCode(n);
				
				//int number_unpacked  = DeltaMapper.unpackStrings2(string, string_table, delta);
				int number_unpacked  =  DeltaMapper.unpackCode(string, string_table, code, length, string_length, delta);
				for(int j = 1; j < delta.length; j++)
				     delta[j] += channel_delta_min[0];
			    shifted_blue = DeltaMapper.getValuesFromDeltas3(delta, xdim , ydim, channel_init[0]);
			    
			    string          = (byte [])channel_data.get(1);
				string_table    = (int [])channel_table.get(1);
				
				number_unpacked = DeltaMapper.unpackStrings2(string, string_table, delta);
				for(int j = 1; j < delta.length; j++)
				     delta[j] += channel_delta_min[1];
			    shifted_green = DeltaMapper.getValuesFromDeltas3(delta, xdim , ydim, channel_init[1]);
			    
			    string          = (byte [])channel_data.get(2);
				string_table    = (int [])channel_table.get(2);
				
				number_unpacked = DeltaMapper.unpackStrings2(string, string_table, delta);
				for(int j = 1; j < delta.length; j++)
				     delta[j] += channel_delta_min[2];
			    shifted_red = DeltaMapper.getValuesFromDeltas3(delta, xdim , ydim, channel_init[2]);
			}
			else if(min_set_id == 1)
			{
				byte [] string       = (byte [])channel_data.get(0);
				int  [] string_table = (int [])channel_table.get(0);
				int  [] delta        =  new int[xdim * ydim];
				int number_unpacked  = DeltaMapper.unpackStrings2(string, string_table, delta);
				for(int j = 1; j < delta.length; j++)
				     delta[j] += channel_delta_min[0];
			    shifted_blue = DeltaMapper.getValuesFromDeltas3(delta, xdim , ydim, channel_init[0]);
			    
			    string          = (byte [])channel_data.get(1);
				string_table    = (int [])channel_table.get(1);
				number_unpacked = DeltaMapper.unpackStrings2(string, string_table, delta);
				for(int j = 1; j < delta.length; j++)
				     delta[j] += channel_delta_min[2];
			    shifted_red = DeltaMapper.getValuesFromDeltas3(delta, xdim , ydim, channel_init[2]);
			  
			    string          = (byte [])channel_data.get(2);
				string_table    = (int [])channel_table.get(2);
				number_unpacked = DeltaMapper.unpackStrings2(string, string_table, delta);
				for(int j = 1; j < delta.length; j++)
				     delta[j] += channel_delta_min[4];
			    shifted_red_green = DeltaMapper.getValuesFromDeltas3(delta, xdim , ydim, channel_init[4]);
			    for(int j = 0; j < shifted_blue_green.length; j++)
			    {
			    	shifted_red_green[j] += channel_min[4];
			    	shifted_red_green[j] = -shifted_red_green[j];
			    }
			    shifted_green = DeltaMapper.getSum(shifted_red, shifted_red_green);
			}
			else if(min_set_id == 2)
			{
				byte [] string       = (byte [])channel_data.get(0);
				int  [] string_table = (int [])channel_table.get(0);
				int  [] delta        =  new int[xdim * ydim];
				int number_unpacked  = DeltaMapper.unpackStrings2(string, string_table, delta);
				for(int j = 1; j < delta.length; j++)
				     delta[j] += channel_delta_min[0];
			    shifted_blue = DeltaMapper.getValuesFromDeltas3(delta, xdim , ydim, channel_init[0]);
			    
			    string          = (byte [])channel_data.get(1);
				string_table    = (int [])channel_table.get(1);
				number_unpacked = DeltaMapper.unpackStrings2(string, string_table, delta);
				for(int j = 1; j < delta.length; j++)
				     delta[j] += channel_delta_min[2];
			    shifted_red = DeltaMapper.getValuesFromDeltas3(delta, xdim , ydim, channel_init[2]);
			  
			    string          = (byte [])channel_data.get(2);
				string_table    = (int [])channel_table.get(2);
				number_unpacked = DeltaMapper.unpackStrings2(string, string_table, delta);
				for(int j = 1; j < delta.length; j++)
				     delta[j] += channel_delta_min[3];
			    shifted_blue_green = DeltaMapper.getValuesFromDeltas3(delta, xdim , ydim, channel_init[3]);
			    for(int j = 0; j < shifted_blue_green.length; j++)
			    {
			    	shifted_blue_green[j] += channel_min[3];
			    	shifted_blue_green[j] = -shifted_blue_green[j];
			    }
			    shifted_green = DeltaMapper.getSum(shifted_blue, shifted_blue_green);
			}
			else if(min_set_id == 3)
			{
				byte [] string       = (byte [])channel_data.get(0);
				int  [] string_table = (int [])channel_table.get(0);
				int  [] delta        =  new int[xdim * ydim];
				int number_unpacked  = DeltaMapper.unpackStrings2(string, string_table, delta);
				for(int j = 1; j < delta.length; j++)
				     delta[j] += channel_delta_min[0];
			    shifted_blue = DeltaMapper.getValuesFromDeltas3(delta, xdim , ydim, channel_init[0]);
			    
			    string          = (byte [])channel_data.get(1);
				string_table    = (int [])channel_table.get(1);	
				number_unpacked = DeltaMapper.unpackStrings2(string, string_table, delta);
				for(int j = 1; j < delta.length; j++)
				     delta[j] += channel_delta_min[3];
			    shifted_blue_green = DeltaMapper.getValuesFromDeltas3(delta, xdim , ydim, channel_init[3]);
			    for(int j = 0; j < shifted_blue_green.length; j++)
			    {
			    	shifted_blue_green[j] += channel_min[3];
			    	shifted_blue_green[j]  = -shifted_blue_green[j]; 
			    }
			    shifted_green = DeltaMapper.getSum(shifted_blue_green, shifted_blue);
			    
			    string          = (byte [])channel_data.get(2);
				string_table    = (int [])channel_table.get(2);
				number_unpacked = DeltaMapper.unpackStrings2(string, string_table, delta);
				for(int j = 1; j < delta.length; j++)
				     delta[j] += channel_delta_min[4];
			    shifted_red_green = DeltaMapper.getValuesFromDeltas3(delta, xdim , ydim, channel_init[4]);
			    for(int j = 0; j < shifted_blue_green.length; j++)
			    	shifted_red_green[j] += channel_min[4];
			    shifted_red = DeltaMapper.getSum(shifted_red_green, shifted_green);	
			}
			else if(min_set_id == 4)
			{
				byte [] string       = (byte [])channel_data.get(0);
				int  [] string_table = (int [])channel_table.get(0);
				int  [] delta        =  new int[xdim * ydim];
				int number_unpacked  = DeltaMapper.unpackStrings2(string, string_table, delta);
				for(int j = 1; j < delta.length; j++)
				     delta[j] += channel_delta_min[0];
			    shifted_blue = DeltaMapper.getValuesFromDeltas3(delta, xdim , ydim, channel_init[0]);
			    
			    string          = (byte [])channel_data.get(1);
				string_table    = (int [])channel_table.get(1);	
				number_unpacked = DeltaMapper.unpackStrings2(string, string_table, delta);
				for(int j = 1; j < delta.length; j++)
				     delta[j] += channel_delta_min[3];
			    shifted_blue_green = DeltaMapper.getValuesFromDeltas3(delta, xdim , ydim, channel_init[3]);
			    for(int j = 0; j < shifted_blue_green.length; j++)
			    {
			    	shifted_blue_green[j] += channel_min[3];
			    	shifted_blue_green[j]  = -shifted_blue_green[j]; 
			    }
			    shifted_green = DeltaMapper.getSum(shifted_blue, shifted_blue_green);
			    
			    string          = (byte [])channel_data.get(2);
				string_table    = (int [])channel_table.get(2);
				number_unpacked = DeltaMapper.unpackStrings2(string, string_table, delta);
				for(int j = 1; j < delta.length; j++)
				     delta[j] += channel_delta_min[5];
			    shifted_red_blue = DeltaMapper.getValuesFromDeltas3(delta, xdim , ydim, channel_init[5]);
			    for(int j = 0; j < shifted_blue_green.length; j++)
			    	shifted_red_blue[j] += channel_min[5];
			    shifted_red = DeltaMapper.getSum(shifted_blue, shifted_red_blue);
			}
			else if(min_set_id == 5)
			{
				byte [] string       = (byte [])channel_data.get(0);
				int  [] string_table = (int [])channel_table.get(0);
				int  [] delta        =  new int[xdim * ydim];
				int number_unpacked  = DeltaMapper.unpackStrings2(string, string_table, delta);
				for(int j = 1; j < delta.length; j++)
				     delta[j] += channel_delta_min[1];
			    shifted_green = DeltaMapper.getValuesFromDeltas3(delta, xdim , ydim, channel_init[1]);
			    
			    string          = (byte [])channel_data.get(1);
				string_table    = (int [])channel_table.get(1);	
				number_unpacked = DeltaMapper.unpackStrings2(string, string_table, delta);
				for(int j = 1; j < delta.length; j++)
				     delta[j] += channel_delta_min[2];
			    shifted_red = DeltaMapper.getValuesFromDeltas3(delta, xdim , ydim, channel_init[2]);
			    
			    string          = (byte [])channel_data.get(2);
				string_table    = (int [])channel_table.get(2);
				number_unpacked = DeltaMapper.unpackStrings2(string, string_table, delta);
				for(int j = 1; j < delta.length; j++)
				     delta[j] += channel_delta_min[3];
			    shifted_blue_green = DeltaMapper.getValuesFromDeltas3(delta, xdim , ydim, channel_init[3]);
			    for(int j = 0; j < shifted_blue_green.length; j++)
			    	shifted_blue_green[j] += channel_min[3];
			    shifted_blue = DeltaMapper.getSum(shifted_blue_green, shifted_green);	
			}
			else if(min_set_id == 6)
			{
				byte [] string       = (byte [])channel_data.get(0);
				int  [] string_table = (int [])channel_table.get(0);
				int  [] delta        =  new int[xdim * ydim];
				int number_unpacked  = DeltaMapper.unpackStrings2(string, string_table, delta);
				for(int j = 1; j < delta.length; j++)
				     delta[j] += channel_delta_min[2];
			    shifted_red = DeltaMapper.getValuesFromDeltas3(delta, xdim , ydim, channel_init[2]);
			    
			    string          = (byte [])channel_data.get(1);
				string_table    = (int [])channel_table.get(1);	
				number_unpacked = DeltaMapper.unpackStrings2(string, string_table, delta);
				for(int j = 1; j < delta.length; j++)
				     delta[j] += channel_delta_min[3];
			    shifted_blue_green = DeltaMapper.getValuesFromDeltas3(delta, xdim , ydim, channel_init[3]);
			    for(int j = 0; j < shifted_blue_green.length; j++)
			    	shifted_blue_green[j] += channel_min[3];
			    
			    string          = (byte [])channel_data.get(2);
				string_table    = (int [])channel_table.get(2);
				number_unpacked = DeltaMapper.unpackStrings2(string, string_table, delta);
				for(int j = 1; j < delta.length; j++)
				     delta[j] += channel_delta_min[4];
			    shifted_red_green = DeltaMapper.getValuesFromDeltas3(delta, xdim , ydim, channel_init[4]);
			    for(int j = 0; j < shifted_blue_green.length; j++)
			    	shifted_red_green[j] += channel_min[4];
			    for(int j = 0; j < shifted_red_green.length; j++)
			    	shifted_red_green[j] = -shifted_red_green[j];
			    
			    shifted_green = DeltaMapper.getSum(shifted_red_green, shifted_red);
			    shifted_blue = DeltaMapper.getSum(shifted_blue_green, shifted_green);	
			}
			else if(min_set_id == 7)
			{
				byte [] string       = (byte [])channel_data.get(0);
				int  [] string_table = (int [])channel_table.get(0);
				int  [] delta        =  new int[xdim * ydim];
				int number_unpacked  = DeltaMapper.unpackStrings2(string, string_table, delta);
				for(int j = 1; j < delta.length; j++)
				     delta[j] += channel_delta_min[1];
			    shifted_green = DeltaMapper.getValuesFromDeltas3(delta, xdim , ydim, channel_init[1]);
			    
			    string          = (byte [])channel_data.get(1);
				string_table    = (int [])channel_table.get(1);	
				number_unpacked = DeltaMapper.unpackStrings2(string, string_table, delta);
				for(int j = 1; j < delta.length; j++)
				     delta[j] += channel_delta_min[3];
			    shifted_blue_green = DeltaMapper.getValuesFromDeltas3(delta, xdim , ydim, channel_init[3]);
			    for(int j = 0; j < shifted_blue_green.length; j++)
			    	shifted_blue_green[j] += channel_min[3];
			    shifted_blue = DeltaMapper.getSum(shifted_green, shifted_blue_green);
			    
			    string          = (byte [])channel_data.get(2);
				string_table    = (int [])channel_table.get(2);
				number_unpacked = DeltaMapper.unpackStrings2(string, string_table, delta);
				for(int j = 1; j < delta.length; j++)
				     delta[j] += channel_delta_min[4];
			    shifted_red_green = DeltaMapper.getValuesFromDeltas3(delta, xdim , ydim, channel_init[4]);
			    for(int j = 0; j < shifted_red_green.length; j++)
			    	shifted_red_green[j] += channel_min[4];
			    shifted_red  = DeltaMapper.getSum(shifted_green, shifted_red_green);
			}
			else if(min_set_id == 8)
			{
				byte [] string       = (byte [])channel_data.get(0);
				int  [] string_table = (int [])channel_table.get(0);
				int  [] delta        =  new int[xdim * ydim];
				int number_unpacked  = DeltaMapper.unpackStrings2(string, string_table, delta);
				for(int j = 1; j < delta.length; j++)
				     delta[j] += channel_delta_min[1];
			    shifted_green = DeltaMapper.getValuesFromDeltas3(delta, xdim , ydim, channel_init[1]);
			    
			    string          = (byte [])channel_data.get(1);
				string_table    = (int [])channel_table.get(1);
				number_unpacked = DeltaMapper.unpackStrings2(string, string_table, delta);
				for(int j = 1; j < delta.length; j++)
				     delta[j] += channel_delta_min[4];
			    shifted_red_green = DeltaMapper.getValuesFromDeltas3(delta, xdim , ydim, channel_init[4]);
			    for(int j = 0; j < shifted_red_green.length; j++)
			    	shifted_red_green[j] += channel_min[4];
			    shifted_red  = DeltaMapper.getSum(shifted_green, shifted_red_green);
			    

			    string          = (byte [])channel_data.get(2);
				string_table    = (int [])channel_table.get(2);
				number_unpacked = DeltaMapper.unpackStrings2(string, string_table, delta);
				for(int j = 1; j < delta.length; j++)
				     delta[j] += channel_delta_min[5];
			    shifted_red_blue = DeltaMapper.getValuesFromDeltas3(delta, xdim , ydim, channel_init[5]);
			    for(int j = 0; j < shifted_red_blue.length; j++)
			    {
			    	shifted_red_blue[j] += channel_min[5];
			    	shifted_red_blue[j] = -shifted_red_blue[j];
			    }
			    shifted_blue  = DeltaMapper.getSum(shifted_red, shifted_red_blue);
			}
			else if(min_set_id == 9)
			{
				byte [] string       = (byte [])channel_data.get(0);
				int  [] string_table = (int [])channel_table.get(0);
				int  [] delta        =  new int[xdim * ydim];
				int number_unpacked  = DeltaMapper.unpackStrings2(string, string_table, delta);
				for(int j = 1; j < delta.length; j++)
				     delta[j] += channel_delta_min[2];
			    shifted_red = DeltaMapper.getValuesFromDeltas3(delta, xdim , ydim, channel_init[2]);
			    
			    string          = (byte [])channel_data.get(1);
				string_table    = (int [])channel_table.get(1);
				number_unpacked = DeltaMapper.unpackStrings2(string, string_table, delta);
				for(int j = 1; j < delta.length; j++)
				     delta[j] += channel_delta_min[4];
			    shifted_red_green = DeltaMapper.getValuesFromDeltas3(delta, xdim , ydim, channel_init[4]);
			    for(int j = 0; j < shifted_red_green.length; j++)
			    {
			    	shifted_red_green[j] += channel_min[4];
			    	shifted_red_green[j] = -shifted_red_green[j];
			    }
			    shifted_green = DeltaMapper.getSum(shifted_red, shifted_red_green);
			    
			    string          = (byte [])channel_data.get(2);
				string_table    = (int [])channel_table.get(2);	
				number_unpacked = DeltaMapper.unpackStrings2(string, string_table, delta);
				for(int j = 1; j < delta.length; j++)
				     delta[j] += channel_delta_min[5];
			    shifted_red_blue = DeltaMapper.getValuesFromDeltas3(delta, xdim , ydim, channel_init[5]);
			    for(int j = 0; j < shifted_blue_green.length; j++)
			    {	
			    	shifted_red_blue[j] += channel_min[5];
			    	shifted_red_blue[j] = -shifted_red_blue[j];
			    }
			    shifted_blue = DeltaMapper.getSum(shifted_red, shifted_red_blue);
			}
			
			
			
			
			
			
			
			
			
			
			
			/*
			
			int  [] delta0 =  new int[xdim * ydim];
			int  [] delta1 =  new int[xdim * ydim];
			int  [] delta2 =  new int[xdim * ydim];
			
			byte [] string       = (byte [])channel_data.get(0);
			int  [] string_table = (int [])channel_table.get(0);
			int number_unpacked  = DeltaMapper.unpackStrings2(string, string_table, delta0);
			for(int j = 1; j < delta0.length; j++)
			     delta0[j] += channel_delta_min[0];
			delta0[0] = 0;
			
			string       = (byte [])channel_data.get(1);
			string_table = (int [])channel_table.get(1);
			number_unpacked  = DeltaMapper.unpackStrings2(string, string_table, delta1);
			for(int j = 1; j < delta1.length; j++)
			     delta1[j] += channel_delta_min[1];
			delta1[0] = 0;
			
			string       = (byte [])channel_data.get(2);
			string_table = (int [])channel_table.get(2);
			number_unpacked  = DeltaMapper.unpackStrings2(string, string_table, delta2);
			for(int j = 1; j < delta1.length; j++)
			     delta2[j] += channel_delta_min[2];
			delta2[0] = 0;
			
			
			if(min_set_id == 0)
			{
			    shifted_blue = DeltaMapper.getValuesFromDeltas3(delta0, xdim , ydim, channel_init[0]);
			    
			    shifted_green = DeltaMapper.getValuesFromDeltas3(delta1, xdim , ydim, channel_init[1]);
			    
			    shifted_red = DeltaMapper.getValuesFromDeltas3(delta2, xdim , ydim, channel_init[2]);
			}
			else if(min_set_id == 1)
			{
			    shifted_blue = DeltaMapper.getValuesFromDeltas3(delta0, xdim , ydim, channel_init[0]);
			    
			    shifted_red = DeltaMapper.getValuesFromDeltas3(delta1, xdim , ydim, channel_init[2]);
			  
			    shifted_red_green = DeltaMapper.getValuesFromDeltas3(delta2, xdim , ydim, channel_init[4]);
			    
			    for(int j = 0; j < shifted_blue_green.length; j++)
			    {
			    	shifted_red_green[j] += channel_min[4];
			    	shifted_red_green[j] = -shifted_red_green[j];
			    }
			    
			    shifted_green = DeltaMapper.getSum(shifted_red, shifted_red_green);
			}
			else if(min_set_id == 2)
			{
				
			    shifted_blue = DeltaMapper.getValuesFromDeltas3(delta0, xdim , ydim, channel_init[0]);
			    
			    shifted_red = DeltaMapper.getValuesFromDeltas3(delta1, xdim , ydim, channel_init[2]);
			  
			    shifted_blue_green = DeltaMapper.getValuesFromDeltas3(delta2, xdim , ydim, channel_init[4]);
			    for(int j = 0; j < shifted_blue_green.length; j++)
			    {
			    	shifted_blue_green[j] += channel_min[4];
			    	shifted_blue_green[j] = -shifted_blue_green[j];
			    }
			    shifted_green = DeltaMapper.getSum(shifted_blue, shifted_blue_green);
			}
			else if(min_set_id == 3)
			{
				
			    shifted_blue = DeltaMapper.getValuesFromDeltas3(delta0, xdim , ydim, channel_init[0]);
			    
			   
			    shifted_blue_green = DeltaMapper.getValuesFromDeltas3(delta1, xdim , ydim, channel_init[3]);
			    for(int j = 0; j < shifted_blue_green.length; j++)
			    {
			    	shifted_blue_green[j] += channel_min[3];
			    	shifted_blue_green[j]  = -shifted_blue_green[j]; 
			    }
			    shifted_green = DeltaMapper.getSum(shifted_blue_green, shifted_blue);
			    
			   
			    shifted_red_green = DeltaMapper.getValuesFromDeltas3(delta2, xdim , ydim, channel_init[4]);
			    for(int j = 0; j < shifted_blue_green.length; j++)
			    	shifted_red_green[j] += channel_min[4];
			    shifted_red = DeltaMapper.getSum(shifted_red_green, shifted_green);	
			}
			else if(min_set_id == 4)
			{
			    shifted_blue = DeltaMapper.getValuesFromDeltas3(delta0, xdim , ydim, channel_init[0]);
			    
			    shifted_blue_green = DeltaMapper.getValuesFromDeltas3(delta1, xdim , ydim, channel_init[3]);
			    
			    for(int j = 0; j < shifted_blue_green.length; j++)
			    {
			    	shifted_blue_green[j] += channel_min[3];
			    	shifted_blue_green[j]  = -shifted_blue_green[j]; 
			    }
			    shifted_green = DeltaMapper.getSum(shifted_blue, shifted_blue_green);
			    
			
			    shifted_red_blue = DeltaMapper.getValuesFromDeltas3(delta2, xdim , ydim, channel_init[5]);
			    
			    for(int j = 0; j < shifted_blue_green.length; j++)
			    	shifted_red_blue[j] += channel_min[5];
			    shifted_red = DeltaMapper.getSum(shifted_blue, shifted_red_blue);
			}
			else if(min_set_id == 5)
			{
			    shifted_green = DeltaMapper.getValuesFromDeltas3(delta0, xdim , ydim, channel_init[1]);
			    
			    shifted_red = DeltaMapper.getValuesFromDeltas3(delta1, xdim , ydim, channel_init[2]);
			    
			    shifted_blue_green = DeltaMapper.getValuesFromDeltas3(delta2, xdim , ydim, channel_init[3]);
			    
			    for(int j = 0; j < shifted_blue_green.length; j++)
			    	shifted_blue_green[j] += channel_min[3];
			    shifted_blue = DeltaMapper.getSum(shifted_blue_green, shifted_green);	
			}
			else if(min_set_id == 6)
			{
			    shifted_red = DeltaMapper.getValuesFromDeltas3(delta0, xdim , ydim, channel_init[2]);
			    
			    shifted_blue_green = DeltaMapper.getValuesFromDeltas3(delta1, xdim , ydim, channel_init[3]);
			    for(int j = 0; j < shifted_blue_green.length; j++)
			    	shifted_blue_green[j] += channel_min[3];
			    
			    shifted_red_green = DeltaMapper.getValuesFromDeltas3(delta2, xdim , ydim, channel_init[4]);
			    for(int j = 0; j < shifted_blue_green.length; j++)
			    	shifted_red_green[j] += channel_min[4];
			    for(int j = 0; j < shifted_red_green.length; j++)
			    	shifted_red_green[j] = -shifted_red_green[j];
			    
			    shifted_green = DeltaMapper.getSum(shifted_red_green, shifted_red);
			    shifted_blue = DeltaMapper.getSum(shifted_blue_green, shifted_green);	
			}
			else if(min_set_id == 7)
			{
			    shifted_green = DeltaMapper.getValuesFromDeltas3(delta0, xdim , ydim, channel_init[1]);
			    
			    shifted_blue_green = DeltaMapper.getValuesFromDeltas3(delta1, xdim , ydim, channel_init[3]);
			    for(int j = 0; j < shifted_blue_green.length; j++)
			    	shifted_blue_green[j] += channel_min[3];
			    
			    shifted_blue = DeltaMapper.getSum(shifted_green, shifted_blue_green);
			    
			    shifted_red_green = DeltaMapper.getValuesFromDeltas3(delta2, xdim , ydim, channel_init[4]);
			    for(int j = 0; j < shifted_red_green.length; j++)
			    	shifted_red_green[j] += channel_min[4];
			    shifted_red  = DeltaMapper.getSum(shifted_green, shifted_red_green);
			}
			else if(min_set_id == 8)
			{
                shifted_green = DeltaMapper.getValuesFromDeltas3(delta0, xdim , ydim, channel_init[1]);
			    
                shifted_red_green = DeltaMapper.getValuesFromDeltas3(delta1, xdim , ydim, channel_init[4]);
			    for(int j = 0; j < shifted_red_green.length; j++)
			    	shifted_red_green[j] += channel_min[4];
			    shifted_red  = DeltaMapper.getSum(shifted_red_green, shifted_green);
			    
			    shifted_red_blue = DeltaMapper.getValuesFromDeltas3(delta2, xdim , ydim, channel_init[5]);
			    for(int j = 0; j < shifted_red_blue.length; j++)
			    {
			    	shifted_red_blue[j] += channel_min[5];
			    	shifted_red_blue[j] = -shifted_red_blue[j];
			    }
			    shifted_blue  = DeltaMapper.getSum(shifted_red_blue, shifted_red_blue);
			}
			else if(min_set_id == 9)
			{
			    shifted_red = DeltaMapper.getValuesFromDeltas3(delta0, xdim , ydim, channel_init[2]);
			    
			    shifted_red_green = DeltaMapper.getValuesFromDeltas3(delta1, xdim , ydim, channel_init[4]);
			    for(int j = 0; j < shifted_red_green.length; j++)
			    {
			    	shifted_red_green[j] += channel_min[4];
			    	shifted_red_green[j] = -shifted_red_green[j];
			    }
			    shifted_green = DeltaMapper.getSum(shifted_red, shifted_red_green);
			    
			    shifted_red_blue = DeltaMapper.getValuesFromDeltas3(delta2, xdim , ydim, channel_init[5]);
			    for(int j = 0; j < shifted_blue_green.length; j++)
			    {	
			    	shifted_red_blue[j] += channel_min[5];
			    	shifted_red_blue[j] = -shifted_red_blue[j];
			    }
			    shifted_blue = DeltaMapper.getSum(shifted_red, shifted_red_blue);
			}
			 */
			
		    for(int i = 0; i < xdim * ydim; i++)
			{
		    	shifted_blue[i]  <<= pixel_shift;
		        shifted_green[i] <<= pixel_shift;
                shifted_red[i]   <<= pixel_shift;
                
                new_pixel[i]  = 0;
	            new_pixel[i] |= shifted_blue[i] << 16;
	            new_pixel[i] |= shifted_green[i] << 8;    
	            new_pixel[i] |= shifted_red[i];
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
}