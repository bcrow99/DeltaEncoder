import java.awt.*;
import java.awt.image.*;
import java.io.*;
import java.util.*;
import javax.swing.*;

// BlockReader version 2.5 (reads Block formats 3 to 10; 5: mixed context
// coding, a block size per channel; 6: bands of rows, decoded at the same
// time; 7 and 8: 5 and 6 with the mixed coder's second opinions; 9 and 10:
// 7 and 8 with the 225-entry predictor table)
//
// Reads BlockWriter's files: block map deltas (delta type 13), Context
// coded (format version 3) or mixed-context coded (versions 4 and 5; in 5
// each channel has its own block size). See
// BlockWriter.SaveHandler.save for the layout.
public class BlockReader
{
	static final char FORMAT_ID      = 'B';
	static final int  FORMAT_VERSION = 10;  // 3: one predictor table (DeltaMapper.PREDICTOR_TABLE_256) and cross-channel block maps; 4: mixed context coding; 5: a block size per channel; 6: independently decodable bands; 7 and 8: 5 and 6 with the mixed coder's second opinions; 9 and 10: 7 and 8 with the 225-entry predictor table (DeltaMapper.PREDICTOR_TABLE)
	/**
	 * The oldest format version read (the block predictors changed in version 3).
	 */
	static final int  OLDEST_VERSION = 3;

	int xdim, ydim, pixel_shift, pixel_quant, set_id;
	int[]    min   = new int[3], init = new int[3];
	byte[][] map   = new byte[3][];
	int[][]  delta = new int[3][];
	/**
	 * The file's format version.
	 */
	int      version;
	/**
	 * Version 4: each channel's deltas as read, decoded as the channel is rebuilt.
	 */
	DeltaMapper.MixedDeltas[] mixed = new DeltaMapper.MixedDeltas[3];
	/**
	 * Version 6: the number of bands, and each band's channels' first values,
	 * block maps and coded deltas.
	 */
	int bands = 1;
	/**
	 * Version 6: each band's channels' first values.
	 */
	int[][] band_init;
	/**
	 * Version 6: each band's channels' block maps.
	 */
	byte[][][] band_map;
	/**
	 * Version 6: each band's channels' coded deltas.
	 */
	DeltaMapper.MixedDeltas[][] band_mixed;

	BufferedImage decoded_image = null;
	ViewerSupport view;

	public static void main(String[] args)
	{
		ViewerSupport.applyHiDpiFontScaleIfNeeded();
		if(args.length != 1) { System.out.println("Usage: java BlockReader <filename>"); System.exit(0); }
		new BlockReader(args[0]);
	}

	public BlockReader(String filename)
	{
		try
		{
			long start = System.nanoTime();
			try(DataInputStream in = new DataInputStream(new BufferedInputStream(new FileInputStream(filename))))
			{
				read(in, filename);
			}
			System.out.println("File read and entropy decoded in " + ((System.nanoTime() - start) / 1_000_000) + " ms.");

			ViewerSupport.runOnEdt(() ->
			{
				view = new ViewerSupport("Block Reader  " + filename, xdim, ydim);
				view.frame.getJMenuBar().add(view.makeViewMenu());
				view.setStatus("decoding...");
				view.show();
			});

			start = System.nanoTime();
			int[]   size    = DeltaMapper.getQuantizedSize(xdim, ydim, pixel_quant);
			int[]   id      = DeltaMapper.getChannels(set_id);
			int[][] channel = new int[3][];
			if(banded()) decodeBands(channel, size[0], size[1]);
			// In order: each channel's predictors may use the one before it.
			else for(int i = 0; i < 3; i++)
			{
				int[] ref = (i > 0) ? channel[i - 1] : null;
				if(version >= 4)
				{
					int[][] r = DeltaMapper.getValuesFromMixedDeltas(mixed[i], Arrays.copyOf(delta, i), size[0], size[1], init[i], map[i], ref, version >= 7, table());
					delta[i]   = r[0];
					channel[i] = r[1];
				}
				else channel[i] = DeltaMapper.getValuesFromDeltas(delta[i], size[0], size[1], init[i], 13, map[i], 0, ref, table());
			}
			for(int i = 0; i < 3; i++) if(id[i] > 2) for(int k = 0; k < channel[i].length; k++) channel[i][k] += min[i];
			int[][] bgr = DeltaMapper.getBlueGreenRed(set_id, channel[0], channel[1], channel[2]);
			if(pixel_quant != 0) ViewerSupport.parallel(3, c -> bgr[c] = ResizeMapper.resize(bgr[c], size[0], xdim, ydim));
			BufferedImage image = new BufferedImage(xdim, ydim, BufferedImage.TYPE_INT_RGB);
			image.setRGB(0, 0, xdim, ydim, DeltaMapper.getPixel(bgr[0], bgr[1], bgr[2], xdim, pixel_shift), 0, xdim);
			decoded_image = image;
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

	/**
	 * Whether the file is in bands (formats 6, 8 and 10).
	 *
	 * @return whether it is
	 */
	private boolean banded() { return version == 6 || version == 8 || version == 10; }

	/**
	 * The table the file's block maps index: PREDICTOR_TABLE from format 9,
	 * PREDICTOR_TABLE_256 before.
	 *
	 * @return the table
	 */
	private DeltaMapper.PredictorTable table() { return (version >= 9) ? DeltaMapper.PREDICTOR_TABLE : DeltaMapper.PREDICTOR_TABLE_256; }

	private void read(DataInputStream in, String filename) throws IOException
	{
		int id = in.readUnsignedByte();
		version = in.readUnsignedByte();
		if(id != FORMAT_ID || version < OLDEST_VERSION || version > FORMAT_VERSION)
			throw new IOException(id != FORMAT_ID
				? filename + " is not a Block Writer file."
				: filename + " is Block format version " + version + "; this reader reads versions " + OLDEST_VERSION + " to " + FORMAT_VERSION
				  + " (the block predictors changed in version " + OLDEST_VERSION + ").");
		xdim        = in.readUnsignedShort();
		ydim        = in.readUnsignedShort();
		pixel_shift = in.readByte();
		pixel_quant = in.readByte();
		set_id      = in.readByte();
		int[] size  = DeltaMapper.getQuantizedSize(xdim, ydim, pixel_quant);
		if(banded()) { readBands(in, size[0], size[1]); return; }

		for(int i = 0; i < 3; i++)
		{
			min[i]   = in.readInt();
			init[i]  = in.readInt();
			map[i]   = DeltaMapper.readMap(in, 13, (i > 0) ? map[i - 1] : null, size[0]);
			if(version >= 4) mixed[i] = DeltaMapper.readMixedDeltas(in);   // decoded as the channel is rebuilt
			else             delta[i] = DeltaMapper.readContextDeltas(in, size[0] * size[1], Arrays.copyOf(delta, i), size[0]);
		}
		System.out.println("Image:        " + xdim + " x " + ydim);
		System.out.print(DeltaMapper.describeBlockMaps(set_id, map, size[0], table()));
		System.out.println();
	}

	/**
	 * Formats 6, 8 and 10 (see BlockWriter.SaveHandler.inBands): the bands, read (maps
	 * decoded, deltas not yet).
	 *
	 * @param in the stream, after the header's channel set
	 * @param w  the quantized width
	 * @param h  the quantized height
	 * @throws IOException if the stream fails or the bands don't fit the image
	 */
	private void readBands(DataInputStream in, int w, int h) throws IOException
	{
		bands = in.readUnsignedByte();
		if(bands < 1 || h / bands < 2) throw new IOException("A Block file with " + bands + " bands of a " + h + "-row image: not one this version reads.");
		for(int i = 0; i < 3; i++) min[i] = in.readInt();
		int[] length = new int[bands];
		for(int b = 0; b < bands; b++) length[b] = in.readInt();
		band_init  = new int[bands][3];
		band_map   = new byte[bands][3][];
		band_mixed = new DeltaMapper.MixedDeltas[bands][3];
		for(int b = 0; b < bands; b++)
		{
			byte[] bytes = new byte[length[b]];
			in.readFully(bytes);
			DataInputStream bi = new DataInputStream(new ByteArrayInputStream(bytes));
			for(int i = 0; i < 3; i++)
			{
				band_init[b][i]  = bi.readInt();
				band_map[b][i]   = DeltaMapper.readMap(bi, 13, (i > 0) ? band_map[b][i - 1] : null, w);
				band_mixed[b][i] = DeltaMapper.readMixedDeltas(bi);
			}
		}
		// The listing: each channel's maps over all the bands (the same block
		// size and predictor set in every band).
		for(int i = 0; i < 3; i++)
		{
			int head = DeltaMapper.mapHeaderLength(band_map[0][i]), n = head;
			for(int b = 0; b < bands; b++) n += band_map[b][i].length - head;
			map[i] = Arrays.copyOf(band_map[0][i], n);
			for(int b = 1, at = band_map[0][i].length; b < bands; at += band_map[b][i].length - head, b++)
				System.arraycopy(band_map[b][i], head, map[i], at, band_map[b][i].length - head);
		}
		System.out.println("Image:        " + xdim + " x " + ydim);
		System.out.print(DeltaMapper.describeBlockMaps(set_id, map, w, table()));
		System.out.println(BlockWriter.describeBands(bands, h));
		System.out.println();
	}

	/**
	 * Formats 6, 8 and 10: the bands decoded at the same time, into the channels.
	 *
	 * @param channel set to the three channels, in coding order
	 * @param w       the quantized width
	 * @param h       the quantized height
	 */
	private void decodeBands(int[][] channel, int w, int h)
	{
		for(int i = 0; i < 3; i++) channel[i] = new int[w * h];
		java.util.stream.IntStream.range(0, bands).parallel().forEach(b ->
		{
			int y0 = BlockWriter.bandStart(b, bands, h), y1 = BlockWriter.bandStart(b + 1, bands, h);
			int[][] d = new int[3][], v = new int[3][];
			for(int i = 0; i < 3; i++)
			{
				int[][] r = DeltaMapper.getValuesFromMixedDeltas(band_mixed[b][i], Arrays.copyOf(d, i), w, y1 - y0, band_init[b][i], band_map[b][i], (i > 0) ? v[i - 1] : null, version >= 7, table());
				d[i] = r[0];
				v[i] = r[1];
				System.arraycopy(v[i], 0, channel[i], y0 * w, v[i].length);
			}
		});
	}
}
