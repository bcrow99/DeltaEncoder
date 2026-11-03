import java.awt.image.*;
import java.io.*;
import javax.swing.*;

// PixelReader version 1.5
//
// Reads the Pixel format files PixelWriter saves (versions 1 to 5) and
// shows the image. Version 4 adds Average: the coded image is the averaged
// top level, expanded with the sign bits (PixelMapper.expandPyramid).
// Version 5 adds the prediction (MED or least squares), each coded
// channel's offset and largest value (the channel set green, blue - green,
// red - green), and resizes back centred (ResizeMapper.resize2).
public class PixelReader
{
	// File format: every file starts with FORMAT_ID ('X'; it was 'P' until
	// that turned out to be the Packet format's id too) and
	// FORMAT_VERSION. Bump FORMAT_VERSION in the writer and reader together
	// whenever the file layout or the coder's output changes.
	static final char FORMAT_ID      = 'X';
	static final int  FORMAT_VERSION = 5;   // 2 added the coding byte, 3 the channels byte, 4 the averaging pyramid, 5 the prediction, offsets and largest values, and centred resizing; older files are read with the defaults

	/**
	 * The header: the format version, the image's size, the quantization, the
	 * block size, the coding, the channel set and the Average levels.
	 */
	int      version, xdim, ydim, pixel_shift, pixel_quant, block_size, coding, channels, pixel_pyramid;
	/**
	 * The prediction and the least-squares window radius (MED before format version 5).
	 */
	int      prediction = PixelMapper.MED, radius = PixelMapper.LS_RADIUS;
	/**
	 * Each coded channel's offset and largest value.
	 */
	int[]    offset, max;
	boolean  use_saddle;
	boolean[][][] sign_bits = new boolean[3][][];   // per channel (red, green, blue), per level
	byte[][] coded = new byte[3][];   // per coded channel, in coding order

	BufferedImage decoded_image = null;
	ViewerSupport view;

	public static void main(String[] args)
	{
		ViewerSupport.applyHiDpiFontScaleIfNeeded();
		if(args.length != 1) { System.out.println("Usage: java PixelReader <filename>"); System.exit(0); }
		new PixelReader(args[0]);
	}

	/**
	 * A reader without a window, for tools: read() a file, then decodeRGB().
	 */
	PixelReader() {}

	public PixelReader(String filename)
	{
		try
		{
			long start = System.nanoTime();
			try(DataInputStream in = new DataInputStream(new BufferedInputStream(new FileInputStream(filename))))
			{
				read(in, filename);
			}
			System.out.println("File read in " + ((System.nanoTime() - start) / 1_000_000) + " ms.");

			ViewerSupport.runOnEdt(() ->
			{
				view = new ViewerSupport("Pixel Reader  " + filename, xdim, ydim);
				view.frame.getJMenuBar().add(view.makeViewMenu());
				view.setStatus("decoding...");
				view.show();
			});

			int[] rgb = decodeRGB();
			decoded_image = new BufferedImage(xdim, ydim, BufferedImage.TYPE_INT_RGB);
			decoded_image.setRGB(0, 0, xdim, ydim, rgb, 0, xdim);
			SwingUtilities.invokeLater(() -> { view.setImage(decoded_image); view.setStatus(null); view.fitAndShrink(); });
		}
		catch(Exception e)
		{
			String message = (e instanceof IOException && e.getMessage() != null) ? e.getMessage() : "Can't decode " + filename + ": " + e;
			if(view != null) SwingUtilities.invokeLater(() -> view.setStatus("decode failed"));
			ViewerSupport.showError(view != null ? view.frame : null, message);
			ViewerSupport.exitIfNoWindows();
		}
	}

	/**
	 * Decodes the channels read, expands them (Average) and assembles the
	 * image's pixels.
	 *
	 * @return the pixels, 0xRRGGBB, row by row
	 */
	int[] decodeRGB()
	{
		long start = System.nanoTime();
		int[]   size    = DeltaMapper.getQuantizedSize(xdim, ydim, pixel_quant);
		int[]   top     = PixelMapper.pyramidSize(size[0], size[1], pixel_pyramid);
		int[][] channel = PixelMapper.decode(coded, top[0], top[1], pixel_shift, block_size, coding, channels, prediction, radius, offset, max);
		if(pixel_pyramid > 0)
			ViewerSupport.parallel(3, i -> channel[i] = PixelMapper.expandPyramid(channel[i], size[0], size[1], sign_bits[i], 255 >> pixel_shift, use_saddle));
		System.out.println("Channels decoded in " + ((System.nanoTime() - start) / 1_000_000) + " ms.");

		start = System.nanoTime();
		int[] rgb = PixelMapper.getRGB(channel, size[0], xdim, ydim, pixel_quant, pixel_shift, version >= 5);
		System.out.println("RGB assembled in " + ((System.nanoTime() - start) / 1_000_000) + " ms.");
		return rgb;
	}

	/**
	 * Reads a file's header and coded channels.
	 *
	 * @param in       the file
	 * @param filename its name, for messages
	 * @throws IOException if it can't be read or isn't a Pixel file this reader reads
	 */
	void read(DataInputStream in, String filename) throws IOException
	{
		int id = in.readUnsignedByte();
		version = in.readUnsignedByte();
		if(id == 'P')
			throw new IOException(filename + " starts with 'P': a Packet Writer file (open it with PacketReader), or a Pixel Writer file"
				+ " saved before the Pixel format's id changed from 'P' to 'X' (save the image again with PixelWriter).");
		if(id != FORMAT_ID || version < 1 || version > FORMAT_VERSION)
			throw new IOException(id != FORMAT_ID
				? filename + " is not a Pixel Writer file."
				: filename + " is Pixel format version " + version + "; this reader reads versions 1 to " + FORMAT_VERSION + ".");
		xdim        = in.readUnsignedShort();
		ydim        = in.readUnsignedShort();
		pixel_shift = in.readByte();
		pixel_quant = in.readByte();
		block_size  = in.readUnsignedShort();
		coding      = (version >= 2) ? in.readByte() : PixelMapper.VALUES;
		channels    = (version >= 3) ? in.readByte() : PixelMapper.PRIMARY;
		if(version >= 5) { prediction = in.readByte(); radius = in.readUnsignedByte(); }
		pixel_pyramid = (version >= 4) ? in.readByte() : 0;
		use_saddle    = (version >= 4) && in.readByte() != 0;
		if(pixel_shift < 0 || pixel_shift > 7 || pixel_quant < 0 || pixel_quant > 10 || block_size < 1
			|| coding < 0 || coding >= PixelMapper.CODING_NAMES.length || channels < 0 || channels >= PixelMapper.CHANNEL_SET_NAMES.length
			|| (version < 5 && channels == PixelMapper.GREEN_DIFFERENCES)
			|| prediction < 0 || prediction >= PixelMapper.PREDICTION_NAMES.length || radius < 1
			|| pixel_pyramid < 0 || pixel_pyramid > PixelMapper.PYRAMID_MAX)
			throw new IOException(filename + ": bad header (Color Resolution " + pixel_shift + ", Pixel Resolution " + pixel_quant
				+ ", block size " + block_size + ", coding " + coding + ", channels " + channels + ", prediction " + prediction + ").");
		int max_value = 255 >> pixel_shift;
		if(version >= 5)
		{
			offset = new int[3]; max = new int[3];
			for(int i = 0; i < 3; i++)
			{
				offset[i] = in.readInt(); max[i] = in.readInt();
				if(max[i] < 0 || max[i] > 2 * max_value) throw new IOException(filename + ": bad header (channel " + i + "'s largest value " + max[i] + ").");
			}
		}
		else
		{
			offset = (channels == PixelMapper.DIFFERENCE) ? new int[] {0, -max_value, -max_value} : new int[3];
			max    = PixelMapper.channelMax(max_value, channels);
		}
		for(int i = 0; i < 3; i++)
		{
			coded[i] = new byte[in.readInt()];
			in.readFully(coded[i]);
		}
		if(pixel_pyramid > 0) for(int i = 0; i < 3; i++) sign_bits[i] = PixelMapper.readSignBits(in, pixel_pyramid);

		System.out.println("Image:            " + xdim + " x " + ydim);
		System.out.println("Pixel Resolution: " + pixel_quant);
		System.out.println("Color Resolution: " + pixel_shift);
		System.out.println("Block size:       " + block_size);
		System.out.println("Coding:           " + PixelMapper.CODING_NAMES[coding]);
		System.out.println("Channels:         " + PixelMapper.CHANNEL_SET_NAMES[channels]);
		System.out.println("Prediction:       " + PixelMapper.PREDICTION_NAMES[prediction] + ((prediction == PixelMapper.LS) ? " (window radius " + radius + ")" : ""));
		System.out.println("Average:          " + pixel_pyramid + ((pixel_pyramid > 0 && use_saddle) ? " (saddle)" : ""));
		System.out.println();
	}
}
