import java.util.*;
import java.util.zip.*;
import java.lang.Math.*;

public class DeltaMapper
{
	
	public static int[] shrink(int src[], int xdim, int ydim)
	{
		int [] dst = new int[(xdim - 1) * (ydim - 1)];
		
		int k = 0;
		for(int i = 0; i < ydim - 1; i++)
		{
			for(int j = 0; j < xdim - 1; j++)	
			{
				int w    = src[i * xdim + j];
				int x    = src[i * xdim + j + 1];
				int y    = src[(i + 1) * xdim + j];
				int z    = src[(i + 1) * xdim + j + 1];
			    dst[k++] = (w + x + y + z) / 4;	
			    // Rounding up seems to increase the error when we expand back.  
			}
		}
		return(dst);
	}
	
	public static double[] shrink(double src[], int xdim, int ydim)
	{
		double [] dst = new double[(xdim - 1) * (ydim - 1)];
		
		int k = 0;
		for(int i = 0; i < ydim - 1; i++)
		{
			for(int j = 0; j < xdim - 1; j++)	
			{
				double w = src[i * xdim + j];
				double x = src[i * xdim + j + 1];
				double y = src[(i + 1) * xdim + j];
				double z = src[(i + 1) * xdim + j + 1];
			    dst[k++] = (w + x + y + z) / 4.;	
			}
		}
		return(dst);
	}
	
	public static double[] shrinkX(double src[], int xdim, int ydim)
	{
		double [] dst = new double[(xdim - 1) * ydim];
		
		int k = 0;
		for(int i = 0; i < ydim; i++)
		{
			for(int j = 0; j < xdim - 1; j++)	
			{
				double w = src[i * xdim + j];
				double x = src[i * xdim + j + 1];
			    dst[k++] = (w + x) / 2.;	
			}
		}
		return(dst);
	}
	
	public static double[] shrinkY(double src[], int xdim, int ydim)
	{
		double [] dst = new double[xdim * (ydim - 1)];
		
		for(int j = 0; j < xdim; j++)
		{ 
			int k = j;
			for(int i = 0; i < ydim - 1; i++)	
			{
				double w = src[i * xdim + k];
				double y = src[(i + 1) * xdim + k];
			    dst[k]   = (w + y) / 2.;	
			}
			k += xdim;
		}
		return(dst);
	}
	
	public static double[] shrinkXY(double src[], int xdim, int ydim)
	{
		double [] intermediate = shrinkX(src, xdim, ydim);
		double [] dst          = shrinkY(intermediate, xdim - 1, ydim);
	
		return(dst);
	}
	
	public static double[] shrink4(double src[], int xdim, int ydim)
	{
		int _xdim = xdim / 2;
		int _ydim = ydim / 2;
		double [] dst = new double[_xdim * _ydim];
		
		int k = 0;
		int m = 0;
		for(int i = 0; i < _ydim - 1; i++)
		{
			int n = 0;
			for(int j = 0; j < _xdim - 1; j++)	
			{
				double w = src[m * xdim + n];
				double x = src[m * xdim + n + 1];
				double y = src[(m + 1) * xdim + n];
				double z = src[(m + 1) * xdim + n + 1];
			    dst[k++] = (w + x + y + z) / 4.;
			    n       += 2;
			}
			m += 2;
		}
		
		return(dst);
	}
	
	public static double[] shrink(double src[], int xdim, int ydim, double error[])
	{
		double [] dst = new double[(xdim - 1) * (ydim - 1)];
		
		int k = 0;
		for(int i = 0; i < ydim - 1; i++)
		{
			for(int j = 0; j < xdim - 1; j++)	
			{
				double w  = src[i * xdim + j];
				double x  = src[i * xdim + j + 1];
				double y = src[(i + 1) * xdim + j];
				double z = src[(i + 1) * xdim + j + 1];
				
				double _w = error[i * xdim + j];
				double _x = error[i * xdim + j + 1];
				double _y = error[(i + 1) * xdim + j];
				double _z = error[(i + 1) * xdim + j + 1];
				
				double _error    = _w + _x + _y + _z;
				double error_root = 0;
				if(_error > 0)
				    error_root = Math.sqrt(_error);
				else
				{
					error_root = Math.sqrt(-_error);
					error_root = -error_root;
				}
				
			    //dst[k++] = ((w - _w) + (x - _x) + (y - _y) + (z - _z)) / 4.;
				//dst[k++] = (w + x + y + z + error_root) / 4.;
				dst[k++] = (w + x + y + z) / 4. + error_root;
				//dst[k++] = (w + x + y + z) / 4.;
			}
		}
		return(dst);
	}
	
	public static int[] shrink(int src[], int xdim, int ydim, int error[])
	{
		int [] dst = new int[(xdim - 1) * (ydim - 1)];
		int k = 0;
		for(int i = 0; i < ydim - 1; i++)
		{
			for(int j = 0; j < xdim - 1; j++)	
			{
			    dst[k] = (src[i * xdim + j] + src[i * xdim + j + 1] +
			    		    src[(i + 1) * xdim + j] + src[(i + 1) * xdim + j + 1]) / 4;	
			    //dst[k] += error[i * xdim + j] / 4;
			    dst[k] += error[i * xdim + j];
			    k++;
			    //error[i * xdim + j + 1] -= error[i * xdim + j];
			    //error[i * xdim + j + 1] -= error[i * xdim + j] / 4;
			    //error[(i + 1) * xdim + j] -= error[i * xdim + j] / 4;
			    //error[(i + 1) * xdim + j + 1] -= error[i * xdim + j] / 4;
			}
		}
		return(dst);
	}

	public static double[] expandX(double src[], int xdim, int ydim)
	{
		double [] dst = new double[(xdim + 1) * ydim];
		
		int k = 0;
		for(int i = 0; i < ydim; i++)
		{
			for(int j = 0; j < xdim - 1; j++)	
			{
			    if(j == 0)
			    {
			        dst[k++] = src[i * xdim + j] + (src[i * xdim + j] - src[i * xdim + j + 1]) / 2.;	
			        dst[k++] = (src[i * xdim + j] + src[i * xdim + j + 1]) / 2.;
			    }
			    else if(j < xdim - 2)
			    {
			        dst[k++] = (src[i * xdim + j] + src[i * xdim + j + 1]) / 2.;	
			    }
			    else if(j == xdim - 2)
			    {
			    	dst[k++] = (src[i * xdim + j] + src[i * xdim + j + 1]) / 2.;
			    	dst[k++] = src[i * xdim + j] + (src[i * xdim + j + 1] - src[i * xdim + j]) / 2.;	
			    }
			}
		}
		return(dst);
	}
	
	public static double[] expandY(double src[], int xdim, int ydim)
	{
		double [] dst = new double[(ydim + 1) * xdim];
		
		//System.out.println("Dst length = " + dst.length);
		int k = 0;
		for(int j = 0; j < xdim; j++)
		{
			k = j;
			for(int i = 0; i < ydim - 1; i++)	
			{
			    if(i == 0)
			    {
			        dst[k]        = src[i * xdim + j] + (src[i * xdim + j] - src[(i + 1) * xdim + j]) / 2.;	
			        dst[k + xdim] = (src[i * xdim + j] + src[(i + 1) * xdim + j]) / 2.;	
			        k += 2 * xdim;
			    }
			    else if(i < ydim - 2)
			    {
			        dst[k] = (src[i * xdim + j] + src[(i + 1) * xdim + j]) / 2.;	
			        k += xdim;
			    }
			    else if(i == ydim - 2)
			    {
			    	dst[k]        = (src[i * xdim + j] + src[(i + 1) * xdim + j]) / 2.;
			    	dst[k + xdim] = src[i * xdim + j] + (src[(i + 1) * xdim + j] - src[i * xdim + j]) / 2.;
			    }
			}
		}
		return(dst);
	}
	
	public static double[] expandYX(double src[], int xdim, int ydim)
	{
	    double[] intermediate = expandY(src, xdim, ydim);
	    double[] dst          = expandX(intermediate, xdim, ydim + 1);
	    return dst;
	}
	
	public static double[] expandXY(double src[], int xdim, int ydim)
	{
	    double[] intermediate = expandX(src, xdim, ydim);
	    double[] dst          = expandY(intermediate, xdim + 1, ydim);
	    return dst;
	}
	
	
	public static int[] expandX(int src[], int xdim, int ydim)
	{
		int [] dst = new int[(xdim + 1) * ydim];
		
		int k = 0;
		for(int i = 0; i < ydim; i++)
		{
			for(int j = 0; j < xdim - 1; j++)	
			{
			    if(j == 0)
			    {
			        dst[k++] = src[i * xdim + j] + (src[i * xdim + j] - src[i * xdim + j + 1]) / 2;	
			        dst[k++] = (src[i * xdim + j] + src[i * xdim + j + 1]) / 2;
			    }
			    else if(j < xdim - 2)
			    {
			        dst[k++] = (src[i * xdim + j] + src[i * xdim + j + 1]) / 2;	
			    }
			    else if(j == xdim - 2)
			    {
			    	dst[k++] = (src[i * xdim + j] + src[i * xdim + j + 1]) / 2;
			    	dst[k++] = src[i * xdim + j] + (src[i * xdim + j + 1] - src[i * xdim + j]) / 2;	
			    }
			}
		}
		return(dst);
	}
	
	public static int[] expandY(int src[], int xdim, int ydim)
	{
		int [] dst = new int[(ydim + 1) * xdim];
		
		//System.out.println("Dst length = " + dst.length);
		int k = 0;
		for(int j = 0; j < xdim; j++)
		{
			k = j;
			for(int i = 0; i < ydim - 1; i++)	
			{
			    if(i == 0)
			    {
			        dst[k]        = src[i * xdim + j] + (src[i * xdim + j] - src[(i + 1) * xdim + j]) / 2;	
			        dst[k + xdim] = (src[i * xdim + j] + src[(i + 1) * xdim + j]) / 2;	
			        k += 2 * xdim;
			    }
			    else if(i < ydim - 2)
			    {
			        dst[k] = (src[i * xdim + j] + src[(i + 1) * xdim + j]) / 2;	
			        k += xdim;
			    }
			    else if(i == ydim - 2)
			    {
			    	dst[k]        = (src[i * xdim + j] + src[(i + 1) * xdim + j]) / 2;
			    	dst[k + xdim] = src[i * xdim + j] + (src[(i + 1) * xdim + j] - src[i * xdim + j]) / 2;
			    }
			}
		}
		return(dst);
	}
	
	public static int[] expandYX(int src[], int xdim, int ydim)
	{
	    int[] intermediate = expandY(src, xdim, ydim);
	    int[] dst          = expandX(intermediate, xdim, ydim + 1);
	    return dst;
	}
	
	public static int[] expandXY(int src[], int xdim, int ydim)
	{
	    int[] intermediate = expandX(src, xdim, ydim);
	    int[] dst          = expandY(intermediate, xdim + 1, ydim);
	    return dst;
	}
	
	public static double[] expand(double src[], int xdim, int ydim)
	{
		double [] dst = new double[(xdim + 1) * (ydim + 1)];
		
		// Expanding the NW quadrant w/ left up filter.
		for(int i = 0; i < ydim - 2; i++)
		{
		    int k = i * xdim;
		    for(int j = 0; j < xdim - 2; j++)
		    {
		        dst[k++] = ((src[i * xdim + j] + (src[i * xdim + j] - src[i * xdim + j + 1]) / 2 ) +
		                    (src[i * xdim + j] + (src[i * xdim + j] - src[i * xdim + j + xdim]) /2)) / 2;
		    }
		}
		
		
		// Expanding the NE quadrant w/ right up filter.
		for(int i = 0; i < ydim - 2; i++)
		{
		    int k = i * xdim + xdim - 2;
		    for(int j = xdim - 2; j < xdim; j++)
		    {
		        dst[k++] = ((src[i * xdim + j] + (src[i * xdim + j] - src[i * xdim + j - 1]) /2) +
		                    (src[i * xdim + j] + (src[i * xdim + j] - src[i * xdim + j + xdim]) /2)) / 2;
		    }
		}
		
		// Expanding the SW quadrant w/ left down filter.
		for(int i = ydim - 2; i < ydim; i++)
		{
		    int k = i * xdim;
			for(int j = 0; j < xdim - 2; j++)
			{
		        dst[k++] = ((src[i * xdim + j] + (src[i * xdim + j] - src[i * xdim + j + 1]) / 2 ) +
				             (src[i * xdim + j] + (src[i * xdim + j] - src[i * xdim + j - xdim]) /2)) / 2;
		    }
		}
		
		// Expanding the SE quadrant w/ right down filter.
		for(int i = ydim - 2; i < ydim; i++)
		{
		    int k = i * xdim + xdim - 2;
			for(int j = xdim - 2; j < xdim; j++)
			{
			    dst[k++] = ((src[i * xdim + j] + (src[i * xdim + j] - src[i * xdim + j - 1]) / 2 ) +
						    (src[i * xdim + j] + (src[i * xdim + j] - src[i * xdim + j - xdim]) /2)) / 2;
			}
		}
		
		return(dst);
	}

	
    public static void expand4(int src[], int xdim, int ydim, int dst[])
    {
        int       i, j, x, y, new_xdim;
        new_xdim  = 2 * xdim;
        i         = 0; 
        j         = 0;

        // Process the top row.
        dst[j]                = src[i];
        dst[j + 1]            = (1333 * src[i] + 894 * src[i + 1]) / 2227;
        dst[j + new_xdim]     = (1333 * src[i] + 894 * src[i + xdim]) / 2227;
        dst[j + new_xdim + 1] = (1000  * src[i] + 447 * src[i + 1] + 447 * src[i + xdim] + 333 * src[i + xdim + 1]) / 2227;
        i++; 
        j                    += 2;
        for(x = 1; x < xdim - 1; x++)
        {
            dst[j]                = (1333 * src[i] + 894 * src[i - 1]) / 2227; 
            dst[j + 1]            = (1333 * src[i] + 894 * src[i + 1]) / 2227; 
            dst[j + new_xdim]     = (1000 * src[i] + 447 * src[i - 1] + 447 * src[i + xdim] + 333 * src[i + xdim - 1]) / 2227; 
            dst[j + new_xdim + 1] = (1000  * src[i] + 447 * src[i + 1] + 447 * src[i + xdim] + 333 * src[i + xdim + 1]) / 2227; 
            j += 2;
            i++;
        }
        dst[j]                = (1333 * src[i] + 894 * src[i - 1]) / 2227;
        dst[j + 1]            = src[i];
        dst[j + new_xdim]     = (1000  * src[i] + 447 * src[i - 1] + 447 * src[i + xdim] + 333 * src[i + xdim - 1]) / 2227;
        dst[j + new_xdim + 1] = (1333 * src[i] + 894 * src[i + xdim]) / 2227;
        i++; 
        j                    += new_xdim + 2;

        // Process the middle section.
        for(y = 1; y < ydim - 1; y++)
        {
            dst[j]                = (1333 * src[i] + 894 * src[i - xdim]) / 2227;
            dst[j + 1]            = (1000  * src[i] + 447 * src[i - xdim] + 447 * src[i + 1] + 333 * src[i - xdim + 1]) / 2227;
            dst[j + new_xdim]     = (1333 * src[i] +  894 * src[i + xdim]) / 2227; 
            dst[j + new_xdim + 1] = (1000  * src[i] + 447 * src[i + 1] + 447 * src[i + xdim] + 333 * src[i + xdim + 1]) / 2227;
            i++;
            j += 2;
            for(x = 1; x < xdim - 1; x++)
            {
                dst[j]                = (1000  * src[i] + 447 * src[i - xdim] + 447 * src[i - 1] + 333 * src[i - xdim - 1]) / 2227;
                dst[j + 1]            = (1000 * src[i] + 447 * src[i - xdim] + 447 * src[i + 1] + 333 * src[i - xdim + 1]) / 2227;
                dst[j + new_xdim]     = (1000 * src[i] + 447 * src[i + xdim] + 447 * src[i - 1] + 333 * src[i + xdim - 1]) / 2227; 
                dst[j + new_xdim + 1] = (1000 * src[i] + 447 * src[i + 1] + 447 * src[i + xdim] + 333 * src[i + xdim + 1]) / 2227;
                i++;
                j += 2;
            }
            dst[j]                = (1000  * src[i] + 894 * src[i - xdim] + 447 * src[i - 1]) / 2227;
            dst[j + 1]            = (1333 * src[i] + 894 * src[i - xdim]) / 2227;
            dst[j + new_xdim]     = (1000  * src[i] + 447 * src[i - 1] + 447 * src[i + xdim] + 333 * src[i + xdim - 1]) / 2227;
            dst[j + new_xdim + 1] = (1333 * src[i] + 894 * src[i + xdim]) / 2227; 
            i++;
            j += new_xdim + 2;
        }

        // Process the bottom row.
        dst[j]                = (1333 * src[i] + 894 * src[i - xdim]) / 2227;
        dst[j + 1]            = (1000  * src[i] + 447 * src[i + 1] + 447 * src[i - xdim] + 333 * src[i - xdim + 1]) / 2227;
        dst[j + new_xdim]     = src[i];
        dst[j + new_xdim + 1] = (1333 * src[i] + 894 * src[i + 1]) / 2227;
        i++;
        j                    += 2;
        for(x = 1; x < xdim - 1; x++)
        {
            dst[j]                = (1000  * src[i] + 447 * src[i - 1] + 447 * src[i - xdim] + 333 * src[i - xdim - 1]) / 2227; 
            dst[j + 1]            = (1000  * src[i] + 447 * src[i + 1] + 447 * src[i - xdim] + 333 * src[i - xdim + 1]) / 2227; 
            dst[j + new_xdim]     = (1333 * src[i] + 894 * src[i - 1]) / 2227; 
            dst[j + new_xdim + 1] = (1333 * src[i] + 894 * src[i + 1]) / 2227; 
            i++;
            j += 2;
        }
        dst[j]                = (1000  * src[i] + 447 * src[i - 1] + 447 * src[i - xdim] + 333 * src[i - xdim - 1]) / 2227;
        dst[j + 1]            = (1333 * src[i] + 894 * src[i - xdim]) / 2227;
        dst[j + new_xdim]     = (1333 * src[i] + 894 * src[i - 1]) / 2227;
        dst[j + new_xdim + 1] = src[i];
    }

    public static void shrink4(int src[], int xdim, int ydim, int dst[])
    {
        int    i, j, k, r, c;
        int    sum;
    
        r = ydim;
        c = xdim;
        k = 0;
        if(xdim % 2 == 0)
        {
            for(i = 0; i < r - 1; i += 2)
            {
                for (j = 0; j < c - 1; j += 2)
                {
                    sum = (src[i *c + j] + src[i * c + j + 1] + src[(i + 1) * c + j] + src[(i + 1) * c + j + 1]) / 4;
                    dst[k++] = sum;
                }
            }
        }
        else  // Looks wrong.
        {
            for(i = 0; i < r - 1; i += 2)
            {
                for(j = 0; j < c - 1; j += 2)
                {
                    sum = (src[i * c+j] + src[i * c + j + 1] + src[(i + 1) * c + j] + src[(i + 1) * c + j + 1]) / 4;
                    dst[k++] = sum;
                }
                k++;
            }
        }
    }
    
    public static void shrink4(int src[], int xdim, int ydim, int dst[], int error[])
    {
        int    i, j, k, r, c;
        int    sum;
    
        r = ydim;
        c = xdim;
        k = 0;
        if(xdim % 2 == 0)
        {
            for(i = 0; i < r - 1; i += 2)
            {
                for (j = 0; j < c - 1; j += 2)
                {
                	/*
                    sum = (src[i * c + j] + error[i * c + j] + src[i * c + j + 1] + error[i * c + j + 1] 
                    		+ src[(i + 1) * c + j] + error[(i + 1) * c + j] + src[(i + 1) * c + j + 1] + error[(i + 1) * c + j + 1]) / 4;
                    */
                	int current_error = (error[i * c + j] + error[(i + 1) * c + j]) / 2;
                
                	sum = (src[i * c + j] + src[i * c + j + 1] + src[(i + 1) * c + j]  + src[(i + 1) * c + j + 1] + (error[i * c + j + 1]  +  + error[(i + 1) * c + j + 1])) / 4 / 4;
                    dst[k++] = sum;
                }
            }
        }
        else // Looks wrong.
        {
            for(i = 0; i < r - 1; i += 2)
            {
                for(j = 0; j < c - 1; j += 2)
                {
                    sum = (src[i * c + j] + error[i * c + j] +  src[i * c + j + 1] + error[i * c + j + 1]
                    		+ src[(i + 1) * c + j] + error[(i + 1) * c + j] + src[(i + 1) * c + j + 1] + error[(i + 1) * c + j + 1]) / 4;
                    dst[k++] = sum;
                }
                k++;
            }
        }
    }
    
   
    
	public static void extract(int src[], int xdim, int ydim, int xoffset, int yoffset, int dst[], int new_xdim, int new_ydim)
	{
		int xend = xoffset + new_xdim;
		int yend = yoffset + new_ydim;
		int j = 0;
		for (int y = yoffset; y < yend; y++)
		{
			int i = y * xdim + xoffset;
			for (int x = xoffset; x < xend; x++)
			{
				dst[j] = src[i];
				j++;
				i++;
			}
		}
	}

	public static void avgAreaXTransform(int src[], int xdim, int ydim, int dst[], int new_xdim, int start_fraction[], int end_fraction[], int number_of_pixels[])
	{
		double differential = (double) xdim / (double) new_xdim;
		int weight          = (int) (differential * xdim);
		weight             *= 1000;
		int factor          = 1000 * xdim;

		double real_position        = 0.;
		int    current_whole_number = 0;
		for(int i = 0; i < new_xdim; i++)
		{
			double previous_position     = real_position;
			int    previous_whole_number = current_whole_number;
			real_position               += differential;
			current_whole_number         = (int) (real_position);
			number_of_pixels[i]          = current_whole_number - previous_whole_number;
			start_fraction[i]            = (int) (1000. * (1. - (previous_position - (double) (previous_whole_number))));
			end_fraction[i]              = (int) (1000. * (real_position - (double) (current_whole_number)));
		}

		int x = 0;
		for(int y = 0; y < ydim; y++)
		{
			int i = y * new_xdim;
			int j = y * xdim;
			for(x = 0; x < new_xdim - 1; x++)
			{
				if (number_of_pixels[x] == 0)
				{
					dst[i] = src[j];
					i++;
				} 
				else
				{
					int total = start_fraction[x] * xdim * src[j];
					j++;
					int k = number_of_pixels[x] - 1;
					while (k > 0)
					{
						total += factor * src[j];
						j++;
						k--;
					}
					total += end_fraction[x] * xdim * src[j];
					total /= weight;
					dst[i] = total;
					i++;
				}
			}
			if (number_of_pixels[x] == 0)
				dst[i] = src[j];
			else
			{
				int total = start_fraction[x] * xdim * src[j];
				j++;
				int k = number_of_pixels[x] - 1;
				while(k > 0)
				{
					total += factor * src[j];
					j++;
					k--;
				}
				total /= weight - end_fraction[x] * xdim;
				dst[i] = total;
			}
		}
	}

	public static void avgAreaYTransform(int src[], int xdim, int ydim, int dst[], int new_ydim, int start_fraction[],
			int end_fraction[], int number_of_pixels[])
	{
		int weight, current_whole_number, previous_whole_number;
		int total, factor;
		double real_position, differential, previous_position;

		differential = (double) ydim / (double) new_ydim;
		weight = (int) (differential * ydim);
		weight *= 1000;
		factor = ydim * 1000;

		real_position = 0.;
		current_whole_number = 0;
		for (int i = 0; i < new_ydim; i++)
		{
			previous_position = real_position;
			previous_whole_number = current_whole_number;
			real_position += differential;
			current_whole_number = (int) (real_position);
			number_of_pixels[i] = current_whole_number - previous_whole_number;
			start_fraction[i] = (int) (1000. * (1. - (previous_position - (double) (previous_whole_number))));
			end_fraction[i] = (int) (1000. * (real_position - (double) (current_whole_number)));
		}

		int y = 0;
		for (int x = 0; x < xdim; x++)
		{
			int i = x;
			int j = x;
			for (y = 0; y < new_ydim - 1; y++)
			{
				if (number_of_pixels[y] == 0)
				{
					dst[i] = src[j];
					i += xdim;
				} else
				{
					total = start_fraction[y] * ydim * src[j];
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
			if (number_of_pixels[y] == 0)
				dst[i] = src[j];
			else
			{
				total = start_fraction[y] * ydim * src[j];
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
	}

	public static void avgAreaTransform(int src[], int xdim, int ydim, int dst[], int new_xdim, int new_ydim, int workspace[],
			int start_fraction[], int end_fraction[], int number_of_pixels[])
	{
		avgAreaXTransform(src, xdim, ydim, workspace, new_xdim, start_fraction, end_fraction, number_of_pixels);
		avgAreaYTransform(workspace, new_xdim, ydim, dst, new_ydim, start_fraction, end_fraction, number_of_pixels);
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
		
		// Changing from 2-d array to 1-d array to simplify processing.
		// The main issue is a 2-d format complicates making the transform
		// work in both directions.  On the other hand, the 2-d format
		// is occasionally convenient.  
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
		
		// Back to 2-d.
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
			
		// Changing from 2-d array to 1-d array to simplify processing.
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
		
		// Back to 2-d.
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
			
		// Changing from 2-d array to 1-d array to simplify processing.
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
		
		// Back to 2-d.
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
	
}