import java.awt.*;
import java.awt.event.*;
import java.awt.image.*;
import java.io.*;
import java.util.*;
import java.util.zip.*;
import javax.swing.*;

// DeltaWriter version 1.0
public class DeltaWriter
{
	// File format: every file starts with FORMAT_ID ('D' = Delta format) and
	// FORMAT_VERSION. Bump FORMAT_VERSION in the writer and reader together
	// whenever the file layout or a coder's output changes.
	static final char FORMAT_ID      = 'D';
	static final int  FORMAT_VERSION = 1;

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
	int delta_type    = 5;
	int compress_type = 1;   // 0=Integer, 1=String, 2=String*
	int entropy_type  = 0;
	int smooth_level  = 0;
	int smooth2_level = 0;
	byte scanline5_variant = 0;
	int  block_size = DeltaMapper.BLOCK_DEFAULT;   // block map (delta type 13): block width/height
	int  block_set  = 0;                           // block map: predictor set (DeltaMapper.BLOCK_SET_NAMES)
	JSpinner       block_spinner;
	JRadioButton[] block_button = new JRadioButton[DeltaMapper.BLOCK_SET_NAMES.length];
	boolean        updating = false;               // set while the program moves the block controls

	// ---- Image pyramid (detail-preserving average/expand) -------------------
	// pixel_pyramid: number of shrink/expand levels (0 = none), capped at 2:
	// deeper levels produced block artifacts even with sign-bit correction.
	// Applied after every other quantizing step, only to the 3 selected
	// channels; channel-set selection still uses the full-resolution channels.
	//
	// use_saddle: use ImageMapper.expandGradientSaddle (adds the cross-
	// derivative term) instead of plain expandGradient at every expand step.
	//
	// Sign bits (one bit per pixel, restoring detail lost by averaging) are
	// applied at every level: level(k-1) is rebuilt from level(k) with
	// level(k)'s sign bits, and that corrected level becomes the reference
	// for the next. Each level's sign-bit map is 1/4 the size of the one
	// before it.
	int     pixel_pyramid = 0;
	boolean use_saddle    = false;

	JSlider smooth_slider, smooth2_slider, pquant_slider, pshift_slider, corr_slider, segment_slider;
	java.util.function.IntConsumer pyramid_setter;
	JCheckBox saddle_checkbox;
	JRadioButtonMenuItem[] delta_button, entropy_button;
	JRadioButton[] compress_button, int_radio_btns;
	JMenu          datatype_menu;   // greyed out for Context, which codes the deltas directly

	// ---- Results of the last Apply (read by Save) ---------------------------
	int[]  set_sum = new int[10], channel_sum = new int[6];
	int[]  channel_init = new int[6], channel_min = new int[6], channel_delta_min = new int[6];
	int[]  channel_length = new int[6], channel_compressed_length = new int[6];
	byte[] channel_iterations = new byte[3];
	int[][]       table    = new int[3][];       // string tables (compress_type > 0)
	byte[][]      payload  = new byte[3][];      // delta bytes (Integer) or unary strings
	int[][]       delta_list = new int[3][];     // the deltas themselves, for the Context type
	int           delta_xdim;                    // their row width
	byte[][]      map      = new byte[3][];      // delta-type maps (types 6-13)
	boolean[][][] sign_bit = new boolean[3][][]; // pyramid sign bits, one bitmap per level

	long    file_length;
	boolean applied = false;   // the last Apply finished; Save needs it

	static final String[] ENTROPY_NAMES = {"LZ77", "Huffman", "Arithmetic", "Adaptive", "Context"};

	public static void main(String[] args)
	{
		ViewerSupport.applyHiDpiFontScaleIfNeeded();
		if(args.length == 1) { new DeltaWriter(args[0]); return; }
		FileDialog fd = new FileDialog((java.awt.Frame) null, "Open Image", FileDialog.LOAD);
		fd.setVisible(true);
		if(fd.getFile() != null) new DeltaWriter(new File(fd.getDirectory(), fd.getFile()).getPath());
		else System.exit(0);
	}

	// Startup survey: picks the channel set, ranks the 14 delta types by
	// the compressed size of their deltas plus their map, then picks String
	// or String* for the selected type.
	public void init()
	{
		int[]   size = DeltaMapper.getQuantizedSize(image_xdim, image_ydim, pixel_quant);
		int     new_xdim = size[0], new_ydim = size[1];
		int[][] qc = quantizedChannels(size, false);
		ViewerSupport.parallel(6, i -> channel_sum[i] = (int) Math.floor(CodeMapper.getShannonLimit(DeltaMapper.getIdealFrequency2(qc[i], new_xdim, new_ydim))));
		computeSetSums();
		printChannelSetRanking();
		int[] channel_id = DeltaMapper.getChannels(min_set_id);

		// The 3 channels in parallel, each writing only its own row.
		int[][]     per_channel_delta_bits       = new int[3][DeltaMapper.DELTA_TYPES];
		int[][]     per_channel_map_bits         = new int[3][DeltaMapper.DELTA_TYPES];
		boolean[][] per_channel_delta_compressed = new boolean[3][DeltaMapper.DELTA_TYPES];
		boolean[][] per_channel_map_compressed   = new boolean[3][DeltaMapper.DELTA_TYPES];
		byte[][][]  maps                         = new byte[3][DeltaMapper.DELTA_TYPES][];
		ViewerSupport.parallel(3, i ->
		{
			for(int t = 0; t < DeltaMapper.DELTA_TYPES; t++)
			{
				ArrayList result = DeltaMapper.getDeltas(qc[channel_id[i]], new_xdim, new_ydim, t, scanline5_variant, block_size, block_set);
				byte[] delta_bytes = packAndCompress((int[]) result.get(1));
				per_channel_delta_bits[i][t]       = StringMapper.getBitlength(delta_bytes);
				per_channel_delta_compressed[i][t] = (StringMapper.getIterations(delta_bytes) & 15) > 0;
				if(DeltaMapper.hasMap(t))
				{
					maps[i][t] = (byte[]) result.get(2);
				}
			}
		});

		// Maps are ranked on what Save writes (DeltaMapper.writeMap), whose
		// context form uses the previous channel's map, so they are sized
		// once all three channels' maps exist.
		ViewerSupport.parallel(DeltaMapper.DELTA_TYPES, t ->
		{
			if(!DeltaMapper.hasMap(t)) return;
			for(int i = 0; i < 3; i++)
			{
				try
				{
					ByteArrayOutputStream bytes = new ByteArrayOutputStream();
					DeltaMapper.writeMap(new DataOutputStream(bytes), t, maps[i][t], (i > 0) ? maps[i - 1][t] : null, new_xdim);
					per_channel_map_bits[i][t] = 8 * bytes.size();
				}
				catch(IOException e) { per_channel_map_bits[i][t] = Integer.MAX_VALUE / 8; }
			}
		});

		int[]     delta_bits       = new int[DeltaMapper.DELTA_TYPES];
		int[]     map_bits         = new int[DeltaMapper.DELTA_TYPES];
		boolean[] delta_compressed = new boolean[DeltaMapper.DELTA_TYPES];
		boolean[] map_compressed   = new boolean[DeltaMapper.DELTA_TYPES];
		int[]     total            = new int[DeltaMapper.DELTA_TYPES];
		for(int t = 0; t < DeltaMapper.DELTA_TYPES; t++)
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
		for(int t = 1; t < DeltaMapper.DELTA_TYPES; t++) if(total[t] < total[best]) best = t;
		delta_type = best;
		printDeltaTypeRanking(delta_bits, map_bits, delta_compressed, map_compressed, total);

		// String or String*, whichever is smaller for the selected type.
		int[] str_bits = new int[3], str_star_bits = new int[3];
		ViewerSupport.parallel(3, i ->
		{
			int[] delta = (int[]) DeltaMapper.getDeltas(qc[channel_id[i]], new_xdim, new_ydim, delta_type, scanline5_variant, block_size, block_set).get(1);
			str_bits[i]      = StringMapper.getBitlength((byte[]) StringMapper.getStringList(delta.clone(), false).get(3));
			str_star_bits[i] = StringMapper.getBitlength((byte[]) StringMapper.getStringList(delta.clone(), true).get(3));
		});
		compress_type = (str_star_bits[0] + str_star_bits[1] + str_star_bits[2] < str_bits[0] + str_bits[1] + str_bits[2]) ? 2 : 1;
		if(delta_type == 13) searchBlockSettings(qc, size, channel_id);
	}

	// ---- Block map settings --------------------------------------------------

	// Picks block_size and block_set for the current settings by coding with
	// each candidate (DeltaMapper.findBestBlock, Context-coded sizes), in the
	// background with the other menus disabled, then applies.
	private void findBlockSettings()
	{
		view.setMenusEnabled(false);
		view.setStatus("finding block settings\u2026");
		new SwingWorker<Void,Void>()
		{
			@Override protected Void doInBackground()
			{
				int[]   size = DeltaMapper.getQuantizedSize(image_xdim, image_ydim, pixel_quant);
				int[][] qc   = quantizedChannels(size, true);
				searchBlockSettings(qc, size, DeltaMapper.getChannels(min_set_id));
				return null;
			}
			@Override protected void done()
			{
				try { get(); } catch(Exception e) { System.out.println("Find block settings: " + e); }
				view.setMenusEnabled(true);
				datatype_menu.setEnabled(entropy_type != 4);
				view.setStatus(null);
				showBlockSettings();
				apply();
			}
		}.execute();
	}

	// With a pixel pyramid the block map codes the top level, so the search
	// does too.
	private void searchBlockSettings(int[][] qc, int[] size, int[] channel_id)
	{
		long    start = System.nanoTime();
		int[]   top   = DeltaReader.getPyramidSize(size[0], size[1], pixel_pyramid);
		int[][] ch    = new int[3][];
		for(int i = 0; i < 3; i++)
			ch[i] = (pixel_pyramid == 0) ? qc[channel_id[i]] : shrinkPyramid(qc[channel_id[i]], size[0], size[1], new boolean[pixel_pyramid][]);
		long[][] bytes = new long[DeltaMapper.BLOCK_SET_NAMES.length][DeltaMapper.BLOCK_SEARCH_SIZES.length];
		int[]    best  = DeltaMapper.findBestBlock(ch, top[0], top[1], bytes);
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

	// The six candidate channels after smoothing (if asked), resizing and
	// quantizing (see DeltaMapper.getCandidateChannels); sets channel_min
	// and channel_init.
	private int[][] quantizedChannels(int[] size, boolean smooth)
	{
		int[][] q = new int[3][];
		ViewerSupport.parallel(3, i ->
		{
			int[] ch = source[i];
			if(smooth && smooth_level > 0)  ch = DeltaMapper.bilateralSmooth(ch, image_xdim, image_ydim, smooth_level);
			if(smooth && smooth2_level > 0) ch = DeltaMapper.anisotropicSmooth(ch, image_xdim, image_ydim, smooth2_level);
			if(pixel_quant != 0) ch = ResizeMapper.resize(ch, image_xdim, size[0], size[1]);
			q[i] = DeltaMapper.quantizeChannel(ch, pixel_shift);
		});
		int[][] qc = DeltaMapper.getCandidateChannels(q[0], q[1], q[2], channel_min);
		for(int i = 0; i < 6; i++) channel_init[i] = qc[i][0];
		return qc;
	}

	public DeltaWriter(String _filename)
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

		view = new ViewerSupport("Delta Writer  " + filename, image_xdim, image_ydim);
		JFrame   frame    = view.frame;
		JMenuBar menu_bar = frame.getJMenuBar();

		JMenu file_menu = new JMenu("File");
		JMenuItem open_item = new JMenuItem("Open...");
		open_item.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_O, InputEvent.CTRL_DOWN_MASK));
		open_item.addActionListener(e -> { FileDialog fd = new FileDialog(frame, "Open Image", FileDialog.LOAD); fd.setVisible(true); if(fd.getFile() != null) new DeltaWriter(fd.getDirectory() + fd.getFile()); });
		file_menu.add(open_item); file_menu.addSeparator();
		JMenuItem reset_item = new JMenuItem("Reset");
		reset_item.addActionListener(e ->
		{
			smooth_level = 0; smooth2_level = 0; pixel_quant = 0; pixel_shift = 0; correction = 0; pixel_pyramid = 0;
			smooth_slider.setValue(0); smooth2_slider.setValue(0); pquant_slider.setValue(0); pshift_slider.setValue(0); corr_slider.setValue(0); pyramid_setter.accept(0);
			apply();
		});
		file_menu.add(reset_item);
		JMenuItem save_item = new JMenuItem("Save"); save_item.addActionListener(new SaveHandler()); file_menu.add(save_item);

		JMenu quant_menu = new JMenu("Quantization");
		JSlider[] ss = new JSlider[1];
		quant_menu.add(ViewerSupport.makeSliderDialog(frame, "Smooth", 0, 10, smooth_level, v -> { smooth_level = v; apply(); }, ss)); smooth_slider = ss[0];
		quant_menu.add(ViewerSupport.makeSliderDialog(frame, "Smooth2", 0, 10, smooth2_level, v -> { smooth2_level = v; apply(); }, ss)); smooth2_slider = ss[0];
		quant_menu.add(ViewerSupport.makeSliderDialog(frame, "Pixel Resolution", 0, 10, pixel_quant, v -> { pixel_quant = v; apply(); }, ss)); pquant_slider = ss[0];
		quant_menu.add(ViewerSupport.makeSliderDialog(frame, "Color Resolution", 0, 7, pixel_shift, v -> { pixel_shift = v; apply(); }, ss)); pshift_slider = ss[0];
		java.util.function.IntConsumer[] ps = new java.util.function.IntConsumer[1];
		quant_menu.add(makePyramidDialog(frame, "Average", 0, 2, pixel_pyramid, use_saddle,
			v -> { pixel_pyramid = v; apply(); },
			v -> { use_saddle = v; apply(); }, ps)); pyramid_setter = ps[0];
		// Error Correction (below a separator) is not a quantizing step: it blends
		// the preview back toward the original by correction/10. Preview only;
		// the saved file is unaffected.
		quant_menu.addSeparator();
		quant_menu.add(ViewerSupport.makeSliderDialog(frame, "Error Correction", 0, 10, correction, v -> { correction = v; apply(); }, ss)); corr_slider = ss[0];

		// Two dialogs, each with an Integer and a String button (all four in
		// one group). Integer is disabled when a channel's range is too large.
		datatype_menu = new JMenu("Datatype");
		ButtonGroup cg = new ButtonGroup();
		JRadioButton int_a = new JRadioButton("Integer"), str_a = new JRadioButton("String");
		JRadioButton int_b = new JRadioButton("Integer"), str_b = new JRadioButton("String");
		cg.add(int_a); cg.add(str_a); cg.add(int_b); cg.add(str_b);
		compress_button = new JRadioButton[] {int_a, str_a};
		int_radio_btns  = new JRadioButton[] {int_a, int_b};
		(compress_type == 0 ? int_a : str_a).setSelected(true);
		for(JRadioButton b : int_radio_btns) b.addActionListener(e -> { if(compress_type != 0) { compress_type = 0; apply(); } });
		for(JRadioButton b : new JRadioButton[] {str_a, str_b}) b.addActionListener(e -> { if(compress_type == 0) { compress_type = 1; apply(); } });
		datatype_menu.add(makeButtonDialog(frame, "Integer", new JPanel(new GridLayout(2, 1, 4, 4)), int_a, str_a));
		datatype_menu.add(makeButtonDialog(frame, "String", new JPanel(), int_b, str_b));

		JMenu delta_menu = new JMenu("Delta");
		String[] dnames = {"H","V","Average","Med","Directional","Adaptive","Scanline 1","Scanline 2","Scanline 3","Scanline 4","Scanline 5","Map 1","Map 2","Block Map"};
		delta_button = new JRadioButtonMenuItem[DeltaMapper.DELTA_TYPES]; ButtonGroup dg = new ButtonGroup();
		for(int i = 0; i < DeltaMapper.DELTA_TYPES; i++)
		{
			final int dt = i;
			delta_button[i] = new JRadioButtonMenuItem(dnames[i]); dg.add(delta_button[i]); delta_menu.add(delta_button[i]);
			delta_button[i].addActionListener(e ->
			{
				if(delta_type == dt) return;
				delta_type = dt;
				if(dt == 13) findBlockSettings(); else apply();
			});
		}
		delta_button[delta_type].setSelected(true);

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
			entropy_button[i] = new JRadioButtonMenuItem(ENTROPY_NAMES[i]); eg.add(entropy_button[i]); entropy_menu.add(entropy_button[i]);
			entropy_button[i].setSelected(entropy_type == i);
			entropy_button[i].addActionListener(e -> { entropy_type = et; datatype_menu.setEnabled(et != 4); });
		}
		// Segment size for Arithmetic (see pixel_segment). Doesn't call
		// apply(): segmentation only happens at Save time.
		entropy_menu.addSeparator();
		entropy_menu.add(ViewerSupport.makeSliderDialog(frame, "Segment Size", 0, 10, pixel_segment, v -> pixel_segment = v, ss)); segment_slider = ss[0];

		menu_bar.add(file_menu); menu_bar.add(view.makeViewMenu()); menu_bar.add(quant_menu);
		menu_bar.add(delta_menu); menu_bar.add(datatype_menu); menu_bar.add(entropy_menu);

		view.setImage(original_image);
		view.show();
		SwingUtilities.invokeLater(() -> showInitialImage());
	}

	// A menu item opening a small dialog that holds two buttons.
	private static JMenuItem makeButtonDialog(JFrame parent, String title, JPanel panel, JRadioButton a, JRadioButton b)
	{
		JDialog dialog = new JDialog(parent, title);
		panel.add(a); panel.add(b); dialog.add(panel);
		JMenuItem item = new JMenuItem(title);
		item.addActionListener(e -> { Point p = parent.getLocation(); dialog.setLocation(p.x, p.y - 80); dialog.pack(); dialog.setVisible(true); });
		return item;
	}

	// Dialog for pixel_pyramid (lo-hi): a read-only value field with +/-
	// buttons, plus the "Use Saddle" checkbox. setter_ref receives a setter
	// the caller can use later (e.g. from Reset) to change the value.
	private JMenuItem makePyramidDialog(JFrame parent, String title, int lo, int hi, int init, boolean saddle_init,
	                                    java.util.function.IntConsumer on_value_change,
	                                    java.util.function.Consumer<Boolean> on_saddle_change,
	                                    java.util.function.IntConsumer[] setter_ref)
	{
		JMenuItem item = new JMenuItem(title); JDialog dialog = new JDialog(parent, title);
		int[] value = {init};
		JTextField field = new JTextField(3); field.setText(" " + init + " "); field.setEditable(false); field.setHorizontalAlignment(JTextField.CENTER);

		java.util.function.IntConsumer setter = v -> { value[0] = Math.max(lo, Math.min(hi, v)); field.setText(" " + value[0] + " "); on_value_change.accept(value[0]); };
		if(setter_ref != null) setter_ref[0] = setter;

		JButton minus_button = new JButton("-"), plus_button = new JButton("+");
		minus_button.addActionListener(e -> { if(value[0] > lo) setter.accept(value[0] - 1); });
		plus_button.addActionListener(e -> { if(value[0] < hi) setter.accept(value[0] + 1); });

		JPanel field_panel = new JPanel(); field_panel.add(field);
		JPanel button_panel = new JPanel(); button_panel.add(minus_button); button_panel.add(plus_button);
		saddle_checkbox = new JCheckBox("Use Saddle", saddle_init);
		saddle_checkbox.addActionListener(e -> on_saddle_change.accept(saddle_checkbox.isSelected()));

		JPanel panel = new JPanel(new GridLayout(3, 1));
		panel.add(field_panel); panel.add(button_panel); panel.add(saddle_checkbox);
		dialog.add(panel);
		item.addActionListener(e -> { Point p = parent.getLocation(); dialog.setLocation(p.x, p.y - 100); dialog.pack(); dialog.setVisible(true); });
		return item;
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
				datatype_menu.setEnabled(entropy_type != 4);
				delta_button[delta_type].setSelected(true);
				compress_button[compress_type == 0 ? 0 : 1].setSelected(true);
				showBlockSettings();
				apply();
			}
		}.execute();
	}

	// Packs and compresses an int[] (deltas, or a widened map) into a
	// StringMapper bit string. Callers read both the bitlength and, via
	// getIterations, whether it really compressed.
	static byte[] packAndCompress(int[] values)
	{
		return StringMapper.compressStrings((byte[]) StringMapper.getStringList(values, false).get(3));
	}

	static int[] widen(byte[] b)
	{
		int[] v = new int[b.length];
		for(int k = 0; k < b.length; k++) v[k] = b[k] & 0xFF;
		return v;
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

	// Prints the ranked channel-set table (rank, channel composition, per-
	// channel entropy estimate, total), marking the selected set.
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

	// Prints the ranked delta-type table in bytes: deltas, map (types with
	// a map only) and total, marking the selected type.
	private void printDeltaTypeRanking(int[] delta_bits, int[] map_bits, boolean[] delta_compressed, boolean[] map_compressed, int[] total)
	{
		Integer[] order = new Integer[DeltaMapper.DELTA_TYPES];
		for(int i = 0; i < DeltaMapper.DELTA_TYPES; i++) order[i] = i;
		Arrays.sort(order, (a, b) -> total[a] - total[b]);
		System.out.println("Delta types, smallest first: bytes for the deltas and, for types that have one, the");
		System.out.println("predictor map, all 3 channels. <= marks the type chosen.");
		System.out.println(String.format("      %-16s %12s %12s %12s", "delta type", "deltas", "map", "total"));
		for(int r = 0; r < DeltaMapper.DELTA_TYPES; r++)
		{
			int idx = order[r];
			System.out.println(String.format("  %2d. %-16s %12d %12s %12d%s",
				r + 1, DeltaMapper.DELTA_TYPE_NAMES[idx], delta_bits[idx] / 8,
				DeltaMapper.hasMap(idx) ? String.valueOf(map_bits[idx] / 8) : "", total[idx] / 8, (idx == delta_type) ? "  <=" : ""));
		}
		System.out.println();
	}

	// ---- Image pyramid --------------------------------------------------------

	// Pads c to a multiple of 2^levels, then shrinks it levels times;
	// sign_bits[lvl] compares level lvl with level lvl+1. Returns the top
	// level (DeltaReader.expandPyramid undoes it).
	private static int[] shrinkPyramid(int[] c, int xdim, int ydim, boolean[][] sign_bits)
	{
		int levels = sign_bits.length, mult = 1 << levels;
		int padded_xdim = ImageMapper.padTo(xdim, mult), padded_ydim = ImageMapper.padTo(ydim, mult);
		int[] level = ImageMapper.padEdgeReplicate(c, xdim, ydim, padded_xdim, padded_ydim);
		int   level_xdim = padded_xdim;
		for(int lvl = 0; lvl < levels; lvl++)
		{
			int[] next = ImageMapper.shrinkAvg(level, level_xdim);
			sign_bits[lvl] = ImageMapper.buildGeqBits(level, next, level_xdim);
			level = next;
			level_xdim /= 2;
		}
		return level;
	}

	// ---- Apply --------------------------------------------------------------

	// Quantizes, picks the channel set, codes the deltas (what Save writes),
	// then decodes them the way DeltaReader does for the preview.
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
		int[][] qc = quantizedChannels(size, true);
		ViewerSupport.parallel(6, i -> channel_sum[i] = (int) Math.floor(CodeMapper.getShannonLimit(DeltaMapper.getIdealFrequency2(qc[i], new_xdim, new_ydim))));
		computeSetSums();
		int[] channel_id = DeltaMapper.getChannels(min_set_id);

		// Integer needs every delta - delta_min to fit in a byte.
		boolean int_allowed = true;
		for(int i = 0; i < 3 && int_allowed; i++)
		{
			int[] c = qc[channel_id[i]];
			int cmin = c[0], cmax = c[0];
			for(int v : c) { if(v < cmin) cmin = v; if(v > cmax) cmax = v; }
			if((cmax - cmin) * 2 > 255) int_allowed = false;
		}
		if(!int_allowed && compress_type == 0) { compress_type = 1; SwingUtilities.invokeLater(() -> compress_button[1].setSelected(true)); }
		final boolean enable_int = int_allowed;
		SwingUtilities.invokeLater(() -> { for(JRadioButton b : int_radio_btns) b.setEnabled(enable_int); });

		int[] top = DeltaReader.getPyramidSize(new_xdim, new_ydim, pixel_pyramid);
		int   top_xdim = top[0], top_ydim = top[1];

		// The 3 chosen channels in parallel: pyramid, deltas, encode, then
		// decode back. Each writes only its own slot.
		int[][]       new_table    = new int[3][];
		byte[][]      new_payload  = new byte[3][];
		byte[][]      new_map      = new byte[3][];
		boolean[][][] new_sign_bit = new boolean[3][][];
		int[][]       new_delta    = new int[3][];
		int[][]       dc           = new int[3][];
		ViewerSupport.parallel(3, i ->
		{
			int   j = channel_id[i];
			int[] c = qc[j];
			if(pixel_pyramid != 0)
			{
				new_sign_bit[i] = new boolean[pixel_pyramid][];
				c = shrinkPyramid(c, new_xdim, new_ydim, new_sign_bit[i]);
			}
			ArrayList result = DeltaMapper.getDeltas(c, top_xdim, top_ydim, delta_type, scanline5_variant, block_size, block_set);
			int[] delta = (int[]) result.get(1);
			new_delta[i] = delta.clone();
			if(DeltaMapper.hasMap(delta_type)) new_map[i] = (byte[]) result.get(2);

			// Integer: one byte per delta, delta - delta_min. The bit length,
			// string length and iterations aren't used and keep their last
			// values.
			if(compress_type == 0)
			{
				channel_delta_min[j] = (int) StringMapper.getHistogram(delta).get(0);
				byte[] b = new byte[delta.length];
				for(int k = 1; k < delta.length; k++) b[k] = (byte)(delta[k] - channel_delta_min[j]);
				new_payload[i] = b;
			}
			else
			{
				ArrayList dsl = StringMapper.getStringList(delta, compress_type == 2);
				channel_delta_min[j] = (int) dsl.get(0); channel_length[j] = (int) dsl.get(1);
				new_table[i] = (int[]) dsl.get(2); new_payload[i] = (byte[]) dsl.get(3);
				channel_compressed_length[j] = StringMapper.getBitlength(new_payload[i]);
				channel_iterations[i] = StringMapper.getIterations(new_payload[i]);
			}

			// Decode back, as DeltaReader does.
			int[] d2;
			if(compress_type == 0)
			{
				d2 = new int[top_xdim * top_ydim];
				for(int k = 1; k < d2.length; k++) d2[k] = (new_payload[i][k] & 0xFF) + channel_delta_min[j];
			}
			else
			{
				byte[] str = StringMapper.decompressStrings(new_payload[i]);
				d2 = StringMapper.unpackStrings(str, new_table[i], top_xdim * top_ydim, channel_length[j]);
				d2[0] = 0; for(int k = 1; k < d2.length; k++) d2[k] += channel_delta_min[j];
			}
			int[] ch = DeltaMapper.getValuesFromDeltas(d2, top_xdim, top_ydim, channel_init[j], delta_type, new_map[i], scanline5_variant);
			if(pixel_pyramid != 0) ch = DeltaReader.expandPyramid(ch, new_xdim, new_ydim, new_sign_bit[i], j > 2, use_saddle);
			if(j > 2) for(int k = 0; k < ch.length; k++) ch[k] += channel_min[j];
			dc[i] = ch;
		});
		table = new_table; payload = new_payload; map = new_map; sign_bit = new_sign_bit;
		delta_list = new_delta; delta_xdim = top_xdim;

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

		// Header, then per channel: min, init, delta min, bit lengths,
		// iterations, map (types 6-13), sign bits (pyramid), string table
		// (String and String*, not Context), then the entropy-coded payload.
		private void save(DataOutputStream out) throws IOException
		{
			int[] channel_id = DeltaMapper.getChannels(min_set_id);
			out.writeByte(FORMAT_ID); out.writeByte(FORMAT_VERSION);
			out.writeShort(image_xdim); out.writeShort(image_ydim); out.writeByte(pixel_shift); out.writeByte(pixel_quant);
			out.writeByte(min_set_id); out.writeByte(delta_type); out.writeByte(compress_type); out.writeByte(entropy_type); out.writeByte(scanline5_variant);
			out.writeByte(pixel_pyramid); out.writeByte(use_saddle ? 1 : 0);

			long t0 = System.nanoTime();
			byte[][] coded = (entropy_type == 4) ? contextCode(delta_list, delta_xdim) : entropyCode(payload, entropy_type, pixel_segment);
			System.out.println("Entropy coding [" + ENTROPY_NAMES[entropy_type] + "] took " + ViewerSupport.formatDuration(System.nanoTime() - t0));

			for(int i = 0; i < 3; i++)
			{
				int j = channel_id[i];
				out.writeInt(channel_min[j]); out.writeInt(channel_init[j]); out.writeInt(channel_delta_min[j]);
				out.writeInt(channel_length[j]); out.writeInt(channel_compressed_length[j]); out.writeByte(channel_iterations[i]);
				if(DeltaMapper.hasMap(delta_type)) DeltaMapper.writeMap(out, delta_type, map[i], (i > 0) ? map[i - 1] : null, delta_xdim);
				if(pixel_pyramid != 0) writeSignBits(out, sign_bit[i]);
				if(compress_type > 0 && entropy_type != 4) DeltaMapper.writeTable(out, table[i]);
				out.write(coded[i]);
			}
		}
	}

	// Sign bits, one bitmap per pyramid level: int length, then
	// (length+7)/8 bytes, bit q in byte q>>3, bit q&7. The reader takes the
	// number of levels from the header.
	private static void writeSignBits(DataOutputStream out, boolean[][] sign_bits) throws IOException
	{
		for(boolean[] bits : sign_bits)
		{
			byte[] packed = new byte[(bits.length + 7) / 8];
			for(int q = 0; q < bits.length; q++) if(bits[q]) packed[q >> 3] |= (byte)(1 << (q & 7));
			out.writeInt(bits.length);
			out.write(packed);
		}
	}

	// Context entropy type (DeltaWriter2 uses it too): each channel's deltas
	// context-coded directly (DeltaMapper.packContextDeltas), with channel
	// i's contexts using channels 0..i-1, so the reader decodes in order.
	// Checks that each channel decodes back.
	static byte[][] contextCode(int[][] delta, int xdim)
	{
		byte[][] coded = new byte[3][];
		ViewerSupport.parallel(3, i ->
		{
			try
			{
				int[][] previous = Arrays.copyOf(delta, i);
				coded[i] = DeltaMapper.packContextDeltas(delta[i], previous, xdim);
				int[] back = DeltaMapper.readContextDeltas(new DataInputStream(new ByteArrayInputStream(coded[i])), delta[i].length, previous, xdim);
				if(!Arrays.equals(back, delta[i])) System.out.println("WARNING: channel " + i + " context-coded deltas do not decode back.");
			}
			catch(IOException e) { throw new UncheckedIOException(e); }
		});
		return coded;
	}

	// Entropy-codes the 3 channel payloads (DeltaWriter2 uses it too). Each
	// result is what follows the channel's table in the file:
	//   LZ77:       int payload length, int Deflated length, Deflated payload;
	//   Huffman:    int length + the 256 code lengths (Deflated), int length + the code;
	//   Arithmetic: the frequency tables (ArithmeticMapper.packFrequencies),
	//               then each block's int length and coded bytes;
	//   Adaptive:   int length + the coded bytes.
	static byte[][] entropyCode(byte[][] payload, int entropy_type, int pixel_segment)
	{
		byte[][] coded = new byte[3][];
		if(entropy_type == 0)
		{
			ViewerSupport.parallel(3, i ->
			{
				byte[] zipped = CodeMapper.deflate(payload[i], Deflater.BEST_COMPRESSION);
				coded[i] = java.nio.ByteBuffer.allocate(8 + zipped.length).putInt(payload[i].length).putInt(zipped.length).put(zipped).array();
			});
		}
		else if(entropy_type == 1)
		{
			ViewerSupport.parallel(3, i ->
			{
				byte[] lengths = CodeMapper.getRegularHuffmanLength(ArithmeticMapper.getFrequency(payload[i]));
				byte[] code    = CodeMapper.packRegularCode(payload[i], lengths);
				if(!Arrays.equals(payload[i], CodeMapper.unpackRegularCode(code, lengths, payload[i].length)))
					System.out.println("WARNING: channel " + i + " Huffman payload does not decode back.");
				byte[] tables = CodeMapper.packRegularTables(new byte[][] {lengths}, Deflater.BEST_COMPRESSION);
				coded[i] = java.nio.ByteBuffer.allocate(8 + tables.length + code.length).putInt(tables.length).put(tables).putInt(code.length).put(code).array();
			});
		}
		else if(entropy_type == 2)
		{
			// All blocks of all 3 channels are coded on the shared pool.
			byte[][][] blocks = new byte[3][][];
			int[][][]  freqs  = new int[3][][];
			byte[][][] enc    = new byte[3][][];
			for(int i = 0; i < 3; i++)
			{
				int n = (pixel_segment >= 10) ? 1 : Math.max(1, payload[i].length / (500 + pixel_segment * 500));
				blocks[i] = ArithmeticMapper.getBlocks(payload[i], n);
				freqs[i]  = new int[n][];
				enc[i]    = new byte[n][];
				for(int m = 0; m < n; m++) freqs[i][m] = ArithmeticMapper.getFrequency(blocks[i][m]);
			}
			int n0 = blocks[0].length, n1 = blocks[1].length;
			ViewerSupport.parallel(n0 + n1 + blocks[2].length, b ->
			{
				int i = (b < n0) ? 0 : (b < n0 + n1) ? 1 : 2;
				int m = b - ((i == 0) ? 0 : (i == 1) ? n0 : n0 + n1);
				enc[i][m] = ArithmeticMapper.getIntervalValueFastFenwick(blocks[i][m], freqs[i][m]);
			});
			ViewerSupport.parallel(3, i ->
			{
				ByteArrayOutputStream bytes = new ByteArrayOutputStream();
				DataOutputStream d = new DataOutputStream(bytes);
				try
				{
					d.write(ArithmeticMapper.packFrequencies(freqs[i], Deflater.BEST_COMPRESSION));
					for(byte[] e : enc[i]) { d.writeInt(e.length); d.write(e); }
				}
				catch(IOException e) { throw new UncheckedIOException(e); }
				coded[i] = bytes.toByteArray();
			});
		}
		else
		{
			ViewerSupport.parallel(3, i ->
			{
				byte[] code = ArithmeticMapper.getIntervalValueAdaptive(payload[i]);
				if(!Arrays.equals(payload[i], ArithmeticMapper.getArithmeticValuesAdaptive(code, payload[i].length)))
					System.out.println("WARNING: channel " + i + " adaptive payload does not decode back.");
				coded[i] = java.nio.ByteBuffer.allocate(4 + code.length).putInt(code.length).put(code).array();
			});
		}
		return coded;
	}
}
