import java.awt.*;
import java.awt.image.*;
import java.io.*;
import java.util.*;
import javax.swing.*;

// DeltaReader version 1.0
public class DeltaReader
{
	// File format: every file starts with FORMAT_ID ('D' = Delta format) and
	// FORMAT_VERSION. Bump FORMAT_VERSION in the writer and reader together
	// whenever the file layout or a coder's output changes.
	static final char FORMAT_ID      = 'D';
	static final int  FORMAT_VERSION = 1;

	static final String[] ENTROPY_NAMES  = {"LZ77", "Huffman", "Arithmetic", "Adaptive", "Context"};
	static final String[] COMPRESS_NAMES = {"Integer", "String", "String*", "String* (DeltaWriter2)"};

	// ---- Header -------------------------------------------------------------
	// compress_type: 0 Integer (one byte per delta), 1 String, 2 String*,
	// 3 String* from DeltaWriter2 (read like 2).
	int     xdim, ydim;
	int     pixel_shift, pixel_quant, set_id, delta_type, compress_type, entropy_type, scanline5_variant;
	int     pixel_pyramid;
	boolean use_saddle;

	// ---- Per channel, as read -----------------------------------------------
	int[]         min = new int[3], init = new int[3], delta_min = new int[3], length = new int[3], compressed_length = new int[3];
	int[][]       table    = new int[3][];
	byte[][]      map      = new byte[3][];
	boolean[][][] sign_bit = new boolean[3][][];  // pyramid sign bits, one bitmap per level
	byte[][]      coded    = new byte[3][];       // LZ77 (already inflated), Huffman and Adaptive payloads
	int[][]       delta    = new int[3][];        // Context (4): decoded while reading
	byte[][]      huffman_lengths = new byte[3][];
	int[][][]     freqs  = new int[3][][];        // Arithmetic: one table per block
	byte[][][]    blocks = new byte[3][][];

	BufferedImage decoded_image = null;
	ViewerSupport view;

	public static void main(String[] args)
	{
		ViewerSupport.applyHiDpiFontScaleIfNeeded();
		if(args.length != 1) { System.out.println("Usage: java DeltaReader <filename>"); System.exit(0); }
		new DeltaReader(args[0]);
	}

	public DeltaReader(String filename)
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
				view = new ViewerSupport("Delta Reader  " + filename, xdim, ydim);
				view.frame.getJMenuBar().add(view.makeViewMenu());
				view.setStatus("decoding…");
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
				? filename + " is not a Delta Writer file."
				: filename + " is Delta format version " + version + "; this reader reads version " + FORMAT_VERSION + ".");
		xdim              = in.readUnsignedShort();
		ydim              = in.readUnsignedShort();
		pixel_shift       = in.readByte();
		pixel_quant       = in.readByte();
		set_id            = in.readByte();
		delta_type        = in.readByte();
		compress_type     = in.readByte();
		entropy_type      = in.readByte();
		scanline5_variant = in.readByte();
		pixel_pyramid     = in.readByte();
		use_saddle        = in.readByte() != 0;

		System.out.println("Image:        " + xdim + " x " + ydim);
		System.out.println("Channel set:  " + DeltaMapper.SET_NAMES[set_id]);
		System.out.println("Delta type:   " + DeltaMapper.DELTA_TYPE_NAMES[delta_type]);
		System.out.println("Datatype:     " + COMPRESS_NAMES[compress_type]);
		System.out.println("Entropy type: " + ENTROPY_NAMES[entropy_type]);
		System.out.println();

		for(int i = 0; i < 3; i++)
		{
			min[i]               = in.readInt();
			init[i]              = in.readInt();
			delta_min[i]         = in.readInt();
			length[i]            = in.readInt();
			compressed_length[i] = in.readInt();
			in.readByte();   // iterations (the string carries them too)
			if(DeltaMapper.hasMap(delta_type)) map[i] = DeltaMapper.readMap(in, delta_type, (i > 0) ? map[i - 1] : null, getPyramidSize(DeltaMapper.getQuantizedSize(xdim, ydim, pixel_quant)[0], DeltaMapper.getQuantizedSize(xdim, ydim, pixel_quant)[1], pixel_pyramid)[0]);
			if(pixel_pyramid != 0) sign_bit[i] = readSignBits(in, pixel_pyramid);
			if(entropy_type == 4)        // Context: decoded here, in channel order
			{
				int[] size = DeltaMapper.getQuantizedSize(xdim, ydim, pixel_quant);
				int[] top  = getPyramidSize(size[0], size[1], pixel_pyramid);
				delta[i] = DeltaMapper.readContextDeltas(in, top[0] * top[1], Arrays.copyOf(delta, i), top[0]);
				continue;
			}
			if(compress_type > 0) table[i] = DeltaMapper.readTable(in);

			if(entropy_type == 0)        // LZ77: payload length, Deflated length, Deflated bytes
			{
				int n = in.readInt();
				byte[] zipped = new byte[in.readInt()];
				in.readFully(zipped);
				coded[i] = CodeMapper.inflate(zipped, n);
			}
			else if(entropy_type == 1)   // Huffman: code lengths (Deflated), coded payload
			{
				byte[] packed = new byte[in.readInt()];
				in.readFully(packed);
				huffman_lengths[i] = CodeMapper.unpackRegularTables(packed, 1)[0];
				coded[i] = new byte[in.readInt()];
				in.readFully(coded[i]);
			}
			else if(entropy_type == 2)   // Arithmetic: frequency tables, then the coded blocks
			{
				freqs[i]  = ArithmeticMapper.readFrequencies(in);
				blocks[i] = new byte[freqs[i].length][];
				for(int k = 0; k < blocks[i].length; k++)
				{
					blocks[i][k] = new byte[in.readInt()];
					in.readFully(blocks[i][k]);
				}
			}
			else                         // Adaptive: the coded payload
			{
				coded[i] = new byte[in.readInt()];
				in.readFully(coded[i]);
			}
		}
	}

	// DeltaWriter's sign bits: one bitmap per pyramid level, each an int
	// length, then (length+7)/8 bytes, bit q in byte q>>3, bit q&7.
	private static boolean[][] readSignBits(DataInputStream in, int levels) throws IOException
	{
		boolean[][] sign_bits = new boolean[levels][];
		for(int lvl = 0; lvl < levels; lvl++)
		{
			boolean[] bits   = new boolean[in.readInt()];
			byte[]    packed = new byte[(bits.length + 7) / 8];
			in.readFully(packed);
			for(int q = 0; q < bits.length; q++) bits[q] = (packed[q >> 3] & (1 << (q & 7))) != 0;
			sign_bits[lvl] = bits;
		}
		return sign_bits;
	}

	// Entropy decode -> deltas (bytes or unary strings) -> channel values,
	// then the pyramid expand if there is one.
	private int[] decodeChannel(int i)
	{
		try
		{
			int[]  size = DeltaMapper.getQuantizedSize(xdim, ydim, pixel_quant);
			int[]  top  = getPyramidSize(size[0], size[1], pixel_pyramid);
			int    n    = top[0] * top[1];
			if(entropy_type == 4) return toChannel(i, delta[i], size, top);
			int    payload_length = (compress_type == 0) ? n : StringMapper.getBytelength(compressed_length[i]);
			byte[] payload;
			if(entropy_type == 0)      payload = coded[i];
			else if(entropy_type == 1) payload = CodeMapper.unpackRegularCode(coded[i], huffman_lengths[i], payload_length);
			else if(entropy_type == 3) payload = ArithmeticMapper.getArithmeticValuesAdaptive(coded[i], payload_length);
			else
			{
				int[]    block_length = ArithmeticMapper.getBlockLengths(payload_length, freqs[i].length);
				byte[][] block = new byte[block_length.length][];
				ViewerSupport.parallel(block.length, k ->
					block[k] = ArithmeticMapper.getArithmeticValuesFastFenwick(blocks[i][k], freqs[i][k], block_length[k]));
				payload = ArithmeticMapper.joinBlocks(block);
			}

			int[] delta;
			if(compress_type == 0)
			{
				// One unsigned byte per delta: delta - delta_min.
				delta = new int[n];
				for(int k = 1; k < n; k++) delta[k] = (payload[k] & 0xFF) + delta_min[i];
			}
			else
			{
				byte[] str = StringMapper.decompressStrings(payload);
				delta = StringMapper.unpackStrings(str, table[i], n, length[i]);
				delta[0] = 0;
				for(int k = 1; k < delta.length; k++) delta[k] += delta_min[i];
			}

			return toChannel(i, delta, size, top);
		}
		catch(Exception e) { throw new RuntimeException("channel " + i + ": " + e, e); }
	}

	private int[] toChannel(int i, int[] delta, int[] size, int[] top)
	{
		int[]   channel    = DeltaMapper.getValuesFromDeltas(delta, top[0], top[1], init[i], delta_type, map[i], scanline5_variant);
		boolean difference = DeltaMapper.getChannels(set_id)[i] > 2;
		if(pixel_pyramid != 0) channel = expandPyramid(channel, size[0], size[1], sign_bit[i], difference, use_saddle);
		if(difference) for(int k = 0; k < channel.length; k++) channel[k] += min[i];
		return channel;
	}

	// ---- Image pyramid (see DeltaWriter) ------------------------------------

	// Size of the top pyramid level, the size the deltas are coded at.
	static int[] getPyramidSize(int xdim, int ydim, int levels)
	{
		if(levels == 0) return new int[] {xdim, ydim};
		int mult = 1 << levels;
		return new int[] {ImageMapper.padTo(xdim, mult) >> levels, ImageMapper.padTo(ydim, mult) >> levels};
	}

	// Inverse of DeltaWriter.shrinkPyramid (DeltaWriter's preview uses it
	// too): expands the top level back to xdim x ydim with sign-bit
	// correction at each level. Difference channels are offset by their minimum but not rescaled, so
	// they range 0-510 (clamping them to 255 would corrupt high-contrast areas).
	static int[] expandPyramid(int[] top, int xdim, int ydim, boolean[][] sign_bits, boolean difference, boolean saddle)
	{
		int levels = sign_bits.length, mult = 1 << levels;
		int max_value = difference ? 510 : 255;
		int[] level = top;
		int   level_xdim = ImageMapper.padTo(xdim, mult) >> levels;
		for(int lvl = levels - 1; lvl >= 0; lvl--)
		{
			int[] predicted = saddle ? ImageMapper.expandGradientSaddle(level, level_xdim, max_value) : ImageMapper.expandGradient(level, level_xdim, max_value);
			level = ImageMapper.refineWithSignBits(level, predicted, sign_bits[lvl], level_xdim * 2, max_value);
			level_xdim *= 2;
		}
		return ImageMapper.crop(level, ImageMapper.padTo(xdim, mult), ImageMapper.padTo(ydim, mult), xdim, ydim);
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
