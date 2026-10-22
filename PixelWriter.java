import java.awt.*;
import java.awt.event.*;
import java.awt.image.*;
import java.io.*;
import java.util.*;
import javax.swing.*;

// PixelWriter version 1.3
//
// Codes the red, green and blue channels block by block with context-
// adaptive arithmetic coding (see PixelMapper). Menus: File, View,
// Quantization (Pixel Resolution and Color Resolution) and Block (Block
// Size; Coding: the pixel values with an exact prediction or with
// brightness bands as context, or the prediction errors; and Channels: red,
// green and blue, or green and the differences red - green and blue -
// green). At start-up, init() finds the block size that codes the image
// smallest; changing the coding or the channels finds it again. Save writes the file "foo", which PixelReader reads.
public class PixelWriter
{
	// File format: every file starts with FORMAT_ID ('X'; it was 'P' until
	// that turned out to be the Packet format's id too) and
	// FORMAT_VERSION. Bump FORMAT_VERSION in the writer and reader together
	// whenever the file layout or the coder's output changes.
	static final char FORMAT_ID      = 'X';
	static final int  FORMAT_VERSION = 3;   // 2 added the coding byte, 3 the channels byte

	// ---- Image state --------------------------------------------------------
	ViewerSupport view;
	BufferedImage original_image;
	BufferedImage working_image;
	String        filename;
	int           image_xdim, image_ydim;
	int[][]       source;   // red, green, blue of the original
	long          file_length;

	// ---- Compression parameters ---------------------------------------------
	int pixel_quant = 4;
	int pixel_shift = 3;
	int block_size  = 64;   // replaced by init()
	int coding      = PixelMapper.VALUES;
	int channels    = PixelMapper.PRIMARY;

	JSlider pquant_slider, pshift_slider, block_slider;
	JRadioButton[] coding_button  = new JRadioButton[PixelMapper.CODING_NAMES.length];
	JRadioButton[] channel_button = new JRadioButton[PixelMapper.CHANNEL_SET_NAMES.length];
	boolean updating = false;   // set while the program moves a slider

	// ---- Results of the last Apply (read by Save) ---------------------------
	int[][]  quantized;          // red, green, blue at the quantized size
	int      qxdim, qydim;
	byte[][] coded;              // per channel, in PixelMapper.ORDER
	boolean  applied = false;    // the last Apply finished; Save needs it

	public static void main(String[] args)
	{
		ViewerSupport.applyHiDpiFontScaleIfNeeded();
		if(args.length == 1) { new PixelWriter(args[0]); return; }
		FileDialog fd = new FileDialog((java.awt.Frame) null, "Open Image", FileDialog.LOAD);
		fd.setVisible(true);
		if(fd.getFile() != null) new PixelWriter(new File(fd.getDirectory(), fd.getFile()).getPath());
		else System.exit(0);
	}

	public PixelWriter(String _filename)
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

		view = new ViewerSupport("Pixel Writer  " + filename, image_xdim, image_ydim);
		JFrame   frame    = view.frame;
		JMenuBar menu_bar = frame.getJMenuBar();

		JMenu file_menu = new JMenu("File");
		JMenuItem open_item = new JMenuItem("Open...");
		open_item.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_O, InputEvent.CTRL_DOWN_MASK));
		open_item.addActionListener(e -> { FileDialog fd = new FileDialog(frame, "Open Image", FileDialog.LOAD); fd.setVisible(true); if(fd.getFile() != null) new PixelWriter(fd.getDirectory() + fd.getFile()); });
		file_menu.add(open_item); file_menu.addSeparator();
		JMenuItem reset_item = new JMenuItem("Reset");
		reset_item.addActionListener(e ->
		{
			pixel_quant = 0; pixel_shift = 0;
			updating = true; pquant_slider.setValue(0); pshift_slider.setValue(0); updating = false;
			apply();
		});
		file_menu.add(reset_item);
		JMenuItem save_item = new JMenuItem("Save"); save_item.addActionListener(e -> save()); file_menu.add(save_item);

		JSlider[] ss = new JSlider[1];
		JMenu quant_menu = new JMenu("Quantization");
		quant_menu.add(ViewerSupport.makeSliderDialog(frame, "Pixel Resolution", 0, 10, pixel_quant, v -> { pixel_quant = v; if(!updating) apply(); }, ss)); pquant_slider = ss[0];
		quant_menu.add(ViewerSupport.makeSliderDialog(frame, "Color Resolution", 0, 7, pixel_shift, v -> { pixel_shift = v; if(!updating) apply(); }, ss)); pshift_slider = ss[0];

		// Block size, coding and channels: the coded size changes, the picture
		// doesn't. A new coding or channel choice has its own best block size,
		// so choosing one runs the block size search again.
		JMenu block_menu = new JMenu("Block");
		block_menu.add(ViewerSupport.makeSliderDialog(frame, "Block Size", PixelMapper.BLOCK_MIN, PixelMapper.BLOCK_MAX, block_size,
			v -> { block_size = v; if(!updating) apply(); }, ss)); block_slider = ss[0];
		block_menu.add(ViewerSupport.makeRadioDialog(frame, "Coding", PixelMapper.CODING_NAMES, coding,
			v -> { if(v != coding) { coding = v; findBlockSize(); } }, coding_button));
		block_menu.add(ViewerSupport.makeRadioDialog(frame, "Channels", PixelMapper.CHANNEL_SET_NAMES, channels,
			v -> { if(v != channels) { channels = v; findBlockSize(); } }, channel_button));

		menu_bar.add(file_menu); menu_bar.add(view.makeViewMenu()); menu_bar.add(quant_menu); menu_bar.add(block_menu);

		view.setImage(original_image);
		view.show();
		SwingUtilities.invokeLater(() -> showInitialImage());
	}

	// Shows the default quantization, then finds the block size.
	private void showInitialImage()
	{
		quantize();
		showPreview();
		findBlockSize();
	}

	// Runs init() in the background with the menus disabled, then applies the
	// block size it found.
	private void findBlockSize()
	{
		quantize();
		view.setMenusEnabled(false);
		view.setStatus("finding the best block size...");
		new SwingWorker<Void,Void>()
		{
			@Override protected Void doInBackground() { init(); return null; }
			@Override protected void done()
			{
				try { get(); } catch(Exception e) { System.out.println("init: " + e); }
				view.setStatus(null);
				view.setMenusEnabled(true);
				updating = true; block_slider.setValue(block_size); updating = false;
				apply();
			}
		}.execute();
	}

	// Start-up: picks the block size that codes the image smallest at the
	// starting quantization (PixelMapper.findBestBlock) and prints the sizes tried.
	public void init()
	{
		long t0 = System.nanoTime();
		ArrayList<int[]> tried = new ArrayList<int[]>();
		block_size = PixelMapper.findBestBlock(quantized, qxdim, qydim, 255 >> pixel_shift, pixel_shift, coding, channels, tried);
		System.out.println("Block sizes tried (" + PixelMapper.CODING_NAMES[coding] + "; " + PixelMapper.CHANNEL_SET_NAMES[channels]
			+ "; Pixel Resolution " + pixel_quant + ", Color Resolution " + pixel_shift + "):");
		System.out.println("bytes for the 3 channels.");
		System.out.println("<= marks the size chosen.");
		for(int[] t : tried)
			System.out.println(String.format("  %4d  %,12d%s", t[0], t[1], (t[0] == block_size) ? "  <=" : ""));
		System.out.println("Block size search took " + ViewerSupport.formatDuration(System.nanoTime() - t0));
		System.out.println();
	}

	// ---- Apply --------------------------------------------------------------

	// Quantizes, codes (what Save writes) and shows the image the reader will show.
	void apply()
	{
		applied = false;
		try
		{
			quantize();
			long t0 = System.nanoTime();
			coded = PixelMapper.encode(quantized, qxdim, qydim, 255 >> pixel_shift, pixel_shift, block_size, coding, channels);
			long bytes = 0;
			for(byte[] c : coded) bytes += c.length;
			System.out.println(String.format("%s; %s; Pixel Resolution %d, Color Resolution %d, block size %d: %,d bytes coded (%.4f of the raw size) in %s",
				PixelMapper.CODING_NAMES[coding], PixelMapper.CHANNEL_SET_NAMES[channels], pixel_quant, pixel_shift, block_size, bytes, (double) bytes / (3.0 * image_xdim * image_ydim), ViewerSupport.formatDuration(System.nanoTime() - t0)));
			showPreview();
			applied = true;
		}
		catch(Exception e) { System.out.println("Apply: " + e); e.printStackTrace(); }
	}

	// The channels resized (Pixel Resolution) and shifted (Color Resolution).
	private void quantize()
	{
		int[] size = DeltaMapper.getQuantizedSize(image_xdim, image_ydim, pixel_quant);
		int[][] q = new int[3][];
		ViewerSupport.parallel(3, i ->
		{
			int[] ch = source[i];
			if(pixel_quant != 0)
			{
				ch = ResizeMapper.resize(ch, image_xdim, size[0], size[1]);
				for(int k = 0; k < ch.length; k++) ch[k] = PixelMapper.clamp(ch[k]);
			}
			q[i] = DeltaMapper.quantizeChannel(ch, pixel_shift);
		});
		quantized = q; qxdim = size[0]; qydim = size[1];
	}

	// The quantized image as PixelReader will show it.
	private void showPreview()
	{
		int[] rgb = PixelMapper.getRGB(quantized, qxdim, image_xdim, image_ydim, pixel_quant, pixel_shift);
		working_image.setRGB(0, 0, image_xdim, image_ydim, rgb, 0, image_xdim);
		view.setImage(working_image);
	}

	// ---- Save ---------------------------------------------------------------

	// Writes "foo": FORMAT_ID, FORMAT_VERSION, width, height (unsigned
	// shorts), pixel_shift, pixel_quant (bytes), block size (short), coding
	// and channels (bytes), then for each coded channel in order (green, then
	// red and blue or their differences from green): its coded length (int)
	// and coded bytes. Checks the channels decode back first.
	void save()
	{
		if(!applied) apply();
		if(!applied) { ViewerSupport.showError(view.frame, "Nothing saved: the last Apply failed."); return; }

		long t0 = System.nanoTime();
		int[][] back = PixelMapper.decode(coded, qxdim, qydim, 255 >> pixel_shift, pixel_shift, block_size, coding, channels);
		for(int i = 0; i < 3; i++)
			if(!Arrays.equals(back[i], quantized[i]))
			{
				ViewerSupport.showError(view.frame, "Nothing saved: the " + PixelMapper.CHANNEL_NAMES[i] + " channel does not decode back.");
				return;
			}
		System.out.println("Decode check took " + ViewerSupport.formatDuration(System.nanoTime() - t0));

		File file = new File("foo");
		try(DataOutputStream out = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(file))))
		{
			out.writeByte(FORMAT_ID); out.writeByte(FORMAT_VERSION);
			out.writeShort(image_xdim); out.writeShort(image_ydim);
			out.writeByte(pixel_shift); out.writeByte(pixel_quant);
			out.writeShort(block_size);
			out.writeByte(coding);
			out.writeByte(channels);
			for(int i = 0; i < 3; i++) { out.writeInt(coded[i].length); out.write(coded[i]); }
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
}
