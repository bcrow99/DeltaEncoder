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
	public static ArrayList segment(byte[] string, int bitlength, double bin)
	{
	    ArrayList result = new ArrayList();
	    if(bitlength % 8 != 0)
		{
			System.out.println("Segment bitlength must be a multiple of 8.");
			return result;
		}
	    
	    int string_bitlength   = StringMapper.getBitlength(string);
		int number_of_segments = string_bitlength / bitlength;
		int segment_bitlength  = bitlength;
		int segment_bytelength = bitlength / 8;
		segment_bytelength++;
		
		int odd_bits                = string_bitlength % bitlength;
		int last_segment_bitlength  = bitlength + odd_bits;
		int last_segment_bytelength = last_segment_bitlength / 8;
		byte extra_bits        = 0;
		if(odd_bits % 8 != 0)
		{
			extra_bits   = (byte) (8 - odd_bits);
			extra_bits <<= 5;
			last_segment_bytelength++;
		}
		last_segment_bytelength++;
		
		int  min_segment_bytelength = segment_bytelength;
		int  max_segment_bytelength = last_segment_bytelength;
		byte string_data            = string[string.length - 1]; 
		
		int[] bit_table  = StringMapper.getBitTable();
		int[] bin_number = new int[number_of_segments];
		
		ArrayList<byte[]> segments = new ArrayList<byte[]>();
		for(int i = 0; i < number_of_segments; i++)
		{
			if(i < number_of_segments - 1)
			{
				byte[] segment = new byte[segment_bytelength];
				for(int j = 0; j < segment.length - 1; j++)
					segment[j] = string[i * (segment_bytelength - 1) + j];
				double zero_ratio = StringMapper.getZeroRatio(segment, segment_bitlength, bit_table);
				bin_number[i]     = getBinNumber(zero_ratio, bin);
				if(zero_ratio < .5)
					segment[segment.length - 1] = 16;
				segments.add(segment);
			} 
			else
			{
				byte[] segment = new byte[last_segment_bytelength];
				for(int j = 0; j < segment.length - 1; j++)
					segment[j] = string[i * (segment_bytelength - 1) + j];
				segment[segment.length - 1] = extra_bits;
				double zero_ratio = StringMapper.getZeroRatio(segment, last_segment_bitlength, bit_table);
				bin_number[i]     = getBinNumber(zero_ratio, bin);
				if(zero_ratio < .5)
					segment[segment.length - 1] |= 16;
				segments.add(segment);
			}
		}
	    
		result.add(segments);
		result.add(min_segment_bytelength);
		result.add(max_segment_bytelength);
		result.add(extra_bits);
		result.add(string_data);
		result.add(bin_number);
	    return result;
	}
	
	public static ArrayList merge(ArrayList<byte[]> segments, int [] bin_number, double bin, int min_segment_bytelength, int max_segment_bytelength, byte extra_bits, byte string_data, int merge_type)
	{
		ArrayList<byte[]> merged_segments    = new ArrayList<byte[]>();
        int               number_of_segments = segments.size();
        int               number_of_bins     = (int)(1. / bin);
        int               bin_divider        = number_of_bins / 2;
        int               difference         = bin_divider / 2;
        
        //System.out.println("Number of bins is " + number_of_bins);
        //System.out.println("Bin divider is " + bin_divider);
        
        int [] bit_table = StringMapper.getBitTable();
        
        int i = 0;
		for(i = 0; i < number_of_segments - 1; i++)
		{
			int current_bin = bin_number[i];
			int j           = 1;
			int next_bin    = bin_number[i + j];
            
			boolean similar = false;

			// From least to most exclusive.
			if(merge_type == 0)
			{
				if((current_bin < bin_divider && next_bin < bin_divider) || (current_bin >= bin_divider && next_bin >= bin_divider))
					similar = true;
			}
			else if(merge_type == 1)
			{
				if((current_bin < bin_divider - 1 && next_bin < bin_divider - 1) || (current_bin > bin_divider + 1 && next_bin > bin_divider + 1))
					similar = true;   
			}
			else if(merge_type == 2)
			{
				if((current_bin < bin_divider && next_bin < bin_divider && Math.abs(current_bin - next_bin) < difference) || (current_bin >= bin_divider && next_bin >= bin_divider && Math.abs(current_bin - next_bin) < difference))
					similar = true;   
			}
			else if(merge_type == 3)
			{
				if(current_bin == next_bin)
					similar = true;	
			}
			
			if(similar)
			{
				while(similar && i + j < number_of_segments - 1)
				{
					next_bin = bin_number[i + j + 1];
					if(merge_type == 0)
					{
						if((current_bin < bin_divider && next_bin < bin_divider) || (current_bin >= bin_divider && next_bin >= bin_divider))
							similar = true;
						else
							similar = false;
					}
					else if(merge_type == 1)
					{
						if((current_bin < bin_divider - 1 && next_bin < bin_divider - 1) || (current_bin > bin_divider + 1 && next_bin > bin_divider + 1))
							similar = true;  
						else
							similar = false;
					}
					else if(merge_type == 2)
					{
						if((current_bin < bin_divider && next_bin < bin_divider && Math.abs(current_bin - next_bin) < difference) || (current_bin >= bin_divider && next_bin >= bin_divider && Math.abs(current_bin - next_bin) < difference))
							similar = true; 
						else
							similar = false;
					}
					else if(merge_type == 3)
					{
						if(current_bin == next_bin)
							similar = true;	
						else
							similar = false;
					}
					if(similar)
						j++;
				}

				int merged_bytelength = j * (min_segment_bytelength - 1);
				if(i + j == number_of_segments - 1)
					merged_bytelength += max_segment_bytelength - 1;
				else
					merged_bytelength += min_segment_bytelength - 1;
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
					merged_segment[merged_bytelength - 1] = extra_bits;
				
				// This almost works, but fails sometimes when the bin divider is odd.
				/*
				if(current_bin < bin_divider)
				    merged_segment[merged_bytelength - 1] |= 16;
				*/
				
				// Do it the hard way.
				int bitlength = StringMapper.getBitlength(merged_segment);
				double ratio  = StringMapper.getZeroRatio(merged_segment, bitlength, bit_table);
				if(ratio < .5)
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

		max_segment_bytelength                       = 0;
		min_segment_bytelength                       = Integer.MAX_VALUE;
		int number_of_uncompressed_segments          = 0;
		int number_of_uncompressed_adjacent_segments = 0;
		int previous_iterations                      = 1;
		
		for(i = 0; i < number_of_merged_segments; i++)
		{
			byte[] segment = merged_segments.get(i);
			int type = StringMapper.getType(segment);
			int bitlength = StringMapper.getBitlength(segment);
			double ratio = StringMapper.getZeroRatio(segment, bitlength, bit_table);
			
			
			byte[] compressed_segment = StringMapper.compressStrings(segment);
			compressed_segments.add(compressed_segment);
			if(compressed_segment.length - 1 > max_segment_bytelength)
				max_segment_bytelength = compressed_segment.length - 1;
			if(compressed_segment.length - 1 < min_segment_bytelength)
				min_segment_bytelength = compressed_segment.length - 1;
			int current_iterations = StringMapper.getIterations(compressed_segment);
			if(current_iterations == 0 || current_iterations == 16)
				number_of_uncompressed_segments++;
			if((previous_iterations == 0 || previous_iterations == 16) && (current_iterations == 0 || current_iterations == 16))
				number_of_uncompressed_adjacent_segments++;
			previous_iterations = current_iterations;
			
		}

		
		ArrayList result = new ArrayList();
		int number_of_compressed_segments = compressed_segments.size();
		if(number_of_compressed_segments == 1)
		{
			byte [] segment             = compressed_segments.get(0);
			segment[segment.length - 1] = string_data;
			compressed_segments.set(0, segment);
			result.add(compressed_segments);
			return result;
		}
		else
		{
			result.add(compressed_segments);	
			result.add(min_segment_bytelength);
			result.add(max_segment_bytelength);
			result.add(extra_bits);
			result.add(string_data);
			result.add(number_of_uncompressed_segments);
			result.add(number_of_uncompressed_adjacent_segments);
		    return result;
		}
	}
	
	public static ArrayList combine(ArrayList<byte[]> segments, int min_segment_bytelength, int max_segment_bytelength, byte extra_bits, byte string_data)
	{
		ArrayList result = new ArrayList();
		ArrayList<byte[]> combined_segments    = new ArrayList<byte[]>();
		
		int[] bit_table  = StringMapper.getBitTable();
		
		int number_of_segments = segments.size();
		int i = 0;
		for(i = 0; i < number_of_segments - 1; i++)
		{
			byte[] current_segment = segments.get(i);
			int current_iterations = StringMapper.getIterations(current_segment);

			byte[] next_segment = segments.get(i + 1);
			int next_iterations = StringMapper.getIterations(next_segment);

			if((current_iterations == 0 || current_iterations == 16) && (next_iterations == 0 || next_iterations == 16))
			{
				int j = 1;
				while((next_iterations == 0 || next_iterations == 16) && i + j + 1 < number_of_segments)
				{
					next_segment = segments.get(i + j + 1);
					next_iterations = StringMapper.getIterations(next_segment);
					if(next_iterations == 0 || next_iterations == 16)
						j++;
				}

				int combined_length = 0;
				for(int k = 0; k < j + 1; k++)
				{
					byte[] segment = segments.get(i + k);
					combined_length += segment.length - 1;
				}
				combined_length++;

				if(max_segment_bytelength < combined_length - 1)
					max_segment_bytelength = combined_length - 1;

				byte[] combined_segment = new byte[combined_length];

				int m = 0;
				for(int k = 0; k < j + 1; k++)
				{
					byte[] segment = segments.get(i + k);
					for(int n = 0; n < segment.length - 1; n++)
						combined_segment[m + n] = segment[n];
					m += segment.length - 1;
				}

				if(i + j == number_of_segments - 1)
				{
					int last_bitlength = (combined_segment.length - 1) * 8;
					int k = extra_bits >> 5;
					k &= 7;
					last_bitlength -= k;
					double zero_ratio = StringMapper.getZeroRatio(combined_segment, last_bitlength, bit_table);
					combined_segment[combined_segment.length - 1] = extra_bits;
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
		if(i == number_of_segments - 1)
		{
			byte[] segment = segments.get(i);
			combined_segments.add(segment);
		}

		int number_of_uncompressed_segments = 0;
		
		int number_of_combined_segments = combined_segments.size();
		if(number_of_combined_segments == 1)
		{
			byte[] segment = combined_segments.get(0);
			segment[segment.length - 1] = string_data;
			combined_segments.set(0, segment);
			result.add(combined_segments);
			return result;
		}
		else
		{
		    int[] combined_iterations = new int[number_of_combined_segments];
		    for(i = 0; i < number_of_combined_segments; i++)
		    {
			    byte[] segment = combined_segments.get(i);
			    combined_iterations[i] = StringMapper.getIterations(segment);
			    if(combined_iterations[i] == 0 || combined_iterations[i] == 16)
				    number_of_uncompressed_segments++;
		    }
		    result.add(combined_segments);
			result.add(min_segment_bytelength);
			result.add(max_segment_bytelength);
			result.add(extra_bits);
			result.add(string_data);
			result.add(number_of_uncompressed_segments);
			return result; 
		}
	}
	
	public static ArrayList splice(ArrayList<byte[]> segments, int min_segment_bytelength, int max_segment_bytelength)
	{
		int number_of_segments = segments.size();
		boolean [] isCompressed = new boolean[number_of_segments];
		for(int i = 0; i < number_of_segments; i++)
		{
			byte[] segment = segments.get(i);
			int iterations = StringMapper.getIterations(segment);
			if(iterations != 0 && iterations != 16)
				isCompressed[i] = true;	
		}
		
		int overhead = 16;
		if(max_segment_bytelength > Short.MAX_VALUE * 2 + 1)
			overhead = 40;
		else if(max_segment_bytelength > Byte.MAX_VALUE * 2 + 1)
			overhead = 24;
		
		int   total_spliced_bits = 0;
		int   max_spliced_bits   = 0;
		int[] bit_table          = StringMapper.getBitTable();
		
		ArrayList<byte[]> spliced_segments = new ArrayList<byte[]>();
	
		int i = 0;
		for(i = 0; i < number_of_segments - 1; i++)
		{
			byte[] current_segment = segments.get(i);
			// Current segment is not uncompressed.
			if(isCompressed[i])
				spliced_segments.add(current_segment); 
			else
			{
				// No following compressed segment to add bits to.
				if(!isCompressed[i + 1])
					spliced_segments.add(current_segment);
				else
				{
					// Check if adding bits to next segment results in a bit reduction.
					int     current_bitlength      = StringMapper.getBitlength(current_segment); 
					byte [] next_segment           = segments.get(i + 1);
					int     next_bitlength         = StringMapper.getBitlength(next_segment);
					byte [] decompressed_segment   = StringMapper.decompressStrings(next_segment);
					int	    decompressed_bitlength = StringMapper.getBitlength(decompressed_segment);
					
					int augmented_bitlength        = current_bitlength + decompressed_bitlength;
					int    augmented_bytelength    = augmented_bitlength / 8;
					if(augmented_bitlength % 8 != 0)
						augmented_bytelength++;
					augmented_bytelength++;
					
					byte[] augmented_segment = new byte[augmented_bytelength];
					for(int j = 0; j < current_segment.length - 1; j++)
						augmented_segment[j] = current_segment[j];
					
					// We need this information to splice the segments together.
					int current_odd_bits = current_bitlength % 8;
					if(current_odd_bits == 0)
					{
						int k = 0;
						for(int j = current_segment.length - 1; j < augmented_segment.length - 1; j++)
							augmented_segment[j] = decompressed_segment[k++];
					}
					else
					{
						int extra_bits   = 8 - current_odd_bits;
						byte splice_byte = decompressed_segment[0];
						byte mask        = getTrailingMask(extra_bits);
						splice_byte     &= mask;
						splice_byte    <<= current_odd_bits;
						
						augmented_segment[current_segment.length - 2] |= splice_byte;
						
						byte [] shifted_segment = shiftRight(decompressed_segment, extra_bits);
						int k = 0;
						for(int j = current_segment.length - 1; j < augmented_segment.length - 1; j++)
						    augmented_segment[j] = shifted_segment[k++];
					}
				
					// Set the bit type and iterations.
					augmented_segment[augmented_segment.length - 1] = 0;
					double ratio = StringMapper.getZeroRatio(augmented_segment, augmented_bitlength, bit_table);
					if(ratio < .5)
						augmented_segment[augmented_segment.length - 1] = 16;
					int augmented_odd_bits = augmented_bitlength % 8;
					if(augmented_odd_bits != 0)
					{
						// Set the bit length if it's odd.
						byte extra_bits = (byte)(8 - augmented_odd_bits);
						extra_bits <<= 5;
						augmented_segment[augmented_segment.length - 1] |= extra_bits;
						
						// Clean up any bits from the metadata.
						byte mask = getTrailingMask(augmented_odd_bits);
						augmented_segment[augmented_segment.length - 2] &= mask;
					}
				
					byte [] compressed_segment   = StringMapper.compressStrings(augmented_segment);
					int     compressed_bitlength = StringMapper.getBitlength(compressed_segment);
					int     spliced_bits         = current_bitlength;
					//int     bit_reduction        = spliced_bits - (compressed_bitlength - next_bitlength);
					int     bit_reduction        = (spliced_bits + overhead) - (compressed_bitlength - next_bitlength);
					int     max_reduction        = bit_reduction;
				
					for(int j = 1; j < current_bitlength; j ++)
					{
						byte [] shifted_segment                     = shiftRight(augmented_segment, j);
						int     shifted_bitlength                   = augmented_bitlength - j;
						int     shifted_bytelength = shifted_bitlength / 8;
						if(shifted_bitlength % 8 != 0)
							shifted_bytelength++;
						shifted_bytelength++;
						
						// When j % 8 + shifted odd bits == 0 a byte full of empty bits is produced.
						// We'll do a simple check on the length for any case where the segment got padded.
						if(shifted_segment.length != shifted_bytelength)
						{
							byte [] clipped_segment = new byte[shifted_bytelength];
							for(int k = 0; k < shifted_bytelength; k++)
							    clipped_segment[k] = shifted_segment[k];
							shifted_segment = clipped_segment;
						}
						
						shifted_segment[shifted_segment.length - 1] = 0;
						ratio                                       = StringMapper.getZeroRatio(shifted_segment, shifted_bitlength, bit_table);
						if(ratio < .5)
							shifted_segment[shifted_segment.length - 1] = 16;
						
						int shifted_odd_bits            = shifted_bitlength % 8;
						if(shifted_odd_bits != 0)
						{
							byte extra_bits = (byte)(8 - shifted_odd_bits);
							extra_bits <<= 5;
							shifted_segment[shifted_segment.length - 1] |= extra_bits;
							byte mask = getTrailingMask(shifted_odd_bits);
							shifted_segment[shifted_segment.length - 2] &= mask;
						}
					
						compressed_segment   = StringMapper.compressStrings(shifted_segment);
						compressed_bitlength = StringMapper.getBitlength(compressed_segment);
						
						int current_bits  = current_bitlength - j;
						bit_reduction     = current_bits - (compressed_bitlength - next_bitlength);
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
						
						if(spliced_bits == current_bitlength)
						{
						    //System.out.println("Eliminated uncompressed segment 1.");	
							compressed_segment   = StringMapper.compressStrings(augmented_segment);
							if(compressed_segment.length - 1 > max_segment_bytelength)
							{
								max_segment_bytelength = compressed_segment.length - 1;
								if(max_segment_bytelength > Short.MAX_VALUE * 2 + 1)
									overhead = 40;
								else if(max_segment_bytelength > Byte.MAX_VALUE * 2 + 1)
									overhead = 24;
							}
							
							spliced_segments.add(compressed_segment);
						    i++;
						}
						else
						{
							int reduced_bitlength  = current_bitlength - spliced_bits;
							int reduced_bytelength = reduced_bitlength / 8;
							if(reduced_bitlength % 8 != 0)
								reduced_bytelength++;
							reduced_bytelength++;
							
							byte [] reduced_segment = new byte[reduced_bytelength];
						
							for(int j = 0; j < reduced_segment.length - 1; j++)
								reduced_segment[j] = current_segment[j];
							
							reduced_segment[reduced_segment.length - 1] = 0;
							ratio = StringMapper.getZeroRatio(reduced_segment, reduced_bitlength, bit_table);
							if(ratio < .5)
								reduced_segment[reduced_segment.length - 1] = 16;
								
							int reduced_odd_bits  = reduced_bitlength % 8;
							if(reduced_odd_bits != 0)
							{
								byte extra_bits = (byte)(8 - reduced_odd_bits);
								extra_bits <<= 5;
								reduced_segment[reduced_segment.length - 1] |= extra_bits;
							
								byte mask = getTrailingMask(reduced_odd_bits);
								reduced_segment[reduced_segment.length - 2] &= mask;	
							}
							if(reduced_segment.length - 1 < min_segment_bytelength)
								min_segment_bytelength = compressed_segment.length - 1;
							
							int     unused_bits        = current_bitlength - spliced_bits;
							byte [] shifted_segment    = shiftRight(augmented_segment, unused_bits);
							int     shifted_bitlength  = augmented_bitlength - unused_bits;
							int     shifted_bytelength = shifted_bitlength / 8;
							if(shifted_bitlength % 8 != 0)
								shifted_bytelength++;
							shifted_bytelength++;
							
							if(shifted_segment.length != shifted_bytelength)
							{
								byte [] clipped_segment = new byte[shifted_bytelength];
								for(int k = 0; k < shifted_bytelength; k++)
								    clipped_segment[k] = shifted_segment[k];
								shifted_segment = clipped_segment;
							}
							
							shifted_segment[shifted_segment.length - 1] = 0;
							ratio                     = StringMapper.getZeroRatio(shifted_segment, shifted_bitlength, bit_table);
							if(ratio < .5)
								shifted_segment[shifted_segment.length - 1] = 16;
								
							int shifted_odd_bits = shifted_bitlength % 8;
							if(shifted_odd_bits != 0)
							{
								byte extra_bits = (byte)(8 - shifted_odd_bits);
								extra_bits <<= 5;
								shifted_segment[shifted_segment.length - 1] |= extra_bits;
							
								byte mask = getTrailingMask(shifted_odd_bits);
								shifted_segment[shifted_segment.length - 2] &= mask;
							}
							
							compressed_segment  = StringMapper.compressStrings(shifted_segment);
							if(compressed_segment.length - 1 > max_segment_bytelength)
								max_segment_bytelength = compressed_segment.length - 1;
							
							byte [] processed_segment = StringMapper.decompressStrings(compressed_segment);
							
							int processed_bitlength = StringMapper.getBitlength(processed_segment);
							processed_bitlength    += StringMapper.getBitlength(reduced_segment);
							
							if(processed_bitlength != augmented_bitlength)
							{
								System.out.println("Bit lengths do not agree.");
								System.out.println("Current bit length is " + current_bitlength);
								System.out.println("Decompressed bit length of next segment is " + decompressed_bitlength);
								System.out.println("Reduced bit length is " + reduced_bitlength);
								System.out.println("Decompressed bit length of augmented segment is " + processed_bitlength);
								
								System.out.println();
							}
						
							spliced_segments.add(reduced_segment);
							spliced_segments.add(compressed_segment);
							i++;
						}
					}
					else
					{
						// The current uncompressed segment is added to the spliced list with no modification.
						spliced_segments.add(current_segment);
					}
				}
			}	
		}
		
		// We have one segment left that wasn't processed, since it doesn't have a following segment.
		byte [] last_segment = segments.get(number_of_segments - 1);
		spliced_segments.add(last_segment);
		
		ArrayList result = new ArrayList();
		result.add(spliced_segments);
		result.add(min_segment_bytelength);
		result.add(max_segment_bytelength);
		result.add(total_spliced_bits);
		result.add(max_spliced_bits);
		return result;	
	}
	
	public static ArrayList splice2(ArrayList<byte[]> segments, int min_segment_bytelength, int max_segment_bytelength)
	{
		int        number_of_segments = segments.size();
		boolean [] isCompressed       = new boolean[number_of_segments];
		
		for(int i = 0; i < number_of_segments; i++)
		{
			byte[] segment = segments.get(i);
			int iterations = StringMapper.getIterations(segment);
			if(iterations != 0 && iterations != 16)
				isCompressed[i] = true;	    	
		}
		
		int overhead = 16;
		if(max_segment_bytelength > Short.MAX_VALUE * 2 + 1)
			overhead = 40;
		else if(max_segment_bytelength > Byte.MAX_VALUE * 2 + 1)
			overhead = 24;
		
		int   total_spliced_bits = 0;
		int   max_spliced_bits   = 0;
		int[] bit_table          = StringMapper.getBitTable();
		
		ArrayList<byte[]> spliced_segments   = new ArrayList<byte[]>();
		byte []           first_segment      = segments.get(0);
		spliced_segments.add(first_segment);
		
		for(int i = 1; i < number_of_segments; i++)
		{
			byte[] current_segment  = segments.get(i);
			if(isCompressed[i])
				spliced_segments.add(current_segment);
			else
			{
				if(!isCompressed[i - 1])
					spliced_segments.add(current_segment);
				else
				{
					byte[] previous_segment       = spliced_segments.getLast();
				    byte[] decompressed_segment   = StringMapper.decompressStrings(previous_segment);
				    int    previous_bitlength     = StringMapper.getBitlength(previous_segment);
				    int    decompressed_bitlength = StringMapper.getBitlength(decompressed_segment);
				    int    current_bitlength      = StringMapper.getBitlength(current_segment);
				    int    augmented_bitlength    = decompressed_bitlength + current_bitlength;
				    int    augmented_bytelength   = augmented_bitlength / 8;
				    if(augmented_bitlength % 8 != 0)
					    augmented_bytelength++;
				    augmented_bytelength++;
				
				    byte[] augmented_segment = new byte[augmented_bytelength];
				    for(int j = 0; j < decompressed_segment.length - 1; j++)
					    augmented_segment[j] = decompressed_segment[j];
				
				    int decompressed_odd_bits = decompressed_bitlength % 8;
				    if(decompressed_odd_bits == 0)
				    {
					    int k = 0;
					    for(int j = decompressed_segment.length - 1; j < augmented_segment.length - 1; j++)
						    augmented_segment[j] = current_segment[k++];
				    }
				    else
				    {
					    int  extra_bits  = 8 - decompressed_odd_bits;
					    byte splice_byte = current_segment[0];
					    byte mask        = getTrailingMask(extra_bits);
					    splice_byte     &= mask;
					    splice_byte    <<= decompressed_odd_bits;
					
					    augmented_segment[decompressed_segment.length - 2] |= splice_byte;
					
					    byte [] clipped_segment = new byte[current_segment.length - 1];
					    for(int j = 0; j < clipped_segment.length; j++)
						    clipped_segment[j] = current_segment[j];	
					    byte [] shifted_segment = shiftRight(clipped_segment, extra_bits);
					
					    int k = 0;
					    for(int j = decompressed_segment.length - 1; j < augmented_segment.length - 1; j++)
					        augmented_segment[j] = shifted_segment[k++];
				    }
				
				    double ratio = StringMapper.getZeroRatio(augmented_segment, augmented_bitlength, bit_table);
				    if(ratio < .5)
					    augmented_segment[augmented_segment.length - 1] = 16;
				    else 
					    augmented_segment[augmented_segment.length - 1] = 0;
				    int augmented_odd_bits     = augmented_bitlength % 8;
				    if(augmented_odd_bits != 0)
				    {   
					    byte extra_bits = (byte)(8 - augmented_odd_bits);
					    extra_bits <<= 5;
					    augmented_segment[augmented_segment.length - 1] |= extra_bits;
					
					    byte mask = getTrailingMask(augmented_odd_bits);
					    augmented_segment[augmented_segment.length - 2] &= mask;
				    }
				
				    byte [] compressed_segment   = StringMapper.compressStrings(augmented_segment);
				    int     compressed_bitlength = StringMapper.getBitlength(compressed_segment);
				    int     spliced_bits         = current_bitlength;
				    //int   bit_reduction          = spliced_bits - (compressed_bitlength - previous_bitlength);
				    int     bit_reduction        = (spliced_bits + overhead) - (compressed_bitlength - previous_bitlength);
				    int     max_reduction        = bit_reduction;
			
				    for(int j = 1; j < current_bitlength; j ++)
				    {
					    int clipped_bitlength  = augmented_bitlength - j;
					    int clipped_bytelength = clipped_bitlength / 8;
					    if(clipped_bitlength % 8 != 0)
						    clipped_bytelength++;
					    clipped_bytelength++;
					
					    byte [] clipped_segment = new byte[clipped_bytelength];
					    for(int k = 0; k < clipped_segment.length - 1; k++)
						    clipped_segment[k] = augmented_segment[k];
					
					    clipped_segment[clipped_segment.length - 1] = 0;
					    ratio = StringMapper.getZeroRatio(clipped_segment, clipped_bitlength, bit_table);
					    if(ratio < .5)
						    clipped_segment[clipped_segment.length - 1] = 16;
						    
					    int clipped_odd_bits            = clipped_bitlength % 8;
					    if(clipped_odd_bits != 0)
					    {
						    byte extra_bits = (byte)(8 - clipped_odd_bits);
						    extra_bits    <<= 5;
						    clipped_segment[clipped_segment.length - 1] |= extra_bits;
						
						    byte mask = getTrailingMask(clipped_odd_bits);
						    clipped_segment[clipped_segment.length - 2] &= mask;
					    }
					
					    compressed_segment   = StringMapper.compressStrings(clipped_segment);
					    compressed_bitlength = StringMapper.getBitlength(compressed_segment);
					
					    int current_bits  = current_bitlength - j;
					    bit_reduction     = current_bits - (compressed_bitlength - previous_bitlength);
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
					
				        if(spliced_bits == current_bitlength)
				        {
				            //System.out.println("Eliminated uncompressed segment 2.");
				            compressed_segment   = StringMapper.compressStrings(augmented_segment);
				            int j = spliced_segments.size();
				            spliced_segments.set(j - 1, compressed_segment);
				            if(compressed_segment.length - 1 > max_segment_bytelength)
							{
								max_segment_bytelength = compressed_segment.length - 1;
								if(max_segment_bytelength > Short.MAX_VALUE * 2 + 1)
									overhead = 40;
								else if(max_segment_bytelength > Byte.MAX_VALUE * 2 + 1)
									overhead = 24;
							}
				        }
				        else
				        {
				            int unused_bits        = current_bitlength   - spliced_bits;
				            int clipped_bitlength  = augmented_bitlength - unused_bits;
				            int clipped_bytelength = clipped_bitlength / 8;
				            if(clipped_bitlength % 8 != 0)
				                clipped_bytelength++;
				            clipped_bytelength++;
						
				            byte [] clipped_segment = new byte[clipped_bytelength];
				            for(int k = 0; k < clipped_segment.length - 1; k++)
				                clipped_segment[k] = augmented_segment[k];
						
				            clipped_segment[clipped_segment.length - 1] = 0;
				            ratio = StringMapper.getZeroRatio(clipped_segment, clipped_bitlength, bit_table);
				            if(ratio < .5)
				                clipped_segment[clipped_segment.length - 1] = 16;
				                
				            int clipped_odd_bits            = clipped_bitlength % 8;
				            if(clipped_odd_bits != 0)
				            {
							    byte extra_bits = (byte)(8 - clipped_odd_bits);
							    extra_bits <<= 5;
							    clipped_segment[clipped_segment.length - 1] |= extra_bits;
							
							    byte mask = getTrailingMask(clipped_odd_bits);
							    clipped_segment[clipped_segment.length - 2] &= mask;
						    }
				            compressed_segment = StringMapper.compressStrings(clipped_segment);
				            if(compressed_segment.length - 1 > max_segment_bytelength)
			            	        max_segment_bytelength = compressed_segment.length - 1;
				            
				            
							int j = spliced_segments.size();
							spliced_segments.set(j - 1, compressed_segment);
							
							byte [] decompressed_clipped_segment = StringMapper.decompressStrings(clipped_segment);
							
							int clipped_segment_bitlength = StringMapper.getBitlength(decompressed_clipped_segment);
							
							clipped_segment = new byte[current_segment.length - 1];
							for(j = 0; j < clipped_segment.length; j++)
								clipped_segment[j] = current_segment[j];
							byte [] shifted_segment = shiftRight(clipped_segment, spliced_bits);
							
							int reduced_bitlength  = current_bitlength - spliced_bits;
							int reduced_bytelength =  reduced_bitlength / 8;
							if(reduced_bitlength % 8 != 0)
								reduced_bytelength++;
							reduced_bytelength++;
							byte [] reduced_segment = new byte[reduced_bytelength];
							
							if(reduced_segment.length - 1 < min_segment_bytelength)
								min_segment_bytelength = reduced_segment.length - 1;
							
							for(j = 0 ; j < shifted_segment.length; j++)
								reduced_segment[j] = shifted_segment[j];
							ratio = StringMapper.getZeroRatio(reduced_segment, reduced_bitlength);
							if(ratio < .5)
								reduced_segment[reduced_segment.length - 1] = 16;
							else
								reduced_segment[reduced_segment.length - 1] = 0;
							int reduced_odd_bits  = reduced_bitlength % 8;
							if(reduced_odd_bits != 0)
							{
								byte extra_bits = (byte)(8 - reduced_odd_bits);
								extra_bits    <<= 5;
								reduced_segment[reduced_segment.length - 1] |= extra_bits;
							}
							spliced_segments.add(reduced_segment);
						}
					}	
				    else
				    {
				    	    // There was no bit reduction.
					    spliced_segments.add(current_segment);
				    }
			    }
		    }	
		}
		
		ArrayList result = new ArrayList();
		result.add(spliced_segments);
		result.add(min_segment_bytelength);
		result.add(max_segment_bytelength);
		result.add(total_spliced_bits);
		result.add(max_spliced_bits);
		return result;	
	}
	
	public static byte [] restore(ArrayList<byte[]> segments, byte string_data)
	{
		
	    int number_of_segments = segments.size();
	    
	    int total_bitlength = 0;
	    for(int i = 0; i < number_of_segments; i++)
	    {
	    	    byte [] segment    = segments.get(i);
	    	    int     iterations = StringMapper.getIterations(segment);
	    	    
	    	    if(iterations == 0 || iterations == 16)
	    	    {
	    	        int     bitlength  = StringMapper.getBitlength(segment);
	    	        total_bitlength   += bitlength;
	    	    }
	    	    else
	    	    {
	    	    	    byte [] decompressed_segment = StringMapper.decompressStrings(segment);
	    	    	    int     bitlength            = StringMapper.getBitlength(decompressed_segment);
		    	    total_bitlength             += bitlength;
	    	    }
	    }
	    
	    int bytelength  = total_bitlength / 8;
	    if(total_bitlength % 8 != 0)
	    	    bytelength++;
	    bytelength++;
	    byte [] dst     = new byte[bytelength]; 
	    int bit_offset  = 0;
        int byte_offset = 0;
        for(int i = 0; i < number_of_segments; i++)
		{
        	    byte[] segment    = segments.get(i); 
        	    int    iterations = StringMapper.getIterations(segment);
        	    if(iterations != 0 && iterations != 16)
        	    {
        	    	    segment = StringMapper.decompressStrings(segment);
        	    	    iterations = StringMapper.getIterations(segment);
        	    }
        	    
        	    int bitlength  = StringMapper.getBitlength(segment);
        	    int bit_shift  = bit_offset % 8;
        	    if(bit_shift == 0)
        	    {
        	    	    for(int j = 0; j < segment.length - 1; j++)
					    dst[byte_offset + j] = segment[j];   
        	    }
        	    else
        	    {
        	      	byte [] clipped_segment = new byte[segment.length - 1];
				for(int j = 0; j < clipped_segment.length; j++)
					clipped_segment[j] = segment[j];
					
				byte [] shifted_segment = SegmentMapper.shiftLeft(clipped_segment, bit_shift);
				dst[byte_offset] |= shifted_segment[0]; 
				
				for(int j = 1; j < shifted_segment.length; j++)
				{
					dst[byte_offset + j] = shifted_segment[j];
				}
        	    }
        	    
        	    bit_offset += bitlength;
        	    byte_offset = bit_offset / 8;
		}
	    
        dst[dst.length - 1] = string_data;
		return dst;
	}
	
	public static ArrayList getSegmentedData(byte[] string, int minimum_bitlength, int segment_type, int merge_type, double bin)
	{
		ArrayList result = new ArrayList();
		if(minimum_bitlength % 8 != 0)
		{
			System.out.println("Minimum segment bitlength must be a multiple of 8.");
			return result;
		}
		
		if(segment_type < 0 || segment_type > 3)
		{
			System.out.println("Unsupported segment type.");
			return result;	
		}
		else
		{
			//double bin                     = .02;
			ArrayList segmented_list       = segment(string, minimum_bitlength, bin);

			ArrayList<byte[]> segments     = (ArrayList<byte[]>)segmented_list.get(0);
			
			int     min_segment_bytelength = (int)segmented_list.get(1);
			int     max_segment_bytelength = (int)segmented_list.get(2);
			byte    extra_bits             = (byte)segmented_list.get(3);
			byte    string_data            = (byte)segmented_list.get(4);
			int  [] bin_number             = (int [])segmented_list.get(5);
			
			int number_of_regular_segments = segments.size();
			if(segment_type == 0)
			{
				System.out.println("Number of regular segments is "  + number_of_regular_segments);
				System.out.println("Regular segment byte length is " + min_segment_bytelength);
				System.out.println("Odd segment byte length is "     + max_segment_bytelength);
				
				int total_bitlength = 0;
				int final_number_of_segments = segments.size();
				for(int i = 0; i < final_number_of_segments; i++)
				{
					byte [] segment = segments.get(i);
					total_bitlength += StringMapper.getBitlength(segment);
				}
				System.out.println("Total bitlength is " + total_bitlength);
				
				result.add(segments);
				result.add(max_segment_bytelength);
				return result;
			}
			
			ArrayList         merged_list     = merge(segments, bin_number, bin, min_segment_bytelength, max_segment_bytelength, extra_bits, string_data, merge_type);
			ArrayList<byte[]> merged_segments = (ArrayList<byte[]>)merged_list.get(0);
			
			int number_of_merged_segments = merged_segments.size();
			if(number_of_merged_segments == 1)
			{
				System.out.println("No segmentation with current parameters.");
				System.out.println("Returning " + number_of_regular_segments + " segments merged back into the original string.");
				System.out.println("String bitlength was " + (min_segment_bytelength * 8));
				result.add(merged_segments);
				result.add(min_segment_bytelength);
				return result;
			} 
			else
			{
				min_segment_bytelength = (int)merged_list.get(1);
				max_segment_bytelength = (int)merged_list.get(2);
				extra_bits             = (byte)merged_list.get(3);
				string_data            = (byte)merged_list.get(4);
				int number_of_uncompressed_segments          = (int)merged_list.get(5);
				int number_of_uncompressed_adjacent_segments = (int)merged_list.get(6);
				
				if(segment_type == 1)
				{
					System.out.println("Number of regular segments is "               + number_of_regular_segments);
					System.out.println("Number of merged segments is "                + number_of_merged_segments);
				    System.out.println("Maximum segment byte length is "              + max_segment_bytelength);
				    System.out.println("Number of uncompressed segments is "          + number_of_uncompressed_segments);
				    System.out.println("Number of uncompressed adjacent segments is " + number_of_uncompressed_adjacent_segments);
				    int total_bitlength = 0;
					int final_number_of_segments = merged_segments.size();
					for(int i = 0; i < final_number_of_segments; i++)
					{
					    byte [] segment = merged_segments.get(i);
					    total_bitlength += StringMapper.getBitlength(segment);
					}
					System.out.println("Total bitlength is " + total_bitlength);
				    
				    
				    result.add(merged_segments);
					result.add(max_segment_bytelength);
					return result;
				}
				else if(segment_type == 2 || segment_type == 3)
				{
					if(number_of_uncompressed_adjacent_segments == 0)
					{
						System.out.println("No uncompressed adjacent segments to combine.");
						System.out.println("Returning merged segments.");   
						System.out.println("Number of regular segments is "      + number_of_regular_segments);
					    System.out.println("Number of merged segments is "       + number_of_merged_segments);
				        System.out.println("Maximum segment byte length is "     + max_segment_bytelength);
				        System.out.println("Number of uncompressed segments is " + number_of_uncompressed_segments);
				        
				        
				        int total_bitlength = 0;
						int final_number_of_segments = merged_segments.size();
						for(int i = 0; i < final_number_of_segments; i++)
						{
						    byte [] segment = merged_segments.get(i);
						    total_bitlength += StringMapper.getBitlength(segment);
						}
						System.out.println("Total bitlength is " + total_bitlength);
				        
				        
				        result.add(merged_segments);
						result.add(max_segment_bytelength);
						return result;
					}
					
					ArrayList combined_list             = combine(merged_segments, min_segment_bytelength, max_segment_bytelength, extra_bits, string_data);
					ArrayList<byte[]> combined_segments = (ArrayList<byte[]>)combined_list.get(0);
					int number_of_combined_segments     = combined_segments.size();
					if(number_of_combined_segments == 1)
					{
						System.out.println("No segmentation with current parameters.");
						System.out.println("Returning " + number_of_merged_segments + " segments combined back into the original string.");
						result.add(combined_segments);
						result.add(max_segment_bytelength);
						return result;
					}
					
					min_segment_bytelength              = (int) combined_list.get(1);
					max_segment_bytelength              = (int) combined_list.get(2);
					extra_bits                          = (byte)combined_list.get(3);
					string_data                         = (byte)combined_list.get(4);
					number_of_uncompressed_segments     = (int) combined_list.get(5);
					
					if(segment_type == 2)
					{
						System.out.println("Number of regular segments is "      + number_of_regular_segments);
					    System.out.println("Number of merged segments is "       + number_of_merged_segments);
					    System.out.println("Number of combined segments is " + number_of_combined_segments);
					    System.out.println("Maximum segment byte length is "        + max_segment_bytelength);
					    System.out.println("Number of uncompressed segments is "    + number_of_uncompressed_segments);
					    
					    int total_bitlength = 0;
						int final_number_of_segments = combined_segments.size();
						for(int i = 0; i < final_number_of_segments; i++)
						{
						    byte [] segment = combined_segments.get(i);
						    total_bitlength += StringMapper.getBitlength(segment);
						}
						System.out.println("Total bitlength is " + total_bitlength);
					    
					    
					    result.add(combined_segments);
						result.add(max_segment_bytelength);
						return result;
					}
					
					if(segment_type == 3)
					{
						if(number_of_uncompressed_segments == 0)
						{
							System.out.println("No uncompressed segments to borrow bits from.");
							System.out.println("Returning " + number_of_combined_segments + " combined segments.");
						    System.out.println("Maximum segment byte length is "     + max_segment_bytelength);
						    result.add(combined_segments);
							result.add(max_segment_bytelength);
							return result;
						}
						
						// It's probably impossible for the segments to splice down to one segment, at least with
						// this chain of processing.  Not bothering to check. 
						// Not passing the extra bits information because it never gets used and after this step
						// the segments are no longer regular, meaning any of the uncompressed segments might have
						// uneven bit lengths, not just the last one.
						ArrayList spliced_list              = splice(combined_segments, min_segment_bytelength, max_segment_bytelength);
						ArrayList<byte[]> spliced_segments  = (ArrayList<byte[]>)spliced_list.get(0);
						int number_of_spliced_segments      = spliced_segments.size();
						min_segment_bytelength              = (int) spliced_list.get(1);
						max_segment_bytelength              = (int) spliced_list.get(2);
						int total_spliced_bits              = (int) spliced_list.get(3);
						int max_spliced_bits1               = (int) spliced_list.get(4);
						
						ArrayList spliced_list2             = splice2(combined_segments, min_segment_bytelength, max_segment_bytelength);
						ArrayList<byte[]> spliced_segments2 = (ArrayList<byte[]>)spliced_list2.get(0);
						number_of_spliced_segments          = spliced_segments2.size();
						min_segment_bytelength              = (int) spliced_list2.get(1);
						max_segment_bytelength              = (int) spliced_list2.get(2);
						total_spliced_bits                 += (int) spliced_list2.get(3);
						int max_spliced_bits2               = (int) spliced_list2.get(4);
						
						int max_spliced_bits = max_spliced_bits1;
						if(max_spliced_bits1 < max_spliced_bits2)
							max_spliced_bits = max_spliced_bits2;
						
						System.out.println("Number of regular segments is "  + number_of_regular_segments);
					    System.out.println("Number of merged segments is "   + number_of_merged_segments);
					    System.out.println("Number of combined segments is " + number_of_combined_segments);
						System.out.println("Number of spliced segments is "  + number_of_spliced_segments);
						
						int total_bitlength = 0;
						int final_number_of_segments = spliced_segments2.size();
						for(int i = 0; i < final_number_of_segments; i++)
						{
						    byte [] segment = spliced_segments2.get(i);
						    total_bitlength += StringMapper.getBitlength(segment);
						}
						System.out.println("Total bitlength is " + total_bitlength);
						
						/*
						System.out.println("Maximum segment byte length is " + max_segment_bytelength);
						System.out.println("Maximum number of spliced bits is " + max_spliced_bits);
						System.out.println("Total number of spliced bits is " + total_spliced_bits);
						*/
						
						result.add(spliced_segments2);
					    result.add(max_segment_bytelength);
					    return result;
					}
				}
				
				return result;
		    }
		}
	}

	public static byte[] shiftRight(byte[] src, int bit_length)
	{
		int src_length  = 8 * src.length;
		int dst_length  = src_length - bit_length;
		int byte_length = dst_length / 8;
		if(dst_length % 8 != 0)
			byte_length++;
		
		byte [] dst     = new byte[byte_length];
		
		
		double bit_shift = bit_length;
		bit_shift       /= 8;
		
		/*
		System.out.println("Src length is " + src.length);
		System.out.println("Dst length is " + dst.length);
		System.out.println("Bit shift is " + String.format("%.2f", bit_shift));
		System.out.println();
		*/
		
		int offset = bit_length / 8;
		int j = 0;
		for(int i = offset; i < src.length; i++)
			dst[j++] = src[i];
		
		int shift = bit_length % 8;
		if(shift != 0)
		{
			int  reverse_shift  = 8 - shift;
		    int  remaining_bits = reverse_shift;
		    byte mask           = getTrailingMask(remaining_bits);
		    
		    for(int i = 0; i < dst.length - 1; i++)
			{
		    	    // Shift out the least significant bits.
				dst[i] >>= shift;
		        
		        // Zero the empty most significant bits.
			    dst[i]  &= mask;
			    
			    // Logically or the least significant bits from
			    // the next byte with the most significant empty
			    // bits in the current byte.
				byte extra_bits = (byte) (dst[i + 1] << reverse_shift);
				dst[i] |= extra_bits;
			}
		    
		    // Shift out the least significant bits
		    // that were already written to the previous byte.
			dst[dst.length - 1] >>= shift;
		    // Zero the empty bits.
			dst[dst.length - 1]  &= mask;
		}
		
		return dst;
	}
	
	public static ArrayList shiftRight2(byte[] src, int bitlength)
	{
		ArrayList result = new ArrayList();
		
		int original_bitlength  = 8 * src.length;
		int remainder_bitlength = original_bitlength - bitlength;
		int fragment_bitlength  = original_bitlength - remainder_bitlength;
		
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