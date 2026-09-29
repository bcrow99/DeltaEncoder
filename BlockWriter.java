import java.awt.*;
import java.awt.event.*;
import java.awt.image.*;
import java.io.*;
import java.util.*;
import javax.swing.*;

// BlockWriter version 1.0
//
// A writer with one delta type and one entropy coder: the block map (delta
// type 13) with Context-coded deltas (DeltaMapper.packContextDeltas) and a
// context-coded map. Its startup analysis picks the channel set, then the
// block size and predictor set by coding with each candidate and keeping
// the smallest -- actual sizes, not estimates. Files are read by
// BlockReader.
public class BlockWriter
{
	// File format: every file starts with FORMAT_ID ('B' = Block format) and
	// FORMAT_VERSION. Bump FORMAT_VERSION in the writer and reader together
	// whenever the file layout or a coder's output changes.
	static final char FORMAT_ID      = 'B';
	static final int  FORMAT_VERSION = 1;

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
	int block_size  = DeltaMapper.BLOCK_DEFAULT;
	int block_set   = 0;

	JSlider        pquant_slider, pshift_slider, corr_slider;
	JSpinner       block_spinner;
	JRadioButton[] set_button = new JRadioButton[DeltaMapper.BLOCK_SET_NAMES.length];

	// ---- Results of the last Apply (read by Save) ---------------------------
	int[]    set_sum = new int[10], channel_sum = new int[6];
	int[]    channel_init = new int[6], channel_min = new int[6];
	int[][]  delta_list = new int[3][];
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
		// (worth doing after changing the quantization).
		JMenu block_menu = new JMenu("Block");
		JSpinner[] sp = new JSpinner[1];
		block_menu.add(ViewerSupport.makeSpinnerDialog(frame, "Block Size", DeltaMapper.BLOCK_MIN, DeltaMapper.BLOCK_MAX, block_size,
			v -> { block_size = v; if(!updating) apply(); }, sp)); block_spinner = sp[0];
		block_menu.add(ViewerSupport.makeRadioDialog(frame, "Block Predictors", DeltaMapper.BLOCK_SET_NAMES, block_set,
			v -> { block_set = v; if(!updating) apply(); }, set_button));
		block_menu.addSeparator();
		JMenuItem find_item = new JMenuItem("Find Best");
		find_item.addActionListener(e -> runAnalysis());
		block_menu.add(find_item);

		menu_bar.add(file_menu); menu_bar.add(view.makeViewMenu()); menu_bar.add(quant_menu); menu_bar.add(block_menu);

		view.setImage(original_image);
		view.show();
		SwingUtilities.invokeLater(() -> { apply(); runAnalysis(); });
	}

	// ---- Analysis ---------------------------------------------------------------

	// Runs init() in the background with the other menus disabled, then
	// shows the chosen settings in the controls and applies them.
	private void runAnalysis()
	{
		view.setMenusEnabled(false);
		view.setStatus("analysing…");
		new SwingWorker<int[],Void>()
		{
			@Override protected int[] doInBackground() { return init(); }
			@Override protected void done()
			{
				try
				{
					int[] best = get();
					updating = true;
					block_spinner.setValue(best[0]);
					set_button[best[1]].setSelected(true);
					block_size = best[0]; block_set = best[1];
					updating = false;
				}
				catch(Exception e) { System.out.println("init: " + e); }
				view.setMenusEnabled(true);
				view.setStatus(null);
				apply();
			}
		}.execute();
	}

	// Picks the channel set with the smallest entropy estimate, then the
	// block size and predictor set that code smallest (see
	// DeltaMapper.findBestBlock). Returns {block size, predictor set}.
	public int[] init()
	{
		long    start = System.nanoTime();
		int[]   size  = DeltaMapper.getQuantizedSize(image_xdim, image_ydim, pixel_quant);
		int[][] qc    = quantizedChannels(size, new int[6], new int[6]);
		int[]   sum   = new int[6];
		ViewerSupport.parallel(6, i -> sum[i] = (int) Math.floor(CodeMapper.getShannonLimit(DeltaMapper.getIdealFrequency(qc[i], size[0], size[1]))));
		int set_id = bestSet(sum);
		int[] id = DeltaMapper.getChannels(set_id);
		int[][] ch = { qc[id[0]], qc[id[1]], qc[id[2]] };

		long[][] bytes = new long[DeltaMapper.BLOCK_SET_NAMES.length][DeltaMapper.BLOCK_SEARCH_SIZES.length];
		int[]    best  = DeltaMapper.findBestBlock(ch, size[0], size[1], bytes);
		System.out.println("Channel set: " + DeltaMapper.SET_NAMES[set_id]);
		System.out.print(DeltaMapper.getBlockTable(bytes, best));
		System.out.println("Analysis took " + ViewerSupport.formatDuration(System.nanoTime() - start));
		System.out.println();
		return best;
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
		ViewerSupport.parallel(3, i ->
		{
			int j = channel_id[i];
			ArrayList result = DeltaMapper.getDeltas(qc[j], new_xdim, new_ydim, 13, 0, block_size, block_set);
			delta_list[i] = (int[]) result.get(1);
			map_list[i]   = (byte[]) result.get(2);
			dc[i] = DeltaMapper.getValuesFromDeltas(delta_list[i], new_xdim, new_ydim, channel_init[j], 13, map_list[i], 0);
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
			System.out.println("Block size " + block_size + ", " + DeltaMapper.BLOCK_SET_NAMES[block_set] + ": " + file.length() + " bytes");
			System.out.println("Original compression rate: " + String.format("%.4f", (double) file_length / raw));
			System.out.println("Output  compression rate:  " + String.format("%.4f", (double) file.length() / raw));
			System.out.println();
		}

		// Header: FORMAT_ID, FORMAT_VERSION, width, height (unsigned
		// shorts), pixel_shift, pixel_quant, channel set. Then per channel:
		// int min, int init, the block map (DeltaMapper.writeMap, type 13;
		// it carries the block size and predictor set), then the deltas
		// (DeltaMapper.packContextDeltas). Channels 1 and 2 use the ones
		// before them as context, so the reader decodes in order.
		private void save(DataOutputStream out) throws IOException
		{
			long t0 = System.nanoTime();
			byte[][] coded = new byte[3][];
			ViewerSupport.parallel(3, i ->
			{
				try
				{
					int[][] previous = Arrays.copyOf(delta_list, i);
					coded[i] = DeltaMapper.packContextDeltas(delta_list[i], previous, delta_xdim);
					int[] back = DeltaMapper.readContextDeltas(new DataInputStream(new ByteArrayInputStream(coded[i])), delta_list[i].length, previous, delta_xdim);
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
