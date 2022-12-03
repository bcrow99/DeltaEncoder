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

public class ExpandTester
{
	BufferedImage original_image;
	BufferedImage image;
	JMenuItem     apply_item;
	JDialog       shift_dialog;
	JTextField    shift_value;
	ImageCanvas   image_canvas;
	
	int           xdim, ydim;
	String        filename;
	
	byte[]        pixel_strings;
	byte[]        delta_strings;
	
	byte[]        compressed_pixel_strings;
	byte[]        compressed_delta_strings;
	
	byte[]        zipped_pixel_strings;
	byte[]        zipped_delta_strings;
	
	int [] original_pixel;
	int [] pixel;
	int [] red;
	int [] green;
	int [] blue;
	int [] blue_green;
	int [] red_green;
	int [] zip_data;
	
	int pixel_shift  = 3;
	
	
	
	public static void main(String[] args)
	{
		if (args.length != 1)
		{
			System.out.println("Usage: java ExpandTester <filename>");
			System.exit(0);
		}
		String    prefix    = new String("C:/Users/Brian Crowley/Desktop/");
		String    filename  = new String(args[0]);
		String java_version = System.getProperty("java.version");
		
		String os = System.getProperty("os.name");
		String os_version = System.getProperty("os.version");
		String machine = System.getProperty("os.arch");
		System.out.println("Current java version is " + java_version);
		System.out.println("Current os is " + os + " " + os_version + " on " + machine);
		System.out.println("Image file is " + filename);
		ExpandTester processor = new ExpandTester(prefix + filename);
	}

	public ExpandTester(String _filename)
	{
		filename = _filename;
		try
		{
			File file = new File(filename);
			original_image = ImageIO.read(file);
			int raster_type = original_image.getType();
			ColorModel color_model = original_image.getColorModel();
			int number_of_channels = color_model.getNumColorComponents();
			int number_of_bits = color_model.getPixelSize();
			xdim = original_image.getWidth();
			ydim = original_image.getHeight();
			System.out.println("Xdim is " + xdim + " and ydim is " + ydim);
		    System.out.println("Number of pixel bits is " + (xdim * ydim * 8));
			
		    original_pixel = new int[xdim * ydim];
			pixel          = new int[xdim * ydim];
			red            = new int[xdim * ydim];
		    green          = new int[xdim * ydim];
		    blue           = new int[xdim * ydim];
		  
			pixel_strings        = new byte[xdim * ydim * 15];
			delta_strings        = new byte[xdim * ydim * 2];
		
			zipped_pixel_strings = new byte[xdim * ydim * 15];
			zipped_delta_strings = new byte[xdim * ydim * 2];
			
			
			
			System.out.println();
			
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
               
			    JFrame frame = new JFrame("Delta Encoder");
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
		ApplyHandler()
		{
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
			System.out.println("Pixel shift is " + pixel_shift);
		    int  size = xdim * ydim;
		    for(int i = 0; i < size; i++)
		    {
		    	red[i]   = (original_pixel[i] >> 16) & 0xff;
		    	green[i] = (original_pixel[i] >> 8) & 0xff; 
                blue[i]  =  original_pixel[i] & 0xff; 
		    }
		    
		    int green_min = Integer.MAX_VALUE;
		    int green_max = Integer.MIN_VALUE;
		    for(int i = 0; i < size; i++)
		    {
		    	if(green[i] < green_min)
	            	green_min = green[i];
		    	if(green[i] > green_max)
            	green_max = green[i];
		    }
		    
		    System.out.println("Green min is " + green_min);
		    System.out.println("Green max is " + green_max);
		    
		    
		    int [] green_shrink = DeltaMapper.shrink4(green, xdim, ydim);
		    int [] green_expand = DeltaMapper.expand(green_shrink, xdim / 2, ydim / 2);
		    int [] green_dilate = DeltaMapper.dilate(green_expand, xdim - 1, ydim - 1);
		    
		    int green_shrink_min = Integer.MAX_VALUE;
		    int green_shrink_max = Integer.MIN_VALUE;
		    for(int i = 0; i < size / 4; i++)
		    {
		    	if(green_shrink_min > green_shrink[i])
		    		green_shrink_min = green_shrink[i];
		    	if(green_shrink_max < green_shrink[i])
		    		green_shrink_max = green_shrink[i];
		    }
		    
		    System.out.println("Green shrink min is " + green_shrink_min);
		    System.out.println("Green shrink max is " + green_shrink_max);
		    
		    int green_expand_min = Integer.MAX_VALUE;
		    int green_expand_max = Integer.MIN_VALUE;
		    for(int i = 0; i < (xdim - 1) * (ydim - 1); i++)
		    {
		    	if(green_expand_min > green_expand[i])
		    		green_expand_min = green_expand[i];
		    	if(green_expand_max < green_expand[i])
		    		green_expand_max = green_expand[i];
		    }
		    
		    System.out.println("Green expand min is " + green_expand_min);
		    System.out.println("Green expand max is " + green_expand_max);
		    
		    int green_dilate_min = Integer.MAX_VALUE;
		    int green_dilate_max = Integer.MIN_VALUE;
		    for(int i = 0; i < xdim * ydim; i++)
		    {
		    	if(green_dilate_min > green_dilate[i])
		    		green_dilate_min = green_dilate[i];
		    	if(green_dilate_max < green_dilate[i])
		    		green_dilate_max = green_dilate[i];
		    }
		    
		    System.out.println("Green dilate min is " + green_dilate_min);
		    System.out.println("Green dilate max is " + green_dilate_max);
		    
		    
		    
		    
		    //blue_green = DeltaMapper.getDifference(blue, green_dilate);
		    //red_green  = DeltaMapper.getDifference(red, green_dilate);
		    
		    blue_green = DeltaMapper.getDifference(blue, green);
		    red_green  = DeltaMapper.getDifference(red, green);
		    
		    for(int i = 0; i < size; i++)
		    {
		    	//green[i] = green_dilate[i];
		    	green[i] >>= pixel_shift;
		    }
		    
		    int blue_green_min = 0;
		    for(int i = 0; i < size; i++)
		    	if(blue_green[i] < blue_green_min)
	            	blue_green_min = blue_green[i];
		    for(int i = 0; i < size; i++)
		    {
		    	blue_green[i] -= blue_green_min;
		    	//blue_green[i] >>= pixel_shift;
		    }
		    
		    int red_green_min = 0;
		    for(int i = 0; i < size; i++)
		    	if(red_green[i] < red_green_min)
		    		red_green_min = red_green[i];
		    for(int i = 0; i < size; i++)
		    {
		    	red_green[i] -= red_green_min;
		    	//red_green[i] >>= pixel_shift;
		    }
		    
		    
		    int init_value           = green[0];
		    int[] delta              = DeltaMapper.getDeltasFromValues(green, xdim, ydim, init_value);
		    ArrayList histogram_list = DeltaMapper.getHistogram(delta);
		    int delta_min            = (int)histogram_list.get(0);
		    int [] histogram         = (int[])histogram_list.get(1);
		    int delta_random_lut[]   = DeltaMapper.getRandomTable(histogram); 
		    for(int i = 0; i < delta.length; i++)
		    	delta[i] -= delta_min;
		    int delta_length = 0;
		    delta_length  = DeltaMapper.packStrings2(delta, delta_random_lut, delta_strings);
		    
		    int string_length = delta_length / 8;
		    if(delta_length % 8 != 0)
		    	string_length++;
		    byte [] strings = new byte[string_length];
		    for(int i = 0; i < string_length; i++)
		        strings[i] = delta_strings[i];	
		     
		    int zipped_delta_length = 0;
		    try
		    {
		    	Deflater deflater = new Deflater();
		    	deflater.setInput(strings);
		    	deflater.finish();
		    	zipped_delta_length = deflater.deflate(zipped_delta_strings);
		    	deflater.end();
		    }
		    catch(Exception e)
		    {
		    	System.out.println(e.toString());
		    }
		   
		    int number_of_ints = DeltaMapper.unpackStrings2(delta_strings, delta_random_lut, delta);
		    for(int i = 0; i < delta.length; i++)
		    	delta[i] += delta_min;
		   
            int[] new_green = DeltaMapper.getValuesFromDeltas(delta, xdim , ydim, init_value);
            for(int i = 0; i < new_green.length; i++)
            {
            	new_green[i] <<= pixel_shift;
            }
            
		    for(int i = 0; i < size; i++)
            {
                pixel[i] = 0;
                pixel[i] |= 255 << 24;
                pixel[i] |= red[i] << 16;
                pixel[i] |= new_green[i] << 8;
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
		     
		    double packed_delta_bits_ratio = delta_length;
		    packed_delta_bits_ratio       /= xdim * ydim * 8;
		    
		
		    double zipped_delta_ratio = zipped_delta_length;
		    zipped_delta_ratio /= xdim * ydim;
		    
		    System.out.println();
		    
		    System.out.println("Green:");
		 
		    
		    System.out.println("The ratio of packed delta bits to pixel bits is " + String.format("%.4f", packed_delta_bits_ratio));
		
		    System.out.println("The ratio of zipped delta bits to pixel bits is " + String.format("%.4f", zipped_delta_ratio));
		   
		   
		    init_value       = blue_green[0];
		    delta            = DeltaMapper.getDeltasFromValues(blue_green, xdim, ydim, init_value);
		    histogram_list   = DeltaMapper.getHistogram(delta);
		    delta_min        = (int)histogram_list.get(0);
		    histogram        = (int[])histogram_list.get(1);
		    delta_random_lut = DeltaMapper.getRandomTable(histogram); 
		    for(int i = 0; i < delta.length; i++)
		    	delta[i] -= delta_min;
		    delta_length  = DeltaMapper.packStrings2(delta, delta_random_lut, delta_strings);
		    
		    
            zipped_delta_length = 0;
            string_length = delta_length / 8;
		    if(delta_length % 8 != 0)
		    	string_length++;
		    strings = new byte[string_length];
		    for(int i = 0; i < string_length; i++)
		        strings[i] = delta_strings[i];	
		     
		  
		    try
		    {
		    	Deflater deflater = new Deflater();
		    	deflater.setInput(strings);
		    	deflater.finish();
		    	zipped_delta_length = deflater.deflate(zipped_delta_strings);
		    	deflater.end();
		    }
		    catch(Exception e)
		    {
		    	System.out.println(e.toString());
		    }
		
		    packed_delta_bits_ratio = delta_length;
		    packed_delta_bits_ratio /= xdim * ydim * 8;
		  
		    zipped_delta_ratio = zipped_delta_length;
		    zipped_delta_ratio /= xdim * ydim;
		    
		   
		    System.out.println("Blue-Green:");
		    
		    System.out.println("The ratio of packed delta bits to pixel bits is " + String.format("%.4f", packed_delta_bits_ratio));
		    System.out.println("The ratio of zipped delta bits to pixel bits is " + String.format("%.4f", zipped_delta_ratio));
		    
		    
		    init_value       = red_green[0];
		    delta            = DeltaMapper.getDeltasFromValues(red_green, xdim, ydim, init_value);
		    histogram_list   = DeltaMapper.getHistogram(delta);
		    delta_min        = (int)histogram_list.get(0);
		    histogram        = (int[])histogram_list.get(1);
		    delta_random_lut = DeltaMapper.getRandomTable(histogram); 
		    for(int i = 0; i < delta.length; i++)
		    	delta[i] -= delta_min;
		    delta_length  = DeltaMapper.packStrings2(delta, delta_random_lut, delta_strings);
		    
		    
            zipped_delta_length = 0;
            string_length = delta_length / 8;
		    if(delta_length % 8 != 0)
		    	string_length++;
		    strings = new byte[string_length];
		    for(int i = 0; i < string_length; i++)
		        strings[i] = delta_strings[i];	
		     
		  
		    try
		    {
		    	Deflater deflater = new Deflater();
		    	deflater.setInput(strings);
		    	deflater.finish();
		    	zipped_delta_length = deflater.deflate(zipped_delta_strings);
		    	deflater.end();
		    }
		    catch(Exception e)
		    {
		    	System.out.println(e.toString());
		    }
		
		    packed_delta_bits_ratio = delta_length;
		    packed_delta_bits_ratio /= xdim * ydim * 8;
		  
		    zipped_delta_ratio = zipped_delta_length;
		    zipped_delta_ratio /= xdim * ydim;
		    
		   
		    System.out.println("Red-Green:");
		    
		    System.out.println("The ratio of packed delta bits to pixel bits is " + String.format("%.4f", packed_delta_bits_ratio));
		    System.out.println("The ratio of zipped delta bits to pixel bits is " + String.format("%.4f", zipped_delta_ratio));
		    System.out.println();
		}
	}
	
	
	
	class ReloadHandler implements ActionListener
	{
		ReloadHandler()
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