import java.util.*;

// PixelMapper version 1.1
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
// left neighbours, corrected by how far the earlier channels at the same
// pixel were from their own MED predictions (the average of those errors).
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
// blocks of 256 (the largest the slider allows, and the best size for all
// three codings at both settings below):
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

	// The block sizes init() tries: powers of 2, then the steps halfway
	// (by ratio) around the best of those.
	public static final int[] SEARCH_SIZES = {8, 16, 32, 64, 128, 256};

	// ---- Coding ---------------------------------------------------------------

	// Codes 3 channels (values 0..max_value, in red, green, blue order) of an
	// xdim x ydim image. Returns the coded bytes per channel, in ORDER. The
	// channels are coded in parallel: the encoder has all of them.
	public static byte[][] encode(int[][] channel, int xdim, int ydim, int max_value, int shift, int block, int coding)
	{
		int[][] ordered = new int[3][];
		for(int i = 0; i < 3; i++) ordered[i] = channel[ORDER[i]];
		int[][] error = new int[3][];
		for(int i = 0; i < 2; i++) error[i] = predictionError(ordered[i], xdim, ydim, max_value);
		byte[][] coded = new byte[3][];
		ViewerSupport.parallel(3, i -> coded[i] = encodeChannel(ordered[i], i, error, xdim, ydim, max_value, shift, block, coding));
		return coded;
	}

	// Decodes what encode() made; returns red, green, blue. The channels are
	// decoded in order, since each one's contexts use the ones before it.
	public static int[][] decode(byte[][] coded, int xdim, int ydim, int max_value, int shift, int block, int coding)
	{
		int[][] ordered = new int[3][];
		int[][] error   = new int[3][];
		for(int i = 0; i < 3; i++)
		{
			ordered[i] = decodeChannel(coded[i], i, error, xdim, ydim, max_value, shift, block, coding);
			if(i < 2) error[i] = predictionError(ordered[i], xdim, ydim, max_value);
		}
		int[][] channel = new int[3][];
		for(int i = 0; i < 3; i++) channel[ORDER[i]] = ordered[i];
		return channel;
	}

	private static byte[] encodeChannel(int[] v, int i, int[][] error, int xdim, int ydim, int max_value, int shift, int block, int coding)
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
						context(v, i, error, x, y, xdim, max_value, shift, coding, cp);
						Model m = models.get(cp[0]);
						int value = v[y * xdim + x];
						int s = (coding == ERRORS) ? Math.floorMod(value - cp[1], max_value + 1) : value;
						encoder.encode(m.start(s), m.f[s], m.total);
						m.update(s);
					}
			}
		return encoder.finish();
	}

	private static int[] decodeChannel(byte[] coded, int i, int[][] error, int xdim, int ydim, int max_value, int shift, int block, int coding)
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
						context(v, i, error, x, y, xdim, max_value, shift, coding, cp);
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
	// channels' MED errors.
	static void context(int[] v, int i, int[][] error, int x, int y, int xdim, int max_value, int shift, int coding, int[] cp)
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
			p += sum / i;
			if(p < 0) p = 0; else if(p > max_value) p = max_value;
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

	// The block size (BLOCK_MIN..BLOCK_MAX) that codes the channels smallest:
	// every size in SEARCH_SIZES, then the sizes halfway (by ratio) to each
	// neighbour of the best. tried, if not null, gets {size, coded bytes of
	// the 3 channels} for every size tried, smallest size first.
	public static int findBestBlock(int[][] channel, int xdim, int ydim, int max_value, int shift, int coding, List<int[]> tried)
	{
		TreeMap<Integer, Long> bytes = new TreeMap<Integer, Long>();
		measure(SEARCH_SIZES, channel, xdim, ydim, max_value, shift, coding, bytes);
		int best = bestOf(bytes);
		ArrayList<Integer> more = new ArrayList<Integer>();
		int lower = (int) Math.round(best / Math.sqrt(2)), upper = (int) Math.round(best * Math.sqrt(2));
		if(lower >= BLOCK_MIN && !bytes.containsKey(lower)) more.add(lower);
		if(upper <= BLOCK_MAX && !bytes.containsKey(upper)) more.add(upper);
		int[] extra = new int[more.size()];
		for(int k = 0; k < extra.length; k++) extra[k] = more.get(k);
		measure(extra, channel, xdim, ydim, max_value, shift, coding, bytes);
		if(tried != null) for(Map.Entry<Integer, Long> e : bytes.entrySet()) tried.add(new int[] {e.getKey(), (int) (long) e.getValue()});
		return bestOf(bytes);
	}

	private static void measure(int[] sizes, int[][] channel, int xdim, int ydim, int max_value, int shift, int coding, TreeMap<Integer, Long> bytes)
	{
		long[] b = new long[sizes.length];
		ViewerSupport.parallel(sizes.length, s ->
		{
			for(byte[] c : encode(channel, xdim, ydim, max_value, shift, sizes[s], coding)) b[s] += c.length;
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
