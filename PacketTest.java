import java.awt.image.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.zip.*;
import javax.imageio.*;

// PacketTest -- headless replica of PacketWriter's Save path, for sweeping
// segmentation settings without the GUI. Uses the real helper classes
// (StringMapper, SegmentMapper, DeltaMapper, CodeMapper, ResizeMapper,
// ArithmeticMapper), so compile it in the same folder as them. Needs the
// new SegmentMapper methods (getSegmentedData2, packSegments3,
// unpackSegments3, restore2).
//
// Usage:
//   java PacketTest <image> <pixel_quant> <pixel_shift> <config> [<config> ...]
//   config = level:merge_type:bins[:segment_type]   (segment_type defaults to 3)
// Example:
//   java PacketTest church.png 4 3 0:2:20:2 0:0:20:2 0:2:6:2 10:2:20:2
//
// Output (one line per config, in the order given):
//   level, merge type, bins, segment type, segment count, compressed segments,
//   S/U (segment bits / uncompressed bits), table bits, and the % change of
//   the segmented layout vs the whole string before entropy coding, after LZ77
//   and after Arithmetic -- each including both layouts' compressed tables.
//   Negative = segmented is smaller. "ok" = the full round trip (table,
//   unpackSegments2, restore) reproduced the original string exactly.
// Runs in parallel across all cores. segment_type 3 (with splice) can be
// extremely slow on large images; segment_type 2 is what PacketWriter uses.
public class PacketTest
{
	static final int MIN_SEGMENT_BITS = 256;
	

	// ---------- same helpers as PacketWriter ----------
	static int[] quantizeChannel(int[] channel, int pixel_shift)
	{
		if (pixel_shift == 0) return channel;
		int half = 1 << (pixel_shift - 1);
		int[] rounded = new int[channel.length];
		for (int k = 0; k < channel.length; k++) { int v = channel[k] + half; if (v > 255) v = 255; rounded[k] = v; }
		return DeltaMapper.shift(rounded, -pixel_shift);
	}

	static double binWidth(int n) { double w = 1.0 / n; while ((int)(1.0 / w) < n) w = Math.nextDown(w); return w; }

	static int getMinimumSegmentBits(int total_bits, int level)
	{
		if (level >= 10 || total_bits <= 2 * MIN_SEGMENT_BITS) return 0;
		double bits = MIN_SEGMENT_BITS * Math.pow((double) total_bits / MIN_SEGMENT_BITS, level / 10.0);
		int b = ((int) bits) / 8 * 8;
		if (b < MIN_SEGMENT_BITS) b = MIN_SEGMENT_BITS;
		if (b >= total_bits) return 0;
		return b;
	}

	@SuppressWarnings("unchecked")
	static ArrayList<byte[]> segmentString(byte[] string, int level, int merge_type, double bin, int segment_type)
	{
		ArrayList<byte[]> segs = new ArrayList<byte[]>();
		int min_bits = getMinimumSegmentBits(StringMapper.getBitlength(string), level);
		if (min_bits == 0) { segs.add(StringMapper.compressStrings(string)); return segs; }
		ArrayList result = (segment_type <= 2) ? SegmentMapper.getSegmentedData2(string, min_bits, segment_type, merge_type, bin)
		                                        : SegmentMapper.getSegmentedData(string, min_bits, segment_type, merge_type, bin);
		if (result.size() == 0) { segs.add(StringMapper.compressStrings(string)); return segs; }
		return (ArrayList<byte[]>) result.get(0);
	}

	static byte[] deflate(byte[] src)
	{
		Deflater def = new Deflater(Deflater.BEST_COMPRESSION);
		byte[] buf = new byte[src.length * 2 + 64];
		def.setInput(src); def.finish(); int len = def.deflate(buf); def.end();
		return Arrays.copyOf(buf, len);
	}

	static class Packet
	{
		ArrayList<byte[]> segments; byte[] table, ztable, payload; int bits, compressed;
		int cost() { return ztable.length + payload.length; }
	}

	static Packet makePacket(ArrayList<byte[]> segs) throws IOException
	{
		Packet p = new Packet(); p.segments = segs;
		ByteArrayOutputStream bos = new ByteArrayOutputStream(); DataOutputStream d = new DataOutputStream(bos);
		int n = segs.size(), max = 0;
		for (byte[] sg : segs) if (sg.length - 1 > max) max = sg.length - 1;
		int width = (max <= 255) ? 1 : (max <= 65535) ? 2 : 4;
		d.writeInt(n); d.writeByte(width);
		for (byte[] sg : segs) { int len = sg.length - 1; if (width == 1) d.writeByte(len); else if (width == 2) d.writeShort(len); else d.writeInt(len); }
		for (byte[] sg : segs)
		{
			d.writeByte(sg[sg.length - 1]);
			int it = StringMapper.getIterations(sg); if (it != 0 && it != 16) p.compressed++;
			p.bits += StringMapper.getBitlength(sg);
		}
		d.flush(); p.table = bos.toByteArray(); p.ztable = deflate(p.table);
		p.payload = (byte[]) SegmentMapper.packSegments3(segs).get(0);
		return p;
	}

	static byte[] unpackPacket(Packet p, byte string_data) throws Exception
	{
		Inflater inf = new Inflater(); inf.setInput(p.ztable);
		byte[] table = new byte[p.table.length]; inf.inflate(table); inf.end();
		DataInputStream d = new DataInputStream(new ByteArrayInputStream(table));
		int n = d.readInt(), width = d.readByte();
		int[] bytelength = new int[n]; byte[] data = new byte[n];
		for (int k = 0; k < n; k++) bytelength[k] = (width == 1) ? d.readUnsignedByte() : (width == 2) ? d.readUnsignedShort() : d.readInt();
		for (int k = 0; k < n; k++) data[k] = d.readByte();
		return SegmentMapper.restore2(SegmentMapper.unpackSegments3(p.payload, bytelength, data), string_data);
	}

	static boolean sameBits(byte[] a, byte[] b)
	{
		int bl = StringMapper.getBitlength(a);
		if (bl != StringMapper.getBitlength(b)) return false;
		int full = bl / 8;
		for (int k = 0; k < full; k++) if (a[k] != b[k]) return false;
		int odd = bl % 8;
		if (odd != 0) { int m = (1 << odd) - 1; if ((a[full] & m) != (b[full] & m)) return false; }
		return true;
	}

	static int lz77Size(byte[] payload) { return payload.length == 0 ? 0 : 8 + deflate(payload).length; }

	static int arithmeticSize(byte[] payload)
	{
		if (payload.length == 0) return 0;
		int min_seg = 500;   // pixel_segment = 0 (the default)
		int ns = Math.max(1, payload.length / min_seg);
		int seg_len = payload.length / ns, odd_len = seg_len + payload.length % ns;
		byte[][] sg = new byte[ns][]; int[][] fr = new int[ns][256]; int pos = 0;
		for (int m = 0; m < ns; m++) { sg[m] = new byte[m < ns - 1 ? seg_len : odd_len]; for (int k = 0; k < sg[m].length; k++) { sg[m][k] = payload[pos++]; fr[m][sg[m][k] & 0xFF]++; } }
		int size = 12;
		for (int m = 0; m < ns; m++) size += 4 + ArithmeticMapper.getIntervalValueFast(sg[m], fr[m]).length;
		int fmax = 0; for (int[] row : fr) for (int v : row) if (v > fmax) fmax = v;
		int bpe = (fmax < Byte.MAX_VALUE * 2 + 2) ? 1 : (fmax < Short.MAX_VALUE * 2 + 2) ? 2 : 4;
		byte[] fb = new byte[ns * 256 * bpe];
		for (int k = 0; k < ns; k++) for (int m = 0; m < 256; m++) { int v = fr[k][m]; int base = k * 256 * bpe + m * bpe; for (int b = 0; b < bpe; b++) fb[base + b] = (byte) (v >> (8 * b)); }
		return size + deflate(fb).length;
	}

	// ---------- image -> 3 uncompressed strings, exactly as PacketWriter.applyImpl ----------
	static byte[][] strings;
	static int[] stringBits = new int[3];

	static void prepare(String file, int pixel_quant, int pixel_shift) throws Exception
	{
		BufferedImage img = ImageIO.read(new File(file));
		int xdim = img.getWidth(), ydim = img.getHeight();
		int[] px = img.getRGB(0, 0, xdim, ydim, null, 0, xdim);
		int[][] ch = new int[3][xdim * ydim];
		for (int i = 0; i < px.length; i++) { ch[0][i] = (px[i] >> 16) & 0xff; ch[1][i] = (px[i] >> 8) & 0xff; ch[2][i] = px[i] & 0xff; }

		int nx = xdim, ny = ydim;
		if (pixel_quant != 0) { double f = pixel_quant / 10.0; nx = xdim - (int) (f * (xdim / 2 - 2)); ny = ydim - (int) (f * (ydim / 2 - 2)); }
		ArrayList<int[]> qcl = new ArrayList<int[]>();
		for (int i = 0; i < 3; i++)
			qcl.add(pixel_quant == 0 ? quantizeChannel(ch[i], pixel_shift) : quantizeChannel(ResizeMapper.resize(ch[i], xdim, nx, ny), pixel_shift));
		qcl.add(DeltaMapper.getDifference(qcl.get(0), qcl.get(1)));
		qcl.add(DeltaMapper.getDifference(qcl.get(2), qcl.get(1)));
		qcl.add(DeltaMapper.getDifference(qcl.get(2), qcl.get(0)));
		int[] sum = new int[6];
		for (int i = 0; i < 6; i++)
		{
			int[] qc = qcl.get(i); int min = 256; for (int v : qc) if (v < min) min = v;
			if (i > 2) for (int k = 0; k < qc.length; k++) qc[k] -= min;
			sum[i] = (int) Math.floor(CodeMapper.getShannonLimit(DeltaMapper.getIdealFrequency(qc, nx, ny)));
		}
		int[] set = new int[10];
		set[0]=sum[0]+sum[1]+sum[2]; set[1]=sum[0]+sum[4]+sum[2]; set[2]=sum[0]+sum[3]+sum[2]; set[3]=sum[0]+sum[1]+sum[4];
		set[4]=sum[0]+sum[3]+sum[5]; set[5]=sum[3]+sum[1]+sum[2]; set[6]=sum[3]+sum[4]+sum[2]; set[7]=sum[3]+sum[1]+sum[4];
		set[8]=sum[5]+sum[1]+sum[4]; set[9]=sum[5]+sum[4]+sum[2];
		int best = 0; for (int i = 1; i < 10; i++) if (set[i] < set[best]) best = i;
		int[] cid = DeltaMapper.getChannels(best);

		strings = new byte[3][];
		for (int i = 0; i < 3; i++)
		{
			int[] qc = qcl.get(cid[i]);
			int init = qc[0];
			int[] delta = (int[]) DeltaMapper.getAverageDeltasFromValues(qc, nx, ny).get(1);
			ArrayList dsl = StringMapper.getStringList(delta, false);
			int dmin = (int) dsl.get(0), len = (int) dsl.get(1);
			strings[i] = (byte[]) dsl.get(3);
			stringBits[i] = StringMapper.getBitlength(strings[i]);
			// sanity: the string decodes back to the exact channel (validates the transcription)
			int[] d2 = StringMapper.unpackStrings(strings[i], (int[]) dsl.get(2), nx * ny, len);
			d2[0] = 0; for (int k = 1; k < d2.length; k++) d2[k] += dmin;
			if (!Arrays.equals(DeltaMapper.getValuesFromAverageDeltas(d2, nx, ny, init), qc))
				System.err.println("  WARNING: string round trip failed on channel " + i);
		}
		System.err.println(String.format("%s: %dx%d -> %dx%d, set %d, string bits %d %d %d",
			new File(file).getName(), xdim, ydim, nx, ny, best, stringBits[0], stringBits[1], stringBits[2]));
	}

	public static void main(String[] args) throws Exception
	{
		if (args.length < 4)
		{
			System.out.println("Usage: java PacketTest <image> <pixel_quant> <pixel_shift> level:merge:bins[:segment_type] ...");
			return;
		}
		final PrintStream report = System.out;
		System.setOut(new PrintStream(OutputStream.nullOutputStream()));
		// args: image pixel_quant pixel_shift config... (config = level:merge:bins)
		String file = args[0];
		prepare(file, Integer.parseInt(args[1]), Integer.parseInt(args[2]));
		String name = new File(file).getName().replace(".png", "");

		// whole-string baseline (independent of config)
		int U = 0, W = 0, Tw = 0, lzW = 0, arW = 0;
		for (int i = 0; i < 3; i++)
		{
			ArrayList<byte[]> single = new ArrayList<byte[]>(); single.add(StringMapper.compressStrings(strings[i]));
			Packet w = makePacket(single);
			U += stringBits[i]; W += w.bits; Tw += w.ztable.length * 8;
			lzW += lz77Size(w.payload); arW += arithmeticSize(w.payload);
		}
		final double whole_pre = W + Tw, whole_lz = lzW * 8.0 + Tw, whole_ar = arW * 8.0 + Tw;
		report.println(String.format("%s: whole string %d bits (compressed to %d, W/U %.4f), LZ77 %d B, Arithmetic %d B", name, U, W, (double) W / U, lzW, arW));
		report.println(String.format("%5s %5s %5s %5s %7s %6s %7s %9s %8s %8s %8s %4s %7s", "level", "merge", "bins", "stype", "segs", "compr", "S/U", "table b", "pre %", "LZ77 %", "Arith %", "", "secs"));
		final int fU = U;

		ExecutorService pool = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
		List<Future<String>> out = new ArrayList<>();
		for (int a = 3; a < args.length; a++)
		{
			String[] c = args[a].split(":");
			final int level = Integer.parseInt(c[0]), merge = Integer.parseInt(c[1]), bins = Integer.parseInt(c[2]), stype = c.length > 3 ? Integer.parseInt(c[3]) : 3;
			out.add(pool.submit(() -> {
				long t0 = System.nanoTime();
				int nseg = 0, ncomp = 0, S = 0, Ts = 0, lz = 0, ar = 0; boolean ok = true;
				for (int i = 0; i < 3; i++)
				{
					Packet p = makePacket(segmentString(strings[i], level, merge, binWidth(bins), stype));
					if (!sameBits(strings[i], unpackPacket(p, strings[i][strings[i].length - 1]))) ok = false;
					nseg += p.segments.size(); ncomp += p.compressed; S += p.bits; Ts += p.ztable.length * 8;
					lz += lz77Size(p.payload); ar += arithmeticSize(p.payload);
				}
				double secs = (System.nanoTime() - t0) / 1e9;
				double pre = 100 * ((S + Ts) - whole_pre) / whole_pre;
				double lzd = 100 * ((lz * 8.0 + Ts) - whole_lz) / whole_lz;
				double ard = 100 * ((ar * 8.0 + Ts) - whole_ar) / whole_ar;
				return String.format("%5d %5d %5d %5d %7d %6d %7.4f %9d %+8.2f %+8.2f %+8.2f %4s %7.1f", level, merge, bins, stype, nseg, ncomp, (double) S / fU, Ts, pre, lzd, ard, ok ? "ok" : "FAIL", secs);
			}));
		}
		for (Future<String> f : out) { report.println(f.get()); report.flush(); }
		pool.shutdown();
	}
}
