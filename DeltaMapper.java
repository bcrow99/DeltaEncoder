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
	    histogram_list.add(value_range);
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

        // The first int is always 0.  Opportunity to use a code to switch delta types.
        // To maximize compression it should be eliminated or replaced with a single bit.
        if(src[0] != 0)
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
        
        try
        {
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
        }
        catch(Exception e)
        {
        	System.out.println(e.toString());
        	System.out.println("Exiting unpackStrings2 with an exception.");
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
    
	public static int compressZeroBits(byte src[], int size, byte dst[]) {
		for (int i = 0; i < dst.length; i++)
			dst[i] = 0;
		int current_byte = 0;
		int current_bit = 0;
		byte mask = 0x01;
		dst[0] = 0;

		int i = 0;
		int j = 0;
		int k = 0;

		try {
			for (i = 0; i < size; i++) {
				if ((src[k] & (mask << j)) == 0 && i < size - 1) {
					i++;
					j++;

					if (j == 8) {
						j = 0;
						k++;
					}
					if ((src[k] & (mask << j)) == 0) {
						// Current bit is a 0.
						current_bit++;
						if (current_bit == 8) {
							current_byte++;
							// current_bit = dst[current_byte] = 0;
							current_bit = 0;
						}
					} else {
						// Current bit is a 1.
						dst[current_byte] |= (byte) mask << current_bit;
						current_bit++;
						if (current_bit == 8) {
							current_byte++;
							current_bit = dst[current_byte] = 0;
						}
						dst[current_byte] |= (byte) mask << current_bit;
						current_bit++;
						if (current_bit == 8) {
							current_byte++;
							current_bit = 0;
						}
					}
				} 
				else if ((src[k] & (mask << j)) == 0 && (i == size - 1)) 
				{
					// We're at the end of the string and we have an odd 0.
					// Put a 1 down to signal that there is an odd 0.
					// This works for double iterations but can produce trailing 
					// zero bits in the other recursive cases.
					dst[current_byte] |= (byte) mask << current_bit;
					current_bit++;
					if (current_bit == 8) 
					{
						current_byte++;
						current_bit = 0;
					}
				} 
				else 
				{
					// Current first bit is a 1.
					dst[current_byte] |= (byte) mask << current_bit;
					current_bit++;
					if (current_bit == 8) 
					{
						current_byte++;
						current_bit = 0;
					}
					current_bit++;
					if (current_bit == 8) 
					{
						current_byte++;
						current_bit = 0;
					}
				}
				j++;
				if (j == 8) 
				{
					j = 0;
					k++;
				}
			}
		} 
		catch (Exception e) 
		{
			System.out.println(e.toString());
			System.out.println("Exiting compressZeroBits with an exception.");
		}
		
		int number_of_bits = current_byte * 8;
		number_of_bits += current_bit;

		return (number_of_bits);
	}

	public static int decompressZeroBits(byte src[], int size, byte dst[]) 
	{
		// This is necessary because dst can be used multiple times,
    	// so we can't count on it being initialized with zeros.
		for (int i = 0; i < dst.length; i++)
			dst[i] = 0;
		int current_byte = 0;
		int current_bit = 0;
		byte mask = 0x01;
		dst[0] = 0;

		int i = 0;
		int j = 0;
		int k = 0;
		
		try 
		{
			for (i = 0; i < size; i++) 
			{
				if ((src[k] & (mask << j)) != 0 && i < size - 1) 
				{
					i++;
					j++;
					if (j == 8) 
					{
						j = 0;
						k++;
					}
					if ((src[k] & (mask << j)) != 0) 
					{
						current_bit++;
						if (current_bit == 8) 
						{
							current_byte++;
							current_bit = 0;
						}
						dst[current_byte] |= (byte) mask << current_bit;
						current_bit++;
						if (current_bit == 8) {
							current_byte++;
							current_bit = 0;
						}
					} 
					else 
					{
						dst[current_byte] |= (byte) mask << current_bit;
						current_bit++;
						if (current_bit == 8) {
							current_byte++;
							current_bit = 0;
						}
					}
				} 
				else if (((src[k] & (mask << j)) != 0) && i == size - 1) 
				{
					// Append an odd 0.
					current_bit++;
					if (current_bit == 8) 
					{
						current_byte++;
						current_bit = 0;
					}
				} 
				else if ((src[k] & (mask << j)) == 0) 
				{
					current_bit++;
					if (current_bit == 8) 
					{
						current_byte++;
						if (current_byte < dst.length)
							current_bit = 0;
					}
					current_bit++;
					if (current_bit == 8) 
					{
						current_byte++;
						if (current_byte < dst.length)
							current_bit = 0;
					}
				}

				j++;
				if (j == 8) 
				{
					j = 0;
					k++;
				}
			}
		} 
		catch (Exception e) 
		{
			System.out.println(e.toString());
			System.out.println("Input size was " + size);
			System.out.println("Exception at index " + i);
			System.out.println("Exiting decompressZeroBits with an exception.");
		}

		int number_of_bits = current_byte * 8;
		number_of_bits += current_bit;
		return (number_of_bits);
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
    // Worth noting that sometimes expanded strings compress better with
    // a prefix free code later than unexpanded strings since it's 
    // another example of recursive compression.
    public static int compressZeroStrings2(byte src[], int length, byte dst[])
    {
    	int     current_length  = 0;
    	int     iterations      = 0;
    	boolean even_iterations = true;
    	
    	int string_length = length;
    	int byte_length   = string_length / 8;
    	if(string_length % 8 != 0)
    		byte_length++;
    	int input_length  = src.length;
    	int output_length = dst.length;
    	
    	try
    	{
            int amount = getCompressionAmount(src, length, 0);
    	    if(amount > 0)
    	    {
    		    // 0 iterations
    		    System.arraycopy(src, 0, dst, 0, byte_length);
    		    dst[byte_length] = (byte)iterations;
    	        return length;
    	    }
            else
            {
            	iterations = 1;
    		    current_length = compressZeroBits(src, length, dst);
    		    amount         = getCompressionAmount(dst, current_length, 0);
    		
    		    if(amount >= 0)
    		    {
    		    	// 1 iteration
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
        	        	   string_length = previous_length;
                           input_length  = dst.length;
                           output_length = temp.length;
        	        	
                           current_length = compressZeroBits(dst, previous_length, temp);
                           iterations++; 
                           even_iterations = true;
                           amount         = getCompressionAmount(temp, current_length, 0);
                        
        	            }
                        else
                        {
                        	string_length = previous_length;
                            input_length  = temp.length;
                            output_length = dst.length;
                        	
                            current_length  = compressZeroBits(temp, previous_length, dst);
                            iterations++; 
                        	even_iterations = false;
                            amount         = getCompressionAmount(dst, current_length, 0);
                        }
    			    }
    			
    			    if(iterations % 2 == 0) 
                    {
                	    // The last iteration used temp as a destination,
                	    // so we need to copy the data from temp to dst.
    				
                        byte_length = current_length / 8;
                        if(current_length % 8 != 0)
                            byte_length++; 
                        System.arraycopy(temp,  0,  dst,  0,  byte_length);
                    } 
                    // else the result is already in dst.
    			
    			    int last_byte = current_length / 8;
                    if(current_length % 8 != 0)
                	    last_byte++;
                    dst[last_byte] = (byte)iterations;
    		    }
    	    }
    	}
    	catch(Exception e)
    	{
    		System.out.println(e.toString());
    		System.out.println("Exiting compressZeroStrings2 with an exception.");
    		System.out.println("The number of iterations was " + iterations);
    		System.out.println("String length was " + string_length);
    		System.out.println("Byte length was " + byte_length);
    		System.out.println("Input buffer length was " + input_length);
    		System.out.println("Output buffer length was " + output_length);
    	}
    	return current_length;
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
        	
        	System.arraycopy(src,  0,  dst, 0, byte_length);
        	return length;
        }
        
        int current_length = 0;
        if(iterations == 1)
        {
            current_length = decompressZeroBits(src, length, dst);
            return current_length;
        }
        else if(iterations % 2 == 0)
        {
        	byte[]  temp = new byte[dst.length];
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
            return current_length;
        }
        else
        {
        	byte[]  temp = new byte[dst.length];
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
            return current_length;
        } 
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
    	// This is necessary because dst can be used multiple times,
    	// so we can't count on it being initialized with zeros.
    	for(int i = 0; i < dst.length; i++)
        	dst[i] = 0;
    	
        int  current_byte = 0;
        int  current_bit  = 0;
        byte mask         = 0x01;
        dst[0]            = 0;
        
        int i = 0;
        int j = 0;
        int k = 0;
      
        for(i = 0; i < size; i++)
        {
			try 
			{
				// First bit is a 0, get next bit.
				if (((src[k] & (mask << j)) == 0) && i < size - 1) 
				{
					i++;
					j++;
					if (j == 8) 
					{
						j = 0;
						k++;
					}
					if ((src[k] & (mask << j)) == 0) 
					{
						// Another zero bit. Leave a zero bit in the output.
						current_bit++;
						if (current_bit == 8) {
							current_byte++;
							current_bit = 0;
						}
					} 
					else 
					{
						// We have 01->10.
						dst[current_byte] |= (byte) mask << current_bit;
						current_bit++;
						if (current_bit == 8) 
						{
							current_byte++;
							current_bit = 0;
						}
						current_bit++;
						if (current_bit == 8) 
						{
							current_byte++;
							current_bit = 0;
						}
					}
				} 
				else if (((src[k] & (mask << j)) == 0) && i == size - 1) 
				{
					// Append an odd 1.
					dst[current_byte] |= (byte) mask << current_bit;
					current_bit++;
					if (current_bit == 8) 
					{
						current_byte++;
						current_bit = 0;
					}
				} 
				else if ((src[k] & (mask << j)) != 0) 
				{
					// We have a 1 bit, put down two 1 bits in the output.
					dst[current_byte] |= (byte) mask << current_bit;
					current_bit++;
					if (current_bit == 8) 
					{
						current_byte++;
						if (current_byte < dst.length)
							current_bit = 0;
					}
					dst[current_byte] |= (byte) mask << current_bit;
					current_bit++;
					if (current_bit == 8) 
					{
						current_byte++;
						if (current_byte < dst.length)
							current_bit = 0;
					}
				}
				j++;
				if (j == 8) 
				{
					j = 0;
					k++;
				}
			}
			catch (Exception e) 
			{
				System.out.println(e.toString());
				System.out.println("Size of input was " + size);
				System.out.println("Exception on index " + i);
				System.out.println("Exiting decompressOneBits.");
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
    // original encoder/decoder programs.
    public static int compressOneStrings2(byte src[], int length, byte dst[])
    {
    	int current_length = 0;
    
    	boolean even_iterations = true;
    	
    	try
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
    		current_length = compressOneBits(src, length, dst);
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
                
    		}
    	}
    	}
    	catch(Exception e)
    	{
    		System.out.println(e.toString());
    		System.out.println("Exiting compressOneStrings2 with exception.");
    	}
    	return current_length;
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
        	System.arraycopy(src, 0, dst, 0, byte_length);
        	return length;
        }
        
        
        
        int current_length = 0;
        if(iterations == 1)
        {
            current_length = decompressOneBits(src, length, dst);
            return current_length;
        }
        else if(iterations % 2 == 0)
        {
        	byte[]  temp = new byte[dst.length];
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
            return current_length;
        }
        else
        {
        	byte[]  temp = new byte[dst.length];
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
            return current_length;
        }
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
}