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
	    //System.out.println("Sorted keys:");
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
	
	public static int[] extract(int[] src, int src_xdim, int xoffset, int yoffset, int xdim, int ydim)
	{
	    int src_ydim = src.length / src_xdim;
	   
	    
	    int [] dst = new int[ydim * xdim];
	    
	    for(int i = 0; i < ydim; i++)
	    {
	    	for(int j = 0; j < xdim; j++)
	    	{
	    	    dst[i * xdim + j] = src[(i + yoffset) * src_xdim + j + xoffset];	
	    	}
	    }
	    return(dst); 
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
    public static ArrayList getDeltasFromValues3(int src[], int xdim, int ydim)
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
        
        // We're checking to see how close the paeth filter comes to an
        // ideal delta set--which is not very close, but it can still produce
        // a better result than just using horizontal or vertical deltas.
        
        /*
        int horizontal_sum = 0;
        int vertical_sum   = 0;
        int diagonal_sum   = 0;
        int limit_sum      = 0;
        
        for(int i = 1; i < ydim; i++)
        {
        	for(int j = 1; j < xdim; j++)
        	{
        		
        		int horizontal_delta = Math.abs(src[i * xdim + j] - src[i * xdim + j - 1]);
        		horizontal_sum  += horizontal_delta;
        		
        		int vertical_delta = Math.abs(src[i * xdim + j] - src[(i - 1) * xdim + j]);
        		vertical_sum += vertical_delta;
        		
        		int diagonal_delta = Math.abs(src[i * xdim + j] - src[(i - 1) * xdim + j - 1]);
        		diagonal_sum += diagonal_delta;
        		
        		if(horizontal_delta <= vertical_delta && horizontal_delta <= diagonal_delta)
        			limit_sum += horizontal_delta;
        		else if(vertical_delta <= diagonal_delta)
        			limit_sum += vertical_delta;
        		else
        			limit_sum += diagonal_delta;
        	}
        }
        */
        
        k = 0;
        for(int i = 0; i < ydim; i++)
        {
        	if(i == 0)
        	{
                for(int j = 0; j < xdim; j++)
                {
            	    if(j == 0)
            	    {
            		    // Setting the first value to 2 to mark the delta type paeth.
            			dst[k++] = 2;
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
            	    	{
            	    	    delta = horizontal_delta;
            	    	    horizontal++;
            	    	    
            	    	    if(Math.abs(vertical_delta) < Math.abs(horizontal_delta))
            	    	    {
            	    	    	//System.out.println("Assigned vertical delta but absolute value of horizontal is smaller.");
            	    	    }
            	    	}
            	    	else if(delta_b <= delta_c)
            	    	{
            	    	    delta = vertical_delta;
            	    	    vertical++;
            	    	    
            	    	    if(Math.abs(diagonal_delta) < Math.abs(vertical_delta))
            	    	    {
            	    	    	//System.out.println("Assigned horizontal delta but absolute value of diagonal is smaller.");	
            	    	    }
            	    	}
            	    	else
            	    	{
            	    	    delta = diagonal_delta;
            	    	    diagonal++;
            	    	    
            	    	    if(vertical_delta < diagonal_delta || horizontal_delta < diagonal_delta)
            	    	    {
            	    	    	//System.out.println("Assigned diagonal delta but absolute value of orthogonal is smaller.");		
            	    	    }
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

    public static int[] getValuesFromDeltas3(int src[], int xdim, int ydim, int init_value)
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
    
    
    // These packing/unpacking functions represent int values
    // as unary strings.
    // This set of functions makes no assumptions about the 
    // the maxiumum length of an individual string.
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
                       //current_bit = dst[current_byte] = 0;
                       current_bit = 0;
                   }
               }
            }
            else if((src[k] & (mask << j)) == 0 && (i == size -1)) // We're at the end of the string and we have an odd 0.
            {
                // Put a 1 down to signal that there is an odd 0.
            	// This works for single iterations but fails in the recursive case.
            	// Seems like the extra trailing bits don't corrupt the original
            	// length of the bit string.
            	dst[current_byte] |= (byte)mask << current_bit;
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
            	// Current first bit is a 1.
                dst[current_byte] |= (byte)mask << current_bit;
                current_bit++;
                if(current_bit == 8)
                {
                    current_byte++;
                    //current_bit = dst[current_byte] = 0;
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
                        current_length = compressZeroBits(dst, previous_length, temp);
                    else
                        current_length = compressZeroBits(temp, previous_length, dst);
                    iterations++; 
                    amount             = getCompressionAmount(dst, current_length, 0);
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
    // saves processing by simply returning the current length.  This breaks the
    // encoder/decoder programs.
    public static int compressZeroStrings2(byte src[], int length, byte dst[])
    {
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
    	
    		//System.out.println("Current length is " + current_length);
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
                        current_length = compressZeroBits(dst, previous_length, temp);
                    else
                        current_length = compressZeroBits(temp, previous_length, dst);
                    iterations++; 
                    amount             = getCompressionAmount(dst, current_length, 0);
                    
            		//System.out.println("Current length is " + current_length);
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
        	iterations += 256;
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
        
        //System.out.println("The number of iterations is " + iterations);
        
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
            	// This works for single iterations but fails in the recursive case.
            	// There may be another way to preserve the values at the end of the
            	// string but it gets pretty complicated. 
            	// It might be the original length is uncorrupted but haven't checked.
            	// It seems like everything works without padding the input. Still can't trust
            	// that the length returned by the string decompression functions arent off by 
            	// the number of iterations, so it helps to know the original length.
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
    		amount             = getCompressionAmount(dst, current_length, 0);
    		
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
                    amount             = getCompressionAmount(dst, current_length, 0);
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
    // saves processing by simply returning the current length.  This breaks the
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
    		amount             = getCompressionAmount(dst, current_length, 0);
    		
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
    
    // Returns string length of packed strings, may include extra trailing bits.
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
    
    public static ArrayList checkStringType(byte src[], int size)
    {
        int last_byte = size / 8;
        if(size % 8 != 0)
        	last_byte++;
        
        int string_type = 0;
        int iterations  = src[last_byte];
        if(iterations < 0)
        {
        	string_type = 1;
        	iterations += 256;
        }
    
        ArrayList result = new ArrayList();
        result.add(string_type);
        result.add(iterations);
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
    public static double getShannonLimit(int [] weight)
    {
    	int sum = 0;
    	int n   = weight.length;
    	
    	for(int i = 0; i < n; i++)
    	    sum += weight[i];
    	
    	double limit = 0;
    	for(int i = 0; i < n; i++)
    	{
    		double exponent = weight[i];
    		exponent       /= sum;
    		
    		double factor = Math.pow(2, exponent);
    		limit          += weight[i] * factor;
    	}
    	
    	return limit;
    }

    // We'll refer to integer values as frequencies,
    // and fractions as weights.
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
        
        // For some reason, this breaks the compress strings function.
        // Seems like we're neglecting odd bits.
        /*
        if(bit_length % 8 != 0)
        	byte_length++;
        if(byte_length > string.length)
        {
        	System.out.println("Length of string is longer than array.");
        	byte_length--;
        }
        */
        
      
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
    	return total;
    }
    
    
    public static ArrayList getSegmentData(byte [] string, int string_bit_length, int segment_bit_length)
    {
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
            byte [] processed_segment  = new byte[2 * segment_byte_length + 2];
            byte [] compressed_segment = new byte[2 * segment_byte_length + 2];
            
            for(int j = 0; j < segment_byte_length; j++)
                segment[j] = string[i * segment_byte_length + j];
            
            double zero_ratio = getZeroRatio(segment, segment_bit_length);
            //System.out.println("Segment bit length is " + segment_bit_length);
            if(zero_ratio >= .5)
            {
            	int compression_amount = getCompressionAmount(segment, segment_bit_length, 0);
            	if(compression_amount < 0)
            	{
            		int compression_length = compressZeroStrings2(segment, segment_bit_length, compressed_segment);
            		
            		System.out.println("Compression length 0 is " + compression_length);
            		int byte_length = compression_length / 8 + 1;
            		if(compression_length % 8 != 0)
            			byte_length++;
            		byte [] clipped_string = new byte[byte_length];
            		for(int j = 0; j < byte_length; j++)
            			clipped_string[j] = compressed_segment[j];
            		compressed_length.add(compression_length);
        			compressed_data.add(clipped_string);
        			
        			
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
            		System.out.println("Compression length 1 is " + compression_length);
            		int byte_length = compression_length / 8 + 1;
            		if(compression_length % 8 != 0)
            			byte_length++;
            		byte [] clipped_string = new byte[byte_length];
            		for(int j = 0; j < byte_length; j++)
            			clipped_string[j] = compressed_segment[j];
            		compressed_length.add(compression_length);
        			compressed_data.add(clipped_string);
        			
        			
        			int string_type = getStringType(compressed_segment, compression_length);
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
        byte [] compressed_segment = new byte[2 * last_segment_byte_length + 2];
        
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
    	
    	ArrayList segment_data = new ArrayList();
    	segment_data.add(compressed_length);
    	segment_data.add(compressed_data);
    	return segment_data;
    }
    
    
    
    public static ArrayList getMergedSegments(byte [] string, int length, int segment_length)
    {
    	boolean debug = true;
    	
    
        int number_of_segments  = length / segment_length;
        int segment_byte_length = segment_length / 8;
        int remainder           = length % segment_length;
        int last_segment_length = segment_length + remainder;
        int last_segment_byte_length = last_segment_length / 8;
        if(last_segment_length % 8 != 0)
        	last_segment_byte_length++;
        
        
        ArrayList init_list = new ArrayList();
     
        for(int i = 0; i < number_of_segments - 1; i++)
        {
            byte [] segment = new byte[segment_byte_length];	
            byte [] compressed_segment = new byte[2 * segment_byte_length];
            for(int j = 0; j < segment_byte_length; j++)
                segment[j] = string[i * segment_byte_length + j];
            double zero_ratio = getZeroRatio(segment, segment_length);
            ArrayList segment_list = new ArrayList();
            
            
            int compression_length = 0;
            
            if(zero_ratio >= .5)
            	compression_length = compressZeroStrings2(segment, segment_length, compressed_segment);
            else
            	compression_length = compressOneStrings2(segment, segment_length, compressed_segment);
            
            int byte_length = compression_length / 8;
            // Include odd bits.
            if(compression_length % 8 != 0)
            	byte_length++;
            // Include information about bit type and iterations.
            byte_length++;
            byte [] clipped_segment = new byte[byte_length];
            for(int j = 0; j < byte_length; j++)
            	clipped_segment[j] = compressed_segment[j];
            
            int iterations = getIterations(clipped_segment, compression_length);
           
            int offset = i + segment_length;
            
            
            
            segment_list.add(zero_ratio);
            segment_list.add(iterations);	
            segment_list.add(offset);
            segment_list.add(segment_length);
            segment_list.add(compression_length);
            segment_list.add(clipped_segment);
     
            init_list.add(segment_list);
        }
        
        // Get the last segment list that probably has an odd length.
        ArrayList segment_list       = new ArrayList();
        int       index              = number_of_segments - 1;
        byte []   segment            = new byte[last_segment_byte_length]; 
        byte []   compressed_segment = new byte[2 * last_segment_byte_length];
        for(int i = 0; i < last_segment_byte_length; i++)
        	segment[i] = string[index * segment_length + i];
        
        int compression_length  = 0;
        
        double zero_ratio = getZeroRatio(segment, last_segment_length);
        if(zero_ratio >= .5)
        	compression_length = compressZeroStrings2(segment, last_segment_length, compressed_segment);
        else
        	compression_length = compressOneStrings2(segment, last_segment_length, compressed_segment);
        
        int byte_length = compression_length / 8;
        // Include odd bits.
        if(compression_length % 8 != 0)
        	byte_length++;
        // Include information about bit type and iterations.
        byte_length++;
        byte [] clipped_segment = new byte[byte_length];
        for(int j = 0; j < byte_length; j++)
        	clipped_segment[j] = compressed_segment[j];
        
        int iterations = getIterations(clipped_segment, compression_length);
        int offset     = index + segment_length;
        
        
        
        
        
        
        segment_list.add(zero_ratio);
   	    segment_list.add(iterations);
   	    segment_list.add(offset);
   	    segment_list.add(last_segment_length);
   	    segment_list.add(compression_length);
   	    segment_list.add(clipped_segment);
      
        init_list.add(segment_list);
        
        
        // Finished constructing initial list.
        
        
           
        
        // Merge similar segments.
        
        ArrayList previous_list               = new ArrayList();
        ArrayList current_list                = new ArrayList();
        int       current_number_of_segments  = init_list.size();
        int       previous_number_of_segments = 0;
       
        for(int i = 0; i < init_list.size(); i++)
        {
        	segment_list = (ArrayList)init_list.get(i);
        	previous_list.add(segment_list);
        }
        
        while(current_number_of_segments != previous_number_of_segments)
        {
        	previous_number_of_segments = previous_list.size();
        	current_list.clear();
        
            int i = 0;
        	for(i = 0; i < previous_number_of_segments - 1; i++)
        	{
        	    ArrayList current_segment_list = (ArrayList)previous_list.get(i);
        	    ArrayList next_segment_list    = (ArrayList)previous_list.get(i + 1);
        	    
        	    double current_ratio = (double)current_segment_list.get(0);
        	    int    current_type = 0;
        	    if(current_ratio < .5)
        	    	current_type = 1;
        	    double next_ratio = (double)next_segment_list.get(0);
        	    int next_type = 0;
        	    if(next_ratio < .5)
        	    	next_type = 1;
        	    
        	    int current_iterations = (int)current_segment_list.get(1);
        	    int next_iterations    = (int)next_segment_list.get(1);
        	    
        	    if(current_type == next_type && current_iterations > 0 && next_iterations > 0)
        	    {
        	    	int current_offset = (int)current_segment_list.get(2);
        	    	int current_length = (int)current_segment_list.get(3);
        	    	int next_length    = (int)next_segment_list.get(3);
        	    	int merged_length  = current_length + next_length;
        	    	
        	    	int byte_offset = current_offset / 8;
        	    	byte_length = merged_length / 8;
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
        	    	int current_compression = (int)current_segment_list.get(4);
        	    	int next_compression    = (int)next_segment_list.get(4);
        	    	if(merged_compression_length <= current_compression + next_compression + 16)
        	    	{
        	    		double merged_ratio   = getZeroRatio(merged_segment, merged_length);
        	    		int merged_iterations = getIterations(compressed_merged_segment, merged_compression_length);
        	    		int compressed_byte_length = merged_compression_length / 8;
        				if(merged_compression_length % 8 != 0)
        				    compressed_byte_length++;
        				compressed_byte_length++;
        				
        				
        				
        				clipped_segment = new byte[compressed_byte_length];
        				for(int j = 0; j < compressed_byte_length; j++)
        					clipped_segment[j] = compressed_merged_segment[j];
        	    		
        	    		ArrayList merged_segment_list = new ArrayList();
        	    		merged_segment_list.add(merged_ratio);
        	    		merged_segment_list.add(merged_iterations);
        	    		merged_segment_list.add(current_offset);
        	    		merged_segment_list.add(merged_length);
        	    		merged_segment_list.add(merged_compression_length);
        	    		merged_segment_list.add(clipped_segment);
        	    
        	    		current_list.add(merged_segment_list);
            	        
        	    		i++;
        	    	}
        	    	else
        	    		current_list.add(current_segment_list);
        	    }
        	    else if(current_iterations == 0 && next_iterations == 0)
        	    {
        	    	int current_offset = (int)current_segment_list.get(2);
        	    	int current_length = (int)current_segment_list.get(3);
        	    	int next_length    = (int)next_segment_list.get(3);
        	    	int merged_length  = current_length + next_length;
        	    	
        	    	int byte_offset = current_offset / 8;
        	    	byte_length = merged_length / 8;
        	    	if(merged_length % 8 != 0)
        	    		byte_length++;
        	    	
        	    	if(merged_length % 8 != 0  && i != previous_number_of_segments - 2)
        	    		System.out.println("Merged length not evenly divisible by 8 at index = " + i);
        	    	byte [] merged_segment = new byte[byte_length];
        	    	for(int j = 0; j < byte_length; j++)
        	    		merged_segment[j] = string[j + byte_offset];   
        	    	double merged_ratio = getZeroRatio(merged_segment, merged_length);
        	    	
        	    	
        	    	// Test to see if the merged segments compress.
        	    	
        	    	byte [] compressed_merged_segment = new byte[merged_segment.length * 2];
        	    	int compression_lengt = 0;
        	    	if(merged_ratio >= .5)
        	    		compression_length = compressZeroStrings2(merged_segment, merged_length, compressed_merged_segment);
        	    	else
        	    		compression_length = compressOneStrings2(merged_segment, merged_length, compressed_merged_segment);
        	    	int merged_iterations = 0;
        	    	if(compression_length < merged_length)
        	    	{
        	    		//System.out.println("Merged segments that did not compress, compress after merging.");
        	    		
        	    		merged_iterations = getIterations(compressed_merged_segment, compression_length);
        	    		int compressed_byte_length = compression_length / 8;
        				if(compression_length % 8 != 0)
        				    compressed_byte_length++;
        				compressed_byte_length++;
        				
        				clipped_segment = new byte[compressed_byte_length];
        				for(int j = 0; j < compressed_byte_length; j++)
        					clipped_segment[j] = compressed_merged_segment[j];
        	    	}
        	    	else
        	    	{
        	    		if(compression_length > merged_length)
        	    			System.out.println("Compression length is an unexpected value.");
        	    		
        	    		byte_length = merged_length / 8;
        	    		if(merged_length % 8 != 0)
        	    			byte_length++;
        	    		byte_length++;
        	    	    clipped_segment    = new byte[byte_length];
                	    for(int j = 0; j < byte_length; j++)
                		    clipped_segment[j] = merged_segment[j];
                	    clipped_segment[merged_segment.length] = 0;
        	    	}
        	    	
        	    	ArrayList merged_segment_list = new ArrayList();
        	    	merged_segment_list.add(merged_ratio);
    	    		merged_segment_list.add(merged_iterations);
    	    		merged_segment_list.add(current_offset);
    	    		merged_segment_list.add(merged_length);
    	    		merged_segment_list.add(compression_length);
    	    		merged_segment_list.add(clipped_segment);
    	    		
    	    		current_list.add(merged_segment_list);
    	    		i++;
        	    }
        	    else
        	    	current_list.add(current_segment_list);
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
    		    segment_list = (ArrayList)current_list.get(i);
    		    previous_list.add(segment_list);
    	    }
    	    // Previous list now has current list data.
        }
       
       
        if(debug)
        {
        	System.out.println("Length of initial list is " + init_list.size());
            
            System.out.println("Length of merged list is " + current_list.size());
            int n = current_list.size();
            for(int i = 0; i < n; i++)
            {
                segment_list = (ArrayList)current_list.get(i);	
            
                zero_ratio   = (double)segment_list.get(0);
                iterations   = (int)segment_list.get(1);
                offset       = (int)segment_list.get(2);
                length       = (int)segment_list.get(3);
                int t_length = (int)segment_list.get(4);
                byte [] data = (byte [])segment_list.get(5);
                
            
                byte_length = length / 8;
                
                if(length % 8 != 0)
                { 
            	    byte_length++;
            	    if(i != n - 1)
            	    {
            		    System.out.println("Segment had uneven length " + length + " at index = " + i);
            	    }
                }
                segment = new byte[byte_length];
                compressed_segment = new byte[2 * byte_length];
                byte [] decompressed_segment = new byte[2 * byte_length];
         
                if(offset % 8 != 0)
            	    System.out.println("The offset is not a multiple of 8.");
            
                int start = offset / 8;
                for(int j = start; j < start + byte_length; j++)
                    segment[j - start] = string[j];
            
                if(iterations == 0)
                {
                    compression_length = 0;
            	    if(zero_ratio >= .5)
                        compression_length = compressZeroStrings2(segment, length, compressed_segment);
            	    else
            	    	compression_length = compressOneStrings2(segment, length, compressed_segment);
            		if(compression_length < length)
            			System.out.println("Segment compressed although list says 0 iterations.");
            		boolean same_data = true;
            		for(int j = 0; j < byte_length - 2; j++)
            			if(segment[j] != data[j])
            				same_data = false;
            		if(!same_data)
            		    System.out.println("Original data does not agree with processed data.");
            		
                }
                else
                {
                	compression_length = 0;
                	if(zero_ratio >= .5)
                		compression_length = compressZeroStrings2(segment, length, compressed_segment);
                	else
                		compression_length = compressOneStrings2(segment, length, compressed_segment);
                    int current_iterations = getIterations(compressed_segment, compression_length);;
                    if(current_iterations != iterations)
                        System.out.println("Current iterations does not agree with iterations on the list.");
                    if(t_length != compression_length)
                    {
                 		System.out.println("Transform lengths do not agree at index " + i); 
                 		System.out.println("List value = " + t_length + ", current value = " + compression_length);
                 		segment_list.add(4, compression_length);
                    }
                    int decompression_length = 0;
                    if(zero_ratio >= .5)
                		decompression_length = decompressZeroStrings(data, t_length, decompressed_segment);
                	else
                		decompression_length = decompressOneStrings(data, t_length, decompressed_segment);
                    boolean same_data = true;
            		for(int j = 0; j < byte_length - 2; j++)
            			if(segment[j] != decompressed_segment[j])
            				same_data = false;
            		if(!same_data)
            		    System.out.println("Original data does not agree with processed data.");
                }
            }
        }
       
        return current_list;
    }
   
    
    public static ArrayList getTransformInformation2(byte [] string, int length, int minimum_length)
    {
    	boolean debug = true;
    	ArrayList data_list = new ArrayList();
    
        int number_of_segments = length / minimum_length;
        int segment_length     = minimum_length / 8;
    	
        int remainder           = length % minimum_length;
        int last_segment_length = segment_length + remainder / 8;
        if(remainder % 8 != 0)
        	last_segment_length++;
        
        
        ArrayList string_list = new ArrayList();
     
        for(int i = 0; i < number_of_segments - 1; i++)
        {
            byte [] segment = new byte[segment_length];	
            byte [] compressed_segment = new byte[2 * segment_length];
            for(int j = 0; j < segment_length; j++)
                segment[j] = string[i * segment_length + j];
            double zero_ratio = getZeroRatio(segment, minimum_length);
            ArrayList segment_list = new ArrayList();
            
            
            int compression_length = 0;
            int iterations         = 0;
            if(zero_ratio >= .5)
            	compression_length = compressZeroStrings2(segment, minimum_length, compressed_segment);
            else
            	compression_length = compressOneStrings2(segment, minimum_length, compressed_segment);
            
            byte [] t_segment = new byte[1];
            if(compression_length == minimum_length)
            {
            	t_segment    = new byte[segment.length + 1];
            	for(int j = 0; j < segment.length; j++)
            		t_segment[j] = segment[j];
            	t_segment[segment.length] = 0;
            }
            else
            {
            	iterations = getIterations(compressed_segment, compression_length);
            	int compressed_byte_length = compression_length / 8;
                
            	// To include odd bits.
    			if(compression_length % 8 != 0)
    				compressed_byte_length++;
    			
    			// To include overhead--iterations and transform type.
    			compressed_byte_length++;
    			
    			t_segment = new byte[compressed_byte_length];
    			for(int k = 0; k < compressed_byte_length; k++)
    				t_segment[k] = compressed_segment[k];
            }
            
            
            segment_list.add(zero_ratio);
            segment_list.add(iterations);	
            segment_list.add(i * minimum_length);
            segment_list.add(minimum_length);
            segment_list.add(compression_length);
            segment_list.add(t_segment);
     
            string_list.add(segment_list);
        }
        
        // Get the last segment list that might have an odd length.
        ArrayList segment_list = new ArrayList();
        int       i            = number_of_segments - 1;
        byte []   segment      = new byte[last_segment_length]; 
        byte [] compressed_segment = new byte[2 * last_segment_length];
        for(int j = 0; j < last_segment_length; j++)
        	segment[j] = string[i * segment_length + j];
        
        int compression_length  = 0;
        int iterations          = 0;
        byte [] t_segment = new byte[1];
        
        double zero_ratio = getZeroRatio(segment, minimum_length + remainder);
        if(zero_ratio >= .5)
        	compression_length = compressZeroStrings2(segment, minimum_length + remainder, compressed_segment);
        else
        	compression_length = compressOneStrings2(segment, minimum_length + remainder, compressed_segment);
        if(compression_length == minimum_length + remainder)
        {
        	t_segment    = new byte[segment.length + 1];
        	for(int j = 0; j < segment.length; j++)
        		t_segment[j] = segment[j];
        	t_segment[segment.length] = 0;
        }
        else
        {
        	iterations = getIterations(compressed_segment, compression_length);
        	int compressed_byte_length = compression_length / 8;
			if(compression_length % 8 != 0)
				compressed_byte_length++;
			compressed_byte_length++;
			t_segment = new byte[compressed_byte_length];
			for(int k = 0; k < compressed_byte_length; k++)
				t_segment[k] = compressed_segment[k];
        }
        
        segment_list.add(zero_ratio);
   	    segment_list.add(iterations);
   	    segment_list.add(i * minimum_length);
   	    segment_list.add(minimum_length + remainder);
   	    segment_list.add(compression_length);
   	    segment_list.add(t_segment);
      
        string_list.add(segment_list);
        
        
        
       
        number_of_segments = string_list.size();
        int adaptive_length1 = 0;
        for(i = 0; i < number_of_segments; i++)
        {
        	segment_list = (ArrayList)string_list.get(i);
        	adaptive_length1 += (int)segment_list.get(4);
        }
       
        // Finished constructing initial list.
        
        
           
        
        // Merge similar segments.
        
        ArrayList previous_list               = new ArrayList();
        ArrayList current_list                = new ArrayList();
        int       current_number_of_segments  = string_list.size();
        int       previous_number_of_segments = 0;
       
        for(i = 0; i < string_list.size(); i++)
        {
        	segment_list = (ArrayList)string_list.get(i);
        	previous_list.add(segment_list);
        }
        
        while(current_number_of_segments != previous_number_of_segments)
        {
        	previous_number_of_segments = previous_list.size();
        	current_list.clear();
        

        	for(i = 0; i < previous_number_of_segments - 1; i++)
        	{
        	    ArrayList current_segment_list = (ArrayList)previous_list.get(i);
        	    ArrayList next_segment_list    = (ArrayList)previous_list.get(i + 1);
        	    
        	    double current_ratio = (double)current_segment_list.get(0);
        	    int    current_type = 0;
        	    if(current_ratio < .5)
        	    	current_type = 1;
        	    double next_ratio = (double)next_segment_list.get(0);
        	    int next_type = 0;
        	    if(next_ratio < .5)
        	    	next_type = 1;
        	    
        	    int current_iterations = (int)current_segment_list.get(1);
        	    int next_iterations    = (int)next_segment_list.get(1);
        	    
        	    if(current_type == next_type && current_iterations > 0 && next_iterations > 0)
        	    {
        	    	int current_offset = (int)current_segment_list.get(2);
        	    	int current_length = (int)current_segment_list.get(3);
        	    	int next_length    = (int)next_segment_list.get(3);
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
        	    	int current_compression = (int)current_segment_list.get(4);
        	    	int next_compression    = (int)next_segment_list.get(4);
        	    	if(merged_compression_length <= current_compression + next_compression + 16)
        	    	{
        	    		double merged_ratio   = getZeroRatio(merged_segment, merged_length);
        	    		int merged_iterations = getIterations(compressed_merged_segment, merged_compression_length);
        	    		int compressed_byte_length = merged_compression_length / 8;
        				if(merged_compression_length % 8 != 0)
        				    compressed_byte_length++;
        				compressed_byte_length++;
        				
        				t_segment = new byte[compressed_byte_length];
        				for(int k = 0; k < compressed_byte_length; k++)
        					t_segment[k] = compressed_merged_segment[k];
        	    		
        	    		ArrayList merged_segment_list = new ArrayList();
        	    		merged_segment_list.add(merged_ratio);
        	    		merged_segment_list.add(merged_iterations);
        	    		merged_segment_list.add(current_offset);
        	    		merged_segment_list.add(merged_length);
        	    		merged_segment_list.add(merged_compression_length);
        	    		merged_segment_list.add(t_segment);
        	    
        	    		current_list.add(merged_segment_list);
            	        
        	    		i++;
        	    	}
        	    	else
        	    		current_list.add(current_segment_list);
        	    }
        	    else if(current_iterations == 0 && next_iterations == 0)
        	    {
        	    	int current_offset = (int)current_segment_list.get(2);
        	    	int current_length = (int)current_segment_list.get(3);
        	    	int next_length    = (int)next_segment_list.get(3);
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
        	    	double merged_ratio = getZeroRatio(merged_segment, merged_length);
        	    	
        	    	
        	    	// Test to see if the merged segments compress.
        	    	
        	    	byte [] compressed_merged_segment = new byte[merged_segment.length * 2];
        	    	int compression_lengt = 0;
        	    	if(merged_ratio >= .5)
        	    		compression_length = compressZeroStrings2(merged_segment, merged_length, compressed_merged_segment);
        	    	else
        	    		compression_length = compressOneStrings2(merged_segment, merged_length, compressed_merged_segment);
        	    	int merged_iterations = 0;
        	    	if(compression_length < merged_length)
        	    	{
        	    		//System.out.println("Merged segments that did not compress, compress after merging.");
        	    		
        	    		merged_iterations = getIterations(compressed_merged_segment, compression_length);
        	    		int compressed_byte_length = compression_length / 8;
        				if(compression_length % 8 != 0)
        				    compressed_byte_length++;
        				compressed_byte_length++;
        				
        				t_segment = new byte[compressed_byte_length];
        				for(int k = 0; k < compressed_byte_length; k++)
        					t_segment[k] = compressed_merged_segment[k];
        	    	}
        	    	else
        	    	{
        	    		
        	    		if(compression_length > merged_length)
        	    			System.out.println("Compression length is an unexpected value.");
        	    	    t_segment    = new byte[merged_segment.length + 1];
                	    for(int j = 0; j < merged_segment.length; j++)
                		    t_segment[j] = merged_segment[j];
                	    t_segment[merged_segment.length] = 0;
        	    	}
        	    	
        	    	ArrayList merged_segment_list = new ArrayList();
        	    	merged_segment_list.add(merged_ratio);
    	    		merged_segment_list.add(merged_iterations);
    	    		merged_segment_list.add(current_offset);
    	    		merged_segment_list.add(merged_length);
    	    		merged_segment_list.add(compression_length);
    	    		merged_segment_list.add(t_segment);
    	    		
    	    		current_list.add(merged_segment_list);
    	    		i++;
        	    }
        	    else
        	    	current_list.add(current_segment_list);
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
    		    segment_list = (ArrayList)current_list.get(i);
    		    previous_list.add(segment_list);
    	    }
    	    // Previous list now has current list data.
        	
        }
       
        
        int adaptive_length2 = 0;
        int n = current_list.size();
        for(i = 0; i < n; i++)
        {
            segment_list = (ArrayList)current_list.get(i);
            adaptive_length2 += (int)segment_list.get(4);
        }
        
        
        if(debug)
        {
        	System.out.println("Length of initial list is " + string_list.size());
            System.out.println("Adaptive length collected from initial list was " + adaptive_length1);
            System.out.println("Length of merged list is " + current_list.size());
            System.out.println("Adaptive length collected from merged list is " + adaptive_length2);
            
            adaptive_length2 = 0;
            for(i = 0; i < n; i++)
            {
                segment_list = (ArrayList)current_list.get(i);	
            
                zero_ratio     = (double)segment_list.get(0);
                iterations     = (int)segment_list.get(1);
                int offset     = (int)segment_list.get(2);
                int bit_length = (int)segment_list.get(3);
                int t_length   = (int)segment_list.get(4);
            
                int byte_length = bit_length / 8;
                
                if(bit_length % 8 != 0)
                { 
            	    byte_length++;
            	    if(i != n - 1)
            	    {
            		    System.out.println("Segment had uneven length " + bit_length + " at index = " + i);
            	    }
                }
                segment = new byte[byte_length];
                byte [] compressed_string = new byte[2 * byte_length];
         
                if(offset % 8 != 0)
            	    System.out.println("The offset is not a multiple of 8.");
            
                int start = offset / 8;
                for(int j = start; j < start + byte_length; j++)
                    segment[j - start] = string[j];
            
                if(iterations == 0)
                {
                    compression_length = 0;
            	    if(zero_ratio >= .5)
                        compression_length = compressZeroStrings2(segment, bit_length, compressed_string);
            	    else
            	    	compression_length = compressOneStrings2(segment, bit_length, compressed_string);
            		if(compression_length < bit_length)
            		{
            			System.out.println("Segment compressed although list says 0 iterations.");
            			adaptive_length2 += compression_length;
            		}
            		else
                     	adaptive_length2  += bit_length;	
                }
                else
                {
                	compression_length = 0;
                	if(zero_ratio >= .5)
                		compression_length = compressZeroStrings2(segment, bit_length, compressed_string);
                	else
                		compression_length = compressOneStrings2(segment, bit_length, compressed_string);
                    int current_iterations = getIterations(compressed_string, compression_length);;
                    if(current_iterations != iterations)
                        System.out.println("Current iterations does not agree with iterations on the list.");
                    if(t_length != compression_length)
                    {
                 		System.out.println("Transform lengths do not agree at index " + i); 
                 		System.out.println("List value = " + t_length + ", current value = " + compression_length);
                 		segment_list.add(4, compression_length);
                    }
                    adaptive_length2      += compression_length;	
                }
            }
            System.out.println("Adaptive length produced from merged list is " + adaptive_length2);
        }
        
        //data_list.add(current_list);
        //data_list.add(adaptive_length2);
        
        //data_list.add(current_list);
        //data_list.add(adaptive_length1);
        return current_list;
    }
}