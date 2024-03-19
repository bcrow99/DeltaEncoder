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

		int max_segment_byte_length = last_segment_byte_length;

		ArrayList compressed_length = new ArrayList();
		ArrayList compressed_data = new ArrayList();

		int current_byte_length = segment_byte_length;
		int current_bit_length = segment_bit_length;
		byte[] segment = new byte[current_byte_length];
		byte[] compressed_segment = new byte[2 * current_byte_length];

		for (int i = 0; i < number_of_segments; i++) 
		{
			if (i == number_of_segments - 1) {
				current_byte_length = last_segment_byte_length;
				current_bit_length = last_segment_bit_length;
				segment = new byte[current_byte_length];
				compressed_segment = new byte[2 * current_byte_length];
			}
			for (int j = 0; j < current_byte_length; j++)
				segment[j] = string[i * segment_byte_length + j];

			double zero_ratio = StringMapper.getZeroRatio(segment, current_bit_length);
			int compression_amount = 0;
			if (zero_ratio >= .5)
				compression_amount = StringMapper.getCompressionAmount(segment, current_bit_length, 0);
			else
				compression_amount = StringMapper.getCompressionAmount(segment, current_bit_length, 1);
			if (compression_amount >= 0) {
				// Add 0 iterations to uncompressed segment.
				byte[] clipped_string = new byte[current_byte_length + 1];
				for (int j = 0; j < current_byte_length; j++)
					clipped_string[j] = segment[j];
				clipped_string[current_byte_length] = 0;
				compressed_length.add(current_bit_length);
				compressed_data.add(clipped_string);
			} else 
			{
				int compressed_bit_length = 0;
				if (zero_ratio >= .5)
					compressed_bit_length = StringMapper.compressZeroStrings(segment, current_bit_length,
							compressed_segment);
				else
					compressed_bit_length = StringMapper.compressOneStrings(segment, current_bit_length,
							compressed_segment);
				int compressed_byte_length = compressed_bit_length / 8;
				if (compressed_bit_length % 8 != 0)
					compressed_byte_length++;
				compressed_byte_length++;

				byte[] clipped_string = new byte[compressed_byte_length];
				for (int j = 0; j < compressed_byte_length; j++)
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

	// This function returns a segment list with regular lengths, and checks if the string decompresses properley.
	public static ArrayList getSegmentedData2(byte[] string, int string_bit_length, int segment_bit_length) 
	{
		int number_of_segments = string_bit_length / segment_bit_length;
		int segment_byte_length = segment_bit_length / 8;
		int remainder = string_bit_length % segment_bit_length;
		int last_segment_bit_length = segment_bit_length + remainder;
		int last_segment_byte_length = segment_byte_length + remainder / 8;
		if (remainder % 8 != 0)
			last_segment_byte_length++;

		int max_segment_byte_length = last_segment_byte_length;

		ArrayList compressed_length = new ArrayList();
		ArrayList compressed_data = new ArrayList();

		int current_byte_length = segment_byte_length;
		int current_bit_length = segment_bit_length;
		byte[] segment = new byte[current_byte_length];
		byte[] compressed_segment = new byte[2 * current_byte_length];

		for (int i = 0; i < number_of_segments; i++) 
		{
			if (i == number_of_segments - 1) {
				current_byte_length = last_segment_byte_length;
				current_bit_length = last_segment_bit_length;
				segment = new byte[current_byte_length];
				compressed_segment = new byte[2 * current_byte_length];
			}
			for (int j = 0; j < current_byte_length; j++)
				segment[j] = string[i * segment_byte_length + j];

			double zero_ratio = StringMapper.getZeroRatio(segment, current_bit_length);
			int compression_amount = 0;
			if (zero_ratio >= .5)
				compression_amount = StringMapper.getCompressionAmount(segment, current_bit_length, 0);
			else
				compression_amount = StringMapper.getCompressionAmount(segment, current_bit_length, 1);
			if (compression_amount >= 0) 
			{
				// Add 0 iterations to uncompressed segment.
				byte[] clipped_string = new byte[current_byte_length + 1];
				for (int j = 0; j < current_byte_length; j++)
					clipped_string[j] = segment[j];
				clipped_string[current_byte_length] = 0;
				compressed_length.add(current_bit_length);
				compressed_data.add(clipped_string);
			} 
			else 
			{
				int compressed_bit_length = 0;
				int new_length            = 0;
				byte [] decompressed_segment = new byte[segment.length * 2];
				if (zero_ratio >= .5)
				{
					compressed_bit_length = StringMapper.compressZeroStrings(segment, current_bit_length, compressed_segment);
					new_length            = StringMapper.decompressZeroStrings(compressed_segment, compressed_bit_length, decompressed_segment);
				}
				else
				{
					compressed_bit_length = StringMapper.compressOneStrings(segment, current_bit_length, compressed_segment);
					new_length            = StringMapper.decompressOneStrings(compressed_segment, compressed_bit_length, decompressed_segment);
				}
				int compressed_byte_length = compressed_bit_length / 8;
				if (compressed_bit_length % 8 != 0)
					compressed_byte_length++;
				compressed_byte_length++;

				byte[] clipped_string = new byte[compressed_byte_length];
				for (int j = 0; j < compressed_byte_length; j++)
					clipped_string[j] = compressed_segment[j];
				compressed_data.add(clipped_string);
				compressed_length.add(compressed_bit_length);
				
				if(new_length != current_bit_length)
				{
				    System.out.println("Iterations for current segment was " + clipped_string[compressed_byte_length]);
				    System.out.println("Original bit length was " + current_bit_length);
				    System.out.println("Processed bit length was " + new_length);
				}
			}
		}
		
		ArrayList segment_data = new ArrayList();
		segment_data.add(compressed_length);
		segment_data.add(compressed_data);
		segment_data.add(max_segment_byte_length);
		return segment_data;
	}
	
	public static ArrayList getMergedSegmentedData(byte[] string, int string_bit_length, int segment_bit_length) 
	{
		int number_of_segments = string_bit_length / segment_bit_length;
		int segment_byte_length = segment_bit_length / 8;
		int remainder = string_bit_length % segment_bit_length;
		int last_segment_bit_length = segment_bit_length + remainder;
		int last_segment_byte_length = segment_byte_length + remainder / 8;
		if (remainder % 8 != 0)
			last_segment_byte_length++;
		
		//System.out.println("Regular bit length is " + segment_bit_length);
		//System.out.println("Last bit length is " + last_segment_bit_length);

		int max_segment_byte_length = last_segment_byte_length;
		int max_byte_value          = Byte.MAX_VALUE * 2 + 1;
		int max_short_value         = Short.MAX_VALUE * 2 + 1;

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
				string_type++;
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
			} 
			else 
			{
				int compressed_bit_length = 0;
				if (string_type == 0)
					compressed_bit_length = StringMapper.compressZeroStrings(segment, current_bit_length,
							compressed_segment);
				else
					compressed_bit_length = StringMapper.compressOneStrings(segment, current_bit_length,
							compressed_segment);
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

		// Merging segments.

		int overhead = 0;
		if(max_segment_byte_length <= Byte.MAX_VALUE * 2 + 1)
			overhead = 16;
		else if(max_segment_byte_length <= Short.MAX_VALUE * 2 + 1)
			overhead = 24;
		else
			overhead = 40;
		ArrayList previous_segment_type = (ArrayList) segment_type.clone();
		ArrayList previous_segment_length = (ArrayList) segment_length.clone();
		ArrayList previous_compressed_length = (ArrayList) compressed_length.clone();
		ArrayList previous_compressed_data = (ArrayList) compressed_data.clone();

		ArrayList current_segment_type = new ArrayList();
		ArrayList current_segment_length = new ArrayList();
		ArrayList current_compressed_length = new ArrayList();
		ArrayList current_compressed_data = new ArrayList();

		int current_number_of_segments = compressed_data.size();
		int previous_number_of_segments = 0;
        int initial_number_of_segments = current_number_of_segments;
		
		
		int last_bit_length = (int)segment_length.get(current_number_of_segments - 1);
		//System.out.println("The last segment bit length is " + last_bit_length);
	
		int iterations = 0;
		int number_of_zero_segments = 0;
		int number_of_one_segments  = 0;
		int number_of_uncompressed_segments = 0;
		int number_of_compressed_segments = 0;
		
		
		while (current_number_of_segments != previous_number_of_segments) 
		{
			iterations++;
			previous_number_of_segments = previous_compressed_data.size();
			number_of_zero_segments = 0;
			number_of_one_segments  = 0;
			number_of_uncompressed_segments = 0;
			number_of_compressed_segments   = 0;
			
			int i = 0;
			int current_offset = 0;

			for (i = 0; i < previous_number_of_segments; i++) 
			{
				int current_iterations = 0;
				int next_iterations    = 0;
				if (i < previous_number_of_segments - 1) 
				{
					int current_string_type = (int) previous_segment_type.get(i);
					int next_string_type = (int) previous_segment_type.get(i + 1);
					int current_uncompressed_length = (int) previous_segment_length.get(i);
					int next_uncompressed_length = (int) previous_segment_length.get(i + 1);
					int current_length = (int) previous_compressed_length.get(i);
					int next_length = (int) previous_compressed_length.get(i + 1);
					byte[] current_string = (byte[]) previous_compressed_data.get(i);
					byte[] next_string = (byte[]) previous_compressed_data.get(i + 1);
					
					current_iterations = current_string[current_string.length - 1];
					if(current_iterations < 0)
						current_iterations = -current_iterations;
				
					next_iterations    = next_string[next_string.length - 1];
					if(next_iterations < 0)
						next_iterations = -next_iterations;
                    
					if(current_string_type == 1)
						number_of_one_segments++;
					else
						number_of_zero_segments++;
					
					//if (current_string_type == next_string_type && current_iterations > 0 && next_iterations > 0 && next_iterations == current_iterations) 
				    if (current_string_type == next_string_type && current_iterations > 0 && next_iterations > 0) 	
					{
						number_of_compressed_segments++;
						int merged_uncompressed_length = current_uncompressed_length + next_uncompressed_length;

						int byte_offset = current_offset / 8;
						int byte_length = merged_uncompressed_length / 8;
						if(merged_uncompressed_length % 8 != 0)
							byte_length++;
						if (byte_length > max_segment_byte_length)
							max_segment_byte_length = byte_length;

						byte[] merged_segment = new byte[byte_length];
						for (int j = 0; j < byte_length; j++)
							merged_segment[j] = string[j + byte_offset];
						byte[] compressed_merged_segment = new byte[2 * byte_length];
						
						int merged_compression_length = 0;
						try 
						{
							if (current_string_type == 0)
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
						// We also account for the overhead--a byte plus a byte,
						// a short plus a byte, or an int plus a byte.
						
						if (merged_compression_length <= (current_length + next_length - overhead)) 
						{
							int compressed_byte_length = merged_compression_length / 8;
							if (merged_compression_length % 8 != 0)
								compressed_byte_length++;
							compressed_byte_length++;
							byte[] segment_string = new byte[compressed_byte_length];
							for (int j = 0; j < compressed_byte_length; j++)
								segment_string[j] = compressed_merged_segment[j];
							current_iterations = current_string[current_string.length - 1];
							if(current_iterations < 0)
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
								max_segment_byte_length = compressed_byte_length;
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
					else if (current_iterations == 0 && next_iterations == 0)
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

						if (byte_length + 1 > max_segment_byte_length)
							max_segment_byte_length = byte_length + 1;

						byte[] merged_segment = new byte[byte_length + 1];

						for (int j = 0; j < byte_length; j++)
							merged_segment[j] = string[j + byte_offset];
						merged_segment[byte_length] = 0;

						// Keep track of maximum segment length.
						if (byte_length > max_segment_byte_length)
							max_segment_byte_length = byte_length;

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
			overhead = 0;
			if(max_segment_byte_length <= Byte.MAX_VALUE * 2 + 1)
				overhead = 16;
			else if(max_segment_byte_length <= Short.MAX_VALUE * 2 + 1)
				overhead = 24;
			else
				overhead = 40;
		}
		
		System.out.println("The number of segments in initial list is " + initial_number_of_segments);
		System.out.println("Number of iterations merging segments was " + iterations);
		System.out.println("Number of merged segments was " + previous_compressed_data.size());
		System.out.println("Number of iterations merging segments was " + iterations);
		System.out.println("Minimum segment byte length was " + (segment_bit_length / 8));
		System.out.println("Maximum segment byte length was " + max_segment_byte_length);
		System.out.println("Number of zero segments is " + number_of_zero_segments);
		System.out.println("Number of one segments is " + number_of_one_segments);
		System.out.println("Number of compressed segments is " + number_of_compressed_segments);
		System.out.println("Number of uncompressed segments is " + number_of_uncompressed_segments);
		System.out.println();
		
		
		ArrayList segment_data = new ArrayList();
		segment_data.add(previous_compressed_length);
		segment_data.add(previous_compressed_data);
		segment_data.add(max_segment_byte_length);
		return segment_data;
	}
	public static ArrayList getMergedSegmentedData2(byte[] string, int string_bit_length, int segment_bit_length) 
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
					compressed_bit_length = StringMapper.compressZeroStrings2(segment, current_bit_length, compressed_segment);
				else
					compressed_bit_length = StringMapper.compressOneStrings2(segment, current_bit_length, compressed_segment);
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
		
		
		
		// Merging segments.
        /*
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
					int current_string_type = (int) previous_segment_type.get(i);
					int next_string_type = (int) previous_segment_type.get(i + 1);
					int current_uncompressed_length = (int) previous_segment_length.get(i);
					int next_uncompressed_length = (int) previous_segment_length.get(i + 1);
					int current_length = (int) previous_compressed_length.get(i);
					int next_length = (int) previous_compressed_length.get(i + 1);
					byte[] current_string = (byte[]) previous_compressed_data.get(i);
					byte[] next_string = (byte[]) previous_compressed_data.get(i + 1);
					
					current_iterations = current_string[current_string.length - 1];
					if(current_iterations < 0)
						current_iterations = -current_iterations;
				
					next_iterations = next_string[next_string.length - 1];
                    
					if(next_iterations < 0)
						next_iterations = -next_iterations;
                    
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
						if (byte_length + 1 > max_segment_byte_length)
							max_segment_byte_length = byte_length + 1;

						byte[] merged_segment = new byte[byte_length];
						for (int j = 0; j < byte_length; j++)
							merged_segment[j] = string[j + byte_offset];
						byte[] compressed_merged_segment = new byte[2 * byte_length];
						
						int merged_compression_length = 0;
						try 
						{
							if (current_string_type == 0)
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
						if(merged_compression_length <= (current_length + next_length - overhead)) 
						{
							int compressed_byte_length = merged_compression_length / 8;
							if (merged_compression_length % 8 != 0)
								compressed_byte_length++;
							compressed_byte_length++;
							byte[] segment_string = new byte[compressed_byte_length];
							for (int j = 0; j < compressed_byte_length; j++)
								segment_string[j] = compressed_merged_segment[j];
							current_iterations = current_string[current_string.length - 1];
							if(current_iterations < 0)
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
								max_segment_byte_length = compressed_byte_length;
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

						if (byte_length + 1 > max_segment_byte_length)
							max_segment_byte_length = byte_length + 1;

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
		
		System.out.println("The number of segments in initial list is " + initial_number_of_segments);
		System.out.println("Number of iterations merging segments was " + iterations);
		System.out.println("Number of merged segments was " + previous_compressed_data.size());
		System.out.println("Number of iterations merging segments was " + iterations);
		System.out.println("Minimum segment byte length was " + (segment_bit_length / 8));
		System.out.println("Maximum segment byte length was " + max_segment_byte_length);
		System.out.println("Number of zero segments is " + number_of_zero_segments);
		System.out.println("Number of one segments is " + number_of_one_segments);
		System.out.println("Number of compressed segments is " + number_of_compressed_segments);
		System.out.println("Number of uncompressed segments is " + number_of_uncompressed_segments);
		System.out.println();
		
		
		ArrayList segment_data = new ArrayList();
		segment_data.add(previous_compressed_length);
		segment_data.add(previous_compressed_data);
		segment_data.add(max_segment_byte_length);
		return segment_data;
		*/
	}
}