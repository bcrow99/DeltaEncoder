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

public class TableTester
{
	BufferedImage original_image;
	BufferedImage image;
	JMenuItem     apply_item;
	JDialog       shift_dialog;
	JTextField    shift_value;
	ImageCanvas   image_canvas;
	int           pixel_shift = 3;
	int           xdim, ydim;
	byte[]        temp;
	double        zero_ratio;
	
	public static void main(String[] args)
	{
		if (args.length != 1)
		{
			System.out.println("Usage: java TableTester <filename>");
			System.exit(0);
		}
		String      prefix    = new String("C:/Users/Brian Crowley/Desktop/");
		String      filename  = new String(args[0]);
		TableTester processor = new TableTester(prefix + filename);
	}

	public TableTester(String filename)
	{
		try
		{
			File file = new File(filename);
			original_image = ImageIO.read(file);
			int raster_type = original_image.getType();
			//System.out.println("The raster type is " + raster_type);
			ColorModel color_model = original_image.getColorModel();
			int number_of_channels = color_model.getNumColorComponents();
			//System.out.println("The number of channels is " + number_of_channels);
			int number_of_bits = color_model.getPixelSize();
			//System.out.println("The number of bits per pixel is " + number_of_bits);
			xdim = original_image.getWidth();
			ydim = original_image.getHeight();
			temp = new byte[5 * xdim * ydim];
			//System.out.println("Xdim is " + xdim + " and ydim is " + ydim);
			
			// Only interested in 3 channel RGB for now.
			
			if(raster_type == BufferedImage.TYPE_3BYTE_BGR)
			{
				int[]          pixel = new int[xdim * ydim];
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
			  
			    image = new BufferedImage(xdim, ydim, BufferedImage.TYPE_INT_RGB);
			    
			  
			    for(int i = 0; i < xdim; i++)
			    {
			    	for(int j = 0; j < ydim; j++)
			    	{
			    		image.setRGB(i, j, pixel[j * xdim + i]);
			    	}
			    }
			    
			    
			    int[]          red     = new int[xdim * ydim];
			    int[]          green   = new int[xdim * ydim];
			    int[]          blue    = new int[xdim * ydim];
			    
			    int            size    = xdim * ydim;
			    for(int i = 0; i < size; i++)
			    {
			    	red[i]   = (pixel[i] >> 16) & 0xff;
			    	green[i] = (pixel[i] >> 8) & 0xff; 
                    blue[i]  = pixel[i] & 0xff; 
			    }
			    
			    for(int i = 0; i < size; i++)
                {
                    pixel[i] = 0;
                    pixel[i] |= 255 << 24;
                    pixel[i] |= red[i] << 16;
                    pixel[i] |= green[i] << 8;
                    pixel[i] |= blue[i];
                }
               
			    JFrame frame = new JFrame("String Tester");
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
				
				apply_item           = new JMenuItem("Apply");
				ApplyHandler apply_handler        = new ApplyHandler();
				apply_item.addActionListener(apply_handler);
				
				JMenuItem reload_item = new JMenuItem("Reload");
				ReloadHandler reload_handler        = new ReloadHandler();
				reload_item.addActionListener(reload_handler);
				
				file_menu.add(apply_item);
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
						if (slider.getValueIsAdjusting() == false)
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
				
				
				/*
			    try
				{
					ImageIO.write(image, "jpg", new File("C:/Users/Brian Crowley/Desktop/foo.jpg"));
				} 
				catch (IOException e)
				{
					e.printStackTrace();
				}
				*/
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
		int [] original_pixel;
		int [] pixel;
		int [] red;
		int [] green;
		int [] blue;
		int [] scratch;
		int [] error;
	
		double[] red_double;
		double[] red_shrink;
		double[] red_expand;
		
		double[] green_double;
		double[] green_shrink;
		double[] green_expand;
		
		double[] blue_double;
		double[] blue_shrink;
		double[] blue_expand;
		
		double[] blue_green;
		double[] red_green;
		
		double[] scratch_double;
		ApplyHandler()
		{
			original_pixel = new int[xdim * ydim];
			pixel          = new int[xdim * ydim];
			red            = new int[xdim * ydim];
		    green          = new int[xdim * ydim];
		    blue           = new int[xdim * ydim];
		    scratch        = new int[xdim * ydim];
		    error          = new int[xdim * ydim];
		    
		    red_double   = new double[xdim * ydim];
		    green_double   = new double[xdim * ydim];
		    blue_double   = new double[xdim * ydim];
		    
		    red_green = new double[xdim * ydim / 4];
		    blue_green = new double[xdim * ydim / 4];
		    
		    
		    
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
	        }	
		}
		
		public void actionPerformed(ActionEvent event)
		{
		    int  size = xdim * ydim;
		    for(int i = 0; i < size; i++)
		    {
		    	red[i]   = (original_pixel[i] >> 16) & 0xff;
		    	green[i] = (original_pixel[i] >> 8) & 0xff; 
                blue[i]  =  original_pixel[i] & 0xff; 
		    }
		    
		    //System.out.println("Got here.");
		    for(int i = 0; i < size; i++)
		    { 
		    	//red[i] >>= pixel_shift;
	    	    red_double[i] = red[i];
	    	    
	    	    
		    	green[i] >>= pixel_shift;
		    	green_double[i] = green[i];
		    	
		    	
		    	//blue[i] >>= pixel_shift;
		    	blue_double[i] = blue[i];
		    }
		    
		    red_shrink   = DeltaMapper.shrink4(red_double, xdim, ydim);
		    green_shrink = DeltaMapper.shrink4(green_double, xdim, ydim);
		    blue_shrink  = DeltaMapper.shrink4(blue_double, xdim, ydim);
		    
		    double red_green_min  = Double.MAX_VALUE;
		    double red_green_max  = -Double.MAX_VALUE;
		    double blue_green_min = Double.MAX_VALUE;
		    double blue_green_max = -Double.MAX_VALUE;
		    
		    for(int i = 0; i < size / 4; i++)
		    { 
		    	red_green[i] = red_shrink[i] - green_shrink[i];
		        blue_green[i] = blue_shrink[i] - green_shrink[i];
		        
		        if(red_green[i] > red_green_max)
		        	red_green_max = red_green[i];
		        if(red_green[i] < red_green_min)
		        	red_green_min = red_green[i];
		        
		        if(blue_green[i] > blue_green_max)
		        	blue_green_max = blue_green[i];
		        if(blue_green[i] < blue_green_min)
		        	blue_green_min = blue_green[i];
		    }
		    
		    // Move the range of the values so they're all positive, 
		    // or move positive range to start from 0.
		    for(int i = 0; i < size / 4; i++)
		    {
		        red_green[i]  -= red_green_min;
		        blue_green[i] -= blue_green_min;
		    }
		        
		    // Get the integer values we use to get deltas and strings.
		    int shrunk_pixel_min = Integer.MAX_VALUE;
		    int shrunk_pixel_max = -Integer.MAX_VALUE;
		    int shrunk_pixel[]   = new int[size / 4];
		    
		    for(int i = 0; i < size / 4; i++)
		    {
		    	shrunk_pixel[i] = (int) (green_shrink[i] + .5);
		    	if(shrunk_pixel[i] > shrunk_pixel_max)
		        	shrunk_pixel_max = shrunk_pixel[i];
		        if(shrunk_pixel[i] < shrunk_pixel_min)
		        	shrunk_pixel_min = shrunk_pixel[i];
		    }
		    
		    System.out.println("The pixel min is " + shrunk_pixel_min);
		    System.out.println("The pixel max is " + shrunk_pixel_max);
		    System.out.println("The range is " + (shrunk_pixel_max - shrunk_pixel_min + 1));
		    System.out.println();
		    
		    int init_value = shrunk_pixel[0];
		    
		    int[] delta = DeltaMapper.getDeltasFromValues(shrunk_pixel, xdim / 2, ydim / 2, init_value);
		    
		    ArrayList histogram_list = DeltaMapper.getHistogram(delta);
		    int       delta_min      = (int)histogram_list.get(0);
		    int[]     histogram      = (int[])histogram_list.get(1);
		    int       delta_range    = histogram.length;
		    int       delta_max      = delta_min + delta_range - 1;
		    
		    /*
		    int delta_min = delta[0];
		    int delta_max = delta[0];
		   
		    for(int i = 0; i < size / 4; i++)
		    {
		    	if(delta[i] > delta_max)
		    		delta_max = delta[i];
		    	if(delta[i] < delta_min)
		    		delta_min = delta[i];
		    }
		    int range_of_delta_values = delta_max - delta_min + 1;
		    */
		    
		    System.out.println("The delta min is " + delta_min);
		    System.out.println("The delta max is " + delta_max);
		    System.out.println("The range is     " + delta_range);
		    System.out.println();
		    
		    
		    int random_lut[]    = DeltaMapper.getRandomTable(histogram);
		    int symmetric_lut[] = DeltaMapper.getSymmetricTable(histogram.length);
		    int modal_lut[]     = DeltaMapper.getModalTable(histogram);
		    
		 
		
		    /*
		    System.out.println("Random table:");
		    for(int i = 0; i <range_of_delta_values; i++)
		    {
		    	System.out.println(i + "-> " + random_lut[i]);
		    }
		    System.out.println();
		    */
		    
		    
		    /*
		    System.out.println("Symmetric table:");
		    for(i = 0; i < range_of_delta_values; i++)
		    {
		    	System.out.println(i + "-> " + symmetric_lut[i]);
		    }
		    System.out.println();
		    */
		    
		    /*
		    System.out.println("Modal table:");
		    for(int i = 0; i < number_of_different_delta_values; i++)
		    {
		    	System.out.println(i + "-> " + modal_lut[i]);
		    }
		    System.out.println();
		    */

		    for(int i = 0; i < delta.length; i++)
		    	delta[i] -= delta_min;
		    byte [] bit_strings = new byte[5 * xdim * ydim];
		 
          
		    int number_of_bits  = DeltaMapper.packStrings2(delta, random_lut, bit_strings);
		    //int number_of_bits  = DeltaMapper.packStrings2(delta, symmetric_lut, bit_strings);
		    //int number_of_bits  = DeltaMapper.packStrings2(delta, modal_lut, bit_strings);
		    byte [] compressed_bit_strings = new byte[5 * xdim * ydim];
		    byte [] compressed_bit_strings2 = new byte[5 * xdim * ydim];
		    byte [] compressed_bit_strings3 = new byte[5 * xdim * ydim];
		    
		    int compressed_number_of_bits = DeltaMapper.compressZeroBits(bit_strings, number_of_bits, compressed_bit_strings);
		    int compressed_number_of_bits2 = DeltaMapper.compressZeroBits(compressed_bit_strings, compressed_number_of_bits, compressed_bit_strings2); 
		    int compressed_number_of_bits3 = DeltaMapper.compressZeroBits(compressed_bit_strings2, compressed_number_of_bits2, compressed_bit_strings3);
		    int decompressed_number_of_bits = DeltaMapper.decompressZeroBits(compressed_bit_strings, compressed_number_of_bits, bit_strings);
		    
		    System.out.println("Number of bits in original image is         " + (xdim * ydim * 8));
		    System.out.println("Number of bits in unary strings is          " + number_of_bits);
		    System.out.println("Number of compressed bits is                " + compressed_number_of_bits);
		    System.out.println("Number of compressed bits on second pass is " + compressed_number_of_bits2);
		    System.out.println("Number of compressed bits on third pass is  " + compressed_number_of_bits3);
		    System.out.println("Number of decompressed bits is              " + decompressed_number_of_bits);
		    System.out.println();
		    
		    
		    
		    
		    
		    
		    
		    //  Regression testing.
		    int number_of_ints = DeltaMapper.unpackStrings2(bit_strings, random_lut, delta);
		  
		    //System.out.println("Number of ints unpacked is " + number_of_ints);
		 
		    for(int i = 0; i < size / 4; i++)
		    	delta[i] += delta_min;
		   
            int[] new_shrunk_pixel = DeltaMapper.getValuesFromDeltas(delta, xdim / 2, ydim / 2, init_value);
		    
		    int new_shrunk_pixel_min = new_shrunk_pixel[0];
		    int new_shrunk_pixel_max = new_shrunk_pixel[0];
		   
		    for(int i = 0; i < new_shrunk_pixel.length; i++)
		    {
		    	if(new_shrunk_pixel[i] > new_shrunk_pixel_max)
		    		new_shrunk_pixel_max = new_shrunk_pixel[i];
		    	if(new_shrunk_pixel[i] < new_shrunk_pixel_min)
		    		new_shrunk_pixel_min = new_shrunk_pixel[i];
		    }
		    
		    int range_of_pixel_values = new_shrunk_pixel_max - new_shrunk_pixel_min + 1;
		    
		    
		    System.out.println("The pixel min is " + new_shrunk_pixel_min);
		    System.out.println("The pixel max is " + new_shrunk_pixel_max);
		    System.out.println("The range is     " + range_of_pixel_values);
		    System.out.println();
		    
		    /*
		    System.out.println("The low value in the processed green channel is " + green_min);
		    System.out.println("The high value in the processed green channel is " + green_max);
		    for(int i = 0; i < size; i++)
            {
                pixel[i] = 0;
                pixel[i] |= 255 << 24;
                pixel[i] |= red[i] << 16;
                pixel[i] |= green[i] << 8;
                pixel[i] |= blue[i];
            }
		    
		    for(int i = 0; i < xdim; i++)
		    {
		    	for(int j = 0; j < ydim; j++)
		    	{
		    		image.setRGB(i, j, pixel[j * xdim + i]);
		    	}
		    }
		    
		    */
		    image_canvas.repaint();
		    //System.out.println("Processed image.");
		    System.out.println();
		}
	}
	
	class ReloadHandler implements ActionListener
	{
		int[] pixel;
		
		ReloadHandler()
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
	        }	
		}
		
		public void actionPerformed(ActionEvent event)
		{
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
	        }	
			for(int i = 0; i < xdim; i++)
		    {
		    	for(int j = 0; j < ydim; j++)
		    	{
		    		image.setRGB(i, j, pixel[j * xdim + i]);
		    	}
		    	
		    } 
			System.out.println("Reloaded original image.");
			image_canvas.repaint();
		}
	}
}