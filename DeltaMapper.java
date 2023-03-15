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
	
	public static int[] getRandomTable(int histogram[])
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
	
	
	public static int getVerticalDeltaSum(int src[], int xdim, int ydim)
	{
		int sum = 0;
		for(int i = 1; i < ydim - 1; i++)
		{
			for(int j = 1; j < xdim - 1; j++)
			{
			    int delta = src[i * xdim + j] - src[i * xdim + j - 1];
			    sum += Math.abs(delta);
			}
		}
		return sum;
	}
	
	public static int getHorizontalDeltaSum(int src[], int xdim, int ydim)
	{
		int sum = 0;
		for(int i = 1; i < ydim - 1; i++)
		{
			for(int j = 1; j < xdim - 1; j++)
			{
				int delta = src[i * xdim + j] - src[(i - 1) * xdim + j - 1];
			    sum += Math.abs(delta);	
			}
		}
		return sum;
	}
	
	
	// These function use horizontal deltas.
    public static int[] getValuesFromAbsoluteDeltas1(ArrayList delta_list, int xdim, int ydim)
    {
    	int init_value = (int)delta_list.get(0);
    	int[] map      = (int[])delta_list.get(1);
    	int[] delta    = (int[])delta_list.get(2);
    	
    	int[] dst      = new int[xdim * ydim];
        int k          = 0;
        int value      = init_value;
        int current_value = value;
        
        for(int i = 0; i < ydim; i++)
        {
            if(i == 0)
            {
                dst[k]  = init_value; 
                k++;
            } 
            else
            {  
                if(map[k] == 1)
                    value -= delta[k];
                else
                    value += delta[k];
                dst[k] = value;
                k++;
                current_value = value;
            }
            
            for(int j = 1; j < xdim; j++)
            {
                if(map[k] == 1)
                    current_value -= delta[k];
                else
                    current_value += delta[k];
                dst[k]       = current_value;
                k++;
            }
        }
        return dst;
    }
	
	public static ArrayList getAbsoluteDeltasFromValues1(int src[], int xdim, int ydim)
    {
        int[] dst = new int[xdim * ydim];
        int[] map = new int[xdim * ydim];
        int   init_value = src[0];
        
        int k     = 0;
        int value = init_value;
        for(int i = 0; i < ydim; i++)
        {
        	// We set the first value to zero to mark the type of deltas as horizontal.
            if(i == 0)
        	    dst[k++] = 0;
            else
            {
            	int delta  = src[k] - init_value;
            	dst[k]     = Math.abs(delta);
            	if(delta >= 0)
            	    map[k] = 0;
            	else
            	    map[k] = 1;
            	init_value += delta;
            	value = init_value;
            	k++;
            }
            for(int j = 1; j < xdim; j++)
            {
                int delta      = src[k] - value;
                value += delta;
                dst[k]       = Math.abs(delta);
                if(delta >= 0)
            	    map[k] = 0;
            	else
            	    map[k] = 1;
            }
        }
        
        ArrayList result = new ArrayList();
        result.add(src[0]);
        result.add(map);
        result.add(dst);
        return result;
    }
    
	// These functions use the sums of the vertical deltas and horizontal deltas 
	// to decide which deltas to use in compression.
	// Those values work better than the sum of the absolute values as a heuristic
	// to decide which to use.
	public static int[] getDeltasFromValues(int src[], int xdim, int ydim, int init_value)
    {
		int [] delta1     = getDeltasFromValues1(src, xdim, ydim, init_value);
		int    delta_sum1 = getHorizontalDeltaSum(delta1, xdim, ydim);
    	int [] delta2     = getDeltasFromValues2(src, xdim, ydim, init_value);
		int    delta_sum2 = getVerticalDeltaSum(delta2, xdim, ydim);
    	
		if(delta_sum1 <= delta_sum2)
		{
			//System.out.println("Using horizontal deltas.");
			return delta1;
		}
		else
		{
			//System.out.println("Using vertical deltas.");
			return delta2;
		}
    }
	
    public static int[] getDeltasFromValues1(int src[], int xdim, int ydim, int init_value)
    {
        int[] dst = new int[xdim * ydim];
    	
        int k     = 0;
        int value = init_value;
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
            	value = init_value;
            }
            for(int j = 1; j < xdim; j++)
            {
                int delta      = src[k]  - value;
                value += delta;
                dst[k++]       = delta;
            }
        }
        
        return dst;
    }
    
    public static int[] getValuesFromDeltas(int src[],int xdim, int ydim, int init_value)
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
        else
        {
        	// We could throw an exception here but will just return an 
        	// uninitialized array.
        	System.out.println("Type of delta undefined.");
        	int [] value = new int[xdim * ydim];
        	return value;
        }
        	
    }
    
    
    // These function use horizontal deltas.
    public static int[] getValuesFromDeltas1(int src[],int xdim, int ydim, int init_value)
    {
    	int[] dst = new int[xdim * ydim];
    	
        int k     = 0;
        int value = init_value;
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
    
    
    // These functions use vertical deltas.
    public static int[] getDeltasFromValues2(int src[], int xdim, int ydim, int init_value)
    {
        int[] dst = new int[xdim * ydim];
    	
        // Setting the first value to 1 mark the type of deltas vertical.
        int k     = 0;
        int value = init_value;
        int delta = 0;
        for(int i = 0; i < ydim; i++)
        {
            for(int j = 0; j < xdim; j++)
            {
            	if(i == 0)
            	{
            		if(j == 0)
            			dst[k++] = 1;
            		else
            		{
            		    delta     = src[k] - value;
                        value     += delta;
                        dst[k++]  = delta;
            		}
            	}
            	else
            	{
                    delta          = src[k]  - src[k - xdim];
                    dst[k++]       = delta;
            	}
            }
        }
        
        
        return dst;
    }

    public static int[] getValuesFromDeltas2(int src[],int xdim, int ydim, int init_value)
    {
    	int[] dst = new int[xdim * ydim];
    	
    	// We know the first value in the source data is 0.
    	// We'll use that to mark the type of deltas.
        dst[0] = init_value;
    	
        int value = init_value;
        
        for(int i = 1; i < xdim; i++)
        {
        	value   += src[i];
        	dst[i] = value;
        }
        
        
        // Now we can use values from the destination data. 
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
    
    public static int[] getDeltasFromValues(int src[])
    {
    	int   length = src.length;
        int[] dst = new int[length];
    	
        int value  = src[0];
        dst[0]     = value;
        int j      = 0;
        for(int i = 1; i < length; i++)
        {
            int delta     = src[i] - value;
            value        += delta;
            dst[i]      = delta;
        }
        return dst;
    }

    public static int[] getValuesFromDeltas(int src[])
    {
    	int length = src.length;
    	int[] dst = new int[length];
    	
        int value = src[0];
        dst[0]    = value;
        for(int i = 1; i < length; i++)
        {
            value  = dst[i - 1];
            value += src[i];
            dst[i] = value;
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
    
    public static byte checkStringType(byte src[], int size)
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
        //System.out.println("The number of iterations is " + number_of_iterations);
         
        return(string_type);
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
    
	public static int getBlockDeltaSum(int src[], int src_xdim, int src_ydim, int block_xdim, int block_ydim)
	{
		int xdim = src_xdim / block_xdim;
	    int ydim = src_ydim / block_ydim;
	    
	    int delta_sum = 0;
	    int yoffset   = 0;
	    for(int i = 0; i < ydim; i++)
	    {
	    	int xoffset = 0;
	    	for(int j = 0; j < xdim; j++)
	    	{
	    		int [] block      = DeltaMapper.extract(src, src_xdim, xoffset, yoffset, block_xdim, block_ydim);
	  		    int    init_value = block[0];
	  		    int [] delta      = DeltaMapper.getDeltasFromValues(block, block_xdim, block_ydim, init_value); 
	  		    if(delta[0] == 0)
	  		    {
	  		    	//System.out.println("Delta type is horizontal.");
	  		    	int h_sum =  DeltaMapper.getHorizontalDeltaSum(delta, block_xdim, block_ydim);
	  		    	delta_sum += h_sum;
	  		    }
	  		    else
	  		    {
	  		    	//System.out.println("Delta type is vertical.");
	  		    	int v_sum = DeltaMapper.getVerticalDeltaSum(delta, block_xdim, block_ydim);
	  		    	delta_sum += v_sum;
	  		    } 	
	  		    xoffset += block_xdim;
	    	}
	    	yoffset += block_ydim;
	    }
	    return delta_sum;
	}
	
}