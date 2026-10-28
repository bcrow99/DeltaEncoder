import java.awt.*;
import java.awt.image.*;
import java.io.*;
import java.util.*;
import javax.swing.*;

// SimpleReader version 3.1 (reads Simple format 7: least squares alone, centred resizing)
//
// Reads SimpleWriter's files: every interior pixel predicted by the
// least-squares fit with each channel's window radius and clamp, the deltas
// mixed-context coded with the second opinions, in bands of rows decoded at
// the same time, resized back with ResizeMapper.resize2 (centred). See
// SimpleWriter.SaveHandler.save for the layout. Older Simple files (formats
// 3 to 6) are not read.
public class SimpleReader
{
	// File format: every file starts with FORMAT_ID ('S' = Simple format) and
	// FORMAT_VERSION. Bump FORMAT_VERSION in the writer and reader together
	// whenever the file layout or a coder's output changes.
	static final char FORMAT_ID      = 'S';
	static final int  FORMAT_VERSION = 7;   // 6: least squares alone, a window radius and clamp per channel, in bands; 7: 6 with centred resizing (ResizeMapper.resize2)

	int xdim, ydim, pixel_shift, pixel_quant, set_id;
	/**
	 * Each channel's minimum (added back to the difference channels), window
	 * radius and clamp, in coding order.
	 */
	int[]     min = new int[3], radius = new int[3];
	/**
	 * Whether each channel's prediction has the wide clamp.
	 */
	boolean[] wide = new boolean[3];
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

	BufferedImage decoded_image = null;
	ViewerSupport view;

	public static void main(String[] args)
	{
		ViewerSupport.applyHiDpiFontScaleIfNeeded();
		if(args.length != 1) { System.out.println("Usage: java SimpleReader <filename>"); System.exit(0); }
		new SimpleReader(args[0]);
	}

	public SimpleReader(String filename)
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
				view = new ViewerSupport("Simple Reader  " + filename, xdim, ydim);
				view.frame.getJMenuBar().add(view.makeViewMenu());
				view.setStatus("decoding...");
				view.show();
			});

			start = System.nanoTime();
			decoded_image = decode();
			System.out.println("Image rebuilt in " + ((System.nanoTime() - start) / 1_000_000) + " ms.");
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

	private void read(DataInputStream in, String filename) throws IOException
	{
		int id = in.readUnsignedByte(), version = in.readUnsignedByte();
		if(id != FORMAT_ID || version != FORMAT_VERSION)
			throw new IOException(id != FORMAT_ID
				? filename + " is not a Simple Writer file."
				: filename + " is Simple format version " + version + "; this reader reads version " + FORMAT_VERSION + " only.");
		xdim        = in.readUnsignedShort();
		ydim        = in.readUnsignedShort();
		pixel_shift = in.readByte();
		pixel_quant = in.readByte();
		set_id      = in.readByte();
		for(int i = 0; i < 3; i++)
		{
			min[i]    = in.readInt();
			radius[i] = in.readUnsignedByte();
			wide[i]   = in.readUnsignedByte() != 0;
			if(radius[i] < 1) throw new IOException(filename + ": channel " + i + " has window radius " + radius[i] + ".");
		}
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
		System.out.println("Channel set:  " + DeltaMapper.SET_NAMES[set_id]);
		for(int i = 0; i < 3; i++) System.out.println(String.format("Channel %d - %s: %s", i, DeltaMapper.channelName(set_id, i), DeltaMapper.describeWindow(radius[i], wide[i])));
		if(bands > 1) System.out.println(SimpleWriter.describeBands(bands, h));
		System.out.println();
	}

	// The bands decoded at the same time (each band's channels in order: a
	// channel's fit and contexts use the ones before it), then the channel
	// set recombined, resized and shifted.
	private BufferedImage decode()
	{
		int[]   size = DeltaMapper.getQuantizedSize(xdim, ydim, pixel_quant);
		int     w = size[0], h = size[1];
		int[][] channel = new int[3][w * h];
		java.util.stream.IntStream.range(0, bands).parallel().forEach(b ->
		{
			int y0 = SimpleWriter.bandStart(b, bands, h), y1 = SimpleWriter.bandStart(b + 1, bands, h);
			int[][] d = new int[3][], v = new int[3][];
			for(int i = 0; i < 3; i++)
			{
				int[][] r = DeltaMapper.getValuesFromLeastSquaresDeltas(band_mixed[b][i], Arrays.copyOf(d, i), w, y1 - y0, band_init[b][i], (i > 0) ? v[i - 1] : null, radius[i], wide[i]);
				d[i] = r[0];
				v[i] = r[1];
				System.arraycopy(v[i], 0, channel[i], y0 * w, v[i].length);
			}
		});
		int[] id = DeltaMapper.getChannels(set_id);
		for(int i = 0; i < 3; i++) if(id[i] > 2) for(int k = 0; k < channel[i].length; k++) channel[i][k] += min[i];
		int[][] bgr = DeltaMapper.getBlueGreenRed(set_id, channel[0], channel[1], channel[2]);
		if(pixel_quant != 0) ViewerSupport.parallel(3, c -> bgr[c] = ResizeMapper.resize2(bgr[c], w, xdim, ydim));
		BufferedImage image = new BufferedImage(xdim, ydim, BufferedImage.TYPE_INT_RGB);
		image.setRGB(0, 0, xdim, ydim, DeltaMapper.getPixel(bgr[0], bgr[1], bgr[2], xdim, pixel_shift), 0, xdim);
		return image;
	}
}
