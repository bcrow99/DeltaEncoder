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

public class DeltaWriter
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
	
	int segment_length = 20;
	int segment_type   = 3;
	int merge_type     = 0;
	
	double merge_bin   = .05;
	int correction     = 0;
	int min_set_id     = 0;
	
	int delta_type      = 2;
	
	boolean precompress = false;
	int deflate_type    = 0;
	
	int histogram_type    = 0;
	int histogram_channel = 0;
	boolean histogram_visible = false;
	
	double scale       = 1.;

	int[] set_sum, channel_sum;
	String[] set_string;
	String[] channel_string;

	int[] channel_init;
	int[] channel_min;
	int[] channel_delta_min;

	int[] channel_length;
	int[] channel_compressed_length;
	int[] channel_string_type;
	int[] number_of_segments;
	byte[] channel_iterations;
	
	JTextField[] channel_text;

	int[] max_bytelength;

	long file_length;
	double file_compression_rate;
	
	Canvas histogram_canvas;

	ArrayList <Object>channel_list, table_list, string_list, cat_string_list, map_list, segment_list;

	boolean initialized = false;

	public static void main(String[] args)
	{
		if(args.length != 1)
		{
			System.out.println("Usage: java DeltaWriter <filename>");
			System.exit(0);
		}

		//String prefix = new String("C:/Users/Brian Crowley/Desktop/");
		String prefix = new String("");
		String filename = new String(args[0]);

		DeltaWriter writer = new DeltaWriter(prefix + filename);
		writer.init();
	}
	
	public void init()
	{
	    System.out.println("Loaded file.");
	    System.out.println("Image xdim = " + image_xdim + ", ydim = " + image_ydim);
	    System.out.println();
	}
	
	public DeltaWriter(String _filename)
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
			cat_string_list  = new ArrayList<Object>();
			map_list     = new ArrayList<Object>();
			segment_list = new ArrayList<Object>();

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

			channel_init      = new int[6];
			channel_min       = new int[6];
			channel_delta_min = new int[6];
			channel_sum       = new int[6];
			channel_length    = new int[6];
			channel_compressed_length = new int[6];

			channel_string_type = new int[3];
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
				
				JMenu segmentation_menu = new JMenu("Segmentation");
				
				JMenuItem segment_length_item   = new JMenuItem("Segment Length");
				JDialog   segment_length_dialog = new JDialog(frame, "Segment Length");
				ActionListener segment_length_handler = new ActionListener()
				{
					public void actionPerformed(ActionEvent e)
					{
						Point location_point = frame.getLocation();
						int x = (int) location_point.getX();
						int y = (int) location_point.getY();

						segment_length_dialog.setLocation(x, y - 150);
						segment_length_dialog.pack();
						segment_length_dialog.setVisible(true);
					}
				};
				segment_length_item.addActionListener(segment_length_handler);

				JPanel segment_length_panel = new JPanel(new BorderLayout());
				JSlider segment_slider = new JSlider();
				segment_slider.setMinimum(0);
				segment_slider.setMaximum(20);
				segment_slider.setValue(segment_length);
				JTextField segment_length_value = new JTextField(3);
				segment_length_value.setText(" " + segment_length + " ");
				ChangeListener segment_slider_handler = new ChangeListener()
				{
					public void stateChanged(ChangeEvent e)
					{
						JSlider slider = (JSlider) e.getSource();
						segment_length = slider.getValue();
						if (slider.getValueIsAdjusting() == false)
						{
							apply_item.doClick();
							segment_length_value.setText(" " + segment_length + " ");
						}
					}
				};
				segment_slider.addChangeListener(segment_slider_handler);
				segment_length_panel.add(segment_slider, BorderLayout.CENTER);
				segment_length_panel.add(segment_length_value, BorderLayout.EAST);
				segment_length_dialog.add(segment_length_panel);
				segmentation_menu.add(segment_length_item);
				
				JMenuItem segment_type_item   = new JMenuItem("Segment Type");
				JDialog   segment_type_dialog = new JDialog(frame, "Segment Type");
				ActionListener segment_type_handler = new ActionListener()
				{
					public void actionPerformed(ActionEvent e)
					{
						Point location_point = frame.getLocation();
						int x = (int) location_point.getX();
						int y = (int) location_point.getY();

						segment_type_dialog.setLocation(x, y - 150);
						segment_type_dialog.pack();
						segment_type_dialog.setVisible(true);
					}
				};
				segment_type_item.addActionListener(segment_type_handler);
				
				
				
				JTextField segment_type_value = new JTextField();
			    segment_type_value.setHorizontalAlignment(JTextField.CENTER);
			    segment_type_value.setText("" + segment_type);
			    
			    segment_type_value.addActionListener(new ActionListener()
			    	{
			    	    public void actionPerformed(ActionEvent e) 
			    	    {
				        String input = segment_type_value.getText();   
				        int new_segment_type = segment_type;
						try
						{
						    new_segment_type = Integer.parseInt(input);	
						}
						catch(Exception e2)
						{
						    System.out.println("Invalid input.");
						    System.out.println(e2.toString());
						    System.out.println("Must input a number from 0 to 3.");
						    segment_type_value.setText("" + segment_type);
						} 
						
						if(new_segment_type >= 0 && new_segment_type <= 3 && new_segment_type != segment_type)
						{
						    segment_type = new_segment_type;
						    apply_item.doClick();
						}
						else
						{
							System.out.println("Invalid input.");	
							System.out.println("Must input a number from 0 to 3.");
							segment_type_value.setText("" + segment_type);
						}
			    	    }
			    	});
			    
				JButton segment_type_button = new JButton("Set Segment Type");
				ActionListener button_type_handler = new ActionListener()
				{
					public void actionPerformed(ActionEvent e)
					{
						String input = segment_type_value.getText();
						int new_segment_type = segment_type;
						try
						{
						    new_segment_type = Integer.parseInt(input);	
						}
						catch(Exception e2)
						{
						    System.out.println("Invalid input.");
						    System.out.println(e2.toString());
						    System.out.println("Must input a number from 0 to 3.");
						    segment_type_value.setText("" + segment_type);
						} 
						
						if(new_segment_type >= 0 && new_segment_type <= 3 && new_segment_type != segment_type)
						{
						    segment_type = new_segment_type;
						    apply_item.doClick();
						}
						else
						{
							System.out.println("Invalid input.");	
							System.out.println("Must input a number from 0 to 3.");
							segment_type_value.setText("" + segment_type);
						}
					}
				};
				segment_type_button.addActionListener(button_type_handler);
				
				JPanel segment_type_panel = new JPanel(new GridLayout(2, 1));
				segment_type_panel.add(segment_type_value);
				segment_type_panel.add(segment_type_button);
				segment_type_dialog.add(segment_type_panel);
				segmentation_menu.add(segment_type_item);
				
				
				JMenuItem merge_type_item   = new JMenuItem("Merge Type");
				JDialog   merge_type_dialog = new JDialog(frame, "Merge Type");
				ActionListener merge_type_handler = new ActionListener()
				{
					public void actionPerformed(ActionEvent e)
					{
						Point location_point = frame.getLocation();
						int x = (int) location_point.getX();
						int y = (int) location_point.getY();

						merge_type_dialog.setLocation(x, y - 150);
						merge_type_dialog.pack();
						merge_type_dialog.setVisible(true);
					}
				};
				merge_type_item.addActionListener(merge_type_handler);
				
				
				JTextField merge_type_value = new JTextField();
			    merge_type_value.setHorizontalAlignment(JTextField.CENTER);
			    merge_type_value.setText("" + merge_type);
			    
			    merge_type_value.addActionListener(new ActionListener()
		      	{
			    	    public void actionPerformed(ActionEvent e) 
		    	        {
			            String input = merge_type_value.getText();   
			            int new_merge_type = merge_type;
					    try
					    {
					        new_merge_type = Integer.parseInt(input);	
					    }
					    catch(Exception e2)
					    {
					        System.out.println("Invalid input.");
					        System.out.println(e2.toString());
					        System.out.println("Must input a number from 0 to 3.");
					        merge_type_value.setText("" + merge_type);
					    } 
					
					    if(new_merge_type >= 0 && new_merge_type <= 3 && new_merge_type != merge_type)
					    {
					        merge_type = new_merge_type;
					        apply_item.doClick();
					    }
					    else
					    {
						    System.out.println("Invalid input.");	
						    System.out.println("Must input a number from 0 to 3.");
						    segment_type_value.setText("" + segment_type);
					    }
		    	        }
		    	    });
			    
				JButton merge_type_button = new JButton("Set Merge Type");
				ActionListener button_merge_handler = new ActionListener()
				{
					public void actionPerformed(ActionEvent e)
					{
						String input_string = merge_type_value.getText();
						int new_merge_type = merge_type;
						try
						{
						    new_merge_type = Integer.parseInt(input_string);	
						}
						catch(Exception e2)
						{
						    System.out.println("Invalid input.");
						    System.out.println(e2.toString());
						    System.out.println("Must input a number from 0 to 3.");
						    merge_type_value.setText("" + merge_type);
						} 
						
						if(new_merge_type >= 0 && new_merge_type <= 3 && new_merge_type != merge_type)
						{
						    merge_type = new_merge_type;
						    if(segment_type > 0)
						        apply_item.doClick();
						}
						else
						{
							System.out.println("Invalid input.");	
							System.out.println("Must input a number from 0 to 3.");
							merge_type_value.setText("" + merge_type);
						}
					}
				};
				merge_type_button.addActionListener(button_merge_handler);
				
				JPanel merge_type_panel = new JPanel(new GridLayout(2, 1));
				merge_type_panel.add(merge_type_value);
				merge_type_panel.add(merge_type_button);
				merge_type_dialog.add(merge_type_panel);
				segmentation_menu.add(merge_type_item);
				
			
				JMenuItem merge_bin_item   = new JMenuItem("Merge Bin");
				JDialog   merge_bin_dialog = new JDialog(frame, "Merge Bin");
				ActionListener merge_bin_handler = new ActionListener()
				{
					public void actionPerformed(ActionEvent e)
					{
						Point location_point = frame.getLocation();
						int x = (int) location_point.getX();
						int y = (int) location_point.getY();

						merge_bin_dialog.setLocation(x, y - 150);
						merge_bin_dialog.pack();
						merge_bin_dialog.setVisible(true);
					}
				};
				merge_bin_item.addActionListener(merge_bin_handler);
				
				
				JTextField merge_bin_value = new JTextField();
			    merge_bin_value.setHorizontalAlignment(JTextField.CENTER);
			    merge_bin_value.setText(String.format("%.2f", merge_bin));
			    
			    
			    merge_bin_value.addActionListener(new ActionListener()
		      	{
			    	    public void actionPerformed(ActionEvent e) 
		    	        {
			            String input         = merge_bin_value.getText();   
			            double new_merge_bin = merge_bin;
					    try
					    {
					        new_merge_bin = Double.parseDouble(input);	
					    }
					    catch(Exception e2)
					    {
					        System.out.println("Invalid input.");
					        System.out.println(e2.toString());
					        System.out.println("Must input a decimal number between 0 and 1.");
					        merge_bin_value.setText(String.format("%.2f", merge_bin));
					    } 
					
					    if(new_merge_bin > 0 && new_merge_bin < 1 && new_merge_bin != merge_bin)
					    {
					        merge_bin = new_merge_bin;
					        if(segment_type > 0)
					            apply_item.doClick();
					    }
					    else
					    {
						    System.out.println("Invalid input.");	
						    System.out.println("Must input a decimal number between 0 and 1.");
						    segment_type_value.setText(String.format("%.2f", merge_bin));
					    }
		    	        }
		    	    });
			  
				JButton merge_bin_button = new JButton("Set Merge Bin");
				ActionListener button_bin_handler = new ActionListener()
				{
					public void actionPerformed(ActionEvent e)
					{
						String input_string = merge_bin_value.getText();
						double new_merge_bin = merge_bin;
						try
						{
						    new_merge_bin = Double.parseDouble(input_string);	
						}
						catch(Exception e2)
						{
						    System.out.println("Invalid input.");
						    System.out.println(e2.toString());
						    System.out.println("Must input a decimal number between 0 and 1.");
						    merge_bin_value.setText("" + merge_type);
						} 
						
						if(new_merge_bin > 0 && new_merge_bin < 1 && new_merge_bin != merge_bin)
						{
						    merge_bin = new_merge_bin;
						    if(segment_type > 0)
						        apply_item.doClick();
						}
						else
						{
							System.out.println("Invalid input.");	
							System.out.println("Must input a decimal number between 0 and 1.");
							merge_bin_value.setText(String.format("%.2f", merge_bin));
						}
					}
				};
				merge_bin_button.addActionListener(button_bin_handler);
				
				JPanel merge_bin_panel = new JPanel(new GridLayout(2, 1));
				merge_bin_panel.add(merge_bin_value);
				merge_bin_panel.add(merge_bin_button);
				merge_bin_dialog.add(merge_bin_panel);
				segmentation_menu.add(merge_bin_item);
				
				
				// Looks like something we mostly don't want to do,
				// but this makes it easy to double check.
				JMenuItem precompress_item   = new JMenuItem("Precompress");
				JDialog   precompress_dialog = new JDialog(frame, "Precompress");
				ActionListener precompress_handler = new ActionListener()
				{
					public void actionPerformed(ActionEvent e)
					{
						Point location_point = frame.getLocation();
						int x = (int) location_point.getX();
						int y = (int) location_point.getY();

						precompress_dialog.setLocation(x, y - 150);
						precompress_dialog.pack();
						precompress_dialog.setVisible(true);
					}
				};
				precompress_item.addActionListener(precompress_handler);
				
				JToggleButton precompress_button = new JToggleButton("Precompress", precompress);
				ActionListener precompress_button_handler = new ActionListener()
				{
					public void actionPerformed(ActionEvent e)
					{
						if(precompress)
						{
						    precompress = false;
						    precompress_button.setSelected(false);
						    apply_item.doClick();
						}
						else
						{
							precompress = true;
						    precompress_button.setSelected(true);	
						    apply_item.doClick();
						}
					}
				};
				precompress_button.addActionListener(precompress_button_handler);
				
				JPanel precompress_panel  = new JPanel();
				precompress_panel.add(precompress_button);
				precompress_dialog.add(precompress_panel);
				
				
				segmentation_menu.add(precompress_item);
				
				
				

				JMenu delta_menu = new JMenu("Delta");

				JRadioButtonMenuItem[] delta_button = new JRadioButtonMenuItem[8];

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

				
				
                JMenu histogram_menu = new JMenu("Histogram");
                
                
                
        		
		     	JPanel histogram_panel = new JPanel(new BorderLayout());
		     	Canvas histogram_canvas = new HistogramCanvas();
		     	histogram_canvas.setSize(522, 100);
		     	
		     	
		     	// JScrollBar histogram_scrollbar = new JScrollBar(JScrollBar.HORIZONTAL, 0, 1, 0, 101);
		        // AdjustmentListener histogram_scrollbar_handler = new AdjustmentListener()
		     	//{
		     	//    public void adjustmentValueChanged(AdjustmentEvent event)
		     	//	{
		     	//		int position = event.getValue();
		     	//		if (event.getValueIsAdjusting() == false)
		     	//		{
		     	//			System.out.println("Position is " + position);
		     	//		}
		     	//	}
		     	//};
		     	//histogram_scrollbar.addAdjustmentListener(histogram_scrollbar_handler);
		     	//histogram_panel.add(histogram_scrollbar, BorderLayout.SOUTH);
		     	
		     	
		     	histogram_panel.add(histogram_canvas, BorderLayout.CENTER);
		     	
		     	
		     	
		     	
		     	
		     	
		     	JDialog histogram_dialog = new JDialog(frame, "Histogram");
		     	histogram_dialog.add(histogram_panel);
		     	
		     	ActionListener histogram_handler = new ActionListener()
				{
					public void actionPerformed(ActionEvent e)
					{
						Point location_point = frame.getLocation();
						int x = (int) location_point.getX();
						int y = (int) location_point.getY();

						//histogram_dialog.setLocation(x, y - 150);
						histogram_dialog.setLocation(x, y - 100);
						histogram_dialog.pack();
						histogram_dialog.setVisible(true);
					}
				};
		   
                JMenuItem channel_item  = new JMenuItem("Channel");
               
                JPanel    channel_panel = new JPanel(new BorderLayout());
                
                JPanel channel_button_panel = new JPanel(new GridLayout(3, 1)); 
                
                JRadioButtonMenuItem[] channel_button = new JRadioButtonMenuItem[3];
                
                class HistogramChannelHandler implements ActionListener
				{
					int index;

					HistogramChannelHandler(int index)
					{
						this.index = index;
					}

					public void actionPerformed(ActionEvent e)
					{
						if (histogram_channel != index)
						{
							channel_button[histogram_channel].setSelected(false);
							histogram_channel = index;
							channel_button[histogram_channel].setSelected(true);
							histogram_canvas.repaint();
						} 
					}
				}

				for (int i = 0; i < 3; i++)
				{
					channel_button[i] = new JRadioButtonMenuItem();
					if(i == histogram_channel)
						channel_button[i].setSelected(true);
					channel_button[i].addActionListener(new HistogramChannelHandler(i));
					channel_button_panel.add(channel_button[i]);
				}
                
                JPanel channel_text_panel = new JPanel(new GridLayout(3, 1));
                channel_text = new JTextField[3];
                for(int i = 0; i < 3; i++)
            	        channel_text[i] = new JTextField("           ");
                if(min_set_id == 0)
                {
                	    channel_text[0].setText("blue");
                	    channel_text[1].setText("green");
                	    channel_text[2].setText("red");
                }
                else if(min_set_id == 1)
                {
                	    channel_text[0].setText("blue");
            	        channel_text[1].setText("red");
            	        channel_text[2].setText("red - green");
                }
                else if(min_set_id == 2)
                {
                	    channel_text[0].setText("blue");
            	        channel_text[1].setText("red");
            	        channel_text[2].setText("blue - green");
                }
                else if(min_set_id == 3)
                {
                	    channel_text[0].setText("blue");
            	        channel_text[1].setText("blue - green");
            	        channel_text[2].setText("red - green");
                }
                else if(min_set_id == 4)
                {
                	    channel_text[0].setText("blue");
            	        channel_text[1].setText("blue - green");
            	        channel_text[2].setText("red - blue");
                }
                else if(min_set_id == 5)
                {
                	    channel_text[0].setText("green");
            	        channel_text[1].setText("red");
            	        channel_text[2].setText("blue - green");
                }
                else if(min_set_id == 6)
                {
                	    channel_text[0].setText("red");
            	        channel_text[1].setText("blue - green");
            	        channel_text[2].setText("red - green");
                }
                else if(min_set_id == 7)
                {
                	    channel_text[0].setText("green");
        	            channel_text[1].setText("blue - green");
        	            channel_text[2].setText("red - green");
                }
                else if(min_set_id == 8)
                {
                	    channel_text[0].setText("green");
        	            channel_text[1].setText("red - green");
        	            channel_text[2].setText("red - blue");
                }
                else if(min_set_id == 9)
                {
                	    channel_text[0].setText("red");
        	            channel_text[1].setText("red - green");
        	            channel_text[2].setText("red - blue");
                }
    			
                for(int i = 0; i < 3; i++)
                	    channel_text_panel.add(channel_text[i]);
                channel_panel.add(BorderLayout.WEST, channel_button_panel);
                channel_panel.add(BorderLayout.CENTER, channel_text_panel);
                
                JDialog channel_dialog = new JDialog(frame, "Channel");
                channel_dialog.add(channel_panel);
				ActionListener channel_handler = new ActionListener()
				{
					public void actionPerformed(ActionEvent e)
					{
						Point location_point = frame.getLocation();
						int x = (int) location_point.getX();
						int y = (int) location_point.getY();

						channel_dialog.setLocation(x + 530, y - 150);
						channel_dialog.pack();
						channel_dialog.setVisible(true);
					}
				};
				channel_item.addActionListener(channel_handler);
                
                	histogram_menu.add(channel_item);   
                	
                	
                JMenuItem type_item  = new JMenuItem("Datatype");
              
                JPanel    type_panel = new JPanel(new BorderLayout());
                
                JPanel type_button_panel = new JPanel(new GridLayout(6, 1)); 
                
                JRadioButtonMenuItem[] type_button = new JRadioButtonMenuItem[6];
                
                class HistogramTypeHandler implements ActionListener
				{
					int index;

					HistogramTypeHandler(int index)
					{
						this.index = index;
					}

					public void actionPerformed(ActionEvent e)
					{
						if (histogram_type != index)
						{
							type_button[histogram_type].setSelected(false);
							histogram_type = index;
							type_button[histogram_type].setSelected(true);
						} 
					}
				}

				for (int i = 0; i < 6; i++)
				{
					type_button[i] = new JRadioButtonMenuItem();
					if(i == histogram_type)
						type_button[i].setSelected(true);
					type_button[i].addActionListener(new HistogramTypeHandler(i));
					type_button_panel.add(type_button[i]);
				}
                
                JPanel type_text_panel = new JPanel(new GridLayout(6, 1));
                JTextField [] type_text = new JTextField[6];
                type_text[0] = new JTextField("Delta strings                   ");
                type_text[1] = new JTextField("Delta strings compressed        ");
                type_text[2] = new JTextField("Delta strings zipped            ");
                type_text[3] = new JTextField("Delta strings compressed zipped ");
                type_text[4] = new JTextField("Delta strings segmented         ");
                type_text[5] = new JTextField("Delta strings segmented zipped  ");
                for(int i = 0; i < 6; i++)
                	    type_text_panel.add(type_text[i]);
                
                type_panel.add(BorderLayout.WEST, type_button_panel);
                type_panel.add(BorderLayout.CENTER, type_text_panel);
                
                JDialog type_dialog = new JDialog(frame, "Histogram Datatype");
                type_dialog.add(type_panel);
				ActionListener type_handler = new ActionListener()
				{
					public void actionPerformed(ActionEvent e)
					{
						Point location_point = frame.getLocation();
						int x = (int) location_point.getX();
						int y = (int) location_point.getY();

						type_dialog.setLocation(x + 680, y - 150);
						type_dialog.pack();
						type_dialog.setVisible(true);
					}
				};
				type_item.addActionListener(type_handler);
                
                	histogram_menu.add(type_item);   
                	
		
		     	JToggleButton show_histogram_button = new JToggleButton("Show Histogram", histogram_visible);
				ActionListener show_button_handler = new ActionListener()
				{
					public void actionPerformed(ActionEvent e)
					{
						if(histogram_visible)
						{
						    histogram_visible = false;
						    show_histogram_button.setSelected(false);
						   
						    histogram_dialog.pack();
						    histogram_dialog.setVisible(false);
						}
						else
						{
							histogram_visible = true;
							show_histogram_button.setSelected(true);	
						
							histogram_dialog.pack();
							histogram_dialog.setVisible(true);
						}
					}
				};
				show_histogram_button.addActionListener(show_button_handler);
				
				histogram_menu.add(show_histogram_button);
			
				menu_bar.add(file_menu);
				menu_bar.add(delta_menu);
				menu_bar.add(settings_menu);
				menu_bar.add(segmentation_menu);
				menu_bar.add(histogram_menu);

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

				// We could get the ideal delta sum for each channel.
				// channel_sum[i] = DeltaMapper.getIdealSum(quantized_channel, new_xdim, new_ydim);
				
				// Instead we subsample to speed up processing.  It seems to almost always produce a 
				// similar result to adding all the deltas.
				channel_sum[i] = DeltaMapper.getIdealSum(quantized_channel, new_xdim, new_ydim, 20);
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
			
			if(min_set_id == 0)
            {
            	    channel_text[0].setText("blue");
            	    channel_text[1].setText("green");
            	    channel_text[2].setText("red");
            }
            else if(min_set_id == 1)
            {
            	    channel_text[0].setText("blue");
        	        channel_text[1].setText("red");
        	        channel_text[2].setText("red - green");
            }
            else if(min_set_id == 2)
            {
            	    channel_text[0].setText("blue");
        	        channel_text[1].setText("red");
        	        channel_text[2].setText("blue - green");
            }
            else if(min_set_id == 3)
            {
            	    channel_text[0].setText("blue");
        	        channel_text[1].setText("blue - green");
        	        channel_text[2].setText("red - green");
            }
            else if(min_set_id == 4)
            {
            	    channel_text[0].setText("blue");
        	        channel_text[1].setText("blue - green");
        	        channel_text[2].setText("red - blue");
            }
            else if(min_set_id == 5)
            {
            	    channel_text[0].setText("green");
        	        channel_text[1].setText("red");
        	        channel_text[2].setText("blue - green");
            }
            else if(min_set_id == 6)
            {
            	    channel_text[0].setText("red");
        	        channel_text[1].setText("blue - green");
        	        channel_text[2].setText("red - green");
            }
            else if(min_set_id == 7)
            {
            	    channel_text[0].setText("green");
    	            channel_text[1].setText("blue - green");
    	            channel_text[2].setText("red - green");
            }
            else if(min_set_id == 8)
            {
            	    channel_text[0].setText("green");
    	            channel_text[1].setText("red - green");
    	            channel_text[2].setText("red - blue");
            }
            else if(min_set_id == 9)
            {
            	    channel_text[0].setText("red");
    	            channel_text[1].setText("red - green");
    	            channel_text[2].setText("red - blue");
            }
			
			file_compression_rate = file_length;
			file_compression_rate /= image_xdim * image_ydim * 3;

			//System.out.println("A set of channels with the lowest delta sum is " + set_string[min_index]);

			int[] channel_id = DeltaMapper.getChannels(min_set_id);

			table_list.clear();
			string_list.clear();
			segment_list.clear();
			map_list.clear();

			// Create the segment strings for each reference or difference channel
			// in the minimum set that will be used by the save handler.
			for (int i = 0; i < 3; i++)
			{
				int j = channel_id[i];

				int[] quantized_channel = quantized_channel_list.get(j);
                
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
					result = DeltaMapper.getIdealDeltasFromValues2(quantized_channel, new_xdim, new_ydim);
					byte[] map = (byte[]) result.get(2);
					map_list.add(map);
				}

				int[] delta = (int[]) result.get(1);

	
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

				int [] bit_table = StringMapper.getBitTable();
				if(segment_length == 0)
				{
					ArrayList<byte[]> segments = new ArrayList<byte[]>();
					segments.add(compression_string);
					segment_list.add(segments);
				} 
				else
				{
					int bitlength = StringMapper.getBitlength(compression_string);
					
					double zero_ratio = StringMapper.getZeroRatio(compression_string, bitlength, bit_table);
					
					int original_number_of_segments = (int) Math.pow(2, segment_length);
					int minimum_segment_length      = bitlength / original_number_of_segments;
					if(minimum_segment_length < 8)
						minimum_segment_length = 8;	
					int remainder                   = minimum_segment_length % 8;
					minimum_segment_length         -= remainder;
					
					long start = System.nanoTime();
					ArrayList segmented_list = SegmentMapper.getSegmentedData(compression_string, minimum_segment_length, segment_type, merge_type, merge_bin);
					long stop = System.nanoTime();
					long time = stop - start;
					
					System.out.println("It took " + (time / 1000000) + " ms to segment data.");
					
					ArrayList <byte[]> segments = (ArrayList <byte[]>) segmented_list.get(0);
					max_bytelength[i]           = (int) segmented_list.get(1); 
	
					segment_list.add(segments);
					
					ArrayList packed_list   = SegmentMapper.packSegments(segments);
					byte [] packed_segments = (byte [])packed_list.get(0);
					zero_ratio              =  StringMapper.getZeroRatio(packed_segments, packed_segments.length * 8, bit_table);
					System.out.println("The segments zero ratio is " + String.format("%.2f", zero_ratio));
					System.out.println();
				}
			}

			// Decompressing the data and displaying it.
			// Helps us know when something is broken without using DeltaReader.
			for (int i = 0; i < 3; i++)
			{
				int j = channel_id[i];

				int[] table = (int[]) table_list.get(i);
				int[] delta = new int[new_xdim * new_ydim];

				ArrayList segments = (ArrayList) segment_list.get(i);
				int number_of_segments = segments.size();

				if (number_of_segments == 1)
				{
					byte[] current_string = (byte[]) segments.get(0);
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
				} 
				else
				{
					byte[]  original_string = (byte [])string_list.get(i);
					byte [] restored_string = SegmentMapper.restore(segments, original_string[original_string.length - 1]);
					
					boolean same                 = true;
					int current_index            = 0;
					while(same && current_index < original_string.length)
					{
					    if(original_string[current_index] != restored_string[current_index])
					    {
					        	same = false;
					    	    System.out.println("Restored string differs from original string at index " + current_index);
					    	    System.out.println("Restored string length is " + restored_string.length);
					    	    System.out.println("Original value is " + original_string[current_index]);
					    	    System.out.println("Restored value is " + restored_string[current_index]);
					    	    System.out.println("Previous value is " + original_string[current_index - 1]);
					    	    System.out.println("Channel is " + i);
					    	    System.out.println();
					    }
					    current_index++;
					}
					
					int restored_iterations = StringMapper.getIterations(restored_string);
					if (restored_iterations == 0 || restored_iterations == 16)
					{
						int number_unpacked = StringMapper.unpackStrings2(restored_string, table, delta);
						if(number_unpacked != delta.length)
							System.out.println("Did not unpack expected number of values.");
					}
					else
					{
						byte[] decompressed_string = StringMapper.decompressStrings2(restored_string);
						int number_unpacked = StringMapper.unpackStrings2(decompressed_string, table, delta);
						if(number_unpacked != delta.length)
							System.out.println("Did not unpack expected number of values.");
					}
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
					channel    = DeltaMapper.getValuesFromIdealDeltas4(delta, new_xdim, new_ydim, channel_init[j], map);
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

					// If the string didn't compress,
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
						ArrayList map_string_list = StringMapper.getStringList2(map);
						int map_min = (int) map_string_list.get(0);
						//int map_bit_length = (int) map_string_list.get(1);
						int[] string_table = (int[]) map_string_list.get(2);
						byte[] compressed_map = (byte[]) map_string_list.get(3);

						out.writeShort(string_table.length);
						for (int k = 0; k < string_table.length; k++)
							out.writeShort(string_table[k]);
						out.writeInt(compressed_map.length);
						out.write(compressed_map, 0, compressed_map.length);
						out.writeByte(map_min);
						out.writeInt(map.length);
					}

					ArrayList segments = (ArrayList) segment_list.get(i);
					int number_of_segments = segments.size();
					out.writeInt(number_of_segments);

					Deflater deflater;
					
					if(number_of_segments == 1)
					{
						byte[] string = (byte[]) string_list.get(i);

						int unary_bit_length = StringMapper.getBitlength(string);

						int unary_length = unary_bit_length + 8 + 256 * 8;

						ArrayList huffman_list = CodeMapper.getHuffmanList(string);

						int huffman_bit_length = (int) huffman_list.get(0);
						double shannon_limit = (double) huffman_list.get(1);

						//int huffman_length = huffman_bit_length + 256 * 8 + 256 / 8;

						out.writeInt(string.length);

						int current_iterations = StringMapper.getIterations(string);
						if (current_iterations > 15)
							current_iterations -= 16;
						System.out.println("The unary string compressed " + current_iterations + " time(s).");
						
						if(deflate_type == 0)
							deflater = new Deflater(Deflater.BEST_COMPRESSION);
						else if(deflate_type == 1)
							deflater = new Deflater(Deflater.HUFFMAN_ONLY);
						else
							deflater = new Deflater(Deflater.FILTERED);
						deflater.setInput(string);
						byte[] zipped_string = new byte[2 * string.length];
						deflater.finish();
						int zipped_length = deflater.deflate(zipped_string);
						deflater.end();

						if (zipped_length < string.length)
						{
							System.out.println("Zipped channel " + i);
							out.writeInt(zipped_length);
							out.write(zipped_string, 0, zipped_length);
						} 
						else
						{
							System.out.println("Did not zip channel " + i);
							out.writeInt(0);
							out.write(string, 0, string.length);
						}
						
						System.out.println("Unary length is " + unary_length);
						System.out.println("Unary huffman length is " + huffman_bit_length);
						System.out.println("Unary zipped length is " + (zipped_length * 8));
						System.out.println("Shannon limit is " + String.format("%.1f", shannon_limit));
						System.out.println();
					} 
					else
					{
						int total_length = 0;
						byte[] segment_data = new byte[number_of_segments];
						for (int k = 0; k < number_of_segments; k++)
						{
							byte[] current_segment = (byte[]) segments.get(k);
							segment_data[k]        = current_segment[current_segment.length - 1];
						}
						
						if(deflate_type == 0)
							deflater = new Deflater(Deflater.BEST_COMPRESSION);
						else if(deflate_type == 1)
							deflater = new Deflater(Deflater.HUFFMAN_ONLY);
						else
							deflater = new Deflater(Deflater.FILTERED);
						deflater.setInput(segment_data);
						byte[] zipped_data = new byte[2 * segment_data.length];
						deflater.finish();
						int zipped_length = deflater.deflate(zipped_data);
						deflater.end();

						
						if (zipped_length < segment_data.length)
						{
							//System.out.println("Zipped channel " + i + " data.");
							out.writeInt(zipped_length);
							out.write(zipped_data, 0, zipped_length);
						} 
						else
						{
							//System.out.println("Did not zip channel " + i + " data.");
							out.writeInt(0);
							out.write(segment_data, 0, number_of_segments);
						}
						
						
						out.writeInt(max_bytelength[i]);
						
						int length_type = 0;
						byte [] segment_length = new byte[1];
						if(max_bytelength[i] > Short.MAX_VALUE * 2 + 1)
						{
							//System.out.println("Segment length fits in an int.");
							length_type = 2;
							segment_length = new byte[4 * number_of_segments];
					    }
						else if(max_bytelength[i] > Byte.MAX_VALUE * 2 + 1)
						{
							//System.out.println("Segment length fits in an unsigned short.");
							length_type = 1;
							segment_length = new byte[2 * number_of_segments];
						}
						else
						{
							//System.out.println("Segment length fits in an unsigned byte.");
							segment_length = new byte[number_of_segments];	
						}
						
						for (int k = 0; k < number_of_segments; k++)
						{
							if(length_type == 0)
							{
								byte[] current_segment = (byte[]) segments.get(k);
								segment_length[k] = (byte) (current_segment.length - 1);
								total_length += current_segment.length - 1;	
							}
							else if(length_type == 1)
							{
								byte[] current_segment    = (byte[]) segments.get(k);
								int length                = current_segment.length - 1;
								short a                   = (short)length;
							    a                        &= 0x00ff;
							    short b                   = (short)(length >> 8);
							    b                        &= 0x00ff;
							    segment_length[2 * k]     = (byte)a;
							    segment_length[2 * k + 1] = (byte)b;
								total_length += length;			
							}
							else if(length_type == 2)
							{
								byte[] current_segment    = (byte[]) segments.get(k);
								int length                = current_segment.length - 1;
								int a                     = length;
							    a                        &= 0x000000ff;
							    int b                   = length >> 8;
							    b                        &= 0x000000ff;
							    int c                     = length;
							    c                        &= 0x000000ff;
							    int d                   = length >> 8;
							    d                        &= 0x000000ff;
							    segment_length[2 * k]     = (byte)a;
							    segment_length[2 * k + 1] = (byte)b;
							    segment_length[2 * k + 2] = (byte)c;
							    segment_length[2 * k + 3] = (byte)d;
								total_length += length;	
							}
						}
						
						if(deflate_type == 0)
							deflater = new Deflater(Deflater.BEST_COMPRESSION);
						else if(deflate_type == 1)
							deflater = new Deflater(Deflater.HUFFMAN_ONLY);
						else
							deflater = new Deflater(Deflater.FILTERED);
						deflater.setInput(segment_length);
						zipped_data = new byte[2 * segment_length.length];
						deflater.finish();
						zipped_length = deflater.deflate(zipped_data);
						deflater.end();

						if (zipped_length < segment_length.length)
						{
							//System.out.println("Zipped channel " + i + " lengths.");
							out.writeInt(zipped_length);
							out.write(zipped_data, 0, zipped_length);
						} 
						else
						{
							//System.out.println("Did not zip channel " + i + " lengths.");
							out.writeInt(0);
							out.write(segment_length, 0, segment_length.length);
						}

						ArrayList packed_list = SegmentMapper.packSegments(segments);
						byte [] packed_segments = (byte [])packed_list.get(0);
						int packed_length = packed_segments.length;
					
						if(deflate_type == 0)
							deflater = new Deflater(Deflater.BEST_COMPRESSION);
						else if(deflate_type == 1)
							deflater = new Deflater(Deflater.HUFFMAN_ONLY);
						else
							deflater = new Deflater(Deflater.FILTERED);
						deflater.setInput(packed_segments);
						zipped_data = new byte[2 * packed_length];
						deflater.finish();
						zipped_length = deflater.deflate(zipped_data);
						deflater.end();
						
						if(zipped_length < packed_length)
						{
							//System.out.println("Zipped packed segments.");
							out.writeInt(zipped_length);
							out.writeInt(packed_length);
							out.write(zipped_data, 0, zipped_length);
						}
						else
						{
							out.writeInt(0);
							out.writeInt(packed_length);	
							out.write(packed_segments, 0, packed_length);
							//System.out.println("Did not zip packed segments.");
						}
					}
				}

				out.flush();
				out.close();

				File file = new File("foo");
				long file_length = file.length();
				double compression_rate = file_length;
				compression_rate /= image_xdim * image_ydim * 3;
				System.out.println("The file compression rate is " + String.format("%.4f", file_compression_rate));
				System.out.println("Delta type is " + delta_type);
				System.out.println("Delta bits compression rate is " + String.format("%.4f", compression_rate));
				System.out.println();
			} 
			catch (Exception e)
			{
				System.out.println(e.toString());
			}
		}
	}
	
	class HistogramCanvas extends Canvas
	{
		public void paint(Graphics g)
		{
			if(!initialized)
				apply_item.doClick();
			Graphics2D g2 = (Graphics2D) g;

			Rectangle visible_area = g2.getClipBounds();

			int xdim = (int) visible_area.getWidth();
			int ydim = (int) visible_area.getHeight();
			g2.setColor(java.awt.Color.WHITE);
			g2.fillRect(0, 0, xdim, ydim);
			
			int channel     = histogram_channel;
			
			System.out.println("Histogramming channel " + histogram_channel);
			int compression = histogram_type;
			int [] count    = new int[256];
			byte[] string   = (byte[]) string_list.get(channel);
			int number_of_segments = 1;
			
			if(compression == 0)
			{
				int iterations = StringMapper.getIterations(string);	
				if(iterations != 0 && iterations != 16)
					string = StringMapper.decompressStrings2(string);	
				System.out.println("Displaying packed deltas.");
			}
			else if(compression == 1)
			{
				int iterations = StringMapper.getIterations(string);	
				if(iterations == 0 || iterations == 16)
				{
				    System.out.println("String was not compressed.");
				    System.out.println("Displaying packed deltas.");
				}
				else
					System.out.println("Displaying packed compressed deltas."); 
			}
			else if(compression == 2)
			{
				int iterations = StringMapper.getIterations(string);	
				if(iterations != 0 && iterations != 16)
					string = StringMapper.decompressStrings2(string);	
				// Optionally zip segment data.
				// Deflater deflater = new Deflater();
				//Deflater deflater = new Deflater(Deflater.HUFFMAN_ONLY);
				Deflater deflater = new Deflater(Deflater.BEST_COMPRESSION);
				deflater.setInput(string);
				byte[] zipped_data = new byte[2 * string.length];
				deflater.finish();
				int zipped_length = deflater.deflate(zipped_data);
				deflater.end();

				if (zipped_length < string.length)
				{
					byte [] clipped_data = new byte[zipped_length];
					for(int i = 0; i < zipped_length; i++)
						clipped_data[i] = zipped_data[i];
					string = clipped_data;
					System.out.println("Packed deltas compressed when zipped.");
					System.out.println("Displaying zipped packed deltas.");
				} 
				else
				{
					System.out.println("Packed deltas did not compress when zipped.");
					System.out.println("Displaying zipped packed deltas.");	
				}	
			}
			else if(compression == 3)
			{
				int iterations = StringMapper.getIterations(string);	
				
				// Optionally zip segment data.
				// Deflater deflater = new Deflater();
				Deflater deflater = new Deflater(Deflater.BEST_COMPRESSION);
				deflater.setInput(string);
				byte[] zipped_data = new byte[2 * string.length];
				deflater.finish();
				int zipped_length = deflater.deflate(zipped_data);
				deflater.end();

				if(zipped_length < string.length)
				{
					byte [] clipped_data = new byte[zipped_length];
					for(int i = 0; i < zipped_length; i++)
						clipped_data[i] = zipped_data[i];
					string = clipped_data;
					if(iterations == 0 || iterations == 16)
					{
					    System.out.println("Packed deltas compressed when zipped.");
					    System.out.println("Displaying zipped packed deltas.");
					}
					else
					{
						System.out.println("Packed compressed deltas compressed when zipped.");
					    System.out.println("Displaying zipped compressed packed deltas."); 	
					}
				} 
				else
				{
					System.out.println("Packed/compressed deltas did not compress when zipped.");
					System.out.println("Displaying packed/compressed deltas.");	
				}	
			}
			else if(compression == 4 || compression == 5)
			{
				ArrayList segments = (ArrayList) segment_list.get(channel);
				number_of_segments = segments.size();	
				
				// We can't use the segment length to determine if the 
				// string is segmented because it might have been merged
				// back into an unsegmented string.
				if(number_of_segments == 1)
				{
					// Either the string was never segmented or it merged
					// back into a single string.
				    System.out.println("String was not segmented.");	
				    System.out.println("Displaying packed/compressed deltas.");
				}
				else
				{
				    ArrayList packed_list       = SegmentMapper.packSegments(segments);
					byte []   packed_segments = (byte [])packed_list.get(0);
				    string  = packed_segments;
				   
					if(compression == 5)
					{
						//Deflater deflater = new Deflater();
						Deflater deflater = new Deflater(Deflater.BEST_COMPRESSION);
						//Deflater deflater = new Deflater(Deflater.HUFFMAN_ONLY);
						deflater.setInput(string);
						byte[] zipped_data = new byte[2 * string.length];
						deflater.finish();
						int zipped_length = deflater.deflate(zipped_data);
						deflater.end();
						
						if(zipped_length < string.length)
						{
							byte [] clipped_data = new byte[zipped_length];
							for(int i = 0; i < zipped_length; i++)
								clipped_data[i] = zipped_data[i];
							string = clipped_data;
							System.out.println("Segmented data compressed when zipped.");
							System.out.println("Displaying zipped packed segments.");
						}
						else
						{
							System.out.println("Segmented data did not compress when zipped.");
							System.out.println("Displaying unzipped packed segments.");
						}
					}
				}
			}
			
			int     j    = string.length - 1;
			// If the string is concatenated segments, it does not include a trailing data byte.
			// We are histogramming the entire zipped segment until we can determine the size
			// and position of the header.
			if(number_of_segments > 1)
				j++;
			
			for(int i = 0; i < j; i++)
			{
				int k = string[i];
				if(k < 0)
					k += 256;
				count[k]++;
			}
			
			int number_of_different_values = 0;
			for(int i = 0; i < 256; i++)
			{
				if(count[i] != 0)
					number_of_different_values++;  
			}
			
			int max   = count[0];
			int index = 0;
			
			for(int i = 1; i < 256; i++)
			{
				if(count[i] > max)
				{
				    max   = count[i];
				    index = i;
				}
			}
			
			System.out.println("Number of different values is " + number_of_different_values);
			System.out.println("Max instances is " + max + " out of " + (string.length - 1) + " at index " + index);
			System.out.println();
			
			g2.setColor(java.awt.Color.BLACK);
			
			double width = xdim;
			width    /= 256;
		
			int offset = 5;
			int increment = (int)width;
			for(int i = 0; i < 256; i++)
			{
				double k = count[i];
				k       /= max;
				k       *= ydim;
				
				int height = (int)k;
				int delta  = ydim - height;
				
				if(height != 0)
				    g2.fillRect(offset , delta, increment, height);
				else
				{
					g2.setColor(java.awt.Color.RED);
					g2.drawLine(offset , delta, offset + increment, delta);
					g2.setColor(java.awt.Color.BLACK);
				}
				offset += increment;
			}
		}
	}
}