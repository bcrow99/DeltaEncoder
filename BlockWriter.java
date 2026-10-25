import java.awt.*;
import java.awt.event.*;
import java.awt.image.*;
import java.io.*;
import java.util.*;
import javax.swing.*;

// BlockWriter version 2.1 (writes Block format 4, mixed context coding)
//
// A writer with one delta type and one entropy coder: the block map (delta
// type 13) with mixed-context-coded deltas (DeltaMapper.packMixedDeltas)
// and a coded map. Each block uses one predictor from its channel's set, and a
// set can be any of the 256 predictors in DeltaMapper.PREDICTOR_TABLE (id
// 255 is the least-squares predictor, LS: see DeltaMapper.LeastSquares). The
// sets come from the predictor table alone: the startup analysis (and Find
// Best) picks the channel set, then surveys each channel's set
// (DeltaMapper.surveyBlockSets: predictors added in greedy order by
// estimated coded bits, the estimate's reference deltas from the whole
// table, blocks assigned by the estimate, and the real coded size measured
// at each count), and keeps the block shape that codes smallest -- actual
// sizes, not estimates. The sets are surveyed per block shape: a shape
// picked by hand uses its own (see update). The analysis measures sizes
// with the Context coder (DeltaMapper.packContextDeltas); the file uses the
// mixed coder, about 1% smaller. Files are read by BlockReader.
public class BlockWriter
{
	// File format: every file starts with FORMAT_ID ('B' = Block format) and
	// FORMAT_VERSION. Bump FORMAT_VERSION in the writer and reader together
	// whenever the file layout or a coder's output changes.
	static final char FORMAT_ID      = 'B';
	static final int  FORMAT_VERSION = 4;   // 3: one predictor table (DeltaMapper.PREDICTOR_TABLE) and cross-channel block maps; 4: mixed context coding

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
	int block_size  = DeltaMapper.BLOCK_DEFAULT;   // the square's size
	int block_shape = 0;          // 0: square (block_size); i + 1: DeltaMapper.BLOCK_SHAPES[i]
	boolean survey_shapes = true; // Find Best also tries the rectangles and lines
	// Each channel's surveyed predictor ids, by block shape: every shape the
	// last analysis tried, plus any surveyed since (see update and apply).
	TreeMap<Integer, int[][]> survey_sets = null;

	JSlider        pquant_slider, pshift_slider, corr_slider;
	JSpinner       block_spinner;
	JRadioButton[] shape_button = new JRadioButton[DeltaMapper.BLOCK_SHAPES.length + 1];   // [0] = Square

	// The block map's shape code (DeltaMapper.blockWidth/blockHeight).
	int block() { return (block_shape == 0) ? block_size : DeltaMapper.SHAPE_FIRST + block_shape - 1; }

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
	boolean updating = false;   // set while the analysis moves the controls

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

		JMenu file_menu = new JMenu("File");
		JMenuItem open_item = new JMenuItem("Open...");
		open_item.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_O, InputEvent.CTRL_DOWN_MASK));
		open_item.addActionListener(e -> { FileDialog fd = new FileDialog(frame, "Open Image", FileDialog.LOAD); fd.setVisible(true); if(fd.getFile() != null) new BlockWriter(fd.getDirectory() + fd.getFile()); });
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

		// Block menu: the settings, and Find Best to rerun the analysis
		// (worth doing after changing the quantization: the surveyed sets
		// are kept until then). A shape picked by hand gets its own surveyed
		// sets, from the analysis or surveyed when picked.
		JMenu block_menu = new JMenu("Block");
		JSpinner[] sp = new JSpinner[1];
		block_menu.add(ViewerSupport.makeSpinnerDialog(frame, "Block Size", DeltaMapper.BLOCK_MIN, DeltaMapper.BLOCK_MAX, block_size,
			v -> { block_size = v; if(!updating) update(); }, sp)); block_spinner = sp[0];
		// Block Shape: squares (of Block Size), or a rectangle or line.
		String[] shape_names = new String[DeltaMapper.BLOCK_SHAPES.length + 1];
		shape_names[0] = "Square (Block Size)";
		for(int i = 0; i < DeltaMapper.BLOCK_SHAPES.length; i++)
		{
			int code = DeltaMapper.SHAPE_FIRST + i;
			shape_names[i + 1] = DeltaMapper.shapeName(code) + ((DeltaMapper.blockHeight(code) == 1) ? " (line)" : "");
		}
		block_menu.add(ViewerSupport.makeRadioDialog(frame, "Block Shape", shape_names, block_shape,
			v -> { block_shape = v; if(!updating) update(); }, shape_button));
		block_menu.addSeparator();
		JCheckBoxMenuItem shapes_item = new JCheckBoxMenuItem("Find Best Tries Rectangles", survey_shapes);
		shapes_item.addActionListener(e -> survey_shapes = shapes_item.isSelected());
		block_menu.add(shapes_item);
		JMenuItem find_item = new JMenuItem("Find Best");
		find_item.addActionListener(e -> runAnalysis());
		block_menu.add(find_item);

		menu_bar.add(file_menu); menu_bar.add(view.makeViewMenu()); menu_bar.add(quant_menu); menu_bar.add(block_menu);

		view.setImage(original_image);
		view.show();
		// The preview shows the original until the analysis has chosen the sets.
		SwingUtilities.invokeLater(this::runAnalysis);
	}

	// ---- Analysis ---------------------------------------------------------------

	// Runs init() in the background with the other menus disabled, then
	// shows the chosen settings in the controls and applies them.
	private void runAnalysis()
	{
		view.setMenusEnabled(false);
		view.setStatus("analysing...");
		new SwingWorker<Integer,Void>()
		{
			@Override protected Integer doInBackground() { return init(); }
			@Override protected void done()
			{
				try
				{
					int best = get();
					updating = true;
					if(best < DeltaMapper.SHAPE_FIRST) { block_spinner.setValue(best); block_size = best; block_shape = 0; }
					else block_shape = best - DeltaMapper.SHAPE_FIRST + 1;
					shape_button[block_shape].setSelected(true);
					updating = false;
				}
				catch(Exception e) { System.out.println("init: " + e); }
				view.setMenusEnabled(true);
				view.setStatus(null);
				apply();
			}
		}.execute();
	}

	// Picks the channel set with the smallest entropy estimate, then surveys
	// each channel's predictor set from the table at each block shape
	// (DeltaMapper.surveyBlockSets, kept in survey_sets). Returns the block
	// shape that codes smallest.
	public int init()
	{
		long    start = System.nanoTime();
		int[]   size  = DeltaMapper.getQuantizedSize(image_xdim, image_ydim, pixel_quant);
		int[][] qc    = quantizedChannels(size, new int[6], new int[6]);
		int[]   sum   = new int[6];
		ViewerSupport.parallel(6, i -> sum[i] = (int) Math.floor(CodeMapper.getShannonLimit(DeltaMapper.getIdealFrequency(qc[i], size[0], size[1]))));
		int set_id = bestSet(sum);
		int[] id = DeltaMapper.getChannels(set_id);
		int[][] ch = { qc[id[0]], qc[id[1]], qc[id[2]] };
		System.out.println("Channel set: " + DeltaMapper.SET_NAMES[set_id]);

		DeltaMapper.BlockSurvey survey = DeltaMapper.surveyBlockSets(ch, size[0], size[1], DeltaMapper.PREDICTOR_TABLE, survey_shapes, true);
		TreeMap<Integer, int[][]> sets = new TreeMap<Integer, int[][]>();
		for(Map.Entry<Integer, DeltaMapper.BlockSurvey> e : survey.tried.entrySet()) sets.put(e.getKey(), e.getValue().ids);
		survey_sets = sets;
		System.out.print(survey.report);
		System.out.print(DeltaMapper.describeGroupUse(survey, DeltaMapper.getBlockDeltasByCost(ch, size[0], size[1], survey.block, survey.ids, DeltaMapper.PREDICTOR_TABLE, true)));
		System.out.println(String.format("Surveyed sets (block %s): %,d bytes.", DeltaMapper.shapeName(survey.block), survey.bytes));
		System.out.println("Analysis took " + ViewerSupport.formatDuration(System.nanoTime() - start));
		System.out.println();
		return survey.block;
	}

	// After a change to the block settings: with no surveyed sets yet for
	// this shape, surveys them first (in the background, as the analysis
	// runs), so a shape picked by hand gets the sets that suit it; otherwise
	// just applies.
	private void update()
	{
		if(survey_sets != null && survey_sets.containsKey(block())) { apply(); return; }
		final int block = block();
		view.setMenusEnabled(false);
		view.setStatus("surveying block " + DeltaMapper.shapeName(block) + "...");
		new SwingWorker<int[][],Void>()
		{
			@Override protected int[][] doInBackground() { return surveyShape(block); }
			@Override protected void done()
			{
				try { surveyed().put(block, get()); }
				catch(Exception e) { System.out.println("survey: " + e); }
				view.setMenusEnabled(true);
				view.setStatus(null);
				apply();
			}
		}.execute();
	}

	// Each channel's surveyed predictor ids at one block shape, for the
	// current quantization and channel set (DeltaMapper.surveyAtBlockSize).
	public int[][] surveyShape(int block)
	{
		long    start = System.nanoTime();
		int[]   size  = DeltaMapper.getQuantizedSize(image_xdim, image_ydim, pixel_quant);
		int[][] qc    = quantizedChannels(size, new int[6], new int[6]);
		int[]   sum   = new int[6];
		ViewerSupport.parallel(6, i -> sum[i] = (int) Math.floor(CodeMapper.getShannonLimit(DeltaMapper.getIdealFrequency(qc[i], size[0], size[1]))));
		int[] id = DeltaMapper.getChannels(bestSet(sum));
		DeltaMapper.BlockSurvey survey = DeltaMapper.surveyAtBlockSize(new int[][] { qc[id[0]], qc[id[1]], qc[id[2]] }, size[0], size[1], block, DeltaMapper.PREDICTOR_TABLE, true);
		System.out.println(String.format("Surveyed sets for block %s: %,d bytes, %d, %d and %d predictors (took %s)", DeltaMapper.shapeName(block), survey.bytes,
			survey.ids[0].length, survey.ids[1].length, survey.ids[2].length, ViewerSupport.formatDuration(System.nanoTime() - start)));
		return survey.ids;
	}

	// survey_sets, made if there is none yet.
	private TreeMap<Integer, int[][]> surveyed()
	{
		if(survey_sets == null) survey_sets = new TreeMap<Integer, int[][]>();
		return survey_sets;
	}

	// The six candidate channels after quantizing (see
	// DeltaMapper.getCandidateChannels); fills min and init.
	private int[][] quantizedChannels(int[] size, int[] min, int[] init)
	{
		int[][] q = new int[3][];
		for(int i = 0; i < 3; i++)
		{
			int[] ch = source[i];
			if(pixel_quant != 0) ch = ResizeMapper.resize(ch, image_xdim, size[0], size[1]);
			q[i] = DeltaMapper.quantizeChannel(ch, pixel_shift);
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

	// ---- Apply --------------------------------------------------------------

	// Quantizes, picks the channel set, makes the block map deltas (what
	// Save codes), then rebuilds the image from them for the preview.
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
		int[][] qc = quantizedChannels(size, channel_min, channel_init);
		ViewerSupport.parallel(6, i -> channel_sum[i] = (int) Math.floor(CodeMapper.getShannonLimit(DeltaMapper.getIdealFrequency(qc[i], new_xdim, new_ydim))));
		min_set_id = bestSet(channel_sum);

		int[]   channel_id = DeltaMapper.getChannels(min_set_id);
		int[][] dc = new int[3][];
		// The surveyed sets, blocks assigned by estimated coded size as the
		// survey does (channels in order). With none yet for this shape (an
		// analysis that failed, or a coder used without one), they are
		// surveyed now.
		int[][] survey_ids = surveyed().get(block());
		if(survey_ids == null) { survey_ids = surveyShape(block()); surveyed().put(block(), survey_ids); }
		final ArrayList[] by_cost = DeltaMapper.getBlockDeltasByCost(new int[][] {qc[channel_id[0]], qc[channel_id[1]], qc[channel_id[2]]}, new_xdim, new_ydim, block(),
			survey_ids, DeltaMapper.PREDICTOR_TABLE, true);
		int[][] raw = new int[3][];   // decoded, before the minimum: the next channel's reference
		ViewerSupport.channels(true, 3, i ->
		{
			int j = channel_id[i];
			ArrayList result = by_cost[i];
			delta_list[i] = (int[]) result.get(1);
			map_list[i]   = (byte[]) result.get(2);
			dc[i] = DeltaMapper.getValuesFromDeltas(delta_list[i], new_xdim, new_ydim, channel_init[j], 13, map_list[i], 0, (i > 0) ? raw[i - 1] : null);
			raw[i] = dc[i].clone();
			value_list[i] = raw[i];
			if(j > 2) for(int k = 0; k < dc[i].length; k++) dc[i][k] += channel_min[j];
		});
		delta_xdim = new_xdim;

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
			System.out.println("Block " + DeltaMapper.shapeName(block()) + ", predictors " + DeltaMapper.describeMapSet(map_list[0]) + ", "
				+ DeltaMapper.describeMapSet(map_list[1]) + ", " + DeltaMapper.describeMapSet(map_list[2]) + ": " + file.length() + " bytes");
			System.out.println("Original compression rate: " + String.format("%.4f", (double) file_length / raw));
			System.out.println("Output  compression rate:  " + String.format("%.4f", (double) file.length() / raw));
			System.out.println();
		}

		// Header: FORMAT_ID, FORMAT_VERSION, width, height (unsigned
		// shorts), pixel_shift, pixel_quant, channel set. Then per channel:
		// int min, int init, the block map (DeltaMapper.writeMap, type 13;
		// it carries the block size and the channel's predictor ids), then the deltas,
		// mixed-context coded (DeltaMapper.packMixedDeltas). Channels 1 and 2
		// use the ones before them as context, so the reader decodes in order.
		private void save(DataOutputStream out) throws IOException
		{
			long t0 = System.nanoTime();
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
			System.out.println("Entropy coding took " + ViewerSupport.formatDuration(System.nanoTime() - t0));

			int[] channel_id = DeltaMapper.getChannels(min_set_id);
			out.writeByte(FORMAT_ID); out.writeByte(FORMAT_VERSION);
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
	}
}
