import java.awt.image.*;
import java.io.*;
import java.nio.ByteBuffer;
import java.util.*;
import javax.swing.*;

// PacketReader version 1.0
public class PacketReader
{
	// File format: every file starts with FORMAT_ID ('P' = Packet format) and
	// FORMAT_VERSION. Bump FORMAT_VERSION in the writer and reader together
	// whenever the file layout or a coder's output changes.
	static final char FORMAT_ID      = 'P';
	static final int  FORMAT_VERSION = 1;

	static final String[] ENTROPY_NAMES = {"LZ77","Huffman","Arithmetic","Arithmetic (per packet)","Huffman (per packet)","Adaptive","Adaptive (per packet)","Context"};

	// ---- Header -------------------------------------------------------------
	// There is no compress_type: PacketWriter always writes unary strings.
	int xdim, ydim;
	int pixel_shift, pixel_quant, set_id, delta_type, entropy_type, scanline5_variant;
	int packet_level;   // informational only; segmentation is self-describing

	// ---- Per channel, as read -----------------------------------------------
	int[]    min = new int[3], init = new int[3], delta_min = new int[3], length = new int[3];
	int[][]  table = new int[3][];
	byte[][] map   = new byte[3][];

	// Segment table: string_data is the string's trailing data byte (for
	// restore2); segment_bytelength each segment's byte length without its
	// trailing data byte, segment_data those data bytes. The entropy-decoded
	// payload is the segments' bits packed back to back (packSegments3).
	byte[]   string_data        = new byte[3];
	int[][]  segment_bytelength = new int[3][];
	byte[][] segment_data       = new byte[3][];
	int[]    payload_length     = new int[3];

	// Entropy-coded data. coded: LZ77 (already inflated), Huffman and
	// Adaptive payloads. blocks: Arithmetic blocks, or the coded packets of
	// the per-packet types. freqs: a frequency table per Arithmetic block or
	// packet. huffman_lengths: code lengths for Huffman (one table) and
	// Huffman per packet (one per packet).
	byte[][]   coded           = new byte[3][];
	byte[][][] blocks          = new byte[3][][];
	int[][][]  freqs           = new int[3][][];
	byte[][][] huffman_lengths = new byte[3][][];
	int[][]    delta           = new int[3][];   // Context (7): decoded while reading

	BufferedImage decoded_image = null;
	ViewerSupport view;

	public static void main(String[] args)
	{
		ViewerSupport.applyHiDpiFontScaleIfNeeded();
		if(args.length != 1) { System.out.println("Usage: java PacketReader <filename>"); System.exit(0); }
		new PacketReader(args[0]);
	}

	public PacketReader(String filename)
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
				view = new ViewerSupport("Packet Reader  " + filename, xdim, ydim);
				view.frame.getJMenuBar().add(view.makeViewMenu());
				view.setStatus("decoding...");
				view.show();
			});

			start = System.nanoTime();
			int[][] channel = new int[3][];
			ViewerSupport.parallel(3, i -> channel[i] = decodeChannel(i));
			System.out.println("Channels processed in " + ((System.nanoTime() - start) / 1_000_000) + " ms.");

			start = System.nanoTime();
			decoded_image = assemble(channel);
			System.out.println("RGB assembled in " + ((System.nanoTime() - start) / 1_000_000) + " ms.");
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

	private void read(DataInputStream in, String filename) throws Exception
	{
		int id = in.readUnsignedByte(), version = in.readUnsignedByte();
		if(id != FORMAT_ID || version != FORMAT_VERSION)
			throw new IOException(id != FORMAT_ID
				? filename + " is not a Packet Writer file."
				: filename + " is Packet format version " + version + "; this reader reads version " + FORMAT_VERSION + ".");
		xdim              = in.readUnsignedShort();
		ydim              = in.readUnsignedShort();
		pixel_shift       = in.readByte();
		pixel_quant       = in.readByte();
		set_id            = in.readByte();
		delta_type        = in.readByte();
		entropy_type      = in.readByte();
		scanline5_variant = in.readByte();
		packet_level      = in.readByte();

		System.out.println("Image:        " + xdim + " x " + ydim);
		System.out.println("Channel set:  " + DeltaMapper.SET_NAMES[set_id]);
		System.out.println("Delta type:   " + DeltaMapper.DELTA_TYPE_NAMES[delta_type]);
		System.out.println("Entropy type: " + ENTROPY_NAMES[entropy_type]);
		System.out.println("Packet level: " + packet_level);
		System.out.println();

		for(int i = 0; i < 3; i++)
		{
			min[i]       = in.readInt();
			init[i]      = in.readInt();
			delta_min[i] = in.readInt();
			length[i]    = in.readInt();
			if(DeltaMapper.hasMap(delta_type)) map[i] = DeltaMapper.readMap(in, delta_type, (i > 0) ? map[i - 1] : null, DeltaMapper.getQuantizedSize(xdim, ydim, pixel_quant)[0]);
			if(entropy_type == 7)        // Context: decoded here, in channel order
			{
				int[] size = DeltaMapper.getQuantizedSize(xdim, ydim, pixel_quant);
				delta[i] = DeltaMapper.readContextDeltas(in, size[0] * size[1], Arrays.copyOf(delta, i), size[0]);
				continue;
			}
			table[i] = DeltaMapper.readTable(in);
			readSegmentTable(in, i);

			int n = segment_bytelength[i].length;
			if(entropy_type == 0)        // LZ77: payload length, Deflated length, Deflated bytes
			{
				int payload_bytes = in.readInt();
				coded[i] = CodeMapper.inflate(readBytes(in), payload_bytes);
			}
			else if(entropy_type == 1)   // Huffman: code lengths (Deflated), coded payload
			{
				huffman_lengths[i] = CodeMapper.unpackRegularTables(readBytes(in), 1);
				coded[i] = readBytes(in);
			}
			else if(entropy_type == 2)   // Arithmetic: frequency tables, then the coded blocks
			{
				freqs[i]  = ArithmeticMapper.readFrequencies(in);
				blocks[i] = new byte[freqs[i].length][];
				for(int k = 0; k < blocks[i].length; k++) blocks[i][k] = readBytes(in);
			}
			else if(entropy_type == 5)   // Adaptive: the coded payload
				coded[i] = readBytes(in);
			else                         // per packet: tables (3, 4), coded lengths, coded packets
			{
				if(entropy_type == 3)
				{
					int type = in.readInt();
					freqs[i] = ArithmeticMapper.inflateFrequencies(readBytes(in), n, type);
				}
				else if(entropy_type == 4)
					huffman_lengths[i] = CodeMapper.unpackRegularTables(readBytes(in), n);
				int[] coded_length = CodeMapper.unpackRegularLengths(readBytes(in), n);
				blocks[i] = new byte[n][];
				for(int k = 0; k < n; k++) { blocks[i][k] = new byte[coded_length[k]]; in.readFully(blocks[i][k]); }
			}
		}
	}

	// int length, then that many bytes.
	private static byte[] readBytes(DataInputStream in) throws IOException
	{
		byte[] b = new byte[in.readInt()];
		in.readFully(b);
		return b;
	}

	// The string's data byte, then the Deflated segment table (see
	// PacketWriter.makePacket): count, length-field width, all byte
	// lengths, then all trailing data bytes.
	private void readSegmentTable(DataInputStream in, int i) throws Exception
	{
		string_data[i] = in.readByte();
		int raw_length = in.readInt();
		ByteBuffer t = ByteBuffer.wrap(CodeMapper.inflate(readBytes(in), raw_length));
		int n = t.getInt(), width = t.get();
		segment_bytelength[i] = new int[n];
		segment_data[i]       = new byte[n];
		for(int k = 0; k < n; k++)
			segment_bytelength[i][k] = (width == 1) ? t.get() & 0xFF : (width == 2) ? t.getShort() & 0xFFFF : t.getInt();
		t.get(segment_data[i]);
		long bits = 0;
		for(int k = 0; k < n; k++) bits += segment_bytelength[i][k] * 8L - ((segment_data[i][k] >> 5) & 7);
		payload_length[i] = (int) ((bits + 7) / 8);
		System.out.println("Channel " + i + ": " + n + " segments, " + bits + " bits, " + payload_length[i] + " payload bytes");
	}

	// Entropy decode -> segments -> unary string -> deltas -> channel values.
	private int[] decodeChannel(int i)
	{
		try
		{
			int[] size = DeltaMapper.getQuantizedSize(xdim, ydim, pixel_quant);
			if(entropy_type == 7) return toChannel(i, delta[i], size);
			int[] blen = segment_bytelength[i];
			ArrayList<byte[]> segments;
			if(entropy_type == 3 || entropy_type == 4 || entropy_type == 6)
			{
				// Each packet on its own, with its trailing data byte re-attached.
				byte[][] seg = new byte[blen.length][];
				ViewerSupport.parallel(blen.length, k ->
				{
					byte[] body;
					if(entropy_type == 3)      body = (blen[k] == 0) ? new byte[0] : ArithmeticMapper.getArithmeticValuesFastFenwick(blocks[i][k], freqs[i][k], blen[k]);
					else if(entropy_type == 4) body = CodeMapper.unpackRegularCode(blocks[i][k], huffman_lengths[i][k], blen[k]);
					else                       body = ArithmeticMapper.getArithmeticValuesAdaptive(blocks[i][k], blen[k]);
					seg[k] = Arrays.copyOf(body, blen[k] + 1);
					seg[k][blen[k]] = segment_data[i][k];
				});
				segments = new ArrayList<byte[]>(Arrays.asList(seg));
			}
			else
			{
				byte[] payload;
				if(entropy_type == 0)      payload = coded[i];
				else if(entropy_type == 1) payload = CodeMapper.unpackRegularCode(coded[i], huffman_lengths[i][0], payload_length[i]);
				else if(entropy_type == 5) payload = ArithmeticMapper.getArithmeticValuesAdaptive(coded[i], payload_length[i]);
				else
				{
					int[]    block_length = ArithmeticMapper.getBlockLengths(payload_length[i], freqs[i].length);
					byte[][] block = new byte[block_length.length][];
					ViewerSupport.parallel(block.length, k ->
						block[k] = ArithmeticMapper.getArithmeticValuesFastFenwick(blocks[i][k], freqs[i][k], block_length[k]));
					payload = ArithmeticMapper.joinBlocks(block);
				}
				segments = SegmentMapper.unpackSegments3(payload, blen, segment_data[i]);
			}
			// restore2 decompresses whichever segments were compressed and
			// joins them at their bit offsets.
			byte[] str   = SegmentMapper.restore2(segments, string_data[i]);
			int[]  delta = StringMapper.unpackStrings(str, table[i], size[0] * size[1], length[i]);
			delta[0] = 0;
			for(int k = 1; k < delta.length; k++) delta[k] += delta_min[i];

			return toChannel(i, delta, size);
		}
		catch(Exception e) { throw new RuntimeException("channel " + i + ": " + e, e); }
	}

	private int[] toChannel(int i, int[] delta, int[] size)
	{
		int[] channel = DeltaMapper.getValuesFromDeltas(delta, size[0], size[1], init[i], delta_type, map[i], scanline5_variant);
		if(DeltaMapper.getChannels(set_id)[i] > 2)
			for(int k = 0; k < channel.length; k++) channel[k] += min[i];
		return channel;
	}

	// Recombine the channel set, then resize, then shift.
	private BufferedImage assemble(int[][] channel)
	{
		int[]   size = DeltaMapper.getQuantizedSize(xdim, ydim, pixel_quant);
		int[][] bgr  = DeltaMapper.getBlueGreenRed(set_id, channel[0], channel[1], channel[2]);
		if(pixel_quant != 0) ViewerSupport.parallel(3, c -> bgr[c] = ResizeMapper.resize(bgr[c], size[0], xdim, ydim));
		BufferedImage image = new BufferedImage(xdim, ydim, BufferedImage.TYPE_INT_RGB);
		image.setRGB(0, 0, xdim, ydim, DeltaMapper.getPixel(bgr[0], bgr[1], bgr[2], xdim, pixel_shift), 0, xdim);
		return image;
	}
}
