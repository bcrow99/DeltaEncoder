
public class ResizeMapper
{
	// These functions accept arbitrary dimensions, up or down.
	public static int[] resizeX(int src[], int xdim, int new_xdim)
	{
		int ydim = src.length / xdim;
		int[] dst = new int[new_xdim * ydim];

		if (new_xdim == xdim)
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

	public static int[] resize(int src[], int xdim, int new_xdim, int new_ydim)
	{
		// Reversing the order helps reduce noise when we resize down and up,
		// but not completely. There is (fairly rare) speckling that might be related
		// to taking average values from averaged values.
		if (new_xdim < xdim)
		{
			int[] tmp = resizeX(src, xdim, new_xdim);
			int[] dst = resizeY(tmp, new_xdim, new_ydim);
			return dst;
		} else
		{
			int[] tmp = resizeY(src, xdim, new_ydim);
			int[] dst = resizeX(tmp, xdim, new_xdim);
			return dst;
		}

	}
}