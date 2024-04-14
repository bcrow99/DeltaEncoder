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
	
	int pixel_quant = 0;
	int pixel_shift = 0;
	int correction  = 0;
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
	double file_compression_rate;

	ArrayList channel_list, table_list, string_list, channel_data, map_list;

	boolean initialized = false;
	
	
	int delta_type = 5;
	
	public static void main(String[] args)
	{
		if (args.length != 1)
		{
			System.out.println("Usage: java SimpleWriter <filename>");
			System.exit(0);
		}
		String prefix       = new String("C:/Users/Brian Crowley/Desktop/");
		String filename     = new String(args[0]);
	
		SimpleWriter writer = new SimpleWriter(prefix + filename);
		//SimpleWriter writer = new SimpleWriter(filename);
	}

	public SimpleWriter(String _filename)
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
			    
			    JFrame frame = new JFrame("Delta Writer " + filename);
			    //JFrame frame = new JFrame("Delta Writer");
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
				
				
				JMenu delta_menu = new JMenu("Delta");
				
				JRadioButtonMenuItem [] delta_button = new JRadioButtonMenuItem[7];
				
				delta_button[0] = new JRadioButtonMenuItem("H");
				delta_button[1] = new JRadioButtonMenuItem("V");
				delta_button[2] = new JRadioButtonMenuItem("HV average");
				delta_button[3] = new JRadioButtonMenuItem("Paeth");
				delta_button[4] = new JRadioButtonMenuItem("Gradient");
				delta_button[5] = new JRadioButtonMenuItem("Scanline");
				delta_button[6] = new JRadioButtonMenuItem("Ideal");
				
				delta_type = 5;
				delta_button[5].setSelected(true);
				
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
				
				for(int i = 0; i < 7; i++)
				{
					delta_button[i].addActionListener(new ButtonHandler(i));
					delta_menu.add(delta_button[i]);
				}
			
				menu_bar.add(file_menu);
				menu_bar.add(delta_menu);
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
		    	
		    	// Get the ideal delta sum.
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
			channel_data.clear();
			map_list.clear();
			
			
			for(int i = 0; i < 3; i++)
			{
				int j = channel_id[i];
				
				int [] quantized_channel = (int[])quantized_channel_list.get(j);
				
				ArrayList result = new ArrayList();
				if(delta_type == 0)
				{
					result = DeltaMapper.getHorizontalDeltasFromValues(quantized_channel, new_xdim, new_ydim);
					int sum = (int)result.get(0);
					//System.out.println("Horizontal sum is " + sum);	
				}
				else if(delta_type == 1)
				{
					result = DeltaMapper.getVerticalDeltasFromValues(quantized_channel, new_xdim, new_ydim);
					int sum = (int)result.get(0);
				    //System.out.println("Vertical sum is " + sum);
				}
				else if(delta_type == 2)
				{
					result = DeltaMapper.getAverageDeltasFromValues(quantized_channel, new_xdim, new_ydim);
					int sum = (int)result.get(0);
					//System.out.println("Average sum is " + sum);
				}
				else if(delta_type == 3)
				{
					result = DeltaMapper.getPaethDeltasFromValues(quantized_channel, new_xdim, new_ydim);
					int sum = (int)result.get(0);
					//System.out.println("Paeth sum is " + sum);	
				}
				else if(delta_type == 4)
				{
					result = DeltaMapper.getGradientDeltasFromValues(quantized_channel, new_xdim, new_ydim);
					int sum = (int)result.get(0);
					//System.out.println("Gradient sum is " + sum);	
				}
				else if(delta_type == 5)
				{
					result = DeltaMapper.getMixedDeltasFromValues(quantized_channel, new_xdim, new_ydim);
				    byte [] map = (byte [])result.get(2);
				    
				    if(map.length != new_ydim - 1)
				    	System.out.println("Map length does not agree with image dimension.");
				    map_list.add(map);
				    int sum = (int)result.get(0);
				    //System.out.println("Mixed sum is " + sum);	
				}
				else if(delta_type == 6)
				{
					result = DeltaMapper.getIdealDeltasFromValues(quantized_channel, new_xdim, new_ydim);
				    byte [] map = (byte [])result.get(2);
				    map_list.add(map);
				    int sum = (int)result.get(0);
				    
				    
				    byte [] map_string       = new byte[map.length];
				    ArrayList histogram_list = StringMapper.getHistogram(map);
				    int [] histogram         = (int[])histogram_list.get(1);
					int [] string_table      = StringMapper.getRankTable(histogram);
				    
				    int map_length           = StringMapper.packStrings2(map, string_table, map_string);
				    
				    double zero_one_ratio = map.length;
		            if(histogram.length > 1)
		            {
					    int min_value = Integer.MAX_VALUE;
					    for(int k = 0; k < histogram.length; k++)
						 if(histogram[k] < min_value)
							min_value = histogram[k];
					    zero_one_ratio -= min_value;
		            }	
			        zero_one_ratio  /= map_length;
			        
			        byte[] map_compression_string = new byte[1];
			        if(zero_one_ratio > .5)
					    map_compression_string   = StringMapper.compressZeroStrings(map_string, map_length);
			        else
			        	map_compression_string   = StringMapper.compressOneStrings(map_string, map_length);
			        double compression_rate = map_compression_string.length;
				    compression_rate /= map.length;
				    
				    System.out.println("The string compression rate for the map was " + String.format("%.4f", compression_rate));
				    
				    Deflater deflater = new Deflater();
			    	deflater.setInput(map_compression_string);
			    	byte [] zipped_string = new byte[2 * map.length];
	            	deflater.finish();
	            	int zipped_length = deflater.deflate(zipped_string);
	            	deflater.end();
	            	
	            	compression_rate = zipped_length;
	            	compression_rate /= map.length;
	            	System.out.println("The zipped string compression rate for the map was " + String.format("%.4f", compression_rate));
				   
				    // The ideal sum for function 2 produces a slightly larger
				    // number because the last delta in each row is simply assigned
				    // to the horizontal delta to make the map smaller.
				    System.out.println("Ideal sum is " + sum);	
				}
                int []    delta  = (int [])result.get(1);
               
				ArrayList histogram_list = StringMapper.getHistogram(delta);
			    channel_delta_min[j]     = (int)histogram_list.get(0);
			    int [] histogram         = (int[])histogram_list.get(1);
				int [] string_table = StringMapper.getRankTable(histogram);
				table_list.add(string_table);
				
				for(int k = 1; k < delta.length; k++)
					delta[k] -= channel_delta_min[j];
				byte [] string         = new byte[xdim * ydim * 16];
				channel_length[j]      = StringMapper.packStrings2(delta, string_table, string);
			
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
				    byte [] compression_string   = StringMapper.compressZeroStrings(string, channel_length[j]);
				    channel_compressed_length[j] = StringMapper.getBitlength(compression_string);
				    channel_string_type[i]       = StringMapper.getType(compression_string);
				    if(channel_string_type[i] != 0)
				    	System.out.println("Unexpected string type for 0 string.");
				    channel_iterations[i]        = StringMapper.getIterations(compression_string);
				    string_list.add(compression_string);   
		        }   
		        else
		        {
		        	byte [] compression_string   = StringMapper.compressOneStrings(string, channel_length[j]);
				    channel_compressed_length[j] = StringMapper.getBitlength(compression_string);
				    channel_string_type[i]       = StringMapper.getType(compression_string);
				    channel_iterations[i]        = StringMapper.getIterations(compression_string);
				    if(channel_string_type[i] != 1 && channel_iterations[i] != 0)
				    	System.out.println("Unexpected string type for 1 string.");
				    string_list.add(compression_string);  
		        }
			}
			
			ArrayList resized_channel_list = new ArrayList();
			
			for(int i = 0; i < 3; i++)
		    {
			    byte [] string = (byte [])string_list.get(i);
			    int  [] table  = (int [])table_list.get(i);
			    int  [] delta  = new int[new_xdim * new_ydim]; 
			    int j          = channel_id[i];
			    
			    if(channel_length[j] != channel_compressed_length[j])
			    {
			    	byte [] decompressed_string = new byte[xdim * ydim * 16];
			    	int decompressed_length = 0;
			    	if(channel_string_type[i] == 0)
			    		decompressed_length = StringMapper.decompressZeroStrings(string, channel_compressed_length[j], decompressed_string);
			    	else
			    		decompressed_length = StringMapper.decompressOneStrings(string, channel_compressed_length[j], decompressed_string);
			    	if(decompressed_length != channel_length[j])
			    		System.out.println("Decompressed length different from original length.");
			    	int number_unpacked = StringMapper.unpackStrings2(decompressed_string, table, delta);
					if(number_unpacked != new_xdim * new_ydim)
					    System.out.println("Number of values unpacked does not agree with image dimensions.");	
			    }
			    else
			    {
			    	int number_unpacked = StringMapper.unpackStrings2(string, table, delta);
					if(number_unpacked != new_xdim * new_ydim)
					    System.out.println("Number of values unpacked does not agree with image dimensions.");	
			    }
			    
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
		        	
		        	out.writeInt(channel_delta_min[j]);
		        
		        	out.writeInt(channel_length[j]);
		        	
		        	// If the string didn't compress, 
		        	// this is the same as the uncompressed length.
		        	out.writeInt(channel_compressed_length[j]);
		        	
		        	out.writeByte(channel_iterations[i]);
		        	
		        	int [] table = (int[])table_list.get(i);
		            out.writeShort(table.length);
		         
		            int max_byte_value  = Byte.MAX_VALUE * 2 + 1;
					int max_short_value = Short.MAX_VALUE * 2 + 1;
					
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
		            
		            if(delta_type == 5  || delta_type == 6)
		            {
		            	byte [] map = (byte [])map_list.get(i);
		            	
		            	ArrayList result  = StringMapper.getHistogram(map);
		            	int min_value     = (int)result.get(0);
		            	int [] histogram  = (int [])result.get(1);
		            	int value_range   = (int)result.get(2);
		            	int [] string_table = StringMapper.getRankTable(histogram);
					
		            	for(int k = 0; k < map.length; k++)
							map[k] -= min_value;
						byte [] string = new byte[map.length * 2];
						int map_length = StringMapper.packStrings2(map, string_table, string);
						
						double zero_one_ratio = map.length;
			            if(histogram.length > 1)
			            {
						    int min_histogram_value = Integer.MAX_VALUE;
						    for(int k = 0; k < histogram.length; k++)
							if(histogram[k] < min_histogram_value)
								min_histogram_value = histogram[k];
						    zero_one_ratio -= min_histogram_value;
			            }	
				        zero_one_ratio  /= map_length;
				        
				        out.writeShort(string_table.length);
				       
			            for(int k = 0; k < string_table.length; k++)
			                out.writeShort(string_table[k]);
			            
				        if(zero_one_ratio > .5)
				        {
				            byte[] compressed_map = StringMapper.compressZeroStrings(string, map_length);
				            out.writeInt(compressed_map.length);
				            out.write(compressed_map, 0, compressed_map.length);
				        }
				        else
				        {
				        	byte[] compressed_map = StringMapper.compressOneStrings(string, map_length);	
				        	out.writeInt(compressed_map.length);
				            out.write(compressed_map, 0, compressed_map.length);
				        }
				        
			            out.writeByte(min_value);
			            out.writeInt(map.length);
		            }
		            
	                byte [] string = (byte [])string_list.get(i);
	                out.writeInt(string.length);
	                
	                Deflater deflater = new Deflater();
			    	deflater.setInput(string);
			    	byte [] zipped_string = new byte[2 *string.length];
	            	deflater.finish();
	            	int zipped_length = deflater.deflate(zipped_string);
	            	deflater.end(); 
	                out.writeInt(zipped_length);
	                out.write(zipped_string, 0, zipped_length);
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