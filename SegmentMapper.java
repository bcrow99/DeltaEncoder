import java.util.*;
import java.util.zip.*;
import java.lang.Math.*;
import java.math.*;

public class SegmentMapper 
{
	// This function returns a segment list with regular lengths.

	public static ArrayList getSegmentedData(byte[] string, int string_bit_length, int segment_bit_length) 
	{
		int number_of_segments = string_bit_length / segment_bit_length;
		int segment_byte_length = segment_bit_length / 8;
		int remainder = string_bit_length % segment_bit_length;
		int last_segment_bit_length = segment_bit_length + remainder;
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
		int current_bit_length  = segment_bit_length;
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
			if(string_type == 0)
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
				int compressed_bit_length = 0;
				if (string_type == 0)
					compressed_bit_length = StringMapper.compressZeroStrings(segment, current_bit_length, compressed_segment);
				else
					compressed_bit_length = StringMapper.compressOneStrings(segment, current_bit_length, compressed_segment);
				int compressed_byte_length = compressed_bit_length / 8;
				if (compressed_bit_length % 8 != 0)
					compressed_byte_length++;
				
				// Add the byte for iterations not included in the bit length.
				compressed_byte_length++;

				byte[] clipped_string = new byte[compressed_byte_length];
				for (int j = 0; j < compressed_byte_length; j++)
					clipped_string[j] = compressed_segment[j];
				compressed_data.add(clipped_string);
				compressed_length.add(compressed_bit_length);
			}
		}

		// Finished constructing initial list.

		ArrayList segment_data = new ArrayList();
		segment_data.add(compressed_length);
		segment_data.add(compressed_data);
		segment_data.add(max_segment_byte_length);
		
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
		
		return segment_data;
	}
	
	public static ArrayList getMergedSegmentedData(byte[] string, int minimum_bitlength) 
	{
		ArrayList list = new ArrayList();
		
		// For the merging to work without any bit masking and shifting, 
		// the minimum segment bit length has to be a multiple of 8
		// Open question if this constraint significantly affects rate of compression, but seems unlikely.
		if(minimum_bitlength % 8 != 0)
		{
		    System.out.println("Minimum segment bitlength must be a multiple of 8.");
		    return list;
		}
		
		int string_type       = StringMapper.getType(string);
		int string_iterations = StringMapper.getIterations(string);
		int string_bitlength  = StringMapper.getBitlength(string);
		
		System.out.println("Original string length is " + string_bitlength);
		
		int number_of_segments = string_bitlength / minimum_bitlength;
		int segment_bitlength      = minimum_bitlength;
		int segment_bytelength     = minimum_bitlength / 8;
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
		    	double zero_ratio = StringMapper.getZeroRatio(segment, segment_bitlength);
		    	
		    	byte extra_bits = 0;
				if(remainder != 0)
				{
					extra_bits = (byte)(8 - remainder);
				    extra_bits <<= 5;
				    segment[segment.length - 1] = extra_bits;
				}
				
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
				segment_list.set(i, compressed_segment);
			}
			else
			{
				byte [] compressed_segment = StringMapper.compressOneStrings(segment);
				segment_list.set(i, compressed_segment);
			}
		}
		
		// Finished constructing initial list.
		
		// The int for the segment byte length, and the trailing byte with segment information.
		// As the number of segments increase and the segment lengths decrease, the int might
		// be replaced by a short or byte.
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
			
			System.out.println("Starting merge.");
			
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
				        	System.out.println("Merging uncompressed segments.");
				            int modulus = current_bitlength % 8;
				            if(modulus != 0)
				            	System.out.println("Segment " + i + " is uneven.");
				            else
				            	System.out.println("Segment " + i + " is even.");
				            modulus = next_bitlength % 8;
				            if(modulus != 0)
				            	System.out.println("Segment " + (i + 1) + " is uneven.");
				            else
				            	System.out.println("Segment " + (i + 1) + " is even.");
				            modulus = combined_bitlength % 8;
				            if(modulus != 0)
				            	System.out.println("Combined segment is uneven.");
				            else
				            	System.out.println("Combined segment is even.");
				            System.out.println();
				            
				            int bytelength         = current_segment.length + next_segment.length - 1;
				            byte [] merged_segment = new byte[bytelength];
				            

				    	    for(int k = 0; k < current_segment.length - 1; k++)
				    	    	merged_segment[k] = current_segment[k];
				    	    int offset = current_segment.length - 1;
				    	    for(int k = 0; k < next_segment.length - 1; k++)
				    	    	merged_segment[k + offset] = next_segment[k];
				    	    modulus = combined_bitlength % 8;
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
				    		
				    		/*
				    		int modulus = decompressed_current_bitlength % 8;
				            if(modulus != 0)
				            	System.out.println("Segment " + i + " is uneven.");
				            else
				            	System.out.println("Segment " + i + " is even.");
				            modulus = decompressed_next_bitlength % 8;
				            if(modulus != 0)
				            	System.out.println("Segment " + (i + 1) + " is uneven.");
				            else
				            	System.out.println("Segment " + (i + 1) + " is even.");
				            modulus = decompressed_combined_bitlength % 8;
				            if(modulus != 0)
				            	System.out.println("Combined segment is uneven.");
				            else
				            	System.out.println("Combined segment is even.");
				            */
				            
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
		  
			System.out.println("Finished merge.");
			
			// Scan merged list.
			
			int segmented_bitlength = 0;
		    current_number_of_segments = merged_list.size();
		    if(merged_list.size() < segment_list.size())
		    {
			    segment_list.clear();
			    for(int i = 0; i < merged_list.size(); i++)
			    {
			        byte [] current_segment = (byte [])	merged_list.get(i); 
			        
			        int current_iterations = StringMapper.getIterations(current_segment);
			        if(current_iterations == 0 || current_iterations == 16)
			        {
			        	int current_bitlength = StringMapper.getBitlength(current_segment);
			        	if(current_bitlength % 8 != 0)
			        	{
			        		System.out.println("Uncompressed segment at index " + i + " has uneven bit length.");
			        		System.out.println("There are " + current_number_of_segments + " segments.");
			        		segmented_bitlength += current_bitlength;
			        	}
			        }
			        else
			        {
			        	if(current_iterations < 16)
			    		{
			    			byte [] decompressed_current_segment = StringMapper.decompressZeroStrings(current_segment);
			    			int current_bitlength = StringMapper.getBitlength(decompressed_current_segment);
			    			if(current_bitlength % 8 != 0)
				        	{
				        		System.out.println("Compressed 0 segment at index " + i + " has uneven bit length.");
				        		System.out.println("There are " + current_number_of_segments + " segments.");
				        	}
			    			segmented_bitlength += current_bitlength;
			    		}
			    		else
			    		{
			    			byte [] decompressed_current_segment = StringMapper.decompressOneStrings(current_segment);
			    			int current_bitlength = StringMapper.getBitlength(decompressed_current_segment);
			    			if(current_bitlength % 8 != 0)
				        	{
				        		System.out.println("Compressed 1 segment at index " + i + " has uneven bit length.");
				        		System.out.println("There are " + current_number_of_segments + " segments.");
				        	}
			    			segmented_bitlength += current_bitlength;
			    		}
			        }
			        segment_list.add(current_segment);
			    }
			    System.out.println("Scanned merge list.");
			    System.out.println("Segmented bitlength is " + segmented_bitlength);
		    }
		    System.out.println();
		    
		}
		
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
				int compressed_bit_length = 0;
				if (string_type == 0)
				{
					compressed_bit_length = StringMapper.compressZeroStrings(segment, current_bit_length, compressed_segment);
					/*
					int bitlength2        = StringMapper.compressZeroStrings(segment, current_bit_length, compressed_segment);
					
					if(compressed_bit_length != bitlength2)
						System.out.println("Length from compresszero1 " + bitlength2 + " is not equal to length from compresszero2 " + compressed_bit_length);
					*/
				}  
				else
				{
					compressed_bit_length = StringMapper.compressOneStrings(segment, current_bit_length, compressed_segment);
					/*
					int bitlength2        = StringMapper.compressOneStrings(segment, current_bit_length, compressed_segment);
					if(compressed_bit_length != bitlength2)
						System.out.println("Length from compressone1 " + bitlength2 + " is not equal to length from compressone2 " + compressed_bit_length);
					*/
					
				}
				int compressed_byte_length = compressed_bit_length / 8;
				if (compressed_bit_length % 8 != 0)
					compressed_byte_length++;
				
				// Add the byte for iterations not included in the bit length.
				compressed_byte_length++;

				byte[] clipped_string = new byte[compressed_byte_length];
				for (int j = 0; j < compressed_byte_length; j++)
					clipped_string[j] = compressed_segment[j];
				compressed_data.add(clipped_string);
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
						byte[] compressed_merged_segment = new byte[2 * byte_length];
						
						int merged_compression_length = 0;
						try 
						{
							if(current_string_type == 0)
								merged_compression_length = StringMapper.compressZeroStrings(merged_segment, merged_uncompressed_length, compressed_merged_segment);
							else
								merged_compression_length = StringMapper.compressOneStrings(merged_segment, merged_uncompressed_length, compressed_merged_segment);
						} 
						catch (Exception e) 
						{
							System.out.println(e.toString());
							System.out.println("Exception compressing segments in getMergedSegmentedData.");
							System.out.println("Current index is " + i);
							System.out.println("The byte length is " + byte_length);
							System.out.println("The input buffer length is " + merged_segment.length);
							System.out.println("The output buffer length is " + compressed_merged_segment.length);
							System.out.println("The string type is " + current_string_type);
							System.out.println("The bit length being compressed is " + merged_uncompressed_length);
							System.out.println();
						}
						
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
		
		/*
		System.out.println("The number of segments in initial list is " + initial_number_of_segments);
		System.out.println("Number of iterations merging segments was " + iterations);
		System.out.println("Number of merged segments was " + previous_compressed_data.size());
		System.out.println("Minimum segment byte length was " + (segment_bit_length / 8));
		System.out.println("Maximum segment byte length was " + max_segment_byte_length);
		System.out.println("Number of zero segments is " + number_of_zero_segments);
		System.out.println("Number of one segments is " + number_of_one_segments);
		System.out.println("Number of compressed segments is " + number_of_compressed_segments);
		System.out.println("Number of uncompressed segments is " + number_of_uncompressed_segments);
		System.out.println();
		*/
		
		ArrayList segment_data = new ArrayList();
		segment_data.add(previous_compressed_length);
		segment_data.add(previous_compressed_data);
		segment_data.add(max_segment_byte_length);
		return segment_data;
	}
}