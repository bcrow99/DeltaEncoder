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

public class SimpleWriter
{
	BufferedImage image;
	ImageCanvas   image_canvas;
	int           xdim, ydim;
	JMenuItem     apply_item;
	String        filename;
	int []        pixel;
	
	// Initial parameters for compression.
	int pixel_quant    = 4;
	int pixel_shift    = 3;
	
	// This value is inversely related to the lengths of the segments.
	int segment_length = 14;
	
	int correction     = 0;
	int min_set_id     = 0;
	int delta_type     = 2;
	
	
	int    [] set_sum, channel_sum;
	String [] set_string;
	String [] channel_string;
	
	
	int [] channel_init;
	int [] channel_min;
	int [] channel_delta_min;
	
	
	int  [] channel_length;
	int  [] channel_compressed_length;
	int  [] channel_string_type;
	int  [] number_of_segments;
	byte [] channel_iterations;
	
	int [] max_bytelength;
	
	long file_length;
	double file_compression_rate;

	ArrayList channel_list, table_list, string_list, map_list, segment_list;
	
	boolean initialized = false;
	
	public static void main(String[] args)
	{
		if (args.length != 1)
		{
			System.out.println("Usage: java SimpleWriter <filename>");
			System.exit(0);
		}
		
		String prefix       = new String("");
		//String prefix       = new String("C:/Users/Brian Crowley/Desktop/");
		String filename     = new String(args[0]);
		
		SimpleWriter writer = new SimpleWriter(prefix + filename);
	}

	public SimpleWriter(String _filename)
	{
		filename = _filename;
		try
		{
			File file                    = new File(filename);
			file_length                  = file.length();
			BufferedImage original_image = ImageIO.read(file);
			int raster_type              = original_image.getType();
			xdim                         = original_image.getWidth();
			ydim                         = original_image.getHeight();
		
			System.out.println("Xdim = " + xdim + ", ydim = " + ydim);
			System.out.println();
			
		    channel_list = new ArrayList();
		    table_list   = new ArrayList();
		    string_list  = new ArrayList();
		    map_list     = new ArrayList();
		    segment_list = new ArrayList();
		    
		    channel_string    = new String[6];
		    channel_string[0] = new String("blue");
		    channel_string[1] = new String("green");
		    channel_string[2] = new String("red");
		    channel_string[3] = new String("blue-green");
		    channel_string[4] = new String("red-green");
		    channel_string[5] = new String("red-blue");
		    
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
            max_bytelength      = new int[3];
            
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
		        int [] blue  = new int[xdim * ydim];
		        int [] green = new int[xdim * ydim];
		        int [] red   = new int[xdim * ydim];
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
			    
			    JFrame frame = new JFrame("Delta Writer " + filename);
			    
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
						int k = 0;
						for(int i = 0; i < xdim; i++)
					    	for(int j = 0; j < ydim; j++)
					    		image.setRGB(i, j, pixel[k++]); 
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
				shift_slider.setValue(pixel_shift);
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
				
				JMenuItem segment_item   = new JMenuItem("Segmentation");
				JDialog   segment_dialog = new JDialog(frame, "Segmentation");
				ActionListener segment_handler = new ActionListener()
				{
					public void actionPerformed(ActionEvent e)
					{
						Point location_point = frame.getLocation();
						int x = (int) location_point.getX();
						int y = (int) location_point.getY();

						segment_dialog.setLocation(x, y - 150);
						segment_dialog.pack();
						segment_dialog.setVisible(true);
					}
				};
				segment_item.addActionListener(segment_handler);
				
				JPanel segment_panel = new JPanel(new BorderLayout());
				JSlider segment_slider = new JSlider();
				segment_slider.setMinimum(0);
				segment_slider.setMaximum(14);
				segment_slider.setValue(segment_length);
				JTextField segment_value = new JTextField(3);
				segment_value.setText(" " + segment_length + " ");
				ChangeListener segment_slider_handler = new ChangeListener()
				{
					public void stateChanged(ChangeEvent e)
					{
						JSlider slider = (JSlider) e.getSource();
						segment_length = slider.getValue();
						if(slider.getValueIsAdjusting() == false)
						{
							apply_item.doClick();
							segment_value.setText(" " + segment_length + " ");
						}
					}
				};
				segment_slider.addChangeListener(segment_slider_handler);
				segment_panel.add(segment_slider, BorderLayout.CENTER);
				segment_panel.add(segment_value, BorderLayout.EAST);
				segment_dialog.add(segment_panel);
				settings_menu.add(segment_item);
				
				// The correction value is a convenience to help see what
				// quantizing does to the original image. Instead of
				// simply switching back and forth between the original
				// image and the quantized image, the user can slowly
				// move back and forth between the two images.
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
				frame.setLocation(5, 200);
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
		// The minimum set changes depending on how much the 
		// image is quantized, so it has to be reevaluated
		// with every change in the parameters.
		public void actionPerformed(ActionEvent event)
		{
			ArrayList shifted_channel_list = new ArrayList();
			//byte [] iterations = new byte[3];
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
		    	
		    	channel_sum[i] = DeltaMapper.getIdealSum(quantized_channel, new_xdim, new_ydim, 20);
		    	//channel_sum[i] = DeltaMapper.getIdealSum(quantized_channel, new_xdim, new_ydim);
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
			//min_set_id = 0;
			
			file_compression_rate  = file_length;
			file_compression_rate /= xdim * ydim * 3;
			
			System.out.println("A set of channels with the lowest delta sum is " + set_string[min_index]);
			System.out.println("Set id is " + min_set_id);
			System.out.println();
			
			int [] channel_id = DeltaMapper.getChannels(min_set_id);
			
			table_list.clear();
			string_list.clear();
			segment_list.clear();
			
			map_list.clear();
			
			for(int i = 0; i < 3; i++)
			{
				int j = channel_id[i];
				
				int [] quantized_channel = (int[])quantized_channel_list.get(j);
				
				ArrayList result = DeltaMapper.getAverageDeltasFromValues(quantized_channel, new_xdim, new_ydim);
				
				int [] delta  = (int [])result.get(1);
				
				ArrayList delta_string_list = StringMapper.getStringList(delta);
				channel_delta_min[j]        = (int)delta_string_list.get(0);
				channel_length[j]           = (int)delta_string_list.get(1);
				int [] string_table         = (int [])delta_string_list.get(2);
				byte [] compression_string  = (byte [])delta_string_list.get(3);
				
				table_list.add(string_table);
				string_list.add(compression_string);
				
				channel_compressed_length[j] = StringMapper.getBitlength(compression_string);
		        channel_string_type[i]       = StringMapper.getType(compression_string);
		        channel_iterations[i]        = StringMapper.getIterations(compression_string);
		        
		        if(segment_length == 0)
		        {
		        	ArrayList segments = new ArrayList();
		        	segments.add(compression_string);
		        	segment_list.add(segments);
		        }
		        else
		        {
		        	int bitlength  = StringMapper.getBitlength(compression_string);
		        	int original_number_of_segments = (int)Math.pow(2,  segment_length);
		        	
            		int minimum_segment_length = bitlength / original_number_of_segments;
            		int remainder = minimum_segment_length % 8;
            		minimum_segment_length -= remainder;
     	          
     	            // The threshold that seems to produce maximum compression is around 96.
     	            if(minimum_segment_length < 96)
     	            	minimum_segment_length = 96;  
     	           ArrayList segment_data_list = SegmentMapper.getMergedSegmentedData2(compression_string, minimum_segment_length);
   	               ArrayList  segments         = (ArrayList)segment_data_list.get(0);
   	               max_bytelength[i]           = (int)segment_data_list.get(1);
   	             
   	               if(segments.size() == 1)
   	               {
   	            	    // The single merged segment should be identical to the original string.
   	            	    ArrayList segment = new ArrayList();
   	            	    segment.add(compression_string);
   	            	    segment_list.add(segment);
   	               }
   	               else
   	                   segment_list.add(segments); 
		        }
			}
			
			ArrayList resized_channel_list = new ArrayList();
			
			for(int i = 0; i < 3; i++)
		    {
		        int j  = channel_id[i];
		        
		        int  [] table  = (int [])table_list.get(i);
		        int  [] delta  = new int[new_xdim * new_ydim]; 
		       
		        ArrayList segments     = (ArrayList)segment_list.get(i);
		        int number_of_segments = segments.size();
		        //System.out.println("The number of segments is " + number_of_segments);
		        if(number_of_segments == 1)
		        {
		        	byte [] current_string = (byte [])segments.get(0);
			        int current_iterations = StringMapper.getIterations(current_string);
		        	
			        if(current_iterations == 0 || current_iterations == 16)
			        {
			        	int number_unpacked = StringMapper.unpackStrings2(current_string, table, delta);
					    if(number_unpacked != new_xdim * new_ydim)
					        System.out.println("Number of values unpacked does not agree with image dimensions.");	
			        }
			        else
			        {
			        	if(current_iterations < 16)
			        	{
			        	    byte [] decompressed_string = StringMapper.decompressZeroStrings(current_string);
			        	    int number_unpacked = StringMapper.unpackStrings2(decompressed_string, table, delta);
						    if(number_unpacked != new_xdim * new_ydim)
						        System.out.println("Number of values unpacked does not agree with image dimensions.");
			        	}
			        	else
			        	{    
			        		byte [] decompressed_string = StringMapper.decompressOneStrings(current_string);
			        	    int number_unpacked = StringMapper.unpackStrings2(decompressed_string, table, delta);
						    if(number_unpacked != new_xdim * new_ydim)
						        System.out.println("Number of values unpacked does not agree with image dimensions.");
			        	}	
			        }
		        }
		        else
		        {
		            ArrayList decompressed_segments = new ArrayList();
		            
		            int max_zero_iterations = 0;
		            int max_one_iterations  = 0;
		            int max_zero_index      = 0;
		            int max_one_index       = 0;
		            
		            
		            int [] iterations = new int[number_of_segments];
		            for(int k = 0; k < number_of_segments; k++)
		            {
		            	byte [] current_string = (byte [])segments.get(k);
				        int current_iterations = StringMapper.getIterations(current_string);
				        iterations[k] = current_iterations;
				       
				        if(current_iterations == 0 || current_iterations == 16)
				        	decompressed_segments.add(current_string);
				        else
				        {
				        	if(current_iterations < 16)
				        	{
				        	    byte [] decompressed_string = StringMapper.decompressZeroStrings(current_string);
				        	    decompressed_segments.add(decompressed_string);
				        	    if(current_iterations > max_zero_iterations)
				        	    {
				        	    	max_zero_iterations = current_iterations;
				        	    	max_zero_index      = k;
				        	    }
				        	}
				        	else
				        	{    
				        		byte [] decompressed_string = StringMapper.decompressOneStrings(current_string);
				        		decompressed_segments.add(decompressed_string);
				        		current_iterations -= 16;
				        		if(current_iterations > max_one_iterations)
				        		{
				        	    	max_one_iterations = current_iterations;
				        	    	max_one_index      = k;
				        		}
				        	}		
				        }
		            }
		            
		            System.out.println("Maximum number of zero iterations was " + max_zero_iterations + " on segment " + max_zero_index);
		            
		            System.out.println();
		            
		            byte [] mask = SegmentMapper.getPositiveMask();
		            
		            /*
		            if(max_zero_iterations > 0)
		            {
		                byte [] compressed_zero_segment   = (byte []) segments.get(max_zero_index);
			                
		                System.out.println("Length of compressed zero segment is "   + compressed_zero_segment.length);
		                System.out.println("Iterations is " + max_zero_iterations);
		                System.out.println();
			               
		                System.out.println("Byte values:");
		                for(int k = 0; k < compressed_zero_segment.length; k++)
		                {
		                    int value = compressed_zero_segment[k];
		                    if(value < 0)
		                    	value += 2 * Byte.MAX_VALUE + 2;
		                    System.out.print(value + " ");    
		                }
		                System.out.println();
		                System.out.println("Bit values:");
		                for(int k = 0; k < compressed_zero_segment.length; k++)
		                {
		                    for(int m = 7; m >= 0; m--)
		                    {
		                        if((mask[m] & compressed_zero_segment[k]) == 0)
		                        	System.out.print("0 ");
		                        else
		                        	System.out.print("1 ");	
		                    }
		                    System.out.print("  ");
		                }
		                System.out.println();
		                System.out.println();       
		            	
		            	
		            	byte [] decompressed_zero_segment = (byte []) decompressed_segments.get(max_zero_index);
		                
		                System.out.println("Length of decompressed zero segment is " + decompressed_zero_segment.length);
		                System.out.println();
		                
		                System.out.println("Byte values:");
		                for(int k = 0; k < decompressed_zero_segment.length; k++)
		                {
		                    int value = decompressed_zero_segment[k];
		                    if(value < 0)
		                    	value += 2 * Byte.MAX_VALUE + 2;
		                    System.out.print(value + " ");    
		                }
		                System.out.println();
		                System.out.println("Bit values:");
		                for(int k = 0; k < decompressed_zero_segment.length; k++)
		                {
		                	int n = 7;
		                    for(int m = 7; m >= 0; m--)
		                    {
		                        if((mask[m] & decompressed_zero_segment[k]) == 0)
		                        	System.out.print("0 ");
		                        else
		                        	System.out.print("1 ");	
		                    }
		                    System.out.print("  ");  
		                }
		                System.out.println();
		                System.out.println();
		                System.out.println();
		              
		              
		                int current_max = max_zero_iterations - 1;
		                int current_index = 0;
		                
		                while(current_max > 0)
		                {
		                	boolean found_current_max = false;
		                	outer: for(int k = 0; k < number_of_segments; k++)
		                	{
		                	    if(iterations[k] == current_max)	
		                	    {
		                	        current_index = k;
		                	        found_current_max = true;
		                	        break outer;
		                	    }
		                	}
		                	
		                	
		                	if(found_current_max)
		                	{
		                	compressed_zero_segment   = (byte []) segments.get(current_index);
		                	
		                	
			                System.out.println("Length of compressed zero segment is "   + compressed_zero_segment.length);
			                //System.out.println("Iterations is " + current_max);
			                
			                System.out.println("Current max is " + current_max);
			                System.out.println("Iterations from table is " + iterations[current_index]);
			                
			                int current_iterations = StringMapper.getIterations(compressed_zero_segment);
			                System.out.println("Iterations is " + current_iterations);
		                	if(current_iterations != iterations[current_index])
		                	{
		                	   System.out.println("Iterations appended to string does not agree with table.");	
		                	   
		                	}
			                System.out.println();
				               
			                System.out.println("Byte values:");
			                for(int k = 0; k < compressed_zero_segment.length; k++)
			                {
			                    int value = compressed_zero_segment[k];
			                    if(value < 0)
			                    	value += 2 * Byte.MAX_VALUE + 2;
			                    System.out.print(value + " ");    
			                }
			                System.out.println();
			                System.out.println("Bit values:");
			                for(int k = 0; k < compressed_zero_segment.length; k++)
			                {
			                    for(int m = 7; m >= 0; m--)
			                    {
			                        if((mask[m] & compressed_zero_segment[k]) == 0)
			                        	System.out.print("0 ");
			                        else
			                        	System.out.print("1 ");	
			                    }
			                    System.out.print("  ");
			                }
			                current_iterations = StringMapper.getIterations(compressed_zero_segment);
			                System.out.println("Iterations is " + current_iterations);
			                
			                System.out.println();
			                System.out.println();       
			            	
			            	decompressed_zero_segment = (byte []) decompressed_segments.get(current_index);
			                
			                System.out.println("Length of decompressed zero segment is " + decompressed_zero_segment.length);
			                System.out.println("Byte values:");
			                for(int k = 0; k < decompressed_zero_segment.length; k++)
			                {
			                    int value = decompressed_zero_segment[k];
			                    if(value < 0)
			                    	value += 2 * Byte.MAX_VALUE + 2;
			                    System.out.print(value + " ");    
			                }
			                System.out.println();
			                System.out.println("Bit values:");
			                for(int k = 0; k < decompressed_zero_segment.length; k++)
			                {
			                    for(int m = 7; m >= 0; m--)
			                    {
			                        if((mask[m] & decompressed_zero_segment[k]) == 0)
			                        	System.out.print("0 ");
			                        else
			                        	System.out.print("1 ");	
			                    }
			                    System.out.print("  ");  
			                }
			                System.out.println();
			                System.out.println();
			                System.out.println();
		                	}
		                	else
		                	{
		                		System.out.println("No segments with " + current_max + " iterations.");
		                		System.out.println();
				                System.out.println();
		                	}
			                
			                current_max--;
		                } 
		            }
		            
		            */
		            
		            System.out.println("Maximum number of one iterations was " + max_one_iterations + " on segment " + max_one_index);
		            
		            if(max_one_iterations > 0)
		            {
		                byte [] compressed_one_segment   = (byte []) segments.get(max_one_index);
			                
		                System.out.println("Length of compressed one segment is "   + compressed_one_segment.length);
		                System.out.println("Iterations is " + max_one_iterations);
		                System.out.println();
			               
		                System.out.println("Byte values:");
		                for(int k = 0; k < compressed_one_segment.length; k++)
		                {
		                    int value = compressed_one_segment[k];
		                    if(value < 0)
		                    	value += 2 * Byte.MAX_VALUE + 2;
		                    System.out.print(value + " ");    
		                }
		                System.out.println();
		                System.out.println("Bit values:");
		                for(int k = 0; k < compressed_one_segment.length; k++)
		                {
		                    for(int m = 7; m >= 0; m--)
		                    {
		                        if((mask[m] & compressed_one_segment[k]) == 0)
		                        	System.out.print("0 ");
		                        else
		                        	System.out.print("1 ");	
		                    }
		                    System.out.print("  ");
		                }
		                System.out.println();
		                System.out.println();       
		            	
		            	
		            	byte [] decompressed_one_segment = (byte []) decompressed_segments.get(max_one_index);
		                
		                System.out.println("Length of decompressed one segment is " + decompressed_one_segment.length);
		                System.out.println();
		                
		                System.out.println("Byte values:");
		                for(int k = 0; k < decompressed_one_segment.length; k++)
		                {
		                    int value = decompressed_one_segment[k];
		                    if(value < 0)
		                    	value += 2 * Byte.MAX_VALUE + 2;
		                    System.out.print(value + " ");    
		                }
		                System.out.println();
		                System.out.println("Bit values:");
		                for(int k = 0; k < decompressed_one_segment.length; k++)
		                {
		                	int n = 7;
		                    for(int m = 7; m >= 0; m--)
		                    {
		                        if((mask[m] & decompressed_one_segment[k]) == 0)
		                        	System.out.print("0 ");
		                        else
		                        	System.out.print("1 ");	
		                    }
		                    System.out.print("  ");  
		                }
		                System.out.println();
		                System.out.println();
		                System.out.println();
		              
		              
		                int current_max = max_one_iterations - 1;
		                int current_index = 0;
		                
		                while(current_max > 0)
		                {
		                	boolean found_current_max = false;
		                	outer: for(int k = 0; k < number_of_segments; k++)
		                	{
		                	    if(iterations[k] == current_max)	
		                	    {
		                	        current_index = k;
		                	        found_current_max = true;
		                	        break outer;
		                	    }
		                	}
		                	
		                	
		                	if(found_current_max)
		                	{
		                		compressed_one_segment   = (byte []) segments.get(current_index);
			                	
			                	
				                System.out.println("Length of compressed one segment is "   + compressed_one_segment.length);
				               
				                System.out.println();
					               
				                System.out.println("Byte values:");
				                for(int k = 0; k < compressed_one_segment.length; k++)
				                {
				                    int value = compressed_one_segment[k];
				                    if(value < 0)
				                    	value += 2 * Byte.MAX_VALUE + 2;
				                    System.out.print(value + " ");    
				                }
				                System.out.println();
				                System.out.println("Bit values:");
				                for(int k = 0; k < compressed_one_segment.length; k++)
				                {
				                    for(int m = 7; m >= 0; m--)
				                    {
				                        if((mask[m] & compressed_one_segment[k]) == 0)
				                        	System.out.print("0 ");
				                        else
				                        	System.out.print("1 ");	
				                    }
				                    System.out.print("  ");
				                }
				                
				                System.out.println();
				                System.out.println();       
				            	
				            	decompressed_one_segment = (byte []) decompressed_segments.get(current_index);
				                
				                System.out.println("Length of decompressed one segment is " + decompressed_one_segment.length);
				                System.out.println("Byte values:");
				                for(int k = 0; k < decompressed_one_segment.length; k++)
				                {
				                    int value = decompressed_one_segment[k];
				                    if(value < 0)
				                    	value += 2 * Byte.MAX_VALUE + 2;
				                    System.out.print(value + " ");    
				                }
				                System.out.println();
				                System.out.println("Bit values:");
				                for(int k = 0; k < decompressed_one_segment.length; k++)
				                {
				                    for(int m = 7; m >= 0; m--)
				                    {
				                        if((mask[m] & decompressed_one_segment[k]) == 0)
				                        	System.out.print("0 ");
				                        else
				                        	System.out.print("1 ");	
				                    }
				                    System.out.print("  ");  
				                }
				                System.out.println();
				                System.out.println();
				                System.out.println();
		                	}
		                	else
		                	{
		                		System.out.println("No segments with " + current_max + " iterations.");
		                		System.out.println();
				                System.out.println();
		                	}
			                
			                current_max--;
		                } 
		            }
		           
		            System.out.println();
		            // Error checking.
		            
		            // Create buffer to put concatenated strings.
				    int string_length = 0;
				    for(int k = 0; k < decompressed_segments.size(); k++)
				    {
				        byte [] current_string = (byte [])decompressed_segments.get(k);
				        string_length += current_string.length - 1;
				    }
				    
				    // Add byte for string information.
				    string_length++; 
				    byte [] reconstructed_string = new byte[string_length];
				    
				    // Concatenate strings less trailing byte with individual string information.
				    int offset = 0;
				    int total_bitlength = 0;
				    for(int k = 0; k < decompressed_segments.size(); k++)
					{
						byte [] segment = (byte [])decompressed_segments.get(k);
						for(int m = 0; m < segment.length - 1; m++)
							reconstructed_string[offset + m] = segment[m];	
						offset += segment.length - 1;
						int segment_bitlength = StringMapper.getBitlength(segment);
						total_bitlength += segment_bitlength;
					}
					
					if(total_bitlength % 8 != 0)
					{
						int modulus = total_bitlength % 8;
						byte extra_bits = (byte)(8 - modulus);
						extra_bits <<= 5;
						reconstructed_string[reconstructed_string.length - 1] = extra_bits;
					}
					reconstructed_string[reconstructed_string.length - 1] |= channel_iterations[i];
				    
		            
			        if(channel_iterations[i] == 0 || channel_iterations[i] == 16)
			        {
			        	int number_unpacked = StringMapper.unpackStrings2(reconstructed_string, table, delta);
					    if(number_unpacked != new_xdim * new_ydim)
					        System.out.println("Number of values unpacked does not agree with image dimensions.");		
			        }
			        else
			        {
			        	if(channel_iterations[i] < 16)
			        	{
			        	    byte [] decompressed_string = StringMapper.decompressZeroStrings(reconstructed_string);
			        	    int number_unpacked = StringMapper.unpackStrings2(decompressed_string, table, delta);
						    if(number_unpacked != new_xdim * new_ydim)
						        System.out.println("Number of values unpacked does not agree with image dimensions.");
			        	}
			        	else
			        	{  
			        		byte [] decompressed_string = StringMapper.decompressOneStrings(reconstructed_string);
			        	    int number_unpacked = StringMapper.unpackStrings2(decompressed_string, table, delta);
						    if(number_unpacked != new_xdim * new_ydim)
						        System.out.println("Number of values unpacked does not agree with image dimensions.");
			        	}		
			        }
		        }
		        
		        for(int k = 1; k < delta.length; k++)
		    	    delta[k] += channel_delta_min[j];
		    
		        int [] channel = DeltaMapper.getValuesFromAverageDeltas(delta, new_xdim , new_ydim, channel_init[j]); 
		    
			    if(j > 2)
				    for(int k = 0; k < channel.length; k++)
					    channel[k] += channel_min[j];
			
			    if(new_xdim != xdim || new_ydim != ydim)
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
		    	
		    	if(correction != 0)
		    	{
		    		double factor = correction;
		    		factor       /= 10;
		    	    
		    		double addend = (double)error[0][i] * factor;
		    		blue[i] += (int)addend;
		    		
		    		addend = (double)error[1][i] * factor;
		    		green[i] += addend;
		    		
		    		addend = (double)error[2][i] * factor;
		    		red[i] += addend;	
		    	}
		    }
	
		    int k = 0;
		    for(int i = 0; i < ydim; i++)
			{
				for(int j = 0; j < xdim; j++)
				{
				    image.setRGB(j, i, (blue[k] << 16) + (green[k] << 8) + red[k]);
				    k++;
				}
			}
			image_canvas.repaint();
			initialized = true;
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
		        
		        out.writeShort(xdim);
		        out.writeShort(ydim);
		        out.writeByte(pixel_shift);
		        out.writeByte(pixel_quant);
		        out.writeByte(min_set_id);
		        out.writeByte(delta_type);
		     
		        for(int i = 0; i < 3; i++)
		        {
		        	int j = channel_id[i];
		        	
		        	out.writeInt(channel_min[j]);
		        	out.writeInt(channel_init[j]);
		        	out.writeInt(channel_delta_min[j]); 
	        	    out.writeInt(channel_length[j]);
	        	    out.writeInt(channel_compressed_length[j]);
	        	    out.writeByte(channel_iterations[i]);
	        	    int [] table = (int[])table_list.get(i);
	                out.writeShort(table.length);
	                int max_byte_value  = Byte.MAX_VALUE * 2 + 1;
	                if(table.length <= max_byte_value)
                    {
	                    for(int k = 0; k < table.length; k++)
	                        out.writeByte(table[k]);
                     }
	                else 
	                {
	                    for(int k = 0; k < table.length; k++)
	                        out.writeShort(table[k]);
                    }
		        	
		           
		            
		            ArrayList segments = (ArrayList)segment_list.get(i);
		            int number_of_segments = segments.size();
		            out.writeInt(number_of_segments);
	            	 
	            	 if(number_of_segments == 1)
	            	 {
	            		 byte [] string = (byte [])string_list.get(i);
	            		 out.writeInt(string.length);
	            		 out.writeInt(0);
	                	 out.write(string, 0, string.length);
	            	 }
	            	 else
	            	 {
	            		 out.writeInt(max_bytelength[i]);
	            		 // Zipping the segments lengths works better than compressing them
	            		 // as unary strings.  The case where huffman coding does better than
	            		 // recursive unary string coding is with a larger number of elements
	            		 // with a more uniform distribution.  
	            		 // The reason why the bitwise compression works better than zipping
	            		 // on small numbers of elements probably relates to the difference in
	            		 // the overhead.  That means that the main advantages over huffman
	            		 // coding are when the data allows for recursion because of a skewed
	            		 // distribution or with small numbers of elements, when the overhead comes into play. 
	            		 // Probably why compressing the maps works better than zipping them is the values are 
	            		 // only 0 thru 1 and 7.  
	            		
	            		 // When the segments lengths are in bytes and segment lengths are grouped
	            		 // together and zipped, and then sent as a block instead of with each
	            		 // segments the compression rate is improved.  When the lengths have to be 
	            		 // represented as shorts or int the difference is not very significant, 
	            		 // at least when the numbers of segments are small.
	            		 
	            		 // It still seems like the finer the segmentation, the more compression
	            		 // there should be, especially if the segment lengths are not included.
	            		 // Probably need to look into the merge process again, and maximize the
	            		 // the number of opposing string types.  Uneven segment lengths might help
	            		 // in that regard. 
	            		
	            		 if(max_bytelength[i] <= Byte.MAX_VALUE * 2 + 1)
	            		 {
	            			// Grouping the segments lengths.
	            			// For now we won't bother modifying the writer/reader
	            			// protocol.
	            			// This offers an example of when zipping 
	            			// (which we are using as a proxy for pure huffman coding)
	            			// does better than unary string compression.
	            			
	            			/*
							byte [] segment_length = new byte[number_of_segments];  
							for(int k = 0; k < number_of_segments; k++)
							{
							    byte[] current_segment = (byte [])segments.get(k);
							    segment_length[k]      = (byte)current_segment.length;
							}  
							byte [] zipped_segment_lengths = new byte[number_of_segments * 2];
							Deflater deflater = new Deflater();
				            deflater.setInput(segment_length);
				            deflater.finish();
				            int zipped_length = deflater.deflate(zipped_segment_lengths);
				            deflater.end(); 
				            System.out.println("Maximum segment length requires an unsigned byte.");
							System.out.println("The length of the segment lengths in bytes is " + number_of_segments);
							System.out.println("The length of the zipped segment lengths is "   + zipped_length);
							System.out.println(); 
							out.writeShort(zipped_length);
							out.write(zipped_segment_lengths, 0, zipped_length);
							*/
							
		            		
							for(int k = 0; k < number_of_segments; k++)
		    				{
		    					byte[] current_segment = (byte [])segments.get(k); 
		    					out.writeByte(current_segment.length); 
		    					out.write(current_segment, 0, current_segment.length);
		    				}  
	            		 }
						 else if(max_bytelength[i] <= Short.MAX_VALUE * 2 + 1)
						 {
							 for(int k = 0; k < number_of_segments; k++)
		    				 {
		    					 byte[] current_segment = (byte [])segments.get(k); 
		    					 out.writeShort(current_segment.length); 
		    					 out.write(current_segment, 0, current_segment.length);
		    				 }  
							 
							 /*
							 System.out.println("Maximum segment length requires an unsigned short.");
							 System.out.println("Total compressed length was " + total_compressed_length);
							 System.out.println("Total zipped length was " + total_zipped_length);
							 System.out.println(); 
							 */
							 
						 }
						 else
						 {
							for(int k = 0; k < number_of_segments; k++)
		    				{
		    					byte[] current_segment = (byte [])segments.get(k); 
		    					out.writeInt(current_segment.length); 
		    					out.write(current_segment, 0, current_segment.length);
		    				}	
						 }
	            	 }  
		        }
		        
		        out.flush();
		        out.close();
		        
		        File file = new File("foo");
		        long file_length = file.length();
		        double compression_rate = file_length;
		        compression_rate /= xdim * ydim * 3;
		        System.out.println("The file compression rate is " + String.format("%.4f", file_compression_rate));
				System.out.println("Delta type is " + delta_type);
		        System.out.println("Delta bits compression rate is " + String.format("%.4f", compression_rate));
		        System.out.println();
		    }
			catch(Exception e)
			{
				System.out.println(e.toString());
			}
		}
	}
}