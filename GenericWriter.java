import java.awt.*;
import java.awt.event.*;
import java.awt.image.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import javax.swing.*;

// GenericWriter version 1.0 (writes Simple 2 formats 2 and 3, and Huffman
// formats 1 and 2)
//
// NativeEncoder's NativeWriter in Java alone: the same program and the same
// files, with nothing in C. SimpleWriter2 and HuffmanWriter in one, with a
// choice of deltas: the same image, the same Apply and the same deltas, with
// the coding chosen by the Entropy menu.
//
// One channel set, green, blue-green and red-green (SET_ID; the set
// SimpleWriter's analysis picks most often, and within about 1% of its
// choice on the test images). Nothing is coded until Save. Pixel Resolution
// resizes with ResizeMapper.resize2 (centred). The Bands menu splits the
// image into up to MAX_BANDS bands of rows that a reader can decode at the
// same time, for a small cost per band boundary.
//
// The Delta menu chooses the deltas:
//   LS:  every interior pixel predicted by the least-squares fit from the rows
//        above (DeltaMapper.RowFit, the wide clamp: the window is the RADIUS
//        rows above, with none of the pixel's own row, so a reader solves a
//        whole row's fits at the same time before decoding it), solved at
//        every EVERY-th pixel along a row; a channel's fit uses the channel
//        before it. The default.
//   Avg: the classic average filter (DeltaMapper.getAverageDeltasFromValues:
//        each pixel from (left + above) / 2), no fit.
//
// The Entropy menu chooses the coding:
//   Context aware: the deltas context-aware coded with the second opinions
//        (DeltaMapper.packMixedDeltas; a channel's contexts use the deltas
//        of the channels before it). Simple 2 format: 2 with LS, the same
//        files as SimpleWriter2; 3 with Avg (the delta type after the Pixel
//        Resolution). The default.
//   Huffman: each channel's deltas as HuffmanWriter codes them
//        (HuffmanWriter.huffmanCode: unary strings by frequency rank, then a
//        canonical Huffman code over the strings' bytes); no contexts, so a
//        channel's coding uses no other channel. Huffman format: 1 with LS,
//        the same files as HuffmanWriter; 2 with Avg. Bigger files (on the
//        test photos 9-29% with LS), faster to decode.
//
// Files are read by GenericReader (formats 2 and 1 by SimpleReader2 and
// HuffmanReader too), and by NativeEncoder's NativeReader: GenericWriter's
// files are NativeWriter's, byte for byte.
/**
 * The generic writer: LS or average deltas, context aware or Huffman coded
 * (see the comment above).
 */
public class GenericWriter
{
	/**
	 * A context aware coded file starts with SIMPLE_ID ('F' = Simple 2
	 * format) and its version: SIMPLE_VERSION for the LS deltas,
	 * SIMPLE_VERSION_DELTA_TYPE for the others. Bump them in the writer and
	 * reader together whenever the file layout or a coder's output changes.
	 */
	static final char SIMPLE_ID      = 'F';
	/**
	 * The Simple 2 format's version for the LS deltas: 2 (SimpleWriter2's; 1
	 * was the fit from 8 neighbours, solved at every 3rd
	 * pixel).
	 */
	static final int  SIMPLE_VERSION = 2;
	/**
	 * The Simple 2 format with the delta type: version 2 with a byte, the
	 * delta type (DELTA_LS or DELTA_AVERAGE), after the Pixel Resolution.
	 * Written for the average deltas.
	 */
	static final int  SIMPLE_VERSION_DELTA_TYPE = 3;
	/**
	 * A Huffman coded file starts with HUFFMAN_ID ('H' = Huffman format) and
	 * its version, as above.
	 */
	static final char HUFFMAN_ID      = 'H';
	/**
	 * The Huffman format's version for the LS deltas: 1 (HuffmanWriter's).
	 */
	static final int  HUFFMAN_VERSION = 1;
	/**
	 * The Huffman format with the delta type: version 1 with a byte, the
	 * delta type, after the Pixel Resolution. Written for the average deltas.
	 */
	static final int  HUFFMAN_VERSION_DELTA_TYPE = 2;
	/**
	 * The entropy types (the Entropy menu): context aware coding
	 * (DeltaMapper.packMixedDeltas), the Simple 2 format.
	 */
	static final int  ENTROPY_MIXED = 0;
	/**
	 * The entropy types (the Entropy menu): Huffman coding (huffmanCode), the
	 * Huffman format.
	 */
	static final int  ENTROPY_HUFFMAN = 1;
	/**
	 * The Entropy menu's names for them.
	 */
	static final String[] ENTROPY_NAMES = {"Context aware", "Huffman"};
	/**
	 * The delta types (the Delta menu): the least-squares fit from the rows
	 * above (DeltaMapper.RowFit).
	 */
	static final int  DELTA_LS = 0;
	/**
	 * The delta types (the Delta menu): the average of the pixels left and
	 * above (DeltaMapper.getAverageDeltasFromValues).
	 */
	static final int  DELTA_AVERAGE = 1;
	/**
	 * The Delta menu's names for them.
	 */
	static final String[] DELTA_NAMES = {"LS", "Avg"};
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
	 * The most bands Save offers: enough for a reader to use about 96 cores
	 * (three channels' threads a band) on a big image. (The file holds the
	 * number in a byte: at most 255.)
	 */
	static final int  MAX_BANDS = 64;
	/**
	 * The fewest rows (of the quantized image) a band may have: smaller images
	 * get fewer bands.
	 */
	static final int  BAND_MIN_ROWS = 64;

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
	 * The coding Save uses: ENTROPY_MIXED or ENTROPY_HUFFMAN (the Entropy
	 * menu).
	 */
	int entropy_type = ENTROPY_MIXED;
	/**
	 * Whether Save checks its coding by decoding it back (the Decode Check
	 * item in the File menu): off unless switched on. The check decodes
	 * every band again, which takes about as long as decoding the file; off,
	 * Save is that much faster, and a file that doesn't decode right is
	 * found by its reader instead (then, with the check back on, Save says
	 * whether the writer or the reader is at fault).
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
		if(args.length >= 1) { new GenericWriter(args[0], (args.length > 1) ? args[1] : ViewerSupport.DEFAULT_OUTPUT); return; }
		FileDialog fd = new FileDialog((java.awt.Frame) null, "Open Image", FileDialog.LOAD);
		fd.setVisible(true);
		if(fd.getFile() != null) new GenericWriter(new File(fd.getDirectory(), fd.getFile()).getPath());
		else System.exit(0);
	}

	/**
	 * Loads an image and opens its window.
	 *
	 * @param _filename the image
	 */
	public GenericWriter(String _filename)
	{
		this(_filename, ViewerSupport.DEFAULT_OUTPUT);
	}

	/**
	 * Loads an image and opens its window; Save writes output_file.
	 *
	 * @param _filename    the image
	 * @param _output_file the file Save writes
	 */
	public GenericWriter(String _filename, String _output_file)
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

		view = new ViewerSupport("Generic Writer  " + filename, image_xdim, image_ydim);
		JFrame   frame    = view.frame;
		JMenuBar menu_bar = frame.getJMenuBar();

		JMenu file_menu = new JMenu("File");
		JMenuItem open_item = new JMenuItem("Open...");
		open_item.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_O, InputEvent.CTRL_DOWN_MASK));
		open_item.addActionListener(e -> { FileDialog fd = new FileDialog(frame, "Open Image", FileDialog.LOAD); fd.setVisible(true); if(fd.getFile() != null) new GenericWriter(fd.getDirectory() + fd.getFile()); });
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

		// Bands menu: Save codes the image as this many bands of rows, each
		// decodable on its own (so a reader can decode them at the same time),
		// for a small cost per band boundary. Like Save itself, it never
		// changes the preview.
		JMenu bands_menu = new JMenu("Bands");
		JSlider[] bs = new JSlider[1];
		bands_menu.add(ViewerSupport.makeSliderDialog(frame, "Number of Bands", 1, MAX_BANDS, bands, v -> { bands = v; view.setStatus(bandText()); }, bs));

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

		// Entropy menu: context aware or Huffman coding. It too only takes
		// effect at Save.
		JMenu entropy_menu = new JMenu("Entropy");
		ButtonGroup entropy_group = new ButtonGroup();
		for(int t = 0; t < ENTROPY_NAMES.length; t++)
		{
			int type = t;
			JRadioButtonMenuItem item = new JRadioButtonMenuItem(ENTROPY_NAMES[t], t == entropy_type);
			item.addActionListener(e -> entropy_type = type);
			entropy_group.add(item);
			entropy_menu.add(item);
		}

		menu_bar.add(file_menu); menu_bar.add(view.makeViewMenu()); menu_bar.add(quant_menu); menu_bar.add(delta_menu); menu_bar.add(entropy_menu); menu_bar.add(bands_menu);

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
		int[]   min = new int[6];
		int[][] q   = quantizedChannels(size, pixel_quant, pixel_shift);
		int[][] qc  = DeltaMapper.getCandidateChannels(q[0], q[1], q[2], min);
		int[][] v = new int[3][];
		int[]   m = new int[3];
		for(int i = 0; i < 3; i++) { v[i] = qc[CHANNELS[i]]; m[i] = min[CHANNELS[i]]; }
		value_list  = v;
		channel_min = m;
		delta_xdim  = new_xdim;

		// As GenericReader: resize (centred: resize2), then shift. The reader
		// rebuilds blue, green and red exactly as quantized, so the preview
		// uses them as they are: no need to decode.
		int[][] bgr = q.clone();
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
	 * File &gt; Save: codes the image (encode) into output_file ("foo" unless another is given), twice, and
	 * prints both times (the first includes compiling the code), without the
	 * decode check, and the check's time.
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
			// The image is coded twice (ViewerSupport.printRunTimes: the first run
			// of each coding's first Save includes the JIT's warm-up, so its times
			// are printed apart; after that, the two timed together, halved). Both
			// leave out the decode check (if it's on: the Decode Check item), which
			// is timed on its own. Both give the same bytes.
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
					check[pass] = encode(new DataOutputStream(bytes), value_list, channel_min, delta_xdim, image_xdim, image_ydim, pixel_shift, pixel_quant, bandsUsed(), delta_type, entropy_type, decode_check);
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
			System.out.println(String.format("Coded %d x %d (%,d pixels), %s deltas, %s", delta_xdim, rows, (long) delta_xdim * rows, DELTA_NAMES[delta_type], ENTROPY_NAMES[entropy_type]));
			boolean averaged = ViewerSupport.printRunTimes("GenericWriter " + DELTA_NAMES[delta_type] + " " + ENTROPY_NAMES[entropy_type], took[0], took[1], both);
			long    check_time = averaged ? (check[0] + check[1]) / 2 : check[1];
			System.out.println("Decode check:                     " + (decode_check ? ViewerSupport.formatDuration(check_time) : "off"));
			System.out.println("Timeline (second run):            " + last_timeline);
			System.out.println("Original compression rate: " + String.format("%.4f", (double) file_length / raw));
			System.out.println("Output  compression rate:  " + String.format("%.4f", (double) file.length() / raw));
			System.out.println();
		}
	}

	/**
	 * The last encode's timeline (from its start: when each channel's fit
	 * ended, when each channel's coding ended, when all were coded), which
	 * Save prints.
	 */
	static volatile String last_timeline = "";

	/**
	 * Codes the channels and writes the file. Header: the format's id and
	 * version (SIMPLE_ID or HUFFMAN_ID; the version for LS, or the one with
	 * the delta type), width, height (unsigned shorts), pixel_shift,
	 * pixel_quant, the delta type (an unsigned byte, only in the versions with
	 * it); per channel (coding order) int min; the number of bands n (an
	 * unsigned byte), n int lengths, and the n bands. Band b is rows
	 * SimpleWriter.bandStart(b) to bandStart(b + 1) of the quantized image,
	 * coded as an image of its own (the fit and the contexts start again at
	 * its top): per channel (green, blue-green, red-green), int init, then its
	 * deltas coded: context aware (DeltaMapper.packMixedDeltas, with the
	 * second opinions) or Huffman (HuffmanWriter.huffmanCode, see there for
	 * the layout). The deltas: LS, from the least-squares fit from the rows
	 * above (DeltaMapper.RowFit: radius RADIUS, solved at every EVERY-th pixel
	 * along a row; channels 1 and 2 use the ones before them), or the average
	 * of the pixels left and above (DeltaMapper.getAverageDeltasFromValues).
	 * With the context aware coding a channel's contexts use the deltas of the
	 * channels before it, so a band's channels are decoded in order, each a
	 * couple of rows behind the one before; the bands can be decoded at the
	 * same time.
	 * <p>
	 * With LS, each channel is fitted once over the whole image (the fit
	 * splits itself across the cores); the bands' deltas come from it, with
	 * only each band's first rows fitted again
	 * (DeltaMapper.getRowFitBandDeltas). The average deltas are found a band
	 * at a time, all at once. A channel's coding needs only the deltas of the
	 * channels before it (context aware) or its own (Huffman), so its bands
	 * are coded while the next channel is fitted. Once everything is coded,
	 * each band's channels are checked by decoding them back, at the same time
	 * (unless check is false); the time that took is returned, so the coding
	 * can be timed without it.
	 *
	 * @param out          the stream
	 * @param value        the channels as quantized, in coding order (difference channels less their minimum)
	 * @param min          each channel's minimum, in coding order
	 * @param xdim         their width
	 * @param image_xdim   the image's width
	 * @param image_ydim   the image's height
	 * @param pixel_shift  the Color Resolution
	 * @param pixel_quant  the Pixel Resolution
	 * @param n            the number of bands
	 * @param delta_type   DELTA_LS or DELTA_AVERAGE
	 * @param entropy_type ENTROPY_MIXED or ENTROPY_HUFFMAN
	 * @param check        whether to check the coding by decoding it back (the Decode Check menu item)
	 * @return the time the decode check took, in nanoseconds (0 without it)
	 * @throws IOException if the stream fails
	 */
	static long encode(DataOutputStream out, int[][] value, int[] min, int xdim, int image_xdim, int image_ydim, int pixel_shift, int pixel_quant, int n,
	                   int delta_type, int entropy_type, boolean check) throws IOException
	{
		boolean huffman = entropy_type == ENTROPY_HUFFMAN;
		long check_time = 0, start = System.nanoTime();
		long[] fit_done = new long[3], code_done = new long[3];   // when each channel's deltas, and its last band's coding, were done
		int rows = value[0].length / xdim;
		int[][][]  d = new int[n][3][];      // [band][channel]
		byte[][][] coded = new byte[n][3][];
		ExecutorService coder = Executors.newFixedThreadPool(Math.max(1, Math.min(3 * n, Runtime.getRuntime().availableProcessors())));
		ExecutorService fitter = Executors.newFixedThreadPool(3);
		java.util.List<Future<?>> jobs = new ArrayList<>();
		try
		{
			// The average deltas take no fit: they're found here, a band at a
			// time, all at once (each band's channel is a single pass).
			if(delta_type == DELTA_AVERAGE)
			{
				ViewerSupport.parallel(3 * n, job ->
				{
					int b = job / 3, i = job % 3;
					int y0 = SimpleWriter.bandStart(b, n, rows), y1 = SimpleWriter.bandStart(b + 1, n, rows);
					d[b][i] = new int[(y1 - y0) * xdim];
					DeltaMapper.getAverageDeltas(value[i], y0 * xdim, xdim, y1 - y0, d[b][i]);   // (straight from the channel: no copy of the band)
				});
				long now = System.nanoTime();
				for(int i = 0; i < 3; i++) fit_done[i] = now;
			}
			// The three channels' fits at the same time: each needs only its
			// channel and the one before, all known (each still splits into
			// bands of rows on every core). The coding of channel i waits only
			// for the fits of channels 0 to i (context aware: its contexts use
			// their deltas), or its own (Huffman).
			java.util.List<Future<int[]>> fits = new ArrayList<>();
			for(int i = 0; i < 3 && delta_type == DELTA_LS; i++)
			{
				int[] v = value[i], ref = (i > 0) ? value[i - 1] : null;
				int fi = i;
				fits.add(fitter.submit(() ->
				{
					int[] deltas = DeltaMapper.getRowFitDeltas(v, ref, xdim, rows, RADIUS, EVERY);
					fit_done[fi] = System.nanoTime();
					return deltas;
				}));
			}
			for(int i = 0; i < 3; i++)
			{
				int   fi = i;
				int[] ref = (i > 0) ? value[i - 1] : null;
				int[] whole = (delta_type == DELTA_LS) ? result(fits.get(i)) : null;
				// Each band's deltas from the whole channel's: only its first
				// rows fitted again, all the bands at the same time (one after
				// another, 64 bands took about half a second a channel).
				if(delta_type == DELTA_LS && n == 1) d[0][i] = whole;
				else if(delta_type == DELTA_LS)
					ViewerSupport.parallel(n, b ->
					{
						int y0 = SimpleWriter.bandStart(b, n, rows), y1 = SimpleWriter.bandStart(b + 1, n, rows);
						d[b][fi] = DeltaMapper.getRowFitBandDeltas(whole, value[fi], ref, xdim, y0, y1, RADIUS, EVERY);
					});
				for(int b = 0; b < n; b++)
				{
					int fb = b;
					jobs.add(coder.submit(() ->
					{
						if(huffman) coded[fb][fi] = HuffmanWriter.huffmanCode(d[fb][fi]);
						else
						{
							int     y0 = SimpleWriter.bandStart(fb, n, rows), y1 = SimpleWriter.bandStart(fb + 1, n, rows);
							int[]   ch = (n == 1) ? value[fi] : Arrays.copyOfRange(value[fi], y0 * xdim, y1 * xdim);
							int[][] previous = Arrays.copyOf(d[fb], fi);
							coded[fb][fi] = DeltaMapper.packMixedDeltas(d[fb][fi], ch, previous, xdim, true);
						}
						long now = System.nanoTime();
						synchronized(code_done) { code_done[fi] = Math.max(code_done[fi], now); }
						return null;
					}));
				}
			}
			waitFor(jobs);
			// The check, once everything is coded (so its time can be told
			// apart): each band's channels decoded back, at the same time.
			long checked = System.nanoTime(), coded_all = checked;
			jobs.clear();
			for(int b = 0; b < n && check; b++)
				for(int i = 0; i < 3; i++)
				{
					int fb = b, fi = i;
					jobs.add(coder.submit(() ->
					{
						int[] dd = d[fb][fi];
						boolean same;
						if(huffman)
						{
							int[] back = HuffmanWriter.huffmanDecode(new DataInputStream(new ByteArrayInputStream(coded[fb][fi])), dd.length);
							// (Delta 0 isn't coded: the band's first value, init, is written instead.)
							same = back.length == dd.length;
							for(int k = 1; k < dd.length && same; k++) same = back[k] == dd[k];
						}
						else
						{
							int y0 = SimpleWriter.bandStart(fb, n, rows), y1 = SimpleWriter.bandStart(fb + 1, n, rows);
							int[][] previous = Arrays.copyOf(d[fb], fi);
							int[] ch = (n == 1) ? value[fi] : Arrays.copyOfRange(value[fi], y0 * xdim, y1 * xdim);
							int[] p = new int[ch.length];
							for(int k = 0; k < p.length; k++) p[k] = ch[k] - dd[k];
							same = Arrays.equals(DeltaMapper.unpackMixedDeltas(coded[fb][fi], ch, previous, xdim, p), dd);
						}
						if(!same)
							System.out.println("WARNING: band " + fb + " channel " + fi + " deltas do not decode back.");
						return null;
					}));
				}
			waitFor(jobs);
			check_time = check ? System.nanoTime() - checked : 0;
			last_timeline = String.format(((delta_type == DELTA_LS) ? "fits" : "deltas") + " done at %.0f, %.0f, %.0f ms; codings at %.0f, %.0f, %.0f ms; all coded at %.0f ms",
				(fit_done[0] - start) / 1e6, (fit_done[1] - start) / 1e6, (fit_done[2] - start) / 1e6,
				(code_done[0] - start) / 1e6, (code_done[1] - start) / 1e6, (code_done[2] - start) / 1e6, (coded_all - start) / 1e6);
		}
		finally { coder.shutdownNow(); fitter.shutdownNow(); }

		byte[][] band = new byte[n][];
		for(int b = 0; b < n; b++)
		{
			int y0 = SimpleWriter.bandStart(b, n, rows);
			ByteArrayOutputStream bytes = new ByteArrayOutputStream();
			DataOutputStream bo = new DataOutputStream(bytes);
			for(int i = 0; i < 3; i++) { bo.writeInt(value[i][y0 * xdim]); bo.write(coded[b][i]); }
			band[b] = bytes.toByteArray();
		}
		if(huffman) { out.writeByte(HUFFMAN_ID); out.writeByte((delta_type == DELTA_LS) ? HUFFMAN_VERSION : HUFFMAN_VERSION_DELTA_TYPE); }
		else        { out.writeByte(SIMPLE_ID);  out.writeByte((delta_type == DELTA_LS) ? SIMPLE_VERSION  : SIMPLE_VERSION_DELTA_TYPE); }
		out.writeShort(image_xdim); out.writeShort(image_ydim);
		out.writeByte(pixel_shift); out.writeByte(pixel_quant);
		if(delta_type != DELTA_LS) out.writeByte(delta_type);
		for(int i = 0; i < 3; i++) out.writeInt(min[i]);
		out.writeByte(n);
		for(int b = 0; b < n; b++) out.writeInt(band[b].length);
		for(int b = 0; b < n; b++) out.write(band[b]);
		return check_time;
	}

	// ---- Huffman coding -----------------------------------------------------

	/**
	 * A band's channel Huffman decoded (HuffmanWriter.huffmanDecode) and
	 * rebuilt from its average deltas (DeltaMapper.getValuesFromAverageDeltas),
	 * into dst from offset on.
	 *
	 * @param h      the channel's Huffman coding
	 * @param xdim   the width
	 * @param ydim   the band's height
	 * @param init   its first value
	 * @param dst    gets the values
	 * @param offset where they start in dst
	 */
	static void huffmanAverage(HuffmanWriter.HuffmanChannel h, int xdim, int ydim, int init, int[] dst, int offset)
	{
		DeltaMapper.getValuesFromAverageDeltas(HuffmanWriter.huffmanDecode(h, xdim * ydim), xdim, ydim, init, dst, offset);
	}

	/**
	 * A job's result, waiting for it; if it failed, its failure is thrown
	 * as waitFor throws it.
	 *
	 * @param job the job
	 * @param <T> its result's type
	 * @return its result
	 * @throws IOException if it failed with one, or the wait was interrupted
	 */
	private static <T> T result(Future<T> job) throws IOException
	{
		try { return job.get(); }
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
}
