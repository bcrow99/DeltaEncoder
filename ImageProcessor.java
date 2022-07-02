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

public class ImageProcessor
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
			System.out.println("Usage: java ImageProcessor <filename>");
			System.exit(0);
		}
		String         prefix    = new String("C:/Users/Brian Crowley/Desktop/");
		String         filename  = new String(args[0]);
		ImageProcessor processor = new ImageProcessor(prefix + filename);
	}

	public ImageProcessor(String filename)
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
			System.out.println("Xdim is " + xdim + " and ydim is " + ydim);
			
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
               
			   
			    JFrame frame = new JFrame("Image Processor");
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
		
		double[] green_double;
		double[] scratch_double;
		double[] green_contract;
		double[] green_expand;
		
		ApplyHandler()
		{
			original_pixel = new int[xdim * ydim];
			pixel          = new int[xdim * ydim];
			red            = new int[xdim * ydim];
		    green          = new int[xdim * ydim];
		    blue           = new int[xdim * ydim];
		    scratch        = new int[xdim * ydim];
		    error          = new int[xdim * ydim];
		    
		    green_double   = new double[xdim * ydim];
		    
		    
		    
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
		    
		    int green_max = -1;
		    int green_min = 256;
		    for(int i = 0; i < size; i++)
		    {
		    	if(green[i] > green_max)
		    		green_max = green[i];
		    	if(green[i] < green_min)
		    		green_min = green[i];
		    	green_double[i] = green[i];
		    }
		    
		    System.out.println("The low value in the green channel is " + green_min);
		    System.out.println("The high value in the green channel is " + green_max);
			
		   
		    
		    green_contract = DeltaMapper.shrink4(green_double, xdim, ydim);
		    green_expand   = DeltaMapper.expand4(green_contract, xdim / 2, ydim / 2);
		    //green_expand   = DeltaMapper.expandGradient(green_contract, xdim - 1, ydim - 1);
		    //green_expand   = DeltaMapper.expandAverage(green_contract, xdim - 1, ydim - 1);
		    for(int i = 0; i < size; i++)
		    {
		    	scratch[i] = (int)(green_expand[i] + .5);
		    }
		    
		    int scratch_max = -1;
		    int scratch_min = 256;
		    
		    size = xdim * ydim;
		    for(int i = 0; i < size; i++)
		    {
		    	if(scratch[i] > scratch_max)
		    		scratch_max = scratch[i];
		    	if(scratch[i] < scratch_min)
		    		scratch_min = scratch[i];
		    	/*
		    	if(scratch[i] < 0)
		    		scratch[i] = 0;
		    	if(scratch[i] > 255)
		    		scratch[i] = 255;
		    	*/
		    }
		    System.out.println("The low value in the processed green channel is " + scratch_min);
		    System.out.println("The high value in the processed green channel is " + scratch_max);
		    
		    int total_error = 0;
		    
		    double[] error_double = new double[size];
		    for(int i = 0; i < size; i++)
		    {
		    	error_double[i] = green[i] - scratch[i];
		    	//error_double[i] = scratch[i] - green[i];
		    	total_error    += Math.abs(error_double[i]);
		    	//total_error += error[i];
		    }
		    System.out.println("Total error is " + total_error);
		    
		    double[] shrunk_green_double = DeltaMapper.shrink4(green_double, xdim, ydim, error_double);
		    green_expand   = DeltaMapper.expand4(shrunk_green_double, xdim / 2, ydim /2);
		    
		    for(int i = 0; i < size; i++)
		    {
		    	scratch[i] = (int)(green_expand[i] + .5);
		    	if(scratch[i] < 0)
		    		scratch[i] = 0;
		    	if(scratch[i] > 255)
		    		scratch[i] = 255;
		    }
		   
		    total_error = 0;
		    for(int i = 0; i < size; i++)
		    {
		    	error[i] = green[i] - scratch[i];
		    	//total_error += error[i];
		    	total_error += Math.abs(error[i]);
		    	error_double[i] = error[i];
		    }
		    System.out.println("Total error shrinking with error is " + total_error);
		    
		    int shrunk_green[] = new int[size / 4];
		    for(int i = 0; i < size / 4; i++)
		    {
		    	shrunk_green[i] = (int) (shrunk_green_double[i] + .5);
		    }
		    
		    int[] delta = DeltaMapper.getDeltasFromValues(shrunk_green, xdim / 2, ydim / 2);
		    
		    int delta_min = delta[0];
		    int delta_max = delta[0];
		   
		    for(int i = 0; i < size / 4; i++)
		    {
		    	if(delta[i] > delta_max)
		    		delta_max = delta[i];
		    	if(delta[i] < delta_min)
		    		delta_min = delta[i];
		    }
		    for(int i = 0; i < size / 4; i++)
		    	delta[i] -= delta_min;
		    
		  
		    int number_of_different_values = delta_max - delta_min + 1;
			System.out.println("The number of different values in the shifted shrunken green delta channel is " + number_of_different_values);
		    System.out.println("The low value  is " + delta_min);
		    System.out.println("The high value is " + delta_max);
		    
		    byte [] bit_strings = new byte[5 * xdim * ydim];
		    int number_of_bits  = DeltaMapper.packStrings(delta, size / 4, number_of_different_values, bit_strings);
		    System.out.println("Number of bits is " + number_of_bits);
		    int number_of_bytes = DeltaMapper.unpackStrings(bit_strings, number_of_different_values, delta, size / 4);
		    System.out.println("Number of bytes is " + number_of_bytes);
		   
		    
		    for(int i = 0; i < size / 4; i++)
		    	delta[i] += delta_min;
		    int[] value = DeltaMapper.getValuesFromDeltas(delta, xdim / 2, ydim / 2);
		    
		    total_error = 0;
		    for(int i = 0; i < size / 4; i++)
		    {
		        int error = Math.abs(shrunk_green[i] - value[i]);
		        total_error += error;
		    }
		    System.out.println("Total error after deltas is " + total_error);
		    /*
		    
		    green_max = -1;
		    green_min = 256;
		    for(int i = 0; i < size / 4; i++)
		    {
		    	if(shrunk_green[i] > green_max)
		    		green_max = shrunk_green[i];
		    	if(shrunk_green[i] < green_min)
		    		green_min = shrunk_green[i];
		    }
		    
		    int [] delta = new int[size / 4];
		    int [] value = new int[size / 4];
		    
		    getDeltasFromValues(shrunk_green, delta, xdim / 2, ydim / 2);
		    getValuesFromDeltas(delta, value, xdim / 2, ydim / 2);
		    
		    total_error = 0;
		    for(int i = 0; i < size / 4; i++)
		    {
		        int error = Math.abs(shrunk_green[i] - value[i]);
		        total_error += error;
		    }
		    System.out.println("Total error after deltas is " + total_error);
		    
		    green_max = green_min = delta[0];
		    for(int i = 0; i < size / 4; i++)
		    {
		    	if(delta[i] > green_max)
		    		green_max = delta[i];
		    	if(delta[i] < green_min)
		    		green_min = delta[i];
		    }
		    
		    
		    System.out.println("The low value in the shrunken green delta channel is " + green_min);
		    System.out.println("The high value in the shrunken green delta channel is " + green_max);
		    
		    for(int i = 0; i < size / 4; i++)
            {
	            delta[i] -= green_min;
	            delta[i] >>= pixel_shift;   
            }
		  
		    	
		    
		    green_max = green_min = delta[0];
		    for(int i = 0; i < size / 4; i++)
		    {
		    	if(delta[i] > green_max)
		    		green_max = delta[i];
		    	if(delta[i] < green_min)
		    		green_min = delta[i];
		    }
		    
		    int number_of_different_values = green_max - green_min + 1;
			System.out.println("The number of different values in the shifted shrunken green delta channel is " + number_of_different_values);
			
		    
		    System.out.println("The low value in the shrunken shifted green delta channel is " + green_min);
		    System.out.println("The high value in the shrunken shifted green delta channel is " + green_max);
		    
		    byte [] bit_strings = new byte[5 * xdim * ydim];
		    int number_of_bits  = packStrings(delta, size / 4, number_of_different_values, bit_strings);
		    System.out.println("Number of bits is " + number_of_bits);
		    int number_of_bytes = unpackStrings(bit_strings, number_of_different_values, delta, size / 4);
		    System.out.println("Number of bytes is " + number_of_bytes);
		   
		    
		    total_error = 0;
		    for(int i = 0; i < size; i++)
		    {
		    	if(scratch[i] < 0)
		    		scratch[i] = 0;
		    	if(scratch[i] > 255)
		    		scratch[i] = 255;
		    	error[i] = Math.abs(green[i] - scratch[i]);
		    	total_error += error[i];
		    	green[i] = scratch[i];
		    }
		    System.out.println("Total error after adjusting for error is " + total_error);
		    
		    int[] blue_green = new int[xdim * ydim];
		    int[] red_green  = new int[xdim * ydim];
		    for(int i = 0; i < size; i++)
		    {
		    	blue_green[i] = green[i]  - blue[i];
		    	blue_green[i] >>= pixel_shift;
		    	red_green[i]  = green[i] - red[i];
		    	red_green[i] >>= pixel_shift;
		    }
		    
		    int [] blue_green_delta = new int[xdim * ydim];
		    int [] red_green_delta  = new int[xdim * ydim];
		    getDeltasFromValues(blue_green, blue_green_delta, xdim, ydim);
		    getDeltasFromValues(red_green, red_green_delta, xdim, ydim);
		    green_max = green_min = blue_green_delta[0];
		    for(int i = 0; i < size; i++)
		    {
		    	if(blue_green_delta[i] > green_max)
		    		green_max = blue_green_delta[i];
		    	if(blue_green_delta[i] < green_min)
		    		green_min = blue_green_delta[i];
		    }
		    
		    number_of_different_values = green_max - green_min + 1;
			System.out.println("The number of different values in the blue green delta channel is " + number_of_different_values);
			for(int i = 0; i < size; i++)
				blue_green_delta[i] -= green_min;	
		    
		    number_of_bits  = packStrings(blue_green_delta, size, number_of_different_values, bit_strings);
		    
		    System.out.println("Number of bits in strings is " + number_of_bits);
		   
		    number_of_bytes = unpackStrings(bit_strings, number_of_different_values, red_green_delta, size);
		    System.out.println("Number of bytes unpacked is " + number_of_bytes);
		    
		    total_error = 0;
		    for(int i = 0; i < size; i++)
		    {
		    	int error = blue_green_delta[i] - red_green_delta[i];
		    	total_error += error;
		    	
		    }
		    
		    System.out.println("Total error after packing and unpacking strings is " + total_error);
		    
		    byte[] compressed_bit_strings = new byte[5 * size];
		    byte[]decompressed_bit_strings = new byte[5 * size];
		    int compressed_number_of_bits = compressStrings(bit_strings, number_of_bits, compressed_bit_strings);
		    System.out.println("Number of bits in compressed strings is " + compressed_number_of_bits);
		    
		    int number_of_decompressed_bits = decompressStrings(compressed_bit_strings, compressed_number_of_bits, decompressed_bit_strings);
		    
		    // Extra bits, maybe from the way the iterations number is being set, but correct amount of data is uncorrupted.
		    System.out.println("Number of bits in decompressed strings is " + number_of_decompressed_bits);
		    number_of_bytes = compressed_number_of_bits / 8;
		    if(compressed_number_of_bits % 8 != 0)
		    	number_of_bytes++;
		    total_error = 0;
		    for(int i = 0; i < number_of_bytes; i++)
		    {
		    	int error = decompressed_bit_strings[i] - bit_strings[i];
		    	total_error += Math.abs(error);
		    }
		    
		    System.out.println("Total error after compressing and decompressing bits is " + total_error);
		    
		    
		    green_max = -1;
		    green_min = 256;
		    for(int i = 0; i < size; i++)
		    {
		    	if(green[i] > green_max)
		    		green_max = green[i];
		    	if(green[i] < green_min)
		    		green_min = green[i];
		    }
		    
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
		    System.out.println("Processed image.");
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

