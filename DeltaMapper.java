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
        	// We set the first value to zero to mark the type of deltas as the average of a and b.
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
 
    public static ArrayList getGradientDeltasFromValues(int src[], int xdim, int ydim)
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

    public static int[] getValuesFromGradientDeltas(int src[], int xdim, int ydim, int init_value)
    {
    	int[] dst = new int[xdim * ydim];
        dst[0]    = init_value;
        int value = init_value;
        
        if(src[0] != 4)
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
            		int k = i * xdim + j;
                	value = dst[k - 1];
                	value += src[k];
                	dst[k] = value;	
            	}
            }
        }
        return dst;
    }
 
    // Adjusting the diagonal deltas to account for the different distances from
    // pixel centers does not seem to help.  Another possibility is averaging the
    // the backward diagonal of pixels a and d. That would involve skipping the 
    // second row since the the filter would then use 3 rows.
    public static ArrayList getGradientDeltasFromValues2(int src[], int xdim, int ydim)
    {
    	int[] dst          = new int[xdim * ydim];
        int init_value     = src[0];
        int value          = init_value;
        
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
            	    	// Use the vertical delta and reset the init value.
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
            	    	// Use the gradient filter.
            	    	int a = src[k - 1];
            	    	int b = src[k - xdim];
            	    	int c = src[k - xdim - 1];
            	    	int d = src[k - xdim + 1];
            	    	int e = src[k - xdim - 2];
            	    	
            	    	int [] gradient = new int[4];
            	    	gradient[0] = Math.abs(c - b);
            	    	gradient[1] = Math.abs(c - a);
            	    	
            	    	double adjusted_gradient = Math.abs(b - d);
            	    	adjusted_gradient *= .707;
            	    	gradient[2] = (int)adjusted_gradient;
      
            	    	adjusted_gradient = Math.abs(a - e);
            	    	adjusted_gradient *= .707;
            	    	gradient[3] = (int)adjusted_gradient;;
            	    	
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
            	    		_delta -= src[k - 1];
            	    	else if(max_index == 1)
            	    		_delta -= src[k - xdim];
            	    	else if(max_index == 2)
            	    		_delta -= src[k - xdim - 1];
            	    	else
            	    		_delta -= src[k - xdim + 1];
            	    	dst[k] = _delta;
            	    	sum += Math.abs(_delta);
            	    	_delta = Math.abs(_delta);
            	    	
            	    	if(_delta > delta[0] || _delta > delta[1] || _delta > delta[2] || _delta > delta[3])
            	    		number_of_misses++;
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
        return result;    
    }

    public static int[] getValuesFromGradientDeltas2(int src[], int xdim, int ydim, int init_value)
    {
    	int[] dst = new int[xdim * ydim];
        dst[0]    = init_value;
        int value = init_value;
        
        if(src[0] != 4)
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
        	    	
        	    	double adjusted_gradient = Math.abs(b - d);
        	    	adjusted_gradient *= .707;
        	    	gradient[2] = (int)adjusted_gradient;
  
        	    	adjusted_gradient = Math.abs(a - e);
        	    	adjusted_gradient *= .707;
        	    	gradient[3] = (int)adjusted_gradient;;
        	    	
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
    	    	
    	    	if(j == 1)
    	    		sum[4] += Math.abs(_delta);
    	    	else
    	    	{
    	    		a = src[k - 1];
        	    	b = src[k - xdim];
        	    	c = src[k - xdim - 1];
        	    	d = src[k - xdim + 1];
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
        	   
        	    	_delta = src[k];
        	    	if(max_index == 0)
        	    		_delta -= src[k - 1];
        	    	else if(max_index == 1)
        	    		_delta -= src[k - xdim];
        	    	else if(max_index == 2)
        	    		_delta -= src[k - xdim - 1];
        	    	else
        	    		_delta -= src[k - xdim + 1];
        	    	sum[4] += Math.abs(_delta);	
    	    	}
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
            			dst[j] = 0;
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
        		int m = map[i - 1];
        		
        		for(int j = 0; j < xdim; j++)
                {
        			int k = i * xdim + j;
            	    if(j == 0)
            	    {
            	    	// Use the vertical delta for the first value.
            	    	int delta  = src[k] - init_value;
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
            	    		if(j == 1)
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
                    	    	gradient[2] = Math.abs(b - d);
                    	    	gradient[3] = Math.abs(a - e);
                    	    	
                    	    
                    	    	int max_value = gradient[0];
                    	    	int max_index = 0;
                    	    	for(int n = 1; n < 4; n++)
                    	    	{
                    	    		if(gradient[m] > max_value)
                    	    		{
                    	    		    max_value = gradient[n];
                    	    		    max_index = n;
                    	    		}
                    	    	}
                    	    	
                    	    	int delta = src[k];
                    	    	if(max_index == 0)
                    	    		delta -= src[k - 1];
                    	    	else if(max_index == 1)
                    	    		delta -= src[k - xdim];
                    	    	else if(max_index == 2)
                    	    		delta -= src[k - xdim - 1];
                    	    	else
                    	    		delta -= src[k - xdim + 1];
                    	    	dst[k] = delta;
                    	    	sum += Math.abs(delta);	
            	    		}
            	    		
            	    	}
            	    }
            	    else
            	    {
            	    	// Use the horizontal delta.
            	    	int delta  = src[k] - src[k - 1];
            	    	dst[k] = delta;
            	    	sum += Math.abs(delta);
            	    }
                }
        	}
        }
       
        //System.out.println();
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
        
        if(src[0] != 0)
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
  
    

    // This function returns the result from the standard png filters, using
    // the filter that produces the smallest delta sum for each row.
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
            		    // Setting the first value to 4 to mark the delta type mixed.
            			dst[j] = 0;
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
                	    	int d = src[k - xdim - 1];;
            	    		
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
    
    public static int[] getValuesFromMixedDeltas2(int [] src, int xdim, int ydim, int init_value, byte [] map)
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
            	    value = dst[k - 1];
            	    value += src[k];
            	    dst[k] = value;
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
            	    	//We have a set of 4 possible deltas to use.
            	    	int a = src[k] - src[k - 1];
            	    	int b = src[k] - src[k - xdim];
            	    	int c = src[k] - src[k - xdim - 1];
            	    	int d = src[k] - src[k - xdim + 1];
            	    	
            	    	
            	    	// There might be a way to choose deltas that produces
            	    	// more compression later.  For now we'll just prioritize 
            	    	// the comparisons as horizontal, vertical, back diagonal,
            	    	// and forward diagonal.
            	    	
            	    	//int delta_a = a - previous_delta;
            	    	//int delta_b = b - previous_delta;
            	    	//int delta_c = c - previous_delta;
            	    	//int delta_d = d - previous_delta;
            	    
            	    	
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
    		    		
    		    		/*
    		    		System.out.println("Table:");
    		    		for(int m = 0; m < table.length; m++)
    		    			System.out.println(table[m][0] + " " + table[m][1]);
    		    		*/
    		    		
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
    		    		
    		    		/*
    		    		System.out.println("Table:");
    		    		for(int m = 0; m < table.length; m++)
    		    			System.out.println(table[m][0] + " " + table[m][1]);
    		    		System.out.println();
    		    		*/
    		    		
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
    		    		
    		    		/*
    		    		if(i == 1)
    		    		{
    		    		System.out.println("Table:");
    		    		for(int m = 0; m < table.length; m++)
    		    			System.out.println(table[m][0] + " " + table[m][1]);
    		    		System.out.println();
    		    		}
    		    		*/
    		    		
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
    		    		
    		    		/*
    		    		if(i == 1)
    		    		{
    		    		    System.out.println("Table:");
    		    		    for(int m = 0; m < table.length; m++)
    		    			    System.out.println(table[m][0] + " " + table[m][1]);
    		    		    System.out.println();
    		    		}
    		    	    */
    		    		
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
    		    		
    		    		/*
    		    		System.out.println("Table:");
    		    		for(int m = 0; m < table.length; m++)
    		    			System.out.println(table[m][0] + " " + table[m][1]);
    		    		System.out.println();
    		    		*/
    		    		
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
    		    		
    		    		/*
    		    		if(j == 1)
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
    		    		
    		    		/*
    		    		System.out.println("Table:");
    		    		for(int m = 0; m < table.length; m++)
    		    			System.out.println(table[m][0] + " " + table[m][1]);
    		    		System.out.println();
    		    		*/
    		    		
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
			if(x > 1)
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
			if(x > 1)
			    neighbors.add(src[(y + 1) * xdim + x - 1]);	
			neighbors.add(src[(y + 1) * xdim + x]);
			if(x < xdim - 1)
			    neighbors.add(src[(y + 1) * xdim + x + 1]);
		}
		
		return neighbors;
	}
    
    public static int getNeighborIndex(int x, int y, int xdim, int location)
    {
    	int k = y * xdim + x;
    	//System.out.println("Index is " + k);
    	//System.out.println("Location is " + location);
    	
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
    	//System.out.println("Neighbor index is " + k);
    	
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
    
    // Get an ideal delta set and a map of which pixels are used.
    public static ArrayList getIdealDeltasFromValues2(int src[], int xdim, int ydim, ArrayList delta_list)
    {
    	int size = xdim * ydim;
    	
    	int[]     delta       = new int[size];
        byte[]    map         = new byte[size];
        boolean[] is_assigned = new boolean[size];
        int[]     assignments = new int[size];
       
        int x = xdim / 2;
        int y = ydim / 2;
        int i = y * xdim + x;
        
        
        int p = 0;
        
        
        int init_value = src[i];
        int [][] table   = (int [][])delta_list.get(i);
        
        // Used only for error checking delta type.
        delta[i] = 0; 
       
        int current_delta = table[0][0];
        map[i]   = (byte)table[0][1];
        is_assigned[i] = true;
        assignments[i]++;
        p++;
       
        
        // We know this pixel is unassigned.
        int j = getNeighborIndex(x, y, xdim, table[0][1]);
        int k = getInverseLocation(table[0][1]); 
        delta[j] = -table[0][0];
        map[j]   = (byte)k;
        is_assigned[j] = true;
        assignments[j]++;
        p++;
        
        
        // Now we might be rerouted to an already assigned pixel.
        boolean neighbor_unassigned = true;
        while(p < size && neighbor_unassigned)
        {
            i = j;
            x = i % xdim;
            y = i / xdim;
            table = (int[][])delta_list.get(i); 
            outer: for(int m = 0; m < table.length; m++)
		    {
			    j = getNeighborIndex(x, y, xdim, table[m][1]); 
			    if(!is_assigned[j])
			    {
				    //System.out.println("Unassigned neighbor at location " + table[m][1]);
				
				    k = getInverseLocation(table[m][1]);
				    delta[j] = -table[m][0];
		            map[j]   = (byte)k;
		            is_assigned[j] = true;
		            assignments[j]++;
		            //System.out.println("Assigned pixel " + (n + 2));
		            //System.out.println("Index is " + j);
		            //System.out.println("Set location of value to " + map[j]);
		            //System.out.println();
		            p++;
				    break outer;
			    }
			    else
			    {
				    //System.out.println("Assigned neighbor.");	
			        //System.out.println(table[m][0] + " " + table[m][1]);
			    	if(m == table.length - 1)
			    		neighbor_unassigned = false;	
			    }
		    }
        }
        
        //System.out.println("Number of assignments was " + p);
		
        int n = 0;
        for(i = 0; i < size; i++)
        	if(assignments[i] != 0)
        		n++;
        
        System.out.println("Number of assigned deltas after one pass is " + n);
        
        n = 0;
        for(i = 0; i < size; i++)
	    {
	        x = i % xdim;
	        y = i / xdim;
	        
	    
	        ArrayList neighbors = new ArrayList();
	    
	        if(assignments[i] == 0)
	        {
	            neighbors = getNeighbors(assignments, x, y, xdim, ydim);
	            for(j = 0; j < neighbors.size(); j++)
	            {
	        	    k = (int)neighbors.get(j);
	        	    if(k != 0)
	        	    {
	        		    assignments[i]++;
	        	    }
	            }
	            if(assignments[i] != 0)
		        	n++;
	        }
	        else
	        	n++;
	    }
        
        System.out.println("Number of assigned deltas after one dilation is " + n);
        System.out.println();
       
        /*
		boolean complete = false;
		boolean same     = false;
		
		
		int n = 0;
		while(!complete && !same)
		{
		    for(i = 0; i < size; i++)
		    {
		        x = i % xdim;
		        y = i / xdim;
		        n = 0;
		    
		        ArrayList neighbors = new ArrayList();
		    
		        if(assignments[i] == 0)
		        {
		            neighbors = getNeighbors(assignments, x, y, xdim, ydim);
		            for(j = 0; j < neighbors.size(); j++)
		            {
		        	    k = (int)neighbors.get(j);
		        	    if(k != 0)
		        		    assignments[i]++;
		            }
		            if(assignments[i] != 0)
			        	n++;
		        }
		        else
		        	n++;
		    }
		    if(n == size)
		        complete = true;
		    else if(n == p)
		        same = true;
		    else
		    	p = n;
		}
        
		if(!complete)
			System.out.println("Function did not complete.");
		
		if(same)
			System.out.println("n and p were equal");
		else
			System.out.println("n and p were not equal");
		
		int number_unassigned = 0;
		for(i = 0; i < size; i++)
		{
			if(assignments[i] == 0)
				number_unassigned++;	
		}
		
		System.out.println("Number of pixels assigned is " + n);
		System.out.println("Number of unassigned pixels was " + number_unassigned);
		System.out.println("Number of pixels is " + size);
		System.out.println();
		*/
		
		
		
        ArrayList result = new ArrayList();
        result.add(init_value);
        result.add(delta);
        result.add(map);
        
        
        
        
        return result;
    }
    
    // Get an ideal delta set and a map of which pixels are used.
    public static ArrayList getIdealDeltasFromValues2(int src[], int xdim, int ydim)
    {
    	int size = xdim * ydim;
    	
    	int[]  delta    = new int[size];
        byte[] map      = new byte[size];
        int[]  neighbor = new int[size];
       
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
            	    	if((Math.abs(a) <= Math.abs(b)) && (Math.abs(a) <= Math.abs(c)))
            	    	{
            	    		delta[k] = src[k] - src[k + 1];
            	    		map[k]   = 4;
            	    		neighbor[k + 1]++;
            	    	}
            	    	else if((Math.abs(b) <= Math.abs(c)))
            	    	{
            	    		delta[k] = src[k] - src[k + xdim];
            	    		map[k]   = 6;	
            	    		neighbor[k + xdim]++;
            	    	}
            	    	else
            	    	{
            	    		delta[k] = src[k] - src[k + xdim + 1];	
            	    		map[k]   = 7;
            	    		neighbor[k + xdim + 1]++;
            	    	}
            	    	
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
            	    	
            	    	if((Math.abs(a) <= Math.abs(b)) && (Math.abs(a) <= Math.abs(c)) && (Math.abs(a) <= Math.abs(d)) && (Math.abs(a) <= Math.abs(e)))
            	    	{
            	    		delta[k] = src[k] - src[k - 1];
            	    		map[k]   = 3;
            	    		neighbor[k - 1]++;
            	    	}
            	    	else if((Math.abs(b) <= Math.abs(c)) && (Math.abs(b) <= Math.abs(d)) && (Math.abs(b) <= Math.abs(e)))
            	    	{
            	    		delta[k] = src[k] - src[k + 1];
            	    		map[k]   = 4;	
            	    		neighbor[k + 1]++;
            	    	}
            	    	else if((Math.abs(c) <= Math.abs(d)) && (Math.abs(c) <= Math.abs(e)))
            	    	{
            	    		delta[k] = src[k] - src[k + xdim - 1];
            	    		map[k]   = 5;
            	    		neighbor[k + xdim - 1]++;
            	    	}
            	    	else if((Math.abs(d) <= Math.abs(e)))
            	    	{
            	    		delta[k] = src[k] - src[k + xdim];
            	    		map[k] = 6;
            	    		neighbor[k + xdim]++;
            	    	}
            	    	else
            	    	{
            	    		delta[k] = src[k] - src[k + xdim + 1];
            	    		map[k] = 7;	
            	    		neighbor[k + xdim + 1]++;
            	    	}
            			
                        k++;
            		}
            		else if(j == xdim - 1)
            		{
            			 // We have a set of 3 possible deltas to use.
            	    	int a = src[k] - src[k - 1];
            	    	int b = src[k] - src[k + xdim - 1];
            	    	int c = src[k] - src[k + xdim];
            	    	
            	    	if((Math.abs(a) <= Math.abs(b)) && (Math.abs(a) <= Math.abs(c)))
            	    	{
            	    		delta[k] = src[k] - src[k - 1];
            	    		map[k]   = 3;
            	    		neighbor[k - 1]++;
            	    	}
            	    	else if((Math.abs(b) <= Math.abs(c)))
            	    	{
            	    		delta[k] = src[k] - src[k + xdim - 1];
            	    		map[k]   = 5;	
            	    		neighbor[k + xdim - 1]++;
            	    	}
            	    	else
            	    	{
            	    		delta[k] = src[k] - src[k + xdim];	
            	    		map[k]   = 6;
            	    		neighbor[k + xdim]++;
            	    	}
            	    	
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
            	    	
            	    	if((Math.abs(a) <= Math.abs(b)) && (Math.abs(a) <= Math.abs(c)) && (Math.abs(a) <= Math.abs(d)) && (Math.abs(a) <= Math.abs(e)))
            	    	{
            	    		delta[k] = src[k] - src[k - xdim];
            	    		map[k]   = 1;
            	    		neighbor[k - xdim]++;
            	    	}
            	    	else if((Math.abs(b) <= Math.abs(c)) && (Math.abs(b) <= Math.abs(d)) && (Math.abs(b) <= Math.abs(e)))
            	    	{
            	    		delta[k] = src[k] - src[k - xdim + 1];
            	    		map[k]   = 2;	
            	    		neighbor[k - xdim + 1]++;
            	    	}
            	    	else if((Math.abs(c) <= Math.abs(d)) && (Math.abs(c) <= Math.abs(e)))
            	    	{
            	    		delta[k] = src[k] - src[k + 1];
            	    		map[k]   = 4;
            	    		neighbor[k + 1]++;
            	    	}
            	    	else if((Math.abs(d) <= Math.abs(e)))
            	    	{
            	    		delta[k] = src[k] - src[k + xdim];
            	    		map[k] = 6;
            	    		neighbor[k + xdim]++;
            	    	}
            	    	else
            	    	{
            	    		delta[k] = src[k] - src[k + xdim + 1];
            	    		map[k] = 7;	
            	    		neighbor[k + xdim + 1]++;
            	    	}
            	    	
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
            	    	
            	    	
            	    	if((Math.abs(a) <= Math.abs(b)) && (Math.abs(a) <= Math.abs(c)) && (Math.abs(a) <= Math.abs(d)) && (Math.abs(a) <= Math.abs(e)) && (Math.abs(a) <= Math.abs(f)) && (Math.abs(a) <= Math.abs(g)) && (Math.abs(a) <= Math.abs(h)))
            	    	{
            	    		delta[k] = src[k] - src[k - xdim - 1];
            	    		map[k]   = 0;
            	    		neighbor[k - xdim - 1]++;
            	    	}
            	    	else if((Math.abs(b) <= Math.abs(c)) && (Math.abs(b) <= Math.abs(d)) && (Math.abs(b) <= Math.abs(e)) && (Math.abs(b) <= Math.abs(f)) && (Math.abs(b) <= Math.abs(g)) && (Math.abs(b) <= Math.abs(h)))
            	    	{
            	    		delta[k] = src[k] - src[k - xdim];
            	    		map[k]   = 1;	
            	    		neighbor[k - xdim]++;
            	    	}
            	    	else if((Math.abs(c) <= Math.abs(d)) && (Math.abs(c) <= Math.abs(e)) && (Math.abs(c) <= Math.abs(f)) && (Math.abs(c) <= Math.abs(g)) && (Math.abs(c) <= Math.abs(h)))
            	    	{
            	    		delta[k] = src[k] - src[k - xdim + 1];
            	    		map[k]   = 2;
            	    		neighbor[k - xdim + 1]++;
            	    	}
            	    	else if((Math.abs(d) <= Math.abs(e)) && (Math.abs(d) <= Math.abs(f)) && (Math.abs(d) <= Math.abs(g)) && (Math.abs(d) <= Math.abs(h)))
            	    	{
            	    		delta[k] = src[k] - src[k - 1];
            	    		map[k] = 3;
            	    		neighbor[k - 1]++;
            	    	}
            	    	else if((Math.abs(e) <= Math.abs(f)) && (Math.abs(e) <= Math.abs(g)) && (Math.abs(e) <= Math.abs(h)))
            	    	{
            	    		delta[k] = src[k] - src[k + 1];
            	    		map[k] = 4;	
            	    		neighbor[k + 1]++;
            	    	}
            	    	else if((Math.abs(f) <= Math.abs(g)) && (Math.abs(f) <= Math.abs(h)))
            	    	{
            	    		delta[k] = src[k] - src[k + xdim - 1];
            	    		map[k] = 5;	
            	    		neighbor[k + xdim - 1]++;
            	    	}
            	    	else if((Math.abs(g) <= Math.abs(h)))
            	    	{
            	    		delta[k] = src[k] - src[k + xdim];
            	    		map[k] = 6;	
            	    		neighbor[k + xdim]++;
            	    	}
            	    	else
            	    	{
            	    		delta[k] = src[k] - src[k + xdim + 1];
            	    		map[k] = 7;	
            	    		neighbor[k + xdim + 1]++;
            	    	}
            	    	
        	    	    k++;
        	    	    
            	    }
            	    else if(j == xdim - 1)
            	    {
            	    	// We have a set of 5 possible deltas to use.
            	    	int a = src[k] - src[k - xdim - 1];
            	    	int b = src[k] - src[k - xdim];
            	    	int c = src[k] - src[k - 1];
            	    	int d = src[k] - src[k + xdim - 1];
            	    	int e = src[k] - src[k + xdim];
            	    	
            	    	if((Math.abs(a) <= Math.abs(b)) && (Math.abs(a) <= Math.abs(c)) && (Math.abs(a) <= Math.abs(d)) && (Math.abs(a) <= Math.abs(e)))
            	    	{
            	    		delta[k] = src[k] - src[k - xdim - 1];
            	    		map[k]   = 0;
            	    		neighbor[k - xdim - 1]++;
            	    	}
            	    	else if((Math.abs(b) <= Math.abs(c)) && (Math.abs(b) <= Math.abs(d)) && (Math.abs(b) <= Math.abs(e)))
            	    	{
            	    		delta[k] = src[k] - src[k - xdim];
            	    		map[k]   = 1;	
            	    		neighbor[k - xdim]++;
            	    	}
            	    	else if((Math.abs(c) <= Math.abs(d)) && (Math.abs(c) <= Math.abs(e)))
            	    	{
            	    		delta[k] = src[k] - src[k - 1];
            	    		map[k]   = 4;
            	    		neighbor[k - 1]++;
            	    	}
            	    	else if((Math.abs(d) <= Math.abs(e)))
            	    	{
            	    		delta[k] = src[k] - src[k + xdim - 1];
            	    		map[k] = 5;
            	    		neighbor[k + xdim - 1]++;
            	    	}
            	    	else
            	    	{
            	    		delta[k] = src[k] - src[k + xdim];
            	    		map[k] = 6;	
            	    		neighbor[k + xdim]++;
            	    	}
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
            	    	
            	    	if((Math.abs(a) <= Math.abs(b)) && (Math.abs(a) <= Math.abs(c)))
            	    	{
            	    		delta[k] = src[k] - src[k - xdim];
            	    		map[k]   = 1;
            	    		neighbor[k - xdim]++;
            	    	}
            	    	else if((Math.abs(b) <= Math.abs(c)))
            	    	{
            	    		delta[k] = src[k] - src[k - xdim + 1];
            	    		map[k]   = 2;	
            	    		neighbor[k - xdim + 1]++;
            	    	}
            	    	else
            	    	{
            	    		delta[k] = src[k] - src[k + 1];	
            	    		map[k]   = 4;
            	    		neighbor[k + 1]++;
            	    	}
            	    	
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
            	    	
            	    	if((Math.abs(a) <= Math.abs(b)) && (Math.abs(a) <= Math.abs(c)) && (Math.abs(a) <= Math.abs(d)) && (Math.abs(a) <= Math.abs(e)))
            	    	{
            	    		delta[k] = src[k] - src[k - xdim - 1];
            	    		map[k]   = 0;
            	    		neighbor[k - xdim - 1]++;
            	    	}
            	    	else if((Math.abs(b) <= Math.abs(c)) && (Math.abs(b) <= Math.abs(d)) && (Math.abs(b) <= Math.abs(e)))
            	    	{
            	    		delta[k] = src[k] - src[k - xdim];
            	    		map[k]   = 1;	
            	    		neighbor[k - xdim]++;
            	    	}
            	    	else if((Math.abs(c) <= Math.abs(d)) && (Math.abs(c) <= Math.abs(e)))
            	    	{
            	    		delta[k] = src[k] - src[k - xdim + 1];
            	    		map[k]   = 2;
            	    		neighbor[k - xdim + 1]++;
            	    	}
            	    	else if((Math.abs(d) <= Math.abs(e)))
            	    	{
            	    		delta[k] = src[k] - src[k - 1];
            	    		map[k]   = 3;
            	    		neighbor[k - 1]++;
            	    	}
            	    	else
            	    	{
            	    		delta[k] = src[k] - src[k + 1];
            	    		map[k]   = 4;	
            	    		neighbor[k + 1]++;
            	    	}
            			
                        k++;
            		}
            		else if(j == xdim - 1)
            		{
            			 // We have a set of 3 possible deltas to use.
            	    	int a = src[k] - src[k - xdim - 1];
            	    	int b = src[k] - src[k - xdim];
            	    	int c = src[k] - src[k - 1];
            	    	
            	    	if((Math.abs(a) <= Math.abs(b)) && (Math.abs(a) <= Math.abs(c)))
            	    	{
            	    		delta[k] = src[k] - src[k - xdim - 1];
            	    		map[k]   = 0;
            	    		neighbor[k - xdim - 1]++;
            	    	}
            	    	else if((Math.abs(b) <= Math.abs(c)))
            	    	{
            	    		delta[k] = src[k] - src[k - xdim];
            	    		map[k]   = 1;	
            	    		neighbor[k - xdim]++;
            	    	}
            	    	else
            	    	{
            	    		delta[k] = src[k] - src[k - 1];	
            	    		map[k]   = 3;
            	    		neighbor[k - 1]++;
            	    	}
            	    	
            			k++;	
            		}
            	}	
        	}
        }
        
        // Index and value of most connected pixel.
        int max_index = 0;
        int max_value = src[0];
        
        for(int i = 1; i < size; i++)
        {
        	if(neighbor[i] > neighbor[max_index])
        	{
        		max_index = i;
        		max_value = src[i];
        		//System.out.println("Max number is " + neighbor[i]);
        	}
        }
        
        //System.out.println("Most connected value is " + neighbor[max_index]);
        ArrayList result = new ArrayList();
        result.add(max_value);
        result.add(max_index);
        result.add(delta);
        result.add(map);
        
        //System.out.println("Most connected value is " + max_value);
        //System.out.println("Most connected index is " + max_index);
        //System.out.println("Returning from delta function.");
        //System.out.println();
        return result;
    }
    
    
    
    
    public static int[] getValuesFromIdealDeltas2(int [] src, int xdim, int ydim, int init_value, byte [] map)
    {
        int size = xdim * ydim;
        
        int []     dst                = new int[size]; 
     
        int x = xdim / 2;
        int y = ydim / 2;
        
        int i = y * xdim + x;
        
        if(src[i] != 0)
        	System.out.println("Wrong code.");
        
        dst[i]         = init_value; 
        int next_pixel = map[i];
        
        int p = 1;
        int current_value = init_value;
        while(p < size)
        {
        	int j = 0;
        	if(next_pixel == 0)
        	    j = i - xdim - 1;
        	else if(next_pixel == 1)
        		j = i - xdim;
        	else if(next_pixel == 2)
        		j = i - xdim + 1;
        	else if(next_pixel == 3)
        		j = i - 1;
        	else if(next_pixel == 4)
        		j = i + 1;
        	else if(next_pixel == 5)
        		j = i + xdim - 1;
        	else if(next_pixel == 6)
        		j = i + xdim;
        	else if(next_pixel == 1)
        		j = i + xdim + 1;
        	try
        	{
        	    dst[j] = current_value + src[j];
        	    current_value = dst[j];
        	    next_pixel = map[j];
        	}
        	catch(Exception e)
        	{
        	    System.out.println("i = " + i + ", j = " + j + ", p = " + p);
        	    System.exit(1);
        	}
        	
        	i = j;
        	p++;
        }
        
        return dst;
    }

   
    // Get an ideal delta set and a map of which pixels are used.
    public static ArrayList getIdealDeltasFromValues3(int src[], int xdim, int ydim, ArrayList delta_list)
    {
    	int size = xdim * ydim;
    	
    	int[]     delta       = new int[size];
        byte[]    map         = new byte[size];
        boolean[] is_assigned = new boolean[size];
        
        int x = xdim / 2;
        int y = ydim / 2;
        int i = y * xdim + x;
        
        int initial_value = src[i];
        delta[i]          = 7;
        map[i]            = 0;
        is_assigned[i]    = true;
        
        
        int [][] table = (int [][])delta_list.get(i);
        
        int current_delta = table[0][0];
        int location      = table[0][1];
        
        System.out.println("Smallest delta for center pixel is " + current_delta + " with pixel " + location);
        
        int j = getNeighborIndex(x, y, xdim, location);
        int k = getInverseLocation(location);
        
        map[j] = (byte)k;
        delta[j] = -current_delta;
        is_assigned[j] = true;
        int number_of_pixels = 2;
        boolean unassigned_neighbors = true;
       
        while(unassigned_neighbors && number_of_pixels < size)
        {
        	i = j;
        	table = (int [][])delta_list.get(i);
        	
        	boolean same_location = true;
        	k = 0;
        	while(same_location)
        	{
        		y = i / xdim;
        		x = i % xdim;
        		if(k < table.length)
        		{
        			current_delta = table[k][0];
        	        int new_location  = table[k][1];
        	        if(new_location == map[j])
            	    	k++;
        	        else
        	        {
        	            int m = getNeighborIndex(x, y, xdim, new_location);
        	            if(is_assigned[m])
        	        	    k++;
        	            else
        	            {
        	                int inverse_location = getInverseLocation(new_location);
        	                map[m]               = (byte)inverse_location;
        	                delta[m]             = -current_delta;
        	                same_location        = false;
        	                j                    = m;	
        	                number_of_pixels++;
        	                System.out.println("Assigned delta " + m);
        	                System.out.println("Number of pixels is " + number_of_pixels);
        	                System.out.println();
        	            }
        	        }
        		}
        	    else
        	    {
        	    	same_location = false;
        	    	unassigned_neighbors = false;
        	    }
        	}
        }
        
        ArrayList result = new ArrayList();
        return result;
    }
    
    public static int[] getValuesFromIdealDeltas3(int [] src, int xdim, int ydim, int init_value, int init_index, byte [] map)
    {
        int size = xdim * ydim;
        
        int []     dst                = new int[size]; 
        boolean [] is_assigned        = new boolean[size];
        boolean [] neighbors_assigned = new boolean[size];
        boolean    complete           = false;
        boolean    same_result        = false;
        
        dst[init_index]         = init_value; 
        is_assigned[init_index] = true;  
        int current_number      = 1;
        int previous_number     = 1;
        
        for(int i = 0; i < size; i++)
        {
        	if(is_assigned[i] == true)
        	    System.out.println("Value " + i + " has been assigned.");
        }
        
        
        int k = 0;
        for(int i = 0; i < ydim; i++)
        {
        	for(int j = 0; j < xdim; j++)
        	{
        		if(is_assigned[k])
        			System.out.println("Value " + k + " has been assigned.");	
        		k++;
        	}			
        }
        
        while(!complete && !same_result)
        {
        	System.out.println("Got here 0.");
        	k = 0;
            for(int i = 0; i < ydim; i++)
            {
                if(i == 0) 
                {
                    for(int j = 0; j < xdim; j++)	
                    {
                        if(j == 0)	
                        {
                        	if(is_assigned[k])
                        		System.out.println("Got here 1.");		
                            if(is_assigned[k] && !neighbors_assigned[k])
                            {
                            	// Check 3 neighbors.
                            	int m = k + xdim + 1;
                            	int n = k + xdim;
                            	int p = k + 1;
                            	
                            	int a = 0;
                            	int b = 1;
                            	int c = 3;
                            	
                            	if(!is_assigned[m])
                            	{
                            	    if(map[m] == a)	
                            	    {
                            	        dst[m] = dst[k] - src[m];
                            	        is_assigned[m] = true;
                            	    }
                            	}
                            	
                            	if(!is_assigned[n])
                            	{
                            		if(map[n] == b)	
                            	    {
                            	        dst[n] = dst[k] - src[n];
                            	        is_assigned[n] = true;
                            	    }	
                            	}
                            	
                            	if(!is_assigned[p])
                            	{
                            		if(map[p] == c)	
                            	    {
                            		    dst[p] = dst[k] - src[p];
                        	            is_assigned[p] = true;
                            	    }
                            	}
                            	
                            	if(is_assigned[m] && is_assigned[n] && is_assigned[p])
                            		neighbors_assigned[k] = true;	
                            }
                            k++;
                        }
                        else if(j < xdim - 1)
                        {
                        	if(is_assigned[k])
                        		System.out.println("Got here 2.");	
                        	if(is_assigned[k] && !neighbors_assigned[k])
                            {
                            	// Check 5 neighbors.
                        		
                        		int m = k + xdim + 1;
                        		int n = k + xdim;
                        		int p = k + xdim - 1;
                        		int q = k + 1;
                        		int r = k - 1;
                        		
                        		int a = 0;
                        		int b = 1;
                        		int c = 2;
                        		int d = 3;
                        		int e = 4;
                        		
                    	    	if(!is_assigned[m])
                            	{
                            	    if(map[m] == a)	
                            	    {
                            	        dst[m]         = dst[k] - src[m];
                            	        is_assigned[m] = true;
                            	    }
                            	}
                    	    	
                            	if(!is_assigned[n])
                            	{
                            		if(map[n] == b)	
                            	    {
                            	        dst[n]         = dst[k] - src[n];
                            	        is_assigned[n] = true;
                            	    }	
                            	}
                            	
                            	if(!is_assigned[p])
                            	{
                            		if(map[p] == c)	
                            	    {
                            		    dst[p]         = dst[k] - src[p];
                        	            is_assigned[p] = true;
                            	    }
                            	}
                            	
                            	if(!is_assigned[q])
                            	{
                            		if(map[q] == d)	
                            	    {
                            		    dst[q]         = dst[k] - src[q];
                        	            is_assigned[q] = true;
                            	    }
                            	}
                            	
                            	if(!is_assigned[r])
                            	{
                            		if(map[r] == e)	
                            	    {
                            		    dst[r]         = dst[k] - src[r];
                        	            is_assigned[r] = true;
                            	    }
                            	}
                            	
                            	if(is_assigned[m] && is_assigned[n] && is_assigned[p] && is_assigned[q] && is_assigned[r])
                            		neighbors_assigned[k] = true;	
                            }   
                        	k++;
                        }
                        else
                        {
                        	if(is_assigned[k])
                        		System.out.println("Got here 3.");	
                        	if(is_assigned[k] && !neighbors_assigned[k])
                            {
                        		if(is_assigned[k] && !neighbors_assigned[k])
                                {
                        			System.out.println("Got here 3.");
                        			// Check 3 neighbors.
                        			int m = k + xdim;
                        			int n = k + xdim - 1;
                        			int p = k - 1;
                        			
                        			int a = 1;
                        			int b = 2;
                        			int c = 4;
                        			
                                	if(!is_assigned[m])
                                	{
                                	    if(map[m] == a)	
                                	    {
                                	        dst[m]         = dst[k] - src[m];
                                	        is_assigned[m] = true;
                                	    }
                                	}
                                	
                                	if(!is_assigned[n])
                                	{
                                		if(map[n] == b)	
                                	    {
                                	        dst[n] = dst[k] - src[n];
                                	        is_assigned[n] = true;
                                	    }	
                                	}
                                	
                                	if(!is_assigned[p])
                                	{
                                		if(map[p] == c)	
                                	    {
                                		    dst[p] = dst[k] - src[p];
                            	            is_assigned[p] = true;
                                	    }
                                	}
                                	
                                	if(is_assigned[k - 1] && is_assigned[k + xdim - 1] && is_assigned[k + xdim])
                                		neighbors_assigned[k] = true;	
                                }
                        		k++;
                            }	
                        }
                    }
                }
                else if(i < ydim - 1)
                {
                	for(int j = 0; j < xdim; j++)	
                    {
                        if(j == 0)	
                        {
                        	if(is_assigned[k])
                        		System.out.println("Got here 4.");	
                        	if(is_assigned[k] && !neighbors_assigned[k])
                            {
                            	// Check 5 neighbors.
                        		System.out.println("Got here 4.");
                        		int m = k + xdim + 1;
                        		int n = k + xdim;
                        		int p = k + 1;
                        		int q = k - xdim + 1;
                        		int r = k - xdim;
                        		
                        		int a = 0;
                        		int b = 1;
                        		int c = 3;
                        		int d = 5;
                        		int e = 6;
                        		
                        		if(!is_assigned[m])
                            	{
                            	    if(map[m] == a)	
                            	    {
                            	        dst[m]         = dst[k] - src[m];
                            	        is_assigned[m] = true;
                            	    }
                            	}
                        		
                            	if(!is_assigned[n])
                            	{
                            		if(map[n] == b)	
                            	    {
                            	        dst[n]         = dst[k] - src[n];
                            	        is_assigned[n] = true;
                            	    }	
                            	}
                            	
                            	if(!is_assigned[p])
                            	{
                            		if(map[p] == c)	
                            	    {
                            		    dst[p]         = dst[k] - src[p];
                        	            is_assigned[p] = true;
                            	    }
                            	}
                            	
                            	if(!is_assigned[q])
                            	{
                            		if(map[q] == d)	
                            	    {
                            		    dst[q]         = dst[k] - src[q];
                        	            is_assigned[q] = true;
                            	    }
                            	}
                            	
                            	if(!is_assigned[r])
                            	{
                            		if(map[r] == e)	
                            	    {
                            		    dst[r]         = dst[k] - src[r];
                        	            is_assigned[r] = true;
                            	    }
                            	}
                            	
                            	if(is_assigned[m] && is_assigned[n] && is_assigned[p] && is_assigned[q] && is_assigned[r])
                            		neighbors_assigned[k] = true;	
                            } 
                        	k++;
                        }
                        else if(j < xdim - 1)
                        {
                        	if(is_assigned[k] && !neighbors_assigned[k])
                            {
                            	// Check 8 neighbors.
                        		System.out.println("Got here 5.");
                    	    	int m = k + xdim + 1;
                    	    	int n = k + xdim;
                    	    	int p = k + xdim - 1;
                    	    	int q = k + 1;
                    	    	int r = k - 1;
                    	    	int s = k - xdim + 1;
                    	    	int t = k - xdim;
                    	    	int u = k - xdim - 1;
                    	    	
                    	    	int a = 0;
                    	    	int b = 1;
                    	    	int c = 2;
                    	    	int d = 3;
                    	    	int e = 4;
                    	    	int f = 5;
                    	    	int g = 6;
                    	    	int h = 7;
                    	    	
                    	    	if(!is_assigned[m])
                            	{
                            		if(map[m] == a)	
                            	    {
                            	        dst[m]         = dst[k] - src[m];
                            	        is_assigned[m] = true;
                            	    }	
                            	}
                    	    	
                    	    	if(!is_assigned[n])
                            	{
                            		if(map[n] == b)	
                            	    {
                            	        dst[n]         = dst[k] - src[n];
                            	        is_assigned[n] = true;
                            	    }	
                            	}
                    	    	
                    	    	if(!is_assigned[p])
                            	{
                            		if(map[p] == c)	
                            	    {
                            	        dst[p]         = dst[k] - src[p];
                            	        is_assigned[p] = true;
                            	    }	
                            	}
                    	    
                    	    	if(!is_assigned[q])
                            	{
                            		if(map[q] == d)	
                            	    {
                            	        dst[q]         = dst[k] - src[q];
                            	        is_assigned[q] = true;
                            	    }	
                            	}
                    
                    	    	if(!is_assigned[r])
                            	{
                            		if(map[r] == e)	
                            	    {
                            	        dst[r]         = dst[k] - src[r];
                            	        is_assigned[r] = true;
                            	    }	
                            	}
                    	    
                    	    	if(!is_assigned[s])
                            	{
                            		if(map[s] == f)	
                            	    {
                            	        dst[s]         = dst[k] - src[s];
                            	        is_assigned[s] = true;
                            	    }	
                            	}
                    	    
                    	    	if(!is_assigned[t])
                            	{
                            		if(map[t] == g)	
                            	    {
                            	        dst[t]         = dst[k] - src[t];
                            	        is_assigned[t] = true;
                            	    }	
                            	}
                    	    	
                    	    	
                    	    	if(!is_assigned[u])
                            	{
                            		if(map[u] == h)	
                            	    {
                            	        dst[u]         = dst[k] - src[u];
                            	        is_assigned[u] = true;
                            	    }	
                            	}
                    	    	
                            	if(is_assigned[m] && is_assigned[n] && is_assigned[p] && is_assigned[q] && is_assigned[r] && is_assigned[s] && is_assigned[t] && is_assigned[u])
                            		neighbors_assigned[k] = true;	
                            } 
                        	k++;
                        }
                        else
                        {
                        	if(is_assigned[k])
                        		System.out.println("Got here 6.");
                        	if(is_assigned[k] && !neighbors_assigned[k])
                            {
                            	// Check 5 neighbors.
                        		System.out.println("Got here 6.");
                        		int m = k - xdim + 1;
                        		int n = k - xdim;
                        		int p = k - xdim - 1;
                        		int q = k + 1;
                        		int r = k - 1;
                        		
                        		int a = 0;
                        		int b = 1;
                        		int c = 2;
                        	    int d = 3;
                        	    int e = 4;
                        		
                        		if(!is_assigned[m])
                            	{
                            	    if(map[m] == a)	
                            	    {
                            	        dst[m]         = dst[k] - src[m];
                            	        is_assigned[m] = true;
                            	    }
                            	}
                        		
                            	if(!is_assigned[n])
                            	{
                            		if(map[n] == b)	
                            	    {
                            	        dst[n]         = dst[k] - src[n];
                            	        is_assigned[n] = true;
                            	    }	
                            	}
                            	
                            	if(!is_assigned[p])
                            	{
                            		if(map[p] == c)	
                            	    {
                            		    dst[p]         = dst[k] - src[p];
                        	            is_assigned[p] = true;
                            	    }
                            	}
                            	
                            	if(!is_assigned[q])
                            	{
                            		if(map[q] == d)	
                            	    {
                            		    dst[q]         = dst[k] - src[q];
                        	            is_assigned[q] = true;
                            	    }
                            	}
                            	if(!is_assigned[r])
                            	{
                            		if(map[r] == e)	
                            	    {
                            		    dst[r]         = dst[k] - src[r];
                        	            is_assigned[r] = true;
                            	    }
                            	}
                            	
                            	if(is_assigned[m] && is_assigned[n] && is_assigned[p] && is_assigned[q] && is_assigned[r])
                            		neighbors_assigned[k] = true;	
                            } 
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
                        	if(is_assigned[k] && !neighbors_assigned[k])
                            {
                        		// Check 3 neighbors.
                        		
                        		int m = k + 1;
                        		int n = k - xdim;
                        		int p = k - xdim + 1;
                        		
                        		int a = 3;
                        		int b = 6;
                        		int c = 7;
                        		
                    	    	if(!is_assigned[m])
                            	{
                            		if(map[m] == a)	
                            	    {
                            		    dst[m] = dst[k] - src[m];
                        	            is_assigned[m] = true;
                            	    }
                            	}
                    	    	
                    	    	if(!is_assigned[n])
                            	{
                            	    if(map[n] == b)	
                            	    {
                            	        dst[n] = dst[k] - src[n];
                            	        is_assigned[n] = true;
                            	    }
                            	}
                    	    	
                            	if(!is_assigned[p])
                            	{
                            		if(map[p] == c)	
                            	    {
                            	        dst[p] = dst[k] - src[p];
                            	        is_assigned[p] = true;
                            	    }	
                            	}
                            	
                            	if(is_assigned[m] && is_assigned[n] && is_assigned[p])
                            		neighbors_assigned[k] = true;	
                            } 
                        	k++;
                        }
                        else if(j < xdim - 1)
                        {
                        	if(is_assigned[k])
                        		System.out.println("Got here 8.");
                        	if(is_assigned[k]  && !neighbors_assigned[k])
                            {
                        		// Check 5 neighbors.
                        		System.out.println("Got here 8.");
                        		int m = k + 1;
                        		int n = k - 1;
                        		int p = k - xdim + 1;
                        		int q = k - xdim;
                    			int r = k - xdim - 1;
                    			
                    			int a = 3;
                    			int b = 4;
                    			int c = 5;
                    			int d = 6;
                    			int e = 7;
                    	    	
                    			if(!is_assigned[m])
                            	{
                            	    if(map[m] == a)	
                            	    {
                            	        dst[m]         = dst[k] - src[m];
                            	        is_assigned[m] = true;
                            	    }
                            	}
                        		
                            	if(!is_assigned[n])
                            	{
                            		if(map[n] == b)	
                            	    {
                            	        dst[n]         = dst[k] - src[n];
                            	        is_assigned[n] = true;
                            	    }	
                            	}
                            	
                            	if(!is_assigned[p])
                            	{
                            		if(map[p] == c)	
                            	    {
                            		    dst[p]         = dst[k] - src[p];
                        	            is_assigned[p] = true;
                            	    }
                            	}
                            	
                            	if(!is_assigned[q])
                            	{
                            		if(map[q] == d)	
                            	    {
                            		    dst[q]         = dst[k] - src[q];
                        	            is_assigned[k - xdim] = true;
                            	    }
                            	}
                            	if(!is_assigned[r])
                            	{
                            		if(map[r] == e)	
                            	    {
                            		    dst[r]         = dst[k] - src[r];
                        	            is_assigned[r] = true;
                            	    }
                            	}
                            	
                            	if(is_assigned[m] && is_assigned[n] && is_assigned[p] && is_assigned[q] && is_assigned[r])
                            		neighbors_assigned[k] = true;		
                            } 
                        	k++;
                        }
                        else
                        {
                        	if(is_assigned[k] && !neighbors_assigned[k])
                            {
                        		// Check 3 neighbors.
                        		
                        		int m = k - 1;
                        		int n = k - xdim;
                    	    	int p = k - xdim - 1;
                    	    	
                    	    	int a = 4;
                    	    	int b = 6;
                    	    	int c = 7;
                    	    	
                    	    	if(!is_assigned[m])
                            	{
                            		if(map[m] == a)	
                            	    {
                            	        dst[m]         = dst[k] - src[m];
                            	        is_assigned[m] = true;
                            	    }	
                            	}
                    	    	
                    	    	if(!is_assigned[n])
                            	{
                            	    if(map[n] == b)	
                            	    {
                            	        dst[n]         = dst[k] - src[n];
                            	        is_assigned[n] = true;
                            	    }
                            	}
                    	    	
                    	    	if(!is_assigned[p])
                            	{
                            		if(map[p] == c)	
                            	    {
                            		    dst[p]         = dst[k] - src[p];
                        	            is_assigned[p] = true;
                            	    }
                            	}
                    	    	
                            	if(is_assigned[m] && is_assigned[n] && is_assigned[p])
                            		neighbors_assigned[k] = true;
                            } 
                        	k++;
                        }
                    }	
                }
            }
            
            current_number = 0;
            for(int i = 0; i < size; i++)
            	if(is_assigned[i])
            		current_number++;
            if(current_number == size)
            	complete = true;
            else if(current_number == previous_number)
            	same_result = true;
            previous_number = current_number;
            System.out.println("Current number is " + current_number);
            	
        }
        
        if(complete)
        	System.out.println("Function completed.");
        else
        {
        	System.out.println("Function did not complete.");
        	System.out.println(current_number + " values out of " + size + " were assigned.");
        }
        return dst;
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