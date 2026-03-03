import java.awt.*;
import java.awt.image.*;
import java.io.*;
import java.awt.event.*;
import java.awt.geom.AffineTransform;
import java.util.*;
import java.util.zip.*;
import java.util.zip.Deflater.*;
import javax.imageio.*;
import javax.swing.*;
import javax.swing.event.*;

public class SimpleWriter
{
	BufferedImage original_image;
	BufferedImage working_image;
	BufferedImage display_image;
	ImageCanvas image_canvas;
	int image_xdim, image_ydim;
	int canvas_xdim, canvas_ydim;
	int screen_xdim, screen_ydim;
	JMenuItem apply_item;
	String filename;
	int[] pixel;

	int pixel_quant    = 4;
	int pixel_shift    = 3;
	
	int correction     = 0;
	int min_set_id     = 0;
	
	int delta_type      = 5;
	
	boolean precompress = false;
	int deflate_type    = 0;
	
	double scale       = 1.;

	int[] set_sum, channel_sum;
	String[] set_string;
	String[] delta_type_string;
	String[] channel_string;

	int[] channel_init;
	int[] channel_min;
	int[] channel_delta_min;

	int[] channel_length;
	int[] channel_compressed_length;
	int[] channel_string_type;
	int[] number_of_segments;
	byte[] channel_iterations;
	
	
	int[] max_bytelength;
	int [] min_bytelength;

	long file_length;
	double file_compression_rate;
	JRadioButtonMenuItem[] delta_button;

	ArrayList <Object>channel_list, table_list, string_list, map_list, delta_list;

	boolean initialized = false;

	public static void main(String[] args)
	{
		if(args.length != 1)
		{
			System.out.println("Usage: java SimpleWriter <filename>");
			System.exit(0);
		}

		String prefix = new String("C:/Users/bcrow/Desktop/");
		//String prefix = new String("");
		String filename = new String(args[0]);

		SimpleWriter writer = new SimpleWriter(prefix + filename);
		writer.init();
	}
	
	public void init()
	{
	    System.out.println("Loaded file.");
	    System.out.println("Image xdim = " + image_xdim + ", ydim = " + image_ydim);
	    System.out.println();
	    ArrayList<int[]> quantized_channel_list   = new ArrayList<int[]>();
		ArrayList<int[]> dequantized_channel_list = new ArrayList<int[]>();
		
		int new_xdim = image_xdim;
		int new_ydim = image_ydim;
		if (pixel_quant != 0)
		{
			double factor = pixel_quant;
			factor /= 10;
			new_xdim = image_xdim - (int) (factor * (image_xdim / 2 - 2));
			new_ydim = image_ydim - (int) (factor * (image_ydim / 2 - 2));
		}
		
		// Quantize.
		for(int i = 0; i < 3; i++)
		{
			int[] channel = (int[]) channel_list.get(i);
			if(pixel_quant == 0)
			{
				if(pixel_shift == 0)
					quantized_channel_list.add(channel);	
				else
				{
					int [] shifted_channel = DeltaMapper.shift(channel, -pixel_shift);
					quantized_channel_list.add(shifted_channel);
				}
			}
			else
			{
				int [] resized_channel = ResizeMapper.resize(channel, image_xdim, new_xdim, new_ydim);
				if(pixel_shift == 0)
					quantized_channel_list.add(resized_channel);	
				else
				{
					int [] shifted_channel = DeltaMapper.shift(resized_channel, -pixel_shift);
					quantized_channel_list.add(shifted_channel);
				}
			}
		}
		
		// Get the set of channels with deltas that have a minimum sum, including difference channels.
		int[] quantized_blue = quantized_channel_list.get(0);
		int[] quantized_green = quantized_channel_list.get(1);
		int[] quantized_red = quantized_channel_list.get(2);
     
		int[] quantized_blue_green = DeltaMapper.getDifference(quantized_blue, quantized_green);
		int[] quantized_red_green = DeltaMapper.getDifference(quantized_red, quantized_green);
		int[] quantized_red_blue = DeltaMapper.getDifference(quantized_red, quantized_blue);

		quantized_channel_list.add(quantized_blue_green);
		quantized_channel_list.add(quantized_red_green);
		quantized_channel_list.add(quantized_red_blue);

		for (int i = 0; i < 6; i++)
		{
			int min = 256;
			int[] quantized_channel = quantized_channel_list.get(i);

			// Find the channel minimums.
			for (int j = 0; j < quantized_channel.length; j++)
				if (quantized_channel[j] < min)
					min = quantized_channel[j];
			channel_min[i] = min;

			// Get rid of the negative numbers in the difference channels.
			if (i > 2)
				for (int j = 0; j < quantized_channel.length; j++)
					quantized_channel[j] -= min;

			// Save the initial value.
			channel_init[i] = quantized_channel[0];

			// Replace the original data with the modified data.
			quantized_channel_list.set(i, quantized_channel);

			int [] frequency     = DeltaMapper.getIdealFrequency(quantized_channel, new_xdim, new_ydim);
			double shannon_limit = CodeMapper.getShannonLimit(frequency);
			channel_sum[i]       = (int)Math.floor(shannon_limit);
		}

		// Find the optimal set.
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

		int min_index = 0;
		int min_delta_sum = Integer.MAX_VALUE;
		for (int i = 0; i < 10; i++)
		{
			if (set_sum[i] < min_delta_sum)
			{
				min_delta_sum = set_sum[i];
				min_index = i;
			}
		}
		min_set_id = min_index;
		
		//System.out.println("Minimum set with current settings is " + set_string[min_set_id]);
		System.out.println();
		
		int [] channel_id = DeltaMapper.getChannels(min_set_id);
		
		int [] channel_delta_sum = new int[7];
		
		for(int i = 0; i < 3; i++)
		{
		    int j = channel_id[i];	
		    int[] quantized_channel = quantized_channel_list.get(j);
		    
		    int [] frequency     = DeltaMapper.getHorizontalFrequency(quantized_channel, new_xdim, new_ydim);
			double shannon_limit = CodeMapper.getShannonLimit(frequency);
			int    shannon_sum   = (int)Math.floor(shannon_limit);
			channel_delta_sum[0] = shannon_sum;

		    frequency     = DeltaMapper.getVerticalFrequency(quantized_channel, new_xdim, new_ydim);
			shannon_limit = CodeMapper.getShannonLimit(frequency);
			shannon_sum   = (int)Math.floor(shannon_limit);
			channel_delta_sum[1] = shannon_sum;
			
			frequency     = DeltaMapper.getAverageFrequency(quantized_channel, new_xdim, new_ydim);
			shannon_limit = CodeMapper.getShannonLimit(frequency);
			shannon_sum   = (int)Math.floor(shannon_limit);
			channel_delta_sum[2] = shannon_sum;
			
			frequency     = DeltaMapper.getPaethFrequency(quantized_channel, new_xdim, new_ydim);
			shannon_limit = CodeMapper.getShannonLimit(frequency);
			shannon_sum   = (int)Math.floor(shannon_limit);
			channel_delta_sum[3] = shannon_sum;
			
			frequency     = DeltaMapper.getGradientFrequency(quantized_channel, new_xdim, new_ydim);
			shannon_limit = CodeMapper.getShannonLimit(frequency);
			shannon_sum   = (int)Math.floor(shannon_limit);
			channel_delta_sum[4] = shannon_sum;
			
			ArrayList <int []> result = DeltaMapper.getScanlineFrequency(quantized_channel, new_xdim, new_ydim);
			frequency     = result.get(0);
			shannon_limit = CodeMapper.getShannonLimit(frequency);
			shannon_sum   = (int)Math.floor(shannon_limit);
			channel_delta_sum[5] = shannon_sum;
			
			int [] map = result.get(1);
			channel_delta_sum[5] += map.length / 4;
			
			result     = DeltaMapper.getScanline2Frequency(quantized_channel, new_xdim, new_ydim);
			frequency     = result.get(0);
			shannon_limit = CodeMapper.getShannonLimit(frequency);
			shannon_sum   = (int)Math.floor(shannon_limit);
			channel_delta_sum[6] = shannon_sum;
			
			map = result.get(1);
			channel_delta_sum[6] += map.length / 4;
		}
		
		min_delta_sum = channel_delta_sum[0];
		min_index     = 0;
		
		for(int i = 1; i < 7; i++)
		{
			if(channel_delta_sum[i] < min_delta_sum)
			{
				min_delta_sum = channel_delta_sum[i];
				min_index = i;
			}
		}
		delta_button[min_index].doClick();
		System.out.println("The delta type that produces the smallest entropy sum is " + delta_type_string[min_index]);
	}
	
	public SimpleWriter(String _filename)
	{
		filename = _filename;
		try
		{
			File file = new File(filename);
			file_length = file.length();
			original_image = ImageIO.read(file);
			int raster_type = original_image.getType();
			image_xdim = original_image.getWidth();
			image_ydim = original_image.getHeight();

			channel_list = new ArrayList<Object>();
			table_list   = new ArrayList<Object>();
			string_list  = new ArrayList<Object>();
			map_list     = new ArrayList<Object>();
			delta_list   = new ArrayList<Object>();

			channel_string = new String[6];
			channel_string[0] = new String("blue");
			channel_string[1] = new String("green");
			channel_string[2] = new String("red");
			channel_string[3] = new String("blue-green");
			channel_string[4] = new String("red-green");
			channel_string[5] = new String("red-blue");

			set_sum = new int[10];
			set_string = new String[10];
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
			
			delta_type_string = new String[8];
			delta_type_string[0] = new String("horizontal");
			delta_type_string[1] = new String("vertical");
			delta_type_string[2] = new String("average");
			delta_type_string[3] = new String("paeth");
			delta_type_string[4] = new String("gradient");
			delta_type_string[5] = new String("scanline (1)");
			delta_type_string[6] = new String("scanline (2)");
			delta_type_string[7] = new String("frame map");

			channel_init      = new int[6];
			channel_min       = new int[6];
			channel_delta_min = new int[6];
			channel_sum       = new int[6];
			channel_length    = new int[6];
			channel_compressed_length = new int[6];

			channel_string_type = new int[3];
			min_bytelength      = new int[3];
			max_bytelength      = new int[3];
			channel_iterations  = new byte[3];
			
			if(raster_type == BufferedImage.TYPE_3BYTE_BGR)
			{
				pixel = new int[image_xdim * image_ydim];
				PixelGrabber pixel_grabber = new PixelGrabber(original_image, 0, 0, image_xdim, image_ydim, pixel, 0, image_xdim);
				try
				{
					pixel_grabber.grabPixels();
				} 
				catch (InterruptedException e)
				{
					System.err.println(e.toString());
				}
				if ((pixel_grabber.getStatus() & ImageObserver.ABORT) != 0)
				{
					System.err.println("Error grabbing pixels.");
					System.exit(1);
				}

				int[] alpha = new int[image_xdim * image_ydim];
				int[] blue  = new int[image_xdim * image_ydim];
				int[] green = new int[image_xdim * image_ydim];
				int[] red   = new int[image_xdim * image_ydim];
				for (int i = 0; i < image_xdim * image_ydim; i++)
				{
					alpha[i] = (pixel[i] >> 24) & 0xff;
					blue[i] = (pixel[i] >> 16) & 0xff;
					green[i] = (pixel[i] >> 8) & 0xff;
					red[i] = pixel[i] & 0xff;
				}
				channel_list.add(blue);
				channel_list.add(green);
				channel_list.add(red);

				working_image = new BufferedImage(image_xdim, image_ydim, BufferedImage.TYPE_INT_RGB);

				for (int i = 0; i < image_xdim; i++)
				{
					for (int j = 0; j < image_ydim; j++)
						working_image.setRGB(i, j, pixel[j * image_xdim + i]);
				}

				JFrame frame = new JFrame("Delta Writer " + filename);

				WindowAdapter window_handler = new WindowAdapter()
				{
					public void windowClosing(WindowEvent event)
					{
						System.exit(0);
					}
				};
				frame.addWindowListener(window_handler);

				Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
				screen_xdim = (int) screenSize.getWidth();
				screen_ydim = (int) screenSize.getHeight();
				if(image_xdim <= (screen_xdim - 20) && image_ydim <= (screen_ydim - 200))
				{
					canvas_xdim = image_xdim;
					canvas_ydim = image_ydim;
					scale = 1.;
				} 
				else if (image_xdim <= (screen_xdim - 20))
				{
					canvas_ydim = screen_ydim - 200;
					scale = canvas_ydim;
					scale /= image_ydim;
					canvas_xdim = (int) (scale * image_xdim);
				} 
				else if (image_ydim <= (screen_ydim - 200))
				{
					canvas_xdim = screen_xdim - 20;
					scale = canvas_ydim;
					scale /= image_ydim;
				} 
				else
				{
					double xscale = screen_xdim - 20;
					xscale /= image_xdim;
					double yscale = screen_ydim - 200;
					yscale /= image_ydim;

					if (xscale <= yscale)
						scale = xscale;
					else
						scale = yscale;

					canvas_xdim = (int) (scale * image_xdim);
					canvas_ydim = (int) (scale * image_ydim);
				}

				if (scale == 1.)
					display_image = original_image;
				else
				{
					AffineTransform scaling_transform = new AffineTransform();
					scaling_transform.scale(scale, scale);
					AffineTransformOp scale_op = new AffineTransformOp(scaling_transform, AffineTransformOp.TYPE_BILINEAR);
					display_image = new BufferedImage(canvas_xdim, canvas_ydim, original_image.getType());
					display_image = scale_op.filter(original_image, display_image);
				}
				image_canvas = new ImageCanvas();
				image_canvas.setSize(canvas_xdim, canvas_ydim);
				frame.getContentPane().add(image_canvas, BorderLayout.CENTER);

				JMenuBar menu_bar = new JMenuBar();

				JMenu file_menu = new JMenu("File");

				apply_item = new JMenuItem("Apply");
				ApplyHandler apply_handler = new ApplyHandler();
				apply_item.addActionListener(apply_handler);
				file_menu.add(apply_item);

				JMenuItem reload_item = new JMenuItem("Reload");
				ActionListener reload_handler = new ActionListener()
				{
					public void actionPerformed(ActionEvent event)
					{
						if (scale == 1.)
							display_image = original_image;
						else
						{
							System.out.println("Scaling image.");
							AffineTransform scaling_transform = new AffineTransform();
							scaling_transform.scale(scale, scale);
							AffineTransformOp scale_op = new AffineTransformOp(scaling_transform,
									AffineTransformOp.TYPE_BILINEAR);
							BufferedImage scaled_image = new BufferedImage(canvas_xdim, canvas_ydim,
									original_image.getType());
							scaled_image = scale_op.filter(original_image, scaled_image);
							display_image = scaled_image;
						}

						System.out.println("Reloaded original image.");
						image_canvas.repaint();
					}
				};

				reload_item.addActionListener(reload_handler);
				file_menu.add(reload_item);

				JMenuItem save_item = new JMenuItem("Save");
				SaveHandler save_handler = new SaveHandler();
				save_item.addActionListener(save_handler);
				file_menu.add(save_item);

				JMenu settings_menu = new JMenu("Quantization");

				JMenuItem quant_item = new JMenuItem("Pixel Resolution");
				JDialog quant_dialog = new JDialog(frame, "Pixel Resolution");
				ActionListener quant_handler = new ActionListener()
				{
					public void actionPerformed(ActionEvent e)
					{
						Point location_point = frame.getLocation();
						int x = (int) location_point.getX();
						int y = (int) location_point.getY();

						quant_dialog.setLocation(x, y - 50);
						quant_dialog.pack();
						quant_dialog.setVisible(true);
					}
				};
				quant_item.addActionListener(quant_handler);

				JPanel quant_panel = new JPanel(new BorderLayout());
				JSlider quant_slider = new JSlider();
				quant_slider.setMinimum(0);
				quant_slider.setMaximum(10);
				quant_slider.setValue(pixel_quant);
				JTextField quant_value = new JTextField(3);
				quant_value.setText(" " + pixel_quant + " ");
				ChangeListener quant_slider_handler = new ChangeListener()
				{
					public void stateChanged(ChangeEvent e)
					{
						JSlider slider = (JSlider) e.getSource();
						pixel_quant = slider.getValue();
						quant_value.setText(" " + pixel_quant + " ");
						if (slider.getValueIsAdjusting() == false)
						{
							apply_item.doClick();
						}
					}
				};
				quant_slider.addChangeListener(quant_slider_handler);
				quant_panel.add(quant_slider, BorderLayout.CENTER);
				quant_panel.add(quant_value, BorderLayout.EAST);
				quant_dialog.add(quant_panel);
				settings_menu.add(quant_item);

				JMenuItem shift_item = new JMenuItem("Color Resolution");
				JDialog shift_dialog = new JDialog(frame, "Color Resolution");
				ActionListener shift_handler = new ActionListener()
				{
					public void actionPerformed(ActionEvent e)
					{
						Point location_point = frame.getLocation();
						int x = (int) location_point.getX();
						int y = (int) location_point.getY();

						shift_dialog.setLocation(x, y - 100);
						shift_dialog.pack();
						shift_dialog.setVisible(true);
					}
				};
				shift_item.addActionListener(shift_handler);

				JPanel shift_panel = new JPanel(new BorderLayout());
				JSlider shift_slider = new JSlider();
				shift_slider.setMinimum(0);
				shift_slider.setMaximum(7);
				shift_slider.setValue(pixel_shift);
				JTextField shift_value = new JTextField(3);
				shift_value.setText(" " + pixel_shift + " ");
				ChangeListener shift_slider_handler = new ChangeListener()
				{
					public void stateChanged(ChangeEvent e)
					{
						JSlider slider = (JSlider) e.getSource();
						pixel_shift = slider.getValue();
						shift_value.setText(" " + pixel_shift + " ");
						if (slider.getValueIsAdjusting() == false)
							apply_item.doClick();
					}
				};
				shift_slider.addChangeListener(shift_slider_handler);
				shift_panel.add(shift_slider, BorderLayout.CENTER);
				shift_panel.add(shift_value, BorderLayout.EAST);
				shift_dialog.add(shift_panel);
				settings_menu.add(shift_item);

				
				// The correction value is a convenience to help see what
				// quantizing does to the original image. Instead of
				// simply switching back and forth between the original
				// image and the quantized image, the user can slowly
				// move back and forth between the two images.
				JMenuItem correction_item = new JMenuItem("Error Correction");
				JDialog correction_dialog = new JDialog(frame, "Error Correction");
				ActionListener correction_handler = new ActionListener()
				{
					public void actionPerformed(ActionEvent e)
					{
						Point location_point = frame.getLocation();
						int x = (int) location_point.getX();
						int y = (int) location_point.getY();

						correction_dialog.setLocation(x, y - 150);
						correction_dialog.pack();
						correction_dialog.setVisible(true);
					}
				};
				correction_item.addActionListener(correction_handler);

				JPanel correction_panel = new JPanel(new BorderLayout());
				JSlider correction_slider = new JSlider();
				correction_slider.setMinimum(0);
				correction_slider.setMaximum(10);
				correction_slider.setValue(correction);
				JTextField correction_value = new JTextField(3);
				correction_value.setText(" " + correction + " ");
				ChangeListener correction_slider_handler = new ChangeListener()
				{
					public void stateChanged(ChangeEvent e)
					{
						JSlider slider = (JSlider) e.getSource();
						correction = slider.getValue();
						correction_value.setText(" " + correction + " ");
						if (slider.getValueIsAdjusting() == false)
							apply_item.doClick();
					}
				};
				correction_slider.addChangeListener(correction_slider_handler);
				correction_panel.add(correction_slider, BorderLayout.CENTER);
				correction_panel.add(correction_value, BorderLayout.EAST);
				correction_dialog.add(correction_panel);

				settings_menu.add(correction_item);
				
				

				JMenu delta_menu = new JMenu("Delta");

				delta_button = new JRadioButtonMenuItem[8];

				delta_button[0] = new JRadioButtonMenuItem("H");
				delta_button[1] = new JRadioButtonMenuItem("V");
				delta_button[2] = new JRadioButtonMenuItem("Average");
				delta_button[3] = new JRadioButtonMenuItem("Paeth");
				delta_button[4] = new JRadioButtonMenuItem("Gradient");
				delta_button[5] = new JRadioButtonMenuItem("Scanline 1");
				delta_button[6] = new JRadioButtonMenuItem("Scanline 2");
				delta_button[7] = new JRadioButtonMenuItem("Map");

				delta_button[delta_type].setSelected(true);

				class ButtonHandler implements ActionListener
				{
					int index;

					ButtonHandler(int index)
					{
						this.index = index;
					}

					public void actionPerformed(ActionEvent e)
					{
						if (delta_type != index)
						{
							delta_button[delta_type].setSelected(false);
							delta_type = index;
							delta_button[delta_type].setSelected(true);
							apply_item.doClick();
						} 
						else
							delta_button[delta_type].setSelected(true);
					}
				}

				for (int i = 0; i < 8; i++)
				{
					delta_button[i].addActionListener(new ButtonHandler(i));
					delta_menu.add(delta_button[i]);
				}

				menu_bar.add(file_menu);
				menu_bar.add(delta_menu);
				menu_bar.add(settings_menu);

				frame.setJMenuBar(menu_bar);

				frame.pack();
				frame.setLocation(5, 200);
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
			g.drawImage(display_image, 0, 0, this);
		}
	}

	class ApplyHandler implements ActionListener
	{
		// The minimum set changes depending on how the
		// image is quantized, so it has to be reevaluated
		// with every change in the parameters.
		public void actionPerformed(ActionEvent event)
		{
			ArrayList<int[]> quantized_channel_list   = new ArrayList<int[]>();
			ArrayList<int[]> dequantized_channel_list = new ArrayList<int[]>();
			
			int new_xdim = image_xdim;
			int new_ydim = image_ydim;
			if (pixel_quant != 0)
			{
				double factor = pixel_quant;
				factor /= 10;
				new_xdim = image_xdim - (int) (factor * (image_xdim / 2 - 2));
				new_ydim = image_ydim - (int) (factor * (image_ydim / 2 - 2));
			}
			
			// Quantize.
			for(int i = 0; i < 3; i++)
			{
				int[] channel = (int[]) channel_list.get(i);
				if(pixel_quant == 0)
				{
					if(pixel_shift == 0)
						quantized_channel_list.add(channel);	
					else
					{
						int [] shifted_channel = DeltaMapper.shift(channel, -pixel_shift);
						quantized_channel_list.add(shifted_channel);
					}
				}
				else
				{
					int [] resized_channel = ResizeMapper.resize(channel, image_xdim, new_xdim, new_ydim);
					if(pixel_shift == 0)
						quantized_channel_list.add(resized_channel);	
					else
					{
						int [] shifted_channel = DeltaMapper.shift(resized_channel, -pixel_shift);
						quantized_channel_list.add(shifted_channel);
					}
				}
			}
			
			// Get the set of channels with deltas that have a minimum sum, including difference channels.
			int[] quantized_blue  = quantized_channel_list.get(0);
			int[] quantized_green = quantized_channel_list.get(1);
			int[] quantized_red   = quantized_channel_list.get(2);
         
			int[] quantized_blue_green = DeltaMapper.getDifference(quantized_blue, quantized_green);
			int[] quantized_red_green  = DeltaMapper.getDifference(quantized_red, quantized_green);
			int[] quantized_red_blue   = DeltaMapper.getDifference(quantized_red, quantized_blue);

			quantized_channel_list.add(quantized_blue_green);
			quantized_channel_list.add(quantized_red_green);
			quantized_channel_list.add(quantized_red_blue);

			for (int i = 0; i < 6; i++)
			{
				int min = 256;
				int[] quantized_channel = quantized_channel_list.get(i);

				// Find the channel minimums.
				for (int j = 0; j < quantized_channel.length; j++)
					if (quantized_channel[j] < min)
						min = quantized_channel[j];
				channel_min[i] = min;

				// Get rid of the negative numbers in the difference channels.
				if (i > 2)
					for (int j = 0; j < quantized_channel.length; j++)
						quantized_channel[j] -= min;

				// Save the initial value.
				channel_init[i] = quantized_channel[0];

				// Replace the original data with the modified data.
				quantized_channel_list.set(i, quantized_channel);

				int [] frequency     = DeltaMapper.getIdealFrequency(quantized_channel, new_xdim, new_ydim);
				double shannon_limit = CodeMapper.getShannonLimit(frequency);
				channel_sum[i]       = (int)Math.floor(shannon_limit);
			}

			// Find the optimal set.
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

			int min_index = 0;
			int min_delta_sum = Integer.MAX_VALUE;
			for (int i = 0; i < 10; i++)
			{
				if (set_sum[i] < min_delta_sum)
				{
					min_delta_sum = set_sum[i];
					min_index = i;
				}
			}
			min_set_id = min_index;
			
			file_compression_rate = file_length;
			file_compression_rate /= image_xdim * image_ydim * 3;

			System.out.println("A set of channels with the lowest entropy sum is " + set_string[min_index]);
			System.out.println();

			int[] channel_id = DeltaMapper.getChannels(min_set_id);

			table_list.clear();
			string_list.clear();
			map_list.clear();
			delta_list.clear();

			// Create the segment strings for each reference or difference channel
			// in the minimum set that will be used by the save handler.
			for (int i = 0; i < 3; i++)
			{
				int j = channel_id[i];

				int[] quantized_channel = quantized_channel_list.get(j);
				
				double current_sum = channel_sum[j];
				
				int [] frequency     = DeltaMapper.getHorizontalFrequency(quantized_channel, new_xdim, new_ydim);
				double shannon_limit = CodeMapper.getShannonLimit(frequency);
				int    shannon_sum   = (int)Math.floor(shannon_limit);
				
				double horizontal_ratio = shannon_sum;
				horizontal_ratio       /= current_sum;
				
				
				frequency     = DeltaMapper.getVerticalFrequency(quantized_channel, new_xdim, new_ydim);
				shannon_limit = CodeMapper.getShannonLimit(frequency);
				shannon_sum   = (int)Math.floor(shannon_limit);
				
				double vertical_ratio = shannon_sum;
				vertical_ratio       /= current_sum;
				
				
				frequency     = DeltaMapper.getAverageFrequency(quantized_channel, new_xdim, new_ydim);
				shannon_limit = CodeMapper.getShannonLimit(frequency);
				shannon_sum   = (int)Math.floor(shannon_limit);
				
				double average_ratio = shannon_sum;
				average_ratio       /= current_sum;
				
				
				frequency     = DeltaMapper.getPaethFrequency(quantized_channel, new_xdim, new_ydim);
				shannon_limit = CodeMapper.getShannonLimit(frequency);
				shannon_sum   = (int)Math.floor(shannon_limit);
				
				double paeth_ratio = shannon_sum;
				paeth_ratio       /= current_sum;
				
				frequency     = DeltaMapper.getGradientFrequency(quantized_channel, new_xdim, new_ydim);
				shannon_limit = CodeMapper.getShannonLimit(frequency);
				shannon_sum   = (int)Math.floor(shannon_limit);
				
				double gradient_ratio = shannon_sum;
				gradient_ratio       /= current_sum;
				
				
				ArrayList <int []> f_result  = DeltaMapper.getScanlineFrequency(quantized_channel, new_xdim, new_ydim);
				frequency = f_result.get(0);
				shannon_limit = CodeMapper.getShannonLimit(frequency);
				shannon_sum   = (int)Math.floor(shannon_limit);
				
				
				shannon_sum += (new_ydim - 1) / 4;
				double scanline_ratio = shannon_sum;
				scanline_ratio       /= current_sum;
				
				
				f_result     = DeltaMapper.getScanline2Frequency(quantized_channel, new_xdim, new_ydim);
				frequency = f_result.get(0);
				shannon_limit = CodeMapper.getShannonLimit(frequency);
				shannon_sum   = (int)Math.floor(shannon_limit);
				
				shannon_sum += (new_ydim - 1) / 4;
				double scanline2_ratio = shannon_sum;
				scanline2_ratio       /= current_sum;
				
				System.out.println("Horizontal ratio is " + String.format("%.2f", horizontal_ratio));
				System.out.println("Vertical ratio is   " + String.format("%.2f", vertical_ratio));
				System.out.println("Average ratio is    " + String.format("%.2f", average_ratio));
				System.out.println("Paeth ratio is      " + String.format("%.2f", paeth_ratio));
				System.out.println("Gradient ratio is   " + String.format("%.2f", gradient_ratio));
				System.out.println("Scanline ratio is   " + String.format("%.2f", scanline_ratio));
				System.out.println("Scanline2 ratio is  " + String.format("%.2f", scanline2_ratio));
				
				ArrayList<Object> result = new ArrayList<Object>();
				
				if (delta_type == 0)
				{
					result = DeltaMapper.getHorizontalDeltasFromValues(quantized_channel, new_xdim, new_ydim);
				} 
				else if (delta_type == 1)
				{
					result = DeltaMapper.getVerticalDeltasFromValues(quantized_channel, new_xdim, new_ydim);
				} 
				else if (delta_type == 2)
				{
					result = DeltaMapper.getAverageDeltasFromValues(quantized_channel, new_xdim, new_ydim);
				} 
				else if (delta_type == 3)
				{
					result = DeltaMapper.getPaethDeltasFromValues(quantized_channel, new_xdim, new_ydim);
				} 
				else if (delta_type == 4)
				{
					result = DeltaMapper.getGradientDeltasFromValues(quantized_channel, new_xdim, new_ydim);
				} 
				else if (delta_type == 5)
				{
					result = DeltaMapper.getMixedDeltasFromValues(quantized_channel, new_xdim, new_ydim);
					byte[] map = (byte[]) result.get(2);
					map_list.add(map);
				} 
				else if (delta_type == 6)
				{
					result = DeltaMapper.getMixedDeltasFromValues2(quantized_channel, new_xdim, new_ydim);
					byte[] map = (byte[]) result.get(2);
					map_list.add(map);
				} 
				else if (delta_type == 7)
				{
					result = DeltaMapper.getIdealDeltasFromValues(quantized_channel, new_xdim, new_ydim);
					byte[] map = (byte[]) result.get(2);
					map_list.add(map);
				}

				int[] delta = (int[]) result.get(1);
				
				/*
				byte [] delta_bytes = new byte[delta.length];
			    for(int k = 0; k < delta.length; k++)
			    {
			    	    delta_bytes[k] = (byte)(delta[k] + channel_delta_min[j]);
			    }
			    delta_list.add(delta_bytes);
			    */

				ArrayList delta_string_list = StringMapper.getStringList(delta, precompress);
				System.out.println();
				
				channel_delta_min[j]        = (int) delta_string_list.get(0);
				channel_length[j]           = (int) delta_string_list.get(1);
				int[] string_table          = (int[]) delta_string_list.get(2);
				byte[] compression_string   = (byte[]) delta_string_list.get(3);

				table_list.add(string_table);
				string_list.add(compression_string);

				channel_compressed_length[j] = StringMapper.getBitlength(compression_string);
				channel_string_type[i]       = StringMapper.getType(compression_string);
				channel_iterations[i]        = StringMapper.getIterations(compression_string);
			}

			// Decompressing the data and displaying it.
			for (int i = 0; i < 3; i++)
			{
				int j = channel_id[i];

				int[] table = (int[]) table_list.get(i);
				int[] delta = new int[new_xdim * new_ydim];
				byte[] current_string = (byte[]) string_list.get(i);
				int current_iterations = StringMapper.getIterations(current_string);

				if (current_iterations == 0 || current_iterations == 16)
				{
					int number_unpacked = StringMapper.unpackStrings2(current_string, table, delta);
					if (number_unpacked != new_xdim * new_ydim)
						System.out.println("Number of values unpacked does not agree with image dimensions.");
				} 
				else
				{
					byte[] decompressed_string = StringMapper.decompressStrings2(current_string);
					int number_unpacked = StringMapper.unpackStrings2(decompressed_string, table, delta);
					if(number_unpacked != new_xdim * new_ydim)
						System.out.println("Number of values unpacked does not agree with image dimensions.");
				}
				
				for (int k = 1; k < delta.length; k++)
					delta[k] += channel_delta_min[j];

				int[] channel = new int[0];

				if (delta_type == 0)
				{
					channel = DeltaMapper.getValuesFromHorizontalDeltas(delta, new_xdim, new_ydim, channel_init[j]);
				} 
				else if (delta_type == 1)
				{
					channel = DeltaMapper.getValuesFromVerticalDeltas(delta, new_xdim, new_ydim, channel_init[j]);
				} 
				else if (delta_type == 2)
				{
					channel = DeltaMapper.getValuesFromAverageDeltas(delta, new_xdim, new_ydim, channel_init[j]);
				} 
				else if (delta_type == 3)
				{
					channel = DeltaMapper.getValuesFromPaethDeltas(delta, new_xdim, new_ydim, channel_init[j]);
				} 
				else if (delta_type == 4)
				{
					channel = DeltaMapper.getValuesFromGradientDeltas(delta, new_xdim, new_ydim, channel_init[j]);
				} 
				else if (delta_type == 5)
				{
					byte[] map = (byte[]) map_list.get(i);
					channel    = DeltaMapper.getValuesFromMixedDeltas(delta, new_xdim, new_ydim, channel_init[j], map);
				} 
				else if (delta_type == 6)
				{
					byte[] map = (byte[]) map_list.get(i);
					channel    = DeltaMapper.getValuesFromMixedDeltas2(delta, new_xdim, new_ydim, channel_init[j], map);
				} 
				else if (delta_type == 7)
				{
					byte[] map = (byte[]) map_list.get(i);
					channel    = DeltaMapper.getValuesFromIdealDeltas(delta, new_xdim, new_ydim, channel_init[j], map);
				}
                
				// Remove the negative numbers from difference channels.
				if (j > 2)
					for (int k = 0; k < channel.length; k++)
						channel[k] += channel_min[j];

				// Dequantize.
				if(pixel_shift == 0)
				{
				    if(pixel_quant == 0)
				    {
				    	    dequantized_channel_list.add(channel);
				    }
				    else
				    {
				    	    int [] resized_channel = ResizeMapper.resize(channel, new_xdim, image_xdim, image_ydim);	
				    	    dequantized_channel_list.add(resized_channel);
				    }
				}
				else
				{
				    int [] shifted_channel = DeltaMapper.shift(channel, pixel_shift);	
				    if(pixel_quant == 0)
				    	    dequantized_channel_list.add(shifted_channel);	
				    else
				    {
				    	    int [] resized_channel = ResizeMapper.resize(shifted_channel, new_xdim, image_xdim, image_ydim);	
				    	    dequantized_channel_list.add(resized_channel);	
				    }
				}
			}

			// Assemble the rgb files if the minimum set contains difference channels.
			int[] blue = new int[new_xdim * new_ydim];
			int[] green = new int[new_xdim * new_ydim];
			int[] red = new int[new_xdim * new_ydim];
			if(min_set_id == 0)
			{
				blue  = dequantized_channel_list.get(0);
				green = dequantized_channel_list.get(1);
				red   = dequantized_channel_list.get(2);
			} 
			else if(min_set_id == 1)
			{
				blue = dequantized_channel_list.get(0);
				red = dequantized_channel_list.get(1);
				int[] red_green = dequantized_channel_list.get(2);
				green = DeltaMapper.getDifference(red, red_green);
			} 
			else if(min_set_id == 2)
			{
				blue = dequantized_channel_list.get(0);
				red = dequantized_channel_list.get(1);
				int[] blue_green = dequantized_channel_list.get(2);
				green = DeltaMapper.getDifference(blue, blue_green);
			} 
			else if(min_set_id == 3)
			{
				blue = dequantized_channel_list.get(0);
				int[] blue_green = dequantized_channel_list.get(1);
				green = DeltaMapper.getDifference(blue, blue_green);
				int[] red_green = dequantized_channel_list.get(2);
				red = DeltaMapper.getSum(red_green, green);
			} 
			else if(min_set_id == 4)
			{
				blue = dequantized_channel_list.get(0);
				int[] blue_green = dequantized_channel_list.get(1);
				green = DeltaMapper.getDifference(blue, blue_green);
				int[] red_blue = dequantized_channel_list.get(2);
				red = DeltaMapper.getSum(blue, red_blue);
			} 
			else if (min_set_id == 5)
			{
				green = dequantized_channel_list.get(0);
				red = dequantized_channel_list.get(1);
				int[] blue_green = dequantized_channel_list.get(2);
				blue = DeltaMapper.getSum(blue_green, green);
			} 
			else if(min_set_id == 6)
			{
				red = dequantized_channel_list.get(0);
				int[] blue_green = dequantized_channel_list.get(1);
				int[] red_green = dequantized_channel_list.get(2);
				for (int i = 0; i < red_green.length; i++)
					red_green[i] = -red_green[i];
				green = DeltaMapper.getSum(red_green, red);
				blue = DeltaMapper.getSum(blue_green, green);
			} 
			else if(min_set_id == 7)
			{
				green = dequantized_channel_list.get(0);
				int[] blue_green = dequantized_channel_list.get(1);
				blue = DeltaMapper.getSum(green, blue_green);
				int[] red_green = dequantized_channel_list.get(2);
				red = DeltaMapper.getSum(green, red_green);
			} 
			else if(min_set_id == 8)
			{
				green = dequantized_channel_list.get(0);
				int[] red_green = dequantized_channel_list.get(1);
				red = DeltaMapper.getSum(green, red_green);
				int[] red_blue = dequantized_channel_list.get(2);
				blue = DeltaMapper.getDifference(red, red_blue);
			} 
			else if(min_set_id == 9)
			{
				red = dequantized_channel_list.get(0);
				int[] red_green = dequantized_channel_list.get(1);
				green = DeltaMapper.getDifference(red, red_green);
				int[] red_blue = dequantized_channel_list.get(2);
				blue = DeltaMapper.getDifference(red, red_blue);
			}

			int[] original_blue = (int[]) channel_list.get(0);
			int[] original_green = (int[]) channel_list.get(1);
			int[] original_red = (int[]) channel_list.get(2);

			int[][] error = new int[3][image_xdim * image_ydim];

			for (int i = 0; i < image_xdim * image_ydim; i++)
			{
				error[0][i] = original_blue[i] - blue[i];
				error[1][i] = original_green[i] - green[i];
				error[2][i] = original_red[i] - red[i];

				if (correction != 0)
				{
					double factor = correction;
					factor /= 10;

					double addend = (double) error[0][i] * factor;
					blue[i] += (int) addend;

					addend = (double) error[1][i] * factor;
					green[i] += addend;

					addend = (double) error[2][i] * factor;
					red[i] += addend;
				}
			}

			int k = 0;
			for (int i = 0; i < image_ydim; i++)
			{
				for (int j = 0; j < image_xdim; j++)
				{
					working_image.setRGB(j, i, (blue[k] << 16) + (green[k] << 8) + red[k]);
					k++;
				}
			}

			if (scale == 1.)
				display_image = working_image;
			else
			{
				System.out.println("Scaling image.");
				AffineTransform scaling_transform = new AffineTransform();
				scaling_transform.scale(scale, scale);
				AffineTransformOp scale_op = new AffineTransformOp(scaling_transform, AffineTransformOp.TYPE_BILINEAR);
				BufferedImage scaled_image = new BufferedImage(canvas_xdim, canvas_ydim, working_image.getType());
				scaled_image = scale_op.filter(working_image, scaled_image);
				display_image = scaled_image;
			}

			System.out.println("Loaded quantized image.");
			System.out.println();
			image_canvas.repaint();
			initialized = true;
		}
	}

	class SaveHandler implements ActionListener
	{
		public void actionPerformed(ActionEvent event)
		{
			if(!initialized)
				apply_item.doClick();
			int channel_id[] = DeltaMapper.getChannels(min_set_id);

			try
			{
				DataOutputStream out = new DataOutputStream(new FileOutputStream(new File("foo")));

				// Dimensions of full sized frame
				out.writeShort(image_xdim);
				out.writeShort(image_ydim);

				// Compression parameters
				out.writeByte(pixel_shift);
				out.writeByte(pixel_quant);
				out.writeByte(min_set_id);
				out.writeByte(delta_type);

				for (int i = 0; i < 3; i++)
				{
					int j = channel_id[i];

					// Only used for difference channels.
					out.writeInt(channel_min[j]);

					out.writeInt(channel_init[j]);
					out.writeInt(channel_delta_min[j]);

					out.writeInt(channel_length[j]);

					// If the string didn't compress or wasn't compressed,
					// this is the same as the uncompressed length.
					out.writeInt(channel_compressed_length[j]);

					out.writeByte(channel_iterations[i]);

					int[] table = (int[]) table_list.get(i);
					out.writeShort(table.length);

					int max_byte_value = Byte.MAX_VALUE * 2 + 1;

					if (table.length <= max_byte_value)
					{
						for (int k = 0; k < table.length; k++)
							out.writeByte(table[k]);
					} 
					else
					{
						for (int k = 0; k < table.length; k++)
							out.writeShort(table[k]);
					}

					if(delta_type == 5 || delta_type == 6 || delta_type == 7)
					{
						byte[] map = (byte[]) map_list.get(i);
						
						byte [] packed_map = SegmentMapper.packBits(map, 2);
						
						out.writeInt(map.length);
						out.writeInt(packed_map.length);
						out.write(packed_map, 0, packed_map.length);
					}
					
					
					Deflater deflater;
					
					if(deflate_type == 0)
						deflater = new Deflater(Deflater.BEST_COMPRESSION);
					else if(deflate_type == 1)
						deflater = new Deflater(Deflater.HUFFMAN_ONLY);
					else
						deflater = new Deflater(Deflater.FILTERED);

					
					byte [] string = (byte []) string_list.get(i);
					byte [] zipped_data = new byte[2 * string.length];
					deflater.setInput(string);
					
					/*
					byte [] delta_bytes = (byte[]) delta_list.get(i);
					byte [] zipped_data = new byte[2 * delta_bytes.length];
					deflater.setInput(delta_bytes);
					*/
					
					deflater.finish();
					int zipped_length = deflater.deflate(zipped_data);
					deflater.end();
					
					
					out.writeInt(zipped_length);
					out.write(zipped_data, 0, zipped_length);
				}

				out.flush();
				out.close();

				File file = new File("foo");
				long file_length = file.length();
				double compression_rate = file_length;
				compression_rate /= image_xdim * image_ydim * 3;
				System.out.println("The file compression rate is " + String.format("%.4f", file_compression_rate));
				System.out.println("Delta type is " + delta_type_string[delta_type]);
				System.out.println("Delta bits compression rate is " + String.format("%.4f", compression_rate));
				System.out.println();
			} 
			catch (Exception e)
			{
				System.out.println(e.toString());
			}
		}
	}
}