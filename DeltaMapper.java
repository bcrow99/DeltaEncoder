import java.util.*;
import java.util.zip.*;
import java.lang.Math.*;

public class DeltaMapper
{
	public static int[] getDifference(int src1[], int src2[])
	{
		int length = src1.length;
		int [] difference = new int[length];
		
		// Could throw an exception here, but will
		// just return uninitialized array the same length
		// as src1.
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
		int [] difference = new int[length];
		
		if(src2.length == length)  // else return uninitialzed array.
		{
		    for(int i = 0; i < length; i++)
		    {
			    difference[i] = src1[i] + src2[i];
		    }
		}
		return(difference);
	}
	
	public static int[] getHistogram(int value[], int value_range, int value_min)
	{
	    int [] histogram = new int[value_range];
	    for(int i = 0; i < value_range; i++)
	    	histogram[i] = 0;
	    for(int i = 0; i < value.length; i++)
	    {
	    	int j = value[i] - value_min;
	    	histogram[j]++;
	    }
	    return histogram;
	}
	
	public static ArrayList getHistogram(int value[])
	{  
	    int value_min = value[0];
	    int value_max = value[0];
	    for(int i = 0; i < value.length; i++)
	    {
	    	if(value[i] > value_max)
	    		value_max = value[i];
	    	if(value[i] < value_min)
	    		value_min = value[i];
	    }
	    int value_range = value_max - value_min + 1;
	    int [] histogram = new int[value_range];
	    for(int i = 0; i < value_range; i++)
	    	histogram[i] = 0;
	    for(int i = 0; i < value.length; i++)
	    {
	    	int j = value[i] - value_min;
	    	histogram[j]++;
	    }
	    
	    ArrayList histogram_list = new ArrayList();
	    histogram_list.add(value_min);
	    histogram_list.add(histogram);
	    return histogram_list;
	}
	
	public static int[] getRankTable(int histogram[])
	{
		ArrayList key_list     = new ArrayList();
	    Hashtable rank_table   = new Hashtable();
	    for(int i = 0; i < histogram.length; i++)
	    {
	    	double key = histogram[i];
	    	while(rank_table.containsKey(key))
	    	{
	    		key +=.001;
	    	}
	    	rank_table.put(key, i);
	    	key_list.add(key);
	    }
	    Collections.sort(key_list);
	    
	    int random_lut[] = new int[histogram.length];
	    int k     = -1;
	    for(int i = histogram.length - 1; i >= 0; i--)
	    {
	    	double key = (double)key_list.get(i);
	    	int    j   = (int)rank_table.get(key);
	    	random_lut[j]   = ++k;
	    }	
	    return random_lut;
	}
	
	
    public static int[] getValuesFromDeltas(int src[], int xdim, int ydim, int init_value)
    {
        if(src[0] == 0)
        {
        	//System.out.println("Type of delta is horizontal.");
        	int [] value = getValuesFromDeltas1(src, xdim, ydim, init_value);
        	
        	return value;
        }
        else if(src[0] == 1)
        {
        	//System.out.println("Type of delta is vertical.");
        	int [] value = getValuesFromDeltas2(src, xdim, ydim, init_value);
        	return value;   	
        }
        else if(src[0] == 2)
        {
        	//System.out.println("Type of delta is paeth.");
        	int [] value = getValuesFromDeltas3(src, xdim, ydim, init_value);
        	return value;   	
        }
        else
        {
        	// We could throw an exception here but will just return an 
        	// uninitialized array.
        	System.out.println("Type of delta undefined.");
        	int [] value = new int[xdim * ydim];
        	return value;
        }
        	
    }
    
    public static ArrayList getDeltasFromValues1(int src[], int xdim, int ydim)
    {
        int[] dst            = new int[xdim * ydim];
        int   sum            = 0;
        int   init_value     = src[0];
        int   value          = init_value;
         
        int k     = 0;
        for(int i = 0; i < ydim; i++)
        {
        	// We set the first value to zero to mark the type of deltas as horizontal.
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
    
    public static int[] getValuesFromDeltas1(int src[],int xdim, int ydim, int init_value)
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
    
    // These functions use vertical deltas.
    public static ArrayList getDeltasFromValues2(int src[], int xdim, int ydim)
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

    public static int[] getValuesFromDeltas2(int src[], int xdim, int ydim, int init_value)
    {
    	int[] dst = new int[xdim * ydim];
        dst[0] = init_value;
        int value = init_value;
        
        // We know the first value in the source data is used to code the type of deltas.
        // Skip 0 and start with 1.
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
    
    public static int getPaethSum(int src[], int xdim, int ydim)
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
            	    	{
            	    	    delta = horizontal_delta;
            	    	
            	    	}
            	    	else if(delta_b <= delta_c)
            	    	{
            	    	    delta = vertical_delta;
            	    	}
            	    	else
            	    	{
            	    	    delta = diagonal_delta;
            	    	}
            	    	k++;
            	    	sum += Math.abs(delta);
            	    }
                }
        	}
        }
        return sum;
    }

    public static int getPaethSum2(int src[], int xdim, int ydim, int interval)
    {
        int sum            = 0;
        for(int i = 1; i < ydim; i++)
        {
        	int k = i * xdim + 1;
        	for(int j = 1; j < xdim; j += interval)
            {
                int a = src[k - 1];
            	int b = src[k - xdim];
            	int c = src[k - xdim - 1];
            	int d = a + b - c;
            	int e = src[k];
            	
            	int delta_a = Math.abs(a - d);
            	int delta_b = Math.abs(b - d);
            	int delta_c = Math.abs(c - d);
            	    	   	
            	if(delta_a <= delta_b && delta_a <= delta_c)
            		sum += Math.abs(e - a);
            	else if(delta_b <= delta_c)
            	    sum += Math.abs(e - b);
            	else
            	    sum += Math.abs(e - c);
            	
            	k += interval;  	
        	}
        }
        return sum;
    }

    // These functions use the horizontal, vertical, or diagonal
    // deltas depending on the result of a convolution. 
    // Not using the sum anymore.
    
    // The paeth filter usually works better than vertical or horizontal deltas,
    // but still does not do as well as simply choosing the smallest delta.
    // The problem is choosing the smallest delta requires keeping a state
    // that seems to amount to more than the sum of paeth deltas minus the sum of 
    // ideal deltas.
    public static ArrayList getDeltasFromValues3(int src[], int xdim, int ydim)
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
            		    // Setting the first value to 0 to mark the delta type paeth.
            	    	// Since we're only using one type of delta, we don't really
            	    	// have a use for the first delta as a code word.
            			dst[k++] = 0;
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
            	    	// We dont have a horizontal delta or diagonal delta for our paeth filter,
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

    public static int[] getValuesFromDeltas3(int src[], int xdim, int ydim, int init_value)
    {
    	int[] dst = new int[xdim * ydim];
        dst[0]    = init_value;
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
    public static ArrayList getDeltasFromValues4(int src[], int xdim, int ydim)
    {
        int[]  dst        = new int[xdim * ydim];
        int[] direction   = new int[xdim * ydim];
        
        int init_value     = src[0];
        int value          = init_value;
        int delta          = 0;
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
            			dst[k]       = 3;
            			direction[k] = 0;
            			k++;
            	    }
            		else
            		{
            			// We don't have an upper or upper diagonal delta to check
            			// in the first row, so we just use horizontal deltas.
            		    delta        = src[k] - value;
                        value       += delta;
                        dst[k]       = delta;
                        direction[k] = 0;
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
            	    	// We dont have a horizontal delta or diagonal delta to check,
            	    	// so we just use a vertical delta, and reset our init value.
            	    	delta        = src[k] - init_value;
            	    	init_value   = src[k];
            	    	dst[k]       = delta;
            	    	direction[k] = 1;
            	    	sum          += Math.abs(delta);
            	    	k++;
            	    }
            	    else
            	    {
            	    	// Now we have a set of 3 possible pixels to use.
            	    	int a = src[k] - src[k - 1];
            	    	int b = src[k] - src[k - xdim];
            	    	int c = src[k] - src[k - xdim - 1];
            	    	
            	    	if(Math.abs(a) <= Math.abs(b) && Math.abs(a) <= Math.abs(c))
            	    	{
            	    		delta        = a;
            	    	    dst[k]       = delta;
            	    	    direction[k] = 0;
            	    	    sum         += Math.abs(delta);
            	    	    k++;
            	    	}
            	    	else if(Math.abs(b)<= Math.abs(c))
            	    	{
            	    		delta        = b;
            	    		dst[k]       = delta;
            	    	    direction[k] = 1;
            	    	    sum         += Math.abs(delta);
            	    	    k++;
            	    	}
            	    	else
            	    	{
            	    		delta        = c;
            	    		dst[k]       = delta;
            	    	    direction[k] = 2;
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
        result.add(direction);
        return result;
    }

    public static int[] getValuesFromDeltas4(int [] src, int xdim, int ydim, int init_value, int [] direction)
    {
    	int[] dst = new int[xdim * ydim];
        dst[0] = init_value;
        int value = init_value;
        
        if(src[0] != 3)
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
            		int current_direction = direction[i * xdim + j]; 
            		if(current_direction == 0)
            		    value = dst[i * xdim + j - 1];
            		else if(current_direction == 1)
            		    value = dst[(i - 1) * xdim + j];
            		else if(current_direction == 2)
            		    value = dst[(i - 1) * xdim + j - 1];
            	    value += src[i * xdim + j];
            	    dst[i * xdim + j] = value;
            	}
            }
        }
        
        return dst;
    }
    
    // Get an optimal set of orthogonal deltas and a map of which pixels are used.
    public static ArrayList getDeltasFromValues5(int src[], int xdim, int ydim)
    {
        int[]  dst        = new int[xdim * ydim];
        int[] direction   = new int[xdim * ydim];
        
        int init_value     = src[0];
        int value          = init_value;
        int delta          = 0;
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
            		    // Setting the first value to 4 to mark the delta type optimal orthogonal.
            			dst[k]       = 4;
            			direction[k] = 0;
            			k++;
            	    }
            		else
            		{
            			// We don't have an upper delta to check
            			// in the first row, so we just use horizontal deltas.
            		    delta        = src[k] - value;
                        value       += delta;
                        dst[k]       = delta;
                        direction[k] = 0;
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
            	    	// We dont have a horizontal delta to check,
            	    	// so we just use a vertical delta, and reset our init value.
            	    	delta        = src[k] - init_value;
            	    	init_value   = src[k];
            	    	dst[k]       = delta;
            	    	direction[k] = 1;
            	    	sum          += Math.abs(delta);
            	    	k++;
            	    }
            	    else
            	    {
            	    	// Now we have 2 possible pixels to use.
            	    	int a = src[k] - src[k - 1];
            	    	int b = src[k] - src[k - xdim];
            	    	
            	    	if(Math.abs(a) <= Math.abs(b))
            	    	{
            	    		delta        = a;
            	    	    dst[k]       = delta;
            	    	    direction[k] = 0;
            	    	    sum         += Math.abs(delta);
            	    	    k++;
            	    	}
            	    	else
            	    	{
            	    		delta        = b;
            	    		dst[k]       = delta;
            	    	    direction[k] = 1;
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
        result.add(direction);
        return result;
    }

    public static int[] getValuesFromDeltas5(int [] src, int xdim, int ydim, int init_value, int [] direction)
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
            		int current_direction = direction[i * xdim + j]; 
            		if(current_direction == 0)
            		    value = dst[i * xdim + j - 1];
            		else if(current_direction == 1)
            		    value = dst[(i - 1) * xdim + j];
            	    value += src[i * xdim + j];
            	    dst[i * xdim + j] = value;
            	}
            }
        }
        
        return dst;
    }
    
    
    public static ArrayList getDeltasFromValues6(int src[], int xdim, int ydim)
    {
        int[] dst          = new int[xdim * ydim];
        int init_value     = src[0];
        int value          = init_value;
        int delta          = 0;
        
        int sum            = 0;
        int horizontal     = 0;
    	int vertical       = 0;
    	int diagonal       = 0;
        int k              = 0;
        
        
        // We're keeping track of which pixel gets used, and then 
        // using the value to make our next prediction.
       
        int previous_value = 0;
         
        k = 0;
        for(int i = 0; i < ydim; i++)
        {
        	if(i == 0)
        	{
                for(int j = 0; j < xdim; j++)
                {
            	    if(j == 0)
            	    {
            		    // Setting the first value to 5 to mark the delta type.
            			dst[k++] = 5;
            	    }
            		else
            		{
            			// We don't have an upper or upper diagonal delta to check
            			// in the first row, so we just use horizontal deltas.
            		    delta     = src[k] - value;
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
            	    	// We dont have a horizontal delta or diagonal delta,
            	    	// so we just use a vertical delta, and reset our init value.
            	    	delta      = src[k] - init_value;
            	    	init_value = src[k];
            	    	dst[k++]   = delta;
            	        sum += Math.abs(delta);
            	        
            	        previous_value = init_value;
            	    }
            	    else
            	    {
            	    	int a = src[k - 1];
            	    	int b = src[k - xdim];
            	    	int c = src[k - xdim - 1];
            	    	int d = previous_value;
            	    	
            	    	// Prediction deltas.
            	    	int delta_a = Math.abs(a - d);
            	    	int delta_b = Math.abs(b - d);
            	    	int delta_c = Math.abs(c - d);
            	    	
            	    	// Actual deltas.
            	    	int horizontal_delta = src[k] - src[k - 1];
            	    	int vertical_delta   = src[k] - src[k - xdim];
            	    	int diagonal_delta   = src[k] - src[k - xdim - 1];
            	    	
            	    	if(delta_a <= delta_b && delta_a <= delta_c)
            	    	{
            	    	    delta = horizontal_delta;
            	    	    previous_value = a;
            	    	}
            	    	else if(delta_b <= delta_c)
            	    	{
            	    	    delta = vertical_delta;
            	    	    previous_value = b;
            	    	}
            	    	else
            	    	{
            	    	    delta = diagonal_delta;
            	    	    previous_value = c;
            	    	}
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

    public static int[] getValuesFromDeltas6(int src[], int xdim, int ydim, int init_value)
    {
    	int[] dst = new int[xdim * ydim];
        dst[0]    = init_value;
        int value = init_value;

        
        if(src[0] != 2)
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
    
    public static int packCode(int src[], int table[], int [] code, int [] length, byte dst[])
    {
    	int current_bit = 0;
    	int _current_bit = 0;
    	
    	for(int i = 0; i < src.length; i++)
    	{
    	    int j = src[i];
    	    int k = table[j];
    	    
    	    
    	    int code_word   = code[k];
    	    int code_length = length[k];
    	    int bit_span    = current_bit + code_length;
    	    int byte_span   = bit_span / 8;
    	    if(bit_span % 8 != 0)
    	    	byte_span++;
    	    
    	    int shift = current_bit % 8;
    	    
    	    
    	    for(int m = 0; m < byte_span; m++)
    	    {
    	    	/*
    	        int shifted_code_word = code_word << shift;
    	        shifted_code_word   >>= shift;
    	        shift += 8;
    	        
    	        byte code_byte = (byte)(shifted_code_word & 0x00FF);
    	        
    	        int current_byte = current_bit / 8;
    	        if(current_bit % 8 != 1)
    	        	current_byte++;
    	        dst[current_byte] &= code_byte;
    	        */
    	    	if(m != byte_span - 1)
    	            current_bit += 8;
    	    	else
    	    		current_bit += code_length % byte_span;
    	    }
    	    
    	    _current_bit += code_length;
    	}
    	
    	System.out.println("current_bit = " + current_bit + ", _current_bit = " + _current_bit);
    	int bit_length = _current_bit;
    	return bit_length;
    }
    
    public static int unpackCode(byte src[], int table[], int [] code, int [] length, int dst[])
    {
    	int current_bit = 0;
    	
    	for(int i = 0; i < src.length; i++)
    	{
    		
    	}
    	
    	int bit_length = current_bit;
    	return bit_length;
    }
    
    // These packing/unpacking functions represent int values as unary strings.
    // This set of functions makes no assumptions about the maxiumum length of 
    // an individual string, which makes it useful compressing data that 
    // cannot be scanned beforehand.
    public static int packStrings(int src[], int table[], byte dst[])
    {
        int size             = src.length;
        for(int i = 0; i < dst.length; i++)
        	dst[i] = 0;
        int [] mask  = new int[8];
        
        mask[0] = 1;
        mask[1] = 3;
        mask[2] = 7;
        mask[3] = 15;
        mask[4] = 31;
        mask[5] = 63;
        mask[6] = 127;
        mask[7] = 255;
    
        int start_bit  = 0;
        int stop_bit   = 0;
        int p   = 0;
        dst[p]  = 0;
        
        for(int i = 0; i < size; i++)
        {
            int j = src[i];
            int k = table[j];
            if(k == 0)
            {
                start_bit++;
                if(start_bit == 8)
                {
                    dst[++p] = 0;
                    start_bit       = 0;
                }
            }
            else
            {
                stop_bit = (start_bit + k + 1) % 8;
                
                if(k <= 7)
                {
                    dst[p] |= (byte) (mask[k - 1] << start_bit);
                    if(stop_bit <= start_bit)
                    {
                        dst[++p] = 0;
                        if(stop_bit != 0)
                            dst[p] |= (byte)(mask[k - 1] >> (8 - start_bit));
                    }
                }
                else if(k > 7)
                {
                	dst[p] |= (byte)(mask[7] << start_bit);
            		int m = (k - 8) / 8;
                    for(int n = 0; n < m; n++)
                        dst[++p] = (byte)(mask[7]);
                    dst[++p] = 0;
                    if(start_bit != 0)
                        dst[p] |= (byte)(mask[7] >> (8 - start_bit));	
                    
                    if(k % 8 != 0)
                    {
                        m = k % 8 - 1;
                        dst[p] |= (byte)(mask[m] << start_bit);
                        if(stop_bit <= start_bit)
                        {
                            dst[++p] = 0;
                            if(stop_bit != 0)
                                dst[p] |= (byte)(mask[m] >> (8 - start_bit));
                        }
                    }
                    else if(stop_bit <= start_bit)
                            dst[++p] = 0;
                }
                start_bit = stop_bit;
            }
        }
        if(start_bit != 0)
            p++;
        int number_of_bits = p * 8;
        if(start_bit != 0)
            number_of_bits -= 8 - start_bit;
        return(number_of_bits);
    }
    
    public static int unpackStrings(byte src[], int table[], int dst[])
    {
        for(int i = 0; i < dst.length; i++)
        	dst[i] = 0;
    	
    	int size                       = dst.length;
        int number_of_different_values = table.length;
        int number_unpacked            = 0;
        
        
        int [] inverse_table = new int[number_of_different_values];
        for(int i = 0; i < number_of_different_values; i++)
        {
            int j            = table[i];
            inverse_table[j] = i;
        }
        
        int length   = 1;
        int src_byte = 0;
        int dst_byte = 0;
        byte mask    = 0x01;
        byte bit     = 0;
        
        while(dst_byte < size)
        {
            byte non_zero = (byte)(src[src_byte] & (byte)(mask << bit));
            if(non_zero != 0)
                length++;
            else
            {
                int k = length - 1;
                dst[dst_byte++] = inverse_table[k];
                number_unpacked++;
                length = 1;
            }
            bit++;
            if(bit == 8)
            {
                bit = 0;
                src_byte++;
            }
        }
        return(number_unpacked);
    }
    
    // These packing/unpacking functions use the maximum length
    // code to dispense with a stop bit in the longest string.
    // Not very significant until we are looking at binary and
    // low resolution images.
    public static int packStrings2(int src[], int table[], byte dst[])
    {
    	int size             = src.length;
    	int number_of_values = table.length;
    	
    	int maximum_length = number_of_values - 1;
    
        int [] mask  = new int[8];
        
        mask[0] = 1;
        mask[1] = 3;
        mask[2] = 7;
        mask[3] = 15;
        mask[4] = 31;
        mask[5] = 63;
        mask[6] = 127;
        mask[7] = 255;
        
    
        int start_bit  = 0;
        int stop_bit   = 0;
        int p   = 0;
        dst[p]  = 0;
        
        for(int i = 0; i < size; i++)
        {
            int j = src[i];
            int k = table[j];
            
            if(k == 0)
            {
                start_bit++;
                if(start_bit == 8)
                {
                    dst[++p] = 0;
                    start_bit       = 0;
                }
            }
            else
            {
                stop_bit = (start_bit + k + 1) % 8;
                if(k == maximum_length)
                {
                	stop_bit--;
                	if(stop_bit < 0)
                		stop_bit = 7;
                }
                
                if(k <= 7)
                {
                	dst[p] |= (byte) (mask[k - 1] << start_bit);
                	
                    if(stop_bit <= start_bit)
                    {
                        dst[++p] = 0;
                        if(stop_bit != 0)
                        {
                            dst[p] |= (byte)(mask[k - 1] >> (8 - start_bit));
                        }
                    }
                }
                else if(k > 7)
                {
                	dst[p] |= (byte)(mask[7] << start_bit);
            		int m = (k - 8) / 8;
                    for(int n = 0; n < m; n++)
                        dst[++p] = (byte)(mask[7]);
                    dst[++p] = 0;
                    
                    if(start_bit != 0)
                        dst[p] |= (byte)(mask[7] >> (8 - start_bit));	
                    
                    if(k % 8 != 0)
                    {
                        m = k % 8 - 1;
                        
                        dst[p] |= (byte)(mask[m] << start_bit);
                        
                        if(stop_bit <= start_bit)
                        {
                            dst[++p] = 0;
                            if(stop_bit != 0)
                            {
                                dst[p] |= (byte)(mask[m] >> (8 - start_bit));
                            }
                        }
                    }
                    // If this is the maximum_length index and it's a multiple of 8,
                    // then we already incremeted the index and then reset the stop bit.
                    // Don't want to do it twice.   Very tricky bug.
                    else if(stop_bit <= start_bit && k != maximum_length)
                            dst[++p] = 0;
                }
                start_bit = stop_bit;
            }
        }
        
        if(start_bit != 0)
            p++;
        int number_of_bits = p * 8;
        if(start_bit != 0)
            number_of_bits -= 8 - start_bit;
        return(number_of_bits);
    }
    
    
    public static int unpackStrings2(byte src[], int table[], int dst[])
    {
    	for(int i = 0; i < dst.length; i++)
        	dst[i] = 0;
        int size             = dst.length;
        int number_of_values = table.length;
        int number_unpacked  = 0;
        int maximum_length   = number_of_values - 1;
   
        int [] index = new int[number_of_values];
        
        int [] inverse_table = new int[number_of_values];
        for(int i = 0; i < number_of_values; i++)
        {
            int j            = table[i];
            inverse_table[j] = i;
            index[i]         = 0;
        }
        
        int length   = 1;
        int src_byte = 0;
        int dst_byte = 0;
        
        
        byte mask = 0x01;
        byte bit  = 0;
        
        while(dst_byte < size)
        {
            byte non_zero = (byte)(src[src_byte] & (byte)(mask << bit));
            if(non_zero != 0 && length < maximum_length)
                length++;
            else if(non_zero == 0)
            {
                int k = length - 1;
                dst[dst_byte++] = inverse_table[k]; 
               
                index[k]++;
                
                number_unpacked++;
                length = 1;
            }
            else if(length == maximum_length)
            {
            	int k = length;
            	dst[dst_byte++] = inverse_table[k];
            	
                index[k]++;
                
                number_unpacked++;
                length = 1;
            }
            bit++;
            if(bit == 8)
            {
                bit = 0;
                src_byte++;
            }
        }
        return(number_unpacked);
    }
    
    public static ArrayList getStringData(int [] delta)
    {
    	ArrayList histogram_list       = DeltaMapper.getHistogram(delta);
	   

		int [] histogram            = (int[])histogram_list.get(1);
		int [] string_table = DeltaMapper.getRankTable(histogram);
		
		int    number_of_values = 0;
		int    predicted_bit_length = 0;
		double predicted_zero_ratio = 0;
		int    min_value = Integer.MAX_VALUE;
		int    min_index = 0;
		
		
		for(int i = 0; i < histogram.length; i++)
		{
			number_of_values += histogram[i];
			if(histogram[i] < min_value)
			{
				min_value = histogram[i];
				min_index = i;
			}
		}
		
		for(int i = 0; i < histogram.length; i++)
		{
			if(histogram.length == 1)
			{
				predicted_bit_length = number_of_values;
			    predicted_zero_ratio = 1.;   	
			}
			else if(histogram.length == 2)
			{
				predicted_bit_length += histogram[i];
			    if(i == 1)
			    {
			    	if(histogram[0] > histogram[1])
			    	    predicted_zero_ratio = histogram[0];
			    	else
			    		predicted_zero_ratio = histogram[1];
			    	predicted_zero_ratio /= number_of_values;
			    }	
			}
			else
			{
				int j = string_table[i];
				if(i != min_index)
				{
					predicted_bit_length += histogram[i] * (j + 1); 	
				}
				else
				{
					predicted_bit_length += histogram[i] * j;     	
				} 
			}
			predicted_zero_ratio  = number_of_values;
	    	predicted_zero_ratio -= min_value;
	    	predicted_zero_ratio /= predicted_bit_length;
		}
		
    	ArrayList data_list = new ArrayList();
    	data_list.add(predicted_bit_length);
    	data_list.add(predicted_zero_ratio);
    	return data_list;
    }
    
    public static int getStringLength(int [] delta)
    {
    	ArrayList histogram_list       = DeltaMapper.getHistogram(delta);
		int [] histogram            = (int[])histogram_list.get(1);
		int [] string_table = DeltaMapper.getRankTable(histogram);
		
		int    number_of_values = 0;
		int    predicted_bit_length = 0;
		int    min_value = Integer.MAX_VALUE;
		int    min_index = 0;
		
		
		for(int i = 0; i < histogram.length; i++)
		{
			number_of_values += histogram[i];
			if(histogram[i] < min_value)
			{
				min_value = histogram[i];
				min_index = i;
			}
		}
		
		for(int i = 0; i < histogram.length; i++)
		{
			if(histogram.length == 1)
			{
				predicted_bit_length = number_of_values;
			}
			else if(histogram.length == 2)
			{
				predicted_bit_length += histogram[i];
			}
			else
			{
				int j = string_table[i];
				if(i != min_index)
					predicted_bit_length += histogram[i] * (j + 1); 	
				else
					predicted_bit_length += histogram[i] * j;     	 
			}
		}
		
    	return predicted_bit_length;
    }
    
    public static int compressZeroBits(byte src[], int size, byte dst[])
    {
        for(int i = 0; i < dst.length; i++)
        	dst[i] = 0;
        int current_byte        = 0;
        int current_bit         = 0;
        byte mask               = 0x01;
        dst[0]                  = 0;
        
        int i = 0;
        int j = 0;
        int k = 0;
        for(i = 0; i < size; i++)
        {
            if((src[k] & (mask << j)) == 0 && i < size - 1)
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
                   // Current bit is a 0.
                   current_bit++;
                   if(current_bit == 8)
                   {
                       current_byte++;
                       //current_bit = dst[current_byte] = 0;
                       current_bit = 0;
                   }
               }
               else
               {
                   // Current bit is a 1.
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
                       current_bit = 0;
                   }
               }
            }
            else if((src[k] & (mask << j)) == 0 && (i == size -1)) 
            {
            	// We're at the end of the string and we have an odd 0.
            	// Put a 1 down to signal that there is an odd 0.
            	// This works for single iterations but might fail in the recursive case.
            	// Seems like there could be extra trailing bits when we unwind the
            	// recursion, but that does not seem to happen.
            
            	dst[current_byte] |= (byte)mask << current_bit;
                current_bit++;
                if(current_bit == 8)
                {
                    current_byte++;
                    current_bit = 0;
                }
            }
            else
            {
            	// Current first bit is a 1.
                dst[current_byte] |= (byte)mask << current_bit;
                current_bit++;
                if(current_bit == 8)
                {
                    current_byte++;
                    current_bit = 0;
                }
                current_bit++;
                if(current_bit == 8)
                {
                    current_byte++;
                    //current_bit = dst[current_byte] = 0;
                    current_bit = 0;
                }
            }
            j++;
            if(j == 8)
            {
                j = 0;
                k++;
            }
        }    
        int number_of_bits = current_byte * 8;
        number_of_bits    += current_bit;
        
        return(number_of_bits);
    }

    public static int decompressZeroBits(byte src[], int size, byte dst[])
    {
    	for(int i = 0; i < dst.length; i++)
        	dst[i] = 0;
    	
        int  current_byte = 0;
        int  current_bit  = 0;
        byte mask         = 0x01;
        dst[0]            = 0;
        
        int j = 0;
        int k = 0;
      
        for(int i = 0; i < size; i++)
        {
            if(((src[k] & (mask << j)) != 0) && i < size - 1)
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
                        current_bit = 0;
                    }
                    dst[current_byte] |= (byte)mask << current_bit;
                    current_bit++;
                    if(current_bit == 8)
                    {
                        current_byte++;
                        current_bit = 0;
                    }
                }
                else
                {
                    dst[current_byte] |= (byte)mask << current_bit;
                    current_bit++;
                    if(current_bit == 8)
                    {
                        current_byte++;
                        current_bit = 0;
                    }
                }
            }
            else if(((src[k] & (mask << j)) != 0) && i == size - 1)
            {
            	// Append an odd 0.
            	current_bit++;
                if(current_bit == 8)
                {
                    current_byte++;
                    current_bit = 0;
                }	
            }
            else if((src[k] & (mask << j)) == 0)
            {
                current_bit++;
                if(current_bit == 8)
                {
                    current_byte++;
                    if(current_byte < dst.length)
                        current_bit = 0;
                }
                current_bit++;
                if(current_bit == 8)
                {
                    current_byte++;
                    if(current_byte < dst.length)
                        current_bit = 0;
                }
            }
            
            j++;
            if(j == 8)
            {
                j = 0;
                k++;
            }
        }    
       
        int number_of_bits = current_byte * 8;
        number_of_bits += current_bit;
        return(number_of_bits);
    }
    
    // This function uses a metric to see if the data will expand or contract.
    // It duplicates the behavior of the original compress zero strings function,
    // by returning an expanded string and then stopping.
    // It's worth noting that sometimes the most compression is achieved by 
    // zipping the expanded data, but probably not significant
    // enough to justify all the extra processing.
    public static int compressZeroStrings(byte src[], int length, byte dst[])
    {
        int amount = getCompressionAmount(src, length, 0);
    	
    	if(amount > 0)
    	{
    		int current_length = compressZeroBits(src, length, dst);
    		int iterations     = 1;
    		int last_byte = current_length / 8;
            if(current_length % 8 != 0)
            	last_byte++;
            dst[last_byte] = (byte)iterations;
            return(current_length);
    	}
    	else
    	{
    		int current_length = compressZeroBits(src, length, dst);
    		int iterations     = 1;
    		amount             = getCompressionAmount(dst, current_length, 0);
    		
    		if(amount >= 0)
    		{
    			int last_byte = current_length / 8;
                if(current_length % 8 != 0)
                	last_byte++;
                dst[last_byte] = (byte)iterations;
                return(current_length);
    		}
    		else
    		{
    			byte [] temp      = new byte[src.length];  
    			while(amount < 0)
    			{
    				int previous_length = current_length;
        	        if(iterations % 2 == 1)
        	        {
                        current_length = compressZeroBits(dst, previous_length, temp);
                        amount         = getCompressionAmount(temp, current_length, 0);
                        
        	        }
                    else
                    {
                        current_length = compressZeroBits(temp, previous_length, dst);
                        amount             = getCompressionAmount(dst, current_length, 0);
                    }
                    iterations++; 
                    
    			}
    			
    			if(iterations % 2 == 0) 
                {
                	// The last iteration used temp as a destination,
                	// so we need to copy the data from temp to dst.
                    int byte_length = current_length / 8;
                    if(current_length % 8 != 0)
                        byte_length++; 
                    for(int i = 0; i < byte_length; i++)
                        dst[i] = temp[i];
                }   
                // else the result is already in dst.
    			
    			int last_byte = current_length / 8;
                if(current_length % 8 != 0)
                	last_byte++;
                dst[last_byte] = (byte)iterations;
                
                return current_length;
    		}
    	}
    }
    
    // This function uses a metric to see if the data will expand or contract, and
    // copies the src to dst instead of processing it if it would expand.  
    // This breaks the encoder/decoder programs.
    public static int compressZeroStrings2(byte src[], int length, byte dst[])
    {
    	boolean debug = false;
    	
        int amount = getCompressionAmount(src, length, 0);
    	if(amount > 0)
    	{
    		int byte_length = length / 8;
    		if(length % 8 != 0)
    			byte_length++;
    		for(int i = 0; i < byte_length; i++)
    			dst[i] = src[i];
    		dst[byte_length] = 0;
    	    return length;
    	}
        else
        {
    		int current_length = compressZeroBits(src, length, dst);
    		int iterations     = 1;
    		amount             = getCompressionAmount(dst, current_length, 0);
    		
    		
    		if(amount >= 0)
    		{
    			int last_byte = current_length / 8;
                if(current_length % 8 != 0)
                	last_byte++;
                dst[last_byte] = (byte)iterations;
                
                return(current_length);
    		}
    		else
    		{
    			byte [] temp      = new byte[src.length];  
    			while(amount < 0)
    			{
    				int previous_length = current_length;
        	        if(iterations % 2 == 1)
        	        {
                        current_length = compressZeroBits(dst, previous_length, temp);
                        amount         = getCompressionAmount(temp, current_length, 0);
        	        }
                    else
                    {
                        current_length = compressZeroBits(temp, previous_length, dst);
                        amount         = getCompressionAmount(dst, current_length, 0);
                    }
                    iterations++; 
                    
    			}
    			
    			if(iterations % 2 == 0) 
                {
                	// The last iteration used temp as a destination,
                	// so we need to copy the data from temp to dst.
                    int byte_length = current_length / 8;
                    if(current_length % 8 != 0)
                        byte_length++; 
                    for(int i = 0; i < byte_length; i++)
                        dst[i] = temp[i];
                }   
                // else the result is already in dst.
    			
    			int last_byte = current_length / 8;
                if(current_length % 8 != 0)
                	last_byte++;
                dst[last_byte] = (byte)iterations;
                
                return current_length;
    		}
    	}
    }
    
    public static int decompressZeroStrings(byte src[], int length, byte dst[])
    {
        // Getting the number of iterations appended to
        // the end of the string. 
        int last_byte = length / 8;
        if(length % 8 != 0)
        	last_byte++;
        int iterations = src[last_byte];
        
        // If it's not a zero type string, we'll still process it.
        // This might actually compress a one type string.
        if(iterations < 0)
        {
        	System.out.println("Not zero type string.");
        	iterations = -iterations;
        }
        
        // If it was not compressed, we copy src to dst.
        if(iterations == 0)
        {
        	int byte_length = length / 8;
        	if(length % 8 != 0)
        		byte_length++;
        	for(int i = 0; i < byte_length; i++)
        		dst[i] = src[i];
        	return length;
        }
        
        
        byte[]  temp = new byte[dst.length];
        int current_length = 0;
        if(iterations == 1)
        {
        	
            current_length = decompressZeroBits(src, length, dst);
            iterations--;
        }
        else if(iterations % 2 == 0)
        {
            current_length = decompressZeroBits(src, length, temp);
            iterations--;
            while(iterations > 0)
            {
            	
                int previous_length = current_length;
                if(iterations % 2 == 0)
                    current_length = decompressZeroBits(dst, previous_length, temp);
                else
                    current_length = decompressZeroBits(temp, previous_length, dst);
                iterations--;
            }
        }
        else
        {
            current_length = decompressZeroBits(src, length, dst);
            iterations--;
            while(iterations > 0)
            {
                int previous_length = current_length;
                if(iterations % 2 == 0)
                    current_length = decompressZeroBits(dst, previous_length, temp);
                else
                    current_length = decompressZeroBits(temp, previous_length, dst);
                iterations--;
            }
        }
       
        return(current_length);
    }
   
    public static int compressOneBits(byte src[], int size, byte dst[])
    {
        for(int i = 0; i < dst.length; i++)
        	dst[i] = 0;
        int current_byte        = 0;
        int current_bit         = 0;
        byte mask               = 0x01;
        dst[0]                  = 0;
        
        int i = 0;
        int j = 0;
        int k = 0;
        for(i = 0; i < size; i++)
        {
            // Current bit is a 1.  Check the next bit.
            if((src[k] & (mask << j)) != 0 && i < size - 1)
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
                   // Current bit is a 1.
                   // We parsed two 1 bits in a row, put down a 1 bit.
                   dst[current_byte] |= (byte)mask << current_bit;
                   
                   // Move to the start of the next code.
                   current_bit++;
                   if(current_bit == 8)
                   {
                       current_byte++;
                       current_bit = 0;
                   }
               }
               else
               {
                   // Current bit is a 0.
                   // We want 10 -> 01. 
                   // Increment and leave a 0 bit.
                   current_bit++;
                   if(current_bit == 8)
                   {
                       current_byte++;
                       current_bit = 0;
                   }
                   
                   // Put down a 1 bit.
                   dst[current_byte] |= (byte)mask << current_bit;
                   
                   // Move to the start of the next code.
                   current_bit++;
                   if(current_bit == 8)
                   {
                       current_byte++;
                       current_bit = 0;
                   }
               }
            }
            else if((src[k] & (mask << j)) != 0 && (i == size - 1)) // We're at the end of the string and we have an odd 1.
            {
                // Put a 0 down to signal that there is an odd 1.
            	// This works for single iterations but might fail in the recursive case.
            	// It seems like there could be extra trailing bits up to the amount of
            	// iterations, but that does not seem to happen.
                current_bit++;
                if(current_bit == 8)
                {
                    current_byte++;
                    current_bit = 0;
                }
            }
            else
            {
            	// Current first bit is a 0.
            	// 0->00
                current_bit++;
                if(current_bit == 8)
                {
                    current_byte++;
                    current_bit = 0;
                }
                current_bit++;
                if(current_bit == 8)
                {
                    current_byte++;
                    current_bit = 0;
                }
            }
            j++;
            if(j == 8)
            {
                j = 0;
                k++;
            }
        }    
        int number_of_bits = current_byte * 8;
        number_of_bits    += current_bit;
        return(number_of_bits);
    }

    public static int decompressOneBits(byte src[], int size, byte dst[])
    {
    	for(int i = 0; i < dst.length; i++)
        	dst[i] = 0;
    	
        int  current_byte = 0;
        int  current_bit  = 0;
        byte mask         = 0x01;
        dst[0]            = 0;
        
        int j = 0;
        int k = 0;
      
        for(int i = 0; i < size; i++)
        {
            // First bit is a 0, get next bit.
            if(((src[k] & (mask << j)) == 0) && i < size - 1)
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
                    // Another zero bit.  Leave a zero bit in the output.
                    current_bit++;
                    if(current_bit == 8)
                    {
                        current_byte++;
                        current_bit = 0;
                    }
                }
                else
                {
                    // We have 01->10.
                    dst[current_byte] |= (byte)mask << current_bit;
                    current_bit++;
                    if(current_bit == 8)
                    {
                        current_byte++;
                        current_bit = 0;
                    }
                    current_bit++;
                    if(current_bit == 8)
                    {
                        current_byte++;
                        current_bit = 0;
                    }
                }
            }
            else if(((src[k] & (mask << j)) == 0) && i == size - 1)
            {
            	// Append an odd 1.
            	dst[current_byte] |= (byte)mask << current_bit;
            	current_bit++;
                if(current_bit == 8)
                {
                    current_byte++;
                    current_bit = 0;
                }	
            }
            else if((src[k] & (mask << j)) != 0)
            {
                // We have a 1 bit, put down two 1 bits in the output.
                dst[current_byte] |= (byte)mask << current_bit;
                current_bit++;
                if(current_bit == 8)
                {
                    current_byte++;
                    if(current_byte < dst.length)
                        current_bit = 0;
                }
                dst[current_byte] |= (byte)mask << current_bit;
                current_bit++;
                if(current_bit == 8)
                {
                    current_byte++;
                    if(current_byte < dst.length)
                        current_bit = 0;
                }
            }
            j++;
            if(j == 8)
            {
                j = 0;
                k++;
            }
        }    
        
        int number_of_bits = current_byte * 8;
        number_of_bits += current_bit;
        return(number_of_bits);
    }
    
    public static int compressOneStrings(byte src[], int length, byte dst[])
    {
        int amount = getCompressionAmount(src, length, 1);
    	
    	if(amount > 0)
    	{
    		int current_length = compressOneBits(src, length, dst);
    		int iterations     = 1;
    		int last_byte = current_length / 8;
            if(current_length % 8 != 0)
            	last_byte++;
            dst[last_byte] = (byte)iterations;
            dst[last_byte] &= 127;
            return(current_length);
    	}
    	else
    	{
    		int current_length = compressOneBits(src, length, dst);
    		int iterations     = 1;
    		amount             = getCompressionAmount(dst, current_length, 1);
    		
    		if(amount >= 0)
    		{
    			int last_byte = current_length / 8;
                if(current_length % 8 != 0)
                	last_byte++;
                dst[last_byte] = (byte)iterations;
                dst[last_byte] &= 127;
                return(current_length);
    		}
    		else
    		{
    			byte [] temp      = new byte[src.length];  
    			while(amount < 0)
    			{
    				int previous_length = current_length;
        	        if(iterations % 2 == 1)
                        current_length = compressOneBits(dst, previous_length, temp);
                    else
                        current_length = compressOneBits(temp, previous_length, dst);
                    iterations++; 
                    amount             = getCompressionAmount(dst, current_length, 1);
    			}
    			
    			if(iterations % 2 == 0) 
                {
                	// The last iteration used temp as a destination,
                	// so we need to copy the data from temp to dst.
                    int byte_length = current_length / 8;
                    if(current_length % 8 != 0)
                        byte_length++; 
                    for(int i = 0; i < byte_length; i++)
                        dst[i] = temp[i];
                }   
                // else the result is already in dst.
    			
    			int last_byte = current_length / 8;
                if(current_length % 8 != 0)
                	last_byte++;
                dst[last_byte] = (byte)-iterations;
                return current_length;
    		}
    	}
    }
    
    // This function uses a metric to see if the data will expand or contract, and
    // simply copies src to dst if it will expand.  This breaks the
    // encoder/decoder programs.
    public static int compressOneStrings2(byte src[], int length, byte dst[])
    {
        int amount = getCompressionAmount(src, length, 1);
    	if(amount > 0)
    	{
    		int byte_length = length / 8;
    		if(length % 8 != 0)
    			byte_length++;
    		for(int i = 0; i < byte_length; i++)
    			dst[i] = src[i];
    		dst[byte_length] = 0;
    		dst[byte_length] &= 127;
    	    
    	    return length;
    	}
        else
        {
    		int current_length = compressOneBits(src, length, dst);
    		int iterations     = 1;
    		amount             = getCompressionAmount(dst, current_length, 1);
    		
    		if(amount >= 0)
    		{
    			int last_byte = current_length / 8;
                if(current_length % 8 != 0)
                	last_byte++;
                dst[last_byte] = (byte)-iterations;
                return(current_length);
    		}
    		else
    		{
    			byte [] temp      = new byte[src.length];  
    			while(amount < 0)
    			{
    				int previous_length = current_length;
        	        if(iterations % 2 == 1)
        	        {
                        current_length = compressOneBits(dst, previous_length, temp);
                        amount         = getCompressionAmount(dst, current_length, 1);
        	        }
                    else
                    {
                        current_length = compressOneBits(temp, previous_length, dst);
                        amount         = getCompressionAmount(dst, current_length, 1);
                    }
                    iterations++; 
    			}
    			
    			if(iterations % 2 == 0) 
                {
                	// The last iteration used temp as a destination,
                	// so we need to copy the data from temp to dst.
                    int byte_length = current_length / 8;
                    if(current_length % 8 != 0)
                        byte_length++; 
                    for(int i = 0; i < byte_length; i++)
                        dst[i] = temp[i];
                }   
                // else the result is already in dst.
    			
    			int last_byte = current_length / 8;
                if(current_length % 8 != 0)
                	last_byte++;
                dst[last_byte] = (byte)-iterations;
                return current_length;
    		}
    	}
    }
    
    // Returns string length of packed strings.
    public static int decompressOneStrings(byte src[], int length, byte dst[])
    {
        // Getting the number of iterations appended to
        // the end of the string.
        int last_byte = length / 8;
        if(length % 8 != 0)
            last_byte++;
        int iterations = src[last_byte];
        
        // We process the string anyway, although we might
        // actually be compressing it.
        if(iterations > 0)
        	System.out.println("Is not one type string.");
        else
        	iterations = -iterations;
        
        // If it was not compressed, we copy src to dst.
        if(iterations == 0)
        {
        	int byte_length = length / 8;
        	if(length % 8 != 0)
        		byte_length++;
        	for(int i = 0; i < byte_length; i++)
        		dst[i] = src[i];
        	return length;
        }
        
        byte[]  temp = new byte[dst.length];
        
        int current_length = 0;
        if(iterations == 1)
        {
            current_length = decompressOneBits(src, length, dst);
            iterations--;
        }
        else if(iterations % 2 == 0)
        {
            current_length = decompressOneBits(src, length, temp);
            iterations--;
            while(iterations > 0)
            {
                int previous_length = current_length;
                if(iterations % 2 == 0)
                    current_length = decompressOneBits(dst, previous_length, temp);
                else
                    current_length = decompressOneBits(temp, previous_length, dst);
                iterations--;
            }
        }
        else
        {
            current_length = decompressOneBits(src, length, dst);
            iterations--;
            while(iterations > 0)
            {
                int previous_length = current_length;
                if(iterations % 2 == 0)
                    current_length = decompressOneBits(dst, previous_length, temp);
                else
                    current_length = decompressOneBits(temp, previous_length, dst);
                iterations--;
            }
        }
        return(current_length);
    } 
    
    public static int getIterations(byte string[], int length)
    {
    	int last_byte = length / 8;
        if(length % 8 != 0)
        	last_byte++;
        int iterations  = string[last_byte];
        if(iterations < 0)
            iterations = -iterations;
        return iterations;
    }
    
    public static int getStringType(byte string[], int length)
    {
    	int last_byte = length / 8;
        if(length % 8 != 0)
        	last_byte++;
        int string_type = 0;
        int iterations  = string[last_byte];
        if(iterations < 0)
            string_type = 1;
        return string_type;
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
    
    public static int[] getCanonicalCode(int [] length)
    {
    	int n = length.length;
    	
        int [] code         = new int[n];
        int [] shifted_code = new int[n];
        int max_length = length[n - 1];
        
        code[0] = 0;
        shifted_code[0] = 0;
        for(int i = 1; i < n; i++)
        {
        	code[i]   = (int)(code[i - 1] + Math.pow(2, max_length - length[i - 1]));
        	int shift = max_length - length[i];
        	shifted_code[i] = code[i] >> shift;
        }
        return shifted_code;
    }
    
    public static int[] getUnaryCode(int n)
    {
        int [] code         = new int[n];
       
        code[0] = 0;
	    for(int i = 1; i < n; i++)
	    {
	    	code[i] = code[i - 1] + (int)Math.pow(2, i);
	    }
	    code[n - 1]++;
        return code;
    }
    
    public static int[] getUnaryLength(int n)
    {
    	int [] length = new int[n];
    	for(int i = 0; i < n; i++)
    		length[i] = i + 1;
    	length[n - 1]--;
    	return length;
    }
    
    public static int[] getHuffmanLength(int [] weight)
    {
    	// The in-place processing is one of the
    	// trickiest parts of this code, but we
    	// don't want to modify the input so we'll 
    	// make a copy and work from that.
    	int n = weight.length;
    	
    	int [] w = new int[n];
    	for(int i = 0; i < n; i++)
    		w[i] = weight[i];
    	
    	int leaf = n - 1;
    	int root = n - 1;
    	int next;
    	
    	// Create tree.
    	for(next = n - 1; next > 0; next--)
    	{
    		// Find first child.
    	    if(leaf < 0 || (root > next && w[root] < w[leaf]))
    	    {
    	        // Use internal node and reassign w[next].
    	    	w[next] = w[root];
    	    	w[root] = next;
    	    	root--;
    	    }
    	    else
    	    {
    	    	// Use leaf node and reassign w[next].
    	    	w[next] = w[leaf];
    	    	leaf--;
    	    }
    	    
    	    // Find second child.
    	    if(leaf < 0 || (root > next && w[root] < w[leaf]))
    	    {
    	        // Use internal node and add to w[next].
    	    	w[next] += w[root];
    	    	w[root] = next;
    	    	root--;
    	    }
    	    else
    	    {
    	    	// Use leaf node and add to w[next].
    	    	w[next] += w[leaf];
    	    	leaf--;
    	    }
    	}
    	
    	// Traverse tree from root down, converting parent pointers into
    	// internal node depths.
    	w[1] = 0;
    	for(next = 2; next < n; next++)
    		w[next] = w[w[next]] + 1;
    	
    	// Final pass to produce code lengths.
    	int avail = 1;
    	int used  = 0;
    	int depth = 0;
    	
    	root = 1;
    	next = 0;
    	
    	while(avail > 0)
    	{
    		// Count internal nodes at each depth.
    		while(root < n && w[root] == depth)
    		{
    			used++;
    			root++;
    		}
    		
    		// Assign as leaves any nodes that are not internal.
    		while(avail > used)
    		{
    			w[next] = depth;
    			next++;
    			avail--;
    		}
    		
    		// Reset variables.
    		avail = 2 * used;
    		used  = 0;
    		depth++;
    	}
    	
    	 return w;
    }
      
    public static double getZeroRatio(int [] code, int [] length, int [] frequency)
    {
    	int    n     = code.length;
    	double ratio = 0;
    	
    	int    number_of_zeros = 0;
    	int    number_of_ones  = 0;
    	
    	for(int i = 0; i < n; i++)
    	{
    		int mask = 1;
    		for(int j = 0; j < length[i]; j++)
    		{
    			int bit_mask = mask << j;
    			int bit      = code[i] & bit_mask;
    			if(bit == 0)
    				number_of_zeros++;
    			else
    				number_of_ones++;
    		}
    	}
    	ratio  = number_of_zeros;
    	ratio /= number_of_zeros + number_of_ones;
    	
    	return ratio;
    }
    
    public static double log2(double value)
    {
    	double result = (Math.log(value)/Math.log(2.));
    	return result;
    }
    
    // Somewhere we are passing histograms with holes in
    // them.  Tricky problem that also hampers deflate.
    // We'll filter the zero values--room to improve
    // efficiency although it shouldn't effect final
    // result, unlike deflate.
    public static double getShannonLimit(int [] frequency)
    {
    	int n   = frequency.length;
    	int sum = 0;
    	for(int i = 0; i < n; i++)
    	    sum += frequency[i];
    	double [] weight = new double[n];
    	for(int i = 0; i < n; i++)
    	{
    	    weight[i]  = frequency[i];
    	    weight[i] /= sum;
    	}
    	
    	double limit = 0;
    	for(int i = 0; i < n; i++)
    	{
    		if(weight[i] != 0)
    	        limit -= frequency[i] * log2(weight[i]);
    	}
    	
        return limit;
    }

    // We'll refer to integer values as frequencies,
    // and fractions (or probabilities) as weights.
    public static int getCost(int [] length, int [] frequency)
    {
    	int n    = length.length;
    	int cost = 0;
    	
    	for(int i = 0; i < n; i++)
    	{
    	    cost += length[i] * frequency[i];
    	}
    	return cost;
    }
    
    public static double getZeroRatio(byte [] string, int bit_length)
    {
    	int byte_length = bit_length / 8;
    	int zero_sum    = 0;
        int one_sum     = 0;
        byte mask       = 1;
        
        int n           = byte_length;
        
        for(int i = 0; i < n; i++)
        {
        	for(int j = 0; j < 8; j++)
        	{
        	    int k = string[i] & mask << j;	
        	    if(k == 0)
        	    	zero_sum++;
        	    else
        	    	one_sum++;
        	}
        }
       
    	int remainder = bit_length % 8;
    	if(remainder != 0)
    	{
    		for(int i = 0; i < remainder; i++)
    		{
    			int j = string[byte_length] & mask << i;
    			if(j == 0)
    				zero_sum++;
    			else
    				one_sum++;
    		}
    	}
    	
    	double ratio = zero_sum;
    	ratio       /= zero_sum + one_sum;
    	return ratio;
    }
    
    public static int getCompressionAmount(byte [] string, int bit_length, int transform_type)
    {
    	int  positive = 0;
        int  negative = 0;     
        byte mask     = 1;
      
        int byte_length = bit_length / 8;
        int n           = byte_length;
        if(transform_type == 0)
        {
        	int previous = 1;
            for(int i = 0; i < n; i++)
            {
            	for(int j = 0; j < 8; j++)
            	{
            	    int k = string[i] & mask << j;	
            	    
            	    if(k != 0 && previous != 0)
            	    	positive++;
            	    else if(k != 0 && previous == 0)
            	        previous = 1;
            	    else if(k == 0 && previous != 0)
            	    	previous = 0;
            	    else if(k == 0 && previous == 0)
            	    {
            	        negative++;
            	        previous = 1;
            	    }
            	}
            }
           
            
        	int remainder = bit_length % 8;
        	if(remainder != 0)
        	{
        		for(int i = 0; i < remainder; i++)
        		{
        			int j = string[byte_length] & mask << i;
        			
        			if(j != 0 && previous != 0)
            	    	positive++;
            	    else if(j != 0 && previous == 0)
            	        previous = 1;
            	    else if(j == 0 && previous != 0)
            	    	previous = 0;
            	    else if(j == 0 && previous == 0)
            	    {
            	        negative++;
            	        previous = 1;
            	    }
        		}
        	}	
        }
        else
        {
        	int previous = 0;
            for(int i = 0; i < n; i++)
            {
            	for(int j = 0; j < 8; j++)
            	{
            	    int k = string[i] & mask << j;	
            	    
            	    if(k == 0 && previous == 0)
            	    	positive++;
            	    else if(k == 0 && previous == 1)
            	        previous = 0;
            	    else if(k != 0 && previous == 0)
            	    	previous = 1;
            	    else if(k != 0 && previous != 0)
            	    {
            	        negative++;
            	        previous = 0;
            	    }
            	}
            }
           
        	int remainder = bit_length % 8;
        	if(remainder != 0)
        	{
        		for(int i = 0; i < remainder; i++)
        		{
        			int j = string[byte_length] & mask << i;
        			
        			if(j == 0 && previous == 0)
            	    	positive++;
            	    else if(j == 0 && previous == 1)
            	        previous = 0;
            	    else if(j != 0 && previous == 0)
            	    	previous = 1;
            	    else if(j != 0 && previous != 0)
            	    {
            	        negative++;
            	        previous = 0;
            	    }
        		}
        	}		
        }
    	int total = positive - negative;
    	// Account for the extra bits, although
    	// it doesn't show up in the bit length.
    	if(bit_length % 8 != 0)
    	    total += 8 - bit_length % 8;	
    	return total;
    }
    
    // This function returns a segment list with regular intervals.
    public static ArrayList getSegmentData(byte [] string, int string_bit_length, int segment_bit_length)
    {
    	
    	boolean debug1  = false;
    	boolean debug2 = true;
    	
    	int number_of_segments       = string_bit_length / segment_bit_length;
    	int segment_byte_length      = segment_bit_length / 8;
    	int remainder                = string_bit_length % segment_bit_length;
    	int last_segment_bit_length  = segment_bit_length + remainder;
    	int last_segment_byte_length = segment_byte_length + remainder / 8;
    	if(remainder % 8 != 0)
    		last_segment_byte_length++;   
    	
    	
    	ArrayList compressed_length = new ArrayList();
    	ArrayList compressed_data   = new ArrayList();
    	
    	
    	
    	for(int i = 0; i < number_of_segments - 1; i++)
        {
            byte [] segment = new byte[segment_byte_length];	
            byte [] processed_segment  = new byte[2 * segment_byte_length];
            byte [] compressed_segment = new byte[2 * segment_byte_length];
            
            for(int j = 0; j < segment_byte_length; j++)
                segment[j] = string[i * segment_byte_length + j];
            
            double zero_ratio = getZeroRatio(segment, segment_bit_length);
           
            if(zero_ratio >= .5)
            {
            	int compression_amount = getCompressionAmount(segment, segment_bit_length, 0);
            	
            	if(compression_amount < 0)
            	{
            		int compression_length = compressZeroStrings2(segment, segment_bit_length, compressed_segment);
            		int byte_length = compression_length / 8 + 1;
            		if(compression_length % 8 != 0)
            			byte_length++;
            		byte [] clipped_string = new byte[byte_length];
            		for(int j = 0; j < byte_length; j++)
            			clipped_string[j] = compressed_segment[j];
            		compressed_length.add(compression_length);
        			compressed_data.add(clipped_string);
            	}
            	else
            	{
            		byte [] clipped_string = new byte[segment_byte_length + 1];
            		for(int j = 0; j < segment_byte_length; j++)
            			clipped_string[j] = segment[j];
            		clipped_string[segment_byte_length] = 0;
        			compressed_length.add(segment_bit_length);
        			compressed_data.add(clipped_string);
            	}
            }
            else
            {
            	int compression_amount = getCompressionAmount(segment, segment_bit_length, 1);
            	
            	if(compression_amount < 0)
            	{
            		int compression_length = compressOneStrings2(segment, segment_bit_length, compressed_segment);
            		int byte_length = compression_length / 8 + 1;
            		if(compression_length % 8 != 0)
            			byte_length++;
            		byte [] clipped_string = new byte[byte_length];
            		for(int j = 0; j < byte_length; j++)
            			clipped_string[j] = compressed_segment[j];
            		compressed_length.add(compression_length);
        			compressed_data.add(clipped_string);
        			
            	}
            	else
            	{
            		byte [] clipped_string = new byte[segment_byte_length + 1];
            		for(int j = 0; j < segment_byte_length; j++)
            			clipped_string[j] = segment[j];
            		clipped_string[segment_byte_length] = 0;
        			compressed_length.add(segment_bit_length);
        			compressed_data.add(clipped_string);
            	}	
            }
        }
    	
    	int i = number_of_segments - 1;
    	byte [] segment = new byte[last_segment_byte_length];	
        byte [] compressed_segment = new byte[2 * last_segment_byte_length];
        byte [] processed_segment  = new byte[2 * last_segment_byte_length];
        
        for(int j = 0; j < last_segment_byte_length; j++)
            segment[j] = string[i * segment_byte_length + j];
        
        double zero_ratio = getZeroRatio(segment, last_segment_bit_length);
       
        if(zero_ratio >= .5)
        {
        	int compression_amount = getCompressionAmount(segment, last_segment_bit_length, 0);
        	if(compression_amount < 0)
        	{
        		int compression_length = compressZeroStrings2(segment, last_segment_bit_length, compressed_segment);
        		
        		int byte_length = compression_length / 8 + 1;
        		if(compression_length % 8 != 0)
        			byte_length++;
        		byte [] clipped_string = new byte[byte_length];
        		for(int j = 0; j < byte_length; j++)
        			clipped_string[j] = compressed_segment[j];
        		compressed_length.add(compression_length);
    			compressed_data.add(clipped_string);
    			
    			if(debug1)
    			{
    			    System.out.println("Compression length 0 is " + compression_length);
    			    int decompression_length = decompressZeroStrings(compressed_segment, compression_length, processed_segment);
    			    System.out.println("Decompression length 0 is " + decompression_length);
    			    boolean same_string = true;
    			    for(int j = 0; j < segment.length; j++)
    			    {
    				    if(segment[j] != processed_segment[j])
    					    same_string = false;
    			    }
    			    if(!same_string)
    				    System.out.println("Processed data is not the same as original data.");
    			    System.out.println();
    			}
        	}
        	else
        	{
        		byte [] clipped_string = new byte[last_segment_byte_length + 1];
        		for(int j = 0; j < last_segment_byte_length; j++)
        			clipped_string[j] = segment[j];
        		clipped_string[last_segment_byte_length] = 0;
    			compressed_length.add(last_segment_bit_length);
    			compressed_data.add(clipped_string);
        	}
        }
        else
        {
        	int compression_amount = getCompressionAmount(segment, last_segment_bit_length, 1);
        	if(compression_amount < 0)
        	{
        		int compression_length = compressOneStrings2(segment, last_segment_bit_length, compressed_segment);
        		int byte_length = compression_length / 8 + 1;
        		if(compression_length % 8 != 0)
        			byte_length++;
        		byte [] clipped_string = new byte[byte_length];
        		for(int j = 0; j < byte_length; j++)
        			clipped_string[j] = compressed_segment[j];
        		compressed_length.add(compression_length);
    			compressed_data.add(clipped_string);
    			
    			if(debug1)
    			{
    				System.out.println("Compression length 1 is " + compression_length);
                    int decompression_length = decompressOneStrings(compressed_segment, compression_length, processed_segment);
    			    System.out.println("Decompression length 1 is " + decompression_length);
    			    boolean same_string = true;
    			    for(int j = 0; j < segment.length; j++)
    			    {
    				    if(segment[j] != processed_segment[j])
    					    same_string = false;
    			    }
    			    if(!same_string)
    				    System.out.println("Processed data is not the same as original data.");
    			    System.out.println();
    			}
        	}
        	else
        	{
        		byte [] clipped_string = new byte[last_segment_byte_length + 1];
        		for(int j = 0; j < last_segment_byte_length; j++)
        			clipped_string[j] = segment[j];
        		clipped_string[last_segment_byte_length] = 0;
    			compressed_length.add(last_segment_bit_length);
    			compressed_data.add(clipped_string);
        	}	
        }
    	
        if(debug2)
        {
            double zero_ratio_min = 1;
		    double zero_ratio_max = 0.;
		    int    min_iterations = Integer.MAX_VALUE;
		    int    max_iterations = 0;
		    int    number_of_zero_segments = 0;
		    int    number_of_one_segments  = 0;
		    int    number_of_compressed_segments = 0;
		
		    for(i = 0; i < compressed_data.size(); i++)
		    {
			    int length   = (int)compressed_length.get(i);
			    byte [] data = (byte [])compressed_data.get(i);
			
			    zero_ratio = getZeroRatio(data, length);
			    if(zero_ratio < zero_ratio_min)
				    zero_ratio_min = zero_ratio;
			    if(zero_ratio > zero_ratio_max)
				    zero_ratio_max = zero_ratio;
			    if(zero_ratio >= .5)
				    number_of_zero_segments++;
			    else
				    number_of_one_segments++;
			    int iterations = getIterations(data, length);
			    if(iterations > max_iterations)
				    max_iterations = iterations;
			    if(iterations < min_iterations)
			        min_iterations = iterations;
			    if(iterations > 0)
			        number_of_compressed_segments++;
			    }
	
		    System.out.println("Number of segments in regular segment list is " + compressed_data.size());
		    System.out.println("Number of compressed segments is " + number_of_compressed_segments);
		    System.out.println("Number of zero segments is " + number_of_zero_segments);
		    System.out.println("Number of one segments is " + number_of_one_segments);
		    System.out.println("Minimum zero ratio is " + String.format("%.4f", zero_ratio_min));
		    System.out.println("Maximum zero ratio is " + String.format("%.4f", zero_ratio_max));
		    System.out.println("Minimum iterations is " + min_iterations);
		    System.out.println("Maximum iterations is " + max_iterations);
		    System.out.println();
        }
        
    	ArrayList segment_data = new ArrayList();
    	segment_data.add(compressed_length);
    	segment_data.add(compressed_data);
    	return segment_data;
    }
    

    // This function merges adjacent segments with the same bit type if they both compress, or if 
    // both don't compress regardless of bit_type.
    public static ArrayList getSegmentData2(byte [] string, int string_bit_length, int segment_bit_length)
    {
    	boolean debug1 = false;
    	boolean debug2 = true;
    	
    	int number_of_segments       = string_bit_length / segment_bit_length;
    	int segment_byte_length      = segment_bit_length / 8;
    	int remainder                = string_bit_length % segment_bit_length;
    	int last_segment_bit_length  = segment_bit_length + remainder;
    	int last_segment_byte_length = segment_byte_length + remainder / 8;
    	if(remainder % 8 != 0)
    		last_segment_byte_length++;   
    	
    	ArrayList init_list        = new ArrayList();
    	
    	byte [] processed_segment  = new byte[2 * last_segment_byte_length];
        byte [] compressed_segment = new byte[2 * last_segment_byte_length];
        
    

        for(int i = 0; i < number_of_segments - 1; i++)
        {
            byte [] segment = new byte[segment_byte_length];	
           
            
            for(int j = 0; j < segment_byte_length; j++)
                segment[j] = string[i * segment_byte_length + j];
            
            double zero_ratio = getZeroRatio(segment, segment_bit_length);
           
            if(zero_ratio >= .5)
            {
            	int compression_amount = getCompressionAmount(segment, segment_bit_length, 0);
            	
            	if(compression_amount < 0)
            	{
            		int compression_length = compressZeroStrings2(segment, segment_bit_length, compressed_segment);
            		int byte_length = compression_length / 8 + 1;
            		if(compression_length % 8 != 0)
            			byte_length++;
            		byte [] clipped_string = new byte[byte_length];
            		for(int j = 0; j < byte_length; j++)
            			clipped_string[j] = compressed_segment[j];
            		ArrayList segment_list = new ArrayList();
            		segment_list.add(segment_bit_length);
            		segment_list.add(compression_length);
            		segment_list.add(clipped_string);
            		init_list.add(segment_list);
            	}
            	else
            	{
            		byte [] clipped_string = new byte[segment_byte_length + 1];
            		for(int j = 0; j < segment_byte_length; j++)
            			clipped_string[j] = segment[j];
            		clipped_string[segment_byte_length] = 0;
            		ArrayList segment_list = new ArrayList();
            		segment_list.add(segment_bit_length);
            		segment_list.add(segment_bit_length);
            		segment_list.add(clipped_string);
            		init_list.add(segment_list);
            	}
            }
            else
            {
            	int compression_amount = getCompressionAmount(segment, segment_bit_length, 1);
            	
            	if(compression_amount < 0)
            	{
            		int compression_length = compressOneStrings2(segment, segment_bit_length, compressed_segment);
            		int byte_length = compression_length / 8 + 1;
            		if(compression_length % 8 != 0)
            			byte_length++;
            		byte [] clipped_string = new byte[byte_length];
            		for(int j = 0; j < byte_length; j++)
            			clipped_string[j] = compressed_segment[j];
            		ArrayList segment_list = new ArrayList();
            		segment_list.add(segment_bit_length);
            		segment_list.add(compression_length);
            		segment_list.add(clipped_string);
            		init_list.add(segment_list);
        			
            	}
            	else
            	{
            		byte [] clipped_string = new byte[segment_byte_length + 1];
            		for(int j = 0; j < segment_byte_length; j++)
            			clipped_string[j] = segment[j];
            		clipped_string[segment_byte_length] = 0;
            		ArrayList segment_list = new ArrayList();
            		segment_list.add(segment_bit_length);
            		segment_list.add(segment_bit_length);
            		segment_list.add(clipped_string);
            		init_list.add(segment_list);
            	}	
            }
        }
    	
    	int i = number_of_segments - 1;
    	byte [] segment = new byte[last_segment_byte_length];	
    
        
        for(int j = 0; j < last_segment_byte_length; j++)
            segment[j] = string[i * segment_byte_length + j];
        
        double zero_ratio = getZeroRatio(segment, last_segment_bit_length);
       
        if(zero_ratio >= .5)
        {
        	int compression_amount = getCompressionAmount(segment, last_segment_bit_length, 0);
        	if(compression_amount < 0)
        	{
        		int compression_length = compressZeroStrings2(segment, last_segment_bit_length, compressed_segment);
        		
        		int byte_length = compression_length / 8 + 1;
        		if(compression_length % 8 != 0)
        			byte_length++;
        		byte [] clipped_string = new byte[byte_length];
        		for(int j = 0; j < byte_length; j++)
        			clipped_string[j] = compressed_segment[j];
        		ArrayList segment_list = new ArrayList();
        		segment_list.add(segment_bit_length);
        		segment_list.add(compression_length);
        		segment_list.add(clipped_string);
        		init_list.add(segment_list);
    			
    			if(debug1)
    			{
    			    System.out.println("Compression length 0 is " + compression_length);
    			    int decompression_length = decompressZeroStrings(compressed_segment, compression_length, processed_segment);
    			    System.out.println("Decompression length 0 is " + decompression_length);
    			    boolean same_string = true;
    			    for(int j = 0; j < segment.length; j++)
    			    {
    				    if(segment[j] != processed_segment[j])
    					    same_string = false;
    			    }
    			    if(!same_string)
    				    System.out.println("Processed data is not the same as original data.");
    			    System.out.println();
    			}
        	}
        	else
        	{
        		byte [] clipped_string = new byte[last_segment_byte_length + 1];
        		for(int j = 0; j < last_segment_byte_length; j++)
        			clipped_string[j] = segment[j];
        		clipped_string[last_segment_byte_length] = 0;
        		ArrayList segment_list = new ArrayList();
        		segment_list.add(segment_bit_length);
        		segment_list.add(segment_bit_length);
        		segment_list.add(clipped_string);
        		init_list.add(segment_list);
        	}
        }
        else
        {
        	int compression_amount = getCompressionAmount(segment, last_segment_bit_length, 1);
        	if(compression_amount < 0)
        	{
        		int compression_length = compressOneStrings2(segment, last_segment_bit_length, compressed_segment);
        		int byte_length = compression_length / 8 + 1;
        		if(compression_length % 8 != 0)
        			byte_length++;
        		byte [] clipped_string = new byte[byte_length];
        		for(int j = 0; j < byte_length; j++)
        			clipped_string[j] = compressed_segment[j];
        		ArrayList segment_list = new ArrayList();
        		segment_list.add(last_segment_bit_length);
        		segment_list.add(compression_length);
        		segment_list.add(clipped_string);
        		init_list.add(segment_list);
    			
    			if(debug1)
    			{
    				System.out.println("Compression length 1 is " + compression_length);
                    int decompression_length = decompressOneStrings(compressed_segment, compression_length, processed_segment);
    			    System.out.println("Decompression length 1 is " + decompression_length);
    			    boolean same_string = true;
    			    for(int j = 0; j < segment.length; j++)
    			    {
    				    if(segment[j] != processed_segment[j])
    					    same_string = false;
    			    }
    			    if(!same_string)
    				    System.out.println("Processed data is not the same as original data.");
    			    System.out.println();
    			}
        	}
        	else
        	{
        		byte [] clipped_string = new byte[last_segment_byte_length + 1];
        		for(int j = 0; j < last_segment_byte_length; j++)
        			clipped_string[j] = segment[j];
        		clipped_string[last_segment_byte_length] = 0;
        		ArrayList segment_list = new ArrayList();
        		segment_list.add(last_segment_bit_length);
        		segment_list.add(last_segment_bit_length);
        		segment_list.add(clipped_string);
        		init_list.add(segment_list);
        	}	
        }
    	
		if(debug2)
		{
		    double zero_ratio_min = 1;
		    double zero_ratio_max = 0.;
		    int    min_iterations = Integer.MAX_VALUE;
		    int    max_iterations = 0;
		    int    number_of_zero_segments = 0;
		    int    number_of_one_segments  = 0;
		    int    number_of_compressed_segments = 0;
		    int    max_string_type = -1;
		    int    min_string_type = -1;
		
		    for(i = 0; i < init_list.size(); i++)
            {
			    ArrayList segment_list = (ArrayList)init_list.get(i);	
			
			    int length   = (int)segment_list.get(1);
			    byte [] data = (byte [])segment_list.get(2);
			
			
			    zero_ratio = getZeroRatio(data, length);
			    if(zero_ratio < zero_ratio_min)
				    zero_ratio_min = zero_ratio;
			    if(zero_ratio > zero_ratio_max)
			        zero_ratio_max = zero_ratio;
			    if(zero_ratio >= .5)
			        number_of_zero_segments++;
			    else
			        number_of_one_segments++;
			    int iterations = getIterations(data, length);
			    int string_type = getStringType(data, length);
			    if(iterations > max_iterations)
			    {
			        max_iterations = iterations;
			        max_string_type = string_type;
			    }
			    if(iterations < min_iterations)
			    {
			        min_iterations = iterations;
			        min_string_type = string_type;
			    }
			    if(iterations > 0)
			        number_of_compressed_segments++;
			}
    	
		    System.out.println("Initial list has " + init_list.size() + " segments.");
		    System.out.println("Number of compressed segments is " + number_of_compressed_segments);
		    System.out.println("Number of zero segments is " + number_of_zero_segments);
		    System.out.println("Number of one segments is " + number_of_one_segments);
		    System.out.println("Minimum zero ratio is " + String.format("%.4f", zero_ratio_min));
		    System.out.println("Maximum zero ratio is " + String.format("%.4f", zero_ratio_max));
		    System.out.println("Minimum iterations is " + min_iterations);
		    System.out.println("Minimum string type is " + min_string_type);
		    System.out.println("Maximum iterations is " + max_iterations);
		    System.out.println("Maximum string type is " + max_string_type);
		    System.out.println();
		}
		
      
    	// Finished constructing the initial list.
    	
        // Merge similar segments that are adjacent.
        
        ArrayList previous_list               = new ArrayList();
        ArrayList current_list                = new ArrayList();
        int       current_number_of_segments  = init_list.size();
        int       previous_number_of_segments = 0;
       
        for(i = 0; i < init_list.size(); i++)
        {
        	ArrayList segment_list = (ArrayList)init_list.get(i);
        	previous_list.add(segment_list);
        }
        
        int current_offset = 0;
        while(current_number_of_segments != previous_number_of_segments)
        {
        	previous_number_of_segments = previous_list.size();
        	current_list.clear();
        
            i = 0;
            current_offset = 0;
            
        	for(i = 0; i < previous_number_of_segments - 1; i++)
        	{
        	    ArrayList current_segment_list = (ArrayList)previous_list.get(i);
        	    ArrayList next_segment_list    = (ArrayList)previous_list.get(i + 1);
        	    
        	    int     current_length   = (int)    current_segment_list.get(0);
        	    int     next_length      = (int)    next_segment_list.get(0);
        	    int     current_t_length = (int)    current_segment_list.get(1);
        	    int     next_t_length    = (int)    next_segment_list.get(1);
        	    byte [] current_string   = (byte [])current_segment_list.get(2);
        	    byte [] next_string      = (byte [])next_segment_list.get(2);
        	    
        	    int current_type       = getStringType(current_string, current_t_length);
        	    int next_type          = getStringType(next_string,    next_t_length);
        	    int current_iterations = getIterations(current_string, current_t_length);
        	    int next_iterations    = getIterations(next_string,    next_t_length);
        	    
        	    if(current_type == next_type && current_iterations > 0 && next_iterations > 0)
        	    {
        	    	int merged_length  = current_length + next_length;
        	    	
        	    	int byte_offset = current_offset / 8;
        	    	int byte_length = merged_length / 8;
        	    	if(merged_length % 8 != 0)
        	    		byte_length++;
        	    	if(merged_length % 8 != 0  && i != previous_number_of_segments - 2)
        	    		System.out.println("Merged length not evenly divisible by 8 at index = " + i);
        	    	
        	    	byte [] merged_segment = new byte[byte_length];
        	    	for(int j = 0; j < byte_length; j++)
        	    		merged_segment[j] = string[j + byte_offset];
        	    	byte [] compressed_merged_segment = new byte[2 * byte_length];
        	    	int merged_compression_length = 0;
        	    	if(current_type == 0)
        	    	    merged_compression_length = compressZeroStrings2(merged_segment, merged_length, compressed_merged_segment);
        	    	else
        	    		merged_compression_length = compressOneStrings2(merged_segment, merged_length, compressed_merged_segment);
        	    	
        	    	if(merged_compression_length <= current_t_length + next_t_length)
        	    	{
        	    		int compressed_byte_length = merged_compression_length / 8;
        				if(merged_compression_length % 8 != 0)
        				    compressed_byte_length++;
        				compressed_byte_length++;
        				byte [] segment_string = new byte[compressed_byte_length];
        				for(int j = 0; j < compressed_byte_length; j++)
        					segment_string[j] = compressed_merged_segment[j];
        	    		
        	    		ArrayList merged_segment_list = new ArrayList();
        	    		merged_segment_list.add(merged_length);
        	    		merged_segment_list.add(merged_compression_length);
        	    		merged_segment_list.add(segment_string);
        	    
        	    		current_list.add(merged_segment_list);
        	    		
        	    		current_offset += merged_length;
            	        
        	    		i++;
        	    	}
        	    	else
        	    	{
        	    		current_list.add(current_segment_list);
        	    		current_offset += current_length;
        	    	}
        	    }
        	    else if(current_iterations == 0 && next_iterations == 0)
        	    {
        	    	int merged_length  = current_length + next_length;
        	    	
        	    	int byte_offset = current_offset / 8;
        	    	int byte_length = merged_length / 8;
        	    	if(merged_length % 8 != 0)
        	    		byte_length++;
        	    	
        	    	if(merged_length % 8 != 0  && i != previous_number_of_segments - 2)
        	    		System.out.println("Merged length not evenly divisible by 8 at index = " + i);
        	    	byte [] merged_segment = new byte[byte_length];
        	    	for(int j = 0; j < byte_length; j++)
        	    		merged_segment[j] = string[j + byte_offset];   
        	    	
        	    	byte_length = merged_length / 8;
    	    		if(merged_length % 8 != 0)
    	    			byte_length++;
    	    		byte_length++;
    	    	    byte [] segment_string    = new byte[byte_length];
            	    for(int j = 0; j < byte_length - 1; j++)
            		    segment_string[j] = merged_segment[j];
            	    segment_string[merged_segment.length] = 0;
            	    
            	    ArrayList merged_segment_list = new ArrayList();
    	    		merged_segment_list.add(merged_length);
    	    		merged_segment_list.add(merged_length);
    	    		merged_segment_list.add(segment_string);
    	    		
    	    		current_list.add(merged_segment_list);
    	    		
    	    		current_offset += merged_length;
    	    		i++;
        	    }
        	    else
        	    {
        	    	current_list.add(current_segment_list);
        	    	current_offset += current_length;
        	    }
        	}
        	
    	    if(i == previous_number_of_segments - 1)
    	    {
    		    ArrayList current_segment_list = (ArrayList)previous_list.get(i);
    		    current_list.add(current_segment_list);
    	    }
    	
    	    previous_list.clear();
    	   
    	    current_number_of_segments = current_list.size();
    	   
    	    for(i = 0; i < current_number_of_segments; i++)
    	    {
    		    ArrayList segment_list = (ArrayList)current_list.get(i);
    		    previous_list.add(segment_list);
    	    }
    	    // Previous list now has current list data.  
        }
        
		ArrayList compressed_length = new ArrayList();
    	ArrayList compressed_data   = new ArrayList();
		for(i = 0; i < current_list.size(); i++)
		{
			ArrayList segment_list = (ArrayList)current_list.get(i);
			int length   = (int)segment_list.get(1);
			byte [] data = (byte [])segment_list.get(2);
			compressed_length.add(length);
			compressed_data.add(data);
		}
		
		if(debug2)
		{
		    double zero_ratio_min = 1;
		    double zero_ratio_max = 0.;
		    int min_iterations = Integer.MAX_VALUE;
		    int max_iterations = 0;
		    int number_of_zero_segments = 0;
		    int number_of_one_segments  = 0;
		    int number_of_compressed_segments = 0;
		    int max_string_type = -1;
		    int min_string_type = -1;
		    for(i = 0; i < current_list.size(); i++)
		    {
		        ArrayList segment_list = (ArrayList)current_list.get(i);
		        int length   = (int)segment_list.get(1);
		        byte [] data = (byte [])segment_list.get(2);
			
			
		        zero_ratio = getZeroRatio(data, length);
		        if(zero_ratio < zero_ratio_min)
		            zero_ratio_min = zero_ratio;
		        if(zero_ratio > zero_ratio_max)
		            zero_ratio_max = zero_ratio;
		        if(zero_ratio >= .5)
		            number_of_zero_segments++;
		        else
		            number_of_one_segments++;
		        int iterations  = getIterations(data, length);
		        int string_type = getStringType(data, length);
		        if(iterations > max_iterations)
		        {
		            max_iterations = iterations;
		            max_string_type = string_type;
		        }
		        if(iterations < min_iterations)
		        {
		            min_iterations = iterations;
		            min_string_type = string_type;
		        }
		        if(iterations > 0)
		            number_of_compressed_segments++;	
		    }
	
		    System.out.println("Merged list has " + current_list.size() + " segments");
		    System.out.println("Number of compressed segments is " + number_of_compressed_segments);
		    System.out.println("Number of zero segments is " + number_of_zero_segments);
		    System.out.println("Number of one segments is " + number_of_one_segments);
		    System.out.println("Minimum zero ratio is " + String.format("%.4f", zero_ratio_min));
		    System.out.println("Maximum zero ratio is " + String.format("%.4f", zero_ratio_max));
		    System.out.println("Minimum iterations is " + min_iterations);
		    System.out.println("Minimum string type is " + min_string_type);
		    System.out.println("Maximum iterations is " + max_iterations);
		    System.out.println("Maximum string type is " + max_string_type);
		    System.out.println();
		}
        
		ArrayList segment_data = new ArrayList();
    	segment_data.add(compressed_length);
    	segment_data.add(compressed_data);
    	return segment_data;
    }
    
    

    // This function merges adjacent segments with the same bit type if they both compress, or if 
    // both don't compress regardless of bit_type.  It then checks to see if merged segments that didn't 
    // compress separately, compress after being merged.  This happens fairly frequently, but 
    // presumably is a single iteration with a modest amount of compression--worth looking into,
    // especially since it is not intuitive that that could happen.
    public static ArrayList getSegmentData3(byte [] string, int string_bit_length, int segment_bit_length)
    {
    	boolean debug1 = false;
    	boolean debug2 = true;
    	
    	int number_of_segments       = string_bit_length / segment_bit_length;
    	int segment_byte_length      = segment_bit_length / 8;
    	int remainder                = string_bit_length % segment_bit_length;
    	int last_segment_bit_length  = segment_bit_length + remainder;
    	int last_segment_byte_length = segment_byte_length + remainder / 8;
    	if(remainder % 8 != 0)
    		last_segment_byte_length++;   
    	
    	ArrayList init_list        = new ArrayList();
    	
    
        for(int i = 0; i < number_of_segments - 1; i++)
        {
            byte [] segment = new byte[segment_byte_length];	
            byte [] processed_segment  = new byte[2 * segment_byte_length];
            byte [] compressed_segment = new byte[2 * segment_byte_length];
            
            for(int j = 0; j < segment_byte_length; j++)
                segment[j] = string[i * segment_byte_length + j];
            
            double zero_ratio = getZeroRatio(segment, segment_bit_length);
           
            if(zero_ratio >= .5)
            {
            	int compression_amount = getCompressionAmount(segment, segment_bit_length, 0);
            	
            	if(compression_amount < 0)
            	{
            		int compression_length = compressZeroStrings2(segment, segment_bit_length, compressed_segment);
            		int byte_length = compression_length / 8 + 1;
            		if(compression_length % 8 != 0)
            			byte_length++;
            		byte [] clipped_string = new byte[byte_length];
            		for(int j = 0; j < byte_length; j++)
            			clipped_string[j] = compressed_segment[j];
            		ArrayList segment_list = new ArrayList();
            		segment_list.add(segment_bit_length);
            		segment_list.add(compression_length);
            		segment_list.add(clipped_string);
            		init_list.add(segment_list);
            	}
            	else
            	{
            		byte [] clipped_string = new byte[segment_byte_length + 1];
            		for(int j = 0; j < segment_byte_length; j++)
            			clipped_string[j] = segment[j];
            		clipped_string[segment_byte_length] = 0;
            		ArrayList segment_list = new ArrayList();
            		segment_list.add(segment_bit_length);
            		segment_list.add(segment_bit_length);
            		segment_list.add(clipped_string);
            		init_list.add(segment_list);
            	}
            }
            else
            {
            	int compression_amount = getCompressionAmount(segment, segment_bit_length, 1);
            	
            	if(compression_amount < 0)
            	{
            		int compression_length = compressOneStrings2(segment, segment_bit_length, compressed_segment);
            		int byte_length = compression_length / 8 + 1;
            		if(compression_length % 8 != 0)
            			byte_length++;
            		byte [] clipped_string = new byte[byte_length];
            		for(int j = 0; j < byte_length; j++)
            			clipped_string[j] = compressed_segment[j];
            		ArrayList segment_list = new ArrayList();
            		segment_list.add(segment_bit_length);
            		segment_list.add(compression_length);
            		segment_list.add(clipped_string);
            		init_list.add(segment_list);
        			
            	}
            	else
            	{
            		byte [] clipped_string = new byte[segment_byte_length + 1];
            		for(int j = 0; j < segment_byte_length; j++)
            			clipped_string[j] = segment[j];
            		clipped_string[segment_byte_length] = 0;
            		ArrayList segment_list = new ArrayList();
            		segment_list.add(segment_bit_length);
            		segment_list.add(segment_bit_length);
            		segment_list.add(clipped_string);
            		init_list.add(segment_list);
            	}	
            }
        }
    	
    	int i = number_of_segments - 1;
    	byte [] segment = new byte[last_segment_byte_length];	
        byte [] compressed_segment = new byte[2 * last_segment_byte_length];
        byte [] processed_segment  = new byte[2 * last_segment_byte_length];
        
        for(int j = 0; j < last_segment_byte_length; j++)
            segment[j] = string[i * segment_byte_length + j];
        
        double zero_ratio = getZeroRatio(segment, last_segment_bit_length);
       
        if(zero_ratio >= .5)
        {
        	int compression_amount = getCompressionAmount(segment, last_segment_bit_length, 0);
        	if(compression_amount < 0)
        	{
        		int compression_length = compressZeroStrings2(segment, last_segment_bit_length, compressed_segment);
        		
        		int byte_length = compression_length / 8 + 1;
        		if(compression_length % 8 != 0)
        			byte_length++;
        		byte [] clipped_string = new byte[byte_length];
        		for(int j = 0; j < byte_length; j++)
        			clipped_string[j] = compressed_segment[j];
        		ArrayList segment_list = new ArrayList();
        		segment_list.add(segment_bit_length);
        		segment_list.add(compression_length);
        		segment_list.add(clipped_string);
        		init_list.add(segment_list);
    			
    			if(debug1)
    			{
    			    System.out.println("Compression length 0 is " + compression_length);
    			    int decompression_length = decompressZeroStrings(compressed_segment, compression_length, processed_segment);
    			    System.out.println("Decompression length 0 is " + decompression_length);
    			    boolean same_string = true;
    			    for(int j = 0; j < segment.length; j++)
    			    {
    				    if(segment[j] != processed_segment[j])
    					    same_string = false;
    			    }
    			    if(!same_string)
    				    System.out.println("Processed data is not the same as original data.");
    			    System.out.println();
    			}
        	}
        	else
        	{
        		byte [] clipped_string = new byte[last_segment_byte_length + 1];
        		for(int j = 0; j < last_segment_byte_length; j++)
        			clipped_string[j] = segment[j];
        		clipped_string[last_segment_byte_length] = 0;
        		ArrayList segment_list = new ArrayList();
        		segment_list.add(segment_bit_length);
        		segment_list.add(segment_bit_length);
        		segment_list.add(clipped_string);
        		init_list.add(segment_list);
        	}
        }
        else
        {
        	int compression_amount = getCompressionAmount(segment, last_segment_bit_length, 1);
        	if(compression_amount < 0)
        	{
        		int compression_length = compressOneStrings2(segment, last_segment_bit_length, compressed_segment);
        		int byte_length = compression_length / 8 + 1;
        		if(compression_length % 8 != 0)
        			byte_length++;
        		byte [] clipped_string = new byte[byte_length];
        		for(int j = 0; j < byte_length; j++)
        			clipped_string[j] = compressed_segment[j];
        		ArrayList segment_list = new ArrayList();
        		segment_list.add(last_segment_bit_length);
        		segment_list.add(compression_length);
        		segment_list.add(clipped_string);
        		init_list.add(segment_list);
    			
    			if(debug1)
    			{
    				System.out.println("Compression length 1 is " + compression_length);
                    int decompression_length = decompressOneStrings(compressed_segment, compression_length, processed_segment);
    			    System.out.println("Decompression length 1 is " + decompression_length);
    			    boolean same_string = true;
    			    for(int j = 0; j < segment.length; j++)
    			    {
    				    if(segment[j] != processed_segment[j])
    					    same_string = false;
    			    }
    			    if(!same_string)
    				    System.out.println("Processed data is not the same as original data.");
    			    System.out.println();
    			}
        	}
        	else
        	{
        		byte [] clipped_string = new byte[last_segment_byte_length + 1];
        		for(int j = 0; j < last_segment_byte_length; j++)
        			clipped_string[j] = segment[j];
        		clipped_string[last_segment_byte_length] = 0;
        		ArrayList segment_list = new ArrayList();
        		segment_list.add(last_segment_bit_length);
        		segment_list.add(last_segment_bit_length);
        		segment_list.add(clipped_string);
        		init_list.add(segment_list);
        	}	
        }
    	
		if(debug2)
		{
		    double zero_ratio_min = 1;
		    double zero_ratio_max = 0.;
		    int    min_iterations = Integer.MAX_VALUE;
		    int    max_iterations = 0;
		    int    number_of_zero_segments = 0;
		    int    number_of_one_segments  = 0;
		    int    number_of_compressed_segments = 0;
		    int    max_string_type = -1;
		    int    min_string_type = -1;
		
		    for(i = 0; i < init_list.size(); i++)
            {
			    ArrayList segment_list = (ArrayList)init_list.get(i);	
			
			    int length   = (int)segment_list.get(1);
			    byte [] data = (byte [])segment_list.get(2);
			
			
			    zero_ratio = getZeroRatio(data, length);
			    if(zero_ratio < zero_ratio_min)
				    zero_ratio_min = zero_ratio;
			    if(zero_ratio > zero_ratio_max)
			        zero_ratio_max = zero_ratio;
			    if(zero_ratio >= .5)
			        number_of_zero_segments++;
			    else
			        number_of_one_segments++;
			    int iterations = getIterations(data, length);
			    int string_type = getStringType(data, length);
			    if(iterations > max_iterations)
			    {
			        max_iterations = iterations;
			        max_string_type = string_type;
			    }
			    if(iterations < min_iterations)
			    {
			        min_iterations = iterations;
			        min_string_type = string_type;
			    }
			    if(iterations > 0)
			        number_of_compressed_segments++;
			}
    	
		    System.out.println("Initial list has " + init_list.size() + " segments.");
		    System.out.println("Number of compressed segments is " + number_of_compressed_segments);
		    System.out.println("Number of zero segments is " + number_of_zero_segments);
		    System.out.println("Number of one segments is " + number_of_one_segments);
		    System.out.println("Minimum zero ratio is " + String.format("%.4f", zero_ratio_min));
		    System.out.println("Maximum zero ratio is " + String.format("%.4f", zero_ratio_max));
		    System.out.println("Minimum iterations is " + min_iterations);
		    System.out.println("Minimum string type is " + min_string_type);
		    System.out.println("Maximum iterations is " + max_iterations);
		    System.out.println("Maximum string type is " + max_string_type);
		    System.out.println();
		}
		
		
    	// Finished constructing the initial list.
    	
        // Merge similar segments that are adjacent.
        
        ArrayList previous_list               = new ArrayList();
        ArrayList current_list                = new ArrayList();
        int       current_number_of_segments  = init_list.size();
        int       previous_number_of_segments = 0;
       
        for(i = 0; i < init_list.size(); i++)
        {
        	ArrayList segment_list = (ArrayList)init_list.get(i);
        	previous_list.add(segment_list);
        }
        
        int current_offset = 0;
        while(current_number_of_segments != previous_number_of_segments && current_number_of_segments > 1)
        {
        	if(debug1)
        	    System.out.println("Current number of segments is " + current_number_of_segments);
        	previous_number_of_segments = previous_list.size();
        	current_list.clear();
        
            i = 0;
            current_offset = 0;
            
        	for(i = 0; i < previous_number_of_segments - 1; i++)
        	{
        	    ArrayList current_segment_list = (ArrayList)previous_list.get(i);
        	    ArrayList next_segment_list    = (ArrayList)previous_list.get(i + 1);
        	    
        	    int     current_length   = (int)    current_segment_list.get(0);
        	    int     next_length      = (int)    next_segment_list.get(0);
        	    int     current_t_length = (int)    current_segment_list.get(1);
        	    int     next_t_length    = (int)    next_segment_list.get(1);
        	    byte [] current_string   = (byte [])current_segment_list.get(2);
        	    byte [] next_string      = (byte [])next_segment_list.get(2);
        	    
        	    int current_type       = getStringType(current_string, current_t_length);
        	    int next_type          = getStringType(next_string,    next_t_length);
        	    int current_iterations = getIterations(current_string, current_t_length);
        	    int next_iterations    = getIterations(next_string,    next_t_length);
        	    
        	    if(current_type == next_type && current_iterations > 0 && next_iterations > 0)
        	    {
        	    	int merged_length  = current_length + next_length;
        	    	
        	    	int byte_offset = current_offset / 8;
        	    	int byte_length = merged_length / 8;
        	    	if(merged_length % 8 != 0)
        	    		byte_length++;
        	    	if(merged_length % 8 != 0  && i != previous_number_of_segments - 2)
        	    		System.out.println("Merged length not evenly divisible by 8 at index = " + i);
        	    	
        	    	byte [] merged_segment = new byte[byte_length];
        	    	for(int j = 0; j < byte_length; j++)
        	    		merged_segment[j] = string[j + byte_offset];
        	    	byte [] compressed_merged_segment = new byte[2 * byte_length];
        	    	processed_segment = new byte[2 * byte_length];
        	    	int merged_compression_length = 0;
        	    	if(current_type == 0)
        	    	{
        	    	    merged_compression_length = compressZeroStrings2(merged_segment, merged_length, compressed_merged_segment);
        	    	    if(debug1)
            			{
                			System.out.println("Original length is " + merged_length);
            				System.out.println("Compression length 0 is " + merged_compression_length);
                            int decompression_length = decompressZeroStrings(compressed_merged_segment, merged_compression_length, processed_segment);
            			    System.out.println("Decompression length 0 is " + decompression_length);
            			    boolean same_string = true;
            			    for(int j = 0; j < merged_segment.length; j++)
            			    {
            				    if(merged_segment[j] != processed_segment[j])
            					    same_string = false;
            			    }
            			    if(!same_string)
            				    System.out.println("Processed data is not the same as original data.");
            			    int iterations = getIterations(compressed_merged_segment, merged_compression_length);
            			    System.out.println("Iterations is " + iterations);
            			    System.out.println();
            			}
        	    	}
        	    	else
        	    	{
        	    		merged_compression_length = compressOneStrings2(merged_segment, merged_length, compressed_merged_segment);
        	    		if(debug1)
            			{
                			System.out.println("Original length is " + merged_length);
            				System.out.println("Compression length 1 is " + merged_compression_length);
                            int decompression_length = decompressOneStrings(compressed_merged_segment, merged_compression_length, processed_segment);
            			    System.out.println("Decompression length 1 is " + decompression_length);
            			    boolean same_string = true;
            			    for(int j = 0; j < merged_segment.length; j++)
            			    {
            				    if(merged_segment[j] != processed_segment[j])
            					    same_string = false;
            			    }
            			    if(!same_string)
            				    System.out.println("Processed data is not the same as original data.");
            			    int iterations = getIterations(compressed_merged_segment, merged_compression_length);
            			    System.out.println("Iterations is " + iterations);
            			    System.out.println();
            			}
        	    	}
        	    	
        	    	if(merged_compression_length <= current_t_length + next_t_length)
        	    	{
        	    		int compressed_byte_length = merged_compression_length / 8;
        				if(merged_compression_length % 8 != 0)
        				    compressed_byte_length++;
        				compressed_byte_length++;
        				byte [] segment_string = new byte[compressed_byte_length];
        				for(int j = 0; j < compressed_byte_length; j++)
        					segment_string[j] = compressed_merged_segment[j];
        	    		
        	    		ArrayList merged_segment_list = new ArrayList();
        	    		merged_segment_list.add(merged_length);
        	    		merged_segment_list.add(merged_compression_length);
        	    		merged_segment_list.add(segment_string);
        	    
        	    		current_list.add(merged_segment_list);
        	    		
        	    		current_offset += merged_length;
            	        
        	    		i++;
        	    		
        	    	}
        	    	else
        	    	{
        	    		current_list.add(current_segment_list);
        	    		current_offset += current_length;
        	    	}
        	    }
        	    else if(current_iterations == 0 && next_iterations == 0)
        	    {
        	    	int merged_length  = current_length + next_length;
        	    	
        	    	int byte_offset = current_offset / 8;
        	    	int byte_length = merged_length / 8;
        	    	if(merged_length % 8 != 0)
        	    		byte_length++;
        	    	
        	    	if(merged_length % 8 != 0  && i != previous_number_of_segments - 2)
        	    		System.out.println("Merged length not evenly divisible by 8 at index = " + i);
        	    	byte [] merged_segment = new byte[byte_length];
        	    	for(int j = 0; j < byte_length; j++)
        	    		merged_segment[j] = string[j + byte_offset];   
        	    	
        	    	// Test to see if the merged segments compress.
        	    	double merged_ratio = getZeroRatio(merged_segment, merged_length);
        	    	byte [] compressed_merged_segment = new byte[merged_segment.length * 2];
        	    	int compression_length = 0;
        	    	if(merged_ratio >= .5)
        	    		compression_length = compressZeroStrings2(merged_segment, merged_length, compressed_merged_segment);
        	    	else
        	    		compression_length = compressOneStrings2(merged_segment, merged_length, compressed_merged_segment);
        	    	
        	    	if(compression_length < merged_length)
        	    	{
        	    		if(debug1)
        	    		    System.out.println("Merged segments that did not compress, compress after merging.");
        	    		
        	    		int compressed_byte_length = compression_length / 8;
        				if(compression_length % 8 != 0)
        				    compressed_byte_length++;
        				compressed_byte_length++;
        				
        				byte [] segment_string = new byte[compressed_byte_length];
        				for(int j = 0; j < compressed_byte_length; j++)
        					segment_string[j] = compressed_merged_segment[j];
        				
        				ArrayList merged_segment_list = new ArrayList();
        	    		merged_segment_list.add(merged_length);
        	    		merged_segment_list.add(compression_length);
        	    		merged_segment_list.add(segment_string);
        	    		
        	    		current_list.add(merged_segment_list);
        	    		current_offset += merged_length;
        	    		i++;
        				
        	    	}
        	    	else
        	    	{
        	    		if(compression_length > merged_length)
        	    			System.out.println("Compression length is an unexpected value.");
        	    		
        	    		byte_length = merged_length / 8;
        	    		if(merged_length % 8 != 0)
        	    			byte_length++;
        	    		byte_length++;
        	    	    byte [] segment_string    = new byte[byte_length];
                	    for(int j = 0; j < byte_length - 1; j++)
                		    segment_string[j] = merged_segment[j];
                	    segment_string[merged_segment.length] = 0;
                	    
                	    ArrayList merged_segment_list = new ArrayList();
        	    		merged_segment_list.add(merged_length);
        	    		merged_segment_list.add(compression_length);
        	    		merged_segment_list.add(segment_string);
        	    		
        	    		current_list.add(merged_segment_list);
        	    		current_offset += merged_length;
        	    		i++;
        	    	}
        	    }
        	    else
        	    {
        	    	current_list.add(current_segment_list);
        	    	current_offset += current_length;
        	    }
        	}
        	

    	    if(i == previous_number_of_segments - 1)
    	    {
    		    ArrayList current_segment_list = (ArrayList)previous_list.get(i);
    		    current_list.add(current_segment_list);
    	    }
    	
    	    previous_list.clear();
    	   
    	    current_number_of_segments = current_list.size();
    	   
    	    for(i = 0; i < current_number_of_segments; i++)
    	    {
    		    ArrayList segment_list = (ArrayList)current_list.get(i);
    		    previous_list.add(segment_list);
    	    }
    	    // Previous list now has current list data.  
        }
        
        ArrayList compressed_length = new ArrayList();
    	ArrayList compressed_data   = new ArrayList();
	
		for(i = 0; i < current_list.size(); i++)
		{
			ArrayList segment_list = (ArrayList)current_list.get(i);
			int length   = (int)segment_list.get(1);
			byte [] data = (byte [])segment_list.get(2);
			compressed_length.add(length);
			compressed_data.add(data);
		}
		
		if(debug2)
		{
		    double zero_ratio_min = 1;
		    double zero_ratio_max = 0.;
		    int min_iterations = Integer.MAX_VALUE;
		    int max_iterations = 0;
		    int number_of_zero_segments = 0;
		    int number_of_one_segments  = 0;
		    int number_of_compressed_segments = 0;
		    int max_string_type = -1;
		    int min_string_type = -1;
		    for(i = 0; i < current_list.size(); i++)
		    {
		        ArrayList segment_list = (ArrayList)current_list.get(i);
		        int length   = (int)segment_list.get(1);
		        byte [] data = (byte [])segment_list.get(2);
			
			
		        zero_ratio = getZeroRatio(data, length);
		        if(zero_ratio < zero_ratio_min)
		            zero_ratio_min = zero_ratio;
		        if(zero_ratio > zero_ratio_max)
		            zero_ratio_max = zero_ratio;
		        if(zero_ratio >= .5)
		            number_of_zero_segments++;
		        else
		            number_of_one_segments++;
		        int iterations  = getIterations(data, length);
		        int string_type = getStringType(data, length);
		        if(iterations > max_iterations)
		        {
		            max_iterations = iterations;
		            max_string_type = string_type;
		        }
		        if(iterations < min_iterations)
		        {
		            min_iterations = iterations;
		            min_string_type = string_type;
		        }
		        if(iterations > 0)
		            number_of_compressed_segments++;	
		    }
	
		    System.out.println("Merged list has " + current_list.size() + " segments");
		    System.out.println("Number of compressed segments is " + number_of_compressed_segments);
		    System.out.println("Number of zero segments is " + number_of_zero_segments);
		    System.out.println("Number of one segments is " + number_of_one_segments);
		    System.out.println("Minimum zero ratio is " + String.format("%.4f", zero_ratio_min));
		    System.out.println("Maximum zero ratio is " + String.format("%.4f", zero_ratio_max));
		    System.out.println("Minimum iterations is " + min_iterations);
		    System.out.println("Minimum string type is " + min_string_type);
		    System.out.println("Maximum iterations is " + max_iterations);
		    System.out.println("Maximum string type is " + max_string_type);
		    System.out.println();
		}
        
		ArrayList segment_data = new ArrayList();
    	segment_data.add(compressed_length);
    	segment_data.add(compressed_data);
    	return segment_data;
    }
    
}