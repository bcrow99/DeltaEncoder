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
	ImageCanvas   image_canvas;
	
	int           xdim, ydim;
	String        filename;
	
	int [] original_pixel;
	int [] alpha;
	int [] red;
	int [] green;
	int [] blue;
	
	int     pixel_shift  = 3;
	int     pixel_length = 0;
	boolean huffman_only = true;
	boolean compress     = false;
	long    file_length  = 0;
	
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
		    System.out.println("File ratio is " + String.format("%.4f", file_ratio));
		    System.out.println();
		    
		    for(int i = 0; i < xdim * ydim; i++)
		    {
		        shifted_blue[i]  = blue[i]  >> pixel_shift;
		        shifted_green[i] = green[i] >> pixel_shift;
			    shifted_red[i]   = red[i]   >> pixel_shift; 
		    }
		    
		    
		    int init_value           = shifted_green[0];
		    int[] delta              = DeltaMapper.getDeltasFromValues(shifted_green, xdim, ydim, init_value);
			
		    int green_delta_sum = 0;
		    if(delta[0] == 0)
		    {
		    	green_delta_sum = DeltaMapper.getHorizontalDeltaSum(delta, xdim, ydim);
		    	//System.out.println("The green delta sum is " + green_delta_sum);
		    }
		    else if(delta[0] == 1)
		    {
		    	green_delta_sum = DeltaMapper.getVerticalDeltaSum(delta, xdim, ydim);
		    	//System.out.println("The green delta sum is " + green_delta_sum);
		    }
		    else
		    	System.out.println("Undefined delta type.");
		    
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
	    	
	    	System.out.println("The ratio of zipped green delta ints to pixel bits is " + String.format("%.4f", ratio));
			
		    
		    
		    
		    
			int delta_length = 0;
			delta_length  = DeltaMapper.packStrings2(delta, delta_random_lut, delta_strings);
			//delta_length  = DeltaMapper.packStrings(delta, delta_random_lut, delta_strings);
			ratio = delta_length;
			ratio /= pixel_length;
			System.out.println("The ratio of green delta packed string bits to pixel bits is " + String.format("%.4f", ratio));
			
			// Getting the number of bytes from the number of bits.
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
	    	System.out.println("The compression rate for zipped green delta packed string bits is " + String.format("%.4f", ratio));
	    	
			if(compress)
			{
				// Padding the input to enable recursion, which corrupts trailing bits in string.
				int compressed_delta_length =  DeltaMapper.compressStrings(delta_strings, delta_length + 8, compressed_strings);
				ratio  = compressed_delta_length;
				ratio /= pixel_length;
				System.out.println("The compression rate for compressed packed delta string bits is " + String.format("%.4f", ratio));	
				
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
			    System.out.println("The compression rate for zipped compressed packed delta string bits is " + String.format("%.4f", ratio));
			}
			System.out.println();
	          
		    
			int block_xdim = xdim / 4;
		    int block_ydim = ydim / 4;
		    int block_pixel_length = block_xdim * block_ydim * 8;
		    int [] block = DeltaMapper.extract(shifted_green, xdim, 0, 0, block_xdim, block_ydim);
		     
		    init_value         = block[0];
		    delta              = DeltaMapper.getDeltasFromValues(block, block_xdim, block_ydim, init_value);
			
		    int block_delta_sum = 0;
		    if(delta[0] == 0)
		    {
		    	block_delta_sum = DeltaMapper.getHorizontalDeltaSum(delta, block_xdim, block_ydim);
		    	//System.out.println("The block delta sum is " + block_delta_sum);
		    }
		    else if(delta[0] == 1)
		    {
		    	block_delta_sum = DeltaMapper.getVerticalDeltaSum(delta, block_xdim, block_ydim);
		    	//System.out.println("The block delta sum is " + block_delta_sum);
		    }
		    else
		    	System.out.println("Undefined delta type.");
		    
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
	    	
	    	System.out.println("The compression rate for zipped green block delta ints is " + String.format("%.4f", ratio));
			
			delta_length = 0;
			delta_length  = DeltaMapper.packStrings2(delta, delta_random_lut, delta_strings);
			ratio = delta_length;
			ratio /= block_pixel_length;
			System.out.println("The compression rate for green block packed delta string bits to pixel bits is " + String.format("%.4f", ratio));
			
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
	    	System.out.println("The compression rate for zipped green block packed delta strings is " + String.format("%.4f", ratio));
	    	
			if(compress)
			{
				// Padding the input to enable recursion, which corrupts trailing bits in string.
				int compressed_delta_length =  DeltaMapper.compressStrings(delta_strings, delta_length + 8, compressed_strings);
				ratio  = compressed_delta_length;
				ratio /= block_pixel_length;
				System.out.println("The compression rate for compressed packed delta string bits is " + String.format("%.4f", ratio));	
				
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
			    System.out.println("The compression rate for zipped compressed packed delta string bits is " + String.format("%.4f", ratio));
			}
			System.out.println();
		    
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