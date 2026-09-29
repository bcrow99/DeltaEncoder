import java.awt.image.*;
import java.io.*;
import java.util.*;
import javax.imageio.*;
import javax.imageio.stream.*;

// RDTest: rate-distortion comparison of JPEG and the delta coder, both made
// from the same lossless original and both measured against it.
//
//   java RDTest image.png [scale] [display width]
//     scale          1 = full size (default), 2 = half size (faster), ...
//     display width  width the image is viewed at, for the "at display"
//                    columns (default 1024). Both the original and each
//                    decoded image are shrunk to it (box average) before
//                    comparing, so detail too fine to see there no longer
//                    counts as error.
//
// JPEG: Java's ImageIO writer at qualities 5-95.
// Delta coder: Color Resolution 0-6 x Pixel Resolution 0-10, block map
// (size 12, Scanline 16) with Context coding, sized as BlockWriter saves it.
public class Comparison
{
	static final String GUIDE =
		"How to read this:\n" +
		"  bpp      bits per pixel of the file (file bytes x 8 / pixels). Smaller = smaller file.\n" +
		"  PSNR     error against the original, in dB. Higher = closer. Roughly: 30 visible damage,\n" +
		"           35 good, 40 hard to tell apart, 50+ near-perfect. 99 = identical.\n" +
		"  SSIM     structural similarity, 0-1. 1 = identical; above 0.95 is usually very good.\n" +
		"  max err  largest error of any pixel value (0-255).\n" +
		"  blocky   how much stronger edges are on the 8-pixel grid than elsewhere; 1.00 = no blocks.\n" +
		"  display  the same PSNR/SSIM after shrinking to the display width.\n";

	public static void main(String[] args) throws Exception
	{
		BufferedImage image = ImageIO.read(new File(args[0]));
		int scale   = (args.length > 1) ? Integer.parseInt(args[1]) : 1;
		int display = (args.length > 2) ? Integer.parseInt(args[2]) : 1024;
		Img orig = Img.from(image, scale);
		int factor = Math.max(1, (int) Math.ceil((double) orig.w / display));
		Img orig_display = orig.shrink(factor);
		double pixels = (double) orig.w * orig.h;

		System.out.println("Image: " + args[0] + ", " + orig.w + " x " + orig.h + (scale > 1 ? " (1/" + scale + " size)" : ""));
		System.out.println("Display comparison at " + orig_display.w + " x " + orig_display.h + (factor > 1 ? " (1/" + factor + ")" : " (no shrinking needed)"));
		System.out.println();
		System.out.print(GUIDE);
		System.out.println();

		long lossless = deltaBytes(orig, 0, 0);
		System.out.println(String.format("Lossless (delta coder, no quantization): %,d bytes = %.2f bpp", lossless, lossless * 8 / pixels));
		System.out.println();

		// ---- JPEG --------------------------------------------------------------
		System.out.println("JPEG, by quality setting");
		System.out.println("  quality      bytes    bpp  |  PSNR    SSIM  max err  blocky  |  display: PSNR    SSIM  |  decoded JPEG re-coded losslessly");
		List<Point> jpeg = new ArrayList<Point>();
		for(int q : new int[] {5, 10, 15, 20, 30, 40, 50, 60, 70, 80, 90, 95})
		{
			byte[] file = writeJpeg(orig, q / 100f);
			Img    dec  = Img.from(ImageIO.read(new ByteArrayInputStream(file)), 1);
			Point  p    = measure("q" + q, file.length, pixels, orig, orig_display, dec, factor);
			jpeg.add(p);
			long recode = deltaBytes(dec, 0, 0);
			System.out.println(String.format("  %-7s %,10d  %5.2f  | %s  | %s  |  %,10d bytes = %.1f x the JPEG",
				p.name, p.bytes, p.bpp, p.fullColumns(), p.displayColumns(), recode, (double) recode / file.length));
		}
		System.out.println("  (The last column is why delta-coding a decoded JPEG is misleading: its artifacts cost");
		System.out.println("   several times the JPEG's own size to store losslessly.)");
		System.out.println();

		// ---- Delta coder ---------------------------------------------------------
		int[] color = {0, 1, 2, 3, 4, 5, 6}, pixel = {0, 2, 4, 6, 8, 10};
		Point[] delta = new Point[color.length * pixel.length];
		ViewerSupport.parallel(delta.length, t ->
		{
			int c = color[t / pixel.length], p = pixel[t % pixel.length];
			delta[t] = measure("color " + c + ", pixel " + p, deltaBytes(orig, p, c), pixels, orig, orig_display, reconstruct(orig, p, c), factor);
		});
		Arrays.sort(delta, (a, b) -> Double.compare(a.bpp, b.bpp));
		List<Point> frontier_full = frontier(delta, false), frontier_display = frontier(delta, true);
		System.out.println("Delta coder, by Color Resolution and Pixel Resolution (smallest file first)");
		System.out.println("  * = no other setting gives a smaller file with less error (full size); d = same, at display size");
		System.out.println("  setting               best      bytes    bpp  |  PSNR    SSIM  max err  blocky  |  display: PSNR    SSIM");
		for(Point p : delta)
			System.out.println(String.format("  %-18s    %s%s  %,10d  %5.2f  | %s  | %s", p.name,
				frontier_full.contains(p) ? "*" : " ", frontier_display.contains(p) ? "d" : " ", p.bytes, p.bpp, p.fullColumns(), p.displayColumns()));
		System.out.println();

		// ---- Comparison ----------------------------------------------------------
		compare("full size", frontier_full, jpeg, false);
		compare("display size", frontier_display, jpeg, true);

		// ---- Bottom line ---------------------------------------------------------
		System.out.println("Bottom line");
		for(boolean at_display : new boolean[] {false, true})
		{
			Point top = jpeg.get(jpeg.size() - 1);
			double top_psnr = at_display ? top.psnr_display : top.psnr;
			Point cheapest = null;
			for(Point p : delta) if((at_display ? p.psnr_display : p.psnr) > top_psnr) { cheapest = p; break; }
			String where = at_display ? "at display size" : "at full size   ";
			if(cheapest == null)
				System.out.println(String.format("  %s: no delta setting is closer than JPEG %s (%.2f dB).", where, top.name, top_psnr));
			else
				System.out.println(String.format("  %s: JPEG %s reaches %.2f dB at %.2f bpp. The smallest delta setting that does better is\n" +
					"                   %s: %.2f dB at %.2f bpp (%.1f x the size of %s). Anything coarser, use JPEG.",
					where, top.name, top_psnr, top.bpp, cheapest.name, at_display ? cheapest.psnr_display : cheapest.psnr, cheapest.bpp, cheapest.bpp / top.bpp, top.name));
		}
	}

	// ---- Comparison at equal file size ------------------------------------------

	static void compare(String label, List<Point> frontier, List<Point> jpeg, boolean at_display)
	{
		System.out.println("Delta coder vs JPEG at the same file size, error measured at " + label);
		System.out.println("  (best delta settings only; JPEG's PSNR interpolated between its quality steps)");
		System.out.println("  setting                bpp  |  delta PSNR   JPEG PSNR   difference");
		double cross_bpp = Double.NaN, cross_psnr = Double.NaN;
		double previous = Double.NaN; Point previous_point = null;
		for(Point p : frontier)
		{
			double mine = at_display ? p.psnr_display : p.psnr;
			double jp   = jpegPsnrAt(jpeg, p.bpp, at_display);
			String verdict;
			if(Double.isNaN(jp))
				{
					Point top = jpeg.get(jpeg.size() - 1);
					double top_psnr = at_display ? top.psnr_display : top.psnr;
					if(p.bpp < jpeg.get(0).bpp)  verdict = "smaller than JPEG's smallest file";
					else if(mine > top_psnr)      verdict = String.format("closer than JPEG's best (%s, %.2f dB)", top.name, top_psnr);
					else                          verdict = String.format("bigger than %s, yet %s is closer (%.2f dB)", top.name, top.name, top_psnr);
				}
			else
			{
				double d = mine - jp;
				verdict = String.format("%+6.2f dB  %s", d, (d >= 0) ? "delta better" : "JPEG better");
				if(!Double.isNaN(previous) && previous < 0 && d >= 0)
				{
					double t = -previous / (d - previous);
					cross_bpp  = previous_point.bpp + t * (p.bpp - previous_point.bpp);
					cross_psnr = (at_display ? previous_point.psnr_display : previous_point.psnr) + t * (mine - (at_display ? previous_point.psnr_display : previous_point.psnr));
				}
				previous = d; previous_point = p;
			}
			System.out.println(String.format("  %-18s %6.2f  |  %9.2f   %9s   %s", p.name, p.bpp, mine,
				Double.isNaN(jp) ? "-" : String.format("%.2f", jp), verdict));
		}
		if(!Double.isNaN(cross_bpp))
			System.out.println(String.format("  => Crossover near %.2f bpp / %.1f dB: below that, JPEG gives less error for the same size;", cross_bpp, cross_psnr) +
				"\n     above it, the delta coder does.");
		else
		{
			boolean all_jpeg = true;
			for(Point p : frontier) { double jp = jpegPsnrAt(jpeg, p.bpp, at_display); if(!Double.isNaN(jp) && (at_display ? p.psnr_display : p.psnr) >= jp) all_jpeg = false; }
			System.out.println(all_jpeg
				? "  => JPEG is better wherever the two overlap; the delta coder is only useful beyond JPEG's best quality."
				: "  => The delta coder is better wherever the two overlap.");
		}
		System.out.println();
	}

	// Settings on the best-rate frontier: no other setting has a smaller file
	// and higher PSNR (at full size or at display size).
	static List<Point> frontier(Point[] sorted_by_bpp, boolean at_display)
	{
		List<Point> f = new ArrayList<Point>();
		double best = -1;
		for(Point p : sorted_by_bpp)
		{
			double v = at_display ? p.psnr_display : p.psnr;
			if(v > best) { f.add(p); best = v; }
		}
		return f;
	}

	static double jpegPsnrAt(List<Point> jpeg, double bpp, boolean at_display)
	{
		for(int i = 0; i + 1 < jpeg.size(); i++)
		{
			Point a = jpeg.get(i), b = jpeg.get(i + 1);
			if(bpp >= a.bpp && bpp <= b.bpp)
			{
				double pa = at_display ? a.psnr_display : a.psnr, pb = at_display ? b.psnr_display : b.psnr;
				return pa + (bpp - a.bpp) / (b.bpp - a.bpp) * (pb - pa);
			}
		}
		return Double.NaN;
	}

	// ---- One measured result ----------------------------------------------------

	static class Point
	{
		String name; long bytes; double bpp, psnr, ssim, max, blocky, psnr_display, ssim_display;
		String fullColumns()    { return String.format("%5.2f  %6.4f  %7.0f  %6.2f", psnr, ssim, max, blocky); }
		String displayColumns() { return String.format("%5.2f  %6.4f", psnr_display, ssim_display); }
	}

	static Point measure(String name, long bytes, double pixels, Img orig, Img orig_display, Img rec, int factor)
	{
		Point p = new Point();
		p.name = name; p.bytes = bytes; p.bpp = bytes * 8 / pixels;
		p.psnr = psnr(orig, rec); p.ssim = ssim(orig, rec); p.max = maxError(orig, rec); p.blocky = blockiness(rec);
		Img rd = rec.shrink(factor);
		p.psnr_display = psnr(orig_display, rd); p.ssim_display = ssim(orig_display, rd);
		return p;
	}

	// ---- Images -----------------------------------------------------------------

	// Channels in the toolkit's order (pixel >> 16, >> 8, & 255).
	static class Img
	{
		int w, h; int[][] c;

		static Img from(BufferedImage image, int scale)
		{
			Img m = new Img();
			m.w = image.getWidth() / scale; m.h = image.getHeight() / scale;
			m.c = new int[3][m.w * m.h];
			int[] p = image.getRGB(0, 0, image.getWidth(), image.getHeight(), null, 0, image.getWidth());
			int n = scale * scale;
			for(int y = 0; y < m.h; y++)
				for(int x = 0; x < m.w; x++)
				{
					int r = 0, g = 0, b = 0;
					for(int dy = 0; dy < scale; dy++) for(int dx = 0; dx < scale; dx++)
					{
						int v = p[(y * scale + dy) * image.getWidth() + x * scale + dx];
						r += (v >> 16) & 255; g += (v >> 8) & 255; b += v & 255;
					}
					int k = y * m.w + x;
					m.c[0][k] = (r + n / 2) / n; m.c[1][k] = (g + n / 2) / n; m.c[2][k] = (b + n / 2) / n;
				}
			return m;
		}

		// Box average over factor x factor squares.
		Img shrink(int factor)
		{
			if(factor == 1) return this;
			Img m = new Img();
			m.w = w / factor; m.h = h / factor; m.c = new int[3][m.w * m.h];
			int n = factor * factor;
			for(int ch = 0; ch < 3; ch++)
				for(int y = 0; y < m.h; y++)
					for(int x = 0; x < m.w; x++)
					{
						int s = 0;
						for(int dy = 0; dy < factor; dy++) for(int dx = 0; dx < factor; dx++) s += c[ch][(y * factor + dy) * w + x * factor + dx];
						m.c[ch][y * m.w + x] = (s + n / 2) / n;
					}
			return m;
		}

		double[] luma()
		{
			double[] y = new double[w * h];
			for(int k = 0; k < y.length; k++) y[k] = 0.299 * c[0][k] + 0.587 * c[1][k] + 0.114 * c[2][k];
			return y;
		}
	}

	// ---- The delta coder, as BlockWriter ----------------------------------------

	static int[][] quantized(Img m, int pixel_quant, int pixel_shift, int[] size)
	{
		int[][] q = new int[3][];
		for(int c = 0; c < 3; c++)
		{
			int[] ch = m.c[c];
			if(pixel_quant != 0) ch = ResizeMapper.resize(ch, m.w, size[0], size[1]);
			q[c] = DeltaMapper.quantizeChannel(ch, pixel_shift);
		}
		return q;
	}

	static long deltaBytes(Img m, int pixel_quant, int pixel_shift)
	{
		int[]   size = DeltaMapper.getQuantizedSize(m.w, m.h, pixel_quant);
		int[][] q    = quantized(m, pixel_quant, pixel_shift, size);
		int[]   min  = new int[6];
		int[][] qc   = DeltaMapper.getCandidateChannels(q[0], q[1], q[2], min);
		int[]   sum  = new int[6];
		for(int i = 0; i < 6; i++) sum[i] = (int) Math.floor(CodeMapper.getShannonLimit(DeltaMapper.getIdealFrequency(qc[i], size[0], size[1])));
		int best = 0, best_sum = Integer.MAX_VALUE;
		for(int s = 0; s < 10; s++) { int[] c = DeltaMapper.getChannels(s); int t = sum[c[0]] + sum[c[1]] + sum[c[2]]; if(t < best_sum) { best_sum = t; best = s; } }
		int[] id = DeltaMapper.getChannels(best);
		int[][] ch = { qc[id[0]], qc[id[1]], qc[id[2]] };
		return 9 + 3 * 8 + DeltaMapper.getBlockMapBytes(ch, size[0], size[1], 12, 0);   // + BlockWriter's header
	}

	// What the reader shows: the coding is lossless, so the channels come
	// back as quantized; resize back, shift back.
	static Img reconstruct(Img m, int pixel_quant, int pixel_shift)
	{
		int[]   size = DeltaMapper.getQuantizedSize(m.w, m.h, pixel_quant);
		int[][] q    = quantized(m, pixel_quant, pixel_shift, size);
		Img r = new Img(); r.w = m.w; r.h = m.h; r.c = new int[3][];
		for(int c = 0; c < 3; c++)
		{
			int[] ch = q[c];
			if(pixel_quant != 0) ch = ResizeMapper.resize(ch, size[0], m.w, m.h);
			r.c[c] = (pixel_shift != 0) ? DeltaMapper.shift(ch, pixel_shift) : ch;
		}
		return r;
	}

	// ---- JPEG --------------------------------------------------------------------

	static byte[] writeJpeg(Img m, float quality) throws IOException
	{
		BufferedImage img = new BufferedImage(m.w, m.h, BufferedImage.TYPE_INT_RGB);
		int[] p = new int[m.w * m.h];
		for(int k = 0; k < p.length; k++) p[k] = (m.c[0][k] << 16) | (m.c[1][k] << 8) | m.c[2][k];
		img.setRGB(0, 0, m.w, m.h, p, 0, m.w);
		ImageWriter writer = ImageIO.getImageWritersByFormatName("jpg").next();
		ImageWriteParam param = writer.getDefaultWriteParam();
		param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
		param.setCompressionQuality(quality);
		ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		try(ImageOutputStream out = ImageIO.createImageOutputStream(bytes))
		{
			writer.setOutput(out);
			writer.write(null, new IIOImage(img, null, null), param);
		}
		writer.dispose();
		return bytes.toByteArray();
	}

	// ---- Metrics ------------------------------------------------------------------

	static double psnr(Img a, Img b)
	{
		double se = 0;
		for(int c = 0; c < 3; c++) for(int k = 0; k < a.c[c].length; k++) { double e = a.c[c][k] - b.c[c][k]; se += e * e; }
		double mse = se / (3.0 * a.c[0].length);
		return (mse == 0) ? 99.0 : 10 * Math.log10(255.0 * 255.0 / mse);
	}

	static double maxError(Img a, Img b)
	{
		int max = 0;
		for(int c = 0; c < 3; c++) for(int k = 0; k < a.c[c].length; k++) max = Math.max(max, Math.abs(a.c[c][k] - b.c[c][k]));
		return max;
	}

	// SSIM of the luma over 8x8 windows.
	static double ssim(Img ia, Img ib)
	{
		double[] a = ia.luma(), b = ib.luma();
		int w = ia.w, h = ia.h;
		double c1 = 6.5025, c2 = 58.5225, total = 0; int windows = 0;
		for(int by = 0; by + 8 <= h; by += 8)
			for(int bx = 0; bx + 8 <= w; bx += 8)
			{
				double ma = 0, mb = 0;
				for(int y = by; y < by + 8; y++) for(int x = bx; x < bx + 8; x++) { ma += a[y * w + x]; mb += b[y * w + x]; }
				ma /= 64; mb /= 64;
				double va = 0, vb = 0, cov = 0;
				for(int y = by; y < by + 8; y++) for(int x = bx; x < bx + 8; x++)
				{
					double da = a[y * w + x] - ma, db = b[y * w + x] - mb;
					va += da * da; vb += db * db; cov += da * db;
				}
				va /= 63; vb /= 63; cov /= 63;
				total += ((2 * ma * mb + c1) * (2 * cov + c2)) / ((ma * ma + mb * mb + c1) * (va + vb + c2));
				windows++;
			}
		return total / windows;
	}

	// Mean luma jump across 8-pixel boundaries / mean jump elsewhere.
	static double blockiness(Img m)
	{
		double[] y = m.luma();
		int w = m.w, h = m.h;
		double on = 0, off = 0; long n_on = 0, n_off = 0;
		for(int r = 0; r < h; r++)
			for(int x = 1; x < w; x++)
			{
				double d = Math.abs(y[r * w + x] - y[r * w + x - 1]);
				if(x % 8 == 0) { on += d; n_on++; } else { off += d; n_off++; }
			}
		for(int r = 1; r < h; r++)
			for(int x = 0; x < w; x++)
			{
				double d = Math.abs(y[r * w + x] - y[(r - 1) * w + x]);
				if(r % 8 == 0) { on += d; n_on++; } else { off += d; n_off++; }
			}
		return (on / n_on) / Math.max(1e-9, off / n_off);
	}
}
