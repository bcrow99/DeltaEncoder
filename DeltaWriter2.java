import java.awt.*;
import java.awt.event.*;
import java.awt.image.*;
import java.io.*;
import java.util.*;
import java.util.zip.*;
import javax.swing.*;

// DeltaWriter2 version 1.0
//
// A streamlined DeltaWriter. Files it saves are read by the regular
// DeltaReader -- the header and per-channel layout are the same, with
// compress_type 3 (see COMPRESS_TYPE), pixel_pyramid 0 and use_saddle off.
//
// Compared with DeltaWriter:
//   - no Datatype menu and no Integer path: deltas are always unary
//     strings from StringMapper.getStringList(delta, true), which keeps
//     the string compressed only if that beats StringMapper's threshold;
//   - the Quantization menu has just Pixel Resolution, Color Resolution
//     and Error Correction (no Smooth, Smooth2 or pyramid averaging);
//   - only the scanline, frame-map and block-map delta types (6-13), and the
//     startup survey only ranks those, followed by a map-coding report.
public class DeltaWriter2
{
	// File format: every file starts with FORMAT_ID ('D' = Delta format) and
	// FORMAT_VERSION. Bump FORMAT_VERSION in the writer and reader together
	// whenever the file layout or a coder's output changes.
	static final char FORMAT_ID      = 'D';
	static final int  FORMAT_VERSION = 1;

	// compress_type written to the header: String* (strings that don't
	// compress enough come back uncompressed from getStringList).
	// DeltaReader treats it like 2.
	static final byte COMPRESS_TYPE = 3;

	// The delta types this program uses, in menu order. The numbers are
	// the same delta_type values DeltaWriter/DeltaReader use.
	static final int      FIRST_DELTA_TYPE = 6;
	static final int      N_DELTA_TYPES    = 8;
	static final String[] DELTA_MENU_NAMES = {"Scanline 1","Scanline 2","Scanline 3","Scanline 4","Scanline 5","Map 1","Map 2","Block Map"};

	// ---- Image state --------------------------------------------------------
	ViewerSupport view;
	BufferedImage original_image;
	BufferedImage working_image;
	String        filename;
	int           image_xdim, image_ydim;
	int[][]       source;   // blue, green, red of the original

	// ---- Compression parameters ---------------------------------------------
	int pixel_quant   = 4;
	int pixel_shift   = 3;
	int pixel_segment = 10;  // Arithmetic blocks: 10 = one block per channel (default); lower = blocks of 500+500*pixel_segment bytes
	int correction    = 0;
	int min_set_id    = 0;
	int delta_type    = 6;   // 6-13 only (Scanline 1-5, Map 1-2, Block Map)
	int entropy_type  = 0;
	byte scanline5_variant = 0;
	int  block_size = DeltaMapper.BLOCK_DEFAULT;   // block map (delta type 13): block width/height
	int  block_set  = 0;                           // block map: predictor set (DeltaMapper.BLOCK_SET_NAMES)
	JSpinner       block_spinner;
	JRadioButton[] block_button = new JRadioButton[DeltaMapper.BLOCK_SET_NAMES.length];
	boolean        updating = false;               // set while the program moves the block controls

	JSlider pquant_slider, pshift_slider, corr_slider, segment_slider;
	JRadioButtonMenuItem[] delta_button, entropy_button;

	// ---- Results of the last Apply (read by Save) ---------------------------
	int[]  set_sum = new int[10], channel_sum = new int[6];
	int[]  channel_init = new int[6], channel_min = new int[6], channel_delta_min = new int[6];
	int[]  channel_length = new int[6], channel_compressed_length = new int[6];
	byte[] channel_iterations = new byte[3];
	int[][]  table  = new int[3][];
	byte[][] string = new byte[3][];
	int[][]  delta_list = new int[3][];   // the deltas themselves, for the Context type
	int      delta_xdim;                  // their row width
	byte[][] map    = new byte[3][];

	long    file_length;
	boolean applied = false;   // the last Apply finished; Save needs it

	public static void main(String[] args)
	{
		ViewerSupport.applyHiDpiFontScaleIfNeeded();
		if(args.length == 1) { new DeltaWriter2(args[0]); return; }
		FileDialog fd = new FileDialog((java.awt.Frame) null, "Open Image", FileDialog.LOAD);
		fd.setVisible(true);
		if(fd.getFile() != null) new DeltaWriter2(new File(fd.getDirectory(), fd.getFile()).getPath());
		else System.exit(0);
	}

	// Startup survey: picks the channel set, then ranks the 7 scanline/
	// frame-map delta types by the compressed size of their delta string
	// plus their map, and selects the smallest.
	public void init()
	{
		int[]   size = DeltaMapper.getQuantizedSize(image_xdim, image_ydim, pixel_quant);
		int     new_xdim = size[0], new_ydim = size[1];
		int[][] qc = quantizedChannels(size);
		ViewerSupport.parallel(6, i -> channel_sum[i] = (int) Math.floor(CodeMapper.getShannonLimit(DeltaMapper.getIdealFrequency2(qc[i], new_xdim, new_ydim))));
		computeSetSums();
		printChannelSetRanking();
		int[] channel_id = DeltaMapper.getChannels(min_set_id);

		// The 3 channels in parallel, each writing only its own row. Arrays
		// are indexed 0-6 (delta_type - FIRST_DELTA_TYPE).
		int[][]     per_channel_delta_bits       = new int[3][N_DELTA_TYPES];
		int[][]     per_channel_map_bits         = new int[3][N_DELTA_TYPES];
		boolean[][] per_channel_delta_compressed = new boolean[3][N_DELTA_TYPES];
		boolean[][] per_channel_map_compressed   = new boolean[3][N_DELTA_TYPES];
		long[][][]  map_report = new long[3][N_DELTA_TYPES][MAP_REPORT_COLUMNS];
		byte[][][]  maps       = new byte[3][N_DELTA_TYPES][];
		ViewerSupport.parallel(3, i ->
		{
			for(int t = 0; t < N_DELTA_TYPES; t++)
			{
				int    type   = FIRST_DELTA_TYPE + t;
				ArrayList result = DeltaMapper.getDeltas(qc[channel_id[i]], new_xdim, new_ydim, type, scanline5_variant, block_size, block_set);
				byte[] m      = (byte[]) result.get(2);

				byte[] delta_bytes = DeltaWriter.packAndCompress((int[]) result.get(1));
				per_channel_delta_bits[i][t]       = StringMapper.getBitlength(delta_bytes);
				per_channel_delta_compressed[i][t] = (StringMapper.getIterations(delta_bytes) & 15) > 0;

				maps[i][t] = m;
				byte[] map_bytes  = DeltaWriter.packAndCompress(DeltaWriter.widen(m));
				int    map_bits   = StringMapper.getBitlength(map_bytes);
				int    arith_size = arithmeticMapBytes(m);
				per_channel_map_bits[i][t]       = map_bits;
				per_channel_map_compressed[i][t] = (StringMapper.getIterations(map_bytes) & 15) > 0;

				long[] mr = map_report[i][t];
				mr[0] = m.length;
				mr[2] = (map_bits + 7) / 8;
				mr[3] = CodeMapper.deflate(m, Deflater.BEST_COMPRESSION).length;
				mr[4] = arith_size;
				mr[5] = (long) Math.ceil(entropyBits(m) / 8);
				mr[6] = (StringMapper.getBitlength(delta_bytes) + 7) / 8;
				mr[7] = (long) Math.ceil(conditionalEntropyBits(m, 0) / 8);
				// Left+up estimate for the 2-D maps: frame maps (interior
				// pixels, xdim - 2 across) and block maps (one entry per block,
				// after the 2 header bytes).
				if(type == 11 || type == 12)
					mr[8] = (long) Math.ceil(conditionalEntropyBits(m, new_xdim - 2) / 8);
				else if(type == 13)
					mr[8] = (long) Math.ceil(conditionalEntropyBits(Arrays.copyOfRange(m, 2, m.length), (new_xdim - 2 + m[0] - 1) / m[0]) / 8);
				else
					mr[8] = -1;
			}
		});

		// Maps for types 9-13 are stored in the smallest of the string,
		// arithmetic and context-coded forms (see DeltaMapper.writeMap), and
		// the context form uses the previous channel's map, so they are sized
		// here, once all three channels' maps exist. Types 6-8 are tiny raw
		// 2-bit maps, ranked on the string size.
		ViewerSupport.parallel(N_DELTA_TYPES, t ->
		{
			int type = FIRST_DELTA_TYPE + t;
			for(int i = 0; i < 3; i++)
			{
				int written = writtenMapBytes(type, maps[i][t], (i > 0) ? maps[i - 1][t] : null, new_xdim);
				map_report[i][t][1] = written;
				per_channel_map_bits[i][t] = 8 * written;
			}
		});

		int[]     delta_bits       = new int[N_DELTA_TYPES];
		int[]     map_bits         = new int[N_DELTA_TYPES];
		boolean[] delta_compressed = new boolean[N_DELTA_TYPES];
		boolean[] map_compressed   = new boolean[N_DELTA_TYPES];
		int[]     total            = new int[N_DELTA_TYPES];
		for(int t = 0; t < N_DELTA_TYPES; t++)
		{
			for(int i = 0; i < 3; i++)
			{
				delta_bits[t]       += per_channel_delta_bits[i][t];
				map_bits[t]         += per_channel_map_bits[i][t];
				delta_compressed[t] |= per_channel_delta_compressed[i][t];
				map_compressed[t]   |= per_channel_map_compressed[i][t];
			}
			total[t] = delta_bits[t] + map_bits[t];
		}
		int best = 0;
		for(int t = 1; t < N_DELTA_TYPES; t++) if(total[t] < total[best]) best = t;
		delta_type = FIRST_DELTA_TYPE + best;
		printDeltaTypeRanking(delta_bits, map_bits, delta_compressed, map_compressed, total);
		printMapReport(map_report);
		if(delta_type == 13) searchBlockSettings(qc, size, channel_id);
	}

	// ---- Block map settings --------------------------------------------------

	// Picks block_size and block_set for the current quantization by coding
	// with each candidate (DeltaMapper.findBestBlock, Context-coded sizes),
	// in the background with the other menus disabled, then applies.
	private void findBlockSettings()
	{
		view.setMenusEnabled(false);
		view.setStatus("finding block settings\u2026");
		new SwingWorker<Void,Void>()
		{
			@Override protected Void doInBackground()
			{
				int[]   size = DeltaMapper.getQuantizedSize(image_xdim, image_ydim, pixel_quant);
				int[][] qc   = quantizedChannels(size);
				searchBlockSettings(qc, size, DeltaMapper.getChannels(min_set_id));
				return null;
			}
			@Override protected void done()
			{
				try { get(); } catch(Exception e) { System.out.println("Find block settings: " + e); }
				view.setMenusEnabled(true);
				view.setStatus(null);
				showBlockSettings();
				apply();
			}
		}.execute();
	}

	private void searchBlockSettings(int[][] qc, int[] size, int[] channel_id)
	{
		long     start = System.nanoTime();
		int[][]  ch    = { qc[channel_id[0]], qc[channel_id[1]], qc[channel_id[2]] };
		long[][] bytes = new long[DeltaMapper.BLOCK_SET_NAMES.length][DeltaMapper.BLOCK_SEARCH_SIZES.length];
		int[]    best  = DeltaMapper.findBestBlock(ch, size[0], size[1], bytes);
		block_size = best[0]; block_set = best[1];
		System.out.print(DeltaMapper.getBlockTable(bytes, best));
		System.out.println("Block search took " + ViewerSupport.formatDuration(System.nanoTime() - start));
		System.out.println();
	}

	// Moves the block controls to block_size and block_set without
	// triggering an apply.
	private void showBlockSettings()
	{
		updating = true;
		block_spinner.setValue(block_size);
		block_button[block_set].setSelected(true);
		updating = false;
	}

	// The six candidate channels after resizing and quantizing (see
	// DeltaMapper.getCandidateChannels); sets channel_min and channel_init.
	private int[][] quantizedChannels(int[] size)
	{
		int[][] q = new int[3][];
		ViewerSupport.parallel(3, i ->
		{
			int[] ch = source[i];
			if(pixel_quant != 0) ch = ResizeMapper.resize(ch, image_xdim, size[0], size[1]);
			q[i] = DeltaMapper.quantizeChannel(ch, pixel_shift);
		});
		int[][] qc = DeltaMapper.getCandidateChannels(q[0], q[1], q[2], channel_min);
		for(int i = 0; i < 6; i++) channel_init[i] = qc[i][0];
		return qc;
	}

	// ---- Map-coding report ---------------------------------------------------
	// Printed after the delta-type ranking; the legend is printed with it.
	// Columns (sums over the 3 channels): map entries; actual sizes from
	// writeMap (with the previous channel's map, as Save writes it),
	// StringMapper, Deflate and the arithmetic form; entropy estimates (order
	// 0, given the left entry, given left and upper); and the delta string
	// size for scale. Report only.
	static final int MAP_REPORT_COLUMNS = 9;

	private void printMapReport(long[][][] map_report)
	{
		System.out.println("Map coding: size of each delta type's predictor map, in bytes, all 3 channels together");
		System.out.println("  map entries    how many predictor choices the map holds");
		System.out.println("  Actual coders (the smallest is marked <):");
		System.out.println("    as saved     what Save writes: the smallest of unary strings, arithmetic, and context coding");
		System.out.println("    strings      unary strings (StringMapper)");
		System.out.println("    deflate      one byte per entry, zip-style Deflate");
		System.out.println("    arithmetic   arithmetic coding with fixed frequencies");
		System.out.println("  Estimates (theoretical minimum if each entry is coded knowing only...):");
		System.out.println("    alone        ...how often each value occurs (arithmetic lands just above this;");
		System.out.println("                 Deflate can go below it on long runs)");
		System.out.println("    given left   ...plus the entry to its left");
		System.out.println("    given l+up   ...plus the entries to its left and above (frame and block maps only; \"-\" otherwise).");
		System.out.println("                 \"as saved\" can beat this: the context coder also uses the previous channel.");
		System.out.println("  deltas         the deltas' size (unary strings), for scale");
		System.out.println(String.format("  %-16s %11s |%10s %10s %10s %11s  |%10s %11s %11s  |%10s",
			"delta type", "map entries", "as saved", "strings", "deflate", "arithmetic", "alone", "given left", "given l+up", "deltas"));
		for(int t = 0; t < N_DELTA_TYPES; t++)
		{
			long[] sum = new long[MAP_REPORT_COLUMNS];
			for(int i = 0; i < 3; i++)
				for(int c = 0; c < MAP_REPORT_COLUMNS; c++) sum[c] += map_report[i][t][c];
			int best = 1;
			for(int c = 2; c <= 4; c++) if(sum[c] < sum[best]) best = c;
			String[] cell = new String[5];
			for(int c = 1; c <= 4; c++) cell[c] = sum[c] + (c == best ? "<" : " ");
			String hlu = (sum[8] < 0) ? "-" : String.valueOf(sum[8]);
			System.out.println(String.format("  %-16s %11d |%11s%11s%11s%11s  |%10d %11d %11s  |%10d",
				DeltaMapper.DELTA_TYPE_NAMES[FIRST_DELTA_TYPE + t], sum[0], cell[1], cell[2], cell[3], cell[4], sum[5], sum[7], hlu, sum[6]));
		}
		System.out.println();
	}

	// Bytes DeltaMapper.writeMap writes for this map.
	private static int writtenMapBytes(int type, byte[] m, byte[] previous, int xdim)
	{
		try
		{
			ByteArrayOutputStream bytes = new ByteArrayOutputStream();
			DeltaMapper.writeMap(new DataOutputStream(bytes), type, m, previous, xdim);
			return bytes.size();
		}
		catch(IOException e) { return -1; }
	}

	// Size of DeltaMapper.writeMap's arithmetic-coded form (without its flag
	// byte): int length, short K, K x (byte value, int count), int coded
	// length, then the coded bytes (none when K <= 1).
	private static int arithmeticMapBytes(byte[] m)
	{
		int[] freq = ArithmeticMapper.getFrequency(m);
		int   K    = 0;
		for(int v : freq) if(v > 0) K++;
		int coded = (K <= 1) ? 0 : ArithmeticMapper.getIntervalValueFastFenwick(m, freq).length;
		return 4 + 2 + 5 * K + 4 + coded;
	}

	// Empirical conditional entropy (bits) of each entry given its context:
	// the previous entry when width == 0, else the left and upper entries
	// of a width-wide grid. Entries without a full context (the first
	// entry, or the first row/column) get their own context value.
	private static double conditionalEntropyBits(byte[] data, int width)
	{
		// Map the values actually used to 0..K-1 so the tables stay small.
		int[] index = new int[256]; Arrays.fill(index, -1);
		int K = 0;
		for(byte b : data) if(index[b & 0xFF] < 0) index[b & 0xFF] = K++;
		if(K <= 1) return 0;
		int C = (width == 0) ? K + 1 : (K + 1) * (K + 1);   // +1 for "no neighbour"
		int[][] count = new int[C][K];
		for(int k = 0; k < data.length; k++)
		{
			int left = (k == 0 || (width > 0 && k % width == 0)) ? K : index[data[k - 1] & 0xFF];
			int ctx;
			if(width == 0) ctx = left;
			else
			{
				int up = (k < width) ? K : index[data[k - width] & 0xFF];
				ctx = left * (K + 1) + up;
			}
			count[ctx][index[data[k] & 0xFF]]++;
		}
		double bits = 0;
		for(int[] row : count)
		{
			long n = 0; for(int v : row) n += v;
			for(int v : row) if(v > 0) bits -= v * (Math.log((double) v / n) / Math.log(2));
		}
		return bits;
	}

	private static double entropyBits(byte[] data)
	{
		double bits = 0, n = data.length;
		for(int v : ArithmeticMapper.getFrequency(data)) if(v > 0) bits -= v * (Math.log(v / n) / Math.log(2));
		return bits;
	}

	public DeltaWriter2(String _filename)
	{
		filename = _filename;
		try
		{
			file_length    = new File(filename).length();
			original_image = ViewerSupport.readImage(filename);
		}
		catch(IOException e)
		{
			ViewerSupport.showError(null, e.getMessage());
			ViewerSupport.exitIfNoWindows();
			return;
		}
		image_xdim = original_image.getWidth();
		image_ydim = original_image.getHeight();

		int[] pixel = original_image.getRGB(0, 0, image_xdim, image_ydim, null, 0, image_xdim);
		source = new int[3][pixel.length];
		for(int i = 0; i < pixel.length; i++)
		{
			source[0][i] = (pixel[i] >> 16) & 0xff; source[1][i] = (pixel[i] >> 8) & 0xff; source[2][i] = pixel[i] & 0xff;
		}
		working_image = new BufferedImage(image_xdim, image_ydim, BufferedImage.TYPE_INT_RGB);
		working_image.setRGB(0, 0, image_xdim, image_ydim, pixel, 0, image_xdim);

		view = new ViewerSupport("Delta Writer 2  " + filename, image_xdim, image_ydim);
		JFrame   frame    = view.frame;
		JMenuBar menu_bar = frame.getJMenuBar();

		JMenu file_menu = new JMenu("File");
		JMenuItem open_item = new JMenuItem("Open...");
		open_item.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_O, InputEvent.CTRL_DOWN_MASK));
		open_item.addActionListener(e -> { FileDialog fd = new FileDialog(frame, "Open Image", FileDialog.LOAD); fd.setVisible(true); if(fd.getFile() != null) new DeltaWriter2(fd.getDirectory() + fd.getFile()); });
		file_menu.add(open_item); file_menu.addSeparator();
		JMenuItem reset_item = new JMenuItem("Reset");
		reset_item.addActionListener(e -> { pixel_quant = 0; pixel_shift = 0; correction = 0; pquant_slider.setValue(0); pshift_slider.setValue(0); corr_slider.setValue(0); apply(); });
		file_menu.add(reset_item);
		JMenuItem save_item = new JMenuItem("Save"); save_item.addActionListener(new SaveHandler()); file_menu.add(save_item);

		JMenu quant_menu = new JMenu("Quantization");
		JSlider[] ss = new JSlider[1];
		quant_menu.add(ViewerSupport.makeSliderDialog(frame, "Pixel Resolution", 0, 10, pixel_quant, v -> { pixel_quant = v; apply(); }, ss)); pquant_slider = ss[0];
		quant_menu.add(ViewerSupport.makeSliderDialog(frame, "Color Resolution", 0, 7, pixel_shift, v -> { pixel_shift = v; apply(); }, ss)); pshift_slider = ss[0];
		// Error Correction (below a separator) is not a quantizing step: it blends
		// the preview back toward the original by correction/10. Preview only;
		// the saved file is unaffected.
		quant_menu.addSeparator();
		quant_menu.add(ViewerSupport.makeSliderDialog(frame, "Error Correction", 0, 10, correction, v -> { correction = v; apply(); }, ss)); corr_slider = ss[0];

		JMenu delta_menu = new JMenu("Delta");
		delta_button = new JRadioButtonMenuItem[N_DELTA_TYPES]; ButtonGroup dg = new ButtonGroup();
		for(int i = 0; i < N_DELTA_TYPES; i++)
		{
			final int dt = FIRST_DELTA_TYPE + i;
			delta_button[i] = new JRadioButtonMenuItem(DELTA_MENU_NAMES[i]); dg.add(delta_button[i]); delta_menu.add(delta_button[i]);
			delta_button[i].addActionListener(e ->
			{
				if(delta_type == dt) return;
				delta_type = dt;
				if(dt == 13) findBlockSettings(); else apply();
			});
		}
		delta_button[delta_type - FIRST_DELTA_TYPE].setSelected(true);

		// Block map settings (delta type 13); a change re-applies only when
		// the block map is selected. Choosing Block Map, or Find Best Block
		// Settings, picks both by coding with each candidate (see
		// findBlockSettings).
		delta_menu.addSeparator();
		JSpinner[] sp = new JSpinner[1];
		delta_menu.add(ViewerSupport.makeSpinnerDialog(frame, "Block Size", DeltaMapper.BLOCK_MIN, DeltaMapper.BLOCK_MAX, block_size,
			v -> { block_size = v; if(delta_type == 13 && !updating) apply(); }, sp)); block_spinner = sp[0];
		delta_menu.add(ViewerSupport.makeRadioDialog(frame, "Block Predictors", DeltaMapper.BLOCK_SET_NAMES, block_set,
			v -> { block_set = v; if(delta_type == 13 && !updating) apply(); }, block_button));
		JMenuItem find_item = new JMenuItem("Find Best Block Settings");
		find_item.addActionListener(e -> findBlockSettings());
		delta_menu.add(find_item);

		// entropy_type equals the button index: 0 LZ77, 1 Huffman, 2 Arithmetic,
		// 3 Adaptive, 4 Context.
		JMenu entropy_menu = new JMenu("Entropy");
		entropy_button = new JRadioButtonMenuItem[DeltaWriter.ENTROPY_NAMES.length]; ButtonGroup eg = new ButtonGroup();
		for(int i = 0; i < DeltaWriter.ENTROPY_NAMES.length; i++)
		{
			final int et = i;
			entropy_button[i] = new JRadioButtonMenuItem(DeltaWriter.ENTROPY_NAMES[i]); eg.add(entropy_button[i]); entropy_menu.add(entropy_button[i]);
			entropy_button[i].setSelected(entropy_type == i);
			entropy_button[i].addActionListener(e -> entropy_type = et);
		}
		// Segment size for Arithmetic (see pixel_segment). Doesn't call
		// apply(): segmentation only happens at Save time.
		entropy_menu.addSeparator();
		entropy_menu.add(ViewerSupport.makeSliderDialog(frame, "Segment Size", 0, 10, pixel_segment, v -> pixel_segment = v, ss)); segment_slider = ss[0];

		menu_bar.add(file_menu); menu_bar.add(view.makeViewMenu()); menu_bar.add(quant_menu);
		menu_bar.add(delta_menu); menu_bar.add(entropy_menu);

		view.setImage(original_image);
		view.show();
		SwingUtilities.invokeLater(() -> showInitialImage());
	}

	// Applies the default quantization, then runs init() in the background
	// with the other menus disabled so Apply and Save can't overlap it.
	private void showInitialImage()
	{
		apply();
		view.setMenusEnabled(false);
		new SwingWorker<Void,Void>()
		{
			@Override protected Void doInBackground() { init(); return null; }
			@Override protected void done()
			{
				try { get(); } catch(Exception e) { System.out.println("init: " + e); }
				view.setMenusEnabled(true);
				delta_button[delta_type - FIRST_DELTA_TYPE].setSelected(true);
				showBlockSettings();
				apply();
			}
		}.execute();
	}

	private void computeSetSums()
	{
		for(int s = 0; s < 10; s++)
		{
			int[] c = DeltaMapper.getChannels(s);
			set_sum[s] = channel_sum[c[0]] + channel_sum[c[1]] + channel_sum[c[2]];
		}
		min_set_id = 0;
		for(int s = 1; s < 10; s++) if(set_sum[s] < set_sum[min_set_id]) min_set_id = s;
	}

	// Prints the ranked channel-set table, marking the selected set.
	private void printChannelSetRanking()
	{
		Integer[] order = new Integer[10];
		for(int i = 0; i < 10; i++) order[i] = i;
		Arrays.sort(order, (a, b) -> set_sum[a] - set_sum[b]);
		System.out.println("Channel sets, smallest first: estimated bytes for each channel's deltas");
		System.out.println("(entropy estimate, before real coding), and the set's total. <= marks the set used.");
		System.out.println(String.format("      %-32s %10s %10s %10s %12s", "channel set", "1st", "2nd", "3rd", "total"));
		for(int r = 0; r < 10; r++)
		{
			int idx = order[r];
			int[] c = DeltaMapper.getChannels(idx);
			System.out.println(String.format("  %2d. %-32s %10d %10d %10d %12d%s", r + 1, DeltaMapper.SET_NAMES[idx],
				channel_sum[c[0]] / 8, channel_sum[c[1]] / 8, channel_sum[c[2]] / 8, set_sum[idx] / 8, (idx == min_set_id) ? "  <=" : ""));
		}
		System.out.println();
	}

	// Prints the ranked delta-type table in bytes: deltas, map and total,
	// marking the selected type. Arrays are indexed by delta_type -
	// FIRST_DELTA_TYPE.
	private void printDeltaTypeRanking(int[] delta_bits, int[] map_bits, boolean[] delta_compressed, boolean[] map_compressed, int[] total)
	{
		Integer[] order = new Integer[N_DELTA_TYPES];
		for(int i = 0; i < N_DELTA_TYPES; i++) order[i] = i;
		Arrays.sort(order, (a, b) -> total[a] - total[b]);
		System.out.println("Delta types, smallest first: bytes for the deltas (unary strings) and the predictor map");
		System.out.println("(as Save writes it; scanline maps are tiny), all 3 channels. <= marks the type chosen.");
		System.out.println(String.format("      %-16s %12s %12s %12s", "delta type", "deltas", "map", "total"));
		for(int r = 0; r < N_DELTA_TYPES; r++)
		{
			int idx = order[r];
			System.out.println(String.format("  %2d. %-16s %12d %12d %12d%s",
				r + 1, DeltaMapper.DELTA_TYPE_NAMES[FIRST_DELTA_TYPE + idx], delta_bits[idx] / 8,
				map_bits[idx] / 8, total[idx] / 8, (FIRST_DELTA_TYPE + idx == delta_type) ? "  <=" : ""));
		}
		System.out.println();
	}

	// ---- Apply --------------------------------------------------------------

	// Quantizes, picks the channel set, codes the deltas as String* (what
	// Save writes), then decodes them the way DeltaReader does for the
	// preview.
	void apply()
	{
		applied = false;
		try { applyImpl(); applied = true; }
		catch(Exception e) { System.out.println("Apply: " + e); e.printStackTrace(); }
	}

	private void applyImpl()
	{
		int[]   size = DeltaMapper.getQuantizedSize(image_xdim, image_ydim, pixel_quant);
		int     new_xdim = size[0], new_ydim = size[1];
		int[][] qc = quantizedChannels(size);
		ViewerSupport.parallel(6, i -> channel_sum[i] = (int) Math.floor(CodeMapper.getShannonLimit(DeltaMapper.getIdealFrequency2(qc[i], new_xdim, new_ydim))));
		computeSetSums();
		int[] channel_id = DeltaMapper.getChannels(min_set_id);

		// The 3 chosen channels in parallel: deltas, string, then decode
		// back. Each writes only its own slot.
		int[][]  new_table  = new int[3][];
		byte[][] new_string = new byte[3][];
		byte[][] new_map    = new byte[3][];
		int[][]  new_delta  = new int[3][];
		int[][]  dc         = new int[3][];
		ViewerSupport.parallel(3, i ->
		{
			int j = channel_id[i];
			ArrayList result = DeltaMapper.getDeltas(qc[j], new_xdim, new_ydim, delta_type, scanline5_variant, block_size, block_set);
			new_map[i] = (byte[]) result.get(2);
			new_delta[i] = ((int[]) result.get(1)).clone();

			ArrayList dsl = StringMapper.getStringList((int[]) result.get(1), true);
			channel_delta_min[j] = (int) dsl.get(0); channel_length[j] = (int) dsl.get(1);
			new_table[i] = (int[]) dsl.get(2); new_string[i] = (byte[]) dsl.get(3);
			channel_compressed_length[j] = StringMapper.getBitlength(new_string[i]);
			channel_iterations[i] = StringMapper.getIterations(new_string[i]);

			byte[] str   = StringMapper.decompressStrings(new_string[i]);
			int[]  delta = StringMapper.unpackStrings(str, new_table[i], new_xdim * new_ydim, channel_length[j]);
			delta[0] = 0; for(int k = 1; k < delta.length; k++) delta[k] += channel_delta_min[j];
			dc[i] = DeltaMapper.getValuesFromDeltas(delta, new_xdim, new_ydim, channel_init[j], delta_type, new_map[i], scanline5_variant);
			if(j > 2) for(int k = 0; k < dc[i].length; k++) dc[i][k] += channel_min[j];
		});
		table = new_table; string = new_string; map = new_map;
		delta_list = new_delta; delta_xdim = new_xdim;

		// Like DeltaReader: recombine the channels first, then resize, then
		// shift. Resizing each channel before recombining gives different
		// results (ResizeMapper rounds down).
		int[][] bgr = DeltaMapper.getBlueGreenRed(min_set_id, dc[0], dc[1], dc[2]);
		ViewerSupport.parallel(3, c ->
		{
			if(pixel_quant != 0) bgr[c] = ResizeMapper.resize(bgr[c], new_xdim, image_xdim, image_ydim);
			if(pixel_shift != 0) bgr[c] = DeltaMapper.shift(bgr[c], pixel_shift);
		});

		int[] rgb = new int[image_xdim * image_ydim];
		double f = correction / 10.0;
		for(int i = 0; i < rgb.length; i++)
		{
			int b = bgr[0][i], g = bgr[1][i], r = bgr[2][i];
			if(correction != 0) { b += (int)((source[0][i] - b) * f); g += (int)((source[1][i] - g) * f); r += (int)((source[2][i] - r) * f); }
			rgb[i] = (b << 16) + (g << 8) + r;
		}
		working_image.setRGB(0, 0, image_xdim, image_ydim, rgb, 0, image_xdim);
		view.setImage(working_image);
	}

	// ---- Save ---------------------------------------------------------------

	class SaveHandler implements ActionListener
	{
		public void actionPerformed(ActionEvent event)
		{
			if(!applied) apply();
			if(!applied) { ViewerSupport.showError(view.frame, "Nothing saved: the last Apply failed."); return; }
			File file = new File("foo");
			try(DataOutputStream out = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(file))))
			{
				save(out);
			}
			catch(Exception e)
			{
				file.delete();
				ViewerSupport.showError(view.frame, "Save failed: " + e);
				e.printStackTrace();
				return;
			}
			int raw = image_xdim * image_ydim * 3;
			System.out.println("Original compression rate: " + String.format("%.4f", (double) file_length / raw));
			System.out.println("Output  compression rate:  " + String.format("%.4f", (double) file.length() / raw));
		}

		// DeltaWriter's layout with compress_type COMPRESS_TYPE, pixel_pyramid
		// 0 and use_saddle 0. Per channel: min, init, delta min, bit lengths,
		// iterations, map, string table (not for Context), then the
		// entropy-coded payload (see
		// DeltaWriter.entropyCode).
		private void save(DataOutputStream out) throws IOException
		{
			int[] channel_id = DeltaMapper.getChannels(min_set_id);
			out.writeByte(FORMAT_ID); out.writeByte(FORMAT_VERSION);
			out.writeShort(image_xdim); out.writeShort(image_ydim); out.writeByte(pixel_shift); out.writeByte(pixel_quant);
			out.writeByte(min_set_id); out.writeByte(delta_type); out.writeByte(COMPRESS_TYPE); out.writeByte(entropy_type); out.writeByte(scanline5_variant);
			out.writeByte(0); out.writeByte(0);

			long t0 = System.nanoTime();
			byte[][] coded = (entropy_type == 4) ? DeltaWriter.contextCode(delta_list, delta_xdim) : DeltaWriter.entropyCode(string, entropy_type, pixel_segment);
			System.out.println("Entropy coding [" + DeltaWriter.ENTROPY_NAMES[entropy_type] + "] took " + ViewerSupport.formatDuration(System.nanoTime() - t0));

			for(int i = 0; i < 3; i++)
			{
				int j = channel_id[i];
				out.writeInt(channel_min[j]); out.writeInt(channel_init[j]); out.writeInt(channel_delta_min[j]);
				out.writeInt(channel_length[j]); out.writeInt(channel_compressed_length[j]); out.writeByte(channel_iterations[i]);
				DeltaMapper.writeMap(out, delta_type, map[i], (i > 0) ? map[i - 1] : null, delta_xdim);
				if(entropy_type != 4) DeltaMapper.writeTable(out, table[i]);
				out.write(coded[i]);
			}
		}
	}
}
