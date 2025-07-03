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
	* We don't parameterize the list because we want to add random types.
	*
	* @param src the input byte string
	* @param minimum_bitlength original segment length
	* @return the segmented/merged byte string
	*/
	public static ArrayList getMergedSegmentedData(byte[] string, int minimum_bitlength) 
	{
		ArrayList result = new ArrayList();
		
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
		    return result;
		}
		
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
		
		// Add a trailing byte for metadata.
		last_segment_bytelength++;
		
		// To start off, the last segment byte length is always greater than or equal to the 
		// maximum byte length.
		int max_segment_bytelength = last_segment_bytelength;
		
		// Get an initial list of uncompressed segments of equal length, except possibly the last segment.
		// Set iterations to 16 or 0, depending on the type.  Extra bits will be 0, except possibly
		// the last segment.
		ArrayList segments = new ArrayList();
		
		// Look up table for collecting bit data.
		int [] bit_table = StringMapper.getBitTable();	
		
		for(int i = 0; i < number_of_segments; i++)
		{
		    if(i < number_of_segments - 1)
		    {
		        	byte [] segment = new byte[segment_bytelength];
		    	    for(int j = 0; j < segment.length - 1; j++)
					segment[j] = string[i * (segment_bytelength - 1) + j];
		    	
		    	    
		    	    double zero_ratio = StringMapper.getZeroRatio(segment, segment_bitlength, bit_table);
		       	byte iterations = 0;
			    if(zero_ratio < .5)
					iterations = 16;
				segment[segment.length - 1] = iterations;
				segments.add(segment);
		    }	
		    else 
		    {
		    	    byte [] segment = new byte[last_segment_bytelength];   
		    	    for(int j = 0; j < segment.length - 1; j++)
					segment[j] = string[i * (segment_bytelength - 1) + j];
		    	
		    	    // Check if the last segment is even.
		    	    byte extra_bits = 0;
				if(remainder != 0)
				{
					extra_bits = (byte)(8 - remainder);
				    extra_bits <<= 5;
				}
		    	    segment[segment.length - 1] = extra_bits;
		    	
		    	    double zero_ratio = StringMapper.getZeroRatio(segment, last_segment_bitlength, bit_table);
		      	byte iterations = 0;
				if (zero_ratio < .5)
					iterations = 16;
				segment[segment.length - 1] |= iterations;
				segments.add(segment);
		    }
		}
		
		ArrayList compressed_segments = new ArrayList();
		for(int i = 0; i < number_of_segments; i++)
		{
			byte [] segment           = (byte [])segments.get(i);
			byte [] compressed_segment = StringMapper.compressStrings(segment);
			compressed_segments.add(compressed_segment);
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
	
		int current_number_of_segments  = number_of_segments;
		int previous_number_of_segments = 0;
		ArrayList merged_segments       = new ArrayList();
		
		while(current_number_of_segments != previous_number_of_segments)
		{
		    previous_number_of_segments = current_number_of_segments;
		    merged_segments.clear();
		    
			for(int i = 0; i < current_number_of_segments; i++) 
			{
				byte [] current_segment = (byte[])compressed_segments.get(i);
				if(i < current_number_of_segments - 1)
				{
				    byte [] next_segment   = (byte[])compressed_segments.get(i + 1);
				    int current_iterations = StringMapper.getIterations(current_segment);
				    int next_iterations    = StringMapper.getIterations(next_segment);
				    
				    if(current_iterations == next_iterations || (current_iterations == 0 && next_iterations == 16) || (current_iterations == 16 && next_iterations == 0))
				    {
				        int current_bitlength  = StringMapper.getBitlength(current_segment);
				        int next_bitlength     = StringMapper.getBitlength(next_segment);
				        int combined_bitlength = current_bitlength + next_bitlength;
				        
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
				    	    
				    	        double zero_ratio = StringMapper.getZeroRatio(merged_segment, combined_bitlength, bit_table);
				    	        if(zero_ratio >= .5)
				    	         	current_iterations = 0;
				    	        else
				    	    	        current_iterations = 16;
				    	        merged_segment[merged_segment.length - 1] |= current_iterations;
				
				    	        merged_segments.add(merged_segment);
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
				    	        }
				    	        
				    	        if(current_iterations > 15)
				    	        	    uncompressed_merged_segment[uncompressed_merged_segment.length - 1] |= 16;   
				    	        
				    	        byte [] merged_segment = StringMapper.compressStrings(uncompressed_merged_segment);
				    	        int merged_bitlength   = StringMapper.getBitlength(merged_segment);
				    	        if(merged_bitlength - overhead < decompressed_combined_bitlength)
				    	        {
				    	    	        merged_segments.add(merged_segment);
					    	        i++;	
				    	        }
				    	        else
				    	    	        merged_segments.add(current_segment);
				        }
				    }
				    else
				    	    merged_segments.add(current_segment);	
			    }
				else if(i == current_number_of_segments - 1)
				    merged_segments.add(current_segment);
		    }
			
		    current_number_of_segments = merged_segments.size();
		    if(merged_segments.size() < compressed_segments.size())
		    {
		    	    // There's going to be another iteration, so clear compressed segment list
		    	    // and get the maximum byte length from the merged set of segments.
			    compressed_segments.clear();
			    for(int i = 0; i < merged_segments.size(); i++)
			    {
			        byte [] segment = (byte [])	merged_segments.get(i); 
			        if(segment.length > max_segment_bytelength)
		        		    max_segment_bytelength = segment.length;
			        compressed_segments.add(segment);
			    }
			    
			    // Check overhead in case max segment bytelength has changed.
			    if(max_segment_bytelength <= Byte.MAX_VALUE * 2 + 1)
				    overhead = 16;
				else if(max_segment_bytelength <= Short.MAX_VALUE * 2 + 1)
					overhead = 24;
				else 
					overhead = 40;
		    }  
		}
		
		result.add(compressed_segments);
		result.add(max_segment_bytelength);
		return result;
	}
	
	
	
	/**
	* Segments a byte string and then merges the segments back together,
	* depending on how it affects the rate of compression.
	* We don't parameterize the list because we want to add random types.
	* Similar to the method above but returns some extra information.
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
		ArrayList segments = new ArrayList();
		
		// Look up table for collecting bit data.
		int [] bit_table = StringMapper.getBitTable();	
		
		for(int i = 0; i < number_of_segments; i++)
		{
		    if(i < number_of_segments - 1)
		    {
		        	byte [] segment = new byte[segment_bytelength];
		    	    for(int j = 0; j < segment.length - 1; j++)
					segment[j] = string[i * (segment_bytelength - 1) + j];
		    	
		    	    
		    	    double zero_ratio = StringMapper.getZeroRatio(segment, segment_bitlength, bit_table);
		       	byte iterations = 0;
			    if(zero_ratio < .5)
					iterations = 16;
				segment[segment.length - 1] = iterations;
				segments.add(segment);
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
		    	
		    	    double zero_ratio = StringMapper.getZeroRatio(segment, last_segment_bitlength, bit_table);
		      	byte iterations = 0;
				if (zero_ratio < .5)
					iterations = 16;
				segment[segment.length - 1] |= iterations;
				segments.add(segment);
		    }
		}
		

		ArrayList compressed_segments = new ArrayList();
		for(int i = 0; i < number_of_segments; i++)
		{
			byte [] segment = (byte [])segments.get(i);
			int type = StringMapper.getType(segment);
			
			if(type == 0)
			{
				byte [] compressed_segment = StringMapper.compressZeroStrings(segment);
				byte [] decompressed_segment = StringMapper.decompressZeroStrings(compressed_segment);
				int     one_amount             = StringMapper.getCompressionAmount(compressed_segment, StringMapper.getBitlength(compressed_segment), 1);
				
				if(decompressed_segment.length != segment.length)
				{
					System.out.println("Checking zero string compression");
					System.out.println("The original segment length was " + segment.length);
					System.out.println("The decompressed segment length was " + decompressed_segment.length);
					compressed_segments.add(segment);	
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
		    	    	        compressed_segments.add(segment);
		    	        else
		    	    	        compressed_segments.add(compressed_segment);
				}
			}
			else
			{
				byte [] compressed_segment = StringMapper.compressOneStrings(segment);
				int     zero_amount        = StringMapper.getCompressionAmount(compressed_segment, StringMapper.getBitlength(compressed_segment), 0);
				if(zero_amount < 0)
				{
					System.out.println("Compressed segment " + i + " with type " + StringMapper.getType(compressed_segment) + " could be reduced by " + zero_amount + " with zero bitwise transform.");
					System.out.println("The zero ratio of the segment is " + StringMapper.getZeroRatio2(compressed_segment, StringMapper.getBitlength(compressed_segment)));
				}
				byte [] decompressed_segment = StringMapper.decompressOneStrings(compressed_segment);
				if(decompressed_segment.length != segment.length)
				{
					System.out.println("Checking one string compression");
					System.out.println("The original segment length was " + segment.length);
					System.out.println("The decompressed segment length was " + decompressed_segment.length);
					compressed_segments.add(segment);	
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
		    	         	compressed_segments.add(segment);
		    	        else
		    	    	        compressed_segments.add(compressed_segment);
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
		ArrayList merged_segments = new ArrayList();
		ArrayList input_list  = new ArrayList();
		
		while(current_number_of_segments != previous_number_of_segments)
		{
		    //iterations++;
		    previous_number_of_segments = current_number_of_segments;
		    merged_segments.clear();
		    input_list.clear();
		    
			for(int i = 0; i < current_number_of_segments; i++) 
			{
				byte [] current_segment = (byte[])compressed_segments.get(i);
				if(i < current_number_of_segments - 1)
				{
				    byte [] next_segment   = (byte[])compressed_segments.get(i + 1);
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
				    	    
				    	        double zero_ratio = StringMapper.getZeroRatio2(merged_segment, combined_bitlength);
				    	        if(zero_ratio >= .5)
				    	         	current_iterations = 0;
				    	        else
				    	    	        current_iterations = 16;
				    	        merged_segment[merged_segment.length - 1] |= current_iterations;
				    	    
				    	        int compression_amount = 0;
				    	        if(current_iterations == 0)
				    	         	compression_amount = StringMapper.getCompressionAmount(merged_segment, combined_bitlength, 0);
				    	        else
				    	    	        compression_amount = StringMapper.getCompressionAmount(merged_segment, combined_bitlength, 1);
				    	        if(compression_amount < 0)
				    	        {
				    	    	        System.out.println("Uncompressed segment can be compressed by " + compression_amount);
				    	    	        System.out.println();
				    	        }
				    	        else if(compression_amount == 0)
				    	        {
				    	    	        System.out.println("Length of uncompressed segment is not changed by bit transform.");
				    	         	System.out.println();
				    	        }
				    	        merged_segments.add(merged_segment);
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
				    	    	        merged_segments.add(merged_segment);
				    	    	        input_list.add(uncompressed_merged_segment);
					    	        i++;	
				    	        }
				    	        else
				    	        {
				    	    	        merged_segments.add(current_segment); 
				    	         	input_list.add(current_segment);
				    	        }
				        }
				    }
				    else
				    {
				    	    merged_segments.add(current_segment);	
				    	    input_list.add(current_segment);
				    }
			    }
				else if(i == current_number_of_segments - 1)
				{
				    merged_segments.add(current_segment);
				    input_list.add(current_segment);
				}
		    }
			
		    current_number_of_segments = merged_segments.size();
		    if(merged_segments.size() < compressed_segments.size())
		    {
			    compressed_segments.clear();
			    for(int i = 0; i < merged_segments.size(); i++)
			    {
			        byte [] current_segment = (byte [])	merged_segments.get(i); 
			        if(current_segment.length > max_segment_bytelength)
		        		max_segment_bytelength = current_segment.length;
			        compressed_segments.add(current_segment);
			    }
			    if(max_segment_bytelength <= Byte.MAX_VALUE * 2 + 1)
				    overhead = 16;
				else if(max_segment_bytelength <= Short.MAX_VALUE * 2 + 1)
					overhead = 24;
				else 
					overhead = 40;
		    }  
		}
		
		int size = compressed_segments.size();
		
		ArrayList<Integer> segment_number = new ArrayList<Integer>();
		ArrayList<Boolean> segment_is_compressed = new ArrayList<Boolean>();
		ArrayList<Integer> compressions = new ArrayList<Integer>();
		double bin_size = .05;
		
		if(size == 1)
		{
			compressed_segments.clear();
			compressed_segments.add(string);
		    list.add(compressed_segments);
		    list.add(string.length - 1);
		    
		    int    bitlength = StringMapper.getBitlength(string);
		    double ratio     = StringMapper.getZeroRatio2(string, bitlength);
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
				byte [] current_segment = (byte [])compressed_segments.get(i);
				
				int current_bitlength   = StringMapper.getBitlength(current_segment);
				
				double ratio            = StringMapper.getZeroRatio(current_segment, current_bitlength, bit_table);
				
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
			}
			
			list.add(compressed_segments);
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
	* Packs a list of bitstrings into a single bitstring in a single byte array. 
	* Assumes each segment has a data byte used to calculate the bitlength.
	*
	* @param  ArrayList segments array list with byte arrays that include a trailing byte with segment data.
	* @return ArrayList result array list that includes the packed bit string and the bit lengths of the packed segments.
	*/
	public static ArrayList packSegments(ArrayList <byte []>segments)
	{
		int size         = segments.size();
		int [] bitlength = new int[size];
		
		int total_bitlength = 0;
		for(int i = 0; i < size; i++)
		{
			byte [] current_segment = (byte [])segments.get(i);
			bitlength[i]            = StringMapper.getBitlength(current_segment);
			total_bitlength        += bitlength[i];
		}	
		
		int total_bytelength = total_bitlength / 8;
		if(total_bitlength % 8 != 0)
			total_bytelength++;
		byte [] string = new byte[total_bytelength];
		
		byte [] mask = {1, 3, 7, 15, 31, 63, 127};
		
		int bit_offset = 0;
		
		for(int i = 0; i < size - 1; i++)
		{
			int m = bit_offset % 8;
		    int n = bit_offset / 8;
		    
		    
		    byte [] current_segment = (byte [])segments.get(i);
			int     length          = StringMapper.getBitlength(current_segment);
			
			
			if(m == 0)
			{
				for(int j = 0; j < current_segment.length - 1; j++)	
			        string[n + j] = current_segment[j];	
			}
			else
			{
				for(int j = 0; j < current_segment.length - 1; j++)
				{
					byte a     = (byte)(current_segment[j] << m);
					string[n + j] |= a;
					
					int number_of_bits = m;
					byte b             = (byte)(current_segment[j] >> 8 - m);
					b                 &= mask[number_of_bits - 1];
				    string[n + j + 1] = b;
				}
			} 
			
			bit_offset += length;
		}
		
		byte [] last_segment = (byte [])segments.get(size - 1);
		int m                = bit_offset % 8;
	    int n                = bit_offset / 8;
	    
	    if(m == 0)
		{
			for(int j = 0; j < last_segment.length - 1; j++)	
		        string[n + j] = last_segment[j];	
		}
	    else
		{
			for(int j = 0; j < last_segment.length - 2; j++)
			{
				byte a     = (byte)(last_segment[j] << m);
				string[n + j] |= a;
				
				int number_of_bits = m;
				byte b             = (byte)(last_segment[j] >> 8 - m);
				b                 &= mask[number_of_bits - 1];
			    string[n + j + 1] = b;
			}
			byte a = (byte)(last_segment[last_segment.length - 2] << m);
			string[n + last_segment.length - 2] |= a;
			if(n + last_segment.length - 2 + 1 < string.length)
			{
				byte b             = (byte)(last_segment[last_segment.length - 2] >> 8 - m);
				b                 &= mask[m - 1];
			    string[n + last_segment.length - 2 + 1] = b;   	
			}
			
		} 
		
		ArrayList result = new ArrayList();
		result.add(string);
		result.add(bitlength);
		return result;
	}
	
	
	/**
	* Packs a list of bit strings into a single bit string in a single byte array. 
	* 
	* @ param ArrayList a list of byte arrays that contain a bit string 
	* @ param byte [] bitlength lengths of bit strings contained in byte arrays
	* @return ArrayList result includes the packed bit strings in a single byte array.
	*/
	public static ArrayList packSegments(ArrayList <byte []>segments, int [] bitlength)
	{
		// Returning a list instead of the byte array directly provides
		// for a simple way of error checking by returning an empty list.
		// We can even attach an error code if necessary.
		ArrayList result = new ArrayList();
		int size         = segments.size();
		
		if(size != bitlength.length)
		{
			System.out.println("Number of segments and number of bit lengths do not agree.");
			return result;
		}
		
		int total_bitlength = 0;
		for(int i = 0; i < size; i++)
			total_bitlength        += bitlength[i];
		int total_bytelength = total_bitlength / 8;
		if(total_bitlength % 8 != 0)
			total_bytelength++;
		byte [] string = new byte[total_bytelength];
		
		byte [] mask = {1, 3, 7, 15, 31, 63, 127};
		
		int bit_offset = 0;
		
		for(int i = 0; i < size - 1; i++)
		{
			int m = bit_offset % 8;
		    int n = bit_offset / 8;
		    
		    
		    byte [] current_segment = (byte [])segments.get(i);
			int     length          = StringMapper.getBitlength(current_segment);
			
			
			if(m == 0)
			{
				for(int j = 0; j < current_segment.length - 1; j++)	
			        string[n + j] = current_segment[j];	
			}
			else
			{
				for(int j = 0; j < current_segment.length - 1; j++)
				{
					byte a     = (byte)(current_segment[j] << m);
					string[n + j] |= a;
					
					int number_of_bits = m;
					byte b             = (byte)(current_segment[j] >> 8 - m);
					b                 &= mask[number_of_bits - 1];
				    string[n + j + 1] = b;
				}
			} 
			
			bit_offset += length;
		}
		
		byte [] last_segment = (byte [])segments.get(size - 1);
		int m                = bit_offset % 8;
	    int n                = bit_offset / 8;
	    
	    if(m == 0)
		{
			for(int j = 0; j < last_segment.length - 1; j++)	
		        string[n + j] = last_segment[j];	
		}
	    else
		{
			for(int j = 0; j < last_segment.length - 2; j++)
			{
				byte a     = (byte)(last_segment[j] << m);
				string[n + j] |= a;
				
				int number_of_bits = m;
				byte b             = (byte)(last_segment[j] >> 8 - m);
				b                 &= mask[number_of_bits - 1];
			    string[n + j + 1] = b;
			}
			byte a = (byte)(last_segment[last_segment.length - 2] << m);
			string[n + last_segment.length - 2] |= a;
			if(n + last_segment.length - 2 + 1 < string.length)
			{
				byte b             = (byte)(last_segment[last_segment.length - 2] >> 8 - m);
				b                 &= mask[m - 1];
			    string[n + last_segment.length - 2 + 1] = b;   	
			}
			
		} 
		
		result.add(string);
		return result;
	}
	
	
	
	/**
	* Packs a list of bit strings into a single bit string in a single byte array. 
	*
	* @param  ArrayList list of byte arrays that include a trailing byte with segment data.
	* @return ArrayList result which includes the bit string in a single byte array and the lengths of the packed segments.
	*/
	public static ArrayList packSegments2(ArrayList <byte []>segments)
	{
		int size         = segments.size();
		int [] bitlength = new int[size];
		
		int total_bitlength = 0;
		for(int i = 0; i < size; i++)
		{
			byte [] current_segment = (byte [])segments.get(i);
			bitlength[i]            = StringMapper.getBitlength(current_segment);
			total_bitlength        += bitlength[i];
		}	
		
		int total_bytelength = total_bitlength / 8;
		if(total_bitlength % 8 != 0)
			total_bytelength++;
		byte [] string = new byte[total_bytelength];
		
		int k = 0;
		for(int i = 0; i < size; i++)
		{
			byte [] current_segment = (byte [])segments.get(i);
			bitlength[i]            = StringMapper.getBitlength(current_segment);
			
			for(int j = 0; j < bitlength[i]; j++)
			{
				int value = SegmentMapper.getBit(current_segment, j);	
				SegmentMapper.setBit(string, k++, value);
			}
		}
		
		ArrayList result = new ArrayList();
		result.add(string);
		result.add(bitlength);
		return result;
	}
	
	/**
	* Packs a list of bit strings into a single bit string in a single byte array. 
	*
	*
	* @param  ArrayList segments list of byte arrays that include a trailing byte with segment data.
	* @return ArrayList result which includes the bit string in a single byte array and the bit lengths of the packed segments.
	*/
	public static ArrayList packSegments2(ArrayList <byte []>segments, int [] bitlength)
	{
		int size         = segments.size();
		
		int total_bitlength = 0;
		for(int i = 0; i < size; i++)
		{
			byte [] current_segment = (byte [])segments.get(i);
			bitlength[i]            = StringMapper.getBitlength(current_segment);
			total_bitlength        += bitlength[i];
		}	
		
		int total_bytelength = total_bitlength / 8;
		if(total_bitlength % 8 != 0)
			total_bytelength++;
		byte [] string = new byte[total_bytelength];
		
		int k = 0;
		for(int i = 0; i < size; i++)
		{
			byte [] current_segment = (byte [])segments.get(i);
			bitlength[i]            = StringMapper.getBitlength(current_segment);
			
			for(int j = 0; j < bitlength[i]; j++)
			{
				int value = SegmentMapper.getBit(current_segment, j);	
				SegmentMapper.setBit(string, k++, value);
			}
		}
		
		ArrayList result = new ArrayList();
		result.add(string);
		return result;
	}
	
	
	/**
	* Unpacks a string of concatenated segments into separate segments including a data byte.  
	*
	* @param int [] bytelength
	* @param byte [] array of data bytes to be appended to segments
	* @return ArrayList segments a list of byte arrays containing bit strings with a data byte appended.
	*/
	public static ArrayList <byte []> unpackSegments(byte []string, int bytelength[], byte data[])
	{
		int number_of_segments = data.length;
		ArrayList <byte []> unpacked_segments = new ArrayList <byte []>();
		byte [] mask = {1, 3, 7, 15, 31, 63, 127};
		
		int bit_offset = 0;
		for(int i = 0; i < number_of_segments - 1; i++)
		{
			byte extra_bits = (byte)(data[i] >> 5);
			extra_bits &= 7;
			int bitlength = bytelength[i] * 8 - extra_bits;
			byte [] segment = new byte[bytelength[i] + 1];
			
			int m = bit_offset % 8;
			int n = bit_offset / 8;
			if(m == 0)
			{
				for(int j = 0; j < bytelength[i]; j++)
					segment[j] = string[n + j];
				// It can be useful later if extra bits are zeroed.
				if(bitlength % 8 != 0)
				{
					int number_of_bits = bitlength % 8;
					segment[bytelength[i] - 1] &= mask[number_of_bits - 1];
				}
			}
			else
			{
				for(int j = 0; j < bytelength[i]; j++)
				{
					segment[j]  = (byte)(string[n + j] >> m);
					int number_of_bits   = 8 - m;
					segment[j] &= mask[number_of_bits - 1];
					
					if(j < bytelength[i] - 1)
					{
						byte high_bits = (byte)(string[n + j + 1]);
						segment[j] |= high_bits << 8 - m;
					}
					else if(j == bytelength[i] - 1)
					{
						byte high_bits = (byte)(string[n + j + 1]); 
					    segment[j] |= high_bits << 8 - m;
		    	            int number_of_extra_bits = (j + 1) * 8 - bitlength;
		    	            if(number_of_extra_bits > 0)
		    	            	    segment[j] &= mask[8 - number_of_extra_bits - 1];	     
					}
				}
			}

			segment[bytelength[i]] = data[i];
			unpacked_segments.add(segment);
			bit_offset += bitlength;
		}
		 
		
		int i = number_of_segments - 1;
		byte extra_bits = (byte)(data[i] >> 5);
		extra_bits &= 7;
		int bitlength = bytelength[i] * 8 - extra_bits;
		byte [] segment = new byte[bytelength[i] + 1];
		
		int m = bit_offset % 8;
		int n = bit_offset / 8;
		if(m == 0)
		{
			for(int j = 0; j < bytelength[i]; j++)
				segment[j] = string[n + j];	
		}
		else
		{
			for(int j = 0; j < bytelength[i]; j++)
			{
				segment[j]  = (byte)(string[n + j] >> m);
				int number_of_bits   = 8 - m;
				segment[j] &= mask[number_of_bits - 1];
				
				if(j < bytelength[i] - 1)
				{
					byte high_bits = (byte)(string[n + j + 1]);
					segment[j] |= high_bits << 8 - m;
				}
				else if(j == bytelength[i] - 1)
				{
					if(n + j + 1 < string.length)
					{
					    byte high_bits = (byte)(string[n + j + 1]); 
					    segment[j] |= high_bits << 8 - m;
		    	            int number_of_extra_bits = (j + 1) * 8 - bitlength;
		    	            if(number_of_extra_bits > 0)
		    	            {
		    	            	    number_of_bits = 8 - number_of_extra_bits;
		    	            	    segment[j] &= mask[number_of_bits - 1];
		    	            }
		    	            
		    	            
		    	            
		    	            
		    	            
		    	            /*
		    	            if(number_of_extra_bits > 0)
		    	            	    segment[j] &= mask[8 - number_of_extra_bits - 1]; 
		    	            	*/
					}
				}
			}	
		}
		unpacked_segments.add(segment);
		
		return unpacked_segments;
	}
	
	/**
	* Unpacks a string of concatenated segments into separate segments.  
	*
	* @param int [] string byte array containing concatenated strings
	* @param int [] bitlength lengths of bit strings in concatenated string
	* @return ArrayList segments a list of byte arrays containing bit strings.
	*/
	
	public static ArrayList <byte []> unpackSegments(byte []string, int bitlength[])
	{
		int number_of_segments = bitlength.length;
		ArrayList <byte []> unpacked_segments = new ArrayList <byte []>();
		byte [] mask = {1, 3, 7, 15, 31, 63, 127};
		
		int bit_offset = 0;
		for(int i = 0; i < number_of_segments - 1; i++)
		{
			int bytelength = bitlength[i] / 8;
			if(bitlength[i] % 8 != 0)
				bytelength++;
			
			byte [] segment = new byte[bytelength];
			
			int m = bit_offset % 8;
			int n = bit_offset / 8;
			if(m == 0)
			{
				for(int j = 0; j < bytelength; j++)
					segment[j] = string[n + j];
				// It can be useful later if extra bits are zeroed.
				if(bitlength[i] % 8 != 0)
				{
					int number_of_bits = bitlength[i] % 8;
					segment[bytelength - 1] &= mask[number_of_bits - 1];
				}
			}
			else
			{
				for(int j = 0; j < bytelength; j++)
				{
					segment[j]  = (byte)(string[n + j] >> m);
					int number_of_bits   = 8 - m;
					segment[j] &= mask[number_of_bits - 1];
					
					if(j < bytelength - 1)
					{
						byte high_bits = (byte)(string[n + j + 1]);
						segment[j] |= high_bits << 8 - m;
					}
					else if(j == bytelength - 1)
					{
						byte high_bits = (byte)(string[n + j + 1]); 
					    segment[j] |= high_bits << 8 - m;
		    	            int number_of_extra_bits = (j + 1) * 8 - bitlength[i];
		    	            if(number_of_extra_bits > 0)
		    	            	    segment[j] &= mask[8 - number_of_extra_bits - 1];	     
					}
				}
			}

			unpacked_segments.add(segment);
			bit_offset += bitlength[i];
		}
		 
		
		int i = number_of_segments - 1;
		
		int bytelength = bitlength[i] / 8;
		if(bitlength[i] % 8 != 0)
			bytelength++;
		
		byte [] segment = new byte[bytelength];
		
		int m = bit_offset % 8;
		int n = bit_offset / 8;
		if(m == 0)
		{
			for(int j = 0; j < bytelength; j++)
				segment[j] = string[n + j];	
		}
		else
		{
			for(int j = 0; j < bytelength; j++)
			{
				segment[j]  = (byte)(string[n + j] >> m);
				int number_of_bits   = 8 - m;
				segment[j] &= mask[number_of_bits - 1];
				
				if(j < bytelength - 1)
				{
					byte high_bits = (byte)(string[n + j + 1]);
					segment[j] |= high_bits << 8 - m;
				}
				else if(j == bytelength - 1)
				{
					if(n + j + 1 < string.length)
					{
					    byte high_bits = (byte)(string[n + j + 1]); 
					    segment[j] |= high_bits << 8 - m;
		    	            int number_of_extra_bits = (j + 1) * 8 - bitlength[i]	;
		    	            if(number_of_extra_bits > 0)
		    	            {
		    	            	    number_of_bits = 8 - number_of_extra_bits;
		    	            	    segment[j] &= mask[number_of_bits - 1];
		    	            }
					}
				}
			}	
		}
		unpacked_segments.add(segment);
		
		return unpacked_segments;
	}

	/**
	* Unpacks a string of concatenated segments into separate segments including a data byte.  
	*
	* @return ArrayList unpacked segments a list of bit strings with a data byte.
	*/
	public static ArrayList <byte []> unpackSegments2(byte []string, int bytelength[], byte data[])
	{
		int number_of_segments = data.length;
		ArrayList <byte []> unpacked_segments = new ArrayList <byte []>();

		int k = 0;
		for(int i = 0; i < number_of_segments; i++)
		{
			byte extra_bits = (byte)(data[i] >> 5);
			extra_bits &= 7;
			int bitlength = bytelength[i] * 8 - extra_bits;
			byte [] segment = new byte[bytelength[i] + 1];
			for(int j = 0; j < bitlength; j++)
			{
				int value = SegmentMapper.getBit(string, k++);
				SegmentMapper.setBit(segment, j, value);
			}
			segment[bytelength[i]] = data[i];
			unpacked_segments.add(segment);
		}
		
		return unpacked_segments;
	}
	

	/**
	* Unpacks a string of concatenated segments into separate segments including a data byte.  
	*
	* @return ArrayList unpacked segments a list of bit strings with a data byte.
	*/
	public static ArrayList <byte []> unpackSegments2(byte []string, int bitlength[])
	{
		int number_of_segments = bitlength.length;
		ArrayList <byte []> unpacked_segments = new ArrayList <byte []>();

		int k = 0;
		for(int i = 0; i < number_of_segments; i++)
		{
			int bytelength = bitlength[i] / 8;
			if(bitlength[i] % 8 != 0)
				bytelength++;
			
			byte [] segment = new byte[bytelength];
			
			for(int j = 0; j < bitlength[i]; j++)
			{
				int value = SegmentMapper.getBit(string, k++);
				SegmentMapper.setBit(segment, j, value);
			}
			
			unpacked_segments.add(segment);
		}
		
		return unpacked_segments;
	}
	
	
	/**
	* Produces a set of masks that can be anded to isolate a bit at any position.
	*
	* @return byte [] masks
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
	* Produces a mask that can be anded to isolate a bit at a specific position.
	*
	* @return byte mask
	*/
	public static byte getPositiveMask(int position)
	{
		int i = 1;
		for(int j = 0; j < position; j++)
			i *= 2;
		byte mask = (byte)i;
		
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
	* Produces a set of masks that can be ored to isolate a bit at any position.
	*
	* @return byte [] masks
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
	* Produces a mask that can be ored to isolate a bit at a specific position.
	*
	* @return byte mask
	*/
	public static byte getNegativeMask(int position)
	{
		int i = 1;
		for(int j = 0; j < position; j++)
			i *= 2;
		
		byte mask = (byte)i;
		mask      = (byte)(~mask);
		
		return mask;
	}
	
	/**
	* Produces a set of masks that can be anded to isolate leading segments.
	*
	* @return byte [] masks
	*/
	public static byte [] getLeadingMask()
	{
		byte [] mask = new byte[7];
		mask[0] = -2;
		for(int i = 1; i < 7; i++)
			mask[i] = (byte)(mask[i - 1] * 2);
		return mask;	
	}
	
	/**
	* Produces a single mask that can be anded to isolate a leading segment of a 
	* specific length.
	*
	* @return byte mask
	*/
	public static byte getLeadingMask(int length)
	{
		byte [] mask = new byte[7];
		mask[0] = -2;
		for(int i = 1; i < 7; i++)
			mask[i] = (byte)(mask[i - 1] * 2);
		return mask[length - 1];	
	}
	
	/**
	* Produces a set of masks that can be anded to isolate trailing segments.
	*
	* @return byte [] mask
	*/
	public static byte [] getTrailingMask()
	{
		byte [] mask = new byte[7];
		mask[0] = 1;
		for(int i = 1; i < 7; i++)
			mask[i] = (byte)(mask[i - 1] * 2 + 1);
		return mask;	
	}
	
	/**
	* Produces a single mask that can be anded to isolate a trailing segment of a 
	* specific length.
	*
	* @return byte mask
	*/
	public static byte getTrailingMask(int length)
	{
		byte [] mask = new byte[7];
		mask[0] = 1;
		for(int i = 1; i < 7; i++)
			mask[i] = (byte)(mask[i - 1] * 2 + 1);
		return mask[length - 1];	
	}
	
	public static int getBit(byte [] string, int position)
	{
		int  byte_offset = position / 8;
		int  bit_offset  = position % 8;
		byte mask        = getPositiveMask(bit_offset); 
		
		if((string[byte_offset] & mask) == 0)
			return 0;
		else
			return 1;
	}
	
	public static void setBit(byte [] string, int position, int value)
	{
		int byte_offset = position / 8;
		int bit_offset  = position % 8;
		if(value == 0)
		{
			byte mask = getNegativeMask(bit_offset);
			string[byte_offset] &= mask;
		}
		else
		{
			byte mask = getPositiveMask(bit_offset);
			string[byte_offset] |= mask;
		}
	}
	
	
}