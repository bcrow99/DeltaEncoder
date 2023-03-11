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

public class Encoder
{
	BufferedImage original_image;
	BufferedImage image;
	JMenuItem     apply_item;
	
	JDialog       shift_dialog;
	JTextField    shift_value;
	
	ImageCanvas   image_canvas;
	
	int    xdim, ydim;
	String filename;
	
	int [] original_pixel;
	int [] alpha;
	int [] red;
	int [] green;
	int [] blue;
	
	byte   [] channel_compression_type, channel_bit_type;
	int    [] channel_init, channel_min;
	
	int    [] set_sum, channel_sum,channel_delta_min;
	double [] set_rate, channel_rate;
	String [] type_string;
	String [] set_string;
	String [] channel_string;
	int    [] shifted_blue, shifted_green, shifted_red;
	int    [] shifted_blue_green, shifted_red_green, shifted_red_blue;
	
	int    [][] compression_length;
	
	ArrayList channel_table, channel_data, channel_src;
	
	double  file_ratio;
	int     min_set_id = 0;
	int     pixel_shift = 3;
	long    file_length = 0;
	boolean initialized = false;
	
	public static void main(String[] args)
	{
		if (args.length != 1)
		{
			System.out.println("Usage: java Encoder <filename>");
			System.exit(0);
		}
	    //String prefix      = new String("");
	    String prefix       = new String("C:/Users/Brian Crowley/Desktop/");
		String filename     = new String(args[0]);
		String java_version = System.getProperty("java.version");
		String os           = System.getProperty("os.name");
		String os_version   = System.getProperty("os.version");
		String machine      = System.getProperty("os.arch");
		//System.out.println("Current java version is " + java_version);
		//System.out.println("Current os is " + os + " " + os_version + " on " + machine);
		//System.out.println("Image file is " + filename);
		Encoder encoder = new Encoder(prefix + filename);
	}

	public Encoder(String _filename)
	{
		filename = _filename;
		try
		{
			File file              = new File(filename);
			file_length            = file.length();
			original_image         = ImageIO.read(file);
			int raster_type        = original_image.getType();
			ColorModel color_model = original_image.getColorModel();
			int number_of_channels = color_model.getNumColorComponents();
			int number_of_bits     = color_model.getPixelSize();
			xdim                   = original_image.getWidth();
			ydim                   = original_image.getHeight();
			
		    original_pixel         = new int[xdim * ydim];
		    alpha                  = new int[xdim * ydim];
			blue                   = new int[xdim * ydim];
		    green                  = new int[xdim * ydim];
		    red                    = new int[xdim * ydim];
		    
		    // PNG files do not always have this raster type.
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
               
			    JFrame frame = new JFrame("Encoder");
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
				
				JMenuItem save_item      = new JMenuItem("Save");
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
			else
			{
				System.out.println("File raster type not supported.");
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
		int    [] new_alpha, new_blue, new_green, new_red, new_pixel;
		int       pixel_length;
		
		
		public ApplyHandler()
		{
			new_alpha    = new int[xdim * ydim]; 
		    new_blue     = new int[xdim * ydim];
		    shifted_blue = new int[xdim * ydim];
		     
		    new_green    = new int[xdim * ydim];
		    shifted_green = new int[xdim * ydim];
		     
		    new_red     = new int[xdim * ydim];
		    shifted_red = new int[xdim * ydim];
		    new_pixel   = new int[xdim * ydim];
		    
		   
		    // The 6 different kinds of compressions we intend to evaluate per channel.
		    // Huffman encoding usually offers some amount of compression unless
		    // the blocks are so small the extra overhead becomes significant.
		    type_string    = new String[7];
			type_string[0] = new String("strings");
			type_string[1] = new String("zipped strings");
			type_string[2] = new String("compressed strings");
			type_string[3] = new String("zipped compressed strings");
			type_string[4] = new String("zipped delta bytes");
			type_string[5] = new String("zipped pixel bytes");
			// If a frame is divided into blocks, there 
			// might be multiple types of compression.
			type_string[6] = new String("mixed");
		  
		    // The delta sum for each channel.
		    channel_sum  = new int[6];
		    
		    // The different minimum compression rates per channel.
		    channel_rate = new double[6];
		    
		    // The type of compression that produces the minimum result.
		    channel_compression_type = new byte[6];
		    
		    // What type of string being compressed.
		    channel_bit_type = new byte[6];
		    
		    // The lengths of the bit string produced for each channel by each method of compression.
		    compression_length = new int[6][6];
		    
		    // The look up tables used to pack/unpack strings.
		    channel_table = new ArrayList();
		    
		    // Different compression results.
		    channel_data = new ArrayList();
		    
		    channel_src  = new ArrayList();
		   
		    for(int i = 0; i < 6; i++)
		    {
		    	ArrayList data_list = new ArrayList();
		    	channel_data.add(data_list);
		    	channel_bit_type[i] = 0;
		    }
		    
		    // The input for the delta values.
		    channel_init = new int[6];
		    
		    // The value we subtract from difference 
		    // frame values so the least value is 0.
		    // Also keeping it for reference frames,
		    // but not subtracting it.
		    channel_min = new int[6];
		    
		    // The value we subtract from deltas so the
		    // least value is 0.
		    channel_delta_min = new int[6];
		    
		    channel_string    = new String[6];
		    channel_string[0] = new String("blue");
		    channel_string[1] = new String("green");
		    channel_string[2] = new String("red");
		    channel_string[3] = new String("blue-green");
		    channel_string[4] = new String("red-green");
		    channel_string[5] = new String("red-blue");
		    
		    // The delta sum for each set of channels that can produce an image.
		    set_sum       = new int[10];
		    set_rate      = new double[10];
		    set_string    = new String[10]; 
		    set_string[0] = new String("blue, green, and red.");
		    set_string[1] = new String("blue, red, and red-green.");
		    set_string[2] = new String("blue, red, and blue-green.");
		    set_string[3] = new String("blue, blue-green, and red-green.");
		    set_string[4] = new String("blue, blue-green, and red-blue.");
		    set_string[5] = new String("green, red, and blue-green.");
		    set_string[6] = new String("red, blue-green, and red-green.");
		    set_string[7] = new String("green, blue-green, and red-green.");
		    set_string[8] = new String("green, red-green, and red-blue.");
		    set_string[9] = new String("red, red-green, red-blue.");
		    
		    pixel_length = xdim * ydim * 8;
		    file_ratio   = file_length * 8;
		    file_ratio  /= pixel_length * 3;
		}
		
		public void actionPerformed(ActionEvent event)
		{
			// Clear all array lists.
			channel_src.clear();
		    channel_table.clear();
		    
		    for(int i = 0; i < 6; i++)
		    {
		    	ArrayList data_list = (ArrayList)channel_data.get(i);
		    	data_list.clear();
		    }
			
			System.out.println("Pixel shift is " + pixel_shift);
		    //System.out.println("File compression rate is " + String.format("%.4f", file_ratio));
		    System.out.println();
		    
		    for(int i = 0; i < xdim * ydim; i++)
		    {
		        shifted_blue[i]  = blue[i]  >> pixel_shift;
		        shifted_green[i] = green[i] >> pixel_shift;
			    shifted_red[i]   = red[i]   >> pixel_shift; 
		    }
		    
		    // The minimum reference channel value is usually 0,
		    // unless there's an unusual distribution of values.
		    // Not sure we really need to account for the minimum in the reference frames,
		    // and it might confuse the difference frames if we make different adjustments
		    // to different reference frames.  Probably why subtracting it worked before is 
		    // it's almost always 0. 
		    int blue_min  = Integer.MAX_VALUE;
		    int green_min = Integer.MAX_VALUE;
		    int red_min   = Integer.MAX_VALUE;
		    for(int i = 0; i < xdim * ydim; i++)
		    {
		        if(shifted_blue[i] < blue_min)
		            blue_min = shifted_blue[i];
		        if(shifted_green[i] < green_min)
		            green_min = shifted_green[i];
		        if(shifted_red[i] < red_min)
		            red_min = shifted_red[i];
		    }
		    channel_min[0]  = (short)blue_min;
		    channel_init[0] = (short)shifted_blue[0];
		    channel_min[1]  = (short)green_min;
		    channel_init[1] = (short)shifted_green[0];
		    channel_min[2]  = (short)red_min;
		    channel_init[2] = (short)shifted_red[0];
		    
		    
		    // The difference frames usually contain negative numbers,
		    // so we'll subtract the minimum to make them all positive.
		    // Might not be necessary.
		    shifted_blue_green = DeltaMapper.getDifference(shifted_blue, shifted_green);
	        int blue_green_min = 0;
		    for(int i = 0; i < xdim * ydim; i++)
		    	if(shifted_blue_green[i] < blue_green_min)
	            	blue_green_min = shifted_blue_green[i];
		    if(blue_green_min != 0)
		        for(int i = 0; i < xdim * ydim; i++)
		    	    shifted_blue_green[i] -= blue_green_min;
		    channel_min[3]  = (short)blue_green_min;
		    channel_init[3] = (short)shifted_blue_green[0]; 
		    
		    
		    shifted_red_green = DeltaMapper.getDifference(shifted_red, shifted_green);
	        int red_green_min = 0;
		    for(int i = 0; i < xdim * ydim; i++)
		    	if(shifted_red_green[i] < red_green_min)
		    		red_green_min = shifted_red_green[i];
		    if(red_green_min != 0)
		        for(int i = 0; i < xdim * ydim; i++)
		    	    shifted_red_green[i] -= red_green_min;
		    channel_min[4]  = (short)red_green_min;
		    channel_init[4] = (short)shifted_red_green[0]; 
		    
		    
		    shifted_red_blue = DeltaMapper.getDifference(shifted_red, shifted_blue);
	        int red_blue_min = 0;
		    for(int i = 0; i < xdim * ydim; i++)
		    	if(shifted_red_blue[i] < red_blue_min)
		    		red_blue_min = shifted_red_blue[i];
		    if(red_blue_min != 0)
		        for(int i = 0; i < xdim * ydim; i++)
		    	    shifted_red_blue[i] -= red_blue_min;
		    channel_min[5]  = (short)red_blue_min;
		    channel_init[5] = (short)shifted_red_blue[0]; 
		    
		    
		    channel_src.add(shifted_blue);
		    channel_src.add(shifted_green);
		    channel_src.add(shifted_red);
		    channel_src.add(shifted_blue_green);
		    channel_src.add(shifted_red_green);
		    channel_src.add(shifted_red_blue);
		    
		    for(int i = 0; i < 6; i++)
		    {
		    	int [] src = (int [])channel_src.get(i);
		    	int [] delta = new int[xdim * ydim];
			    delta = DeltaMapper.getDeltasFromValues(src, xdim, ydim, channel_init[i]);
			    if(delta[0] == 0)
			    	channel_sum[i] = DeltaMapper.getHorizontalDeltaSum(delta, xdim, ydim);
			    else
			    	channel_sum[i] = DeltaMapper.getVerticalDeltaSum(delta, xdim, ydim);
			    ArrayList histogram_list       = DeltaMapper.getHistogram(delta);
			    channel_delta_min[i] = (int)histogram_list.get(0);
		
				int [] histogram            = (int[])histogram_list.get(1);
				int [] string_table = DeltaMapper.getRandomTable(histogram);
				channel_table.add(string_table);
				
				
				
				
				int    number_of_values = 0;
				int    predicted_bit_length = 0;
				double predicted_zero_one_ratio = 0;
				int    min_value = Integer.MAX_VALUE;
				
				for(int j = 0; j < histogram.length; j++)
				{
					number_of_values += histogram[j];
					if(histogram.length == 1)
					{
					    predicted_bit_length = number_of_values;
					    predicted_zero_one_ratio = 1.;
					}
					else if(histogram.length == 2)
					{
					    predicted_bit_length += histogram[j];
					    if(j == 1)
					    {
					    	if(histogram[0] > histogram[1])
					    	    predicted_zero_one_ratio = histogram[0];
					    	else
					    		predicted_zero_one_ratio = histogram[1];
					    	predicted_zero_one_ratio /= number_of_values;
					    }
					}
					else
					{
						int k = string_table[j];
					    if(j < histogram.length - 1)	
					    {
					        predicted_bit_length += histogram[j] * (k + 1); 
					        if(histogram[j] < min_value)
					        	min_value = histogram[j];
					    }
					    else
					    {
					    	predicted_bit_length += histogram[j] * (k + 1);
					    	 if(histogram[j] < min_value)
						        	min_value = histogram[j];
					    	
					    	predicted_zero_one_ratio  = number_of_values;
					    	predicted_zero_one_ratio -= min_value;
					    	
					    	predicted_zero_one_ratio /= predicted_bit_length;
					    	// Don't understand why the bit length always 
					    	// seems to be off by a little bit.
					    	// Works well enough to be useful.
					    	// We can predict the (approximate) length of a bit string 
					    	// and whether it's likely to compress further.
					    }
					}
				}
				
				
				
				
				//System.out.println("Predicted bit length of packed strings is " + predicted_bit_length);
				//System.out.println("Predicted zero one ratio is " + String.format("%.2f", predicted_zero_one_ratio));
				if(number_of_values != xdim * ydim)
					System.out.println("Number of values did not agree with image_dimensions.");
				
				
				
			
				
				ArrayList data_list = (ArrayList)channel_data.get(i);
				data_list.clear();
				
				for(int j = 1; j < delta.length; j++)
					delta[j] -= channel_delta_min[i];
				byte [] string = new byte[xdim * ydim * 4];
				compression_length[i][0] = DeltaMapper.packStrings2(delta, string_table, string);
			
				int string_array_length = compression_length[i][0] / 8;
				byte remainder = (byte)(compression_length[i][0] % 8);
				if(remainder != 0)
					string_array_length++;
				byte [] clipped_string = new byte[string_array_length + 1];
				for(int j = 0; j < string_array_length; j++)
					clipped_string[j] = string[j];
				if(remainder == 0)
					clipped_string[string_array_length] = 0;	
				else
				{
					 byte bits                           = 8;
					 int null_bits                       = bits - remainder;
					 clipped_string[string_array_length] = (byte)null_bits;
				}
				data_list.add(clipped_string);
				//*********************************************************************
				
				byte [] zipped_string = new byte[string.length * 2];
			    Deflater deflater = new Deflater(Deflater.HUFFMAN_ONLY);	
		    	deflater.setInput(clipped_string);
		    	deflater.finish();
		    	int zipped_string_length = deflater.deflate(zipped_string);
		    	deflater.end();
		    	clipped_string = new byte[zipped_string_length];
		    	for(int j = 0; j < zipped_string_length; j++)
		    		clipped_string[j] = zipped_string[j];
		    	compression_length[i][1] = zipped_string_length * 8;
		    	data_list.add(clipped_string);
		    	//*********************************************************************
		    	
		    	double zero_one_ratio = xdim * ydim;
		        if(histogram.length > 1)
		        {
					min_value = Integer.MAX_VALUE;
					for(int j = 0; j < histogram.length; j++)
						 if(histogram[j] < min_value)
							min_value = histogram[j];
					zero_one_ratio -= min_value;
		        }	
			    zero_one_ratio  /= compression_length[i][0];
				byte[] compressed_string = new byte[string.length * 2];	
				
				// Odd 0's at the end of the output double themselves in the recursive process,
				// although a single iteration can be managed accurately.  The original length of the bitstring might be intact.
				
				// As long as you use the original length instead of the length returned by the decompression functions everything works,
				// although the length returned by the decompression functions should be close and sometimes the same.  Not
				// sure if it's something that ever screws things up.  The limit of the difference should be the number of iterations of
				// the bitwise compression.
				
				if(zero_one_ratio > .5)
				{
					compression_length[i][2] = DeltaMapper.compressZeroStrings(string, compression_length[i][0], compressed_string);
					channel_bit_type[i] = 0;
				}
				else
				{
					compression_length[i][2] =  DeltaMapper.compressOneStrings(string, compression_length[i][0], compressed_string);
					channel_bit_type[i] = 1;
				}
				
				string_array_length = compression_length[i][2] / 8;
				remainder = (byte)(compression_length[i][2] % 8);
				if(remainder != 0)
					string_array_length++;
				clipped_string = new byte[string_array_length + 1];
				for(int j = 0; j < string_array_length; j++)
					clipped_string[j] = compressed_string[j];
				if(remainder == 0)
					clipped_string[string_array_length] = 0;	
				else
				{
					 byte bits                           = 8;
					 int null_bits                       = bits - remainder;
					 clipped_string[string_array_length] = (byte)null_bits;
				}
				data_list.add(clipped_string);	
				//*********************************************************************
				
				zipped_string = new byte[clipped_string.length * 2];
				deflater = new Deflater(Deflater.BEST_COMPRESSION);
				deflater.setInput(clipped_string);
		    	deflater.finish();
		    	zipped_string_length = deflater.deflate(zipped_string);
		    	deflater.end();
		    	clipped_string = new byte[zipped_string_length];
		    	for(int j = 0; j < zipped_string_length; j++)
		    		clipped_string[j] = zipped_string[j];
		    	compression_length[i][3] = zipped_string_length * 8;
		    	
		    	data_list.add(clipped_string);
		    	//*********************************************************************
				
		    	byte [] image_bytes = new byte[xdim * ydim];
		    	
		    	
		    	int number_of_zero_values = 0;
	    		for(int j = 0; j < histogram.length; j++)
	    			if(histogram[j] == 0)
	    				number_of_zero_values++; 
		    	if(histogram.length < 257)
		    	{
		    		for(int j = 0; j < image_bytes.length; j++)
		    	    	image_bytes[j] = (byte)delta[j];
		    		deflater = new Deflater(Deflater.BEST_COMPRESSION);
		    	    deflater.setInput(image_bytes);
			    	deflater.finish();	
			    	zipped_string = new byte[image_bytes.length * 2];
			    	zipped_string_length = deflater.deflate(zipped_string);
			    	deflater.end();
			    	clipped_string = new byte[zipped_string_length];
			    	for(int j = 0; j < zipped_string_length; j++)
			    		clipped_string[j] = zipped_string[j];
			    	compression_length[i][4] = zipped_string_length * 8;
			    	data_list.add(clipped_string);
		    	}
		    	else
		    	{
		    		compression_length[i][4] = pixel_length;
		    		data_list.add(delta);
		    		
		    		System.out.println("Channel is " + channel_string[i]);
		    		System.out.println("Did not huffman encode deltas.");
		    		System.out.println();
		    	}	
		    		
		    	for(int j = 0; j < image_bytes.length; j++)
	    	    	image_bytes[j] = (byte)src[j];
		    	deflater = new Deflater(Deflater.BEST_COMPRESSION);
	    	    deflater.setInput(image_bytes);
		    	deflater.finish();	
		    	zipped_string = new byte[image_bytes.length * 2];
		    	zipped_string_length = deflater.deflate(zipped_string);
		    	deflater.end();
		    	clipped_string = new byte[zipped_string_length];
		    	for(int j = 0; j < zipped_string_length; j++)
		    		clipped_string[j] = zipped_string[j];
		    	compression_length[i][5] = zipped_string_length * 8;
		    	data_list.add(clipped_string);		
		    	
		    	int min_index = 0;
		    	min_value = compression_length[i][0];
		    	for(int j = 1; j < 6; j++)
		    	{
		    		if(compression_length[i][j] < min_value)
		    		{
		    			min_index = j;
		    			min_value = compression_length[i][j];
		    		}
		    	}
		    	
		    	channel_compression_type[i] = (byte)min_index;
		    	channel_rate[i]             = min_value;
		    	channel_rate[i]             /= pixel_length;
		    	
		    }
		    
			//System.out.println("************************************************************************");
		   
			set_sum[0] = channel_sum[0] + channel_sum[1] + channel_sum[2];
			set_sum[1] = channel_sum[0] + channel_sum[4] + channel_sum[2];
			set_sum[2] = channel_sum[0] + channel_sum[3] + channel_sum[2];
			set_sum[3] = channel_sum[0] + channel_sum[1] + channel_sum[4];
			set_sum[4] = channel_sum[0] + channel_sum[3] + channel_sum[5];
			set_sum[5] = channel_sum[3] + channel_sum[1] + channel_sum[2];
			set_sum[6] = channel_sum[3] + channel_sum[4] + channel_sum[2];
			set_sum[7] = channel_sum[3] + channel_sum[1] + channel_sum[4];
			set_sum[8] = channel_sum[5] + channel_sum[1] + channel_sum[4];
			set_sum[9] = channel_sum[5] + channel_sum[4] + channel_sum[2];
			
			set_rate[0] = (channel_rate[0] + channel_rate[1] + channel_rate[2]) / 3;
			set_rate[1] = (channel_rate[0] + channel_rate[4] + channel_rate[2]) / 3;
			set_rate[2] = (channel_rate[0] + channel_rate[3] + channel_rate[2]) / 3;
			set_rate[3] = (channel_rate[0] + channel_rate[1] + channel_rate[4]) / 3;
			set_rate[4] = (channel_rate[0] + channel_rate[3] + channel_rate[5]) / 3;
			set_rate[5] = (channel_rate[3] + channel_rate[1] + channel_rate[2]) / 3;
			set_rate[6] = (channel_rate[3] + channel_rate[4] + channel_rate[2]) / 3;
			set_rate[7] = (channel_rate[3] + channel_rate[1] + channel_rate[4]) / 3;
			set_rate[8] = (channel_rate[5] + channel_rate[1] + channel_rate[4]) / 3;
			set_rate[9] = (channel_rate[5] + channel_rate[4] + channel_rate[2]) / 3;
			
			int min_index     = 0;
			int min_delta_sum = Integer.MAX_VALUE;
			for(int i = 0; i < 10; i++)
			{
				if(set_sum[i] < min_delta_sum)
				{
				    min_delta_sum = set_sum[i];
				    min_index = i;
				}
			}
			
			min_set_id = min_index;
			System.out.println("A set with the lowest delta sum is " + set_string[min_index]);
			System.out.println("Set rate is " + String.format("%.4f", set_rate[min_index]));
			System.out.println();
			
			double min_delta_rate = Double.MAX_VALUE;
			for(int i = 0; i < 10; i++)
			{
				if(set_rate[i] < min_delta_rate)
				{
				    min_delta_rate = set_rate[i];
				    min_index = i;
				}
			}
			System.out.println("A set with the lowest compression ratio is " + set_string[min_index]);
			System.out.println("Set rate is " + String.format("%.4f", set_rate[min_index]));
			System.out.println();
			
			int [] channel = DeltaMapper.getChannels(min_set_id);
			
			double rate = 1;
			for(int i = 0; i < 3; i++)
			{
				int channel_id = channel[i];
				System.out.println(channel_string[channel_id] + ":");
				
				rate  = compression_length[channel_id][0];
				rate /= pixel_length;
				System.out.println("Packed string rate is " + String.format("%.4f", rate));
				
				rate  = compression_length[channel_id][1];
				rate /= pixel_length;
				System.out.println("Zipped string rate is " + String.format("%.4f", rate));
				
				rate  = compression_length[channel_id][2];
				rate /= pixel_length;
				System.out.println("Compressed string rate is " + String.format("%.4f", rate));
				
				rate  = compression_length[channel_id][3];
				rate /= pixel_length;
				System.out.println("Zipped compressed string rate is " + String.format("%.4f", rate));
				
				rate  = compression_length[channel_id][4];
				rate /= pixel_length;
				System.out.println("Zipped delta length is " + String.format("%.4f", rate));
				
				rate  = compression_length[channel_id][5];
				rate /= pixel_length;
				System.out.println("Zipped pixel length is " + String.format("%.4f", rate));
				
				System.out.println();
			}
			
	    	
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
		    
		    initialized = true;
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
	
	class SaveHandler implements ActionListener
	{
		public void actionPerformed(ActionEvent event)
		{
			if(!initialized)
				apply_item.doClick();
			
			System.out.println("Saving " + set_string[min_set_id]);
			System.out.println("Set rate  is " + String.format("%.2f", set_rate[min_set_id]));
			System.out.println("RGB rate  is " + String.format("%.2f", set_rate[0]));
			System.out.println("File rate is " + String.format("%.2f", file_ratio));
			System.out.println("Shift is     " + pixel_shift);
		    
			System.out.println();
			
			int channel[] = DeltaMapper.getChannels(min_set_id);
			System.out.println();
			
		    try
		    {
		        DataOutputStream out = new DataOutputStream(new FileOutputStream(new File("foo")));
		        
		        // Dimensions of frame.
		        out.writeShort(xdim);
		        out.writeShort(ydim);
		        
		        // Pixel shift
		        out.writeByte(pixel_shift);
		        
		        for(int i = 0; i < 3; i++)
		        {
		        	int j = channel[i];
		            
		        	System.out.println("Saving " + channel_string[j] + " channel.");
		        	
		        	// Channel 0-5.
		            out.writeByte(j);
		        
		            out.writeInt(channel_min[j]);
		            
		            // Init value for deltas.
		            out.writeInt(channel_init[j]);
		            //System.out.println("Init value for deltas is " + channel_init[j]);
		        
		            // Minimum_value for deltas.
		            out.writeInt(channel_delta_min[j]);
		            //System.out.println("Minimum value for deltas is " + channel_delta_min[j]);
		        
		            // Compression type 0-5.
		            out.writeByte(channel_compression_type[j]);
		            
		            // Bit type.
		            out.writeByte(channel_bit_type[j]);
		            
		            System.out.println("Type of compression is " + type_string[channel_compression_type[j]] + " bit type " + channel_bit_type[j]);
		            System.out.println("Compression rate is " + String.format("%.2f", channel_rate[j]));
		            System.out.println();
		            // The length of the table used for string packing/unpacking.
		            int [] string_table = (int[])channel_table.get(j);
		            out.writeShort(string_table.length);
		            //System.out.println("String table length is " + string_table.length);
		        
		            // The table itself.
		            for(int k = 0; k < string_table.length; k++)
		                out.writeInt(string_table[k]);
		       
		            // The lengths of the packed and compressed bitstrings.
		            out.writeInt(compression_length[j][0]);
		        	out.writeInt(compression_length[j][2]);
		            System.out.println();
		       
		            ArrayList data_list = (ArrayList)channel_data.get(j);
		            byte[] data = (byte[])data_list.get(channel_compression_type[j]);
		        
		            // The length of the data.
		            out.writeInt(data.length);
		        
		            // The data itself.
		            out.write(data, 0, data.length);
		        } 
		        out.flush();
		        out.close();
		    }
		    catch(Exception e)
		    {
			    System.out.println(e.toString());
		    }
		}
	}
}