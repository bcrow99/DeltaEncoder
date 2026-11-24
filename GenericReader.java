import java.awt.image.*;
import java.io.*;
import javax.swing.*;

// GenericReader version 1.2 (reads Simple 2 formats 2 and 3, Huffman formats 1
// to 4, and Neighbour Huffman format 1)
//
// NativeEncoder's NativeReader in Java alone. Reads GenericWriter's files
// (and NativeWriter's, the same files, and SimpleWriter2's and
// HuffmanWriter's: Simple 2 format 2 and Huffman formats 3 and 1): the channels
// green, blue-green and red-green (GenericWriter.SET_ID), in bands of rows
// decoded at the same time (with LS deltas, ViewerSupport.lsBandsAtOnce at a
// time), resized back with ResizeMapper.resize2 (centred). The file's first
// byte says how its deltas were coded:
//
//   Simple 2 ('F', context aware): the file is read in stages: each band's
//   channels start decoding as soon as the start of them has been read, and
//   decode their coded bytes as they arrive, while the rest of the file is
//   read; a band's channels are decoded at the same time, each a couple of
//   rows behind the one before (its contexts use their deltas).
//
//   Huffman ('H'): the file is read, then each band's channels' deltas are
//   decoded (HuffmanWriter.huffmanDecode) and the channels rebuilt from them,
//   the bands at the same time (LS: ViewerSupport.lsBandsAtOnce at a time).
//
//   Neighbour Huffman ('N'): as Huffman, the file is read, then each band's
//   channels' deltas are decoded (NeighbourMapper.neighbourDecode) and the
//   channels rebuilt from them.
//
// The deltas: LS, every interior pixel predicted by the least-squares fit
// from the rows above (DeltaMapper.RowFit, window radius GenericWriter.RADIUS,
// solved at every GenericWriter.EVERY-th pixel along a row; a row's fits are
// solved at the same time, before the row is decoded), or, when the version
// says so, the delta type in the header: Avg, each pixel predicted from
// (left + above) / 2. Then blue, green and red are rebuilt, resized back and
// packed. See GenericWriter.encode for the layout.
/**
 * Reads GenericWriter's files (see the comment above).
 */
public class GenericReader
{
	/**
	 * The image's width and height, Color Resolution and Pixel Resolution.
	 */
	int xdim, ydim, pixel_shift, pixel_quant;
	/**
	 * The deltas' type: GenericWriter.DELTA_LS, or the one the header names.
	 */
	int delta_type;
	/**
	 * The coding: GenericWriter.ENTROPY_MIXED (Simple 2 format), ENTROPY_HUFFMAN
	 * (Huffman format) or ENTROPY_NEIGHBOUR (Neighbour Huffman format).
	 */
	int entropy_type;
	/**
	 * A Huffman file's code lengths are Deflated (its versions before
	 * GenericWriter.HUFFMAN_VERSION).
	 */
	boolean huffman_deflated;
	/**
	 * A Neighbour Huffman file's version (GenericWriter.FORMAT_VERSION, or
	 * FORMAT_VERSION_UNFLIPPED: its signs not flipped).
	 */
	int neighbour_version = GenericWriter.FORMAT_VERSION;
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
		if(args.length != 1) { System.out.println("Usage: java GenericReader <filename>"); System.exit(0); }
		new GenericReader(args[0]);
	}

	/**
	 * Reads a file's header, opens its window, then reads and decodes the
	 * file (twice, printing both times: the first includes compiling the
	 * code; see decode).
	 *
	 * @param filename the file
	 */
	public GenericReader(String filename)
	{
		try
		{
			try(DataInputStream in = new DataInputStream(new BufferedInputStream(new FileInputStream(filename))))
			{
				readHeader(in, filename);
			}
			System.out.println("Image:        " + xdim + " x " + ydim);
			System.out.println("Deltas:       " + GenericWriter.DELTA_NAMES[delta_type]);
			System.out.println("Entropy:      " + GenericWriter.ENTROPY_NAMES[entropy_type]);
			if(bands > 1) System.out.println(SimpleWriter.describeBands(bands, DeltaMapper.getQuantizedSize(xdim, ydim, pixel_quant)[1]));
			System.out.println();

			ViewerSupport.runOnEdt(() ->
			{
				view = new ViewerSupport("Generic Reader  " + filename, xdim, ydim);
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
				+ ViewerSupport.formatDuration(read_time) + ((entropy_type == GenericWriter.ENTROPY_MIXED) ? ", while decoding." : "."));
			ViewerSupport.printRunTimes("GenericReader " + GenericWriter.DELTA_NAMES[delta_type] + " " + GenericWriter.ENTROPY_NAMES[entropy_type], first, second, both);
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
	 * Reads the header: the format (so the coding), the image's size, the
	 * quantization, the delta type, the channels' minimums, the number of
	 * bands and their lengths.
	 *
	 * @param in       the file
	 * @param filename its name, for messages
	 * @throws IOException if it can't be read or isn't a file GenericWriter writes
	 */
	private void readHeader(DataInputStream in, String filename) throws IOException
	{
		int id = in.readUnsignedByte(), version = in.readUnsignedByte(), ls_version, type_version;
		if(id == GenericWriter.SIMPLE_ID)
		{
			entropy_type = GenericWriter.ENTROPY_MIXED;
			ls_version   = GenericWriter.SIMPLE_VERSION;
			type_version = GenericWriter.SIMPLE_VERSION_DELTA_TYPE;
		}
		else if(id == GenericWriter.HUFFMAN_ID)
		{
			// The versions before, with the code lengths Deflated, are read
			// too: as the ones they came before.
			entropy_type = GenericWriter.ENTROPY_HUFFMAN;
			ls_version   = GenericWriter.HUFFMAN_VERSION;
			type_version = GenericWriter.HUFFMAN_VERSION_DELTA_TYPE;
			huffman_deflated = version == GenericWriter.HUFFMAN_VERSION_DEFLATE || version == GenericWriter.HUFFMAN_VERSION_DEFLATE_DELTA_TYPE;
			if(huffman_deflated) version += GenericWriter.HUFFMAN_VERSION - GenericWriter.HUFFMAN_VERSION_DEFLATE;
		}
		else
		{
			// Neighbour Huffman: the delta type always there; the version
			// before, its signs not flipped, read too, as this one.
			entropy_type = -1;
			for(int t = 0; t < GenericWriter.FORMAT_IDS.length; t++) if(GenericWriter.FORMAT_IDS[t] != 0 && id == GenericWriter.FORMAT_IDS[t]) entropy_type = t;
			if(entropy_type < 0) throw new IOException(filename + " is not a file GenericWriter writes.");
			ls_version   = -1;
			type_version = GenericWriter.FORMAT_VERSION;
			neighbour_version = version;
			if(version == GenericWriter.FORMAT_VERSION_UNFLIPPED) version = GenericWriter.FORMAT_VERSION;
		}
		String format = (id == GenericWriter.SIMPLE_ID) ? "Simple 2" : (id == GenericWriter.HUFFMAN_ID) ? "Huffman" : GenericWriter.ENTROPY_NAMES[entropy_type];
		if(version != ls_version && version != type_version)
			throw new IOException(filename + " is " + format + " format version " + version + "; this reader reads "
			                      + ((ls_version < 0) ? "version " + type_version : "versions " + ls_version + " and " + type_version) + " only.");
		xdim        = in.readUnsignedShort();
		ydim        = in.readUnsignedShort();
		pixel_shift = in.readByte();
		pixel_quant = in.readByte();
		delta_type  = (version == ls_version) ? GenericWriter.DELTA_LS : in.readUnsignedByte();
		if(delta_type != GenericWriter.DELTA_LS && delta_type != GenericWriter.DELTA_AVERAGE)
			throw new IOException(filename + ": unknown delta type " + delta_type + ".");
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
	 * How many coded bytes are read at a time, saying each time that they
	 * have arrived.
	 */
	static final int CHUNK = 1 << 16;

	/**
	 * Reads the header again (decode), checking it's the one the
	 * constructor read.
	 *
	 * @param in   the file, at its start
	 * @param name its name, for messages
	 * @throws IOException if it can't be read or has changed
	 */
	private void checkHeader(DataInputStream in, String name) throws IOException
	{
		int xd = xdim, yd = ydim, nb = bands, dt = delta_type, et = entropy_type;
		int[] len = length;
		readHeader(in, name);
		if(xdim != xd || ydim != yd || bands != nb || delta_type != dt || entropy_type != et || !java.util.Arrays.equals(length, len))
			throw new IOException(name + " changed while it was read.");
	}

	/**
	 * Reads a file GenericWriter wrote from a stream and decodes it
	 * (decodeMixed or decodeHuffman, by its format), then rebuilds blue,
	 * green and red, resizes and shifts them. Leaves the time the reading
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
		int     w = size[0];
		int[][] channel = new int[3][w * size[1]];
		if(entropy_type == GenericWriter.ENTROPY_MIXED) decodeMixed(raw, name, w, size[1], channel);
		else if(entropy_type == GenericWriter.ENTROPY_HUFFMAN) decodeHuffman(raw, name, w, size[1], channel);
		else                                           decodeAlone(raw, name, w, size[1], channel);

		// The minimums are added back to the difference channels, then blue,
		// green and red rebuilt, resized back and packed.
		for(int i = 0; i < 3; i++) if(GenericWriter.CHANNELS[i] > 2) for(int k = 0; k < channel[i].length; k++) channel[i][k] += min[i];
		int[][] bgr = DeltaMapper.getBlueGreenRed(GenericWriter.SET_ID, channel[0], channel[1], channel[2]);
		if(pixel_quant != 0) ViewerSupport.parallel(3, c -> bgr[c] = ResizeMapper.resize2(bgr[c], w, xdim, ydim));
		return ViewerSupport.makeImage(bgr[0], bgr[1], bgr[2], xdim, ydim, pixel_shift);
	}

	/**
	 * decode for a Simple 2 file (context aware): reads it and decodes it into
	 * channel, in stages. The bands decode in order, in threads of their own:
	 * all at once with the average deltas, ViewerSupport.lsBandsAtOnce at a
	 * time with LS (each band's channels in threads of their own, each a
	 * couple of rows behind the one before: a channel's fit and contexts use
	 * the ones before it), and each channel starts as soon as this thread has
	 * read the start of it (its rank table), decoding its coded bytes as they
	 * arrive, a chunk at a time, while the rest are read
	 * (ArithmeticMapper.Arriving). Leaves the time the reading took in
	 * read_time.
	 *
	 * @param raw     the stream (from the start of the file)
	 * @param name    its name, for messages
	 * @param w       the quantized width
	 * @param h       the quantized height
	 * @param channel gets the channels (w * h each)
	 * @throws IOException if it can't be read, or isn't the file the header was read from
	 */
	private void decodeMixed(InputStream raw, String name, int w, int h, int[][] channel) throws IOException
	{
		int[]   radius = {GenericWriter.RADIUS, GenericWriter.RADIUS, GenericWriter.RADIUS};
		int[][] init = new int[bands][3];
		@SuppressWarnings({"unchecked", "rawtypes"})
		java.util.concurrent.CompletableFuture<DeltaMapper.MixedDeltas>[][] coded = new java.util.concurrent.CompletableFuture[bands][3];
		for(int b = 0; b < bands; b++) for(int i = 0; i < 3; i++) coded[b][i] = new java.util.concurrent.CompletableFuture<>();
		Throwable[] failed = new Throwable[1];
		// The bands' threads, each taking the next band, in order, when it's
		// done with one: ViewerSupport.lsBandsAtOnce of them with LS, as many
		// as there are bands with the average deltas (they decoded fastest all
		// at once).
		int at_once = (delta_type == GenericWriter.DELTA_LS) ? ViewerSupport.lsBandsAtOnce(bands) : bands;
		Thread[] worker = ViewerSupport.startBandThreads(bands, at_once, fb ->
		{
			try
			{
				int y0 = SimpleWriter.bandStart(fb, bands, h), y1 = SimpleWriter.bandStart(fb + 1, bands, h);
				int[][] v = (delta_type == GenericWriter.DELTA_AVERAGE)
					? DeltaMapper.getValuesFromAverageChannels(coded[fb], w, y1 - y0, init[fb])[1]
					: DeltaMapper.getValuesFromRowFitChannels(coded[fb], w, y1 - y0, init[fb], radius, GenericWriter.EVERY)[1];
				for(int i = 0; i < 3; i++) System.arraycopy(v[i], 0, channel[i], y0 * w, v[i].length);
			}
			catch(Throwable e) { synchronized(failed) { if(failed[0] == null) failed[0] = e; } }
		});
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
			checkHeader(in, name);
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
	}

	/**
	 * decode for a Huffman file: reads it all, then decodes it into channel:
	 * every band's channels' deltas at the same time
	 * (HuffmanWriter.huffmanDecode), then rebuilds each band's channels from
	 * them (DeltaMapper.getValuesFromRowFitDeltas), ViewerSupport.lsBandsAtOnce
	 * bands at a time. Leaves the time the reading took in read_time.
	 *
	 * @param raw     the stream (from the start of the file)
	 * @param name    its name, for messages
	 * @param w       the quantized width
	 * @param h       the quantized height
	 * @param channel gets the channels (w * h each)
	 * @throws IOException if it can't be read, or isn't the file the header was read from
	 */
	private void decodeHuffman(InputStream raw, String name, int w, int h, int[][] channel) throws IOException
	{
		int[]   radius = {GenericWriter.RADIUS, GenericWriter.RADIUS, GenericWriter.RADIUS};
		int[][] init = new int[bands][3];
		HuffmanWriter.HuffmanChannel[][] coded = new HuffmanWriter.HuffmanChannel[bands][3];
		long start = System.nanoTime();
		DataInputStream in = new DataInputStream(new BufferedInputStream(raw, CHUNK));
		checkHeader(in, name);
		for(int b = 0; b < bands; b++)
		{
			byte[] band = new byte[length[b]];
			in.readFully(band);
			DataInputStream bi = new DataInputStream(new ByteArrayInputStream(band));
			// Each channel: init, then huffmanCode's bytes.
			for(int i = 0; i < 3; i++)
			{
				init[b][i] = bi.readInt();
				coded[b][i] = HuffmanWriter.huffmanRead(bi, huffman_deflated);
			}
			if(bi.available() != 0) throw new IOException(name + ": band " + b + " is not the length its header says.");
		}
		read_time = System.nanoTime() - start;

		// The average deltas: every band's channels decoded and rebuilt, each
		// on its own, all at the same time.
		if(delta_type == GenericWriter.DELTA_AVERAGE)
		{
			ViewerSupport.parallel(bands * 3, job ->
			{
				int b = job / 3, i = job % 3;
				int y0 = SimpleWriter.bandStart(b, bands, h), y1 = SimpleWriter.bandStart(b + 1, bands, h);
				GenericWriter.huffmanAverage(coded[b][i], w, y1 - y0, init[b][i], channel[i], y0 * w);   // (straight into the channel)
			});
		}

		// The fit's deltas, ViewerSupport.lsBandsAtOnce bands at a time: each
		// band's channels' deltas decoded at the same time, then the channels
		// rebuilt.
		if(delta_type == GenericWriter.DELTA_LS) ViewerSupport.forBands(bands, ViewerSupport.lsBandsAtOnce(bands), b ->
		{
			int y0 = SimpleWriter.bandStart(b, bands, h), y1 = SimpleWriter.bandStart(b + 1, bands, h);
			int[][] delta = new int[3][];
			ViewerSupport.parallel(3, i -> delta[i] = HuffmanWriter.huffmanDecode(coded[b][i], w * (y1 - y0)));
			int[][] v = DeltaMapper.getValuesFromRowFitDeltas(delta, w, y1 - y0, init[b], radius, GenericWriter.EVERY);
			for(int i = 0; i < 3; i++) System.arraycopy(v[i], 0, channel[i], y0 * w, v[i].length);
		});
	}

	/**
	 * decode for a Neighbour Huffman file: reads it all, then decodes it into
	 * channel: every band's channels' deltas decoded at the same time
	 * (GenericWriter.decodeChannel), then each band's channels rebuilt from them,
	 * with the average deltas each on its own (at the same time as the
	 * decoding), with LS by DeltaMapper.getValuesFromRowFitDeltas,
	 * ViewerSupport.lsBandsAtOnce bands at a time. Leaves the time the reading took in read_time.
	 *
	 * @param raw     the stream (from the start of the file)
	 * @param name    its name, for messages
	 * @param w       the quantized width
	 * @param h       the quantized height
	 * @param channel gets the channels (w * h each)
	 * @throws IOException if it can't be read, or isn't the file the header was read from
	 */
	private void decodeAlone(InputStream raw, String name, int w, int h, int[][] channel) throws IOException
	{
		int[]      radius = {GenericWriter.RADIUS, GenericWriter.RADIUS, GenericWriter.RADIUS};
		int[][]    init = new int[bands][3];
		Object[][] coded = new Object[bands][3];
		long start = System.nanoTime();
		DataInputStream in = new DataInputStream(new BufferedInputStream(raw, CHUNK));
		checkHeader(in, name);
		for(int b = 0; b < bands; b++)
		{
			byte[] band = new byte[length[b]];
			in.readFully(band);
			DataInputStream bi = new DataInputStream(new ByteArrayInputStream(band));
			// Each channel: init, then its coding's bytes.
			for(int i = 0; i < 3; i++)
			{
				init[b][i] = bi.readInt();
				coded[b][i] = GenericWriter.readChannel(entropy_type, bi, neighbour_version);
			}
			if(bi.available() != 0) throw new IOException(name + ": band " + b + " is not the length its header says.");
		}
		read_time = System.nanoTime() - start;

		// Every band's channels' deltas at the same time; with the average
		// deltas, each channel rebuilt straight away, into the channel.
		int[][][] delta = new int[bands][3][];
		ViewerSupport.parallel(bands * 3, job ->
		{
			int b = job / 3, i = job % 3;
			int y0 = SimpleWriter.bandStart(b, bands, h), y1 = SimpleWriter.bandStart(b + 1, bands, h);
			try { delta[b][i] = GenericWriter.decodeChannel(entropy_type, coded[b][i], w, y1 - y0); }
			catch(IOException e) { throw new UncheckedIOException(e); }
			if(delta_type == GenericWriter.DELTA_AVERAGE)
				DeltaMapper.getValuesFromAverageDeltas(delta[b][i], w, y1 - y0, init[b][i], channel[i], y0 * w);
		});

		// The fit's deltas: each band's channels rebuilt, ViewerSupport.lsBandsAtOnce bands at a time.
		if(delta_type == GenericWriter.DELTA_LS) ViewerSupport.forBands(bands, ViewerSupport.lsBandsAtOnce(bands), b ->
		{
			int y0 = SimpleWriter.bandStart(b, bands, h), y1 = SimpleWriter.bandStart(b + 1, bands, h);
			int[][] v = DeltaMapper.getValuesFromRowFitDeltas(delta[b], w, y1 - y0, init[b], radius, GenericWriter.EVERY);
			for(int i = 0; i < 3; i++) System.arraycopy(v[i], 0, channel[i], y0 * w, v[i].length);
		});
	}
}
