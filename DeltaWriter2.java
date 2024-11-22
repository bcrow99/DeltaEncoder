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

public class DeltaWriter2
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
	// This value is inversely related to the lengths of the segment.
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

	ArrayList delta_min_list, seed_length_list;
	
	boolean initialized = false;
	
	public static void main(String[] args)
	{
		if (args.length != 1)
		{
			System.out.println("Usage: java DeltaWriter2 <filename>");
			System.exit(0);
		}
		
		//String prefix       = new String("");
		String prefix       = new String("C:/Users/Brian Crowley/Desktop/");
		String filename     = new String(args[0]);
		
		DeltaWriter2 writer = new DeltaWriter2(prefix + filename);
	}

	public DeltaWriter2(String _filename)
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
			System.out.println();
			
			int pixel_length = xdim * ydim * 8;
		    
		    // Set in constructor and accessed 
			// by apply handler.
		    channel_list = new ArrayList();
		   
		    // Set by apply handler and accessed
		    // by save handler.
		    table_list  = new ArrayList();
		    string_list = new ArrayList();
		    map_list    = new ArrayList();
		    
		    // Dedicated lists for delta type 7.
		    delta_min_list = new ArrayList();
		    seed_length_list = new ArrayList();
		    segment_list = new ArrayList();
		    
		 
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
				
				
				JMenu delta_menu = new JMenu("Delta");
				
				JRadioButtonMenuItem [] delta_button = new JRadioButtonMenuItem[8];
				
				delta_button[0] = new JRadioButtonMenuItem("H");
				delta_button[1] = new JRadioButtonMenuItem("V");
				delta_button[2] = new JRadioButtonMenuItem("HV average");
				delta_button[3] = new JRadioButtonMenuItem("Paeth");
				delta_button[4] = new JRadioButtonMenuItem("Gradient");
				delta_button[5] = new JRadioButtonMenuItem("Scanline");
				delta_button[6] = new JRadioButtonMenuItem("Ideal (4)");
				delta_button[7] = new JRadioButtonMenuItem("Ideal (8)");
				
				delta_button[delta_type].setSelected(true);
				
				class ButtonHandler implements ActionListener
				{
					int index;
				    ButtonHandler(int index)	
				    {
				        this.index = index;	
				    }
				    
				    public void actionPerformed(ActionEvent e) 
		            {
				    	if(delta_type != index)
				    	{
				    	    delta_button[delta_type].setSelected(false);
				    	    delta_type = index;
				    	    delta_button[delta_type].setSelected(true);
				    	    apply_item.doClick();
				    	}
				    	else
					    	delta_button[delta_type].setSelected(true);
		            }  
				    
				}
				
				for(int i = 0; i < 8; i++)
				{
					delta_button[i].addActionListener(new ButtonHandler(i));
					delta_menu.add(delta_button[i]);
				}
			
				menu_bar.add(file_menu);
				menu_bar.add(delta_menu);
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
		    	if(delta_type != 7)
		    	    channel_init[i] = quantized_channel[0];
		    	else
		    	{
		    		int x = new_xdim / 2;
		    		int y = new_ydim / 2;
		    		int j = y * new_xdim + x;
		    		
		    		channel_init[i] = quantized_channel[j];
		    		
		    	}
		    	
		    	// Replace the original data with the modified data.
		    	quantized_channel_list.set(i, quantized_channel);
		    	
		    	// Get the ideal delta (4) sum.
		    	channel_sum[i] = DeltaMapper.getIdealSum(quantized_channel, new_xdim, new_ydim, 20);
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
			
			file_compression_rate  = file_length;
			file_compression_rate /= xdim * ydim * 3;
			
			System.out.println("A set of channels with the lowest delta sum is " + set_string[min_index]);
			System.out.println();
			
			int [] channel_id = DeltaMapper.getChannels(min_set_id);
			
			table_list.clear();
			string_list.clear();
			segment_list.clear();
			
			map_list.clear();
			delta_min_list.clear(); 
			seed_length_list.clear();
			
			for(int i = 0; i < 3; i++)
			{
				int j = channel_id[i];
				
				int [] quantized_channel = (int[])quantized_channel_list.get(j);
				
				ArrayList result = new ArrayList();
				
				ArrayList delta_list = DeltaMapper.getDeltaListFromValues2(quantized_channel, new_xdim, new_ydim);
				
				int ideal_sum = DeltaMapper.getIdealDeltaSum(delta_list);
		        int worst_sum = DeltaMapper.getWorstlDeltaSum(delta_list);
		        //System.out.println("Ideal delta sum is " + ideal_sum);
		        //System.out.println("Worst delta sum is " + worst_sum);
				if(delta_type == 0)
				{
					result = DeltaMapper.getHorizontalDeltasFromValues(quantized_channel, new_xdim, new_ydim);
					int sum = (int)result.get(0);
					//System.out.println("Sum of horizontal deltas is " + sum);
				}
				else if(delta_type == 1)
				{
					result = DeltaMapper.getVerticalDeltasFromValues(quantized_channel, new_xdim, new_ydim);
					int sum = (int)result.get(0);
					//System.out.println("Sum of vertical deltas is " + sum);
				}
				else if(delta_type == 2)
				{
					result = DeltaMapper.getAverageDeltasFromValues(quantized_channel, new_xdim, new_ydim);
					int sum = (int)result.get(0);
					//System.out.println("Sum of averaged horizontal and vertical deltas is " + sum);
				}
				else if(delta_type == 3)
				{
					result = DeltaMapper.getPaethDeltasFromValues(quantized_channel, new_xdim, new_ydim);
					int sum = (int)result.get(0);
					//System.out.println("Sum of paeth deltas is " + sum);
				}
				else if(delta_type == 4)
				{
					result = DeltaMapper.getGradientDeltasFromValues(quantized_channel, new_xdim, new_ydim);
					int sum = (int)result.get(0);
					//System.out.println("Sum of gradient deltas is " + sum);
				}
				else if(delta_type == 5)
				{
					result = DeltaMapper.getMixedDeltasFromValues(quantized_channel, new_xdim, new_ydim);
					int sum = (int)result.get(0);
					//System.out.println("Sum of mixed deltas is " + sum);
					
				    byte [] map = (byte [])result.get(2);
				    map_list.add(map);
				}
				else if(delta_type == 6)
				{
					result = DeltaMapper.getIdealDeltasFromValues(quantized_channel, new_xdim, new_ydim);
					int sum = (int)result.get(0);
					//System.out.println("Sum of ideal deltas (4) is " + sum);
				
				    byte [] map = (byte [])result.get(2);
				    map_list.add(map);
				}
				else if(delta_type == 7)
				{
			        result = DeltaMapper.getIdealDeltasFromValues3(quantized_channel, new_xdim, new_ydim, delta_list);
			        int sum = (int)result.get(0);
			        //System.out.println("Sum of ideal deltas (8) is " + sum);
			        
				    ArrayList seed_map     = (ArrayList)result.get(2);
				    ArrayList dilation_map = (ArrayList)result.get(4);
				    ArrayList list = new ArrayList();
				    list.add(seed_map);
				    list.add(dilation_map);
				    map_list.add(list);
				    
				    // Don't need this.
				    seed_length_list.add(seed_map.size());
				}
				System.out.println();
		
				if(delta_type != 7)
				{
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
	     	          
	     	            // Check this.
	     	            if(minimum_segment_length < 8)
	     	            	minimum_segment_length = 8;
     	                
     	                //System.out.println("Minimum segment length is " + minimum_segment_length);
	     	            
	     	             //System.out.println("Original number of segments is " + original_number_of_segments);	     	            
	     	            
	     	             ArrayList segment_data_list = SegmentMapper.getMergedSegmentedData(compression_string, minimum_segment_length);
	     	             
	     	             if(channel_iterations[i] == 0)
	     	             { 
	     	            	System.out.println("Merging uncompressed data.");
	     	            	ArrayList foo_list = SegmentMapper.getMergedData(compression_string);
	     	            	//byte [] merged_foo = SegmentMapper.getMergedString(compression_string);
	     	            	//byte [] unmerged_foo = SegmentMapper.getUnmergedString(merged_foo);
	     	            	
	     	            	//System.out.println("Packed strings had length " + compression_string.length);
	     	            	//System.out.println("Merged packed strings had length " + merged_foo.length);
	     	            	//System.out.println("Unmerged packed strings had length " + unmerged_foo.length);
	     	            	//double compression_rate = merged_foo.length;
	     	            	//compression_rate       /= xdim * ydim;
	     	            	//System.out.println("Compression rate for merged strings is " + String.format("%.4f", compression_rate));
	     	             }
	     	             else
	     	             {
	     	            	if(channel_string_type[i] == 0) 
	     	            	{
	     	            		System.out.println("Merging compressed zero strings.");
	     	            	    byte [] foo = StringMapper.decompressZeroStrings(compression_string);
	     	            	    //ArrayList foo_list = SegmentMapper.getMergedData(foo);
	     	            	    ArrayList merged_data = SegmentMapper.getMergedData(compression_string);
	     	            	    //byte [] unmerged_foo = SegmentMapper.getUnmergedString(merged_foo);
	     	            	   
	     	            	    System.out.println("Compressed packed strings had length " + compression_string.length);
		     	                //System.out.println("Merged compressed packed strings had length " + merged_foo.length);
		     	                //System.out.println("Unmerged packed strings had length " + unmerged_foo.length);
		     	                //double compression_rate = merged_foo.length;
		     	                //compression_rate       /= xdim * ydim;
		     	                //System.out.println("Compression rate for merged strings is " + String.format("%.4f", compression_rate));
	     	            	}
	     	            	else
	     	            	{
	     	            	    System.out.println("Merging compressed one strings.");
	     	            		byte [] foo = StringMapper.decompressOneStrings(compression_string);
	     	            	    //ArrayList foo_list = SegmentMapper.getMergedData(foo);
	     	            		//byte [] merged_foo = SegmentMapper.getMergedString(compression_string);
	     	            		//byte [] unmerged_foo = SegmentMapper.getUnmergedString(merged_foo);
	     	            		ArrayList merged_data = SegmentMapper.getMergedData(compression_string);
	     	            		//System.out.println("Compressed packed strings had length " + compression_string.length);
			     	            //System.out.println("Merged compressed packed strings had length " + merged_foo.length);
			     	            //System.out.println("Unmerged packed strings had length " + unmerged_foo.length);
			     	            //double compression_rate = merged_foo.length;
		     	                //compression_rate       /= xdim * ydim;
		     	                //System.out.println("Compression rate for merged strings is " + String.format("%.4f", compression_rate));
	     	            	}
	     	             }
	     	             
	     	             // A list of compressed strings.
	     	             ArrayList  segments  = (ArrayList)segment_data_list.get(0);
	     	             max_bytelength[i]    = (int)segment_data_list.get(1);
	     	             
	     	             //System.out.println("Number of segments from merge function is " + segments.size());
	     	             //System.out.println();
	     	            
	     	             // The single merged segment should be identical to the original string.
	     	             // For now, we'll add the original string.
	     	             if(segments.size()  == 1)
	     	             {
	     	            	 ArrayList segment = new ArrayList();
	     	            	 segment.add(compression_string);
	     	            	 segment_list.add(segment);
	     	             }
	     	             else
	     	                 segment_list.add(segments);
			        }
				}
				else if(delta_type == 7)
				{
					// The channel bit length, type, and iterations are already contained in the compression strings.
				    // For now we won't bother setting the channel data like we do the other delta types, 
					// which is redundant information useful for error checking and debugging but not really required.
					// The issue is that this delta type requires saving pairs of values, not single values.
					// We'll attach lists of strings and maps that we attach to the string list and map list,
					// and have created a dedicated delta min list for delta type 7.
					
					ArrayList channel_delta_min_list = new ArrayList();
					ArrayList channel_table_list     = new ArrayList();
					ArrayList channel_string_list    = new ArrayList();
					
					ArrayList seed_delta_list  = (ArrayList)result.get(1);
					int [] seed_delta = new int[seed_delta_list.size()];
					for(int k = 0; k < seed_delta.length; k++)
						seed_delta[k] = (int)seed_delta_list.get(k);
					
					ArrayList seed_delta_string_list = StringMapper.getStringList2(seed_delta);
					int seed_delta_min               = (int)seed_delta_string_list.get(0);
					int [] seed_string_table         = (int [])seed_delta_string_list.get(2);
					byte [] seed_compression_string  = (byte [])seed_delta_string_list.get(3);
					
					channel_delta_min_list.add(seed_delta_min);
					channel_table_list.add(seed_string_table);
					channel_string_list.add(seed_compression_string);
					
                    ArrayList dilated_delta_list = (ArrayList)result.get(3);
					
					int [] dilated_delta = new int[dilated_delta_list.size()];
					for(int k = 0; k < dilated_delta.length; k++)
						dilated_delta[k] = (int)dilated_delta_list.get(k);
					
					ArrayList dilated_delta_string_list = StringMapper.getStringList2(dilated_delta);
					int dilated_delta_min               = (int)dilated_delta_string_list.get(0);
					int [] dilated_string_table         = (int [])dilated_delta_string_list.get(2);
					byte [] dilated_compression_string  = (byte [])dilated_delta_string_list.get(3);
					
					channel_delta_min_list.add(dilated_delta_min);
					channel_table_list.add(dilated_string_table);
					channel_string_list.add(dilated_compression_string);
					
					delta_min_list.add(channel_delta_min_list);
					table_list.add(channel_table_list);
					string_list.add(channel_string_list);
				}
			}
			
			ArrayList resized_channel_list = new ArrayList();
			
			for(int i = 0; i < 3; i++)
		    {
		        int j  = channel_id[i];
				
		        if(delta_type != 7)
				{
		        	// Get original data to compare with result from decompression.
			        byte [] original_string = (byte [])string_list.get(i);
			        int original_type       = StringMapper.getType(original_string);
			        int original_iterations = StringMapper.getIterations(original_string);
			        int original_bitlength  = StringMapper.getBitlength(original_string);
			        
			        int  [] table  = (int [])table_list.get(i);
			        int  [] delta  = new int[new_xdim * new_ydim]; 
			       
			        ArrayList segments     = (ArrayList)segment_list.get(i);
			        int number_of_segments = segments.size();
			        //System.out.println("The number of segments is " + number_of_segments);
			        if(number_of_segments == 1)
			        {
			        	byte [] current_string = (byte [])segments.get(0);
			        	//System.out.println("Length of original string was " + original_string.length);
			            //System.out.println("Length of string from segment list is " + current_string.length);
			            
			            int current_type       = StringMapper.getType(current_string);
				        int current_iterations = StringMapper.getIterations(current_string);
				        int current_bitlength  = StringMapper.getBitlength(current_string);
			        	
				        //System.out.println("Original type is " + original_type + ", iterations is " + original_iterations + ", bitlength is " + original_bitlength);
				        //System.out.println("Current type is " + current_type + ", iterations is " + current_iterations + ", bitlength is " + current_bitlength);
				        //System.out.println();
			        	
				        if(current_iterations == 0 || current_iterations == 16)
				        {
				        	//System.out.println("Uncompressed.");
				        	int number_unpacked = StringMapper.unpackStrings2(original_string, table, delta);
				        	//int number_unpacked = StringMapper.unpackStrings2(current_string, table, delta);
						    if(number_unpacked != new_xdim * new_ydim)
						        System.out.println("Number of values unpacked does not agree with image dimensions.");	
				        }
				        else
				        {
				        	if(current_type == 0)
				        	{
				        		//System.out.println("Decompressing zero strings.");
				        	    byte [] decompressed_string = StringMapper.decompressZeroStrings(original_string);
				        	    //byte [] decompressed_string = StringMapper.decompressZeroStrings(current_string);
				        	    int number_unpacked = StringMapper.unpackStrings2(decompressed_string, table, delta);
							    if(number_unpacked != new_xdim * new_ydim)
							        System.out.println("Number of values unpacked does not agree with image dimensions.");
				        	}
				        	else
				        	{    
				        		//System.out.println("Decompressing one strings.");
				        		byte [] decompressed_string = StringMapper.decompressOneStrings(original_string);
				        		//byte [] decompressed_string = StringMapper.decompressOneStrings(current_string);
				        	    int number_unpacked = StringMapper.unpackStrings2(decompressed_string, table, delta);
							    if(number_unpacked != new_xdim * new_ydim)
							        System.out.println("Number of values unpacked does not agree with image dimensions.");
				        	}	
				        }
			        }
			        else
			        {
			            ArrayList decompressed_segments = new ArrayList();
			            for(int k = 0; k < number_of_segments; k++)
			            {
			            	byte [] current_string = (byte [])segments.get(k);
					        int current_iterations = StringMapper.getIterations(current_string);
					       
					        if(current_iterations == 0 || current_iterations == 16)
					        	decompressed_segments.add(current_string);
					        else
					        {
					        	if(current_iterations < 16)
					        	{
					        	    byte [] decompressed_string = StringMapper.decompressZeroStrings(current_string);
					        	    decompressed_segments.add(decompressed_string);
					        	}
					        	else
					        	{    
					        		byte [] decompressed_string = StringMapper.decompressOneStrings(current_string);
					        		decompressed_segments.add(decompressed_string);
					        	}		
					        }
			            }
			            
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
					    
					    
			            int reconstructed_type       = StringMapper.getType(reconstructed_string);
				        int reconstructed_iterations = StringMapper.getIterations(reconstructed_string);
				        int reconstructed_bitlength  = StringMapper.getBitlength(reconstructed_string);
			        	
				        /*
				        System.out.println("Original type is " + original_type + ", iterations is " + original_iterations + ", bitlength is " + original_bitlength);
				        System.out.println("Reconstructed type is " + reconstructed_type + ", iterations is " + reconstructed_iterations + ", bitlength is " + reconstructed_bitlength);
				     
				        
				        System.out.println("Length of original string is " + original_string.length);
				        System.out.println("Length of reconstructed string is " + reconstructed_string.length);
				        
				        
				        for(int k = 0; k < original_string.length; k++)
				        {
				        	if(original_string[k] != reconstructed_string[k])
				        	{
				        		System.out.println("Strings differ at index " + k);
				        		break;
				        	}
				        }
				        */
				        
				        if(reconstructed_iterations == 0 || reconstructed_iterations == 16)
				        {
				        	int number_unpacked = StringMapper.unpackStrings2(original_string, table, delta);
				        	//int number_unpacked = StringMapper.unpackStrings2(reconstructed_string, table, delta);
						    if(number_unpacked != new_xdim * new_ydim)
						        System.out.println("Number of values unpacked does not agree with image dimensions.");		
				        }
				        else
				        {
				        	if(reconstructed_type == 0)
				        	{
				        	    byte [] decompressed_string = StringMapper.decompressZeroStrings(original_string);
				        	    //byte [] decompressed_string = StringMapper.decompressZeroStrings(reconstructed_string);
				        	    int number_unpacked = StringMapper.unpackStrings2(decompressed_string, table, delta);
							    if(number_unpacked != new_xdim * new_ydim)
							        System.out.println("Number of values unpacked does not agree with image dimensions.");
				        	}
				        	else
				        	{  
				        		byte [] decompressed_string = StringMapper.decompressOneStrings(original_string);
				        		//byte [] decompressed_string = StringMapper.decompressOneStrings(reconstructed_string);
				        	    int number_unpacked = StringMapper.unpackStrings2(decompressed_string, table, delta);
							    if(number_unpacked != new_xdim * new_ydim)
							        System.out.println("Number of values unpacked does not agree with image dimensions.");
				        	}		
				        }
			        }
			        
	                System.out.println();
			        for(int k = 1; k < delta.length; k++)
			    	    delta[k] += channel_delta_min[j];
			    
			        int [] channel = new int[0];
			    
			        if(delta_type == 0)
			        {
			    	    channel = DeltaMapper.getValuesFromHorizontalDeltas(delta, new_xdim , new_ydim, channel_init[j]);
			        }
			        else if(delta_type == 1)
			        {
			            channel = DeltaMapper.getValuesFromVerticalDeltas(delta, new_xdim , new_ydim, channel_init[j]);   	
			        }
			        else if(delta_type == 2)
			        {
			            channel = DeltaMapper.getValuesFromAverageDeltas(delta, new_xdim , new_ydim, channel_init[j]);   	
			        }
			        else if(delta_type == 3)
			        {
			            channel = DeltaMapper.getValuesFromPaethDeltas(delta, new_xdim , new_ydim, channel_init[j]);   	
			        }
			        else if(delta_type == 4)
			        {
			    	    channel = DeltaMapper.getValuesFromGradientDeltas(delta, new_xdim , new_ydim, channel_init[j]);   	
			        }
			        else if(delta_type == 5)
			        {
			    	    byte [] map = (byte[])map_list.get(i);
			            channel = DeltaMapper.getValuesFromMixedDeltas(delta, new_xdim , new_ydim, channel_init[j], map);	   	
			        }
			        else if(delta_type == 6)
			        {
			    	    byte [] map = (byte[])map_list.get(i);
			            channel = DeltaMapper.getValuesFromIdealDeltas(delta, new_xdim , new_ydim, channel_init[j], map);	   	
			        }
			    
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
				else if(delta_type == 7)
				{
					ArrayList channel_string_list = (ArrayList)string_list.get(i);
			        ArrayList channel_table_list  = (ArrayList)table_list.get(i);	
			        ArrayList channel_map_list    = (ArrayList)map_list.get(i);
			        ArrayList channel_delta_min   = (ArrayList)delta_min_list.get(i);
			        
			        byte [] seed_string = (byte [])channel_string_list.get(0);
			        byte [] dilated_string = (byte [])channel_string_list.get(1);
			        
			        int [] seed_table = (int [])channel_table_list.get(0);
			        int [] dilated_table = (int [])channel_table_list.get(1);
			      
			        ArrayList seed_map = (ArrayList)channel_map_list.get(0);
			        ArrayList dilated_map = (ArrayList)channel_map_list.get(1);
			        
			        int seed_delta_min = (int)channel_delta_min.get(0);
			        int dilated_delta_min = (int)channel_delta_min.get(1);
			        
			        
			        int  seed_length = (int)seed_length_list.get(i);
			        int  dilated_length = new_xdim * new_ydim - seed_length - 1;
			        int [] seed_delta = new int[seed_length];
			        int [] dilated_delta = new int[dilated_length];
			        
			        int iter  = StringMapper.getIterations(seed_string);
			        if(iter == 0 || iter == 16)
			        {
			        	int number_unpacked = StringMapper.unpackStrings2(seed_string, seed_table, seed_delta);
					    if(number_unpacked != seed_length)
					        System.out.println("Number of values unpacked does not agree with seed length.");	
			        }
			        else
			        {
			        	int  string_type = StringMapper.getType(seed_string);

			        	byte [] decompressed_string = new byte[1];
				        if(string_type == 0)
				        	decompressed_string = StringMapper.decompressZeroStrings(seed_string);	
				        else
				        	decompressed_string = StringMapper.decompressOneStrings(seed_string);
			        	int number_unpacked = StringMapper.unpackStrings2(decompressed_string, seed_table, seed_delta);
					    if(number_unpacked != seed_length)
					        System.out.println("Number of values unpacked does not agree with seed length.");
			        }
			        

			        ArrayList seed_delta_list = new ArrayList();
			        for(int k = 0; k < seed_length; k++)
			        	seed_delta_list.add(seed_delta[k] + seed_delta_min);
			        
			        
			        iter = StringMapper.getIterations(dilated_string);
			        if(iter == 0 || iter == 16)
			        {
			        	//System.out.println("String did not compress.");
			        	int number_unpacked = StringMapper.unpackStrings2(dilated_string, dilated_table, dilated_delta);
					    if(number_unpacked != dilated_length)
					        System.out.println("Number of values unpacked does not agree with dilated length.");	
			        }
			        else
			        {
			        	int  string_type = StringMapper.getType(dilated_string);

			        	byte [] decompressed_string = new byte[1];
				        if(string_type == 0)
				        	decompressed_string = StringMapper.decompressZeroStrings(dilated_string);	
				        else
				        	decompressed_string = StringMapper.decompressOneStrings(dilated_string);
			        	int number_unpacked = StringMapper.unpackStrings2(decompressed_string, dilated_table, dilated_delta);
			        	if(number_unpacked != dilated_length)
					        System.out.println("Number of values unpacked does not agree with dilated length.");  
			        }
			        
			        ArrayList dilated_delta_list = new ArrayList();
			        for(int k = 0; k < dilated_length; k++)
			        	dilated_delta_list.add(dilated_delta[k] + dilated_delta_min);
			        
			        ArrayList list = new ArrayList();
			        list.add(0);
			        list.add(seed_delta_list);
			        list.add(seed_map);
			        list.add(dilated_delta_list);
			        list.add(dilated_map);
			        
			        int [] channel = DeltaMapper.getValuesFromIdealDeltas2(list, new_xdim , new_ydim, channel_init[j]);
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
			        System.out.println();
				}
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
	
		    for(int i = 0; i < ydim; i++)
			{
				for(int j = 0; j < xdim; j++)
				{
					int k = i * xdim + j;
					
					int pixel = 0;
					
					pixel |= blue[k] << 16;
				
				    pixel |= green[k] << 8;
					
					pixel |= red[k];
					
				    image.setRGB(j, i, pixel);
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
		        
		        // Dimensions of full sized frame
		        out.writeShort(xdim);
		        out.writeShort(ydim);
		        
		        // Compression parameters
		        out.writeByte(pixel_shift);
		        out.writeByte(pixel_quant);
		        out.writeByte(min_set_id);
		        out.writeByte(delta_type);
		     
		        for(int i = 0; i < 3; i++)
		        {
		        	int j = channel_id[i];
		        	
		        	// Only use this for difference channels.
		        	out.writeInt(channel_min[j]);
		        	
		        	out.writeInt(channel_init[j]);
		        	
		        	if(delta_type != 7)
		        	{
		        	    out.writeInt(channel_delta_min[j]);
		        
		        	    out.writeInt(channel_length[j]);
		        	
		        	    // If the string didn't compress, 
		        	    // this is the same as the uncompressed length.
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
		        	}
		        	else if(delta_type == 7)
		        	{
		        	    ArrayList channel_delta_min_list = (ArrayList)delta_min_list.get(i);
		        	    int seed_delta_min = (int)channel_delta_min_list.get(0);
		        	    int dilated_delta_min = (int)channel_delta_min_list.get(1);
		        	    
		        	    out.writeInt(seed_delta_min);
		        	    out.writeInt(dilated_delta_min);
		        	   
		        	    ArrayList channel_table_list = (ArrayList)table_list.get(i);
		        	    int [] seed_table = (int [])channel_table_list.get(0);
		        	    int [] dilated_table = (int [])channel_table_list.get(1);
		        	    
		        	    int max_byte_value  = Byte.MAX_VALUE * 2 + 1;
		        	    
		        	    out.writeShort(seed_table.length);   
		                if(seed_table.length <= max_byte_value)
                        {
		                    for(int k = 0; k < seed_table.length; k++)
		                        out.writeByte(seed_table[k]);
                         }
		                else 
		                {
		                    for(int k = 0; k < seed_table.length; k++)
		                        out.writeShort(seed_table[k]);
                        }
		                
		                out.writeShort(dilated_table.length);    
		                if(dilated_table.length <= max_byte_value)
                        {
		                    for(int k = 0; k < dilated_table.length; k++)
		                        out.writeByte(dilated_table[k]);
                        }
		                else 
		                {
		                    for(int k = 0; k < dilated_table.length; k++)
		                        out.writeShort(dilated_table[k]);
                        }
		        	}
		            
		            if(delta_type == 5  || delta_type == 6)
		            {
		            	byte [] map = (byte [])map_list.get(i);
		            	
		            	ArrayList map_string_list = StringMapper.getStringList2(map);
						int map_min               = (int)map_string_list.get(0);
						int map_bit_length        = (int)map_string_list.get(1);
						int [] string_table       = (int [])map_string_list.get(2);
						byte [] compressed_map    = (byte [])map_string_list.get(3);
		            	
						out.writeShort(string_table.length);
						for(int k = 0; k < string_table.length; k++)
			                out.writeShort(string_table[k]);
		            	
						out.writeInt(compressed_map.length);
			            out.write(compressed_map, 0, compressed_map.length);
		            	
			            out.writeByte(map_min);
			            out.writeInt(map.length);
		            	
		            	
		            }
		            else if(delta_type == 7)
		            {
		                ArrayList channel_map_list = (ArrayList)map_list.get(i);	
		                
		                ArrayList seed_map_list = (ArrayList)channel_map_list.get(0);
		                ArrayList dilated_map_list = (ArrayList)channel_map_list.get(1);
		                
		                byte [] seed_map = new byte[seed_map_list.size()];
		                for(int k = 0; k < seed_map.length; k++)
		                	seed_map[k] = (byte)seed_map_list.get(k);
		            
		                ArrayList map_string_list = StringMapper.getStringList2(seed_map);
						int map_min               = (int)map_string_list.get(0);
						int map_bit_length        = (int)map_string_list.get(1);
						int [] string_table       = (int [])map_string_list.get(2);
						byte [] compressed_map    = (byte [])map_string_list.get(3);
		                
						out.writeShort(string_table.length);
						for(int k = 0; k < string_table.length; k++)
			                out.writeShort(string_table[k]);
		            	
						out.writeInt(compressed_map.length);
			            out.write(compressed_map, 0, compressed_map.length);
		            	
			            out.writeByte(map_min);
			            out.writeInt(seed_map.length);
			            
			            Deflater deflater = new Deflater();
	                    deflater.setInput(compressed_map);
	                    byte [] zipped_string = new byte[2 * compressed_map.length];
	                    deflater.finish();
	                    int zipped_length = deflater.deflate(zipped_string);
	                    deflater.end(); 
			            
			            System.out.println("Compressed seed map length was " +  compressed_map.length);
			            System.out.println("Zipped compressed seed map length was " +  zipped_length);
			            
		                byte [] dilated_map = new byte[dilated_map_list.size()];
		                for(int k = 0; k < dilated_map.length; k++)
		                	dilated_map[k] = (byte)dilated_map_list.get(k);
		              
		                
		                map_string_list = StringMapper.getStringList2(dilated_map);
						map_min         = (int)map_string_list.get(0);
						map_bit_length  = (int)map_string_list.get(1);
						string_table    = (int [])map_string_list.get(2);
						compressed_map  = (byte [])map_string_list.get(3);
		                
		                out.writeShort(string_table.length);
						for(int k = 0; k < string_table.length; k++)
			                out.writeShort(string_table[k]);
		            	
						out.writeInt(compressed_map.length);
			            out.write(compressed_map, 0, compressed_map.length);
		            	
			            out.writeByte(map_min);
			            out.writeInt(dilated_map.length);
		                
		                
			            deflater = new Deflater();
	                    deflater.setInput(compressed_map);
	                    zipped_string = new byte[2 * compressed_map.length];
	                    deflater.finish();
	                    zipped_length = deflater.deflate(zipped_string);
	                    deflater.end(); 
			            
			            System.out.println("Compressed dilated map length was " +  compressed_map.length);
			            System.out.println("Zipped compressed dilated map length was " +  zipped_length);
			            System.out.println();
		            }
		           
		            // There is only one segment, which itself might or might not be segmented.
		            if(delta_type != 7)
		            {
		            	 ArrayList segments = (ArrayList)segment_list.get(i);
		            	 int number_of_segments = segments.size();
		            	 out.writeInt(number_of_segments);
		            	 
		            	 if(number_of_segments == 1)
		            	 {
		            		 byte [] string = (byte [])string_list.get(i);
		            		 out.writeInt(string.length);
		            		 
		            		 
		            		 Deflater deflater = new Deflater();
			                 deflater.setInput(string);
			                 byte [] zipped_string = new byte[2 * string.length];
			                 deflater.finish();
			                 int zipped_length = deflater.deflate(zipped_string);
			                 deflater.end(); 
			                 
			                 
			                 if(zipped_length < string.length)
			                 {
			                     out.writeInt(zipped_length);
			            	     out.write(zipped_string, 0, zipped_length); 
			                 }
			                 else
			                 {
			                	 out.writeInt(0);
			                	 out.write(string, 0, string.length);
			                 }
		            	 }
		            	 else
		            	 {
		            		 out.writeInt(max_bytelength[i]);
		            		 // Zipping the segments lengths works better than compressing them.
		            		 // The reason why the bitwise compression works better than zipping
		            		 // on small numbers of elements probably relates to the difference in
		            		 // the overhead.  That means that the main advantages over huffman
		            		 // coding are when the data allows for recursion or with small numbers
		            		 // of elements, when the overhead comes into play. Probably why compressing the
		            		 // maps works better than zipping them is the values are only 0 thru 3 or 0 thru 7.  
		            		 // Need to reexplore if the zipping overhead can be reduced.
		            		 
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
								    System.out.println("Maximum segment length requires an unsigned byte.");
								    
								    // Collect the segment lengths.
								    byte [] segment_length = new byte[number_of_segments + 1];
								    
								    for(int k = 0; k < number_of_segments; k++)
								    {
								    	byte[] current_segment = (byte [])segments.get(k);
								    	segment_length[k]      = (byte)current_segment.length;
								    }
								    
								    // Add trailing byte.
								    int bitlength = number_of_segments * 8;
								    double zero_ratio = StringMapper.getZeroRatio(segment_length, bitlength);
								    if(zero_ratio < .5)
								    	segment_length[segment_length.length - 1] = 16;
								    
								    // Compress the segment lengths.
								    byte [] compressed_segments = new byte[1];
								    if(zero_ratio < .5)
								         compressed_segments = StringMapper.compressOneStrings(segment_length);
								    else
								    	compressed_segments = StringMapper.compressZeroStrings(segment_length);
								    
								    Deflater deflater = new Deflater();
					                deflater.setInput(compressed_segments);
					                byte [] zipped_string = new byte[2 * compressed_segments.length];
					                deflater.finish();
					                int zipped_compressed_length = deflater.deflate(zipped_string);
					                deflater.end(); 
								    
								    deflater = new Deflater();
					                deflater.setInput(segment_length);
					                deflater.finish();
					                int zipped_length = deflater.deflate(zipped_string);
					                deflater.end(); 
								    
								    System.out.println("The length of the segment lengths in unsigned bytes is " + number_of_segments);
								    System.out.println("The length of the compressed segment lengths is " + compressed_segments.length);
								    System.out.println("The length of the zipped segment lengths is " + zipped_length);
								    System.out.println("The length of the zipped compressed segment lengths is " + zipped_compressed_length);
		            		 }
							 else if(max_bytelength[i] <= Short.MAX_VALUE * 2 + 1)
							 {
								    System.out.println("Maximum segment length requires an unsigned short.");
							 }
							 else
							 {
									System.out.println("Maximum segment length requires an int.");
							 }
		            		 System.out.println();
		            		 
		            		 int max_length = 0;
		            		 int min_length = Integer.MAX_VALUE;
		            		 for(int k = 0; k < number_of_segments; k++)
		    				 {
		    					 byte[] current_string = (byte [])segments.get(k); 
		    					 if(current_string.length < min_length)
		    						 min_length = current_string.length;
		    					 if(current_string.length > max_length)
			    						 max_length = current_string.length;
		    				 }
		            		 
		            		 int max_delta = max_length - min_length;
		            		 if(max_delta <= Byte.MAX_VALUE * 2 + 1)
								    System.out.println("Maximum segment length delta requires an unsigned byte.");
								else if(max_delta <= Short.MAX_VALUE * 2 + 1)
								    System.out.println("Maximum segment length delta requires an unsigned short.");
								else
									System.out.println("Maximum segment length delta requires an int.");
		            		 System.out.println();
		            		
		            		 
		    				 for(int k = 0; k < number_of_segments; k++)
		    				 {
		    					 byte[] current_string = (byte [])segments.get(k); 
		    					 if(max_bytelength[i] <= Byte.MAX_VALUE * 2 + 1)
		    						 out.writeByte(current_string.length);
		    					 else if(max_bytelength[i] <= Short.MAX_VALUE * 2 + 1)
		    						 out.writeShort(current_string.length); 
		    					 else
		 	                    	out.writeInt(current_string.length);
		    					 out.write(current_string, 0, current_string.length);
		    				 }
		            	 }
		            }
		            else if(delta_type == 7) // There are two segments, which themselves might or might not be segmented.
		            {
		            	// We're segmenting delta type 7 in the save handler, when the other
		            	// delta types are segmented in the apply handler.  This also
		            	// means we are not doing a complete regression test in the main
		            	// program by decompressing the data and redisplaying the image.
		            	// Should change this--we have to run the reader program to see
		            	// if everything is working.
		            	
		                ArrayList channel_string_list = (ArrayList)	string_list.get(i);
		                
		                byte [] seed_string = (byte [])channel_string_list.get(0);
		                byte [] dilated_string = (byte [])channel_string_list.get(1);
		                
		            	
		            	 int number_of_segments = 1;
		            	 if(segment_length == 0)
		            	 {
		            		 System.out.println("Strings will be unsegmented.");
		            		 out.writeInt(number_of_segments);
		            		 
		            		 byte[] current_string = seed_string;
	    					 Deflater deflater = new Deflater();
	 	                     deflater.setInput(current_string);
	 	                     byte [] zipped_string = new byte[2 * current_string.length];
	 	                     deflater.finish();
		                     int zipped_length = deflater.deflate(zipped_string);
		                     deflater.end(); 
		                     
		                     if(zipped_length < current_string.length)
		                     {
		                         out.writeInt(current_string.length);
		                         out.writeInt(zipped_length);
			            	     out.write(zipped_string, 0, zipped_length);
		                     }
		                     else
		                     {
		                    	 out.writeInt(current_string.length);
		                         out.writeInt(0);
			            	     out.write(current_string, 0, current_string.length);	 
		                     }
		                    
		                     
		                     out.writeInt(number_of_segments);
			            	 current_string    = dilated_string;
	    					 deflater = new Deflater();
	 	                     deflater.setInput(current_string);
	 	                     zipped_string = new byte[2 * current_string.length];
	 	                     deflater.finish();
		                     zipped_length = deflater.deflate(zipped_string);
		                     deflater.end(); 
		                     
	 		            	 if(zipped_length < current_string.length)
		                     {
		                         out.writeInt(current_string.length);
		                         out.writeInt(zipped_length);
			            	     out.write(zipped_string, 0, zipped_length);
		                     }
		                     else
		                     {
		                    	 out.writeInt(current_string.length);
		                         out.writeInt(0);
			            	     out.write(current_string, 0, current_string.length);	 
		                     }
		                    	
		            	 }
		            	 else
		            	 {
		            	     // Strings might or might not be segmented, depending on whether
		            		 // the merge function returns a single segment.
		            		 int bitlength  = StringMapper.getBitlength(seed_string);
		            	
		     	             int original_number_of_segments = (int)Math.pow(2,  segment_length);
				        	
		            		 int minimum_segment_length = bitlength / original_number_of_segments;
		            		 int remainder = minimum_segment_length % 8;
		            		 minimum_segment_length -= remainder;
		     	          
		     	             if(minimum_segment_length < 16)
		     	            	 minimum_segment_length = 16;
		     	             ArrayList segment_data_list = SegmentMapper.getMergedSegmentedData(seed_string, minimum_segment_length);
		     	             ArrayList segments    = (ArrayList)segment_data_list.get(0);
		     	             number_of_segments          = segments.size();
		    				 int max_segment_byte_length = (int)segment_data_list.get(1);
		    				 out.writeInt(number_of_segments);
		    				 
		    				 if(number_of_segments == 1)
		    				 {
		    					 Deflater deflater = new Deflater();
		 	                     deflater.setInput(seed_string);
		 	                     byte [] zipped_string = new byte[2 * seed_string.length];
		 	                     deflater.finish();
			                     int zipped_length = deflater.deflate(zipped_string);
			                     deflater.end(); 
			                    
			                     out.writeInt(seed_string.length);
		 		            	 if(zipped_length < seed_string.length)
			                     {
			                         out.writeInt(zipped_length);
				            	     out.write(zipped_string, 0, zipped_length);
			                     }
			                     else
			                     {
			                         out.writeInt(0);
				            	     out.write(seed_string, 0, seed_string.length);	 
			                     }	 
		    				 }
		    				 else
		    				 {
		    				     out.writeInt(max_segment_byte_length);
		    				     for(int k = 0; k < number_of_segments; k++)
		    				     {
		    				         byte[] current_segment = (byte [])segments.get(k); 
		    				   
			                         if(max_segment_byte_length <= Byte.MAX_VALUE * 2 + 1)
			                    	     out.writeByte(current_segment.length);
		 	                         else if(max_segment_byte_length <= Short.MAX_VALUE * 2 + 1)
		 	                    	     out.writeShort(current_segment.length);
		 	                         else
		 	                    	    out.writeInt(current_segment.length); 
		 	                    	 out.write(current_segment, 0, current_segment.length);
		    				     }
		    				 }
		    				 
		    				 bitlength = StringMapper.getBitlength(dilated_string);
			            	 minimum_segment_length = bitlength / original_number_of_segments;
			            	 remainder = minimum_segment_length % 8;
			            	 minimum_segment_length -= remainder;  
			     	         if(minimum_segment_length < 16)
			     	             minimum_segment_length = 16;
		     	             
		     	             segment_data_list       = SegmentMapper.getMergedSegmentedData(dilated_string, minimum_segment_length);
		     	             segments                = (ArrayList)segment_data_list.get(0);
		     	             number_of_segments      = segments.size();
		    				 max_segment_byte_length = (int)segment_data_list.get(1);
		    				 out.writeInt(number_of_segments);
		    				 
		    				 if(number_of_segments == 1)
		    				 {
		    					 Deflater deflater = new Deflater();
		 	                     deflater.setInput(seed_string);
		 	                     byte [] zipped_string = new byte[2 * dilated_string.length];
		 	                     deflater.finish();
			                     int zipped_length = deflater.deflate(zipped_string);
			                     deflater.end(); 
			                    
			                     out.writeInt(dilated_string.length);
		 		            	 if(zipped_length < dilated_string.length)
			                     {
			                         out.writeInt(zipped_length);
				            	     out.write(zipped_string, 0, zipped_length);
			                     }
			                     else
			                     {
			                         out.writeInt(0);
				            	     out.write(dilated_string, 0, dilated_string.length);	 
			                     }	 	 
		    				 }
		    				 else
		    				 {
		    				     out.writeInt(max_segment_byte_length);
		    				     for(int k = 0; k < number_of_segments; k++)
		    				     {
		    				         byte[] current_segment = (byte [])segments.get(k); 
		    				   
			                         if(max_segment_byte_length <= Byte.MAX_VALUE * 2 + 1)
			                    	     out.writeByte(current_segment.length);
		 	                         else if(max_segment_byte_length <= Short.MAX_VALUE * 2 + 1)
		 	                    	     out.writeShort(current_segment.length);
		 	                         else
		 	                    	     out.writeInt(current_segment.length); 
		 	                    	 out.write(current_segment, 0, current_segment.length);
		    				     }
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