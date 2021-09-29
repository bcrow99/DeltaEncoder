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
			    
			    
			    /*
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
               */
			  
			    image = new BufferedImage(xdim, ydim, BufferedImage.TYPE_INT_RGB);
			    
			  
			    for(int i = 0; i < xdim; i++)
			    {
			    	for(int j = 0; j < ydim; j++)
			    	{
			    		image.setRGB(i, j, pixel[j * xdim + i]);
			    	}
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
		
		
		ApplyHandler()
		{
			original_pixel = new int[xdim * ydim];
			pixel          = new int[xdim * ydim];
			red            = new int[xdim * ydim];
		    green          = new int[xdim * ydim];
		    blue           = new int[xdim * ydim];
		    scratch        = new int[xdim * ydim];
		    error          = new int[xdim * ydim];
		    
		    
		    
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
		    }
		    
		    System.out.println("The low value in the green channel is " + green_min);
		    System.out.println("The high value in the green channel is " + green_max);
			
		   
		    int [] shrunk_green = new int[size / 4];
		    int [] shrunk_error = new int[size / 4];
		    
		    
		    shrink(green, xdim, ydim, shrunk_green);
		    expand(shrunk_green, xdim / 2, ydim /2, scratch);
		    
		    
		    
		    
		    
		    int scratch_max = -1;
		    int scratch_min = 256;
		    for(int i = 0; i < size; i++)
		    {
		    	if(scratch[i] > scratch_max)
		    		scratch_max = scratch[i];
		    	if(scratch[i] < scratch_min)
		    		scratch_min = scratch[i];
		    }
		    //System.out.println("The low value in the processed green channel is " + scratch_min);
		    //System.out.println("The high value in the processed green channel is " + scratch_max);
		    
		    int total_error = 0;
		    for(int i = 0; i < size; i++)
		    {
		    	error[i] = green[i] - scratch[i];
		    	total_error += error[i];
		    }
		    System.out.println("Total error is " + total_error);
		    
		    shrink(green, xdim, ydim, shrunk_green, error);
		    expand(shrunk_green, xdim / 2, ydim /2, scratch);
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
		    	error[i] = green[i] - scratch[i];
		    	total_error += error[i];
		    	green[i] = scratch[i];
		    }
		    System.out.println("Total error after adjusting for error is " + total_error);
		     
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
	
    public void expand(int src[], int xdim, int ydim, int dst[])
    {
        int       i, j, x, y, new_xdim;
        new_xdim  = 2 * xdim;
        i         = 0; 
        j         = 0;

        // Process the top row.
        dst[j]                = src[i];
        dst[j + 1]            = (1333 * src[i] + 894 * src[i + 1]) / 2227;
        dst[j + new_xdim]     = (1333 * src[i] + 894 * src[i + xdim]) / 2227;
        dst[j + new_xdim + 1] = (1000  * src[i] + 447 * src[i + 1] + 447 * src[i + xdim] + 333 * src[i + xdim + 1]) / 2227;
        i++; 
        j                    += 2;
        for(x = 1; x < xdim - 1; x++)
        {
            dst[j]                = (1333 * src[i] + 894 * src[i - 1]) / 2227; 
            dst[j + 1]            = (1333 * src[i] + 894 * src[i + 1]) / 2227; 
            dst[j + new_xdim]     = (1000 * src[i] + 447 * src[i - 1] + 447 * src[i + xdim] + 333 * src[i + xdim - 1]) / 2227; 
            dst[j + new_xdim + 1] = (1000  * src[i] + 447 * src[i + 1] + 447 * src[i + xdim] + 333 * src[i + xdim + 1]) / 2227; 
            j += 2;
            i++;
        }
        dst[j]                = (1333 * src[i] + 894 * src[i - 1]) / 2227;
        dst[j + 1]            = src[i];
        dst[j + new_xdim]     = (1000  * src[i] + 447 * src[i - 1] + 447 * src[i + xdim] + 333 * src[i + xdim - 1]) / 2227;
        dst[j + new_xdim + 1] = (1333 * src[i] + 894 * src[i + xdim]) / 2227;
        i++; 
        j                    += new_xdim + 2;

        // Process the middle section.
        for(y = 1; y < ydim - 1; y++)
        {
            dst[j]                = (1333 * src[i] + 894 * src[i - xdim]) / 2227;
            dst[j + 1]            = (1000  * src[i] + 447 * src[i - xdim] + 447 * src[i + 1] + 333 * src[i - xdim + 1]) / 2227;
            dst[j + new_xdim]     = (1333 * src[i] +  894 * src[i + xdim]) / 2227; 
            dst[j + new_xdim + 1] = (1000  * src[i] + 447 * src[i + 1] + 447 * src[i + xdim] + 333 * src[i + xdim + 1]) / 2227;
            i++;
            j += 2;
            for(x = 1; x < xdim - 1; x++)
            {
                dst[j]                = (1000  * src[i] + 447 * src[i - xdim] + 447 * src[i - 1] + 333 * src[i - xdim - 1]) / 2227;
                dst[j + 1]            = (1000 * src[i] + 447 * src[i - xdim] + 447 * src[i + 1] + 333 * src[i - xdim + 1]) / 2227;
                dst[j + new_xdim]     = (1000 * src[i] + 447 * src[i + xdim] + 447 * src[i - 1] + 333 * src[i + xdim - 1]) / 2227; 
                dst[j + new_xdim + 1] = (1000 * src[i] + 447 * src[i + 1] + 447 * src[i + xdim] + 333 * src[i + xdim + 1]) / 2227;
                i++;
                j += 2;
            }
            dst[j]                = (1000  * src[i] + 894 * src[i - xdim] + 447 * src[i - 1]) / 2227;
            dst[j + 1]            = (1333 * src[i] + 894 * src[i - xdim]) / 2227;
            dst[j + new_xdim]     = (1000  * src[i] + 447 * src[i - 1] + 447 * src[i + xdim] + 333 * src[i + xdim - 1]) / 2227;
            dst[j + new_xdim + 1] = (1333 * src[i] + 894 * src[i + xdim]) / 2227; 
            i++;
            j += new_xdim + 2;
        }

        // Process the bottom row.
        dst[j]                = (1333 * src[i] + 894 * src[i - xdim]) / 2227;
        dst[j + 1]            = (1000  * src[i] + 447 * src[i + 1] + 447 * src[i - xdim] + 333 * src[i - xdim + 1]) / 2227;
        dst[j + new_xdim]     = src[i];
        dst[j + new_xdim + 1] = (1333 * src[i] + 894 * src[i + 1]) / 2227;
        i++;
        j                    += 2;
        for(x = 1; x < xdim - 1; x++)
        {
            dst[j]                = (1000  * src[i] + 447 * src[i - 1] + 447 * src[i - xdim] + 333 * src[i - xdim - 1]) / 2227; 
            dst[j + 1]            = (1000  * src[i] + 447 * src[i + 1] + 447 * src[i - xdim] + 333 * src[i - xdim + 1]) / 2227; 
            dst[j + new_xdim]     = (1333 * src[i] + 894 * src[i - 1]) / 2227; 
            dst[j + new_xdim + 1] = (1333 * src[i] + 894 * src[i + 1]) / 2227; 
            i++;
            j += 2;
        }
        dst[j]                = (1000  * src[i] + 447 * src[i - 1] + 447 * src[i - xdim] + 333 * src[i - xdim - 1]) / 2227;
        dst[j + 1]            = (1333 * src[i] + 894 * src[i - xdim]) / 2227;
        dst[j + new_xdim]     = (1333 * src[i] + 894 * src[i - 1]) / 2227;
        dst[j + new_xdim + 1] = src[i];
    }

    public void shrink(int src[], int xdim, int ydim, int dst[])
    {
        int    i, j, k, r, c;
        int    sum;
    
        r = ydim;
        c = xdim;
        k = 0;
        if(xdim % 2 == 0)
        {
            for(i = 0; i < r - 1; i += 2)
            {
                for (j = 0; j < c - 1; j += 2)
                {
                    sum = (src[i *c + j] + src[i * c + j + 1] + src[(i + 1) * c + j] + src[(i + 1) * c + j + 1]) / 4;
                    dst[k++] = sum;
                }
            }
        }
        else  // Looks wrong.
        {
            for(i = 0; i < r - 1; i += 2)
            {
                for(j = 0; j < c - 1; j += 2)
                {
                    sum = (src[i * c+j] + src[i * c + j + 1] + src[(i + 1) * c + j] + src[(i + 1) * c + j + 1]) / 4;
                    dst[k++] = sum;
                }
                k++;
            }
        }
    }
    
    public void shrink(int src[], int xdim, int ydim, int dst[], int error[])
    {
        int    i, j, k, r, c;
        int    sum;
    
        r = ydim;
        c = xdim;
        k = 0;
        if(xdim % 2 == 0)
        {
            for(i = 0; i < r - 1; i += 2)
            {
                for (j = 0; j < c - 1; j += 2)
                {
                    sum = (src[i * c + j] + error[i * c + j] + src[i * c + j + 1] + error[i * c + j + 1] 
                    		+ src[(i + 1) * c + j] + error[(i + 1) * c + j] + src[(i + 1) * c + j + 1] + error[(i + 1) * c + j + 1]) / 4;
                    dst[k++] = sum;
                }
            }
        }
        else // Looks wrong.
        {
            for(i = 0; i < r - 1; i += 2)
            {
                for(j = 0; j < c - 1; j += 2)
                {
                    sum = (src[i * c + j] + error[i * c + j] +  src[i * c + j + 1] + error[i * c + j + 1]
                    		+ src[(i + 1) * c + j] + error[(i + 1) * c + j] + src[(i + 1) * c + j + 1] + error[(i + 1) * c + j + 1]) / 4;
                    dst[k++] = sum;
                }
                k++;
            }
        }
    }
    
   
    
    public void getDeltasFromValues(int src[], int dst[], int xdim, int ydim)
    {
        int i, j, k;
        int current_value;
        int start_value;
        int delta_value;
    
        k = 0;
        start_value = 0;
        for(i = 0; i < ydim; i++)
        {
            delta_value  = src[k] - start_value;
            
            start_value += delta_value;
            dst[k]     = delta_value;
            k++;
            current_value = start_value; 
            for(j = 1; j < xdim; j++)
            {
                delta_value    = src[k]  - current_value;
                current_value += delta_value;
                dst[k]       = delta_value;
                k++;
            }
        }
    }

    public void getValuesFromDeltas(int src[], int dst[], int xdim, int ydim)
    {
        int current_value;
        int start_value;
        int i, j, k;
    
        k = 0;
        start_value = 0;
        for(i = 0; i < ydim; i++)
        {
            start_value  += src[k];
            current_value = start_value;
            dst[k] = current_value;
            k++;
            for(j = 1; j < xdim; j++)
            {
                current_value += src[k];
                dst[k]       = current_value;
                k++;
            }
        }
    }
    
    public int packStrings(int src[], int size, int number_of_different_values, byte dst[])
    {
        int i, j, k;
        int current_byte;
        byte current_bit, value; 
        byte next_bit; 
        int  index, second_index;
        int  number_of_bits;
        int inverse_table[], table[], mask[];
    
        inverse_table = new int[number_of_different_values]; 
        inverse_table[0] = number_of_different_values / 2;
        i = j = number_of_different_values / 2;
        k = 1;
        j++;
        i--;
        while(i >= 0)
        {
            inverse_table[k] = j;
            k++;
            j++;
            inverse_table[k] = i;
            k++;
            i--;
        }

        table = new int[number_of_different_values];
        for(i = 0; i < number_of_different_values; i++)
        {
            j        = inverse_table[i];
            table[j] = i;
        }
    
        mask  = new int[8];
        mask[0] = 1;
        for(i = 1; i < 8; i++)
        {
            mask[i] = mask[i - 1];
            mask[i] *= 2;
            mask[i]++;
        }
    
    
        current_bit = 0;
        current_byte = 0;
        dst[current_byte] = 0;
        for(i = 0; i < size; i++)
        {
            k     = src[i];
            index = table[k];
            if(index == 0)
            {
                current_bit++;
                if(current_bit == 8)
                    dst[++current_byte] = current_bit = 0;
            }
            else
            {
                next_bit = (byte)((current_bit + index + 1) % 8);
                if(index <= 7)
                {
                    dst[current_byte] |= (byte) (mask[index - 1] << current_bit);
                    if(next_bit <= current_bit)
                    {
                        dst[++current_byte] = 0;
                        if(next_bit != 0)
                            dst[current_byte] |= (byte)(mask[index - 1] >> (8 - current_bit));
                    }
                }
                else if(index > 7)
                {
                    dst[current_byte] |= (byte)(mask[7] << current_bit);
                    j = (index - 8) / 8;
                    for(k = 0; k < j; k++)
                        dst[++current_byte] = (byte)(mask[7]);
                    dst[++current_byte] = 0;
                    if(current_bit != 0)
                        dst[current_byte] |= (byte)(mask[7] >> (8 - current_bit));
    
                    if(index % 8 != 0)
                    {
                        second_index = index % 8 - 1;
                        dst[current_byte] |= (byte)(mask[second_index] << current_bit);
                        if(next_bit <= current_bit)
                        {
                            dst[++current_byte] = 0;
                            if(next_bit != 0)
                                dst[current_byte] |= (byte)(mask[second_index] >> (8 - current_bit));
                        }
                    }
                    else if(next_bit <= current_bit)
                            dst[++current_byte] = 0;
                }
                current_bit = next_bit;
            }
        }
        if(current_bit != 0)
            current_byte++;
        number_of_bits = current_byte * 8;
        if(current_bit != 0)
            number_of_bits -= 8 - current_bit;
        return(number_of_bits);
    }
    
    public int unpackStrings(byte src[], int number_of_different_values, int dst[], int size)
    {
        int  number_of_bytes_unpacked;
        int  current_src_byte, current_dst_byte;
        byte non_zero, mask, current_bit;
        int  index, current_length;
        int  table[];
        int  i, j, k;
    
        table = new int[number_of_different_values]; 
        table[0] = number_of_different_values / 2;
        i = j = number_of_different_values / 2;
        k = 1;
        j++;
        i--;
        while(i >= 0)
        {
            table[k] = j;
            k++;
            j++;
            table[k] = i;
            k++;
            i--;
        }
        current_length = 1;
        current_src_byte = 0;
        mask = 0x01;
        current_bit = 0;
        current_dst_byte = 0;
        while(current_dst_byte < size)
        {
            non_zero = (byte)(src[current_src_byte] & (byte)(mask << current_bit));
            if(non_zero != 0)
                current_length++;
            else
            {
                index = current_length - 1;
                dst[current_dst_byte++] = table[index];
                current_length = 1;
            }
            current_bit++;
            if(current_bit == 8)
            {
                current_bit = 0;
                current_src_byte++;
            }
        }
        if(current_bit == 0)
            number_of_bytes_unpacked = current_src_byte;
        else
            number_of_bytes_unpacked = current_src_byte + 1;
        return(number_of_bytes_unpacked);
    }

    
    
    
    
    
    
    
    
    
    
    
    
    
    
}

