import java.util.*;
import java.util.zip.*;
import java.lang.Math.*;
import java.math.*;

public class SegmentMapper
{
	
	// This function returns a segment list with regular lengths.
    public static ArrayList getSegmentedData(byte [] string, int string_bit_length, int segment_bit_length)
    {
    	int number_of_segments       = string_bit_length / segment_bit_length;
    	int segment_byte_length      = segment_bit_length / 8;
    	int remainder                = string_bit_length % segment_bit_length;
    	int last_segment_bit_length  = segment_bit_length + remainder;
    	int last_segment_byte_length = segment_byte_length + remainder / 8;
    	if(remainder % 8 != 0)
    		last_segment_byte_length++;   
    	
    	int max_segment_byte_length = last_segment_byte_length;
    	
    	
    	ArrayList compressed_length = new ArrayList();
    	ArrayList compressed_data   = new ArrayList();
    	
    	
    	int     current_byte_length = segment_byte_length;
    	int     current_bit_length  = segment_bit_length;
    	byte [] segment             = new byte[current_byte_length];
    	byte [] compressed_segment  = new byte[2 * current_byte_length];
    	
    	for(int i = 0; i < number_of_segments; i++)
        {
    		
    	    if(i == number_of_segments - 1)	
    	    {
    	    	current_byte_length = last_segment_byte_length;
    	    	current_bit_length  = last_segment_bit_length;
    	    	segment             = new byte[current_byte_length];
    	    	compressed_segment  = new byte[2 * current_byte_length];
    	    }
    	    for(int j = 0; j < current_byte_length; j++)
                segment[j] = string[i * segment_byte_length + j];
    	    
    	    double zero_ratio = DeltaMapper.getZeroRatio(segment, current_bit_length);
    	    int    compression_amount = 0;
    	    if(zero_ratio >= .5)
    	    	compression_amount = DeltaMapper.getCompressionAmount(segment, current_bit_length, 0);
    	    else
    	    	compression_amount = DeltaMapper.getCompressionAmount(segment, current_bit_length, 1);
    	    if(compression_amount >= 0)
    	    {
    	    	// Add 0 iterations to uncompressed segment.
        		byte [] clipped_string = new byte[current_byte_length + 1];
        		for(int j = 0; j < current_byte_length; j++)
        			clipped_string[j] = segment[j];
        		clipped_string[current_byte_length] = 0;
    			compressed_length.add(current_bit_length);
    			compressed_data.add(clipped_string);	
    	    }
    	    else
    	    {
    	    	int compressed_bit_length = 0;
    	    	if(zero_ratio >= .5)
    	    		compressed_bit_length =  DeltaMapper.compressZeroStrings2(segment, current_bit_length, compressed_segment);
    	    	else
    	    		compressed_bit_length =  DeltaMapper.compressOneStrings2(segment, current_bit_length, compressed_segment);	
    	    	int compressed_byte_length = compressed_bit_length / 8;
        		if(compressed_bit_length % 8 != 0)
        			compressed_byte_length++;
        		compressed_byte_length++;
        	
        		// We're over expanding the compressed string
        		// with extra trailing bits because of odd
        		// stop bits at the end of strings in the recursion.
        		
        		// It mostly happens with binary images,
        		// which makes sense because they are mostly likely
        		// to produce extra zero bits in the recursive process.
        		// It also happens when there are more segments, 
        		// which makes sense because there are more opportunities
        		// to produce extra trailing bits.
        		
        		//compressed_byte_length += 2;
        		
        		// We'll just catch the exception instead of trying to 
        		// avoid it, since the program has seemingly processed
        		// the data correctly when it runs out of memory.
        		// The error seems to be relegated to
        		// low resolution images with lots of segments, probably
        		// for the reasons described above.
        		
        		// I'm not sure if there's any logic related directly to 
        		// the length of the clipped string in the rest of the program.
        		// Best practice is to avoid relying on it.
        		
        		byte [] clipped_string = new byte[compressed_byte_length];
        		for(int j = 0; j < compressed_byte_length; j++)
        			clipped_string[j] = compressed_segment[j];
        		compressed_data.add(clipped_string);
        		compressed_length.add(compressed_bit_length);
    	    }
        }
    	
    	ArrayList segment_data = new ArrayList();
    	segment_data.add(compressed_length);
    	segment_data.add(compressed_data);
    	segment_data.add(max_segment_byte_length);
    	return segment_data;
    }
	
    public static ArrayList getMergedSegmentedData(byte [] string, int string_bit_length, int segment_bit_length)
    {
    	int number_of_segments       = string_bit_length / segment_bit_length;
    	int segment_byte_length      = segment_bit_length / 8;
    	int remainder                = string_bit_length % segment_bit_length;
    	int last_segment_bit_length  = segment_bit_length + remainder;
    	int last_segment_byte_length = segment_byte_length + remainder / 8;
    	if(remainder % 8 != 0)
    		last_segment_byte_length++;   
    	
    	int max_segment_byte_length = last_segment_byte_length;
    	
    	
    	ArrayList compressed_length = new ArrayList();
    	ArrayList compressed_data   = new ArrayList();
    	ArrayList compressed_type   = new ArrayList();
    	
    	
    	int     current_byte_length = segment_byte_length;
    	int     current_bit_length  = segment_bit_length;
    	byte [] segment             = new byte[current_byte_length];
    	byte [] compressed_segment  = new byte[2 * current_byte_length];
    	
    	for(int i = 0; i < number_of_segments; i++)
        {
    	    if(i == number_of_segments - 1)	
    	    {
    	    	current_byte_length = last_segment_byte_length;
    	    	current_bit_length  = last_segment_bit_length;
    	    	segment             = new byte[current_byte_length];
    	    	compressed_segment  = new byte[2 * current_byte_length];
    	    }
    	    for(int j = 0; j < current_byte_length; j++)
                segment[j] = string[i * segment_byte_length + j];
    	    
    	    double zero_ratio = DeltaMapper.getZeroRatio(segment, current_bit_length);
    	    int string_type = 0;
    	    if(zero_ratio < .5)
    	        string_type++;
    	    compressed_type.add(string_type);
    	    
    	    int    compression_amount = 0;
    	    if(string_type == 0)
    	    	compression_amount = DeltaMapper.getCompressionAmount(segment, current_bit_length, 0);
    	    else
    	    	compression_amount = DeltaMapper.getCompressionAmount(segment, current_bit_length, 1);
    	    if(compression_amount >= 0)
    	    {
        		byte [] clipped_string = new byte[current_byte_length + 1];
        		for(int j = 0; j < current_byte_length; j++)
        			clipped_string[j] = segment[j];
        		clipped_string[current_byte_length] = 0;
    			compressed_length.add(current_bit_length);
    			compressed_data.add(clipped_string);	
    	    }
    	    else
    	    {
    	    	int compressed_bit_length = 0;
    	    	if(string_type == 0)
    	    		compressed_bit_length =  DeltaMapper.compressZeroStrings2(segment, current_bit_length, compressed_segment);
    	    	else
    	    		compressed_bit_length =  DeltaMapper.compressOneStrings2(segment, current_bit_length, compressed_segment);	
    	    	int compressed_byte_length = compressed_bit_length / 8;
        		if(compressed_bit_length % 8 != 0)
        			compressed_byte_length++;
        		// Add the byte for iterations not included in the bit length.
        		compressed_byte_length++;
        	
        		byte [] clipped_string = new byte[compressed_byte_length];
        		for(int j = 0; j < compressed_byte_length; j++)
        			clipped_string[j] = compressed_segment[j];
        		compressed_data.add(clipped_string);
        		compressed_length.add(compressed_bit_length);
    	    }
        }
    	
    	// Finished constructing initial list.
    	
    	
    	
    	
    	
    	// Merging segments.
    	
    	ArrayList previous_type    = (ArrayList)compressed_type.clone();
    	ArrayList previous_length  = (ArrayList)compressed_length.clone();
    	ArrayList previous_data    = (ArrayList)compressed_data.clone();
    	
    	ArrayList current_type     = new ArrayList();
    	ArrayList current_length   = new ArrayList();
    	ArrayList current_data     = new ArrayList();
    	
        int       current_number_of_segments  = compressed_data.size();
        int       previous_number_of_segments = 0;
       
        int current_offset = 0;
        while(current_number_of_segments != previous_number_of_segments)
        {
        	previous_number_of_segments = previous_data.size();
        	
        	int i = 0;
            current_offset = 0;
            
        	for(i = 0; i < previous_number_of_segments - 1; i++)
        	{
        		int current_string_type   = (int)previous_type.get(i);
        		int next_string_type      = (int)previous_type.get(i + 1);
        		int current_string_length = (int)previous_length.get(i);
        		int next_string_length    = (int)previous_length.get(i + 1);
        		byte [] current_string    = (byte [])previous_data.get(i);
        		byte [] next_string       = (byte [])previous_data.get(i + 1);
        	    int current_iterations    = DeltaMapper.getIterations(current_string, current_string_length);
        	    int next_iterations       = DeltaMapper.getIterations(next_string,    next_string_length);
        	    
        	    if(current_string_type == next_string_type && current_iterations > 0 && next_iterations > 0)
        	    {
                   int merged_length  = current_string_length + next_string_length;
        	    	
        	    	int byte_offset = current_offset / 8;
        	    	int byte_length = merged_length / 8;
        	    	if(merged_length % 8 != 0)
        	    		byte_length++;
        	    	if(merged_length % 8 != 0  && i != previous_number_of_segments - 2)
        	    		System.out.println("Merged length not evenly divisible by 8 at index = " + i);
        	    	if(byte_length > max_segment_byte_length)
        	    		max_segment_byte_length = byte_length;
        	    	
        	    	byte [] merged_segment = new byte[byte_length];
        	    	for(int j = 0; j < byte_length; j++)
        	    		merged_segment[j] = string[j + byte_offset];
        	    	byte [] compressed_merged_segment = new byte[2 * byte_length];
        	    	int merged_compression_length = 0;
        	    	if(current_string_type == 0)
        	    	    merged_compression_length = DeltaMapper.compressZeroStrings2(merged_segment, merged_length, compressed_merged_segment);
        	    	else
        	    		merged_compression_length = DeltaMapper.compressOneStrings2(merged_segment, merged_length, compressed_merged_segment);
        	    	
        	    	if(merged_compression_length <= current_string_length + next_string_length)
        	    	{
        	    		int compressed_byte_length = merged_compression_length / 8;
        				if(merged_compression_length % 8 != 0)
        				    compressed_byte_length++;
        				compressed_byte_length++;
        				byte [] segment_string = new byte[compressed_byte_length];
        				for(int j = 0; j < compressed_byte_length; j++)
        					segment_string[j] = compressed_merged_segment[j];
        	    		
        	    		current_type.add(DeltaMapper.getStringType(segment_string, merged_compression_length));
        	    		current_length.add(merged_compression_length);
        	    		current_data.add(segment_string);
        	    
        	    		
        	    		current_offset += merged_length;
            	        
        	    		i++;
        	    	}
        	    	else
        	    	{
        	    		current_type.add(current_string_type);
            	    	current_length.add(current_string_length);
            	    	current_data.add(current_string);
            	    	current_offset += current_string_length;
            	    	
            	    	//i++;
        	    	}
        	    }
        	    else if(current_iterations == 0 && next_iterations == 0)
        	    {
                    int merged_length  = current_string_length + next_string_length;
        	    	
        	    	int byte_offset = current_offset / 8;
        	    	int byte_length = merged_length / 8;
        	    	if(merged_length % 8 != 0)
        	    		byte_length++;
        	    	
        	    	if(merged_length % 8 != 0  && i != previous_number_of_segments - 2)
        	    		System.out.println("Merged length not evenly divisible by 8 at index = " + i);
        	    	byte [] merged_segment = new byte[byte_length];
        	    	for(int j = 0; j < byte_length; j++)
        	    		merged_segment[j] = string[j + byte_offset];   
        	    	
        	    	byte_length = merged_length / 8;
    	    		if(merged_length % 8 != 0)
    	    			byte_length++;
    	    		byte_length++;
    	    		
    	    		// Keep track of maximum segment length.
    	    		if(byte_length > max_segment_byte_length)
    	    			max_segment_byte_length = byte_length;
    	    	   
    	    		byte [] segment_string    = new byte[byte_length];
            	    for(int j = 0; j < byte_length - 1; j++)
            		    segment_string[j] = merged_segment[j];
            	    segment_string[merged_segment.length] = 0;
            	    
            	    int merged_type = DeltaMapper.getStringType(segment_string, merged_length);
            	    current_type.add(merged_type);
            	    current_length.add(merged_length);
            	    current_data.add(segment_string);
            	 
    	    		current_offset += merged_length;
    	    		i++;	
        	    }
        	    else
        	    {
        	    	current_type.add(current_string_type);
        	    	current_length.add(current_string_length);
        	    	current_data.add(current_string);
        	    	current_offset += current_string_length;
        	    	
        	    	//i++;
        	    }
        	}
        	
        	previous_type = (ArrayList)current_type.clone();
        	previous_length = (ArrayList)current_length.clone();
        	previous_data = (ArrayList)current_data.clone();
        	
        	current_type.clear();
        	current_length.clear();
        	current_data.clear();
        }
    	
    	ArrayList segment_data = new ArrayList();
    	segment_data.add(compressed_length);
    	segment_data.add(compressed_data);
    	segment_data.add(max_segment_byte_length);
    	return segment_data;
    }
	
    // This function merges adjacent segments with the same bit type if they both compress, or if 
    // both don't compress regardless of bit_type.
    public static ArrayList getSegmentData2(byte [] string, int string_bit_length, int segment_bit_length)
    {
    	int number_of_segments       = string_bit_length / segment_bit_length;
    	int segment_byte_length      = segment_bit_length / 8;
    	int remainder                = string_bit_length % segment_bit_length;
    	int last_segment_bit_length  = segment_bit_length + remainder;
    	int last_segment_byte_length = segment_byte_length + remainder / 8;
    	if(remainder % 8 != 0)
    		last_segment_byte_length++;   
    	int max_segment_byte_length = last_segment_byte_length;
    	ArrayList init_list        = new ArrayList();
    	byte [] processed_segment  = new byte[2 * last_segment_byte_length];
        byte [] compressed_segment = new byte[2 * last_segment_byte_length];
        for(int i = 0; i < number_of_segments - 1; i++)
        {
            byte [] segment = new byte[segment_byte_length];	
           
            
            for(int j = 0; j < segment_byte_length; j++)
                segment[j] = string[i * segment_byte_length + j];
            
            double zero_ratio = DeltaMapper.getZeroRatio(segment, segment_bit_length);
           
            if(zero_ratio >= .5)
            {
            	int compression_amount = DeltaMapper.getCompressionAmount(segment, segment_bit_length, 0);
            	
            	if(compression_amount < 0)
            	{
            		int compression_length = DeltaMapper.compressZeroStrings2(segment, segment_bit_length, compressed_segment);
            		int byte_length = compression_length / 8 + 1;
            		if(compression_length % 8 != 0)
            			byte_length++;
            		byte [] clipped_string = new byte[byte_length];
            		for(int j = 0; j < byte_length; j++)
            			clipped_string[j] = compressed_segment[j];
            		ArrayList segment_list = new ArrayList();
            		segment_list.add(segment_bit_length);
            		segment_list.add(compression_length);
            		segment_list.add(clipped_string);
            		init_list.add(segment_list);
            	}
            	else
            	{
            		byte [] clipped_string = new byte[segment_byte_length + 1];
            		for(int j = 0; j < segment_byte_length; j++)
            			clipped_string[j] = segment[j];
            		clipped_string[segment_byte_length] = 0;
            		ArrayList segment_list = new ArrayList();
            		segment_list.add(segment_bit_length);
            		segment_list.add(segment_bit_length);
            		segment_list.add(clipped_string);
            		init_list.add(segment_list);
            	}
            }
            else
            {
            	int compression_amount = DeltaMapper.getCompressionAmount(segment, segment_bit_length, 1);
            	
            	if(compression_amount < 0)
            	{
            		int compression_length = DeltaMapper.compressOneStrings2(segment, segment_bit_length, compressed_segment);
            		int byte_length = compression_length / 8 + 1;
            		if(compression_length % 8 != 0)
            			byte_length++;
            		byte_length += 2;
            		byte [] clipped_string = new byte[byte_length];
            		for(int j = 0; j < byte_length; j++)
            			clipped_string[j] = compressed_segment[j];
            		ArrayList segment_list = new ArrayList();
            		segment_list.add(segment_bit_length);
            		segment_list.add(compression_length);
            		segment_list.add(clipped_string);
            		init_list.add(segment_list);
            	}
            	else
            	{
            		byte [] clipped_string = new byte[segment_byte_length + 1];
            		for(int j = 0; j < segment_byte_length; j++)
            			clipped_string[j] = segment[j];
            		clipped_string[segment_byte_length] = 0;
            		ArrayList segment_list = new ArrayList();
            		segment_list.add(segment_bit_length);
            		segment_list.add(segment_bit_length);
            		segment_list.add(clipped_string);
            		init_list.add(segment_list);
            	}	
            }
        }
    	
    	int i = number_of_segments - 1;
    	byte [] segment = new byte[last_segment_byte_length];	
    
        
        for(int j = 0; j < last_segment_byte_length; j++)
        {
            segment[j] = string[i * segment_byte_length + j];
        }
        
        double zero_ratio = DeltaMapper.getZeroRatio(segment, last_segment_bit_length);
       
        if(zero_ratio >= .5)
        {
        	int compression_amount = DeltaMapper.getCompressionAmount(segment, last_segment_bit_length, 0);
        	if(compression_amount < 0)
        	{
        		int compression_length = DeltaMapper.compressZeroStrings2(segment, last_segment_bit_length, compressed_segment);
        		
        		int byte_length = compression_length / 8 + 1;
        		if(compression_length % 8 != 0)
        			byte_length++;
        		byte_length += 2;
        		byte [] clipped_string = new byte[byte_length];
        		for(int j = 0; j < byte_length; j++)
        			clipped_string[j] = compressed_segment[j];
        		ArrayList segment_list = new ArrayList();
        		segment_list.add(segment_bit_length);
        		segment_list.add(compression_length);
        		segment_list.add(clipped_string);
        		init_list.add(segment_list);
    			
    			
        	}
        	else
        	{
        		byte [] clipped_string = new byte[last_segment_byte_length + 1];
        		for(int j = 0; j < last_segment_byte_length; j++)
        			clipped_string[j] = segment[j];
        		clipped_string[last_segment_byte_length] = 0;
        		ArrayList segment_list = new ArrayList();
        		segment_list.add(segment_bit_length);
        		segment_list.add(segment_bit_length);
        		segment_list.add(clipped_string);
        		init_list.add(segment_list);
        	}
        }
        else
        {
        	int compression_amount = DeltaMapper.getCompressionAmount(segment, last_segment_bit_length, 1);
        	if(compression_amount < 0)
        	{
        		int compression_length = DeltaMapper.compressOneStrings2(segment, last_segment_bit_length, compressed_segment);
        		int byte_length = compression_length / 8 + 1;
        		if(compression_length % 8 != 0)
        			byte_length++;
        		byte_length += 2;
        		byte [] clipped_string = new byte[byte_length];
        		for(int j = 0; j < byte_length; j++)
        			clipped_string[j] = compressed_segment[j];
        		ArrayList segment_list = new ArrayList();
        		segment_list.add(last_segment_bit_length);
        		segment_list.add(compression_length);
        		segment_list.add(clipped_string);
        		init_list.add(segment_list);
        	}
        	else
        	{
        		byte [] clipped_string = new byte[last_segment_byte_length + 1];
        		for(int j = 0; j < last_segment_byte_length; j++)
        			clipped_string[j] = segment[j];
        		clipped_string[last_segment_byte_length] = 0;
        		ArrayList segment_list = new ArrayList();
        		segment_list.add(last_segment_bit_length);
        		segment_list.add(last_segment_bit_length);
        		segment_list.add(clipped_string);
        		init_list.add(segment_list);
        	}	
        }
      
    	// Finished constructing the initial list.
    	
        // Merge similar segments that are adjacent.
        
        ArrayList previous_list               = new ArrayList();
        ArrayList current_list                = new ArrayList();
        int       current_number_of_segments  = init_list.size();
        int       previous_number_of_segments = 0;
       
        for(i = 0; i < init_list.size(); i++)
        {
        	ArrayList segment_list = (ArrayList)init_list.get(i);
        	previous_list.add(segment_list);
        }
        
        int current_offset = 0;
        while(current_number_of_segments != previous_number_of_segments)
        {
        	previous_number_of_segments = previous_list.size();
        	current_list.clear();
        
            i = 0;
            current_offset = 0;
            
        	for(i = 0; i < previous_number_of_segments - 1; i++)
        	{
        	    ArrayList current_segment_list = (ArrayList)previous_list.get(i);
        	    ArrayList next_segment_list    = (ArrayList)previous_list.get(i + 1);
        	    
        	    int     current_length   = (int)    current_segment_list.get(0);
        	    int     next_length      = (int)    next_segment_list.get(0);
        	    int     current_t_length = (int)    current_segment_list.get(1);
        	    int     next_t_length    = (int)    next_segment_list.get(1);
        	    byte [] current_string   = (byte [])current_segment_list.get(2);
        	    byte [] next_string      = (byte [])next_segment_list.get(2);
        	    
        	    int current_type       = DeltaMapper.getStringType(current_string, current_t_length);
        	    int next_type          = DeltaMapper.getStringType(next_string,    next_t_length);
        	    int current_iterations = DeltaMapper.getIterations(current_string, current_t_length);
        	    int next_iterations    = DeltaMapper.getIterations(next_string,    next_t_length);
        	    
        	    if(current_type == next_type && current_iterations > 0 && next_iterations > 0)
        	    {
        	    	int merged_length  = current_length + next_length;
        	    	
        	    	int byte_offset = current_offset / 8;
        	    	int byte_length = merged_length / 8;
        	    	if(merged_length % 8 != 0)
        	    		byte_length++;
        	    	if(merged_length % 8 != 0  && i != previous_number_of_segments - 2)
        	    		System.out.println("Merged length not evenly divisible by 8 at index = " + i);
        	    	if(byte_length > max_segment_byte_length)
        	    		max_segment_byte_length = byte_length;
        	    	
        	    	byte [] merged_segment = new byte[byte_length];
        	    	for(int j = 0; j < byte_length; j++)
        	    		merged_segment[j] = string[j + byte_offset];
        	    	byte [] compressed_merged_segment = new byte[2 * byte_length];
        	    	int merged_compression_length = 0;
        	    	if(current_type == 0)
        	    	    merged_compression_length = DeltaMapper.compressZeroStrings2(merged_segment, merged_length, compressed_merged_segment);
        	    	else
        	    		merged_compression_length = DeltaMapper.compressOneStrings2(merged_segment, merged_length, compressed_merged_segment);
        	    	
        	    	if(merged_compression_length <= current_t_length + next_t_length)
        	    	{
        	    		int compressed_byte_length = merged_compression_length / 8;
        				if(merged_compression_length % 8 != 0)
        				    compressed_byte_length++;
        				compressed_byte_length++;
        				byte [] segment_string = new byte[compressed_byte_length];
        				for(int j = 0; j < compressed_byte_length; j++)
        					segment_string[j] = compressed_merged_segment[j];
        	    		
        	    		ArrayList merged_segment_list = new ArrayList();
        	    		merged_segment_list.add(merged_length);
        	    		merged_segment_list.add(merged_compression_length);
        	    		merged_segment_list.add(segment_string);
        	    
        	    		current_list.add(merged_segment_list);
        	    		
        	    		current_offset += merged_length;
            	        
        	    		i++;
        	    	}
        	    	else
        	    	{
        	    		current_list.add(current_segment_list);
        	    		current_offset += current_length;
        	    	}
        	    }
        	    else if(current_iterations == 0 && next_iterations == 0)
        	    {
        	    	int merged_length  = current_length + next_length;
        	    	
        	    	int byte_offset = current_offset / 8;
        	    	int byte_length = merged_length / 8;
        	    	if(merged_length % 8 != 0)
        	    		byte_length++;
        	    	
        	    	if(merged_length % 8 != 0  && i != previous_number_of_segments - 2)
        	    		System.out.println("Merged length not evenly divisible by 8 at index = " + i);
        	    	byte [] merged_segment = new byte[byte_length];
        	    	for(int j = 0; j < byte_length; j++)
        	    		merged_segment[j] = string[j + byte_offset];   
        	    	
        	    	byte_length = merged_length / 8;
    	    		if(merged_length % 8 != 0)
    	    			byte_length++;
    	    		byte_length++;
    	    		
    	    		if(byte_length > max_segment_byte_length)
    	    			max_segment_byte_length = byte_length;
    	    	   
    	    		byte [] segment_string    = new byte[byte_length];
            	    for(int j = 0; j < byte_length - 1; j++)
            		    segment_string[j] = merged_segment[j];
            	    segment_string[merged_segment.length] = 0;
            	    
            	    ArrayList merged_segment_list = new ArrayList();
    	    		merged_segment_list.add(merged_length);
    	    		merged_segment_list.add(merged_length);
    	    		merged_segment_list.add(segment_string);
    	    		
    	    		current_list.add(merged_segment_list);
    	    		
    	    		current_offset += merged_length;
    	    		i++;
        	    }
        	    else
        	    {
        	    	current_list.add(current_segment_list);
        	    	current_offset += current_length;
        	    }
        	}
        	
    	    if(i == previous_number_of_segments - 1)
    	    {
    		    ArrayList current_segment_list = (ArrayList)previous_list.get(i);
    		    current_list.add(current_segment_list);
    	    }
    	
    	    previous_list.clear();
    	   
    	    current_number_of_segments = current_list.size();
    	   
    	    for(i = 0; i < current_number_of_segments; i++)
    	    {
    		    ArrayList segment_list = (ArrayList)current_list.get(i);
    		    previous_list.add(segment_list);
    	    }
    	    // Previous list now has current list data.  
        }
        
		ArrayList compressed_length = new ArrayList();
    	ArrayList compressed_data   = new ArrayList();
		for(i = 0; i < current_list.size(); i++)
		{
			ArrayList segment_list = (ArrayList)current_list.get(i);
			int length   = (int)segment_list.get(1);
			byte [] data = (byte [])segment_list.get(2);
			compressed_length.add(length);
			compressed_data.add(data);
		}
		
		ArrayList segment_data = new ArrayList();
    	segment_data.add(compressed_length);
    	segment_data.add(compressed_data);
    	segment_data.add(max_segment_byte_length + 1);
    	return segment_data;
    }
    
    

    // This function merges adjacent segments with the same bit type if they both compress, or if 
    // both don't compress regardless of bit_type.  It then checks to see if merged segments that didn't 
    // compress separately, compress after being merged.  This happens fairly frequently, but 
    // presumably is a single iteration with a modest amount of compression--worth looking into,
    // especially since it is not intuitive that that could happen.
    public static ArrayList getSegmentData3(byte [] string, int string_bit_length, int segment_bit_length)
    {
    	boolean debug1 = false;
    	boolean debug2 = true;
    	
    	int number_of_segments       = string_bit_length / segment_bit_length;
    	int segment_byte_length      = segment_bit_length / 8;
    	int remainder                = string_bit_length % segment_bit_length;
    	int last_segment_bit_length  = segment_bit_length + remainder;
    	int last_segment_byte_length = segment_byte_length + remainder / 8;
    	if(remainder % 8 != 0)
    		last_segment_byte_length++;   
    	
    	ArrayList init_list        = new ArrayList();
    	
    
        for(int i = 0; i < number_of_segments - 1; i++)
        {
            byte [] segment = new byte[segment_byte_length];	
            byte [] processed_segment  = new byte[2 * segment_byte_length];
            byte [] compressed_segment = new byte[2 * segment_byte_length];
            
            for(int j = 0; j < segment_byte_length; j++)
                segment[j] = string[i * segment_byte_length + j];
            
            double zero_ratio = DeltaMapper.getZeroRatio(segment, segment_bit_length);
           
            if(zero_ratio >= .5)
            {
            	int compression_amount = DeltaMapper.getCompressionAmount(segment, segment_bit_length, 0);
            	
            	if(compression_amount < 0)
            	{
            		int compression_length = DeltaMapper.compressZeroStrings2(segment, segment_bit_length, compressed_segment);
            		int byte_length = compression_length / 8 + 1;
            		if(compression_length % 8 != 0)
            			byte_length++;
            		byte [] clipped_string = new byte[byte_length];
            		for(int j = 0; j < byte_length; j++)
            			clipped_string[j] = compressed_segment[j];
            		ArrayList segment_list = new ArrayList();
            		segment_list.add(segment_bit_length);
            		segment_list.add(compression_length);
            		segment_list.add(clipped_string);
            		init_list.add(segment_list);
            	}
            	else
            	{
            		byte [] clipped_string = new byte[segment_byte_length + 1];
            		for(int j = 0; j < segment_byte_length; j++)
            			clipped_string[j] = segment[j];
            		clipped_string[segment_byte_length] = 0;
            		ArrayList segment_list = new ArrayList();
            		segment_list.add(segment_bit_length);
            		segment_list.add(segment_bit_length);
            		segment_list.add(clipped_string);
            		init_list.add(segment_list);
            	}
            }
            else
            {
            	int compression_amount = DeltaMapper.getCompressionAmount(segment, segment_bit_length, 1);
            	
            	if(compression_amount < 0)
            	{
            		int compression_length = DeltaMapper.compressOneStrings2(segment, segment_bit_length, compressed_segment);
            		int byte_length = compression_length / 8 + 1;
            		if(compression_length % 8 != 0)
            			byte_length++;
            		byte [] clipped_string = new byte[byte_length];
            		for(int j = 0; j < byte_length; j++)
            			clipped_string[j] = compressed_segment[j];
            		ArrayList segment_list = new ArrayList();
            		segment_list.add(segment_bit_length);
            		segment_list.add(compression_length);
            		segment_list.add(clipped_string);
            		init_list.add(segment_list);
        			
            	}
            	else
            	{
            		byte [] clipped_string = new byte[segment_byte_length + 1];
            		for(int j = 0; j < segment_byte_length; j++)
            			clipped_string[j] = segment[j];
            		clipped_string[segment_byte_length] = 0;
            		ArrayList segment_list = new ArrayList();
            		segment_list.add(segment_bit_length);
            		segment_list.add(segment_bit_length);
            		segment_list.add(clipped_string);
            		init_list.add(segment_list);
            	}	
            }
        }
    	
    	int i = number_of_segments - 1;
    	byte [] segment = new byte[last_segment_byte_length];	
        byte [] compressed_segment = new byte[2 * last_segment_byte_length];
        byte [] processed_segment  = new byte[2 * last_segment_byte_length];
        
        for(int j = 0; j < last_segment_byte_length; j++)
            segment[j] = string[i * segment_byte_length + j];
        
        double zero_ratio = DeltaMapper.getZeroRatio(segment, last_segment_bit_length);
       
        if(zero_ratio >= .5)
        {
        	int compression_amount = DeltaMapper.getCompressionAmount(segment, last_segment_bit_length, 0);
        	if(compression_amount < 0)
        	{
        		int compression_length = DeltaMapper.compressZeroStrings2(segment, last_segment_bit_length, compressed_segment);
        		
        		int byte_length = compression_length / 8 + 1;
        		if(compression_length % 8 != 0)
        			byte_length++;
        		byte [] clipped_string = new byte[byte_length];
        		for(int j = 0; j < byte_length; j++)
        			clipped_string[j] = compressed_segment[j];
        		ArrayList segment_list = new ArrayList();
        		segment_list.add(segment_bit_length);
        		segment_list.add(compression_length);
        		segment_list.add(clipped_string);
        		init_list.add(segment_list);
    			
    			if(debug1)
    			{
    			    System.out.println("Compression length 0 is " + compression_length);
    			    int decompression_length = DeltaMapper.decompressZeroStrings(compressed_segment, compression_length, processed_segment);
    			    System.out.println("Decompression length 0 is " + decompression_length);
    			    boolean same_string = true;
    			    for(int j = 0; j < segment.length; j++)
    			    {
    				    if(segment[j] != processed_segment[j])
    					    same_string = false;
    			    }
    			    if(!same_string)
    				    System.out.println("Processed data is not the same as original data.");
    			    System.out.println();
    			}
        	}
        	else
        	{
        		byte [] clipped_string = new byte[last_segment_byte_length + 1];
        		for(int j = 0; j < last_segment_byte_length; j++)
        			clipped_string[j] = segment[j];
        		clipped_string[last_segment_byte_length] = 0;
        		ArrayList segment_list = new ArrayList();
        		segment_list.add(segment_bit_length);
        		segment_list.add(segment_bit_length);
        		segment_list.add(clipped_string);
        		init_list.add(segment_list);
        	}
        }
        else
        {
        	int compression_amount = DeltaMapper.getCompressionAmount(segment, last_segment_bit_length, 1);
        	if(compression_amount < 0)
        	{
        		int compression_length = DeltaMapper.compressOneStrings2(segment, last_segment_bit_length, compressed_segment);
        		int byte_length = compression_length / 8 + 1;
        		if(compression_length % 8 != 0)
        			byte_length++;
        		byte [] clipped_string = new byte[byte_length];
        		for(int j = 0; j < byte_length; j++)
        			clipped_string[j] = compressed_segment[j];
        		ArrayList segment_list = new ArrayList();
        		segment_list.add(last_segment_bit_length);
        		segment_list.add(compression_length);
        		segment_list.add(clipped_string);
        		init_list.add(segment_list);
    			
    			if(debug1)
    			{
    				System.out.println("Compression length 1 is " + compression_length);
                    int decompression_length = DeltaMapper.decompressOneStrings(compressed_segment, compression_length, processed_segment);
    			    System.out.println("Decompression length 1 is " + decompression_length);
    			    boolean same_string = true;
    			    for(int j = 0; j < segment.length; j++)
    			    {
    				    if(segment[j] != processed_segment[j])
    					    same_string = false;
    			    }
    			    if(!same_string)
    				    System.out.println("Processed data is not the same as original data.");
    			    System.out.println();
    			}
        	}
        	else
        	{
        		byte [] clipped_string = new byte[last_segment_byte_length + 1];
        		for(int j = 0; j < last_segment_byte_length; j++)
        			clipped_string[j] = segment[j];
        		clipped_string[last_segment_byte_length] = 0;
        		ArrayList segment_list = new ArrayList();
        		segment_list.add(last_segment_bit_length);
        		segment_list.add(last_segment_bit_length);
        		segment_list.add(clipped_string);
        		init_list.add(segment_list);
        	}	
        }
    	
		if(debug2)
		{
		    double zero_ratio_min = 1;
		    double zero_ratio_max = 0.;
		    int    min_iterations = Integer.MAX_VALUE;
		    int    max_iterations = 0;
		    int    number_of_zero_segments = 0;
		    int    number_of_one_segments  = 0;
		    int    number_of_compressed_segments = 0;
		    int    max_string_type = -1;
		    int    min_string_type = -1;
		
		    for(i = 0; i < init_list.size(); i++)
            {
			    ArrayList segment_list = (ArrayList)init_list.get(i);	
			
			    int length   = (int)segment_list.get(1);
			    byte [] data = (byte [])segment_list.get(2);
			
			
			    zero_ratio = DeltaMapper.getZeroRatio(data, length);
			    if(zero_ratio < zero_ratio_min)
				    zero_ratio_min = zero_ratio;
			    if(zero_ratio > zero_ratio_max)
			        zero_ratio_max = zero_ratio;
			    if(zero_ratio >= .5)
			        number_of_zero_segments++;
			    else
			        number_of_one_segments++;
			    int iterations = DeltaMapper.getIterations(data, length);
			    int string_type = DeltaMapper.getStringType(data, length);
			    if(iterations > max_iterations)
			    {
			        max_iterations = iterations;
			        max_string_type = string_type;
			    }
			    if(iterations < min_iterations)
			    {
			        min_iterations = iterations;
			        min_string_type = string_type;
			    }
			    if(iterations > 0)
			        number_of_compressed_segments++;
			}
    	
		    System.out.println("Initial list has " + init_list.size() + " segments.");
		    System.out.println("Number of compressed segments is " + number_of_compressed_segments);
		    System.out.println("Number of zero segments is " + number_of_zero_segments);
		    System.out.println("Number of one segments is " + number_of_one_segments);
		    System.out.println("Minimum zero ratio is " + String.format("%.4f", zero_ratio_min));
		    System.out.println("Maximum zero ratio is " + String.format("%.4f", zero_ratio_max));
		    System.out.println("Minimum iterations is " + min_iterations);
		    System.out.println("Minimum string type is " + min_string_type);
		    System.out.println("Maximum iterations is " + max_iterations);
		    System.out.println("Maximum string type is " + max_string_type);
		    System.out.println();
		}
		
		
    	// Finished constructing the initial list.
    	
        // Merge similar segments that are adjacent.
        
        ArrayList previous_list               = new ArrayList();
        ArrayList current_list                = new ArrayList();
        int       current_number_of_segments  = init_list.size();
        int       previous_number_of_segments = 0;
       
        for(i = 0; i < init_list.size(); i++)
        {
        	ArrayList segment_list = (ArrayList)init_list.get(i);
        	previous_list.add(segment_list);
        }
        
        int current_offset = 0;
        while(current_number_of_segments != previous_number_of_segments && current_number_of_segments > 1)
        {
        	if(debug1)
        	    System.out.println("Current number of segments is " + current_number_of_segments);
        	previous_number_of_segments = previous_list.size();
        	current_list.clear();
        
            i = 0;
            current_offset = 0;
            
        	for(i = 0; i < previous_number_of_segments - 1; i++)
        	{
        	    ArrayList current_segment_list = (ArrayList)previous_list.get(i);
        	    ArrayList next_segment_list    = (ArrayList)previous_list.get(i + 1);
        	    
        	    int     current_length   = (int)    current_segment_list.get(0);
        	    int     next_length      = (int)    next_segment_list.get(0);
        	    int     current_t_length = (int)    current_segment_list.get(1);
        	    int     next_t_length    = (int)    next_segment_list.get(1);
        	    byte [] current_string   = (byte [])current_segment_list.get(2);
        	    byte [] next_string      = (byte [])next_segment_list.get(2);
        	    
        	    int current_type       = DeltaMapper.getStringType(current_string, current_t_length);
        	    int next_type          = DeltaMapper.getStringType(next_string,    next_t_length);
        	    int current_iterations = DeltaMapper.getIterations(current_string, current_t_length);
        	    int next_iterations    = DeltaMapper.getIterations(next_string,    next_t_length);
        	    
        	    if(current_type == next_type && current_iterations > 0 && next_iterations > 0)
        	    {
        	    	int merged_length  = current_length + next_length;
        	    	
        	    	int byte_offset = current_offset / 8;
        	    	int byte_length = merged_length / 8;
        	    	if(merged_length % 8 != 0)
        	    		byte_length++;
        	    	if(merged_length % 8 != 0  && i != previous_number_of_segments - 2)
        	    		System.out.println("Merged length not evenly divisible by 8 at index = " + i);
        	    	
        	    	byte [] merged_segment = new byte[byte_length];
        	    	for(int j = 0; j < byte_length; j++)
        	    		merged_segment[j] = string[j + byte_offset];
        	    	byte [] compressed_merged_segment = new byte[2 * byte_length];
        	    	processed_segment = new byte[2 * byte_length];
        	    	int merged_compression_length = 0;
        	    	if(current_type == 0)
        	    	{
        	    	    merged_compression_length = DeltaMapper.compressZeroStrings2(merged_segment, merged_length, compressed_merged_segment);
        	    	    if(debug1)
            			{
                			System.out.println("Original length is " + merged_length);
            				System.out.println("Compression length 0 is " + merged_compression_length);
                            int decompression_length = DeltaMapper.decompressZeroStrings(compressed_merged_segment, merged_compression_length, processed_segment);
            			    System.out.println("Decompression length 0 is " + decompression_length);
            			    boolean same_string = true;
            			    for(int j = 0; j < merged_segment.length; j++)
            			    {
            				    if(merged_segment[j] != processed_segment[j])
            					    same_string = false;
            			    }
            			    if(!same_string)
            				    System.out.println("Processed data is not the same as original data.");
            			    int iterations = DeltaMapper.getIterations(compressed_merged_segment, merged_compression_length);
            			    System.out.println("Iterations is " + iterations);
            			    System.out.println();
            			}
        	    	}
        	    	else
        	    	{
        	    		merged_compression_length = DeltaMapper.compressOneStrings2(merged_segment, merged_length, compressed_merged_segment);
        	    		if(debug1)
            			{
                			System.out.println("Original length is " + merged_length);
            				System.out.println("Compression length 1 is " + merged_compression_length);
                            int decompression_length = DeltaMapper.decompressOneStrings(compressed_merged_segment, merged_compression_length, processed_segment);
            			    System.out.println("Decompression length 1 is " + decompression_length);
            			    boolean same_string = true;
            			    for(int j = 0; j < merged_segment.length; j++)
            			    {
            				    if(merged_segment[j] != processed_segment[j])
            					    same_string = false;
            			    }
            			    if(!same_string)
            				    System.out.println("Processed data is not the same as original data.");
            			    int iterations = DeltaMapper.getIterations(compressed_merged_segment, merged_compression_length);
            			    System.out.println("Iterations is " + iterations);
            			    System.out.println();
            			}
        	    	}
        	    	
        	    	if(merged_compression_length <= current_t_length + next_t_length)
        	    	{
        	    		int compressed_byte_length = merged_compression_length / 8;
        				if(merged_compression_length % 8 != 0)
        				    compressed_byte_length++;
        				compressed_byte_length++;
        				byte [] segment_string = new byte[compressed_byte_length];
        				for(int j = 0; j < compressed_byte_length; j++)
        					segment_string[j] = compressed_merged_segment[j];
        	    		
        	    		ArrayList merged_segment_list = new ArrayList();
        	    		merged_segment_list.add(merged_length);
        	    		merged_segment_list.add(merged_compression_length);
        	    		merged_segment_list.add(segment_string);
        	    
        	    		current_list.add(merged_segment_list);
        	    		
        	    		current_offset += merged_length;
            	        
        	    		i++;
        	    		
        	    	}
        	    	else
        	    	{
        	    		current_list.add(current_segment_list);
        	    		current_offset += current_length;
        	    	}
        	    }
        	    else if(current_iterations == 0 && next_iterations == 0)
        	    {
        	    	int merged_length  = current_length + next_length;
        	    	
        	    	int byte_offset = current_offset / 8;
        	    	int byte_length = merged_length / 8;
        	    	if(merged_length % 8 != 0)
        	    		byte_length++;
        	    	
        	    	if(merged_length % 8 != 0  && i != previous_number_of_segments - 2)
        	    		System.out.println("Merged length not evenly divisible by 8 at index = " + i);
        	    	byte [] merged_segment = new byte[byte_length];
        	    	for(int j = 0; j < byte_length; j++)
        	    		merged_segment[j] = string[j + byte_offset];   
        	    	
        	    	// Test to see if the merged segments compress.
        	    	double merged_ratio = DeltaMapper.getZeroRatio(merged_segment, merged_length);
        	    	byte [] compressed_merged_segment = new byte[merged_segment.length * 2];
        	    	int compression_length = 0;
        	    	if(merged_ratio >= .5)
        	    		compression_length = DeltaMapper.compressZeroStrings2(merged_segment, merged_length, compressed_merged_segment);
        	    	else
        	    		compression_length = DeltaMapper.compressOneStrings2(merged_segment, merged_length, compressed_merged_segment);
        	    	
        	    	if(compression_length < merged_length)
        	    	{
        	    		if(debug1)
        	    		    System.out.println("Merged segments that did not compress, compress after merging.");
        	    		
        	    		int compressed_byte_length = compression_length / 8;
        				if(compression_length % 8 != 0)
        				    compressed_byte_length++;
        				compressed_byte_length++;
        				
        				byte [] segment_string = new byte[compressed_byte_length];
        				for(int j = 0; j < compressed_byte_length; j++)
        					segment_string[j] = compressed_merged_segment[j];
        				
        				ArrayList merged_segment_list = new ArrayList();
        	    		merged_segment_list.add(merged_length);
        	    		merged_segment_list.add(compression_length);
        	    		merged_segment_list.add(segment_string);
        	    		
        	    		current_list.add(merged_segment_list);
        	    		current_offset += merged_length;
        	    		i++;
        				
        	    	}
        	    	else
        	    	{
        	    		if(compression_length > merged_length)
        	    			System.out.println("Compression length is an unexpected value.");
        	    		
        	    		byte_length = merged_length / 8;
        	    		if(merged_length % 8 != 0)
        	    			byte_length++;
        	    		byte_length++;
        	    	    byte [] segment_string    = new byte[byte_length];
                	    for(int j = 0; j < byte_length - 1; j++)
                		    segment_string[j] = merged_segment[j];
                	    segment_string[merged_segment.length] = 0;
                	    
                	    ArrayList merged_segment_list = new ArrayList();
        	    		merged_segment_list.add(merged_length);
        	    		merged_segment_list.add(compression_length);
        	    		merged_segment_list.add(segment_string);
        	    		
        	    		current_list.add(merged_segment_list);
        	    		current_offset += merged_length;
        	    		i++;
        	    	}
        	    }
        	    else
        	    {
        	    	current_list.add(current_segment_list);
        	    	current_offset += current_length;
        	    }
        	}
        	

    	    if(i == previous_number_of_segments - 1)
    	    {
    		    ArrayList current_segment_list = (ArrayList)previous_list.get(i);
    		    current_list.add(current_segment_list);
    	    }
    	
    	    previous_list.clear();
    	   
    	    current_number_of_segments = current_list.size();
    	   
    	    for(i = 0; i < current_number_of_segments; i++)
    	    {
    		    ArrayList segment_list = (ArrayList)current_list.get(i);
    		    previous_list.add(segment_list);
    	    }
    	    // Previous list now has current list data.  
        }
        
        ArrayList compressed_length = new ArrayList();
    	ArrayList compressed_data   = new ArrayList();
	
		for(i = 0; i < current_list.size(); i++)
		{
			ArrayList segment_list = (ArrayList)current_list.get(i);
			int length   = (int)segment_list.get(1);
			byte [] data = (byte [])segment_list.get(2);
			compressed_length.add(length);
			compressed_data.add(data);
		}
		
		if(debug2)
		{
		    double zero_ratio_min = 1;
		    double zero_ratio_max = 0.;
		    int min_iterations = Integer.MAX_VALUE;
		    int max_iterations = 0;
		    int number_of_zero_segments = 0;
		    int number_of_one_segments  = 0;
		    int number_of_compressed_segments = 0;
		    int max_string_type = -1;
		    int min_string_type = -1;
		    for(i = 0; i < current_list.size(); i++)
		    {
		        ArrayList segment_list = (ArrayList)current_list.get(i);
		        int length   = (int)segment_list.get(1);
		        byte [] data = (byte [])segment_list.get(2);
			
		        zero_ratio = DeltaMapper.getZeroRatio(data, length);
		        if(zero_ratio < zero_ratio_min)
		            zero_ratio_min = zero_ratio;
		        if(zero_ratio > zero_ratio_max)
		            zero_ratio_max = zero_ratio;
		        if(zero_ratio >= .5)
		            number_of_zero_segments++;
		        else
		            number_of_one_segments++;
		        int iterations  = DeltaMapper.getIterations(data, length);
		        int string_type = DeltaMapper.getStringType(data, length);
		        if(iterations > max_iterations)
		        {
		            max_iterations = iterations;
		            max_string_type = string_type;
		        }
		        if(iterations < min_iterations)
		        {
		            min_iterations = iterations;
		            min_string_type = string_type;
		        }
		        if(iterations > 0)
		            number_of_compressed_segments++;	
		    }
	
		    System.out.println("Merged list has " + current_list.size() + " segments");
		    System.out.println("Number of compressed segments is " + number_of_compressed_segments);
		    System.out.println("Number of zero segments is " + number_of_zero_segments);
		    System.out.println("Number of one segments is " + number_of_one_segments);
		    System.out.println("Minimum zero ratio is " + String.format("%.4f", zero_ratio_min));
		    System.out.println("Maximum zero ratio is " + String.format("%.4f", zero_ratio_max));
		    System.out.println("Minimum iterations is " + min_iterations);
		    System.out.println("Minimum string type is " + min_string_type);
		    System.out.println("Maximum iterations is " + max_iterations);
		    System.out.println("Maximum string type is " + max_string_type);
		    System.out.println();
		}
        
		ArrayList segment_data = new ArrayList();
    	segment_data.add(compressed_length);
    	segment_data.add(compressed_data);
    	return segment_data;
    }
    

    
    // This function returns a segment list with regular lengths.
    public static ArrayList getSegmentData(byte [] string, int string_bit_length, int segment_bit_length)
    {
    	
    	boolean debug1  = false;
    	boolean debug2 = true;
    	
    	int number_of_segments       = string_bit_length / segment_bit_length;
    	int segment_byte_length      = segment_bit_length / 8;
    	int remainder                = string_bit_length % segment_bit_length;
    	int last_segment_bit_length  = segment_bit_length + remainder;
    	int last_segment_byte_length = segment_byte_length + remainder / 8;
    	if(remainder % 8 != 0)
    		last_segment_byte_length++;   
    	
    	int max_segment_byte_length = last_segment_byte_length;
    	
    	
    	ArrayList compressed_length = new ArrayList();
    	ArrayList compressed_data   = new ArrayList();
    	
    	
    	
    	for(int i = 0; i < number_of_segments - 1; i++)
        {
            byte [] segment = new byte[segment_byte_length];	
            byte [] processed_segment  = new byte[2 * segment_byte_length];
            byte [] compressed_segment = new byte[2 * segment_byte_length];
            
            for(int j = 0; j < segment_byte_length; j++)
                segment[j] = string[i * segment_byte_length + j];
            
            double zero_ratio = DeltaMapper.getZeroRatio(segment, segment_bit_length);
           
            if(zero_ratio >= .5)
            {
            	int compression_amount = DeltaMapper.getCompressionAmount(segment, segment_bit_length, 0);
            	
            	if(compression_amount < 0)
            	{
            		int compression_length = DeltaMapper.compressZeroStrings2(segment, segment_bit_length, compressed_segment);
            		
            		
            		int byte_length = compression_length / 8;
            		if(compression_length % 8 != 0)
            			byte_length++;
            		// This might be a bug related to recursion.  We should
            		// only have to increment the byte length by 1.
            		// This seems like a lot of extra trailing bits.
            		// It mostly happens with binary images.
            		byte_length += 3;
            		byte [] clipped_string = new byte[byte_length];
            		for(int j = 0; j < byte_length; j++)
            			clipped_string[j] = compressed_segment[j];
            		compressed_data.add(clipped_string);
            		
            		//compressed_data.add(compressed_segment);
            		compressed_length.add(compression_length);
        			
            	}
            	else
            	{
            		// Add 0 iterations to uncompressed segment.
            		byte [] clipped_string = new byte[segment_byte_length + 1];
            		for(int j = 0; j < segment_byte_length; j++)
            			clipped_string[j] = segment[j];
            		clipped_string[segment_byte_length] = 0;
        			compressed_length.add(segment_bit_length);
        			compressed_data.add(clipped_string);
            	}
            }
            else
            {
            	int compression_amount = DeltaMapper.getCompressionAmount(segment, segment_bit_length, 1);
            	
            	if(compression_amount < 0)
            	{
            		int compression_length = DeltaMapper.compressOneStrings2(segment, segment_bit_length, compressed_segment);
            		
            		int byte_length = compression_length / 8;
            		if(compression_length % 8 != 0)
            			byte_length++;
            		
            		// This might be a bug related to recursion.  We should
            		// only have to increment the byte length by 1,
            		// and that works most of the time.
            		// This seems like a lot of extra trailing bits.
            		// It mostly happens with binary images.
            		byte_length += 3;
            		byte [] clipped_string = new byte[byte_length];
            		for(int j = 0; j < byte_length; j++)
            			clipped_string[j] = compressed_segment[j];
            		compressed_data.add(clipped_string);
            		
            		//compressed_data.add(compressed_segment);
            		compressed_length.add(compression_length);
        			
        			
            	}
            	else
            	{
            		// Add 0 iterations to uncompressed segment.
            		byte [] clipped_string = new byte[segment_byte_length + 1];
            		for(int j = 0; j < segment_byte_length; j++)
            			clipped_string[j] = segment[j];
            		clipped_string[segment_byte_length] = 0;
        			compressed_length.add(segment_bit_length);
        			compressed_data.add(clipped_string);
            	}	
            }
        }
    	
    	int i = number_of_segments - 1;
    	byte [] segment = new byte[last_segment_byte_length];	
        byte [] compressed_segment = new byte[2 * last_segment_byte_length];
        byte [] processed_segment  = new byte[2 * last_segment_byte_length];
        
        for(int j = 0; j < last_segment_byte_length; j++)
            segment[j] = string[i * segment_byte_length + j];
        
        double zero_ratio = DeltaMapper.getZeroRatio(segment, last_segment_bit_length);
       
        if(zero_ratio >= .5)
        {
        	int compression_amount = DeltaMapper.getCompressionAmount(segment, last_segment_bit_length, 0);
        	if(compression_amount < 0)
        	{
        		int compression_length = DeltaMapper.compressZeroStrings2(segment, last_segment_bit_length, compressed_segment);
        		
        		int byte_length = compression_length / 8 + 1;
        		if(compression_length % 8 != 0)
        			byte_length++;
        		// Should not need to do this.
        		// Only seems to matter with binary images.
        		byte_length += 2;
        		byte [] clipped_string = new byte[byte_length];
        		for(int j = 0; j < byte_length; j++)
        			clipped_string[j] = compressed_segment[j];
        		compressed_length.add(compression_length);
    			compressed_data.add(clipped_string);
    			
    			if(debug1)
    			{
    			    //System.out.println("Compression length 0 is " + compression_length);
    			    int decompression_length = DeltaMapper.decompressZeroStrings(compressed_segment, compression_length, processed_segment);
    			    //System.out.println("Decompression length 0 is " + decompression_length);
    			    boolean same_string = true;
    			    for(int j = 0; j < segment.length; j++)
    			    {
    				    if(segment[j] != processed_segment[j])
    					    same_string = false;
    			    }
    			    if(!same_string)
    				    System.out.println("Processed data is not the same as original data.");
    			    System.out.println();
    			}
        	}
        	else
        	{
        		// Add 0 iterations to uncompressed segment.
        		byte [] clipped_string = new byte[last_segment_byte_length + 1];
        		for(int j = 0; j < last_segment_byte_length; j++)
        			clipped_string[j] = segment[j];
        		clipped_string[last_segment_byte_length] = 0;
    			compressed_length.add(last_segment_bit_length);
    			compressed_data.add(clipped_string);
        	}
        }
        else
        {
        	int compression_amount = DeltaMapper.getCompressionAmount(segment, last_segment_bit_length, 1);
        	if(compression_amount < 0)
        	{
        		int compression_length = DeltaMapper.compressOneStrings2(segment, last_segment_bit_length, compressed_segment);
        		
        		int byte_length = compression_length / 8 + 1;
        		if(compression_length % 8 != 0)
        			byte_length++;
        		// Should not need to do this.
        		// Only seems to matter with binary images.
        		byte_length += 2;
        		byte [] clipped_string = new byte[byte_length];
        		for(int j = 0; j < byte_length; j++)
        			clipped_string[j] = compressed_segment[j];
        		
    			compressed_data.add(clipped_string);
        		//compressed_data.add(compressed_segment);
        		
        		compressed_length.add(compression_length);
    			if(debug1)
    			{
    				//System.out.println("Compression length 1 is " + compression_length);
                    int decompression_length = DeltaMapper.decompressOneStrings(compressed_segment, compression_length, processed_segment);
    			    //System.out.println("Decompression length 1 is " + decompression_length);
    			    boolean same_string = true;
    			    for(int j = 0; j < segment.length; j++)
    			    {
    				    if(segment[j] != processed_segment[j])
    					    same_string = false;
    			    }
    			    if(!same_string)
    				    System.out.println("Processed data is not the same as original data.");
    			    System.out.println();
    			}
        	}
        	else
        	{
        		byte [] clipped_string = new byte[last_segment_byte_length + 1];
        		for(int j = 0; j < last_segment_byte_length; j++)
        			clipped_string[j] = segment[j];
        		clipped_string[last_segment_byte_length] = 0;
    			compressed_length.add(last_segment_bit_length);
    			compressed_data.add(clipped_string);
        	}	
        }
    	
        if(debug2)
        {
            double zero_ratio_min = 1;
		    double zero_ratio_max = 0.;
		    int    min_iterations = Integer.MAX_VALUE;
		    int    max_iterations = 0;
		    int    number_of_zero_segments = 0;
		    int    number_of_one_segments  = 0;
		    int    number_of_compressed_segments = 0;
		
		    for(i = 0; i < compressed_data.size(); i++)
		    {
			    int length   = (int)compressed_length.get(i);
			    byte [] data = (byte [])compressed_data.get(i);
			
			    zero_ratio = DeltaMapper.getZeroRatio(data, length);
			    if(zero_ratio < zero_ratio_min)
				    zero_ratio_min = zero_ratio;
			    if(zero_ratio > zero_ratio_max)
				    zero_ratio_max = zero_ratio;
			    if(zero_ratio >= .5)
				    number_of_zero_segments++;
			    else
				    number_of_one_segments++;
			    int iterations = DeltaMapper.getIterations(data, length);
			    if(iterations > max_iterations)
				    max_iterations = iterations;
			    if(iterations < min_iterations)
			        min_iterations = iterations;
			    if(iterations > 0)
			        number_of_compressed_segments++;
			    }
	
		    System.out.println("Number of segments in regular segment list is " + compressed_data.size());
		    System.out.println("Number of compressed segments is " + number_of_compressed_segments);
		    System.out.println("Number of zero segments is " + number_of_zero_segments);
		    System.out.println("Number of one segments is " + number_of_one_segments);
		    System.out.println("Minimum zero ratio is " + String.format("%.4f", zero_ratio_min));
		    System.out.println("Maximum zero ratio is " + String.format("%.4f", zero_ratio_max));
		    System.out.println("Minimum iterations is " + min_iterations);
		    System.out.println("Maximum iterations is " + max_iterations);
		    System.out.println();
        }
        
    	ArrayList segment_data = new ArrayList();
    	segment_data.add(compressed_length);
    	segment_data.add(compressed_data);
    	segment_data.add(max_segment_byte_length + 1);
    	return segment_data;
    }
}