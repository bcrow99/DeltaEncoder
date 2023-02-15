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
	
	ImageCanvas   image_canvas;
	
	int           xdim, ydim;
	String        filename;
	
	
	int [] original_pixel;
	int [] alpha;
	int [] red;
	int [] green;
	int [] blue;
	
	int     pixel_shift = 3;
	long    file_length = 0;
	int     block_xdim  = 20;
	int     block_ydim  = 14;
	int     x_offset    = 0;
	int     y_offset    = 0;
	int     x_limit     = 0;
	int     y_limit     = 0;
	
	ArrayList xdim_list, ydim_list;
	
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
		        
		        xdim_list = new ArrayList();
			    ydim_list = new ArrayList();
			    
			    for(int i = 2; i < xdim; i *= 2)
			    	xdim_list.add(i);
			    x_limit = (int)xdim_list.get(xdim_list.size() - 1);
			    
			    for(int i = 2; i < ydim; i *= 2)
			    	ydim_list.add(i);
			    y_limit = (int)ydim_list.get(ydim_list.size() - 1);
			    
			    System.out.print("Even xdim: ");
			    for(int i = 0; i < xdim_list.size(); i++)
			    {
			        System.out.print((int)xdim_list.get(i) + " ");  	
			    }
			    System.out.println();
			  
			    System.out.print("Even ydim: ");
			    for(int i = 0; i < ydim_list.size(); i++)
			    {
			        System.out.print((int)ydim_list.get(i) + " ");  	
			    }
			    System.out.println();
			    
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
				
				JMenuItem block_item   = new JMenuItem("Block Size");
				JDialog   block_dialog = new JDialog(frame, "Block Size");
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
				
				JPanel block_panel  = new JPanel(new BorderLayout());
				JPanel xblock_panel = new JPanel(new BorderLayout());
				JPanel yblock_panel = new JPanel(new BorderLayout());
				block_panel.add(xblock_panel, BorderLayout.NORTH);
				block_panel.add(yblock_panel, BorderLayout.SOUTH);
				
				JSlider xblock_slider = new JSlider();
				xblock_slider.setMinimum(0);
				xblock_slider.setMaximum(xdim_list.size() - 1);
				int position = xdim_list.size() - 1;
				xblock_slider.setValue(position);
				JTextField xblock_value = new JTextField(3);
				block_xdim = (int)xdim_list.get(position);
				xblock_value.setText(" " + block_xdim + " ");
				ChangeListener xblock_slider_handler = new ChangeListener()
				{
					public void stateChanged(ChangeEvent e)
					{
						JSlider slider = (JSlider) e.getSource();
						int index      = slider.getValue();
						block_xdim     = (int) xdim_list.get(index);
						xblock_value.setText(" " + block_xdim + " ");
						
						//if(slider.getValueIsAdjusting() == false)
						//	apply_item.doClick();
					}
				};
				xblock_slider.addChangeListener(xblock_slider_handler);
				xblock_panel.add(xblock_slider, BorderLayout.CENTER);
				xblock_panel.add(xblock_value, BorderLayout.EAST);
				
				JSlider yblock_slider = new JSlider();
				yblock_slider.setMinimum(0);
				yblock_slider.setMaximum(ydim_list.size() - 1);
				position = ydim_list.size() - 1;
				yblock_slider.setValue(position);
				JTextField yblock_value = new JTextField(3);
				block_ydim = (int)ydim_list.get(position);
				yblock_value.setText(" " + block_ydim + " ");
				ChangeListener yblock_slider_handler = new ChangeListener()
				{
					public void stateChanged(ChangeEvent e)
					{
						JSlider slider = (JSlider) e.getSource();
						int index      = slider.getValue();
						block_ydim     = (int) ydim_list.get(index);
						yblock_value.setText(" " + block_ydim + " ");
						//if(slider.getValueIsAdjusting() == false)
						//	apply_item.doClick();
					}
				};
				yblock_slider.addChangeListener(yblock_slider_handler);
				yblock_panel.add(yblock_slider, BorderLayout.CENTER);
				yblock_panel.add(yblock_value, BorderLayout.EAST);
				block_dialog.add(block_panel);
				settings_menu.add(block_item);
				
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
		    type_string    = new String[6];
			type_string[0] = new String("zipped bytes");
			type_string[1] = new String("strings");
			type_string[2] = new String("zipped strings");
			type_string[3] = new String("compressed strings");
			type_string[4] = new String("zipped compressed strings");
			type_string[5] = new String("mixed");
		  
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
		    channel_string[3] = new String("blue-green");
		    channel_string[4] = new String("red-green");
		    channel_string[5] = new String("red-blue");
		    
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
		    System.out.println("File compression rate is " + String.format("%.4f", file_ratio));
		    System.out.println();
		    
		    for(int i = 0; i < xdim * ydim; i++)
		    {
		        shifted_blue[i]  = blue[i]  >> pixel_shift;
		        shifted_green[i] = green[i] >> pixel_shift;
			    shifted_red[i]   = red[i]   >> pixel_shift; 
		    }
		    int [] shifted_blue_green = DeltaMapper.getDifference(shifted_blue, shifted_green);
	        int blue_green_min = 0;
		    for(int i = 0; i < xdim * ydim; i++)
		    	if(shifted_blue_green[i] < blue_green_min)
	            	blue_green_min = shifted_blue_green[i];
		    for(int i = 0; i < xdim * ydim; i++)
		    {
		    	shifted_blue_green[i] -= blue_green_min;
		    }
		    
		    int [] shifted_red_green = DeltaMapper.getDifference(shifted_red, shifted_green);
	        int red_green_min = 0;
		    for(int i = 0; i < xdim * ydim; i++)
		    	if(shifted_red_green[i] < red_green_min)
		    		red_green_min = shifted_red_green[i];
		    for(int i = 0; i < xdim * ydim; i++)
		    {
		    	shifted_red_green[i] -= red_green_min;
		    }
		    
		    int [] shifted_red_blue = DeltaMapper.getDifference(shifted_red, shifted_blue);
	        int red_blue_min = 0;
		    for(int i = 0; i < xdim * ydim; i++)
		    	if(shifted_red_blue[i] < red_blue_min)
		    		red_blue_min = shifted_red_blue[i];
		    for(int i = 0; i < xdim * ydim; i++)
		    {
		    	shifted_red_blue[i] -= red_blue_min;
		    }
		    
		    System.out.println("Block xdim is " + block_xdim + ", ydim is " + block_ydim ); 
		    System.out.println("X limit is " + x_limit + ", y limit is " + y_limit);
			System.out.println();
			
			int block_pixel_length   = block_xdim * block_ydim * 8;
			int clipped_pixel_length = x_limit * y_limit * 8;
		    
		    
			int [] block = DeltaMapper.extract(shifted_blue_green, xdim, x_offset, y_offset, x_limit, y_limit);
		    ArrayList data_list = DeltaMapper.getCompressionData(block, x_limit, y_limit);
		    ArrayList histogram_list = (ArrayList)data_list.get(0);
		    int [] histogram = (int [])histogram_list.get(1);
		    double [] rate   = (double [])data_list.get(1);
		    int delta_sum    = (int)data_list.get(2);
			
		    
		    System.out.println("Clipped blue-green frame:");
		    System.out.println("The compression rate for zipped delta bytes is " + String.format("%.4f", rate[0]));
		    System.out.println("The compression rate for delta strings is " + String.format("%.4f", rate[1]));
		    System.out.println("The compression rate for zipped delta strings is " + String.format("%.4f", rate[2]));
		    System.out.println("The compression rate for compressed delta strings is " + String.format("%.4f", rate[3]));
		    System.out.println("The compression rate for zipped compressed delta strings is " + String.format("%.4f", rate[4]));
		    
		    double delta_sum_area_ratio = delta_sum;
		    delta_sum_area_ratio /= x_limit * y_limit;
		    //System.out.println("Delta sum is " + delta_sum);
		    System.out.println("Delta sum area ratio is " + String.format("%.4f", delta_sum_area_ratio));
		    System.out.println();
		    
		    
		    double overhead = 16;
		    overhead /= x_limit * y_limit;
		    System.out.println("Overhead is " + String.format("%.4f", overhead));
		    
		    int xblocks = x_limit / block_xdim;
		    int yblocks = y_limit / block_ydim;
		    
		    int total_delta_sum = 0;
		    
		    int yoffset   = 0;
		    for(int i = 0; i < yblocks; i++)
		    {
		    	int xoffset = 0;
		    	for(int j = 0; j < xblocks; j++)
		    	{
		    		block      = DeltaMapper.extract(shifted_blue_green, xdim, xoffset, yoffset, block_xdim, block_ydim);
		    		ArrayList block_list = DeltaMapper.getCompressionData(block, block_xdim, block_ydim);
		    		histogram_list = (ArrayList)block_list.get(0);
		 		    histogram = (int [])histogram_list.get(1);
		 		    rate   = (double [])block_list.get(1);
		 		    int number_of_values = histogram.length;
		 		    delta_sum = (int)block_list.get(2);
		  		    
		  		    int block_number = i * xblocks + j;
		  		    System.out.println("Block " + block_number);
		  		    System.out.println("The compression rate for zipped delta bytes is " + String.format("%.4f", rate[0]));
				    System.out.println("The compression rate for delta strings is " + String.format("%.4f", rate[1]));
				    System.out.println("The compression rate for zipped delta strings is " + String.format("%.4f", rate[2]));
				    System.out.println("The compression rate for compressed delta strings is " + String.format("%.4f", rate[3]));
				    System.out.println("The compression rate for zipped compressed delta strings is " + String.format("%.4f", rate[4]));
				    delta_sum_area_ratio = delta_sum;
				    delta_sum_area_ratio /= block_xdim * block_ydim;
				    
				    total_delta_sum += delta_sum;
				    //System.out.println("Delta sum is " + delta_sum);
				    System.out.println("Delta sum area ratio is " + String.format("%.4f", delta_sum_area_ratio));
				    System.out.println();
				    
				    xoffset += block_xdim;
		  		    
		    	}
		    	yoffset += block_ydim;
		    }
		    double average_delta_sum_ratio = total_delta_sum;
		    average_delta_sum_ratio /= x_limit * y_limit;
		    System.out.println("Average delta sum area ratio for the blocks is " + String.format("%.4f", average_delta_sum_ratio));
		    overhead = xblocks * yblocks * 16;
		    overhead /= x_limit * y_limit;
		    System.out.println("Overhead is " + String.format("%.4f", overhead));
			
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