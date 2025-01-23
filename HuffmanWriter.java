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

public class HuffmanWriter
{
	BufferedImage image;
	ImageCanvas   image_canvas;
	int           xdim, ydim;
	JMenuItem     apply_item;
	String        filename;
	int []        pixel;
	
	int pixel_quant    = 4;
	int pixel_shift    = 3;
	int segment_length = 0;
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
	
	long   file_length;
	double file_compression_rate;

	ArrayList channel_list, table_list, string_list, map_list, segment_list;
	ArrayList code_list, code_length_list;
	
	boolean initialized = false;
	
	public static void main(String[] args)
	{
		if (args.length != 1)
		{
			System.out.println("Usage: java HuffmanWriter <filename>");
			System.exit(0);
		}
		
		//String prefix       = new String("");
		String prefix       = new String("C:/Users/Brian Crowley/Desktop/");
		String filename     = new String(args[0]);
		
		HuffmanWriter writer = new HuffmanWriter(prefix + filename);
	}

	public HuffmanWriter(String _filename)
	{
		filename = _filename;
		try
		{
			File file = new File(filename);
			file_length            = file.length();
			BufferedImage original_image = ImageIO.read(file);
			int raster_type = original_image.getType();
			xdim = original_image.getWidth();
			ydim = original_image.getHeight();
		
			System.out.println("Xdim = " + xdim + ", ydim = " + ydim);
			System.out.println();
		    
		    channel_list = new ArrayList();
		    table_list   = new ArrayList();
		    string_list  = new ArrayList();
		    map_list     = new ArrayList();
		    segment_list = new ArrayList();
		    code_list    = new ArrayList();
		    code_length_list = new ArrayList();
		    
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
				delta_button[2] = new JRadioButtonMenuItem("Average");
				delta_button[3] = new JRadioButtonMenuItem("Paeth");
				delta_button[4] = new JRadioButtonMenuItem("Gradient");
				delta_button[5] = new JRadioButtonMenuItem("Scanline 1");
				delta_button[6] = new JRadioButtonMenuItem("Scanline 2");
				delta_button[7] = new JRadioButtonMenuItem("Map");
				
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
			
			file_compression_rate  = file_length;
			file_compression_rate /= xdim * ydim * 3;
			
			System.out.println("A set of channels with the lowest delta sum is " + set_string[min_index]);
			System.out.println();
			
			int [] channel_id = DeltaMapper.getChannels(min_set_id);
			
			table_list.clear();
			string_list.clear();
			segment_list.clear();
			map_list.clear();
			code_list.clear();
			code_length_list.clear();
			
			for(int i = 0; i < 3; i++)
			{
				int j = channel_id[i];
				
				int [] quantized_channel = (int[])quantized_channel_list.get(j);
				
				ArrayList result = new ArrayList();
				
				if(delta_type == 0)
				{
					result = DeltaMapper.getHorizontalDeltasFromValues(quantized_channel, new_xdim, new_ydim);	
				}
				else if(delta_type == 1)
				{
					result = DeltaMapper.getVerticalDeltasFromValues(quantized_channel, new_xdim, new_ydim);	
				}
				else if(delta_type == 2)
				{
					result = DeltaMapper.getAverageDeltasFromValues(quantized_channel, new_xdim, new_ydim);
				}
				else if(delta_type == 3)
				{
					result = DeltaMapper.getPaethDeltasFromValues(quantized_channel, new_xdim, new_ydim);
				}
				else if(delta_type == 4)
				{
					result = DeltaMapper.getGradientDeltasFromValues(quantized_channel, new_xdim, new_ydim);
				}
				else if(delta_type == 5)
				{
					result = DeltaMapper.getMixedDeltasFromValues(quantized_channel, new_xdim, new_ydim);
				    byte [] map = (byte [])result.get(2);
				    map_list.add(map);
				}
				else if(delta_type == 6)
				{
					result = DeltaMapper.getMixedDeltasFromValues2(quantized_channel, new_xdim, new_ydim);
				    byte [] map = (byte [])result.get(2);
				    map_list.add(map);
				}
				else if(delta_type == 7)
				{
					result = DeltaMapper.getIdealDeltasFromValues4(quantized_channel, new_xdim, new_ydim);
				    byte [] map = (byte [])result.get(2);
				    map_list.add(map);
				}
				
				
				
				int [] delta             = (int [])result.get(1);
				ArrayList histogram_list = StringMapper.getHistogram(delta);
				int delta_min            = (int)histogram_list.get(0);
                int [] histogram         = (int[])histogram_list.get(1);
			    int value_range          = (int)histogram_list.get(2);
			    channel_delta_min[j]     = delta_min;
			
			    int n = histogram.length;
				ArrayList frequency_list = new ArrayList();
			    for(int k = 0; k < n; k++)
			        frequency_list.add(histogram[k]);
			    Collections.sort(frequency_list, Comparator.reverseOrder());
			    int [] frequency = new int[n];
			    for(int k = 0; k < n; k++)
			    	frequency[k] = (int)frequency_list.get(k);
			    byte [] huffman_length   = CodeMapper.getHuffmanLength2(frequency);
			    int [] huffman_code     = CodeMapper.getCanonicalCode(huffman_length);
			    int [] rank_table       = StringMapper.getRankTable(histogram);
			    
			    code_list.add(huffman_code);
			    code_length_list.add(huffman_length);
			    table_list.add(rank_table);
			    
			    int  estimated_bit_length = CodeMapper.getCost(huffman_length, frequency);
			    int byte_length = estimated_bit_length / 8;
			    if(estimated_bit_length % 8 != 0)
			    	byte_length++;
			    byte_length += 2;
			    byte [] packed_delta = new byte[byte_length];
			    
			    
			    //delta[0] = value_range / 2;
			    delta[0] = 0;
			    
			    // Get rid of the negative numbers.
			    for(int k = 1; k < delta.length; k++)
			    	delta[k] -= delta_min;
			    int bit_length               =  CodeMapper.packCode(delta, rank_table, huffman_code, huffman_length, packed_delta);
			    channel_length[j]            = bit_length;
			   
			    System.out.println("Byte length of packed deltas is " + byte_length);
			    
			    /*
			    double zero_ratio = StringMapper.getZeroRatio(packed_delta, bit_length);
			    if(zero_ratio <= .5)
			    {
			    	packed_delta[byte_length - 1] = 16;
			    	byte [] compressed_packed_delta = StringMapper.compressOneStrings(packed_delta, bit_length);
			    	System.out.println("Byte length of packed deltas after compressing one strings is " + compressed_packed_delta.length);
			    }
			    else
			    {
			    	packed_delta[byte_length - 1] = 16;
			    	byte [] compressed_packed_delta = StringMapper.compressOneStrings(packed_delta, bit_length);
			    	System.out.println("Byte length of packed deltas after compressing zero strings is " + compressed_packed_delta.length);
			    }
			    */
			   
			    channel_string_type[i] = 0;
		        channel_iterations[i]  = 0;
			   
				int [] unpacked_delta = new int[delta.length];
				int number_unpacked  =  CodeMapper.unpackCode(packed_delta, rank_table, huffman_code, huffman_length, channel_length[j], unpacked_delta);
				
				
				
				/*
				boolean different = false;
				int     index     = 0;
				
				for(int k = 0; k < delta.length; k++)
				{
					if(delta[k] != unpacked_delta[k])
					{
						different = true;
						index     = k;
						break;
					}
				}
				
				
				System.out.println("Estimated bit length of huffman encoded deltas is " + estimated_bit_length);
				System.out.println("Actual bit length of huffman encoded deltas is " + bit_length);
				System.out.println("The number of deltas is " + delta.length);
				System.out.println("The number of deltas unpacked is " + number_unpacked);
				if(different)
					System.out.println("Original values are different from decoded values at index " + index);
				else
					System.out.println("Original values are the same as decoded values.");
				*/
				
				
				// Restore the negative numbers and reset the initial value.
				delta[0] = 0;
				for(int k = 1; k < delta.length; k++)
			    	delta[k] += delta_min;
				string_list.add(packed_delta);
				segment_list.add(packed_delta);
			}
			
			ArrayList resized_channel_list = new ArrayList();
			
			for(int i = 0; i < 3; i++)
		    {
		        int j  = channel_id[i];
		       
		        int  [] table        = (int [])table_list.get(i);
		        int [] code          = (int [])code_list.get(i);
		        byte [] code_length  = (byte [])code_length_list.get(i);
		        byte [] packed_delta = (byte [])string_list.get(i);
		     
		        int  [] delta        = new int[new_xdim * new_ydim]; 
		        int number_unpacked  =  CodeMapper.unpackCode(packed_delta, table, code, code_length, channel_length[j], delta);
		        for(int k = 1; k < delta.length; k++)
		        {
		        	delta[k] += channel_delta_min[j];
		        }
		        
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
		    	    
		    	
		    	    ArrayList map_histogram_list = StringMapper.getHistogram(map);
		    	    int map_min                  = (int)map_histogram_list.get(0);
			        int [] map_histogram         = (int[])map_histogram_list.get(1);
			        int value_range              = (int)map_histogram_list.get(2);
				    int [] map_table             = StringMapper.getRankTable(map_histogram);
			        for(int k = 0; k < map.length; k++)
				    	map[k] -= map_min;
				    byte [] map_string = new byte[map.length * 4];
				    int map_bit_length = StringMapper.packStrings2(map, map_table, map_string);
				    
				    int map_byte_length = map_bit_length / 8;
				    if(map_bit_length % 8 != 0)
				    	map_byte_length++;
				    map_byte_length++;
				    
				    byte [] clipped_map_string = new byte[map_byte_length];
				    
				    for(int k = 0; k < map_byte_length - 1; k++)
				    {
				    	clipped_map_string[k] = map_string[k];
				    }
				    
				    if(map_bit_length % 8 != 0)
				    {
				        int extra_bits = 8 - (map_bit_length % 8);
				        extra_bits <<= 5;
				        clipped_map_string[map_byte_length - 1] = (byte)extra_bits;
				    }
				  
				    double zero_percentage   = map.length;
			        if(map_histogram.length > 1)
			        {
				        int min_histogram_value = Integer.MAX_VALUE;
				        for(int k = 0; k < map_histogram.length; k++)
					        if(map_histogram[k] < min_histogram_value)
						        min_histogram_value = map_histogram[k];
				        zero_percentage -= min_histogram_value;
			        }	
			        zero_percentage  /= map_bit_length;
			        if(zero_percentage < .5)
			        	clipped_map_string[map_byte_length - 1] |= 16;
			        
			        byte [] compressed_string = new byte[0];
			        
			        if(zero_percentage > .5)
				        compressed_string   = StringMapper.compressZeroStrings(clipped_map_string);
			        else
			        	compressed_string   = StringMapper.compressOneStrings(clipped_map_string);
			        
			        int number_of_iterations = StringMapper.getIterations(compressed_string);
			        
			        int combined_unary_bytelength = compressed_string.length + map_table.length;
			        System.out.println("Combined byte length of packed strings and table is " + combined_unary_bytelength);
			        System.out.println("Number of iterations of strings was " + number_of_iterations);
			    
		    	    
		    	    channel = DeltaMapper.getValuesFromMixedDeltas(delta, new_xdim , new_ydim, channel_init[j], map);
		        }
		        else if(delta_type == 6)
		        {
		        	
		    	    byte [] map = (byte[])map_list.get(i);
		    	    
		    	    ArrayList map_histogram_list = StringMapper.getHistogram(map);
		    	    int map_min                  = (int)map_histogram_list.get(0);
			        int [] map_histogram         = (int[])map_histogram_list.get(1);
			        int value_range              = (int)map_histogram_list.get(2);
				    int [] map_table             = StringMapper.getRankTable(map_histogram);
				    
				
			        for(int k = 0; k < map.length; k++)
				    	map[k] -= map_min;
				    byte [] map_string = new byte[map.length * 4];
				    int map_bit_length = StringMapper.packStrings2(map, map_table, map_string);
				    
				    int map_byte_length = map_bit_length / 8;
				    if(map_bit_length % 8 != 0)
				    	map_byte_length++;
				    map_byte_length++;
				    
				    byte [] clipped_map_string = new byte[map_byte_length];
				    
				    for(int k = 0; k < map_byte_length - 1; k++)
				    {
				    	clipped_map_string[k] = map_string[k];
				    }
				    
				    if(map_bit_length % 8 != 0)
				    {
				        int extra_bits = 8 - (map_bit_length % 8);
				        extra_bits <<= 5;
				        clipped_map_string[map_byte_length - 1] = (byte)extra_bits;
				    }
				  
				    double zero_percentage   = map.length;
			        if(map_histogram.length > 1)
			        {
				        int min_histogram_value = Integer.MAX_VALUE;
				        for(int k = 0; k < map_histogram.length; k++)
					        if(map_histogram[k] < min_histogram_value)
						        min_histogram_value = map_histogram[k];
				        zero_percentage -= min_histogram_value;
			        }	
			        zero_percentage  /= map_bit_length;
			        if(zero_percentage < .5)
			        	clipped_map_string[map_byte_length - 1] |= 16;
			        
			        byte [] compressed_string = new byte[0];
			        
			        if(zero_percentage > .5)
				        compressed_string   = StringMapper.compressZeroStrings(clipped_map_string);
			        else
			        	compressed_string   = StringMapper.compressOneStrings(clipped_map_string);
			        
			        int number_of_iterations = StringMapper.getIterations(compressed_string);
			        
			        int combined_unary_bytelength = compressed_string.length + map_table.length;
			        System.out.println("Combined byte length of packed strings and table is " + combined_unary_bytelength);
			        System.out.println("Number of iterations of strings was " + number_of_iterations);
			      
		    	    channel = DeltaMapper.getValuesFromMixedDeltas2(delta, new_xdim , new_ydim, channel_init[j], map);
		        }
		        else if(delta_type == 7)
		        {
		    	    byte [] map = (byte[])map_list.get(i);
		    	    
		    	 
		    	    ArrayList map_histogram_list = StringMapper.getHistogram(map);
		    	    int map_min                  = (int)map_histogram_list.get(0);
			        int [] map_histogram         = (int[])map_histogram_list.get(1);
			        int value_range              = (int)map_histogram_list.get(2);
				    int [] map_table             = StringMapper.getRankTable(map_histogram);
				    
			        for(int k = 0; k < map.length; k++)
				    	map[k] -= map_min;
				    byte [] map_string = new byte[map.length * 4];
				    int map_bit_length = StringMapper.packStrings2(map, map_table, map_string);
				    
				    int map_byte_length = map_bit_length / 8;
				    if(map_bit_length % 8 != 0)
				    	map_byte_length++;
				    map_byte_length++;
				    
				    byte [] clipped_map_string = new byte[map_byte_length];
				    
				    for(int k = 0; k < map_byte_length - 1; k++)
				    {
				    	clipped_map_string[k] = map_string[k];
				    }
				    
				    if(map_bit_length % 8 != 0)
				    {
				        int extra_bits = 8 - (map_bit_length % 8);
				        extra_bits <<= 5;
				        clipped_map_string[map_byte_length - 1] = (byte)extra_bits;
				    }
				  
				    double zero_percentage   = map.length;
			        if(map_histogram.length > 1)
			        {
				        int min_histogram_value = Integer.MAX_VALUE;
				        for(int k = 0; k < map_histogram.length; k++)
					        if(map_histogram[k] < min_histogram_value)
						        min_histogram_value = map_histogram[k];
				        zero_percentage -= min_histogram_value;
			        }	
			        zero_percentage  /= map_bit_length;
			        if(zero_percentage < .5)
			        	clipped_map_string[map_byte_length - 1] |= 16;
			        
			        byte [] compressed_string = new byte[0];
			        
			        if(zero_percentage > .5)
				        compressed_string   = StringMapper.compressZeroStrings(clipped_map_string);
			        else
			        	compressed_string   = StringMapper.compressOneStrings(clipped_map_string);
			        
			        int number_of_iterations = StringMapper.getIterations(compressed_string);
			        
			        int combined_unary_bytelength = compressed_string.length + map_table.length;
			        System.out.println("Combined byte length of packed strings and table is " + combined_unary_bytelength);
			        System.out.println("Number of iterations of strings was " + number_of_iterations);
			        
			        channel = DeltaMapper.getValuesFromIdealDeltas4(delta, new_xdim , new_ydim, channel_init[j], map);	   	
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
		        	
		        	out.writeInt(channel_min[j]);
		        	out.writeInt(channel_init[j]);
		        	out.writeInt(channel_delta_min[j]);
	        	    out.writeInt(channel_length[j]);
	        	    int [] table = (int[])table_list.get(i);
	                out.writeShort(table.length);
	         
	                if(table.length <= Byte.MAX_VALUE * 2 + 1)
                    {
	                	byte [] buffer = new byte[table.length];
	                	for(int k = 0; k < buffer.length; k++)
	                		buffer[k] = (byte)table[k];
	                	out.write(buffer, 0, buffer.length);
                    }
	                else 
	                {
	                    for(int k = 0; k < table.length; k++)
	                        out.writeShort(table[k]);
                    }
		        	
		            if(delta_type == 5  || delta_type == 6 || delta_type == 7)
		            {
		            	byte [] map               = (byte [])map_list.get(i);
		            	ArrayList map_string_list = StringMapper.getStringList2(map);
						int map_min               = (int)map_string_list.get(0);
						int map_bit_length        = (int)map_string_list.get(1);
						int [] string_table       = (int [])map_string_list.get(2);
						byte [] compressed_map    = (byte [])map_string_list.get(3);
						
						int current_iterations = StringMapper.getIterations(compressed_map);
						if(current_iterations == 0 || current_iterations == 16)
							System.out.println("Map did not compress.");
						else
							System.out.println("Map compressed.");
								
		            	
						out.writeShort(string_table.length);
						for(int k = 0; k < string_table.length; k++)
			                out.writeShort(string_table[k]);
						out.writeInt(compressed_map.length);
			            out.write(compressed_map, 0, compressed_map.length);
			            out.writeByte(map_min);
			            out.writeInt(map.length);
		            }
		            
		            
            		/*
                    int n = code_length.length;
            		out.writeInt(n);
            		out.write(code_length, 0, n);
            		*/
		            
            		byte  [] code_length = (byte [])code_length_list.get(i);
            		int n = code_length.length;
            		System.out.println("Number of codes is " + n);
            		byte [] length_delta = new byte[n - 1];
            		
            		byte init_value = code_length[0];
            		length_delta[0] = (byte)(code_length[1] - code_length[0]);
            		
            		
            		out.writeByte(n);
            		out.writeByte(init_value);
            		
            		int max_delta = 0;
            		for(int k = 1; k < n - 1; k++)
            		{
            		    byte current_delta = (byte)(code_length[k + 1] - code_length[k]);	
            		    if(current_delta > max_delta)
            		    	max_delta = current_delta;
            		    length_delta[k] = current_delta;
            		}
            		out.writeByte(max_delta);
            		if(max_delta == 1)
            		{
            		    int byte_length = (n - 1) / 8;
            		    if((n - 1) % 8 != 0)
            		    	byte_length++;
            		    byte [] packed_length = new byte[byte_length];
            		    
            		    byte [] mask = SegmentMapper.getPositiveMask();
            		    
            		    int m = 0;
            		    outer: for(int k = 0; k < byte_length; k++)
            		    {
            		    	for(n = 0; n < 8; n++)
            		    	{
            		    		if(m == length_delta.length)
            		    	    	break outer;
            		    		if(length_delta[m] == 1)
            		    		    packed_length[k] |= mask[n];   
            		    		m++;
            		    	}
            		    }
            		    //out.writeByte(byte_length);
            		    out.write(packed_length, 0, byte_length);
            		}
            		else if(max_delta <= 3)
            		{
            			int byte_length = (n - 1) / 4;
            		    if((n - 1) % 4 != 0)
            		    	byte_length++;
            		    byte [] packed_length = new byte[byte_length];	
            		    
            		    int m = 0;
            		    outer: for(int k = 0; k < byte_length; k++)
            		    {
            		    	for(n = 0; n < 8; n += 2)
            		    	{
            		    		if(m == length_delta.length)
            		    	    	break outer;
            		    	  
            		    		byte value = length_delta[m];
            		    		System.out.println("Value is " + value);
            		    		if(value > 0)
            		    		{
            		    		    value <<= n;
            		    		    packed_length[k] |= value;
            		    		}
            		    		m++;
            		    	}
            		    }
            		    //out.writeByte(byte_length);
            		    out.write(packed_length, 0, byte_length);
            		}
            		else if(max_delta <= 8)
            		{
            			int byte_length = (n - 1) / 2;
            		    if((n - 1) % 2 != 0)
            		    	byte_length++;
            		    byte [] packed_length = new byte[byte_length];
            		    
            		    int m = 0;
            		    outer: for(int k = 0; k < byte_length; k++)
            		    {
            		    	for(n = 0; n < 8; n += 4)
            		    	{
            		    		if(m == length_delta.length)
            		    	    	break outer;
            		    	  
            		    		byte value = length_delta[m];
            		    		if(value > 0)
            		    		{
            		    		    value <<= n;
            		    		    packed_length[k] |= value;
            		    		}
            		    		m++;
            		    	}
            		    }
            		    out.write(packed_length, 0, byte_length);
            		}
            		else
            		{
            		    out.write(length_delta, 1, n - 1);    	
            		}
            		System.out.println("Code delta max is " + max_delta);
            		System.out.println();

            		byte [] string = (byte [])string_list.get(i);
 		            out.writeInt(string.length);
             		out.write(string, 0, string.length);
		        }
		      
		        out.flush();
		        out.close();
		        
		        File file = new File("foo");
		        long file_length = file.length();
		        double compression_rate = file_length;
		        compression_rate /= xdim * ydim * 3;
		        System.out.println("The file compression rate is " + String.format("%.4f", file_compression_rate));
				System.out.println("Delta type is " + delta_type);
		        System.out.println("Huffman compression rate is " + String.format("%.4f", compression_rate));
		        System.out.println();
		    }
			catch(Exception e)
			{
				System.out.println(e.toString());
			}
		}
	}
}