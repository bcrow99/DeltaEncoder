import java.util.*;
import java.util.zip.*;
import java.lang.Math.*;
import java.math.*;

public class SegmentMapper {

	// This function returns a segment list with regular lengths.
	public static ArrayList getSegmentedData(byte[] string, int string_bit_length, int segment_bit_length) {
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

		for (int i = 0; i < number_of_segments; i++) {

			if (i == number_of_segments - 1) {
				current_byte_length = last_segment_byte_length;
				current_bit_length = last_segment_bit_length;
				segment = new byte[current_byte_length];
				compressed_segment = new byte[2 * current_byte_length];
			}
			for (int j = 0; j < current_byte_length; j++)
				segment[j] = string[i * segment_byte_length + j];

			double zero_ratio = DeltaMapper.getZeroRatio(segment, current_bit_length);
			int compression_amount = 0;
			if (zero_ratio >= .5)
				compression_amount = DeltaMapper.getCompressionAmount(segment, current_bit_length, 0);
			else
				compression_amount = DeltaMapper.getCompressionAmount(segment, current_bit_length, 1);
			if (compression_amount >= 0) {
				// Add 0 iterations to uncompressed segment.
				byte[] clipped_string = new byte[current_byte_length + 1];
				for (int j = 0; j < current_byte_length; j++)
					clipped_string[j] = segment[j];
				clipped_string[current_byte_length] = 0;
				compressed_length.add(current_bit_length);
				compressed_data.add(clipped_string);
			} else {
				int compressed_bit_length = 0;
				if (zero_ratio >= .5)
					compressed_bit_length = DeltaMapper.compressZeroStrings2(segment, current_bit_length,
							compressed_segment);
				else
					compressed_bit_length = DeltaMapper.compressOneStrings2(segment, current_bit_length,
							compressed_segment);
				int compressed_byte_length = compressed_bit_length / 8;
				if (compressed_bit_length % 8 != 0)
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

				// compressed_byte_length += 2;

				// We'll just catch the exception instead of trying to
				// avoid it, since the program has seemingly processed
				// the data correctly when it runs out of memory.
				// The error seems to be relegated to
				// low resolution images with lots of segments, probably
				// for the reasons described above.

				// I'm not sure if there's any logic related directly to
				// the length of the clipped string in the rest of the program.
				// Best practice is to avoid relying on it.

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

	public static ArrayList getMergedSegmentedData(byte[] string, int string_bit_length, int segment_bit_length) 
	{
		int number_of_segments = string_bit_length / segment_bit_length;
		int segment_byte_length = segment_bit_length / 8;
		int remainder = string_bit_length % segment_bit_length;
		int last_segment_bit_length = segment_bit_length + remainder;
		int last_segment_byte_length = segment_byte_length + remainder / 8;
		if (remainder % 8 != 0)
			last_segment_byte_length++;
		
		System.out.println("Regular bit length is " + segment_bit_length);
		System.out.println("Last bit length is " + last_segment_bit_length);

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

			double zero_ratio = DeltaMapper.getZeroRatio(segment, current_bit_length);
			int string_type = 0;
			if (zero_ratio < .5)
				string_type++;
			segment_type.add(string_type);
			segment_length.add(current_bit_length);

			int compression_amount = 0;
			if (string_type == 0)
				compression_amount = DeltaMapper.getCompressionAmount(segment, current_bit_length, 0);
			else
				compression_amount = DeltaMapper.getCompressionAmount(segment, current_bit_length, 1);
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
					compressed_bit_length = DeltaMapper.compressZeroStrings2(segment, current_bit_length,
							compressed_segment);
				else
					compressed_bit_length = DeltaMapper.compressOneStrings2(segment, current_bit_length,
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

		System.out.println("The number of segments in initial list is " + current_number_of_segments);
		int last_bit_length = (int)segment_length.get(current_number_of_segments - 1);
		System.out.println("The last segment bit length is " + last_bit_length);
		System.out.println();

		int iterations = 0;
		while (current_number_of_segments != previous_number_of_segments) 
		{
			iterations++;
			previous_number_of_segments = previous_compressed_data.size();

			int i = 0;
			int current_offset = 0;

			for (i = 0; i < previous_number_of_segments; i++) 
			{
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
					int current_iterations = DeltaMapper.getIterations(current_string, current_length);
					int next_iterations = DeltaMapper.getIterations(next_string, next_length);


					if (current_string_type == next_string_type && current_iterations > 0 && next_iterations > 0) 
					{
						int merged_uncompressed_length = current_uncompressed_length + next_uncompressed_length;

						int byte_offset = current_offset / 8;
						int byte_length = merged_uncompressed_length / 8;
						if(byte_length % 8 != 0)
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
								merged_compression_length = DeltaMapper.compressZeroStrings2(merged_segment,
										merged_uncompressed_length, compressed_merged_segment);
							else
								merged_compression_length = DeltaMapper.compressOneStrings2(merged_segment,
										merged_uncompressed_length, compressed_merged_segment);
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
						// We should also account for overhead.
						// For now we'll wait until we actually write and read files,
						// so we have a good way to evaluate results to show if the separate
						// files compress separately even with twice the overhead.
						if (merged_compression_length <= (current_length + next_length + 8)) 
						{
							int compressed_byte_length = merged_compression_length / 8;
							if (merged_compression_length % 8 != 0)
								compressed_byte_length++;
							compressed_byte_length++;
							byte[] segment_string = new byte[compressed_byte_length];
							for (int j = 0; j < compressed_byte_length; j++)
								segment_string[j] = compressed_merged_segment[j];

							current_segment_type.add(DeltaMapper.getStringType(segment_string, merged_compression_length));
							current_segment_length.add(merged_uncompressed_length);
							current_compressed_length.add(merged_compression_length);
							current_compressed_data.add(segment_string);

							current_offset += merged_uncompressed_length;

							i++;
							//System.out.println("Merged segments.");
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
					//if(current_iterations == 0 && next_iterations == 0)
					{
						int merged_length = current_length + next_length;
						int merged_uncompressed_length = current_uncompressed_length + next_uncompressed_length;

						if (merged_length != merged_uncompressed_length) 
						{
							System.out.println("Uncompressed segment has different length for string.");
						}

						int byte_offset = current_offset / 8;
						int byte_length = merged_uncompressed_length / 8;
						if (merged_uncompressed_length % 8 != 0) 
						{
							byte_length++;
							System.out.println("Uneven segment at index " + (i + 1));
							System.out.println("Current number of segments is " + previous_number_of_segments);
							System.out.println("Current iteration is " + iterations);
						}

						if (byte_length > max_segment_byte_length)
							max_segment_byte_length = byte_length;

						byte[] merged_segment = new byte[byte_length + 1];

						for (int j = 0; j < byte_length; j++)
							merged_segment[j] = string[j + byte_offset];
						merged_segment[byte_length] = 0;

						// Keep track of maximum segment length.
						if (byte_length > max_segment_byte_length)
							max_segment_byte_length = byte_length;

						int merged_type = DeltaMapper.getStringType(merged_segment, merged_length);
						current_segment_type.add(merged_type);
						current_segment_length.add(merged_length);
						current_compressed_length.add(merged_length);
						current_compressed_data.add(merged_segment);

						current_offset += merged_uncompressed_length;
						i++;
					} 
					else 
					{
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

					current_segment_type.add(current_string_type);
					current_segment_length.add(current_uncompressed_length);
					current_compressed_length.add(current_length);
					current_compressed_data.add(current_string);
					
					// Last segment, don't need to increment offset.
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
		}

		System.out.println("Number of iterations merging segments was " + iterations);
		System.out.println("Number of merged segments was " + previous_compressed_data.size());
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

		int max_segment_byte_length = last_segment_byte_length;

		ArrayList compressed_length = new ArrayList();
		ArrayList compressed_data = new ArrayList();
		ArrayList compressed_type = new ArrayList();

		int current_byte_length = segment_byte_length;
		int current_bit_length = segment_bit_length;
		byte[] segment = new byte[current_byte_length];
		byte[] compressed_segment = new byte[2 * current_byte_length];

		for (int i = 0; i < number_of_segments; i++) {
			if (i == number_of_segments - 1) {
				current_byte_length = last_segment_byte_length;
				current_bit_length = last_segment_bit_length;
				segment = new byte[current_byte_length];
				compressed_segment = new byte[2 * current_byte_length];
			}
			for (int j = 0; j < current_byte_length; j++)
				segment[j] = string[i * segment_byte_length + j];

			double zero_ratio = DeltaMapper.getZeroRatio(segment, current_bit_length);
			int string_type = 0;
			if (zero_ratio < .5)
				string_type++;
			compressed_type.add(string_type);

			int compression_amount = 0;
			if (string_type == 0)
				compression_amount = DeltaMapper.getCompressionAmount(segment, current_bit_length, 0);
			else
				compression_amount = DeltaMapper.getCompressionAmount(segment, current_bit_length, 1);
			if (compression_amount >= 0) {
				byte[] clipped_string = new byte[current_byte_length + 1];
				for (int j = 0; j < current_byte_length; j++)
					clipped_string[j] = segment[j];
				clipped_string[current_byte_length] = 0;
				compressed_length.add(current_bit_length);
				compressed_data.add(clipped_string);
			} else {
				int compressed_bit_length = 0;
				if (string_type == 0)
					compressed_bit_length = DeltaMapper.compressZeroStrings2(segment, current_bit_length,
							compressed_segment);
				else
					compressed_bit_length = DeltaMapper.compressOneStrings2(segment, current_bit_length,
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

		ArrayList previous_type = (ArrayList) compressed_type.clone();
		ArrayList previous_length = (ArrayList) compressed_length.clone();
		ArrayList previous_data = (ArrayList) compressed_data.clone();

		ArrayList current_type = new ArrayList();
		ArrayList current_length = new ArrayList();
		ArrayList current_data = new ArrayList();

		int current_number_of_segments = compressed_data.size();
		int previous_number_of_segments = 0;

		int current_offset = 0;
		while (current_number_of_segments != previous_number_of_segments) {
			previous_number_of_segments = previous_data.size();

			int i = 0;
			current_offset = 0;

			for (i = 0; i < previous_number_of_segments - 1; i++) {
				int current_string_type = (int) previous_type.get(i);
				int next_string_type = (int) previous_type.get(i + 1);
				int current_string_length = (int) previous_length.get(i);
				int next_string_length = (int) previous_length.get(i + 1);
				byte[] current_string = (byte[]) previous_data.get(i);
				byte[] next_string = (byte[]) previous_data.get(i + 1);
				int current_iterations = DeltaMapper.getIterations(current_string, current_string_length);
				int next_iterations = DeltaMapper.getIterations(next_string, next_string_length);

				if (current_string_type == next_string_type && current_iterations > 0 && next_iterations > 0) {
					int merged_length = current_string_length + next_string_length;

					int byte_offset = current_offset / 8;
					int byte_length = merged_length / 8;
					if (merged_length % 8 != 0)
						byte_length++;
					if (merged_length % 8 != 0 && i != previous_number_of_segments - 2)
						System.out.println("Merged length not evenly divisible by 8 at index = " + i);
					if (byte_length > max_segment_byte_length)
						max_segment_byte_length = byte_length;

					byte[] merged_segment = new byte[byte_length];
					for (int j = 0; j < byte_length; j++)
						merged_segment[j] = string[j + byte_offset];
					byte[] compressed_merged_segment = new byte[2 * byte_length];
					int merged_compression_length = 0;
					if (current_string_type == 0)
						merged_compression_length = DeltaMapper.compressZeroStrings2(merged_segment, merged_length,
								compressed_merged_segment);
					else
						merged_compression_length = DeltaMapper.compressOneStrings2(merged_segment, merged_length,
								compressed_merged_segment);

					if (merged_compression_length <= current_string_length + next_string_length) {
						int compressed_byte_length = merged_compression_length / 8;
						if (merged_compression_length % 8 != 0)
							compressed_byte_length++;
						compressed_byte_length++;
						byte[] segment_string = new byte[compressed_byte_length];
						for (int j = 0; j < compressed_byte_length; j++)
							segment_string[j] = compressed_merged_segment[j];

						current_type.add(DeltaMapper.getStringType(segment_string, merged_compression_length));
						current_length.add(merged_compression_length);
						current_data.add(segment_string);

						current_offset += merged_length;

						i++;
					} else {
						current_type.add(current_string_type);
						current_length.add(current_string_length);
						current_data.add(current_string);
						current_offset += current_string_length;
					}
				} else if (current_iterations == 0 && next_iterations == 0) {
					int merged_length = current_string_length + next_string_length;

					int byte_offset = current_offset / 8;
					int byte_length = merged_length / 8;
					if (merged_length % 8 != 0)
						byte_length++;

					if (merged_length % 8 != 0 && i != previous_number_of_segments - 2)
						System.out.println("Merged length not evenly divisible by 8 at index = " + i);
					byte[] merged_segment = new byte[byte_length];
					for (int j = 0; j < byte_length; j++)
						merged_segment[j] = string[j + byte_offset];

					byte_length = merged_length / 8;
					if (merged_length % 8 != 0)
						byte_length++;
					byte_length++;

					// Keep track of maximum segment length.
					if (byte_length > max_segment_byte_length)
						max_segment_byte_length = byte_length;

					byte[] segment_string = new byte[byte_length];
					for (int j = 0; j < byte_length - 1; j++)
						segment_string[j] = merged_segment[j];
					segment_string[merged_segment.length] = 0;

					int merged_type = DeltaMapper.getStringType(segment_string, merged_length);
					current_type.add(merged_type);
					current_length.add(merged_length);
					current_data.add(segment_string);

					current_offset += merged_length;
					i++;
				} else {
					current_type.add(current_string_type);
					current_length.add(current_string_length);
					current_data.add(current_string);
					current_offset += current_string_length;
				}
			}

			previous_type = (ArrayList) current_type.clone();
			previous_length = (ArrayList) current_length.clone();
			previous_data = (ArrayList) current_data.clone();

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
}