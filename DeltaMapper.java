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
    
    // Get an ideal delta set and a map of which pixels are used.
    public static ArrayList getIdealDeltasFromValues2(int src[], int xdim, int ydim)
    {
        int[]    dst      = new int[xdim * ydim];
        
        
        int size = (xdim - 2) * (ydim - 1);
        
        byte[]    map      = new byte[size];
        byte[][]  neighbor = new byte[size][4];
       
      
        int init_value     = src[0];
        int value          = init_value;
        int delta          = 0;
        
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
            			k++;
            	    }
            		else
            		{
            		    delta        = src[k] - value;
                        value       += delta;
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
            	    	delta          = src[k] - init_value;
            	    	init_value     = src[k];
            	    	k++;
            	    }
            	    else if(j < xdim - 1)
            	    {
            	    	//We have a set of 4 possible deltas to use.
            	    	int a = src[k] - src[k - 1];
            	    	int b = src[k] - src[k - xdim];
            	    	int c = src[k] - src[k - xdim - 1];
            	    	int d = src[k] - src[k - xdim + 1];
            	    	
            	    	if(Math.abs(a) <= Math.abs(b) && Math.abs(a) <= Math.abs(c) && Math.abs(a) <= Math.abs(d))
            	    	    neighbor[m][0] = 1;
            	    	if(Math.abs(b) <= Math.abs(a) && Math.abs(b) <= Math.abs(c) && Math.abs(b) <= Math.abs(d))
            	    		neighbor[m][1] = 1;
            	    	if(Math.abs(c) <= Math.abs(a) && Math.abs(c) <= Math.abs(b) && Math.abs(c) <= Math.abs(d))
            	    		neighbor[m][2] = 1;
            	    	if(Math.abs(d) <= Math.abs(a) && Math.abs(d) <= Math.abs(b) && Math.abs(d) <= Math.abs(c))
            	    		neighbor[m][3] = 1;
        	    	    k++;
        	    	    m++;
            	    }
            	    else
            	    {
        	    	    k++;	
            	    }
                }
        	}
        }
        
        int [] total = new int[4];
        for(int i = 0; i < size; i++)
        {
            for(int j = 0; j < 4; j++)	
            	total[j] += neighbor[i][j];
        }
        System.out.println();
        Hashtable rank_table = new Hashtable();
        ArrayList key_list   = new ArrayList();
        boolean[] is_assigned = new boolean[size];
        for(int i = 0; i < 4; i++)
        {
        	key_list.add(total[i]);
        	rank_table.put(total[i], i);
        	System.out.println(i + " " + total[i]);
        }
        System.out.println();
        Collections.sort(key_list, Collections.reverseOrder());
        for(int i = 0; i < 4; i++)
        {
        	int key   = (int)key_list.get(i);
        	int index = (int)rank_table.get(key);
        	System.out.println(index + " " + key);
        	for(int j = 0; j < size; j++)
        	{
        		if(neighbor[j][index] == 1 && !is_assigned[j])
        		{
        			map[j] = (byte)index;
        		    is_assigned[j] = true;
        		}
        		
        		if(neighbor[j][index] != 0 && neighbor[j][index] != 1)
        		{
        			System.out.println("Unexpected value.");
        		}
        	}
        }
        
        boolean all_assigned = true;
        for(int i = 0; i < size; i++)
        {
        	if(is_assigned[i] == false)
        		all_assigned = false;
        }
        
        init_value     = src[0];
        value          = init_value;
        delta          = 0;
        sum            = 0;
        
        k = 0;
        m = 0;
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
            			k++;
            	    }
            		else
            		{
            		    delta        = src[k] - value;
                        value       += delta;
                        dst[k]       = delta;
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
            	    	delta          = src[k] - init_value;
            	    	init_value     = src[k];
            	    	dst[k]         = delta;
            	    	sum           += Math.abs(delta);
            	    	k++;
            	    }
            	    else if(j < xdim - 1)
            	    {
            	    	int a = src[k] - src[k - 1];
            	    	int b = src[k] - src[k - xdim];
            	    	int c = src[k] - src[k - xdim - 1];
            	    	int d = src[k] - src[k - xdim + 1];
            	    	
            	    	if(map[m] == 0)
            	    		delta = a;
            	    	else if(map[m] == 1)
            	    		delta = b;
            	    	else if(map[m] == 2)
            	    		delta = c;
            	    	else if(map[m] == 3)
            	    		delta = d;
            	    	
            	    	dst[k] = delta;	
            	    	sum   += Math.abs(delta);
        	    	    k++;
        	    	    m++;
            	    }
            	    else
            	    {
            	    	delta  = src[k] - src[k - 1];
            	    	dst[k] = delta;
            	    	sum   += Math.abs(delta);
        	    	    k++;	
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
 
    
    public static int[] getValuesFromIdealDeltas2(int [] src, int xdim, int ydim, int init_value, byte [] map)
    {
    	int[] dst = new int[xdim * ydim];
        dst[0] = init_value;
        int value = init_value;
        
        if(src[0] != 6)
        	System.out.println("Wrong code.");
        
        for(int i = 1; i < xdim; i++)
        {
        	value += src[i];
        	dst[i] = value;
        }
        
        int m = 0;
        for(int i = 1; i < ydim; i++)
        {
            for(int j = 0; j < xdim; j++)	
            {
            	int k = i * xdim + j;
            	if(j == 0)
            	{
            	    init_value += src[k];
            	    dst[k]     = init_value;
            	    value      = init_value;
            	}
            	else if(j < xdim - 1)
            	{
            		int n = map[m]; 
            		if(n == 0)
            		    value = dst[k - 1];
            		else if(n == 1)
            		    value = dst[k - xdim];
            		else if(n == 2)
            		    value = dst[k - xdim - 1];
            		else if(n == 3)
            			value = dst[k - xdim + 1];
            	    value += src[i * xdim + j];
            	    dst[k] = value;
            	    m++;
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