import java.util.*;
import java.util.zip.*;
import java.lang.Math.*;
import java.math.*;

public class WaveletMapper
{
	public static int[] transpose(int []src, int xdim)
	{
		int    ydim = src.length / xdim;
		int [] dst  = new int[src.length];
		
		for(int i = 0; i < src.length; i++)
	    	dst[i] = src[i];
		
		int k = 0;
		for (int i = 0; i < ydim; i++)
		    for (int j = 0; j < xdim; j++)
		    	dst[k++] = src[j * ydim + i];
		
		return dst;
	}
	
	public static int[] half_transpose(int []src, int xdim)
	{
		int    ydim = src.length / xdim;
		int [] dst  = new int[src.length];
		
	
		for(int i = 0; i < src.length; i++)
	    	dst[i] = src[i];
	    
		
		int k = 0;
		for (int i = 0; i < ydim /2; i++)
		    for (int j = 0; j < xdim; j++)
		    	dst[k++] = src[j * ydim + i];
		
		return dst;
	}
	
	public static int [] half_transpose2(int []src, int xdim)
	{
	    int ydim  = src.length / xdim;
	    int dst[] = new int[src.length];
	    
	   
	    /*
	    for(int i = 0; i < src.length; i++)
	    	dst[i] = src[i];
	    */
	    
	    
	    int k = 0;
	    for(int i = 0; i < xdim / 2; i++)
	        for (int j = 0; j < ydim; j++)
	            dst[j * xdim + i] = src[k++];
	    return dst;
	}
	
	// Replaces a row or part of a row in the source.
	public static void daub4(int [] src, int offset, int xdim)
	{
		double [] c       = {0.4829629, 0.8365163, 0.2241438, -0.129409};
		int    [] buffer = new int[xdim];
		int i     = 0;
		int k     = offset;
		int m     = xdim / 2;
		
		for(int j = 0; j <= xdim - 4; j += 2)
		{
			buffer[i]     = (int)(c[0] * src[j + k] + c[1] * src[j + k + 1] + c[2] * src[j + k + 2] + c[3] * src[j + k + 3]);
			buffer[i + m] = (int)(c[3] * src[j + k] - c[2] * src[j + k + 1] + c[1] * src[j + k + 2] - c[0] * src[j + k + 3]);	
			i++;
		}
		buffer[i]     = (int)(c[0] * src[k + xdim - 2] + c[1] * src[k + xdim - 1] + c[2] * src[k] + c[3] * src[k + 1]);
		buffer[i + m] = (int)(c[3] * src[k + xdim - 2] - c[2] * src[k + xdim - 1] + c[1] * src[k] - c[0] * src[k + 1]);
		
		for(i = 0; i < xdim; i++)
		    src[i + k] = buffer[i]; 
	}
	
	public static void inverse_daub4(int [] src, int offset, int xdim)
	{
		double [] c       = {0.4829629, 0.8365163, 0.2241438, -0.129409};
		int    [] buffer = new int[xdim];
		int i, j;
		int k  = offset;
		int m  = xdim / 2 - 1;
		int n  = m + 1;
		int[]  value = new int[4];
		
		buffer[0] = (int)(c[2] * src[k + m] + c[1] * src[k + xdim - 1] + c[0] * src[k + n] + c[3] * src[k + n]);
		buffer[1] = (int)(c[3] * src[k + m] - c[0] * src[k + xdim - 1] + c[1] * src[k]     - c[2] * src[k + n]);
		
		for (i = 0, j = 2; i < m; i++)  
	    {
		    value[0] = src[i + k];
		    value[1] = src[i + k + 1];
		    value[2] = src[i + k + n];
		    value[3] = src[i + k + n + 1];
		
		    buffer[j++] = (int)(c[2] * value[0] + c[1] * value[2] + c[0] * value[1] + c[3] * value[3]);
		    buffer[j++] = (int)(c[3] * value[0] - c[0] * value[2] + c[1] * value[1] - c[2] * value[3]);
	    }
		
		for(i = 0; i < xdim; i++)
		    src[i + k] = buffer[i];
	}
	
	public static int [] forward_transform(int [] src, int xdim)
	{
		int [] buffer = new int[src.length];
		for(int i = 0; i < src.length; i++)
			buffer[i] = src[i];
		int ydim = src.length / xdim;
		
		int min  = xdim;
		while(min % 2 == 0)
			min /= 2;
		min *= 2;
		
		if(min < 4)
			min = 4;
		for(int i = 0; i < ydim; i++)
		{
			int offset = i * xdim;
			for(int j = xdim; j >= min; j /= 2)
			{
				daub4(buffer, offset, j);
			}
		}
		
		int [] dst =  half_transpose(buffer, xdim);
		
		min  = ydim;
		while(min % 2 == 0)
			min /= 2;
		min *= 2;
		if(min < 4)
			min = 4;
		for(int i = 0; i < xdim /2; i++)  
		{
		    int offset = i * ydim;
		    for(int j = ydim; j >= min; j /= 2)
		    {
		    	daub4(dst, offset, j);	
		    }
		}
		
		return dst;
	}
	
	public static int [] inverse_transform(int [] src, int xdim)
	{
	    int ydim = src.length / xdim;
	    int min = ydim;
	    while (min % 2 == 0)
	        min /= 2;
	    min  *= 2;
	    if(min < 4)
	    	min = 4;
	    int [] buffer = new int[src.length];
	    for(int i = 0; i < src.length; i++)
	    	buffer[i] = src[i];
	    for(int i = 0; i < xdim / 2; i++)  
	    {
	        int offset = i * ydim;
	        for(int j = min; j <= ydim; j *= 2)
	        {
	        	inverse_daub4(buffer, offset, j);
	        }
	    }

	    int [] dst = half_transpose2(buffer, xdim);

	    min = xdim;
	    while(min % 2 == 0)
	       min /= 2;
	    min *= 2;
	    if(min < 4)
	    	min = 4;

	    for(int i = 0; i < ydim ; i++)
	    {
	        int offset = i * xdim;
	    	for(int j = min; j <= xdim; j *= 2)
	    	{
	    		inverse_daub4(dst, offset, j);	
	    	}
	    }
	    
	    for(int i = 0; i < dst.length; i++)
	    {
	    	if(dst[i] < 0)
	    		dst[i] = 0;
	    	else if(dst[i] > 255)
	    		dst[i] = 255;
	    }
	    
	    return dst;
	}
	
	public static int[] getPixel(int[] blue, int[] green, int[] red, int xdim)
	{
        int ydim = blue.length / xdim;
        int [] pixel = new int[blue.length];
       
		int blue_shift  = 16;		
		int green_shift = 8;	
		
		int k = 0;
		for(int i = 0; i < ydim; i++)
		{
			for(int j = 0; j < xdim; j++)
			{
				pixel[k] = (blue[k] << blue_shift) + (green[k] << green_shift) + red[k];
			    k++;
			}
		}
		return pixel;
	}
}