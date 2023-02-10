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

public class BlockTester
{
	BufferedImage original_image;
	BufferedImage image;
	JMenuItem     apply_item;
	
	JDialog       shift_dialog;
	JTextField    shift_value;
	
	JDialog       block_dialog;
	JTextField    block_value;
	
	ImageCanvas   image_canvas;
	
	int           xdim, ydim;
	String        filename;
	
	
	int [] original_pixel;
	int [] alpha;
	int [] red;
	int [] green;
	int [] blue;
	
	int     pixel_shift  = 3;
	boolean huffman_only = true;
	long    file_length  = 0;

	int     block_xdim   = 16;
	int     block_ydim   = 16;
	int     block_dim    = 16;
	int     x_offset      = 0;
	int     y_offset      = 0;
	
	public static void main(String[] args)
	{
		if (args.length != 1)
		{
			System.out.println("Usage: java BlockTester <filename>");
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
		BlockTester encoder = new BlockTester(prefix + filename);
	}

	public BlockTester(String _filename)
	{
		filename = _filename;
		try
		{
			File file = new File(filename);
			file_length = file.length();
			original_image = ImageIO.read(file);
			int raster_type = original_image.getType();
			ColorModel color_model = original_image.getColorModel();
			int number_of_channels = color_model.getNumColorComponents();
			int number_of_bits = color_model.getPixelSize();
			xdim = original_image.getWidth();
			ydim = original_image.getHeight();
			int pixel_length = xdim * ydim * 8;
		    
		    original_pixel = new int[xdim * ydim];
		    alpha          = new int[xdim * ydim];
			blue           = new int[xdim * ydim];
		    green          = new int[xdim * ydim];
		    red            = new int[xdim * ydim];
		    
			
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
               
			    JFrame frame = new JFrame("Block Tester");
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
				
				
				 
				JMenuItem block_item = new JMenuItem("Block Size");
				ActionListener block_handler = new ActionListener()
				{
					public void actionPerformed(ActionEvent e)
					{
						Point location_point = frame.getLocation();
						int x = (int) location_point.getX();
						int y = (int) location_point.getY();

						x += xdim;

						block_dialog.setLocation(x, y);
						block_dialog.pack();
						block_dialog.setVisible(true);
					}
				};
				block_item.addActionListener(block_handler);
				block_dialog = new JDialog(frame, "Block Size");
				JPanel block_panel = new JPanel(new BorderLayout());
				JSlider block_slider = new JSlider();
				block_slider.setMinimum(2);
				if(xdim < ydim)
				    block_slider.setMaximum(xdim);
				else
				    block_slider.setMaximum(ydim);
				block_slider.setValue(block_dim);
				block_value = new JTextField(3);
				block_value.setText(" " + block_dim + " ");
				ChangeListener block_slider_handler = new ChangeListener()
				{
					public void stateChanged(ChangeEvent e)
					{
						JSlider slider = (JSlider) e.getSource();
						block_dim = slider.getValue();
						block_xdim = block_dim;
						block_ydim = block_dim;
						block_value.setText(" " + block_dim + " ");
						if(slider.getValueIsAdjusting() == false)
						{
							apply_item.doClick();
						}
					}
				};
				block_slider.addChangeListener(block_slider_handler);
				block_panel.add(block_slider, BorderLayout.CENTER);
				block_panel.add(block_value, BorderLayout.EAST);
				block_dialog.add(block_panel);
				
				settings_menu.add(block_item);
				
			
				JCheckBoxMenuItem set_huffman = new JCheckBoxMenuItem("Huffman Only");
				ActionListener huffman_handler = new ActionListener()
				{
					public void actionPerformed(ActionEvent e) 
		            {
		            	JCheckBoxMenuItem item = (JCheckBoxMenuItem) e.getSource();
		            	if(huffman_only == true)
						{
		            		huffman_only = false;
							item.setState(false);
						}
						else
						{
							huffman_only = true;
							item.setState(true);	
						}
		            }   	
				};
				set_huffman.addActionListener(huffman_handler);
				if(huffman_only)
					set_huffman.setState(true);
				else
					set_huffman.setState(false);
				settings_menu.add(set_huffman);
				
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
		int    [] shifted_blue, shifted_green, shifted_red;
		byte   [] delta_bytes, delta_strings, zipped_strings, compressed_strings;
		double [] channel_rate, set_rate;
		int    [] channel_type;
		String [] type_string;
		String [] set_string;
		String [] channel_string;
		int    [] set_sum, channel_sum;
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
		    delta_strings      = new byte[xdim * ydim * 2];
		    zipped_strings     = new byte[xdim * ydim * 8];
		    compressed_strings = new byte[xdim * ydim * 8];
		    
		    // The 5 different kinds of compressions per channel.
		    type_string    = new String[5];
			type_string[0] = new String("zipped bytes");
			type_string[1] = new String("strings");
			type_string[2] = new String("zipped strings");
			type_string[3] = new String("compressed strings");
			type_string[4] = new String("zipped compressed strings");
		  
		    // The delta sum for each channel.
		    channel_sum  = new int[6];
		    // The minimum compression rate per channel.
		    channel_rate = new double[6];
		    // The type of compression that produces the minimum result.
		    channel_type = new int[6];
		    
		    channel_string    = new String[6];
		    channel_string[0] = new String("blue");
		    channel_string[1] = new String("green");
		    channel_string[2] = new String("red");
		    channel_string[3] = new String("blue green");
		    channel_string[4] = new String("red green");
		    channel_string[5] = new String("red blue");
		    
		    // The delta sum for each set of channels that can produce an image.
		    set_sum       = new int[10];
		    set_rate      = new double[10];
		    set_string    = new String[10]; 
		    set_string[0] = new String("blue, green, and red.");
		    set_string[1] = new String("blue, red-green, and red.");
		    set_string[2] = new String("blue, blue-green, and red.");
		    set_string[3] = new String("blue, blue-green, and red-green.");
		    set_string[4] = new String("blue, blue-green, and red-blue.");
		    set_string[5] = new String("blue-green, green, and red.");
		    set_string[6] = new String("blue-green, red-green, and red.");
		    set_string[7] = new String("blue-green, green, and red-green.");
		    set_string[8] = new String("red-blue, green, and red-green.");
		    set_string[9] = new String("red-blue, red-green, and red.");
		    
		    pixel_length = xdim * ydim * 8;
		    file_ratio   = file_length * 8;
		    file_ratio  /= pixel_length * 3;
		}
		
		public void actionPerformed(ActionEvent event)
		{
		    System.out.println("Pixel shift is " + pixel_shift);
		    System.out.println("Huffman only is " + huffman_only);
		    System.out.println("File compression rate is " + String.format("%.4f", file_ratio));
		    System.out.println();
		    
		    for(int i = 0; i < xdim * ydim; i++)
		    {
		        shifted_blue[i]  = blue[i]  >> pixel_shift;
		        shifted_green[i] = green[i] >> pixel_shift;
			    shifted_red[i]   = red[i]   >> pixel_shift; 
		    }
		    int min_value, min_index, max_value, max_index;
		    
		    ArrayList xdim_list = new ArrayList();
		    ArrayList ydim_list = new ArrayList();
		    int dim = xdim / 2;
		    while(dim > 1)
		    {
		    	xdim_list.add(dim);
		    	dim /= 2;
		    }
		    dim = ydim / 2;
		    while(dim > 1)
		    {
		    	ydim_list.add(dim);
		    	dim /= 2;
		    }
		    int xdim_list_size = xdim_list.size();
		    int ydim_list_size = ydim_list.size();
		    
		    
		    int min_list_size = 0;
		    if(xdim_list_size < ydim_list_size)
		    	min_list_size = xdim_list_size;
		    else
		    	min_list_size = ydim_list_size;
		    
		    int [] blue_sum_value, green_sum_value, red_sum_value;
		    blue_sum_value  = new int[min_list_size];
	    	green_sum_value = new int[min_list_size];
	    	red_sum_value   = new int[min_list_size];
	    	int _xdim       = 0;
	    	int _ydim       = 0;
	    	for(int i = 0; i < min_list_size; i++)
	        {
	        	_xdim = (int)xdim_list.get(i);
	        	_ydim = (int)ydim_list.get(i);
	        	int blue_block_delta_sum  = DeltaMapper.getBlockDeltaSum(shifted_blue,  xdim, ydim, _xdim, _ydim);
				int green_block_delta_sum = DeltaMapper.getBlockDeltaSum(shifted_green, xdim, ydim, _xdim, _ydim);
				int red_block_delta_sum   = DeltaMapper.getBlockDeltaSum(shifted_red,   xdim, ydim, _xdim, _ydim);
				int number_of_blocks      = (xdim / _xdim) * (ydim / _ydim);
				blue_sum_value[i]         = blue_block_delta_sum  + number_of_blocks * 16;
				green_sum_value[i]        = green_block_delta_sum + number_of_blocks * 16;
		        red_sum_value[i]          = red_block_delta_sum   + number_of_blocks * 16;		
	        }
	    
	    	// These are the optimal block sizes that divide up the image evenly.
		    min_index = 0;
		    min_value = Integer.MAX_VALUE;
		    for(int i = 0; i < blue_sum_value.length; i++)
		    {
		    	if(blue_sum_value[i] < min_value)
		    	{
		    		min_value = blue_sum_value[i];
		    		min_index = i;
		    	}
		    }
		    
		    _xdim = (int)xdim_list.get(min_index);
		    _ydim = (int)ydim_list.get(min_index);
		    System.out.println("Minimum sampled block size for blue channel is " + _xdim + "x" + _ydim);
		    
		    min_index = 0;
		    min_value = green_sum_value[0];
		    for(int i = 1; i < green_sum_value.length; i++)
		    {
		    	if(green_sum_value[i] < min_value)
		    	{
		    		min_value = green_sum_value[i];
		    		min_index = i;
		    	}
		    }
		    _xdim = (int)xdim_list.get(min_index);
		    _ydim = (int)ydim_list.get(min_index);
            System.out.println("Minimum sampled block size for green channel is " + _xdim + "x" + _ydim); 
            
		    min_index = 0;
		    min_value = red_sum_value[0];
		    for(int i = 1; i < red_sum_value.length; i++)
		    {
		    	if(red_sum_value[i] < min_value)
		    	{
		    		min_value = red_sum_value[i];
		    		min_index = i;
		    	}
		    }
		    
		    _xdim = (int)xdim_list.get(min_index);
		    _ydim = (int)ydim_list.get(min_index);
            System.out.println("Minimum sampled block size for red channel is " + _xdim + "x" + _ydim);
            System.out.println();
            
          
		    System.out.println("Complete green frame:");
		    
		    ArrayList data_list = DeltaMapper.getCompressionData(shifted_green, xdim, ydim);
		    ArrayList histogram_list = (ArrayList)data_list.get(0);
		    int [] histogram = (int [])histogram_list.get(1);
		    double [] rate = (double [])data_list.get(1);
		    int delta_sum = (int)data_list.get(2);
		    System.out.println("The number of different delta values is " + histogram.length);
		    System.out.println("The compression rate for zipped delta bytes is " + String.format("%.4f", rate[0]));
		    System.out.println("The compression rate for delta strings is " + String.format("%.4f", rate[1]));
		    System.out.println("The compression rate for zipped delta strings is " + String.format("%.4f", rate[2]));
		    System.out.println("The compression rate for compressed delta strings is " + String.format("%.4f", rate[3]));
		    System.out.println("The compression rate for zipped compressed delta strings is " + String.format("%.4f", rate[4]));
		    System.out.println();
		    
			
			System.out.println("Block xdim is " + block_xdim + ", ydim is " + block_ydim ); 
			System.out.println();
			
			int block_pixel_length = block_xdim * block_ydim * 8;
			
		
		    System.out.println("Blue:");
		    int [] block = DeltaMapper.extract(shifted_blue, xdim, x_offset, y_offset, block_xdim, block_ydim);
		    data_list = DeltaMapper.getCompressionData(block, block_xdim, block_ydim);
		    histogram_list = (ArrayList)data_list.get(0);
		    histogram = (int [])histogram_list.get(1);
		    rate = (double [])data_list.get(1);
		    delta_sum = (int)data_list.get(2);
		    System.out.println("The number of different delta values is " + histogram.length);
		    System.out.println("The compression rate for zipped delta bytes is " + String.format("%.4f", rate[0]));
		    System.out.println("The compression rate for delta strings is " + String.format("%.4f", rate[1]));
		    System.out.println("The compression rate for zipped delta strings is " + String.format("%.4f", rate[2]));
		    System.out.println("The compression rate for compressed delta strings is " + String.format("%.4f", rate[3]));
		    System.out.println("The compression rate for zipped compressed delta strings is " + String.format("%.4f", rate[4]));
			
			double min_rate_value = rate[0];
			min_index      = 0;
			for(int i = 1; i < 5; i++)
			{
				if(rate[i] < min_rate_value)
				{
					min_rate_value = rate[i];
					min_index = i;
				}
			}
			channel_rate[0] = min_rate_value;
			channel_type[0] = min_index;
			channel_sum[0]  = delta_sum;
			String compression_type = type_string[min_index];
			System.out.println("Minimum rate is " + String.format("%.4f", min_rate_value) + " using " + compression_type);
			System.out.println();
			
			System.out.println("Green:");
		    block = DeltaMapper.extract(shifted_green, xdim, x_offset, y_offset, block_xdim, block_ydim);
		    data_list = DeltaMapper.getCompressionData(block, block_xdim, block_ydim);
		    histogram_list = (ArrayList)data_list.get(0);
		    histogram = (int [])histogram_list.get(1);
		    rate = (double [])data_list.get(1);
		    delta_sum = (int)data_list.get(2);
		    System.out.println("The number of different delta values is " + histogram.length);
		    System.out.println("The compression rate for zipped delta bytes is " + String.format("%.4f", rate[0]));
		    System.out.println("The compression rate for delta strings is " + String.format("%.4f", rate[1]));
		    System.out.println("The compression rate for zipped delta strings is " + String.format("%.4f", rate[2]));
		    System.out.println("The compression rate for compressed delta strings is " + String.format("%.4f", rate[3]));
		    System.out.println("The compression rate for zipped compressed delta strings is " + String.format("%.4f", rate[4]));
		    
			min_rate_value = rate[0];
			min_index = 0;
			for(int i = 1; i < 5; i++)
			{
				if(rate[i] < min_rate_value)
				{
					min_rate_value = rate[i];
					min_index = i;
				}
			}
			channel_rate[1] = min_rate_value;
			channel_type[1] = min_index;
			channel_sum[1]  = delta_sum;
			compression_type = type_string[min_index];
			System.out.println("Minimum rate is " + String.format("%.4f", channel_rate[1]) + " using " + compression_type);
			System.out.println();
				
		    System.out.println("Red:");
		    block = DeltaMapper.extract(shifted_red, xdim, x_offset, y_offset, block_xdim, block_ydim);
		    data_list = DeltaMapper.getCompressionData(block, block_xdim, block_ydim);
		    histogram_list = (ArrayList)data_list.get(0);
		    histogram = (int [])histogram_list.get(1);
		    rate = (double [])data_list.get(1);
		    delta_sum = (int)data_list.get(2);
		    System.out.println("The number of different delta values is " + histogram.length);
		    System.out.println("The compression rate for zipped delta bytes is " + String.format("%.4f", rate[0]));
		    System.out.println("The compression rate for delta strings is " + String.format("%.4f", rate[1]));
		    System.out.println("The compression rate for zipped delta strings is " + String.format("%.4f", rate[2]));
		    System.out.println("The compression rate for compressed delta strings is " + String.format("%.4f", rate[3]));
		    System.out.println("The compression rate for zipped compressed delta strings is " + String.format("%.4f", rate[4]));
			min_rate_value = rate[0];
			min_index = 0;
			for(int i = 1; i < 5; i++)
			{
				if(rate[i] < min_rate_value)
				{
					min_rate_value = rate[i];
					min_index = i;
				}
			}
			channel_rate[2] = min_rate_value;
			channel_type[2] = min_index;
			channel_sum[2]  = delta_sum;
			compression_type = type_string[min_index];
			System.out.println("Minimum rate is " + String.format("%.4f", channel_rate[2]) + " using " + compression_type);
			System.out.println();
			
			
			
			System.out.println("Blue-green:");
			int [] shifted_blue_green = DeltaMapper.getDifference(shifted_blue, shifted_green);
	        int blue_green_min = 0;
		    for(int i = 0; i < xdim * ydim; i++)
		    	if(shifted_blue_green[i] < blue_green_min)
	            	blue_green_min = shifted_blue_green[i];
		    for(int i = 0; i < xdim * ydim; i++)
		    {
		    	shifted_blue_green[i] -= blue_green_min;
		    }
		    
		    block = DeltaMapper.extract(shifted_blue_green, xdim, x_offset, y_offset, block_xdim, block_ydim);
		    data_list = DeltaMapper.getCompressionData(block, block_xdim, block_ydim);
		    histogram_list = (ArrayList)data_list.get(0);
		    histogram = (int [])histogram_list.get(1);
		    rate = (double [])data_list.get(1);
		    delta_sum = (int)data_list.get(2);
		    System.out.println("The number of different delta values is " + histogram.length);
		    System.out.println("The compression rate for zipped delta bytes is " + String.format("%.4f", rate[0]));
		    System.out.println("The compression rate for delta strings is " + String.format("%.4f", rate[1]));
		    System.out.println("The compression rate for zipped delta strings is " + String.format("%.4f", rate[2]));
		    System.out.println("The compression rate for compressed delta strings is " + String.format("%.4f", rate[3]));
		    System.out.println("The compression rate for zipped compressed delta strings is " + String.format("%.4f", rate[4])); 
		    
			min_rate_value = rate[0];
			min_index = 0;
			for(int i = 1; i < 4; i++)
			{
				if(rate[i] < min_rate_value)
				{
					min_rate_value = rate[i];
					min_index = i;
				}
			}
			
			channel_rate[3] = min_rate_value;
			channel_type[3] = min_index;
			channel_sum[3]  = delta_sum;
			compression_type = type_string[min_index];
			System.out.println("Minimum rate is " + String.format("%.4f", channel_rate[3]) + " using " + compression_type);
			System.out.println();
			
		    
			System.out.println("Red-green:");
			int [] shifted_red_green = DeltaMapper.getDifference(shifted_red, shifted_green);
	        int red_green_min = 0;
		    for(int i = 0; i < xdim * ydim; i++)
		    	if(shifted_red_green[i] < red_green_min)
		    		red_green_min = shifted_red_green[i];
		    for(int i = 0; i < xdim * ydim; i++)
		    {
		    	shifted_red_green[i] -= red_green_min;
		    }
		    
		    block = DeltaMapper.extract(shifted_red_green, xdim, x_offset, y_offset, block_xdim, block_ydim);
		     
		    data_list = DeltaMapper.getCompressionData(block, block_xdim, block_ydim);
		    histogram_list = (ArrayList)data_list.get(0);
		    histogram = (int [])histogram_list.get(1);
		    rate = (double [])data_list.get(1);
		    delta_sum = (int)data_list.get(2);
		    System.out.println("The number of different delta values is " + histogram.length);
		    System.out.println("The compression rate for zipped delta bytes is " + String.format("%.4f", rate[0]));
		    System.out.println("The compression rate for delta strings is " + String.format("%.4f", rate[1]));
		    System.out.println("The compression rate for zipped delta strings is " + String.format("%.4f", rate[2]));
		    System.out.println("The compression rate for compressed delta strings is " + String.format("%.4f", rate[3]));
		    System.out.println("The compression rate for zipped compressed delta strings is " + String.format("%.4f", rate[4]));
			min_rate_value = rate[0];
			min_index = 0;
			for(int i = 1; i < 5; i++)
			{
				if(rate[i] < min_rate_value)
				{
					min_rate_value = rate[i];
					min_index = i;
				}
			}
			channel_rate[4] = min_rate_value;
			channel_type[4] = min_index;
			channel_sum[4] = delta_sum;
			compression_type = type_string[min_index];
			System.out.println("Minimum rate is " + String.format("%.4f", channel_rate[4]) + " using " + compression_type);
			System.out.println();
			
		    
			System.out.println("Red-blue:");
			int [] shifted_red_blue = DeltaMapper.getDifference(shifted_red, shifted_blue);
	        int red_blue_min = 0;
		    for(int i = 0; i < xdim * ydim; i++)
		    	if(shifted_red_blue[i] < red_blue_min)
		    		red_blue_min = shifted_red_blue[i];
		    for(int i = 0; i < xdim * ydim; i++)
		    {
		    	shifted_red_blue[i] -= red_blue_min;
		    }
		    
		    block = DeltaMapper.extract(shifted_red_blue, xdim, x_offset, y_offset, block_xdim, block_ydim);
		    data_list = DeltaMapper.getCompressionData(block, block_xdim, block_ydim);
		    histogram_list = (ArrayList)data_list.get(0);
		    histogram = (int [])histogram_list.get(1);
		    rate = (double [])data_list.get(1);
		    delta_sum = (int)data_list.get(2);
		    System.out.println("The number of different delta values is " + histogram.length);
		    System.out.println("The compression rate for zipped delta bytes is " + String.format("%.4f", rate[0]));
		    System.out.println("The compression rate for delta strings is " + String.format("%.4f", rate[1]));
		    System.out.println("The compression rate for zipped delta strings is " + String.format("%.4f", rate[2]));
		    System.out.println("The compression rate for compressed delta strings is " + String.format("%.4f", rate[3]));
		    System.out.println("The compression rate for zipped compressed delta strings is " + String.format("%.4f", rate[4]));
			
			min_rate_value = rate[0];
			min_index = 0;
			for(int i = 1; i < 5; i++)
			{
				if(rate[i] < min_rate_value)
				{
					min_rate_value = rate[i];
					min_index = i;
				}
			}
			
			channel_rate[5] = min_rate_value;
			channel_type[5] = min_index;
			channel_sum[5]  = delta_sum;
			compression_type = type_string[min_index];
			System.out.println("Minimum rate is " + String.format("%.4f", channel_rate[5]) + " using " + compression_type);
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
			
			System.out.println("The blue compression rate is       " + String.format("%.4f", channel_rate[0]));
			System.out.println("The green compression rate is      " + String.format("%.4f", channel_rate[1]));
			System.out.println("The red compression rate is        " + String.format("%.4f", channel_rate[2]));
			System.out.println("The blue green compression rate is " + String.format("%.4f", channel_rate[3]));
			System.out.println("The red green compression rate is  " + String.format("%.4f", channel_rate[4]));
			System.out.println("The red blue compression rate is   " + String.format("%.4f", channel_rate[5]));
			System.out.println();
			
			min_index = 0;
			min_value = Integer.MAX_VALUE;
			for(int i = 0; i < 10; i++)
			{
				if(set_sum[i] < min_value)
				{
				    min_value = set_sum[i];
				    min_index = i;
				}
			}
			System.out.println("A set of channels with the lowest delta sum is " + set_string[min_index]);
			System.out.println("The compression rate is " + String.format("%.4f", set_rate[min_index]));
			
			max_index = 0;
			max_value = 0;
			for(int i = 0; i < 10; i++)
			{
				if(set_sum[i] > max_value)
				{
				    max_value = set_sum[i];
				    max_index = i;
				}
			}
			System.out.println("A set of channels with the highest delta sum is " + set_string[max_index]);
			System.out.println("The compression rate is " + String.format("%.4f", set_rate[max_index]));
			
			
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