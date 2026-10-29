import java.awt.*;
import java.awt.event.*;
import java.awt.image.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import javax.swing.*;

// SimpleWriter2 version 1.0 (writes Simple 2 format 1)
//
// SimpleWriter without the analysis, for speed: one channel set, green,
// blue-green and red-green (SET_ID; the set SimpleWriter's analysis picks
// most often, and within about 1% of its choice on the test images), every
// interior pixel predicted by the least-squares fit
// (DeltaMapper.LeastSquares, the wide clamp) with one window radius,
// RADIUS, for every channel and image, and the deltas mixed-context coded
// with the second opinions (DeltaMapper.packMixedDeltas). As in
// SimpleWriter, a channel's fit uses the channel before it and its contexts
// the deltas of the channels before it. Nothing is fitted or coded until
// Save. Pixel Resolution resizes with ResizeMapper.resize2 (centred). The
// Bands menu splits the image into up to MAX_BANDS bands of rows that a
// reader can decode at the same time, for a small cost per band boundary.
// Files are read by SimpleReader2.
/**
 * SimpleWriter without the analysis: one channel set and one
 * least-squares window radius (see the comment above).
 */
public class SimpleWriter2
{
	/**
	 * Every file starts with FORMAT_ID ('F' = Simple 2 format) and
	 * FORMAT_VERSION.
	 */
	static final char FORMAT_ID      = 'F';
	/**
	 * The file format's version: bump it in the writer and reader together
	 * whenever the file layout or a coder's output changes.
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
		if(args.length == 1) { new SimpleWriter2(args[0]); return; }
		FileDialog fd = new FileDialog((java.awt.Frame) null, "Open Image", FileDialog.LOAD);
		fd.setVisible(true);
		if(fd.getFile() != null) new SimpleWriter2(new File(fd.getDirectory(), fd.getFile()).getPath());
		else System.exit(0);
	}

	/**
	 * Loads an image and opens its window.
	 *
	 * @param _filename the image
	 */
	public SimpleWriter2(String _filename)
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
		open_item.addActionListener(e -> { FileDialog fd = new FileDialog(frame, "Open Image", FileDialog.LOAD); fd.setVisible(true); if(fd.getFile() != null) new SimpleWriter2(fd.getDirectory() + fd.getFile()); });
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

		// Like SimpleReader2: resize (centred: resize2), then shift. The reader
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
	 * File &gt; Save: codes the image (encode) into the file "foo".
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
			File file = new File("foo");
			long start = System.nanoTime();
			try(DataOutputStream out = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(file))))
			{
				encode(out, value_list, channel_min, delta_xdim, image_xdim, image_ydim, pixel_shift, pixel_quant, bandsUsed());
			}
			catch(Exception e)
			{
				file.delete();
				ViewerSupport.showError(view.frame, "Save failed: " + e);
				e.printStackTrace();
				return;
			}
			long took = System.nanoTime() - start;
			int n = bandsUsed(), raw = image_xdim * image_ydim * 3, rows = value_list[0].length / delta_xdim;
			if(n > 1) System.out.println(String.format("Saved %s: %,d bytes (%s)", file.getName(), file.length(), SimpleWriter.describeBands(n, rows)));
			else      System.out.println(String.format("Saved %s: %,d bytes", file.getName(), file.length()));
			System.out.println(String.format("Coded in %s (%d x %d, %,d pixels)", ViewerSupport.formatDuration(took), delta_xdim, rows, (long) delta_xdim * rows));
			System.out.println("Original compression rate: " + String.format("%.4f", (double) file_length / raw));
			System.out.println("Output  compression rate:  " + String.format("%.4f", (double) file.length() / raw));
			System.out.println();
		}
	}

	/**
	 * Codes the channels and writes the file. Format 1. Header: FORMAT_ID,
	 * FORMAT_VERSION, width, height (unsigned shorts), pixel_shift,
	 * pixel_quant; per channel (coding order) int min; the number of bands n
	 * (an unsigned byte), n int lengths, and the n bands. Band b is rows SimpleWriter.bandStart(b) to
	 * bandStart(b + 1) of the quantized image, coded as an image of its own
	 * (the fit and the contexts start again at its top): per channel (green,
	 * blue-green, red-green), int init, then the least-squares deltas (radius RADIUS)
	 * mixed-context coded with the second opinions
	 * (DeltaMapper.packMixedDeltas). Channels 1 and 2 use the ones before
	 * them (their values in the fit, their deltas as context), so a band's
	 * channels are decoded in order, each a couple of rows behind the one
	 * before; the bands can be decoded at the same time.
	 * <p>
	 * Each channel is fitted once over the whole image (the fit splits itself
	 * across the cores); the bands' deltas come from it, with only each
	 * band's first rows fitted again (DeltaMapper.getLeastSquaresBandDeltas).
	 * A channel's coding needs only the deltas of the channels before it, so
	 * its bands are coded (and checked by decoding them back) while the next
	 * channel is fitted.
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
	 * @throws IOException if the stream fails
	 */
	static void encode(DataOutputStream out, int[][] value, int[] min, int xdim, int image_xdim, int image_ydim, int pixel_shift, int pixel_quant, int n) throws IOException
	{
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
				int[] whole = DeltaMapper.getLeastSquaresDeltas(value[i], ref, xdim, rows, RADIUS);
				for(int b = 0; b < n; b++)
				{
					int y0 = SimpleWriter.bandStart(b, n, rows), y1 = SimpleWriter.bandStart(b + 1, n, rows);
					d[b][i] = (n == 1) ? whole : DeltaMapper.getLeastSquaresBandDeltas(whole, value[i], ref, xdim, y0, y1, RADIUS);
				}
				for(int b = 0; b < n; b++)
				{
					int fb = b;
					jobs.add(coder.submit(() ->
					{
						int y0 = SimpleWriter.bandStart(fb, n, rows), y1 = SimpleWriter.bandStart(fb + 1, n, rows);
						int[]   ch = (n == 1) ? value[fi] : Arrays.copyOfRange(value[fi], y0 * xdim, y1 * xdim);
						int[]   dd = d[fb][fi];
						int[][] previous = Arrays.copyOf(d[fb], fi);
						coded[fb][fi] = DeltaMapper.packMixedDeltas(dd, ch, previous, xdim, true);
						int[] p = new int[ch.length];
						for(int k = 0; k < p.length; k++) p[k] = ch[k] - dd[k];
						if(!Arrays.equals(DeltaMapper.unpackMixedDeltas(coded[fb][fi], ch, previous, xdim, p), dd))
							System.out.println("WARNING: band " + fb + " channel " + fi + " deltas do not decode back.");
						return null;
					}));
				}
			}
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
	}
}
