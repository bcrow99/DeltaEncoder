import java.awt.*;
import java.awt.image.*;
import java.io.*;
import java.awt.event.*;
import java.util.*;
import java.util.zip.*;
import java.lang.Math.*;
import java.awt.geom.AffineTransform;
import javax.imageio.*;
import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

public class HuffmanWriter
{
	BufferedImage original_image;
	BufferedImage working_image;
	BufferedImage display_image;
	ImageCanvas   image_canvas;
	JScrollPane   scroll_pane;
	int           xdim, ydim;
	int           screen_xdim, screen_ydim;
	JMenuItem     apply_item;
	String        filename;
	int []        pixel;

	int pixel_quant    = 4;
	int pixel_shift    = 3;
	int segment_length = 0;
	int correction     = 0;
	int min_set_id     = 0;
	int delta_type     = 2;
	int compress_type  = 1;

	// zoom_scale is the current zoom level (1.0 = 100%)
	double zoom_scale = 1.0;
	double fit_scale  = 1.0;

	int    [] set_sum, channel_sum;
	String [] set_string;
	String [] delta_type_string;
	String [] channel_string;

	int [] channel_init;
	int [] channel_min;
	int [] channel_delta_min;

	int  [] channel_string_length;
	int  [] channel_string_type;
	int  [] number_of_segments;
	byte [] channel_iterations;

	int  [] channel_huffman_length;

	int [] max_bytelength;

	long   file_length;
	double file_compression_rate;

	ArrayList huffman_table_list, string_table_list;
	ArrayList channel_list, string_list, map_list, delta_list;
	ArrayList code_list, code_length_list;

	JFrame  frame       = null;
	boolean initialized = false;

	// Zoom constants
	static final double ZOOM_FACTOR = 1.25;
	static final double ZOOM_MIN    = 0.05;
	static final double ZOOM_MAX    = 32.0;

	public static void main(String[] args)
	{
		if (args.length != 1)
		{
			System.out.println("Usage: java HuffmanWriter <filename>");
			System.exit(0);
		}
		String prefix   = new String("");
		String filename = new String(args[0]);
		HuffmanWriter writer = new HuffmanWriter(prefix + filename);
		writer.init();
	}

	public void init()
	{
		System.out.println("Loaded file.");
		System.out.println("Image xdim = " + xdim + ", ydim = " + ydim);
		System.out.println();

		// Shift then resize, matching HuffmanWriter's quantization convention.
		ArrayList<int[]> quantized_channel_list = new ArrayList<int[]>();

		int new_xdim = xdim;
		int new_ydim = ydim;
		if (pixel_quant != 0)
		{
			double factor = pixel_quant;
			factor       /= 10;
			new_xdim = xdim - (int)(factor * (xdim / 2 - 2));
			new_ydim = ydim - (int)(factor * (ydim / 2 - 2));
		}

		for (int i = 0; i < 3; i++)
		{
			int [] channel = (int []) channel_list.get(i);
			if (pixel_quant == 0)
			{
				if (pixel_shift == 0)
					quantized_channel_list.add(channel);
				else
				{
					int [] shifted_channel = DeltaMapper.shift(channel, -pixel_shift);
					quantized_channel_list.add(shifted_channel);
				}
			}
			else
			{
				int [] resized_channel = ResizeMapper.resize(channel, xdim, new_xdim, new_ydim);
				if (pixel_shift == 0)
					quantized_channel_list.add(resized_channel);
				else
				{
					int [] shifted_channel = DeltaMapper.shift(resized_channel, -pixel_shift);
					quantized_channel_list.add(shifted_channel);
				}
			}
		}

		int [] quantized_blue  = quantized_channel_list.get(0);
		int [] quantized_green = quantized_channel_list.get(1);
		int [] quantized_red   = quantized_channel_list.get(2);

		int [] quantized_blue_green = DeltaMapper.getDifference(quantized_blue, quantized_green);
		int [] quantized_red_green  = DeltaMapper.getDifference(quantized_red,  quantized_green);
		int [] quantized_red_blue   = DeltaMapper.getDifference(quantized_red,  quantized_blue);

		quantized_channel_list.add(quantized_blue_green);
		quantized_channel_list.add(quantized_red_green);
		quantized_channel_list.add(quantized_red_blue);

		for (int i = 0; i < 6; i++)
		{
			int min = 256;
			int [] quantized_channel = quantized_channel_list.get(i);

			for (int j = 0; j < quantized_channel.length; j++)
				if (quantized_channel[j] < min)
					min = quantized_channel[j];
			channel_min[i] = min;

			if (i > 2)
				for (int j = 0; j < quantized_channel.length; j++)
					quantized_channel[j] -= min;

			channel_init[i] = quantized_channel[0];
			quantized_channel_list.set(i, quantized_channel);

			int [] frequency     = DeltaMapper.getIdealFrequency(quantized_channel, new_xdim, new_ydim);
			double shannon_limit = CodeMapper.getShannonLimit(frequency);
			channel_sum[i]       = (int)Math.floor(shannon_limit);
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
		

		int [] channel_id        = DeltaMapper.getChannels(min_set_id);
		int [] channel_delta_sum = new int[7];

		for (int i = 0; i < 3; i++)
		{
			int j = channel_id[i];
			int [] quantized_channel = quantized_channel_list.get(j);

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

			ArrayList<int[]> result = DeltaMapper.getScanlineFrequency(quantized_channel, new_xdim, new_ydim);
			frequency     = result.get(0);
			shannon_limit = CodeMapper.getShannonLimit(frequency);
			shannon_sum   = (int)Math.floor(shannon_limit);
			channel_delta_sum[5] = shannon_sum;
			int [] map = result.get(1);
			channel_delta_sum[5] += map.length / 4;

			result        = DeltaMapper.getScanline2Frequency(quantized_channel, new_xdim, new_ydim);
			frequency     = result.get(0);
			shannon_limit = CodeMapper.getShannonLimit(frequency);
			shannon_sum   = (int)Math.floor(shannon_limit);
			channel_delta_sum[6] = shannon_sum;
			map = result.get(1);
			channel_delta_sum[6] += map.length / 4;
		}

		min_delta_sum = channel_delta_sum[0];
		min_index     = 0;
		for (int i = 1; i < 7; i++)
		{
			if (channel_delta_sum[i] < min_delta_sum)
			{
				min_delta_sum = channel_delta_sum[i];
				min_index = i;
			}
		}
		
		delta_type = min_index;
		System.out.println("A set of channels with the lowest entropy sum is " + set_string[min_set_id]);
		System.out.println("The delta type that produces the smallest entropy sum is " + delta_type_string[delta_type]);
	}

	public HuffmanWriter(String _filename)
	{
		filename = _filename;
		try
		{
			File file = new File(filename);
			file_length               = file.length();
			original_image            = ImageIO.read(file);
			int raster_type           = original_image.getType();
			xdim                      = original_image.getWidth();
			ydim                      = original_image.getHeight();

			System.out.println("Xdim = " + xdim + ", ydim = " + ydim);

			channel_list       = new ArrayList();
			huffman_table_list = new ArrayList();
			string_table_list  = new ArrayList();
			string_list        = new ArrayList();
			map_list           = new ArrayList();
			delta_list         = new ArrayList();
			code_list          = new ArrayList();
			code_length_list   = new ArrayList();

			channel_string    = new String[6];
			channel_string[0] = new String("blue");
			channel_string[1] = new String("green");
			channel_string[2] = new String("red");
			channel_string[3] = new String("blue-green");
			channel_string[4] = new String("red-green");
			channel_string[5] = new String("red-blue");

			set_sum       = new int[10];
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

			delta_type_string    = new String[8];
			delta_type_string[0] = new String("horizontal");
			delta_type_string[1] = new String("vertical");
			delta_type_string[2] = new String("average");
			delta_type_string[3] = new String("paeth");
			delta_type_string[4] = new String("gradient");
			delta_type_string[5] = new String("scanline (1)");
			delta_type_string[6] = new String("scanline (2)");
			delta_type_string[7] = new String("frame map");

			channel_init         = new int[6];
			channel_min          = new int[6];
			channel_delta_min    = new int[6];
			channel_sum          = new int[6];
			channel_huffman_length = new int[6];
			channel_string_length  = new int[6];

			channel_string_type = new int[3];
			channel_iterations  = new byte[3];
			max_bytelength      = new int[3];

			if (raster_type == BufferedImage.TYPE_3BYTE_BGR)
			{
				pixel = new int[xdim * ydim];
				PixelGrabber pixel_grabber = new PixelGrabber(original_image, 0, 0, xdim, ydim, pixel, 0, xdim);
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

				int [] alpha = new int[xdim * ydim];
				int [] blue  = new int[xdim * ydim];
				int [] green = new int[xdim * ydim];
				int [] red   = new int[xdim * ydim];
				for (int i = 0; i < xdim * ydim; i++)
				{
					alpha[i] = (pixel[i] >> 24) & 0xff;
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

				// ── Screen / scale setup ────────────────────────────────────
				Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
				screen_xdim = (int) screenSize.getWidth();
				screen_ydim = (int) screenSize.getHeight();

				// Cap the initial window at 70% of the screen in each dimension.
				int max_canvas_w = (int)(screen_xdim * 0.70) - 40;
				int max_canvas_h = (int)(screen_ydim * 0.70) - 80;
				double xscale    = (double) max_canvas_w / xdim;
				double yscale    = (double) max_canvas_h / ydim;
				fit_scale        = Math.min(1.0, Math.min(xscale, yscale));
				zoom_scale       = fit_scale;

				// ── Canvas and scroll pane ──────────────────────────────────
				image_canvas = new ImageCanvas();
				image_canvas.setPreferredSize(new Dimension(
						Math.max(1, (int)(xdim * zoom_scale)),
						Math.max(1, (int)(ydim * zoom_scale))));

				scroll_pane = new JScrollPane(image_canvas,
						JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
						JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
				scroll_pane.getVerticalScrollBar().setUnitIncrement(16);
				scroll_pane.getHorizontalScrollBar().setUnitIncrement(16);

				// Ctrl+wheel zooms; plain wheel scrolls.
				scroll_pane.addMouseWheelListener(new MouseWheelListener()
				{
					public void mouseWheelMoved(MouseWheelEvent e)
					{
						if (e.isControlDown())
						{
							JViewport vp          = scroll_pane.getViewport();
							Point     view_pos    = vp.getViewPosition();
							Point     mouse_pt    = e.getPoint();
							int       mouse_can_x = mouse_pt.x + view_pos.x;
							int       mouse_can_y = mouse_pt.y + view_pos.y;

							double old_scale = zoom_scale;
							if (e.getWheelRotation() < 0)
								zoom_scale = Math.min(ZOOM_MAX, zoom_scale * ZOOM_FACTOR);
							else
								zoom_scale = Math.max(ZOOM_MIN, zoom_scale / ZOOM_FACTOR);

							if (zoom_scale == old_scale) return;

							updateDisplayImage();
							image_canvas.setPreferredSize(new Dimension(
									(int)(xdim * zoom_scale),
									(int)(ydim * zoom_scale)));
							image_canvas.revalidate();
							image_canvas.repaint();

							double ratio = zoom_scale / old_scale;
							int new_vx   = (int)(mouse_can_x * ratio) - mouse_pt.x;
							int new_vy   = (int)(mouse_can_y * ratio) - mouse_pt.y;
							vp.setViewPosition(new Point(Math.max(0, new_vx), Math.max(0, new_vy)));

							updateTitle();
						}
						else
						{
							scroll_pane.dispatchEvent(e);
						}
					}
				});

				// ── Frame ───────────────────────────────────────────────────
				frame = new JFrame("Huffman Writer " + filename);
				WindowAdapter window_handler = new WindowAdapter()
				{
					public void windowClosing(WindowEvent event) { System.exit(0); }
				};
				frame.addWindowListener(window_handler);
				frame.getContentPane().add(scroll_pane, BorderLayout.CENTER);

				// ── Menu bar ────────────────────────────────────────────────
				JMenuBar menu_bar = new JMenuBar();
				JMenu file_menu   = new JMenu("File");

				apply_item = new JMenuItem("Apply");
				ApplyHandler apply_handler = new ApplyHandler();
				apply_item.addActionListener(apply_handler);
				file_menu.add(apply_item);

				JMenuItem reload_item = new JMenuItem("Reload");
				ActionListener reload_handler = new ActionListener()
				{
					public void actionPerformed(ActionEvent event)
					{
						for (int i = 0; i < xdim; i++)
							for (int j = 0; j < ydim; j++)
								working_image.setRGB(i, j, pixel[j * xdim + i]);
						initialized = false;
						updateDisplayImage();
						image_canvas.repaint();
						System.out.println("Reloaded original image.");
					}
				};
				reload_item.addActionListener(reload_handler);
				file_menu.add(reload_item);

				JMenuItem save_item = new JMenuItem("Save");
				SaveHandler save_handler = new SaveHandler();
				save_item.addActionListener(save_handler);
				file_menu.add(save_item);

				// ── View / Zoom menu ────────────────────────────────────────
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
						double xs = (double) vp_size.width  / xdim;
						double ys = (double) vp_size.height / ydim;
						zoom_scale = Math.min(xs, ys);
						updateDisplayImage();
						image_canvas.setPreferredSize(new Dimension(
								(int)(xdim * zoom_scale),
								(int)(ydim * zoom_scale)));
						image_canvas.revalidate();
						image_canvas.repaint();
						updateTitle();
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
						image_canvas.revalidate();
						image_canvas.repaint();
						updateTitle();
					}
				});
				view_menu.add(zoom_actual_item);

				// ── Settings menu ───────────────────────────────────────────
				JMenu settings_menu = new JMenu("Settings");

				JMenuItem quant_item   = new JMenuItem("Pixel Resolution");
				JDialog   quant_dialog = new JDialog(frame, "Pixel Resolution");
				ActionListener quant_handler = new ActionListener()
				{
					public void actionPerformed(ActionEvent e)
					{
						Point location_point = frame.getLocation();
						quant_dialog.setLocation((int)location_point.getX(), (int)location_point.getY() - 50);
						quant_dialog.pack();
						quant_dialog.setVisible(true);
					}
				};
				quant_item.addActionListener(quant_handler);

				JPanel     quant_panel  = new JPanel(new BorderLayout());
				JSlider    quant_slider = new JSlider();
				quant_slider.setMinimum(0);
				quant_slider.setMaximum(10);
				quant_slider.setValue(pixel_quant);
				JTextField quant_value  = new JTextField(3);
				quant_value.setText(" " + pixel_quant + " ");
				ChangeListener quant_slider_handler = new ChangeListener()
				{
					public void stateChanged(ChangeEvent e)
					{
						JSlider slider = (JSlider) e.getSource();
						pixel_quant = slider.getValue();
						quant_value.setText(" " + pixel_quant + " ");
						if (slider.getValueIsAdjusting() == false)
							apply_item.doClick();
					}
				};
				quant_slider.addChangeListener(quant_slider_handler);
				quant_panel.add(quant_slider, BorderLayout.CENTER);
				quant_panel.add(quant_value,  BorderLayout.EAST);
				quant_dialog.add(quant_panel);
				settings_menu.add(quant_item);

				JMenuItem shift_item   = new JMenuItem("Color Resolution");
				JDialog   shift_dialog = new JDialog(frame, "Color Resolution");
				ActionListener shift_handler = new ActionListener()
				{
					public void actionPerformed(ActionEvent e)
					{
						Point location_point = frame.getLocation();
						shift_dialog.setLocation((int)location_point.getX(), (int)location_point.getY() - 100);
						shift_dialog.pack();
						shift_dialog.setVisible(true);
					}
				};
				shift_item.addActionListener(shift_handler);

				JPanel     shift_panel  = new JPanel(new BorderLayout());
				JSlider    shift_slider = new JSlider();
				shift_slider.setMinimum(0);
				shift_slider.setMaximum(7);
				shift_slider.setValue(pixel_shift);
				JTextField shift_value  = new JTextField(3);
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
				shift_panel.add(shift_value,  BorderLayout.EAST);
				shift_dialog.add(shift_panel);
				settings_menu.add(shift_item);

				JMenuItem correction_item   = new JMenuItem("Error Correction");
				JDialog   correction_dialog = new JDialog(frame, "Error Correction");
				ActionListener correction_handler = new ActionListener()
				{
					public void actionPerformed(ActionEvent e)
					{
						Point location_point = frame.getLocation();
						correction_dialog.setLocation((int)location_point.getX(), (int)location_point.getY() - 200);
						correction_dialog.pack();
						correction_dialog.setVisible(true);
					}
				};
				correction_item.addActionListener(correction_handler);

				JPanel     correction_panel  = new JPanel(new BorderLayout());
				JSlider    correction_slider  = new JSlider();
				correction_slider.setMinimum(0);
				correction_slider.setMaximum(10);
				correction_slider.setValue(correction);
				JTextField correction_value   = new JTextField(3);
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
				correction_panel.add(correction_value,  BorderLayout.EAST);
				correction_dialog.add(correction_panel);
				settings_menu.add(correction_item);

				// ── Datatype menu ───────────────────────────────────────────
				JMenu datatype_menu = new JMenu("Datatype");
				JRadioButtonMenuItem [] datatype_button = new JRadioButtonMenuItem[3];
				datatype_button[0] = new JRadioButtonMenuItem("Integer");
				datatype_button[1] = new JRadioButtonMenuItem("String");
				datatype_button[2] = new JRadioButtonMenuItem("String*");
				datatype_button[compress_type].setSelected(true);

				class DatatypeButtonHandler implements ActionListener
				{
					int index;
					DatatypeButtonHandler(int index) { this.index = index; }
					public void actionPerformed(ActionEvent e)
					{
						if (compress_type != index)
						{
							datatype_button[compress_type].setSelected(false);
							compress_type = index;
							datatype_button[compress_type].setSelected(true);
							apply_item.doClick();
						}
						else
							datatype_button[compress_type].setSelected(true);
					}
				}

				for (int i = 0; i < 3; i++)
				{
					datatype_button[i].addActionListener(new DatatypeButtonHandler(i));
					datatype_menu.add(datatype_button[i]);
				}

				// ── Delta menu ──────────────────────────────────────────────
				JMenu delta_menu = new JMenu("Delta");
				JRadioButtonMenuItem [] delta_button = new JRadioButtonMenuItem[8];
				delta_button[0] = new JRadioButtonMenuItem("H");
				delta_button[1] = new JRadioButtonMenuItem("V");
				delta_button[2] = new JRadioButtonMenuItem("Average");
				delta_button[3] = new JRadioButtonMenuItem("Paeth");
				delta_button[4] = new JRadioButtonMenuItem("Gradient");
				delta_button[5] = new JRadioButtonMenuItem("Scanline 1");
				delta_button[6] = new JRadioButtonMenuItem("Scanline 2");
				delta_button[7] = new JRadioButtonMenuItem("Map");
				delta_button[delta_type].setSelected(true);

				class DeltaButtonHandler implements ActionListener
				{
					int index;
					DeltaButtonHandler(int index) { this.index = index; }
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
					delta_button[i].addActionListener(new DeltaButtonHandler(i));
					delta_menu.add(delta_button[i]);
				}

				menu_bar.add(file_menu);
				menu_bar.add(view_menu);
				menu_bar.add(datatype_menu);
				menu_bar.add(delta_menu);
				menu_bar.add(settings_menu);
				frame.setJMenuBar(menu_bar);

				// Initial canvas size and frame bounds.
				display_image = original_image;
				image_canvas.setPreferredSize(new Dimension(
						(int)(xdim * zoom_scale),
						(int)(ydim * zoom_scale)));
				updateTitle();

				int max_frame_w = (int)(screen_xdim * 0.70);
				int max_frame_h = (int)(screen_ydim * 0.70);
				frame.setSize(Math.min(xdim + 40, max_frame_w),
				              Math.min(ydim + 80,  max_frame_h));
				frame.setLocation(5, 5);
				frame.setVisible(true);

				// Recompute fit scale against the live viewport once layout is done.
				SwingUtilities.invokeLater(() -> showInitialImage());
			}
		}
		catch (Exception e)
		{
			System.out.println(e.getMessage());
			System.exit(1);
		}
	}

	// -------------------------------------------------------------------------
	// Zoom helpers
	// -------------------------------------------------------------------------
	/** Called once on the EDT after the frame is visible, to lock in the correct fit scale. */
	private void showInitialImage()
	{
		Dimension vp_size = scroll_pane.getViewport().getSize();
		double xs = (vp_size.width  > 0) ? (double) vp_size.width  / xdim : 1.0;
		double ys = (vp_size.height > 0) ? (double) vp_size.height / ydim : 1.0;
		fit_scale  = Math.min(1.0, Math.min(xs, ys));
		zoom_scale = fit_scale;
		updateDisplayImage();
		image_canvas.setPreferredSize(new Dimension(
				(int)(xdim * zoom_scale),
				(int)(ydim * zoom_scale)));
		image_canvas.revalidate();
		image_canvas.repaint();
		updateTitle();
	}

	private void zoomBy(double factor)
	{
		double new_scale = Math.max(ZOOM_MIN, Math.min(ZOOM_MAX, zoom_scale * factor));
		if (new_scale == zoom_scale) return;

		JViewport vp      = scroll_pane.getViewport();
		Point     vp_pos  = vp.getViewPosition();
		Dimension vp_size = vp.getSize();

		double centre_x = vp_pos.x + vp_size.width  / 2.0;
		double centre_y = vp_pos.y + vp_size.height / 2.0;
		double ratio    = new_scale / zoom_scale;
		zoom_scale      = new_scale;

		updateDisplayImage();
		image_canvas.setPreferredSize(new Dimension(
				(int)(xdim * zoom_scale),
				(int)(ydim * zoom_scale)));
		image_canvas.revalidate();
		image_canvas.repaint();

		int new_vx = (int)(centre_x * ratio - vp_size.width  / 2.0);
		int new_vy = (int)(centre_y * ratio - vp_size.height / 2.0);
		vp.setViewPosition(new Point(Math.max(0, new_vx), Math.max(0, new_vy)));

		updateTitle();
	}

	private void updateDisplayImage()
	{
		BufferedImage source = (initialized) ? working_image : original_image;
		if (zoom_scale == 1.0)
		{
			display_image = source;
		}
		else
		{
			int w = Math.max(1, (int)(xdim * zoom_scale));
			int h = Math.max(1, (int)(ydim * zoom_scale));
			AffineTransform t    = new AffineTransform();
			t.scale(zoom_scale, zoom_scale);
			AffineTransformOp op = new AffineTransformOp(t, AffineTransformOp.TYPE_BILINEAR);
			display_image = new BufferedImage(w, h, source.getType());
			display_image = op.filter(source, display_image);
		}
	}

	private void updateTitle()
	{
		int pct = (int) Math.round(zoom_scale * 100);
		frame.setTitle("Huffman Writer " + filename + "  [" + pct + "%]");
	}

	// -------------------------------------------------------------------------
	// ImageCanvas
	// -------------------------------------------------------------------------
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
			if (display_image != null)
				g.drawImage(display_image, 0, 0, this);
		}
	}

	// -------------------------------------------------------------------------
	// ApplyHandler  (logic unchanged from original)
	// -------------------------------------------------------------------------
	class ApplyHandler implements ActionListener
	{
		public void actionPerformed(ActionEvent event)
		{
			ArrayList quantized_channel_list = new ArrayList();

			int new_xdim = xdim;
			int new_ydim = ydim;
			if (pixel_quant != 0)
			{
				double factor = pixel_quant;
				factor       /= 10;
				new_xdim = xdim - (int)(factor * (xdim / 2 - 2));
				new_ydim = ydim - (int)(factor * (ydim / 2 - 2));
			}

			for (int i = 0; i < 3; i++)
			{
				int [] channel = (int []) channel_list.get(i);
				if (pixel_quant == 0)
				{
					if (pixel_shift == 0)
						quantized_channel_list.add(channel);
					else
					{
						int [] shifted_channel = DeltaMapper.shift(channel, -pixel_shift);
						quantized_channel_list.add(shifted_channel);
					}
				}
				else
				{
					int [] resized_channel = ResizeMapper.resize(channel, xdim, new_xdim, new_ydim);
					if (pixel_shift == 0)
						quantized_channel_list.add(resized_channel);
					else
					{
						int [] shifted_channel = DeltaMapper.shift(resized_channel, -pixel_shift);
						quantized_channel_list.add(shifted_channel);
					}
				}
			}

			int [] quantized_blue  = (int []) quantized_channel_list.get(0);
			int [] quantized_green = (int []) quantized_channel_list.get(1);
			int [] quantized_red   = (int []) quantized_channel_list.get(2);

			int [] quantized_blue_green = DeltaMapper.getDifference(quantized_blue, quantized_green);
			int [] quantized_red_green  = DeltaMapper.getDifference(quantized_red,  quantized_green);
			int [] quantized_red_blue   = DeltaMapper.getDifference(quantized_red,  quantized_blue);

			quantized_channel_list.add(quantized_blue_green);
			quantized_channel_list.add(quantized_red_green);
			quantized_channel_list.add(quantized_red_blue);

			for (int i = 0; i < 6; i++)
			{
				int min = 256;
				int [] quantized_channel = (int []) quantized_channel_list.get(i);

				for (int j = 0; j < quantized_channel.length; j++)
					if (quantized_channel[j] < min)
						min = quantized_channel[j];
				channel_min[i] = min;

				if (i > 2)
					for (int j = 0; j < quantized_channel.length; j++)
						quantized_channel[j] -= min;

				channel_init[i] = quantized_channel[0];
				quantized_channel_list.set(i, quantized_channel);

				int [] frequency     = DeltaMapper.getIdealFrequency(quantized_channel, new_xdim, new_ydim);
				double shannon_limit = CodeMapper.getShannonLimit(frequency);
				channel_sum[i]       = (int)Math.floor(shannon_limit);
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
			{
				if (set_sum[i] < min_delta_sum)
				{
					min_delta_sum = set_sum[i];
					min_index = i;
				}
			}
			min_set_id = min_index;

			file_compression_rate  = file_length;
			file_compression_rate /= xdim * ydim * 3;


			int [] channel_id = DeltaMapper.getChannels(min_set_id);

			huffman_table_list.clear();
			string_table_list.clear();
			string_list.clear();
			delta_list.clear();
			map_list.clear();
			code_list.clear();
			code_length_list.clear();

			for (int i = 0; i < 3; i++)
			{
				int j = channel_id[i];
				int [] quantized_channel = (int []) quantized_channel_list.get(j);

				ArrayList result = new ArrayList();

				if      (delta_type == 0) result = DeltaMapper.getHorizontalDeltasFromValues(quantized_channel, new_xdim, new_ydim);
				else if (delta_type == 1) result = DeltaMapper.getVerticalDeltasFromValues(quantized_channel, new_xdim, new_ydim);
				else if (delta_type == 2) result = DeltaMapper.getAverageDeltasFromValues(quantized_channel, new_xdim, new_ydim);
				else if (delta_type == 3) result = DeltaMapper.getPaethDeltasFromValues(quantized_channel, new_xdim, new_ydim);
				else if (delta_type == 4) result = DeltaMapper.getGradientDeltasFromValues(quantized_channel, new_xdim, new_ydim);
				else if (delta_type == 5)
				{
					result = DeltaMapper.getMixedDeltasFromValues(quantized_channel, new_xdim, new_ydim);
					map_list.add((byte []) result.get(2));
				}
				else if (delta_type == 6)
				{
					result = DeltaMapper.getMixedDeltasFromValues2(quantized_channel, new_xdim, new_ydim);
					map_list.add((byte []) result.get(2));
				}
				else if (delta_type == 7)
				{
					result = DeltaMapper.getIdealDeltasFromValues(quantized_channel, new_xdim, new_ydim);
					map_list.add((byte []) result.get(2));
				}

				int [] delta = (int []) result.get(1);

				if (compress_type == 0)
				{
					ArrayList histogram_list = StringMapper.getHistogram(delta);
					int delta_min            = (int) histogram_list.get(0);
					int [] histogram         = (int []) histogram_list.get(1);
					channel_delta_min[j]     = delta_min;

					int n = histogram.length;
					ArrayList frequency_list = new ArrayList();
					for (int k = 0; k < n; k++)
						frequency_list.add(histogram[k]);
					Collections.sort(frequency_list, Comparator.reverseOrder());
					int [] frequency = new int[n];
					for (int k = 0; k < n; k++)
						frequency[k] = (int) frequency_list.get(k);
					byte [] huffman_length = CodeMapper.getHuffmanLength2(frequency);
					int  [] huffman_code   = CodeMapper.getCanonicalCode(huffman_length);
					int  [] rank_table     = StringMapper.getRankTable(histogram);

					code_list.add(huffman_code);
					code_length_list.add(huffman_length);
					huffman_table_list.add(rank_table);

					delta[0] = 0;
					for (int k = 1; k < delta.length; k++)
						delta[k] -= delta_min;

					ArrayList pack_list          = CodeMapper.packCode(delta, rank_table, huffman_code, huffman_length);
					byte []   packed_delta        = (byte []) pack_list.get(0);
					int       bit_length          = (int) pack_list.get(1);
					channel_huffman_length[j]     = bit_length;
					delta_list.add(packed_delta);

					delta[0] = 0;
					for (int k = 1; k < delta.length; k++)
						delta[k] += delta_min;
				}
				else if (compress_type == 1 || compress_type == 2)
				{
					boolean precompress = (compress_type == 2);
					ArrayList delta_string_list = StringMapper.getStringList(delta, precompress);

					channel_delta_min[j]     = (int)   delta_string_list.get(0);
					channel_string_length[j] = (int)   delta_string_list.get(1);
					int  [] string_table     = (int []) delta_string_list.get(2);
					byte [] delta_string     = (byte []) delta_string_list.get(3);

					string_table_list.add(string_table);
					string_list.add(delta_string);

					channel_string_length[j] = StringMapper.getBitlength(delta_string);
					channel_string_type[i]   = StringMapper.getType(delta_string);
					channel_iterations[i]    = StringMapper.getIterations(delta_string);

					int [] delta_string_ints = new int[delta_string.length];
					for (int k = 0; k < delta_string.length; k++)
					{
						delta_string_ints[k] = (int) delta_string[k];
						if (delta_string_ints[k] < 0)
							delta_string_ints[k] += 256;
					}

					ArrayList histogram_list = StringMapper.getHistogram(delta_string_ints);
					int [] histogram         = (int []) histogram_list.get(1);

					int n = histogram.length;
					ArrayList frequency_list = new ArrayList();
					for (int k = 0; k < n; k++)
						frequency_list.add(histogram[k]);
					Collections.sort(frequency_list, Comparator.reverseOrder());
					int [] frequency = new int[n];
					for (int k = 0; k < n; k++)
						frequency[k] = (int) frequency_list.get(k);
					byte [] huffman_length = CodeMapper.getHuffmanLength2(frequency);
					int  [] huffman_code   = CodeMapper.getCanonicalCode(huffman_length);
					int  [] rank_table     = StringMapper.getRankTable(histogram);

					code_list.add(huffman_code);
					code_length_list.add(huffman_length);
					huffman_table_list.add(rank_table);

					ArrayList pack_list      = CodeMapper.packCode(delta_string_ints, rank_table, huffman_code, huffman_length);
					byte []   packed_delta   = (byte []) pack_list.get(0);
					int       bit_length     = (int) pack_list.get(1);
					channel_huffman_length[j] = bit_length;
					delta_list.add(packed_delta);

					delta[0] = 0;
					for (int k = 1; k < delta.length; k++)
						delta[k] += channel_delta_min[j];
				}
			}

			ArrayList resized_channel_list = new ArrayList();

			for (int i = 0; i < 3; i++)
			{
				int j  = channel_id[i];

				int  [] delta        = new int[new_xdim * new_ydim];
				byte [] packed_delta = (byte []) delta_list.get(i);

				if (compress_type == 0)
				{
					int  [] table       = (int [])  huffman_table_list.get(i);
					int  [] code        = (int [])  code_list.get(i);
					byte [] code_length = (byte []) code_length_list.get(i);
					CodeMapper.unpackCode(packed_delta, table, code, code_length, channel_huffman_length[j], delta);
					for (int k = 1; k < delta.length; k++)
						delta[k] += channel_delta_min[j];
				}
				else if (compress_type == 1 || compress_type == 2)
				{
					int bytelength       = StringMapper.getBytelength(channel_string_length[j]);
					int [] delta_string_ints = new int[bytelength];
					byte [] delta_string = new byte[bytelength];
					int  [] table        = (int [])  huffman_table_list.get(i);
					int  [] code         = (int [])  code_list.get(i);
					byte [] code_length  = (byte []) code_length_list.get(i);
					CodeMapper.unpackCode(packed_delta, table, code, code_length, channel_huffman_length[j], delta_string_ints);

					for (int k = 0; k < bytelength; k++)
						delta_string[k] = (byte) delta_string_ints[k];

					table = (int []) string_table_list.get(i);
					int current_iterations = StringMapper.getIterations(delta_string);
					int number_unpacked;
					if (current_iterations == 0 || current_iterations == 16)
					{
						number_unpacked = StringMapper.unpackStrings2(delta_string, table, delta);
						if (number_unpacked != new_xdim * new_ydim)
							System.out.println("Number of values unpacked does not agree with image dimensions.");
					}
					else
					{
						byte[] decompressed_string = StringMapper.decompressStrings2(delta_string);
						number_unpacked = StringMapper.unpackStrings2(decompressed_string, table, delta);
						if (number_unpacked != new_xdim * new_ydim)
							System.out.println("Number of values unpacked does not agree with image dimensions.");
					}

					for (int k = 1; k < delta.length; k++)
						delta[k] += channel_delta_min[j];
				}

				int [] channel = new int[0];
				if      (delta_type == 0) channel = DeltaMapper.getValuesFromHorizontalDeltas(delta, new_xdim, new_ydim, channel_init[j]);
				else if (delta_type == 1) channel = DeltaMapper.getValuesFromVerticalDeltas(delta, new_xdim, new_ydim, channel_init[j]);
				else if (delta_type == 2) channel = DeltaMapper.getValuesFromAverageDeltas(delta, new_xdim, new_ydim, channel_init[j]);
				else if (delta_type == 3) channel = DeltaMapper.getValuesFromPaethDeltas(delta, new_xdim, new_ydim, channel_init[j]);
				else if (delta_type == 4) channel = DeltaMapper.getValuesFromGradientDeltas(delta, new_xdim, new_ydim, channel_init[j]);
				else if (delta_type == 5 || delta_type == 6 || delta_type == 7)
				{
					byte [] map = (byte []) map_list.get(i);
					if      (delta_type == 5) channel = DeltaMapper.getValuesFromMixedDeltas(delta, new_xdim, new_ydim, channel_init[j], map);
					else if (delta_type == 6) channel = DeltaMapper.getValuesFromMixedDeltas2(delta, new_xdim, new_ydim, channel_init[j], map);
					else if (delta_type == 7) channel = DeltaMapper.getValuesFromIdealDeltas(delta, new_xdim, new_ydim, channel_init[j], map);
				}

				if (j > 2)
					for (int k = 0; k < channel.length; k++)
						channel[k] += channel_min[j];

				// Dequantize: shift second, matching the encode order.
				if (pixel_shift == 0)
				{
					if (new_xdim != xdim || new_ydim != ydim)
						resized_channel_list.add(ResizeMapper.resize(channel, new_xdim, xdim, ydim));
					else
						resized_channel_list.add(channel);
				}
				else
				{
					int [] shifted_channel = DeltaMapper.shift(channel, pixel_shift);
					if (new_xdim != xdim || new_ydim != ydim)
						resized_channel_list.add(ResizeMapper.resize(shifted_channel, new_xdim, xdim, ydim));
					else
						resized_channel_list.add(shifted_channel);
				}
			}

			int [] blue  = new int[xdim * ydim];
			int [] green = new int[xdim * ydim];
			int [] red   = new int[xdim * ydim];

			if (min_set_id == 0)
			{
				blue  = (int []) resized_channel_list.get(0);
				green = (int []) resized_channel_list.get(1);
				red   = (int []) resized_channel_list.get(2);
			}
			else if (min_set_id == 1)
			{
				blue  = (int []) resized_channel_list.get(0);
				red   = (int []) resized_channel_list.get(1);
				green = DeltaMapper.getDifference(red, (int []) resized_channel_list.get(2));
			}
			else if (min_set_id == 2)
			{
				blue  = (int []) resized_channel_list.get(0);
				red   = (int []) resized_channel_list.get(1);
				green = DeltaMapper.getDifference(blue, (int []) resized_channel_list.get(2));
			}
			else if (min_set_id == 3)
			{
				blue  = (int []) resized_channel_list.get(0);
				int [] blue_green = (int []) resized_channel_list.get(1);
				green = DeltaMapper.getDifference(blue, blue_green);
				red   = DeltaMapper.getSum((int []) resized_channel_list.get(2), green);
			}
			else if (min_set_id == 4)
			{
				blue  = (int []) resized_channel_list.get(0);
				int [] blue_green = (int []) resized_channel_list.get(1);
				green = DeltaMapper.getDifference(blue, blue_green);
				red   = DeltaMapper.getSum(blue, (int []) resized_channel_list.get(2));
			}
			else if (min_set_id == 5)
			{
				green = (int []) resized_channel_list.get(0);
				red   = (int []) resized_channel_list.get(1);
				blue  = DeltaMapper.getSum((int []) resized_channel_list.get(2), green);
			}
			else if (min_set_id == 6)
			{
				red = (int []) resized_channel_list.get(0);
				int [] blue_green = (int []) resized_channel_list.get(1);
				int [] red_green  = (int []) resized_channel_list.get(2);
				for (int i = 0; i < red_green.length; i++)
					red_green[i] = -red_green[i];
				green = DeltaMapper.getSum(red_green, red);
				blue  = DeltaMapper.getSum(blue_green, green);
			}
			else if (min_set_id == 7)
			{
				green = (int []) resized_channel_list.get(0);
				blue  = DeltaMapper.getSum(green, (int []) resized_channel_list.get(1));
				red   = DeltaMapper.getSum(green, (int []) resized_channel_list.get(2));
			}
			else if (min_set_id == 8)
			{
				green = (int []) resized_channel_list.get(0);
				red   = DeltaMapper.getSum(green, (int []) resized_channel_list.get(1));
				blue  = DeltaMapper.getDifference(red, (int []) resized_channel_list.get(2));
			}
			else if (min_set_id == 9)
			{
				red   = (int []) resized_channel_list.get(0);
				green = DeltaMapper.getDifference(red, (int []) resized_channel_list.get(1));
				blue  = DeltaMapper.getDifference(red, (int []) resized_channel_list.get(2));
			}

			int [] original_blue  = (int []) channel_list.get(0);
			int [] original_green = (int []) channel_list.get(1);
			int [] original_red   = (int []) channel_list.get(2);

			int [][] error = new int[3][xdim * ydim];

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
			initialized = true;
		}
	}

	// -------------------------------------------------------------------------
	// SaveHandler  (logic unchanged from original)
	// -------------------------------------------------------------------------
	class SaveHandler implements ActionListener
	{
		public void actionPerformed(ActionEvent event)
		{
			if (!initialized)
				apply_item.doClick();
			int channel_id[] = DeltaMapper.getChannels(min_set_id);

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
					out.writeInt(channel_huffman_length[j]);

					int [] table = (int []) huffman_table_list.get(i);
					out.writeShort(table.length);

					if (table.length <= Byte.MAX_VALUE * 2 + 1)
					{
						byte [] buffer = new byte[table.length];
						for (int k = 0; k < buffer.length; k++)
							buffer[k] = (byte) table[k];
						out.write(buffer, 0, buffer.length);
					}
					else
					{
						for (int k = 0; k < table.length; k++)
							out.writeShort(table[k]);
					}

					if (delta_type == 5 || delta_type == 6 || delta_type == 7)
					{
						byte [] map        = (byte []) map_list.get(i);
						byte [] packed_map = SegmentMapper.packBits(map, 2);
						out.writeInt(map.length);
						out.writeInt(packed_map.length);
						out.write(packed_map, 0, packed_map.length);
					}

					byte  [] code_length  = (byte []) code_length_list.get(i);
					ArrayList length_list = CodeMapper.packLengthTable(code_length);
					int      n            = (int)    length_list.get(0);
					byte     init_value   = (byte)   length_list.get(1);
					byte     max_delta    = (byte)   length_list.get(2);
					byte []  packed_table_delta = (byte []) length_list.get(3);

					out.writeInt(n);
					out.writeByte(init_value);
					out.writeByte(max_delta);
					out.writeByte(packed_table_delta.length);
					out.write(packed_table_delta, 0, packed_table_delta.length);

					byte [] packed_delta = (byte []) delta_list.get(i);
					out.writeInt(packed_delta.length);
					out.write(packed_delta, 0, packed_delta.length);

					if (compress_type == 1 || compress_type == 2)
					{
						out.writeInt(channel_string_length[j]);

						table = (int []) string_table_list.get(i);
						out.writeShort(table.length);

						byte [] buffer = new byte[table.length];
						for (int k = 0; k < buffer.length; k++)
							buffer[k] = (byte) table[k];
						out.write(buffer, 0, buffer.length);
					}
				}

				out.flush();
				out.close();

				File file = new File("foo");
				long file_length_out    = file.length();
				double compression_rate = (double) file_length_out / (xdim * ydim * 3);
				System.out.println("The file compression rate is " + String.format("%.4f", file_compression_rate));
				System.out.println("Huffman compression rate is " + String.format("%.4f", compression_rate));
				System.out.println();
			}
			catch (Exception e)
			{
				System.out.println(e.toString());
			}
		}
	}
}
