import java.awt.*;
import java.awt.image.*;
import java.io.*;
import java.util.*;
import javax.swing.*;

// BlockReader version 2.1 (reads Block format 4, mixed context coding)
//
// Reads BlockWriter's files: block map deltas (delta type 13), Context
// coded (format version 3) or mixed-context coded (version 4). See
// BlockWriter.SaveHandler.save for the layout.
public class BlockReader
{
	static final char FORMAT_ID      = 'B';
	static final int  FORMAT_VERSION = 4;   // 3: one predictor table (DeltaMapper.PREDICTOR_TABLE) and cross-channel block maps; 4: mixed context coding
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
			// In order: each channel's predictors may use the one before it.
			for(int i = 0; i < 3; i++)
			{
				int[] ref = (i > 0) ? channel[i - 1] : null;
				if(version >= 4)
				{
					int[][] r = DeltaMapper.getValuesFromMixedDeltas(mixed[i], Arrays.copyOf(delta, i), size[0], size[1], init[i], map[i], ref);
					delta[i]   = r[0];
					channel[i] = r[1];
				}
				else channel[i] = DeltaMapper.getValuesFromDeltas(delta[i], size[0], size[1], init[i], 13, map[i], 0, ref);
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

		for(int i = 0; i < 3; i++)
		{
			min[i]   = in.readInt();
			init[i]  = in.readInt();
			map[i]   = DeltaMapper.readMap(in, 13, (i > 0) ? map[i - 1] : null, size[0]);
			if(version >= 4) mixed[i] = DeltaMapper.readMixedDeltas(in);   // decoded as the channel is rebuilt
			else             delta[i] = DeltaMapper.readContextDeltas(in, size[0] * size[1], Arrays.copyOf(delta, i), size[0]);
		}
		System.out.println("Image:        " + xdim + " x " + ydim);
		System.out.println("Channel set:  " + DeltaMapper.SET_NAMES[set_id]);
		System.out.println("Block map:    block " + DeltaMapper.shapeName(map[0][0] & 0xFF) + "; predictors " + DeltaMapper.describeMapSet(map[0]) + ", " + DeltaMapper.describeMapSet(map[1]) + ", " + DeltaMapper.describeMapSet(map[2]));
		System.out.println();
	}
}
