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

public class TestStringInformation
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
	
	byte   [] channel_compression_type, channel_bit_type;
	int    [] channel_init, channel_min;
	
	int    [] set_sum, channel_sum,channel_delta_min;
	double [] set_rate, channel_rate;
	String [] type_string;
	String [] set_string;
	String [] channel_string;
	int    [] shifted_blue, shifted_green, shifted_red;
	int    [] shifted_blue_green, shifted_red_green, shifted_red_blue;
	
	int    [][] compression_length;
	
	ArrayList channel_table, channel_map, channel_data, channel_src, channel_histogram_length;
	ArrayList delta_map, delta_histogram_length;
	
	double  file_ratio;
	int     min_set_id = 0;
	int     pixel_shift = 3;
	long    file_length = 0;
	boolean initialized = false;
	
	public static void main(String[] args)
	{
		if (args.length != 1)
		{
			System.out.println("Usage: java TestStringInformation <filename>");
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
		TestStringInformation encoder = new TestStringInformation(prefix + filename);
	}

	public TestStringInformation(String _filename)
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
               
			    JFrame frame = new JFrame("TestStringInformation");
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
				frame.setLocation(10, 200);
				frame.setVisible(true);
			} 
			else
			{
				System.out.println("File raster type not supported.");
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
		    
		   
		    // The 6 different kinds of compressions we intend to evaluate per channel.
		    // Huffman encoding usually offers some amount of compression unless
		    // the blocks are so small the extra overhead becomes significant.
		    type_string    = new String[7];
			type_string[0] = new String("strings");
			type_string[1] = new String("zipped strings");
			type_string[2] = new String("compressed strings");
			type_string[3] = new String("zipped compressed strings");
			type_string[4] = new String("zipped delta bytes");
			type_string[5] = new String("zipped pixel bytes");
			// If a frame is divided into blocks, there 
			// might be multiple types of compression.
			type_string[6] = new String("mixed");
		  
		    // The delta sum for each channel.
		    channel_sum  = new int[6];
		    
		    // The different minimum compression rates per channel.
		    channel_rate = new double[6];
		    
		    // The type of compression that produces the minimum result.
		    channel_compression_type = new byte[6];
		    
		    // What type of string being compressed.
		    channel_bit_type = new byte[6];
		    
		    // The lengths of the bit string produced for each channel by each method of compression.
		    compression_length = new int[6][6];
		    
		    // The look up tables used to pack/unpack strings.
		    channel_table = new ArrayList();
		    
		    // Map used to remove histogram holes.
		    channel_map = new ArrayList();
		    delta_map = new ArrayList();
		    
		    // Different compression results.
		    channel_data = new ArrayList();
		    
		    // Original pixels for each channel.
		    channel_src  = new ArrayList();
		    
		    // The decoder needs this to see whether values were remapped to remove histogram holes.
		    channel_histogram_length = new ArrayList();
		    delta_histogram_length = new ArrayList();
		   
		    for(int i = 0; i < 6; i++)
		    {
		    	ArrayList data_list = new ArrayList();
		    	channel_data.add(data_list);
		    	channel_bit_type[i] = 0;
		    }
		    
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
		    channel_map.clear();
		    delta_map.clear();
		    channel_histogram_length.clear();
		    delta_histogram_length.clear();
		    
		    for(int i = 0; i < 6; i++)
		    {
		    	ArrayList data_list = (ArrayList)channel_data.get(i);
		    	data_list.clear();
		    }
			
			System.out.println("Pixel shift is " + pixel_shift);
			
		    //System.out.println("File compression rate is " + String.format("%.4f", file_ratio));
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
		    
		    byte [] delta_string    = new byte[xdim * ydim * 8];
		    byte [] sign_string     = new byte[xdim * ydim];
		    byte [] direction_string = new byte[xdim * ydim];
		    
		  
		    for(int i = 0; i < 6; i++)
		    {
		    	int [] src = (int [])channel_src.get(i);
		    	int number_of_errors, min_value;
		    	ArrayList histogram_list;
		    	int [] histogram, rank_table, frequency, length, code;
		    	int n;
		    	double ratio;
			    
		    	System.out.println("Processing " + channel_string[i] + " channel.");
		    	System.out.println();
		    
		    	ArrayList list3 = DeltaMapper.getDeltasFromValues3(src, xdim, ydim);
		    	int        sum3 = (int)list3.get(0);
		    	int []   delta3 = (int [])list3.get(1);
		    	//System.out.println("Sum returned with paeth deltas is " + sum3);
		    	histogram_list  = DeltaMapper.getHistogram(delta3);
				min_value       = (int)histogram_list.get(0);
				histogram       = (int[])histogram_list.get(1);
				rank_table      = DeltaMapper.getRankTable(histogram);
				
				for(int j = 1; j < delta3.length; j++)
					delta3[j] -= min_value;
				int length3  = DeltaMapper.packStrings2(delta3, rank_table, delta_string);
				
				
				double zero_one_ratio = xdim * ydim;
		        if(histogram.length > 1)
		        {
					min_value = Integer.MAX_VALUE;
					for(int j = 0; j < histogram.length; j++)
						 if(histogram[j] < min_value)
							min_value = histogram[j];
					zero_one_ratio -= min_value;
		        }	
			    zero_one_ratio  /= length3;
			    
			    System.out.println("The zero ratio calculated from the string length is " + String.format("%.4f", zero_one_ratio));
				

				ArrayList info        = DeltaMapper.getStringInformation2(delta_string, length3);
				ArrayList zero_list   = (ArrayList)info.get(0);
				ArrayList one_list    = (ArrayList)info.get(1);
				ArrayList s_position  = (ArrayList)info.get(2);
				ArrayList s_type      = (ArrayList)info.get(3);
				ArrayList s_length    = (ArrayList)info.get(4);
				System.out.println("The position list has size " + s_position.size());
				System.out.println("The type list has size " + s_type.size());
				System.out.println("The length list has size " + s_length.size());
				
				int number_of_zero_bits = 0;
				int number_of_one_bits  = 0;
				
				int number_of_2_strings = 0;
				int number_of_3_strings = 0;
				n = s_length.size();
				for(int j = 0; j < n; j++)
				{
					int current_type = (int)s_type.get(j);
					int current_length = (int)s_length.get(j);
					
					if(current_type == 0)
					{
						number_of_zero_bits += current_length - 1;
						number_of_one_bits++;
					}
					else if(current_type == 1)
					{
						number_of_one_bits += current_length - 1;
						number_of_zero_bits++;
					}
					else if(current_type == 2)
					{
						number_of_zero_bits += current_length;
						number_of_2_strings++;
					}
					else if(current_type == 3)
					{
						number_of_one_bits += current_length;
						number_of_3_strings++;
					}
				}
				
				System.out.println("There were " + number_of_2_strings + " zero strings with no stop bit.");
				System.out.println("There were " + number_of_3_strings + " one strings with no stop bit.");
				
				
				zero_one_ratio = number_of_zero_bits;
				zero_one_ratio       /= number_of_zero_bits + number_of_one_bits;
				System.out.println("The zero ratio calculated from the string information is " + String.format("%.4f", zero_one_ratio));
				
				
			    byte [] compressed_string = new byte[xdim * ydim * 8];
			    int compression_length = 0;
			    
			    
		    	ArrayList t_length = new ArrayList();
		    	ArrayList t_type   = new ArrayList();
		    	for(int j = 0; j < n - 2; j++)
		    	{
		    		int current_type   = (int)s_type.get(j);
		    		int current_length = (int)s_length.get(j);
		    		
		    		if(current_type == 0)
		    		{
		    		    int compressed_length = (current_length - 1) / 2 + 2;	
		    		    t_length.add(compressed_length);
		    		    t_type.add(0);
		    		}
		    		else if(current_type == 1)
		    		{
		    			int compressed_length = (current_length - 1) * 2 + 1;	
		    			t_length.add(compressed_length);
		    		    t_type.add(1);
		    		    int next_type = (int)s_type.get(j + 1);
		    		    if(next_type == 0)
		    		    {
		    		    	t_length.add(1);
		    		    	t_type.add(1);
		    		    }
		    		    else if(next_type == 1)
		    		    {
		    		    	t_length.add(0); 
		    		    	t_type.add(0);
		    		    }
		    		}
		    		else
		    			System.out.println("Unexpected type.");
		    	}
			    
		    	// We're neglecting the last two elements in the list so
		    	// we don't have to deal with the end conditions.
		    	System.out.println("Original list has size " + s_length.size());
		    	
		    	int predicted_length2 = 0;
			    System.out.println("Transformed list has size " + t_length.size());
			    n = t_length.size();
			    for(int j = 0; j < n; j++)
			    {
			        int current_length = (int) t_length.get(j);	
			        predicted_length2 += current_length;
			    }
			    
			    
			    
			    
			    
			    
			    
			    
			    if(zero_one_ratio > .5)
			    {
			    	int predicted_length = 0;
			    	n = s_length.size();
			    	for(int j = 0; j < n - 2; j++)
			    	{
			    		int current_type = (int)s_type.get(j);
			    		int current_length = (int)s_length.get(j);
			    		if(current_type == 0)
			    			predicted_length += (current_length - 1) / 2 + 2;
			    		else
			    		{
			    			predicted_length += (current_length - 1) * 2;
			    			
			    		    // We end up with a trailing run bit and need to check the next string type.
			    			int next_type  = ((int)s_type.get(j + 1));
			    			if(next_type == 0)
			    				predicted_length++;
			    			else
			    				predicted_length += 2;
			    			int this_length = (int)s_length.get(j);
			    			this_length++;
			    			s_length.set(j, this_length);
			    			s_type.set(j, 4);
			    			int next_length = (int)s_length.get(j + 1);
		    				next_length--;
		    				s_length.set(j + 1, next_length);
			    		}
			    	}
			    	
			    	int next_to_last_type = (int)s_type.get(n - 2);
			    	int next_to_last_length    = (int)s_length.get(n - 2);
			    	if(next_to_last_type == 0)
			    		predicted_length += (next_to_last_length - 1) / 2 + 2;
			    	else
			    	{
			    		predicted_length += (next_to_last_length - 1) * 2;	
			    		int next_type  = ((int)s_type.get(n - 1));
		    			if(next_type == 0 || next_type == 2)
		    				predicted_length++;
		    			else
		    				predicted_length += 2;
		    			int this_length = (int)s_length.get(n - 2);
		    			this_length++;
		    			s_length.set(n - 2, this_length);
		    			s_type.set(n - 2, 4);
		    			int next_length = (int)s_length.get(n - 1);
	    				next_length--;
	    				s_length.set(n - 1, next_length);
			    	}
			    	
			    	int last_type   = (int)s_type.get(n - 1);
			    	int last_length = (int)s_length.get(n - 1);
			    	if(last_length > 0)
			    	{
			    	    if(last_type == 0)
			    	    {
			    	    	predicted_length += (last_length - 1) / 2 + 2;    	
			    	    }
			    	    else if(last_type == 1)
			    	    {
			    	    	predicted_length += (last_length - 1) * 2 + 1;	
			    	    }
			    	    else if(last_type == 2)
			    	    {
			    	        predicted_length += last_length / 2;
			    	    }
			    	    else if(last_type == 3)
			    	    {
			    	    	predicted_length += last_length * 2;
			    	    }
			    	}
			    	
			    	
			    	
					compression_length = DeltaMapper.compressZeroStrings(delta_string, length3, compressed_string);
					
					System.out.println("Predicted length of bit string is " + predicted_length2);
					System.out.println("Actual length of bit string is " + compression_length);
			    }
			    else
			    {
                    int predicted_length = 0;
			    	n = s_length.size();
			    	for(int j = 0; j < n - 2; j++)
			    	{
			    		int current_type = (int)s_type.get(j);
			    		int current_length = (int)s_length.get(j);
			    		if(current_type == 1)
			    			predicted_length += (current_length - 1) / 2 + 2;
			    		else
			    		{
			    			predicted_length += (current_length - 1) * 2;
			    			
			    		    // We end up with a trailing run bit and need to check the next string type.
			    			int next_type  = ((int)s_type.get(j + 1));
			    			if(next_type == 1)
			    				predicted_length++;
			    			else
			    				predicted_length += 2;
			    			int next_length = (int)s_length.get(j + 1);
		    				next_length--;
		    				s_length.set(j + 1, next_length);
			    		}
			    	}
			    	
			    	int next_to_last_type = (int)s_type.get(n - 2);
			    	int next_to_last_length    = (int)s_length.get(n - 2);
			    	if(next_to_last_type == 1)
			    		predicted_length += (next_to_last_length - 1) / 2 + 2;
			    	else
			    	{
			    		predicted_length += (next_to_last_length - 1) * 2;	
			    		int next_type  = ((int)s_type.get(n - 1));
		    			if(next_type == 1 || next_type == 3)
		    				predicted_length++;
		    			else
		    				predicted_length += 2;
		    			int next_length = (int)s_length.get(n - 1);
	    				next_length--;
	    				s_length.set(n - 1, next_length);
			    	}
			    	
			    	int last_type   = (int)s_type.get(n - 1);
			    	int last_length = (int)s_length.get(n - 1);
			    	if(last_length > 0)
			    	{
			    	    if(last_type == 1)
			    	    {
			    	    	predicted_length += (last_length - 1) / 2 + 2;    	
			    	    }
			    	    else if(last_type == 0)
			    	    {
			    	    	predicted_length += (last_length - 1) * 2 + 1;	
			    	    }
			    	    else if(last_type == 3)
			    	    {
			    	        predicted_length += last_length / 2;
			    	    }
			    	    else if(last_type == 2)
			    	    {
			    	    	predicted_length += last_length * 2;
			    	    }
			    	}
			    	
					compression_length =  DeltaMapper.compressOneStrings(delta_string, length3, compressed_string);
					System.out.println("Predicted length of bit string is " + predicted_length);
					System.out.println("Actual length of bit string is " + compression_length);
			    }
				
				
				
			    
				System.out.println();
		    }  
	    	
		    
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
}