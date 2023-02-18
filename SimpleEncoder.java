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

public class SimpleEncoder
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
	short  [] init, delta_min;
	int    [] channel_type, set_sum, channel_sum;
	double [] channel_rate, set_rate;
	String [] type_string;
	String [] set_string;
	String [] channel_string;
	int    [] shifted_blue, shifted_green, shifted_red;
	int    [] shifted_blue_green, shifted_red_green, shifted_red_blue;
	
	ArrayList lut_list;
	
	
	int    pixel_shift = 3;
	long   file_length = 0;
	boolean initialized = false;
	
	public static void main(String[] args)
	{
		if (args.length != 1)
		{
			System.out.println("Usage: java SimpleEncoder <filename>");
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
		SimpleEncoder encoder = new SimpleEncoder(prefix + filename);
	}

	public SimpleEncoder(String _filename)
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
               
			    JFrame frame = new JFrame("Simple Encoder");
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
		    
		    // Delta bytes will have the same dimension as the image channel.
		    delta_bytes = new byte[xdim * ydim];
		    
		    // Allocate extra memory to allow for expansion as well as compression.
		    delta_strings             = new byte[xdim * ydim * 2];
		    zipped_strings            = new byte[xdim * ydim * 8];
		    compressed_strings        = new byte[xdim * ydim * 8];
		    zipped_compressed_strings = new byte[xdim * ydim * 8];
		    
		    // The 5 different kinds of compressions per channel.
		    type_string    = new String[6];
			type_string[0] = new String("zipped bytes");
			type_string[1] = new String("strings");
			type_string[2] = new String("zipped strings");
			type_string[3] = new String("compressed strings");
			type_string[4] = new String("zipped compressed strings");
			
			// If a frame is divided into blocks, there 
			// might be multiple types of compression.
			type_string[5] = new String("mixed");
		  
		    // The delta sum for each channel.
		    channel_sum  = new int[6];
		    // The different minimum compression rates per channel.
		    channel_rate = new double[6];
		    // The type of compression that produces the minimum result.
		    channel_type = new int[6];
		    // The look up tables used to pack/unpack strings.
		    lut_list = new ArrayList();
		    // The input for the delta values.
		    init = new short[6];
		    // The value we subtract from the deltas so the
		    // least value is 0.
		    delta_min = new short[6];
		    
		    
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
		    
		    // These values are usually 0 to start with,
		    // but checking for images with unusual distributions of values.
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
		    
		    if(blue_min != 0)
		    	for(int i = 0; i < xdim * ydim; i++)
		    		shifted_blue[i] -= blue_min;
		    if(green_min != 0)
		    	for(int i = 0; i < xdim * ydim; i++)
		    		shifted_green[i] -= green_min;
		    if(red_min != 0)
		    	for(int i = 0; i < xdim * ydim; i++)
		    		shifted_red[i] -= red_min;
		    
		    delta_min[0] = (short)blue_min;
		    init[0]      = (short)shifted_blue[0];
		    delta_min[1] = (short)green_min;
		    init[1]      = (short)shifted_green[0];
		    delta_min[2] = (short)red_min;
		    init[2]      = (short)shifted_red[0];
		    
		    // These values will usually contain negative numbers, except in the case
		    // of perfectly correlated channels.
		    shifted_blue_green = DeltaMapper.getDifference(shifted_blue, shifted_green);
	        int blue_green_min = 0;
		    for(int i = 0; i < xdim * ydim; i++)
		    	if(shifted_blue_green[i] < blue_green_min)
	            	blue_green_min = shifted_blue_green[i];
		    if(blue_green_min != 0)
		        for(int i = 0; i < xdim * ydim; i++)
		    	    shifted_blue_green[i] -= blue_green_min;
		    
		    shifted_red_green = DeltaMapper.getDifference(shifted_red, shifted_green);
	        int red_green_min = 0;
		    for(int i = 0; i < xdim * ydim; i++)
		    	if(shifted_red_green[i] < red_green_min)
		    		red_green_min = shifted_red_green[i];
		    if(red_green_min != 0)
		        for(int i = 0; i < xdim * ydim; i++)
		    	    shifted_red_green[i] -= red_green_min;
		    
		    shifted_red_blue = DeltaMapper.getDifference(shifted_red, shifted_blue);
	        int red_blue_min = 0;
		    for(int i = 0; i < xdim * ydim; i++)
		    	if(shifted_red_blue[i] < red_blue_min)
		    		red_blue_min = shifted_red_blue[i];
		    if(red_blue_min != 0)
		        for(int i = 0; i < xdim * ydim; i++)
		    	    shifted_red_blue[i] -= red_blue_min;
		    
		    delta_min[3] = (short)blue_green_min;
		    init[3]      = (short)shifted_blue_green[0];
		    delta_min[4] = (short)red_green_min;
		    init[4]      = (short)shifted_red_green[0];
		    delta_min[5] = (short)red_blue_min;
		    init[5]      = (short)shifted_red_blue[0];
		    
		    ArrayList data_list, histogram_list;
		    int []    histogram, lut;
		    double [] rate;
		    int       delta_sum, min_delta_sum, max_delta_sum, min_index, max_index;
		    double    min_rate, max_rate;
		    String    compression_type;
		    
		    // Get the blue channel data.
		    data_list      = DeltaMapper.getCompressionData(shifted_blue, xdim, ydim);
		    histogram_list = (ArrayList)data_list.get(0);
		    histogram      = (int [])histogram_list.get(1);
		    rate           = (double [])data_list.get(1);
		    delta_sum      = (int)data_list.get(2);
		    lut            = DeltaMapper.getRandomTable(histogram);
		    lut_list.add(lut);
		    
		    min_rate  = rate[0];
			min_index = 0;
			for(int i = 1; i < 5; i++)
			{
				if(rate[i] < min_rate)
				{
					min_rate = rate[i];
					min_index = i;
				}
			}
			
			channel_rate[0] = min_rate;
			channel_type[0] = min_index;
			channel_sum[0]  = delta_sum;
			compression_type = type_string[min_index];
			System.out.println("Blue minimum rate is " + String.format("%.4f", min_rate) + " using " + compression_type);
			System.out.println("The number of different delta values is " + histogram.length);
		    
		    
            // Get the green channel data.
		    data_list      = DeltaMapper.getCompressionData(shifted_green, xdim, ydim);
		    histogram_list = (ArrayList)data_list.get(0);
		    histogram      = (int [])histogram_list.get(1);
		    rate           = (double [])data_list.get(1);
		    delta_sum      = (int)data_list.get(2);
		    lut            = DeltaMapper.getRandomTable(histogram);
		    lut_list.add(lut);
		    
		    min_rate  = rate[0];
			min_index = 0;
			for(int i = 1; i < 5; i++)
			{
				if(rate[i] < min_rate)
				{
					min_rate = rate[i];
					min_index = i;
				}
			}
			
			channel_rate[1] = min_rate;
			channel_type[1] = min_index;
			channel_sum[1]  = delta_sum;
			compression_type = type_string[min_index];
			System.out.println("Green minimum rate is " + String.format("%.4f", min_rate) + " using " + compression_type);
			System.out.println("The number of different delta values is " + histogram.length);
			
			// Get the red channel data.
		    data_list      = DeltaMapper.getCompressionData(shifted_red, xdim, ydim);
		    histogram_list = (ArrayList)data_list.get(0);
		    histogram      = (int [])histogram_list.get(1);
		    rate           = (double [])data_list.get(1);
		    delta_sum      = (int)data_list.get(2);
		    lut            = DeltaMapper.getRandomTable(histogram);
		    lut_list.add(lut);
		    
		    min_rate  = rate[0];
			min_index = 0;
			for(int i = 1; i < 5; i++)
			{
				if(rate[i] < min_rate)
				{
					min_rate = rate[i];
					min_index = i;
				}
			}
			
			channel_rate[2] = min_rate;
			channel_type[2] = min_index;
			channel_sum[2]  = delta_sum;
			compression_type = type_string[min_index];
			System.out.println("Red minimum rate is " + String.format("%.4f", min_rate) + " using " + compression_type);
			System.out.println("The number of different delta values is " + histogram.length);
			
			// Get the blue-green channel data.
		    data_list      = DeltaMapper.getCompressionData(shifted_blue_green, xdim, ydim);
		    histogram_list = (ArrayList)data_list.get(0);
		    histogram      = (int [])histogram_list.get(1);
		    rate           = (double [])data_list.get(1);
		    delta_sum      = (int)data_list.get(2);
		    lut            = DeltaMapper.getRandomTable(histogram);
		    lut_list.add(lut);
		    
		    min_rate  = rate[0];
			min_index = 0;
			for(int i = 1; i < 5; i++)
			{
				if(rate[i] < min_rate)
				{
					min_rate = rate[i];
					min_index = i;
				}
			}
			
			channel_rate[3] = min_rate;
			channel_type[3] = min_index;
			channel_sum[3]  = delta_sum;
			compression_type = type_string[min_index];
			System.out.println("Blue-green minimum rate is " + String.format("%.4f", min_rate) + " using " + compression_type);
			System.out.println("The number of different delta values is " + histogram.length);
			
			// Get the red-green channel data.
		    data_list      = DeltaMapper.getCompressionData(shifted_red_green, xdim, ydim);
		    histogram_list = (ArrayList)data_list.get(0);
		    histogram      = (int [])histogram_list.get(1);
		    rate           = (double [])data_list.get(1);
		    delta_sum      = (int)data_list.get(2);
		    lut            = DeltaMapper.getRandomTable(histogram);
		    lut_list.add(lut);
		    min_rate  = rate[0];
		    
			min_index = 0;
			for(int i = 1; i < 5; i++)
			{
				if(rate[i] < min_rate)
				{
					min_rate = rate[i];
					min_index = i;
				}
			}
			
			channel_rate[4] = min_rate;
			channel_type[4] = min_index;
			channel_sum[4]  = delta_sum;
			compression_type = type_string[min_index];
			System.out.println("Red-green minimum rate is " + String.format("%.4f", min_rate) + " using " + compression_type);
			System.out.println("The number of different delta values is " + histogram.length);
		  
			// Get the red-blue channel data.
		    data_list      = DeltaMapper.getCompressionData(shifted_red_blue, xdim, ydim);
		    histogram_list = (ArrayList)data_list.get(0);
		    histogram      = (int [])histogram_list.get(1);
		    rate           = (double [])data_list.get(1);
		    delta_sum      = (int)data_list.get(2);
		    lut            = DeltaMapper.getRandomTable(histogram);
		    lut_list.add(lut);
		    
		    
		    min_rate  = rate[0];
			min_index = 0;
			for(int i = 1; i < 5; i++)
			{
				if(rate[i] < min_rate)
				{
					min_rate = rate[i];
					min_index = i;
				}
			}
			
			channel_rate[5] = min_rate;
			channel_type[5] = min_index;
			channel_sum[5]  = delta_sum;
			compression_type = type_string[min_index];
			System.out.println("Red-blue minimum rate is " + String.format("%.4f", min_rate) + " using " + compression_type);
			System.out.println("The number of different delta values is " + histogram.length);
			System.out.println();
		  
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
			
			
			min_index     = 0;
			min_delta_sum = Integer.MAX_VALUE;
			for(int i = 0; i < 10; i++)
			{
				if(set_sum[i] < min_delta_sum)
				{
				    min_delta_sum = set_sum[i];
				    min_index = i;
				}
			}
			System.out.println("A set of channels with the lowest delta sum is " + set_string[min_index]);
			System.out.println("The compression rate is " + String.format("%.4f", set_rate[min_index]));
			System.out.println();
			max_index     = 0;
			max_delta_sum = 0;
			for(int i = 0; i < 10; i++)
			{
				if(set_sum[i] > max_delta_sum)
				{
				    max_delta_sum = set_sum[i];
				    max_index     = i;
				}
			}
			System.out.println("A set of channels with the highest delta sum is " + set_string[max_index]);
			System.out.println("The compression rate is " + String.format("%.4f", set_rate[max_index]));
			System.out.println();
			System.out.println("**********************************************************************");
			System.out.println();
			
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
			System.out.println("A set of channels with the lowest delta sum is " + set_string[min_index]);
			System.out.println("The compression rate is " + String.format("%.4f", set_rate[min_index]));
			
			int [] channel = DeltaMapper.getChannels(min_index);
			for(int i = 0; i < 3; i++)
			{
				int j = channel[i];
				int k = channel_type[i];
				
				int [] src = new int[1];
				if(j == 0)
				    src = shifted_blue;
				else if(j == 1)
					src = shifted_green;
				else if(j == 2)
					src = shifted_red;
				else if(j == 3)
					src = shifted_blue_green;
				else if(j == 4)
					src = shifted_red_green;
				else if(j == 5)
					src = shifted_red_blue;
				ArrayList data_list            = (ArrayList)DeltaMapper.getCompressionData(src, xdim, ydim);
				ArrayList compressed_data_list = (ArrayList)data_list.get(4);
				byte [] compressed_data         = (byte [])compressed_data_list.get(k);
				System.out.println(channel_string[j] + " has " + String.format("%.4f", channel_rate[j]) + " compression with " + type_string[k]);
				System.out.println("Compressed data array has length " + compressed_data.length);
				System.out.println();
			}
		}
	}
}