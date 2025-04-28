/**
* This is a class that resizes rasters.
* 
* @author Brian Crowley
* @version 1.0
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
				for(int i = 0; i < ydim - 1; i++)
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
						int start = i * new_xdim;
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
			/*
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
			*/
			
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
					int stop = start + segment_length * xdim - xdim;
					for (int j = 0; j < number_of_segments; j++)
					{
						for (int k = start; k < stop; k += xdim)
						{
							dst[m] = src[k];
							m += xdim;
						}
						start = stop + xdim;
						stop = start + segment_length * xdim - xdim;
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
						int stop = start + segment_length * xdim - xdim;
						for (int j = 0; j < number_of_segments; j++)
						{
							for (int k = start; k < stop; k += xdim)
							{
								dst[m] = src[k];
								m += xdim;
							}
							start = stop + xdim;
							stop = start + segment_length * xdim - xdim;
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
					for (int i = 0; i < xdim; i++)
					{
						m = i;
						int start = i;
						int stop = start + segment_length * xdim - xdim;
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
							stop = start + segment_length * xdim - xdim;
						}
					}
					
					System.out.println("Got here.");
					System.out.println("Dst length is " + dst.length);
					System.out.println("Last dst index is " + (m - xdim));
					System.out.println();
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