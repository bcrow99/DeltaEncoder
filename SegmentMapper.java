import java.util.*;
import java.util.zip.*;
import java.lang.Math.*;
import java.math.*;

public class SegmentMapper 
{
	// This function returns a segment list with regular lengths that might be uneven.
	// Unfinished.  Not clear that this gets us anything.If we're using uneven lengths
	// we should probably always start off with a minimum length 1 for zero strings
	// and 2 for one strings, and not use regular lengths.
	public static ArrayList getSegmentedData(byte[] string, int bitlength) 
	{
		ArrayList list = new ArrayList();
		
		int string_type       = StringMapper.getType(string);
		int string_iterations = StringMapper.getIterations(string);
		int string_bitlength  = StringMapper.getBitlength(string);
	
		int number_of_segments = string_bitlength / bitlength;
		int segment_bitlength  = bitlength;
		int segment_bytelength = bitlength / 8;
		// Add byte for string information.
		segment_bytelength++;
		
		int remainder                = string_bitlength % bitlength;
		int last_segment_bitlength   = bitlength + remainder;
		int last_segment_bytelength  = last_segment_bitlength / 8;
		
		// The last segment is the only one that might need an extra byte for odd bits.
		if (remainder % 8 != 0)
			last_segment_bytelength++;
		// Add byte for string information.
		last_segment_bytelength++;
		
		// Get information that goes in last byte of last segment.
		byte extra_bits = 0;
		if(remainder != 0)
		{
			extra_bits = (byte)(8 - remainder);
		    extra_bits <<= 5;
		}
		
		// Get a list of uncompressed segments of equal length, except possibly the last segment.
		// Set iterations to 16 or 0, depending on the type.  Extra bits will be 0, except for possibly
		// the last segment.
		ArrayList segment_list = new ArrayList();
	
        
		if(bitlength % 8 == 0)
		{
		    int i;
		
		    for(i = 0; i < number_of_segments - 1; i++)
		    {
			    byte [] segment = new byte[segment_bytelength];
	    	    for(int j = 0; j < segment.length - 1; j++)
				    segment[j] = string[i * (segment_bytelength - 1) + j];
	    	
	    	    double zero_ratio = StringMapper.getZeroRatio(segment, segment_bitlength);
	    	    byte iterations   = 0;
			    if (zero_ratio < .5)
				    iterations = 16;
			    segment[segment.length - 1] = iterations;
			    segment_list.add(segment);	
		    }
		
		    i = number_of_segments - 1; 
		
		    byte [] segment = new byte[last_segment_bytelength];   
    	    for(int j = 0; j < segment.length - 1; j++)
			    segment[j] = string[i * (segment_bytelength - 1) + j];
 
    	    double zero_ratio = StringMapper.getZeroRatio(segment, last_segment_bitlength);
    	    byte iterations   = 0;
		    if (zero_ratio < .5)
			    iterations = 16;
		    segment[segment.length - 1] = iterations;
		    segment_list.add(segment);
		
		
		    for(i = 0; i < number_of_segments; i++)
		    {
			    segment = (byte [])segment_list.get(i);
			    int type = StringMapper.getType(segment);
			    if(type == 0)
			    {
				    byte [] compressed_segment = StringMapper.compressZeroStrings(segment);
				    if(compressed_segment.length < segment.length)
					    segment_list.set(i, compressed_segment);	
			    }
			    else
			    {
				    byte [] compressed_segment = StringMapper.compressOneStrings(segment);
			        if(compressed_segment.length < segment.length)
			            segment_list.set(i, compressed_segment);
			    }
		    }
		}
		else
		{
			// The information for a segment will always be collected from at least
			// two bytes in the string, although there might be intermediate bytes.
			
			// Masks that might or might not be helpful.
			// Might be able to do everything with shifting.
		    byte [] leading_mask  = new byte[7];
			leading_mask[0] = -2;
			leading_mask[1] = -4;
			leading_mask[2] = -8;
			leading_mask[3] = -16;
			leading_mask[4] = -32;
			leading_mask[5] = -64;
			leading_mask[6] = -128;
			
			
			byte [] trailing_mask  = new byte[7];
			trailing_mask[0] = 1;
			trailing_mask[1] = 3;
			trailing_mask[2] = 7;
			trailing_mask[3] = 15;
			trailing_mask[4] = 31;
			trailing_mask[5] = 63;
			trailing_mask[6] = 127;
	        
			
			int i;
			
			// Subtract the first and last string bytes and the information byte
			// to get the number of intermediate bytes, which might be 0.
			int intermediate_bytes = segment_bytelength - 3; 
		   
			int string_bit_offset  = 0;
		    for(i = 0; i < number_of_segments - 1; i++)
		    {
			    byte [] segment = new byte[segment_bytelength];
			    
			    // Calculate this at the beginning and use it later, because
			    // the string_bit_offset will be getting incremented and we
			    // want to use the initial value.
			    int trailing_bit_offset = string_bit_offset + segment_bitlength;
			    
			    
			    int string_byte_offset = string_bit_offset / 8;
			    int modulus            = string_bit_offset % 8;
			    if(modulus == 0)
			    {
			    	segment[0] = string[string_byte_offset];
			    	string_bit_offset += 8;
			    	string_byte_offset++;
			    	int j;
			    	for(j = 0; j < intermediate_bytes; j++)
			    	{
			    		segment[j] = string[string_byte_offset];
				    	string_bit_offset += 8;
				    	string_byte_offset++;	
			    	}
			    	
			    	modulus = trailing_bit_offset % 8;
			    	string_bit_offset += 8 - modulus;
			    	// The modulus has to be non-zero but we'll do an error check.
			    	if(modulus == 0)
			    	    System.out.println("Trailing modulus is zero when leading byte was even.");
			    	
			    	
			    	// We don't increment the byte offset, which means the following
			    	// leading byte will have to be divided and the first segment right shifted,
			    	// and the second segment left shifted. We don't have to do any shifting in
			    	// this case.  We don't really have to do any masking either since the 
			    	// extra bits should be overwritten, but we'll set the extra bits to zero.
			    	// It might help debugging the bit stream, or even the compression rate
			    	// if the segments are small enough relative to the number of extra bits.
			    	
			    	int k             = 6 - (modulus - 1);
			    	byte mask         = trailing_mask[k];
			    	byte masked_value = (byte)(string[string_byte_offset] & mask);
			    	segment[j]        = masked_value;
			    	
			    	j++;
			    	if(j == segment_bytelength - 1)
					{
			    		extra_bits = 0;
						if(modulus != 0)
						{
							extra_bits = (byte)(8 - modulus);
						    extra_bits <<= 5;
						}
			    		segment[j] = extra_bits;
			    		double zero_ratio = StringMapper.getZeroRatio(segment, segment_bitlength);
			    	    byte iterations   = 0;
					    if (zero_ratio <= .5)
						    iterations = 16;
					    segment[j] |= iterations;
					}
					else
						System.out.println("Wrong index " + j + " for segment byte length " + segment_bytelength);
				    segment_list.add(segment);
			    }
			    else  //Non zero modulus for bit offset.
			    {
			    	int k             = 6 - (modulus - 1);
			    	byte mask         = leading_mask[k];
			    	byte masked_value = (byte)(string[string_byte_offset] & mask);
			    }
		    }
		    
		    // Odd case.
		    i = number_of_segments - 1; 
		
		    byte [] segment = new byte[last_segment_bytelength];   
    	    for(int j = 0; j < segment.length - 1; j++)
			    segment[j] = string[i * (segment_bytelength - 1) + j];
 
    	    double zero_ratio = StringMapper.getZeroRatio(segment, last_segment_bitlength);
    	    byte iterations   = 0;
		    if (zero_ratio < .5)
			    iterations = 16;
		    segment[segment.length - 1] = iterations;
		    segment_list.add(segment);
		
		
		    for(i = 0; i < number_of_segments; i++)
		    {
			    segment = (byte [])segment_list.get(i);
			    int type = StringMapper.getType(segment);
			    if(type == 0)
			    {
				    byte [] compressed_segment = StringMapper.compressZeroStrings(segment);
				    if(compressed_segment.length < segment.length)
					    segment_list.set(i, compressed_segment);	
			    }
			    else
			    {
				    byte [] compressed_segment = StringMapper.compressOneStrings(segment);
			        if(compressed_segment.length < segment.length)
			            segment_list.set(i, compressed_segment);
			    }
		    }	
		}
		list.add(segment_list);
		list.add(last_segment_bitlength);
		return list;
	}

	
	public static ArrayList getSegmentedData2(byte[] string, int bitlength) 
	{
		ArrayList list = new ArrayList();
		
		// The minimum segment bit length is a multiple of 8 to keep things simple.
		// Open question if this constraint significantly affects rate of compression.
		// The only segment that might have an odd length is the last one.
		if(bitlength % 8 != 0)
		{
			// Return empty list.
		    System.out.println("Segment bitlength must be a multiple of 8.");
		    return list;
		}
		
		int string_type       = StringMapper.getType(string);
		int string_iterations = StringMapper.getIterations(string);
		int string_bitlength  = StringMapper.getBitlength(string);
	
		int number_of_segments = string_bitlength / bitlength;
		int segment_bitlength  = bitlength;
		int segment_bytelength = bitlength / 8;
		// Add byte for string information.
		segment_bytelength++;
		
		int remainder                = string_bitlength % bitlength;
		int last_segment_bitlength   = bitlength + remainder;
		int last_segment_bytelength  = last_segment_bitlength / 8;
		
		// The last segment is the only one that might need an extra byte for odd bits.
		if (remainder % 8 != 0)
			last_segment_bytelength++;
		// Add byte for string information.
		last_segment_bytelength++;
		
		// Get information that goes in last byte.
		byte extra_bits = 0;
		if(remainder != 0)
		{
			extra_bits = (byte)(8 - remainder);
		    extra_bits <<= 5;
		}
		
		// Get a list of uncompressed segments of equal length, except possibly the last segment.
		// Set iterations to 16 or 0, depending on the type.  Extra bits will be 0, except for possibly
		// the last segment.
		ArrayList segment_list = new ArrayList();
		
		int i;
		
		for(i = 0; i < number_of_segments - 1; i++)
		{
			byte [] segment = new byte[segment_bytelength];
	    	for(int j = 0; j < segment.length - 1; j++)
				segment[j] = string[i * (segment_bytelength - 1) + j];
	    	
	    	double zero_ratio = StringMapper.getZeroRatio(segment, segment_bitlength);
	    	byte iterations   = 0;
			if (zero_ratio < .5)
				iterations = 16;
			segment[segment.length - 1] = iterations;
			segment_list.add(segment);	
		}
		
		i = number_of_segments - 1; 
		
		byte [] segment = new byte[last_segment_bytelength];   
    	for(int j = 0; j < segment.length - 1; j++)
			segment[j] = string[i * (segment_bytelength - 1) + j];
 
    	double zero_ratio = StringMapper.getZeroRatio(segment, last_segment_bitlength);
    	byte iterations   = 0;
		if (zero_ratio < .5)
			iterations = 16;
		segment[segment.length - 1] = iterations;
		segment_list.add(segment);
		
		for(i = 0; i < number_of_segments; i++)
		{
			segment = (byte [])segment_list.get(i);
			int type = StringMapper.getType(segment);
			if(type == 0)
			{
				byte [] compressed_segment = StringMapper.compressZeroStrings(segment);
				if(compressed_segment.length < segment.length)
					segment_list.set(i, compressed_segment);
					
			}
			else
			{
				byte [] compressed_segment = StringMapper.compressOneStrings(segment);
				if(compressed_segment.length < segment.length)
					segment_list.set(i, compressed_segment);
			}
		}
		
		list.add(segment_list);
		list.add(last_segment_bitlength);
		return list;
	}
    
	public static ArrayList getMergedData(byte[] string)
	{
		ArrayList list = new ArrayList();
		
		int string_bitlength  = StringMapper.getBitlength(string);
		
		int bit_offset = 0;
		
		byte [] mask      = new byte[8];
		byte current_mask = 1;
		for(int i = 0; i < 8; i++)
		{
			mask[i]        = current_mask;
			current_mask <<= 1;
		}
		
		ArrayList init_list = new ArrayList();
		
		while(bit_offset < string_bitlength)
		{
			int i = bit_offset % 8;
			int j = bit_offset / 8;
			int k = 1;
			
			if((string[j] & mask[i]) == 0)
			{
			    // Segment type is 0.
			    bit_offset++;
			    i = bit_offset % 8;
			    j = bit_offset / 8;
			    if((string[j] & mask[i]) == 0)
				{
			    	bit_offset++;
			    	k++;
				}
			    else
			    {
			    	ArrayList segment = new ArrayList();
			    	int type          = 0;
			    	int length        = k;
			    	segment.add(type);
			    	segment.add(length);
			    	init_list.add(segment);
			    }
			}
			else
			{
				// Segment type is 1.
				bit_offset++;
			    i = bit_offset % 8;
			    j = bit_offset / 8;
			    if((string[j] & mask[i]) == 1)
				{
			    	// Keep going until we get to a 0 bit.
			    	bit_offset++;
			    	k++;
				}
			    else
			    {
			    	// Unlike 0 strings, we include the determining bit.
			    	bit_offset++;
			    	ArrayList segment = new ArrayList();
			    	int type          = 1;
			    	int length        = k + 1;
			    	segment.add(type);
			    	segment.add(length);
			    	init_list.add(segment);
			    }
			}
		}
		
		ArrayList aggregated_list = new ArrayList();
		ArrayList current_segment = new ArrayList();
		ArrayList init_segment    = (ArrayList)init_list.get(0);
		
		int current_type   = (int)init_segment.get(0);
		int current_length = (int)init_segment.get(1);
		current_segment.add(current_type);
		current_segment.add(current_length);
		
		int previous_type  = current_type;
		for(int i = 1; i < init_list.size() - 1; i++)
		{
			init_segment   = (ArrayList)init_list.get(i);
			current_type   = (int)init_segment.get(0);
			current_length = (int)init_segment.get(1);
			
			if(current_type == previous_type)
			    current_segment.add(current_length);
			else
			{
				aggregated_list.add(current_segment);
				current_segment = new ArrayList();
				current_segment.add(current_type);
				current_segment.add(current_length);
				
				previous_type = current_type;
			}
		}
		
		int i          = init_list.size() - 1;
		init_segment   = (ArrayList)init_list.get(i);
		current_type   = (int)init_segment.get(0);
		current_length = (int)init_segment.get(1);
		
		if(current_type == previous_type)
		{
		    current_segment.add(current_length);
		    aggregated_list.add(current_segment);
		}
		else
		{
			aggregated_list.add(current_segment);
			current_segment = new ArrayList();
			current_segment.add(current_type);
			current_segment.add(current_length);
			aggregated_list.add(current_segment);
		}
		
		int number_of_zero_segments = 0;
		int number_of_one_segments  = 0;
		
		int total_zero_length = 0;
		int max_zero_length   = 0;
		int total_one_length  = 0;
		int max_one_length    = 0;
		int max_one_cat_length = 0;
		for(i = 0; i < aggregated_list.size(); i++)
		{
			ArrayList segment = (ArrayList)aggregated_list.get(i);
			current_type      = (int)segment.get(0);
			if(current_type == 0)
			{
				number_of_zero_segments++;
				current_length = (int)segment.get(1);
				total_zero_length += current_length;
				if(current_length > max_zero_length)
					max_zero_length = current_length;
			}
			else
			{
				number_of_one_segments++;
				current_length         = (int)segment.get(1);
				total_one_length      += current_length;
				int current_cat_length = current_length;
				
				// There can be multiple one strings.
				for(int j = 1; j < segment.size(); j++)
				{
					current_length = (int)segment.get(j);
					total_one_length += current_length;
					if(current_length > max_one_length)
						max_one_length = current_length;  
					current_cat_length += current_length;
				}
				
				if(current_cat_length > max_one_cat_length)
					max_one_cat_length = current_cat_length;	
			}
		}
		
		int average_zero_length = total_zero_length / number_of_zero_segments;
		int average_one_length = total_one_length / number_of_one_segments;
		
		System.out.println();
		System.out.println("Number of zero segments is " + number_of_zero_segments);
		System.out.println("Average length is " + average_zero_length);
		System.out.println("Maximum length is " + max_zero_length);
		System.out.println();
		System.out.println("Number of one segments is "  + number_of_one_segments);
		System.out.println("Average length of concatenated string is " + average_one_length);
		System.out.println("Maximum individual length is " + max_one_length);
		System.out.println("Maximum cat length is " + max_one_cat_length);
		System.out.println();
		
		return aggregated_list;
	}
	
	
	public static byte [] getMergedString(byte[] string)
	{
		ArrayList list = new ArrayList();
		
		int string_bitlength  = StringMapper.getBitlength(string);
		
		int bit_offset = 0;
		
		byte [] mask      = new byte[8];
		byte current_mask = 1;
		for(int i = 0; i < 8; i++)
		{
			mask[i]        = current_mask;
			current_mask <<= 1;
		}
		
		ArrayList init_list = new ArrayList();
		
		while(bit_offset < string_bitlength)
		{
			int i = bit_offset % 8;
			int j = bit_offset / 8;
			int k = 1;
			
			if((string[j] & mask[i]) == 0)
			{
			    // Segment type is 0.
			    bit_offset++;
			    i = bit_offset % 8;
			    j = bit_offset / 8;
			    if((string[j] & mask[i]) == 0)
				{
			    	bit_offset++;
			    	k++;
				}
			    else
			    {
			    	ArrayList segment = new ArrayList();
			    	int type          = 0;
			    	int length        = k;
			    	segment.add(type);
			    	segment.add(length);
			    	init_list.add(segment);
			    }
			}
			else
			{
				// Segment type is 1.
				bit_offset++;
			    i = bit_offset % 8;
			    j = bit_offset / 8;
			    if((string[j] & mask[i]) == 1)
				{
			    	// Keep going until we get to a 0 bit.
			    	bit_offset++;
			    	k++;
				}
			    else
			    {
			    	// Unlike 0 strings, we include the determining bit.
			    	bit_offset++;
			    	ArrayList segment = new ArrayList();
			    	int type          = 1;
			    	int length        = k + 1;
			    	segment.add(type);
			    	segment.add(length);
			    	init_list.add(segment);
			    }
			}
		}
		
		ArrayList aggregated_list = new ArrayList();
		ArrayList current_segment = new ArrayList();
		ArrayList init_segment    = (ArrayList)init_list.get(0);
		
		int current_type   = (int)init_segment.get(0);
		int current_length = (int)init_segment.get(1);
		current_segment.add(current_type);
		current_segment.add(current_length);
		
		int previous_type  = current_type;
		for(int i = 1; i < init_list.size() - 1; i++)
		{
			init_segment   = (ArrayList)init_list.get(i);
			current_type   = (int)init_segment.get(0);
			current_length = (int)init_segment.get(1);
			
			if(current_type == previous_type)
			    current_segment.add(current_length);
			else
			{
				aggregated_list.add(current_segment);
				current_segment = new ArrayList();
				current_segment.add(current_type);
				current_segment.add(current_length);
				
				previous_type = current_type;
			}
		}
		
		int i          = init_list.size() - 1;
		init_segment   = (ArrayList)init_list.get(i);
		current_type   = (int)init_segment.get(0);
		current_length = (int)init_segment.get(1);
		
		if(current_type == previous_type)
		{
		    current_segment.add(current_length);
		    aggregated_list.add(current_segment);
		}
		else
		{
			aggregated_list.add(current_segment);
			current_segment = new ArrayList();
			current_segment.add(current_type);
			current_segment.add(current_length);
			aggregated_list.add(current_segment);
		}
		
		int number_of_zero_segments = 0;
		int number_of_one_segments  = 0;
		
		int total_zero_length = 0;
		int max_zero_length   = 0;
		int total_one_length  = 0;
		int max_one_length    = 0;
		int max_one_cat_length = 0;
		for(i = 0; i < aggregated_list.size(); i++)
		{
			ArrayList segment = (ArrayList)aggregated_list.get(i);
			current_type      = (int)segment.get(0);
			if(current_type == 0)
			{
				number_of_zero_segments++;
				current_length = (int)segment.get(1);
				total_zero_length += current_length;
				if(current_length > max_zero_length)
					max_zero_length = current_length;
			}
			else
			{
				number_of_one_segments++;
				current_length         = (int)segment.get(1);
				total_one_length      += current_length;
				int current_cat_length = current_length;
				
				// There can be multiple one strings.
				for(int j = 1; j < segment.size(); j++)
				{
					current_length = (int)segment.get(j);
					total_one_length += current_length;
					if(current_length > max_one_length)
						max_one_length = current_length;  
					current_cat_length += current_length;
				}
				
				if(current_cat_length > max_one_cat_length)
					max_one_cat_length = current_cat_length;	
			}
		}
		
		int average_zero_length = total_zero_length / number_of_zero_segments;
		int average_one_length  = total_one_length / number_of_one_segments;
		
		// Good place to check input.
		/*
		System.out.println();
		System.out.println("Number of zero segments is " + number_of_zero_segments);
		System.out.println("Average length is " + average_zero_length);
		System.out.println("Maximum length is " + max_zero_length);
		System.out.println();
		System.out.println("Number of one segments is "  + number_of_one_segments);
		System.out.println("Average length of concatenated string is " + average_one_length);
		System.out.println("Maximum individual length is " + max_one_length);
		System.out.println("Maximum cat length is " + max_one_cat_length);
		System.out.println();
		*/
		
		
		// Assuming the input has been reduce to 0 and 10 strings, the
		// merged_string should be no larger than the original string.
		byte [] unclipped_string = new byte [string.length];
		
		byte [] or_mask = new byte[8];
		byte [] and_mask = new byte[8];
		
		int mask_value = 1;
		
		for(i = 0; i < 8; i++)
		{
			or_mask[i]  = (byte)mask_value;
			and_mask[i] = (byte)~or_mask[i];
			mask_value *= 2;
		}
		
		
		int bitlength  = 0;
		for(i = 0; i < aggregated_list.size(); i++)
		{
			// Byte offset for destination string.
		    int j = bitlength / 8;
		    // Index for mask.
		    int k = bitlength % 8;
		    
		    ArrayList segment = (ArrayList)aggregated_list.get(i);	
		    int       type    = (int)segment.get(0);
		    
		    if(type == 0)
		        unclipped_string[j] |= or_mask[k];
		    else
		    	unclipped_string[j] &= and_mask[k];
		    bitlength++;
		}
		
		int merged_length = bitlength / 8;
		if(bitlength % 8 != 0)
			merged_length++;
		merged_length++;
		
		
		
		
		
		
		
		byte [] merged_string = new byte[merged_length];
		
		
		return merged_string;
	}
	
	public static ArrayList getMergedSegmentedData(byte[] string, int minimum_bitlength) 
	{
		ArrayList list = new ArrayList();
		
		// For the merging to work without any bit masking and shifting, 
		// the minimum segment bit length has to be a multiple of 8.
		// Open question if this constraint significantly affects rate of compression.
		if(minimum_bitlength % 8 != 0)
		{
			// Return an empty list.
		    System.out.println("Minimum segment bitlength must be a multiple of 8.");
		    return list;
		}
		
		int string_type       = StringMapper.getType(string);
		int string_iterations = StringMapper.getIterations(string);
		int string_bitlength  = StringMapper.getBitlength(string);
		
		//System.out.println("Original string length is " + string_bitlength);
		
		int number_of_segments = string_bitlength / minimum_bitlength;
		int segment_bitlength  = minimum_bitlength;
		int segment_bytelength = minimum_bitlength / 8;
		segment_bytelength++;
		
		int remainder                = string_bitlength % minimum_bitlength;
		int last_segment_bitlength   = minimum_bitlength + remainder;
		int last_segment_bytelength  = last_segment_bitlength / 8;
		// The last segment is the only one that might have odd bits and need an extra byte.
		if (remainder % 8 != 0)
			last_segment_bytelength++;
		last_segment_bytelength++;
		
		// To start off, the last segment byte length is always greater than or equal to the 
		// maximum byte length.
		int max_segment_bytelength = last_segment_bytelength;
		
		// Get an initial list of uncompressed segments of equal length, except possibly the last segment.
		// Set iterations to 16 or 0, depending on the type.  Extra bits will be 0, except for possibly
		// the last segment.
		ArrayList segment_list = new ArrayList();
		
		for(int i = 0; i < number_of_segments; i++)
		{
		    if(i < number_of_segments - 1)
		    {
		    	byte [] segment = new byte[segment_bytelength];
		    	for(int j = 0; j < segment.length - 1; j++)
					segment[j] = string[i * (segment_bytelength - 1) + j];
		    	
		    	double zero_ratio = StringMapper.getZeroRatio(segment, segment_bitlength);
		    	byte iterations = 0;
				if (zero_ratio < .5)
					iterations = 16;
				segment[segment.length - 1] = iterations;
				segment_list.add(segment);
		    }	
		    else 
		    {
		    	byte [] segment = new byte[last_segment_bytelength];   
		    	for(int j = 0; j < segment.length - 1; j++)
					segment[j] = string[i * (segment_bytelength - 1) + j];
		    	
		    	byte extra_bits = 0;
				if(remainder != 0)
				{
					extra_bits = (byte)(8 - remainder);
				    extra_bits <<= 5;
				}
		    	segment[segment.length - 1] = extra_bits;
		    	
		    	double zero_ratio = StringMapper.getZeroRatio(segment, last_segment_bitlength);
		    	byte iterations = 0;
				if (zero_ratio < .5)
					iterations = 16;
				segment[segment.length - 1] |= iterations;
				segment_list.add(segment);
		    }
		}
		
		// Compress the strings, if possible.
		for(int i = 0; i < number_of_segments; i++)
		{
			byte [] segment = (byte [])segment_list.get(i);
			int type = StringMapper.getType(segment);
			if(type == 0)
			{
				byte [] compressed_segment = StringMapper.compressZeroStrings(segment);
				if(compressed_segment.length < segment.length)
					segment_list.set(i, compressed_segment);
			}
			else
			{
				byte [] compressed_segment = StringMapper.compressOneStrings(segment);
				if(compressed_segment.length < segment.length)
					segment_list.set(i, compressed_segment);

			}
		}
		//System.out.println("Finished constructing initial list.");
		
		
		// The int for the segment byte length and the trailing byte with segment information
		// add up to 40 bits when the segment byte length requires an int.
		// As the number of segments increase and the segment lengths decrease, the int can
		// be replaced by a short or byte, so the overhead for smaller segments could get as
		// low as 16.
		int overhead = 40;
		if(max_segment_bytelength <= Byte.MAX_VALUE * 2 + 1)
		    overhead = 16;
		else if(max_segment_bytelength <= Short.MAX_VALUE * 2 + 1)
			overhead = 24;
		else 
			overhead = 40;
	
		int current_number_of_segments = number_of_segments;
		int previous_number_of_segments = 0;
		
		int iterations = 0; 
		ArrayList merged_list = new ArrayList();
		
		while(current_number_of_segments != previous_number_of_segments)
		{
		    iterations++;
		    previous_number_of_segments = current_number_of_segments;
		    merged_list.clear();
		    
			for(int i = 0; i < current_number_of_segments; i++) 
			{
				byte [] current_segment = (byte[])segment_list.get(i);
				if(i < current_number_of_segments - 1)
				{
				    byte [] next_segment   = (byte[])segment_list.get(i + 1);
				    int current_iterations = StringMapper.getIterations(current_segment);
				    int next_iterations    = StringMapper.getIterations(next_segment);
				    
				    // The current segment and the next segment should have
			    	// similar zero ratios if they have the same number of iterations.
				    if(current_iterations == next_iterations)
				    {
				    	// Get the combined bit length of the two segments to compare
				        // with the merged bit length minus overhead for one segment.
				        int current_bitlength   = StringMapper.getBitlength(current_segment);
				        int next_bitlength      = StringMapper.getBitlength(next_segment);
				        int combined_bitlength  = current_bitlength + next_bitlength;
				        
				        if(current_iterations == 0 || current_iterations == 16)
				        {
				            int bytelength         = current_segment.length + next_segment.length - 1;
				            byte [] merged_segment = new byte[bytelength];
				    	    for(int k = 0; k < current_segment.length - 1; k++)
				    	    	merged_segment[k] = current_segment[k];
				    	    int offset = current_segment.length - 1;
				    	    for(int k = 0; k < next_segment.length - 1; k++)
				    	    	merged_segment[k + offset] = next_segment[k];
				    	    int modulus = combined_bitlength % 8;
				    	    if(modulus != 0)
				    	    {
				    	    	byte extra_bits = (byte)(8 - modulus);
				    	    	extra_bits    <<= 5;
				    	    	merged_segment[merged_segment.length - 1] = extra_bits;
				    	    }
				    	    merged_segment[merged_segment.length - 1] |= current_iterations;
				    	    merged_list.add(merged_segment);
				    	    i++;
				        }
				        else
				        {
				        	byte [] decompressed_current_segment = current_segment;
				    		byte [] decompressed_next_segment    = next_segment;   	
				    		if(current_iterations < 16)
				    		{
				    			decompressed_current_segment = StringMapper.decompressZeroStrings(current_segment);
				    	        decompressed_next_segment    = StringMapper.decompressZeroStrings(next_segment);   	
				    		}
				    		else
				    		{
				    			decompressed_current_segment = StringMapper.decompressOneStrings(current_segment);
				    	        decompressed_next_segment    = StringMapper.decompressOneStrings(next_segment);	
				    		}
				    		
				    		int decompressed_current_bitlength  = StringMapper.getBitlength(decompressed_current_segment);
			    	        int decompressed_next_bitlength     = StringMapper.getBitlength(decompressed_next_segment); 
				    		int decompressed_combined_bitlength = decompressed_current_bitlength + decompressed_next_bitlength;
				            
				            int bytelength = decompressed_current_segment.length + decompressed_next_segment.length - 1;
				            byte [] uncompressed_merged_segment = new byte[bytelength];
				            
				            for(int k = 0; k < decompressed_current_segment.length - 1; k++)
				    	    	uncompressed_merged_segment[k] = decompressed_current_segment[k];
				    	    int offset = decompressed_current_segment.length - 1;
				    	    for(int k = 0; k < decompressed_next_segment.length - 1; k++)
				    	    	uncompressed_merged_segment[k + offset] = decompressed_next_segment[k];
				    	    
				    	    int modulus = decompressed_combined_bitlength % 8;
				    	    if(modulus != 0)
				    	    {
				    	    	byte extra_bits = (byte)(8 - modulus);
				    	    	extra_bits    <<= 5;
				    	    	uncompressed_merged_segment[uncompressed_merged_segment.length - 1] = extra_bits;
				    	    	System.out.println("Segments "  + i + " and " + (i + 1) + " combined are uneven");
				    	    }
				    	    
				    	    byte [] merged_segment = new byte[1];
				    	    if(current_iterations > 15)
				    	    {
				    	        uncompressed_merged_segment[uncompressed_merged_segment.length - 1] |= 16;
				    	        merged_segment = StringMapper.compressOneStrings(uncompressed_merged_segment);
				    	    }
				    	    else
				    	    	merged_segment = StringMapper.compressZeroStrings(uncompressed_merged_segment);	
				    	    
				    	    int merged_bitlength = StringMapper.getBitlength(merged_segment);
				    	    if(merged_bitlength - overhead < combined_bitlength)
				    	    {
				    	    	merged_list.add(merged_segment);
					    	    i++;	
				    	    }
				    	    else
				    	    	merged_list.add(current_segment); 
				        }
				    }
				    else
				    	merged_list.add(current_segment);	
			    }
				else if(i == current_number_of_segments - 1)
				    merged_list.add(current_segment);	
		    }
			
		    current_number_of_segments = merged_list.size();
		    if(merged_list.size() < segment_list.size())
		    {
			    segment_list.clear();
			    for(int i = 0; i < merged_list.size(); i++)
			    {
			        byte [] current_segment = (byte [])	merged_list.get(i); 
			        if(current_segment.length > max_segment_bytelength)
		        		max_segment_bytelength = current_segment.length;
			        segment_list.add(current_segment);
			    }
			    if(max_segment_bytelength <= Byte.MAX_VALUE * 2 + 1)
				    overhead = 16;
				else if(max_segment_bytelength <= Short.MAX_VALUE * 2 + 1)
					overhead = 24;
				else 
					overhead = 40;
		    }  
		}
		
		list.add(segment_list);
		list.add(max_segment_bytelength);
		
		return list;
	}

	
	// This version of the function does a lot of error checking.
	public static ArrayList getMergedSegmentedData2(byte[] string, int minimum_bitlength) 
	{
		ArrayList list = new ArrayList();
		// For the merging to work without any bit masking and shifting, 
		// the minimum segment bit length has to be a multiple of 8.
		// Open question if this constraint significantly affects rate of compression, but seems unlikely.
		if(minimum_bitlength % 8 != 0)
		{
			// Return an empty list.
		    System.out.println("Minimum segment bitlength must be a multiple of 8.");
		    return list;
		}
		
		int string_type       = StringMapper.getType(string);
		int string_iterations = StringMapper.getIterations(string);
		int string_bitlength  = StringMapper.getBitlength(string);
		
		//System.out.println("Original string length is " + string_bitlength);
		
		int number_of_segments = string_bitlength / minimum_bitlength;
		int segment_bitlength  = minimum_bitlength;
		int segment_bytelength = minimum_bitlength / 8;
		segment_bytelength++;
		
		int remainder                = string_bitlength % minimum_bitlength;
		int last_segment_bitlength   = minimum_bitlength + remainder;
		int last_segment_bytelength  = last_segment_bitlength / 8;
		// The last segment is the only one that might have odd bits and need an extra byte.
		if (remainder % 8 != 0)
			last_segment_bytelength++;
		last_segment_bytelength++;
		
		// To start off, the last segment byte length is always greater than or equal to the 
		// maximum byte length.
		int max_segment_bytelength = last_segment_bytelength;
		
		// Get an initial list of uncompressed segments of equal length, except possibly the last segment.
		// Set iterations to 16 or 0, depending on the type.  Extra bits will be 0, except for possibly
		// the last segment.
		ArrayList segment_list = new ArrayList();
		byte [] reconstructed_string = new byte[string.length];
		
		for(int i = 0; i < number_of_segments; i++)
		{
		    if(i < number_of_segments - 1)
		    {
		    	byte [] segment = new byte[segment_bytelength];
		    	for(int j = 0; j < segment.length - 1; j++)
					segment[j] = string[i * (segment_bytelength - 1) + j];
		    	
		    	double zero_ratio = StringMapper.getZeroRatio(segment, segment_bitlength);
		    	byte iterations = 0;
				if (zero_ratio < .5)
					iterations = 16;
				segment[segment.length - 1] = iterations;
				segment_list.add(segment);
		    }	
		    else 
		    {
		    	byte [] segment = new byte[last_segment_bytelength];   
		    	for(int j = 0; j < segment.length - 1; j++)
					segment[j] = string[i * (segment_bytelength - 1) + j];
		    	
		    	byte extra_bits = 0;
				if(remainder != 0)
				{
					extra_bits = (byte)(8 - remainder);
				    extra_bits <<= 5;
				}
		    	segment[segment.length - 1] = extra_bits;
		    	
		    	double zero_ratio = StringMapper.getZeroRatio(segment, last_segment_bitlength);
		    	byte iterations = 0;
				if (zero_ratio < .5)
					iterations = 16;
				segment[segment.length - 1] |= iterations;
				segment_list.add(segment);
		    }
		}
		
		int offset = 0;
		for(int i = 0; i < number_of_segments; i++)
		{
			byte [] segment = (byte [])segment_list.get(i);
			for(int j = 0; j < segment.length - 1; j++)
				reconstructed_string[offset + j] = segment[j];	
			offset += segment_bytelength - 1;
		}
		
		if(string_bitlength % 8 != 0)
		{
			int modulus = string_bitlength % 8;
			byte extra_bits = (byte)(8 - modulus);
			extra_bits <<= 5;
			reconstructed_string[string.length - 1] = extra_bits;
		}
		reconstructed_string[string.length - 1] |= string_iterations;
		
		boolean same = true;
		for(int i = 0; i < string.length; i++)
		{
			if(string[i] != reconstructed_string[i])
			{
				System.out.println("Reconstructed string differs from original string at index " + i);
				same = false;
				break;
			}
		}
		
		// Compress the strings, if possible.
		for(int i = 0; i < number_of_segments; i++)
		{
			byte [] segment = (byte [])segment_list.get(i);
			int type = StringMapper.getType(segment);
			if(type == 0)
			{
				byte [] compressed_segment = StringMapper.compressZeroStrings(segment);
				if(compressed_segment.length < segment.length)
				{
					segment_list.set(i, compressed_segment);
					
					// Test if decompression works.
					byte [] decompressed_segment = StringMapper.decompressZeroStrings(segment);
					if(decompressed_segment.length != segment.length)
					{
						System.out.println("Decompressed segment length different from original length at segment index " + i);
					}
					else
					{
						for(int j = 0; j < segment.length; j++)
						{
							if(segment[j] != decompressed_segment[j])
							{
								System.out.println("Decompressed segment differs from original segment at byte index " + j);
								break;
							}
						}
					}
				}
			}
			else
			{
				byte [] compressed_segment = StringMapper.compressOneStrings(segment);
				if(compressed_segment.length < segment.length)
				{
					segment_list.set(i, compressed_segment);
					
					// Test if decompression works.
					byte [] decompressed_segment = StringMapper.decompressOneStrings(segment);
					if(decompressed_segment.length != segment.length)
					{
						System.out.println("Decompressed segment length different from original length at segment index " + i);
					}
					else
					{
						for(int j = 0; j < segment.length; j++)
						{
							if(segment[j] != decompressed_segment[j])
							{
								System.out.println("Decompressed segment differs from original segment at byte index " + j);
								break;
							}
						}
					}
				}
			}
		}
		//System.out.println("Finished constructing initial list.");
		// Finished constructing initial list.
		
		
		// The int for the segment byte length and the trailing byte with segment information
		// add up to 40 bits.
		// As the number of segments increase and the segment lengths decrease, the int might
		// be replaced by a short or byte, so the overhead for smaller segments could get as
		// low as 16.
		int overhead = 40;
		int current_number_of_segments = number_of_segments;
		int previous_number_of_segments = 0;
		
		int iterations = 0; 
		ArrayList merged_list = new ArrayList();
		
		while(current_number_of_segments != previous_number_of_segments)
		{
		    iterations++;
		    previous_number_of_segments = current_number_of_segments;
		    merged_list.clear();
		    
			int number_of_zero_segments         = 0;
			int number_of_one_segments          = 0;
			int number_of_uncompressed_segments = 0;
			int number_of_compressed_segments   = 0;
			
			//System.out.println("Starting merge.");
			
			for(int i = 0; i < current_number_of_segments; i++) 
			{
				//System.out.println();
				//System.out.println("Number of segments is " + current_number_of_segments);
				byte [] current_segment = (byte[])segment_list.get(i);
				if(i < current_number_of_segments - 1)
				{
				    byte [] next_segment    = (byte[])segment_list.get(i + 1);
				    
				    int current_iterations = StringMapper.getIterations(current_segment);
				    int next_iterations    = StringMapper.getIterations(next_segment);
				    
				    // The current segment and the next segment have the same type
			    	// and similar zero ratios if they have the same number of iterations.
				    if(current_iterations == next_iterations)
				    {
				    	// Get the combined bit length of the two segments to compare
				        // with the merged bit length minus overhead.
				        int current_bitlength   = StringMapper.getBitlength(current_segment);
				        int next_bitlength      = StringMapper.getBitlength(next_segment);
				        int combined_bitlength  = current_bitlength + next_bitlength;
				        
				        if(current_iterations == 0 || current_iterations == 16)
				        {
				            int bytelength         = current_segment.length + next_segment.length - 1;
				            byte [] merged_segment = new byte[bytelength];
				            

				    	    for(int k = 0; k < current_segment.length - 1; k++)
				    	    	merged_segment[k] = current_segment[k];
				    	    offset = current_segment.length - 1;
				    	    for(int k = 0; k < next_segment.length - 1; k++)
				    	    	merged_segment[k + offset] = next_segment[k];
				    	    int modulus = combined_bitlength % 8;
				    	    if(modulus != 0)
				    	    {
				    	    	byte extra_bits = (byte)(8 - modulus);
				    	    	extra_bits    <<= 5;
				    	    	merged_segment[merged_segment.length - 1] = extra_bits;
				    	    }
				    	    
				    	    merged_segment[merged_segment.length - 1] |= current_iterations;
				    	    merged_list.add(merged_segment);
				    	    i++;
				        }
				        else
				        {
				        	byte [] decompressed_current_segment = current_segment;
				    		byte [] decompressed_next_segment    = next_segment;   	
				    		if(current_iterations < 16)
				    		{
				    			decompressed_current_segment = StringMapper.decompressZeroStrings(current_segment);
				    	        decompressed_next_segment    = StringMapper.decompressZeroStrings(next_segment);   	
				    		}
				    		else
				    		{
				    			decompressed_current_segment = StringMapper.decompressOneStrings(current_segment);
				    	        decompressed_next_segment    = StringMapper.decompressOneStrings(next_segment);	
				    		}
				    		
				    		int decompressed_current_bitlength  = StringMapper.getBitlength(decompressed_current_segment);
			    	        int decompressed_next_bitlength     = StringMapper.getBitlength(decompressed_next_segment); 
				    		int decompressed_combined_bitlength = decompressed_current_bitlength + decompressed_next_bitlength;
				            
				            int bytelength = decompressed_current_segment.length + decompressed_next_segment.length - 1;
				            byte [] uncompressed_merged_segment = new byte[bytelength];
				            
				            for(int k = 0; k < decompressed_current_segment.length - 1; k++)
				    	    	uncompressed_merged_segment[k] = decompressed_current_segment[k];
				    	    offset = decompressed_current_segment.length - 1;
				    	    for(int k = 0; k < decompressed_next_segment.length - 1; k++)
				    	    	uncompressed_merged_segment[k + offset] = decompressed_next_segment[k];
				    	    
				    	    int modulus = decompressed_combined_bitlength % 8;
				    	    if(modulus != 0)
				    	    {
				    	    	byte extra_bits = (byte)(8 - modulus);
				    	    	extra_bits    <<= 5;
				    	    	uncompressed_merged_segment[uncompressed_merged_segment.length - 1] = extra_bits;
				    	    	System.out.println("Segments "  + i + " and " + (i + 1) + " combined are uneven");
				    	    }
				    	    
				    	    byte [] merged_segment = new byte[1];
				    	    if(current_iterations > 15)
				    	    {
				    	        uncompressed_merged_segment[uncompressed_merged_segment.length - 1] |= 16;
				    	        merged_segment = StringMapper.compressOneStrings(uncompressed_merged_segment);
				    	    }
				    	    else
				    	    	merged_segment = StringMapper.compressZeroStrings(uncompressed_merged_segment);	
				    	    
				    	    int merged_bitlength = StringMapper.getBitlength(merged_segment);
				    	    if(merged_bitlength - 40 < combined_bitlength)
				    	    {
				    	    	merged_list.add(merged_segment);
					    	    i++;	
				    	    }
				    	    else
				    	    {
				    	    	merged_list.add(current_segment); 
				    	    }
				        }
				    }
				    else
				    {
				    	merged_list.add(current_segment);	
				    }
			    }
				else if(i == current_number_of_segments - 1)
			    {
				    merged_list.add(current_segment);	
			    }
		    }
		  
			//System.out.println("Finished merge.");
			
			// Scan merged list.
			
			int segmented_bitlength = 0;
		    current_number_of_segments = merged_list.size();
		    if(merged_list.size() < segment_list.size())
		    {
			    segment_list.clear();
			    for(int i = 0; i < merged_list.size(); i++)
			    {
			        byte [] current_segment = (byte [])	merged_list.get(i); 
			        if(current_segment.length > max_segment_bytelength)
		        		max_segment_bytelength = current_segment.length;
			        int current_iterations = StringMapper.getIterations(current_segment);
			        if(current_iterations == 0 || current_iterations == 16)
			        {
			        	int current_bitlength = StringMapper.getBitlength(current_segment);
			        	segmented_bitlength += current_bitlength;
			        	segment_list.add(current_segment);
			        		
			        }
			        else
			        {
			        	if(current_iterations < 16)
			    		{
			    			byte [] decompressed_current_segment = StringMapper.decompressZeroStrings(current_segment);
			    			int current_bitlength = StringMapper.getBitlength(decompressed_current_segment);
			    			segmented_bitlength += current_bitlength;
			    			segment_list.add(current_segment);
			    		}
			    		else
			    		{
			    			byte [] decompressed_current_segment = StringMapper.decompressOneStrings(current_segment);
			    			int current_bitlength = StringMapper.getBitlength(decompressed_current_segment);
			    			segmented_bitlength += current_bitlength;
			    			segment_list.add(current_segment);
			    		}
			        }
			        
			    }
			    //System.out.println("Scanned merge list.");
			    //System.out.println("Segmented bitlength is " + segmented_bitlength);
		    }
		    //System.out.println();    
		}
		
		ArrayList decompressed_segments = new ArrayList();
		
		int segmented_bitlength = 0;
		for(int i = 0; i < segment_list.size(); i++)
	    {
	        byte [] current_segment = (byte [])segment_list.get(i); 
	        
	        int current_iterations = StringMapper.getIterations(current_segment);
	        if(current_iterations == 0 || current_iterations == 16)
	        {
	        	int current_bitlength = StringMapper.getBitlength(current_segment);
	        	segmented_bitlength += current_bitlength;
	        	decompressed_segments.add(current_segment);
	        }
	        else
	        {
	        	if(current_iterations < 16)
	    		{
	    			byte [] decompressed_current_segment = StringMapper.decompressZeroStrings(current_segment);
	    			int current_bitlength = StringMapper.getBitlength(decompressed_current_segment);
	    			segmented_bitlength += current_bitlength;
	    			decompressed_segments.add(decompressed_current_segment);
	    		}
	    		else
	    		{
	    			byte [] decompressed_current_segment = StringMapper.decompressOneStrings(current_segment);
	    			int current_bitlength = StringMapper.getBitlength(decompressed_current_segment);
	    			segmented_bitlength += current_bitlength;
	    			decompressed_segments.add(decompressed_current_segment);
	    		}
	        }
	    }
		
		//System.out.println("Scanned final segment list.");
	    //System.out.println("Segmented bitlength is " + segmented_bitlength);
	    
		/*
		int bytelength = segmented_bitlength / 8;
		// Add byte for odd bits.
		if(segmented_bitlength % 8 != 0)
			bytelength++;
		// Add byte for string information.
		bytelength++;
		
		byte [] reconstructed_string = new byte[bytelength];
		
		if(reconstructed_string.length != string.length)
		{
			System.out.println("Reconstructed string length different from original string length.");
		}
		else
		{
			int offset = 0;
			for(int i = 0; i < decompressed_segments.size(); i++)
			{
			    byte [] current_segment = (byte [])decompressed_segments.get(i);
			    for(int j = 0; j < current_segment.length - 1; j++)
			    	reconstructed_string[offset + j] = current_segment[j];
			    offset                          += current_segment.length - 1;
			}
			
			if(string_bitlength % 8 != 0)
			{
				int modulus = string_bitlength % 8;
				byte extra_bits = (byte) (8 - modulus);
				extra_bits <<= 5;
				reconstructed_string[string.length - 1] = extra_bits;
			}
			reconstructed_string[string.length - 1] |= string_iterations;
			
			boolean same = true;
			for(int i = 0; i < string.length; i++)
			{
				if(string[i] != reconstructed_string[i])
				{
					System.out.println("Reconstructed string differs from original string at index " + i);
					same = false;
					break;
				}
			}
			
			if(same)
				System.out.println("Original string and reconstructed string are the same.");
			else
				System.out.println("Original string and reconstructed string are not the same.");
				
		}
		*/
		
		
		list.add(segment_list);
		list.add(max_segment_bytelength);
		
		return list;
	}

	public static ArrayList getMergedSegmentedData(byte[] string, int string_bit_length, int segment_bit_length) 
	{
		int number_of_segments  = string_bit_length / segment_bit_length;
		int segment_byte_length = segment_bit_length / 8;
		int remainder           = string_bit_length % segment_bit_length;
		int last_segment_bit_length  = segment_bit_length + remainder;
		int last_segment_byte_length = segment_byte_length + remainder / 8;
		if (remainder % 8 != 0)
			last_segment_byte_length++;
		
		int max_byte_value  = Byte.MAX_VALUE * 2 + 1;
		int max_short_value = Short.MAX_VALUE * 2 + 1;
		

		int max_segment_byte_length = last_segment_byte_length;

		ArrayList segment_length = new ArrayList();
		ArrayList segment_type = new ArrayList();
		ArrayList compressed_length = new ArrayList();
		ArrayList compressed_data = new ArrayList();

		int current_byte_length = segment_byte_length;
		int current_bit_length = segment_bit_length;
		byte[] segment = new byte[current_byte_length];
		byte[] compressed_segment = new byte[2 * current_byte_length];

		for (int i = 0; i < number_of_segments; i++) 
		{
			if (i == number_of_segments - 1) 
			{
				current_byte_length = last_segment_byte_length;
				current_bit_length = last_segment_bit_length;
				segment = new byte[current_byte_length];
				compressed_segment = new byte[2 * current_byte_length];
			}
			for (int j = 0; j < current_byte_length; j++)
				segment[j] = string[i * segment_byte_length + j];

			double zero_ratio = StringMapper.getZeroRatio(segment, current_bit_length);
			int string_type = 0;
			if (zero_ratio < .5)
				string_type = 1;
			segment_type.add(string_type);
			segment_length.add(current_bit_length);

			int compression_amount = 0;
			if (string_type == 0)
				compression_amount = StringMapper.getCompressionAmount(segment, current_bit_length, 0);
			else
				compression_amount = StringMapper.getCompressionAmount(segment, current_bit_length, 1);
			if (compression_amount >= 0) 
			{
				byte[] clipped_string = new byte[current_byte_length + 1];
				for (int j = 0; j < current_byte_length; j++)
					clipped_string[j] = segment[j];
				clipped_string[current_byte_length] = 0;
				compressed_length.add(current_bit_length);
				compressed_data.add(clipped_string);
				
				byte extra_bits = (byte)(current_bit_length % 8);
				if(extra_bits != 0)
					extra_bits = (byte)(8 - extra_bits);
				extra_bits <<= 5;
				clipped_string[current_byte_length] = extra_bits;
				
			} 
			else 
			{
				byte [] compressed_string = new byte[1];
				if(string_type == 0)
					compressed_string = StringMapper.compressZeroStrings(segment);
				else
					compressed_string = StringMapper.compressOneStrings(segment);
					
				
				compressed_data.add(compressed_string);
				int compressed_bit_length = StringMapper.getBitlength(compressed_string);
				compressed_length.add(compressed_bit_length);
			}
		}

		// Finished constructing initial list.
       
		
		// Check the byte at the end of each segment to see if it matches the list.
		// Not necessary but good regression testing.
		for(int i = 0; i < compressed_data.size(); i++)
		{
			byte [] current_segment = (byte [])compressed_data.get(i);
			int current_length      = (int)compressed_length.get(i);
			byte last_byte          = current_segment[current_segment.length - 1];
			byte extra_bits         = (byte)(last_byte >> 5);
		    extra_bits             &= 7;
		    int bit_length          = (current_segment.length - 1) * 8 - extra_bits;
		    byte iterations         = (byte)(last_byte & 31);
		    int string_type         = 0;
		    if(iterations > 15)
		    	iterations -= 16;
		    if(current_length != bit_length)
		    {
		    	System.out.println("Length from list " + current_length + " is not equal to length from byte " + bit_length + " at index " + i + " in segment mapper.");
		    	System.out.println("Iterations is " + iterations + " and string type is " + string_type);
		    	System.out.println();
		    }
		}
	
		
		// Merging segments.
      
		int overhead = 0;
		if(max_segment_byte_length <= max_byte_value)
			overhead = 16;
		else if(max_segment_byte_length <= max_short_value)
			overhead = 24;
		else
			overhead = 40;
		
		ArrayList previous_segment_type      = (ArrayList) segment_type.clone();
		ArrayList previous_segment_length    = (ArrayList) segment_length.clone();
		ArrayList previous_compressed_length = (ArrayList) compressed_length.clone();
		ArrayList previous_compressed_data   = (ArrayList) compressed_data.clone();

		ArrayList current_segment_type      = new ArrayList();
		ArrayList current_segment_length    = new ArrayList();
		ArrayList current_compressed_length = new ArrayList();
		ArrayList current_compressed_data   = new ArrayList();

		int previous_number_of_segments = 0;
		int current_number_of_segments  = compressed_data.size();
		int initial_number_of_segments  = current_number_of_segments;
		int last_bit_length             = (int)segment_length.get(current_number_of_segments - 1);
	
		int iterations                      = 0;
		int number_of_zero_segments         = 0;
		int number_of_one_segments          = 0;
		int number_of_uncompressed_segments = 0;
		int number_of_compressed_segments   = 0;
		
		
		while(current_number_of_segments != previous_number_of_segments) 
		{
			iterations++;
			previous_number_of_segments     = previous_compressed_data.size();
			number_of_zero_segments         = 0;
			number_of_one_segments          = 0;
			number_of_uncompressed_segments = 0;
			number_of_compressed_segments   = 0;
			int current_offset              = 0;
			
			
			for(int i = 0; i < previous_number_of_segments; i++) 
			{
				int current_iterations = 0;
				int next_iterations    = 0;
				if(i < previous_number_of_segments - 1) 
				{
					//int current_string_type = (int) previous_segment_type.get(i);
					//int next_string_type = (int) previous_segment_type.get(i + 1);
					int current_uncompressed_length = (int) previous_segment_length.get(i);
					int next_uncompressed_length    = (int) previous_segment_length.get(i + 1);
					int current_length = (int) previous_compressed_length.get(i);
					int next_length = (int) previous_compressed_length.get(i + 1);
					byte[] current_string = (byte[]) previous_compressed_data.get(i);
					byte[] next_string = (byte[]) previous_compressed_data.get(i + 1);
					
					current_iterations = current_string[current_string.length - 1] & 31;
					int current_string_type = 0;
					if(current_iterations > 15)
					{
					    current_iterations -= 16;
					    current_string_type = 1;
					}
					
					next_iterations = next_string[next_string.length - 1] & 31;
					int next_string_type = 0;
					if(next_iterations > 15)
					{
					    next_iterations -= 16;
					    next_string_type = 1;
					}
                    
					if(current_string_type == 1)
						number_of_one_segments++;
					else
						number_of_zero_segments++;
					if(current_string_type == next_string_type && current_iterations == next_iterations)
				    //if(current_string_type == next_string_type && current_iterations > 0 && next_iterations > 0) 	
					{
						number_of_compressed_segments++;
						int merged_uncompressed_length = current_uncompressed_length + next_uncompressed_length;

						int byte_offset = current_offset / 8;
						int byte_length = merged_uncompressed_length / 8;
						if(merged_uncompressed_length % 8 != 0)
							byte_length++;
						

						byte[] merged_segment = new byte[byte_length];
						for (int j = 0; j < byte_length; j++)
							merged_segment[j] = string[j + byte_offset];
						byte[] compressed_merged_segment = new byte[1];
						if(current_string_type == 0)
							compressed_merged_segment = StringMapper.compressZeroStrings(merged_segment);
						else
							compressed_merged_segment = StringMapper.compressOneStrings(merged_segment);
						
						int merged_compression_length = StringMapper.getBitlength(merged_segment);
						
                        // Do a check to see if the segments compress better when merged.
						// We also account for the overhead--either a byte,
						// a short, or an int.
						if(merged_compression_length - overhead <= (current_length + next_length)) 
						{
							int compressed_byte_length = merged_compression_length / 8;
							if (merged_compression_length % 8 != 0)
								compressed_byte_length++;
							compressed_byte_length++;
							byte[] segment_string = new byte[compressed_byte_length];
							for (int j = 0; j < compressed_byte_length; j++)
								segment_string[j] = compressed_merged_segment[j];
							current_iterations = current_string[current_string.length - 1] & 31;
							if(current_iterations > 15)
								current_string_type = 1;
							else
								current_string_type = 0;
							current_segment_type.add(current_string_type);
							current_segment_length.add(merged_uncompressed_length);
							current_compressed_length.add(merged_compression_length);
							current_compressed_data.add(segment_string);

							current_offset += merged_uncompressed_length;

							i++;
							//System.out.println("Merged segments.");
							
							if (compressed_byte_length > max_segment_byte_length)
							{
								max_segment_byte_length = compressed_byte_length;
								if(max_segment_byte_length <= max_byte_value)
									overhead = 16;
								else if(max_segment_byte_length <= max_short_value)
									overhead = 24;
								else
									overhead = 40;
							}
						} 
						else 
						{
							//System.out.println("Segments of same string type compress better separately.");
							current_segment_type.add(current_string_type);
							current_segment_length.add(current_uncompressed_length);
							current_compressed_length.add(current_length);
							current_compressed_data.add(current_string);
							current_offset += current_uncompressed_length;
						}
					} 
					else if(current_iterations == 0 && next_iterations == 0)
					{
						number_of_uncompressed_segments++;
						int merged_length = current_length + next_length;
						int merged_uncompressed_length = current_uncompressed_length + next_uncompressed_length;
						int byte_offset = current_offset / 8;
						int byte_length = merged_uncompressed_length / 8;
						if (merged_uncompressed_length % 8 != 0) 
						{
							byte_length++;
							//System.out.println("Uneven segment at index " + (i + 1));
							//System.out.println("Current number of segments is " + previous_number_of_segments);
							//System.out.println("Current iteration is " + iterations);
						}

						if(byte_length + 1 > max_segment_byte_length)
							max_segment_byte_length = byte_length + 1;
						if(max_segment_byte_length <= max_byte_value)
							overhead = 16;
						else if(max_segment_byte_length <= max_short_value)
							overhead = 24;
						else
							overhead = 40;

						byte[] merged_segment = new byte[byte_length + 1];

						for (int j = 0; j < byte_length; j++)
							merged_segment[j] = string[j + byte_offset];
						merged_segment[byte_length] = 0;

						int merged_type = 0;
						current_iterations = merged_segment[merged_segment.length - 1];
						if(current_iterations < 0)
							merged_type = 1;
						current_segment_type.add(merged_type);
						current_segment_length.add(merged_length);
						current_compressed_length.add(merged_length);
						current_compressed_data.add(merged_segment);

						current_offset += merged_uncompressed_length;
						i++;
					} 
					else 
					{
						current_iterations = current_string[current_string.length - 1];
						if(current_iterations < 0)
						{
							current_string_type = 1;
							current_iterations = -current_iterations;
						}
						else
							current_string_type = 0;
						if(current_iterations == 0)
							number_of_uncompressed_segments++;
						else
							number_of_compressed_segments++;
						current_segment_type.add(current_string_type);
						current_segment_length.add(current_uncompressed_length);
						current_compressed_length.add(current_length);
						current_compressed_data.add(current_string);

						current_offset += current_uncompressed_length;
					}
				} 
				else 
				{
					int current_string_type = (int) previous_segment_type.get(i);
					int current_uncompressed_length = (int) previous_segment_length.get(i);
					int current_length = (int) previous_compressed_length.get(i);
					byte[] current_string = (byte[]) previous_compressed_data.get(i);
					current_iterations = current_string[current_string.length - 1];
					if(current_iterations == 0)
						number_of_uncompressed_segments++;
					else
						number_of_compressed_segments++;
					if(current_string_type == 0)
						number_of_zero_segments++;
					else
						number_of_one_segments++;
					current_segment_type.add(current_string_type);
					current_segment_length.add(current_uncompressed_length);
					current_compressed_length.add(current_length);
					current_compressed_data.add(current_string);
				}
			}
			
			
			previous_segment_type = (ArrayList) current_segment_type.clone();
			previous_segment_length = (ArrayList) current_segment_length.clone();
			previous_compressed_length = (ArrayList) current_compressed_length.clone();
			previous_compressed_data = (ArrayList) current_compressed_data.clone();

			current_number_of_segments = current_compressed_data.size();
			current_segment_type.clear();
			current_segment_length.clear();
			current_compressed_length.clear();
			current_compressed_data.clear();
			
			// Reset overhead since we might have some longer segments.
			if(max_segment_byte_length <= max_byte_value)
				overhead = 16;
			else if(max_segment_byte_length <= max_short_value)
				overhead = 24;
			else
				overhead = 40;
		}
		
		
		
		ArrayList segment_data = new ArrayList();
		segment_data.add(previous_compressed_length);
		segment_data.add(previous_compressed_data);
		segment_data.add(max_segment_byte_length);
		return segment_data;
	}

}