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

public class CompressTester
{
	BufferedImage original_image;
	BufferedImage image;
	JMenuItem     apply_item;
	JDialog       shift_dialog;
	JTextField    shift_value;
	ImageCanvas   image_canvas;
	
	int           xdim, ydim;
	byte[]        temp;
	double        zero_ratio;
	
	byte[]        delta_strings;
	byte[]        odd_compressed_strings;
	byte[]        even_compressed_strings;
	byte[]        compressed_strings;
	
	int           pixel_shift       = 3;
	int           delta_length      = 0;
	int           compressed_length = 0;
	
	String        filename;
	
	public static void main(String[] args)
	{
		if (args.length != 1)
		{
			System.out.println("Usage: java CompressTester <filename>");
			System.exit(0);
		}
		String      prefix    = new String("C:/Users/Brian Crowley/Desktop/");
		String      filename  = new String(args[0]);
		CompressTester processor = new CompressTester(prefix + filename);
	}

	public CompressTester(String _filename)
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
			delta_strings = new byte[25 * xdim * ydim];
			odd_compressed_strings = new byte[25 * xdim * ydim];
			even_compressed_strings = new byte[25 * xdim * ydim];
			
		
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
			    
		        /*
			    for(int i = 0; i < size; i++)
                {
                    pixel[i] = 0;
                    pixel[i] |= 255 << 24;
                    pixel[i] |= red[i] << 16;
                    pixel[i] |= green[i] << 8;
                    pixel[i] |= blue[i];
                }
                */
               
			    JFrame frame = new JFrame("Compression Tester");
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
				file_menu.add(apply_item);
				
				JMenuItem reload_item = new JMenuItem("Reload");
				ReloadHandler reload_handler        = new ReloadHandler();
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
		    int  size      = xdim * ydim;
		    int  pixel_min = 256;
		    for(int i = 0; i < size; i++)
		    {
		    	red[i]   = (original_pixel[i] >> 16) & 0xff;
		    	green[i] = (original_pixel[i] >> 8) & 0xff; 
                blue[i]  =  original_pixel[i] & 0xff; 
                green[i] >>= pixel_shift;
                if(pixel_min > green[i])
                	pixel_min = green[i];
		    }
		    for(int i = 0; i < green.length; i++)
		    	green[i] -= pixel_min;
		    
		    
		    
		    
		    int init_value           = green[0];
		    int[] delta              = DeltaMapper.getDeltasFromValues(green, xdim, ydim, init_value);
		    ArrayList histogram_list = DeltaMapper.getHistogram(delta);
		    int delta_min            = (int)histogram_list.get(0);
		    int[] histogram          = (int[])histogram_list.get(1);
		    int delta_random_lut[] = DeltaMapper.getRandomTable(histogram); 
		    for(int i = 0; i < delta.length; i++)
		    	delta[i] -= delta_min;
		    delta_length  = DeltaMapper.packStrings2(delta, delta_random_lut, delta_strings);
		    int number_of_ints = DeltaMapper.unpackStrings2(delta_strings, delta_random_lut, delta);
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
		    
		    double packed_delta_bits_ratio = delta_length;
		    packed_delta_bits_ratio       /= size * 8;
		    System.out.println("Ratio of packed delta bits to pixel bits is " + String.format("%.2f", packed_delta_bits_ratio));
		    
		    compressed_length      = DeltaMapper.compressZeroBits(delta_strings, delta_length, even_compressed_strings);
		    int     current_length = compressed_length;
		    boolean even           = true;
		    int     iterations     = 1;
		    while(compressed_length < delta_length && current_length <= compressed_length)
		    {
		    	compressed_length = current_length;
		    	if(even)
		    	{
		    	    current_length = DeltaMapper.compressZeroBits(even_compressed_strings, delta_length, odd_compressed_strings);
		    	    if(current_length <= compressed_length)
		    	    {
		    	        even = false;
		    	        iterations++;
		    	    }
		    	}
		    	else
		    	{
		    		current_length = DeltaMapper.compressZeroBits(odd_compressed_strings, delta_length, even_compressed_strings);
		    		if(current_length <= compressed_length)
		    		{
		    	        even = true;
		    	        iterations++;
		    		}
		    	}
		    	
		    }
		    if(even)
		    	compressed_strings = even_compressed_strings;
		    else
		    	compressed_strings = odd_compressed_strings;
		    double compressed_packed_delta_bits_ratio = compressed_length;
		    compressed_packed_delta_bits_ratio       /= size * 8;
		    System.out.println("Ratio of compressed packed delta bits to pixel bits is " + String.format("%.2f", compressed_packed_delta_bits_ratio));
		    System.out.println("Number of iterations is " + iterations);
		 
		    for(int i = 0; i < pixel.length; i++)
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
		    System.out.println();
		}
	}
	

	class SaveHandler implements ActionListener
	{
		public void actionPerformed(ActionEvent event)
		{
			StringTokenizer tokenizer = new StringTokenizer(filename, ".");
		    String          root      = tokenizer.nextToken();
			String          extension = tokenizer.nextToken();
			
			String raw_delta_filename      = new String(root + "_u_" + pixel_shift + ".raw");
			String zip_delta_filename      = new String(root + "_u_" + pixel_shift + ".gz");
			String raw_compressed_filename = new String(root + "_c_" + pixel_shift + ".raw");
			String zip_compressed_filename = new String(root + "_c_" + pixel_shift + ".gz");
			
			if(delta_length == 0)
			{
			    System.out.println("No current data.");
			}
			else
			{
			    int number_of_bytes = delta_length / 8;
			    if(delta_length % 8 != 0)
			    	number_of_bytes++;
			    
			    int number_of_compressed_bytes = compressed_length / 8;
			    if(compressed_length % 8 != 0)
			    	number_of_compressed_bytes++;
			    
			    try
			    {
			        FileOutputStream raw_delta_out = new FileOutputStream(raw_delta_filename, false);
			        raw_delta_out.write(delta_strings, 0, delta_length);
			        raw_delta_out.close();
			        
			        GZIPOutputStream  zip_delta_out = new GZIPOutputStream(new FileOutputStream(zip_delta_filename)); 
			        zip_delta_out.write(delta_strings, 0, delta_length);
			        zip_delta_out.close();
			        
			        FileOutputStream compressed = new  FileOutputStream(raw_compressed_filename); 
			        compressed.write(compressed_strings, 0, compressed_length);
			        compressed.close();
			        
			        GZIPOutputStream compressed_zip = new GZIPOutputStream(new FileOutputStream(zip_compressed_filename)); 
			        compressed_zip.write(compressed_strings, 0, compressed_length);
			        compressed_zip.close();
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