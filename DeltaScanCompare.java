import java.awt.image.*;
import java.io.*;
import java.util.*;
import javax.imageio.*;

// Compares the per-row delta distributions produced by each scanline method
// against the per-pixel frame map (2) lower bound.  For each method, shows
// which filter wins per row, how often each filter is chosen, and how close
// the row-level entropy comes to the FM(2) ideal.
//
// Usage:  java DeltaScanCompare <image> [pixel_quant] [pixel_shift]

public class DeltaScanCompare
{
	// ---- Filter set definitions ----

	// Scanline (1): horizontal, vertical, average, MED
	static int filterS1(int a, int b, int c, int d, int e, int f)
	{
		// f = filter index 0-3
		switch (f)
		{
			case 0: return e - a;
			case 1: return e - b;
			case 2: return e - (a + b) / 2;
			default:
				int med;
				if      (c >= Math.max(a, b)) med = Math.min(a, b);
				else if (c <= Math.min(a, b)) med = Math.max(a, b);
				else                          med = a + b - c;
				return e - med;
		}
	}
	static final String[] NAME_S1 = {"horiz", "vert", "avg", "MED"};

	// Scanline (2): horizontal, vertical, average, avg(left, above-right)
	static int filterS2(int a, int b, int c, int d, int e, int f)
	{
		switch (f)
		{
			case 0: return e - a;
			case 1: return e - b;
			case 2: return e - (a + b) / 2;
			default: return e - (a + d) / 2;
		}
	}
	static final String[] NAME_S2 = {"horiz", "vert", "avg", "avg(l,ar)"};

	// Scanline (3): horizontal, average, MED, directional
	static int filterS3(int a, int b, int c, int d, int e, int f)
	{
		switch (f)
		{
			case 0: return e - a;
			case 1: return e - (a + b) / 2;
			case 2:
				int med;
				if      (c >= Math.max(a, b)) med = Math.min(a, b);
				else if (c <= Math.min(a, b)) med = Math.max(a, b);
				else                          med = a + b - c;
				return e - med;
			default:
				int h_edge  = Math.abs(b - c) + Math.abs(b - d);
				int v_edge  = Math.abs(a - c) + Math.abs(a - b);
				int dl_edge = Math.abs(c - d);
				int dr_edge = Math.abs(a - d);
				int dir_pred;
				if      (h_edge >= v_edge && h_edge >= dl_edge && h_edge >= dr_edge) dir_pred = b;
				else if (v_edge >= dl_edge && v_edge >= dr_edge)                     dir_pred = a;
				else if (dl_edge >= dr_edge)                                         dir_pred = d;
				else                                                                 dir_pred = c;
				return e - dir_pred;
		}
	}
	static final String[] NAME_S3 = {"horiz", "avg", "MED", "directional"};

	// ---- Row-level computations ----

	// FM(2): 16-predictor minimum delta per pixel
	static int[] rowDeltasFM2(int[] qc, int xdim, int row)
	{
		int start = row * xdim + 1;
		int n     = xdim - 2;
		int[] d   = new int[n];
		for (int j = 0; j < n; j++)
		{
			int k = start + j;
			int a = qc[k - 1], b = qc[k - xdim], c = qc[k - xdim - 1], dd = qc[k - xdim + 1];
			int e = qc[k];

			int med;
			if      (c >= Math.max(a, b)) med = Math.min(a, b);
			else if (c <= Math.min(a, b)) med = Math.max(a, b);
			else                          med = a + b - c;

			int[] pred = {a, c, b, dd, (a+c)>>1, (c+b)>>1, (b+dd)>>1, (dd+a)>>1,
			              (a+b)>>1, (c+dd)>>1, (a+b+c+dd)>>2, med,
			              (a+b+c)>>2, (a+b+dd)>>2, (a+c+dd)>>2, (b+c+dd)>>2};

			int best_abs = Integer.MAX_VALUE, best_delta = 0;
			for (int p : pred) { int delta = e - p, abs = Math.abs(delta);
			                     if (abs < best_abs) { best_abs = abs; best_delta = delta; } }
			d[j] = best_delta;
		}
		return d;
	}

	// Shannon entropy in bits for an integer array
	static double shannonBits(int[] values)
	{
		if (values.length == 0) return 0;
		int min = values[0], max = values[0];
		for (int v : values) { if (v < min) min = v; if (v > max) max = v; }
		if (min == max) return 0;
		int[] freq = new int[max - min + 1];
		for (int v : values) freq[v - min]++;
		double total = values.length, h = 0;
		for (int f : freq) if (f > 0) { double p = f / total; h -= p * Math.log(p) / Math.log(2); }
		return h * total;
	}

	// Concatenate n arrays
	static int[] concat(int[]... arrays)
	{
		int len = 0;
		for (int[] a : arrays) len += a.length;
		int[] out = new int[len];
		int pos = 0;
		for (int[] a : arrays) { System.arraycopy(a, 0, out, pos, a.length); pos += a.length; }
		return out;
	}

	// ---- Main analysis ----

	static final String[] SET_NAME = {
		"blue, green, red",        "blue, red, red-green",
		"blue, red, blue-green",   "blue, blue-green, red-green",
		"blue, blue-green, red-blue", "green, red, blue-green",
		"red, blue-green, red-green", "green, blue-green, red-green",
		"green, red-green, red-blue", "red, red-green, red-blue"
	};

	public static void main(String[] args) throws Exception
	{
		if (args.length < 1)
		{
			System.out.println("Usage: java DeltaScanCompare <image> [pixel_quant] [pixel_shift]");
			System.exit(0);
		}

		String filename    = args[0];
		int    pixel_quant = args.length > 1 ? Integer.parseInt(args[1]) : 4;
		int    pixel_shift = args.length > 2 ? Integer.parseInt(args[2]) : 3;

		// ---- Load and prepare image ----
		BufferedImage img = ImageIO.read(new File(filename));
		if (img == null) { System.out.println("Cannot read: " + filename); System.exit(1); }
		int xdim = img.getWidth(), ydim = img.getHeight();
		System.out.println("Image:        " + filename + "  (" + xdim + " x " + ydim + ")");
		System.out.println("pixel_quant=" + pixel_quant + "  pixel_shift=" + pixel_shift);

		int[] pixel = new int[xdim * ydim];
		PixelGrabber pg = new PixelGrabber(img, 0, 0, xdim, ydim, pixel, 0, xdim);
		pg.grabPixels();

		int[] b = new int[pixel.length], g = new int[pixel.length], r = new int[pixel.length];
		for (int i = 0; i < pixel.length; i++) {
			b[i] = (pixel[i] >> 16) & 0xFF;
			g[i] = (pixel[i] >>  8) & 0xFF;
			r[i] =  pixel[i]         & 0xFF;
		}

		int new_xdim = xdim, new_ydim = ydim;
		if (pixel_quant != 0) {
			double ff = pixel_quant / 10.0;
			new_xdim = xdim - (int)(ff * (xdim / 2 - 2));
			new_ydim = ydim - (int)(ff * (ydim / 2 - 2));
		}

		ArrayList<int[]> cl = new ArrayList<>();
		for (int[] ch : new int[][] {b, g, r}) {
			int[] q = (pixel_quant == 0) ? ch : ResizeMapper.resize(ch, xdim, new_xdim, new_ydim);
			cl.add(pixel_shift == 0 ? q : DeltaMapper.shift(q, -pixel_shift));
		}
		cl.add(DeltaMapper.getDifference(cl.get(0), cl.get(1)));
		cl.add(DeltaMapper.getDifference(cl.get(2), cl.get(1)));
		cl.add(DeltaMapper.getDifference(cl.get(2), cl.get(0)));
		for (int i = 0; i < 6; i++) {
			int[] qc = cl.get(i); int mn = 256;
			for (int v : qc) if (v < mn) mn = v;
			if (i > 2) for (int k = 0; k < qc.length; k++) qc[k] -= mn;
			cl.set(i, qc);
		}

		// Select best channel set
		int[] cs = new int[6];
		for (int i = 0; i < 6; i++)
			cs[i] = (int) Math.floor(CodeMapper.getShannonLimit(
					DeltaMapper.getIdealFrequency(cl.get(i), new_xdim, new_ydim)));
		int[] ss = {cs[0]+cs[1]+cs[2], cs[0]+cs[4]+cs[2], cs[0]+cs[3]+cs[2],
		            cs[0]+cs[1]+cs[4], cs[0]+cs[3]+cs[5], cs[3]+cs[1]+cs[2],
		            cs[3]+cs[4]+cs[2], cs[3]+cs[1]+cs[4], cs[5]+cs[1]+cs[4],
		            cs[5]+cs[4]+cs[2]};
		int best_set = 0;
		for (int i = 1; i < 10; i++) if (ss[i] < ss[best_set]) best_set = i;
		int[] channel_id = DeltaMapper.getChannels(best_set);

		System.out.println("Channel set:  " + SET_NAME[best_set]);
		System.out.println();

		// ---- Per-row analysis ----
		// For each row, compute entropy across all 3 channels simultaneously.
		// rows 1..new_ydim-1 where all causal neighbors exist.
		int n_rows = new_ydim - 1;
		int n_cols = new_xdim - 2;

		// Per-row: [method][row] = bits for that row
		// methods: S1(4), S2(4), S3(4), FM2 — stored as best-filter entropy per row
		double[] rowBitsS1   = new double[n_rows];
		double[] rowBitsS2   = new double[n_rows];
		double[] rowBitsS3   = new double[n_rows];
		double[] rowBitsFM2  = new double[n_rows];

		// Per-row filter choice counts [method][filter]
		int[][] choiceS1 = new int[3][4];  // [channel][filter]
		int[][] choiceS2 = new int[3][4];
		int[][] choiceS3 = new int[3][4];
		// combined across channels
		int[] filterCountS1 = new int[4];
		int[] filterCountS2 = new int[4];
		int[] filterCountS3 = new int[4];

		// Gap buckets vs FM2: < 2%, 2-5%, 5-10%, 10-20%, > 20%
		int[] gapBucketS1 = new int[5];
		int[] gapBucketS2 = new int[5];
		int[] gapBucketS3 = new int[5];

		for (int row = 1; row < new_ydim; row++)
		{
			int ri = row - 1;

			// Collect deltas across 3 channels for each filter set and FM2
			ArrayList<int[]> s1Deltas   = new ArrayList<>();
			ArrayList<int[]> s2Deltas   = new ArrayList<>();
			ArrayList<int[]> s3Deltas   = new ArrayList<>();
			ArrayList<int[]> fm2Deltas  = new ArrayList<>();
			// per-filter delta arrays for this row across channels
			int[][] s1f = new int[4][0];
			int[][] s2f = new int[4][0];
			int[][] s3f = new int[4][0];

			for (int ci = 0; ci < 3; ci++)
			{
				int[] qc = cl.get(channel_id[ci]);
				int   start = row * new_xdim + 1;

				// S1 deltas per filter
				int[][] ds1 = new int[4][n_cols];
				int[][] ds2 = new int[4][n_cols];
				int[][] ds3 = new int[4][n_cols];
				int[]   dfm = new int[n_cols];

				for (int j = 0; j < n_cols; j++)
				{
					int k  = start + j;
					int a  = qc[k - 1], bv = qc[k - new_xdim];
					int c  = qc[k - new_xdim - 1], d = qc[k - new_xdim + 1];
					int e  = qc[k];

					// S1
					int med;
					if      (c >= Math.max(a, bv)) med = Math.min(a, bv);
					else if (c <= Math.min(a, bv)) med = Math.max(a, bv);
					else                           med = a + bv - c;
					ds1[0][j] = e - a;
					ds1[1][j] = e - bv;
					ds1[2][j] = e - (a + bv) / 2;
					ds1[3][j] = e - med;

					// S2
					ds2[0][j] = e - a;
					ds2[1][j] = e - bv;
					ds2[2][j] = e - (a + bv) / 2;
					ds2[3][j] = e - (a + d) / 2;

					// S3
					int h_edge  = Math.abs(bv - c) + Math.abs(bv - d);
					int v_edge  = Math.abs(a  - c) + Math.abs(a  - bv);
					int dl_edge = Math.abs(c - d);
					int dr_edge = Math.abs(a - d);
					int dir_pred;
					if      (h_edge >= v_edge && h_edge >= dl_edge && h_edge >= dr_edge) dir_pred = bv;
					else if (v_edge >= dl_edge && v_edge >= dr_edge)                     dir_pred = a;
					else if (dl_edge >= dr_edge)                                          dir_pred = d;
					else                                                                  dir_pred = c;
					ds3[0][j] = e - a;
					ds3[1][j] = e - (a + bv) / 2;
					ds3[2][j] = e - med;
					ds3[3][j] = e - dir_pred;

					// FM2
					int[] pred16 = {a, c, bv, d, (a+c)>>1, (c+bv)>>1, (bv+d)>>1, (d+a)>>1,
					                (a+bv)>>1, (c+d)>>1, (a+bv+c+d)>>2, med,
					                (a+bv+c)>>2, (a+bv+d)>>2, (a+c+d)>>2, (bv+c+d)>>2};
					int best_abs = Integer.MAX_VALUE, best_delta = 0;
					for (int p : pred16) {
						int delta = e - p, abs = Math.abs(delta);
						if (abs < best_abs) { best_abs = abs; best_delta = delta; }
					}
					dfm[j] = best_delta;
				}

				// Accumulate filter deltas for best-filter selection
				for (int f = 0; f < 4; f++) {
					s1f[f] = concat(s1f[f], ds1[f]);
					s2f[f] = concat(s2f[f], ds2[f]);
					s3f[f] = concat(s3f[f], ds3[f]);
				}
				fm2Deltas.add(dfm);
			}

			// Compute entropy for each filter across all 3 channels
			double[] entS1 = new double[4], entS2 = new double[4], entS3 = new double[4];
			for (int f = 0; f < 4; f++) {
				entS1[f] = shannonBits(s1f[f]);
				entS2[f] = shannonBits(s2f[f]);
				entS3[f] = shannonBits(s3f[f]);
			}

			// FM2 entropy
			double entFM2 = shannonBits(concat(fm2Deltas.toArray(new int[0][])));

			// Best filter per method
			int bestS1 = 0, bestS2 = 0, bestS3 = 0;
			for (int f = 1; f < 4; f++) {
				if (entS1[f] < entS1[bestS1]) bestS1 = f;
				if (entS2[f] < entS2[bestS2]) bestS2 = f;
				if (entS3[f] < entS3[bestS3]) bestS3 = f;
			}

			rowBitsS1[ri]  = entS1[bestS1];
			rowBitsS2[ri]  = entS2[bestS2];
			rowBitsS3[ri]  = entS3[bestS3];
			rowBitsFM2[ri] = entFM2;

			filterCountS1[bestS1]++;
			filterCountS2[bestS2]++;
			filterCountS3[bestS3]++;

			// Gap buckets
			double pixelsPerRow = (double) n_cols * 3;
			gapBucketS1[gapBucket(rowBitsS1[ri], entFM2, pixelsPerRow)]++;
			gapBucketS2[gapBucket(rowBitsS2[ri], entFM2, pixelsPerRow)]++;
			gapBucketS3[gapBucket(rowBitsS3[ri], entFM2, pixelsPerRow)]++;
		}

		// ---- Print report ----
		double totalFM2  = sum(rowBitsFM2);
		double totalS1   = sum(rowBitsS1);
		double totalS2   = sum(rowBitsS2);
		double totalS3   = sum(rowBitsS3);
		double pixelCount = (double) n_rows * n_cols * 3;

		System.out.println("=== Overall delta entropy (interior rows, 3 channels) ===");
		System.out.println();
		System.out.printf("%-18s  %,12.0f bits  %7.4f bits/pixel%n",
				"frame map (2)", totalFM2, totalFM2 / pixelCount);
		printMethodLine("scanline (1)",  totalS1,  totalFM2, pixelCount);
		printMethodLine("scanline (2)",  totalS2,  totalFM2, pixelCount);
		printMethodLine("scanline (3)",  totalS3,  totalFM2, pixelCount);

		System.out.println();
		System.out.println("=== Per-row statistics (bits/pixel) ===");
		System.out.println();
		System.out.printf("%-18s  %8s  %8s  %8s  %8s%n",
				"", "mean", "std", "min gap", "max gap");
		System.out.printf("%-18s  %8s  %8s  %8s  %8s%n",
				"", "vs FM(2)", "of gap", "vs FM(2)", "vs FM(2)");
		System.out.println("-".repeat(62));
		printRowStats("scanline (1)", rowBitsS1, rowBitsFM2, n_cols * 3);
		printRowStats("scanline (2)", rowBitsS2, rowBitsFM2, n_cols * 3);
		printRowStats("scanline (3)", rowBitsS3, rowBitsFM2, n_cols * 3);

		System.out.println();
		System.out.println("=== Filter choice distribution (% of rows) ===");
		System.out.println();
		printFilterDist("scanline (1)", filterCountS1, NAME_S1, n_rows);
		printFilterDist("scanline (2)", filterCountS2, NAME_S2, n_rows);
		printFilterDist("scanline (3)", filterCountS3, NAME_S3, n_rows);

		System.out.println();
		System.out.println("=== Gap vs FM(2) by row — % of rows in each bucket ===");
		System.out.printf("  %-8s  %-8s  %-8s  %-8s  %-8s%n",
				"< 2%", "2-5%", "5-10%", "10-20%", "> 20%");
		printGapBuckets("scanline (1)", gapBucketS1, n_rows);
		printGapBuckets("scanline (2)", gapBucketS2, n_rows);
		printGapBuckets("scanline (3)", gapBucketS3, n_rows);
	}

	// ---- Helpers ----

	static int gapBucket(double method, double fm2, double pixels)
	{
		if (fm2 == 0) return 0;
		double gap = (method - fm2) / pixels;  // bits/pixel gap
		double fm2pp = fm2 / pixels;
		double pct = fm2pp == 0 ? 0 : 100.0 * gap / fm2pp;
		if (pct <  2) return 0;
		if (pct <  5) return 1;
		if (pct < 10) return 2;
		if (pct < 20) return 3;
		return 4;
	}

	static double sum(double[] a) { double s = 0; for (double v : a) s += v; return s; }

	static void printMethodLine(String name, double total, double fm2, double pixels)
	{
		double gap  = total - fm2;
		double pct  = 100.0 * gap / fm2;
		System.out.printf("%-18s  %,12.0f bits  %7.4f bits/pixel  gap %+.0f (+%.1f%%)%n",
				name, total, total / pixels, gap, pct);
	}

	static void printRowStats(String name, double[] method, double[] fm2, int pixPerRow)
	{
		double sumGap = 0, sumGapSq = 0, minGap = Double.MAX_VALUE, maxGap = 0;
		int n = method.length;
		for (int i = 0; i < n; i++) {
			double g = (method[i] - fm2[i]) / pixPerRow;
			sumGap   += g;
			sumGapSq += g * g;
			if (g < minGap) minGap = g;
			if (g > maxGap) maxGap = g;
		}
		double mean = sumGap / n;
		double std  = Math.sqrt(sumGapSq / n - mean * mean);
		System.out.printf("%-18s  %8.4f  %8.4f  %8.4f  %8.4f%n",
				name, mean, std, minGap, maxGap);
	}

	static void printFilterDist(String name, int[] counts, String[] filterNames, int nRows)
	{
		System.out.printf("%-18s", name);
		for (int f = 0; f < 4; f++)
			System.out.printf("  %s: %5.1f%%", filterNames[f], 100.0 * counts[f] / nRows);
		System.out.println();
	}

	static void printGapBuckets(String name, int[] buckets, int nRows)
	{
		System.out.printf("%-18s", name);
		for (int b : buckets) System.out.printf("  %6.1f%%  ", 100.0 * b / nRows);
		System.out.println();
	}
}
