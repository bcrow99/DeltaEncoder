import java.util.*;

public class ImageSegmentDemo
{
	public static void main(String[] args) throws Exception
	{
		String filename = args.length >= 1 ? args[0] : "real_test_image.png";
		int channelIndex = args.length >= 2 ? Integer.parseInt(args[1]) : 0;
		int x0 = args.length >= 3 ? Integer.parseInt(args[2]) : 10;
		int y0 = args.length >= 4 ? Integer.parseInt(args[3]) : 10;
		int dim = args.length >= 5 ? Integer.parseInt(args[4]) : 32;
		double budgetSeconds = args.length >= 6 ? Double.parseDouble(args[5]) : 120.0;
		int maxThreads = args.length >= 7 ? Integer.parseInt(args[6]) : 16;

		System.out.println("Loading '" + filename + "', channel " + channelIndex
			+ ", segment at (" + x0 + "," + y0 + "), requested " + dim + "x" + dim);

		ImageSegmentExtractor.Segment seg = ImageSegmentExtractor.extractFromImage(filename, channelIndex, x0, y0, dim);

		System.out.println("Actual segment size: " + seg.actualWidth + "x" + seg.actualHeight
			+ " (" + seg.symbols.length + " symbols)");
		System.out.println("Distinct values in this segment: " + seg.rankToValue.length);
		System.out.println("Raw pixel values (first 20): " + Arrays.toString(Arrays.copyOf(seg.rawValues, Math.min(20, seg.rawValues.length))));

		List<FractionMapper.BigFraction> candidates = new ArrayList<>();
		int[] numer = {1, 3, 1, 2, 1, 3, 2, 3, 4, 1};
		int[] denom = {20, 20, 5, 5, 2, 5, 3, 4, 5, 4};
		for (int k = 0; k < numer.length; k++) candidates.add(FractionMapper.BigFraction.of(numer[k], denom[k]));

		System.out.println("\nCompressing (racing " + candidates.size() + " candidate targets across up to "
			+ Math.min(maxThreads, candidates.size()) + " threads, budget " + budgetSeconds + "s)...");

		long t0 = System.nanoTime();
		SegmentSteerDemo.CompressResult result = SegmentSteerDemo.compressSegment(
			seg.symbols, seg.histogram, candidates, maxThreads, budgetSeconds, 5_000_000L);
		long t1 = System.nanoTime();

		if (!result.success)
		{
			System.out.println("FAILED: no candidate target succeeded within budget ("
				+ (t1 - t0) / 1_000_000 + " ms elapsed).");
			return;
		}

		System.out.println("\nSUCCESS");
		System.out.println("  target used:      " + result.target + "  (" + result.target.toDouble() + ")");
		System.out.println("  backtracks:        " + result.backtracks);
		System.out.println("  elapsed:           " + result.elapsedMillis + " ms");
		System.out.println("  serialized cost:   " + String.format("%.1f", result.costBits) + " bits ("
			+ String.format("%.1f", result.costBits / 8) + " bytes)");
		int n = seg.symbols.length;
		System.out.println("  original size:     " + (n * 8) + " bits (" + n + " bytes, if 1 byte/symbol)");
		System.out.println("  overhead ratio:    " + String.format("%.2fx", result.costBits / (n * 8)));

		int[] decodedSymbols = SegmentSteerDemo.decompressSegment(result.orderings, seg.histogram, result.target, n);
		int[] decodedValues = ImageSegmentExtractor.symbolsToValues(decodedSymbols, seg.rankToValue);

		boolean matches = Arrays.equals(decodedValues, seg.rawValues);
		System.out.println("  decoded matches original REAL pixel values exactly: " + matches);
		if (!matches)
		{
			for (int i = 0; i < n; i++)
				if (decodedValues[i] != seg.rawValues[i])
				{ System.out.println("  first mismatch at index " + i + ": expected " + seg.rawValues[i] + " got " + decodedValues[i]); break; }
		}
	}
}
