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
			    // Double check.
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
				double y  = src[(i + 1) * xdim + j];
				double z  = src[(i + 1) * xdim + j + 1];
				
				double _w = error[i * xdim + j];
				double _x = error[i * xdim + j + 1];
				double _y = error[(i + 1) * xdim + j];
				double _z = error[(i + 1) * xdim + j + 1];
				
				double _error     = _w + _x + _y + _z;
				double error_root = 0;
				if(_error > 0)
				    error_root = Math.sqrt(_error);
				else
				{
					error_root = Math.sqrt(-_error);
					error_root = -error_root;
				}
				dst[k++] = (w + x + y + z) / 4. + error_root;
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
				int w  = src[i * xdim + j];
				int x  = src[i * xdim + j + 1];
				int y  = src[(i + 1) * xdim + j];
				int z  = src[(i + 1) * xdim + j + 1];
				
				int _w = error[i * xdim + j];
				int _x = error[i * xdim + j + 1];
				int _y = error[(i + 1) * xdim + j];
				int _z = error[(i + 1) * xdim + j + 1];
				
				int _error     = _w + _x + _y + _z;
				int error_root = 0;
				if(_error > 0)
				    error_root = (int)Math.sqrt(_error);
				else
				{
					error_root = (int)Math.sqrt(-_error);
					error_root = -error_root;
				}
				dst[k++]  = (int)((w + x + y + z) / 4. + error_root);
			}
		}
		return(dst);
	}
	
	
	public static double[] shrink4(double src[], int xdim, int ydim)
	{
		int _xdim = xdim / 2;
		int _ydim = ydim / 2;
		double [] dst = new double[_xdim * _ydim];
		
		int k = 0;
		int m = 0;
		for(int i = 0; i < _ydim; i++)
		{
			int n = 0;
			for(int j = 0; j < _xdim; j++)	
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
	
	public static double[] shrink4(double src[], int xdim, int ydim, double error[])
	{
		int _xdim = xdim / 2;
		int _ydim = ydim / 2;
		double [] dst = new double[_xdim * _ydim];
		
		int k = 0;
		int m = 0;
		for(int i = 0; i < _ydim; i++)
		{
			int n = 0;
			for(int j = 0; j < _xdim; j++)	
			{
				double w = src[m * xdim + n];
				double x = src[m * xdim + n + 1];
				double y = src[(m + 1) * xdim + n];
				double z = src[(m + 1) * xdim + n + 1];
				
				double _w = error[m * xdim + n];
				double _x = error[m * xdim + n + 1];
				double _y = error[(m + 1) * xdim + n];
				double _z = error[(m + 1) * xdim + n + 1];
				
				
				double _error     = _w + _x + _y + _z;
				double error_root = 0;
				if(_error > 0)
				    error_root = Math.sqrt(_error);
				else
				{
					error_root = Math.sqrt(-_error);
					error_root = -error_root;
				}
				dst[k++] = (w + x + y + z) / 4. + error_root;
			    n       += 2;
			}
			m += 2;
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
	
	public static double[] expandAverage(double src[], int xdim, int ydim)
	{
		double [] dst = new double[(xdim + 1) * (ydim + 1)];
		
		// Expanding the NW quadrant w/ left up filter.
		for(int i = 0; i < ydim - 1; i++)
		{
		    int k = i * xdim;
		    for(int j = 0; j < xdim - 1; j++)
		    {
		    	
		        dst[k++] = (src[i * xdim + j] + src[i * xdim + j + 1] +
		                    src[i * xdim + j + xdim] + src[i * xdim + j + xdim + 1]) / 4;
		    }
		}
		
		
		// Expanding the NE quadrant w/ right up filter.
		for(int i = 0; i < ydim - 1; i++)
		{
		    int k  = i * xdim + xdim - 1;
		    int j  = xdim - 1;
		    dst[k] = (src[i * xdim + j] + src[i * xdim + j - 1] + src[i * xdim + xdim + j] + src[i * xdim + xdim + j - 1]) / 4;
		}
		
		// Expanding the SW quadrant w/ left down filter.
		int i = ydim - 1;
		int k = i * xdim;
		for(int j = 0; j < xdim - 2; j++)
		{
		    dst[k++] = ((src[i * xdim + j] + src[i * xdim + j + 1] + (src[(i - 1) * xdim + j] - src[(i - 1) * xdim + j + 1]) /2)) / 4;
		}
		
		// Expanding the SE quadrant w/ right down filter.
		i = ydim - 1;
		int j = xdim - 1;
		k = (i + 1) * xdim - 1;
		dst[k] = ((src[i * xdim + j] +  src[i * xdim + j - 1] + + (src[(i - 1) * xdim + j] - src[(i - 1) * xdim + j - 1]) /2)) / 4;
		
		return(dst);
	}

	public static double[] expandGradient(double src[], int xdim, int ydim)
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

	public static double[] expand4(double src[], int xdim, int ydim)
    {
		int _xdim = xdim * 2;
		int _ydim = ydim * 2;
		
		double[] dst = new double[_xdim * _ydim];
		
		for(int i = 0; i < ydim - 1; i++)
		{
			for(int j = 0; j < xdim - 1; j++)
			{
				int k              = (2 * i + 1) * _xdim + 1 + 2 * j;
				dst[k]             = (src[i * xdim + j]           + (src[i * xdim + j + 1] + src[(i + 1) * xdim + j])     / 2) / 2;
				dst[k + 1]         = (src[i * xdim + j + 1]       + (src[i * xdim + j]     + src[(i + 1) * xdim + j + 1]) / 2) / 2;
				dst[k + _xdim]     = (src[(i + 1) * xdim + j]     + (src[i * xdim + j]     + src[(i + 1) * xdim + j + 1]) / 2) / 2;
				dst[k + _xdim + 1] = (src[(i + 1) * xdim + j + 1] + (src[i * xdim + j + 1] + src[(i + 1) * xdim + j])     / 2) / 2;
			
			}
		}
	    
		dst[0] = src[0];
		for(int j = 1; j < _xdim - 1; j++)
		    dst[j] = (src[j / 2] + dst[_xdim + j]) / 2;
	    dst[_xdim - 1] = src[xdim - 1];
	    
	    dst[_xdim * (_ydim - 1)] = src[xdim * (ydim - 1)];
	    for(int j = 1; j < _xdim - 1; j++)
		    dst[_xdim * (_ydim - 1) + j] = (src[j / 2] + dst[_xdim + j]) / 2;
	    dst[_xdim * _ydim - 1] = src[xdim * ydim - 1];
	    
	    for(int i = 1; i < _ydim - 1; i++)
	    {
	        dst[i * _xdim]             = (src[(i / 2) * xdim]            + dst[i * _xdim + 1])	       / 2;
	        dst[i * _xdim + _xdim - 1] = (src[(i / 2) * xdim + xdim - 1] + dst[i * _xdim + _xdim - 2]) / 2;
	    }
		
		return dst;
    }
	
	 
    public static int[] getDeltasFromValues(int src[], int xdim, int ydim)
    {
        int[] dst = new int[xdim * ydim];
    	
        int k     = 0;
        int value = 126;
        for(int i = 0; i < ydim; i++)
        {
            int delta     = src[k] - value;
            value        += delta;
            dst[k++]      = delta;
            int current_value = value; 
            for(int j = 1; j < xdim; j++)
            {
                delta          = src[k]  - current_value;
                current_value += delta;
                dst[k++]       = delta;
            }
        }
        
        return dst;
    }

    public static int[] getValuesFromDeltas(int src[],int xdim, int ydim)
    {
    	int[] dst = new int[xdim * ydim];
    	
        int k     = 0;
        int value = 126;
        for(int i = 0; i < ydim; i++)
        {
            value            += src[k];
            int current_value = value;
            dst[k++]          = current_value;
            for(int j = 1; j < xdim; j++)
            {
                current_value += src[k];
                dst[k++]       = current_value;
            }
        }
        
        return dst;
    }
    
    public static int packStrings(int src[], int size, int number_of_different_values, byte dst[])
    {
        int i, j, k;
        int current_byte;
        byte current_bit, value; 
        byte next_bit; 
        int  index, second_index;
        int  number_of_bits;
        int  table[], inverse_table[], mask[];
    
        /*
        inverse_table = new int[number_of_different_values]; 
        inverse_table[0] = number_of_different_values / 2;
        i = j = number_of_different_values / 2;
        k = 1;
        j++;
        i--;
        while(i >= 0 && k < number_of_different_values)
        {
            inverse_table[k] = j;
            k++;
            j++;
            if(k < number_of_different_values)
                inverse_table[k] = i;
            k++;
            i--;
        }
        */
        
        
       

        table = new int[number_of_different_values];
        
        i = 0;
        
        if(number_of_different_values % 2 == 1)
            j = number_of_different_values / 2;
        else
        	j = number_of_different_values / 2 - 1;
        
        table[j--] = i;
        i += 2;
        while(j >= 0)
        {
        	table[j--] = i;
        	i += 2;
        }
        
        if(number_of_different_values % 2 == 1)
            j = number_of_different_values / 2 + 1;
        else
        	j = number_of_different_values / 2;
        
        i = 1;
        table[j++] = i;
        i += 2;
        while(j < number_of_different_values)
        {
        	table[j++] = i;
        	i += 2;
        }
        
        mask  = new int[8];
        mask[0] = 1;
        for(i = 1; i < 8; i++)
        {
            mask[i] = mask[i - 1];
            mask[i] *= 2;
            mask[i]++;
        }
    
        current_bit = 0;
        current_byte = 0;
        dst[current_byte] = 0;
        for(i = 0; i < size; i++)
        {
            k     = src[i];
            index = table[k];
            if(index == 0)
            {
                current_bit++;
                if(current_bit == 8)
                    dst[++current_byte] = current_bit = 0;
            }
            else
            {
                next_bit = (byte)((current_bit + index + 1) % 8);
                if(index <= 7)
                {
                    dst[current_byte] |= (byte) (mask[index - 1] << current_bit);
                    if(next_bit <= current_bit)
                    {
                        dst[++current_byte] = 0;
                        if(next_bit != 0)
                            dst[current_byte] |= (byte)(mask[index - 1] >> (8 - current_bit));
                    }
                }
                else if(index > 7)
                {
                    dst[current_byte] |= (byte)(mask[7] << current_bit);
                    j = (index - 8) / 8;
                    for(k = 0; k < j; k++)
                        dst[++current_byte] = (byte)(mask[7]);
                    dst[++current_byte] = 0;
                    if(current_bit != 0)
                        dst[current_byte] |= (byte)(mask[7] >> (8 - current_bit));
    
                    if(index % 8 != 0)
                    {
                        second_index = index % 8 - 1;
                        dst[current_byte] |= (byte)(mask[second_index] << current_bit);
                        if(next_bit <= current_bit)
                        {
                            dst[++current_byte] = 0;
                            if(next_bit != 0)
                                dst[current_byte] |= (byte)(mask[second_index] >> (8 - current_bit));
                        }
                    }
                    else if(next_bit <= current_bit)
                            dst[++current_byte] = 0;
                }
                current_bit = next_bit;
            }
        }
        if(current_bit != 0)
            current_byte++;
        number_of_bits = current_byte * 8;
        if(current_bit != 0)
            number_of_bits -= 8 - current_bit;
        return(number_of_bits);
    }
    
    public static int unpackStrings(byte src[], int number_of_different_values, int dst[], int size)
    {
        int  number_of_bytes_unpacked;
        int  current_src_byte, current_dst_byte;
        byte non_zero, mask, current_bit;
        int  index, current_length;
        int  table[], inverse_table[];
        int  i, j, k;
    
        
        table = new int[number_of_different_values];
        
        i = 0;
        
        if(number_of_different_values % 2 == 1)
            j = number_of_different_values / 2;
        else
        	j = number_of_different_values / 2 - 1;
        
        table[j--] = i;
        i += 2;
        while(j >= 0)
        {
        	table[j--] = i;
        	i += 2;
        }
        
        if(number_of_different_values % 2 == 1)
            j = number_of_different_values / 2 + 1;
        else
        	j = number_of_different_values / 2;
        
        i = 1;
        table[j++] = i;
        i += 2;
        while(j < number_of_different_values)
        {
        	table[j++] = i;
        	i += 2;
        }
        
        inverse_table = new int[number_of_different_values];
        for(i = 0; i < number_of_different_values; i++)
        {
            j = table[i];
            inverse_table[j] = i;
        }
        
        current_length = 1;
        current_src_byte = 0;
        mask = 0x01;
        current_bit = 0;
        current_dst_byte = 0;
        while(current_dst_byte < size)
        {
            non_zero = (byte)(src[current_src_byte] & (byte)(mask << current_bit));
            if(non_zero != 0)
                current_length++;
            else
            {
                index = current_length - 1;
                dst[current_dst_byte++] = table[index];
                current_length = 1;
            }
            current_bit++;
            if(current_bit == 8)
            {
                current_bit = 0;
                current_src_byte++;
            }
        }
        if(current_bit == 0)
            number_of_bytes_unpacked = current_src_byte;
        else
            number_of_bytes_unpacked = current_src_byte + 1;
        return(number_of_bytes_unpacked);
    }
    
    public static int compressZeroBits(byte src[], int size, byte dst[])
    {
        byte mask, current_bit;
        int  current_byte;
        int  number_of_zero_bits;
        int  number_of_bits;
        int  current_length;
        int  i, j, k;
        int  minimum_amount_of_compression, byte_size;

        byte_size = size / 8;
        minimum_amount_of_compression = 0;
        j = k = number_of_zero_bits = 0;
        current_byte = 0;
        current_bit = 0;
        mask = 0x01;
        dst[0] = 0;

        for(i = 0; i < size; i++)
        {
            if((current_byte + (byte_size - i / 8) / 2 + minimum_amount_of_compression) > byte_size) 
                return(-1);
            if((src[k] & (mask << j)) == 0)
            {
                i++;
                j++;
                if(j == 8)
                {
                    j = 0;
                    k++;
                }
                if((src[k] & (mask << j)) == 0)
                {
                    number_of_zero_bits++;
                    current_bit++;
                    if(current_bit == 8)
                    {
                        current_byte++;
                        current_bit = dst[current_byte] = 0;
                    }
                }
                else
                {
                    dst[current_byte] |= (byte)mask << current_bit;
                    current_bit++;
                    if(current_bit == 8)
                    {
                        current_byte++;
                        current_bit = dst[current_byte] = 0;
                    }
                    dst[current_byte] |= (byte)mask << current_bit;
                    current_bit++;
                    if(current_bit == 8)
                    {
                        current_byte++;
                        current_bit = dst[current_byte] = 0;
                    }
                }
            }
            else
            {
                dst[current_byte] |= (byte)mask << current_bit;
                current_bit++;
                if(current_bit == 8)
                {
                    current_byte++;
                    current_bit = dst[current_byte] = 0;
                }
                number_of_zero_bits++;
                current_bit++;
                if(current_bit == 8)
                {
                    current_byte++;
                    current_bit = dst[current_byte] = 0;
                }
            }
            j++;
            if(j == 8)
            {
                j = 0;
                k++;
            }
        }    
       
        /*
        if(current_bit != 0)
            current_byte++;
        number_of_bits = current_byte * 8;
        if(current_bit != 0)
            number_of_bits -= 8 - current_bit;
        */
        number_of_bits = current_byte * 8;
        number_of_bits += current_bit;
        return(number_of_bits);
    }

    public static int decompressZeroBits(byte src[], int size, byte dst[])
    {
        byte mask, current_bit;
        int  current_byte;
        int  current_length;
        int  number_of_bits;
        int  i, j, k;

        current_length = 0;
        current_byte = 0;
        current_bit  = 0;
        dst[0] = 0;
        mask = 0x01;
        j = k = 0;
       
        for(i = 0; i < size; i++)
        {
            if((src[k] & (mask << j)) != 0)
            {
                i++;
                j++;
                if(j == 8)
                {
                    j = 0;
                    k++;
                }
                if((src[k] & (mask << j)) != 0)
                {
                    current_bit++;
                    if(current_bit == 8)
                    {
                        current_byte++;
                        current_bit = dst[current_byte] = 0;
                    }
                    dst[current_byte] |= (byte)mask << current_bit;
                    current_bit++;
                    if(current_bit == 8)
                    {
                        current_byte++;
                        current_bit = dst[current_byte] = 0;
                    }
                }
                else
                {
                    dst[current_byte] |= (byte)mask << current_bit;
                    current_bit++;
                    if(current_bit == 8)
                    {
                        current_byte++;
                        current_bit = dst[current_byte] = 0;
                    }
                }
            }
            else
            {
                current_bit++;
                if(current_bit == 8)
                {
                    current_byte++;
                    current_bit = dst[current_byte] = 0;
                }
                current_bit++;
                if(current_bit == 8)
                {
                    current_byte++;
                    current_bit = dst[current_byte] = 0;
                }
            }
            j++;
            if(j == 8)
            {
                j = 0;
                k++;
            }
        }    
        
        /*
        if(current_bit != 0)
            current_byte++;
        number_of_bits = current_byte * 8;
        if(current_bit != 0)
            number_of_bits -= 8 - current_bit;
        */
        number_of_bits = current_byte * 8;
        number_of_bits += current_bit;
        return(number_of_bits);
    }

    public static int compressStrings(byte src[], int size, byte dst[])
    {
        int number_of_iterations, current_size, previous_size;
        int byte_size, i;
        byte mask;
        byte[]  temp = new byte[5 * size];
        
        byte_size = size / 8;
        if(size % 8 != 0)
            byte_size++;
        number_of_iterations = 1;
        current_size = previous_size = size;
        current_size = compressZeroBits(src, previous_size, dst);
        while(current_size < previous_size && current_size > 1)
        {
            previous_size = current_size;
            if(number_of_iterations % 2 == 1)
                current_size = compressZeroBits(dst, previous_size, temp);
            else
                current_size = compressZeroBits(temp, previous_size, dst);
            number_of_iterations++;
        }
        if(current_size == -1 && number_of_iterations > 1)
        {
            current_size = previous_size;
            number_of_iterations--;
        }
        if(number_of_iterations % 2 == 0)
        {
            byte_size = current_size / 8;
            if(current_size % 8 != 0)
                byte_size++; 
            for(i = 0; i < byte_size; i++)
                dst[i] = temp[i];
        }        
    
        if(current_size > 0)
        {
            System.out.println("The number of iterations was " + number_of_iterations);
            if(current_size % 8 == 0)
            {
                byte_size      = current_size / 8;
                dst[byte_size] = (byte) number_of_iterations;
                dst[byte_size] &= 127;
            }
            else
            {
                int  remainder = current_size % 8;
                byte_size = current_size / 8;

                dst[byte_size] |= (byte) (number_of_iterations << remainder);
                byte_size++;
                dst[byte_size] = 0;
                if(remainder > 1)
                    dst[byte_size] = (byte) (number_of_iterations >> 8 - remainder);
            }
            return(current_size + 8);
        }
        return(current_size);
    }
    
    public static int decompressStrings(byte src[], int size, byte dst[])
    {
        int  byte_index;
        int  number_of_iterations;
        int  addend;
        int  mask;
        int  remainder, i;
        int  previous_size, current_size, byte_size;
        byte[]  temp = new byte[5 * size];
        
        byte_index = size / 8 - 1;
        remainder  = size % 8;
        if(remainder != 0)
        {
            int value = 254;
            addend = 2;
            for(i = 1; i < remainder; i++)
            {
                value -= addend;
                addend <<= 1;
            } 
            mask = value;
            number_of_iterations = src[byte_index];
            if(number_of_iterations < 0)
                number_of_iterations += 256;
            number_of_iterations &= mask;
            number_of_iterations >>= remainder;
            byte_index++;
            if(remainder > 1)
            {
                mask = 1;
                for(i = 2; i < remainder; i++)
                {
                    mask <<= 1;
                    mask++;
                }
                addend = src[byte_index]; 
                if(addend < 0)
                    addend += 256;
                addend &= mask;
                addend <<= 8 - remainder;
                number_of_iterations += addend;
                mask++;
            }
            else
                mask = 1;
        }
        else
        {
           mask = 127;
           number_of_iterations = src[byte_index];
           if(number_of_iterations < 0)
               number_of_iterations += 256; 
           number_of_iterations &= mask;
           mask++;
        }
        
        System.out.println("The number of iterations was " + number_of_iterations);
        
        current_size = 0;
        if(number_of_iterations == 1)
        {
            current_size = decompressZeroBits(src, size - 8, dst);
            number_of_iterations--;
        }
        else if(number_of_iterations % 2 == 0)
        {
            current_size = decompressZeroBits(src, size - 8, temp);
            number_of_iterations--;
            while(number_of_iterations > 0)
            {
                previous_size = current_size;
                if(number_of_iterations % 2 == 0)
                    current_size = decompressZeroBits(dst, previous_size, temp);
                else
                    current_size = decompressZeroBits(temp, previous_size, dst);
                number_of_iterations--;
            }
        }
        else
        {
            current_size = decompressZeroBits(src, size - 8, dst);
            number_of_iterations--;
            while(number_of_iterations > 0)
            {
                previous_size = current_size;
                if(number_of_iterations % 2 == 0)
                    current_size = decompressZeroBits(dst, previous_size, temp);
                else
                    current_size = decompressZeroBits(temp, previous_size, dst);
                number_of_iterations--;
            }
        }
        return(current_size);
    }
}