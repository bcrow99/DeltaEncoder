import java.awt.*;
import java.awt.image.*;
import java.io.*;
import java.util.*;
import javax.swing.*;

// SimpleReader version 1.0
public class SimpleReader
{
	// File format: every file starts with FORMAT_ID ('S' = Simple format) and
	// FORMAT_VERSION. Bump FORMAT_VERSION in the writer and reader together
	// whenever the file layout or a coder's output changes.
	static final char FORMAT_ID      = 'S';
	static final int  FORMAT_VERSION = 1;

	static final String[] ENTROPY_NAMES = {"LZ77", "Huffman", "Arithmetic", "Adaptive", "Context"};

	// ---- Header -------------------------------------------------------------
	// There is no compress_type: SimpleWriter always writes unary strings.
	int xdim, ydim;
	int pixel_shift, pixel_quant, set_id, delta_type, entropy_type, scanline5_variant;

	// ---- Per channel, as read -----------------------------------------------
	int[]      min = new int[3], init = new int[3], delta_min = new int[3], length = new int[3], compressed_length = new int[3];
	int[][]    table = new int[3][];
	byte[][]   map   = new byte[3][];
	byte[][]   coded = new byte[3][];     // LZ77 (already inflated), Huffman and Adaptive payloads
	byte[][]   huffman_lengths = new byte[3][];
	int[][][]  freqs  = new int[3][][];   // Arithmetic: one table per block
	byte[][][] blocks = new byte[3][][];
	int[][]    delta  = new int[3][];     // Context: decoded while reading

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
				? filename + " is not a Simple Writer file."
				: filename + " is Simple format version " + version + "; this reader reads version " + FORMAT_VERSION + ".");
		xdim              = in.readUnsignedShort();
		ydim              = in.readUnsignedShort();
		pixel_shift       = in.readByte();
		pixel_quant       = in.readByte();
		set_id            = in.readByte();
		delta_type        = in.readByte();
		entropy_type      = in.readByte();
		scanline5_variant = in.readByte();

		System.out.println("Image:        " + xdim + " x " + ydim);
		System.out.println("Channel set:  " + DeltaMapper.SET_NAMES[set_id]);
		System.out.println("Delta type:   " + DeltaMapper.DELTA_TYPE_NAMES[delta_type]);
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
			if(DeltaMapper.hasMap(delta_type)) map[i] = DeltaMapper.readMap(in, delta_type, (i > 0) ? map[i - 1] : null, DeltaMapper.getQuantizedSize(xdim, ydim, pixel_quant)[0]);
			if(entropy_type != 4) table[i] = DeltaMapper.readTable(in);

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
			else if(entropy_type == 4)   // Context: decoded here, in channel order
			{
				int[] size = DeltaMapper.getQuantizedSize(xdim, ydim, pixel_quant);
				delta[i] = DeltaMapper.readContextDeltas(in, size[0] * size[1], Arrays.copyOf(delta, i), size[0]);
			}
			else                         // Adaptive: the coded payload
			{
				coded[i] = new byte[in.readInt()];
				in.readFully(coded[i]);
			}
		}
	}

	// Entropy decode -> unary strings -> deltas -> channel values.
	private int[] decodeChannel(int i)
	{
		try
		{
			int[]  size = DeltaMapper.getQuantizedSize(xdim, ydim, pixel_quant);
			if(entropy_type == 4) return toChannel(i, delta[i], size);
			int    payload_length = StringMapper.getBytelength(compressed_length[i]);
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

			byte[] str   = StringMapper.decompressStrings(payload);
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
