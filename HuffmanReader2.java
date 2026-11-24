import java.awt.image.*;
import java.io.*;
import javax.swing.*;

// HuffmanReader2 version 1.0 (reads Neighbour Huffman format 1 only)
//
// Reads HuffmanWriter2's files: HuffmanReader with the neighbour Huffman
// decoding (NeighbourMapper.neighbourDecode) in place of the unary strings,
// and either delta type. With the LS deltas, each band's channels' deltas
// are decoded, all bands and channels at the same time, then each band's
// channels are rebuilt from them with the least-squares fit from the rows
// above (DeltaMapper.getValuesFromRowFitDeltas: the channels at the same
// time, each a row behind the one before; ViewerSupport.lsBandsAtOnce
// bands at a time); with the average deltas, every band's channels are
// decoded and rebuilt, each on its own, all at the same time. Then blue,
// green and red are rebuilt, resized back and packed as HuffmanReader does.
// See HuffmanWriter2.encode and NeighbourMapper.neighbourCode for the layout.
/**
 * Reads HuffmanWriter2's files (see the comment above).
 */
public class HuffmanReader2
{
	/**
	 * Every file starts with FORMAT_ID ('N' = Neighbour Huffman format) and
	 * FORMAT_VERSION.
	 */
	static final char FORMAT_ID      = HuffmanWriter2.FORMAT_ID;
	/**
	 * The file format's version (HuffmanWriter2.FORMAT_VERSION); the ones
	 * before, from NeighbourMapper.VERSION_UNFLIPPED, are read too.
	 */
	static final int  FORMAT_VERSION = HuffmanWriter2.FORMAT_VERSION;

	/**
	 * The image's width and height, Color Resolution and Pixel Resolution.
	 */
	int xdim, ydim, pixel_shift, pixel_quant;
	/**
	 * The deltas: HuffmanWriter2.DELTA_LS or DELTA_AVERAGE.
	 */
	int delta_type;
	/**
	 * The file's format version, which is its coding's
	 * (NeighbourMapper.VERSION, VERSION_FLIPPED or VERSION_UNFLIPPED).
	 */
	int version;
	/**
	 * Each channel's minimum (added back to the difference channels), in
	 * coding order.
	 */
	int[] min = new int[3];
	/**
	 * The number of bands.
	 */
	int bands;
	/**
	 * Each band's length in bytes.
	 */
	int[] length;
	/**
	 * How long the last decodeFile took to read the file (while decoding).
	 */
	long read_time;

	/**
	 * The decoded image.
	 */
	BufferedImage decoded_image = null;
	/**
	 * The window.
	 */
	ViewerSupport view;

	/**
	 * Reads and shows a file.
	 *
	 * @param args the file
	 */
	public static void main(String[] args)
	{
		ViewerSupport.applyHiDpiFontScaleIfNeeded();
		if(args.length != 1) { System.out.println("Usage: java HuffmanReader2 <filename>"); System.exit(0); }
		new HuffmanReader2(args[0]);
	}

	/**
	 * Reads a file's header, opens its window, then reads and decodes the
	 * file (twice, printing both times: the first includes compiling the
	 * code). Reading and decoding overlap: each channel of each band starts
	 * decoding as soon as the start of it has been read, and decodes its
	 * coded bytes as they arrive (decode).
	 *
	 * @param filename the file
	 */
	public HuffmanReader2(String filename)
	{
		try
		{
			try(DataInputStream in = new DataInputStream(new BufferedInputStream(new FileInputStream(filename))))
			{
				readHeader(in, filename);
			}
			System.out.println("Image:        " + xdim + " x " + ydim + ", " + HuffmanWriter2.DELTA_NAMES[delta_type] + " deltas");
			if(bands > 1) System.out.println(SimpleWriter.describeBands(bands, DeltaMapper.getQuantizedSize(xdim, ydim, pixel_quant)[1]));
			System.out.println();

			ViewerSupport.runOnEdt(() ->
			{
				view = new ViewerSupport("Huffman Reader 2  " + filename, xdim, ydim);
				view.frame.getJMenuBar().add(view.makeViewMenu());
				view.setStatus("decoding...");
				view.show();
			});

			// Read and decoded twice (ViewerSupport.printRunTimes: the first
			// decoding in this Java process includes the JIT's warm-up, so the
			// times are printed apart, the second as the run time). The image
			// shows after the first.
			long start = System.nanoTime(), all = start;
			decoded_image = decodeFile(filename);
			long first = System.nanoTime() - start, first_read = read_time;
			SwingUtilities.invokeLater(() -> { view.setImage(decoded_image); view.setStatus(null); view.fitAndShrink(); });
			start = System.nanoTime();
			BufferedImage again = decodeFile(filename);
			long second = System.nanoTime() - start, both = System.nanoTime() - all;
			int[] p = decoded_image.getRGB(0, 0, xdim, ydim, null, 0, xdim), q = again.getRGB(0, 0, xdim, ydim, null, 0, xdim);
			if(!java.util.Arrays.equals(p, q)) System.out.println("WARNING: the second decoding differs from the first.");
			System.out.println("Image rebuilt (" + xdim + " x " + ydim + "); the file read in " + ViewerSupport.formatDuration(first_read) + ", then "
				+ ViewerSupport.formatDuration(read_time) + ".");
			ViewerSupport.printRunTimes("HuffmanReader2", first, second, both);
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
	 * Reads the header: the image's size, the quantization, the channels'
	 * minimums, the number of bands and their lengths.
	 *
	 * @param in       the file
	 * @param filename its name, for messages
	 * @throws IOException if it can't be read or isn't a Neighbour Huffman file
	 */
	private void readHeader(DataInputStream in, String filename) throws IOException
	{
		int id = in.readUnsignedByte();
		version = in.readUnsignedByte();
		if(id != FORMAT_ID || version < NeighbourMapper.VERSION_UNFLIPPED || version > FORMAT_VERSION)
			throw new IOException(id != FORMAT_ID
				? filename + " is not a Neighbour Huffman file."
				: filename + " is Neighbour Huffman format version " + version + "; this reader reads versions "
				  + NeighbourMapper.VERSION_UNFLIPPED + " to " + FORMAT_VERSION + " only.");
		xdim        = in.readUnsignedShort();
		ydim        = in.readUnsignedShort();
		pixel_shift = in.readByte();
		pixel_quant = in.readByte();
		delta_type  = in.readUnsignedByte();
		if(delta_type != HuffmanWriter2.DELTA_LS && delta_type != HuffmanWriter2.DELTA_AVERAGE) throw new IOException(filename + ": unknown delta type " + delta_type + ".");
		for(int i = 0; i < 3; i++) min[i] = in.readInt();
		int h = DeltaMapper.getQuantizedSize(xdim, ydim, pixel_quant)[1];
		bands = in.readUnsignedByte();
		if(bands < 1 || h / bands < 2) throw new IOException(filename + ": " + bands + " bands of a " + h + "-row image.");
		length = new int[bands];
		for(int b = 0; b < bands; b++) length[b] = in.readInt();
	}

	/**
	 * Reads the file and decodes it, in stages (decode).
	 *
	 * @param filename the file
	 * @return the image
	 * @throws IOException if it can't be read
	 */
	private BufferedImage decodeFile(String filename) throws IOException
	{
		try(InputStream in = new FileInputStream(filename)) { return decode(in, filename); }
	}

	/**
	 * The read buffer's size.
	 */
	static final int CHUNK = 1 << 16;

	/**
	 * Reads a Neighbour Huffman file from a stream and decodes it: reads it
	 * all, then (LS deltas) decodes every band's channels' deltas at the same
	 * time (NeighbourMapper.neighbourDecode) and rebuilds each band's channels
	 * from them (DeltaMapper.getValuesFromRowFitDeltas),
	 * ViewerSupport.lsBandsAtOnce bands at a time, or (average deltas)
	 * decodes and rebuilds every band's channels at the same time; then blue,
	 * green and red are rebuilt, resized and shifted.
	 * Leaves the time the reading took in read_time.
	 *
	 * @param raw  the stream (from the start of the file)
	 * @param name its name, for messages
	 * @return the image
	 * @throws IOException if it can't be read, or isn't the file the header was read from
	 */
	private BufferedImage decode(InputStream raw, String name) throws IOException
	{
		int[]   size = DeltaMapper.getQuantizedSize(xdim, ydim, pixel_quant);
		int     w = size[0], h = size[1];
		int[]   radius = {HuffmanWriter2.RADIUS, HuffmanWriter2.RADIUS, HuffmanWriter2.RADIUS};
		int[][] channel = new int[3][w * h];
		int[][] init = new int[bands][3];
		NeighbourMapper.NeighbourChannel[][] coded = new NeighbourMapper.NeighbourChannel[bands][3];
		long start = System.nanoTime();
		DataInputStream in = new DataInputStream(new BufferedInputStream(raw, CHUNK));
		int xd = xdim, yd = ydim, nb = bands, dt = delta_type;
		int[] len = length;
		readHeader(in, name);
		if(xdim != xd || ydim != yd || bands != nb || delta_type != dt || !java.util.Arrays.equals(length, len)) throw new IOException(name + " changed while it was read.");
		for(int b = 0; b < bands; b++)
		{
			byte[] band = new byte[length[b]];
			in.readFully(band);
			DataInputStream bi = new DataInputStream(new ByteArrayInputStream(band));
			// Each channel: init, then neighbourCode's bytes.
			for(int i = 0; i < 3; i++)
			{
				init[b][i] = bi.readInt();
				coded[b][i] = NeighbourMapper.neighbourRead(bi, version);
			}
			if(bi.available() != 0) throw new IOException(name + ": band " + b + " is not the length its header says.");
		}
		read_time = System.nanoTime() - start;

		// Every band's channels decoded and rebuilt (GenericReader.neighbourBands).
		GenericReader.neighbourBands(coded, init, w, h, delta_type == HuffmanWriter2.DELTA_AVERAGE, radius, HuffmanWriter2.EVERY, channel);

		// The minimums are added back to the difference channels, then blue,
		// green and red rebuilt, resized back and packed.
		for(int i = 0; i < 3; i++) if(HuffmanWriter2.CHANNELS[i] > 2) for(int k = 0; k < channel[i].length; k++) channel[i][k] += min[i];
		int[][] bgr = DeltaMapper.getBlueGreenRed(HuffmanWriter2.SET_ID, channel[0], channel[1], channel[2]);
		if(pixel_quant != 0) ViewerSupport.parallel(3, c -> bgr[c] = ResizeMapper.resize2(bgr[c], w, xdim, ydim));
		BufferedImage image = ViewerSupport.makeImage(bgr[0], bgr[1], bgr[2], xdim, ydim, pixel_shift);
		return image;
	}
}
