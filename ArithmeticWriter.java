// ============================================================
// ArithmeticWriter.java
// ============================================================
import java.awt.*;
import java.awt.image.*;
import java.io.*;
import java.math.*;
import java.awt.event.*;
import java.awt.geom.AffineTransform;
import java.util.*;
import java.util.zip.*;
import javax.imageio.*;
import javax.swing.*;
import javax.swing.event.*;

public class ArithmeticWriter
{
	BufferedImage original_image, working_image, display_image;
	ImageCanvas   image_canvas;
	JScrollPane   scroll_pane;
	int image_xdim, image_ydim;
	int screen_xdim, screen_ydim;
	JMenuItem apply_item;
	String filename;
	int[] pixel;

	int pixel_quant   = 4;
	int pixel_shift   = 3;
	int correction    = 0;
	int smooth_level  = 0;
	int smooth2_level = 0;
	int min_set_id    = 0;
	int previous_min_set_id = -1;
	int delta_type    = 5;
	int pixel_segment = 0;
	int data_type     = 0;   // 0=String, 1=byte, 2=short, 3=int
	boolean slow_arithmetic = false;
	int channel_order = 0;   // 0=First, 1=Last, 2=Descending, 3=Ascending, 4=None — Slow arithmetic only

	double zoom_scale = 1.0;
	double fit_scale  = 1.0;
	static final double ZOOM_FACTOR = 1.25, ZOOM_MIN = 0.05, ZOOM_MAX = 32.0;
	static int openWindowCount = 0;

	int[] set_sum, channel_sum;
	String[] set_string, delta_type_string, channel_string;
	int[] channel_init, channel_min, channel_delta_min;
	int[] channel_length, channel_compressed_length;
	byte[] channel_iterations;

	long   file_length;
	double file_compression_rate;
	boolean initialized   = false;
	boolean print_ranking = false;

	// Saved statistics for the Statistics dialog
	int[]     saved_delta_cost  = new int[12];
	int[]     saved_map_cost    = new int[12];
	boolean[] saved_delta_comp  = new boolean[12];
	boolean[] saved_map_comp    = new boolean[12];
	Integer[] saved_delta_order = new Integer[12];
	int[]     saved_ss          = new int[10];
	int[]     saved_cs          = new int[6];
	Integer[] saved_set_order   = new Integer[10];

	JRadioButtonMenuItem[] delta_button;
	JSlider quant_slider, shift_slider, corr_slider, smooth1_slider, smooth2_slider;
	JFrame frame = null;

	// encode lists
	ArrayList<Object> channel_list, table_list, string_list, map_list, delta_list;

	public static void main(String[] args)
	{
		if (args.length == 1)
			new ArithmeticWriter(args[0]);
		else
		{
			FileDialog fd = new FileDialog((java.awt.Frame) null, "Open Image", FileDialog.LOAD);
			fd.setVisible(true);
			if (fd.getFile() != null)
				new ArithmeticWriter(new File(fd.getDirectory(), fd.getFile()).getPath());
			else
				System.exit(0);
		}
	}

	public ArithmeticWriter(String _filename)
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
			System.out.println("Loaded " + filename + "  (" + image_xdim + " x " + image_ydim + ")");

			channel_list = new ArrayList<>();
			table_list   = new ArrayList<>();
			string_list  = new ArrayList<>();
			map_list     = new ArrayList<>();
			delta_list   = new ArrayList<>();

			channel_string    = new String[]{"blue", "green", "red", "blue-green", "red-green", "red-blue"};
			set_sum    = new int[10];
			delta_type_string = new String[]{"H", "V", "Average", "Med", "Adaptive", "Directional",
				"Scanline 1", "Scanline 2", "Scanline 3", "Scanline 4", "Map 1", "Map 2"};
			channel_init = new int[6];
			channel_min  = new int[6];
			channel_delta_min = new int[6];
			channel_sum  = new int[6];
			channel_length = new int[6];
			channel_compressed_length = new int[6];
			channel_iterations = new byte[3];

			if (raster_type == BufferedImage.TYPE_3BYTE_BGR)
			{
				pixel = new int[image_xdim * image_ydim];
				PixelGrabber pg = new PixelGrabber(original_image, 0, 0, image_xdim, image_ydim, pixel, 0, image_xdim);
				try
				{
					pg.grabPixels();
				}
				catch (InterruptedException e)
				{
					System.err.println(e);
				}

				int[] blue  = new int[image_xdim * image_ydim];
				int[] green = new int[image_xdim * image_ydim];
				int[] red   = new int[image_xdim * image_ydim];
				for (int i = 0; i < pixel.length; i++)
				{
					blue[i]  = (pixel[i] >> 16) & 0xff;
					green[i] = (pixel[i] >> 8) & 0xff;
					red[i]   = pixel[i] & 0xff;
				}
				channel_list.add(blue);
				channel_list.add(green);
				channel_list.add(red);

				working_image = new BufferedImage(image_xdim, image_ydim, BufferedImage.TYPE_INT_RGB);
				for (int i = 0; i < image_xdim; i++)
				{
					for (int j = 0; j < image_ydim; j++)
					{
						working_image.setRGB(i, j, pixel[j * image_xdim + i]);
					}
				}

				Dimension sc = Toolkit.getDefaultToolkit().getScreenSize();
				screen_xdim = (int) sc.getWidth();
				screen_ydim = (int) sc.getHeight();
				fit_scale = Math.min(1.0, Math.min((double)(screen_xdim * 70 / 100 - 40) / image_xdim, (double)(screen_ydim * 70 / 100 - 80) / image_ydim));
				zoom_scale = fit_scale;

				image_canvas = new ImageCanvas();
				scroll_pane = new JScrollPane(image_canvas, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
				scroll_pane.getVerticalScrollBar().setUnitIncrement(16);
				scroll_pane.getHorizontalScrollBar().setUnitIncrement(16);
				scroll_pane.addMouseWheelListener(e ->
				{
					if (e.isControlDown())
					{
						JViewport vp = scroll_pane.getViewport();
						Point vp_pos = vp.getViewPosition();
						Point mp = e.getPoint();
						int mcx = mp.x + vp_pos.x;
						int mcy = mp.y + vp_pos.y;
						double old = zoom_scale;
						if (e.getWheelRotation() < 0)
							zoom_scale = Math.min(ZOOM_MAX, zoom_scale * ZOOM_FACTOR);
						else
							zoom_scale = Math.max(ZOOM_MIN, zoom_scale / ZOOM_FACTOR);
						if (zoom_scale == old)
							return;
						updateDisplayImage();
						image_canvas.setPreferredSize(new Dimension((int)(image_xdim * zoom_scale), (int)(image_ydim * zoom_scale)));
						image_canvas.revalidate();
						image_canvas.repaint();
						double r = zoom_scale / old;
						vp.setViewPosition(new Point(Math.max(0, (int)(mcx * r) - mp.x), Math.max(0, (int)(mcy * r) - mp.y)));
						updateTitle();
					}
					else
						scroll_pane.dispatchEvent(e);
				});

				openWindowCount++;
				frame = new JFrame("Arithmetic Writer  " + filename);
				frame.addWindowListener(new WindowAdapter()
				{
					public void windowClosing(WindowEvent e)
					{
						frame.dispose();
						if (--openWindowCount == 0)
							System.exit(0);
					}
				});
				frame.getContentPane().add(scroll_pane, BorderLayout.CENTER);

				JMenuBar menu_bar = new JMenuBar();

				// File menu
				JMenu file_menu = new JMenu("File");
				JMenuItem open_item = new JMenuItem("Open...");
				open_item.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_O, InputEvent.CTRL_DOWN_MASK));
				open_item.addActionListener(e ->
				{
					FileDialog fd = new FileDialog(frame, "Open Image", FileDialog.LOAD);
					fd.setVisible(true);
					if (fd.getFile() != null)
						new ArithmeticWriter(fd.getDirectory() + fd.getFile());
				});
				file_menu.add(open_item);
				file_menu.addSeparator();

				apply_item = new JMenuItem("Apply");
				apply_item.addActionListener(new ApplyHandler());

				JMenuItem reset_item = new JMenuItem("Reset");
				reset_item.addActionListener(e ->
				{
					// setValue triggers onChange which sets field + calls Apply
					quant_slider.setValue(0);
					shift_slider.setValue(0);
					corr_slider.setValue(0);
					smooth1_slider.setValue(0);
					smooth2_slider.setValue(0);
				});
				file_menu.add(reset_item);

				JMenuItem save_item = new JMenuItem("Save");
				save_item.addActionListener(new SaveHandler());
				file_menu.add(save_item);

				// View menu
				JMenu view_menu = new JMenu("View");
				JMenuItem zi = new JMenuItem("Zoom In");
				zi.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_EQUALS, InputEvent.CTRL_DOWN_MASK));
				zi.addActionListener(e -> zoomBy(ZOOM_FACTOR));
				view_menu.add(zi);

				JMenuItem zo = new JMenuItem("Zoom Out");
				zo.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_MINUS, InputEvent.CTRL_DOWN_MASK));
				zo.addActionListener(e -> zoomBy(1.0 / ZOOM_FACTOR));
				view_menu.add(zo);

				JMenuItem zf = new JMenuItem("Fit");
				zf.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_0, InputEvent.CTRL_DOWN_MASK));
				zf.addActionListener(e ->
				{
					Dimension v = scroll_pane.getViewport().getSize();
					zoom_scale = Math.min((double) v.width / image_xdim, (double) v.height / image_ydim);
					updateDisplayImage();
					image_canvas.setPreferredSize(new Dimension((int)(image_xdim * zoom_scale), (int)(image_ydim * zoom_scale)));
					image_canvas.revalidate();
					image_canvas.repaint();
					updateTitle();
				});
				view_menu.add(zf);

				JMenuItem za = new JMenuItem("100%");
				za.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_1, InputEvent.CTRL_DOWN_MASK));
				za.addActionListener(e ->
				{
					zoom_scale = 1.0;
					updateDisplayImage();
					image_canvas.setPreferredSize(new Dimension(image_xdim, image_ydim));
					image_canvas.revalidate();
					image_canvas.repaint();
					updateTitle();
				});
				view_menu.add(za);

				// Quantization menu
				JMenu quant_menu = new JMenu("Quantization");
				// Smooth dialog with bilateral + anisotropic sliders
				{
					JDialog smooth_dlg = new JDialog(frame, "Smooth");
					smooth1_slider = new JSlider(0, 10, smooth_level);
					smooth2_slider = new JSlider(0, 10, smooth2_level);
					smooth1_slider.setMajorTickSpacing(1);
					smooth1_slider.setPaintTicks(true);
					smooth1_slider.setSnapToTicks(true);
					smooth2_slider.setMajorTickSpacing(1);
					smooth2_slider.setPaintTicks(true);
					smooth2_slider.setSnapToTicks(true);

					JTextField tf1 = new JTextField(" 0 ", 3);
					JTextField tf2 = new JTextField(" 0 ", 3);
					smooth1_slider.addChangeListener(e ->
					{
						tf1.setText(" " + smooth1_slider.getValue() + " ");
						if (!smooth1_slider.getValueIsAdjusting())
						{
							smooth_level = smooth1_slider.getValue();
							print_ranking = true;
							apply_item.doClick();
						}
					});
					smooth2_slider.addChangeListener(e ->
					{
						tf2.setText(" " + smooth2_slider.getValue() + " ");
						if (!smooth2_slider.getValueIsAdjusting())
						{
							smooth2_level = smooth2_slider.getValue();
							print_ranking = true;
							apply_item.doClick();
						}
					});

					JPanel p1 = new JPanel(new BorderLayout(4, 4));
					p1.add(new javax.swing.JLabel(" Bilateral:     "), BorderLayout.WEST);
					p1.add(smooth1_slider, BorderLayout.CENTER);
					p1.add(tf1, BorderLayout.EAST);

					JPanel p2 = new JPanel(new BorderLayout(4, 4));
					p2.add(new javax.swing.JLabel(" Anisotropic:"), BorderLayout.WEST);
					p2.add(smooth2_slider, BorderLayout.CENTER);
					p2.add(tf2, BorderLayout.EAST);

					JPanel sp = new JPanel(new java.awt.GridLayout(2, 1, 4, 4));
					sp.add(p1);
					sp.add(p2);
					smooth_dlg.add(sp);

					JMenuItem smooth_mi = new JMenuItem("Smooth");
					smooth_mi.addActionListener(e ->
					{
						Point loc = frame.getLocation();
						smooth_dlg.setLocation((int) loc.getX(), (int) loc.getY() - 80);
						smooth_dlg.pack();
						smooth_dlg.setVisible(true);
					});
					quant_menu.add(smooth_mi);
				}

				quant_slider = new JSlider(0, 10, pixel_quant);
				shift_slider = new JSlider(0, 7, pixel_shift);
				corr_slider  = new JSlider(0, 10, correction);
				quant_menu.add(makeSliderWithRef("Pixel Resolution", quant_slider, v ->
				{
					pixel_quant = v;
					print_ranking = true;
					apply_item.doClick();
				}));
				quant_menu.add(makeSliderWithRef("Color Resolution", shift_slider, v ->
				{
					pixel_shift = v;
					print_ranking = true;
					apply_item.doClick();
				}));
				quant_menu.add(makeSliderWithRef("Error Correction", corr_slider, v ->
				{
					correction = v;
					print_ranking = true;
					apply_item.doClick();
				}));

				// Delta menu
				JMenu delta_menu = new JMenu("Delta");
				String[] dnames = {"H", "V", "Average", "Med", "Adaptive", "Directional", "Scanline 1", "Scanline 2", "Scanline 3", "Scanline 4", "Map 1", "Map 2"};
				delta_button = new JRadioButtonMenuItem[12];
				ButtonGroup dg = new ButtonGroup();
				for (int i = 0; i < 12; i++)
				{
					delta_button[i] = new JRadioButtonMenuItem(dnames[i]);
					dg.add(delta_button[i]);
					delta_menu.add(delta_button[i]);
					final int new_delta_type = i;
					delta_button[i].addActionListener(e ->
					{
						if (delta_type != new_delta_type)
						{
							delta_type = new_delta_type;
							print_ranking = true;
							apply_item.doClick();
						}
					});
				}
				delta_button[delta_type].setSelected(true);

				// Data menu
				JMenu data_menu = new JMenu("Data");
				// Type dialog: String vs Integer
				JDialog type_dlg = new JDialog(frame, "Type");
				JRadioButton str_r = new JRadioButton("String",  true);
				JRadioButton int_r = new JRadioButton("Integer", false);
				ButtonGroup type_grp = new ButtonGroup();
				type_grp.add(str_r);
				type_grp.add(int_r);
				str_r.addActionListener(e ->
				{
					if (data_type != 0)
					{
						data_type = 0;
						apply_item.doClick();
					}
				});
				int_r.addActionListener(e ->
				{
					if (data_type == 0)
					{
						data_type = 1;
						apply_item.doClick();
					}
				});
				JPanel type_pnl = new JPanel();
				type_pnl.add(str_r);
				type_pnl.add(int_r);
				type_dlg.add(type_pnl);

				JMenuItem type_mi = new JMenuItem("Type");
				type_mi.addActionListener(e ->
				{
					str_r.setSelected(data_type == 0);
					int_r.setSelected(data_type != 0);
					Point loc = frame.getLocation();
					type_dlg.setLocation((int) loc.getX(), (int) loc.getY() - 80);
					type_dlg.pack();
					type_dlg.setVisible(true);
				});
				data_menu.add(type_mi);
				data_menu.add(makeSliderWithRef("Threshold", new JSlider(0, 10, 1), v ->
				{
					StringMapper.compress_threshold = v / 10.0;
					print_ranking = true;
					apply_item.doClick();
				}));

				// Arithmetic menu
				JMenu arithmetic_menu = new JMenu("Arithmetic");

				// Type dialog: Fast / Slow
				JDialog arithmetic_type_dlg = new JDialog(frame, "Type");
				JRadioButton fast_r = new JRadioButton("Fast", true);
				JRadioButton slow_r = new JRadioButton("Slow", false);
				ButtonGroup arithmetic_grp = new ButtonGroup();
				arithmetic_grp.add(fast_r);
				arithmetic_grp.add(slow_r);
				fast_r.addActionListener(e -> slow_arithmetic = false);
				slow_r.addActionListener(e -> slow_arithmetic = true);

				JPanel arithmetic_type_pnl = new JPanel();
				arithmetic_type_pnl.add(fast_r);
				arithmetic_type_pnl.add(slow_r);
				arithmetic_type_dlg.add(arithmetic_type_pnl);

				JMenuItem arithmetic_type_mi = new JMenuItem("Type");
				arithmetic_type_mi.addActionListener(e ->
				{
					fast_r.setSelected(!slow_arithmetic);
					slow_r.setSelected(slow_arithmetic);
					Point loc = frame.getLocation();
					arithmetic_type_dlg.setLocation((int) loc.getX(), (int) loc.getY() - 80);
					arithmetic_type_dlg.pack();
					arithmetic_type_dlg.setVisible(true);
				});
				arithmetic_menu.add(arithmetic_type_mi);

				// Granularity slider
				arithmetic_menu.add(makeSliderWithRef("Granularity", new JSlider(0, 10, pixel_segment), v -> pixel_segment = v));

				// Order submenu (Slow arithmetic only — controls how each segment's
				// symbol alphabet is reordered before arithmetic coding; None is the
				// zero-overhead baseline: no order table written, old Fenwick path used.
				// See getOrderTable and SaveHandler's Slow branch.
				JMenu order_menu = new JMenu("Order");
				JRadioButtonMenuItem first_r = new JRadioButtonMenuItem("First", true);
				JRadioButtonMenuItem last_r  = new JRadioButtonMenuItem("Last", false);
				JRadioButtonMenuItem desc_r  = new JRadioButtonMenuItem("Descending", false);
				JRadioButtonMenuItem asc_r   = new JRadioButtonMenuItem("Ascending", false);
				JRadioButtonMenuItem none_r  = new JRadioButtonMenuItem("None", false);
				ButtonGroup order_grp = new ButtonGroup();
				order_grp.add(first_r);
				order_grp.add(last_r);
				order_grp.add(desc_r);
				order_grp.add(asc_r);
				order_grp.add(none_r);
				first_r.addActionListener(e -> channel_order = 0);
				last_r.addActionListener(e -> channel_order = 1);
				desc_r.addActionListener(e -> channel_order = 2);
				asc_r.addActionListener(e -> channel_order = 3);
				none_r.addActionListener(e -> channel_order = 4);
				order_menu.add(first_r);
				order_menu.add(last_r);
				order_menu.add(desc_r);
				order_menu.add(asc_r);
				order_menu.add(none_r);
				arithmetic_menu.add(order_menu);

				// Statistics menu
				JMenu stats_menu = new JMenu("Statistics");
				JMenuItem stats_mi = new JMenuItem("Show...");
				stats_mi.addActionListener(e -> showStatistics());
				stats_menu.add(stats_mi);

				menu_bar.add(file_menu);
				menu_bar.add(view_menu);
				menu_bar.add(quant_menu);
				menu_bar.add(delta_menu);
				menu_bar.add(data_menu);
				menu_bar.add(arithmetic_menu);
				menu_bar.add(stats_menu);
				frame.setJMenuBar(menu_bar);

				display_image = original_image;
				image_canvas.setPreferredSize(new Dimension((int)(image_xdim * zoom_scale), (int)(image_ydim * zoom_scale)));
				updateTitle();
				int display_w = (int)(image_xdim * zoom_scale);
				int display_h = (int)(image_ydim * zoom_scale);
				frame.pack();
				int ow = frame.getWidth() - scroll_pane.getViewport().getWidth();
				int oh = frame.getHeight() - scroll_pane.getViewport().getHeight();
				int dialog_clearance = 120;
				frame.setSize(display_w + ow, Math.min(display_h + oh, screen_ydim - dialog_clearance));
				frame.setLocation(5, dialog_clearance);
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

	private JMenuItem makeSlider(String title, int lo, int hi, int init, java.util.function.IntConsumer onChange)
	{
		JSlider slider = new JSlider(lo, hi, init);
		return makeSliderWithRef(title, slider, onChange);
	}

	private JMenuItem makeSliderWithRef(String title, JSlider slider, java.util.function.IntConsumer onChange)
	{
		JMenuItem item = new JMenuItem(title);
		JDialog dialog = new JDialog(frame, title);
		slider.setMajorTickSpacing(1);
		slider.setPaintTicks(true);
		slider.setSnapToTicks(true);

		JTextField field = new JTextField(3);
		field.setText(" " + slider.getValue() + " ");
		slider.addChangeListener(e ->
		{
			int v = slider.getValue();
			field.setText(" " + v + " ");
			if (!slider.getValueIsAdjusting())
				onChange.accept(v);
		});

		JPanel p = new JPanel(new BorderLayout());
		p.add(slider, BorderLayout.CENTER);
		p.add(field, BorderLayout.EAST);
		dialog.add(p);

		item.addActionListener(e ->
		{
			Point loc = frame.getLocation();
			dialog.setLocation((int) loc.getX(), (int) loc.getY() - 60);
			dialog.pack();
			dialog.setVisible(true);
		});
		return item;
	}

	private void showInitialImage()
	{
		Dimension v = scroll_pane.getViewport().getSize();
		double vw = (v.width > 0) ? (double) v.width / image_xdim : 1.0;
		double vh = (v.height > 0) ? (double) v.height / image_ydim : 1.0;
		fit_scale = Math.min(1.0, Math.min(vw, vh));
		zoom_scale = fit_scale;
		updateDisplayImage();
		image_canvas.setPreferredSize(new Dimension((int)(image_xdim * zoom_scale), (int)(image_ydim * zoom_scale)));
		image_canvas.revalidate();
		image_canvas.repaint();
		updateTitle();
		apply_item.doClick();

		new javax.swing.SwingWorker<Void, Void>()
		{
			@Override
			protected Void doInBackground()
			{
				init();
				return null;
			}

			@Override
			protected void done()
			{
				for (int k = 0; k < 12; k++)
				{
					if (delta_button[k] != null)
						delta_button[k].setSelected(k == delta_type);
				}
				apply_item.doClick();
			}
		}.execute();
	}

	// Prints the ranked channel-set table (rank, channel names, per-channel
	// entropy estimate, total). Shared by init() and by ApplyHandler when the
	// optimal set changes.
	private void printColorSetRanking(int[] ss, int[] cs, Integer[] so)
	{
		String[][] set_disp = {
			{"blue",  "green",         "red"},
			{"blue",  "red",           "red   -  green"},
			{"blue",  "red",           "blue  -  green"},
			{"blue",  "blue  -  green", "red   -  green"},
			{"blue",  "blue  -  green", "red   -  blue"},
			{"green", "red",           "blue  -  green"},
			{"red",   "blue  -  green", "red   -  green"},
			{"green", "blue  -  green", "red   -  green"},
			{"green", "red   -  green", "red   -  blue"},
			{"red",   "red   -  green", "red   -  blue"}
		};
		for (int r = 0; r < 10; r++)
		{
			int idx = so[r];
			int[] c = DeltaMapper.getChannels(idx);
			System.out.println(String.format("  %2d. %-8s%-20s%-14s %10d %10d %10d %12d",
				r + 1, set_disp[idx][0], set_disp[idx][1], set_disp[idx][2], cs[c[0]], cs[c[1]], cs[c[2]], ss[idx]));
		}
		System.out.println();
	}

	private void init()
	{
		ArrayList<int[]> channel_set_list = new ArrayList<>();
		int nx = image_xdim;
		int ny = image_ydim;
		if (pixel_quant != 0)
		{
			double f = pixel_quant / 10.0;
			nx = image_xdim - (int)(f * (image_xdim / 2 - 2));
			ny = image_ydim - (int)(f * (image_ydim / 2 - 2));
		}

		for (int i = 0; i < 3; i++)
		{
			int[] ch = (int[]) channel_list.get(i);
			if (pixel_quant == 0)
			{
				if (pixel_shift == 0)
					channel_set_list.add(ch);
				else
					channel_set_list.add(DeltaMapper.shift(ch, -pixel_shift));
			}
			else
			{
				int[] r = ResizeMapper.resize(ch, image_xdim, nx, ny);
				if (pixel_shift == 0)
					channel_set_list.add(r);
				else
					channel_set_list.add(DeltaMapper.shift(r, -pixel_shift));
			}
		}
		channel_set_list.add(DeltaMapper.getDifference(channel_set_list.get(0), channel_set_list.get(1)));
		channel_set_list.add(DeltaMapper.getDifference(channel_set_list.get(2), channel_set_list.get(1)));
		channel_set_list.add(DeltaMapper.getDifference(channel_set_list.get(2), channel_set_list.get(0)));

		int[] cs = new int[6];
		for (int i = 0; i < 6; i++)
		{
			int[] qc = channel_set_list.get(i);
			int min = 256;
			for (int v : qc)
			{
				if (v < min)
					min = v;
			}
			if (i > 2)
				for (int k = 0; k < qc.length; k++)
				{
					qc[k] -= min;
				}
			channel_set_list.set(i, qc);
			cs[i] = (int) Math.floor(CodeMapper.getShannonLimit(DeltaMapper.getIdealFrequency(qc, nx, ny)));
		}

		int[] ss = new int[10];
		ss[0] = cs[0] + cs[1] + cs[2];
		ss[1] = cs[0] + cs[4] + cs[2];
		ss[2] = cs[0] + cs[3] + cs[2];
		ss[3] = cs[0] + cs[1] + cs[4];
		ss[4] = cs[0] + cs[3] + cs[5];
		ss[5] = cs[3] + cs[1] + cs[2];
		ss[6] = cs[3] + cs[4] + cs[2];
		ss[7] = cs[3] + cs[1] + cs[4];
		ss[8] = cs[5] + cs[1] + cs[4];
		ss[9] = cs[5] + cs[4] + cs[2];

		Integer[] so = new Integer[10];
		for (int i = 0; i < 10; i++)
		{
			so[i] = i;
		}
		java.util.Arrays.sort(so, (a, b) -> ss[a] - ss[b]);
		saved_ss = ss.clone();
		saved_cs = cs.clone();
		saved_set_order = so.clone();

		int best_set = so[0];
		int[] cid = DeltaMapper.getChannels(best_set);

		print_ranking = true;
		System.out.println("Channel sets (ranked):");
		printColorSetRanking(ss, cs, so);

		System.out.println("Starting data collection...");
		collectData(channel_set_list, cid, nx, ny, true);
	}

	private void collectData(ArrayList<int[]> channel_set_list, int[] cid, int nx, int ny, boolean set_delta_type)
	{
		if (!print_ranking && !set_delta_type)
			return;
		print_ranking = false;

		String[] delta_names = {"H", "V", "Average", "Med", "Adaptive", "Directional", "Scanline 1", "Scanline 2", "Scanline 3", "Scanline 4", "Map 1", "Map 2"};
		int[] delta_cost  = new int[12];
		int[] map_cost    = new int[12];
		boolean[] delta_comp = new boolean[12];
		boolean[] map_comp   = new boolean[12];

		Thread[] threads = new Thread[12];
		for (int t = 0; t < 12; t++)
		{
			final int ft = t;
			threads[t] = new Thread(() ->
			{
				int cost = 0;
				int mcost = 0;
				boolean comp = false;
				boolean mcomp = false;
				for (int ci = 0; ci < 3; ci++)
				{
					int[] qc = channel_set_list.get(cid[ci]);
					ArrayList result;
					if (ft == 0)
						result = DeltaMapper.getHorizontalDeltasFromValues(qc, nx, ny);
					else if (ft == 1)
						result = DeltaMapper.getVerticalDeltasFromValues(qc, nx, ny);
					else if (ft == 2)
						result = DeltaMapper.getAverageDeltasFromValues(qc, nx, ny);
					else if (ft == 3)
						result = DeltaMapper.getMedDeltasFromValues(qc, nx, ny);
					else if (ft == 4)
						result = DeltaMapper.getAdaptiveDeltasFromValues(qc, nx, ny);
					else if (ft == 5)
						result = DeltaMapper.getDirectionalDeltasFromValues(qc, nx, ny);
					else if (ft == 6)
						result = DeltaMapper.getMixedDeltasFromValues(qc, nx, ny);
					else if (ft == 7)
						result = DeltaMapper.getMixedDeltasFromValues2(qc, nx, ny);
					else if (ft == 8)
						result = DeltaMapper.getMixedDeltasFromValues4(qc, nx, ny);
					else if (ft == 9)
						result = DeltaMapper.getMixedDeltasFromValues16Rows(qc, nx, ny);
					else if (ft == 10)
						result = DeltaMapper.getIdealDeltasFromValues8(qc, nx, ny);
					else
						result = DeltaMapper.getIdealDeltasFromValues16(qc, nx, ny);

					byte[] dstr = (byte[]) StringMapper.getStringList((int[]) result.get(1), true).get(3);
					cost += StringMapper.getBitlength(dstr);
					if ((StringMapper.getIterations(dstr) & 15) > 0)
						comp = true;

					if (ft >= 6)
					{
						byte[] map = (byte[]) result.get(2);
						int[] mi = new int[map.length];
						for (int k = 0; k < map.length; k++)
						{
							mi[k] = map[k] & 0xFF;
						}
						byte[] mstr = (byte[]) StringMapper.getStringList(mi, true).get(3);
						mcost += StringMapper.getBitlength(mstr);
						if ((StringMapper.getIterations(mstr) & 15) > 0)
							mcomp = true;
					}
				}
				delta_cost[ft] = cost;
				map_cost[ft]   = mcost;
				delta_comp[ft] = comp;
				map_comp[ft]   = mcomp;
			});
			threads[t].start();
		}
		try
		{
			for (Thread t : threads)
			{
				t.join();
			}
		}
		catch (InterruptedException e)
		{
			Thread.currentThread().interrupt();
		}

		int[] total = new int[12];
		for (int t = 0; t < 12; t++)
		{
			total[t] = delta_cost[t] + map_cost[t];
		}
		Integer[] delta_order = new Integer[12];
		for (int i = 0; i < 12; i++)
		{
			delta_order[i] = i;
		}
		java.util.Arrays.sort(delta_order, (a, b) -> total[a] - total[b]);
		if (set_delta_type)
			delta_type = delta_order[0];
		saved_delta_cost  = delta_cost.clone();
		saved_map_cost    = map_cost.clone();
		saved_delta_comp  = delta_comp.clone();
		saved_map_comp    = map_comp.clone();
		saved_delta_order = delta_order.clone();

		System.out.println("Delta types (ranked):");
		for (int r = 0; r < 12; r++)
		{
			int idx = delta_order[r];
			String sel = (idx == delta_type) ? " **" : "";
			String dc = delta_comp[idx] ? "*" : " ";
			String mc = map_comp[idx] ? "*" : " ";
			if (map_cost[idx] > 0 || idx >= 6)
				System.out.println(String.format("  %2d. %-12s delta: %12d%s      map: %12d%s      total: %12d%s",
					r + 1, delta_names[idx], delta_cost[idx], dc, map_cost[idx], mc, total[idx], sel));
			else
				System.out.println(String.format("  %2d. %-12s delta: %12d%s                              total: %12d%s",
					r + 1, delta_names[idx], delta_cost[idx], dc, total[idx], sel));
		}
		System.out.println();
	}

	private void zoomBy(double factor)
	{
		double ns = Math.max(ZOOM_MIN, Math.min(ZOOM_MAX, zoom_scale * factor));
		if (ns == zoom_scale)
			return;
		JViewport vp = scroll_pane.getViewport();
		Point vp_pos = vp.getViewPosition();
		Dimension vs = vp.getSize();
		double cx = vp_pos.x + vs.width / 2.0;
		double cy = vp_pos.y + vs.height / 2.0;
		double r  = ns / zoom_scale;
		zoom_scale = ns;

		updateDisplayImage();
		image_canvas.setPreferredSize(new Dimension((int)(image_xdim * zoom_scale), (int)(image_ydim * zoom_scale)));
		image_canvas.revalidate();
		image_canvas.repaint();
		vp.setViewPosition(new Point(Math.max(0, (int)(cx * r - vs.width / 2.0)), Math.max(0, (int)(cy * r - vs.height / 2.0))));
		updateTitle();
	}

	private void updateDisplayImage()
	{
		BufferedImage src = (working_image != null && initialized) ? working_image : original_image;
		if (zoom_scale == 1.0)
		{
			display_image = src;
			return;
		}
		int w = Math.max(1, (int)(image_xdim * zoom_scale));
		int h = Math.max(1, (int)(image_ydim * zoom_scale));
		AffineTransform t = new AffineTransform();
		t.scale(zoom_scale, zoom_scale);
		display_image = new AffineTransformOp(t, AffineTransformOp.TYPE_BILINEAR).filter(src, new BufferedImage(w, h, src.getType()));
	}

	private void updateTitle()
	{
		frame.setTitle("Arithmetic Writer  " + filename + "  [" + (int) Math.round(zoom_scale * 100) + "%]");
	}

	private static void writeTable(DataOutputStream out, int[] table) throws IOException
	{
		out.writeShort(table.length);
		int max = Byte.MAX_VALUE * 2 + 1;
		if (table.length <= max)
		{
			for (int v : table)
			{
				out.writeByte(v);
			}
		}
		else
		{
			for (int v : table)
			{
				out.writeShort(v);
			}
		}
	}

	// Computes a symbol-to-rank order table (index = original byte value,
	// value = new position in the reordered frequency table) for one
	// segment, according to the currently selected Order submenu option.
	// getDescendingTable already returns symbol->rank; the other three
	// (getFirstTable/getLastTable/getAscendingTable) return rank->symbol,
	// so those are inverted here to match the shape getIntervalValue/
	// getArithmeticValues expect. Only called when channel_order != 4
	// (None) — SaveHandler skips this entirely for the no-overhead baseline.
	private byte[] getOrderTable(byte[] seg, int[] freq)
	{
		byte[] rank_to_symbol;
		if (channel_order == 0)
			rank_to_symbol = ArithmeticMapper.getFirstTable(seg, freq);
		else if (channel_order == 1)
			rank_to_symbol = ArithmeticMapper.getLastTable(seg, freq);
		else if (channel_order == 2)
			return ArithmeticMapper.getDescendingTable(freq);
		else
			rank_to_symbol = ArithmeticMapper.getAscendingTable(freq);

		byte[] symbol_to_rank = new byte[rank_to_symbol.length];
		for (int rank = 0; rank < rank_to_symbol.length; rank++)
		{
			int symbol = rank_to_symbol[rank] & 0xFF;
			symbol_to_rank[symbol] = (byte) rank;
		}
		return symbol_to_rank;
	}

	// Compute deltas for a given channel and delta type
	private ArrayList<Object> computeDeltas(int[] qc, int nx, int ny, int delta_type)
	{
		if (delta_type == 0)
			return DeltaMapper.getHorizontalDeltasFromValues(qc, nx, ny);
		if (delta_type == 1)
			return DeltaMapper.getVerticalDeltasFromValues(qc, nx, ny);
		if (delta_type == 2)
			return DeltaMapper.getAverageDeltasFromValues(qc, nx, ny);
		if (delta_type == 3)
			return DeltaMapper.getMedDeltasFromValues(qc, nx, ny);
		if (delta_type == 4)
			return DeltaMapper.getAdaptiveDeltasFromValues(qc, nx, ny);
		if (delta_type == 5)
			return DeltaMapper.getDirectionalDeltasFromValues(qc, nx, ny);
		if (delta_type == 6)
			return DeltaMapper.getMixedDeltasFromValues(qc, nx, ny);
		if (delta_type == 7)
			return DeltaMapper.getMixedDeltasFromValues2(qc, nx, ny);
		if (delta_type == 8)
			return DeltaMapper.getMixedDeltasFromValues4(qc, nx, ny);
		if (delta_type == 9)
			return DeltaMapper.getMixedDeltasFromValues16Rows(qc, nx, ny);
		if (delta_type == 10)
			return DeltaMapper.getIdealDeltasFromValues8(qc, nx, ny);
		return DeltaMapper.getIdealDeltasFromValues16(qc, nx, ny);
	}

	// Reconstruct channel values from deltas
	private int[] reconstructChannel(int[] delta, int nx, int ny, int init, int delta_type, int idx)
	{
		if (delta_type == 0)
			return DeltaMapper.getValuesFromHorizontalDeltas(delta, nx, ny, init);
		if (delta_type == 1)
			return DeltaMapper.getValuesFromVerticalDeltas(delta, nx, ny, init);
		if (delta_type == 2)
			return DeltaMapper.getValuesFromAverageDeltas(delta, nx, ny, init);
		if (delta_type == 3)
			return DeltaMapper.getValuesFromMedDeltas(delta, nx, ny, init);
		if (delta_type == 4)
			return DeltaMapper.getValuesFromAdaptiveDeltas(delta, nx, ny, init);
		if (delta_type == 5)
			return DeltaMapper.getValuesFromDirectionalDeltas(delta, nx, ny, init);
		if (delta_type == 6)
			return DeltaMapper.getValuesFromMixedDeltas(delta, nx, ny, init, (byte[]) map_list.get(idx));
		if (delta_type == 7)
			return DeltaMapper.getValuesFromMixedDeltas2(delta, nx, ny, init, (byte[]) map_list.get(idx));
		if (delta_type == 8)
			return DeltaMapper.getValuesFromMixedDeltas4(delta, nx, ny, init, (byte[]) map_list.get(idx));
		if (delta_type == 9)
			return DeltaMapper.getValuesFromMixedDeltas16Rows(delta, nx, ny, init, (byte[]) map_list.get(idx));
		if (delta_type == 10)
			return DeltaMapper.getValuesFromIdealDeltas8(delta, nx, ny, init, (byte[]) map_list.get(idx));
		return DeltaMapper.getValuesFromIdealDeltas16(delta, nx, ny, init, (byte[]) map_list.get(idx));
	}

	private void showStatistics()
	{
		if (saved_set_order == null || saved_set_order[0] == null || saved_delta_order == null || saved_delta_order[0] == null)
		{
			JOptionPane.showMessageDialog(frame, "Data collection still in progress, please wait.");
			return;
		}

		JDialog dlg = new JDialog(frame, "Statistics");
		dlg.setLayout(new java.awt.GridLayout(2, 1, 8, 8));

		// Channel sets table
		String[][] set_disp = {
			{"blue",  "green",         "red"},
			{"blue",  "red",           "red - green"},
			{"blue",  "red",           "blue - green"},
			{"blue",  "blue - green",  "red - green"},
			{"blue",  "blue - green",  "red - blue"},
			{"green", "red",           "blue - green"},
			{"red",   "blue - green",  "red - green"},
			{"green", "blue - green",  "red - green"},
			{"green", "red - green",   "red - blue"},
			{"red",   "red - green",   "red - blue"}
		};
		String[] set_cols = {"Rank", "Ch 1", "Ch 2", "Ch 3", "Entropy 1", "Entropy 2", "Entropy 3", "Total"};
		Object[][] set_data = new Object[10][8];
		for (int r = 0; r < 10; r++)
		{
			int idx = saved_set_order[r];
			int[] c = DeltaMapper.getChannels(idx);
			set_data[r][0] = r + 1;
			set_data[r][1] = set_disp[idx][0];
			set_data[r][2] = set_disp[idx][1];
			set_data[r][3] = set_disp[idx][2];
			set_data[r][4] = saved_cs[c[0]];
			set_data[r][5] = saved_cs[c[1]];
			set_data[r][6] = saved_cs[c[2]];
			set_data[r][7] = saved_ss[idx];
		}
		javax.swing.JTable set_table = new javax.swing.JTable(set_data, set_cols);
		set_table.setEnabled(false);
		dlg.add(new JScrollPane(set_table));

		// Delta types table
		String[] delta_names = {"H", "V", "Average", "Med", "Adaptive", "Directional", "Scanline 1", "Scanline 2", "Scanline 3", "Scanline 4", "Map 1", "Map 2"};
		String[] delta_cols = {"Rank", "Type", "Delta", "*", "Map", "*", "Total", "**"};
		Object[][] delta_data = new Object[12][8];
		for (int r = 0; r < 12; r++)
		{
			int idx = saved_delta_order[r];
			int total = saved_delta_cost[idx] + saved_map_cost[idx];
			delta_data[r][0] = r + 1;
			delta_data[r][1] = delta_names[idx];
			delta_data[r][2] = saved_delta_cost[idx];
			delta_data[r][3] = saved_delta_comp[idx] ? "*" : " ";
			delta_data[r][4] = (saved_map_cost[idx] > 0) ? saved_map_cost[idx] : null;
			delta_data[r][5] = (saved_map_cost[idx] > 0 && saved_map_comp[idx]) ? "*" : " ";
			delta_data[r][6] = total;
			delta_data[r][7] = (idx == delta_type) ? "**" : " ";
		}
		javax.swing.JTable delta_table = new javax.swing.JTable(delta_data, delta_cols);
		delta_table.setEnabled(false);
		dlg.add(new JScrollPane(delta_table));

		dlg.setSize(700, 500);
		Point loc = frame.getLocation();
		dlg.setLocation((int) loc.getX() + 20, (int) loc.getY() + 20);
		dlg.setVisible(true);
	}

	class ImageCanvas extends JPanel
	{
		public ImageCanvas()
		{
			setOpaque(true);
		}

		@Override
		public Dimension getPreferredSize()
		{
			if (display_image != null)
				return new Dimension(display_image.getWidth(), display_image.getHeight());
			return new Dimension(Math.max(1, (int)(image_xdim * zoom_scale)), Math.max(1, (int)(image_ydim * zoom_scale)));
		}

		@Override
		protected synchronized void paintComponent(Graphics g)
		{
			super.paintComponent(g);
			if (display_image != null)
				g.drawImage(display_image, 0, 0, this);
		}
	}

	class ApplyHandler implements ActionListener
	{
		public void actionPerformed(ActionEvent event)
		{
			try
			{
				applyImpl();
			}
			catch (Exception e)
			{
				System.out.println("ApplyHandler: " + e);
				e.printStackTrace();
			}
		}

		private void applyImpl()
		{
			ArrayList<int[]> channel_set_list = new ArrayList<>();
			ArrayList<int[]> decoded_channel_list = new ArrayList<>();
			int new_xdim = image_xdim;
			int new_ydim = image_ydim;
			if (pixel_quant != 0)
			{
				double f = pixel_quant / 10.0;
				new_xdim = image_xdim - (int)(f * (image_xdim / 2 - 2));
				new_ydim = image_ydim - (int)(f * (image_ydim / 2 - 2));
			}

			// ---- Smoothing/quant/shift pass (3 threads, one per channel) --------
			int[][] pre_qc = new int[3][];
			final int fnew_xdim = new_xdim;
			final int fnew_ydim = new_ydim;
			Thread[] pre_threads = new Thread[3];
			for (int i = 0; i < 3; i++)
			{
				final int fi = i;
				pre_threads[i] = new Thread(() ->
				{
					int[] ch = (int[]) channel_list.get(fi);
					if (smooth_level > 0)
						ch = DeltaMapper.bilateralSmooth(ch, image_xdim, image_ydim, smooth_level);
					if (smooth2_level > 0)
						ch = DeltaMapper.anisotropicSmooth(ch, image_xdim, image_ydim, smooth2_level);
					if (pixel_quant == 0)
					{
						if (pixel_shift == 0)
							pre_qc[fi] = ch;
						else
							pre_qc[fi] = DeltaMapper.shift(ch, -pixel_shift);
					}
					else
					{
						int[] r = ResizeMapper.resize(ch, image_xdim, fnew_xdim, fnew_ydim);
						if (pixel_shift == 0)
							pre_qc[fi] = r;
						else
							pre_qc[fi] = DeltaMapper.shift(r, -pixel_shift);
					}
				});
				pre_threads[i].start();
			}
			try
			{
				for (Thread t : pre_threads)
				{
					t.join();
				}
			}
			catch (InterruptedException e)
			{
				Thread.currentThread().interrupt();
			}
			for (int i = 0; i < 3; i++)
			{
				channel_set_list.add(pre_qc[i]);
			}

			channel_set_list.add(DeltaMapper.getDifference(channel_set_list.get(0), channel_set_list.get(1)));
			channel_set_list.add(DeltaMapper.getDifference(channel_set_list.get(2), channel_set_list.get(1)));
			channel_set_list.add(DeltaMapper.getDifference(channel_set_list.get(2), channel_set_list.get(0)));

			for (int i = 0; i < 6; i++)
			{
				int[] qc = channel_set_list.get(i);
				int min = 256;
				for (int v : qc)
				{
					if (v < min)
						min = v;
				}
				channel_min[i] = min;
				if (i > 2)
					for (int k = 0; k < qc.length; k++)
					{
						qc[k] -= min;
					}
				channel_init[i] = qc[0];
				channel_set_list.set(i, qc);
				channel_sum[i] = (int) Math.floor(CodeMapper.getShannonLimit(DeltaMapper.getIdealFrequency(qc, new_xdim, new_ydim)));
			}

			int[] ss = new int[10];
			ss[0] = channel_sum[0] + channel_sum[1] + channel_sum[2];
			ss[1] = channel_sum[0] + channel_sum[4] + channel_sum[2];
			ss[2] = channel_sum[0] + channel_sum[3] + channel_sum[2];
			ss[3] = channel_sum[0] + channel_sum[1] + channel_sum[4];
			ss[4] = channel_sum[0] + channel_sum[3] + channel_sum[5];
			ss[5] = channel_sum[3] + channel_sum[1] + channel_sum[2];
			ss[6] = channel_sum[3] + channel_sum[4] + channel_sum[2];
			ss[7] = channel_sum[3] + channel_sum[1] + channel_sum[4];
			ss[8] = channel_sum[5] + channel_sum[1] + channel_sum[4];
			ss[9] = channel_sum[5] + channel_sum[4] + channel_sum[2];

			int min_sum = Integer.MAX_VALUE;
			int min_idx = 0;
			for (int i = 0; i < 10; i++)
			{
				if (ss[i] < min_sum)
				{
					min_sum = ss[i];
					min_idx = i;
				}
			}
			min_set_id = min_idx;

			if (previous_min_set_id != -1 && previous_min_set_id != min_set_id)
			{
				Integer[] so = new Integer[10];
				for (int i = 0; i < 10; i++)
				{
					so[i] = i;
				}
				java.util.Arrays.sort(so, (a, b) -> ss[a] - ss[b]);
				System.out.println("Optimal color set changed:");
				printColorSetRanking(ss, channel_sum, so);
			}
			previous_min_set_id = min_set_id;

			file_compression_rate = (double) file_length / (image_xdim * image_ydim * 3);
			int[] channel_id = DeltaMapper.getChannels(min_set_id);
			table_list.clear();
			string_list.clear();
			map_list.clear();
			delta_list.clear();

			// ---- Encode pass (3 threads, one per channel) ----------------------
			int global_dmax = 0;
			int[][] raw_deltas = new int[3][];
			int[][]  enc_tables  = new int[3][];
			byte[][] enc_strings = new byte[3][];
			byte[][] enc_maps    = new byte[3][];
			int[]    local_dmax  = new int[3];

			final int fnx = new_xdim;
			final int fny = new_ydim;
			Thread[] enc_threads = new Thread[3];
			for (int i = 0; i < 3; i++)
			{
				final int fi = i;
				enc_threads[i] = new Thread(() ->
				{
					int fj = channel_id[fi];
					int[] qc = channel_set_list.get(fj);
					ArrayList<Object> result = computeDeltas(qc, fnx, fny, delta_type);
					if (delta_type >= 6)
						enc_maps[fi] = (byte[]) result.get(2);
					int[] delta = (int[]) result.get(1);
					if (data_type == 0)
					{
						ArrayList dsl = StringMapper.getStringList(delta, true);
						channel_delta_min[fj] = (int) dsl.get(0);
						channel_length[fj] = (int) dsl.get(1);
						enc_tables[fi]  = (int[]) dsl.get(2);
						enc_strings[fi] = (byte[]) dsl.get(3);
						channel_compressed_length[fj] = StringMapper.getBitlength(enc_strings[fi]);
						channel_iterations[fi] = StringMapper.getIterations(enc_strings[fi]);
						for (int k = 1; k < delta.length; k++)
						{
							delta[k] += channel_delta_min[fj];
						}
					}
					else
					{
						int dmin = delta[1];
						for (int k = 2; k < delta.length; k++)
						{
							if (delta[k] < dmin)
								dmin = delta[k];
						}
						channel_delta_min[fj] = dmin;
						int dmax = 0;
						for (int k = 1; k < delta.length; k++)
						{
							int v = delta[k] - dmin;
							if (v > dmax)
								dmax = v;
						}
						local_dmax[fi] = dmax;
						channel_length[fj] = delta.length;
						channel_iterations[fi] = 0;
						raw_deltas[fi] = delta;
						enc_tables[fi] = new int[0];
					}
				});
				enc_threads[i].start();
			}
			try
			{
				for (Thread t : enc_threads)
				{
					t.join();
				}
			}
			catch (InterruptedException e)
			{
				Thread.currentThread().interrupt();
			}

			for (int i = 0; i < 3; i++)
			{
				if (delta_type >= 6)
					map_list.add(enc_maps[i]);
				table_list.add(enc_tables[i]);
				if (data_type == 0)
					string_list.add(enc_strings[i]);
			}

			if (data_type != 0)
			{
				for (int i = 0; i < 3; i++)
				{
					if (local_dmax[i] > global_dmax)
						global_dmax = local_dmax[i];
				}
				if (global_dmax <= 255)
					data_type = 1;
				else if (global_dmax <= 65535)
					data_type = 2;
				else
					data_type = 3;
				int width = (data_type == 1) ? 1 : (data_type == 2) ? 2 : 4;
				for (int i = 0; i < 3; i++)
				{
					int j = channel_id[i];
					int[] delta = raw_deltas[i];
					int dmin = channel_delta_min[j];
					channel_compressed_length[j] = width;
					byte[] db = new byte[delta.length * width];
					db[0] = 0;
					for (int k = 1; k < delta.length; k++)
					{
						int v = delta[k] - dmin;
						int base = k * width;
						for (int b = 0; b < width; b++)
						{
							db[base + b] = (byte)(v >> (8 * b));
						}
					}
					delta_list.add(db);
				}
			}

			// ---- Preview decode pass (3 threads, one per channel) -------------
			int[][] pre_dqc = new int[3][];
			final int fnxd = new_xdim;
			final int fnyd = new_ydim;
			Thread[] dec_threads = new Thread[3];
			for (int i = 0; i < 3; i++)
			{
				final int fi = i;
				dec_threads[i] = new Thread(() ->
				{
					int j = channel_id[fi];
					int[] delta;
					if (data_type == 0)
					{
						int[] tbl = (int[]) table_list.get(fi);
						byte[] str = StringMapper.decompressStrings((byte[]) string_list.get(fi));
						delta = StringMapper.unpackStrings(str, tbl, fnxd * fnyd, channel_length[j]);
						for (int k = 1; k < delta.length; k++)
						{
							delta[k] += channel_delta_min[j];
						}
					}
					else
					{
						int width = channel_compressed_length[j];
						byte[] db = (byte[]) delta_list.get(fi);
						delta = new int[fnxd * fnyd];
						delta[0] = 0;
						for (int k = 1; k < delta.length; k++)
						{
							int base = k * width;
							int v = 0;
							for (int b = 0; b < width; b++)
							{
								v |= (db[base + b] & 0xFF) << (8 * b);
							}
							delta[k] = v + channel_delta_min[j];
						}
					}
					int[] ch = reconstructChannel(delta, fnxd, fnyd, channel_init[j], delta_type, fi);
					if (j > 2)
						for (int k = 0; k < ch.length; k++)
						{
							ch[k] += channel_min[j];
						}
					if (pixel_shift == 0)
					{
						if (pixel_quant == 0)
							pre_dqc[fi] = ch;
						else
							pre_dqc[fi] = ResizeMapper.resize(ch, fnxd, image_xdim, image_ydim);
					}
					else
					{
						int[] s = DeltaMapper.shift(ch, pixel_shift);
						if (pixel_quant == 0)
							pre_dqc[fi] = s;
						else
							pre_dqc[fi] = ResizeMapper.resize(s, fnxd, image_xdim, image_ydim);
					}
				});
				dec_threads[i].start();
			}
			try
			{
				for (Thread t : dec_threads)
				{
					t.join();
				}
			}
			catch (InterruptedException e)
			{
				Thread.currentThread().interrupt();
			}
			for (int i = 0; i < 3; i++)
			{
				decoded_channel_list.add(pre_dqc[i]);
			}

			// ---- RGB assembly -----------------------------------------------
			int[] blue  = new int[image_xdim * image_ydim];
			int[] green = new int[image_xdim * image_ydim];
			int[] red   = new int[image_xdim * image_ydim];
			if (min_set_id == 0)
			{
				blue = decoded_channel_list.get(0);
				green = decoded_channel_list.get(1);
				red = decoded_channel_list.get(2);
			}
			else if (min_set_id == 1)
			{
				blue = decoded_channel_list.get(0);
				red = decoded_channel_list.get(1);
				green = DeltaMapper.getDifference(red, decoded_channel_list.get(2));
			}
			else if (min_set_id == 2)
			{
				blue = decoded_channel_list.get(0);
				red = decoded_channel_list.get(1);
				green = DeltaMapper.getDifference(blue, decoded_channel_list.get(2));
			}
			else if (min_set_id == 3)
			{
				blue = decoded_channel_list.get(0);
				green = DeltaMapper.getDifference(blue, decoded_channel_list.get(1));
				red = DeltaMapper.getSum(decoded_channel_list.get(2), green);
			}
			else if (min_set_id == 4)
			{
				blue = decoded_channel_list.get(0);
				green = DeltaMapper.getDifference(blue, decoded_channel_list.get(1));
				red = DeltaMapper.getSum(blue, decoded_channel_list.get(2));
			}
			else if (min_set_id == 5)
			{
				green = decoded_channel_list.get(0);
				red = decoded_channel_list.get(1);
				blue = DeltaMapper.getSum(decoded_channel_list.get(2), green);
			}
			else if (min_set_id == 6)
			{
				red = decoded_channel_list.get(0);
				int[] bg = decoded_channel_list.get(1);
				int[] rg = decoded_channel_list.get(2);
				for (int i = 0; i < rg.length; i++)
				{
					rg[i] = -rg[i];
				}
				green = DeltaMapper.getSum(rg, red);
				blue = DeltaMapper.getSum(bg, green);
			}
			else if (min_set_id == 7)
			{
				green = decoded_channel_list.get(0);
				blue = DeltaMapper.getSum(green, decoded_channel_list.get(1));
				red = DeltaMapper.getSum(green, decoded_channel_list.get(2));
			}
			else if (min_set_id == 8)
			{
				green = decoded_channel_list.get(0);
				red = DeltaMapper.getSum(green, decoded_channel_list.get(1));
				blue = DeltaMapper.getDifference(red, decoded_channel_list.get(2));
			}
			else
			{
				red = decoded_channel_list.get(0);
				green = DeltaMapper.getDifference(red, decoded_channel_list.get(1));
				blue = DeltaMapper.getDifference(red, decoded_channel_list.get(2));
			}

			int[] ob  = (int[]) channel_list.get(0);
			int[] og  = (int[]) channel_list.get(1);
			int[] or_ = (int[]) channel_list.get(2);
			int k = 0;
			for (int i = 0; i < image_ydim; i++)
			{
				for (int j = 0; j < image_xdim; j++)
				{
					if (correction != 0)
					{
						double f = correction / 10.0;
						blue[k]  += (int)((ob[k] - blue[k]) * f);
						green[k] += (int)((og[k] - green[k]) * f);
						red[k]   += (int)((or_[k] - red[k]) * f);
					}
					working_image.setRGB(j, i, (blue[k] << 16) + (green[k] << 8) + red[k]);
					k++;
				}
			}

			updateDisplayImage();
			image_canvas.repaint();
			initialized = true;

			final ArrayList<int[]> fchannel_set_list = channel_set_list;
			final int[] fcid = channel_id;
			final int cdnx = new_xdim;
			final int cdny = new_ydim;
			if (print_ranking)
				System.out.println("Starting to collect data...");
			new Thread(() -> ArithmeticWriter.this.collectData(fchannel_set_list, fcid, cdnx, cdny, false)).start();
		}
	}

	class SaveHandler implements ActionListener
	{
		public void actionPerformed(ActionEvent event)
		{
			if (!initialized)
				apply_item.doClick();
			int[] channel_id = DeltaMapper.getChannels(min_set_id);
			try
			{
				DataOutputStream out = new DataOutputStream(new FileOutputStream(new File("foo")));
				out.writeShort(image_xdim);
				out.writeShort(image_ydim);
				out.writeByte(pixel_shift);
				out.writeByte(pixel_quant);
				out.writeByte(min_set_id);
				out.writeByte(delta_type);
				out.writeByte(slow_arithmetic ? 1 : 0);
				out.writeByte(data_type);

				// ---- Per-channel encode pass (3 threads, one per channel) ----------
				byte[][]    channel_blocks       = new byte[3][];
				int[]       stat_entropy_bits    = new int[3];
				int[]       stat_map_bits        = new int[3];
				int[]       stat_compressed_bits = new int[3];
				Exception[] thread_error         = new Exception[3];

				Thread[] save_threads = new Thread[3];
				for (int i = 0; i < 3; i++)
				{
					final int fi = i;
					final int fj = channel_id[i];
					save_threads[i] = new Thread(() ->
					{
						try
						{
							java.io.ByteArrayOutputStream chan_baos = new java.io.ByteArrayOutputStream();
							DataOutputStream cout = new DataOutputStream(chan_baos);

							cout.writeInt(channel_min[fj]);
							cout.writeInt(channel_init[fj]);
							cout.writeInt(channel_delta_min[fj]);
							cout.writeInt(channel_length[fj]);
							cout.writeInt(channel_compressed_length[fj]);
							cout.writeByte(channel_iterations[fi]);

							int map_bits = 0;
							// Map (for scanline/map delta types)
							if (delta_type >= 6)
							{
								byte[] map = (byte[]) map_list.get(fi);
								int[] map_int = new int[map.length];
								for (int k = 0; k < map.length; k++)
								{
									map_int[k] = map[k] & 0xFF;
								}
								int map0 = map_int[0];
								ArrayList dsl = StringMapper.getStringList(map_int, true);
								int dmin = (int) dsl.get(0);
								int[] tbl = (int[]) dsl.get(2);
								byte[] mstr = (byte[]) dsl.get(3);
								int bl = StringMapper.getBitlength(mstr);
								map_bits = bl;
								cout.writeInt(map0 - dmin);
								cout.writeInt(map.length);
								writeTable(cout, tbl);
								cout.writeInt(dmin);
								cout.writeInt(bl);
								cout.write(mstr, 0, StringMapper.getBytelength(bl));
							}

							// Table: always present (empty for Integer)
							writeTable(cout, (int[]) table_list.get(fi));

							byte[] payload = (data_type == 0) ? (byte[]) string_list.get(fi) : (byte[]) delta_list.get(fi);

							// Wrap to count bytes written for this channel's entropy payload
							java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
							DataOutputStream dout = new DataOutputStream(baos);

							// Arithmetic — same for String and Integer (bytes are bytes)
							int min_seg = 500 + pixel_segment * 500;
							int n_segs  = (pixel_segment >= 10) ? 1 : Math.max(1, payload.length / min_seg);
							int seg_len = payload.length / n_segs;
							int odd_len = seg_len + payload.length % n_segs;
							byte[][] segs = new byte[n_segs][];
							int[][]  freqs = new int[n_segs][256];
							for (int m = 0; m < n_segs; m++)
							{
								segs[m] = new byte[(m < n_segs - 1) ? seg_len : odd_len];
							}
							int pos = 0;
							for (int m = 0; m < n_segs; m++)
							{
								for (int nn = 0; nn < segs[m].length; nn++)
								{
									segs[m][nn] = payload[pos];
									freqs[m][payload[pos] & 0xFF]++;
									pos++;
								}
							}
							int fmax = 0;
							for (int[] row : freqs)
							{
								for (int v : row)
								{
									if (v > fmax)
										fmax = v;
								}
							}
							int lt  = (fmax < 256) ? 0 : (fmax < 65536) ? 1 : 2;
							int bpe = (lt == 0) ? 1 : (lt == 1) ? 2 : 4;
							byte[] fb = new byte[n_segs * 256 * bpe];
							for (int m = 0; m < n_segs; m++)
							{
								for (int k = 0; k < 256; k++)
								{
									int v = freqs[m][k];
									int base = m * 256 * bpe + k * bpe;
									for (int b = 0; b < bpe; b++)
									{
										fb[base + b] = (byte)(v >> (8 * b));
									}
								}
							}
							Deflater def = new Deflater(Deflater.BEST_COMPRESSION);
							byte[] zf = new byte[fb.length + 64];
							def.setInput(fb);
							def.finish();
							int zl = def.deflate(zf);
							def.end();
							dout.writeInt(payload.length);
							dout.writeInt(n_segs);
							dout.writeInt(lt);
							dout.writeInt(zl);
							dout.write(zf, 0, zl);

							int n_procs = Math.max(1, Math.min(n_segs, Runtime.getRuntime().availableProcessors()));
							long arith_start = System.nanoTime();
							if (slow_arithmetic)
							{
								// Slow arithmetic. When channel_order == 4 (None), skip order
								// tables entirely and fall back to the original Fenwick path —
								// this is the zero-overhead baseline the other four options are
								// measured against. Otherwise, compute a per-segment order table
								// and use the order-aware (non-Fenwick) interval search, since
								// reordering the alphabet changes the interval's offset, which
								// only the linear-scan getIntervalValue currently supports.
								boolean use_order = (channel_order != 4);
								BigInteger[][] slow_results = new BigInteger[n_segs][2];
								byte[][]       order_tables = use_order ? new byte[n_segs][] : null;
								Thread[] at = new Thread[n_procs];
								for (int p = 0; p < n_procs; p++)
								{
									final int fp = p;
									at[p] = new Thread(() ->
									{
										for (int m = fp; m < n_segs; m += n_procs)
										{
											if (use_order)
											{
												order_tables[m] = getOrderTable(segs[m], freqs[m]);
												slow_results[m] = ArithmeticMapper.getIntervalValue(segs[m], freqs[m], order_tables[m]);
											}
											else
											{
												slow_results[m] = ArithmeticMapper.getIntervalValueFenwick(segs[m], freqs[m]);
											}
										}
									});
									at[p].start();
								}
								try
								{
									for (Thread t : at)
									{
										t.join();
									}
								}
								catch (InterruptedException e)
								{
									Thread.currentThread().interrupt();
								}
								long arith_ms = (System.nanoTime() - arith_start) / 1_000_000;
								System.out.println(String.format("  Slow arithmetic encode (order=%s): %d segs, %d threads, %d ms",
									use_order ? "yes" : "none", n_segs, n_procs, arith_ms));

								dout.writeByte(use_order ? 1 : 0);
								for (int m = 0; m < n_segs; m++)
								{
									if (use_order)
										dout.write(order_tables[m], 0, order_tables[m].length);
									byte[] b0 = slow_results[m][0].toByteArray();
									dout.writeInt(b0.length);
									dout.write(b0, 0, b0.length);
									byte[] b1 = slow_results[m][1].toByteArray();
									dout.writeInt(b1.length);
									dout.write(b1, 0, b1.length);
								}
							}
							else
							{
								// Fast arithmetic (Fenwick) — order-invariant, so Order menu is ignored
								byte[][] fast_results = new byte[n_segs][];
								Thread[] at = new Thread[n_procs];
								for (int p = 0; p < n_procs; p++)
								{
									final int fp = p;
									at[p] = new Thread(() ->
									{
										for (int m = fp; m < n_segs; m += n_procs)
										{
											fast_results[m] = ArithmeticMapper.getIntervalValueFastFenwick(segs[m], freqs[m]);
										}
									});
									at[p].start();
								}
								try
								{
									for (Thread t : at)
									{
										t.join();
									}
								}
								catch (InterruptedException e)
								{
									Thread.currentThread().interrupt();
								}
								long arith_ms = (System.nanoTime() - arith_start) / 1_000_000;
								System.out.println(String.format("  Fast arithmetic encode: %d segs, %d threads, %d ms", n_segs, n_procs, arith_ms));
								for (int m = 0; m < n_segs; m++)
								{
									dout.writeInt(fast_results[m].length);
									dout.write(fast_results[m], 0, fast_results[m].length);
								}
							}

							dout.flush();
							byte[] chan_bytes = baos.toByteArray();
							cout.write(chan_bytes);

							// Compute per-channel stats
							int entropy_bits;
							if (data_type == 0)
								entropy_bits = channel_length[fj];
							else
							{
								int[] freq = new int[256];
								for (int k = 0; k < payload.length; k++)
								{
									freq[payload[k] & 0xFF]++;
								}
								entropy_bits = (int) Math.floor(CodeMapper.getShannonLimit(freq));
							}
							int compressed_bits = chan_bytes.length * 8;

							stat_entropy_bits[fi]    = entropy_bits;
							stat_map_bits[fi]        = map_bits;
							stat_compressed_bits[fi] = compressed_bits;

							cout.flush();
							channel_blocks[fi] = chan_baos.toByteArray();
						}
						catch (Exception e)
						{
							thread_error[fi] = e;
						}
					});
					save_threads[i].start();
				}
				try
				{
					for (Thread t : save_threads)
					{
						t.join();
					}
				}
				catch (InterruptedException e)
				{
					Thread.currentThread().interrupt();
				}

				for (int i = 0; i < 3; i++)
				{
					if (thread_error[i] != null)
						throw thread_error[i];
				}

				// ---- Sequential write + stats print (preserves original ordering) --
				for (int i = 0; i < 3; i++)
				{
					int j = channel_id[i];
					out.write(channel_blocks[i]);

					String ch_name = channel_string[j];
					ch_name = Character.toUpperCase(ch_name.charAt(0)) + ch_name.substring(1);
					if (stat_map_bits[i] > 0)
						System.out.println(String.format("%-12s delta: %10d   map: %8d   actual: %10d",
							ch_name, stat_entropy_bits[i], stat_map_bits[i], stat_compressed_bits[i]));
					else
						System.out.println(String.format("%-12s delta: %10d                  actual: %10d",
							ch_name, stat_entropy_bits[i], stat_compressed_bits[i]));
				}

				out.flush();
				out.close();
				double rate = (double) new File("foo").length() / (image_xdim * image_ydim * 3);
				System.out.println("Original rate: " + String.format("%.4f", file_compression_rate));
				System.out.println("Output rate:   " + String.format("%.4f", rate));
				System.out.println();
			}
			catch (Exception e)
			{
				System.out.println("Save error: " + e);
				e.printStackTrace();
			}
		}
	}
}