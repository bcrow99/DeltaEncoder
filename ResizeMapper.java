/**
 * Resizes rasters, for the writers' Pixel Resolution setting: by dropping
 * columns and rows (shrinking) or inserting the average of two neighbours
 * (growing), at evenly spaced points.
 * <p>
 * The raster is split into segments, one more than the number of columns
 * (or rows) to drop or add; when they don't divide evenly, one fewer, with
 * the extra length spread over evenly spaced "long" segments
 * ({@link #longSegments}). Shrinking drops the column after each segment;
 * growing inserts an average after each segment but the last.
 *
 * @author Brian Crowley
 * @version 1.1
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
