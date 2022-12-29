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
	String        filename;
	
	int [] original_pixel;
	int [] alpha;
	int [] red;
	int [] green;
	int [] blue;
	
	int pixel_shift  = 3;
	
	byte[] delta_strings;
	
	public static void main(String[] args)
	{
		if (args.length != 1)
		{
			System.out.println("Usage: java CompressTester <filename>");
			System.exit(0);
		}
		String prefix       = new String("C:/Users/Brian Crowley/Desktop/");
		String filename     = new String(args[0]);
		String java_version = System.getProperty("java.version");
		String os           = System.getProperty("os.name");
		String os_version   = System.getProperty("os.version");
		String machine      = System.getProperty("os.arch");
		//System.out.println("Current java version is " + java_version);
		//System.out.println("Current os is " + os + " " + os_version + " on " + machine);
		//System.out.println("Image file is " + filename);
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
			//System.out.println("Xdim is " + xdim + " and ydim is " + ydim);
		    System.out.println("Number of pixel bits per channel is " + (xdim * ydim * 8));
			
		    original_pixel = new int[xdim * ydim];
		    alpha          = new int[xdim * ydim];
			blue           = new int[xdim * ydim];
		    green          = new int[xdim * ydim];
		    red            = new int[xdim * ydim];
		    
		    delta_strings  = new byte[xdim * ydim * 2];
			
			System.out.println();
			
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
		public void actionPerformed(ActionEvent event)
		{
			 int [] new_alpha    = new int[xdim * ydim]; 
		     int [] new_blue     = new int[xdim * ydim];
		     int [] shifted_blue = new int[xdim * ydim];
		     
		     int [] new_green    = new int[xdim * ydim];
		     int [] shifted_green = new int[xdim * ydim];
		     
		     int [] new_red     = new int[xdim * ydim];
		     int [] shifted_red = new int[xdim * ydim];
		     int [] new_pixel   = new int[xdim * ydim];
		     
		     //int mask = 255;
		     //int k = 0;
		     //for(int j = 0; j < pixel_shift; j++)
		     //	 k += (int)Math.pow(2., j);
		     //mask -= k;
		     //System.out.println("Mask = " + mask);
		     
		     for(int i = 0; i < xdim * ydim; i++)
			 {
		         new_alpha[i] = alpha[i];
		         
			     new_blue[i]    = blue[i];
			     new_blue[i]  >>= pixel_shift;
		         new_blue[i]  <<= pixel_shift;
		         //new_blue[i]   &= mask;
			    
			     //new_green[i]   = green[i];
			     //new_green[i] >>= pixel_shift;
		         //new_green[i] <<= pixel_shift;
		         //new_green[i]   &= mask;
		        
		         shifted_blue[i]  = blue[i]  >> pixel_shift;
		         shifted_green[i] = green[i] >> pixel_shift;
			     shifted_red[i]   = red[i] >> pixel_shift;
		        
		        
		        
	             new_red[i]     = red[i];
	             new_red[i]   >>= pixel_shift;
		         new_red[i]   <<= pixel_shift;
		         //new_red[i]   &= mask;
			}
		    
		    int init_value           = shifted_green[0];
			int[] delta              = DeltaMapper.getDeltasFromValues(shifted_green, xdim, ydim, init_value);
			ArrayList histogram_list = DeltaMapper.getHistogram(delta);
			int delta_min            = (int)histogram_list.get(0);
			int [] histogram         = (int[])histogram_list.get(1);
			int delta_random_lut[]   = DeltaMapper.getRandomTable(histogram); 
			for(int i = 0; i < delta.length; i++)
			    delta[i] -= delta_min;
			int delta_length = 0;
			delta_length  = DeltaMapper.packStrings(delta, delta_random_lut, delta_strings);
			
			
			byte [] compressed_strings = new byte[xdim * ydim * 10];
			byte [] decompressed_strings = new byte[xdim * ydim * 10];
			
			int compressed_delta_length = DeltaMapper.compressZeroBits(delta_strings, delta_length, compressed_strings);
			
			int array_length = compressed_delta_length / 8;
			if(compressed_delta_length % 8 != 0)
				array_length++;
			
			byte [] strings = new byte[array_length];
			for(int i = 0; i < array_length; i++)
			    strings[i] = compressed_strings[i];
			byte [] zipped_strings = new byte[array_length * 2];
			try
		    {
		    	//Deflater deflater = new Deflater(Deflater.BEST_COMPRESSION);
		    	Deflater deflater = new Deflater(Deflater.HUFFMAN_ONLY);
		    	
		    	deflater.setInput(strings);
		    	deflater.finish();
		    	int zipped_length = deflater.deflate(zipped_strings);
		    	deflater.end();
		    	
		        System.out.println("Zipped string length in bits was " + (zipped_length * 8));
		        
		        Inflater inflater = new Inflater();
		        inflater.setInput(zipped_strings, 0, zipped_length);
		        int unzipped_length = inflater.inflate(strings);
		        
		        System.out.println("Unzipped string length in bits was " + (unzipped_length * 8));
		    }
		    catch(Exception e)
		    {
		    	System.out.println(e.toString());
		    }
			
			for(int i = 0; i < array_length; i++)
			{
				if(strings[i] != compressed_strings[i])
					System.out.println("Unzipped value was not equal to original value.");
			}
			
			//int decompressed_delta_length = DeltaMapper.decompressZeroBits(compressed_strings, compressed_delta_length, decompressed_strings);
			int decompressed_delta_length = DeltaMapper.decompressZeroBits(strings, compressed_delta_length, decompressed_strings);
			
			System.out.println("The length of the green delta string in bits is " + delta_length);
			System.out.println("The length of the compressed delta string in bits is " + compressed_delta_length);
			System.out.println("The length of the decompressed delta string in bits is " + decompressed_delta_length);
			System.out.println();
			
			int string_length = delta_length / 8;
			
			for(int i = 0; i < string_length; i++)
			{
				if(decompressed_strings[i] != delta_strings[i])
				{
					System.out.println("Original value different from decompressed value at i = " + i);
				}
			}
			
			int [] new_delta = new int[delta.length];
			//int number_of_ints = DeltaMapper.unpackStrings(delta_strings, delta_random_lut, new_delta);
			int number_of_ints = DeltaMapper.unpackStrings(decompressed_strings, delta_random_lut, new_delta);
			for(int i = 0; i < delta.length; i++)
			     new_delta[i] += delta_min;
			   
	        new_green = DeltaMapper.getValuesFromDeltas(new_delta, xdim , ydim, init_value);
	        
	        int [] shifted_blue_green = DeltaMapper.getDifference(shifted_blue, shifted_green);
	       
	        int blue_green_min = 0;
		    for(int i = 0; i < xdim * ydim; i++)
		    	if(shifted_blue_green[i] < blue_green_min)
	            	blue_green_min = shifted_blue_green[i];
		    for(int i = 0; i < xdim * ydim; i++)
		    {
		    	shifted_blue_green[i] -= blue_green_min;
		    }
	     
		    init_value       = shifted_blue_green[0];
		    delta            = DeltaMapper.getDeltasFromValues(shifted_blue_green, xdim, ydim, init_value);
		    histogram_list   = DeltaMapper.getHistogram(delta);
		    delta_min        = (int)histogram_list.get(0);
		    histogram        = (int[])histogram_list.get(1);
		    delta_random_lut = DeltaMapper.getRandomTable(histogram); 
		    for(int i = 0; i < delta.length; i++)
		    	delta[i] -= delta_min;
		    delta_length = 0;
		    
		    delta_length  = DeltaMapper.packStrings(delta, delta_random_lut, delta_strings);
		    new_delta = new int[delta.length];
			
		    compressed_delta_length = DeltaMapper.compressZeroBits(delta_strings, delta_length, compressed_strings);
			decompressed_delta_length = DeltaMapper.decompressZeroBits(compressed_strings, compressed_delta_length, decompressed_strings);
		    
			System.out.println("The length of the blue green delta string in bits is " + delta_length);
			System.out.println("The length of the compressed delta string in bits is " + compressed_delta_length);
			System.out.println("The length of the decompressed delta string in bits is " + decompressed_delta_length);
			System.out.println();
		    
		    //number_of_ints = DeltaMapper.unpackStrings(delta_strings, delta_random_lut, new_delta);
			number_of_ints = DeltaMapper.unpackStrings(decompressed_strings, delta_random_lut, new_delta);
		    for(int i = 0; i < delta.length; i++)
		    {
		    	new_delta[i] += delta_min;
		    	delta[i]     += delta_min;
		    	
		    	if(new_delta[i] != delta[i])
		    	{
		    		System.out.println("Unpacked value different from original value.");
		    	}
		    }
		   
            int[] new_blue_green = DeltaMapper.getValuesFromDeltas(new_delta, xdim , ydim, init_value);
            
		    for(int i = 0; i < xdim * ydim; i++)
		    {
		    	new_blue_green[i] += blue_green_min;
		    }
		   
		    new_blue = DeltaMapper.getSum(new_blue_green, new_green);
		    for(int i = 0; i < xdim * ydim; i++)
		    {
		    	new_blue[i] <<= pixel_shift;
		    	if(new_blue[i] < 0)
		    	{
		    		new_blue[i] = 0;
		    		System.out.println("Negative value out of range.");
		    	}
		    	else if(new_blue[i] > 255)
		    	{
		    		new_blue[i] = 255;
		    		System.out.println("Positive value out of range.");
		    	}
		    }
		    
	        int [] red_green = DeltaMapper.getDifference(shifted_red, new_green);
	        int red_green_min = 0;
		    for(int i = 0; i < xdim * ydim; i++)
		    	if(red_green[i] < red_green_min)
	            	red_green_min = red_green[i];
		    for(int i = 0; i < xdim * ydim; i++)
		    {
		    	red_green[i] -= red_green_min;
		    }
	     
		    init_value       = red_green[0];
		    delta            = DeltaMapper.getDeltasFromValues(red_green, xdim, ydim, init_value);
		    histogram_list   = DeltaMapper.getHistogram(delta);
		    delta_min        = (int)histogram_list.get(0);
		    histogram        = (int[])histogram_list.get(1);
		    delta_random_lut = DeltaMapper.getRandomTable(histogram); 
		    for(int i = 0; i < delta.length; i++)
		    	delta[i] -= delta_min;
		    delta_length = 0;
		    
		    delta_length  = DeltaMapper.packStrings(delta, delta_random_lut, delta_strings);
		    
		    compressed_delta_length = DeltaMapper.compressZeroBits(delta_strings, delta_length, compressed_strings);
			decompressed_delta_length = DeltaMapper.decompressZeroBits(compressed_strings, compressed_delta_length, decompressed_strings);
		    
			System.out.println("The length of the red green delta string in bits is " + delta_length);
			System.out.println("The length of the compressed delta string in bits is " + compressed_delta_length);
			System.out.println("The length of the decompressed delta string in bits is " + decompressed_delta_length);
			System.out.println();
		    
		    
			
			new_delta = new int[delta.length];
		    //number_of_ints = DeltaMapper.unpackStrings(delta_strings, delta_random_lut, new_delta);
		    number_of_ints = DeltaMapper.unpackStrings(decompressed_strings, delta_random_lut, new_delta);
		    for(int i = 0; i < delta.length; i++)
		    {
		    	new_delta[i] += delta_min;
		    	delta[i] += delta_min;
		    	
		    	if(new_delta[i] != delta[i])
		    	{
		    		System.out.println("Unpacked value different from original value.");
		    	}
		    }
		   
            int[] new_red_green = DeltaMapper.getValuesFromDeltas(new_delta, xdim , ydim, init_value);
            
		    for(int i = 0; i < xdim * ydim; i++)
		    {
		    	new_red_green[i] += red_green_min;
		    }
		   
		    new_red = DeltaMapper.getSum(new_red_green, new_green);
		    for(int i = 0; i < xdim * ydim; i++)
		    {
		    	new_red[i] <<= pixel_shift;
		    	if(new_red[i] < 0)
		    	{
		    		new_red[i] = 0;
		    		System.out.println("Negative value out of range.");
		    	}
		    	else if(new_red[i] > 255)
		    	{
		    		new_red[i] = 255;
		    		System.out.println("Positive value out of range.");
		    	}
		    }
		   
		    for(int i = 0; i < xdim * ydim; i++)
		    {
		        // Not initalizing the alpha channel.
	            new_pixel[i] = 0;
	            new_green[i] <<= pixel_shift;
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
		    System.out.println("Processed image.");
		    System.out.println();
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