import java.awt.image.*;
import java.io.*;
import javax.swing.*;

// HuffmanReader version 1.0 (reads Huffman format 1 only)
//
// Reads HuffmanWriter's files: SimpleReader2 with the Huffman decoding in
// place of the mixed context decoding. Each band's channels' deltas are
// decoded (CodeMapper's regular Huffman code, then StringMapper's unary
// strings), all bands and channels at the same time; then each band's
// channels are rebuilt from them with the least-squares fit from the rows
// above (DeltaMapper.getValuesFromRowFitDeltas: the channels at the same
// time, each a row behind the one before; the bands at the same time), and
// blue, green and red rebuilt, resized back and packed as SimpleReader2 does.
// See HuffmanWriter.encode and huffmanCode for the layout.
/**
 * Reads HuffmanWriter's files (see the comment above).
 */
public class HuffmanReader
{
	/**
	 * Every file starts with FORMAT_ID ('H' = Huffman format) and
	 * FORMAT_VERSION.
	 */
	static final char FORMAT_ID      = HuffmanWriter.FORMAT_ID;
	/**
	 * The file format's version: bump it in the writer and reader together
	 * whenever the file layout or a coder's output changes. 2: the fit from 6
	 * neighbours, solved at every 4th pixel (1: 8 neighbours, every 3rd).
	 */
	static final int  FORMAT_VERSION = HuffmanWriter.FORMAT_VERSION;

	/**
	 * The image's width and height, Color Resolution and Pixel Resolution.
	 */
	int xdim, ydim, pixel_shift, pixel_quant;
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
		if(args.length != 1) { System.out.println("Usage: java HuffmanReader <filename>"); System.exit(0); }
		new HuffmanReader(args[0]);
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
	public HuffmanReader(String filename)
	{
		try
		{
			try(DataInputStream in = new DataInputStream(new BufferedInputStream(new FileInputStream(filename))))
			{
				readHeader(in, filename);
			}
			System.out.println("Image:        " + xdim + " x " + ydim);
			if(bands > 1) System.out.println(SimpleWriter.describeBands(bands, DeltaMapper.getQuantizedSize(xdim, ydim, pixel_quant)[1]));
			System.out.println();

			ViewerSupport.runOnEdt(() ->
			{
				view = new ViewerSupport("Huffman Reader  " + filename, xdim, ydim);
				view.frame.getJMenuBar().add(view.makeViewMenu());
				view.setStatus("decoding...");
				view.show();
			});

			// Read and decoded twice, the times of both printed: the first
			// includes compiling the code (the first time it runs in this
			// JVM), the second is the run time only. The image shows after
			// the first.
			long start = System.nanoTime();
			decoded_image = decodeFile(filename);
			long first = System.nanoTime() - start, first_read = read_time;
			SwingUtilities.invokeLater(() -> { view.setImage(decoded_image); view.setStatus(null); view.fitAndShrink(); });
			start = System.nanoTime();
			BufferedImage again = decodeFile(filename);
			long second = System.nanoTime() - start;
			int[] p = decoded_image.getRGB(0, 0, xdim, ydim, null, 0, xdim), q = again.getRGB(0, 0, xdim, ydim, null, 0, xdim);
			if(!java.util.Arrays.equals(p, q)) System.out.println("WARNING: the second decoding differs from the first.");
			System.out.println("Image rebuilt (" + xdim + " x " + ydim + "); the file read in " + ViewerSupport.formatDuration(first_read) + ", then "
				+ ViewerSupport.formatDuration(read_time) + ".");
			System.out.println("Run time plus compile time: " + ViewerSupport.formatDuration(first));
			System.out.println("Run time only:              " + ViewerSupport.formatDuration(second));
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
	 * @throws IOException if it can't be read or isn't a Huffman file
	 */
	private void readHeader(DataInputStream in, String filename) throws IOException
	{
		int id = in.readUnsignedByte(), version = in.readUnsignedByte();
		if(id != FORMAT_ID || version != FORMAT_VERSION)
			throw new IOException(id != FORMAT_ID
				? filename + " is not a Huffman file."
				: filename + " is Huffman format version " + version + "; this reader reads version " + FORMAT_VERSION + " only.");
		xdim        = in.readUnsignedShort();
		ydim        = in.readUnsignedShort();
		pixel_shift = in.readByte();
		pixel_quant = in.readByte();
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
	 * Reads a Huffman file from a stream and decodes it: reads it all, then
	 * decodes every band's channels' deltas at the same time
	 * (HuffmanWriter.huffmanDecode), then rebuilds each band's channels from
	 * them (DeltaMapper.getValuesFromRowFitDeltas), the bands at the same
	 * time; then blue, green and red are rebuilt, resized and shifted.
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
		int[]   radius = {HuffmanWriter.RADIUS, HuffmanWriter.RADIUS, HuffmanWriter.RADIUS};
		int[][] channel = new int[3][w * h];
		int[][] init = new int[bands][3];
		HuffmanWriter.HuffmanChannel[][] coded = new HuffmanWriter.HuffmanChannel[bands][3];
		long start = System.nanoTime();
		DataInputStream in = new DataInputStream(new BufferedInputStream(raw, CHUNK));
		int xd = xdim, yd = ydim, nb = bands;
		int[] len = length;
		readHeader(in, name);
		if(xdim != xd || ydim != yd || bands != nb || !java.util.Arrays.equals(length, len)) throw new IOException(name + " changed while it was read.");
		for(int b = 0; b < bands; b++)
		{
			byte[] band = new byte[length[b]];
			in.readFully(band);
			DataInputStream bi = new DataInputStream(new ByteArrayInputStream(band));
			// Each channel: init, then huffmanCode's bytes.
			for(int i = 0; i < 3; i++)
			{
				init[b][i] = bi.readInt();
				coded[b][i] = HuffmanWriter.huffmanRead(bi);
			}
			if(bi.available() != 0) throw new IOException(name + ": band " + b + " is not the length its header says.");
		}
		read_time = System.nanoTime() - start;

		// Every band's channels' deltas at the same time, then each band's
		// channels rebuilt, the bands at the same time.
		int[][][] delta = new int[bands][3][];
		ViewerSupport.parallel(3 * bands, j ->
		{
			int b = j / 3, i = j % 3;
			int y0 = SimpleWriter.bandStart(b, bands, h), y1 = SimpleWriter.bandStart(b + 1, bands, h);
			delta[b][i] = HuffmanWriter.huffmanDecode(coded[b][i], w * (y1 - y0));
		});
		ViewerSupport.parallel(bands, b ->
		{
			int y0 = SimpleWriter.bandStart(b, bands, h), y1 = SimpleWriter.bandStart(b + 1, bands, h);
			int[][] v = DeltaMapper.getValuesFromRowFitDeltas(delta[b], w, y1 - y0, init[b], radius, HuffmanWriter.EVERY);
			for(int i = 0; i < 3; i++) System.arraycopy(v[i], 0, channel[i], y0 * w, v[i].length);
		});

		// The minimums are added back to the difference channels, then blue,
		// green and red rebuilt, resized back and packed.
		for(int i = 0; i < 3; i++) if(HuffmanWriter.CHANNELS[i] > 2) for(int k = 0; k < channel[i].length; k++) channel[i][k] += min[i];
		int[][] bgr = DeltaMapper.getBlueGreenRed(HuffmanWriter.SET_ID, channel[0], channel[1], channel[2]);
		if(pixel_quant != 0) ViewerSupport.parallel(3, c -> bgr[c] = ResizeMapper.resize2(bgr[c], w, xdim, ydim));
		BufferedImage image = new BufferedImage(xdim, ydim, BufferedImage.TYPE_INT_RGB);
		image.setRGB(0, 0, xdim, ydim, DeltaMapper.getPixel(bgr[0], bgr[1], bgr[2], xdim, pixel_shift), 0, xdim);
		return image;
	}
}
