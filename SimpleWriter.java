import java.awt.*;
import java.awt.event.*;
import java.awt.image.*;
import java.io.*;
import java.util.*;
import java.util.zip.*;
import javax.swing.*;

// SimpleWriter version 1.0
public class SimpleWriter
{
	// File format: every file starts with FORMAT_ID ('S' = Simple format) and
	// FORMAT_VERSION. Bump FORMAT_VERSION in the writer and reader together
	// whenever the file layout or a coder's output changes.
	static final char FORMAT_ID      = 'S';
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
	int delta_type    = 2;   // average filter by default; user can change it from the Delta menu
	int entropy_type  = 0;
	byte scanline5_variant = 0;
	int  block_size = DeltaMapper.BLOCK_DEFAULT;   // block map (delta type 13): block width/height
	int  block_set  = 0;                           // block map: predictor set (DeltaMapper.BLOCK_SET_NAMES)

	JSlider pquant_slider, pshift_slider, corr_slider, segment_slider;
	JRadioButtonMenuItem[] delta_button, entropy_button;

	// ---- Results of the last Apply (read by Save) ---------------------------
	int[]  set_sum = new int[10], channel_sum = new int[6];
	int[]  channel_init = new int[6], channel_min = new int[6], channel_delta_min = new int[6];
	int[]  channel_length = new int[6], channel_compressed_length = new int[6];
	byte[] channel_iterations = new byte[3];
	ArrayList<int[]>  table_list  = new ArrayList<int[]>();
	ArrayList<byte[]> string_list = new ArrayList<byte[]>();
	ArrayList<byte[]> map_list    = new ArrayList<byte[]>();
	int[][] delta_list = new int[3][];   // the deltas themselves, for the Context type
	int     delta_xdim;                  // their row width

	long    file_length;
	boolean applied = false;   // the last Apply finished; Save needs it

	public static void main(String[] args)
	{
		ViewerSupport.applyHiDpiFontScaleIfNeeded();
		if(args.length == 1) { new SimpleWriter(args[0]); return; }
		FileDialog fd = new FileDialog((java.awt.Frame) null, "Open Image", FileDialog.LOAD);
		fd.setVisible(true);
		if(fd.getFile() != null) new SimpleWriter(new File(fd.getDirectory(), fd.getFile()).getPath());
		else System.exit(0);
	}

	// Picks the channel set with the smallest entropy estimate. The delta
	// type is not chosen here: it is 2 (average) unless the user picks
	// another from the Delta menu. Deltas are always packed as unary strings
	// (getStringList(delta, true)); StringMapper skips its own compression
	// when it doesn't pay off.
	public void init()
	{
		int[]   size = DeltaMapper.getQuantizedSize(image_xdim, image_ydim, pixel_quant);
		int[][] qc   = quantizedChannels(size);
		for(int i = 0; i < 6; i++)
			channel_sum[i] = (int) Math.floor(CodeMapper.getShannonLimit(DeltaMapper.getIdealFrequency(qc[i], size[0], size[1])));
		computeSetSums();
		printChannelSetRanking();
	}

	// The six candidate channels after quantizing (see
	// DeltaMapper.getCandidateChannels); sets channel_min and channel_init.
	private int[][] quantizedChannels(int[] size)
	{
		int[][] q = new int[3][];
		for(int i = 0; i < 3; i++)
		{
			int[] ch = source[i];
			if(pixel_quant != 0) ch = ResizeMapper.resize(ch, image_xdim, size[0], size[1]);
			q[i] = DeltaMapper.quantizeChannel(ch, pixel_shift);
		}
		int[][] qc = DeltaMapper.getCandidateChannels(q[0], q[1], q[2], channel_min);
		for(int i = 0; i < 6; i++) channel_init[i] = qc[i][0];
		return qc;
	}

	public SimpleWriter(String _filename)
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

		view = new ViewerSupport("Simple Writer  " + filename, image_xdim, image_ydim);
		JFrame   frame    = view.frame;
		JMenuBar menu_bar = frame.getJMenuBar();

		JMenu file_menu = new JMenu("File");
		JMenuItem open_item = new JMenuItem("Open...");
		open_item.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_O, InputEvent.CTRL_DOWN_MASK));
		open_item.addActionListener(e -> { FileDialog fd = new FileDialog(frame, "Open Image", FileDialog.LOAD); fd.setVisible(true); if(fd.getFile() != null) new SimpleWriter(fd.getDirectory() + fd.getFile()); });
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
		String[] dnames = {"H","V","Average","Med","Directional","Adaptive","Scanline 1","Scanline 2","Scanline 3","Scanline 4","Scanline 5","Map 1","Map 2","Block Map"};
		delta_button = new JRadioButtonMenuItem[DeltaMapper.DELTA_TYPES]; ButtonGroup dg = new ButtonGroup();
		for(int i = 0; i < DeltaMapper.DELTA_TYPES; i++)
		{
			final int dt = i;
			delta_button[i] = new JRadioButtonMenuItem(dnames[i]); dg.add(delta_button[i]); delta_menu.add(delta_button[i]);
			delta_button[i].addActionListener(e -> { if(delta_type != dt) { delta_type = dt; apply(); } });
		}
		delta_button[delta_type].setSelected(true);

		// Block map settings (delta type 13); a change re-applies only when
		// the block map is selected.
		delta_menu.addSeparator();
		delta_menu.add(ViewerSupport.makeSpinnerDialog(frame, "Block Size", DeltaMapper.BLOCK_MIN, DeltaMapper.BLOCK_MAX, block_size,
			v -> { block_size = v; if(delta_type == 13) apply(); }));
		delta_menu.add(ViewerSupport.makeRadioDialog(frame, "Block Predictors", DeltaMapper.BLOCK_SET_NAMES, block_set,
			v -> { block_set = v; if(delta_type == 13) apply(); }));

		// entropy_type equals the button index: 0 LZ77, 1 Huffman, 2 Arithmetic,
		// 3 Adaptive, 4 Context.
		JMenu entropy_menu = new JMenu("Entropy");
		entropy_button = new JRadioButtonMenuItem[ENTROPY_NAMES.length]; ButtonGroup eg = new ButtonGroup();
		for(int i = 0; i < ENTROPY_NAMES.length; i++)
		{
			final int et = i;
			entropy_button[i] = new JRadioButtonMenuItem(ENTROPY_NAMES[i]); eg.add(entropy_button[i]); entropy_menu.add(entropy_button[i]);
			entropy_button[i].setSelected(entropy_type == i);
			entropy_button[i].addActionListener(e -> entropy_type = et);
		}
		// Segment size for Arithmetic (min_seg = 500 + pixel_segment*500;
		// 10 = one segment). Doesn't call apply(): segmentation only
		// happens at Save time and never affects the preview.
		entropy_menu.addSeparator();
		entropy_menu.add(ViewerSupport.makeSliderDialog(frame, "Segment Size", 0, 10, pixel_segment, v -> pixel_segment = v, ss)); segment_slider = ss[0];

		menu_bar.add(file_menu); menu_bar.add(view.makeViewMenu()); menu_bar.add(quant_menu);
		menu_bar.add(delta_menu); menu_bar.add(entropy_menu);

		view.setImage(original_image);
		view.show();
		SwingUtilities.invokeLater(() -> showInitialImage());
	}

	static final String[] ENTROPY_NAMES = {"LZ77", "Huffman", "Arithmetic", "Adaptive", "Context"};

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
				delta_button[delta_type].setSelected(true);
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

	// Prints the ranked channel-set table (rank, channel composition, per-
	// channel entropy estimate, total), marking the selected set.
	private void printChannelSetRanking()
	{
		Integer[] order = new Integer[10];
		for(int i = 0; i < 10; i++) order[i] = i;
		Arrays.sort(order, (a, b) -> set_sum[a] - set_sum[b]);
		System.out.println("Channel sets (ranked):");
		for(int r = 0; r < 10; r++)
		{
			int idx = order[r];
			int[] c = DeltaMapper.getChannels(idx);
			System.out.println(String.format("  %2d. %-32s %10d %10d %10d %12d%s", r + 1, DeltaMapper.SET_NAMES[idx],
				channel_sum[c[0]], channel_sum[c[1]], channel_sum[c[2]], set_sum[idx], (idx == min_set_id) ? " **" : ""));
		}
		System.out.println();
	}

	// ---- Apply --------------------------------------------------------------

	// Quantizes, picks the channel set, codes the deltas as unary strings
	// (what Save writes), then decodes them the way SimpleReader does for
	// the preview.
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
		for(int i = 0; i < 6; i++)
			channel_sum[i] = (int) Math.floor(CodeMapper.getShannonLimit(DeltaMapper.getIdealFrequency(qc[i], new_xdim, new_ydim)));
		computeSetSums();

		int[] channel_id = DeltaMapper.getChannels(min_set_id);
		delta_xdim = new_xdim;
		table_list.clear(); string_list.clear(); map_list.clear();
		for(int i = 0; i < 3; i++)
		{
			int j = channel_id[i];
			ArrayList result = DeltaMapper.getDeltas(qc[j], new_xdim, new_ydim, delta_type, scanline5_variant, block_size, block_set);
			if(DeltaMapper.hasMap(delta_type)) map_list.add((byte[]) result.get(2));
			delta_list[i] = ((int[]) result.get(1)).clone();
			// Always unary strings; StringMapper applies its own
			// compression only when it gets below its threshold.
			ArrayList dsl = StringMapper.getStringList((int[]) result.get(1), true);
			byte[] str = (byte[]) dsl.get(3);
			channel_delta_min[j] = (int) dsl.get(0); channel_length[j] = (int) dsl.get(1);
			table_list.add((int[]) dsl.get(2)); string_list.add(str);
			channel_compressed_length[j] = StringMapper.getBitlength(str);
			channel_iterations[i] = StringMapper.getIterations(str);
		}

		// Decode what Save will write, as SimpleReader does.
		int[][] dc = new int[3][];
		for(int i = 0; i < 3; i++)
		{
			int j = channel_id[i];
			byte[] str   = StringMapper.decompressStrings(string_list.get(i));
			int[]  delta = StringMapper.unpackStrings(str, table_list.get(i), new_xdim * new_ydim, channel_length[j]);
			delta[0] = 0; for(int k = 1; k < delta.length; k++) delta[k] += channel_delta_min[j];
			byte[] map = DeltaMapper.hasMap(delta_type) ? map_list.get(i) : null;
			dc[i] = DeltaMapper.getValuesFromDeltas(delta, new_xdim, new_ydim, channel_init[j], delta_type, map, scanline5_variant);
			if(j > 2) for(int k = 0; k < dc[i].length; k++) dc[i][k] += channel_min[j];
		}
		// Like SimpleReader: recombine the channels first, then resize, then
		// shift. Resizing each channel before recombining gives different
		// results (ResizeMapper rounds down).
		int[][] bgr = DeltaMapper.getBlueGreenRed(min_set_id, dc[0], dc[1], dc[2]);
		for(int c = 0; c < 3; c++)
		{
			if(pixel_quant != 0) bgr[c] = ResizeMapper.resize(bgr[c], new_xdim, image_xdim, image_ydim);
			if(pixel_shift != 0) bgr[c] = DeltaMapper.shift(bgr[c], pixel_shift);
		}

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
		// iterations, map (types 6-13), string table (not for Context), then
		// the entropy-coded payload (see below for each type).
		private void save(DataOutputStream out) throws IOException
		{
			int[] channel_id = DeltaMapper.getChannels(min_set_id);
			out.writeByte(FORMAT_ID); out.writeByte(FORMAT_VERSION);
			out.writeShort(image_xdim); out.writeShort(image_ydim); out.writeByte(pixel_shift); out.writeByte(pixel_quant);
			out.writeByte(min_set_id); out.writeByte(delta_type); out.writeByte(entropy_type); out.writeByte(scanline5_variant);

			long t0 = System.nanoTime();
			byte[][] coded = new byte[3][];
			if(entropy_type == 0)
			{
				// LZ77: int payload length, int Deflated length, Deflated payload.
				ViewerSupport.parallel(3, i ->
				{
					byte[] zipped = CodeMapper.deflate(string_list.get(i), Deflater.BEST_COMPRESSION);
					coded[i] = java.nio.ByteBuffer.allocate(8 + zipped.length).putInt(string_list.get(i).length).putInt(zipped.length).put(zipped).array();
				});
			}
			else if(entropy_type == 1)
			{
				// Regular Huffman (CodeMapper): int length + the 256 code
				// lengths (Deflated), then int length + the coded payload.
				ViewerSupport.parallel(3, i ->
				{
					byte[] payload = string_list.get(i);
					byte[] lengths = CodeMapper.getRegularHuffmanLength(ArithmeticMapper.getFrequency(payload));
					byte[] code    = CodeMapper.packRegularCode(payload, lengths);
					if(!Arrays.equals(payload, CodeMapper.unpackRegularCode(code, lengths, payload.length)))
						System.out.println("WARNING: channel " + i + " Huffman payload does not decode back.");
					byte[] table = CodeMapper.packRegularTables(new byte[][]{lengths}, Deflater.BEST_COMPRESSION);
					coded[i] = java.nio.ByteBuffer.allocate(8 + table.length + code.length).putInt(table.length).put(table).putInt(code.length).put(code).array();
				});
			}
			else if(entropy_type == 2)
			{
				// Arithmetic: the frequency tables (ArithmeticMapper.packFrequencies),
				// then each block's int length and coded bytes. All blocks of all
				// 3 channels are coded on the shared pool.
				byte[][][] blocks = new byte[3][][];
				int[][][]  freqs  = new int[3][][];
				byte[][][] enc    = new byte[3][][];
				for(int i = 0; i < 3; i++)
				{
					byte[] payload = string_list.get(i);
					int n = (pixel_segment >= 10) ? 1 : Math.max(1, payload.length / (500 + pixel_segment * 500));
					blocks[i] = ArithmeticMapper.getBlocks(payload, n);
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
			else if(entropy_type == 4)
			{
				// Context: the deltas coded directly with the context coder
				// (DeltaMapper.packContextDeltas); no string table. Channel i's
				// contexts use channels 0..i-1, so the reader decodes in order.
				ViewerSupport.parallel(3, i ->
				{
					try
					{
						int[][] previous = Arrays.copyOf(delta_list, i);
						coded[i] = DeltaMapper.packContextDeltas(delta_list[i], previous, delta_xdim);
						int[] back = DeltaMapper.readContextDeltas(new DataInputStream(new ByteArrayInputStream(coded[i])), delta_list[i].length, previous, delta_xdim);
						if(!Arrays.equals(back, delta_list[i])) System.out.println("WARNING: channel " + i + " context-coded deltas do not decode back.");
					}
					catch(IOException e) { throw new UncheckedIOException(e); }
				});
			}
			else
			{
				// Adaptive (ArithmeticMapper's adaptive coder): no table, just
				// int length + the coded bytes.
				ViewerSupport.parallel(3, i ->
				{
					byte[] payload = string_list.get(i);
					byte[] code    = ArithmeticMapper.getIntervalValueAdaptive(payload);
					if(!Arrays.equals(payload, ArithmeticMapper.getArithmeticValuesAdaptive(code, payload.length)))
						System.out.println("WARNING: channel " + i + " adaptive payload does not decode back.");
					coded[i] = java.nio.ByteBuffer.allocate(4 + code.length).putInt(code.length).put(code).array();
				});
			}
			System.out.println("Entropy coding [" + ENTROPY_NAMES[entropy_type] + "] took " + ViewerSupport.formatDuration(System.nanoTime() - t0));

			for(int i = 0; i < 3; i++)
			{
				int j = channel_id[i];
				out.writeInt(channel_min[j]); out.writeInt(channel_init[j]); out.writeInt(channel_delta_min[j]);
				out.writeInt(channel_length[j]); out.writeInt(channel_compressed_length[j]); out.writeByte(channel_iterations[i]);
				if(DeltaMapper.hasMap(delta_type)) DeltaMapper.writeMap(out, delta_type, map_list.get(i), (i > 0) ? map_list.get(i - 1) : null, delta_xdim);
				if(entropy_type != 4) DeltaMapper.writeTable(out, table_list.get(i));
				out.write(coded[i]);
			}
		}
	}
}
