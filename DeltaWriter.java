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
	int pixel_segment = 0;
	int correction    = 0;
	int min_set_id    = 0;
	int delta_type    = 5;
	int compress_type = 1;   // 0=Integer, 1=String, 2=String*
	int entropy_type  = 0;
	int smooth_level  = 0;
	int smooth2_level = 0;
	byte scanline5_variant = 0;

	JSlider smooth_slider, smooth2_slider, pquant_slider, pshift_slider, corr_slider, segment_slider;

	double zoom_scale = 1.0;
	double fit_scale  = 1.0;
	static final double ZOOM_FACTOR = 1.25;
	static final double ZOOM_MIN    = 0.05;
	static final double ZOOM_MAX    = 32.0;

	// Detected once at startup by applyHiDpiFontScaleIfNeeded(), before any
	// window is created. 1.0 when no override was applied (the expected
	// case on Windows and correctly-configured Linux sessions). Used both
	// to scale the fixed pixel allowances that budget space for window
	// chrome (menu bar, borders), and to raise the "100%"/native zoom
	// ceiling so image content displays at the same physical size it
	// would on a standard-DPI display, rather than shrunk to raw source
	// pixels.
	static double hidpi_scale = 1.0;

	int[]  set_sum, channel_sum;
	int[]  channel_init, channel_min, channel_delta_min;
	int[]  channel_length, channel_compressed_length;
	byte[] channel_iterations;

	String[] set_string, delta_type_string, channel_string;
	JRadioButtonMenuItem[] delta_button;
	JRadioButton[] compress_button;
	JRadioButton[] int_radio_btns;
	JRadioButtonMenuItem[] entropy_button;

	ArrayList<Object> channel_list, table_list, string_list, map_list, delta_list;

	long   file_length;
	double file_compression_rate;
	boolean initialized = false;

	static int openWindowCount  = 0;
	static int nextWindowOffset = 0;

	public static void main(String[] args)
	{
		applyHiDpiFontScaleIfNeeded();
		if (args.length == 1) { new DeltaWriter(args[0]); }
		else
		{
			FileDialog fd = new FileDialog((java.awt.Frame) null, "Open Image", FileDialog.LOAD);
			fd.setVisible(true);
			if (fd.getFile() != null) new DeltaWriter(new File(fd.getDirectory(), fd.getFile()).getPath());
			else System.exit(0);
		}
	}

	// =========================================================================
	// HiDPI font-scale fallback.
	//
	// Windows has reliably detected and applied per-monitor HiDPI scaling
	// since JDK 9 (JEP 263) -- GraphicsConfiguration's own transform already
	// reflects the OS scale factor there, so detectMissingUiScale() below
	// returns 1.0 immediately and applyHiDpiFontScaleIfNeeded() is a no-op:
	// this code path is never active on Windows, and normal platform
	// behavior is completely undisturbed. The problem this exists for is
	// Linux/X11 specifically, where DPI reporting through the display
	// server is much less consistent and Swing/AWT frequently fails to
	// notice a HiDPI display at all, rendering everything at a fixed
	// assumed ~96 DPI regardless of the desktop's actual configured scale
	// (the same class of issue as unpatched Notepad++ on a HiDPI Windows
	// display, just the Linux-side equivalent).
	// =========================================================================

	/**
	 * Returns the UI scale factor that should be applied on top of Swing's
	 * own automatic scaling, or 1.0 if no override is needed.
	 *
	 * Deliberately conservative: only returns a scale factor when there is
	 * clear evidence Java's own automatic detection missed a genuinely
	 * HiDPI display, checking several independent signals in order of
	 * reliability rather than trusting any single one -- Toolkit.
	 * getScreenResolution() in particular is not trustworthy on its own:
	 * on at least one real Linux system it simply returns the JVM's
	 * hardcoded 96 DPI default rather than anything reflecting the actual
	 * desktop scale, so it's kept only as a last resort behind two more
	 * reliable, independent signals.
	 */
	private static double detectMissingUiScale()
	{
		try
		{
			GraphicsConfiguration gc = GraphicsEnvironment.getLocalGraphicsEnvironment()
				.getDefaultScreenDevice().getDefaultConfiguration();
			double current_scale = gc.getDefaultTransform().getScaleX();

			// If Java already reports a scale above 1.0, it has already
			// detected and is applying HiDPI scaling correctly (the
			// normal, reliable case on Windows, and on properly
			// configured Linux/X11 or Wayland sessions too) -- nothing
			// to do.
			if (current_scale > 1.01) return 1.0;

			// current_scale == 1.0 is ambiguous by itself: either this
			// genuinely is a standard ~96 DPI display, or (the Linux/X11
			// failure mode this method exists to catch) it's a HiDPI
			// display that Java's automatic per-monitor scaling never
			// picked up.

			// 1. GDK_SCALE: a direct integer scale factor GTK3+ itself
			// reads, set by GNOME and other GTK-based desktops when
			// integer HiDPI scaling is configured. When present this is
			// about as authoritative a signal as exists, short of Java's
			// own (already-checked) detection.
			String gdk_scale_str = System.getenv("GDK_SCALE");
			if (gdk_scale_str != null)
			{
				try
				{
					double gdk_scale = Double.parseDouble(gdk_scale_str.trim());
					if (gdk_scale >= 1.25) return gdk_scale;
				}
				catch (NumberFormatException nfe) { /* fall through to next signal */ }
			}

			// 2. Xft.dpi via XSettings, queried through AWT's desktop
			// property mechanism -- independent of, and often more
			// reliable than, Toolkit.getScreenResolution() (which
			// derives DPI from the X server's reported physical monitor
			// dimensions in millimetres, a value that's frequently wrong
			// or defaulted on real hardware). By convention this
			// property's value is DPI*1024, not DPI directly.
			Object xft_dpi_prop = Toolkit.getDefaultToolkit().getDesktopProperty("gnome.Xft/DPI");
			if (xft_dpi_prop instanceof Integer)
			{
				double xft_dpi           = ((Integer) xft_dpi_prop) / 1024.0;
				double xft_implied_scale = xft_dpi / 96.0;
				if (xft_implied_scale >= 1.25) return xft_implied_scale;
			}

			// 3. Toolkit.getScreenResolution(): kept as a last-resort
			// signal, since it can still be correct on systems other than
			// the one this fallback chain was extended for.
			int    dpi           = Toolkit.getDefaultToolkit().getScreenResolution();
			double implied_scale = dpi / 96.0;
			if (implied_scale >= 1.25) return implied_scale;

			return 1.0;
		}
		catch (Exception e)
		{
			// Fail safe: never let a detection problem break startup or
			// force an unwanted scale change.
			return 1.0;
		}
	}

	/**
	 * Scales every default Swing font by whatever detectMissingUiScale()
	 * determines is needed, or does nothing at all if it returns 1.0 (the
	 * expected outcome on Windows and on correctly-configured Linux
	 * sessions). Must run before any Swing component is constructed, since
	 * components read their fonts from these defaults at creation time.
	 *
	 * Also sets the static hidpi_scale field, used elsewhere to scale the
	 * fixed pixel allowances that budget space for window chrome (menu
	 * bar, borders) and to raise the "100%"/native zoom ceiling so image
	 * content is displayed at the same physical size it would be on a
	 * standard-DPI display, not shrunk to match raw source pixels.
	 *
	 * Writes through both UIManager.getLookAndFeelDefaults() (the current
	 * look-and-feel's own defaults table) and UIManager.put() (the
	 * top-level developer-override table, checked before the L&F's own
	 * defaults by every standard component) for robustness across JDK
	 * builds and look-and-feel implementations.
	 */
	private static void applyHiDpiFontScaleIfNeeded()
	{
		double scale = detectMissingUiScale();
		hidpi_scale  = scale;
		if (scale <= 1.01) return;

		UIDefaults defaults = UIManager.getLookAndFeelDefaults();
		for (Object key : new java.util.Vector<Object>(defaults.keySet()))
		{
			Object value = defaults.get(key);
			if (value instanceof Font)
			{
				Font  font     = (Font) value;
				float new_size = (float) (font.getSize() * scale);
				Font  scaled   = font.deriveFont(new_size);
				defaults.put(key, scaled);
				UIManager.put(key, scaled);
			}
		}
	}

	public void init()
	{
		ArrayList<int[]> quantized_channel_list = new ArrayList<int[]>();
		int new_xdim = image_xdim, new_ydim = image_ydim;
		if (pixel_quant != 0)
		{
			double factor = pixel_quant / 10.0;
			new_xdim = image_xdim - (int)(factor * (image_xdim / 2 - 2));
			new_ydim = image_ydim - (int)(factor * (image_ydim / 2 - 2));
		}
		for (int i = 0; i < 3; i++)
		{
			int[] channel = (int[]) channel_list.get(i);

			if (pixel_quant == 0) quantized_channel_list.add(quantizeChannel(channel, pixel_shift));
			else
			{
				int[] resized = ResizeMapper.resize(channel, image_xdim, new_xdim, new_ydim);
				quantized_channel_list.add(quantizeChannel(resized, pixel_shift));
			}
		}
		int[] qb = quantized_channel_list.get(0), qg = quantized_channel_list.get(1), qr = quantized_channel_list.get(2);
		quantized_channel_list.add(DeltaMapper.getDifference(qb, qg));
		quantized_channel_list.add(DeltaMapper.getDifference(qr, qg));
		quantized_channel_list.add(DeltaMapper.getDifference(qr, qb));
		for (int i = 0; i < 6; i++)
		{
			int[] qc = quantized_channel_list.get(i); int min = 256;
			for (int v : qc) if (v < min) min = v;
			channel_min[i] = min;
			if (i > 2) for (int k = 0; k < qc.length; k++) qc[k] -= min;
			channel_init[i] = qc[0];
			quantized_channel_list.set(i, qc);
			channel_sum[i] = (int) Math.floor(CodeMapper.getShannonLimit(DeltaMapper.getIdealFrequency(qc, new_xdim, new_ydim)));
		}
		computeSetSums();
		int min_sum = Integer.MAX_VALUE, min_idx = 0;
		for (int i = 0; i < 10; i++) if (set_sum[i] < min_sum) { min_sum = set_sum[i]; min_idx = i; }
		min_set_id = min_idx;
		printChannelSetRanking();
		int[] channel_id = DeltaMapper.getChannels(min_set_id);

		// Select best delta type. Parallelized across the 3 channels (each
		// channel's full 13-delta-type analysis is completely independent
		// of the others), matching the same per-channel-thread pattern
		// already used elsewhere in this file (ApplyHandler's smoothing/
		// encode/decode passes, the segmented arithmetic-encoding threads).
		// Each thread writes into its own slot of a per-channel results
		// array to avoid any shared mutable state between threads; the
		// three channels' contributions are summed after all three join.
		int[]     delta_bits        = new int[13];
		int[]     map_bits          = new int[13];
		boolean[] delta_compressed  = new boolean[13];
		boolean[] map_compressed    = new boolean[13];

		int[][]     per_channel_delta_bits       = new int[3][13];
		int[][]     per_channel_map_bits         = new int[3][13];
		boolean[][] per_channel_delta_compressed = new boolean[3][13];
		boolean[][] per_channel_map_compressed   = new boolean[3][13];

		final int f_new_xdim = new_xdim;
		final int f_new_ydim = new_ydim;

		Thread[] delta_type_threads = new Thread[3];
		for (int i = 0; i < 3; i++)
		{
			final int fi = i;
			delta_type_threads[i] = new Thread(() ->
			{
				int[] qc = quantized_channel_list.get(channel_id[fi]);
				int[]     ch_delta_bits       = per_channel_delta_bits[fi];
				int[]     ch_map_bits         = per_channel_map_bits[fi];
				boolean[] ch_delta_compressed = per_channel_delta_compressed[fi];
				boolean[] ch_map_compressed   = per_channel_map_compressed[fi];

				ArrayList[] encs = {
					DeltaMapper.getHorizontalDeltasFromValues(qc, f_new_xdim, f_new_ydim),
					DeltaMapper.getVerticalDeltasFromValues(qc, f_new_xdim, f_new_ydim),
					DeltaMapper.getAverageDeltasFromValues(qc, f_new_xdim, f_new_ydim),
					DeltaMapper.getMedDeltasFromValues(qc, f_new_xdim, f_new_ydim),
					DeltaMapper.getDirectionalDeltasFromValues(qc, f_new_xdim, f_new_ydim),
					DeltaMapper.getAdaptiveDeltasFromValues(qc, f_new_xdim, f_new_ydim),
				};
				for (int t = 0; t < 6; t++)
				{
					byte[] compressed = packAndCompress((int[]) encs[t].get(1));
					ch_delta_bits[t] += StringMapper.getBitlength(compressed);
					if ((StringMapper.getIterations(compressed) & 15) > 0) ch_delta_compressed[t] = true;
				}
				ArrayList[] encs2 = {
					DeltaMapper.getMixedDeltasFromValues(qc, f_new_xdim, f_new_ydim),                              // type 6  (scanline 1)
					DeltaMapper.getMixedDeltasFromValues2(qc, f_new_xdim, f_new_ydim),                             // type 7  (scanline 2)
					DeltaMapper.getMixedDeltasFromValues4(qc, f_new_xdim, f_new_ydim),                             // type 8  (scanline 3)
					DeltaMapper.getMixedDeltasFromValues16Rows(qc, f_new_xdim, f_new_ydim),                        // type 9  (scanline 4)
					DeltaMapper.getMixedDeltasFromValues8Rows(qc, f_new_xdim, f_new_ydim, scanline5_variant),      // type 10 (scanline 5)
					DeltaMapper.getIdealDeltasFromValues8(qc, f_new_xdim, f_new_ydim),                             // type 11 (frame map 1)
					DeltaMapper.getIdealDeltasFromValues16(qc, f_new_xdim, f_new_ydim),                            // type 12 (frame map 2)
				};
				for (int t = 0; t < 7; t++)
				{
					int    type_idx = t + 6;
					int[]  delta     = (int[])  encs2[t].get(1);
					byte[] map       = (byte[]) encs2[t].get(2);

					byte[] delta_compressed_bytes = packAndCompress(delta);
					ch_delta_bits[type_idx] += StringMapper.getBitlength(delta_compressed_bytes);
					if ((StringMapper.getIterations(delta_compressed_bytes) & 15) > 0) ch_delta_compressed[type_idx] = true;

					int[] map_int = new int[map.length];
					for (int k = 0; k < map.length; k++) map_int[k] = map[k] & 0xFF;
					byte[] map_compressed_bytes = packAndCompress(map_int);
					ch_map_bits[type_idx] += StringMapper.getBitlength(map_compressed_bytes);
					if ((StringMapper.getIterations(map_compressed_bytes) & 15) > 0) ch_map_compressed[type_idx] = true;
				}
			});
			delta_type_threads[i].start();
		}
		try { for (Thread t : delta_type_threads) t.join(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }

		for (int i = 0; i < 3; i++)
		{
			for (int t = 0; t < 13; t++)
			{
				delta_bits[t]       += per_channel_delta_bits[i][t];
				map_bits[t]         += per_channel_map_bits[i][t];
				delta_compressed[t] |= per_channel_delta_compressed[i][t];
				map_compressed[t]   |= per_channel_map_compressed[i][t];
			}
		}

		int[] total_delta_sum = new int[13];
		for (int t = 0; t < 13; t++) total_delta_sum[t] = delta_bits[t] + map_bits[t];
		min_sum = total_delta_sum[0]; min_idx = 0;
		for (int i = 1; i < 13; i++) if (total_delta_sum[i] < min_sum) { min_sum = total_delta_sum[i]; min_idx = i; }
		delta_type = min_idx;
		printDeltaTypeRanking(delta_bits, map_bits, delta_compressed, map_compressed, total_delta_sum);

		// Select compress_type
		{
			int str_bits_total = 0, str_star_bits_total = 0;
			for (int i = 0; i < 3; i++)
			{
				int[] qc_test = quantized_channel_list.get(channel_id[i]);
				ArrayList tr;
				if      (delta_type == 0)  tr = DeltaMapper.getHorizontalDeltasFromValues(qc_test, new_xdim, new_ydim);
				else if (delta_type == 1)  tr = DeltaMapper.getVerticalDeltasFromValues(qc_test, new_xdim, new_ydim);
				else if (delta_type == 2)  tr = DeltaMapper.getAverageDeltasFromValues(qc_test, new_xdim, new_ydim);
				else if (delta_type == 3)  tr = DeltaMapper.getMedDeltasFromValues(qc_test, new_xdim, new_ydim);
				else if (delta_type == 4)  tr = DeltaMapper.getDirectionalDeltasFromValues(qc_test, new_xdim, new_ydim);
				else if (delta_type == 5)  tr = DeltaMapper.getAdaptiveDeltasFromValues(qc_test, new_xdim, new_ydim);
				else if (delta_type == 6)  tr = DeltaMapper.getMixedDeltasFromValues(qc_test, new_xdim, new_ydim);
				else if (delta_type == 7)  tr = DeltaMapper.getMixedDeltasFromValues2(qc_test, new_xdim, new_ydim);
				else if (delta_type == 8)  tr = DeltaMapper.getMixedDeltasFromValues4(qc_test, new_xdim, new_ydim);
				else if (delta_type == 9)  tr = DeltaMapper.getMixedDeltasFromValues16Rows(qc_test, new_xdim, new_ydim);
				else if (delta_type == 10) tr = DeltaMapper.getMixedDeltasFromValues8Rows(qc_test, new_xdim, new_ydim, scanline5_variant);
				else if (delta_type == 11) tr = DeltaMapper.getIdealDeltasFromValues8(qc_test, new_xdim, new_ydim);
				else                       tr = DeltaMapper.getIdealDeltasFromValues16(qc_test, new_xdim, new_ydim);
				int[] td = (int[]) tr.get(1);
				str_bits_total      += StringMapper.getBitlength((byte[]) StringMapper.getStringList(td.clone(), false).get(3));
				str_star_bits_total += StringMapper.getBitlength((byte[]) StringMapper.getStringList(td.clone(), true ).get(3));
			}
			compress_type = (str_star_bits_total < str_bits_total) ? 2 : 1;
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
		}
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

			channel_list = new ArrayList<Object>(); table_list = new ArrayList<Object>();
			string_list  = new ArrayList<Object>(); map_list   = new ArrayList<Object>(); delta_list = new ArrayList<Object>();

			channel_string    = new String[]{"blue","green","red","blue-green","red-green","red-blue"};
			set_sum    = new int[10];
			set_string = new String[]{"blue, green, red","blue, red, red-green","blue, red, blue-green","blue, blue-green, red-green","blue, blue-green, red-blue","green, red, blue-green","red, blue-green, red-green","green, blue-green, red-green","green, red-green, red-blue","red, red-green, red-blue"};
			delta_type_string = new String[]{"horizontal","vertical","average","med","directional","adaptive","scanline (1)","scanline (2)","scanline (3)","scanline (4)","scanline (5)","frame map (1)","frame map (2)"};
			channel_init = new int[6]; channel_min = new int[6]; channel_delta_min = new int[6];
			channel_sum  = new int[6]; channel_length = new int[6]; channel_compressed_length = new int[6];
			channel_iterations = new byte[3];

			if (raster_type == BufferedImage.TYPE_3BYTE_BGR)
			{
				pixel = new int[image_xdim * image_ydim];
				PixelGrabber pg = new PixelGrabber(original_image, 0, 0, image_xdim, image_ydim, pixel, 0, image_xdim);
				try { pg.grabPixels(); } catch (InterruptedException e) { e.printStackTrace(); }
				int[] blue = new int[image_xdim*image_ydim], green = new int[image_xdim*image_ydim], red = new int[image_xdim*image_ydim];
				for (int i = 0; i < pixel.length; i++) { blue[i]=(pixel[i]>>16)&0xff; green[i]=(pixel[i]>>8)&0xff; red[i]=pixel[i]&0xff; }
				channel_list.add(blue); channel_list.add(green); channel_list.add(red);
				working_image = new BufferedImage(image_xdim, image_ydim, BufferedImage.TYPE_INT_RGB);
				for (int i=0;i<image_xdim;i++) for (int j=0;j<image_ydim;j++) working_image.setRGB(i,j,pixel[j*image_xdim+i]);

				Dimension screen = Toolkit.getDefaultToolkit().getScreenSize();
				screen_xdim = (int)screen.getWidth(); screen_ydim = (int)screen.getHeight();
				int mw=(int)(screen_xdim*0.70)-(int)(40*hidpi_scale), mh=(int)(screen_ydim*0.70)-(int)(80*hidpi_scale);
				fit_scale = Math.min(hidpi_scale, Math.min((double)mw/image_xdim, (double)mh/image_ydim));
				zoom_scale = fit_scale;

				image_canvas = new ImageCanvas();
				scroll_pane = new JScrollPane(image_canvas, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
				scroll_pane.getVerticalScrollBar().setUnitIncrement(16); scroll_pane.getHorizontalScrollBar().setUnitIncrement(16);
				scroll_pane.addMouseWheelListener(e -> {
					if (e.isControlDown()) {
						JViewport vp=scroll_pane.getViewport(); Point vpos=vp.getViewPosition(); Point mpt=e.getPoint();
						int mcx=mpt.x+vpos.x, mcy=mpt.y+vpos.y; double old=zoom_scale;
						zoom_scale=(e.getWheelRotation()<0)?Math.min(ZOOM_MAX,zoom_scale*ZOOM_FACTOR):Math.max(ZOOM_MIN,zoom_scale/ZOOM_FACTOR);
						if(zoom_scale==old)return; updateDisplayImage();
						image_canvas.setPreferredSize(new Dimension((int)(image_xdim*zoom_scale),(int)(image_ydim*zoom_scale)));
						image_canvas.revalidate(); image_canvas.repaint();
						double r=zoom_scale/old; vp.setViewPosition(new Point(Math.max(0,(int)(mcx*r)-mpt.x),Math.max(0,(int)(mcy*r)-mpt.y)));
						updateTitle();
					} else scroll_pane.dispatchEvent(e);
				});

				frame = new JFrame("Delta Writer  " + filename);
				openWindowCount++;
				frame.addWindowListener(new WindowAdapter() { public void windowClosing(WindowEvent e) { frame.dispose(); if(--openWindowCount==0)System.exit(0); }});
				frame.getContentPane().add(scroll_pane, BorderLayout.CENTER);

				JMenuBar menu_bar = new JMenuBar();
				JMenu file_menu = new JMenu("File");
				JMenuItem open_item = new JMenuItem("Open...");
				open_item.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_O, InputEvent.CTRL_DOWN_MASK));
				open_item.addActionListener(e -> { FileDialog fd=new FileDialog(frame,"Open Image",FileDialog.LOAD); fd.setVisible(true); if(fd.getFile()!=null) new DeltaWriter(fd.getDirectory()+fd.getFile()); });
				file_menu.add(open_item); file_menu.addSeparator();
				JMenuItem reset_item = new JMenuItem("Reset");
				reset_item.addActionListener(e -> { smooth_level=0;smooth2_level=0;pixel_quant=0;pixel_shift=0;correction=0; if(smooth_slider!=null)smooth_slider.setValue(0); if(smooth2_slider!=null)smooth2_slider.setValue(0); if(pquant_slider!=null)pquant_slider.setValue(0); if(pshift_slider!=null)pshift_slider.setValue(0); if(corr_slider!=null)corr_slider.setValue(0); new ApplyHandler().actionPerformed(null); });
				file_menu.add(reset_item);
				JMenuItem save_item = new JMenuItem("Save"); save_item.addActionListener(new SaveHandler()); file_menu.add(save_item);

				JMenu view_menu = new JMenu("View");
				JMenuItem zi=new JMenuItem("Zoom In"); zi.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_EQUALS,InputEvent.CTRL_DOWN_MASK)); zi.addActionListener(e->zoomBy(ZOOM_FACTOR)); view_menu.add(zi);
				JMenuItem zo=new JMenuItem("Zoom Out"); zo.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_MINUS,InputEvent.CTRL_DOWN_MASK)); zo.addActionListener(e->zoomBy(1.0/ZOOM_FACTOR)); view_menu.add(zo);
				JMenuItem zf=new JMenuItem("Fit"); zf.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_0,InputEvent.CTRL_DOWN_MASK)); zf.addActionListener(e->{ Dimension vps=scroll_pane.getViewport().getSize(); zoom_scale=Math.min((double)vps.width/image_xdim,(double)vps.height/image_ydim); updateDisplayImage(); image_canvas.setPreferredSize(new Dimension((int)(image_xdim*zoom_scale),(int)(image_ydim*zoom_scale))); image_canvas.revalidate(); image_canvas.repaint(); updateTitle(); }); view_menu.add(zf);
				JMenuItem za=new JMenuItem("100%"); za.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_1,InputEvent.CTRL_DOWN_MASK)); za.addActionListener(e->{ zoom_scale=hidpi_scale; updateDisplayImage(); image_canvas.setPreferredSize(new Dimension((int)(image_xdim*zoom_scale),(int)(image_ydim*zoom_scale))); image_canvas.revalidate(); image_canvas.repaint(); updateTitle(); }); view_menu.add(za);

				JMenu quant_menu = new JMenu("Quantization");
				JSlider[] ss = new JSlider[1];
				quant_menu.add(makeSliderDialog(frame,"Smooth",0,10,smooth_level,v->{smooth_level=v;new ApplyHandler().actionPerformed(null);},ss)); smooth_slider=ss[0];
				quant_menu.add(makeSliderDialog(frame,"Smooth2",0,10,smooth2_level,v->{smooth2_level=v;new ApplyHandler().actionPerformed(null);},ss)); smooth2_slider=ss[0];
				quant_menu.add(makeSliderDialog(frame,"Pixel Resolution",0,10,pixel_quant,v->{pixel_quant=v;new ApplyHandler().actionPerformed(null);},ss)); pquant_slider=ss[0];
				quant_menu.add(makeSliderDialog(frame,"Color Resolution",0,7,pixel_shift,v->{pixel_shift=v;new ApplyHandler().actionPerformed(null);},ss)); pshift_slider=ss[0];
				quant_menu.add(makeSliderDialog(frame,"Error Correction",0,10,correction,v->{correction=v;new ApplyHandler().actionPerformed(null);},ss)); corr_slider=ss[0];

				JMenu datatype_menu = new JMenu("Datatype");
				ButtonGroup cg=new ButtonGroup();
				JRadioButton int_a=new JRadioButton("Integer"),str_a=new JRadioButton("String");
				JRadioButton int_b=new JRadioButton("Integer"),str_b=new JRadioButton("String");
				cg.add(int_a);cg.add(str_a);cg.add(int_b);cg.add(str_b);
				compress_button=new JRadioButton[]{int_a,str_a}; int_radio_btns=new JRadioButton[]{int_a,int_b};
				(compress_type==0?int_a:str_a).setSelected(true);
				int_a.addActionListener(e->{if(compress_type!=0){compress_type=0;new ApplyHandler().actionPerformed(null);}});
				int_b.addActionListener(e->{if(compress_type!=0){compress_type=0;new ApplyHandler().actionPerformed(null);}});
				str_a.addActionListener(e->{if(compress_type==0){compress_type=1;new ApplyHandler().actionPerformed(null);}});
				str_b.addActionListener(e->{if(compress_type==0){compress_type=1;new ApplyHandler().actionPerformed(null);}});
				{JDialog dlg=new JDialog(frame,"Integer");JPanel p=new JPanel(new java.awt.GridLayout(2,1,4,4));p.add(int_a);p.add(str_a);dlg.add(p);JMenuItem mi=new JMenuItem("Integer");mi.addActionListener(e->{Point loc=frame.getLocation();dlg.setLocation((int)loc.getX(),(int)loc.getY()-80);dlg.pack();dlg.setVisible(true);});datatype_menu.add(mi);}
				{JDialog dlg=new JDialog(frame,"String");JPanel rp=new JPanel();rp.add(int_b);rp.add(str_b);dlg.add(rp);JMenuItem mi=new JMenuItem("String");mi.addActionListener(e->{Point loc=frame.getLocation();dlg.setLocation((int)loc.getX(),(int)loc.getY()-80);dlg.pack();dlg.setVisible(true);});datatype_menu.add(mi);}

				JMenu delta_menu = new JMenu("Delta");
				String[] dnames={"H","V","Average","Med","Directional","Scanline 5","Adaptive","Scanline 1","Scanline 2","Scanline 3","Scanline 4","Map (1)","Map (2)"};
				int[] dtype_map={0,1,2,3,4,10,5,6,7,8,9,11,12};
				delta_button=new JRadioButtonMenuItem[13]; ButtonGroup dg=new ButtonGroup();
				for(int i=0;i<13;i++){delta_button[i]=new JRadioButtonMenuItem(dnames[i]);dg.add(delta_button[i]);delta_menu.add(delta_button[i]);final int dt=dtype_map[i];delta_button[i].addActionListener(e->{if(delta_type!=dt){delta_type=dt;new ApplyHandler().actionPerformed(null);}});}
				delta_button[0].setSelected(true);

				JMenu entropy_menu = new JMenu("Entropy");
				entropy_button=new JRadioButtonMenuItem[4];
				entropy_button[0]=new JRadioButtonMenuItem("LZ77"); entropy_button[1]=new JRadioButtonMenuItem("Huffman");
				entropy_button[2]=new JRadioButtonMenuItem("Arithmetic"); entropy_button[3]=new JRadioButtonMenuItem("Slow Arithmetic");
				ButtonGroup eg=new ButtonGroup();
				for(int i=0;i<4;i++){eg.add(entropy_button[i]);entropy_menu.add(entropy_button[i]);}
				int[] entropy_map={0,1,3,2};
				entropy_button[0].setSelected(entropy_type==0); entropy_button[1].setSelected(entropy_type==1);
				entropy_button[2].setSelected(entropy_type==3); entropy_button[3].setSelected(entropy_type==2);
				for(int i=0;i<4;i++){final int et=entropy_map[i];entropy_button[i].addActionListener(e->{if(entropy_type!=et)entropy_type=et;});}

				// Segment size for the Arithmetic/Slow Arithmetic entropy types
				// (SaveHandler's `min_seg = 500 + pixel_segment*500`, up to
				// pixel_segment=10 forcing a single unsegmented chunk). Like
				// the entropy_type radio buttons above, this deliberately does
				// NOT call ApplyHandler -- segmentation only happens inside
				// SaveHandler at Save time, it never affects the live preview.
				entropy_menu.addSeparator();
				entropy_menu.add(makeSliderDialog(frame,"Segment Size",0,10,pixel_segment,v->{pixel_segment=v;},ss)); segment_slider=ss[0];

				menu_bar.add(file_menu); menu_bar.add(view_menu); menu_bar.add(quant_menu);
				menu_bar.add(delta_menu); menu_bar.add(datatype_menu); menu_bar.add(entropy_menu);
				frame.setJMenuBar(menu_bar);

				display_image=original_image;
				image_canvas.setPreferredSize(new Dimension((int)(image_xdim*zoom_scale),(int)(image_ydim*zoom_scale)));
				updateTitle();
				frame.setSize(Math.min((int)(image_xdim*fit_scale)+(int)(40*hidpi_scale),(int)(screen_xdim*0.70)),Math.min((int)(image_ydim*fit_scale)+(int)(80*hidpi_scale),(int)(screen_ydim*0.70)));
				int _off=nextWindowOffset; nextWindowOffset=(_off+30)%270;
				frame.setLocation((screen_xdim-frame.getWidth())/2+_off,(screen_ydim-frame.getHeight())/2+_off);
				frame.setVisible(true);
				SwingUtilities.invokeLater(()->showInitialImage());
			}
			else
			{
				// Previously, if raster_type didn't match, the
				// constructor silently did nothing at all -- no window,
				// no error, no message. Fail loudly instead.
				System.out.println("Unsupported image color model (raster_type=" + raster_type
					+ ", expected TYPE_3BYTE_BGR=" + BufferedImage.TYPE_3BYTE_BGR + "). No window was created.");
			}
		}
		catch(Exception e){e.printStackTrace();System.exit(1);}
	}

	private JMenuItem makeSliderDialog(JFrame parent,String title,int lo,int hi,int init,java.util.function.IntConsumer onChange){return makeSliderDialog(parent,title,lo,hi,init,onChange,null);}
	private JMenuItem makeSliderDialog(JFrame parent,String title,int lo,int hi,int init,java.util.function.IntConsumer onChange,JSlider[] ref)
	{
		JMenuItem item=new JMenuItem(title); JDialog dialog=new JDialog(parent,title); JSlider slider=new JSlider(lo,hi,init);
		if(ref!=null)ref[0]=slider;
		JTextField field=new JTextField(3); field.setText(" "+init+" ");
		slider.addChangeListener(e->{int v=slider.getValue();field.setText(" "+v+" ");onChange.accept(v);});
		JPanel p=new JPanel(new BorderLayout()); p.add(slider,BorderLayout.CENTER); p.add(field,BorderLayout.EAST); dialog.add(p);
		item.addActionListener(e->{Point loc=parent.getLocation();dialog.setLocation((int)loc.getX(),(int)loc.getY()-60);dialog.pack();dialog.setVisible(true);});
		return item;
	}

	private void showInitialImage()
	{
		smooth_level=0;smooth2_level=0;pixel_quant=4;pixel_shift=3;correction=0;
		if(smooth_slider!=null)smooth_slider.setValue(0); if(smooth2_slider!=null)smooth2_slider.setValue(0);
		if(pquant_slider!=null)pquant_slider.setValue(4); if(pshift_slider!=null)pshift_slider.setValue(3); if(corr_slider!=null)corr_slider.setValue(0);
		new ApplyHandler().actionPerformed(null);
		new javax.swing.SwingWorker<Void,Void>()
		{
			@Override protected Void doInBackground(){init();return null;}
			@Override protected void done()
			{
				int[] dm={0,1,2,3,4,10,5,6,7,8,9,11,12};
				for(int i2=0;i2<13;i2++)if(dm[i2]==delta_type){delta_button[i2].setSelected(true);break;}
				compress_button[compress_type==0?0:1].setSelected(true);
				new ApplyHandler().actionPerformed(null);
			}
		}.execute();
	}

	private void zoomBy(double factor)
	{
		double ns=Math.max(ZOOM_MIN,Math.min(ZOOM_MAX,zoom_scale*factor)); if(ns==zoom_scale)return;
		JViewport vp=scroll_pane.getViewport(); Point vpos=vp.getViewPosition(); Dimension vs=vp.getSize();
		double cx=vpos.x+vs.width/2.0,cy=vpos.y+vs.height/2.0,r=ns/zoom_scale; zoom_scale=ns;
		updateDisplayImage(); image_canvas.setPreferredSize(new Dimension((int)(image_xdim*zoom_scale),(int)(image_ydim*zoom_scale)));
		image_canvas.revalidate();image_canvas.repaint();
		vp.setViewPosition(new Point(Math.max(0,(int)(cx*r-vs.width/2.0)),Math.max(0,(int)(cy*r-vs.height/2.0))));
		updateTitle();
	}

	private void updateDisplayImage()
	{
		BufferedImage src=(working_image!=null&&initialized)?working_image:original_image;
		if(zoom_scale==1.0){display_image=src;return;}
		int w=Math.max(1,(int)(image_xdim*zoom_scale)),h=Math.max(1,(int)(image_ydim*zoom_scale));
		AffineTransform t=new AffineTransform();t.scale(zoom_scale,zoom_scale);
		display_image=new AffineTransformOp(t,AffineTransformOp.TYPE_BILINEAR).filter(src,new BufferedImage(w,h,src.getType()));
	}

	private void updateTitle(){frame.setTitle("Delta Writer  "+filename+"  ["+(int)Math.round(zoom_scale*100)+"%]");}

	private static void writeTable(DataOutputStream out,int[] table) throws IOException
	{
		out.writeShort(table.length);
		int max=Byte.MAX_VALUE*2+1;
		if(table.length<=max)for(int v:table)out.writeByte(v);else for(int v:table)out.writeShort(v);
	}

	// FIX (bugs #1 and #3): right-shifts channel by pixel_shift with
	// rounding to nearest (adding half a quantization step before
	// truncating, then clamping so the chosen quantization index can
	// never reconstruct past 255), rather than a pure truncating shift.
	//
	// Bug #3: the ORIGINAL rounding attempt in init() compared against
	// `255 - pixel_shift` (unrelated to `half`), which neither reliably
	// prevented overflow (e.g. pixel_shift=4, half=8: channel[k]=250
	// passes `250<251`, but 250+8=258, 258>>4=16, 16<<4=256 on the read
	// side -- past 255) nor correctly identified when rounding was safe.
	// applyImpl() had no rounding attempt at all -- a plain truncating
	// DeltaMapper.shift(ch,-pixel_shift) -- which biases every
	// reconstructed pixel <= the original, never brighter, by about half
	// a quantization step on average (confirmed in the Python port this
	// project also produced: ~61 levels dark on average at pixel_shift=7).
	//
	// Bug #1: `channel_list.get(i)` returns a reference to the ACTUAL
	// stored array, not a copy. The original code mutated `channel[k]`
	// in place, permanently corrupting channel_list's contents (the
	// pristine source pixel data every subsequent Apply/Save call reads
	// as ground truth) the first time init() ran -- which happens
	// automatically, via the background SwingWorker in
	// showInitialImage(), on essentially every image you open. This
	// helper returns a NEW array and never modifies `channel` itself, so
	// it's safe to call on a shared channel_list reference repeatedly.
	private static int[] quantizeChannel(int[] channel, int pixel_shift)
	{
		if (pixel_shift == 0) return channel;
		int half = 1 << (pixel_shift - 1);
		int[] rounded = new int[channel.length];
		for (int k = 0; k < channel.length; k++)
		{
			int v = channel[k] + half;
			if (v > 255) v = 255;
			rounded[k] = v;
		}
		return DeltaMapper.shift(rounded, -pixel_shift);
	}

	// Packs and compresses an int[] array (delta values, or a map array
	// widened from byte to int), returning the compressed StringMapper
	// bit string. Callers pull both the bitlength and the "did it really
	// compress" answer (via getIterations) from the same compressed
	// result, so packing/compressing only has to happen once per array.
	private byte[] packAndCompress(int[] values)
	{
		byte[] packed = (byte[]) StringMapper.getStringList(values, false).get(3);
		return StringMapper.compressStrings(packed);
	}

	private void computeSetSums()
	{
		set_sum[0]=channel_sum[0]+channel_sum[1]+channel_sum[2]; set_sum[1]=channel_sum[0]+channel_sum[4]+channel_sum[2];
		set_sum[2]=channel_sum[0]+channel_sum[3]+channel_sum[2]; set_sum[3]=channel_sum[0]+channel_sum[1]+channel_sum[4];
		set_sum[4]=channel_sum[0]+channel_sum[3]+channel_sum[5]; set_sum[5]=channel_sum[3]+channel_sum[1]+channel_sum[2];
		set_sum[6]=channel_sum[3]+channel_sum[4]+channel_sum[2]; set_sum[7]=channel_sum[3]+channel_sum[1]+channel_sum[4];
		set_sum[8]=channel_sum[5]+channel_sum[1]+channel_sum[4]; set_sum[9]=channel_sum[5]+channel_sum[4]+channel_sum[2];
	}

	// Prints the ranked channel-set table (rank, channel composition, per-
	// channel entropy estimate, total), marking the one init() selected.
	private void printChannelSetRanking()
	{
		Integer[] order = new Integer[10];
		for (int i = 0; i < 10; i++) order[i] = i;
		java.util.Arrays.sort(order, (a, b) -> set_sum[a] - set_sum[b]);
		System.out.println("Channel sets (ranked):");
		for (int r = 0; r < 10; r++)
		{
			int idx = order[r];
			int[] c = DeltaMapper.getChannels(idx);
			String sel = (idx == min_set_id) ? " **" : "";
			System.out.println(String.format("  %2d. %-32s %10d %10d %10d %12d%s",
				r + 1, set_string[idx], channel_sum[c[0]], channel_sum[c[1]], channel_sum[c[2]], set_sum[idx], sel));
		}
		System.out.println();
	}

	// Prints the ranked delta-type table: rank, name, delta bits (with its
	// own compression marker), map bits for the types that have one (with
	// its own marker), and the combined total used for selection -- marking
	// the type init() actually selected. Mirrors SimpleWriter's format:
	// types without a map (0-5) print with the map column blank rather than
	// a misleading "0", so the delta/total columns stay aligned either way.
	//
	// A compression marker (*) means StringMapper's compressStrings() ran
	// on that specific bit string (delta or map) and its iterations count
	// indicated real compression, not just a pass-through. All 13 types
	// build a real delta bit string and compress it for real (types 6-12
	// previously used a Shannon-limit estimate from a frequency histogram
	// instead -- faster, but with no actual bit string to check
	// compression on). The map-using types (6-12) also now compress their
	// actual per-pixel map array the same way, rather than the older
	// approximation of feeding the map's frequency counts through the same
	// pipeline as a stand-in for the real data.
	private void printDeltaTypeRanking(int[] delta_bits, int[] map_bits,
	                                    boolean[] delta_compressed, boolean[] map_compressed,
	                                    int[] total_delta_sum)
	{
		Integer[] order = new Integer[13];
		for (int i = 0; i < 13; i++) order[i] = i;
		java.util.Arrays.sort(order, (a, b) -> total_delta_sum[a] - total_delta_sum[b]);
		System.out.println("Delta types (ranked):");
		for (int r = 0; r < 13; r++)
		{
			int idx = order[r];
			String dc  = delta_compressed[idx] ? "*" : " ";
			String sel = (idx == delta_type) ? " **" : "";
			if (idx >= 6)
			{
				String mc = map_compressed[idx] ? "*" : " ";
				System.out.println(String.format("  %2d. %-16s delta: %12d%s      map: %12d%s      total: %12d%s",
					r + 1, delta_type_string[idx], delta_bits[idx], dc, map_bits[idx], mc, total_delta_sum[idx], sel));
			}
			else
			{
				System.out.println(String.format("  %2d. %-16s delta: %12d%s                              total: %12d%s",
					r + 1, delta_type_string[idx], delta_bits[idx], dc, total_delta_sum[idx], sel));
			}
		}
		System.out.println();
	}

	// FIX (bug #5): DeltaReader.java expects two DIFFERENT on-disk map
	// formats depending on delta_type -- a raw, uncompressed 2-bit-packed
	// array (no table) for delta_type 6-8, and a StringMapper-compressed
	// array (with a table) for delta_type 9-12. writeMap() previously
	// always wrote the StringMapper format regardless of delta_type,
	// which would desync DeltaReader's byte stream (and corrupt
	// everything read after it) for any file saved with delta_type 6, 7,
	// or 8.
	private void writeMapRaw2Bit(DataOutputStream out,int i) throws IOException
	{
		// Matches DeltaReader.java's expectation exactly:
		// map_raw[q] = (pm[q>>2] >> ((q&3)<<1)) & 0x3
		byte[] map=(byte[])map_list.get(i);
		int ml=map.length;
		int pml=(ml+3)/4;
		byte[] packed=new byte[pml];
		for(int q=0;q<ml;q++)
		{
			int v=map[q]&0x3;
			packed[q>>2]|=(byte)(v<<((q&3)<<1));
		}
		out.writeInt(ml); out.writeInt(pml); out.write(packed,0,pml);
	}

	private void writeMapStringMapper(DataOutputStream out,int i) throws IOException
	{
		byte[] map=(byte[])map_list.get(i); int[] map_int=new int[map.length];
		for(int q=0;q<map.length;q++)map_int[q]=map[q]&0xFF;
		ArrayList dsl=StringMapper.getStringList(map_int,false);
		int dmin=(int)dsl.get(0); int[] tbl=(int[])dsl.get(2); byte[] str=(byte[])dsl.get(3); int bl=StringMapper.getBitlength(str);
		out.writeInt(map.length); writeTable(out,tbl); out.writeInt(dmin); out.writeInt(bl); out.write(str,0,StringMapper.getBytelength(bl));
	}

	private void writeMap(DataOutputStream out,int i) throws IOException
	{
		if(delta_type>=6&&delta_type<=8) writeMapRaw2Bit(out,i);
		else writeMapStringMapper(out,i);
	}

	class ImageCanvas extends JPanel
	{
		public ImageCanvas(){setOpaque(true);}
		@Override public Dimension getPreferredSize(){return display_image!=null?new Dimension(display_image.getWidth(),display_image.getHeight()):new Dimension(Math.max(1,(int)(image_xdim*zoom_scale)),Math.max(1,(int)(image_ydim*zoom_scale)));}
		@Override protected synchronized void paintComponent(Graphics g){super.paintComponent(g);if(display_image!=null)g.drawImage(display_image,0,0,this);}
	}

	class ApplyHandler implements ActionListener
	{
		public void actionPerformed(ActionEvent event)
		{
			try{applyImpl();}catch(Exception e){System.out.println("ApplyHandler exception: "+e);e.printStackTrace();}
		}

		private void applyImpl()
		{
			ArrayList<int[]> qcl=new ArrayList<int[]>(), dqcl=new ArrayList<int[]>();
			int new_xdim=image_xdim,new_ydim=image_ydim;
			if(pixel_quant!=0){double f=pixel_quant/10.0;new_xdim=image_xdim-(int)(f*(image_xdim/2-2));new_ydim=image_ydim-(int)(f*(image_ydim/2-2));}
			for(int i=0;i<3;i++)
			{
				int[] ch=(int[])channel_list.get(i);
				if(smooth_level>0)ch=DeltaMapper.bilateralSmooth(ch,image_xdim,image_ydim,smooth_level);
				if(smooth2_level>0)ch=DeltaMapper.anisotropicSmooth(ch,image_xdim,image_ydim,smooth2_level);
				if(pixel_quant==0)qcl.add(quantizeChannel(ch,pixel_shift));
				else{int[] r=ResizeMapper.resize(ch,image_xdim,new_xdim,new_ydim);qcl.add(quantizeChannel(r,pixel_shift));}
			}
			qcl.add(DeltaMapper.getDifference(qcl.get(0),qcl.get(1)));
			qcl.add(DeltaMapper.getDifference(qcl.get(2),qcl.get(1)));
			qcl.add(DeltaMapper.getDifference(qcl.get(2),qcl.get(0)));
			for(int i=0;i<6;i++)
			{
				int[] qc=qcl.get(i);int min=256;for(int v:qc)if(v<min)min=v;
				channel_min[i]=min;if(i>2)for(int k=0;k<qc.length;k++)qc[k]-=min;
				channel_init[i]=qc[0];qcl.set(i,qc);
				channel_sum[i]=(int)Math.floor(CodeMapper.getShannonLimit(DeltaMapper.getIdealFrequency(qc,new_xdim,new_ydim)));
			}
			DeltaWriter.this.computeSetSums();
			int min_sum=Integer.MAX_VALUE,min_idx=0;
			for(int i=0;i<10;i++)if(set_sum[i]<min_sum){min_sum=set_sum[i];min_idx=i;}
			min_set_id=min_idx;
			file_compression_rate=(double)file_length/(image_xdim*image_ydim*3);
			int[] channel_id=DeltaMapper.getChannels(min_set_id);
			table_list.clear();string_list.clear();map_list.clear();delta_list.clear();

			boolean int_allowed=true;
			for(int i=0;i<3;i++){int[] qc=qcl.get(channel_id[i]);int cmin=qc[0],cmax=qc[0];for(int v:qc){if(v<cmin)cmin=v;if(v>cmax)cmax=v;}if((cmax-cmin)*2>255){int_allowed=false;break;}}
			if(!int_allowed&&compress_type==0){compress_type=1;SwingUtilities.invokeLater(()->compress_button[1].setSelected(true));}
			if(int_radio_btns!=null){final boolean ena=int_allowed;SwingUtilities.invokeLater(()->{for(JRadioButton b:int_radio_btns)b.setEnabled(ena);});}

			for(int i=0;i<3;i++)
			{
				int j=channel_id[i];int[] qc=qcl.get(j);
				ArrayList<Object> result=new ArrayList<Object>();
				if(delta_type==0)result=DeltaMapper.getHorizontalDeltasFromValues(qc,new_xdim,new_ydim);
				else if(delta_type==1)result=DeltaMapper.getVerticalDeltasFromValues(qc,new_xdim,new_ydim);
				else if(delta_type==2)result=DeltaMapper.getAverageDeltasFromValues(qc,new_xdim,new_ydim);
				else if(delta_type==3)result=DeltaMapper.getMedDeltasFromValues(qc,new_xdim,new_ydim);
				else if(delta_type==4)result=DeltaMapper.getDirectionalDeltasFromValues(qc,new_xdim,new_ydim);
				else if(delta_type==5)result=DeltaMapper.getAdaptiveDeltasFromValues(qc,new_xdim,new_ydim);
				else if(delta_type==6){result=DeltaMapper.getMixedDeltasFromValues(qc,new_xdim,new_ydim);map_list.add(result.get(2));}
				else if(delta_type==7){result=DeltaMapper.getMixedDeltasFromValues2(qc,new_xdim,new_ydim);map_list.add(result.get(2));}
				else if(delta_type==8){result=DeltaMapper.getMixedDeltasFromValues4(qc,new_xdim,new_ydim);map_list.add(result.get(2));}
				else if(delta_type==9){result=DeltaMapper.getMixedDeltasFromValues16Rows(qc,new_xdim,new_ydim);map_list.add(result.get(2));}
				else if(delta_type==10){result=DeltaMapper.getMixedDeltasFromValues8Rows(qc,new_xdim,new_ydim,scanline5_variant);map_list.add(result.get(2));}
				else if(delta_type==11){result=DeltaMapper.getIdealDeltasFromValues8(qc,new_xdim,new_ydim);map_list.add(result.get(2));}
				else{result=DeltaMapper.getIdealDeltasFromValues16(qc,new_xdim,new_ydim);map_list.add(result.get(2));}
				int[] delta=(int[])result.get(1);
				if(compress_type==0)
				{
					ArrayList hl=StringMapper.getHistogram(delta);int delta_min=(int)hl.get(0);channel_delta_min[j]=delta_min;
					byte[] db=new byte[delta.length];db[0]=0;
					for(int k=1;k<delta.length;k++)db[k]=(byte)(delta[k]-delta_min);
					delta_list.add(db);for(int k=1;k<delta.length;k++)delta[k]=db[k]+delta_min;
				}
				else
				{
					boolean precompress=(compress_type==2);
					ArrayList dsl=StringMapper.getStringList(delta,precompress);
					channel_delta_min[j]=(int)dsl.get(0);channel_length[j]=(int)dsl.get(1);
					table_list.add((int[])dsl.get(2));string_list.add((byte[])dsl.get(3));
					channel_compressed_length[j]=StringMapper.getBitlength((byte[])dsl.get(3));
					channel_iterations[i]=StringMapper.getIterations((byte[])dsl.get(3));
					for(int k=1;k<delta.length;k++)delta[k]+=channel_delta_min[j];
				}
			}

			for(int i=0;i<3;i++)
			{
				int j=channel_id[i];int[] delta=new int[new_xdim*new_ydim];
				if(compress_type==0){byte[] db=(byte[])delta_list.get(i);delta[0]=0;for(int k=1;k<delta.length;k++)delta[k]=db[k]+channel_delta_min[j];}
				else{int[] tbl=(int[])table_list.get(i);byte[] str=StringMapper.decompressStrings((byte[])string_list.get(i));delta=StringMapper.unpackStrings(str,tbl,new_xdim*new_ydim,channel_length[j]);delta[0]=0;for(int k=1;k<delta.length;k++)delta[k]+=channel_delta_min[j];}
				int[] ch=new int[0];
				if(delta_type==0)ch=DeltaMapper.getValuesFromHorizontalDeltas(delta,new_xdim,new_ydim,channel_init[j]);
				else if(delta_type==1)ch=DeltaMapper.getValuesFromVerticalDeltas(delta,new_xdim,new_ydim,channel_init[j]);
				else if(delta_type==2)ch=DeltaMapper.getValuesFromAverageDeltas(delta,new_xdim,new_ydim,channel_init[j]);
				else if(delta_type==3)ch=DeltaMapper.getValuesFromMedDeltas(delta,new_xdim,new_ydim,channel_init[j]);
				else if(delta_type==4)ch=DeltaMapper.getValuesFromDirectionalDeltas(delta,new_xdim,new_ydim,channel_init[j]);
				else if(delta_type==5)ch=DeltaMapper.getValuesFromAdaptiveDeltas(delta,new_xdim,new_ydim,channel_init[j]);
				else if(delta_type==6)ch=DeltaMapper.getValuesFromMixedDeltas(delta,new_xdim,new_ydim,channel_init[j],(byte[])map_list.get(i));
				else if(delta_type==7)ch=DeltaMapper.getValuesFromMixedDeltas2(delta,new_xdim,new_ydim,channel_init[j],(byte[])map_list.get(i));
				else if(delta_type==8)ch=DeltaMapper.getValuesFromMixedDeltas4(delta,new_xdim,new_ydim,channel_init[j],(byte[])map_list.get(i));
				else if(delta_type==9)ch=DeltaMapper.getValuesFromMixedDeltas16Rows(delta,new_xdim,new_ydim,channel_init[j],(byte[])map_list.get(i));
				else if(delta_type==10)ch=DeltaMapper.getValuesFromMixedDeltas8Rows(delta,new_xdim,new_ydim,channel_init[j],(byte[])map_list.get(i),scanline5_variant);
				else if(delta_type==11)ch=DeltaMapper.getValuesFromIdealDeltas8(delta,new_xdim,new_ydim,channel_init[j],(byte[])map_list.get(i));
				else ch=DeltaMapper.getValuesFromIdealDeltas16(delta,new_xdim,new_ydim,channel_init[j],(byte[])map_list.get(i));
				if(j>2)for(int k=0;k<ch.length;k++)ch[k]+=channel_min[j];
				// FIX (bug #2): no resize/shift here -- DeltaReader.java's
				// canonical order is decode raw small channels, THEN
				// recombine via set_id (below), THEN resize the combined
				// blue/green/red, THEN shift. Resizing/shifting each
				// channel individually before recombining (the original
				// order here) is NOT equivalent, because ResizeMapper's
				// internal averaging uses truncating integer division --
				// this file's own live preview didn't actually show what
				// DeltaReader would produce when loading the saved file.
				dqcl.add(ch);
			}

			int[] blue=new int[new_xdim*new_ydim],green=new int[new_xdim*new_ydim],red=new int[new_xdim*new_ydim];
			if(min_set_id==0){blue=dqcl.get(0);green=dqcl.get(1);red=dqcl.get(2);}
			else if(min_set_id==1){blue=dqcl.get(0);red=dqcl.get(1);green=DeltaMapper.getDifference(red,dqcl.get(2));}
			else if(min_set_id==2){blue=dqcl.get(0);red=dqcl.get(1);green=DeltaMapper.getDifference(blue,dqcl.get(2));}
			else if(min_set_id==3){blue=dqcl.get(0);green=DeltaMapper.getDifference(blue,dqcl.get(1));red=DeltaMapper.getSum(dqcl.get(2),green);}
			else if(min_set_id==4){blue=dqcl.get(0);green=DeltaMapper.getDifference(blue,dqcl.get(1));red=DeltaMapper.getSum(blue,dqcl.get(2));}
			else if(min_set_id==5){green=dqcl.get(0);red=dqcl.get(1);blue=DeltaMapper.getSum(dqcl.get(2),green);}
			else if(min_set_id==6){red=dqcl.get(0);int[]bg=dqcl.get(1);int[]rg=dqcl.get(2);for(int i=0;i<rg.length;i++)rg[i]=-rg[i];green=DeltaMapper.getSum(rg,red);blue=DeltaMapper.getSum(bg,green);}
			else if(min_set_id==7){green=dqcl.get(0);blue=DeltaMapper.getSum(green,dqcl.get(1));red=DeltaMapper.getSum(green,dqcl.get(2));}
			else if(min_set_id==8){green=dqcl.get(0);red=DeltaMapper.getSum(green,dqcl.get(1));blue=DeltaMapper.getDifference(red,dqcl.get(2));}
			else if(min_set_id==9){red=dqcl.get(0);green=DeltaMapper.getDifference(red,dqcl.get(1));blue=DeltaMapper.getDifference(red,dqcl.get(2));}

			if(pixel_quant!=0)
			{
				blue=ResizeMapper.resize(blue,new_xdim,image_xdim,image_ydim);
				green=ResizeMapper.resize(green,new_xdim,image_xdim,image_ydim);
				red=ResizeMapper.resize(red,new_xdim,image_xdim,image_ydim);
			}
			if(pixel_shift!=0)
			{
				blue=DeltaMapper.shift(blue,pixel_shift);
				green=DeltaMapper.shift(green,pixel_shift);
				red=DeltaMapper.shift(red,pixel_shift);
			}

			int[] ob=(int[])channel_list.get(0),og=(int[])channel_list.get(1),or_=(int[])channel_list.get(2);
			for(int i=0;i<image_xdim*image_ydim;i++){if(correction!=0){double f=correction/10.0;blue[i]+=(int)((ob[i]-blue[i])*f);green[i]+=(int)((og[i]-green[i])*f);red[i]+=(int)((or_[i]-red[i])*f);}}
			int k=0;for(int i=0;i<image_ydim;i++)for(int j=0;j<image_xdim;j++)working_image.setRGB(j,i,(blue[k]<<16)+(green[k]<<8)+red[k++]);
			updateDisplayImage();image_canvas.repaint();initialized=true;
		}
	}

	class SaveHandler implements ActionListener
	{
		public void actionPerformed(ActionEvent event)
		{
			if(!initialized)new ApplyHandler().actionPerformed(null);
			int[] channel_id=DeltaMapper.getChannels(min_set_id);
			try
			{
				DataOutputStream out=new DataOutputStream(new FileOutputStream(new File("foo")));
				out.writeShort(image_xdim);out.writeShort(image_ydim);out.writeByte(pixel_shift);out.writeByte(pixel_quant);
				out.writeByte(min_set_id);out.writeByte(delta_type);out.writeByte(compress_type);out.writeByte(entropy_type);out.writeByte(scanline5_variant);
				if(entropy_type==0||entropy_type==1)
				{
					for(int i=0;i<3;i++)
					{
						int j=channel_id[i];
						out.writeInt(channel_min[j]);out.writeInt(channel_init[j]);out.writeInt(channel_delta_min[j]);
						out.writeInt(channel_length[j]);out.writeInt(channel_compressed_length[j]);out.writeByte(channel_iterations[i]);
						if(delta_type>=6)writeMap(out,i);
						if(compress_type>0)writeTable(out,(int[])table_list.get(i));
						byte[] payload=getPayload(i);
						if(entropy_type==0)
						{
							Deflater def=new Deflater(Deflater.BEST_COMPRESSION);byte[] zipped=new byte[2*payload.length];
							def.setInput(payload);def.finish();int zl=def.deflate(zipped);def.end();
							out.writeInt(payload.length);out.writeInt(zl);out.write(zipped,0,zl);
						}
						else
						{
							int[] pi=new int[payload.length];for(int k=0;k<payload.length;k++){pi[k]=payload[k];if(pi[k]<0)pi[k]+=256;}
							ArrayList hl=StringMapper.getHistogram(pi);int pmin=(int)hl.get(0);int[] hist=(int[])hl.get(1);int[] rt=StringMapper.getRankTable(hist);
							for(int k=0;k<pi.length;k++)pi[k]-=pmin;int n=hist.length;
							ArrayList<Integer> fl=new ArrayList<>();for(int v:hist)fl.add(v);Collections.sort(fl,Comparator.reverseOrder());
							int[] freq=new int[n];for(int k=0;k<n;k++)freq[k]=fl.get(k);
							byte[] hl2=CodeMapper.getHuffmanLength2(freq);int[] hc=CodeMapper.getCanonicalCode(hl2);
							ArrayList pl=CodeMapper.packCode(pi,rt,hc,hl2);byte[] pb=(byte[])pl.get(0);int bl=(int)pl.get(1);
							writeTable(out,rt);out.writeInt(pmin);
							ArrayList ltl=CodeMapper.packLengthTable(hl2);int ltn=(int)ltl.get(0);byte ltinit=(byte)ltl.get(1);byte ltmax=(byte)ltl.get(2);byte[] ltdelta=(byte[])ltl.get(3);
							out.writeInt(ltn);out.writeByte(ltinit);out.writeByte(ltmax);out.writeByte(ltdelta.length);out.write(ltdelta,0,ltdelta.length);out.writeInt(bl);out.writeInt(pb.length);out.write(pb,0,pb.length);
						}
					}
				}
				else if(entropy_type==2)
				{
					byte[][] payloads=new byte[3][];int[] n_segs=new int[3];byte[][][] segs=new byte[3][][];int[][][] freqs=new int[3][][];
					for(int i=0;i<3;i++){payloads[i]=getPayload(i);int min_seg=500+pixel_segment*500;n_segs[i]=(pixel_segment>=10)?1:Math.max(1,payloads[i].length/min_seg);int seg_len=payloads[i].length/n_segs[i];int odd_len=seg_len+payloads[i].length%n_segs[i];segs[i]=new byte[n_segs[i]][];freqs[i]=new int[n_segs[i]][256];for(int m=0;m<n_segs[i];m++)segs[i][m]=new byte[m<n_segs[i]-1?seg_len:odd_len];int pos=0;for(int m=0;m<n_segs[i];m++)for(int nn=0;nn<segs[i][m].length;nn++){segs[i][m][nn]=payloads[i][pos];int p=payloads[i][pos];if(p<0)p+=256;freqs[i][m][p]++;pos++;}}
					BigInteger[][][] offsets=new BigInteger[3][][];for(int i=0;i<3;i++)offsets[i]=new BigInteger[n_segs[i]][2];
					Thread[][][] enc_threads=new Thread[3][][];
					for(int i=0;i<3;i++){enc_threads[i]=new Thread[1][n_segs[i]];for(int m=0;m<n_segs[i];m++){final BigInteger[] so=offsets[i][m];final byte[] sd=segs[i][m];final int[] sf=freqs[i][m];enc_threads[i][0][m]=new Thread(()->{BigInteger[] r=ArithmeticMapper.getIntervalValue(sd,sf);so[0]=r[0];so[1]=r[1];});enc_threads[i][0][m].start();}}
					for(int i=0;i<3;i++)for(Thread t:enc_threads[i][0])t.join();
					int[] len_types=new int[3];byte[][] zip_freqs=new byte[3][];int[] zip_lens=new int[3];deflateFrequencies(n_segs,freqs,len_types,zip_freqs,zip_lens);
					for(int i=0;i<3;i++){int j=channel_id[i];out.writeInt(channel_min[j]);out.writeInt(channel_init[j]);out.writeInt(channel_delta_min[j]);out.writeInt(channel_length[j]);out.writeInt(channel_compressed_length[j]);out.writeByte(channel_iterations[i]);if(delta_type>=6)writeMap(out,i);if(compress_type>0)writeTable(out,(int[])table_list.get(i));out.writeInt(n_segs[i]);out.writeInt(len_types[i]);out.writeInt(zip_lens[i]);out.write(zip_freqs[i],0,zip_lens[i]);for(int k=0;k<n_segs[i];k++){byte[] b0=offsets[i][k][0].toByteArray();out.writeInt(b0.length);out.write(b0,0,b0.length);byte[] b1=offsets[i][k][1].toByteArray();out.writeInt(b1.length);out.write(b1,0,b1.length);}}
				}
				else
				{
					byte[][] payloads=new byte[3][];int[] n_segs=new int[3];byte[][][] segs=new byte[3][][];int[][][] freqs=new int[3][][];
					for(int i=0;i<3;i++){payloads[i]=getPayload(i);int min_seg=500+pixel_segment*500;n_segs[i]=(pixel_segment>=10)?1:Math.max(1,payloads[i].length/min_seg);int seg_len=payloads[i].length/n_segs[i];int odd_len=seg_len+payloads[i].length%n_segs[i];segs[i]=new byte[n_segs[i]][];freqs[i]=new int[n_segs[i]][256];for(int m=0;m<n_segs[i];m++)segs[i][m]=new byte[m<n_segs[i]-1?seg_len:odd_len];int pos=0;for(int m=0;m<n_segs[i];m++)for(int nn=0;nn<segs[i][m].length;nn++){segs[i][m][nn]=payloads[i][pos];int p=payloads[i][pos];if(p<0)p+=256;freqs[i][m][p]++;pos++;}}
					byte[][][] fast_enc=new byte[3][][];for(int i=0;i<3;i++)fast_enc[i]=new byte[n_segs[i]][];
					Thread[][][] fast_threads=new Thread[3][][];
					for(int i=0;i<3;i++){fast_threads[i]=new Thread[1][n_segs[i]];for(int m=0;m<n_segs[i];m++){final byte[][] fe=fast_enc[i];final int fm=m;final byte[] sd=segs[i][m];final int[] sf=freqs[i][m];fast_threads[i][0][m]=new Thread(()->fe[fm]=ArithmeticMapper.getIntervalValueFast(sd,sf));fast_threads[i][0][m].start();}}
					for(int i=0;i<3;i++)for(Thread t:fast_threads[i][0])t.join();
					int[] len_types=new int[3];byte[][] zip_freqs=new byte[3][];int[] zip_lens=new int[3];deflateFrequencies(n_segs,freqs,len_types,zip_freqs,zip_lens);
					for(int i=0;i<3;i++){int j=channel_id[i];out.writeInt(channel_min[j]);out.writeInt(channel_init[j]);out.writeInt(channel_delta_min[j]);out.writeInt(channel_length[j]);out.writeInt(channel_compressed_length[j]);out.writeByte(channel_iterations[i]);if(delta_type>=6)writeMap(out,i);if(compress_type>0)writeTable(out,(int[])table_list.get(i));out.writeInt(n_segs[i]);out.writeInt(len_types[i]);out.writeInt(zip_lens[i]);out.write(zip_freqs[i],0,zip_lens[i]);for(int k=0;k<n_segs[i];k++){byte[] enc=fast_enc[i][k];out.writeInt(enc.length);out.write(enc,0,enc.length);}}
				}
				out.flush();out.close();
				File saved=new File("foo");double rate=(double)saved.length()/(image_xdim*image_ydim*3);
				System.out.println("Original compression rate: "+String.format("%.4f",file_compression_rate));
				System.out.println("Output  compression rate:  "+String.format("%.4f",rate));
			}
			catch(Exception e){System.out.println("SaveHandler exception: "+e);e.printStackTrace();}
		}

		private void deflateFrequencies(int[] n_segs,int[][][] freqs,int[] len_types,byte[][] zip_freqs,int[] zip_lens) throws InterruptedException
		{
			Thread[] dfl=new Thread[3];
			for(int i=0;i<3;i++){final int fi=i;final int[][] fr=freqs[i];final int ns=n_segs[i];dfl[i]=new Thread(()->{int fmax=0;for(int[]row:fr)for(int v:row)if(v>fmax)fmax=v;int lt=(fmax<Byte.MAX_VALUE*2+2)?0:(fmax<Short.MAX_VALUE*2+2)?1:2;len_types[fi]=lt;int bpe=(lt==0)?1:(lt==1)?2:4;byte[] fb=new byte[ns*256*bpe];for(int k=0;k<ns;k++)for(int m=0;m<256;m++){int v=fr[k][m];int base=k*256*bpe+m*bpe;for(int b=0;b<bpe;b++)fb[base+b]=(byte)(v>>(8*b));}Deflater def=new Deflater(Deflater.BEST_COMPRESSION);byte[] zf=new byte[fb.length];def.setInput(fb);def.finish();int zl=def.deflate(zf);def.end();zip_freqs[fi]=zf;zip_lens[fi]=zl;});dfl[i].start();}
			for(Thread t:dfl)t.join();
		}

		private byte[] getPayload(int i){return compress_type==0?(byte[])delta_list.get(i):(byte[])string_list.get(i);}
	}
}
