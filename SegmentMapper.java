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
				int number_of_iterations = StringMapper.getIterations(compressed_segment);
			}
			else
			{
				byte [] compressed_segment = StringMapper.compressOneStrings(segment);
				if(compressed_segment.length < segment.length)
					segment_list.set(i, compressed_segment);
				int number_of_iterations = StringMapper.getIterations(compressed_segment);
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
				    	    	//System.out.println("Segments "  + i + " and " + (i + 1) + " combined are uneven");
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
		
		int size = segment_list.size();
		
		if(size == 1)
		{
		    segment_list.clear();
		    segment_list.add(string);
		    list.add(segment_list);
		    list.add(string.length - 1);
		    return list;
		}
		else
		{
			boolean no_compression = true;
			
			for(int i = 0; i < size; i++)
			{
				byte [] current_segment = (byte [])segment_list.get(i);
				int current_iterations  = StringMapper.getIterations(current_segment);
				if(current_iterations != 0 && current_iterations != 16)
					no_compression = false;
			}
			if(no_compression)
			{
				segment_list.clear();
			    segment_list.add(string);
			    list.add(segment_list);
			    list.add(string.length - 1);
			    return list;	
			}
			else
			{
				list.add(segment_list);
				list.add(max_segment_bytelength);
				return list;
			}
		}
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
	* Produces a set of masks that can be anded to isolate a leading segment.
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
}