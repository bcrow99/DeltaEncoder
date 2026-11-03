import java.awt.image.*;
import java.io.*;
import javax.swing.*;

// SimpleReader version 3.3 (reads Simple format 8: least squares alone, the
// wide clamp, centred resizing)
//
// Reads SimpleWriter's files: every interior pixel predicted by the
// least-squares fit (the wide clamp) with each channel's window radius, the deltas
// mixed-context coded with the second opinions, in bands of rows decoded at
// the same time, resized back with ResizeMapper.resize2 (centred). The file
// is read in stages: each band's channels start decoding as soon as the
// start of them has been read, and decode their coded bytes as they arrive,
// while the rest of the file is read. See SimpleWriter.encode for the
// layout. Older Simple files (formats 3 to 7) are not read.
/**
 * Reads SimpleWriter's files (see the comment above).
 */
public class SimpleReader
{
	/**
	 * Every file starts with FORMAT_ID ('S' = Simple format) and
	 * FORMAT_VERSION.
	 */
	static final char FORMAT_ID      = 'S';
	/**
	 * The file format's version: bump it in the writer and reader together
	 * whenever the file layout or a coder's output changes. 6: least squares
	 * alone, a window radius and clamp per channel, in bands; 7: 6 with
	 * centred resizing (ResizeMapper.resize2); 8: 7 with the wide clamp only
	 * (no clamp byte).
	 */
	static final int  FORMAT_VERSION = 8;

	/**
	 * The image's width and height, Color Resolution, Pixel Resolution and
	 * channel set.
	 */
	int xdim, ydim, pixel_shift, pixel_quant, set_id;
	/**
	 * Each channel's minimum (added back to the difference channels) and
	 * window radius, in coding order.
	 */
	int[] min = new int[3], radius = new int[3];
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
		if(args.length != 1) { System.out.println("Usage: java SimpleReader <filename>"); System.exit(0); }
		new SimpleReader(args[0]);
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
	public SimpleReader(String filename)
	{
		try
		{
			try(DataInputStream in = new DataInputStream(new BufferedInputStream(new FileInputStream(filename))))
			{
				readHeader(in, filename);
			}
			int h = DeltaMapper.getQuantizedSize(xdim, ydim, pixel_quant)[1];
			System.out.println("Image:        " + xdim + " x " + ydim);
			System.out.println("Channel set:  " + DeltaMapper.SET_NAMES[set_id]);
			for(int i = 0; i < 3; i++) System.out.println(String.format("Channel %d - %s: radius %d", i, DeltaMapper.channelName(set_id, i), radius[i]));
			if(bands > 1) System.out.println(SimpleWriter.describeBands(bands, h));
			System.out.println();

			ViewerSupport.runOnEdt(() ->
			{
				view = new ViewerSupport("Simple Reader  " + filename, xdim, ydim);
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
				+ ViewerSupport.formatDuration(read_time) + ", while decoding.");
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
	 * Reads the header: the image's size, the quantization, the channel set,
	 * each channel's minimum and window radius, the number of bands and their
	 * lengths.
	 *
	 * @param in       the file
	 * @param filename its name, for messages
	 * @throws IOException if it can't be read or isn't a Simple file this reader reads
	 */
	private void readHeader(DataInputStream in, String filename) throws IOException
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
			if(radius[i] < 1) throw new IOException(filename + ": channel " + i + " has window radius " + radius[i] + ".");
		}
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
	 * How many coded bytes are read at a time, saying each time that they
	 * have arrived.
	 */
	static final int CHUNK = 1 << 16;

	/**
	 * Reads a Simple file from a stream and decodes it, in stages: the bands
	 * start decoding at once, each in its own thread (each band's channels in
	 * threads of their own, each a couple of rows behind the one before: a
	 * channel's fit and contexts use the ones before it), and each channel
	 * starts as soon as this thread has read the start of it (its rank
	 * table), decoding its coded bytes as they arrive, a chunk at a time,
	 * while the rest are read (ArithmeticMapper.Arriving). Then the channel
	 * set is recombined, resized and shifted. Leaves the time the reading
	 * took in read_time.
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
		int[]   rad = radius.clone();
		int[][] channel = new int[3][w * h];
		int[][] init = new int[bands][3];
		@SuppressWarnings({"unchecked", "rawtypes"})
		java.util.concurrent.CompletableFuture<DeltaMapper.MixedDeltas>[][] coded = new java.util.concurrent.CompletableFuture[bands][3];
		for(int b = 0; b < bands; b++) for(int i = 0; i < 3; i++) coded[b][i] = new java.util.concurrent.CompletableFuture<>();
		Throwable[] failed = new Throwable[1];
		Thread[] worker = new Thread[bands];
		for(int b = 0; b < bands; b++)
		{
			int fb = b;
			worker[b] = new Thread(() ->
			{
				try
				{
					int y0 = SimpleWriter.bandStart(fb, bands, h), y1 = SimpleWriter.bandStart(fb + 1, bands, h);
					int[][] v = DeltaMapper.getValuesFromLeastSquaresChannels(coded[fb], w, y1 - y0, init[fb], rad, 1)[1];
					for(int i = 0; i < 3; i++) System.arraycopy(v[i], 0, channel[i], y0 * w, v[i].length);
				}
				catch(Throwable e) { synchronized(failed) { if(failed[0] == null) failed[0] = e; } }
			});
			worker[b].start();
		}
		long start = System.nanoTime();
		java.util.List<ArithmeticMapper.Arriving> arriving = new java.util.ArrayList<>();
		long[] used = new long[1];   // the bytes read so far
		try
		{
			DataInputStream in = new DataInputStream(new FilterInputStream(new BufferedInputStream(raw, CHUNK))
			{
				@Override public int read() throws IOException { int c = super.read(); if(c >= 0) used[0]++; return c; }
				@Override public int read(byte[] b, int off, int len) throws IOException { int n = super.read(b, off, len); if(n > 0) used[0] += n; return n; }
			});
			int xd = xdim, yd = ydim, nb = bands, set = set_id;
			int[] len = length, mn = min.clone();
			readHeader(in, name);
			if(xdim != xd || ydim != yd || bands != nb || set_id != set || !java.util.Arrays.equals(length, len)
				|| !java.util.Arrays.equals(min, mn) || !java.util.Arrays.equals(radius, rad)) throw new IOException(name + " changed while it was read.");
			for(int b = 0; b < bands; b++)
			{
				long end = used[0] + length[b];
				for(int i = 0; i < 3; i++)
				{
					init[b][i] = in.readInt();
					DeltaMapper.MixedDeltas md = DeltaMapper.readArrivingMixedDeltas(in);
					arriving.add(md.arriving);
					coded[b][i].complete(md);
					byte[] buf = md.arriving.bytes;
					for(int got = 0; got < buf.length; )
					{
						int n = in.read(buf, got, Math.min(CHUNK, buf.length - got));
						if(n < 0) throw new EOFException(name + " ends in band " + b + ", channel " + i + ".");
						got += n;
						md.arriving.arrived(got);
					}
				}
				if(used[0] != end) throw new IOException(name + ": band " + b + " is not the length its header says.");
			}
		}
		catch(Throwable e)   // any failure, an Error too (running out of memory, say): otherwise the band threads would wait forever for their bytes
		{
			for(int b = 0; b < bands; b++) for(int i = 0; i < 3; i++) coded[b][i].completeExceptionally(e);
			for(ArithmeticMapper.Arriving a : arriving) a.fail(e);
			for(Thread t : worker) try { t.join(); } catch(InterruptedException x) { Thread.currentThread().interrupt(); }
			throw e;
		}
		read_time = System.nanoTime() - start;
		for(Thread t : worker)
			try { t.join(); } catch(InterruptedException e) { Thread.currentThread().interrupt(); throw new IOException(e); }
		if(failed[0] instanceof RuntimeException) throw (RuntimeException) failed[0];
		if(failed[0] instanceof Error) throw (Error) failed[0];
		if(failed[0] != null) throw new IOException(failed[0]);

		int[] id = DeltaMapper.getChannels(set_id);
		for(int i = 0; i < 3; i++) if(id[i] > 2) for(int k = 0; k < channel[i].length; k++) channel[i][k] += min[i];
		int[][] bgr = DeltaMapper.getBlueGreenRed(set_id, channel[0], channel[1], channel[2]);
		if(pixel_quant != 0) ViewerSupport.parallel(3, c -> bgr[c] = ResizeMapper.resize2(bgr[c], w, xdim, ydim));
		BufferedImage image = new BufferedImage(xdim, ydim, BufferedImage.TYPE_INT_RGB);
		image.setRGB(0, 0, xdim, ydim, DeltaMapper.getPixel(bgr[0], bgr[1], bgr[2], xdim, pixel_shift), 0, xdim);
		return image;
	}
}
