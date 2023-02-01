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
	
	JDialog       block_dialog;
	JTextField    block_value;
	
	ImageCanvas   image_canvas;
	
	int           xdim, ydim;
	String        filename;
	
	boolean       green_only = false;
	
	int [] original_pixel;
	int [] alpha;
	int [] red;
	int [] green;
	int [] blue;
	
	int     pixel_shift  = 3;
	int     pixel_length = 0;
	boolean huffman_only = true;
	boolean compress     = true;
	long    file_length  = 0;

	int     block_xdim   = 16;
	int     block_ydim   = 16;
	int     block_dim    = 16;
	int     x_offset      = 0;
	int     y_offset      = 0;
	
	byte[] delta_strings;
	
	public static void main(String[] args)
	{
		if (args.length != 1)
		{
			System.out.println("Usage: java BlockTester <filename>");
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
		    delta_strings  = new byte[xdim * ydim * 2];
			
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
				
				
				 
				JMenuItem block_item = new JMenuItem("Block Size");
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
				block_dialog = new JDialog(frame, "Block Size");
				JPanel block_panel = new JPanel(new BorderLayout());
				JSlider block_slider = new JSlider();
				block_slider.setMinimum(2);
				if(xdim < ydim)
				    block_slider.setMaximum(xdim);
				else
				    block_slider.setMaximum(ydim);
				block_slider.setValue(block_dim);
				block_value = new JTextField(3);
				block_value.setText(" " + block_dim + " ");
				ChangeListener block_slider_handler = new ChangeListener()
				{
					public void stateChanged(ChangeEvent e)
					{
						JSlider slider = (JSlider) e.getSource();
						block_dim = slider.getValue();
						block_xdim = block_dim;
						block_ydim = block_dim;
						block_value.setText(" " + block_dim + " ");
						if(slider.getValueIsAdjusting() == false)
						{
							apply_item.doClick();
						}
					}
				};
				block_slider.addChangeListener(block_slider_handler);
				block_panel.add(block_slider, BorderLayout.CENTER);
				block_panel.add(block_value, BorderLayout.EAST);
				block_dialog.add(block_panel);
				
				settings_menu.add(block_item);
				
				
				
				JCheckBoxMenuItem set_compress = new JCheckBoxMenuItem("Compress");
				ActionListener compress_handler = new ActionListener()
				{
					public void actionPerformed(ActionEvent e) 
		            {
		            	JCheckBoxMenuItem item = (JCheckBoxMenuItem) e.getSource();
		            	if(compress == true)
						{
		            		compress = false;
							item.setState(false);
						}
						else
						{
							compress = true;
							item.setState(true);	
						}
		            }   	
				};
				set_compress.addActionListener(compress_handler);
				if(compress)
					set_compress.setState(true);
				else
					set_compress.setState(false);
				settings_menu.add(set_compress);
				
				JCheckBoxMenuItem set_huffman = new JCheckBoxMenuItem("Huffman Only");
				ActionListener huffman_handler = new ActionListener()
				{
					public void actionPerformed(ActionEvent e) 
		            {
		            	JCheckBoxMenuItem item = (JCheckBoxMenuItem) e.getSource();
		            	if(huffman_only == true)
						{
		            		huffman_only = false;
							item.setState(false);
						}
						else
						{
							huffman_only = true;
							item.setState(true);	
						}
		            }   	
				};
				set_huffman.addActionListener(huffman_handler);
				if(huffman_only)
					set_huffman.setState(true);
				else
					set_huffman.setState(false);
				settings_menu.add(set_huffman);
				
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
		    
		    byte [] delta_bytes = new byte[xdim * ydim];
		    
		    
		    byte [] zipped_strings = new byte[xdim * ydim * 8];
		    byte [] compressed_strings = new byte[xdim * ydim * 8];
		    
		    double file_ratio = 0;
		    
		    Deflater deflater;
		    
		    int number_of_channels = 3;
		    int pixel_length = xdim * ydim * 8;
		    file_ratio = file_length * 8;
		    file_ratio /= pixel_length * number_of_channels;
		    System.out.println("Pixel shift is " + pixel_shift);
		    System.out.println("Huffman only is " + huffman_only);
		    System.out.println("File compression rate is " + String.format("%.4f", file_ratio));
		    System.out.println();
		    
		    for(int i = 0; i < xdim * ydim; i++)
		    {
		        shifted_blue[i]  = blue[i]  >> pixel_shift;
		        shifted_green[i] = green[i] >> pixel_shift;
			    shifted_red[i]   = red[i]   >> pixel_shift; 
		    }
		    
		    //System.out.println("Green:");
		    int init_value           = shifted_green[0];
		    int[] delta              = DeltaMapper.getDeltasFromValues(shifted_green, xdim, ydim, init_value);
			
		    ArrayList histogram_list = DeltaMapper.getHistogram(delta);
			int delta_min            = (int)histogram_list.get(0);
			int [] histogram         = (int[])histogram_list.get(1);
			int delta_random_lut[]   = DeltaMapper.getRandomTable(histogram); 
			delta_bytes[0]           = 0;
			for(int i = 1; i < delta.length; i++)
			{
			    delta[i] -= delta_min;
			    delta_bytes[i] = (byte)delta[i];
			}
			
			if(huffman_only)
				deflater = new Deflater(Deflater.HUFFMAN_ONLY);	
			else
				deflater = new Deflater(Deflater.BEST_COMPRESSION);	
	    	deflater.setInput(delta_bytes, 0, xdim * ydim);
	    	deflater.finish();
	    	int zipped_length = deflater.deflate(zipped_strings);
	    	deflater.end();
			
	    	double ratio = zipped_length * 8;
	    	ratio /= pixel_length;
	    	
	    	//System.out.println("The compression rate for zipped delta ints is " + String.format("%.4f", ratio));

			int delta_length = 0;
			delta_length  = DeltaMapper.packStrings2(delta, delta_random_lut, delta_strings);
			ratio = delta_length;
			ratio /= pixel_length;
			//System.out.println("The compression rate for delta strings is " + String.format("%.4f", ratio));
			
			int delta_array_length = delta_length / 8;
			if(delta_length % 8 != 0)
				delta_array_length++;
		
			if(huffman_only)
				deflater = new Deflater(Deflater.HUFFMAN_ONLY);	
			else
				deflater = new Deflater(Deflater.BEST_COMPRESSION);	
	    	deflater.setInput(delta_strings, 0, delta_array_length);
	    	deflater.finish();
	    	zipped_length = deflater.deflate(zipped_strings);
	    	deflater.end();
	    	
	    	ratio = zipped_length * 8;
	    	ratio /= pixel_length;
	    	//System.out.println("The compression rate for zipped delta strings is " + String.format("%.4f", ratio));
	    	//System.out.println();
	    	
			if(compress)
			{
                double zero_one_ratio = xdim * ydim;
				// Subtract the missing stop bits from the max codes.
				int min_value = Integer.MAX_VALUE;
				for(int i = 0; i < histogram.length; i++)
					if(histogram[i] < min_value)
						min_value = histogram[i];
				zero_one_ratio -= min_value;
				
				zero_one_ratio  /= delta_length;
				
				// This is a precise number, but it's also important how the numbers are arranged.
				int compressed_delta_length = 0;
				
				// No clear cut off point where compressing zero strings will work, but it definitely won't work with less than half.
				// Will keep an eye on this.
				if(zero_one_ratio > .5)
				{
				    compressed_delta_length =  DeltaMapper.compressZeroStrings(delta_strings, delta_length, compressed_strings);
				    if(compressed_delta_length > delta_length)
				    {
				    	//System.out.println("Zero strings did not compress.");
				    	//System.out.println("The zero one ratio in the bit string is " + String.format("%.4f", zero_one_ratio));
				    }
				}
				else
				{
				    compressed_delta_length =  DeltaMapper.compressOneStrings(delta_strings, delta_length, compressed_strings);
				    if(compressed_delta_length > delta_length)
				    {
				    	//System.out.println("One strings did not compress.");
				    	//System.out.println("The zero one ratio in the bit string is " + String.format("%.4f", zero_one_ratio));
				    }
				}
				ratio  = compressed_delta_length;
				ratio /= pixel_length;
				//System.out.println("The compression rate for compressed delta strings is " + String.format("%.4f", ratio));	
				
				int compressed_array_length = compressed_delta_length / 8;
				if(compressed_delta_length %8 != 0)
					compressed_array_length++;
				
				if(huffman_only)
					deflater = new Deflater(Deflater.HUFFMAN_ONLY);
				else
					deflater = new Deflater(Deflater.BEST_COMPRESSION);
				deflater.setInput(compressed_strings, 0, compressed_array_length);
			    deflater.finish();
			    zipped_length = deflater.deflate(zipped_strings);
			    deflater.end();
			    
			    ratio = zipped_length * 8;
			    ratio /= pixel_length;
			    //System.out.println("The compression rate for zipped compressed delta strings is " + String.format("%.4f", ratio));
			    //System.out.println();
			}
			
			System.out.println("Block xdim is " + block_xdim + ", ydim is " + block_ydim );  
			System.out.println();

		    //System.out.println("Green:");
		    int block_pixel_length = block_xdim * block_ydim * 8;
		    int [] block = DeltaMapper.extract(shifted_green, xdim, x_offset, y_offset, block_xdim, block_ydim);
		     
		    init_value       = block[0];
		    delta            = DeltaMapper.getDeltasFromValues(block, block_xdim, block_ydim, init_value);
		    
		    int green_delta_sum = 0;
		    if(delta[0] == 0)
		    {
		    	//System.out.println("Delta type is horizontal.");
		    	green_delta_sum =  DeltaMapper.getHorizontalDeltaSum(delta, block_xdim, block_ydim);
		    }
		    else
		    {
		    	//System.out.println("Delta type is vertical.");
		    	green_delta_sum =  DeltaMapper.getVerticalDeltaSum(delta, block_xdim, block_ydim);
		    }
		    histogram_list   = DeltaMapper.getHistogram(delta);
			delta_min        = (int)histogram_list.get(0);
			histogram        = (int[])histogram_list.get(1);
			delta_random_lut = DeltaMapper.getRandomTable(histogram); 
			delta_bytes[0]   = 0;
			for(int i = 1; i < delta.length; i++)
			{
			    delta[i]      -= delta_min;
			    delta_bytes[i] = (byte)delta[i];
			}
			if(huffman_only)
				deflater = new Deflater(Deflater.HUFFMAN_ONLY);
			else
				deflater = new Deflater(Deflater.BEST_COMPRESSION);
			deflater.setInput(delta_bytes, 0, block_xdim * block_ydim);
	    	deflater.finish();
	    	zipped_length = deflater.deflate(zipped_strings);
	    	deflater.end();
			
	    	ratio = zipped_length * 8;
	    	ratio /= block_pixel_length;
	    	
	    	//System.out.println("The compression rate for zipped delta ints is " + String.format("%.4f", ratio));
			
			delta_length = 0;
			delta_length  = DeltaMapper.packStrings2(delta, delta_random_lut, delta_strings);
			ratio = delta_length;
			ratio /= block_pixel_length;
			//System.out.println("The compression rate for delta strings is " + String.format("%.4f", ratio));
			
			double green_string_rate = ratio;
			double compressed_green_string_rate = 0;
			
			delta_array_length = delta_length / 8;
			if(delta_length % 8 != 0)
				delta_array_length++;
			
			if(huffman_only)
				deflater = new Deflater(Deflater.HUFFMAN_ONLY);	
			else
				deflater = new Deflater(Deflater.BEST_COMPRESSION);	
			
	    	deflater.setInput(delta_strings, 0, delta_array_length);
	    	deflater.finish();
	    	zipped_length = deflater.deflate(zipped_strings);
	    	deflater.end();
			
	    	ratio = zipped_length * 8;
	    	ratio /= block_pixel_length;
	    	//System.out.println("The compression rate for zipped delta strings is " + String.format("%.4f", ratio));
	    	//System.out.println();
	    	
			if(compress)
			{
				double zero_one_ratio = xdim * ydim;
				
				// Subtract the missing stop bits from the max codes.
				int min_value = Integer.MAX_VALUE;
				for(int i = 0; i < histogram.length; i++)
					if(histogram[i] < min_value)
						min_value = histogram[i];
				zero_one_ratio -= min_value;
				
				zero_one_ratio  /= delta_length;
				
				// This is a precise number, but it's also important how the numbers are arranged.
				int compressed_delta_length = 0;
				
				// No clear cut off point where compressing zero strings will work, but it definitely won't work with less than half.
				// Will keep an eye on this.
				if(zero_one_ratio > .5)
				{
				    compressed_delta_length =  DeltaMapper.compressZeroStrings(delta_strings, delta_length, compressed_strings);
				    if(compressed_delta_length > delta_length)
				    {
				    	//System.out.println("Zero strings did not compress.");
				    	//System.out.println("The zero one ratio in the bit string is " + String.format("%.4f", zero_one_ratio));
				    }
				}
				else
				{
				    compressed_delta_length =  DeltaMapper.compressOneStrings(delta_strings, delta_length, compressed_strings);
				    if(compressed_delta_length > delta_length)
				    {
				    	//System.out.println("One strings did not compress.");
				    	//System.out.println("The zero one ratio in the bit string is " + String.format("%.4f", zero_one_ratio));
				    }
				}
				//System.out.println("Compressed delta length is " + compressed_delta_length);
				ratio  = compressed_delta_length;
				ratio /= block_pixel_length;
				compressed_green_string_rate = ratio;
				//System.out.println("The compression rate for compressed delta strings is " + String.format("%.4f", ratio));	
				
				int compressed_array_length = compressed_delta_length / 8;
				if(compressed_delta_length %8 != 0)
					compressed_array_length++;
				
				if(huffman_only)
					deflater = new Deflater(Deflater.HUFFMAN_ONLY);
				else
					deflater = new Deflater(Deflater.BEST_COMPRESSION);
				deflater.setInput(compressed_strings, 0, compressed_array_length);
			    deflater.finish();
			    zipped_length = deflater.deflate(zipped_strings);
			    deflater.end();
			    
			    ratio = zipped_length * 8;
			    ratio /= block_pixel_length;
			    //System.out.println("The compression rate for zipped compressed delta strings is " + String.format("%.4f", ratio));
			    //System.out.println();
			}
			//System.out.println();
			
			
		    //System.out.println("Blue:");
		    block = DeltaMapper.extract(shifted_blue, xdim, x_offset, y_offset, block_xdim, block_ydim);
		     
		    init_value       = block[0];
		    delta            = DeltaMapper.getDeltasFromValues(block, block_xdim, block_ydim, init_value);
		    
		    int blue_delta_sum = 0;
		    if(delta[0] == 0)
		    {
		    	//System.out.println("Delta type is horizontal.");
		    	blue_delta_sum =  DeltaMapper.getHorizontalDeltaSum(delta, block_xdim, block_ydim);
		    }
		    else
		    {
		    	//System.out.println("Delta type is vertical.");
		    	blue_delta_sum =  DeltaMapper.getVerticalDeltaSum(delta, block_xdim, block_ydim);
		    }
		    histogram_list   = DeltaMapper.getHistogram(delta);
			delta_min        = (int)histogram_list.get(0);
			histogram        = (int[])histogram_list.get(1);
			delta_random_lut = DeltaMapper.getRandomTable(histogram); 
			delta_bytes[0]   = 0;
			for(int i = 1; i < delta.length; i++)
			{
			    delta[i]      -= delta_min;
			    delta_bytes[i] = (byte)delta[i];
			}
			if(huffman_only)
				deflater = new Deflater(Deflater.HUFFMAN_ONLY);
			else
				deflater = new Deflater(Deflater.BEST_COMPRESSION);
			deflater.setInput(delta_bytes, 0, block_xdim * block_ydim);
	    	deflater.finish();
	    	zipped_length = deflater.deflate(zipped_strings);
	    	deflater.end();
			
	    	ratio = zipped_length * 8;
	    	ratio /= block_pixel_length;
	    	
	    	//System.out.println("The compression rate for zipped delta ints is " + String.format("%.4f", ratio));
			
			delta_length = 0;
			delta_length  = DeltaMapper.packStrings2(delta, delta_random_lut, delta_strings);
			ratio = delta_length;
			ratio /= block_pixel_length;
			//System.out.println("The compression rate for delta strings is " + String.format("%.4f", ratio));
			double blue_string_rate = ratio;
			double compressed_blue_string_rate = 0;
			
			delta_array_length = delta_length / 8;
			if(delta_length % 8 != 0)
				delta_array_length++;
			
			if(huffman_only)
				deflater = new Deflater(Deflater.HUFFMAN_ONLY);	
			else
				deflater = new Deflater(Deflater.BEST_COMPRESSION);	
			
	    	deflater.setInput(delta_strings, 0, delta_array_length);
	    	deflater.finish();
	    	zipped_length = deflater.deflate(zipped_strings);
	    	deflater.end();
			
	    	ratio = zipped_length * 8;
	    	ratio /= block_pixel_length;
	    	//System.out.println("The compression rate for zipped delta strings is " + String.format("%.4f", ratio));
	    	//System.out.println();
	    	
			if(compress)
			{
				double zero_one_ratio = xdim * ydim;
				
				// Subtract the missing stop bits from the max codes.
				int min_value = Integer.MAX_VALUE;
				for(int i = 0; i < histogram.length; i++)
					if(histogram[i] < min_value)
						min_value = histogram[i];
				zero_one_ratio -= min_value;
				
				zero_one_ratio  /= delta_length;
				
				// This is a precise number, but it's also important how the numbers are arranged.
				int compressed_delta_length = 0;
				
				// No clear cut off point where compressing zero strings will work, but it definitely won't work with less than half.
				// Will keep an eye on this.
				if(zero_one_ratio > .5)
				{
				    compressed_delta_length =  DeltaMapper.compressZeroStrings(delta_strings, delta_length, compressed_strings);
				    if(compressed_delta_length > delta_length)
				    {
				    	//System.out.println("Zero strings did not compress.");
				    	//System.out.println("The zero one ratio in the bit string is " + String.format("%.4f", zero_one_ratio));
				    }
				}
				else
				{
				    compressed_delta_length =  DeltaMapper.compressOneStrings(delta_strings, delta_length, compressed_strings);
				    if(compressed_delta_length > delta_length)
				    {
				    	//System.out.println("One strings did not compress.");
				    	//System.out.println("The zero one ratio in the bit string is " + String.format("%.4f", zero_one_ratio));
				    }
				}
				//System.out.println("Compressed delta length is " + compressed_delta_length);
				ratio  = compressed_delta_length;
				ratio /= block_pixel_length;
				//System.out.println("The compression rate for compressed delta strings is " + String.format("%.4f", ratio));	
				compressed_blue_string_rate = ratio;
				
				int compressed_array_length = compressed_delta_length / 8;
				if(compressed_delta_length %8 != 0)
					compressed_array_length++;
				
				if(huffman_only)
					deflater = new Deflater(Deflater.HUFFMAN_ONLY);
				else
					deflater = new Deflater(Deflater.BEST_COMPRESSION);
				deflater.setInput(compressed_strings, 0, compressed_array_length);
			    deflater.finish();
			    zipped_length = deflater.deflate(zipped_strings);
			    deflater.end();
			    
			    ratio = zipped_length * 8;
			    ratio /= block_pixel_length;
			    //System.out.println("The compression rate for zipped compressed delta strings is " + String.format("%.4f", ratio));
			    //System.out.println();
			}
			//System.out.println();
			
		    //System.out.println("Red:");
		    block = DeltaMapper.extract(shifted_red, xdim, x_offset, y_offset, block_xdim, block_ydim);
		     
		    init_value       = block[0];
		    delta            = DeltaMapper.getDeltasFromValues(block, block_xdim, block_ydim, init_value);
		    
		    int red_delta_sum = 0;
		    if(delta[0] == 0)
		    {
		    	//System.out.println("Delta type is horizontal.");
		    	red_delta_sum =  DeltaMapper.getHorizontalDeltaSum(delta, block_xdim, block_ydim);
		    }
		    else
		    {
		    	//System.out.println("Delta type is vertical.");
		    	red_delta_sum =  DeltaMapper.getVerticalDeltaSum(delta, block_xdim, block_ydim);
		    }
		    histogram_list   = DeltaMapper.getHistogram(delta);
			delta_min        = (int)histogram_list.get(0);
			histogram        = (int[])histogram_list.get(1);
			delta_random_lut = DeltaMapper.getRandomTable(histogram); 
			delta_bytes[0]   = 0;
			for(int i = 1; i < delta.length; i++)
			{
			    delta[i]      -= delta_min;
			    delta_bytes[i] = (byte)delta[i];
			}
			if(huffman_only)
				deflater = new Deflater(Deflater.HUFFMAN_ONLY);
			else
				deflater = new Deflater(Deflater.BEST_COMPRESSION);
			deflater.setInput(delta_bytes, 0, block_xdim * block_ydim);
	    	deflater.finish();
	    	zipped_length = deflater.deflate(zipped_strings);
	    	deflater.end();
			
	    	ratio = zipped_length * 8;
	    	ratio /= block_pixel_length;
	    	
	    	//System.out.println("The compression rate for zipped delta ints is " + String.format("%.4f", ratio));
			
			delta_length = 0;
			delta_length  = DeltaMapper.packStrings2(delta, delta_random_lut, delta_strings);
			ratio = delta_length;
			ratio /= block_pixel_length;
			//System.out.println("The compression rate for delta strings is " + String.format("%.4f", ratio));
			
			double red_string_rate = ratio;
			double compressed_red_string_rate = 0;
			
			delta_array_length = delta_length / 8;
			if(delta_length % 8 != 0)
				delta_array_length++;
			
			if(huffman_only)
				deflater = new Deflater(Deflater.HUFFMAN_ONLY);	
			else
				deflater = new Deflater(Deflater.BEST_COMPRESSION);	
			
	    	deflater.setInput(delta_strings, 0, delta_array_length);
	    	deflater.finish();
	    	zipped_length = deflater.deflate(zipped_strings);
	    	deflater.end();
			
	    	ratio = zipped_length * 8;
	    	ratio /= block_pixel_length;
	    	//System.out.println("The compression rate for zipped delta strings is " + String.format("%.4f", ratio));
	    	//System.out.println();
	    	
			if(compress)
			{
				double zero_one_ratio = xdim * ydim;
				
				// Subtract the missing stop bits from the max codes.
				int min_value = Integer.MAX_VALUE;
				for(int i = 0; i < histogram.length; i++)
					if(histogram[i] < min_value)
						min_value = histogram[i];
				zero_one_ratio -= min_value;
				
				zero_one_ratio  /= delta_length;
				
				// This is a precise number, but it's also important how the numbers are arranged.
				int compressed_delta_length = 0;
				
				// No clear cut off point where compressing zero strings will work, but it definitely won't work with less than half.
				// Will keep an eye on this.
				if(zero_one_ratio > .5)
				{
				    compressed_delta_length =  DeltaMapper.compressZeroStrings(delta_strings, delta_length, compressed_strings);
				    if(compressed_delta_length > delta_length)
				    {
				    	//System.out.println("Zero strings did not compress.");
				    	//System.out.println("The zero one ratio in the bit string is " + String.format("%.4f", zero_one_ratio));
				    }
				}
				else
				{
				    compressed_delta_length =  DeltaMapper.compressOneStrings(delta_strings, delta_length, compressed_strings);
				    if(compressed_delta_length > delta_length)
				    {
				    	//System.out.println("One strings did not compress.");
				    	//System.out.println("The zero one ratio in the bit string is " + String.format("%.4f", zero_one_ratio));
				    }
				}
				//System.out.println("Compressed delta length is " + compressed_delta_length);
				ratio  = compressed_delta_length;
				ratio /= block_pixel_length;
				//System.out.println("The compression rate for compressed delta strings is " + String.format("%.4f", ratio));	
				compressed_red_string_rate = ratio;
				
				int compressed_array_length = compressed_delta_length / 8;
				if(compressed_delta_length %8 != 0)
					compressed_array_length++;
				
				if(huffman_only)
					deflater = new Deflater(Deflater.HUFFMAN_ONLY);
				else
					deflater = new Deflater(Deflater.BEST_COMPRESSION);
				deflater.setInput(compressed_strings, 0, compressed_array_length);
			    deflater.finish();
			    zipped_length = deflater.deflate(zipped_strings);
			    deflater.end();
			    
			    ratio = zipped_length * 8;
			    ratio /= block_pixel_length;
			    //System.out.println("The compression rate for zipped compressed delta strings is " + String.format("%.4f", ratio));
			    //System.out.println();
			}
			//System.out.println();
			
			
			//System.out.println("Blue-green:");
			int [] shifted_blue_green = DeltaMapper.getDifference(shifted_blue, shifted_green);
	        int blue_green_min = 0;
		    for(int i = 0; i < xdim * ydim; i++)
		    	if(shifted_blue_green[i] < blue_green_min)
	            	blue_green_min = shifted_blue_green[i];
		    for(int i = 0; i < xdim * ydim; i++)
		    {
		    	shifted_blue_green[i] -= blue_green_min;
		    }
		    
		    block = DeltaMapper.extract(shifted_blue_green, xdim, x_offset, y_offset, block_xdim, block_ydim);
		     
		    init_value         = block[0];
		    delta              = DeltaMapper.getDeltasFromValues(block, block_xdim, block_ydim, init_value);
		    
		    int blue_green_delta_sum = 0;
		    if(delta[0] == 0)
		    {
		    	//System.out.println("Delta type is horizontal.");
		    	blue_green_delta_sum =  DeltaMapper.getHorizontalDeltaSum(delta, block_xdim, block_ydim);
		    }
		    else
		    {
		    	//System.out.println("Delta type is vertical.");
		    	blue_green_delta_sum =  DeltaMapper.getVerticalDeltaSum(delta, block_xdim, block_ydim);
		    }
		    histogram_list   = DeltaMapper.getHistogram(delta);
			delta_min        = (int)histogram_list.get(0);
			histogram        = (int[])histogram_list.get(1);
			delta_random_lut = DeltaMapper.getRandomTable(histogram); 
			delta_bytes[0]   = 0;
			for(int i = 1; i < delta.length; i++)
			{
			    delta[i]      -= delta_min;
			    delta_bytes[i] = (byte)delta[i];
			}
			if(huffman_only)
				deflater = new Deflater(Deflater.HUFFMAN_ONLY);
			else
				deflater = new Deflater(Deflater.BEST_COMPRESSION);
			
			deflater.setInput(delta_bytes, 0, block_xdim * block_ydim);
	    	deflater.finish();
	    	zipped_length = deflater.deflate(zipped_strings);
	    	deflater.end();
			
	    	ratio = zipped_length * 8;
	    	ratio /= block_pixel_length;
	    	
	    	//System.out.println("The compression rate for zipped delta ints is " + String.format("%.4f", ratio));
			
			delta_length = 0;
			delta_length  = DeltaMapper.packStrings2(delta, delta_random_lut, delta_strings);
			ratio = delta_length;
			ratio /= block_pixel_length;
			//System.out.println("The compression rate for delta strings is " + String.format("%.4f", ratio));
			double blue_green_string_rate = ratio;
			double compressed_blue_green_string_rate = 0;
			
			// Getting the number of bytes from the number of bits.
			delta_array_length = delta_length / 8;
			if(delta_length % 8 != 0)
				delta_array_length++;
			
			if(huffman_only)
				deflater = new Deflater(Deflater.HUFFMAN_ONLY);	
			else
				deflater = new Deflater(Deflater.BEST_COMPRESSION);	
			
	    	deflater.setInput(delta_strings, 0, delta_array_length);
	    	deflater.finish();
	    	zipped_length = deflater.deflate(zipped_strings);
	    	deflater.end();
	    	ratio = zipped_length * 8;
	    	ratio /= block_pixel_length;
	    	//System.out.println("The compression rate for zipped delta strings is " + String.format("%.4f", ratio));
	    	//System.out.println();
	    	
			if(compress)
			{
				double zero_one_ratio = xdim * ydim;
				
				int min_value = Integer.MAX_VALUE;
				for(int i = 0; i < histogram.length; i++)
					if(histogram[i] < min_value)
						min_value = histogram[i];
				zero_one_ratio -= min_value;
				
				zero_one_ratio  /= delta_length;
				
				
				int compressed_delta_length = 0;
				
				if(zero_one_ratio > .5)
				{
				    compressed_delta_length =  DeltaMapper.compressZeroStrings(delta_strings, delta_length, compressed_strings);
				    if(compressed_delta_length > delta_length)
				    {
				    	//System.out.println("Zero strings did not compress.");
				    	//System.out.println("The zero one ratio in the bit string is " + String.format("%.4f", zero_one_ratio));
				    }
				}
				else
				{
				    compressed_delta_length =  DeltaMapper.compressOneStrings(delta_strings, delta_length, compressed_strings);
				    if(compressed_delta_length > delta_length)
				    {
				    	//System.out.println("One strings did not compress.");
				    	//System.out.println("The zero one ratio in the bit string is " + String.format("%.4f", zero_one_ratio));
				    }
				}
				ratio  = compressed_delta_length;
				ratio /= block_pixel_length;
				//System.out.println("The compression rate for compressed delta strings is " + String.format("%.4f", ratio));	
				compressed_blue_green_string_rate = ratio;
				
				int compressed_array_length = compressed_delta_length / 8;
				if(compressed_delta_length %8 != 0)
					compressed_array_length++;
				
				if(huffman_only)
					deflater = new Deflater(Deflater.HUFFMAN_ONLY);
				else
					deflater = new Deflater(Deflater.BEST_COMPRESSION);
				deflater.setInput(compressed_strings, 0, compressed_array_length);
			    deflater.finish();
			    zipped_length = deflater.deflate(zipped_strings);
			    deflater.end();
			    
			    ratio = zipped_length * 8;
			    ratio /= block_pixel_length;
			    //System.out.println("The compression rate for zipped compressed delta strings is " + String.format("%.4f", ratio));
			    //System.out.println();
			}
			//System.out.println();
		    
			//System.out.println("Red-green:");
			int [] shifted_red_green = DeltaMapper.getDifference(shifted_red, shifted_green);
	        int red_green_min = 0;
		    for(int i = 0; i < xdim * ydim; i++)
		    	if(shifted_red_green[i] < red_green_min)
		    		red_green_min = shifted_red_green[i];
		    for(int i = 0; i < xdim * ydim; i++)
		    {
		    	shifted_red_green[i] -= red_green_min;
		    }
		    
		    block = DeltaMapper.extract(shifted_red_green, xdim, x_offset, y_offset, block_xdim, block_ydim);
		     
		    init_value         = block[0];
		    delta              = DeltaMapper.getDeltasFromValues(block, block_xdim, block_ydim, init_value);
		    
		    int red_green_delta_sum = 0;
		    if(delta[0] == 0)
		    {
		    	//System.out.println("Delta type is horizontal.");
		    	red_green_delta_sum =  DeltaMapper.getHorizontalDeltaSum(delta, block_xdim, block_ydim);
		    }
		    else
		    {
		    	//System.out.println("Delta type is vertical.");
		    	red_green_delta_sum =  DeltaMapper.getVerticalDeltaSum(delta, block_xdim, block_ydim);
		    }
			
		    histogram_list   = DeltaMapper.getHistogram(delta);
			delta_min        = (int)histogram_list.get(0);
			histogram        = (int[])histogram_list.get(1);
			delta_random_lut = DeltaMapper.getRandomTable(histogram); 
			delta_bytes[0]   = 0;
			for(int i = 1; i < delta.length; i++)
			{
			    delta[i]      -= delta_min;
			    delta_bytes[i] = (byte)delta[i];
			}
			if(huffman_only)
				deflater = new Deflater(Deflater.HUFFMAN_ONLY);
			else
				deflater = new Deflater(Deflater.BEST_COMPRESSION);
			
			deflater.setInput(delta_bytes, 0, block_xdim * block_ydim);
	    	deflater.finish();
	    	zipped_length = deflater.deflate(zipped_strings);
	    	deflater.end();
			
	    	ratio = zipped_length * 8;
	    	ratio /= block_pixel_length;
	    	
	    	//System.out.println("The compression rate for zipped delta ints is " + String.format("%.4f", ratio));
			
			delta_length = 0;
			delta_length  = DeltaMapper.packStrings2(delta, delta_random_lut, delta_strings);
			ratio = delta_length;
			ratio /= block_pixel_length;
			//System.out.println("The compression rate for delta strings is " + String.format("%.4f", ratio));
			double red_green_string_rate = ratio;
			double compressed_red_green_string_rate = 0;
			
			
			// Getting the number of bytes from the number of bits.
			delta_array_length = delta_length / 8;
			if(delta_length % 8 != 0)
				delta_array_length++;
			
			if(huffman_only)
				deflater = new Deflater(Deflater.HUFFMAN_ONLY);	
			else
				deflater = new Deflater(Deflater.BEST_COMPRESSION);	
			
	    	deflater.setInput(delta_strings, 0, delta_array_length);
	    	deflater.finish();
	    	zipped_length = deflater.deflate(zipped_strings);
	    	deflater.end();
			
	    	ratio = zipped_length * 8;
	    	ratio /= block_pixel_length;
	    	//System.out.println("The compression rate for zipped delta strings is " + String.format("%.4f", ratio));
	    	//System.out.println();
	    	
			if(compress)
			{
				double zero_one_ratio = xdim * ydim;
				int min_value = Integer.MAX_VALUE;
				for(int i = 0; i < histogram.length; i++)
					if(histogram[i] < min_value)
						min_value = histogram[i];
				zero_one_ratio -= min_value;
				zero_one_ratio  /= delta_length;
		        int compressed_delta_length =  0;
				if(zero_one_ratio > .5)
				{
				    compressed_delta_length =  DeltaMapper.compressZeroStrings(delta_strings, delta_length, compressed_strings);
				    if(compressed_delta_length > delta_length)
				    {
				    	//System.out.println("Zero strings did not compress.");
				    	//System.out.println("The zero one ratio in the bit string is " + String.format("%.4f", zero_one_ratio));
				    }
				}
				else
				{
				    compressed_delta_length =  DeltaMapper.compressOneStrings(delta_strings, delta_length, compressed_strings);
				    if(compressed_delta_length > delta_length)
				    {
				    	//System.out.println("One strings did not compress.");
				    	//System.out.println("The zero one ratio in the bit string is " + String.format("%.4f", zero_one_ratio));
				    }
				}
				ratio  = compressed_delta_length;
				ratio /= block_pixel_length;
				//System.out.println("The compression rate for compressed delta strings is " + String.format("%.4f", ratio));	
					
				
				int compressed_array_length = compressed_delta_length / 8;
				if(compressed_delta_length %8 != 0)
					compressed_array_length++;
				
				if(huffman_only)
					deflater = new Deflater(Deflater.HUFFMAN_ONLY);
				else
					deflater = new Deflater(Deflater.BEST_COMPRESSION);
				deflater.setInput(compressed_strings, 0, compressed_array_length);
			    deflater.finish();
			    zipped_length = deflater.deflate(zipped_strings);
			    deflater.end();
			    
			    ratio = zipped_length * 8;
			    ratio /= block_pixel_length;
			    //System.out.println("The compression rate for zipped compressed delta strings is " + String.format("%.4f", ratio));
			    //System.out.println();
			}
			//System.out.println();
		    
			//System.out.println("Red-blue:");
			int [] shifted_red_blue = DeltaMapper.getDifference(shifted_red, shifted_blue);
	        int red_blue_min = 0;
		    for(int i = 0; i < xdim * ydim; i++)
		    	if(shifted_red_blue[i] < red_blue_min)
		    		red_blue_min = shifted_red_blue[i];
		    for(int i = 0; i < xdim * ydim; i++)
		    {
		    	shifted_red_blue[i] -= red_blue_min;
		    }
		    
		    block = DeltaMapper.extract(shifted_red_blue, xdim, x_offset, y_offset, block_xdim, block_ydim);
		     
		    init_value         = block[0];
		    delta              = DeltaMapper.getDeltasFromValues(block, block_xdim, block_ydim, init_value);
		    
		    int red_blue_delta_sum = 0;
		    if(delta[0] == 0)
		    {
		    	//System.out.println("Delta type is horizontal.");
		    	red_blue_delta_sum =  DeltaMapper.getHorizontalDeltaSum(delta, block_xdim, block_ydim);
		    }
		    else
		    {
		    	//System.out.println("Delta type is vertical.");
		    	red_blue_delta_sum =  DeltaMapper.getVerticalDeltaSum(delta, block_xdim, block_ydim);
		    }
			
		    histogram_list   = DeltaMapper.getHistogram(delta);
			delta_min        = (int)histogram_list.get(0);
			histogram        = (int[])histogram_list.get(1);
			delta_random_lut = DeltaMapper.getRandomTable(histogram); 
			delta_bytes[0]   = 0;
			for(int i = 1; i < delta.length; i++)
			{
			    delta[i]      -= delta_min;
			    delta_bytes[i] = (byte)delta[i];
			}
			if(huffman_only)
				deflater = new Deflater(Deflater.HUFFMAN_ONLY);
			else
				deflater = new Deflater(Deflater.BEST_COMPRESSION);
			
			deflater.setInput(delta_bytes, 0, block_xdim * block_ydim);
	    	deflater.finish();
	    	zipped_length = deflater.deflate(zipped_strings);
	    	deflater.end();
			
	    	ratio = zipped_length * 8;
	    	ratio /= block_pixel_length;
	    	
	    	//System.out.println("The compression rate for zipped delta ints is " + String.format("%.4f", ratio));
			
			delta_length = 0;
			delta_length  = DeltaMapper.packStrings2(delta, delta_random_lut, delta_strings);
			ratio = delta_length;
			ratio /= block_pixel_length;
			//System.out.println("The compression rate for delta strings is " + String.format("%.4f", ratio));
			double red_blue_string_rate = ratio;
			double compressed_red_blue_string_rate = 0;
			
			// Getting the number of bytes from the number of bits.
			delta_array_length = delta_length / 8;
			if(delta_length % 8 != 0)
				delta_array_length++;
			
			if(huffman_only)
				deflater = new Deflater(Deflater.HUFFMAN_ONLY);	
			else
				deflater = new Deflater(Deflater.BEST_COMPRESSION);	
			
	    	deflater.setInput(delta_strings, 0, delta_array_length);
	    	deflater.finish();
	    	zipped_length = deflater.deflate(zipped_strings);
	    	deflater.end();
			
	    	ratio = zipped_length * 8;
	    	ratio /= block_pixel_length;
	    	//System.out.println("The compression rate for zipped delta strings is " + String.format("%.4f", ratio));
	    	//System.out.println();
	    	
			if(compress)
			{
				double zero_one_ratio = xdim * ydim;
				int min_value = Integer.MAX_VALUE;
				for(int i = 0; i < histogram.length; i++)
					if(histogram[i] < min_value)
						min_value = histogram[i];
				zero_one_ratio -= min_value;
				zero_one_ratio  /= delta_length;
		        int compressed_delta_length =  0;
				if(zero_one_ratio > .5)
				{
				    compressed_delta_length =  DeltaMapper.compressZeroStrings(delta_strings, delta_length, compressed_strings);
				    if(compressed_delta_length > delta_length)
				    {
				    	//System.out.println("Zero strings did not compress.");
				    	//System.out.println("The zero one ratio in the bit string is " + String.format("%.4f", zero_one_ratio));
				    }
				}
				else
				{
				    compressed_delta_length =  DeltaMapper.compressOneStrings(delta_strings, delta_length, compressed_strings);
				    if(compressed_delta_length > delta_length)
				    {
				    	//System.out.println("One strings did not compress.");
				    	//System.out.println("The zero one ratio in the bit string is " + String.format("%.4f", zero_one_ratio));
				    }
				}
				ratio  = compressed_delta_length;
				ratio /= block_pixel_length;
				//System.out.println("The compression rate for compressed delta strings is " + String.format("%.4f", ratio));	
				compressed_red_blue_string_rate = ratio;	
				
				int compressed_array_length = compressed_delta_length / 8;
				if(compressed_delta_length %8 != 0)
					compressed_array_length++;
				
				if(huffman_only)
					deflater = new Deflater(Deflater.HUFFMAN_ONLY);
				else
					deflater = new Deflater(Deflater.BEST_COMPRESSION);
				deflater.setInput(compressed_strings, 0, compressed_array_length);
			    deflater.finish();
			    zipped_length = deflater.deflate(zipped_strings);
			    deflater.end();
			    
			    ratio = zipped_length * 8;
			    ratio /= block_pixel_length;
			    //System.out.println("The compression rate for zipped compressed delta strings is " + String.format("%.4f", ratio));
			    //System.out.println();
			}
			//System.out.println();
			
			int []    delta_sum =    new int[10];
			String [] delta_string = new String[10];
			double [] delta_rate =   new double[10];
			
			delta_sum[0] = blue_delta_sum       + green_delta_sum      + red_delta_sum;
			delta_sum[1] = blue_delta_sum       + red_green_delta_sum  + red_delta_sum;
			delta_sum[2] = blue_delta_sum       + blue_green_delta_sum + red_delta_sum;
			delta_sum[3] = blue_delta_sum       + green_delta_sum      + red_green_delta_sum;
			delta_sum[4] = blue_delta_sum       + blue_green_delta_sum + red_blue_delta_sum;
			delta_sum[5] = blue_green_delta_sum + green_delta_sum      + red_delta_sum;
			delta_sum[6] = blue_green_delta_sum + red_green_delta_sum  + red_delta_sum;
			delta_sum[7] = blue_green_delta_sum + green_delta_sum      + red_green_delta_sum;
			delta_sum[8] = red_blue_delta_sum   + green_delta_sum      + red_green_delta_sum;
			delta_sum[9] = red_blue_delta_sum   + red_green_delta_sum  + red_delta_sum;
			
			delta_string[0] = new String("blue, green, and red.");
			delta_string[1] = new String("blue, red-green, and red.");
			delta_string[2] = new String("blue, blue-green, and red.");
			delta_string[3] = new String("blue, blue-green, and red-green.");
			delta_string[4] = new String("blue, blue-green, and red-blue.");
			delta_string[5] = new String("blue-green, green, and red.");
			delta_string[6] = new String("blue-green, red-green, and red.");
			delta_string[7] = new String("blue-green, green, and red-green.");
			delta_string[8] = new String("red-blue, green, and red-green.");
			delta_string[9] = new String("red-blue, red-green, and red.");
			
			delta_rate[0] = (compressed_blue_string_rate + compressed_green_string_rate + compressed_red_string_rate) / 3;
			delta_rate[1] = (compressed_blue_string_rate + compressed_red_green_string_rate + compressed_red_string_rate) / 3;
			delta_rate[2] = (compressed_blue_string_rate + compressed_blue_green_string_rate + compressed_red_string_rate) / 3;
			delta_rate[3] = (compressed_blue_string_rate + compressed_blue_green_string_rate + compressed_red_green_string_rate) / 3;
			delta_rate[4] = (compressed_blue_string_rate + compressed_blue_green_string_rate + compressed_red_blue_string_rate) / 3;
			delta_rate[5] = (compressed_blue_green_string_rate + compressed_green_string_rate + compressed_red_string_rate) / 3;
			delta_rate[6] = (compressed_blue_green_string_rate + compressed_red_green_string_rate + compressed_red_string_rate) / 3;
			delta_rate[7] = (compressed_blue_green_string_rate + compressed_green_string_rate + compressed_red_green_string_rate) / 3;
			delta_rate[8] = (compressed_red_blue_string_rate + compressed_green_string_rate + compressed_red_green_string_rate) / 3;
			delta_rate[9] = (compressed_red_blue_string_rate + compressed_red_green_string_rate + compressed_red_string_rate) / 3;
			
			int min_index = 0;
			int min_value = Integer.MAX_VALUE;
			for(int i = 0; i < 10; i++)
			{
				if(delta_sum[i] < min_value)
				{
				    min_value = delta_sum[0];
				    min_index = i;
				}
			}
			System.out.println("A set of channels with the lowest delta sum is " + delta_string[min_index]);
			System.out.println("The compression rate is " + String.format("%.4f", delta_rate[min_index]));
			
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