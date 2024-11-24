import java.util.*;
import java.util.zip.*;
import java.lang.Math.*;
import java.math.*;

public class SegmentMapper 
{
	public static ArrayList getSegmentedData(byte[] string, int bitlength) 
	{
		ArrayList list = new ArrayList();
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
		
		// Try compressing the segments.
		// The compression string is the same as the original string
		// if it didn't compress.
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
    
	public static ArrayList getMergedSegmentedData(byte[] string, int minimum_bitlength) 
	{
		ArrayList list = new ArrayList();
		
		// For the merging to work without any bit masking and shifting, 
		// the minimum segment bit length has to be a multiple of 8.
		// The optimal compression rate would probably be achieved by starting
		// with a 1 length for zero strings and a 2 length for one strings.
	    // Open question how much this affects the rate of compression.
		// The only segment that might have an odd length is the last one.
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
		if(minimum_bitlength % 8 != 0)
		{
			// Return an empty list.
		    System.out.println("Minimum segment bitlength must be a multiple of 8.");
		    return list;
		}
		
		int string_type       = StringMapper.getType(string);
		int string_iterations = StringMapper.getIterations(string);
		int string_bitlength  = StringMapper.getBitlength(string);
		
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
		
		int bytelength = segmented_bitlength / 8;
		if(segmented_bitlength % 8 != 0)
			bytelength++;
		bytelength++;
		
		reconstructed_string = new byte[bytelength];
		
		if(reconstructed_string.length != string.length)
		{
			System.out.println("Reconstructed string length different from original string length.");
		}
		else
		{
			offset = 0;
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
			
			same = true;
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
		
		list.add(segment_list);
		list.add(max_segment_bytelength);
		return list;
	}
	
	public static byte [] getPositiveMask()
	{
		byte [] mask = new byte[8];
		mask[0] = 1;
		for(int i = 1; i < 8; i++)
			mask[i] = (byte)(mask[i - 1] << 1);
		return mask;
	}
	
	public static byte [] getNegativeMask()
	{
		byte [] mask = new byte[8];
		mask[0] = 1;
		for(int i = 1; i < 8; i++)
			mask[i] = (byte)(mask[i - 1] << 1);
		for(int i = 0; i < 8; i++)
			mask[i] = (byte)(~mask[i]);
		return mask;
	}
	
	public static byte [] getLeadingMask()
	{
		byte [] mask = new byte[7];
		mask[0] = -2;
		for(int i = 1; i < 7; i++)
			mask[i] = (byte)(mask[i - 1] * 2);
		return mask;	
	}
	
	public static byte [] getTrailingMask()
	{
		byte [] mask = new byte[7];
		mask[0] = 1;
		for(int i = 1; i < 7; i++)
			mask[i] = (byte)(mask[i - 1] * 2 + 1);
		return mask;	
	}
}