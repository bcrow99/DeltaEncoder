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

			if((current_bin < bin_divider - 1 && next_bin < bin_divider - 1) || (current_bin > bin_divider + 1 && next_bin > bin_divider + 1)  || ((current_bin == bin_divider - 1 || current_bin == bin_divider) && (next_bin == bin_divider - 1 || next_bin == bin_divider)))
			// if((current_bin < bin_divider && next_bin < bin_divider && Math.abs(current_bin - next_bin) < difference) || (current_bin >= bin_divider && next_bin >= bin_divider && Math.abs(current_bin - next_bin) < difference))
			// if((current_bin < bin_divider && next_bin < bin_divider) || (current_bin >= bin_divider && next_bin >= bin_divider))
			// if(current_bin == next_bin)
			{
				while(((current_bin < bin_divider - 1 && next_bin < bin_divider - 1) || (current_bin > bin_divider + 1 && next_bin > bin_divider + 1)  || ((current_bin == bin_divider - 1 || current_bin == bin_divider) && (next_bin == bin_divider - 1 || next_bin == bin_divider))) && i + j < number_of_segments - 1)
				// while(((current_bin < bin_divider && next_bin < bin_divider && Math.abs(current_bin - next_bin) < difference) || (current_bin >= bin_divider && next_bin >= bin_divider && Math.abs(current_bin - next_bin) < difference)) && i + j < number_of_segments - 1 )
				// while (((current_bin < bin_divider && next_bin < bin_divider) || (current_bin >= bin_divider && next_bin >= bin_divider)) && i + j < number_of_segments - 1)
				// while((current_bin == next_bin) && i + j < number_of_segments - 1)
				{
					next_bin = bin_number[i + j + 1];
					if((current_bin < bin_divider - 1 && next_bin < bin_divider - 1) || (current_bin > bin_divider + 1 && next_bin > bin_divider + 1)  || ((current_bin == bin_divider - 1 || current_bin == bin_divider) && (next_bin == bin_divider - 1 || next_bin == bin_divider)))
					//if((current_bin < bin_divider && next_bin < bin_divider && Math.abs(current_bin - next_bin) < difference) || (current_bin >= bin_divider && next_bin >= bin_divider && Math.abs(current_bin - next_bin) < difference))
					//if((current_bin < bin_divider && next_bin < bin_divider) || (current_bin >= bin_divider && next_bin >= bin_divider))
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
							
							/*
							System.out.println("Combined segment ratio is " + String.format("%.2f", zero_ratio));
							System.out.print("Byte ratios:");
							for(int k = 0; k < combined_segment.length - 1; k++)
							{
								m = (int) combined_segment[k];
								if(m < 0)
									m += 256;
								
							    zero_ratio = bit_table[m];
								zero_ratio /= 8;
								System.out.print(" " + String.format("%.2f", zero_ratio));
							}
							System.out.println();
							System.out.println();
							*/
							
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
						/*
						System.out.println("Uncombined segment:");
						System.out.print("Byte ratios:");
						for(int k = 0; k < current_segment.length - 1; k++)
						{
							int m = (int) current_segment[k];
							if(m < 0)
								m += 256;
							
						    double zero_ratio = bit_table[m];
							zero_ratio /= 8;
							System.out.print(" " + String.format("%.2f", zero_ratio));
						}
						System.out.println();
						System.out.println();
						*/
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
			
			/*
			int number_of_combined_segments = combined_segments.size();
			boolean[] isCompressed = new boolean[number_of_combined_segments];
			int number_of_uncompressed_segments = 0;
			for(i = 0; i < number_of_combined_segments; i++)
			{
				byte[] segment = combined_segments.get(i);
				int iterations = StringMapper.getIterations(segment);

				if(iterations == 0 || iterations == 16)
					number_of_uncompressed_segments++;
				else
					isCompressed[i] = true;
			}

			
			
			
			System.out.println("Min segment byte length is " + min_segment_bytelength);
			System.out.println("Max segment byte length is " + max_segment_bytelength);
			System.out.println("Number of segments is " + combined_segments.size());
			
           
			int [] combined_iterations = new int[number_of_combined_segments]; 
			double [] combined_ratio   = new double[number_of_combined_segments]; 
			int []    combined_number = new int[number_of_combined_segments];

			for(i = 0; i < number_of_combined_segments ; i++) 
			{ 
				byte [] segment        = combined_segments.get(i); 
				combined_iterations[i] = StringMapper.getIterations(segment); 
				combined_ratio[i]      = StringMapper.getZeroRatio(segment, (segment.length - 1) * 8, bit_table);
			    combined_number[i]     = getBinNumber(combined_ratio[i], bin);
			
			    System.out.println("Segment " + i + "\titerations " + combined_iterations[i] + "\tbin " + combined_number[i] + "\tratio " + String.format("%.2f", combined_ratio[i])); 
			} 
			
			System.out.println();
			System.out.println("The original string had iterations " + StringMapper.getIterations(string));
            System.out.println("The number of regular segments was " + number_of_segments + " with minimum bit length " + minimum_bitlength);
			System.out.println("The number of merged segments was " + number_of_merged_segments);
			System.out.println("The number of compressed/combined segments was " + number_of_combined_segments); 
			System.out. println("The number of compressed/combined segments that were uncompressed was " + number_of_uncompressed_segments);
			*/
			
		}

		return result;
	}

	
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
			for(i = 0; i < number_of_combined_segments; i++)
			{
				byte[] segment = combined_segments.get(i);
				int iterations = StringMapper.getIterations(segment);

				if(iterations != 0 && iterations != 16)
					isCompressed[i] = true;	
			}

			ArrayList<byte[]> spliced_segments = new ArrayList<byte[]>();

			for(i = 0; i < number_of_combined_segments; i++)
			{
				byte[] current_segment = combined_segments.get(i);
				
				// Ignore special cases for now.
				if(i == 0 || i == number_of_combined_segments - 1 || i == number_of_combined_segments - 2)
                {
					spliced_segments.add(current_segment);   
                }
				else
				{
					if(isCompressed[i])
					    spliced_segments.add(current_segment);	
					else
					{
						// The current segment is uncompressed.
						// For now we just add the current segment.
						spliced_segments.add(current_segment);	
						
						int current_bitlength = StringMapper.getBitlength(current_segment);
					    
						// Collect some information about what happens when we try 
						// splicing bits from end bytes.
						
						// This shouldn't happen because we combined adjacent uncompressed segments.
						if(!isCompressed[i - 1])
							System.out.println("Previous segment is uncompressed.");
						if(!isCompressed[i + 1])
							System.out.println("Following segment is uncompressed.");
						
						// We get the previous segment from the swapped list instead of the combined list
						// because it could have been modified as a following segment.
						byte[] previous_segment     = spliced_segments.get(i - 1);
						byte[] decompressed_segment = StringMapper.decompressStrings(previous_segment);
						
						int previous_bitlength = StringMapper.getBitlength(previous_segment);
						int decompressed_bitlength = StringMapper.getBitlength(decompressed_segment);
						
						byte[] augmented_segment = new byte[decompressed_segment.length + 1];
						for(int j = 0; j < decompressed_segment.length - 1; j++)
							augmented_segment[j] = decompressed_segment[j];
						augmented_segment[decompressed_segment.length - 1] = current_segment[0];
						
						//System.out.println("Previous bit length is " + previous_bitlength);
						
						int max_reduction = Integer.MIN_VALUE;
						int max_bits      = -1;
						for(int j = 0; j < 8; j ++)
						{
							int spliced_bits = (j + 1) % 8;
							int odd_bits     = 0;
							if(spliced_bits == 0)
								spliced_bits = 8;
							else
							    odd_bits = 8 - spliced_bits;	 	
						    odd_bits    <<= 5;
							
							augmented_segment[decompressed_segment.length]  = (byte)odd_bits;
							augmented_segment[decompressed_segment.length] |= previous_segment[previous_segment.length - 1];
							
							byte [] compressed_segment   = StringMapper.compressStrings(augmented_segment);
							int     compressed_bitlength = StringMapper.getBitlength(compressed_segment);
							
							//System.out.println("Bit length after adding " + spliced_bits + " bits is " + compressed_bitlength);
							
							int bit_reduction = spliced_bits - (compressed_bitlength - previous_bitlength);
							//System.out.println("Bit reduction is " + bit_reduction);
							
							if(bit_reduction > max_reduction)
							{
								max_reduction = bit_reduction;
								max_bits      = spliced_bits;
							}
						}
						
						System.out.println("Max bit reduction is " + max_reduction + " after splicing " + max_bits);
						
						
						// Get the following segment from the combined list.
						byte[] next_segment  = combined_segments.get(i + 1);
						decompressed_segment = StringMapper.decompressStrings(next_segment);
						
						int next_bitlength = StringMapper.getBitlength(next_segment);
						decompressed_bitlength = StringMapper.getBitlength(decompressed_segment);
						
						augmented_segment = new byte[decompressed_segment.length + 1];
						for(int j = 0; j < decompressed_segment.length - 1; j++)
							augmented_segment[j + 1] = decompressed_segment[j];
						
						
						if(max_bits == 0 || max_reduction == 0)
						{
							// Simple case:  the current uncompressed segment did not swap any of
							// its bits out to the previous segment.  We just copy the last byte
							// from the the current segment to the beginning of the augmented segment.
						    augmented_segment[0] = current_segment[current_segment.length - 2];
						    
						    // We can then shift the whole segment right 0->8 to see what
						    // compresses the most.
						    
						    System.out.println();
						}
						else
						{
                             // We have to right justify the current segment and modify the data byte.
							// Clip the segment so contents from metadata doesn't contaminate 
							// the data.
							byte [] clipped_segment = new byte[current_segment.length - 1];
							for(int j = 0; j < clipped_segment.length; j++)
								clipped_segment[j] = current_segment[j];
							
							ArrayList right_shifted_list     = shiftRight2(clipped_segment, max_bits);
							byte []   right_shifted_segment  = (byte [])right_shifted_list.get(0);
							int       segment_bit_length     = (int)right_shifted_list.get(1);
							byte []   right_shifted_fragment = (byte [])right_shifted_list.get(2);
							int       fragment_bit_length    = (int)right_shifted_list.get(1);
							byte []   left_shifted_segment   = shiftLeft(right_shifted_segment, max_bits);
							for(int j = 0; j < right_shifted_fragment.length; j++)
						        left_shifted_segment[j] |= right_shifted_fragment[j];
							
							
							/*
							byte [] positive_mask = getPositiveMask();
							System.out.println("Clipped segment:");
							for(int j = clipped_segment.length - 1; j >= 0; j--)
							{
								byte current_byte = clipped_segment[j];
								for(int k = 7; k >= 0; k--)
								{
									if((current_byte & positive_mask[k]) == 0)
									    System.out.print("0 ");
									else
										System.out.print("1 ");
								}
								System.out.print(" ");
							}
							System.out.println();
							
							System.out.println("Left shifted segment after or operation with fragment:");
							for(int j = left_shifted_segment.length - 1; j >= 0; j--)
							{
								byte current_byte = left_shifted_segment[j];
								for(int k = 7; k >= 0; k--)
								{
									if((current_byte & positive_mask[k]) == 0)
									    System.out.print("0 ");
									else
										System.out.print("1 ");
								}
								System.out.print(" ");
							}
							System.out.println();
							System.out.println();
							*/
							
							
							/*
							boolean isSame = true;
							for(int j = 0; j < clipped_segment.length; j++)
								if(clipped_segment[j] != left_shifted_segment[j])
									isSame = false;
							if(isSame)
								System.out.println("Shifted segment is the same as original segment.");
							else
								System.out.println("Shifted segment is not the same as original segment.");
							System.out.println();
							*/
							
							// Changing the contents of the current uncompressed segment.
							// Might do this again if we swap bits out to the next compressed segment.
							// For now the byte length remains the same.
							
							// for(int j = 0; j < shifted_segment.length; j++)
							//    current_segment[j] = shifted_segment[j];	
							// int odd_bits = 8 - max_bits;
							// odd_bits   <<= 5;
							// current_segment[current_segment.length - 1] |= odd_bits;
							
							
							// We have to shift the augmented segment left and then logically or
							// the initial byte with the right shifted last byte of the current
							// segment.
						}
						
						
						
						
						
						
						
						
						
					
						
					
						
						/*
						 * 
						 * 
						int min_bitlength = previous_bitlength;
						int min_index     = -1;
						for(int j = 0; j < 8; j ++)
						{
							int odd_bits          = (j + 1) % 8;	
							int current_bitlength = previous_bitlength;
							if(odd_bits == 0)
								current_bitlength += 8;
							else
							{
								current_bitlength += j;
								odd_bits   <<= 5;
							}
							
							augmented_segment[decompressed_segment.length]  = (byte)odd_bits;
							augmented_segment[decompressed_segment.length] |= previous_segment[previous_segment.length - 1];
							
							byte [] compressed_segment   = StringMapper.compressStrings(augmented_segment);
							int     compressed_bitlength = StringMapper.getBitlength(compressed_segment);
							
							
							
							if(compressed_bitlength < min_bitlength)
							{
								min_index = j;
								min_bitlength = compressed_bitlength;
								
							}
							
							System.out.println("Bit length after adding " + (j + 1) + " bits is " + compressed_bitlength);
						}
					
						int number_of_bits = min_index + 1;
						System.out.println("Number of bits spliced is " + number_of_bits);
						System.out.println();
						*/
						
						
						
						
						
						
						
						
						/*
						byte[] previous_segment = swapped_segments.get(i - 1);
						byte[] previous_decompressed_segment = StringMapper.decompressStrings(previous_segment);
						int max_index = 0;
						for(int j = 1; j < current_segment.length - 1; j++)
						{
							byte[] augmented_segment = new byte[previous_decompressed_segment.length + j];
							for(int k = 0; k < previous_decompressed_segment.length - 1; k++)
								augmented_segment[k] = previous_decompressed_segment[k];

							int m = previous_decompressed_segment.length - 1;
							for(int k = 0; k < j; k++)
								augmented_segment[k + m] = current_segment[k];
							int augmented_bitlength = (augmented_segment.length - 1) * 8;

							double zero_ratio = StringMapper.getZeroRatio(augmented_segment, augmented_bitlength, bit_table);
							if(zero_ratio < .5)
								augmented_segment[augmented_segment.length - 1] |= 16;

							byte[] compressed_augmented_segment = StringMapper.compressStrings(augmented_segment);
							if(compressed_augmented_segment.length < augmented_segment.length)
								max_index = j;
							else
								break;
						}
						
						if(max_index != 0)
						{
							int j = max_index;
							byte[] augmented_segment = new byte[previous_decompressed_segment.length + j];

							for(int k = 0; k < previous_decompressed_segment.length - 1; k++)
								augmented_segment[k] = previous_decompressed_segment[k];

							int m = previous_decompressed_segment.length - 1;
							for(int k = 0; k < j; k++)
								augmented_segment[k + m] = current_segment[k];
							int augmented_bitlength = (augmented_segment.length - 1) * 8;
							double zero_ratio = StringMapper.getZeroRatio(augmented_segment, augmented_bitlength, bit_table);
							if(zero_ratio < .5)
								augmented_segment[augmented_segment.length - 1] |= 16;

							byte[] compressed_augmented_segment = StringMapper.compressStrings(augmented_segment);

							byte[] reduced_segment = new byte[current_segment.length - j];
							for(int k = 0; k < reduced_segment.length - 1; k++)
								reduced_segment[k] = current_segment[k + j];
							reduced_segment[reduced_segment.length - 1] = current_segment[current_segment.length - 1];

							if(reduced_segment.length + compressed_augmented_segment.length < previous_segment.length + current_segment.length)
							{
								swapped_segments.remove(i - 1);
								swapped_segments.add(compressed_augmented_segment);
								swapped_segments.add(reduced_segment);
								System.out.println("Swapped " + j + " bytes from end segment to previous segment.");
							} 
							else
							{
								//System.out.println("Did not swap bytes 11.");
								swapped_segments.add(current_segment);
							}
						} 
						else
						{
							// Max index is 0.
							//System.out.println("Did not swap bytes 12.");
							swapped_segments.add(current_segment);
						}
						*/	
					}
				}
			}
			
			
			result.add(spliced_segments);
			result.add(max_segment_bytelength);
			result.add(min_segment_bytelength);
			return result;
		}
	}

	
	
    /*
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
			boolean[] isCompressed = new boolean[number_of_combined_segments];
			int number_of_uncompressed_segments = 0;
			for(i = 0; i < number_of_combined_segments; i++)
			{
				byte[] segment = combined_segments.get(i);
				int iterations = StringMapper.getIterations(segment);

				if(iterations == 0 || iterations == 16)
					number_of_uncompressed_segments++;
				else
					isCompressed[i] = true;
			}

			ArrayList<byte[]> swapped_segments = new ArrayList<byte[]>();

			for(i = 0; i < number_of_combined_segments; i++)
			{
				byte[] current_segment = combined_segments.get(i);
				if(isCompressed[i])
				{
					swapped_segments.add(current_segment);
				} 
				else
				{
					if(i == 0 && number_of_combined_segments > 1)
					{
						System.out.println("Initial segment is uncompressed.");

						// This shouldn't happen the way we combined the segments.
						if(!isCompressed[i + 1])
							swapped_segments.add(current_segment);
						else
						{
							byte[] next_segment = combined_segments.get(i + 1);
							byte[] next_decompressed_segment = StringMapper.decompressStrings(next_segment);

							int max_index = 0;

							for(int j = 1; j < current_segment.length - 1; j++)
							{
								byte[] augmented_segment = new byte[next_decompressed_segment.length + j];

								// Start by adding end bytes from current segment.
								int m = current_segment.length - 1 - j;
								int k = 0;
								for(k = 0; k < j; k++)
									augmented_segment[k] = current_segment[k + m];

								// Then add entire length of next segment.
								for(k = 0; k < next_decompressed_segment.length - 1; k++)
									augmented_segment[k + j] = next_decompressed_segment[k];

								int augmented_bitlength = (augmented_segment.length - 1) * 8;

								// If there are only two segments.
								if(i + 1 == number_of_combined_segments - 1)
								{
									augmented_segment[augmented_segment.length - 1] = last_extra_bits;
									int n = last_extra_bits >> 5;
									n &= 7;
									augmented_bitlength -= n;
								}

								double zero_ratio = StringMapper.getZeroRatio(augmented_segment, augmented_bitlength, bit_table);
								if(zero_ratio < .5)
									augmented_segment[augmented_segment.length - 1] |= 16;

								byte[] compressed_augmented_segment = StringMapper.compressStrings(augmented_segment);
								if(compressed_augmented_segment.length < augmented_segment.length)
									max_index = j;
								else
									break;
							}

							if(max_index != 0)
							{
								int j = max_index;

								byte[] reduced_segment = new byte[current_segment.length - j];
								for(int k = 0; k < reduced_segment.length - 1; k++)
									reduced_segment[k] = current_segment[k];
								int reduced_bitlength = (reduced_segment.length - 1) * 8;
								double zero_ratio = StringMapper.getZeroRatio(reduced_segment, reduced_bitlength, bit_table);
								if(zero_ratio < .5)
									reduced_segment[reduced_segment.length - 1] |= 16;

								byte[] augmented_segment = new byte[next_decompressed_segment.length + j];
								int m = current_segment.length - 1 - j;
								int k = 0;
								for(k = 0; k < j; k++)
									augmented_segment[k] = current_segment[k + m];
								for(k = 0; k < next_decompressed_segment.length - 1; k++)
									augmented_segment[k + j] = next_decompressed_segment[k];
								int augmented_bitlength = (augmented_segment.length - 1) * 8;

								// If there are only two segments.
								if(i + 1 == number_of_combined_segments - 1)
								{
									augmented_segment[augmented_segment.length - 1] = last_extra_bits;
									int n = last_extra_bits >> 5;
									n &= 7;
									augmented_bitlength -= n;
								}

								zero_ratio = StringMapper.getZeroRatio(augmented_segment, augmented_bitlength, bit_table);
								if(zero_ratio < .5)
									augmented_segment[augmented_segment.length - 1] |= 16;

								byte[] compressed_augmented_segment = StringMapper.compressStrings(augmented_segment);

								if(reduced_segment.length + compressed_augmented_segment.length < current_segment.length + next_segment.length)
								{
									swapped_segments.add(reduced_segment);
									swapped_segments.add(augmented_segment);
									i++;
									System.out.println("Swapped bytes from initial segment to next segment.");
								} 
								else
								{
									swapped_segments.add(current_segment);
									//System.out.println("Did not swap bytes 2.");
								}
							} 
							else
							{
								swapped_segments.add(current_segment);
								//System.out.println("Did not swap bytes 3.");
							}
						}
					} 
					else if(i < number_of_combined_segments - 1)
					{
						// First check if we can swap bytes with the previous segment.
						if(isCompressed[i - 1])
						{
							byte[] previous_segment = swapped_segments.get(i - 1);
							byte[] previous_decompressed_segment = StringMapper.decompressStrings(previous_segment);
							int max_index = 0;
							for(int j = 1; j < current_segment.length - 1; j++)
							{
								byte[] augmented_segment = new byte[previous_decompressed_segment.length + j];
								for(int k = 0; k < previous_decompressed_segment.length - 1; k++)
									augmented_segment[k] = previous_decompressed_segment[k];

								int m = previous_decompressed_segment.length - 1;
								for(int k = 0; k < j; k++)
									augmented_segment[k + m] = current_segment[k];
								int augmented_bitlength = (augmented_segment.length - 1) * 8;

								double zero_ratio = StringMapper.getZeroRatio(augmented_segment, augmented_bitlength, bit_table);
								if(zero_ratio < .5)
									augmented_segment[augmented_segment.length - 1] |= 16;

								byte[] compressed_augmented_segment = StringMapper.compressStrings(augmented_segment);
								if(compressed_augmented_segment.length < augmented_segment.length)
									max_index = j;
								else
									break;
							}
							if(max_index != 0)
							{
								int j = max_index;
								byte[] augmented_segment = new byte[previous_decompressed_segment.length + j];

								for(int k = 0; k < previous_decompressed_segment.length - 1; k++)
									augmented_segment[k] = previous_decompressed_segment[k];

								int m = previous_decompressed_segment.length - 1;
								for(int k = 0; k < j; k++)
									augmented_segment[k + m] = current_segment[k];
								int augmented_bitlength = (augmented_segment.length - 1) * 8;
								double zero_ratio = StringMapper.getZeroRatio(augmented_segment, augmented_bitlength, bit_table);
								if(zero_ratio < .5)
									augmented_segment[augmented_segment.length - 1] |= 16;

								byte[] compressed_augmented_segment = StringMapper.compressStrings(augmented_segment);

								byte[] reduced_segment = new byte[current_segment.length - j];
								for(int k = 0; k < reduced_segment.length - 1; k++)
									reduced_segment[k] = current_segment[k + j];
								reduced_segment[reduced_segment.length - 1] = current_segment[current_segment.length - 1];

								if(reduced_segment.length + compressed_augmented_segment.length < previous_segment.length + current_segment.length)
								{
									swapped_segments.remove(i - 1);
									swapped_segments.add(compressed_augmented_segment);

									System.out.println("Swapped " + j + " bytes from interior segment to previous segment.");
									
									// Before we add the current segment we want to check if can swap end bytes with
									// the the next segment.
									current_segment = reduced_segment;
								} 
								else
								{
									//System.out.println("Did not swap bytes 5.");
								}
							} 
							else
							{
								//System.out.println("Did not swap bytes 6.");
							}
						}

						// Now check the next segment to see if we can swap bytes.
						if(isCompressed[i + 1])
						{
							byte[] next_segment = combined_segments.get(i + 1);
							byte[] next_decompressed_segment = StringMapper.decompressStrings(next_segment);

							int max_index = 0;

							for(int j = 1; j < current_segment.length - 1; j++)
							{
								byte[] augmented_segment = new byte[next_decompressed_segment.length + j];

								// Start by adding end bytes from current segment.
								int m = current_segment.length - 1 - j;
								int k = 0;
								for(k = 0; k < j; k++)
									augmented_segment[k] = current_segment[k + m];

								// Then add entire length of next segment.
								for(k = 0; k < next_decompressed_segment.length - 1; k++)
									augmented_segment[k + j] = next_decompressed_segment[k];

								int augmented_bitlength = (augmented_segment.length - 1) * 8;

								// If there are only two segments.
								if(i + 1 == number_of_combined_segments - 1)
								{
									augmented_segment[augmented_segment.length - 1] = last_extra_bits;
									int n = last_extra_bits >> 5;
									n &= 7;
									augmented_bitlength -= n;
								}

								double zero_ratio = StringMapper.getZeroRatio(augmented_segment, augmented_bitlength, bit_table);
								if(zero_ratio < .5)
									augmented_segment[augmented_segment.length - 1] |= 16;

								byte[] compressed_augmented_segment = StringMapper.compressStrings(augmented_segment);
								if(compressed_augmented_segment.length < augmented_segment.length)
									max_index = j;
								else
									break;
							}

							if(max_index != 0)
							{
								int j = max_index;

								byte[] reduced_segment = new byte[current_segment.length - j];
								for(int k = 0; k < reduced_segment.length - 1; k++)
									reduced_segment[k] = current_segment[k];
								int reduced_bitlength = (reduced_segment.length - 1) * 8;
								double zero_ratio = StringMapper.getZeroRatio(reduced_segment, reduced_bitlength, bit_table);
								if(zero_ratio < .5)
									reduced_segment[reduced_segment.length - 1] |= 16;

								byte[] augmented_segment = new byte[next_decompressed_segment.length + j];
								int m = current_segment.length - 1 - j;
								int k = 0;
								for(k = 0; k < j; k++)
									augmented_segment[k] = current_segment[k + m];
								for(k = 0; k < next_decompressed_segment.length - 1; k++)
									augmented_segment[k + j] = next_decompressed_segment[k];
								int augmented_bitlength = (augmented_segment.length - 1) * 8;

								// If there are only two segments.
								if(i + 1 == number_of_combined_segments - 1)
								{
									augmented_segment[augmented_segment.length - 1] = last_extra_bits;
									int n = last_extra_bits >> 5;
									n &= 7;
									augmented_bitlength -= n;
								}

								zero_ratio = StringMapper.getZeroRatio(augmented_segment, augmented_bitlength, bit_table);
								if(zero_ratio < .5)
									augmented_segment[augmented_segment.length - 1] |= 16;

								byte[] compressed_augmented_segment = StringMapper.compressStrings(augmented_segment);

								if(reduced_segment.length + compressed_augmented_segment.length < current_segment.length + next_segment.length)
								{
									swapped_segments.add(reduced_segment);
									swapped_segments.add(augmented_segment);
									i++;
									System.out.println("Swapped " + j + " bytes from interior segment to next segment.");
								} 
								else
								{
									swapped_segments.add(current_segment);
									//System.out.println("Did not swap bytes 8.");
								}
							} 
							else
							{
								swapped_segments.add(current_segment);
								//System.out.println("Did not swap bytes 9.");
							}
						} 
						else
							swapped_segments.add(current_segment);
					} 
					else if(i == number_of_combined_segments - 1 && number_of_combined_segments > 1)
					{
						if(!isCompressed[i - 1])
						{
							swapped_segments.add(current_segment);
						} 
						else
						{
							byte[] previous_segment = swapped_segments.get(i - 1);
							byte[] previous_decompressed_segment = StringMapper.decompressStrings(previous_segment);
							int max_index = 0;
							for(int j = 1; j < current_segment.length - 1; j++)
							{
								byte[] augmented_segment = new byte[previous_decompressed_segment.length + j];
								for(int k = 0; k < previous_decompressed_segment.length - 1; k++)
									augmented_segment[k] = previous_decompressed_segment[k];

								int m = previous_decompressed_segment.length - 1;
								for(int k = 0; k < j; k++)
									augmented_segment[k + m] = current_segment[k];
								int augmented_bitlength = (augmented_segment.length - 1) * 8;

								double zero_ratio = StringMapper.getZeroRatio(augmented_segment, augmented_bitlength, bit_table);
								if(zero_ratio < .5)
									augmented_segment[augmented_segment.length - 1] |= 16;

								byte[] compressed_augmented_segment = StringMapper.compressStrings(augmented_segment);
								if(compressed_augmented_segment.length < augmented_segment.length)
									max_index = j;
								else
									break;
							}
							
							if(max_index != 0)
							{
								int j = max_index;
								byte[] augmented_segment = new byte[previous_decompressed_segment.length + j];

								for(int k = 0; k < previous_decompressed_segment.length - 1; k++)
									augmented_segment[k] = previous_decompressed_segment[k];

								int m = previous_decompressed_segment.length - 1;
								for(int k = 0; k < j; k++)
									augmented_segment[k + m] = current_segment[k];
								int augmented_bitlength = (augmented_segment.length - 1) * 8;
								double zero_ratio = StringMapper.getZeroRatio(augmented_segment, augmented_bitlength, bit_table);
								if(zero_ratio < .5)
									augmented_segment[augmented_segment.length - 1] |= 16;

								byte[] compressed_augmented_segment = StringMapper.compressStrings(augmented_segment);

								byte[] reduced_segment = new byte[current_segment.length - j];
								for(int k = 0; k < reduced_segment.length - 1; k++)
									reduced_segment[k] = current_segment[k + j];
								reduced_segment[reduced_segment.length - 1] = current_segment[current_segment.length - 1];

								if(reduced_segment.length + compressed_augmented_segment.length < previous_segment.length + current_segment.length)
								{
									swapped_segments.remove(i - 1);
									swapped_segments.add(compressed_augmented_segment);
									swapped_segments.add(reduced_segment);
									System.out.println("Swapped " + j + " bytes from end segment to previous segment.");
								} 
								else
								{
									//System.out.println("Did not swap bytes 11.");
									swapped_segments.add(current_segment);
								}
							} 
							else
							{
								// Max index is 0.
								//System.out.println("Did not swap bytes 12.");
								swapped_segments.add(current_segment);
							}
						}
					}
				}
			}

			// result.add(combined_segments);
			// result.add(max_segment_bytelength);

			System.out.println("Combined segments size is " + combined_segments.size());
			System.out.println("Max segment bytelength is " + max_segment_bytelength);
			System.out.println("Swapped segments size is " + swapped_segments.size());

			max_segment_bytelength = 0;
			int number_of_swapped_segments = swapped_segments.size();
			for(i = 0; i < number_of_swapped_segments; i++)
			{
				byte[] current_segment = swapped_segments.get(i);
				if(current_segment.length - 1 > max_segment_bytelength)
					max_segment_bytelength = current_segment.length - 1;
			}
			System.out.println("Max segment bytelength is " + max_segment_bytelength);
			System.out.println();

			result.add(swapped_segments);
			result.add(max_segment_bytelength);

			if(number_of_combined_segments == number_of_swapped_segments)
			{
				for(i = 0; i < number_of_combined_segments; i++)
				{
					byte[] combined_segment = combined_segments.get(i);
					byte[] swapped_segment = swapped_segments.get(i);

					if(combined_segment.length != swapped_segment.length)
					{
						System.out.println("Combined segment " + i + " has length " + combined_segment.length);
						System.out.println("Swapped segment " + i + " has length " + swapped_segment.length);
						System.out.println();
					}
				}
			}
		}

		return result;
	}
    */
	
	
	/**
	 * Segments a byte string and then merges the segments back together, depending
	 * on how it affects the rate of compression.
	 *
	 * @param src               the input byte string
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

		int string_bitlength = StringMapper.getBitlength(string);
		int number_of_segments = string_bitlength / minimum_bitlength;
		int segment_bitlength = minimum_bitlength;
		int segment_bytelength = minimum_bitlength / 8;
		segment_bytelength++;

		int remainder = string_bitlength % minimum_bitlength;
		int last_segment_bitlength = minimum_bitlength + remainder;
		int last_segment_bytelength = last_segment_bitlength / 8;

		// The last segment is the only one that might have odd bits and need an extra
		// byte.
		if(remainder % 8 != 0)
			last_segment_bytelength++;

		// Add a trailing byte for metadata.
		last_segment_bytelength++;

		// To start off, the last segment byte length is always greater than or equal to
		// the
		// maximum byte length.
		int max_segment_bytelength = last_segment_bytelength;

		// Get an initial list of uncompressed segments of equal length, except possibly
		// the last segment.
		// Set iterations to 16 or 0, depending on the type. Extra bits will be 0,
		// except possibly
		// the last segment.
		ArrayList<byte[]> segments = new ArrayList<byte[]>();

		// Look up table for collecting bit data.
		int[] bit_table = StringMapper.getBitTable();

		for(int i = 0; i < number_of_segments; i++)
		{
			if(i < number_of_segments - 1)
			{
				byte[] segment = new byte[segment_bytelength];
				for(int j = 0; j < segment.length - 1; j++)
					segment[j] = string[i * (segment_bytelength - 1) + j];

				double zero_ratio = StringMapper.getZeroRatio(segment, segment_bitlength, bit_table);
				byte iterations = 0;
				if(zero_ratio < .5)
					iterations = 16;
				segment[segment.length - 1] = iterations;
				segments.add(segment);
			} else
			{
				byte[] segment = new byte[last_segment_bytelength];
				for(int j = 0; j < segment.length - 1; j++)
					segment[j] = string[i * (segment_bytelength - 1) + j];

				// Check if the last segment is even.
				byte extra_bits = 0;
				if(remainder != 0)
				{
					extra_bits = (byte) (8 - remainder);
					extra_bits <<= 5;
				}
				segment[segment.length - 1] = extra_bits;

				double zero_ratio = StringMapper.getZeroRatio(segment, last_segment_bitlength, bit_table);
				byte iterations = 0;
				if(zero_ratio < .5)
					iterations = 16;
				segment[segment.length - 1] |= iterations;
				segments.add(segment);
			}
		}

		ArrayList<byte[]> compressed_segments = new ArrayList<byte[]>();
		for(int i = 0; i < number_of_segments; i++)
		{
			byte[] segment = segments.get(i);
			byte[] compressed_segment = StringMapper.compressStrings(segment);
			compressed_segments.add(compressed_segment);
		}

		// The int for the segment byte length and the trailing byte with segment
		// information
		// add up to 40 bits when the segment byte length requires an int.
		// As the number of segments increase and the segment lengths decrease, the int
		// can
		// be replaced by a short or byte, so the overhead for smaller segments could
		// get as
		// low as 16.
		int overhead = 40;
		if(max_segment_bytelength <= Byte.MAX_VALUE * 2 + 1)
			overhead = 16;
		else if(max_segment_bytelength <= Short.MAX_VALUE * 2 + 1)
			overhead = 24;

		int current_number_of_segments = number_of_segments;
		int previous_number_of_segments = 0;
		ArrayList merged_segments = new ArrayList();

		while (current_number_of_segments != previous_number_of_segments)
		{
			previous_number_of_segments = current_number_of_segments;
			merged_segments.clear();

			for(int i = 0; i < current_number_of_segments; i++)
			{
				byte[] current_segment = compressed_segments.get(i);
				if(i < current_number_of_segments - 1)
				{
					byte[] next_segment = compressed_segments.get(i + 1);
					int current_iterations = StringMapper.getIterations(current_segment);
					int next_iterations = StringMapper.getIterations(next_segment);

					if(current_iterations == next_iterations || (current_iterations == 0 && next_iterations == 16) || (current_iterations == 16 && next_iterations == 0))
					{
						int current_bitlength = StringMapper.getBitlength(current_segment);
						int next_bitlength = StringMapper.getBitlength(next_segment);
						int combined_bitlength = current_bitlength + next_bitlength;

						if(current_iterations == 0 || current_iterations == 16)
						{
							int bytelength = current_segment.length + next_segment.length - 1;
							byte[] merged_segment = new byte[bytelength];
							for(int k = 0; k < current_segment.length - 1; k++)
								merged_segment[k] = current_segment[k];
							int offset = current_segment.length - 1;
							for(int k = 0; k < next_segment.length - 1; k++)
								merged_segment[k + offset] = next_segment[k];
							int modulus = combined_bitlength % 8;
							if(modulus != 0)
							{
								byte extra_bits = (byte) (8 - modulus);
								extra_bits <<= 5;
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
						} else
						{
							byte[] decompressed_current_segment = StringMapper.decompressStrings(current_segment);
							byte[] decompressed_next_segment = StringMapper.decompressStrings(next_segment);

							int decompressed_current_bitlength = StringMapper.getBitlength(decompressed_current_segment);
							int decompressed_next_bitlength = StringMapper.getBitlength(decompressed_next_segment);
							int decompressed_combined_bitlength = decompressed_current_bitlength + decompressed_next_bitlength;

							int bytelength = decompressed_current_segment.length + decompressed_next_segment.length - 1;
							byte[] uncompressed_merged_segment = new byte[bytelength];
							for(int k = 0; k < decompressed_current_segment.length - 1; k++)
								uncompressed_merged_segment[k] = decompressed_current_segment[k];
							int offset = decompressed_current_segment.length - 1;
							for(int k = 0; k < decompressed_next_segment.length - 1; k++)
								uncompressed_merged_segment[k + offset] = decompressed_next_segment[k];

							int modulus = decompressed_combined_bitlength % 8;
							if(modulus != 0)
							{
								byte extra_bits = (byte) (8 - modulus);
								extra_bits <<= 5;
								uncompressed_merged_segment[uncompressed_merged_segment.length - 1] = extra_bits;
							}

							if(current_iterations > 15)
								uncompressed_merged_segment[uncompressed_merged_segment.length - 1] |= 16;

							byte[] merged_segment = StringMapper.compressStrings(uncompressed_merged_segment);
							int merged_bitlength = StringMapper.getBitlength(merged_segment);
							if(merged_bitlength - overhead < decompressed_combined_bitlength)
							{
								merged_segments.add(merged_segment);
								i++;
							} else
								merged_segments.add(current_segment);
						}
					} else
						merged_segments.add(current_segment);
				} else if(i == current_number_of_segments - 1)
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
					byte[] segment = (byte[]) merged_segments.get(i);
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

		// When we divide up the original string, we don't account for transform
		// iteration of the entire string if all the segments merge into one
		// so we need to copy the last byte of the string to the single
		// merged segment.
		if(compressed_segments.size() == 1)
		{
			byte[] segment = compressed_segments.get(0);
			if(segment.length != string.length)
				System.out.println("Single segment not same length as original string.");
			else
			{

				segment[segment.length - 1] = string[string.length - 1];
			}
		}
		result.add(compressed_segments);
		result.add(max_segment_bytelength);
		return result;
	}
	
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
		
		System.out.println("Original bit length is "  + original_bitlength);
		System.out.println("Remainder bit length is " + remainder_bitlength);
		System.out.println("Fragment bit length is "  + fragment_bitlength);
		System.out.println();
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
		
		// We don't do this because we assume the odd 
		// most significant bits were emptied by a previous right shift.
		// Otherwise we would allocate another byte
		// to contain the most significant bits shifted out.
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
	
	public static ArrayList shiftLeft2(byte[] src, int bit_length)
	{
		int byte_length = bit_length / 8;
		
		if(bit_length % 8 != 0)
		 	byte_length++;
		
		
		byte[] dst = new byte[src.length + byte_length];
	
		int bit_shift = bit_length % 8;
		if(bit_shift != 0)
		{
		    byte mask = getTrailingMask(bit_shift); 
		    for(int i = 0; i < src.length - 1; i++)
		    {
		    	    byte current_bits        = (byte)(src[i] << bit_shift);
		    	    byte next_bits           = (byte)(mask & src[i]);
		    	    next_bits              >>= 8 - bit_shift;
		    	    dst[i + byte_length]    |= current_bits;
		    	    dst[i + 1 + byte_length] = next_bits;
		    }
		    byte next_bits = (byte)(mask & src[src.length - 1]);
		    next_bits    >>= 8 - bit_shift;
		    dst[dst.length - 1] = next_bits;
		}
		else
		{
			for(int i = 0; i < src.length; i++)
				dst[i + byte_length] = src[i];
		}
		
		ArrayList result = new ArrayList();
		result.add(dst);
		result.add(bit_length);

		return result;
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