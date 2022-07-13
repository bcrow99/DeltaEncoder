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

public class StringTester
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
			System.out.println("Usage: java StringTester <filename>");
			System.exit(0);
		}
		String      prefix    = new String("C:/Users/Brian Crowley/Desktop/");
		String      filename  = new String(args[0]);
		StringTester processor = new StringTester(prefix + filename);
	}

	public StringTester(String filename)
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
	
		double[] green_double;
		double[] scratch_double;
		double[] green_shrink;
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
		    	green[i] >>= pixel_shift;
		    	if(green[i] > green_max)
		    		green_max = green[i];
		    	if(green[i] < green_min)
		    		green_min = green[i];
		    	green_double[i] = green[i];
		    }
		    
		   
			
		   
		    
		    green_shrink = DeltaMapper.shrink4(green_double, xdim, ydim);
		    
		    double green_max_double = green_shrink[0];
		    double green_min_double = green_shrink[0];
		    
		    for(int i = 0; i < size / 4; i++)
		    { 
		    	if(green_shrink[i] > green_max_double)
		    		green_max_double = green_shrink[i];
		    	if(green_shrink[i] < green_min_double)
		    		green_min_double = green_shrink[i];
		    }
		
		    System.out.println();
		    System.out.println("The low value in the image is     " + green_min);
		    System.out.println("The high value in the image is    " + green_max);
		    System.out.println("The low value after shrinking     " + green_min_double);
		    System.out.println("The high value after shrinking is " + green_max_double);
		    
		    green_expand = DeltaMapper.expand4(green_shrink, xdim / 2, ydim / 2);
		  
		    
		    green_max_double = green_expand[0];
		    green_min_double = green_expand[0];
		    for(int i = 0; i < size ; i++)
		    { 
		    	if(green_expand[i] > green_max_double)
		    		green_max_double = green_expand[i];
		    	if(green_expand[i] < green_min_double)
		    		green_min_double = green_expand[i];
		    }
		    System.out.println("The low value after expanding     " + green_min_double);
		    System.out.println("The high value after expanding is " + green_max_double);
		    System.out.println();
		    
		    
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
		    }
		    
		    System.out.println("The low int value after expanding     " + scratch_min);
		    System.out.println("The high int value after expanding is " + scratch_max);
		    
		    int total_error = 0;
		    int biased_error = 0;
		    
		    double[] error_double = new double[size];
		    for(int i = 0; i < size; i++)
		    {
		    	error_double[i] = green[i] - scratch[i];
		    	total_error    += Math.abs(error_double[i]);
		    	biased_error += error[i];
		    }
		    System.out.println("Total error is " + total_error);
		    
		    System.out.println("Total error shrinking and expanding is " + total_error);
		    System.out.println("Biased error shrinking and expanding is " + biased_error);
		    System.out.println();
		    
		    
		    int shrunk_green[] = new int[size / 4];
		    for(int i = 0; i < size / 4; i++)
		    {
		    	shrunk_green[i] = (int) (green_shrink[i] + .5);
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
		    int number_of_different_delta_values = delta_max - delta_min + 1;
		    System.out.println("Range of values in deltas is " + number_of_different_delta_values);
		    System.out.println("The low value is  " + delta_min);
		    System.out.println("The high value is " + delta_max);
		    
		    
		    int[] new_shrunk_green = DeltaMapper.getValuesFromDeltas(delta, xdim / 2, ydim / 2);
		    
		    total_error = 0;
		    
		    for(int i = 0; i < new_shrunk_green.length; i++)
		    {
		    	total_error += Math.abs(new_shrunk_green[i] - shrunk_green[i]);
		    }
		    System.out.println("Difference between orignal image and delta encoded image is " + total_error);
		   
		    green_min = 256;
		    green_max = -1;
		    for(int i = 0; i < new_shrunk_green.length; i++)
		    {
		    	if(new_shrunk_green[i] > green_max)
		    		green_max = new_shrunk_green[i];
		    	if(new_shrunk_green[i] < green_min)
		    		green_min = new_shrunk_green[i];
		    }
		    int range_of_pixel_values = green_max - green_min + 1;
		    System.out.println("Range of pixel values is               " + range_of_pixel_values);
		    System.out.println("The low value after delta encoding is  " + green_min);
		    System.out.println("The high value after delta encoding is " + green_max);
		    System.out.println();
		    
		    
		    int histogram[] = new int[number_of_different_delta_values];
		    for(int i = 0; i < number_of_different_delta_values; i++)
		    	histogram[i] = 0;
		    for(int i = 0; i < delta.length; i++)
		    {
		    	int j = delta[i] - delta_min;
		    	histogram[j]++;
		    }
		    //for(int i = 0; i < number_of_different_values; i++)
		    //	System.out.println(i + " -> " + histogram[i]);
		    
		    
		    ArrayList key_list = new ArrayList();
		    Hashtable rank_table   = new Hashtable();
		    for(int i = 0; i < number_of_different_delta_values; i++)
		    {
		    	double key = histogram[i];
		    	while(rank_table.containsKey(key))
		    	{
		    		key +=.001;
		    	}
		    	rank_table.put(key, i);
		    	key_list.add(key);
		    }
		    Collections.sort(key_list);
		    
		    int random_lut[] = new int[number_of_different_delta_values];
		    int k     = -1;
		    //System.out.println("Sorted keys:");
		    for(int i = number_of_different_delta_values - 1; i >= 0; i--)
		    {
		    	double key = (double)key_list.get(i);
		    	int    j   = (int)rank_table.get(key);
		    	/*
		    	if(key >= 1)
		    	    random_lut[j]   = ++k;
		    	else
		    		random_lut[j]   = k;
		    	*/
		    	random_lut[j]   = ++k;
		    	//System.out.println("Key = " + key + ", value = " + j);
		    }
		    
		   /*
		    System.out.println("Random table:");
		    for(int i = 0; i < number_of_different_delta_values; i++)
		    {
		    	System.out.println(i + "-> " + random_lut[i]);
		    }
		    System.out.println();
		    */
		    
		    
		    int [] symmetric_lut = new int[number_of_different_delta_values];
		    int i = 0;
	        int j = 0;
	        
	        if(number_of_different_delta_values % 2 == 1)
	            j = number_of_different_delta_values / 2;
	        else
	        	j = number_of_different_delta_values / 2 - 1;
	        
	        symmetric_lut[j--] = i;
	        i += 2;
	        while(j >= 0)
	        {
	        	symmetric_lut[j--] = i;
	        	i += 2;
	        }
	        
	        if(number_of_different_delta_values % 2 == 1)
	            j = number_of_different_delta_values / 2 + 1;
	        else
	        	j = number_of_different_delta_values / 2;
	        
	        i = 1;
	        symmetric_lut[j++] = i;
	        i += 2;
	        while(j < number_of_different_delta_values)
	        {
	        	symmetric_lut[j++] = i;
	        	i += 2;
	        }
	       
	        
		    /*
		    System.out.println("Symmetric table:");
		    for(i = 0; i < number_of_different_delta_values; i++)
		    {
		    	System.out.println(i + "-> " + symmetric_lut[i]);
		    }
		    System.out.println();
		    */
	        
	        
		    double modal_key   = (double)key_list.get(number_of_different_delta_values - 1);
		    int    modal_value = (int)rank_table.get(modal_key);
		    int modal_lut[]    = new int[number_of_different_delta_values];
		  
		    
		    int lower_value = modal_value;
		    int upper_value = modal_value + 1;
		    
		    int index = 0;
		    modal_lut[lower_value] = index++;
		    if(upper_value < number_of_different_delta_values)
		    	modal_lut[upper_value] = index++;
		    else
		    {
		    	lower_value--;
		    	modal_lut[lower_value] = index++;
		    }
		    
		    for(i = 2; i < number_of_different_delta_values; i += 2)
		    {
		        if(lower_value > 0)	
		        {
		        	lower_value--;
		        	modal_lut[lower_value] = index++;
		        	upper_value++;
		        	if(upper_value < number_of_different_delta_values)
		        		modal_lut[upper_value] = index++;
		        	else
		        	{
		        		if(lower_value > 0)
		        		{
		        		    lower_value--;
		        		    modal_lut[lower_value] = index++;   
		        		}
		        	}
		        }
		        else
		        {
		        	upper_value++;
		        	modal_lut[upper_value] = index++;
		        	if(upper_value < number_of_different_delta_values - 1)
		        	{
		        		upper_value++;
		                modal_lut[upper_value] = index++;
		        	}
		        }
		    }
		    
		    if(number_of_different_delta_values % 2 == 1)
		    {
		    	if(lower_value > 0)
		    		modal_lut[0] = 0;	
		    	else
		    		modal_lut[number_of_different_delta_values - 1] = number_of_different_delta_values - 1;
		    }
	        
		    /*
		    System.out.println("Modal table:");
		    for(int i = 0; i < number_of_different_delta_values; i++)
		    {
		    	System.out.println(i + "-> " + modal_lut[i]);
		    }
		    System.out.println();
		    */
		    
			//System.out.println("The number of different values in the shifted shrunken green delta channel is " + number_of_different_values);
		    //System.out.println("The low value  is " + delta_min);
		    //System.out.println("The high value is " + delta_max);
		    
		    
		    for(i = 0; i < delta.length; i++)
		    	delta[i] -= delta_min;
		    byte [] bit_strings = new byte[5 * xdim * ydim];
		    
		    //System.out.println("The number of bits in a byte is " + Byte.SIZE);
		  
		    /*
		    byte mask = 1;
		   
		    
		    for(i = 0; i < 7; i++)
		    {
		    	mask <<= 1;
		    	System.out.println("Mask = " + mask);
		    }
		    System.out.println();
		    
		    for(i = 0; i < 7; i++)
		    {
		    	mask >>= 1;
		    	System.out.println("Mask = " + mask);
		    }
		    System.out.println();
		    
		    mask = -1;
		    for(i = 0; i < 7; i++)
		    {
		    	mask <<= 1;
		    	System.out.println("Mask = " + mask);
		    }
		    System.out.println();
		    
		    }
		    */
		    
		    //byte mask = (byte)0b11111110;
		    //int mask = 0b11111110;
		    
		    //System.out.println("Mask = " + mask);
		
		    //System.out.println();
		    

		    int number_of_bits  = DeltaMapper.packStrings2(delta, random_lut, bit_strings);
	
		    System.out.println("Number of bits in original image is " + (xdim * ydim * 2));
		    System.out.println("Number of bits in unary strings is  " + number_of_bits);
		    
		    /*
		    byte [] compressed_bit_strings = new byte[5 * xdim * ydim];
		    int compressed_number_of_bits = DeltaMapper.compressZeroBits(bit_strings, number_of_bits, compressed_bit_strings);
		    int decompressed_number_of_bits = DeltaMapper.decompressZeroBits(compressed_bit_strings, compressed_number_of_bits, bit_strings);
		    System.out.println("Number of compressed bits is        " + compressed_number_of_bits);
		    System.out.println("Number of decompressed bits is      " + decompressed_number_of_bits);
		    */
		    
		   
		    int number_of_ints = DeltaMapper.unpackStrings2(bit_strings, random_lut, delta);
		  
		    System.out.println("Number of ints unpacked is " + number_of_ints);
		 
		   
		    for(i = 0; i < size / 4; i++)
		    	delta[i] += delta_min;
		  
		    new_shrunk_green = DeltaMapper.getValuesFromDeltas(delta, xdim / 2, ydim / 2);
		    
		    green_min = 256;
		    green_max = -1;
		    total_error = 0;
		    for(i = 0; i < new_shrunk_green.length; i++)
		    {
		    	if(new_shrunk_green[i] > green_max)
		    		green_max = new_shrunk_green[i];
		    	if(new_shrunk_green[i] < green_min)
		    		green_min = new_shrunk_green[i];
		    	total_error += Math.abs(new_shrunk_green[i] - shrunk_green[i]);
		    }
		    range_of_pixel_values = green_max - green_min + 1;
		    System.out.println("Range of pixel values is               " + range_of_pixel_values);
		    System.out.println("The low value after string encoding is  " + green_min);
		    System.out.println("The high value after string encoding is " + green_max);
		    System.out.println("Total error is    " + total_error);
		    
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