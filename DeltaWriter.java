import java.awt.*;
import java.awt.image.*;
import java.io.*;
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
	JMenuItem     apply_item;

	// ---- Compression parameters ---------------------------------------------
	int pixel_quant   = 4;
	int pixel_shift   = 3;
	int pixel_segment = 0;   // controls arithmetic segment granularity
	int correction    = 0;
	int min_set_id    = 0;
	int delta_type    = 5;
	int compress_type = 1;   // 0=Integer, 1=String, 2=String*
	int entropy_type  = 0;   // 0=LZ77, 1=Huffman, 3=Arithmetic

	// ---- Zoom ---------------------------------------------------------------
	double zoom_scale = 1.0;
	double fit_scale  = 1.0;
	static final double ZOOM_FACTOR = 1.25;
	static final double ZOOM_MIN    = 0.05;
	static final double ZOOM_MAX    = 32.0;

	// ---- Channel stats -------------------------------------------------------
	int[]  set_sum, channel_sum;
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

		int[] channel_id      = DeltaMapper.getChannels(min_set_id);
		int[] total_delta_sum = new int[12];
		int   fm1_delta = 0, fm1_map = 0, fm2_delta = 0, fm2_map = 0;

		for (int i = 0; i < 3; i++)
		{
			int   j  = channel_id[i];
			int[] qc = quantized_channel_list.get(j);
			ArrayList<int[]> res;

			for (int t = 0; t < 5; t++)
				total_delta_sum[t] += (int) Math.floor(CodeMapper.getShannonLimit(DeltaMapper.getFrequency(qc, new_xdim, new_ydim, t)));

			total_delta_sum[5] += (int) Math.floor(CodeMapper.getShannonLimit(DeltaMapper.getAdaptiveFrequency(qc, new_xdim, new_ydim)));

			res = DeltaMapper.getMedScanlineFrequency(qc, new_xdim, new_ydim);
			total_delta_sum[6] += (int) Math.floor(CodeMapper.getShannonLimit(res.get(0))) + res.get(1).length / 4;

			res = DeltaMapper.getScanline2Frequency(qc, new_xdim, new_ydim);
			total_delta_sum[7] += (int) Math.floor(CodeMapper.getShannonLimit(res.get(0))) + res.get(1).length / 4;

			res = DeltaMapper.getMixedDeltas4Frequency(qc, new_xdim, new_ydim);
			total_delta_sum[8] += (int) Math.floor(CodeMapper.getShannonLimit(res.get(0))) + res.get(1).length / 4;

			ArrayList<int[]> res16r = DeltaMapper.getMixedDeltas16Frequency(qc, new_xdim, new_ydim);
			int sl4d = (int) Math.floor(CodeMapper.getShannonLimit(res16r.get(0)));
			int sl4m = (int) Math.floor(CodeMapper.getShannonLimit(res16r.get(1)));
			total_delta_sum[9] += sl4d + sl4m;

			ArrayList<int[]> res8  = DeltaMapper.getIdealFrequency8(qc, new_xdim, new_ydim);
			int fm1d = (int) Math.floor(CodeMapper.getShannonLimit(res8.get(0)));
			int fm1m = (int) Math.floor(CodeMapper.getShannonLimit(res8.get(1)));
			total_delta_sum[10] += fm1d + fm1m;
			fm1_delta           += fm1d;
			fm1_map             += fm1m;

			ArrayList<int[]> res16 = DeltaMapper.getIdealFrequency16(qc, new_xdim, new_ydim);
			int fm2d = (int) Math.floor(CodeMapper.getShannonLimit(res16.get(0)));
			int fm2m = (int) Math.floor(CodeMapper.getShannonLimit(res16.get(1)));
			total_delta_sum[11] += fm2d + fm2m;
			fm2_delta           += fm2d;
			fm2_map             += fm2m;
		}

		min_sum = total_delta_sum[0]; min_idx = 0;
		for (int i = 1; i < 12; i++) if (total_delta_sum[i] < min_sum) { min_sum = total_delta_sum[i]; min_idx = i; }
		delta_type = min_idx;
		System.out.println("Best delta type is " + delta_type_string[delta_type]);

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
				else if (delta_type == 10) tr = DeltaMapper.getIdealDeltasFromValues8(qc_test, new_xdim, new_ydim);
				else                       tr = DeltaMapper.getIdealDeltasFromValues16(qc_test, new_xdim, new_ydim);

				int[] td = (int[]) tr.get(1);
				str_bits_total      += StringMapper.getBitlength((byte[]) StringMapper.getStringList(td.clone(), false).get(3));
				str_star_bits_total += StringMapper.getBitlength((byte[]) StringMapper.getStringList(td.clone(), true ).get(3));
			}
			compress_type = (str_star_bits_total < str_bits_total) ? 2 : 1;
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

			channel_string    = new String[]{"blue","green","red","blue-green","red-green","red-blue"};

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
				"red, red-green, red-blue."};

			delta_type_string = new String[]{
				"horizontal","vertical","average","med","directional",
				"adaptive","scanline (1)","scanline (2)","scanline (3)","scanline (4)","frame map","frame map (2)"};

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

				apply_item = new JMenuItem("Apply");
				apply_item.addActionListener(new ApplyHandler());
				file_menu.add(apply_item);

				JMenuItem reload_item = new JMenuItem("Reload");
				reload_item.addActionListener(e ->
				{
					updateDisplayImage();
					image_canvas.setPreferredSize(new Dimension((int)(image_xdim*zoom_scale),(int)(image_ydim*zoom_scale)));
					image_canvas.revalidate(); image_canvas.repaint();
					System.out.println("Reloaded original image.");
				});
				file_menu.add(reload_item);
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
				quant_menu.add(makeSliderDialog(frame, "Pixel Resolution",  0, 10, pixel_quant,  v -> { pixel_quant  = v; apply_item.doClick(); }));
				quant_menu.add(makeSliderDialog(frame, "Color Resolution",  0,  7, pixel_shift, v -> { pixel_shift = v; apply_item.doClick(); }));
				quant_menu.add(makeSliderDialog(frame, "Segment Length",    0, 10, pixel_segment, v -> pixel_segment = v));
				quant_menu.add(makeSliderDialog(frame, "Error Correction",  0, 10, correction,   v -> { correction   = v; apply_item.doClick(); }));

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
						if (compress_type != idx) { compress_type = idx; apply_item.doClick(); }
					});
				}

				// Delta menu
				JMenu delta_menu = new JMenu("Delta");
				String[] dnames = {"H","V","Average","Med","Directional","Adaptive","Scanline 1","Scanline 2","Scanline 3","Scanline 4","Map","Map (2)"};
				delta_button = new JRadioButtonMenuItem[12];
				ButtonGroup dg = new ButtonGroup();
				for (int i = 0; i < 12; i++)
				{
					delta_button[i] = new JRadioButtonMenuItem(dnames[i]);
					dg.add(delta_button[i]);
					delta_menu.add(delta_button[i]);
					final int idx = i;
					delta_button[i].addActionListener(e -> { if (delta_type != idx) { delta_type = idx; apply_item.doClick(); }});
				}
				delta_button[delta_type].setSelected(true);

				// Entropy menu — Huffman(1), LZ77(0), Arithmetic(3)
				JMenu entropy_menu = new JMenu("Entropy");
				entropy_button = new JRadioButtonMenuItem[3];
				entropy_button[0] = new JRadioButtonMenuItem("Huffman");
				entropy_button[1] = new JRadioButtonMenuItem("LZ77");
				entropy_button[2] = new JRadioButtonMenuItem("Arithmetic");
				ButtonGroup eg = new ButtonGroup();
				for (int i = 0; i < 3; i++) { eg.add(entropy_button[i]); entropy_menu.add(entropy_button[i]); }
				int[] entropy_map = {1, 0, 3};   // button index → entropy_type value
				entropy_button[0].setSelected(entropy_type == 1);
				entropy_button[1].setSelected(entropy_type == 0);
				entropy_button[2].setSelected(entropy_type == 3);
				for (int i = 0; i < 3; i++)
				{
					final int et = entropy_map[i];
					entropy_button[i].addActionListener(e -> { if (entropy_type != et) entropy_type = et; });
				}

				menu_bar.add(file_menu);
				menu_bar.add(view_menu);
				menu_bar.add(datatype_menu);
				menu_bar.add(delta_menu);
				menu_bar.add(entropy_menu);
				menu_bar.add(quant_menu);
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
		JMenuItem item   = new JMenuItem(title);
		JDialog   dialog = new JDialog(parent, title);
		JSlider   slider = new JSlider(lo, hi, init);
		JTextField field = new JTextField(3);
		field.setText(" " + init + " ");
		slider.addChangeListener(e ->
		{
			int v = slider.getValue();
			field.setText(" " + v + " ");
			if (!slider.getValueIsAdjusting()) onChange.accept(v);
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

		apply_item.setEnabled(false);
		new javax.swing.SwingWorker<Void, Void>()
		{
			@Override protected Void doInBackground() { init(); return null; }
			@Override protected void done()
			{
				delta_button[delta_type].setSelected(true);
				compress_button[compress_type].setSelected(true);
				apply_item.setEnabled(true);
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
	}

	private void writeMap(DataOutputStream out, int i) throws IOException
	{
		byte[] map = (byte[]) map_list.get(i);
		if (delta_type >= 6 && delta_type <= 8)
		{
			int    pm_len = (map.length + 3) / 4;
			byte[] pm     = new byte[pm_len];
			for (int q = 0; q < map.length; q++)
				pm[q >> 2] |= (map[q] & 0x3) << ((q & 3) << 1);
			out.writeInt(map.length); out.writeInt(pm_len); out.write(pm, 0, pm_len);
		}
		else if (delta_type == 9)
		{
			int    pm_len = (map.length + 1) / 2;
			byte[] pm     = new byte[pm_len];
			for (int q = 0; q < map.length; q++) pm[q >> 1] |= (map[q] & 0xF) << ((q & 1) << 2);
			out.writeInt(map.length); out.writeInt(pm_len); out.write(pm, 0, pm_len);
		}
		else if (delta_type == 10)
		{
			int[]  bit_count = new int[1];
			byte[] encoded   = DeltaMapper.encodeMapHuffman(map, 8, bit_count);
			out.writeInt(map.length); out.writeInt(bit_count[0]); out.writeInt(encoded.length); out.write(encoded, 0, encoded.length);
		}
		else if (delta_type == 11)
		{
			int[]  bit_count = new int[1];
			byte[] encoded   = DeltaMapper.encodeMapHuffman(map, 16, bit_count);
			out.writeInt(map.length); out.writeInt(bit_count[0]); out.writeInt(encoded.length); out.write(encoded, 0, encoded.length);
		}
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
			try
			{
				applyImpl();
			}
			catch (Exception e)
			{
				System.out.println("ApplyHandler exception: " + e);
				e.printStackTrace();
			}
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

			computeSetSums();

			int min_sum = Integer.MAX_VALUE, min_idx = 0;
			for (int i = 0; i < 10; i++) if (set_sum[i] < min_sum) { min_sum = set_sum[i]; min_idx = i; }
			min_set_id = min_idx;
			file_compression_rate = (double) file_length / (image_xdim * image_ydim * 3);
			System.out.println("Best channel set is " + set_string[min_set_id]);

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
				else if (delta_type == 10) { result = DeltaMapper.getIdealDeltasFromValues8(qc, new_xdim, new_ydim);    map_list.add(result.get(2)); }
				else if (delta_type == 11) { result = DeltaMapper.getIdealDeltasFromValues16(qc, new_xdim, new_ydim);  map_list.add(result.get(2)); }

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
					delta       = StringMapper.unpackStrings(str, tbl, new_xdim * new_ydim, channel_compressed_length[j]);
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
				else if (delta_type == 10) ch = DeltaMapper.getValuesFromIdealDeltas8(delta, new_xdim, new_ydim, channel_init[j], (byte[]) map_list.get(i));
				else if (delta_type == 11) ch = DeltaMapper.getValuesFromIdealDeltas16(delta, new_xdim, new_ydim, channel_init[j], (byte[]) map_list.get(i));

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
			System.out.println("Loaded quantized image.");
			System.out.println();
			initialized = true;
		}
	}

	// =========================================================================
	// =========================================================================
	// SaveHandler — Huffman (1), LZ77 (0), Arithmetic (3)
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

						if (delta_type >= 6) writeMap(out, i);
						if (compress_type > 0)
							writeTable(out, (int[]) table_list.get(i));

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
							// Huffman — code over unsigned payload byte values
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
						if (delta_type >= 6) writeMap(out, i);
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
				System.out.println("Entropy type:            " + new String[]{"LZ77","Huffman","","Arithmetic"}[entropy_type]);
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
