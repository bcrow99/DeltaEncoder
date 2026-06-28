import java.awt.image.*;
import java.io.*;
import java.util.*;
import javax.imageio.*;

// Loads an image, selects the best channel set, runs every delta method,
// and prints how each method's delta entropy compares to frame map (1)'s
// delta entropy (which serves as the practical lower bound).
//
// Usage:  java DeltaCompare <image> [pixel_quant] [pixel_shift]
//         pixel_quant defaults to 4, pixel_shift defaults to 3.

public class DeltaCompare
{
	static final String[] SET_NAME = {
		"blue, green, red",
		"blue, red, red-green",
		"blue, red, blue-green",
		"blue, blue-green, red-green",
		"blue, blue-green, red-blue",
		"green, red, blue-green",
		"red, blue-green, red-green",
		"green, blue-green, red-green",
		"green, red-green, red-blue",
		"red, red-green, red-blue"
	};

	static final String[] DELTA_NAME = {
		"horizontal",
		"vertical",
		"average",
		"MED",
		"directional",
		"scanline (1)",
		"scanline (2)",
		"scanline (3)",
		"frame map (1)",
		"frame map (2)"
	};

	public static void main(String[] args) throws Exception
	{
		if (args.length < 1)
		{
			System.out.println("Usage: java DeltaCompare <image> [pixel_quant] [pixel_shift]");
			System.exit(0);
		}

		String filename    = args[0];
		int    pixel_quant = args.length > 1 ? Integer.parseInt(args[1]) : 4;
		int    pixel_shift = args.length > 2 ? Integer.parseInt(args[2]) : 3;

		// ---- Load image ----
		BufferedImage img = ImageIO.read(new File(filename));
		if (img == null) { System.out.println("Cannot read: " + filename); System.exit(1); }
		int xdim = img.getWidth(), ydim = img.getHeight();
		System.out.println("Image:        " + filename + "  (" + xdim + " x " + ydim + ")");
		System.out.println("pixel_quant=" + pixel_quant + "  pixel_shift=" + pixel_shift);

		int[] pixel = new int[xdim * ydim];
		PixelGrabber pg = new PixelGrabber(img, 0, 0, xdim, ydim, pixel, 0, xdim);
		pg.grabPixels();

		int[] b = new int[pixel.length], g = new int[pixel.length], r = new int[pixel.length];
		for (int i = 0; i < pixel.length; i++)
		{
			b[i] = (pixel[i] >> 16) & 0xFF;
			g[i] = (pixel[i] >>  8) & 0xFF;
			r[i] =  pixel[i]         & 0xFF;
		}

		// ---- Quantize / resize ----
		int new_xdim = xdim, new_ydim = ydim;
		if (pixel_quant != 0)
		{
			double f = pixel_quant / 10.0;
			new_xdim = xdim - (int)(f * (xdim / 2 - 2));
			new_ydim = ydim - (int)(f * (ydim / 2 - 2));
		}

		ArrayList<int[]> cl = new ArrayList<>();
		for (int[] ch : new int[][] {b, g, r})
		{
			int[] q = (pixel_quant == 0) ? ch : ResizeMapper.resize(ch, xdim, new_xdim, new_ydim);
			cl.add(pixel_shift == 0 ? q : DeltaMapper.shift(q, -pixel_shift));
		}
		cl.add(DeltaMapper.getDifference(cl.get(0), cl.get(1)));
		cl.add(DeltaMapper.getDifference(cl.get(2), cl.get(1)));
		cl.add(DeltaMapper.getDifference(cl.get(2), cl.get(0)));

		for (int i = 0; i < 6; i++)
		{
			int[] qc  = cl.get(i);
			int   min = 256;
			for (int v : qc) if (v < min) min = v;
			if (i > 2) for (int k = 0; k < qc.length; k++) qc[k] -= min;
			cl.set(i, qc);
		}

		// ---- Select best channel set ----
		int[] cs = new int[6];
		for (int i = 0; i < 6; i++)
			cs[i] = (int) Math.floor(CodeMapper.getShannonLimit(
					DeltaMapper.getIdealFrequency(cl.get(i), new_xdim, new_ydim)));

		int[] ss = {
			cs[0]+cs[1]+cs[2], cs[0]+cs[4]+cs[2], cs[0]+cs[3]+cs[2],
			cs[0]+cs[1]+cs[4], cs[0]+cs[3]+cs[5], cs[3]+cs[1]+cs[2],
			cs[3]+cs[4]+cs[2], cs[3]+cs[1]+cs[4], cs[5]+cs[1]+cs[4],
			cs[5]+cs[4]+cs[2]
		};
		int best_set = 0;
		for (int i = 1; i < 10; i++) if (ss[i] < ss[best_set]) best_set = i;

		System.out.println("Channel set:  " + SET_NAME[best_set]);
		System.out.println();

		int[] channel_id = DeltaMapper.getChannels(best_set);

		// ---- Compute delta entropy for each method, summed over 3 channels ----
		long[] delta_bits = new long[10];

		for (int i = 0; i < 3; i++)
		{
			int[] qc = cl.get(channel_id[i]);

			for (int t = 0; t < 5; t++)
				delta_bits[t] += Math.round(CodeMapper.getShannonLimit(
						DeltaMapper.getFrequency(qc, new_xdim, new_ydim, t)));

			ArrayList<int[]> res;

			res = DeltaMapper.getMedScanlineFrequency(qc, new_xdim, new_ydim);
			delta_bits[5] += Math.round(CodeMapper.getShannonLimit(res.get(0)));

			res = DeltaMapper.getScanline2Frequency(qc, new_xdim, new_ydim);
			delta_bits[6] += Math.round(CodeMapper.getShannonLimit(res.get(0)));

			res = DeltaMapper.getMixedDeltas4Frequency(qc, new_xdim, new_ydim);
			delta_bits[7] += Math.round(CodeMapper.getShannonLimit(res.get(0)));

			res = DeltaMapper.getIdealFrequency8(qc, new_xdim, new_ydim);
			delta_bits[8] += Math.round(CodeMapper.getShannonLimit(res.get(0)));

			res = DeltaMapper.getIdealFrequency16(qc, new_xdim, new_ydim);
			delta_bits[9] += Math.round(CodeMapper.getShannonLimit(res.get(0)));
		}

		long baseline = delta_bits[9];  // frame map (2) delta entropy — lower bound

		// ---- Print table ----
		System.out.printf("%-18s  %12s  %12s  %8s%n",
				"delta type", "delta bits", "vs FM(2)", "ratio");
		System.out.println("-".repeat(60));
		for (int t = 0; t < 10; t++)
		{
			long   bits  = delta_bits[t];
			long   diff  = bits - baseline;
			double ratio = (double) bits / baseline;
			String marker = (t == 9) ? "  <- lower bound" : "";
			System.out.printf("%-18s  %,12d  %+,12d  %7.3fx%s%n",
					DELTA_NAME[t], bits, diff, ratio, marker);
		}
		System.out.println();
		System.out.printf("Frame map (2) lower bound: %,d bits%n", baseline);
		System.out.printf("Image interior pixels:     %,d%n",
				(long)(new_xdim - 2) * (new_ydim - 1) * 3);
	}
}
