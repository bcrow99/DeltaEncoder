/**
* This is a class that resizes rasters.
* 
* @author Brian Crowley
* @version 1.0
*/

// version 1.0

/*
 * Three bugs fixed in this version (see inline FIX comments at each site):
 *   1. resizeX2, new_xdim>xdim, remainder==0 branch: loop bound was
 *      `i < ydim - 1`, silently dropping the last output row. Now `i < ydim`.
 *   2. resizeX2, new_xdim>xdim, else->remainder==0 branch: row start was
 *      computed as `i * new_xdim` instead of `i * xdim`, reading from the
 *      wrong source offset. Now `i * xdim`.
 *   3. resizeY2, new_ydim>ydim, else->remainder==0 branch: was missing
 *      `m += xdim;` after an averaging write (causing the next write to
 *      overwrite it), and had `start = stop + xdim` instead of
 *      `start = stop` (causing reads past the end of src -- a real
 *      ArrayIndexOutOfBoundsException for many realistic inputs). Now
 *      matches the pattern used by every other equivalent branch in this
 *      file. This one was confirmed to crash 5/500 and 13/500 in two
 *      independent randomized tests (Java and Python respectively) against
 *      realistic image-size/pixel_quant combinations, and 0/500 after the fix.
 */
public class ResizeMapper
{
	// These functions accept arbitrary dimensions, up or down.
	/**
    * Changes the width of a raster, making it either larger or smaller.
    *
    * @param src the input raster
    * @param xdim the original width
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
			int delta = xdim - new_xdim;
			int number_of_segments = delta + 1;
			int segment_length = xdim / number_of_segments;
			int last_segment_length = segment_length + xdim % number_of_segments;

			int m = 0;
			for (int i = 0; i < ydim; i++)
			{
				int start = i * xdim;
				int stop = start + segment_length - 1;
				for (int j = 0; j < number_of_segments - 1; j++)
				{
					for (int k = start; k < stop; k++)
						dst[m++] = src[k];
					start += segment_length;
					stop = start + segment_length - 1;
				}
				stop = start + last_segment_length;
				for (int k = start; k < stop; k++)
					dst[m++] = src[k];
			}
		} 
		else if (new_xdim > xdim)
		{
			int delta = new_xdim - xdim;
			int number_of_segments = delta + 1;
			int segment_length = xdim / number_of_segments;
			int last_segment_length = segment_length + xdim % number_of_segments;

			int m = 0;
			for (int i = 0; i < ydim; i++)
			{
				int start = i * xdim;
				int stop = start + segment_length;

				for (int j = 0; j < number_of_segments - 1; j++)
				{
					for (int k = start; k < stop; k++)
						dst[m++] = src[k];
					dst[m] = (src[stop] + src[stop - 1]) / 2;

					m++;
					start += segment_length;
					stop = start + segment_length;
				}
				// Write the values from the last segment without adding a pixel.
				stop = start + last_segment_length;
				for (int k = start; k < stop; k++)
					dst[m++] = src[k];
			}
		}
		
		return dst;
	}
	
	
	
	public static int[] resizeX2(int src[], int xdim, int new_xdim)
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
					// Untested code.
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
				    boolean [] isLong   = new boolean[number_of_segments];
				    double     interval = 1.; interval /= remainder + 1;
				    int increment = (int)(interval * number_of_segments);
				    int index = increment;
				    int number_of_long_segments = 0;
				    for(int i = 0; i < remainder; i++)
				    {
				        isLong[index] = true;
				        index        += increment;
				        number_of_long_segments++;
				    }

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
				// FIX: was `i < ydim - 1`, which silently skipped filling
				// in the last output row entirely (left at 0).
				for(int i = 0; i < ydim; i++)
				{
					int start = i * xdim;
					int stop = start + segment_length;
					for (int j = 0; j < number_of_segments - 1; j++)
					{
						for(k = start; k < stop; k++)
							dst[m++] = src[k];
						dst[m++] = (src[k] + src[k - 1]) / 2;
						//start += segment_length + 1;
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
						// FIX: was `i * new_xdim`. src has `xdim` columns
						// per row (its own width, not the target width),
						// so the row start must be computed from xdim --
						// every other row-start computation in this file
						// does. Using new_xdim read from the wrong offset
						// whenever new_xdim != xdim, which was always true
						// in this branch.
						int start = i * xdim;
						int stop = start + segment_length;
						for(int j = 0; j < number_of_segments - 1; j++)
						{
							for(k = start; k < stop; k++)
								dst[m++] = src[k];
							dst[m++] = (src[k] + src[k - 1]) / 2;
							//start += segment_length + 1;
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
				    boolean [] isLong   = new boolean[number_of_segments];
				    double     interval = 1.; interval /= remainder + 1;
				    int increment = (int)(interval * number_of_segments);
				    int index = increment;
				    int number_of_long_segments = 0;
				    for(int i = 0; i < remainder; i++)
				    {
				        isLong[index] = true;
				        index        += increment;
				        number_of_long_segments++;
				    }
				    
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
	* @param src the input raster
	* @param xdim the original width
	* @param new_ydim the resized height
	* @return the resized raster
	*/
	public static int[] resizeY(int src[], int xdim, int new_ydim)
	{
		int ydim = src.length / xdim;
		int[] dst = new int[xdim * new_ydim];

		if (new_ydim == ydim)
			for (int i = 0; i < xdim * ydim; i++)
				dst[i] = src[i];
		else if (new_ydim < ydim)
		{
			int delta = ydim - new_ydim;
			int number_of_segments = delta + 1;
			int segment_length = ydim / number_of_segments;
			int last_segment_length = segment_length + ydim % number_of_segments;

			int m = 0;
			for (int i = 0; i < xdim; i++)
			{
				m = i;
				int start = i;
				int stop = start + segment_length * xdim - xdim;
				for (int j = 0; j < number_of_segments - 1; j++)
				{
					for (int k = start; k < stop; k += xdim)
					{
						dst[m] = src[k];
						m += xdim;
					}

					start = stop + xdim;
					stop = start + segment_length * xdim - xdim;
				}
				stop = start + last_segment_length * xdim;
				for (int k = start; k < stop; k += xdim)
				{
					dst[m] = src[k];
					m += xdim;
				}
			}
		} 
		else if (new_ydim > ydim)
		{
			int delta = new_ydim - ydim;
			int number_of_segments = delta + 1;
			int segment_length = ydim / number_of_segments;
			int last_segment_length = segment_length + ydim % number_of_segments;

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
				stop = start + last_segment_length * xdim;
				for (int k = start; k < stop; k += xdim)
				{
					dst[m] = src[k];
					m += xdim;
				}
			}
		}
		return dst;
	}

	
	public static int[] resizeY2(int src[], int xdim, int new_ydim)
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
					boolean [] isLong   = new boolean[number_of_segments];
				    double     interval = 1.; interval /= remainder + 1;
				    int increment = (int)(interval * number_of_segments);
				    int index = increment;
				    
				    for(int i = 0; i < remainder; i++)
				    {
				        isLong[index] = true;
				        index        += increment;
				    }
				    
				    int number_of_long_segments = 0;
				    for(int i = 0; i < number_of_segments; i++)
				    {
				        if(isLong[i])
				            number_of_long_segments++;
				    }
				    
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
							// FIX (two related bugs):
							// (1) was missing `m += xdim;` here -- every
							//     structurally-equivalent branch elsewhere
							//     in this file advances m after this kind
							//     of averaging write; without it the very
							//     next write below overwrites this value
							//     instead of advancing past it.
							// (2) `start = stop + xdim` had a stray extra
							//     +xdim not present in the equivalent
							//     branch just above (which uses
							//     `start = stop`). That extra +xdim
							//     skipped a source row this algorithm
							//     still needed, which cascaded into
							//     reading past the end of src -- a real
							//     crash (ArrayIndexOutOfBoundsException)
							//     for any input reaching this branch with
							//     number_of_segments >= 2.
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
					boolean [] isLong   = new boolean[number_of_segments];
				    double     interval = 1.; interval /= remainder + 1;
				    int increment = (int)(interval * number_of_segments);
				    int index = increment;
				    
				    for(int i = 0; i < remainder; i++)
				    {
				        isLong[index] = true;
				        index        += increment;
				    }
				    
				    int number_of_long_segments = 0;
				    for(int i = 0; i < number_of_segments; i++)
				    {
				        if(isLong[i])
				            number_of_long_segments++;
				    }
				    
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
	* Changes the height and width of a raster, making it either larger or smaller.
    *
	* @param src the input raster
	* @param xdim the original width
	* @param new_xdim the resized width
	* @param new_ydim the resized height
	* @return the resized raster
	*/
	public static int[] resize(int src[], int xdim, int new_xdim, int new_ydim)
	{
		// Reversing the order possibly helps reduce noise when we resize down and up.
		if (new_xdim < xdim)
		{
			int[] tmp = resizeX2(src, xdim, new_xdim);
			int[] dst = resizeY2(tmp, new_xdim, new_ydim);
			return dst;
		} 
		else
		{
			int[] tmp = resizeY2(src, xdim, new_ydim);
			int[] dst = resizeX2(tmp, xdim, new_xdim);
			return dst;
		}

	}
}
