import java.awt.*;
import java.awt.image.*;
import java.io.*;
import java.awt.event.*;
import java.util.*;
import java.util.zip.*;
import java.lang.Math.*;
import javax.imageio.*;
import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

public class AdaptiveWriter
{
	BufferedImage image;
	ImageCanvas   image_canvas;
	int           xdim, ydim;
	JMenuItem     apply_item;
	String        filename;
	int [] pixel;
	
	int pixel_quant = 0;
	int pixel_shift = 0;
	int correction  = 0;
	int segment     = 0;
	int min_set_id  = 0;
	
	
	int    [] set_sum, channel_sum;
	String [] set_string;
	String [] channel_string;
	
	
	int [] channel_init;
	int [] channel_min;
	int [] channel_delta_min;
	int [] channel_length;
	int [] channel_compressed_length;
	int [] channel_string_type;
	byte [] channel_iterations;
	
	boolean [] channel_segmented;
	
	long file_length;

	ArrayList channel_list, table_list, string_list, channel_data;

	boolean initialized = false;
	
	public static void main(String[] args)
	{
		if (args.length != 1)
		{
			System.out.println("Usage: java AdaptiveWriter <filename>");
			System.exit(0);
		}
		String prefix       = new String("C:/Users/Brian Crowley/Desktop/");
		String filename     = new String(args[0]);
	
		AdaptiveWriter writer = new AdaptiveWriter(prefix + filename);
	}

	public AdaptiveWriter(String _filename)
	{
		filename = _filename;
		try
		{
			File file = new File(filename);
			file_length            = file.length();
			BufferedImage original_image = ImageIO.read(file);
			int raster_type = original_image.getType();
			ColorModel color_model = original_image.getColorModel();
			int number_of_channels = color_model.getNumColorComponents();
			int number_of_bits = color_model.getPixelSize();
			xdim = original_image.getWidth();
			ydim = original_image.getHeight();
		
			System.out.println("Xdim = " + xdim + ", ydim = " + ydim);
			
			int pixel_length = xdim * ydim * 8;
		    
		    // Set in constructor and accessed 
			// by apply handler.
		    channel_list = new ArrayList();
		   
		    // Set by apply handler and accessed
		    // by save handler.
		    table_list = new ArrayList();
		    string_list = new ArrayList();
		    
		    channel_data = new ArrayList();
		 
		    channel_string    = new String[6];
		    channel_string[0] = new String("blue");
		    channel_string[1] = new String("green");
		    channel_string[2] = new String("red");
		    channel_string[3] = new String("blue-green");
		    channel_string[4] = new String("red-green");
		    channel_string[5] = new String("red-blue");
		    
		    // The delta sum for each set of channels that can produce an image.
		    set_sum       = new int[10];
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
		    
		    channel_init      = new int[6];
		    channel_min       = new int[6];
		    channel_delta_min = new int[6];
		    channel_sum       = new int[6];
		    channel_length    = new int[6];
            channel_compressed_length = new int[6];
            channel_string_type = new int[3];
            channel_iterations  = new byte[3];
            channel_segmented   = new boolean[3];
			System.out.println();
			
			if(raster_type == BufferedImage.TYPE_3BYTE_BGR)
			{
				pixel = new int[xdim * ydim];
				PixelGrabber pixel_grabber = new PixelGrabber(original_image, 0, 0, xdim, ydim, pixel, 0, xdim);
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
		        
		        int [] alpha = new int[xdim * ydim];
		        int [] blue = new int[xdim * ydim];
		        int [] green = new int[xdim * ydim];
		        int [] red = new int[xdim * ydim];
		        for(int i = 0; i < xdim * ydim; i++)
				{
			        alpha[i] = (pixel[i] >> 24) & 0xff;
				    blue[i]  = (pixel[i] >> 16) & 0xff;
				    green[i] = (pixel[i] >> 8) & 0xff; 
		            red[i]   =  pixel[i] & 0xff; 
				}
		        channel_list.add(blue);
		        channel_list.add(green);
		        channel_list.add(red);
		        
			    image = new BufferedImage(xdim, ydim, BufferedImage.TYPE_INT_RGB);
			    
			    // This is not the usual order, careful.
			    for(int i = 0; i < xdim; i++)
				{
				    for(int j = 0; j < ydim; j++)
				    	image.setRGB(i, j, pixel[j * xdim + i]);	
				} 
			    
			    JFrame frame = new JFrame("Adaptive Delta Writer");
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
				ActionListener reload_handler = new ActionListener()
				{
					public void actionPerformed(ActionEvent event)
					{
						for(int i = 0; i < xdim; i++)
					    	for(int j = 0; j < ydim; j++)
					    		image.setRGB(i, j, pixel[j * xdim + i]); 
						System.out.println("Reloaded original image.");
						image_canvas.repaint();
					}	
			    };
				
				reload_item.addActionListener(reload_handler);
				file_menu.add(reload_item);
				
				JMenuItem save_item        = new JMenuItem("Save");
				SaveHandler save_handler = new SaveHandler();
				save_item.addActionListener(save_handler);
				file_menu.add(save_item);
				
				JMenu settings_menu = new JMenu("Settings");
			
				JMenuItem quant_item = new JMenuItem("Pixel Resolution");
				JDialog quant_dialog = new JDialog(frame, "Pixel Resolution");
				ActionListener quant_handler = new ActionListener()
				{
					public void actionPerformed(ActionEvent e)
					{
						Point location_point = frame.getLocation();
						int x = (int) location_point.getX();
						int y = (int) location_point.getY();

						quant_dialog.setLocation(x, y - 50);
						quant_dialog.pack();
						quant_dialog.setVisible(true);
					}
				};
				quant_item.addActionListener(quant_handler);
				
				JPanel quant_panel = new JPanel(new BorderLayout());
				JSlider quant_slider = new JSlider();
				quant_slider.setMinimum(0);
				quant_slider.setMaximum(10);
				quant_slider.setValue(pixel_quant);
				JTextField quant_value = new JTextField(3);
				quant_value.setText(" " + pixel_quant + " ");
				ChangeListener quant_slider_handler = new ChangeListener()
				{
					public void stateChanged(ChangeEvent e)
					{
						JSlider slider = (JSlider) e.getSource();
						pixel_quant = slider.getValue();
						quant_value.setText(" " + pixel_quant + " ");
						if(slider.getValueIsAdjusting() == false)
						{
							apply_item.doClick();
						}
					}
				};
				quant_slider.addChangeListener(quant_slider_handler);
				quant_panel.add(quant_slider, BorderLayout.CENTER);
				quant_panel.add(quant_value, BorderLayout.EAST);
				quant_dialog.add(quant_panel);
				settings_menu.add(quant_item);
				
				JMenuItem shift_item   = new JMenuItem("Color Resolution");
				JDialog   shift_dialog = new JDialog(frame, "Color Resolution");
				ActionListener shift_handler = new ActionListener()
				{
					public void actionPerformed(ActionEvent e)
					{
						Point location_point = frame.getLocation();
						int x = (int) location_point.getX();
						int y = (int) location_point.getY();

						shift_dialog.setLocation(x, y - 100);
						shift_dialog.pack();
						shift_dialog.setVisible(true);
					}
				};
				shift_item.addActionListener(shift_handler);
				
				JPanel shift_panel = new JPanel(new BorderLayout());
				JSlider shift_slider = new JSlider();
				shift_slider.setMinimum(0);
				shift_slider.setMaximum(7);
				shift_slider.setValue(correction);
				JTextField shift_value = new JTextField(3);
				shift_value.setText(" " + pixel_shift + " ");
				ChangeListener shift_slider_handler = new ChangeListener()
				{
					public void stateChanged(ChangeEvent e)
					{
						JSlider slider = (JSlider) e.getSource();
						pixel_shift = slider.getValue();
						shift_value.setText(" " + pixel_shift + " ");
						if(slider.getValueIsAdjusting() == false)
							apply_item.doClick();
					}
				};
				shift_slider.addChangeListener(shift_slider_handler);
				shift_panel.add(shift_slider, BorderLayout.CENTER);
				shift_panel.add(shift_value, BorderLayout.EAST);
				shift_dialog.add(shift_panel);
				
				settings_menu.add(shift_item);
				JMenuItem segment_item = new JMenuItem("Segmentation");
				JDialog segment_dialog = new JDialog(frame, "Segmentation");
				ActionListener segment_length_handler = new ActionListener()
				{
					public void actionPerformed(ActionEvent e)
					{
						Point location_point = frame.getLocation();
						int x = (int) location_point.getX();
						int y = (int) location_point.getY();
						y -= 150;
						segment_dialog.setLocation(x, y);
						segment_dialog.pack();
						segment_dialog.setVisible(true);
					}
				};
				segment_item.addActionListener(segment_length_handler);
				
				JPanel segment_panel = new JPanel(new BorderLayout());
				JSlider segment_slider = new JSlider();
				segment_slider.setMinimum(0);
				segment_slider.setMaximum(13);
				segment_slider.setValue(segment);
				JTextField segment_value = new JTextField(3);
				segment_value.setText(" " + segment + " ");
				ChangeListener segment_slider_handler = new ChangeListener()
				{
					public void stateChanged(ChangeEvent e)
					{
						JSlider slider = (JSlider) e.getSource();
						segment = slider.getValue();
						segment_value.setText(" " + segment + " ");
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
				// The correction value is a convenience to help see what
				// quantizing does to the original image, instead of
				// simply switching back and forth between the original
				// image and the quantized image.
				JMenuItem correction_item = new JMenuItem("Error Correction");
				JDialog correction_dialog = new JDialog(frame, "Error Correction");
				ActionListener correction_handler = new ActionListener()
				{
					public void actionPerformed(ActionEvent e)
					{
						Point location_point = frame.getLocation();
						int x = (int) location_point.getX();
						int y = (int) location_point.getY();

						correction_dialog.setLocation(x, y - 200);
						correction_dialog.pack();
						correction_dialog.setVisible(true);
					}
				};
				correction_item.addActionListener(correction_handler);
				
				JPanel correction_panel = new JPanel(new BorderLayout());
				JSlider correction_slider = new JSlider();
				correction_slider.setMinimum(0);
				correction_slider.setMaximum(10);
				correction_slider.setValue(correction);
				JTextField correction_value = new JTextField(3);
				correction_value.setText(" " + correction + " ");
				ChangeListener correction_slider_handler = new ChangeListener()
				{
					public void stateChanged(ChangeEvent e)
					{
						JSlider slider = (JSlider) e.getSource();
						correction = slider.getValue();
						correction_value.setText(" " + correction + " ");
						if(slider.getValueIsAdjusting() == false)
							apply_item.doClick();
					}
				};
				correction_slider.addChangeListener(correction_slider_handler);
				correction_panel.add(correction_slider, BorderLayout.CENTER);
				correction_panel.add(correction_value, BorderLayout.EAST);
				correction_dialog.add(correction_panel);
				
				settings_menu.add(correction_item);
				
				
				menu_bar.add(file_menu);
				menu_bar.add(settings_menu);
				
				frame.setJMenuBar(menu_bar);
				
				frame.pack();
				frame.setLocation(10, 800);
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
		// The minimum set changes depending on how the 
		// image is quantized, so it has to be reevaluated
		// with every change in the parameters.
		public void actionPerformed(ActionEvent event)
		{
			ArrayList shifted_channel_list = new ArrayList();
			byte [] iterations = new byte[3];
			for(int i = 0; i < 3; i++)
			{
				int [] channel         = (int [])channel_list.get(i);
				int [] shifted_channel = new int [xdim * ydim];
				for(int j = 0; j < channel.length; j++)
					shifted_channel[j] = channel[j] >> pixel_shift;	
				shifted_channel_list.add(shifted_channel);
			}
			
			ArrayList quantized_channel_list = new ArrayList();
	       
			int new_xdim = xdim;
		    int new_ydim = ydim;
		    if(pixel_quant != 0)
		    {
		    	double factor = pixel_quant;
		        factor       /= 10;
		        new_xdim = xdim - (int)(factor * (xdim / 2 - 2));
		        new_ydim = ydim - (int)(factor * (ydim / 2 - 2));
		    }
		    
		    for(int i = 0; i < 3; i++)
		    {
		    	int [] shifted_channel = (int [])shifted_channel_list.get(i);
		    	if(pixel_quant == 0)
		    		quantized_channel_list.add(shifted_channel);
		    	else
		    	{
		    		int [] resized_channel = ResizeMapper.resize(shifted_channel, xdim, new_xdim, new_ydim);
		    		quantized_channel_list.add(resized_channel);
		    	}
		    }
		    
		    int [] quantized_blue  = (int [])quantized_channel_list.get(0);
		    int [] quantized_green = (int [])quantized_channel_list.get(1);
		    int [] quantized_red   = (int [])quantized_channel_list.get(2);
		    
		    int [] quantized_blue_green = DeltaMapper.getDifference(quantized_blue, quantized_green);
		    int [] quantized_red_green  = DeltaMapper.getDifference(quantized_red, quantized_green);
		    int [] quantized_red_blue   = DeltaMapper.getDifference(quantized_red, quantized_blue);
		    
		    quantized_channel_list.add(quantized_blue_green);
		    quantized_channel_list.add(quantized_red_green);
		    quantized_channel_list.add(quantized_red_blue);
		    
		    for(int i = 0; i < 6; i++)
		    {
		    	int min = 256;
		    	int [] quantized_channel = (int [])quantized_channel_list.get(i);
		    	
		    	// Find the channel minimums.
		    	for(int j = 0; j < quantized_channel.length; j++)
		    		if(quantized_channel[j] < min)
		    			min = quantized_channel[j];
		    	channel_min[i] = min;
		    	
		    	// Get rid of the negative numbers in the difference channels.
		    	if(i > 2)
		    		for(int j = 0; j < quantized_channel.length; j++)
		    			quantized_channel[j] -= min;
		    	
		    	// Save the initial value.
		    	channel_init[i] = quantized_channel[0];
		    	
		    	// Replace the original data with the modified data.
		    	quantized_channel_list.set(i, quantized_channel);
		    	
		    	// Get the Paeth delta sum.
		    	channel_sum[i] = DeltaMapper.getPaethSum2(quantized_channel, new_xdim, new_ydim, 20);
		    	
		    }
		  
			// Find the optimal set.  
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
			
			double file_compression_rate = file_length;
			file_compression_rate       /= xdim * ydim * 3;
			System.out.println("The file compression rate is " + String.format("%.4f", file_compression_rate));
			
			
			System.out.println("A set of channels with the lowest delta sum is " + set_string[min_index]);
			
		    
			int [] channel_id = DeltaMapper.getChannels(min_set_id);
			
			table_list.clear();
			string_list.clear();
			channel_data.clear();
			
			int total_bits = 0;
			int total_compressed_bits = 0;
			int total_map_bits = 0;
			for(int i = 0; i < 3; i++)
			{
				int j = channel_id[i];
				
				int [] quantized_channel = (int[])quantized_channel_list.get(j);
				
				ArrayList result = DeltaMapper.getDeltasFromValues3(quantized_channel, new_xdim, new_ydim);
                int []    delta  = (int [])result.get(1);
				
				ArrayList histogram_list = DeltaMapper.getHistogram(delta);
			    channel_delta_min[j]     = (int)histogram_list.get(0);
			    int [] histogram         = (int[])histogram_list.get(1);
				int [] string_table = DeltaMapper.getRankTable(histogram);
				table_list.add(string_table);
				
				for(int k = 1; k < delta.length; k++)
					delta[k] -= channel_delta_min[j];
				byte [] string         = new byte[xdim * ydim * 4];
				byte [] compression_string = new byte[xdim * ydim * 4];
				channel_length[j]      = DeltaMapper.packStrings2(delta, string_table, string);
					
				double zero_one_ratio = new_xdim * new_ydim;
	            if(histogram.length > 1)
	            {
				    int min_value = Integer.MAX_VALUE;
				    for(int k = 0; k < histogram.length; k++)
					 if(histogram[k] < min_value)
						min_value = histogram[k];
				    zero_one_ratio -= min_value;
	            }	
		        zero_one_ratio  /= channel_length[j];
		    
		        if(zero_one_ratio > .5)
		        {
				    channel_compressed_length[j] = DeltaMapper.compressZeroStrings(string, channel_length[j], compression_string);
				    channel_string_type[i] = 0;
				    if(channel_compressed_length[j] == channel_length[j])
				    {
				        channel_iterations[i] = 0;
				        string_list.add(string);
				    }
				    else
				    {
				    	int compression_byte_length = channel_compressed_length[j] / 8;
				    	if( channel_compressed_length[j] % 8 != 0)
				    		compression_byte_length++;
				    	compression_byte_length++;
				    	channel_iterations[i] = compression_string[compression_byte_length - 1];
				    	System.out.println("String compressed.");
				    	System.out.println("Iterations was " + channel_iterations[i]);
				    	string_list.add(compression_string);		
				    }
		        }   
		        else
		        {
		    	    channel_compressed_length[j] = DeltaMapper.compressOneStrings(string, channel_length[j], compression_string);
		    	    channel_string_type[i] = 1;
				    if(channel_compressed_length[j] == channel_length[j])
				    {
					    channel_iterations[i] = 0;
					    string_list.add(string);
				    }
				    else
				    {
				    	int compression_byte_length = channel_compressed_length[j] / 8;
				    	if(channel_compressed_length[j] % 8 != 0)
				    		compression_byte_length++;
				    	compression_byte_length++;
				    	channel_iterations[i] = compression_string[compression_byte_length - 1];
				    	System.out.println("String compressed.");
				    	System.out.println("Iterations was " + channel_iterations[i]);
				    	string_list.add(compression_string);			
				    }
		        }
		    	
				if(segment != 0)
				{
					if(channel_length[j] == channel_compressed_length[j])
					{
					    int divisor = 1;
					    int increment = ydim / 5;
					    for(int k = 0; k < segment; k++)
					    {
						    if(k < 11)
						        divisor += increment;
					    }
					
					    int segment_length = channel_length[j] / divisor;
					
					    if(segment > 10)
					    {
						    for (int k = 11; k <= segment; k++)
							    segment_length /= 2;
					    }
					    if(segment_length < 64)
					    	segment_length = 64;
					
					    // The segment length has to be an even multiple of 8,
					    // with the exception of the last segment.
					    int remainder = segment_length % 8;
					    segment_length -= remainder;
					
					    int number_of_segments = channel_length[j] / segment_length;
					
					    System.out.println("Number of original segments is " + number_of_segments);
					    System.out.println("Minimum segment length is " + segment_length);	
					    //ArrayList segment_data_list = SegmentMapper.getSegmentedData2(string, channel_length[j], segment_length);
					    ArrayList segment_data_list = SegmentMapper.getMergedSegmentedData(string, channel_length[j], segment_length);
					    ArrayList length_list = (ArrayList)segment_data_list.get(0);
					    System.out.println("Number of merged segments is " + length_list.size());
					    if(length_list.size() == 1)
					    {
					    	System.out.println("Merged segment list produced no segmentation.");
					    	channel_segmented[i] = false;
					    }
					    else
					    {
					    	channel_segmented[i] = true;
					    }
					    System.out.println();
					    channel_data.add(segment_data_list);
					}
					else
					{
						int divisor = 1;
					    int increment = ydim / 5;
					    for(int k = 0; k < segment; k++)
					    {
						    if(k < 11)
						        divisor += increment;
					    }
					
					    int segment_length = channel_compressed_length[j] / divisor;
					
					    if(segment > 10)
					    {
						    for (int k = 11; k <= segment; k++)
							    segment_length /= 2;
					    }
					    if(segment_length < 64)
					    	segment_length = 64;
					
					    // The segment length has to be an even multiple of 8,
					    // with the exception of the last segment.
					    int remainder = segment_length % 8;
					    segment_length -= remainder;
					
					    int number_of_segments = channel_compressed_length[j] / segment_length;
					
					    System.out.println("Number of original segments is " + number_of_segments);
					    System.out.println("Minimum segment length is " + segment_length);	
					    //ArrayList segment_data_list = SegmentMapper.getSegmentedData2(compression_string, channel_compressed_length[j], segment_length);
					    ArrayList segment_data_list = SegmentMapper.getMergedSegmentedData(compression_string, channel_compressed_length[j], segment_length);
					    ArrayList length_list = (ArrayList)segment_data_list.get(0);
					    System.out.println("Number of merged segments is " + length_list.size());
					    if(length_list.size() == 1)
					    {
					    	System.out.println("Merged segment list produced no segmentation.");
					    	channel_segmented[i] = false;
					    }
					    else
					    {
					    	channel_segmented[i] = true;
					    }
					    
					    channel_data.add(segment_data_list);
						
					}
				}
			}
			
			System.out.println();
			
			ArrayList resized_channel_list = new ArrayList();
			
			for(int i = 0; i < 3; i++)
		    {
			    byte [] string = (byte [])string_list.get(i);
			    int  [] table  = (int [])table_list.get(i);
			    int  [] delta  = new int[new_xdim * new_ydim];
			    
			    int j = channel_id[i];
			    
			    if(channel_length[j] != channel_compressed_length[j])
			    {
			    	byte [] decompressed_string = new byte[xdim * ydim * 4];
			    	int decompressed_length = 0;
			    	if(channel_string_type[i] == 0)
			    		decompressed_length = DeltaMapper.decompressZeroStrings(string, channel_compressed_length[j], decompressed_string);
			    	else
			    		decompressed_length = DeltaMapper.decompressOneStrings(string, channel_compressed_length[j], decompressed_string);
			    	if(decompressed_length != channel_length[j])
			    		System.out.println("Decompressed length different from original length.");
			    	int number_unpacked = DeltaMapper.unpackStrings2(decompressed_string, table, delta);
					if(number_unpacked != new_xdim * new_ydim)
					    System.out.println("Number of values unpacked does not agree with image dimensions.");	
			    }
			    else
			    {
			    	int number_unpacked = DeltaMapper.unpackStrings2(string, table, delta);
					if(number_unpacked != new_xdim * new_ydim)
					    System.out.println("Number of values unpacked does not agree with image dimensions.");	
			    }
			    
			    for(int k = 1; k < delta.length; k++)
			    	delta[k] += channel_delta_min[j];
			    int [] channel = DeltaMapper.getValuesFromDeltas3(delta, new_xdim , new_ydim, channel_init[j]);
				if(j > 2)
					for(int k = 0; k < channel.length; k++)
						channel[k] += channel_min[j];
				if(new_xdim != xdim && new_ydim != ydim)
				{
					int [] resized_channel = ResizeMapper.resize(channel, new_xdim, xdim, ydim);
					resized_channel_list.add(resized_channel);
				    
				}
				else
					resized_channel_list.add(channel);
		    }
			
			int [] blue  = new int[xdim * ydim];
			int [] green = new int[xdim * ydim];
			int [] red   = new int[xdim * ydim];
			
			if(min_set_id == 0)
			{
				blue  = (int [])resized_channel_list.get(0);
				green = (int [])resized_channel_list.get(1);
				red   = (int [])resized_channel_list.get(2);
		    }
			else if(min_set_id == 1)
			{ 
				blue  = (int [])resized_channel_list.get(0);
				red   = (int [])resized_channel_list.get(1);
				int [] red_green = (int [])resized_channel_list.get(2);
				green = DeltaMapper.getDifference(red, red_green);
		    }
			else if(min_set_id == 2)
			{ 
				blue  = (int [])resized_channel_list.get(0);
				red   = (int [])resized_channel_list.get(1);
				int [] blue_green = (int [])resized_channel_list.get(2);
				green = DeltaMapper.getDifference(blue, blue_green);
			}
			else if(min_set_id == 3)
			{ 
				blue       = (int [])resized_channel_list.get(0);
				int [] blue_green = (int [])resized_channel_list.get(1);
				green = DeltaMapper.getDifference(blue, blue_green);
				int [] red_green  = (int [])resized_channel_list.get(2);
				red = DeltaMapper.getSum(red_green, green);
			}
			else if(min_set_id == 4)
			{ 
				blue  = (int [])resized_channel_list.get(0);
				int [] blue_green = (int [])resized_channel_list.get(1);
				green = DeltaMapper.getDifference(blue, blue_green);
				int [] red_blue   = (int [])resized_channel_list.get(2);
				red        = DeltaMapper.getSum(blue, red_blue);
			}
		    else if(min_set_id == 5)
			{
		    	green = (int [])resized_channel_list.get(0);
		    	red   = (int [])resized_channel_list.get(1);
		    	int [] blue_green = (int [])resized_channel_list.get(2);
		    	blue  = DeltaMapper.getSum(blue_green, green);
			}
			else if(min_set_id == 6)
			{
				red     = (int [])resized_channel_list.get(0);
				int [] blue_green = (int [])resized_channel_list.get(1);
				int [] red_green  = (int [])resized_channel_list.get(2);
				for(int i = 0; i < red_green.length; i++)
					red_green[i] = -red_green[i];
				green = DeltaMapper.getSum(red_green, red);
				blue = DeltaMapper.getSum(blue_green, green);	
			}
			else if(min_set_id == 7)
			{
				green   = (int [])resized_channel_list.get(0);
				int [] blue_green = (int [])resized_channel_list.get(1);
				blue = DeltaMapper.getSum(green, blue_green);
				int [] red_green = (int [])resized_channel_list.get(2);
				red  = DeltaMapper.getSum(green, red_green);
			}
			else if(min_set_id == 8)
			{
				green = (int [])resized_channel_list.get(0);
				int [] red_green = (int [])resized_channel_list.get(1);
				red   = DeltaMapper.getSum(green, red_green);
				int [] red_blue  = (int [])resized_channel_list.get(1);
				blue  = DeltaMapper.getDifference(red, red_blue);
		    }
			else if(min_set_id == 9)
			{
				red   = (int [])resized_channel_list.get(0);
				int [] red_green = (int [])resized_channel_list.get(1);
				green = DeltaMapper.getDifference(red, red_green); 
				int [] red_blue  = (int [])resized_channel_list.get(2);
				blue  = DeltaMapper.getDifference(red, red_blue);
			}
			
			int [] original_blue  = (int [])channel_list.get(0);
		    int [] original_green = (int [])channel_list.get(1);
		    int [] original_red   = (int [])channel_list.get(2);
		
		    
		    int [][] error = new int [3][xdim * ydim];
		    
		    for(int i = 0; i < xdim * ydim; i++)
		    {
		    	blue[i]  <<= pixel_shift;
		    	green[i] <<= pixel_shift;
		    	red[i]   <<= pixel_shift;
		    	error[0][i] = original_blue[i] - blue[i];
		    	error[1][i] = original_green[i] - green[i];
		    	error[2][i]  = original_red[i] - red[i];
		    }
	
		    double correction_value = 0;
		    double factor           = 1;
		    for(int i = 0; i < ydim; i++)
			{
				for(int j = 0; j < xdim; j++)
				{
					int k = i * xdim + j;
					
					int pixel = 0;
					
		    		correction_value = error[0][k];
		    		factor = correction;
		    		factor /= 10;
		    		correction_value *= factor;
					pixel |= (blue[k] + (int)correction_value) << 16;
					
					correction_value = error[1][k];
		    		factor = correction;
		    		factor /= 10;
		    		correction_value *= factor;
				    pixel |= (green[k] + (int)correction_value) << 8;
					
					correction_value = error[2][k];
		    		factor = correction;
		    		factor /= 10;
		    		correction_value *= factor;
					pixel |= red[k] + (int)correction_value;
					
				    image.setRGB(j, i, pixel);
				}
			}
		   
			image_canvas.repaint();
			initialized = true;
			// Current information can now be located with min set id from channel arrays,
			// along with table list and string list.
		}
	}
	
	class SaveHandler implements ActionListener
	{
		public void actionPerformed(ActionEvent event)
		{
			if(!initialized)
				apply_item.doClick();
			int channel_id[] = DeltaMapper.getChannels(min_set_id);
			
			try
		    {
		        DataOutputStream out = new DataOutputStream(new FileOutputStream(new File("foo")));
		        
		        // Dimensions of full sized frame
		        out.writeShort(xdim);
		        out.writeShort(ydim);
		        
		        // Compression parameters
		        out.writeByte(pixel_shift);
		        out.writeByte(pixel_quant);
		        out.writeByte(min_set_id);
		        
		        for(int i = 0; i < 3; i++)
		        {
		        	int j = channel_id[i];
		        	
		        	// Don't really use this for reference channels.
		        	out.writeInt(channel_min[j]);
		        	
		        	out.writeInt(channel_init[j]);
		        	out.writeInt(channel_delta_min[j]);
		        
		        	out.writeInt(channel_length[j]);
		        	// If the string didn't compress, 
		        	// this is the same as the uncompressed length.
		        	out.writeInt(channel_compressed_length[j]);
		        	out.writeByte(channel_iterations[i]);
		        	
		        	int [] table = (int[])table_list.get(i);
		            out.writeShort(table.length);
		            for(int k = 0; k < table.length; k++)
		                out.writeInt(table[k]);
		           
		            if(segment == 0 || channel_segmented[i] == false)
		            {
		            	int number_of_segments = 1;
		            	out.writeInt(number_of_segments);
		            	
		                // We need to clip the string to get maximum compression.
		                byte [] unclipped_string = (byte [])string_list.get(i);
		                int byte_length = channel_compressed_length[j] / 8;
		                // If we have odd bits, add a byte.
		                if(channel_compressed_length[j] % 8 != 0)
		            	    byte_length++; 
		                // Add a byte to store how many times the string was compressed.
		                byte_length++;
		            
		                byte [] string = new byte[byte_length];
		                for(int k = 0; k < byte_length; k++)
		            	    string[k] = unclipped_string[k];
		            
		                // If the string was uncompressed, make sure
		                // the last byte is 0, so the reader can check.
		                if(channel_compressed_length[j] == channel_length[j])
		            	    string[byte_length - 1] = 0;
		            
		                out.writeInt(string.length);
		                out.write(string, 0, string.length);
		            }
		            else
			        {
			            ArrayList segment_data_list = (ArrayList)channel_data.get(i);
						ArrayList segment_length    = (ArrayList)segment_data_list.get(0);
						ArrayList segment_data      = (ArrayList)segment_data_list.get(1);
						int max_segment_byte_length = (int)segment_data_list.get(2);
						
			            // Number of segments.
			            int n = segment_data.size();
			            out.writeInt(n);
			            //System.out.println("Wrote int number of segments " + n);
			            
			            out.writeInt(max_segment_byte_length);
			            //System.out.println("Wrote max segment byte length " + max_segment_byte_length);
			            
			            if(max_segment_byte_length < 8192)
			            	System.out.println("Max segment length small enough to pack extra bits in a short.");
			            else if (max_segment_byte_length > Short.MAX_VALUE)
			            	System.out.println("Max segment length too large to fit in a short.");
			            
			            for(int k = 0; k < n; k++)
			            {
			            	int segment_bit_length  = (int)segment_length.get(k);
			            	byte [] segment = (byte [])segment_data.get(k);
			            	short extra_bits = (byte)(segment.length  * 8 - segment_bit_length - 8);
			            	
			            	/*
			            	out.writeInt(segment.length);
			            	out.writeByte(extra_bits);
			            	*/
			            	
			                // Possible bug here.
			            	if(max_segment_byte_length < 80192)
			            	{
			            	    short packed_segment_length = (short)segment.length;
		            	        packed_segment_length <<= 3;
		            	        packed_segment_length += extra_bits;
			            	    out.writeShort(packed_segment_length);
			            	}
			            	else if(max_segment_byte_length <= Short.MAX_VALUE)
			            	{
			            		out.writeShort(segment.length);	
			            		out.writeByte(extra_bits);
			            	}
			            	else
			            	{
			            		int packed_segment_length = segment.length;
			            		packed_segment_length <<= 3;
		            	        packed_segment_length += extra_bits;
			            	    out.writeInt(packed_segment_length);
			            	}
			            
			            	/*
			            	if((max_segment_byte_length * 8) < 80192)
			            	{
			            	    short packed_segment_length = (short)segment.length;
		            	        packed_segment_length <<= 3;
		            	        packed_segment_length += extra_bits;
			            	    out.writeShort(packed_segment_length);
			            	}
			            	else if((max_segment_byte_length * 8) <= Short.MAX_VALUE)
			            	{
			            		out.writeShort(segment.length);	
			            		out.writeByte(extra_bits);
			            	}
			            	else
			            	{
			            		int packed_segment_length = segment.length;
			            		packed_segment_length <<= 3;
		            	        packed_segment_length += extra_bits;
			            	    out.writeInt(packed_segment_length);
			            	}
			            	*/
			            	
			            	
			            	
			            	
			            	out.write(segment, 0, segment.length);
			            }
			        }
		        }
		        
		        
		        out.flush();
		        out.close();
		        
		        File file = new File("foo");
		        long file_length = file.length();
		        double compression_rate = file_length;
		        compression_rate /= xdim * ydim * 3;
		        System.out.println("Delta bits compression rate is " + String.format("%.4f", compression_rate));
		        
		    }
			catch(Exception e)
			{
				System.out.println(e.toString());
			}
		}
	}
}