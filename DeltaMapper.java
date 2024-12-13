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
         
        int k    = 0;
        dst[k++] = 2;
        for(int i = 1; i < xdim; i++)
        {
        	int delta   = src[k] - src[k - 1];
        	dst[k++]    = delta;   
        	sum        += Math.abs(delta);
        }
        
        for(int i = 1; i < ydim; i++)
        {
        	int delta   = src[k] - init_value;
        	dst[k++]    = delta;
        	init_value += delta;
        	sum        += Math.abs(delta);
        	
        	for(int j = 1; j < xdim - 1; j++)
        	{
        		int average = (src[k - 1] + src[k - xdim]) / 2;
                delta       = src[k]  - average;
                dst[k++]    = delta;
                sum        += Math.abs(delta);    	
        	}
        	
        	int average = (src[k - 1] + src[k - xdim]) / 2;
            delta       = src[k]  - average;
            dst[k++]    = delta;
            sum        += Math.abs(delta);
        }
        
        ArrayList result = new ArrayList();
        result.add(sum);
        result.add(dst);
        return result;
    }
    
    public static int[] getValuesFromAverageDeltas(int src[], int xdim, int ydim, int init_value)
    {
    	int k = 0;
    	if(src[k] != 2)
			System.out.println("Wrong code.");
    	
    	int[] dst = new int[xdim * ydim];
    	dst[k++] = init_value;
    	
    	for(int i = 1; i < xdim; i++)
    	{
    	    int value = dst[k - 1] + src[k];
    	    dst[k++]  = value;
    	}
    	
    	for(int i = 1; i < ydim; i++)
    	{
    	    init_value += src[k];
    	    dst[k++]    = init_value;
    	    
    	    for(int j = 1; j < xdim - 1; j++)
    	    {
    	    	int average   = (dst[k - 1] + dst[k - xdim]) / 2;
                int value     = average + src[k];
                dst[k++]      = value;	
    	    }
    	    
    	    int average   = (dst[k - 1] + dst[k - xdim]) / 2;
            int value     = average + src[k];
            dst[k++]      = value;
    	}
    	
        return dst;
    }
    
    public static ArrayList getAverageDeltasFromValues2(int src[], int xdim, int ydim)
    {
        int[] dst            = new int[xdim * ydim];
        int   sum            = 0;
        int   init_value     = src[0];
         
        int k    = 0;
        dst[k++] = 2;
        for(int i = 1; i < xdim; i++)
        {
        	int delta   = src[k] - src[k - 1];
        	dst[k++]    = delta;   
        	sum        += Math.abs(delta);
        }
        
        for(int i = 1; i < ydim; i++)
        {
        	int delta   = src[k] - init_value;
        	dst[k++]    = delta;
        	init_value += delta;
        	sum        += Math.abs(delta);
        	
        	for(int j = 1; j < xdim - 1; j++)
        	{
        		int average = (src[k - 1] + src[k - xdim - 1]) / 2;
                delta   = src[k]  - average;
                dst[k++]    = delta;
                sum        += Math.abs(delta);    	
        	}
        	
        	int average = (src[k - 1] + src[k - xdim - 1]) / 2;
            delta       = src[k]  - average;
            dst[k++]    = delta;
            sum        += Math.abs(delta);
        }
        
        ArrayList result = new ArrayList();
        result.add(sum);
        result.add(dst);
        return result;
    }
    
    public static int[] getValuesFromAverageDeltas2(int src[], int xdim, int ydim, int init_value)
    {
    	int k = 0;
    	if(src[k] != 2)
			System.out.println("Wrong code.");
    	
    	int[] dst = new int[xdim * ydim];
    	dst[k++] = init_value;
    	
    	for(int i = 1; i < xdim; i++)
    	{
    	    int value = dst[k - 1] + src[k];
    	    dst[k++]  = value;
    	}
    	
    	for(int i = 1; i < ydim; i++)
    	{
    	    init_value += src[k];
    	    dst[k++]    = init_value;
    	    
    	    for(int j = 1; j < xdim - 1; j++)
    	    {
    	    	int average   = (dst[k - 1] + dst[k - xdim - 1]) / 2;
                int value     = average + src[k];
                dst[k++]      = value;	
    	    }
    	    
    	    int average   = (dst[k - 1] + dst[k - xdim - 1]) / 2;
            int value     = average + src[k];
            dst[k++]      = value;
    	}
    	
        return dst;
    }
    
    
    public static ArrayList getAverageDeltasFromValues3(int src[], int xdim, int ydim)
    {
        int[] dst            = new int[xdim * ydim];
        int   sum            = 0;
        int   init_value     = src[0];
         
        int k    = 0;
        dst[k++] = 2;
        for(int i = 1; i < xdim; i++)
        {
        	int delta   = src[k] - src[k - 1];
        	dst[k++]    = delta;   
        	sum        += Math.abs(delta);
        }
        
        for(int i = 1; i < ydim; i++)
        {
        	int delta   = src[k] - init_value;
        	dst[k++]    = delta;
        	init_value += delta;
        	sum        += Math.abs(delta);
        	
        	for(int j = 1; j < xdim - 1; j++)
        	{
        		int average = (src[k - 1] + src[k - xdim + 1]) / 2;
                delta   = src[k]  - average;
                dst[k++]    = delta;
                sum        += Math.abs(delta);    	
        	}
        	
        	int average = (src[k - 1] + src[k - xdim]) / 2;
            delta       = src[k]  - average;
            dst[k++]    = delta;
            sum        += Math.abs(delta);
        }
        
        ArrayList result = new ArrayList();
        result.add(sum);
        result.add(dst);
        return result;
    }
    
    public static int[] getValuesFromAverageDeltas3(int src[], int xdim, int ydim, int init_value)
    {
    	int k = 0;
    	if(src[k] != 2)
			System.out.println("Wrong code.");
    	
    	int[] dst = new int[xdim * ydim];
    	dst[k++] = init_value;
    	
    	for(int i = 1; i < xdim; i++)
    	{
    	    int value = dst[k - 1] + src[k];
    	    dst[k++]  = value;
    	}
    	
    	for(int i = 1; i < ydim; i++)
    	{
    	    init_value += src[k];
    	    dst[k++]    = init_value;
    	    
    	    for(int j = 1; j < xdim - 1; j++)
    	    {
    	    	int average   = (dst[k - 1] + dst[k - xdim + 1]) / 2;
                int value     = average + src[k];
                dst[k++]      = value;	
    	    }
    	    
    	    int average   = (dst[k - 1] + dst[k - xdim]) / 2;
            int value     = average + src[k];
            dst[k++]      = value;
    	}
    	
        return dst;
    }
    
    public static ArrayList getAverageDeltasFromValues4(int src[], int xdim, int ydim)
    {
        int[] dst            = new int[xdim * ydim];
        int   sum            = 0;
        int   init_value     = src[0];
         
        int k    = 0;
        dst[k++] = 2;
        for(int i = 1; i < xdim; i++)
        {
        	int delta   = src[k] - src[k - 1];
        	dst[k++]    = delta;   
        	sum        += Math.abs(delta);
        }
        
        for(int i = 1; i < ydim; i++)
        {
        	int delta   = src[k] - init_value;
        	dst[k++]    = delta;
        	init_value += delta;
        	sum        += Math.abs(delta);
        	
        	for(int j = 1; j < xdim - 1; j++)
        	{
        		int average = (src[k - 1] + src[k - xdim] + src[k - xdim - 1] + src[k - xdim + 1]) / 4;
                delta   = src[k]  - average;
                dst[k++]    = delta;
                sum        += Math.abs(delta);    	
        	}
        	
        	int average = (src[k - 1] + src[k - xdim] + src[k - xdim - 1]) / 3;
            delta       = src[k]  - average;
            dst[k++]    = delta;
            sum        += Math.abs(delta);
        }
        
        ArrayList result = new ArrayList();
        result.add(sum);
        result.add(dst);
        return result;
    }
    
    public static int[] getValuesFromAverageDeltas4(int src[], int xdim, int ydim, int init_value)
    {
    	int k = 0;
    	if(src[k] != 2)
			System.out.println("Wrong code.");
    	
    	int[] dst = new int[xdim * ydim];
    	dst[k++] = init_value;
    	
    	for(int i = 1; i < xdim; i++)
    	{
    	    int value = dst[k - 1] + src[k];
    	    dst[k++]  = value;
    	}
    	
    	for(int i = 1; i < ydim; i++)
    	{
    	    init_value += src[k];
    	    dst[k++]    = init_value;
    	    
    	    for(int j = 1; j < xdim - 1; j++)
    	    {
    	    	int average   = (dst[k - 1] + dst[k - xdim] + dst[k - xdim - 1] + dst[k - xdim + 1]) / 4;
                int value     = average + src[k];
                dst[k++]      = value;	
    	    }
    	    
    	    int average   = (dst[k - 1] + dst[k - xdim] + dst[k - xdim - 1]) / 3;
            int value     = average + src[k];
            dst[k++]      = value;
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
    
    public static ArrayList getGradientDeltasFromValues(int src[], int xdim, int ydim)
    {
    	int[] dst      = new int[xdim * ydim];
        int init_value = src[0];
        
        int sum   = 0;
        int delta = 0;
        int k     = 0;
        
        // Process the first row.
        dst[k++] = 4;
        for(int i = 1; i < xdim; i++)
        {
        	delta   = src[k] - src[k - 1];
        	dst[k++]    = delta;   
        	sum        += Math.abs(delta);
        }
        
        // This gradient filter requires two preceding rows,
        // so we process another row.
        // To keep things simple we'll use the horizontal delta,
        // although there is probably another delta type that
        // would produce more compression.
        // Could do a check on the delta sum for the second row.
        
        // Process the leading pixel with the vertical delta.
        delta   = src[k] - init_value;
    	dst[k++]    = delta;
    	init_value += delta;
    	sum        += Math.abs(delta);
    	
    	// Get horizontal deltas for the second row.
    	for(int i = 1; i < xdim; i++)
        {
        	delta   = src[k] - src[k - 1];
        	dst[k++]    = delta;   
        	sum        += Math.abs(delta);
        }
    	
        // Process the other rows using the gradient filter.
        for(int i = 2; i < ydim; i++)
        {
        	
        	delta   = src[k] - init_value;
        	dst[k++]    = delta;
        	init_value += delta;
        	sum        += Math.abs(delta);
        	
        	// It requires a trailing pixel,
        	// so we stop before the end pixel.
        	for(int j = 1; j < xdim - 1; j++)
        	{
        		int a = src[k - 1];
    	    	int b = src[k - xdim];
    	    	int c = src[k - xdim - 1];
    	    	int d = src[k - xdim + 1];
    	    	int e = src[k - 2 * xdim - 1];
    	    	
    	    	int [] gradient = new int[4];
    	    	gradient[0] = Math.abs(a - e);
    	    	gradient[1] = Math.abs(c - d);
    	    	gradient[2] = Math.abs(a - d);
    	    	gradient[3] = Math.abs(b - e);
    	    	
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
    	    	dst[k++] = delta;
    	    	sum += Math.abs(delta); 	
        	}
        	
        	// Add the end pixel delta.
            delta       = src[k]  - src[k - 1];
            dst[k++]    = delta;
            sum        += Math.abs(delta);
        }
        
        ArrayList result = new ArrayList();
        result.add(sum);
        result.add(dst);
        return result;    
    }

    public static int[] getValuesFromGradientDeltas(int src[], int xdim, int ydim, int init_value)
    {
    	int[] dst       = new int[xdim * ydim];
    	int [] gradient = new int[4];
        if(src[0] != 4)
        	System.out.println("Wrong code.");
        
        int k = 0;
        dst[k++] = init_value;
        
        // Get the first two rows with horizontal deltas.
        for(int i = 1; i < xdim; i++)
        {
        	dst[k] = dst[k - 1] + src[k];
        	k++;
        }
        
        init_value += src[k];
    	dst[k++]    = init_value;
        
    	for(int i = 1; i < xdim; i++)
        {
        	dst[k] = dst[k - 1] + src[k];
        	k++;
        }
    	
        for(int i = 2; i < ydim; i++)
        {
        	init_value += src[k];
        	dst[k++]    = init_value;
        	
            for(int j = 1; j < xdim - 1; j++)	
            {
            	int a = dst[k - 1];
        	    int b = dst[k - xdim];
        	    int c = dst[k - xdim - 1];
        	    int d = dst[k - xdim + 1];
        	    int e = dst[k - 2 * xdim - 1];
        	  
    	    	gradient[0] = Math.abs(a - e);
    	    	gradient[1] = Math.abs(c - d);
    	    	gradient[2] = Math.abs(a - d);
    	    	gradient[3] = Math.abs(b - e);
        	    
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
        	    	dst[k] = dst[k - 1] + src[k];
        	    else if(max_index == 1)
        	    	dst[k] = dst[k - xdim] + src[k];
        	    else if(max_index == 2)
        	    	dst[k] = dst[k - xdim - 1] + src[k];
        	    else
        	    	dst[k] = dst[k - xdim + 1] + src[k];  
        	    k++;
            }
            
            // Get trailing pixel with horizontal delta.
            dst[k] = dst[k - 1] + src[k];
            k++;
        }
        return dst;
    }
 
    public static ArrayList getGradientDeltasFromValues2(int src[], int xdim, int ydim)
    {
    	int[] dst          = new int[xdim * ydim];
        int init_value     = src[0];
        
        int sum  = 0;
        int k    = 0;
        dst[k++] = 4;
        for(int i = 1; i < xdim; i++)
        {
        	int delta   = src[k] - src[k - 1];
        	dst[k++]    = delta;   
        	sum        += Math.abs(delta);
        }
        
        for(int i = 1; i < ydim; i++)
        {
        	int delta   = src[k] - init_value;
        	dst[k++]    = delta;
        	init_value += delta;
        	sum        += Math.abs(delta);
        	
        	// This gradient filter requires two preceding
        	// pixels so we use the horizontal delta.
        	delta       = src[k] - src[k - 1];
        	dst[k++]    = delta;
        	sum        += Math.abs(delta);
        	
        	// It also requires a trailing pixel,
        	// so we stop before the end pixel.
        	for(int j = 2; j < xdim - 1; j++)
        	{
        		int a = src[k - 1];
    	    	int b = src[k - xdim];
    	    	int c = src[k - xdim - 1];
    	    	int d = src[k - xdim + 1];
    	    	int e = src[k - xdim - 2];
    	    	
    	    	int [] gradient = new int[4];
    	    	gradient[0] = Math.abs(c - b);
    	    	gradient[1] = Math.abs(c - a);
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
    	    	dst[k++] = delta;
    	    	sum += Math.abs(delta); 	
        	}
        	
        	// Add the end pixel delta.
            delta       = src[k]  - src[k - 1];
            dst[k++]    = delta;
            sum        += Math.abs(delta);
        }
        
        ArrayList result = new ArrayList();
        result.add(sum);
        result.add(dst);
        return result;    
    }

    public static int[] getValuesFromGradientDeltas2(int src[], int xdim, int ydim, int init_value)
    {
    	int[] dst       = new int[xdim * ydim];
    	int [] gradient = new int[4];
        if(src[0] != 4)
        	System.out.println("Wrong code.");
        
        int k = 0;
        dst[k++] = init_value;
        
        for(int i = 1; i < xdim; i++)
        {
        	dst[k] = dst[k - 1] + src[k];
        	k++;
        }
        
        for(int i = 1; i < ydim; i++)
        {
        	// Use vertical and horizontal deltas for first two pixels.
        	init_value += src[k];
        	dst[k++]    = init_value;
        	dst[k]      = dst[k - 1] + src[k];
        	k++;
        	
            for(int j = 2; j < xdim - 1; j++)	
            {
            	int a = dst[k - 1];
        	    int b = dst[k - xdim];
        	    int c = dst[k - xdim - 1];
        	    int d = dst[k - xdim + 1];
        	    int e = dst[k - xdim - 2];
        	    	
        	    gradient[0] = Math.abs(c - b);
        	    gradient[1] = Math.abs(c - a);
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
        	    	dst[k] = dst[k - 1] + src[k];
        	    else if(max_index == 1)
        	    	dst[k] = dst[k - xdim] + src[k];
        	    else if(max_index == 2)
        	    	dst[k] = dst[k - xdim - 1] + src[k];
        	    else
        	    	dst[k] = dst[k - xdim + 1] + src[k];  
        	    k++;
            }
            dst[k] = dst[k - 1] + src[k];
            k++;
        }
        return dst;
    }
 
    public static ArrayList getMixedDeltasFromValues(int src[], int xdim, int ydim)
    {
        byte [] map = new byte[ydim - 1];
        
        for(int i = 1; i < ydim; i++)
        {
        	int [] sum   = new int[4];
        	
        	int k = i * xdim + 1;
        	for(int j = 1; j < xdim - 1; j++)
        	{
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
    	    	k++;
        	}
        	
        	int value = sum[0];
        	int index = 0;
        	for(k = 1; k < 4; k++)
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
        
        int sum  = 0;
        int k    = 0;
        dst[k++] = 5;
        int delta = src[k] - init_value;
        int value = src[k];
        dst[k++]  = delta;
        sum      += Math.abs(delta);
        
        for(int i = 2; i < xdim; i++)
        {
        	delta    = src[k] - value;	
        	value    = src[k];
        	dst[k++] = delta;
        	sum += Math.abs(delta);
        }
        
        for(int i = 1; i < ydim; i++)
        {
        	delta      = src[k] - init_value;
	    	init_value = src[k];
	    	dst[k++]   = delta;	
	    	sum += Math.abs(delta);
	    	
	    	byte m = map[i - 1];
	    	
	    	if(m == 0)
	    	{
	    	    for(int j = 1; j < xdim - 1; j++)
	    	    {
	    	    	delta    = src[k] - src[k - 1];	
	            	dst[k++] = delta;	
	    	    }
	    	    delta    = src[k] - src[k - 1];	
            	dst[k++] = delta;
            	sum += Math.abs(delta);
	    	}
	    	else if(m == 1)
	    	{
	    		for(int j = 1; j < xdim - 1; j++)
	    	    {
	    	    	delta    = src[k] - src[k - xdim];	
	            	dst[k++] = delta;	
	    	    }
	    	    delta    = src[k] - src[k - 1];	
            	dst[k++] = delta;
            	sum += Math.abs(delta);
	    	}
	    	else if(m == 2)
	    	{
	    		for(int j = 1; j < xdim - 1; j++)
	    	    {
	    	    	delta    = src[k] - (src[k - 1] + src[k - xdim])/2;	
	            	dst[k++] = delta;	
	    	    }
	    	    delta    = src[k] - src[k - 1];	
            	dst[k++] = delta;
            	sum += Math.abs(delta);
	    	}
	    	else if(m == 3)
	    	{
	    		for(int j = 1; j < xdim - 1; j++)
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
        	    	dst[k++] = delta;
        	    	sum += Math.abs(delta);		
	    	    }
	    	    delta    = src[k] - src[k - 1];	
            	dst[k++] = delta;	
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
        if(src[0] != 5)
        	System.out.println("Wrong code.");
        
        int[] dst = new int[xdim * ydim];
        int k     = 0;
        dst[k++]  = init_value;
        int value = init_value;
        
        for(int i = 1; i < xdim; i++)
        {
        	value   += src[k];
        	dst[k++] = value;
        }
        
        for(int i = 1; i < ydim; i++)
        {
        	init_value += src[k];
    	    dst[k++]    = init_value;
    	    
    	    int m       = map[i - 1];
    	    if(m == 0)
    	    {
    	        for(int j = 1; j < xdim - 1; j++)
    	        {
    	        	value    = dst[k - 1] + src[k];
    	        	dst[k++] = value;
    	        }
    	    }
    	    else if(m == 1)
    	    {
    	    	for(int j = 1; j < xdim - 1; j++)
    	        {
    	        	value    = dst[k - xdim] + src[k];
    	        	dst[k++] = value;
    	        }	
    	    }
    	    else if(m == 2)
    	    {
    	    	for(int j = 1; j < xdim - 1; j++)
    	        {
    	        	value    = (dst[k - xdim] + dst[k - 1]) / 2 + src[k];
    	        	dst[k++] = value;
    	        }		
    	    }
    	    else if(m == 3)
    	    {
    	    	for(int j = 1; j < xdim - 1; j++)
    	        {
    	    		int a = dst[k - 1];
        	    	int b = dst[k - xdim];
        	    	int c = dst[k - xdim - 1];
        	    	int d = a + b - c;
    	    		
        	    	int delta_a = Math.abs(a - d);
        	    	int delta_b = Math.abs(b - d);
        	    	int delta_c = Math.abs(c - d);
    	    		
        	    	if(delta_a <= delta_b && delta_a <= delta_c)
        	    	    value = dst[k - 1];
        	    	else if(delta_b <= delta_c)
        	    	    value = dst[k - xdim];
        	    	else
        	    	    value = dst[k - xdim - 1];
        	    	value   += src[k];
        	    	dst[k++] = value;
    	        }
    	    }
    	    value = dst[k - 1] + src[k];
    	    dst[k++] = value;
        }
        
        return dst;
    }
    
    // These next two functions have a bug.
    public static ArrayList getMixedDeltasFromValues2(int src[], int xdim, int ydim)
    {
        byte [] map = new byte[ydim - 1];
        
        for(int i = 1; i < ydim; i++)
        {
        	int [] sum   = new int[4];
        	
        	int k = i * xdim + 1;
        	for(int j = 1; j < xdim - 1; j++)
        	{
    	    	sum[0] += Math.abs(src[k] - src[k - 1]);
    	    	sum[1] += Math.abs(src[k] - src[k - xdim]);
    	    	sum[2] += Math.abs(src[k] - (src[k - 1] + src[k - xdim]) / 2);
    	    	
    	    	if(i > 1)
    	    	{
    	    		/*
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
        	    	*/
    	    		
    	    		int a = src[k - 1];
            	    int b = src[k - xdim];
            	    int c = src[k - xdim - 1];
            	    int d = src[k - xdim + 1];
            	    int e = src[k - 2 * xdim - 1];
            	  
            	    int [] gradient = new int[4];
        	    	gradient[0] = Math.abs(a - e);
        	    	gradient[1] = Math.abs(c - d);
        	    	gradient[2] = Math.abs(a - d);
        	    	gradient[3] = Math.abs(b - e);
            	    
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
        	    	sum[3] += Math.abs(delta);
    	    	}
    	    	k++;
        	}
        	
        	int limit = 4;
        	if(i == 1)
        		limit--;
        	int value = sum[0];
        	int index = 0;
        	for(k = 1; k < limit; k++)
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
    	int[] dst      = new int[xdim * ydim];
        int init_value = src[0];
        
        int sum  = 0;
        int k    = 0;
        dst[k++] = 5;
        int delta = src[k] - init_value;
        int value = src[k];
        dst[k++]  = delta;
        sum      += Math.abs(delta);
        
        for(int i = 2; i < xdim; i++)
        {
        	delta    = src[k] - value;	
        	value    = src[k];
        	dst[k++] = delta;
        	sum += Math.abs(delta);
        }
        
        for(int i = 1; i < ydim; i++)
        {
        	delta      = src[k] - init_value;
	    	init_value = src[k];
	    	dst[k++]   = delta;	
	    	sum += Math.abs(delta);
	    	
	    	byte m = map[i - 1];
	    	
	    	if(m == 0)
	    	{
	    	    for(int j = 1; j < xdim - 1; j++)
	    	    {
	    	    	delta    = src[k] - src[k - 1];	
	            	dst[k++] = delta;	
	    	    }
	    	    delta    = src[k] - src[k - 1];	
            	dst[k++] = delta;
            	sum += Math.abs(delta);
	    	}
	    	else if(m == 1)
	    	{
	    		for(int j = 1; j < xdim - 1; j++)
	    	    {
	    	    	delta    = src[k] - src[k - xdim];	
	            	dst[k++] = delta;	
	    	    }
	    	    delta    = src[k] - src[k - 1];	
            	dst[k++] = delta;
            	sum += Math.abs(delta);
	    	}
	    	else if(m == 2)
	    	{
	    		for(int j = 1; j < xdim - 1; j++)
	    	    {
	    	    	delta    = src[k] - (src[k - 1] + src[k - xdim])/2;	
	            	dst[k++] = delta;	
	    	    }
	    	    delta    = src[k] - src[k - 1];	
            	dst[k++] = delta;
            	sum += Math.abs(delta);
	    	}
	    	else if(m == 3)
	    	{
	    		for(int j = 1; j < xdim - 1; j++)
	    	    {
	    			/*
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
        	    	*/
	    			int a = src[k - 1];
            	    int b = src[k - xdim];
            	    int c = src[k - xdim - 1];
            	    int d = src[k - xdim + 1];
            	    int e = src[k - 2 * xdim - 1];
            	  
            	    int [] gradient = new int[4];
        	    	gradient[0] = Math.abs(a - e);
        	    	gradient[1] = Math.abs(c - d);
        	    	gradient[2] = Math.abs(a - d);
        	    	gradient[3] = Math.abs(b - e);
            	    
            	    int max_value = gradient[0];
            	    int max_index = 0;
            	    for(int n = 1; n < 4; n++)
            	    {
            	    	if(gradient[n] > max_value)
            	    	{
            	    		max_value = gradient[n];
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
	    			
        	    	dst[k++] = delta;
        	    	sum += Math.abs(delta);		
	    	    }
	    	    delta    = src[k] - src[k - 1];	
            	dst[k++] = delta;	
	    	}
	    	
        }
        ArrayList result = new ArrayList();
        result.add(sum);
        result.add(dst);
        result.add(map);
        return result;
    }
   
    public static int[] getValuesFromMixedDeltas2(int [] src, int xdim, int ydim, int init_value, byte [] map)
    {
    
        if(src[0] != 5)
        	System.out.println("Wrong code.");
        
        int[] dst = new int[xdim * ydim];
        int k     = 0;
        dst[k++]  = init_value;
        int value = init_value;
        
        for(int i = 1; i < xdim; i++)
        {
        	value   += src[k];
        	dst[k++] = value;
        }
        
        for(int i = 1; i < ydim; i++)
        {
        	init_value += src[k];
    	    dst[k++]    = init_value;
    	    
    	    int m       = map[i - 1];
    	    if(m == 0)
    	    {
    	        for(int j = 1; j < xdim - 1; j++)
    	        {
    	        	value    = dst[k - 1] + src[k];
    	        	dst[k++] = value;
    	        }
    	    }
    	    else if(m == 1)
    	    {
    	    	for(int j = 1; j < xdim - 1; j++)
    	        {
    	        	value    = dst[k - xdim] + src[k];
    	        	dst[k++] = value;
    	        }	
    	    }
    	    else if(m == 2)
    	    {
    	    	for(int j = 1; j < xdim - 1; j++)
    	        {
    	        	value    = (dst[k - xdim] + dst[k - 1]) / 2 + src[k];
    	        	dst[k++] = value;
    	        }		
    	    }
    	    else if(m == 3)
    	    {
    	    	for(int j = 1; j < xdim - 1; j++)
    	        {
    	    		/*
    	    		int a = dst[k - 1];
        	    	int b = dst[k - xdim];
        	    	int c = dst[k - xdim - 1];
        	    	int d = a + b - c;
    	    		
        	    	int delta_a = Math.abs(a - d);
        	    	int delta_b = Math.abs(b - d);
        	    	int delta_c = Math.abs(c - d);
    	    		
        	    	if(delta_a <= delta_b && delta_a <= delta_c)
        	    	    value = dst[k - 1];
        	    	else if(delta_b <= delta_c)
        	    	    value = dst[k - xdim];
        	    	else
        	    	    value = dst[k - xdim - 1];
        	    	*/
    	    		int a = dst[k - 1];
            	    int b = dst[k - xdim];
            	    int c = dst[k - xdim - 1];
            	    int d = dst[k - xdim + 1];
            	    int e = dst[k - 2 * xdim - 1];
            	  
            	    int [] gradient = new int[4];
        	    	gradient[0] = Math.abs(a - e);
        	    	gradient[1] = Math.abs(c - d);
        	    	gradient[2] = Math.abs(a - d);
        	    	gradient[3] = Math.abs(b - e);
            	    
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
    	    		
        	    	value   += src[k];
        	    	dst[k++] = value;
    	        }
    	    }
    	    value = dst[k - 1] + src[k];
    	    dst[k++] = value;
        }
        
        return dst;
    }
    
  
    
    public static ArrayList getMixedDeltasFromValues3(int src[], int xdim, int ydim)
    {
        byte [] map = new byte[ydim - 1];
       
        for(int i = 1; i < ydim; i++)
        {
        	int [] sum   = new int[4];
        	
        	for(int j = 1; j < xdim - 1; j++)
        	{
        		int k = i * xdim + j;
        		
    	    	sum[0] += Math.abs(src[k] - (src[k - 1] + src[k - xdim]) / 2);
    	    	sum[1] += Math.abs(src[k] - (src[k - 1] + src[k - xdim - 1]) / 2);
    	    	sum[2] += Math.abs(src[k] - (src[k - 1] + src[k - xdim + 1]) / 2);
    	    	sum[3] += Math.abs(src[k] - (src[k - 1] + src[k - xdim - 1]  + src[k - xdim] + src[k - xdim + 1]) / 4);
        	}
        	
        	int value = sum[0];
        	int index = 0;
        	for(int k = 1; k < 4; k++)
        	{
        		if(sum[k] < value)
        		{
        			value = sum[k];
        			index = k;
        		}
        	}
        	map[i - 1] = (byte)index;
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
            			dst[j] = 5;
            		else
            		{
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
        			for(int j = 1; j < xdim - 1; j++)
        			{
        				delta = src[k] - (src[k - 1] + src[k - xdim]) / 2;
        	    	    dst[k++] = delta;
        	    	    sum += Math.abs(delta);	
        			}	
        			delta = src[k] - src[k - 1];
        			dst[k++] = delta;
    	    	    sum += Math.abs(delta);
        		}
        		else if(m == 1)
        		{
        			for(int j = 1; j < xdim - 1; j++)
        			{
        				delta = src[k] - (src[k - 1] + src[k - xdim - 1]) / 2;
        	    	    dst[k++] = delta;
        	    	    sum += Math.abs(delta);	
        			}	
        			delta = src[k] - src[k - 1];
        			dst[k++] = delta;
    	    	    sum += Math.abs(delta);
        		}
        		else if(m == 2)
        		{
        			for(int j = 1; j < xdim - 1; j++)
        			{
        				delta  = src[k] - (src[k - 1] + src[k - xdim + 1]) / 2;
        	    	    dst[k++] = delta;
        	    	    sum += Math.abs(delta);	
        			}	
        			delta = src[k] - src[k - 1];
        			dst[k++] = delta;
    	    	    sum += Math.abs(delta);
        		}
        		else if(m == 3)
        		{
        			for(int j = 1; j < xdim - 1; j++)
        			{
        				delta  = src[k] - (src[k - 1] + src[k - xdim - 1] + src[k - xdim] + src[k - xdim + 1]) / 4;
        	    	    dst[k++] = delta;
        	    	    sum += Math.abs(delta);	
        			}	
        			delta = src[k] - src[k - 1];
        			dst[k++] = delta;
    	    	    sum += Math.abs(delta);
        		}
        	}
        }
       
        ArrayList result = new ArrayList();
        result.add(sum);
        result.add(dst);
        result.add(map);
        return result;
    }
   
    public static int[] getValuesFromMixedDeltas3(int [] src, int xdim, int ydim, int init_value, byte [] map)
    {
    	int[] dst = new int[xdim * ydim];
        dst[0]    = init_value;
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
            		    value = (dst[k - 1] + dst[k - xdim]) / 2;
            		else if(m == 1)
            			value = (dst[k - 1] + dst[k - xdim - 1]) / 2;
            		else if(m == 2)
            			value = (dst[k - 1] + dst[k - xdim + 1]) / 2;
            		else if(m == 3)
            			value = (dst[k - 1] + dst[k - xdim - 1] + dst[k - xdim] + dst[k - xdim + 1]) / 4;
            		
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
    
    public static ArrayList getMixedDeltasFromValues4(int src[], int xdim, int ydim)
    {
        byte [] map = new byte[ydim - 1];
       
        for(int i = 1; i < ydim; i++)
        {
        	int [] sum   = new int[4];
        	
        	for(int j = 1; j < xdim - 1; j++)
        	{
        		int k = i * xdim + j;
        		
        		/*
    	    	sum[0] += Math.abs(src[k] - src[k - 1]);
    	    	sum[1] += Math.abs(src[k] - src[k - xdim - 1]);
    	    	sum[2] += Math.abs(src[k] - src[k - xdim]);
    	    	sum[3] += Math.abs(src[k] - src[k - xdim + 1]);
    	    	sum[4] += Math.abs(src[k] - (src[k - 1] + src[k - xdim - 1])/2);
    	    	sum[5] += Math.abs(src[k] - (src[k - 1] + src[k - xdim])/2);
    	    	sum[6] += Math.abs(src[k] - (src[k - 1] + src[k - xdim + 1])/2);
    	    	sum[7] += Math.abs(src[k] - (src[k - xdim - 1] + src[k - xdim])/2);
    	    	sum[8] += Math.abs(src[k] - (src[k - xdim - 1] + src[k - xdim + 1])/2);
    	    	sum[9] += Math.abs(src[k] - (src[k - xdim] + src[k - xdim + 1])/2);
    	    	*/
        		sum[0] += Math.abs(src[k] - src[k - 1]);
        		sum[1] += Math.abs(src[k] - src[k - xdim]);
        		sum[2] += Math.abs(src[k] - (src[k - 1] + src[k - xdim])/2);
        		sum[3] += Math.abs(src[k] - (src[k - 1] + src[k - xdim + 1])/2);
        	}
        	
        	int value = sum[0];
        	int index = 0;
        	for(int k = 1; k < 4; k++)
        	{
        		if(sum[k] < value)
        		{
        			value = sum[k];
        			index = k;
        		}
        	}
        	map[i - 1] = (byte)index;
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
            			dst[j] = 5;
            		else
            		{
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
        		
        		for(int j = 1; j < xdim - 1; j++)
    			{
        			/*
        			if(m == 0)
    				    delta = src[k] - src[k - 1];
        			else if(m == 1)
        				delta = src[k] - src[k - xdim - 1];
        			else if(m == 2)
        				delta = src[k] - src[k - xdim];
        			else if(m == 3)
        				delta = src[k] - src[k - xdim + 1];
        			else if(m == 4)
        				delta = src[k] - (src[k - 1] + src[k - xdim - 1]) / 2;
        			else if(m == 5)
        				delta = src[k] - (src[k - 1] + src[k - xdim]) / 2;
        			else if(m == 6)
        				delta = src[k] - (src[k - 1] + src[k - xdim + 1]) / 2;
        			else if(m == 7)
        				delta = src[k] - (src[k - xdim - 1] + src[k - xdim]) / 2;
        			else if(m == 8)
        				delta = src[k] - (src[k - xdim - 1] + src[k - xdim + 1]) / 2;
        			else if(m == 9)
        				delta = src[k] - (src[k - xdim] + src[k - xdim + 1]) / 2;
        			*/
        			if(m == 0)
    				    delta = src[k] - src[k - 1];
        			else if(m == 1)
        				delta = src[k] - src[k - xdim];
        			else if(m == 2)
        				delta = src[k] - (src[k - 1] + src[k - xdim]) / 2;
        			else if(m == 3)
        				delta = src[k] - (src[k - 1] + src[k - xdim + 1]) / 2;
        			
    				dst[k++] = delta;
    	    	    sum += Math.abs(delta);	
    			}
        		
        		delta = src[k] - src[k - 1];
    			dst[k++] = delta;
	    	    sum += Math.abs(delta);
        	}
        }
       
        ArrayList result = new ArrayList();
        result.add(sum);
        result.add(dst);
        result.add(map);
        return result;
    }
   
    public static int[] getValuesFromMixedDeltas4(int [] src, int xdim, int ydim, int init_value, byte [] map)
    {
    	int[] dst = new int[xdim * ydim];
        dst[0]    = init_value;
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
        	System.out.println("m = " + m);
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
            		/*
            		if(m == 0)
            		    value = dst[k - 1];
            		else if(m == 1)
            			value = dst[k - xdim - 1];
            		else if(m == 2)
            			value = dst[k - xdim];
            		else if(m == 3)
            			value = dst[k - xdim + 1];
            		else if(m == 4)
            			value = (dst[k - 1] + dst[k - xdim - 1]) / 2;
            		else if(m == 5)
            			value = (dst[k - 1] + dst[k - xdim]) / 2;
            		else if(m == 6)
            			value = (dst[k - 1] + dst[k - xdim + 1]) / 2;
            		else if(m == 7)
            			value = (dst[k - xdim - 1] + dst[k - xdim]) / 2;
            		else if(m == 8)
            			value = (dst[k - xdim - 1] + dst[k - xdim + 1]) / 2;
            		else if(m == 9)
            			value = (dst[k - xdim] + dst[k - xdim + 1]) / 2;
            		*/
            		if(m == 0)
            		    value = dst[k - 1];
            		else if(m == 1)
            			value = dst[k - xdim];
            		else if(m == 2)
            			value = (dst[k - 1] + dst[k - xdim]) / 2;
            		else if(m == 3)
            			value = (dst[k - 1] + dst[k - xdim + 1]) / 2;
            		
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
    
    public static ArrayList getMixedDeltasFromValues5(int src[], int xdim, int ydim)
    {
        byte [] map = new byte[ydim - 1];
       
        for(int i = 1; i < ydim; i++)
        {
        	int [] sum   = new int[5];
        	
        	for(int j = 1; j < xdim - 1; j++)
        	{
        		int k = i * xdim + j;
        		
        		/*
    	    	sum[0] += Math.abs(src[k] - src[k - 1]);
    	    	sum[1] += Math.abs(src[k] - src[k - xdim - 1]);
    	    	sum[2] += Math.abs(src[k] - src[k - xdim]);
    	    	sum[3] += Math.abs(src[k] - src[k - xdim + 1]);
    	    	sum[4] += Math.abs(src[k] - (src[k - 1] + src[k - xdim - 1])/2);
    	    	sum[5] += Math.abs(src[k] - (src[k - 1] + src[k - xdim])/2);
    	    	sum[6] += Math.abs(src[k] - (src[k - 1] + src[k - xdim + 1])/2);
    	    	sum[7] += Math.abs(src[k] - (src[k - xdim - 1] + src[k - xdim])/2);
    	    	sum[8] += Math.abs(src[k] - (src[k - xdim - 1] + src[k - xdim + 1])/2);
    	    	sum[9] += Math.abs(src[k] - (src[k - xdim] + src[k - xdim + 1])/2);
    	    	*/
        		sum[0] += Math.abs(src[k] - src[k - 1]);
        		sum[1] += Math.abs(src[k] - src[k - xdim]);
        		sum[2] += Math.abs(src[k] - src[k - xdim - 1]);
        		sum[3] += Math.abs(src[k] - (src[k - 1] + src[k - xdim])/2);
        		sum[4] += Math.abs(src[k] - (src[k - 1] + src[k - xdim + 1])/2);
        	}
        	
        	int value = sum[0];
        	int index = 0;
        	for(int k = 1; k < 4; k++)
        	{
        		if(sum[k] < value)
        		{
        			value = sum[k];
        			index = k;
        		}
        	}
        	map[i - 1] = (byte)index;
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
            			dst[j] = 5;
            		else
            		{
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
        		
        		for(int j = 1; j < xdim - 1; j++)
    			{
        			/*
        			if(m == 0)
    				    delta = src[k] - src[k - 1];
        			else if(m == 1)
        				delta = src[k] - src[k - xdim - 1];
        			else if(m == 2)
        				delta = src[k] - src[k - xdim];
        			else if(m == 3)
        				delta = src[k] - src[k - xdim + 1];
        			else if(m == 4)
        				delta = src[k] - (src[k - 1] + src[k - xdim - 1]) / 2;
        			else if(m == 5)
        				delta = src[k] - (src[k - 1] + src[k - xdim]) / 2;
        			else if(m == 6)
        				delta = src[k] - (src[k - 1] + src[k - xdim + 1]) / 2;
        			else if(m == 7)
        				delta = src[k] - (src[k - xdim - 1] + src[k - xdim]) / 2;
        			else if(m == 8)
        				delta = src[k] - (src[k - xdim - 1] + src[k - xdim + 1]) / 2;
        			else if(m == 9)
        				delta = src[k] - (src[k - xdim] + src[k - xdim + 1]) / 2;
        			*/
        			if(m == 0)
    				    delta = src[k] - src[k - 1];
        			else if(m == 1)
        				delta = src[k] - src[k - xdim];
        			else if(m == 2)
        				delta = src[k] - src[k - xdim - 1];
        			else if(m == 3)
        				delta = src[k] - (src[k - 1] + src[k - xdim]) / 2;
        			else if(m == 4)
        				delta = src[k] - (src[k - 1] + src[k - xdim + 1]) / 2;
        			
    				dst[k++] = delta;
    	    	    sum += Math.abs(delta);	
    			}
        		
        		delta = src[k] - src[k - 1];
    			dst[k++] = delta;
	    	    sum += Math.abs(delta);
        	}
        }
       
        ArrayList result = new ArrayList();
        result.add(sum);
        result.add(dst);
        result.add(map);
        return result;
    }
   
    public static int[] getValuesFromMixedDeltas5(int [] src, int xdim, int ydim, int init_value, byte [] map)
    {
    	int[] dst = new int[xdim * ydim];
        dst[0]    = init_value;
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
        	System.out.println("m = " + m);
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
            		/*
            		if(m == 0)
            		    value = dst[k - 1];
            		else if(m == 1)
            			value = dst[k - xdim - 1];
            		else if(m == 2)
            			value = dst[k - xdim];
            		else if(m == 3)
            			value = dst[k - xdim + 1];
            		else if(m == 4)
            			value = (dst[k - 1] + dst[k - xdim - 1]) / 2;
            		else if(m == 5)
            			value = (dst[k - 1] + dst[k - xdim]) / 2;
            		else if(m == 6)
            			value = (dst[k - 1] + dst[k - xdim + 1]) / 2;
            		else if(m == 7)
            			value = (dst[k - xdim - 1] + dst[k - xdim]) / 2;
            		else if(m == 8)
            			value = (dst[k - xdim - 1] + dst[k - xdim + 1]) / 2;
            		else if(m == 9)
            			value = (dst[k - xdim] + dst[k - xdim + 1]) / 2;
            		*/
            		if(m == 0)
            		    value = dst[k - 1];
            		else if(m == 1)
            			value = dst[k - xdim];
            		else if(m == 2)
            			value = dst[k - xdim - 1];
            		else if(m == 3)
            			value = (dst[k - 1] + dst[k - xdim]) / 2;
            		else if(m == 4)
            			value = (dst[k - 1] + dst[k - xdim + 1]) / 2;
            		
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
    
    public static ArrayList getIdealDeltasFromValues2(int src[], int xdim, int ydim)
    {
        int[]  dst = new int[xdim * ydim];
        byte[] map = new byte[(xdim - 2) * (ydim - 1)];
     
        // Get the map.
        int m = 0;
        for(int i = 1; i < ydim - 1; i++)
        {
        	for(int j = 1; j < xdim - 1; j++)
        	{
        	    int k = i * xdim + j;
        	    
        	    int [] delta = new int[3];
        	   
        	    delta[0] = src[k] - src[k - 1];
        	    delta[1] = src[k] - src[k - xdim]; 
        	    
        	    if(Math.abs(delta[0]) <= Math.abs(delta[1]))
        	        map[m++] = 0;
        	    else
        	    	map[m++] = 1;
        	}
        }
        
        int k = 0;
        m = 0;
        int sum = 0;
        for(int i = 0; i < ydim; i++)
        {
        	if(i == 0)
        	{
                for(int j = 0; j < xdim; j++)
                {
            	    if(j == 0)
            			dst[k++]       = 6;
            		else
            		{
            		    int delta = src[k] - src[k - 1];
                       dst[k++]    = delta;
                       sum      += Math.abs(delta);
            		}
            	}
            }
        	else
        	{
        		int delta = src[k] - src[k - xdim];
        		dst[k++]  = delta;
        		sum      += Math.abs(delta);
        		for(int j = 1; j < xdim - 1; j++)
                {
                    int n = map[m++];
                    if(n == 0)
                    	delta = src[k] - src[k - 1];
                    else if(n == 1)
                    	delta = src[k] - src[k - xdim];
                    /*
                    else if(n == 2)
                    	delta = src[k] - (src[k - 1] + src[k - xdim]) / 2;
                    */
                    dst[k++]  = delta;
            		sum      += Math.abs(delta);
            	}
        		delta = src[k] - src[k - 1];
        		dst[k++]  = delta;
        		sum      += Math.abs(delta);
        	}
        }
        
        ArrayList result = new ArrayList();
        result.add(sum);
        result.add(dst);
        result.add(map);
        return result;
    }
    
    public static int[] getValuesFromIdealDeltas2(int [] src, int xdim, int ydim, int init_value, byte [] map)
    {
    	if(src[0] != 6)
        	System.out.println("Wrong code.");
 
    	int[] dst = new int[xdim * ydim];
    	int k     = 0;
        dst[k++]  = init_value;
        
        for(int i = 1; i < xdim; i++)
        {
        	dst[k] = dst[k - 1] + src[k];
        	k++;
        }
        
        int m = 0;
        for(int i = 1; i < ydim; i++)
        {
            init_value += src[k];
            dst[k++]    = init_value;
            
        	for(int j = 1; j < xdim - 1; j++)	
           {
                int n = map[m++];
                if(n == 0)
                	dst[k] = dst[k - 1] +  src[k];
                else if(n == 1)
                	dst[k] = dst[k - xdim] + src[k];
                else if(n == 2)
                	dst[k] = (dst[k - 1] + dst[k - xdim]) / 2 + src[k];
                k++;
           }
        	dst[k] = dst[k - 1] + src[k];
        	k++;
        }
        return dst;
    }
    
    
    
    
    
    
    
    
    
    
    
     public static ArrayList getIdealDeltasFromValues3(int src[], int xdim, int ydim)
     {
         int[]  dst = new int[xdim * ydim];
         byte[] map = new byte[(xdim - 2) * (ydim - 1)];
      
         // Get the map.
         int m = 0;
         for(int i = 1; i < ydim - 1; i++)
         {
         	for(int j = 1; j < xdim - 1; j++)
         	{
         	    int k = i * xdim + j;
         	    
         	    int [] delta = new int[3];
         	   
         	    delta[0] = src[k] - src[k - 1];
         	    delta[1] = src[k] - src[k - xdim]; 
         	    delta[2] = src[k] - (src[k - 1] + src[k - xdim]) / 2;
         	   
         	    int value = Math.abs(delta[0]);
        	    int index = 0;
        	    for(int n = 1; n < 3; n++)
        	    {
        	    	if(value > Math.abs(delta[n]))
        	    	{
        	    		value = Math.abs(delta[n]);
        	    		index = n;
        	    	}
        	    }
        	    map[m++]   = (byte)index;
         	}
         }
         
         int k = 0;
         m = 0;
         int sum = 0;
         for(int i = 0; i < ydim; i++)
         {
         	if(i == 0)
         	{
                 for(int j = 0; j < xdim; j++)
                 {
             	    if(j == 0)
             			dst[k++]       = 6;
             		else
             		{
             		    int delta = src[k] - src[k - 1];
                        dst[k++]    = delta;
                        sum      += Math.abs(delta);
             		}
             	}
             }
         	else
         	{
         		int delta = src[k] - src[k - xdim];
         		dst[k++]  = delta;
         		sum      += Math.abs(delta);
         		for(int j = 1; j < xdim - 1; j++)
                 {
                     int n = map[m++];
                     if(n == 0)
                     	delta = src[k] - src[k - 1];
                     else if(n == 1)
                     	delta = src[k] - src[k - xdim];
                     else if(n == 2)
                     	delta = src[k] - (src[k - 1] + src[k - xdim]) / 2;
                     dst[k++]  = delta;
             		sum      += Math.abs(delta);
             	}
         		delta = src[k] - src[k - 1];
         		dst[k++]  = delta;
         		sum      += Math.abs(delta);
         	}
         }
         
         ArrayList result = new ArrayList();
         result.add(sum);
         result.add(dst);
         result.add(map);
         return result;
     }
     
     public static int[] getValuesFromIdealDeltas3(int [] src, int xdim, int ydim, int init_value, byte [] map)
     {
     	if(src[0] != 6)
         	System.out.println("Wrong code.");
  
     	int[] dst = new int[xdim * ydim];
     	int k     = 0;
         dst[k++]  = init_value;
         
         for(int i = 1; i < xdim; i++)
         {
         	dst[k] = dst[k - 1] + src[k];
         	k++;
         }
         
         int m = 0;
         for(int i = 1; i < ydim; i++)
         {
             init_value += src[k];
             dst[k++]    = init_value;
             
         	for(int j = 1; j < xdim - 1; j++)	
            {
                 int n = map[m++];
                 if(n == 0)
                 	dst[k] = dst[k - 1] +  src[k];
                 else if(n == 1)
                 	dst[k] = dst[k - xdim] + src[k];
                 else if(n == 2)
                 	dst[k] = (dst[k - 1] + dst[k - xdim]) / 2 + src[k];
                 k++;
            }
         	dst[k] = dst[k - 1] + src[k];
         	k++;
         }
         return dst;
     }
     
     public static ArrayList getIdealDeltasFromValues4(int src[], int xdim, int ydim)
     {
         int[]  dst = new int[xdim * ydim];
         byte[] map = new byte[(xdim - 2) * (ydim - 1)];
      
         // Get the map.
         int m = 0;
         for(int i = 1; i < ydim - 1; i++)
         {
         	for(int j = 1; j < xdim - 1; j++)
         	{
         	    int k = i * xdim + j;
         	    
         	    int [] delta = new int[4];
         	   
         	    delta[0] = src[k] - src[k - 1];
         	    delta[1] = src[k] - src[k - xdim];
         	    delta[2] = src[k] - (src[k - 1]+ src[k - xdim]) / 2;
         	    delta[3] = src[k] - (src[k - 1]+ src[k - xdim + 1]) / 2;
         	   
         	    int value = Math.abs(delta[0]);
         	    int index = 0;
         	    for(int n = 1; n < 4; n++)
         	    {
         	    	if(value > Math.abs(delta[n]))
         	    	{
         	    		value = Math.abs(delta[n]);
         	    		index = n;
         	    	}
         	    }
         	    map[m++]   = (byte)index;
         	}
         }
         
         int k = 0;
         m = 0;
         int sum = 0;
         for(int i = 0; i < ydim; i++)
         {
         	if(i == 0)
         	{
                 for(int j = 0; j < xdim; j++)
                 {
             	    if(j == 0)
             		    // Setting the first value to 6 to mark the delta type ideal.
             			dst[k++]       = 6;
             		else
             		{
             		    int delta = src[k] - src[k - 1];
                        dst[k++]    = delta;
                        sum      += Math.abs(delta);
             		}
             	}
             }
         	else
         	{
         		int delta = src[k] - src[k - xdim];
         		dst[k++]  = delta;
         		sum      += Math.abs(delta);
         		for(int j = 1; j < xdim - 1; j++)
                 {
                     int n = map[m++];
                     if(n == 0)
                     	delta = src[k] - src[k - 1];
                     else if(n == 1)
                     	delta = src[k] - src[k - xdim];
                     else if(n == 2)
                     	delta = src[k] - (src[k - 1] + src[k - xdim]) / 2;
                     else if(n == 3)
                     	delta = src[k] - (src[k - 1] + src[k - xdim + 1]) / 2;
                     dst[k++]  = delta;
             		 sum      += Math.abs(delta);
             	}
         		delta = src[k] - src[k - 1];
         		dst[k++]  = delta;
         		sum      += Math.abs(delta);
         	}
         }
         
         ArrayList result = new ArrayList();
         result.add(sum);
         result.add(dst);
         result.add(map);
         return result;
     }
     
     public static int[] getValuesFromIdealDeltas4(int [] src, int xdim, int ydim, int init_value, byte [] map)
     {
     	if(src[0] != 6)
         	System.out.println("Wrong code.");
  
     	int[] dst = new int[xdim * ydim];
     	int k     = 0;
         dst[k++]  = init_value;
         
         for(int i = 1; i < xdim; i++)
         {
         	dst[k] = dst[k - 1] + src[k];
         	k++;
         }
         
         int m = 0;
         for(int i = 1; i < ydim; i++)
         {
             init_value += src[k];
             dst[k++]    = init_value;
             
         	for(int j = 1; j < xdim - 1; j++)	
             {
                 int n = map[m++];
                 if(n == 0)
                 	dst[k] = dst[k - 1] + src[k];
                 else if(n == 1)
                 	dst[k] = dst[k - xdim] + src[k];
                 else if(n == 2)
                 	dst[k] = (dst[k - 1] + dst[k - xdim]) / 2 + src[k];
                 else if(n == 3)
                 	dst[k] = (dst[k - 1] + dst[k - xdim + 1]) / 2 + src[k];
                 k++;
             }
         	dst[k] = dst[k - 1] + src[k];
         	k++;
         }
         return dst;
     }
     

     public static ArrayList getIdealDeltasFromValues5(int src[], int xdim, int ydim)
     {
         int[]  dst = new int[xdim * ydim];
         byte[] map = new byte[(xdim - 2) * (ydim - 1)];
      
         // Get the map.
         int m = 0;
         for(int i = 1; i < ydim - 1; i++)
         {
         	for(int j = 1; j < xdim - 1; j++)
         	{
         	    int k = i * xdim + j;
         	    
         	    int [] delta = new int[5];
         	   
         	    delta[0] = src[k] - src[k - 1];
         	    delta[1] = src[k] - src[k - xdim];
         	    delta[2] = src[k] - src[k - xdim - 1];
         	    delta[3] = src[k] - (src[k - 1]+ src[k - xdim]) / 2;
         	    delta[4] = src[k] - (src[k - 1]+ src[k - xdim + 1]) / 2;
         	   
         	    int value = Math.abs(delta[0]);
         	    int index = 0;
         	    for(int n = 1; n < 5; n++)
         	    {
         	    	if(value > Math.abs(delta[n]))
         	    	{
         	    		value = Math.abs(delta[n]);
         	    		index = n;
         	    	}
         	    }
         	    map[m++]   = (byte)index;
         	}
         }
         
         int k = 0;
         m = 0;
         int sum = 0;
         for(int i = 0; i < ydim; i++)
         {
         	if(i == 0)
         	{
                 for(int j = 0; j < xdim; j++)
                 {
             	    if(j == 0)
             		    // Setting the first value to 6 to mark the delta type ideal.
             			dst[k++]       = 6;
             		else
             		{
             		    int delta = src[k] - src[k - 1];
                        dst[k++]    = delta;
                        sum      += Math.abs(delta);
             		}
             	}
             }
         	else
         	{
         		int delta = src[k] - src[k - xdim];
         		dst[k++]  = delta;
         		sum      += Math.abs(delta);
         		for(int j = 1; j < xdim - 1; j++)
                 {
                     int n = map[m++];
                     if(n == 0)
                     	delta = src[k] - src[k - 1];
                     else if(n == 1)
                     	delta = src[k] - src[k - xdim];
                     else if(n == 2)
                      	delta = src[k] - src[k - xdim - 1];
                     else if(n == 3)
                     	delta = src[k] - (src[k - 1] + src[k - xdim]) / 2;
                     else if(n == 4)
                     	delta = src[k] - (src[k - 1] + src[k - xdim + 1]) / 2;
                     dst[k++]  = delta;
             		 sum      += Math.abs(delta);
             	}
         		delta = src[k] - src[k - 1];
         		dst[k++]  = delta;
         		sum      += Math.abs(delta);
         	}
         }
         
         ArrayList result = new ArrayList();
         result.add(sum);
         result.add(dst);
         result.add(map);
         return result;
     }
     
     public static int[] getValuesFromIdealDeltas5(int [] src, int xdim, int ydim, int init_value, byte [] map)
     {
     	if(src[0] != 6)
         	System.out.println("Wrong code.");
  
     	int[] dst = new int[xdim * ydim];
     	int k     = 0;
         dst[k++]  = init_value;
         
         for(int i = 1; i < xdim; i++)
         {
         	dst[k] = dst[k - 1] + src[k];
         	k++;
         }
         
         int m = 0;
         for(int i = 1; i < ydim; i++)
         {
             init_value += src[k];
             dst[k++]    = init_value;
             
         	for(int j = 1; j < xdim - 1; j++)	
         	{
                int n = map[m++];
                if(n == 0)
                	dst[k] = dst[k - 1] + src[k];
                else if(n == 1)
                	dst[k] = dst[k - xdim] + src[k];
                else if(n == 2)
                 	dst[k] = dst[k - xdim - 1] + src[k];
                else if(n == 3)
                	dst[k] = (dst[k - 1] + dst[k - xdim]) / 2 + src[k];
                else if(n == 4)
                	dst[k] = (dst[k - 1] + dst[k - xdim + 1]) / 2 + src[k];
                k++;
            } 
         	
         	dst[k] = dst[k - 1] + src[k];
         	k++;
         }
         return dst;
     }
     
     
     /*
     public static ArrayList getIdealDeltasFromValues4(int src[], int xdim, int ydim)
     {
         int[]  dst = new int[xdim * ydim];
         byte[] map = new byte[(xdim - 2) * (ydim - 1)];
      
         // Get the map.
         int m = 0;
         for(int i = 1; i < ydim - 1; i++)
         {
         	for(int j = 1; j < xdim - 1; j++)
         	{
         	    int k = i * xdim + j;
         	    
         	    double [] delta = new double[6];
         	   
         	    delta[0] = Math.abs(src[k] - src[k - 1]);
         	    delta[1] = Math.abs(src[k] - src[k - xdim - 1]);
         	    delta[2] = Math.abs(src[k] - src[k - xdim]);
         	    delta[3] = Math.abs(src[k] - src[k - xdim + 1]);
         	   
         	    double addend = 0.00000001; 
         	    Hashtable <Double, Byte> delta_table = new Hashtable <Double, Byte>();
         	    ArrayList key_list = new ArrayList();
         	    key_list.add(delta[0]);
         	    delta_table.put(delta[0], (byte)0);
         	     
         	    for(k = 1; k < 4; k++)
         	    {
         	    	if(key_list.contains(delta[k]))
         	    	{
         	    	    delta[k] += addend;
         	    	    addend   *= 2.;
         	    	}
         	    	key_list.add(delta[k]);
         	    	delta_table.put(delta[k], (byte)k);
         	    }
         	    
         	    Collections.sort(key_list); 
         	    double key = (double)key_list.get(0);
         	    map[m++]   = delta_table.get(key);
         	}
         }
         
         int k = 0;
         m = 0;
         int sum = 0;
         for(int i = 0; i < ydim; i++)
         {
         	if(i == 0)
         	{
                 for(int j = 0; j < xdim; j++)
                 {
             	    if(j == 0)
             		    // Setting the first value to 6 to mark the delta type ideal.
             			dst[k++]       = 6;
             		else
             		{
             		    int delta = src[k] - src[k - 1];
                        dst[k++]    = delta;
                        sum      += Math.abs(delta);
             		}
             	}
             }
         	else
         	{
         		int delta = src[k] - src[k - xdim];
         		dst[k++]  = delta;
         		sum      += Math.abs(delta);
         		for(int j = 1; j < xdim - 1; j++)
                 {
                     int n = map[m++];
                     if(n == 0)
                     	delta = src[k] - src[k - 1];
                     else if(n == 1)
                     	delta = src[k] - src[k - xdim - 1];
                     else if(n == 2)
                     	delta = src[k] - src[k - xdim];
                     else if(n == 3)
                     	delta = src[k] - src[k - xdim + 1];
                     dst[k++]  = delta;
             		sum      += Math.abs(delta);
             	}
         		delta = src[k] - src[k - 1];
         		dst[k++]  = delta;
         		sum      += Math.abs(delta);
         	}
         }
         
         ArrayList result = new ArrayList();
         result.add(sum);
         result.add(dst);
         result.add(map);
         return result;
     }
     
     public static int[] getValuesFromIdealDeltas4(int [] src, int xdim, int ydim, int init_value, byte [] map)
     {
     	if(src[0] != 6)
         	System.out.println("Wrong code.");
  
     	int[] dst = new int[xdim * ydim];
     	int k     = 0;
         dst[k++]  = init_value;
         
         for(int i = 1; i < xdim; i++)
         {
         	dst[k] = dst[k - 1] + src[k];
         	k++;
         }
         
         int m = 0;
         for(int i = 1; i < ydim; i++)
         {
             init_value += src[k];
             dst[k++]    = init_value;
             
         	for(int j = 1; j < xdim - 1; j++)	
             {
                 int n = map[m++];
                 if(n == 0)
                 	dst[k] = dst[k - 1] + src[k];
                 else if(n == 1)
                 	dst[k] = dst[k - xdim - 1] + src[k];
                 else if(n == 2)
                 	dst[k] = dst[k - xdim] + src[k];
                 else if(n == 3)
                 	dst[k] = dst[k - xdim + 1] + src[k];
                 k++;
             }
         	dst[k] = dst[k - 1] + src[k];
         	k++;
         }
         return dst;
     }
     */
     
    
     
    public static ArrayList getIdealDeltasFromValues6(int src[], int xdim, int ydim)
    {
        int[]  dst = new int[xdim * ydim];
        byte[] map = new byte[(xdim - 2) * (ydim - 1)];
     
        // Get the map.
        int m = 0;
        for(int i = 1; i < ydim - 1; i++)
        {
        	for(int j = 1; j < xdim - 1; j++)
        	{
        	    int k = i * xdim + j;
        	    
        	    double [] delta = new double[6];
        	   
        	    delta[0] = Math.abs(src [k] - src[k - 1]);
        	    delta[1] = Math.abs(src[k] - (src[k - 1] + src[k - xdim - 1]) / 2);
        	    delta[2] = Math.abs(src[k] - src[k - xdim - 1]);
        	    delta[3] = Math.abs(src[k] - (src[k - xdim - 1] + src[k - xdim + 1]) / 2);
        	    delta[4] = Math.abs(src[k] - src[k - xdim + 1]);
        	    delta[5] = Math.abs(src[k] - (src[k - 1] + src[k - xdim + 1]) / 2);
        	   
        	    
        	    double addend = 0.00000001; 
        	    Hashtable <Double, Byte> delta_table = new Hashtable <Double, Byte>();
        	    ArrayList key_list = new ArrayList();
        	    key_list.add(delta[0]);
        	    delta_table.put(delta[0], (byte)0);
        	     
        	    for(k = 1; k < 6; k++)
        	    {
        	    	if(key_list.contains(delta[k]))
        	    	{
        	    	    delta[k] += addend;
        	    	    addend   *= 2.;
        	    	}
        	    	key_list.add(delta[k]);
        	    	delta_table.put(delta[k], (byte)k);
        	    }
        	    
        	    Collections.sort(key_list); 
        	    double key = (double)key_list.get(0);
        	    map[m++]   = delta_table.get(key);
        	}
        }
        
        int k = 0;
        m = 0;
        int sum = 0;
        for(int i = 0; i < ydim; i++)
        {
        	if(i == 0)
        	{
                for(int j = 0; j < xdim; j++)
                {
            	    if(j == 0)
            		    // Setting the first value to 6 to mark the delta type ideal.
            			dst[k++]       = 6;
            		else
            		{
            		    int delta = src[k] - src[k - 1];
                        dst[k++]    = delta;
                        sum      += Math.abs(delta);
            		}
            	}
            }
        	else
        	{
        		int delta = src[k] - src[k - xdim];
        		dst[k++]  = delta;
        		sum      += Math.abs(delta);
        		for(int j = 1; j < xdim - 1; j++)
                {
                    int n = map[m++];
                    if(n == 0)
                    	delta = src[k] - src[k - 1];
                    else if(n == 1)
                    	delta = src[k] - (src[k - 1] + src[k - xdim - 1]) / 2;
                    else if(n == 2)
                    	delta = src[k] - src[k - xdim - 1];
                    else if(n == 3)
                    	delta = src[k] - (src[k - xdim - 1] + src[k - xdim + 1]) / 2;
                    else if(n == 4)
                    	delta = src[k] - src[k - xdim + 1];
                    else if(n == 5)
                    	delta = src[k] - (src[k - 1] + src[k - xdim + 1]) / 2;
                    dst[k++]  = delta;
            		sum      += Math.abs(delta);
            	}
        		delta = src[k] - src[k - 1];
        		dst[k++]  = delta;
        		sum      += Math.abs(delta);
        	}
        }
        
        ArrayList result = new ArrayList();
        result.add(sum);
        result.add(dst);
        result.add(map);
        return result;
    }
    
    public static int[] getValuesFromIdealDeltas6(int [] src, int xdim, int ydim, int init_value, byte [] map)
    {
    	if(src[0] != 6)
        	System.out.println("Wrong code.");
 
    	int[] dst = new int[xdim * ydim];
    	int k     = 0;
        dst[k++]  = init_value;
        
        for(int i = 1; i < xdim; i++)
        {
        	dst[k] = dst[k - 1] + src[k];
        	k++;
        }
        
        int m = 0;
        for(int i = 1; i < ydim; i++)
        {
            init_value += src[k];
            dst[k++]    = init_value;
            
        	for(int j = 1; j < xdim - 1; j++)	
            {
                int n = map[m++];
                if(n == 0)
                	dst[k] = dst[k - 1] + src[k];
                else if(n == 1)
                	dst[k] = (dst[k - 1] + dst[k - xdim - 1]) / 2 + src[k];
                else if(n == 2)
                	dst[k] = dst[k - xdim - 1] + src[k];
                else if(n == 3)
                	dst[k] = (dst[k - xdim - 1] + dst[k - xdim + 1]) / 2 + src[k];
                else if(n == 4)
                	dst[k] = dst[k - xdim + 1] + src[k];
                else if(n == 5)
                	dst[k] = (dst[k - 1] + dst[k - xdim + 1]) / 2 + src[k];
                k++;
            }
        	dst[k] = dst[k - 1] + src[k];
        	k++;
        }
        return dst;
    }
    
    /*
    public static ArrayList getIdealDeltasFromValues8(int src[], int xdim, int ydim)
    {
        int[]  dst = new int[xdim * ydim];
        byte[] map = new byte[(xdim - 2) * (ydim - 1)];
     
        // Get the map.
        int m = 0;
        for(int i = 1; i < ydim - 1; i++)
        {
        	for(int j = 1; j < xdim - 1; j++)
        	{
        	    int k = i * xdim + j;
        	    
        	    double [] delta = new double[8];
        	   
        	    delta[0] = Math.abs(src [k] - src[k - 1]);
        	    delta[1] = Math.abs(src[k] - src[k - xdim - 1]);
        	    delta[2] = Math.abs(src[k] - src[k - xdim]);
        	    delta[3] = Math.abs(src[k] - src[k - xdim + 1]);
        	    delta[4] = Math.abs(src[k] - src[k + 1]);
        	    delta[5] = Math.abs(src[k] - src[k + xdim + 1]);
        	    delta[6] = Math.abs(src[k] - src[k + xdim]);
        	    delta[7] = Math.abs(src[k] - src[k + xdim - 1]);
        	    
        	    double addend = 0.00000001; 
        	    Hashtable <Double, Byte> delta_table = new Hashtable <Double, Byte>();
        	    ArrayList key_list = new ArrayList();
        	    key_list.add(delta[0]);
        	    delta_table.put(delta[0], (byte)0);
        	     
        	    for(k = 1; k < 8; k++)
        	    {
        	    	if(key_list.contains(delta[k]))
        	    	{
        	    	    delta[k] += addend;
        	    	    addend   *= 2.;
        	    	}
        	    	key_list.add(delta[k]);
        	    	delta_table.put(delta[k], (byte)k);
        	    }
        	    
        	    Collections.sort(key_list); 
        	    double key = (double)key_list.get(0);
        	    map[m++]   = delta_table.get(key);
        	}
        }
        
        int k = 0;
        m = 0;
        int sum = 0;
        for(int i = 0; i < ydim - 1; i++)
        {
        	if(i == 0)
        	{
                for(int j = 0; j < xdim; j++)
                {
            	    if(j == 0)
            		    // Setting the first value to 6 to mark the delta type ideal.
            			dst[k++]       = 6;
            		else
            		{
            		    int delta = src[k] - src[k - 1];
                        dst[k++]    = delta;
                        sum      += Math.abs(delta);
            		}
            	}
            }
        	else
        	{
        		int delta = src[k] - src[k - xdim];
        		dst[k++]  = delta;
        		sum      += Math.abs(delta);
        		for(int j = 1; j < xdim - 1; j++)
                {
                    int n = map[m++];
                    if(n == 0)
                    	delta = src[k] - src[k - 1];
                    else if(n == 1)
                    	delta = src[k] - src[k - xdim - 1];
                    else if(n == 2)
                    	delta = src[k] - src[k - xdim];
                    else if(n == 3)
                    	delta = src[k] - src[k - xdim + 1];
                    else if(n == 4)
                    	delta = src[k] - src[k + 1];
                    else if(n == 5)
                    	delta = src[k] - src[k - xdim + 1];
                    else if(n == 6)
                    	delta = src[k] - src[k + xdim];
                    else if(n == 7)
                    	delta = src[k] - src[k + xdim - 1];
                    dst[k++]  = delta;
            		sum      += Math.abs(delta);
            	}
        		delta = src[k] - src[k - 1];
        		dst[k++]  = delta;
        		sum      += Math.abs(delta);
        	}
        }
        
        int delta = src[k] - src[k - xdim];
		dst[k++]  = delta;
		sum      += Math.abs(delta);
		for(int j = 1; j < xdim; j++)
		{
			delta = src[k] - src[k - 1];
			dst[k++]  = delta;
			sum      += Math.abs(delta);	
		}
        
        ArrayList result = new ArrayList();
        result.add(sum);
        result.add(dst);
        result.add(map);
        return result;
    }
    
    public static int[] getValuesFromIdealDeltas8(int [] src, int xdim, int ydim, int init_value, byte [] map)
    {
    	if(src[0] != 6)
        	System.out.println("Wrong code.");
 
    	int[] dst = new int[xdim * ydim];
    	int k     = 0;
        dst[k++]  = init_value;
        
        for(int i = 1; i < xdim; i++)
        {
        	dst[k] = dst[k - 1] + src[k];
        	k++;
        }
        
        int m = 0;
        for(int i = 1; i < ydim - 1; i++)
        {
            init_value += src[k];
            dst[k++]    = init_value;
            
        	for(int j = 1; j < xdim - 1; j++)	
            {
                int n = map[m++];
                if(n == 0)
                	dst[k] = dst[k - 1] + src[k];
                else if(n == 1)
                	dst[k] = dst[k - xdim - 1] + src[k];
                else if(n == 2)
                	dst[k] = dst[k - xdim] + src[k];
                else if(n == 3)
                	dst[k] = dst[k - xdim + 1] + src[k];
                else if(n == 4)
                	dst[k] = dst[k + 1] + src[k];
                else if(n == 5)
                	dst[k] = dst[k + xdim + 1] + src[k];
                else if(n == 6)
                	dst[k] = dst[k + xdim] + src[k];
                else if(n == 7)
                	dst[k] = dst[k + xdim - 1] + src[k];
               
                k++;
            }
        	dst[k] = dst[k - 1] + src[k];
        	k++;
        }
        return dst;
    }
    
    */
    
    
    public static ArrayList getIdealDeltasFromValues8(int src[], int xdim, int ydim)
    {
        int[]  dst = new int[xdim * ydim];
        byte[] map = new byte[(xdim - 2) * (ydim - 1)];
     
        // Get the map.
        int m = 0;
        for(int i = 1; i < ydim - 1; i++)
        {
        	for(int j = 1; j < xdim - 1; j++)
        	{
        	    int k = i * xdim + j;
        	    
        	    double [] delta = new double[8];
        	   
        	    delta[0] = Math.abs(src [k] - src[k - 1]);
        	    delta[1] = Math.abs(src[k] - (src[k - 1] + src[k - xdim - 1]) / 2);
        	    delta[2] = Math.abs(src[k] - src[k - xdim - 1]);
        	    delta[3] = Math.abs(src[k] - (src[k - xdim - 1] + src[k - xdim]) / 2);
        	    delta[4] = Math.abs(src[k] - src[k - xdim]);
        	    delta[5] = Math.abs(src[k] - (src[k - xdim] + src[k - xdim + 1]) / 2);
        	    delta[6] = Math.abs(src[k] - src[k - xdim + 1]);
        	    delta[7] = Math.abs(src[k] - (src[k - xdim + 1] + src[k - 1]) / 2);
        	    
        	    double addend = 0.00000001; 
        	    Hashtable <Double, Byte> delta_table = new Hashtable <Double, Byte>();
        	    ArrayList key_list = new ArrayList();
        	    key_list.add(delta[0]);
        	    delta_table.put(delta[0], (byte)0);
        	     
        	    for(k = 1; k < 8; k++)
        	    {
        	    	if(key_list.contains(delta[k]))
        	    	{
        	    	    delta[k] += addend;
        	    	    addend   *= 2.;
        	    	}
        	    	key_list.add(delta[k]);
        	    	delta_table.put(delta[k], (byte)k);
        	    }
        	    
        	    Collections.sort(key_list); 
        	    double key = (double)key_list.get(0);
        	    map[m++]   = delta_table.get(key);
        	}
        }
        
        int k = 0;
        m = 0;
        int sum = 0;
        for(int i = 0; i < ydim; i++)
        {
        	if(i == 0)
        	{
                for(int j = 0; j < xdim; j++)
                {
            	    if(j == 0)
            		    // Setting the first value to 6 to mark the delta type ideal.
            			dst[k++]       = 6;
            		else
            		{
            		    int delta = src[k] - src[k - 1];
                        dst[k++]    = delta;
                        sum      += Math.abs(delta);
            		}
            	}
            }
        	else
        	{
        		int delta = src[k] - src[k - xdim];
        		dst[k++]  = delta;
        		sum      += Math.abs(delta);
        		for(int j = 1; j < xdim - 1; j++)
                {
                    int n = map[m++];
                    if(n == 0)
                    	delta = src[k] - src[k - 1];
                    else if(n == 1)
                    	delta = src[k] - (src[k - 1] + src[k - xdim - 1]) / 2;
                    else if(n == 2)
                    	delta = src[k] - src[k - xdim - 1];
                    else if(n == 3)
                    	delta = src[k] - (src[k - xdim] + src[k - xdim + 1]) / 2;
                    else if(n == 4)
                    	delta = src[k] - src[k - xdim];
                    else if(n == 5)
                    	delta = src[k] - (src[k - xdim] + src[k - xdim + 1]) / 2;
                    else if(n == 6)
                    	delta = src[k] - src[k - xdim + 1];
                    else if(n == 7)
                    	delta = src[k] - (src[k - xdim + 1] + src[k - 1]) / 2;
                    dst[k++]  = delta;
            		sum      += Math.abs(delta);
            	}
        		delta = src[k] - src[k - 1];
        		dst[k++]  = delta;
        		sum      += Math.abs(delta);
        	}
        }
        
        ArrayList result = new ArrayList();
        result.add(sum);
        result.add(dst);
        result.add(map);
        return result;
    }
    
    public static int[] getValuesFromIdealDeltas8(int [] src, int xdim, int ydim, int init_value, byte [] map)
    {
    	if(src[0] != 6)
        	System.out.println("Wrong code.");
 
    	int[] dst = new int[xdim * ydim];
    	int k     = 0;
        dst[k++]  = init_value;
        
        for(int i = 1; i < xdim; i++)
        {
        	dst[k] = dst[k - 1] + src[k];
        	k++;
        }
        
        int m = 0;
        for(int i = 1; i < ydim; i++)
        {
            init_value += src[k];
            dst[k++]    = init_value;
            
        	for(int j = 1; j < xdim - 1; j++)	
            {
                int n = map[m++];
                if(n == 0)
                	dst[k] = dst[k - 1] + src[k];
                else if(n == 1)
                	dst[k] = (dst[k - 1] + dst[k - xdim - 1]) / 2 + src[k];
                else if(n == 2)
                	dst[k] = dst[k - xdim - 1] + src[k];
                else if(n == 3)
                	dst[k] = (dst[k - xdim] + dst[k - xdim + 1]) / 2 + src[k];
                else if(n == 4)
                	dst[k] = dst[k - xdim] + src[k];
                else if(n == 5)
                	dst[k] = (dst[k - xdim] + dst[k - xdim + 1]) / 2 + src[k];
                else if(n == 6)
                	dst[k] = dst[k - xdim + 1] + src[k];
                else if(n == 7)
                	dst[k] = (dst[k - xdim + 1] + dst[k - 1]) / 2 + src[k];
                k++;
            }
        	dst[k] = dst[k - 1] + src[k];
        	k++;
        }
        return dst;
    }
    
    public static ArrayList getDeltaListFromValues(int src[], int xdim, int ydim)
    {
    	ArrayList delta_list = new ArrayList();
    	
    	int k = 0;
        for(int i = 0; i < ydim; i++)
        {
        	for(int j = 0; j < xdim; j++)
            {
            	int size = 0;
            	if(i == 0 || i == ydim - 1)
            	{
        	        if(j == 0  || j == xdim - 1)
        	    	    size = 3;
        	        else
        	    	    size = 5;
            	}
            	else
            	{
            		if(j == 0  || j == xdim - 1)
        	    	    size = 5;
        	        else
        	    	    size = 8;	
            	}
        	    
        	    int [] value    = new int[size];
    	    	int [] location = new int[size];
    	    	
    	    	if(i == 0)
    	    	{
    	    		if(j == 0)
        	    	{
        	    		value[0]     = src[k] - src[k + 1];
            	    	value[1]     = src[k + xdim];
            	    	value[2]     = src[k + xdim + 1];
            	    	
            	    	location[0]  = 4;
            	    	location[1]  = 6;
            	    	location[2]  = 7;	
        	    	}
        	    	else if(j == xdim - 1)
        	    	{
        	    		value[0]     = src[k] - src[k - 1];
            	    	value[1]     = src[k + xdim - 1];
            	    	value[2]     = src[k + xdim];
            	    	
            	    	location[0]  = 3;
            	    	location[1]  = 5;
            	    	location[2]  = 6;	
        	    	}
        	    	else
        	    	{
        	    		value[0]     = src[k] - src[k - 1];
            	    	value[1]     = src[k] - src[k + 1];
            	    	value[2]     = src[k] - src[k + xdim - 1];
            	    	value[3]     = src[k] - src[k + xdim];
            	    	value[4]     = src[k] - src[k + xdim + 1];
            	    	
            	    	location[0]  = 3;
            	    	location[1]  = 4;
            	    	location[2]  = 5;
            	    	location[3]  = 6;
            	    	location[4]  = 7;	
        	    	}
    	    	}
    	    	else if(i == ydim - 1)
    	    	{
    	    		if(j == 0)
         	    	{
         	    		value[0]     = src[k] - src[k - xdim];
            	    	value[1]     = src[k - xdim + 1];
            	    	value[2]     = src[k + 1];
            	    	
            	    	location[0]  = 1;
            	    	location[1]  = 2;
            	    	location[2]  = 4;	
         	    	}
         	    	else if(j == xdim - 1)
         	    	{
         	    		value[0]     = src[k] - src[k - xdim - 1];
            	    	value[1]     = src[k - xdim];
            	    	value[2]     = src[k - 1];
            	    	
            	    	location[0]  = 0;
            	    	location[1]  = 1;
            	    	location[2]  = 3;	
         	    	}
         	    	else
         	    	{
         	    		value[0] = src[k] - src[k - xdim - 1];
            	    	value[1] = src[k] - src[k - xdim];
            	    	value[2] = src[k] - src[k - xdim + 1];
            	    	value[3] = src[k] - src[k - 1];
            	    	value[4] = src[k] - src[k + 1];
            	    	
            	    	location[0]  = 0;
            	    	location[1]  = 1;
            	    	location[2]  = 2;
            	    	location[3]  = 3;
            	    	location[4]  = 4;	
         	    	}	
    	    	}
    	    	else
    	    	{
    	    		if(j == 0)
         	    	{
         	    		value[0] = src[k] - src[k - xdim];
            	    	value[1] = src[k] - src[k - xdim + 1];
            	    	value[2] = src[k] - src[k + 1];
            	    	value[3] = src[k] - src[k + xdim];
            	    	value[4] = src[k] - src[k + xdim + 1];
            	    	
            	    	location[0]  = 1;
            	    	location[1]  = 2;
            	    	location[2]  = 4;
            	    	location[3]  = 6;
            	    	location[4]  = 7;	
         	    	}
         	    	else if(j == xdim - 1)
         	    	{
         	    		value[0] = src[k] - src[k - xdim - 1];
            	    	value[1] = src[k] - src[k - xdim];
            	    	value[2] = src[k] - src[k - 1];
            	    	value[3] = src[k] - src[k + xdim - 1];
            	    	value[4] = src[k] - src[k + xdim];
            	    	
            	    	location[0]  = 0;
            	    	location[1]  = 1;
            	    	location[2]  = 3;
            	    	location[3]  = 5;
            	    	location[4]  = 6;	
         	    	}
         	    	else
         	    	{
         	    		value[0] = src[k] - src[k - xdim - 1];
            	    	value[1] = src[k] - src[k - xdim];
            	    	value[2] = src[k] - src[k - xdim + 1];
            	    	value[3] = src[k] - src[k - 1];
            	    	value[4] = src[k] - src[k + 1];
            	    	value[5] = src[k] - src[k + xdim - 1];
            	    	value[6] = src[k] - src[k + xdim];
            	    	value[7] = src[k] - src[k + xdim + 1];
            	    	
            	    	location[0]  = 0;
            	    	location[1]  = 1;
            	    	location[2]  = 2;
            	    	location[3]  = 3;
            	    	location[4]  = 4;
            	    	location[5]  = 5;
            	    	location[6]  = 6;
            	    	location[7]  = 7;	
         	    	}	
    	    	}
            	
    	    	double [] delta = new double[size];
    	        for(int m = 0; m < size; m++)
    	    	    delta[m] = Math.abs(value[m]);
    	    	
    	        double addend = 0.00000001; 
    	        Hashtable <Double, ArrayList> delta_table = new Hashtable <Double, ArrayList>();
    	        ArrayList key_list = new ArrayList();
    	        
    	        for(int m = 0; m < size; m++)
    	        {
    	        	if(key_list.contains(delta[m]))
    	        	{
    	        		delta[m] += addend;
    	        		addend   *= 2;
    	        	}
    	        	key_list.add(delta[m]);
    	        	ArrayList list = new ArrayList();
    	        	list.add(value[m]);
    	        	list.add(location[m]);
    	        	delta_table.put(delta[m], list);
    	        }
    	        
    	        Collections.sort(key_list); 
    	       
    	        int [][] table = new int[size][2];
	    		for(int m = 0; m < size; m++)
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