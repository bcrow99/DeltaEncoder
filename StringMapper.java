import java.util.ArrayList;
import java.util.zip.*;
import java.util.*;
import java.lang.Math.*;
import java.math.*;

/**
* This is a class with methods to analyze bit strings.
* 
* @author Brian Crowley
* @version 1.0
*/

public class StringMapper
{
	/**
	* Creates a histogram from a byte array and returns
	* a list including the histogram and some other key
	* information.  The list is not parameterized so 
	* that random types can be added.
	*
	* @param src the input byte array
	* @return unparameterized list with histogram and additonal information.
	*/
	public static ArrayList getHistogram(byte src[])
	{  
	    int min = src[0];
	    int max = src[0];
	    for(int i = 0; i < src.length; i++)
	    {
	    	if(src[i] > max)
	    		max = src[i];
	    	if(src[i] < min)
	    		min = src[i];
	    }
	    int range = max - min + 1;
	    int [] histogram = new int[range];
	    for(int i = 0; i < range; i++)
	    	histogram[i] = 0;
	    for(int i = 0; i < src.length; i++)
	    {
	    	int j = src[i] - min;
	    	histogram[j]++;
	    }
	    
	    ArrayList histogram_list = new ArrayList();
	    histogram_list.add(min);
	    histogram_list.add(histogram);
	    histogram_list.add(range);
	    return histogram_list;
	}
	
	/**
	* Creates a histogram from an int array and returns
	* a list including the histogram and some other key
	* information.  The list is not parameterized so 
	* that random types can be added.
	*
	* @param src the input int array
	* @return unparameterized list with histogram and additonal information.
	*/
	public static ArrayList getHistogram(int src[])
	{  
	    int min = src[0];
	    int max = src[0];
	    for(int i = 0; i < src.length; i++)
	    {
	    	if(src[i] > max)
	    		max = src[i];
	    	if(src[i] < min)
	    		min = src[i];
	    }
	    int range = max - min + 1;
	    int [] histogram = new int[range];
	    for(int i = 0; i < range; i++)
	    	histogram[i] = 0;
	    for(int i = 0; i < src.length; i++)
	    {
	    	int j = src[i] - min;
	    	histogram[j]++;
	    }
	    ArrayList histogram_list = new ArrayList();
	    histogram_list.add(min);
	    histogram_list.add(histogram);
	    histogram_list.add(range);
	    return histogram_list;
	}
	
	
	/**
	* Creates a rank table of most popular to least
	* popular from a histogram.
	* 
	* @param src the input histogram
	* @return unparameterized list with histogram and additonal information.
	*/
	public static int[] getRankTable(int src[])
	{
		ArrayList list     = new ArrayList();
	    Hashtable table   = new Hashtable();
	    for(int i = 0; i < src.length; i++)
	    {
	    	double key = src[i];
	    	while(table.containsKey(key))
	    		key +=.001;
	    	table.put(key, i);
	    	list.add(key);
	    }
	    Collections.sort(list);
	    
	    int rank[] = new int[src.length];
	    int k     = -1;
	    for(int i = src.length - 1; i >= 0; i--)
	    {
	    	double key = (double)list.get(i);
	    	int    j   = (int)table.get(key);
	    	rank[j]   = ++k;
	    }	
	    return rank;
	}
	
	
	
    // This set of functions makes no assumptions about the 
    // the maxiumum length of an individual string.
    
    /**
	* Uses a rank table of most popular to least popular
	* to turn integer representations into unary strings,
	* and then pack pack them into a byte array.  
	* It assumes dst is large enough to contain the result,
	* and returns the length of the bit string.
	* 
	* @param src the input integers
	* @param table the rank table
	* @return byte array containing the bit string
	*/
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
   
	/**
	* Uses a rank table of most popular to least popular
	* to turn unary strings into integers.
	* 
	* @param src the input integers
	* @param table the rank table
	* @param dst the integer array containing the result
	* @return int the length of the dst.array
	*/
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
	
	/**
	* Uses a rank table of most popular to least popular
	* to turn byte representations into unary strings,
	* and then pack pack them into a byte array.  
	* It assumes dst is large enough to contain the result,
	* and returns the length of the bit string.
	* 
	* @param src the input bytes
	* @param table the rank table
	* @return byte array containing the bit string
	*/
	public static int packStrings2(byte src[], int table[], byte dst[])
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
    
	
	/**
	* Uses a rank table of most popular to least popular
	* to turn integer representations into unary strings,
	* and then pack pack them into a byte array.  
	* It assumes dst is large enough to contain the result,
	* and returns the length of the bit string.
	* 
	* @param src the input integers
	* @param table the rank table
	* @return byte array containing the bit string
	*/
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
    
    /**
   	* Uses a rank table of most popular to least popular
   	* to turn unary strings into bytes.
   	* 
   	* @param src the input bytes
   	* @param table the rank table
   	* @param dst the byte array containing the result
   	* @return int the length of the dst.array
   	*/
    public static int unpackStrings2(byte src[], int table[], byte dst[])
    {
        int size             = dst.length;
        int number_of_values = table.length;
        int maximum_length   = number_of_values - 1;
        int length           = 1;
        int src_byte         = 0;
        int dst_byte         = 0;
        int number_unpacked  = 0;
        int mask             = 1;
        int bit              = 0;
       
        byte [] inverse_table = new byte[number_of_values];
        for(int i = 0; i < number_of_values; i++)
        {
            int j            = table[i];
            inverse_table[j] = (byte)i;
        }
        
        try
        {
        	while(dst_byte < size)
            {
                int non_zero = src[src_byte] & (mask << bit);
                if(non_zero != 0 && length < maximum_length)
                    length++;
                else if(non_zero == 0)
                {
                    int k           = length - 1;
                    dst[dst_byte++] = inverse_table[k]; 
                    length          = 1;
                    number_unpacked++;
                    
                }
                else if(length == maximum_length)
                {
                	int k = length;
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
        }
        catch(Exception e)
        {
        	System.out.println(e.toString());
        	System.out.println("Exiting unpackStrings2 with an exception.");
        }
        return(number_unpacked);
    }    
    
    /**
	* Uses a rank table of most popular to least popular
	* to turn unary strings into integers.
	* 
	* @param src the input bytes
	* @param table the rank table
	* @param dst the integer array containing the result
	* @return int the length of the dst.array
	*/
    public static int unpackStrings2(byte src[], int table[], int dst[])
    {
        int size             = dst.length;
        int number_of_values = table.length;
        int maximum_length   = number_of_values - 1;
        int length           = 1;
        int src_byte         = 0;
        int dst_byte         = 0;
        int number_unpacked  = 0;
        int mask             = 1;
        int bit              = 0;
       
        int [] inverse_table = new int[number_of_values];
        for(int i = 0; i < number_of_values; i++)
        {
            int j            = table[i];
            inverse_table[j] = i;
        }
        
        try
        {
        	while(dst_byte < size)
            {
                int non_zero = src[src_byte] & (mask << bit);
                if(non_zero != 0 && length < maximum_length)
                    length++;
                else if(non_zero == 0)
                {
                    int k           = length - 1;
                    dst[dst_byte++] = inverse_table[k]; 
                    length          = 1;
                    number_unpacked++;
                    
                }
                else if(length == maximum_length)
                {
                	int k = length;
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
        }
        catch(Exception e)
        {
        	System.out.println(e.toString());
        	System.out.println("Exiting unpackStrings2 with an exception.");
        }
        return(number_unpacked);
    }

    
    // Stop bit methods.  
    // Zero bits correspond to stop bits in our implementation.
    /**
   	* Does a bitwise substitution that will expand or contract
   	* the original bit string. 
   	*  
   	* @param src     input bytes
   	* @param size    original bit length
   	* @param dst     byte array containing the result
   	* @return        transformed bit length
   	*/
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

		try 
		{
			for (i = 0; i < size; i++) 
			{
				if ((src[k] & (mask << j)) == 0 && i < size - 1) 
				{
					i++;
					j++;

					if (j == 8) 
					{
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
					} 
					else 
					{
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
    
    /**
   	* Does the inverse bitwise substitution that will expand or contract
   	* the original bit string. 
   	*  
   	* @param src     input bytes
   	* @param size    original bit length
   	* @param dst     byte array containing the result
   	* @return        transformed bit length
   	*/
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
	
	/**
   	* This applies a bitwise substitution iteratively that will
   	* expand or contract a bit string.
   	*  
   	* @param src     input bytes
   	* @param size    original bit length
   	* @param dst     byte array containing the result
   	* @return        transformed bit length
   	*/
    public static int compressZeroStrings(byte src[], int bit_length, byte dst[])
    {
    	int     current_length  = 0;
    	int     iterations      = 0;
    	
    	int byte_length   = bit_length / 8;
    	if(bit_length % 8 != 0)
    		byte_length++;
    	
    	try
    	{
            int amount = getCompressionAmount(src, bit_length, 0);
    	    if(amount > 0)
    	    {
    		    // 0 iterations
    		    System.arraycopy(src, 0, dst, 0, byte_length);
    		    dst[byte_length] = 0;
    		    
    		    byte extra_bits = (byte)(bit_length % 8);
    		    if(extra_bits != 0)
    		    	extra_bits = (byte)(8 - extra_bits);
    		    extra_bits     <<= 5;
    		    
    		    dst[byte_length] |= extra_bits;
    	        return bit_length;
    	    }
            else
            {
            	iterations = 1;
    		    current_length = compressZeroBits(src, bit_length, dst);
    		    amount         = getCompressionAmount(dst, current_length, 0);
    		
    		    if(amount >= 0)
    		    {
    		    	// 1 iteration
    			    int last_byte = current_length / 8;
                    if(current_length % 8 != 0)
                	    last_byte++;
                    dst[last_byte] = 1;
                    
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
                           current_length = compressZeroBits(dst, previous_length, temp);
                           iterations++;
                           amount         = getCompressionAmount(temp, current_length, 0);
                        
        	            }
                        else
                        {
                            current_length  = compressZeroBits(temp, previous_length, dst);
                            iterations++; 
                            amount         = getCompressionAmount(dst, current_length, 0);
                        }
    			    }
    			
    			    if(iterations % 2 == 0) 
                    {
                        byte_length = current_length / 8;
                        if(current_length % 8 != 0)
                            byte_length++; 
                        System.arraycopy(temp,  0,  dst,  0,  byte_length);
                    } 
    			
    			    int last_byte = current_length / 8;
                    if(current_length % 8 != 0)
                	    last_byte++;
                	
                    dst[last_byte] = (byte)(iterations);
                    
                    byte extra_bits = (byte)(current_length % 8);
                    if(extra_bits != 0)
                    	extra_bits = (byte)(8 - extra_bits);
                    extra_bits <<= 5;
                    
                    dst[last_byte] |= extra_bits;
    		    }
    	    }
    	}
    	catch(Exception e)
    	{
    		System.out.println(e.toString());
    		System.out.println("Exiting compressZeroStrings2 with an exception.");
    		System.out.println();
    	}
    	return current_length;
    }
   
    /**
   	* This applies a bitwise substitution iteratively that will
   	* expand or contract a bit string.
   	*  
   	* @param src     input bytes
   	* @param size    original bit length
   	* @return        byte array containing the result
   	*/
    public static byte[] compressZeroStrings(byte[] src, int bit_length)
    {
    	try
    	{
            int amount = getCompressionAmount(src, bit_length, 0);
    	    if(amount > 0)
    	    {
    	    	int byte_length   = bit_length / 8;
    	    	if(bit_length % 8 != 0)
    	    		byte_length++;
    	    	byte[] dst = new byte[byte_length + 1];
    		    System.arraycopy(src, 0, dst, 0, byte_length);
    		    
    		    byte extra_bits = (byte)(bit_length % 8);
    		    if(extra_bits != 0)
    		    	extra_bits = (byte)(8 - extra_bits);
    		    extra_bits     <<= 5;
    		    
    		    dst[byte_length] = extra_bits;
    	        return dst;
    	    }
            else
            {
            	byte[] buffer1 = new byte[src.length];
            	byte[] buffer2 = new byte[src.length];
    		    int compressed_length = compressZeroBits(src, bit_length, buffer1);
    		    amount = getCompressionAmount(buffer1, compressed_length, 0);
    		    if(amount >= 0)
    		    {
    		    	// 1 iteration
    			    int byte_length = compressed_length / 8;
                    if(compressed_length % 8 != 0)
                	    byte_length++;
                    byte extra_bits = (byte)(compressed_length % 8);
                    if(extra_bits != 0)
                    	extra_bits = (byte)(8 - extra_bits);
                    extra_bits <<= 5;
                    
                    byte[] dst  = new byte[byte_length + 1];
                    System.arraycopy(buffer1,  0,  dst,  0,  byte_length);
                    dst[byte_length]  = 1;
                    dst[byte_length] |= extra_bits;
                    return dst;
    		    }
    		    else
    		    {
    		    	int iterations = 1;
    			    while(amount < 0 && iterations < 15)
    			    {
    				   int previous_length = compressed_length;
        	           if(iterations % 2 == 1)
        	           {
                           compressed_length = compressZeroBits(buffer1, previous_length, buffer2);
                           iterations++; 
                           amount            = getCompressionAmount(buffer2, compressed_length, 0);
        	            }
                        else
                        {
                            compressed_length = compressZeroBits(buffer2, previous_length, buffer1);
                            iterations++; 
                            amount            = getCompressionAmount(buffer1, compressed_length, 0);
                        }
    			    }
    			
    			    int byte_length = compressed_length / 8;
    			    if(compressed_length % 8 != 0)
    			    	byte_length++;
    			    byte[] dst = new byte[byte_length + 1];
    			    if(iterations % 2 == 0) 
    			    {
                        System.arraycopy(buffer2,  0,  dst,  0,  byte_length);
    			    }
    			    else
    			    {
    			    	System.arraycopy(buffer1,  0,  dst,  0,  byte_length);
    			    }
    			    dst[byte_length] = (byte)iterations; 
                    byte extra_bits = (byte)(compressed_length % 8);
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
    		System.out.println("Exiting compressZeroStrings with an exception.");
    	}
    	
    	// Keep the compiler happy.
    	byte [] dst = new byte[1];
    	return dst;
    }
  
    
    // This function expects a trailing byte with information.
    public static byte[] compressZeroStrings(byte[] src)
    {
    	byte [] dst = new byte[1];
    	try
    	{
    		int bit_length = getBitlength(src);
            int amount = getCompressionAmount(src, bit_length, 0);
    	    if(amount > 0)
    	    {
    	    	dst = src;
    	        return dst;
    	    }
            else
            {
            	byte[] buffer1 = new byte[src.length];
            	byte[] buffer2 = new byte[src.length];
    		    int compressed_length = compressZeroBits(src, bit_length, buffer1);
    		    amount = getCompressionAmount(buffer1, compressed_length, 0);
    		    if(amount >= 0)
    		    {
    		    	// 1 iteration
    			    int byte_length = compressed_length / 8;
                    if(compressed_length % 8 != 0)
                	    byte_length++;
                    byte extra_bits = (byte)(compressed_length % 8);
                    if(extra_bits != 0)
                    	extra_bits = (byte)(8 - extra_bits);
                    extra_bits <<= 5;
                    
                    dst  = new byte[byte_length + 1];
                    System.arraycopy(buffer1,  0,  dst,  0,  byte_length);
                    dst[byte_length]  = 1;
                    dst[byte_length] |= extra_bits;
                    return dst;
    		    }
    		    else
    		    {
    		    	int iterations = 1;
    			    while(amount < 0 && iterations < 15)
    			    {
    				   int previous_length = compressed_length;
        	           if(iterations % 2 == 1)
        	           {
                           compressed_length = compressZeroBits(buffer1, previous_length, buffer2);
                           iterations++; 
                           amount            = getCompressionAmount(buffer2, compressed_length, 0);
        	            }
                        else
                        {
                            compressed_length = compressZeroBits(buffer2, previous_length, buffer1);
                            iterations++; 
                            amount            = getCompressionAmount(buffer1, compressed_length, 0);
                        }
    			    }
    			
    			    int byte_length = compressed_length / 8;
    			    if(compressed_length % 8 != 0)
    			    	byte_length++;
    			    dst = new byte[byte_length + 1];
    			    if(iterations % 2 == 0) 
    			    {
                        System.arraycopy(buffer2,  0,  dst,  0,  byte_length);
    			    }
    			    else
    			    {
    			    	System.arraycopy(buffer1,  0,  dst,  0,  byte_length);
    			    }
    			    dst[byte_length] = (byte)iterations; 
                    byte extra_bits = (byte)(compressed_length % 8);
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
    		System.out.println("Exiting compressZeroStrings with an exception.");
    		return dst;
    	}
    }
    
    
    // This function expects to find the number of iterations in the final
    // byte, as well as the number of odd bits determined by the bit length
    // subtracted from the byte length.
    public static byte[] decompressZeroStrings(byte[] src)
    {
        int last_byte   = src.length - 1;
        int iterations  = src[last_byte] & (byte)31;
        byte extra_bits = (byte)(src[last_byte] >> 5);
        extra_bits     &= 7;
        int bit_length  = (src.length - 1) * 8 - extra_bits; 
     
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
        	
        	return dst;
        }
        
        if(iterations == 1)
        {
        	int byte_length = bit_length / 8;
        	if(bit_length % 8 != 0)
        		byte_length++;
        	byte_length *= 2;
        	byte[] buffer = new byte[byte_length];
            int uncompressed_length = decompressZeroBits(src, bit_length, buffer);
            byte_length = uncompressed_length / 8;
            if(uncompressed_length % 8 != 0)
            	byte_length++;
            byte [] dst = new byte[byte_length + 1];
            System.arraycopy(buffer,  0,  dst, 0, byte_length);
            last_byte = dst.length - 1;
            extra_bits = (byte)(uncompressed_length % 8);
            if(extra_bits != 0)
            	extra_bits = (byte)(8 - extra_bits);
            extra_bits <<= 5;
            dst[last_byte] = extra_bits;
     
            return dst;
        }
        else if(iterations % 2 == 0)
        {
        	int byte_length = bit_length / 8;
        	if(bit_length % 8 != 0)
        		byte_length++;
        	double factor = Math.pow(2, iterations);
        	byte_length  *= factor;
        	
        	byte[] buffer1 = new byte[byte_length];
        	byte[] buffer2 = new byte[byte_length];
        	
            int uncompressed_length = decompressZeroBits(src, bit_length, buffer1);
            iterations--;
            while(iterations > 0)
            {
                int previous_length = uncompressed_length;
                if(iterations % 2 == 1)
                {
                    uncompressed_length = decompressZeroBits(buffer1, previous_length, buffer2);
                }
                else
                {
                    uncompressed_length = decompressZeroBits(buffer2, previous_length, buffer1);
                }
                iterations--;
            }
            byte_length = uncompressed_length / 8;
        	if(uncompressed_length % 8 != 0)
        		byte_length++;
        	
        	byte [] dst = new byte[byte_length + 1];
        	System.arraycopy(buffer2,  0,  dst, 0, byte_length);
            last_byte = dst.length - 1;
            extra_bits = (byte)(uncompressed_length % 8);
            if(extra_bits != 0)
            	extra_bits = (byte)(8 - extra_bits);
            extra_bits <<= 5;
            dst[last_byte] = extra_bits;
            return dst;
        }
        else
        {
        	int byte_length = bit_length / 8;
        	if(bit_length % 8 != 0)
        		byte_length++;
        	double factor = Math.pow(2, iterations);
        	byte_length  *= factor;
        	
        	byte[] buffer1 = new byte[byte_length];
        	byte[] buffer2 = new byte[byte_length];
        	
            int uncompressed_length = decompressZeroBits(src, bit_length, buffer1);
            iterations--;
            while(iterations > 0)
            {
                int previous_length = uncompressed_length;
                if(iterations % 2 == 0)
                {
                    uncompressed_length = decompressZeroBits(buffer1, previous_length, buffer2);
                }
                else
                {
                    uncompressed_length = decompressZeroBits(buffer2, previous_length, buffer1);
                }
                iterations--;
            }
            byte_length = uncompressed_length / 8;
        	if(uncompressed_length % 8 != 0)
        		byte_length++;
        	
        	byte [] dst = new byte[byte_length + 1];
        
        	System.arraycopy(buffer1,  0,  dst, 0, byte_length);
            last_byte = dst.length - 1;
            extra_bits = (byte)(uncompressed_length % 8);
            if(extra_bits != 0)
            	extra_bits = (byte)(8 - extra_bits);
            extra_bits <<= 5;
            dst[last_byte] = extra_bits;
            
            return dst;
        } 
    }
    
    // This function expects to find the number of iterations in the final
    // byte, as well as the number of odd bits determined by the bit length
    // subtracted from the byte length.
    public static int decompressZeroStrings(byte src[], int bit_length, byte dst[])
    { 
        int last_byte = bit_length / 8;
        if(bit_length % 8 != 0)
        	last_byte++;
        int iterations = src[last_byte] & (byte)31;
        byte extra_bits = (byte)(src[last_byte] >> 5);
        extra_bits &= 7;
        
        int bit_length2 = (src.length - 1) * 8 - extra_bits;
        
        if(bit_length != bit_length2)
        	System.out.println("Bit length in arguments does not agree with trailing byte.");
        
        if(iterations > 16)
        {
        	System.out.println("Not zero type string.");
        	iterations -= 16;
        }
        
        if(iterations == 0)
        {
        	int byte_length = bit_length / 8;
        	if(bit_length % 8 != 0)
        		byte_length++;
        	
        	System.arraycopy(src,  0,  dst, 0, byte_length);
        	return bit_length;
        }
        
        int current_length = 0;
        if(iterations == 1)
        {
            current_length = decompressZeroBits(src, bit_length, dst);
            return current_length;
        }
        else if(iterations % 2 == 0)
        {
        	byte[]  temp = new byte[dst.length];
            current_length = decompressZeroBits(src, bit_length, temp);
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
            current_length = decompressZeroBits(src, bit_length, dst);
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
   
    // One bit/one string functions.
    // One bits correspond to run bits in this implementation.
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
                            amount         = getCompressionAmount(temp, current_length, 1);
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
    		System.out.println("Exiting compressOneStrings with exception.");
    	}
    	return current_length;
    }
    
    public static byte[] compressOneStrings(byte[] src, int bit_length)
    {
    	byte[] dst = new byte[1];
    	try
    	{
            int amount = getCompressionAmount(src, bit_length, 1);
    	    if(amount > 0)
    	    {
    		    // 0 iteration
    	    	int byte_length   = bit_length / 8;
    	    	if(bit_length % 8 != 0)
    	    		byte_length++;
    	    	dst = new byte[byte_length + 1];
    	    	
    		    System.arraycopy(src, 0, dst, 0, byte_length);
    		    
    		    
    		    byte extra_bits = (byte)(bit_length % 8);
    		    if(extra_bits != 0)
    		    	extra_bits = (byte)(8 - extra_bits);
    		    extra_bits     <<= 5;
    		    
    		    // Need to set iterations to 16 to allow getting the type.
    		    // Might possibly break the segmentation in the adaptive writer/reader.
    		    dst[byte_length]  = 16;
                
    		    dst[byte_length] |= extra_bits;
    		  
    	        return dst;
    	    }
            else
            {
            	byte[] buffer1        = new byte[src.length];
            	byte[] buffer2        = new byte[src.length];
    		    int compressed_length = compressOneBits(src, bit_length, buffer1);
    		    amount                = getCompressionAmount(buffer1, compressed_length, 1);
    		    if(amount >= 0)
    		    {
    		    	// 1 iteration
    			    int byte_length = compressed_length / 8;
                    if(compressed_length % 8 != 0)
                	    byte_length++;
                    byte extra_bits = (byte)(compressed_length % 8);
                    if(extra_bits != 0)
                    	extra_bits = (byte)(8 - extra_bits);
                    extra_bits <<= 5;
                    
                    dst  = new byte[byte_length + 1];
                    System.arraycopy(buffer1,  0,  dst,  0,  byte_length);
                    
                    // Add 16 to indicate string type.
                    dst[byte_length]  = 1 + 16;
                    dst[byte_length] |= extra_bits;
                   
                    return dst;
    		    }
    		    else
    		    {
    		    	int iterations = 1;
    			    while(amount < 0 && iterations < 15)
    			    {
    				   int previous_length = compressed_length;
        	           if(iterations % 2 == 1)
        	           {
        	        	 
                           compressed_length = compressOneBits(buffer1, previous_length, buffer2);
                           iterations++; 
                           amount         = getCompressionAmount(buffer2, compressed_length, 1);
        	            }
                        else
                        {
                        	
                            compressed_length  = compressOneBits(buffer2, previous_length, buffer1);
                            iterations++; 
                            amount         = getCompressionAmount(buffer1, compressed_length, 1);
                        }
    			    }
    			
    			    int byte_length = compressed_length / 8;
    			    if(compressed_length % 8 != 0)
    			    	byte_length++;
    			    dst = new byte[byte_length + 1];
    			    if(iterations % 2 == 0) 
                        System.arraycopy(buffer2,  0,  dst,  0,  byte_length);
    			    else
    			    	System.arraycopy(buffer1,  0,  dst,  0,  byte_length);
    			    
    			    dst[byte_length] = (byte)(iterations + 16); 
                    byte extra_bits = (byte)(compressed_length % 8);
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
    		System.out.println("Exiting compressOneStrings with an exception.");
    		return dst;
    	}
    }
    
    // This function expects a trailing bit with information.
    public static byte[] compressOneStrings(byte[] src)
    {
    	byte [] dst = new byte[1];
    	try
    	{
    		int bit_length = getBitlength(src);
            int amount = getCompressionAmount(src, bit_length, 1);
    	    if(amount >= 0)
    	    {
    		    // 0 iteration
    	    	int byte_length   = bit_length / 8;
    	    	if(bit_length % 8 != 0)
    	    		byte_length++;
    	    	dst = new byte[byte_length + 1];
    	    	
    		    System.arraycopy(src, 0, dst, 0, byte_length);
    		    
    		    
    		    byte extra_bits = (byte)(bit_length % 8);
    		    if(extra_bits != 0)
    		    	extra_bits = (byte)(8 - extra_bits);
    		    extra_bits     <<= 5;
    		    
    		    
    		    dst[byte_length]  = 16;
                
    		    dst[byte_length] |= extra_bits;
    		  
    	        return dst;
    	    }
            else
            {
            	byte[] buffer1        = new byte[src.length];
            	byte[] buffer2        = new byte[src.length];
    		    int compressed_length = compressOneBits(src, bit_length, buffer1);
    		    amount                = getCompressionAmount(buffer1, compressed_length, 1);
    		    if(amount >= 0)
    		    {
    		    	// 1 iteration
    			    int byte_length = compressed_length / 8;
                    if(compressed_length % 8 != 0)
                	    byte_length++;
                    byte extra_bits = (byte)(compressed_length % 8);
                    if(extra_bits != 0)
                    	extra_bits = (byte)(8 - extra_bits);
                    extra_bits <<= 5;
                    
                    dst  = new byte[byte_length + 1];
                    System.arraycopy(buffer1,  0,  dst,  0,  byte_length);
                    
                    // Add 16 to indicate string type.
                    dst[byte_length]  = 1 + 16;
                    dst[byte_length] |= extra_bits;
                   
                    return dst;
    		    }
    		    else
    		    {
    		    	int iterations = 1;
    			    while(amount < 0 && iterations < 15)
    			    {
    				   int previous_length = compressed_length;
        	           if(iterations % 2 == 1)
        	           {
        	        	 
                           compressed_length = compressOneBits(buffer1, previous_length, buffer2);
                           iterations++; 
                           amount         = getCompressionAmount(buffer2, compressed_length, 1);
        	            }
                        else
                        {
                        	
                            compressed_length  = compressOneBits(buffer2, previous_length, buffer1);
                            iterations++; 
                            amount         = getCompressionAmount(buffer1, compressed_length, 1);
                        }
    			    }
    			
    			    int byte_length = compressed_length / 8;
    			    if(compressed_length % 8 != 0)
    			    	byte_length++;
    			    dst = new byte[byte_length + 1];
    			    if(iterations % 2 == 0) 
                        System.arraycopy(buffer2,  0,  dst,  0,  byte_length);
    			    else
    			    	System.arraycopy(buffer1,  0,  dst,  0,  byte_length);
    			    
    			    dst[byte_length] = (byte)(iterations + 16); 
                    byte extra_bits = (byte)(compressed_length % 8);
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
    		System.out.println("Exiting compressOneStrings with an exception.");
    		return dst;
    	}
    }
    
    // This function expects to find the number of iterations in the final
    // byte, as well as the number of odd bits determined by the bit length
    // subtracted from the byte length.
    public static int decompressOneStrings(byte src[], int length, byte dst[])
    {
        // Getting the number of iterations appended to
        // the end of the string.
        int last_byte = length / 8;
        if(length % 8 != 0)
            last_byte++;
        int iterations = (src[last_byte] & (byte)31);
        
        if(iterations < 16)
        	System.out.println("Is not one type string.");	
        else
        	iterations -= 16;
        
        byte extra_bits = src[last_byte];
        extra_bits >>= 5;
        extra_bits &= 7;
        
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
    
    
    // This function expects to find the number of iterations in the final
    // byte, as well as the number of odd bits determined by the bit length
    // subtracted from the byte length.
    public static byte[] decompressOneStrings(byte[] src)
    {
        int last_byte   = src.length - 1;
        int iterations  = src[last_byte] & (byte)31;
        byte extra_bits = (byte)(src[last_byte] >> 5);
        extra_bits     &= 7;
        int bit_length  = (src.length - 1) * 8 - extra_bits; 
     
        // If it's not a one type string, we'll still process it.
        // This might actually compress a zero type string.
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
        
        if(iterations == 1)
        {
        	int byte_length = bit_length / 8;
        	if(bit_length % 8 != 0)
        		byte_length++;
        	byte_length *= 2;
        	byte[] buffer = new byte[byte_length];
            int uncompressed_length = decompressOneBits(src, bit_length, buffer);
            byte_length = uncompressed_length / 8;
            if(uncompressed_length % 8 != 0)
            	byte_length++;
            byte [] dst = new byte[byte_length + 1];
            System.arraycopy(buffer,  0,  dst, 0, byte_length);
            
            extra_bits = (byte)(uncompressed_length % 8);
            if(extra_bits != 0)
            	extra_bits = (byte)(8 - extra_bits);
            extra_bits <<= 5;
            dst[byte_length] = extra_bits;
     
            return dst;
        }
        else if(iterations % 2 == 0)
        {
        	int byte_length = bit_length / 8;
        	if(bit_length % 8 != 0)
        		byte_length++;
        	double factor = Math.pow(2, iterations);
        	byte_length  *= factor;
        	
        	byte[] buffer1 = new byte[byte_length * 2];
        	byte[] buffer2 = new byte[byte_length * 2];
        	
            int uncompressed_length = decompressOneBits(src, bit_length, buffer1);
            iterations--;
            while(iterations > 0)
            {
                int previous_length = uncompressed_length;
                if(iterations % 2 == 1)
                    uncompressed_length = decompressOneBits(buffer1, previous_length, buffer2);
                else
                    uncompressed_length = decompressOneBits(buffer2, previous_length, buffer1);
                iterations--;
            }
            byte_length = uncompressed_length / 8;
        	if(uncompressed_length % 8 != 0)
        		byte_length++;
        	
        	byte [] dst = new byte[byte_length + 1];
        	System.arraycopy(buffer2,  0,  dst, 0, byte_length);
        
            extra_bits = (byte)(uncompressed_length % 8);
            if(extra_bits != 0)
            	extra_bits = (byte)(8 - extra_bits);
            extra_bits <<= 5;
            dst[byte_length] = extra_bits;
            return dst;
        }
        else
        {
        	int byte_length = bit_length / 8;
        	if(bit_length % 8 != 0)
        		byte_length++;
        	double factor = Math.pow(2, iterations);
        	byte_length  *= factor;
        	
        	byte[] buffer1 = new byte[byte_length * 2];
        	byte[] buffer2 = new byte[byte_length * 2];
        	
            int uncompressed_length = decompressOneBits(src, bit_length, buffer1);
            iterations--;
            while(iterations > 0)
            {
                int previous_length = uncompressed_length;
                if(iterations % 2 == 0)
                {
                    uncompressed_length = decompressOneBits(buffer1, previous_length, buffer2);
                }
                else
                {
                    uncompressed_length = decompressOneBits(buffer2, previous_length, buffer1);
                }
                iterations--;
            }
            byte_length = uncompressed_length / 8;
        	if(uncompressed_length % 8 != 0)
        		byte_length++;
        	
        	byte [] dst = new byte[byte_length + 1];
        	
        	System.arraycopy(buffer1,  0,  dst, 0, byte_length);
           
            extra_bits = (byte)(uncompressed_length % 8);
            if(extra_bits != 0)
            	extra_bits = (byte)(8 - extra_bits);
            extra_bits <<= 5;
            dst[byte_length] = extra_bits;
            return dst;
        } 
    }
    
    public static byte [] decompressStrings(byte [] compressed_string)
    {
    	int type = getType(compressed_string);
    	if(type == 0)
    	{
    		byte [] decompressed_string = decompressZeroStrings(compressed_string);
    		return decompressed_string;
    	}
    	else if(type == 1)
    	{
    		byte [] decompressed_string = decompressOneStrings(compressed_string);
    		return decompressed_string;	
    	}
    	else
    		return compressed_string;
    }
    
    // Functions that get information about a string
    // from the trailing byte.
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
    
    public static ArrayList getStringList(int [] value)
    {
        ArrayList string_list = new ArrayList();
        
        ArrayList histogram_list = getHistogram(value);
        int min_value            = (int)histogram_list.get(0);
        int [] histogram         = (int[])histogram_list.get(1);
        int value_range          = (int)histogram_list.get(2);
	    int [] string_table      = getRankTable(histogram);
	    
	    // Get a string list from a set of deltas.
	    value[0] = value_range / 2;
	    for(int i = 1; i < value.length; i++)
	    	value[i] -= min_value;
	    byte [] string           = new byte[value.length * 16];
	    int bit_length           = packStrings2(value, string_table, string);
	    
	    
	    string_list.add(min_value);
	    string_list.add(bit_length);
	    string_list.add(string_table);
	    
	    
	    double zero_percentage   = value.length;
        if(histogram.length > 1)
        {
	        int min_histogram_value = Integer.MAX_VALUE;
	        for(int k = 0; k < histogram.length; k++)
		        if(histogram[k] < min_histogram_value)
			        min_histogram_value = histogram[k];
	        zero_percentage -= min_histogram_value;
        }	
        zero_percentage  /= bit_length;
        if(zero_percentage > .5)
        {
	        byte [] compression_string1   = compressZeroStrings(string, bit_length);
        	byte [] compression_string2   = compressZeroStrings(string);
	        string_list.add(compression_string1);
        }
        else
        {
        	byte [] compression_string1   = compressOneStrings(string, bit_length);	
        	byte [] compression_string2   = compressOneStrings(string);
        	string_list.add(compression_string1);
        }
        
        return string_list;
    }
    
    // Packing and compressing arrays where the first value
    // is not a code or a delta.
    public static ArrayList getStringList2(int [] value)
    {
        ArrayList string_list = new ArrayList();
        
        ArrayList histogram_list = getHistogram(value);
        int min_value            = (int)histogram_list.get(0);
        int [] histogram         = (int[])histogram_list.get(1);
	    int [] string_table      = getRankTable(histogram);
	    
	    for(int i = 0; i < value.length; i++)
	    	value[i] -= min_value;
	    byte [] string           = new byte[value.length * 16];
	    int bit_length           = packStrings2(value, string_table, string);
	    
	    
	    string_list.add(min_value);
	    string_list.add(bit_length);
	    string_list.add(string_table);
	    
	    
	    double zero_percentage   = value.length;
        if(histogram.length > 1)
        {
	        int min_histogram_value = Integer.MAX_VALUE;
	        for(int k = 0; k < histogram.length; k++)
		        if(histogram[k] < min_histogram_value)
			        min_histogram_value = histogram[k];
	        zero_percentage -= min_histogram_value;
        }	
        zero_percentage  /= bit_length;
        if(zero_percentage > .5)
        {
	        byte [] compression_string   = compressZeroStrings(string);
	        string_list.add(compression_string);
        }
        else
        {
        	byte [] compression_string   = compressOneStrings(string);	
        	string_list.add(compression_string);
        }
        
        return string_list;
    }
    
    
    public static ArrayList getStringList2(byte [] value)
    {
        ArrayList string_list = new ArrayList();
        
        ArrayList histogram_list = getHistogram(value);
        int min_value            = (int)histogram_list.get(0);
        int [] histogram         = (int[])histogram_list.get(1);
	    int [] string_table      = getRankTable(histogram);
	    
	    for(int i = 0; i < value.length; i++)
	    	value[i] -= min_value;
	    byte [] string           = new byte[value.length * 16];
	    int bit_length           = packStrings2(value, string_table, string);
	    
	    
	    string_list.add(min_value);
	    string_list.add(bit_length);
	    string_list.add(string_table);
	    
	    
	    double zero_percentage   = value.length;
        if(histogram.length > 1)
        {
	        int min_histogram_value = Integer.MAX_VALUE;
	        for(int k = 0; k < histogram.length; k++)
		        if(histogram[k] < min_histogram_value)
			        min_histogram_value = histogram[k];
	        zero_percentage -= min_histogram_value;
        }	
        zero_percentage  /= bit_length;
        if(zero_percentage > .5)
        {
	        byte [] compression_string   = compressZeroStrings(string);
	        string_list.add(compression_string);
        }
        else
        {
        	byte [] compression_string   = compressOneStrings(string);	
        	string_list.add(compression_string);
        }
        
        return string_list;
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
