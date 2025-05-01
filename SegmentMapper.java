import java.util.ArrayList;
/**
* This is a class that segments data, and then merges it back depending on how it affect the compression rate.
* 
* @author Brian Crowley
* @version 1.0
*/
public class SegmentMapper 
{
	/**
	* Segments a byte string and then merges the segments back together,
	* depending on how it affects the rate of compression.
	* We dont parameterize the list because we want to add random types.
	*
	* @param src the input byte string
	* @param minimum_bitlength original segment length
	* @return the segmented/merged byte string
	*/
	public static ArrayList getMergedSegmentedData(byte[] string, int minimum_bitlength) 
	{
		ArrayList list = new ArrayList();
		
		// For the merging to work without any bit masking, 
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
		
		int string_type        = StringMapper.getType(string);
		int string_iterations  = StringMapper.getIterations(string);
		int string_bitlength   = StringMapper.getBitlength(string);
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
				segment[segment.length - 1] = iterations;
				segment_list.add(segment);
		    }
		}
		
		System.out.println("Number of segments is " + segment_list.size());
		ArrayList compressed_segment_list = new ArrayList();
		for(int i = 0; i < number_of_segments; i++)
		{
			byte [] segment = (byte [])segment_list.get(i);
			int type = StringMapper.getType(segment);
			
			if(type == 0)
			{
				byte [] compressed_segment = StringMapper.compressZeroStrings(segment);
				byte [] decompressed_segment = StringMapper.decompressZeroStrings(compressed_segment);
				if(decompressed_segment.length != segment.length)
				{
					System.out.println("Checking zero string compression");
					System.out.println("The original segment length was " + segment.length);
					System.out.println("The decompressed segment length was " + decompressed_segment.length);
					compressed_segment_list.add(segment);	
					int compressed_iterations = StringMapper.getIterations(compressed_segment);
					System.out.println("The number of iterations was " + compressed_iterations);
					System.out.println();
				}
				else
				{
				
					boolean same = true;
		    	    int current_index = 0;
		    	    while(same && current_index < segment.length)
		    	    {
		    	        if(decompressed_segment[current_index] != segment[current_index])	
		    	        {
		    	        	System.out.println("Checking zero string compression");
		    	        	System.out.println("The original segment is not equal to decompressed segment at index " + current_index);
		    	            System.out.println("The original value is " + segment[current_index]);
		    	            System.out.println("The decompressed value is " + decompressed_segment[current_index]);
		    	            System.out.println();
		    	        	same = false;   
		    	        }
		    	        current_index++;
		    	    }
		    	    if(!same)
		    	    	compressed_segment_list.add(segment);
		    	    else
		    	    	compressed_segment_list.add(compressed_segment);
				}
			}
			else
			{
				byte [] compressed_segment = StringMapper.compressOneStrings(segment);
				byte [] decompressed_segment = StringMapper.decompressOneStrings(compressed_segment);
				if(decompressed_segment.length != segment.length)
				{
					System.out.println("Checking one string compression");
					System.out.println("The original segment length was " + segment.length);
					System.out.println("The decompressed segment length was " + decompressed_segment.length);
					compressed_segment_list.add(segment);	
					int compressed_iterations = StringMapper.getIterations(compressed_segment);
					System.out.println("The number of iterations was " + compressed_iterations);
					System.out.println();
				}
				else
				{
				
					boolean same = true;
		    	    int current_index = 0;
		    	    while(same && current_index < segment.length)
		    	    {
		    	        if(decompressed_segment[current_index] != segment[current_index])	
		    	        {
		    	        	System.out.println("Checking one string compression");
		    	        	System.out.println("The original segment is not equal to decompressed segment at index " + current_index);
		    	            System.out.println("The original value is " + segment[current_index]);
		    	            System.out.println("The decompressed value is " + decompressed_segment[current_index]);
		    	            System.out.println();
		    	        	same = false;   
		    	        }
		    	        current_index++;
		    	    }
		    	    if(!same)
		    	    	compressed_segment_list.add(segment);
		    	    else
		    	    	compressed_segment_list.add(compressed_segment);
				}
			}
		}
		
		/*
		list.add(compressed_segment_list);
		list.add(max_segment_bytelength);
		list.add(segment_list);
		return list;
		*/
		
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
	
		int current_number_of_segments = number_of_segments;
		int previous_number_of_segments = 0;
		
		int iterations = 0; 
		ArrayList merged_list = new ArrayList();
		ArrayList input_list  = new ArrayList();
		
		while(current_number_of_segments != previous_number_of_segments)
		{
		    iterations++;
		    previous_number_of_segments = current_number_of_segments;
		    merged_list.clear();
		    input_list.clear();
		    
			for(int i = 0; i < current_number_of_segments; i++) 
			{
				byte [] current_segment = (byte[])compressed_segment_list.get(i);
				if(i < current_number_of_segments - 1)
				{
				    byte [] next_segment   = (byte[])compressed_segment_list.get(i + 1);
				    int current_iterations = StringMapper.getIterations(current_segment);
				    int next_iterations    = StringMapper.getIterations(next_segment);
				    
				    
				    // The current segment and the next segment should have
			    	// similar zero ratios if they have the same number of iterations.
				    
				    //if(current_iterations == next_iterations)
				    if(current_iterations == next_iterations || (current_iterations == 0 && next_iterations == 16) || (current_iterations == 16 && next_iterations == 0))	
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
				    	    input_list.add(merged_segment);
				    	    
				    	    i++;
				        }
				        else
				        {
				        	byte [] decompressed_current_segment = StringMapper.decompressStrings(current_segment);
				        	byte [] decompressed_next_segment    = StringMapper.decompressStrings(next_segment);
				        	
				    		int decompressed_current_bitlength   = StringMapper.getBitlength(decompressed_current_segment);
			    	        int decompressed_next_bitlength      = StringMapper.getBitlength(decompressed_next_segment); 
				    		int decompressed_combined_bitlength  = decompressed_current_bitlength + decompressed_next_bitlength;
				            
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
				    	    	
				    	    	//System.out.println("Segments "  + i + " and " + (i + 1) + " combined are uneven");
				    	    	//System.out.println("Current number of segments is " + current_number_of_segments);
				    	    	//System.out.println();
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
				    	    //if(merged_bitlength - overhead < combined_bitlength)
				    	    if(merged_bitlength - overhead < decompressed_combined_bitlength)
				    	    {
				    	    	merged_list.add(merged_segment);
				    	    	input_list.add(uncompressed_merged_segment);
					    	    i++;	
					    	    
				    	    }
				    	    else
				    	    {
				    	    	merged_list.add(current_segment); 
				    	    	input_list.add(current_segment);
				    	    }
				        }
				    }
				    else
				    {
				    	merged_list.add(current_segment);	
				    	input_list.add(current_segment);
				    }
			    }
				else if(i == current_number_of_segments - 1)
				{
				    merged_list.add(current_segment);
				    input_list.add(current_segment);
				}
		    }
			
		    current_number_of_segments = merged_list.size();
		    if(merged_list.size() < compressed_segment_list.size())
		    {
			    compressed_segment_list.clear();
			    for(int i = 0; i < merged_list.size(); i++)
			    {
			        byte [] current_segment = (byte [])	merged_list.get(i); 
			        if(current_segment.length > max_segment_bytelength)
		        		max_segment_bytelength = current_segment.length;
			        compressed_segment_list.add(current_segment);
			    }
			    if(max_segment_bytelength <= Byte.MAX_VALUE * 2 + 1)
				    overhead = 16;
				else if(max_segment_bytelength <= Short.MAX_VALUE * 2 + 1)
					overhead = 24;
				else 
					overhead = 40;
		    }  
		}
		
		int size = compressed_segment_list.size();
		
		if(size == 1)
		{
			compressed_segment_list.clear();
			input_list.clear();
			compressed_segment_list.add(string);
			input_list.add(string);
		    list.add(compressed_segment_list);
		    list.add(string.length - 1);
		    return list;
		}
		else
		{
			boolean no_compression = true;
			
			for(int i = 0; i < size; i++)
			{
				byte [] current_segment = (byte [])compressed_segment_list.get(i);
				int current_iterations  = StringMapper.getIterations(current_segment);
				if(current_iterations != 0 && current_iterations != 16)
					no_compression = false;
			}
			if(no_compression)
			{
				compressed_segment_list.clear();
				input_list.clear();
			    compressed_segment_list.add(string);
			    input_list.add(string);
			    list.add(compressed_segment_list);
			    list.add(string.length - 1);
			    return list;	
			}
			else
			{
				list.add(compressed_segment_list);
				list.add(max_segment_bytelength);
				return list;
			}
		}
	}
	
	/**
	* Segments a byte string and then merges the segments back together,
	* depending on how it affects the rate of compression.
	* We dont parameterize the list because we want to add random types.
	*
	* @param src the input byte string
	* @param minimum_bitlength original segment length
	* @return the segmented/merged byte string
	*/
	public static ArrayList getMergedSegmentedData2(byte[] string, int minimum_bitlength) 
	{
		ArrayList list = new ArrayList();
		
		// For the merging to work without any bit masking, 
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
		
		int string_type        = StringMapper.getType(string);
		int string_iterations  = StringMapper.getIterations(string);
		int string_bitlength   = StringMapper.getBitlength(string);
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
		
		//System.out.println("Number of segments is " + segment_list.size());
		ArrayList compressed_segment_list = new ArrayList();
		for(int i = 0; i < number_of_segments; i++)
		{
			byte [] segment = (byte [])segment_list.get(i);
			int type = StringMapper.getType(segment);
			
			if(type == 0)
			{
				byte [] compressed_segment = StringMapper.compressZeroStrings(segment);
				byte [] decompressed_segment = StringMapper.decompressZeroStrings(compressed_segment);
				int     one_amount             = StringMapper.getCompressionAmount(compressed_segment, StringMapper.getBitlength(compressed_segment), 1);
				if(one_amount < 0)
				{
					System.out.println("Compressed segment " + i + " with type " + StringMapper.getType(compressed_segment) + " could be reduced by " + one_amount + " with one bitwise transform.");
					System.out.println("The zero ratio of the segment is " + StringMapper.getZeroRatio(compressed_segment, StringMapper.getBitlength(compressed_segment)));
				}
				
				if(decompressed_segment.length != segment.length)
				{
					System.out.println("Checking zero string compression");
					System.out.println("The original segment length was " + segment.length);
					System.out.println("The decompressed segment length was " + decompressed_segment.length);
					compressed_segment_list.add(segment);	
					int compressed_iterations = StringMapper.getIterations(compressed_segment);
					System.out.println("The number of iterations was " + compressed_iterations);
					System.out.println();
				}
				else
				{
				
					boolean same = true;
		    	    int current_index = 0;
		    	    while(same && current_index < segment.length)
		    	    {
		    	        if(decompressed_segment[current_index] != segment[current_index])	
		    	        {
		    	        	System.out.println("Checking zero string compression");
		    	        	System.out.println("The original segment is not equal to decompressed segment at index " + current_index);
		    	            System.out.println("The original value is " + segment[current_index]);
		    	            System.out.println("The decompressed value is " + decompressed_segment[current_index]);
		    	            System.out.println();
		    	        	same = false;   
		    	        }
		    	        current_index++;
		    	    }
		    	    if(!same)
		    	    	compressed_segment_list.add(segment);
		    	    else
		    	    	compressed_segment_list.add(compressed_segment);
				}
			}
			else
			{
				byte [] compressed_segment = StringMapper.compressOneStrings(segment);
				int     zero_amount             = StringMapper.getCompressionAmount(compressed_segment, StringMapper.getBitlength(compressed_segment), 0);
				if(zero_amount < 0)
				{
					System.out.println("Compressed segment " + i + " with type " + StringMapper.getType(compressed_segment) + " could be reduced by " + zero_amount + " with zero bitwise transform.");
					System.out.println("The zero ratio of the segment is " + StringMapper.getZeroRatio(compressed_segment, StringMapper.getBitlength(compressed_segment)));
				}
				byte [] decompressed_segment = StringMapper.decompressOneStrings(compressed_segment);
				if(decompressed_segment.length != segment.length)
				{
					System.out.println("Checking one string compression");
					System.out.println("The original segment length was " + segment.length);
					System.out.println("The decompressed segment length was " + decompressed_segment.length);
					compressed_segment_list.add(segment);	
					int compressed_iterations = StringMapper.getIterations(compressed_segment);
					System.out.println("The number of iterations was " + compressed_iterations);
					System.out.println();
				}
				else
				{
				
					boolean same = true;
		    	    int current_index = 0;
		    	    while(same && current_index < segment.length)
		    	    {
		    	        if(decompressed_segment[current_index] != segment[current_index])	
		    	        {
		    	        	System.out.println("Checking one string compression.");
		    	        	System.out.println("The original segment is not equal to decompressed segment at index " + current_index);
		    	            System.out.println("The original value is " + segment[current_index]);
		    	            System.out.println("The decompressed value is " + decompressed_segment[current_index]);
		    	            System.out.println();
		    	        	same = false;   
		    	        }
		    	        current_index++;
		    	    }
		    	    if(!same)
		    	    	compressed_segment_list.add(segment);
		    	    else
		    	    	compressed_segment_list.add(compressed_segment);
				}
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
	
		int current_number_of_segments = number_of_segments;
		int previous_number_of_segments = 0;
		
		//int iterations = 0; 
		ArrayList merged_list = new ArrayList();
		ArrayList input_list  = new ArrayList();
		
		while(current_number_of_segments != previous_number_of_segments)
		{
		    //iterations++;
		    previous_number_of_segments = current_number_of_segments;
		    merged_list.clear();
		    input_list.clear();
		    
			for(int i = 0; i < current_number_of_segments; i++) 
			{
				byte [] current_segment = (byte[])compressed_segment_list.get(i);
				if(i < current_number_of_segments - 1)
				{
				    byte [] next_segment   = (byte[])compressed_segment_list.get(i + 1);
				    int current_iterations = StringMapper.getIterations(current_segment);
				    int next_iterations    = StringMapper.getIterations(next_segment);
				    
				    
				    // The current segment and the next segment should have
			    	// similar zero ratios if they have the same number of iterations.
				    if(current_iterations == next_iterations || (current_iterations == 0 && next_iterations == 16) || (current_iterations == 16 && next_iterations == 0))
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
				    	    input_list.add(merged_segment);
				    	    
				    	    i++;
				        }
				        else
				        {
				        	byte [] decompressed_current_segment = StringMapper.decompressStrings(current_segment);
				        	byte [] decompressed_next_segment    = StringMapper.decompressStrings(next_segment);
				        	
				    		int decompressed_current_bitlength   = StringMapper.getBitlength(decompressed_current_segment);
			    	        int decompressed_next_bitlength      = StringMapper.getBitlength(decompressed_next_segment); 
				    		int decompressed_combined_bitlength  = decompressed_current_bitlength + decompressed_next_bitlength;
				            
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
				    	    	
				    	    	//System.out.println("Segments "  + i + " and " + (i + 1) + " combined are uneven");
				    	    	//System.out.println("Current number of segments is " + current_number_of_segments);
				    	    	//System.out.println();
				    	    }
				    	    byte [] merged_segment = new byte[1];
				    	    if(current_iterations > 15)
				    	    {
				    	    	uncompressed_merged_segment[uncompressed_merged_segment.length - 1] |= 16;	
				    	    	merged_segment = StringMapper.compressOneStrings(uncompressed_merged_segment);
				    	    	
								int     one_amount             = StringMapper.getCompressionAmount(current_segment, current_bitlength, 1);
								if(one_amount < 0)
								{
									System.out.print("Merged segment " + i + " could be reduced by " + one_amount);
								}
				    	    	
				    	    }
				    	    else
				    	        merged_segment = StringMapper.compressZeroStrings(uncompressed_merged_segment);
				    	    int merged_bitlength = StringMapper.getBitlength(merged_segment);
				    	    //if(merged_bitlength - overhead < combined_bitlength)
				    	    if(merged_bitlength - overhead < decompressed_combined_bitlength)
				    	    {
				    	    	merged_list.add(merged_segment);
				    	    	input_list.add(uncompressed_merged_segment);
					    	    i++;	
					    	 
					    	    
				    	    }
				    	    else
				    	    {
				    	    	merged_list.add(current_segment); 
				    	    	input_list.add(current_segment);
				    	    }
				        }
				    }
				    else
				    {
				    	merged_list.add(current_segment);	
				    	input_list.add(current_segment);
				    }
			    }
				else if(i == current_number_of_segments - 1)
				{
				    merged_list.add(current_segment);
				    input_list.add(current_segment);
				}
		    }
			
		    current_number_of_segments = merged_list.size();
		    if(merged_list.size() < compressed_segment_list.size())
		    {
			    compressed_segment_list.clear();
			    for(int i = 0; i < merged_list.size(); i++)
			    {
			        byte [] current_segment = (byte [])	merged_list.get(i); 
			        if(current_segment.length > max_segment_bytelength)
		        		max_segment_bytelength = current_segment.length;
			        compressed_segment_list.add(current_segment);
			    }
			    if(max_segment_bytelength <= Byte.MAX_VALUE * 2 + 1)
				    overhead = 16;
				else if(max_segment_bytelength <= Short.MAX_VALUE * 2 + 1)
					overhead = 24;
				else 
					overhead = 40;
		    }  
		}
		
		int size = compressed_segment_list.size();
		
		ArrayList<Integer> segment_number = new ArrayList<Integer>();
		ArrayList<Boolean> segment_is_compressed = new ArrayList<Boolean>();
		ArrayList<Integer> compressions = new ArrayList<Integer>();
		double bin_size = .05;
		
		if(size == 1)
		{
			compressed_segment_list.clear();
			compressed_segment_list.add(string);
		    list.add(compressed_segment_list);
		    list.add(string.length - 1);
		    
		    int    bitlength = StringMapper.getBitlength(string);
		    double ratio     = StringMapper.getZeroRatio(string, bitlength);
		    int    number    = SegmentMapper.getNumber(ratio, bin_size);
		    segment_number.add(number);
		    list.add(segment_number);
		    
		    int  current_iterations = StringMapper.getIterations(string);
		    if(current_iterations == 0 || current_iterations == 16)
		    {
		    	segment_is_compressed.add(false);
		    	compressions.add(0);
		    }
		    else
		    {
		    	segment_is_compressed.add(true);
		    	if(current_iterations < 16)
		    		compressions.add(current_iterations);
		    	else
		    		compressions.add(current_iterations - 16);
		    }
		    list.add(segment_is_compressed);
		    
		}
		else
		{
			for(int i = 0; i < size; i++)
			{
				byte [] current_segment = (byte [])compressed_segment_list.get(i);
				
				int current_bitlength   = StringMapper.getBitlength(current_segment);
				double ratio            = StringMapper.getZeroRatio(current_segment, current_bitlength);
				int    number           = SegmentMapper.getNumber(ratio, bin_size);
			    segment_number.add(number);
			    
			    int  current_iterations = StringMapper.getIterations(current_segment);
			    if(current_iterations == 0 || current_iterations == 16)
			    {
			    	segment_is_compressed.add(false);
			    	compressions.add(0);
			    }
			    else
			    {
			    	segment_is_compressed.add(true);
			    	if(current_iterations < 16)
			    		compressions.add(current_iterations);
			    	else
			    		compressions.add(current_iterations - 16);
			    }
			    
			    /*
			    int     zero_amount            = StringMapper.getCompressionAmount(current_segment, current_bitlength, 0);
				int     one_amount             = StringMapper.getCompressionAmount(current_segment, current_bitlength, 1);
				if(zero_amount < 0 || one_amount < 0)
				{
					System.out.print("Segment " + i + " with ratio number "	+ number + " and iterations " + current_iterations + " :");
					if(zero_amount < 0)
						System.out.println("Reduced by " + zero_amount + " with zero bitwise transform.");
					if(one_amount < 0)
						System.out.println("Reduced by " + one_amount + " with one bitwise transform.");
					System.out.println();
				}
				*/
			}
			list.add(compressed_segment_list);
			list.add(max_segment_bytelength);
			list.add(segment_number);
			list.add(segment_is_compressed);
			list.add(compressions);
		}
		
		return list;
	}
	
	
	
	public static byte[] shiftRight(byte[] src, int bit_length)
	{
		int offset = bit_length / 8;
		byte[] dst = new byte[src.length - offset];
	    
	    
	    int j      = 0;
	    for(int i = offset; i < src.length; i++)
	        dst[j++] = src[i];
	    
	    int shift = bit_length % 8;
	    if(shift != 0)
	    {
	        int reverse_shift  = 8 - shift;
	        int segment_length = 8 - shift;
	        
	        byte mask = getTrailingMask(segment_length);
	        
	    	for(int i = 0; i < dst.length - 1; i++)
	    	{
	    		dst[i] >>= shift;
	    	    dst[i] &=  mask;
	    	    byte extra_bits = (byte)(dst[i + 1] << reverse_shift);
	    	    dst[i] |= extra_bits;
	    	    
	    	}
	    	
	    	int i = dst.length - 1;
	    	dst[i] >>= shift;
	    	dst[i] &= mask;
	    }
	    
	    return dst;
	}
	
	public static int getNumber(double ratio, double increment)
	{
		int    number = 0;
		double limit  = increment;
		
		while(ratio > limit)
		{
			number++;
			limit += increment;
		}
		
		return number;
	}
	
	public static boolean isSimilar(int current_number, int next_number, int lower_limit, int upper_limit)
	{
		if(current_number < lower_limit && next_number < lower_limit)
			return true;
		else if(current_number > upper_limit && next_number > upper_limit)
			return true;
		else if(current_number >= lower_limit && current_number <= upper_limit && next_number >= lower_limit && next_number <= upper_limit)
			return true;
		else
			return false;
	}
	
	/**
	* Produces a set of masks that can be anded to isolate a bit.
	*
	* @return byte masks
	*/
	public static byte [] getPositiveMask()
	{
		byte [] mask = new byte[8];
		mask[0] = 1;
		for(int i = 1; i < 8; i++)
			mask[i] = (byte)(mask[i - 1] << 1);
		return mask;
	}
	
	/**
	* Produces a set of masks that can be anded to isolate a bit.
	*
	* @return int masks
	*/
	public static int [] getPositiveMask2()
	{
		int [] mask = new int[32];
		mask[0] = 1;
		for(int i = 1; i < 32; i++)
			mask[i] = mask[i - 1] << 1;
		return mask;
	}
	
	/**
	* Produces a set of masks that can be ored to isolate a bit.
	*
	* @return byte masks
	*/
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
	
	/**
	* Produces a set of masks that can be anded to isolate a trailing segment.
	*
	* @return byte masks
	*/
	public static byte [] getLeadingMask()
	{
		byte [] mask = new byte[7];
		mask[0] = -2;
		for(int i = 1; i < 7; i++)
			mask[i] = (byte)(mask[i - 1] * 2);
		return mask;	
	}
	
	public static byte getLeadingMask(int length)
	{
		byte [] mask = new byte[7];
		mask[0] = -2;
		for(int i = 1; i < 7; i++)
			mask[i] = (byte)(mask[i - 1] * 2);
		return mask[length - 1];	
	}
	
	/**
	* Produces a set of masks that can be anded to isolate a trailing segment.
	*
	* @return byte masks
	*/
	public static byte [] getTrailingMask()
	{
		byte [] mask = new byte[7];
		mask[0] = 1;
		for(int i = 1; i < 7; i++)
			mask[i] = (byte)(mask[i - 1] * 2 + 1);
		return mask;	
	}
	
	public static byte getTrailingMask(int length)
	{
		byte [] mask = new byte[7];
		mask[0] = 1;
		for(int i = 1; i < 7; i++)
			mask[i] = (byte)(mask[i - 1] * 2 + 1);
		return mask[length - 1];	
	}
	
}