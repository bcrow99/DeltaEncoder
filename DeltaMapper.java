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
                       current_bit = dst[current_byte] = 0;
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
                       current_bit = dst[current_byte] = 0;
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
                    current_bit = dst[current_byte] = 0;
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
            else if(((src[k] & (mask << j)) != 0) && i == size - 1)
            {
            	// Append an odd 0.
            	current_bit++;
                if(current_bit == 8)
                {
                    current_byte++;
                    current_bit = dst[current_byte] = 0;
                }	
            }
            else if((src[k] & (mask << j)) == 0)
            {
                current_bit++;
                if(current_bit == 8)
                {
                    current_byte++;
                    if(current_byte < dst.length)
                        current_bit = dst[current_byte] = 0;
                }
                current_bit++;
                if(current_bit == 8)
                {
                    current_byte++;
                    if(current_byte < dst.length)
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
       
        int number_of_bits = current_byte * 8;
        number_of_bits += current_bit;
        return(number_of_bits);
    }
   
    public static int compressZeroStrings(byte src[], int size, byte dst[])
    {
        byte[]  temp = new byte[src.length * 2];
        
        int number_of_iterations = 1;
        int previous_size        = size;
        int current_size         = compressZeroBits(src, previous_size, dst);
        while(current_size < previous_size)
        {
            previous_size = current_size;
            if(number_of_iterations % 2 == 1)
            {
                current_size = compressZeroBits(dst, previous_size, temp);
            }
            else
            {
                current_size = compressZeroBits(temp, previous_size, dst);
            }
            number_of_iterations++;
        }
        if(number_of_iterations > 1)
        {
        	// This means our first pass did not expand the data,
        	// and we broke out of a loop when we finally did.
            current_size = previous_size;
            number_of_iterations--;
        }
        if(number_of_iterations % 2 == 0) 
        {
        	// The last recursion used temp as a source,
        	// which then produced a string longer than the previous one,
        	// so we need to copy the data from temp to dst.
            int byte_size = current_size / 8;
            if(current_size % 8 != 0)
                byte_size++; 
            for(int i = 0; i < byte_size; i++)
                dst[i] = temp[i];
        }   
        // else the result is already in dst.
    
        if(current_size % 8 == 0)
        {
            int byte_size      = current_size / 8;
            dst[byte_size] = (byte) number_of_iterations;
            dst[byte_size] &= 127;
        }
        else
        {
            int  remainder = current_size % 8;
            int byte_size = current_size / 8;

            dst[byte_size] |= (byte) (number_of_iterations << remainder);
            byte_size++;
            dst[byte_size] = 0;
            if(remainder > 1)
                dst[byte_size] = (byte) (number_of_iterations >> 8 - remainder);
        }
        current_size += 9;
        
        
        int last_byte = current_size / 8 - 1;
        int remainder = current_size % 8;
        int last_bit  = 7;
        if(remainder != 0)
        {
            last_byte++;
            last_bit = remainder - 1;
        }
        byte mask  = (byte)0xfe;
        mask     <<= last_bit;
        
        dst[last_byte] &= mask;
        
        
        return(current_size);
    }
    
    public static int decompressZeroStrings(byte src[], int size, byte dst[])
    {
        int  byte_index;
        int  number_of_iterations;
        int  addend;
        int  mask;
        int  remainder, i;
        int  previous_size, current_size, byte_size;
        byte[]  temp = new byte[dst.length];
        
        // Getting the number of iterations appended to
        // the end of the string.
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
        
        //System.out.println("The number of iterations is " + number_of_iterations);
        
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
                       current_bit = dst[current_byte] = 0;
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
                       current_bit = dst[current_byte] = 0;
                   }
                   
                   // Put down a 1 bit.
                   dst[current_byte] |= (byte)mask << current_bit;
                   
                   // Move to the start of the next code.
                   current_bit++;
                   if(current_bit == 8)
                   {
                       current_byte++;
                       current_bit = dst[current_byte] = 0;
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
                    current_bit = dst[current_byte] = 0;
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
                        current_bit = dst[current_byte] = 0;
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
                        current_bit = dst[current_byte] = 0;
                    }
                    current_bit++;
                    if(current_bit == 8)
                    {
                        current_byte++;
                        current_bit = dst[current_byte] = 0;
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
                    current_bit = dst[current_byte] = 0;
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
                        current_bit = dst[current_byte] = 0;
                }
                dst[current_byte] |= (byte)mask << current_bit;
                current_bit++;
                if(current_bit == 8)
                {
                    current_byte++;
                    if(current_byte < dst.length)
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
       
        int number_of_bits = current_byte * 8;
        number_of_bits += current_bit;
        return(number_of_bits);
    }
   
    public static int compressOneStrings(byte src[], int size, byte dst[])
    {
        byte[]  temp = new byte[src.length * 2];
        
        int number_of_iterations = 1;
        int previous_size        = size;
        int current_size         = compressOneBits(src, previous_size, dst);
        while(current_size < previous_size)
        {
            previous_size = current_size;
            if(number_of_iterations % 2 == 1)
                current_size = compressOneBits(dst, previous_size, temp);
            else
                current_size = compressOneBits(temp, previous_size, dst);
            number_of_iterations++;
        }
        if(number_of_iterations > 1)
        {
        	// This means our first pass did not expand the data,
        	// and we broke out of a loop when we finally did.
            current_size = previous_size;
            number_of_iterations--;
        }
        if(number_of_iterations % 2 == 0) 
        {
        	// The last recursion used temp as a source,
        	// which then produced a string longer than the previous one,
        	// so we need to copy the data from temp to dst.
            int byte_size = current_size / 8;
            if(current_size % 8 != 0)
                byte_size++; 
            for(int i = 0; i < byte_size; i++)
                dst[i] = temp[i];
        }   
        // else the result is already in dst.
    
        if(current_size % 8 == 0)
        {
            int byte_size      = current_size / 8;
            dst[byte_size] = (byte) number_of_iterations;
            dst[byte_size] &= 127;
        }
        else
        {
            int  remainder = current_size % 8;
            int byte_size = current_size / 8;

            dst[byte_size] |= (byte) (number_of_iterations << remainder);
            byte_size++;
            dst[byte_size] = 0;
            if(remainder > 1)
                dst[byte_size] = (byte) (number_of_iterations >> 8 - remainder);
        }
        current_size += 9;
        int last_byte = current_size / 8 - 1;
        int remainder = current_size % 8;
        int last_bit  = 7;
        if(remainder != 0)
        {
            last_byte++;
            last_bit = remainder - 1;
        }
        
       // Might want to clear null bits first.
        byte mask        = 1;
        mask <<= last_bit;
        dst[last_byte] |= mask;
        return(current_size);
    }
    
    
    // Returns string length of packed strings, may include extra trailing bits.
    public static int decompressOneStrings(byte src[], int size, byte dst[])
    {
        int  byte_index;
        int  number_of_iterations;
        int  addend;
        int  mask;
        int  remainder, i;
        int  previous_size, current_size, byte_size;
        byte[]  temp = new byte[dst.length];
        
        // Getting the number of iterations appended to
        // the end of the string.
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
        
        current_size = 0;
        if(number_of_iterations == 1)
        {
            current_size = decompressOneBits(src, size - 8, dst);
            number_of_iterations--;
        }
        else if(number_of_iterations % 2 == 0)
        {
            current_size = decompressOneBits(src, size - 8, temp);
            number_of_iterations--;
            while(number_of_iterations > 0)
            {
                previous_size = current_size;
                if(number_of_iterations % 2 == 0)
                    current_size = decompressOneBits(dst, previous_size, temp);
                else
                    current_size = decompressOneBits(temp, previous_size, dst);
                number_of_iterations--;
            }
        }
        else
        {
            current_size = decompressOneBits(src, size - 8, dst);
            number_of_iterations--;
            while(number_of_iterations > 0)
            {
                previous_size = current_size;
                if(number_of_iterations % 2 == 0)
                    current_size = decompressOneBits(dst, previous_size, temp);
                else
                    current_size = decompressOneBits(temp, previous_size, dst);
                number_of_iterations--;
            }
        }
        return(current_size);
    } 
    
    public static ArrayList checkStringType(byte src[], int size)
    {
        int  byte_index;
        int  number_of_iterations;
        int  addend;
        int  mask;

 
        int last_byte = size / 8 - 1;
        int remainder = size % 8; 
        int last_bit = 7;
        if(remainder != 0)
        {
        	last_byte++;
        	last_bit = remainder - 1;
        }
     
        byte string_type   = 1;
        string_type <<= last_bit;
        string_type &= src[last_byte];
        if(string_type != 0)
        {
        	// The value can be 1 thru -127.
        	// We'll reset it to 1.
        	string_type = 1;
        	//System.out.println("String type is " + string_type);
        }
        else
        {
        	//System.out.println("String type is " + string_type);
        }
        size--;
        
        // Getting the number of iterations appended to
        // the end of the string.
        byte_index = size / 8 - 1;
        remainder  = size % 8;
        if(remainder != 0)
        {
            int value = 254;
            addend = 2;
            for(int i = 1; i < remainder; i++)
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
                for(int i = 2; i < remainder; i++)
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
        
        ArrayList result = new ArrayList();
        result.add(string_type);
        result.add(number_of_iterations);
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
    
    public static double getZeroOneRatio(int [] code, int [] length, int [] frequency)
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
    	ratio /= number_of_ones;
    	
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
    
    public static ArrayList getStringInformation(byte [] string, int bit_length, int max_length)
    {
    	ArrayList string_information = new ArrayList();
    	Hashtable zero_table         = new Hashtable();
    	Hashtable one_table          = new Hashtable();
    	ArrayList string_position    = new ArrayList();
    	ArrayList string_type        = new ArrayList();
    	ArrayList string_length      = new ArrayList();
    	
    	byte mask = 1;
    	int zero_maxlength = 1;
    	int one_maxlength  = 1;
    	
    	// Get the first bit.
    	byte init = string[0];
    	int previous_type = init & mask;
    	if(previous_type != 0)
    		previous_type = 1;
    	int  length = 1;
    	int  type   = 0;
    	string_position.add(0);
    	
    	// Initializing our data from the first byte.
    	// Assuming the bit string length is greater than 8.
    	for(int i = 1; i < 8; i++)
    	{
    		type = init & mask << i;	
    		if(previous_type == 0)
    		{
    			if(type == 0)
    			    length++;
    			else
    			{
    				// Add the length for the previous pixel.
    				string_length.add(length);
    				string_type.add(0);
    			    if(zero_table.containsKey(length))	
    			    {
    			        int value = (int)zero_table.get(length);
    			        value++;
    			        zero_table.put(length, value);
    			        
    			    }
    			    else
    			    {
    			        zero_table.put(length, 1);
    			        if(length > zero_maxlength)
    			        	zero_maxlength = length;
    			    }
    				
    			    string_position.add(i);
    			    previous_type = 1;
			        length        = 1;
    			}
    		}
    		else if(previous_type == 1)
    		{
    			if(type != 0)
    			{
    			    length++;
    			    
    			    // The max length only pertains to one strings.
    			    if(length == max_length)
    			    {
    			    	string_length.add(length);
    			    	string_type.add(2);
    			    	if(one_table.containsKey(length))	
        			    {
        			        int value = (int)one_table.get(length);
        			        value++;
        			        one_table.put(length, value);
        			    }
        			    else
        			    {
        			    	one_table.put(length, 1); 
        			    	if(length > one_maxlength)
        			        	one_maxlength = length;
        			    }
    			    	previous_type = 2;
        			    length        = 0;
    			    }
    			}
    			else
    			{
    				length++;
    				string_length.add(length);
    				string_type.add(1);
    			    if(one_table.containsKey(length))	
    			    {
    			        int value = (int)one_table.get(length);
    			        value++;
    			        one_table.put(length, value);
    			    }
    			    else
    			    {
    			    	one_table.put(length, 1); 
    			    	if(length > one_maxlength)
    			        	one_maxlength = length;
    			    }
    			    previous_type = 2;
    			    length        = 0;
    			}	
    		}
    		else if(previous_type == 2)
    		{
    		    if(type == 0)
    		        previous_type = 0;
    		    else
    		    	previous_type = 1;
    		    length = 1;
    		    string_position.add(i);
    		}
    	}
    	
    	// Now do all the bytes except the last one.
    	// This way we don't have to keep checking
    	// if we've reached the end of the bit stream.
    	int n = bit_length / 8;
    	for(int i = 1; i < n - 1; i++)
    	{
    		for(int j = 0; j < 8; j++)
    		{
    			type = string[i] & mask << j;	
        		if(previous_type == 0)
        		{
        			if(type == 0)
        			    length++;
        			else
        			{
        				// Add the length of the previous pixel.
        				string_length.add(length);
        				string_type.add(0);
        			    if(zero_table.containsKey(length))	
        			    {
        			        int value = (int)zero_table.get(length);
        			        value++;
        			        zero_table.put(length, value);
        			    }
        			    else
        			    {
        			        zero_table.put(length,1);
        			        if(length > zero_maxlength)
        			        	zero_maxlength = length;
        			    }
        			    
        			    // Add the type and initial bit position
        			    // of the current pixel.
        			    string_position.add(i * 8 + j);
        			    previous_type = 1;
    			        length = 1;
        			}
        		}
        		else if(previous_type == 1)
        		{
        			if(type != 0)
        			{
        			    length++;
        			    if(length == max_length)
        			    {
        			    	string_length.add(length);
        			    	string_type.add(2);
        			    	if(one_table.containsKey(length))	
            			    {
            			        int value = (int)one_table.get(length);
            			        value++;
            			        one_table.put(length, value);
            			    }
            			    else
            			    {
            			    	one_table.put(length,1); 
            			    	if(length > one_maxlength)
            			        	one_maxlength = length;
            			    }
        			    	previous_type = 2;
            			    length        = 0;	
        			    }
        			}
        			else
        			{
        				length++;
        				string_length.add(length);
        				string_type.add(1);
        			    if(one_table.containsKey(length))	
        			    {
        			        int value = (int)one_table.get(length);
        			        value++;
        			        one_table.put(length, value);
        			    }
        			    else
        			    {
        			    	one_table.put(length,1); 
        			    	if(length > one_maxlength)
        			        	one_maxlength = length;
        			    }
        			    previous_type = 2;
    			        length        = 0;
        			}	
        		}
        		else if(previous_type == 2)
        		{
        			if(type == 0)
        		        previous_type = 0;
        		    else
        		    	previous_type = 1;
        		    length = 1;	
        		    string_position.add(i * 8 + j);
        		}
    		}
    	}
    	
    	// Now check the last full byte.
    	n = bit_length / 8;
    	byte last = string[n - 1];
    	int remainder = bit_length % 8;
    	for(int i = 0; i < 7; i++)
    	{
    		type = last & mask << i;	
    		if(previous_type == 0)
    		{
    			if(type == 0)
    			    length++;
    			else
    			{
    				// Add the length for the previous pixel.
    				string_length.add(length);
    				string_type.add(0);
    			    if(zero_table.containsKey(length))	
    			    {
    			        int value = (int)zero_table.get(length);
    			        value++;
    			        zero_table.put(length, value);
    			        
    			    }
    			    else
    			    {
    			        zero_table.put(length, 1);
    			        if(length > zero_maxlength)
    			        	zero_maxlength = length;
    			    }
    				
    			    string_position.add(i);
    			    previous_type = 1;
			        length        = 1;
    			}
    		}
    		else if(previous_type == 1)
    		{
    			if(type != 0)
    			{
    			    length++;
    			    if(length == max_length)
    			    {
    			    	string_length.add(length);
    			    	string_type.add(2);
    			    	if(one_table.containsKey(length))	
        			    {
        			        int value = (int)one_table.get(length);
        			        value++;
        			        one_table.put(length, value);
        			    }
        			    else
        			    {
        			    	one_table.put(length,1); 
        			    	if(length > one_maxlength)
        			        	one_maxlength = length;
        			    }
    			    	previous_type = 2;
        			    length        = 0;
    			    }
    			}
    			else
    			{
    				length++;
    				string_length.add(length);
    				string_type.add(1);
    			    if(one_table.containsKey(length))	
    			    {
    			        int value = (int)one_table.get(length);
    			        value++;
    			        one_table.put(length, value);
    			    }
    			    else
    			    {
    			    	one_table.put(length, 1); 
    			    	if(length > one_maxlength)
    			        	one_maxlength = length;
    			    }
    			    previous_type = 2;
    			    length        = 0;
    			}	
    		}
    		else if(previous_type == 2)
    		{
    		    if(type == 0)
    		        previous_type = 0;
    		    else
    		    	previous_type = 1;
    		    length = 1;
    		    string_position.add(i);
    		}
    	}
    	
    	// Check the last bit in the last full byte.
    	type = last & mask << 7;
    	if(previous_type == 0)
		{
			if(type == 0)
			{
			    length++;
			    if(remainder == 0)
			    {
			    	string_length.add(length);
			    	string_type.add(0);
				    if(zero_table.containsKey(length))	
				    {
				        int value = (int)zero_table.get(length);
				        value++;
				        zero_table.put(length, value);
				        
				    }
				    else
				    {
				        zero_table.put(length, 1);
				        if(length > zero_maxlength)
				        	zero_maxlength = length;
				    }	
			    }
			}
			else
			{
				// Add the length for the previous pixel.
				string_length.add(length);
				string_type.add(0);
			    if(zero_table.containsKey(length))	
			    {
			        int value = (int)zero_table.get(length);
			        value++;
			        zero_table.put(length, value);
			        
			    }
			    else
			    {
			        zero_table.put(length, 1);
			        if(length > zero_maxlength)
			        	zero_maxlength = length;
			    }

		        if(remainder == 0)
		        {
		        	System.out.println("Trailing one bit in bit stream.");
		        }
		        else
		        {
		        	string_position.add(bit_length - 1);
			        //string_type.add(1);
				    previous_type = 1;
			        length        = 1;	
		        }
			}
		}
		else if(previous_type == 1)
		{
			if(type != 0)
			{
			    length++;
			    if(length == max_length)
			    {
			    	string_length.add(length);
			    	string_type.add(2);
			    	if(one_table.containsKey(length))	
    			    {
    			        int value = (int)one_table.get(length);
    			        value++;
    			        one_table.put(length, value);
    			    }
    			    else
    			    {
    			    	one_table.put(length,1); 
    			    	if(length > one_maxlength)
    			        	one_maxlength = length;
    			    }
			    	previous_type = 2;
    			    length        = 0;
			    }
			}
			else
			{
				length++;
				string_length.add(length);
				string_type.add(1);
			    if(one_table.containsKey(length))	
			    {
			        int value = (int)one_table.get(length);
			        value++;
			        one_table.put(length, value);
			    }
			    else
			    {
			    	one_table.put(length, 1); 
			    	if(length > one_maxlength)
			        	one_maxlength = length;
			    }
			    previous_type = 2;
			    length        = 0;
			}	
		}
		else if(previous_type == 2)
		{
		    if(type == 0)
		    {
		    	string_position.add(n * 8 - 1);
		        
		        previous_type = 0;
		        length = 1;
		        if(remainder == 0)
		        {
		        	string_length.add(length);
		        	string_type.add(0);
		        	int value = (int)zero_table.get(length);
		        	value++;
			        zero_table.put(length, value);
		        }
		    }
		    else
		    {
		    	if(remainder == 0)
		        {
		    		System.out.println("Trailing one bit in bitstream.");
		        }
		        else
		        {
		        	string_position.add(n * 8 - 1);
			        previous_type = 1;
			        length = 1;   	
		        }
		    }
		}
    	
    	if(remainder == 0)
    		System.out.println("Bit string is even.");
    	if(remainder != 0) // We need to finish up the last odd byte.
    	{
    		byte extra     = string[n];
    		for(int i = 0; i < remainder; i++)
    		{
    			type = extra & mask << i;	
        		if(previous_type == 0)
        		{
        			if(type == 0)
        			{
        			    length++;
        			    if(i == remainder - 1)
        			    {
        			    	string_length.add(length);
        			    	string_type.add(0);
        			    }
        			}
        			else
        			{
        			    string_length.add(length);
        			    string_type.add(0);
        			    if(zero_table.containsKey(length))	
        			    {
        			        int value = (int)zero_table.get(length);
        			        value++;
        			        zero_table.put(length, value);
        			    }
        			    else
        			    {
        			        zero_table.put(length, 1);
        			        if(length > zero_maxlength)
        			        	zero_maxlength = length;
        			    }
        			    if(i != remainder - 1)
        			    {
        			        string_position.add(n * 8 + i);
        			        previous_type = 1;
    			            length = 1;
        			    }
        			    else
        			    	System.out.println("Trailing one bit in bit stream.");
        			}
        		}
        		else if(previous_type == 1)
        		{
        			if(type != 0)
        			{
        			    length++;
        			    if(length == max_length)
        			    {
        			    	string_length.add(length);
        			    	string_type.add(2);
        			    	if(one_table.containsKey(length))	
            			    {
            			        int value = (int)one_table.get(length);
            			        value++;
            			        one_table.put(length, value);
            			    }
            			    else
            			    {
            			    	one_table.put(length,1); 
            			    	if(length > one_maxlength)
            			        	one_maxlength = length;
            			    }
        			    	previous_type = 2;
            			    length        = 0;
        			    }
        			}
        			else
        			{
        				length++;
        			    if(one_table.containsKey(length))	
        			    {
        			        int value = (int)one_table.get(length);
        			        value++;
        			        one_table.put(length, value);
        			    }
        			    else
        			    {
        			    	one_table.put(length,1); 
        			    	if(length > one_maxlength)
        			        	one_maxlength = length;
        			    }
        			    
        			    if(i == remainder - 1)
        			    {
        			        string_type.add(0);
        			        string_length.add(1);
        			    }
        			    else
        			    {
        			    	string_position.add(n * 8 + i);
        			        previous_type = 0;
    			            length = 1;
        			    }
        			}	
        		}
        		else if(previous_type == 2)
        		{
        		    if(type == 0)	
        		    {
        		    	string_position.add(n * 8 + i);
        			    previous_type = 0;
    			        length = 1;	
    			        if(i == remainder - 1)
    			        {
    			        	string_type.add(0);
    			        	string_length.add(length);
    			        }
        		    }
        		    else
        		    {
        		        if(i == remainder - 1)
        		        	System.out.println("Trailing one bit in bitstream.");
        		        else
        		        {
        		        	string_position.add(n * 8 + i);
            			    previous_type = 1;
        			        length = 1;	
        		        }
        		    }
        		}
    		}
    	}
    	
    	
        if(one_table.containsKey(1))
        	System.out.println("One table contains strings of length 1");
        else
        	System.out.println("One table does not contain strings of length 1");
        
        if(one_table.containsKey(max_length + 1))
        	System.out.println("One table contains length greater than maxlength.");
        else
        	System.out.println("One table does not contain length greater than maxlength.");
        
        
    	int size = zero_table.size();
    	//System.out.println("Zero table size is " + size);
    	//System.out.println("Max length for a zero string is " + zero_maxlength);
    	size = one_table.size();
    	//System.out.println("One table size is " + size);
    	//System.out.println("Max length for a one string is " + one_maxlength);
    	
    	// Convert the hashtable data to simple lists.
    	ArrayList zero_list = new ArrayList();
    	for(int i = 0; i < zero_maxlength; i++)
    	{
    		if(zero_table.containsKey(i + 1))
    		{
    			int value = (int)zero_table.get(i + 1);
    			zero_list.add(value);
    		}
    		else
    			zero_list.add(0);
    	}
    	
    	ArrayList one_list = new ArrayList();
    	for(int i = 0; i < one_maxlength; i++)
    	{
    		if(one_table.containsKey(i + 1))
    		{
    			int value = (int)one_table.get(i + 1);
    			one_list.add(value);
    		}
    		else
    			one_list.add(0);
    	}
    	
    	string_information.add(zero_list);
    	string_information.add(one_list);
    	string_information.add(string_position);
    	string_information.add(string_type);
    	string_information.add(string_length);
    	return string_information;
    }
    
    public static ArrayList getSlidingWindowRatio(byte [] string, int length, int number_of_bits)
    {
        ArrayList ratio_list = new ArrayList();
        int n                = length - number_of_bits;
        double [] ratio      = new double[n];
        int    [] start      = new int[n];
        int    [] end        = new int[n];
        
        int  zero_sum   = 0;
        int  one_sum    = 0;
        byte mask       = 1;
        int  start_byte = 0;
        int  end_byte   = 0;
    	int first_bit = string[start_byte] & mask;
    	if(first_bit == 0)
    	    zero_sum++;
    	else
    		one_sum++;
    	
    	int start_shift = 1;
    	int end_shift   = 1;
    	for(int i = 1; i < number_of_bits; i++)
    	{
    	    int bit = string[end_byte] & mask << end_shift;   	
    	    if(bit == 0)
     	       zero_sum++;
     	    else
     		   one_sum++;
    	    end_shift++;
    	    if(end_shift == 8)
    	    { 
    	    	end_shift = 0;
    	    	end_byte++;
    	    }
    	}
    	
    	
    	double current_ratio = zero_sum;
    	current_ratio       /= (one_sum + zero_sum);
    	ratio[0] = current_ratio;
    	
        int i = 1; 
    	while(i < n)
    	{
    		// Remove a value from our sliding window.
    		if(first_bit == 0)
    			zero_sum--;
    		else
    			one_sum--;
    		
    		// This value is already part of our sum, but we want to
    		// know what it is so we can decrement it from our sum
    		// in the next loop.
    		first_bit    = string[start_byte] & mask << start_shift;
    		start_shift++;
    		if(start_shift == 8)
    		{
    			start_shift = 0;
    			start_byte++;
    		}
    		
    		// New value.
    		int last_bit = string[end_byte] & mask << end_shift; 
    		if(last_bit == 0)
    			zero_sum++;
    		else
    			one_sum++;
    		end_shift++;
    		if(end_shift == 8)
    		{
    			end_shift = 0;
    			end_byte++;
    		}
    		
    		current_ratio  = zero_sum;
        	current_ratio /= (zero_sum + one_sum);
        	ratio[i++]   = current_ratio;
    	}
    	
    	ratio_list.add(ratio);
        return ratio_list;
    }
   
    public static int getLengthDifference(int length, int bit_type, int transform_type, int iterations)
    {
        int difference = 0;
        
        if(bit_type == transform_type)
        {
            int positive = length;
            int negative = 0;
            int previous = length;
            int current  = 0;
            for(int i = 0; i < iterations; i++)
            {
            	current   = 2 * negative;
            	positive /= 2;
            	current += positive;
            	if(previous % 2 != 0)
            	{
            		current++;
            		negative++;
            	}
            	previous = current; 
            }
            difference = current - length;
        }
        else
        {
            int expanded_length = (int)Math.pow(2, iterations) * length;
            difference = expanded_length - length;
        }
        
        return difference;
    }
    public static int getLowerBound(int length, int iterations)
    {
    	int bound = length;
    	for(int i = 0; i < iterations; i++)
    		bound = (bound - 1) / 2;
    	return bound;
    }
    
    public static int getUpperBound(int length, int iterations)
    {
    	int bound = length;
    	for(int i = 0; i < iterations; i++)
    		bound = (bound - 1) * 2;
    	return bound;
    }
    
    public static int getRecursiveLimit(int length)
    {
    	int limit = 0;
    	if(length >= 3)
    	{
    		limit = 1;
    		int current = length;
    		while(current > 4)
    		{
    			limit++;
    			current = (current - 1)/ 2;
    		}
    	}
    	return limit;
    }
    
    public static int [] getLimitTable(int length)
    {
    	int [] limit = new int[length + 1];
    	for(int i = 0; i <= length; i++)
    		limit[i] = getRecursiveLimit(i);
    	return limit;
    }
}