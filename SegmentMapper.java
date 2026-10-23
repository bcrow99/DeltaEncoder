import java.util.ArrayList;

/**
 * Segmentation of unary bit strings ({@link StringMapper}'s format) into
 * pieces that compress well on their own.
 * <p>
 * A string's zero-bit ratio varies along it, and the bit-pair transforms of
 * {@link StringMapper#compressStrings} only pay off where one kind of bit
 * dominates. So the string is cut into regular segments ({@link #segment}),
 * neighbouring segments with similar zero-bit ratios are joined and
 * compressed ({@link #merge3}), runs of segments that still don't compress
 * are joined ({@link #combine3}), and optionally bits are moved between
 * compressed and uncompressed neighbours ({@link #splice}, {@link #splice2}).
 * {@link #getSegmentedData3} runs the whole chain; {@link #restore2} undoes it.
 * {@link #packSegments3} and {@link #unpackSegments3} store the segments back
 * to back for entropy coding.
 * <p>
 * <b>Segments.</b> Each segment is a string in StringMapper's format: its data
 * bits, least significant bit first, then a trailing data byte (bits 0-3: the
 * number of compression passes; bit 4: the transform, 1 when ones are the
 * majority; bits 5-7: the unused bits in the last data byte).
 * <p>
 * In source order: splitting into segments, joining segments, the whole
 * chain, restoring, packing for storage, bit shifts, and masks and bits.
 * Within each section the version in use comes first and older ones after.
 * <p>
 * Version 1.1.
 */
public class SegmentMapper
{
	/** Not used: all of this class's methods are static. */
	public SegmentMapper() {}

	// ---- Splitting into segments -----------------------------------------------------

	/**
	 * Cuts a string into regular segments of bitlength bits; the last segment
	 * also takes the remainder. Each segment gets its own trailing data byte
	 * (passes 0, the transform bit set when ones are the majority, and for the
	 * last segment the unused-bit count), and each segment's zero-bit ratio is
	 * sorted into bins of width bin. A string shorter than one segment becomes a
	 * single segment.
	 *
	 * @param string    the string, with its trailing data byte
	 * @param bitlength the segment length in bits (a multiple of 8)
	 * @param bin       the bin width for zero-bit ratios, e.g. 0.05
	 * @return [segments (ArrayList of byte[]), the regular segment's byte length,
	 *         the last segment's byte length (both including the data byte),
	 *         the last segment's unused-bit count shifted into bits 5-7 (Byte),
	 *         the string's trailing data byte (Byte), each segment's bin
	 *         (int[])]; empty if bitlength is not a multiple of 8
	 */
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

		int[] bit_table  = StringMapper.getBitTable();

		// A string shorter than one segment would otherwise give no segments at all
		// (the loop below runs number_of_segments times): it becomes one segment,
		// sized to the actual data.
		if(number_of_segments == 0)
		{
			int last_segment_bitlength  = string_bitlength;
			int last_segment_bytelength = last_segment_bitlength / 8;
			byte extra_bits             = 0;
			if(last_segment_bitlength % 8 != 0)
			{
				extra_bits   = (byte) (8 - (last_segment_bitlength % 8));
				extra_bits <<= 5;
				last_segment_bytelength++;
			}
			last_segment_bytelength++;

			byte string_data = string[string.length - 1];

			byte[] segment = new byte[last_segment_bytelength];
			for(int j = 0; j < segment.length - 1; j++)
				segment[j] = string[j];
			segment[segment.length - 1] = extra_bits;
			double zero_ratio = StringMapper.getZeroRatio(segment, last_segment_bitlength, bit_table);
			int    bin_num    = getBinNumber(zero_ratio, bin);
			if(zero_ratio < .5)
				segment[segment.length - 1] |= 16;

			ArrayList<byte[]> segments = new ArrayList<byte[]>();
			segments.add(segment);
			int[] bin_number = { bin_num };

			result.add(segments);
			result.add(last_segment_bytelength);
			result.add(last_segment_bytelength);
			result.add(extra_bits);
			result.add(string_data);
			result.add(bin_number);
			return result;
		}

		int segment_bitlength  = bitlength;
		int segment_bytelength = bitlength / 8;
		segment_bytelength++;
		
		int odd_bits                = string_bitlength % bitlength;
		int last_segment_bitlength  = bitlength + odd_bits;
		int last_segment_bytelength = last_segment_bitlength / 8;
		byte extra_bits        = 0;
		if(odd_bits % 8 != 0)
		{
			// The padding that rounds the last segment up to whole bytes depends on
			// odd_bits % 8, not odd_bits (which can be up to bitlength - 1).
			extra_bits   = (byte) (8 - (odd_bits % 8));
			extra_bits <<= 5;
			last_segment_bytelength++;
		}
		last_segment_bytelength++;
		
		int  min_segment_bytelength = segment_bytelength;
		int  max_segment_bytelength = last_segment_bytelength;
		byte string_data            = string[string.length - 1]; 
		
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

	/**
	 * The bin a zero-bit ratio falls in: bins are (k * bin, (k + 1) * bin], with
	 * 0 in bin 0.
	 *
	 * @param ratio the zero-bit ratio
	 * @param bin   the bin width
	 * @return the bin number
	 */
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

	/**
	 * The merge rules shared by the merge methods. bin_divider is half the
	 * number of bins (the bin of a 0.5 ratio) and difference a quarter of them.
	 * <ul>
	 * <li>0: both bins on the same side of bin_divider;</li>
	 * <li>1: both clearly on one side (at least two bins from bin_divider);</li>
	 * <li>2: on the same side and less than difference bins apart;</li>
	 * <li>3: the same bin.</li>
	 * </ul>
	 *
	 * @param merge_type  0 to 3, from least to most exclusive
	 * @param current_bin the bin of the first segment of the run
	 * @param next_bin    the bin of the segment that might join it
	 * @param bin_divider half the number of bins
	 * @param difference  a quarter of the number of bins
	 * @return whether next_bin may join the run
	 */
	private static boolean isSimilarBin(int merge_type, int current_bin, int next_bin, int bin_divider, int difference)
	{
		if(merge_type == 0)
			return (current_bin < bin_divider && next_bin < bin_divider) || (current_bin >= bin_divider && next_bin >= bin_divider);
		else if(merge_type == 1)
			return (current_bin < bin_divider - 1 && next_bin < bin_divider - 1) || (current_bin > bin_divider + 1 && next_bin > bin_divider + 1);
		else if(merge_type == 2)
			return (current_bin < bin_divider && next_bin < bin_divider && Math.abs(current_bin - next_bin) < difference) || (current_bin >= bin_divider && next_bin >= bin_divider && Math.abs(current_bin - next_bin) < difference);
		else if(merge_type == 3)
			return current_bin == next_bin;
		return false;
	}

	/**
	 * Whether two numbers fall in the same of three ranges: both below
	 * lower_limit, both above upper_limit, or both between them (inclusive).
	 *
	 * @param current_number a number
	 * @param next_number    another number
	 * @param lower_limit    the bottom of the middle range
	 * @param upper_limit    the top of the middle range
	 * @return whether they are in the same range
	 */
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

	// ---- Joining segments: merging similar ones, combining uncompressed ones, splicing 

	/**
	 * Joins runs of neighbouring segments with similar zero-bit ratios and
	 * compresses every resulting segment ({@link StringMapper#compressStrings},
	 * in parallel). A run starts at a segment and takes each following segment
	 * whose bin is similar to the first one's ({@link #isSimilarBin}), up to
	 * max_run segments.
	 *
	 * @param segments               the segments, from {@link #segment}
	 * @param bin_number             each segment's bin
	 * @param bin                    the bin width used
	 * @param min_segment_bytelength the regular segment's byte length (with its
	 *                               data byte)
	 * @param max_segment_bytelength the last segment's byte length (with its
	 *                               data byte)
	 * @param extra_bits             the last segment's unused-bit count, in bits
	 *                               5-7
	 * @param string_data            the original string's trailing data byte
	 * @param merge_type             the similarity rule, 0 to 3 (see
	 *                               {@link #isSimilarBin})
	 * @param max_run                the most segments one run may join (1: no
	 *                               merging)
	 * @return [segments] if everything merged into one; otherwise [segments, the
	 *         smallest and largest byte length (without data bytes), extra_bits,
	 *         string_data, the number of segments that didn't compress, the
	 *         number of uncompressed segments that follow another, the most
	 *         compression passes any segment took]
	 */
	public static ArrayList merge3(ArrayList<byte[]> segments, int [] bin_number, double bin, int min_segment_bytelength, int max_segment_bytelength, byte extra_bits, byte string_data, int merge_type, int max_run)
	{
		ArrayList<byte[]> merged_segments    = new ArrayList<byte[]>();
		int               number_of_segments = segments.size();
		int               number_of_bins     = (int)(1. / bin);
		int               bin_divider        = number_of_bins / 2;
		int               difference         = bin_divider / 2;

		int [] bit_table = StringMapper.getBitTable();
		int i = 0;
		for(i = 0; i < number_of_segments - 1; i++)
		{
			int current_bin = bin_number[i];
			int j           = 1;
			int next_bin    = bin_number[i + j];

			boolean similar = max_run > 1 && isSimilarBin(merge_type, current_bin, next_bin, bin_divider, difference);

			if(similar)
			{
				while(similar && i + j < number_of_segments - 1 && j + 1 < max_run)
				{
					next_bin = bin_number[i + j + 1];
					similar  = isSimilarBin(merge_type, current_bin, next_bin, bin_divider, difference);
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
					merged_segment[merged_bytelength - 1] |= extra_bits;

				int bitlength = StringMapper.getBitlength(merged_segment);
				double ratio  = StringMapper.getZeroRatio(merged_segment, bitlength, bit_table);
				if(ratio < .5)
					merged_segment[merged_bytelength - 1] |= 16;

				merged_segments.add(merged_segment);
				i += j;
			}
			else
				merged_segments.add(segments.get(i));
		}

		if(i == number_of_segments - 1)
			merged_segments.add(segments.get(i));

		int number_of_merged_segments = merged_segments.size();

		// Compress every merged segment in parallel.
		byte[][] compressed = new byte[number_of_merged_segments][];
		java.util.stream.IntStream.range(0, number_of_merged_segments).parallel().forEach(k ->
			compressed[k] = StringMapper.compressStrings(merged_segments.get(k)));

		ArrayList<byte[]> compressed_segments = new ArrayList<byte[]>();

		max_segment_bytelength                       = 0;
		min_segment_bytelength                       = Integer.MAX_VALUE;
		int number_of_uncompressed_segments          = 0;
		int number_of_uncompressed_adjacent_segments = 0;
		int previous_iterations                      = 1;
		int max_iterations                           = 0;

		for(i = 0; i < number_of_merged_segments; i++)
		{
			byte[] compressed_segment = compressed[i];
			compressed_segments.add(compressed_segment);
			if(compressed_segment.length - 1 > max_segment_bytelength)
				max_segment_bytelength = compressed_segment.length - 1;
			if(compressed_segment.length - 1 < min_segment_bytelength)
				min_segment_bytelength = compressed_segment.length - 1;
			int current_iterations = StringMapper.getIterations(compressed_segment);
			if(current_iterations == 0 || current_iterations == 16)
				number_of_uncompressed_segments++;
			else if(current_iterations > 16)
			{
				if(max_iterations < current_iterations - 16)
					max_iterations = current_iterations - 16;
			}
			else
			{
				if(max_iterations < current_iterations)
					max_iterations = current_iterations;
			}
			if((previous_iterations == 0 || previous_iterations == 16) && (current_iterations == 0 || current_iterations == 16))
				number_of_uncompressed_adjacent_segments++;
			previous_iterations = current_iterations;
		}

		ArrayList result = new ArrayList();
		result.add(compressed_segments);
		if(compressed_segments.size() == 1)
			return result;
		result.add(min_segment_bytelength);
		result.add(max_segment_bytelength);
		result.add(extra_bits);
		result.add(string_data);
		result.add(number_of_uncompressed_segments);
		result.add(number_of_uncompressed_adjacent_segments);
		result.add(max_iterations);
		return result;
	}

	/**
	 * {@link #merge3} with no limit on the run length.
	 *
	 * @param segments               the segments, from {@link #segment}
	 * @param bin_number             each segment's bin
	 * @param bin                    the bin width used
	 * @param min_segment_bytelength the regular segment's byte length
	 * @param max_segment_bytelength the last segment's byte length
	 * @param extra_bits             the last segment's unused-bit count, in bits 5-7
	 * @param string_data            the original string's trailing data byte
	 * @param merge_type             the similarity rule, 0 to 3
	 * @return as {@link #merge3}
	 */
	public static ArrayList merge2(ArrayList<byte[]> segments, int [] bin_number, double bin, int min_segment_bytelength, int max_segment_bytelength, byte extra_bits, byte string_data, int merge_type)
	{
		return merge3(segments, bin_number, bin, min_segment_bytelength, max_segment_bytelength, extra_bits, string_data, merge_type, Integer.MAX_VALUE);
	}

	/**
	 * The same as {@link #merge2} (the original version, which compressed the
	 * segments one at a time).
	 *
	 * @param segments               the segments, from {@link #segment}
	 * @param bin_number             each segment's bin
	 * @param bin                    the bin width used
	 * @param min_segment_bytelength the regular segment's byte length
	 * @param max_segment_bytelength the last segment's byte length
	 * @param extra_bits             the last segment's unused-bit count, in bits 5-7
	 * @param string_data            the original string's trailing data byte
	 * @param merge_type             the similarity rule, 0 to 3
	 * @return as {@link #merge3}
	 */
	public static ArrayList merge(ArrayList<byte[]> segments, int [] bin_number, double bin, int min_segment_bytelength, int max_segment_bytelength, byte extra_bits, byte string_data, int merge_type)
	{
		return merge3(segments, bin_number, bin, min_segment_bytelength, max_segment_bytelength, extra_bits, string_data, merge_type, Integer.MAX_VALUE);
	}

	/**
	 * Joins runs of neighbouring segments that didn't compress (0 passes), while
	 * the joined segment stays within max_packet_bytes. A joined segment gets a
	 * new data byte (the transform bit by its zero-bit ratio; the unused-bit
	 * count from extra_bits if it ends the string).
	 *
	 * @param segments               the segments, from {@link #merge3}
	 * @param min_segment_bytelength the smallest byte length (passed through)
	 * @param max_segment_bytelength the largest byte length (updated)
	 * @param extra_bits             the last segment's unused-bit count, in bits 5-7
	 * @param string_data            the original string's trailing data byte
	 * @param max_packet_bytes       the most data bytes a joined segment may have
	 * @return [segments] if everything joined into one; otherwise [segments,
	 *         min_segment_bytelength, the largest byte length, extra_bits,
	 *         string_data, the number of segments that didn't compress]
	 */
	public static ArrayList combine3(ArrayList<byte[]> segments, int min_segment_bytelength, int max_segment_bytelength, byte extra_bits, byte string_data, int max_packet_bytes)
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

			if((current_iterations == 0 || current_iterations == 16) && (next_iterations == 0 || next_iterations == 16)
			   && current_segment.length + next_segment.length - 2 <= max_packet_bytes)
			{
				int j = 1;
				int run_bytes = current_segment.length + next_segment.length - 2;
				while((next_iterations == 0 || next_iterations == 16) && i + j + 1 < number_of_segments)
				{
					next_segment = segments.get(i + j + 1);
					next_iterations = StringMapper.getIterations(next_segment);
					if((next_iterations == 0 || next_iterations == 16) && run_bytes + next_segment.length - 1 <= max_packet_bytes)
					{ j++; run_bytes += next_segment.length - 1; }
					else break;
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

	/**
	 * {@link #combine3} with no limit on the joined length.
	 *
	 * @param segments               the segments, from {@link #merge3}
	 * @param min_segment_bytelength the smallest byte length (passed through)
	 * @param max_segment_bytelength the largest byte length (updated)
	 * @param extra_bits             the last segment's unused-bit count, in bits 5-7
	 * @param string_data            the original string's trailing data byte
	 * @return as {@link #combine3}
	 */
	public static ArrayList combine(ArrayList<byte[]> segments, int min_segment_bytelength, int max_segment_bytelength, byte extra_bits, byte string_data)
	{
		return combine3(segments, min_segment_bytelength, max_segment_bytelength, extra_bits, string_data, Integer.MAX_VALUE);
	}

	/**
	 * Moves bits from the end of each uncompressed segment onto the start of the
	 * compressed segment after it, where that makes the pair smaller: the
	 * following segment is decompressed, the uncompressed one's last k bits put in
	 * front, and the result recompressed, for every k. Moving all the bits
	 * removes the uncompressed segment, and is credited with the size of its
	 * table entry (16, 24 or 40 bits, by the width of the length field).
	 *
	 * @param segments               the segments, from {@link #combine3}
	 * @param min_segment_bytelength the smallest byte length (updated)
	 * @param max_segment_bytelength the largest byte length (updated)
	 * @return [segments, the smallest and largest byte length, the total bits
	 *         moved, the most bits moved from one segment]
	 */
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
					// Moving every bit removes the segment, saving its table entry (overhead).
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
								min_segment_bytelength = reduced_segment.length - 1;
							
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
		
		// Add the last segment only if the loop stopped short of it: a splice at
		// the second-to-last segment already consumed (and added) it.
		if(i == number_of_segments - 1)
		{
			byte [] last_segment = segments.get(number_of_segments - 1);
			spliced_segments.add(last_segment);
		}
		
		ArrayList result = new ArrayList();
		result.add(spliced_segments);
		result.add(min_segment_bytelength);
		result.add(max_segment_bytelength);
		result.add(total_spliced_bits);
		result.add(max_spliced_bits);
		return result;	
	}

	/**
	 * The mirror of {@link #splice}: moves bits from the start of each
	 * uncompressed segment onto the end of the compressed segment before it,
	 * where that makes the pair smaller.
	 *
	 * @param segments               the segments, from {@link #combine3}
	 * @param min_segment_bytelength the smallest byte length (updated)
	 * @param max_segment_bytelength the largest byte length (updated)
	 * @return [segments, the smallest and largest byte length, the total bits
	 *         moved, the most bits moved from one segment]
	 */
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
					int size = spliced_segments.size();
					byte[] previous_segment       = spliced_segments.get(size - 1);
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
				    // Moving every bit removes the segment, saving its table entry (overhead).
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
		
							clipped_segment = new byte[current_segment.length - 1];
							for(j = 0; j < clipped_segment.length; j++)
								clipped_segment[j] = current_segment[j];
							byte [] shifted_segment = shiftRight(clipped_segment, spliced_bits);
							
							int     reduced_bitlength  = current_bitlength - spliced_bits;
							int     reduced_bytelength = StringMapper.getBytelength(reduced_bitlength);
							byte [] reduced_segment    = new byte[reduced_bytelength];
							for(j = 0 ; j < shifted_segment.length; j++)
								reduced_segment[j] = shifted_segment[j];
							if(reduced_segment.length - 1 < min_segment_bytelength)
								min_segment_bytelength = reduced_segment.length - 1;
							
							ratio = StringMapper.getZeroRatio(reduced_segment, reduced_bitlength, bit_table);
							if(ratio >= .5)
								StringMapper.setData(0, 0, reduced_bitlength, reduced_segment);
							else
								StringMapper.setData(1, 0, reduced_bitlength, reduced_segment);	
							
							spliced_segments.add(reduced_segment);
						}
					}	
				    else
				    {
				    	    // There was no bit reduction, just add the current segment.
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

	// ---- The whole chain -------------------------------------------------------------

	/**
	 * Segments a string: segment, merge, then combine, with no packet (merged or
	 * combined segment) allowed to grow past maximum_bitlength. Without a cap,
	 * merging collapses long runs of similar segments into a handful of huge
	 * packets once the minimum segment length passes about 1000 bits, so packet
	 * counts drop off a cliff; with maximum_bitlength = 4 * minimum_bitlength they
	 * fall roughly by half per Segment Length level instead. maximum_bitlength
	 * &lt;= minimum_bitlength means no merging at all. This is what PacketWriter
	 * uses.
	 *
	 * @param string            the string, with its trailing data byte
	 * @param minimum_bitlength the regular segment length in bits (a multiple of 8)
	 * @param segment_type      0: segments only; 1: merged; 2: merged and combined
	 * @param merge_type        the similarity rule, 0 to 3 (see {@link #isSimilarBin})
	 * @param bin               the bin width for zero-bit ratios, e.g. 0.05
	 * @param maximum_bitlength the most bits a packet may have
	 * @return [segments, a byte length, string_data]: the byte length is the
	 *         largest segment's, except when everything merged into one segment,
	 *         when it is the regular segment's; an empty list for an invalid
	 *         minimum_bitlength or segment_type
	 * @see #restore2
	 */
	public static ArrayList getSegmentedData3(byte[] string, int minimum_bitlength, int segment_type, int merge_type, double bin, int maximum_bitlength)
	{
		if(minimum_bitlength <= 0 || minimum_bitlength % 8 != 0)
			return new ArrayList();
		return segmentedData(string, minimum_bitlength, segment_type, merge_type, bin, Math.max(1, maximum_bitlength / minimum_bitlength), maximum_bitlength / 8);
	}

	/**
	 * {@link #getSegmentedData3} with no cap on packet size: the same segments
	 * as {@link #getSegmentedData} for segment_type 0, 1 or 2, without the
	 * printed statistics. segment_type 3 (splice) isn't supported -- it's the
	 * slow path PacketWriter no longer uses; call getSegmentedData for that.
	 *
	 * @param string            the string, with its trailing data byte
	 * @param minimum_bitlength the regular segment length in bits (a multiple of 8)
	 * @param segment_type      0: segments only; 1: merged; 2: merged and combined
	 * @param merge_type        the similarity rule, 0 to 3
	 * @param bin               the bin width for zero-bit ratios
	 * @return as {@link #getSegmentedData3}
	 */
	public static ArrayList getSegmentedData2(byte[] string, int minimum_bitlength, int segment_type, int merge_type, double bin)
	{
		return segmentedData(string, minimum_bitlength, segment_type, merge_type, bin, Integer.MAX_VALUE, Integer.MAX_VALUE);
	}

	/**
	 * The chain {@link #getSegmentedData2} and {@link #getSegmentedData3} run:
	 * segment, merge (runs of at most max_run segments), then combine (packets of
	 * at most max_packet_bytes).
	 *
	 * @param string            the string, with its trailing data byte
	 * @param minimum_bitlength the regular segment length in bits (a multiple of 8)
	 * @param segment_type      0: segments only; 1: merged; 2: merged and combined
	 * @param merge_type        the similarity rule, 0 to 3
	 * @param bin               the bin width for zero-bit ratios
	 * @param max_run           the most segments one merge may join
	 * @param max_packet_bytes  the most data bytes a combined segment may have
	 * @return [segments, a byte length, string_data], or an empty list for an
	 *         invalid minimum_bitlength or segment_type
	 */
	private static ArrayList segmentedData(byte[] string, int minimum_bitlength, int segment_type, int merge_type, double bin, int max_run, int max_packet_bytes)
	{
		ArrayList result = new ArrayList();
		if(minimum_bitlength % 8 != 0 || segment_type < 0 || segment_type > 2)
			return result;

		ArrayList segmented_list       = segment(string, minimum_bitlength, bin);
		ArrayList<byte[]> segments     = (ArrayList<byte[]>)segmented_list.get(0);
		int     min_segment_bytelength = (int)segmented_list.get(1);
		int     max_segment_bytelength = (int)segmented_list.get(2);
		byte    extra_bits             = (byte)segmented_list.get(3);
		byte    string_data            = (byte)segmented_list.get(4);
		int  [] bin_number             = (int [])segmented_list.get(5);

		if(segment_type == 0)
		{
			result.add(segments);
			result.add(max_segment_bytelength);
			result.add(string_data);
			return result;
		}

		ArrayList         merged_list     = merge3(segments, bin_number, bin, min_segment_bytelength, max_segment_bytelength, extra_bits, string_data, merge_type, max_run);
		ArrayList<byte[]> merged_segments = (ArrayList<byte[]>)merged_list.get(0);

		if(merged_segments.size() == 1)
		{
			result.add(merged_segments);
			result.add(min_segment_bytelength);
			result.add(string_data);
			return result;
		}

		min_segment_bytelength = (int)merged_list.get(1);
		max_segment_bytelength = (int)merged_list.get(2);
		extra_bits             = (byte)merged_list.get(3);
		string_data            = (byte)merged_list.get(4);
		int number_of_uncompressed_adjacent_segments = (int)merged_list.get(6);

		if(segment_type == 1 || number_of_uncompressed_adjacent_segments == 0)
		{
			result.add(merged_segments);
			result.add(max_segment_bytelength);
			result.add(string_data);
			return result;
		}

		ArrayList         combined_list     = combine3(merged_segments, min_segment_bytelength, max_segment_bytelength, extra_bits, string_data, max_packet_bytes);
		ArrayList<byte[]> combined_segments = (ArrayList<byte[]>)combined_list.get(0);
		if(combined_segments.size() > 1)
			max_segment_bytelength = (int)combined_list.get(2);

		result.add(combined_segments);
		result.add(max_segment_bytelength);
		result.add(string_data);
		return result;
	}

	/**
	 * Segments a string, printing statistics at each stage: segment_type 0
	 * segments only, 1 merges them, 2 also combines, and 3 also splices
	 * (running {@link #splice} and {@link #splice2} on the combined segments and
	 * returning splice2's result; splice only adds to the statistics). Slow: the
	 * splices recompress for every split point tried.
	 *
	 * @param string            the string, with its trailing data byte
	 * @param minimum_bitlength the regular segment length in bits (a multiple of 8)
	 * @param segment_type      0 to 3, as above
	 * @param merge_type        the similarity rule, 0 to 3 (see {@link #isSimilarBin})
	 * @param bin               the bin width for zero-bit ratios
	 * @return [segments, a byte length, string_data], as
	 *         {@link #getSegmentedData3}; an empty list for an invalid
	 *         minimum_bitlength or segment_type
	 */
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
				System.out.println("Total bitlength of regular segments is " + total_bitlength);
				
				// string_data is returned at every exit, so the result alone is enough to
				// call restore().
				result.add(segments);
				result.add(max_segment_bytelength);
				result.add(string_data);
				return result;
			}
			
			ArrayList         merged_list     = merge(segments, bin_number, bin, min_segment_bytelength, max_segment_bytelength, extra_bits, string_data, merge_type);
			ArrayList<byte[]> merged_segments = (ArrayList<byte[]>)merged_list.get(0);
			
			int number_of_merged_segments = merged_segments.size();
			int merged_max_iterations = 0;
			if(number_of_merged_segments == 1)
			{
				System.out.println("No segmentation with current parameters.");
				System.out.println("Returning " + number_of_regular_segments + " segments merged back into the original string.");
				System.out.println("String bitlength was " + (min_segment_bytelength * 8));
				result.add(merged_segments);
				result.add(min_segment_bytelength);
				result.add(string_data);
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
				merged_max_iterations                        = (int)merged_list.get(7);
				
				
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
					result.add(string_data);
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
						System.out.println("Total bitlength of merged/compressed segments is " + total_bitlength);
				        
				        
				        result.add(merged_segments);
						result.add(max_segment_bytelength);
						result.add(string_data);
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
						result.add(string_data);
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
						result.add(string_data);
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
							result.add(string_data);
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
					   
						
						int total_bitlength = 0;
						int max_iterations  = 0;
						
						int [] bit_table = StringMapper.getBitTable();
						for(int i = 0; i < number_of_spliced_segments; i++)
						{
						    byte [] segment = spliced_segments2.get(i);
						    
						    int bitlength   = StringMapper.getBitlength(segment);
						    int iterations  = StringMapper.getIterations(segment);
						    double ratio    = StringMapper.getZeroRatio(segment, bitlength, bit_table);
						    total_bitlength += bitlength;
						    
						    if(iterations < 16)
						    {
						    	    if(max_iterations < iterations)
						    	    	    max_iterations = iterations;
						    }
						    else 
						    {
						    	   if(max_iterations < iterations - 16)
					    	    	    max_iterations = iterations - 16;
						    }
						    //System.out.println("Segment " + i + "\titerations " + iterations  + "\tratio " + String.format("%.2f", ratio));
						}
                         //System.out.println();
                         
                         System.out.println("Number of spliced segments is "  + number_of_spliced_segments);
                         System.out.println("Maximum iterations for spliced segments is " + max_iterations);
                         System.out.println("Maximum iterations for merged segments is " + merged_max_iterations);
						 System.out.println("Total bitlength of merged/compressed/spliced segments is " + total_bitlength);
						 System.out.println();
						
						//System.out.println("Maximum segment byte length is " + max_segment_bytelength);
						//System.out.println("Maximum number of spliced bits is " + max_spliced_bits);
						//System.out.println("Total number of spliced bits is " + total_spliced_bits);
						
						result.add(spliced_segments2);
					    result.add(max_segment_bytelength);
					    result.add(string_data);
					    return result;
					}
				}
				
				return result;
		    }
		}
	}

	// ---- Restoring -------------------------------------------------------------------

	/**
	 * Rebuilds the string from its segments: each compressed segment is
	 * decompressed (in parallel), the segments' bits joined, and string_data
	 * appended as the trailing data byte.
	 *
	 * @param segments    the segments, in order
	 * @param string_data the original string's trailing data byte
	 * @return the original string
	 */
	public static byte [] restore2(ArrayList<byte[]> segments, byte string_data)
	{
		int number_of_segments = segments.size();

		byte[][] decompressed = new byte[number_of_segments][];
		java.util.stream.IntStream.range(0, number_of_segments).parallel().forEach(i ->
		{
			byte[] segment = segments.get(i);
			int iterations = StringMapper.getIterations(segment);
			decompressed[i] = (iterations == 0 || iterations == 16) ? segment : StringMapper.decompressStrings(segment);
		});

		int total_bitlength = 0;
		for(int i = 0; i < number_of_segments; i++)
			total_bitlength += StringMapper.getBitlength(decompressed[i]);

		int bytelength = total_bitlength / 8;
		if(total_bitlength % 8 != 0)
			bytelength++;
		bytelength++;
		byte [] dst     = new byte[bytelength];
		int bit_offset  = 0;
		int byte_offset = 0;
		for(int i = 0; i < number_of_segments; i++)
		{
			byte[] segment   = decompressed[i];
			int    bitlength = StringMapper.getBitlength(segment);
			int    bit_shift = bit_offset % 8;
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

				byte [] shifted_segment = shiftLeft(clipped_segment, bit_shift);
				dst[byte_offset] |= shifted_segment[0];

				for(int j = 1; j < shifted_segment.length; j++)
					dst[byte_offset + j] = shifted_segment[j];
			}

			bit_offset += bitlength;
			byte_offset = bit_offset / 8;
		}

		dst[dst.length - 1] = string_data;
		return dst;
	}

	/**
	 * The same as {@link #restore2} (the original version, which decompressed
	 * each segment twice, one at a time).
	 *
	 * @param segments    the segments, in order
	 * @param string_data the original string's trailing data byte
	 * @return the original string
	 */
	public static byte [] restore(ArrayList<byte[]> segments, byte string_data)
	{
		return restore2(segments, string_data);
	}

	// ---- Packing for storage ---------------------------------------------------------

	/**
	 * Packs segments' bits back to back, with no data bytes and no padding
	 * between them, a byte at a time.
	 *
	 * @param segments the segments, each with its trailing data byte
	 * @return [the packed bits (byte[]), each segment's bit length (int[])]
	 * @see #unpackSegments3(byte[], int[], byte[])
	 */
	public static ArrayList packSegments3(ArrayList<byte[]> segments)
	{
		int size = segments.size();
		int[] bitlength = new int[size];

		int total_bitlength = 0;
		for(int i = 0; i < size; i++)
		{
			bitlength[i] = StringMapper.getBitlength(segments.get(i));
			total_bitlength += bitlength[i];
		}

		int total_bytelength = total_bitlength / 8;
		if(total_bitlength % 8 != 0)
			total_bytelength++;
		byte[] string = new byte[total_bytelength];

		int bit_offset = 0;
		for(int i = 0; i < size; i++)
		{
			byte[] segment = segments.get(i);
			int    length  = bitlength[i];
			int    nbytes  = (length + 7) / 8;
			for(int b = 0; b < nbytes; b++)
			{
				int value = segment[b] & 0xFF;
				if(b == nbytes - 1 && length % 8 != 0)
					value &= (1 << (length % 8)) - 1;      // only this segment's bits
				int pos   = bit_offset + 8 * b;
				int index = pos >> 3;
				int shift = pos & 7;
				string[index] |= (byte)(value << shift);
				if(shift != 0 && index + 1 < total_bytelength)
					string[index + 1] |= (byte)(value >> (8 - shift));
			}
			bit_offset += length;
		}

		ArrayList result = new ArrayList();
		result.add(string);
		result.add(bitlength);
		return result;
	}

	/**
	 * Splits packed bits back into segments, each with its data byte, reading a
	 * byte at a time. Each segment's bit length comes from its byte length and
	 * its data byte's unused-bit count.
	 *
	 * @param string     the packed bits, from {@link #packSegments3}
	 * @param bytelength each segment's byte length, without its data byte
	 * @param data       each segment's data byte
	 * @return the segments
	 */
	public static ArrayList<byte[]> unpackSegments3(byte[] string, int bytelength[], byte data[])
	{
		int number_of_segments = data.length;
		ArrayList<byte[]> unpacked_segments = new ArrayList<byte[]>();

		int bit_offset = 0;
		for(int i = 0; i < number_of_segments; i++)
		{
			int extra_bits = (data[i] >> 5) & 7;
			int bitlength  = bytelength[i] * 8 - extra_bits;
			byte[] segment = new byte[bytelength[i] + 1];
			int nbytes     = (bitlength + 7) / 8;
			for(int b = 0; b < nbytes; b++)
			{
				int pos   = bit_offset + 8 * b;
				int index = pos >> 3;
				int shift = pos & 7;
				int value = (string[index] & 0xFF) >>> shift;
				if(shift != 0 && index + 1 < string.length)
					value |= (string[index + 1] & 0xFF) << (8 - shift);
				if(b == nbytes - 1 && bitlength % 8 != 0)
					value &= (1 << (bitlength % 8)) - 1;   // only this segment's bits
				segment[b] = (byte) value;
			}
			segment[bytelength[i]] = data[i];
			unpacked_segments.add(segment);
			bit_offset += bitlength;
		}

		return unpacked_segments;
	}

	/**
	 * Splits packed bits back into bare bit strings (no data bytes), given each
	 * one's bit length.
	 *
	 * @param string    the packed bits
	 * @param bitlength each segment's bit length
	 * @return the segments, each ceil(bitlength / 8) bytes
	 */
	public static ArrayList<byte[]> unpackSegments3(byte[] string, int bitlength[])
	{
		ArrayList<byte[]> unpacked_segments = new ArrayList<byte[]>();
		int bit_offset = 0;
		for(int i = 0; i < bitlength.length; i++)
		{
			int    nbytes  = (bitlength[i] + 7) / 8;
			byte[] segment = new byte[nbytes];
			for(int b = 0; b < nbytes; b++)
			{
				int pos   = bit_offset + 8 * b;
				int index = pos >> 3;
				int shift = pos & 7;
				int value = (string[index] & 0xFF) >>> shift;
				if(shift != 0 && index + 1 < string.length)
					value |= (string[index + 1] & 0xFF) << (8 - shift);
				if(b == nbytes - 1 && bitlength[i] % 8 != 0)
					value &= (1 << (bitlength[i] % 8)) - 1;   // only this segment's bits
				segment[b] = (byte) value;
			}
			unpacked_segments.add(segment);
			bit_offset += bitlength[i];
		}
		return unpacked_segments;
	}

	/**
	 * The same as {@link #packSegments3} (an older version).
	 *
	 * @param segments the segments, each with its trailing data byte
	 * @return [the packed bits (byte[]), each segment's bit length (int[])]
	 */
	public static ArrayList packSegments(ArrayList<byte[]> segments)
	{
		return packSegments3(segments);
	}

	/**
	 * As {@link #packSegments3}, checking the number of segments against
	 * bitlength (the lengths themselves come from the segments' data bytes).
	 *
	 * @param segments  the segments, each with its trailing data byte
	 * @param bitlength one entry per segment
	 * @return [the packed bits (byte[])], or an empty list if the counts differ
	 */
	public static ArrayList packSegments(ArrayList<byte[]> segments, int[] bitlength)
	{
		ArrayList result = new ArrayList();
		if(segments.size() != bitlength.length)
		{
			System.out.println("Number of segments and number of bit lengths do not agree.");
			return result;
		}
		result.add(packSegments3(segments).get(0));
		return result;
	}

	/**
	 * The same as {@link #packSegments3} (an older version that copied a bit at a time).
	 *
	 * @param segments the segments, each with its trailing data byte
	 * @return [the packed bits (byte[]), each segment's bit length (int[])]
	 */
	public static ArrayList packSegments2(ArrayList<byte[]> segments)
	{
		return packSegments3(segments);
	}

	/**
	 * As {@link #packSegments3}, returning the bit lengths in bitlength.
	 *
	 * @param segments  the segments, each with its trailing data byte
	 * @param bitlength set to each segment's bit length (one entry per segment)
	 * @return [the packed bits (byte[])]
	 */
	public static ArrayList packSegments2(ArrayList<byte[]> segments, int[] bitlength)
	{
		ArrayList packed = packSegments3(segments);
		int[] length = (int[]) packed.get(1);
		System.arraycopy(length, 0, bitlength, 0, length.length);
		ArrayList result = new ArrayList();
		result.add(packed.get(0));
		return result;
	}

	/**
	 * The same as {@link #unpackSegments3(byte[], int[], byte[])} (an older
	 * version: it overwrote the last byte of each segment instead of adding to it, so it never round-tripped).
	 *
	 * @param string     the packed bits
	 * @param bytelength each segment's byte length, without its data byte
	 * @param data       each segment's data byte
	 * @return the segments
	 */
	public static ArrayList<byte[]> unpackSegments(byte[] string, int bytelength[], byte data[])
	{
		return unpackSegments3(string, bytelength, data);
	}

	/**
	 * The same as {@link #unpackSegments3(byte[], int[])} (an older version).
	 *
	 * @param string    the packed bits
	 * @param bitlength each segment's bit length
	 * @return the segments, without data bytes
	 */
	public static ArrayList<byte[]> unpackSegments(byte[] string, int bitlength[])
	{
		return unpackSegments3(string, bitlength);
	}

	/**
	 * The same as {@link #unpackSegments3(byte[], int[], byte[])} (an older
	 * version that copied a bit at a time).
	 *
	 * @param string     the packed bits
	 * @param bytelength each segment's byte length, without its data byte
	 * @param data       each segment's data byte
	 * @return the segments
	 */
	public static ArrayList<byte[]> unpackSegments2(byte[] string, int bytelength[], byte data[])
	{
		return unpackSegments3(string, bytelength, data);
	}

	/**
	 * The same as {@link #unpackSegments3(byte[], int[])} (an older version).
	 *
	 * @param string    the packed bits
	 * @param bitlength each segment's bit length
	 * @return the segments, without data bytes
	 */
	public static ArrayList<byte[]> unpackSegments2(byte[] string, int bitlength[])
	{
		return unpackSegments3(string, bitlength);
	}

	// ---- Bit shifts ------------------------------------------------------------------

	/**
	 * Drops the first bit_length bits of a bit string (bits run least
	 * significant first, so this shifts the rest toward the start).
	 *
	 * @param src        the bits
	 * @param bit_length how many to drop
	 * @return the remaining bits, ceil((8 * src.length - bit_length) / 8) bytes
	 */
	public static byte[] shiftRight(byte[] src, int bit_length)
	{
		int src_length  = 8 * src.length;
		int dst_length  = src_length - bit_length;
		int byte_length = dst_length / 8;
		if(dst_length % 8 != 0)
			byte_length++;
		
		byte [] dst = new byte[byte_length];
		int offset  = bit_length / 8;
		int j       = 0;
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

	/**
	 * Splits a bit string after its first bitlength bits.
	 *
	 * @param src       the bits
	 * @param bitlength where to split
	 * @return [the bits after the split (byte[]), their number (Integer), the
	 *         first bitlength bits (byte[]), bitlength (Integer)]
	 */
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

	/**
	 * Puts bit_length zero bits in front of a bit string (shifting it toward the
	 * end).
	 *
	 * @param src        the bits
	 * @param bit_length how many zero bits to add
	 * @return the bits, src.length + ceil(bit_length / 8) bytes
	 */
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

	/**
	 * As {@link #shiftLeft}, but without the extra byte for the top bits of the
	 * last source byte: it assumes they are empty (as after a
	 * {@link #shiftRight}).
	 *
	 * @param src        the bits
	 * @param bit_length how many zero bits to add
	 * @return the bits, src.length + bit_length / 8 bytes
	 */
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

	// ---- Masks and bits --------------------------------------------------------------

	/**
	 * One-bit masks, position 0 (the least significant bit, the first in a
	 * string) to 7: 0x01, 0x02, ... 0x80.
	 *
	 * @return 8 masks
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
	 * The one-bit mask for a position: 1 &lt;&lt; position.
	 *
	 * @param position 0 to 7
	 * @return the mask
	 */
	public static byte getPositiveMask(int position)
	{
		return (byte) (1 << position);
	}

	/**
	 * One-bit int masks, positions 0 to 31.
	 *
	 * @return 32 masks
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
	 * The complements of the one-bit masks: anded, they clear one bit.
	 *
	 * @return 8 masks, ~0x01 to ~0x80
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
	 * The complement of a one-bit mask: ~(1 &lt;&lt; position).
	 *
	 * @param position 0 to 7
	 * @return the mask
	 */
	public static byte getNegativeMask(int position)
	{
		return (byte) ~(1 << position);
	}

	/**
	 * Masks of the top bits of a byte: 0xFE, 0xFC, ... 0x80 (clearing the bottom
	 * 1 to 7 bits).
	 *
	 * @return 7 masks
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
	 * The mask of the top number_of_trailing_bits bits (the last bits of a byte
	 * in a string): 0xFF &lt;&lt; (8 - number_of_trailing_bits).
	 *
	 * @param number_of_trailing_bits 1 to 7
	 * @return the mask
	 */
	public static byte getLeadingMask(int number_of_trailing_bits)
	{
		return (byte) (0xFF << (8 - number_of_trailing_bits));
	}

	/**
	 * The mask that clears the bottom length bits: 0xFF &lt;&lt; length.
	 *
	 * @param length 1 to 7
	 * @return the mask
	 */
	public static byte getLeadingMask2(int length)
	{
		return (byte) (0xFF << length);
	}

	/**
	 * Masks of the bottom bits of a byte: 0x01, 0x03, ... 0x7F.
	 *
	 * @return 7 masks
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
	 * The mask of the bottom number_of_leading_bits bits (the first bits of a
	 * byte in a string): (1 &lt;&lt; number_of_leading_bits) - 1.
	 *
	 * @param number_of_leading_bits 1 to 7
	 * @return the mask
	 */
	public static byte getTrailingMask(int number_of_leading_bits)
	{
		return (byte) ((1 << number_of_leading_bits) - 1);
	}

	/**
	 * One bit of a string.
	 *
	 * @param string   the bits
	 * @param position the bit's index (least significant bit of byte 0 first)
	 * @return 0 or 1
	 */
	public static int getBit(byte[] string, int position)
	{
		int  byte_offset = position / 8;
		int  bit_offset  = position % 8;
		byte mask        = getPositiveMask(bit_offset);
		if((string[byte_offset] & mask) == 0)
			return 0;
		else
			return 1;
	}

	/**
	 * Sets one bit of a string.
	 *
	 * @param buffer   the bits
	 * @param position the bit's index
	 * @param value    0 or 1
	 */
	public static void setBit(byte[] buffer, int position, int value)
	{
		int byte_offset = position / 8;
		int bit_offset  = position % 8;
		if(value == 0)
		{
			byte mask = getNegativeMask(bit_offset);
			buffer[byte_offset] &= mask;
		} 
		else
		{
			byte mask = getPositiveMask(bit_offset);
			buffer[byte_offset] |= mask;
		}
	}

	/**
	 * Packs the bottom number_of_pack_bits bits of each byte back to back.
	 *
	 * @param src                the values
	 * @param number_of_pack_bits the bits kept from each, 1 to 7
	 * @return the packed bits
	 * @see #unpackBits
	 */
	public static byte[] packBits(byte[] src, int number_of_pack_bits)
	{
		int j               = number_of_pack_bits;
		int number_of_bits  = src.length * j;
		int number_of_bytes = number_of_bits / 8;
		if (number_of_bits % 8 != 0)
			number_of_bytes++;
		byte[] dst = new byte[number_of_bytes];

		byte[] trailing_mask = getTrailingMask();

		int offset       = 0;
		int current_bit  = 0;
		int current_byte = 0;

		for (int i = 0; i < src.length; i++)
		{
			// Mask the source value to exactly j bits before packing.
			byte value = (byte)(src[i] & trailing_mask[j - 1]);

			if (current_bit + j <= 8)
			{
				dst[current_byte] |= (byte)(value << current_bit);
			}
			else
			{
				// Low bits fit in the current byte.
				dst[current_byte] |= (byte)(value << current_bit);

				// High bits spill into the next byte.
				int number_of_extra_bits = (current_bit + j) % 8;
				int k                    = j - number_of_extra_bits;
				byte extra_value         = (byte)((value & 0xFF) >> k);
				extra_value             &= trailing_mask[number_of_extra_bits - 1];
				dst[current_byte + 1]    = extra_value;
			}
			offset      += j;
			current_bit  = offset % 8;
			current_byte = offset / 8;
		}

		return dst;
	}

	/**
	 * Unpacks values packed by {@link #packBits}.
	 *
	 * @param src                 the packed bits
	 * @param number_of_bytes     the number of values
	 * @param number_of_pack_bits the bits per value, 1 to 7
	 * @return the values
	 */
	public static byte[] unpackBits(byte[] src, int number_of_bytes, int number_of_pack_bits)
	{
		byte[] dst = new byte[number_of_bytes];

		int offset       = 0;
		int current_bit  = 0;
		int current_byte = 0;

		byte[] trailing_mask = getTrailingMask();

		int j = number_of_pack_bits;

		int i = 0;

		while (i < number_of_bytes)
		{
			byte value = src[current_byte];
			if (current_bit + j <= 8)
			{
				// All j bits are within the current byte.
				value >>= current_bit;
				value  &= trailing_mask[j - 1];
				dst[i++] = value;
			}
			else
			{
				// j bits straddle a byte boundary.
				int number_of_extra_bits = current_bit + j - 8;
				int number_of_bits       = j - number_of_extra_bits;

				value >>= current_bit;
				value  &= trailing_mask[number_of_bits - 1];

				byte extra_bits  = src[current_byte + 1];
				extra_bits      &= trailing_mask[number_of_extra_bits - 1];
				extra_bits     <<= number_of_bits;

				value   |= extra_bits;
				dst[i++] = value;
			}

			offset      += j;
			current_bit  = offset % 8;
			current_byte = offset / 8;
		}

		return dst;
	}
}
