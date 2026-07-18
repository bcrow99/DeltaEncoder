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

public class DeltaWriter
{
	// ---- Image state --------------------------------------------------------
	BufferedImage original_image;
	BufferedImage working_image;
	BufferedImage display_image;
	ImageCanvas   image_canvas;
	JScrollPane   scroll_pane;
	JFrame        frame = null;
	String        filename;
	int[]         pixel;
	int           image_xdim, image_ydim;
	int           screen_xdim, screen_ydim;

	// ---- Compression parameters ---------------------------------------------
	int pixel_quant   = 4;
	int pixel_shift   = 3;
	int pixel_segment = 0;   // controls arithmetic segment granularity
	int correction    = 0;
	int min_set_id    = 0;
	int delta_type    = 5;
	int compress_type = 1;   // 0=Integer, 1=String, 2=String*
	int entropy_type      = 0;   // 0=LZ77, 1=Huffman, 2=Slow Arithmetic, 3=Arithmetic
	int smooth_level      = 0;   // bilateral pre-smoothing strength 0=off, 1-10
	int smooth2_level     = 0;   // anisotropic diffusion strength 0=off, 1-10

	// Slider references so showInitialImage() can reset them
	JSlider smooth_slider, smooth2_slider, pquant_slider, pshift_slider, corr_slider;

	// ---- Zoom ---------------------------------------------------------------
	double zoom_scale = 1.0;
	double fit_scale  = 1.0;
	static final double ZOOM_FACTOR = 1.25;
	static final double ZOOM_MIN    = 0.05;
	static final double ZOOM_MAX    = 32.0;

	// ---- Channel stats -------------------------------------------------------
	int[]  set_sum, channel_sum;
	int[][] set_channel_sum = new int[10][3];
	int[]  rank_delta_sum     = new int[13];
	int[]  rank_delta_cost    = new int[13];
	int[]  rank_map_cost      = new int[13];
	boolean[] rank_delta_comp = new boolean[13];
	boolean[] rank_map_comp   = new boolean[13];
	Integer[] rank_set_order  = new Integer[10];
	Integer[] rank_dt_order   = new Integer[13];
	boolean   skip_ranking    = false;
	javax.swing.table.DefaultTableModel set_table_model, delta_table_model;
	JTable set_jtable, delta_jtable;
	JDialog stats_set_dialog, stats_delta_dialog;
	int[]  channel_init, channel_min, channel_delta_min;
	int[]  channel_length, channel_compressed_length;
	byte[] channel_iterations;

	// ---- Labels / menu widgets ----------------------------------------------
	String[] set_string, delta_type_string, channel_string;
	JRadioButtonMenuItem[] delta_button;
	JRadioButtonMenuItem[] compress_button;
	JRadioButtonMenuItem[] entropy_button;

	// ---- Working data lists ------------------------------------------------
	ArrayList<Object> channel_list, table_list, string_list, map_list, delta_list;

	long   file_length;
	double file_compression_rate;
	boolean initialized = false;

	static int openWindowCount  = 0;
	static int nextWindowOffset = 0;

	// =========================================================================
	public static void main(String[] args)
	{
		if (args.length == 1)
		{
			new DeltaWriter(args[0]);
		}
		else
		{
			FileDialog fd = new FileDialog((java.awt.Frame) null, "Open Image", FileDialog.LOAD);
			fd.setVisible(true);
			if (fd.getFile() != null)
				new DeltaWriter(new File(fd.getDirectory(), fd.getFile()).getPath());
			else
				System.exit(0);
		}
	}

	// =========================================================================
	// init() — auto-selects best channel set and best delta type.
	// =========================================================================
	public void init()
	{
		System.out.println("Loaded file: " + filename);
		System.out.println("Image xdim = " + image_xdim + ", ydim = " + image_ydim);
		System.out.println();

		ArrayList<int[]> quantized_channel_list = new ArrayList<int[]>();

		int new_xdim = image_xdim;
		int new_ydim = image_ydim;
		if (pixel_quant != 0)
		{
			double factor = pixel_quant / 10.0;
			new_xdim = image_xdim - (int)(factor * (image_xdim / 2 - 2));
			new_ydim = image_ydim - (int)(factor * (image_ydim / 2 - 2));
		}

		for (int i = 0; i < 3; i++)
		{
			int[] channel = (int[]) channel_list.get(i);
			if (pixel_quant == 0)
			{
				quantized_channel_list.add(pixel_shift == 0 ? channel : DeltaMapper.shift(channel, -pixel_shift));
			}
			else
			{
				int[] resized = ResizeMapper.resize(channel, image_xdim, new_xdim, new_ydim);
				quantized_channel_list.add(pixel_shift == 0 ? resized : DeltaMapper.shift(resized, -pixel_shift));
			}
		}

		int[] qb  = quantized_channel_list.get(0);
		int[] qg  = quantized_channel_list.get(1);
		int[] qr  = quantized_channel_list.get(2);
		quantized_channel_list.add(DeltaMapper.getDifference(qb, qg));
		quantized_channel_list.add(DeltaMapper.getDifference(qr, qg));
		quantized_channel_list.add(DeltaMapper.getDifference(qr, qb));

		for (int i = 0; i < 6; i++)
		{
			int[] qc  = quantized_channel_list.get(i);
			int   min = 256;
			for (int v : qc) if (v < min) min = v;
			channel_min[i] = min;
			if (i > 2) for (int k = 0; k < qc.length; k++) qc[k] -= min;
			channel_init[i] = qc[0];
			quantized_channel_list.set(i, qc);
			channel_sum[i] = (int) Math.floor(CodeMapper.getShannonLimit(
					DeltaMapper.getIdealFrequency(qc, new_xdim, new_ydim)));
		}

		computeSetSums();

		int min_sum = Integer.MAX_VALUE, min_idx = 0;
		for (int i = 0; i < 10; i++) if (set_sum[i] < min_sum) { min_sum = set_sum[i]; min_idx = i; }
		min_set_id = min_idx;

		int[] channel_id = DeltaMapper.getChannels(min_set_id);

		// Compute ranking results (sets delta_type); printing happens via done()'s ApplyHandler
		skip_ranking = true;
		computeRanking(quantized_channel_list, channel_id, new_xdim, new_ydim, true);
		skip_ranking = false;



		// Select compress_type by trying String and String* on all three channels
		// and comparing total bit lengths.  Using only one channel is unreliable
		// because compression behaviour varies between primary and difference channels.
		{
			int str_bits_total = 0, str_star_bits_total = 0;
			for (int i = 0; i < 3; i++)
			{
				int[] qc_test = quantized_channel_list.get(channel_id[i]);
				ArrayList tr;
				if      (delta_type == 0) tr = DeltaMapper.getHorizontalDeltasFromValues(qc_test, new_xdim, new_ydim);
				else if (delta_type == 1) tr = DeltaMapper.getVerticalDeltasFromValues(qc_test, new_xdim, new_ydim);
				else if (delta_type == 2) tr = DeltaMapper.getAverageDeltasFromValues(qc_test, new_xdim, new_ydim);
				else if (delta_type == 3) tr = DeltaMapper.getMedDeltasFromValues(qc_test, new_xdim, new_ydim);
				else if (delta_type == 4) tr = DeltaMapper.getDirectionalDeltasFromValues(qc_test, new_xdim, new_ydim);
				else if (delta_type == 5) tr = DeltaMapper.getAdaptiveDeltasFromValues(qc_test, new_xdim, new_ydim);
				else if (delta_type == 6) tr = DeltaMapper.getMixedDeltasFromValues(qc_test, new_xdim, new_ydim);
				else if (delta_type == 7) tr = DeltaMapper.getMixedDeltasFromValues2(qc_test, new_xdim, new_ydim);
				else if (delta_type == 8) tr = DeltaMapper.getMixedDeltasFromValues4(qc_test, new_xdim, new_ydim);
				else if (delta_type == 9) tr = DeltaMapper.getMixedDeltasFromValues16Rows(qc_test, new_xdim, new_ydim);
				else if (delta_type == 10) tr = DeltaMapper.getGradientDeltasFromValues(qc_test, new_xdim, new_ydim);
				else if (delta_type == 11) tr = DeltaMapper.getIdealDeltasFromValues8(qc_test, new_xdim, new_ydim);
				else                       tr = DeltaMapper.getIdealDeltasFromValues16(qc_test, new_xdim, new_ydim);

				int[] td = (int[]) tr.get(1);
				str_bits_total      += StringMapper.getBitlength((byte[]) StringMapper.getStringList(td.clone(), false).get(3));
				str_star_bits_total += StringMapper.getBitlength((byte[]) StringMapper.getStringList(td.clone(), true ).get(3));
			}
			compress_type = (str_star_bits_total < str_bits_total) ? 2 : 1;
			// Only keep String* if delta string compression genuinely helped for this delta type
			if (compress_type == 2 && !rank_delta_comp[delta_type]) compress_type = 1;
			// Integer compress type stores deltas as bytes; max possible delta = 2 × channel
			// value range, which must fit in 0..255.  Check the actual selected channels.
			if (compress_type == 0)
			{
				int[] cid = DeltaMapper.getChannels(min_set_id);
				for (int i = 0; i < 3; i++)
				{
					int[] qc = quantized_channel_list.get(cid[i]);
					int cmin = qc[0], cmax = qc[0];
					for (int v : qc) { if (v < cmin) cmin = v; if (v > cmax) cmax = v; }
					if ((cmax - cmin) * 2 > 255) { compress_type = 1; break; }
				}
			}
			System.out.println("Best compress type is " + new String[]{"Integer","String","String*"}[compress_type]
			                 + "  (String " + str_bits_total + " bits  String* " + str_star_bits_total + " bits)");
		}
	}

	// =========================================================================
	// Constructor — loads image, builds UI, calls init() via showInitialImage().
	// =========================================================================
	public DeltaWriter(String _filename)
	{
		filename = _filename;
		try
		{
			File          file         = new File(filename);
			file_length                = file.length();
			original_image             = ImageIO.read(file);
			int raster_type            = original_image.getType();
			image_xdim                 = original_image.getWidth();
			image_ydim                 = original_image.getHeight();

			channel_list = new ArrayList<Object>();
			table_list   = new ArrayList<Object>();
			string_list  = new ArrayList<Object>();
			map_list     = new ArrayList<Object>();
			delta_list   = new ArrayList<Object>();

			channel_string    = new String[]{"blue","green","red","blue  -  green","red   -  green","red   -  blue"};

			set_sum    = new int[10];
			set_string = new String[]{
				"blue,   green,        red",
				"blue,   red,          red   -  green",
				"blue,   red,          blue  -  green",
				"blue,   blue  -  green,   red   -  green",
				"blue,   blue  -  green,   red   -  blue",
				"green,  red,          blue  -  green",
				"red,    blue  -  green,   red   -  green",
				"green,  blue  -  green,   red   -  green",
				"green,  red   -  green,    red   -  blue",
				"red,    red   -  green,    red   -  blue"};

			delta_type_string = new String[]{
				"horizontal","vertical","average","med","directional",
				"adaptive","scanline (1)","scanline (2)","scanline (3)","scanline (4)","gradient","frame map (1)","frame map (2)"};

			channel_init              = new int[6];
			channel_min               = new int[6];
			channel_delta_min         = new int[6];
			channel_sum               = new int[6];
			channel_length            = new int[6];
			channel_compressed_length = new int[6];
			channel_iterations        = new byte[3];

			if (raster_type == BufferedImage.TYPE_3BYTE_BGR)
			{
				pixel = new int[image_xdim * image_ydim];
				PixelGrabber pg = new PixelGrabber(original_image, 0, 0, image_xdim, image_ydim, pixel, 0, image_xdim);
				try { pg.grabPixels(); } catch (InterruptedException e) { System.err.println(e); e.printStackTrace(); }
				if ((pg.getStatus() & ImageObserver.ABORT) != 0) { System.err.println("Pixel grab aborted."); System.exit(1); }

				int[] blue = new int[image_xdim * image_ydim];
				int[] green = new int[image_xdim * image_ydim];
				int[] red   = new int[image_xdim * image_ydim];
				for (int i = 0; i < pixel.length; i++)
				{
					blue[i]  = (pixel[i] >> 16) & 0xff;
					green[i] = (pixel[i] >>  8) & 0xff;
					red[i]   =  pixel[i]         & 0xff;
				}
				channel_list.add(blue);
				channel_list.add(green);
				channel_list.add(red);

				working_image = new BufferedImage(image_xdim, image_ydim, BufferedImage.TYPE_INT_RGB);
				for (int i = 0; i < image_xdim; i++)
					for (int j = 0; j < image_ydim; j++)
						working_image.setRGB(i, j, pixel[j * image_xdim + i]);

				// ---- Compute initial fit scale ----
				Dimension screen = Toolkit.getDefaultToolkit().getScreenSize();
				screen_xdim = (int) screen.getWidth();
				screen_ydim = (int) screen.getHeight();
				int mw = (int)(screen_xdim * 0.70) - 40;
				int mh = (int)(screen_ydim * 0.70) - 80;
				fit_scale  = Math.min(1.0, Math.min((double)mw / image_xdim, (double)mh / image_ydim));
				zoom_scale = fit_scale;

				image_canvas = new ImageCanvas();
				scroll_pane  = new JScrollPane(image_canvas,
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
							JViewport vp = scroll_pane.getViewport();
							Point vpos   = vp.getViewPosition();
							Point mpt    = e.getPoint();
							int mcx = mpt.x + vpos.x, mcy = mpt.y + vpos.y;
							double old  = zoom_scale;
							zoom_scale  = (e.getWheelRotation() < 0)
								? Math.min(ZOOM_MAX, zoom_scale * ZOOM_FACTOR)
								: Math.max(ZOOM_MIN, zoom_scale / ZOOM_FACTOR);
							if (zoom_scale == old) return;
							updateDisplayImage();
							image_canvas.setPreferredSize(new Dimension((int)(image_xdim*zoom_scale),(int)(image_ydim*zoom_scale)));
							image_canvas.revalidate(); image_canvas.repaint();
							double r = zoom_scale / old;
							vp.setViewPosition(new Point(Math.max(0,(int)(mcx*r)-mpt.x), Math.max(0,(int)(mcy*r)-mpt.y)));
							updateTitle();
						}
						else scroll_pane.dispatchEvent(e);
					}
				});

				frame = new JFrame("Delta Writer  " + filename);
				openWindowCount++;
				frame.addWindowListener(new WindowAdapter() { public void windowClosing(WindowEvent e) { frame.dispose(); if (--openWindowCount == 0) System.exit(0); } });
				frame.getContentPane().add(scroll_pane, BorderLayout.CENTER);

				// ---- Menu bar ----
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
						new DeltaWriter(fd.getDirectory() + fd.getFile());
				});
				file_menu.add(open_item);
				file_menu.addSeparator();


				JMenuItem reset_item = new JMenuItem("Reset");
				reset_item.addActionListener(e ->
				{
					smooth_level = 0;  smooth2_level = 0;
					pixel_quant  = 0;  pixel_shift   = 0;  correction = 0;
					if (smooth_slider  != null) smooth_slider.setValue(0);
					if (smooth2_slider != null) smooth2_slider.setValue(0);
					if (pquant_slider  != null) pquant_slider.setValue(0);
					if (pshift_slider  != null) pshift_slider.setValue(0);
					if (corr_slider    != null) corr_slider.setValue(0);
					new ApplyHandler().actionPerformed(null);
				});
				file_menu.add(reset_item);
				JMenuItem save_item = new JMenuItem("Save");
				save_item.addActionListener(new SaveHandler());
				file_menu.add(save_item);

				// View menu
				JMenu view_menu = new JMenu("View");
				JMenuItem zi = new JMenuItem("Zoom In (+)");
				zi.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_EQUALS, InputEvent.CTRL_DOWN_MASK));
				zi.addActionListener(e -> zoomBy(ZOOM_FACTOR));
				view_menu.add(zi);
				JMenuItem zo = new JMenuItem("Zoom Out (-)");
				zo.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_MINUS, InputEvent.CTRL_DOWN_MASK));
				zo.addActionListener(e -> zoomBy(1.0 / ZOOM_FACTOR));
				view_menu.add(zo);
				JMenuItem zf = new JMenuItem("Fit to Window");
				zf.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_0, InputEvent.CTRL_DOWN_MASK));
				zf.addActionListener(e ->
				{
					Dimension vps = scroll_pane.getViewport().getSize();
					zoom_scale = Math.min((double)vps.width/image_xdim, (double)vps.height/image_ydim);
					updateDisplayImage();
					image_canvas.setPreferredSize(new Dimension((int)(image_xdim*zoom_scale),(int)(image_ydim*zoom_scale)));
					image_canvas.revalidate(); image_canvas.repaint(); updateTitle();
				});
				view_menu.add(zf);
				JMenuItem za = new JMenuItem("Actual Size (100%)");
				za.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_1, InputEvent.CTRL_DOWN_MASK));
				za.addActionListener(e ->
				{
					zoom_scale = 1.0; updateDisplayImage();
					image_canvas.setPreferredSize(new Dimension(image_xdim, image_ydim));
					image_canvas.revalidate(); image_canvas.repaint(); updateTitle();
				});
				view_menu.add(za);

				// Quantization menu
				JMenu quant_menu = new JMenu("Quantization");
				JSlider[] ss = new JSlider[1];
				quant_menu.add(makeSliderDialog(frame, "Smooth",           0, 10, smooth_level,  v -> { smooth_level  = v; new ApplyHandler().actionPerformed(null); }, ss)); smooth_slider  = ss[0];
				quant_menu.add(makeSliderDialog(frame, "Smooth2",          0, 10, smooth2_level, v -> { smooth2_level = v; new ApplyHandler().actionPerformed(null); }, ss)); smooth2_slider = ss[0];
				quant_menu.add(makeSliderDialog(frame, "Pixel Resolution",  0, 10, pixel_quant,  v -> { pixel_quant  = v; new ApplyHandler().actionPerformed(null); }, ss)); pquant_slider  = ss[0];
				quant_menu.add(makeSliderDialog(frame, "Color Resolution",  0,  7, pixel_shift,  v -> { pixel_shift  = v; new ApplyHandler().actionPerformed(null); }, ss)); pshift_slider  = ss[0];
				quant_menu.add(makeSliderDialog(frame, "Segment Length",    0, 10, pixel_segment, v -> pixel_segment = v));
				quant_menu.add(makeSliderDialog(frame, "Error Correction",  0, 10, correction,   v -> { correction   = v; new ApplyHandler().actionPerformed(null); }, ss)); corr_slider    = ss[0];

				// Datatype menu
				JMenu datatype_menu = new JMenu("Datatype");
				compress_button    = new JRadioButtonMenuItem[3];
				compress_button[0] = new JRadioButtonMenuItem("Integer");
				compress_button[1] = new JRadioButtonMenuItem("String");
				compress_button[2] = new JRadioButtonMenuItem("String*");
				ButtonGroup cg = new ButtonGroup();
				for (int i = 0; i < 3; i++) { cg.add(compress_button[i]); datatype_menu.add(compress_button[i]); }
				compress_button[compress_type].setSelected(true);
				for (int i = 0; i < 3; i++)
				{
					final int idx = i;
					compress_button[i].addActionListener(e ->
					{
						if (compress_type != idx) { compress_type = idx; new ApplyHandler().actionPerformed(null); }
					});
				}

				// Delta menu
				JMenu delta_menu = new JMenu("Delta");
				String[] dnames   = {"H","V","Average","Med","Directional","Gradient","Adaptive","Scanline 1","Scanline 2","Scanline 3","Scanline 4","Map (1)","Map (2)"};
				int[]    dtype_map = { 0,  1,  2,       3,    4,            10,        5,         6,           7,           8,           9,           11,        12};
				delta_button = new JRadioButtonMenuItem[13];
				ButtonGroup dg = new ButtonGroup();
				for (int i = 0; i < 13; i++)
				{
					delta_button[i] = new JRadioButtonMenuItem(dnames[i]);
					dg.add(delta_button[i]);
					delta_menu.add(delta_button[i]);
					final int dt = dtype_map[i];
					delta_button[i].addActionListener(e -> { if (delta_type != dt) { delta_type = dt; new ApplyHandler().actionPerformed(null); }});
				}
				delta_button[0].setSelected(true);

				// Entropy menu — LZ77(0), Huffman(1), Arithmetic(3), Slow Arithmetic(2)
				JMenu entropy_menu = new JMenu("Entropy");
				entropy_button = new JRadioButtonMenuItem[4];
				entropy_button[0] = new JRadioButtonMenuItem("LZ77");
				entropy_button[1] = new JRadioButtonMenuItem("Huffman");
				entropy_button[2] = new JRadioButtonMenuItem("Arithmetic");
				entropy_button[3] = new JRadioButtonMenuItem("Slow Arithmetic");
				ButtonGroup eg = new ButtonGroup();
				for (int i = 0; i < 4; i++) { eg.add(entropy_button[i]); entropy_menu.add(entropy_button[i]); }
				int[] entropy_map = {0, 1, 3, 2};   // button index → entropy_type value
				entropy_button[0].setSelected(entropy_type == 0);
				entropy_button[1].setSelected(entropy_type == 1);
				entropy_button[2].setSelected(entropy_type == 3);
				entropy_button[3].setSelected(entropy_type == 2);
				for (int i = 0; i < 4; i++)
				{
					final int et = entropy_map[i];
					entropy_button[i].addActionListener(e -> { if (entropy_type != et) entropy_type = et; });
				}

				menu_bar.add(file_menu);
					menu_bar.add(view_menu);
					menu_bar.add(quant_menu);
					menu_bar.add(delta_menu);
					menu_bar.add(datatype_menu);
					menu_bar.add(entropy_menu);

					// Statistics menu
					set_table_model = new javax.swing.table.DefaultTableModel(
						new String[]{"Rank","Channel 1","Channel 2","Channel 3","Entropy 1","Entropy 2","Entropy 3","Total"}, 0) {
						public boolean isCellEditable(int r, int c) { return false; }
						public Class getColumnClass(int c) { return (c==1||c==2||c==3) ? String.class : Integer.class; }
					};
					delta_table_model = new javax.swing.table.DefaultTableModel(
						new String[]{"Rank","Type","Delta","D*","Map","M*","Total",""}, 0) {
						public boolean isCellEditable(int r, int c) { return false; }
						public Class getColumnClass(int c) { return (c==0||c==2||c==4||c==6) ? Integer.class : String.class; }
					};

					set_jtable   = new JTable(set_table_model);
					delta_jtable = new JTable(delta_table_model);
					set_jtable.setAutoResizeMode(JTable.AUTO_RESIZE_ALL_COLUMNS);
					delta_jtable.setAutoResizeMode(JTable.AUTO_RESIZE_ALL_COLUMNS);

					javax.swing.table.DefaultTableCellRenderer center = new javax.swing.table.DefaultTableCellRenderer();
					center.setHorizontalAlignment(JLabel.CENTER);
					javax.swing.table.DefaultTableCellRenderer left = new javax.swing.table.DefaultTableCellRenderer();
					left.setHorizontalAlignment(JLabel.LEFT);
					for (int c = 0; c < set_table_model.getColumnCount(); c++)
						set_jtable.getColumnModel().getColumn(c).setCellRenderer(c>=1&&c<=3 ? left : center);
					for (int c = 0; c < delta_table_model.getColumnCount(); c++) delta_jtable.getColumnModel().getColumn(c).setCellRenderer(center);
					stats_set_dialog   = new JDialog(frame, "Color Sets",  false);
					stats_delta_dialog = new JDialog(frame, "Delta Types", false);
					stats_set_dialog.add(new JScrollPane(set_jtable));
					stats_delta_dialog.add(new JScrollPane(delta_jtable));

					JMenu stats_menu = new JMenu("Statistics");
					JMenuItem stats_set   = new JMenuItem("Color Sets");
					JMenuItem stats_delta = new JMenuItem("Delta Types");
					stats_set.addActionListener(e   -> { stats_set_dialog.setVisible(true); });
					stats_delta.addActionListener(e -> { stats_delta_dialog.setVisible(true); });
					stats_menu.add(stats_set); stats_menu.add(stats_delta);
					menu_bar.add(stats_menu);

					frame.setJMenuBar(menu_bar);

				display_image = original_image;
				image_canvas.setPreferredSize(new Dimension((int)(image_xdim*zoom_scale),(int)(image_ydim*zoom_scale)));
				updateTitle();
				frame.setSize(Math.min(image_xdim+40,(int)(screen_xdim*0.70)), Math.min(image_ydim+80,(int)(screen_ydim*0.70)));
				int _off = nextWindowOffset; nextWindowOffset = (_off + 30) % 270;
				frame.setLocation((screen_xdim - frame.getWidth()) / 2 + _off, (screen_ydim - frame.getHeight()) / 2 + _off);
				frame.setVisible(true);
				SwingUtilities.invokeLater(() -> showInitialImage());
			}
		}
		catch (Exception e) { System.out.println(e); e.printStackTrace(); System.exit(1); }
	}

	// ---- Slider-dialog factory helper ----------------------------------------
	private JMenuItem makeSliderDialog(JFrame parent, String title, int lo, int hi, int init, java.util.function.IntConsumer onChange)
	{
		return makeSliderDialog(parent, title, lo, hi, init, onChange, null);
	}

	private JMenuItem makeSliderDialog(JFrame parent, String title, int lo, int hi, int init, java.util.function.IntConsumer onChange, JSlider[] ref)
	{
		JMenuItem  item   = new JMenuItem(title);
		JDialog    dialog = new JDialog(parent, title);
		JSlider    slider = new JSlider(lo, hi, init);
		if (ref != null) ref[0] = slider;
		slider.setMajorTickSpacing(1);
		slider.setPaintTicks(true);
		slider.setSnapToTicks(true);
		JTextField field  = new JTextField(3);
		field.setText(" " + init + " ");
		slider.addChangeListener(e ->
		{
			int v = slider.getValue();
			field.setText(" " + v + " ");
			if (!slider.getValueIsAdjusting())
				onChange.accept(v);
		});
		JPanel p = new JPanel(new BorderLayout());
		p.add(slider, BorderLayout.CENTER);
		p.add(field,  BorderLayout.EAST);
		dialog.add(p);
		item.addActionListener(e ->
		{
			Point loc = parent.getLocation();
			dialog.setLocation((int)loc.getX(), (int)loc.getY() - 60);
			dialog.pack();
			dialog.setVisible(true);
		});
		return item;
	}

	private void showInitialImage()
	{
		skip_ranking = true;
		// Reset all quantization parameters for each new image
		smooth_level = 0;  smooth2_level = 0;
		pixel_quant  = 4;  pixel_shift   = 3;  correction = 0;
		if (smooth_slider  != null) smooth_slider.setValue(0);
		if (smooth2_slider != null) smooth2_slider.setValue(0);
		if (pquant_slider  != null) pquant_slider.setValue(4);
		if (pshift_slider  != null) pshift_slider.setValue(3);
		if (corr_slider    != null) corr_slider.setValue(0);
		Dimension vps = scroll_pane.getViewport().getSize();
		fit_scale  = Math.min(1.0, Math.min(vps.width>0?(double)vps.width/image_xdim:1.0, vps.height>0?(double)vps.height/image_ydim:1.0));
		zoom_scale = fit_scale;
		updateDisplayImage();

		// Shrink frame to match actual canvas size — avoids excess space for
		// landscape images where height is not the limiting dimension.
		int display_w  = (int)(image_xdim * zoom_scale);
		int display_h  = (int)(image_ydim * zoom_scale);
		int overhead_w = frame.getWidth()  - vps.width;
		int overhead_h = frame.getHeight() - vps.height;
		int new_frame_w = Math.min(display_w + overhead_w, (int)(screen_xdim * 0.70));
		int new_frame_h = Math.min(display_h + overhead_h, (int)(screen_ydim * 0.70));
		if (new_frame_w < frame.getWidth() || new_frame_h < frame.getHeight())
			frame.setSize(new_frame_w, new_frame_h);

		image_canvas.setPreferredSize(new Dimension(display_w, display_h));
		image_canvas.revalidate(); image_canvas.repaint(); updateTitle();

		// Show immediate preview with current parameters while init() runs
		skip_ranking = true;
		new ApplyHandler().actionPerformed(null);
		skip_ranking = false;

		new javax.swing.SwingWorker<Void, Void>()
		{
			@Override protected Void doInBackground() { init(); return null; }
			@Override protected void done()
			{
				int[] dm = {0,1,2,3,4,10,5,6,7,8,9,11,12};
				for (int i2 = 0; i2 < 13; i2++) if (dm[i2] == delta_type) { delta_button[i2].setSelected(true); break; }
				compress_button[compress_type].setSelected(true);
				new ApplyHandler().actionPerformed(null);
			}
		}.execute();
	}

	private void zoomBy(double factor)
	{
		double ns = Math.max(ZOOM_MIN, Math.min(ZOOM_MAX, zoom_scale * factor));
		if (ns == zoom_scale) return;
		JViewport vp = scroll_pane.getViewport();
		Point vpos   = vp.getViewPosition();
		Dimension vs = vp.getSize();
		double cx = vpos.x + vs.width/2.0, cy = vpos.y + vs.height/2.0;
		double r  = ns / zoom_scale;
		zoom_scale = ns;
		updateDisplayImage();
		image_canvas.setPreferredSize(new Dimension((int)(image_xdim*zoom_scale),(int)(image_ydim*zoom_scale)));
		image_canvas.revalidate(); image_canvas.repaint();
		vp.setViewPosition(new Point(Math.max(0,(int)(cx*r-vs.width/2.0)), Math.max(0,(int)(cy*r-vs.height/2.0))));
		updateTitle();
	}

	private void updateDisplayImage()
	{
		BufferedImage src = (working_image != null && initialized) ? working_image : original_image;
		if (zoom_scale == 1.0) { display_image = src; return; }
		int w = Math.max(1,(int)(image_xdim*zoom_scale)), h = Math.max(1,(int)(image_ydim*zoom_scale));
		AffineTransform t = new AffineTransform(); t.scale(zoom_scale, zoom_scale);
		AffineTransformOp op = new AffineTransformOp(t, AffineTransformOp.TYPE_BILINEAR);
		display_image = op.filter(src, new BufferedImage(w, h, src.getType()));
	}

	private void updateTitle()
	{
		frame.setTitle("Delta Writer  " + filename + "  [" + (int)Math.round(zoom_scale*100) + "%]");
	}

	// ---- table I/O helpers (used by SaveHandler) ----------------------------
	private static void writeTable(DataOutputStream out, int[] table) throws IOException
	{
		out.writeShort(table.length);
		int max = Byte.MAX_VALUE * 2 + 1;
		if (table.length <= max)
			for (int v : table) out.writeByte(v);
		else
			for (int v : table) out.writeShort(v);
	}

	private void computeRanking(ArrayList<int[]> qcl6, int[] cid, int xdim, int ydim, boolean set_delta_type)
	{
		rank_delta_sum  = new int[13];
		rank_delta_cost = new int[13];
		rank_map_cost   = new int[13];
		rank_delta_comp = new boolean[13];
		rank_map_comp   = new boolean[13];

		for (int i = 0; i < 3; i++)
		{
			int[]            qc  = qcl6.get(cid[i]);
			ArrayList<int[]> res;

			ArrayList[] encs = {
				DeltaMapper.getHorizontalDeltasFromValues(qc, xdim, ydim),
				DeltaMapper.getVerticalDeltasFromValues(qc, xdim, ydim),
				DeltaMapper.getAverageDeltasFromValues(qc, xdim, ydim),
				DeltaMapper.getMedDeltasFromValues(qc, xdim, ydim),
				DeltaMapper.getDirectionalDeltasFromValues(qc, xdim, ydim),
				DeltaMapper.getAdaptiveDeltasFromValues(qc, xdim, ydim),
			};
			for (int t = 0; t < 6; t++)
			{
				byte[] packed = (byte[]) StringMapper.getStringList((int[]) encs[t].get(1), false).get(3);
				byte[] cstr   = StringMapper.compressStrings(packed);
				int    bl     = StringMapper.getBitlength(cstr);
				rank_delta_sum[t] += bl; rank_delta_cost[t] += bl;
				if ((StringMapper.getIterations(cstr) & 15) > 0) rank_delta_comp[t] = true;
			}
			{ byte[] p10 = (byte[]) StringMapper.getStringList((int[]) DeltaMapper.getGradientDeltasFromValues(qc, xdim, ydim).get(1), false).get(3);
			  byte[] c10 = StringMapper.compressStrings(p10);
			  int bl10   = StringMapper.getBitlength(c10);
			  rank_delta_sum[10] += bl10; rank_delta_cost[10] += bl10;
			  if ((StringMapper.getIterations(c10) & 15) > 0) rank_delta_comp[10] = true; }

			res = DeltaMapper.getMedScanlineFrequency(qc, xdim, ydim);
			{ ArrayList dsl = StringMapper.getStringList(res.get(1), false); byte[] ms = (byte[]) dsl.get(3);
			  int dc = (int) Math.floor(CodeMapper.getShannonLimit(res.get(0))), mc = StringMapper.getBitlength(ms);
			  rank_delta_sum[6] += dc+mc; rank_delta_cost[6] += dc; rank_map_cost[6] += mc;
			  if ((StringMapper.getIterations(StringMapper.compressStrings(ms)) & 15) > 0) rank_map_comp[6] = true; }

			res = DeltaMapper.getScanline2Frequency(qc, xdim, ydim);
			{ ArrayList dsl = StringMapper.getStringList(res.get(1), false); byte[] ms = (byte[]) dsl.get(3);
			  int dc = (int) Math.floor(CodeMapper.getShannonLimit(res.get(0))), mc = StringMapper.getBitlength(ms);
			  rank_delta_sum[7] += dc+mc; rank_delta_cost[7] += dc; rank_map_cost[7] += mc;
			  if ((StringMapper.getIterations(StringMapper.compressStrings(ms)) & 15) > 0) rank_map_comp[7] = true; }

			res = DeltaMapper.getMixedDeltas4Frequency(qc, xdim, ydim);
			{ ArrayList dsl = StringMapper.getStringList(res.get(1), false); byte[] ms = (byte[]) dsl.get(3);
			  int dc = (int) Math.floor(CodeMapper.getShannonLimit(res.get(0))), mc = StringMapper.getBitlength(ms);
			  rank_delta_sum[8] += dc+mc; rank_delta_cost[8] += dc; rank_map_cost[8] += mc;
			  if ((StringMapper.getIterations(StringMapper.compressStrings(ms)) & 15) > 0) rank_map_comp[8] = true; }

			ArrayList<int[]> r16r = DeltaMapper.getMixedDeltas16Frequency(qc, xdim, ydim);
			{ ArrayList dsl = StringMapper.getStringList(r16r.get(1), false); byte[] ms = (byte[]) dsl.get(3);
			  int dc = (int) Math.floor(CodeMapper.getShannonLimit(r16r.get(0))), mc = StringMapper.getBitlength(ms);
			  rank_delta_sum[9] += dc+mc; rank_delta_cost[9] += dc; rank_map_cost[9] += mc;
			  if ((StringMapper.getIterations(StringMapper.compressStrings(ms)) & 15) > 0) rank_map_comp[9] = true; }

			ArrayList<int[]> r8 = DeltaMapper.getIdealFrequency8(qc, xdim, ydim);
			rank_delta_sum[11] += (int) Math.floor(CodeMapper.getShannonLimit(r8.get(0)) + CodeMapper.getShannonLimit(r8.get(1)));
			rank_delta_cost[11] += (int) Math.floor(CodeMapper.getShannonLimit(r8.get(0)));
			rank_map_cost[11]   += (int) Math.floor(CodeMapper.getShannonLimit(r8.get(1)));

			ArrayList<int[]> r16 = DeltaMapper.getIdealFrequency16(qc, xdim, ydim);
			rank_delta_sum[12] += (int) Math.floor(CodeMapper.getShannonLimit(r16.get(0)) + CodeMapper.getShannonLimit(r16.get(1)));
			rank_delta_cost[12] += (int) Math.floor(CodeMapper.getShannonLimit(r16.get(0)));
			rank_map_cost[12]   += (int) Math.floor(CodeMapper.getShannonLimit(r16.get(1)));
		}

		int min_sum = rank_delta_sum[0], min_idx = 0;
		for (int i = 1; i < 13; i++) if (rank_delta_sum[i] < min_sum) { min_sum = rank_delta_sum[i]; min_idx = i; }
		if (set_delta_type) delta_type = min_idx;

		rank_set_order = new Integer[10];
		for (int i = 0; i < 10; i++) rank_set_order[i] = i;
		java.util.Arrays.sort(rank_set_order, (a, b) -> set_sum[a] - set_sum[b]);
		rank_dt_order = new Integer[13];
		for (int i = 0; i < 13; i++) rank_dt_order[i] = i;
		java.util.Arrays.sort(rank_dt_order, (a, b) -> rank_delta_sum[a] - rank_delta_sum[b]);
		final boolean do_update = !skip_ranking;
		SwingUtilities.invokeLater(() -> { if (do_update) DeltaWriter.this.updateTables(); });
	}

	private void fitColumns(JTable t)
	{
		for (int col = 0; col < t.getColumnCount(); col++)
		{
			int w = t.getTableHeader().getDefaultRenderer()
				.getTableCellRendererComponent(t, t.getColumnModel().getColumn(col).getHeaderValue(), false, false, 0, col)
				.getPreferredSize().width;
			for (int row = 0; row < t.getRowCount(); row++)
				w = Math.max(w, t.prepareRenderer(t.getCellRenderer(row, col), row, col).getPreferredSize().width);
			t.getColumnModel().getColumn(col).setPreferredWidth(w + 12);
		}
		t.setPreferredScrollableViewportSize(t.getPreferredSize());
	}

	private void updateTables()
	{
		if (rank_set_order == null || rank_set_order[0] == null || skip_ranking) return;

		// Console output
		System.out.println("=== " + filename + " ===");
		System.out.println("Channel sets (ranked):");
		for (int rank = 0; rank < 10; rank++)
		{
			int idx = rank_set_order[rank];
			System.out.println(String.format("  %2d. %-34s %12d", rank+1, set_string[idx], set_sum[idx]));
		}
		System.out.println();
		System.out.println("Delta types (ranked):");
		for (int rank = 0; rank < 13; rank++)
		{
			int    idx  = rank_dt_order[rank];
			String dm   = rank_delta_comp[idx] ? "*" : " ";
			String mm   = rank_map_comp[idx]   ? "*" : " ";
			String sel  = (idx == delta_type)  ? " **" : "";
			if (rank_map_cost[idx] > 0)
				System.out.println(String.format("  %2d. %-16s delta: %12d%s      map: %12d%s      total: %12d%s",
					rank+1, delta_type_string[idx], rank_delta_cost[idx], dm, rank_map_cost[idx], mm, rank_delta_sum[idx], sel));
			else
				System.out.println(String.format("  %2d. %-16s delta: %12d%s                              total: %12d%s",
					rank+1, delta_type_string[idx], rank_delta_cost[idx], dm, rank_delta_sum[idx], sel));
		}
		System.out.println();

		// JTable update
		if (set_table_model == null || delta_table_model == null) return;

		set_table_model.setRowCount(0);
		for (int rank = 0; rank < 10; rank++)
		{
			int    idx = rank_set_order[rank];
			int[]  cid = DeltaMapper.getChannels(idx);
			set_table_model.addRow(new Object[]{
				rank+1,
				channel_string[cid[0]], channel_string[cid[1]], channel_string[cid[2]],
				set_channel_sum[idx][0], set_channel_sum[idx][1], set_channel_sum[idx][2],
				set_sum[idx]
			});
		}

		delta_table_model.setRowCount(0);
		for (int rank = 0; rank < 13; rank++)
		{
			int    idx  = rank_dt_order[rank];
			Object map  = rank_map_cost[idx] > 0 ? rank_map_cost[idx] : null;
			Object mstar = rank_map_cost[idx] > 0 ? (rank_map_comp[idx] ? "\u2713" : "") : null;
			delta_table_model.addRow(new Object[]{
				rank + 1,
				delta_type_string[idx],
				rank_delta_cost[idx],
				rank_delta_comp[idx] ? "\u2713" : "",
				map,
				mstar,
				rank_delta_sum[idx],
				idx == delta_type ? "\u2605" : ""
			});
		}
		if (set_jtable   != null) { fitColumns(set_jtable);   if (stats_set_dialog   != null) stats_set_dialog.pack(); }
		if (delta_jtable != null) { fitColumns(delta_jtable); if (stats_delta_dialog != null) stats_delta_dialog.pack(); }
	}

	private void computeSetSums()
	{
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
		for (int i = 0; i < 10; i++)
		{
			int[] cid = DeltaMapper.getChannels(i);
			set_channel_sum[i][0] = channel_sum[cid[0]];
			set_channel_sum[i][1] = channel_sum[cid[1]];
			set_channel_sum[i][2] = channel_sum[cid[2]];
		}
	}

	private void writeMap(DataOutputStream out, int i) throws IOException
	{
		byte[] map     = (byte[]) map_list.get(i);
		int[]  map_int = new int[map.length];
		for (int q = 0; q < map.length; q++) map_int[q] = map[q] & 0xFF;
		ArrayList  dsl    = StringMapper.getStringList(map_int, false);
		int        dmin   = (int)    dsl.get(0);
		int[]      tbl    = (int[])  dsl.get(2);
		byte[]     str    = (byte[]) dsl.get(3);
		int        bl     = StringMapper.getBitlength(str);
		out.writeInt(map.length);
		writeTable(out, tbl);
		out.writeInt(dmin);
		out.writeInt(bl);
		out.write(str, 0, StringMapper.getBytelength(bl));
	}

	// =========================================================================
	// ImageCanvas
	// =========================================================================
	class ImageCanvas extends JPanel
	{
		public ImageCanvas() { setOpaque(true); }
		@Override public Dimension getPreferredSize()
		{
			return (display_image != null)
				? new Dimension(display_image.getWidth(), display_image.getHeight())
				: new Dimension(Math.max(1,(int)(image_xdim*zoom_scale)), Math.max(1,(int)(image_ydim*zoom_scale)));
		}
		@Override protected synchronized void paintComponent(Graphics g)
		{
			super.paintComponent(g);
			if (display_image != null) g.drawImage(display_image, 0, 0, this);
		}
	}

	// =========================================================================
	// ApplyHandler — preview (no entropy coding; same for all entropy types)
	// =========================================================================
	class ApplyHandler implements ActionListener
	{
		public void actionPerformed(ActionEvent event)
		{
			try   { applyImpl(); }
			catch (Exception e) { System.out.println("ApplyHandler exception: " + e); e.printStackTrace(); }
		}

		private void applyImpl()
		{
		ArrayList<int[]> qcl  = new ArrayList<int[]>();   // quantized channel list
		ArrayList<int[]> dqcl = new ArrayList<int[]>();   // dequantized channel list

			int new_xdim = image_xdim, new_ydim = image_ydim;
			if (pixel_quant != 0)
			{
				double f = pixel_quant / 10.0;
				new_xdim = image_xdim - (int)(f * (image_xdim / 2 - 2));
				new_ydim = image_ydim - (int)(f * (image_ydim / 2 - 2));
			}

			for (int i = 0; i < 3; i++)
			{
				int[] ch = (int[]) channel_list.get(i);
				if (smooth_level > 0)
					ch = DeltaMapper.bilateralSmooth(ch, image_xdim, image_ydim, smooth_level);
				if (smooth2_level > 0)
					ch = DeltaMapper.anisotropicSmooth(ch, image_xdim, image_ydim, smooth2_level);
				if (pixel_quant == 0)
					qcl.add(pixel_shift == 0 ? ch : DeltaMapper.shift(ch, -pixel_shift));
				else
				{
					int[] r = ResizeMapper.resize(ch, image_xdim, new_xdim, new_ydim);
					qcl.add(pixel_shift == 0 ? r : DeltaMapper.shift(r, -pixel_shift));
				}
			}

			qcl.add(DeltaMapper.getDifference(qcl.get(0), qcl.get(1)));
			qcl.add(DeltaMapper.getDifference(qcl.get(2), qcl.get(1)));
			qcl.add(DeltaMapper.getDifference(qcl.get(2), qcl.get(0)));

			for (int i = 0; i < 6; i++)
			{
				int[] qc = qcl.get(i); int min = 256;
				for (int v : qc) if (v < min) min = v;
				channel_min[i] = min;
				if (i > 2) for (int k = 0; k < qc.length; k++) qc[k] -= min;
				channel_init[i] = qc[0];
				qcl.set(i, qc);
				channel_sum[i] = (int) Math.floor(CodeMapper.getShannonLimit(DeltaMapper.getIdealFrequency(qc, new_xdim, new_ydim)));
			}

			DeltaWriter.this.computeSetSums();

			int min_sum = Integer.MAX_VALUE, min_idx = 0;
			for (int i = 0; i < 10; i++) if (set_sum[i] < min_sum) { min_sum = set_sum[i]; min_idx = i; }
			min_set_id = min_idx;
			file_compression_rate = (double) file_length / (image_xdim * image_ydim * 3);

			int[] channel_id = DeltaMapper.getChannels(min_set_id);
			table_list.clear(); string_list.clear(); map_list.clear(); delta_list.clear();

			// Guard: Integer mode overflows if 2 × channel value range > 255.
			// Re-check here since pixel_shift or pixel_quant may have changed since init().
			if (compress_type == 0)
			{
				for (int i = 0; i < 3; i++)
				{
					int[] qc = qcl.get(channel_id[i]);
					int cmin = qc[0], cmax = qc[0];
					for (int v : qc) { if (v < cmin) cmin = v; if (v > cmax) cmax = v; }
					if ((cmax - cmin) * 2 > 255)
					{
						compress_type = 1;
						SwingUtilities.invokeLater(() -> compress_button[1].setSelected(true));
						break;
					}
				}
			}

			for (int i = 0; i < 3; i++)
			{
				int   j  = channel_id[i];
				int[] qc = qcl.get(j);

				ArrayList<Object> result = new ArrayList<Object>();
				if      (delta_type == 0) result = DeltaMapper.getHorizontalDeltasFromValues(qc, new_xdim, new_ydim);
				else if (delta_type == 1) result = DeltaMapper.getVerticalDeltasFromValues(qc, new_xdim, new_ydim);
				else if (delta_type == 2) result = DeltaMapper.getAverageDeltasFromValues(qc, new_xdim, new_ydim);
				else if (delta_type == 3) result = DeltaMapper.getMedDeltasFromValues(qc, new_xdim, new_ydim);
				else if (delta_type == 4) result = DeltaMapper.getDirectionalDeltasFromValues(qc, new_xdim, new_ydim);
				else if (delta_type == 5)    result = DeltaMapper.getAdaptiveDeltasFromValues(qc, new_xdim, new_ydim);
				else if (delta_type == 6)  { result = DeltaMapper.getMixedDeltasFromValues(qc, new_xdim, new_ydim);   map_list.add(result.get(2)); }
				else if (delta_type == 7)  { result = DeltaMapper.getMixedDeltasFromValues2(qc, new_xdim, new_ydim);  map_list.add(result.get(2)); }
				else if (delta_type == 8)  { result = DeltaMapper.getMixedDeltasFromValues4(qc, new_xdim, new_ydim);  map_list.add(result.get(2)); }
				else if (delta_type == 9)  { result = DeltaMapper.getMixedDeltasFromValues16Rows(qc, new_xdim, new_ydim); map_list.add(result.get(2)); }
				else if (delta_type == 10) { result = DeltaMapper.getGradientDeltasFromValues(qc, new_xdim, new_ydim); }
				else if (delta_type == 11) { result = DeltaMapper.getIdealDeltasFromValues8(qc, new_xdim, new_ydim);    map_list.add(result.get(2)); }
				else if (delta_type == 12) { result = DeltaMapper.getIdealDeltasFromValues16(qc, new_xdim, new_ydim);  map_list.add(result.get(2)); }

				int[] delta = (int[]) result.get(1);

				if (compress_type == 0)
				{
					ArrayList hl         = StringMapper.getHistogram(delta);
					int       delta_min  = (int) hl.get(0);
					channel_delta_min[j] = delta_min;
					byte[]    db         = new byte[delta.length];
					db[0] = 0;
					for (int k = 1; k < delta.length; k++) db[k] = (byte)(delta[k] - delta_min);
					delta_list.add(db);
					for (int k = 1; k < delta.length; k++) delta[k] = db[k] + delta_min;
				}
				else
				{
					boolean precompress = (compress_type == 2);
					ArrayList dsl       = StringMapper.getStringList(delta, precompress);
					channel_delta_min[j]          = (int)    dsl.get(0);
					channel_length[j]             = (int)    dsl.get(1);
					int[]  string_table           = (int[])  dsl.get(2);
					byte[] compression_string     = (byte[]) dsl.get(3);
					table_list.add(string_table);
					string_list.add(compression_string);
					channel_compressed_length[j]  = StringMapper.getBitlength(compression_string);
					channel_iterations[i]         = StringMapper.getIterations(compression_string);
					for (int k = 1; k < delta.length; k++) delta[k] += channel_delta_min[j];
				}
			}

			// ---- Decode pass for preview ----
			for (int i = 0; i < 3; i++)
			{
				int   j     = channel_id[i];
				int[] delta = new int[new_xdim * new_ydim];

				if (compress_type == 0)
				{
					byte[] db = (byte[]) delta_list.get(i);
					delta[0]  = 0;
					for (int k = 1; k < delta.length; k++) delta[k] = db[k] + channel_delta_min[j];
				}
				else
				{
					int[]  tbl  = (int[])  table_list.get(i);
					byte[] str  = StringMapper.decompressStrings((byte[]) string_list.get(i));
					delta       = StringMapper.unpackStrings(str, tbl, new_xdim * new_ydim, channel_length[j]);
					delta[0]    = 0;
					for (int k = 1; k < delta.length; k++) delta[k] += channel_delta_min[j];
				}

				int[] ch = new int[0];
				if      (delta_type == 0) ch = DeltaMapper.getValuesFromHorizontalDeltas(delta, new_xdim, new_ydim, channel_init[j]);
				else if (delta_type == 1) ch = DeltaMapper.getValuesFromVerticalDeltas(delta, new_xdim, new_ydim, channel_init[j]);
				else if (delta_type == 2) ch = DeltaMapper.getValuesFromAverageDeltas(delta, new_xdim, new_ydim, channel_init[j]);
				else if (delta_type == 3) ch = DeltaMapper.getValuesFromMedDeltas(delta, new_xdim, new_ydim, channel_init[j]);
				else if (delta_type == 4) ch = DeltaMapper.getValuesFromDirectionalDeltas(delta, new_xdim, new_ydim, channel_init[j]);
				else if (delta_type == 5)  ch = DeltaMapper.getValuesFromAdaptiveDeltas(delta, new_xdim, new_ydim, channel_init[j]);
				else if (delta_type == 6)  ch = DeltaMapper.getValuesFromMixedDeltas(delta, new_xdim, new_ydim, channel_init[j], (byte[]) map_list.get(i));
				else if (delta_type == 7)  ch = DeltaMapper.getValuesFromMixedDeltas2(delta, new_xdim, new_ydim, channel_init[j], (byte[]) map_list.get(i));
				else if (delta_type == 8)  ch = DeltaMapper.getValuesFromMixedDeltas4(delta, new_xdim, new_ydim, channel_init[j], (byte[]) map_list.get(i));
				else if (delta_type == 9)  ch = DeltaMapper.getValuesFromMixedDeltas16Rows(delta, new_xdim, new_ydim, channel_init[j], (byte[]) map_list.get(i));
				else if (delta_type == 10) ch = DeltaMapper.getValuesFromGradientDeltas(delta, new_xdim, new_ydim, channel_init[j]);
					else if (delta_type == 11) ch = DeltaMapper.getValuesFromIdealDeltas8(delta, new_xdim, new_ydim, channel_init[j], (byte[]) map_list.get(i));
				else if (delta_type == 12) ch = DeltaMapper.getValuesFromIdealDeltas16(delta, new_xdim, new_ydim, channel_init[j], (byte[]) map_list.get(i));

				if (j > 2) for (int k = 0; k < ch.length; k++) ch[k] += channel_min[j];

				if (pixel_shift == 0)
					dqcl.add(pixel_quant == 0 ? ch : ResizeMapper.resize(ch, new_xdim, image_xdim, image_ydim));
				else
				{
					int[] sh = DeltaMapper.shift(ch, pixel_shift);
					dqcl.add(pixel_quant == 0 ? sh : ResizeMapper.resize(sh, new_xdim, image_xdim, image_ydim));
				}
			}

			int[] blue = new int[image_xdim*image_ydim], green = new int[image_xdim*image_ydim], red = new int[image_xdim*image_ydim];
			if      (min_set_id==0){blue=dqcl.get(0);green=dqcl.get(1);red=dqcl.get(2);}
			else if (min_set_id==1){blue=dqcl.get(0);red=dqcl.get(1);green=DeltaMapper.getDifference(red,dqcl.get(2));}
			else if (min_set_id==2){blue=dqcl.get(0);red=dqcl.get(1);green=DeltaMapper.getDifference(blue,dqcl.get(2));}
			else if (min_set_id==3){blue=dqcl.get(0);green=DeltaMapper.getDifference(blue,dqcl.get(1));red=DeltaMapper.getSum(dqcl.get(2),green);}
			else if (min_set_id==4){blue=dqcl.get(0);green=DeltaMapper.getDifference(blue,dqcl.get(1));red=DeltaMapper.getSum(blue,dqcl.get(2));}
			else if (min_set_id==5){green=dqcl.get(0);red=dqcl.get(1);blue=DeltaMapper.getSum(dqcl.get(2),green);}
			else if (min_set_id==6){red=dqcl.get(0);int[]bg=dqcl.get(1);int[]rg=dqcl.get(2);for(int i=0;i<rg.length;i++)rg[i]=-rg[i];green=DeltaMapper.getSum(rg,red);blue=DeltaMapper.getSum(bg,green);}
			else if (min_set_id==7){green=dqcl.get(0);blue=DeltaMapper.getSum(green,dqcl.get(1));red=DeltaMapper.getSum(green,dqcl.get(2));}
			else if (min_set_id==8){green=dqcl.get(0);red=DeltaMapper.getSum(green,dqcl.get(1));blue=DeltaMapper.getDifference(red,dqcl.get(2));}
			else if (min_set_id==9){red=dqcl.get(0);green=DeltaMapper.getDifference(red,dqcl.get(1));blue=DeltaMapper.getDifference(red,dqcl.get(2));}

			int[] ob=(int[])channel_list.get(0), og=(int[])channel_list.get(1), or_=(int[])channel_list.get(2);
			for (int i = 0; i < image_xdim*image_ydim; i++)
			{
				if (correction != 0)
				{
					double f = correction / 10.0;
					blue[i]  += (int)((ob[i]-blue[i])  * f);
					green[i] += (int)((og[i]-green[i]) * f);
					red[i]   += (int)((or_[i]-red[i])  * f);
				}
			}
			int k = 0;
			for (int i = 0; i < image_ydim; i++)
				for (int j = 0; j < image_xdim; j++)
					working_image.setRGB(j, i, (blue[k]<<16)+(green[k]<<8)+red[k++]);

			updateDisplayImage(); image_canvas.repaint();
			initialized = true;
			DeltaWriter.this.computeRanking(qcl, DeltaMapper.getChannels(min_set_id), new_xdim, new_ydim, false);
		}
	}

	// =========================================================================
	// =========================================================================
	// SaveHandler — LZ77 (0), Huffman (1), Slow Arithmetic BigInteger (2), Arithmetic (3)
	// =========================================================================
	class SaveHandler implements ActionListener
	{
		public void actionPerformed(ActionEvent event)
		{
			if (!initialized) new ApplyHandler().actionPerformed(null);
			int[] channel_id = DeltaMapper.getChannels(min_set_id);

			try
			{
				DataOutputStream out = new DataOutputStream(new FileOutputStream(new File("foo")));

				// ---- Header ----
				out.writeShort(image_xdim);
				out.writeShort(image_ydim);
				out.writeByte(pixel_shift);
				out.writeByte(pixel_quant);
				out.writeByte(min_set_id);
				out.writeByte(delta_type);
				out.writeByte(compress_type);
				out.writeByte(entropy_type);

				if (entropy_type == 0 || entropy_type == 1)
				{
					// ---- LZ77 and Huffman: per-channel sequential write ----
					for (int i = 0; i < 3; i++)
					{
						int j = channel_id[i];
						out.writeInt(channel_min[j]);
						out.writeInt(channel_init[j]);
						out.writeInt(channel_delta_min[j]);
						out.writeInt(channel_length[j]);
						out.writeInt(channel_compressed_length[j]);
						out.writeByte(channel_iterations[i]);
						if (delta_type >= 6 && delta_type != 10) writeMap(out, i);
						if (compress_type > 0) writeTable(out, (int[]) table_list.get(i));

						byte[] payload = getPayload(i);
						if (entropy_type == 0)
						{
							// LZ77
							Deflater def    = new Deflater(Deflater.BEST_COMPRESSION);
							byte[]   zipped = new byte[2 * payload.length];
							def.setInput(payload); def.finish();
							int zl = def.deflate(zipped); def.end();
							out.writeInt(payload.length); out.writeInt(zl); out.write(zipped, 0, zl);
						}
						else
						{
							// Huffman
							int[] pi = new int[payload.length];
							for (int k = 0; k < payload.length; k++) { pi[k] = payload[k]; if (pi[k] < 0) pi[k] += 256; }
							ArrayList hl  = StringMapper.getHistogram(pi);
							int pmin      = (int) hl.get(0);
							int[] hist    = (int[]) hl.get(1);
							int[] rt      = StringMapper.getRankTable(hist);
							for (int k = 0; k < pi.length; k++) pi[k] -= pmin;
							int n = hist.length;
							ArrayList<Integer> fl = new ArrayList<>();
							for (int v : hist) fl.add(v);
							Collections.sort(fl, Comparator.reverseOrder());
							int[] freq = new int[n]; for (int k = 0; k < n; k++) freq[k] = fl.get(k);
							byte[] hl2  = CodeMapper.getHuffmanLength2(freq);
							int[]  hc   = CodeMapper.getCanonicalCode(hl2);
							ArrayList pl = CodeMapper.packCode(pi, rt, hc, hl2);
							byte[] pb   = (byte[]) pl.get(0);
							int    bl   = (int)    pl.get(1);
							writeTable(out, rt);
							out.writeInt(pmin);
							ArrayList ltl  = CodeMapper.packLengthTable(hl2);
							int    ltn     = (int)   ltl.get(0);
							byte   ltinit  = (byte)  ltl.get(1);
							byte   ltmax   = (byte)  ltl.get(2);
							byte[] ltdelta = (byte[]) ltl.get(3);
							out.writeInt(ltn); out.writeByte(ltinit); out.writeByte(ltmax);
							out.writeByte(ltdelta.length); out.write(ltdelta, 0, ltdelta.length);
							out.writeInt(bl); out.writeInt(pb.length); out.write(pb, 0, pb.length);
						}
					}
				}
				else if (entropy_type == 2)
				{
					// ---- Slow Arithmetic (BigInteger) ----
					byte[][]   payloads = new byte[3][];
					int[]      n_segs   = new int[3];
					byte[][][] segs     = new byte[3][][];
					int[][][]  freqs    = new int[3][][];

					for (int i = 0; i < 3; i++)
					{
						payloads[i] = getPayload(i);
						int min_seg = 500 + pixel_segment * 500;
						n_segs[i]   = (pixel_segment >= 10) ? 1 : Math.max(1, payloads[i].length / min_seg);
						int seg_len = payloads[i].length / n_segs[i];
						int odd_len = seg_len + payloads[i].length % n_segs[i];
						segs[i]     = new byte[n_segs[i]][];
						freqs[i]    = new int[n_segs[i]][256];
						for (int m = 0; m < n_segs[i]; m++)
							segs[i][m] = new byte[m < n_segs[i]-1 ? seg_len : odd_len];
						int pos = 0;
						for (int m = 0; m < n_segs[i]; m++)
							for (int nn = 0; nn < segs[i][m].length; nn++)
							{
								segs[i][m][nn] = payloads[i][pos];
								int p = payloads[i][pos]; if (p < 0) p += 256;
								freqs[i][m][p]++; pos++;
							}
					}

					BigInteger[][][] offsets = new BigInteger[3][][];
					for (int i = 0; i < 3; i++) offsets[i] = new BigInteger[n_segs[i]][2];

					int total_segs = n_segs[0] + n_segs[1] + n_segs[2];
					System.out.println("Slow Arithmetic: starting " + total_segs + " segment(s)...");

					Thread[][][] enc_threads = new Thread[3][][];
					for (int i = 0; i < 3; i++)
					{
						enc_threads[i] = new Thread[1][n_segs[i]];
						for (int m = 0; m < n_segs[i]; m++)
						{
							final BigInteger[] seg_off  = offsets[i][m];
							final byte[]       seg_data = segs[i][m];
							final int[]        seg_freq = freqs[i][m];
							final int ci = i, si = m;
							enc_threads[i][0][m] = new Thread(() ->
							{
								System.out.println("Slow Arithmetic: channel " + ci + " segment " + si + " (" + seg_data.length + " bytes)...");
								BigInteger[] r = ArithmeticMapper.getIntervalValue(seg_data, seg_freq);
								seg_off[0] = r[0]; seg_off[1] = r[1];
							});
							enc_threads[i][0][m].start();
						}
					}
					for (int i = 0; i < 3; i++)
						for (Thread t : enc_threads[i][0]) t.join();

					int[]    len_types = new int[3];
					byte[][] zip_freqs = new byte[3][];
					int[]    zip_lens  = new int[3];
					deflateFrequencies(n_segs, freqs, len_types, zip_freqs, zip_lens);

					for (int i = 0; i < 3; i++)
					{
						int j = channel_id[i];
						out.writeInt(channel_min[j]); out.writeInt(channel_init[j]);
						out.writeInt(channel_delta_min[j]); out.writeInt(channel_length[j]);
						out.writeInt(channel_compressed_length[j]); out.writeByte(channel_iterations[i]);
						if (delta_type >= 6 && delta_type != 10) writeMap(out, i);
						if (compress_type > 0) writeTable(out, (int[]) table_list.get(i));
						out.writeInt(n_segs[i]); out.writeInt(len_types[i]);
						out.writeInt(zip_lens[i]); out.write(zip_freqs[i], 0, zip_lens[i]);
						for (int k = 0; k < n_segs[i]; k++)
						{
							byte[] b0 = offsets[i][k][0].toByteArray(); out.writeInt(b0.length); out.write(b0, 0, b0.length);
							byte[] b1 = offsets[i][k][1].toByteArray(); out.writeInt(b1.length); out.write(b1, 0, b1.length);
						}
					}
				}
				else
				{
					// ---- Arithmetic (fast / renormalization) ----
					byte[][]   payloads = new byte[3][];
					int[]      n_segs   = new int[3];
					byte[][][] segs     = new byte[3][][];
					int[][][]  freqs    = new int[3][][];

					for (int i = 0; i < 3; i++)
					{
						payloads[i] = getPayload(i);
						int min_seg = 500 + pixel_segment * 500;
						n_segs[i]   = (pixel_segment >= 10) ? 1 : Math.max(1, payloads[i].length / min_seg);
						int seg_len = payloads[i].length / n_segs[i];
						int odd_len = seg_len + payloads[i].length % n_segs[i];
						segs[i]     = new byte[n_segs[i]][];
						freqs[i]    = new int[n_segs[i]][256];
						for (int m = 0; m < n_segs[i]; m++)
							segs[i][m] = new byte[m < n_segs[i]-1 ? seg_len : odd_len];
						int pos = 0;
						for (int m = 0; m < n_segs[i]; m++)
							for (int nn = 0; nn < segs[i][m].length; nn++)
							{
								segs[i][m][nn] = payloads[i][pos];
								int p = payloads[i][pos]; if (p < 0) p += 256;
								freqs[i][m][p]++; pos++;
							}
					}

					byte[][][] fast_enc = new byte[3][][];
					for (int i = 0; i < 3; i++) fast_enc[i] = new byte[n_segs[i]][];

					Thread[][][] fast_threads = new Thread[3][][];
					for (int i = 0; i < 3; i++)
					{
						fast_threads[i] = new Thread[1][n_segs[i]];
						for (int m = 0; m < n_segs[i]; m++)
						{
							final byte[][] fe = fast_enc[i];
							final int      fm = m;
							final byte[]   sd = segs[i][m];
							final int[]    sf = freqs[i][m];
							fast_threads[i][0][m] = new Thread(() -> fe[fm] = ArithmeticMapper.getIntervalValueFast(sd, sf));
							fast_threads[i][0][m].start();
						}
					}
					for (int i = 0; i < 3; i++)
						for (Thread t : fast_threads[i][0]) t.join();

					int[]    len_types = new int[3];
					byte[][] zip_freqs = new byte[3][];
					int[]    zip_lens  = new int[3];
					deflateFrequencies(n_segs, freqs, len_types, zip_freqs, zip_lens);

					for (int i = 0; i < 3; i++)
					{
						int j = channel_id[i];
						out.writeInt(channel_min[j]); out.writeInt(channel_init[j]);
						out.writeInt(channel_delta_min[j]); out.writeInt(channel_length[j]);
						out.writeInt(channel_compressed_length[j]); out.writeByte(channel_iterations[i]);
						if (delta_type >= 6 && delta_type != 10) writeMap(out, i);
						if (compress_type > 0) writeTable(out, (int[]) table_list.get(i));
						out.writeInt(n_segs[i]); out.writeInt(len_types[i]);
						out.writeInt(zip_lens[i]); out.write(zip_freqs[i], 0, zip_lens[i]);
						for (int k = 0; k < n_segs[i]; k++)
						{
							byte[] enc = fast_enc[i][k];
							out.writeInt(enc.length);
							out.write(enc, 0, enc.length);
						}
					}
				}

				out.flush(); out.close();

				File   saved = new File("foo");
				double rate  = (double) saved.length() / (image_xdim * image_ydim * 3);
				System.out.println("File compression rate:   " + String.format("%.4f", file_compression_rate));
				System.out.println("Delta type:              " + delta_type_string[delta_type]);
				System.out.println("Entropy type:            " + new String[]{"LZ77","Huffman","Slow Arithmetic","Arithmetic"}[entropy_type]);
				System.out.println("Output compression rate: " + String.format("%.4f", rate));
				System.out.println();
			}
			catch (Exception e) { System.out.println("SaveHandler exception: " + e); e.printStackTrace(); }
		}

		private void deflateFrequencies(int[] n_segs, int[][][] freqs,
		                                int[] len_types, byte[][] zip_freqs, int[] zip_lens)
		    throws InterruptedException
		{
			Thread[] dfl = new Thread[3];
			for (int i = 0; i < 3; i++)
			{
				final int fi = i;
				final int[][] fr = freqs[i];
				final int    ns  = n_segs[i];
				dfl[i] = new Thread(() ->
				{
					int fmax = 0;
					for (int[] row : fr) for (int v : row) if (v > fmax) fmax = v;
					int lt  = (fmax < Byte.MAX_VALUE*2+2) ? 0 : (fmax < Short.MAX_VALUE*2+2) ? 1 : 2;
					len_types[fi] = lt;
					int bpe  = (lt == 0) ? 1 : (lt == 1) ? 2 : 4;
					byte[] fb = new byte[ns * 256 * bpe];
					for (int k = 0; k < ns; k++)
						for (int m = 0; m < 256; m++)
						{ int v = fr[k][m]; int base = k*256*bpe + m*bpe; for (int b = 0; b < bpe; b++) fb[base+b] = (byte)(v>>(8*b)); }
					Deflater def = new Deflater(Deflater.BEST_COMPRESSION);
					byte[] zf = new byte[fb.length]; def.setInput(fb); def.finish();
					int zl = def.deflate(zf); def.end();
					zip_freqs[fi] = zf;
					zip_lens[fi]  = zl;
				});
				dfl[i].start();
			}
			for (Thread t : dfl) t.join();
		}

		private byte[] getPayload(int i)
		{
			return compress_type == 0 ? (byte[]) delta_list.get(i) : (byte[]) string_list.get(i);
		}
	}
}
