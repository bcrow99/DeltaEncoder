import java.util.ArrayList;

/**
 * This is a class that segments data, and then merges it back depending on how
 * it affect the compression rate.
 * 
 * @author Brian Crowley
 * @version 1.0
 */
public class SegmentMapper
{

	public static ArrayList getSegmentedData(byte[] string, int minimum_bitlength)
	{
		ArrayList result = new ArrayList();
		if(minimum_bitlength % 8 != 0)
		{
			System.out.println("Minimum segment bitlength must be a multiple of 8.");
			return result;
		}

		int string_bitlength = StringMapper.getBitlength(string);
		int number_of_segments = string_bitlength / minimum_bitlength;
		int segment_bitlength = minimum_bitlength;
		int segment_bytelength = minimum_bitlength / 8;
		segment_bytelength++;

		int remainder = string_bitlength % minimum_bitlength;
		int last_segment_bitlength = minimum_bitlength + remainder;
		int last_segment_bytelength = last_segment_bitlength / 8;

		byte last_extra_bits = 0;
		if(remainder % 8 != 0)
		{
			last_segment_bytelength++;
			last_extra_bits = (byte) (8 - remainder);
			last_extra_bits <<= 5;
		}

		last_segment_bytelength++;

		int max_segment_bytelength = last_segment_bytelength;

		ArrayList<byte[]> segments = new ArrayList<byte[]>();

		int[] bit_table = StringMapper.getBitTable();

		int[] bitlength = new int[number_of_segments];
		int[] bin_number = new int[number_of_segments];
		double bin = .05;

		for(int i = 0; i < number_of_segments; i++)
		{
			if(i < number_of_segments - 1)
			{
				byte[] segment = new byte[segment_bytelength];
				for(int j = 0; j < segment.length - 1; j++)
					segment[j] = string[i * (segment_bytelength - 1) + j];
				bitlength[i] = segment_bitlength;

				double zero_ratio = StringMapper.getZeroRatio(segment, segment_bitlength, bit_table);
				bin_number[i] = getBinNumber(zero_ratio, bin);
				if(zero_ratio < .5)
				{
					byte transform_iterations = 16;
					segment[segment.length - 1] = transform_iterations;
				}

				segments.add(segment);
			} 
			else
			{
				// The only segment that might be uneven.
				byte[] segment = new byte[last_segment_bytelength];
				for(int j = 0; j < segment.length - 1; j++)
					segment[j] = string[i * (segment_bytelength - 1) + j];
				segment[segment.length - 1] = last_extra_bits;
				bitlength[i] = last_segment_bitlength;

				double zero_ratio = StringMapper.getZeroRatio(segment, last_segment_bitlength, bit_table);
				bin_number[i] = getBinNumber(zero_ratio, bin);
				if(zero_ratio < .5)
				{
					byte transform_iterations = 16;
					segment[segment.length - 1] |= transform_iterations;
				}

				segments.add(segment);
			}
		}

		ArrayList<byte[]> merged_segments = new ArrayList<byte[]>();

		int number_of_bins = (int) (1. / bin);
		int bin_divider = number_of_bins / 2;
		int difference = bin_divider / 2;
		int i = 0;

		for(i = 0; i < number_of_segments - 1; i++)
		{
			int current_bin = bin_number[i];
			int j = 1;
			int next_bin = bin_number[i + j];

			// if((current_bin < bin_divider && next_bin < bin_divider &&
			// Math.abs(current_bin - next_bin) < difference) || (current_bin >= bin_divider
			// && next_bin >= bin_divider && Math.abs(current_bin - next_bin) < difference))
			if((current_bin < bin_divider && next_bin < bin_divider) || (current_bin >= bin_divider && next_bin >= bin_divider))
			// if(current_bin == next_bin)
			{
				// while(((current_bin < bin_divider && next_bin < bin_divider &&
				// Math.abs(current_bin - next_bin) < difference) || (current_bin >= bin_divider
				// && next_bin >= bin_divider && Math.abs(current_bin - next_bin) < difference))
				// && i + j < number_of_segments - 1 )
				while (((current_bin < bin_divider && next_bin < bin_divider) || (current_bin >= bin_divider && next_bin >= bin_divider)) && i + j < number_of_segments - 1)
				// while((current_bin == next_bin) && i + j < number_of_segments - 1)
				{
					next_bin = bin_number[i + j + 1];
					// if((current_bin < bin_divider && next_bin < bin_divider &&
					// Math.abs(current_bin - next_bin) < difference) || (current_bin >= bin_divider
					// && next_bin >= bin_divider && Math.abs(current_bin - next_bin) < difference))
					if((current_bin < bin_divider && next_bin < bin_divider) || (current_bin >= bin_divider && next_bin >= bin_divider))
						// if(current_bin == next_bin)
						j++;
				}

				int merged_bytelength = j * (segment_bytelength - 1);
				if(i + j == number_of_segments - 1)
					merged_bytelength += last_segment_bytelength - 1;
				else
					merged_bytelength += segment_bytelength - 1;
				merged_bytelength++;
				byte[] merged_segment = new byte[merged_bytelength];
				int m = 0;
				for(int k = 0; k < j + 1; k++)
				{
					byte[] segment = segments.get(i + k);
					for(int n = 0; n < segment.length - 1; n++)
						merged_segment[m + n] = segment[n];
					m += segment.length - 1;
				}
				if(i + j == number_of_segments - 1)
					merged_segment[merged_bytelength - 1] = last_extra_bits;
				if(current_bin < bin_divider)
					merged_segment[merged_bytelength - 1] |= 16;
				merged_segments.add(merged_segment);

				i += j;

			} 
			else
			{
				byte[] segment = segments.get(i);
				merged_segments.add(segment);
			}
		}

		if(i == number_of_segments - 1)
		{
			byte[] segment = segments.get(i);
			merged_segments.add(segment);
		}

		int number_of_merged_segments = merged_segments.size();

		ArrayList<byte[]> compressed_segments = new ArrayList<byte[]>();

		max_segment_bytelength = 0;
		int[] compressed_iterations = new int[number_of_merged_segments];
		int number_of_uncompressed_adjacent_segments = 0;
		int previous_iterations = 1;
		for(i = 0; i < number_of_merged_segments; i++)
		{
			byte[] segment = merged_segments.get(i);
			byte[] compressed_segment = StringMapper.compressStrings(segment);
			compressed_segments.add(compressed_segment);
			if(compressed_segment.length - 1 > max_segment_bytelength)
				max_segment_bytelength = compressed_segment.length - 1;
			compressed_iterations[i] = StringMapper.getIterations(compressed_segment);
			if((previous_iterations == 0 || previous_iterations == 16) && (compressed_iterations[i] == 0 || compressed_iterations[i] == 16))
				number_of_uncompressed_adjacent_segments++;
			previous_iterations = compressed_iterations[i];
		}

		// This should be the same as the number of merged segments.
		int number_of_compressed_segments = compressed_segments.size();

		// When we divide up the original string, we don't account for transform
		// iterations of the original string so if all the segments merge into one
		// we need to copy the last byte of the string to the single
		// merged segment to restore that information.
		if(number_of_compressed_segments == 1)
		{
			byte[] segment = compressed_segments.get(0);
			if(segment.length != string.length)
				System.out.println("Single segment is not the same length as the original string.");
			else
				segment[segment.length - 1] = string[string.length - 1];
			result.add(compressed_segments);
			result.add(max_segment_bytelength);
		} else
		{
			// We want to combine uncompressed segments to reduce overhead
			// and create a pattern where any uncompressed segment is
			// preceded and followed by a compressed segment.
			ArrayList<byte[]> combined_segments = new ArrayList<byte[]>();

			// We don't do anything because there aren't any adjacent uncompressed
			// segments to combine.
			if(number_of_uncompressed_adjacent_segments == 0)
				combined_segments = compressed_segments;
			else
			{
				for(i = 0; i < number_of_compressed_segments - 1; i++)
				{
					byte[] current_segment = compressed_segments.get(i);
					int current_iterations = StringMapper.getIterations(current_segment);

					byte[] next_segment = compressed_segments.get(i + 1);
					int next_iterations = StringMapper.getIterations(next_segment);

					if((current_iterations == 0 || current_iterations == 16) && (next_iterations == 0 || next_iterations == 16))
					{
						int j = 1;
						while ((next_iterations == 0 || next_iterations == 16) && i + j + 1 < number_of_compressed_segments)
						{
							next_segment = compressed_segments.get(i + j + 1);
							next_iterations = StringMapper.getIterations(next_segment);
							if(next_iterations == 0 || next_iterations == 16)
								j++;
						}

						int combined_length = 0;
						for(int k = 0; k < j + 1; k++)
						{
							byte[] segment = compressed_segments.get(i + k);
							combined_length += segment.length - 1;
						}
						combined_length++;

						if(max_segment_bytelength < combined_length - 1)
							max_segment_bytelength = combined_length - 1;

						byte[] combined_segment = new byte[combined_length];

						int m = 0;
						for(int k = 0; k < j + 1; k++)
						{
							byte[] segment = compressed_segments.get(i + k);
							for(int n = 0; n < segment.length - 1; n++)
								combined_segment[m + n] = segment[n];
							m += segment.length - 1;
						}

						if(i + j == number_of_compressed_segments - 1)
						{
							int last_bitlength = (combined_segment.length - 1) * 8;
							int k = last_extra_bits >> 5;
							k &= 7;
							last_bitlength -= k;
							double zero_ratio = StringMapper.getZeroRatio(combined_segment, last_bitlength, bit_table);
							combined_segment[combined_segment.length - 1] = last_extra_bits;
							if(zero_ratio < .5)
								combined_segment[combined_segment.length - 1] |= 16;
							combined_segments.add(combined_segment);
						} 
						else
						{
							double zero_ratio = StringMapper.getZeroRatio(combined_segment, (combined_segment.length - 1) * 8, bit_table);
							if(zero_ratio < .5)
								combined_segment[combined_segment.length - 1] |= 16;
							combined_segments.add(combined_segment);
						}

						i += j;
					} 
					else
						combined_segments.add(current_segment);
				}

				if(i == number_of_compressed_segments - 1)
				{
					byte[] segment = compressed_segments.get(i);
					combined_segments.add(segment);
				}

				// Similarly to the merge process, we need to restore information if we combined
				// down to one segment.
				if(combined_segments.size() == 1)
				{
					byte[] segment = combined_segments.get(0);
					if(segment.length != string.length)
						System.out.println("Single segment is not the same length as the original string.");
					else
						segment[segment.length - 1] = string[string.length - 1];
				}
			}

			int number_of_combined_segments = combined_segments.size();

			result.add(combined_segments);
			result.add(max_segment_bytelength);

			int[] combined_iterations = new int[number_of_combined_segments];
			double[] combined_ratio = new double[number_of_combined_segments];
			int[] combined_number = new int[number_of_combined_segments];

			int number_of_uncompressed_segments = 0;
			for(i = 0; i < number_of_combined_segments; i++)
			{
				byte[] segment = combined_segments.get(i);
				combined_iterations[i] = StringMapper.getIterations(segment);
				combined_ratio[i] = StringMapper.getZeroRatio(segment, (segment.length - 1) * 8, bit_table);
				combined_number[i] = getBinNumber(combined_ratio[i], bin);
				if(combined_iterations[i] == 0 || combined_iterations[i] == 16)
					number_of_uncompressed_segments++;

				System.out.println("Segment " + i + "\titerations " + combined_iterations[i] + "\tbin " + combined_number[i] + "\tratio " + String.format("%.2f", combined_ratio[i]));
			}
			System.out.println();

			System.out.println("The original string had iterations " + StringMapper.getIterations(string));
			System.out.println("The number of regular segments was " + number_of_segments + " with minimum bit length " + minimum_bitlength);
			System.out.println("The number of merged segments was " + number_of_merged_segments);
			System.out.println("The number of compressed/combined segments was " + number_of_combined_segments);
			System.out.println("The number of compressed/combined segments that were uncompressed was " + number_of_uncompressed_segments);

		}

		return result;
	}

	public static ArrayList getSegmentedData2(byte[] string, int minimum_bitlength)
	{
		ArrayList result = new ArrayList();
		if(minimum_bitlength % 8 != 0)
		{
			System.out.println("Minimum segment bitlength must be a multiple of 8.");
			return result;
		}

		int string_bitlength = StringMapper.getBitlength(string);
		int number_of_segments = string_bitlength / minimum_bitlength;
		int segment_bitlength = minimum_bitlength;
		int segment_bytelength = minimum_bitlength / 8;
		segment_bytelength++;

		int remainder = string_bitlength % minimum_bitlength;
		int last_segment_bitlength = minimum_bitlength + remainder;
		int last_segment_bytelength = last_segment_bitlength / 8;

		byte last_extra_bits = 0;
		if(remainder % 8 != 0)
		{
			last_segment_bytelength++;
			last_extra_bits = (byte) (8 - remainder);
			last_extra_bits <<= 5;
		}

		last_segment_bytelength++;

		int max_segment_bytelength = last_segment_bytelength;

		ArrayList<byte[]> segments = new ArrayList<byte[]>();

		int[] bit_table = StringMapper.getBitTable();

		int[] bitlength = new int[number_of_segments];
		int[] bin_number = new int[number_of_segments];
		double bin = .05;

		for(int i = 0; i < number_of_segments; i++)
		{
			if(i < number_of_segments - 1)
			{
				byte[] segment = new byte[segment_bytelength];
				for(int j = 0; j < segment.length - 1; j++)
					segment[j] = string[i * (segment_bytelength - 1) + j];
				bitlength[i] = segment_bitlength;

				double zero_ratio = StringMapper.getZeroRatio(segment, segment_bitlength, bit_table);
				bin_number[i] = getBinNumber(zero_ratio, bin);
				if(zero_ratio < .5)
				{
					byte transform_iterations = 16;
					segment[segment.length - 1] = transform_iterations;
				}

				segments.add(segment);
			} 
			else
			{
				// The only segment that might be uneven.
				byte[] segment = new byte[last_segment_bytelength];
				for(int j = 0; j < segment.length - 1; j++)
					segment[j] = string[i * (segment_bytelength - 1) + j];
				segment[segment.length - 1] = last_extra_bits;
				bitlength[i] = last_segment_bitlength;

				double zero_ratio = StringMapper.getZeroRatio(segment, last_segment_bitlength, bit_table);
				bin_number[i] = getBinNumber(zero_ratio, bin);
				if(zero_ratio < .5)
				{
					byte transform_iterations = 16;
					segment[segment.length - 1] |= transform_iterations;
				}

				segments.add(segment);
			}
		}

		ArrayList<byte[]> merged_segments = new ArrayList<byte[]>();

		int number_of_bins = (int) (1. / bin);
		int bin_divider    = number_of_bins / 2;
		int difference     = bin_divider / 2;
		
		int i = 0;
		for(i = 0; i < number_of_segments - 1; i++)
		{
			int current_bin = bin_number[i];
			int j = 1;
			int next_bin = bin_number[i + j];
			
			// if((current_bin < bin_divider - 1 && next_bin < bin_divider - 1) || (current_bin > bin_divider + 1 && next_bin > bin_divider + 1)  || ((current_bin == bin_divider - 1 || current_bin == bin_divider) && (next_bin == bin_divider - 1 || next_bin == bin_divider)))
			// if((current_bin < bin_divider && next_bin < bin_divider && Math.abs(current_bin - next_bin) < difference) || (current_bin >= bin_divider && next_bin >= bin_divider && Math.abs(current_bin - next_bin) < difference))
			if((current_bin < bin_divider && next_bin < bin_divider) || (current_bin >= bin_divider && next_bin >= bin_divider))
			// if(current_bin == next_bin)
			{
				//while(((current_bin < bin_divider - 1 && next_bin < bin_divider - 1) || (current_bin > bin_divider + 1 && next_bin > bin_divider + 1)  || ((current_bin == bin_divider - 1 || current_bin == bin_divider) && (next_bin == bin_divider - 1 || next_bin == bin_divider))) && i + j < number_of_segments - 1)
				// while(((current_bin < bin_divider && next_bin < bin_divider && Math.abs(current_bin - next_bin) < difference) || (current_bin >= bin_divider && next_bin >= bin_divider && Math.abs(current_bin - next_bin) < difference)) && i + j < number_of_segments - 1 )
				while (((current_bin < bin_divider && next_bin < bin_divider) || (current_bin >= bin_divider && next_bin >= bin_divider)) && i + j < number_of_segments - 1)
				// while((current_bin == next_bin) && i + j < number_of_segments - 1)
				{
					next_bin = bin_number[i + j + 1];
					//if((current_bin < bin_divider - 1 && next_bin < bin_divider - 1) || (current_bin > bin_divider + 1 && next_bin > bin_divider + 1)  || ((current_bin == bin_divider - 1 || current_bin == bin_divider) && (next_bin == bin_divider - 1 || next_bin == bin_divider)))
					//if((current_bin < bin_divider && next_bin < bin_divider && Math.abs(current_bin - next_bin) < difference) || (current_bin >= bin_divider && next_bin >= bin_divider && Math.abs(current_bin - next_bin) < difference))
					if((current_bin < bin_divider && next_bin < bin_divider) || (current_bin >= bin_divider && next_bin >= bin_divider))
					// if(current_bin == next_bin)
					    j++;
				}

				int merged_bytelength = j * (segment_bytelength - 1);
				if(i + j == number_of_segments - 1)
					merged_bytelength += last_segment_bytelength - 1;
				else
					merged_bytelength += segment_bytelength - 1;
				merged_bytelength++;
				byte[] merged_segment = new byte[merged_bytelength];
				int m = 0;
				for(int k = 0; k < j + 1; k++)
				{
					byte[] segment = segments.get(i + k);
					for(int n = 0; n < segment.length - 1; n++)
						merged_segment[m + n] = segment[n];
					m += segment.length - 1;
				}
				
				if(i + j == number_of_segments - 1)
					merged_segment[merged_bytelength - 1] = last_extra_bits;
				if(current_bin < bin_divider)
					merged_segment[merged_bytelength - 1] |= 16;
				merged_segments.add(merged_segment);

				i += j;

			} 
			else
			{
				byte[] segment = segments.get(i);
				merged_segments.add(segment);
			}
		}

		if(i == number_of_segments - 1)
		{
			byte[] segment = segments.get(i);
			merged_segments.add(segment);
		}

		int number_of_merged_segments = merged_segments.size();

		ArrayList<byte[]> compressed_segments = new ArrayList<byte[]>();

		max_segment_bytelength = 0;
		int[] compressed_iterations = new int[number_of_merged_segments];
		int number_of_uncompressed_adjacent_segments = 0;
		int previous_iterations = 1;
		for(i = 0; i < number_of_merged_segments; i++)
		{
			byte[] segment = merged_segments.get(i);
			byte[] compressed_segment = StringMapper.compressStrings(segment);
			compressed_segments.add(compressed_segment);
			if(compressed_segment.length - 1 > max_segment_bytelength)
				max_segment_bytelength = compressed_segment.length - 1;
			compressed_iterations[i] = StringMapper.getIterations(compressed_segment);
			if((previous_iterations == 0 || previous_iterations == 16) && (compressed_iterations[i] == 0 || compressed_iterations[i] == 16))
				number_of_uncompressed_adjacent_segments++;
			previous_iterations = compressed_iterations[i];
		}

		int number_of_compressed_segments = compressed_segments.size();
		int min_segment_bytelength = Integer.MAX_VALUE;
		
		// When we divide up the original string, we don't account for transform
		// iterations of the original string so if all the segments merge into one
		// we need to copy the last byte of the string to the single
		// merged segment to restore that information.
		if(number_of_compressed_segments == 1)
		{
			byte[] segment = compressed_segments.get(0);
			if(segment.length != string.length)
				System.out.println("Single segment is not the same length as the original string.");
			else
				segment[segment.length - 1] = string[string.length - 1];
			result.add(compressed_segments);
			result.add(max_segment_bytelength);	
			min_segment_bytelength = max_segment_bytelength;
			result.add(min_segment_bytelength);
		} 
		else
		{
			ArrayList<byte[]> combined_segments = new ArrayList<byte[]>();
			if(number_of_uncompressed_adjacent_segments == 0)
			{
				// Still need to get minimum segment byte length.
				combined_segments = compressed_segments;
				for(i = 0; i < number_of_compressed_segments; i++)
				{
					byte [] segment = compressed_segments.get(i);
					if(min_segment_bytelength > segment.length - 1)
						min_segment_bytelength = segment.length - 1;	
				}
			}
			else
			{
				for(i = 0; i < number_of_compressed_segments - 1; i++)
				{
					byte[] current_segment = compressed_segments.get(i);
					int current_iterations = StringMapper.getIterations(current_segment);

					byte[] next_segment = compressed_segments.get(i + 1);
					int next_iterations = StringMapper.getIterations(next_segment);

					if((current_iterations == 0 || current_iterations == 16) && (next_iterations == 0 || next_iterations == 16))
					{
						int j = 1;
						while ((next_iterations == 0 || next_iterations == 16) && i + j + 1 < number_of_compressed_segments)
						{
							next_segment = compressed_segments.get(i + j + 1);
							next_iterations = StringMapper.getIterations(next_segment);
							if(next_iterations == 0 || next_iterations == 16)
								j++;
						}

						int combined_length = 0;
						for(int k = 0; k < j + 1; k++)
						{
							byte[] segment = compressed_segments.get(i + k);
							combined_length += segment.length - 1;
						}
						combined_length++;

						if(max_segment_bytelength < combined_length - 1)
							max_segment_bytelength = combined_length - 1;
						if(min_segment_bytelength > combined_length - 1)
							min_segment_bytelength = combined_length - 1;

						byte[] combined_segment = new byte[combined_length];

						int m = 0;
						for(int k = 0; k < j + 1; k++)
						{
							byte[] segment = compressed_segments.get(i + k);
							for(int n = 0; n < segment.length - 1; n++)
								combined_segment[m + n] = segment[n];
							m += segment.length - 1;
						}

						if(i + j == number_of_compressed_segments - 1)
						{
							int last_bitlength = (combined_segment.length - 1) * 8;
							int k = last_extra_bits >> 5;
							k &= 7;
							last_bitlength -= k;
							double zero_ratio = StringMapper.getZeroRatio(combined_segment, last_bitlength, bit_table);
							combined_segment[combined_segment.length - 1] = last_extra_bits;
							if(zero_ratio < .5)
								combined_segment[combined_segment.length - 1] |= 16;
							combined_segments.add(combined_segment);
						} 
						else
						{
							double zero_ratio = StringMapper.getZeroRatio(combined_segment, (combined_segment.length - 1) * 8, bit_table);
							if(zero_ratio < .5)
								combined_segment[combined_segment.length - 1] |= 16;
							combined_segments.add(combined_segment);
						}
						
						i += j;
					} 
					else
					{
						// Either this segment is compressed or the next segment is compressed.
						combined_segments.add(current_segment);
						if(max_segment_bytelength < current_segment.length - 1)
							max_segment_bytelength = current_segment.length - 1;
						if(min_segment_bytelength > current_segment.length - 1)
							min_segment_bytelength = current_segment.length - 1;
					}
				}

				if(i == number_of_compressed_segments - 1)
				{
					byte[] segment = compressed_segments.get(i);
					combined_segments.add(segment);
					if(max_segment_bytelength < segment.length - 1)
						max_segment_bytelength = segment.length - 1;
					if(min_segment_bytelength > segment.length - 1)
						min_segment_bytelength = segment.length - 1;
				}

				// Similarly to the merge process, we need to restore information if we combined
				// down to one segment.
				if(combined_segments.size() == 1)
				{
					byte[] segment = combined_segments.get(0);
					if(segment.length != string.length)
						System.out.println("Single segment is not the same length as the original string.");
					else
						segment[segment.length - 1] = string[string.length - 1];
				}
			}

			result.add(combined_segments);
			result.add(max_segment_bytelength);
			result.add(min_segment_bytelength);
			
		}

		return result;
	}
	
	public static ArrayList getSegmentedData2(byte[] string, int minimum_bitlength, int lambda)
	{
		ArrayList result = new ArrayList();
		if(minimum_bitlength % 8 != 0)
		{
			System.out.println("Minimum segment bitlength must be a multiple of 8.");
			return result;
		}

		int string_bitlength = StringMapper.getBitlength(string);
		int number_of_segments = string_bitlength / minimum_bitlength;
		int segment_bitlength = minimum_bitlength;
		int segment_bytelength = minimum_bitlength / 8;
		segment_bytelength++;

		int remainder = string_bitlength % minimum_bitlength;
		int last_segment_bitlength = minimum_bitlength + remainder;
		int last_segment_bytelength = last_segment_bitlength / 8;

		byte last_extra_bits = 0;
		if(remainder % 8 != 0)
		{
			last_segment_bytelength++;
			last_extra_bits = (byte) (8 - remainder);
			last_extra_bits <<= 5;
		}

		last_segment_bytelength++;

		int max_segment_bytelength = last_segment_bytelength;

		ArrayList<byte[]> segments = new ArrayList<byte[]>();

		int[] bit_table = StringMapper.getBitTable();

		int[] bitlength = new int[number_of_segments];
		int[] bin_number = new int[number_of_segments];
		double bin = .05;

		for(int i = 0; i < number_of_segments; i++)
		{
			if(i < number_of_segments - 1)
			{
				byte[] segment = new byte[segment_bytelength];
				for(int j = 0; j < segment.length - 1; j++)
					segment[j] = string[i * (segment_bytelength - 1) + j];
				bitlength[i] = segment_bitlength;

				double zero_ratio = StringMapper.getZeroRatio(segment, segment_bitlength, bit_table);
				bin_number[i] = getBinNumber(zero_ratio, bin);
				if(zero_ratio < .5)
				{
					byte transform_iterations = 16;
					segment[segment.length - 1] = transform_iterations;
				}

				segments.add(segment);
			} 
			else
			{
				// The only segment that might be uneven.
				byte[] segment = new byte[last_segment_bytelength];
				for(int j = 0; j < segment.length - 1; j++)
					segment[j] = string[i * (segment_bytelength - 1) + j];
				segment[segment.length - 1] = last_extra_bits;
				bitlength[i] = last_segment_bitlength;

				double zero_ratio = StringMapper.getZeroRatio(segment, last_segment_bitlength, bit_table);
				bin_number[i] = getBinNumber(zero_ratio, bin);
				if(zero_ratio < .5)
				{
					byte transform_iterations = 16;
					segment[segment.length - 1] |= transform_iterations;
				}

				segments.add(segment);
			}
		}

		ArrayList<byte[]> merged_segments = new ArrayList<byte[]>();

		int number_of_bins = (int) (1. / bin);
		int bin_divider    = number_of_bins / 2;
		int difference     = bin_divider / 2;
		
		int i = 0;
		for(i = 0; i < number_of_segments - 1; i++)
		{
			int current_bin = bin_number[i];
			int j = 1;
			int next_bin = bin_number[i + j];

            boolean isTrue = false;

			// From least to most exclusive.
			if(lambda == 0)
			{
				if((current_bin < bin_divider && next_bin < bin_divider) || (current_bin >= bin_divider && next_bin >= bin_divider))
					isTrue = true;
			}
			else if(lambda == 1)
			{
				if((current_bin < bin_divider - 1 && next_bin < bin_divider - 1) || (current_bin > bin_divider + 1 && next_bin > bin_divider + 1))
					isTrue = true;   
			}
			else if(lambda == 2)
			{
				if((current_bin < bin_divider - 1 && next_bin < bin_divider - 1) || (current_bin > bin_divider + 1 && next_bin > bin_divider + 1))
					isTrue = true;   
			}
			else if(lambda == 3)
			{
				if(current_bin == next_bin)
					isTrue = true;	
			}
			
			// if((current_bin < bin_divider - 1 && next_bin < bin_divider - 1) || (current_bin > bin_divider + 1 && next_bin > bin_divider + 1)  || ((current_bin == bin_divider - 1 || current_bin == bin_divider) && (next_bin == bin_divider - 1 || next_bin == bin_divider)))
			// if((current_bin < bin_divider && next_bin < bin_divider && Math.abs(current_bin - next_bin) < difference) || (current_bin >= bin_divider && next_bin >= bin_divider && Math.abs(current_bin - next_bin) < difference))
			if((current_bin < bin_divider && next_bin < bin_divider) || (current_bin >= bin_divider && next_bin >= bin_divider))
			// if(current_bin == next_bin)
			{
				//while(((current_bin < bin_divider - 1 && next_bin < bin_divider - 1) || (current_bin > bin_divider + 1 && next_bin > bin_divider + 1)  || ((current_bin == bin_divider - 1 || current_bin == bin_divider) && (next_bin == bin_divider - 1 || next_bin == bin_divider))) && i + j < number_of_segments - 1)
				// while(((current_bin < bin_divider && next_bin < bin_divider && Math.abs(current_bin - next_bin) < difference) || (current_bin >= bin_divider && next_bin >= bin_divider && Math.abs(current_bin - next_bin) < difference)) && i + j < number_of_segments - 1 )
				while (((current_bin < bin_divider && next_bin < bin_divider) || (current_bin >= bin_divider && next_bin >= bin_divider)) && i + j < number_of_segments - 1)
				// while((current_bin == next_bin) && i + j < number_of_segments - 1)
				{
					next_bin = bin_number[i + j + 1];
					//if((current_bin < bin_divider - 1 && next_bin < bin_divider - 1) || (current_bin > bin_divider + 1 && next_bin > bin_divider + 1)  || ((current_bin == bin_divider - 1 || current_bin == bin_divider) && (next_bin == bin_divider - 1 || next_bin == bin_divider)))
					//if((current_bin < bin_divider && next_bin < bin_divider && Math.abs(current_bin - next_bin) < difference) || (current_bin >= bin_divider && next_bin >= bin_divider && Math.abs(current_bin - next_bin) < difference))
					if((current_bin < bin_divider && next_bin < bin_divider) || (current_bin >= bin_divider && next_bin >= bin_divider))
					// if(current_bin == next_bin)
					    j++;
				}

				int merged_bytelength = j * (segment_bytelength - 1);
				if(i + j == number_of_segments - 1)
					merged_bytelength += last_segment_bytelength - 1;
				else
					merged_bytelength += segment_bytelength - 1;
				merged_bytelength++;
				byte[] merged_segment = new byte[merged_bytelength];
				int m = 0;
				for(int k = 0; k < j + 1; k++)
				{
					byte[] segment = segments.get(i + k);
					for(int n = 0; n < segment.length - 1; n++)
						merged_segment[m + n] = segment[n];
					m += segment.length - 1;
				}
				
				if(i + j == number_of_segments - 1)
					merged_segment[merged_bytelength - 1] = last_extra_bits;
				if(current_bin < bin_divider)
					merged_segment[merged_bytelength - 1] |= 16;
				merged_segments.add(merged_segment);

				i += j;

			} 
			else
			{
				byte[] segment = segments.get(i);
				merged_segments.add(segment);
			}
		}

		if(i == number_of_segments - 1)
		{
			byte[] segment = segments.get(i);
			merged_segments.add(segment);
		}

		int number_of_merged_segments = merged_segments.size();

		ArrayList<byte[]> compressed_segments = new ArrayList<byte[]>();

		max_segment_bytelength = 0;
		int[] compressed_iterations = new int[number_of_merged_segments];
		int number_of_uncompressed_adjacent_segments = 0;
		int previous_iterations = 1;
		for(i = 0; i < number_of_merged_segments; i++)
		{
			byte[] segment = merged_segments.get(i);
			byte[] compressed_segment = StringMapper.compressStrings(segment);
			compressed_segments.add(compressed_segment);
			if(compressed_segment.length - 1 > max_segment_bytelength)
				max_segment_bytelength = compressed_segment.length - 1;
			compressed_iterations[i] = StringMapper.getIterations(compressed_segment);
			if((previous_iterations == 0 || previous_iterations == 16) && (compressed_iterations[i] == 0 || compressed_iterations[i] == 16))
				number_of_uncompressed_adjacent_segments++;
			previous_iterations = compressed_iterations[i];
		}

		// This should be the same as the number of merged segments.
		int number_of_compressed_segments = compressed_segments.size();

		// We need to check this after the strings get compressed.
		int min_segment_bytelength = Integer.MAX_VALUE;
		
		// When we divide up the original string, we don't account for transform
		// iterations of the original string so if all the segments merge into one
		// we need to copy the last byte of the string to the single
		// merged segment to restore that information.
		if(number_of_compressed_segments == 1)
		{
			byte[] segment = compressed_segments.get(0);
			if(segment.length != string.length)
				System.out.println("Single segment is not the same length as the original string.");
			else
				segment[segment.length - 1] = string[string.length - 1];
			result.add(compressed_segments);
			result.add(max_segment_bytelength);	
			min_segment_bytelength = max_segment_bytelength;
			result.add(min_segment_bytelength);
		} 
		else
		{
			// We want to combine uncompressed segments to reduce overhead
			// and create a pattern of compressed and
			// uncompressed segments.
			ArrayList<byte[]> combined_segments = new ArrayList<byte[]>();
			
			// We don't do anything because there aren't any adjacent uncompressed
			// segments to combine.
			if(number_of_uncompressed_adjacent_segments == 0)
			{
				// Still need to get minimum segment byte length.
				combined_segments = compressed_segments;
				for(i = 0; i < number_of_compressed_segments; i++)
				{
					byte [] segment = compressed_segments.get(i);
					if(min_segment_bytelength > segment.length - 1)
						min_segment_bytelength = segment.length - 1;	
				}
			}
			else
			{
				for(i = 0; i < number_of_compressed_segments - 1; i++)
				{
					byte[] current_segment = compressed_segments.get(i);
					int current_iterations = StringMapper.getIterations(current_segment);

					byte[] next_segment = compressed_segments.get(i + 1);
					int next_iterations = StringMapper.getIterations(next_segment);

					if((current_iterations == 0 || current_iterations == 16) && (next_iterations == 0 || next_iterations == 16))
					{
						int j = 1;
						while ((next_iterations == 0 || next_iterations == 16) && i + j + 1 < number_of_compressed_segments)
						{
							next_segment = compressed_segments.get(i + j + 1);
							next_iterations = StringMapper.getIterations(next_segment);
							if(next_iterations == 0 || next_iterations == 16)
								j++;
						}

						int combined_length = 0;
						for(int k = 0; k < j + 1; k++)
						{
							byte[] segment = compressed_segments.get(i + k);
							combined_length += segment.length - 1;
						}
						combined_length++;

						if(max_segment_bytelength < combined_length - 1)
							max_segment_bytelength = combined_length - 1;
						if(min_segment_bytelength > combined_length - 1)
							min_segment_bytelength = combined_length - 1;

						byte[] combined_segment = new byte[combined_length];

						int m = 0;
						for(int k = 0; k < j + 1; k++)
						{
							byte[] segment = compressed_segments.get(i + k);
							for(int n = 0; n < segment.length - 1; n++)
								combined_segment[m + n] = segment[n];
							m += segment.length - 1;
						}

						if(i + j == number_of_compressed_segments - 1)
						{
							int last_bitlength = (combined_segment.length - 1) * 8;
							int k = last_extra_bits >> 5;
							k &= 7;
							last_bitlength -= k;
							double zero_ratio = StringMapper.getZeroRatio(combined_segment, last_bitlength, bit_table);
							combined_segment[combined_segment.length - 1] = last_extra_bits;
							if(zero_ratio < .5)
								combined_segment[combined_segment.length - 1] |= 16;
							combined_segments.add(combined_segment);
						} 
						else
						{
							double zero_ratio = StringMapper.getZeroRatio(combined_segment, (combined_segment.length - 1) * 8, bit_table);
							if(zero_ratio < .5)
								combined_segment[combined_segment.length - 1] |= 16;
							combined_segments.add(combined_segment);
						}
						
						i += j;
					} 
					else
					{
						// Either this segment is compressed or the next segment is compressed.
						combined_segments.add(current_segment);
						if(max_segment_bytelength < current_segment.length - 1)
							max_segment_bytelength = current_segment.length - 1;
						if(min_segment_bytelength > current_segment.length - 1)
							min_segment_bytelength = current_segment.length - 1;
					}
				}

				if(i == number_of_compressed_segments - 1)
				{
					byte[] segment = compressed_segments.get(i);
					combined_segments.add(segment);
					if(max_segment_bytelength < segment.length - 1)
						max_segment_bytelength = segment.length - 1;
					if(min_segment_bytelength > segment.length - 1)
						min_segment_bytelength = segment.length - 1;
				}

				// Similarly to the merge process, we need to restore information if we combined
				// down to one segment.
				if(combined_segments.size() == 1)
				{
					byte[] segment = combined_segments.get(0);
					if(segment.length != string.length)
						System.out.println("Single segment is not the same length as the original string.");
					else
						segment[segment.length - 1] = string[string.length - 1];
				}
			}

			result.add(combined_segments);
			result.add(max_segment_bytelength);
			result.add(min_segment_bytelength);
			
		}

		return result;
	}
	
	
	// The following two segmentation schemes produce shorter total bit lengths than a simple merge and
	// combine, but then are not compressed as effectively by deflate() afterwards.
	public static ArrayList getSegmentedData3(byte[] string, int minimum_bitlength)
	{
		ArrayList result = new ArrayList();
		if(minimum_bitlength % 8 != 0)
		{
			System.out.println("Minimum segment bitlength must be a multiple of 8.");
			return result;
		}

		int string_bitlength = StringMapper.getBitlength(string);
		int number_of_segments = string_bitlength / minimum_bitlength;
		int segment_bitlength = minimum_bitlength;
		int segment_bytelength = minimum_bitlength / 8;
		segment_bytelength++;

		int remainder = string_bitlength % minimum_bitlength;
		int last_segment_bitlength = minimum_bitlength + remainder;
		int last_segment_bytelength = last_segment_bitlength / 8;

		byte last_extra_bits = 0;
		if(remainder % 8 != 0)
		{
			last_segment_bytelength++;
			last_extra_bits = (byte) (8 - remainder);
			last_extra_bits <<= 5;
		}

		last_segment_bytelength++;

		int max_segment_bytelength = last_segment_bytelength;

		ArrayList<byte[]> segments = new ArrayList<byte[]>();

		int[] bit_table = StringMapper.getBitTable();

		int[] bitlength = new int[number_of_segments];
		int[] bin_number = new int[number_of_segments];
		double bin = .05;

		for(int i = 0; i < number_of_segments; i++)
		{
			if(i < number_of_segments - 1)
			{
				byte[] segment = new byte[segment_bytelength];
				for(int j = 0; j < segment.length - 1; j++)
					segment[j] = string[i * (segment_bytelength - 1) + j];
				bitlength[i] = segment_bitlength;

				double zero_ratio = StringMapper.getZeroRatio(segment, segment_bitlength, bit_table);
				bin_number[i] = getBinNumber(zero_ratio, bin);
				if(zero_ratio < .5)
				{
					byte transform_iterations = 16;
					segment[segment.length - 1] = transform_iterations;
				}

				segments.add(segment);
			} 
			else
			{
				// The only segment that might be uneven.
				byte[] segment = new byte[last_segment_bytelength];
				for(int j = 0; j < segment.length - 1; j++)
					segment[j] = string[i * (segment_bytelength - 1) + j];
				segment[segment.length - 1] = last_extra_bits;
				bitlength[i] = last_segment_bitlength;

				double zero_ratio = StringMapper.getZeroRatio(segment, last_segment_bitlength, bit_table);
				bin_number[i] = getBinNumber(zero_ratio, bin);
				if(zero_ratio < .5)
				{
					byte transform_iterations = 16;
					segment[segment.length - 1] |= transform_iterations;
				}

				segments.add(segment);
			}
		}

		ArrayList<byte[]> merged_segments = new ArrayList<byte[]>();

		int number_of_bins = (int) (1. / bin);
		int bin_divider    = number_of_bins / 2;
		int difference     = bin_divider / 2;
		
		int i = 0;
		for(i = 0; i < number_of_segments - 1; i++)
		{
			int current_bin = bin_number[i];
			int j = 1;
			int next_bin = bin_number[i + j];

			// The more restrictive the merging process is, the more improvement there is when splicing bits.  
			// The shortest total bit length is still produced by the simplest merge and then splicing bits. 
			
			//if((current_bin < bin_divider && next_bin < bin_divider && Math.abs(current_bin - next_bin) < difference) || (current_bin >= bin_divider && next_bin >= bin_divider && Math.abs(current_bin - next_bin) < difference))
			if((current_bin < bin_divider && next_bin < bin_divider) || (current_bin >= bin_divider && next_bin >= bin_divider))
			{
				//while(((current_bin < bin_divider && next_bin < bin_divider && Math.abs(current_bin - next_bin) < difference) || (current_bin >= bin_divider && next_bin >= bin_divider && Math.abs(current_bin - next_bin) < difference)) && i + j < number_of_segments - 1)
				while (((current_bin < bin_divider && next_bin < bin_divider) || (current_bin >= bin_divider && next_bin >= bin_divider)) && i + j < number_of_segments - 1)
				{
					next_bin = bin_number[i + j + 1];
					//if((current_bin < bin_divider && next_bin < bin_divider && Math.abs(current_bin - next_bin) < difference) || (current_bin >= bin_divider && next_bin >= bin_divider && Math.abs(current_bin - next_bin) < difference))
					if((current_bin < bin_divider && next_bin < bin_divider) || (current_bin >= bin_divider && next_bin >= bin_divider))
					    j++;
				}

				int merged_bytelength = j * (segment_bytelength - 1);
				if(i + j == number_of_segments - 1)
					merged_bytelength += last_segment_bytelength - 1;
				else
					merged_bytelength += segment_bytelength - 1;
				merged_bytelength++;
				byte[] merged_segment = new byte[merged_bytelength];
				int m = 0;
				for(int k = 0; k < j + 1; k++)
				{
					byte[] segment = segments.get(i + k);
					for(int n = 0; n < segment.length - 1; n++)
						merged_segment[m + n] = segment[n];
					m += segment.length - 1;
				}
				
				if(i + j == number_of_segments - 1)
					merged_segment[merged_bytelength - 1] = last_extra_bits;
				if(current_bin < bin_divider)
					merged_segment[merged_bytelength - 1] |= 16;
				merged_segments.add(merged_segment);

				i += j;

			} 
			else
			{
				byte[] segment = segments.get(i);
				merged_segments.add(segment);
			}
		}

		if(i == number_of_segments - 1)
		{
			byte[] segment = segments.get(i);
			merged_segments.add(segment);
		}

		int number_of_merged_segments = merged_segments.size();

		ArrayList<byte[]> compressed_segments = new ArrayList<byte[]>();

		max_segment_bytelength = 0;
		int[] compressed_iterations = new int[number_of_merged_segments];
		int number_of_uncompressed_adjacent_segments = 0;
		int previous_iterations = 1;
		
		int total_merged_bitlength = 0;
		for(i = 0; i < number_of_merged_segments; i++)
		{
			byte[] segment = merged_segments.get(i);
			byte[] compressed_segment = StringMapper.compressStrings(segment);
			compressed_segments.add(compressed_segment);
			
			int current_bitlength = StringMapper.getBitlength(compressed_segment);
			total_merged_bitlength += current_bitlength;
			if(compressed_segment.length - 1 > max_segment_bytelength)
				max_segment_bytelength = compressed_segment.length - 1;
			compressed_iterations[i] = StringMapper.getIterations(compressed_segment);
			if((previous_iterations == 0 || previous_iterations == 16) && (compressed_iterations[i] == 0 || compressed_iterations[i] == 16))
				number_of_uncompressed_adjacent_segments++;
			previous_iterations = compressed_iterations[i];
		}

		int number_of_compressed_segments = compressed_segments.size();
		int min_segment_bytelength = Integer.MAX_VALUE;
		
		// When we divide up the original string, we don't account for transform
		// iterations of the original string so if all the segments merge into one
		// we need to copy the last byte of the string to the single
		// merged segment to restore that information.
		if(number_of_compressed_segments == 1)
		{
			byte[] segment = compressed_segments.get(0);
			if(segment.length != string.length)
				System.out.println("Single segment is not the same length as the original string.");
			else
				segment[segment.length - 1] = string[string.length - 1];
			result.add(compressed_segments);
			result.add(max_segment_bytelength);	
			min_segment_bytelength = max_segment_bytelength;
			result.add(min_segment_bytelength);
			return result;
		} 
		else
		{
			ArrayList<byte[]> combined_segments = new ArrayList<byte[]>();
			if(number_of_uncompressed_adjacent_segments == 0)
			{
				// Need to check minimum segment byte length, since we compressed segments.
				combined_segments = compressed_segments;
				for(i = 0; i < number_of_compressed_segments; i++)
				{
					byte [] segment = compressed_segments.get(i);
					if(min_segment_bytelength > segment.length - 1)
						min_segment_bytelength = segment.length - 1;	
				}
			}
			else
			{
				for(i = 0; i < number_of_compressed_segments - 1; i++)
				{
					byte[] current_segment = compressed_segments.get(i);
					int current_iterations = StringMapper.getIterations(current_segment);

					byte[] next_segment = compressed_segments.get(i + 1);
					int next_iterations = StringMapper.getIterations(next_segment);

					if((current_iterations == 0 || current_iterations == 16) && (next_iterations == 0 || next_iterations == 16))
					{
						int j = 1;
						while ((next_iterations == 0 || next_iterations == 16) && i + j + 1 < number_of_compressed_segments)
						{
							next_segment = compressed_segments.get(i + j + 1);
							next_iterations = StringMapper.getIterations(next_segment);
							if(next_iterations == 0 || next_iterations == 16)
								j++;
						}

						int combined_length = 0;
						for(int k = 0; k < j + 1; k++)
						{
							byte[] segment = compressed_segments.get(i + k);
							combined_length += segment.length - 1;
						}
						combined_length++;

						if(max_segment_bytelength < combined_length - 1)
							max_segment_bytelength = combined_length - 1;
						if(min_segment_bytelength > combined_length - 1)
							min_segment_bytelength = combined_length - 1;

						byte[] combined_segment = new byte[combined_length];

						int m = 0;
						for(int k = 0; k < j + 1; k++)
						{
							byte[] segment = compressed_segments.get(i + k);
							for(int n = 0; n < segment.length - 1; n++)
								combined_segment[m + n] = segment[n];
							m += segment.length - 1;
						}

						if(i + j == number_of_compressed_segments - 1)
						{
							int last_bitlength = (combined_segment.length - 1) * 8;
							int k = last_extra_bits >> 5;
							k &= 7;
							last_bitlength -= k;
							double zero_ratio = StringMapper.getZeroRatio(combined_segment, last_bitlength, bit_table);
							combined_segment[combined_segment.length - 1] = last_extra_bits;
							if(zero_ratio < .5)
								combined_segment[combined_segment.length - 1] |= 16;
							combined_segments.add(combined_segment);
						} 
						else
						{
							double zero_ratio = StringMapper.getZeroRatio(combined_segment, (combined_segment.length - 1) * 8, bit_table);
							if(zero_ratio < .5)
								combined_segment[combined_segment.length - 1] |= 16;
							combined_segments.add(combined_segment);
						}
						
						i += j;
					} 
					else
					{
						// Either this segment is compressed or the next segment is compressed.
						combined_segments.add(current_segment);
						if(max_segment_bytelength < current_segment.length - 1)
							max_segment_bytelength = current_segment.length - 1;
						if(min_segment_bytelength > current_segment.length - 1)
							min_segment_bytelength = current_segment.length - 1;
					}
				}

				if(i == number_of_compressed_segments - 1)
				{
					// The index i might or might not have been incremented to include the last segment.
					byte[] segment = compressed_segments.get(i);
					combined_segments.add(segment);
					if(max_segment_bytelength < segment.length - 1)
						max_segment_bytelength = segment.length - 1;
					if(min_segment_bytelength > segment.length - 1)
						min_segment_bytelength = segment.length - 1;
				}

				// Similarly to the merge process, we need to restore information if we combined
				// down to one segment.
				if(combined_segments.size() == 1)
				{
					byte[] segment = combined_segments.get(0);
					if(segment.length != string.length)
						System.out.println("Single segment is not the same length as the original string.");
					else
						segment[segment.length - 1] = string[string.length - 1];
					result.add(combined_segments);
					result.add(max_segment_bytelength);
					result.add(min_segment_bytelength);
					return result;
					
				}
			}
			
			int number_of_combined_segments = combined_segments.size();
			boolean [] isCompressed         = new boolean[number_of_combined_segments];

			int total_combined_bitlength = 0;
			int total_combined_decompressed_bitlength = 0;
			for(i = 0; i < number_of_combined_segments; i++)
			{
				byte[] segment = combined_segments.get(i);
				int iterations = StringMapper.getIterations(segment);
				int current_bitlength = StringMapper.getBitlength(segment);
				total_combined_bitlength += current_bitlength;

				if(iterations != 0 && iterations != 16)
				{
					isCompressed[i] = true;	
					byte [] decompressed_segment = StringMapper.decompressStrings(segment);
					int decompressed_bitlength = StringMapper.getBitlength(decompressed_segment);
					total_combined_decompressed_bitlength += decompressed_bitlength;
				}
				else
					total_combined_decompressed_bitlength += current_bitlength;	
			}
			
			ArrayList<byte[]> spliced_segments = new ArrayList<byte[]>();
			int               total_spliced_bits = 0;
			int               max_spliced_bits   = 0;
			byte []           first_segment    = combined_segments.get(0);
			spliced_segments.add(first_segment);
			for(i = 1; i < number_of_combined_segments; i++)
			{
				byte[] current_segment  = combined_segments.get(i);
				
				if(isCompressed[i])
					spliced_segments.add(current_segment);
				else
				{
					// This shouldn't happen because we combined adjacent uncompressed segments.
					if(!isCompressed[i - 1])
						System.out.println("Previous segment is uncompressed.");
					byte[] previous_segment       = combined_segments.get(i - 1);
					byte[] decompressed_segment   = StringMapper.decompressStrings(previous_segment);
					int    previous_bitlength     = StringMapper.getBitlength(previous_segment);
					int    decompressed_bitlength = StringMapper.getBitlength(decompressed_segment);
					int    current_bitlength = StringMapper.getBitlength(current_segment);
					
					byte[] augmented_segment      = new byte[decompressed_segment.length + 1];
					for(int j = 0; j < decompressed_segment.length - 1; j++)
						augmented_segment[j] = decompressed_segment[j];
					augmented_segment[decompressed_segment.length - 1] = current_segment[0];
					
					int max_reduction = Integer.MIN_VALUE;
					int spliced_bits      = 0;
					for(int j = 0; j < 8; j ++)
					{
						// Might want to check here if added byte changes zero ratio, but we'll hide that in
						// compressStrings2.
						augmented_segment[decompressed_segment.length] = decompressed_segment[decompressed_segment.length - 1];
						int  augmented_bitlength = decompressed_bitlength + 8 - j;
						int  odd_bits            = augmented_bitlength % 8;
						byte extra_bits          = 0;
						if(odd_bits != 0)
							extra_bits = (byte)(8 - odd_bits);
						extra_bits <<= 5;
						augmented_segment[decompressed_segment.length] |= extra_bits;
						
						byte [] compressed_segment   = StringMapper.compressStrings2(augmented_segment);
						int     compressed_bitlength = StringMapper.getBitlength(compressed_segment);
						
						int current_bits  = 8 - j;
						int bit_reduction = spliced_bits - (compressed_bitlength - previous_bitlength);
						if(bit_reduction > max_reduction)
						{
							max_reduction = bit_reduction;
							spliced_bits  = current_bits;
						}
					}
					
					if(spliced_bits > 0 && max_reduction > 0)
					{
						total_spliced_bits += spliced_bits;
						if(spliced_bits > max_spliced_bits)
							max_spliced_bits = spliced_bits;
						
						augmented_segment[decompressed_segment.length] = decompressed_segment[decompressed_segment.length - 1];
						int  augmented_bitlength                       = decompressed_bitlength + spliced_bits;
						// Set extra bits.
						int  odd_bits   = augmented_bitlength % 8;
						byte extra_bits = 0;
						if(odd_bits != 0)
							extra_bits = (byte)(8 - odd_bits);
						extra_bits    <<= 5;
						augmented_segment[decompressed_segment.length]  |= extra_bits;
						
						decompressed_bitlength = StringMapper.getBitlength(augmented_segment);
						
						// Check the zero ratio in compressStrings2, after splicing bits.
						byte [] compressed_segment   = StringMapper.compressStrings2(augmented_segment);
						// Check if the maximum byte length changed.
						if(max_segment_bytelength < compressed_segment.length - 1)
							max_segment_bytelength = compressed_segment.length - 1;
						
						// Replace the previous compressed segment.
						spliced_segments.set(i - 1, compressed_segment);
						
						if(spliced_bits == 8)
						{
							// Simple case: drop the second to the last byte and preserve bit type.
							// The bit type might be wrong but with uncompressed segments it's 
							// not usually important, and we can use compressStrings2 to check it.
							byte [] reduced_segment = new byte[current_segment.length - 1];
							for(int j = 0; j < reduced_segment.length - 1; j++)
								reduced_segment[j] = current_segment[j];

							// Preserving the bit type and length, which will still
							// be correct since we removed exactly 8 bits.
							int j                 = current_segment.length - 1;
							reduced_segment[j - 1] = current_segment[j];
							
							// Check if the minimum byte length changed.
							if(min_segment_bytelength > reduced_segment.length - 1)
								min_segment_bytelength = reduced_segment.length - 1;
							
							// Add the segment.
							spliced_segments.add(reduced_segment);
						}
						else
						{
							byte [] clipped_segment = new byte[current_segment.length - 1];
							for(int j = 0; j < clipped_segment.length; j++)
								clipped_segment[j] = current_segment[j];
							// Shift out the the bits that were added to a previous segment.
							byte [] clipped_shifted_segment = shiftRight(clipped_segment, spliced_bits);
							
							// Since we're not assuming even bit lengths to start with, the
							// reduced segment byte length might be shorter than the current one.
							// This is also needed to allow splicing more than 8 bits.
							int reduced_bitlength = current_bitlength - spliced_bits;
							int byte_length       = reduced_bitlength / 8;
							if(reduced_bitlength % 8 != 0)
								byte_length++;
							byte_length++;
							byte [] reduced_segment = new byte[byte_length];
							if(min_segment_bytelength > byte_length - 1)
								min_segment_bytelength = byte_length - 1;
							
							for(int j = 0; j < reduced_segment.length - 1; j++)
								reduced_segment[j] = clipped_shifted_segment[j];
							
							// Set extra bits.
							odd_bits            = reduced_bitlength % 8;
							extra_bits          = 0;
							if(odd_bits != 0)
								extra_bits = (byte)(8 - odd_bits);
							extra_bits <<= 5;
							reduced_segment[reduced_segment.length - 1]  |= extra_bits;
							
							spliced_segments.add(reduced_segment);
							
						}
					}
					else
					{
						// The current uncompressed segment is added to the spliced list with no modification.
						spliced_segments.add(current_segment);
					}
				}
				
			}	
			
            int number_of_spliced_segments = spliced_segments.size();
			
			int total_spliced_bitlength = 0;
			int total_spliced_decompressed_bitlength = 0;
			for(i = 0; i < number_of_spliced_segments; i++)
			{
				byte[] segment = spliced_segments.get(i);
				int iterations = StringMapper.getIterations(segment);
				int current_bitlength = StringMapper.getBitlength(segment);
				total_spliced_bitlength += current_bitlength;

				if(iterations != 0 && iterations != 16)
				{
					byte [] decompressed_segment = StringMapper.decompressStrings(segment);
					int decompressed_bitlength = StringMapper.getBitlength(decompressed_segment);
					total_spliced_decompressed_bitlength += decompressed_bitlength;
				}
				else
					total_spliced_decompressed_bitlength += current_bitlength;	
			}
			
			System.out.println("Total combined bitlength was " + total_combined_bitlength);
			System.out.println("Total spliced bitlength was "  + total_spliced_bitlength);
			System.out.println("Total spliced bits was "       + total_spliced_bits);
			System.out.println("Maximum spliced bits was "     + max_spliced_bits);
			System.out.println();
		
		    result.add(spliced_segments);
		    result.add(max_segment_bytelength);
		    result.add(min_segment_bytelength);
			return result;	
		}			
	}


	public static ArrayList getSegmentedData4(byte[] string, int minimum_bitlength)
	{
		ArrayList result = new ArrayList();
		if(minimum_bitlength % 8 != 0)
		{
			System.out.println("Minimum segment bitlength must be a multiple of 8.");
			return result;
		}

		int string_bitlength   = StringMapper.getBitlength(string);
		int number_of_segments = string_bitlength / minimum_bitlength;
		int segment_bitlength  = minimum_bitlength;
		int segment_bytelength = minimum_bitlength / 8;
		segment_bytelength++;

		int remainder               = string_bitlength % minimum_bitlength;
		int last_segment_bitlength  = minimum_bitlength + remainder;
		int last_segment_bytelength = last_segment_bitlength / 8;

		byte last_extra_bits = 0;
		if(remainder % 8 != 0)
		{
			last_segment_bytelength++;
			last_extra_bits = (byte) (8 - remainder);
			last_extra_bits <<= 5;
		}

		last_segment_bytelength++;

		int max_segment_bytelength = last_segment_bytelength;

		ArrayList<byte[]> segments = new ArrayList<byte[]>();

		int[] bit_table = StringMapper.getBitTable();

		int[] bitlength  = new int[number_of_segments];
		int[] bin_number = new int[number_of_segments];
		double bin = .05;

		for(int i = 0; i < number_of_segments; i++)
		{
			if(i < number_of_segments - 1)
			{
				byte[] segment = new byte[segment_bytelength];
				for(int j = 0; j < segment.length - 1; j++)
					segment[j] = string[i * (segment_bytelength - 1) + j];
				bitlength[i] = segment_bitlength;

				double zero_ratio = StringMapper.getZeroRatio(segment, segment_bitlength, bit_table);
				bin_number[i] = getBinNumber(zero_ratio, bin);
				if(zero_ratio < .5)
				{
					byte transform_iterations = 16;
					segment[segment.length - 1] = transform_iterations;
				}

				segments.add(segment);
			} 
			else
			{
				// The only segment that might be uneven.
				byte[] segment = new byte[last_segment_bytelength];
				for(int j = 0; j < segment.length - 1; j++)
					segment[j] = string[i * (segment_bytelength - 1) + j];
				segment[segment.length - 1] = last_extra_bits;
				bitlength[i] = last_segment_bitlength;

				double zero_ratio = StringMapper.getZeroRatio(segment, last_segment_bitlength, bit_table);
				bin_number[i] = getBinNumber(zero_ratio, bin);
				if(zero_ratio < .5)
				{
					byte transform_iterations = 16;
					segment[segment.length - 1] |= transform_iterations;
				}

				segments.add(segment);
			}
		}

		ArrayList<byte[]> merged_segments = new ArrayList<byte[]>();

		int number_of_bins = (int) (1. / bin);
		int bin_divider    = number_of_bins / 2;
		int difference     = bin_divider / 2;
		
		int i = 0;
		for(i = 0; i < number_of_segments - 1; i++)
		{
			int current_bin = bin_number[i];
			int j = 1;
			int next_bin = bin_number[i + j];

			if((current_bin < bin_divider && next_bin < bin_divider) || (current_bin >= bin_divider && next_bin >= bin_divider))
			{
				while (((current_bin < bin_divider && next_bin < bin_divider) || (current_bin >= bin_divider && next_bin >= bin_divider)) && i + j < number_of_segments - 1)
				{
					next_bin = bin_number[i + j + 1];
					if((current_bin < bin_divider && next_bin < bin_divider) || (current_bin >= bin_divider && next_bin >= bin_divider))
					    j++;
				}

				int merged_bytelength = j * (segment_bytelength - 1);
				if(i + j == number_of_segments - 1)
					merged_bytelength += last_segment_bytelength - 1;
				else
					merged_bytelength += segment_bytelength - 1;
				merged_bytelength++;
				byte[] merged_segment = new byte[merged_bytelength];
				int m = 0;
				for(int k = 0; k < j + 1; k++)
				{
					byte[] segment = segments.get(i + k);
					for(int n = 0; n < segment.length - 1; n++)
						merged_segment[m + n] = segment[n];
					m += segment.length - 1;
				}
				
				if(i + j == number_of_segments - 1)
					merged_segment[merged_bytelength - 1] = last_extra_bits;
				if(current_bin < bin_divider)
					merged_segment[merged_bytelength - 1] |= 16;
				merged_segments.add(merged_segment);

				i += j;

			} 
			else
			{
				byte[] segment = segments.get(i);
				merged_segments.add(segment);
			}
		}

		if(i == number_of_segments - 1)
		{
			byte[] segment = segments.get(i);
			merged_segments.add(segment);
		}

		int number_of_merged_segments = merged_segments.size();

		ArrayList<byte[]> compressed_segments = new ArrayList<byte[]>();

		max_segment_bytelength = 0;
		int[] compressed_iterations = new int[number_of_merged_segments];
		int number_of_uncompressed_adjacent_segments = 0;
		int previous_iterations = 1;
		
		int total_merged_bitlength = 0;
		for(i = 0; i < number_of_merged_segments; i++)
		{
			byte[] segment = merged_segments.get(i);
			byte[] compressed_segment = StringMapper.compressStrings(segment);
			compressed_segments.add(compressed_segment);
			
			int current_bitlength = StringMapper.getBitlength(compressed_segment);
			total_merged_bitlength += current_bitlength;
			if(compressed_segment.length - 1 > max_segment_bytelength)
				max_segment_bytelength = compressed_segment.length - 1;
			compressed_iterations[i] = StringMapper.getIterations(compressed_segment);
			if((previous_iterations == 0 || previous_iterations == 16) && (compressed_iterations[i] == 0 || compressed_iterations[i] == 16))
				number_of_uncompressed_adjacent_segments++;
			previous_iterations = compressed_iterations[i];
		}

		int number_of_compressed_segments = compressed_segments.size();
		int min_segment_bytelength        = Integer.MAX_VALUE;
		
		if(number_of_compressed_segments == 1)
		{
			// When we divide up the original string, we don't account for transform
			// iterations of the original string so if all the segments merge into one
			// we need to copy the last byte of the string to the single
			// merged segment to restore that information.
			
			byte[] segment = compressed_segments.get(0);
			if(segment.length != string.length)
				System.out.println("Single segment is not the same length as the original string.");
			else
				segment[segment.length - 1] = string[string.length - 1];
			result.add(compressed_segments);
			result.add(max_segment_bytelength);	
			min_segment_bytelength = max_segment_bytelength;
			result.add(min_segment_bytelength);
			return result;
		} 
		else
		{
			// We want to combine uncompressed segments to reduce overhead
			// and create a pattern of alternating compressed and uncompressed segments.
			ArrayList<byte[]> combined_segments = new ArrayList<byte[]>();
			
			if(number_of_uncompressed_adjacent_segments == 0)
			{
				// We don't do anything because there aren't any adjacent uncompressed
				// segments to combine.  Still need to check minimum segment byte length.
				combined_segments = compressed_segments;
				for(i = 0; i < number_of_compressed_segments; i++)
				{
					byte [] segment = compressed_segments.get(i);
					if(min_segment_bytelength > segment.length - 1)
						min_segment_bytelength = segment.length - 1;	
				}
			}
			else
			{
				for(i = 0; i < number_of_compressed_segments - 1; i++)
				{
					byte[] current_segment = compressed_segments.get(i);
					int current_iterations = StringMapper.getIterations(current_segment);

					byte[] next_segment = compressed_segments.get(i + 1);
					int next_iterations = StringMapper.getIterations(next_segment);

					if((current_iterations == 0 || current_iterations == 16) && (next_iterations == 0 || next_iterations == 16))
					{
						int j = 1;
						while ((next_iterations == 0 || next_iterations == 16) && i + j + 1 < number_of_compressed_segments)
						{
							next_segment = compressed_segments.get(i + j + 1);
							next_iterations = StringMapper.getIterations(next_segment);
							if(next_iterations == 0 || next_iterations == 16)
								j++;
						}

						int combined_length = 0;
						for(int k = 0; k < j + 1; k++)
						{
							byte[] segment = compressed_segments.get(i + k);
							combined_length += segment.length - 1;
						}
						combined_length++;

						if(max_segment_bytelength < combined_length - 1)
							max_segment_bytelength = combined_length - 1;
						if(min_segment_bytelength > combined_length - 1)
							min_segment_bytelength = combined_length - 1;

						byte[] combined_segment = new byte[combined_length];

						int m = 0;
						for(int k = 0; k < j + 1; k++)
						{
							byte[] segment = compressed_segments.get(i + k);
							for(int n = 0; n < segment.length - 1; n++)
								combined_segment[m + n] = segment[n];
							m += segment.length - 1;
						}

						if(i + j == number_of_compressed_segments - 1)
						{
							int last_bitlength = (combined_segment.length - 1) * 8;
							int k = last_extra_bits >> 5;
							k &= 7;
							last_bitlength -= k;
							double zero_ratio = StringMapper.getZeroRatio(combined_segment, last_bitlength, bit_table);
							combined_segment[combined_segment.length - 1] = last_extra_bits;
							if(zero_ratio < .5)
								combined_segment[combined_segment.length - 1] |= 16;
							combined_segments.add(combined_segment);
						} 
						else
						{
							double zero_ratio = StringMapper.getZeroRatio(combined_segment, (combined_segment.length - 1) * 8, bit_table);
							if(zero_ratio < .5)
								combined_segment[combined_segment.length - 1] |= 16;
							combined_segments.add(combined_segment);
						}
						
						i += j;
					} 
					else
					{
						// Either this segment is compressed or the next segment is compressed.
						combined_segments.add(current_segment);
						if(max_segment_bytelength < current_segment.length - 1)
							max_segment_bytelength = current_segment.length - 1;
						if(min_segment_bytelength > current_segment.length - 1)
							min_segment_bytelength = current_segment.length - 1;
					}
				}

				if(i == number_of_compressed_segments - 1)
				{
					byte[] segment = compressed_segments.get(i);
					combined_segments.add(segment);
					if(max_segment_bytelength < segment.length - 1)
						max_segment_bytelength = segment.length - 1;
					if(min_segment_bytelength > segment.length - 1)
						min_segment_bytelength = segment.length - 1;
				}

				// Similarly to the merge process, we need to restore information if we combined
				// down to one segment.
				if(combined_segments.size() == 1)
				{
					byte[] segment = combined_segments.get(0);
					if(segment.length != string.length)
						System.out.println("Single segment is not the same length as the original string.");
					else
						segment[segment.length - 1] = string[string.length - 1];
					result.add(combined_segments);
					result.add(max_segment_bytelength);
					result.add(min_segment_bytelength);
					return result;
					
				}
			}
			
			int number_of_combined_segments = combined_segments.size();
			boolean [] isCompressed         = new boolean[number_of_combined_segments];
		
			int total_combined_bitlength              = 0;
			int total_combined_decompressed_bitlength = 0;
			for(i = 0; i < number_of_combined_segments; i++)
			{
				byte[] segment = combined_segments.get(i);
				int iterations = StringMapper.getIterations(segment);
				int current_bitlength = StringMapper.getBitlength(segment);
				total_combined_bitlength += current_bitlength;

				if(iterations != 0 && iterations != 16)
				{
					isCompressed[i] = true;	
					byte [] decompressed_segment = StringMapper.decompressStrings(segment);
					int decompressed_bitlength = StringMapper.getBitlength(decompressed_segment);
					total_combined_decompressed_bitlength += decompressed_bitlength;
				}
				else
					total_combined_decompressed_bitlength += current_bitlength;	
			}
			
			ArrayList<byte[]> spliced_segments = new ArrayList<byte[]>();
            int total_spliced_bits             = 0;
            int max_spliced_bits               = 0;
            
			for(i = 0; i < number_of_combined_segments - 1; i++)
			{
				byte[] current_segment = combined_segments.get(i);
				if(isCompressed[i])
					spliced_segments.add(current_segment); 
				else
				{
					if(!isCompressed[i + 1])
						System.out.println("Following segment is uncompressed.");	
					int     current_bitlength      = StringMapper.getBitlength(current_segment); 
					byte [] next_segment           = combined_segments.get(i + 1);
					int     next_bitlength         = StringMapper.getBitlength(next_segment);
					byte [] decompressed_segment   = StringMapper.decompressStrings(next_segment);
					int	    decompressed_bitlength = StringMapper.getBitlength(decompressed_segment);
					
					// Construct an initial byte for the segment being augmented.
					byte init_byte    = 0;
					if(current_bitlength % 8 != 0)
					{
						if(current_bitlength < 8)
						{
							init_byte      = current_segment[current_segment.length - 2];
							int extra_bits = 8 - current_bitlength;
							init_byte <<= extra_bits;
						}
						else
						{
			    	    	        byte first_byte    = current_segment[current_segment.length - 3];
			    	    	        byte second_byte   = current_segment[current_segment.length - 2];
			    	    	        
			    	    	        int  odd_bits   = current_bitlength % 8;
			    	    	        int  extra_bits = 8 - odd_bits;
			    	          	
			    	            first_byte  >>= odd_bits;
							byte mask     = getTrailingMask(extra_bits);
							first_byte   &= mask;
							
							second_byte <<= extra_bits;
						
			    	            init_byte  = first_byte;
			    	            init_byte |= second_byte;
						}
					}
					else
					{
			    	        init_byte = current_segment[current_segment.length - 2];	
					}
					
					byte [] clipped_segment = new byte[decompressed_segment.length];
					for(int j = 0; j < decompressed_segment.length - 1; j++)
						clipped_segment[j + 1] = decompressed_segment[j];
					
					clipped_segment[0] = init_byte;
					if(current_bitlength < 8)
					{
						// Since the initial byte contains empty bits.
					    clipped_segment = shiftRight(clipped_segment, 8 - current_bitlength);
					}
					
					int number_of_bits = 8;
					if(number_of_bits > current_bitlength)
						number_of_bits = current_bitlength;
					
					int spliced_bits  = 0;
					
					// Lets us know if any of the spliced segments broke even if it gets to 0.
					int max_reduction = -1;
				
					for(int j = 0; j < number_of_bits; j++)
					{
						int current_bits      = number_of_bits - j;
						int shifted_bitlength = decompressed_bitlength + current_bits;
						byte odd_bits         = (byte)(shifted_bitlength % 8);
						byte extra_bits       = (byte)(8 - odd_bits);
						extra_bits          <<= 5;
						
						byte [] augmented_segment  = new byte[clipped_segment.length + 1];
						if(current_bits < number_of_bits)
						{
							byte [] shifted_segment = shiftRight(clipped_segment, 8 - current_bits);
						    for(int k = 0; k < shifted_segment.length; k++)
						    	    augmented_segment[k]  = shifted_segment[k];
						}
						else
						{
							for(int k = 0; k < clipped_segment.length; k++)
					    	        augmented_segment[k]  = clipped_segment[k];	
						}
						
						double ratio = StringMapper.getZeroRatio(augmented_segment, shifted_bitlength);
						if(ratio < .5)
							augmented_segment[augmented_segment.length - 1] = 16;
						augmented_segment[augmented_segment.length - 1]    |= extra_bits;
						byte [] compressed_segment   = StringMapper.compressStrings3(augmented_segment);
						int     compressed_bitlength = StringMapper.getBitlength(compressed_segment);
					
						int bit_reduction = current_bits - (compressed_bitlength - next_bitlength);
						if(bit_reduction > max_reduction)
						{
							max_reduction = bit_reduction;
							spliced_bits  = current_bits;
						}
					}
					
					if(max_reduction > 0)  
					{
						total_spliced_bits += spliced_bits;
						if(spliced_bits > max_spliced_bits)
							max_spliced_bits = spliced_bits;
						
					    int reduced_bitlength  = current_bitlength - spliced_bits;
					    if(reduced_bitlength > 0)
					    {
					        int reduced_bytelength = reduced_bitlength / 8;
					        if(reduced_bitlength % 8 != 0)
					    	        reduced_bytelength++;
					        reduced_bytelength++;
					        
					        byte [] reduced_segment = new byte[reduced_bytelength];
					        for(int j = 0; j < reduced_segment.length - 1; j++)
					        	    reduced_segment[j] = current_segment[j];
					        int odd_bits = reduced_bitlength % 8;
					        byte extra_bits = 0;
					        if(odd_bits != 0)
					        {
					        	    extra_bits = (byte)(8 - odd_bits);
					        	    extra_bits <<= 5;
					        }
					        if(extra_bits != 0)
					        {
					        	    // Clear any bits that might have been set in the original segment.
					        	    byte mask = getTrailingMask(odd_bits);
					        	    reduced_segment[reduced_segment.length - 2] &= mask;
					        	    reduced_segment[reduced_segment.length - 1] = extra_bits;
					        	    reduced_bitlength = StringMapper.getBitlength(reduced_segment);
					        }
					        spliced_segments.add(reduced_segment);
					    }
					    
					    if(reduced_bitlength == 0)
					    	    System.out.println("Eliminated uncompressed segment.");
					    
					    // For now, the clipped length and clipped/shifted lengths are the same
					    // since we're limiting the number of spliced bits to 8.
					    byte [] augmented_segment  = new byte[clipped_segment.length + 1];
					    if(spliced_bits == 8)
					    {
					        for(int k = 0; k < clipped_segment.length; k++)
		    	                    augmented_segment[k] = clipped_segment[k];
					    }
					    else
					    {
					    	    byte [] shifted_segment = shiftRight(clipped_segment, 8 - spliced_bits);
					    	    for(int k = 0; k < clipped_segment.length; k++)
		    	                    augmented_segment[k] = shifted_segment[k];
					    }
					   
					    int augmented_bitlength = decompressed_bitlength + spliced_bits;
					    
					    int odd_bits   = augmented_bitlength % 8;
					    byte extra_bits = 0;
					    if(odd_bits != 0)
					        extra_bits = (byte)(8 - odd_bits);
					    extra_bits    <<= 5;
					    
					    double ratio   = StringMapper.getZeroRatio(augmented_segment, augmented_bitlength);
					    if(ratio < .5)
					        	augmented_segment[augmented_segment.length - 1] = 16;   
					    augmented_segment[augmented_segment.length - 1] |= extra_bits;
					  
					    
					    byte [] compressed_segment   = StringMapper.compressStrings(augmented_segment);
					    if((compressed_segment.length - 1) > max_segment_bytelength)
					    	    max_segment_bytelength  = compressed_segment.length - 1;  
				
					    spliced_segments.add(compressed_segment);
					    i++;
					}
					else
					{
						spliced_segments.add(current_segment);
					}
				}
			}
			
			// We have one segment left that hasn't been processed, since it doesn't have a following segment.
			byte [] last_segment = combined_segments.get(number_of_combined_segments - 1);
			spliced_segments.add(last_segment);
		
			int number_of_spliced_segments = spliced_segments.size();
			
			int total_spliced_bitlength = 0;
			int total_spliced_decompressed_bitlength = 0;
			for(i = 0; i < number_of_spliced_segments; i++)
			{
				byte[] segment = spliced_segments.get(i);
				int iterations = StringMapper.getIterations(segment);
				int current_bitlength = StringMapper.getBitlength(segment);
				total_spliced_bitlength += current_bitlength;

				if(iterations != 0 && iterations != 16)
				{
					byte [] decompressed_segment = StringMapper.decompressStrings(segment);
					int decompressed_bitlength = StringMapper.getBitlength(decompressed_segment);
					total_spliced_decompressed_bitlength += decompressed_bitlength;
				}
				else
					total_spliced_decompressed_bitlength += current_bitlength;	
			}
			
			System.out.println("Total combined bitlength was " + total_combined_bitlength);
			System.out.println("Total spliced bitlength was " + total_spliced_bitlength);
			System.out.println("Total spliced bits was " + total_spliced_bits	);
			System.out.println("Maximum spliced bits was " + max_spliced_bits	);
			System.out.println();
		    
			result.add(spliced_segments);
			result.add(max_segment_bytelength);
			result.add(min_segment_bytelength);
			return result;
		}
	}
	
	/*
	// The following two segmentation schemes produce shorter total bit lengths than a simple merge and
	// combine, but then are not compressed as effectively by deflate() afterwards.
	public static ArrayList getSegmentedData3(byte[] string, int minimum_bitlength)
	{
		ArrayList result = new ArrayList();
		if(minimum_bitlength % 8 != 0)
		{
			System.out.println("Minimum segment bitlength must be a multiple of 8.");
			return result;
		}

		int string_bitlength = StringMapper.getBitlength(string);
		int number_of_segments = string_bitlength / minimum_bitlength;
		int segment_bitlength = minimum_bitlength;
		int segment_bytelength = minimum_bitlength / 8;
		segment_bytelength++;

		int remainder = string_bitlength % minimum_bitlength;
		int last_segment_bitlength = minimum_bitlength + remainder;
		int last_segment_bytelength = last_segment_bitlength / 8;

		byte last_extra_bits = 0;
		if(remainder % 8 != 0)
		{
			last_segment_bytelength++;
			last_extra_bits = (byte) (8 - remainder);
			last_extra_bits <<= 5;
		}

		last_segment_bytelength++;

		int max_segment_bytelength = last_segment_bytelength;

		ArrayList<byte[]> segments = new ArrayList<byte[]>();

		int[] bit_table = StringMapper.getBitTable();

		int[] bitlength = new int[number_of_segments];
		int[] bin_number = new int[number_of_segments];
		double bin = .05;

		for(int i = 0; i < number_of_segments; i++)
		{
			if(i < number_of_segments - 1)
			{
				byte[] segment = new byte[segment_bytelength];
				for(int j = 0; j < segment.length - 1; j++)
					segment[j] = string[i * (segment_bytelength - 1) + j];
				bitlength[i] = segment_bitlength;

				double zero_ratio = StringMapper.getZeroRatio(segment, segment_bitlength, bit_table);
				bin_number[i] = getBinNumber(zero_ratio, bin);
				if(zero_ratio < .5)
				{
					byte transform_iterations = 16;
					segment[segment.length - 1] = transform_iterations;
				}

				segments.add(segment);
			} 
			else
			{
				// The only segment that might be uneven.
				byte[] segment = new byte[last_segment_bytelength];
				for(int j = 0; j < segment.length - 1; j++)
					segment[j] = string[i * (segment_bytelength - 1) + j];
				segment[segment.length - 1] = last_extra_bits;
				bitlength[i] = last_segment_bitlength;

				double zero_ratio = StringMapper.getZeroRatio(segment, last_segment_bitlength, bit_table);
				bin_number[i] = getBinNumber(zero_ratio, bin);
				if(zero_ratio < .5)
				{
					byte transform_iterations = 16;
					segment[segment.length - 1] |= transform_iterations;
				}

				segments.add(segment);
			}
		}

		ArrayList<byte[]> merged_segments = new ArrayList<byte[]>();

		int number_of_bins = (int) (1. / bin);
		int bin_divider    = number_of_bins / 2;
		int difference     = bin_divider / 2;
		
		int i = 0;
		for(i = 0; i < number_of_segments - 1; i++)
		{
			int current_bin = bin_number[i];
			int j = 1;
			int next_bin = bin_number[i + j];

			//  The more restrictive the merging process is, the more improvement there is when splicing bits.  The shortest total bit length is still produced by the simplest
			//  merge and then splicing bits. 
			
			//if((current_bin < bin_divider && next_bin < bin_divider && Math.abs(current_bin - next_bin) < difference) || (current_bin >= bin_divider && next_bin >= bin_divider && Math.abs(current_bin - next_bin) < difference))
			if((current_bin < bin_divider && next_bin < bin_divider) || (current_bin >= bin_divider && next_bin >= bin_divider))
			{
				//while(((current_bin < bin_divider && next_bin < bin_divider && Math.abs(current_bin - next_bin) < difference) || (current_bin >= bin_divider && next_bin >= bin_divider && Math.abs(current_bin - next_bin) < difference)) && i + j < number_of_segments - 1)
				while (((current_bin < bin_divider && next_bin < bin_divider) || (current_bin >= bin_divider && next_bin >= bin_divider)) && i + j < number_of_segments - 1)
				{
					next_bin = bin_number[i + j + 1];
					//if((current_bin < bin_divider && next_bin < bin_divider && Math.abs(current_bin - next_bin) < difference) || (current_bin >= bin_divider && next_bin >= bin_divider && Math.abs(current_bin - next_bin) < difference))
					if((current_bin < bin_divider && next_bin < bin_divider) || (current_bin >= bin_divider && next_bin >= bin_divider))
					    j++;
				}

				int merged_bytelength = j * (segment_bytelength - 1);
				if(i + j == number_of_segments - 1)
					merged_bytelength += last_segment_bytelength - 1;
				else
					merged_bytelength += segment_bytelength - 1;
				merged_bytelength++;
				byte[] merged_segment = new byte[merged_bytelength];
				int m = 0;
				for(int k = 0; k < j + 1; k++)
				{
					byte[] segment = segments.get(i + k);
					for(int n = 0; n < segment.length - 1; n++)
						merged_segment[m + n] = segment[n];
					m += segment.length - 1;
				}
				
				if(i + j == number_of_segments - 1)
					merged_segment[merged_bytelength - 1] = last_extra_bits;
				if(current_bin < bin_divider)
					merged_segment[merged_bytelength - 1] |= 16;
				merged_segments.add(merged_segment);

				i += j;

			} 
			else
			{
				byte[] segment = segments.get(i);
				merged_segments.add(segment);
			}
		}

		if(i == number_of_segments - 1)
		{
			byte[] segment = segments.get(i);
			merged_segments.add(segment);
		}

		int number_of_merged_segments = merged_segments.size();

		ArrayList<byte[]> compressed_segments = new ArrayList<byte[]>();

		max_segment_bytelength = 0;
		int[] compressed_iterations = new int[number_of_merged_segments];
		int number_of_uncompressed_adjacent_segments = 0;
		int previous_iterations = 1;
		
		int total_merged_bitlength = 0;
		for(i = 0; i < number_of_merged_segments; i++)
		{
			byte[] segment = merged_segments.get(i);
			byte[] compressed_segment = StringMapper.compressStrings(segment);
			compressed_segments.add(compressed_segment);
			
			int current_bitlength = StringMapper.getBitlength(compressed_segment);
			total_merged_bitlength += current_bitlength;
			if(compressed_segment.length - 1 > max_segment_bytelength)
				max_segment_bytelength = compressed_segment.length - 1;
			compressed_iterations[i] = StringMapper.getIterations(compressed_segment);
			if((previous_iterations == 0 || previous_iterations == 16) && (compressed_iterations[i] == 0 || compressed_iterations[i] == 16))
				number_of_uncompressed_adjacent_segments++;
			previous_iterations = compressed_iterations[i];
		}

		// This should be the same as the number of merged segments.
		int number_of_compressed_segments = compressed_segments.size();

		// We need to check this after the strings get compressed.
		int min_segment_bytelength = Integer.MAX_VALUE;
		
		// When we divide up the original string, we don't account for transform
		// iterations of the original string so if all the segments merge into one
		// we need to copy the last byte of the string to the single
		// merged segment to restore that information.
		if(number_of_compressed_segments == 1)
		{
			byte[] segment = compressed_segments.get(0);
			if(segment.length != string.length)
				System.out.println("Single segment is not the same length as the original string.");
			else
				segment[segment.length - 1] = string[string.length - 1];
			result.add(compressed_segments);
			result.add(max_segment_bytelength);	
			min_segment_bytelength = max_segment_bytelength;
			result.add(min_segment_bytelength);
			return result;
		} 
		else
		{
			// We want to combine uncompressed segments to reduce overhead
			// and create a pattern of compressed and
			// uncompressed segments.
			ArrayList<byte[]> combined_segments = new ArrayList<byte[]>();
			
			// We don't do anything because there aren't any adjacent uncompressed
			// segments to combine.
			if(number_of_uncompressed_adjacent_segments == 0)
			{
				// Still need to check minimum segment byte length.
				combined_segments = compressed_segments;
				for(i = 0; i < number_of_compressed_segments; i++)
				{
					byte [] segment = compressed_segments.get(i);
					if(min_segment_bytelength > segment.length - 1)
						min_segment_bytelength = segment.length - 1;	
				}
			}
			else
			{
				
				for(i = 0; i < number_of_compressed_segments - 1; i++)
				{
					byte[] current_segment = compressed_segments.get(i);
					int current_iterations = StringMapper.getIterations(current_segment);

					byte[] next_segment = compressed_segments.get(i + 1);
					int next_iterations = StringMapper.getIterations(next_segment);

					if((current_iterations == 0 || current_iterations == 16) && (next_iterations == 0 || next_iterations == 16))
					{
						int j = 1;
						while ((next_iterations == 0 || next_iterations == 16) && i + j + 1 < number_of_compressed_segments)
						{
							next_segment = compressed_segments.get(i + j + 1);
							next_iterations = StringMapper.getIterations(next_segment);
							if(next_iterations == 0 || next_iterations == 16)
								j++;
						}

						int combined_length = 0;
						for(int k = 0; k < j + 1; k++)
						{
							byte[] segment = compressed_segments.get(i + k);
							combined_length += segment.length - 1;
						}
						combined_length++;

						if(max_segment_bytelength < combined_length - 1)
							max_segment_bytelength = combined_length - 1;
						if(min_segment_bytelength > combined_length - 1)
							min_segment_bytelength = combined_length - 1;

						byte[] combined_segment = new byte[combined_length];

						int m = 0;
						for(int k = 0; k < j + 1; k++)
						{
							byte[] segment = compressed_segments.get(i + k);
							for(int n = 0; n < segment.length - 1; n++)
								combined_segment[m + n] = segment[n];
							m += segment.length - 1;
						}

						if(i + j == number_of_compressed_segments - 1)
						{
							int last_bitlength = (combined_segment.length - 1) * 8;
							int k = last_extra_bits >> 5;
							k &= 7;
							last_bitlength -= k;
							double zero_ratio = StringMapper.getZeroRatio(combined_segment, last_bitlength, bit_table);
							combined_segment[combined_segment.length - 1] = last_extra_bits;
							if(zero_ratio < .5)
								combined_segment[combined_segment.length - 1] |= 16;
							combined_segments.add(combined_segment);
						} 
						else
						{
							double zero_ratio = StringMapper.getZeroRatio(combined_segment, (combined_segment.length - 1) * 8, bit_table);
							if(zero_ratio < .5)
								combined_segment[combined_segment.length - 1] |= 16;
							combined_segments.add(combined_segment);
						}
						
						i += j;
					} 
					else
					{
						// Either this segment is compressed or the next segment is compressed.
						combined_segments.add(current_segment);
						if(max_segment_bytelength < current_segment.length - 1)
							max_segment_bytelength = current_segment.length - 1;
						if(min_segment_bytelength > current_segment.length - 1)
							min_segment_bytelength = current_segment.length - 1;
					}
				}

				if(i == number_of_compressed_segments - 1)
				{
					byte[] segment = compressed_segments.get(i);
					combined_segments.add(segment);
					if(max_segment_bytelength < segment.length - 1)
						max_segment_bytelength = segment.length - 1;
					if(min_segment_bytelength > segment.length - 1)
						min_segment_bytelength = segment.length - 1;
				}

				// Similarly to the merge process, we need to restore information if we combined
				// down to one segment.
				if(combined_segments.size() == 1)
				{
					byte[] segment = combined_segments.get(0);
					if(segment.length != string.length)
						System.out.println("Single segment is not the same length as the original string.");
					else
						segment[segment.length - 1] = string[string.length - 1];
					result.add(combined_segments);
					result.add(max_segment_bytelength);
					result.add(min_segment_bytelength);
					return result;
					
				}
			}
			
			int number_of_combined_segments = combined_segments.size();
			boolean [] isCompressed         = new boolean[number_of_combined_segments];

			int total_combined_bitlength = 0;
			int total_combined_decompressed_bitlength = 0;
			for(i = 0; i < number_of_combined_segments; i++)
			{
				byte[] segment = combined_segments.get(i);
				int iterations = StringMapper.getIterations(segment);
				int current_bitlength = StringMapper.getBitlength(segment);
				total_combined_bitlength += current_bitlength;

				if(iterations != 0 && iterations != 16)
				{
					isCompressed[i] = true;	
					byte [] decompressed_segment = StringMapper.decompressStrings(segment);
					int decompressed_bitlength = StringMapper.getBitlength(decompressed_segment);
					total_combined_decompressed_bitlength += decompressed_bitlength;
				}
				else
					total_combined_decompressed_bitlength += current_bitlength;	
			}
			
			ArrayList<byte[]> spliced_segments = new ArrayList<byte[]>();
			int               total_spliced_bits = 0;
			byte []           first_segment    = combined_segments.get(0);
			spliced_segments.add(first_segment);
			for(i = 1; i < number_of_combined_segments; i++)
			{
				byte[] current_segment  = combined_segments.get(i);
				
				if(isCompressed[i])
					spliced_segments.add(current_segment);
				else
				{
					// This shouldn't happen because we combined adjacent uncompressed segments.
					if(!isCompressed[i - 1])
						System.out.println("Previous segment is uncompressed.");
					byte[] previous_segment       = combined_segments.get(i - 1);
					byte[] decompressed_segment   = StringMapper.decompressStrings(previous_segment);
					int    previous_bitlength     = StringMapper.getBitlength(previous_segment);
					int    decompressed_bitlength = StringMapper.getBitlength(decompressed_segment);
					int    current_bitlength = StringMapper.getBitlength(current_segment);
					
					byte[] augmented_segment      = new byte[decompressed_segment.length + 1];
					for(int j = 0; j < decompressed_segment.length - 1; j++)
						augmented_segment[j] = decompressed_segment[j];
					augmented_segment[decompressed_segment.length - 1] = current_segment[0];
					
					int max_reduction = Integer.MIN_VALUE;
					int max_bits      = -1;
					for(int j = 0; j < 8; j ++)
					{
						// Might want to check here if added byte changes zero ratio, but we'll hide that in
						// compressStrings2.
						augmented_segment[decompressed_segment.length] = decompressed_segment[decompressed_segment.length - 1];
						int  augmented_bitlength = decompressed_bitlength + 8 - j;
						int  odd_bits            = augmented_bitlength % 8;
						byte extra_bits          = 0;
						if(odd_bits != 0)
							extra_bits = (byte)(8 - odd_bits);
						extra_bits <<= 5;
						augmented_segment[decompressed_segment.length] |= extra_bits;
						
						byte [] compressed_segment   = StringMapper.compressStrings2(augmented_segment);
						int     compressed_bitlength = StringMapper.getBitlength(compressed_segment);
						
						int spliced_bits  = 8 - j;
						int bit_reduction = spliced_bits - (compressed_bitlength - previous_bitlength);
						if(bit_reduction > max_reduction)
						{
							max_reduction = bit_reduction;
							max_bits      = spliced_bits;
						}
					}
					
					if(max_bits > 0 && max_reduction > 0)
					{
						total_spliced_bits += max_bits;
						
						// We want to replace the previous compressed segment in the spliced list, and add
						// the reduced segment.
						
						// Replace the previous regular segment with an augmented segment.
						augmented_segment[decompressed_segment.length] = decompressed_segment[decompressed_segment.length - 1];
						int  augmented_bitlength = decompressed_bitlength + max_bits;
						// Set extra bits.
						int  odd_bits            = augmented_bitlength % 8;
						byte extra_bits          = 0;
						if(odd_bits != 0)
							extra_bits = (byte)(8 - odd_bits);
						extra_bits <<= 5;
						augmented_segment[decompressed_segment.length]  |= extra_bits;
						
						decompressed_bitlength = StringMapper.getBitlength(augmented_segment);
						
						// Check the zero ratio in compressStrings2, after splicing bits.
						byte [] compressed_segment   = StringMapper.compressStrings2(augmented_segment);
						// Check if the maximum byte length changed.
						if(max_segment_bytelength < compressed_segment.length - 1)
							max_segment_bytelength = compressed_segment.length - 1;
						
						// Replace the previous compressed segment.
						spliced_segments.set(i - 1, compressed_segment);
						
						if(max_bits == 8)
						{
							// Simple case: drop the second to the last byte and preserve bit type.
							// The bit type might be wrong but with uncompressed segments it's 
							// not usually important.
							byte [] reduced_segment = new byte[current_segment.length - 1];
							for(int j = 0; j < reduced_segment.length - 1; j++)
								reduced_segment[j] = current_segment[j];

							// Preserving the bit type and length, which will still
							// be correct since we removed exactly 8 bits.
							int j                 = current_segment.length - 1;
							reduced_segment[j - 1] = current_segment[j];
							
							// Check if the minimum byte length changed.
							if(min_segment_bytelength > reduced_segment.length - 1)
								min_segment_bytelength = reduced_segment.length - 1;
							
							// Add the segment.
							spliced_segments.add(reduced_segment);
						}
						else
						{
							// Clip the segment so content from metadata doesn't contaminate 
							// the data.
							byte [] clipped_segment = new byte[current_segment.length - 1];
							for(int j = 0; j < clipped_segment.length; j++)
								clipped_segment[j] = current_segment[j];
							// Shift out the spliced bits.
							byte [] clipped_shifted_segment = shiftRight(clipped_segment, max_bits);
							
							// Since we're not assuming even bit lengths to start with, the
							// reduced segment byte length might be shorter than the current one.
							// This is also needed to allow splicing more than 8 bits.
							int reduced_bitlength = current_bitlength - max_bits;
							int byte_length       = reduced_bitlength / 8;
							if(reduced_bitlength % 8 != 0)
								byte_length++;
							byte_length++;
							byte [] reduced_segment = new byte[byte_length];
							if(min_segment_bytelength > byte_length - 1)
								min_segment_bytelength = byte_length - 1;
							
							for(int j = 0; j < reduced_segment.length - 1; j++)
								reduced_segment[j] = clipped_shifted_segment[j];
							
							// Set extra bits.
							odd_bits            = reduced_bitlength % 8;
							extra_bits          = 0;
							if(odd_bits != 0)
								extra_bits = (byte)(8 - odd_bits);
							extra_bits <<= 5;
							reduced_segment[reduced_segment.length - 1]  |= extra_bits;
							
							spliced_segments.add(reduced_segment);
							
						}
					}
					else
					{
						// The current uncompressed segment is added to the spliced list with no modification.
						spliced_segments.add(current_segment);
					}
				}
				
			}	
			
            int number_of_spliced_segments = spliced_segments.size();
			
			int total_spliced_bitlength = 0;
			int total_spliced_decompressed_bitlength = 0;
			for(i = 0; i < number_of_spliced_segments; i++)
			{
				byte[] segment = spliced_segments.get(i);
				int iterations = StringMapper.getIterations(segment);
				int current_bitlength = StringMapper.getBitlength(segment);
				total_spliced_bitlength += current_bitlength;

				if(iterations != 0 && iterations != 16)
				{
					byte [] decompressed_segment = StringMapper.decompressStrings(segment);
					int decompressed_bitlength = StringMapper.getBitlength(decompressed_segment);
					total_spliced_decompressed_bitlength += decompressed_bitlength;
				}
				else
					total_spliced_decompressed_bitlength += current_bitlength;	
			}
			
			System.out.println("Total combined bitlength was " + total_combined_bitlength);
			System.out.println("Total spliced bitlength was " + total_spliced_bitlength);
			System.out.println("Total spliced bits was " + total_spliced_bits);
			System.out.println();
		
		    result.add(spliced_segments);
		    result.add(max_segment_bytelength);
		    result.add(min_segment_bytelength);
			return result;	
		}			
	}


	public static ArrayList getSegmentedData4(byte[] string, int minimum_bitlength)
	{
		ArrayList result = new ArrayList();
		if(minimum_bitlength % 8 != 0)
		{
			System.out.println("Minimum segment bitlength must be a multiple of 8.");
			return result;
		}

		int string_bitlength = StringMapper.getBitlength(string);
		int number_of_segments = string_bitlength / minimum_bitlength;
		int segment_bitlength = minimum_bitlength;
		int segment_bytelength = minimum_bitlength / 8;
		segment_bytelength++;

		int remainder = string_bitlength % minimum_bitlength;
		int last_segment_bitlength = minimum_bitlength + remainder;
		int last_segment_bytelength = last_segment_bitlength / 8;

		byte last_extra_bits = 0;
		if(remainder % 8 != 0)
		{
			last_segment_bytelength++;
			last_extra_bits = (byte) (8 - remainder);
			last_extra_bits <<= 5;
		}

		last_segment_bytelength++;

		int max_segment_bytelength = last_segment_bytelength;

		ArrayList<byte[]> segments = new ArrayList<byte[]>();

		int[] bit_table = StringMapper.getBitTable();

		int[] bitlength = new int[number_of_segments];
		int[] bin_number = new int[number_of_segments];
		double bin = .05;

		for(int i = 0; i < number_of_segments; i++)
		{
			if(i < number_of_segments - 1)
			{
				byte[] segment = new byte[segment_bytelength];
				for(int j = 0; j < segment.length - 1; j++)
					segment[j] = string[i * (segment_bytelength - 1) + j];
				bitlength[i] = segment_bitlength;

				double zero_ratio = StringMapper.getZeroRatio(segment, segment_bitlength, bit_table);
				bin_number[i] = getBinNumber(zero_ratio, bin);
				if(zero_ratio < .5)
				{
					byte transform_iterations = 16;
					segment[segment.length - 1] = transform_iterations;
				}

				segments.add(segment);
			} 
			else
			{
				// The only segment that might be uneven.
				byte[] segment = new byte[last_segment_bytelength];
				for(int j = 0; j < segment.length - 1; j++)
					segment[j] = string[i * (segment_bytelength - 1) + j];
				segment[segment.length - 1] = last_extra_bits;
				bitlength[i] = last_segment_bitlength;

				double zero_ratio = StringMapper.getZeroRatio(segment, last_segment_bitlength, bit_table);
				bin_number[i] = getBinNumber(zero_ratio, bin);
				if(zero_ratio < .5)
				{
					byte transform_iterations = 16;
					segment[segment.length - 1] |= transform_iterations;
				}

				segments.add(segment);
			}
		}

		ArrayList<byte[]> merged_segments = new ArrayList<byte[]>();

		int number_of_bins = (int) (1. / bin);
		int bin_divider    = number_of_bins / 2;
		int difference     = bin_divider / 2;
		
		int i = 0;
		for(i = 0; i < number_of_segments - 1; i++)
		{
			int current_bin = bin_number[i];
			int j = 1;
			int next_bin = bin_number[i + j];

			if((current_bin < bin_divider && next_bin < bin_divider) || (current_bin >= bin_divider && next_bin >= bin_divider))
			{
				while (((current_bin < bin_divider && next_bin < bin_divider) || (current_bin >= bin_divider && next_bin >= bin_divider)) && i + j < number_of_segments - 1)
				{
					next_bin = bin_number[i + j + 1];
					if((current_bin < bin_divider && next_bin < bin_divider) || (current_bin >= bin_divider && next_bin >= bin_divider))
					    j++;
				}

				int merged_bytelength = j * (segment_bytelength - 1);
				if(i + j == number_of_segments - 1)
					merged_bytelength += last_segment_bytelength - 1;
				else
					merged_bytelength += segment_bytelength - 1;
				merged_bytelength++;
				byte[] merged_segment = new byte[merged_bytelength];
				int m = 0;
				for(int k = 0; k < j + 1; k++)
				{
					byte[] segment = segments.get(i + k);
					for(int n = 0; n < segment.length - 1; n++)
						merged_segment[m + n] = segment[n];
					m += segment.length - 1;
				}
				
				if(i + j == number_of_segments - 1)
					merged_segment[merged_bytelength - 1] = last_extra_bits;
				if(current_bin < bin_divider)
					merged_segment[merged_bytelength - 1] |= 16;
				merged_segments.add(merged_segment);

				i += j;

			} 
			else
			{
				byte[] segment = segments.get(i);
				merged_segments.add(segment);
			}
		}

		if(i == number_of_segments - 1)
		{
			byte[] segment = segments.get(i);
			merged_segments.add(segment);
		}

		int number_of_merged_segments = merged_segments.size();

		ArrayList<byte[]> compressed_segments = new ArrayList<byte[]>();

		max_segment_bytelength = 0;
		int[] compressed_iterations = new int[number_of_merged_segments];
		int number_of_uncompressed_adjacent_segments = 0;
		int previous_iterations = 1;
		
		int total_merged_bitlength = 0;
		for(i = 0; i < number_of_merged_segments; i++)
		{
			byte[] segment = merged_segments.get(i);
			byte[] compressed_segment = StringMapper.compressStrings(segment);
			compressed_segments.add(compressed_segment);
			
			int current_bitlength = StringMapper.getBitlength(compressed_segment);
			total_merged_bitlength += current_bitlength;
			if(compressed_segment.length - 1 > max_segment_bytelength)
				max_segment_bytelength = compressed_segment.length - 1;
			compressed_iterations[i] = StringMapper.getIterations(compressed_segment);
			if((previous_iterations == 0 || previous_iterations == 16) && (compressed_iterations[i] == 0 || compressed_iterations[i] == 16))
				number_of_uncompressed_adjacent_segments++;
			previous_iterations = compressed_iterations[i];
		}

		// This should be the same as the number of merged segments.
		int number_of_compressed_segments = compressed_segments.size();

		// We need to check this after the strings get compressed.
		int min_segment_bytelength = Integer.MAX_VALUE;
		
		// When we divide up the original string, we don't account for transform
		// iterations of the original string so if all the segments merge into one
		// we need to copy the last byte of the string to the single
		// merged segment to restore that information.
		if(number_of_compressed_segments == 1)
		{
			byte[] segment = compressed_segments.get(0);
			if(segment.length != string.length)
				System.out.println("Single segment is not the same length as the original string.");
			else
				segment[segment.length - 1] = string[string.length - 1];
			result.add(compressed_segments);
			result.add(max_segment_bytelength);	
			min_segment_bytelength = max_segment_bytelength;
			result.add(min_segment_bytelength);
			return result;
		} 
		else
		{
			// We want to combine uncompressed segments to reduce overhead
			// and create a pattern of compressed and
			// uncompressed segments.
			ArrayList<byte[]> combined_segments = new ArrayList<byte[]>();
			
			// We don't do anything because there aren't any adjacent uncompressed
			// segments to combine.
			if(number_of_uncompressed_adjacent_segments == 0)
			{
				// Still need to check minimum segment byte length.
				combined_segments = compressed_segments;
				for(i = 0; i < number_of_compressed_segments; i++)
				{
					byte [] segment = compressed_segments.get(i);
					if(min_segment_bytelength > segment.length - 1)
						min_segment_bytelength = segment.length - 1;	
				}
			}
			else
			{
				for(i = 0; i < number_of_compressed_segments - 1; i++)
				{
					byte[] current_segment = compressed_segments.get(i);
					int current_iterations = StringMapper.getIterations(current_segment);

					byte[] next_segment = compressed_segments.get(i + 1);
					int next_iterations = StringMapper.getIterations(next_segment);

					if((current_iterations == 0 || current_iterations == 16) && (next_iterations == 0 || next_iterations == 16))
					{
						int j = 1;
						while ((next_iterations == 0 || next_iterations == 16) && i + j + 1 < number_of_compressed_segments)
						{
							next_segment = compressed_segments.get(i + j + 1);
							next_iterations = StringMapper.getIterations(next_segment);
							if(next_iterations == 0 || next_iterations == 16)
								j++;
						}

						int combined_length = 0;
						for(int k = 0; k < j + 1; k++)
						{
							byte[] segment = compressed_segments.get(i + k);
							combined_length += segment.length - 1;
						}
						combined_length++;

						if(max_segment_bytelength < combined_length - 1)
							max_segment_bytelength = combined_length - 1;
						if(min_segment_bytelength > combined_length - 1)
							min_segment_bytelength = combined_length - 1;

						byte[] combined_segment = new byte[combined_length];

						int m = 0;
						for(int k = 0; k < j + 1; k++)
						{
							byte[] segment = compressed_segments.get(i + k);
							for(int n = 0; n < segment.length - 1; n++)
								combined_segment[m + n] = segment[n];
							m += segment.length - 1;
						}

						if(i + j == number_of_compressed_segments - 1)
						{
							int last_bitlength = (combined_segment.length - 1) * 8;
							int k = last_extra_bits >> 5;
							k &= 7;
							last_bitlength -= k;
							double zero_ratio = StringMapper.getZeroRatio(combined_segment, last_bitlength, bit_table);
							combined_segment[combined_segment.length - 1] = last_extra_bits;
							if(zero_ratio < .5)
								combined_segment[combined_segment.length - 1] |= 16;
							combined_segments.add(combined_segment);
						} 
						else
						{
							double zero_ratio = StringMapper.getZeroRatio(combined_segment, (combined_segment.length - 1) * 8, bit_table);
							if(zero_ratio < .5)
								combined_segment[combined_segment.length - 1] |= 16;
							combined_segments.add(combined_segment);
						}
						
						i += j;
					} 
					else
					{
						// Either this segment is compressed or the next segment is compressed.
						combined_segments.add(current_segment);
						if(max_segment_bytelength < current_segment.length - 1)
							max_segment_bytelength = current_segment.length - 1;
						if(min_segment_bytelength > current_segment.length - 1)
							min_segment_bytelength = current_segment.length - 1;
					}
				}

				if(i == number_of_compressed_segments - 1)
				{
					byte[] segment = compressed_segments.get(i);
					combined_segments.add(segment);
					if(max_segment_bytelength < segment.length - 1)
						max_segment_bytelength = segment.length - 1;
					if(min_segment_bytelength > segment.length - 1)
						min_segment_bytelength = segment.length - 1;
				}

				// Similarly to the merge process, we need to restore information if we combined
				// down to one segment.
				if(combined_segments.size() == 1)
				{
					byte[] segment = combined_segments.get(0);
					if(segment.length != string.length)
						System.out.println("Single segment is not the same length as the original string.");
					else
						segment[segment.length - 1] = string[string.length - 1];
					result.add(combined_segments);
					result.add(max_segment_bytelength);
					result.add(min_segment_bytelength);
					return result;
					
				}
			}
			
			int number_of_combined_segments = combined_segments.size();
			boolean [] isCompressed         = new boolean[number_of_combined_segments];
		
			
			int total_combined_bitlength = 0;
			int total_combined_decompressed_bitlength = 0;
			for(i = 0; i < number_of_combined_segments; i++)
			{
				byte[] segment = combined_segments.get(i);
				int iterations = StringMapper.getIterations(segment);
				int current_bitlength = StringMapper.getBitlength(segment);
				total_combined_bitlength += current_bitlength;

				if(iterations != 0 && iterations != 16)
				{
					isCompressed[i] = true;	
					byte [] decompressed_segment = StringMapper.decompressStrings(segment);
					int decompressed_bitlength = StringMapper.getBitlength(decompressed_segment);
					total_combined_decompressed_bitlength += decompressed_bitlength;
				}
				else
					total_combined_decompressed_bitlength += current_bitlength;	
			}
			

			ArrayList<byte[]> spliced_segments = new ArrayList<byte[]>();
            int total_spliced_bits             = 0;
			for(i = 0; i < number_of_combined_segments - 1; i++)
			{
				byte[] current_segment = combined_segments.get(i);
				if(isCompressed[i])
					spliced_segments.add(current_segment); 
				else
				{
					// This shouldn't happen because we combined adjacent uncompressed segments.
					if(!isCompressed[i + 1])
						System.out.println("Following segment is uncompressed.");	
					
					int     current_bitlength      = StringMapper.getBitlength(current_segment); 
					byte [] next_segment           = combined_segments.get(i + 1);
					int     next_bitlength         = StringMapper.getBitlength(next_segment);
					byte [] decompressed_segment   = StringMapper.decompressStrings(next_segment);
					int	    decompressed_bitlength = StringMapper.getBitlength(decompressed_segment);
					
					int original_pair_bitlength = current_bitlength + decompressed_bitlength;
					
					byte init_byte    = 0;
					if(current_bitlength % 8 != 0)
					{
						if(current_bitlength < 8)
						{
							// There is only one partially filled byte.
					    	    // we will need to fill the init byte with bits from the next segment.
							//System.out.println("The length of the current uncompressed segment is less than a byte.");
							init_byte     = current_segment[current_segment.length - 2];
							
							// We'll do a left shift now to left justify the byte, and then a right 
							// shift later when the byte is prepended to the next segment.
							int extra_bits = 8 - current_bitlength;
							init_byte <<= extra_bits;
						}
						else
						{
							
							// We need to construct the init byte from two current segment bytes.
							// System.out.println("The length of the current uncompressed segment is uneven.");
							
							// System.out.println("Last two segment bytes:");
						
			    	    	        byte first_byte    = current_segment[current_segment.length - 3];
			    	    	        byte second_byte   = current_segment[current_segment.length - 2];
			    	    	        
			    	    	        int  odd_bits   = current_bitlength % 8;
			    	    	        int  extra_bits = 8 - odd_bits;
			    	          	
			    	            first_byte  >>= odd_bits;
							byte mask     = getTrailingMask(extra_bits);
							first_byte   &= mask;
							
							second_byte <<= extra_bits;
						
			    	            init_byte  = first_byte;
			    	            init_byte |= second_byte;
						}
					}
					else
					{
						// The current bitlength is even, we can just use the last segment byte.
			    	        init_byte = current_segment[current_segment.length - 2];	
					}
					
					// We need to clip the segment we're checking so metadata doesn't contaminate data.
					byte [] clipped_segment = new byte[decompressed_segment.length];
					for(int j = 0; j < decompressed_segment.length - 1; j++)
						clipped_segment[j + 1] = decompressed_segment[j];
					
					clipped_segment[0] = init_byte;
					// Since the init byte contains empty bits.
					if(current_bitlength < 8)
					    clipped_segment = shiftRight(clipped_segment, 8 - current_bitlength);
					
					int number_of_bits = 8;
					if(number_of_bits > current_bitlength)
						number_of_bits = current_bitlength;
					
					int max_bits      = 0;
					int max_reduction = -1;
				
					for(int j = 0; j < number_of_bits; j++)
					{
						int spliced_bits      = number_of_bits - j;
						int shifted_bitlength = decompressed_bitlength + spliced_bits;
						byte odd_bits         = (byte)(shifted_bitlength % 8);
						byte extra_bits       = (byte)(8 - odd_bits);
						extra_bits          <<= 5;
						
						byte [] augmented_segment  = new byte[clipped_segment.length + 1];
						if(spliced_bits < number_of_bits)
						{
							byte [] shifted_segment = shiftRight(clipped_segment, 8 - spliced_bits);
						    for(int k = 0; k < shifted_segment.length; k++)
						    	    augmented_segment[k]  = shifted_segment[k];
						}
						else
						{
							for(int k = 0; k < clipped_segment.length; k++)
					    	        augmented_segment[k]  = clipped_segment[k];	
						}
						
						double ratio = StringMapper.getZeroRatio(augmented_segment, shifted_bitlength);
						if(ratio < .5)
							augmented_segment[augmented_segment.length - 1] = 16;
						augmented_segment[augmented_segment.length - 1]    |= extra_bits;
						byte [] compressed_segment   = StringMapper.compressStrings3(augmented_segment);
						int     compressed_bitlength = StringMapper.getBitlength(compressed_segment);
					
						int bit_reduction = spliced_bits - (compressed_bitlength - next_bitlength);
						
						if(bit_reduction > max_reduction)
						{
							max_reduction = bit_reduction;
							max_bits      = spliced_bits;
						}
					}
					
					if(max_reduction > 0)  
					{
						total_spliced_bits += max_bits;
					    int reduced_bitlength  = current_bitlength - max_bits;
					    if(reduced_bitlength > 0)
					    {
					        int reduced_bytelength = reduced_bitlength / 8;
					        if(reduced_bitlength % 8 != 0)
					    	        reduced_bytelength++;
					        reduced_bytelength++;
					        
					        byte [] reduced_segment = new byte[reduced_bytelength];
					        for(int j = 0; j < reduced_segment.length - 1; j++)
					        	    reduced_segment[j] = current_segment[j];
					        int odd_bits = reduced_bitlength % 8;
					        byte extra_bits = 0;
					        if(odd_bits != 0)
					        {
					        	    extra_bits = (byte)(8 - odd_bits);
					        	    extra_bits <<= 5;
					        }
					        if(extra_bits != 0)
					        {
					        	    // Clear any bits that might have been set in the original segment.
					        	    byte mask = getTrailingMask(odd_bits);
					        	    reduced_segment[reduced_segment.length - 2] &= mask;
					        	    // Put extra bits in data byte, don't worry about bit type.
					        	    reduced_segment[reduced_segment.length - 1] = extra_bits;
					        	    
					        	    reduced_bitlength = StringMapper.getBitlength(reduced_segment);
					        }
					        spliced_segments.add(reduced_segment);
					    }
					    
					    
					    if(reduced_bitlength == 0)
					    	    System.out.println("Eliminated uncompressed segment.");
					    
					    // For now, the clipped length and clipped/shifted lengths are the same
					    // since we're limiting the number of spliced bits to 8.
					    byte [] augmented_segment  = new byte[clipped_segment.length + 1];
					    if(max_bits == 8)
					    {
					        for(int k = 0; k < clipped_segment.length; k++)
		    	                    augmented_segment[k] = clipped_segment[k];
					    }
					    else
					    {
					    	    byte [] shifted_segment = shiftRight(clipped_segment, 8 - max_bits);
					    	    for(int k = 0; k < clipped_segment.length; k++)
		    	                    augmented_segment[k] = shifted_segment[k];
					    }
					   
					    int augmented_bitlength = decompressed_bitlength + max_bits;
					    
					    int odd_bits   = augmented_bitlength % 8;
					    byte extra_bits = 0;
					    if(odd_bits != 0)
					        extra_bits = (byte)(8 - odd_bits);
					    extra_bits    <<= 5;
					    
					    double ratio   = StringMapper.getZeroRatio(augmented_segment, augmented_bitlength);
					    if(ratio < .5)
					        	augmented_segment[augmented_segment.length - 1] = 16;   
					    augmented_segment[augmented_segment.length - 1] |= extra_bits;
					  
					    
					    byte [] compressed_segment   = StringMapper.compressStrings(augmented_segment);
					    if((compressed_segment.length - 1) > max_segment_bytelength)
					    	    max_segment_bytelength  = compressed_segment.length - 1;  
				
					    spliced_segments.add(compressed_segment);
					    i++;
					}
					else
					{
						spliced_segments.add(current_segment);
					}
				}
			}
			
			// We have one segment left that hasn't been processed, since it doesn't have a following segment.
			byte [] last_segment = combined_segments.get(number_of_combined_segments - 1);
			spliced_segments.add(last_segment);
		
			int number_of_spliced_segments = spliced_segments.size();
			
			int total_spliced_bitlength = 0;
			int total_spliced_decompressed_bitlength = 0;
			for(i = 0; i < number_of_spliced_segments; i++)
			{
				byte[] segment = spliced_segments.get(i);
				int iterations = StringMapper.getIterations(segment);
				int current_bitlength = StringMapper.getBitlength(segment);
				total_spliced_bitlength += current_bitlength;

				if(iterations != 0 && iterations != 16)
				{
					byte [] decompressed_segment = StringMapper.decompressStrings(segment);
					int decompressed_bitlength = StringMapper.getBitlength(decompressed_segment);
					total_spliced_decompressed_bitlength += decompressed_bitlength;
				}
				else
					total_spliced_decompressed_bitlength += current_bitlength;	
			}
			
			System.out.println("Total combined bitlength was " + total_combined_bitlength);
			System.out.println("Total spliced bitlength was " + total_spliced_bitlength);
			System.out.println("Total spliced bits was " + total_spliced_bits	);
			System.out.println();
		    
			result.add(spliced_segments);
			result.add(max_segment_bytelength);
			result.add(min_segment_bytelength);
			return result;
		}
	}

	*/
	
	
	public static byte[] shiftRight(byte[] src, int bit_length)
	{
		int byte_length = bit_length / 8;
		byte[] dst = new byte[src.length - byte_length];

		int j = 0;
		for(int i = byte_length; i < src.length; i++)
			dst[j++] = src[i];

		int shift = bit_length % 8;
		if(shift != 0)
		{
			int reverse_shift = 8 - shift;
			byte mask         = getTrailingMask(reverse_shift);
			
			for(int i = 0; i < dst.length - 1; i++)
			{
				dst[i] >>= shift;
			    dst[i]  &= mask;
				byte extra_bits = (byte) (dst[i + 1] << reverse_shift);
				dst[i] |= extra_bits;
			}

			int i = dst.length - 1;
			dst[i] >>>= shift;
			dst[i]  &= mask;
		}

		return dst;
	}
	
	public static ArrayList shiftRight2(byte[] src, int bitlength)
	{
		ArrayList result = new ArrayList();
		
		int original_bitlength  = 8 * src.length;
		int remainder_bitlength = original_bitlength - bitlength;
		int fragment_bitlength  = original_bitlength - remainder_bitlength;
		
		//System.out.println("Original bit length is "  + original_bitlength);
		//System.out.println("Remainder bit length is " + remainder_bitlength);
		//System.out.println("Fragment bit length is "  + fragment_bitlength);
		//System.out.println();
		int remainder_bytelength = remainder_bitlength / 8;
		if(remainder_bitlength % 8 != 0)
			remainder_bytelength++;
		
		int fragment_bytelength = fragment_bitlength / 8;
		if(fragment_bitlength % 8 != 0)
			fragment_bytelength++;

		byte[] remainder = new byte[remainder_bytelength];

		int j = 0;
		int k = bitlength / 8;
		for(int i = k; i < src.length; i++)
			remainder[j++] = src[i];

		int shift = bitlength % 8;
		if(shift != 0)
		{
			int reverse_shift = 8 - shift;
			byte mask = getTrailingMask(reverse_shift);
			for(int i = 0; i < remainder.length - 1; i++)
			{
				remainder[i] >>= shift;
			    remainder[i]  &= mask;
				byte extra_bits = (byte) (remainder[i + 1]);	
			    extra_bits <<=  reverse_shift;	
			    remainder[i] |= extra_bits;
			}
			int i = remainder.length - 1;
			remainder[i] >>= shift;
			remainder[i]  &= mask;
		}

		result.add(remainder);
		result.add(remainder_bitlength);
		
		byte[] fragment = new byte[fragment_bytelength];
		for(int i = 0; i < fragment.length; i++)
			fragment[i] = src[i];
		if(fragment_bitlength % 8 != 0)
		{
			byte mask = getTrailingMask(fragment_bitlength % 8);
			fragment[fragment.length - 1] &= mask;
		}
		
		result.add(fragment);
		result.add(fragment_bitlength);
		
		return result;	
	}
	
	public static byte[] shiftLeft(byte[] src, int bit_length)
	{
		int byte_length = bit_length / 8;
		
		if(bit_length % 8 != 0)
		 	byte_length++;
		
		byte[] dst = new byte[src.length + byte_length];
		
		int bit_shift = bit_length % 8;
		if(bit_shift != 0)
		{
			int reverse_shift = 8 - bit_shift;
			byte mask = getTrailingMask(bit_shift);
			for(int i = 0; i < src.length; i++)
		    {
				byte current_bits        = (byte)(src[i] << bit_shift);
		    	    dst[i + byte_length - 1] |= current_bits;
		    	    
		    	    byte next_bits           = (byte)(src[i] >> reverse_shift);
		    	    next_bits               &= mask;
		    	    dst[i + byte_length] = next_bits;
		    }
		}
		else
		{
			for(int i = 0; i < src.length; i++)
				dst[i + byte_length] = src[i];
		}
		
		return dst;
	}
	
	public static byte[] shiftLeft2(byte[] src, int bit_length)
	{
		int byte_length = bit_length / 8;
		
		// We don't do this because we assume the odd 
		// most significant bits were emptied by a previous right shift.
		// Otherwise we would allocate another byte
		// to contain the most significant bits shifted out
		// of the last src byte.
		//
		// if(bit_length % 8 != 0)
		//  	   byte_length++;
		
		byte[] dst = new byte[src.length + byte_length];
	
		int bit_shift = bit_length % 8;
		if(bit_shift != 0)
		{
			int reverse_shift = 8 - bit_shift;
			if(src.length == 1)
				 dst[0] = (byte)(src[0] << bit_shift);	
			else
			{
				byte mask = getTrailingMask(bit_shift);
				for(int i = 0; i < src.length - 1; i++)
			    {
			    	    byte current_bits        = (byte)(src[i] << bit_shift);
			    	    byte next_bits           = (byte)(src[i] >> reverse_shift);
			    	    next_bits               &= mask;
			    	    dst[i + byte_length]    |= current_bits;
			    	    dst[i + 1 + byte_length] = next_bits;
			    }
				
				// Not worrying about most significant bits from final src byte,
				// as stated above.
				int i = src.length - 1;
				int j = dst.length - 1;
				byte current_bits = (byte)(src[i] << bit_shift);
				dst[j] |= current_bits;
			}
		}
		else
		{
			for(int i = 0; i < src.length; i++)
				dst[i + byte_length] = src[i];
		}

		return dst;
	}
	
	
	
	
	public static int getBinNumber(double ratio, double bin)
	{
		int number = 0;
		double total = bin;

		while (ratio > total)
		{
			number++;
			total += bin;
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
	 * @param ArrayList segments array list with byte arrays that include a trailing
	 *                  byte with segment data.
	 * @return ArrayList result array list that includes the packed bit string and
	 *         the bit lengths of the packed segments.
	 */
	public static ArrayList packSegments(ArrayList<byte[]> segments)
	{
		int size = segments.size();
		int[] bitlength = new int[size];

		int total_bitlength = 0;
		for(int i = 0; i < size; i++)
		{
			byte[] current_segment = (byte[]) segments.get(i);
			bitlength[i] = StringMapper.getBitlength(current_segment);
			total_bitlength += bitlength[i];
		}

		int total_bytelength = total_bitlength / 8;
		if(total_bitlength % 8 != 0)
			total_bytelength++;
		byte[] string = new byte[total_bytelength];

		byte[] mask = { 1, 3, 7, 15, 31, 63, 127 };

		int bit_offset = 0;

		for(int i = 0; i < size - 1; i++)
		{
			int m = bit_offset % 8;
			int n = bit_offset / 8;

			byte[] current_segment = (byte[]) segments.get(i);
			int length = StringMapper.getBitlength(current_segment);

			if(m == 0)
			{
				for(int j = 0; j < current_segment.length - 1; j++)
					string[n + j] = current_segment[j];
			} else
			{
				for(int j = 0; j < current_segment.length - 1; j++)
				{
					byte a = (byte) (current_segment[j] << m);
					string[n + j] |= a;

					int number_of_bits = m;
					byte b = (byte) (current_segment[j] >> 8 - m);
					b &= mask[number_of_bits - 1];
					string[n + j + 1] = b;
				}
			}

			bit_offset += length;
		}

		byte[] last_segment = (byte[]) segments.get(size - 1);
		int m = bit_offset % 8;
		int n = bit_offset / 8;

		if(m == 0)
		{
			for(int j = 0; j < last_segment.length - 1; j++)
				string[n + j] = last_segment[j];
		} else
		{
			for(int j = 0; j < last_segment.length - 2; j++)
			{
				byte a = (byte) (last_segment[j] << m);
				string[n + j] |= a;

				int number_of_bits = m;
				byte b = (byte) (last_segment[j] >> 8 - m);
				b &= mask[number_of_bits - 1];
				string[n + j + 1] = b;
			}
			byte a = (byte) (last_segment[last_segment.length - 2] << m);
			string[n + last_segment.length - 2] |= a;
			if(n + last_segment.length - 2 + 1 < string.length)
			{
				byte b = (byte) (last_segment[last_segment.length - 2] >> 8 - m);
				b &= mask[m - 1];
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
	 * @ param ArrayList a list of byte arrays that contain a bit string @ param
	 * byte [] bitlength lengths of bit strings contained in byte arrays
	 * 
	 * @return ArrayList result includes the packed bit strings in a single byte
	 *         array.
	 */
	public static ArrayList packSegments(ArrayList<byte[]> segments, int[] bitlength)
	{
		// Returning a list instead of the byte array directly provides
		// for a simple way of error checking by returning an empty list.

		ArrayList result = new ArrayList();
		int size = segments.size();

		if(size != bitlength.length)
		{
			System.out.println("Number of segments and number of bit lengths do not agree.");
			return result;
		}

		int total_bitlength = 0;
		for(int i = 0; i < size; i++)
			total_bitlength += bitlength[i];
		int total_bytelength = total_bitlength / 8;
		if(total_bitlength % 8 != 0)
			total_bytelength++;
		byte[] string = new byte[total_bytelength];

		byte[] mask = { 1, 3, 7, 15, 31, 63, 127 };

		int bit_offset = 0;

		for(int i = 0; i < size - 1; i++)
		{
			int m = bit_offset % 8;
			int n = bit_offset / 8;

			byte[] current_segment = (byte[]) segments.get(i);
			int length = StringMapper.getBitlength(current_segment);

			if(m == 0)
			{
				for(int j = 0; j < current_segment.length - 1; j++)
					string[n + j] = current_segment[j];
			} else
			{
				for(int j = 0; j < current_segment.length - 1; j++)
				{
					byte a = (byte) (current_segment[j] << m);
					string[n + j] |= a;

					int number_of_bits = m;
					byte b = (byte) (current_segment[j] >> 8 - m);
					b &= mask[number_of_bits - 1];
					string[n + j + 1] = b;
				}
			}

			bit_offset += length;
		}

		byte[] last_segment = (byte[]) segments.get(size - 1);
		int m = bit_offset % 8;
		int n = bit_offset / 8;

		if(m == 0)
		{
			for(int j = 0; j < last_segment.length - 1; j++)
				string[n + j] = last_segment[j];
		} else
		{
			for(int j = 0; j < last_segment.length - 2; j++)
			{
				byte a = (byte) (last_segment[j] << m);
				string[n + j] |= a;

				int number_of_bits = m;
				byte b = (byte) (last_segment[j] >> 8 - m);
				b &= mask[number_of_bits - 1];
				string[n + j + 1] = b;
			}
			byte a = (byte) (last_segment[last_segment.length - 2] << m);
			string[n + last_segment.length - 2] |= a;
			if(n + last_segment.length - 2 + 1 < string.length)
			{
				byte b = (byte) (last_segment[last_segment.length - 2] >> 8 - m);
				b &= mask[m - 1];
				string[n + last_segment.length - 2 + 1] = b;
			}

		}

		result.add(string);
		return result;
	}

	/**
	 * Packs a list of bit strings into a single bit string in a single byte array.
	 *
	 * @param ArrayList list of byte arrays that include a trailing byte with
	 *                  segment data.
	 * @return ArrayList result which includes the bit string in a single byte array
	 *         and the lengths of the packed segments.
	 */
	public static ArrayList packSegments2(ArrayList<byte[]> segments)
	{
		int size = segments.size();
		int[] bitlength = new int[size];

		int total_bitlength = 0;
		for(int i = 0; i < size; i++)
		{
			byte[] current_segment = (byte[]) segments.get(i);
			bitlength[i] = StringMapper.getBitlength(current_segment);
			total_bitlength += bitlength[i];
		}

		int total_bytelength = total_bitlength / 8;
		if(total_bitlength % 8 != 0)
			total_bytelength++;
		byte[] string = new byte[total_bytelength];

		int k = 0;
		for(int i = 0; i < size; i++)
		{
			byte[] current_segment = (byte[]) segments.get(i);
			bitlength[i] = StringMapper.getBitlength(current_segment);

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
	 * @param ArrayList segments list of byte arrays that include a trailing byte
	 *                  with segment data.
	 * @return ArrayList result which includes the bit string in a single byte array
	 *         and the bit lengths of the packed segments.
	 */
	public static ArrayList packSegments2(ArrayList<byte[]> segments, int[] bitlength)
	{
		int size = segments.size();

		int total_bitlength = 0;
		for(int i = 0; i < size; i++)
		{
			byte[] current_segment = (byte[]) segments.get(i);
			bitlength[i] = StringMapper.getBitlength(current_segment);
			total_bitlength += bitlength[i];
		}

		int total_bytelength = total_bitlength / 8;
		if(total_bitlength % 8 != 0)
			total_bytelength++;
		byte[] string = new byte[total_bytelength];

		int k = 0;
		for(int i = 0; i < size; i++)
		{
			byte[] current_segment = (byte[]) segments.get(i);
			bitlength[i] = StringMapper.getBitlength(current_segment);

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
	 * Unpacks a string of concatenated segments into separate segments including a
	 * data byte.
	 *
	 * @param int  [] bytelength
	 * @param byte [] array of data bytes to be appended to segments
	 * @return ArrayList segments a list of byte arrays containing bit strings with
	 *         a data byte appended.
	 */
	public static ArrayList<byte[]> unpackSegments(byte[] string, int bytelength[], byte data[])
	{
		int number_of_segments = data.length;
		ArrayList<byte[]> unpacked_segments = new ArrayList<byte[]>();
		byte[] mask = { 1, 3, 7, 15, 31, 63, 127 };

		int bit_offset = 0;
		for(int i = 0; i < number_of_segments - 1; i++)
		{
			byte extra_bits = (byte) (data[i] >> 5);
			extra_bits &= 7;
			int bitlength = bytelength[i] * 8 - extra_bits;
			byte[] segment = new byte[bytelength[i] + 1];

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
			} else
			{
				for(int j = 0; j < bytelength[i]; j++)
				{
					segment[j] = (byte) (string[n + j] >> m);
					int number_of_bits = 8 - m;
					segment[j] &= mask[number_of_bits - 1];

					if(j < bytelength[i] - 1)
					{
						byte high_bits = (byte) (string[n + j + 1]);
						segment[j] |= high_bits << 8 - m;
					} else if(j == bytelength[i] - 1)
					{
						byte high_bits = (byte) (string[n + j + 1]);
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
		byte extra_bits = (byte) (data[i] >> 5);
		extra_bits &= 7;
		int bitlength = bytelength[i] * 8 - extra_bits;
		byte[] segment = new byte[bytelength[i] + 1];

		int m = bit_offset % 8;
		int n = bit_offset / 8;
		if(m == 0)
		{
			for(int j = 0; j < bytelength[i]; j++)
				segment[j] = string[n + j];
		} else
		{
			for(int j = 0; j < bytelength[i]; j++)
			{
				segment[j] = (byte) (string[n + j] >> m);
				int number_of_bits = 8 - m;
				segment[j] &= mask[number_of_bits - 1];

				if(j < bytelength[i] - 1)
				{
					byte high_bits = (byte) (string[n + j + 1]);
					segment[j] |= high_bits << 8 - m;
				} else if(j == bytelength[i] - 1)
				{
					if(n + j + 1 < string.length)
					{
						byte high_bits = (byte) (string[n + j + 1]);
						segment[j] |= high_bits << 8 - m;
						int number_of_extra_bits = (j + 1) * 8 - bitlength;
						if(number_of_extra_bits > 0)
						{
							number_of_bits = 8 - number_of_extra_bits;
							segment[j] &= mask[number_of_bits - 1];
						}

						/*
						 * if(number_of_extra_bits > 0) segment[j] &= mask[8 - number_of_extra_bits -
						 * 1];
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

	public static ArrayList<byte[]> unpackSegments(byte[] string, int bitlength[])
	{
		int number_of_segments = bitlength.length;
		ArrayList<byte[]> unpacked_segments = new ArrayList<byte[]>();
		byte[] mask = { 1, 3, 7, 15, 31, 63, 127 };

		int bit_offset = 0;
		for(int i = 0; i < number_of_segments - 1; i++)
		{
			int bytelength = bitlength[i] / 8;
			if(bitlength[i] % 8 != 0)
				bytelength++;

			byte[] segment = new byte[bytelength];

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
			} else
			{
				for(int j = 0; j < bytelength; j++)
				{
					segment[j] = (byte) (string[n + j] >> m);
					int number_of_bits = 8 - m;
					segment[j] &= mask[number_of_bits - 1];

					if(j < bytelength - 1)
					{
						byte high_bits = (byte) (string[n + j + 1]);
						segment[j] |= high_bits << 8 - m;
					} else if(j == bytelength - 1)
					{
						byte high_bits = (byte) (string[n + j + 1]);
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

		byte[] segment = new byte[bytelength];

		int m = bit_offset % 8;
		int n = bit_offset / 8;
		if(m == 0)
		{
			for(int j = 0; j < bytelength; j++)
				segment[j] = string[n + j];
		} else
		{
			for(int j = 0; j < bytelength; j++)
			{
				segment[j] = (byte) (string[n + j] >> m);
				int number_of_bits = 8 - m;
				segment[j] &= mask[number_of_bits - 1];

				if(j < bytelength - 1)
				{
					byte high_bits = (byte) (string[n + j + 1]);
					segment[j] |= high_bits << 8 - m;
				} else if(j == bytelength - 1)
				{
					if(n + j + 1 < string.length)
					{
						byte high_bits = (byte) (string[n + j + 1]);
						segment[j] |= high_bits << 8 - m;
						int number_of_extra_bits = (j + 1) * 8 - bitlength[i];
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
	 * Unpacks a string of concatenated segments into separate segments including a
	 * data byte.
	 *
	 * @return ArrayList unpacked segments a list of bit strings with a data byte.
	 */
	public static ArrayList<byte[]> unpackSegments2(byte[] string, int bytelength[], byte data[])
	{
		int number_of_segments = data.length;
		ArrayList<byte[]> unpacked_segments = new ArrayList<byte[]>();

		int k = 0;
		for(int i = 0; i < number_of_segments; i++)
		{
			byte extra_bits = (byte) (data[i] >> 5);
			extra_bits &= 7;
			int bitlength = bytelength[i] * 8 - extra_bits;
			byte[] segment = new byte[bytelength[i] + 1];
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
	 * Unpacks a string of concatenated segments into separate segments including a
	 * data byte.
	 *
	 * @return ArrayList unpacked segments a list of bit strings with a data byte.
	 */
	public static ArrayList<byte[]> unpackSegments2(byte[] string, int bitlength[])
	{
		int number_of_segments = bitlength.length;
		ArrayList<byte[]> unpacked_segments = new ArrayList<byte[]>();

		int k = 0;
		for(int i = 0; i < number_of_segments; i++)
		{
			int bytelength = bitlength[i] / 8;
			if(bitlength[i] % 8 != 0)
				bytelength++;

			byte[] segment = new byte[bytelength];

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
	public static byte[] getPositiveMask()
	{
		byte[] mask = new byte[8];
		mask[0] = 1;
		for(int i = 1; i < 8; i++)
			mask[i] = (byte) (mask[i - 1] << 1);
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
		byte mask = (byte) i;

		return mask;
	}

	/**
	 * Produces a set of masks that can be anded to isolate a bit.
	 *
	 * @return int masks
	 */
	public static int[] getPositiveMask2()
	{
		int[] mask = new int[32];
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
	public static byte[] getNegativeMask()
	{
		byte[] mask = new byte[8];
		mask[0] = 1;
		for(int i = 1; i < 8; i++)
			mask[i] = (byte) (mask[i - 1] << 1);
		for(int i = 0; i < 8; i++)
			mask[i] = (byte) (~mask[i]);
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

		byte mask = (byte) i;
		mask = (byte) (~mask);

		return mask;
	}

	/**
	 * Produces a set of masks that can be anded to isolate leading segments.
	 *
	 * @return byte [] masks
	 */
	public static byte[] getLeadingMask()
	{
		byte[] mask = new byte[7];
		mask[0] = -2;
		for(int i = 1; i < 7; i++)
			mask[i] = (byte) (mask[i - 1] * 2);
		return mask;
	}

	/**
	 * Produces a single mask that can be anded to isolate the trailing 
	 * (most significant) bits of segment of a byte.
	 *
	 * @return byte mask
	 */
	public static byte getLeadingMask(int number_of_trailing_bits)
	{
		int i = 8 - number_of_trailing_bits;
		byte[] mask = new byte[7];
		mask[0] = -2;
		for(int j = 1; j < 7; j++)
			mask[j] = (byte) (mask[j - 1] * 2);
		return mask[i - 1];
	}
	
	/**
	 * Produces a single mask that can be anded to isolate the trailing 
	 * (most significant) bits of a byte.
	 *
	 * @return byte mask
	 */
	public static byte getLeadingMask2(int length)
	{
		byte[] mask = new byte[7];
		mask[0] = -2;
		for(int i = 1; i < 7; i++)
			mask[i] = (byte) (mask[i - 1] * 2);
		return mask[length - 1];
	}

	/**
	 * Produces a set of masks that can be anded to isolate leading segments.
	 *
	 * @return byte [] mask
	 */
	public static byte[] getTrailingMask()
	{
		byte[] mask = new byte[7];
		mask[0] = 1;
		for(int i = 1; i < 7; i++)
			mask[i] = (byte) (mask[i - 1] * 2 + 1);
		return mask;
	}

	/**
	 * Produces a single mask that can be anded to isolate the leading (least significant)
	 * bits of a byte.
	 *
	 * @return byte mask
	 */
	public static byte getTrailingMask(int number_of_leading_bits)
	{
		byte[] mask = new byte[7];
		mask[0] = 1;
		for(int i = 1; i < 7; i++)
			mask[i] = (byte) (mask[i - 1] * 2 + 1);
		return mask[number_of_leading_bits - 1];
	}

	public static int getBit(byte[] string, int position)
	{
		int byte_offset = position / 8;
		int bit_offset = position % 8;
		byte mask = getPositiveMask(bit_offset);

		if((string[byte_offset] & mask) == 0)
			return 0;
		else
			return 1;
	}

	public static void setBit(byte[] string, int position, int value)
	{
		int byte_offset = position / 8;
		int bit_offset = position % 8;
		if(value == 0)
		{
			byte mask = getNegativeMask(bit_offset);
			string[byte_offset] &= mask;
		} else
		{
			byte mask = getPositiveMask(bit_offset);
			string[byte_offset] |= mask;
		}
	}
}