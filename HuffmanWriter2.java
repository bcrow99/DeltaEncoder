import java.awt.*;
import java.awt.event.*;
import java.awt.image.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import javax.swing.*;

// HuffmanWriter2 version 1.0 (writes Neighbour Huffman format 1)
//
// HuffmanWriter with a different Huffman coding: the same image, Apply,
// channels (green, blue-green and red-green, SET_ID) and bands, with a
// choice of deltas (the Delta menu, as in GenericWriter: the least-squares
// fit from the rows above, or the average filter), and each band's
// channel's deltas coded directly, with no unary strings, by Huffman codes
// chosen from the neighbours (neighbourCode):
//
//   Each delta's code table is chosen by how busy its neighbourhood is: the
//   size of the deltas already coded to its left and above (activity, a
//   weighted sum of six of them), in one of CONTEXTS - 1 steps. The reader
//   knows the same neighbours, so it makes the same choice: the file says
//   nothing about which table is used where. Two things more, for the quiet
//   parts, where most deltas are 0 and a Huffman code's one bit a delta at
//   the least would be too much:
//     run mode: where the neighbourhood is all zeros, the number of zero
//       deltas that follow (to the end of the row at most) is coded as one
//       symbol, its length's size class, with the length's other bits as
//       they are; then the delta that stopped the run;
//     pairs: in the quietest contexts (1 to PAIR_CONTEXTS), two deltas are
//       coded as one symbol, each -PAIR_RANGE to PAIR_RANGE or an escape
//       (then coded from a table of its own).
//   Each channel of each band has its own tables, made from its own
//   deltas; their code lengths are coded compactly (writeLengths), with no
//   Deflate, so the files don't depend on a zlib.
//
// On the test photos the files are 8-12% smaller than HuffmanWriter's
// lossless (Color and Pixel Resolution 0) and 1-5% smaller at the defaults,
// coded and decoded about as fast with the LS deltas and about twice as fast
// with the average deltas. (On nearly constant synthetic images, whose files
// are tiny either way, HuffmanWriter's unary strings do better: each run
// still costs about 10 bits a row.) No contexts across channels: a channel's
// coding uses no other channel. Files are read by HuffmanReader2.
/**
 * HuffmanWriter with neighbour-chosen Huffman tables in place of the unary
 * strings, and a choice of deltas (see the comment above).
 */
public class HuffmanWriter2
{
	/**
	 * Every file starts with FORMAT_ID ('N' = Neighbour Huffman format) and
	 * FORMAT_VERSION.
	 */
	static final char FORMAT_ID      = 'N';
	/**
	 * The file format's version: bump it in the writer and reader together
	 * whenever the file layout or the coder's output changes. 1: the first.
	 */
	static final int  FORMAT_VERSION = 1;
	/**
	 * The channel set coded: green, blue-green, red-green (see
	 * DeltaMapper.getChannels).
	 */
	static final int  SET_ID = 7;
	/**
	 * Its channels, in coding order (DeltaMapper's channel ids).
	 */
	static final int[] CHANNELS = DeltaMapper.getChannels(SET_ID);
	/**
	 * The least-squares window radius, for every channel.
	 */
	static final int  RADIUS = 10;
	/**
	 * How often along a row the fit is solved: at every EVERY-th pixel, the
	 * pixels between using the last solution (DeltaMapper.RowFit).
	 */
	static final int  EVERY = 4;
	/**
	 * The most bands Save offers (the file holds the number in a byte).
	 */
	static final int  MAX_BANDS = 64;
	/**
	 * The fewest rows (of the quantized image) a band may have: smaller images
	 * get fewer bands.
	 */
	static final int  BAND_MIN_ROWS = 64;
	/**
	 * The delta types (the Delta menu): the least-squares fit from the rows
	 * above (as HuffmanWriter's).
	 */
	static final int  DELTA_LS = 0;
	/**
	 * The delta types (the Delta menu): the average of the pixels left and
	 * above (DeltaMapper.getAverageDeltas).
	 */
	static final int  DELTA_AVERAGE = 1;
	/**
	 * The Delta menu's names for them.
	 */
	static final String[] DELTA_NAMES = {"LS", "Avg"};

	// ---- Image state --------------------------------------------------------
	/**
	 * The window.
	 */
	ViewerSupport view;
	/**
	 * The image as loaded.
	 */
	BufferedImage original_image;
	/**
	 * The preview.
	 */
	BufferedImage working_image;
	/**
	 * The image's file.
	 */
	String        filename;
	/** The file Save writes (ViewerSupport.DEFAULT_OUTPUT, "foo", unless another is given). */
	String        output_file;
	/**
	 * The image's width and height.
	 */
	int           image_xdim, image_ydim;
	/**
	 * Blue, green and red of the original.
	 */
	int[][]       source;

	// ---- Parameters ---------------------------------------------------------
	/**
	 * The Pixel Resolution (ResizeMapper.resize2's reduction).
	 */
	int pixel_quant = 4;
	/**
	 * The Color Resolution (bits dropped from each value).
	 */
	int pixel_shift = 3;
	/**
	 * The Error Correction (preview only).
	 */
	int correction  = 0;
	/**
	 * How many independently decodable bands Save writes; at most MAX_BANDS,
	 * and fewer if bands would have under BAND_MIN_ROWS rows.
	 */
	int bands       = 1;
	/**
	 * The deltas Save codes: DELTA_LS or DELTA_AVERAGE (the Delta menu).
	 */
	int delta_type  = DELTA_LS;
	/**
	 * Whether Save checks its coding by decoding it back (the Decode Check
	 * item in the File menu): off unless switched on.
	 */
	boolean decode_check = false;

	/**
	 * The Quantization sliders (Reset moves them).
	 */
	JSlider pquant_slider, pshift_slider, corr_slider;

	// ---- Results of the last Apply (read by Save) ---------------------------
	/**
	 * The channels as quantized, in coding order, each difference channel
	 * less its minimum: what Save codes.
	 */
	int[][]  value_list = new int[3][];
	/**
	 * Each channel's minimum (added back to the difference channels by the
	 * reader), in coding order.
	 */
	int[]    channel_min = new int[3];
	/**
	 * Their width.
	 */
	int      delta_xdim;

	/**
	 * The image file's length.
	 */
	long    file_length;
	/**
	 * The last Apply finished; Save needs it.
	 */
	boolean applied  = false;

	/**
	 * Opens the image named, or one chosen in a file dialog.
	 *
	 * @param args the image, or nothing
	 */
	public static void main(String[] args)
	{
		ViewerSupport.applyHiDpiFontScaleIfNeeded();
		// The image, and the file Save writes ("foo" if none is given).
		if(args.length >= 1) { new HuffmanWriter2(args[0], (args.length > 1) ? args[1] : ViewerSupport.DEFAULT_OUTPUT); return; }
		FileDialog fd = new FileDialog((java.awt.Frame) null, "Open Image", FileDialog.LOAD);
		fd.setVisible(true);
		if(fd.getFile() != null) new HuffmanWriter2(new File(fd.getDirectory(), fd.getFile()).getPath());
		else System.exit(0);
	}

	/**
	 * Loads an image and opens its window.
	 *
	 * @param _filename the image
	 */
	public HuffmanWriter2(String _filename)
	{
		this(_filename, ViewerSupport.DEFAULT_OUTPUT);
	}

	/**
	 * Loads an image and opens its window; Save writes output_file.
	 *
	 * @param _filename    the image
	 * @param _output_file the file Save writes
	 */
	public HuffmanWriter2(String _filename, String _output_file)
	{
		filename    = _filename;
		output_file = _output_file;
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
		System.out.println(String.format("Loaded %s, %d x %d (%,d pixels)", filename, image_xdim, image_ydim, (long) image_xdim * image_ydim));

		int[] pixel = original_image.getRGB(0, 0, image_xdim, image_ydim, null, 0, image_xdim);
		source = new int[3][pixel.length];
		for(int i = 0; i < pixel.length; i++)
		{
			source[0][i] = (pixel[i] >> 16) & 0xff; source[1][i] = (pixel[i] >> 8) & 0xff; source[2][i] = pixel[i] & 0xff;
		}
		working_image = new BufferedImage(image_xdim, image_ydim, BufferedImage.TYPE_INT_RGB);
		ViewerSupport.setPixels(working_image, pixel);

		view = new ViewerSupport("Huffman Writer 2  " + filename, image_xdim, image_ydim);
		JFrame   frame    = view.frame;
		JMenuBar menu_bar = frame.getJMenuBar();

		JMenu file_menu = new JMenu("File");
		JMenuItem open_item = new JMenuItem("Open...");
		open_item.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_O, InputEvent.CTRL_DOWN_MASK));
		open_item.addActionListener(e -> { FileDialog fd = new FileDialog(frame, "Open Image", FileDialog.LOAD); fd.setVisible(true); if(fd.getFile() != null) new HuffmanWriter2(fd.getDirectory() + fd.getFile()); });
		file_menu.add(open_item); file_menu.addSeparator();
		JMenuItem reset_item = new JMenuItem("Reset");
		reset_item.addActionListener(e -> { pixel_quant = 0; pixel_shift = 0; correction = 0; pquant_slider.setValue(0); pshift_slider.setValue(0); corr_slider.setValue(0); apply(); });
		file_menu.add(reset_item);
		JMenuItem save_item = new JMenuItem("Save"); save_item.addActionListener(new SaveHandler()); file_menu.add(save_item);
		JCheckBoxMenuItem check_item = new JCheckBoxMenuItem("Decode Check", decode_check);
		check_item.addActionListener(e -> decode_check = check_item.isSelected());
		file_menu.add(check_item);

		JMenu quant_menu = new JMenu("Quantization");
		JSlider[] ss = new JSlider[1];
		quant_menu.add(ViewerSupport.makeSliderDialog(frame, "Pixel Resolution", 0, 10, pixel_quant, v -> { pixel_quant = v; apply(); }, ss)); pquant_slider = ss[0];
		quant_menu.add(ViewerSupport.makeSliderDialog(frame, "Color Resolution", 0, 7, pixel_shift, v -> { pixel_shift = v; apply(); }, ss)); pshift_slider = ss[0];
		// Error Correction (below a separator) is not a quantizing step: it blends
		// the preview back toward the original by correction/10. Preview only;
		// the saved file is unaffected.
		quant_menu.addSeparator();
		quant_menu.add(ViewerSupport.makeSliderDialog(frame, "Error Correction", 0, 10, correction, v -> { correction = v; apply(); }, ss)); corr_slider = ss[0];

		// Delta menu: the least-squares fit or the average filter. Like Save
		// itself, it never changes the preview.
		JMenu delta_menu = new JMenu("Delta");
		ButtonGroup delta_group = new ButtonGroup();
		for(int t = 0; t < DELTA_NAMES.length; t++)
		{
			int type = t;
			JRadioButtonMenuItem item = new JRadioButtonMenuItem(DELTA_NAMES[t], t == delta_type);
			item.addActionListener(e -> delta_type = type);
			delta_group.add(item);
			delta_menu.add(item);
		}

		// Bands menu: Save codes the image as this many bands of rows, each
		// decodable on its own (so a reader can decode them at the same time),
		// for a small cost per band boundary. Like Save itself, it never
		// changes the preview.
		JMenu bands_menu = new JMenu("Bands");
		JSlider[] bs = new JSlider[1];
		bands_menu.add(ViewerSupport.makeSliderDialog(frame, "Number of Bands", 1, MAX_BANDS, bands, v -> { bands = v; view.setStatus(bandText()); }, bs));

		menu_bar.add(file_menu); menu_bar.add(view.makeViewMenu()); menu_bar.add(quant_menu); menu_bar.add(delta_menu); menu_bar.add(bands_menu);

		view.setImage(original_image);
		view.show();
		SwingUtilities.invokeLater(this::apply);
	}

	// ---- Bands --------------------------------------------------------------

	/**
	 * The bands Save will write: bands, but no more than leave BAND_MIN_ROWS
	 * rows in each.
	 *
	 * @return the number of bands
	 */
	int bandsUsed()
	{
		int rows = DeltaMapper.getQuantizedSize(image_xdim, image_ydim, pixel_quant)[1];
		return Math.max(1, Math.min(bands, rows / BAND_MIN_ROWS));
	}

	/**
	 * The Bands dialog's status line.
	 *
	 * @return the bands Save will write, in words
	 */
	private String bandText()
	{
		int n = bandsUsed();
		if(n == 1) return (bands > 1) ? "1 band: the image is too small for more" : "1 band";
		return SimpleWriter.describeBands(n, DeltaMapper.getQuantizedSize(image_xdim, image_ydim, pixel_quant)[1]);
	}

	// ---- Apply --------------------------------------------------------------

	/**
	 * Blue, green and red, resized and quantized.
	 *
	 * @param size  the quantized size
	 * @param quant the Pixel Resolution
	 * @param shift the Color Resolution
	 * @return the channels
	 */
	int[][] quantizedChannels(int[] size, int quant, int shift)
	{
		int[][] q = new int[3][];
		ViewerSupport.parallel(3, i ->
		{
			int[] ch = source[i];
			if(quant != 0) ch = ResizeMapper.resize2(ch, image_xdim, size[0], size[1]);
			q[i] = DeltaMapper.quantizeChannel(ch, shift);
		});
		return q;
	}

	/**
	 * Quantizes (the channels Save codes), then rebuilds the image for the
	 * preview.
	 */
	void apply()
	{
		applied = false;
		try { applyImpl(); applied = true; }
		catch(Exception e) { System.out.println("Apply: " + e); e.printStackTrace(); }
	}

	/**
	 * Apply's work (apply catches its exceptions).
	 */
	private void applyImpl()
	{
		int[]   size = DeltaMapper.getQuantizedSize(image_xdim, image_ydim, pixel_quant);
		int     new_xdim = size[0];
		int[][] q = quantizedChannels(size, pixel_quant, pixel_shift);
		int[]   min = new int[6];
		int[][] qc = DeltaMapper.getCandidateChannels(q[0], q[1], q[2], min);
		int[][] v = new int[3][];
		int[]   m = new int[3];
		for(int i = 0; i < 3; i++) { v[i] = qc[CHANNELS[i]]; m[i] = min[CHANNELS[i]]; }
		value_list  = v;
		channel_min = m;
		delta_xdim  = new_xdim;

		// Like HuffmanReader2: resize (centred: resize2), then shift. The reader
		// rebuilds blue, green and red exactly as quantized, so the preview
		// uses them as they are: no need to decode.
		int[][] bgr = q;
		ViewerSupport.parallel(3, c ->
		{
			if(pixel_quant != 0) bgr[c] = ResizeMapper.resize2(bgr[c], new_xdim, image_xdim, image_ydim);
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
		ViewerSupport.setPixels(working_image, rgb);
		view.setImage(working_image);
	}

	// ---- Save ---------------------------------------------------------------

	/**
	 * File &gt; Save: codes the image (encode) into output_file ("foo" unless
	 * another is given), twice, and prints the times
	 * (ViewerSupport.printRunTimes), without the decode check, and the
	 * check's time.
	 */
	class SaveHandler implements ActionListener
	{
		/**
		 * A handler for the Save item.
		 */
		SaveHandler() {}

		/**
		 * Saves.
		 *
		 * @param event the menu item's event
		 */
		public void actionPerformed(ActionEvent event)
		{
			if(!applied) apply();
			if(!applied) { ViewerSupport.showError(view.frame, "Nothing saved: the last Apply failed."); return; }
			// The image is coded twice (ViewerSupport.printRunTimes: the first
			// Save's first run includes the JIT's warm-up, so its times are printed
			// apart; after that, the two timed together, halved). Both leave out the
			// decode check (if it's on: the Decode Check item), which is timed on
			// its own. Both give the same bytes.
			File file = new File(output_file);
			long[] took = new long[2], check = new long[2];
			long   both;
			try
			{
				// Each run timed, and the two together (the decode checks taken
				// out of both); the bytes compared after them.
				byte[][] got = new byte[2][];
				long all = System.nanoTime();
				for(int pass = 0; pass < 2; pass++)
				{
					ByteArrayOutputStream bytes = new ByteArrayOutputStream();
					long start = System.nanoTime();
					check[pass] = encode(new DataOutputStream(bytes), value_list, channel_min, delta_xdim, image_xdim, image_ydim, pixel_shift, pixel_quant, bandsUsed(), delta_type, decode_check);
					took[pass] = System.nanoTime() - start - check[pass];
					got[pass] = bytes.toByteArray();
				}
				both = System.nanoTime() - all - check[0] - check[1];
				if(!Arrays.equals(got[0], got[1])) System.out.println("WARNING: the second coding differs from the first.");
				byte[] data = got[1];
				try(OutputStream out = new BufferedOutputStream(new FileOutputStream(file))) { out.write(data); }
			}
			catch(Exception e)
			{
				file.delete();
				ViewerSupport.showError(view.frame, "Save failed: " + e);
				e.printStackTrace();
				return;
			}
			int n = bandsUsed(), raw = image_xdim * image_ydim * 3, rows = value_list[0].length / delta_xdim;
			if(n > 1) System.out.println(String.format("Saved %s: %,d bytes (%s)", file.getName(), file.length(), SimpleWriter.describeBands(n, rows)));
			else      System.out.println(String.format("Saved %s: %,d bytes", file.getName(), file.length()));
			System.out.println(String.format("Coded %d x %d (%,d pixels), %s deltas", delta_xdim, rows, (long) delta_xdim * rows, DELTA_NAMES[delta_type]));
			boolean averaged = ViewerSupport.printRunTimes("HuffmanWriter2 " + DELTA_NAMES[delta_type], took[0], took[1], both);
			long    check_time = averaged ? (check[0] + check[1]) / 2 : check[1];
			System.out.println("Decode check:                     " + (decode_check ? ViewerSupport.formatDuration(check_time) : "off"));
			System.out.println("Original compression rate: " + String.format("%.4f", (double) file_length / raw));
			System.out.println("Output  compression rate:  " + String.format("%.4f", (double) file.length() / raw));
			System.out.println();
		}
	}

	/**
	 * Codes the channels and writes the file. Format 1. Header: FORMAT_ID,
	 * FORMAT_VERSION, width, height (unsigned shorts), pixel_shift,
	 * pixel_quant, the delta type (DELTA_LS or DELTA_AVERAGE, a byte); per
	 * channel (coding order) int min; the number of bands n (an unsigned
	 * byte), n int lengths, and the n bands. Band b is rows
	 * SimpleWriter.bandStart(b) to bandStart(b + 1) of the quantized image,
	 * coded as an image of its own: per channel (green, blue-green,
	 * red-green), int init, then its deltas neighbour Huffman coded
	 * (neighbourCode, see there for the layout). With the LS deltas, the
	 * least-squares fit from the rows above (DeltaMapper.RowFit: radius
	 * RADIUS, solved at every EVERY-th pixel along a row; channels 1 and 2
	 * use the ones before them, as in HuffmanWriter); with the average
	 * deltas, each channel on its own (DeltaMapper.getAverageDeltas). The
	 * bands can be decoded at the same time.
	 * <p>
	 * With the LS deltas each channel is fitted once over the whole image (the
	 * fit splits itself across the cores) and the bands' deltas come from it,
	 * with only each band's first rows fitted again
	 * (DeltaMapper.getRowFitBandDeltas). A channel's coding needs only its
	 * own deltas, so its bands are coded while the next channel is fitted.
	 * Once everything is coded, if check is set, each band's channels are
	 * checked by decoding them back, at the same time; the time that took is
	 * returned, so the coding can be timed without it.
	 *
	 * @param out         the stream
	 * @param value       the channels as quantized, in coding order (difference channels less their minimum)
	 * @param min         each channel's minimum, in coding order
	 * @param xdim        their width
	 * @param image_xdim  the image's width
	 * @param image_ydim  the image's height
	 * @param pixel_shift the Color Resolution
	 * @param pixel_quant the Pixel Resolution
	 * @param n           the number of bands
	 * @param delta_type  DELTA_LS or DELTA_AVERAGE
	 * @param check       whether to check the coding by decoding it back (the Decode Check menu item)
	 * @return the time the decode check took, in nanoseconds (0 without it)
	 * @throws IOException if the stream fails
	 */
	static long encode(DataOutputStream out, int[][] value, int[] min, int xdim, int image_xdim, int image_ydim, int pixel_shift, int pixel_quant, int n,
	                   int delta_type, boolean check) throws IOException
	{
		long check_time = 0;
		int rows = value[0].length / xdim;
		int[][][]  d = new int[n][3][];      // [band][channel]
		byte[][][] coded = new byte[n][3][];
		ExecutorService coder = Executors.newFixedThreadPool(Math.max(1, Math.min(3 * n, Runtime.getRuntime().availableProcessors())));
		java.util.List<Future<?>> jobs = new ArrayList<>();
		try
		{
			// The average deltas take no fit: every band's channels at once.
			if(delta_type == DELTA_AVERAGE)
				ViewerSupport.parallel(3 * n, job ->
				{
					int b = job / 3, i = job % 3;
					int y0 = SimpleWriter.bandStart(b, n, rows), y1 = SimpleWriter.bandStart(b + 1, n, rows);
					d[b][i] = new int[(y1 - y0) * xdim];
					DeltaMapper.getAverageDeltas(value[i], y0 * xdim, xdim, y1 - y0, d[b][i]);   // (straight from the channel: no copy of the band)
				});
			for(int i = 0; i < 3; i++)
			{
				int   fi = i;
				if(delta_type == DELTA_LS)
				{
					int[] ref = (i > 0) ? value[i - 1] : null;
					int[] whole = DeltaMapper.getRowFitDeltas(value[i], ref, xdim, rows, RADIUS, EVERY);
					// Each band's deltas from the whole channel's: only its first
					// rows fitted again, all the bands at the same time.
					if(n == 1) d[0][i] = whole;
					else ViewerSupport.parallel(n, b ->
					{
						int y0 = SimpleWriter.bandStart(b, n, rows), y1 = SimpleWriter.bandStart(b + 1, n, rows);
						d[b][fi] = DeltaMapper.getRowFitBandDeltas(whole, value[fi], ref, xdim, y0, y1, RADIUS, EVERY);
					});
				}
				for(int b = 0; b < n; b++)
				{
					int fb = b;
					jobs.add(coder.submit(() ->
					{
						coded[fb][fi] = neighbourCode(d[fb][fi], xdim);
						return null;
					}));
				}
			}
			waitFor(jobs);
			if(check)
			{
				// The check, once everything is coded (so its time can be told
				// apart): each band's channels decoded back, at the same time.
				long check_start = System.nanoTime();
				jobs.clear();
				for(int b = 0; b < n; b++)
					for(int i = 0; i < 3; i++)
					{
						int fb = b, fi = i;
						jobs.add(coder.submit(() ->
						{
							int[] dd = d[fb][fi];
							int[] back = neighbourDecode(neighbourRead(new DataInputStream(new ByteArrayInputStream(coded[fb][fi]))), xdim, dd.length / xdim);
							// (Delta 0 isn't coded: the band's first value, init, is written instead.)
							boolean same = back.length == dd.length;
							for(int k = 1; k < dd.length && same; k++) same = back[k] == dd[k];
							if(!same)
								System.out.println("WARNING: band " + fb + " channel " + fi + " deltas do not decode back.");
							return null;
						}));
					}
				waitFor(jobs);
				check_time = System.nanoTime() - check_start;
			}
		}
		finally { coder.shutdownNow(); }

		byte[][] band = new byte[n][];
		for(int b = 0; b < n; b++)
		{
			int y0 = SimpleWriter.bandStart(b, n, rows);
			ByteArrayOutputStream bytes = new ByteArrayOutputStream();
			DataOutputStream bo = new DataOutputStream(bytes);
			for(int i = 0; i < 3; i++) { bo.writeInt(value[i][y0 * xdim]); bo.write(coded[b][i]); }
			band[b] = bytes.toByteArray();
		}
		out.writeByte(FORMAT_ID); out.writeByte(FORMAT_VERSION);
		out.writeShort(image_xdim); out.writeShort(image_ydim);
		out.writeByte(pixel_shift); out.writeByte(pixel_quant);
		out.writeByte(delta_type);
		for(int i = 0; i < 3; i++) out.writeInt(min[i]);
		out.writeByte(n);
		for(int b = 0; b < n; b++) out.writeInt(band[b].length);
		for(int b = 0; b < n; b++) out.write(band[b]);
		return check_time;
	}

	/**
	 * Waits for jobs, passing on the first one's failure.
	 *
	 * @param jobs the jobs
	 * @throws IOException if one failed with one, or was interrupted
	 */
	private static void waitFor(java.util.List<Future<?>> jobs) throws IOException
	{
		for(Future<?> f : jobs)
		{
			try { f.get(); }
			catch(InterruptedException e) { Thread.currentThread().interrupt(); throw new IOException(e); }
			catch(ExecutionException e)
			{
				Throwable c = e.getCause();
				if(c instanceof IOException) throw (IOException) c;
				if(c instanceof RuntimeException) throw (RuntimeException) c;
				if(c instanceof Error) throw (Error) c;
				throw new IOException(c);
			}
		}
	}

	// =========================================================================
	// The neighbour Huffman coding
	// =========================================================================

	/**
	 * The number of contexts: 0 (run mode: the neighbourhood all zeros) and 1
	 * to CONTEXTS - 1 by the neighbourhood's activity (context).
	 */
	static final int   CONTEXTS = 16;
	/**
	 * Where contexts 2 to CONTEXTS - 1 start: context c (above 0) is 1 plus
	 * the number of these the activity reaches (about half a power of 2
	 * apart).
	 */
	static final int[] ACTIVITY_STEPS = {2, 3, 4, 6, 8, 12, 16, 23, 32, 46, 64, 91, 128, 181};
	/**
	 * Contexts 1 to PAIR_CONTEXTS code two deltas at a time (a pair symbol).
	 */
	static final int   PAIR_CONTEXTS = 6;
	/**
	 * A pair symbol's deltas: -PAIR_RANGE to PAIR_RANGE, or PAIR_ESCAPE for
	 * anything else (then coded from the escape table).
	 */
	static final int   PAIR_RANGE = 3;
	/**
	 * The pair symbols' escape: the delta is coded after, from the escape table.
	 */
	static final int   PAIR_ESCAPE = 2 * PAIR_RANGE + 1;
	/**
	 * The values a pair symbol's delta takes (2 PAIR_RANGE + 1, and the escape).
	 */
	static final int   PAIR_WIDTH = PAIR_ESCAPE + 1;
	/**
	 * Run lengths' size classes: 0 for none, else the length's bit count (a
	 * run is at most a row, under 65,536).
	 */
	static final int   RUN_SYMBOLS = 18;
	/**
	 * The tables, one set per channel of each band: the run lengths' size
	 * classes.
	 */
	static final int   RUN_TABLE = 0;
	/**
	 * The tables: single deltas in context c are table c (1 to CONTEXTS - 1);
	 * pairs in context c are table PAIR_TABLE + c (1 to PAIR_CONTEXTS).
	 */
	static final int   PAIR_TABLE = CONTEXTS - 1;
	/**
	 * The tables: the delta that stops a run.
	 */
	static final int   STOP_TABLE = PAIR_TABLE + PAIR_CONTEXTS + 1;
	/**
	 * The tables: the pairs' escaped deltas.
	 */
	static final int   ESCAPE_TABLE = STOP_TABLE + 1;
	/**
	 * How many tables.
	 */
	static final int   TABLES = ESCAPE_TABLE + 1;
	/**
	 * The longest code a table may have.
	 */
	static final int   MAX_CODE_LENGTH = 20;
	/**
	 * The code lengths' coding (writeLengths): lengths 0 to MAX_CODE_LENGTH
	 * as they are, then three more symbols: the last length again, 3 to 6
	 * times (2 bits more); 3 to 10 zeros (3 bits); 11 to 138 zeros (7 bits).
	 */
	static final int   LENGTH_REPEAT = MAX_CODE_LENGTH + 1, LENGTH_ZEROS = LENGTH_REPEAT + 1, LENGTH_MANY_ZEROS = LENGTH_ZEROS + 1, LENGTH_SYMBOLS = LENGTH_MANY_ZEROS + 1;
	/**
	 * The longest code in the code lengths' own code (its lengths are
	 * written in 4 bits each).
	 */
	static final int   MAX_LENGTH_CODE_LENGTH = 15;
	/**
	 * How many bits the decoder's lookup tables take at once; longer codes
	 * are found from the canonical code (Table.decode).
	 */
	static final int   LOOKUP_BITS = 10;

	/**
	 * A delta's neighbourhood activity: how big the deltas already coded near
	 * it are. Inside the image, 2 |left| + 2 |above| + |above left| + |above
	 * right| + |two left| + |two above| (on the second row, above again for two
	 * above); on the first row 8 |left|, in the first column 8 |above|, in
	 * the second and last columns 4 (|left| + |above|). 0 only if they're all
	 * 0 (run mode).
	 *
	 * @param d    the deltas (d[0] is 0)
	 * @param k    the delta's index (at least 1)
	 * @param x    its column
	 * @param y    its row
	 * @param xdim the width
	 * @return the activity
	 */
	static int activity(int[] d, int k, int x, int y, int xdim)
	{
		if(y == 0) return 8 * Math.abs(d[k - 1]);
		if(x == 0) return 8 * Math.abs(d[k - xdim]);
		if(x == 1 || x == xdim - 1) return 4 * (Math.abs(d[k - 1]) + Math.abs(d[k - xdim]));
		int two_above = (y >= 2) ? d[k - 2 * xdim] : d[k - xdim];
		return 2 * Math.abs(d[k - 1]) + 2 * Math.abs(d[k - xdim]) + Math.abs(d[k - xdim - 1]) + Math.abs(d[k - xdim + 1])
		       + Math.abs(d[k - 2]) + Math.abs(two_above);
	}

	/**
	 * The context of an activity: 0 for 0, else 1 plus the number of
	 * ACTIVITY_STEPS it reaches.
	 *
	 * @param a the activity
	 * @return the context, 0 to CONTEXTS - 1
	 */
	static int context(int a)
	{
		if(a == 0) return 0;
		int c = 1;
		while(c - 1 < ACTIVITY_STEPS.length && a >= ACTIVITY_STEPS[c - 1]) c++;
		return c;
	}

	/**
	 * A delta's value in a pair symbol: delta + PAIR_RANGE, or PAIR_ESCAPE.
	 *
	 * @param delta the delta
	 * @return its value
	 */
	private static int pairValue(int delta)
	{
		return (delta >= -PAIR_RANGE && delta <= PAIR_RANGE) ? delta + PAIR_RANGE : PAIR_ESCAPE;
	}

	/**
	 * A channel's deltas coded with neighbour-chosen Huffman tables (see the
	 * comment at the top). The deltas are walked in order, and at each one,
	 * from its neighbourhood's activity: in run mode (activity 0), the run of
	 * zero deltas from it to the end of the row at most (its length's size
	 * class from the run table, then the length's bits below its top bit as
	 * they are), then, if the run stopped before the row's end, the delta
	 * that stopped it (stop table); in contexts 1 to PAIR_CONTEXTS, if the
	 * next delta is in the same row, the two as one pair symbol (pair table),
	 * then any escaped ones (escape table); otherwise the delta (its
	 * context's table). The single, stop and escape tables code delta - lo,
	 * lo the smallest delta. Layout: int lo, int the number of delta values
	 * (hi - lo + 1), int the number of bytes that follow, and those bytes: a
	 * bit stream (most significant bit first) of the tables' code lengths
	 * (writeLengths), then the codes. Delta 0 isn't coded (the band's first
	 * value is written instead) and is taken as 0.
	 *
	 * @param delta the deltas (not changed)
	 * @param xdim  the width
	 * @return the coded bytes
	 * @throws IOException never in practice (a byte stream)
	 */
	static byte[] neighbourCode(int[] delta, int xdim) throws IOException
	{
		int n = delta.length;
		int[] d = delta.clone();
		d[0] = 0;
		int lo = 0, hi = 0;
		if(n > 1)
		{
			lo = hi = d[1];
			for(int k = 2; k < n; k++) { if(d[k] < lo) lo = d[k]; else if(d[k] > hi) hi = d[k]; }
		}
		int values = hi - lo + 1;

		// The symbols counted, the tables made, then the symbols coded.
		int[][] frequency = new int[TABLES][];
		for(int t = 0; t < TABLES; t++) frequency[t] = new int[tableSize(t, values)];
		walk(d, xdim, lo, frequency, null);
		Table[] table = new Table[TABLES];
		int[][] length = new int[TABLES][];
		for(int t = 0; t < TABLES; t++) { length[t] = codeLengths(frequency[t], MAX_CODE_LENGTH); table[t] = new Table(length[t]); }
		BitWriter bits = new BitWriter(n / 2 + 1024);
		writeLengths(bits, length);
		walk(d, xdim, lo, table, bits);
		byte[] code = bits.toByteArray();

		ByteArrayOutputStream bytes = new ByteArrayOutputStream(code.length + 12);
		DataOutputStream out = new DataOutputStream(bytes);
		out.writeInt(lo);
		out.writeInt(values);
		out.writeInt(code.length);
		out.write(code);
		return bytes.toByteArray();
	}

	/**
	 * A table's number of symbols.
	 *
	 * @param t      the table
	 * @param values the number of delta values
	 * @return its size
	 */
	static int tableSize(int t, int values)
	{
		if(t == RUN_TABLE) return RUN_SYMBOLS;
		if(t > PAIR_TABLE && t <= PAIR_TABLE + PAIR_CONTEXTS) return PAIR_WIDTH * PAIR_WIDTH;
		return values;
	}

	/**
	 * neighbourCode's walk through the deltas: counts the symbols into
	 * frequency (if bits is null) or codes them with the tables into bits.
	 *
	 * @param d         the deltas (d[0] is 0)
	 * @param xdim      the width
	 * @param lo        the smallest delta
	 * @param frequency the counts, a table's symbols each (counting), or null
	 * @param bits      the bit stream (coding), or null
	 */
	private static void walk(int[] d, int xdim, int lo, int[][] frequency, BitWriter bits)
	{
		walk(d, xdim, lo, frequency, null, bits);
	}

	/**
	 * neighbourCode's walk through the deltas, coding with the tables.
	 *
	 * @param d     the deltas (d[0] is 0)
	 * @param xdim  the width
	 * @param lo    the smallest delta
	 * @param table the tables
	 * @param bits  the bit stream
	 */
	private static void walk(int[] d, int xdim, int lo, Table[] table, BitWriter bits)
	{
		walk(d, xdim, lo, null, table, bits);
	}

	/**
	 * neighbourCode's walk through the deltas: each symbol counted into
	 * frequency (if it's not null), or coded with table into bits.
	 *
	 * @param d         the deltas (d[0] is 0)
	 * @param xdim      the width
	 * @param lo        the smallest delta
	 * @param frequency the counts, or null
	 * @param table     the tables, or null
	 * @param bits      the bit stream, or null
	 */
	private static void walk(int[] d, int xdim, int lo, int[][] frequency, Table[] table, BitWriter bits)
	{
		boolean counting = frequency != null;
		int n = d.length;
		for(int k = 1; k < n; )
		{
			int y = k / xdim, x = k - y * xdim, end = (y + 1) * xdim;
			int a = activity(d, k, x, y, xdim);
			if(a == 0)
			{
				// Run mode: the zeros that follow, to the row's end at most,
				// then the delta that stopped them.
				int r = 0;
				while(k + r < end && d[k + r] == 0) r++;
				int size = 32 - Integer.numberOfLeadingZeros(r);
				if(counting) frequency[RUN_TABLE][size]++;
				else
				{
					table[RUN_TABLE].write(bits, size);
					if(size > 1) bits.write(r & ((1 << (size - 1)) - 1), size - 1);
				}
				k += r;
				if(k < end)
				{
					if(counting) frequency[STOP_TABLE][d[k] - lo]++;
					else         table[STOP_TABLE].write(bits, d[k] - lo);
					k++;
				}
				continue;
			}
			int c = context(a);
			if(c <= PAIR_CONTEXTS && k + 1 < end)
			{
				// Two deltas as one symbol, then any escaped one.
				int u = pairValue(d[k]), v = pairValue(d[k + 1]);
				int t = PAIR_TABLE + c, s = u * PAIR_WIDTH + v;
				if(counting) frequency[t][s]++;
				else         table[t].write(bits, s);
				for(int j = 0; j < 2; j++)
				{
					if((j == 0 ? u : v) != PAIR_ESCAPE) continue;
					if(counting) frequency[ESCAPE_TABLE][d[k + j] - lo]++;
					else         table[ESCAPE_TABLE].write(bits, d[k + j] - lo);
				}
				k += 2;
				continue;
			}
			if(counting) frequency[c][d[k] - lo]++;
			else         table[c].write(bits, d[k] - lo);
			k++;
		}
	}

	/**
	 * A channel's coding as neighbourCode wrote it, read by neighbourRead.
	 */
	static final class NeighbourChannel
	{
		/** The smallest delta. */
		int    lo;
		/** The number of delta values. */
		int    values;
		/** The bit stream. */
		byte[] code;

		/** An empty one, for neighbourRead to fill in. */
		NeighbourChannel() { }
	}

	/**
	 * Reads a channel's coding neighbourCode wrote.
	 *
	 * @param in the stream, at the start of it
	 * @return the coding
	 * @throws IOException if the stream fails or isn't what neighbourCode writes
	 */
	static NeighbourChannel neighbourRead(DataInputStream in) throws IOException
	{
		NeighbourChannel h = new NeighbourChannel();
		h.lo     = in.readInt();
		h.values = in.readInt();
		int length = in.readInt();
		if(h.values < 1 || length < 0) throw new IOException("bad neighbour Huffman coding");
		h.code = new byte[length];
		in.readFully(h.code);
		return h;
	}

	/**
	 * A channel's deltas from its neighbour Huffman coding: neighbourCode's
	 * walk, the other way.
	 *
	 * @param h    the coding
	 * @param xdim the width
	 * @param ydim the band's height
	 * @return the deltas (delta 0 is 0)
	 * @throws IOException if the coding isn't what neighbourCode writes
	 */
	static int[] neighbourDecode(NeighbourChannel h, int xdim, int ydim) throws IOException
	{
		int n = xdim * ydim, lo = h.lo;
		BitReader bits = new BitReader(h.code);
		int[][] length = readLengths(bits, h.values);
		Table[] table = new Table[TABLES];
		for(int t = 0; t < TABLES; t++) table[t] = new Table(length[t]);
		Table run_table = table[RUN_TABLE], stop_table = table[STOP_TABLE], escape_table = table[ESCAPE_TABLE];
		int[] d = new int[n];
		for(int k = 1; k < n; )
		{
			int y = k / xdim, x = k - y * xdim, end = (y + 1) * xdim;
			int a = activity(d, k, x, y, xdim);
			if(a == 0)
			{
				int size = run_table.read(bits);
				int r = (size <= 1) ? size : (1 << (size - 1)) | bits.read(size - 1);
				if(r > end - k) throw new IOException("bad neighbour Huffman coding: a run past the row's end");
				k += r;   // (the zeros are there already)
				if(k < end) d[k++] = stop_table.read(bits) + lo;
				continue;
			}
			int c = context(a);
			if(c <= PAIR_CONTEXTS && k + 1 < end)
			{
				int s = table[PAIR_TABLE + c].read(bits);
				int u = s / PAIR_WIDTH, v = s - u * PAIR_WIDTH;
				d[k]     = (u == PAIR_ESCAPE) ? escape_table.read(bits) + lo : u - PAIR_RANGE;
				d[k + 1] = (v == PAIR_ESCAPE) ? escape_table.read(bits) + lo : v - PAIR_RANGE;
				k += 2;
				continue;
			}
			d[k++] = table[c].read(bits) + lo;
		}
		if(bits.overrun()) throw new IOException("bad neighbour Huffman coding: it ends too soon");
		return d;
	}

	// ---- Code lengths ---------------------------------------------------------

	/**
	 * Huffman code lengths for these counts, none longer than limit: 0 for the
	 * symbols not used, 1 for the only one if there's one. The code is made
	 * the usual way (the two least frequent merged, again and again; ties go
	 * to the symbols first, then the merged ones in the order made, symbols
	 * in order of count, then value), then, if any code is too long, the
	 * lengths are brought within limit as JPEG does it (its Annex K.3) and
	 * given out again, shortest first, in the order of the first lengths
	 * (then of value).
	 *
	 * @param frequency the counts
	 * @param limit     the longest code allowed
	 * @return the lengths
	 */
	static int[] codeLengths(int[] frequency, int limit)
	{
		int size = frequency.length;
		int[] length = new int[size];
		int used = 0;
		for(int s = 0; s < size; s++) if(frequency[s] > 0) used++;
		if(used == 0) return length;
		if(used == 1)
		{
			for(int s = 0; s < size; s++) if(frequency[s] > 0) length[s] = 1;
			return length;
		}

		// The symbols by count, then value; then the merging, from two queues:
		// the symbols in that order, and the merged nodes in the order made.
		long[] key = new long[used];
		for(int s = 0, j = 0; s < size; s++) if(frequency[s] > 0) key[j++] = ((long) frequency[s] << 32) | s;
		Arrays.sort(key);
		int nodes = 2 * used - 1;
		long[] weight = new long[nodes];
		int[]  parent = new int[nodes];
		for(int j = 0; j < used; j++) weight[j] = key[j] >>> 32;
		int leaf = 0, merged = used;
		for(int next = used; next < nodes; next++)
		{
			int[] two = new int[2];
			for(int m = 0; m < 2; m++)
			{
				if(leaf < used && (merged >= next || weight[leaf] <= weight[merged])) two[m] = leaf++;
				else two[m] = merged++;
			}
			weight[next] = weight[two[0]] + weight[two[1]];
			parent[two[0]] = parent[two[1]] = next;
		}
		int[] depth = new int[nodes];
		int longest = 0;
		for(int v = nodes - 2; v >= 0; v--)
		{
			depth[v] = depth[parent[v]] + 1;
			if(v < used && depth[v] > longest) longest = depth[v];
		}
		for(int j = 0; j < used; j++) length[(int) (key[j] & 0xffffffffL)] = depth[j];
		if(longest <= limit) return length;

		// Too long: the number of codes of each length brought within limit
		// (JPEG's Annex K.3), then the lengths given out again.
		int[] count = new int[longest + 1];
		for(int s = 0; s < size; s++) if(length[s] > 0) count[length[s]]++;
		for(int i = longest; i > limit; i--)
			while(count[i] > 0)
			{
				int j = i - 2;
				while(count[j] == 0) j--;
				count[i] -= 2;
				count[i - 1]++;
				count[j + 1] += 2;
				count[j]--;
			}
		long[] order = new long[used];
		for(int s = 0, j = 0; s < size; s++) if(length[s] > 0) order[j++] = ((long) length[s] << 32) | s;
		Arrays.sort(order);
		for(int j = 0, l = 1; j < used; j++)
		{
			while(count[l] == 0) l++;
			length[(int) (order[j] & 0xffffffffL)] = l;
			count[l]--;
		}
		return length;
	}

	/**
	 * Writes the tables' code lengths (all TABLES of them, one after another,
	 * each of its tableSize): coded with the code lengths' own symbols
	 * (LENGTH_SYMBOLS: a length, the last length again 3 to 6 times, or a run
	 * of zeros), themselves Huffman coded; first that code's lengths, 4 bits
	 * each, then the symbols, each followed by its extra bits (2, 3 or 7).
	 * A run of a length is written as the length, then repeats of 6 (the last
	 * fewer), with any one or two left over as lengths; zeros as runs of 138
	 * or fewer, then 10 or fewer, then any one or two left over as zeros.
	 *
	 * @param bits   the bit stream
	 * @param length the tables' code lengths
	 */
	static void writeLengths(BitWriter bits, int[][] length)
	{
		int total = 0;
		for(int[] l : length) total += l.length;
		int[] all = new int[total];
		for(int t = 0, at = 0; t < length.length; at += length[t].length, t++) System.arraycopy(length[t], 0, all, at, length[t].length);

		// The symbols (and their extra bits), then their code.
		int[] symbol = new int[total], extra = new int[total];
		int m = 0;
		for(int i = 0; i < total; )
		{
			int v = all[i], run = 1;
			while(i + run < total && all[i + run] == v) run++;
			i += run;
			if(v == 0)
			{
				while(run >= 11) { int r = Math.min(run, 138); symbol[m] = LENGTH_MANY_ZEROS; extra[m++] = r - 11; run -= r; }
				if(run >= 3)     { symbol[m] = LENGTH_ZEROS; extra[m++] = run - 3; run = 0; }
				while(run > 0)   { symbol[m++] = 0; run--; }
			}
			else
			{
				symbol[m++] = v;
				run--;
				while(run >= 3) { int r = Math.min(run, 6); symbol[m] = LENGTH_REPEAT; extra[m++] = r - 3; run -= r; }
				while(run > 0)  { symbol[m++] = v; run--; }
			}
		}
		int[] frequency = new int[LENGTH_SYMBOLS];
		for(int j = 0; j < m; j++) frequency[symbol[j]]++;
		int[] code_length = codeLengths(frequency, MAX_LENGTH_CODE_LENGTH);
		for(int s = 0; s < LENGTH_SYMBOLS; s++) bits.write(code_length[s], 4);
		Table table = new Table(code_length);
		for(int j = 0; j < m; j++)
		{
			table.write(bits, symbol[j]);
			if(symbol[j] == LENGTH_REPEAT)     bits.write(extra[j], 2);
			else if(symbol[j] == LENGTH_ZEROS) bits.write(extra[j], 3);
			else if(symbol[j] == LENGTH_MANY_ZEROS) bits.write(extra[j], 7);
		}
	}

	/**
	 * Reads the tables' code lengths writeLengths wrote.
	 *
	 * @param bits   the bit stream
	 * @param values the number of delta values
	 * @return the tables' code lengths
	 * @throws IOException if they aren't what writeLengths writes
	 */
	static int[][] readLengths(BitReader bits, int values) throws IOException
	{
		int[] code_length = new int[LENGTH_SYMBOLS];
		for(int s = 0; s < LENGTH_SYMBOLS; s++) code_length[s] = bits.read(4);
		Table table = new Table(code_length);
		int[][] length = new int[TABLES][];
		int total = 0;
		for(int t = 0; t < TABLES; t++) { length[t] = new int[tableSize(t, values)]; total += length[t].length; }
		int[] all = new int[total];
		for(int i = 0, last = -1; i < total; )
		{
			int s = table.read(bits), run;
			if(s <= MAX_CODE_LENGTH)      { all[i++] = s; last = s; continue; }
			if(s == LENGTH_REPEAT)        { if(last < 0) throw new IOException("bad code lengths"); run = 3 + bits.read(2); s = last; }
			else if(s == LENGTH_ZEROS)    { run = 3 + bits.read(3); s = 0; last = 0; }
			else                          { run = 11 + bits.read(7); s = 0; last = 0; }
			if(i + run > total || bits.overrun()) throw new IOException("bad code lengths");
			for(int j = 0; j < run; j++) all[i++] = s;
		}
		for(int t = 0, at = 0; t < TABLES; at += length[t].length, t++) System.arraycopy(all, at, length[t], 0, length[t].length);
		return length;
	}

	// ---- Tables and bits ------------------------------------------------------

	/**
	 * A canonical Huffman code (codes given out in order of length, then of
	 * symbol), for coding and for decoding.
	 */
	static final class Table
	{
		/** Each symbol's code length (0: not used). */
		final int[] length;
		/** Each symbol's code. */
		final int[] code;
		/** The decoder's lookup: the next LOOKUP_BITS bits to (symbol &lt;&lt; 5) | length; 0 for a longer code. */
		int[] lookup;
		/** For longer codes: the first code of each length, the number of codes of each length, and where they start in sorted. */
		int[] first, count, start;
		/** The symbols in code order. */
		int[] sorted;
		/** The longest code. */
		int longest;

		/**
		 * The code for these lengths.
		 *
		 * @param length each symbol's code length
		 */
		Table(int[] length)
		{
			this.length = length;
			int size = length.length;
			code = new int[size];
			int max = 0;
			for(int l : length) max = Math.max(max, l);
			longest = max;
			count = new int[max + 2];
			for(int l : length) if(l > 0) count[l]++;
			first = new int[max + 2];
			start = new int[max + 2];
			int next = 0, at = 0;
			for(int l = 1; l <= max; l++)
			{
				next = (next + count[l - 1]) << 1;
				first[l] = next;
				start[l] = at;
				at += count[l];
			}
			sorted = new int[at];
			int[] fill = Arrays.copyOf(first, max + 2), place = Arrays.copyOf(start, max + 2);
			for(int s = 0; s < size; s++)
			{
				int l = length[s];
				if(l == 0) continue;
				code[s] = fill[l]++;
				sorted[place[l]++] = s;
			}
		}

		/**
		 * Writes a symbol's code.
		 *
		 * @param bits the bit stream
		 * @param s    the symbol
		 */
		void write(BitWriter bits, int s)
		{
			bits.write(code[s], length[s]);
		}

		/**
		 * Reads a symbol.
		 *
		 * @param bits the bit stream
		 * @return the symbol
		 * @throws IOException if the bits are no code of this table's
		 */
		int read(BitReader bits) throws IOException
		{
			if(lookup == null) makeLookup();
			int e = lookup[bits.peek(LOOKUP_BITS)];
			if(e != 0) { bits.skip(e & 31); return e >>> 5; }
			// A longer code: from the canonical code, a length at a time.
			int all = bits.peek(MAX_CODE_LENGTH);
			for(int l = LOOKUP_BITS + 1; l <= longest; l++)
			{
				int c = all >>> (MAX_CODE_LENGTH - l), i = c - first[l];
				if(i >= 0 && i < count[l]) { bits.skip(l); return sorted[start[l] + i]; }
			}
			throw new IOException("bad neighbour Huffman coding: no such code");
		}

		/**
		 * Makes the decoder's lookup table.
		 */
		private void makeLookup()
		{
			lookup = new int[1 << LOOKUP_BITS];
			for(int s = 0; s < length.length; s++)
			{
				int l = length[s];
				if(l == 0 || l > LOOKUP_BITS) continue;
				int from = code[s] << (LOOKUP_BITS - l), to = from + (1 << (LOOKUP_BITS - l));
				for(int i = from; i < to; i++) lookup[i] = (s << 5) | l;
			}
		}
	}

	/**
	 * Writes bits, most significant first, into a growing byte array.
	 */
	static final class BitWriter
	{
		/** The bytes so far. */
		private byte[] buffer;
		/** How many of them. */
		private int    size;
		/** The bits not yet written out (the low bit_count of them). */
		private long   bits;
		/** How many. */
		private int    bit_count;

		/**
		 * An empty one.
		 *
		 * @param capacity the bytes to start with
		 */
		BitWriter(int capacity) { buffer = new byte[Math.max(16, capacity)]; }

		/**
		 * Writes the low count bits of value.
		 *
		 * @param value the bits
		 * @param count how many (0 to 24)
		 */
		void write(int value, int count)
		{
			bits = (bits << count) | (value & ((1L << count) - 1));
			bit_count += count;
			while(bit_count >= 8)
			{
				bit_count -= 8;
				if(size == buffer.length) buffer = Arrays.copyOf(buffer, 2 * size);
				buffer[size++] = (byte) (bits >>> bit_count);
			}
		}

		/**
		 * The bytes, the last filled out with 0 bits.
		 *
		 * @return the bytes
		 */
		byte[] toByteArray()
		{
			if(bit_count > 0) write(0, 8 - bit_count);
			return Arrays.copyOf(buffer, size);
		}
	}

	/**
	 * Reads bits, most significant first; past the end, 0 bits.
	 */
	static final class BitReader
	{
		/** The bytes. */
		private final byte[] buffer;
		/** The next byte. */
		private int  at;
		/** Bits read in and not used (the low bit_count of them). */
		private long bits;
		/** How many. */
		private int  bit_count;
		/** How many 0 bytes were made up past the end. */
		private int  past_end;

		/**
		 * One reading these bytes.
		 *
		 * @param buffer the bytes
		 */
		BitReader(byte[] buffer) { this.buffer = buffer; }

		/**
		 * The next count bits, not used up.
		 *
		 * @param count how many (at most 24)
		 * @return them
		 */
		int peek(int count)
		{
			while(bit_count < count)
			{
				int b;
				if(at < buffer.length) b = buffer[at++] & 0xff;
				else { b = 0; past_end++; }
				bits = (bits << 8) | b;
				bit_count += 8;
			}
			return (int) (bits >>> (bit_count - count)) & ((1 << count) - 1);
		}

		/**
		 * Uses up count bits (peeked already).
		 *
		 * @param count how many
		 */
		void skip(int count) { bit_count -= count; }

		/**
		 * The next count bits.
		 *
		 * @param count how many (at most 24)
		 * @return them
		 */
		int read(int count)
		{
			if(count == 0) return 0;
			int v = peek(count);
			bit_count -= count;
			return v;
		}

		/**
		 * Whether bits past the end were used (the stream was too short).
		 *
		 * @return true if they were
		 */
		boolean overrun() { return 8 * past_end > bit_count; }
	}
}
