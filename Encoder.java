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
	short  [] channel_init, channel_min;
	int    [] set_sum, channel_sum, channel_length, channel_delta_min;
	double [] set_rate, channel_rate;
	String [] type_string;
	String [] set_string;
	String [] channel_string;
	int    [] shifted_blue, shifted_green, shifted_red;
	int    [] shifted_blue_green, shifted_red_green, shifted_red_blue;
	
	ArrayList channel_table, channel_data;
	
	
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
		    
		    // The input for the delta values.
		    channel_init = new short[6];
		    
		    // The value we subtract from frame values so the
		    // least value is 0.
		    channel_min = new short[6];
		    
		    
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
			
			for(int i = 0; i < delta.length; i++)
				delta[i] -= channel_delta_min[0];
			string             = new byte[xdim * ydim * 2];
	    	string_length      = 0;
			string_length      = DeltaMapper.packStrings2(delta, string_table, string);
			channel_length[0]  = string_length;
			channel_rate[0]    = string_length;
			channel_rate[0]   /= pixel_length;
			channel_type[0]    = 0;
			string_array_length = string_length / 8;
			if(string_length % 8 != 0)
				string_array_length++;
			clipped_string = new byte[string_array_length];
			for(int i = 0; i < string_array_length; i++)
				clipped_string[i] = string[i];
			channel_data.add(clipped_string);
			
			//*******************************************************************************
			
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
		    channel_delta_min[1] = (int)histogram_list.get(0);
			histogram            = (int[])histogram_list.get(1);
			string_table         = DeltaMapper.getRandomTable(histogram);
			channel_table.add(string_table);
			for(int i = 0; i < delta.length; i++)
				delta[i] -= channel_delta_min[1];
			string              = new byte[xdim * ydim * 2];
	    	string_length       = 0;
			string_length       = DeltaMapper.packStrings2(delta, string_table, string);
			channel_length[1]   = string_length;
			channel_rate[1]     = string_length;
			channel_rate[1]    /= pixel_length;
			channel_type[1]     = 0;
			string_array_length = string_length / 8;
			if(string_length % 8 != 0)
				string_array_length++;
			clipped_string = new byte[string_array_length];
			for(int i = 0; i < string_array_length; i++)
				clipped_string[i] = string[i];
			channel_data.add(clipped_string);
			
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
			for(int i = 0; i < delta.length; i++)
				delta[i] -= channel_delta_min[2];
			 
			string            = new byte[xdim * ydim * 2];
	    	string_length     = 0;
			string_length     = DeltaMapper.packStrings2(delta, string_table, string);
			channel_length[2] = string_length;
			channel_rate[2]   = string_length;
			channel_rate[2]  /= pixel_length;
			channel_type[2]   = 0;
			string_array_length = string_length / 8;
			if(string_length % 8 != 0)
				string_array_length++;
			clipped_string = new byte[string_array_length];
			for(int i = 0; i < string_array_length; i++)
				clipped_string[i] = string[i];
			channel_data.add(clipped_string);
			
			
			
			
			
			
			
			
			// The difference frames will usually contain negative numbers,
			// unless they are perfectly correlated with the other frame.
			// We'll go ahead and set the least values to 0. Worth checking if it
			// works anyway.
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
			
			for(int i = 0; i < delta.length; i++)
				delta[i] -= channel_delta_min[3];
			string             = new byte[xdim * ydim * 2];
	    	string_length      = 0;
			string_length      = DeltaMapper.packStrings2(delta, string_table, string);
			channel_length[3]  = string_length;
			channel_rate[3]    = string_length;
			channel_rate[3]   /= pixel_length;
			channel_type[3]    = 0;
			string_array_length = string_length / 8;
			if(string_length % 8 != 0)
				string_array_length++;
			clipped_string = new byte[string_array_length];
			for(int i = 0; i < string_array_length; i++)
				clipped_string[i] = string[i];
			channel_data.add(clipped_string);
			
			
			
		    
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
			
			for(int i = 0; i < delta.length; i++)
				delta[i] -= channel_delta_min[4];
			string             = new byte[xdim * ydim * 2];
	    	string_length      = 0;
			string_length      = DeltaMapper.packStrings2(delta, string_table, string);
			channel_length[4]  = string_length;
			channel_rate[4]    = string_length;
			channel_rate[4]   /= pixel_length;
			channel_type[4]    = 0;
			string_array_length = string_length / 8;
			if(string_length % 8 != 0)
				string_array_length++;
			clipped_string = new byte[string_array_length];
			for(int i = 0; i < string_array_length; i++)
				clipped_string[i] = string[i];
			channel_data.add(clipped_string);
		    
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
			
			for(int i = 0; i < delta.length; i++)
				delta[i] -= channel_delta_min[5];
			string             = new byte[xdim * ydim * 2];
	    	string_length      = 0;
			string_length      = DeltaMapper.packStrings2(delta, string_table, string);
			channel_length[5]  = string_length;
			channel_rate[5]    = string_length;
			channel_rate[5]   /= pixel_length;
			channel_type[5]    = 0;
			string_array_length = string_length / 8;
			if(string_length % 8 != 0)
				string_array_length++;
			clipped_string = new byte[string_array_length];
			for(int i = 0; i < string_array_length; i++)
				clipped_string[i] = string[i];
			channel_data.add(clipped_string);
		    
			//System.out.println("************************************************************************");
		  
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
			System.out.println("Saving:");
			System.out.println("A set of channels with the lowest delta sum is " + set_string[min_index]);
			System.out.println("The compression rate is " + String.format("%.4f", set_rate[min_index]));
			
			int [] channel = DeltaMapper.getChannels(min_index);
			for(int i = 0; i < 3; i++)
			{
				// Index for channel, 0-5.
				int j = channel[i];
				// Index for compression type, 0-4.
				int k = channel_type[j];
				
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
				ArrayList data_list      = (ArrayList)DeltaMapper.getCompressionData(src, xdim, ydim);
				ArrayList histogram_list = (ArrayList)data_list.get(0);
				int delta_min            = (int)histogram_list.get(0);
				int [] histogram         = (int[])histogram_list.get(1);
				int [] delta_random_lut  = DeltaMapper.getRandomTable(histogram); 
			    double [] rate           = (double [])data_list.get(1);
			    int delta_sum            = (int)data_list.get(2);
			    int compression_type     = (int)data_list.get(3);
			    int bitstring_length     = (int)data_list.get(4);
			    if(k != compression_type)
			    {
			    	System.out.println("Channel is " + channel_string[j]);
			        System.out.println("Channel list type is " + k);
			        System.out.println("Data list type is " + compression_type);
			    }
				ArrayList compressed_data_list = (ArrayList)data_list.get(5);
				byte [] compressed_data        = (byte [])compressed_data_list.get(k);
				
				System.out.println(channel_string[j] + " has " + String.format("%.4f", channel_rate[j]) + " compression with " + type_string[k]);
				//System.out.println("Compressed data array has length " + compressed_data.length);
				
				System.out.println();
				
				if(i == 1)
				{
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
				        out.writeShort(src[0]);
				        
				        // Minimum_value for deltas.
				        out.writeShort(delta_min);
				       
				        // Compression type 0-4.
				        out.writeByte(k);
				        
				        int table_length = delta_random_lut.length;
				        
				        // The length of the table used for
				        // string packing/unpacking.
				            
				        out.writeShort(table_length);
				        System.out.println("String table length is " + table_length);
				        
				        // The table itself.
				        for(int m = 0; m < table_length; m++)
				            out.writeInt(delta_random_lut[m]);
				        if(k > 2)
				            out.writeInt(bitstring_length);
				        
				        // The length of the data.
				        out.writeInt(compressed_data.length);
				        System.out.println("Data length is " + compressed_data.length);
				        
				        // The data itself.
				        out.write(compressed_data, 0, compressed_data.length);
				        
				        out.close();
				    }
				    catch(Exception e)
				    {
					    System.out.println(e.toString());
				    }
				}
			}
		}
	}
}