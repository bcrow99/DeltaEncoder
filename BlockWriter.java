import java.awt.*;
import java.awt.event.*;
import java.awt.image.*;
import java.io.*;
import java.util.*;
import javax.swing.*;

// BlockWriter version 2.3 (writes Block format 5: mixed context coding, a
// block size per channel; or format 6, the same in bands)
//
// A writer with one delta type and one entropy coder: the block map (delta
// type 13) with mixed-context-coded deltas (DeltaMapper.packMixedDeltas)
// and a coded map. Each block uses one predictor from its channel's set, and a
// set can be any of the 256 predictors in DeltaMapper.PREDICTOR_TABLE (id
// 255 is the least-squares predictor, LS: see DeltaMapper.LeastSquares).
// There are no block settings to choose: the survey makes them from the
// predictor table alone. It picks the channel set, then, channel by
// channel, the block size (a square of 4, 6, 8, 12 or 16) and the predictor set
// that code the channel smallest (DeltaMapper.surveyChannels: predictors
// added in greedy order by estimated coded bits, the estimate's reference
// deltas from the whole table, blocks assigned by the estimate, and the real
// coded size measured at each count). It runs at startup and again after
// any change to the quantization (Pixel Resolution or Color Resolution).
// The survey measures sizes with the Context coder
// (DeltaMapper.packContextDeltas); the file uses the mixed coder, about 1%
// smaller. File > Bands splits the image into up to MAX_BANDS bands of rows
// that a reader can decode at the same time (format 6), for about a byte
// per column per band boundary; with 1 (the default) Save writes format 5.
// Files are read by BlockReader.
public class BlockWriter
{
	// File format: every file starts with FORMAT_ID ('B' = Block format) and
	// FORMAT_VERSION. Bump FORMAT_VERSION in the writer and reader together
	// whenever the file layout or a coder's output changes.
	static final char FORMAT_ID      = 'B';
	static final int  FORMAT_VERSION = 6;   // 3: one predictor table (DeltaMapper.PREDICTOR_TABLE) and cross-channel block maps; 4: mixed context coding; 5: a block size per channel; 6: independently decodable bands
	/**
	 * The version written with one band: the layout before bands.
	 */
	static final int  ONE_BAND_VERSION = 5;
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
	 * Roughly what each band boundary costs, in tenths of a byte per column
	 * (measured: 0.8 to 1.6 bytes per column); for the Bands estimate only.
	 */
	static final int  BAND_COST_TENTHS = 13;

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
	 * How many independently decodable bands Save writes (1: one, format 5);
	 * at most MAX_BANDS, and fewer if bands would have under BAND_MIN_ROWS rows.
	 */
	int bands       = 1;

	// ---- The last survey (see survey) ----------------------------------------
	/**
	 * Each channel's block size and predictor ids, in coding order (null
	 * before the first survey).
	 */
	int[]   survey_blocks = null;
	/**
	 * Each channel's predictor ids, in coding order.
	 */
	int[][] survey_ids    = null;
	/**
	 * Each channel's deltas and block map as the survey chose them (what Apply
	 * codes).
	 */
	int[][] survey_deltas = null;
	/**
	 * Each channel's block map as the survey chose it.
	 */
	byte[][] survey_maps  = null;
	/**
	 * The quantization the survey was made for.
	 */
	int     survey_quant = -1, survey_shift = -1;
	/**
	 * The survey's time, printed with the next Apply's listing; -1 once printed.
	 */
	long    survey_time  = -1;
	/**
	 * The survey's coded size (Context-coded deltas plus maps), for the Bands
	 * estimate.
	 */
	long    survey_bytes = 0;

	JSlider pquant_slider, pshift_slider, corr_slider;
	/**
	 * A survey is running in the background.
	 */
	boolean analysing = false;
	/**
	 * Waits for the quantization sliders to settle before surveying.
	 */
	javax.swing.Timer quant_timer;

	// ---- Results of the last Apply (read by Save) ---------------------------
	int[]    set_sum = new int[10], channel_sum = new int[6];
	int[]    channel_init = new int[6], channel_min = new int[6];
	int[][]  delta_list = new int[3][];
	/**
	 * The channels as the reader rebuilds them (before any minimum is added
	 * back): the mixed coder's contexts use them.
	 */
	int[][]  value_list = new int[3][];
	byte[][] map_list   = new byte[3][];
	int      delta_xdim;

	long    file_length;
	boolean applied  = false;   // the last Apply finished; Save needs it

	public static void main(String[] args)
	{
		ViewerSupport.applyHiDpiFontScaleIfNeeded();
		if(args.length == 1) { new BlockWriter(args[0]); return; }
		FileDialog fd = new FileDialog((java.awt.Frame) null, "Open Image", FileDialog.LOAD);
		fd.setVisible(true);
		if(fd.getFile() != null) new BlockWriter(new File(fd.getDirectory(), fd.getFile()).getPath());
		else System.exit(0);
	}

	public BlockWriter(String _filename)
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

		view = new ViewerSupport("Block Writer  " + filename, image_xdim, image_ydim);
		JFrame   frame    = view.frame;
		JMenuBar menu_bar = frame.getJMenuBar();

		// A quantization change surveys again, once the slider has stopped
		// moving for QUANT_SETTLE_MS.
		quant_timer = new javax.swing.Timer(QUANT_SETTLE_MS, e -> runAnalysis());
		quant_timer.setRepeats(false);

		JMenu file_menu = new JMenu("File");
		JMenuItem open_item = new JMenuItem("Open...");
		open_item.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_O, InputEvent.CTRL_DOWN_MASK));
		open_item.addActionListener(e -> { FileDialog fd = new FileDialog(frame, "Open Image", FileDialog.LOAD); fd.setVisible(true); if(fd.getFile() != null) new BlockWriter(fd.getDirectory() + fd.getFile()); });
		file_menu.add(open_item); file_menu.addSeparator();
		JMenuItem reset_item = new JMenuItem("Reset");
		reset_item.addActionListener(e -> { pixel_quant = 0; pixel_shift = 0; correction = 0; pquant_slider.setValue(0); pshift_slider.setValue(0); corr_slider.setValue(0); quantChanged(); });
		file_menu.add(reset_item);
		// Bands: Save codes the image as this many bands of rows, each decodable
		// on its own (so a reader can decode them at the same time), for a small
		// cost per band boundary. Like Save itself, it never changes the preview.
		JSlider[] bs = new JSlider[1];
		file_menu.add(ViewerSupport.makeSliderDialog(frame, "Bands", 1, MAX_BANDS, bands, v -> { bands = v; view.setStatus(bandEstimate()); }, bs));
		JMenuItem save_item = new JMenuItem("Save"); save_item.addActionListener(new SaveHandler()); file_menu.add(save_item);

		JMenu quant_menu = new JMenu("Quantization");
		JSlider[] ss = new JSlider[1];
		quant_menu.add(ViewerSupport.makeSliderDialog(frame, "Pixel Resolution", 0, 10, pixel_quant, v -> { pixel_quant = v; quantChanged(); }, ss)); pquant_slider = ss[0];
		quant_menu.add(ViewerSupport.makeSliderDialog(frame, "Color Resolution", 0, 7, pixel_shift, v -> { pixel_shift = v; quantChanged(); }, ss)); pshift_slider = ss[0];
		// Error Correction (below a separator) is not a quantizing step: it blends
		// the preview back toward the original by correction/10. Preview only;
		// the saved file is unaffected, and there is nothing to survey again.
		quant_menu.addSeparator();
		quant_menu.add(ViewerSupport.makeSliderDialog(frame, "Error Correction", 0, 10, correction, v -> { correction = v; if(surveyCurrent()) apply(); }, ss)); corr_slider = ss[0];

		// Block menu: what the survey chose (nothing to set by hand).
		JMenu block_menu = new JMenu("Block");
		JMenuItem size_item = new JMenuItem("Size");
		size_item.addActionListener(e -> showText("Block Size", sizeText()));
		block_menu.add(size_item);
		JMenuItem predictor_item = new JMenuItem("Predictors");
		predictor_item.addActionListener(e -> showText("Block Predictors", predictorText()));
		block_menu.add(predictor_item);

		menu_bar.add(file_menu); menu_bar.add(view.makeViewMenu()); menu_bar.add(quant_menu); menu_bar.add(block_menu);

		view.setImage(original_image);
		view.show();
		// The preview shows the original until the survey has chosen the blocks.
		SwingUtilities.invokeLater(this::runAnalysis);
	}

	/**
	 * How long a quantization slider must rest before the survey runs again, in
	 * milliseconds.
	 */
	static final int QUANT_SETTLE_MS = 400;

	// ---- Survey -----------------------------------------------------------------

	// After a quantization change: surveys again once the sliders settle.
	private void quantChanged()
	{
		view.setStatus("waiting to survey...");
		quant_timer.restart();
	}

	// Whether the last survey was made for the current quantization.
	private boolean surveyCurrent() { return survey_blocks != null && survey_quant == pixel_quant && survey_shift == pixel_shift; }

	// Surveys in the background with the other menus disabled, then applies
	// (which prints the listing). If the quantization changed meanwhile, it
	// surveys again.
	private void runAnalysis()
	{
		if(analysing) return;   // done() checks the quantization again
		analysing = true;
		view.setMenusEnabled(false);
		view.setStatus("surveying...");
		final int quant = pixel_quant, shift = pixel_shift;
		new SwingWorker<Void,Void>()
		{
			@Override protected Void doInBackground() { survey(quant, shift); return null; }
			@Override protected void done()
			{
				boolean failed = false;
				try { get(); }
				catch(Exception e) { System.out.println("survey: " + e); failed = true; }
				analysing = false;
				boolean changed = (quant != pixel_quant || shift != pixel_shift);
				if(changed && !quant_timer.isRunning()) { runAnalysis(); return; }
				if(failed) { view.setMenusEnabled(true); view.setStatus("survey failed"); return; }
				view.setMenusEnabled(true);
				view.setStatus(null);
				if(surveyCurrent()) apply();
			}
		}.execute();
	}

	/**
	 * Surveys the channel set, then each channel's block size and predictor
	 * set, for this quantization (DeltaMapper.surveyChannels); keeps them in
	 * survey_blocks and survey_ids.
	 *
	 * @param quant the Pixel Resolution
	 * @param shift the Color Resolution
	 */
	public void survey(int quant, int shift)
	{
		long    start = System.nanoTime();
		int[]   size  = DeltaMapper.getQuantizedSize(image_xdim, image_ydim, quant);
		int[][] qc    = quantizedChannels(size, quant, shift, new int[6], new int[6]);
		int[]   sum   = new int[6];
		ViewerSupport.parallel(6, i -> sum[i] = (int) Math.floor(CodeMapper.getShannonLimit(DeltaMapper.getIdealFrequency(qc[i], size[0], size[1]))));
		int[] id = DeltaMapper.getChannels(bestSet(sum));
		DeltaMapper.BlockSurvey survey = DeltaMapper.surveyChannels(new int[][] { qc[id[0]], qc[id[1]], qc[id[2]] }, size[0], size[1],
			DeltaMapper.PREDICTOR_TABLE, DeltaMapper.SURVEY_CHANNEL_SIZES, true);
		synchronized(this)
		{
			survey_blocks = survey.blocks;
			survey_ids    = survey.ids;
			survey_deltas = survey.deltas;
			survey_maps   = survey.maps;
			survey_quant  = quant;
			survey_shift  = shift;
			survey_time   = System.nanoTime() - start;
			survey_bytes  = survey.bytes;
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
			if(quant != 0) ch = ResizeMapper.resize(ch, image_xdim, size[0], size[1]);
			q[i] = DeltaMapper.quantizeChannel(ch, shift);
		}
		int[][] qc = DeltaMapper.getCandidateChannels(q[0], q[1], q[2], min);
		for(int i = 0; i < 6; i++) init[i] = qc[i][0];
		return qc;
	}

	private static int bestSet(int[] channel_sum)
	{
		int best = 0, best_sum = Integer.MAX_VALUE;
		for(int s = 0; s < 10; s++)
		{
			int[] c = DeltaMapper.getChannels(s);
			int t = channel_sum[c[0]] + channel_sum[c[1]] + channel_sum[c[2]];
			if(t < best_sum) { best_sum = t; best = s; }
		}
		return best;
	}

	// ---- The Block menu ---------------------------------------------------------

	// Each channel's block size and number of blocks.
	private String sizeText()
	{
		if(!applied) return "No survey yet.";
		StringBuilder t = new StringBuilder();
		for(int i = 0; i < 3; i++) t.append(DeltaMapper.describeBlockSize(min_set_id, i, map_list[i], delta_xdim)).append('\n');
		if(bandsUsed() > 1) t.append(describeBands(bandsUsed(), value_list[0].length / delta_xdim)).append('\n');
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
	 * The Bands dialog's estimate of what the bands cost.
	 *
	 * @return the estimate, for the status line
	 */
	private String bandEstimate()
	{
		int n = bandsUsed();
		if(n == 1) return (bands > 1) ? "1 band: the image is too small for more" : "1 band";
		int[] size = DeltaMapper.getQuantizedSize(image_xdim, image_ydim, pixel_quant);
		long  cost = (long) (n - 1) * size[0] * BAND_COST_TENTHS / 10;
		String pct = (survey_bytes > 0) ? String.format(" (%+.2f%%)", 100.0 * cost / survey_bytes) : "";
		return describeBands(n, size[1]) + String.format(": about +%,d bytes", cost) + pct;
	}

	// Each channel's predictors, most used first, with their numbers of blocks.
	private String predictorText()
	{
		if(!applied) return "No survey yet.";
		StringBuilder t = new StringBuilder();
		for(int i = 0; i < 3; i++)
		{
			if(i > 0) t.append('\n');
			t.append(String.format("Channel %d - %s:%n", i, DeltaMapper.channelName(min_set_id, i)));
			t.append(DeltaMapper.describeBlockPredictors(map_list[i], "  "));
		}
		return t.toString();
	}

	// Shows text in a dialog, in a fixed-width font so the counts line up.
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

	// Quantizes, picks the channel set, makes the block map deltas (what
	// Save codes), then rebuilds the image from them for the preview. After
	// a new survey it prints the channel set and each channel's blocks and
	// predictors.
	void apply()
	{
		applied = false;
		try { applyImpl(); applied = true; }
		catch(Exception e) { System.out.println("Apply: " + e); e.printStackTrace(); }
	}

	private void applyImpl()
	{
		// A survey for this quantization first, if there isn't one (a coder
		// whose fields were set directly, or a survey that failed).
		if(!surveyCurrent()) survey(pixel_quant, pixel_shift);

		int[]   size = DeltaMapper.getQuantizedSize(image_xdim, image_ydim, pixel_quant);
		int     new_xdim = size[0], new_ydim = size[1];
		int[][] qc = quantizedChannels(size, pixel_quant, pixel_shift, channel_min, channel_init);
		ViewerSupport.parallel(6, i -> channel_sum[i] = (int) Math.floor(CodeMapper.getShannonLimit(DeltaMapper.getIdealFrequency(qc[i], new_xdim, new_ydim))));
		min_set_id = bestSet(channel_sum);

		int[]   channel_id = DeltaMapper.getChannels(min_set_id);
		int[][] dc = new int[3][];
		// The deltas and maps the survey chose (for this quantization, so for
		// these channels): each channel's block size and set, blocks assigned
		// by estimated coded size.
		// The reader rebuilds each channel exactly as quantized (before the
		// minimum is added back), so the preview and the mixed coder use the
		// quantized channels as they are: no need to decode the deltas here.
		ViewerSupport.parallel(3, i ->
		{
			int j = channel_id[i];
			delta_list[i] = survey_deltas[i];
			map_list[i]   = survey_maps[i];
			value_list[i] = qc[j];
			dc[i] = qc[j].clone();
			if(j > 2) for(int k = 0; k < dc[i].length; k++) dc[i][k] += channel_min[j];
		});
		delta_xdim = new_xdim;

		if(survey_time >= 0)
		{
			System.out.print(DeltaMapper.describeBlockMaps(min_set_id, map_list, delta_xdim));
			System.out.println("Analysis took " + ViewerSupport.formatDuration(survey_time));
			System.out.println();
			survey_time = -1;
		}

		// Like BlockReader: recombine the channels first, then resize, then
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
			if(one_band_bytes > 0)
				System.out.println(String.format("Saved %s: %,d bytes (%s; %,d as one band, %+.2f%%)", file.getName(), file.length(),
					describeBands(bandsUsed(), value_list[0].length / delta_xdim), one_band_bytes, 100.0 * (file.length() - one_band_bytes) / one_band_bytes));
			else
				System.out.println(String.format("Saved %s: %,d bytes", file.getName(), file.length()));
			System.out.println("Original compression rate: " + String.format("%.4f", (double) file_length / raw));
			System.out.println("Output  compression rate:  " + String.format("%.4f", (double) file.length() / raw));
			System.out.println();
		}

		/**
		 * The size of the file as one band, when Save wrote bands (0 otherwise).
		 */
		long one_band_bytes = 0;

		// One band: format 5 (oneBand). Bands: format 6 (inBands), and the size
		// it would have been as one band for the report.
		private void save(DataOutputStream out) throws IOException
		{
			one_band_bytes = 0;
			if(bandsUsed() == 1) { oneBand(out); return; }
			ByteArrayOutputStream one = new ByteArrayOutputStream();
			oneBand(new DataOutputStream(one));
			one_band_bytes = one.size();
			inBands(out, bandsUsed());
		}

		/**
		 * Format 5. Header: FORMAT_ID, ONE_BAND_VERSION, width, height (unsigned
		 * shorts), pixel_shift, pixel_quant, channel set. Then per channel: int
		 * min, int init, the block map (DeltaMapper.writeMap, type 13; it
		 * carries the channel's block size and predictor ids; its context lines
		 * up the previous channel's map, DeltaMapper.alignMap), then the deltas,
		 * mixed-context coded (DeltaMapper.packMixedDeltas). Channels 1 and 2 use
		 * the ones before them as context, so the reader decodes in order.
		 *
		 * @param out the stream
		 * @throws IOException if the stream fails
		 */
		private void oneBand(DataOutputStream out) throws IOException
		{
			byte[][] coded = new byte[3][];
			ViewerSupport.parallel(3, i ->
			{
				try
				{
					int[][] previous = Arrays.copyOf(delta_list, i);
					coded[i] = DeltaMapper.packMixedDeltas(delta_list[i], value_list[i], previous, delta_xdim);
					int[] back = DeltaMapper.unpackMixedDeltas(coded[i], value_list[i], previous, delta_xdim);
					if(!Arrays.equals(back, delta_list[i])) System.out.println("WARNING: channel " + i + " deltas do not decode back.");
				}
				catch(IOException e) { throw new UncheckedIOException(e); }
			});

			int[] channel_id = DeltaMapper.getChannels(min_set_id);
			out.writeByte(FORMAT_ID); out.writeByte(ONE_BAND_VERSION);
			out.writeShort(image_xdim); out.writeShort(image_ydim);
			out.writeByte(pixel_shift); out.writeByte(pixel_quant); out.writeByte(min_set_id);
			for(int i = 0; i < 3; i++)
			{
				int j = channel_id[i];
				out.writeInt(channel_min[j]); out.writeInt(channel_init[j]);
				DeltaMapper.writeMap(out, 13, map_list[i], (i > 0) ? map_list[i - 1] : null, delta_xdim);
				out.write(coded[i]);
			}
		}
	
		/**
		 * Format 6: the header as format 5's, then the number of bands n (an
		 * unsigned byte), each channel's int min (coding order), then n int
		 * lengths and the n bands. Band b is rows bandStart(b) to bandStart(b +
		 * 1) of the quantized image, coded as an image of its own with the
		 * survey's block sizes and predictor sets (blocks assigned by cost in the
		 * band; the predictors, least squares and contexts start again at its
		 * top): per channel, int init, the block map, then the mixed-coded
		 * deltas. A reader can decode the bands at the same time.
		 *
		 * @param out the stream
		 * @param n   the number of bands
		 * @throws IOException if the stream fails
		 */
		private void inBands(DataOutputStream out, int n) throws IOException
		{
			int   xdim = delta_xdim, rows = value_list[0].length / xdim;
			byte[][] band = new byte[n][];
			for(int b = 0; b < n; b++)
			{
				int y0 = bandStart(b, n, rows), y1 = bandStart(b + 1, n, rows), h = y1 - y0;
				int[][] ch = new int[3][];
				for(int i = 0; i < 3; i++) ch[i] = Arrays.copyOfRange(value_list[i], y0 * xdim, y1 * xdim);
				ArrayList[] got = DeltaMapper.getBlockDeltasByCost(ch, xdim, h, survey_blocks, survey_ids, DeltaMapper.PREDICTOR_TABLE, true);
				int[][]  d = new int[3][];
				byte[][] m = new byte[3][];
				for(int i = 0; i < 3; i++) { d[i] = (int[]) got[i].get(1); m[i] = (byte[]) got[i].get(2); }
				byte[][] coded = new byte[3][];
				final int band_b = b;
				ViewerSupport.parallel(3, i ->
				{
					try
					{
						int[][] previous = Arrays.copyOf(d, i);
						coded[i] = DeltaMapper.packMixedDeltas(d[i], ch[i], previous, xdim);
						if(!Arrays.equals(DeltaMapper.unpackMixedDeltas(coded[i], ch[i], previous, xdim), d[i]))
							System.out.println("WARNING: band " + band_b + " channel " + i + " deltas do not decode back.");
					}
					catch(IOException e) { throw new UncheckedIOException(e); }
				});
				ByteArrayOutputStream bytes = new ByteArrayOutputStream();
				DataOutputStream bo = new DataOutputStream(bytes);
				for(int i = 0; i < 3; i++)
				{
					bo.writeInt(ch[i][0]);
					DeltaMapper.writeMap(bo, 13, m[i], (i > 0) ? m[i - 1] : null, xdim);
					bo.write(coded[i]);
				}
				band[b] = bytes.toByteArray();
			}

			int[] channel_id = DeltaMapper.getChannels(min_set_id);
			out.writeByte(FORMAT_ID); out.writeByte(FORMAT_VERSION);
			out.writeShort(image_xdim); out.writeShort(image_ydim);
			out.writeByte(pixel_shift); out.writeByte(pixel_quant); out.writeByte(min_set_id);
			out.writeByte(n);
			for(int i = 0; i < 3; i++) out.writeInt(channel_min[channel_id[i]]);
			for(int b = 0; b < n; b++) out.writeInt(band[b].length);
			for(int b = 0; b < n; b++) out.write(band[b]);
		}
	}
}
