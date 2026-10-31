import java.awt.*;
import java.awt.event.*;
import java.awt.image.*;
import java.io.*;
import java.util.*;
import javax.swing.*;

// SimpleWriter version 3.3 (writes Simple format 8: least squares alone, the
// wide clamp, centred resizing)
//
// A writer with one predictor and one entropy coder: every interior pixel is
// predicted by the least-squares fit (DeltaMapper.LeastSquares: at each pixel,
// the linear predictor that best fits a causal window of the pixels before
// it, clamped to 2 beyond the range of its eight neighbours), with no blocks
// and no map, and the deltas are mixed-context coded with the second
// opinions (DeltaMapper.packMixedDeltas). The analysis picks the channel set,
// then, channel by channel, the window radius (4, 8 or 16:
// DeltaMapper.WINDOW_RADII) that codes the channel smallest, by the real
// coded size (DeltaMapper.surveyWindows). It runs at
// startup and again after any change to the quantization (Pixel Resolution
// or Color Resolution). Pixel Resolution resizes with ResizeMapper.resize2
// (centred). The Bands menu splits the image into up to MAX_BANDS bands of
// rows that a reader can decode at the same time, for a small cost per band
// boundary (the fit and the contexts start again at each band's top); the
// reader also decodes a band's three channels at the same time. Save
// analyses and codes the image twice (encode), printing both times (the
// first includes compiling the code) and the decode check's apart. Files
// are read by SimpleReader.
public class SimpleWriter
{
	// File format: every file starts with FORMAT_ID ('S' = Simple format) and
	// FORMAT_VERSION. Bump FORMAT_VERSION in the writer and reader together
	// whenever the file layout or a coder's output changes.
	static final char FORMAT_ID      = 'S';
	static final int  FORMAT_VERSION = 8;   // 3 to 5: unary strings or block maps with a choice of delta type and entropy coder (no longer read); 6: least squares alone, a window radius and clamp per channel, in bands; 7: 6 with centred resizing (ResizeMapper.resize2); 8: 7 with the wide clamp only (no clamp byte)
	/**
	 * The most bands Save offers.
	 */
	static final int  MAX_BANDS = 16;
	/**
	 * The fewest rows (of the quantized image) a band may have: smaller images
	 * get fewer bands.
	 */
	static final int  BAND_MIN_ROWS = 64;
	/**
	 * The channel set bestSet picks when it ties for the smallest estimate:
	 * green, blue-green, red-green (DeltaMapper.getChannels).
	 */
	static final int  TIE_SET = 7;

	// ---- Image state --------------------------------------------------------
	ViewerSupport view;
	BufferedImage original_image;
	BufferedImage working_image;
	String        filename;
	int           image_xdim, image_ydim;
	int[][]       source;   // blue, green, red of the original

	// ---- Parameters ---------------------------------------------------------
	int pixel_quant = 4;
	int pixel_shift = 3;
	int correction  = 0;
	int min_set_id  = 0;
	/**
	 * How many independently decodable bands Save writes; at most MAX_BANDS,
	 * and fewer if bands would have under BAND_MIN_ROWS rows.
	 */
	int bands       = 1;

	// ---- The last analysis (see analyse) ------------------------------------
	/**
	 * Each channel's window radius and deltas (null before the first
	 * analysis).
	 */
	DeltaMapper.WindowSurvey survey = null;
	/**
	 * The quantization the analysis was made for.
	 */
	int     survey_quant = -1, survey_shift = -1;
	/**
	 * The analysis's time, printed with the next Apply's listing; -1 once printed.
	 */
	long    survey_time  = -1;

	JSlider pquant_slider, pshift_slider, corr_slider;
	/**
	 * An analysis is running in the background.
	 */
	boolean analysing = false;
	/**
	 * Waits for the quantization sliders to settle before analysing.
	 */
	javax.swing.Timer quant_timer;

	// ---- Results of the last Apply (read by Save) ---------------------------
	int[]    channel_sum = new int[6];
	int[]    channel_init = new int[6], channel_min = new int[6];
	/**
	 * The channels as the reader rebuilds them (before any minimum is added
	 * back): what Save codes.
	 */
	int[][]  value_list = new int[3][];
	int      delta_xdim;

	long    file_length;
	boolean applied  = false;   // the last Apply finished; Save needs it

	public static void main(String[] args)
	{
		ViewerSupport.applyHiDpiFontScaleIfNeeded();
		if(args.length == 1) { new SimpleWriter(args[0]); return; }
		FileDialog fd = new FileDialog((java.awt.Frame) null, "Open Image", FileDialog.LOAD);
		fd.setVisible(true);
		if(fd.getFile() != null) new SimpleWriter(new File(fd.getDirectory(), fd.getFile()).getPath());
		else System.exit(0);
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
		System.out.println(String.format("Loaded %s, %d x %d (%,d pixels)", filename, image_xdim, image_ydim, (long) image_xdim * image_ydim));

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

		// A quantization change analyses again, once the slider has stopped
		// moving for QUANT_SETTLE_MS.
		quant_timer = new javax.swing.Timer(QUANT_SETTLE_MS, e -> runAnalysis());
		quant_timer.setRepeats(false);

		JMenu file_menu = new JMenu("File");
		JMenuItem open_item = new JMenuItem("Open...");
		open_item.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_O, InputEvent.CTRL_DOWN_MASK));
		open_item.addActionListener(e -> { FileDialog fd = new FileDialog(frame, "Open Image", FileDialog.LOAD); fd.setVisible(true); if(fd.getFile() != null) new SimpleWriter(fd.getDirectory() + fd.getFile()); });
		file_menu.add(open_item); file_menu.addSeparator();
		JMenuItem reset_item = new JMenuItem("Reset");
		reset_item.addActionListener(e -> { pixel_quant = 0; pixel_shift = 0; correction = 0; pquant_slider.setValue(0); pshift_slider.setValue(0); corr_slider.setValue(0); quantChanged(); });
		file_menu.add(reset_item);
		JMenuItem save_item = new JMenuItem("Save"); save_item.addActionListener(new SaveHandler()); file_menu.add(save_item);

		JMenu quant_menu = new JMenu("Quantization");
		JSlider[] ss = new JSlider[1];
		quant_menu.add(ViewerSupport.makeSliderDialog(frame, "Pixel Resolution", 0, 10, pixel_quant, v -> { pixel_quant = v; quantChanged(); }, ss)); pquant_slider = ss[0];
		quant_menu.add(ViewerSupport.makeSliderDialog(frame, "Color Resolution", 0, 7, pixel_shift, v -> { pixel_shift = v; quantChanged(); }, ss)); pshift_slider = ss[0];
		// Error Correction (below a separator) is not a quantizing step: it blends
		// the preview back toward the original by correction/10. Preview only;
		// the saved file is unaffected, and there is nothing to analyse again.
		quant_menu.addSeparator();
		quant_menu.add(ViewerSupport.makeSliderDialog(frame, "Error Correction", 0, 10, correction, v -> { correction = v; if(surveyCurrent()) apply(); }, ss)); corr_slider = ss[0];

		// Bands menu: Save codes the image as this many bands of rows, each
		// decodable on its own (so a reader can decode them at the same time),
		// for a small cost per band boundary. Like Save itself, it never
		// changes the preview.
		JMenu bands_menu = new JMenu("Bands");
		JSlider[] bs = new JSlider[1];
		bands_menu.add(ViewerSupport.makeSliderDialog(frame, "Number of Bands", 1, MAX_BANDS, bands, v -> { bands = v; view.setStatus(bandText()); }, bs));

		// Window menu: what the analysis chose (nothing to set by hand).
		JMenu window_menu = new JMenu("Window");
		JMenuItem window_item = new JMenuItem("Radius");
		window_item.addActionListener(e -> showText("Window", windowText()));
		window_menu.add(window_item);
		JMenuItem sizes_item = new JMenuItem("Sizes");
		sizes_item.addActionListener(e -> showText("Coded Size by Radius", sizesText()));
		window_menu.add(sizes_item);

		menu_bar.add(file_menu); menu_bar.add(view.makeViewMenu()); menu_bar.add(quant_menu); menu_bar.add(bands_menu); menu_bar.add(window_menu);

		view.setImage(original_image);
		view.show();
		// The preview shows the original until the analysis is done.
		SwingUtilities.invokeLater(this::runAnalysis);
	}

	/**
	 * How long a quantization slider must rest before the analysis runs again,
	 * in milliseconds.
	 */
	static final int QUANT_SETTLE_MS = 400;

	// ---- Analysis ---------------------------------------------------------------

	// After a quantization change: analyses again once the sliders settle.
	private void quantChanged()
	{
		view.setStatus("waiting to analyse...");
		quant_timer.restart();
	}

	// Whether the last analysis was made for the current quantization.
	private boolean surveyCurrent() { return survey != null && survey_quant == pixel_quant && survey_shift == pixel_shift; }

	// Analyses in the background with the other menus disabled, then applies
	// (which prints the listing). If the quantization changed meanwhile, it
	// analyses again.
	private void runAnalysis()
	{
		if(analysing) return;   // done() checks the quantization again
		analysing = true;
		view.setMenusEnabled(false);
		view.setStatus("analysing...");
		final int quant = pixel_quant, shift = pixel_shift;
		new SwingWorker<Void,Void>()
		{
			@Override protected Void doInBackground() { analyse(quant, shift); return null; }
			@Override protected void done()
			{
				boolean failed = false;
				try { get(); }
				catch(Exception e) { System.out.println("analysis: " + e); failed = true; }
				analysing = false;
				boolean changed = (quant != pixel_quant || shift != pixel_shift);
				if(changed && !quant_timer.isRunning()) { runAnalysis(); return; }
				if(failed) { view.setMenusEnabled(true); view.setStatus("analysis failed"); return; }
				view.setMenusEnabled(true);
				view.setStatus(null);
				if(surveyCurrent()) apply();
			}
		}.execute();
	}

	/**
	 * Picks the channel set, then each channel's window radius, for
	 * this quantization (DeltaMapper.surveyWindows); keeps them in survey.
	 *
	 * @param quant the Pixel Resolution
	 * @param shift the Color Resolution
	 */
	public void analyse(int quant, int shift)
	{
		long    start = System.nanoTime();
		int[]   size  = DeltaMapper.getQuantizedSize(image_xdim, image_ydim, quant);
		int[][] qc    = quantizedChannels(size, quant, shift, new int[6], new int[6]);
		int[]   sum   = new int[6];
		ViewerSupport.parallel(6, i -> sum[i] = (int) Math.floor(CodeMapper.getShannonLimit(DeltaMapper.getIdealFrequency(qc[i], size[0], size[1]))));
		int[] id = DeltaMapper.getChannels(bestSet(sum));
		DeltaMapper.WindowSurvey s = DeltaMapper.surveyWindows(new int[][] { qc[id[0]], qc[id[1]], qc[id[2]] }, size[0], size[1], DeltaMapper.WINDOW_RADII);
		synchronized(this)
		{
			survey       = s;
			survey_quant = quant;
			survey_shift = shift;
			survey_time  = System.nanoTime() - start;
		}
	}

	// The six candidate channels after quantizing (see
	// DeltaMapper.getCandidateChannels); fills min and init.
	private int[][] quantizedChannels(int[] size, int quant, int shift, int[] min, int[] init)
	{
		int[][] q = new int[3][];
		for(int i = 0; i < 3; i++)
		{
			int[] ch = source[i];
			if(quant != 0) ch = ResizeMapper.resize2(ch, image_xdim, size[0], size[1]);
			q[i] = DeltaMapper.quantizeChannel(ch, shift);
		}
		int[][] qc = DeltaMapper.getCandidateChannels(q[0], q[1], q[2], min);
		for(int i = 0; i < 6; i++) init[i] = qc[i][0];
		return qc;
	}

	/**
	 * The channel set with the smallest summed estimate: on a tie, set
	 * TIE_SET if it is one of those tied (on an image where the estimates
	 * can't tell the sets apart, such as a gray one, its difference channels
	 * code smaller), otherwise the first.
	 *
	 * @param channel_sum each candidate channel's estimate
	 * @return the channel set
	 */
	private static int bestSet(int[] channel_sum)
	{
		int[] tie = DeltaMapper.getChannels(TIE_SET);
		int best = TIE_SET, best_sum = channel_sum[tie[0]] + channel_sum[tie[1]] + channel_sum[tie[2]];
		for(int s = 0; s < 10; s++)
		{
			int[] c = DeltaMapper.getChannels(s);
			int t = channel_sum[c[0]] + channel_sum[c[1]] + channel_sum[c[2]];
			if(t < best_sum) { best_sum = t; best = s; }
		}
		return best;
	}

	// ---- The Window menu --------------------------------------------------------

	/**
	 * The channel set and each channel's window, as Apply prints them.
	 *
	 * @param set_id the channel set
	 * @param s      the analysis
	 * @return the listing, each line ending in a newline
	 */
	static String describeWindows(int set_id, DeltaMapper.WindowSurvey s)
	{
		StringBuilder t = new StringBuilder("Channel set: " + DeltaMapper.SET_NAMES[set_id] + "\n");
		for(int i = 0; i < 3; i++)
			t.append(String.format("Channel %d - %s: radius %d (%,d bytes)%n", i, DeltaMapper.channelName(set_id, i), s.radius[i], s.bytes[i]));
		return t.toString();
	}

	// Each channel's window, and the bands Save will write.
	private String windowText()
	{
		if(!applied) return "No analysis yet.";
		String t = describeWindows(min_set_id, survey);
		if(bandsUsed() > 1) t += describeBands(bandsUsed(), value_list[0].length / delta_xdim) + "\n";
		return t;
	}

	// Each channel's coded size at every radius tried; the chosen one starred.
	private String sizesText()
	{
		if(!applied) return "No analysis yet.";
		DeltaMapper.WindowSurvey s = survey;
		StringBuilder t = new StringBuilder();
		for(int i = 0; i < 3; i++)
		{
			if(i > 0) t.append('\n');
			t.append(String.format("Channel %d - %s:%n", i, DeltaMapper.channelName(min_set_id, i)));
			t.append(String.format("  %6s %12s%n", "radius", "bytes"));
			for(int r = 0; r < s.radii.length; r++)
				t.append(String.format("  %6d %,11d%s%n", s.radii[r], s.tried[i][r], (s.radii[r] == s.radius[i]) ? "*" : " "));
		}
		return t.toString();
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
	 * A band's first row.
	 *
	 * @param b    the band (n for the end of the last)
	 * @param n    the number of bands
	 * @param rows the rows split into them
	 * @return the row
	 */
	static int bandStart(int b, int n, int rows) { return (int) ((long) b * rows / n); }

	/**
	 * The bands in words: "8 bands of 375 rows" (or "of 374 to 375 rows").
	 *
	 * @param n    the number of bands
	 * @param rows the rows split into them
	 * @return the description
	 */
	static String describeBands(int n, int rows)
	{
		int lo = rows / n, hi = (rows + n - 1) / n;
		return n + " bands of " + ((lo == hi) ? lo : lo + " to " + hi) + " rows";
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
		return describeBands(n, DeltaMapper.getQuantizedSize(image_xdim, image_ydim, pixel_quant)[1]);
	}

	// Shows text in a dialog, in a fixed-width font so the columns line up.
	private void showText(String title, String text)
	{
		JTextArea area = new JTextArea(text.trim());
		area.setEditable(false);
		area.setFont(new Font(Font.MONOSPACED, Font.PLAIN, area.getFont().getSize()));
		JScrollPane pane = new JScrollPane(area);
		Dimension d = area.getPreferredSize();
		pane.setPreferredSize(new Dimension(Math.min(d.width + 40, 900), Math.min(d.height + 30, 600)));
		JOptionPane.showMessageDialog(view.frame, pane, title, JOptionPane.PLAIN_MESSAGE);
	}

	// ---- Apply --------------------------------------------------------------

	// Quantizes, picks the channel set (the channels Save codes), then
	// rebuilds the image for the preview. After a new analysis it prints the
	// channel set and each channel's window.
	void apply()
	{
		applied = false;
		try { applyImpl(); applied = true; }
		catch(Exception e) { System.out.println("Apply: " + e); e.printStackTrace(); }
	}

	private void applyImpl()
	{
		// An analysis for this quantization first, if there isn't one (a coder
		// whose fields were set directly, or an analysis that failed).
		if(!surveyCurrent()) analyse(pixel_quant, pixel_shift);

		int[]   size = DeltaMapper.getQuantizedSize(image_xdim, image_ydim, pixel_quant);
		int     new_xdim = size[0], new_ydim = size[1];
		int[][] qc = quantizedChannels(size, pixel_quant, pixel_shift, channel_min, channel_init);
		ViewerSupport.parallel(6, i -> channel_sum[i] = (int) Math.floor(CodeMapper.getShannonLimit(DeltaMapper.getIdealFrequency(qc[i], new_xdim, new_ydim))));
		min_set_id = bestSet(channel_sum);

		// The reader rebuilds each channel exactly as quantized (before the
		// minimum is added back), so the preview uses the quantized channels
		// as they are: no need to decode the deltas here.
		int[]   channel_id = DeltaMapper.getChannels(min_set_id);
		int[][] dc = new int[3][];
		for(int i = 0; i < 3; i++)
		{
			int j = channel_id[i];
			value_list[i] = qc[j];
			dc[i] = qc[j].clone();
			if(j > 2) for(int k = 0; k < dc[i].length; k++) dc[i][k] += channel_min[j];
		}
		delta_xdim = new_xdim;

		if(survey_time >= 0)
		{
			System.out.print(describeWindows(min_set_id, survey));
			System.out.println(String.format("Analysis took %s (%d x %d, %,d pixels)", ViewerSupport.formatDuration(survey_time), new_xdim, new_ydim, (long) new_xdim * new_ydim));
			System.out.println();
			survey_time = -1;
		}

		// Like SimpleReader: recombine the channels first, then resize, then
		// shift. Resizing each channel before recombining gives different
		// results (ResizeMapper rounds down). Resizing is centred (resize2).
		int[][] bgr = DeltaMapper.getBlueGreenRed(min_set_id, dc[0], dc[1], dc[2]);
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
			// includes compiling the code (the first time it runs in this JVM;
			// the preview's analysis has compiled some of it already), the
			// second is the run time only. Each includes the analysis (the
			// channel set and the window survey: encode does it again, the
			// same as the preview's) but leaves out the decode check, which is
			// timed on its own. Both give the same bytes.
			File file = new File("foo");
			long[] took = new long[2], check = new long[2];
			int[]  size = DeltaMapper.getQuantizedSize(image_xdim, image_ydim, pixel_quant);
			try
			{
				int[]   min = new int[6];
				int[][] qc  = quantizedChannels(size, pixel_quant, pixel_shift, min, new int[6]);
				byte[]  data = null;
				for(int pass = 0; pass < 2; pass++)
				{
					ByteArrayOutputStream bytes = new ByteArrayOutputStream();
					long start = System.nanoTime();
					check[pass] = encode(new DataOutputStream(bytes), qc, min, size[0], image_xdim, image_ydim, pixel_shift, pixel_quant, bandsUsed());
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
			int n = bandsUsed(), raw = image_xdim * image_ydim * 3;
			if(n > 1) System.out.println(String.format("Saved %s: %,d bytes (%s)", file.getName(), file.length(), describeBands(n, size[1])));
			else      System.out.println(String.format("Saved %s: %,d bytes", file.getName(), file.length()));
			System.out.println(String.format("Coded %d x %d (%,d pixels)", size[0], size[1], (long) size[0] * size[1]));
			System.out.println("Run time plus compile time: " + ViewerSupport.formatDuration(took[0]));
			System.out.println("Run time only:              " + ViewerSupport.formatDuration(took[1]));
			System.out.println("Decode check:               " + ViewerSupport.formatDuration(check[1]));
			System.out.println("Original compression rate: " + String.format("%.4f", (double) file_length / raw));
			System.out.println("Output  compression rate:  " + String.format("%.4f", (double) file.length() / raw));
			System.out.println();
		}
	}

	/**
	 * Analyses and codes the channels and writes the file. Format 8.
	 * Header: FORMAT_ID, FORMAT_VERSION, width, height (unsigned shorts),
	 * pixel_shift, pixel_quant, channel set; per channel (coding order) int
	 * min and the window radius (an unsigned byte); the number of bands n (an
	 * unsigned byte), n int lengths, and the n bands. Band b is rows
	 * bandStart(b) to bandStart(b + 1) of the quantized image, coded as an
	 * image of its own (the fit and the contexts start again at its top):
	 * per channel, int init, then the least-squares deltas mixed-context
	 * coded with the second opinions (DeltaMapper.packMixedDeltas). Channels
	 * 1 and 2 use the ones before them (their values in the fit, their deltas
	 * as context), so a band's channels are decoded in order, each a couple
	 * of rows behind the one before; the bands can be decoded at the same
	 * time.
	 * <p>
	 * The analysis as the preview's: the channel set with the smallest
	 * estimate (bestSet), then each channel's window radius by the real
	 * coded size (DeltaMapper.surveyWindows). One band is what the survey
	 * already coded; more bands reuse its deltas past each band's first rows
	 * (DeltaMapper.getLeastSquaresBandDeltas) and are coded at the same time.
	 * Once everything is coded, each band's channels are checked by decoding
	 * them back, at the same time; the time that took is returned, so the
	 * coding can be timed without it.
	 *
	 * @param out         the stream
	 * @param qc          the six candidate channels, quantized (DeltaMapper.getCandidateChannels)
	 * @param min         their minimums
	 * @param xdim        their width
	 * @param image_xdim  the image's width
	 * @param image_ydim  the image's height
	 * @param pixel_shift the Color Resolution
	 * @param pixel_quant the Pixel Resolution
	 * @param n           the number of bands
	 * @return the time the decode check took, in nanoseconds
	 * @throws IOException if the stream fails
	 */
	static long encode(DataOutputStream out, int[][] qc, int[] min, int xdim, int image_xdim, int image_ydim, int pixel_shift, int pixel_quant, int n) throws IOException
	{
		int rows = qc[0].length / xdim;
		int[] sum = new int[6];
		ViewerSupport.parallel(6, i -> sum[i] = (int) Math.floor(CodeMapper.getShannonLimit(DeltaMapper.getIdealFrequency(qc[i], xdim, rows))));
		int     set_id = bestSet(sum);
		int[]   id = DeltaMapper.getChannels(set_id);
		int[][] value = {qc[id[0]], qc[id[1]], qc[id[2]]};
		DeltaMapper.WindowSurvey s = DeltaMapper.surveyWindows(value, xdim, rows, DeltaMapper.WINDOW_RADII);

		int[][][]  ch = new int[n][3][], d = new int[n][3][];   // [band][channel]
		byte[][][] coded = new byte[n][3][];
		ViewerSupport.parallel(n, b ->
		{
			int y0 = bandStart(b, n, rows), y1 = bandStart(b + 1, n, rows);
			for(int i = 0; i < 3; i++)
			{
				ch[b][i] = (n == 1) ? value[i] : Arrays.copyOfRange(value[i], y0 * xdim, y1 * xdim);
				d[b][i]  = (n == 1) ? s.deltas[i] : DeltaMapper.getLeastSquaresBandDeltas(s.deltas[i], value[i], (i > 0) ? value[i - 1] : null, xdim, y0, y1, s.radius[i]);
			}
			ViewerSupport.parallel(3, i ->
			{
				try { coded[b][i] = (n == 1) ? s.coded[i] : DeltaMapper.packMixedDeltas(d[b][i], ch[b][i], Arrays.copyOf(d[b], i), xdim, true); }
				catch(IOException e) { throw new UncheckedIOException(e); }
			});
		});
		// The check, once everything is coded (so its time can be told
		// apart): each band's channels decoded back, at the same time.
		long check = System.nanoTime();
		ViewerSupport.parallel(n, b -> ViewerSupport.parallel(3, i ->
		{
			try
			{
				int[] c = ch[b][i], dd = d[b][i], p = new int[c.length];
				for(int k = 0; k < p.length; k++) p[k] = c[k] - dd[k];
				if(!Arrays.equals(DeltaMapper.unpackMixedDeltas(coded[b][i], c, Arrays.copyOf(d[b], i), xdim, p), dd))
					System.out.println("WARNING: band " + b + " channel " + i + " deltas do not decode back.");
			}
			catch(IOException e) { throw new UncheckedIOException(e); }
		}));
		long check_time = System.nanoTime() - check;

		byte[][] band = new byte[n][];
		for(int b = 0; b < n; b++)
		{
			ByteArrayOutputStream bytes = new ByteArrayOutputStream();
			DataOutputStream bo = new DataOutputStream(bytes);
			for(int i = 0; i < 3; i++) { bo.writeInt(ch[b][i][0]); bo.write(coded[b][i]); }
			band[b] = bytes.toByteArray();
		}
		out.writeByte(FORMAT_ID); out.writeByte(FORMAT_VERSION);
		out.writeShort(image_xdim); out.writeShort(image_ydim);
		out.writeByte(pixel_shift); out.writeByte(pixel_quant); out.writeByte(set_id);
		for(int i = 0; i < 3; i++)
		{
			out.writeInt(min[id[i]]);
			out.writeByte(s.radius[i]);
		}
		out.writeByte(n);
		for(int b = 0; b < n; b++) out.writeInt(band[b].length);
		for(int b = 0; b < n; b++) out.write(band[b]);
		return check_time;
	}
}
