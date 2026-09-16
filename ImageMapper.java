import java.util.ArrayList;

/**
 * Merged image-processing utility class.
 *
 * Combines two originally separate ImageMapper.java files that happened to
 * share a name:
 *
 *  1) The shrink/expand pyramid demo's operators (shrinkAvg, expandGradient,
 *     expandGradientSaddle, refineWithSignBits, the not-divisible-by-4
 *     boundary handling, and error measurement) -- used by ShrinkExpander.java.
 *  2) A separate, larger image-dilation / area-resampling / registration
 *     utility class (smooth, dilateImage family, avgArea*Transform,
 *     getGradient/getVariance, getTranslation, expandX, contract, etc.).
 *
 * NOTE ON A NAME COLLISION: both source files independently defined
 * shrinkAvg(int[][]) with slightly different behavior -- the pyramid
 * version rounds ((sum + 2) / 4), the utility version truncates (sum / 4).
 * Since ShrinkExpander.java (and the pyramid error-analysis work earlier in
 * this project) specifically depends on the *rounding* behavior, that
 * version was kept as the single shrinkAvg(int[][]). The utility file's
 * truncating int[][] overload was dropped as redundant; its shrinkAvg(double[][])
 * overload doesn't collide (different erasure) and was kept unchanged.
 *
 * The dilation-utility methods below also had 4 confirmed bugs fixed
 * (verified by compiling and testing against the pre-fix behavior):
 *   - dilateImage(): location_type==3 read src[k-1] instead of src[k+xdim]
 *     for its second neighbor check.
 *   - dilateImageVertical(): same location_type==3 wrong-variable bug.
 *   - dilateImageDiagonal(): location_type==5's fourth diagonal neighbor
 *     check (isInterpolated[k+xdim+1]) incorrectly re-read
 *     src[k-xdim-1] instead of src[k+xdim+1].
 *   - dilateImageDiagonal(): location_type==4 used '=' instead of '+=' for
 *     its first neighbor accumulation (tested harmless in isolation, but
 *     fixed for consistency/robustness against future edits).
 *   - expandX(double[][], int iterations) returned a throwaway new
 *     double[1][1] instead of the source array when iterations <= 0;
 *     now returns src unchanged in that case.
 *
 * version 2.0: added flat int[]/boolean[] (single-array-plus-width)
 * overloads of the pyramid operators, for consistency with how
 * DeltaWriter/DeltaReader/DeltaMapper/StringMapper/ResizeMapper represent
 * images everywhere else in that project (a flat array plus a separate
 * width, rather than int[][]) -- see the "Pyramid demo operators, flat-
 * array overloads" section. Genuine standalone implementations, not thin
 * wrappers that round-trip through the int[][] versions, so no per-call
 * conversion overhead when chaining many pyramid levels. Also promoted
 * padTo() from private to public, since DeltaWriter/DeltaReader need to
 * compute padded dimensions directly.
 */
public class ImageMapper {

    // ===================================================================
    // Pyramid demo operators (used by ShrinkExpander.java)
    // ===================================================================


    // ---- pyramid operators ----

    public static int[][] shrinkAvg(int[][] src) {
        int ydim = src.length;
        int xdim = src[0].length;
        int _xdim = xdim / 2;
        int _ydim = ydim / 2;
        int[][] dst = new int[_ydim][_xdim];
        for (int i = 0; i < ydim - 1; i += 2) {
            int k = i / 2;
            for (int j = 0; j < xdim - 1; j += 2) {
                int m = j / 2;
                dst[k][m] = (src[i][j] + src[i][j + 1] + src[i + 1][j] + src[i + 1][j + 1] + 2) / 4;
            }
        }
        return dst;
    }

    public static int[][] expandGradient(int[][] avg) {
        int _ydim = avg.length;
        int _xdim = avg[0].length;
        int ydim = _ydim * 2;
        int xdim = _xdim * 2;
        int[][] dst = new int[ydim][xdim];

        for (int k = 0; k < _ydim; k++) {
            for (int m = 0; m < _xdim; m++) {
                double A  = avg[k][m];
                double gx = horizGrad(avg, k, m);
                double gy = vertGrad(avg, k, m);

                double tl = A - 0.5 * gy - 0.5 * gx;
                double tr = A - 0.5 * gy + 0.5 * gx;
                double bl = A + 0.5 * gy - 0.5 * gx;
                double br = A + 0.5 * gy + 0.5 * gx;

                int i = 2 * k, j = 2 * m;
                dst[i][j]         = clamp(Math.round(tl));
                dst[i][j + 1]     = clamp(Math.round(tr));
                dst[i + 1][j]     = clamp(Math.round(bl));
                dst[i + 1][j + 1] = clamp(Math.round(br));
            }
        }
        return dst;
    }

    private static double horizGrad(int[][] avg, int k, int m) {
        int _xdim = avg[0].length;
        int m0 = Math.max(m - 1, 0);
        int m1 = Math.min(m + 1, _xdim - 1);
        if (m0 == m1) return 0.0;
        return (avg[k][m1] - avg[k][m0]) / (2.0 * (m1 - m0));
    }

    private static double vertGrad(int[][] avg, int k, int m) {
        int _ydim = avg.length;
        int k0 = Math.max(k - 1, 0);
        int k1 = Math.min(k + 1, _ydim - 1);
        if (k0 == k1) return 0.0;
        return (avg[k1][m] - avg[k0][m]) / (2.0 * (k1 - k0));
    }

    /**
     * Saddle-capable variant of expandGradient: adds one more Taylor term,
     * the mixed partial (cross) derivative gxy, estimated from the four
     * DIAGONAL neighboring block averages. A plane (A + gy*r + gx*c) can
     * only tilt; it can't represent a block sitting at a saddle point,
     * where the surface curves opposite ways along the two diagonals (e.g.
     * neighbors arranged low/high/high/low in a checkerboard). Adding
     * gxy*r*c captures that.
     *
     * Uses no information beyond what's already in avg[][] -- no new
     * bits, no side channel, just a higher-order read of the same
     * neighbor data expandGradient already has. The four correction terms
     * (+,-,-,+) still sum to zero, so the block mean is still preserved
     * exactly, same as the plane-only version.
     */
    public static int[][] expandGradientSaddle(int[][] avg) {
        int _ydim = avg.length;
        int _xdim = avg[0].length;
        int ydim = _ydim * 2;
        int xdim = _xdim * 2;
        int[][] dst = new int[ydim][xdim];

        for (int k = 0; k < _ydim; k++) {
            for (int m = 0; m < _xdim; m++) {
                double A   = avg[k][m];
                double gx  = horizGrad(avg, k, m);
                double gy  = vertGrad(avg, k, m);
                double gxy = crossGrad(avg, k, m);

                double tl = A - 0.5 * gy - 0.5 * gx + 0.25 * gxy;
                double tr = A - 0.5 * gy + 0.5 * gx - 0.25 * gxy;
                double bl = A + 0.5 * gy - 0.5 * gx - 0.25 * gxy;
                double br = A + 0.5 * gy + 0.5 * gx + 0.25 * gxy;

                int i = 2 * k, j = 2 * m;
                dst[i][j]         = clamp(Math.round(tl));
                dst[i][j + 1]     = clamp(Math.round(tr));
                dst[i + 1][j]     = clamp(Math.round(bl));
                dst[i + 1][j + 1] = clamp(Math.round(br));
            }
        }
        return dst;
    }

    // Mixed second difference from the four diagonal block-average
    // neighbors -- same clamp-to-edge boundary handling as horizGrad/
    // vertGrad, generalized to two axes at once.
    private static double crossGrad(int[][] avg, int k, int m) {
        int _ydim = avg.length, _xdim = avg[0].length;
        int k0 = Math.max(k - 1, 0);
        int k1 = Math.min(k + 1, _ydim - 1);
        int m0 = Math.max(m - 1, 0);
        int m1 = Math.min(m + 1, _xdim - 1);
        if (k0 == k1 || m0 == m1) return 0.0;
        double num = avg[k1][m1] - avg[k1][m0] - avg[k0][m1] + avg[k0][m0];
        double denom = (2.0 * (k1 - k0)) * (2.0 * (m1 - m0));
        return num / denom;
    }

    /**
     * Optional refinement pass using one side-information bit per pixel.
     *
     * geq[i][j] == true  means the ORIGINAL pixel at (i,j) was >= the
     *                    average of the block it came from (avg[i/2][j/2]).
     * geq[i][j] == false means it was strictly less than that average.
     *
     * (Always satisfiable: since avg = round(sum/4) now rather than floor,
     * this no longer guarantees at least one pixel is >= avg the way the
     * un-rounded version did, but the four true pixel values and their
     * exact mean are still mutually consistent by construction, so the
     * constraint set below is always feasible for them.)
     *
     * Alternates two projections until the four predicted values are
     * consistent with both the bits and the exact block mean -- a small
     * instance of POCS (alternating projection onto convex sets).
     */
    public static int[][] refineWithSignBits(int[][] avg, int[][] predicted, boolean[][] geq) {
        int ydim = predicted.length;
        int xdim = predicted[0].length;
        int[][] dst = new int[ydim][xdim];

        for (int i = 0; i < ydim - 1; i += 2) {
            int k = i / 2;
            for (int j = 0; j < xdim - 1; j += 2) {
                int m = j / 2;
                double A = avg[k][m];

                double[] p = {
                    predicted[i][j],     predicted[i][j + 1],
                    predicted[i + 1][j], predicted[i + 1][j + 1]
                };
                boolean[] bit = {
                    geq[i][j],     geq[i][j + 1],
                    geq[i + 1][j], geq[i + 1][j + 1]
                };

                for (int iter = 0; iter < 10; iter++) {
                    for (int t = 0; t < 4; t++) {
                        boolean predGeq = p[t] >= A;
                        if (predGeq != bit[t]) {
                            p[t] = bit[t] ? A : A - 1;
                        }
                    }
                    double sum  = p[0] + p[1] + p[2] + p[3];
                    double corr = (sum - 4 * A) / 4.0;
                    for (int t = 0; t < 4; t++) p[t] -= corr;
                }

                dst[i][j]         = clamp(Math.round(p[0]));
                dst[i][j + 1]     = clamp(Math.round(p[1]));
                dst[i + 1][j]     = clamp(Math.round(p[2]));
                dst[i + 1][j + 1] = clamp(Math.round(p[3]));
            }
        }
        return dst;
    }

    private static int clamp(long v) {
        if (v < 0) return 0;
        if (v > 255) return 255;
        return (int) v;
    }

    // Parameterized clamp, used only by the flat-array pyramid methods
    // below -- DeltaWriter/DeltaReader can legitimately hand these a
    // DIFFERENCE channel (blue-green, red-green, red-blue), which gets
    // shifted by its own observed minimum to become non-negative but is
    // never rescaled, so its values can genuinely run up to 510 (two
    // [0,255] channels differing by as much as 255 in either direction)
    // -- not just 255. The int[][] methods above stay hardcoded to 255,
    // since ShrinkExpander only ever feeds them genuine, always-in-range
    // RGB channels.
    private static int clamp(long v, int maxValue) {
        if (v < 0) return 0;
        if (v > maxValue) return maxValue;
        return (int) v;
    }

    // Damping factor for a set of raw offsets from a center value: the
    // LARGEST s in [0,1] such that center + s*offset stays within
    // [0,maxValue] for every offset given. Used instead of clamping each
    // predicted corner independently -- independent clamping breaks the
    // exact mean-preservation the unclamped plane-fit formula otherwise
    // guarantees (the offset terms cancel exactly across the four
    // corners), which is what turns a graceful falloff into visible
    // hard-edged noise once pyramid levels chain without correction.
    // Scaling every offset down by the SAME factor keeps that
    // cancellation intact -- the corners just converge smoothly toward
    // the center (blur) instead of individual corners hitting a wall.
    private static double computeDampingFactor(double center, double[] rawOffsets, int maxValue) {
        double s = 1.0;
        for (double off : rawOffsets) {
            if (off > 0) s = Math.min(s, (maxValue - center) / off);
            else if (off < 0) s = Math.min(s, center / (-off));
        }
        return Math.max(0.0, s);
    }

    // geq[i][j] == true means the original pixel at (i,j) was >= the
    // (rounded) average of the block it came from; false means it was
    // strictly less than that average. This is the one bit of side
    // information refineWithSignBits uses per pixel.
    public static boolean[][] buildGeqBits(int[][] orig, int[][] avg) {
        int h = orig.length, w = orig[0].length;
        boolean[][] geq = new boolean[h][w];
        for (int i = 0; i < h; i++) {
            int k = i / 2;
            for (int j = 0; j < w; j++) {
                int m = j / 2;
                geq[i][j] = orig[i][j] >= avg[k][m];
            }
        }
        return geq;
    }

    // ---- boundary handling for dimensions not divisible by a pyramid's PAD_MULTIPLE ----

    // Pads out to the next multiple of `multiple` by replicating the last
    // real row/column. shrinkAvg's loops start at 0 and step by 2, so a
    // leftover row/column can only ever occur at the bottom/right edge --
    // that's the only side that ever needs padding. Replication keeps the
    // local gradient at the seam at ~zero, a neutral assumption, rather
    // than the fake dark edge zero-padding would introduce right where the
    // gradient estimate can least afford it.
    public static int[][] padEdgeReplicate(int[][] src, int multiple) {
        int h = src.length, w = src[0].length;
        int newH = padTo(h, multiple);
        int newW = padTo(w, multiple);
        if (newH == h && newW == w) return src;
        int[][] dst = new int[newH][newW];
        for (int y = 0; y < newH; y++) {
            int sy = Math.min(y, h - 1);
            for (int x = 0; x < newW; x++) {
                dst[y][x] = src[sy][Math.min(x, w - 1)];
            }
        }
        return dst;
    }

    public static int padTo(int dim, int multiple) {
        int rem = dim % multiple;
        return rem == 0 ? dim : dim + (multiple - rem);
    }

    // Crops back down to the original, unpadded dimensions (top-left
    // aligned, since padding was only ever added at the bottom/right).
    public static int[][] crop(int[][] src, int h, int w) {
        int[][] dst = new int[h][w];
        for (int y = 0; y < h; y++) {
            System.arraycopy(src[y], 0, dst[y], 0, w);
        }
        return dst;
    }

    // ---- error measurement ----

    // Returns { meanSignedError, meanAbsError, pixelCount } for one channel.
    public static double[] errorStats(int[][] orig, int[][] recon) {
        int h = orig.length, w = orig[0].length;
        long n = (long) h * w;
        double sumErr = 0, sumAbs = 0;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int e = recon[y][x] - orig[y][x];
                sumErr += e;
                sumAbs += Math.abs(e);
            }
        }
        return new double[]{ sumErr / n, sumAbs / n, n };
    }

    // ===================================================================
    // Pyramid demo operators, FLAT-ARRAY overloads (version 2.0)
    //
    // Genuine standalone implementations operating on a flat int[]/
    // boolean[] plus a separate width, matching how images are
    // represented everywhere else in the DeltaWriter/DeltaReader project
    // (as opposed to int[][] above, used only by ShrinkExpander's demo).
    // Same algorithms as the int[][] versions above; verified against
    // them directly (see the accompanying test harness) rather than
    // assumed identical from a mechanical transcription.
    // ===================================================================

    public static int[] shrinkAvg(int[] src, int xdim) {
        int ydim = src.length / xdim;
        int _xdim = xdim / 2;
        int _ydim = ydim / 2;
        int[] dst = new int[_xdim * _ydim];
        for (int i = 0; i < ydim - 1; i += 2) {
            int k = i / 2;
            for (int j = 0; j < xdim - 1; j += 2) {
                int m = j / 2;
                dst[k * _xdim + m] = (src[i * xdim + j] + src[i * xdim + j + 1]
                                     + src[(i + 1) * xdim + j] + src[(i + 1) * xdim + j + 1] + 2) / 4;
            }
        }
        return dst;
    }

    private static double horizGradFlat(int[] avg, int xdim, int k, int m) {
        int m0 = Math.max(m - 1, 0);
        int m1 = Math.min(m + 1, xdim - 1);
        if (m0 == m1) return 0.0;
        return (avg[k * xdim + m1] - avg[k * xdim + m0]) / (2.0 * (m1 - m0));
    }

    private static double vertGradFlat(int[] avg, int xdim, int ydim, int k, int m) {
        int k0 = Math.max(k - 1, 0);
        int k1 = Math.min(k + 1, ydim - 1);
        if (k0 == k1) return 0.0;
        return (avg[k1 * xdim + m] - avg[k0 * xdim + m]) / (2.0 * (k1 - k0));
    }

    private static double crossGradFlat(int[] avg, int xdim, int ydim, int k, int m) {
        int k0 = Math.max(k - 1, 0);
        int k1 = Math.min(k + 1, ydim - 1);
        int m0 = Math.max(m - 1, 0);
        int m1 = Math.min(m + 1, xdim - 1);
        if (k0 == k1 || m0 == m1) return 0.0;
        double num = avg[k1 * xdim + m1] - avg[k1 * xdim + m0] - avg[k0 * xdim + m1] + avg[k0 * xdim + m0];
        double denom = (2.0 * (k1 - k0)) * (2.0 * (m1 - m0));
        return num / denom;
    }

    // xdim here is avg's width (the SMALLER, pre-expand width). maxValue
    // is the legitimate upper bound for THIS channel's values -- 255 for
    // a raw RGB channel, up to 510 for a shifted difference channel; see
    // clamp(long,int)'s comment for why this can't just be hardcoded.
    public static int[] expandGradient(int[] avg, int xdim, int maxValue) {
        int _xdim = xdim;
        int _ydim = avg.length / xdim;
        int ydim = _ydim * 2;
        int newXdim = _xdim * 2;
        int[] dst = new int[newXdim * ydim];

        for (int k = 0; k < _ydim; k++) {
            for (int m = 0; m < _xdim; m++) {
                double A  = avg[k * _xdim + m];
                double gx = horizGradFlat(avg, _xdim, k, m);
                double gy = vertGradFlat(avg, _xdim, _ydim, k, m);

                double[] raw = { -0.5 * gy - 0.5 * gx, -0.5 * gy + 0.5 * gx, 0.5 * gy - 0.5 * gx, 0.5 * gy + 0.5 * gx };
                double s = computeDampingFactor(A, raw, maxValue);

                int i = 2 * k, j = 2 * m;
                dst[i * newXdim + j]             = clamp(Math.round(A + s * raw[0]), maxValue);
                dst[i * newXdim + j + 1]         = clamp(Math.round(A + s * raw[1]), maxValue);
                dst[(i + 1) * newXdim + j]       = clamp(Math.round(A + s * raw[2]), maxValue);
                dst[(i + 1) * newXdim + j + 1]   = clamp(Math.round(A + s * raw[3]), maxValue);
            }
        }
        return dst;
    }

    public static int[] expandGradientSaddle(int[] avg, int xdim, int maxValue) {
        int _xdim = xdim;
        int _ydim = avg.length / xdim;
        int ydim = _ydim * 2;
        int newXdim = _xdim * 2;
        int[] dst = new int[newXdim * ydim];

        for (int k = 0; k < _ydim; k++) {
            for (int m = 0; m < _xdim; m++) {
                double A   = avg[k * _xdim + m];
                double gx  = horizGradFlat(avg, _xdim, k, m);
                double gy  = vertGradFlat(avg, _xdim, _ydim, k, m);
                double gxy = crossGradFlat(avg, _xdim, _ydim, k, m);

                double[] raw = {
                    -0.5 * gy - 0.5 * gx + 0.25 * gxy, -0.5 * gy + 0.5 * gx - 0.25 * gxy,
                    0.5 * gy - 0.5 * gx - 0.25 * gxy,  0.5 * gy + 0.5 * gx + 0.25 * gxy
                };
                double s = computeDampingFactor(A, raw, maxValue);

                int i = 2 * k, j = 2 * m;
                dst[i * newXdim + j]             = clamp(Math.round(A + s * raw[0]), maxValue);
                dst[i * newXdim + j + 1]         = clamp(Math.round(A + s * raw[1]), maxValue);
                dst[(i + 1) * newXdim + j]       = clamp(Math.round(A + s * raw[2]), maxValue);
                dst[(i + 1) * newXdim + j + 1]   = clamp(Math.round(A + s * raw[3]), maxValue);
            }
        }
        return dst;
    }

    // origXdim is orig's (the LARGER array's) width; avg is half that width.
    public static boolean[] buildGeqBits(int[] orig, int[] avg, int origXdim) {
        int h = orig.length / origXdim;
        int w = origXdim;
        int avgXdim = w / 2;
        boolean[] geq = new boolean[h * w];
        for (int i = 0; i < h; i++) {
            int k = i / 2;
            for (int j = 0; j < w; j++) {
                int m = j / 2;
                geq[i * w + j] = orig[i * w + j] >= avg[k * avgXdim + m];
            }
        }
        return geq;
    }

    // predictedXdim is predicted's (the LARGER array's) width; avg is half that width.
    public static int[] refineWithSignBits(int[] avg, int[] predicted, boolean[] geq, int predictedXdim, int maxValue) {
        int xdim = predictedXdim;
        int ydim = predicted.length / predictedXdim;
        int avgXdim = xdim / 2;
        int[] dst = new int[predicted.length];

        for (int i = 0; i < ydim - 1; i += 2) {
            int k = i / 2;
            for (int j = 0; j < xdim - 1; j += 2) {
                int m = j / 2;
                double A = avg[k * avgXdim + m];

                double[] p = {
                    predicted[i * xdim + j],           predicted[i * xdim + j + 1],
                    predicted[(i + 1) * xdim + j],     predicted[(i + 1) * xdim + j + 1]
                };
                boolean[] bit = {
                    geq[i * xdim + j],           geq[i * xdim + j + 1],
                    geq[(i + 1) * xdim + j],     geq[(i + 1) * xdim + j + 1]
                };

                // If the UNCORRECTED gradient prediction disagrees with
                // ANY of the 4 sign bits, that's direct evidence the
                // gradient estimate for this whole block is unreliable
                // (a real edge or fine detail the plane fit can't
                // represent) -- not just that one corner. Restarting the
                // correction from flat (all four = A) instead of from
                // that untrustworthy prediction avoids the alternative:
                // three corners keeping their (still-trusted) gradient-
                // predicted contrast while the fourth gets pulled toward
                // A alone, which is exactly what produces an isolated,
                // locally-inconsistent speck. Flattening the whole block
                // trades some detail for a smooth, coherent one.
                boolean anyMismatch = false;
                for (int t = 0; t < 4; t++) if ((p[t] >= A) != bit[t]) anyMismatch = true;
                if (anyMismatch) { p[0] = p[1] = p[2] = p[3] = A; }

                for (int iter = 0; iter < 10; iter++) {
                    for (int t = 0; t < 4; t++) {
                        boolean predGeq = p[t] >= A;
                        if (predGeq != bit[t]) {
                            p[t] = bit[t] ? A : A - 1;
                        }
                    }
                    double sum  = p[0] + p[1] + p[2] + p[3];
                    double corr = (sum - 4 * A) / 4.0;
                    for (int t = 0; t < 4; t++) p[t] -= corr;
                }

                // Damp each corner's DEVIATION FROM A (not from 0) rather
                // than clamping p[t] independently. The POCS loop above
                // already drives sum(p) to exactly 4A, so damping toward
                // A preserves that mean exactly regardless of s, and
                // since A + s*(p[t]-A) stays on the same side of A as
                // p[t] for any s in [0,1], it can never flip a corner's
                // sign relative to A -- so it can't undo the sign
                // constraint the loop above just established.
                double[] dev = { p[0] - A, p[1] - A, p[2] - A, p[3] - A };
                double s = computeDampingFactor(A, dev, maxValue);

                dst[i * xdim + j]             = clamp(Math.round(A + s * dev[0]), maxValue);
                dst[i * xdim + j + 1]         = clamp(Math.round(A + s * dev[1]), maxValue);
                dst[(i + 1) * xdim + j]       = clamp(Math.round(A + s * dev[2]), maxValue);
                dst[(i + 1) * xdim + j + 1]   = clamp(Math.round(A + s * dev[3]), maxValue);
            }
        }
        return dst;
    }

    public static int[] padEdgeReplicate(int[] src, int xdim, int ydim, int newXdim, int newYdim) {
        if (newXdim == xdim && newYdim == ydim) return src;
        int[] dst = new int[newXdim * newYdim];
        for (int y = 0; y < newYdim; y++) {
            int sy = Math.min(y, ydim - 1);
            for (int x = 0; x < newXdim; x++) {
                dst[y * newXdim + x] = src[sy * xdim + Math.min(x, xdim - 1)];
            }
        }
        return dst;
    }

    public static int[] crop(int[] src, int xdim, int ydim, int newXdim, int newYdim) {
        int[] dst = new int[newXdim * newYdim];
        for (int y = 0; y < newYdim; y++) {
            System.arraycopy(src, y * xdim, dst, y * newXdim, newXdim);
        }
        return dst;
    }

    // ===================================================================
    // Image dilation / area-resampling / registration utility methods
    // ===================================================================

	public static void smooth(int src[], int xdim, int ydim, double smooth_factor, int number_of_iterations, int dst[])
	{
		double even[] = new double[xdim * ydim];
		double odd[] = new double[xdim * ydim];
		double weight[] = new double[xdim * ydim];
		double product[] = new double[xdim * ydim];
		double current_src[];
		double current_dst[];
		double dx, dy, dxy, sum, factor;
		double total_weights;
		int index;
		int i, j, k;

		factor = 1.0 / (2 * smooth_factor * smooth_factor);
		current_src = odd;
		current_dst = even;

		for (i = 0; i < xdim * ydim; i++)
			current_src[i] = current_dst[i] = (double) src[i];

		for (i = 0; i < number_of_iterations; i++)
		{
			if (i % 2 == 0)
			{
				current_src = even;
				current_dst = odd;
			} else
			{
				current_src = odd;
				current_dst = even;
			}

			for (j = 1; j < ydim - 1; j++)
			{
				index = j * xdim;
				for (k = 1; k < xdim - 1; k++)
				{
					index++;
					dx = (current_src[index - 1] - current_src[index + 1]) / 2.;
					dy = (current_src[index - xdim] - current_src[index + xdim]) / 2.;
					dxy = dx * dx + dy * dy;
					weight[index] = java.lang.Math.exp(-dxy * factor);
					product[index] = weight[index] * current_src[index];
				}
			}

			for (j = 2; j < ydim - 2; j++)
			{
				index = j * xdim + 2;
				total_weights = weight[index - xdim - 1] + weight[index - xdim] + weight[index - xdim + 1]
						+ weight[index - 1] + weight[index] + weight[index + 1] + weight[index + xdim - 1]
						+ weight[index + xdim] + weight[index + xdim + 1];
				sum = product[index - xdim - 1] + product[index - xdim] + product[index - xdim + 1] + product[index - 1]
						+ product[index] + product[index + 1] + product[index + xdim - 1] + product[index + xdim]
						+ product[index + xdim + 1];

				for (k = 2; k < xdim - 2; k++)
				{
					current_dst[index] = sum / total_weights;

					total_weights += weight[index + xdim + 2] + weight[index + 2] + weight[index - xdim + 2]
							- weight[index - xdim - 1] - weight[index - 1] - weight[index + xdim - 1];

					sum += product[index - xdim + 2] + product[index + 2] + product[index + xdim + 2]
							- product[index - xdim - 1] - product[index - 1] - product[index + xdim - 1];
					index++;
				}
			}
		}
		for (i = 0; i < xdim * ydim; i++)
			dst[i] = (int) current_dst[i];
	}

	public static int getLocationType(int xindex, int yindex, int xdim, int ydim)
	{
		int location_type = 0;
		if (yindex == 0)
		{
			if (xindex == 0)
			{
				location_type = 1;
			} else if (xindex % xdim != xdim - 1)
			{
				location_type = 2;
			} else
			{
				location_type = 3;
			}
		} else if (yindex % ydim != ydim - 1)
		{
			if (xindex == 0)
			{
				location_type = 4;
			} else if (xindex % xdim != xdim - 1)
			{
				location_type = 5;
			} else
			{
				location_type = 6;
			}
		} else
		{
			if (xindex == 0)
			{
				location_type = 7;
			} else if (xindex % xdim != xdim - 1)
			{
				location_type = 8;
			} else
			{
				location_type = 9;
			}
		}
		return (location_type);
	}

	// This function assumes the sample density is greater in y than x.
	public static double[][] getImageDilation(double src[][], boolean isInterpolated[][])
	{
		int ydim = src.length;
		int xdim = src[0].length;

		double dst[][] = new double[ydim][xdim];


		double source[];
		double dest[];
		double gray1[] = new double[xdim * ydim];
		double gray2[] = new double[xdim * ydim];
		boolean isAssigned[] = new boolean[xdim * ydim];
		int number_of_uninterpolated_cells = 0;
		int number_of_iterations = 0;

		// Reformat data for low level code that uses a single index.
		for (int i = 0; i < ydim; i++)
		{
			for (int j = 0; j < xdim; j++)
			{
				int k = i * xdim + j;
				gray1[k] = src[i][j];
				isAssigned[k] = isInterpolated[i][j];
				if(isAssigned[k] == false)
					number_of_uninterpolated_cells++;
			}
		}

		//System.out.println("The number of uninterpolated cells is " + number_of_uninterpolated_cells);

		boolean even = true;
		int previous_number_of_uninterpolated_cells = 0;

		while(number_of_uninterpolated_cells != 0  && number_of_uninterpolated_cells  != previous_number_of_uninterpolated_cells)
		{
			previous_number_of_uninterpolated_cells = number_of_uninterpolated_cells;
			number_of_iterations++;
			if (even == true)
			{
				source = gray1;
				dest = gray2;
				even = false;
			}
			else
			{
				source = gray2;
				dest = gray1;
				even = true;
			}

			dilateImageVertical(source, isAssigned, xdim, ydim, 0, dest);

			number_of_uninterpolated_cells = 0;
			for (int i = 0; i < xdim * ydim; i++)
			{
				if(isAssigned[i] == false)
					number_of_uninterpolated_cells++;
			}
		}

		// If dilating image vertically didn't complete,
		// do a diagonal image dilation.
		previous_number_of_uninterpolated_cells = 0;
		while(number_of_uninterpolated_cells != 0 && previous_number_of_uninterpolated_cells != number_of_uninterpolated_cells)
		{
			System.out.println("Vertical dilation did not complete.");
			if(even == true)
			{
				source = gray1;
				dest = gray2;
				even = false;
			}
			else
			{
				source = gray2;
				dest = gray1;
				even = true;
			}
			dilateImageDiagonal(source, isAssigned, xdim, ydim, 0, dest);
			previous_number_of_uninterpolated_cells = number_of_uninterpolated_cells;
			number_of_uninterpolated_cells          = 0;
			for (int i = 0; i < xdim * ydim; i++)
			{
				if(isAssigned[i] == false)
					number_of_uninterpolated_cells++;
			}
		}

		// If dilating image diagonally didn't complete,
		// do a regular image dilation.
		previous_number_of_uninterpolated_cells = 0;
		while(number_of_uninterpolated_cells != 0 && previous_number_of_uninterpolated_cells != number_of_uninterpolated_cells)
		{
			System.out.println("Diagonal dilation did not complete.");
			if(even == true)
			{
				source = gray1;
				dest = gray2;
				even = false;
			}
			else
			{
				source = gray2;
				dest = gray1;
				even = true;
			}
			dilateImage(source, isAssigned, xdim, ydim, 0, dest);
			previous_number_of_uninterpolated_cells = number_of_uninterpolated_cells;
			number_of_uninterpolated_cells          = 0;
			for (int i = 0; i < xdim * ydim; i++)
			{
				if(isAssigned[i] == false)
					number_of_uninterpolated_cells++;
			}
		}
		System.out.println("The final number of uninterpolated cells is " + number_of_uninterpolated_cells);
		if(even == true)
		{
			int k = 0;
			for (int i = 0; i < ydim; i++)
			{
				for (int j = 0; j < xdim; j++)
				{
					dst[i][j] = gray1[k++];
				}
			}
		}
		else
		{
			int k = 0;
			for (int i = 0; i < ydim; i++)
			{
				for (int j = 0; j < xdim; j++)
				{
					dst[i][j] = gray2[k++];
				}
			}
		}

		System.out.println("The number of iterations was " + number_of_iterations);
		return dst;
	}


	// This function modifies values in isInterpolated and dst, and can be called
	// multiple times until all the values in isInterpolated are true.
	// Theoretically, it should complete even if only one pixel has been interpolated at the start.
	// Also, using single index into image to keep low level code simple--will have
	// to reformat data for processing.
	public static void dilateImage(double src[], boolean isInterpolated[], int xdim, int ydim, int neighbor_threshold, double dst[])
	{
		boolean wasInterpolated[] = new boolean[xdim * ydim];
		for (int i = 0; i < ydim; i++)
		{
			for (int j = 0; j < xdim; j++)
			{
				int k = i * xdim + j;
				if(isInterpolated[k])
				{
					dst[k]             = src[k];
					wasInterpolated[k] = true;
				}
				else
				{
					// Orthogonal weight is 1.
					double diagonal_weight  = 0.7071;
					double total_weight     = 0;
					double value            = 0.;
					int number_of_neighbors = 0;

					int location_type = getLocationType(j, i, xdim, ydim);

					if(location_type == 1)
					{
						// Orthogonal.
						if (isInterpolated[k + 1])
						{
							number_of_neighbors++;
							total_weight += 1.;
							value        += src[k + 1];
						}
						if (isInterpolated[k + xdim])
						{
							number_of_neighbors++;
							total_weight += 1.;
							value        += src[k + xdim];
						}

						// Diagonal.
						if (isInterpolated[k + xdim + 1])
						{
							number_of_neighbors++;
							total_weight += diagonal_weight;
							value        += diagonal_weight * src[k + xdim + 1];
						}
					}
					else if(location_type == 2)
					{
						if(isInterpolated[k - 1])
						{
							number_of_neighbors++;
							total_weight += 1.;
							value += src[k - 1];
						}
						if(isInterpolated[k + 1])
						{
							number_of_neighbors++;
							total_weight += 1.;
							value += src[k + 1];
						}
						if(isInterpolated[k + xdim])
						{
							number_of_neighbors++;
							total_weight += 1.;
							value += src[k + xdim];
						}

						if(isInterpolated[k + xdim - 1])
						{
							number_of_neighbors++;
							total_weight += diagonal_weight;
							value += diagonal_weight * src[k + xdim - 1];
						}
						if(isInterpolated[k + xdim + 1])
						{
							number_of_neighbors++;
							total_weight += diagonal_weight;
							value += diagonal_weight * src[k + xdim + 1];
						}
					}
					else if(location_type == 3)
					{
						if(isInterpolated[k - 1])
						{
							number_of_neighbors++;
							total_weight += 1.;
							value += src[k - 1];
						}
						if(isInterpolated[k + xdim])
						{
							number_of_neighbors++;
							total_weight += 1.;
							value += src[k + xdim];
						}

						if(isInterpolated[k + xdim - 1])
						{
							number_of_neighbors++;
							total_weight += diagonal_weight;
							value += diagonal_weight * src[k + xdim - 1];
						}
					}
					else if(location_type == 4)
					{
						if(isInterpolated[k - xdim])
						{
							number_of_neighbors++;
							total_weight += 1.;
							value += src[k - xdim];
						}
						if(isInterpolated[k + xdim])
						{
							number_of_neighbors++;
							total_weight += 1.;
							value += src[k + xdim];
						}
						if(isInterpolated[k + 1])
						{
							number_of_neighbors++;
							total_weight += 1.;
							value += src[k + 1];
						}

						if(isInterpolated[k - xdim + 1])
						{
							number_of_neighbors++;
							total_weight += diagonal_weight;
							value += diagonal_weight * src[k - xdim + 1];
						}
						if(isInterpolated[k + xdim + 1])
						{
							number_of_neighbors++;
							total_weight += diagonal_weight;
							value += diagonal_weight * src[k + xdim + 1];
						}
					}
					else if(location_type == 5)
					{
						if(isInterpolated[k - xdim])
						{
							number_of_neighbors++;
							total_weight += 1.;
							value += src[k - xdim];
						}
						if(isInterpolated[k + xdim])
						{
							number_of_neighbors++;
							total_weight += 1.;
							value += src[k + xdim];
						}
						if(isInterpolated[k - 1])
						{
							number_of_neighbors++;
							total_weight += 1.;
							value += src[k - 1];
						}
						if(isInterpolated[k + 1])
						{
							number_of_neighbors++;
							total_weight += 1.;
							value += src[k + 1];
						}

						if(isInterpolated[k - xdim - 1])
						{
							number_of_neighbors++;
							total_weight += diagonal_weight;
							value += diagonal_weight * src[k - xdim - 1];
						}
						if(isInterpolated[k + xdim - 1])
						{
							number_of_neighbors++;
							total_weight += diagonal_weight;
							value += diagonal_weight * src[k + xdim - 1];
						}

						if(isInterpolated[k - xdim + 1])
						{
							number_of_neighbors++;
							total_weight += diagonal_weight;
							value += diagonal_weight * src[k - xdim + 1];
						}
						if(isInterpolated[k + xdim + 1])
						{
							number_of_neighbors++;
							total_weight += diagonal_weight;
							value += diagonal_weight * src[k + xdim + 1];
						}
					}
					else if(location_type == 6)
					{
						if(isInterpolated[k - xdim])
						{
							number_of_neighbors++;
							total_weight += 1.;
							value += src[k - xdim];
						}
						if(isInterpolated[k + xdim])
						{
							number_of_neighbors++;
							total_weight += 1.;
							value += src[k + xdim];
						}
						if(isInterpolated[k - 1])
						{
							number_of_neighbors++;
							total_weight += 1.;
							value += src[k - 1];
						}

						if(isInterpolated[k - xdim - 1])
						{
							number_of_neighbors++;
							total_weight += diagonal_weight;
							value += diagonal_weight * src[k - xdim - 1];
						}
						if(isInterpolated[k + xdim - 1])
						{
							number_of_neighbors++;
							total_weight += diagonal_weight;
							value += diagonal_weight * src[k + xdim - 1];
						}
					}
					else if(location_type == 7)
					{
						if(isInterpolated[k - xdim])
						{
							number_of_neighbors++;
							total_weight += 1.;
							value += src[k - xdim];
						}
						if(isInterpolated[k + 1])
						{
							number_of_neighbors++;
							total_weight += 1.;
							value += src[k + 1];
						}

						if(isInterpolated[k - xdim + 1])
						{
							number_of_neighbors++;
							total_weight += diagonal_weight;
							value += diagonal_weight * src[k - xdim + 1];
						}
					}
					else if(location_type == 8)
					{
						if(isInterpolated[k - xdim])
						{
							number_of_neighbors++;
							total_weight += 1.;
							value += src[k - xdim];
						}
						if(isInterpolated[k - 1])
						{
							number_of_neighbors++;
							total_weight += 1.;
							value += src[k - 1];
						}
						if(isInterpolated[k + 1])
						{
							number_of_neighbors++;
							total_weight += 1.;
							value += src[k + 1];
						}

						if(isInterpolated[k - xdim - 1])
						{
							number_of_neighbors++;
							total_weight += diagonal_weight;
							value += diagonal_weight * src[k - xdim - 1];
						}
						if(isInterpolated[k - xdim + 1])
						{
							number_of_neighbors++;
							total_weight += diagonal_weight;
							value += diagonal_weight * src[k - xdim + 1];
						}
					}
					else if(location_type == 9)
					{
						if(isInterpolated[k - xdim])
						{
							number_of_neighbors++;
							total_weight += 1.;
							value += src[k - xdim];
						}
						if(isInterpolated[k - 1])
						{
							number_of_neighbors++;
							total_weight += 1.;
							value += src[k - 1];
						}

						if(isInterpolated[k - xdim - 1])
						{
							number_of_neighbors++;
							total_weight += diagonal_weight;
							value += diagonal_weight * src[k - xdim - 1];
						}
					}

					if(number_of_neighbors > neighbor_threshold)
					{
						// Found required number of neighbors this iteration, set value.
						value /= total_weight;
						dst[k] = (int) value;
						wasInterpolated[k] = true;
						// System.out.println("Number of neighbors was " + number_of_neighbors);
					}
					else
					{
						dst[k] = 0;
						wasInterpolated[k] = false;
						// No neighbors, set value to zero.
					}
				}
			}
		}

		// We need to reset the boolean array that got passed into the function, since it gets reused.
		for (int i = 0; i < xdim * ydim; i++)
		{
			isInterpolated[i] = wasInterpolated[i];
		}
	}

	// This function is not guaranteed to complete, and returns an incomplete result after reaching a limit.
	// The problem is if one column is completely unpopulated it will recurse endlessly.  Still useful--a combination
	// of this and the regular dilateImage produces a better result than regular dilateImage alone.
	public static void dilateImageVertical(double src[], boolean isInterpolated[], int xdim, int ydim, int neighbor_threshold, double dst[])
	{
		boolean wasInterpolated[] = new boolean[xdim * ydim];
		for (int i = 0; i < ydim; i++)
		{
			for (int j = 0; j < xdim; j++)
			{
				int k = i * xdim + j;
				if (isInterpolated[k])
				{
					dst[k]             = src[k];
					wasInterpolated[k] = true;
				}
				else
				{
					//double diagonal_weight  = 0.7071;
					double total_weight     = 0;
					double value            = 0.;
					int number_of_neighbors = 0;
					int location_type       = getLocationType(j, i, xdim, ydim);

					if(location_type == 1)
					{
						if(isInterpolated[k + xdim])
						{
							total_weight += 1.;
							value += src[k + xdim];
							number_of_neighbors++;
						}
					}
					else if(location_type == 2)
					{
						if(isInterpolated[k + xdim])
						{
							total_weight += 1.;
							value += src[k + xdim];
							number_of_neighbors++;
						}
					}
					else if(location_type == 3)
					{
						if (isInterpolated[k + xdim])
						{
							total_weight += 1.;
							value += src[k + xdim];
							number_of_neighbors++;
						}
					}
					else if(location_type == 4)
					{
						if(isInterpolated[k - xdim])
						{
							total_weight += 1.;
							value += src[k - xdim];
							number_of_neighbors++;
						}
						if(isInterpolated[k + xdim])
						{
							total_weight += 1.;
							value += src[k + xdim];
							number_of_neighbors++;
						}
					}
					else if(location_type == 5)
					{
						if(isInterpolated[k - xdim])
						{
							total_weight += 1.;
							value += src[k - xdim];
							number_of_neighbors++;
						}
						if(isInterpolated[k + xdim])
						{
							total_weight += 1.;
							value += src[k + xdim];
							number_of_neighbors++;
						}
					}
					else if(location_type == 6)
					{
						if(isInterpolated[k - xdim])
						{
							total_weight += 1.;
							value += src[k - xdim];
							number_of_neighbors++;
						}
						if(isInterpolated[k + xdim])
						{
							total_weight += 1.;
							value += src[k + xdim];
							number_of_neighbors++;
						}
					}
					else if(location_type == 7)
					{
						if(isInterpolated[k - xdim])
						{
							total_weight += 1.;
							value += src[k - xdim];
							number_of_neighbors++;
						}
					}
					else if(location_type == 8)
					{
						if(isInterpolated[k - xdim])
						{
							total_weight += 1.;
							value += src[k - xdim];
							number_of_neighbors++;
						}
					}
					else if(location_type == 9)
					{
						if(isInterpolated[k - xdim])
						{
							total_weight += 1.;
							value += src[k - xdim];
							number_of_neighbors++;
						}
					}

					if(number_of_neighbors > neighbor_threshold)
					{
						value /= total_weight;
						dst[k] = (int) value;
						wasInterpolated[k] = true;
					}
					else
					{
						dst[k] = 0;
						wasInterpolated[k] = false;
					}
				}
			}
		}

		// Reset the boolean array since it gets reused.
		for (int i = 0; i < xdim * ydim; i++)
		{
			isInterpolated[i] = wasInterpolated[i];
		}
	}


	public static void dilateImageDiagonal(double src[], boolean isInterpolated[], int xdim, int ydim, int neighbor_threshold, double dst[])
	{
		boolean wasInterpolated[] = new boolean[xdim * ydim];
		for (int i = 0; i < ydim; i++)
		{
			for (int j = 0; j < xdim; j++)
			{
				int k = i * xdim + j;
				if(isInterpolated[k])
				{
					dst[k]             = src[k];
					wasInterpolated[k] = true;
				}
				else
				{
					double total_weight     = 0;
					double value            = 0.;
					int number_of_neighbors = 0;

					int location_type = getLocationType(j, i, xdim, ydim);

					if(location_type == 1)
					{
						if (isInterpolated[k + xdim + 1])
						{
							number_of_neighbors++;
							total_weight += 1.;
							value        += src[k + xdim + 1];
						}
					}
					else if(location_type == 2)
					{
						if(isInterpolated[k + xdim - 1])
						{
							number_of_neighbors++;
							total_weight += 1.;
							value        += src[k + xdim - 1];
						}
						if(isInterpolated[k + xdim + 1])
						{
							number_of_neighbors++;
							total_weight += 1.;
							value        += src[k + xdim + 1];
						}
					}
					else if(location_type == 3)
					{
						if(isInterpolated[k + xdim - 1])
						{
							number_of_neighbors++;
							total_weight += 1.;
							value        += src[k + xdim - 1];
						}
					}
					else if(location_type == 4)
					{
						if(isInterpolated[k - xdim + 1])
						{
							number_of_neighbors++;
							total_weight += 1.;
							value        += src[k - xdim + 1];
						}
						if(isInterpolated[k + xdim + 1])
						{
							number_of_neighbors++;
							total_weight += 1.;
							value        += src[k + xdim + 1];
						}
					}
					else if(location_type == 5)
					{
						if(isInterpolated[k - xdim - 1])
						{
							number_of_neighbors++;
							total_weight += 1.;
							value        += src[k - xdim - 1];
						}
						if(isInterpolated[k + xdim - 1])
						{
							number_of_neighbors++;
							total_weight += 1.;
							value        += src[k + xdim - 1];
						}

						if(isInterpolated[k - xdim + 1])
						{
							number_of_neighbors++;
							total_weight += 1.;
							value        += src[k - xdim + 1];
						}
						if(isInterpolated[k + xdim + 1])
						{
							number_of_neighbors++;
							total_weight += 1.;
							value        += src[k + xdim + 1];
						}
					}
					else if(location_type == 6)
					{
						if(isInterpolated[k - xdim - 1])
						{
							number_of_neighbors++;
							total_weight += 1.;
							value       += src[k - xdim - 1];
						}
						if(isInterpolated[k + xdim - 1])
						{
							number_of_neighbors++;
							total_weight += 1.;
							value        += src[k + xdim - 1];
						}
					}
					else if(location_type == 7)
					{
						if(isInterpolated[k - xdim + 1])
						{
							number_of_neighbors++;
							total_weight += 1.;
							value        += src[k - xdim + 1];
						}
					}
					else if(location_type == 8)
					{
						if(isInterpolated[k - xdim - 1])
						{
							number_of_neighbors++;
							total_weight += 1.;
							value        += src[k - xdim - 1];
						}
						if(isInterpolated[k - xdim + 1])
						{
							number_of_neighbors++;
							total_weight += 1.;
							value        += src[k - xdim + 1];
						}
					}
					else if(location_type == 9)
					{
						if(isInterpolated[k - xdim - 1])
						{
							number_of_neighbors++;
							total_weight += 1.;
							value        += src[k - xdim - 1];
						}
					}

					if(number_of_neighbors > neighbor_threshold)
					{
						value /= total_weight;
						dst[k] = (int) value;
						wasInterpolated[k] = true;
					}
					else
					{
						dst[k] = 0;
						wasInterpolated[k] = false;
					}
				}
			}
		}

		for (int i = 0; i < xdim * ydim; i++)
		{
			isInterpolated[i] = wasInterpolated[i];
		}
	}

	public static int[] avgAreaXTransform(int source[], int xdim, int ydim, int new_xdim)
	{
		double differential         = (double) xdim / (double) new_xdim;
		int    weight               = (int)(differential * xdim) * 1000;
		int    factor               = xdim * 1000;
		double real_position        = 0.;
		int    current_whole_number = 0;

		int [] start_fraction   = new int[new_xdim];
		int [] end_fraction     = new int[new_xdim];
		int [] number_of_pixels = new int[new_xdim];
		for(int i = 0; i < new_xdim; i++)
		{
		    double  previous_position     = real_position;
		    int     previous_whole_number = current_whole_number;

		    real_position       += differential;
		    current_whole_number = (int) (real_position);
			number_of_pixels[i]  = current_whole_number - previous_whole_number;
			start_fraction[i]    = (int) (1000. * (1. - (previous_position - (double) (previous_whole_number))));
			end_fraction[i]      = (int) (1000. * (real_position - (double) (current_whole_number)));
		}

		int[] dest = new int[ydim * new_xdim];
		for (int y = 0; y < ydim; y++)
		{
			int i = y * new_xdim;
			int j = y * xdim;
			for (int x = 0; x < new_xdim - 1; x++)
			{
				if (number_of_pixels[x] == 0)
				{
					dest[i] = source[j];
					i++;
				}
				else
				{
					int total = start_fraction[x] * xdim * source[j];
					j++;
					int k = number_of_pixels[x] - 1;
					while (k > 0)
					{
						total += factor * source[j];
						j++;
						k--;
					}
					total += end_fraction[x] * xdim * source[j];
					total /= weight;
					dest[i] = total;
					i++;
				}
			}

			int x = new_xdim - 1;
			if (number_of_pixels[x] == 0)
				dest[i] = source[j];
			else
			{
				int total = start_fraction[x] * xdim * source[j];
				j++;
				int k = number_of_pixels[x] - 1;
				while (k > 0)
				{
					total += factor * source[j];
					j++;
					k--;
				}
				total /= weight - end_fraction[x] * xdim;
				dest[i] = total;
			}
		}
		return(dest);
	}

	public static int[][] avgAreaXTransform(int src[][], int new_xdim)
	{
		int ydim = src.length;
		int xdim = src[0].length;
		int[][] dst = new int[ydim][new_xdim];

		int [] source = new int[xdim * ydim];
		int [] dest   = new int[new_xdim * ydim];

		for (int i = 0; i < ydim; i++)
		{
			for (int j = 0; j < xdim; j++)
			{
				int k = i * xdim + j;
				source[k] = src[i][j];
			}
		}

		double differential         = (double) xdim / (double) new_xdim;
		int    weight               = (int)(differential * xdim) * 1000;
		int    factor               = xdim * 1000;
		double real_position        = 0.;
		int    current_whole_number = 0;

		int [] start_fraction   = new int[new_xdim];
		int [] end_fraction     = new int[new_xdim];
		int [] number_of_pixels = new int[new_xdim];

		for(int i = 0; i < new_xdim; i++)
		{
		    double  previous_position     = real_position;
		    int     previous_whole_number = current_whole_number;

		    real_position       += differential;
		    current_whole_number = (int) (real_position);
			number_of_pixels[i]  = current_whole_number - previous_whole_number;
			start_fraction[i]    = (int) (1000. * (1. - (previous_position - (double) (previous_whole_number))));
			end_fraction[i]      = (int) (1000. * (real_position - (double) (current_whole_number)));
		}

		for (int y = 0; y < ydim; y++)
		{
			int i = y * new_xdim;
			int j = y * xdim;
			for (int x = 0; x < new_xdim - 1; x++)
			{
				if (number_of_pixels[x] == 0)
				{
					dest[i] = source[j];
					i++;
				}
				else
				{
					int total = start_fraction[x] * xdim * source[j];
					j++;
					int k = number_of_pixels[x] - 1;
					while (k > 0)
					{
						total += factor * source[j];
						j++;
						k--;
					}
					total += end_fraction[x] * xdim * source[j];
					total /= weight;
					dest[i] = total;
					i++;
				}
			}

			int x = new_xdim - 1;
			if (number_of_pixels[x] == 0)
				dest[i] = source[j];
			else
			{
				int total = start_fraction[x] * xdim * source[j];
				j++;
				int k = number_of_pixels[x] - 1;
				while (k > 0)
				{
					total += factor * source[j];
					j++;
					k--;
				}
				total /= weight - end_fraction[x] * xdim;
				dest[i] = total;
			}
		}

		for (int i = 0; i < ydim; i++)
		{
			for (int j = 0; j < new_xdim; j++)
			{
				int k = i * new_xdim + j;
				dst[i][j] = dest[k];
			}
		}
		return(dst);
	}

	public static int [] avgAreaYTransform(int src[], int xdim, int ydim, int new_ydim)
	{
		double differential         = (double) ydim / (double) new_ydim;
		int    weight               = (int) (differential * ydim) * 1000;
		int    factor               = ydim * 1000;
		double real_position        = 0.;
		int    current_whole_number = 0;

		int [] start_fraction   = new int[new_ydim];
		int [] end_fraction     = new int[new_ydim];
		int [] number_of_pixels = new int[new_ydim];
		for (int i = 0; i < new_ydim; i++)
		{
			double previous_position     = real_position;
			int    previous_whole_number = current_whole_number;

			real_position       += differential;
			current_whole_number = (int) (real_position);
			number_of_pixels[i]  = current_whole_number - previous_whole_number;
			start_fraction[i]    = (int) (1000. * (1. - (previous_position - (double) (previous_whole_number))));
			end_fraction[i]      = (int) (1000. * (real_position - (double) (current_whole_number)));
		}

		int [] dst = new int[xdim * new_ydim];
		for (int x = 0; x < xdim; x++)
		{
			int i = x;
			int j = x;
			for (int y = 0; y < new_ydim - 1; y++)
			{
				if (number_of_pixels[y] == 0)
				{
					dst[i] = src[j];
					i += xdim;
				}
				else
				{
					int total = start_fraction[y] * ydim * src[j];
					j += xdim;
					int k = number_of_pixels[y] - 1;
					while (k > 0)
					{
						total += factor * src[j];
						j += xdim;
						k--;
					}
					total += end_fraction[y] * ydim * src[j];
					total /= weight;
					dst[i] = total;
					i += xdim;
				}
			}
			int y = new_ydim - 1;
			if (number_of_pixels[y] == 0)
				dst[i] = src[j];
			else
			{
				int total = start_fraction[y] * ydim * src[j];
				j += xdim;
				int k = number_of_pixels[y] - 1;
				while (k > 0)
				{
					total += factor * src[j];
					j += xdim;
					k--;
				}
				total /= weight - end_fraction[y] * ydim;
				dst[i] = total;
			}
		}
		return(dst);
	}

	public static int[][] avgAreaYTransform(int src[][], int new_ydim)
	{
		int ydim = src.length;
		int xdim = src[0].length;

		int [] source = new int[xdim * ydim];
		int [] dest   = new int[xdim * new_ydim];
		for (int i = 0; i < ydim; i++)
		{
			int k = 0;
			for (int j = 0; j < xdim; j++)
			{
				source[k] = src[i][j];
				k++;
			}
		}

		double differential         = (double) ydim / (double) new_ydim;
		int    weight               = (int) (differential * ydim) * 1000;
		int    factor               = ydim * 1000;
		double real_position        = 0.;
		int    current_whole_number = 0;

		int [] start_fraction   = new int[new_ydim];
		int [] end_fraction     = new int[new_ydim];
		int [] number_of_pixels = new int[new_ydim];
		for (int i = 0; i < new_ydim; i++)
		{
			double previous_position     = real_position;
			int    previous_whole_number = current_whole_number;

			real_position       += differential;
			current_whole_number = (int) (real_position);
			number_of_pixels[i]  = current_whole_number - previous_whole_number;
			start_fraction[i]    = (int) (1000. * (1. - (previous_position - (double) (previous_whole_number))));
			end_fraction[i]      = (int) (1000. * (real_position - (double) (current_whole_number)));
		}

		for (int x = 0; x < xdim; x++)
		{
			int i = x;
			int j = x;
			for (int y = 0; y < new_ydim - 1; y++)
			{
				if (number_of_pixels[y] == 0)
				{
					dest[i] = source[j];
					i += xdim;
				}
				else
				{
					int total = start_fraction[y] * ydim * source[j];
					j += xdim;
					int k = number_of_pixels[y] - 1;
					while (k > 0)
					{
						total += factor * source[j];
						j += xdim;
						k--;
					}
					total += end_fraction[y] * ydim * source[j];
					total /= weight;
					dest[i] = total;
					i += xdim;
				}
			}
			int y = new_ydim - 1;
			if (number_of_pixels[y] == 0)
				dest[i] = source[j];
			else
			{
				int total = start_fraction[y] * ydim * source[j];
				j += xdim;
				int k = number_of_pixels[y] - 1;
				while (k > 0)
				{
					total += factor * source[j];
					j += xdim;
					k--;
				}
				total /= weight - end_fraction[y] * ydim;
				dest[i] = total;
			}
		}

		int[][] dst = new int[new_ydim][xdim];
		for (int i = 0; i < new_ydim; i++)
		{
			int k = 0;
			for (int j = 0; j < xdim; j++)
			{
				dst[i][j] = dest[k];
				k++;
			}
		}

		return(dst);

	}

	public static int [] avgAreaTransform(int src[], int xdim, int ydim, int new_xdim, int new_ydim)
	{
		int [] intermediate = avgAreaXTransform(src, xdim, ydim, new_xdim);
	    int [] dst          = avgAreaYTransform(intermediate, new_xdim, ydim, new_ydim);
	    return(dst);
	}

	public static int [][] avgAreaTransform(int src[][], int new_xdim, int new_ydim)
	{
		int ydim = src.length;
		int xdim = src[0].length;

		int [] source = new int[xdim * ydim];
		for (int i = 0; i < ydim; i++)
		{
			int k = 0;
			for (int j = 0; j < xdim; j++)
			{
				source[k] = src[i][j];
				k++;
			}
		}
		int [] intermediate = avgAreaXTransform(source, xdim, ydim, new_xdim);
		int [] dest         = avgAreaYTransform(intermediate, new_xdim, ydim, new_ydim);

		int[][] dst = new int[new_ydim][new_xdim];
		for (int i = 0; i < new_ydim; i++)
		{
			int k = 0;
			for (int j = 0; j < xdim; j++)
			{
				dst[i][j] = dest[k];
				k++;
			}
		}

		return(dst);
	}

	public static ArrayList[][] getGradient(int src[][])
	{
		int ydim = src.length;
		int xdim = src[0].length;
		ArrayList[][] dst = new ArrayList[ydim][xdim];

		for (int i = 0; i < ydim; i++)
		{
			for (int j = 0; j < xdim; j++)
			{
				int type = getLocationType(j, i, xdim, ydim);
				double xgradient = 0;
				double ygradient = 0;
				if (type == 1)
				{
					xgradient = Double.NaN;
					ygradient = Double.NaN;

					ArrayList gradient_list = new ArrayList();
					gradient_list.add(xgradient);
					gradient_list.add(ygradient);
					dst[i][j] = gradient_list;
				} else if (type == 2)
				{
					xgradient = (src[i][j + 1] - src[i][j - 1]) + (src[i + 1][j + 1] - src[i + 1][j - 1]);
					xgradient /= 2;
					ygradient = Double.NaN;

					ArrayList gradient_list = new ArrayList();
					gradient_list.add(xgradient);
					gradient_list.add(ygradient);
					dst[i][j] = gradient_list;
				} else if (type == 3)
				{
					xgradient = Double.NaN;
					ygradient = Double.NaN;

					ArrayList gradient_list = new ArrayList();
					gradient_list.add(xgradient);
					gradient_list.add(ygradient);
					dst[i][j] = gradient_list;
				} else if (type == 4)
				{
					xgradient = Double.NaN;
					ygradient = (src[i + 1][j] - src[i - 1][j]) + (src[i + 1][j + 1] - src[i - 1][j + 1]);
					ygradient /= 2;

					ArrayList gradient_list = new ArrayList();
					gradient_list.add(xgradient);
					gradient_list.add(ygradient);
					dst[i][j] = gradient_list;
				} else if (type == 5)
				{
					xgradient = src[i - 1][j + 1] - src[i - 1][j - 1] + src[i][j + 1] - src[i][j - 1]
							+ src[i + 1][j + 1] - src[i + 1][j - 1];
					xgradient /= 3;
					ygradient = src[i + 1][j - 1] - src[i - 1][j - 1] + src[i + 1][j] - src[i - 1][j]
							+ src[i + 1][j + 1] - src[i - 1][j + 1];
					ygradient /= 3;

					ArrayList gradient_list = new ArrayList();
					gradient_list.add(xgradient);
					gradient_list.add(ygradient);
					dst[i][j] = gradient_list;
				} else if (type == 6)
				{
					xgradient = Double.NaN;
					ygradient = src[i + 1][j - 1] - src[i - 1][j - 1] + src[i + 1][j] - src[i - 1][j];
					ygradient /= 2;

					ArrayList gradient_list = new ArrayList();
					gradient_list.add(xgradient);
					gradient_list.add(ygradient);
					dst[i][j] = gradient_list;
				} else if (type == 7)
				{
					xgradient = Double.NaN;
					ygradient = Double.NaN;

					ArrayList gradient_list = new ArrayList();
					gradient_list.add(xgradient);
					gradient_list.add(ygradient);
					dst[i][j] = gradient_list;
				} else if (type == 8)
				{
					xgradient = (src[i - 1][j + 1] - src[i - 1][j - 1]) + (src[i][j + 1] - src[i][j - 1]);
					xgradient /= 2;
					ygradient = Double.NaN;

					ArrayList gradient_list = new ArrayList();
					gradient_list.add(xgradient);
					gradient_list.add(ygradient);
					dst[i][j] = gradient_list;
				} else if (type == 9)
				{
					xgradient = Double.NaN;
					ygradient = Double.NaN;

					ArrayList gradient_list = new ArrayList();
					gradient_list.add(xgradient);
					gradient_list.add(ygradient);
					dst[i][j] = gradient_list;
				}
			}
		}
		return (dst);
	}

	public static ArrayList[][] getGradient(double src[][])
	{
		int ydim = src.length;
		int xdim = src[0].length;
		ArrayList[][] dst = new ArrayList[ydim][xdim];

		for (int i = 0; i < ydim; i++)
		{
			for (int j = 0; j < xdim; j++)
			{
				int type = getLocationType(j, i, xdim, ydim);
				double xgradient = 0;
				double ygradient = 0;
				if (type == 1) { xgradient = Double.NaN; ygradient = Double.NaN; ArrayList gradient_list = new ArrayList(); gradient_list.add(xgradient); gradient_list.add(ygradient); dst[i][j] = gradient_list; }
				else if (type == 2) { xgradient = (src[i][j + 1] - src[i][j - 1]) + (src[i + 1][j + 1] - src[i + 1][j - 1]); xgradient /= 2; ygradient = Double.NaN; ArrayList gradient_list = new ArrayList(); gradient_list.add(xgradient); gradient_list.add(ygradient); dst[i][j] = gradient_list; }
				else if (type == 3) { xgradient = Double.NaN; ygradient = Double.NaN; ArrayList gradient_list = new ArrayList(); gradient_list.add(xgradient); gradient_list.add(ygradient); dst[i][j] = gradient_list; }
				else if (type == 4) { xgradient = Double.NaN; ygradient = (src[i + 1][j] - src[i - 1][j]) + (src[i + 1][j + 1] - src[i - 1][j + 1]); ygradient /= 2; ArrayList gradient_list = new ArrayList(); gradient_list.add(xgradient); gradient_list.add(ygradient); dst[i][j] = gradient_list; }
				else if (type == 5) { xgradient = src[i - 1][j + 1] - src[i - 1][j - 1] + src[i][j + 1] - src[i][j - 1] + src[i + 1][j + 1] - src[i + 1][j - 1]; xgradient /= 3; ygradient = src[i + 1][j - 1] - src[i - 1][j - 1] + src[i + 1][j] - src[i - 1][j] + src[i + 1][j + 1] - src[i - 1][j + 1]; ygradient /= 3; ArrayList gradient_list = new ArrayList(); gradient_list.add(xgradient); gradient_list.add(ygradient); dst[i][j] = gradient_list; }
				else if (type == 6) { xgradient = Double.NaN; ygradient = src[i + 1][j - 1] - src[i - 1][j - 1] + src[i + 1][j] - src[i - 1][j]; ygradient /= 2; ArrayList gradient_list = new ArrayList(); gradient_list.add(xgradient); gradient_list.add(ygradient); dst[i][j] = gradient_list; }
				else if (type == 7) { xgradient = Double.NaN; ygradient = Double.NaN; ArrayList gradient_list = new ArrayList(); gradient_list.add(xgradient); gradient_list.add(ygradient); dst[i][j] = gradient_list; }
				else if (type == 8) { xgradient = (src[i - 1][j + 1] - src[i - 1][j - 1]) + (src[i][j + 1] - src[i][j - 1]); xgradient /= 2; ygradient = Double.NaN; ArrayList gradient_list = new ArrayList(); gradient_list.add(xgradient); gradient_list.add(ygradient); dst[i][j] = gradient_list; }
				else if (type == 9) { xgradient = Double.NaN; ygradient = Double.NaN; ArrayList gradient_list = new ArrayList(); gradient_list.add(xgradient); gradient_list.add(ygradient); dst[i][j] = gradient_list; }
			}
		}
		return (dst);
	}

	// This version checks to see if the direction of the
	// gradient is the same across the pixel, and returns
	// Nan if it isn't instead of doing a calculation.
	// This does change the result of getTranslation.
	public static ArrayList[][] getSmoothGradient(int src[][])
	{
		int ydim = src.length;
		int xdim = src[0].length;
		ArrayList[][] dst = new ArrayList[ydim][xdim];

		for (int i = 0; i < ydim; i++)
		{
			for (int j = 0; j < xdim; j++)
			{
				int type = getLocationType(j, i, xdim, ydim);
				double xgradient = 0;
				double ygradient = 0;
				if (type == 1) { xgradient = Double.NaN; ygradient = Double.NaN; ArrayList gradient_list = new ArrayList(); gradient_list.add(xgradient); gradient_list.add(ygradient); dst[i][j] = gradient_list; }
				else if (type == 2) { xgradient = (src[i][j + 1] - src[i][j - 1]) + (src[i + 1][j + 1] - src[i + 1][j - 1]); xgradient /= 2; ygradient = Double.NaN; ArrayList gradient_list = new ArrayList(); gradient_list.add(xgradient); gradient_list.add(ygradient); dst[i][j] = gradient_list; }
				else if (type == 3) { xgradient = Double.NaN; ygradient = Double.NaN; ArrayList gradient_list = new ArrayList(); gradient_list.add(xgradient); gradient_list.add(ygradient); dst[i][j] = gradient_list; }
				else if (type == 4) { xgradient = Double.NaN; ygradient = (src[i + 1][j] - src[i - 1][j]) + (src[i + 1][j + 1] - src[i - 1][j + 1]); ygradient /= 2; ArrayList gradient_list = new ArrayList(); gradient_list.add(xgradient); gradient_list.add(ygradient); dst[i][j] = gradient_list; }
				else if (type == 5)
				{
					if(((src[i][j + 1] < src[i][j]) && (src[i][j] < src[i][j - 1]))  ||
					   ((src[i][j + 1] > src[i][j]) && (src[i][j] > src[i][j - 1])))
					{
					    xgradient = src[i - 1][j + 1] - src[i - 1][j - 1] +
							        src[i][j + 1] - src[i][j - 1] +
							        src[i + 1][j + 1] - src[i + 1][j - 1];
					    xgradient /= 3;
					}
					else
					{
						xgradient = Double.NaN;
					}

					if(((src[i + 1][j] < src[i][j]) && (src[i][j] < src[i - 1][j]))  ||
							   ((src[i + 1][j] > src[i][j]) && (src[i][j] > src[i - 1][j])))
					{
					    ygradient = src[i + 1][j - 1] - src[i - 1][j - 1] +
							    	src[i + 1][j] - src[i - 1][j] +
							    	src[i + 1][j + 1] - src[i - 1][j + 1];
					    ygradient /= 3;
					}
					else
				        ygradient = Double.NaN;

					ArrayList gradient_list = new ArrayList();
					gradient_list.add(xgradient);
					gradient_list.add(ygradient);
					dst[i][j] = gradient_list;
				}
				else if (type == 6) { xgradient = Double.NaN; ygradient = src[i + 1][j - 1] - src[i - 1][j - 1] + src[i + 1][j] - src[i - 1][j]; ygradient /= 2; ArrayList gradient_list = new ArrayList(); gradient_list.add(xgradient); gradient_list.add(ygradient); dst[i][j] = gradient_list; }
				else if (type == 7) { xgradient = Double.NaN; ygradient = Double.NaN; ArrayList gradient_list = new ArrayList(); gradient_list.add(xgradient); gradient_list.add(ygradient); dst[i][j] = gradient_list; }
				else if (type == 8) { xgradient = (src[i - 1][j + 1] - src[i - 1][j - 1]) + (src[i][j + 1] - src[i][j - 1]); xgradient /= 2; ygradient = Double.NaN; ArrayList gradient_list = new ArrayList(); gradient_list.add(xgradient); gradient_list.add(ygradient); dst[i][j] = gradient_list; }
				else if (type == 9) { xgradient = Double.NaN; ygradient = Double.NaN; ArrayList gradient_list = new ArrayList(); gradient_list.add(xgradient); gradient_list.add(ygradient); dst[i][j] = gradient_list; }
			}
		}
		return (dst);
	}

	public static int[][] getVariance(int src[][])
	{
		int ydim = src.length;
		int xdim = src[0].length;
		int[][] dst = new int[ydim][xdim];

		for (int i = 0; i < ydim; i++)
		{
			for (int j = 0; j < xdim; j++)
			{
				int type = getLocationType(j, i, xdim, ydim);
				int variance = 0;
				if (type == 1) { variance += Math.abs(src[i][j] - src[i][j + 1]); variance += Math.abs(src[i][j] - src[i + 1][j]); variance += Math.abs(src[i][j] - src[i + 1][j + 1]); dst[i][j] = variance; }
				else if (type == 2) { variance += Math.abs(src[i][j] - src[i][j - 1]); variance += Math.abs(src[i][j] - src[i][j + 1]); variance += Math.abs(src[i][j] - src[i + 1][j - 1]); variance += Math.abs(src[i][j] - src[i + 1][j]); variance += Math.abs(src[i][j] - src[i + 1][j + 1]); dst[i][j] = variance; }
				else if (type == 3) { variance += Math.abs(src[i][j] - src[i][j - 1]); variance += Math.abs(src[i][j] - src[i + 1][j]); variance += Math.abs(src[i][j] - src[i + 1][j - 1]); dst[i][j] = variance; }
				else if (type == 4) { variance += Math.abs(src[i][j] - src[i - 1][j]); variance += Math.abs(src[i][j] - src[i - 1][j + 1]); variance += Math.abs(src[i][j] - src[i][j + 1]); variance += Math.abs(src[i][j] - src[i + 1][j]); variance += Math.abs(src[i][j] - src[i + 1][j + 1]); dst[i][j] = variance; }
				else if (type == 5) { variance += Math.abs(src[i][j] - src[i - 1][j - 1]); variance += Math.abs(src[i][j] - src[i - 1][j]); variance += Math.abs(src[i][j] - src[i - 1][j + 1]); variance += Math.abs(src[i][j] - src[i][j - 1]); variance += Math.abs(src[i][j] - src[i][j + 1]); variance += Math.abs(src[i][j] - src[i + 1][j - 1]); variance += Math.abs(src[i][j] - src[i + 1][j]); variance += Math.abs(src[i][j] - src[i + 1][j + 1]); dst[i][j] = variance; }
				else if (type == 6) { variance += Math.abs(src[i][j] - src[i - 1][j]); variance += Math.abs(src[i][j] - src[i - 1][j - 1]); variance += Math.abs(src[i][j] - src[i][j - 1]); variance += Math.abs(src[i][j] - src[i + 1][j]); variance += Math.abs(src[i][j] - src[i + 1][j - 1]); dst[i][j] = variance; }
				else if (type == 7) { variance += Math.abs(src[i][j] - src[i - 1][j]); variance += Math.abs(src[i][j] - src[i - 1][j + 1]); variance += Math.abs(src[i][j] - src[i][j + 1]); dst[i][j] = variance; }
				else if (type == 8) { variance += Math.abs(src[i][j] - src[i - 1][j - 1]); variance += Math.abs(src[i][j] - src[i - 1][j]); variance += Math.abs(src[i][j] - src[i - 1][j + 1]); variance += Math.abs(src[i][j] - src[i][j - 1]); variance += Math.abs(src[i][j] - src[i][j + 1]); dst[i][j] = variance; }
				else if (type == 9) { variance += Math.abs(src[i][j] - src[i - 1][j - 1]); variance += Math.abs(src[i][j] - src[i - 1][j]); variance += Math.abs(src[i][j] - src[i][j - 1]); dst[i][j] = variance; }
			}
		}
		return (dst);
	}

	public static int[][] extract(int[][] source, int xoffset, int yoffset, int xdim, int ydim)
	{
	    int src_ydim = source.length;
	    int src_xdim = source[0].length;

	    int [][] dest = new int[ydim][xdim];

	    for(int i = 0; i < ydim; i++)
	    {
	    	for(int j = 0; j < xdim; j++)
	    	{
	    	    dest[i][j] = source[i + yoffset][j + xoffset];
	    	}
	    }
	    return(dest);
	}

	public static int[][] shift(int[][] source, int x, int y)
	{
		int     ydim   = source.length;
		int     xdim   = source[0].length;
		int     xdelta = Math.abs(x);
		int     ydelta = Math.abs(y);
		int     _xdim  = xdim - xdelta;
		int     _ydim  = ydim - ydelta;
		int[][] dest   = new int[_ydim][_xdim];

		int k = 0;
		if(y > 0)
			k = y;
		int m = 0;
		if(x > 0)
			m = x;
		for(int i = 0; i < _ydim; i++)
        {
        	for(int j = 0; j < _xdim; j++)
        	{
        		dest[i][j] = source[i + k][j + m];
        	}
        }
        return dest;
	}

	public static double[][] shift(double[][] source, int x, int y)
	{
		int        ydim   = source.length;
		int        xdim   = source[0].length;
		int        xdelta = Math.abs(x);
		int        ydelta = Math.abs(y);
		int        _xdim  = xdim - xdelta;
		int        _ydim  = ydim - ydelta;
		double[][] dest   = new double[_ydim][_xdim];

		int k = 0;
		if(y > 0)
			k = y;
		int m = 0;
		if(x > 0)
			m = x;
		for(int i = 0; i < _ydim; i++)
        {
        	for(int j = 0; j < _xdim; j++)
        	{
        		dest[i][j] = source[i + k][j + m];
        	}
        }
        return dest;
	}

	// This shrinks the source by one pixel in both dimensions.
	public static int[][] contract(int[][] source)
	{
		int ydim = source.length;
		int xdim = source[0].length;

		int[][] dest = new int[ydim - 1][xdim - 1];

	    for(int i = 0; i < ydim - 1; i++)
		{
			for(int j = 0; j < xdim - 1; j++)
			{
				double w = (double) source[i][j];
				double x = (double) source[i][j + 1];
				double y = (double) source[i + 1][j];
				double z = (double) source[i + 1][j + 1];
				dest[i][j] = (int) ((w + x + y + z) * .25);
			}
		}
		return(dest);
	}

	// x and y should be some number from 1 to -1
	public static int[][] translate(int[][] source, double x, double y)
	{
		int ydim = source.length;
		int xdim = source[0].length;
		int[][] dest = new int[ydim - 1][xdim - 1];

		x += 1.;
		x *= .5;
		y += 1.;
		y *= .5;

		for (int i = 0; i < ydim - 1; i++)
		{
			for (int j = 0; j < xdim - 1; j++)
			{
				double a = (double) source[i][j] * (1. - x) + (double) source[i][j + 1] * x;
				double b = (double) source[i + 1][j] * (1. - x) + (double) source[i + 1][j + 1] * x;
				dest[i][j] = (int) ((a * (1. - y) + b * y) * .5 + .5);
			}
		}
		return(dest);
	}

	// Simple version that only calculates subpixel translations.
	public static double[] getTranslation(int[][] source1, int[][] source2)
	{
		//Assumes source1 and source2 are same size.
		int ydim = source1.length;
		int xdim = source1[0].length;

		int[][] estimate = new int[ydim][xdim];
		for (int i = 0; i < ydim; i++)
			for (int j = 0; j < xdim; j++)
				estimate[i][j] = source2[i][j];

		double[] dest = new double[3];

		double w  = 0;
		double x  = 0;
		double z  = 0;
		double b1 = 0;
		double b2 = 0;

		ArrayList[][] gradient = getGradient(estimate);
		for (int i = 1; i < ydim - 1; i++)
		{
			for (int j = 1; j < xdim - 1; j++)
			{
				ArrayList current_gradient = gradient[i][j];
				double    xgradient        = (double) current_gradient.get(0);
				double    ygradient        = (double) current_gradient.get(1);
				if(!Double.isNaN(xgradient) && !Double.isNaN(ygradient))
				{
				    double xx     = xgradient * xgradient;
				    double xy     = xgradient * ygradient;
				    double yy     = ygradient * ygradient;
				    double delta  = source1[i][j] - estimate[i][j];
				    double xdelta = xgradient * delta;
				    double ydelta = ygradient * delta;

				    w += xx;
				    x += xy;
				    z += yy;
				    b1 += xdelta;
				    b2 += ydelta;
				}
			}
		}
		double xincrement = (b1 - x * b2 / z) / (w - x * x / z);
		double yincrement = (b2 - x * b1 / w) / (z - x * x / w);

		if (xincrement == 0. && yincrement == 0.)
		{
			dest[0] = 0; dest[1] = 0; dest[2] = 0;
			return (dest);
		}

		double xincrement_min = Math.abs(xincrement) / 100.;
		double yincrement_min = Math.abs(yincrement) / 100.;

		double previous_xincrement = xincrement;
		double previous_yincrement = yincrement;
		double xtranslation        = xincrement;
		double ytranslation        = yincrement;

		int [][] current_source = contract(source1);
		estimate       = translate(source2, xtranslation, ytranslation);
		int current_number_of_estimates = 1;
		int maximum_number_of_estimates = 10;

		while (current_number_of_estimates < maximum_number_of_estimates)
		{
			w = 0; x = 0; z = 0; b1 = 0; b2 = 0;
            gradient = getGradient(estimate);
			int _ydim = estimate.length;
			int _xdim = estimate[0].length;

			for (int i = 1; i < _ydim - 1; i++)
			{
				for (int j = 1; j < _xdim - 1; j++)
				{
					ArrayList current_gradient = gradient[i][j];
					double    xgradient = (double) current_gradient.get(0);
					double    ygradient = (double) current_gradient.get(1);
					if(!Double.isNaN(xgradient) && !Double.isNaN(ygradient))
					{
					    double xx = xgradient * xgradient;
					    double xy = xgradient * ygradient;
					    double yy = ygradient * ygradient;
					    double delta = current_source[i][j] - estimate[i][j];
					    double xdelta = xgradient * delta;
					    double ydelta = ygradient * delta;
					    w += xx; x += xy; z += yy; b1 += xdelta; b2 += ydelta;
					}
				}
			}

			xincrement          = (b1 - x * b2 / z) / (w - x * x / z);
			xtranslation       += xincrement;
		    previous_xincrement = xincrement;
			yincrement          = (b2 - x * b1 / w) / (z - x * x / w);
			ytranslation       += yincrement;
			previous_yincrement = yincrement;

			if(Math.abs(xincrement) < xincrement_min || Math.abs(yincrement) < yincrement_min)
			{
				dest[0] = 1; dest[1] = xtranslation; dest[2] = ytranslation;
				return dest;
			}
			else if((xincrement < 0 && previous_xincrement > 0) || (xincrement > 0 && previous_xincrement < 0)
			|| (yincrement < 0 && previous_yincrement > 0) || (yincrement > 0 && previous_yincrement < 0))
			{
				dest[0] = 2; dest[1] = xtranslation; dest[2] = ytranslation;
				return (dest);
			}
			else if(xtranslation >= 1. || ytranslation >= 1.)
		    {
		    	dest[0] = 4; dest[1] = xtranslation; dest[2] = ytranslation;
				return (dest);
		    }
			else
			{
				estimate = translate(source2, xtranslation, ytranslation);
				current_number_of_estimates++;
			}
		}
		dest[0] = 3; dest[1] = xtranslation; dest[2] = ytranslation;
		return dest;
	}

	public static int[][] expandX(int src[][], int expand)
	{
		int ydim = src.length;
		int xdim = src[0].length;
		int _xdim = (xdim - 1) * expand + xdim;
		int [][] dst = new int[ydim][_xdim];
		for(int i = 0; i < ydim; i++)
		{
			int k = 0;
			int end_value = 0;
			for(int j = 0; j < xdim - 1; j++)
			{
				int start_value  = src[i][j];
				end_value        = src[i][j + 1];
				dst[i][k++]      = start_value;
				double delta     = start_value - end_value;
				double increment = delta / (expand + 1);
				for(int m = 0; m < expand; m++)
				{
					start_value += increment;
					dst[i][k++]  = start_value;
				}
			}
			dst[i][k] = end_value;
		}
		return(dst);
	}

	public static double[][] expandX(double src[][], int iterations)
	{
		double[][] current_src = src;
		double [][] result = src;
		for(int i = 0; i < iterations; i++)
		{
			result = expandX(current_src);
		    current_src = result;
		}
		return result;
	}

	public static double[][] expandX(double src[][])
	{
		int ydim = src.length;
		int xdim = src[0].length;
		int _xdim = 2 * xdim - 1;
		double [][] dst = new double[ydim][_xdim];
		double diagonal_weight  = 0.7071;
		for(int i = 0; i < ydim; i++)
		{
			int k = 0;
			for(int j = 0; j < xdim - 1; j++)
			{
				dst[i][k++]  = src[i][j];
				double value = 0;
				double weight = 0;
				if(i == 0)
				{
					dst[i][k++] = (src[i][j] + src[i][j + 1]) / 2;
				}
				else if(i == ydim - 1)
				{
					dst[i][k++] = (src[i][j] + src[i][j + 1]) / 2;
				}
				else
				{
					dst[i][k++] = (src[i][j] + src[i][j + 1]) / 2;
				}
			}
			dst[i][k] = src[i][xdim - 1];
		}
		return(dst);
	}
	public static double[][] shrinkAvg(double src[][])
	{
		int ydim = src.length;
		int xdim = src[0].length;
		int _xdim = xdim / 2;
		int _ydim = ydim / 2;
		double [][] dst = new double[_ydim][_xdim];
		for(int i = 0; i < ydim - 1; i += 2)
		{
			int k = i / 2;
			for(int j = 0; j < xdim - 1; j += 2)
			{
			    int m = j / 2;
			    dst[k][m] = (src[i][j] + src[i][j + 1] + src[i + 1][j] + src[i + 1][j + 1]) / 4.;
			}
		}
		return(dst);
	}
}
