import java.awt.*;
import java.awt.event.*;
import java.awt.image.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import javax.swing.*;

// HuffmanWriter version 1.0 (writes Huffman format 1)
//
// HuffmanWriter is SimpleWriter2 (Simple 2 format 2) up to the entropy
// coding: the same channels (green, blue-green and red-green, SET_ID), the
// same least-squares fit from the rows above (DeltaMapper.RowFit: the window
// is the RADIUS rows above, solved at every EVERY-th pixel along a row), the
// same bands, Apply and Save. Then, in place of the mixed context coding,
// each band's channel's deltas are coded as DeltaWriter2 codes them: unary
// strings by frequency rank (StringMapper.getStringList), with StringMapper's
// bit-pair transform kept only if it saves enough (its rule), then a
// canonical Huffman code over the string's bytes, one per channel and band
// (CodeMapper's regular Huffman code, DeltaWriter's entropy type 1). No
// contexts, so a channel's coding uses no other channel. Less compression
// than Simple 2 (on the test photos the files are 9-29% bigger), faster to
// decode. Files are read by HuffmanReader (and NativeEncoder's).
/**
 * SimpleWriter2 with Huffman coding in place of the mixed context coding
 * (see the comment above).
 */
public class HuffmanWriter
{
	/**
	 * Every file starts with FORMAT_ID ('H' = Huffman format) and
	 * FORMAT_VERSION.
	 */
	static final char FORMAT_ID      = 'H';
	/**
	 * The file format's version: bump it in the writer and reader together
	 * whenever the file layout or a coder's output changes. 1: the first.
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
	 * The most bands Save offers.
	 */
	static final int  MAX_BANDS = 16;
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
		if(args.length == 1) { new HuffmanWriter(args[0]); return; }
		FileDialog fd = new FileDialog((java.awt.Frame) null, "Open Image", FileDialog.LOAD);
		fd.setVisible(true);
		if(fd.getFile() != null) new HuffmanWriter(new File(fd.getDirectory(), fd.getFile()).getPath());
		else System.exit(0);
	}

	/**
	 * Loads an image and opens its window.
	 *
	 * @param _filename the image
	 */
	public HuffmanWriter(String _filename)
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
		System.out.println(String.format("Loaded %s, %d x %d (%,d pixels)", filename, image_xdim, image_ydim, (long) image_xdim * image_ydim));

		int[] pixel = original_image.getRGB(0, 0, image_xdim, image_ydim, null, 0, image_xdim);
		source = new int[3][pixel.length];
		for(int i = 0; i < pixel.length; i++)
		{
			source[0][i] = (pixel[i] >> 16) & 0xff; source[1][i] = (pixel[i] >> 8) & 0xff; source[2][i] = pixel[i] & 0xff;
		}
		working_image = new BufferedImage(image_xdim, image_ydim, BufferedImage.TYPE_INT_RGB);
		working_image.setRGB(0, 0, image_xdim, image_ydim, pixel, 0, image_xdim);

		view = new ViewerSupport("Simple Writer 2  " + filename, image_xdim, image_ydim);
		JFrame   frame    = view.frame;
		JMenuBar menu_bar = frame.getJMenuBar();

		JMenu file_menu = new JMenu("File");
		JMenuItem open_item = new JMenuItem("Open...");
		open_item.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_O, InputEvent.CTRL_DOWN_MASK));
		open_item.addActionListener(e -> { FileDialog fd = new FileDialog(frame, "Open Image", FileDialog.LOAD); fd.setVisible(true); if(fd.getFile() != null) new HuffmanWriter(fd.getDirectory() + fd.getFile()); });
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

		// Bands menu: Save codes the image as this many bands of rows, each
		// decodable on its own (so a reader can decode them at the same time),
		// for a small cost per band boundary. Like Save itself, it never
		// changes the preview.
		JMenu bands_menu = new JMenu("Bands");
		JSlider[] bs = new JSlider[1];
		bands_menu.add(ViewerSupport.makeSliderDialog(frame, "Number of Bands", 1, MAX_BANDS, bands, v -> { bands = v; view.setStatus(bandText()); }, bs));

		menu_bar.add(file_menu); menu_bar.add(view.makeViewMenu()); menu_bar.add(quant_menu); menu_bar.add(bands_menu);

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

		// Like HuffmanReader: resize (centred: resize2), then shift. The reader
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
		working_image.setRGB(0, 0, image_xdim, image_ydim, rgb, 0, image_xdim);
		view.setImage(working_image);
	}

	// ---- Save ---------------------------------------------------------------

	/**
	 * File &gt; Save: codes the image (encode) into the file "foo", twice, and
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
			// The image is coded twice and the times of both printed: the first
			// includes compiling the code (the first time it runs in this JVM),
			// the second is the run time only. Both leave out the decode check,
			// which is timed on its own. Both give the same bytes.
			File file = new File("foo");
			long[] took = new long[2], check = new long[2];
			try
			{
				byte[] data = null;
				for(int pass = 0; pass < 2; pass++)
				{
					ByteArrayOutputStream bytes = new ByteArrayOutputStream();
					long start = System.nanoTime();
					check[pass] = encode(new DataOutputStream(bytes), value_list, channel_min, delta_xdim, image_xdim, image_ydim, pixel_shift, pixel_quant, bandsUsed());
					took[pass] = System.nanoTime() - start - check[pass];
					byte[] got = bytes.toByteArray();
					if(data != null && !Arrays.equals(data, got)) System.out.println("WARNING: the second coding differs from the first.");
					data = got;
				}
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
			System.out.println(String.format("Coded %d x %d (%,d pixels)", delta_xdim, rows, (long) delta_xdim * rows));
			System.out.println("Run time plus compile time: " + ViewerSupport.formatDuration(took[0]));
			System.out.println("Run time only:              " + ViewerSupport.formatDuration(took[1]));
			System.out.println("Decode check:               " + ViewerSupport.formatDuration(check[1]));
			System.out.println("Original compression rate: " + String.format("%.4f", (double) file_length / raw));
			System.out.println("Output  compression rate:  " + String.format("%.4f", (double) file.length() / raw));
			System.out.println();
		}
	}

	/**
	 * Codes the channels and writes the file. Format 1. Header: FORMAT_ID,
	 * FORMAT_VERSION, width, height (unsigned shorts), pixel_shift,
	 * pixel_quant; per channel (coding order) int min; the number of bands n
	 * (an unsigned byte), n int lengths, and the n bands. Band b is rows
	 * SimpleWriter.bandStart(b) to bandStart(b + 1) of the quantized image,
	 * coded as an image of its own (the fit starts again at its top): per
	 * channel (green, blue-green, red-green), int init, then the deltas of
	 * the least-squares fit from the rows above (DeltaMapper.RowFit: radius
	 * RADIUS, solved at every EVERY-th pixel along a row) Huffman coded
	 * (huffmanCode, see there for the layout). Channels 1 and 2 use the ones
	 * before them in the fit (their values), so a band's channels are
	 * rebuilt in order, each a row behind the one before; the bands can be
	 * decoded at the same time.
	 * <p>
	 * Each channel is fitted once over the whole image (the fit splits itself
	 * across the cores); the bands' deltas come from it, with only each
	 * band's first rows fitted again (DeltaMapper.getRowFitBandDeltas).
	 * A channel's coding needs only its own deltas, so its bands are coded
	 * while the next channel is fitted. Once everything
	 * is coded, each band's channels are checked by decoding them back, at
	 * the same time; the time that took is returned, so the coding can be
	 * timed without it.
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
	 * @return the time the decode check took, in nanoseconds
	 * @throws IOException if the stream fails
	 */
	static long encode(DataOutputStream out, int[][] value, int[] min, int xdim, int image_xdim, int image_ydim, int pixel_shift, int pixel_quant, int n) throws IOException
	{
		long check_time = 0;
		int rows = value[0].length / xdim;
		int[][][]  d = new int[n][3][];      // [band][channel]
		byte[][][] coded = new byte[n][3][];
		ExecutorService coder = Executors.newFixedThreadPool(Math.max(1, Math.min(3 * n, Runtime.getRuntime().availableProcessors())));
		java.util.List<Future<?>> jobs = new ArrayList<>();
		try
		{
			for(int i = 0; i < 3; i++)
			{
				int   fi = i;
				int[] ref = (i > 0) ? value[i - 1] : null;
				int[] whole = DeltaMapper.getRowFitDeltas(value[i], ref, xdim, rows, RADIUS, EVERY);
				for(int b = 0; b < n; b++)
				{
					int y0 = SimpleWriter.bandStart(b, n, rows), y1 = SimpleWriter.bandStart(b + 1, n, rows);
					d[b][i] = (n == 1) ? whole : DeltaMapper.getRowFitBandDeltas(whole, value[i], ref, xdim, y0, y1, RADIUS, EVERY);
				}
				for(int b = 0; b < n; b++)
				{
					int fb = b;
					jobs.add(coder.submit(() ->
					{
						coded[fb][fi] = huffmanCode(d[fb][fi]);
						return null;
					}));
				}
			}
			waitFor(jobs);
			// The check, once everything is coded (so its time can be told
			// apart): each band's channels decoded back, at the same time.
			long check = System.nanoTime();
			jobs.clear();
			for(int b = 0; b < n; b++)
				for(int i = 0; i < 3; i++)
				{
					int fb = b, fi = i;
					jobs.add(coder.submit(() ->
					{
						int[] dd = d[fb][fi], back = huffmanDecode(new DataInputStream(new ByteArrayInputStream(coded[fb][fi])), dd.length);
						// (Delta 0 isn't coded: the band's first value, init, is written instead.)
						boolean same = back.length == dd.length;
						for(int k = 1; k < dd.length && same; k++) same = back[k] == dd[k];
						if(!same)
							System.out.println("WARNING: band " + fb + " channel " + fi + " deltas do not decode back.");
						return null;
					}));
				}
			waitFor(jobs);
			check_time = System.nanoTime() - check;
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
		for(int i = 0; i < 3; i++) out.writeInt(min[i]);
		out.writeByte(n);
		for(int b = 0; b < n; b++) out.writeInt(band[b].length);
		for(int b = 0; b < n; b++) out.write(band[b]);
		return check_time;
	}

	/**
	 * A channel's deltas Huffman coded, as DeltaWriter2 codes them: unary
	 * strings by frequency rank (StringMapper.getStringList, the bit-pair
	 * transform kept only if it saves enough), then a canonical Huffman code
	 * over the string's bytes (CodeMapper's regular Huffman code). Layout:
	 * int the deltas' minimum, int the unary string's bit length, its rank
	 * table (DeltaMapper.writeTable), int the (transformed) string's byte
	 * length, int and the Huffman code lengths (CodeMapper.packRegularTables),
	 * int and the code. Delta 0 isn't used (the band's first value is
	 * written instead).
	 *
	 * @param delta the deltas (not changed)
	 * @return the coded bytes
	 * @throws IOException never in practice (a byte stream)
	 */
	static byte[] huffmanCode(int[] delta) throws IOException
	{
		ArrayList<?> list   = StringMapper.getStringList(delta.clone(), true);
		int          min    = (int) list.get(0);
		int          bits   = (int) list.get(1);
		int[]        table  = (int[]) list.get(2);
		byte[]       string = (byte[]) list.get(3);
		int[]  frequency = ArithmeticMapper.getFrequency(string);
		byte[] lengths = CodeMapper.getRegularHuffmanLength(frequency);
		byte[] code    = CodeMapper.packRegularCode(string, lengths);
		byte[] tables  = CodeMapper.packRegularTables(new byte[][] {lengths}, java.util.zip.Deflater.BEST_COMPRESSION);
		ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		DataOutputStream out = new DataOutputStream(bytes);
		out.writeInt(min);
		out.writeInt(bits);
		DeltaMapper.writeTable(out, table);
		out.writeInt(string.length);
		out.writeInt(tables.length); out.write(tables);
		out.writeInt(code.length);   out.write(code);
		return bytes.toByteArray();
	}

	/**
	 * A channel's Huffman coding as huffmanCode wrote it (read by
	 * huffmanRead, its code lengths inflated).
	 */
	static final class HuffmanChannel
	{
		/** The deltas' minimum. */
		int    min;
		/** The untransformed string's bits. */
		int    bits;
		/** The (transformed) string's bytes. */
		int    length;
		/** The rank table. */
		int[]  table;
		/** Each byte value's code length (256 of them). */
		byte[] lengths;
		/** The Huffman code. */
		byte[] code;

		/** An empty one, for huffmanRead to fill in. */
		HuffmanChannel() { }
	}

	/**
	 * Reads a channel's coding huffmanCode wrote.
	 *
	 * @param in the stream, at the start of it
	 * @return the coding
	 * @throws IOException if the stream fails or isn't what huffmanCode writes
	 */
	static HuffmanChannel huffmanRead(DataInputStream in) throws IOException
	{
		HuffmanChannel h = new HuffmanChannel();
		h.min    = in.readInt();
		h.bits   = in.readInt();
		h.table  = DeltaMapper.readTable(in);
		h.length = in.readInt();
		byte[] tables = new byte[in.readInt()]; in.readFully(tables);
		h.code   = new byte[in.readInt()]; in.readFully(h.code);
		try { h.lengths = CodeMapper.unpackRegularTables(tables, 1)[0]; }
		catch(java.util.zip.DataFormatException e) { throw new IOException("bad Huffman tables", e); }
		return h;
	}

	/**
	 * Reads a channel's deltas huffmanCode wrote (huffmanRead, then
	 * huffmanDecode).
	 *
	 * @param in the stream, at the start of them
	 * @param n  how many deltas
	 * @return the deltas (delta 0 is 0)
	 * @throws IOException if the stream fails or isn't what huffmanCode writes
	 */
	static int[] huffmanDecode(DataInputStream in, int n) throws IOException
	{
		return huffmanDecode(huffmanRead(in), n);
	}

	/**
	 * A channel's deltas from its Huffman coding.
	 *
	 * @param h the coding
	 * @param n how many deltas
	 * @return the deltas (delta 0 is 0)
	 */
	static int[] huffmanDecode(HuffmanChannel h, int n)
	{
		byte[] string = CodeMapper.unpackRegularCode(h.code, h.lengths, h.length);
		int[]  delta  = StringMapper.unpackStrings(StringMapper.decompressStrings(string), h.table, n, h.bits);
		delta[0] = 0;
		for(int k = 1; k < delta.length; k++) delta[k] += h.min;
		return delta;
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
