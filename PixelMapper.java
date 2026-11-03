import java.util.*;

// PixelMapper version 1.4
//
// Codes the pixels of the red, green and blue channels with context-
// adaptive arithmetic coding, for PixelWriter and PixelReader.
//
// The image is divided into square blocks (smaller at the right and bottom
// edges), coded in rows of blocks, and the pixels of each block row by row.
// Each channel is one arithmetic-coded stream whose models restart at the
// start of every block, so a block is coded with what was learned inside
// it only. Neighbours, though, are taken across block edges: they are
// already decoded when the block is reached.
//
// The channels are coded green first, then red and blue or their
// differences from green (CHANNEL_SET_NAMES). Every coding starts from a
// prediction (PREDICTION_NAMES): the median edge predictor (MED) of the
// left, upper and upper-left neighbours, or least squares (DeltaMapper's
// LeastSquares, the wide clamp, as Simple codes with). With MED and red,
// green and blue, the prediction is corrected by how far the earlier
// channels at the same pixel were from their own MED predictions (the
// average of those errors): the channels move together, so this removes
// much of the prediction error of the second and third channels. (Least
// squares fits from the previous channel itself.) "Activity" is how busy
// the neighbourhood is: |left - upper-left| + |upper - upper-left| on the
// 0-255 scale.
//
// With MED the blocks are coded one after another, each row by row. Least
// squares fits from pixels above and to the right of the pixel, which in
// that order may be in the next block, not yet decoded; so with least
// squares the pixels are coded in raster order instead, each block keeping
// its own models while the rows go through it -- the same models, in the
// same order within each block, so the same probabilities.
//
// Three codings (CODING_NAMES):
//
//   0 Values, exact prediction: the pixel value itself, in a context of the
//     predicted value (every value its own context) x 3 activity bins.
//     Many contexts, each learning its own distribution.
//   1 Values, brightness bands: the pixel value itself, in a context of the
//     predicted value grouped into bands of 4 (on the 0-255 scale; at Color
//     Resolution 2 and up a band is one value) x 2 activity bins. Fewer
//     contexts, so each one learns faster.
//   2 Errors: value - prediction (wrapped into 0..max_value), in a context
//     of 6 activity bins x 5 bins of how large the earlier channels' errors
//     were at the pixel. 30 contexts, all sharing the same peaked shape --
//     delta coding, with the models restarting at each block.
//
// Counts start at 1 and go up by INCREMENT per symbol coded; a context
// whose total passes LIMIT has its counts halved.
//
// Measured with PixelTest and PixelTest2 on 7 photographs: total bytes with
// blocks of 256 (the best size for all three codings at both settings
// below, among sizes up to 256):
//
//                                  lossless    Color Resolution 3
//   Values, exact prediction       5.19 MB          1.86 MB
//   Values, brightness bands       4.98 MB          1.85 MB
//   Errors                         4.60 MB          1.91 MB
//   DeltaWriter (block map +
//     Context), for comparison     4.08 MB          1.73 MB
//
// Lossless, values with the exact prediction keep improving past 256: the
// whole image as one block gave 4.71 MB.
//
// Other findings: correcting the prediction from the earlier channels saved
// 12-15%; LIMIT 2^13 adapted better than 2^16, and INCREMENT 32 was about as
// good as 16 or 64; the bands help most with small blocks (16-64), where
// the exact prediction's many contexts have too few pixels to learn from.
public class PixelMapper
{
	public static final int BLOCK_MIN = 8, BLOCK_MAX = 256;
	public static final int INCREMENT = 32;
	public static final int LIMIT     = 1 << 13;

	public static final int VALUES = 0, BANDS = 1, ERRORS = 2;
	public static final String[] CODING_NAMES = {"Values, exact prediction", "Values, brightness bands", "Errors (value - prediction)"};

	// Activity bins (lower limits, 0-255 scale) for each coding, and the bins
	// for the size of the earlier channels' errors (Errors only).
	static final int[][] ACTIVITY_LIMIT = { {0, 5, 13}, {0, 5}, {0, 2, 5, 10, 20, 40} };
	static final int[]   CROSS_LIMIT    = {0, 2, 5, 12, 30};
	static final int     BAND_BITS      = 2;   // bands of 4 on the 0-255 scale

	public static final String[] CHANNEL_NAMES = {"red", "green", "blue"};

	/**
	 * The channel sets (CHANNEL_SET_NAMES):
	 * <ul>
	 * <li>PRIMARY: green, red, blue, with (MED) red's and blue's predictions
	 * corrected by the earlier channels' errors (see above).
	 * <li>DIFFERENCE: green, red - green, blue - green, each difference plus
	 * max_value so it is never negative (format versions 3 and 4;
	 * GREEN_DIFFERENCES replaces it in the writer).
	 * <li>GREEN_DIFFERENCES: green, blue - green, red - green (Simple 2's
	 * set), each difference less its minimum, so a channel's values run from
	 * 0 to the difference's range only: a smaller alphabet, whose counts (all
	 * starting at 1 in every block) waste less. The differences aren't
	 * corrected: they already remove what green shares with red and blue.
	 * </ul>
	 * Measured on bluejay, potter, finch and owl1 (blocks of 256, MED):
	 * DIFFERENCE was 6-12% smaller than PRIMARY lossless, 2-6% at Pixel
	 * Resolution 4 and Color Resolution 3, and GREEN_DIFFERENCES 2.3-4.2%
	 * smaller than DIFFERENCE lossless, 0.7-0.9% at q4 s3. With least squares
	 * instead of MED (GREEN_DIFFERENCES), 0.6-3.3% smaller lossless and 8-10%
	 * at q4 s3.
	 */
	public static final int PRIMARY = 0, DIFFERENCE = 1, GREEN_DIFFERENCES = 2;
	/**
	 * The channel sets' names.
	 */
	public static final String[] CHANNEL_SET_NAMES = {"Red, green, blue (corrected)", "Green, red - green, blue - green (plus the maximum)", "Green, blue - green, red - green"};
	/**
	 * The sets the writer offers, in its menu's order.
	 */
	public static final int[] WRITER_CHANNEL_SETS = {GREEN_DIFFERENCES, PRIMARY};

	/**
	 * Each set's coding order, as indexes into {red, green, blue} (the
	 * writers' channel order: red holds bits 16-23 of a pixel); after the
	 * first, each is coded less green in the difference sets.
	 */
	static final int[][] ORDER = { {1, 0, 2}, {1, 0, 2}, {1, 2, 0} };

	/**
	 * The prediction (PREDICTION_NAMES): MED, or least squares with the
	 * window radius LS_RADIUS (stored in the file).
	 */
	public static final int MED = 0, LS = 1;
	/**
	 * The predictions' names.
	 */
	public static final String[] PREDICTION_NAMES = {"Median edge (MED)", "Least squares"};
	/**
	 * The least-squares window radius the writer uses.
	 */
	public static final int LS_RADIUS = 8;

	// The block sizes init() tries: powers of 2, then (if the largest is the
	// best) doubling up to the whole frame, then the steps halfway (by ratio)
	// around the best of those.
	public static final int[] SEARCH_SIZES = {8, 16, 32, 64, 128, 256};

	/**
	 * The largest block size worth having for an xdim x ydim image (the Block
	 * Size slider's maximum): one block covering the whole frame, since any
	 * larger size codes the same, but never less than BLOCK_MAX.
	 *
	 * @param xdim the width
	 * @param ydim the height
	 * @return the block size
	 */
	public static int blockLimit(int xdim, int ydim)
	{
		return Math.max(BLOCK_MAX, Math.max(xdim, ydim));
	}

	// ---- The channels as coded ------------------------------------------------

	/**
	 * The three channels as they are coded, and what doesn't depend on the
	 * block size or the coding (prepare): so the block size search makes
	 * them, and the least-squares predictions, once.
	 */
	public static final class Prepared
	{
		/**
		 * The width and height, the channel set, the prediction and the
		 * least-squares window radius.
		 */
		final int xdim, ydim, channels, prediction, radius = LS_RADIUS;
		/**
		 * The coded channels, in coding order, each 0..max[i].
		 */
		final int[][] v;
		/**
		 * What was subtracted from each to make it so (a difference's
		 * minimum, or minus max_value for DIFFERENCE; 0 for the others), and
		 * its largest value: both go in the file.
		 */
		final int[] offset, max;
		/**
		 * With least squares, each channel's prediction at each pixel (-1 at
		 * the edges, where MED predicts); null with MED.
		 */
		final int[][] pred;
		/**
		 * The first two channels' prediction errors (the later channels'
		 * contexts use them).
		 */
		final int[][] error = new int[3][];

		/**
		 * The prepared channels (prepare makes them).
		 *
		 * @param xdim       the width
		 * @param ydim       the height
		 * @param channels   the channel set
		 * @param prediction MED or LS
		 * @param v          the coded channels
		 * @param offset     what was subtracted from each
		 * @param max        each one's largest value
		 * @param pred       the least-squares predictions, or null
		 */
		Prepared(int xdim, int ydim, int channels, int prediction, int[][] v, int[] offset, int[] max, int[][] pred)
		{
			this.xdim = xdim; this.ydim = ydim; this.channels = channels; this.prediction = prediction;
			this.v = v; this.offset = offset; this.max = max; this.pred = pred;
		}

		/**
		 * The least-squares window radius, for the file.
		 *
		 * @return the radius
		 */
		public int radius() { return radius; }

		/**
		 * Each coded channel's offset, for the file.
		 *
		 * @return the offsets, in coding order
		 */
		public int[] offset() { return offset.clone(); }

		/**
		 * Each coded channel's largest value, for the file.
		 *
		 * @return the largest values, in coding order
		 */
		public int[] max() { return max.clone(); }
	}

	/**
	 * The channels (values 0..max_value, red, green, blue) as the channel set
	 * codes them, with the least-squares predictions if asked for.
	 *
	 * @param channel    red, green and blue
	 * @param xdim       the width
	 * @param ydim       the height
	 * @param max_value  the largest value
	 * @param channels   the channel set (PRIMARY, DIFFERENCE or GREEN_DIFFERENCES)
	 * @param prediction MED or LS
	 * @return the prepared channels
	 */
	public static Prepared prepare(int[][] channel, int xdim, int ydim, int max_value, int channels, int prediction)
	{
		int[]   order  = ORDER[channels];
		int[][] v      = new int[3][];
		int[]   offset = new int[3], max = new int[3];
		for(int i = 0; i < 3; i++)
		{
			int[] c = channel[order[i]];
			if(i == 0 || channels == PRIMARY) { v[i] = c; max[i] = max_value; continue; }
			int[] green = channel[order[0]], d = new int[c.length];
			for(int k = 0; k < d.length; k++) d[k] = c[k] - green[k];
			int lo = -max_value, hi = max_value;   // DIFFERENCE: offset by max_value
			if(channels == GREEN_DIFFERENCES)
			{
				lo = Integer.MAX_VALUE; hi = Integer.MIN_VALUE;
				for(int x : d) { if(x < lo) lo = x; if(x > hi) hi = x; }
				if(d.length == 0) { lo = 0; hi = 0; }
			}
			for(int k = 0; k < d.length; k++) d[k] -= lo;
			v[i] = d; offset[i] = lo; max[i] = hi - lo;
		}
		int[][] pred = null;
		if(prediction == LS)
		{
			pred = new int[3][];
			int[][] p = pred;
			ViewerSupport.parallel(3, i -> p[i] = leastSquaresPrediction(v[i], (i > 0) ? v[i - 1] : null, xdim, ydim, max[i]));
		}
		Prepared pr = new Prepared(xdim, ydim, channels, prediction, v, offset, max, pred);
		for(int i = 0; i < 2; i++) pr.error[i] = predictionError(v[i], (pred == null) ? null : pred[i], xdim, ydim, max[i]);
		return pr;
	}

	/**
	 * A channel's least-squares predictions (LS wide, clamped to 0..max), -1
	 * at the edges.
	 *
	 * @param v    the channel
	 * @param ref  the channel before it, or null for the first
	 * @param xdim the width
	 * @param ydim the height
	 * @param max  its largest value
	 * @return the predictions
	 */
	private static int[] leastSquaresPrediction(int[] v, int[] ref, int xdim, int ydim, int max)
	{
		int[] p = new int[v.length];
		Arrays.fill(p, -1);
		if(xdim < 3 || ydim < 2) return p;
		short[] wide = DeltaMapper.LeastSquares.plane(v, ref, xdim, ydim, LS_RADIUS)[1];
		for(int y = 1; y < ydim; y++)
			for(int x = 1; x < xdim - 1; x++) { int k = y * xdim + x; p[k] = clamp(wide[k], max); }
		return p;
	}

	// ---- Coding ---------------------------------------------------------------

	/**
	 * Codes the prepared channels. The channels are coded in parallel: the
	 * encoder has all of them.
	 *
	 * @param pr     the channels (prepare)
	 * @param shift  the Color Resolution (the contexts' bins are on the 0-255 scale)
	 * @param block  the block size
	 * @param coding VALUES, BANDS or ERRORS
	 * @return the coded bytes per channel, in coding order
	 */
	public static byte[][] encode(Prepared pr, int shift, int block, int coding)
	{
		boolean correct = (pr.channels == PRIMARY && pr.prediction == MED);
		byte[][] coded  = new byte[3][];
		ViewerSupport.parallel(3, i -> coded[i] = (pr.pred == null)
			? encodeChannel(pr.v[i], i, pr.error, pr.xdim, pr.ydim, pr.max[i], shift, block, coding, correct)
			: encodeRaster(pr.v[i], i, pr.error, pr.pred[i], pr.xdim, pr.ydim, pr.max[i], shift, block, coding));
		return coded;
	}

	/**
	 * Decodes what encode() made from a file of format version 4 or before:
	 * MED, and the channel set's offsets and largest values (channelMax).
	 *
	 * @param coded     the coded bytes per channel, in coding order
	 * @param xdim      the width
	 * @param ydim      the height
	 * @param max_value the largest value
	 * @param shift     the Color Resolution
	 * @param block     the block size
	 * @param coding    the coding
	 * @param channels  PRIMARY or DIFFERENCE
	 * @return red, green, blue
	 */
	public static int[][] decode(byte[][] coded, int xdim, int ydim, int max_value, int shift, int block, int coding, int channels)
	{
		int[] offset = (channels == DIFFERENCE) ? new int[] {0, -max_value, -max_value} : new int[3];
		return decode(coded, xdim, ydim, shift, block, coding, channels, MED, LS_RADIUS, offset, channelMax(max_value, channels));
	}

	/**
	 * Decodes what encode() made; returns red, green, blue. Each channel's
	 * contexts (and least-squares fit) use the ones before it, so with them
	 * the channels decode at the same time, each a row (or, with MED, a row
	 * of blocks) behind the one before; when they don't (MED, the difference
	 * sets and a value coding), each on its own.
	 *
	 * @param coded      the coded bytes per channel, in coding order
	 * @param xdim       the width
	 * @param ydim       the height
	 * @param shift      the Color Resolution
	 * @param block      the block size
	 * @param coding     the coding
	 * @param channels   the channel set
	 * @param prediction MED or LS
	 * @param radius     the least-squares window radius
	 * @param offset     each coded channel's offset (Prepared.offset)
	 * @param max        each coded channel's largest value
	 * @return red, green, blue
	 */
	public static int[][] decode(byte[][] coded, int xdim, int ydim, int shift, int block, int coding, int channels, int prediction, int radius, int[] offset, int[] max)
	{
		int n = xdim * ydim;
		int[][] ordered = new int[3][n], error = new int[3][];
		for(int i = 0; i < 2; i++) error[i] = new int[n];
		boolean correct = (channels == PRIMARY && prediction == MED);
		if(prediction == MED && channels != PRIMARY && coding != ERRORS)
			ViewerSupport.parallel(3, i -> decodeChannel(coded[i], i, error, ordered[i], xdim, ydim, max[i], shift, block, coding, false, null));
		else
		{
			// Each channel waits for the one before it to be a row of blocks
			// (MED) or a row (LS) ahead.
			int step = (prediction == MED) ? block : 1;
			RowGate gate = new RowGate();
			Thread[] worker = new Thread[3];
			for(int c = 0; c < 3; c++)
			{
				int i = c;
				worker[i] = new Thread(() ->
				{
					try
					{
						if(prediction == MED) decodeChannel(coded[i], i, error, ordered[i], xdim, ydim, max[i], shift, block, coding, correct, gate);
						else decodeRaster(coded[i], i, error, ordered, xdim, ydim, max[i], shift, block, coding, radius, gate);
					}
					catch(Throwable e) { gate.fail(e); }
				});
				worker[i].start();
			}
			for(Thread t : worker)
				try { t.join(); } catch(InterruptedException e) { Thread.currentThread().interrupt(); throw new RuntimeException(e); }
			gate.check();
		}
		int[] order = ORDER[channels];
		int[][] channel = new int[3][];
		for(int i = 0; i < 3; i++)
		{
			int[] v = ordered[i];
			if(offset[i] != 0 || (i > 0 && channels != PRIMARY))
				for(int k = 0; k < n; k++) v[k] += offset[i] + ((i > 0 && channels != PRIMARY) ? ordered[0][k] : 0);
			channel[order[i]] = v;
		}
		return channel;
	}

	/**
	 * Each channel's rows decoded, for the decoder's channels to wait on the
	 * ones before them, and the first failure.
	 */
	static final class RowGate
	{
		/**
		 * Each channel's rows decoded.
		 */
		private final int[] done = new int[3];
		/**
		 * The first failure, if any.
		 */
		private Throwable failed;

		/**
		 * A gate with no rows decoded.
		 */
		RowGate() {}

		/**
		 * Waits until channel i - 1 has decoded rows to y (none for channel 0).
		 *
		 * @param i the channel
		 * @param y the rows needed
		 */
		synchronized void await(int i, int y)
		{
			while(i > 0 && done[i - 1] < y && failed == null)
				try { wait(); } catch(InterruptedException e) { Thread.currentThread().interrupt(); throw new RuntimeException(e); }
			if(failed != null) throw new IllegalStateException("an earlier channel failed to decode", failed);
		}

		/**
		 * Says channel i has decoded its first y rows.
		 *
		 * @param i the channel
		 * @param y the rows
		 */
		synchronized void done(int i, int y) { done[i] = y; notifyAll(); }

		/**
		 * Records a failure, waking the channels waiting.
		 *
		 * @param e the failure
		 */
		synchronized void fail(Throwable e) { if(failed == null) failed = e; notifyAll(); }

		/**
		 * Throws the first failure, if any.
		 */
		synchronized void check()
		{
			if(failed instanceof RuntimeException) throw (RuntimeException) failed;
			if(failed instanceof Error) throw (Error) failed;
			if(failed != null) throw new RuntimeException(failed);
		}
	}

	// The largest value of each coded channel of format versions 1 to 4: a
	// difference spans 0..2 * max_value.
	static int[] channelMax(int max_value, int channels)
	{
		return (channels == DIFFERENCE) ? new int[] {max_value, 2 * max_value, 2 * max_value} : new int[] {max_value, max_value, max_value};
	}

	// MED: the blocks one after another, each row by row.
	private static byte[] encodeChannel(int[] v, int i, int[][] error, int xdim, int ydim, int max_value, int shift, int block, int coding, boolean correct)
	{
		Models models = new Models(max_value + 1);
		ArithmeticMapper.RangeEncoder encoder = new ArithmeticMapper.RangeEncoder(v.length / 2);
		int[] cp = new int[2];
		for(int by = 0; by < ydim; by += block)
			for(int bx = 0; bx < xdim; bx += block)
			{
				models.restart();
				int y1 = Math.min(ydim, by + block), x1 = Math.min(xdim, bx + block);
				for(int y = by; y < y1; y++)
					for(int x = bx; x < x1; x++)
					{
						context(v, i, error, x, y, xdim, max_value, shift, coding, correct, -1, cp);
						Model m = models.get(cp[0]);
						int value = v[y * xdim + x];
						int s = (coding == ERRORS) ? Math.floorMod(value - cp[1], max_value + 1) : value;
						encoder.encode(m.start(s), m.f[s], m.total);
						m.update(s);
					}
			}
		return encoder.finish();
	}

	// Decodes encodeChannel's coding into v; its MED errors into error[i] (for
	// the first two channels) a row of blocks at a time, each once the
	// channel before has done that row of blocks (gate, if not null).
	private static void decodeChannel(byte[] coded, int i, int[][] error, int[] v, int xdim, int ydim, int max_value, int shift, int block, int coding, boolean correct, RowGate gate)
	{
		Models models = new Models(max_value + 1);
		ArithmeticMapper.RangeDecoder decoder = new ArithmeticMapper.RangeDecoder(coded);
		int[] cp = new int[2];
		for(int by = 0; by < ydim; by += block)
		{
			int y1 = Math.min(ydim, by + block);
			if(gate != null) gate.await(i, y1);
			for(int bx = 0; bx < xdim; bx += block)
			{
				models.restart();
				int x1 = Math.min(xdim, bx + block);
				for(int y = by; y < y1; y++)
					for(int x = bx; x < x1; x++)
					{
						context(v, i, error, x, y, xdim, max_value, shift, coding, correct, -1, cp);
						Model m = models.get(cp[0]);
						int s = m.find(decoder.target(m.total));
						decoder.decode(m.start(s), m.f[s]);
						v[y * xdim + x] = (coding == ERRORS) ? (cp[1] + s) % (max_value + 1) : s;
						m.update(s);
					}
			}
			if(i < 2) predictionError(v, null, xdim, by, y1, max_value, error[i]);
			if(gate != null) gate.done(i, y1);
		}
	}

	/**
	 * Least squares: raster order, each block keeping its own models while
	 * the rows go through it (new ones at the top of each row of blocks).
	 *
	 * @param v         the channel
	 * @param i         its place in the coding order
	 * @param error     the earlier channels' prediction errors
	 * @param pred      its predictions (-1 where MED predicts)
	 * @param xdim      the width
	 * @param ydim      the height
	 * @param max_value its largest value
	 * @param shift     the Color Resolution
	 * @param block     the block size
	 * @param coding    the coding
	 * @return the coded bytes
	 */
	private static byte[] encodeRaster(int[] v, int i, int[][] error, int[] pred, int xdim, int ydim, int max_value, int shift, int block, int coding)
	{
		Models[] models = new Models[(xdim + block - 1) / block];
		ArithmeticMapper.RangeEncoder encoder = new ArithmeticMapper.RangeEncoder(v.length / 2);
		int[] cp = new int[2];
		for(int y = 0; y < ydim; y++)
		{
			if(y % block == 0) for(int b = 0; b < models.length; b++) models[b] = new Models(max_value + 1);
			for(int x = 0; x < xdim; x++)
			{
				int k = y * xdim + x;
				context(v, i, error, x, y, xdim, max_value, shift, coding, false, pred[k], cp);
				Model m = models[x / block].get(cp[0]);
				int s = (coding == ERRORS) ? Math.floorMod(v[k] - cp[1], max_value + 1) : v[k];
				encoder.encode(m.start(s), m.f[s], m.total);
				m.update(s);
			}
		}
		return encoder.finish();
	}

	/**
	 * Decodes encodeRaster's coding into value[i], fitting least squares as
	 * it goes, a row at a time, each once the channel before has done that
	 * row (its values are the fit's previous channel, its errors the
	 * contexts'); its errors into error[i] (the first two channels).
	 *
	 * @param coded     the coded bytes
	 * @param i         the channel's place in the coding order
	 * @param error     the channels' prediction errors
	 * @param value     the channels, filled in
	 * @param xdim      the width
	 * @param ydim      the height
	 * @param max_value its largest value
	 * @param shift     the Color Resolution
	 * @param block     the block size
	 * @param coding    the coding
	 * @param radius    the least-squares window radius
	 * @param gate      the channels' rows decoded
	 */
	private static void decodeRaster(byte[] coded, int i, int[][] error, int[][] value, int xdim, int ydim, int max_value, int shift, int block, int coding, int radius, RowGate gate)
	{
		int[] v = value[i], e = error[i];
		DeltaMapper.LeastSquares ls = new DeltaMapper.LeastSquares(v, (i > 0) ? value[i - 1] : null, xdim, radius);
		Models[] models = new Models[(xdim + block - 1) / block];
		ArithmeticMapper.RangeDecoder decoder = new ArithmeticMapper.RangeDecoder(coded);
		int[] cp = new int[2];
		for(int y = 0; y < ydim; y++)
		{
			gate.await(i, y + 1);
			if(y % block == 0) for(int b = 0; b < models.length; b++) models[b] = new Models(max_value + 1);
			for(int x = 0; x < xdim; x++)
			{
				int k = y * xdim + x, p = -1;
				if(y > 0 && x > 0 && x < xdim - 1) { ls.next(k); p = clamp(ls.wide, max_value); }
				context(v, i, error, x, y, xdim, max_value, shift, coding, false, p, cp);
				Model m = models[x / block].get(cp[0]);
				int s = m.find(decoder.target(m.total));
				decoder.decode(m.start(s), m.f[s]);
				v[k] = (coding == ERRORS) ? (cp[1] + s) % (max_value + 1) : s;
				m.update(s);
				if(e != null) e[k] = v[k] - cp[1];
			}
			gate.done(i, y + 1);
		}
	}

	// ---- Contexts ---------------------------------------------------------------

	// For pixel (x, y) of channel i (in coding order), sets cp[0] to its
	// context and cp[1] to its prediction: given, or (given < 0) MED.
	// error[0..i-1] are the earlier channels' prediction errors; correct says
	// whether they correct the prediction.
	static void context(int[] v, int i, int[][] error, int x, int y, int xdim, int max_value, int shift, int coding, boolean correct, int given, int[] cp)
	{
		int k = y * xdim + x;
		int a = (x > 0) ? v[k - 1] : (y > 0) ? v[k - xdim] : (max_value + 1) / 2;
		int b = (y > 0) ? v[k - xdim] : a;
		int c = (x > 0 && y > 0) ? v[k - xdim - 1] : b;
		int p = (given >= 0) ? given : med(a, b, c), cross = 0;
		if(i > 0)
		{
			int sum = 0, size = 0;
			for(int j = 0; j < i; j++) { sum += error[j][k]; size += Math.abs(error[j][k]); }
			if(correct)
			{
				p += sum / i;
				if(p < 0) p = 0; else if(p > max_value) p = max_value;
			}
			cross = bucket((size / i) << shift, CROSS_LIMIT);
		}
		int[] limit    = ACTIVITY_LIMIT[coding];
		int   activity = bucket((Math.abs(a - c) + Math.abs(b - c)) << shift, limit);
		if(coding == VALUES)     cp[0] = p * limit.length + activity;
		else if(coding == BANDS) cp[0] = (p >> Math.max(0, BAND_BITS - shift)) * limit.length + activity;
		else                     cp[0] = activity * CROSS_LIMIT.length + cross;
		cp[1] = p;
	}

	static int bucket(int v, int[] limit)
	{
		int b = 0;
		while(b + 1 < limit.length && v >= limit[b + 1]) b++;
		return b;
	}

	// Each pixel's value minus its prediction: pred's where it has one
	// (least squares), MED (the same neighbours as context()) elsewhere.
	static int[] predictionError(int[] v, int[] pred, int xdim, int ydim, int max_value)
	{
		int[] e = new int[v.length];
		predictionError(v, pred, xdim, 0, ydim, max_value, e);
		return e;
	}

	/**
	 * As predictionError(int[], int[], int, int, int), rows y0 to y1 - 1 into e.
	 *
	 * @param v         the channel
	 * @param pred      its predictions (-1 where MED predicts), or null for MED
	 * @param xdim      the width
	 * @param y0        the first row
	 * @param y1        the row after the last
	 * @param max_value its largest value
	 * @param e         where the errors go
	 */
	static void predictionError(int[] v, int[] pred, int xdim, int y0, int y1, int max_value, int[] e)
	{
		for(int y = y0; y < y1; y++)
			for(int x = 0; x < xdim; x++)
			{
				int k = y * xdim + x;
				if(pred != null && pred[k] >= 0) { e[k] = v[k] - pred[k]; continue; }
				int a = (x > 0) ? v[k - 1] : (y > 0) ? v[k - xdim] : (max_value + 1) / 2;
				int b = (y > 0) ? v[k - xdim] : a;
				int c = (x > 0 && y > 0) ? v[k - xdim - 1] : b;
				e[k] = v[k] - med(a, b, c);
			}
	}

	static int med(int a, int b, int c)
	{
		if(c >= Math.max(a, b)) return Math.min(a, b);
		if(c <= Math.min(a, b)) return Math.max(a, b);
		return a + b - c;
	}

	// ---- Choosing the block size -------------------------------------------------

	// The block size (BLOCK_MIN..blockLimit) that codes the prepared channels smallest:
	// every size in SEARCH_SIZES; while the largest tried is the best and
	// smaller than the frame, twice that (the last one the whole frame: the
	// larger of xdim and ydim); then the sizes halfway (by ratio) to each
	// neighbour of the best. tried, if not null, gets {size, coded bytes of
	// the 3 channels} for every size tried, smallest size first.
	public static int findBestBlock(Prepared pr, int shift, int coding, List<int[]> tried)
	{
		int xdim = pr.xdim, ydim = pr.ydim;
		TreeMap<Integer, Long> bytes = new TreeMap<Integer, Long>();
		measure(SEARCH_SIZES, pr, shift, coding, bytes);
		int best = bestOf(bytes), frame = Math.max(xdim, ydim);
		for(int last = SEARCH_SIZES[SEARCH_SIZES.length - 1]; best == last && last < frame; best = bestOf(bytes))
		{
			last = Math.min(2 * last, frame);
			measure(new int[] {last}, pr, shift, coding, bytes);
		}
		ArrayList<Integer> more = new ArrayList<Integer>();
		int lower = (int) Math.round(best / Math.sqrt(2)), upper = Math.min((int) Math.round(best * Math.sqrt(2)), blockLimit(xdim, ydim));
		if(lower >= BLOCK_MIN && !bytes.containsKey(lower)) more.add(lower);
		if(upper > best && !bytes.containsKey(upper)) more.add(upper);
		int[] extra = new int[more.size()];
		for(int k = 0; k < extra.length; k++) extra[k] = more.get(k);
		measure(extra, pr, shift, coding, bytes);
		if(tried != null) for(Map.Entry<Integer, Long> e : bytes.entrySet()) tried.add(new int[] {e.getKey(), (int) (long) e.getValue()});
		return bestOf(bytes);
	}

	private static void measure(int[] sizes, Prepared pr, int shift, int coding, TreeMap<Integer, Long> bytes)
	{
		long[] b = new long[sizes.length];
		ViewerSupport.parallel(sizes.length, s ->
		{
			for(byte[] c : encode(pr, shift, sizes[s], coding)) b[s] += c.length;
		});
		for(int s = 0; s < sizes.length; s++) bytes.put(sizes[s], b[s]);
	}

	private static int bestOf(TreeMap<Integer, Long> bytes)
	{
		int best = -1;
		for(Map.Entry<Integer, Long> e : bytes.entrySet())
			if(best < 0 || e.getValue() < bytes.get(best)) best = e.getKey();
		return best;
	}

	// ---- Quantizing and assembling (shared by the writer and reader) ------------

	// The image the reader shows: each channel (red, green, blue, at the
	// quantized size) resized back to xdim x ydim (centred, ResizeMapper.resize2,
	// from format version 5; ResizeMapper.resize before), then shifted back up.
	public static int[] getRGB(int[][] channel, int qxdim, int xdim, int ydim, int pixel_quant, int pixel_shift, boolean centred)
	{
		int[][] c = new int[3][];
		ViewerSupport.parallel(3, i -> c[i] = (pixel_quant == 0) ? channel[i]
			: centred ? ResizeMapper.resize2(channel[i], qxdim, xdim, ydim) : ResizeMapper.resize(channel[i], qxdim, xdim, ydim));
		int[] rgb = new int[xdim * ydim];
		for(int k = 0; k < rgb.length; k++)
		{
			int r = clamp(c[0][k] << pixel_shift), g = clamp(c[1][k] << pixel_shift), b = clamp(c[2][k] << pixel_shift);
			rgb[k] = (r << 16) | (g << 8) | b;
		}
		return rgb;
	}

	static int clamp(int v) { return (v < 0) ? 0 : (v > 255) ? 255 : v; }

	/**
	 * v clamped to 0..max.
	 *
	 * @param v   the value
	 * @param max the largest value
	 * @return the clamped value
	 */
	static int clamp(int v, int max) { return (v < 0) ? 0 : (v > max) ? max : v; }

	// ---- Averaging pyramid (the Quantization menu's Average) -------------------
	//
	// Each level halves the width and height: every 2 x 2 square becomes its
	// rounded average (ImageMapper.shrinkAvg), and only the smallest level is
	// coded. To expand, each level is predicted from the one below it by a
	// gradient fit (ImageMapper.expandGradient, or expandGradientSaddle with
	// the cross term), then corrected with one sign bit per pixel: whether
	// the pixel was >= its square's average (ImageMapper.buildGeqBits /
	// refineWithSignBits). This is the same scheme DeltaWriter used, applied
	// here to the red, green and blue channels themselves (values
	// 0..max_value), never to difference channels. The image is first padded
	// (edge pixels repeated) to a multiple of 2^levels.

	public static final int PYRAMID_MAX = 2;

	// Size of the top level, the size the pixels are coded at.
	public static int[] pyramidSize(int xdim, int ydim, int levels)
	{
		if(levels == 0) return new int[] {xdim, ydim};
		int mult = 1 << levels;
		return new int[] {ImageMapper.padTo(xdim, mult) >> levels, ImageMapper.padTo(ydim, mult) >> levels};
	}

	// Shrinks c (xdim x ydim) levels times; sign_bits[lvl] (levels of them)
	// gets level lvl's bits against level lvl+1. Returns the top level.
	public static int[] shrinkPyramid(int[] c, int xdim, int ydim, boolean[][] sign_bits)
	{
		int   levels = sign_bits.length, mult = 1 << levels;
		int   level_xdim = ImageMapper.padTo(xdim, mult);
		int[] level = ImageMapper.padEdgeReplicate(c, xdim, ydim, level_xdim, ImageMapper.padTo(ydim, mult));
		for(int lvl = 0; lvl < levels; lvl++)
		{
			int[] next = ImageMapper.shrinkAvg(level, level_xdim);
			sign_bits[lvl] = ImageMapper.buildGeqBits(level, next, level_xdim);
			level = next;
			level_xdim /= 2;
		}
		return level;
	}

	// Inverse of shrinkPyramid, as close as the sign bits allow: expands the
	// top level back to xdim x ydim, correcting each level with its sign bits.
	public static int[] expandPyramid(int[] top, int xdim, int ydim, boolean[][] sign_bits, int max_value, boolean saddle)
	{
		int   levels = sign_bits.length, mult = 1 << levels;
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

	// Sign bits, one bitmap per level: int length, then (length + 7) / 8
	// bytes, bit q in byte q >> 3 at bit q & 7.
	public static void writeSignBits(java.io.DataOutputStream out, boolean[][] sign_bits) throws java.io.IOException
	{
		for(boolean[] bits : sign_bits)
		{
			byte[] packed = new byte[(bits.length + 7) / 8];
			for(int q = 0; q < bits.length; q++) if(bits[q]) packed[q >> 3] |= (byte) (1 << (q & 7));
			out.writeInt(bits.length);
			out.write(packed);
		}
	}

	public static boolean[][] readSignBits(java.io.DataInputStream in, int levels) throws java.io.IOException
	{
		boolean[][] sign_bits = new boolean[levels][];
		for(int lvl = 0; lvl < levels; lvl++)
		{
			int    n      = in.readInt();
			byte[] packed = new byte[(n + 7) / 8];
			in.readFully(packed);
			sign_bits[lvl] = new boolean[n];
			for(int q = 0; q < n; q++) sign_bits[lvl][q] = (packed[q >> 3] & (1 << (q & 7))) != 0;
		}
		return sign_bits;
	}

	// Bytes the sign bits take in the file.
	public static long signBitBytes(boolean[][] sign_bits)
	{
		long n = 0;
		for(boolean[] bits : sign_bits) n += 4 + (bits.length + 7) / 8;
		return n;
	}

	// ---- Models -------------------------------------------------------------------

	// The models of one channel, one per context, created as contexts are
	// first used. restart() makes every model start over (lazily: a model
	// is reset when it is next used), so a block costs only the models it
	// touches.
	static final class Models
	{
		final int n;
		Model[] array = new Model[0];
		int generation = 0;

		Models(int n) { this.n = n; }

		void restart() { generation++; }

		Model get(int context)
		{
			if(context >= array.length) array = Arrays.copyOf(array, Math.max(context + 1, array.length * 2));
			Model m = array[context];
			if(m == null) { m = new Model(n); array[context] = m; }
			if(m.generation != generation) { m.reset(); m.generation = generation; }
			return m;
		}
	}

	// One context's counts over n values, with a Fenwick tree for the running
	// totals (as in ArithmeticMapper's context coder).
	static final class Model
	{
		final int[] f, bit;
		final int   n, top;
		int total, generation = -1;

		Model(int n)
		{
			this.n = n;
			f   = new int[n];
			bit = new int[n + 1];
			top = Integer.highestOneBit(n);
		}

		void reset() { Arrays.fill(f, 1); rebuild(); }

		void rebuild()
		{
			Arrays.fill(bit, 0);
			total = 0;
			for(int s = 0; s < n; s++)
			{
				total += f[s];
				for(int i = s + 1; i <= n; i += i & -i) bit[i] += f[s];
			}
		}

		int start(int s)
		{
			int sum = 0;
			for(int i = s; i > 0; i -= i & -i) sum += bit[i];
			return sum;
		}

		int find(int target)
		{
			int pos = 0;
			for(int b = top; b > 0; b >>= 1)
			{
				int next = pos + b;
				if(next <= n && bit[next] <= target) { target -= bit[next]; pos = next; }
			}
			return pos;
		}

		void update(int s)
		{
			f[s]  += INCREMENT;
			total += INCREMENT;
			for(int i = s + 1; i <= n; i += i & -i) bit[i] += INCREMENT;
			if(total > LIMIT)
			{
				for(int k = 0; k < n; k++) f[k] = (f[k] + 1) >>> 1;
				rebuild();
			}
		}
	}
}
