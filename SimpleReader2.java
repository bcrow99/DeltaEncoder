import java.awt.image.*;
import java.io.*;
import javax.swing.*;

// SimpleReader2 version 1.0 (reads Simple 2 format 1)
//
// Reads SimpleWriter2's files: the channels green, blue-green and red-green
// (SimpleWriter2.SET_ID), every interior pixel predicted by the
// least-squares fit from the rows above (DeltaMapper.RowFit, the wide clamp,
// window radius SimpleWriter2.RADIUS, solved at every
// SimpleWriter2.EVERY-th pixel along a row; a row's fits are solved at the
// same time, before the row is decoded), the deltas
// mixed-context coded with the second opinions, in bands of rows decoded at
// the same time, resized back with ResizeMapper.resize2 (centred). See
// SimpleWriter2.encode for the layout.
/**
 * Reads SimpleWriter2's files (see the comment above).
 */
public class SimpleReader2
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
	 * Each band's channels' first values.
	 */
	int[][] band_init;
	/**
	 * Each band's channels' coded deltas.
	 */
	DeltaMapper.MixedDeltas[][] band_mixed;

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
		if(args.length != 1) { System.out.println("Usage: java SimpleReader2 <filename>"); System.exit(0); }
		new SimpleReader2(args[0]);
	}

	/**
	 * Reads a file, opens its window and decodes it (twice, printing both
	 * times: the first includes compiling the code).
	 *
	 * @param filename the file
	 */
	public SimpleReader2(String filename)
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
				view = new ViewerSupport("Simple Reader 2  " + filename, xdim, ydim);
				view.frame.getJMenuBar().add(view.makeViewMenu());
				view.setStatus("decoding...");
				view.show();
			});

			// Decoded twice, the times of both printed: the first includes
			// compiling the code (the first time it runs in this JVM), the
			// second is the run time only. The image shows after the first.
			start = System.nanoTime();
			decoded_image = decode();
			long first = System.nanoTime() - start;
			SwingUtilities.invokeLater(() -> { view.setImage(decoded_image); view.setStatus(null); view.fitAndShrink(); });
			start = System.nanoTime();
			BufferedImage again = decode();
			long second = System.nanoTime() - start;
			int[] p = decoded_image.getRGB(0, 0, xdim, ydim, null, 0, xdim), q = again.getRGB(0, 0, xdim, ydim, null, 0, xdim);
			if(!java.util.Arrays.equals(p, q)) System.out.println("WARNING: the second decoding differs from the first.");
			System.out.println("Image rebuilt (" + xdim + " x " + ydim + ").");
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
	 * Reads the header and the bands' coded deltas (decoded by decode).
	 *
	 * @param in       the file
	 * @param filename its name, for messages
	 * @throws IOException if it can't be read or isn't a Simple 2 file
	 */
	private void read(DataInputStream in, String filename) throws IOException
	{
		int id = in.readUnsignedByte(), version = in.readUnsignedByte();
		if(id != FORMAT_ID || version != FORMAT_VERSION)
			throw new IOException(id != FORMAT_ID
				? filename + " is not a Simple Writer 2 file."
				: filename + " is Simple 2 format version " + version + "; this reader reads version " + FORMAT_VERSION + " only.");
		xdim        = in.readUnsignedShort();
		ydim        = in.readUnsignedShort();
		pixel_shift = in.readByte();
		pixel_quant = in.readByte();
		for(int i = 0; i < 3; i++) min[i] = in.readInt();
		int h = DeltaMapper.getQuantizedSize(xdim, ydim, pixel_quant)[1];
		bands = in.readUnsignedByte();
		if(bands < 1 || h / bands < 2) throw new IOException(filename + ": " + bands + " bands of a " + h + "-row image.");
		int[] length = new int[bands];
		for(int b = 0; b < bands; b++) length[b] = in.readInt();
		band_init  = new int[bands][3];
		band_mixed = new DeltaMapper.MixedDeltas[bands][3];
		for(int b = 0; b < bands; b++)
		{
			byte[] bytes = new byte[length[b]];
			in.readFully(bytes);
			DataInputStream bi = new DataInputStream(new ByteArrayInputStream(bytes));
			for(int i = 0; i < 3; i++)
			{
				band_init[b][i]  = bi.readInt();
				band_mixed[b][i] = DeltaMapper.readMixedDeltas(bi);
			}
		}
		System.out.println("Image:        " + xdim + " x " + ydim);
		if(bands > 1) System.out.println(SimpleWriter.describeBands(bands, h));
		System.out.println();
	}

	/**
	 * The bands decoded at the same time (each band's channels at the same
	 * time too, each a couple of rows behind the one before: a channel's fit
	 * and contexts use the ones before it), then blue, green and red rebuilt
	 * from them, resized and shifted.
	 *
	 * @return the image
	 */
	private BufferedImage decode()
	{
		int[]   size = DeltaMapper.getQuantizedSize(xdim, ydim, pixel_quant);
		int     w = size[0], h = size[1];
		int[]   radius = {SimpleWriter2.RADIUS, SimpleWriter2.RADIUS, SimpleWriter2.RADIUS};
		int[][] channel = new int[3][w * h];
		java.util.stream.IntStream.range(0, bands).parallel().forEach(b ->
		{
			int y0 = SimpleWriter.bandStart(b, bands, h), y1 = SimpleWriter.bandStart(b + 1, bands, h);
			int[][] v = DeltaMapper.getValuesFromRowFitChannels(band_mixed[b], w, y1 - y0, band_init[b], radius, SimpleWriter2.EVERY)[1];
			for(int i = 0; i < 3; i++) System.arraycopy(v[i], 0, channel[i], y0 * w, v[i].length);
		});
		for(int i = 0; i < 3; i++) if(SimpleWriter2.CHANNELS[i] > 2) for(int k = 0; k < channel[i].length; k++) channel[i][k] += min[i];
		int[][] bgr = DeltaMapper.getBlueGreenRed(SimpleWriter2.SET_ID, channel[0], channel[1], channel[2]);
		if(pixel_quant != 0) ViewerSupport.parallel(3, c -> bgr[c] = ResizeMapper.resize2(bgr[c], w, xdim, ydim));
		BufferedImage image = new BufferedImage(xdim, ydim, BufferedImage.TYPE_INT_RGB);
		image.setRGB(0, 0, xdim, ydim, DeltaMapper.getPixel(bgr[0], bgr[1], bgr[2], xdim, pixel_shift), 0, xdim);
		return image;
	}
}
