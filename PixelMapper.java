import java.util.*;

// PixelMapper version 1.3
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
// The channels are coded green, red, blue. Every coding starts from the same
// prediction: the median edge predictor (MED) of the left, upper and upper-
// left neighbours, corrected (when coding red, green and blue; see
// CHANNEL_SET_NAMES) by how far the earlier channels at the same pixel were
// from their own MED predictions (the average of those errors).
// The channels move together, so this removes much of the prediction error
// of the second and third channels. "Activity" is how busy the neighbourhood
// is: |left - upper-left| + |upper - upper-left| on the 0-255 scale.
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

	// Coding order of the 3 channels, as indexes into {red, green, blue}
	// (the writers' channel order: red holds bits 16-23 of a pixel).
	public static final int[] ORDER = {1, 0, 2};
	public static final String[] CHANNEL_NAMES = {"red", "green", "blue"};

	// Channels coded (CHANNEL_SET_NAMES):
	//   PRIMARY:    green, red, blue, with red's and blue's predictions
	//               corrected by the earlier channels' errors (see above).
	//   DIFFERENCE: green, red - green, blue - green (each difference offset
	//               by max_value so it is never negative), with no correction:
	//               the difference already removes what green shares with
	//               red and blue, and correcting as well made files larger.
	// Measured with DiffTest (7 photographs, blocks of 256): with the value
	// codings, DIFFERENCE was 2.4% smaller lossless and 1.7% at Color
	// Resolution 3; with Errors the two were within 0.3%.
	public static final int PRIMARY = 0, DIFFERENCE = 1;
	public static final String[] CHANNEL_SET_NAMES = {"Red, green, blue (corrected)", "Green, red - green, blue - green"};

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

	// ---- Coding ---------------------------------------------------------------

	// Codes 3 channels (values 0..max_value, in red, green, blue order) of an
	// xdim x ydim image. Returns the coded bytes per channel, in ORDER. The
	// channels are coded in parallel: the encoder has all of them.
	public static byte[][] encode(int[][] channel, int xdim, int ydim, int max_value, int shift, int block, int coding, int channels)
	{
		int[][] ordered = new int[3][];
		for(int i = 0; i < 3; i++) ordered[i] = channel[ORDER[i]];
		if(channels == DIFFERENCE)
			for(int i = 1; i < 3; i++)
			{
				int[] d = new int[ordered[0].length];
				for(int k = 0; k < d.length; k++) d[k] = ordered[i][k] - ordered[0][k] + max_value;
				ordered[i] = d;
			}
		int[]   max   = channelMax(max_value, channels);
		int[][] error = new int[3][];
		for(int i = 0; i < 2; i++) error[i] = predictionError(ordered[i], xdim, ydim, max[i]);
		byte[][] coded = new byte[3][];
		ViewerSupport.parallel(3, i -> coded[i] = encodeChannel(ordered[i], i, error, xdim, ydim, max[i], shift, block, coding, channels == PRIMARY));
		return coded;
	}

	// Decodes what encode() made; returns red, green, blue. The channels are
	// decoded in order, since each one's contexts use the ones before it.
	public static int[][] decode(byte[][] coded, int xdim, int ydim, int max_value, int shift, int block, int coding, int channels)
	{
		int[]   max     = channelMax(max_value, channels);
		int[][] ordered = new int[3][];
		int[][] error   = new int[3][];
		for(int i = 0; i < 3; i++)
		{
			ordered[i] = decodeChannel(coded[i], i, error, xdim, ydim, max[i], shift, block, coding, channels == PRIMARY);
			if(i < 2) error[i] = predictionError(ordered[i], xdim, ydim, max[i]);
		}
		if(channels == DIFFERENCE)
			for(int i = 1; i < 3; i++)
				for(int k = 0; k < ordered[i].length; k++) ordered[i][k] += ordered[0][k] - max_value;
		int[][] channel = new int[3][];
		for(int i = 0; i < 3; i++) channel[ORDER[i]] = ordered[i];
		return channel;
	}

	// The largest value of each coded channel: a difference spans 0..2 * max_value.
	static int[] channelMax(int max_value, int channels)
	{
		return (channels == DIFFERENCE) ? new int[] {max_value, 2 * max_value, 2 * max_value} : new int[] {max_value, max_value, max_value};
	}

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
						context(v, i, error, x, y, xdim, max_value, shift, coding, correct, cp);
						Model m = models.get(cp[0]);
						int value = v[y * xdim + x];
						int s = (coding == ERRORS) ? Math.floorMod(value - cp[1], max_value + 1) : value;
						encoder.encode(m.start(s), m.f[s], m.total);
						m.update(s);
					}
			}
		return encoder.finish();
	}

	private static int[] decodeChannel(byte[] coded, int i, int[][] error, int xdim, int ydim, int max_value, int shift, int block, int coding, boolean correct)
	{
		int[] v = new int[xdim * ydim];
		Models models = new Models(max_value + 1);
		ArithmeticMapper.RangeDecoder decoder = new ArithmeticMapper.RangeDecoder(coded);
		int[] cp = new int[2];
		for(int by = 0; by < ydim; by += block)
			for(int bx = 0; bx < xdim; bx += block)
			{
				models.restart();
				int y1 = Math.min(ydim, by + block), x1 = Math.min(xdim, bx + block);
				for(int y = by; y < y1; y++)
					for(int x = bx; x < x1; x++)
					{
						context(v, i, error, x, y, xdim, max_value, shift, coding, correct, cp);
						Model m = models.get(cp[0]);
						int s = m.find(decoder.target(m.total));
						decoder.decode(m.start(s), m.f[s]);
						v[y * xdim + x] = (coding == ERRORS) ? (cp[1] + s) % (max_value + 1) : s;
						m.update(s);
					}
			}
		return v;
	}

	// ---- Contexts ---------------------------------------------------------------

	// For pixel (x, y) of channel i (in coding order), sets cp[0] to its
	// context and cp[1] to its prediction. error[0..i-1] are the earlier
	// channels' MED errors; correct says whether they correct the prediction.
	static void context(int[] v, int i, int[][] error, int x, int y, int xdim, int max_value, int shift, int coding, boolean correct, int[] cp)
	{
		int k = y * xdim + x;
		int a = (x > 0) ? v[k - 1] : (y > 0) ? v[k - xdim] : (max_value + 1) / 2;
		int b = (y > 0) ? v[k - xdim] : a;
		int c = (x > 0 && y > 0) ? v[k - xdim - 1] : b;
		int p = med(a, b, c), cross = 0;
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

	// Each pixel's value minus its MED prediction (same neighbours as context()).
	static int[] predictionError(int[] v, int xdim, int ydim, int max_value)
	{
		int[] e = new int[v.length];
		for(int y = 0; y < ydim; y++)
			for(int x = 0; x < xdim; x++)
			{
				int k = y * xdim + x;
				int a = (x > 0) ? v[k - 1] : (y > 0) ? v[k - xdim] : (max_value + 1) / 2;
				int b = (y > 0) ? v[k - xdim] : a;
				int c = (x > 0 && y > 0) ? v[k - xdim - 1] : b;
				e[k] = v[k] - med(a, b, c);
			}
		return e;
	}

	static int med(int a, int b, int c)
	{
		if(c >= Math.max(a, b)) return Math.min(a, b);
		if(c <= Math.min(a, b)) return Math.max(a, b);
		return a + b - c;
	}

	// ---- Choosing the block size -------------------------------------------------

	// The block size (BLOCK_MIN..blockLimit) that codes the channels smallest:
	// every size in SEARCH_SIZES; while the largest tried is the best and
	// smaller than the frame, twice that (the last one the whole frame: the
	// larger of xdim and ydim); then the sizes halfway (by ratio) to each
	// neighbour of the best. tried, if not null, gets {size, coded bytes of
	// the 3 channels} for every size tried, smallest size first.
	public static int findBestBlock(int[][] channel, int xdim, int ydim, int max_value, int shift, int coding, int channels, List<int[]> tried)
	{
		TreeMap<Integer, Long> bytes = new TreeMap<Integer, Long>();
		measure(SEARCH_SIZES, channel, xdim, ydim, max_value, shift, coding, channels, bytes);
		int best = bestOf(bytes), frame = Math.max(xdim, ydim);
		for(int last = SEARCH_SIZES[SEARCH_SIZES.length - 1]; best == last && last < frame; best = bestOf(bytes))
		{
			last = Math.min(2 * last, frame);
			measure(new int[] {last}, channel, xdim, ydim, max_value, shift, coding, channels, bytes);
		}
		ArrayList<Integer> more = new ArrayList<Integer>();
		int lower = (int) Math.round(best / Math.sqrt(2)), upper = Math.min((int) Math.round(best * Math.sqrt(2)), blockLimit(xdim, ydim));
		if(lower >= BLOCK_MIN && !bytes.containsKey(lower)) more.add(lower);
		if(upper > best && !bytes.containsKey(upper)) more.add(upper);
		int[] extra = new int[more.size()];
		for(int k = 0; k < extra.length; k++) extra[k] = more.get(k);
		measure(extra, channel, xdim, ydim, max_value, shift, coding, channels, bytes);
		if(tried != null) for(Map.Entry<Integer, Long> e : bytes.entrySet()) tried.add(new int[] {e.getKey(), (int) (long) e.getValue()});
		return bestOf(bytes);
	}

	private static void measure(int[] sizes, int[][] channel, int xdim, int ydim, int max_value, int shift, int coding, int channels, TreeMap<Integer, Long> bytes)
	{
		long[] b = new long[sizes.length];
		ViewerSupport.parallel(sizes.length, s ->
		{
			for(byte[] c : encode(channel, xdim, ydim, max_value, shift, sizes[s], coding, channels)) b[s] += c.length;
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
	// quantized size) resized back to xdim x ydim, then shifted back up.
	public static int[] getRGB(int[][] channel, int qxdim, int xdim, int ydim, int pixel_quant, int pixel_shift)
	{
		int[][] c = new int[3][];
		ViewerSupport.parallel(3, i -> c[i] = (pixel_quant != 0) ? ResizeMapper.resize(channel[i], qxdim, xdim, ydim) : channel[i]);
		int[] rgb = new int[xdim * ydim];
		for(int k = 0; k < rgb.length; k++)
		{
			int r = clamp(c[0][k] << pixel_shift), g = clamp(c[1][k] << pixel_shift), b = clamp(c[2][k] << pixel_shift);
			rgb[k] = (r << 16) | (g << 8) | b;
		}
		return rgb;
	}

	static int clamp(int v) { return (v < 0) ? 0 : (v > 255) ? 255 : v; }

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
