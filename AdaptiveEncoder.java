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

public class AdaptiveEncoder
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
	
	ArrayList channel_table, channel_data, channel_src;
	
	double  file_ratio;
	int     min_set_id  = 0;
	int     pixel_shift = 0;
	long    file_length = 0;
	boolean initialized = false;
	boolean merge       = true;
	boolean recompress  = true;
	
	int     segment_length = 100;
	
	public static void main(String[] args)
	{
		if (args.length != 1)
		{
			System.out.println("Usage: java AdaptiveEncoder <filename>");
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
		AdaptiveEncoder encoder = new AdaptiveEncoder(prefix + filename);
	}

	public AdaptiveEncoder(String _filename)
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
               
			    JFrame frame = new JFrame("AdaptiveEncoder");
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
				
				JMenuItem segment_item = new JMenuItem("Segment Length");
				ActionListener segment_length_handler = new ActionListener()
				{
					public void actionPerformed(ActionEvent e)
					{
						Point location_point = frame.getLocation();
						int x = (int) location_point.getX();
						int y = (int) location_point.getY();
						x += xdim;
						segment_dialog.setLocation(x, y);
						segment_dialog.pack();
						segment_dialog.setVisible(true);
					}
				};
				segment_item.addActionListener(segment_length_handler);
				segment_dialog = new JDialog(frame, "Segment Length");
				JPanel segment_panel = new JPanel(new BorderLayout());
				JSlider segment_slider = new JSlider();
				segment_slider.setMinimum(0);
				segment_slider.setMaximum(100);
				segment_slider.setValue(segment_length);
				segment_value = new JTextField(3);
				segment_value.setText(" " + segment_length + " ");
				ChangeListener segment_slider_handler = new ChangeListener()
				{
					public void stateChanged(ChangeEvent e)
					{
						JSlider slider = (JSlider) e.getSource();
						segment_length = slider.getValue();
						segment_value.setText(" " + segment_length + " ");
						if(slider.getValueIsAdjusting() == false)
						{
							apply_item.doClick();
						}
					}
				};
				segment_slider.addChangeListener(segment_slider_handler);
				segment_panel.add(segment_slider, BorderLayout.CENTER);
				segment_panel.add(segment_value, BorderLayout.EAST);
				segment_dialog.add(segment_panel);
				
				settings_menu.add(segment_item);
				
				JCheckBoxMenuItem merge_item = new JCheckBoxMenuItem("Merge");
				ActionListener merge_handler = new ActionListener()
				{
					public void actionPerformed(ActionEvent e) 
		            {
		            	JCheckBoxMenuItem item = (JCheckBoxMenuItem) e.getSource();
		            	if(merge == true)
						{
		            		merge = false;
							item.setState(false);
						}
						else
						{
							merge = true;
							item.setState(true);
						}
		            }   	
				};
				merge_item.addActionListener(merge_handler);
				if(merge)
					merge_item.setState(true);
				else
					merge_item.setState(false);
				settings_menu.add(merge_item);
				
				JCheckBoxMenuItem recompress_item = new JCheckBoxMenuItem("Recompress");
				ActionListener recompress_handler = new ActionListener()
				{
					public void actionPerformed(ActionEvent e) 
		            {
		            	JCheckBoxMenuItem item = (JCheckBoxMenuItem) e.getSource();
		            	if(recompress == true)
						{
		            		recompress = false;
							item.setState(false);
						}
						else
						{
							recompress = true;
							item.setState(true);
						}
		            }   	
				};
				recompress_item.addActionListener(recompress_handler);
				if(recompress)
					recompress_item.setState(true);
				else
					recompress_item.setState(false);
				settings_menu.add(recompress_item);
				
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
		    
		    // Different compression results.
		    channel_data = new ArrayList();
		    
		    channel_src  = new ArrayList();
		   
		    for(int i = 0; i < 6; i++)
		    {
		    	ArrayList data_list = new ArrayList();
		    	channel_data.add(data_list);
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
			System.out.println();
			
			int [] channel = DeltaMapper.getChannels(min_set_id);
		    
			for(int i = 0; i < 3; i++)
			{
				int       j      = channel[i];
				int []    src    = (int [])channel_src.get(j);
				ArrayList result = DeltaMapper.getDeltasFromValues3(src, xdim, ydim);
				int []    delta  = (int [])result.get(1);
				
				
				
				ArrayList histogram_list = DeltaMapper.getHistogram(delta);
			    channel_delta_min[j]     = (int)histogram_list.get(0);
			    int [] histogram         = (int[])histogram_list.get(1);
				int [] string_table = DeltaMapper.getRankTable(histogram);
				channel_table.add(string_table);
				
				for(int k = 1; k < delta.length; k++)
					delta[k] -= channel_delta_min[j];
				
				// We have to do another histogram inside the function after 
				// we subtract the min to get an exact result.
				// Seems like we shouldn't have to do that, but
				// don't want to change something that is probably
				// reverse engineered somewhere else.
				// Might be worth just allocating a conservative amount
				// of memory and save on processing, or live with
				// some kind of fudge factor.
				int predicted_length = DeltaMapper.getStringLength(delta);
				
				int string_byte_length = predicted_length / 8;
				if(predicted_length % 8 != 0)
					string_byte_length++;
				
				byte [] string             = new byte[2 * string_byte_length];
				byte [] compression_string = new byte[2 * string_byte_length];
				channel_length[j]          = DeltaMapper.packStrings2(delta, string_table, string);
				
				double string_rate = predicted_length;
				string_rate       /= pixel_length;
				System.out.println("The packed string rate is " + String.format("%.4f", string_rate));
				
				int minimum_segment_length = 64 + segment_length * 8;
			   
				ArrayList segment_data_list = new ArrayList();
				if(merge)
				{
					if(recompress)
				        segment_data_list = DeltaMapper.getSegmentData2(string, channel_length[j], minimum_segment_length);
					else
						segment_data_list = DeltaMapper.getSegmentData3(string, channel_length[j], minimum_segment_length);
				}
				else
					segment_data_list = DeltaMapper.getSegmentData(string, channel_length[j], minimum_segment_length);
				ArrayList compression_length = (ArrayList)segment_data_list.get(0);
				ArrayList compression_data   = (ArrayList)segment_data_list.get(1);
				ArrayList data_list = (ArrayList)channel_data.get(j);
				data_list.clear();
				data_list.add(compression_length);
				data_list.add(compression_data);
			
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
	
	class SaveHandler implements ActionListener
	{
		public void actionPerformed(ActionEvent event)
		{
			if(!initialized)
				apply_item.doClick();
			
			//System.out.println("Saving " + set_string[min_set_id]);
			//System.out.println("Set rate  is " + String.format("%.2f", set_rate[min_set_id]));
			//System.out.println("RGB rate  is " + String.format("%.2f", set_rate[0]));
			//System.out.println("File rate is " + String.format("%.2f", file_ratio));
			//System.out.println("Shift is     " + pixel_shift);
			//System.out.println();
			
			int channel[] = DeltaMapper.getChannels(min_set_id);
			System.out.println();
			
		    try
		    {
		        DataOutputStream out = new DataOutputStream(new FileOutputStream(new File("foo")));
		        
		        // Dimensions of frame.
		        out.writeShort(xdim);
		        out.writeShort(ydim);
		        
		        // Pixel shift
		        out.writeByte(pixel_shift);
		        
		        for(int i = 0; i < 3; i++)
		        {
		        	
		        	int j = channel[i];
		            
		        	System.out.println("Saving " + channel_string[j] + " channel.");
		        	
		        	// Channel 0-5.
		            out.writeByte(j);
		        
		            out.writeInt(channel_min[j]);
		            
		            // Init value for deltas.
		            out.writeInt(channel_init[j]);
		            //System.out.println("Init value for deltas is " + channel_init[j]);
		        
		            // Minimum_value for deltas.
		            out.writeInt(channel_delta_min[j]);
		            //System.out.println("Minimum value for deltas is " + channel_delta_min[j]);
		        
		            
		            // The length of the table used for string packing/unpacking.
		            // We're only saving tables for selected channels,
		            // so we use i instead of j.
		            int [] string_table = (int[])channel_table.get(i);
		            out.writeShort(string_table.length);
		            //System.out.println("String table length is " + string_table.length);
		       
		            // The table itself.
		            for(int k = 0; k < string_table.length; k++)
		                out.writeInt(string_table[k]);
		       
		            // The length of the packed strings concatenated.
		            out.writeInt(channel_length[j]);
		            System.out.println("Bit length is " + channel_length[j]);
		            
		            // The data itself.
		            ArrayList data_list = (ArrayList)channel_data.get(j);
		            
		            ArrayList segment_length = (ArrayList)data_list.get(0);
		            ArrayList segment_data   = (ArrayList)data_list.get(1);
		            
		            int n = segment_length.size();
		            if(i == 0)
		                System.out.println("There are " + n + " segments.");
		            
		            
		            
		            // Number of segments.
		            out.writeInt(n);
		            
		            for(int k = 0; k < n; k++)
		            {
		            	
		            	int bit_length  = (int)segment_length.get(k);
		            	byte [] segment = (byte [])segment_data.get(k);
		            	
		            	
		            	
		            	
		            	
		            	int iterations = DeltaMapper.getIterations(segment, bit_length);
		            	if(i == 0)
		            	    System.out.println("Iterations is " + iterations);
		            	byte extra_bits = 8;
		            	if(bit_length % 8 != 0)
		            	    extra_bits     += 8 - (bit_length % 8);
		            	int calculated_length = segment.length * 8 - extra_bits;
		            	
		            	if(i == 0)
		            	{
		            		System.out.println("Segment " + k + " has bit length " + bit_length);
		            		System.out.println("Calculated bit length is " + calculated_length);
		            		System.out.println("Byte length is " + segment.length);
		            		System.out.println("Extra bits is " + extra_bits);
		            	}
		            	
		            	
		            	out.writeInt(segment.length);
		            	out.writeByte(extra_bits);
		            	out.write(segment, 0, segment.length);
		            }
		        } 
		        out.flush();
		        out.close();
		        
		        File file              = new File("foo");
		        long foo_length        = file.length(); 
		        double foo_ratio = foo_length;
		        foo_ratio       /= (3 * xdim * ydim);
		        System.out.println("Compression ratio is " + String.format("%.4f", foo_ratio));
		        System.out.println("File compression rate is " + String.format("%.4f", file_ratio));
		        System.out.println();
		    }
		    catch(Exception e)
		    {
			    System.out.println(e.toString());
		    }
		}
	}
}