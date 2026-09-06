import java.awt.image.BufferedImage;
import java.awt.image.PixelGrabber;
import java.io.File;
import java.util.*;
import javax.imageio.ImageIO;

/**
 * Extracts up to 32x32 pixel segments from arbitrary images and converts
 * them into the (symbols, histogram) form SteeredArithmeticMapper /
 * SegmentSteerDemo need -- meant to be a drop-in fit for DeltaWriter.java,
 * not a parallel data format: channel extraction here uses the exact same
 * PixelGrabber + bit-shift convention DeltaWriter.java's own constructor
 * already uses (blue = (pixel>>16)&0xff, green = (pixel>>8)&0xff,
 * red = pixel&0xff), and segment extraction works on any flat int[]
 * channel array with a given xdim/ydim -- so it accepts either a raw
 * pixel channel from loadChannels() below, or one of DeltaWriter's own
 * already-computed delta/quantized channel arrays (quantized_channel_list
 * entries), unchanged.
 */
public class ImageSegmentExtractor
{
	/** One extracted, ready-to-encode segment: the flattened raw values
	 *  (row-major within the segment, actualWidth*actualHeight long --
	 *  segments clipped at image edges may be smaller than requested),
	 *  the compact symbol-index array SteeredArithmeticMapper expects,
	 *  the histogram over that compact alphabet, and rankToValue for
	 *  mapping decoded symbols back to real values afterward. */
	public static final class Segment
	{
		public final int x0, y0;              // top-left corner in the source channel
		public final int actualWidth, actualHeight;
		public final int[] rawValues;          // original values, row-major, length actualWidth*actualHeight
		public final int[] symbols;            // same length, values remapped to compact 0..K-1 indices
		public final int[] histogram;          // length K; histogram[s] = count of symbol s in `symbols`
		public final int[] rankToValue;        // length K; rankToValue[s] = the real value symbol s represents

		Segment(int x0, int y0, int actualWidth, int actualHeight, int[] rawValues,
		        int[] symbols, int[] histogram, int[] rankToValue)
		{
			this.x0 = x0; this.y0 = y0; this.actualWidth = actualWidth; this.actualHeight = actualHeight;
			this.rawValues = rawValues; this.symbols = symbols; this.histogram = histogram; this.rankToValue = rankToValue;
		}
	}

	/** Loads an arbitrary image file and extracts its blue/green/red
	 *  channels as flat int[] arrays, using the exact same PixelGrabber +
	 *  bit-shift convention as DeltaWriter.java's own constructor, so
	 *  channel index 0/1/2 here means the same thing it does there. */
	public static int[][] loadChannels(String filename) throws Exception
	{
		File file = new File(filename);
		BufferedImage image = ImageIO.read(file);
		int xdim = image.getWidth();
		int ydim = image.getHeight();

		int[] pixel = new int[xdim * ydim];
		PixelGrabber pg = new PixelGrabber(image, 0, 0, xdim, ydim, pixel, 0, xdim);
		try { pg.grabPixels(); } catch (InterruptedException e) { throw new RuntimeException(e); }

		int[] blue = new int[xdim * ydim], green = new int[xdim * ydim], red = new int[xdim * ydim];
		for (int i = 0; i < pixel.length; i++)
		{
			blue[i]  = (pixel[i] >> 16) & 0xff;
			green[i] = (pixel[i] >> 8) & 0xff;
			red[i]   = pixel[i] & 0xff;
		}
		return new int[][]{ blue, green, red, { xdim, ydim } }; // dims packed as a trailing pseudo-channel for convenience
	}

	/**
	 * Extracts a segment up to `requestedDim` x `requestedDim` from a flat
	 * channel array (row-major, length xdim*ydim), starting at (x0, y0).
	 * Segments that would run past the image edge are clipped, not
	 * padded or wrapped -- actualWidth/actualHeight in the returned
	 * Segment reflect this, so a segment near the bottom-right corner of
	 * a small image may come back smaller than requested. `requestedDim`
	 * is clamped to 32 regardless of what's passed, matching the scale
	 * this scheme has actually been verified at (see SegmentSteerDemo).
	 *
	 * @param channel      flat source array (raw pixel channel, or one of
	 *                     DeltaWriter's own delta/quantized channel
	 *                     arrays -- either works unchanged)
	 * @param xdim, ydim   dimensions of `channel`
	 * @param x0, y0       top-left corner of the segment to extract
	 * @param requestedDim desired segment width/height, clamped to <= 32
	 */
	public static Segment extractSegment(int[] channel, int xdim, int ydim, int x0, int y0, int requestedDim)
	{
		int dim = Math.max(1, Math.min(32, requestedDim));
		int actualWidth  = Math.min(dim, xdim - x0);
		int actualHeight = Math.min(dim, ydim - y0);
		if (actualWidth <= 0 || actualHeight <= 0)
			throw new IllegalArgumentException("segment origin (" + x0 + "," + y0 + ") is outside the "
				+ xdim + "x" + ydim + " channel");

		int[] rawValues = new int[actualWidth * actualHeight];
		int p = 0;
		for (int y = y0; y < y0 + actualHeight; y++)
			for (int x = x0; x < x0 + actualWidth; x++)
				rawValues[p++] = channel[y * xdim + x];

		// Compact rank mapping: real value -> 0..K-1 symbol index, K = number
		// of DISTINCT values actually present in this segment (not the full
		// possible range), matching the rank-table convention already used
		// elsewhere in this codebase (StringMapper's rank tables) rather than
		// wasting alphabet slots on values that never occur in this segment.
		TreeMap<Integer, Integer> valueCounts = new TreeMap<>();
		for (int v : rawValues) valueCounts.merge(v, 1, Integer::sum);
		int[] rankToValue = new int[valueCounts.size()];
		HashMap<Integer, Integer> valueToRank = new HashMap<>();
		int r = 0;
		for (int v : valueCounts.keySet()) { rankToValue[r] = v; valueToRank.put(v, r); r++; }

		int[] symbols = new int[rawValues.length];
		int[] histogram = new int[rankToValue.length];
		for (int i = 0; i < rawValues.length; i++)
		{
			int s = valueToRank.get(rawValues[i]);
			symbols[i] = s;
			histogram[s]++;
		}

		return new Segment(x0, y0, actualWidth, actualHeight, rawValues, symbols, histogram, rankToValue);
	}

	/** Convenience one-shot: load an image, pick a channel (0=blue,
	 *  1=green, 2=red), and extract one segment -- everything
	 *  SegmentSteerDemo.compressSegment()/decompressSegment() need,
	 *  ready to use. */
	public static Segment extractFromImage(String filename, int channelIndex, int x0, int y0, int requestedDim) throws Exception
	{
		int[][] loaded = loadChannels(filename);
		int[] dims = loaded[3];
		int xdim = dims[0], ydim = dims[1];
		return extractSegment(loaded[channelIndex], xdim, ydim, x0, y0, requestedDim);
	}

	/** Maps decoded symbol indices back to real values, undoing the rank
	 *  mapping applied in extractSegment() -- use this on
	 *  SegmentSteerDemo.decompressSegment()'s output to recover the
	 *  actual pixel/delta values rather than the compact symbol indices. */
	public static int[] symbolsToValues(int[] symbols, int[] rankToValue)
	{
		int[] values = new int[symbols.length];
		for (int i = 0; i < symbols.length; i++) values[i] = rankToValue[symbols[i]];
		return values;
	}
}
