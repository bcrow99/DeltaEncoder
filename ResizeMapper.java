/**
 * Resizes rasters, for the writers' Pixel Resolution setting: by dropping
 * columns and rows (shrinking) or inserting the average of two neighbours
 * (growing), at evenly spaced points.
 * <p>
 * The raster is split into segments, one more than the number of columns
 * (or rows) to drop or add; when they don't divide evenly, one fewer, with
 * the extra length spread over evenly spaced "long" segments
 * ({@link #longSegments}). Shrinking drops the column after each segment;
 * growing inserts an average after each segment but the last. resize2 does
 * the same with the dropped or inserted columns and rows centred.
 *
 * @author Brian Crowley
 * @version 1.2
 */
public class ResizeMapper
{
	/** Not used: all of this class's methods are static. */
	public ResizeMapper() {}

	/**
	 * Changes the width of a raster, making it either larger or smaller.
	 *
	 * @param src      the input raster
	 * @param xdim     the original width
	 * @param new_xdim the resized width
	 * @return the resized raster
	 */
	public static int[] resizeX(int src[], int xdim, int new_xdim)
	{
		int ydim = src.length / xdim;
		int[] dst = new int[new_xdim * ydim];
		
		if(new_xdim == xdim)
		{
			for (int i = 0; i < xdim * ydim; i++)
				dst[i] = src[i];
		} 
		else if (new_xdim < xdim)
		{
			int number_of_segments = xdim - new_xdim + 1;
			int remainder          = new_xdim % number_of_segments;
			if(remainder == 0)
			{
				int segment_length = new_xdim / number_of_segments;
				int m = 0;
				for (int i = 0; i < ydim; i++)
				{
					int start = i * xdim;
					int stop = start + segment_length;
					for (int j = 0; j < number_of_segments; j++)
					{
						for (int k = start; k < stop; k++)
							dst[m++] = src[k];
						start += segment_length + 1;
						stop = start + segment_length;
					}
				}
			}
			else
			{
				number_of_segments = xdim - new_xdim;
				remainder          = new_xdim % number_of_segments;
				int segment_length = new_xdim / number_of_segments;
				if(remainder == 0)
				{
					int m = 0;
					for (int i = 0; i < ydim; i++)
					{
						int start = i * xdim;
						int stop = start + segment_length;
						for (int j = 0; j < number_of_segments; j++)
						{
							for (int k = start; k < stop; k++)
								dst[m++] = src[k];
							start += segment_length + 1;
							stop = start + segment_length;
						}
					}
				}
				else
				{
				    boolean [] isLong = longSegments(number_of_segments, remainder);

				    int m = 0;
					for(int i = 0; i < ydim; i++)
					{
						int start = i * xdim;
						int stop  = start + segment_length;
						for(int j = 0; j < number_of_segments; j++)
						{
							if(isLong[j])
								stop++;
							for(int k = start; k < stop; k++)
								dst[m++] = src[k];
							start = stop + 1;
							stop = start + segment_length;
						}
					}
				}	
			}
		} 
		else if (new_xdim > xdim)
		{
			int number_of_segments = new_xdim - xdim + 1;
			int remainder          = xdim % number_of_segments;
			if(remainder == 0)
			{
				int segment_length = xdim / number_of_segments;
				int k = 0;
				int m = 0;
				for(int i = 0; i < ydim; i++)
				{
					int start = i * xdim;
					int stop = start + segment_length;
					for (int j = 0; j < number_of_segments - 1; j++)
					{
						for(k = start; k < stop; k++)
							dst[m++] = src[k];
						dst[m++] = (src[k] + src[k - 1]) / 2;
						start += segment_length;
						stop = start + segment_length;
					}
					for(int j = start; j < stop; j++)
						dst[m++] = src[j];
				}
			}
			else
			{
				
				number_of_segments = new_xdim - xdim;
				remainder          = xdim % number_of_segments;
				int segment_length = xdim / number_of_segments;
				if(remainder == 0)
				{
					int k = 0;
					int m = 0;
					for (int i = 0; i < ydim; i++)
					{
						int start = i * xdim;
						int stop = start + segment_length;
						for(int j = 0; j < number_of_segments - 1; j++)
						{
							for(k = start; k < stop; k++)
								dst[m++] = src[k];
							dst[m++] = (src[k] + src[k - 1]) / 2;
							start += segment_length;
							stop = start + segment_length;
						}
						for(k = start; k < stop; k++)
							dst[m++] = src[k];
						dst[m++] = src[k - 1];
					}
				}
				else
				{
				    boolean [] isLong = longSegments(number_of_segments, remainder);
				    
				    int k = 0;
				    int m = 0;
					for(int i = 0; i < ydim; i++)
					{
						int start = i * xdim;
						int stop  = start + segment_length;
						for(int j = 0; j < number_of_segments - 1; j++)
						{
							if(isLong[j])
							{
								stop++;
							}
							for(k = start; k < stop; k++)
								dst[m++] = src[k];
							dst[m++] = (src[k] + src[k - 1]) / 2;
							start = stop;
							stop  = start + segment_length;
						}
						// The last segment can be a long one too.
						if(isLong[number_of_segments - 1])
							stop++;
						for(k = start; k < stop; k++)
							dst[m++] = src[k];
						dst[m++] = src[k - 1];
					}	
				}	
			}	
		}
		return dst;
	}

	/**
	 * Changes the height of a raster, making it either larger or smaller.
	 *
	 * @param src      the input raster
	 * @param xdim     the width
	 * @param new_ydim the resized height
	 * @return the resized raster
	 */
	public static int[] resizeY(int src[], int xdim, int new_ydim)
	{
		int ydim  = src.length / xdim;
		int[] dst = new int[xdim * new_ydim];

		if(new_ydim == ydim)
			for(int i = 0; i < xdim * ydim; i++)
				dst[i] = src[i];
		else if(new_ydim < ydim)
		{
			int number_of_segments = ydim - new_ydim + 1;
			int remainder          = new_ydim % number_of_segments;
			if(remainder == 0)
			{
				int segment_length = new_ydim / number_of_segments;
				int m = 0;
				for (int i = 0; i < xdim; i++)
				{
					m = i;
					int start = i;
					int stop = start + segment_length * xdim;
					for (int j = 0; j < number_of_segments; j++)
					{
						for (int k = start; k < stop; k += xdim)
						{
							dst[m] = src[k];
							m += xdim;
						}
						start = stop + xdim;
						stop = start + segment_length * xdim;
					}
				}
			}
			else
			{
				number_of_segments = ydim - new_ydim;
				remainder          = new_ydim % number_of_segments;
				int segment_length = new_ydim / number_of_segments;
				if(remainder == 0)
				{
					int m = 0;
					for (int i = 0; i < xdim; i++)
					{
						m = i;
						int start = i;
						int stop = start + segment_length * xdim;
						for (int j = 0; j < number_of_segments; j++)
						{
							for (int k = start; k < stop; k += xdim)
							{
								dst[m] = src[k];
								m += xdim;
							}
							start = stop + xdim;
							stop = start + segment_length * xdim;
						}
					}	
				}
				else
				{	
					boolean [] isLong = longSegments(number_of_segments, remainder);
				    
				    int m = 0;
					for (int i = 0; i < xdim; i++)
					{
						m = i;
						int start = i;
						int stop = start + segment_length * xdim;
						for (int j = 0; j < number_of_segments; j++)
						{
							if(isLong[j])
								stop += xdim;
							for(int k = start; k < stop; k += xdim)
							{
								dst[m] = src[k];
								m += xdim;
							}
							start = stop + xdim;
							stop = start + segment_length * xdim;
						}
					}
				}
			}
		} 
		else if(new_ydim > ydim)
		{
			int number_of_segments = new_ydim - ydim + 1;
			int remainder          = ydim % number_of_segments;
			if(remainder == 0)
			{
				int segment_length = ydim / number_of_segments;
				int m = 0;
				for (int i = 0; i < xdim; i++)
				{
					m = i;
					int start = i;
					int stop = start + segment_length * xdim;

					for (int j = 0; j < number_of_segments - 1; j++)
					{
						for (int k = start; k < stop; k += xdim)
						{
							dst[m] = src[k];
							m += xdim;
						}
						// We add a pixel at the end of each segment.
						dst[m] = (src[stop] + src[stop - xdim]) / 2;

						m += xdim;
						start = stop;
						stop = start + segment_length * xdim;
					}

					// We write the last segment without adding a pixel.
					stop = start + segment_length * xdim;
					for (int k = start; k < stop; k += xdim)
					{
						dst[m] = src[k];
						m += xdim;
					}
				}
			}
			else
			{
				number_of_segments = new_ydim - ydim;
				remainder          = ydim % number_of_segments;
				int segment_length = ydim / number_of_segments;
				if(remainder == 0)
				{
					int j = 0;
					int k = 0;
					int m = 0;
				    for (int i = 0; i < xdim; i++)
					{
						m = i;
						int start = i;
						int stop = start + segment_length * xdim;
						for(j = 0; j < number_of_segments - 1; j++)
						{
							for(k = start; k < stop; k += xdim)
							{
								dst[m] = src[k];
								m += xdim;
							}
							dst[m] = (src[k] + src[k - xdim]) / 2;
							m += xdim;
							start = stop;
							stop = start + segment_length * xdim;
						}
						for(k = start; k < stop; k += xdim)
						{
							dst[m] = src[k];
							m += xdim;
						}
						dst[m] = src[k - xdim];
					}
				}
				else
				{
					boolean [] isLong = longSegments(number_of_segments, remainder);
				    
				    int j = 0;
				    int k = 0;
				    int m = 0;
					for(int i = 0; i < xdim; i++)
					{
						m = i;
						int start = i;
						int stop = start + segment_length * xdim;
						for(j = 0; j < number_of_segments - 1; j++)
						{
							if(isLong[j])
								stop += xdim;
							
							for(k = start; k < stop; k += xdim)
							{
								dst[m] = src[k];
								m += xdim;
							}
							dst[m] = (src[k] + src[k - xdim]) / 2;
							m += xdim;
							
							start = stop;
							stop  = start + segment_length * xdim;
						}
						if(isLong[j])
							stop += xdim;
						for(k = start; k < stop; k += xdim)
						{
							dst[m] = src[k];
							m += xdim;
						}
						dst[m] = src[k - xdim];
					}
				}	
			}	
		}
		return dst;
	}

	/**
	 * Changes the height and width of a raster, making it either larger or
	 * smaller. Shrinking works across first; growing down first (so a resize
	 * down and back up undoes the steps in reverse order, which may reduce
	 * noise).
	 *
	 * @param src      the input raster
	 * @param xdim     the original width
	 * @param new_xdim the resized width
	 * @param new_ydim the resized height
	 * @return the resized raster
	 */
	public static int[] resize(int src[], int xdim, int new_xdim, int new_ydim)
	{
		if (new_xdim < xdim)
		{
			int[] tmp = resizeX(src, xdim, new_xdim);
			int[] dst = resizeY(tmp, new_xdim, new_ydim);
			return dst;
		}
		else
		{
			int[] tmp = resizeY(src, xdim, new_ydim);
			int[] dst = resizeX(tmp, xdim, new_xdim);
			return dst;
		}
	}

	/**
	 * As {@link #resize}, but with the dropped (shrinking) or inserted
	 * (growing) columns and rows centred: spread evenly with half a gap at
	 * each end, the same pattern counted from either side
	 * ({@link #centredPositions}). Mirroring or rotating an image before
	 * shrinking it then keeps the same original pixels, and the first and
	 * last columns and rows are kept. Shrinking only drops (every value kept
	 * is an original one); growing inserts the average of the nearest kept
	 * neighbours, and puts the kept values back exactly where a shrink to
	 * that size took them from. Both directions are done in one pass, each
	 * pixel the average of up to four source pixels with one rounding, so the
	 * result doesn't depend on which direction goes first (a pixel inserted
	 * in one direction only is the average of its two neighbours, as
	 * resizeX2 and resizeY2 give it).
	 *
	 * @param src      the input raster
	 * @param xdim     the original width
	 * @param new_xdim the resized width
	 * @param new_ydim the resized height
	 * @return the resized raster
	 */
	public static int[] resize2(int src[], int xdim, int new_xdim, int new_ydim)
	{
		int[][] col = centredMap(xdim, new_xdim), row = centredMap(src.length / xdim, new_ydim);
		int[] ca = col[0], cb = col[1], dst = new int[new_xdim * new_ydim];
		for(int y = 0; y < new_ydim; y++)
		{
			int s = row[0][y] * xdim, t = row[1][y] * xdim, d = y * new_xdim;
			for(int x = 0; x < new_xdim; x++) dst[d + x] = (src[s + ca[x]] + src[s + cb[x]] + src[t + ca[x]] + src[t + cb[x]]) / 4;
		}
		return dst;
	}

	/**
	 * Changes the width of a raster with centred columns (see {@link #resize2}).
	 *
	 * @param src      the input raster
	 * @param xdim     the original width
	 * @param new_xdim the resized width
	 * @return the resized raster
	 */
	public static int[] resizeX2(int src[], int xdim, int new_xdim)
	{
		int   ydim = src.length / xdim;
		int[][] map = centredMap(xdim, new_xdim);
		int[] a = map[0], b = map[1], dst = new int[new_xdim * ydim];
		for(int y = 0; y < ydim; y++)
			for(int x = 0, s = y * xdim, d = y * new_xdim; x < new_xdim; x++) dst[d + x] = (src[s + a[x]] + src[s + b[x]]) / 2;
		return dst;
	}

	/**
	 * Changes the height of a raster with centred rows (see {@link #resize2}).
	 *
	 * @param src      the input raster
	 * @param xdim     the width
	 * @param new_ydim the resized height
	 * @return the resized raster
	 */
	public static int[] resizeY2(int src[], int xdim, int new_ydim)
	{
		int   ydim = src.length / xdim;
		int[][] map = centredMap(ydim, new_ydim);
		int[] a = map[0], b = map[1], dst = new int[xdim * new_ydim];
		for(int y = 0; y < new_ydim; y++)
			for(int x = 0, s = a[y] * xdim, t = b[y] * xdim, d = y * xdim; x < xdim; x++) dst[d + x] = (src[s + x] + src[t + x]) / 2;
		return dst;
	}

	/**
	 * Where each pixel of a resized line of new_n comes from, in a line of n:
	 * the average of source pixels a[i] and b[i] (the same pixel where it is
	 * kept). Shrinking drops the centred positions of the n - new_n; growing
	 * inserts new_n - n at the centred positions of the new line, each the
	 * average of the nearest kept pixels either side (or a copy of the one
	 * there is).
	 *
	 * @param n     the line's length
	 * @param new_n the resized length
	 * @return {a, b}
	 */
	private static int[][] centredMap(int n, int new_n)
	{
		int[] a = new int[new_n], b = new int[new_n];
		if(new_n <= n)
		{
			boolean[] drop = new boolean[n];
			for(int p : centredPositions(n, n - new_n)) drop[p] = true;
			for(int i = 0, o = 0; i < n; i++) if(!drop[i]) { a[o] = i; b[o] = i; o++; }
		}
		else
		{
			boolean[] insert = new boolean[new_n];
			for(int p : centredPositions(new_n, new_n - n)) insert[p] = true;
			int[] kept = new int[new_n];   // the source pixel at each kept position, -1 where inserted
			for(int o = 0, i = 0; o < new_n; o++) kept[o] = insert[o] ? -1 : i++;
			for(int o = 0; o < new_n; o++)
			{
				if(kept[o] >= 0) { a[o] = kept[o]; b[o] = kept[o]; continue; }
				int l = o - 1, r = o + 1;
				while(l >= 0 && kept[l] < 0) l--;
				while(r < new_n && kept[r] < 0) r++;
				a[o] = (l >= 0) ? kept[l] : kept[r];
				b[o] = (r < new_n) ? kept[r] : kept[l];
			}
		}
		return new int[][] {a, b};
	}

	/**
	 * The centred positions of d columns (or rows) in a line of n: spread
	 * evenly with half a gap at each end, (2j + 1) n / (2d) for the left half
	 * and mirrored for the right, so the pattern reads the same from either
	 * end. With an odd d the middle one is at the centre ((n - 1) / 2: one
	 * left of centre when n is even, the one place the pattern can't be
	 * symmetric).
	 *
	 * @param n the line's length
	 * @param d how many positions, 0 to n - 1
	 * @return the positions, in increasing order
	 */
	private static int[] centredPositions(int n, int d)
	{
		int[] p = new int[d];
		for(int j = 0; j < d / 2; j++)
		{
			p[j]         = (int) ((2L * j + 1) * n / (2L * d));
			p[d - 1 - j] = n - 1 - p[j];
		}
		if(d % 2 == 1) p[d / 2] = (n - 1) / 2;
		return p;
	}

	/**
	 * Which of number_of_segments segments are one longer, to take up a
	 * remainder: remainder of them, every number_of_segments / (remainder + 1)
	 * segments, starting with that one.
	 *
	 * @param number_of_segments the number of segments
	 * @param remainder          how many are long (less than number_of_segments)
	 * @return whether each segment is long
	 */
	private static boolean[] longSegments(int number_of_segments, int remainder)
	{
		boolean[] isLong    = new boolean[number_of_segments];
		int       increment = number_of_segments / (remainder + 1);
		int       index     = increment;
		for(int i = 0; i < remainder; i++)
		{
			isLong[index] = true;
			index        += increment;
		}
		return isLong;
	}
}
