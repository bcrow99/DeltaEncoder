import java.util.*;

/**
 * Delta coding of image channels: the channel sets, the 14 delta types (how
 * each pixel is predicted from its neighbours), their maps, and the context
 * coding of the deltas, shared by the writers and readers.
 * <p>
 * A pixel is predicted from its causal neighbours -- a (left), b (above), c
 * (above-left) and d (above-right) -- and its delta is the difference. The
 * delta types are 0-5, one fixed predictor (horizontal, vertical, average,
 * MED, directional, adaptive); 6-10, one predictor per row (the scanline
 * types); 11-12, one per pixel (the frame maps); and 13, the block map, one
 * per block from a set of up to 255 predictors chosen per channel. Types 6-13
 * store a map of the choices ({@link #writeMap}).
 * <p>
 * In source order: channels; the delta types (dispatch, then 0-5, 6-10, 11-12
 * and the block map); maps and tables on disk; context coding of deltas; then
 * the research methods -- frequency estimates, other delta encoders, a map
 * Huffman coder, smoothing, and neighbour lists.
 * <p>
 * Version 1.0.
 */
public class DeltaMapper
{
	/** Not used: all of this class's methods are static. */
	public DeltaMapper() {}

	// =========================================================================
	// Channels: colour sets, differences and quantizing
	// =========================================================================

	/**
	 * The ten channel sets, by name: each is three of the six candidate channels
	 * (see {@link #getChannels}) that together give blue, green and red back.
	 */
	public static final String[] SET_NAMES = {
		"blue, green, red", "blue, red, red-green", "blue, red, blue-green",
		"blue, blue-green, red-green", "blue, blue-green, red-blue",
		"green, red, blue-green", "red, blue-green, red-green",
		"green, blue-green, red-green", "green, red-green, red-blue",
		"red, red-green, red-blue"};

	/**
	 * The candidate channels (0 blue, 1 green, 2 red, 3 blue-green, 4 red-green,
	 * 5 red-blue) of a channel set.
	 *
	 * @param set_id the channel set, 0 to 9 (see {@link #SET_NAMES})
	 * @return its three channel ids, in coding order
	 */
	public static int[] getChannels(int set_id)
	{
		int[] channel = new int[3];
		if(set_id==0)      { channel[0]=0; channel[1]=1; channel[2]=2; }
		else if(set_id==1) { channel[0]=0; channel[1]=2; channel[2]=4; }
		else if(set_id==2) { channel[0]=0; channel[1]=2; channel[2]=3; }
		else if(set_id==3) { channel[0]=0; channel[1]=3; channel[2]=4; }
		else if(set_id==4) { channel[0]=0; channel[1]=3; channel[2]=5; }
		else if(set_id==5) { channel[0]=1; channel[1]=2; channel[2]=3; }
		else if(set_id==6) { channel[0]=2; channel[1]=3; channel[2]=4; }
		else if(set_id==7) { channel[0]=1; channel[1]=3; channel[2]=4; }
		else if(set_id==8) { channel[0]=1; channel[1]=4; channel[2]=5; }
		else if(set_id==9) { channel[0]=2; channel[1]=4; channel[2]=5; }
		return channel;
	}

	/**
	 * The six candidate channels: blue, green, red, then blue-green, red-green
	 * and red-blue. Each difference channel is shifted by its minimum so it
	 * starts at 0.
	 *
	 * @param blue  the blue channel
	 * @param green the green channel
	 * @param red   the red channel
	 * @param min   set to each channel's minimum (unshifted for the colours)
	 * @return the six channels
	 */
	public static int[][] getCandidateChannels(int[] blue, int[] green, int[] red, int[] min)
	{
		int[][] c = {blue, green, red, getDifference(blue, green), getDifference(red, green), getDifference(red, blue)};
		for(int i = 0; i < 6; i++)
		{
			int m = Integer.MAX_VALUE;
			for(int v : c[i]) if(v < m) m = v;
			min[i] = m;
			if(i > 2) for(int k = 0; k < c[i].length; k++) c[i][k] -= m;
		}
		return c;
	}

	/**
	 * Rebuilds blue, green and red from the three channels of set_id (see
	 * getChannels), with the difference channels' minimums already added back.
	 * The arrays passed in are not changed.
	 *
	 * @param set_id the channel set, 0 to 9
	 * @param c0     its first channel
	 * @param c1     its second
	 * @param c2     its third
	 * @return {blue, green, red}
	 * @throws IllegalArgumentException for another set_id
	 */
	public static int[][] getBlueGreenRed(int set_id, int[] c0, int[] c1, int[] c2)
	{
		int[] blue, green, red;
		switch(set_id)
		{
			case 0:  blue = c0; green = c1; red = c2; break;
			case 1:  blue = c0; red = c1; green = getDifference(red, c2); break;
			case 2:  blue = c0; red = c1; green = getDifference(blue, c2); break;
			case 3:  blue = c0; green = getDifference(blue, c1); red = getSum(c2, green); break;
			case 4:  blue = c0; green = getDifference(blue, c1); red = getSum(blue, c2); break;
			case 5:  green = c0; red = c1; blue = getSum(c2, green); break;
			case 6:  red = c0; green = getDifference(red, c2); blue = getSum(c1, green); break;
			case 7:  green = c0; blue = getSum(green, c1); red = getSum(green, c2); break;
			case 8:  green = c0; red = getSum(green, c1); blue = getDifference(red, c2); break;
			case 9:  red = c0; green = getDifference(red, c1); blue = getDifference(red, c2); break;
			default: throw new IllegalArgumentException("set_id " + set_id);
		}
		return new int[][] {blue, green, red};
	}

	/**
	 * src1 - src2, element by element.
	 *
	 * @param src1 a channel
	 * @param src2 another, the same length
	 * @return the difference
	 * @throws IllegalArgumentException if the lengths differ
	 */
	public static int[] getDifference(int src1[], int src2[])
	{
		int length = src1.length;
		if(src2.length != length)
			throw new IllegalArgumentException("getDifference: src1.length (" + length + ") != src2.length (" + src2.length + ")");

		int[] difference = new int[length];
		for(int i = 0; i < length; i++)
			difference[i] = src1[i] - src2[i];
		return(difference);
	}

	/**
	 * src1 + src2, element by element.
	 *
	 * @param src1 a channel
	 * @param src2 another, the same length
	 * @return the sum
	 * @throws IllegalArgumentException if the lengths differ
	 */
	public static int[] getSum(int src1[], int src2[])
	{
		int length = src1.length;
		if(src2.length != length)
			throw new IllegalArgumentException("getSum: src1.length (" + length + ") != src2.length (" + src2.length + ")");

		int[] sum = new int[length];
		for(int i = 0; i < length; i++)
			sum[i] = src1[i] + src2[i];
		return(sum);
	}

	/**
	 * Shifts every value: left for a positive shift, right (arithmetic) for a
	 * negative one.
	 *
	 * @param src   the values
	 * @param shift the bits to shift by
	 * @return the shifted values
	 */
	public static int[] shift(int src[], int shift)
	{
		int length = src.length;
		int[] shifted_value = new int[length];

		if(shift < 0)
		{
			for(int i = 0; i < src.length; i++)
				shifted_value[i] = src[i] >> -shift;
		}
		else
		{
			for(int i = 0; i < src.length; i++)
				shifted_value[i] = src[i] << shift;
		}

		return(shifted_value);
	}

	/**
	 * Smallest image the writers accept (the delta types need an interior).
	 */
	public static final int MIN_DIM = 4;

	/**
	 * Size after Pixel Resolution resizing. Images under MIN_DIM in either
	 * direction are not resized.
	 *
	 * @param xdim        the width
	 * @param ydim        the height
	 * @param pixel_quant 0 (none) to 10
	 * @return {width, height}
	 */
	public static int[] getQuantizedSize(int xdim, int ydim, int pixel_quant)
	{
		if(pixel_quant == 0 || xdim < MIN_DIM || ydim < MIN_DIM)
			return new int[] {xdim, ydim};
		double factor = pixel_quant / 10.0;
		return new int[] {xdim - (int)(factor * (xdim / 2 - 2)), ydim - (int)(factor * (ydim / 2 - 2))};
	}

	/**
	 * Right-shifts a channel by pixel_shift, rounding to nearest (clamped so the
	 * result never reconstructs past 255). Returns a new array: the channel
	 * passed in is often the stored source data and must not change.
	 *
	 * @param channel     the channel
	 * @param pixel_shift the bits to drop
	 * @return the quantized channel
	 */
	public static int[] quantizeChannel(int[] channel, int pixel_shift)
	{
		if(pixel_shift == 0) return channel.clone();
		int half = 1 << (pixel_shift - 1);
		int[] rounded = new int[channel.length];
		for(int k = 0; k < channel.length; k++)
			rounded[k] = Math.min(channel[k] + half, 255);
		return shift(rounded, -pixel_shift);
	}

	/**
	 * Packs three channels into int pixels, each value shifted up by pixel_shift
	 * (undoing the colour quantization): blue in bits 16-23, green 8-15, red 0-7.
	 *
	 * @param blue        the blue channel
	 * @param green       the green channel
	 * @param red         the red channel
	 * @param xdim        the width
	 * @param pixel_shift the colour quantization (bits)
	 * @return the pixels
	 */
	public static int[] getPixel(int[] blue, int[] green, int[] red, int xdim, int pixel_shift)
	{
		int ydim = blue.length / xdim;
		int[] pixel = new int[blue.length];

		int blue_shift  = pixel_shift + 16;
		int green_shift = pixel_shift + 8;
		int red_shift   = pixel_shift;

		int k = 0;
		for(int i = 0; i < ydim; i++)
		{
			for(int j = 0; j < xdim; j++)
			{
				pixel[k] = (blue[k] << blue_shift) + (green[k] << green_shift) + (red[k] << red_shift);
				k++;
			}
		}
		return pixel;
	}

	// =========================================================================
	// Delta types
	// =========================================================================

	/**
	 * The delta types' names, 0 to 13.
	 */
	public static final String[] DELTA_TYPE_NAMES = {
		"horizontal", "vertical", "average", "med", "directional", "adaptive",
		"scanline (1)", "scanline (2)", "scanline (3)", "scanline (4)", "scanline (5)",
		"frame map (1)", "frame map (2)", "block map"};

	/**
	 * The number of delta types.
	 */
	public static final int DELTA_TYPES = DELTA_TYPE_NAMES.length;

	/**
	 * Whether a delta type stores a map.
	 *
	 * @param delta_type 0 to 13
	 * @return whether the type has a map (6-13)
	 */
	public static boolean hasMap(int delta_type) { return delta_type >= 6; }

	/**
	 * Deltas for delta_type 0-12 (see {@link #DELTA_TYPE_NAMES}).
	 *
	 * @param src        the channel
	 * @param xdim       the width
	 * @param ydim       the height
	 * @param delta_type 0 to 12
	 * @param variant    scanline 5's predictor set (type 10 only)
	 * @return [sum, deltas, map, first value] for types 6-12, [sum, deltas, first
	 *         value] otherwise
	 * @throws IllegalArgumentException for another delta_type, or 13: the block
	 *         map's predictors come from the survey ({@link #surveyedBlockMaps})
	 */
	public static ArrayList getDeltas(int[] src, int xdim, int ydim, int delta_type, int variant)
	{
		switch(delta_type)
		{
			case 0:  return getHorizontalDeltasFromValues(src, xdim, ydim);
			case 1:  return getVerticalDeltasFromValues(src, xdim, ydim);
			case 2:  return getAverageDeltasFromValues(src, xdim, ydim);
			case 3:  return getMedDeltasFromValues(src, xdim, ydim);
			case 4:  return getDirectionalDeltasFromValues(src, xdim, ydim);
			case 5:  return getAdaptiveDeltasFromValues(src, xdim, ydim);
			case 6:  return getMixedDeltasFromValues(src, xdim, ydim);
			case 7:  return getMixedDeltasFromValues2(src, xdim, ydim);
			case 8:  return getMixedDeltasFromValues4(src, xdim, ydim);
			case 9:  return getMixedDeltasFromValues16Rows(src, xdim, ydim);
			case 10: return getMixedDeltasFromValues8Rows(src, xdim, ydim, variant);
			case 11: return getIdealDeltasFromValues8(src, xdim, ydim);
			case 12: return getIdealDeltasFromValues16(src, xdim, ydim);
			case 13: throw new IllegalArgumentException("delta type 13: the block map's deltas come from the survey (surveyedBlockMaps)");
			default: throw new IllegalArgumentException("delta_type " + delta_type);
		}
	}

	/**
	 * Inverse of {@link #getDeltas}.
	 *
	 * @param delta      the deltas
	 * @param xdim       the width
	 * @param ydim       the height
	 * @param init_value the first value
	 * @param delta_type 0 to 13
	 * @param map        the map (ignored for types 0-5)
	 * @param variant    scanline 5's predictor set
	 * @param ref        the previous channel as decoded, before any minimum is
	 *                   added back (null for the first); used by the block map
	 *                   only, so its channels decode in order
	 * @return the channel
	 * @throws IllegalArgumentException for another delta_type
	 */
	public static int[] getValuesFromDeltas(int[] delta, int xdim, int ydim, int init_value, int delta_type, byte[] map, int variant, int[] ref)
	{
		switch(delta_type)
		{
			case 0:  return getValuesFromHorizontalDeltas(delta, xdim, ydim, init_value);
			case 1:  return getValuesFromVerticalDeltas(delta, xdim, ydim, init_value);
			case 2:  return getValuesFromAverageDeltas(delta, xdim, ydim, init_value);
			case 3:  return getValuesFromMedDeltas(delta, xdim, ydim, init_value);
			case 4:  return getValuesFromDirectionalDeltas(delta, xdim, ydim, init_value);
			case 5:  return getValuesFromAdaptiveDeltas(delta, xdim, ydim, init_value);
			case 6:  return getValuesFromMixedDeltas(delta, xdim, ydim, init_value, map);
			case 7:  return getValuesFromMixedDeltas2(delta, xdim, ydim, init_value, map);
			case 8:  return getValuesFromMixedDeltas4(delta, xdim, ydim, init_value, map);
			case 9:  return getValuesFromMixedDeltas16Rows(delta, xdim, ydim, init_value, map);
			case 10: return getValuesFromMixedDeltas8Rows(delta, xdim, ydim, init_value, map, variant);
			case 11: return getValuesFromIdealDeltas8(delta, xdim, ydim, init_value, map);
			case 12: return getValuesFromIdealDeltas16(delta, xdim, ydim, init_value, map);
			case 13: return getValuesFromBlockDeltas(delta, xdim, ydim, init_value, map, ref);
			default: throw new IllegalArgumentException("delta_type " + delta_type);
		}
	}

	/**
	 * As {@link #getValuesFromDeltas(int[], int, int, int, int, byte[], int, int[])},
	 * with a block map's (type 13) ids in the given table (PREDICTOR_TABLE_256
	 * for older files).
	 *
	 * @param delta      the deltas
	 * @param xdim       the width
	 * @param ydim       the height
	 * @param init_value the first value
	 * @param delta_type 0 to 13
	 * @param map        the map (ignored for types 0-5)
	 * @param variant    scanline 5's predictor set
	 * @param ref        the previous channel as decoded (block map only)
	 * @param table      the table a block map's ids index
	 * @return the channel
	 */
	public static int[] getValuesFromDeltas(int[] delta, int xdim, int ydim, int init_value, int delta_type, byte[] map, int variant, int[] ref, PredictorTable table)
	{
		if(delta_type == 13) return getValuesFromBlockDeltas(delta, xdim, ydim, init_value, map, ref, table);
		return getValuesFromDeltas(delta, xdim, ydim, init_value, delta_type, map, variant, ref);
	}

	// =========================================================================
	// Delta types 0-5: one fixed predictor
	// =========================================================================

	/**
	 * Delta type 0, horizontal: each pixel from the one to its left (the first column from the one above).
	 *
	 * @param src  the channel
	 * @param xdim the width
	 * @param ydim the height
	 * @return [sum of |delta| (Integer), deltas (int[]), the first value (Integer)]
	 */
	public static ArrayList getHorizontalDeltasFromValues(int src[], int xdim, int ydim)
	{
		int[] dst = new int[xdim * ydim];
		int sum = 0;
		int init_value = src[0];
		// init_value is updated below as the column-0 predictor; the decoder
		// needs the original src[0], so that is what gets returned.
		int original_init_value = src[0];
		int value = init_value;

		int k = 0;
		for(int i = 0; i < ydim; i++)
		{
			if(i == 0)
				dst[k++] = 0;
			else
			{
				int delta = src[k] - init_value;
				dst[k++] = delta;
				init_value += delta;
				sum += Math.abs(delta);
				value = init_value;
			}

			for(int j = 1; j < xdim; j++)
			{
				int delta = src[k] - value;
				value += delta;
				sum += Math.abs(delta);
				dst[k++] = delta;
			}
		}

		ArrayList result = new ArrayList();
		result.add(sum);
		result.add(dst);
		result.add(original_init_value);
		return result;
	}

	/**
	 * Inverse of {@link #getHorizontalDeltasFromValues}.
	 *
	 * @param src        the deltas
	 * @param xdim       the width
	 * @param ydim       the height
	 * @param init_value the first value
	 * @return the channel
	 */
	public static int[] getValuesFromHorizontalDeltas(int src[], int xdim, int ydim, int init_value)
	{
		int[] dst = new int[xdim * ydim];

		int k = 0;
		int value = init_value;
		for(int i = 0; i < ydim; i++)
		{
			if(i != 0)
				value += src[k];
			int current_value = value;
			dst[k++] = current_value;
			for(int j = 1; j < xdim; j++)
			{
				current_value += src[k];
				dst[k++] = current_value;
			}
		}
		return dst;
	}

	/**
	 * Delta type 1, vertical: each pixel from the one above (row 0 from the one to its left).
	 *
	 * @param src  the channel
	 * @param xdim the width
	 * @param ydim the height
	 * @return [sum of |delta| (Integer), deltas (int[]), the first value (Integer)]
	 */
	public static ArrayList getVerticalDeltasFromValues(int src[], int xdim, int ydim)
	{
		int[] dst = new int[xdim * ydim];
		int init_value = src[0];
		int value = init_value;
		int delta = 0;
		int sum = 0;

		int k = 0;
		for(int i = 0; i < ydim; i++)
		{
			for(int j = 0; j < xdim; j++)
			{
				if(i == 0)
				{
					if(j == 0)
						dst[k++] = 0;
					else
					{
						delta = src[k] - value;
						value += delta;
						dst[k++] = delta;
						sum += Math.abs(delta);
					}
				}
				else
				{
					delta = src[k] - src[k - xdim];
					dst[k++] = delta;
					sum += Math.abs(delta);
				}
			}
		}

		ArrayList result = new ArrayList();
		result.add(sum);
		result.add(dst);
		result.add(init_value);
		return result;
	}

	/**
	 * Inverse of {@link #getVerticalDeltasFromValues}.
	 *
	 * @param src        the deltas
	 * @param xdim       the width
	 * @param ydim       the height
	 * @param init_value the first value
	 * @return the channel
	 */
	public static int[] getValuesFromVerticalDeltas(int src[], int xdim, int ydim, int init_value)
	{
		int[] dst = new int[xdim * ydim];
		dst[0] = init_value;
		int value = init_value;

		for(int i = 1; i < xdim; i++)
		{
			value += src[i];
			dst[i] = value;
		}

		for(int i = 1; i < ydim; i++)
		{
			for(int j = 0; j < xdim; j++)
			{
				int index = i * xdim + j;
				dst[index] = dst[index - xdim] + src[index];
			}
		}

		return dst;
	}

	/**
	 * Delta type 2, average: each interior pixel from (left + above) / 2.
	 *
	 * @param src  the channel
	 * @param xdim the width
	 * @param ydim the height
	 * @return [sum of |delta| (Integer), deltas (int[]), the first value (Integer)]
	 */
	public static ArrayList getAverageDeltasFromValues(int src[], int xdim, int ydim)
	{
		int[] dst = new int[xdim * ydim];
		int sum = 0;
		int init_value = src[0];

		int k = 0;
		dst[k++] = 0;
		for(int i = 1; i < xdim; i++)
		{
			int delta = src[k] - src[k - 1];
			dst[k++] = delta;
			sum += Math.abs(delta);
		}

		for(int i = 1; i < ydim; i++)
		{
			int delta = src[k] - src[k - xdim];
			dst[k++] = delta;
			sum += Math.abs(delta);

			for(int j = 1; j < xdim; j++)
			{
				delta = src[k] - (src[k - 1] + src[k - xdim]) / 2;
				dst[k++] = delta;
				sum += Math.abs(delta);
			}
		}

		ArrayList result = new ArrayList();
		result.add(sum);
		result.add(dst);
		result.add(init_value);
		return result;
	}

	/**
	 * Inverse of {@link #getAverageDeltasFromValues}.
	 *
	 * @param src        the deltas
	 * @param xdim       the width
	 * @param ydim       the height
	 * @param init_value the first value
	 * @return the channel
	 */
	public static int[] getValuesFromAverageDeltas(int src[], int xdim, int ydim, int init_value)
	{
		int[] dst = new int[xdim * ydim];
		int k = 0;
		dst[k++] = init_value;

		for(int i = 1; i < xdim; i++)
		{
			int value = dst[k - 1] + src[k];
			dst[k++] = value;
		}

		for(int i = 1; i < ydim; i++)
		{
			int value = dst[k - xdim] + src[k];
			dst[k++] = value;
			for(int j = 1; j < xdim; j++)
			{
				value = (dst[k - 1] + dst[k - xdim]) / 2 + src[k];
				dst[k++] = value;
			}
		}

		return dst;
	}

	/**
	 * Delta type 3, MED (the median edge detector of LOCO-I/JPEG-LS): each interior pixel from min(a, b) if c &gt;= max(a, b), max(a, b) if c &lt;= min(a, b), else a + b - c (a left, b above, c above-left).
	 *
	 * @param src  the channel
	 * @param xdim the width
	 * @param ydim the height
	 * @return [sum of |delta| (Integer), deltas (int[]), the first value (Integer)]
	 */
	public static ArrayList getMedDeltasFromValues(int src[], int xdim, int ydim)
	{
		int[] dst = new int[xdim * ydim];
		int init_value = src[0];
		int sum = 0;
		int k = 0;

		dst[k++] = 0;
		for (int j = 1; j < xdim; j++)
		{
			int delta = src[k] - src[k - 1];
			dst[k++] = delta;
			sum += Math.abs(delta);
		}

		for (int i = 1; i < ydim; i++)
		{
			int delta = src[k] - src[k - xdim];
			dst[k++] = delta;
			sum += Math.abs(delta);

			for (int j = 1; j < xdim; j++)
			{
				int a = src[k - 1];
				int b = src[k - xdim];
				int c = src[k - xdim - 1];

				int pred;
				if (c >= Math.max(a, b))
					pred = Math.min(a, b);
				else if (c <= Math.min(a, b))
					pred = Math.max(a, b);
				else
					pred = a + b - c;

				delta = src[k] - pred;
				dst[k++] = delta;
				sum += Math.abs(delta);
			}
		}

		ArrayList result = new ArrayList();
		result.add(sum);
		result.add(dst);
		result.add(init_value);
		return result;
	}

	/**
	 * Inverse of {@link #getMedDeltasFromValues}.
	 *
	 * @param src        the deltas
	 * @param xdim       the width
	 * @param ydim       the height
	 * @param init_value the first value
	 * @return the channel
	 */
	public static int[] getValuesFromMedDeltas(int src[], int xdim, int ydim, int init_value)
	{
		int[] dst = new int[xdim * ydim];
		int k = 0;

		dst[k++] = init_value;
		for (int j = 1; j < xdim; j++)
		{
			dst[k] = dst[k - 1] + src[k];
			k++;
		}

		for (int i = 1; i < ydim; i++)
		{
			dst[k] = dst[k - xdim] + src[k];
			k++;

			for (int j = 1; j < xdim; j++)
			{
				int a = dst[k - 1];
				int b = dst[k - xdim];
				int c = dst[k - xdim - 1];

				int pred;
				if (c >= Math.max(a, b))
					pred = Math.min(a, b);
				else if (c <= Math.min(a, b))
					pred = Math.max(a, b);
				else
					pred = a + b - c;

				dst[k] = pred + src[k];
				k++;
			}
		}

		return dst;
	}

	/**
	 * Delta type 4, directional: each interior pixel from above, left, above-right or above-left, by which of four edge measures is strongest (the last column uses MED).
	 *
	 * @param src  the channel
	 * @param xdim the width
	 * @param ydim the height
	 * @return [sum of |delta| (Integer), deltas (int[]), the first value (Integer)]
	 */
	public static ArrayList getDirectionalDeltasFromValues(int src[], int xdim, int ydim)
	{
		int[] dst        = new int[xdim * ydim];
		int   init_value = src[0];
		int   sum        = 0;
		int   k          = 0;

		dst[k++] = 0;
		for (int j = 1; j < xdim; j++)
		{
			int delta = src[k] - src[k - 1];
			dst[k++]  = delta;
			sum      += Math.abs(delta);
		}

		for (int i = 1; i < ydim; i++)
		{
			int delta = src[k] - src[k - xdim];
			dst[k++]  = delta;
			sum      += Math.abs(delta);

			for (int j = 1; j < xdim - 1; j++)
			{
				int a = src[k - 1];
				int b = src[k - xdim];
				int c = src[k - xdim - 1];
				int d = src[k - xdim + 1];

				int h_edge  = Math.abs(b - c) + Math.abs(b - d);
				int v_edge  = Math.abs(a - c) + Math.abs(a - b);
				int dl_edge = Math.abs(c - d);
				int dr_edge = Math.abs(a - d);

				int pred;
				if (h_edge >= v_edge && h_edge >= dl_edge && h_edge >= dr_edge)
					pred = b;
				else if (v_edge >= dl_edge && v_edge >= dr_edge)
					pred = a;
				else if (dl_edge >= dr_edge)
					pred = d;
				else
					pred = c;

				delta    = src[k] - pred;
				dst[k++] = delta;
				sum     += Math.abs(delta);
			}

			// Last column: no above-right, fall back to MED
			{
				int a = src[k - 1];
				int b = src[k - xdim];
				int c = src[k - xdim - 1];

				int pred;
				if (c >= Math.max(a, b))
					pred = Math.min(a, b);
				else if (c <= Math.min(a, b))
					pred = Math.max(a, b);
				else
					pred = a + b - c;

				delta    = src[k] - pred;
				dst[k++] = delta;
				sum     += Math.abs(delta);
			}
		}

		ArrayList result = new ArrayList();
		result.add(sum);
		result.add(dst);
		result.add(init_value);
		return result;
	}

	/**
	 * Inverse of {@link #getDirectionalDeltasFromValues}.
	 *
	 * @param src        the deltas
	 * @param xdim       the width
	 * @param ydim       the height
	 * @param init_value the first value
	 * @return the channel
	 */
	public static int[] getValuesFromDirectionalDeltas(int src[], int xdim, int ydim, int init_value)
	{
		int[] dst = new int[xdim * ydim];
		int   k   = 0;

		dst[k++] = init_value;
		for (int j = 1; j < xdim; j++)
		{
			dst[k] = dst[k - 1] + src[k];
			k++;
		}

		for (int i = 1; i < ydim; i++)
		{
			dst[k] = dst[k - xdim] + src[k];
			k++;

			for (int j = 1; j < xdim - 1; j++)
			{
				int a = dst[k - 1];
				int b = dst[k - xdim];
				int c = dst[k - xdim - 1];
				int d = dst[k - xdim + 1];

				int h_edge  = Math.abs(b - c) + Math.abs(b - d);
				int v_edge  = Math.abs(a - c) + Math.abs(a - b);
				int dl_edge = Math.abs(c - d);
				int dr_edge = Math.abs(a - d);

				int pred;
				if (h_edge >= v_edge && h_edge >= dl_edge && h_edge >= dr_edge)
					pred = b;
				else if (v_edge >= dl_edge && v_edge >= dr_edge)
					pred = a;
				else if (dl_edge >= dr_edge)
					pred = d;
				else
					pred = c;

				dst[k] = pred + src[k];
				k++;
			}

			// Last column: MED
			{
				int a = dst[k - 1];
				int b = dst[k - xdim];
				int c = dst[k - xdim - 1];

				int pred;
				if (c >= Math.max(a, b))
					pred = Math.min(a, b);
				else if (c <= Math.min(a, b))
					pred = Math.max(a, b);
				else
					pred = a + b - c;

				dst[k] = pred + src[k];
				k++;
			}
		}

		return dst;
	}

	/**
	 * The adaptive predictor: with pa = |b - c| and pb = |a - c|, left (a) if
	 * pb &gt; 2 pa, above (b) if pa &gt; 2 pb, otherwise MED.
	 *
	 * @param a left
	 * @param b above
	 * @param c above-left
	 * @param d above-right (not used)
	 * @return the prediction
	 */
	private static int adaptivePred(int a, int b, int c, int d)
	{
		int pa = Math.abs(b - c);   // vertical gradient at above-left corner
		int pb = Math.abs(a - c);   // horizontal gradient at above-left corner
		if (pb > pa * 2) return a;  // left
		if (pa > pb * 2) return b;  // above
		// otherwise MED
		if (c >= Math.max(a, b)) return Math.min(a, b);
		if (c <= Math.min(a, b)) return Math.max(a, b);
		return a + b - c;
	}

	/**
	 * Delta type 5, adaptive: each interior pixel from {@link #adaptivePred}, decided from its causal neighbours, so no map is needed.
	 *
	 * @param src  the channel
	 * @param xdim the width
	 * @param ydim the height
	 * @return [sum of |delta| (Integer), deltas (int[]), the first value (Integer)]
	 */
	public static ArrayList getAdaptiveDeltasFromValues(int[] src, int xdim, int ydim)
	{
		int[] dst        = new int[xdim * ydim];
		int   init_value = src[0];
		int   sum        = 0;
		int   k          = 0;

		// Row 0: horizontal.
		dst[k++] = 0;
		for (int j = 1; j < xdim; j++)
		{
			int delta = src[k] - src[k - 1];
			dst[k++]  = delta;
			sum      += Math.abs(delta);
		}

		// Rows 1..ydim-1.
		for (int i = 1; i < ydim; i++)
		{
			// Col 0: vertical.
			int delta = src[k] - src[k - xdim];
			dst[k++]  = delta;
			sum      += Math.abs(delta);

			// Cols 1..xdim-2: adaptive rule.
			for (int j = 1; j < xdim - 1; j++)
			{
				int a = src[k - 1], b = src[k - xdim];
				int c = src[k - xdim - 1], d = src[k - xdim + 1];
				delta    = src[k] - adaptivePred(a, b, c, d);
				dst[k++] = delta;
				sum     += Math.abs(delta);
			}

			// Last col: horizontal.
			int delta2 = src[k] - src[k - 1];
			dst[k++]   = delta2;
			sum       += Math.abs(delta2);
		}

		ArrayList result = new ArrayList();
		result.add(sum);
		result.add(dst);
		result.add(init_value);
		return result;
	}

	/**
	 * Inverse of {@link #getAdaptiveDeltasFromValues}.
	 *
	 * @param src        the deltas
	 * @param xdim       the width
	 * @param ydim       the height
	 * @param init_value the first value
	 * @return the channel
	 */
	public static int[] getValuesFromAdaptiveDeltas(int[] src, int xdim, int ydim, int init_value)
	{
		int[] dst = new int[xdim * ydim];
		int   k   = 0;

		// Row 0: horizontal cumsum.
		dst[k++] = init_value;
		for (int j = 1; j < xdim; j++) { dst[k] = dst[k - 1] + src[k]; k++; }

		// Rows 1..ydim-1.
		for (int i = 1; i < ydim; i++)
		{
			// Col 0: vertical.
			dst[k] = dst[k - xdim] + src[k]; k++;

			// Cols 1..xdim-2: adaptive rule.
			for (int j = 1; j < xdim - 1; j++)
			{
				int a = dst[k - 1], b = dst[k - xdim];
				int c = dst[k - xdim - 1], d = dst[k - xdim + 1];
				dst[k] = adaptivePred(a, b, c, d) + src[k]; k++;
			}

			// Last col: horizontal.
			dst[k] = dst[k - 1] + src[k]; k++;
		}

		return dst;
	}

	// =========================================================================
	// Delta types 6-10: one predictor per row (scanline)
	// =========================================================================

	/**
	 * Delta type 6, scanline 1: one predictor per row -- left, above, (left + above) / 2 or MED -- the one whose deltas have the smallest Shannon limit. The map has one entry (0-3) per row from row 1.
	 *
	 * @param src  the channel
	 * @param xdim the width
	 * @param ydim the height
	 * @return [sum of |delta| (Integer), deltas (int[]), map (byte[]), the first value (Integer)]
	 */
	public static ArrayList getMixedDeltasFromValues(int src[], int xdim, int ydim)
	{
		byte[] map = new byte[ydim - 1];

		for (int i = 1; i < ydim; i++)
		{
			int[][] delta = new int[4][xdim - 2];

			int k = i * xdim + 1;
			for (int j = 1; j < xdim - 1; j++)
			{
				delta[0][j - 1] = src[k] - src[k - 1];
				delta[1][j - 1] = src[k] - src[k - xdim];
				delta[2][j - 1] = src[k] - (src[k - 1] + src[k - xdim]) / 2;

				int a = src[k - 1];
				int b = src[k - xdim];
				int c = src[k - xdim - 1];

				int pred;
				if (c >= Math.max(a, b))
					pred = Math.min(a, b);
				else if (c <= Math.min(a, b))
					pred = Math.max(a, b);
				else
					pred = a + b - c;

				delta[3][j - 1] = src[k] - pred;
				k++;
			}

			int[] limit = new int[4];
			for (int j = 0; j < 4; j++)
			{
				int[] current_delta = delta[j];

				int delta_min = current_delta[0];
				int delta_max = current_delta[0];
				for (k = 1; k < current_delta.length; k++)
				{
					if (current_delta[k] < delta_min)      delta_min = current_delta[k];
					else if (current_delta[k] > delta_max) delta_max = current_delta[k];
				}

				for (k = 0; k < current_delta.length; k++)
					current_delta[k] -= delta_min;
				int range = delta_max - delta_min;
				int[] frequency = new int[range + 1];
				for (k = 0; k < current_delta.length; k++)
					frequency[current_delta[k]]++;
				double shannon_limit = CodeMapper.getShannonLimit(frequency);
				limit[j] = (int) Math.floor(shannon_limit);
			}

			int value = limit[0];
			int index = 0;
			for (k = 1; k < 4; k++)
			{
				if (limit[k] < value) { value = limit[k]; index = k; }
			}
			map[i - 1] = (byte) index;
		}

		int[] dst       = new int[xdim * ydim];
		int init_value  = src[0];
		// The original src[0] (init_value changes below).
		int original_init_value = src[0];
		int sum         = 0;
		int k           = 0;

		dst[k++] = 0;
		int delta = src[k] - init_value;
		int value = src[k];
		dst[k++] = delta;
		sum += Math.abs(delta);

		for (int i = 2; i < xdim; i++)
		{
			delta = src[k] - value;
			value = src[k];
			dst[k++] = delta;
			sum += Math.abs(delta);
		}

		for (int i = 1; i < ydim; i++)
		{
			delta      = src[k] - init_value;
			init_value = src[k];
			dst[k++]   = delta;
			sum       += Math.abs(delta);

			byte m = map[i - 1];

			if (m == 0)
			{
				for (int j = 1; j < xdim - 1; j++) { delta = src[k] - src[k-1]; dst[k++] = delta; }
				delta = src[k] - src[k-1]; dst[k++] = delta; sum += Math.abs(delta);
			}
			else if (m == 1)
			{
				for (int j = 1; j < xdim - 1; j++) { delta = src[k] - src[k-xdim]; dst[k++] = delta; }
				delta = src[k] - src[k-1]; dst[k++] = delta; sum += Math.abs(delta);
			}
			else if (m == 2)
			{
				for (int j = 1; j < xdim - 1; j++) { delta = src[k] - (src[k-1]+src[k-xdim])/2; dst[k++] = delta; }
				delta = src[k] - src[k-1]; dst[k++] = delta; sum += Math.abs(delta);
			}
			else if (m == 3)
			{
				for (int j = 1; j < xdim - 1; j++)
				{
					int a = src[k - 1];
					int b = src[k - xdim];
					int c = src[k - xdim - 1];

					int pred;
					if (c >= Math.max(a, b))
						pred = Math.min(a, b);
					else if (c <= Math.min(a, b))
						pred = Math.max(a, b);
					else
						pred = a + b - c;

					delta = src[k] - pred;
					dst[k++] = delta;
					sum += Math.abs(delta);
				}
				delta = src[k] - src[k-1]; dst[k++] = delta;
			}
		}

		ArrayList result = new ArrayList();
		result.add(sum);
		result.add(dst);
		result.add(map);
		result.add(original_init_value);
		return result;
	}

	/**
	 * Inverse of {@link #getMixedDeltasFromValues}.
	 *
	 * @param src        the deltas
	 * @param xdim       the width
	 * @param ydim       the height
	 * @param init_value the first value
	 * @param map        the map getMixedDeltasFromValues returned
	 * @return the channel
	 */
	public static int[] getValuesFromMixedDeltas(int[] src, int xdim, int ydim, int init_value, byte[] map)
	{
		int[] dst = new int[xdim * ydim];
		int k     = 0;
		int value = init_value;

		dst[k++] = init_value;
		for (int i = 1; i < xdim; i++) { value += src[k]; dst[k++] = value; }

		for (int i = 1; i < ydim; i++)
		{
			init_value += src[k];
			dst[k++] = init_value;

			int m = map[i - 1];
			if (m == 0)
			{
				for (int j = 1; j < xdim - 1; j++) { value = dst[k-1] + src[k]; dst[k++] = value; }
			}
			else if (m == 1)
			{
				for (int j = 1; j < xdim - 1; j++) { value = dst[k-xdim] + src[k]; dst[k++] = value; }
			}
			else if (m == 2)
			{
				for (int j = 1; j < xdim - 1; j++) { value = (dst[k-xdim]+dst[k-1])/2 + src[k]; dst[k++] = value; }
			}
			else if (m == 3)
			{
				for (int j = 1; j < xdim - 1; j++)
				{
					int a = dst[k - 1];
					int b = dst[k - xdim];
					int c = dst[k - xdim - 1];

					int pred;
					if (c >= Math.max(a, b))
						pred = Math.min(a, b);
					else if (c <= Math.min(a, b))
						pred = Math.max(a, b);
					else
						pred = a + b - c;

					value = pred + src[k];
					dst[k++] = value;
				}
			}
			value = dst[k-1] + src[k];
			dst[k++] = value;
		}

		return dst;
	}

	/**
	 * Delta type 7, scanline 2: one predictor per row -- left, above, (left + above) / 2 or (left + above-right) / 2 -- the one with the smallest sum of |delta|. The map has one entry (0-3) per row from row 1.
	 *
	 * @param src  the channel
	 * @param xdim the width
	 * @param ydim the height
	 * @return [sum of |delta| (Integer), deltas (int[]), map (byte[]), the first value (Integer)]
	 */
	public static ArrayList getMixedDeltasFromValues2(int src[], int xdim, int ydim)
	{
		byte[] map = new byte[ydim - 1];

		for(int i = 1; i < ydim; i++)
		{
			int[] sum = new int[4];

			for(int j = 1; j < xdim - 1; j++)
			{
				int k = i * xdim + j;
				sum[0] += Math.abs(src[k] - src[k - 1]);
				sum[1] += Math.abs(src[k] - src[k - xdim]);
				sum[2] += Math.abs(src[k] - (src[k-1] + src[k-xdim]) / 2);
				sum[3] += Math.abs(src[k] - (src[k-1] + src[k-xdim+1]) / 2);
			}

			int value = sum[0];
			int index = 0;
			for(int k = 1; k < 4; k++)
			{
				if(sum[k] < value) { value = sum[k]; index = k; }
			}
			map[i - 1] = (byte) index;
		}

		int[] dst       = new int[xdim * ydim];
		int init_value  = src[0];
		// The original src[0] (init_value changes below).
		int original_init_value = src[0];
		int value       = init_value;
		int sum         = 0;

		for(int i = 0; i < ydim; i++)
		{
			if(i == 0)
			{
				for(int j = 0; j < xdim; j++)
				{
					if(j == 0)
						dst[j] = 0;
					else
					{
						int delta = src[j] - value;
						value += delta;
						dst[j] = delta;
					}
				}
			}
			else
			{
				int k = i * xdim;
				int delta = src[k] - init_value;
				init_value = src[k];
				dst[k] = delta;
				k++;

				int m = map[i - 1];

				for(int j = 1; j < xdim - 1; j++)
				{
					if(m == 0)      delta = src[k] - src[k - 1];
					else if(m == 1) delta = src[k] - src[k - xdim];
					else if(m == 2) delta = src[k] - (src[k-1] + src[k-xdim]) / 2;
					else            delta = src[k] - (src[k-1] + src[k-xdim+1]) / 2; // m == 3

					dst[k++] = delta;
					sum += Math.abs(delta);
				}

				delta = src[k] - src[k - 1];
				dst[k++] = delta;
				sum += Math.abs(delta);
			}
		}

		ArrayList result = new ArrayList();
		result.add(sum);
		result.add(dst);
		result.add(map);
		result.add(original_init_value);
		return result;
	}

	/**
	 * Inverse of {@link #getMixedDeltasFromValues2}.
	 *
	 * @param src        the deltas
	 * @param xdim       the width
	 * @param ydim       the height
	 * @param init_value the first value
	 * @param map        the map getMixedDeltasFromValues2 returned
	 * @return the channel
	 */
	public static int[] getValuesFromMixedDeltas2(int[] src, int xdim, int ydim, int init_value, byte[] map)
	{
		int[] dst = new int[xdim * ydim];
		dst[0] = init_value;
		int value = init_value;

		for(int i = 1; i < xdim; i++) { value += src[i]; dst[i] = value; }

		for(int i = 1; i < ydim; i++)
		{
			byte m = map[i - 1];
			for(int j = 0; j < xdim; j++)
			{
				int k = i * xdim + j;
				if(j == 0)
				{
					init_value += src[k];
					dst[k] = init_value;
				}
				else if(j < xdim - 1)
				{
					if(m == 0)      value = dst[k - 1];
					else if(m == 1) value = dst[k - xdim];
					else if(m == 2) value = (dst[k-1] + dst[k-xdim]) / 2;
					else if(m == 3) value = (dst[k-1] + dst[k-xdim+1]) / 2;

					value += src[k];
					dst[k] = value;
				}
				else
				{
					value = dst[k-1] + src[k];
					dst[k] = value;
				}
			}
		}
		return dst;
	}

	/**
	 * Delta type 8, scanline 3: one predictor per row -- left, (left + above) / 2, MED or directional -- the one whose deltas have the smallest Shannon limit. The map has one entry (0-3) per row from row 1.
	 *
	 * @param src  the channel
	 * @param xdim the width
	 * @param ydim the height
	 * @return [sum of |delta| (Integer), deltas (int[]), map (byte[]), the first value (Integer)]
	 */
	public static ArrayList getMixedDeltasFromValues4(int src[], int xdim, int ydim)
	{
		byte[] map = new byte[ydim - 1];

		for (int i = 1; i < ydim; i++)
		{
			int[][] delta = new int[4][xdim - 2];

			int k = i * xdim + 1;
			for (int j = 1; j < xdim - 1; j++)
			{
				int a = src[k - 1];
				int b = src[k - xdim];
				int c = src[k - xdim - 1];
				int d = src[k - xdim + 1];

				delta[0][j - 1] = src[k] - a;
				delta[1][j - 1] = src[k] - (a + b) / 2;

				int med_pred;
				if (c >= Math.max(a, b))
					med_pred = Math.min(a, b);
				else if (c <= Math.min(a, b))
					med_pred = Math.max(a, b);
				else
					med_pred = a + b - c;
				delta[2][j - 1] = src[k] - med_pred;

				int h_edge  = Math.abs(b - c) + Math.abs(b - d);
				int v_edge  = Math.abs(a - c) + Math.abs(a - b);
				int dl_edge = Math.abs(c - d);
				int dr_edge = Math.abs(a - d);

				int dir_pred;
				if (h_edge >= v_edge && h_edge >= dl_edge && h_edge >= dr_edge)
					dir_pred = b;
				else if (v_edge >= dl_edge && v_edge >= dr_edge)
					dir_pred = a;
				else if (dl_edge >= dr_edge)
					dir_pred = d;
				else
					dir_pred = c;
				delta[3][j - 1] = src[k] - dir_pred;

				k++;
			}

			int[] limit = new int[4];
			for (int j = 0; j < 4; j++)
			{
				int[] current_delta = delta[j];

				int delta_min = current_delta[0];
				int delta_max = current_delta[0];
				for (k = 1; k < current_delta.length; k++)
				{
					if (current_delta[k] < delta_min)      delta_min = current_delta[k];
					else if (current_delta[k] > delta_max) delta_max = current_delta[k];
				}

				for (k = 0; k < current_delta.length; k++)
					current_delta[k] -= delta_min;
				int range = delta_max - delta_min;
				int[] frequency = new int[range + 1];
				for (k = 0; k < current_delta.length; k++)
					frequency[current_delta[k]]++;
				double shannon_limit = CodeMapper.getShannonLimit(frequency);
				limit[j] = (int) Math.floor(shannon_limit);
			}

			int value = limit[0];
			int index = 0;
			for (k = 1; k < 4; k++)
			{
				if (limit[k] < value) { value = limit[k]; index = k; }
			}
			map[i - 1] = (byte) index;
		}

		int[] dst       = new int[xdim * ydim];
		int init_value  = src[0];
		// The original src[0] (init_value changes below).
		int original_init_value = src[0];
		int sum         = 0;
		int k           = 0;

		dst[k++] = 0;
		int delta = src[k] - init_value;
		int value = src[k];
		dst[k++] = delta;
		sum += Math.abs(delta);

		for (int i = 2; i < xdim; i++)
		{
			delta = src[k] - value;
			value = src[k];
			dst[k++] = delta;
			sum += Math.abs(delta);
		}

		for (int i = 1; i < ydim; i++)
		{
			delta      = src[k] - init_value;
			init_value = src[k];
			dst[k++]   = delta;
			sum       += Math.abs(delta);

			byte m = map[i - 1];

			if (m == 0)
			{
				for (int j = 1; j < xdim - 1; j++) { delta = src[k]-src[k-1]; dst[k++] = delta; sum += Math.abs(delta); }
				delta = src[k] - src[k-1]; dst[k++] = delta; sum += Math.abs(delta);
			}
			else if (m == 1)
			{
				for (int j = 1; j < xdim - 1; j++) { delta = src[k]-(src[k-1]+src[k-xdim])/2; dst[k++] = delta; sum += Math.abs(delta); }
				delta = src[k] - src[k-1]; dst[k++] = delta; sum += Math.abs(delta);
			}
			else if (m == 2)
			{
				for (int j = 1; j < xdim - 1; j++)
				{
					int a = src[k - 1];
					int b = src[k - xdim];
					int c = src[k - xdim - 1];

					int pred;
					if (c >= Math.max(a, b))
						pred = Math.min(a, b);
					else if (c <= Math.min(a, b))
						pred = Math.max(a, b);
					else
						pred = a + b - c;

					delta = src[k] - pred;
					dst[k++] = delta;
					sum += Math.abs(delta);
				}
				delta = src[k] - src[k-1]; dst[k++] = delta; sum += Math.abs(delta);
			}
			else if (m == 3)
			{
				for (int j = 1; j < xdim - 1; j++)
				{
					int a = src[k - 1];
					int b = src[k - xdim];
					int c = src[k - xdim - 1];
					int d = src[k - xdim + 1];

					int h_edge  = Math.abs(b - c) + Math.abs(b - d);
					int v_edge  = Math.abs(a - c) + Math.abs(a - b);
					int dl_edge = Math.abs(c - d);
					int dr_edge = Math.abs(a - d);

					int pred;
					if (h_edge >= v_edge && h_edge >= dl_edge && h_edge >= dr_edge)
						pred = b;
					else if (v_edge >= dl_edge && v_edge >= dr_edge)
						pred = a;
					else if (dl_edge >= dr_edge)
						pred = d;
					else
						pred = c;

					delta = src[k] - pred;
					dst[k++] = delta;
					sum += Math.abs(delta);
				}
				// Last column: MED fallback
				{
					int a = src[k - 1];
					int b = src[k - xdim];
					int c = src[k - xdim - 1];

					int pred;
					if (c >= Math.max(a, b))
						pred = Math.min(a, b);
					else if (c <= Math.min(a, b))
						pred = Math.max(a, b);
					else
						pred = a + b - c;

					delta = src[k] - pred;
					dst[k++] = delta;
					sum += Math.abs(delta);
				}
			}
		}

		ArrayList result = new ArrayList();
		result.add(sum);
		result.add(dst);
		result.add(map);
		result.add(original_init_value);
		return result;
	}

	/**
	 * Inverse of {@link #getMixedDeltasFromValues4}.
	 *
	 * @param src        the deltas
	 * @param xdim       the width
	 * @param ydim       the height
	 * @param init_value the first value
	 * @param map        the map getMixedDeltasFromValues4 returned
	 * @return the channel
	 */
	public static int[] getValuesFromMixedDeltas4(int[] src, int xdim, int ydim, int init_value, byte[] map)
	{
		int[] dst   = new int[xdim * ydim];
		int   k     = 0;
		int   value = init_value;

		dst[k++] = init_value;
		for (int i = 1; i < xdim; i++) { value += src[k]; dst[k++] = value; }

		for (int i = 1; i < ydim; i++)
		{
			init_value += src[k];
			dst[k++] = init_value;

			int m = map[i - 1];

			if (m == 0)
			{
				for (int j = 1; j < xdim - 1; j++) { value = dst[k-1] + src[k]; dst[k++] = value; }
			}
			else if (m == 1)
			{
				for (int j = 1; j < xdim - 1; j++) { value = (dst[k-1]+dst[k-xdim])/2 + src[k]; dst[k++] = value; }
			}
			else if (m == 2)
			{
				for (int j = 1; j < xdim - 1; j++)
				{
					int a = dst[k - 1];
					int b = dst[k - xdim];
					int c = dst[k - xdim - 1];

					int pred;
					if (c >= Math.max(a, b))
						pred = Math.min(a, b);
					else if (c <= Math.min(a, b))
						pred = Math.max(a, b);
					else
						pred = a + b - c;

					value = pred + src[k];
					dst[k++] = value;
				}
			}
			else if (m == 3)
			{
				for (int j = 1; j < xdim - 1; j++)
				{
					int a = dst[k - 1];
					int b = dst[k - xdim];
					int c = dst[k - xdim - 1];
					int d = dst[k - xdim + 1];

					int h_edge  = Math.abs(b - c) + Math.abs(b - d);
					int v_edge  = Math.abs(a - c) + Math.abs(a - b);
					int dl_edge = Math.abs(c - d);
					int dr_edge = Math.abs(a - d);

					int pred;
					if (h_edge >= v_edge && h_edge >= dl_edge && h_edge >= dr_edge)
						pred = b;
					else if (v_edge >= dl_edge && v_edge >= dr_edge)
						pred = a;
					else if (dl_edge >= dr_edge)
						pred = d;
					else
						pred = c;

					value = pred + src[k];
					dst[k++] = value;
				}
				// Last column: MED fallback
				{
					int a = dst[k - 1];
					int b = dst[k - xdim];
					int c = dst[k - xdim - 1];

					int pred;
					if (c >= Math.max(a, b))
						pred = Math.min(a, b);
					else if (c <= Math.min(a, b))
						pred = Math.max(a, b);
					else
						pred = a + b - c;

					value = pred + src[k];
					dst[k++] = value;
				}
				continue;
			}

			value = dst[k-1] + src[k];
			dst[k++] = value;
		}

		return dst;
	}

	/**
	 * The 16 predictors of scanline 4 (delta type 9), from left a, above b,
	 * above-left c and above-right d: a, b, c, d, the averages of a+b, b+c, a+c,
	 * b+d and c+d, the average of all four, a+b-c, MED, and the weighted blends
	 * (3a+b)/4, (a+3b)/4, (3a+d)/4 and (3b+a)/4.
	 *
	 * @param a left
	 * @param b above
	 * @param c above-left
	 * @param d above-right
	 * @param p the predictor, 0 to 15
	 * @return the prediction
	 */
	private static int pred16(int a, int b, int c, int d, int p)
	{
		switch (p)
		{
			case  0: return a;                                                              // left
			case  1: return b;                                                              // above
			case  2: return c;                                                              // above-left
			case  3: return d;                                                              // above-right
			case  4: return (a + b) >> 1;                                                  // avg left+above
			case  5: return (b + c) >> 1;                                                  // avg above+above-left
			case  6: return (a + c) >> 1;                                                  // avg left+above-left
			case  7: return (b + d) >> 1;                                                  // avg above+above-right
			case  8: return (c + d) >> 1;                                                  // avg above-left+above-right
			case  9: return (a + b + c + d + 2) >> 2;                                     // avg all four
			case 10: return a + b - c;                                                     // gradient (lossless JPEG)
			case 11: {                                                                      // MED
				if (c >= Math.max(a, b)) return Math.min(a, b);
				if (c <= Math.min(a, b)) return Math.max(a, b);
				return a + b - c;
			}
			case 12: return (a * 3 + b + 2) >> 2;                                         // weighted 3:1 left:above
			case 13: return (a + b * 3 + 2) >> 2;                                         // weighted 1:3 left:above
			case 14: return (a * 3 + d + 2) >> 2;                                         // weighted left+above-right
			case 15: return (b * 3 + a + 2) >> 2;                                         // weighted above+left
			default: return a;
		}
	}

	/**
	 * Delta type 9, scanline 4: each row (row 0 too) uses whichever of the 16 predictors of {@link #pred16} has the smallest sum of |delta|, with missing neighbours taken as 0. The map has one entry (0-15) per row.
	 *
	 * @param src  the channel
	 * @param xdim the width
	 * @param ydim the height
	 * @return [sum of |delta| (Integer), deltas (int[]), map (byte[]), the first value (Integer)]
	 */
	public static ArrayList getMixedDeltasFromValues16Rows(int[] src, int xdim, int ydim)
	{
		int[]  dst = new int[xdim * ydim];
		byte[] map = new byte[ydim];   // one entry per row, value 0-15

		dst[0] = 0;
		for (int row = 0; row < ydim; row++)
		{
			int best_pred = 0, best_sad = Integer.MAX_VALUE;
			for (int p = 0; p < 16; p++)
			{
				int sad = 0;
				for (int col = 0; col < xdim; col++)
				{
					int k = row * xdim + col;
					if (k == 0) continue;
					int a = (col > 0)                    ? src[k - 1]         : 0;
					int b = (row > 0)                    ? src[k - xdim]      : 0;
					int c = (row > 0 && col > 0)         ? src[k - xdim - 1]  : 0;
					int d = (row > 0 && col < xdim - 1) ? src[k - xdim + 1]  : 0;
					sad += Math.abs(src[k] - pred16(a, b, c, d, p));
				}
				if (sad < best_sad) { best_sad = sad; best_pred = p; }
			}
			map[row] = (byte) best_pred;
			for (int col = 0; col < xdim; col++)
			{
				int k = row * xdim + col;
				if (k == 0) continue;
				int a = (col > 0)                    ? src[k - 1]         : 0;
				int b = (row > 0)                    ? src[k - xdim]      : 0;
				int c = (row > 0 && col > 0)         ? src[k - xdim - 1]  : 0;
				int d = (row > 0 && col < xdim - 1) ? src[k - xdim + 1]  : 0;
				dst[k] = src[k] - pred16(a, b, c, d, best_pred);
			}
		}
		int total = 0;
		for (int v : dst) total += Math.abs(v);

		ArrayList result = new ArrayList();
		result.add(total);
		result.add(dst);
		result.add(map);
		result.add(src[0]);
		return result;
	}

	/**
	 * Inverse of {@link #getMixedDeltasFromValues16Rows}.
	 *
	 * @param src        the deltas
	 * @param xdim       the width
	 * @param ydim       the height
	 * @param init_value the first value
	 * @param map        the map getMixedDeltasFromValues16Rows returned
	 * @return the channel
	 */
	public static int[] getValuesFromMixedDeltas16Rows(int[] src, int xdim, int ydim, int init_value, byte[] map)
	{
		int[] dst = new int[xdim * ydim];
		dst[0] = init_value;

		for (int row = 0; row < ydim; row++)
		{
			int p = map[row] & 0xF;
			for (int col = 0; col < xdim; col++)
			{
				int k = row * xdim + col;
				if (k == 0) continue;
				int a = (col > 0)                    ? dst[k - 1]         : 0;
				int b = (row > 0)                    ? dst[k - xdim]      : 0;
				int c = (row > 0 && col > 0)         ? dst[k - xdim - 1]  : 0;
				int d = (row > 0 && col < xdim - 1) ? dst[k - xdim + 1]  : 0;
				dst[k] = src[k] + pred16(a, b, c, d, p);
			}
		}
		return dst;
	}

	/**
	 * Scanline 5's predictor sets (delta type 10): each row maps the local
	 * predictor 0-7 to a {@link #pred16} predictor. Add rows to define new
	 * variants; variant 0 is the default.
	 */
	public static final int[][] FILTER_SETS_8 = {
		// variant 0: spread coverage -- directional anchors + best composites
		{ 0, 1, 2, 3, 4, 10, 9, 5 },
		// variant 1: averaging focus -- left, above, avg(l,a), avg-all-4, gradient, MED, weighted blends
		{ 0, 1, 4, 9, 10, 11, 12, 13 },
		// variant 2: variant 1 with weighted 1:3 swapped for avg(above, above-right)
		{ 0, 1, 4, 9, 10, 11, 12, 7 },
	};

	/**
	 * Predictor p of an 8-predictor set (see {@link #FILTER_SETS_8}).
	 *
	 * @param a       left
	 * @param b       above
	 * @param c       above-left
	 * @param d       above-right
	 * @param p       the predictor, 0 to 7
	 * @param variant the set
	 * @return the prediction
	 */
	public static int pred8(int a, int b, int c, int d, int p, int variant)
	{
		return pred16(a, b, c, d, FILTER_SETS_8[variant][p]);
	}

	/**
	 * Delta type 10, scanline 5: as scanline 4, with 8 predictors chosen from
	 * pred16's by {@link #FILTER_SETS_8}.
	 *
	 * @param src     the channel
	 * @param xdim    the width
	 * @param ydim    the height
	 * @param variant the predictor set
	 * @return [sum of |delta| (Integer), deltas (int[]), map (byte[]), the first
	 *         value (Integer)]
	 */
	public static ArrayList getMixedDeltasFromValues8Rows(int[] src, int xdim, int ydim, int variant)
	{
		int[]  dst = new int[xdim * ydim];
		byte[] map = new byte[ydim];

		dst[0] = 0;
		for (int row = 0; row < ydim; row++)
		{
			int best_pred = 0, best_sad = Integer.MAX_VALUE;
			for (int p = 0; p < 8; p++)
			{
				int sad = 0;
				for (int col = 0; col < xdim; col++)
				{
					int k = row * xdim + col;
					if (k == 0) continue;
					int a = (col > 0)                    ? src[k - 1]        : 0;
					int b = (row > 0)                    ? src[k - xdim]     : 0;
					int c = (row > 0 && col > 0)         ? src[k - xdim - 1] : 0;
					int d = (row > 0 && col < xdim - 1) ? src[k - xdim + 1] : 0;
					sad += Math.abs(src[k] - pred8(a, b, c, d, p, variant));
				}
				if (sad < best_sad) { best_sad = sad; best_pred = p; }
			}
			map[row] = (byte) best_pred;
			for (int col = 0; col < xdim; col++)
			{
				int k = row * xdim + col;
				if (k == 0) continue;
				int a = (col > 0)                    ? src[k - 1]        : 0;
				int b = (row > 0)                    ? src[k - xdim]     : 0;
				int c = (row > 0 && col > 0)         ? src[k - xdim - 1] : 0;
				int d = (row > 0 && col < xdim - 1) ? src[k - xdim + 1] : 0;
				dst[k] = src[k] - pred8(a, b, c, d, best_pred, variant);
			}
		}
		int total = 0;
		for (int v : dst) total += Math.abs(v);

		ArrayList result = new ArrayList();
		result.add(total);
		result.add(dst);
		result.add(map);
		result.add(src[0]);
		return result;
	}

	/**
	 * Inverse of {@link #getMixedDeltasFromValues8Rows}.
	 *
	 * @param src        the deltas
	 * @param xdim       the width
	 * @param ydim       the height
	 * @param init_value the first value
	 * @param map        the map getMixedDeltasFromValues8Rows returned
	 * @param variant    the predictor set it used
	 * @return the channel
	 */
	public static int[] getValuesFromMixedDeltas8Rows(int[] src, int xdim, int ydim,
	                                                   int init_value, byte[] map, int variant)
	{
		int[] dst = new int[xdim * ydim];
		dst[0] = init_value;

		for (int row = 0; row < ydim; row++)
		{
			int p = map[row] & 0x7;
			for (int col = 0; col < xdim; col++)
			{
				int k = row * xdim + col;
				if (k == 0) continue;
				int a = (col > 0)                    ? dst[k - 1]        : 0;
				int b = (row > 0)                    ? dst[k - xdim]     : 0;
				int c = (row > 0 && col > 0)         ? dst[k - xdim - 1] : 0;
				int d = (row > 0 && col < xdim - 1) ? dst[k - xdim + 1] : 0;
				dst[k] = src[k] + pred8(a, b, c, d, p, variant);
			}
		}
		return dst;
	}

	// =========================================================================
	// Delta types 11-12: one predictor per pixel (frame maps)
	// =========================================================================

	/**
	 * Delta type 11, frame map 1: each interior pixel uses whichever of 8
	 * predictors is closest -- left, (left + above-left) / 2, above-left,
	 * (above-left + above) / 2, above, (above + above-right) / 2, above-right,
	 * (above-right + left) / 2 -- ties to the first. Row 0 is horizontal, column
	 * 0 vertical and the last column horizontal. The map has one entry per
	 * interior pixel ((xdim - 2) * (ydim - 1)); the last row's are left at 0
	 * (left).
	 *
	 * @param src  the channel
	 * @param xdim the width
	 * @param ydim the height
	 * @return [sum of |delta| (Integer), deltas (int[]), map (byte[]), the first
	 *         value (Integer)]
	 */
	public static ArrayList getIdealDeltasFromValues8(int src[], int xdim, int ydim)
	{
		int[] dst  = new int[xdim * ydim];
		byte[] map = new byte[(xdim - 2) * (ydim - 1)];
		int init_value = src[0];

		int m = 0;
		for(int i = 1; i < ydim - 1; i++)
		{
			for(int j = 1; j < xdim - 1; j++)
			{
				int k = i * xdim + j;
				double[] delta = {
					Math.abs(src[k]-src[k-1]),
					Math.abs(src[k]-(src[k-1]+src[k-xdim-1])/2),
					Math.abs(src[k]-src[k-xdim-1]),
					Math.abs(src[k]-(src[k-xdim-1]+src[k-xdim])/2),
					Math.abs(src[k]-src[k-xdim]),
					Math.abs(src[k]-(src[k-xdim]+src[k-xdim+1])/2),
					Math.abs(src[k]-src[k-xdim+1]),
					Math.abs(src[k]-(src[k-xdim+1]+src[k-1])/2)
				};

				double addend = 0.00000001;
				Hashtable<Double, Byte> delta_table = new Hashtable<Double, Byte>();
				ArrayList key_list = new ArrayList();
				key_list.add(delta[0]); delta_table.put(delta[0], (byte)0);
				for(k = 1; k < 8; k++)
				{
					if(key_list.contains(delta[k])) { delta[k] += addend; addend *= 2.; }
					key_list.add(delta[k]); delta_table.put(delta[k], (byte)k);
				}
				Collections.sort(key_list);
				map[m++] = delta_table.get((double)key_list.get(0));
			}
		}

		int k = 0; m = 0; int sum = 0;
		for(int i = 0; i < ydim; i++)
		{
			if(i == 0)
			{
				dst[k++] = 0;
				for(int j = 1; j < xdim; j++) { int delta = src[k]-src[k-1]; dst[k++] = delta; sum += Math.abs(delta); }
			}
			else
			{
				int delta = src[k]-src[k-xdim]; dst[k++] = delta; sum += Math.abs(delta);
				for(int j = 1; j < xdim - 1; j++)
				{
					int n = map[m++];
					if(n == 0)      delta = src[k]-src[k-1];
					else if(n == 1) delta = src[k]-(src[k-1]+src[k-xdim-1])/2;
					else if(n == 2) delta = src[k]-src[k-xdim-1];
					else if(n == 3) delta = src[k]-(src[k-xdim-1]+src[k-xdim])/2;
					else if(n == 4) delta = src[k]-src[k-xdim];
					else if(n == 5) delta = src[k]-(src[k-xdim]+src[k-xdim+1])/2;
					else if(n == 6) delta = src[k]-src[k-xdim+1];
					else            delta = src[k]-(src[k-xdim+1]+src[k-1])/2;
					dst[k++] = delta; sum += Math.abs(delta);
				}
				delta = src[k]-src[k-1]; dst[k++] = delta; sum += Math.abs(delta);
			}
		}

		ArrayList result = new ArrayList();
		result.add(sum); result.add(dst); result.add(map); result.add(init_value);
		return result;
	}

	/**
	 * Inverse of {@link #getIdealDeltasFromValues8}.
	 *
	 * @param src        the deltas
	 * @param xdim       the width
	 * @param ydim       the height
	 * @param init_value the first value
	 * @param map        the map getIdealDeltasFromValues8 returned
	 * @return the channel
	 */
	public static int[] getValuesFromIdealDeltas8(int[] src, int xdim, int ydim, int init_value, byte[] map)
	{
		int[] dst = new int[xdim * ydim];
		int k = 0;
		dst[k++] = init_value;
		for(int i = 1; i < xdim; i++) { dst[k] = dst[k-1] + src[k]; k++; }

		int m = 0;
		for(int i = 1; i < ydim; i++)
		{
			init_value += src[k]; dst[k++] = init_value;
			for(int j = 1; j < xdim - 1; j++)
			{
				int n = map[m++];
				if(n == 0)      dst[k] = dst[k-1]                          + src[k];
				else if(n == 1) dst[k] = (dst[k-1]+dst[k-xdim-1])    / 2  + src[k];
				else if(n == 2) dst[k] = dst[k-xdim-1]                     + src[k];
				else if(n == 3) dst[k] = (dst[k-xdim-1]+dst[k-xdim]) / 2  + src[k];
				else if(n == 4) dst[k] = dst[k-xdim]                       + src[k];
				else if(n == 5) dst[k] = (dst[k-xdim]+dst[k-xdim+1]) / 2  + src[k];
				else if(n == 6) dst[k] = dst[k-xdim+1]                     + src[k];
				else            dst[k] = (dst[k-xdim+1]+dst[k-1])    / 2  + src[k];
				k++;
			}
			dst[k] = dst[k-1] + src[k]; k++;
		}
		return dst;
	}

	/**
	 * Delta type 12, frame map 2: each interior pixel uses whichever of 16 causal
	 * predictors is closest (a left, b above, c above-left, d above-right):
	 * <pre>
	 *   0  a           4  (a+c)&gt;&gt;1    8  (a+b)&gt;&gt;1      12  (a+b+c)&gt;&gt;2
	 *   1  c           5  (c+b)&gt;&gt;1    9  (c+d)&gt;&gt;1      13  (a+b+d)&gt;&gt;2
	 *   2  b           6  (b+d)&gt;&gt;1   10  (a+b+c+d)&gt;&gt;2  14  (a+c+d)&gt;&gt;2
	 *   3  d           7  (d+a)&gt;&gt;1   11  MED(a,b,c)     15  (b+c+d)&gt;&gt;2
	 * </pre>
	 * The map covers rows 1 to ydim - 1 and columns 1 to xdim - 2, one byte
	 * (0-15) per pixel.
	 *
	 * @param src  the channel
	 * @param xdim the width
	 * @param ydim the height
	 * @return [sum of |delta| (Integer), deltas (int[]), map (byte[]), the first
	 *         value (Integer)]
	 */
	public static ArrayList getIdealDeltasFromValues16(int src[], int xdim, int ydim)
	{
		int[]  dst        = new int[xdim * ydim];
		// Map covers all non-first rows, interior columns.
		byte[] map        = new byte[(xdim - 2) * (ydim - 1)];
		int    init_value = src[0];
		int    sum        = 0;
		int    m          = 0;

		// Pass 1: choose best of 16 causal predictors for each map pixel.
		for (int i = 1; i < ydim; i++)
		{
			for (int j = 1; j < xdim - 1; j++)
			{
				int k = i * xdim + j;
				int a = src[k - 1];           // left
				int b = src[k - xdim];        // above
				int c = src[k - xdim - 1];    // above-left
				int d = src[k - xdim + 1];    // above-right
				int e = src[k];

				int med;
				if (c >= Math.max(a, b))      med = Math.min(a, b);
				else if (c <= Math.min(a, b)) med = Math.max(a, b);
				else                          med = a + b - c;

				int[] pred = {
					a,                   //  0: left
					c,                   //  1: above-left
					b,                   //  2: above
					d,                   //  3: above-right
					(a + c) >> 1,        //  4: avg(left, above-left)
					(c + b) >> 1,        //  5: avg(above-left, above)
					(b + d) >> 1,        //  6: avg(above, above-right)
					(d + a) >> 1,        //  7: avg(above-right, left)
					(a + b) >> 1,        //  8: avg(left, above)
					(c + d) >> 1,        //  9: avg(above-left, above-right)
					(a + b + c + d) >> 2, // 10: all-four average
					med,                  // 11: MED predictor
					(a + b + c) >> 2,     // 12: three-way (a, b, c)
					(a + b + d) >> 2,     // 13: three-way (a, b, d)
					(a + c + d) >> 2,     // 14: three-way (a, c, d)
					(b + c + d) >> 2      // 15: three-way (b, c, d)
				};

				int best_abs = Integer.MAX_VALUE;
				int best_idx = 0;
				for (int n = 0; n < 16; n++)
				{
					int abs_delta = Math.abs(e - pred[n]);
					if (abs_delta < best_abs) { best_abs = abs_delta; best_idx = n; }
				}
				map[m++] = (byte) best_idx;
			}
		}

		// Pass 2: compute deltas.
		int k = 0;
		m = 0;

		// Row 0: horizontal deltas.
		dst[k++] = 0;
		for (int j = 1; j < xdim; j++)
		{
			int delta = src[k] - src[k - 1];
			dst[k++] = delta;
			sum += Math.abs(delta);
		}

		// Rows 1..ydim-1: col 0 vertical, interior map-driven, last col horizontal.
		for (int i = 1; i < ydim; i++)
		{
			int delta = src[k] - src[k - xdim];
			dst[k++] = delta;
			sum += Math.abs(delta);

			for (int j = 1; j < xdim - 1; j++)
			{
				int n = map[m++] & 0xFF;
				int a = src[k - 1];
				int b = src[k - xdim];
				int c = src[k - xdim - 1];
				int d = src[k - xdim + 1];

				int med;
				if (c >= Math.max(a, b))      med = Math.min(a, b);
				else if (c <= Math.min(a, b)) med = Math.max(a, b);
				else                          med = a + b - c;

				int pred_val;
				switch (n)
				{
					case  0: pred_val = a;              break;
					case  1: pred_val = c;              break;
					case  2: pred_val = b;              break;
					case  3: pred_val = d;              break;
					case  4: pred_val = (a + c) >> 1;  break;
					case  5: pred_val = (c + b) >> 1;  break;
					case  6: pred_val = (b + d) >> 1;  break;
					case  7: pred_val = (d + a) >> 1;  break;
					case  8: pred_val = (a + b) >> 1;  break;
					case  9: pred_val = (c + d) >> 1;  break;
					case 10: pred_val = (a + b + c + d) >> 2; break;
					case 11: pred_val = med;            break;
					case 12: pred_val = (a + b + c) >> 2; break;
					case 13: pred_val = (a + b + d) >> 2; break;
					case 14: pred_val = (a + c + d) >> 2; break;
					default: pred_val = (b + c + d) >> 2; break; // 15
				}

				delta = src[k] - pred_val;
				dst[k++] = delta;
				sum += Math.abs(delta);
			}

			int delta2 = src[k] - src[k - 1];
			dst[k++] = delta2;
			sum += Math.abs(delta2);
		}

		ArrayList result = new ArrayList();
		result.add(sum);
		result.add(dst);
		result.add(map);
		result.add(init_value);
		return result;
	}

	/**
	 * Inverse of {@link #getIdealDeltasFromValues16}.
	 *
	 * @param src        the deltas
	 * @param xdim       the width
	 * @param ydim       the height
	 * @param init_value the first value
	 * @param map        the map getIdealDeltasFromValues16 returned
	 * @return the channel
	 */
	public static int[] getValuesFromIdealDeltas16(int[] src, int xdim, int ydim, int init_value, byte[] map)
	{
		int[] dst = new int[xdim * ydim];
		int   k   = 0;
		int   m   = 0;

		// Row 0: horizontal cumsum.
		dst[k++] = init_value;
		for (int j = 1; j < xdim; j++) { dst[k] = dst[k - 1] + src[k]; k++; }

		// Rows 1..ydim-1: col 0 vertical, interior map-driven, last col horizontal.
		for (int i = 1; i < ydim; i++)
		{
			dst[k] = dst[k - xdim] + src[k]; k++;

			for (int j = 1; j < xdim - 1; j++)
			{
				int n = map[m++] & 0xFF;
				int a = dst[k - 1];           // left        [decoded]
				int b = dst[k - xdim];        // above       [decoded]
				int c = dst[k - xdim - 1];    // above-left  [decoded]
				int d = dst[k - xdim + 1];    // above-right [decoded]

				int med;
				if (c >= Math.max(a, b))      med = Math.min(a, b);
				else if (c <= Math.min(a, b)) med = Math.max(a, b);
				else                          med = a + b - c;

				int pred_val;
				switch (n)
				{
					case  0: pred_val = a;              break;
					case  1: pred_val = c;              break;
					case  2: pred_val = b;              break;
					case  3: pred_val = d;              break;
					case  4: pred_val = (a + c) >> 1;  break;
					case  5: pred_val = (c + b) >> 1;  break;
					case  6: pred_val = (b + d) >> 1;  break;
					case  7: pred_val = (d + a) >> 1;  break;
					case  8: pred_val = (a + b) >> 1;  break;
					case  9: pred_val = (c + d) >> 1;  break;
					case 10: pred_val = (a + b + c + d) >> 2; break;
					case 11: pred_val = med;            break;
					case 12: pred_val = (a + b + c) >> 2; break;
					case 13: pred_val = (a + b + d) >> 2; break;
					case 14: pred_val = (a + c + d) >> 2; break;
					default: pred_val = (b + c + d) >> 2; break; // 15
				}

				dst[k] = pred_val + src[k]; k++;
			}

			dst[k] = dst[k - 1] + src[k]; k++;
		}

		return dst;
	}

	// =========================================================================
	// Delta type 13: block map
	// =========================================================================

	// Block map (delta type 13): the interior is split into blocks (see
	// SHAPE_FIRST), and each uses one predictor from its channel's set. Row 0
	// is horizontal, column 0 vertical and the last column horizontal, as in
	// frame map 2. The map is [block shape, n, n predictor ids
	// (PREDICTOR_TABLE, see "Predictor tables" below), then one entry per
	// block, row by row: an index 0..n-1 into the ids]. Each channel's map
	// carries its own ids, so the channels may use different predictors. Some
	// predictors use the previous channel (in coding order) at the same pixel,
	// so a channel is coded and decoded with the one before it: see
	// PixelSource. The sets and the blocks' choices come from the survey
	// (surveyChannels), from the whole table, by estimated coded size.

	/**
	 * Square block sizes (BLOCK_MIN to BLOCK_MAX), and the writers' default.
	 */
	public static final int BLOCK_MIN = 4, BLOCK_MAX = 32, BLOCK_DEFAULT = 8;

	/**
	 * Block shapes, a block map's first byte: BLOCK_MIN to BLOCK_MAX is a square
	 * of that size; SHAPE_FIRST + i is BLOCK_SHAPES[i].
	 */
	public static final int SHAPE_FIRST = 64;

	/**
	 * The rectangle and line shapes, {width, height} (width 0: the whole row).
	 * Lines are the 1-high ones. Blocks are numbered row by row either way.
	 */
	public static final int[][] BLOCK_SHAPES = { {16, 4}, {32, 2}, {64, 1}, {256, 1}, {0, 1}, {4, 16}, {1, 64} };

	/**
	 * A block shape's width.
	 *
	 * @param block the shape (see {@link #SHAPE_FIRST})
	 * @param xdim  the width of the deltas (for the whole-row shape)
	 * @return the width in pixels
	 */
	public static int blockWidth(int block, int xdim)
	{
		if(block < SHAPE_FIRST) return block;
		int w = BLOCK_SHAPES[block - SHAPE_FIRST][0];
		return (w == 0) ? Math.max(1, xdim - 2) : w;
	}

	/**
	 * A block shape's height.
	 *
	 * @param block the shape
	 * @return the height in pixels
	 */
	public static int blockHeight(int block) { return (block < SHAPE_FIRST) ? block : BLOCK_SHAPES[block - SHAPE_FIRST][1]; }

	/**
	 * Whether a block map's first byte is a shape this version reads.
	 *
	 * @param block the shape
	 * @return whether it is a square size or one of BLOCK_SHAPES
	 */
	public static boolean validShape(int block)
	{
		return (block >= BLOCK_MIN && block <= BLOCK_MAX) || (block >= SHAPE_FIRST && block < SHAPE_FIRST + BLOCK_SHAPES.length);
	}

	/**
	 * A shape's name: "8" for a square, "16x4", "row" for a whole-row line.
	 *
	 * @param block the shape
	 * @return the name
	 */
	public static String shapeName(int block)
	{
		if(block < SHAPE_FIRST) return String.valueOf(block);
		int[] s = BLOCK_SHAPES[block - SHAPE_FIRST];
		return (s[0] == 0) ? "row" : s[0] + "x" + s[1];
	}

	/**
	 * Primitives 0-27 from left a, above b, above-left c and above-right d (see
	 * {@link #PRIMITIVE_NAMES} and {@link #EXTRA_PRIMITIVE_NAMES}); 22-27 are the
	 * selection predictors.
	 *
	 * @param id the primitive, 0 to 27
	 * @param a  left
	 * @param b  above
	 * @param c  above-left
	 * @param d  above-right
	 * @return the prediction
	 */
	public static int blockPredictor(int id, int a, int b, int c, int d)
	{
		switch(id)
		{
			case  0: return a;
			case  1: return b;
			case  2: return c;
			case  3: return d;
			case  4: return (a + b) >> 1;
			case  5: return (b + c) >> 1;
			case  6: return (a + c) >> 1;
			case  7: return (b + d) >> 1;
			case  8: return (c + d) >> 1;
			case  9: return (a + b + c + d + 2) >> 2;
			case 10: return a + b - c;
			case 11: if(c >= Math.max(a, b)) return Math.min(a, b); if(c <= Math.min(a, b)) return Math.max(a, b); return a + b - c;
			case 12: return (a * 3 + b + 2) >> 2;
			case 13: return (a + b * 3 + 2) >> 2;
			case 14: return (a * 3 + d + 2) >> 2;
			case 15: return (b * 3 + a + 2) >> 2;
			case 16: return (a + d) >> 1;
			case 17: return a + ((b - c) >> 1);
			case 18: return b + ((a - c) >> 1);
			case 19: return a + d - b;
			case 20: return (a + b + d + 1) / 3;
			case 21: return b + ((d - c) >> 1);
			// 22-27: selection predictors.
			case 22: { int p = a + b - c, pa = Math.abs(p - a), pb = Math.abs(p - b), pc = Math.abs(p - c);
			           return (pa <= pb && pa <= pc) ? a : (pb <= pc) ? b : c; }                 // Paeth (PNG)
			case 23: return median(a, b, c);
			case 24: return median(a, b, d);
			case 25: return median(a, d, a + d - b);                                           // MED along the up-right diagonal
			case 26: return (Math.abs(b - c) <= Math.abs(a - c)) ? a : b;                     // the flatter direction
			case 27: { int h = Math.abs(b - c), v = Math.abs(a - c), g = Math.abs(a - b);     // ... of three: left, up, up-right
			           return (h <= v && h <= g) ? a : (v <= g) ? b : d; }
			default: return b + ((d - c) >> 1);
		}
	}

	/**
	 * The median of three values.
	 *
	 * @param x a value
	 * @param y a value
	 * @param z a value
	 * @return the middle one
	 */
	private static int median(int x, int y, int z) { return Math.max(Math.min(x, y), Math.min(Math.max(x, y), z)); }

	// ---- Predictor ids --------------------------------------------------------
	//
	// A predictor id (one byte) is an index into PREDICTOR_TABLE, below.

	/**
	 * The primitives that use only a, b, c and d (blockPredictor 0-21).
	 */
	public static final int PRIMITIVES = 22;   // the primitives with only a, b, c, d (blockPredictor 0-21)

	/**
	 * Names of primitives 0-21.
	 */
	public static final String[] PRIMITIVE_NAMES = {
		"a", "b", "c", "d", "(a+b)/2", "(b+c)/2", "(a+c)/2", "(b+d)/2",
		"(c+d)/2", "(a+b+c+d)/4", "a+b-c", "MED", "(3a+b)/4", "(a+3b)/4", "(3a+d)/4", "(3b+a)/4",
		"(a+d)/2", "a+(b-c)/2", "b+(a-c)/2", "a+d-b", "(a+b+d)/3", "b+(d-c)/2" };

	/**
	 * A readable name: "MED", or "avg(MED, a)" for an average.
	 *
	 * @param id a PREDICTOR_TABLE id
	 * @return its name
	 */
	public static String predictorName(int id) { return PREDICTOR_TABLE.name(id); }

	/**
	 * The predictor ids a block map uses (map[2..1+n]).
	 *
	 * @param map a block map
	 * @return its ids
	 */
	public static int[] getMapIds(byte[] map)
	{
		int[] ids = new int[map[1] & 0xFF];
		for(int p = 0; p < ids.length; p++) ids[p] = map[2 + p] & 0xFF;
		return ids;
	}

	/**
	 * Where a block map's entries start: after the shape, n and the n ids.
	 *
	 * @param map a block map
	 * @return 2 + n
	 */
	public static int mapHeaderLength(byte[] map) { return 2 + (map[1] & 0xFF); }

	/**
	 * A channel's name in a channel set: blue, green, red, blue-green,
	 * red-green or red-blue.
	 *
	 * @param set_id the channel set
	 * @param i      the channel, in coding order
	 * @return its name
	 */
	public static String channelName(int set_id, int i) { return SET_NAMES[set_id].split(", ")[i]; }

	/**
	 * A channel's block map in a line: "Channel 0 - blue: 8 x 8, 2,400 blocks".
	 *
	 * @param set_id the channel set
	 * @param i      the channel, in coding order
	 * @param map    its block map
	 * @param xdim   the row width of the deltas
	 * @return the line (no newline)
	 */
	public static String describeBlockSize(int set_id, int i, byte[] map, int xdim)
	{
		int block = map[0] & 0xFF;
		return String.format("Channel %d - %s: %d x %d, %,d blocks", i, channelName(set_id, i), blockWidth(block, xdim), blockHeight(block),
			map.length - mapHeaderLength(map));
	}

	/**
	 * The predictors a block map's blocks use, most used first (ties in the
	 * map's order), one per line: the number of blocks, then the name.
	 *
	 * @param map    a block map
	 * @param indent put before each line
	 * @return the lines, each ending in a newline
	 */
	public static String describeBlockPredictors(byte[] map, String indent)
	{
		return describeBlockPredictors(map, indent, PREDICTOR_TABLE);
	}

	/**
	 * As {@link #describeBlockPredictors(byte[], String)}, with the map's ids
	 * in the given table.
	 *
	 * @param map    the block map
	 * @param indent put before each line
	 * @param table  the table the map's ids index
	 * @return the lines, each ending in a newline
	 */
	public static String describeBlockPredictors(byte[] map, String indent, PredictorTable table)
	{
		int[] ids   = getMapIds(map);
		int[] count = new int[ids.length];
		for(int q = mapHeaderLength(map); q < map.length; q++) count[map[q] & 0xFF]++;
		Integer[] order = new Integer[ids.length];
		for(int p = 0; p < ids.length; p++) order[p] = p;
		Arrays.sort(order, (a, b) -> (count[a] != count[b]) ? count[b] - count[a] : a - b);
		StringBuilder t = new StringBuilder();
		for(int p : order) if(count[p] > 0) t.append(String.format("%s%,9d  %s%n", indent, count[p], table.name(ids[p])));
		return t.toString();
	}

	/**
	 * The share of the channels' predicted pixels (all but the first row and
	 * the first and last columns) whose block's predictor uses least squares
	 * (LS or LS wide, alone or averaged).
	 *
	 * @param map  the channels' block maps (each with its id list)
	 * @param xdim the width
	 * @param ydim the height
	 * @return the share, 0 to 1
	 */
	public static double leastSquaresShare(byte[][] map, int xdim, int ydim)
	{
		long ls = 0, all = 0;
		for(byte[] m : map)
		{
			int block = m[0] & 0xFF, bw_ = blockWidth(block, xdim), bh_ = blockHeight(block);
			int bw = blocksAcross(xdim, block), bh = blocksDown(ydim, block), head = mapHeaderLength(m);
			for(int by = 0; by < bh; by++)
				for(int bx = 0; bx < bw; bx++)
				{
					long area = (long) Math.min(bw_, xdim - 2 - bx * bw_) * Math.min(bh_, ydim - 1 - by * bh_);
					int  pid  = m[2 + (m[head + by * bw + bx] & 0xFF)] & 0xFF;
					all += area;
					if(PREDICTOR_TABLE.first(pid) >= LS_FIRST || PREDICTOR_TABLE.second(pid) >= LS_FIRST) ls += area;
				}
		}
		return (all == 0) ? 0 : (double) ls / all;
	}

	/**
	 * The channel set, then each channel's block size, number of blocks and
	 * predictors (see {@link #describeBlockSize} and {@link #describeBlockPredictors}).
	 *
	 * @param set_id the channel set
	 * @param map    the channels' block maps, in coding order
	 * @param xdim   the row width of the deltas
	 * @return the description, each line ending in a newline
	 */
	public static String describeBlockMaps(int set_id, byte[][] map, int xdim)
	{
		return describeBlockMaps(set_id, map, xdim, PREDICTOR_TABLE);
	}

	/**
	 * As {@link #describeBlockMaps(int, byte[][], int)}, with the maps' ids in
	 * the given table (PREDICTOR_TABLE_256 for older files).
	 *
	 * @param set_id the channel set
	 * @param map    the channels' block maps, in coding order
	 * @param xdim   the row width of the deltas
	 * @param table  the table the maps' ids index
	 * @return the description, each line ending in a newline
	 */
	public static String describeBlockMaps(int set_id, byte[][] map, int xdim, PredictorTable table)
	{
		StringBuilder t = new StringBuilder("Channel set: " + SET_NAMES[set_id] + String.format("%n"));
		for(int i = 0; i < map.length; i++)
			t.append(describeBlockSize(set_id, i, map[i], xdim)).append(String.format("%n")).append(describeBlockPredictors(map[i], "  ", table));
		return t.toString();
	}

	// ---- Choosing the block size and predictor set -----------------------

	/**
	 * Block sizes surveyBlockSets (one shape for all the channels) may pick.
	 */
	public static final int[] BLOCK_SEARCH_SIZES = {4, 6, 8, 12, 16, 24, 32};

	/**
	 * Runs body(0..n-1) on up to as many threads as there are cores and as the
	 * free heap allows, each task needing about task_bytes. Big images run fewer
	 * tasks at once instead of running out of memory.
	 *
	 * @param n          the number of tasks
	 * @param task_bytes the memory one task needs
	 * @param body       the task
	 */
	static void boundedParallel(int n, long task_bytes, java.util.function.IntConsumer body)
	{
		Runtime rt   = Runtime.getRuntime();
		long    free = rt.maxMemory() - (rt.totalMemory() - rt.freeMemory());
		int     p    = (int) Math.max(1, Math.min(Math.min(n, rt.availableProcessors()), (long) (free * 0.6) / Math.max(1, task_bytes)));
		if(p == 1) { for(int i = 0; i < n; i++) body.accept(i); return; }
		java.util.concurrent.atomic.AtomicInteger next = new java.util.concurrent.atomic.AtomicInteger();
		Throwable[] failed = new Throwable[1];
		Thread[] worker = new Thread[p];
		for(int w = 0; w < p; w++)
		{
			worker[w] = new Thread(() ->
			{
				try { for(int i; (i = next.getAndIncrement()) < n && failed[0] == null; ) body.accept(i); }
				catch(Throwable e) { synchronized(failed) { if(failed[0] == null) failed[0] = e; } }
			});
			worker[w].start();
		}
		for(Thread t : worker)
			try { t.join(); } catch(InterruptedException e) { Thread.currentThread().interrupt(); throw new RuntimeException(e); }
		if(failed[0] instanceof RuntimeException) throw (RuntimeException) failed[0];
		if(failed[0] instanceof Error) throw (Error) failed[0];
		if(failed[0] != null) throw new RuntimeException(failed[0]);
	}

	/**
	 * Each channel's PixelSource, with the channel before it as reference.
	 *
	 * @param channel the channels, in coding order
	 * @param xdim    the width
	 * @param ydim    the height
	 * @param table   the predictor table
	 * @return the PixelSources
	 */
	static PixelSource[] pixelSources(int[][] channel, int xdim, int ydim, PredictorTable table)
	{
		PixelSource[] ps = new PixelSource[channel.length];
		java.util.stream.IntStream.range(0, channel.length).parallel().forEach(i ->
			ps[i] = new PixelSource(channel[i], (i > 0) ? channel[i - 1] : null, xdim, ydim, table));
		return ps;
	}

	// ---- Predictor tables -------------------------------------------------------
	//
	// A block map's ids index PREDICTOR_TABLE: 225 predictors (256 in
	// PREDICTOR_TABLE_256, which older files use), each a
	// primitive or the average of two, (P_i + P_j + 1) >> 1 (see
	// predictor.txt for each one and how they were chosen). Primitives 0-21
	// are blockPredictor's on a, b, c, d; the rest are:
	//
	//   selection   22 Paeth, 23 median(a,b,c), 24 median(a,b,d),
	//               25 median(a,d,a+d-b), 26 switch(a,b), 27 switch(a,b,d)
	//               (blockPredictor);
	//   cross       28-35: a predictor plus the previous channel's error
	//               under it, P + (R - P_R), R the previous channel (coded
	//               first) at the same pixel: P = a, b, d, MED, (a+b)/2,
	//               a+b-c; then MED and (a+b)/2 with half the error. On the
	//               first channel they are just P;
	//   reach       36 2a-aa, 37 2b-bb (aa two to the left, bb two up),
	//               38 GAP (CALIC's gradient-adjusted predictor), 39 GAP with
	//               thresholds an eighth as large (for coarser colour);
	//   clamped     40 a+b-c and 41 a+d-b, clamped to the range of a, b, c, d;
	//   bias        42-45: MED, (a+b)/2, a and b plus their mean error so far
	//               in the pixel's gradient context (as JPEG-LS corrects
	//               bias; 729 contexts, counts halved at 64). The mean is of
	//               every earlier pixel in raster order, whatever predictor
	//               it was coded with, so the decoder keeps the same counts;
	//   least       46 LS: a linear predictor fitted by least squares to the
	//   squares     pixels just above and to the left, refitted at every
	//               pixel (see LeastSquares), its prediction clamped to 2
	//               beyond the range of a, b, c and d; 47 LS wide: the same
	//               fit clamped to 2 beyond the range of all 8 of its
	//               neighbours (they differ only where the fit lands outside
	//               a, b, c and d, at steep edges). Each alone.
	//
	// Beyond a, b, c and d these need the channel's earlier pixels and the
	// previous channel: see PixelSource.

	/**
	 * Where each group of primitives past 21 starts (see the section comment above).
	 */
	public static final int SELECTION_FIRST = 22, CROSS_FIRST = 28, REACH_FIRST = 36, CLAMP_FIRST = 40, BIAS_FIRST = 42, LS_FIRST = 46, LS_WIDE = 47, ALL_PRIMITIVES = 48;

	/**
	 * The least-squares primitive's window: this many rows above, columns
	 * either side, and pixels to the left (see LeastSquares).
	 */
	public static final int LS_RADIUS = 10;

	/**
	 * The groups' names.
	 */
	public static final String[] GROUP_NAMES = {"selection", "cross-channel", "longer reach", "clamped", "bias-corrected", "least squares"};

	/**
	 * Where each group starts, and the end of the last.
	 */
	public static final int[] GROUP_FIRST = {SELECTION_FIRST, CROSS_FIRST, REACH_FIRST, CLAMP_FIRST, BIAS_FIRST, LS_FIRST, ALL_PRIMITIVES};

	/**
	 * Names of primitives 22-46.
	 */
	public static final String[] EXTRA_PRIMITIVE_NAMES = {
		"Paeth", "median(a,b,c)", "median(a,b,d)", "median(a,d,a+d-b)", "switch(a,b)", "switch(a,b,d)",
		"a+(R-Ra)", "b+(R-Rb)", "d+(R-Rd)", "MED+(R-MED_R)", "(a+b)/2+(R-..)", "a+b-c+(R-..)", "MED+(R-MED_R)/2", "(a+b)/2+(R-..)/2",
		"2a-aa", "2b-bb", "GAP", "GAP/8",
		"clamp(a+b-c)", "clamp(a+d-b)",
		"MED+bias", "(a+b)/2+bias", "a+bias", "b+bias",
		"LS", "LS wide"};

	/**
	 * The cross-channel primitives' base predictors (the last two with half the error).
	 */
	private static final int[] CROSS_BASE = {0, 1, 3, 11, 4, 10, 11, 4};   // the last two with half the error

	/**
	 * The bias-corrected primitives' base predictors.
	 */
	private static final int[] BIAS_BASE  = {11, 4, 0, 1};

	/**
	 * A primitive's name.
	 *
	 * @param p a primitive
	 * @return its name
	 */
	public static String primitiveName(int p) { return (p < PRIMITIVES) ? PRIMITIVE_NAMES[p] : EXTRA_PRIMITIVE_NAMES[p - PRIMITIVES]; }

	/**
	 * A table of predictors, each a primitive or the average of two,
	 * (P<sub>i</sub> + P<sub>j</sub> + 1) &gt;&gt; 1.
	 */
	public static final class PredictorTable
	{
		/**
		 * The table's name.
		 */
		public  final String name;
		/**
		 * Each predictor's two primitives (equal for a primitive alone).
		 */
		private final int[]  first, second;   // primitives; first == second for a primitive alone
		/**
		 * How many primitives its entries use: 0 to primitives - 1.
		 */
		public  final int    primitives;      // primitives its entries use: 0..primitives-1

		/**
		 * A table of first[k] and second[k] averaged (or alone, where equal).
		 *
		 * @param name   the table's name
		 * @param first  each predictor's first primitive
		 * @param second each predictor's second primitive
		 */
		PredictorTable(String name, int[] first, int[] second)
		{
			this.name   = name;
			this.first  = first;
			this.second = second;
			int m = 0;
			for(int k = 0; k < first.length; k++) m = Math.max(m, Math.max(first[k], second[k]));
			primitives = m + 1;
		}

		/**
		 * The number of predictors.
		 *
		 * @return the table's size
		 */
		public int size() { return first.length; }
		/**
		 * A predictor's first primitive.
		 *
		 * @param id the predictor
		 * @return its first primitive
		 */
		public int first(int id)  { return first[id]; }
		/**
		 * A predictor's second primitive.
		 *
		 * @param id the predictor
		 * @return its second primitive (its first, if alone)
		 */
		public int second(int id) { return second[id]; }
		/**
		 * Whether a predictor is a primitive alone.
		 *
		 * @param id the predictor
		 * @return whether its two primitives are the same
		 */
		public boolean alone(int id) { return first[id] == second[id]; }
		/**
		 * A predictor's name: "MED", or "avg(MED, a)" for an average.
		 *
		 * @param id the predictor
		 * @return its name
		 */
		public String name(int id)
		{
			return alone(id) ? primitiveName(first[id]) : "avg(" + primitiveName(first[id]) + ", " + primitiveName(second[id]) + ")";
		}
		/**
		 * Whether a predictor is primitive p or an average with p.
		 *
		 * @param id the predictor
		 * @param p  a primitive
		 * @return whether it uses p
		 */
		public boolean uses(int id, int p) { return first[id] == p || second[id] == p; }
		/**
		 * This table without the given primitives (alone or in averages), ids in the
		 * same order.
		 *
		 * @param name       the new table's name
		 * @param primitives the primitives to drop
		 * @return the smaller table
		 */
		public PredictorTable without(String name, int... primitives)
		{
			ArrayList<Integer> keep = new ArrayList<Integer>();
			for(int id = 0; id < size(); id++)
			{
				boolean drop = false;
				for(int p : primitives) drop |= uses(id, p);
				if(!drop) keep.add(id);
			}
			int[] i = new int[keep.size()], j = new int[keep.size()];
			for(int k = 0; k < i.length; k++) { i[k] = first[keep.get(k)]; j[k] = second[keep.get(k)]; }
			return new PredictorTable(name, i, j);
		}
		/**
		 * A predictor's prediction from the primitives' values.
		 *
		 * @param id   the predictor
		 * @param prim the primitives' values at the pixel
		 * @return the prediction
		 */
		int predict(int id, int[] prim) { int i = first[id], j = second[id]; return (i == j) ? prim[i] : (prim[i] + prim[j] + 1) >> 1; }
	}

	/**
	 * A table from pairs of primitives (equal for a primitive alone).
	 *
	 * @param name  the table's name
	 * @param pairs first and second primitive of each predictor, one after another
	 * @return the table
	 */
	public static PredictorTable pairTable(String name, int[] pairs)
	{
		int[] i = new int[pairs.length / 2], j = new int[pairs.length / 2];
		for(int k = 0; k < i.length; k++) { i[k] = pairs[2 * k]; j[k] = pairs[2 * k + 1]; }
		return new PredictorTable(name, i, j);
	}

	/**
	 * PREDICTOR_TABLE_256, as pairs of primitives, 256 of them (ids are one
	 * byte): the table of Block formats 3 to 8 and Delta, Packet and Simple
	 * formats 3 and 4.
	 * Ids 0-254 were chosen by measurement on 7 test images at Colour
	 * Resolution 0 and 3 (see predictor.txt); id 255 is LS, alone:
	 * <ul>
	 * <li>31 primitives. Of the 44 tried, 13 were dropped together because
	 *     removing them (alone and in averages) cost under 0.03%: 10 a+b-c,
	 *     12 (3a+b)/4, 14 (3a+d)/4, 17 a+(b-c)/2, 20 (a+b+d)/3, 22 Paeth, 26 and 27
	 *     the switches, 33 a+b-c+(R-..), 38 GAP, 41 clamp(a+d-b), 43
	 *     (a+b)/2+bias and 44 a+bias (15 and 21 were already out);</li>
	 * <li>224 averages of two of them: the most used (share of blocks) of all 465
	 *     in a survey over every pair, 97% of their use;</li>
	 * <li>255: the least-squares primitive (46), added later.</li>
	 * </ul>
	 */
	static final int[] PREDICTOR_PAIRS_256 = {
		0,0, 1,1, 2,2, 3,3, 4,4, 5,5, 6,6, 7,7, 8,8, 9,9, 11,11, 13,13,
		16,16, 18,18, 19,19, 23,23, 24,24, 25,25, 28,28, 29,29, 30,30, 31,31, 32,32, 34,34,
		35,35, 36,36, 37,37, 39,39, 40,40, 42,42, 45,45, 2,0, 2,1, 3,0, 3,1, 4,0,
		4,3, 5,0, 5,1, 5,2, 5,3, 5,4, 6,0, 6,1, 6,2, 6,3, 6,4, 7,0,
		7,1, 7,4, 7,5, 7,6, 8,0, 8,1, 8,4, 9,4, 9,6, 11,0, 11,1, 11,7,
		11,8, 13,0, 16,0, 16,1, 16,3, 16,4, 16,5, 16,6, 16,7, 16,8, 16,9, 16,11,
		18,0, 18,1, 18,3, 18,4, 18,5, 18,6, 18,7, 18,8, 18,9, 18,13, 18,16, 19,3,
		19,5, 19,6, 19,7, 19,8, 19,18, 23,0, 23,1, 23,2, 23,3, 23,4, 23,5, 23,6,
		24,0, 24,1, 24,4, 24,5, 24,6, 24,7, 24,9, 24,18, 24,23, 25,1, 25,3, 25,5,
		25,6, 28,0, 28,5, 28,6, 28,7, 28,8, 28,16, 29,1, 29,4, 29,5, 29,6, 29,7,
		29,8, 29,16, 29,28, 30,6, 30,7, 30,16, 30,25, 30,28, 30,29, 31,0, 31,5, 31,6,
		31,7, 31,11, 31,16, 32,4, 32,5, 32,6, 32,7, 32,8, 32,9, 32,16, 32,28, 32,29,
		32,30, 34,0, 34,2, 34,3, 34,6, 34,7, 34,8, 34,16, 34,23, 34,25, 34,28, 34,29,
		34,30, 34,31, 35,0, 35,1, 35,2, 35,3, 35,4, 35,5, 35,6, 35,7, 35,8, 35,9,
		35,16, 35,23, 35,24, 35,28, 35,29, 35,30, 35,32, 36,2, 36,4, 36,6, 36,11, 36,16,
		36,25, 37,5, 37,7, 37,8, 37,9, 37,13, 37,16, 37,18, 37,19, 37,25, 39,0, 39,1,
		39,2, 39,3, 39,4, 39,5, 39,6, 39,7, 39,8, 39,9, 39,11, 39,13, 39,16, 39,18,
		39,19, 39,23, 39,24, 39,25, 39,28, 39,29, 39,30, 39,31, 39,32, 39,34, 39,35, 39,36,
		39,37, 40,0, 40,1, 40,3, 40,7, 40,11, 40,16, 40,19, 40,25, 40,34, 40,36, 40,37,
		40,39, 42,0, 42,1, 42,3, 42,4, 42,6, 42,7, 42,16, 42,18, 42,23, 42,29, 42,34,
		42,35, 42,36, 42,37, 42,39, 42,40, 45,0, 45,3, 45,4, 45,5, 45,6, 45,7, 45,16,
		45,23, 45,34, 45,39, 46,46 };

	/**
	 * PREDICTOR_TABLE, as pairs of primitives: 225 of them. Ids 0-223 are the
	 * 224 entries of PREDICTOR_PAIRS_256 that BlockWriter's survey chose at
	 * least once over 19 test images at four settings (Color Resolution 0, 1,
	 * 3 and 4 with Pixel Resolution 0, 1, 4 and 5; 76 surveys), in their old
	 * order, LS last (223); the 32 dropped were never chosen. 224 is the
	 * wide-clamped LS (LS_WIDE): with it, 7 of the images came out 0.35%
	 * smaller lossless (haze 1.1%) and the same at Color Resolution 3. See
	 * predictor.txt for every primitive and entry.
	 */
	static final int[] PREDICTOR_PAIRS = {
		0,0, 1,1, 3,3, 4,4, 5,5, 6,6, 7,7, 8,8, 9,9, 11,11, 13,13, 16,16,
		19,19, 23,23, 24,24, 25,25, 28,28, 29,29, 30,30, 31,31, 32,32, 34,34, 35,35, 39,39,
		40,40, 42,42, 45,45, 2,0, 2,1, 3,0, 4,0, 4,3, 5,0, 5,1, 5,4, 6,0,
		6,1, 6,2, 6,4, 7,0, 7,4, 7,5, 7,6, 8,0, 8,1, 8,4, 9,4, 11,0,
		11,1, 11,7, 11,8, 13,0, 16,0, 16,1, 16,3, 16,4, 16,5, 16,6, 16,7, 16,9,
		16,11, 18,0, 18,1, 18,4, 18,5, 18,6, 18,7, 18,8, 18,9, 18,13, 18,16, 19,3,
		19,18, 23,0, 23,2, 23,4, 23,5, 23,6, 24,0, 24,1, 24,4, 24,5, 24,6, 24,7,
		24,9, 24,18, 24,23, 25,1, 25,3, 25,5, 28,0, 28,5, 28,6, 28,7, 28,8, 28,16,
		29,1, 29,4, 29,5, 29,6, 29,7, 29,8, 29,16, 29,28, 30,6, 30,7, 30,16, 30,25,
		30,28, 30,29, 31,5, 31,6, 31,11, 31,16, 32,4, 32,5, 32,6, 32,7, 32,8, 32,9,
		32,16, 32,28, 32,30, 34,0, 34,2, 34,3, 34,6, 34,7, 34,8, 34,16, 34,23, 34,25,
		34,28, 34,29, 34,30, 34,31, 35,0, 35,1, 35,2, 35,3, 35,4, 35,5, 35,6, 35,7,
		35,8, 35,9, 35,16, 35,23, 35,24, 35,28, 35,29, 35,30, 36,4, 36,11, 36,16, 36,25,
		37,9, 37,13, 37,16, 37,18, 37,19, 37,25, 39,0, 39,1, 39,2, 39,3, 39,4, 39,5,
		39,6, 39,7, 39,8, 39,9, 39,11, 39,13, 39,16, 39,23, 39,24, 39,25, 39,28, 39,29,
		39,30, 39,31, 39,32, 39,34, 39,35, 39,36, 39,37, 40,0, 40,1, 40,3, 40,7, 40,11,
		40,16, 40,19, 40,25, 40,34, 40,36, 40,37, 40,39, 42,0, 42,1, 42,3, 42,4, 42,6,
		42,7, 42,16, 42,18, 42,23, 42,29, 42,34, 42,35, 42,36, 42,37, 42,39, 42,40, 45,0,
		45,4, 45,5, 45,6, 45,7, 45,16, 45,34, 45,39, 46,46, 47,47 };

	/**
	 * The predictor table block maps use.
	 */
	public static final PredictorTable PREDICTOR_TABLE = pairTable("predictors", PREDICTOR_PAIRS);

	/**
	 * The 256-entry table of older files (see PREDICTOR_PAIRS_256), for reading them.
	 */
	public static final PredictorTable PREDICTOR_TABLE_256 = pairTable("predictors (256)", PREDICTOR_PAIRS_256);

	/**
	 * The most predictor ids a block map may carry, and one more than the
	 * largest id (both tables' ids are one byte).
	 */
	public static final int MAX_PREDICTORS = 256;

	/**
	 * Its size, 256.
	 */
	public static final int PREDICTORS = PREDICTOR_TABLE.size();   // 225

	/**
	 * The values a table's primitives are computed from at pixel k: the channel
	 * v (the encoder's source, or the decoder's output so far), the previous
	 * channel ref (null for the first), and the bias counts.
	 * <p>
	 * prepare(k) must be called for each interior pixel in raster order before
	 * its prediction, and commit(k) once v[k] is known; the encoder does this for
	 * the whole channel up front (the constructor), the decoder as it goes.
	 */
	public static final class PixelSource
	{
		/**
		 * The channel, and the previous channel (null for the first).
		 */
		final int[]     v, ref;
		/**
		 * The width.
		 */
		final int       xdim;
		/**
		 * The bias primitives' corrections, [s][k] (|corr| at most the channel's range); null if the table has none.
		 */
		final short[][] corr;          // bias primitives' corrections, [s][k] (|corr| <= the channel's range)
		/**
		 * The bias primitives' error sums and counts, [s][context].
		 */
		final int[][]   sum, count;    // [s][context]
		/**
		 * The least-squares primitive at each pixel; null unless it is needed.
		 */
		short[]         ls;
		/**
		 * The wide-clamped least-squares primitive at each pixel (LS_WIDE); null unless ls is computed.
		 */
		short[]         lsw;
		/**
		 * The decoder's least-squares predictor (see ls); the encoder computes ls up front.
		 */
		final LeastSquares lsq;
		/**
		 * The bias context of the pixel between prepare and commit.
		 */
		int             context;       // the bias context of the pixel between prepare and commit

		/**
		 * For the encoder: v is the whole channel, and the bias corrections are
		 * computed up front.
		 *
		 * @param v     the channel
		 * @param ref   the previous channel, or null for the first
		 * @param xdim  the width
		 * @param ydim  the height
		 * @param table the predictor table (bias counts are kept only if it uses them)
		 */
		public PixelSource(int[] v, int[] ref, int xdim, int ydim, PredictorTable table)
		{
			this(v, ref, xdim, table, false);
			short[][] planes = (table.primitives > LS_FIRST) ? leastSquaresPlanes(v, ref, xdim, ydim) : null;
			ls  = (planes != null) ? planes[0] : null;
			lsw = (planes != null) ? planes[1] : null;
			if(corr != null)
				for(int y = 1; y < ydim; y++)
					for(int x = 1; x < xdim - 1; x++) { prepare(y * xdim + x); commit(y * xdim + x); }
		}

		/**
		 * For the decoder: v fills in as it decodes (call prepare and commit for each
		 * pixel).
		 *
		 * @param v             the output channel, filled in as it decodes
		 * @param ref           the previous channel, or null for the first
		 * @param xdim          the width
		 * @param table         the predictor table
		 * @param least_squares whether to compute the least-squares primitive
		 */
		PixelSource(int[] v, int[] ref, int xdim, PredictorTable table, boolean least_squares)
		{
			this.v = v; this.ref = ref; this.xdim = xdim;
			ls  = least_squares ? new short[v.length] : null;
			lsw = least_squares ? new short[v.length] : null;
			lsq = least_squares ? new LeastSquares(v, ref, xdim) : null;
			boolean bias = table.primitives > BIAS_FIRST;
			corr    = bias ? new short[BIAS_BASE.length][v.length] : null;
			sum     = bias ? new int[BIAS_BASE.length][729] : null;
			count   = bias ? new int[BIAS_BASE.length][729] : null;
		}

		/**
		 * Computes pixel k's bias context and corrections, and the least-squares
		 * primitive (before its prediction).
		 *
		 * @param k an interior pixel, in raster order
		 */
		void prepare(int k) { prepare(k, true); }

		/**
		 * As {@link #prepare(int)}; the decoder fits the least-squares primitive
		 * only where it is used.
		 *
		 * @param k  an interior pixel, in raster order
		 * @param ls whether the pixel's predictor uses the least-squares primitive
		 */
		void prepare(int k, boolean ls)
		{
			if(lsq != null) { this.ls[k] = (short) lsq.next(k, ls); lsw[k] = (short) lsq.wide; }
			if(corr == null) return;
			int a = v[k - 1], b = v[k - xdim], c = v[k - xdim - 1], d = v[k - xdim + 1];
			int t = (gradientLevel(d - b) + 4) * 81 + (gradientLevel(b - c) + 4) * 9 + gradientLevel(c - a) + 4;
			context = t;
			for(int s = 0; s < BIAS_BASE.length; s++)
			{
				int n = count[s][t], e = sum[s][t];
				corr[s][k] = (short) ((n == 0) ? 0 : (e >= 0) ? (e + n / 2) / n : -((-e + n / 2) / n));
			}
		}

		/**
		 * Counts pixel k's errors into the bias statistics (once v[k] is known).
		 *
		 * @param k the pixel just prepared
		 */
		void commit(int k)
		{
			if(corr == null) return;
			int a = v[k - 1], b = v[k - xdim], c = v[k - xdim - 1], d = v[k - xdim + 1], t = context;
			for(int s = 0; s < BIAS_BASE.length; s++)
			{
				sum[s][t] += v[k] - blockPredictor(BIAS_BASE[s], a, b, c, d);
				if(++count[s][t] == 64) { sum[s][t] /= 2; count[s][t] = 32; }
			}
		}

		/**
		 * Primitive q (at least CROSS_FIRST) at interior pixel k.
		 *
		 * @param q the primitive
		 * @param k the pixel
		 * @param a left
		 * @param b above
		 * @param c above-left
		 * @param d above-right
		 * @return its value
		 */
		int extended(int q, int k, int a, int b, int c, int d)
		{
			if(q < REACH_FIRST)
			{
				int s = q - CROSS_FIRST, base = CROSS_BASE[s], p = blockPredictor(base, a, b, c, d);
				if(ref == null) return p;
				int e = ref[k] - blockPredictor(base, ref[k - 1], ref[k - xdim], ref[k - xdim - 1], ref[k - xdim + 1]);
				return (s >= 6) ? p + (e >> 1) : p + e;
			}
			if(q < CLAMP_FIRST)
			{
				int x = k % xdim, y = k / xdim;
				int aa = (x >= 2) ? v[k - 2] : a, bb = (y >= 2) ? v[k - 2 * xdim] : b;
				if(q == REACH_FIRST)     return 2 * a - aa;
				if(q == REACH_FIRST + 1) return 2 * b - bb;
				int nne = (y >= 2) ? v[k - 2 * xdim + 1] : d;
				return (q == REACH_FIRST + 2) ? gap(a, aa, b, bb, c, d, nne, 80, 32, 8) : gap(a, aa, b, bb, c, d, nne, 10, 4, 1);
			}
			if(q < BIAS_FIRST)
			{
				int lo = Math.min(Math.min(a, b), Math.min(c, d)), hi = Math.max(Math.max(a, b), Math.max(c, d));
				int p = (q == CLAMP_FIRST) ? a + b - c : a + d - b;
				return Math.max(lo, Math.min(hi, p));
			}
			if(q == LS_WIDE)  return lsw[k];
			if(q >= LS_FIRST) return ls[k];
			int s = q - BIAS_FIRST;
			return blockPredictor(BIAS_BASE[s], a, b, c, d) + corr[s][k];
		}

		/**
		 * The first np primitives at interior pixel k.
		 *
		 * @param k    the pixel
		 * @param np   how many
		 * @param prim set to their values
		 */
		void primitives(int k, int np, int[] prim)
		{
			int a = v[k - 1], b = v[k - xdim], c = v[k - xdim - 1], d = v[k - xdim + 1];
			int m = Math.min(np, CROSS_FIRST);
			for(int q = 0; q < m; q++) prim[q] = blockPredictor(q, a, b, c, d);
			for(int q = m; q < np; q++) prim[q] = extended(q, k, a, b, c, d);
		}

		/**
		 * One primitive at interior pixel k.
		 *
		 * @param q the primitive
		 * @param k the pixel
		 * @return its value
		 */
		int primitive(int q, int k)
		{
			int a = v[k - 1], b = v[k - xdim], c = v[k - xdim - 1], d = v[k - xdim + 1];
			return (q < CROSS_FIRST) ? blockPredictor(q, a, b, c, d) : extended(q, k, a, b, c, d);
		}

		/**
		 * A table's prediction at interior pixel k.
		 *
		 * @param table the predictor table
		 * @param id    the predictor
		 * @param k     the pixel
		 * @return the prediction
		 */
		int predict(PredictorTable table, int id, int k)
		{
			int i = table.first(id), j = table.second(id), p = primitive(i, k);
			return (i == j) ? p : (p + primitive(j, k) + 1) >> 1;
		}
	}

	/**
	 * JPEG-LS gradient levels: 0, then +-1 for 1-2, +-2 for 3-6, +-3 for 7-20, +-4
	 * beyond.
	 *
	 * @param g a gradient
	 * @return its level, -4 to 4
	 */
	private static int gradientLevel(int g)
	{
		int m = Math.abs(g), l = (m == 0) ? 0 : (m < 3) ? 1 : (m < 7) ? 2 : (m < 21) ? 3 : 4;
		return (g < 0) ? -l : l;
	}

	/**
	 * CALIC's gradient-adjusted predictor.
	 *
	 * @param w   left (a)
	 * @param ww  two to the left
	 * @param n   above (b)
	 * @param nn  two above
	 * @param nw  above-left (c)
	 * @param ne  above-right (d)
	 * @param nne above-right, one row higher
	 * @param t1  the largest threshold (80 for 8-bit values)
	 * @param t2  the middle one (32)
	 * @param t3  the smallest (8)
	 * @return the prediction
	 */
	private static int gap(int w, int ww, int n, int nn, int nw, int ne, int nne, int t1, int t2, int t3)
	{
		int dh = Math.abs(w - ww) + Math.abs(n - nw) + Math.abs(n - ne);
		int dv = Math.abs(w - nw) + Math.abs(n - nn) + Math.abs(ne - nne);
		if(dv - dh > t1) return w;
		if(dh - dv > t1) return n;
		int p = (2 * (w + n) + ne - nw) >> 2;
		if(dv - dh > t2)      p = (p + w) >> 1;
		else if(dv - dh > t3) p = (3 * p + w) >> 2;
		else if(dh - dv > t2) p = (p + n) >> 1;
		else if(dh - dv > t3) p = (3 * p + n) >> 2;
		return p;
	}

	/**
	 * Recently computed least-squares planes, oldest first: {xdim, a copy of the
	 * channel, a copy of the previous channel, the planes (LS, LS wide)}.
	 */
	private static final java.util.LinkedList<Object[]> LS_CACHE = new java.util.LinkedList<Object[]>();

	/**
	 * How many planes LS_CACHE keeps: a channel set's three.
	 */
	private static final int LS_CACHE_SIZE = 3;

	/**
	 * The encoder's least-squares primitives for a channel, LS and LS wide
	 * ({@link LeastSquares#plane}), reused if the same channel (and previous
	 * channel) was just done: the analysis and Apply make several
	 * PixelSources for each channel.
	 *
	 * @param v    the channel
	 * @param ref  the previous channel, or null for the first
	 * @param xdim the width
	 * @param ydim the height
	 * @return LS and LS wide at each interior pixel
	 */
	static short[][] leastSquaresPlanes(int[] v, int[] ref, int xdim, int ydim)
	{
		synchronized(LS_CACHE)
		{
			for(Object[] e : LS_CACHE)
				if((Integer) e[0] == xdim && Arrays.equals((int[]) e[1], v) && Arrays.equals((int[]) e[2], ref)) return (short[][]) e[3];
		}
		short[][] plane = LeastSquares.plane(v, ref, xdim, ydim);
		synchronized(LS_CACHE)
		{
			LS_CACHE.addLast(new Object[] {xdim, v.clone(), (ref == null) ? null : ref.clone(), plane});
			while(LS_CACHE.size() > LS_CACHE_SIZE) LS_CACHE.removeFirst();
		}
		return plane;
	}

	/**
	 * The least-squares primitive (LS_FIRST): at each pixel, the linear
	 * predictor (with a constant term) that best fits the pixels in a causal
	 * window -- the LS_RADIUS rows above, LS_RADIUS columns either side, and
	 * the LS_RADIUS pixels to the left -- from 8 neighbours (a, b, c, d, two
	 * to the left, two up, two to the right one up, one to the right two up)
	 * and, after the first channel, the previous channel at the pixel, to its
	 * left and above. The window's sums are exact integers, kept as it slides
	 * (each pixel's products are computed once, and kept for the LS_RADIUS + 1
	 * rows that use them); the fit (ridge 1, solved by L D L^T) is done in a
	 * fixed order, so the decoder and the Python version get the same values.
	 * The prediction is clamped to 2 beyond the range of a, b, c, d and
	 * rounded (and, for LS_WIDE, kept in wide: clamped to 2 beyond the range
	 * of all 8 spatial neighbours); a until the window has twice as many
	 * pixels as unknowns (or if the fit fails).
	 * <p>
	 * next(k) must be called for every interior pixel in raster order, once
	 * the pixels before k are known, from row 1 or from a row given to start.
	 * Because the sums are exact, the encoder can compute bands of rows
	 * independently (see plane) and get the decoder's values.
	 */
	static final class LeastSquares
	{
		/**
		 * The channel (filled in as it decodes), and the previous channel (null for the first).
		 */
		final int[]  v, ref;
		/**
		 * The width, the number of features, their augmented length (features, 1,
		 * value) and the number of products of the augmented vector.
		 */
		final int    xdim, nf, m, nt;
		/**
		 * Per column, the window rows' sums of products; the window's sums; their
		 * current-row part.
		 */
		final long[] col, win, cur;
		/**
		 * The products of each pixel of the last LS_RADIUS + 1 rows, by row modulo
		 * LS_RADIUS + 1 (a product is at most about 520 * 520).
		 */
		final int[]  ring;
		/**
		 * The augmented vector of the pixel being added.
		 */
		final long[] z;
		/**
		 * The normal equations' matrix.
		 */
		final double[][] a;
		/**
		 * Their right-hand side, the solution, and D of L D L^T.
		 */
		final double[]   b, coef, dg;
		/**
		 * The row of the last pixel.
		 */
		int row = -1;
		/**
		 * The last next's prediction clamped to 2 beyond the range of all 8
		 * neighbours (LS_WIDE) rather than a, b, c and d.
		 */
		int wide;

		/**
		 * The predictor for a channel.
		 *
		 * @param v    the channel
		 * @param ref  the previous channel, or null for the first
		 * @param xdim the width
		 */
		LeastSquares(int[] v, int[] ref, int xdim)
		{
			this.v = v; this.ref = ref; this.xdim = xdim;
			nf   = (ref == null) ? 8 : 11;
			m    = nf + 2;
			nt   = m * (m + 1) / 2;
			col  = new long[xdim * nt];
			win  = new long[nt];
			cur  = new long[nt];
			ring = new int[(LS_RADIUS + 1) * xdim * nt];
			z    = new long[m];
			a    = new double[nf + 1][nf + 1];
			b    = new double[nf + 1];
			coef = new double[nf + 1];
			dg   = new double[nf + 1];
		}

		/**
		 * The encoder's least-squares primitive for a whole channel, in bands of
		 * rows computed in parallel.
		 *
		 * @param v    the channel
		 * @param ref  the previous channel, or null for the first
		 * @param xdim the width
		 * @param ydim the height
		 * @return LS and LS wide at each interior pixel (0 elsewhere)
		 */
		static short[][] plane(int[] v, int[] ref, int xdim, int ydim)
		{
			short[] out = new short[v.length], wide = new short[v.length];
			int threads = Runtime.getRuntime().availableProcessors();
			int band = Math.max(4 * (LS_RADIUS + 1), (ydim - 1 + threads - 1) / threads);
			int bands = (ydim - 1 + band - 1) / band;
			java.util.stream.IntStream.range(0, bands).parallel().forEach(t ->
			{
				int y0 = 1 + t * band, y1 = Math.min(ydim, y0 + band);
				LeastSquares ls = new LeastSquares(v, ref, xdim);
				ls.start(y0);
				for(int y = y0; y < y1; y++)
					for(int x = 1; x < xdim - 1; x++) { out[y * xdim + x] = (short) ls.next(y * xdim + x); wide[y * xdim + x] = (short) ls.wide; }
			});
			return new short[][] {out, wide};
		}

		/**
		 * Sets up the window as if the rows before y0 had been gone through.
		 *
		 * @param y0 the first row next will be called for
		 */
		void start(int y0)
		{
			int r = LS_RADIUS;
			for(int ty = Math.max(1, y0 - 1 - r); ty <= y0 - 1; ty++)
				for(int tx = 1; tx < xdim - 1; tx++)
				{
					products(ty, tx);
					if(ty <= y0 - 2) for(int t = 0, c = tx * nt, s = slot(ty, tx); t < nt; t++) col[c + t] += ring[s + t];
				}
			row = y0 - 1;
		}

		/**
		 * Where pixel (y, x)'s products are kept in ring.
		 *
		 * @param y the row
		 * @param x the column
		 * @return the offset
		 */
		private int slot(int y, int x) { return ((y % (LS_RADIUS + 1)) * xdim + x) * nt; }

		/**
		 * The value at (y, x), clamped to the image.
		 *
		 * @param y the row
		 * @param x the column
		 * @return the value
		 */
		private int at(int y, int x)
		{
			if(y < 0) y = 0;
			if(x < 0) x = 0; else if(x > xdim - 1) x = xdim - 1;
			return v[y * xdim + x];
		}

		/**
		 * Sets z to the features of pixel (y, x), 1, and its value.
		 *
		 * @param y the row
		 * @param x the column
		 */
		private void features(int y, int x)
		{
			z[0] = at(y, x - 1); z[1] = at(y - 1, x); z[2] = at(y - 1, x - 1); z[3] = at(y - 1, x + 1);
			z[4] = at(y, x - 2); z[5] = at(y - 2, x); z[6] = at(y - 1, x + 2); z[7] = at(y - 2, x + 1);
			if(ref != null)
			{
				int k = y * xdim + x;
				z[8] = ref[k]; z[9] = ref[k - 1]; z[10] = ref[k - xdim];
			}
			z[nf] = 1;
			z[nf + 1] = v[y * xdim + x];
		}

		/**
		 * Computes pixel (y, x)'s products into its slot in ring.
		 *
		 * @param y the row
		 * @param x the column
		 */
		private void products(int y, int x)
		{
			features(y, x);
			int t = slot(y, x);
			for(int i = 0; i < m; i++)
			{
				long zi = z[i];
				for(int j = i; j < m; j++) ring[t++] = (int) (zi * z[j]);
			}
		}

		/**
		 * The primitive at interior pixel k.
		 *
		 * @param k the pixel
		 * @return the prediction
		 */
		int next(int k) { return next(k, true); }

		/**
		 * Moves the window to interior pixel k, and fits there if asked: the
		 * decoder fits only where a block uses the primitive.
		 *
		 * @param k   the pixel
		 * @param fit whether to fit
		 * @return the prediction (0 without a fit)
		 */
		int next(int k, boolean fit)
		{
			int y = k / xdim, x = k % xdim, r = LS_RADIUS;
			if(y != row)
			{
				// The window's rows become y - r .. y - 1 (from 1): add row y - 1
				// (its last pixel's products first), drop row y - 1 - r.
				if(y - 1 >= 1)
				{
					products(y - 1, xdim - 2);
					for(int tx = 1; tx < xdim - 1; tx++) for(int t = 0, c = tx * nt, s = slot(y - 1, tx); t < nt; t++) col[c + t] += ring[s + t];
				}
				if(y - 1 - r >= 1)
					for(int tx = 1; tx < xdim - 1; tx++) for(int t = 0, c = tx * nt, s = slot(y - 1 - r, tx); t < nt; t++) col[c + t] -= ring[s + t];
				row = y;
				Arrays.fill(win, 0); Arrays.fill(cur, 0);
				for(int tx = 1; tx <= Math.min(xdim - 2, 1 + r); tx++) for(int t = 0; t < nt; t++) win[t] += col[tx * nt + t];
			}
			else
			{
				if(x + r <= xdim - 2) for(int t = 0, c = (x + r) * nt; t < nt; t++) win[t] += col[c + t];
				if(x - r - 1 >= 1)    for(int t = 0, c = (x - r - 1) * nt; t < nt; t++) win[t] -= col[c + t];
				products(y, x - 1);
				for(int t = 0, s = slot(y, x - 1); t < nt; t++) cur[t] += ring[s + t];
				if(x - 1 - r >= 1) for(int t = 0, s = slot(y, x - 1 - r); t < nt; t++) cur[t] -= ring[s + t];
			}
			if(!fit) { wide = 0; return 0; }

			int n = nf + 1;
			int pa = at(y, x - 1), pb = at(y - 1, x), pc = at(y - 1, x - 1), pd = at(y - 1, x + 1);
			// Products of (i, j), i <= j < m: index i * m - i * (i - 1) / 2 + (j - i).
			long count = win[nf * m - nf * (nf - 1) / 2] + cur[nf * m - nf * (nf - 1) / 2];
			if(count < 2 * n) { wide = pa; return pa; }
			// The normal equations' lower triangle (ridge 1 on the diagonal), then
			// L D L^T: L unit lower triangular (in a's lower triangle), D in dg.
			for(int i = 0; i < n; i++)
			{
				int base = i * m - i * (i - 1) / 2;
				for(int j = i; j < n; j++) a[j][i] = (double) (win[base + j - i] + cur[base + j - i]);
				b[i] = (double) (win[base + m - 1 - i] + cur[base + m - 1 - i]);
				a[i][i] += 1.0;
			}
			for(int j = 0; j < n; j++)
			{
				double[] aj = a[j];
				double s = aj[j];
				for(int q = 0; q < j; q++) s -= aj[q] * aj[q] * dg[q];
				if(s < 1e-9) { wide = pa; return pa; }
				dg[j] = s;
				for(int i = j + 1; i < n; i++)
				{
					double[] ai = a[i];
					double t = ai[j];
					for(int q = 0; q < j; q++) t -= ai[q] * aj[q] * dg[q];
					ai[j] = t / s;
				}
			}
			for(int i = 0; i < n; i++)
			{
				double s = b[i];
				for(int q = 0; q < i; q++) s -= a[i][q] * coef[q];
				coef[i] = s;
			}
			for(int i = 0; i < n; i++) coef[i] /= dg[i];
			for(int i = n - 1; i >= 0; i--)
			{
				double s = coef[i];
				for(int q = i + 1; q < n; q++) s -= a[q][i] * coef[q];
				coef[i] = s;
			}
			features(y, x);
			double p = 0;
			for(int i = 0; i < n; i++) p += coef[i] * z[i];
			int lo = Math.min(Math.min(pa, pb), Math.min(pc, pd)), hi = Math.max(Math.max(pa, pb), Math.max(pc, pd));
			int wlo = lo, whi = hi;
			for(int i = 4; i < 8; i++) { wlo = (int) Math.min(wlo, z[i]); whi = (int) Math.max(whi, z[i]); }
			double w = Math.max(wlo - 2, Math.min(whi + 2, p));
			wide = (int) Math.floor(w + 0.5);
			if(p < lo - 2) p = lo - 2;
			if(p > hi + 2) p = hi + 2;
			return (int) Math.floor(p + 0.5);
		}
	}

	// ---- Surveying predictor sets ----------------------------------------------
	//
	// Any of the PREDICTORS can be in a block map's set, and each channel has
	// its own set, chosen from the whole table: the survey ranks predictors
	// by an estimate of their coded size rather than by |delta|:
	//
	//   1. A cost per delta value (DeltaCost): -log2 of its frequency in the
	//      reference deltas -- each block's best of the whole table by sum of
	//      |delta| (tableReferenceDeltas) -- per coding context (getContext,
	//      with the deltas already chosen for the channels before as
	//      context). In 1/256 bits.
	//   2. For a block size, each predictor's estimated bits in each block
	//      (blockCostTable): the sum of the costs of its deltas.
	//   3. Per channel, predictors in greedy order (greedyCostOrder): first
	//      the one with the smallest total, then each time the one that
	//      saves the most estimated bits when every block uses its cheapest
	//      predictor so far -- less what it adds to the map (the entropy of
	//      the block counts, and a byte for its id). The order stops when no
	//      predictor saves anything.
	//   4. The real coded size -- Context-coded deltas plus the map
	//      (writeMap) -- for the first n of that order, at n = SURVEY_COUNTS
	//      and the whole order, until two in a row are larger than the best.
	//      Blocks use their cheapest predictor by the estimate
	//      (assignByCost), not the smallest |delta|.
	//   5. surveyChannels (what the writers use): each channel in turn at
	//      every size in SURVEY_CHANNEL_SIZES, keeping the one that codes it
	//      smallest, so each channel has its own block size (a map's context
	//      lines up the previous channel's map: alignMap). surveyBlockSets
	//      instead finds one size for all the channels: 4, 8 and 16, then the
	//      untried sizes next to the best.
	//
	// getBlockDeltasByCost makes the same deltas and maps from the surveyed
	// sizes and ids, so a writer can redo them without surveying again;
	// surveyedBlockMaps keeps the last survey, for writers that ask for the
	// same channels' block maps several times.

	/**
	 * The prefix lengths whose real coded size the survey measures.
	 */
	public static final int[] SURVEY_COUNTS = {1, 2, 3, 4, 5, 6, 8, 10, 12, 16, 20, 24, 32, 40, 48, 64, 80, 96, 128, 160, 192, 255};

	/**
	 * A predictor id in the map header, in 1/256 bits.
	 */
	static final int SURVEY_ID_COST       = 8 * 256;    // a predictor id in the map header, 1/256 bits

	/**
	 * Blocks the greedy order is found on, at most.
	 */
	static final int SURVEY_SAMPLE_BLOCKS = 1 << 17;    // blocks the greedy order is found on, at most

	/**
	 * floor(x + 0.5) of 256 log<sub>2</sub>(r), as the survey's costs are rounded.
	 *
	 * @param r a ratio
	 * @return its cost, 1/256 bits
	 */
	private static int cost256(double r) { return (int) Math.floor(256 * Math.log(r) / Math.log(2) + 0.5); }

	/**
	 * Estimated cost of each delta value of a channel in each coding context,
	 * from the reference deltas (1/256 bits; adaptive counts start at 1 and go up
	 * by 32, as the Context coder's do).
	 */
	public static final class DeltaCost
	{
		/**
		 * The smallest delta modelled, and the number of delta values.
		 */
		final int     lo, range;
		/**
		 * Each delta's cost in each context, [context][delta - lo], 1/256 bits.
		 */
		final int[][] cost;      // [context][delta - lo]
		/**
		 * Each pixel's context.
		 */
		final int[]   context;   // each pixel's context

		/**
		 * The cost model for a channel.
		 *
		 * @param src      the channel (for the range of deltas)
		 * @param ref      reference deltas, whose counts give the costs
		 * @param previous the deltas of the channels before (for the contexts)
		 * @param xdim     the width
		 * @param ydim     the height
		 */
		public DeltaCost(int[] src, int[] ref, int[][] previous, int xdim, int ydim)
		{
			int min = Integer.MAX_VALUE, max = Integer.MIN_VALUE;
			for(int v : src) { if(v < min) min = v; if(v > max) max = v; }
			int span = max - min;
			lo    = -2 * span - 2;
			range = 4 * span + 5;
			context = new int[src.length];
			long[][] count = new long[CONTEXTS][range];
			long[]   total = new long[CONTEXTS];
			for(int y = 1; y < ydim; y++)
				for(int x = 1; x < xdim - 1; x++)
				{
					int k = y * xdim + x, c = getContext(ref, previous, k, xdim);
					context[k] = c;
					count[c][index(ref[k])]++;
					total[c]++;
				}
			cost = new int[CONTEXTS][range];
			for(int c = 0; c < CONTEXTS; c++)
				for(int v = 0; v < range; v++)
					cost[c][v] = cost256((32.0 * total[c] + range) / (32.0 * count[c][v] + 1));
		}

		/**
		 * A delta's index in cost, clamped to the range.
		 *
		 * @param delta a delta
		 * @return delta - lo, clamped
		 */
		int index(int delta) { return Math.max(0, Math.min(range - 1, delta - lo)); }
	}

	/**
	 * Estimated bits (1/256) of each predictor in each block, as [block * n + q]
	 * for the ids given (n of them), or for every id (ids null, n = the table's
	 * size); blocks row by row as in the map.
	 *
	 * @param ps    the channel, and what its primitives need
	 * @param ydim  the height
	 * @param block the block shape
	 * @param dc    the cost model
	 * @param table the predictor table
	 * @param ids   the ids, or null for all
	 * @return the estimates
	 */
	public static int[] blockCostTable(PixelSource ps, int ydim, int block, DeltaCost dc, PredictorTable table, int[] ids)
	{
		int bh = blocksDown(ydim, block);
		int[] rows = new int[bh];
		for(int by = 0; by < bh; by++) rows[by] = by;
		return blockCostRows(ps, ydim, block, dc, table, ids, rows);
	}

	/**
	 * Which of the ids predict alike: on the first channel (no ref) the
	 * cross-channel primitives are their base predictors (CROSS_BASE), so
	 * entries that differ only in that predict the same at every pixel.
	 * same[q] is the first position with the same predictions as pid[q]
	 * (q itself if none earlier); with a ref every position is its own. The
	 * survey computes only the first of each group: its sums and estimates
	 * are the others', and a strict-less argmin over the first of each group
	 * picks what it would over all of them.
	 *
	 * @param ps    the channel
	 * @param table the predictor table
	 * @param pid   the ids
	 * @return each position's first alike
	 */
	static int[] samePrediction(PixelSource ps, PredictorTable table, int[] pid)
	{
		int   n = pid.length, np = table.primitives;
		int[] same = new int[n];
		int[] seen = new int[np * np];
		Arrays.fill(seen, -1);
		for(int q = 0; q < n; q++)
		{
			if(ps.ref != null) { same[q] = q; continue; }
			int i = firstChannelPrimitive(table.first(pid[q])), j = firstChannelPrimitive(table.second(pid[q]));
			int key = Math.min(i, j) * np + Math.max(i, j);
			if(seen[key] < 0) seen[key] = q;
			same[q] = seen[key];
		}
		return same;
	}

	/**
	 * The positions that are the first of their group (see samePrediction).
	 *
	 * @param same each position's first alike
	 * @return those positions, increasing
	 */
	static int[] distinctPositions(int[] same)
	{
		int m = 0;
		for(int q = 0; q < same.length; q++) if(same[q] == q) m++;
		int[] at = new int[m];
		m = 0;
		for(int q = 0; q < same.length; q++) if(same[q] == q) at[m++] = q;
		return at;
	}

	/**
	 * What a primitive is on the first channel: a cross-channel primitive's
	 * base predictor, any other primitive itself.
	 *
	 * @param q the primitive
	 * @return the primitive it equals without a ref
	 */
	private static int firstChannelPrimitive(int q)
	{
		return (q >= CROSS_FIRST && q < REACH_FIRST) ? CROSS_BASE[q - CROSS_FIRST] : q;
	}

	/**
	 * As {@link #blockCostTable}, for the given block rows only:
	 * est[(r * bw + bx) * n + q] for rows[r].
	 *
	 * @param ps    the channel, and what its primitives need
	 * @param ydim  the height
	 * @param block the block shape
	 * @param dc    the cost model
	 * @param table the predictor table
	 * @param ids   the ids, or null for all
	 * @param rows  the block rows
	 * @return the estimates
	 */
	static int[] blockCostRows(PixelSource ps, int ydim, int block, DeltaCost dc, PredictorTable table, int[] ids, int[] rows)
	{
		int   n  = (ids == null) ? table.size() : ids.length;
		int[] pid = new int[n];
		for(int q = 0; q < n; q++) pid[q] = (ids == null) ? q : ids[q];
		int   bw = blocksAcross(ps.xdim, block);
		int[] est = new int[rows.length * bw * n];
		java.util.stream.IntStream.range(0, rows.length).parallel().forEach(r ->
			blockCostRow(ps, ydim, block, dc, table, pid, rows[r], est, r * bw * n, new int[table.primitives]));
		return est;
	}

	/**
	 * One block row's estimates, [bx * n + q] from offset.
	 *
	 * @param ps     the channel, and what its primitives need
	 * @param ydim   the height
	 * @param block  the block shape
	 * @param dc     the cost model
	 * @param table  the predictor table
	 * @param pid    the ids
	 * @param by     the block row
	 * @param est    the estimates, added to
	 * @param offset where this row's start in est
	 * @param prim   scratch space for the primitives
	 */
	private static void blockCostRow(PixelSource ps, int ydim, int block, DeltaCost dc, PredictorTable table, int[] pid, int by, int[] est, int offset, int[] prim)
	{
		int[] src = ps.v;
		int   xdim = ps.xdim, n = pid.length, np = table.primitives;
		int   bh_ = blockHeight(block), bw_ = blockWidth(block, xdim);
		int   y0 = 1 + by * bh_, y1 = Math.min(ydim, y0 + bh_);
		int[] same = samePrediction(ps, table, pid), at = distinctPositions(same);
		if(at.length == n)
		{
			for(int y = y0; y < y1; y++)
				for(int x = 1; x < xdim - 1; x++)
				{
					int   k = y * xdim + x, s = src[k];
					int[] cc = dc.cost[dc.context[k]];
					ps.primitives(k, np, prim);
					int base = offset + (x - 1) / bw_ * n;
					for(int q = 0; q < n; q++) est[base + q] += cc[dc.index(s - table.predict(pid[q], prim))];
				}
			return;
		}
		// The first of each group only, then each position copies its group's.
		int   m = at.length, bw = blocksAcross(xdim, block);
		int[] one = new int[m], slot = new int[n], part = new int[bw * m];
		for(int t = 0; t < m; t++) { one[t] = pid[at[t]]; slot[at[t]] = t; }
		for(int y = y0; y < y1; y++)
			for(int x = 1; x < xdim - 1; x++)
			{
				int   k = y * xdim + x, s = src[k];
				int[] cc = dc.cost[dc.context[k]];
				ps.primitives(k, np, prim);
				int base = (x - 1) / bw_ * m;
				for(int t = 0; t < m; t++) part[base + t] += cc[dc.index(s - table.predict(one[t], prim))];
			}
		for(int bx = 0; bx < bw; bx++)
			for(int q = 0; q < n; q++) est[offset + bx * n + q] += part[bx * m + slot[same[q]]];
	}

	/**
	 * Block maps for prefixes of ids (the first counts[c] of them), each block
	 * taking the first id of the prefix with the smallest estimate, as
	 * assignByCost does. One pass over the block rows, holding one row's
	 * estimates at a time, so large images don't need the whole table.
	 *
	 * @param ps     the channel, and what its primitives need
	 * @param ydim   the height
	 * @param block  the block shape
	 * @param dc     the cost model
	 * @param table  the predictor table
	 * @param ids    the ids, in order
	 * @param counts the prefix lengths, increasing
	 * @return one map per prefix
	 */
	static byte[][] prefixMaps(PixelSource ps, int ydim, int block, DeltaCost dc, PredictorTable table, int[] ids, int[] counts)
	{
		int bw = blocksAcross(ps.xdim, block), bh = blocksDown(ydim, block), n = ids.length;
		byte[][] map = new byte[counts.length][];
		for(int c = 0; c < counts.length; c++)
		{
			int m = counts[c];
			map[c] = new byte[2 + m + bw * bh];
			map[c][0] = (byte) block; map[c][1] = (byte) m;
			for(int q = 0; q < m; q++) map[c][2 + q] = (byte) ids[q];
		}
		java.util.stream.IntStream.range(0, bh).parallel().forEach(by ->
		{
			int[] est = new int[bw * n];
			blockCostRow(ps, ydim, block, dc, table, ids, by, est, 0, new int[table.primitives]);
			for(int bx = 0; bx < bw; bx++)
			{
				int best = 0, c = 0;
				for(int q = 0; q < n && c < counts.length; q++)
				{
					if(est[bx * n + q] < est[bx * n + best]) best = q;
					while(c < counts.length && counts[c] == q + 1) { map[c][2 + counts[c] + by * bw + bx] = (byte) best; c++; }
				}
			}
		});
		return map;
	}

	/**
	 * round(256 c log<sub>2</sub> c) for c = 0..n: n log<sub>2</sub> n - the sum of
	 * c log<sub>2</sub> c is the map's size (bits) when n blocks are split into
	 * groups of size c.
	 *
	 * @param n the largest count
	 * @return the table
	 */
	private static long[] nlog2nTable(int n)
	{
		long[] t = new long[n + 1];
		for(int c = 2; c <= n; c++) t[c] = (long) Math.floor(256.0 * c * Math.log(c) / Math.log(2) + 0.5);
		return t;
	}

	/**
	 * Predictor ids in greedy order over a blockCostTable of every id (see the
	 * survey section comment); stops when no predictor saves anything.
	 *
	 * @param est    the estimates
	 * @param blocks the number of blocks
	 * @return the ids, in order
	 */
	public static int[] greedyCostOrder(int[] est, int blocks)
	{
		return greedyCostOrder(est, blocks, PREDICTORS);
	}

	/**
	 * As {@link #greedyCostOrder(int[], int)}, over a table of P predictors.
	 *
	 * @param est    the estimates
	 * @param blocks the number of blocks
	 * @param P      the number of predictors in est
	 * @return the ids, in order
	 */
	public static int[] greedyCostOrder(int[] est, int blocks, final int P)
	{
		return greedyCostOrder(est, blocks, P, SURVEY_ID_COST);
	}

	/**
	 * As {@link #greedyCostOrder(int[], int, int)}, with the cost of an id in the
	 * map header given (scaled down when est covers only a sample of the blocks).
	 *
	 * @param est     the estimates
	 * @param blocks  the number of blocks in est
	 * @param P       the number of predictors in est
	 * @param id_cost the cost of an id, 1/256 bits
	 * @return the ids, in order
	 */
	public static int[] greedyCostOrder(int[] est, int blocks, final int P, final long id_cost)
	{
		long[] NL = nlog2nTable(blocks);
		long[] total = new long[P];
		for(int b = 0; b < blocks; b++) for(int p = 0; p < P; p++) total[p] += est[b * P + p];
		int first = 0;
		for(int p = 1; p < P; p++) if(total[p] < total[first]) first = p;

		ArrayList<Integer> order = new ArrayList<Integer>();
		order.add(first);
		boolean[] chosen = new boolean[P]; chosen[first] = true;
		int[]  cur = new int[blocks], assign = new int[blocks];
		for(int b = 0; b < blocks; b++) cur[b] = est[b * P + first];
		long[] count = new long[P]; count[0] = blocks;
		while(order.size() < Math.min(P, 255))   // a set has at most 255 ids
		{
			final int n = order.size();
			long map = NL[blocks];
			for(int q = 0; q < n; q++) map -= NL[(int) count[q]];
			final long map_old = map;
			long[] gain = new long[P];
			java.util.stream.IntStream.range(0, P).parallel().forEach(p ->
			{
				if(chosen[p]) return;
				long[] moved_from = new long[n];
				long   save = 0;
				int    moved = 0;
				for(int b = 0; b < blocks; b++)
				{
					int e = est[b * P + p];
					if(e < cur[b]) { save += cur[b] - e; moved_from[assign[b]]++; moved++; }
				}
				long map_new = NL[blocks] - NL[moved];
				for(int q = 0; q < n; q++) map_new -= NL[(int) (count[q] - moved_from[q])];
				gain[p] = save - (map_new - map_old) - id_cost;
			});
			int best = -1;
			for(int p = 0; p < P; p++) if(!chosen[p] && (best < 0 || gain[p] > gain[best])) best = p;
			if(gain[best] <= 0) break;
			chosen[best] = true;
			order.add(best);
			for(int b = 0; b < blocks; b++)
			{
				int e = est[b * P + best];
				if(e < cur[b]) { cur[b] = e; count[assign[b]]--; assign[b] = n; count[n]++; }
			}
		}
		int[] ids = new int[order.size()];
		for(int k = 0; k < ids.length; k++) ids[k] = order.get(k);
		return ids;
	}

	/**
	 * A block map for the ids: each block uses the first of them with the
	 * smallest estimate.
	 *
	 * @param est    a blockCostTable: of every id of the table (all), or of the ids
	 * @param all    whether est covers every id
	 * @param stride the number of ids per block in est
	 * @param ids    the ids
	 * @param xdim   the width
	 * @param ydim   the height
	 * @param block  the block shape
	 * @return the map
	 */
	private static byte[] assignByCost(int[] est, boolean all, int stride, int[] ids, int xdim, int ydim, int block)
	{
		int    bw = blocksAcross(xdim, block), bh = blocksDown(ydim, block), n = ids.length;
		int    head = 2 + n;
		byte[] map = new byte[head + bw * bh];
		map[0] = (byte) block; map[1] = (byte) n;
		for(int q = 0; q < n; q++) map[2 + q] = (byte) ids[q];
		for(int b = 0; b < bw * bh; b++)
		{
			int best = 0;
			for(int q = 1; q < n; q++)
				if(est[b * stride + (all ? ids[q] : q)] < est[b * stride + (all ? ids[best] : best)]) best = q;
			map[head + b] = (byte) best;
		}
		return map;
	}

	/**
	 * The block deltas for a given block map (its shape, ids and choices); the
	 * same list as getBlockDeltasFromValues.
	 *
	 * @param ps    the channel, and what its primitives need
	 * @param ydim  the height
	 * @param map   the block map
	 * @param table the table its ids index
	 * @return [sum of |delta| (Integer), deltas (int[]), map (byte[]), the first
	 *         value (Integer)]
	 */
	public static ArrayList getBlockDeltasFromMap(PixelSource ps, int ydim, byte[] map, PredictorTable table)
	{
		return getBlockDeltasFromMap(ps, ydim, map, table, getMapIds(map));
	}

	/**
	 * As {@link #getBlockDeltasFromMap(PixelSource, int, byte[], PredictorTable)},
	 * with the set's ids given (tables over 256 ids, for research, can't be read
	 * back from the map's one-byte ids). Rows in parallel.
	 *
	 * @param ps    the channel, and what its primitives need
	 * @param ydim  the height
	 * @param map   the block map
	 * @param table the table the ids index
	 * @param ids   the ids
	 * @return [sum of |delta| (Integer), deltas (int[]), map (byte[]), the first
	 *         value (Integer)]
	 */
	static ArrayList getBlockDeltasFromMap(PixelSource ps, int ydim, byte[] map, PredictorTable table, int[] ids)
	{
		int[] src = ps.v;
		int   xdim = ps.xdim;
		int   block = map[0] & 0xFF, bw = blocksAcross(xdim, block), head = mapHeaderLength(map);
		int   bh_ = blockHeight(block), bw_ = blockWidth(block, xdim);
		int[] dst = new int[xdim * ydim];
		blockEdges(src, dst, xdim, ydim);
		// Rows in parallel: the predictions read only the source (and bias
		// corrections computed up front).
		java.util.stream.IntStream.range(1, ydim).parallel().forEach(y ->
		{
			int row = head + (y - 1) / bh_ * bw;
			for(int x = 1; x < xdim - 1; x++)
			{
				int k = y * xdim + x;
				dst[k] = src[k] - ps.predict(table, ids[map[row + (x - 1) / bw_] & 0xFF], k);
			}
		});
		int sum = 0;
		for(int v : dst) sum += Math.abs(v);
		ArrayList result = new ArrayList();
		result.add(sum);
		result.add(dst);
		result.add(map);
		result.add(src[0]);
		return result;
	}

	/**
	 * The cost model for a channel at a block shape: reference deltas from the
	 * whole table ({@link #tableReferenceDeltas}), context from the chosen
	 * deltas of the channels before.
	 *
	 * @param ps            the channel, and what its primitives need
	 * @param ydim          the height
	 * @param block         the block shape
	 * @param chosen_before the deltas chosen for the channels before
	 * @param table         the predictor table
	 * @return the cost model
	 */
	static DeltaCost channelCost(PixelSource ps, int ydim, int block, int[][] chosen_before, PredictorTable table)
	{
		return new DeltaCost(ps.v, tableReferenceDeltas(ps, ydim, block, table), chosen_before, ps.xdim, ydim);
	}

	/**
	 * The cost model for a channel at a block shape, from reference deltas
	 * already found.
	 *
	 * @param ps            the channel
	 * @param ydim          the height
	 * @param ref           the reference deltas
	 * @param chosen_before the deltas chosen for the channels before
	 * @return the cost model
	 */
	static DeltaCost channelCost(PixelSource ps, int ydim, int[] ref, int[][] chosen_before)
	{
		return new DeltaCost(ps.v, ref, chosen_before, ps.xdim, ydim);
	}

	/**
	 * Each channel's block deltas for its predictor ids, blocks assigned by
	 * estimated cost as the survey does. Channels in order: each one's costs use
	 * the deltas of the ones before.
	 *
	 * @param channel the three channels, in coding order
	 * @param xdim    the width
	 * @param ydim    the height
	 * @param block   the block shape
	 * @param ids     each channel's ids
	 * @return each channel's list, as getBlockDeltasFromValues gives it
	 */
	public static ArrayList[] getBlockDeltasByCost(int[][] channel, int xdim, int ydim, int block, int[][] ids)
	{
		return getBlockDeltasByCost(channel, xdim, ydim, block, ids, PREDICTOR_TABLE);
	}

	/**
	 * As {@link #getBlockDeltasByCost(int[][], int, int, int, int[][])}, with the
	 * ids in the given table.
	 *
	 * @param channel the three channels, in coding order
	 * @param xdim    the width
	 * @param ydim    the height
	 * @param block   the block shape
	 * @param ids     each channel's ids
	 * @param table   the table the ids index
	 * @return each channel's list
	 */
	public static ArrayList[] getBlockDeltasByCost(int[][] channel, int xdim, int ydim, int block, int[][] ids, PredictorTable table)
	{
		int[] blocks = new int[channel.length];
		Arrays.fill(blocks, block);
		return getBlockDeltasByCost(channel, xdim, ydim, blocks, ids, table);
	}

	/**
	 * As {@link #getBlockDeltasByCost(int[][], int, int, int, int[][], PredictorTable)},
	 * with a block shape per channel (as {@link #surveyChannels} chooses them).
	 *
	 * @param channel the three channels, in coding order
	 * @param xdim    the width
	 * @param ydim    the height
	 * @param blocks  each channel's block shape
	 * @param ids     each channel's ids
	 * @param table   the table the ids index
	 * @return each channel's list
	 */
	public static ArrayList[] getBlockDeltasByCost(int[][] channel, int xdim, int ydim, int[] blocks, int[][] ids, PredictorTable table)
	{
		ArrayList[] result = new ArrayList[channel.length];
		int[][] delta = new int[channel.length][];
		PixelSource[] ps = pixelSources(channel, xdim, ydim, table);
		for(int i = 0; i < channel.length; i++)
		{
			int       block = blocks[i];
			DeltaCost dc  = channelCost(ps[i], ydim, block, Arrays.copyOf(delta, i), table);
			byte[]    map = prefixMaps(ps[i], ydim, block, dc, table, ids[i], new int[] {ids[i].length})[0];
			result[i] = getBlockDeltasFromMap(ps[i], ydim, map, table, ids[i]);
			delta[i]  = (int[]) result[i].get(1);
		}
		return result;
	}

	/**
	 * The result of surveyBlockSets: the block shape, each channel's predictor
	 * ids, the coded bytes, and a printable report.
	 */
	public static final class BlockSurvey
	{
		/** An empty survey. */
		public BlockSurvey() {}

		/**
		 * The block shape chosen.
		 */
		public int     block;
		/**
		 * Each channel's predictor ids.
		 */
		public int[][] ids = new int[3][];
		/**
		 * The coded bytes, deltas and maps.
		 */
		public long    bytes;
		/**
		 * A printable report of the survey.
		 */
		public String  report;
		/**
		 * Every shape the full survey tried, by shape, the chosen one
		 * included (null for a survey at one shape).
		 */
		public TreeMap<Integer, BlockSurvey> tried;
		/**
		 * Each channel's block shape ({@link #surveyChannels}; null for a survey
		 * with one shape for all the channels).
		 */
		public int[] blocks;
		/**
		 * Each channel's deltas and block map as chosen ({@link #surveyChannels};
		 * null otherwise): what {@link #getBlockDeltasByCost} would make from
		 * blocks and ids.
		 */
		public int[][]  deltas;
		/**
		 * Each channel's block map, as chosen.
		 */
		public byte[][] maps;
	}

	/**
	 * Chooses the block size and each channel's predictor set (see the section
	 * comment), squares only.
	 *
	 * @param channel the three channels, in coding order
	 * @param xdim    the width
	 * @param ydim    the height
	 * @return the survey
	 */
	public static BlockSurvey surveyBlockSets(int[][] channel, int xdim, int ydim)
	{
		return surveyBlockSets(channel, xdim, ydim, PREDICTOR_TABLE);
	}

	/**
	 * As {@link #surveyBlockSets(int[][], int, int)}; survey_shapes also tries
	 * the rectangles and lines.
	 *
	 * @param channel       the three channels, in coding order
	 * @param xdim          the width
	 * @param ydim          the height
	 * @param survey_shapes whether to try BLOCK_SHAPES too
	 * @return the survey
	 */
	public static BlockSurvey surveyBlockSets(int[][] channel, int xdim, int ydim, boolean survey_shapes)
	{
		return surveyBlockSets(channel, xdim, ydim, PREDICTOR_TABLE, survey_shapes);
	}

	/**
	 * As {@link #surveyBlockSets(int[][], int, int)}, choosing from the ids of the
	 * given table.
	 *
	 * @param channel the three channels, in coding order
	 * @param xdim    the width
	 * @param ydim    the height
	 * @param table   the predictor table
	 * @return the survey
	 */
	public static BlockSurvey surveyBlockSets(int[][] channel, int xdim, int ydim, PredictorTable table)
	{
		return surveyBlockSets(channel, xdim, ydim, table, false);
	}

	/**
	 * The survey in full: block sizes 4, 8 and 16 (in parallel, as memory
	 * allows), then the untried sizes next to the best, then (with
	 * survey_shapes) the rectangles and lines.
	 *
	 * @param channel       the three channels, in coding order
	 * @param xdim          the width
	 * @param ydim          the height
	 * @param table         the predictor table
	 * @param survey_shapes whether to try BLOCK_SHAPES too
	 * @return the survey
	 */
	public static BlockSurvey surveyBlockSets(int[][] channel, int xdim, int ydim, PredictorTable table, boolean survey_shapes)
	{
		TreeMap<Integer, BlockSurvey> tried = new TreeMap<Integer, BlockSurvey>();
		StringBuilder log = new StringBuilder();
		// The block sizes are independent: surveyed at the same time (each
		// ends with a long serial stretch of real coding).
		PixelSource[] ps = pixelSources(channel, xdim, ydim, table);
		long task = 44L * xdim * ydim + 4L * table.size() * Math.min(SURVEY_SAMPLE_BLOCKS, blocksAcross(xdim, 4) * ((ydim - 1 + 3) / 4));
		int[] first = {4, 8, 16};
		BlockSurvey[] got = new BlockSurvey[first.length];
		StringBuilder[] logs = new StringBuilder[first.length];
		boundedParallel(first.length, task, t -> { logs[t] = new StringBuilder(); got[t] = surveyBlockSize(ps, ydim, first[t], table, logs[t]); });
		for(int t = 0; t < first.length; t++) { tried.put(first[t], got[t]); log.append(logs[t]); }
		int best = bestSurvey(tried);
		int s = Arrays.binarySearch(BLOCK_SEARCH_SIZES, best);
		ArrayList<Integer> next = new ArrayList<Integer>();
		for(int t : new int[] {s - 1, s + 1})
			if(t >= 0 && t < BLOCK_SEARCH_SIZES.length && !tried.containsKey(BLOCK_SEARCH_SIZES[t])) next.add(BLOCK_SEARCH_SIZES[t]);
		BlockSurvey[] more = new BlockSurvey[next.size()];
		StringBuilder[] more_log = new StringBuilder[next.size()];
		boundedParallel(next.size(), task, t -> { more_log[t] = new StringBuilder(); more[t] = surveyBlockSize(ps, ydim, next.get(t), table, more_log[t]); });
		for(int t = 0; t < more.length; t++) { tried.put(next.get(t), more[t]); log.append(more_log[t]); }
		// Then the rectangles and lines (BLOCK_SHAPES), if asked for.
		if(survey_shapes)
		{
			int[] shape = new int[BLOCK_SHAPES.length];
			for(int t = 0; t < shape.length; t++) shape[t] = SHAPE_FIRST + t;
			BlockSurvey[] sg = new BlockSurvey[shape.length];
			StringBuilder[] sl = new StringBuilder[shape.length];
			boundedParallel(shape.length, task, t -> { sl[t] = new StringBuilder(); sg[t] = surveyBlockSize(ps, ydim, shape[t], table, sl[t]); });
			for(int t = 0; t < shape.length; t++) { tried.put(shape[t], sg[t]); log.append(sl[t]); }
		}
		BlockSurvey result = tried.get(bestSurvey(tried));

		StringBuilder r = new StringBuilder("Predictor survey: bytes coded (Context-coded deltas + maps) for the first n predictors\n");
		r.append("in each channel's greedy order (by estimated bits); * marks the n kept for the channel.\n");
		r.append(log);
		r.append("Block size:");
		for(Map.Entry<Integer, BlockSurvey> e : tried.entrySet()) r.append(String.format("  %s -> %,d", shapeName(e.getKey()), e.getValue().bytes));
		r.append(String.format("   chose %s (%,d bytes)\n", shapeName(result.block), result.bytes));
		for(int i = 0; i < 3; i++)
		{
			r.append(String.format("Channel %d, %d predictors:", i, result.ids[i].length));
			for(int k = 0; k < result.ids[i].length; k++) r.append((k % 6 == 0) ? "\n    " : ", ").append(table.name(result.ids[i][k]));
			r.append('\n');
		}
		result.report = r.toString();
		result.tried  = tried;
		return result;
	}

	/**
	 * The survey at one block shape only: the same sets the full survey
	 * would choose for that shape.
	 *
	 * @param channel the three channels, in coding order
	 * @param xdim    the width
	 * @param ydim    the height
	 * @param block   the block shape
	 * @param table   the predictor table
	 * @return the survey
	 */
	public static BlockSurvey surveyAtBlockSize(int[][] channel, int xdim, int ydim, int block, PredictorTable table)
	{
		return surveyBlockSize(pixelSources(channel, xdim, ydim, table), ydim, block, table, new StringBuilder());
	}

	/**
	 * Which of the primitives past 21 a survey's sets use, by group (GROUP_NAMES):
	 * for each primitive, the ids chosen (alone or averaged) and the share of
	 * blocks using them; then each group's share of blocks and the primitives
	 * nobody chose.
	 *
	 * @param survey the survey
	 * @param coded  getBlockDeltasByCost's result for it
	 * @return the report
	 */
	public static String describeGroupUse(BlockSurvey survey, ArrayList[] coded)
	{
		PredictorTable table = PREDICTOR_TABLE;
		long total = 0;
		long[][] blocks = new long[3][table.size()];
		for(int i = 0; i < 3; i++)
		{
			byte[] map = (byte[]) coded[i].get(2);
			for(int k = mapHeaderLength(map); k < map.length; k++) { blocks[i][survey.ids[i][map[k] & 0xFF]]++; total++; }
		}
		StringBuilder r = new StringBuilder("Predictors past the basic 22 in the surveyed sets (share of all blocks):\n");
		for(int g = 0; g < GROUP_NAMES.length; g++)
		{
			int lo = GROUP_FIRST[g], hi = GROUP_FIRST[g + 1];
			long any = 0;
			for(int i = 0; i < 3; i++)
				for(int pid : survey.ids[i])
					for(int p = lo; p < hi; p++) if(table.uses(pid, p)) { any += blocks[i][pid]; break; }
			StringBuilder used = new StringBuilder(), unused = new StringBuilder();
			for(int p = lo; p < hi; p++)
			{
				boolean in_table = false;
				for(int id = 0; id < table.size(); id++) in_table |= table.uses(id, p);
				if(!in_table) continue;
				long alone = 0, avg = 0; int chosen = 0;
				for(int i = 0; i < 3; i++)
					for(int pid : survey.ids[i])
						if(table.uses(pid, p))
						{
							chosen++;
							if(table.alone(pid)) alone += blocks[i][pid]; else avg += blocks[i][pid];
						}
				String name = primitiveName(p);
				if(chosen == 0) unused.append(unused.length() == 0 ? "" : ", ").append(name);
				else used.append(String.format("      %-20s %2d ids chosen, %5.2f%% alone, %5.2f%% in averages%n", name, chosen, 100.0 * alone / total, 100.0 * avg / total));
			}
			if(used.length() == 0) r.append(String.format("  %s: unused.%n", GROUP_NAMES[g]));
			else
			{
				r.append(String.format("  %s: used, in %.2f%% of blocks.%n", GROUP_NAMES[g], 100.0 * any / total)).append(used);
				if(unused.length() > 0) r.append("      unused: ").append(unused).append('\n');
			}
		}
		return r.toString();
	}

	/**
	 * The best of the shapes surveyed.
	 *
	 * @param tried the surveys, by shape
	 * @return the shape with the fewest bytes (the first, on a tie)
	 */
	private static int bestSurvey(TreeMap<Integer, BlockSurvey> tried)
	{
		int best = -1;
		for(Map.Entry<Integer, BlockSurvey> e : tried.entrySet()) if(best < 0 || e.getValue().bytes < tried.get(best).bytes) best = e.getKey();
		return best;
	}

	/**
	 * The survey at one block shape: per channel, the greedy order and the real
	 * coded size of its prefixes (see the section comment). Big images find the
	 * order on a sample of block rows.
	 *
	 * @param ps    the channels' PixelSources
	 * @param ydim  the height
	 * @param block the block shape
	 * @param table the predictor table
	 * @param log   the report lines, added to
	 * @return the survey
	 */
	private static BlockSurvey surveyBlockSize(PixelSource[] ps, int ydim, int block, PredictorTable table, StringBuilder log)
	{
		try
		{
			int xdim = ps[0].xdim;
			BlockSurvey result = new BlockSurvey();
			result.block = block;
			int[][]  delta = new int[3][];
			byte[][] map   = new byte[3][];
			int      bw = blocksAcross(xdim, block), bh = blocksDown(ydim, block), blocks = bw * bh;
			int      step = surveyStep(blocks), sampled = (bh + step - 1) / step * bw;
			log.append(String.format("  block %s%s:\n", shapeName(block), (step > 1) ? String.format(" (order from %,d of %,d blocks)", sampled, blocks) : ""));
			for(int i = 0; i < 3; i++)
			{
				StringBuilder line = new StringBuilder();
				int[][] before = Arrays.copyOf(delta, i);
				ChannelSurvey c = surveyChannel(ps[i], ydim, block, table, channelCost(ps[i], ydim, block, before, table), before, (i > 0) ? map[i - 1] : null, line);
				result.ids[i] = c.ids; delta[i] = c.delta; map[i] = c.map;
				result.bytes += c.bytes;
				log.append(String.format("    channel %d", i)).append(line).append('\n');
			}
			return result;
		}
		catch(java.io.IOException e) { throw new java.io.UncheckedIOException(e); }
	}

	/**
	 * Big images find the survey's greedy order on every step-th block row (at
	 * most SURVEY_SAMPLE_BLOCKS blocks), with the id's cost scaled to match;
	 * the maps are then made over every block.
	 *
	 * @param blocks the number of blocks
	 * @return the step
	 */
	private static int surveyStep(int blocks) { return (blocks > SURVEY_SAMPLE_BLOCKS) ? (blocks + SURVEY_SAMPLE_BLOCKS - 1) / SURVEY_SAMPLE_BLOCKS : 1; }

	/**
	 * One channel's survey at one block shape: its predictor set, coded size,
	 * deltas and map.
	 */
	static final class ChannelSurvey
	{
		/** An empty result (no bytes yet). */
		ChannelSurvey() {}

		/**
		 * The block shape.
		 */
		int    block;
		/**
		 * The predictor ids kept.
		 */
		int[]  ids;
		/**
		 * The coded bytes (Context-coded deltas plus the map).
		 */
		long   bytes = Long.MAX_VALUE;
		/**
		 * The deltas.
		 */
		int[]  delta;
		/**
		 * The block map.
		 */
		byte[] map;
	}

	/**
	 * One channel of the survey at one block shape: the greedy order by
	 * estimated bits, then the real coded size of its prefixes at
	 * SURVEY_COUNTS until two in a row are larger than the best.
	 *
	 * @param ps            the channel, and what its primitives need
	 * @param ydim          the height
	 * @param block         the block shape
	 * @param table         the predictor table
	 * @param dc            the cost model
	 * @param chosen_before the deltas chosen for the channels before
	 * @param previous_map  the map chosen for the channel before, or null
	 * @param line          the report line, added to
	 * @return the channel's survey
	 * @throws java.io.IOException never in practice (byte streams)
	 */
	private static ChannelSurvey surveyChannel(PixelSource ps, int ydim, int block, PredictorTable table, DeltaCost dc,
		int[][] chosen_before, byte[] previous_map, StringBuilder line) throws java.io.IOException
	{
		int   xdim = ps.xdim;
		int   bw = blocksAcross(xdim, block), bh = blocksDown(ydim, block), blocks = bw * bh;
		int   step = surveyStep(blocks);
		int[] rows = new int[(bh + step - 1) / step];
		for(int r = 0; r < rows.length; r++) rows[r] = r * step;
		int   sampled = rows.length * bw;
		int[]     est   = blockCostRows(ps, ydim, block, dc, table, null, rows);
		int[]     order = greedyCostOrder(est, sampled, table.size(), (long) SURVEY_ID_COST * sampled / blocks);
		ArrayList<Integer> counts = new ArrayList<Integer>();
		for(int c : SURVEY_COUNTS) if(c < order.length) counts.add(c);
		counts.add(order.length);
		byte[][] prefix = null;
		if(step > 1)
		{
			est = null;
			prefix = prefixMaps(ps, ydim, block, dc, table, order, counts.stream().mapToInt(Integer::intValue).toArray());
		}

		ChannelSurvey result = new ChannelSurvey();
		result.block = block;
		int worse = 0;
		line.append(String.format(" (order %d):", order.length));
		// The counts are coded surveyWidth() at a time, on cores the other
		// block shapes leave idle; those past where the stopping rule ends
		// are thrown away, so the result (and the report) is the same.
		final int[] est_ = est;
		final byte[][] prefix_ = prefix;
		int width = surveyWidth();
		boolean done = false;
		for(int c0 = 0; c0 < counts.size() && !done; c0 += width)
		{
			int      c1    = Math.min(counts.size(), c0 + width), first = c0;
			int[][]  ids   = new int[c1 - c0][];
			int[][]  d     = new int[c1 - c0][];
			byte[][] m     = new byte[c1 - c0][];
			long[]   size  = new long[c1 - c0];
			boundedParallel(c1 - c0, 16L * xdim * ydim, t ->
			{
				int c = first + t;
				ids[t] = Arrays.copyOf(order, counts.get(c));
				byte[]    mp = (prefix_ != null) ? prefix_[c] : assignByCost(est_, true, table.size(), ids[t], xdim, ydim, block);
				ArrayList r  = getBlockDeltasFromMap(ps, ydim, mp, table, ids[t]);
				d[t] = (int[]) r.get(1);
				m[t] = (byte[]) r.get(2);
				try
				{
					java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
					writeMap(new java.io.DataOutputStream(bytes), 13, m[t], previous_map, xdim);
					size[t] = bytes.size() + packContextDeltas(d[t], chosen_before, xdim).length;
				}
				catch(java.io.IOException e) { throw new java.io.UncheckedIOException(e); }
			});
			for(int t = 0; t < c1 - c0; t++)
			{
				line.append(String.format(" %d:%,d", ids[t].length, size[t]));
				if(size[t] < result.bytes) { result.bytes = size[t]; result.ids = ids[t]; result.delta = d[t]; result.map = m[t]; worse = 0; line.append('*'); }
				else if(++worse == 2) { done = true; break; }
			}
		}
		return result;
	}

	/**
	 * How many of a channel's predictor counts the survey codes at once at
	 * each block shape: the cores left over when the five block shapes run
	 * at once, shared out (1 on up to 9 cores, 2 on 10-14, 3 on 15-19, at
	 * most 4).
	 *
	 * @return the number of counts coded at once
	 */
	private static int surveyWidth()
	{
		return Math.max(1, Math.min(4, Runtime.getRuntime().availableProcessors() / 5));
	}

	/**
	 * The square block sizes {@link #surveyChannels} tries by default.
	 */
	public static final int[] SURVEY_CHANNEL_SIZES = {4, 6, 8, 12, 16};

	/**
	 * The survey with a block shape per channel: channels in order, each
	 * surveyed at every shape in sizes (in parallel, as memory allows) given
	 * the deltas and map chosen for the channels before it, keeping the shape
	 * that codes it smallest (the first on a tie). The result's block is
	 * channel 0's; blocks has each channel's.
	 *
	 * @param channel     the three channels, in coding order
	 * @param xdim        the width
	 * @param ydim        the height
	 * @param table       the predictor table
	 * @param sizes       the block shapes to try
	 * @return the survey
	 */
	public static BlockSurvey surveyChannels(int[][] channel, int xdim, int ydim, PredictorTable table, int[] sizes)
	{
		PixelSource[] ps = pixelSources(channel, xdim, ydim, table);
		long task = 44L * xdim * ydim + 4L * table.size() * Math.min(SURVEY_SAMPLE_BLOCKS, blocksAcross(xdim, 4) * ((ydim - 1 + 3) / 4));
		BlockSurvey result = new BlockSurvey();
		result.blocks = new int[channel.length];
		int[][]  delta = new int[channel.length][];
		byte[][] map   = new byte[channel.length][];
		StringBuilder log = new StringBuilder();
		for(int i = 0; i < channel.length; i++)
		{
			final int ci = i;
			ChannelSurvey[] got = new ChannelSurvey[sizes.length];
			StringBuilder[] line = new StringBuilder[sizes.length];
			int[][] before = Arrays.copyOf(delta, i);
			byte[]  prev   = (i > 0) ? map[i - 1] : null;
			// The whole table's reference deltas for every size in one pass.
			int[][] ref = tableReferenceDeltas(ps[i], ydim, sizes, table);
			boundedParallel(sizes.length, task, t ->
			{
				line[t] = new StringBuilder();
				try
				{
					DeltaCost dc = channelCost(ps[ci], ydim, ref[t], before);
					got[t] = surveyChannel(ps[ci], ydim, sizes[t], table, dc, before, prev, line[t]);
				}
				catch(java.io.IOException e) { throw new java.io.UncheckedIOException(e); }
			});
			int best = 0;
			for(int t = 0; t < sizes.length; t++)
			{
				log.append(String.format("  channel %d, block %s", i, shapeName(sizes[t]))).append(line[t]).append('\n');
				if(got[t].bytes < got[best].bytes) best = t;
			}
			result.blocks[i] = sizes[best];
			result.ids[i]    = got[best].ids;
			result.bytes    += got[best].bytes;
			delta[i] = got[best].delta;
			map[i]   = got[best].map;
		}
		result.block  = result.blocks[0];
		result.report = log.toString();
		result.deltas = delta;
		result.maps   = map;
		return result;
	}

	/**
	 * The last surveyedBlockMaps: {xdim, the channels (copies), the survey}.
	 */
	private static Object[] SURVEYED = null;

	/**
	 * The channel set whose three channels have the smallest summed entropy
	 * estimate (getIdealFrequency2; the first on a tie), as the writers pick it.
	 *
	 * @param qc   the six candidate channels (getCandidateChannels)
	 * @param xdim the width
	 * @param ydim the height
	 * @return the channel set, 0 to 9
	 */
	public static int bestChannelSet(int[][] qc, int xdim, int ydim)
	{
		int[] sum = new int[6];
		java.util.stream.IntStream.range(0, 6).parallel().forEach(i -> sum[i] = (int) Math.floor(CodeMapper.getShannonLimit(getIdealFrequency2(qc[i], xdim, ydim))));
		int best = 0, best_sum = Integer.MAX_VALUE;
		for(int s = 0; s < 10; s++)
		{
			int[] c = getChannels(s);
			int t = sum[c[0]] + sum[c[1]] + sum[c[2]];
			if(t < best_sum) { best_sum = t; best = s; }
		}
		return best;
	}

	/**
	 * A channel's block map from a survey, as getDeltas gives other types:
	 * [sum of |delta| (Integer), deltas (int[]), map (byte[]), the first value
	 * (Integer)]. The deltas and map are copies, as getDeltas' are new: some
	 * callers change them (StringMapper.getStringList), and the survey is kept
	 * for the next call (surveyedBlockMaps).
	 *
	 * @param survey the survey (surveyedBlockMaps)
	 * @param i      the channel, in coding order
	 * @param first  the channel's first value
	 * @return the list
	 */
	public static ArrayList surveyedDeltas(BlockSurvey survey, int i, int first)
	{
		long sum = 0;
		for(int v : survey.deltas[i]) sum += Math.abs(v);
		ArrayList result = new ArrayList();
		result.add((int) Math.min(Integer.MAX_VALUE, sum));
		result.add(survey.deltas[i].clone());
		result.add(survey.maps[i].clone());
		result.add(first);
		return result;
	}

	/**
	 * The block maps the survey chooses for three channels (surveyChannels at
	 * SURVEY_CHANNEL_SIZES, from PREDICTOR_TABLE): each channel's block size,
	 * predictor set, deltas and map. The last result is kept, and returned
	 * again for the same channels, so a writer can ask whenever it needs the
	 * block map's deltas (ranking delta types, choosing a datatype, Apply).
	 *
	 * @param channel the three channels, in coding order
	 * @param xdim    the width
	 * @param ydim    the height
	 * @return the survey (blocks, ids, deltas and maps per channel)
	 */
	public static synchronized BlockSurvey surveyedBlockMaps(int[][] channel, int xdim, int ydim)
	{
		if(SURVEYED != null && (Integer) SURVEYED[0] == xdim)
		{
			int[][] had = (int[][]) SURVEYED[1];
			boolean same = had.length == channel.length;
			for(int i = 0; same && i < channel.length; i++) same = Arrays.equals(had[i], channel[i]);
			if(same) return (BlockSurvey) SURVEYED[2];
		}
		BlockSurvey survey = surveyChannels(channel, xdim, ydim, PREDICTOR_TABLE, SURVEY_CHANNEL_SIZES);
		int[][] copy = new int[channel.length][];
		for(int i = 0; i < channel.length; i++) copy[i] = channel[i].clone();
		SURVEYED = new Object[] {xdim, copy, survey};
		return survey;
	}

	/**
	 * Blocks across the interior (columns 1 to xdim - 2).
	 *
	 * @param xdim  the width
	 * @param block the block shape
	 * @return the number of blocks
	 */
	private static int blocksAcross(int xdim, int block) { int w = blockWidth(block, xdim); return (xdim - 2 + w - 1) / w; }

	/**
	 * Blocks down the interior (rows 1 to ydim - 1).
	 *
	 * @param ydim  the height
	 * @param block the block shape
	 * @return the number of blocks
	 */
	private static int blocksDown(int ydim, int block)   { int h = blockHeight(block);      return (ydim - 1 + h - 1) / h; }

	/**
	 * Delta type 13, block map, with a set of predictor ids: each block takes the
	 * one with the smallest sum of |delta|.
	 *
	 * @param src   the channel
	 * @param ref   the previous channel (null for the first)
	 * @param xdim  the width
	 * @param ydim  the height
	 * @param block the block shape
	 * @param ids   the predictor ids
	 * @return [sum of |delta| (Integer), deltas (int[]), map (byte[]), the first
	 *         value (Integer)]
	 */
	public static ArrayList getBlockDeltasFromValues(int[] src, int[] ref, int xdim, int ydim, int block, int[] ids)
	{
		return getBlockDeltasFromValues(new PixelSource(src, ref, xdim, ydim, PREDICTOR_TABLE), ydim, block, ids);
	}

	/**
	 * As {@link #getBlockDeltasFromValues(int[], int[], int, int, int, int[])},
	 * with the channel (and what its primitives need) in ps.
	 *
	 * @param ps    the channel, and what its primitives need
	 * @param ydim  the height
	 * @param block the block shape
	 * @param ids   the predictor ids
	 * @return [sum of |delta| (Integer), deltas (int[]), map (byte[]), the first
	 *         value (Integer)]
	 */
	static ArrayList getBlockDeltasFromValues(PixelSource ps, int ydim, int block, int[] ids)
	{
		int    xdim = ps.xdim;
		int    bw = blocksAcross(xdim, block), bh = blocksDown(ydim, block), n = ids.length;
		byte[] map = new byte[2 + n + bw * bh];
		map[0] = (byte) block; map[1] = (byte) n;
		for(int p = 0; p < n; p++) map[2 + p] = (byte) ids[p];
		blockChoices(ps, ydim, block, PREDICTOR_TABLE, ids, map, 2 + n);
		return getBlockDeltasFromMap(ps, ydim, map, PREDICTOR_TABLE, ids);
	}

	/**
	 * Each block's choice (an index into ids) by smallest sum of |delta|, the
	 * first on a tie, written into map from offset head.
	 *
	 * @param ps    the channel, and what its primitives need
	 * @param ydim  the height
	 * @param block the block shape
	 * @param table the table the ids index
	 * @param ids   the predictor ids (at most 256)
	 * @param map   the map the choices go in
	 * @param head  where the first block's choice goes
	 */
	private static void blockChoices(PixelSource ps, int ydim, int block, PredictorTable table, int[] ids, byte[] map, int head)
	{
		int[]  src = ps.v;
		int    xdim = ps.xdim;
		int    bw = blocksAcross(xdim, block), bh = blocksDown(ydim, block), n = ids.length;
		// Each primitive the set uses, once per pixel.
		boolean[] need = new boolean[table.primitives];
		for(int id : ids) { need[table.first(id)] = true; need[table.second(id)] = true; }
		int m = 0; for(boolean b : need) if(b) m++;
		int[] used = new int[m]; m = 0;
		for(int q = 0; q < need.length; q++) if(need[q]) used[m++] = q;
		// The first of each group of alike ids only (see samePrediction):
		// the first smallest among them is the first smallest of all.
		int[] at = distinctPositions(samePrediction(ps, table, ids));
		int   na = at.length;
		int[] one = new int[na];
		for(int t = 0; t < na; t++) one[t] = ids[at[t]];
		java.util.stream.IntStream.range(0, bh).parallel().forEach(by ->
		{
			long[] sums = new long[bw * na];
			int[]  prim = new int[table.primitives];
			int    bh_ = blockHeight(block), bw_ = blockWidth(block, xdim);
			int    y0 = 1 + by * bh_, y1 = Math.min(ydim, y0 + bh_);
			for(int y = y0; y < y1; y++)
				for(int x = 1; x < xdim - 1; x++)
				{
					int k = y * xdim + x, base = (x - 1) / bw_ * na;
					for(int q : used) prim[q] = ps.primitive(q, k);
					for(int p = 0; p < na; p++) sums[base + p] += Math.abs(src[k] - table.predict(one[p], prim));
				}
			for(int bx = 0; bx < bw; bx++)
			{
				int best = 0;
				for(int p = 1; p < na; p++) if(sums[bx * na + p] < sums[bx * na + best]) best = p;
				map[head + by * bw + bx] = (byte) at[best];
			}
		});
	}

	/**
	 * The survey's reference deltas from the whole table, at several block
	 * shapes: each block takes whichever of the table's predictors gives the
	 * smallest sum of |delta| (the first on a tie). Each family of nested
	 * squares (4, 8 and 16; 6 and 12) is found in one pass over the pixels,
	 * the larger blocks' sums added up from the smallest's; other shapes one
	 * at a time. Either way the result is the same.
	 *
	 * @param ps     the channel, and what its primitives need
	 * @param ydim   the height
	 * @param blocks the block shapes
	 * @param table  the predictor table (at most 256 ids)
	 * @return each shape's deltas
	 */
	static int[][] tableReferenceDeltas(PixelSource ps, int ydim, int[] blocks, PredictorTable table)
	{
		int[][] ref = new int[blocks.length][];
		if(!nestedSquares(blocks))
		{
			// Each family of nested squares (4, 8, 16; 6, 12) in its own pass;
			// other shapes one at a time.
			for(int[] family : nestedFamilies(blocks))
			{
				int[] shapes = new int[family.length];
				for(int f = 0; f < family.length; f++) shapes[f] = blocks[family[f]];
				int[][] got = (family.length == 1) ? new int[][] {tableReferenceDeltas(ps, ydim, shapes[0], table)} : tableReferenceDeltas(ps, ydim, shapes, table);
				for(int f = 0; f < family.length; f++) ref[family[f]] = got[f];
			}
			return ref;
		}
		int xdim = ps.xdim, n = table.size();
		int base = Integer.MAX_VALUE, top = 0;
		for(int b : blocks) { base = Math.min(base, b); top = Math.max(top, b); }
		int[] ids = new int[n];
		for(int p = 0; p < n; p++) ids[p] = p;
		byte[][] map = new byte[blocks.length][];
		for(int t = 0; t < blocks.length; t++)
		{
			map[t] = new byte[2 + blocksAcross(xdim, blocks[t]) * blocksDown(ydim, blocks[t])];
			map[t][0] = (byte) blocks[t];
		}
		final int base_ = base, top_ = top;
		int bw_base = blocksAcross(xdim, base), rows_base = top / base;
		int[] src = ps.v;
		// The first of each group of alike ids only (see samePrediction).
		int[] at = distinctPositions(samePrediction(ps, table, ids));
		int   na = at.length;
		// A band of top rows at a time: each base block's sums of |delta|, then
		// the sums of each shape's blocks, which are exactly unions of base blocks.
		java.util.stream.IntStream.range(0, blocksDown(ydim, top)).parallel().forEach(band ->
		{
			int[] sums = new int[rows_base * bw_base * na];
			int[] prim = new int[table.primitives];
			int   y0 = 1 + band * top_, y1 = Math.min(ydim, y0 + top_);
			for(int y = y0; y < y1; y++)
			{
				int r = (y - y0) / base_;
				for(int x = 1; x < xdim - 1; x++)
				{
					int k = y * xdim + x, s0 = (r * bw_base + (x - 1) / base_) * na;
					ps.primitives(k, table.primitives, prim);
					for(int p = 0; p < na; p++) sums[s0 + p] += Math.abs(src[k] - table.predict(at[p], prim));
				}
			}
			long[] total = new long[na];
			for(int t = 0; t < blocks.length; t++)
			{
				int size = blocks[t], f = size / base_, rows = top_ / size, bw = blocksAcross(xdim, size), bh = blocksDown(ydim, size);
				for(int rs = 0; rs < rows; rs++)
				{
					int by = band * rows + rs;
					if(by >= bh) break;
					for(int bx = 0; bx < bw; bx++)
					{
						Arrays.fill(total, 0);
						for(int rr = rs * f; rr < rs * f + f; rr++)
							for(int cc = bx * f; cc < Math.min(bw_base, bx * f + f); cc++)
								for(int p = 0, s0 = (rr * bw_base + cc) * na; p < na; p++) total[p] += sums[s0 + p];
						int best = 0;
						for(int p = 1; p < na; p++) if(total[p] < total[best]) best = p;
						map[t][2 + by * bw + bx] = (byte) at[best];
					}
				}
			}
		});
		for(int t = 0; t < blocks.length; t++) ref[t] = (int[]) getBlockDeltasFromMap(ps, ydim, map[t], table, ids).get(1);
		return ref;
	}

	/**
	 * The shapes split into families of nested squares, smallest first: each
	 * square joins the first family whose smallest divides it and whose
	 * largest divides it or is divided by it (4, 6, 8, 12, 16: {4, 8, 16} and
	 * {6, 12}). Other shapes are families of one.
	 *
	 * @param blocks the shapes
	 * @return the families, as indices into blocks
	 */
	private static ArrayList<int[]> nestedFamilies(int[] blocks)
	{
		Integer[] order = new Integer[blocks.length];
		for(int t = 0; t < blocks.length; t++) order[t] = t;
		Arrays.sort(order, (a, b) -> blocks[a] - blocks[b]);
		ArrayList<ArrayList<Integer>> family = new ArrayList<ArrayList<Integer>>();
		for(int t : order)
		{
			int b = blocks[t];
			ArrayList<Integer> home = null;
			if(b < SHAPE_FIRST)
				for(ArrayList<Integer> f : family)
				{
					int base = blocks[f.get(0)], top = blocks[f.get(f.size() - 1)];
					if(base < SHAPE_FIRST && b % base == 0 && (top % b == 0 || b % top == 0)) { home = f; break; }
				}
			if(home == null) { home = new ArrayList<Integer>(); family.add(home); }
			home.add(t);
		}
		ArrayList<int[]> result = new ArrayList<int[]>();
		for(ArrayList<Integer> f : family) result.add(f.stream().mapToInt(Integer::intValue).toArray());
		return result;
	}

	/**
	 * Whether the shapes are squares, each dividing the largest and divided by
	 * the smallest: then every block of each is a union of the smallest's
	 * blocks, inside one row of the largest's.
	 *
	 * @param blocks the shapes
	 * @return whether their reference deltas can be found in one pass
	 */
	private static boolean nestedSquares(int[] blocks)
	{
		int base = Integer.MAX_VALUE, top = 0;
		for(int b : blocks) { if(b >= SHAPE_FIRST) return false; base = Math.min(base, b); top = Math.max(top, b); }
		for(int b : blocks) if(b % base != 0 || top % b != 0) return false;
		return true;
	}

	/**
	 * The survey's reference deltas from the whole table at one block shape
	 * (see the method above, which finds several shapes' in one pass).
	 *
	 * @param ps    the channel, and what its primitives need
	 * @param ydim  the height
	 * @param block the block shape
	 * @param table the predictor table (at most 256 ids)
	 * @return the deltas
	 */
	static int[] tableReferenceDeltas(PixelSource ps, int ydim, int block, PredictorTable table)
	{
		int[] ids = new int[table.size()];
		for(int p = 0; p < ids.length; p++) ids[p] = p;
		// No id list in this map's header (256 ids don't fit its count byte):
		// the choices start at 2 and the ids are passed directly.
		byte[] map = new byte[2 + blocksAcross(ps.xdim, block) * blocksDown(ydim, block)];
		map[0] = (byte) block;
		blockChoices(ps, ydim, block, table, ids, map, 2);
		return (int[]) getBlockDeltasFromMap(ps, ydim, map, table, ids).get(1);
	}

	/**
	 * Inverse of getBlockDeltasFromValues (and of the survey's deltas).
	 *
	 * @param delta      the deltas
	 * @param xdim       the width
	 * @param ydim       the height
	 * @param init_value the first value
	 * @param map        the block map
	 * @param ref        the previous channel's values, as decoded (before any
	 *                   minimum is added back), or null for the first channel
	 * @return the channel
	 */
	public static int[] getValuesFromBlockDeltas(int[] delta, int xdim, int ydim, int init_value, byte[] map, int[] ref)
	{
		return blockValues(delta, xdim, ydim, init_value, map, ref, null, PREDICTOR_TABLE);
	}

	/**
	 * As {@link #getValuesFromBlockDeltas(int[], int, int, int, byte[], int[])},
	 * with the map's ids in the given table (PREDICTOR_TABLE_256 for older files).
	 *
	 * @param delta      the deltas
	 * @param xdim       the width
	 * @param ydim       the height
	 * @param init_value the first value
	 * @param map        the block map
	 * @param ref        the previous channel's values, as decoded, or null for the first channel
	 * @param table      the table the map's ids index
	 * @return the channel
	 */
	public static int[] getValuesFromBlockDeltas(int[] delta, int xdim, int ydim, int init_value, byte[] map, int[] ref, PredictorTable table)
	{
		return blockValues(delta, xdim, ydim, init_value, map, ref, null, table);
	}

	/**
	 * getValuesFromBlockDeltas, with the deltas optionally decoded as they are
	 * needed (each from the deltas and values before it).
	 *
	 * @param delta      the deltas (filled in as decoded, if mix is given)
	 * @param xdim       the width
	 * @param ydim       the height
	 * @param init_value the first value
	 * @param map        the block map
	 * @param ref        the previous channel's values, as decoded, or null
	 * @param mix        the decoder of the deltas, or null if delta holds them
	 * @param table      the table the map's ids index
	 * @return the channel
	 */
	private static int[] blockValues(int[] delta, int xdim, int ydim, int init_value, byte[] map, int[] ref, MixedDecoder mix, PredictorTable table)
	{
		int   block = map[0] & 0xFF, bw = blocksAcross(xdim, block), head = mapHeaderLength(map);
		int   bh_ = blockHeight(block), bw_ = blockWidth(block, xdim);
		int[] ids   = getMapIds(map);
		int[] dst   = new int[xdim * ydim];
		boolean least_squares = false;
		for(int id : ids)
		{
			if(id >= table.size()) throw new IllegalArgumentException("Block map with predictor id " + id + ": not in the " + table.name + " table.");
			least_squares |= table.uses(id, LS_FIRST) || table.uses(id, LS_WIDE);
		}
		PixelSource ps = new PixelSource(dst, ref, xdim, table, least_squares);
		if(mix != null) mix.next(0, delta, dst);
		dst[0] = init_value;
		for(int x = 1; x < xdim; x++)
		{
			if(mix != null) mix.next(x, delta, dst);
			dst[x] = dst[x - 1] + delta[x];
		}
		for(int y = 1; y < ydim; y++)
		{
			int k = y * xdim;
			if(mix != null) mix.next(k, delta, dst);
			dst[k] = dst[k - xdim] + delta[k];
			int row = head + (y - 1) / bh_ * bw;
			for(int x = 1; x < xdim - 1; x++)
			{
				k++;
				int id = ids[map[row + (x - 1) / bw_] & 0xFF];
				ps.prepare(k, table.uses(id, LS_FIRST) || table.uses(id, LS_WIDE));
				int p = ps.predict(table, id, k);
				if(mix != null) mix.next(k, delta, dst, p);
				dst[k] = delta[k] + p;
				ps.commit(k);
			}
			k++;
			if(mix != null) mix.next(k, delta, dst);
			dst[k] = dst[k - 1] + delta[k];
		}
		return dst;
	}

	/**
	 * The block map's edges: row 0 horizontal, column 0 vertical, last column
	 * horizontal.
	 *
	 * @param src  the channel
	 * @param dst  the deltas, filled in at the edges
	 * @param xdim the width
	 * @param ydim the height
	 */
	private static void blockEdges(int[] src, int[] dst, int xdim, int ydim)
	{
		for(int x = 1; x < xdim; x++) dst[x] = src[x] - src[x - 1];
		for(int y = 1; y < ydim; y++)
		{
			dst[y * xdim]            = src[y * xdim] - src[(y - 1) * xdim];
			dst[y * xdim + xdim - 1] = src[y * xdim + xdim - 1] - src[y * xdim + xdim - 2];
		}
	}

	// =========================================================================
	// Maps on disk
	// =========================================================================

	// ---- Tables and maps on disk ---------------------------------------------

	/**
	 * Writes a StringMapper rank table: short length, then one unsigned byte per
	 * entry if the length is at most 255, otherwise one short per entry.
	 *
	 * @param out   the stream
	 * @param table the table
	 * @throws java.io.IOException if the stream fails
	 */
	public static void writeTable(java.io.DataOutputStream out, int[] table) throws java.io.IOException
	{
		out.writeShort(table.length);
		if(table.length <= 255) for(int v : table) out.writeByte(v);
		else                    for(int v : table) out.writeShort(v);
	}

	/**
	 * Reads a table {@link #writeTable} wrote.
	 *
	 * @param in the stream
	 * @return the table
	 * @throws java.io.IOException if the stream fails
	 */
	public static int[] readTable(java.io.DataInputStream in) throws java.io.IOException
	{
		int   n     = in.readShort();
		int[] table = new int[n];
		if(n <= 255) for(int k = 0; k < n; k++) table[k] = in.readUnsignedByte();
		else         for(int k = 0; k < n; k++) table[k] = in.readShort();
		return table;
	}

	/**
	 * Writes a delta type's map. Types 6-8 (values 0-3): int length, int packed
	 * length, then 4 values per byte, low bits first. Types 9-13: a flag byte,
	 * then one of
	 * <ul>
	 * <li>0: a unary-string map -- int length, table, int min, int bit length,
	 *     first value as a byte, then the string;</li>
	 * <li>1: an arithmetic-coded map -- int length, short K, K pairs of (value
	 *     byte, int count), int coded length, coded bytes;</li>
	 * <li>2: a context-coded map (see {@link #mapContext}) -- int length, byte K
	 *     (values are 0..K-1), int coded length, coded bytes (only for K &lt;= 32);</li>
	 * <li>3: a neighbour-coded map (see {@link #mapToNeighbours}; format version
	 *     2) -- int length, short K, int coded length, coded bytes.</li>
	 * </ul>
	 * A block map (type 13) starts with its shape, the number of predictors n and
	 * their n ids (one byte each); the entries after them are coded as above. The
	 * writer keeps whichever form is smallest.
	 *
	 * @param out        the stream
	 * @param delta_type 6 to 13
	 * @param map        the map
	 * @param previous   the map of the channel before (null for the first)
	 * @param xdim       the row width of the deltas
	 * @throws java.io.IOException if the stream fails
	 */
	public static void writeMap(java.io.DataOutputStream out, int delta_type, byte[] map, byte[] previous, int xdim) throws java.io.IOException
	{
		if(delta_type <= 8)
		{
			byte[] packed = new byte[(map.length + 3) / 4];
			for(int q = 0; q < map.length; q++)
				packed[q >> 2] |= (byte)((map[q] & 3) << ((q & 3) << 1));
			out.writeInt(map.length); out.writeInt(packed.length); out.write(packed);
			return;
		}
		int width = mapWidth(delta_type, xdim);
		if(delta_type == 13)
		{
			// Block size, n, the n predictor ids, then the entries.
			int head = mapHeaderLength(map);
			out.write(map, 0, head);
			width    = blocksAcross(xdim, map[0] & 0xFF);
			int block = map[0] & 0xFF;
			map      = Arrays.copyOfRange(map, head, map.length);
			if(previous != null)
				previous = alignMap(Arrays.copyOfRange(previous, mapHeaderLength(previous), previous.length), previous[0] & 0xFF, block, xdim, map.length);
		}
		byte[][] form = {mapToString(map), mapToArithmetic(map), mapToContext(map, previous, width), mapToNeighbours(map, previous, width)};
		int best = 0;
		for(int f = 1; f < form.length; f++) if(form[f] != null && form[f].length < form[best].length) best = f;
		out.writeByte(best);
		out.write(form[best]);
	}

	/**
	 * Reads a map {@link #writeMap} wrote.
	 *
	 * @param in         the stream
	 * @param delta_type 6 to 13
	 * @param previous   the map of the channel before (null for the first), as
	 *                   given to the writer
	 * @param xdim       the row width of the deltas
	 * @return the map
	 * @throws java.io.IOException if the stream fails or the map is not one this
	 *         version reads
	 */
	public static byte[] readMap(java.io.DataInputStream in, int delta_type, byte[] previous, int xdim) throws java.io.IOException
	{
		if(delta_type <= 8)
		{
			int    n      = in.readInt();
			byte[] packed = new byte[in.readInt()];
			in.readFully(packed);
			byte[] map = new byte[n];
			for(int q = 0; q < n; q++) map[q] = (byte)((packed[q >> 2] >> ((q & 3) << 1)) & 3);
			return map;
		}
		if(delta_type == 13)
		{
			int   block = in.readUnsignedByte();
			int   n     = in.readUnsignedByte();
			if(n < 1 || n > MAX_PREDICTORS) throw new java.io.IOException("Block map with " + n + " predictors: not one this version reads.");
			int[] ids = new int[n];
			for(int p = 0; p < n; p++)
			{
				ids[p] = in.readUnsignedByte();
				if(ids[p] >= MAX_PREDICTORS) throw new java.io.IOException("Block map with predictor id " + ids[p] + ": not one this version reads.");
			}
			if(!validShape(block))
				throw new java.io.IOException("Block map with block shape " + block + ": not one this version reads.");
			byte[] prev = (previous == null) ? null : Arrays.copyOfRange(previous, mapHeaderLength(previous), previous.length);
			int    prev_block = (previous == null) ? block : previous[0] & 0xFF;
			byte[] body = readMapForms(in, (prev == null) ? null : n_entries -> alignMap(prev, prev_block, block, xdim, n_entries), blocksAcross(xdim, block));
			int    head = 2 + ids.length;
			byte[] map  = new byte[body.length + head];
			map[0] = (byte) block; map[1] = (byte) ids.length;
			for(int p = 0; p < ids.length; p++) map[2 + p] = (byte) ids[p];
			System.arraycopy(body, 0, map, head, body.length);
			for(int q = head; q < map.length; q++)
				if((map[q] & 0xFF) >= ids.length) throw new java.io.IOException("Block map entry " + (map[q] & 0xFF) + " with " + ids.length + " predictors.");
			return map;
		}
		return readMapForms(in, (previous == null) ? null : n_entries -> previous, mapWidth(delta_type, xdim));
	}

	/**
	 * The previous channel's block map entries lined up with this channel's
	 * blocks: each block takes the entry of the previous channel's block
	 * under its centre (the nearest one at the edges). With the same block
	 * shape that is entry for entry, as before format 5.
	 *
	 * @param previous   the previous channel's map entries
	 * @param prev_block the previous channel's block shape
	 * @param block      this channel's block shape
	 * @param xdim       the row width of the deltas
	 * @param n          the number of this map's entries
	 * @return the entries, one per block of this map
	 */
	static byte[] alignMap(byte[] previous, int prev_block, int block, int xdim, int n)
	{
		if(prev_block == block) return previous;
		int    w = blocksAcross(xdim, block), pw = blocksAcross(xdim, prev_block), ph = previous.length / pw;
		int    bw = blockWidth(block, xdim), bh = blockHeight(block), pbw = blockWidth(prev_block, xdim), pbh = blockHeight(prev_block);
		byte[] out = new byte[n];
		for(int k = 0; k < n; k++)
		{
			int px = Math.min(pw - 1, ((k % w) * bw + bw / 2) / pbw);
			int py = Math.min(ph - 1, ((k / w) * bh + bh / 2) / pbh);
			out[k] = previous[py * pw + px];
		}
		return out;
	}

	/**
	 * Reads the flag byte and one of the four map forms.
	 *
	 * @param in            the stream
	 * @param previous_for  the previous channel's map entries, lined up with
	 *                      this map's n entries (given n), or null
	 * @param width         the map's row width
	 * @return the map entries
	 * @throws java.io.IOException if the stream fails or the form is unknown
	 */
	private static byte[] readMapForms(java.io.DataInputStream in, java.util.function.IntFunction<byte[]> previous_for, int width) throws java.io.IOException
	{
		int coding = in.readByte();
		int n      = in.readInt();
		byte[] previous = (previous_for == null) ? null : previous_for.apply(n);
		if(coding == 3)
		{
			int    K     = in.readShort();
			byte[] coded = new byte[in.readInt()];
			in.readFully(coded);
			int[]  value = new int[n];
			// Each context needs the values before it, so the token before
			// is turned into its value first (the decoder asks in order).
			int[] token = ArithmeticMapper.getArithmeticValuesContext(coded, n, K + 3, NEIGHBOUR_CONTEXTS, (k, t) ->
			{
				if(k > 0) value[k - 1] = neighbourValue(t[k - 1], value, previous, k - 1, width);
				return neighbourContext(value, previous, k, width);
			});
			byte[] map = new byte[n];
			for(int q = 0; q < n; q++)
			{
				if(q == n - 1) value[q] = neighbourValue(token[q], value, previous, q, width);
				map[q] = (byte) value[q];
			}
			return map;
		}
		if(coding == 2)
		{
			int    K     = in.readUnsignedByte();
			byte[] coded = new byte[in.readInt()];
			in.readFully(coded);
			int[] symbol = ArithmeticMapper.getArithmeticValuesContext(coded, n, K, K * K * K * 3,
				(k, s) -> mapContext(s, previous, k, width, K));
			byte[] map = new byte[n];
			for(int q = 0; q < n; q++) map[q] = (byte) symbol[q];
			return map;
		}
		if(coding == 1)
		{
			int   K    = in.readShort();
			int[] freq = new int[256];
			int   only = 0;
			for(int q = 0; q < K; q++) { only = in.readUnsignedByte(); freq[only] = in.readInt(); }
			byte[] coded = new byte[in.readInt()];
			in.readFully(coded);
			if(K > 1) return ArithmeticMapper.getArithmeticValuesFastFenwick(coded, freq, n);
			byte[] map = new byte[n];
			Arrays.fill(map, (byte) only);
			return map;
		}
		int[]  table = readTable(in);
		int    min   = in.readInt();
		int    bits  = in.readInt();
		int    first = in.readUnsignedByte();
		byte[] str   = new byte[StringMapper.getBytelength(bits)];
		in.readFully(str);
		byte[] unpacked = StringMapper.decompressStrings(str);
		int[]  value    = StringMapper.unpackStrings(unpacked, table, n, StringMapper.getBitlength(unpacked));
		byte[] map = new byte[n];
		for(int q = 0; q < n; q++) map[q] = (byte)(value[q] + min);
		map[0] = (byte) first;   // getStringList doesn't keep the first value
		return map;
	}

	/**
	 * Form 0: a map as a unary string (StringMapper).
	 *
	 * @param map the map entries
	 * @return the form's bytes
	 * @throws java.io.IOException never in practice (a byte stream)
	 */
	private static byte[] mapToString(byte[] map) throws java.io.IOException
	{
		int[] value = new int[map.length];
		for(int q = 0; q < map.length; q++) value[q] = map[q] & 0xFF;
		ArrayList list  = StringMapper.getStringList(value, false);
		int[]     table = (int[]) list.get(2);
		byte[]    str   = (byte[]) list.get(3);
		int       bits  = StringMapper.getBitlength(str);
		java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
		java.io.DataOutputStream out = new java.io.DataOutputStream(bytes);
		out.writeInt(map.length); writeTable(out, table); out.writeInt((int) list.get(0)); out.writeInt(bits);
		out.writeByte(map[0]); out.write(str, 0, StringMapper.getBytelength(bits));
		return bytes.toByteArray();
	}

	/**
	 * Row width of a map: one entry per row for the scanline types (9, 10), one
	 * per interior pixel for the frame maps (11, 12). (The block map's width
	 * depends on its shape; see blocksAcross.)
	 *
	 * @param delta_type 9 to 12
	 * @param xdim       the width
	 * @return the row width
	 */
	private static int mapWidth(int delta_type, int xdim)
	{
		return (delta_type <= 10) ? 1 : xdim - 2;
	}

	/**
	 * Context of map entry k: its left and upper neighbours, the entry at the
	 * same place in the previous channel's map, and whether the upper-left
	 * neighbour equals the left one, the upper one, or neither. Measured with
	 * MapTest: 13-24% smaller than the string or arithmetic forms. Missing
	 * neighbours count as 0.
	 *
	 * @param m        the entries
	 * @param previous the previous channel's map entries, or null
	 * @param k        the entry
	 * @param width    the row width
	 * @param K        the number of values
	 * @return the context
	 */
	private static int mapContext(int[] m, byte[] previous, int k, int width, int K)
	{
		int x     = k % width;
		int left  = (x > 0) ? m[k - 1] : 0;
		int up    = (k >= width) ? m[k - width] : 0;
		int ul    = (x > 0 && k >= width) ? m[k - width - 1] : 0;
		int prev  = (previous != null) ? previous[k] & 0xFF : 0;
		int shape = (ul == left) ? 0 : (ul == up) ? 1 : 2;
		return ((left * K + up) * K + prev) * 3 + shape;
	}

	/**
	 * The most values the context form (2) takes.
	 */
	private static final int CONTEXT_MAP_MAX = 32;

	/**
	 * Form 3, the neighbour form: each entry coded as "same as the left entry"
	 * (0), "same as the one above" (1), "same as the previous channel's map here"
	 * (2), or 3 + its value, the first that applies. The context is which of
	 * those neighbours, and the upper-left one, agree with each other. Any number
	 * of values works, so it suits large predictor sets; it measured 0.05-0.3%
	 * smaller than the other forms on block maps (MapIdeaTest). Missing
	 * neighbours never match. This is the number of contexts.
	 */
	private static final int NEIGHBOUR_CONTEXTS = 32;

	/**
	 * A neighbour of entry k: 0 left, 1 above, 2 the previous channel's, 3
	 * above-left.
	 *
	 * @param v        the entries
	 * @param previous the previous channel's map entries, or null
	 * @param k        the entry
	 * @param width    the row width
	 * @param which    0 to 3
	 * @return its value, or -1 if missing
	 */
	private static int neighbour(int[] v, byte[] previous, int k, int width, int which)
	{
		int x = k % width;
		switch(which)
		{
			case 0:  return (x > 0) ? v[k - 1] : -1;
			case 1:  return (k >= width) ? v[k - width] : -1;
			case 2:  return (previous != null) ? previous[k] & 0xFF : -1;
			default: return (x > 0 && k >= width) ? v[k - width - 1] : -1;
		}
	}

	/**
	 * Entry k's context in the neighbour form: which neighbours agree.
	 *
	 * @param v        the entries
	 * @param previous the previous channel's map entries, or null
	 * @param k        the entry
	 * @param width    the row width
	 * @return the context, 0 to NEIGHBOUR_CONTEXTS - 1
	 */
	private static int neighbourContext(int[] v, byte[] previous, int k, int width)
	{
		int L = neighbour(v, previous, k, width, 0), U = neighbour(v, previous, k, width, 1);
		int V = neighbour(v, previous, k, width, 2), D = neighbour(v, previous, k, width, 3);
		int c = 0;
		if(L >= 0 && L == U) c |= 1;
		if(V >= 0 && V == L) c |= 2;
		if(V >= 0 && V == U) c |= 4;
		if(D >= 0 && D == L) c |= 8;
		if(D >= 0 && D == U) c |= 16;
		return c;
	}

	/**
	 * The value a neighbour-form token stands for at entry k.
	 *
	 * @param token    0-2 a neighbour, or 3 + value
	 * @param v        the entries so far
	 * @param previous the previous channel's map entries, or null
	 * @param k        the entry
	 * @param width    the row width
	 * @return the value
	 */
	private static int neighbourValue(int token, int[] v, byte[] previous, int k, int width)
	{
		return (token < 3) ? neighbour(v, previous, k, width, token) : token - 3;
	}

	/**
	 * Form 3: a map in the neighbour form.
	 *
	 * @param map      the map entries
	 * @param previous the previous channel's map entries, or null
	 * @param width    the row width
	 * @return the form's bytes
	 * @throws java.io.IOException never in practice (a byte stream)
	 */
	private static byte[] mapToNeighbours(byte[] map, byte[] previous, int width) throws java.io.IOException
	{
		int n = map.length, K = 1;
		int[] v = new int[n];
		for(int q = 0; q < n; q++) { v[q] = map[q] & 0xFF; K = Math.max(K, v[q] + 1); }
		int[] token = new int[n], context = new int[n];
		for(int k = 0; k < n; k++)
		{
			int s = v[k];
			int L = neighbour(v, previous, k, width, 0), U = neighbour(v, previous, k, width, 1), V = neighbour(v, previous, k, width, 2);
			token[k]   = (L >= 0 && s == L) ? 0 : (U >= 0 && s == U) ? 1 : (V >= 0 && s == V) ? 2 : 3 + s;
			context[k] = neighbourContext(v, previous, k, width);
		}
		byte[] coded = ArithmeticMapper.getIntervalValueContext(token, K + 3, context, NEIGHBOUR_CONTEXTS);
		java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
		java.io.DataOutputStream out = new java.io.DataOutputStream(bytes);
		out.writeInt(n); out.writeShort(K); out.writeInt(coded.length); out.write(coded);
		return bytes.toByteArray();
	}

	/**
	 * Form 2: a map context coded (see {@link #mapContext}).
	 *
	 * @param map      the map entries
	 * @param previous the previous channel's map entries, or null
	 * @param width    the row width
	 * @return the form's bytes
	 * @throws java.io.IOException never in practice (a byte stream)
	 */
	private static byte[] mapToContext(byte[] map, byte[] previous, int width) throws java.io.IOException
	{
		int K = 1;
		for(byte b : map) K = Math.max(K, (b & 0xFF) + 1);
		if(previous != null) for(byte b : previous) K = Math.max(K, (b & 0xFF) + 1);
		// Its K^3 x 3 contexts, each with K counts, are all set up at once
		// (ArithmeticMapper.getIntervalValueContext), so it is only offered
		// for up to CONTEXT_MAP_MAX values; larger sets use the other forms.
		if(K > CONTEXT_MAP_MAX) return null;
		int[] symbol  = new int[map.length];
		for(int q = 0; q < map.length; q++) symbol[q] = map[q] & 0xFF;
		int[] context = new int[map.length];
		for(int q = 0; q < map.length; q++) context[q] = mapContext(symbol, previous, q, width, K);
		byte[] coded = ArithmeticMapper.getIntervalValueContext(symbol, K, context, K * K * K * 3);
		java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
		java.io.DataOutputStream out = new java.io.DataOutputStream(bytes);
		out.writeInt(map.length); out.writeByte(K); out.writeInt(coded.length); out.write(coded);
		return bytes.toByteArray();
	}

	/**
	 * Form 1: a map arithmetic coded with its value counts.
	 *
	 * @param map the map entries
	 * @return the form's bytes
	 * @throws java.io.IOException never in practice (a byte stream)
	 */
	private static byte[] mapToArithmetic(byte[] map) throws java.io.IOException
	{
		int[] freq = new int[256];
		for(byte b : map) freq[b & 0xFF]++;
		int K = 0;
		for(int v : freq) if(v > 0) K++;
		java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
		java.io.DataOutputStream out = new java.io.DataOutputStream(bytes);
		out.writeInt(map.length);
		out.writeShort(K);
		for(int v = 0; v < 256; v++) if(freq[v] > 0) { out.writeByte(v); out.writeInt(freq[v]); }
		if(K <= 1) out.writeInt(0);
		else { byte[] coded = ArithmeticMapper.getIntervalValueFastFenwick(map, freq); out.writeInt(coded.length); out.write(coded); }
		return bytes.toByteArray();
	}

	// =========================================================================
	// Context coding of deltas
	// =========================================================================

	// ---- Context coding of deltas ------------------------------------------
	//
	// For the Context entropy type: the deltas are coded directly, as ranks
	// (most frequent value = 0), with ArithmeticMapper's context coder. The
	// context of a pixel combines how busy its neighbourhood is -- |left| +
	// |above| + |above-left| + |above-right| of the deltas already coded,
	// in 12 buckets -- with the size of the deltas at the same pixel in the
	// channels coded before it (5 buckets), so channels must be decoded in
	// order. Measured with ModelTest: 4-9.5% smaller than the Adaptive type.

	/**
	 * Lower limits of the 12 activity buckets: |left| + |above| + |above-left| + |above-right| of the deltas already coded.
	 */
	private static final int[] ACTIVITY_LIMIT = {0, 1, 2, 3, 5, 7, 10, 14, 20, 28, 40, 60};

	/**
	 * Lower limits of the 5 buckets for the size of the deltas at the same pixel in the channels coded before.
	 */
	private static final int[] CROSS_LIMIT    = {0, 1, 3, 6, 12};

	/**
	 * The number of delta contexts.
	 */
	public  static final int   CONTEXTS       = ACTIVITY_LIMIT.length * CROSS_LIMIT.length;

	/**
	 * Context of pixel k of delta d.
	 *
	 * @param d        the deltas
	 * @param previous the deltas of the channels coded before this one
	 * @param k        the pixel
	 * @param xdim     the row width
	 * @return the context, 0 to CONTEXTS - 1
	 */
	public static int getContext(int[] d, int[][] previous, int k, int xdim)
	{
		int x = k % xdim, activity = 0, cross = 0;
		if(x > 0) activity += Math.abs(d[k - 1]);
		if(k >= xdim)
		{
			activity += Math.abs(d[k - xdim]);
			if(x > 0)        activity += Math.abs(d[k - xdim - 1]);
			if(x < xdim - 1) activity += Math.abs(d[k - xdim + 1]);
		}
		for(int[] p : previous) cross += Math.abs(p[k]);
		return bucket(activity, ACTIVITY_LIMIT) * CROSS_LIMIT.length + bucket(cross, CROSS_LIMIT);
	}

	/**
	 * The bucket a value falls in.
	 *
	 * @param v     a value
	 * @param limit the buckets' lower limits (the first is 0)
	 * @return the last bucket whose lower limit v reaches
	 */
	private static int bucket(int v, int[] limit)
	{
		int b = 0;
		while(b + 1 < limit.length && v >= limit[b + 1]) b++;
		return b;
	}

	/**
	 * A channel's deltas, context coded: int min, the rank table (value -&gt; rank,
	 * see writeTable), int coded length, coded bytes. The number of deltas is not
	 * stored (it is xdim * ydim).
	 *
	 * @param delta    the deltas
	 * @param previous the deltas of the channels coded before this one
	 * @param xdim     the row width
	 * @return the coded bytes
	 * @throws java.io.IOException never in practice (a byte stream)
	 * @see #readContextDeltas
	 */
	public static byte[] packContextDeltas(int[] delta, int[][] previous, int xdim) throws java.io.IOException
	{
		int[] lo   = new int[1];
		int[] rank = rankTable(delta, lo);   // most frequent value first
		int   min  = lo[0];

		int[] symbol  = new int[delta.length];
		int[] context = new int[delta.length];
		for(int k = 0; k < delta.length; k++)
		{
			symbol[k]  = rank[delta[k] - min];
			context[k] = getContext(delta, previous, k, xdim);
		}
		byte[] coded = ArithmeticMapper.getIntervalValueContext(symbol, rank.length, context, CONTEXTS);

		java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
		java.io.DataOutputStream out = new java.io.DataOutputStream(bytes);
		out.writeInt(min);
		writeTable(out, rank);
		out.writeInt(coded.length);
		out.write(coded);
		return bytes.toByteArray();
	}

	/**
	 * Reads deltas {@link #packContextDeltas} wrote.
	 *
	 * @param in       the stream
	 * @param n        the number of deltas
	 * @param previous the deltas of the channels decoded before this one
	 * @param xdim     the row width
	 * @return the deltas
	 * @throws java.io.IOException if the stream fails
	 */
	public static int[] readContextDeltas(java.io.DataInputStream in, int n, int[][] previous, int xdim) throws java.io.IOException
	{
		int    min   = in.readInt();
		int[]  rank  = readTable(in);
		byte[] coded = new byte[in.readInt()];
		in.readFully(coded);

		int[] value = new int[rank.length];
		for(int v = 0; v < rank.length; v++) value[rank[v]] = v + min;

		// Deltas are filled in as the decoder asks for each context.
		int[] delta = new int[n];
		int[] done  = {0};
		int[] symbol = ArithmeticMapper.getArithmeticValuesContext(coded, n, rank.length, CONTEXTS, (k, s) ->
		{
			while(done[0] < k) { delta[done[0]] = value[s[done[0]]]; done[0]++; }
			return getContext(delta, previous, k, xdim);
		});
		for(int k = done[0]; k < n; k++) delta[k] = value[symbol[k]];
		return delta;
	}

	// =========================================================================
	// Mixed context coding (Block format 4)
	//
	// The deltas as ranks (as packContextDeltas), coded with a mixture of
	// adaptive context models. Each model is the context coder's (counts from
	// 1, +CONTEXT_INCREMENT, halved above CONTEXT_LIMIT), with its own context:
	//   0  activity (as getContext) plus CALIC's gradients of this channel's
	//      values, dh + dv, halved; cross as getContext
	//   1  activity plus half the earlier channels' |deltas| to the E, SW, S
	//      and SE (those channels are fully known); cross as getContext
	//   2  activity; cross from the earlier channels' 3x3 neighbourhood, / 3
	// The first channel mixes model 0 and getContext's context (models 1 and
	// 2 would both equal that one).
	// Mixture: C_i(s) = cum_i(s) * 2^MIX_R_BITS / total_i,
	// M(s) = (sum_i w_i C_i(s)) >> MIX_W_BITS + s, out of 2^MIX_R_BITS +
	// the number of symbols (so every symbol has at least 1). The weights are
	// ints summing to 2^MIX_W_BITS, one set per channel, moved after each
	// symbol along the code-length gradient: w_i += w_i (q_i - q) / (q
	// MIX_RATE), at least MIX_WMIN, then rescaled (the remainder to the
	// largest, first on a tie). Cumulative counts by a linear scan from rank
	// 0 (ranks are most frequent first, so the scan is short). Model 0 needs
	// the values decoded so far, so the decoder decodes each delta as it
	// rebuilds the channel (getValuesFromMixedDeltas).
	// Measured on 10 test images: 1.0% smaller than packContextDeltas.
	//
	// With second opinions (Block formats 7 and 8): each model's context is
	// also split 9 ways by how far a simple predictor's value lies from the
	// block's prediction, signed (gradientLevel of opinion - prediction):
	// GAP/8 for model 0, MED for model 1 and a (left) for model 2. Where the
	// two disagree the delta tends to be larger and to take the opinion's
	// sign, which activity alone can't show. Only interior pixels have a
	// block prediction; the rest use the middle level. The decoder has the
	// prediction before it decodes the delta (blockValues). Measured on 10
	// test images (lossless, Color Resolution 3, and Pixel Resolution 4 with
	// Color Resolution 3): 1.95% smaller, 3.06% at the writers' default
	// settings; a weighted predictor (JPEG XL's) as an opinion, or its error
	// as a context, did no better.
	// =========================================================================

	/**
	 * Each model's cumulative counts are scaled to 2<sup>MIX_R_BITS</sup>.
	 */
	static final int MIX_R_BITS = 16;

	/**
	 * The mixing weights sum to 2<sup>MIX_W_BITS</sup>.
	 */
	static final int MIX_W_BITS = 16;

	/**
	 * The smallest a mixing weight can be.
	 */
	static final int MIX_WMIN   = 256;

	/**
	 * The weights move by 1 / MIX_RATE of the code-length gradient per symbol.
	 */
	static final int MIX_RATE   = 32;

	/**
	 * Each opinion's levels: gradientLevel's -4 to 4.
	 */
	static final int MIX_OPINION_LEVELS = 9;

	/**
	 * The activity bucket of each value 0 to 255 (larger values use the last).
	 */
	private static final int[] MIX_ACTIVITY_TABLE = bucketTable(ACTIVITY_LIMIT, 256);

	/**
	 * The cross bucket of each value 0 to 63 (larger values use the last).
	 */
	private static final int[] MIX_CROSS_TABLE    = bucketTable(CROSS_LIMIT, 64);

	/**
	 * The bucket of each value below size.
	 *
	 * @param limit the buckets' lower limits (the first is 0)
	 * @param size  the table size
	 * @return the table
	 */
	private static int[] bucketTable(int[] limit, int size)
	{
		int[] t = new int[size];
		for(int v = 0; v < size; v++) t[v] = bucket(v, limit);
		return t;
	}

	/**
	 * Ranks the delta values by count, most frequent first (ties: smaller value first).
	 *
	 * @param delta the deltas
	 * @param min   set to the smallest delta, in min[0]
	 * @return the rank of each value, indexed by value - min
	 */
	private static int[] rankTable(int[] delta, int[] min)
	{
		int lo = Integer.MAX_VALUE, hi = Integer.MIN_VALUE;
		for(int v : delta) { if(v < lo) lo = v; if(v > hi) hi = v; }
		int[] count = new int[hi - lo + 1];
		for(int v : delta) count[v - lo]++;
		Integer[] order = new Integer[count.length];
		for(int v = 0; v < count.length; v++) order[v] = v;
		Arrays.sort(order, (a, b) -> (count[a] != count[b]) ? count[b] - count[a] : a - b);
		int[] rank = new int[count.length];
		for(int r = 0; r < order.length; r++) rank[order[r]] = r;
		min[0] = lo;
		return rank;
	}

	/**
	 * The mixed coder's models, weights and contexts for one channel; used the
	 * same way to encode and decode.
	 */
	static final class MixedModels
	{
		/**
		 * The number of models: 3, or 2 for the first channel.
		 */
		final int m;
		/**
		 * The number of symbols (ranks).
		 */
		final int n_symbols;
		/**
		 * The row width.
		 */
		final int xdim;
		/**
		 * Counts by model, context and symbol.
		 */
		final int[][][] f;
		/**
		 * Each model's total count per context.
		 */
		final int[][] total;
		/**
		 * The mixing weights (summing to 2<sup>MIX_W_BITS</sup>).
		 */
		final long[] w;
		/**
		 * Each model's context at the current pixel.
		 */
		final int[] ctx = new int[3];
		/**
		 * Each model's scaled count of the symbol just coded.
		 */
		final long[] q;
		/**
		 * Decoding scratch: each model's cumulative count, and its scaled
		 * cumulative count below and through the symbol being tried.
		 */
		final long[] cum, c0, c1;
		/**
		 * Per pixel, from the earlier channels: the cross bucket, the forward sum
		 * (E, SW, S, SE), and the 3x3 cross bucket.
		 */
		final int[] cb, fwd, c9b;
		/**
		 * Whether the contexts include the second opinions (see the section comment).
		 */
		final boolean opinions;
		/**
		 * The block's prediction at the pixel being coded (interior pixels; set
		 * before encode or decode when opinions is true).
		 */
		int prediction;

		/**
		 * Models with every count 1 and equal weights.
		 *
		 * @param n_symbols the number of symbols
		 * @param previous  the deltas of the channels coded before this one
		 * @param xdim      the row width
		 * @param n         the number of deltas
		 */
		MixedModels(int n_symbols, int[][] previous, int xdim, int n)
		{
			this(n_symbols, previous, xdim, n, false);
		}

		/**
		 * As {@link #MixedModels(int, int[][], int, int)}, with or without the
		 * second opinions.
		 *
		 * @param n_symbols the number of symbols
		 * @param previous  the deltas of the channels coded before this one
		 * @param xdim      the row width
		 * @param n         the number of deltas
		 * @param opinions  whether the contexts include the second opinions
		 */
		MixedModels(int n_symbols, int[][] previous, int xdim, int n, boolean opinions)
		{
			this.n_symbols = n_symbols;
			this.xdim      = xdim;
			this.opinions  = opinions;
			int contexts = opinions ? CONTEXTS * MIX_OPINION_LEVELS : CONTEXTS;
			m     = (previous.length == 0) ? 2 : 3;
			f     = new int[m][contexts][n_symbols];
			total = new int[m][contexts];
			for(int[][] fm : f) for(int[] fc : fm) Arrays.fill(fc, 1);
			for(int[] t : total) Arrays.fill(t, n_symbols);
			w = new long[m];
			Arrays.fill(w, (1L << MIX_W_BITS) / m);
			w[0] += (1L << MIX_W_BITS) - (1L << MIX_W_BITS) / m * m;
			q = new long[m];
			cum = new long[m]; c0 = new long[m]; c1 = new long[m];
			cb = new int[n]; fwd = new int[n]; c9b = new int[n];
			if(m == 3) earlierTerms(previous, n / xdim);
		}

		/**
		 * Fills cb, fwd and c9b from the earlier channels' deltas.
		 *
		 * @param previous the deltas of the channels coded before this one
		 * @param ydim     the height
		 */
		private void earlierTerms(int[][] previous, int ydim)
		{
			int   n = xdim * ydim, top = MIX_CROSS_TABLE.length - 1;
			int[] cross = new int[n];
			for(int[] p : previous) for(int k = 0; k < n; k++) cross[k] += Math.abs(p[k]);
			for(int y = 0; y < ydim; y++)
				for(int x = 0; x < xdim; x++)
				{
					int k = y * xdim + x, s9 = 0, sum = 0;
					for(int yy = Math.max(0, y - 1); yy < Math.min(ydim, y + 2); yy++)
						for(int xx = Math.max(0, x - 1); xx < Math.min(xdim, x + 2); xx++) s9 += cross[yy * xdim + xx];
					if(x + 1 < xdim) sum += cross[k + 1];
					if(y + 1 < ydim)
					{
						sum += cross[k + xdim];
						if(x > 0)        sum += cross[k + xdim - 1];
						if(x + 1 < xdim) sum += cross[k + xdim + 1];
					}
					cb[k]  = MIX_CROSS_TABLE[Math.min(cross[k], top)];
					c9b[k] = MIX_CROSS_TABLE[Math.min(s9 / 3, top)];
					fwd[k] = sum;
				}
		}

		/**
		 * Sets each model's context at pixel k.
		 *
		 * @param k the pixel
		 * @param d the deltas (read below k only)
		 * @param v the channel's values (read below k only)
		 */
		void contexts(int k, int[] d, int[] v)
		{
			int x = k % xdim, act = 0, g = 0, top = MIX_ACTIVITY_TABLE.length - 1;
			if(x > 0) act += Math.abs(d[k - 1]);
			if(k >= xdim)
			{
				act += Math.abs(d[k - xdim]);
				if(x > 0)        act += Math.abs(d[k - xdim - 1]);
				if(x < xdim - 1) act += Math.abs(d[k - xdim + 1]);
			}
			if(x >= 2 && k >= 2 * xdim && x < xdim - 1)
			{
				int W = v[k - 1], N = v[k - xdim], NW = v[k - xdim - 1], NE = v[k - xdim + 1];
				g = Math.abs(W - v[k - 2]) + Math.abs(N - NW) + Math.abs(N - NE)
				  + Math.abs(W - NW) + Math.abs(N - v[k - 2 * xdim]) + Math.abs(NE - v[k - 2 * xdim + 1]);
			}
			int c = cb[k];
			ctx[0] = MIX_ACTIVITY_TABLE[Math.min((act + g) >> 1, top)] * CROSS_LIMIT.length + c;
			if(m == 3)
			{
				ctx[1] = MIX_ACTIVITY_TABLE[Math.min(act + (fwd[k] >> 1), top)] * CROSS_LIMIT.length + c;
				ctx[2] = MIX_ACTIVITY_TABLE[Math.min(act, top)] * CROSS_LIMIT.length + c9b[k];
			}
			else ctx[1] = MIX_ACTIVITY_TABLE[Math.min(act, top)] * CROSS_LIMIT.length + c;
			if(opinions)
			{
				int mid = MIX_OPINION_LEVELS / 2, o0 = mid, o1 = mid, o2 = mid;
				if(k >= xdim && x >= 1 && x < xdim - 1)
				{
					int a = v[k - 1], b = v[k - xdim], cc = v[k - xdim - 1], dd = v[k - xdim + 1];
					boolean up2 = k >= 2 * xdim;
					int aa = (x >= 2) ? v[k - 2] : a, bb = up2 ? v[k - 2 * xdim] : b, nne = up2 ? v[k - 2 * xdim + 1] : dd;
					o0 = gradientLevel(gap(a, aa, b, bb, cc, dd, nne, 10, 4, 1) - prediction) + mid;
					o1 = gradientLevel(blockPredictor(11, a, b, cc, dd) - prediction) + mid;
					o2 = gradientLevel(a - prediction) + mid;
				}
				ctx[0] = ctx[0] * MIX_OPINION_LEVELS + o0;
				ctx[1] = ctx[1] * MIX_OPINION_LEVELS + o1;
				if(m == 3) ctx[2] = ctx[2] * MIX_OPINION_LEVELS + o2;
			}
		}

		/**
		 * After coding symbol s (with q set): moves the weights, then counts s.
		 *
		 * @param s the symbol
		 */
		void update(int s)
		{
			long qm = 0;
			for(int j = 0; j < m; j++) qm += w[j] * q[j];
			qm >>= MIX_W_BITS;
			if(qm < 1) qm = 1;
			long t = 0;
			for(int j = 0; j < m; j++)
			{
				w[j] += w[j] * (q[j] - qm) / (qm * MIX_RATE);
				if(w[j] < MIX_WMIN) w[j] = MIX_WMIN;
				t += w[j];
			}
			long t2 = 0;
			int  big = 0;
			for(int j = 0; j < m; j++)
			{
				w[j] = (w[j] << MIX_W_BITS) / t;
				t2  += w[j];
				if(w[j] > w[big]) big = j;
			}
			w[big] += (1L << MIX_W_BITS) - t2;
			for(int j = 0; j < m; j++)
			{
				int[] fc = f[j][ctx[j]];
				fc[s] += ArithmeticMapper.CONTEXT_INCREMENT;
				total[j][ctx[j]] += ArithmeticMapper.CONTEXT_INCREMENT;
				if(total[j][ctx[j]] > ArithmeticMapper.CONTEXT_LIMIT)
				{
					int u = 0;
					for(int i = 0; i < n_symbols; i++) { fc[i] = (fc[i] + 1) >> 1; u += fc[i]; }
					total[j][ctx[j]] = u;
				}
			}
		}

		/**
		 * Codes symbol s at pixel k and updates the models.
		 *
		 * @param enc the encoder
		 * @param k   the pixel
		 * @param s   the symbol
		 * @param d   the deltas
		 * @param v   the channel's values
		 */
		void encode(ArithmeticMapper.RangeEncoder enc, int k, int s, int[] d, int[] v)
		{
			contexts(k, d, v);
			long a = 0, b = 0;
			for(int j = 0; j < m; j++)
			{
				int[] fc = f[j][ctx[j]];
				long  cs = 0;
				for(int i = 0; i < s; i++) cs += fc[i];
				long  t  = total[j][ctx[j]];
				long  c0 = (cs << MIX_R_BITS) / t, c1 = ((cs + fc[s]) << MIX_R_BITS) / t;
				a += w[j] * c0;
				b += w[j] * c1;
				q[j] = c1 - c0;
			}
			long lo = (a >> MIX_W_BITS) + s, hi = (b >> MIX_W_BITS) + s + 1;
			enc.encode(lo, hi - lo, (1L << MIX_R_BITS) + n_symbols);
			update(s);
		}

		/**
		 * Decodes the symbol at pixel k and updates the models.
		 *
		 * @param dec the decoder
		 * @param k   the pixel
		 * @param d   the deltas (filled below k)
		 * @param v   the channel's values (filled below k)
		 * @return the symbol
		 */
		int decode(ArithmeticMapper.RangeDecoder dec, int k, int[] d, int[] v)
		{
			contexts(k, d, v);
			long t  = dec.target((1L << MIX_R_BITS) + n_symbols);
			int  s  = 0;
			long lo = 0, hi;
			Arrays.fill(cum, 0);
			Arrays.fill(c1, 0);
			while(true)
			{
				long a = 0;
				for(int j = 0; j < m; j++)
				{
					c0[j]   = c1[j];
					cum[j] += f[j][ctx[j]][s];
					c1[j]   = (cum[j] << MIX_R_BITS) / total[j][ctx[j]];
					a      += w[j] * c1[j];
				}
				hi = (a >> MIX_W_BITS) + s + 1;
				if(t < hi || s == n_symbols - 1) break;
				lo = hi;
				s++;
			}
			for(int j = 0; j < m; j++) q[j] = c1[j] - c0[j];
			dec.decode(lo, hi - lo);
			update(s);
			return s;
		}
	}

	/**
	 * A channel's deltas, mixed-context coded: int min, the rank table (as
	 * {@link #packContextDeltas}), int coded length, coded bytes.
	 *
	 * @param delta    the deltas
	 * @param values   the channel as the decoder rebuilds it (before any minimum is added back)
	 * @param previous the deltas of the channels coded before this one
	 * @param xdim     the row width
	 * @return the coded bytes
	 * @throws java.io.IOException never in practice (a byte stream)
	 * @see #readMixedDeltas
	 * @see #getValuesFromMixedDeltas
	 */
	public static byte[] packMixedDeltas(int[] delta, int[] values, int[][] previous, int xdim) throws java.io.IOException
	{
		return packMixedDeltas(delta, values, previous, xdim, false);
	}

	/**
	 * As {@link #packMixedDeltas(int[], int[], int[][], int)}, with or without
	 * the second opinions (Block formats 7 and 8; the block's prediction at
	 * each pixel is values - delta).
	 *
	 * @param delta    the deltas
	 * @param values   the channel as the decoder rebuilds it (before any minimum is added back)
	 * @param previous the deltas of the channels coded before this one
	 * @param xdim     the row width
	 * @param opinions whether the contexts include the second opinions
	 * @return the coded bytes
	 * @throws java.io.IOException never in practice (a byte stream)
	 */
	public static byte[] packMixedDeltas(int[] delta, int[] values, int[][] previous, int xdim, boolean opinions) throws java.io.IOException
	{
		int[] min  = new int[1];
		int[] rank = rankTable(delta, min);
		MixedModels models = new MixedModels(rank.length, previous, xdim, delta.length, opinions);
		ArithmeticMapper.RangeEncoder enc = new ArithmeticMapper.RangeEncoder(delta.length / 2);
		for(int k = 0; k < delta.length; k++)
		{
			models.prediction = values[k] - delta[k];
			models.encode(enc, k, rank[delta[k] - min[0]], delta, values);
		}
		byte[] coded = enc.finish();

		java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
		java.io.DataOutputStream out = new java.io.DataOutputStream(bytes);
		out.writeInt(min[0]);
		writeTable(out, rank);
		out.writeInt(coded.length);
		out.write(coded);
		return bytes.toByteArray();
	}

	/**
	 * A channel's mixed-coded deltas as read, before decoding (which needs the
	 * channel's values as they are rebuilt; see {@link #getValuesFromMixedDeltas}).
	 */
	public static final class MixedDeltas
	{
		/**
		 * The delta value of each rank.
		 */
		final int[]  value;
		/**
		 * The coded bytes.
		 */
		final byte[] coded;

		/**
		 * Holds what {@link #readMixedDeltas} read.
		 *
		 * @param value the delta value of each rank
		 * @param coded the coded bytes
		 */
		MixedDeltas(int[] value, byte[] coded) { this.value = value; this.coded = coded; }
	}

	/**
	 * Reads deltas {@link #packMixedDeltas} wrote, without decoding them.
	 *
	 * @param in the stream
	 * @return the rank table and coded bytes
	 * @throws java.io.IOException if the stream fails
	 */
	public static MixedDeltas readMixedDeltas(java.io.DataInputStream in) throws java.io.IOException
	{
		int    min   = in.readInt();
		int[]  rank  = readTable(in);
		byte[] coded = new byte[in.readInt()];
		in.readFully(coded);
		int[] value = new int[rank.length];
		for(int v = 0; v < rank.length; v++) value[rank[v]] = v + min;
		return new MixedDeltas(value, coded);
	}

	/**
	 * Decodes {@link #packMixedDeltas}' bytes given the channel's values (for
	 * checking the coding; a reader rebuilds the values as it decodes, with
	 * {@link #getValuesFromMixedDeltas}).
	 *
	 * @param packed   what packMixedDeltas returned
	 * @param values   the channel's values
	 * @param previous the deltas of the channels coded before this one
	 * @param xdim     the row width
	 * @return the deltas
	 * @throws java.io.IOException if the bytes are short
	 */
	public static int[] unpackMixedDeltas(byte[] packed, int[] values, int[][] previous, int xdim) throws java.io.IOException
	{
		return unpackMixedDeltas(packed, values, previous, xdim, null);
	}

	/**
	 * As {@link #unpackMixedDeltas(byte[], int[], int[][], int)}, for bytes
	 * coded with the second opinions: given the block's prediction at each
	 * pixel (null for bytes coded without them).
	 *
	 * @param packed     what packMixedDeltas returned
	 * @param values     the channel's values
	 * @param previous   the deltas of the channels coded before this one
	 * @param xdim       the row width
	 * @param prediction the block's prediction at each pixel, or null
	 * @return the deltas
	 * @throws java.io.IOException if the bytes are short
	 */
	public static int[] unpackMixedDeltas(byte[] packed, int[] values, int[][] previous, int xdim, int[] prediction) throws java.io.IOException
	{
		MixedDeltas md = readMixedDeltas(new java.io.DataInputStream(new java.io.ByteArrayInputStream(packed)));
		MixedDecoder dec = new MixedDecoder(md, previous, xdim, values.length, prediction != null);
		int[] d = new int[values.length];
		for(int k = 0; k < d.length; k++) dec.next(k, d, values, (prediction != null) ? prediction[k] : 0);
		return d;
	}

	/**
	 * Decodes a channel's mixed-coded deltas one at a time, in order.
	 */
	static final class MixedDecoder
	{
		/**
		 * The models.
		 */
		final MixedModels models;
		/**
		 * The range decoder.
		 */
		final ArithmeticMapper.RangeDecoder dec;
		/**
		 * The delta value of each rank.
		 */
		final int[] value;

		/**
		 * A decoder at the first delta.
		 *
		 * @param md       the rank table and coded bytes
		 * @param previous the deltas of the channels decoded before this one
		 * @param xdim     the row width
		 * @param n        the number of deltas
		 * @param opinions whether the contexts include the second opinions
		 */
		MixedDecoder(MixedDeltas md, int[][] previous, int xdim, int n, boolean opinions)
		{
			models = new MixedModels(md.value.length, previous, xdim, n, opinions);
			dec    = new ArithmeticMapper.RangeDecoder(md.coded);
			value  = md.value;
		}

		/**
		 * Decodes delta k into d[k].
		 *
		 * @param k the pixel (0, 1, 2, ... in turn)
		 * @param d the deltas (filled below k)
		 * @param v the channel's values (filled below k)
		 */
		void next(int k, int[] d, int[] v) { d[k] = value[models.decode(dec, k, d, v)]; }

		/**
		 * As {@link #next(int, int[], int[])}, given the block's prediction at
		 * the pixel (used only with the second opinions, at interior pixels).
		 *
		 * @param k          the pixel (0, 1, 2, ... in turn)
		 * @param d          the deltas (filled below k)
		 * @param v          the channel's values (filled below k)
		 * @param prediction the block's prediction at k
		 */
		void next(int k, int[] d, int[] v, int prediction) { models.prediction = prediction; next(k, d, v); }
	}

	/**
	 * Decodes a channel {@link #packMixedDeltas} coded while rebuilding it from
	 * its block map, as {@link #getValuesFromBlockDeltas} does.
	 *
	 * @param md         the rank table and coded bytes ({@link #readMixedDeltas})
	 * @param previous   the deltas of the channels decoded before this one
	 * @param xdim       the width
	 * @param ydim       the height
	 * @param init_value the first value
	 * @param map        the block map
	 * @param ref        the previous channel's values, as decoded (before any
	 *                   minimum is added back), or null for the first channel
	 * @return {deltas, values}
	 */
	public static int[][] getValuesFromMixedDeltas(MixedDeltas md, int[][] previous, int xdim, int ydim, int init_value, byte[] map, int[] ref)
	{
		return getValuesFromMixedDeltas(md, previous, xdim, ydim, init_value, map, ref, false);
	}

	/**
	 * As {@link #getValuesFromMixedDeltas(MixedDeltas, int[][], int, int, int, byte[], int[])},
	 * for deltas coded with or without the second opinions.
	 *
	 * @param md         the rank table and coded bytes ({@link #readMixedDeltas})
	 * @param previous   the deltas of the channels decoded before this one
	 * @param xdim       the width
	 * @param ydim       the height
	 * @param init_value the first value
	 * @param map        the block map
	 * @param ref        the previous channel's values, as decoded, or null for the first channel
	 * @param opinions   whether the deltas were coded with the second opinions
	 * @return {deltas, values}
	 */
	public static int[][] getValuesFromMixedDeltas(MixedDeltas md, int[][] previous, int xdim, int ydim, int init_value, byte[] map, int[] ref, boolean opinions)
	{
		return getValuesFromMixedDeltas(md, previous, xdim, ydim, init_value, map, ref, opinions, PREDICTOR_TABLE);
	}

	/**
	 * As {@link #getValuesFromMixedDeltas(MixedDeltas, int[][], int, int, int, byte[], int[], boolean)},
	 * with the map's ids in the given table (PREDICTOR_TABLE_256 for older files).
	 *
	 * @param md         the rank table and coded bytes ({@link #readMixedDeltas})
	 * @param previous   the deltas of the channels decoded before this one
	 * @param xdim       the width
	 * @param ydim       the height
	 * @param init_value the first value
	 * @param map        the block map
	 * @param ref        the previous channel's values, as decoded, or null for the first channel
	 * @param opinions   whether the deltas were coded with the second opinions
	 * @param table      the table the map's ids index
	 * @return {deltas, values}
	 */
	public static int[][] getValuesFromMixedDeltas(MixedDeltas md, int[][] previous, int xdim, int ydim, int init_value, byte[] map, int[] ref, boolean opinions, PredictorTable table)
	{
		int[] delta = new int[xdim * ydim];
		int[] values = blockValues(delta, xdim, ydim, init_value, map, ref, new MixedDecoder(md, previous, xdim, delta.length, opinions), table);
		return new int[][] {delta, values};
	}

	// =========================================================================
	// Research: frequency estimates
	// =========================================================================

	/**
	 * A quick estimate of how well a channel predicts: the histogram, over the
	 * interior (rows 1 to ydim - 1, columns 1 to xdim - 2), of each pixel's
	 * difference from whichever of left, above, above-left and above-right is
	 * closest (ties in that order; see {@link #idealDelta}). Used to pick the
	 * channel set.
	 *
	 * @param src  the channel
	 * @param xdim the width
	 * @param ydim the height
	 * @return the count of each delta, from the smallest to the largest (two
	 *         zeros if there is no interior)
	 */
	public static int[] getIdealFrequency2(int src[], int xdim, int ydim)
	{
		// No interior pixels (xdim < 3 or ydim < 2): match what
		// getIdealFrequency returns in that case.
		if(xdim < 3 || ydim < 2)
			return new int[2];

		int src_min = src[0], src_max = src[0];
		for(int v : src)
		{
			if(v < src_min) src_min = v;
			if(v > src_max) src_max = v;
		}
		int span = src_max - src_min;

		int[] count = new int[2 * span + 1];
		for(int i = 1; i < ydim; i++)
		{
			int k = i * xdim + 1;
			for(int j = 1; j < xdim - 1; j++)
			{
				count[idealDelta(src, k, xdim) + span]++;
				k++;
			}
		}

		int lo = 0, hi = count.length - 1;
		while(count[lo] == 0) lo++;
		while(count[hi] == 0) hi--;
		return java.util.Arrays.copyOfRange(count, lo, hi + 1);
	}

	/**
	 * The ideal delta at pixel k: the difference from whichever causal neighbour
	 * (left, above, above-left, above-right) is closest, ties in that order.
	 *
	 * @param src  the channel
	 * @param k    an interior pixel
	 * @param xdim the width
	 * @return that neighbour minus src[k]
	 */
	private static int idealDelta(int src[], int k, int xdim)
	{
		int a = src[k - 1];
		int b = src[k - xdim];
		int c = src[k - xdim - 1];
		int d = src[k - xdim + 1];
		int e = src[k];

		int delta_a = Math.abs(a - e);
		int delta_b = Math.abs(b - e);
		int delta_c = Math.abs(c - e);
		int delta_d = Math.abs(d - e);

		if(delta_a <= delta_b && delta_a <= delta_c && delta_a <= delta_d)
			return a - e;
		else if(delta_b <= delta_c && delta_b <= delta_d)
			return b - e;
		else if(delta_c <= delta_d)
			return c - e;
		else
			return d - e;
	}

	/**
	 * The same histogram as {@link #getIdealFrequency2} (the original version,
	 * which collected the deltas in a list first).
	 *
	 * @param src  the channel
	 * @param xdim the width
	 * @param ydim the height
	 * @return the count of each delta, from the smallest to the largest
	 */
	public static int[] getIdealFrequency(int src[], int xdim, int ydim)
	{
		return getIdealFrequency2(src, xdim, ydim);
	}

	/**
	 * The histogram of the deltas delta type 0-4 gives (horizontal, vertical,
	 * average, MED, directional) over the interior: rows 1 to ydim - 1, columns 1
	 * to xdim - 2.
	 *
	 * @param src        the channel
	 * @param xdim       the width
	 * @param ydim       the height
	 * @param delta_type 0 to 4
	 * @return the count of each delta, from the smallest to the largest
	 */
	public static int[] getFrequency(int[] src, int xdim, int ydim, int delta_type)
	{
		return interiorHistogram((int[]) getDeltas(src, xdim, ydim, delta_type, 0).get(1), xdim, ydim);
	}

	/**
	 * The histogram of deltas over the interior: rows 1 to ydim - 1, columns 1
	 * to xdim - 2.
	 *
	 * @param delta the deltas
	 * @param xdim  the width
	 * @param ydim  the height
	 * @return the count of each delta, from the smallest to the largest
	 */
	private static int[] interiorHistogram(int[] delta, int xdim, int ydim)
	{
		int delta_min = Integer.MAX_VALUE, delta_max = Integer.MIN_VALUE;
		for (int i = 1; i < ydim; i++)
			for (int j = 1; j < xdim - 1; j++)
			{
				int v = delta[i * xdim + j];
				if (v < delta_min) delta_min = v;
				if (v > delta_max) delta_max = v;
			}
		int[] frequency = new int[delta_max - delta_min + 1];
		for (int i = 1; i < ydim; i++)
			for (int j = 1; j < xdim - 1; j++)
				frequency[delta[i * xdim + j] - delta_min]++;
		return frequency;
	}

	/**
	 * The histogram of every delta but the first, those from -255 to 255.
	 *
	 * @param delta the deltas
	 * @return 511 counts, delta + 255
	 */
	private static int[] clippedHistogram(int[] delta)
	{
		int[] frequency = new int[511];
		for (int k = 1; k < delta.length; k++)
		{
			int i = delta[k] + 255;
			if (i >= 0 && i < 511) frequency[i]++;
		}
		return frequency;
	}

	/**
	 * How often each value occurs in a map.
	 *
	 * @param map a map
	 * @param n   the number of values
	 * @return each value's count
	 */
	private static int[] mapHistogram(byte[] map, int n)
	{
		int[] frequency = new int[n];
		for (byte m : map) frequency[m]++;
		return frequency;
	}

	/**
	 * The histogram of {@link #getAdaptiveDeltasFromValues}'s deltas over every
	 * pixel but the first, so it is comparable with the other types' totals.
	 *
	 * @param src  the channel
	 * @param xdim the width
	 * @param ydim the height
	 * @return the count of each delta, from the smallest to the largest
	 */
	public static int[] getAdaptiveFrequency(int[] src, int xdim, int ydim)
	{
		int[] delta = (int[]) getAdaptiveDeltasFromValues(src, xdim, ydim).get(1);
		int mn = Integer.MAX_VALUE, mx = Integer.MIN_VALUE;
		for (int k = 1; k < delta.length; k++) { if (delta[k] < mn) mn = delta[k]; if (delta[k] > mx) mx = delta[k]; }
		int[] freq = new int[mx - mn + 1];
		for (int k = 1; k < delta.length; k++) freq[delta[k] - mn]++;
		return freq;
	}

	/**
	 * The histograms of {@link #getMixedDeltasFromValues} (scanline 1): its
	 * deltas over the interior and its map.
	 *
	 * @param src  the channel
	 * @param xdim the width
	 * @param ydim the height
	 * @return [delta histogram (smallest to largest), predictor counts (4)]
	 */
	public static ArrayList<int[]> getMedScanlineFrequency(int src[], int xdim, int ydim)
	{
		ArrayList deltas = getDeltas(src, xdim, ydim, 6, 0);
		ArrayList<int[]> result = new ArrayList<int[]>();
		result.add(interiorHistogram((int[]) deltas.get(1), xdim, ydim));
		result.add(mapHistogram((byte[]) deltas.get(2), 4));
		return result;
	}

	/**
	 * A frequency estimate for a scanline type with the predictors left, above,
	 * (left + above) / 2 and (left + above-right) / 2, each row choosing by the
	 * Shannon limit of its deltas (as {@link #getMixedDeltasFromValues} does;
	 * scanline 2 chooses by sum of |delta| instead).
	 *
	 * @param src  the channel
	 * @param xdim the width
	 * @param ydim the height
	 * @return [delta histogram (smallest to largest), predictor counts (4)]
	 */
	public static ArrayList<int[]> getScanline2Frequency(int src[], int xdim, int ydim)
	{
		ArrayList<Integer> delta_list = new ArrayList<Integer>();
		byte[] map = new byte[ydim - 1];

		for(int i = 1; i < ydim; i++)
		{
			int[][] delta = new int[4][xdim - 2];

			int k = i * xdim + 1;
			for(int j = 1; j < xdim - 1; j++)
			{
				delta[0][j - 1] = src[k] - src[k - 1];
				delta[1][j - 1] = src[k] - src[k - xdim];
				delta[2][j - 1] = src[k] - (src[k - 1] + src[k - xdim]) / 2;
				delta[3][j - 1] = src[k] - (src[k - 1] + src[k - xdim + 1]) / 2;
				k++;
			}

			int[] limit = new int[4];
			for(int j = 0; j < 4; j++)
			{
				int[] current_delta = delta[j];

				int delta_min = current_delta[0];
				int delta_max = current_delta[0];
				for(k = 1; k < current_delta.length; k++)
				{
					if(current_delta[k] < delta_min) delta_min = current_delta[k];
					else if(current_delta[k] > delta_max) delta_max = current_delta[k];
				}

				for(k = 0; k < current_delta.length; k++)
					current_delta[k] -= delta_min;
				int range = delta_max - delta_min;
				int[] frequency = new int[range + 1];
				for(k = 0; k < current_delta.length; k++)
					frequency[current_delta[k]]++;
				double shannon_limit = CodeMapper.getShannonLimit(frequency);
				limit[j] = (int) Math.floor(shannon_limit);
			}

			int value = limit[0];
			int index = 0;
			for(k = 1; k < 4; k++)
			{
				if(limit[k] < value) { value = limit[k]; index = k; }
			}
			map[i - 1] = (byte) index;
		}

		for(int i = 1; i < ydim; i++)
		{
			int k = i * xdim + 1;
			byte m = map[i - 1];

			if(m == 0)
			{
				for(int j = 1; j < xdim - 1; j++) { delta_list.add(src[k] - src[k - 1]); k++; }
			}
			else if(m == 1)
			{
				for(int j = 1; j < xdim - 1; j++) { delta_list.add(src[k] - src[k - xdim]); k++; }
			}
			else if(m == 2)
			{
				for(int j = 1; j < xdim - 1; j++) { delta_list.add(src[k] - (src[k-1] + src[k-xdim]) / 2); k++; }
			}
			else if(m == 3)
			{
				for(int j = 1; j < xdim - 1; j++) { delta_list.add(src[k] - (src[k-1] + src[k-xdim+1]) / 2); k++; }
			}
		}

		int delta_min = Integer.MAX_VALUE;
		int delta_max = Integer.MIN_VALUE;
		int size = delta_list.size();
		for(int i = 0; i < size; i++)
		{
			int current_delta = delta_list.get(i);
			if(current_delta < delta_min) delta_min = current_delta;
			if(current_delta > delta_max) delta_max = current_delta;
		}

		int range = delta_max - delta_min;
		int[] frequency = new int[range + 1];
		for(int i = 0; i < size; i++)
		{
			int current_value = delta_list.get(i);
			current_value -= delta_min;
			frequency[current_value]++;
		}

		int[] map_frequency = new int[4];
		for(int i = 0; i < map.length; i++)
			map_frequency[map[i]]++;

		ArrayList<int[]> result = new ArrayList<int[]>();
		result.add(frequency);
		result.add(map_frequency);
		return result;
	}

	/**
	 * The histograms of {@link #getMixedDeltasFromValues4} (scanline 3): its
	 * deltas over the interior and its map.
	 *
	 * @param src  the channel
	 * @param xdim the width
	 * @param ydim the height
	 * @return [delta histogram (smallest to largest), predictor counts (4)]
	 */
	public static ArrayList<int[]> getMixedDeltas4Frequency(int src[], int xdim, int ydim)
	{
		ArrayList deltas = getDeltas(src, xdim, ydim, 8, 0);
		ArrayList<int[]> result = new ArrayList<int[]>();
		result.add(interiorHistogram((int[]) deltas.get(1), xdim, ydim));
		result.add(mapHistogram((byte[]) deltas.get(2), 4));
		return result;
	}

	/**
	 * The histograms of {@link #getMixedDeltasFromValues16Rows} (scanline 4): its
	 * deltas (every pixel but the first, those from -255 to 255) and its map.
	 *
	 * @param src  the channel
	 * @param xdim the width
	 * @param ydim the height
	 * @return [delta histogram (511 entries, delta + 255), predictor counts (16)]
	 */
	public static ArrayList<int[]> getMixedDeltas16Frequency(int[] src, int xdim, int ydim)
	{
		ArrayList deltas = getMixedDeltasFromValues16Rows(src, xdim, ydim);
		ArrayList<int[]> result = new ArrayList<>();
		result.add(clippedHistogram((int[]) deltas.get(1)));
		result.add(mapHistogram((byte[]) deltas.get(2), 16));
		return result;
	}

	/**
	 * The histograms of {@link #getMixedDeltasFromValues8Rows} (scanline 5): its
	 * deltas (every pixel but the first, those from -255 to 255) and its map.
	 *
	 * @param src     the channel
	 * @param xdim    the width
	 * @param ydim    the height
	 * @param variant the predictor set (see {@link #FILTER_SETS_8})
	 * @return [delta histogram (511 entries, delta + 255), predictor counts (8)]
	 */
	public static ArrayList<int[]> getMixedDeltas8Frequency(int[] src, int xdim, int ydim, int variant)
	{
		ArrayList deltas = getMixedDeltasFromValues8Rows(src, xdim, ydim, variant);
		ArrayList<int[]> result = new ArrayList<>();
		result.add(clippedHistogram((int[]) deltas.get(1)));
		result.add(mapHistogram((byte[]) deltas.get(2), 8));
		return result;
	}

	/**
	 * A frequency estimate for {@link #getIdealDeltasFromValues8}: for each
	 * interior pixel (including the last row, which that method leaves at
	 * predictor 0), the delta from the closest of its 8 predictors.
	 *
	 * @param src  the channel
	 * @param xdim the width
	 * @param ydim the height
	 * @return [delta histogram (smallest to largest), predictor counts (8)]
	 */
	public static ArrayList<int[]> getIdealFrequency8(int src[], int xdim, int ydim)
	{
		ArrayList<Integer> delta_list = new ArrayList<Integer>();
		int[] map_freq = new int[8];

		for (int i = 1; i < ydim; i++)
		{
			for (int j = 1; j < xdim - 1; j++)
			{
				int k = i * xdim + j;
				int a = src[k - 1];
				int b = src[k - xdim];
				int c = src[k - xdim - 1];
				int d = src[k - xdim + 1];
				int e = src[k];

				int[] pred = {
					a,           //  0: left
					(a + c) / 2, //  1: avg(left, above-left)
					c,           //  2: above-left
					(c + b) / 2, //  3: avg(above-left, above)
					b,           //  4: above
					(b + d) / 2, //  5: avg(above, above-right)
					d,           //  6: above-right
					(d + a) / 2  //  7: avg(above-right, left)
				};

				int best_abs = Integer.MAX_VALUE, best_idx = 0, best_delta = 0;
				for (int n = 0; n < 8; n++)
				{
					int delta     = e - pred[n];
					int abs_delta = Math.abs(delta);
					if (abs_delta < best_abs) { best_abs = abs_delta; best_idx = n; best_delta = delta; }
				}
				delta_list.add(best_delta);
				map_freq[best_idx]++;
			}
		}

		int delta_min = Integer.MAX_VALUE, delta_max = Integer.MIN_VALUE;
		int size = delta_list.size();
		for (int i = 0; i < size; i++)
		{
			int v = delta_list.get(i);
			if (v < delta_min) delta_min = v;
			if (v > delta_max) delta_max = v;
		}
		int[] delta_freq = new int[delta_max - delta_min + 1];
		for (int i = 0; i < size; i++) delta_freq[delta_list.get(i) - delta_min]++;

		ArrayList<int[]> result = new ArrayList<int[]>();
		result.add(delta_freq);
		result.add(map_freq);
		return result;
	}

	/**
	 * A frequency estimate for {@link #getIdealDeltasFromValues16}: for each
	 * interior pixel, the delta from the closest of its 16 predictors.
	 *
	 * @param src  the channel
	 * @param xdim the width
	 * @param ydim the height
	 * @return [delta histogram (smallest to largest), predictor counts (16)]
	 */
	public static ArrayList<int[]> getIdealFrequency16(int src[], int xdim, int ydim)
	{
		ArrayList<Integer> delta_list = new ArrayList<Integer>();
		int[] map_freq = new int[16];

		for (int i = 1; i < ydim; i++)
		{
			for (int j = 1; j < xdim - 1; j++)
			{
				int k = i * xdim + j;
				int a = src[k - 1];
				int b = src[k - xdim];
				int c = src[k - xdim - 1];
				int d = src[k - xdim + 1];
				int e = src[k];

				int med;
				if (c >= Math.max(a, b))      med = Math.min(a, b);
				else if (c <= Math.min(a, b)) med = Math.max(a, b);
				else                          med = a + b - c;

				int[] pred = {
					a, c, b, d,
					(a+c)>>1, (c+b)>>1, (b+d)>>1, (d+a)>>1,
					(a+b)>>1, (c+d)>>1,
					(a+b+c+d)>>2, med,
					(a+b+c)>>2, (a+b+d)>>2, (a+c+d)>>2, (b+c+d)>>2
				};

				int best_abs   = Integer.MAX_VALUE;
				int best_delta = 0;
				int best_n     = 0;
				for (int n = 0; n < 16; n++)
				{
					int delta     = e - pred[n];
					int abs_delta = Math.abs(delta);
					if (abs_delta < best_abs) { best_abs = abs_delta; best_delta = delta; best_n = n; }
				}
				delta_list.add(best_delta);
				map_freq[best_n]++;
			}
		}

		int delta_min = Integer.MAX_VALUE, delta_max = Integer.MIN_VALUE;
		int size = delta_list.size();
		for (int i = 0; i < size; i++)
		{
			int v = delta_list.get(i);
			if (v < delta_min) delta_min = v;
			if (v > delta_max) delta_max = v;
		}
		int[] delta_freq = new int[delta_max - delta_min + 1];
		for (int i = 0; i < size; i++) delta_freq[delta_list.get(i) - delta_min]++;

		ArrayList<int[]> result = new ArrayList<int[]>();
		result.add(delta_freq);
		result.add(map_freq);
		return result;
	}

	// =========================================================================
	// Research: other delta encoders (not delta types)
	// =========================================================================

	/**
	 * Paeth deltas (as in PNG): each interior pixel from whichever of left, above and above-left is closest to left + above - above-left. Not one of the delta types.
	 *
	 * @param src  the channel
	 * @param xdim the width
	 * @param ydim the height
	 * @return [sum of |delta| (Integer), deltas (int[]), the first value (Integer)]
	 */
	public static ArrayList getPaethDeltasFromValues(int src[], int xdim, int ydim)
	{
		int[] dst = new int[xdim * ydim];
		int init_value = src[0];
		// The original src[0] (init_value changes below).
		int original_init_value = src[0];
		int value = init_value;
		int delta = 0;
		int sum = 0;
		int k = 0;

		for(int i = 0; i < ydim; i++)
		{
			if(i == 0)
			{
				for(int j = 0; j < xdim; j++)
				{
					if(j == 0)
						dst[k++] = 0;
					else
					{
						delta = src[k] - value;
						value += delta;
						dst[k++] = delta;
						sum += Math.abs(delta);
					}
				}
			}
			else
			{
				for(int j = 0; j < xdim; j++)
				{
					if(j == 0)
					{
						delta = src[k] - init_value;
						init_value = src[k];
						dst[k++] = delta;
						sum += Math.abs(delta);
					}
					else
					{
						int a = src[k - 1];
						int b = src[k - xdim];
						int c = src[k - xdim - 1];
						int d = a + b - c;

						int delta_a = Math.abs(a - d);
						int delta_b = Math.abs(b - d);
						int delta_c = Math.abs(c - d);

						if(delta_a <= delta_b && delta_a <= delta_c)
							delta = src[k] - src[k - 1];
						else if(delta_b <= delta_c)
							delta = src[k] - src[k - xdim];
						else
							delta = src[k] - src[k - xdim - 1];

						dst[k++] = delta;
						sum += Math.abs(delta);
					}
				}
			}
		}
		ArrayList result = new ArrayList();
		result.add(sum);
		result.add(dst);
		result.add(original_init_value);
		return result;
	}

	/**
	 * Inverse of {@link #getPaethDeltasFromValues}.
	 *
	 * @param src        the deltas
	 * @param xdim       the width
	 * @param ydim       the height
	 * @param init_value the first value
	 * @return the channel
	 */
	public static int[] getValuesFromPaethDeltas(int src[], int xdim, int ydim, int init_value)
	{
		int[] dst = new int[xdim * ydim];
		dst[0] = init_value;
		int value = init_value;

		for(int i = 1; i < xdim; i++)
		{
			value += src[i];
			dst[i] = value;
		}

		for(int i = 1; i < ydim; i++)
		{
			for(int j = 0; j < xdim; j++)
			{
				if(j == 0)
				{
					init_value += src[i * xdim];
					dst[i * xdim] = init_value;
					value = init_value;
				}
				else
				{
					int a = dst[i * xdim + j - 1];
					int b = dst[(i - 1) * xdim + j];
					int c = dst[(i - 1) * xdim + j - 1];
					int d = a + b - c;

					int delta_a = Math.abs(a - d);
					int delta_b = Math.abs(b - d);
					int delta_c = Math.abs(c - d);

					if(delta_a <= delta_b && delta_a <= delta_c)
						dst[i * xdim + j] = a + src[i * xdim + j];
					else if(delta_b <= delta_c)
						dst[i * xdim + j] = b + src[i * xdim + j];
					else
						dst[i * xdim + j] = c + src[i * xdim + j];
				}
			}
		}
		return dst;
	}

	/**
	 * Gradient deltas: from row 2 on, each interior pixel from the left, above, above-left or above-right neighbour, by which of four gradients (reaching two rows up) is largest. Not one of the delta types.
	 *
	 * @param src  the channel
	 * @param xdim the width
	 * @param ydim the height
	 * @return [sum of |delta| (Integer), deltas (int[]), the first value (Integer)]
	 */
	public static ArrayList getGradientDeltasFromValues(int src[], int xdim, int ydim)
	{
		int[] dst        = new int[xdim * ydim];
		int[] gradient   = new int[4];
		int   init_value = src[0];
		// The original src[0] (init_value changes below).
		int   original_init_value = src[0];
		int   sum        = 0;
		int   delta      = 0;
		int   k          = 0;

		dst[k++] = 0;
		for(int i = 1; i < xdim; i++)
		{
			delta = src[k] - src[k - 1];
			dst[k++] = delta;
			sum += Math.abs(delta);
		}

		delta = src[k] - init_value;
		dst[k++] = delta;
		init_value += delta;
		sum += Math.abs(delta);

		for(int i = 1; i < xdim; i++)
		{
			delta = src[k] - src[k - 1];
			dst[k++] = delta;
			sum += Math.abs(delta);
		}

		for(int i = 2; i < ydim; i++)
		{
			delta = src[k] - init_value;
			dst[k++] = delta;
			init_value += delta;
			sum += Math.abs(delta);

			for(int j = 1; j < xdim - 1; j++)
			{
				int a = src[k - 1];
				int b = src[k - xdim];
				int c = src[k - xdim - 1];
				int d = src[k - xdim + 1];
				int e = src[k - 2 * xdim - 1];

				gradient[0] = Math.abs(a - e);
				gradient[1] = Math.abs(c - d);
				gradient[2] = Math.abs(a - d);
				gradient[3] = Math.abs(b - e);

				int max_value = gradient[0];
				int max_index = 0;
				for(int m = 1; m < 4; m++)
				{
					if(gradient[m] > max_value) { max_value = gradient[m]; max_index = m; }
				}

				if(max_index == 0)      delta = src[k] - src[k - 1];
				else if(max_index == 1) delta = src[k] - src[k - xdim];
				else if(max_index == 2) delta = src[k] - src[k - xdim - 1];
				else                    delta = src[k] - src[k - xdim + 1];
				dst[k++] = delta;
				sum += Math.abs(delta);
			}

			delta = src[k] - src[k - 1];
			dst[k++] = delta;
			sum += Math.abs(delta);
		}

		ArrayList result = new ArrayList();
		result.add(sum);
		result.add(dst);
		result.add(original_init_value);
		return result;
	}

	/**
	 * Inverse of {@link #getGradientDeltasFromValues}.
	 *
	 * @param src        the deltas
	 * @param xdim       the width
	 * @param ydim       the height
	 * @param init_value the first value
	 * @return the channel
	 */
	public static int[] getValuesFromGradientDeltas(int src[], int xdim, int ydim, int init_value)
	{
		int[] dst      = new int[xdim * ydim];
		int[] gradient = new int[4];
		int   k        = 0;

		dst[k++] = init_value;
		for(int i = 1; i < xdim; i++) { dst[k] = dst[k-1] + src[k]; k++; }

		init_value += src[k];
		dst[k++] = init_value;
		for(int i = 1; i < xdim; i++) { dst[k] = dst[k-1] + src[k]; k++; }

		for(int i = 2; i < ydim; i++)
		{
			init_value += src[k];
			dst[k++] = init_value;

			for(int j = 1; j < xdim - 1; j++)
			{
				int a = dst[k - 1];
				int b = dst[k - xdim];
				int c = dst[k - xdim - 1];
				int d = dst[k - xdim + 1];
				int e = dst[k - 2 * xdim - 1];

				gradient[0] = Math.abs(a - e);
				gradient[1] = Math.abs(c - d);
				gradient[2] = Math.abs(a - d);
				gradient[3] = Math.abs(b - e);

				int max_value = gradient[0];
				int max_index = 0;
				for(int m = 1; m < 4; m++)
				{
					if(gradient[m] > max_value) { max_value = gradient[m]; max_index = m; }
				}

				if(max_index == 0)      dst[k] = dst[k-1]        + src[k];
				else if(max_index == 1) dst[k] = dst[k-xdim]     + src[k];
				else if(max_index == 2) dst[k] = dst[k-xdim-1]   + src[k];
				else                    dst[k] = dst[k-xdim+1]   + src[k];
				k++;
			}

			dst[k] = dst[k-1] + src[k];
			k++;
		}
		return dst;
	}

	/**
	 * A variant of {@link #getGradientDeltasFromValues} with different gradients (reaching two columns left), from column 2 on. Not one of the delta types.
	 *
	 * @param src  the channel
	 * @param xdim the width
	 * @param ydim the height
	 * @return [sum of |delta| (Integer), deltas (int[]), the first value (Integer)]
	 */
	public static ArrayList getGradientDeltasFromValues2(int src[], int xdim, int ydim)
	{
		int[] dst      = new int[xdim * ydim];
		int[] gradient = new int[4];
		int   init_value = src[0];
		// The original src[0] (init_value changes below).
		int   original_init_value = src[0];
		int   sum = 0;
		int   k   = 0;

		dst[k++] = 0;
		for(int i = 1; i < xdim; i++)
		{
			int delta = src[k] - src[k - 1];
			dst[k++] = delta;
			sum += Math.abs(delta);
		}

		for(int i = 1; i < ydim; i++)
		{
			int delta = src[k] - init_value;
			dst[k++] = delta;
			init_value += delta;
			sum += Math.abs(delta);

			delta = src[k] - src[k - 1];
			dst[k++] = delta;
			sum += Math.abs(delta);

			for(int j = 2; j < xdim - 1; j++)
			{
				int a = src[k - 1];
				int b = src[k - xdim];
				int c = src[k - xdim - 1];
				int d = src[k - xdim + 1];
				int e = src[k - xdim - 2];

				gradient[0] = Math.abs(c - b);
				gradient[1] = Math.abs(c - a);
				gradient[2] = Math.abs(a - b);
				gradient[3] = Math.abs(a - e);

				int max_value = gradient[0];
				int max_index = 0;
				for(int m = 1; m < 4; m++)
				{
					if(gradient[m] > max_value) { max_value = gradient[m]; max_index = m; }
				}

				if(max_index == 0)      delta = src[k] - src[k - 1];
				else if(max_index == 1) delta = src[k] - src[k - xdim];
				else if(max_index == 2) delta = src[k] - src[k - xdim - 1];
				else                    delta = src[k] - src[k - xdim + 1];
				dst[k++] = delta;
				sum += Math.abs(delta);
			}

			delta = src[k] - src[k - 1];
			dst[k++] = delta;
			sum += Math.abs(delta);
		}

		ArrayList result = new ArrayList();
		result.add(sum);
		result.add(dst);
		result.add(original_init_value);
		return result;
	}

	/**
	 * Inverse of {@link #getGradientDeltasFromValues2}.
	 *
	 * @param src        the deltas
	 * @param xdim       the width
	 * @param ydim       the height
	 * @param init_value the first value
	 * @return the channel
	 */
	public static int[] getValuesFromGradientDeltas2(int src[], int xdim, int ydim, int init_value)
	{
		int[] dst      = new int[xdim * ydim];
		int[] gradient = new int[4];
		int   k        = 0;

		dst[k++] = init_value;
		for(int i = 1; i < xdim; i++) { dst[k] = dst[k-1] + src[k]; k++; }

		for(int i = 1; i < ydim; i++)
		{
			init_value += src[k];
			dst[k++] = init_value;
			dst[k]   = dst[k-1] + src[k];
			k++;

			for(int j = 2; j < xdim - 1; j++)
			{
				int a = dst[k - 1];
				int b = dst[k - xdim];
				int c = dst[k - xdim - 1];
				int d = dst[k - xdim + 1];
				int e = dst[k - xdim - 2];

				gradient[0] = Math.abs(c - b);
				gradient[1] = Math.abs(c - a);
				gradient[2] = Math.abs(a - b);
				gradient[3] = Math.abs(a - e);

				int max_value = gradient[0];
				int max_index = 0;
				for(int m = 1; m < 4; m++)
				{
					if(gradient[m] > max_value) { max_value = gradient[m]; max_index = m; }
				}

				if(max_index == 0)      dst[k] = dst[k-1]      + src[k];
				else if(max_index == 1) dst[k] = dst[k-xdim]   + src[k];
				else if(max_index == 2) dst[k] = dst[k-xdim-1] + src[k];
				else                    dst[k] = dst[k-xdim+1] + src[k];
				k++;
			}

			dst[k] = dst[k-1] + src[k];
			k++;
		}
		return dst;
	}

	/**
	 * Two-level scanline deltas: each row picks the two of five predictors (left,
	 * above, above-left, (left + above) / 2, (left + above-right) / 2) with the
	 * smallest sums of |delta| (a line map, 0-9 for the pair), then each pixel
	 * uses the better of the two (a pixel map). Not one of the delta types.
	 *
	 * @param src  the channel
	 * @param xdim the width
	 * @param ydim the height
	 * @return [sum of |delta| (Integer), deltas (int[]), line map (byte[]), pixel
	 *         map (byte[]), the first value (Integer)]
	 */
	public static ArrayList getMixedDeltasFromValues3(int src[], int xdim, int ydim)
	{
		byte[] line_map = new byte[ydim - 1];
		int m = 0;
		for(int i = 1; i < ydim; i++)
		{
			int[] sum = new int[5];

			for(int j = 1; j < xdim - 1; j++)
			{
				int k = i * xdim + j;
				sum[0] += Math.abs(src[k] - src[k - 1]);
				sum[1] += Math.abs(src[k] - src[k - xdim]);
				sum[2] += Math.abs(src[k] - src[k - xdim - 1]);
				sum[3] += Math.abs(src[k] - (src[k-1] + src[k-xdim]) / 2);
				sum[4] += Math.abs(src[k] - (src[k-1] + src[k-xdim+1]) / 2);
			}

			ArrayList key_list = new ArrayList();
			Hashtable<Double, Integer> delta_table = new Hashtable<Double, Integer>();

			double current_key = sum[0];
			double addend = 0.00000001;

			key_list.add(current_key);
			delta_table.put(current_key, 0);
			for(int k = 1; k < 5; k++)
			{
				current_key = sum[k];
				if(key_list.contains(current_key)) { current_key += addend; addend *= 2.; }
				key_list.add(current_key);
				delta_table.put(current_key, k);
			}

			Collections.sort(key_list);

			double first_key  = (double) key_list.get(0);
			int first_type    = delta_table.get(first_key);
			double second_key = (double) key_list.get(1);
			int second_type   = delta_table.get(second_key);

			ArrayList type_list = new ArrayList();
			type_list.add(first_type);
			type_list.add(second_type);

			if     (type_list.contains(0) && type_list.contains(1)) line_map[m++] = (byte) 0;
			else if(type_list.contains(0) && type_list.contains(2)) line_map[m++] = (byte) 1;
			else if(type_list.contains(0) && type_list.contains(3)) line_map[m++] = (byte) 2;
			else if(type_list.contains(0) && type_list.contains(4)) line_map[m++] = (byte) 3;
			else if(type_list.contains(1) && type_list.contains(2)) line_map[m++] = (byte) 4;
			else if(type_list.contains(1) && type_list.contains(3)) line_map[m++] = (byte) 5;
			else if(type_list.contains(1) && type_list.contains(4)) line_map[m++] = (byte) 6;
			else if(type_list.contains(2) && type_list.contains(3)) line_map[m++] = (byte) 7;
			else if(type_list.contains(2) && type_list.contains(4)) line_map[m++] = (byte) 8;
			else                                                      line_map[m++] = (byte) 9;
		}

		byte[] pixel_map = new byte[(xdim - 2) * (ydim - 1)];
		int n = 0;
		for(int i = 1; i < ydim; i++)
		{
			m = line_map[i - 1];
			for(int j = 1; j < xdim - 1; j++)
			{
				int k = i * xdim + j;
				int[] value = new int[2];   // this pixel's error under each of the row's two predictors
				if(m == 0)
				{
					value[0] += Math.abs(src[k] - src[k-1]);
					value[1] += Math.abs(src[k] - src[k-xdim]);
				}
				else if(m == 1)
				{
					value[0] += Math.abs(src[k] - src[k-1]);
					value[1] += Math.abs(src[k] - src[k-xdim-1]);
				}
				else if(m == 2)
				{
					value[0] += Math.abs(src[k] - src[k-1]);
					value[1] += Math.abs(src[k] - (src[k-1]+src[k-xdim])/2);
				}
				else if(m == 3)
				{
					value[0] += Math.abs(src[k] - src[k-1]);
					value[1] += Math.abs(src[k] - (src[k-1]+src[k-xdim+1])/2);
				}
				else if(m == 4)
				{
					value[0] += Math.abs(src[k] - src[k-xdim]);
					value[1] += Math.abs(src[k] - src[k-xdim-1]);
				}
				else if(m == 5)
				{
					value[0] += Math.abs(src[k] - src[k-xdim]);
					value[1] += Math.abs(src[k] - (src[k-1]+src[k-xdim])/2);
				}
				else if(m == 6)
				{
					value[0] += Math.abs(src[k] - src[k-xdim]);
					value[1] += Math.abs(src[k] - (src[k-1]+src[k-xdim+1])/2);
				}
				else if(m == 7)
				{
					value[0] += Math.abs(src[k] - src[k-xdim-1]);
					value[1] += Math.abs(src[k] - (src[k-1]+src[k-xdim])/2);
				}
				else if(m == 8)
				{
					value[0] += Math.abs(src[k] - src[k-xdim-1]);
					value[1] += Math.abs(src[k] - (src[k-1]+src[k-xdim+1])/2);
				}
				else
				{
					value[0] += Math.abs(src[k] - (src[k-1]+src[k-xdim])/2);
					value[1] += Math.abs(src[k] - (src[k-1]+src[k-xdim+1])/2);
				}
				pixel_map[n++] = (value[0] <= value[1]) ? (byte)0 : (byte)1;
			}
		}

		int[] dst      = new int[xdim * ydim];
		int init_value = src[0];
		// The original src[0] (init_value changes below).
		int original_init_value = src[0];
		int value      = init_value;
		int sum        = 0;
		int p          = 0;

		for(int i = 0; i < ydim; i++)
		{
			if(i == 0)
			{
				for(int j = 0; j < xdim; j++)
				{
					if(j == 0) dst[j] = 0;
					else { int delta = src[j] - value; value += delta; dst[j] = delta; }
				}
			}
			else
			{
				int k = i * xdim;
				int delta = src[k] - init_value;
				init_value = src[k];
				dst[k] = delta;
				k++;

				m = line_map[i - 1];

				for(int j = 1; j < xdim - 1; j++)
				{
					n = pixel_map[p++];
					if(m == 0)      delta = (n==0) ? src[k]-src[k-1]   : src[k]-src[k-xdim];
					else if(m == 1) delta = (n==0) ? src[k]-src[k-1]   : src[k]-src[k-xdim-1];
					else if(m == 2) delta = (n==0) ? src[k]-src[k-1]   : src[k]-(src[k-1]+src[k-xdim])/2;
					else if(m == 3) delta = (n==0) ? src[k]-src[k-1]   : src[k]-(src[k-1]+src[k-xdim+1])/2;
					else if(m == 4) delta = (n==0) ? src[k]-src[k-xdim]: src[k]-src[k-xdim-1];
					else if(m == 5) delta = (n==0) ? src[k]-src[k-xdim]: src[k]-(src[k-1]+src[k-xdim])/2;
					else if(m == 6) delta = (n==0) ? src[k]-src[k-xdim]: src[k]-(src[k-1]+src[k-xdim+1])/2;
					else if(m == 7) delta = (n==0) ? src[k]-src[k-xdim-1]: src[k]-(src[k-1]+src[k-xdim])/2;
					else if(m == 8) delta = (n==0) ? src[k]-src[k-xdim-1]: src[k]-(src[k-1]+src[k-xdim+1])/2;
					else            delta = (n==0) ? src[k]-(src[k-1]+src[k-xdim])/2 : src[k]-(src[k-1]+src[k-xdim+1])/2;
					dst[k++] = delta;
					sum += Math.abs(delta);
				}

				delta = src[k] - src[k-1];
				dst[k++] = delta;
				sum += Math.abs(delta);
			}
		}

		ArrayList result = new ArrayList();
		result.add(sum);
		result.add(dst);
		result.add(line_map);
		result.add(pixel_map);
		result.add(original_init_value);
		return result;
	}

	/**
	 * Inverse of {@link #getMixedDeltasFromValues3}.
	 *
	 * @param src        the deltas
	 * @param xdim       the width
	 * @param ydim       the height
	 * @param init_value the first value
	 * @param line_map   each row's pair of predictors
	 * @param pixel_map  each pixel's choice from its row's pair
	 * @return the channel
	 */
	public static int[] getValuesFromMixedDeltas3(int[] src, int xdim, int ydim, int init_value, byte[] line_map, byte[] pixel_map)
	{
		int[] dst = new int[xdim * ydim];
		dst[0] = init_value;
		int value = init_value;

		for(int i = 1; i < xdim; i++) { value += src[i]; dst[i] = value; }

		int p = 0;
		for(int i = 1; i < ydim; i++)
		{
			byte m = line_map[i - 1];
			for(int j = 0; j < xdim; j++)
			{
				int k = i * xdim + j;
				if(j == 0)
				{
					init_value += src[k];
					dst[k] = init_value;
				}
				else if(j < xdim - 1)
				{
					int n = pixel_map[p++];
					if(m == 0)      value = (n==0) ? dst[k-1]     : dst[k-xdim];
					else if(m == 1) value = (n==0) ? dst[k-1]     : dst[k-xdim-1];
					else if(m == 2) value = (n==0) ? dst[k-1]     : (dst[k-1]+dst[k-xdim])/2;
					else if(m == 3) value = (n==0) ? dst[k-1]     : (dst[k-1]+dst[k-xdim+1])/2;
					else if(m == 4) value = (n==0) ? dst[k-xdim]  : dst[k-xdim-1];
					else if(m == 5) value = (n==0) ? dst[k-xdim]  : (dst[k-1]+dst[k-xdim])/2;
					else if(m == 6) value = (n==0) ? dst[k-xdim]  : (dst[k-1]+dst[k-xdim+1])/2;
					else if(m == 7) value = (n==0) ? dst[k-xdim-1]: (dst[k-1]+dst[k-xdim])/2;
					else if(m == 8) value = (n==0) ? dst[k-xdim-1]: (dst[k-1]+dst[k-xdim+1])/2;
					else            value = (n==0) ? (dst[k-1]+dst[k-xdim])/2 : (dst[k-1]+dst[k-xdim+1])/2;
					value += src[k];
					dst[k] = value;
				}
				else
				{
					value = dst[k-1] + src[k];
					dst[k] = value;
				}
			}
		}
		return dst;
	}

	/**
	 * Per-pixel ideal deltas: every pixel from row 1 on uses whichever of left,
	 * above, above-left and above-right is closest (the edge columns from the
	 * ones they have). The map has one entry per pixel from row 1, so it costs
	 * far more than it saves; it measures the best a per-pixel choice could do.
	 *
	 * @param src  the channel
	 * @param xdim the width
	 * @param ydim the height
	 * @return [0 (Integer), deltas (int[]), map (byte[]), the first value
	 *         (Integer)]
	 */
	public static ArrayList getIdealDeltasFromValues(int src[], int xdim, int ydim)
	{
		int[] dst  = new int[xdim * ydim];
		byte[] map = new byte[xdim * (ydim - 1)];

		int init_value = src[0];
		int m = 0;

		for(int i = 1; i < ydim - 1; i++)
		{
			for(int j = 0; j < xdim; j++)
			{
				int k = i * xdim + j;
				if(j == 0)
				{
					map[m++] = (Math.abs(src[k]-src[k-xdim]) <= Math.abs(src[k]-src[k-xdim+1])) ? (byte)1 : (byte)3;
				}
				else if(j < xdim - 1)
				{
					int da = Math.abs(src[k]-src[k-1]);
					int db = Math.abs(src[k]-src[k-xdim]);
					int dc = Math.abs(src[k]-src[k-xdim-1]);
					int dd = Math.abs(src[k]-src[k-xdim+1]);
					if     (da<=db && da<=dc && da<=dd) map[m++] = 0;
					else if(db<=dc && db<=dd)           map[m++] = 1;
					else if(dc<=dd)                     map[m++] = 2;
					else                                map[m++] = 3;
				}
				else
				{
					int da = Math.abs(src[k]-src[k-1]);
					int db = Math.abs(src[k]-src[k-xdim]);
					int dc = Math.abs(src[k]-src[k-xdim-1]);
					if     (da<=db && da<=dc) map[m++] = 0;
					else if(db<=dc)           map[m++] = 1;
					else                      map[m++] = 2;
				}
			}
		}

		int k = xdim * (ydim - 1);
		for(int j = 0; j < xdim; j++)
		{
			if(j == 0)
			{
				map[m++] = (Math.abs(src[k]-src[k-xdim]) <= Math.abs(src[k]-src[k-xdim+1])) ? (byte)1 : (byte)3;
			}
			else if(j < xdim - 1)
			{
				int da = Math.abs(src[k]-src[k-1]);
				int db = Math.abs(src[k]-src[k-xdim]);
				int dc = Math.abs(src[k]-src[k-xdim-1]);
				int dd = Math.abs(src[k]-src[k-xdim+1]);
				if     (da<=db && da<=dc && da<=dd) map[m++] = 0;
				else if(db<=dc && db<=dd)           map[m++] = 1;
				else if(dc<=dd)                     map[m++] = 2;
				else                                map[m++] = 3;
			}
			else
			{
				int da = Math.abs(src[k]-src[k-1]);
				int db = Math.abs(src[k]-src[k-xdim]);
				int dc = Math.abs(src[k]-src[k-xdim-1]);
				if     (da<=db && da<=dc) map[m++] = 0;
				else if(db<=dc)           map[m++] = 1;
				else                      map[m++] = 2;
			}
			k++;
		}

		k = 0; m = 0;
		int delta = 0;
		for(int i = 0; i < ydim; i++)
		{
			if(i == 0)
			{
				for(int j = 0; j < xdim; j++)
				{
					if(j == 0) dst[k++] = delta;
					else { delta = src[k] - src[k-1]; dst[k++] = delta; }
				}
			}
			else
			{
				for(int j = 0; j < xdim; j++)
				{
					int n = map[m++];
					if(n == 0)      delta = src[k] - src[k-1];
					else if(n == 1) delta = src[k] - src[k-xdim];
					else if(n == 2) delta = src[k] - src[k-xdim-1];
					else            delta = src[k] - src[k-xdim+1];
					dst[k++] = delta;
				}
			}
		}

		ArrayList result = new ArrayList();
		result.add(0);
		result.add(dst);
		result.add(map);
		result.add(init_value);
		return result;
	}

	/**
	 * Inverse of {@link #getIdealDeltasFromValues(int[], int, int)}.
	 *
	 * @param src        the deltas
	 * @param xdim       the width
	 * @param ydim       the height
	 * @param init_value the first value
	 * @param map        the map getIdealDeltasFromValues(int[], int, int) returned
	 * @return the channel
	 */
	public static int[] getValuesFromIdealDeltas(int[] src, int xdim, int ydim, int init_value, byte[] map)
	{
		int[] dst = new int[xdim * ydim];
		int k = 0;
		dst[k++] = init_value;

		for(int i = 1; i < xdim; i++) { dst[k] = dst[k-1] + src[k]; k++; }

		int m = 0;
		for(int i = 1; i < ydim; i++)
		{
			for(int j = 0; j < xdim; j++)
			{
				int n = map[m++];
				if(n == 0)      dst[k] = dst[k-1]      + src[k];
				else if(n == 1) dst[k] = dst[k-xdim]   + src[k];
				else if(n == 2) dst[k] = dst[k-xdim-1] + src[k];
				else            dst[k] = dst[k-xdim+1] + src[k];
				k++;
			}
		}
		return dst;
	}

	/**
	 * Predictor sets for {@link #getIdealDeltasFromValues(int[], int, int, int[][])}:
	 * each predictor lists the neighbours it averages (0 left, 1 above, 2
	 * above-left, 3 above-right).
	 */
	public static final int[][] IDEAL_PREDICTORS_2 = { {0}, {1} },
	                            IDEAL_PREDICTORS_3 = { {0}, {1}, {0, 1} },
	                            IDEAL_PREDICTORS_4 = { {0}, {1}, {0, 1}, {0, 3} },
	                            IDEAL_PREDICTORS_5 = { {0}, {1}, {2}, {0, 1}, {0, 3} },
	                            IDEAL_PREDICTORS_6 = { {0}, {0, 2}, {2}, {2, 3}, {3}, {0, 3} };

	/**
	 * A predictor from an ideal-deltas set: one neighbour, or the average (/ 2)
	 * of two.
	 *
	 * @param v         the values
	 * @param k         an interior pixel
	 * @param xdim      the width
	 * @param predictor the neighbours (0 left, 1 above, 2 above-left, 3
	 *                  above-right)
	 * @return the prediction
	 */
	private static int idealPredict(int[] v, int k, int xdim, int[] predictor)
	{
		int[] offset = {1, xdim, xdim + 1, xdim - 1};
		if (predictor.length == 1) return v[k - offset[predictor[0]]];
		return (v[k - offset[predictor[0]]] + v[k - offset[predictor[1]]]) / 2;
	}

	/**
	 * Ideal deltas with any set of neighbour predictors: each interior pixel uses
	 * whichever predictor is closest (ties to the first). Row 0 is horizontal,
	 * column 0 vertical and the last column horizontal. The map has one entry
	 * per interior pixel ((xdim - 2) * (ydim - 1)); the last row's are left at 0.
	 *
	 * @param src        the channel
	 * @param xdim       the width
	 * @param ydim       the height
	 * @param predictors the predictors (see {@link #IDEAL_PREDICTORS_2})
	 * @return [sum of |delta| (Integer), deltas (int[]), map (byte[]), the first
	 *         value (Integer)]
	 */
	public static ArrayList getIdealDeltasFromValues(int src[], int xdim, int ydim, int[][] predictors)
	{
		int[]  dst = new int[xdim * ydim];
		byte[] map = new byte[(xdim - 2) * (ydim - 1)];

		int m = 0;
		for (int i = 1; i < ydim - 1; i++)
		{
			for (int j = 1; j < xdim - 1; j++)
			{
				int k = i * xdim + j, best = 0, best_value = Integer.MAX_VALUE;
				for (int n = 0; n < predictors.length; n++)
				{
					int value = Math.abs(src[k] - idealPredict(src, k, xdim, predictors[n]));
					if (value < best_value) { best_value = value; best = n; }
				}
				map[m++] = (byte) best;
			}
		}

		int k = 0, sum = 0;
		m = 0;
		for (int i = 0; i < ydim; i++)
		{
			if (i == 0)
			{
				dst[k++] = 0;
				for (int j = 1; j < xdim; j++) { int delta = src[k] - src[k - 1]; dst[k++] = delta; sum += Math.abs(delta); }
			}
			else
			{
				int delta = src[k] - src[k - xdim]; dst[k++] = delta; sum += Math.abs(delta);
				for (int j = 1; j < xdim - 1; j++)
				{
					delta = src[k] - idealPredict(src, k, xdim, predictors[map[m++]]);
					dst[k++] = delta; sum += Math.abs(delta);
				}
				delta = src[k] - src[k - 1]; dst[k++] = delta; sum += Math.abs(delta);
			}
		}

		ArrayList result = new ArrayList();
		result.add(sum); result.add(dst); result.add(map); result.add(src[0]);
		return result;
	}

	/**
	 * Inverse of {@link #getIdealDeltasFromValues(int[], int, int, int[][])}.
	 *
	 * @param src        the deltas
	 * @param xdim       the width
	 * @param ydim       the height
	 * @param init_value the first value
	 * @param map        the map it returned
	 * @param predictors the predictors it used
	 * @return the channel
	 */
	public static int[] getValuesFromIdealDeltas(int[] src, int xdim, int ydim, int init_value, byte[] map, int[][] predictors)
	{
		int[] dst = new int[xdim * ydim];
		int k = 0;
		dst[k++] = init_value;
		for (int i = 1; i < xdim; i++) { dst[k] = dst[k - 1] + src[k]; k++; }

		int m = 0;
		for (int i = 1; i < ydim; i++)
		{
			dst[k] = dst[k - xdim] + src[k]; k++;
			for (int j = 1; j < xdim - 1; j++)
			{
				dst[k] = idealPredict(dst, k, xdim, predictors[map[m++]]) + src[k];
				k++;
			}
			dst[k] = dst[k - 1] + src[k]; k++;
		}
		return dst;
	}

	/**
	 * {@link #getIdealDeltasFromValues(int[], int, int, int[][])} with
	 * {@link #IDEAL_PREDICTORS_2}: left and above.
	 *
	 * @param src  the channel
	 * @param xdim the width
	 * @param ydim the height
	 * @return [sum of |delta| (Integer), deltas (int[]), map (byte[]), the first
	 *         value (Integer)]
	 */
	public static ArrayList getIdealDeltasFromValues2(int src[], int xdim, int ydim)
	{
		return getIdealDeltasFromValues(src, xdim, ydim, IDEAL_PREDICTORS_2);
	}

	/**
	 * Inverse of {@link #getIdealDeltasFromValues2}.
	 *
	 * @param src        the deltas
	 * @param xdim       the width
	 * @param ydim       the height
	 * @param init_value the first value
	 * @param map        the map it returned
	 * @return the channel
	 */
	public static int[] getValuesFromIdealDeltas2(int[] src, int xdim, int ydim, int init_value, byte[] map)
	{
		return getValuesFromIdealDeltas(src, xdim, ydim, init_value, map, IDEAL_PREDICTORS_2);
	}

	/**
	 * {@link #getIdealDeltasFromValues(int[], int, int, int[][])} with
	 * {@link #IDEAL_PREDICTORS_3}: left, above and (left + above) / 2.
	 *
	 * @param src  the channel
	 * @param xdim the width
	 * @param ydim the height
	 * @return [sum of |delta| (Integer), deltas (int[]), map (byte[]), the first
	 *         value (Integer)]
	 */
	public static ArrayList getIdealDeltasFromValues3(int src[], int xdim, int ydim)
	{
		return getIdealDeltasFromValues(src, xdim, ydim, IDEAL_PREDICTORS_3);
	}

	/**
	 * Inverse of {@link #getIdealDeltasFromValues3}.
	 *
	 * @param src        the deltas
	 * @param xdim       the width
	 * @param ydim       the height
	 * @param init_value the first value
	 * @param map        the map it returned
	 * @return the channel
	 */
	public static int[] getValuesFromIdealDeltas3(int[] src, int xdim, int ydim, int init_value, byte[] map)
	{
		return getValuesFromIdealDeltas(src, xdim, ydim, init_value, map, IDEAL_PREDICTORS_3);
	}

	/**
	 * {@link #getIdealDeltasFromValues(int[], int, int, int[][])} with
	 * {@link #IDEAL_PREDICTORS_4}: left, above, (left + above) / 2 and (left + above-right) / 2.
	 *
	 * @param src  the channel
	 * @param xdim the width
	 * @param ydim the height
	 * @return [sum of |delta| (Integer), deltas (int[]), map (byte[]), the first
	 *         value (Integer)]
	 */
	public static ArrayList getIdealDeltasFromValues4(int src[], int xdim, int ydim)
	{
		return getIdealDeltasFromValues(src, xdim, ydim, IDEAL_PREDICTORS_4);
	}

	/**
	 * Inverse of {@link #getIdealDeltasFromValues4}.
	 *
	 * @param src        the deltas
	 * @param xdim       the width
	 * @param ydim       the height
	 * @param init_value the first value
	 * @param map        the map it returned
	 * @return the channel
	 */
	public static int[] getValuesFromIdealDeltas4(int[] src, int xdim, int ydim, int init_value, byte[] map)
	{
		return getValuesFromIdealDeltas(src, xdim, ydim, init_value, map, IDEAL_PREDICTORS_4);
	}

	/**
	 * {@link #getIdealDeltasFromValues(int[], int, int, int[][])} with
	 * {@link #IDEAL_PREDICTORS_5}: left, above, above-left, (left + above) / 2 and (left + above-right) / 2.
	 *
	 * @param src  the channel
	 * @param xdim the width
	 * @param ydim the height
	 * @return [sum of |delta| (Integer), deltas (int[]), map (byte[]), the first
	 *         value (Integer)]
	 */
	public static ArrayList getIdealDeltasFromValues5(int src[], int xdim, int ydim)
	{
		return getIdealDeltasFromValues(src, xdim, ydim, IDEAL_PREDICTORS_5);
	}

	/**
	 * Inverse of {@link #getIdealDeltasFromValues5}.
	 *
	 * @param src        the deltas
	 * @param xdim       the width
	 * @param ydim       the height
	 * @param init_value the first value
	 * @param map        the map it returned
	 * @return the channel
	 */
	public static int[] getValuesFromIdealDeltas5(int[] src, int xdim, int ydim, int init_value, byte[] map)
	{
		return getValuesFromIdealDeltas(src, xdim, ydim, init_value, map, IDEAL_PREDICTORS_5);
	}

	/**
	 * {@link #getIdealDeltasFromValues(int[], int, int, int[][])} with
	 * {@link #IDEAL_PREDICTORS_6}: left, (left + above-left) / 2, above-left, (above-left + above-right) / 2, above-right and (left + above-right) / 2.
	 *
	 * @param src  the channel
	 * @param xdim the width
	 * @param ydim the height
	 * @return [sum of |delta| (Integer), deltas (int[]), map (byte[]), the first
	 *         value (Integer)]
	 */
	public static ArrayList getIdealDeltasFromValues6(int src[], int xdim, int ydim)
	{
		return getIdealDeltasFromValues(src, xdim, ydim, IDEAL_PREDICTORS_6);
	}

	/**
	 * Inverse of {@link #getIdealDeltasFromValues6}.
	 *
	 * @param src        the deltas
	 * @param xdim       the width
	 * @param ydim       the height
	 * @param init_value the first value
	 * @param map        the map it returned
	 * @return the channel
	 */
	public static int[] getValuesFromIdealDeltas6(int[] src, int xdim, int ydim, int init_value, byte[] map)
	{
		return getValuesFromIdealDeltas(src, xdim, ydim, init_value, map, IDEAL_PREDICTORS_6);
	}

	// =========================================================================
	// Research: Huffman coding of maps
	// =========================================================================

	/**
	 * Compact Huffman coding of a map whose values are 0 to n_sym - 1 (n_sym a
	 * power of 2: 4, 8 or 16), with canonical codes.
	 * <p>
	 * Output: n_sym code lengths, 4 bits each (two per byte, low nibble first),
	 * then the codes most significant bit first, zero-padded to a byte. The caller
	 * stores bit_count_out[0] (the payload bits) so the decoder knows where the
	 * codes end.
	 *
	 * @param map           the map
	 * @param n_sym         the number of values (4, 8 or 16)
	 * @param bit_count_out set to {payload bits}
	 * @return the coded map
	 * @see #decodeMapHuffman
	 */
	public static byte[] encodeMapHuffman(byte[] map, int n_sym, int[] bit_count_out)
	{
		// Count symbol frequencies.
		int[] freq = new int[n_sym];
		for (byte b : map) freq[b & 0xFF]++;

		// Build Huffman code lengths.
		int[] len = huffmanLengths(freq, n_sym);

		// Find max code length.
		int max_len = 0;
		for (int l : len) if (l > max_len) max_len = l;

		// Assign canonical codes.
		int[] codes = huffmanCodes(len, n_sym, max_len);

		// Count total payload bits.
		int total_bits = 0;
		for (int s = 0; s < n_sym; s++) total_bits += freq[s] * len[s];
		bit_count_out[0] = total_bits;

		// Build output: header nibbles + bit stream.
		int    header = n_sym / 2;
		byte[] out    = new byte[header + (total_bits + 7) / 8];

		// Write code lengths as 4-bit nibbles (low nibble first).
		for (int s = 0; s < n_sym; s++)
			out[s >> 1] |= (len[s] & 0xF) << ((s & 1) << 2);

		// Write Huffman bit stream (MSB first within each byte).
		int bit_pos = 0;
		for (byte b : map)
		{
			int s    = b & 0xFF;
			int code = codes[s];
			int clen = len[s];
			for (int bit = clen - 1; bit >= 0; bit--)
			{
				if (((code >> bit) & 1) == 1)
					out[header + (bit_pos >> 3)] |= 1 << (7 - (bit_pos & 7));
				bit_pos++;
			}
		}

		return out;
	}

	/**
	 * Decodes {@link #encodeMapHuffman}'s output.
	 *
	 * @param encoded    the coded map
	 * @param n_sym      the number of values
	 * @param map_length the number of entries
	 * @param bit_count  the payload bits
	 * @return the map
	 */
	public static byte[] decodeMapHuffman(byte[] encoded, int n_sym, int map_length, int bit_count)
	{
		int header = n_sym / 2;

		// Read code lengths from nibbles.
		int[] len = new int[n_sym];
		for (int s = 0; s < n_sym; s++)
			len[s] = (encoded[s >> 1] >> ((s & 1) << 2)) & 0xF;

		// Find max code length and rebuild canonical codes.
		int max_len = 0;
		for (int l : len) if (l > max_len) max_len = l;
		int[] codes = huffmanCodes(len, n_sym, max_len);

		// Decode bit stream.
		byte[] map    = new byte[map_length];
		int    bit_pos = 0;

		for (int q = 0; q < map_length; q++)
		{
			int acc = 0, acc_len = 0;
			outer:
			while (true)
			{
				int byte_idx = header + (bit_pos >> 3);
				acc     = (acc << 1) | ((encoded[byte_idx] >> (7 - (bit_pos & 7))) & 1);
				acc_len++;
				bit_pos++;
				for (int s = 0; s < n_sym; s++)
					if (len[s] == acc_len && codes[s] == acc)
						{ map[q] = (byte) s; break outer; }
			}
		}

		return map;
	}

	/**
	 * Huffman code lengths from counts, by repeatedly joining the two smallest.
	 *
	 * @param freq  each value's count
	 * @param n_sym the number of values
	 * @return each value's code length (0 for unused values; 1 if only one is used)
	 */
	private static int[] huffmanLengths(int[] freq, int n_sym)
	{
		int[] len  = new int[n_sym];
		int   used = 0;
		for (int f : freq) if (f > 0) used++;

		if (used <= 1)
		{
			for (int s = 0; s < n_sym; s++) if (freq[s] > 0) { len[s] = 1; break; }
			return len;
		}

		// Nodes 0..n_sym-1 are leaves; n_sym.. are internal.
		long[]    node_freq = new long[2 * n_sym];
		int[]     parent    = new int  [2 * n_sym];
		boolean[] active    = new boolean[2 * n_sym];

		for (int s = 0; s < n_sym; s++)
		{
			node_freq[s] = freq[s];
			active[s]    = freq[s] > 0;
			parent[s]    = -1;
		}

		int next = n_sym;
		while (true)
		{
			int m1 = -1, m2 = -1;
			for (int n = 0; n < next; n++)
			{
				if (!active[n]) continue;
				if (m1 == -1 || node_freq[n] < node_freq[m1]) { m2 = m1; m1 = n; }
				else if (m2 == -1 || node_freq[n] < node_freq[m2]) m2 = n;
			}
			if (m2 == -1) break;

			node_freq[next] = node_freq[m1] + node_freq[m2];
			parent[m1]  = next; active[m1]   = false;
			parent[m2]  = next; active[m2]   = false;
			active[next] = true; parent[next] = -1;
			next++;
		}

		int root = next - 1;
		for (int s = 0; s < n_sym; s++)
		{
			if (freq[s] == 0) continue;
			int depth = 0, node = s;
			while (node != root) { depth++; node = parent[node]; }
			len[s] = depth;
		}
		return len;
	}

	/**
	 * Canonical Huffman codes from code lengths, values in order within a length.
	 *
	 * @param len     each value's code length
	 * @param n_sym   the number of values
	 * @param max_len the longest length
	 * @return each value's code
	 */
	private static int[] huffmanCodes(int[] len, int n_sym, int max_len)
	{
		if (max_len == 0) return new int[n_sym];

		// Count symbols at each length.
		int[] bl_count = new int[max_len + 1];
		for (int s = 0; s < n_sym; s++) bl_count[len[s]]++;
		bl_count[0] = 0;

		// First code at each length (standard canonical assignment).
		int[] next_code = new int[max_len + 2];
		int   code      = 0;
		for (int bits = 1; bits <= max_len; bits++)
		{
			code = (code + bl_count[bits - 1]) << 1;
			next_code[bits] = code;
		}

		// Assign codes to symbols in order (canonical: sorted by symbol index).
		int[] codes = new int[n_sym];
		for (int s = 0; s < n_sym; s++)
			if (len[s] > 0) codes[s] = next_code[len[s]]++;

		return codes;
	}

	// =========================================================================
	// Research: smoothing
	// =========================================================================

	/**
	 * Bilateral smoothing: edge-preserving noise reduction (a 5x5 kernel, spatial
	 * sigma 1.5, range sigma threshold<sup>2</sup>).
	 *
	 * @param src       the channel (values 0-255)
	 * @param xdim      the width
	 * @param ydim      the height
	 * @param threshold the strength; 0 returns a copy
	 * @return the smoothed channel
	 */
	public static int[] bilateralSmooth(int[] src, int xdim, int ydim, int threshold)
	{
		if (threshold == 0) return src.clone();

		double sigma_r = threshold * threshold;  // quadratic: 1,4,9,16,25 for threshold 1-5
		double sigma_s = 1.5;                // spatial sigma (fixed, 5x5 kernel)
		int    radius  = 2;

		// Range weight lookup: indexed by absolute intensity difference 0-255
		double[] rw = new double[256];
		double   r2 = 2.0 * sigma_r * sigma_r;
		for (int d = 0; d < 256; d++) rw[d] = Math.exp(-(d * d) / r2);

		// Spatial weight kernel
		int    ksize = 2 * radius + 1;
		double[][]  sw = new double[ksize][ksize];
		double s2 = 2.0 * sigma_s * sigma_s;
		for (int dy = -radius; dy <= radius; dy++)
			for (int dx = -radius; dx <= radius; dx++)
				sw[dy + radius][dx + radius] = Math.exp(-(dx * dx + dy * dy) / s2);

		int[] dst = new int[src.length];
		for (int row = 0; row < ydim; row++)
		{
			for (int col = 0; col < xdim; col++)
			{
				int    center = src[row * xdim + col];
				double sum_w  = 0.0, sum_v = 0.0;
				for (int dy = -radius; dy <= radius; dy++)
				{
					int ny = row + dy;
					if (ny < 0 || ny >= ydim) continue;
					for (int dx = -radius; dx <= radius; dx++)
					{
						int nx = col + dx;
						if (nx < 0 || nx >= xdim) continue;
						int    v = src[ny * xdim + nx];
						double w = sw[dy + radius][dx + radius] * rw[Math.abs(v - center)];
						sum_w += w; sum_v += w * v;
					}
				}
				dst[row * xdim + col] = (int) Math.round(sum_v / sum_w);
			}
		}
		return dst;
	}

	/**
	 * Anisotropic diffusion (Perona-Malik): iterative edge-preserving smoothing,
	 * threshold iterations with K = 3 * threshold + 5 and lambda 0.25 (the
	 * stability limit for four directions).
	 *
	 * @param src       the channel
	 * @param xdim      the width
	 * @param ydim      the height
	 * @param threshold the strength; 0 returns a copy
	 * @return the smoothed channel
	 */
	public static int[] anisotropicSmooth(int[] src, int xdim, int ydim, int threshold)
	{
		if (threshold == 0) return src.clone();

		int    iterations = threshold;
		double K2         = (threshold * 3.0 + 5.0) * (threshold * 3.0 + 5.0);
		double lambda     = 0.25;

		// Conductance lookup: c[d+255] = exp(-d^2/K^2) for d in [-255,255]
		double[] c = new double[511];
		for (int d = -255; d <= 255; d++) c[d + 255] = Math.exp(-(d * d) / K2);

		double[] img  = new double[src.length];
		double[] next = new double[src.length];
		for (int i = 0; i < src.length; i++) img[i] = src[i];

		for (int iter = 0; iter < iterations; iter++)
		{
			for (int row = 0; row < ydim; row++)
			{
				for (int col = 0; col < xdim; col++)
				{
					int    k  = row * xdim + col;
					double v  = img[k];
					double dN = (row > 0)        ? img[k - xdim] - v : 0;
					double dS = (row < ydim - 1) ? img[k + xdim] - v : 0;
					double dE = (col < xdim - 1) ? img[k + 1]    - v : 0;
					double dW = (col > 0)        ? img[k - 1]    - v : 0;
					int iN = Math.max(0, Math.min(510, (int)(dN + 255.5)));
					int iS = Math.max(0, Math.min(510, (int)(dS + 255.5)));
					int iE = Math.max(0, Math.min(510, (int)(dE + 255.5)));
					int iW = Math.max(0, Math.min(510, (int)(dW + 255.5)));
					next[k] = v + lambda * (c[iN]*dN + c[iS]*dS + c[iE]*dE + c[iW]*dW);
				}
			}
			double[] tmp = img; img = next; next = tmp;
		}

		int[] dst = new int[src.length];
		for (int i = 0; i < src.length; i++)
			dst[i] = Math.max(0, Math.min(255, (int) Math.round(img[i])));
		return dst;
	}

	// =========================================================================
	// Research: neighbour lists and locations
	// =========================================================================

	/**
	 * For each pixel, the differences from each of its neighbours (8 inside, 5 on
	 * an edge, 3 in a corner), sorted by size, with each neighbour's location
	 * (0-7, see {@link #getNeighborIndex}).
	 *
	 * @param src  the channel
	 * @param xdim the width
	 * @param ydim the height
	 * @return one int[size][2] table per pixel: {value, location}, smallest first
	 */
	public static ArrayList getDeltaListFromValues(int src[], int xdim, int ydim)
	{
		ArrayList delta_list = new ArrayList();

		int k = 0;
		for(int i = 0; i < ydim; i++)
		{
			for(int j = 0; j < xdim; j++)
			{
				int size;
				if(i == 0 || i == ydim - 1)
					size = (j == 0 || j == xdim - 1) ? 3 : 5;
				else
					size = (j == 0 || j == xdim - 1) ? 5 : 8;

				int[] value    = new int[size];
				int[] location = new int[size];

				if(i == 0)
				{
					if(j == 0)
					{
						value[0]=src[k]-src[k+1];       location[0]=4;
						value[1]=src[k]-src[k+xdim];    location[1]=6;
						value[2]=src[k]-src[k+xdim+1];  location[2]=7;
					}
					else if(j == xdim - 1)
					{
						value[0]=src[k]-src[k-1];       location[0]=3;
						value[1]=src[k]-src[k+xdim-1];  location[1]=5;
						value[2]=src[k]-src[k+xdim];    location[2]=6;
					}
					else
					{
						value[0]=src[k]-src[k-1];       location[0]=3;
						value[1]=src[k]-src[k+1];       location[1]=4;
						value[2]=src[k]-src[k+xdim-1];  location[2]=5;
						value[3]=src[k]-src[k+xdim];    location[3]=6;
						value[4]=src[k]-src[k+xdim+1];  location[4]=7;
					}
				}
				else if(i == ydim - 1)
				{
					if(j == 0)
					{
						value[0]=src[k]-src[k-xdim];    location[0]=1;
						value[1]=src[k]-src[k-xdim+1];  location[1]=2;
						value[2]=src[k]-src[k+1];       location[2]=4;
					}
					else if(j == xdim - 1)
					{
						value[0]=src[k]-src[k-xdim-1];  location[0]=0;
						value[1]=src[k]-src[k-xdim];    location[1]=1;
						value[2]=src[k]-src[k-1];       location[2]=3;
					}
					else
					{
						value[0]=src[k]-src[k-xdim-1];  location[0]=0;
						value[1]=src[k]-src[k-xdim];    location[1]=1;
						value[2]=src[k]-src[k-xdim+1];  location[2]=2;
						value[3]=src[k]-src[k-1];       location[3]=3;
						value[4]=src[k]-src[k+1];       location[4]=4;
					}
				}
				else
				{
					if(j == 0)
					{
						value[0]=src[k]-src[k-xdim];    location[0]=1;
						value[1]=src[k]-src[k-xdim+1];  location[1]=2;
						value[2]=src[k]-src[k+1];       location[2]=4;
						value[3]=src[k]-src[k+xdim];    location[3]=6;
						value[4]=src[k]-src[k+xdim+1];  location[4]=7;
					}
					else if(j == xdim - 1)
					{
						value[0]=src[k]-src[k-xdim-1];  location[0]=0;
						value[1]=src[k]-src[k-xdim];    location[1]=1;
						value[2]=src[k]-src[k-1];       location[2]=3;
						value[3]=src[k]-src[k+xdim-1];  location[3]=5;
						value[4]=src[k]-src[k+xdim];    location[4]=6;
					}
					else
					{
						value[0]=src[k]-src[k-xdim-1];  location[0]=0;
						value[1]=src[k]-src[k-xdim];    location[1]=1;
						value[2]=src[k]-src[k-xdim+1];  location[2]=2;
						value[3]=src[k]-src[k-1];       location[3]=3;
						value[4]=src[k]-src[k+1];       location[4]=4;
						value[5]=src[k]-src[k+xdim-1];  location[5]=5;
						value[6]=src[k]-src[k+xdim];    location[6]=6;
						value[7]=src[k]-src[k+xdim+1];  location[7]=7;
					}
				}

				double[] delta = new double[size];
				for(int m = 0; m < size; m++) delta[m] = Math.abs(value[m]);

				double addend = 0.00000001;
				Hashtable<Double, ArrayList> delta_table = new Hashtable<Double, ArrayList>();
				ArrayList key_list = new ArrayList();

				for(int m = 0; m < size; m++)
				{
					if(key_list.contains(delta[m])) { delta[m] += addend; addend *= 2; }
					key_list.add(delta[m]);
					ArrayList list = new ArrayList();
					list.add(value[m]); list.add(location[m]);
					delta_table.put(delta[m], list);
				}

				Collections.sort(key_list);
				int[][] table = new int[size][2];
				for(int m = 0; m < size; m++)
				{
					double key = (double) key_list.get(m);
					ArrayList current = delta_table.get(key);
					table[m][0] = (int) current.get(0);
					table[m][1] = (int) current.get(1);
				}

				delta_list.add(table);
				k++;
			}
		}

		return delta_list;
	}

	/**
	 * Each pixel's smallest difference, from {@link #getDeltaListFromValues}.
	 *
	 * @param delta_list its result
	 * @return one value per pixel
	 */
	public static int[] getIdealDeltasFromList(ArrayList delta_list)
	{
		int[] ideal_delta = new int[delta_list.size()];
		for(int i = 0; i < delta_list.size(); i++)
		{
			int[][] table = (int[][]) delta_list.get(i);
			ideal_delta[i] = table[0][0];
		}
		return ideal_delta;
	}

	/**
	 * The sum of each pixel's smallest |difference|.
	 *
	 * @param delta_list {@link #getDeltaListFromValues}'s result
	 * @return the sum
	 */
	public static int getIdealDeltaSum(ArrayList delta_list)
	{
		int sum = 0;
		for(int i = 0; i < delta_list.size(); i++)
		{
			int[][] table = (int[][]) delta_list.get(i);
			sum += Math.abs(table[0][0]);
		}
		return sum;
	}

	/**
	 * The sum of each pixel's largest |difference|.
	 *
	 * @param delta_list {@link #getDeltaListFromValues}'s result
	 * @return the sum
	 */
	public static int getWorstlDeltaSum(ArrayList delta_list)
	{
		int sum = 0;
		for(int i = 0; i < delta_list.size(); i++)
		{
			int[][] table = (int[][]) delta_list.get(i);
			sum += Math.abs(table[table.length - 1][0]);
		}
		return sum;
	}

	/**
	 * The values of a pixel's neighbours (up to 8), row by row.
	 *
	 * @param src  the channel
	 * @param x    the column
	 * @param y    the row
	 * @param xdim the width
	 * @param ydim the height
	 * @return the neighbours' values
	 */
	public static ArrayList getNeighbors(int[] src, int x, int y, int xdim, int ydim)
	{
		ArrayList neighbors = new ArrayList();
		if(y > 0)
		{
			if(x > 0) neighbors.add(src[(y-1)*xdim+x-1]);
			neighbors.add(src[(y-1)*xdim+x]);
			if(x < xdim-1) neighbors.add(src[(y-1)*xdim+x+1]);
		}
		if(x > 0) neighbors.add(src[y*xdim+x-1]);
		if(x < xdim-1) neighbors.add(src[y*xdim+x+1]);
		if(y < ydim-1)
		{
			if(x > 0) neighbors.add(src[(y+1)*xdim+x-1]);
			neighbors.add(src[(y+1)*xdim+x]);
			if(x < xdim-1) neighbors.add(src[(y+1)*xdim+x+1]);
		}
		return neighbors;
	}

	/**
	 * Where a pixel is: 1-3 the top row (left corner, middle, right corner), 4-6
	 * the middle rows, 7-9 the bottom row.
	 *
	 * @param x    the column
	 * @param y    the row
	 * @param xdim the width
	 * @param ydim the height
	 * @return the location type, 1 to 9
	 */
	public static int getLocationType(int x, int y, int xdim, int ydim)
	{
		if(y == 0)
		{
			if(x == 0)         return 1;
			if(x < xdim-1)    return 2;
			return 3;
		}
		if(y < ydim-1)
		{
			if(x == 0)         return 4;
			if(x < xdim-1)    return 5;
			return 6;
		}
		if(x == 0)             return 7;
		if(x < xdim-1)        return 8;
		return 9;
	}

	/**
	 * The index of a neighbour location among those a location type has.
	 *
	 * @param location_type 1 to 9, from {@link #getLocationType}
	 * @param location      0 to 7 (see {@link #getNeighborIndex})
	 * @return its index, or -1 if that type doesn't have it
	 */
	public static int getLocationIndex(int location_type, int location)
	{
		switch(location_type)
		{
			case 1:
				if(location==4) return 0; if(location==6) return 1; if(location==7) return 2; break;
			case 2:
				if(location==3) return 0; if(location==4) return 1; if(location==5) return 2;
				if(location==6) return 3; if(location==7) return 4; break;
			case 3:
				if(location==3) return 0; if(location==5) return 1; if(location==6) return 2; break;
			case 4:
				if(location==1) return 0; if(location==2) return 1; if(location==4) return 2;
				if(location==6) return 3; if(location==7) return 4; break;
			case 5:
				if(location==0) return 0; if(location==1) return 1; if(location==2) return 2;
				if(location==3) return 3; if(location==4) return 4; if(location==5) return 5;
				if(location==6) return 6; if(location==7) return 7; break;
			case 6:
				if(location==0) return 0; if(location==1) return 1; if(location==3) return 2;
				if(location==5) return 3; if(location==6) return 4; break;
			case 7:
				if(location==1) return 0; if(location==2) return 1; if(location==4) return 2; break;
			case 8:
				if(location==0) return 0; if(location==1) return 1; if(location==2) return 2;
				if(location==3) return 3; if(location==4) return 4; break;
			case 9:
				if(location==0) return 0; if(location==1) return 1; if(location==3) return 2; break;
		}
		return -1;
	}

	/**
	 * The array index of a neighbour: locations 0-2 the row above (left to
	 * right), 3 left, 4 right, 5-7 the row below.
	 *
	 * @param x        the column
	 * @param y        the row
	 * @param xdim     the width
	 * @param location 0 to 7
	 * @return the index (the pixel itself for any other location)
	 */
	public static int getNeighborIndex(int x, int y, int xdim, int location)
	{
		int k = y * xdim + x;
		if(location==0) return k-xdim-1;
		if(location==1) return k-xdim;
		if(location==2) return k-xdim+1;
		if(location==3) return k-1;
		if(location==4) return k+1;
		if(location==5) return k+xdim-1;
		if(location==6) return k+xdim;
		if(location==7) return k+xdim+1;
		return k;
	}

	/**
	 * The opposite neighbour location.
	 *
	 * @param location 0 to 7
	 * @return 7 - location
	 */
	public static int getInverseLocation(int location)
	{
		return 7 - location;
	}
}
