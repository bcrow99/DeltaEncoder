import java.awt.*;
import java.awt.image.*;
import java.io.*;
import java.awt.event.*;
import java.awt.geom.AffineTransform;
import java.util.*;
import java.util.zip.*;
import javax.imageio.*;
import javax.swing.*;

// A streamlined DeltaWriter. Files it saves are read by the regular
// DeltaReader -- the header and per-channel layout are the same, with
// compress_type always 2 (String*), pixel_pyramid 0 and use_saddle off.
//
// Compared with DeltaWriter:
//   - no Datatype menu and no Integer path: deltas are always unary
//     strings from StringMapper.getStringList(delta, true), which keeps
//     the string compressed only if that beats StringMapper's threshold;
//   - the Quantization menu has just Pixel Resolution, Color Resolution
//     and Error Correction (no Smooth, Smooth2 or pyramid averaging);
//   - only the scanline and frame-map delta types (6-12), and the
//     startup survey only ranks those.
public class DeltaWriter2
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
	int pixel_segment = 10;  // Arithmetic blocks: 10 = one block per channel (default); lower = blocks of 500+500*pixel_segment bytes
	int correction    = 0;
	int min_set_id    = 0;
	int delta_type    = 6;   // 6-12 only (Scanline 1-5, Map 1-2)
	int entropy_type  = 0;
	byte scanline5_variant = 0;

	// Written to the header for DeltaReader. 3 means String* (strings that
	// don't compress enough come back uncompressed from getStringList)
	// AND a one-byte coding flag in front of each map for delta_type 9-12,
	// so a map can be stored either as a unary string (0) or arithmetic
	// coded (1), whichever is smaller -- see writeMap. DeltaReader treats
	// 3 like 2 everywhere else, and files from DeltaWriter (1 or 2) have
	// no flag, so they read exactly as before. No pyramid.
	static final byte COMPRESS_TYPE = 3;

	// The delta types this program uses, in menu order. The numbers are
	// the same delta_type values DeltaWriter/DeltaReader use.
	static final int   FIRST_DELTA_TYPE = 6;
	static final int   N_DELTA_TYPES    = 7;
	static final String[] DELTA_MENU_NAMES = {"Scanline 1","Scanline 2","Scanline 3","Scanline 4","Scanline 5","Map 1","Map 2"};

	JSlider pquant_slider, pshift_slider, corr_slider, segment_slider;

	double zoom_scale = 1.0;
	double fit_scale  = 1.0;
	static final double ZOOM_FACTOR = 1.25;
	static final double ZOOM_MIN    = 0.05;
	static final double ZOOM_MAX    = 32.0;

	// See DeltaWriter.java's matching field for the full explanation.
	static double hidpi_scale = 1.0;

	int[]  set_sum, channel_sum;
	int[]  channel_init, channel_min, channel_delta_min;
	int[]  channel_length, channel_compressed_length;
	byte[] channel_iterations;

	String[] set_string, delta_type_string, channel_string;
	JRadioButtonMenuItem[] delta_button;
	JRadioButtonMenuItem[] entropy_button;

	ArrayList<Object> channel_list, table_list, string_list, map_list;

	long   file_length;
	double file_compression_rate;
	boolean initialized = false;

	static int openWindowCount  = 0;
	static int nextWindowOffset = 0;

	public static void main(String[] args)
	{
		applyHiDpiFontScaleIfNeeded();
		if (args.length == 1) { new DeltaWriter2(args[0]); }
		else
		{
			FileDialog fd = new FileDialog((java.awt.Frame) null, "Open Image", FileDialog.LOAD);
			fd.setVisible(true);
			if (fd.getFile() != null) new DeltaWriter2(new File(fd.getDirectory(), fd.getFile()).getPath());
			else System.exit(0);
		}
	}

	// =========================================================================
	// HiDPI font-scale fallback -- see DeltaWriter.java's matching methods
	// for the full explanation. A no-op on Windows and correctly configured
	// Linux sessions; only activates where Java missed a HiDPI display.
	// =========================================================================

	private static double detectMissingUiScale()
	{
		try
		{
			GraphicsConfiguration gc = GraphicsEnvironment.getLocalGraphicsEnvironment()
				.getDefaultScreenDevice().getDefaultConfiguration();
			double current_scale = gc.getDefaultTransform().getScaleX();

			if (current_scale > 1.01) return 1.0;

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

			Object xft_dpi_prop = Toolkit.getDefaultToolkit().getDesktopProperty("gnome.Xft/DPI");
			if (xft_dpi_prop instanceof Integer)
			{
				double xft_dpi           = ((Integer) xft_dpi_prop) / 1024.0;
				double xft_implied_scale = xft_dpi / 96.0;
				if (xft_implied_scale >= 1.25) return xft_implied_scale;
			}

			int    dpi           = Toolkit.getDefaultToolkit().getScreenResolution();
			double implied_scale = dpi / 96.0;
			if (implied_scale >= 1.25) return implied_scale;

			return 1.0;
		}
		catch (Exception e)
		{
			return 1.0;
		}
	}

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

	// Startup survey: picks the channel set, then ranks the 7 scanline/
	// frame-map delta types by the compressed size of their delta string
	// plus their map, and selects the smallest.
	public void init()
	{
		final int new_xdim, new_ydim;
		if (pixel_quant != 0)
		{
			double factor = pixel_quant / 10.0;
			new_xdim = image_xdim - (int)(factor * (image_xdim / 2 - 2));
			new_ydim = image_ydim - (int)(factor * (image_ydim / 2 - 2));
		}
		else { new_xdim = image_xdim; new_ydim = image_ydim; }

		// Resize/quantize the 3 base channels, then offsets and entropy
		// estimates for all 6 candidates -- each stage in parallel.
		final int[][] qc6 = new int[6][];
		parallel(3, i ->
		{
			int[] channel = (int[]) channel_list.get(i);
			if (pixel_quant == 0) qc6[i] = quantizeChannel(channel, pixel_shift);
			else                  qc6[i] = quantizeChannel(ResizeMapper.resize(channel, image_xdim, new_xdim, new_ydim), pixel_shift);
		});
		qc6[3] = DeltaMapper.getDifference(qc6[0], qc6[1]);
		qc6[4] = DeltaMapper.getDifference(qc6[2], qc6[1]);
		qc6[5] = DeltaMapper.getDifference(qc6[2], qc6[0]);
		parallel(6, i ->
		{
			int[] qc = qc6[i]; int min = 256;
			for (int v : qc) if (v < min) min = v;
			channel_min[i] = min;
			if (i > 2) for (int k = 0; k < qc.length; k++) qc[k] -= min;
			channel_init[i] = qc[0];
			channel_sum[i] = (int) Math.floor(CodeMapper.getShannonLimit(DeltaMapper.getIdealFrequency2(qc, new_xdim, new_ydim)));
		});
		computeSetSums();
		int min_sum = Integer.MAX_VALUE, min_idx = 0;
		for (int i = 0; i < 10; i++) if (set_sum[i] < min_sum) { min_sum = set_sum[i]; min_idx = i; }
		min_set_id = min_idx;
		printChannelSetRanking();
		final int[] channel_id = DeltaMapper.getChannels(min_set_id);

		// Survey the 7 delta types, the 3 channels in parallel. Each
		// channel writes only its own row; rows are summed afterwards.
		// Arrays are indexed 0-6 (delta_type - FIRST_DELTA_TYPE).
		final int[][]     per_channel_delta_bits       = new int[3][N_DELTA_TYPES];
		final int[][]     per_channel_map_bits         = new int[3][N_DELTA_TYPES];
		final boolean[][] per_channel_delta_compressed = new boolean[3][N_DELTA_TYPES];
		final boolean[][] per_channel_map_compressed   = new boolean[3][N_DELTA_TYPES];
		// Map-coding report (see printMapReport): sizes in bytes of each
		// channel's map as written, after compressStrings, after Deflate
		// and after Arithmetic, plus its order-0 entropy.
		final long[][][]  map_report = new long[3][N_DELTA_TYPES][MAP_REPORT_COLUMNS];
		parallel(3, i ->
		{
			int[] qc = qc6[channel_id[i]];
			for (int t = 0; t < N_DELTA_TYPES; t++)
			{
				ArrayList result = getDeltas(FIRST_DELTA_TYPE + t, qc, new_xdim, new_ydim);
				int[]  delta = (int[])  result.get(1);
				byte[] map   = (byte[]) result.get(2);

				byte[] delta_compressed_bytes = packAndCompress(delta);
				per_channel_delta_bits[i][t] = StringMapper.getBitlength(delta_compressed_bytes);
				per_channel_delta_compressed[i][t] = (StringMapper.getIterations(delta_compressed_bytes) & 15) > 0;

				int[] map_int = new int[map.length];
				for (int k = 0; k < map.length; k++) map_int[k] = map[k] & 0xFF;
				byte[] map_compressed_bytes = packAndCompress(map_int);
				// Map types 9-12 are stored as the smaller of the string and
				// the arithmetic-coded block (see writeMap), so rank them on
				// that. (Types 6-8 are tiny raw 2-bit maps.)
				int string_map_bits = StringMapper.getBitlength(map_compressed_bytes);
				int arith_map_bytes = encodeMapArithmetic(map).length;
				per_channel_map_bits[i][t] = (FIRST_DELTA_TYPE + t >= 9) ? Math.min(string_map_bits, 8 * arith_map_bytes) : string_map_bits;
				per_channel_map_compressed[i][t] = (StringMapper.getIterations(map_compressed_bytes) & 15) > 0;

				long[] mr = map_report[i][t];
				mr[0] = map.length;
				mr[1] = writtenMapBytes(FIRST_DELTA_TYPE + t, map);
				mr[2] = (StringMapper.getBitlength(map_compressed_bytes) + 7) / 8;
				mr[3] = deflatedBytes(map);
				mr[4] = arith_map_bytes;
				mr[5] = (long) Math.ceil(entropyBits(map) / 8);
				mr[6] = (StringMapper.getBitlength(delta_compressed_bytes) + 7) / 8;
				// Context entropies: given the previous entry, and (for maps
				// with one entry per pixel) given the left and upper entries.
				mr[7] = (long) Math.ceil(conditionalEntropyBits(map, 0) / 8);
				mr[8] = (map.length == new_xdim * new_ydim) ? (long) Math.ceil(conditionalEntropyBits(map, new_xdim) / 8) : -1;
			}
		});

		int[]     delta_bits       = new int[N_DELTA_TYPES];
		int[]     map_bits         = new int[N_DELTA_TYPES];
		boolean[] delta_compressed = new boolean[N_DELTA_TYPES];
		boolean[] map_compressed   = new boolean[N_DELTA_TYPES];
		int[]     total_delta_sum  = new int[N_DELTA_TYPES];
		for (int t = 0; t < N_DELTA_TYPES; t++)
		{
			for (int i = 0; i < 3; i++)
			{
				delta_bits[t]       += per_channel_delta_bits[i][t];
				map_bits[t]         += per_channel_map_bits[i][t];
				delta_compressed[t] |= per_channel_delta_compressed[i][t];
				map_compressed[t]   |= per_channel_map_compressed[i][t];
			}
			total_delta_sum[t] = delta_bits[t] + map_bits[t];
		}
		int best = 0;
		for (int t = 1; t < N_DELTA_TYPES; t++) if (total_delta_sum[t] < total_delta_sum[best]) best = t;
		delta_type = FIRST_DELTA_TYPE + best;
		printDeltaTypeRanking(delta_bits, map_bits, delta_compressed, map_compressed, total_delta_sum);
		printMapReport(map_report);
	}

	// ---- Map-coding report ---------------------------------------------------
	// Printed after the delta-type ranking. For each of the 7 types, summed
	// over the 3 channels: map entries, then the map's size in bytes
	//   written  -- as Save writes it (raw 2-bit for types 6-8; for 9-12
	//               the smaller of the string or arithmetic block, plus
	//               the one-byte flag);
	//   strings  -- after StringMapper.compressStrings (what the survey
	//               ranking above counts);
	//   deflate  -- one byte per entry, Deflate at BEST_COMPRESSION;
	//   arith    -- the arithmetic-coded map block as writeMap would store
	//               it: one ArithmeticMapper block plus its value/count table;
	//   H0       -- order-0 entropy, the floor for any coder that looks at
	//               each entry on its own (Deflate can beat it on runs);
	//   H|L      -- entropy of each entry given the previous one;
	//   H|LU     -- entropy given the left AND upper entries, for maps with
	//               one entry per pixel ("-" otherwise). These are what a
	//               context-modeling coder could approach; they're a bit
	//               optimistic, since a real coder has to learn or store
	//               the per-context statistics;
	// and, for scale, the compressed delta string. Report only -- nothing
	// it measures changes the saved file.
	static final int MAP_REPORT_COLUMNS = 9;

	private void printMapReport(long[][][] map_report)
	{
		System.out.println("Map coding (bytes, 3 channels; smallest real coder marked <):");
		System.out.println(String.format("      %-16s %10s %10s %10s %10s %10s %10s %10s %10s %12s",
			"", "entries", "written", "strings", "deflate", "arith", "H0", "H|L", "H|LU", "delta"));
		for (int t = 0; t < N_DELTA_TYPES; t++)
		{
			long[] sum = new long[MAP_REPORT_COLUMNS];
			for (int i = 0; i < 3; i++)
				for (int c = 0; c < MAP_REPORT_COLUMNS; c++) sum[c] += map_report[i][t][c];
			int best = 1;
			for (int c = 2; c <= 4; c++) if (sum[c] < sum[best]) best = c;
			String[] cell = new String[5];
			for (int c = 1; c <= 4; c++) cell[c] = sum[c] + (c == best ? "<" : " ");
			String hlu = (sum[8] < 0) ? "-" : String.valueOf(sum[8]);
			System.out.println(String.format("      %-16s %10d %11s%11s%11s%11s %10d %10d %10s %12d",
				delta_type_string[t], sum[0], cell[1], cell[2], cell[3], cell[4], sum[5], sum[7], hlu, sum[6]));
		}
		System.out.println();
	}

	// Bytes writeMap would write for this map (including its small header).
	private int writtenMapBytes(int type, byte[] map)
	{
		try
		{
			ByteArrayOutputStream bytes = new ByteArrayOutputStream();
			DataOutputStream out = new DataOutputStream(bytes);
			writeMap(out, type, map);
			out.flush();
			return bytes.size();
		}
		catch (IOException e) { return -1; }
	}

	private static int deflatedBytes(byte[] data)
	{
		Deflater def = new Deflater(Deflater.BEST_COMPRESSION);
		def.setInput(data); def.finish();
		byte[] buf = new byte[data.length + 1024];
		int n = 0;
		while (!def.finished()) n += def.deflate(buf);
		def.end();
		return n;
	}

	// Empirical conditional entropy (bits) of each entry given its context:
	// the previous entry when width == 0, else the left and upper entries
	// of a width-wide grid. Entries without a full context (the first
	// entry, or the first row/column) get their own context value.
	private static double conditionalEntropyBits(byte[] data, int width)
	{
		// Map the values actually used to 0..K-1 so the tables stay small.
		int[] index = new int[256]; java.util.Arrays.fill(index, -1);
		int K = 0;
		for (byte b : data) if (index[b & 0xFF] < 0) index[b & 0xFF] = K++;
		if (K <= 1) return 0;
		int C = (width == 0) ? K + 1 : (K + 1) * (K + 1);   // +1 for "no neighbour"
		int[][] count = new int[C][K];
		for (int k = 0; k < data.length; k++)
		{
			int left = (k == 0 || (width > 0 && k % width == 0)) ? K : index[data[k-1] & 0xFF];
			int ctx;
			if (width == 0) ctx = left;
			else
			{
				int up = (k < width) ? K : index[data[k-width] & 0xFF];
				ctx = left * (K + 1) + up;
			}
			count[ctx][index[data[k] & 0xFF]]++;
		}
		double bits = 0;
		for (int[] row : count)
		{
			long n = 0; for (int v : row) n += v;
			for (int v : row) if (v > 0) bits -= v * (Math.log((double) v / n) / Math.log(2));
		}
		return bits;
	}

	private static double entropyBits(byte[] data)
	{
		int[] freq = new int[256];
		for (byte b : data) freq[b & 0xFF]++;
		double bits = 0, n = data.length;
		for (int v : freq) if (v > 0) bits -= v * (Math.log(v / n) / Math.log(2));
		return bits;
	}

	// Deltas for one of the 7 delta types: result.get(1) is the int[]
	// deltas, result.get(2) the byte[] map.
	private ArrayList getDeltas(int type, int[] qc, int xdim, int ydim)
	{
		if      (type == 6)  return DeltaMapper.getMixedDeltasFromValues(qc, xdim, ydim);                         // scanline 1
		else if (type == 7)  return DeltaMapper.getMixedDeltasFromValues2(qc, xdim, ydim);                        // scanline 2
		else if (type == 8)  return DeltaMapper.getMixedDeltasFromValues4(qc, xdim, ydim);                        // scanline 3
		else if (type == 9)  return DeltaMapper.getMixedDeltasFromValues16Rows(qc, xdim, ydim);                   // scanline 4
		else if (type == 10) return DeltaMapper.getMixedDeltasFromValues8Rows(qc, xdim, ydim, scanline5_variant); // scanline 5
		else if (type == 11) return DeltaMapper.getIdealDeltasFromValues8(qc, xdim, ydim);                        // frame map 1
		else                 return DeltaMapper.getIdealDeltasFromValues16(qc, xdim, ydim);                       // frame map 2
	}

	// Inverse of getDeltas.
	private int[] getValues(int type, int[] delta, int xdim, int ydim, int init, byte[] map)
	{
		if      (type == 6)  return DeltaMapper.getValuesFromMixedDeltas(delta, xdim, ydim, init, map);
		else if (type == 7)  return DeltaMapper.getValuesFromMixedDeltas2(delta, xdim, ydim, init, map);
		else if (type == 8)  return DeltaMapper.getValuesFromMixedDeltas4(delta, xdim, ydim, init, map);
		else if (type == 9)  return DeltaMapper.getValuesFromMixedDeltas16Rows(delta, xdim, ydim, init, map);
		else if (type == 10) return DeltaMapper.getValuesFromMixedDeltas8Rows(delta, xdim, ydim, init, map, scanline5_variant);
		else if (type == 11) return DeltaMapper.getValuesFromIdealDeltas8(delta, xdim, ydim, init, map);
		else                 return DeltaMapper.getValuesFromIdealDeltas16(delta, xdim, ydim, init, map);
	}

	public DeltaWriter2(String _filename)
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
			string_list  = new ArrayList<Object>(); map_list   = new ArrayList<Object>();

			channel_string    = new String[]{"blue","green","red","blue-green","red-green","red-blue"};
			set_sum    = new int[10];
			set_string = new String[]{"blue, green, red","blue, red, red-green","blue, red, blue-green","blue, blue-green, red-green","blue, blue-green, red-blue","green, red, blue-green","red, blue-green, red-green","green, blue-green, red-green","green, red-green, red-blue","red, red-green, red-blue"};
			delta_type_string = new String[]{"scanline (1)","scanline (2)","scanline (3)","scanline (4)","scanline (5)","frame map (1)","frame map (2)"};
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
				working_image.setRGB(0, 0, image_xdim, image_ydim, pixel, 0, image_xdim);

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

				frame = new JFrame("Delta Writer 2  " + filename);
				openWindowCount++;
				frame.addWindowListener(new WindowAdapter() { public void windowClosing(WindowEvent e) { frame.dispose(); if(--openWindowCount==0)System.exit(0); }});
				frame.getContentPane().add(scroll_pane, BorderLayout.CENTER);

				JMenuBar menu_bar = new JMenuBar();
				JMenu file_menu = new JMenu("File");
				JMenuItem open_item = new JMenuItem("Open...");
				open_item.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_O, InputEvent.CTRL_DOWN_MASK));
				open_item.addActionListener(e -> { FileDialog fd=new FileDialog(frame,"Open Image",FileDialog.LOAD); fd.setVisible(true); if(fd.getFile()!=null) new DeltaWriter2(fd.getDirectory()+fd.getFile()); });
				file_menu.add(open_item); file_menu.addSeparator();
				JMenuItem reset_item = new JMenuItem("Reset");
				reset_item.addActionListener(e -> { pixel_quant=0;pixel_shift=0;correction=0; if(pquant_slider!=null)pquant_slider.setValue(0); if(pshift_slider!=null)pshift_slider.setValue(0); if(corr_slider!=null)corr_slider.setValue(0); new ApplyHandler().actionPerformed(null); });
				file_menu.add(reset_item);
				JMenuItem save_item = new JMenuItem("Save"); save_item.addActionListener(new SaveHandler()); file_menu.add(save_item);

				JMenu view_menu = new JMenu("View");
				JMenuItem zi=new JMenuItem("Zoom In"); zi.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_EQUALS,InputEvent.CTRL_DOWN_MASK)); zi.addActionListener(e->zoomBy(ZOOM_FACTOR)); view_menu.add(zi);
				JMenuItem zo=new JMenuItem("Zoom Out"); zo.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_MINUS,InputEvent.CTRL_DOWN_MASK)); zo.addActionListener(e->zoomBy(1.0/ZOOM_FACTOR)); view_menu.add(zo);
				JMenuItem zf=new JMenuItem("Fit"); zf.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_0,InputEvent.CTRL_DOWN_MASK)); zf.addActionListener(e->{ Dimension vps=scroll_pane.getViewport().getSize(); zoom_scale=Math.min((double)vps.width/image_xdim,(double)vps.height/image_ydim); updateDisplayImage(); image_canvas.setPreferredSize(new Dimension((int)(image_xdim*zoom_scale),(int)(image_ydim*zoom_scale))); image_canvas.revalidate(); image_canvas.repaint(); updateTitle(); }); view_menu.add(zf);
				JMenuItem za=new JMenuItem("100%"); za.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_1,InputEvent.CTRL_DOWN_MASK)); za.addActionListener(e->{ zoom_scale=hidpi_scale; updateDisplayImage(); image_canvas.setPreferredSize(new Dimension((int)(image_xdim*zoom_scale),(int)(image_ydim*zoom_scale))); image_canvas.revalidate(); image_canvas.repaint(); updateTitle(); }); view_menu.add(za);

				JMenu quant_menu = new JMenu("Quantization");
				JSlider[] ss = new JSlider[1];
				quant_menu.add(makeSliderDialog(frame,"Pixel Resolution",0,10,pixel_quant,v->{pixel_quant=v;new ApplyHandler().actionPerformed(null);},ss)); pquant_slider=ss[0];
				quant_menu.add(makeSliderDialog(frame,"Color Resolution",0,7,pixel_shift,v->{pixel_shift=v;new ApplyHandler().actionPerformed(null);},ss)); pshift_slider=ss[0];
				// Error Correction sits below a separator: it isn't a form of
				// quantization, just a way to see how far the round trip
				// strays from the original.
				quant_menu.addSeparator();
				quant_menu.add(makeSliderDialog(frame,"Error Correction",0,10,correction,v->{correction=v;new ApplyHandler().actionPerformed(null);},ss)); corr_slider=ss[0];

				JMenu delta_menu = new JMenu("Delta");
				delta_button=new JRadioButtonMenuItem[N_DELTA_TYPES]; ButtonGroup dg=new ButtonGroup();
				for(int i=0;i<N_DELTA_TYPES;i++){delta_button[i]=new JRadioButtonMenuItem(DELTA_MENU_NAMES[i]);dg.add(delta_button[i]);delta_menu.add(delta_button[i]);final int dt=FIRST_DELTA_TYPE+i;delta_button[i].addActionListener(e->{if(delta_type!=dt){delta_type=dt;new ApplyHandler().actionPerformed(null);}});}
				delta_button[delta_type-FIRST_DELTA_TYPE].setSelected(true);

				JMenu entropy_menu = new JMenu("Entropy");
				entropy_button=new JRadioButtonMenuItem[3];
				entropy_button[0]=new JRadioButtonMenuItem("LZ77"); entropy_button[1]=new JRadioButtonMenuItem("Huffman");
				entropy_button[2]=new JRadioButtonMenuItem("Arithmetic");
				ButtonGroup eg=new ButtonGroup();
				for(int i=0;i<3;i++){eg.add(entropy_button[i]);entropy_menu.add(entropy_button[i]);}
				for(int i=0;i<3;i++){entropy_button[i].setSelected(entropy_type==i);}
				for(int i=0;i<3;i++){final int et=i;entropy_button[i].addActionListener(e->{if(entropy_type!=et)entropy_type=et;});}

				// Segment size for the Arithmetic entropy type (SaveHandler's
				// `min_seg = 500 + pixel_segment*500`, up to pixel_segment=10
				// forcing a single unsegmented chunk). Only used at Save, so
				// it doesn't call ApplyHandler.
				entropy_menu.addSeparator();
				entropy_menu.add(makeSliderDialog(frame,"Segment Size",0,10,pixel_segment,v->{pixel_segment=v;},ss)); segment_slider=ss[0];

				menu_bar.add(file_menu); menu_bar.add(view_menu); menu_bar.add(quant_menu);
				menu_bar.add(delta_menu); menu_bar.add(entropy_menu);
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
				System.out.println("Unsupported image color model (raster_type=" + raster_type
					+ ", expected TYPE_3BYTE_BGR=" + BufferedImage.TYPE_3BYTE_BGR + "). No window was created.");
			}
		}
		catch(Exception e){e.printStackTrace();System.exit(1);}
	}

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
		pixel_quant=4;pixel_shift=3;correction=0;
		if(pquant_slider!=null)pquant_slider.setValue(4); if(pshift_slider!=null)pshift_slider.setValue(3); if(corr_slider!=null)corr_slider.setValue(0);
		new ApplyHandler().actionPerformed(null);
		new javax.swing.SwingWorker<Void,Void>()
		{
			@Override protected Void doInBackground(){init();return null;}
			@Override protected void done()
			{
				delta_button[delta_type-FIRST_DELTA_TYPE].setSelected(true);
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

	private void updateTitle(){frame.setTitle("Delta Writer 2  "+filename+"  ["+(int)Math.round(zoom_scale*100)+"%]");}

	private static void writeTable(DataOutputStream out,int[] table) throws IOException
	{
		out.writeShort(table.length);
		int max=Byte.MAX_VALUE*2+1;
		if(table.length<=max)for(int v:table)out.writeByte(v);else for(int v:table)out.writeShort(v);
	}

	// Formats a duration with at most three whole digits and three
	// fraction digits, in the smallest unit that fits (ns/usecs/ms/secs/min).
	private static String formatDuration(long nanos)
	{
		String[] units = { "ns", "usecs", "ms", "secs", "min" };
		double[] divisors = { 1.0, 1e3, 1e6, 1e9, 60e9 };

		int idx = units.length - 1;
		for (int i = 0; i < units.length; i++)
		{
			double v = nanos / divisors[i];
			if (v < 1000.0) { idx = i; break; }
		}

		double value = nanos / divisors[idx];
		if (value >= 999.9995 && idx < units.length - 1)
		{
			idx++;
			value = nanos / divisors[idx];
		}

		return String.format("%.3f %s", value, units[idx]);
	}

	// Right-shifts channel by pixel_shift with rounding to nearest,
	// clamped so a quantization index never reconstructs past 255.
	// Returns a new array and never modifies `channel` (a shared
	// channel_list reference) -- see DeltaWriter's bugs #1 and #3.
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

	// Packs and compresses an int[] array (deltas, or a map widened to
	// int), returning the compressed StringMapper bit string.
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

	// Prints the ranked channel-set table, marking the one init() selected.
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

	// Prints the ranked delta-type table: delta bits and map bits (each
	// with a * if compressStrings really compressed it) and their total,
	// marking the type init() selected. Arrays are indexed 0-6.
	private void printDeltaTypeRanking(int[] delta_bits, int[] map_bits,
	                                    boolean[] delta_compressed, boolean[] map_compressed,
	                                    int[] total_delta_sum)
	{
		Integer[] order = new Integer[N_DELTA_TYPES];
		for (int i = 0; i < N_DELTA_TYPES; i++) order[i] = i;
		java.util.Arrays.sort(order, (a, b) -> total_delta_sum[a] - total_delta_sum[b]);
		System.out.println("Delta types (ranked):");
		for (int r = 0; r < N_DELTA_TYPES; r++)
		{
			int idx = order[r];
			String dc  = delta_compressed[idx] ? "*" : " ";
			String mc  = map_compressed[idx]   ? "*" : " ";
			String sel = (FIRST_DELTA_TYPE + idx == delta_type) ? " **" : "";
			System.out.println(String.format("  %2d. %-16s delta: %12d%s      map: %12d%s      total: %12d%s",
				r + 1, delta_type_string[idx], delta_bits[idx], dc, map_bits[idx], mc, total_delta_sum[idx], sel));
		}
		System.out.println();
	}

	// DeltaReader expects two different map formats: raw 2-bit-packed
	// (no table) for delta_type 6-8, and a StringMapper string (with a
	// table) for delta_type 9-12.
	private static void writeMapRaw2Bit(DataOutputStream out,byte[] map) throws IOException
	{
		// Matches DeltaReader: map_raw[q] = (pm[q>>2] >> ((q&3)<<1)) & 0x3
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

	private static void writeMapStringMapper(DataOutputStream out,byte[] map) throws IOException
	{
		int[] map_int=new int[map.length];
		for(int q=0;q<map.length;q++)map_int[q]=map[q]&0xFF;
		ArrayList dsl=StringMapper.getStringList(map_int,false);
		int dmin=(int)dsl.get(0); int[] tbl=(int[])dsl.get(2); byte[] str=(byte[])dsl.get(3); int bl=StringMapper.getBitlength(str);
		out.writeInt(map.length); writeTable(out,tbl); out.writeInt(dmin); out.writeInt(bl); out.write(str,0,StringMapper.getBytelength(bl));
	}

	private void writeMap(DataOutputStream out,int i) throws IOException
	{
		writeMap(out,delta_type,(byte[])map_list.get(i));
	}

	// Types 6-8: raw 2-bit map, as DeltaReader always expects. Types 9-12:
	// a flag byte (0 = unary string, 1 = arithmetic), then whichever of the
	// two is smaller. The flag is only present because COMPRESS_TYPE is 3.
	private static void writeMap(DataOutputStream out,int type,byte[] map) throws IOException
	{
		if(type<=8){writeMapRaw2Bit(out,map);return;}
		ByteArrayOutputStream string_form=new ByteArrayOutputStream();
		writeMapStringMapper(new DataOutputStream(string_form),map);
		byte[] arith_form=encodeMapArithmetic(map);
		if(arith_form.length<string_form.size()){out.writeByte(1);out.write(arith_form);}
		else{out.writeByte(0);string_form.writeTo(out);}
	}

	// Arithmetic-coded map block, as DeltaReader reads it:
	//   int   map length
	//   short K, the number of distinct values
	//   K x { byte value, int count }
	//   int   encoded length, then the ArithmeticMapper.getIntervalValueFast
	//         bytes (0 bytes when K <= 1 -- the table says it all)
	private static byte[] encodeMapArithmetic(byte[] map)
	{
		try
		{
			int[] freq=new int[256];
			for(byte b:map)freq[b&0xFF]++;
			int K=0;for(int v:freq)if(v>0)K++;
			ByteArrayOutputStream bytes=new ByteArrayOutputStream();
			DataOutputStream out=new DataOutputStream(bytes);
			out.writeInt(map.length);
			out.writeShort(K);
			for(int v=0;v<256;v++)if(freq[v]>0){out.writeByte(v);out.writeInt(freq[v]);}
			if(K<=1)out.writeInt(0);
			else{byte[] enc=ArithmeticMapper.getIntervalValueFast(map,freq);out.writeInt(enc.length);out.write(enc);}
			out.flush();
			return bytes.toByteArray();
		}
		catch(IOException e){throw new UncheckedIOException(e);}
	}

	// Runs body(0) .. body(n-1) in parallel on the shared thread pool and
	// waits for all of them.
	private static void parallel(int n,java.util.function.IntConsumer body)
	{
		java.util.stream.IntStream.range(0,n).parallel().forEach(body);
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
			final int new_xdim, new_ydim;
			if(pixel_quant!=0){double f=pixel_quant/10.0;new_xdim=image_xdim-(int)(f*(image_xdim/2-2));new_ydim=image_ydim-(int)(f*(image_ydim/2-2));}
			else{new_xdim=image_xdim;new_ydim=image_ydim;}

			// ---- Resize and quantize the 3 base channels (in parallel) ----
			final int[][] qc6=new int[6][];
			parallel(3,i->{
				int[] ch=(int[])channel_list.get(i);
				if(pixel_quant==0)qc6[i]=quantizeChannel(ch,pixel_shift);
				else qc6[i]=quantizeChannel(ResizeMapper.resize(ch,image_xdim,new_xdim,new_ydim),pixel_shift);
			});
			qc6[3]=DeltaMapper.getDifference(qc6[0],qc6[1]);
			qc6[4]=DeltaMapper.getDifference(qc6[2],qc6[1]);
			qc6[5]=DeltaMapper.getDifference(qc6[2],qc6[0]);

			// ---- Offsets and entropy estimates for all 6 candidates (in parallel) ----
			parallel(6,i->{
				int[] qc=qc6[i];int min=256;for(int v:qc)if(v<min)min=v;
				channel_min[i]=min;if(i>2)for(int k=0;k<qc.length;k++)qc[k]-=min;
				channel_init[i]=qc[0];
				channel_sum[i]=(int)Math.floor(CodeMapper.getShannonLimit(DeltaMapper.getIdealFrequency2(qc,new_xdim,new_ydim)));
			});
			DeltaWriter2.this.computeSetSums();
			int min_sum=Integer.MAX_VALUE,min_idx=0;
			for(int i=0;i<10;i++)if(set_sum[i]<min_sum){min_sum=set_sum[i];min_idx=i;}
			min_set_id=min_idx;
			file_compression_rate=(double)file_length/(image_xdim*image_ydim*3);
			final int[] channel_id=DeltaMapper.getChannels(min_set_id);

			// ---- The 3 chosen channels, in parallel: deltas, string, then
			// decode back for the preview. Each channel writes only its own
			// slot; the shared lists are filled in channel order afterwards
			// (Save reads them by index). ----
			final int[][]  tables=new int[3][];
			final byte[][] strings=new byte[3][];
			final byte[][] maps=new byte[3][];
			final int[][]  decoded=new int[3][];
			parallel(3,i->{
				int j=channel_id[i];int[] qc=qc6[j];

				ArrayList result=getDeltas(delta_type,qc,new_xdim,new_ydim);
				int[]  delta=(int[])result.get(1);
				byte[] map=(byte[])result.get(2);
				maps[i]=map;

				// String*: getStringList keeps the string compressed only if
				// that beats StringMapper's threshold.
				ArrayList dsl=StringMapper.getStringList(delta,true);
				channel_delta_min[j]=(int)dsl.get(0);channel_length[j]=(int)dsl.get(1);
				tables[i]=(int[])dsl.get(2);strings[i]=(byte[])dsl.get(3);
				channel_compressed_length[j]=StringMapper.getBitlength(strings[i]);
				channel_iterations[i]=StringMapper.getIterations(strings[i]);

				// Decode back, exactly as DeltaReader will.
				byte[] str=StringMapper.decompressStrings(strings[i]);
				int[] d2=StringMapper.unpackStrings(str,tables[i],new_xdim*new_ydim,channel_length[j]);
				d2[0]=0;for(int k=1;k<d2.length;k++)d2[k]+=channel_delta_min[j];
				int[] ch=getValues(delta_type,d2,new_xdim,new_ydim,channel_init[j],map);

				if(j>2)for(int k=0;k<ch.length;k++)ch[k]+=channel_min[j];
				// No resize/shift here -- DeltaReader recombines the raw
				// small channels via set_id first, THEN resizes, THEN
				// shifts (see DeltaWriter's bug #2).
				decoded[i]=ch;
			});
			table_list.clear();string_list.clear();map_list.clear();
			for(int i=0;i<3;i++){table_list.add(tables[i]);string_list.add(strings[i]);map_list.add(maps[i]);}

			ArrayList<int[]> dqcl=new ArrayList<int[]>(Arrays.asList(decoded));
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

			// ---- Resize back to full size and undo the color shift (in parallel) ----
			final int[][] bgr={blue,green,red};
			parallel(3,c->{
				int[] v=bgr[c];
				if(pixel_quant!=0)v=ResizeMapper.resize(v,new_xdim,image_xdim,image_ydim);
				if(pixel_shift!=0)v=DeltaMapper.shift(v,pixel_shift);
				bgr[c]=v;
			});
			blue=bgr[0];green=bgr[1];red=bgr[2];

			int[] ob=(int[])channel_list.get(0),og=(int[])channel_list.get(1),or_=(int[])channel_list.get(2);
			int[] rgb=new int[image_xdim*image_ydim];
			for(int i=0;i<rgb.length;i++)
			{
				if(correction!=0){double f=correction/10.0;blue[i]+=(int)((ob[i]-blue[i])*f);green[i]+=(int)((og[i]-green[i])*f);red[i]+=(int)((or_[i]-red[i])*f);}
				rgb[i]=(blue[i]<<16)+(green[i]<<8)+red[i];
			}
			working_image.setRGB(0,0,image_xdim,image_ydim,rgb,0,image_xdim);
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
				// Same header as DeltaWriter, so DeltaReader reads it as is:
				// compress_type is always String*, pixel_pyramid 0, use_saddle 0.
				out.writeShort(image_xdim);out.writeShort(image_ydim);out.writeByte(pixel_shift);out.writeByte(pixel_quant);
				out.writeByte(min_set_id);out.writeByte(delta_type);out.writeByte(COMPRESS_TYPE);out.writeByte(entropy_type);out.writeByte(scanline5_variant);
				out.writeByte(0);out.writeByte(0);
				if(entropy_type==0||entropy_type==1)
				{
					// Entropy-code the 3 channels in parallel, then write them
					// in channel order.
					final byte[][] zipped=new byte[3][];final int[] zip_len=new int[3];
					final byte[][] huff_lengths=new byte[3][],huff_tables=new byte[3][],huff_coded=new byte[3][];
					long t0=System.nanoTime();
					parallel(3,i->{
						byte[] payload=(byte[])string_list.get(i);
						if(entropy_type==0)
						{
							Deflater def=new Deflater(Deflater.BEST_COMPRESSION);byte[] z=new byte[2*payload.length];
							def.setInput(payload);def.finish();zip_len[i]=def.deflate(z);def.end();
							zipped[i]=z;
						}
						else
						{
							// Regular Huffman (CodeMapper): one canonical code for the
							// channel's payload, stored as its 256 code lengths
							// (Deflated) followed by the coded payload.
							int[] freq=new int[256];for(byte v:payload)freq[v&0xFF]++;
							huff_lengths[i]=CodeMapper.getRegularHuffmanLength(freq);
							huff_coded[i]=CodeMapper.packRegularCode(payload,huff_lengths[i]);
							huff_tables[i]=CodeMapper.packRegularTables(new byte[][]{huff_lengths[i]},Deflater.BEST_COMPRESSION);
						}
					});
					long entropy_nanos=System.nanoTime()-t0;
					for(int i=0;i<3;i++)
					{
						int j=channel_id[i];
						out.writeInt(channel_min[j]);out.writeInt(channel_init[j]);out.writeInt(channel_delta_min[j]);
						out.writeInt(channel_length[j]);out.writeInt(channel_compressed_length[j]);out.writeByte(channel_iterations[i]);
						writeMap(out,i);
						writeTable(out,(int[])table_list.get(i));
						if(entropy_type==0)
						{
							out.writeInt(((byte[])string_list.get(i)).length);out.writeInt(zip_len[i]);out.write(zipped[i],0,zip_len[i]);
						}
						else
						{
							byte[] payload=(byte[])string_list.get(i);
							if(!Arrays.equals(payload,CodeMapper.unpackRegularCode(huff_coded[i],huff_lengths[i],payload.length)))
								System.out.println("WARNING: channel "+i+" Huffman payload does not decode back.");
							out.writeInt(huff_tables[i].length);out.write(huff_tables[i]);
							out.writeInt(huff_coded[i].length);out.write(huff_coded[i]);
						}
					}
					System.out.println("Entropy coding ["+(entropy_type==0?"LZ77":"Huffman")+"] took "+formatDuration(entropy_nanos));
				}
				else
				{
					byte[][] payloads=new byte[3][];int[] n_segs=new int[3];byte[][][] segs=new byte[3][][];int[][][] freqs=new int[3][][];
					for(int i=0;i<3;i++){payloads[i]=(byte[])string_list.get(i);int min_seg=500+pixel_segment*500;n_segs[i]=(pixel_segment>=10)?1:Math.max(1,payloads[i].length/min_seg);int seg_len=payloads[i].length/n_segs[i];int odd_len=seg_len+payloads[i].length%n_segs[i];segs[i]=new byte[n_segs[i]][];freqs[i]=new int[n_segs[i]][256];for(int m=0;m<n_segs[i];m++)segs[i][m]=new byte[m<n_segs[i]-1?seg_len:odd_len];int pos=0;for(int m=0;m<n_segs[i];m++)for(int nn=0;nn<segs[i][m].length;nn++){segs[i][m][nn]=payloads[i][pos];int p=payloads[i][pos];if(p<0)p+=256;freqs[i][m][p]++;pos++;}}
					byte[][][] fast_enc=new byte[3][][];for(int i=0;i<3;i++)fast_enc[i]=new byte[n_segs[i]][];
					// All blocks of all 3 channels encoded on the shared thread pool.
					long fast_arithmetic_t0=System.nanoTime();
					int total_blocks=n_segs[0]+n_segs[1]+n_segs[2];
					java.util.stream.IntStream.range(0,total_blocks).parallel().forEach(b->{
						int i=(b<n_segs[0])?0:(b<n_segs[0]+n_segs[1])?1:2;
						int m=b-((i==0)?0:(i==1)?n_segs[0]:n_segs[0]+n_segs[1]);
						fast_enc[i][m]=ArithmeticMapper.getIntervalValueFast(segs[i][m],freqs[i][m]);
					});
					System.out.println("Entropy coding [Arithmetic] took "+formatDuration(System.nanoTime()-fast_arithmetic_t0));
					int[] len_types=new int[3];byte[][] zip_freqs=new byte[3][];int[] zip_lens=new int[3];deflateFrequencies(n_segs,freqs,len_types,zip_freqs,zip_lens);
					for(int i=0;i<3;i++){int j=channel_id[i];out.writeInt(channel_min[j]);out.writeInt(channel_init[j]);out.writeInt(channel_delta_min[j]);out.writeInt(channel_length[j]);out.writeInt(channel_compressed_length[j]);out.writeByte(channel_iterations[i]);writeMap(out,i);writeTable(out,(int[])table_list.get(i));out.writeInt(n_segs[i]);out.writeInt(len_types[i]);out.writeInt(zip_lens[i]);out.write(zip_freqs[i],0,zip_lens[i]);for(int k=0;k<n_segs[i];k++){byte[] enc=fast_enc[i][k];out.writeInt(enc.length);out.write(enc,0,enc.length);}}
				}
				out.flush();out.close();
				File saved=new File("foo");double rate=(double)saved.length()/(image_xdim*image_ydim*3);
				System.out.println("Original compression rate: "+String.format("%.4f",file_compression_rate));
				System.out.println("Output  compression rate:  "+String.format("%.4f",rate));
			}
			catch(Exception e){System.out.println("SaveHandler exception: "+e);e.printStackTrace();}
		}

		private void deflateFrequencies(int[] n_segs,int[][][] freqs,int[] len_types,byte[][] zip_freqs,int[] zip_lens)
		{
			parallel(3,fi->{int[][] fr=freqs[fi];int ns=n_segs[fi];int fmax=0;for(int[]row:fr)for(int v:row)if(v>fmax)fmax=v;int lt=(fmax<Byte.MAX_VALUE*2+2)?0:(fmax<Short.MAX_VALUE*2+2)?1:2;len_types[fi]=lt;int bpe=(lt==0)?1:(lt==1)?2:4;byte[] fb=new byte[ns*256*bpe];for(int k=0;k<ns;k++)for(int m=0;m<256;m++){int v=fr[k][m];int base=k*256*bpe+m*bpe;for(int b=0;b<bpe;b++)fb[base+b]=(byte)(v>>(8*b));}Deflater def=new Deflater(Deflater.BEST_COMPRESSION);byte[] zf=new byte[fb.length+64];def.setInput(fb);def.finish();int zl=def.deflate(zf);def.end();zip_freqs[fi]=zf;zip_lens[fi]=zl;});
		}
	}
}
