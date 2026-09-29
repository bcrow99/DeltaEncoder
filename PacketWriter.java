import java.awt.*;
import java.awt.event.*;
import java.awt.image.*;
import java.io.*;
import java.nio.ByteBuffer;
import java.util.*;
import java.util.zip.*;
import javax.swing.*;

// PacketWriter version 1.0
public class PacketWriter
{
	// File format: every file starts with FORMAT_ID ('P' = Packet format) and
	// FORMAT_VERSION. Bump FORMAT_VERSION in the writer and reader together
	// whenever the file layout or a coder's output changes.
	static final char FORMAT_ID      = 'P';
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
	// Entropy menu, in entropy_type order. 3, 4 and 6 code every packet on
	// its own instead of all of a channel's packets as one stream (see
	// codePackets). 5 and 6 use ArithmeticMapper's adaptive coder, which
	// stores no frequency table.
	static final String[] ENTROPY_NAMES = {"LZ77","Huffman","Arithmetic","Arithmetic (per packet)","Huffman (per packet)","Adaptive","Adaptive (per packet)","Context"};
	static final int ARITHMETIC_PER_PACKET = 3;
	static final int HUFFMAN_PER_PACKET    = 4;
	static final int ADAPTIVE              = 5;
	static final int ADAPTIVE_PER_PACKET   = 6;
	static final int CONTEXT               = 7;   // deltas context-coded directly (DeltaMapper.packContextDeltas); no packets
	byte scanline5_variant = 0;
	int  block_size = DeltaMapper.BLOCK_DEFAULT;   // block map (delta type 13): block width/height
	int  block_set  = 0;                           // block map: predictor set (DeltaMapper.BLOCK_SET_NAMES)

	static final String[] CHANNEL_NAMES = {"blue","green","red","blue-green","red-green","red-blue"};

	// ---- Packet (string segmentation) parameters ----------------------------
	// packet_level 0..10 sets the minimum segment length passed to
	// SegmentMapper.getSegmentedData3(): 0 = MIN_SEGMENT_BITS (32 bytes),
	// 10 = no segmentation (the whole string compressed as one piece, which
	// is what SimpleWriter does). Levels in between are geometric steps.
	// Merging is capped at MAX_PACKET_FACTOR times the minimum segment
	// length: uncapped, merging collapses the packet count to a handful by
	// level 2; capped, it falls roughly by half per level.
	// Segmentation is lossless, so like the entropy settings it only runs
	// at Save and never affects the live preview.
	int packet_level = 0;
	static final int    MIN_SEGMENT_BITS = 256;
	static final int    MAX_PACKET_FACTOR = 4;
	// segment -> merge -> combine (no splice). Testing showed splice()/splice2()
	// saved only ~0.5% of segment bits at 100x+ the run time -- their cost grows
	// with the square of each uncompressed run, so large images effectively hang.
	static final int    SEGMENT_TYPE     = 2;

	// Merge criterion and bin width passed to getSegmentedData3(). Each
	// segment's zero-bit ratio is sorted into BINS bins; merging joins
	// neighbouring segments on the same side of 0.5 whose bins are less than
	// a quarter of the range apart (merge type 2). Testing showed these work
	// well as fixed defaults.
	static final int    MERGE_TYPE = 2;
	static final int    BINS       = 20;
	static final double BIN        = binWidth(BINS);

	// Auto (Segment Length dialog): at Save, pick the level giving the
	// smallest output with the selected entropy coder (see chooseLevel).
	boolean packet_auto = false;
	JCheckBox packet_auto_box;

	// Bin width for a bin count. merge() recovers the count as
	// (int)(1.0/bin), so nudge the width down if floating-point rounding
	// would make that come out one short.
	static double binWidth(int number_of_bins)
	{
		double w = 1.0 / number_of_bins;
		while((int)(1.0 / w) < number_of_bins) w = Math.nextDown(w);
		return w;
	}

	JSlider pquant_slider, pshift_slider, corr_slider, segment_slider, packet_slider;
	JRadioButtonMenuItem[] delta_button, entropy_button;

	// ---- Results of the last Apply (read by Save) ---------------------------
	int[]  set_sum = new int[10], channel_sum = new int[6];
	int[]  channel_init = new int[6], channel_min = new int[6], channel_delta_min = new int[6];
	int[]  channel_length = new int[6];
	ArrayList<int[]>  table_list  = new ArrayList<int[]>();
	ArrayList<byte[]> string_list = new ArrayList<byte[]>();   // uncompressed unary strings
	ArrayList<byte[]> map_list    = new ArrayList<byte[]>();
	int[][] delta_list = new int[3][];   // the deltas themselves, for the Context type
	int     delta_xdim;                  // their row width

	long    file_length;
	boolean applied = false;   // the last Apply finished; Save needs it

	public static void main(String[] args)
	{
		ViewerSupport.applyHiDpiFontScaleIfNeeded();
		if(args.length == 1) { new PacketWriter(args[0]); return; }
		FileDialog fd = new FileDialog((java.awt.Frame) null, "Open Image", FileDialog.LOAD);
		fd.setVisible(true);
		if(fd.getFile() != null) new PacketWriter(new File(fd.getDirectory(), fd.getFile()).getPath());
		else System.exit(0);
	}

	// Picks the channel set with the smallest entropy estimate. delta_type
	// isn't chosen automatically (unlike DeltaWriter): it is the Delta menu
	// selection, default 2 (average).
	public void init()
	{
		int[] size = DeltaMapper.getQuantizedSize(image_xdim, image_ydim, pixel_quant);
		computeSetSums(quantizedChannels(size), size);
		printChannelSetRanking();
	}

	// The six candidate channels after quantizing (see
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

	// Entropy estimates of the 6 candidates, the set sums, and min_set_id.
	private void computeSetSums(int[][] qc, int[] size)
	{
		ViewerSupport.parallel(6, i ->
			channel_sum[i] = (int) Math.floor(CodeMapper.getShannonLimit(DeltaMapper.getIdealFrequency2(qc[i], size[0], size[1]))));
		for(int s = 0; s < 10; s++)
		{
			int[] c = DeltaMapper.getChannels(s);
			set_sum[s] = channel_sum[c[0]] + channel_sum[c[1]] + channel_sum[c[2]];
		}
		min_set_id = 0;
		for(int s = 1; s < 10; s++) if(set_sum[s] < set_sum[min_set_id]) min_set_id = s;
	}

	public PacketWriter(String _filename)
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

		view = new ViewerSupport("Packet Writer  " + filename, image_xdim, image_ydim);
		JFrame   frame    = view.frame;
		JMenuBar menu_bar = frame.getJMenuBar();

		JMenu file_menu = new JMenu("File");
		JMenuItem open_item = new JMenuItem("Open...");
		open_item.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_O, InputEvent.CTRL_DOWN_MASK));
		open_item.addActionListener(e -> { FileDialog fd = new FileDialog(frame, "Open Image", FileDialog.LOAD); fd.setVisible(true); if(fd.getFile() != null) new PacketWriter(fd.getDirectory() + fd.getFile()); });
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

		// Packet menu: minimum segment length for string segmentation.
		// Like the entropy settings, this only takes effect at Save.
		JMenu packet_menu = new JMenu("Packet");
		packet_menu.add(makeSegmentLengthDialog(frame));

		JMenu entropy_menu = new JMenu("Entropy");
		entropy_button = new JRadioButtonMenuItem[ENTROPY_NAMES.length]; ButtonGroup eg = new ButtonGroup();
		for(int i = 0; i < ENTROPY_NAMES.length; i++)
		{
			final int et = i;
			entropy_button[i] = new JRadioButtonMenuItem(ENTROPY_NAMES[i]); eg.add(entropy_button[i]); entropy_menu.add(entropy_button[i]);
			entropy_button[i].setSelected(entropy_type == i);
			entropy_button[i].addActionListener(e -> entropy_type = et);
		}
		// Arithmetic block size (min_seg = 500 + pixel_segment*500;
		// 10 = one block). Like the entropy buttons, it doesn't call
		// apply(): it only affects Save, never the preview.
		entropy_menu.addSeparator();
		entropy_menu.add(ViewerSupport.makeSliderDialog(frame, "Segment Size", 0, 10, pixel_segment, v -> pixel_segment = v, ss)); segment_slider = ss[0];

		menu_bar.add(file_menu); menu_bar.add(view.makeViewMenu()); menu_bar.add(quant_menu);
		menu_bar.add(delta_menu); menu_bar.add(packet_menu); menu_bar.add(entropy_menu);

		view.setImage(original_image);
		view.show();
		SwingUtilities.invokeLater(() -> showInitialImage());
	}

	// Segment Length: a 0-10 slider plus an Auto toggle. With Auto on, the
	// slider is disabled and Save picks the level (then shows it here).
	private JMenuItem makeSegmentLengthDialog(JFrame parent)
	{
		JMenuItem  item   = new JMenuItem("Segment Length");
		JDialog    dialog = new JDialog(parent, "Segment Length");
		JSlider    slider = new JSlider(0, 10, packet_level);
		JTextField field  = new JTextField(3);
		JCheckBox  auto   = new JCheckBox("Auto", packet_auto);
		packet_slider = slider; packet_auto_box = auto;
		field.setText(" " + packet_level + " "); field.setEditable(false);
		slider.addChangeListener(e -> { int v = slider.getValue(); field.setText(" " + v + " "); packet_level = v; });
		auto.addActionListener(e -> { packet_auto = auto.isSelected(); slider.setEnabled(!packet_auto); });
		slider.setEnabled(!packet_auto);
		JPanel panel = new JPanel(new BorderLayout());
		panel.add(slider, BorderLayout.CENTER); panel.add(field, BorderLayout.EAST); panel.add(auto, BorderLayout.SOUTH);
		dialog.add(panel);
		item.addActionListener(e -> { Point p = parent.getLocation(); dialog.setLocation(p.x, p.y - 80); dialog.pack(); dialog.setVisible(true); });
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
				delta_button[delta_type].setSelected(true);
				apply();
			}
		}.execute();
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

	// Quantizes, picks the channel set and packs the deltas as uncompressed
	// unary strings (Save segments and compresses them), then decodes them
	// the way PacketReader does for the preview.
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
		computeSetSums(qc, size);

		int[]    channel_id = DeltaMapper.getChannels(min_set_id);
		delta_xdim = new_xdim;
		int[][]  tables  = new int[3][];
		byte[][] strings = new byte[3][], maps = new byte[3][];
		int[][]  dc      = new int[3][];
		ViewerSupport.parallel(3, i ->
		{
			int j = channel_id[i];
			ArrayList result = DeltaMapper.getDeltas(qc[j], new_xdim, new_ydim, delta_type, scanline5_variant, block_size, block_set);
			if(DeltaMapper.hasMap(delta_type)) maps[i] = (byte[]) result.get(2);
			delta_list[i] = ((int[]) result.get(1)).clone();
			ArrayList dsl = StringMapper.getStringList((int[]) result.get(1), false);
			channel_delta_min[j] = (int) dsl.get(0); channel_length[j] = (int) dsl.get(1);
			tables[i] = (int[]) dsl.get(2); strings[i] = (byte[]) dsl.get(3);

			// Decode back, as PacketReader will.
			byte[] str   = StringMapper.decompressStrings(strings[i]);
			int[]  delta = StringMapper.unpackStrings(str, tables[i], new_xdim * new_ydim, channel_length[j]);
			delta[0] = 0; for(int k = 1; k < delta.length; k++) delta[k] += channel_delta_min[j];
			dc[i] = DeltaMapper.getValuesFromDeltas(delta, new_xdim, new_ydim, channel_init[j], delta_type, maps[i], scanline5_variant);
			if(j > 2) for(int k = 0; k < dc[i].length; k++) dc[i][k] += channel_min[j];
		});
		table_list.clear(); string_list.clear(); map_list.clear();
		for(int i = 0; i < 3; i++) { table_list.add(tables[i]); string_list.add(strings[i]); if(maps[i] != null) map_list.add(maps[i]); }

		// Like PacketReader: recombine the channels first, then resize, then
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

	// ---- Segmentation ---------------------------------------------------------

	// Minimum segment length in bits for a packet_level, or 0 meaning
	// "don't segment -- compress the whole string as one piece". Level 0 is
	// MIN_SEGMENT_BITS; each level multiplies it by the same factor, so
	// level 10 would reach the full string length.
	private static int getMinimumSegmentBits(int total_bits, int level)
	{
		if(level >= 10 || total_bits <= 2 * MIN_SEGMENT_BITS) return 0;
		double bits = MIN_SEGMENT_BITS * Math.pow((double) total_bits / MIN_SEGMENT_BITS, level / 10.0);
		int b = ((int) bits) / 8 * 8;
		if(b < MIN_SEGMENT_BITS) b = MIN_SEGMENT_BITS;
		if(b >= total_bits) return 0;
		return b;
	}

	// Splits an uncompressed unary string (with its trailing data byte)
	// into segments, each with its own trailing data byte recording
	// whether and how it was compressed.
	@SuppressWarnings("unchecked")
	private static ArrayList<byte[]> segmentString(byte[] string, int level)
	{
		ArrayList<byte[]> segs = new ArrayList<byte[]>();
		int min_bits = getMinimumSegmentBits(StringMapper.getBitlength(string), level);
		if(min_bits != 0)
		{
			ArrayList result = SegmentMapper.getSegmentedData3(string, min_bits, SEGMENT_TYPE, MERGE_TYPE, BIN, MAX_PACKET_FACTOR * min_bits);
			if(result.size() != 0) return (ArrayList<byte[]>) result.get(0);
		}
		segs.add(StringMapper.compressStrings(string));
		return segs;
	}

	// True if two strings (each with a trailing data byte) hold the same
	// bits -- used to verify the segmentation round trip at Save.
	private static boolean sameBits(byte[] a, byte[] b)
	{
		int bl = StringMapper.getBitlength(a);
		if(bl != StringMapper.getBitlength(b)) return false;
		int full = bl / 8;
		for(int k = 0; k < full; k++) if(a[k] != b[k]) return false;
		int odd = bl % 8;
		if(odd != 0) { int m = (1 << odd) - 1; if((a[full] & m) != (b[full] & m)) return false; }
		return true;
	}

	// ---- Packet layout -------------------------------------------------------
	//
	// A channel's segments are stored in two parts:
	//
	//  * payload: every segment's bits packed back to back with
	//    SegmentMapper.packSegments3 -- no per-segment byte padding and no
	//    trailing data bytes in between. This is what the entropy coder sees.
	//
	//  * table: segment count, length-field width (1, 2 or 4 bytes), then
	//    all segment byte lengths (excluding the trailing data byte), then
	//    all trailing data bytes (odd-bit count, compression type and
	//    iterations). Stored as columns so Deflate finds the repetition,
	//    and written Deflate-compressed.
	static class Packet
	{
		ArrayList<byte[]> segments;
		byte[] table;       // uncompressed table
		byte[] ztable;      // Deflate-compressed table (what gets written)
		byte[] payload;     // packed segment bits
		int    bits;        // total segment bits
		int    compressed;  // number of segments that are themselves compressed
	}

	private static Packet makePacket(ArrayList<byte[]> segs)
	{
		Packet p = new Packet();
		p.segments = segs;
		int n = segs.size(), max = 0;
		for(byte[] sg : segs) max = Math.max(max, sg.length - 1);
		int width = (max <= 255) ? 1 : (max <= 65535) ? 2 : 4;
		ByteBuffer table = ByteBuffer.allocate(5 + n * (width + 1));
		table.putInt(n).put((byte) width);
		for(byte[] sg : segs)
		{
			int len = sg.length - 1;
			if(width == 1) table.put((byte) len); else if(width == 2) table.putShort((short) len); else table.putInt(len);
		}
		for(byte[] sg : segs)
		{
			table.put(sg[sg.length - 1]);
			int it = StringMapper.getIterations(sg);
			if(it != 0 && it != 16) p.compressed++;
			p.bits += StringMapper.getBitlength(sg);
		}
		p.table   = table.array();
		p.ztable  = CodeMapper.deflate(p.table, Deflater.BEST_COMPRESSION);
		p.payload = (byte[]) SegmentMapper.packSegments3(segs).get(0);
		return p;
	}

	// Rebuilds the uncompressed string from a packet exactly the way
	// PacketReader does (inflate table, unpack segments, restore), so Save
	// can verify the round trip.
	private static byte[] unpackPacket(Packet p, byte string_data) throws DataFormatException
	{
		ByteBuffer d = ByteBuffer.wrap(CodeMapper.inflate(p.ztable, p.table.length));
		int n = d.getInt(), width = d.get();
		int[]  bytelength = new int[n];
		byte[] data       = new byte[n];
		for(int k = 0; k < n; k++) bytelength[k] = (width == 1) ? d.get() & 0xFF : (width == 2) ? d.getShort() & 0xFFFF : d.getInt();
		d.get(data);
		return SegmentMapper.restore2(SegmentMapper.unpackSegments3(p.payload, bytelength, data), string_data);
	}

	// ---- Entropy coding ------------------------------------------------------
	//
	// codePayload and codePackets return the bytes written for a channel
	// after its segment table. With a non-null warning they also decode the
	// result back (as PacketReader will) and set warning[i] on a mismatch.

	// Types 0, 1, 2 and 5 code a channel's whole payload:
	//   LZ77:       int payload length, int Deflated length, Deflated payload;
	//   Huffman:    int table_len, the 256 code lengths (CodeMapper's regular
	//               Huffman) Deflated, int coded_len, the coded payload;
	//   Arithmetic: the block frequency tables (packFrequencies), then each
	//               block's int length and coded bytes;
	//   Adaptive:   int coded_len, the coded payload (no table).
	// The per-packet types 3, 4 and 6 give their whole-payload counterpart
	// (2, 1 and 5), for the whole-string comparison at Save.
	private byte[] codePayload(byte[] payload, int type, String[] warning, int i)
	{
		if(type == 0)
		{
			byte[] zipped = CodeMapper.deflate(payload, Deflater.BEST_COMPRESSION);
			return ByteBuffer.allocate(8 + zipped.length).putInt(payload.length).putInt(zipped.length).put(zipped).array();
		}
		if(type == 1 || type == HUFFMAN_PER_PACKET)
		{
			byte[] lengths = CodeMapper.getRegularHuffmanLength(ArithmeticMapper.getFrequency(payload));
			byte[] table   = CodeMapper.packRegularTables(new byte[][]{lengths}, Deflater.BEST_COMPRESSION);
			byte[] code    = CodeMapper.packRegularCode(payload, lengths);
			if(warning != null && !Arrays.equals(payload, CodeMapper.unpackRegularCode(code, lengths, payload.length)))
				warning[i] = "Huffman payload does not decode back.";
			return ByteBuffer.allocate(8 + table.length + code.length).putInt(table.length).put(table).putInt(code.length).put(code).array();
		}
		if(type == ADAPTIVE || type == ADAPTIVE_PER_PACKET)
		{
			byte[] code = ArithmeticMapper.getIntervalValueAdaptive(payload);
			if(warning != null && !Arrays.equals(payload, ArithmeticMapper.getArithmeticValuesAdaptive(code, payload.length)))
				warning[i] = "adaptive payload does not decode back.";
			return ByteBuffer.allocate(4 + code.length).putInt(code.length).put(code).array();
		}
		byte[][] block = ArithmeticMapper.getBlocks(payload, arithmeticBlocks(payload.length));
		int[][]  freq  = new int[block.length][];
		byte[][] enc   = new byte[block.length][];
		ViewerSupport.parallel(block.length, m ->
		{
			freq[m] = ArithmeticMapper.getFrequency(block[m]);
			enc[m]  = ArithmeticMapper.getIntervalValueFastFenwick(block[m], freq[m]);
		});
		byte[] tables = ArithmeticMapper.packFrequencies(freq, Deflater.BEST_COMPRESSION);
		int size = tables.length;
		for(byte[] e : enc) size += 4 + e.length;
		ByteBuffer out = ByteBuffer.allocate(size).put(tables);
		for(byte[] e : enc) out.putInt(e.length).put(e);
		return out.array();
	}

	// Arithmetic blocks for a payload (pixel_segment 10 = one block).
	private int arithmeticBlocks(int length)
	{
		return (pixel_segment >= 10) ? 1 : Math.max(1, length / (500 + pixel_segment * 500));
	}

	// Types 3, 4 and 6 code each packet (a segment's bytes without its
	// trailing data byte, which is in the segment table) on its own:
	//   Arithmetic per packet: int type, int zip_len, every packet's frequency
	//       table Deflated together (ArithmeticMapper.deflateFrequencies);
	//   Huffman per packet: int tables_len, every packet's 256 code lengths
	//       Deflated together (CodeMapper.packRegularTables);
	//   Adaptive per packet: no tables;
	// then int lengths_len, each packet's coded length as a varint
	// (CodeMapper.packRegularLengths), and the coded packets back to back.
	// The packet count and byte lengths come from the segment table.
	private static byte[] codePackets(ArrayList<byte[]> segs, int type, String[] warning, int i)
	{
		int n = segs.size();
		byte[][] body = new byte[n][], coded = new byte[n][];
		for(int k = 0; k < n; k++) { byte[] sg = segs.get(k); body[k] = Arrays.copyOf(sg, sg.length - 1); }
		boolean[] ok = new boolean[n];
		Arrays.fill(ok, true);
		byte[] head;
		if(type == ARITHMETIC_PER_PACKET)
		{
			int[][] freq = new int[n][];
			ViewerSupport.parallel(n, k ->
			{
				freq[k]  = ArithmeticMapper.getFrequency(body[k]);
				coded[k] = (body[k].length == 0) ? new byte[0] : ArithmeticMapper.getIntervalValueFastFenwick(body[k], freq[k]);
			});
			int    freq_type = ArithmeticMapper.getFrequencyType(freq);
			byte[] zipped    = ArithmeticMapper.deflateFrequencies(freq, freq_type, Deflater.BEST_COMPRESSION);
			head = ByteBuffer.allocate(8 + zipped.length).putInt(freq_type).putInt(zipped.length).put(zipped).array();
			if(warning != null)
				ViewerSupport.parallel(n, k -> ok[k] = body[k].length == 0
					|| Arrays.equals(body[k], ArithmeticMapper.getArithmeticValuesFastFenwick(coded[k], freq[k], body[k].length)));
		}
		else if(type == HUFFMAN_PER_PACKET)
		{
			byte[][] table = new byte[n][];
			ViewerSupport.parallel(n, k ->
			{
				table[k] = CodeMapper.getRegularHuffmanLength(ArithmeticMapper.getFrequency(body[k]));
				coded[k] = CodeMapper.packRegularCode(body[k], table[k]);
			});
			byte[] tables = CodeMapper.packRegularTables(table, Deflater.BEST_COMPRESSION);
			head = ByteBuffer.allocate(4 + tables.length).putInt(tables.length).put(tables).array();
			if(warning != null)
			{
				try
				{
					byte[][] unpacked = CodeMapper.unpackRegularTables(tables, n);
					ViewerSupport.parallel(n, k -> ok[k] = Arrays.equals(body[k], CodeMapper.unpackRegularCode(coded[k], unpacked[k], body[k].length)));
				}
				catch(Exception e) { ok[0] = false; }
			}
		}
		else
		{
			ViewerSupport.parallel(n, k -> coded[k] = ArithmeticMapper.getIntervalValueAdaptive(body[k]));
			head = new byte[0];
			if(warning != null)
				ViewerSupport.parallel(n, k -> ok[k] = Arrays.equals(body[k], ArithmeticMapper.getArithmeticValuesAdaptive(coded[k], body[k].length)));
		}
		for(boolean b : ok) if(!b) warning[i] = "packets do not decode back to their segments.";

		byte[] lengths = CodeMapper.packRegularLengths(coded);
		int size = head.length + 4 + lengths.length;
		for(byte[] c : coded) size += c.length;
		ByteBuffer out = ByteBuffer.allocate(size).put(head).putInt(lengths.length).put(lengths);
		for(byte[] c : coded) out.put(c);
		return out.array();
	}

	static boolean isPerPacket(int type) { return type == ARITHMETIC_PER_PACKET || type == HUFFMAN_PER_PACKET || type == ADAPTIVE_PER_PACKET; }

	// Bytes codePayload writes for a payload with the selected coder (for
	// the per-packet types, their whole-payload counterpart).
	private int entropySize(byte[] payload)
	{
		return (payload.length == 0) ? 0 : codePayload(payload, entropy_type, null, 0).length;
	}

	// ---- Save ---------------------------------------------------------------

	class SaveHandler implements ActionListener
	{
		public void actionPerformed(ActionEvent event)
		{
			if(!applied) apply();
			if(!applied) { ViewerSupport.showError(view.frame, "Nothing saved: the last Apply failed."); return; }
			File     file   = new File("foo");
			Packet[] packet = new Packet[3];
			byte[][] coded  = new byte[3][];
			try(DataOutputStream out = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(file))))
			{
				save(out, packet, coded);
			}
			catch(Exception e)
			{
				file.delete();
				ViewerSupport.showError(view.frame, "Save failed: " + e);
				e.printStackTrace();
				return;
			}
			if(entropy_type != CONTEXT) printBreakdown(packet, coded);
			int raw = image_xdim * image_ydim * 3;
			System.out.println("Original compression rate: " + String.format("%.4f", (double) file_length / raw));
			System.out.println("Output  compression rate:  " + String.format("%.4f", (double) file.length() / raw));
			System.out.println();
		}

		// Header, then per channel: min, init, delta min, bit length, map
		// (types 6-13), string table, the string's trailing data byte, the
		// segment table (raw length, Deflated length, Deflated bytes), then
		// the entropy-coded payload (codePayload / codePackets).
		private void save(DataOutputStream out, Packet[] packet, byte[][] coded) throws IOException
		{
			if(entropy_type == CONTEXT) { saveContext(out, coded); return; }
			if(packet_auto)
			{
				packet_level = chooseLevel();
				packet_slider.setValue(packet_level);
			}

			// Segment each channel's string and check the round trip
			// PacketReader will perform.
			long      t0 = System.nanoTime();
			byte[]    string_data = new byte[3];
			boolean[] restore_ok  = new boolean[3];
			ViewerSupport.parallel(3, i ->
			{
				byte[] string  = string_list.get(i);
				string_data[i] = string[string.length - 1];
				packet[i]      = makePacket(segmentString(string, packet_level));
				try { restore_ok[i] = sameBits(string, unpackPacket(packet[i], string_data[i])); }
				catch(DataFormatException e) { restore_ok[i] = false; }
			});
			for(int i = 0; i < 3; i++)
				if(!restore_ok[i]) System.out.println("WARNING: channel " + i + " packet does not restore to the original string.");
			System.out.println("Segmentation (level " + packet_level + ") took " + ViewerSupport.formatDuration(System.nanoTime() - t0));

			t0 = System.nanoTime();
			String[] warning = new String[3];
			ViewerSupport.parallel(3, i -> coded[i] = isPerPacket(entropy_type)
				? codePackets(packet[i].segments, entropy_type, warning, i)
				: codePayload(packet[i].payload, entropy_type, warning, i));
			System.out.println("Entropy coding [" + ENTROPY_NAMES[entropy_type] + "] took " + ViewerSupport.formatDuration(System.nanoTime() - t0));
			for(int i = 0; i < 3; i++)
				if(warning[i] != null) System.out.println("WARNING: channel " + i + " " + warning[i]);

			int[] channel_id = DeltaMapper.getChannels(min_set_id);
			out.writeByte(FORMAT_ID); out.writeByte(FORMAT_VERSION);
			out.writeShort(image_xdim); out.writeShort(image_ydim); out.writeByte(pixel_shift); out.writeByte(pixel_quant);
			out.writeByte(min_set_id); out.writeByte(delta_type); out.writeByte(entropy_type); out.writeByte(scanline5_variant);
			out.writeByte(packet_level);
			for(int i = 0; i < 3; i++)
			{
				int j = channel_id[i];
				out.writeInt(channel_min[j]); out.writeInt(channel_init[j]); out.writeInt(channel_delta_min[j]);
				out.writeInt(channel_length[j]);
				if(DeltaMapper.hasMap(delta_type)) DeltaMapper.writeMap(out, delta_type, map_list.get(i), (i > 0) ? map_list.get(i - 1) : null, delta_xdim);
				DeltaMapper.writeTable(out, table_list.get(i));
				out.writeByte(string_data[i]);
				out.writeInt(packet[i].table.length); out.writeInt(packet[i].ztable.length); out.write(packet[i].ztable);
				out.write(coded[i]);
			}
		}

		// Context: per channel min, init, delta min, bit length, map (types
		// 6-13), then the context-coded deltas (DeltaMapper.packContextDeltas).
		// Channel i's contexts use channels 0..i-1, so the reader decodes in
		// order. Packets don't apply.
		private void saveContext(DataOutputStream out, byte[][] coded) throws IOException
		{
			long t0 = System.nanoTime();
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
			System.out.println("Entropy coding [" + ENTROPY_NAMES[entropy_type] + "] took " + ViewerSupport.formatDuration(System.nanoTime() - t0));

			int[] channel_id = DeltaMapper.getChannels(min_set_id);
			out.writeByte(FORMAT_ID); out.writeByte(FORMAT_VERSION);
			out.writeShort(image_xdim); out.writeShort(image_ydim); out.writeByte(pixel_shift); out.writeByte(pixel_quant);
			out.writeByte(min_set_id); out.writeByte(delta_type); out.writeByte(entropy_type); out.writeByte(scanline5_variant);
			out.writeByte(packet_level);
			for(int i = 0; i < 3; i++)
			{
				int j = channel_id[i];
				out.writeInt(channel_min[j]); out.writeInt(channel_init[j]); out.writeInt(channel_delta_min[j]);
				out.writeInt(channel_length[j]);
				if(DeltaMapper.hasMap(delta_type)) DeltaMapper.writeMap(out, delta_type, map_list.get(i), (i > 0) ? map_list.get(i - 1) : null, delta_xdim);
				out.write(coded[i]);
			}
		}

		// Per-channel breakdown: segmented vs whole string. Both layouts
		// store payload bits plus a compressed table, so
		//   total difference = payload difference + overhead difference
		// exactly (up to <8 bits of byte rounding on each payload). The last
		// line codes both payloads with the selected coder (headers and
		// tables included) plus each layout's segment table.
		private void printBreakdown(Packet[] packet, byte[][] coded)
		{
			int[]    channel_id = DeltaMapper.getChannels(min_set_id);
			Packet[] whole = new Packet[3];
			int[]    ew    = new int[3];
			ViewerSupport.parallel(3, i ->
			{
				ArrayList<byte[]> single = new ArrayList<byte[]>();
				single.add(StringMapper.compressStrings(string_list.get(i)));
				whole[i] = makePacket(single);
				ew[i]    = entropySize(whole[i].payload) + whole[i].ztable.length;
			});
			String name = ENTROPY_NAMES[entropy_type];
			System.out.println("Packet level " + packet_level + (packet_auto ? " (Auto)" : "") + ", entropy " + name);
			for(int i = 0; i < 3; i++)
			{
				Packet sp = packet[i], wp = whole[i];
				int U  = StringMapper.getBitlength(string_list.get(i)), W = wp.bits, S = sp.bits;
				int Tw = wp.ztable.length * 8, Ts = sp.ztable.length * 8;
				int d_payload = S - W, d_over = Ts - Tw;
				int es = coded[i].length + sp.ztable.length;
				System.out.println("Channel " + i + " (" + CHANNEL_NAMES[channel_id[i]] + "):");
				System.out.println(String.format("  uncompressed string          %10d bits", U));
				System.out.println(String.format("  whole string, compressed     %10d bits   ratio %.4f   table %6d bits", W, (double) W / U, Tw));
				System.out.println(String.format("  segmented (%5d segs, %5d compr) %4s%10d bits   ratio %.4f   table %6d bits (raw %d)",
					sp.segments.size(), sp.compressed, "", S, (double) S / U, Ts, sp.table.length * 8));
				System.out.println(String.format("  payload difference  (S - W)  %+10d bits", d_payload));
				System.out.println(String.format("  overhead difference (Ts - Tw)%+10d bits", d_over));
				System.out.println(String.format("  total difference             %+10d bits   (%.2f%% of whole)", d_payload + d_over, 100.0 * (d_payload + d_over) / (W + Tw)));
				System.out.println(String.format("  after %-10s whole %8d B   segmented %8d B   difference %+d B (%+.2f%%)   [entropy output + table]",
					name, ew[i], es, es - ew[i], 100.0 * (es - ew[i]) / ew[i]));
			}
		}

		// Returns the Segment Length level with the smallest output for the
		// selected entropy coder: segment table plus coded payload over the 3
		// channels (nothing else written depends on the level). Levels run
		// from 10 down; one with the same minimum segment lengths as the
		// previous reuses its result. Stops once a level is more than
		// AUTO_STOP_PERCENT above the best so far: lower levels are the
		// slowest to try, and in testing sizes only kept rising from there.
		// Ties go to the higher level (fewer packets).
		static final double AUTO_STOP_PERCENT = 3.0;

		private int chooseLevel()
		{
			long   t0         = System.nanoTime();
			long[] cost       = new long[11];
			int[]  packets_at = new int[11];
			Arrays.fill(cost, -1);
			String previous  = null;
			long   best_cost = Long.MAX_VALUE;
			for(int level = 10; level >= 0; level--)
			{
				final int L = level;
				String signature = "";
				for(int i = 0; i < 3; i++) signature += getMinimumSegmentBits(StringMapper.getBitlength(string_list.get(i)), L) + ",";
				if(signature.equals(previous)) { cost[L] = cost[L + 1]; packets_at[L] = packets_at[L + 1]; continue; }
				previous = signature;
				long[] c = new long[3];
				int[]  n = new int[3];
				ViewerSupport.parallel(3, i ->
				{
					ArrayList<byte[]> segs = segmentString(string_list.get(i), L);
					Packet p = makePacket(segs);
					n[i] = segs.size();
					// Arithmetic sizes are estimated (see estimateArithmetic);
					// LZ77, Huffman and Adaptive are coded for real.
					c[i] = p.ztable.length + ((entropy_type == ARITHMETIC_PER_PACKET) ? estimatePerPacket(segs)
					                        : (entropy_type == HUFFMAN_PER_PACKET)    ? estimateHuffmanPerPacket(segs)
					                        : (entropy_type == ADAPTIVE_PER_PACKET)   ? codePackets(segs, entropy_type, null, i).length
					                        : (entropy_type == 2)                     ? estimateArithmetic(p.payload)
					                        : entropySize(p.payload));
				});
				cost[L] = c[0] + c[1] + c[2]; packets_at[L] = n[0] + n[1] + n[2];
				if(cost[L] < best_cost) best_cost = cost[L];
				else if(cost[L] > best_cost * (1 + AUTO_STOP_PERCENT / 100)) break;
			}
			int best = 10;
			for(int level = 9; level >= 0; level--) if(cost[level] >= 0 && cost[level] < cost[best]) best = level;
			boolean estimated = entropy_type == 2 || entropy_type == ARITHMETIC_PER_PACKET || entropy_type == HUFFMAN_PER_PACKET;
			System.out.println("Auto Segment Length (" + ENTROPY_NAMES[entropy_type] + "), bytes by level" + (estimated ? " (estimated)" : "") + ":");
			for(int level = 0; level <= 10; level++)
				if(cost[level] < 0) System.out.println(String.format("  %2d  (skipped)", level));
				else System.out.println(String.format("  %2d  %8d packets  %10d B  %+7.2f%%%s", level, packets_at[level], cost[level],
					100.0 * (cost[level] - cost[10]) / cost[10], level == best ? "  <" : ""));
			System.out.println("  (% vs level 10, the whole string); chose level " + best + " in " + ViewerSupport.formatDuration(System.nanoTime() - t0));
			return best;
		}

		// ---- Size estimates for Auto -------------------------------------
		// Auto only compares levels, so Arithmetic isn't coded: a block's size
		// is predicted from its byte counts (within about 0.01% of the real
		// size in testing), and frequency tables use Deflate's default setting,
		// since the best one is very slow on thousands of tables. Save codes
		// the chosen level for real.
		private long estimateArithmetic(byte[] payload)
		{
			if(payload.length == 0) return 0;
			byte[][] block = ArithmeticMapper.getBlocks(payload, arithmeticBlocks(payload.length));
			int[][]  freq  = new int[block.length][];
			for(int m = 0; m < block.length; m++) freq[m] = ArithmeticMapper.getFrequency(block[m]);
			long size = 12 + tableEstimate(freq);
			for(int m = 0; m < block.length; m++) size += 4 + blockEstimate(freq[m], block[m].length);
			return size;
		}

		private long estimatePerPacket(ArrayList<byte[]> segs)
		{
			int[][] freq = new int[segs.size()][];
			long    size = 12;
			for(int k = 0; k < freq.length; k++)
			{
				byte[] sg = segs.get(k);
				freq[k] = ArithmeticMapper.getFrequency(Arrays.copyOf(sg, sg.length - 1));
				long b = blockEstimate(freq[k], sg.length - 1);
				size += b + CodeMapper.getVarintBytes(b);
			}
			return size + tableEstimate(freq);
		}

		// ArithmeticMapper counts each byte down as it codes it, so a block
		// with counts c costs about log2(n! / (c0! c1! ...)) bits, a little
		// under n times its entropy; plus the range coder's leading byte and
		// its flush.
		private static long blockEstimate(int[] f, int n)
		{
			if(n == 0) return 0;
			double ln = logFactorial(n);
			for(int v : f) if(v > 0) ln -= logFactorial(v);
			return (long) Math.ceil(ln / Math.log(2) / 8) + 6;
		}

		// ln(k!) by Stirling's series (plenty accurate for k >= 1).
		private static double logFactorial(int k)
		{
			if(k < 2) return 0;
			double x = k;
			return x * Math.log(x) - x + 0.5 * Math.log(2 * Math.PI * x) + 1 / (12 * x) - 1 / (360 * x * x * x);
		}

		// Huffman per packet: code lengths are cheap to compute, so this is
		// exact apart from the tables, which use Deflate's default setting.
		private long estimateHuffmanPerPacket(ArrayList<byte[]> segs)
		{
			int      n     = segs.size();
			byte[][] table = new byte[n][];
			long[]   coded = new long[n];
			ViewerSupport.parallel(n, k ->
			{
				byte[] sg   = segs.get(k);
				int[]  freq = ArithmeticMapper.getFrequency(Arrays.copyOf(sg, sg.length - 1));
				table[k] = CodeMapper.getRegularHuffmanLength(freq);
				coded[k] = CodeMapper.getRegularCodeBytes(freq, table[k]);
			});
			long size = 8 + CodeMapper.packRegularTables(table, Deflater.DEFAULT_COMPRESSION).length;
			for(long c : coded) size += c + CodeMapper.getVarintBytes(c);
			return size;
		}

		private static int tableEstimate(int[][] freq)
		{
			return ArithmeticMapper.deflateFrequencies(freq, ArithmeticMapper.getFrequencyType(freq), Deflater.DEFAULT_COMPRESSION).length;
		}
	}
}
