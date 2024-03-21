import java.util.*;
import java.util.zip.*;
import java.lang.Math.*;
import java.math.*;

public class StringMapper
{
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
	
	public static ArrayList getHistogram(byte value[])
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
	    
	    int rank_lut[] = new int[histogram.length];
	    int k     = -1;
	    for(int i = histogram.length - 1; i >= 0; i--)
	    {
	    	double key = (double)key_list.get(i);
	    	int    j   = (int)rank_table.get(key);
	    	rank_lut[j]   = ++k;
	    }	
	    return rank_lut;
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
                    // then we already incremented the index and then reset the stop bit.
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
    
    public static int packStrings2(byte src[], int table[], byte dst[])
    {
    	int size             = src.length;
    	int number_of_values = table.length;
    	
    	int maximum_length = number_of_values - 1;
    	
    	//System.out.println("Maximum length is " + maximum_length);
    
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
            //System.out.println("Value is " + j);
            int k = table[j];
            //System.out.println("Lookup value is " + k);
            //System.out.println();
            
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
    
    public static int unpackStrings2(byte src[], int table[], byte dst[])
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
                dst[dst_byte++] = (byte)inverse_table[k]; 
               
                index[k]++;
                
                number_unpacked++;
                length = 1;
            }
            else if(length == maximum_length)
            {
            	int k = length;
            	dst[dst_byte++] = (byte)inverse_table[k];
            	
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
    
    
    
    public static int compressZeroBits(byte src[], int size, byte dst[]) 
	{
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
	
	// This function uses a metric to see if the data will expand or contract, and
    // copies the src to dst instead of processing it if it would expand.  
    // Worth noting that sometimes expanded strings compress better with
    // a prefix free code later (using deflate) than unexpanded strings since it's 
    // another example of recursive compression.
    public static int compressZeroStrings(byte src[], int length, byte dst[])
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
    			    while(amount < 0 && iterations < 15)
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
    		System.out.println("Exiting compressZeroStrings with an exception.");
    		System.out.println("The number of iterations was " + iterations);
    		System.out.println("String length was " + string_length);
    		System.out.println("Byte length was " + byte_length);
    		System.out.println("Input buffer length was " + input_length);
    		System.out.println("Output buffer length was " + output_length);
    	}
    	return current_length;
    }
    
    
    // This function expects to find the iterations in the final byte
    // as a positive number.
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
    		    dst[byte_length] = 0;
    		    
    		    byte extra_bits = (byte)(length % 8);
    		    if(extra_bits != 0)
    		    {
    		    	extra_bits = (byte)(8 - extra_bits);
    		    }
    		    extra_bits     <<= 5;
    		    
    		    dst[byte_length] |= extra_bits;
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
                    dst[last_byte] = 1;
                    
                    byte modulus = (byte)(current_length % 8);
                    
                    byte extra_bits = 0;
                    
                    if(modulus != 0)
                    	extra_bits = (byte)(8 - modulus);
                    
                    extra_bits <<= 5;
                    dst[last_byte] |= extra_bits;
                
                    return(current_length);
    		    }
    		    else
    		    {
    			    byte [] temp      = new byte[src.length];  
    			    while(amount < 0 && iterations < 15)
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
    			
    			    int last_byte = current_length / 8;
                    if(current_length % 8 != 0)
                	    last_byte++;
                	
                    dst[last_byte] = (byte)(iterations);
                    
                    byte modulus = (byte)(current_length % 8);
                    
                    byte extra_bits = 0;
                    
                    if(modulus != 0)
                    	extra_bits = (byte)(8 - modulus);
                    
                    extra_bits <<= 5;
                    dst[last_byte] |= extra_bits;
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
   
    // This function expects to find the number of iterations in the final
    // byte, as well as the number of odd bits determined by the bit length
    // subtracted from the byte length.
    public static int decompressZeroStrings2(byte src[], int length, byte dst[])
    {
        // Getting the number of iterations appended to
        // the end of the string. 
        int last_byte = length / 8;
        if(length % 8 != 0)
        	last_byte++;
        int iterations = src[last_byte] & (byte)31;
        byte extra_bits = (byte)(src[last_byte] >> 5);
        extra_bits &= 7;
        
        int modulus = length % 8;
        int inverse = modulus;
        if(inverse != 0)
        	inverse = 8 - inverse;
       
        if(extra_bits != inverse)
        {
        	System.out.println("Extra bits is " + extra_bits);
        	System.out.println("Inverse of modulus of bit length is " + inverse);
        	System.out.println();
        }
        
        int bit_length = (src.length - 1) * 8 - extra_bits;
        
        // If it's not a zero type string, we'll still process it.
        // This might actually compress a one type string.
        if(iterations > 16)
        {
        	System.out.println("Not zero type string.");
        	iterations -= 16;
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
    
    
    public static byte [] compressZeroStrings3(byte src[], int bit_length)
    {
    	int     current_bit_length = 0;
    	byte    iterations         = 0;
    	//boolean even_iterations    = true;
    	
    	int byte_length   = bit_length / 8;
    	if(bit_length % 8 != 0)
    		byte_length++;
    	byte [] buffer = new byte[byte_length];
    	
    	try
    	{
            int amount = getCompressionAmount(src, bit_length, 0);
    	    if(amount > 0)
    	    {
    	    	byte [] dst = new byte[byte_length + 1];
    		    System.arraycopy(src, 0, dst, 0, byte_length);
    		    
    		    // 0 iterations
    		    dst[byte_length] = iterations; 
    		    byte extra_bits = (byte)(bit_length % 8);
    		    if(extra_bits != 0)
    		    	extra_bits = (byte)(8 - extra_bits);
    		    extra_bits     <<= 5;
    		    dst[byte_length] |= extra_bits;
    	        return dst;
    	    }
            else
            {
            	iterations++;
    		    current_bit_length = compressZeroBits(src, bit_length, buffer);
    		    amount             = getCompressionAmount(buffer, current_bit_length, 0);
    		
    		    if(amount >= 0)
    		    {
    		    	byte_length = current_bit_length / 8;
    		    	if(current_bit_length % 8 != 0)
    		    		byte_length++;
    		    	byte [] dst = new byte[byte_length + 1];
        		    System.arraycopy(buffer, 0, dst, 0, byte_length);
    		    	
        		    // 1 iteration
        		    dst[byte_length] = iterations;
                    byte extra_bits = (byte)(current_bit_length % 8);
                    if(extra_bits != 0)
                    	extra_bits = (byte)(8 - extra_bits); 
                    extra_bits <<= 5;
                    dst[byte_length] |= extra_bits;
                    return(dst);
    		    }
    		    else
    		    {
    			    byte [] temp = new byte[src.length];  
    			    iterations++;
    			    while(amount < 0 && iterations < 15)
    			    {
    				   int previous_length = current_bit_length;
        	           if(iterations % 2 == 0)
        	           {
                           current_bit_length = compressZeroBits(buffer, previous_length, temp);
                           amount         = getCompressionAmount(temp, current_bit_length, 0);
        	            }
                        else
                        {
                            current_bit_length  = compressZeroBits(temp, previous_length, buffer);
                            amount         = getCompressionAmount(buffer, current_bit_length, 0);
                        }
        	            iterations++;
    			    }
    			
    			    byte_length = current_bit_length / 8;
                    if(current_bit_length % 8 != 0)
                        byte_length++;
    			    byte [] dst = new byte[byte_length + 1];
    			    
    			    if(iterations % 2 == 1)  
                        System.arraycopy(temp,  0,  dst,  0,  byte_length);
    			    else
    			    	System.arraycopy(buffer,  0,  dst,  0,  byte_length);
    			
                    dst[byte_length] = iterations; 
                    byte extra_bits = (byte)(current_bit_length % 8);
                    if(extra_bits != 0)
                    	extra_bits = (byte)(8 - extra_bits);
                    extra_bits <<= 5;
                    dst[byte_length] |= extra_bits;
                    return dst;
    		    }
    	    }
    	}
    	catch(Exception e)
    	{
    		System.out.println(e.toString());
    		System.out.println("Exiting compressZeroStrings3 with an exception.");
    		return src;
    	}
    }
   
    // This function expects to find the number of iterations in the final
    // byte, as well as the number of odd bits determined by the bit length
    // subtracted from the byte length.
    public static byte [] decompressZeroStrings3(byte src[])
    {
        // Getting the number of iterations appended to
        // the end of the string, along with the extra bits
    	// used to calculate the bit length. 
    	byte iterations = (byte)(src[src.length - 1] & 31);
    	
    	System.out.println("Number of iterations is " + iterations);
    	
        byte extra_bits = (byte)(src[src.length - 1] >> 5);
        extra_bits &= 7;
        int bit_length = (src.length - 1) * 8 - extra_bits;
        
        // If it's not a zero type string, we'll still process it.
        // This might actually compress a one type string.
        if(iterations > 16)
        {
        	System.out.println("Not zero type string.");
        	iterations -= 16;
        }
        
        // If it was not compressed, we copy src to dst.
        if(iterations == 0)
        {
        	byte [] dst = new byte[src.length];
        	System.arraycopy(src,  0,  dst, 0, src.length);
        	System.out.println("Processing uncompressed string.");
        	return dst;
        }
        
        int current_bit_length = bit_length;
        if(iterations == 1)
        {
        	byte [] buffer     = new byte[src.length * 2];
            current_bit_length = decompressZeroBits(src, bit_length, buffer);
            int byte_length    = current_bit_length / 8;
            if(current_bit_length % 8 != 0)
            	byte_length++;
            byte [] dst        = new byte[byte_length + 1];
            System.arraycopy(buffer,  0,  dst, 0, byte_length);
            
            extra_bits = (byte)(current_bit_length % 8);
            if(extra_bits != 0)
            	extra_bits = (byte)(8 - extra_bits);
            extra_bits <<= 5;
            
            dst[byte_length] = extra_bits;
            System.out.println("Processed one iteration.");
            return dst;
        }
        else
        {
        	int current_byte_length = current_bit_length % 8;
        	if(current_bit_length % 8 != 0)
        		current_byte_length++;
        	byte[] buffer1 = new byte[current_byte_length * 2];
        	byte[] buffer2 = new byte[current_byte_length * 2];
        	byte[] dst     = new byte[current_byte_length * 2];
        	
        	int previous_length = current_bit_length;
            current_bit_length  = decompressZeroBits(src, previous_length, buffer1);
            iterations--;
            
            while(iterations > 0)
            {
            	if(iterations % 2 == 1)
            	{
            		current_byte_length = current_bit_length % 8;
                	if(current_bit_length % 8 != 0)
                		current_byte_length++;
                	buffer2 = new byte[current_byte_length * 2];
                	previous_length = current_bit_length;
                	current_bit_length  = decompressZeroBits(buffer1, previous_length, buffer2);
                    iterations--;
                    if(iterations == 0)
                    {
                    	current_byte_length = current_bit_length % 8;
                    	if(current_bit_length % 8 != 0)
                    		current_byte_length++;
                    	dst = new byte[current_byte_length + 1];
                    	System.arraycopy(buffer2,  0,  dst, 0, current_byte_length);
                    	extra_bits = (byte)(current_bit_length % 8);
                        if(extra_bits != 0)
                        	extra_bits = (byte)(8 - extra_bits);
                        extra_bits <<= 5;
                        
                        dst[current_byte_length] = extra_bits;
                    }
            	}
            	else
            	{
            		current_byte_length = current_bit_length % 8;
                	if(current_bit_length % 8 != 0)
                		current_byte_length++;
                	buffer1 = new byte[current_byte_length * 2];
                	previous_length = current_bit_length;
                	current_bit_length  = decompressZeroBits(buffer2, previous_length, buffer1);
                    iterations--;
                    if(iterations == 0)
                    {
                    	current_byte_length = current_bit_length % 8;
                    	if(current_bit_length % 8 != 0)
                    		current_byte_length++;
                    	dst = new byte[current_byte_length + 1];
                    	System.arraycopy(buffer1,  0,  dst, 0, current_byte_length);
                    	extra_bits = (byte)(current_bit_length % 8);
                        if(extra_bits != 0)
                        	extra_bits = (byte)(8 - extra_bits);
                        extra_bits <<= 5;
                        
                        dst[current_byte_length] = extra_bits;
                    }	
            	}
            }
            
            
            return dst;
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
    			while(amount < 0 && iterations < 15)
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
    		System.out.println("Exiting compressOneStrings with exception.");
    	}
    	return current_length;
    }
   
    

    // This function expects to find the number of iterations in the final
    // byte in the form of a negative number,
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
    		    byte extra_bits = (byte)(length % 8);
                if(extra_bits != 0)
            	    extra_bits = (byte)(8 - extra_bits);
                extra_bits <<= 5;
                dst[byte_length] |= extra_bits;
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
                    dst[last_byte] = (byte) (iterations + 16);
                    byte extra_bits = (byte)(current_length % 8);
                    if(extra_bits != 0)
                	    extra_bits = (byte)(8 - extra_bits);
                    extra_bits <<= 5;
                    dst[last_byte] |= extra_bits;
                    return(current_length);
    		    }
    		    else
    		    {
    			    byte [] temp      = new byte[src.length];  
    			    while(amount < 0 && iterations < 15)
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
    			
    			    int last_byte = current_length / 8;
                    if(current_length % 8 != 0)
                	    last_byte++;
                
                    dst[last_byte] = (byte)(iterations + 16);
                
                    byte modulus = (byte)(current_length % 8);
                
                    byte extra_bits = 0;
                
                   if(modulus != 0)
                	   extra_bits = (byte)(8 - modulus);
                
                   extra_bits <<= 5;
                   dst[last_byte] |= extra_bits;
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
    
    // This function expects to find the number of iterations in the final
    // byte in the form of a negative number, as well as the number of odd bits 
    // determined by the bit length subtracted from the byte length.
    public static int decompressOneStrings2(byte src[], int length, byte dst[])
    {
        // Getting the number of iterations appended to
        // the end of the string.
        int last_byte = length / 8;
        if(length % 8 != 0)
            last_byte++;
        int iterations = (src[last_byte] & (byte)31);
        
        if(iterations < 16)
        {
        	System.out.println("Is not one type string.");	
        }
        else
        	iterations -= 16;
        
        byte extra_bits = src[last_byte];
        extra_bits >>= 5;
        extra_bits &= 7;
        
        int modulus = length % 8;
        int inverse = modulus;
        if(inverse != 0)
        	inverse = 8 - inverse;
        
        if(extra_bits != inverse)
        {
        	System.out.println("Extra bits is " + extra_bits);
        	System.out.println("Inverse of modulus of bit length is " + inverse);
        	System.out.println();
        }
        
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
   
    public static byte [] compressOneStrings3(byte src[], int bit_length)
    {
    	int     current_bit_length = 0;
    	byte    iterations         = 0;
    
    	int byte_length   = bit_length / 8;
    	if(bit_length % 8 != 0)
    		byte_length++;
    	byte [] buffer = new byte[byte_length];
    	
    	try
    	{
            int amount = getCompressionAmount(src, bit_length, 1);
    	    if(amount > 0)
    	    {
    	    	byte [] dst = new byte[byte_length + 1];
    		    System.arraycopy(src, 0, dst, 0, byte_length);
    		    
    		    // 0 iterations
    		    dst[byte_length] = iterations; 
    		    byte extra_bits = (byte)(bit_length % 8);
    		    if(extra_bits != 0)
    		    	extra_bits = (byte)(8 - extra_bits);
    		    extra_bits     <<= 5;
    		    dst[byte_length] |= extra_bits;
    	        return dst;
    	    }
            else
            {
            	iterations++;
    		    current_bit_length = compressOneBits(src, bit_length, buffer);
    		    amount             = getCompressionAmount(buffer, current_bit_length, 1);
    		
    		    if(amount >= 0)
    		    {
    		    	byte_length = current_bit_length / 8;
    		    	if(current_bit_length % 8 != 0)
    		    		byte_length++;
    		    	byte [] dst = new byte[byte_length + 1];
        		    System.arraycopy(buffer, 0, dst, 0, byte_length);
    		    	
        		    // 1 iteration-we add 15 to indicate a one string.
        		    dst[byte_length] = (byte)(iterations + 16);
                    byte extra_bits = (byte)(current_bit_length % 8);
                    if(extra_bits != 0)
                    	extra_bits = (byte)(8 - extra_bits); 
                    extra_bits <<= 5;
                    dst[byte_length] |= extra_bits;
                    return(dst);
    		    }
    		    else
    		    {
    			    byte [] temp = new byte[src.length];  
    			    while(amount < 0 && iterations < 15)
    			    {
    				   int previous_length = current_bit_length;
        	           if(iterations % 2 == 1)
        	           {
                           current_bit_length = compressOneBits(buffer, previous_length, temp);
                           iterations++; 
                           amount         = getCompressionAmount(temp, current_bit_length, 1);
        	            }
                        else
                        {
                            current_bit_length  = compressOneBits(temp, previous_length, buffer);
                            iterations++; 
                            amount         = getCompressionAmount(buffer, current_bit_length, 1);
                        }
    			    }
    			
    			    byte_length = current_bit_length / 8;
                    if(current_bit_length % 8 != 0)
                        byte_length++;
    			    byte [] dst = new byte[byte_length + 1];
    			    
    			    if(iterations % 2 == 0)  
                        System.arraycopy(temp,  0,  dst,  0,  byte_length);
    			    else
    			    	System.arraycopy(buffer,  0,  dst,  0,  byte_length);
    			
                    dst[byte_length] = (byte)(iterations + 16); 
                    byte extra_bits = (byte)(current_bit_length % 8);
                    if(extra_bits != 0)
                    	extra_bits = (byte)(8 - extra_bits);
                    extra_bits <<= 5;
                    dst[byte_length] |= extra_bits;
                    return dst;
    		    }
    	    }
    	}
    	catch(Exception e)
    	{
    		System.out.println(e.toString());
    		System.out.println("Exiting compressZeroStrings3 with an exception.");
    		return src;
    	}
    }
   
    // This function expects to find the number of iterations in the final
    // byte, as well as the number of odd bits determined by the bit length
    // subtracted from the byte length.
    public static byte [] decompressOneStrings3(byte src[])
    {
        // Getting the number of iterations appended to
        // the end of the string, along with the extra bits
    	// used to calculate the bit length. 
    	byte iterations = (byte)(src[src.length - 1] & 31);
    	
        byte extra_bits = (byte)(src[src.length - 1] >> 5);
        extra_bits &= 7;
        int bit_length = (src.length - 1) * 8 - extra_bits;
        
        if(iterations < 16)
        {
        	System.out.println("Not one type string.");
        }
        else
        	iterations -= 16;
        
        // If it was not compressed, we copy src to dst.
        if(iterations == 0)
        {
        	byte [] dst = new byte[src.length];
        	System.arraycopy(src,  0,  dst, 0, src.length);
        	return dst;
        }
        
        int current_bit_length = bit_length;
        if(iterations == 1)
        {
        	byte [] buffer     = new byte[src.length * 2];
            current_bit_length = decompressOneBits(src, bit_length, buffer);
            int byte_length    = current_bit_length / 8;
            if(current_bit_length % 8 != 0)
            	byte_length++;
            byte [] dst        = new byte[byte_length + 1];
            System.arraycopy(buffer,  0,  dst, 0, byte_length);
            
            extra_bits = (byte)(current_bit_length % 8);
            if(extra_bits != 0)
            	extra_bits = (byte)(8 - extra_bits);
            extra_bits <<= 5;
            
            dst[byte_length] = extra_bits;
            
            return dst;
        }
        else
        {
        	int current_byte_length = current_bit_length % 8;
        	if(current_bit_length % 8 != 0)
        		current_byte_length++;
        	byte[] buffer1 = new byte[current_byte_length * 2];
        	byte[] buffer2 = new byte[current_byte_length * 2];
        	byte[] dst     = new byte[current_byte_length * 2];
        	
        	int previous_length = current_bit_length;
            current_bit_length  = decompressOneBits(src, previous_length, buffer1);
            iterations--;
            
            while(iterations > 0)
            {
            	if(iterations % 2 != 0)
            	{
            		current_byte_length = current_bit_length % 8;
                	if(current_bit_length % 8 != 0)
                		current_byte_length++;
                	buffer2 = new byte[current_byte_length * 2];
                	previous_length = current_bit_length;
                	current_bit_length  = decompressOneBits(buffer1, previous_length, buffer2);
                    iterations--;
                    if(iterations == 0)
                    {
                    	current_byte_length = current_bit_length % 8;
                    	if(current_bit_length % 8 != 0)
                    		current_byte_length++;
                    	dst = new byte[current_byte_length + 1];
                    	System.arraycopy(buffer2,  0,  dst, 0, current_byte_length);
                    	extra_bits = (byte)(current_bit_length % 8);
                        if(extra_bits != 0)
                        	extra_bits = (byte)(8 - extra_bits);
                        extra_bits <<= 5;
                        
                        dst[current_byte_length] = extra_bits;
                    }
            	}
            	else
            	{
            		current_byte_length = current_bit_length % 8;
                	if(current_bit_length % 8 != 0)
                		current_byte_length++;
                	buffer1 = new byte[current_byte_length * 2];
                	previous_length = current_bit_length;
                	current_bit_length  = decompressOneBits(buffer2, previous_length, buffer1);
                    iterations--;
                    if(iterations == 0)
                    {
                    	current_byte_length = current_bit_length % 8;
                    	if(current_bit_length % 8 != 0)
                    		current_byte_length++;
                    	dst = new byte[current_byte_length + 1];
                    	System.arraycopy(buffer1,  0,  dst, 0, current_byte_length);
                    	extra_bits = (byte)(current_bit_length % 8);
                        if(extra_bits != 0)
                        	extra_bits = (byte)(8 - extra_bits);
                        extra_bits <<= 5;
                        
                        dst[current_byte_length] = extra_bits;
                    }	
            	}
            }
            return dst;
        }
    }

    public static int getBitlength(byte [] string)
    {
        byte last_byte  = string[string.length - 1];
        byte extra_bits = (byte)(last_byte >> 5);
        extra_bits     &= 7;
        int bitlength   = (string.length - 1) * 8 - extra_bits;
        
        return bitlength;
    }
    
    public static byte getIterations(byte [] string)
    {
        byte last_byte  = string[string.length - 1];
        byte iterations = (byte)(last_byte & 31);
        
        return iterations;
    }
    
    public static byte getType(byte [] string)
    {
        byte last_byte  = string[string.length - 1];
        byte iterations = (byte)(last_byte & 31);
        byte type = 0;
        if(iterations > 15)
            type = 1;
        
        return type;
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