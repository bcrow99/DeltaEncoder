import java.util.ArrayList;

/**
 * Image operators on single channels: the averaging pyramid used by
 * PixelMapper, and older utilities for smoothing, dilation, area
 * resampling, gradients and registration.
 * <p>
 * The averaging pyramid halves an image by 2 x 2 rounded averages
 * ({@link #shrinkAvg(int[], int)}) and expands it back by fitting a plane
 * to each block from its neighbours ({@link #expandGradient(int[], int, int)},
 * or {@link #expandGradientSaddle(int[], int, int)} with a cross term),
 * then corrects the expansion with one sign bit per pixel: whether the
 * pixel was &gt;= its block's average ({@link #buildGeqBits(int[], int[], int)},
 * {@link #refineWithSignBits(int[], int[], boolean[], int, int)}). Images
 * are padded to a multiple of 2^levels first ({@link #padTo},
 * {@link #padEdgeReplicate(int[], int, int, int, int)}) and cropped after.
 * The flat-array versions (an int[] plus a width) are the ones in use. The
 * int[][] versions came from a stand-alone demo; they clamp to 255 and
 * refine from the prediction as it is, so they differ slightly.
 * <p>
 * In source order: the pyramid on flat arrays; the pyramid on int[][];
 * smoothing and dilation; area-average resampling; gradients and
 * variance; registration (sub-pixel translation); and widening and
 * shrinking double arrays.
 * <p>
 * Version 3.0.
 */
public class ImageMapper
{
	/** Not used: all of this class's methods are static. */
	public ImageMapper() {}

    // ======================================================================
    // Averaging pyramid, flat arrays (used by PixelMapper)
    // ======================================================================

    /**
     * Halves a flat image: each 2 x 2 square becomes its rounded average,
     * (sum + 2) / 4. An odd last row or column is dropped (pad first with
     * {@link #padEdgeReplicate(int[], int, int, int, int)}).
     *
     * @param src  the image, row by row
     * @param xdim its width
     * @return the (xdim / 2) x (ydim / 2) image of averages
     */
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

    /**
     * Doubles a flat image of block averages by fitting a plane to each block:
     * the gradients come from the neighbouring blocks (central differences,
     * one-sided at the edges), and the four pixels are A -+ gy/2 -+ gx/2.
     * Instead of clamping each pixel, the four offsets are scaled down by the
     * same factor until all fit in [0, maxValue], so the block mean is kept and
     * the corners fade toward the mean rather than hitting a wall.
     *
     * @param avg      the block averages, row by row
     * @param xdim     avg's width (the smaller one)
     * @param maxValue the largest legal value: 255 for a colour channel, up to
     *                 510 for a shifted difference channel
     * @return the 2 xdim x 2 ydim prediction
     */
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

    /**
     * {@link #expandGradient(int[], int, int)} plus the cross term: the mixed
     * derivative gxy, from the four diagonal neighbouring blocks, adds +-gxy/4
     * to the corners, so a block at a saddle (neighbours low/high/high/low)
     * can be represented. The four corrections cancel, so the mean is kept.
     *
     * @param avg      the block averages, row by row
     * @param xdim     avg's width (the smaller one)
     * @param maxValue the largest legal value
     * @return the 2 xdim x 2 ydim prediction
     */
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

    /**
     * {@link #horizGrad} on a flat array.
     *
     * @param avg  the block averages
     * @param xdim their width
     * @param k    the block row
     * @param m    the block column
     * @return the gradient
     */
    private static double horizGradFlat(int[] avg, int xdim, int k, int m) {
        int m0 = Math.max(m - 1, 0);
        int m1 = Math.min(m + 1, xdim - 1);
        if (m0 == m1) return 0.0;
        return (avg[k * xdim + m1] - avg[k * xdim + m0]) / (2.0 * (m1 - m0));
    }

    /**
     * {@link #vertGrad} on a flat array.
     *
     * @param avg  the block averages
     * @param xdim their width
     * @param ydim their height
     * @param k    the block row
     * @param m    the block column
     * @return the gradient
     */
    private static double vertGradFlat(int[] avg, int xdim, int ydim, int k, int m) {
        int k0 = Math.max(k - 1, 0);
        int k1 = Math.min(k + 1, ydim - 1);
        if (k0 == k1) return 0.0;
        return (avg[k1 * xdim + m] - avg[k0 * xdim + m]) / (2.0 * (k1 - k0));
    }

    /**
     * {@link #crossGrad} on a flat array.
     *
     * @param avg  the block averages
     * @param xdim their width
     * @param ydim their height
     * @param k    the block row
     * @param m    the block column
     * @return the cross derivative
     */
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

    /**
     * The sign bits for {@link #refineWithSignBits(int[], int[], boolean[], int, int)}:
     * whether each pixel is &gt;= the average of its 2 x 2 square.
     *
     * @param orig     the image before shrinking
     * @param avg      its {@link #shrinkAvg(int[], int)}
     * @param origXdim orig's width (the larger one)
     * @return one bit per pixel of orig
     */
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

    /**
     * Corrects a predicted level with its sign bits. Per 2 x 2 block: if the
     * prediction disagrees with any bit, the block restarts flat (all four at
     * the average A), since the plane fit is evidently wrong there; then ten
     * rounds of alternating projection (POCS) move each pixel to the side of
     * A its bit says and restore the exact mean 4A. The deviations from A are
     * finally damped by one common factor to fit in [0, maxValue], which keeps
     * both the mean and every pixel's side of A.
     *
     * @param avg           the block averages
     * @param predicted     the prediction from {@link #expandGradient(int[], int, int)}
     *                      or {@link #expandGradientSaddle(int[], int, int)}
     * @param geq           the sign bits from {@link #buildGeqBits(int[], int[], int)}
     * @param predictedXdim predicted's width (the larger one)
     * @param maxValue      the largest legal value
     * @return the refined level
     */
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

    /**
     * Clamps to [0, maxValue]: the flat-array methods may be given a shifted
     * difference channel, whose values run to 510.
     *
     * @param v        the value
     * @param maxValue the largest legal value
     * @return the clamped value
     */
    private static int clamp(long v, int maxValue) {
        if (v < 0) return 0;
        if (v > maxValue) return maxValue;
        return (int) v;
    }

    /**
     * The largest s in [0, 1] such that center + s * offset is in
     * [0, maxValue] for every offset. Scaling all the offsets by one factor
     * keeps their sum, and so the block mean, where clamping each corner on
     * its own would not.
     *
     * @param center     the block average
     * @param rawOffsets the corners' offsets from it
     * @param maxValue   the largest legal value
     * @return the factor
     */
    private static double computeDampingFactor(double center, double[] rawOffsets, int maxValue) {
        double s = 1.0;
        for (double off : rawOffsets) {
            if (off > 0) s = Math.min(s, (maxValue - center) / off);
            else if (off < 0) s = Math.min(s, center / (-off));
        }
        return Math.max(0.0, s);
    }

    /**
     * Rounds a dimension up to a multiple.
     *
     * @param dim      the dimension
     * @param multiple the multiple
     * @return the smallest multiple of multiple that is &gt;= dim
     */
    public static int padTo(int dim, int multiple) {
        int rem = dim % multiple;
        return rem == 0 ? dim : dim + (multiple - rem);
    }

    /**
     * Pads a flat image at the bottom and right by repeating its last row and
     * column. (Shrinking only leaves rows and columns over at those edges, and
     * repeating keeps the gradient there near zero, where zero padding would
     * add a false dark edge.)
     *
     * @param src     the image
     * @param xdim    its width
     * @param ydim    its height
     * @param newXdim the padded width
     * @param newYdim the padded height
     * @return the padded image, or src itself if the size is unchanged
     */
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

    /**
     * The top-left newXdim x newYdim corner of a flat image (undoes the padding).
     *
     * @param src     the image
     * @param xdim    its width
     * @param ydim    its height
     * @param newXdim the cropped width
     * @param newYdim the cropped height
     * @return the cropped image
     */
    public static int[] crop(int[] src, int xdim, int ydim, int newXdim, int newYdim) {
        int[] dst = new int[newXdim * newYdim];
        for (int y = 0; y < newYdim; y++) {
            System.arraycopy(src, y * xdim, dst, y * newXdim, newXdim);
        }
        return dst;
    }

    // ======================================================================
    // Averaging pyramid, int[][] (the original demo's versions)
    // ======================================================================

    /**
     * Halves an image: each 2 x 2 square becomes its rounded average,
     * (sum + 2) / 4. An odd last row or column is dropped.
     *
     * @param src the image
     * @return the averages
     */
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

    /**
     * Doubles an image of block averages by fitting a plane to each block, as
     * {@link #expandGradient(int[], int, int)}, but clamping each pixel to
     * [0, 255] on its own (so the block mean may shift at the extremes).
     *
     * @param avg the block averages
     * @return the prediction, twice the size
     */
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

    /**
     * {@link #expandGradient(int[][])} plus the cross term gxy (see
     * {@link #expandGradientSaddle(int[], int, int)}). Uses nothing beyond
     * avg itself: no extra bits, just a higher-order read of the same
     * neighbours.
     *
     * @param avg the block averages
     * @return the prediction, twice the size
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

    /**
     * Horizontal gradient at block (k, m): a central difference, one-sided
     * at the edges, 0 for a one-block-wide image.
     *
     * @param avg the block averages
     * @param k   the block row
     * @param m   the block column
     * @return the gradient per pixel of the expanded image
     */
    private static double horizGrad(int[][] avg, int k, int m) {
        int _xdim = avg[0].length;
        int m0 = Math.max(m - 1, 0);
        int m1 = Math.min(m + 1, _xdim - 1);
        if (m0 == m1) return 0.0;
        return (avg[k][m1] - avg[k][m0]) / (2.0 * (m1 - m0));
    }

    /**
     * Vertical gradient at block (k, m), as {@link #horizGrad}.
     *
     * @param avg the block averages
     * @param k   the block row
     * @param m   the block column
     * @return the gradient per pixel of the expanded image
     */
    private static double vertGrad(int[][] avg, int k, int m) {
        int _ydim = avg.length;
        int k0 = Math.max(k - 1, 0);
        int k1 = Math.min(k + 1, _ydim - 1);
        if (k0 == k1) return 0.0;
        return (avg[k1][m] - avg[k0][m]) / (2.0 * (k1 - k0));
    }

    /**
     * Mixed second difference at block (k, m) from its four diagonal
     * neighbours, with the same clamp-to-edge handling as {@link #horizGrad}.
     *
     * @param avg the block averages
     * @param k   the block row
     * @param m   the block column
     * @return the cross derivative
     */
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
     * The sign bits: whether each pixel is &gt;= the average of its 2 x 2 square.
     *
     * @param orig the image before shrinking
     * @param avg  its {@link #shrinkAvg(int[][])}
     * @return one bit per pixel of orig
     */
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

    /**
     * Corrects a predicted level with its sign bits by ten rounds of
     * alternating projection: each pixel goes to the side of the block average
     * its bit says, then the four are shifted to restore the exact mean. Unlike
     * {@link #refineWithSignBits(int[], int[], boolean[], int, int)} it starts
     * from the prediction even where it disagrees with the bits, and clamps
     * each pixel to [0, 255].
     *
     * @param avg       the block averages
     * @param predicted the prediction
     * @param geq       the sign bits
     * @return the refined level
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

    /**
     * Clamps to [0, 255].
     *
     * @param v the value
     * @return the clamped value
     */
    private static int clamp(long v) {
        if (v < 0) return 0;
        if (v > 255) return 255;
        return (int) v;
    }

    /**
     * Pads an image at the bottom and right to multiples of multiple by
     * repeating its last row and column.
     *
     * @param src      the image
     * @param multiple the multiple
     * @return the padded image, or src itself if no padding is needed
     */
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

    /**
     * The top-left h x w corner of an image (undoes the padding).
     *
     * @param src the image
     * @param h   the cropped height
     * @param w   the cropped width
     * @return the cropped image
     */
    public static int[][] crop(int[][] src, int h, int w) {
        int[][] dst = new int[h][w];
        for (int y = 0; y < h; y++) {
            System.arraycopy(src[y], 0, dst[y], 0, w);
        }
        return dst;
    }

    /**
     * Error of a reconstruction against the original.
     *
     * @param orig  the original channel
     * @param recon the reconstruction
     * @return {mean signed error, mean absolute error, pixel count}
     */
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

	// ======================================================================
	// Smoothing and dilation
	// ======================================================================

	/**
	 * Edge-preserving smoothing: each pass replaces a pixel with the weighted
	 * mean of its 3 x 3 neighbourhood, a neighbour's weight being
	 * exp(-|gradient|^2 / (2 smooth_factor^2)), so pixels on strong edges
	 * count for little. The two outermost rows and columns are left as they
	 * are.
	 *
	 * @param src                  the image, row by row
	 * @param xdim                 its width
	 * @param ydim                 its height
	 * @param smooth_factor        the gradient scale; larger smooths across stronger edges
	 * @param number_of_iterations the number of passes
	 * @param dst                  receives the result, truncated to int
	 */
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

	/**
	 * Where a pixel lies, as a type 1-9 laid out like a keypad from the top
	 * left: 1 2 3 on the top row, 4 5 6 in the middle, 7 8 9 on the bottom
	 * row; 1, 4 and 7 are the left column, 3, 6 and 9 the right. 5 is the
	 * interior.
	 *
	 * @param xindex the column
	 * @param yindex the row
	 * @param xdim   the width
	 * @param ydim   the height
	 * @return the location type, 1-9
	 */
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

	/**
	 * Fills the uninterpolated cells of an image by repeated dilation:
	 * vertical passes until they stop making progress, then diagonal passes,
	 * then full 8-neighbour passes. Assumes the samples are denser in y than
	 * in x. Prints its progress to standard output.
	 *
	 * @param src            the image; only the interpolated cells are read
	 * @param isInterpolated which cells hold data
	 * @return the filled image
	 */
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

	/**
	 * One dilation pass over the 8-neighbourhood. Each uninterpolated cell
	 * with more than neighbor_threshold interpolated neighbours becomes their
	 * weighted mean (orthogonal neighbours weigh 1, diagonal ones 0.7071); the
	 * others are set to 0. Call repeatedly
	 * until every cell is interpolated; it completes even from one cell.
	 *
	 * @param src                the image, row by row
	 * @param isInterpolated     which cells hold data; updated in place
	 * @param xdim               the width
	 * @param ydim               the height
	 * @param neighbor_threshold a cell needs more than this many interpolated neighbours
	 * @param dst                receives the result
	 */
	public static void dilateImage(double src[], boolean isInterpolated[], int xdim, int ydim, int neighbor_threshold, double dst[])
	{
		dilate(src, isInterpolated, xdim, ydim, neighbor_threshold, dst, ORTHOGONAL, DIAGONAL, 0.7071);
	}

	/**
	 * {@link #dilateImage} using only the cells above and below.
	 *
	 * @param src                the image, row by row
	 * @param isInterpolated     which cells hold data; updated in place
	 * @param xdim               the width
	 * @param ydim               the height
	 * @param neighbor_threshold a cell needs more than this many interpolated neighbours
	 * @param dst                receives the result
	 */
	public static void dilateImageVertical(double src[], boolean isInterpolated[], int xdim, int ydim, int neighbor_threshold, double dst[])
	{
		dilate(src, isInterpolated, xdim, ydim, neighbor_threshold, dst, VERTICAL, NONE, 1.);
	}

	/**
	 * {@link #dilateImage} using only the four diagonal cells, weighing 1.
	 *
	 * @param src                the image, row by row
	 * @param isInterpolated     which cells hold data; updated in place
	 * @param xdim               the width
	 * @param ydim               the height
	 * @param neighbor_threshold a cell needs more than this many interpolated neighbours
	 * @param dst                receives the result
	 */
	public static void dilateImageDiagonal(double src[], boolean isInterpolated[], int xdim, int ydim, int neighbor_threshold, double dst[])
	{
		dilate(src, isInterpolated, xdim, ydim, neighbor_threshold, dst, NONE, DIAGONAL, 1.);
	}

	/**
	 * The orthogonal neighbours {dy, dx} {@link #dilateImage} reads, indexed by
	 * location type (see {@link #getLocationType}), in the order they are
	 * summed. The order fixes the floating-point sums, so it is the order of
	 * the original per-type code.
	 */
	private static final int[][][] ORTHOGONAL = {
		{},
		{{0, 1}, {1, 0}},
		{{0, -1}, {0, 1}, {1, 0}},
		{{0, -1}, {1, 0}},
		{{-1, 0}, {1, 0}, {0, 1}},
		{{-1, 0}, {1, 0}, {0, -1}, {0, 1}},
		{{-1, 0}, {1, 0}, {0, -1}},
		{{-1, 0}, {0, 1}},
		{{-1, 0}, {0, -1}, {0, 1}},
		{{-1, 0}, {0, -1}}
	};

	/**
	 * The neighbours {@link #dilateImageVertical} reads, as {@link #ORTHOGONAL}.
	 */
	private static final int[][][] VERTICAL = {
		{},
		{{1, 0}}, {{1, 0}}, {{1, 0}},
		{{-1, 0}, {1, 0}}, {{-1, 0}, {1, 0}}, {{-1, 0}, {1, 0}},
		{{-1, 0}}, {{-1, 0}}, {{-1, 0}}
	};

	/**
	 * The diagonal neighbours {@link #dilateImage} and
	 * {@link #dilateImageDiagonal} read, as {@link #ORTHOGONAL}.
	 */
	private static final int[][][] DIAGONAL = {
		{},
		{{1, 1}},
		{{1, -1}, {1, 1}},
		{{1, -1}},
		{{-1, 1}, {1, 1}},
		{{-1, -1}, {1, -1}, {-1, 1}, {1, 1}},
		{{-1, -1}, {1, -1}},
		{{-1, 1}},
		{{-1, -1}, {-1, 1}},
		{{-1, -1}}
	};

	/**
	 * No neighbours, for every location type.
	 */
	private static final int[][][] NONE = new int[10][0][];

	/**
	 * One dilation pass reading, per location type, the given orthogonal
	 * neighbours (weight 1) and diagonal ones (weight diagonal_weight). See
	 * {@link #dilateImage}.
	 *
	 * @param src                the image
	 * @param isInterpolated     which cells hold data; updated in place
	 * @param xdim               the width
	 * @param ydim               the height
	 * @param neighbor_threshold a cell needs more than this many interpolated neighbours
	 * @param dst                receives the result
	 * @param orthogonal         per location type, the {dy, dx} orthogonal neighbours
	 * @param diagonal           per location type, the {dy, dx} diagonal neighbours
	 * @param diagonal_weight    the diagonal neighbours' weight
	 */
	private static void dilate(double src[], boolean isInterpolated[], int xdim, int ydim, int neighbor_threshold, double dst[],
	                           int[][][] orthogonal, int[][][] diagonal, double diagonal_weight)
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
					continue;
				}
				double total_weight     = 0;
				double value            = 0.;
				int number_of_neighbors = 0;
				int location_type       = getLocationType(j, i, xdim, ydim);
				for (int[] d : orthogonal[location_type])
				{
					int n = k + d[0] * xdim + d[1];
					if (isInterpolated[n])
					{
						number_of_neighbors++;
						total_weight += 1.;
						value        += src[n];
					}
				}
				for (int[] d : diagonal[location_type])
				{
					int n = k + d[0] * xdim + d[1];
					if (isInterpolated[n])
					{
						number_of_neighbors++;
						total_weight += diagonal_weight;
						value        += diagonal_weight * src[n];
					}
				}
				if (number_of_neighbors > neighbor_threshold)
				{
					value             /= total_weight;
					dst[k]             = value;
					wasInterpolated[k] = true;
				}
				else
				{
					dst[k]             = 0;
					wasInterpolated[k] = false;
				}
			}
		}
		for (int i = 0; i < xdim * ydim; i++)
			isInterpolated[i] = wasInterpolated[i];
	}

	// ======================================================================
	// Area-average resampling
	// ======================================================================

	/**
	 * Resamples a flat image to a new width by area averaging: each output
	 * pixel is the mean of the input pixels it covers, weighted by how much of
	 * each it covers. Enlarging repeats or blends neighbouring pixels.
	 *
	 * @param source   the image, row by row
	 * @param xdim     its width
	 * @param ydim     its height
	 * @param new_xdim the new width
	 * @return the new_xdim x ydim image
	 */
	public static int[] avgAreaXTransform(int source[], int xdim, int ydim, int new_xdim)
	{
		int[][] table = avgAreaTable(xdim, new_xdim);
		int[]   dest  = new int[ydim * new_xdim];
		for (int y = 0; y < ydim; y++)
			avgAreaLine(source, y * xdim, 1, dest, y * new_xdim, 1, xdim, new_xdim, table);
		return(dest);
	}

	/**
	 * Resamples a flat image to a new height by area averaging (see
	 * {@link #avgAreaXTransform(int[], int, int, int)}).
	 *
	 * @param src      the image, row by row
	 * @param xdim     its width
	 * @param ydim     its height
	 * @param new_ydim the new height
	 * @return the xdim x new_ydim image
	 */
	public static int [] avgAreaYTransform(int src[], int xdim, int ydim, int new_ydim)
	{
		int[][] table = avgAreaTable(ydim, new_ydim);
		int []  dst   = new int[xdim * new_ydim];
		for (int x = 0; x < xdim; x++)
			avgAreaLine(src, x, xdim, dst, x, xdim, ydim, new_ydim, table);
		return(dst);
	}

	/**
	 * Resamples a flat image to a new size by area averaging, x then y.
	 *
	 * @param src      the image, row by row
	 * @param xdim     its width
	 * @param ydim     its height
	 * @param new_xdim the new width
	 * @param new_ydim the new height
	 * @return the new_xdim x new_ydim image
	 */
	public static int [] avgAreaTransform(int src[], int xdim, int ydim, int new_xdim, int new_ydim)
	{
		int [] intermediate = avgAreaXTransform(src, xdim, ydim, new_xdim);
		int [] dst          = avgAreaYTransform(intermediate, new_xdim, ydim, new_ydim);
		return(dst);
	}

	/**
	 * Where each output sample falls when resampling dim samples to new_dim:
	 * sample x covers input positions [x dim / new_dim, (x + 1) dim / new_dim).
	 *
	 * @param dim     the input length
	 * @param new_dim the output length
	 * @return {start_fraction, end_fraction, number_of_pixels}, the fractions of
	 *         the first and last input samples covered being in thousandths
	 */
	private static int[][] avgAreaTable(int dim, int new_dim)
	{
		double differential         = (double) dim / (double) new_dim;
		double real_position        = 0.;
		int    current_whole_number = 0;

		int [] start_fraction   = new int[new_dim];
		int [] end_fraction     = new int[new_dim];
		int [] number_of_pixels = new int[new_dim];
		for (int i = 0; i < new_dim; i++)
		{
			double previous_position     = real_position;
			int    previous_whole_number = current_whole_number;

			real_position       += differential;
			current_whole_number = (int) (real_position);
			number_of_pixels[i]  = current_whole_number - previous_whole_number;
			start_fraction[i]    = (int) (1000. * (1. - (previous_position - (double) (previous_whole_number))));
			end_fraction[i]      = (int) (1000. * (real_position - (double) (current_whole_number)));
		}
		return new int[][] {start_fraction, end_fraction, number_of_pixels};
	}

	/**
	 * Resamples one row (strides 1) or column (strides xdim). The sums are
	 * long: in int they overflowed once 255 dim^2 / new_dim passed about 2.1
	 * million (a 2000-pixel row shrunk by 4).
	 *
	 * @param src        the input
	 * @param j          the row's or column's first input index
	 * @param stride_in  the input stride
	 * @param dst        the output
	 * @param i          the first output index
	 * @param stride_out the output stride
	 * @param dim        the input length
	 * @param new_dim    the output length
	 * @param table      from {@link #avgAreaTable}
	 */
	private static void avgAreaLine(int[] src, int j, int stride_in, int[] dst, int i, int stride_out, int dim, int new_dim, int[][] table)
	{
		int[] start_fraction   = table[0];
		int[] end_fraction     = table[1];
		int[] number_of_pixels = table[2];
		double differential    = (double) dim / (double) new_dim;
		long   weight          = (long) (int) (differential * dim) * 1000;
		long   factor          = (long) dim * 1000;

		for (int x = 0; x < new_dim; x++)
		{
			if (number_of_pixels[x] == 0)
				dst[i] = src[j];
			else
			{
				long total = (long) start_fraction[x] * dim * src[j];
				j += stride_in;
				for (int k = number_of_pixels[x] - 1; k > 0; k--)
				{
					total += factor * src[j];
					j += stride_in;
				}
				if (x < new_dim - 1)
				{
					total += (long) end_fraction[x] * dim * src[j];
					total /= weight;
				}
				else
					total /= weight - (long) end_fraction[x] * dim;
				dst[i] = (int) total;
			}
			i += stride_out;
		}
	}

	/**
	 * {@link #avgAreaXTransform(int[], int, int, int)} for an int[][] image.
	 *
	 * @param src      the image
	 * @param new_xdim the new width
	 * @return the resampled image
	 */
	public static int[][] avgAreaXTransform(int src[][], int new_xdim)
	{
		int ydim = src.length, xdim = src[0].length;
		return unflatten(avgAreaXTransform(flatten(src), xdim, ydim, new_xdim), new_xdim, ydim);
	}

	/**
	 * {@link #avgAreaYTransform(int[], int, int, int)} for an int[][] image.
	 *
	 * @param src      the image
	 * @param new_ydim the new height
	 * @return the resampled image
	 */
	public static int[][] avgAreaYTransform(int src[][], int new_ydim)
	{
		int ydim = src.length, xdim = src[0].length;
		return unflatten(avgAreaYTransform(flatten(src), xdim, ydim, new_ydim), xdim, new_ydim);
	}

	/**
	 * {@link #avgAreaTransform(int[], int, int, int, int)} for an int[][] image.
	 *
	 * @param src      the image
	 * @param new_xdim the new width
	 * @param new_ydim the new height
	 * @return the resampled image
	 */
	public static int [][] avgAreaTransform(int src[][], int new_xdim, int new_ydim)
	{
		int ydim = src.length, xdim = src[0].length;
		return unflatten(avgAreaTransform(flatten(src), xdim, ydim, new_xdim, new_ydim), new_xdim, new_ydim);
	}

	/**
	 * Copies an int[][] image into a flat array, row by row.
	 *
	 * @param src the image
	 * @return the flat image
	 */
	private static int[] flatten(int[][] src)
	{
		int ydim = src.length, xdim = src[0].length;
		int[] dst = new int[xdim * ydim];
		for (int i = 0; i < ydim; i++)
			System.arraycopy(src[i], 0, dst, i * xdim, xdim);
		return dst;
	}

	/**
	 * Copies a flat image into an int[][].
	 *
	 * @param src  the flat image
	 * @param xdim its width
	 * @param ydim its height
	 * @return the image
	 */
	private static int[][] unflatten(int[] src, int xdim, int ydim)
	{
		int[][] dst = new int[ydim][xdim];
		for (int i = 0; i < ydim; i++)
			System.arraycopy(src, i * xdim, dst[i], 0, xdim);
		return dst;
	}

	// ======================================================================
	// Gradients and variance
	// ======================================================================

	/**
	 * {@link #getGradient(double[][])} of an int image.
	 *
	 * @param src the image
	 * @return per pixel, a list {xgradient, ygradient} of Doubles
	 */
	public static ArrayList[][] getGradient(int src[][])
	{
		int ydim = src.length;
		int xdim = src[0].length;
		double[][] s = new double[ydim][xdim];
		for (int i = 0; i < ydim; i++)
			for (int j = 0; j < xdim; j++)
				s[i][j] = src[i][j];
		return getGradient(s);
	}

	/**
	 * The gradient at each pixel: the mean of the central differences over
	 * the three rows (for x) or columns (for y) around it, or the two that
	 * exist at an edge. A gradient across an edge is NaN: the top and bottom
	 * rows have only x, the left and right columns only y, and the corners
	 * neither.
	 *
	 * @param src the image
	 * @return per pixel, a list {xgradient, ygradient} of Doubles
	 */
	public static ArrayList[][] getGradient(double src[][])
	{
		int ydim = src.length;
		int xdim = src[0].length;
		ArrayList[][] dst = new ArrayList[ydim][xdim];

		for (int i = 0; i < ydim; i++)
		{
			for (int j = 0; j < xdim; j++)
			{
				int    type      = getLocationType(j, i, xdim, ydim);
				double xgradient = Double.NaN;
				double ygradient = Double.NaN;
				if (type == 2)
					xgradient = ((src[i][j + 1] - src[i][j - 1]) + (src[i + 1][j + 1] - src[i + 1][j - 1])) / 2;
				else if (type == 4)
					ygradient = ((src[i + 1][j] - src[i - 1][j]) + (src[i + 1][j + 1] - src[i - 1][j + 1])) / 2;
				else if (type == 5)
				{
					xgradient = (src[i - 1][j + 1] - src[i - 1][j - 1] + src[i][j + 1] - src[i][j - 1]
					             + src[i + 1][j + 1] - src[i + 1][j - 1]) / 3;
					ygradient = (src[i + 1][j - 1] - src[i - 1][j - 1] + src[i + 1][j] - src[i - 1][j]
					             + src[i + 1][j + 1] - src[i - 1][j + 1]) / 3;
				}
				else if (type == 6)
					ygradient = (src[i + 1][j - 1] - src[i - 1][j - 1] + src[i + 1][j] - src[i - 1][j]) / 2;
				else if (type == 8)
					xgradient = ((src[i - 1][j + 1] - src[i - 1][j - 1]) + (src[i][j + 1] - src[i][j - 1])) / 2;
				dst[i][j] = gradientPair(xgradient, ygradient);
			}
		}
		return (dst);
	}

	/**
	 * {@link #getGradient(int[][])}, except that an interior pixel's gradient
	 * along an axis is NaN unless the pixel lies strictly between its two
	 * neighbours on that axis. (This changes the results of getTranslation if
	 * used there.)
	 *
	 * @param src the image
	 * @return per pixel, a list {xgradient, ygradient} of Doubles
	 */
	public static ArrayList[][] getSmoothGradient(int src[][])
	{
		int ydim = src.length;
		int xdim = src[0].length;
		ArrayList[][] dst = getGradient(src);
		for (int i = 1; i < ydim - 1; i++)
		{
			for (int j = 1; j < xdim - 1; j++)
			{
				boolean xmonotone = (src[i][j + 1] < src[i][j] && src[i][j] < src[i][j - 1])
				                 || (src[i][j + 1] > src[i][j] && src[i][j] > src[i][j - 1]);
				boolean ymonotone = (src[i + 1][j] < src[i][j] && src[i][j] < src[i - 1][j])
				                 || (src[i + 1][j] > src[i][j] && src[i][j] > src[i - 1][j]);
				if (!xmonotone || !ymonotone)
				{
					ArrayList g = dst[i][j];
					dst[i][j] = gradientPair(xmonotone ? (double) g.get(0) : Double.NaN, ymonotone ? (double) g.get(1) : Double.NaN);
				}
			}
		}
		return (dst);
	}

	/**
	 * A gradient as getGradient returns it.
	 *
	 * @param xgradient the x gradient
	 * @param ygradient the y gradient
	 * @return the list {xgradient, ygradient}
	 */
	private static ArrayList gradientPair(double xgradient, double ygradient)
	{
		ArrayList gradient_list = new ArrayList();
		gradient_list.add(xgradient);
		gradient_list.add(ygradient);
		return gradient_list;
	}

	/**
	 * The sum of absolute differences between each pixel and its neighbours
	 * (the 3 to 8 that exist).
	 *
	 * @param src the image
	 * @return the sums
	 */
	public static int[][] getVariance(int src[][])
	{
		int ydim = src.length;
		int xdim = src[0].length;
		int[][] dst = new int[ydim][xdim];

		for (int i = 0; i < ydim; i++)
		{
			for (int j = 0; j < xdim; j++)
			{
				int variance = 0;
				for (int y = Math.max(i - 1, 0); y <= Math.min(i + 1, ydim - 1); y++)
					for (int x = Math.max(j - 1, 0); x <= Math.min(j + 1, xdim - 1); x++)
						variance += Math.abs(src[i][j] - src[y][x]);
				dst[i][j] = variance;
			}
		}
		return (dst);
	}

	// ======================================================================
	// Registration
	// ======================================================================

	/**
	 * A window of an image.
	 *
	 * @param source  the image
	 * @param xoffset the window's left column
	 * @param yoffset the window's top row
	 * @param xdim    its width
	 * @param ydim    its height
	 * @return the window
	 */
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

	/**
	 * Crops |x| columns and |y| rows: from the left or top when positive, from
	 * the right or bottom when negative. Two images cropped by opposite shifts
	 * line up at that offset.
	 *
	 * @param source the image
	 * @param x      the columns to drop
	 * @param y      the rows to drop
	 * @return the cropped image
	 */
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

	/**
	 * {@link #shift(int[][], int, int)} for a double image.
	 *
	 * @param source the image
	 * @param x      the columns to drop
	 * @param y      the rows to drop
	 * @return the cropped image
	 */
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

	/**
	 * The (truncated) average of each 2 x 2 neighbourhood: the image shrinks
	 * by one pixel each way, and its samples move half a pixel.
	 *
	 * @param source the image
	 * @return the averages
	 */
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

	/**
	 * Shifts an image by a fraction of a pixel by bilinear interpolation. The
	 * result is one pixel smaller each way; x = y = 0 gives the rounded
	 * {@link #contract} (a half-pixel shift), and -1 or 1 a shift of half a
	 * pixel more or less.
	 *
	 * @param source the image
	 * @param x      the x shift, from -1 to 1
	 * @param y      the y shift, from -1 to 1
	 * @return the shifted image
	 */
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
				dest[i][j] = (int) (a * (1. - y) + b * y + .5);
			}
		}
		return(dest);
	}

	/**
	 * Estimates the sub-pixel translation of source2 against source1 by
	 * iterated least squares on the gradients (at most ten estimates). The
	 * images must be the same size.
	 *
	 * @param source1 the reference image
	 * @param source2 the shifted image
	 * @return {status, xtranslation, ytranslation}; status is 0 for no motion,
	 *         1 converged, 2 oscillating (an increment changed sign), 3 out of
	 *         iterations, 4 the translation reached a whole pixel
	 */
	public static double[] getTranslation(int[][] source1, int[][] source2)
	{
		double[] s = normalEquations(source1, source2);
		double w = s[0], x = s[1], z = s[2], b1 = s[3], b2 = s[4];
		double xincrement = (b1 - x * b2 / z) / (w - x * x / z);
		double yincrement = (b2 - x * b1 / w) / (z - x * x / w);

		if (xincrement == 0. && yincrement == 0.)
			return new double[] {0, 0, 0};

		double xincrement_min = Math.abs(xincrement) / 100.;
		double yincrement_min = Math.abs(yincrement) / 100.;
		double xtranslation   = xincrement;
		double ytranslation   = yincrement;

		int [][] current_source = contract(source1);
		int [][] estimate       = translate(source2, xtranslation, ytranslation);
		int maximum_number_of_estimates = 10;

		for (int current_number_of_estimates = 1; current_number_of_estimates < maximum_number_of_estimates; current_number_of_estimates++)
		{
			s  = normalEquations(current_source, estimate);
			w  = s[0]; x = s[1]; z = s[2]; b1 = s[3]; b2 = s[4];
			double previous_xincrement = xincrement;
			double previous_yincrement = yincrement;
			xincrement    = (b1 - x * b2 / z) / (w - x * x / z);
			yincrement    = (b2 - x * b1 / w) / (z - x * x / w);
			xtranslation += xincrement;
			ytranslation += yincrement;

			if (Math.abs(xincrement) < xincrement_min || Math.abs(yincrement) < yincrement_min)
				return new double[] {1, xtranslation, ytranslation};
			else if ((xincrement < 0 && previous_xincrement > 0) || (xincrement > 0 && previous_xincrement < 0)
			      || (yincrement < 0 && previous_yincrement > 0) || (yincrement > 0 && previous_yincrement < 0))
				return new double[] {2, xtranslation, ytranslation};
			else if (Math.abs(xtranslation) >= 1. || Math.abs(ytranslation) >= 1.)
				return new double[] {4, xtranslation, ytranslation};
			estimate = translate(source2, xtranslation, ytranslation);
		}
		return new double[] {3, xtranslation, ytranslation};
	}

	/**
	 * The sums for the least-squares translation step, over the interior
	 * pixels of estimate where both gradients are defined.
	 *
	 * @param source   the reference image
	 * @param estimate the current estimate, the same size
	 * @return {sum xx, sum xy, sum yy, sum x delta, sum y delta}, the
	 *         gradients being estimate's and delta = source - estimate
	 */
	private static double[] normalEquations(int[][] source, int[][] estimate)
	{
		int ydim = estimate.length;
		int xdim = estimate[0].length;
		ArrayList[][] gradient = getGradient(estimate);
		double w = 0, x = 0, z = 0, b1 = 0, b2 = 0;
		for (int i = 1; i < ydim - 1; i++)
		{
			for (int j = 1; j < xdim - 1; j++)
			{
				double xgradient = (double) gradient[i][j].get(0);
				double ygradient = (double) gradient[i][j].get(1);
				if (!Double.isNaN(xgradient) && !Double.isNaN(ygradient))
				{
					double delta = source[i][j] - estimate[i][j];
					w  += xgradient * xgradient;
					x  += xgradient * ygradient;
					z  += ygradient * ygradient;
					b1 += xgradient * delta;
					b2 += ygradient * delta;
				}
			}
		}
		return new double[] {w, x, z, b1, b2};
	}

	// ======================================================================
	// Widening and shrinking
	// ======================================================================

	/**
	 * Widens an image by inserting expand values between each pair of
	 * horizontal neighbours, evenly spaced between them and rounded.
	 *
	 * @param src    the image
	 * @param expand the values to insert between neighbours
	 * @return the (xdim - 1) * (expand + 1) + 1 wide image
	 */
	public static int[][] expandX(int src[][], int expand)
	{
		int ydim = src.length;
		int xdim = src[0].length;
		int _xdim = (xdim - 1) * expand + xdim;
		int [][] dst = new int[ydim][_xdim];
		for(int i = 0; i < ydim; i++)
		{
			int k = 0;
			for(int j = 0; j < xdim - 1; j++)
			{
				int start_value = src[i][j];
				int end_value   = src[i][j + 1];
				dst[i][k++]     = start_value;
				for(int m = 1; m <= expand; m++)
					dst[i][k++] = (int) Math.round(start_value + (double) (end_value - start_value) * m / (expand + 1));
			}
			dst[i][k] = src[i][xdim - 1];
		}
		return(dst);
	}

	/**
	 * Widens an image to 2 xdim - 1 by inserting the midpoint between each
	 * pair of horizontal neighbours.
	 *
	 * @param src the image
	 * @return the widened image
	 */
	public static double[][] expandX(double src[][])
	{
		int ydim = src.length;
		int xdim = src[0].length;
		double [][] dst = new double[ydim][2 * xdim - 1];
		for(int i = 0; i < ydim; i++)
		{
			int k = 0;
			for(int j = 0; j < xdim - 1; j++)
			{
				dst[i][k++] = src[i][j];
				dst[i][k++] = (src[i][j] + src[i][j + 1]) / 2;
			}
			dst[i][k] = src[i][xdim - 1];
		}
		return(dst);
	}

	/**
	 * {@link #expandX(double[][])} applied repeatedly.
	 *
	 * @param src        the image
	 * @param iterations the number of times
	 * @return the widened image, or src itself if iterations &lt;= 0
	 */
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

	/**
	 * Halves an image: each 2 x 2 square becomes its (unrounded) average. An
	 * odd last row or column is dropped.
	 *
	 * @param src the image
	 * @return the averages
	 */
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
