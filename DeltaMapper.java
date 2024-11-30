import java.util.*;
import java.util.zip.*;
import java.lang.Math.*;
import java.math.*;

public class DeltaMapper
{
	public static int[] getDifference(int src1[], int src2[])
	{
		int length = src1.length;
		int [] difference = new int[length];
		
		if(src2.length == length)
		{
		    for(int i = 0; i < length; i++)
		    {
			    difference[i] = src1[i] - src2[i];
		    }
		}
		return(difference);
	}
	
	public static int[] getSum(int src1[], int src2[])
	{
		int length = src1.length;
		int [] sum = new int[length];
		
		if(src2.length == length) 
		{
		    for(int i = 0; i < length; i++)
		    {
			    sum[i] = src1[i] + src2[i];
		    }
		}
		return(sum);
	}
	
    public static int getIdealSum(int src[], int xdim, int ydim)
    {
        int init_value     = src[0];
        int value          = init_value;
        int delta          = 0;
        
        int sum            = 0;
        int k              = 0;
         
        k = 0;
        for(int i = 0; i < ydim; i++)
        {
        	if(i == 0)
        	{
                for(int j = 0; j < xdim; j++)
                {
            	    if(j == 0)
            	    {
            			k++;
            	    }
            		else
            		{
            		    delta     = src[k] - value;
                        value     += delta;
                        k++;
                        sum      += Math.abs(delta);
            		}
            	}
            }
        	else
        	{
        		for(int j = 0; j < xdim; j++)
                {
            	    if(j == 0)
            	    {
            	    	delta      = src[k] - init_value;
            	    	init_value = src[k];
            	    	k++;
            	        sum += Math.abs(delta);
            	    }
            	    else
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
            	    	{
            	    	    delta = delta_a;
            	    	
            	    	}
            	    	else if(delta_b <= delta_c && delta_b <= delta_d)
            	    	{
            	    	    delta = delta_b;
            	    	}
            	    	else if(delta_c <= delta_d)
            	    	{
            	    	    delta = delta_c;
            	    	}
            	    	else
            	    	{
            	    		delta = delta_d;
            	    	}
            	    	k++;
            	    	sum += Math.abs(delta);
            	    }
                }
        	}
        }
        return sum;
    }

    public static int getIdealSum(int src[], int xdim, int ydim, int interval)
    {
        int sum   = 0;
        int delta = 0;
        for(int i = 1; i < ydim; i++)
        {
        	int k = i * xdim + 1;
        	for(int j = 1; j < xdim - 1; j += interval)
            {
                int a = src[k - 1];
            	int b = src[k - xdim];
            	int c = src[k - xdim - 1];
            	int d = src[k - xdim - 1];
            	int e = src[k];
            	
            	int delta_a = Math.abs(a - e);
            	int delta_b = Math.abs(b - e);
            	int delta_c = Math.abs(c - e);
            	int delta_d = Math.abs(d - e);
            	    	   	
            	if(delta_a <= delta_b && delta_a <= delta_c && delta_a <= delta_d)
    	    	{
    	    	    delta = delta_a;
    	    	}
    	    	else if(delta_b <= delta_c && delta_b <= delta_d)
    	    	{
    	    	    delta = delta_b;
    	    	}
    	    	else if(delta_c <= delta_d)
    	    	{
    	    	    delta = delta_c;
    	    	}
    	    	else
    	    	{
    	    		delta = delta_d;
    	    	}
            	sum += delta;
            	k += interval;  	
        	}
        }
        return sum;
    }
    
    public static ArrayList getHorizontalDeltasFromValues(int src[], int xdim, int ydim)
    {
        int[] dst            = new int[xdim * ydim];
        int   sum            = 0;
        int   init_value     = src[0];
        int   value          = init_value;
         
        int k     = 0;
        for(int i = 0; i < ydim; i++)
        {
        	// We set the first value to zero to mark the type of deltas as horizontal.
        	// We don't want to include the initial value because it could easily be
        	// much larger than any of our deltas.
            if(i == 0)
        	    dst[k++] = 0;
            else
            {
            	int delta   = src[k] - init_value;
            	dst[k++]    = delta;
            	init_value += delta;
            	sum        += Math.abs(delta);
            	value       = init_value;
            }
            
            for(int j = 1; j < xdim; j++)
            {
                int delta = src[k]  - value;
                value    += delta;
                sum      += Math.abs(delta);
                dst[k++]  = delta;
            }
        }
        
        ArrayList result = new ArrayList();
        result.add(sum);
        result.add(dst);
        return result;
    }
    
    public static int[] getValuesFromHorizontalDeltas(int src[],int xdim, int ydim, int init_value)
    {
    	int[] dst = new int[xdim * ydim];
    	
        int k     = 0;
        int value = init_value;
        for(int i = 0; i < ydim; i++)
        {
        	if(i != 0)
                value += src[k];
        	else
        	{
        		if(src[k] != 0)
        			System.out.println("Wrong code.");
        	}
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
    
    public static ArrayList getVerticalDeltasFromValues(int src[], int xdim, int ydim)
    {
        int[] dst          = new int[xdim * ydim];
        int init_value     = src[0];
        int value          = init_value;
        int delta          = 0;
       
        int sum            = 0;
        
        int k          = 0;
        for(int i = 0; i < ydim; i++)
        {
            for(int j = 0; j < xdim; j++)
            {
            	if(i == 0)
            	{
            		// Setting the first value to 1 mark the type of deltas vertical.
            		if(j == 0)
            			dst[k++] = 1;
            		else
            		{
            		    delta    = src[k] - value;
                        value    += delta;
                        dst[k++]  = delta;
                        sum      += Math.abs(delta);
            		}
            	}
            	else
            	{
                    delta    = src[k]  - src[k - xdim];
                    dst[k++] = delta;
                    sum     += Math.abs(delta);
            	}
            }
        }
        
        ArrayList result = new ArrayList();
        result.add(sum);
        result.add(dst);
        return result;
    }

    public static int[] getValuesFromVerticalDeltas(int src[], int xdim, int ydim, int init_value)
    {
    	int[] dst = new int[xdim * ydim];
        dst[0] = init_value;
        int value = init_value;
        
        if(src[0] != 1)
            System.out.println("Wrong code.");
        
        for(int i = 1; i < xdim; i++)
        {
        	value   += src[i];
        	dst[i] = value;
        }
        
        // Now we can use values from the destination data to get the rest of the values. 
        for(int i = 1; i < ydim; i++)
        {
            for(int j = 0; j < xdim; j++)
            {
            	int index  = i * xdim + j;
            	dst[index] = dst[index - xdim] + src[index]; 
            }
        }
       
        return dst;
    }
    
    // This can get a better result than the other functions that rely on preceding
    // values because the average of two pixel values can be closer to a pixel value
    // than any actual value.
    public static ArrayList getAverageDeltasFromValues(int src[], int xdim, int ydim)
    {
        int[] dst            = new int[xdim * ydim];
        int   sum            = 0;
        int   init_value     = src[0];
        int   value          = init_value;
         
        int k     = 0;
        for(int i = 0; i < ydim; i++)
        {
        	// We set the first value to 2 to mark the type of deltas as the average of a and b.
            if(i == 0)
        	    dst[k++] = 2;
            else
            {
            	int delta   = src[k] - init_value;
            	dst[k++]    = delta;
            	init_value += delta;
            	sum        += Math.abs(delta);
            	value       = init_value;
            }
            
            if(i == 0)
            {
                for(int j = 1; j < xdim; j++)
                {
                    int delta = src[k] - value;
                    dst[k++]  = delta;
                    value    += delta;
                    sum      += Math.abs(delta);
                }
            }
            else
            {
            	for(int j = 1; j < xdim; j++)
                {
            		int average = (src[k - 1] + src[k - xdim]) / 2;
                    int delta   = src[k]  - average;
                    dst[k++]    = delta;
                    sum        += Math.abs(delta);
                }   	
            }
        }
        
        ArrayList result = new ArrayList();
        result.add(sum);
        result.add(dst);
        return result;
    }
    
    public static int[] getValuesFromAverageDeltas(int src[],int xdim, int ydim, int init_value)
    {
    	int[] dst = new int[xdim * ydim];
    	
        int k     = 0;
        int value = init_value;
        for(int i = 0; i < ydim; i++)
        {
        	if(i != 0)
                value += src[k];
        	else
        	{
        		if(src[k] != 2)
        			System.out.println("Wrong code.");
        	}
            int current_value = value;
            dst[k++]          = current_value;
            
            if(i == 0)
            {
                for(int j = 1; j < xdim; j++)
                {
                    current_value += src[k];
                    dst[k++]       = current_value;
                }
            }
            else
            {
            	for(int j = 1; j < xdim; j++)
                {
            		int average   = (dst[k - 1] + dst[k - xdim]) / 2;
                    current_value = average + src[k];
                    dst[k++]      = current_value;
                }   	
            }
        }
        return dst;
    }
    
    // This is an attempt to include all possible values from preceding known values
    // including their averages.  It does more poorly than a random function for 
    // reasons that are not understood, probably an error in the implementation.
    public static ArrayList getAverageDeltasFromValues2(int src[], int xdim, int ydim)
    {
        int[] dst            = new int[xdim * ydim];
        int   sum            = 0;
        int   init_value     = src[0];
        int   value          = init_value;
         
        int k     = 0;
        for(int i = 0; i < ydim; i++)
        {
        	// We set the first value to 2 to mark the type of deltas as the average of a, b, c, or d.
            if(i == 0)
        	    dst[k++] = 2;
            else
            {
            	int delta   = src[k] - init_value;
            	dst[k++]    = delta;
            	init_value += delta;
            	sum        += Math.abs(delta);
            	value       = init_value;
            }
            
            if(i == 0)
            {
                for(int j = 1; j < xdim; j++)
                {
                    int delta = src[k] - value;
                    dst[k++]  = delta;
                    value    += delta;
                    sum      += Math.abs(delta);
                }
            }
            else
            {
            	for(int j = 1; j < xdim - 1; j++)
                {
            		int ab_delta = Math.abs(src[k - xdim - 1] - src[k - xdim]);
            		int ac_delta = Math.abs(src[k - xdim - 1] - src[k - xdim + 1]);
            		int ad_delta = Math.abs(src[k - xdim - 1] - src[k - 1]);
            		int bc_delta = Math.abs(src[k - xdim] - src[k - xdim + 1]);
            		int bd_delta = Math.abs(src[k - xdim] - src[k - 1]);
            		int cd_delta = Math.abs(src[k - xdim + 1] - src[k - 1]);
            		
            		if(ab_delta >= ac_delta && ab_delta >= ad_delta && ab_delta >= bc_delta && ab_delta >= bd_delta && ab_delta >= cd_delta)
            		{
            			int average = (src[k - xdim - 1] - src[k - xdim]) / 2;
                        int delta   = src[k]  - average;
                        dst[k++]    = delta;
                        sum        += Math.abs(delta);	
            		}
            		else if(ac_delta >= ad_delta && ac_delta >= bc_delta && ac_delta >= bd_delta && ac_delta >= cd_delta)
            		{
            			int average = (src[k - xdim - 1] - src[k - xdim + 1]) / 2;
                        int delta   = src[k]  - average;
                        dst[k++]    = delta;
                        sum        += Math.abs(delta);	
            		}
            		else if(ad_delta >= bc_delta && ad_delta >= bd_delta && ad_delta >= cd_delta)
            		{
            			int average = (src[k - xdim - 1] - src[k - 1]) / 2;
                        int delta   = src[k]  - average;
                        dst[k++]    = delta;
                        sum        += Math.abs(delta);	
            		}
            		else if(bc_delta >= bd_delta && bc_delta >= cd_delta)
            		{
            			int average = (src[k - xdim] - src[k - xdim + 1]) / 2;
                        int delta   = src[k]  - average;
                        dst[k++]    = delta;
                        sum        += Math.abs(delta);	
            		}
            		else if(bd_delta >= cd_delta)
            		{
            			int average = (src[k - xdim] - src[k - 1]) / 2;
                        int delta   = src[k]  - average;
                        dst[k++]    = delta;
                        sum        += Math.abs(delta);	
            		}
            		else
            		{
            			int average = (src[k - xdim + 1] - src[k - 1]) / 2;
                        int delta   = src[k]  - average;
                        dst[k++]    = delta;
                        sum        += Math.abs(delta);	
            		}
                } 
            	int average = (src[k - 1] + src[k - xdim]) / 2;
                int delta   = src[k]  - average;
                dst[k++]    = delta;
                sum        += Math.abs(delta);
            }
        }
        
        ArrayList result = new ArrayList();
        result.add(sum);
        result.add(dst);
        return result;
    }
    
    // Not fully implemented.
    public static int[] getValuesFromAverageDeltas2(int src[],int xdim, int ydim, int init_value)
    {
    	int[] dst = new int[xdim * ydim];
    	
        int k     = 0;
        int value = init_value;
        for(int i = 0; i < ydim; i++)
        {
        	if(i != 0)
                value += src[k];
        	else
        	{
        		if(src[k] != 2)
        			System.out.println("Wrong code.");
        	}
            int current_value = value;
            dst[k++]          = current_value;
            
            if(i == 0)
            {
                for(int j = 1; j < xdim; j++)
                {
                    current_value += src[k];
                    dst[k++]       = current_value;
                }
            }
            else
            {
            	for(int j = 1; j < xdim; j++)
                {
            		int average   = (dst[k - 1] + dst[k - xdim]) / 2;
                    current_value = average + src[k];
                    dst[k++]      = current_value;
                }   	
            }
        }
        return dst;
    }
    
    
    // The paeth filter usually works better than vertical or horizontal deltas,
    // but still does not do as well as simply choosing the smallest delta 
    // by a very significant amount.
    
    // The problem is choosing the smallest delta requires keeping a state
    // that seems to amount to more than the compressed paeth deltas minus the 
    // compressed ideal deltas.
    public static ArrayList getPaethDeltasFromValues(int src[], int xdim, int ydim)
    {
    	int[] dst          = new int[xdim * ydim];
        int init_value     = src[0];
        int value          = init_value;
        int delta          = 0;
        int sum            = 0;
        int k              = 0;
      
        for(int i = 0; i < ydim; i++)
        {
        	if(i == 0)
        	{
                for(int j = 0; j < xdim; j++)
                {
            	    if(j == 0)
            	    {
            		    // Setting the first value to 3 to mark the delta type paeth.
            			dst[k++] = 3;
            	    }
            		else
            		{
            			// We don't have an upper or upper diagonal delta to check
            			// in the first row, so we just use horizontal deltas.
            		    delta     = src[k] - value;
                        value     += delta;
                        dst[k++]  = delta;
                        sum      += Math.abs(delta);
            		}
            	}
            }
        	else
        	{
        		for(int j = 0; j < xdim; j++)
                {
            	    if(j == 0)
            	    {
            	    	// We don't have a horizontal delta or diagonal delta for our paeth filter,
            	    	// so we just use a vertical delta, and reset our init value.
            	    	delta      = src[k] - init_value;
            	    	init_value = src[k];
            	    	dst[k++]   = delta;
            	        sum += Math.abs(delta);
            	    }
            	    else
            	    {
            	    	int a = src[k - 1];
            	    	int b = src[k - xdim];
            	    	int c = src[k - xdim - 1];
            	    	int d = a + b - c;
            	    	
            	    	
            	    	// Prediction deltas.
            	    	int delta_a = Math.abs(a - d);
            	    	int delta_b = Math.abs(b - d);
            	    	int delta_c = Math.abs(c - d);
            	    	
            	    	
            	    	// Actual deltas.
            	    	int horizontal_delta = src[k] - src[k - 1];
            	    	int vertical_delta   = src[k] - src[k - xdim];
            	    	int diagonal_delta   = src[k] - src[k - xdim - 1];
            	    	
            	    	if(delta_a <= delta_b && delta_a <= delta_c)
            	    	    delta = horizontal_delta;
            	    	else if(delta_b <= delta_c)
            	    	    delta = vertical_delta;
            	    	else
            	    	    delta = diagonal_delta;
            	    	
            	    	dst[k++] = delta;
            	    	
            	    	
            	    	sum += Math.abs(delta);
            	    }
                }
        	}
        }
        ArrayList result = new ArrayList();
        result.add(sum);
        result.add(dst);
        return result;    
    }

    public static int[] getValuesFromPaethDeltas(int src[], int xdim, int ydim, int init_value)
    {
    	int[] dst = new int[xdim * ydim];
        dst[0]    = init_value;
        int value = init_value;

        if(src[0] != 3)
        	System.out.println("Wrong code at beginning of delta array.");
        
        for(int i = 1; i < xdim; i++)
        {
        	value   += src[i];
        	dst[i] = value;
        }
        
        for(int i = 1; i < ydim; i++)
        {
            for(int j = 0; j < xdim; j++)	
            {
            	if(j == 0)
            	{
            	    init_value   += src[i * xdim];
            	    dst[i * xdim] = init_value;
            	    value         = init_value;
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
        	    	{
        	    	    dst[i * xdim + j] = a + src[i * xdim + j];
        	    	}
        	    	else if(delta_b <= delta_c)
        	    	{
        	    		dst[i * xdim + j] = b + src[i * xdim + j];
        	    	}
        	    	else
        	    	{
        	    		dst[i * xdim + j] = c + src[i * xdim + j];
        	    	}
            	}
            }
        }
        return dst;
    }
 
    public static ArrayList getGradientDeltasFromValues3(int src[], int xdim, int ydim)
    {
    	int[] dst          = new int[xdim * ydim];
        int init_value     = src[0];
        int value          = init_value;
        
        int sum            = 0;
        int k              = 0;
        int number_of_misses = 0;
        
        int forward_diagonals = 0;
      
        for(int i = 0; i < ydim; i++)
        {
        	if(i == 0)
        	{
                for(int j = 0; j < xdim; j++)
                {
            	    if(j == 0)
            	    {
            		    // Setting the first value to 4 to mark the delta type gradient.
            			dst[k++] = 4;
            	    }
            		else
            		{
            			// Use the horizontal deltas.
            		    int delta = src[k] - value;
                        value    += delta;
                        dst[k++]  = delta;
                        sum      += Math.abs(delta);
            		}
            	}
            }
        	else
        	{
        		for(int j = 0; j < xdim; j++)
                {
        			k = i * xdim + j;
            	    if(j == 0)
            	    {
            	    	// Use the vertical delta, and reset our init value.
            	    	int delta  = src[k] - init_value;
            	    	init_value = src[k];
            	    	dst[k]   = delta;
            	        sum        += Math.abs(delta);
            	    }
            	    else if(j == 1)
            	    {
            	    	// Use the Paeth filter.
            	    	
            	    	int a = src[k - 1];
            	    	int b = src[k - xdim];
            	    	int c = src[k - xdim - 1];
            	    	int d = a + b - c;
            	    
            	    	int delta_a = Math.abs(a - d);
            	    	int delta_b = Math.abs(b - d);
            	    	int delta_c = Math.abs(c - d);
            	    
            	    	
            	    	int delta = 0;
            	    	if(delta_a <= delta_b && delta_a <= delta_c)
            	    	    delta = src[k] - src[k - 1];
            	    	else if(delta_b <= delta_c)
            	    	    delta = src[k] - src[k - xdim];
            	    	else
            	    	    delta = src[k] - src[k - xdim - 1];
            	    	
            	    	if(delta > src[k] - src[k - 1] || delta > src[k] - src[k - xdim] || delta > src[k] - src[k - xdim])
            	    		number_of_misses++;
            	    	dst[k] = delta;	
            	    }
            	    else if(j < xdim - 1)
            	    {
            	    	int a = src[k - 1];
            	    	int b = src[k - xdim];
            	    	int c = src[k - xdim - 1];
            	    	int d = src[k - xdim + 1];
            	    	int e = src[k - xdim - 2];
            	    	
            	    	int [] gradient = new int[4];
            	    	gradient[0] = Math.abs(c - b);
            	    	gradient[1] = Math.abs(c - a);
            	    	gradient[2] = Math.abs(b - d);
            	    	gradient[3] = Math.abs(a - e);
            	    	
            	    	int max_value = gradient[0];
            	    	int max_index = 0;
            	    	for(int m = 1; m < 4; m++)
            	    	{
            	    		if(gradient[m] > max_value)
            	    		{
            	    		    max_value = gradient[m];
            	    		    max_index = m;
            	    		}
            	    	}
            	    	
            	    	int delta = 0;
            	    	
            	    	
            	    	if(max_index == 0)
            	    		delta = src[k] - src[k - 1];
            	    	else if(max_index == 1)
            	    		delta = src[k] - src[k - xdim];
            	    	else if(max_index == 2)
            	    		delta = src[k] - src[k - xdim - 1];
            	    	else
            	    	{
            	    		delta = src[k] - src[k - xdim + 1];
            	    		forward_diagonals++;
            	    	}
            	    	dst[k] = delta;
            	    	sum += Math.abs(delta);
            	    }
            	    else
            	    {
            	    	k = i * xdim + j;
            	    	int delta  = src[k] - src[k - 1];
            	    	dst[k] = delta;
            	    	sum += Math.abs(delta);
            	    }
                }
        	}
        }
        ArrayList result = new ArrayList();
        result.add(sum);
        result.add(dst);
        result.add(number_of_misses);
        
        //System.out.println("Number of forward diagonals was " + forward_diagonals);
        return result;    
    }

    /*
    public static ArrayList getGradientDeltasFromValues(int src[], int xdim, int ydim)
    {
    	int[] dst          = new int[xdim * ydim];
        int init_value     = src[0];
        int value          = init_value;
        
        int sum            = 0;
        int k              = 0;
      
        for(int i = 0; i < ydim; i++)
        {
        	if(i == 0)
        	{
                for(int j = 0; j < xdim; j++)
                {
            	    if(j == 0)
            	    {
            		    // Setting the first value to 4 to mark the delta type gradient.
            			dst[k++] = 4;
            	    }
            		else
            		{
            			// Use the horizontal deltas.
            		    int delta = src[k] - value;
                        value    += delta;
                        dst[k++]  = delta;
                        sum      += Math.abs(delta);
            		}
            	}
            }
        	else
        	{
        		for(int j = 0; j < xdim; j++)
                {
            	    if(j == 0)
            	    {
            	    	// Use the vertical delta, and reset our init value.
            	    	int delta  = src[k] - init_value;
            	    	init_value = src[k];
            	    	dst[k++]   = delta;
            	        sum        += Math.abs(delta);
            	    }
            	    else if(j == 1)
            	    {
            	    	// Use the paeth filter since the gradient filter requires two preceding pixels.
            	    	int a = src[k - 1];
            	    	int b = src[k - xdim];
            	    	int c = src[k - xdim - 1];
            	    	int d = a + b - c;
            	    
            	    	int delta_a = Math.abs(a - d);
            	    	int delta_b = Math.abs(b - d);
            	    	int delta_c = Math.abs(c - d);
            	    
            	    	
            	    	int delta = 0;
            	    	if(delta_a <= delta_b && delta_a <= delta_c)
            	    	    delta = src[k] - src[k - 1];
            	    	else if(delta_b <= delta_c)
            	    	    delta = src[k] - src[k - xdim];
            	    	else
            	    	    delta = src[k] - src[k - xdim - 1];
            	 
            	    	dst[k++] = delta;	
            	    	sum        += Math.abs(delta);
            	    }
            	    else if(j < xdim - 1)
            	    {
            	    	int a = src[k - 1];
            	    	int b = src[k - xdim];
            	    	int c = src[k - xdim - 1];
            	    	int d = src[k - xdim + 1];
            	    	int e = src[k - xdim - 2];
            	    	
            	    	int [] gradient = new int[4];
            	    	gradient[0] = Math.abs(c - b);
            	    	gradient[1] = Math.abs(c - a);
            	    	gradient[2] = Math.abs(b - d);
            	    	gradient[3] = Math.abs(a - e);
            	    	
            	    	int max_value = gradient[0];
            	    	int max_index = 0;
            	    	for(int m = 1; m < 4; m++)
            	    	{
            	    		if(gradient[m] > max_value)
            	    		{
            	    		    max_value = gradient[m];
            	    		    max_index = m;
            	    		}
            	    	}
            	    	
            	    	int delta;
            	    	if(max_index == 0)
            	    		delta = src[k] - src[k - 1];
            	    	else if(max_index == 1)
            	    		delta = src[k] - src[k - xdim];
            	    	else if(max_index == 2)
            	    		delta = src[k] - src[k - xdim - 1];
            	    	else
            	    		delta = src[k] - src[k - xdim + 1];
            	    	
            	    	dst[k++] = delta;
            	    	sum += Math.abs(delta);
            	    }
            	    else
            	    {
            	    	int delta  = src[k] - src[k - 1];
            	    	dst[k++] = delta;
            	    	sum += Math.abs(delta);
            	    }
                }
        	}
        }
        ArrayList result = new ArrayList();
        result.add(sum);
        result.add(dst);
        return result;    
    }

    
    public static int[] getValuesFromGradientDeltas(int src[], int xdim, int ydim, int init_value)
    {
    	int[] dst = new int[xdim * ydim];
        dst[0]    = init_value;
        int value = init_value;
        
        if(src[0] != 4)
        	System.out.println("Wrong code.");
        
        for(int i = 1; i < xdim; i++)
        {
        	value   += src[i];
        	dst[i] = value;
        }
        
        for(int i = 1; i < ydim; i++)
        {
            for(int j = 0; j < xdim; j++)	
            {
            	int k = i * xdim + j;
            	if(j == 0)
            	{
            	    init_value   += src[i * xdim];
            	    dst[i * xdim] = init_value;
            	    value         = init_value;
            	}
            	else if(j == 1)
            	{
            		int a = src[k - 1];
        	    	int b = src[k - xdim];
        	    	int c = src[k - xdim - 1];
        	    	int d = a + b - c;
        	    
        	    	int delta_a = Math.abs(a - d);
        	    	int delta_b = Math.abs(b - d);
        	    	int delta_c = Math.abs(c - d);
        	    
        	    	
        	    	int delta = 0;
        	    	if(delta_a <= delta_b && delta_a <= delta_c)
        	    	    delta = src[k] - src[k - 1];
        	    	else if(delta_b <= delta_c)
        	    	    delta = src[k] - src[k - xdim];
        	    	else
        	    	    delta = src[k] - src[k - xdim - 1];
        	 
        	    	dst[k] = src[k] + delta;		
            	}
            	else if(j < xdim - 1)
            	{
            		int a = dst[k - 1];
        	    	int b = dst[k - xdim];
        	    	int c = dst[k - xdim - 1];
        	    	int d = dst[k - xdim + 1];
        	    	int e = dst[k - xdim - 2];
        	    	
        	    	int [] gradient = new int[4];
        	    	gradient[0] = Math.abs(c - b);
        	    	gradient[1] = Math.abs(c - a);
        	    	gradient[2] = Math.abs(b - d);
        	    	gradient[3] = Math.abs(a - e);
        	    	
        	    	int [] delta = new int[4];
        	    	delta[0] = Math.abs(src[k] - a);
        	    	delta[1] = Math.abs(src[k] - b);
        	    	delta[2] = Math.abs(src[k] - c);
        	    	delta[3] = Math.abs(src[k] - d);
        	    	
        	    	int max_value = gradient[0];
        	    	int max_index = 0;
        	    	for(int m = 1; m < 4; m++)
        	    	{
        	    		if(gradient[m] > max_value)
        	    		{
        	    		    max_value = gradient[m];
        	    		    max_index = m;
        	    		}
        	    	}
        	    	
        	    	int _delta = src[k];
        	    	if(max_index == 0)
        	    		dst[k] = dst[k - 1] + src[k];
        	    	else if(max_index == 1)
        	    		dst[k] = dst[k - xdim] + src[k];
        	    	else if(max_index == 2)
        	    		dst[k] = dst[k - xdim - 1] + src[k];
        	    	else
        	    		dst[k] = dst[k - xdim + 1] + src[k];   
            	}
            	else
            	{
                	value = dst[k - 1];
                	value += src[k];
                	dst[k] = value;	
            	}
            }
        }
        return dst;
    }
    */
    
    public static ArrayList getGradientDeltasFromValues(int src[], int xdim, int ydim)
    {
    	int[] dst          = new int[xdim * ydim];
        int init_value     = src[0];
        int value          = init_value;
        
        int sum            = 0;
        int k              = 0;
        
        for(int i = 0; i < ydim; i++)
        {
        	if(i == 0)
        	{
                for(int j = 0; j < xdim; j++)
                {
            	    if(j == 0)
            	    {
            		    // Setting the first value to 4 to mark the delta type gradient.
            			dst[k++] = 4;
            	    }
            		else
            		{
            			// Use the horizontal deltas.
            		    int delta = src[k] - value;
                        value    += delta;
                        dst[k++]  = delta;
                        sum      += Math.abs(delta);
            		}
            	}
            }
        	else
        	{
        		for(int j = 0; j < xdim; j++)
                {
        			k = i * xdim + j;
            	    if(j == 0)
            	    {
            	    	// Use the vertical delta, and reset our init value.
            	    	int delta  = src[k] - init_value;
            	    	init_value = src[k];
            	    	dst[k]   = delta;
            	        sum        += Math.abs(delta);
            	    }
            	    else if(j == 1)
            	    {
            	    	// Use the Paeth filter.
            	    	
            	    	int a = src[k - 1];
            	    	int b = src[k - xdim];
            	    	int c = src[k - xdim - 1];
            	    	int d = a + b - c;
            	    
            	    	int delta_a = Math.abs(a - d);
            	    	int delta_b = Math.abs(b - d);
            	    	int delta_c = Math.abs(c - d);
            	    
            	    	
            	    	int delta = 0;
            	    	if(delta_a <= delta_b && delta_a <= delta_c)
            	    	    delta = src[k] - src[k - 1];
            	    	else if(delta_b <= delta_c)
            	    	    delta = src[k] - src[k - xdim];
            	    	else
            	    	    delta = src[k] - src[k - xdim - 1];
            	    	
            	    	dst[k] = delta;	
            	    }
            	    else if(j < xdim - 1)
            	    {
            	    	int a = src[k - 1];
            	    	int b = src[k - xdim];
            	    	int c = src[k - xdim - 1];
            	    	int d = src[k - xdim + 1];
            	    	int e = src[k - xdim - 2];
            	    	
            	    	int [] gradient = new int[4];
            	    	gradient[0] = Math.abs(c - b);
            	    	gradient[1] = Math.abs(c - a);
            	    	//gradient[2] = Math.abs(b - d);
            	    	gradient[2] = Math.abs(a - b);
            	    	gradient[3] = Math.abs(a - e);
            	    	
            	    	int max_value = gradient[0];
            	    	int max_index = 0;
            	    	for(int m = 1; m < 4; m++)
            	    	{
            	    		if(gradient[m] > max_value)
            	    		{
            	    		    max_value = gradient[m];
            	    		    max_index = m;
            	    		}
            	    	}
            	    	
            	    	int delta = 0;
            	    	
            	    	
            	    	if(max_index == 0)
            	    		delta = src[k] - src[k - 1];
            	    	else if(max_index == 1)
            	    		delta = src[k] - src[k - xdim];
            	    	else if(max_index == 2)
            	    		delta = src[k] - src[k - xdim - 1];
            	    	else
            	    		delta = src[k] - src[k - xdim + 1];
            	    	dst[k] = delta;
            	    	sum += Math.abs(delta);
            	    }
            	    else
            	    {
            	    	k = i * xdim + j;
            	    	int delta  = src[k] - src[k - 1];
            	    	dst[k] = delta;
            	    	sum += Math.abs(delta);
            	    }
                }
        	}
        }
        ArrayList result = new ArrayList();
        result.add(sum);
        result.add(dst);
        return result;    
    }

    public static int[] getValuesFromGradientDeltas(int src[], int xdim, int ydim, int init_value)
    {
    	int[] dst = new int[xdim * ydim];
        dst[0]    = init_value;
        int value = init_value;
        
        if(src[0] != 4)
        	System.out.println("Wrong code.");
        
        for(int i = 1; i < xdim; i++)
        {
        	value   += src[i];
        	dst[i] = value;
        }
        
        for(int i = 1; i < ydim; i++)
        {
            for(int j = 0; j < xdim; j++)	
            {
            	if(j == 0)
            	{
            	    init_value   += src[i * xdim];
            	    dst[i * xdim] = init_value;
            	    value         = init_value;
            	}
            	else if(j == 1)
            	{
            	    int a = dst[i * xdim + j - 1];
            	    int b = dst[(i - 1) * xdim + j];
            	    int c = dst[(i - 1) * xdim + j - 1];
            	    int d = a + b - c;
            	    
            	    int delta_a = Math.abs(a - d);
        	    	int delta_b = Math.abs(b - d);
        	    	int delta_c = Math.abs(c - d);
        	    	
        	    	if(delta_a <= delta_b && delta_a <= delta_c)
        	    	{
        	    	    dst[i * xdim + j] = a + src[i * xdim + j];
        	    	}
        	    	else if(delta_b <= delta_c)
        	    	{
        	    		dst[i * xdim + j] = b + src[i * xdim + j];
        	    	}
        	    	else
        	    	{
        	    		dst[i * xdim + j] = c + src[i * xdim + j];
        	    	}
            	}
            	else if(j < xdim - 1)
            	{
            		int k = i * xdim + j;
            		int a = dst[k - 1];
        	    	int b = dst[k - xdim];
        	    	int c = dst[k - xdim - 1];
        	    	int d = dst[k - xdim + 1];
        	    	int e = dst[k - xdim - 2];
        	    	
        	    	int [] gradient = new int[4];
        	    	gradient[0] = Math.abs(c - b);
        	    	gradient[1] = Math.abs(c - a);
        	    	
        	    	//gradient[2] = Math.abs(b - d);
        	    	gradient[2] = Math.abs(a - b);
        	    	gradient[3] = Math.abs(a - e);
        	    	
        	    	int [] delta = new int[4];
        	    	delta[0] = Math.abs(src[k] - a);
        	    	delta[1] = Math.abs(src[k] - b);
        	    	delta[2] = Math.abs(src[k] - c);
        	    	delta[3] = Math.abs(src[k] - d);
        	    	
        	    	int max_value = gradient[0];
        	    	int max_index = 0;
        	    	for(int m = 1; m < 4; m++)
        	    	{
        	    		if(gradient[m] > max_value)
        	    		{
        	    		    max_value = gradient[m];
        	    		    max_index = m;
        	    		}
        	    	}
        	    	
        	    	int _delta = src[k];
        	    	if(max_index == 0)
        	    		dst[k] = dst[k - 1] + src[k];
        	    	else if(max_index == 1)
        	    		dst[k] = dst[k - xdim] + src[k];
        	    	else if(max_index == 2)
        	    		dst[k] = dst[k - xdim - 1] + src[k];
        	    	else
        	    		dst[k] = dst[k - xdim + 1] + src[k];   
            	}
            	else
            	{
            		int k = i * xdim + j;
                	value = dst[k - 1];
                	value += src[k];
                	dst[k] = value;	
            	}
            }
        }
        return dst;
    }
 
    // This function returns the result from the standard png filters, using
    // the filter that produces the smallest delta sum for each row.
    public static ArrayList getMixedDeltasFromValues(int src[], int xdim, int ydim)
    {
        byte [] map = new byte[ydim - 1];
        
        // Scanning the raster line by line to figure out
        // which filter works best for each line.
        // We skip the first line since the only available
        // delta is horizontal.
        for(int i = 1; i < ydim; i++)
        {
        	int [] sum   = new int[4];
        	
        	for(int j = 1; j < xdim - 1; j++)
        	{
        		int k = i * xdim + j;
        		
    	    	sum[0] += Math.abs(src[k] - src[k - 1]);
    	    	sum[1] += Math.abs(src[k] - src[k - xdim]);
    	    	sum[2] += Math.abs(src[k] - (src[k - 1] + src[k - xdim]) / 2);
    	    	
    	    	int a = src[k - 1];
    	    	int b = src[k - xdim];
    	    	int c = src[k - xdim - 1];
    	    	int d = a + b - c;
    	    	
    	    	int e = Math.abs(a - d);
    	    	int f = Math.abs(b - d);
    	    	int g = Math.abs(c - d);
    	    	
    	    	int delta;
    	    	if(e <= f && e <= g)
    	    	    delta = src[k] - src[k - 1];
    	    	else if(f <= g)
    	    	    delta = src[k] - src[k - xdim];
    	    	else
    	    	    delta = src[k] - src[k - xdim - 1];
    	    	sum[3] += Math.abs(delta);
        	}
        	
        	int value = sum[0];
        	int index = 0;
        	for(int k = 1; k < 3; k++)
        	{
        		if(sum[k] < value)
        		{
        			value = sum[k];
        			index = k;
        		}
        	}
        	map[i - 1] = (byte)index;
        }
    	
        // Now go back and collect the deltas.
    	int[] dst = new int[xdim * ydim];
     
        int init_value = src[0];
        int value      = init_value;
        int sum        = 0;
        
        for(int i = 0; i < ydim; i++)
        {
        	if(i == 0)
        	{
                for(int j = 0; j < xdim; j++)
                {
            	    if(j == 0)
            		    // Setting the first value to 5 to mark the delta type mixed.
            			dst[j] = 5;
            		else
            		{
            			// We don't have any vertical or diagonal deltas to check
            			// in the first row, so we just use horizontal deltas.
            		    int delta    = src[j] - value;
                        value       += delta;
                        dst[j]       = delta;
            		}
            	}
            }
        	else
        	{
        		int k      = i * xdim;
        		int delta  = src[k] - init_value;
    	    	init_value = src[k];
    	    	dst[k]     = delta;
        		k++;
        		
        		int m = map[i - 1];
        		
        		if(m == 0)
        		{
        			for(int j = 1; j < xdim; j++)
        			{
        				delta = src[k] - src[k - 1];
        	    	    dst[k] = delta;
        	    	    sum += Math.abs(delta);	
        	    	    k++;
        			}	
        		}
        		else if(m == 1)
        		{
        			for(int j = 1; j < xdim; j++)
        			{
        				delta = src[k] - src[k - xdim];
        	    	    dst[k] = delta;
        	    	    sum += Math.abs(delta);	
        	    	    k++;
        			}	
        		}
        		else if(m == 2)
        		{
        			for(int j = 1; j < xdim; j++)
        			{
        				delta  = src[k] - (src[k - 1] + src[k - xdim]) / 2;
        	    	    dst[k] = delta;
        	    	    sum += Math.abs(delta);	
        	    	    k++;
        			}	
        		}
        		else if(m == 3)
        		{
        			for(int j = 1; j < xdim; j++)
        			{
        				int a = src[k - 1];
            	    	int b = src[k - xdim];
            	    	int c = src[k - xdim - 1];
            	    	int d = a + b - c;
        	    		
            	    	int e = Math.abs(a - d);
            	    	int f = Math.abs(b - d);
            	    	int g = Math.abs(c - d);
        	    		
            	    	if(e <= f && e <= g)
            	    	    delta = src[k] - src[k - 1];
            	    	else if(f <= g)
            	    	    delta = src[k] - src[k - xdim];
            	    	else
            	    	    delta = src[k] - src[k - xdim - 1];
            	    	dst[k] = delta;
            	    
            	    	sum += Math.abs(delta);	
        			}
        		}
        	}
        }
       
        ArrayList result = new ArrayList();
        result.add(sum);
        result.add(dst);
        result.add(map);
        return result;
    }
    
    
    
    // This function returns the result from the standard png filters, using
    // the filter that produces the smallest delta sum for each row, plus the gradient filter.
    public static ArrayList getMixedDeltasFromValues2(int src[], int xdim, int ydim)
    {
        byte [] map = new byte[ydim - 1];
        
        for(int i = 1; i < ydim; i++)
        {
        	int [] sum   = new int[5];
        	
        	for(int j = 1; j < xdim - 1; j++)
        	{
        		int k = i * xdim + j;
        		
    	    	sum[0] += Math.abs(src[k] - src[k - 1]);
    	    	sum[1] += Math.abs(src[k] - src[k - xdim]);
    	    	sum[2] += Math.abs(src[k] - (src[k - 1] + src[k - xdim]) / 2);
    	    	
    	    	int a = src[k - 1];
    	    	int b = src[k - xdim];
    	    	int c = src[k - xdim - 1];
    	    	int d = a + b - c;
    	    	
    	    	int e = Math.abs(a - d);
    	    	int f = Math.abs(b - d);
    	    	int g = Math.abs(c - d);
    	    	
    	    	int delta;
    	    	if(e <= f && e <= g)
    	    	    delta = src[k] - src[k - 1];
    	    	else if(f <= g)
    	    	    delta = src[k] - src[k - xdim];
    	    	else
    	    	    delta = src[k] - src[k - xdim - 1];
    	    	sum[3] += Math.abs(delta);
    	    	
    	    	if(j == 1)
    	    	    sum[4] += Math.abs(delta);
    	    	else
    	    	{
    	    		a = src[k - 1];
        	    	b = src[k - xdim];
        	    	c = src[k - xdim - 1];
        	    	d = src[k - xdim + 1];
        	    	e = src[k - xdim - 2];
        	    	
        	    	int [] gradient = new int[4];
        	    	gradient[0] = Math.abs(c - b);
        	    	gradient[1] = Math.abs(c - a);
        	    	
        	    	//gradient[2] = Math.abs(b - d);
        	    	gradient[2] = Math.abs(a - b);
        	    	gradient[3] = Math.abs(a - e);
        	    	
        	    	
        	    	int max_value = gradient[0];
        	    	int max_index = 0;
        	    	for(int m = 1; m < 4; m++)
        	    	{
        	    		if(gradient[m] > max_value)
        	    		{
        	    		    max_value = gradient[m];
        	    		    max_index = m;
        	    		}
        	    	}
        	    	if(max_index == 0)
        	    		delta = src[k] - src[k - 1];
        	    	else if(max_index == 1)
        	    		delta = src[k] - src[k - xdim];
        	    	else if(max_index == 2)
        	    		delta = src[k] - src[k - xdim - 1];
        	    	else
        	    		delta = src[k] - src[k - xdim + 1]; 
        	    	sum[4] += Math.abs(delta);
    	    	}
    	    	
    	    	
    	    	/*
    	    	a = src[k - 1];
    	    	b = src[k - xdim];
    	    	c = src[k - xdim - 1];
    	    	d = src[k - xdim + 1];
    	    	e = (a + b + c + d) / 4;
    	    	
    	    	int [] avg = new int[4];
    	    	avg[0] = Math.abs(e - a);
    	    	avg[1] = Math.abs(e - b);
    	    	avg[2] = Math.abs(e - c);
    	    	avg[3] = Math.abs(e - d);
    	    	
    	    	int value = avg[0];
    	    	int index = 0;
    	    	for(int m = 1; m < 3; m++)
    	    	{
    	    		if(avg[m] < value)
    	    		{
    	    			value = avg[m];
    	    			index = m;
    	    		}
    	    	}
    	    	
    	    	if(index == 0)	
    	    		sum[4] += Math.abs(src[k] - src[k - 1]);
    	    	else if(index == 1)
    	    		sum[4] += Math.abs(src[k] - src[k - xdim]);
    	    	else if(index == 2)
    	    		sum[4] += Math.abs(src[k] - src[k - xdim - 1]);
    	    	else if(index == 3)
    	    		sum[4] += Math.abs(src[k] - src[k - xdim + 1]);	
    	    	*/
        	}
        	
        	// Get the least sum for that row.
        	int value = sum[0];
        	int index = 0;
        	for(int k = 1; k < 3; k++)
        	{
        		if(sum[k] < value)
        		{
        			value = sum[k];
        			index = k;
        		}
        	}
        	map[i - 1] = (byte)index;
        }
    	
        // Now go back and collect the deltas.
    	int[] dst = new int[xdim * ydim];
     
        int init_value = src[0];
        int value      = init_value;
        int sum        = 0;
        
        for(int i = 0; i < ydim; i++)
        {
        	if(i == 0)
        	{
                for(int j = 0; j < xdim; j++)
                {
            	    if(j == 0)
            		    // Setting the first value to 5 to mark the delta type mixed.
            			dst[j] = 5;
            		else
            		{
            			// We don't have any vertical or diagonal deltas to check
            			// in the first row, so we just use horizontal deltas.
            		    int delta    = src[j] - value;
                        value       += delta;
                        dst[j]       = delta;
            		}
            	}
            }
        	else
        	{
        		// Use the vertical delta for the first pixel.
        		int k      = i * xdim;
        		int delta  = src[k] - init_value;
    	    	init_value = src[k];
    	    	dst[k]     = delta;
        		k++;
        		
        		// Now use the filters from the map,
        		// including a horizontal delta for the gradient
        		// filter in the end pixel case.
        		int m = map[i - 1];
        		if(m == 0)
        		{
        			for(int j = 1; j < xdim; j++)
        			{
        				delta = src[k] - src[k - 1];
        	    	    dst[k] = delta;
        	    	    sum += Math.abs(delta);	
        	    	    k++;
        			}	
        		}
        		else if(m == 1)
        		{
        			for(int j = 1; j < xdim; j++)
        			{
        				delta = src[k] - src[k - xdim];
        	    	    dst[k] = delta;
        	    	    sum += Math.abs(delta);	
        	    	    k++;
        			}	
        		}
        		else if(m == 2)
        		{
        			for(int j = 1; j < xdim; j++)
        			{
        				delta  = src[k] - (src[k - 1] + src[k - xdim]) / 2;
        	    	    dst[k] = delta;
        	    	    sum += Math.abs(delta);	
        	    	    k++;
        			}	
        		}
        		else if(m == 3)
        		{
        			for(int j = 1; j < xdim; j++)
        			{
        				int a = src[k - 1];
            	    	int b = src[k - xdim];
            	    	int c = src[k - xdim - 1];
            	    	int d = a + b - c;
        	    		
            	    	int e = Math.abs(a - d);
            	    	int f = Math.abs(b - d);
            	    	int g = Math.abs(c - d);
        	    		
            	    	if(e <= f && e <= g)
            	    	    delta = src[k] - src[k - 1];
            	    	else if(f <= g)
            	    	    delta = src[k] - src[k - xdim];
            	    	else
            	    	    delta = src[k] - src[k - xdim - 1];
            	    	dst[k] = delta;
            	    
            	    	sum += Math.abs(delta);	
            	    	k++;
        			}
        		}
        		else if(m == 4)
        		{
        			// Use the paeth filter for the second pixel because
        			// the gradient filter requires two preceding pixels.
        			for(int j = 1; j < xdim - 1; j++)
        			{
        				if(j == 1)
        				{
        					int a = src[k - 1];
                	    	int b = src[k - xdim];
                	    	int c = src[k - xdim - 1];
                	    	int d = a + b - c;
            	    		
                	    	int e = Math.abs(a - d);
                	    	int f = Math.abs(b - d);
                	    	int g = Math.abs(c - d);
            	    		
                	    	if(e <= f && e <= g)
                	    	    delta = src[k] - src[k - 1];
                	    	else if(f <= g)
                	    	    delta = src[k] - src[k - xdim];
                	    	else
                	    	    delta = src[k] - src[k - xdim - 1];
                	    	dst[k] = delta;
                	    
                	    	sum += Math.abs(delta);	
                	    	k++;	
        				}
        				else
        				{
            	    	    int a = src[k - 1];
            	    	    int b = src[k - xdim];
            	    	    int c = src[k - xdim - 1];
            	    	    int d = src[k - xdim + 1];
            	    	    int e = src[k - xdim - 2];
            	    	
            	    	    int [] gradient = new int[4];
            	    	    gradient[0] = Math.abs(c - b);
            	    	    gradient[1] = Math.abs(c - a);
            	    	    //gradient[2] = Math.abs(b - d);
            	    	    gradient[2] = Math.abs(a - d);
            	    	    gradient[3] = Math.abs(a - e);
            	    	
            	    	    int max_value = gradient[0];
            	    	    int max_index = 0;
            	    	    for(int n = 1; n < 4; n++)
            	    	    {
            	    		    if(gradient[n] > max_value)
            	    		   {
            	    		        max_value = gradient[n];
            	    		        max_index = n;
            	    		   }
            	    	    }
            	    	    
                	    	if(max_index == 0)
                	    		delta = src[k] - src[k - 1];
                	    	else if(max_index == 1)
                	    		delta = src[k] - src[k - xdim];
                	    	else if(max_index == 2)
                	    		delta = src[k] - src[k - xdim - 1];
                	    	else
                	    		delta = src[k] - src[k - xdim + 1];
                	    	sum += Math.abs(delta);	
                	    	k++;
            	    	}
        			}
        			// Use another delta for last pixel.
        			delta = src[k] - src[k - 1];
        			dst[k] = delta;
        			sum += Math.abs(delta);	
        	    	k++;
        		}
        	}
        }
       
        ArrayList result = new ArrayList();
        result.add(sum);
        result.add(dst);
        result.add(map);
        return result;
    }
    
    public static int[] getValuesFromMixedDeltas(int [] src, int xdim, int ydim, int init_value, byte [] map)
    {
    	int[] dst = new int[xdim * ydim];
        dst[0] = init_value;
        int value = init_value;
        
        if(src[0] != 5)
        	System.out.println("Wrong code.");
        
        for(int i = 1; i < xdim; i++)
        {
        	value   += src[i];
        	dst[i] = value;
        }
        
        for(int i = 1; i < ydim; i++)
        {
        	byte m = map[i - 1];
            for(int j = 0; j < xdim; j++)	
            {
            	int k = i * xdim + j;
            	if(j == 0)
            	{
            	    init_value += src[k];
            	    dst[k]      = init_value;
            	}
            	else if(j < xdim - 1)
            	{
            		if(m == 0)
            		    value = dst[k - 1];
            		else if(m == 1)
            			value = dst[k - xdim];
            		else if(m == 2)
            			value = (dst[k - 1] + dst[k - xdim]) / 2;
            		else if(m == 3 || (m == 4 && j == 1))
            		{
            			int a = dst[k - 1];
            	    	int b = dst[k - xdim];
            	    	int c = dst[k - xdim - 1];
            	    	int d = a + b - c;
        	    		
            	    	int delta_a = Math.abs(a - d);
            	    	int delta_b = Math.abs(b - d);
            	    	int delta_c = Math.abs(c - d);
        	    		
            	    	int paeth_delta = 0;
            	    	if(delta_a <= delta_b && delta_a <= delta_c)
            	    	    value = dst[k - 1];
            	    	else if(delta_b <= delta_c)
            	    	    value = dst[k - xdim];
            	    	else
            	    	    value = dst[k - xdim - 1];
            		}
            		else if(m == 4)
            		{
            			int a = dst[k - 1];
        	    	    int b = dst[k - xdim];
        	    	    int c = dst[k - xdim - 1];
        	    	    int d = dst[k - xdim + 1];
        	    	    int e = dst[k - xdim - 2];
        	    	
        	    	    int [] gradient = new int[4];
        	    	    gradient[0] = Math.abs(c - b);
        	    	    gradient[1] = Math.abs(c - a);
        	    	    //gradient[2] = Math.abs(b - d);
        	    	    gradient[2] = Math.abs(a - d);
        	    	    gradient[3] = Math.abs(a - e);
        	    	
        	    	    int max_value = gradient[0];
        	    	    int max_index = 0;
        	    	    for(int n = 1; n < 4; n++)
        	    	    {
        	    		    if(gradient[n] > max_value)
        	    		   {
        	    		        max_value = gradient[n];
        	    		        max_index = n;
        	    		   }
        	    	    }
        	    	    
            	    	if(max_index == 0)
            	    		value = dst[k - 1];
            	    	else if(max_index == 1)
            	    		value = dst[k - xdim];
            	    	else if(max_index == 2)
            	    		value = dst[k - xdim - 1];
            	    	else
            	    		value = dst[k - xdim + 1];	
            		}
            		value += src[k];
            		dst[k] = value;
            	}
            	else
            	{
            	    value = dst[k - 1];
            	    value += src[k];
            	    dst[k] = value;
            	}
            }
        }
        return dst;
    }
    
    /*
    // This function returns the result from the standard png filters plus a gradient filter, 
    // using the filter that produces the smallest delta sum for each row.
    public static ArrayList getMixedDeltasFromValues2(int src[], int xdim, int ydim)
    {
        byte [] map = new byte[ydim - 1];
        
        for(int i = 1; i < ydim; i++)
        {
        	int [] sum   = new int[5];
        	int [] delta = new int[4];
        	
        	for(int j = 1; j < xdim - 1; j++)
        	{
        		int k = i * xdim + j;
        		
    	    	sum[0] += Math.abs(src[k] - src[k - 1]);
    	    	sum[1] += Math.abs(src[k] - src[k - xdim]);
    	    	sum[2] += Math.abs(src[k] - (src[k - 1] + src[k - xdim]) / 2);
    	    	
    	    	int a = src[k - 1];
    	    	int b = src[k - xdim];
    	    	int c = src[k - xdim - 1];
    	    	int d = a + b - c;
    	    	
    	    	delta[0] = Math.abs(a - d);
    	    	delta[1] = Math.abs(b - d);
    	    	delta[2] = Math.abs(c - d);
    	    	
    	    	int _delta   = 0;
    	    	if(delta[0] <= delta[1] && delta[0] <= delta[2])
    	    	    _delta = src[k] - src[k - 1];
    	    	else if(delta[1] <= delta[2])
    	    	    _delta = src[k] - src[k - xdim];
    	    	else
    	    	    _delta = src[k] - src[k - xdim - 1];
    	    	sum[3] += Math.abs(_delta);
    	    	
    
    	    	a = src[k - 1];
    	    	b = src[k - xdim];
    	    	c = src[k - xdim - 1];
    	    	d = src[k - xdim - 1];
    	    	
    	    	int average = (a + b + c + d) / 4;
    	    	delta[0] = Math.abs(average - a);
    	    	delta[1] = Math.abs(average - b);
    	    	delta[2] = Math.abs(average - c);
    	    	delta[3] = Math.abs(average - c);
    	    	
    	    	int min_value = delta[0];
    	    	int min_index = 0;
    	    	for(int m = 1; m < 3; m++)
    	    	{
    	    		if(delta[m] < min_value)
    	    		{
    	    			min_value = delta[m];
    	    			min_index = m;
    	    		}
    	    	}
    	    	
    	    	int m = min_value;
    	    	if(m == 0)	
    	    		sum[4] += Math.abs(src[k] - src[k - 1]);
    	    	else if(m == 1)
    	    		sum[4] += Math.abs(src[k] - src[k - xdim]);
    	    	else if(m == 1)
    	    		sum[4] += Math.abs(src[k] - src[k - xdim - 1]);
    	    	else if(m == 1)
    	    		sum[4] += Math.abs(src[k] - src[k - xdim + 1]);	
        	}
        	
        	int min_value = sum[0];
        	int min_index = 0;
        	for(int k = 1; k < 4; k++)
        	{
        		if(sum[k] < min_value)
        		{
        			min_value = sum[k];
        			min_index = k;
        		}
        	}
        	
        	map[i - 1] = (byte)min_index;
        }
    	
    	int[] dst = new int[xdim * ydim];
     
        int init_value = src[0];
        int value      = init_value;
        int sum        = 0;
        
        for(int i = 0; i < ydim; i++)
        {
        	if(i == 0)
        	{
                for(int j = 0; j < xdim; j++)
                {
            	    if(j == 0)
            		    // Setting the first value to 5 to mark the delta type mixed.
            			dst[j] = 5;
            		else
            		{
            			// We don't have any vertical or diagonal deltas to check
            			// in the first row, so we just use horizontal deltas.
            		    int delta    = src[j] - value;
                        value       += delta;
                        dst[j]       = delta;
            		}
            	}
            }
        	else
        	{
        		int m     = map[i - 1];
        		
        		for(int j = 0; j < xdim; j++)
                {
        			int k = i * xdim + j;
            	    if(j == 0)
            	    {
            	    	// Use the vertical delta for the first value.
            	    	int delta      = src[k] - init_value;
            	    	init_value = src[k];
            	    	dst[k]     = delta;
            	    }
            	    else if(j < xdim - 1)
            	    {
            	    	if(m == 0)
            	    	{
            	    	    int delta = src[k] - src[k - 1];
            	    	    dst[k] = delta;
            	    	    sum += Math.abs(delta);
            	    	}
            	    	else if(m == 1)
            	    	{
            	    		int delta = src[k] - src[k - xdim];
            	    	    dst[k] = delta;
            	    	    sum += Math.abs(delta);	
            	    	}
            	    	else if(m == 2)
            	    	{
            	    		int average = (src[k - 1] + src[k - xdim]) / 2;
            	    		int delta = src[k] - average;
            	    	    dst[k] = delta;
            	    	    sum += Math.abs(delta);	       	
            	    	}
            	    	else if(m == 3)
            	    	{
            	    		int a = src[k - 1];
                	    	int b = src[k - xdim];
                	    	int c = src[k - xdim - 1];
                	    	int d = a + b - c;
            	    		
                	    	int delta_a = Math.abs(a - d);
                	    	int delta_b = Math.abs(b - d);
                	    	int delta_c = Math.abs(c - d);
            	    		
                	    	int paeth_delta = 0;
                	    	if(delta_a <= delta_b && delta_a <= delta_c)
                	    	    paeth_delta = src[k] - src[k - 1];
                	    	else if(delta_b <= delta_c)
                	    	    paeth_delta = src[k] - src[k - xdim];
                	    	else
                	    	    paeth_delta = src[k] - src[k - xdim - 1];
                	    	dst[k] = paeth_delta;
                	    
                	    	sum += Math.abs(paeth_delta);
            	    	}
            	    	else if(m == 4)
            	    	{
            	    		int a = src[k - 1];
                	    	int b = src[k - xdim];
                	    	int c = src[k - xdim - 1];
                	    	int d = src[k - xdim - 1];
            	    		
                	    	int [] delta = new int[4];

                	    	int average = (a + b + c + d) / 4;
                	    	delta[0] = Math.abs(average - a);
                	    	delta[1] = Math.abs(average - b);
                	    	delta[2] = Math.abs(average - c);
                	    	delta[3] = Math.abs(average - c);
                	    	
                	    	int min_value = delta[0];
                	    	int min_index = 0;
                	    	for(int n = 1; n < 3; n++)
                	    	{
                	    		if(delta[n] < min_value)
                	    		{
                	    			min_value = delta[n];
                	    			min_index = n;
                	    		}
                	    	}
                	    	
                	    	int n = min_value;
                	    	if(n == 0)	
                	    	{
                	    		sum += Math.abs(src[k] - src[k - 1]);
                	    		dst[k] = src[k] - src[k - 1];
                	    	}
                	    	else if(n == 1)
                	    	{
                	    		sum += Math.abs(src[k] - src[k - xdim]);
                	    		dst[k] = src[k] - src[k - xdim];
                	    	}
                	    	else if(n == 3)
                	    	{
                	    		sum += Math.abs(src[k] - src[k - xdim - 1]);
                	    		dst[k] = src[k] - src[k - xdim - 1];
                	    	}
                	    	else if(n == 3)
                	    	{
                	    		sum += Math.abs(src[k] - src[k - xdim + 1]);
                	    		dst[k] = src[k] - src[k - xdim + 1];
                	    	}
                	    	
            	    	}
            	    }
            	    else
            	    {
            	    	// Use the horizontal delta, in case the best filter
            	    	// was the modified paeth.
            	    	int delta  = src[k] - src[k - 1];
            	    	dst[k] = delta;
            	    	sum += Math.abs(delta);
            	    }
                }
        	}
        }
       
        System.out.println();
        ArrayList result = new ArrayList();
        result.add(sum);
        result.add(dst);
        result.add(map);
        return result;
    }
    */
    
     // This function also parses for the gradient filter.
     public static int[] getValuesFromMixedDeltas2(int [] src, int xdim, int ydim, int init_value, byte [] map)
     {
    	int[] dst = new int[xdim * ydim];
        dst[0] = init_value;
        int value = init_value;
        
        if(src[0] != 5)
        	System.out.println("Wrong code.");
        
        for(int i = 1; i < xdim; i++)
        {
        	value   += src[i];
        	dst[i] = value;
        }
        
        for(int i = 1; i < ydim; i++)
        {
        	byte m = map[i - 1];
            for(int j = 0; j < xdim; j++)	
            {
            	int k = i * xdim + j;
            	if(j == 0)
            	{
            	    init_value += src[k];
            	    dst[k]      = init_value;
            	}
            	else if(j < xdim - 1)
            	{
            		if(m == 0)
            		    value = dst[k - 1];
            		else if(m == 1)
            			value = dst[k - xdim];
            		else if(m == 2)
            			value = (dst[k - 1] + dst[k - xdim]) / 2;
            		else if(m == 3)
            		{
            			int a = dst[k - 1];
            	    	int b = dst[k - xdim];
            	    	int c = dst[k - xdim - 1];
            	    	int d = a + b - c;
        	    		
            	    	int delta_a = Math.abs(a - d);
            	    	int delta_b = Math.abs(b - d);
            	    	int delta_c = Math.abs(c - d);
        	    		
            	    	int paeth_delta = 0;
            	    	if(delta_a <= delta_b && delta_a <= delta_c)
            	    	    value = dst[k - 1];
            	    	else if(delta_b <= delta_c)
            	    	    value = dst[k - xdim];
            	    	else
            	    	    value = dst[k - xdim - 1];
            		}
            		else if(m == 4)
            		{
            			if(j == 1)
            			{
            				int a = dst[k - 1];
                	    	int b = dst[k - xdim];
                	    	int c = dst[k - xdim - 1];
                	    	int d = a + b - c;
            	    		
                	    	int delta_a = Math.abs(a - d);
                	    	int delta_b = Math.abs(b - d);
                	    	int delta_c = Math.abs(c - d);
            	    		
                	    	int paeth_delta = 0;
                	    	if(delta_a <= delta_b && delta_a <= delta_c)
                	    	    value = dst[k - 1];
                	    	else if(delta_b <= delta_c)
                	    	    value = dst[k - xdim];
                	    	else
                	    	    value = dst[k - xdim - 1];    	
            			}
            			else
            			{
            				int a = dst[k - 1];
                	    	int b = dst[k - xdim];
                	    	int c = dst[k - xdim - 1];
                	    	int d = dst[k - xdim + 1];
                	    	int e = dst[k - xdim - 2];
                	    	
                	    	int [] gradient = new int[4];
                	    	gradient[0] = Math.abs(c - b);
                	    	gradient[1] = Math.abs(c - a);
                	    	gradient[2] = Math.abs(b - d);
                	    	gradient[3] = Math.abs(a - e);
                	    
                	    	int max_value = gradient[0];
                	    	int max_index = 0;
                	    	for(int n = 1; n < 4; n++)
                	    	{
                	    		if(gradient[n] > max_value)
                	    		{
                	    		    max_value = gradient[n];
                	    		    max_index = n;
                	    		}
                	    	}
                	    	
                	    	if(max_index == 0)
                	    		value = dst[k - 1];
                	    	else if(max_index == 1)
                	    		value = dst[k - xdim];
                	    	else if(max_index == 2)
                	    		value = dst[k - xdim - 1];
                	    	else
                	    		value = dst[k - xdim + 1];   	
            			}
            		}
            		value += src[k];
            		dst[k] = value;
            	}
            	else
            	{
            	    value = dst[k - 1];
            	    value += src[k];
            	    dst[k] = value;
            	}
            }
        }
        return dst;
    }
    

    
    
    
    
    
 
    
    // There are quite a few ways to construct the map and collect the deltas for 
    // delta type 6 that produce slightly different amounts of compression.  This
    // is the version that usually does the best, but not by much.
    public static ArrayList getIdealDeltasFromValues(int src[], int xdim, int ydim)
    {
        int[]  dst = new int[xdim * ydim];
        byte[] map = new byte[xdim * (ydim - 1)];
        
        int init_value     = src[0];
        int value          = init_value;
        int delta          = 0;
        int previous_delta = 0;
        int sum            = 0;
        
        int k = 0;
        for(int i = 0; i < ydim; i++)
        {
        	if(i == 0)
        	{
                for(int j = 0; j < xdim; j++)
                {
            	    if(j == 0)
            	    {
            		    // Setting the first value to 6 to mark the delta type ideal.
            			dst[k]       = 6;
            			//map[k] = 0;
            			k++;
            	    }
            		else
            		{
            			// We don't have any vertical or diagonal deltas to check
            			// in the first row, so we just use horizontal deltas.
            		    delta        = src[k] - value;
                        value       += delta;
                        dst[k]       = delta;
                        //map[k] = 0;
                        sum         += Math.abs(delta);
                        k++;
            		}
            	}
            }
        	else
        	{
        		for(int j = 0; j < xdim; j++)
                {
            	    if(j == 0)
            	    {
            	    	/*
            	    	delta          = src[k] - init_value;
            	    	previous_delta = delta;
            	    	init_value     = src[k];
            	    	dst[k]         = delta;
            	    	map[k - xdim]         = 1;
            	    	sum           += Math.abs(delta);
            	    	k++;
            	    	*/
            	    	int a = src[k] - src[k - xdim];
            	    	int b = src[k] - src[k - xdim + 1];
            	    	int c = src[k] - (src[k - xdim] + src[k - xdim + 1]) / 2;
            	    	if(Math.abs(a) <= Math.abs(b) && Math.abs(a) <= Math.abs(c))
            	    	{
            	    		delta          = a;
            	    	    dst[k]         = delta;
            	    	    previous_delta = delta;
            	    	    map[k - xdim]  = 0;
            	    	    sum           += Math.abs(delta);
            	    	    k++;
            	    	}
            	    	else if(Math.abs(b) <= Math.abs(c))
            	    	{
            	    		delta          = b;
            	    		dst[k]         = delta;
            	    		previous_delta = delta;
            	    	    map[k - xdim]  = 1;
            	    	    sum           += Math.abs(delta);
            	    	    k++;
            	    	}
            	    	else
            	    	{
            	    		delta          = c;
            	    		dst[k]         = delta;
            	    		previous_delta = delta;	
            	    		map[k - xdim]  = 2;
            	    	    sum           += Math.abs(delta);
            	    	    k++;
            	    	}
            	    		
            	    }
            	    else if(j < xdim - 1)
            	    {
            	    	// We have a set of 4 possible deltas to use.
            	    	int a = src[k] - src[k - 1];
            	    	int b = src[k] - src[k - xdim];
            	    	int c = (src[k] - (src[k - xdim] + src[k - 1]) / 2);
            	    	int d = src[k] - src[k - xdim - 1];
            	    	
            	    	// There might be a way to choose deltas that produces
            	    	// more compression later.  For now we'll just prioritize 
            	    	// the comparisons as horizontal, vertical, horizontal/vertical average,
            	    	// and back diagonal.  Theoretically, the orthogonal deltas are likely
            	    	// to be smaller since the center of the pixels is closer (1 < square root of 2).
            	    	// We are not using the forward diagonal so far.
            	    	if(Math.abs(a) <= Math.abs(b) && Math.abs(a) <= Math.abs(c) && Math.abs(a) <= Math.abs(d))
            	    	{
            	    		delta          = a;
            	    	    dst[k]         = delta;
            	    	    previous_delta = delta;
            	    	    map[k - xdim]  = 0;
            	    	    sum           += Math.abs(delta);
            	    	    k++;
            	    	}
            	    	else if(Math.abs(b) <= Math.abs(c)&& Math.abs(b) <= Math.abs(d))
            	    	{
            	    		delta          = b;
            	    		dst[k]         = delta;
            	    		previous_delta = delta;
            	    	    map[k - xdim]  = 1;
            	    	    sum           += Math.abs(delta);
            	    	    k++;
            	    	}
            	    	else if(Math.abs(c) <= Math.abs(d))
            	    	{
            	    		delta          = c;
            	    		dst[k]         = delta;
            	    		previous_delta = delta;
            	    	    map[k - xdim]  = 2;
            	    	    sum           += Math.abs(delta);
            	    	    k++;	
            	    	}
            	    	else
            	    	{
            	    		delta          = d;
            	    		dst[k]         = delta;
            	    		map[k - xdim]  = 3;
            	    	    sum           += Math.abs(delta);
            	    	    k++;
            	    	}
            	    }
            	    else
            	    {
            	    	// We'll treat this separately since this is
            	    	// an end pixel, with no forward diagonal,
            	    	// although we aren't using the forward
            	    	// diagonal now.
            	    	int a = src[k] - src[k - 1];
            	    	int b = src[k] - src[k - xdim];
            	    	int c = (src[k] - (src[k - xdim] + src[k - 1]) / 2);
            	    	int d = src[k] - src[k - xdim - 1];
            	    	
            	    	if(Math.abs(a) <= Math.abs(b) && Math.abs(a) <= Math.abs(c) && Math.abs(a) <= Math.abs(d))
            	    	{
            	    		delta          = a;
            	    	    dst[k]         = delta;
            	    	    map[k - xdim]  = 0;
            	    	    sum           += Math.abs(delta);
            	    	    k++;
            	    	}
            	    	else if(Math.abs(b) <= Math.abs(c)&& Math.abs(b) <= Math.abs(d))
            	    	{
            	    		delta          = b;
            	    		dst[k]         = delta;
            	    	    map[k - xdim]  = 1;
            	    	    sum           += Math.abs(delta);
            	    	    k++;
            	    	}
            	    	else if(Math.abs(c) <= Math.abs(d))
            	    	{
            	    		delta          = c;
            	    		dst[k]         = delta;
            	    	    map[k - xdim]  = 2;
            	    	    sum           += Math.abs(delta);
            	    	    k++;	
            	    	}
            	    	else
            	    	{
            	    		delta          = d;
            	    		dst[k]         = delta;
            	    		map[k - xdim]  = 3;
            	    	    sum           += Math.abs(delta);
            	    	    k++;
            	    	}
            	    }
                }
        	}
        }
        
        ArrayList result = new ArrayList();
        result.add(sum);
        result.add(dst);
        result.add(map);
        return result;
    }
    
    public static int[] getValuesFromIdealDeltas(int [] src, int xdim, int ydim, int init_value, byte [] map)
    {
    	int[] dst = new int[xdim * ydim];
        dst[0] = init_value;
        int value = init_value;
        
        if(src[0] != 6)
        	System.out.println("Wrong code.");
        
        for(int i = 1; i < xdim; i++)
        {
        	value   += src[i];
        	dst[i] = value;
        }
        
        for(int i = 1; i < ydim; i++)
        {
            for(int j = 0; j < xdim; j++)	
            {
            	if(j == 0)
            	{
            		int k = i * xdim;
            		int m = map[k - xdim];
            		
            		if(m == 0)
            		    value = dst[k - xdim];
            		else if(m == 1)
            		    value = dst[k - xdim + 1];
            		else if(m == 2)
            			value = (dst[k - xdim] + dst[k - xdim + 1]) / 2; 
            		value += src[k];
            	    dst[k] = value;
            	}
            	else
            	{
            		int k = i * xdim + j;
            		int m = map[k - xdim]; 
            		if(m == 0)
            		    value = dst[k - 1];
            		else if(m == 1)
            		    value = dst[k - xdim];
            		else if(m == 2)
            			value = (dst[k - xdim] + dst[k - 1]) / 2;    
            		else if(m == 3)
            			value = dst[k - xdim - 1];
            	    value += src[i * xdim + j];
            	    dst[k] = value;
            	}
            }
        }
        return dst;
    }
    
    /*
    // Get an ideal delta set from the pre-processed values and a map of which pixels are used.
    public static ArrayList getIdealDeltasFromValues(int src[], int xdim, int ydim)
    {
        int[]  dst = new int[xdim * ydim];
        byte[] map = new byte[xdim * (ydim - 1)];
        
        int init_value     = src[0];
        int value          = init_value;
        int delta          = 0;
        int previous_delta = 0;
        int sum            = 0;
        
        int k = 0;
        for(int i = 0; i < ydim; i++)
        {
        	if(i == 0)
        	{
                for(int j = 0; j < xdim; j++)
                {
            	    if(j == 0)
            	    {
            		    // Setting the first value to 6 to mark the delta type ideal.
            			dst[k]       = 6;
            			//map[k] = 0;
            			k++;
            	    }
            		else
            		{
            			// We don't have any vertical or diagonal deltas to check
            			// in the first row, so we just use horizontal deltas.
            		    delta        = src[k] - value;
                        value       += delta;
                        dst[k]       = delta;
                        //map[k] = 0;
                        sum         += Math.abs(delta);
                        k++;
            		}
            	}
            }
        	else
        	{
        		for(int j = 0; j < xdim; j++)
                {
            	    if(j == 0)
            	    {
            	    	// We could check the diagonal delta but won't be very significant difference in result.
            	    	// We just use a vertical delta, and reset our initial value.
            	    	delta          = src[k] - init_value;
            	    	previous_delta = delta;
            	    	init_value     = src[k];
            	    	dst[k]         = delta;
            	    	map[k - xdim]         = 1;
            	    	sum           += Math.abs(delta);
            	    	k++;
            	    }
            	    else if(j < xdim - 1)
            	    {
            	    	// We have a set of 4 possible deltas to use.
            	    	int a = src[k] - src[k - 1];
            	    	int b = src[k] - src[k - xdim];
            	    	int c = src[k] - src[k - xdim - 1];
            	    	int d = (src[k] - (src[k - xdim] + src[k - 1]) / 2);
            	    	int e = src[k] - src[k - xdim + 1];
            	    	
            	    	
            	    	
            	    	// There might be a way to choose deltas that produces
            	    	// more compression later.  For now we'll just prioritize 
            	    	// the comparisons as horizontal, vertical, back diagonal,
            	    	// and horizontal/vertical average.  Theoretically, the orthogonal
            	    	// deltas are likely to be smaller since the center of
            	    	// the pixels is closer (1 < square root of 2).
            	    	// We are not using the forward diagonal so far.
            	    	if(Math.abs(a) <= Math.abs(b) && Math.abs(a) <= Math.abs(c) && Math.abs(a) <= Math.abs(d) && Math.abs(a) <= Math.abs(e))
            	    	{
            	    		delta          = a;
            	    	    dst[k]         = delta;
            	    	    previous_delta = delta;
            	    	    map[k - xdim]  = 0;
            	    	    sum           += Math.abs(delta);
            	    	    k++;
            	    	}
            	    	else if(Math.abs(b) <= Math.abs(c)&& Math.abs(b) <= Math.abs(d) && Math.abs(b) <= Math.abs(e))
            	    	{
            	    		delta          = b;
            	    		dst[k]         = delta;
            	    		previous_delta = delta;
            	    	    map[k - xdim]         = 1;
            	    	    sum           += Math.abs(delta);
            	    	    k++;
            	    	}
            	    	else if(Math.abs(c) <= Math.abs(d) && Math.abs(c) <= Math.abs(e))
            	    	{
            	    		delta          = c;
            	    		dst[k]         = delta;
            	    		previous_delta = delta;
            	    	    map[k - xdim]         = 2;
            	    	    sum           += Math.abs(delta);
            	    	    k++;	
            	    	}
            	    	else if(Math.abs(d) <= Math.abs(e))
            	    	{
            	    		delta          = d;
            	    		dst[k]         = delta;
            	    		map[k - xdim]  = 3;
            	    	    sum           += Math.abs(delta);
            	    	    k++;
            	    	}
            	    	else
            	    	{
            	    		delta          = e;
            	    		dst[k]         = delta;
            	    		map[k - xdim]  = 4;
            	    	    sum           += Math.abs(delta);
            	    	    k++;	
            	    	}
            	    }
            	    else
            	    {
            	    	// We'll treat this separately since this is
            	    	// an end pixel, with no forward diagonal.
            	    	int a = src[k] - src[k - 1];
            	    	int b = src[k] - src[k - xdim];
            	    	int c = src[k] - src[k - xdim - 1];
            	    	int d = (src[k] - (src[k - xdim] + src[k - 1]) / 2);
            	    	
            	    	if(Math.abs(a) <= Math.abs(b) && Math.abs(a) <= Math.abs(c) && Math.abs(a) <= Math.abs(d))
            	    	{
            	    		delta          = a;
            	    	    dst[k]         = delta;
            	    	    map[k - xdim]  = 0;
            	    	    sum           += Math.abs(delta);
            	    	    k++;
            	    	}
            	    	else if(Math.abs(b) <= Math.abs(c)&& Math.abs(b) <= Math.abs(d))
            	    	{
            	    		delta          = b;
            	    		dst[k]         = delta;
            	    	    map[k - xdim]  = 1;
            	    	    sum           += Math.abs(delta);
            	    	    k++;
            	    	}
            	    	else if(Math.abs(c) <= Math.abs(d))
            	    	{
            	    		delta          = c;
            	    		dst[k]         = delta;
            	    	    map[k - xdim]  = 2;
            	    	    sum           += Math.abs(delta);
            	    	    k++;	
            	    	}
            	    	else
            	    	{
            	    		delta          = d;
            	    		dst[k]         = delta;
            	    		map[k - xdim]  = 3;
            	    	    sum           += Math.abs(delta);
            	    	    k++;
            	    	}
            	    }
                }
        	}
        }
        
        ArrayList result = new ArrayList();
        result.add(sum);
        result.add(dst);
        result.add(map);
        return result;
    }
    
    public static int[] getValuesFromIdealDeltas(int [] src, int xdim, int ydim, int init_value, byte [] map)
    {
    	int[] dst = new int[xdim * ydim];
        dst[0] = init_value;
        int value = init_value;
        
        if(src[0] != 6)
        	System.out.println("Wrong code.");
        
        for(int i = 1; i < xdim; i++)
        {
        	value   += src[i];
        	dst[i] = value;
        }
        
        for(int i = 1; i < ydim; i++)
        {
            for(int j = 0; j < xdim; j++)	
            {
            	if(j == 0)
            	{
            	    init_value   += src[i * xdim];
            	    dst[i * xdim] = init_value;
            	    value         = init_value;
            	}
            	else
            	{
            		int k = i * xdim + j;
            		int m = map[k - xdim]; 
            		if(m == 0)
            		    value = dst[k - 1];
            		else if(m == 1)
            		    value = dst[k - xdim];
            		else if(m == 2)
            		    value = dst[k - xdim - 1];
            		else if(m == 3)
            			value = (dst[k - xdim] + dst[k - 1]) / 2;
            		else if(m == 4)
            			value = dst[k - xdim + 1];
            	    value += src[i * xdim + j];
            	    dst[k] = value;
            	}
            }
        }
        return dst;
    }
    */
    
    /*
    // Get an ideal delta set from the pre-processed values and a map of which pixels are used.
    public static ArrayList getIdealDeltasFromValues(int src[], int xdim, int ydim)
    {
        int[]  dst = new int[xdim * ydim];
        
        // Don't bother including the first row, when we always
        // use the horizontal delta.
        byte[] map = new byte[(xdim - 1) * (ydim - 1)];
        
        int init_value     = src[0];
        int value          = init_value;
        int delta          = 0;
        int previous_delta = 0;
        int sum            = 0;
        
        int k = 0;
        int m = 0;
        for(int i = 0; i < ydim; i++)
        {
        	if(i == 0)
        	{
                for(int j = 0; j < xdim; j++)
                {
            	    if(j == 0)
            	    {
            		    // Setting the first value to 6 to mark the delta type ideal.
            			dst[k] = 6;
            			//map[k] = 0;
            			k++;
            	    }
            		else
            		{
            			// We don't have any vertical or diagonal deltas to check
            			// in the first row, so we just use horizontal deltas.
            		    delta        = src[k] - value;
                        value       += delta;
                        dst[k]       = delta;
                        //map[k] = 0;
                        sum         += Math.abs(delta);
                        k++;
            		}
            	}
            }
        	else
        	{
        		for(int j = 0; j < xdim; j++)
                {
            	    if(j == 0)
            	    {
            	    	// We could check the diagonal delta but won't be very significant difference in result.
            	    	// We just use a vertical delta, and reset our initial value.
            	    	delta          = src[k] - init_value;
            	    	previous_delta = delta;
            	    	init_value     = src[k];
            	    	dst[k]         = delta;
            	    	sum           += Math.abs(delta);
            	    	k++;
            	    }
            	    else
            	    {
            	    	// We have a set of 4 possible deltas to use.
            	    	int a = src[k] - src[k - 1];
            	    	int b = src[k] - src[k - xdim];
            	    	int c = src[k] - src[k - xdim - 1];
            	    	int d = src[k] - (src[k - xdim] + src[k - 1]) / 2;
            	    	//int d = src[k] - src[k - xdim + 1];
            	    	
            	    	
            	    	// There might be a way to choose deltas that produces
            	    	// more compression later.  For now we'll just prioritize 
            	    	// the comparisons as horizontal, vertical, back diagonal,
            	    	// and forward diagonal.  Theoretically, the orthogonal
            	    	// deltas are likely to be smaller since the center of
            	    	// the pixels is closer (1 < square root of 2).
            	    	// Already checked this in gradient filter where it
            	    	// seem to produce no significant difference.
            	    	if(Math.abs(a) <= Math.abs(b) && Math.abs(a) <= Math.abs(c) && Math.abs(a) <= Math.abs(d))
            	    	{
            	    		delta          = a;
            	    	    dst[k]         = delta;
            	    	    previous_delta = delta;
            	    	    map[m++]         = 0;
            	    	    sum           += Math.abs(delta);
            	    	    k++;
            	    	}
            	    	else if(Math.abs(b) <= Math.abs(c)&& Math.abs(b) <= Math.abs(d))
            	    	{
            	    		delta          = b;
            	    		dst[k]         = delta;
            	    		previous_delta = delta;
            	    	    map[m++]         = 1;
            	    	    sum           += Math.abs(delta);
            	    	    k++;
            	    	}
            	    	else if(Math.abs(c) <= Math.abs(d))
            	    	{
            	    		delta          = c;
            	    		dst[k]         = delta;
            	    		previous_delta = delta;
            	    	    map[m++]         = 2;
            	    	    sum           += Math.abs(delta);
            	    	    k++;	
            	    	}
            	    	else
            	    	{
            	    		delta          = d;
            	    		dst[k]         = delta;
            	    		previous_delta = delta;
            	    		map[m++]         = 3;
            	    	    sum           += Math.abs(delta);
            	    	    k++;
            	    	}
            	    }
                }
        	}
        }
        
        System.out.println("Map length is " + map.length);
        System.out.println("m is " + m);
        ArrayList result = new ArrayList();
        result.add(sum);
        result.add(dst);
        result.add(map);
        return result;
    }
    
    public static int[] getValuesFromIdealDeltas(int [] src, int xdim, int ydim, int init_value, byte [] map)
    {
    	int[] dst = new int[xdim * ydim];
        dst[0] = init_value;
        int value = init_value;
        
        if(src[0] != 6)
        	System.out.println("Wrong code.");
        
        for(int i = 1; i < xdim; i++)
        {
        	value   += src[i];
        	dst[i] = value;
        }
        
        int k = 0;
        for(int i = 1; i < ydim; i++)
        {
            for(int j = 0; j < xdim; j++)	
            {
            	if(j == 0)
            	{
            	    init_value   += src[i * xdim];
            	    dst[i * xdim] = init_value;
            	    value         = init_value;
            	}
            	else
            	{
            		int m = map[k++]; 
            		if(m == 0)
            		    value = dst[i * xdim + j - 1];
            		else if(m == 1)
            		    value = dst[(i - i) * xdim + j];
            		else if(m == 2)
            		    value = dst[(i - 1) * xdim + j - 1];
            		else if(m == 3)
            		{
            			value = (dst[(i - 1)* xdim + j] + dst[i * xdim + j - 1]) / 2;
            		}
            	    value += src[i * xdim + j];
            	    dst[i * xdim + j] = value;
            	}
            }
        }
        return dst;
    }
    */
    
    /*
    // Get an ideal delta set from the pre-processed values and a map of which pixels are used.
    public static ArrayList getIdealDeltasFromValues(int src[], int xdim, int ydim)
    {
        int[]  dst = new int[xdim * ydim];
        byte[] map = new byte[xdim * ydim];
        
        int init_value     = src[0];
        int value          = init_value;
        int delta          = 0;
        int previous_delta = 0;
        int sum            = 0;
        
        int k = 0;
        for(int i = 0; i < ydim; i++)
        {
        	if(i == 0)
        	{
                for(int j = 0; j < xdim; j++)
                {
            	    if(j == 0)
            	    {
            		    // Setting the first value to 6 to mark the delta type ideal.
            			dst[k]       = 6;
            			map[k] = 0;
            			k++;
            	    }
            		else
            		{
            			// We don't have any vertical or diagonal deltas to check
            			// in the first row, so we just use horizontal deltas.
            		    delta        = src[k] - value;
                        value       += delta;
                        dst[k]       = delta;
                        map[k] = 0;
                        sum         += Math.abs(delta);
                        k++;
            		}
            	}
            }
        	else
        	{
        		for(int j = 0; j < xdim; j++)
                {
            	    if(j == 0)
            	    {
            	    	// We could check the diagonal delta but won't be very significant difference in result.
            	    	// We just use a vertical delta, and reset our initial value.
            	    	delta          = src[k] - init_value;
            	    	previous_delta = delta;
            	    	init_value     = src[k];
            	    	dst[k]         = delta;
            	    	map[k]         = 1;
            	    	sum           += Math.abs(delta);
            	    	k++;
            	    }
            	    else if(j < xdim - 1)
            	    {
            	    	// We have a set of 4 possible deltas to use.
            	    	int a = src[k] - src[k - 1];
            	    	int b = src[k] - src[k - xdim];
            	    	int c = src[k] - src[k - xdim - 1];
            	    	int d = src[k] - src[k - xdim + 1];
            	    	
            	    	
            	    	// There might be a way to choose deltas that produces
            	    	// more compression later.  For now we'll just prioritize 
            	    	// the comparisons as horizontal, vertical, back diagonal,
            	    	// and forward diagonal.  Theoretically, the orthogonal
            	    	// deltas are likely to be smaller since the center of
            	    	// the pixels is closer (1 < square root of 2).
            	    	if(Math.abs(a) <= Math.abs(b) && Math.abs(a) <= Math.abs(c) && Math.abs(a) <= Math.abs(d))
            	    	{
            	    		delta          = a;
            	    	    dst[k]         = delta;
            	    	    previous_delta = delta;
            	    	    map[k]         = 0;
            	    	    sum           += Math.abs(delta);
            	    	    k++;
            	    	}
            	    	else if(Math.abs(b) <= Math.abs(c)&& Math.abs(b) <= Math.abs(d))
            	    	{
            	    		delta          = b;
            	    		dst[k]         = delta;
            	    		previous_delta = delta;
            	    	    map[k]         = 1;
            	    	    sum           += Math.abs(delta);
            	    	    k++;
            	    	}
            	    	else if(Math.abs(c) <= Math.abs(d))
            	    	{
            	    		delta          = c;
            	    		dst[k]         = delta;
            	    		previous_delta = delta;
            	    	    map[k]         = 2;
            	    	    sum           += Math.abs(delta);
            	    	    k++;	
            	    	}
            	    	else
            	    	{
            	    		delta          = d;
            	    		dst[k]         = delta;
            	    		previous_delta = delta;
            	    		map[k]         = 3;
            	    	    sum           += Math.abs(delta);
            	    	    k++;
            	    	}
            	    }
            	    else
            	    {
            	    	// We have a set of 3 possible pixels to use.
            	    	int a = src[k] - src[k - 1];
            	    	int b = src[k] - src[k - xdim];
            	    	int c = src[k] - src[k - xdim - 1];
            	    	
            	    	
            	    	//int delta_a = a - previous_delta;
            	    	//int delta_b = b - previous_delta;
            	    	//int delta_c = c - previous_delta;
            	    	
            	    	
            	    	if(Math.abs(a) <= Math.abs(b) && Math.abs(a) <= Math.abs(c))
            	    	{
            	    		delta        = a;
            	    	    dst[k]       = delta;
            	    	    map[k] = 0;
            	    	    sum         += Math.abs(delta);
            	    	    k++;
            	    	}
            	    	else if(Math.abs(b)<= Math.abs(c))
            	    	{
            	    		delta        = b;
            	    		dst[k]       = delta;
            	    	    map[k] = 1;
            	    	    sum         += Math.abs(delta);
            	    	    k++;
            	    	}
            	    	else
            	    	{
            	    		delta        = c;
            	    		dst[k]       = delta;
            	    	    map[k] = 2;
            	    	    sum         += Math.abs(delta);
            	    	    k++;	
            	    	}	
            	    }
                }
        	}
        }
        
        ArrayList result = new ArrayList();
        result.add(sum);
        result.add(dst);
        result.add(map);
        return result;
    }
    
    
    public static int[] getValuesFromIdealDeltas(int [] src, int xdim, int ydim, int init_value, byte [] map)
    {
    	int[] dst = new int[xdim * ydim];
        dst[0] = init_value;
        int value = init_value;
        
        if(src[0] != 6)
        	System.out.println("Wrong code.");
        
        for(int i = 1; i < xdim; i++)
        {
        	value   += src[i];
        	dst[i] = value;
        }
        
        for(int i = 1; i < ydim; i++)
        {
            for(int j = 0; j < xdim; j++)	
            {
            	if(j == 0)
            	{
            	    init_value   += src[i * xdim];
            	    dst[i * xdim] = init_value;
            	    value         = init_value;
            	}
            	else
            	{
            		int k = i * xdim + j;
            		int m = map[i * xdim + j]; 
            		if(m == 0)
            		    value = dst[k - 1];
            		else if(m == 1)
            		    value = dst[k - xdim];
            		else if(m == 2)
            		    value = dst[k - xdim - 1];
            		else if(m == 3)
            			value = dst[k - xdim + 1];
            	    value += src[i * xdim + j];
            	    dst[k] = value;
            	}
            }
        }
        return dst;
    }
    */
    
    public static ArrayList getDeltaListFromValues(int src[], int xdim, int ydim)
    {
    	ArrayList delta_list = new ArrayList();
    	
    	int k = 0;
        for(int i = 0; i < ydim; i++)
        {
        	if(i == 0)
        	{
                for(int j = 0; j < xdim; j++)
                {
            	    if(j == 0)
            	    {
            	    	// We have a set of 3 possible deltas to use.
            	    	int a = src[k] - src[k + 1];
            	    	int b = src[k] - src[k + xdim];
            	    	int c = src[k] - src[k + xdim + 1];
            	    	
            	    	double _a = Math.abs(a);
            	    	ArrayList list_a = new ArrayList();
            	    	list_a.add(a);
            	    	int location = 4;
            	    	list_a.add(location);
            	    	
            	    	double _b = Math.abs(b);
            	    	ArrayList list_b = new ArrayList();
            	    	list_b.add(b);
            	    	location = 6;
            	    	list_b.add(location);
            	    	
            	    	double _c = Math.abs(c);
            	    	ArrayList list_c = new ArrayList();
            	    	list_c.add(c);
            	    	location = 7;
            	    	list_c.add(location);
            	    	
            	    	Hashtable <Double, ArrayList> delta_table = new Hashtable <Double, ArrayList>();
            	    	ArrayList key_list = new ArrayList();
            	    
    		    		double addend = 0.00000001; 
    		    		
    		    		delta_table.put(_a, list_a);
    		    		key_list.add(_a);
    		    		
    		    		if(delta_table.containsKey(_b))
    		    		{
    		    			_b += addend;
    		    			addend *= 2;
    		    		}
    		    		delta_table.put(_b, list_b);
    		    		key_list.add(_b);
    		    		
    		    		if(delta_table.containsKey(_c))
    		    		{
    		    			_c += addend;
    		    			addend *= 2;
    		    		}
    		    		delta_table.put(_c, list_c);
    		    		key_list.add(_c);
    		    		
    		    		Collections.sort(key_list);
    		    		
    		    		int [][] table = new int[key_list.size()][2];
    		    		for(int m = 0; m < key_list.size(); m++)
    		    		{
    		    		   double key = (double)key_list.get(m);
    		    		   ArrayList current_delta_list = delta_table.get(key);
    		    		   table[m][0] = (int)current_delta_list.get(0);
    		    		   table[m][1] = (int)current_delta_list.get(1);
    		    		}
    		    		
    		    		delta_list.add(table);
    		    		
            			k++;
            	    }
            		else if(j < xdim - 1)
            		{
            			// We have a set of 5 possible deltas to use.
            			int a = src[k] - src[k - 1];
            	    	int b = src[k] - src[k + 1];
            	    	int c = src[k] - src[k + xdim - 1];
            	    	int d = src[k] - src[k + xdim];
            	    	int e = src[k] - src[k + xdim + 1];
            	    	
            	    	double _a = Math.abs(a);
            	    	ArrayList list_a = new ArrayList();
            	    	list_a.add(a);
            	    	int location = 3;
            	    	list_a.add(location);
            	    	
            	    	double _b = Math.abs(b);
            	    	ArrayList list_b = new ArrayList();
            	    	list_b.add(b);
            	    	location = 4;
            	    	list_b.add(location);
            	    	
            	    	double _c = Math.abs(c);
            	    	ArrayList list_c = new ArrayList();
            	    	list_c.add(c);
            	    	location = 5;
            	    	list_c.add(location);
            	    	
            	    	double _d = Math.abs(d);
            	    	ArrayList list_d = new ArrayList();
            	    	list_d.add(d);
            	    	location = 6;
            	    	list_d.add(location);
            	    	
            	    	double _e = Math.abs(e);
            	    	ArrayList list_e = new ArrayList();
            	    	list_e.add(e);
            	    	location = 7;
            	    	list_e.add(location);
            	    	
            	    	Hashtable <Double, ArrayList> delta_table = new Hashtable <Double, ArrayList>();
            	    	ArrayList key_list = new ArrayList();
            	    	
    		    		
    		    		double addend = 0.00000001;
    		    		
    		    		delta_table.put(_a, list_a);
    		    		key_list.add(_a);
    		    		
    		    		if(delta_table.containsKey(_b))
    		    		{
    		    			_b += addend;
    		    			addend *= 2;
    		    		}
    		    		delta_table.put(_b, list_b);
    		    		key_list.add(_b);
    		    		
    		    		if(delta_table.containsKey(_c))
    		    		{
    		    			_c += addend;
    		    			addend *= 2;
    		    		}
    		    		delta_table.put(_c, list_c);
    		    		key_list.add(_c);
    		    		
    		    		if(delta_table.containsKey(_d))
    		    		{
    		    			_d += addend;
    		    			addend *= 2;
    		    		}
    		    		delta_table.put(_d, list_d);
    		    		key_list.add(_d);
    		    		
    		    		if(delta_table.containsKey(_e))
    		    		{
    		    			_e += addend;
    		    			addend *= 2;
    		    		}
    		    		delta_table.put(_e, list_e);
    		    		key_list.add(_e);
    		    		
    		    		
    		    		Collections.sort(key_list);
    		    		
    		    		int [][] table = new int[key_list.size()][2];
    		    		for(int m = 0; m < key_list.size(); m++)
    		    		{
    		    		   double key = (double)key_list.get(m);
    		    		   ArrayList current_delta_list = delta_table.get(key);
    		    		   table[m][0] = (int)current_delta_list.get(0);
    		    		   table[m][1] = (int)current_delta_list.get(1);
    		    		}
    		    		delta_list.add(table);
    		    		
                        k++;
            		}
            		else if(j == xdim - 1)
            		{
            			 // We have a set of 3 possible deltas to use.
            	    	int a = src[k] - src[k - 1];
            	    	int b = src[k] - src[k + xdim - 1];
            	    	int c = src[k] - src[k + xdim];
            	    	
            	    	double _a = Math.abs(a);
            	    	ArrayList list_a = new ArrayList();
            	    	list_a.add(a);
            	    	int location = 3;
            	    	list_a.add(location);
            	    	
            	    	double _b = Math.abs(b);
            	    	ArrayList list_b = new ArrayList();
            	    	list_b.add(b);
            	    	location = 5;
            	    	list_b.add(location);
            	    	
            	    	double _c = Math.abs(c);
            	    	ArrayList list_c = new ArrayList();
            	    	list_c.add(c);
            	    	location = 6;
            	    	list_c.add(location);
            	    	
            	    	Hashtable <Double, ArrayList> delta_table = new Hashtable <Double, ArrayList>();
            	    	ArrayList key_list = new ArrayList();
            	    
    		    		double addend = 0.00000001; 
    		    		
    		    		delta_table.put(_a, list_a);
    		    		key_list.add(_a);
    		    		
    		    		if(delta_table.containsKey(_b))
    		    		{
    		    			_b += addend;
    		    			addend *= 2;
    		    		}
    		    		delta_table.put(_b, list_b);
    		    		key_list.add(_b);
    		    		
    		    		if(delta_table.containsKey(_c))
    		    		{
    		    			_c += addend;
    		    			addend *= 2;
    		    		}
    		    		delta_table.put(_c, list_c);
    		    		key_list.add(_c);
    		    		
    		    		Collections.sort(key_list);
    		    		
    		    		int [][] table = new int[key_list.size()][2];
    		    		for(int m = 0; m < key_list.size(); m++)
    		    		{
    		    		   double key = (double)key_list.get(m);
    		    		   ArrayList current_delta_list = delta_table.get(key);
    		    		   table[m][0] = (int)current_delta_list.get(0);
    		    		   table[m][1] = (int)current_delta_list.get(1);
    		    		}
    		    		
    		    		delta_list.add(table);
            	    	
            			k++;	
            		}
            	}
            }
        	else if(i < ydim - 1)
        	{
        		for(int j = 0; j < xdim; j++)
                {
            	    if(j == 0)
            	    {
            	    	// We have a set of 5 possible deltas to use.
            	    	int a = src[k] - src[k - xdim];
            	    	int b = src[k] - src[k - xdim + 1];
            	    	int c = src[k] - src[k + 1];
            	    	int d = src[k] - src[k + xdim];
            	    	int e = src[k] - src[k + xdim + 1];
            	    	
            	    	double _a = Math.abs(a);
            	    	ArrayList list_a = new ArrayList();
            	    	list_a.add(a);
            	    	int location = 1;
            	    	list_a.add(location);
            	    	
            	    	double _b = Math.abs(b);
            	    	ArrayList list_b = new ArrayList();
            	    	list_b.add(b);
            	    	location = 2;
            	    	list_b.add(location);
            	    	
            	    	double _c = Math.abs(c);
            	    	ArrayList list_c = new ArrayList();
            	    	list_c.add(c);
            	    	location = 4;
            	    	list_c.add(location);
            	    	
            	    	double _d = Math.abs(d);
            	    	ArrayList list_d = new ArrayList();
            	    	list_d.add(d);
            	    	location = 6;
            	    	list_d.add(location);
            	    	
            	    	double _e = Math.abs(e);
            	    	ArrayList list_e = new ArrayList();
            	    	list_e.add(e);
            	    	location = 7;
            	    	list_e.add(location);
            	    	
            	    	Hashtable <Double, ArrayList> delta_table = new Hashtable <Double, ArrayList>();
            	    	ArrayList key_list = new ArrayList();
            	    	
    		    		
    		    		double addend = 0.00000001;
    		    		
    		    		delta_table.put(_a, list_a);
    		    		key_list.add(_a);
    		    		
    		    		if(delta_table.containsKey(_b))
    		    		{
    		    			_b += addend;
    		    			addend *= 2;
    		    		}
    		    		delta_table.put(_b, list_b);
    		    		key_list.add(_b);
    		    		
    		    		if(delta_table.containsKey(_c))
    		    		{
    		    			_c += addend;
    		    			addend *= 2;
    		    		}
    		    		delta_table.put(_c, list_c);
    		    		key_list.add(_c);
    		    		
    		    		if(delta_table.containsKey(_d))
    		    		{
    		    			_d += addend;
    		    			addend *= 2;
    		    		}
    		    		delta_table.put(_d, list_d);
    		    		key_list.add(_d);
    		    		
    		    		if(delta_table.containsKey(_e))
    		    		{
    		    			_e += addend;
    		    			addend *= 2;
    		    		}
    		    		delta_table.put(_e, list_e);
    		    		key_list.add(_e);
    		    		
    		    		
    		    		Collections.sort(key_list);
    		    		
    		    		int [][] table = new int[key_list.size()][2];
    		    		for(int m = 0; m < key_list.size(); m++)
    		    		{
    		    		   double key = (double)key_list.get(m);
    		    		   ArrayList current_delta_list = delta_table.get(key);
    		    		   table[m][0] = (int)current_delta_list.get(0);
    		    		   table[m][1] = (int)current_delta_list.get(1);
    		    		}
    		    		delta_list.add(table);
    		    		
            			k++;
            	    }
            	    else if(j < xdim - 1)
            	    {
            	    	// We have a set of 8 possible deltas to use.
            	    	int a = src[k] - src[k - xdim - 1];
            	    	int b = src[k] - src[k - xdim];
            	    	int c = src[k] - src[k - xdim + 1];
            	    	int d = src[k] - src[k - 1];
            	    	int e = src[k] - src[k + 1];
            	    	int f = src[k] - src[k + xdim - 1];
            	    	int g = src[k] - src[k + xdim];
            	    	int h = src[k] - src[k + xdim + 1];
            	    
            	    	
            	    	double _a = Math.abs(a);
            	    	ArrayList list_a = new ArrayList();
            	    	list_a.add(a);
            	    	int location = 0;
            	    	list_a.add(location);
            	    	
            	    	double _b = Math.abs(b);
            	    	ArrayList list_b = new ArrayList();
            	    	list_b.add(b);
            	    	location = 1;
            	    	list_b.add(location);
            	    	
            	    	double _c = Math.abs(c);
            	    	ArrayList list_c = new ArrayList();
            	    	list_c.add(c);
            	    	location = 2;
            	    	list_c.add(location);
            	    	
            	    	double _d = Math.abs(d);
            	    	ArrayList list_d = new ArrayList();
            	    	list_d.add(d);
            	    	location = 3;
            	    	list_d.add(location);
            	    	
            	    	double _e = Math.abs(e);
            	    	ArrayList list_e = new ArrayList();
            	    	list_e.add(e);
            	    	location = 4;
            	    	list_e.add(location);
            	    	
            	    	double _f = Math.abs(f);
            	    	ArrayList list_f = new ArrayList();
            	    	list_f.add(f);
            	    	location = 5;
            	    	list_f.add(location);
            	    	
            	    	double _g = Math.abs(g);
            	    	ArrayList list_g = new ArrayList();
            	    	list_g.add(g);
            	    	location = 6;
            	    	list_g.add(location);
            	    	
            	    	double _h = Math.abs(h);
            	    	ArrayList list_h = new ArrayList();
            	    	list_h.add(h);
            	    	location = 7;
            	    	list_h.add(location);
            	    	
            	    	
            	    	Hashtable <Double, ArrayList> delta_table = new Hashtable <Double, ArrayList>();
            	    	ArrayList key_list = new ArrayList();
            	    	
    		    		
    		    		double addend = 0.00000001;
    		    		
    		    		delta_table.put(_a, list_a);
    		    		key_list.add(_a);
    		    		
    		    		if(delta_table.containsKey(_b))
    		    		{
    		    			_b += addend;
    		    			addend *= 2;
    		    		}
    		    		delta_table.put(_b, list_b);
    		    		key_list.add(_b);
    		    		
    		    		if(delta_table.containsKey(_c))
    		    		{
    		    			_c += addend;
    		    			addend *= 2;
    		    		}
    		    		delta_table.put(_c, list_c);
    		    		key_list.add(_c);
    		    		
    		    		if(delta_table.containsKey(_d))
    		    		{
    		    			_d += addend;
    		    			addend *= 2;
    		    		}
    		    		delta_table.put(_d, list_d);
    		    		key_list.add(_d);
    		    		
    		    		if(delta_table.containsKey(_e))
    		    		{
    		    			_e += addend;
    		    			addend *= 2;
    		    		}
    		    		delta_table.put(_e, list_e);
    		    		key_list.add(_e);
    		    		
    		    		if(delta_table.containsKey(_f))
    		    		{
    		    			_f += addend;
    		    			addend *= 2;
    		    		}
    		    		delta_table.put(_f, list_f);
    		    		key_list.add(_f);
    		    		
    		    		if(delta_table.containsKey(_g))
    		    		{
    		    			_g += addend;
    		    			addend *= 2;
    		    		}
    		    		delta_table.put(_g, list_g);
    		    		key_list.add(_g);
    		    		
    		    		if(delta_table.containsKey(_h))
    		    		{
    		    			_h += addend;
    		    			addend *= 2;
    		    		}
    		    		delta_table.put(_h, list_h);
    		    		key_list.add(_h);
    		    		
    		    		Collections.sort(key_list);
    		    		
    		    		int [][] table = new int[key_list.size()][2];
    		    		for(int m = 0; m < key_list.size(); m++)
    		    		{
    		    		   double key = (double)key_list.get(m);
    		    		   ArrayList current_delta_list = delta_table.get(key);
    		    		   table[m][0] = (int)current_delta_list.get(0);
    		    		   table[m][1] = (int)current_delta_list.get(1);
    		    		}
    		    		delta_list.add(table);
    		    		
    		    		/*
    		    		if(j == 1 && i == 1)
    		    		{
    		    		    System.out.println("Table:");
    		    		    for(int m = 0; m < table.length; m++)
    		    			    System.out.println(table[m][0] + " " + table[m][1]);
    		    		    System.out.println();
    		    		}
    		    	    */
    		    		
        	    	    k++;	
            	    }
            	    else if(j == xdim - 1)
            	    {
            	    	int a = src[k] - src[k - xdim - 1];
            	    	int b = src[k] - src[k - xdim];
            	    	int c = src[k] - src[k - 1];
            	    	int d = src[k] - src[k + xdim - 1];
            	    	int e = src[k] - src[k + xdim];
            	    	
            	    	double _a = Math.abs(a);
            	    	ArrayList list_a = new ArrayList();
            	    	list_a.add(a);
            	    	int location = 0;
            	    	list_a.add(location);
            	    	
            	    	double _b = Math.abs(b);
            	    	ArrayList list_b = new ArrayList();
            	    	list_b.add(b);
            	    	location = 1;
            	    	list_b.add(location);
            	    	
            	    	double _c = Math.abs(c);
            	    	ArrayList list_c = new ArrayList();
            	    	list_c.add(c);
            	    	location = 3;
            	    	list_c.add(location);
            	    	
            	    	double _d = Math.abs(d);
            	    	ArrayList list_d = new ArrayList();
            	    	list_d.add(d);
            	    	location = 5;
            	    	list_d.add(location);
            	    	
            	    	double _e = Math.abs(e);
            	    	ArrayList list_e = new ArrayList();
            	    	list_e.add(e);
            	    	location = 6;
            	    	list_e.add(location);
            	    	
            	    	Hashtable <Double, ArrayList> delta_table = new Hashtable <Double, ArrayList>();
            	    	ArrayList key_list = new ArrayList();
            	    	
    		    		
    		    		double addend = 0.00000001;
    		    		
    		    		delta_table.put(_a, list_a);
    		    		key_list.add(_a);
    		    		
    		    		if(delta_table.containsKey(_b))
    		    		{
    		    			_b += addend;
    		    			addend *= 2;
    		    		}
    		    		delta_table.put(_b, list_b);
    		    		key_list.add(_b);
    		    		
    		    		if(delta_table.containsKey(_c))
    		    		{
    		    			_c += addend;
    		    			addend *= 2;
    		    		}
    		    		delta_table.put(_c, list_c);
    		    		key_list.add(_c);
    		    		
    		    		if(delta_table.containsKey(_d))
    		    		{
    		    			_d += addend;
    		    			addend *= 2;
    		    		}
    		    		delta_table.put(_d, list_d);
    		    		key_list.add(_d);
    		    		
    		    		if(delta_table.containsKey(_e))
    		    		{
    		    			_e += addend;
    		    			addend *= 2;
    		    		}
    		    		delta_table.put(_e, list_e);
    		    		key_list.add(_e);
    		    		
    		    		
    		    		Collections.sort(key_list);
    		    		
    		    		int [][] table = new int[key_list.size()][2];
    		    		for(int m = 0; m < key_list.size(); m++)
    		    		{
    		    		   double key = (double)key_list.get(m);
    		    		   ArrayList current_delta_list = delta_table.get(key);
    		    		   table[m][0] = (int)current_delta_list.get(0);
    		    		   table[m][1] = (int)current_delta_list.get(1);
    		    		}
    		    		delta_list.add(table);
    		    		
    		    		k++;
            	    }
                }
        	}
        	else if(i == ydim - 1)
        	{
                for(int j = 0; j < xdim; j++)
                {
            	    if(j == 0)
            	    {
            		    // We have a set of 3 possible deltas to use.
            	    	int a = src[k] - src[k - xdim];
            	    	int b = src[k] - src[k - xdim + 1];
            	    	int c = src[k] - src[k + 1];
            	    	
            	    	double _a = Math.abs(a);
            	    	ArrayList list_a = new ArrayList();
            	    	list_a.add(a);
            	    	int location = 1;
            	    	list_a.add(location);
            	    	
            	    	double _b = Math.abs(b);
            	    	ArrayList list_b = new ArrayList();
            	    	list_b.add(b);
            	    	location = 2;
            	    	list_b.add(location);
            	    	
            	    	double _c = Math.abs(c);
            	    	ArrayList list_c = new ArrayList();
            	    	list_c.add(c);
            	    	location = 4;
            	    	list_c.add(location);
            	    	
            	    	Hashtable <Double, ArrayList> delta_table = new Hashtable <Double, ArrayList>();
            	    	ArrayList key_list = new ArrayList();
            	    
    		    		double addend = 0.00000001; 
    		    		
    		    		delta_table.put(_a, list_a);
    		    		key_list.add(_a);
    		    		
    		    		if(delta_table.containsKey(_b))
    		    		{
    		    			_b += addend;
    		    			addend *= 2;
    		    		}
    		    		delta_table.put(_b, list_b);
    		    		key_list.add(_b);
    		    		
    		    		if(delta_table.containsKey(_c))
    		    		{
    		    			_c += addend;
    		    			addend *= 2;
    		    		}
    		    		delta_table.put(_c, list_c);
    		    		key_list.add(_c);
    		    		
    		    		Collections.sort(key_list);
    		    		
    		    		int [][] table = new int[key_list.size()][2];
    		    		for(int m = 0; m < key_list.size(); m++)
    		    		{
    		    		   double key = (double)key_list.get(m);
    		    		   ArrayList current_delta_list = delta_table.get(key);
    		    		   table[m][0] = (int)current_delta_list.get(0);
    		    		   table[m][1] = (int)current_delta_list.get(1);
    		    		}
    		    		
    		    		delta_list.add(table);
            	    	
            	    	
            			k++;
            	    }
            		else if(j < xdim - 1)
            		{
            			// We have a set of 5 possible deltas to use.
            			int a = src[k] - src[k - xdim - 1];
            	    	int b = src[k] - src[k - xdim];
            	    	int c = src[k] - src[k - xdim + 1];
            	    	int d = src[k] - src[k - 1];
            	    	int e = src[k] - src[k + 1];
            	
            	    	double _a = Math.abs(a);
            	    	ArrayList list_a = new ArrayList();
            	    	list_a.add(a);
            	    	int location = 0;
            	    	list_a.add(location);
            	    	
            	    	double _b = Math.abs(b);
            	    	ArrayList list_b = new ArrayList();
            	    	list_b.add(b);
            	    	location = 1;
            	    	list_b.add(location);
            	    	
            	    	double _c = Math.abs(c);
            	    	ArrayList list_c = new ArrayList();
            	    	list_c.add(c);
            	    	location = 2;
            	    	list_c.add(location);
            	    	
            	    	double _d = Math.abs(d);
            	    	ArrayList list_d = new ArrayList();
            	    	list_d.add(d);
            	    	location = 3;
            	    	list_d.add(location);
            	    	
            	    	double _e = Math.abs(e);
            	    	ArrayList list_e = new ArrayList();
            	    	list_e.add(e);
            	    	location = 4;
            	    	list_e.add(location);
            	    	
            	    	Hashtable <Double, ArrayList> delta_table = new Hashtable <Double, ArrayList>();
            	    	ArrayList key_list = new ArrayList();
            	    	
    		    		
    		    		double addend = 0.00000001;
    		    		
    		    		delta_table.put(_a, list_a);
    		    		key_list.add(_a);
    		    		
    		    		if(delta_table.containsKey(_b))
    		    		{
    		    			_b += addend;
    		    			addend *= 2;
    		    		}
    		    		delta_table.put(_b, list_b);
    		    		key_list.add(_b);
    		    		
    		    		if(delta_table.containsKey(_c))
    		    		{
    		    			_c += addend;
    		    			addend *= 2;
    		    		}
    		    		delta_table.put(_c, list_c);
    		    		key_list.add(_c);
    		    		
    		    		if(delta_table.containsKey(_d))
    		    		{
    		    			_d += addend;
    		    			addend *= 2;
    		    		}
    		    		delta_table.put(_d, list_d);
    		    		key_list.add(_d);
    		    		
    		    		if(delta_table.containsKey(_e))
    		    		{
    		    			_e += addend;
    		    			addend *= 2;
    		    		}
    		    		delta_table.put(_e, list_e);
    		    		key_list.add(_e);
    		    		
    		    		
    		    		Collections.sort(key_list);
    		    		
    		    		int [][] table = new int[key_list.size()][2];
    		    		for(int m = 0; m < key_list.size(); m++)
    		    		{
    		    		   double key = (double)key_list.get(m);
    		    		   ArrayList current_delta_list = delta_table.get(key);
    		    		   table[m][0] = (int)current_delta_list.get(0);
    		    		   table[m][1] = (int)current_delta_list.get(1);
    		    		}
    		    		delta_list.add(table);
    		    		
                        k++;
            		}
            		else if(j == xdim - 1)
            		{
            			 // We have a set of 3 possible deltas to use.
            	    	int a = src[k] - src[k - xdim - 1];
            	    	int b = src[k] - src[k - xdim];
            	    	int c = src[k] - src[k - 1];
            	    	
            	    	double _a = Math.abs(a);
            	    	ArrayList list_a = new ArrayList();
            	    	list_a.add(a);
            	    	int location = 0;
            	    	list_a.add(location);
            	    	
            	    	double _b = Math.abs(b);
            	    	ArrayList list_b = new ArrayList();
            	    	list_b.add(b);
            	    	location = 1;
            	    	list_b.add(location);
            	    	
            	    	double _c = Math.abs(c);
            	    	ArrayList list_c = new ArrayList();
            	    	list_c.add(c);
            	    	location = 3;
            	    	list_c.add(location);
            	    	
            	    	Hashtable <Double, ArrayList> delta_table = new Hashtable <Double, ArrayList>();
            	    	ArrayList key_list = new ArrayList();
            	    
    		    		double addend = 0.00000001; 
    		    		
    		    		delta_table.put(_a, list_a);
    		    		key_list.add(_a);
    		    		
    		    		if(delta_table.containsKey(_b))
    		    		{
    		    			_b += addend;
    		    			addend *= 2;
    		    		}
    		    		delta_table.put(_b, list_b);
    		    		key_list.add(_b);
    		    		
    		    		if(delta_table.containsKey(_c))
    		    		{
    		    			_c += addend;
    		    			addend *= 2;
    		    		}
    		    		delta_table.put(_c, list_c);
    		    		key_list.add(_c);
    		    		
    		    		Collections.sort(key_list);
    		    		
    		    		int [][] table = new int[key_list.size()][2];
    		    		for(int m = 0; m < key_list.size(); m++)
    		    		{
    		    		   double key = (double)key_list.get(m);
    		    		   ArrayList current_delta_list = delta_table.get(key);
    		    		   table[m][0] = (int)current_delta_list.get(0);
    		    		   table[m][1] = (int)current_delta_list.get(1);
    		    		}
    		    		
    		    		delta_list.add(table);
            	    	
            			k++;	
            		}
            	}	
        	}
        }
        
    	return delta_list;
    }
   
    public static ArrayList getDeltaListFromValues2(int src[], int xdim, int ydim)
    {
    	ArrayList delta_list = new ArrayList();
    	
    	int k = 0;
        for(int i = 0; i < ydim; i++)
        {
        	if(i == 0)
        	{
                for(int j = 0; j < xdim; j++)
                {
            	    if(j == 0)
            	    {
            	    	// We have a set of 3 possible deltas to use.
            	    	int a = src[k] - src[k + 1];
            	    	int b = src[k] - src[k + xdim];
            	    	int c = src[k] - src[k + xdim + 1];
            	    	
            	    	double _a = Math.abs(a);
            	    	ArrayList list_a = new ArrayList();
            	    	list_a.add(a);
            	    	int location = 4;
            	    	list_a.add(location);
            	    	
            	    	double _b = Math.abs(b);
            	    	ArrayList list_b = new ArrayList();
            	    	list_b.add(b);
            	    	location = 6;
            	    	list_b.add(location);
            	    	
            	    	double _c = Math.abs(c);
            	    	ArrayList list_c = new ArrayList();
            	    	list_c.add(c);
            	    	location = 7;
            	    	list_c.add(location);
            	    	
            	    	Hashtable <Double, ArrayList> delta_table = new Hashtable <Double, ArrayList>();
            	    	ArrayList key_list = new ArrayList();
            	    
    		    		double addend = 0.00000001; 
    		    		
    		    		delta_table.put(_a, list_a);
    		    		key_list.add(_a);
    		    		
    		    		if(delta_table.containsKey(_b))
    		    		{
    		    			_b += addend;
    		    			addend *= 2;
    		    		}
    		    		delta_table.put(_b, list_b);
    		    		key_list.add(_b);
    		    		
    		    		if(delta_table.containsKey(_c))
    		    		{
    		    			_c += addend;
    		    			addend *= 2;
    		    		}
    		    		delta_table.put(_c, list_c);
    		    		key_list.add(_c);
    		    		
    		    		Collections.sort(key_list);
    		    		
    		    		int [][] table = new int[key_list.size()][2];
    		    		for(int m = 0; m < key_list.size(); m++)
    		    		{
    		    		   double key = (double)key_list.get(m);
    		    		   ArrayList current_delta_list = delta_table.get(key);
    		    		   table[m][0] = (int)current_delta_list.get(0);
    		    		   table[m][1] = (int)current_delta_list.get(1);
    		    		}
    		    		
    		    		delta_list.add(table);
    		    		
            			k++;
            	    }
            		else if(j < xdim - 1)
            		{
            			// We have a set of 5 possible deltas to use.
            			int a = src[k] - src[k - 1];
            	    	int b = src[k] - src[k + 1];
            	    	int c = src[k] - src[k + xdim - 1];
            	    	int d = src[k] - src[k + xdim];
            	    	int e = src[k] - src[k + xdim + 1];
            	    	
            	    	double _a = Math.abs(a);
            	    	ArrayList list_a = new ArrayList();
            	    	list_a.add(a);
            	    	int location = 3;
            	    	list_a.add(location);
            	    	
            	    	double _b = Math.abs(b);
            	    	ArrayList list_b = new ArrayList();
            	    	list_b.add(b);
            	    	location = 4;
            	    	list_b.add(location);
            	    	
            	    	double _c = Math.abs(c);
            	    	ArrayList list_c = new ArrayList();
            	    	list_c.add(c);
            	    	location = 5;
            	    	list_c.add(location);
            	    	
            	    	double _d = Math.abs(d);
            	    	ArrayList list_d = new ArrayList();
            	    	list_d.add(d);
            	    	location = 6;
            	    	list_d.add(location);
            	    	
            	    	double _e = Math.abs(e);
            	    	ArrayList list_e = new ArrayList();
            	    	list_e.add(e);
            	    	location = 7;
            	    	list_e.add(location);
            	    	
            	    	Hashtable <Double, ArrayList> delta_table = new Hashtable <Double, ArrayList>();
            	    	ArrayList key_list = new ArrayList();
            	    	
    		    		
    		    		double addend = 0.00000001;
    		    		
    		    		delta_table.put(_a, list_a);
    		    		key_list.add(_a);
    		    		
    		    		if(delta_table.containsKey(_b))
    		    		{
    		    			_b += addend;
    		    			addend *= 2;
    		    		}
    		    		delta_table.put(_b, list_b);
    		    		key_list.add(_b);
    		    		
    		    		if(delta_table.containsKey(_c))
    		    		{
    		    			_c += addend;
    		    			addend *= 2;
    		    		}
    		    		delta_table.put(_c, list_c);
    		    		key_list.add(_c);
    		    		
    		    		if(delta_table.containsKey(_d))
    		    		{
    		    			_d += addend;
    		    			addend *= 2;
    		    		}
    		    		delta_table.put(_d, list_d);
    		    		key_list.add(_d);
    		    		
    		    		if(delta_table.containsKey(_e))
    		    		{
    		    			_e += addend;
    		    			addend *= 2;
    		    		}
    		    		delta_table.put(_e, list_e);
    		    		key_list.add(_e);
    		    		
    		    		
    		    		Collections.sort(key_list);
    		    		
    		    		int [][] table = new int[key_list.size()][2];
    		    		for(int m = 0; m < key_list.size(); m++)
    		    		{
    		    		   double key = (double)key_list.get(m);
    		    		   ArrayList current_delta_list = delta_table.get(key);
    		    		   table[m][0] = (int)current_delta_list.get(0);
    		    		   table[m][1] = (int)current_delta_list.get(1);
    		    		}
    		    		delta_list.add(table);
    		    		
                        k++;
            		}
            		else if(j == xdim - 1)
            		{
            			 // We have a set of 3 possible deltas to use.
            	    	int a = src[k] - src[k - 1];
            	    	int b = src[k] - src[k + xdim - 1];
            	    	int c = src[k] - src[k + xdim];
            	    	
            	    	double _a = Math.abs(a);
            	    	ArrayList list_a = new ArrayList();
            	    	list_a.add(a);
            	    	int location = 3;
            	    	list_a.add(location);
            	    	
            	    	double _b = Math.abs(b);
            	    	ArrayList list_b = new ArrayList();
            	    	list_b.add(b);
            	    	location = 5;
            	    	list_b.add(location);
            	    	
            	    	double _c = Math.abs(c);
            	    	ArrayList list_c = new ArrayList();
            	    	list_c.add(c);
            	    	location = 6;
            	    	list_c.add(location);
            	    	
            	    	Hashtable <Double, ArrayList> delta_table = new Hashtable <Double, ArrayList>();
            	    	ArrayList key_list = new ArrayList();
            	    
    		    		double addend = 0.00000001; 
    		    		
    		    		delta_table.put(_a, list_a);
    		    		key_list.add(_a);
    		    		
    		    		if(delta_table.containsKey(_b))
    		    		{
    		    			_b += addend;
    		    			addend *= 2;
    		    		}
    		    		delta_table.put(_b, list_b);
    		    		key_list.add(_b);
    		    		
    		    		if(delta_table.containsKey(_c))
    		    		{
    		    			_c += addend;
    		    			addend *= 2;
    		    		}
    		    		delta_table.put(_c, list_c);
    		    		key_list.add(_c);
    		    		
    		    		Collections.sort(key_list);
    		    		
    		    		int [][] table = new int[key_list.size()][2];
    		    		for(int m = 0; m < key_list.size(); m++)
    		    		{
    		    		   double key = (double)key_list.get(m);
    		    		   ArrayList current_delta_list = delta_table.get(key);
    		    		   table[m][0] = (int)current_delta_list.get(0);
    		    		   table[m][1] = (int)current_delta_list.get(1);
    		    		}
    		    		
    		    		delta_list.add(table);
            	    	
            			k++;	
            		}
            	}
            }
        	else if(i < ydim - 1)
        	{
        		for(int j = 0; j < xdim; j++)
                {
            	    if(j == 0)
            	    {
            	    	// We have a set of 5 possible deltas to use.
            	    	int a = src[k] - src[k - xdim];
            	    	int b = src[k] - src[k - xdim + 1];
            	    	int c = src[k] - src[k + 1];
            	    	int d = src[k] - src[k + xdim];
            	    	int e = src[k] - src[k + xdim + 1];
            	    	
            	    	double _a = Math.abs(a);
            	    	ArrayList list_a = new ArrayList();
            	    	list_a.add(a);
            	    	int location = 1;
            	    	list_a.add(location);
            	    	
            	    	double _b = Math.abs(b);
            	    	ArrayList list_b = new ArrayList();
            	    	list_b.add(b);
            	    	location = 2;
            	    	list_b.add(location);
            	    	
            	    	double _c = Math.abs(c);
            	    	ArrayList list_c = new ArrayList();
            	    	list_c.add(c);
            	    	location = 4;
            	    	list_c.add(location);
            	    	
            	    	double _d = Math.abs(d);
            	    	ArrayList list_d = new ArrayList();
            	    	list_d.add(d);
            	    	location = 6;
            	    	list_d.add(location);
            	    	
            	    	double _e = Math.abs(e);
            	    	ArrayList list_e = new ArrayList();
            	    	list_e.add(e);
            	    	location = 7;
            	    	list_e.add(location);
            	    	
            	    	Hashtable <Double, ArrayList> delta_table = new Hashtable <Double, ArrayList>();
            	    	ArrayList key_list = new ArrayList();
            	    	
    		    		
    		    		double addend = 0.00000001;
    		    		
    		    		delta_table.put(_a, list_a);
    		    		key_list.add(_a);
    		    		
    		    		if(delta_table.containsKey(_b))
    		    		{
    		    			_b += addend;
    		    			addend *= 2;
    		    		}
    		    		delta_table.put(_b, list_b);
    		    		key_list.add(_b);
    		    		
    		    		if(delta_table.containsKey(_c))
    		    		{
    		    			_c += addend;
    		    			addend *= 2;
    		    		}
    		    		delta_table.put(_c, list_c);
    		    		key_list.add(_c);
    		    		
    		    		if(delta_table.containsKey(_d))
    		    		{
    		    			_d += addend;
    		    			addend *= 2;
    		    		}
    		    		delta_table.put(_d, list_d);
    		    		key_list.add(_d);
    		    		
    		    		if(delta_table.containsKey(_e))
    		    		{
    		    			_e += addend;
    		    			addend *= 2;
    		    		}
    		    		delta_table.put(_e, list_e);
    		    		key_list.add(_e);
    		    		
    		    		
    		    		Collections.sort(key_list);
    		    		
    		    		int [][] table = new int[key_list.size()][2];
    		    		for(int m = 0; m < key_list.size(); m++)
    		    		{
    		    		   double key = (double)key_list.get(m);
    		    		   ArrayList current_delta_list = delta_table.get(key);
    		    		   table[m][0] = (int)current_delta_list.get(0);
    		    		   table[m][1] = (int)current_delta_list.get(1);
    		    		}
    		    		delta_list.add(table);
    		    		
            			k++;
            	    }
            	    else if(j < xdim - 1)
            	    {
                        int [][] init_table = new int[8][2];
            	    	
            	    	for(int m = 0; m < 8; m++)
            	    	{
            	    		init_table[m][1] = m;
            	    	}
            	    	
            	    	
            	    	// We have a set of 8 possible deltas to use.
            	    	int a = src[k] - src[k - xdim - 1];
            	    	
            	    	init_table[0][0] = a;
            	    	
            	    	int b = src[k] - src[k - xdim];
            	    	
            	    	init_table[1][0] = b;
            	    	
            	    	
            	    	int c = src[k] - src[k - xdim + 1];
            	    	init_table[2][0] = c;
            	    	
            	    	int d = src[k] - src[k - 1];
            	    	init_table[3][0] = d;
            	    	int e = src[k] - src[k + 1];
            	    	init_table[4][0] = e;
            	    	int f = src[k] - src[k + xdim - 1];
            	    	init_table[5][0] = f;
            	    	int g = src[k] - src[k + xdim];
            	    	init_table[6][0] = g;
            	    	int h = src[k] - src[k + xdim + 1];
            	    	init_table[7][0] = h;
            	    	
            	    	/*
            	    	if(i == 1 && j == 1)
            	    	{
    		    		    System.out.println("Init table:");
    		    		    for(int m = 0; m < init_table.length; m++)
    		    			    System.out.println(init_table[m][0] + " " + init_table[m][1]);
    		    		    System.out.println();	
            	    	}
            	    	*/
            	    
            	    	
            	    	double _a = Math.abs(a);
            	    	ArrayList list_a = new ArrayList();
            	    	list_a.add(a);
            	    	int location = 0;
            	    	list_a.add(location);
            	    	
            	    	double _b = Math.abs(b);
            	    	ArrayList list_b = new ArrayList();
            	    	list_b.add(b);
            	    	location = 1;
            	    	list_b.add(location);
            	    	
            	    	double _c = Math.abs(c);
            	    	ArrayList list_c = new ArrayList();
            	    	list_c.add(c);
            	    	location = 2;
            	    	list_c.add(location);
            	    	
            	    	double _d = Math.abs(d);
            	    	ArrayList list_d = new ArrayList();
            	    	list_d.add(d);
            	    	location = 3;
            	    	list_d.add(location);
            	    	
            	    	double _e = Math.abs(e);
            	    	ArrayList list_e = new ArrayList();
            	    	list_e.add(e);
            	    	location = 4;
            	    	list_e.add(location);
            	    	
            	    	double _f = Math.abs(f);
            	    	ArrayList list_f = new ArrayList();
            	    	list_f.add(f);
            	    	location = 5;
            	    	list_f.add(location);
            	    	
            	    	double _g = Math.abs(g);
            	    	ArrayList list_g = new ArrayList();
            	    	list_g.add(g);
            	    	location = 6;
            	    	list_g.add(location);
            	    	
            	    	double _h = Math.abs(h);
            	    	ArrayList list_h = new ArrayList();
            	    	list_h.add(h);
            	    	location = 7;
            	    	list_h.add(location);
            	    	
            	    	
            	    	Hashtable <Double, ArrayList> delta_table = new Hashtable <Double, ArrayList>();
            	    	ArrayList key_list = new ArrayList();
            	    	
    		    		
    		    		double addend = 0.00000001;
    		    		
    		    		delta_table.put(_a, list_a);
    		    		key_list.add(_a);
    		    		
    		    		if(delta_table.containsKey(_b))
    		    		{
    		    			_b += addend;
    		    			addend *= 2;
    		    		}
    		    		delta_table.put(_b, list_b);
    		    		key_list.add(_b);
    		    		
    		    		if(delta_table.containsKey(_c))
    		    		{
    		    			_c += addend;
    		    			addend *= 2;
    		    		}
    		    		delta_table.put(_c, list_c);
    		    		key_list.add(_c);
    		    		
    		    		if(delta_table.containsKey(_d))
    		    		{
    		    			_d += addend;
    		    			addend *= 2;
    		    		}
    		    		delta_table.put(_d, list_d);
    		    		key_list.add(_d);
    		    		
    		    		if(delta_table.containsKey(_e))
    		    		{
    		    			_e += addend;
    		    			addend *= 2;
    		    		}
    		    		delta_table.put(_e, list_e);
    		    		key_list.add(_e);
    		    		
    		    		if(delta_table.containsKey(_f))
    		    		{
    		    			_f += addend;
    		    			addend *= 2;
    		    		}
    		    		delta_table.put(_f, list_f);
    		    		key_list.add(_f);
    		    		
    		    		if(delta_table.containsKey(_g))
    		    		{
    		    			_g += addend;
    		    			addend *= 2;
    		    		}
    		    		delta_table.put(_g, list_g);
    		    		key_list.add(_g);
    		    		
    		    		if(delta_table.containsKey(_h))
    		    		{
    		    			_h += addend;
    		    			addend *= 2;
    		    		}
    		    		delta_table.put(_h, list_h);
    		    		key_list.add(_h);
    		    		
    		    		Collections.sort(key_list);
    		    		
    		    		int [][] table = new int[key_list.size()][2];
    		    		
    		    		int neighbor_sum = 0;
    		    		for(int m = 0; m < key_list.size(); m++)
    		    		{
    		    		   double key = (double)key_list.get(m);
    		    		   ArrayList current_delta_list = delta_table.get(key);
    		    		   table[m][0] = (int)current_delta_list.get(0);
    		    		   table[m][1] = (int)current_delta_list.get(1);
    		    		   neighbor_sum += table[m][0];
    		    		}
    		    		
    		    		delta_list.add(table);
    		    		
    		    		/*
    		    		if(j == 1 && i == 1)
    		    		{
    		    		    System.out.println("Table:");
    		    		    for(int m = 0; m < table.length; m++)
    		    			    System.out.println(table[m][0] + " " + table[m][1]);
    		    		    System.out.println();
    		    		}
    		    	    */
    		    		
        	    	    k++;	
            	    }
            	    else if(j == xdim - 1)
            	    {
            	    	int a = src[k] - src[k - xdim - 1];
            	    	int b = src[k] - src[k - xdim];
            	    	int c = src[k] - src[k - 1];
            	    	int d = src[k] - src[k + xdim - 1];
            	    	int e = src[k] - src[k + xdim];
            	    	
            	    	double _a = Math.abs(a);
            	    	ArrayList list_a = new ArrayList();
            	    	list_a.add(a);
            	    	int location = 0;
            	    	list_a.add(location);
            	    	
            	    	double _b = Math.abs(b);
            	    	ArrayList list_b = new ArrayList();
            	    	list_b.add(b);
            	    	location = 1;
            	    	list_b.add(location);
            	    	
            	    	double _c = Math.abs(c);
            	    	ArrayList list_c = new ArrayList();
            	    	list_c.add(c);
            	    	location = 3;
            	    	list_c.add(location);
            	    	
            	    	double _d = Math.abs(d);
            	    	ArrayList list_d = new ArrayList();
            	    	list_d.add(d);
            	    	location = 5;
            	    	list_d.add(location);
            	    	
            	    	double _e = Math.abs(e);
            	    	ArrayList list_e = new ArrayList();
            	    	list_e.add(e);
            	    	location = 6;
            	    	list_e.add(location);
            	    	
            	    	Hashtable <Double, ArrayList> delta_table = new Hashtable <Double, ArrayList>();
            	    	ArrayList key_list = new ArrayList();
            	    	
    		    		
    		    		double addend = 0.00000001;
    		    		
    		    		delta_table.put(_a, list_a);
    		    		key_list.add(_a);
    		    		
    		    		if(delta_table.containsKey(_b))
    		    		{
    		    			_b += addend;
    		    			addend *= 2;
    		    		}
    		    		delta_table.put(_b, list_b);
    		    		key_list.add(_b);
    		    		
    		    		if(delta_table.containsKey(_c))
    		    		{
    		    			_c += addend;
    		    			addend *= 2;
    		    		}
    		    		delta_table.put(_c, list_c);
    		    		key_list.add(_c);
    		    		
    		    		if(delta_table.containsKey(_d))
    		    		{
    		    			_d += addend;
    		    			addend *= 2;
    		    		}
    		    		delta_table.put(_d, list_d);
    		    		key_list.add(_d);
    		    		
    		    		if(delta_table.containsKey(_e))
    		    		{
    		    			_e += addend;
    		    			addend *= 2;
    		    		}
    		    		delta_table.put(_e, list_e);
    		    		key_list.add(_e);
    		    		
    		    		
    		    		Collections.sort(key_list);
    		    		
    		    		int [][] table = new int[key_list.size()][2];
    		    		for(int m = 0; m < key_list.size(); m++)
    		    		{
    		    		   double key = (double)key_list.get(m);
    		    		   ArrayList current_delta_list = delta_table.get(key);
    		    		   table[m][0] = (int)current_delta_list.get(0);
    		    		   table[m][1] = (int)current_delta_list.get(1);
    		    		}
    		    		delta_list.add(table);
    		    		
    		    		k++;
            	    }
                }
        	}
        	else if(i == ydim - 1)
        	{
                for(int j = 0; j < xdim; j++)
                {
            	    if(j == 0)
            	    {
            		    // We have a set of 3 possible deltas to use.
            	    	int a = src[k] - src[k - xdim];
            	    	int b = src[k] - src[k - xdim + 1];
            	    	int c = src[k] - src[k + 1];
            	    	
            	    	double _a = Math.abs(a);
            	    	ArrayList list_a = new ArrayList();
            	    	list_a.add(a);
            	    	int location = 1;
            	    	list_a.add(location);
            	    	
            	    	double _b = Math.abs(b);
            	    	ArrayList list_b = new ArrayList();
            	    	list_b.add(b);
            	    	location = 2;
            	    	list_b.add(location);
            	    	
            	    	double _c = Math.abs(c);
            	    	ArrayList list_c = new ArrayList();
            	    	list_c.add(c);
            	    	location = 4;
            	    	list_c.add(location);
            	    	
            	    	Hashtable <Double, ArrayList> delta_table = new Hashtable <Double, ArrayList>();
            	    	ArrayList key_list = new ArrayList();
            	    
    		    		double addend = 0.00000001; 
    		    		
    		    		delta_table.put(_a, list_a);
    		    		key_list.add(_a);
    		    		
    		    		if(delta_table.containsKey(_b))
    		    		{
    		    			_b += addend;
    		    			addend *= 2;
    		    		}
    		    		delta_table.put(_b, list_b);
    		    		key_list.add(_b);
    		    		
    		    		if(delta_table.containsKey(_c))
    		    		{
    		    			_c += addend;
    		    			addend *= 2;
    		    		}
    		    		delta_table.put(_c, list_c);
    		    		key_list.add(_c);
    		    		
    		    		Collections.sort(key_list);
    		    		
    		    		int [][] table = new int[key_list.size()][2];
    		    		for(int m = 0; m < key_list.size(); m++)
    		    		{
    		    		   double key = (double)key_list.get(m);
    		    		   ArrayList current_delta_list = delta_table.get(key);
    		    		   table[m][0] = (int)current_delta_list.get(0);
    		    		   table[m][1] = (int)current_delta_list.get(1);
    		    		}
    		    		
    		    		delta_list.add(table);
            	    	
            	    	
            			k++;
            	    }
            		else if(j < xdim - 1)
            		{
            			// We have a set of 5 possible deltas to use.
            			int a = src[k] - src[k - xdim - 1];
            	    	int b = src[k] - src[k - xdim];
            	    	int c = src[k] - src[k - xdim + 1];
            	    	int d = src[k] - src[k - 1];
            	    	int e = src[k] - src[k + 1];
            	
            	    	double _a = Math.abs(a);
            	    	ArrayList list_a = new ArrayList();
            	    	list_a.add(a);
            	    	int location = 0;
            	    	list_a.add(location);
            	    	
            	    	double _b = Math.abs(b);
            	    	ArrayList list_b = new ArrayList();
            	    	list_b.add(b);
            	    	location = 1;
            	    	list_b.add(location);
            	    	
            	    	double _c = Math.abs(c);
            	    	ArrayList list_c = new ArrayList();
            	    	list_c.add(c);
            	    	location = 2;
            	    	list_c.add(location);
            	    	
            	    	double _d = Math.abs(d);
            	    	ArrayList list_d = new ArrayList();
            	    	list_d.add(d);
            	    	location = 3;
            	    	list_d.add(location);
            	    	
            	    	double _e = Math.abs(e);
            	    	ArrayList list_e = new ArrayList();
            	    	list_e.add(e);
            	    	location = 4;
            	    	list_e.add(location);
            	    	
            	    	Hashtable <Double, ArrayList> delta_table = new Hashtable <Double, ArrayList>();
            	    	ArrayList key_list = new ArrayList();
            	    	
    		    		
    		    		double addend = 0.00000001;
    		    		
    		    		delta_table.put(_a, list_a);
    		    		key_list.add(_a);
    		    		
    		    		if(delta_table.containsKey(_b))
    		    		{
    		    			_b += addend;
    		    			addend *= 2;
    		    		}
    		    		delta_table.put(_b, list_b);
    		    		key_list.add(_b);
    		    		
    		    		if(delta_table.containsKey(_c))
    		    		{
    		    			_c += addend;
    		    			addend *= 2;
    		    		}
    		    		delta_table.put(_c, list_c);
    		    		key_list.add(_c);
    		    		
    		    		if(delta_table.containsKey(_d))
    		    		{
    		    			_d += addend;
    		    			addend *= 2;
    		    		}
    		    		delta_table.put(_d, list_d);
    		    		key_list.add(_d);
    		    		
    		    		if(delta_table.containsKey(_e))
    		    		{
    		    			_e += addend;
    		    			addend *= 2;
    		    		}
    		    		delta_table.put(_e, list_e);
    		    		key_list.add(_e);
    		    		
    		    		
    		    		Collections.sort(key_list);
    		    		
    		    		int [][] table = new int[key_list.size()][2];
    		    		for(int m = 0; m < key_list.size(); m++)
    		    		{
    		    		   double key = (double)key_list.get(m);
    		    		   ArrayList current_delta_list = delta_table.get(key);
    		    		   table[m][0] = (int)current_delta_list.get(0);
    		    		   table[m][1] = (int)current_delta_list.get(1);
    		    		}
    		    		delta_list.add(table);
    		    		
                        k++;
            		}
            		else if(j == xdim - 1)
            		{
            			 // We have a set of 3 possible deltas to use.
            	    	int a = src[k] - src[k - xdim - 1];
            	    	int b = src[k] - src[k - xdim];
            	    	int c = src[k] - src[k - 1];
            	    	
            	    	double _a = Math.abs(a);
            	    	ArrayList list_a = new ArrayList();
            	    	list_a.add(a);
            	    	int location = 0;
            	    	list_a.add(location);
            	    	
            	    	double _b = Math.abs(b);
            	    	ArrayList list_b = new ArrayList();
            	    	list_b.add(b);
            	    	location = 1;
            	    	list_b.add(location);
            	    	
            	    	double _c = Math.abs(c);
            	    	ArrayList list_c = new ArrayList();
            	    	list_c.add(c);
            	    	location = 3;
            	    	list_c.add(location);
            	    	
            	    	Hashtable <Double, ArrayList> delta_table = new Hashtable <Double, ArrayList>();
            	    	ArrayList key_list = new ArrayList();
            	    
    		    		double addend = 0.00000001; 
    		    		
    		    		delta_table.put(_a, list_a);
    		    		key_list.add(_a);
    		    		
    		    		if(delta_table.containsKey(_b))
    		    		{
    		    			_b += addend;
    		    			addend *= 2;
    		    		}
    		    		delta_table.put(_b, list_b);
    		    		key_list.add(_b);
    		    		
    		    		if(delta_table.containsKey(_c))
    		    		{
    		    			_c += addend;
    		    			addend *= 2;
    		    		}
    		    		delta_table.put(_c, list_c);
    		    		key_list.add(_c);
    		    		
    		    		Collections.sort(key_list);
    		    		
    		    		int [][] table = new int[key_list.size()][2];
    		    		for(int m = 0; m < key_list.size(); m++)
    		    		{
    		    		   double key = (double)key_list.get(m);
    		    		   ArrayList current_delta_list = delta_table.get(key);
    		    		   table[m][0] = (int)current_delta_list.get(0);
    		    		   table[m][1] = (int)current_delta_list.get(1);
    		    		}
    		    		
    		    		delta_list.add(table);
            	    	
            			k++;	
            		}
            	}	
        	}
        }
        
    	return delta_list;
    }
   
    
    public static ArrayList getNeighbors(int [] src, int x, int y, int xdim, int ydim)
	{
		ArrayList neighbors = new ArrayList();
		
		if(y > 0)
		{
			if(x > 0)
			    neighbors.add(src[(y - 1) * xdim + x - 1]);
			neighbors.add(src[(y - 1) * xdim + x]);
			if(x < xdim - 1)
			    neighbors.add(src[(y - 1) * xdim + x + 1]);
		}
		
		if(x > 0)
		{
			neighbors.add(src[y * xdim + x - 1]);
		}
		if(x < xdim - 1)
		{
			neighbors.add(src[y * xdim + x + 1]);	
		}
		
		if(y < ydim - 1)
		{ 
			if(x > 0)
			    neighbors.add(src[(y + 1) * xdim + x - 1]);	
			neighbors.add(src[(y + 1) * xdim + x]);
			if(x < xdim - 1)
			    neighbors.add(src[(y + 1) * xdim + x + 1]);
		}
		
		return neighbors;
	}
    
    public static int getLocationType(int x, int y, int xdim, int ydim)
	{
		int location_type = 0;
		
		if (y == 0)
		{
			if (x == 0)
				location_type = 1; 
			else if(x < xdim - 1)
				location_type = 2;
            else if(x == xdim - 1)
				location_type = 3;
		} 
		else if(y < ydim - 1)
		{
			if(x == 0)
				location_type = 4;
            else if(x < xdim - 1)
				location_type = 5;
            else if(x == xdim - 1)
				location_type = 6;
		} 
		else if(y == ydim - 1)
		{
			if(x == 0)
				location_type = 7;
            else if(x < xdim - 1)
				location_type = 8;
            else if(x == xdim - 1)
				location_type = 9;
		}
		return (location_type);
	}
    
    public static int getLocationIndex(int location_type, int location)
    {
    	int index = 0;
    	
    	if(location_type == 1)
    	{
    		if(location == 4)
    			index = 0;
    		else if(location == 6)
    			index = 1;
    		else if(location == 7)
    			index = 2;
    		else
    			index = -1;
    	}
    	else if(location_type == 2)
    	{
    		if(location == 3)
    			index = 0;
    		else if(location == 4)
    			index = 1;
    		else if(location == 5)
    			index = 2;
    		else if(location == 6)
    			index = 3;
    		else if(location == 7)
    			index = 4;
    		else
    			index = -1;	
    	}
    	else if(location_type == 3)
    	{
    		if(location == 3)
    			index = 0;
    		else if(location == 5)
    			index = 1;
    		else if(location == 6)
    			index = 2;
    		else
    			index = -1;	
    	}
    	else if(location_type == 4)
    	{
    		if(location == 1)
    			index = 0;
    		else if(location == 2)
    			index = 1;
    		else if(location == 4)
    			index = 2;
    		else if(location == 6)
    			index = 3;
    		else if(location == 7)
    			index = 4;
    		else
    			index = -1;	
    	}
    	else if(location_type == 5)
    	{
    		if(location == 0)
    			index = 0;
    		else if(location == 1)
    			index = 1;
    		else if(location == 2)
    			index = 2;
    		else if(location == 3)
    			index = 3;
    		else if(location == 4)
    			index = 4;
    		else if(location == 5)
    			index = 5;
    		else if(location == 6)
    			index = 6;
    		else if(location == 7)
    			index = 7;
    		else
    			index = -1;	
    	}
    	else if(location_type == 6)
    	{
    		if(location == 0)
    			index = 0;
    		else if(location == 1)
    			index = 1;
    		else if(location == 3)
    			index = 2;
    		else if(location == 5)
    			index = 3;
    		else if(location == 6)
    			index = 4;
    		else
    			index = -1;	
    	}
    	else if(location_type == 7)
    	{
    		if(location == 1)
    			index = 0;
    		else if(location == 2)
    			index = 1;
    		else if(location == 4)
    			index = 2;
    		else
    			index = -1;	
    	}
    	else if(location_type == 8)
    	{
    		if(location == 0)
    			index = 0;
    		else if(location == 1)
    			index = 1;
    		else if(location == 2)
    			index = 2;
    		else if(location == 3)
    			index = 3;
    		else if(location == 4)
    			index = 4;
    		else
    			index = -1;	
    	}
    	else if(location_type == 9)
    	{
    		if(location == 0)
    			index = 0;
    		else if(location == 1)
    			index = 1;
    		else if(location == 3)
    			index = 2;
    		else
    			index = -1;	
    	}
    	return index;
    }
    
    public static int getNeighborIndex(int x, int y, int xdim, int location)
    {
    	int k = y * xdim + x;
    	
    	if(location == 0)
    		k = k - xdim - 1;
    	else if(location == 1)
    		k = k - xdim;
    	else if(location == 2)
    		k = k - xdim + 1;
    	else if(location == 3)
    		k = k - 1;
    	else if(location == 4)
    		k = k + 1;
    	if(location == 5)
    		k = k + xdim - 1;
    	if(location == 6)
    		k = k + xdim;
    	if(location == 7)
    		k = k + xdim + 1;
    	
    	return k;
    }
    
    public static int getInverseLocation(int location)
    {
    	int inverse_location = 0;
    	if(location == 0)
    		inverse_location = 7;
    	else if(location == 1)
    		inverse_location = 6;
    	else if(location == 2)
    		inverse_location = 5;
    	else if(location == 3)
    		inverse_location = 4;
    	else if(location == 4)
    		inverse_location = 3;
    	else if(location == 5)
    		inverse_location = 2;
    	else if(location == 6)
    		inverse_location = 1;
    	else if(location == 7)
    		inverse_location = 0;
    	
    	return inverse_location;
    }
    
    // Get the ideal deltas.
    // It's not possible to reconstruct the image from these 
    // deltas without some more information, but they can help 
    // measure how well the different delta functions are doing 
    // at selecting the ideal delta.
    public static int [] getIdealDeltasFromList(ArrayList delta_list)
    {
    	int sum = 0;
        int [] ideal_delta = new int[delta_list.size()];
    	for(int i = 0; i < delta_list.size(); i++)
    	{
    		int [][] table   = (int [][])delta_list.get(i);
    		
    		int delta = table[0][0];
    		
    		ideal_delta[i] = delta;
    	}
    	
        return ideal_delta;
    }
    
    // Get the ideal delta sum.
    public static int getIdealDeltaSum(ArrayList delta_list)
    {
    	int sum = 0;
    	
    	for(int i = 0; i < delta_list.size(); i++)
    	{
    		int [][] table   = (int [][])delta_list.get(i);
    		
    		int delta = table[0][0];
    		
    		sum += Math.abs(delta);
    	}
    	
        return sum;
    }
    
 // Get the worst delta sum.
    public static int getWorstlDeltaSum(ArrayList delta_list)
    {
    	int sum = 0;
    	
    	for(int i = 0; i < delta_list.size(); i++)
    	{
    		int [][] table   = (int [][])delta_list.get(i);
    		
    		int delta = table[table.length - 1][0];
    		
    		sum += Math.abs(delta);
    	}
    	
        return sum;
    }
    
    
    // Gets deltas from the entire delta set and a map of which pixels are used.
    // This implementation is not based on a raster scan and uses all possible delta values
    // (delta type 7) but actually produces a lower accuracy rate than the 
    // implementation of delta type 6.
    public static ArrayList getIdealDeltasFromValues2(int src[], int xdim, int ydim, ArrayList delta_list)
    {
    	int size = xdim * ydim;
    	
    	ArrayList seed_delta = new ArrayList();
    	ArrayList seed_map   = new ArrayList();
    	
    	ArrayList dilated_delta = new ArrayList();
    	ArrayList dilated_map   = new ArrayList();
    	
    	
    	int[]     delta       = new int[size];
        byte[]    map         = new byte[size];
        boolean[] is_assigned = new boolean[size];
        boolean[] is_seed     = new boolean[size];
        int[]     assignments = new int[size];
       
        int x = xdim / 2;
        int y = ydim / 2;
        int i = y * xdim + x;
        
        
        int n = 0;
        
        
        int init_value = src[i];
        int [][] table   = (int [][])delta_list.get(i);
        
        // Used only for error checking delta type.
        delta[i] = 0; 
       
        map[i]   = (byte)table[0][1];
        seed_map.add(map[i]);
        is_assigned[i] = true;
        is_seed[i]     = true;
        assignments[i]++;
        n++;
       
        
        // We know this pixel is unassigned.
        i = getNeighborIndex(x, y, xdim, table[0][1]);
        
        delta[i] = -table[0][0];
        seed_delta.add(delta[i]);
        is_assigned[i] = true;
        is_seed[i]     = true;
        assignments[i]++;
        n++;
        
        
        int current_value = init_value + delta[i];
        
        // Tricky part here is we're getting delta and map values
        // from different tables.  
        table = (int[][])delta_list.get(i);
        
        // Now we need to start checking if the optimal delta corresponds to an already assigned pixel,
        // in this special case the initial pixel.
        x = i % xdim;
        y = i / xdim;
        for(int j = 0; j < table.length; j++)
        {
            int k = getNeighborIndex(x, y, xdim, table[j][1]); 
            if(!is_assigned[k])
            {
            	map[i] = (byte)table[j][1];
            	seed_map.add(map[i]);
            	delta[k] = -table[j][0];
            	seed_delta.add(delta[k]);
            	is_assigned[k] = true;
                is_seed[k]     = true;
                assignments[k]++;
                n++;
            	
            	current_value += delta[k];
            	break;
            }
        }
       
        
        // Depending on the dimensions of the image,
        // we can iterate like this at least 8 times without
        // running out of unassigned neighbors.
        // This is just 1 iteration.
        i = getNeighborIndex(x, y, xdim, map[i]);
        x = i % xdim;
        y = i / xdim;
        table = (int[][])delta_list.get(i);
        for(int j = 0; j < table.length; j++)
        {
        	int k = getNeighborIndex(x, y, xdim, table[j][1]); 
            if(!is_assigned[k])
            {
            	map[i]   = (byte)table[j][1];
            	seed_map.add(map[i]);
            	delta[k] = -table[j][0];
            	seed_delta.add(delta[k]);
            	is_assigned[k] = true;
                is_seed[k]     = true;
                assignments[k]++;
                n++;
            	
            	current_value += delta[k];
                
            	break;
            }	
        }
        
        // After a certain number of iterations, we
        // need to check if we've reached a dead end.
        boolean neighbor_unassigned = true;
        boolean first_index         = true;
        while(n < size && neighbor_unassigned)
        {
        	i = getNeighborIndex(x, y, xdim, map[i]);
            x = i % xdim;
            y = i / xdim;
            table = (int[][])delta_list.get(i);
            int j = 0;
            for(j = 0; j < table.length; j++)
            {
            	int k = getNeighborIndex(x, y, xdim, table[j][1]); 
                if(!is_assigned[k])
                {
                	map[i]   = (byte)table[j][1];
                	seed_map.add(map[i]);
                	delta[k] = -table[j][0];
                	seed_delta.add(delta[k]);
                	is_assigned[k] = true;
                    is_seed[k]     = true;
                    assignments[k]++;
                    n++;
                	
                	current_value += delta[k];
                	
                	if(current_value != src[k] && first_index)	
                	{
                	    int _x = k % xdim;
                	    int _y = k / xdim;
                	    first_index = false;
                	    System.out.println("Reconstructed value " + current_value + " does not agree with source value " + src[k] + " at index " + k);
                	    System.out.println("X is " + _x + ", y is " + _y);
                	    System.out.println("Number of deltas collected is " + seed_delta.size());
                	}
                    
                	break;
                }	
            }
            if(j == table.length)
            	neighbor_unassigned = false;
        }
      
        System.out.println("Number of seed pixels assigned is " + n + " out of " + size + " total.");
        //System.out.println("Length of seed delta list is " + seed_delta.size());
        //System.out.println("Length of seed location list is " + seed_map.size());
        
        int  [] value = new int[size];
        x = xdim / 2;
        y = ydim / 2;
        i = y * xdim + x;
        value[i] = init_value;
        
        current_value = init_value;
        
        boolean values_agree = true;
        for(int j = 0; j < seed_map.size(); j++)
        {
            byte location = (byte)seed_map.get(j);
            int  _delta    = (int)seed_delta.get(j);
            
            i = getNeighborIndex(x, y, xdim, (int)location);
            x = i % xdim;
            y = i / xdim;
            value[i] = current_value + _delta;
            current_value = value[i];
            
            if(src[i] != value[i])
            	values_agree = false;
        }
        
        if(!values_agree)
        	System.out.println("Reconstructed seed values do not agree with source values.");
       
        // Now we iterate until we populate the entire raster with delta values and locations.
        int previous_n = 0;
        int p          = 0;
        
        while(n != previous_n && n < size)
        {
            previous_n = n;
            n = 0;
            for(i = 0; i < size; i++)
            {
                if(assignments[i] == 0)
                {
                    x = i % xdim;
                    y = i / xdim;
	        
                    // The location type lets us know the locations of the neighbor pixels.
                    int location_type = getLocationType(x, y, xdim, ydim);
	      
                    table = (int[][])delta_list.get(i);
	        
                    outer: for(int m = 0; m < table.length; m++)
                    {
                        int location = table[m][1];
                        int j =  getLocationIndex(location_type, location);
                        ArrayList neighbors = getNeighbors(assignments, x, y, xdim, ydim);
        		
                        try
                        {
        	                int k = (int)neighbors.get(j);
        	                if(k != 0)
        	                {
        	                    delta[i]       = table[m][0];
        	                    map[i]         = (byte)table[m][1];
        	                    is_assigned[i] = true;
        	                    assignments[i]++;
    	                    }
    	                }   
        	            catch(Exception e)
        	            {
        	                System.out.println("Location type is " + location_type + ", location is " + location + ", index is " + j);
        	                System.out.println("Length of neighbors list is " + neighbors.size());
        	                System.out.println();
        	            }
                    }
                    if(assignments[i] != 0)
	        	        n++;
                }
                else
                    n++;
        	
            }
            p++;
            
        }
        
        System.out.println("Number of assigned deltas after " + p + " dilations is " + n);
        
        // Create the lists we'll use to populate the raster.
        
        for(i = 0; i < size; i++)
        {
        	if(!is_seed[i])
        	{
        		dilated_delta.add(delta[i]);
                dilated_map.add(map[i]);	
                is_assigned[i] = false;
        	}
        }
        
        // Populate the raster.
        for(i = 0; i < dilated_delta.size(); i++)
        {
        	int j = 0;
        	while(is_assigned[j])
        		j++;
        	delta[j] = (int)dilated_delta.get(i);
        	map[j]   = (byte)dilated_map.get(i);
        	is_assigned[j] = true;
        }
        
        int number_of_seeds = 0;
        for(i = 0; i < size; i++)
        {
        	if(!is_seed[i])
        		is_assigned[i] = false;
        	else
        		number_of_seeds++;
        }
        
        n = number_of_seeds;
        p = 0;
        while(n < size)
        {
        	n = 0;
            for(i = 0; i < size; i++)	
            {
            	if(!is_assigned[i])
            	{
            		byte location = map[i];
            		x = i % xdim;
            		y = i / xdim;
            		int j = getNeighborIndex(x, y, xdim, (int)location);
            		if(is_assigned[j])
            		{
            			value[i] = value[j] + delta[i];
            			is_assigned[i] = true;
            			n++;
            		}
            	}
            	else
            		n++;
            }
            p++;
        }
        
        
        System.out.println();        
        ArrayList result = new ArrayList();
        
        int sum = 0;
        
        for(i = 0; i < seed_delta.size(); i++)
            sum += Math.abs((int)seed_delta.get(i));
        for(i = 0; i < dilated_delta.size(); i++)
            sum += Math.abs((int)dilated_delta.get(i));
        result.add(sum);
        result.add(seed_delta);
        result.add(seed_map);
        result.add(dilated_delta);
        result.add(dilated_map);
        return result;
    }
    
    // Using thresholding to return a smaller delta sum after dilating.
    public static ArrayList getIdealDeltasFromValues3(int src[], int xdim, int ydim, ArrayList delta_list)
    {
    	int size = xdim * ydim;
    	
    	ArrayList seed_delta = new ArrayList();
    	ArrayList seed_map   = new ArrayList();
    	
    	ArrayList dilated_delta = new ArrayList();
    	ArrayList dilated_map   = new ArrayList();
    	
    	
    	int[]     delta       = new int[size];
        byte[]    map         = new byte[size];
        boolean[] is_assigned = new boolean[size];
        boolean[] is_seed     = new boolean[size];
        int[]     assignments = new int[size];
       
        int x = xdim / 2;
        int y = ydim / 2;
        int i = y * xdim + x;
        
        
        int n = 0;
        
        
        int init_value = src[i];
        int [][] table   = (int [][])delta_list.get(i);
        
        // Used only for error checking delta type.
        delta[i] = 0; 
       
        map[i]   = (byte)table[0][1];
        seed_map.add(map[i]);
        is_assigned[i] = true;
        is_seed[i]     = true;
        assignments[i]++;
        n++;
       
        
        // We know this pixel is unassigned and is the smallest delta.
        i = getNeighborIndex(x, y, xdim, table[0][1]);
        
        delta[i] = -table[0][0];
        seed_delta.add(delta[i]);
        is_assigned[i] = true;
        is_seed[i]     = true;
        assignments[i]++;
        n++;
        
        
        int current_value = init_value + delta[i];
        
        // Tricky part here is we're getting delta and map values
        // from different tables.  
        table = (int[][])delta_list.get(i);
        
        // Now we need to start checking if the optimal delta corresponds to an already assigned pixel,
        // in this special case the initial pixel.
        x = i % xdim;
        y = i / xdim;
        
        for(int j = 0; j < table.length; j++)
        {
            int k = getNeighborIndex(x, y, xdim, table[j][1]); 
            if(!is_assigned[k])
            {
            	map[i] = (byte)table[j][1];
            	seed_map.add(map[i]);
            	delta[k] = -table[j][0];
            	seed_delta.add(delta[k]);
            	is_assigned[k] = true;
                is_seed[k]     = true;
                assignments[k]++;
                n++;
            	
            	current_value += delta[k];
            	break;
            }
        }
        
       
        // Depending on the dimensions of the image,
        // we can iterate like this at least 8 times without
        // running out of unassigned neighbors.
        // This is just 1 iteration.
        i = getNeighborIndex(x, y, xdim, map[i]);
        x = i % xdim;
        y = i / xdim;
        table = (int[][])delta_list.get(i);
        for(int j = 0; j < table.length; j++)
        {
        	int k = getNeighborIndex(x, y, xdim, table[j][1]); 
            if(!is_assigned[k])
            {
            	map[i]   = (byte)table[j][1];
            	seed_map.add(map[i]);
            	delta[k] = -table[j][0];
            	seed_delta.add(delta[k]);
            	is_assigned[k] = true;
                is_seed[k]     = true;
                assignments[k]++;
                n++;
            	
            	current_value += delta[k];
                
            	break;
            }	
        }
        
        // After a certain number of iterations, we
        // need to check if we've reached a dead end.
        boolean neighbor_unassigned = true;
        boolean first_index         = true;
        while(n < size && neighbor_unassigned)
        {
        	i = getNeighborIndex(x, y, xdim, map[i]);
            x = i % xdim;
            y = i / xdim;
            table = (int[][])delta_list.get(i);
            int j = 0;
            for(j = 0; j < table.length; j++)
            {
            	int k = getNeighborIndex(x, y, xdim, table[j][1]); 
            	
            	// This code usually increases the size of the seed segment,
            	// and usually decreases the number of iterations,
            	// but does not significantly increase the overall
            	// compression. There is a slight increase, because
            	// the seed segment compresses at a higher rate
            	// than the dilated segment.
            	// Might not be worth complicating the code.
            	
            	if(!is_assigned[k])
                {
            		int abs_value = Math.abs(table[j][0]);
            		ArrayList index_list = new ArrayList();
            		
            		index_list.add(j);
            		
            		while(j < table.length - 1)
            		{
            			j++;
            			k = getNeighborIndex(x, y, xdim, table[j][1]);
            			if(!is_assigned[k])
            			{
            				if(abs_value == Math.abs(table[j][0]))
            				{
            					index_list.add(j);
            				}
            				else
            					j = table.length;
            			}
            		}
            		if(index_list.size() == 1)
            		{
            		    j = (int)index_list.get(0);
            		    k = getNeighborIndex(x, y, xdim, table[j][1]);
            		    map[i]   = (byte)table[j][1];
                    	seed_map.add(map[i]);
                    	delta[k] = -table[j][0];
                    	seed_delta.add(delta[k]);
                    	is_assigned[k] = true;
                        is_seed[k]     = true;
                        assignments[k]++;
                        n++;
                    	
                    	current_value += delta[k];
                    	
                    	j = 0;
                    	break;
            		}
            		else
            		{
            			// We have a list of indices of the neighbors
            			// with equal deltas.
            			// Now we'll make a list of how many unassigned
            			// neighbors they have.
            			ArrayList number_of_unassigned_neighbors = new ArrayList();
            			for(int m = 0; m < index_list.size(); m++)
            			{
            				j = (int)index_list.get(m);
            				k = getNeighborIndex(x, y, xdim, table[j][1]);
            				int neighbor_x = k % xdim;
            				int neighbor_y = k / xdim;
            				ArrayList neighbor_list = getNeighbors(assignments, neighbor_x, neighbor_y, xdim, ydim);
            				
            				int number_of_neighbors = 0;
            				for(int p = 0; p < neighbor_list.size(); p++)
            				{
            					int q = (int)neighbor_list.get(p);
            					if(q == 0)
            						number_of_neighbors++;
            				}
            				number_of_unassigned_neighbors.add(number_of_neighbors);
            			}
            			
            			int max_index = 0;
            			int max_number = (int)number_of_unassigned_neighbors.get(0);
            			for(int m = 1; m < index_list.size(); m++)
            			{
            				int current_number = (int)number_of_unassigned_neighbors.get(m);
            				if(current_number > max_number)
            				{
            					max_index = m;
            					max_number = current_number;
            				}
            			}
            			
            			j = (int)index_list.get(max_index);
            		    k = getNeighborIndex(x, y, xdim, table[j][1]);
            		    map[i]   = (byte)table[j][1];
                    	seed_map.add(map[i]);
                    	delta[k] = -table[j][0];
                    	seed_delta.add(delta[k]);
                    	is_assigned[k] = true;
                        is_seed[k]     = true;
                        assignments[k]++;
                        n++;
                    	
                    	current_value += delta[k];
                    	j = 0;
                    	break;
            		}
                }
            	
            	// Much simpler code that produces
            	// similar result.
                //if(!is_assigned[k])
                //{
                //	map[i]   = (byte)table[j][1];
                //	seed_map.add(map[i]);
                //	delta[k] = -table[j][0];
                // 	seed_delta.add(delta[k]);
                //	is_assigned[k] = true;
                //    is_seed[k]     = true;
                //    assignments[k]++;
                //    n++;
                	
                //	current_value += delta[k];
                    
                //	break;
                //}	
            }
            if(j == table.length)
            	neighbor_unassigned = false;
        }
      
        System.out.println("Number of seed pixels assigned is " + n + " out of " + size + " total.");
        //System.out.println("Length of seed delta list is " + seed_delta.size());
        //System.out.println("Length of seed location list is " + seed_map.size());
        
        int  [] value = new int[size];
        x = xdim / 2;
        y = ydim / 2;
        i = y * xdim + x;
        value[i] = init_value;
        
       
        current_value = init_value;
        
        boolean values_agree = true;
        for(int j = 0; j < seed_map.size(); j++)
        {
            byte location = (byte)seed_map.get(j);
            int  _delta    = (int)seed_delta.get(j);
            
            i = getNeighborIndex(x, y, xdim, (int)location);
            x = i % xdim;
            y = i / xdim;
            value[i] = current_value + _delta;
            current_value = value[i];
            
            if(src[i] != value[i])
            	values_agree = false;
        }
        
        if(!values_agree)
        	System.out.println("Reconstructed seed values do not agree with source values.");
       
        // Now we iterate until we populate the entire raster with delta values and locations.
        int previous_n = 0;
        int p          = 0;
        int threshold  = 1;
        
        
        //System.out.println("Threshold is " + threshold);
        while(n < size)
        {
        	if(n == previous_n)
        	{
        		threshold++;
        		//System.out.println("Threshold is " + threshold);
        	}
            previous_n = n;
            n = 0;
            for(i = 0; i < size; i++)
            {
                if(assignments[i] == 0)
                {
                    x = i % xdim;
                    y = i / xdim;
	        
                    // The location type lets us know the locations of the neighbor pixels.
                    int location_type = getLocationType(x, y, xdim, ydim);
	      
                    table = (int[][])delta_list.get(i);
	        
                    //outer: for(int m = 0; m < table.length; m++)
                    int limit = threshold;
                    if(table.length < threshold)
                    	limit = table.length;
                    outer: for(int m = 0; m < limit; m++)
                    {
                        int location = table[m][1];
                        int j =  getLocationIndex(location_type, location);
                        ArrayList neighbors = getNeighbors(assignments, x, y, xdim, ydim);
        		
                        try
                        {
        	                int k = (int)neighbors.get(j);
        	                if(k != 0)
        	                {
        	                    delta[i]       = table[m][0];
        	                    map[i]         = (byte)table[m][1];
        	                    is_assigned[i] = true;
        	                    assignments[i]++;
    	                    }
    	                }   
        	            catch(Exception e)
        	            {
        	                System.out.println("Location type is " + location_type + ", location is " + location + ", index is " + j);
        	                System.out.println("Length of neighbors list is " + neighbors.size());
        	                System.out.println();
        	            }
                    }
                    if(assignments[i] != 0)
	        	        n++;
                }
                else
                    n++;
        	
            }
            p++;
            
        }
        
        System.out.println("Number of assigned deltas after " + p + " dilations is " + n);
        
        // Create the lists we'll use to populate the raster.
        
        for(i = 0; i < size; i++)
        {
        	if(!is_seed[i])
        	{
        		dilated_delta.add(delta[i]);
                dilated_map.add(map[i]);	
                is_assigned[i] = false;
        	}
        }
        
        // Populate the raster.
        for(i = 0; i < dilated_delta.size(); i++)
        {
        	int j = 0;
        	while(is_assigned[j])
        		j++;
        	delta[j] = (int)dilated_delta.get(i);
        	map[j]   = (byte)dilated_map.get(i);
        	is_assigned[j] = true;
        }
        
        int number_of_seeds = 0;
        for(i = 0; i < size; i++)
        {
        	if(!is_seed[i])
        		is_assigned[i] = false;
        	else
        		number_of_seeds++;
        }
        
        n = number_of_seeds;
        p = 0;
        while(n < size)
        {
        	n = 0;
            for(i = 0; i < size; i++)	
            {
            	if(!is_assigned[i])
            	{
            		byte location = map[i];
            		x = i % xdim;
            		y = i / xdim;
            		int j = getNeighborIndex(x, y, xdim, (int)location);
            		if(is_assigned[j])
            		{
            			value[i] = value[j] + delta[i];
            			is_assigned[i] = true;
            			n++;
            		}
            	}
            	else
            		n++;
            }
            p++;
        }
        
        
        System.out.println();        
        ArrayList result = new ArrayList();
        
        int sum = 0;
        
        for(i = 0; i < seed_delta.size(); i++)
            sum += Math.abs((int)seed_delta.get(i));
        for(i = 0; i < dilated_delta.size(); i++)
            sum += Math.abs((int)dilated_delta.get(i));
        result.add(sum);
        result.add(seed_delta);
        result.add(seed_map);
        result.add(dilated_delta);
        result.add(dilated_map);
        return result;
    }
    
    
    
    
    public static int[] getValuesFromIdealDeltas2(ArrayList list, int xdim, int ydim, int init_value)
    {
        int size       = xdim * ydim;
        int[] value    = new int[size];
        int x          = xdim / 2;
        int y          = ydim / 2;
        int i          = y * xdim + x;
        
        int[] delta    = new int[size];
        byte[] map     = new byte[size];
        int[] assigned  = new int[size];
        boolean[] is_assigned = new boolean[size];
        boolean[] is_seed     = new boolean[size];
        
        ArrayList seed_delta = (ArrayList)list.get(1);
        ArrayList seed_map   = (ArrayList)list.get(2);
        
        value[i]       = init_value;
        is_assigned[i] = true;
        is_seed[i]     = true;
        assigned[i]++;
        
        int current_value = init_value;
        for(int j = 0; j < seed_map.size(); j++)
        {
            byte location = (byte)seed_map.get(j);
            int  _delta    = (int)seed_delta.get(j);
            i = getNeighborIndex(x, y, xdim, (int)location);
            
            value[i] = current_value + _delta;
            is_assigned[i] = true;
            is_seed[i]     = true;
            assigned[i]++;
           
            x = i % xdim;
            y = i / xdim;
            current_value = value[i];
        }
        
        ArrayList dilated_delta = (ArrayList)list.get(3);
        ArrayList dilated_map   = (ArrayList)list.get(4);
        
        // Populate the raster.
        for(i = 0; i < dilated_delta.size(); i++)
        {
        	int j = 0;
        	while(is_assigned[j])
        		j++;
        	delta[j] = (int)dilated_delta.get(i);
        	map[j]   = (byte)dilated_map.get(i);
        	is_assigned[j] = true;
        }
        
        int number_of_seeds = 0;
        for(i = 0; i < size; i++)
        {
        	if(!is_seed[i])
        		is_assigned[i] = false;
        	else
        		number_of_seeds++;
        }
        
        int n = number_of_seeds;
        int p = 0;
        while(n < size)
        {
        	n = 0;
            for(i = 0; i < size; i++)	
            {
            	if(!is_assigned[i])
            	{
            		byte location = map[i];
            		x = i % xdim;
            		y = i / xdim;
            		int j = getNeighborIndex(x, y, xdim, (int)location);
            		if(is_assigned[j])
            		{
            			value[i] = value[j] + delta[i];
            			is_assigned[i] = true;
            			n++;
            		}
            	}
            	else
            		n++;
            }
            p++;
        }
        
        System.out.println("Values reconstructed after " + p + " iterations.");
        return value;
    }
    
   
    
    public static int[] getChannels(int set_id)
    {
    	int [] channel = new int[3];
    	
    	if(set_id == 0)
    	{
    	    channel[0] = 0;
    	    channel[1] = 1;
    	    channel[2] = 2;
    	}
    	else if(set_id == 1)
    	{
    		channel[0] = 0;
    	    channel[1] = 2;
    	    channel[2] = 4;	
    	}
    	else if(set_id == 2)
    	{
    		channel[0] = 0;
    	    channel[1] = 2;
    	    channel[2] = 3;	
    	}
    	else if(set_id == 3)
    	{
    		channel[0] = 0;
    	    channel[1] = 3;
    	    channel[2] = 4;	
    	}
    	else if(set_id == 4)
    	{
    		channel[0] = 0;
    	    channel[1] = 3;
    	    channel[2] = 5;	
    	}
    	else if(set_id == 5)
    	{
    		channel[0] = 1;
    	    channel[1] = 2;
    	    channel[2] = 3;	
    	}
    	else if(set_id == 6)
    	{
    		channel[0] = 2;
    	    channel[1] = 3;
    	    channel[2] = 4;	
    	}
    	else if(set_id == 7)
    	{
    		channel[0] = 1;
    	    channel[1] = 3;
    	    channel[2] = 4;	
    	}
    	else if(set_id == 8)
    	{
    		channel[0] = 1;
    	    channel[1] = 4;
    	    channel[2] = 5;	
    	}
    	else if(set_id == 9)
    	{
    		channel[0] = 2;
    	    channel[1] = 4;
    	    channel[2] = 5;	
    	}
    	return channel;
    }
}