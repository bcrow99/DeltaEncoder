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

public class ZipTester
{
	BufferedImage original_image;
	BufferedImage image;
	JMenuItem     apply_item;
	JDialog       shift_dialog;
	JTextField    shift_value;
	ImageCanvas   image_canvas;
	
	int           xdim, ydim;
	String        filename;
	
	byte[]        pixel_bit_strings;
	byte[]        delta_bit_strings;
	
	byte[]        odd_compressed_pixel_strings;
	byte[]        even_compressed_pixel_strings;
	byte[]        compressed_pixel_strings;
	
	byte[]        odd_compressed_delta_strings;
	byte[]        even_compressed_delta_strings;
	byte[]        compressed_delta_strings;
	
	int           pixel_shift  = 3;
	int           pixel_length = 0;
	int           delta_length = 0;
	int           compressed_pixel_length = 0;
	int           compressed_delta_length = 0;
	
	public static void main(String[] args)
	{
		if (args.length != 1)
		{
			System.out.println("Usage: java ZipTester <filename>");
			System.exit(0);
		}
		String    prefix    = new String("C:/Users/Brian Crowley/Desktop/");
		String    filename  = new String(args[0]);
		ZipTester processor = new ZipTester(prefix + filename);
		
		String java_version = System.getProperty("java.version");
		System.out.println("Current java version is " + java_version);
		String os = System.getProperty("os.name");
		String os_version = System.getProperty("os.version");
		String machine = System.getProperty("os.arch");
		System.out.println("Current os is " + os + " " + os_version + " on " + machine);
	}

	public ZipTester(String _filename)
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
			
			pixel_bit_strings        = new byte[xdim * ydim * 25];
			delta_bit_strings        = new byte[xdim * ydim * 25];
			odd_compressed_pixel_strings   = new byte[xdim * ydim * 25];
			even_compressed_pixel_strings  = new byte[xdim * ydim * 25];
			odd_compressed_delta_strings   = new byte[xdim * ydim * 25];
			even_compressed_delta_strings  = new byte[xdim * ydim * 25];
			compressed_pixel_strings = new byte[xdim * ydim * 25];
			compressed_delta_strings = new byte[xdim * ydim * 25];
			//System.out.println("Xdim is " + xdim + " and ydim is " + ydim);
			//System.out.println("Number of pixel bits is " + (xdim * ydim * 8));
			//System.out.println();
			
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
               
			    JFrame frame = new JFrame("Zip Tester");
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
				
				JMenuItem save_item        = new JMenuItem("Save");
				SaveHandler save_handler = new SaveHandler();
				save_item.addActionListener(save_handler);
				file_menu.add(save_item);
				
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
	
		ApplyHandler()
		{
			original_pixel = new int[xdim * ydim];
			pixel          = new int[xdim * ydim];
			red            = new int[xdim * ydim];
		    green          = new int[xdim * ydim];
		    blue           = new int[xdim * ydim];
		  
		    
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
		    	green[i] >>= pixel_shift;
                blue[i]  =  original_pixel[i] & 0xff; 
		    }
		    
		    ArrayList histogram_list = DeltaMapper.getHistogram(green);
		    int pixel_min            = (int)histogram_list.get(0);
		    int[] histogram          = (int[])histogram_list.get(1);
		    int pixel_random_lut[]   = DeltaMapper.getRandomTable(histogram);
		    for(int i = 0; i < green.length; i++)
		    	green[i] -= pixel_min;
		    pixel_length  = DeltaMapper.packStrings2(green, pixel_random_lut, pixel_bit_strings);
		    
		    compressed_pixel_length = DeltaMapper.compressZeroBits(pixel_bit_strings, pixel_length, even_compressed_pixel_strings);
		    int  current_length     = compressed_pixel_length;
		    boolean even            = true;
		    int     iterations      = 1;
		    while(compressed_pixel_length < pixel_length && current_length <= compressed_pixel_length)
		    {
		    	compressed_pixel_length = current_length;
		    	if(even)
		    	{
		    	    current_length = DeltaMapper.compressZeroBits(even_compressed_pixel_strings, compressed_pixel_length, odd_compressed_pixel_strings);
		    	    if(current_length <= compressed_pixel_length)
		    	    {
		    	        even = false;
		    	        iterations++;
		    	    }
		    	}
		    	else
		    	{
		    		current_length = DeltaMapper.compressZeroBits(odd_compressed_pixel_strings, delta_length, even_compressed_pixel_strings);
		    		if(current_length <= compressed_pixel_length)
		    		{
		    	        even = true;
		    	        iterations++;
		    		}
		    	}
		    }
		    if(even)
		    	compressed_pixel_strings = even_compressed_pixel_strings;
		    else
		    	compressed_pixel_strings = odd_compressed_pixel_strings;
		    
		    compressed_delta_length = DeltaMapper.compressZeroBits(delta_bit_strings, delta_length, even_compressed_delta_strings);
		    current_length  = compressed_delta_length;
		    even            = true;
		    iterations      = 1;
		    while(compressed_delta_length < delta_length && current_length <= compressed_delta_length)
		    {
		    	compressed_delta_length = current_length;
		    	if(even)
		    	{
		    	    current_length = DeltaMapper.compressZeroBits(even_compressed_delta_strings, compressed_delta_length, odd_compressed_delta_strings);
		    	    if(current_length <= compressed_delta_length)
		    	    {
		    	        even = false;
		    	        iterations++;
		    	    }
		    	}
		    	else
		    	{
		    		current_length = DeltaMapper.compressZeroBits(odd_compressed_delta_strings, delta_length, even_compressed_delta_strings);
		    		if(current_length <= compressed_delta_length)
		    		{
		    	        even = true;
		    	        iterations++;
		    		}
		    	}
		    }
		    if(even)
		    	compressed_delta_strings = even_compressed_delta_strings;
		    else
		    	compressed_delta_strings = odd_compressed_delta_strings;
		    
		    
		    int init_value         = green[0];
		    int[] delta            = DeltaMapper.getDeltasFromValues(green, xdim, ydim, init_value);
		    histogram_list         = DeltaMapper.getHistogram(delta);
		    int delta_min          = (int)histogram_list.get(0);
		    histogram              = (int[])histogram_list.get(1);
		    int delta_random_lut[] = DeltaMapper.getRandomTable(histogram); 
		    for(int i = 0; i < delta.length; i++)
		    	delta[i] -= delta_min;
		    delta_length  = DeltaMapper.packStrings2(delta, delta_random_lut, delta_bit_strings);
		    
		    

		    int number_of_ints = DeltaMapper.unpackStrings2(delta_bit_strings, delta_random_lut, delta);
		    for(int i = 0; i < delta.length; i++)
		    	delta[i] += delta_min;
		   
            int[] new_green = DeltaMapper.getValuesFromDeltas(delta, xdim , ydim, init_value);
            for(int i = 0; i < new_green.length; i++)
            {
            	new_green[i] += pixel_min;
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
		    
		    double packed_pixel_bits_ratio = pixel_length;
		    packed_pixel_bits_ratio       /= xdim * ydim * 8;
		    
		    double packed_delta_bits_ratio = delta_length;
		    packed_delta_bits_ratio       /= xdim * ydim * 8;
		    
		    double compressed_pixel_ratio = compressed_pixel_length;
		    compressed_pixel_ratio /= xdim * ydim * 8; 
		    
		    double compressed_delta_ratio = compressed_delta_length;
		    compressed_delta_ratio /= xdim * ydim * 8; 
		    
		    System.out.println("The ratio of packed pixel bits to pixel bits is " + String.format("%.2f", packed_pixel_bits_ratio));
		    System.out.println("The ratio of packed delta bits to pixel bits is " + String.format("%.2f", packed_delta_bits_ratio));
		    System.out.println("The ratio of compressed pixel bits to pixel bits is " + String.format("%.2f", compressed_pixel_ratio));
		    System.out.println("The ratio of compressed pixel bits to delta bits is " + String.format("%.2f", compressed_delta_ratio));
		}
	}
	

	class SaveHandler implements ActionListener
	{
		public void actionPerformed(ActionEvent event)
		{
			StringTokenizer tokenizer = new StringTokenizer(filename, ".");
		    String          root      = tokenizer.nextToken();
			String          extension = tokenizer.nextToken();
			
			String raw_pixel_filename = new String(root + "_p_" + pixel_shift + ".raw");
			String zip_pixel_filename = new String(root + "_p_" + pixel_shift + ".gz");
			String raw_delta_filename = new String(root + "_d_" + pixel_shift + ".raw");
			String zip_delta_filename = new String(root + "_d_" + pixel_shift + ".gz");
			System.out.println();
			
			if(pixel_length == 0)
			{
			    System.out.println("No current data.");
			}
			else
			{
			    int number_of_pixel_bytes = pixel_length / 8;
			    if(pixel_length % 8 != 0)
			    	number_of_pixel_bytes++;
			    System.out.println("Number of pixel bytes is " + number_of_pixel_bytes);
			    
			    int number_of_delta_bytes = delta_length / 8;
			    if(delta_length % 8 != 0)
			    	number_of_delta_bytes++;
			    
			    try
			    {
			        FileOutputStream pixel_out = new FileOutputStream(raw_pixel_filename, false);
			        pixel_out.write(pixel_bit_strings, 0, number_of_pixel_bytes);
			        pixel_out.close();
			        
			        
			        File output_file = new File(raw_pixel_filename);
			       
			        long length = output_file.length();
			        System.out.println("Length of file is " + length);
			        double ratio = (double)length / (xdim * ydim);
			        System.out.println("The ratio of packed pixel bits in a file to pixel bits is " + String.format("%.2f", ratio));
			        
			        FileOutputStream delta_out = new FileOutputStream(raw_delta_filename, false);
			        delta_out.write(delta_bit_strings, 0, number_of_delta_bytes);
			        delta_out.close();
			        
			        output_file = new File(raw_delta_filename);   
			        length = output_file.length();
			        System.out.println("Length of file is " + length);
			        ratio = (double)length / (xdim * ydim);
			        System.out.println("The ratio of packed delta bits in a file to pixel bits is " + String.format("%.2f", ratio));
			        
			        GZIPOutputStream pixel_zip = new GZIPOutputStream(new FileOutputStream(zip_pixel_filename)); 
			        pixel_zip.write(pixel_bit_strings, 0, number_of_pixel_bytes);
			        pixel_zip.close();
			        output_file = new File(zip_pixel_filename);   
			        length = output_file.length();
			        System.out.println("Length of file is " + length);
			        ratio = (double)length / (xdim * ydim);
			        System.out.println("The ratio of zipped packed pixel bits in a file to pixel bits is " + String.format("%.2f", ratio));
			        
			        
			        GZIPOutputStream delta_zip = new GZIPOutputStream(new FileOutputStream(zip_delta_filename)); 
			        delta_zip.write(delta_bit_strings, 0, number_of_delta_bytes);
			        delta_zip.close();
			        output_file = new File(zip_delta_filename);   
			        length = output_file.length();
			        System.out.println("Length of file is " + length);
			        ratio = (double)length / (xdim * ydim);
			        System.out.println("The ratio of zipped packed delta bits in a file to pixel bits is " + String.format("%.2f", ratio));
			        
			    }
			    catch(Exception e)
			    {
			    	System.out.println(e.toString());
			    }
			    
			}
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