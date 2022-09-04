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

public class QuantTester
{
	BufferedImage original_image;
	BufferedImage image;
	JMenuItem     apply_item;
	JDialog       shift_dialog;
	JTextField    shift_value;
	ImageCanvas   image_canvas;
	
	int           xdim, ydim;
	String        filename;
	
	byte[]        root_bit_strings;
	byte[]        error_bit_strings;
	int           root_length  = 0;
	int           error_length = 0;
	int           pixel_shift  = 3;
	
	
	public static void main(String[] args)
	{
		if (args.length != 1)
		{
			System.out.println("Usage: java QuantTester <filename>");
			System.exit(0);
		}
		String      prefix    = new String("C:/Users/Brian Crowley/Desktop/");
		String      filename  = new String(args[0]);
		QuantTester processor = new QuantTester(prefix + filename);
	}

	public QuantTester(String _filename)
	{
		filename = _filename;
		try
		{
			File file              = new File(filename);
			original_image         = ImageIO.read(file);
			int raster_type        = original_image.getType();
			ColorModel color_model = original_image.getColorModel();
			int number_of_channels = color_model.getNumColorComponents();
			int number_of_bits     = color_model.getPixelSize();
			xdim                   = original_image.getWidth();
			ydim                   = original_image.getHeight();
			root_bit_strings       = new byte[xdim * ydim * 25];
			error_bit_strings      = new byte[xdim * ydim * 25];
			
			System.out.println("Size of image in bytes is " + (xdim * ydim));
			System.out.println();
			
			if(raster_type == BufferedImage.TYPE_3BYTE_BGR)
			{
				int[]        pixel         = new int[xdim * ydim];
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
			    
		
			    JFrame frame = new JFrame("Quantize Tester");
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
		int [] scratch;
		int [] error;
	
		double[] red_double;
		double[] red_shrink;
		double[] red_expand;
		double[] red_error;
		
		double[] green_double;
		double[] green_shrink;
		double[] green_expand;
		double[] green_error;
		
		double[] blue_double;
		double[] blue_shrink;
		double[] blue_expand;
		double[] blue_error;
		
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
		    
		    red_error = new double[xdim * ydim];
		    green_error = new double[xdim * ydim];
		    blue_error = new double[xdim * ydim];
		    
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
		    int    size     = xdim * ydim;
		    double _factor  = Math.pow(2., (double)pixel_shift);
		    int    factor   = (int)_factor;
		    double fraction = 1. / _factor;
		    
		    //System.out.println("Fraction is " + fraction);
		    
		    int [] error = new int[size];
		    int [] root  = new int[size];
		    
		    int original_green_min = Integer.MAX_VALUE;
		    int original_green_max = -Integer.MAX_VALUE;
		    int shifted_green_min = Integer.MAX_VALUE;
		    int shifted_green_max = -Integer.MAX_VALUE;
		    for(int i = 0; i < size; i++)
		    {
		    	red[i]   = (original_pixel[i] >> 16) & 0xff;
		    	green[i] = (original_pixel[i] >> 8) & 0xff; 
                blue[i]  =  original_pixel[i] & 0xff; 
                
                if(green[i] > original_green_max)
                	original_green_max = green[i];
                if(green[i] < original_green_min)
                	original_green_min = green[i];
                //red[i]   >>= pixel_shift;
                //green[i] >>= pixel_shift;
                
                error[i] = green[i] % factor;
                root[i]  = green[i] / factor;
            
		    }
		    
            int init_value = root[0];
		    
		    int[] delta = DeltaMapper.getDeltasFromValues(root, xdim, ydim, init_value);
		    
		    
		    ArrayList histogram_list = DeltaMapper.getHistogram(delta);
		    int       delta_min      = (int)histogram_list.get(0);
		    int[]     histogram      = (int[])histogram_list.get(1);
		    int       delta_range    = histogram.length;
		    int       delta_max      = delta_min + delta_range - 1;
		   
		    
		    System.out.println("The delta min is " + delta_min);
		    System.out.println("The delta max is " + delta_max);
		    System.out.println("The range is     " + delta_range);
		    System.out.println();
		    
            /*
		    System.out.println("Delta histogram:");
		    for(int i = 0; i < histogram.length; i++)
		    	System.out.println(i + " -> " + histogram[i]);
		    System.out.println();
		    */
		    
		    int delta_random_lut[] = DeltaMapper.getRandomTable(histogram);
		    
		    for(int i = 0; i < delta.length; i++)
		    	delta[i] -= delta_min;
		   
		    root_length  = DeltaMapper.packStrings2(delta, delta_random_lut, root_bit_strings);
		    
		    double root_pixel_ratio = root_length;
		    root_pixel_ratio        /= xdim * ydim * 8;
		  
		    System.out.println("The ratio of packed delta root bits to pixel bits is " + String.format("%.2f", root_pixel_ratio));
		   
		    for(int i = 0; i < delta.length; i++)
		    	delta[i] += delta_min;
		    
		    root = DeltaMapper.getValuesFromDeltas(delta, xdim, ydim, init_value);
		    
		    histogram_list            = DeltaMapper.getHistogram(error);
		    int       error_min       = (int)histogram_list.get(0);
		    int[]     error_histogram = (int[])histogram_list.get(1);
		    int       error_range     = histogram.length;
		    int       error_max       = error_min + error_range - 1;
		   
		    
		    for(int i = 0; i < error.length; i++)
		    	error[i] -= error_min;
		    int error_random_lut[]    = DeltaMapper.getRandomTable(error_histogram);
		    error_length              = DeltaMapper.packStrings2(error, error_random_lut, error_bit_strings);
		    for(int i = 0; i < error.length; i++)
		    	error[i] += error_min;
		    
		    double error_pixel_ratio = error_length;
		    error_pixel_ratio /= xdim * ydim * 8;
		    
		    System.out.println("The ratio of packed error bits to pixel bits is " + String.format("%.2f", error_pixel_ratio));
		    System.out.println();
		    
		    for(int i = 0; i < size; i++)
            {
                int value      = root[i] * factor;
                double d_error = error[i] * fraction;
                d_error       *= factor;
                int _error     = (int)d_error;
                value         += _error;
                green[i]       = value;
                if(green[i] > shifted_green_max)
                	shifted_green_max = green[i];
                if(green[i] < shifted_green_min)
                	shifted_green_min = green[i];
            }
		    
		    
		    System.out.println("Orginal green min is " + original_green_min);
		    System.out.println("Original green max is " + original_green_max);
		    System.out.println("Shifted green min is " + shifted_green_min);
		    System.out.println("Shifted green max is " + shifted_green_max);
		    System.out.println();
		    
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
	

	class SaveHandler implements ActionListener
	{
		public void actionPerformed(ActionEvent event)
		{
			StringTokenizer tokenizer = new StringTokenizer(filename, ".");
		    String          root      = tokenizer.nextToken();
			String          extension = tokenizer.nextToken();
			
			String raw_root_filename = new String(root + "_r_" + pixel_shift + ".raw");
			String zip_root_filename = new String(root + "_r_" + pixel_shift + ".gz");
			String raw_error_filename = new String(root + "_e_" + pixel_shift + ".raw");
			String zip_error_filename = new String(root + "_e_" + pixel_shift + ".gz");
			System.out.println();
			
			if(root_length == 0)
			{
			    System.out.println("No current data.");
			}
			else
			{
			    int number_of_root_bytes = root_length / 8;
			    if(root_length % 8 != 0)
			    	number_of_root_bytes++;
			    
			    int number_of_error_bytes = error_length / 8;
			    if(error_length % 8 != 0)
			    	number_of_error_bytes++;
			    
			    try
			    {
			        FileOutputStream root_out = new FileOutputStream(raw_root_filename, false);
			        root_out.write(root_bit_strings, 0, number_of_root_bytes);
			        root_out.close();
			        
			        FileOutputStream error_out = new FileOutputStream(raw_error_filename, false);
			        error_out.write(error_bit_strings, 0, number_of_error_bytes);
			        error_out.close();
			        
			        GZIPOutputStream root_zip = new GZIPOutputStream(new FileOutputStream(zip_root_filename)); 
			        root_zip.write(root_bit_strings, 0, number_of_root_bytes);
			        root_zip.close();
			        
			        GZIPOutputStream error_zip = new GZIPOutputStream(new FileOutputStream(zip_error_filename)); 
			        error_zip.write(error_bit_strings, 0, number_of_error_bytes);
			        error_zip.close();
			    }
			    catch(Exception e)
			    {
			    	System.out.println(e.toString());
			    }
			    
			}
		}
	}
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
}