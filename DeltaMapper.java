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
    
    
    public static ArrayList getAverageDeltasFromValues(int src[], int xdim, int ydim)
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
    
    // The paeth filter usually works better than vertical or horizontal deltas,
    // but still does not do as well as simply choosing the smallest delta 
    // by a very significant amount.
    
    // The problem is choosing the smallest delta requires keeping a state
    // that seems to amount to more than the compressed paeth deltas minus the 
    // compressed ideal deltas.
    // This function also returns the delta sum and prediction accuracy rate.
    public static ArrayList getPaethDeltasFromValues(int src[], int xdim, int ydim)
    {
    	int[] dst          = new int[xdim * ydim];
        int init_value     = src[0];
        int value          = init_value;
        int delta          = 0;
        int sum            = 0;
        int k              = 0;
        int number_of_misses = 0;
      
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
            	    	
            	    	if(delta > horizontal_delta || delta > vertical_delta || delta > diagonal_delta)
            	    		number_of_misses++;
            	    	dst[k++] = delta;
            	    	
            	    	
            	    	sum += Math.abs(delta);
            	    }
                }
        	}
        }
        ArrayList result = new ArrayList();
        result.add(sum);
        result.add(dst);
        result.add(number_of_misses);
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
 
    // Get an ideal delta set and a map of which pixels are used.
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
            		    // Setting the first value to 3 to mark the delta type ideal.
            			dst[k]       = 4;
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
            	    	//We have a set of 4 possible deltas to use.
            	    	int a = src[k] - src[k - 1];
            	    	int b = src[k] - src[k - xdim];
            	    	int c = src[k] - src[k - xdim - 1];
            	    	int d = src[k] - src[k - xdim + 1];
            	    	
            	    	int delta_a = a - previous_delta;
            	    	int delta_b = b - previous_delta;
            	    	int delta_c = c - previous_delta;
            	    	int delta_d = d - previous_delta;
            	    	
            	    	if(Math.abs(delta_a) <= Math.abs(delta_b) && Math.abs(delta_a) <= Math.abs(delta_c) && Math.abs(delta_a) <= Math.abs(delta_d))
            	    	{
            	    		delta          = a;
            	    	    dst[k]         = delta;
            	    	    previous_delta = delta;
            	    	    map[k]         = 0;
            	    	    sum           += Math.abs(delta);
            	    	    k++;
            	    	}
            	    	else if(Math.abs(delta_b) <= Math.abs(delta_c)&& Math.abs(delta_b) <= Math.abs(delta_d))
            	    	{
            	    		delta          = b;
            	    		dst[k]         = delta;
            	    		previous_delta = delta;
            	    	    map[k]         = 1;
            	    	    sum           += Math.abs(delta);
            	    	    k++;
            	    	}
            	    	else if(Math.abs(delta_c) <= Math.abs(delta_d))
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
            	    	
            	    	int delta_a = a - previous_delta;
            	    	int delta_b = b - previous_delta;
            	    	int delta_c = c - previous_delta;
            	    	
            	    	
            	    	if(Math.abs(delta_a) <= Math.abs(delta_b) && Math.abs(delta_a) <= Math.abs(delta_c))
            	    	{
            	    		delta        = a;
            	    	    dst[k]       = delta;
            	    	    map[k] = 0;
            	    	    sum         += Math.abs(delta);
            	    	    k++;
            	    	}
            	    	else if(Math.abs(delta_b)<= Math.abs(delta_c))
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
    
    // This function returns the result from the standard png filters, using
    // the filter that produces the smallest delta sum for each row.
    public static ArrayList getMixedDeltasFromValues(int src[], int xdim, int ydim)
    {
        byte [] map = new byte[ydim - 1];
        
        for(int i = 1; i < ydim; i++)
        {
        	int [] sum = new int[4];
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
    	    	
    	    	int delta_a = Math.abs(a - d);
    	    	int delta_b = Math.abs(b - d);
    	    	int delta_c = Math.abs(c - d);
    	    	int delta   = 0;
    	    	if(delta_a <= delta_b && delta_a <= delta_c)
    	    	    delta = src[k] - src[k - 1];
    	    	else if(delta_b <= delta_c)
    	    	    delta = src[k] - src[k - xdim];
    	    	else
    	    	    delta = src[k] - src[k - xdim - 1];
    	    	sum[3] += Math.abs(delta);
    	    	
    	    	/*
    	    	a = src[k - 1];
    	    	b = src[k - xdim + 1];
    	    	c = src[k - xdim];
    	    	d = a + b - c;
    	    	
    	    	delta_a = Math.abs(a - d);
    	    	delta_b = Math.abs(b - d);
    	    	delta_c = Math.abs(c - d);
    	    	
    	    	if(delta_a <= delta_b && delta_a <= delta_c)
    	    	    delta = src[k] - src[k - 1];
    	    	else if(delta_b <= delta_c)
    	    	    delta = src[k] - src[k - xdim + 1];
    	    	else
    	    		delta = src[k] - src[k - xdim];
    	    	sum[4] += Math.abs(delta);
    	    	*/
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
    	
        /*
        for(int i = 0; i < ydim - 1; i++)
        {
        	System.out.print(map[i] + " ");
        }
        System.out.println();
        */
        
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
            		    // Setting the first value to 4 to mark the delta type mixed.
            			dst[j] = 4;
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
        		int delta = 0;
        		for(int j = 0; j < xdim; j++)
                {
        			int k = i * xdim + j;
            	    if(j == 0)
            	    {
            	    	// Use the vertical delta for the first value.
            	    	delta      = src[k] - init_value;
            	    	init_value = src[k];
            	    	dst[k]     = delta;
            	    }
            	    else if(j < xdim - 1)
            	    {
            	    	if(m == 0)
            	    	{
            	    	    delta = src[k] - src[k - 1];
            	    	    dst[k] = delta;
            	    	    sum += Math.abs(delta);
            	    	}
            	    	else if(m == 1)
            	    	{
            	    		delta = src[k] - src[k - xdim];
            	    	    dst[k] = delta;
            	    	    sum += Math.abs(delta);	
            	    	}
            	    	else if(m == 2)
            	    	{
            	    		int average = (src[k - 1] + src[k - xdim]) / 2;
            	    		delta = src[k] - average;
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
            	    }
            	    else
            	    {
            	    	// Use the horizontal delta, in case the best filter
            	    	// was the modified paeth.
            	    	delta  = src[k] - src[k - 1];
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
  
    public static int[] getValuesFromMixedDeltas(int [] src, int xdim, int ydim, int init_value, byte [] map)
    {
    	int[] dst = new int[xdim * ydim];
        dst[0] = init_value;
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
            		value += src[k];
            		dst[k] = value;
            	}
            	else
            	{
            	    value = dst[k - 1] ;
            	    value += src[k];
            	    dst[k] = value;
            	}
            }
        }
        return dst;
    }
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    // This function returns the result from the paeth filter or a modified paeth filter,
    // using the backward diagonal or the forward diagonal depending on whether
    // the horizontal or vertical delta is less.  The idea is that if the vertical
    // delta is less than the horizontal delta, the forward diagonal is more likely
    // to be less than the backward diagonal.  
    public static ArrayList getMixedDeltasFromValues2(int src[], int xdim, int ydim)
    {
        byte [] map = new byte[ydim - 1];
        
        for(int i = 1; i < ydim; i++)
        {
        	int vertical_sum = 0;
        	int horizontal_sum = 0;
        	
        	for(int j = 1; j < xdim - 1; j++)
        	{
        		int k = i * xdim + j;
        		
    	    	horizontal_sum += src[k] - src[k - 1];
    	    	vertical_sum   += src[k] - src[k - xdim];
        	}
        	
        	if(vertical_sum < horizontal_sum)
        	    map[i - 1] = 1;
        }
    	
        for(int i = 0; i < ydim - 1; i++)
        {
        	System.out.print(map[i] + " ");
        }
        System.out.println();
        
    	int[] dst = new int[xdim * ydim];
      
    	// Setting the inital value to 4 to show mixed deltas.
    	dst[0] = 4;
        
        int init_value = src[0];
        int value      = init_value;
        int ideal_sum  = 0;
        int paeth_sum  = 0;
        
        for(int i = 0; i < ydim; i++)
        {
        	if(i == 0)
        	{
                for(int j = 0; j < xdim; j++)
                {
            	    if(j == 0)
            		    // Setting the first value to 3 to mark the delta type ideal.
            			dst[j] = 3;
            		else
            		{
            			// We don't have any vertical or diagonal deltas to check
            			// in the first row, so we just use horizontal deltas.
            		    int delta    = src[j] - value;
                        value       += delta;
                        dst[j]       = delta;
                        ideal_sum   += Math.abs(delta);
                        paeth_sum   += Math.abs(delta);
            		}
            	}
            }
        	else
        	{
        		int m     = map[i - 1];
        		int delta = 0;
        		for(int j = 0; j < xdim; j++)
                {
        			int k = i * xdim + j;
            	    if(j == 0)
            	    {
            	    	// Keep the code simple and use the vertical delta for the first value.
            	    	// The forward diagonal delta is also a possibility.
            	    	delta      = src[k] - init_value;
            	    	init_value = src[k];
            	    	dst[k]     = delta;
            	    	ideal_sum += Math.abs(delta);
                        paeth_sum += Math.abs(delta);
            	    }
            	    else if(j < xdim - 1)
            	    {
            	    	if(m == 0)
            	    	{
            	    		// Use paeth filter.
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
                	    
                	    	ideal_sum += Math.abs(paeth_delta);
            	    	}
            	    	else
            	    	{
            	    	    // Use modified paeth filter.
            	    		int a = src[k - 1];
            	    		int b = src[k - xdim + 1];
            	    		int c = src[k - xdim];
            	    		int d = a + b - c;
            	    		
            	    		int delta_a = Math.abs(a - d);
                	    	int delta_b = Math.abs(b - d);
                	    	int delta_c = Math.abs(c - d);
            	    	
                	    	int modified_paeth_delta = 0;
                	    	if(delta_a <= delta_b && delta_a <= delta_c)
                	    		modified_paeth_delta = src[k] - src[k - 1];
                	    	else if(delta_b <= delta_c)
                	    		modified_paeth_delta = src[k] - src[k - xdim + 1];
                	    	else
                	    		modified_paeth_delta = src[k] - src[k - xdim];
                	    	
                	    	ideal_sum += Math.abs(modified_paeth_delta);
            	    	}
            	    	
            	    	// Get the paeth delta to keep a running comparison.
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
            	    	paeth_sum += Math.abs(paeth_delta);	
            	    }
            	    else
            	    {
            	    	// Keep the code simple and use the horizontal delta.
            	    	// Could use a paeth filter.
            	    	delta  = src[k] - src[k - 1];
            	    	dst[k] = delta;
            	    	ideal_sum += Math.abs(delta);
            	    	
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
            	    	paeth_sum += Math.abs(paeth_delta);
            	    }
                }
        	}
        }
        
        System.out.println("Paeth sum is " + paeth_sum);
        System.out.println("Mixed sum is " + ideal_sum);
        
        System.out.println();
        ArrayList result = new ArrayList();
        result.add(ideal_sum);
        result.add(dst);
        result.add(map);
        return result;
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