import java.awt.*;
import java.awt.image.*;
import java.io.*;
import java.math.*;
import java.awt.event.*;
import java.awt.geom.AffineTransform;
import java.util.*;
import java.util.zip.*;
import java.util.zip.Deflater.*;
import javax.imageio.*;
import javax.swing.*;
import javax.swing.event.*;


public class ArithmeticWriter
{
	BufferedImage original_image;
	BufferedImage working_image;
	BufferedImage display_image;
	ImageCanvas image_canvas;
	JScrollPane scroll_pane;
	int xdim, ydim;
	int canvas_xdim, canvas_ydim;
	int screen_xdim, screen_ydim;
	JMenuItem apply_item;
	String filename;
	int[] pixel;

	int pixel_quant    = 4;
	int pixel_shift    = 3;
	int pixel_segment  = 0;
	int correction     = 0;
	int min_set_id     = 0;
	int delta_type     = 5;
	int compress_type  = 1;

	double zoom_scale = 1.0;
	double fit_scale  = 1.0;

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
	int[] min_bytelength;

	long file_length;
	double file_compression_rate;
	JRadioButtonMenuItem[] delta_button;
	JFrame frame = null;

	ArrayList<Object> channel_list, table_list, string_list, map_list, delta_list;

	BigInteger[][] offset;
	int[][]        frequency;
	byte[][]       decoded_segment;
	byte[][]       order;

	boolean initialized = false;

	static final double ZOOM_FACTOR = 1.25;
	static final double ZOOM_MIN    = 0.05;
	static final double ZOOM_MAX    = 32.0;

	public static void main(String[] args)
	{
		if (args.length != 1)
		{
			System.out.println("Usage: java ArithmeticWriter <filename>");
			System.exit(0);
		}
		String prefix   = new String("");
		String filename = new String(args[0]);
		ArithmeticWriter writer = new ArithmeticWriter(prefix + filename);
	}

	public void init()
	{
		System.out.println("Loaded file.");
		System.out.println("Image xdim = " + xdim + ", ydim = " + ydim);
		System.out.println();

		ArrayList<int[]> quantized_channel_list = new ArrayList<int[]>();

		int new_xdim = xdim;
		int new_ydim = ydim;
		if (pixel_quant != 0)
		{
			double factor = pixel_quant / 10.0;
			new_xdim = xdim - (int)(factor * (xdim / 2 - 2));
			new_ydim = ydim - (int)(factor * (ydim / 2 - 2));
		}

		for (int i = 0; i < 3; i++)
		{
			int[] channel = (int[]) channel_list.get(i);
			if (pixel_quant == 0)
			{
				if (pixel_shift == 0) quantized_channel_list.add(channel);
				else quantized_channel_list.add(DeltaMapper.shift(channel, -pixel_shift));
			}
			else
			{
				int[] resized = ResizeMapper.resize(channel, xdim, new_xdim, new_ydim);
				if (pixel_shift == 0) quantized_channel_list.add(resized);
				else quantized_channel_list.add(DeltaMapper.shift(resized, -pixel_shift));
			}
		}

		int[] qb = quantized_channel_list.get(0);
		int[] qg = quantized_channel_list.get(1);
		int[] qr = quantized_channel_list.get(2);
		quantized_channel_list.add(DeltaMapper.getDifference(qb, qg));
		quantized_channel_list.add(DeltaMapper.getDifference(qr, qg));
		quantized_channel_list.add(DeltaMapper.getDifference(qr, qb));

		for (int i = 0; i < 6; i++)
		{
			int min = 256;
			int[] quantized_channel = quantized_channel_list.get(i);
			for (int j = 0; j < quantized_channel.length; j++)
				if (quantized_channel[j] < min) min = quantized_channel[j];
			channel_min[i] = min;
			if (i > 2)
				for (int j = 0; j < quantized_channel.length; j++)
					quantized_channel[j] -= min;
			channel_init[i] = quantized_channel[0];
			quantized_channel_list.set(i, quantized_channel);
			int[] freq  = DeltaMapper.getIdealFrequency(quantized_channel, new_xdim, new_ydim);
			channel_sum[i] = (int) Math.floor(CodeMapper.getShannonLimit(freq));
		}

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

		int min_index     = 0;
		int min_delta_sum = Integer.MAX_VALUE;
		for (int i = 0; i < 10; i++)
			if (set_sum[i] < min_delta_sum) { min_delta_sum = set_sum[i]; min_index = i; }
		min_set_id = min_index;

		int[] channel_id      = DeltaMapper.getChannels(min_set_id);
		int[] total_delta_sum = new int[9];

		for (int i = 0; i < 3; i++)
		{
			int   j                 = channel_id[i];
			int[] quantized_channel = quantized_channel_list.get(j);

			for (int t = 0; t < 5; t++)
				total_delta_sum[t] += (int) Math.floor(CodeMapper.getShannonLimit(
						DeltaMapper.getFrequency(quantized_channel, new_xdim, new_ydim, t)));

			ArrayList<int[]> result;
			int[]  freq;
			double shannon_limit;
			int    shannon_sum;

			result        = DeltaMapper.getMedScanlineFrequency(quantized_channel, new_xdim, new_ydim);
			freq          = result.get(0);
			shannon_limit = CodeMapper.getShannonLimit(freq);
			shannon_sum   = (int) Math.floor(shannon_limit);
			shannon_sum  += result.get(1).length / 4;
			total_delta_sum[5] += shannon_sum;

			result        = DeltaMapper.getScanline2Frequency(quantized_channel, new_xdim, new_ydim);
			freq          = result.get(0);
			shannon_limit = CodeMapper.getShannonLimit(freq);
			shannon_sum   = (int) Math.floor(shannon_limit);
			shannon_sum  += result.get(1).length / 4;
			total_delta_sum[6] += shannon_sum;

			result        = DeltaMapper.getMixedDeltas4Frequency(quantized_channel, new_xdim, new_ydim);
			freq          = result.get(0);
			shannon_limit = CodeMapper.getShannonLimit(freq);
			shannon_sum   = (int) Math.floor(shannon_limit);
			shannon_sum  += result.get(1).length / 4;
			total_delta_sum[7] += shannon_sum;
		}

		min_delta_sum = total_delta_sum[0];
		min_index     = 0;
		for (int i = 1; i < 8; i++)
			if (total_delta_sum[i] < min_delta_sum) { min_delta_sum = total_delta_sum[i]; min_index = i; }
		delta_type = min_index;
		System.out.println("The delta type that produces the smallest entropy sum is " + delta_type_string[delta_type]);
	}

	public ArithmeticWriter(String _filename)
	{
		filename = _filename;
		try
		{
			File file = new File(filename);
			file_length    = file.length();
			original_image = ImageIO.read(file);
			int raster_type = original_image.getType();
			xdim = original_image.getWidth();
			ydim = original_image.getHeight();

			channel_list = new ArrayList<Object>();
			table_list   = new ArrayList<Object>();
			string_list  = new ArrayList<Object>();
			map_list     = new ArrayList<Object>();
			delta_list   = new ArrayList<Object>();

			channel_string = new String[]{"blue","green","red","blue-green","red-green","red-blue"};

			set_sum    = new int[10];
			set_string = new String[]{
				"blue, green, and red.",
				"blue, red, and red-green.",
				"blue, red, and blue-green.",
				"blue, blue-green, and red-green.",
				"blue, blue-green, and red-blue.",
				"green, red, and blue-green.",
				"red, blue-green, and red-green.",
				"green, blue-green, and red-green.",
				"green, red-green, and red-blue.",
				"red, red-green, red-blue."
			};

			delta_type_string    = new String[9];
			delta_type_string[0] = new String("horizontal");
			delta_type_string[1] = new String("vertical");
			delta_type_string[2] = new String("average");
			delta_type_string[3] = new String("med");
			delta_type_string[4] = new String("directional");
			delta_type_string[5] = new String("scanline (1)");
			delta_type_string[6] = new String("scanline (2)");
			delta_type_string[7] = new String("scanline (3)");
			delta_type_string[8] = new String("frame map");

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

			if (raster_type == BufferedImage.TYPE_3BYTE_BGR)
			{
				pixel = new int[xdim * ydim];
				PixelGrabber pixel_grabber = new PixelGrabber(original_image, 0, 0, xdim, ydim, pixel, 0, xdim);
				try { pixel_grabber.grabPixels(); }
				catch (InterruptedException e) { System.err.println(e.toString()); }
				if ((pixel_grabber.getStatus() & ImageObserver.ABORT) != 0)
				{
					System.err.println("Error grabbing pixels.");
					System.exit(1);
				}

				int[] blue  = new int[xdim * ydim];
				int[] green = new int[xdim * ydim];
				int[] red   = new int[xdim * ydim];
				for (int i = 0; i < xdim * ydim; i++)
				{
					blue[i]  = (pixel[i] >> 16) & 0xff;
					green[i] = (pixel[i] >> 8)  & 0xff;
					red[i]   =  pixel[i]         & 0xff;
				}
				channel_list.add(blue);
				channel_list.add(green);
				channel_list.add(red);

				working_image = new BufferedImage(xdim, ydim, BufferedImage.TYPE_INT_RGB);
				for (int i = 0; i < xdim; i++)
					for (int j = 0; j < ydim; j++)
						working_image.setRGB(i, j, pixel[j * xdim + i]);

				Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
				screen_xdim = (int) screenSize.getWidth();
				screen_ydim = (int) screenSize.getHeight();

				int max_canvas_w = (int)(screen_xdim * 0.70) - 40;
				int max_canvas_h = (int)(screen_ydim * 0.70) - 80;
				fit_scale  = Math.min(1.0, Math.min((double)max_canvas_w/xdim, (double)max_canvas_h/ydim));
				zoom_scale = fit_scale;

				image_canvas = new ImageCanvas();
				scroll_pane = new JScrollPane(image_canvas,
						JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
						JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
				scroll_pane.getVerticalScrollBar().setUnitIncrement(16);
				scroll_pane.getHorizontalScrollBar().setUnitIncrement(16);

				scroll_pane.addMouseWheelListener(new MouseWheelListener()
				{
					public void mouseWheelMoved(MouseWheelEvent e)
					{
						if (e.isControlDown())
						{
							JViewport vp       = scroll_pane.getViewport();
							Point     view_pos = vp.getViewPosition();
							Point     mouse_pt = e.getPoint();
							int mouse_canvas_x = mouse_pt.x + view_pos.x;
							int mouse_canvas_y = mouse_pt.y + view_pos.y;

							double old_scale = zoom_scale;
							if (e.getWheelRotation() < 0)
								zoom_scale = Math.min(ZOOM_MAX, zoom_scale * ZOOM_FACTOR);
							else
								zoom_scale = Math.max(ZOOM_MIN, zoom_scale / ZOOM_FACTOR);
							if (zoom_scale == old_scale) return;

							updateDisplayImage();
							image_canvas.setPreferredSize(new Dimension((int)(xdim*zoom_scale),(int)(ydim*zoom_scale)));
							image_canvas.revalidate(); image_canvas.repaint();

							double ratio = zoom_scale / old_scale;
							vp.setViewPosition(new Point(
									Math.max(0, (int)(mouse_canvas_x*ratio) - mouse_pt.x),
									Math.max(0, (int)(mouse_canvas_y*ratio) - mouse_pt.y)));
							updateTitle();
						}
						else
						{
							scroll_pane.dispatchEvent(e);
						}
					}
				});

				frame = new JFrame("Arithmetic Deltas " + filename);
				frame.addWindowListener(new WindowAdapter()
				{
					public void windowClosing(WindowEvent event) { System.exit(0); }
				});
				frame.getContentPane().add(scroll_pane, BorderLayout.CENTER);

				JMenuBar menu_bar = new JMenuBar();
				JMenu file_menu   = new JMenu("File");

				apply_item = new JMenuItem("Apply");
				apply_item.addActionListener(new ApplyHandler());
				file_menu.add(apply_item);

				JMenuItem reload_item = new JMenuItem("Reload");
				reload_item.addActionListener(new ActionListener()
				{
					public void actionPerformed(ActionEvent event)
					{
						display_image = original_image;
						updateDisplayImage();
						image_canvas.setPreferredSize(new Dimension((int)(xdim*zoom_scale),(int)(ydim*zoom_scale)));
						image_canvas.revalidate(); image_canvas.repaint();
						System.out.println("Reloaded original image.");
					}
				});
				file_menu.add(reload_item);

				JMenuItem save_item = new JMenuItem("Save");
				save_item.addActionListener(new SaveHandler());
				file_menu.add(save_item);

				JMenu view_menu = new JMenu("View");

				JMenuItem zoom_in_item = new JMenuItem("Zoom In (+)");
				zoom_in_item.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_EQUALS, InputEvent.CTRL_DOWN_MASK));
				zoom_in_item.addActionListener(new ActionListener()
				{
					public void actionPerformed(ActionEvent e) { zoomBy(ZOOM_FACTOR); }
				});
				view_menu.add(zoom_in_item);

				JMenuItem zoom_out_item = new JMenuItem("Zoom Out (-)");
				zoom_out_item.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_MINUS, InputEvent.CTRL_DOWN_MASK));
				zoom_out_item.addActionListener(new ActionListener()
				{
					public void actionPerformed(ActionEvent e) { zoomBy(1.0 / ZOOM_FACTOR); }
				});
				view_menu.add(zoom_out_item);

				JMenuItem zoom_fit_item = new JMenuItem("Fit to Window");
				zoom_fit_item.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_0, InputEvent.CTRL_DOWN_MASK));
				zoom_fit_item.addActionListener(new ActionListener()
				{
					public void actionPerformed(ActionEvent e)
					{
						Dimension vp_size = scroll_pane.getViewport().getSize();
						zoom_scale = Math.min((double)vp_size.width/xdim, (double)vp_size.height/ydim);
						updateDisplayImage();
						image_canvas.setPreferredSize(new Dimension((int)(xdim*zoom_scale),(int)(ydim*zoom_scale)));
						image_canvas.revalidate(); image_canvas.repaint(); updateTitle();
					}
				});
				view_menu.add(zoom_fit_item);

				JMenuItem zoom_actual_item = new JMenuItem("Actual Size (100%)");
				zoom_actual_item.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_1, InputEvent.CTRL_DOWN_MASK));
				zoom_actual_item.addActionListener(new ActionListener()
				{
					public void actionPerformed(ActionEvent e)
					{
						zoom_scale = 1.0;
						updateDisplayImage();
						image_canvas.setPreferredSize(new Dimension(xdim, ydim));
						image_canvas.revalidate(); image_canvas.repaint(); updateTitle();
					}
				});
				view_menu.add(zoom_actual_item);

				JMenu settings_menu = new JMenu("Quantization");

				JMenuItem quant_item   = new JMenuItem("Pixel Resolution");
				JDialog   quant_dialog = new JDialog(frame, "Pixel Resolution");
				quant_item.addActionListener(new ActionListener()
				{
					public void actionPerformed(ActionEvent e)
					{
						Point p = frame.getLocation();
						quant_dialog.setLocation((int)p.getX(), (int)p.getY() - 50);
						quant_dialog.pack(); quant_dialog.setVisible(true);
					}
				});
				JPanel    quant_panel  = new JPanel(new BorderLayout());
				JSlider   quant_slider = new JSlider(0, 10, pixel_quant);
				JTextField quant_value = new JTextField(3);
				quant_value.setText(" " + pixel_quant + " ");
				quant_slider.addChangeListener(new ChangeListener()
				{
					public void stateChanged(ChangeEvent e)
					{
						JSlider s = (JSlider) e.getSource();
						pixel_quant = s.getValue();
						quant_value.setText(" " + pixel_quant + " ");
						if (!s.getValueIsAdjusting()) apply_item.doClick();
					}
				});
				quant_panel.add(quant_slider, BorderLayout.CENTER);
				quant_panel.add(quant_value,  BorderLayout.EAST);
				quant_dialog.add(quant_panel);
				settings_menu.add(quant_item);

				JMenuItem shift_item   = new JMenuItem("Color Resolution");
				JDialog   shift_dialog = new JDialog(frame, "Color Resolution");
				shift_item.addActionListener(new ActionListener()
				{
					public void actionPerformed(ActionEvent e)
					{
						Point p = frame.getLocation();
						shift_dialog.setLocation((int)p.getX(), (int)p.getY() - 100);
						shift_dialog.pack(); shift_dialog.setVisible(true);
					}
				});
				JPanel    shift_panel  = new JPanel(new BorderLayout());
				JSlider   shift_slider = new JSlider(0, 7, pixel_shift);
				JTextField shift_value = new JTextField(3);
				shift_value.setText(" " + pixel_shift + " ");
				shift_slider.addChangeListener(new ChangeListener()
				{
					public void stateChanged(ChangeEvent e)
					{
						JSlider s = (JSlider) e.getSource();
						pixel_shift = s.getValue();
						shift_value.setText(" " + pixel_shift + " ");
						if (!s.getValueIsAdjusting()) apply_item.doClick();
					}
				});
				shift_panel.add(shift_slider, BorderLayout.CENTER);
				shift_panel.add(shift_value,  BorderLayout.EAST);
				shift_dialog.add(shift_panel);
				settings_menu.add(shift_item);

				JMenuItem segment_item   = new JMenuItem("Segment Length");
				JDialog   segment_dialog = new JDialog(frame, "Segment Length");
				segment_item.addActionListener(new ActionListener()
				{
					public void actionPerformed(ActionEvent e)
					{
						Point p = frame.getLocation();
						segment_dialog.setLocation((int)p.getX(), (int)p.getY() - 50);
						segment_dialog.pack(); segment_dialog.setVisible(true);
					}
				});
				JPanel    segment_panel  = new JPanel(new BorderLayout());
				JSlider   segment_slider = new JSlider(0, 9, pixel_segment);
				JTextField segment_value = new JTextField(3);
				segment_value.setText(" " + pixel_segment + " ");
				segment_slider.addChangeListener(new ChangeListener()
				{
					public void stateChanged(ChangeEvent e)
					{
						JSlider s = (JSlider) e.getSource();
						pixel_segment = s.getValue();
						if (!s.getValueIsAdjusting()) segment_value.setText(" " + pixel_segment + " ");
					}
				});
				segment_panel.add(segment_slider, BorderLayout.CENTER);
				segment_panel.add(segment_value,  BorderLayout.EAST);
				segment_dialog.add(segment_panel);
				settings_menu.add(segment_item);

				JMenuItem correction_item   = new JMenuItem("Error Correction");
				JDialog   correction_dialog = new JDialog(frame, "Error Correction");
				correction_item.addActionListener(new ActionListener()
				{
					public void actionPerformed(ActionEvent e)
					{
						Point p = frame.getLocation();
						correction_dialog.setLocation((int)p.getX(), (int)p.getY() - 150);
						correction_dialog.pack(); correction_dialog.setVisible(true);
					}
				});
				JPanel    correction_panel  = new JPanel(new BorderLayout());
				JSlider   correction_slider = new JSlider(0, 10, correction);
				JTextField correction_value = new JTextField(3);
				correction_value.setText(" " + correction + " ");
				correction_slider.addChangeListener(new ChangeListener()
				{
					public void stateChanged(ChangeEvent e)
					{
						JSlider s = (JSlider) e.getSource();
						correction = s.getValue();
						correction_value.setText(" " + correction + " ");
						if (!s.getValueIsAdjusting()) apply_item.doClick();
					}
				});
				correction_panel.add(correction_slider, BorderLayout.CENTER);
				correction_panel.add(correction_value,  BorderLayout.EAST);
				correction_dialog.add(correction_panel);
				settings_menu.add(correction_item);

				JMenu datatype_menu = new JMenu("Datatype");
				JRadioButtonMenuItem[] datatype_button = new JRadioButtonMenuItem[]{
					new JRadioButtonMenuItem("Integer"),
					new JRadioButtonMenuItem("String"),
					new JRadioButtonMenuItem("String*")
				};
				datatype_button[compress_type].setSelected(true);
				ButtonGroup datatype_group = new ButtonGroup();
				for (int i = 0; i < 3; i++) datatype_group.add(datatype_button[i]);

				class DatatypeButtonHandler implements ActionListener
				{
					int index;
					DatatypeButtonHandler(int index) { this.index = index; }
					public void actionPerformed(ActionEvent e)
					{
						if (compress_type != index) { compress_type = index; apply_item.doClick(); }
					}
				}
				for (int i = 0; i < 3; i++)
				{
					datatype_button[i].addActionListener(new DatatypeButtonHandler(i));
					datatype_menu.add(datatype_button[i]);
				}

				JMenu delta_menu = new JMenu("Delta");
				delta_button    = new JRadioButtonMenuItem[9];
				delta_button[0] = new JRadioButtonMenuItem("H");
				delta_button[1] = new JRadioButtonMenuItem("V");
				delta_button[2] = new JRadioButtonMenuItem("Average");
				delta_button[3] = new JRadioButtonMenuItem("MED");
				delta_button[4] = new JRadioButtonMenuItem("Directional");
				delta_button[5] = new JRadioButtonMenuItem("Scanline 1");
				delta_button[6] = new JRadioButtonMenuItem("Scanline 2");
				delta_button[7] = new JRadioButtonMenuItem("Scanline 3");
				delta_button[8] = new JRadioButtonMenuItem("Map");

				ButtonGroup delta_group = new ButtonGroup();
				for (int i = 0; i < 9; i++) delta_group.add(delta_button[i]);
				delta_button[delta_type].setSelected(true);

				class ButtonHandler implements ActionListener
				{
					int index;
					ButtonHandler(int index) { this.index = index; }
					public void actionPerformed(ActionEvent e)
					{
						if (delta_type != index) { delta_type = index; apply_item.doClick(); }
					}
				}
				for (int i = 0; i < 9; i++)
				{
					delta_button[i].addActionListener(new ButtonHandler(i));
					delta_menu.add(delta_button[i]);
				}

				menu_bar.add(file_menu);
				menu_bar.add(view_menu);
				menu_bar.add(datatype_menu);
				menu_bar.add(delta_menu);
				menu_bar.add(settings_menu);
				frame.setJMenuBar(menu_bar);

				display_image = original_image;
				image_canvas.setPreferredSize(new Dimension((int)(xdim*zoom_scale),(int)(ydim*zoom_scale)));
				updateTitle();

				frame.setSize(Math.min(xdim + 40, (int)(screen_xdim * 0.70)),
				              Math.min(ydim + 80,  (int)(screen_ydim * 0.70)));
				frame.setLocation(5, 5);
				frame.setVisible(true);

				SwingUtilities.invokeLater(() -> showInitialImage());
			}
		}
		catch (Exception e)
		{
			System.out.println(e.getMessage());
			System.exit(1);
		}
	}

	private void showInitialImage()
	{
		Dimension vp_size = scroll_pane.getViewport().getSize();
		double xs = (vp_size.width  > 0) ? (double) vp_size.width  / xdim : 1.0;
		double ys = (vp_size.height > 0) ? (double) vp_size.height / ydim : 1.0;
		fit_scale  = Math.min(1.0, Math.min(xs, ys));
		zoom_scale = fit_scale;
		updateDisplayImage();
		image_canvas.setPreferredSize(new Dimension((int)(xdim*zoom_scale),(int)(ydim*zoom_scale)));
		image_canvas.revalidate(); image_canvas.repaint(); updateTitle();
		init();
		delta_button[delta_type].setSelected(true);
	}

	private void zoomBy(double factor)
	{
		double new_scale = Math.max(ZOOM_MIN, Math.min(ZOOM_MAX, zoom_scale * factor));
		if (new_scale == zoom_scale) return;

		JViewport vp      = scroll_pane.getViewport();
		Point     vp_pos  = vp.getViewPosition();
		Dimension vp_size = vp.getSize();
		double centre_x   = vp_pos.x + vp_size.width  / 2.0;
		double centre_y   = vp_pos.y + vp_size.height / 2.0;
		double ratio       = new_scale / zoom_scale;
		zoom_scale         = new_scale;

		updateDisplayImage();
		image_canvas.setPreferredSize(new Dimension((int)(xdim*zoom_scale),(int)(ydim*zoom_scale)));
		image_canvas.revalidate(); image_canvas.repaint();
		vp.setViewPosition(new Point(
				Math.max(0, (int)(centre_x * ratio - vp_size.width  / 2.0)),
				Math.max(0, (int)(centre_y * ratio - vp_size.height / 2.0))));
		updateTitle();
	}

	private void updateDisplayImage()
	{
		BufferedImage source = (working_image != null && initialized) ? working_image : original_image;
		if (zoom_scale == 1.0)
		{
			display_image = source;
		}
		else
		{
			int w = Math.max(1, (int)(xdim * zoom_scale));
			int h = Math.max(1, (int)(ydim * zoom_scale));
			AffineTransform t = new AffineTransform();
			t.scale(zoom_scale, zoom_scale);
			AffineTransformOp op = new AffineTransformOp(t, AffineTransformOp.TYPE_BILINEAR);
			display_image = new BufferedImage(w, h, source.getType());
			display_image = op.filter(source, display_image);
		}
	}

	private void updateTitle()
	{
		int pct = (int) Math.round(zoom_scale * 100);
		frame.setTitle("Arithmetic Deltas " + filename + "  [" + pct + "%]");
	}

	class ImageCanvas extends JPanel
	{
		public ImageCanvas() { setOpaque(true); }

		@Override
		public Dimension getPreferredSize()
		{
			int w = (display_image != null) ? display_image.getWidth()  : (int)(xdim * zoom_scale);
			int h = (display_image != null) ? display_image.getHeight() : (int)(ydim * zoom_scale);
			return new Dimension(w, h);
		}

		@Override
		protected synchronized void paintComponent(Graphics g)
		{
			super.paintComponent(g);
			if (display_image != null) g.drawImage(display_image, 0, 0, this);
		}
	}

	class ApplyHandler implements ActionListener
	{
		public void actionPerformed(ActionEvent event)
		{
			ArrayList<int[]> quantized_channel_list   = new ArrayList<int[]>();
			ArrayList<int[]> dequantized_channel_list = new ArrayList<int[]>();

			int new_xdim = xdim;
			int new_ydim = ydim;
			if (pixel_quant != 0)
			{
				double factor = pixel_quant / 10.0;
				new_xdim = xdim - (int)(factor * (xdim / 2 - 2));
				new_ydim = ydim - (int)(factor * (ydim / 2 - 2));
			}

			for (int i = 0; i < 3; i++)
			{
				int[] channel = (int[]) channel_list.get(i);
				if (pixel_quant == 0)
				{
					if (pixel_shift == 0) quantized_channel_list.add(channel);
					else quantized_channel_list.add(DeltaMapper.shift(channel, -pixel_shift));
				}
				else
				{
					int[] resized = ResizeMapper.resize(channel, xdim, new_xdim, new_ydim);
					if (pixel_shift == 0) quantized_channel_list.add(resized);
					else quantized_channel_list.add(DeltaMapper.shift(resized, -pixel_shift));
				}
			}

			int[] qb = quantized_channel_list.get(0);
			int[] qg = quantized_channel_list.get(1);
			int[] qr = quantized_channel_list.get(2);
			quantized_channel_list.add(DeltaMapper.getDifference(qb, qg));
			quantized_channel_list.add(DeltaMapper.getDifference(qr, qg));
			quantized_channel_list.add(DeltaMapper.getDifference(qr, qb));

			for (int i = 0; i < 6; i++)
			{
				int min = 256;
				int[] quantized_channel = quantized_channel_list.get(i);
				for (int j = 0; j < quantized_channel.length; j++)
					if (quantized_channel[j] < min) min = quantized_channel[j];
				channel_min[i] = min;
				if (i > 2)
					for (int j = 0; j < quantized_channel.length; j++)
						quantized_channel[j] -= min;
				channel_init[i] = quantized_channel[0];
				quantized_channel_list.set(i, quantized_channel);
				int[] freq  = DeltaMapper.getIdealFrequency(quantized_channel, new_xdim, new_ydim);
				channel_sum[i] = (int) Math.floor(CodeMapper.getShannonLimit(freq));
			}

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

			int min_index     = 0;
			int min_delta_sum = Integer.MAX_VALUE;
			for (int i = 0; i < 10; i++)
				if (set_sum[i] < min_delta_sum) { min_delta_sum = set_sum[i]; min_index = i; }
			min_set_id = min_index;

			file_compression_rate  = file_length;
			file_compression_rate /= xdim * ydim * 3;
			System.out.println("A set of channels with the lowest entropy sum is " + set_string[min_index]);

			int[] channel_id = DeltaMapper.getChannels(min_set_id);

			table_list.clear();
			string_list.clear();
			map_list.clear();
			delta_list.clear();

			for (int i = 0; i < 3; i++)
			{
				int j = channel_id[i];
				int[] quantized_channel = quantized_channel_list.get(j);

				ArrayList<Object> result = new ArrayList<Object>();
				if      (delta_type == 0) result = DeltaMapper.getHorizontalDeltasFromValues(quantized_channel, new_xdim, new_ydim);
				else if (delta_type == 1) result = DeltaMapper.getVerticalDeltasFromValues(quantized_channel, new_xdim, new_ydim);
				else if (delta_type == 2) result = DeltaMapper.getAverageDeltasFromValues(quantized_channel, new_xdim, new_ydim);
				else if (delta_type == 3) result = DeltaMapper.getMedDeltasFromValues(quantized_channel, new_xdim, new_ydim);
				else if (delta_type == 4) result = DeltaMapper.getDirectionalDeltasFromValues(quantized_channel, new_xdim, new_ydim);
				else if (delta_type == 5) { result = DeltaMapper.getMixedDeltasFromValues(quantized_channel, new_xdim, new_ydim);  map_list.add((byte[]) result.get(2)); }
				else if (delta_type == 6) { result = DeltaMapper.getMixedDeltasFromValues2(quantized_channel, new_xdim, new_ydim); map_list.add((byte[]) result.get(2)); }
				else if (delta_type == 7) { result = DeltaMapper.getMixedDeltasFromValues4(quantized_channel, new_xdim, new_ydim); map_list.add((byte[]) result.get(2)); }
				else if (delta_type == 8) { result = DeltaMapper.getIdealDeltasFromValues(quantized_channel, new_xdim, new_ydim);  map_list.add((byte[]) result.get(2)); }

				int[] delta = (int[]) result.get(1);

				if (compress_type == 0)
				{
					ArrayList histogram_list = StringMapper.getHistogram(delta);
					int       delta_min_val  = (int) histogram_list.get(0);
					int[]     histogram      = (int[]) histogram_list.get(1);
					int[]     string_table   = StringMapper.getRankTable(histogram);
					channel_delta_min[j]     = delta_min_val;
					table_list.add(string_table);

					byte[] delta_bytes = new byte[delta.length];
					delta_bytes[0] = 0;
					for (int k = 1; k < delta.length; k++)
						delta_bytes[k] = (byte)(delta[k] - delta_min_val);
					delta_list.add(delta_bytes);

					channel_length[j]           = delta.length;
					channel_compressed_length[j] = delta.length;
					channel_iterations[i]        = 0;

					for (int k = 1; k < delta.length; k++)
						delta[k] = (delta_bytes[k] & 0xFF) + delta_min_val;
				}
				else
				{
					boolean precompress         = (compress_type == 2);
					ArrayList delta_string_list = StringMapper.getStringList(delta, precompress);
					channel_delta_min[j]        = (int)    delta_string_list.get(0);
					channel_length[j]           = (int)    delta_string_list.get(1);
					int[]  string_table         = (int[])  delta_string_list.get(2);
					byte[] compression_string   = (byte[]) delta_string_list.get(3);
					table_list.add(string_table);
					string_list.add(compression_string);
					channel_compressed_length[j] = StringMapper.getBitlength(compression_string);
					channel_string_type[i]       = StringMapper.getType(compression_string);
					channel_iterations[i]        = StringMapper.getIterations(compression_string);
					for (int k = 1; k < delta.length; k++)
						delta[k] += channel_delta_min[j];
				}
			}

			for (int i = 0; i < 3; i++)
			{
				int    j     = channel_id[i];
				int[]  table = (int[]) table_list.get(i);
				int[]  delta;

				if (compress_type == 0)
				{
					byte[] delta_bytes = (byte[]) delta_list.get(i);
					delta    = new int[new_xdim * new_ydim];
					delta[0] = 0;
					for (int k = 1; k < delta.length; k++)
						delta[k] = (delta_bytes[k] & 0xFF) + channel_delta_min[j];
				}
				else
				{
					byte[] current_string = (byte[]) string_list.get(i);
					int    bitlength      = StringMapper.getBitlength(current_string);
					current_string        = StringMapper.decompressStrings(current_string);
					delta                 = StringMapper.unpackStrings(current_string, table, new_xdim * new_ydim, bitlength);
					for (int k = 1; k < delta.length; k++)
						delta[k] += channel_delta_min[j];
				}

				int[] channel = new int[0];
				if      (delta_type == 0) channel = DeltaMapper.getValuesFromHorizontalDeltas(delta, new_xdim, new_ydim, channel_init[j]);
				else if (delta_type == 1) channel = DeltaMapper.getValuesFromVerticalDeltas(delta, new_xdim, new_ydim, channel_init[j]);
				else if (delta_type == 2) channel = DeltaMapper.getValuesFromAverageDeltas(delta, new_xdim, new_ydim, channel_init[j]);
				else if (delta_type == 3) channel = DeltaMapper.getValuesFromMedDeltas(delta, new_xdim, new_ydim, channel_init[j]);
				else if (delta_type == 4) channel = DeltaMapper.getValuesFromDirectionalDeltas(delta, new_xdim, new_ydim, channel_init[j]);
				else if (delta_type == 5) channel = DeltaMapper.getValuesFromMixedDeltas(delta, new_xdim, new_ydim, channel_init[j], (byte[]) map_list.get(i));
				else if (delta_type == 6) channel = DeltaMapper.getValuesFromMixedDeltas2(delta, new_xdim, new_ydim, channel_init[j], (byte[]) map_list.get(i));
				else if (delta_type == 7) channel = DeltaMapper.getValuesFromMixedDeltas4(delta, new_xdim, new_ydim, channel_init[j], (byte[]) map_list.get(i));
				else if (delta_type == 8) channel = DeltaMapper.getValuesFromIdealDeltas(delta, new_xdim, new_ydim, channel_init[j], (byte[]) map_list.get(i));

				if (j > 2)
					for (int k = 0; k < channel.length; k++) channel[k] += channel_min[j];

				if (pixel_shift == 0)
				{
					if (pixel_quant == 0) dequantized_channel_list.add(channel);
					else dequantized_channel_list.add(ResizeMapper.resize(channel, new_xdim, xdim, ydim));
				}
				else
				{
					int[] shifted_channel = DeltaMapper.shift(channel, pixel_shift);
					if (pixel_quant == 0) dequantized_channel_list.add(shifted_channel);
					else dequantized_channel_list.add(ResizeMapper.resize(shifted_channel, new_xdim, xdim, ydim));
				}
			}

			int[] blue  = new int[xdim * ydim];
			int[] green = new int[xdim * ydim];
			int[] red   = new int[xdim * ydim];

			if (min_set_id == 0)
			{
				blue  = dequantized_channel_list.get(0);
				green = dequantized_channel_list.get(1);
				red   = dequantized_channel_list.get(2);
			}
			else if (min_set_id == 1)
			{
				blue  = dequantized_channel_list.get(0);
				red   = dequantized_channel_list.get(1);
				green = DeltaMapper.getDifference(red, dequantized_channel_list.get(2));
			}
			else if (min_set_id == 2)
			{
				blue  = dequantized_channel_list.get(0);
				red   = dequantized_channel_list.get(1);
				green = DeltaMapper.getDifference(blue, dequantized_channel_list.get(2));
			}
			else if (min_set_id == 3)
			{
				blue  = dequantized_channel_list.get(0);
				green = DeltaMapper.getDifference(blue, dequantized_channel_list.get(1));
				red   = DeltaMapper.getSum(dequantized_channel_list.get(2), green);
			}
			else if (min_set_id == 4)
			{
				blue  = dequantized_channel_list.get(0);
				green = DeltaMapper.getDifference(blue, dequantized_channel_list.get(1));
				red   = DeltaMapper.getSum(blue, dequantized_channel_list.get(2));
			}
			else if (min_set_id == 5)
			{
				green = dequantized_channel_list.get(0);
				red   = dequantized_channel_list.get(1);
				blue  = DeltaMapper.getSum(dequantized_channel_list.get(2), green);
			}
			else if (min_set_id == 6)
			{
				red = dequantized_channel_list.get(0);
				int[] blue_green = dequantized_channel_list.get(1);
				int[] red_green  = dequantized_channel_list.get(2);
				for (int i = 0; i < red_green.length; i++) red_green[i] = -red_green[i];
				green = DeltaMapper.getSum(red_green, red);
				blue  = DeltaMapper.getSum(blue_green, green);
			}
			else if (min_set_id == 7)
			{
				green = dequantized_channel_list.get(0);
				blue  = DeltaMapper.getSum(green, dequantized_channel_list.get(1));
				red   = DeltaMapper.getSum(green, dequantized_channel_list.get(2));
			}
			else if (min_set_id == 8)
			{
				green = dequantized_channel_list.get(0);
				red   = DeltaMapper.getSum(green, dequantized_channel_list.get(1));
				blue  = DeltaMapper.getDifference(red, dequantized_channel_list.get(2));
			}
			else if (min_set_id == 9)
			{
				red   = dequantized_channel_list.get(0);
				green = DeltaMapper.getDifference(red, dequantized_channel_list.get(1));
				blue  = DeltaMapper.getDifference(red, dequantized_channel_list.get(2));
			}

			int[] original_blue  = (int[]) channel_list.get(0);
			int[] original_green = (int[]) channel_list.get(1);
			int[] original_red   = (int[]) channel_list.get(2);
			int[][] error = new int[3][xdim * ydim];

			for (int i = 0; i < xdim * ydim; i++)
			{
				error[0][i] = original_blue[i]  - blue[i];
				error[1][i] = original_green[i] - green[i];
				error[2][i] = original_red[i]   - red[i];
				if (correction != 0)
				{
					double factor = correction / 10.0;
					blue[i]  += (int)(error[0][i] * factor);
					green[i] += (int)(error[1][i] * factor);
					red[i]   += (int)(error[2][i] * factor);
				}
			}

			int k = 0;
			for (int i = 0; i < ydim; i++)
				for (int j = 0; j < xdim; j++)
				{
					working_image.setRGB(j, i, (blue[k] << 16) + (green[k] << 8) + red[k]);
					k++;
				}

			updateDisplayImage();
			image_canvas.repaint();
			System.out.println("Loaded quantized image.");
			System.out.println();
			initialized = true;
		}
	}

	class SaveHandler implements ActionListener
	{
	    public void actionPerformed(ActionEvent event)
	    {
	        if (!initialized) apply_item.doClick();
	        int channel_id[] = DeltaMapper.getChannels(min_set_id);

	        int[][]          all_number_of_segments = new int[3][];
	        int[][]          all_segment_lengths     = new int[3][];
	        int[][]          all_odd_segment_lengths = new int[3][];
	        BigInteger[][][] all_offsets             = new BigInteger[3][][];
	        int[][][]        all_frequencies         = new int[3][][];
	        byte[][][]       all_decoded_segments    = new byte[3][][];
	        byte[][]         all_strings             = new byte[3][];
	        byte[][][]       all_segments            = new byte[3][][];
	        int[]            all_length_types        = new int[3];
	        byte[][]         all_zipped_frequencies  = new byte[3][];
	        int[]            all_freq_zipped_lengths = new int[3];
	        Thread[][][]     all_encoder_threads     = new Thread[3][][];

	        int[] process_order = new int[]{ 0, 1, 2 };
	        if (compress_type > 0)
	        {
	            int[] lengths = new int[3];
	            for (int i = 0; i < 3; i++)
	                lengths[i] = ((byte[]) string_list.get(i)).length;
	            for (int i = 1; i < 3; i++)
	                for (int j = i; j > 0 && lengths[process_order[j-1]] < lengths[process_order[j]]; j--)
	                {
	                    int tmp            = process_order[j];
	                    process_order[j]   = process_order[j-1];
	                    process_order[j-1] = tmp;
	                }
	            System.out.println("Processing channel order: "
	                + process_order[0] + ", " + process_order[1] + ", " + process_order[2]
	                + " (descending string length)");
	        }

	        int[] inv_order = new int[3];
	        for (int i = 0; i < 3; i++) inv_order[process_order[i]] = i;

	        long encode_start = System.nanoTime();

	        for (int pi = 0; pi < 3; pi++)
	        {
	            int i = process_order[pi];

	            byte[] string;
	            if (compress_type == 0) string = (byte[]) delta_list.get(i);
	            else                    string = (byte[]) string_list.get(i);

	            int minimum_segment_length = 500 + (pixel_segment * 500);
	            int number_of_segments     = Math.max(1, string.length / minimum_segment_length);
	            System.out.println("Number of segments for channel " + i + " is " + number_of_segments);

	            int segment_length     = string.length / number_of_segments;
	            int odd_segment_length = segment_length + string.length % number_of_segments;

	            byte[][]       segment     = new byte[number_of_segments][];
	            int[][]        freq        = new int[number_of_segments][256];
	            byte[][]       decoded_seg = new byte[number_of_segments][];
	            BigInteger[][] offsets     = new BigInteger[number_of_segments][2];

	            for (int m = 0; m < number_of_segments; m++)
	                segment[m] = (m < number_of_segments - 1) ? new byte[segment_length] : new byte[odd_segment_length];
	            for (int m = 0; m < number_of_segments - 1; m++)
	                decoded_seg[m] = new byte[segment_length];
	            decoded_seg[number_of_segments - 1] = new byte[odd_segment_length];

	            int k = 0;
	            for (int m = 0; m < number_of_segments - 1; m++)
	                for (int n = 0; n < segment_length; n++)
	                {
	                    segment[m][n] = string[k];
	                    int p = string[k]; if (p < 0) p += 256;
	                    freq[m][p]++; k++;
	                }
	            int m = number_of_segments - 1;
	            for (int n = 0; n < odd_segment_length; n++)
	            {
	                segment[m][n] = string[k];
	                int p = string[k]; if (p < 0) p += 256;
	                freq[m][p]++; k++;
	            }

	            Thread[] encoder_thread = new Thread[number_of_segments];
	            for (k = 0; k < number_of_segments; k++)
	            {
	                final int    seg_idx  = k;
	                final byte[] seg_data = segment[k];
	                final int[]  seg_freq = freq[k];
	                encoder_thread[k] = new Thread(() ->
	                    offsets[seg_idx] = ArithmeticMapper.getIntervalValue(seg_data, seg_freq));
	                encoder_thread[k].start();
	            }

	            all_number_of_segments[i]  = new int[]{ number_of_segments };
	            all_segment_lengths[i]     = new int[]{ segment_length };
	            all_odd_segment_lengths[i] = new int[]{ odd_segment_length };
	            all_offsets[i]             = offsets;
	            all_frequencies[i]         = freq;
	            all_decoded_segments[i]    = decoded_seg;
	            all_strings[i]             = string;
	            all_segments[i]            = segment;
	            all_encoder_threads[i]     = new Thread[][]{ encoder_thread };
	        }
	        System.out.println();

	        for (int pi = 0; pi < 3; pi++)
	        {
	            int      i                  = process_order[pi];
	            Thread[] encoder_thread     = all_encoder_threads[i][0];
	            int      number_of_segments = all_number_of_segments[i][0];
	            for (int k = 0; k < number_of_segments; k++)
	                try { encoder_thread[k].join(); }
	                catch (Exception e) { System.out.println("Encoder join: " + e); }
	        }

	        long encTime = System.nanoTime() - encode_start;
	        System.out.println("It took " + (pixel_segment < 3
	            ? (encTime / 1_000_000) + " ms"
	            : (encTime / 1_000_000_000) + " secs")
	            + " to encode all channels.");

	        long decode_start = System.nanoTime();

	        for (int pi = 0; pi < 3; pi++)
	        {
	            int            i                  = process_order[pi];
	            int            number_of_segments = all_number_of_segments[i][0];
	            BigInteger[][] offsets            = all_offsets[i];
	            int[][]        freq               = all_frequencies[i];
	            byte[][]       decoded_seg        = all_decoded_segments[i];
	            byte[][]       segment            = all_segments[i];

	            Thread[] decoder_thread = new Thread[number_of_segments];
	            for (int k = 0; k < number_of_segments; k++)
	            {
	                final int seg_idx    = k;
	                final int seg_length = segment[k].length;
	                decoder_thread[k] = new Thread(() ->
	                    decoded_seg[seg_idx] =
	                        ArithmeticMapper.getArithmeticValues(offsets[seg_idx], freq[seg_idx], seg_length));
	                decoder_thread[k].start();
	            }
	            all_encoder_threads[i][0] = decoder_thread;
	        }

	        for (int pi = 0; pi < 3; pi++)
	        {
	            int      i                  = process_order[pi];
	            int      number_of_segments = all_number_of_segments[i][0];
	            Thread[] decoder_thread     = all_encoder_threads[i][0];
	            byte[][] decoded_seg        = all_decoded_segments[i];
	            byte[]   string             = all_strings[i];

	            for (int k = 0; k < number_of_segments; k++)
	                try { decoder_thread[k].join(); }
	                catch (Exception e) { System.out.println("Decoder join: " + e); }

	            byte[] string2       = new byte[string.length];
	            int    string_offset = 0;
	            for (int mm = 0; mm < decoded_seg.length; mm++)
	            {
	                System.arraycopy(decoded_seg[mm], 0, string2, string_offset, decoded_seg[mm].length);
	                string_offset += decoded_seg[mm].length;
	            }

	            boolean isSame    = true;
	            int     first_idx = 0;
	            for (int k = 0; k < string.length; k++)
	                if (string[k] != string2[k]) { isSame = false; first_idx = k; break; }

	            if (!isSame)
	            {
	                System.out.println("Strings are not equal.");
	                System.out.println("String differed at index " + first_idx);
	                System.out.println("String length is " + string.length);
	                System.out.println("String 1 value is " + string[first_idx]
	                    + ", string 2 value is " + string2[first_idx]);
	            }
	            else
	                System.out.println("Strings are equal for channel " + i + ".");
	        }

	        long decTime = System.nanoTime() - decode_start;
	        System.out.println("It took " + (pixel_segment < 3
	            ? (decTime / 1_000_000) + " ms"
	            : (decTime / 1_000_000_000) + " secs")
	            + " to decode all channels.");
	        System.out.println();

	        Thread[] deflater_thread = new Thread[3];
	        for (int pi = 0; pi < 3; pi++)
	        {
	            int     i                  = process_order[pi];
	            final int fi               = i;
	            int     number_of_segments = all_number_of_segments[i][0];
	            int[][] freq               = all_frequencies[i];

	            int frequency_max = 0;
	            for (int k = 0; k < number_of_segments; k++)
	                for (int mm = 0; mm < freq[k].length; mm++)
	                    if (freq[k][mm] > frequency_max) frequency_max = freq[k][mm];

	            int length_type;
	            if      (frequency_max < Byte.MAX_VALUE  * 2 + 2) { System.out.println("Frequency max for channel " + i + " fits in an unsigned byte.");  length_type = 0; }
	            else if (frequency_max < Short.MAX_VALUE * 2 + 2) { System.out.println("Frequency max for channel " + i + " fits in an unsigned short."); length_type = 1; }
	            else                                               { System.out.println("Frequency max for channel " + i + " fits in an integer.");        length_type = 2; }

	            int    bytes_per_entry = (length_type == 0) ? 1 : (length_type == 1) ? 2 : 4;
	            byte[] frequency_bytes = new byte[number_of_segments * 256 * bytes_per_entry];
	            for (int k = 0; k < number_of_segments; k++)
	                for (int mm = 0; mm < 256; mm++)
	                {
	                    int v    = freq[k][mm];
	                    int base = k * 256 * bytes_per_entry + mm * bytes_per_entry;
	                    for (int b = 0; b < bytes_per_entry; b++)
	                        frequency_bytes[base + b] = (byte)(v >> (8 * b));
	                }

	            all_length_types[i] = length_type;
	            final byte[] freq_bytes_for_thread = frequency_bytes;
	            final int    segs_for_thread       = number_of_segments;
	            deflater_thread[pi] = new Thread(() ->
	            {
	                Deflater deflater         = new Deflater(Deflater.BEST_COMPRESSION);
	                byte[]   zipped_freq_data = new byte[freq_bytes_for_thread.length];
	                deflater.setInput(freq_bytes_for_thread);
	                deflater.finish();
	                int freq_zipped_length = deflater.deflate(zipped_freq_data);
	                deflater.end();
	                System.out.println("Original length of frequency tables for channel " + fi + " is " + (segs_for_thread * 256 * 4));
	                System.out.println("Zipped length for channel " + fi + " is " + freq_zipped_length);
	                all_zipped_frequencies[fi]  = zipped_freq_data;
	                all_freq_zipped_lengths[fi] = freq_zipped_length;
	            });
	            deflater_thread[pi].start();
	        }
	        for (int pi = 0; pi < 3; pi++)
	            try { deflater_thread[pi].join(); }
	            catch (Exception e) { System.out.println("Deflater join: " + e); }
	        System.out.println();

	        try
	        {
	            DataOutputStream out = new DataOutputStream(new FileOutputStream(new File("foo")));

	            out.writeShort(xdim);
	            out.writeShort(ydim);
	            out.writeByte(pixel_shift);
	            out.writeByte(pixel_quant);
	            out.writeByte(min_set_id);
	            out.writeByte(delta_type);
	            out.writeByte(compress_type);

	            for (int i = 0; i < 3; i++)
	            {
	                int j = channel_id[i];

	                out.writeInt(channel_min[j]);
	                out.writeInt(channel_init[j]);
	                out.writeInt(channel_delta_min[j]);
	                out.writeInt(channel_length[j]);
	                out.writeInt(channel_compressed_length[j]);
	                out.writeByte(channel_iterations[i]);

	                int[] table = (int[]) table_list.get(i);
	                out.writeShort(table.length);
	                int max_byte_value = Byte.MAX_VALUE * 2 + 1;
	                if (table.length <= max_byte_value)
	                    for (int k = 0; k < table.length; k++) out.writeByte(table[k]);
	                else
	                    for (int k = 0; k < table.length; k++) out.writeShort(table[k]);

	                if (delta_type == 5 || delta_type == 6 || delta_type == 7 || delta_type == 8)
	                {
	                    byte[] map        = (byte[]) map_list.get(i);
	                    byte[] packed_map = SegmentMapper.packBits(map, 2);
	                    out.writeInt(map.length);
	                    out.writeInt(packed_map.length);
	                    out.write(packed_map, 0, packed_map.length);
	                }

	                int            number_of_segments = all_number_of_segments[i][0];
	                int            length_type        = all_length_types[i];
	                byte[]         zipped_freq_data   = all_zipped_frequencies[i];
	                int            freq_zipped_length = all_freq_zipped_lengths[i];
	                BigInteger[][] offsets            = all_offsets[i];

	                out.writeInt(number_of_segments);
	                out.writeInt(length_type);
	                out.writeInt(freq_zipped_length);
	                out.write(zipped_freq_data, 0, freq_zipped_length);

	                for (int k = 0; k < number_of_segments; k++)
	                {
	                    byte[] bytes = offsets[k][0].toByteArray();
	                    out.writeInt(bytes.length);
	                    out.write(bytes, 0, bytes.length);
	                    bytes = offsets[k][1].toByteArray();
	                    out.writeInt(bytes.length);
	                    out.write(bytes, 0, bytes.length);
	                }
	            }

	            out.flush();
	            out.close();

	            File   file             = new File("foo");
	            long   file_length_out  = file.length();
	            double compression_rate = (double) file_length_out / (xdim * ydim * 3);
	            System.out.println("The file compression rate is " + String.format("%.4f", file_compression_rate));
	            System.out.println("Delta bits compression rate is " + String.format("%.4f", compression_rate));
	            System.out.println();
	        }
	        catch (Exception e)
	        {
	            System.out.println(e.toString());
	        }
	    }
	}

	class ArithmeticEncoder implements Runnable
	{
		byte[] src;
		int[]  frequency;
		int    index;

		public ArithmeticEncoder(byte[] src, int[] frequency, int index)
		{
			this.src       = src;
			this.frequency = frequency;
			this.index     = index;
		}

		public void run()
		{
			offset[index] = ArithmeticMapper.getIntervalValue(src, frequency);
		}
	}

	class ArithmeticDecoder implements Runnable
	{
		BigInteger[] offset;
		int[]        frequency;
		int          n;
		int          index;

		public ArithmeticDecoder(BigInteger[] offset, int[] frequency, int n, int index)
		{
			this.offset    = offset;
			this.frequency = frequency;
			this.index     = index;
			this.n         = n;
		}

		public void run()
		{
			decoded_segment[index] = ArithmeticMapper.getArithmeticValues(offset, frequency, n);
		}
	}
}
