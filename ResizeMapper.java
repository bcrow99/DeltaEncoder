import java.util.*;
import java.util.zip.*;
import java.lang.Math.*;

public class ResizeMapper
{
	// We'll assume all the image dimensions are even.
	public static int [] shrinkX(int [] src, int xdim)
	{
	    int ydim          = src.length / xdim;
	    
	    int shrunken_xdim = xdim / 2;
	   
	    int [] dst    = new int[ydim * shrunken_xdim];
	    
	    for(int i = 0; i < ydim; i++)
	        for(int j = 0; j < xdim; j += 2)
	    		   dst[i * shrunken_xdim + j / 2] = (src[i * xdim + j] + src[i * xdim + j + 1]) / 2;
	    return dst;
	}
	
	public static int [] shrinkY(int [] src, int xdim)
	{
		int ydim          = src.length / xdim;
		int shrunken_ydim = ydim / 2;
		
		int [] dst    = new int[shrunken_ydim * xdim];
	     
	    for(int j = 0; j < xdim; j++)
	    	for(int i = 0; i < ydim; i += 2)  
	    		dst[i / 2 * xdim + j] = (src[i * xdim + j] + src[(i + 1) * xdim + j]);
	    return dst;
	}

	public static int [] shrink(int [] src, int xdim)
	{
		int ydim = src.length / xdim;
		int shrunken_xdim = xdim / 2;
		int [] temp = shrinkX(src, xdim);
		int [] dst  = shrinkY(temp, shrunken_xdim);
		return dst;
	}
	
	// These functions accept arbitrary dimensions.
	public static int [] resizeX(int src[], int xdim, int new_xdim)
	{
		int ydim = src.length / xdim;
		int [] dst = new int[new_xdim * ydim];
		
		if(new_xdim == xdim)
		    for(int i = 0; i < xdim * ydim; i++)	
		    	dst[i] = src[i];
		else if(new_xdim < xdim)
		{
		    int delta               = xdim - new_xdim;
		    int number_of_segments  = delta + 1;
		    int segment_length      = xdim / number_of_segments;
		    int last_segment_length = segment_length + xdim % number_of_segments;
		    
		    int m = 0;
		    for(int i = 0; i < ydim; i++)
		    {
		    	int start = i * xdim;
		    	int stop  = start + segment_length - 1;
		    	for(int j = 0; j < number_of_segments - 1; j++)
		    	{
		    	    for(int k = start; k < stop; k++)
		    	        dst[m++] = src[k];  
		    	    start   += segment_length;
	    	        stop     = start + segment_length - 1;
		    	}
		    	stop            = start + last_segment_length;
		    	for(int k = start; k < stop; k++)
	    	        dst[m++] = src[k];  
		    }
		}
		else if(new_xdim > xdim)
		{
			int delta                 = new_xdim - xdim;
		    int number_of_segments    = delta + 1;
		    int segment_length        = xdim / number_of_segments;
		    int last_segment_length   = segment_length + xdim % number_of_segments;
		    
		    int m = 0;
		    for(int i = 0; i < ydim; i++)
		    {
		    	int start = i * xdim;
		    	int stop  = start + segment_length;
		    	
		    	for(int j = 0; j < number_of_segments - 1; j++)
		    	{
		    	    for(int k = start; k < stop; k++)
		    	        dst[m++] = src[k];
		    	    dst[m++] = (src[stop] + src[stop - 1]) / 2;
		    	    start   += segment_length;
	    	        stop     = start + segment_length;
		    	}
		    	// Write the values from the last segment without adding a pixel.
		    	stop = start + last_segment_length;
		    	for(int k = start; k < stop; k++)
	    	        dst[m++] = src[k];
		    }
		}
		return dst;
	}
	
	public static int [] resizeY(int src[], int xdim, int new_ydim)
	{
		int ydim = src.length / xdim;
		int [] dst = new int[xdim * new_ydim];
		
		if(new_ydim == ydim)
			for(int i = 0; i < xdim * ydim; i++)	
		    	dst[i] = src[i];	
		else if(new_ydim < ydim)
		{
			int delta                 = ydim - new_ydim;
		    int number_of_segments    = delta + 1;
		    int segment_length        = ydim / number_of_segments;
		    int last_segment_length   = segment_length + ydim % number_of_segments;
		    
		    int m = 0;
		    for(int i = 0; i < xdim; i++)
		    { 
		    	m         = i;
		    	int start = i;
		    	int stop  = start + segment_length * xdim - xdim;	
		    	for(int j = 0; j < number_of_segments - 1; j++)
		    	{
		    	    for(int k = start; k < stop; k += xdim) 
		    	    {
		    	    	dst[m] = src[k];
		    	    	m += xdim;
		    	    }
		    	    
		    	    start = stop + xdim;
		    	    stop  = start + segment_length * xdim - xdim;
		    	}
		    	stop = start + last_segment_length * xdim;
		    	for(int k = start; k < stop; k += xdim) 
	    	    {
	    	    	dst[m] = src[k];
	    	    	m += xdim;
	    	    }
		    }
		}
		else if(new_ydim > ydim)
		{
			int delta                 = new_ydim - ydim;
		    int number_of_segments    = delta + 1;
		    int segment_length        = ydim / number_of_segments;
		    int last_segment_length   = segment_length + ydim % number_of_segments;
		    
		    int m = 0;
		    for(int i = 0; i < xdim; i++)
		    {
		    	m = i;
		    	int start = i;
		    	int stop  = start + segment_length * xdim;
		    	
		    	for(int j = 0; j < number_of_segments - 1; j++)
		    	{
		    	    for(int k = start; k < stop; k += xdim) 
		    	    {
		    	    	dst[m] = src[k];
		    	    	m += xdim;
		    	    }
		    	    // We add a pixel at the end of each segment.
		    	    dst[m] = (src[stop] + src[stop - xdim]) / 2;
		    	    m     += xdim;
		    	    start  = stop;
		    	    stop   = start + segment_length * xdim; 
		    	}
		    	
		    	// We write the last segment without adding a pixel.
		    	stop = start + last_segment_length * xdim;
		    	for(int k = start; k < stop; k += xdim) 
	    	    {
	    	    	dst[m] = src[k];
	    	    	m += xdim;
	    	    }
		    }
		}
		
		return dst;
	}
	
	public static ArrayList getColumnList(int [] src, int xdim, int ydim, int interval)
    {
    	ArrayList column_list = new ArrayList();
    	for(int j = interval - 1; j < (xdim - interval); j += interval)
        {
        	int [] column = new int[ydim];
        	int k = 0;
            for(int i = 0; i < ydim; i++)
                column[k++] = src[i * xdim + j]	;
            column_list.add(column);
        }
    	return column_list;
    }
	public static ArrayList getColumnListDifference(int [] src, int xdim, int ydim, int interval)
    {
    	ArrayList column_list = new ArrayList();
    	for(int j = interval - 1; j < (xdim - interval); j += interval)
        {
        	int [] column = new int[ydim];
            for(int i = 0; i < ydim; i++)
            {
            	int k      = i * xdim + j;
                column[i]  = src[k];
                column[i] -= (src[k] + src[k + 1]) / 2;
            }
            column_list.add(column);
        }
    	return column_list;
    }
   
	public static ArrayList getRowList(int [] src, int xdim, int ydim, int interval)
    {
    	ArrayList row_list = new ArrayList();
    	
    	for(int i = interval - 1; i < (ydim - interval); i += interval)
        {
    		int [] row = new int[xdim];
    		for(int j = 0; j < xdim; j++)
    		{
    			int k = i * xdim + j;
        	    row[j]  = src[k]; 
    		}
            row_list.add(row);
        }
    	return row_list;
    }
   
	public static ArrayList getRowListDifference(int [] src, int xdim, int ydim, int interval)
    {
    	ArrayList row_list = new ArrayList();
    	
    	for(int i = interval - 1; i < (ydim - interval); i += interval)
        {
    		int [] row = new int[xdim];
    		for(int j = 0; j < xdim; j++)
    		{
    			int k   = i * xdim + j;
        	    row[j]  = src[k]; 
        	    row[j] -= (src[k] + src[k + xdim]) / 2;
        	    
    		}
            row_list.add(row);
        }
    	return row_list;
    }
	
	public static ArrayList resizeDown(int src[], int xdim, int new_xdim, int new_ydim)
	{
		
		ArrayList resize_list = new ArrayList();
		
		
	    int ydim                = src.length / xdim;
	    
	    int delta               = xdim - new_xdim;
	    int number_of_segments  = delta + 1;
	    int interval            = xdim / number_of_segments;
	    ArrayList column_list = ResizeMapper.getColumnListDifference(src, xdim, ydim, interval);
		int [] tmp = resizeX(src, xdim, new_xdim);
		
		delta               = ydim - new_ydim;
	    number_of_segments  = delta + 1;
	    interval            = ydim / number_of_segments;
	    ArrayList row_list = ResizeMapper.getRowListDifference(tmp, new_xdim, ydim, interval);
		int [] dst = resizeY(tmp, new_xdim, new_ydim);
		
		resize_list.add(dst);
		resize_list.add(column_list);
		resize_list.add(row_list);
		
		return resize_list;
	}
	
	public static int [] resizeUp(ArrayList src_list, int xdim, int new_xdim, int new_ydim)
	{
		int [] src = (int [])src_list.get(0);
		int ydim   = src.length / xdim;
		int [] tmp = resizeY(src, xdim, new_ydim);
		int delta              = new_ydim - ydim;
		int number_of_segments = delta + 1;
		int interval           = new_ydim / number_of_segments;
		ArrayList row_list    = (ArrayList)src_list.get(2);
		int k = 0;
		for(int i = interval - 1; i < (new_ydim - interval); i += interval)
        {
    		int [] row = (int [])row_list.get(k);
    		k++;
    	
    		for(int j = 0; j < xdim; j++)
        	    tmp[i * xdim + j] += row[j];  
        }
		
		int [] dst = resizeX(tmp, xdim, new_xdim);
		delta                 = new_xdim - xdim;
		number_of_segments    = delta + 1;
		interval              = new_xdim / number_of_segments;
		ArrayList column_list = (ArrayList)src_list.get(1);
		k = 0;
		for(int j = interval - 1; j < (new_xdim - interval); j += interval)
        {
        	int [] column = (int [])column_list.get(k);
        	k++;
        	
            for(int i = 0; i < ydim; i++)
                dst[i * new_xdim + j] += column[i]; 
        }
		return dst;
	}
	
	public static int [] resize(int src[], int xdim, int new_xdim, int new_ydim)
	{
		int [] tmp = resizeX(src, xdim, new_xdim);
		int [] dst = resizeY(tmp, new_xdim, new_ydim);
		return dst;
	}
	
	public static int [] expandX(int src[], int xdim)
	{
		int ydim = src.length / xdim;
		int new_xdim = xdim * 2;
	
		int [] dst = new int[new_xdim * ydim];
		for(int i = 0; i < ydim; i++)
		{
			for(int j = 0; j < xdim; j++)
			{
				dst[i * new_xdim + 2 * j]     = src[i * xdim + j];
				dst[i * new_xdim + 2 * j + 1] = src[i * xdim + j];
			}
		}
		return dst;
	}
	
	public static int [] expandY(int [] src, int xdim)
	{
		int ydim     = src.length / xdim;
		int new_ydim = 2 * ydim;
		
		int [] dst = new int[xdim * new_ydim];
		for(int i = 0; i < ydim; i++)
		{
			for(int j = 0; j < xdim; j++)
			{  
				dst[2 * i * xdim + j] = src[i * xdim + j];  
		        dst[2 * i * xdim + xdim + j] = src[i * xdim + j];
			}
		}
	    return dst;	
	}
	
	public static int [] expand(int [] src, int xdim)
	{
		int [] tmp = expandX(src, xdim);
		int [] dst = expandY(tmp, xdim * 2);
		return dst;
	}
	
	   public static int[] getHuffmanLength(int [] frequency)
	    {
	    	// The in-place processing is one of the
	    	// trickiest parts of this code, but we
	    	// don't want to modify the input so we'll 
	    	// make a copy and work from that.
	    	int n = frequency.length;
	    	
	    	int [] w = new int[n];
	    	for(int i = 0; i < n; i++)
	    		w[i] = frequency[i];
	    	
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
	     
	    
	    public static long[] getCanonicalCode(int [] length)
	    {
	    	int n = length.length;
	    	
	        long [] code         = new long[n];
	        long [] shifted_code = new long[n];
	        int max_length = length[n - 1];
	        
	        code[0] = 0;
	        shifted_code[0] = 0;
	        for(int i = 1; i < n; i++)
	        {
	        	code[i]   = (int)(code[i - 1] + Math.pow(2, max_length - length[i - 1]));
	        	int shift = max_length - length[i];
	        	shifted_code[i] = code[i] >> shift;
	        }
	        
	        long [] reversed_code = new long[n];
	        reversed_code[0] = 0;
	        for(int i = 1; i < n; i++)
	        {
	        	long code_word = shifted_code[i];
	        	int  code_length = length[i];
	        	long code_mask = 1;
	        	
	        	for(int j = 0; j < code_length; j++)
	        	{
	        		long result = code_word & (code_mask << j);
	        		if(result != 0)
	        		{
	        			int shift = (code_length - 1) - j;
	        			reversed_code[i] |= code_mask << shift;
	        		}
	        	}
	        }
	        return reversed_code;
	    }
	    

	    public static int packCode(int src[], int table[], long [] code, int [] length, byte dst[])
	    {
	    	int n = code.length;
	    
	    	int current_bit = 0;
	    	for(int i = 0; i < src.length; i++)
	    	{
	    	    int j = src[i];
	    	    int k = table[j];
	    	    
	    	    long code_word   = code[k];
	    	    int  code_length = length[k];
	    	    
	    	    
	    	    int offset = current_bit % 8;  
	    	    code_word <<= offset; 
	    	    int or_length = code_length + offset;
	    	    int number_of_bytes = or_length / 8;
	    	    if(or_length % 8 != 0)
	    	        number_of_bytes++;
	    	   
	    	    int current_byte = current_bit / 8;
	    	   
	    	    
	    	    
	    	    if(number_of_bytes == 1)
	    	    {
	    	    	dst[current_byte] |= (byte)(code_word & 0x00ff);
	    	    	
	    	    }
	    	    else
	    	    {
	    	    	for(int m = 0; m < number_of_bytes - 1; m++)
	        	    {
	        	        dst[current_byte] |= (byte)(code_word & 0x00ff);
	        	        current_byte++;
	        	        code_word >>= 8;
	        	    }	
	    	    	dst[current_byte] = (byte)(code_word & 0x00ff);
	    	    }
	    	    
	    	    current_bit += code_length;
	    	}
	    	
	    	int bit_length = current_bit;
	    	return bit_length;
	    }
	  
	   
	    
	    public static int unpackCode(byte src[], int table[], long [] code, int [] code_length, int string_length, int dst[])
	    {
	    	boolean debug = false;
	    	
	    	int number_of_different_values = table.length;
	        int [] inverse_table = new int[number_of_different_values];
	        for(int i = 0; i < number_of_different_values; i++)
	        {
	            int j            = table[i];
	            inverse_table[j] = i;
	        }
	        
	        int number_of_codes = code.length;
	        int max_length      = code_length[code.length - 1];
	        int max_bytes       = max_length / 8;
	        if(max_length % 8 != 0)
	        	max_bytes++;
	        
	        
	        if(debug)
	        {
	            // Segment of the string bytes we want to debug.
	            int start = 0;
	            int stop  = 10;	
	        
	            System.out.println("Prefix free code:");
	            for(int i = 0; i < code.length; i++)
	            {
	                for(int j = 0; j < code_length[i]; j++)
	                {
	                    long mask = 1;
	                    mask <<= j;
	                    if((code[i] & mask) == 0)
	                        System.out.print(0);
	                    else
	                	    System.out.print(1);
	                }
	                System.out.println();
	            }
	            System.out.println();
	        
	            System.out.println("Look-up table:");
	            for(int i = 0; i < table.length; i++)
	        	    System.out.println(i + " -> " + table[i]);;
	            System.out.println();
	        
	            System.out.println("Bytes from delta string:");
	        
	            for(int i = start; i < stop; i++)
	            {
	        	    System.out.print(i + "  ");
	                for(int j = 0; j < 8; j++)
	                {
	        	        byte src_mask = 1;	
	        	        src_mask <<= j;
	        	        int src_bit = src_mask & src[i];
	        	        if(src_bit == 0)
	        		        System.out.print("0");
	        	        else
	        		        System.out.print("1");
	                }
	                System.out.println();
	            }
	            System.out.println();
	        }
	        
	        int current_bit     = 0;
	        int offset          = 0;
	        int current_byte    = 0;
	    	int number_unpacked = 0;
	    	int dst_byte        = 0;
	    	
	    	boolean matched = true;
	    	
	        for(int i = 0; i < dst.length; i++)
	        {
	        	long src_word       = 0;
	    	
	            for(int j = 0; j < max_bytes; j++)
	            {
	          	    long src_byte = (long)src[current_byte + j];
	          	    if(src_byte < 0)
	          		    src_byte += 256;
	          	    if(j == 0)
	          	        src_byte >>= offset;
	          	    else
	          	        src_byte <<= j * 8 - offset;
	          	    src_word |= src_byte;
	            }

	            if(offset != 0)
	            {
	          	    long src_byte = (long)src[current_byte + max_bytes];
	          	    if(src_byte < 0)
	          		    src_byte += 256;
	          	    
	          	    src_byte <<= max_bytes * 8 - offset;
	          	   
	          	    src_word |= src_byte;
	            }
	      	
	            for(int j = 0; j < code.length; j++)
	            {
	          	    long code_word = code[j];
	          	    long mask = -1;
	          	    mask <<= code_length[j];
	          	    mask = ~mask;
	          	    
	          	    long masked_src_word = src_word & mask;
	          	    long masked_code_word = code_word & mask;
	          	    
	          	    if(masked_src_word == masked_code_word)
	          	    {
	          	    	dst[dst_byte++] = inverse_table[j];
		                number_unpacked++;
	          		    current_bit += code_length[j];
	          		    current_byte = current_bit / 8;
	          		    offset       = current_bit % 8;
	          		    break;
	          	    }
	          	    else if(j == code.length - 1)
	          	    {
	          	    	/*
	          	    	System.out.println("No match for prefix-free code at byte " + current_byte);
	          	    	if(debug)
	          	    	{
	          		        long code_mask = 1;
	          		        System.out.println("Source word:");
	          		        for(int k = 0; k < code_length[j]; k++)
	          		        {
	          		        	long src_result = (code_mask << k) & src_word;
	          		        	if(src_result == 0)
	          		        		System.out.print(0);
	          		        	else
	          		        		System.out.print(1);
	          		        }
	          		        System.out.println();
	          	    	}
	          	    	*/
	          	    }
	            }
	        }  
	        
	        
	        if(debug)
	        {
	            System.out.println("The length of the original bit string was " + string_length);
	            System.out.println("Bits unpacked was " + current_bit);
	            System.out.println();
	        }
	       
	    	return number_unpacked;
	    }
	    
	    
	    public static long[] getUnaryCode(int n)
	    {
	        long [] code         = new long[n];
	       
	        code[0] = 0;
	        
	        long addend = 1;
		    for(int i = 1; i < n; i++)
		    {
		    	code[i] = code[i - 1] + addend;
		    	addend *= 2;
		    }
	        return code;
	    }
	    
}