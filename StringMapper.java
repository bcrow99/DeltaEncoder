import java.util.ArrayList;
import java.util.Hashtable;
import java.util.Collections;

/**
 * This is a class with methods to analyze and process bit strings.
 * 
 * @author Brian Crowley
 * @version 1.0
 */

public class StringMapper
{
	/**
	 * Creates a histogram from a byte array and returns a list including the
	 * histogram and some other key information. The list is not parameterized so
	 * that random types can be added.
	 *
	 * @param src the input byte array
	 * @return unparameterized list with histogram and additonal information.
	 */
	public static ArrayList getHistogram(byte src[])
	{
		int min = src[0];
		int max = src[0];
		for(int i = 0; i < src.length; i++)
		{
			if(src[i] > max)
				max = src[i];
			if(src[i] < min)
				min = src[i];
		}
		int range = max - min + 1;
		int[] histogram = new int[range];
		for(int i = 0; i < range; i++)
			histogram[i] = 0;
		for(int i = 0; i < src.length; i++)
		{
			int j = src[i] - min;
			histogram[j]++;
		}

		ArrayList histogram_list = new ArrayList();
		histogram_list.add(min);
		histogram_list.add(histogram);
		histogram_list.add(range);
		return histogram_list;
	}

	/**
	 * Creates a histogram from an int array and returns a list including the
	 * histogram and some other key information. The list is not parameterized so
	 * that random types can be added.
	 *
	 * @param src the input int array
	 * @return unparameterized list with histogram and additonal information.
	 */
	public static ArrayList getHistogram(int src[])
	{
		int min = src[0];
		int max = src[0];
		for(int i = 0; i < src.length; i++)
		{
			if(src[i] > max)
				max = src[i];
			if(src[i] < min)
				min = src[i];
		}
		int range = max - min + 1;
		int[] histogram = new int[range];
		for(int i = 0; i < range; i++)
			histogram[i] = 0;
		for(int i = 0; i < src.length; i++)
		{
			int j = src[i] - min;
			histogram[j]++;
		}
		ArrayList histogram_list = new ArrayList();
		histogram_list.add(min);
		histogram_list.add(histogram);
		histogram_list.add(range);
		return histogram_list;
	}

	/**
	 * Creates a rank table of most popular to least popular from a histogram.
	 * 
	 * @param src the input histogram
	 * @return unparameterized list with histogram and additonal information.
	 */
	public static int[] getRankTable(int src[])
	{
		ArrayList list = new ArrayList();
		Hashtable table = new Hashtable();
		for(int i = 0; i < src.length; i++)
		{
			double key = src[i];
			while (table.containsKey(key))
				key += .001;
			table.put(key, i);
			list.add(key);
		}
		Collections.sort(list);

		int rank[] = new int[src.length];
		int k = -1;
		for(int i = src.length - 1; i >= 0; i--)
		{
			double key = (double) list.get(i);
			int j = (int) table.get(key);
			rank[j] = ++k;
		}
		return rank;
	}

	/**
	 * Creates a table containing the number of 0 bits in any byte value.
	 * 
	 * @return int [] bit_table with the number of 0 bits for any byte value.
	 */
	
	
	
	// This set of functions makes no assumptions about the
	// the maxiumum length of an individual string.

	/**
	 * Uses a rank table of most popular to least popular to turn integer
	 * representations into unary strings, and then pack pack them into a byte
	 * array. It assumes dst is large enough to contain the result, and returns the
	 * length of the bit string.
	 * 
	 * @param src   the input integers
	 * @param table the rank table
	 * @return byte array containing the bit string
	 */
	public static int packStrings(int src[], int table[], byte dst[])
	{
		int size = src.length;
		for(int i = 0; i < dst.length; i++)
			dst[i] = 0;
		int[] mask = new int[8];

		mask[0] = 1;
		mask[1] = 3;
		mask[2] = 7;
		mask[3] = 15;
		mask[4] = 31;
		mask[5] = 63;
		mask[6] = 127;
		mask[7] = 255;

		int start_bit = 0;
		int stop_bit = 0;
		int p = 0;
		dst[p] = 0;

		for(int i = 0; i < size; i++)
		{
			int j = src[i];
			int k = table[j];
			if(k == 0)
			{
				start_bit++;
				if(start_bit == 8)
				{
					dst[++p] = 0;
					start_bit = 0;
				}
			} 
			else
			{
				stop_bit = (start_bit + k + 1) % 8;

				if(k <= 7)
				{
					dst[p] |= (byte) (mask[k - 1] << start_bit);
					if(stop_bit <= start_bit)
					{
						dst[++p] = 0;
						if(stop_bit != 0)
							dst[p] |= (byte) (mask[k - 1] >> (8 - start_bit));
					}
				}
				else if(k > 7)
				{
					dst[p] |= (byte) (mask[7] << start_bit);
					int m = (k - 8) / 8;
					for(int n = 0; n < m; n++)
						dst[++p] = (byte) (mask[7]);
					dst[++p] = 0;
					if(start_bit != 0)
						dst[p] |= (byte) (mask[7] >> (8 - start_bit));

					if(k % 8 != 0)
					{
						m = k % 8 - 1;
						dst[p] |= (byte) (mask[m] << start_bit);
						if(stop_bit <= start_bit)
						{
							dst[++p] = 0;
							if(stop_bit != 0)
								dst[p] |= (byte) (mask[m] >> (8 - start_bit));
						}
					}
					else if(stop_bit <= start_bit)
						dst[++p] = 0;
				}
				start_bit = stop_bit;
			}
		}
		if(start_bit != 0)
			p++;
		int number_of_bits = p * 8;
		if(start_bit != 0)
			number_of_bits -= 8 - start_bit;
		return(number_of_bits);
	}

	/**
	 * Uses a rank table of most popular to least popular to turn unary strings into
	 * integers.
	 * 
	 * @param src   the input integers
	 * @param table the rank table
	 * @param dst   the integer array containing the result
	 * @return int the length of the dst.array
	 */
	public static int unpackStrings(byte src[], int table[], int dst[])
	{
		for(int i = 0; i < dst.length; i++)
			dst[i] = 0;

		int size = dst.length;
		int number_of_different_values = table.length;
		int number_unpacked = 0;

		int[] inverse_table = new int[number_of_different_values];
		for(int i = 0; i < number_of_different_values; i++)
		{
			int j = table[i];
			inverse_table[j] = i;
		}

		int length = 1;
		int src_byte = 0;
		int dst_byte = 0;
		byte mask = 0x01;
		byte bit = 0;

		while (dst_byte < size)
		{
			byte non_zero = (byte) (src[src_byte] & (byte) (mask << bit));
			if(non_zero != 0)
				length++;
			else
			{
				int k = length - 1;
				dst[dst_byte++] = inverse_table[k];
				number_unpacked++;
				length = 1;
			}
			bit++;
			if(bit == 8)
			{
				bit = 0;
				src_byte++;
			}
		}
		return(number_unpacked);
	}

	// These packing/unpacking functions use the maximum length
	// code to dispense with a stop bit in the longest string.
	// Not very significant until we are looking at binary and
	// low resolution images.

	/**
	 * Uses a rank table of most popular to least popular to turn byte
	 * representations into unary strings, and then pack pack them into a byte
	 * array. It assumes dst is large enough to contain the result, and returns the
	 * length of the bit string.
	 * 
	 * @param src   the input bytes
	 * @param table the rank table
	 * @return byte array containing the bit string
	 */
	public static int packStrings2(byte src[], int table[], byte dst[])
	{
		int size = src.length;
		int number_of_values = table.length;

		int maximum_length = number_of_values - 1;

		int[] mask = new int[8];

		mask[0] = 1;
		mask[1] = 3;
		mask[2] = 7;
		mask[3] = 15;
		mask[4] = 31;
		mask[5] = 63;
		mask[6] = 127;
		mask[7] = 255;

		int start_bit = 0;
		int stop_bit = 0;
		int p = 0;
		dst[p] = 0;

		for(int i = 0; i < size; i++)
		{
			int j = src[i];
			int k = table[j];

			if(k == 0)
			{
				start_bit++;
				if(start_bit == 8)
				{
					dst[++p] = 0;
					start_bit = 0;
				}
			} else
			{
				stop_bit = (start_bit + k + 1) % 8;
				if(k == maximum_length)
				{
					stop_bit--;
					if(stop_bit < 0)
						stop_bit = 7;
				}

				if(k <= 7)
				{
					dst[p] |= (byte) (mask[k - 1] << start_bit);

					if(stop_bit <= start_bit)
					{
						dst[++p] = 0;
						if(stop_bit != 0)
						{
							dst[p] |= (byte) (mask[k - 1] >> (8 - start_bit));
						}
					}
				}
				else if(k > 7)
				{
					dst[p] |= (byte) (mask[7] << start_bit);
					int m = (k - 8) / 8;
					for(int n = 0; n < m; n++)
						dst[++p] = (byte) (mask[7]);
					dst[++p] = 0;

					if(start_bit != 0)
						dst[p] |= (byte) (mask[7] >> (8 - start_bit));

					if(k % 8 != 0)
					{
						m = k % 8 - 1;

						dst[p] |= (byte) (mask[m] << start_bit);

						if(stop_bit <= start_bit)
						{
							dst[++p] = 0;
							if(stop_bit != 0)
							{
								dst[p] |= (byte) (mask[m] >> (8 - start_bit));
							}
						}
					}
					// If this is the maximum_length index and it's a multiple of 8,
					// then we already incremented the index and then reset the stop bit.
					// Don't want to do it twice. Very tricky bug.
					else if(stop_bit <= start_bit && k != maximum_length)
						dst[++p] = 0;
				}
				start_bit = stop_bit;
			}
		}

		if(start_bit != 0)
			p++;
		int number_of_bits = p * 8;
		if(start_bit != 0)
			number_of_bits -= 8 - start_bit;
		return(number_of_bits);
	}

	/**
	 * Uses a rank table of most popular to least popular to turn integer
	 * representations into unary strings, and then pack pack them into a byte
	 * array. It assumes dst is large enough to contain the result, and returns the
	 * length of the bit string.
	 * 
	 * @param src   the input integers
	 * @param table the rank table
	 * @return byte array containing the bit string
	 */
	public static int packStrings2(int src[], int table[], byte dst[])
	{
		int size = src.length;
		int number_of_values = table.length;

		int maximum_length = number_of_values - 1;

		int[] mask = new int[8];

		mask[0] = 1;
		mask[1] = 3;
		mask[2] = 7;
		mask[3] = 15;
		mask[4] = 31;
		mask[5] = 63;
		mask[6] = 127;
		mask[7] = 255;

		int start_bit = 0;
		int stop_bit = 0;
		int p = 0;
		dst[p] = 0;

		for(int i = 0; i < size; i++)
		{
			int j = src[i];
			int k = table[j];

			if(k == 0)
			{
				start_bit++;
				if(start_bit == 8)
				{
					dst[++p] = 0;
					start_bit = 0;
				}
			} else
			{
				stop_bit = (start_bit + k + 1) % 8;
				if(k == maximum_length)
				{
					stop_bit--;
					if(stop_bit < 0)
						stop_bit = 7;
				}

				if(k <= 7)
				{
					dst[p] |= (byte) (mask[k - 1] << start_bit);

					if(stop_bit <= start_bit)
					{
						dst[++p] = 0;
						if(stop_bit != 0)
						{
							dst[p] |= (byte) (mask[k - 1] >> (8 - start_bit));
						}
					}
				}
				else if(k > 7)
				{
					dst[p] |= (byte) (mask[7] << start_bit);
					int m = (k - 8) / 8;
					for(int n = 0; n < m; n++)
						dst[++p] = (byte) (mask[7]);
					dst[++p] = 0;

					if(start_bit != 0)
						dst[p] |= (byte) (mask[7] >> (8 - start_bit));

					if(k % 8 != 0)
					{
						m = k % 8 - 1;

						dst[p] |= (byte) (mask[m] << start_bit);

						if(stop_bit <= start_bit)
						{
							dst[++p] = 0;
							if(stop_bit != 0)
							{
								dst[p] |= (byte) (mask[m] >> (8 - start_bit));
							}
						}
					}
					// If this is the maximum_length index and it's a multiple of 8,
					// then we already incremented the index and then reset the stop bit.
					// Don't want to do it twice. Very tricky bug.
					else if(stop_bit <= start_bit && k != maximum_length)
						dst[++p] = 0;
				}
				start_bit = stop_bit;
			}
		}

		if(start_bit != 0)
			p++;
		int number_of_bits = p * 8;
		if(start_bit != 0)
			number_of_bits -= 8 - start_bit;
		return(number_of_bits);
	}

	/**
	 * Uses a rank table of most popular to least popular to turn unary strings into
	 * bytes.
	 * 
	 * @param src   the input bytes
	 * @param table the rank table
	 * @param dst   the byte array containing the result
	 * @return int the length of the dst.array
	 */
	public static int unpackStrings2(byte src[], int table[], byte dst[])
	{
		int size = dst.length;
		int number_of_values = table.length;
		int maximum_length = number_of_values - 1;
		int length = 1;
		int src_byte = 0;
		int dst_byte = 0;
		int number_unpacked = 0;
		int mask = 1;
		int bit = 0;

		byte[] inverse_table = new byte[number_of_values];
		for(int i = 0; i < number_of_values; i++)
		{
			int j = table[i];
			inverse_table[j] = (byte) i;
		}

		try
		{
			while (dst_byte < size)
			{
				int non_zero = src[src_byte] & (mask << bit);
				if(non_zero != 0 && length < maximum_length)
					length++;
				else if(non_zero == 0)
				{
					int k = length - 1;
					dst[dst_byte++] = inverse_table[k];
					length = 1;
					number_unpacked++;

				} 
				else if(length == maximum_length)
				{
					int k = length;
					dst[dst_byte++] = inverse_table[k];
					number_unpacked++;
					length = 1;
				}
				bit++;
				if(bit == 8)
				{
					bit = 0;
					src_byte++;
				}
			}
		} 
		catch (Exception e)
		{
			System.out.println(e.toString());
			System.out.println("Exiting unpackStrings2 with an exception.");
		}
		return(number_unpacked);
	}

	/**
	 * Uses a rank table of most popular to least popular to turn unary strings into
	 * integers.
	 * 
	 * @param src   the input bytes
	 * @param table the rank table
	 * @param dst   the integer array containing the result
	 * @return int the length of the dst.array
	 */
	public static int unpackStrings2(byte src[], int table[], int dst[])
	{
		int size = dst.length;
		int number_of_values = table.length;
		int maximum_length = number_of_values - 1;
		int length = 1;
		int src_byte = 0;
		int dst_byte = 0;
		int number_unpacked = 0;
		int mask = 1;
		int bit = 0;

		int[] inverse_table = new int[number_of_values];
		for(int i = 0; i < number_of_values; i++)
		{
			int j = table[i];
			inverse_table[j] = i;
		}

		try
		{
			while (dst_byte < size)
			{
				int non_zero = src[src_byte] & (mask << bit);
				if(non_zero != 0 && length < maximum_length)
					length++;
				else if(non_zero == 0)
				{
					int k = length - 1;
					dst[dst_byte++] = inverse_table[k];
					length = 1;
					number_unpacked++;

				}
                else if(length == maximum_length)
				{
					int k = length;
					dst[dst_byte++] = inverse_table[k];
					number_unpacked++;
					length = 1;
				}
				bit++;
				if(bit == 8)
				{
					bit = 0;
					src_byte++;
				}
			}
		} catch (Exception e)
		{
			System.out.println(e.toString());
			System.out.println("Exiting unpackStrings2 with an exception.");
		}
		return(number_unpacked);
	}

	// Stop bit methods.
	// Zero bits correspond to stop bits in our implementation.
	/**
	 * Does a bitwise substitution that will expand or contract the original bit
	 * string.
	 * 
	 * @param src  input bytes
	 * @param size original bit length
	 * @param dst  byte array containing the result
	 * @return transformed bit length
	 */
	public static int [] compressZeroBits(byte src[], int size, byte dst[])
	{
		int [] result = new int[2];
		for(int i = 0; i < dst.length; i++)
			dst[i] = 0;
		int current_byte = 0;
		int current_bit = 0;
		byte mask = 0x01;
		dst[0] = 0;

		int i = 0;
		int j = 0;
		int k = 0;

		try
		{
			for(i = 0; i < size; i++)
			{
				if((src[k] & (mask << j)) == 0 && i < size - 1)
				{
					i++;
					j++;

					if(j == 8)
					{
						j = 0;
						k++;
					}
					if((src[k] & (mask << j)) == 0)
					{
						// Current bit is a 0.
						current_bit++;
						if(current_bit == 8)
						{
							current_byte++;
							// current_bit = dst[current_byte] = 0;
							current_bit = 0;
						}
					}
		            else
					{
						// Current bit is a 1.
						dst[current_byte] |= (byte) mask << current_bit;
						current_bit++;
						if(current_bit == 8)
						{
							current_byte++;
							current_bit = dst[current_byte] = 0;
						}
						dst[current_byte] |= (byte) mask << current_bit;
						current_bit++;
						if(current_bit == 8)
						{
							current_byte++;
							current_bit = 0;
						}
					}
				}
				else if((src[k] & (mask << j)) == 0 && (i == size - 1))
				{
					// We're at the end of the string and we have an odd 0.
					// Put a 1 down to signal that there is an odd 0.
					// This works for a single recursion but can produce trailing
					// zero bits in the other recursive cases.
					dst[current_byte] |= (byte) mask << current_bit;
					current_bit++;
					if(current_bit == 8)
					{
						current_byte++;
						current_bit = 0;
					}
					result[1] = 1;
				}
				else
				{
					// Current first bit is a 1.
					dst[current_byte] |= (byte) mask << current_bit;
					current_bit++;
					if(current_bit == 8)
					{
						current_byte++;
						current_bit = 0;
					}
					current_bit++;
					if(current_bit == 8)
					{
						current_byte++;
						current_bit = 0;
					}
				}
				j++;
				if(j == 8)
				{
					j = 0;
					k++;
				}
			}
		} 
		catch (Exception e)
		{
			System.out.println(e.toString());
			System.out.println("Exiting compressZeroBits with an exception.");
		}

		int number_of_bits = current_byte * 8;
		number_of_bits += current_bit;
        
		result[0] = number_of_bits;
		
		return(result);
	}

	/**
	 * Does the inverse bitwise substitution that will expand or contract the
	 * original bit string.
	 * 
	 * @param src  input bytes
	 * @param size original bit length
	 * @param dst  byte array containing the result
	 * @return transformed bit length
	 */
	public static int decompressZeroBits(byte src[], int size, byte dst[])
	{
		// This is necessary because dst can be used multiple times,
		// so we can't count on it being initialized with zeros.
		for(int i = 0; i < dst.length; i++)
			dst[i] = 0;
		int current_byte = 0;
		int current_bit = 0;
		byte mask = 0x01;
		dst[0] = 0;

		int i = 0;
		int j = 0;
		int k = 0;

		try
		{
			for(i = 0; i < size; i++)
			{
				if((src[k] & (mask << j)) != 0 && i < size - 1)
				{
					i++;
					j++;
					if(j == 8)
					{
						j = 0;
						k++;
					}
					if((src[k] & (mask << j)) != 0)
					{
						current_bit++;
						if(current_bit == 8)
						{
							current_byte++;
							current_bit = 0;
						}
						dst[current_byte] |= (byte) mask << current_bit;
						current_bit++;
						if(current_bit == 8)
						{
							current_byte++;
							current_bit = 0;
						}
					}
				    else
					{
						dst[current_byte] |= (byte) mask << current_bit;
						current_bit++;
						if(current_bit == 8)
						{
							current_byte++;
							current_bit = 0;
						}
					}
				} 
				else if(((src[k] & (mask << j)) != 0) && i == size - 1)
				{
					// Append an odd 0.
					current_bit++;
					if(current_bit == 8)
					{
						current_byte++;
						current_bit = 0;
					}
				} 
				else if((src[k] & (mask << j)) == 0)
				{
					current_bit++;
					if(current_bit == 8)
					{
						current_byte++;
						if(current_byte < dst.length)
							current_bit = 0;
					}
					current_bit++;
					if(current_bit == 8)
					{
						current_byte++;
						if(current_byte < dst.length)
							current_bit = 0;
					}
				}

				j++;
				if(j == 8)
				{
					j = 0;
					k++;
				}
			}
		} 
		catch (Exception e)
		{
			System.out.println(e.toString());
			System.out.println("Input size was " + size);
			System.out.println("Exception at index " + i);
			System.out.println("Exiting decompressZeroBits with an exception.");
		}

		int number_of_bits = current_byte * 8;
		number_of_bits += current_bit;
		return(number_of_bits);
	}
	
	/**
	 * This applies a bitwise substitution iteratively that will expand or contract
	 * a bit string. It does not expect information in the trailing byte,
	 * which can be useful.
	 * 
	 * @param src  input bytes
	 * @param size original bit length
	 * @return byte array containing the result
	 */
	public static byte[] compressZeroStrings(byte[] src, int bit_length)
	{
		try
		{
			int amount = getCompressionAmount(src, bit_length, 0);
			if(amount > 0)
			{
				int byte_length = bit_length / 8;
				if(bit_length % 8 != 0)
					byte_length++;
				byte[] dst = new byte[byte_length + 1];
				System.arraycopy(src, 0, dst, 0, byte_length);

				byte extra_bits = (byte) (bit_length % 8);
				if(extra_bits != 0)
					extra_bits = (byte) (8 - extra_bits);
				extra_bits <<= 5;

				dst[byte_length] = extra_bits;
				return dst;
			} 
			else
			{
				byte[] buffer1 = new byte[src.length];
				byte[] buffer2 = new byte[src.length];
				
				int [] result = compressZeroBits(src, bit_length, buffer1);
				int compressed_length = result[0];
				amount = getCompressionAmount(buffer1, compressed_length, 0);
				//if(amount >= 0 || result[1] != 0)
				if(amount >= 0)
				{
					// 1 iteration
					int byte_length = compressed_length / 8;
					if(compressed_length % 8 != 0)
						byte_length++;
					byte extra_bits = (byte) (compressed_length % 8);
					if(extra_bits != 0)
						extra_bits = (byte) (8 - extra_bits);
					extra_bits <<= 5;

					byte[] dst = new byte[byte_length + 1];
					System.arraycopy(buffer1, 0, dst, 0, byte_length);
					dst[byte_length] = 1;
					dst[byte_length] |= extra_bits;
					return dst;
				} 
				else
				{
					int iterations = 1;
					//while (amount < 0 && iterations < 15 && result[1] == 0)
					while (amount < 0 && iterations < 15)
					{
						int previous_length = compressed_length;
						if(iterations % 2 == 1)
						{
							result = compressZeroBits(buffer1, previous_length, buffer2);
							compressed_length = result[0];
							iterations++;
							amount = getCompressionAmount(buffer2, compressed_length, 0);
						} 
						else
						{
							result = compressZeroBits(buffer2, previous_length, buffer1);
							compressed_length = result[0];
							iterations++;
							amount = getCompressionAmount(buffer1, compressed_length, 0);
						}
					}

					int byte_length = compressed_length / 8;
					if(compressed_length % 8 != 0)
						byte_length++;
					byte[] dst = new byte[byte_length + 1];
					if(iterations % 2 == 0)
					{
						System.arraycopy(buffer2, 0, dst, 0, byte_length);
					} 
					else
					{
						System.arraycopy(buffer1, 0, dst, 0, byte_length);
					}
					dst[byte_length] = (byte) iterations;
					byte extra_bits = (byte) (compressed_length % 8);
					if(extra_bits != 0)
						extra_bits = (byte) (8 - extra_bits);
					extra_bits <<= 5;
					dst[byte_length] |= extra_bits;
					return dst;
				}
			}
		} 
		catch (Exception e)
		{
			System.out.println(e.toString());
			System.out.println("Exiting compressZeroStrings with an exception.");
			byte[] dst = new byte[1];
			return dst;
		}
	}

	/**
	 * This applies a bitwise substitution iteratively that will expand or contract
	 * a bit string.
	 * It requires that the bit string length is contained in the trailing byte.
	 * 
	 * @param src  input bytes
	 * @return byte array containing the result with a trailing data byte.
	 */
	public static byte[] compressZeroStrings(byte[] src)
	{
		ArrayList bitlength_list   = new ArrayList();
		ArrayList zero_ratio_list  = new ArrayList();
		ArrayList zero_amount_list = new ArrayList();
		ArrayList one_amount_list  = new ArrayList();
		
		int    bit_length  = getBitlength(src);
		double zero_ratio  = getZeroRatio(src, bit_length);
		int    zero_amount = getCompressionAmount(src, bit_length, 0);
		int    one_amount  = getCompressionAmount(src, bit_length, 1);
	    
		bitlength_list.add(bit_length);
		zero_ratio_list.add(zero_ratio);
		zero_amount_list.add(zero_amount);
		one_amount_list.add(one_amount);
		
		if(zero_amount > 0)
		{
			// We only print a message that the string didn't compress if the inverse transform would produce compression.
			if(one_amount < 0)
			{
			   int iterations = getIterations(src);
			   System.out.println("String with ratio " + String.format("%.4f", zero_ratio) + " and length " + bit_length + " and " + iterations + " previous iterations did not compress.");
			   System.out.println("The amount expected from the zero transform is " + zero_amount);
			   System.out.println("The amount expected from the one transform is " + one_amount);
			   System.out.println();
			}
			
			byte [] dst    = new byte[src.length];
			System.arraycopy(src, 0, dst, 0, src.length);
			return dst;
		}
		else
		{
			byte[] buffer1 = new byte[src.length];
			byte[] buffer2 = new byte[src.length];
			
			int [] result            = compressZeroBits(src, bit_length, buffer1);
			int    compressed_length = result[0];
			
			if(compressed_length - bit_length != zero_amount)
			{
				System.out.println("Actual amount " + (compressed_length - bit_length) + " was not equal to predicted amount " + zero_amount + " after initial iteration.");
				System.out.println();
			}
			
			zero_ratio  = getZeroRatio(buffer1, compressed_length);
			zero_amount = getCompressionAmount(buffer1, compressed_length, 0);
			one_amount  = getCompressionAmount(buffer1, compressed_length, 1);
		    
			bitlength_list.add(compressed_length);
			zero_ratio_list.add(zero_ratio);
			zero_amount_list.add(zero_amount);
			one_amount_list.add(one_amount);
			
			// This does not appear necessary, but might be useful.
			//if(zero_amount >= 0 || result[1] == 1)
			if(zero_amount > 0)
			{
				boolean isAnomalous = false;
			    if(one_amount < 0)
			    {
			      	/*
			    	    System.out.println("Anomalous 0 string behavior:");
				    int size = zero_ratio_list.size();
				    for(int i = 0; i < size; i++)
				    {
				    	int current_length      = (int)bitlength_list.get(i);
				    	double current_ratio    = (double)zero_ratio_list.get(i);
				    	int current_zero_amount = (int)zero_amount_list.get(i);
				    	int current_one_amount  = (int)one_amount_list.get(i);
				    	System.out.println(current_length + "\t" + String.format("%.2f", current_ratio) + "\t" + current_zero_amount + "\t" + current_one_amount);
				    }
				    int increment = bit_length / 10;
				    int offset    = 0;
				    int length    = increment;
				    System.out.print("Segmented zero ratios: ");
				    for(int i = 0; i < 10; i++)
				    {
				    	double current_ratio = getZeroRatio(src, offset, length);
				    	offset += increment;
				        System.out.print(String.format("%.2f", current_ratio) + " ");
				    }
				    
				    System.out.println();
				    isAnomalous = true;
				    */
			    }
				
				int byte_length = compressed_length / 8;
				if(compressed_length % 8 != 0)
					byte_length++;
				byte extra_bits = (byte) (compressed_length % 8);
				if(extra_bits != 0)
					extra_bits = (byte) (8 - extra_bits);
				extra_bits <<= 5;

				byte[] dst = new byte[byte_length + 1];
				System.arraycopy(buffer1, 0, dst, 0, byte_length);
				dst[byte_length] = 1;
				dst[byte_length] |= extra_bits;
				
				if(isAnomalous)
				{
					if(isAnomalous)
					{
						int increment = compressed_length / 10;
					    int offset    = 0;
					    int length    = increment;
					    
					    for(int i = 0; i < 10; i++)
					    {
					    	double current_ratio = getZeroRatio(dst, offset, length);
					    	offset += increment;
					        //System.out.print(String.format("%.2f", current_ratio) + " ");
					    }
					    
					    //System.out.println();	
					    //System.out.println();
					}
				}
				return dst;
			} 
			else
			{
				int iterations = 1;
				//while (zero_amount < 0 && iterations < 15 && result[1] == 0)
				while(zero_amount < 0 && iterations < 15)
				{
					int previous_length = compressed_length;
					if(iterations % 2 == 1)
					{
						result            = compressZeroBits(buffer1, previous_length, buffer2);
						compressed_length = result[0];
						
						iterations++;
						if(compressed_length - previous_length != zero_amount)
						{
							System.out.println("Actual amount " + (compressed_length - previous_length) + " was not equal to predicted amount " + zero_amount + " after  iteration " + iterations);
							System.out.println();
						}
						
						zero_ratio        = getZeroRatio(buffer2, compressed_length);
						zero_amount       = getCompressionAmount(buffer2, compressed_length, 0);
						one_amount        = getCompressionAmount(buffer2, compressed_length, 1);
						bitlength_list.add(compressed_length);
						zero_ratio_list.add(zero_ratio);
						zero_amount_list.add(zero_amount);
						one_amount_list.add(one_amount);
					} 
					else
					{
						result = compressZeroBits(buffer2, previous_length, buffer1);
						compressed_length = result[0];
						
						iterations++;
						if(compressed_length - previous_length != zero_amount)
						{
							System.out.println("Actual amount " + (compressed_length - previous_length) + " was not equal to predicted amount " + zero_amount + " after  iteration " + iterations);
							System.out.println();
						}
						
						zero_ratio        = getZeroRatio(buffer1, compressed_length);
						zero_amount       = getCompressionAmount(buffer1, compressed_length, 0);
						one_amount        = getCompressionAmount(buffer1, compressed_length, 1);
						bitlength_list.add(compressed_length);
						zero_ratio_list.add(zero_ratio);
						zero_amount_list.add(zero_amount);
						
						one_amount_list.add(one_amount);
					}
				}
				
				boolean isAnomalous = false;
				if(one_amount < 0)
			    {
					// Difficult to understand why this happens.
					/*
					System.out.println("Anomalous 0 string behavior:");
				    int size = zero_ratio_list.size();
				    for(int i = 0; i < size; i++)
				    {
				    	int current_length      = (int)bitlength_list.get(i);
				    	double current_ratio    = (double)zero_ratio_list.get(i);
				    	int current_zero_amount = (int)zero_amount_list.get(i);
				    	int current_one_amount  = (int)one_amount_list.get(i);
				    	System.out.println(current_length + "\t" + String.format("%.2f", current_ratio) + "\t" + current_zero_amount + "\t" + current_one_amount);
				    }
				    
				    System.out.print("Segmented zero ratios: ");
				    int increment = bit_length / 10;
				    int offset    = 0;
				    int length    = increment;
				    
				    for(int i = 0; i < 10; i++)
				    {
				    	double current_ratio = getZeroRatio(src, offset, length);
				    	offset += increment;
				        System.out.print(String.format("%.2f", current_ratio) + " ");
				    }
				    
				    System.out.println();
				    isAnomalous = true;
				    */
			    }
				
				int byte_length = compressed_length / 8;
				if(compressed_length % 8 != 0)
					byte_length++;
				byte[] dst = new byte[byte_length + 1];
				if(iterations % 2 == 0)
					System.arraycopy(buffer2, 0, dst, 0, byte_length);
				else
					System.arraycopy(buffer1, 0, dst, 0, byte_length);
				dst[byte_length] = (byte) iterations;
				byte extra_bits = (byte) (compressed_length % 8);
				if(extra_bits != 0)
					extra_bits = (byte) (8 - extra_bits);
				extra_bits <<= 5;
				dst[byte_length] |= extra_bits;
				
				// For now we'll ignore this.
				// It happens fairly rarely, but there might be a way to make it happen more often.
				// It could be a way to tune the zero ratio.
				/*
				if(isAnomalous)
				{
					int increment = compressed_length / 10;
				    int offset    = 0;
				    int length    = increment;
				    
				    for(int i = 0; i < 10; i++)
				    {
				    	double current_ratio = getZeroRatio(dst, offset, length);
				    	offset += increment;
				        System.out.print(String.format("%.2f", current_ratio) + " ");
				    }
				    
				    System.out.println();	
				    System.out.println();
				}
				*/
			
				return dst;
			}
		}
	}
	
	
	// This function expects to find the number of iterations in the final
	// byte, as well as the number of odd bits determined by the bit length
	// subtracted from the byte length.
	/**
	 * This applies a bitwise substitution iteratively that will expand or contract
	 * a bit string.
	 * It requires that the bit string length is contained in the trailing byte.
	 * 
	 * @param src  input bytes
	 * @return byte array containing the result with a trailing data byte.
	 */
	public static byte[] decompressZeroStrings(byte[] src)
	{
		int last_byte = src.length - 1;
		int iterations = src[last_byte] & (byte) 31;
		byte extra_bits = (byte) (src[last_byte] >> 5);
		extra_bits &= 7;
		int bit_length = (src.length - 1) * 8 - extra_bits;

		// If it's not a zero type string, we'll still process it.
		// This will compress a one type string.
		if(iterations > 16)
		{
			System.out.println("Not zero type string.");
			iterations -= 16;
		}

		// If it was not compressed, we copy src to dst.
		if(iterations == 0)
		{
			byte[] dst = new byte[src.length];
			System.arraycopy(src, 0, dst, 0, src.length);
			return dst;
		}

		if(iterations == 1)
		{
			int byte_length = bit_length / 8;
			if(bit_length % 8 != 0)
				byte_length++;
			byte_length *= 2;
			byte[] buffer = new byte[byte_length];
			int uncompressed_length = decompressZeroBits(src, bit_length, buffer);
			byte_length = uncompressed_length / 8;
			if(uncompressed_length % 8 != 0)
				byte_length++;
			byte[] dst = new byte[byte_length + 1];
			System.arraycopy(buffer, 0, dst, 0, byte_length);
			last_byte = dst.length - 1;
			extra_bits = (byte) (uncompressed_length % 8);
			if(extra_bits != 0)
				extra_bits = (byte) (8 - extra_bits);
			extra_bits <<= 5;
			dst[last_byte] = extra_bits;

			return dst;
		} 
		else if(iterations % 2 == 0)
		{
			int byte_length = bit_length / 8;
			if(bit_length % 8 != 0)
				byte_length++;
			double factor = Math.pow(2, iterations);
			byte_length *= factor;

			byte[] buffer1 = new byte[byte_length];
			byte[] buffer2 = new byte[byte_length];

			int uncompressed_length = decompressZeroBits(src, bit_length, buffer1);
			iterations--;
			while (iterations > 0)
			{
				int previous_length = uncompressed_length;
				if(iterations % 2 == 1)
				{
					uncompressed_length = decompressZeroBits(buffer1, previous_length, buffer2);
				} 
				else
				{
					uncompressed_length = decompressZeroBits(buffer2, previous_length, buffer1);
				}
				iterations--;
			}
			byte_length = uncompressed_length / 8;
			if(uncompressed_length % 8 != 0)
				byte_length++;

			byte[] dst = new byte[byte_length + 1];
			System.arraycopy(buffer2, 0, dst, 0, byte_length);
			last_byte = dst.length - 1;
			extra_bits = (byte) (uncompressed_length % 8);
			if(extra_bits != 0)
				extra_bits = (byte) (8 - extra_bits);
			extra_bits <<= 5;
			dst[last_byte] = extra_bits;
			return dst;
		} 
		else
		{
			int byte_length = bit_length / 8;
			if(bit_length % 8 != 0)
				byte_length++;
			double factor = Math.pow(2, iterations);
			byte_length *= factor;

			byte[] buffer1 = new byte[byte_length];
			byte[] buffer2 = new byte[byte_length];

			int uncompressed_length = decompressZeroBits(src, bit_length, buffer1);
			iterations--;
			while (iterations > 0)
			{
				int previous_length = uncompressed_length;
				if(iterations % 2 == 0)
				{
					uncompressed_length = decompressZeroBits(buffer1, previous_length, buffer2);
				} else
				{
					uncompressed_length = decompressZeroBits(buffer2, previous_length, buffer1);
				}
				iterations--;
			}
			byte_length = uncompressed_length / 8;
			if(uncompressed_length % 8 != 0)
				byte_length++;

			byte[] dst = new byte[byte_length + 1];

			System.arraycopy(buffer1, 0, dst, 0, byte_length);
			last_byte = dst.length - 1;
			extra_bits = (byte) (uncompressed_length % 8);
			if(extra_bits != 0)
				extra_bits = (byte) (8 - extra_bits);
			extra_bits <<= 5;
			dst[last_byte] = extra_bits;

			return dst;
		}
	}
	
	// One bit/one string functions.
	// One bits correspond to run bits in this implementation.
	public static int [] compressOneBits(byte src[], int size, byte dst[])
	{
		int [] result = new int[2];
		for(int i = 0; i < dst.length; i++)
			dst[i] = 0;
		int current_byte = 0;
		int current_bit = 0;
		byte mask = 0x01;
		dst[0] = 0;

		int i = 0;
		int j = 0;
		int k = 0;
		for(i = 0; i < size; i++)
		{
			// Current bit is a 1. Check the next bit.
			if((src[k] & (mask << j)) != 0 && i < size - 1)
			{
				i++;
				j++;
				if(j == 8)
				{
					j = 0;
					k++;
				}
				if((src[k] & (mask << j)) != 0)
				{
					// Current bit is a 1.
					// We parsed two 1 bits in a row, put down a 1 bit.
					dst[current_byte] |= (byte) mask << current_bit;

					// Move to the start of the next code.
					current_bit++;
					if(current_bit == 8)
					{
						current_byte++;
						current_bit = 0;
					}
				} 
				else
				{
					// Current bit is a 0.
					// We want 10 -> 01.
					// Increment and leave a 0 bit.
					current_bit++;
					if(current_bit == 8)
					{
						current_byte++;
						current_bit = 0;
					}

					// Put down a 1 bit.
					dst[current_byte] |= (byte) mask << current_bit;

					// Move to the start of the next code.
					current_bit++;
					if(current_bit == 8)
					{
						current_byte++;
						current_bit = 0;
					}
				}
			} 
			else if((src[k] & (mask << j)) != 0 && (i == size - 1)) // We're at the end of the string and we have an odd 1.															// odd 1.
			{
				// Put a 0 down to signal that there is an odd 1.
				// This works for a single recursion but might fail in the other recursive cases.
				// It actually fails more rarely than we expect.
				current_bit++;
				if(current_bit == 8)
				{
					current_byte++;
					current_bit = 0;
				}
				//This is a flag to let us know there might be a problem later on.
				result[1] = 1;
			} 
			else
			{
				// Current first bit is a 0.
				// 0->00
				current_bit++;
				if(current_bit == 8)
				{
					current_byte++;
					current_bit = 0;
				}
				current_bit++;
				if(current_bit == 8)
				{
					current_byte++;
					current_bit = 0;
				}
			}
			j++;
			if(j == 8)
			{
				j = 0;
				k++;
			}
		}
		int number_of_bits = current_byte * 8;
		number_of_bits += current_bit;
		result[0] = number_of_bits;

		return(result);
	}

	public static int decompressOneBits(byte src[], int size, byte dst[])
	{
		// This is necessary because dst can be used multiple times
		// in our implementation, so we can't count on it being initialized 
		// with zeros.
		for(int i = 0; i < dst.length; i++)
			dst[i] = 0;

		int current_byte = 0;
		int current_bit = 0;
		byte mask = 0x01;
		dst[0] = 0;

		int i = 0;
		int j = 0;
		int k = 0;

		for(i = 0; i < size; i++)
		{
			try
			{
				// First bit is a 0, get next bit.
				if(((src[k] & (mask << j)) == 0) && i < size - 1)
				{
					i++;
					j++;
					if(j == 8)
					{
						j = 0;
						k++;
					}
					if((src[k] & (mask << j)) == 0)
					{
						// Another zero bit. Leave a zero bit in the output.
						current_bit++;
						if(current_bit == 8)
						{
							current_byte++;
							current_bit = 0;
						}
					} 
					else
					{
						// We have 01->10.
						dst[current_byte] |= (byte) mask << current_bit;
						current_bit++;
						if(current_bit == 8)
						{
							current_byte++;
							current_bit = 0;
						}
						current_bit++;
						if(current_bit == 8)
						{
							current_byte++;
							current_bit = 0;
						}
					}
				} 
				else if(((src[k] & (mask << j)) == 0) && i == size - 1)
				{
					// Append an odd 1.
					dst[current_byte] |= (byte) mask << current_bit;
					current_bit++;
					if(current_bit == 8)
					{
						current_byte++;
						current_bit = 0;
					}
				} 
				else if((src[k] & (mask << j)) != 0)
				{
					// We have a 1 bit, put down two 1 bits in the output.
					dst[current_byte] |= (byte) mask << current_bit;
					current_bit++;
					if(current_bit == 8)
					{
						current_byte++;
						if(current_byte < dst.length)
							current_bit = 0;
					}
					dst[current_byte] |= (byte) mask << current_bit;
					current_bit++;
					if(current_bit == 8)
					{
						current_byte++;
						if(current_byte < dst.length)
							current_bit = 0;
					}
				}
				j++;
				if(j == 8)
				{
					j = 0;
					k++;
				}
			} 
			catch (Exception e)
			{
				System.out.println(e.toString());
				System.out.println("Size of input was " + size);
				System.out.println("Exception on index " + i);
				System.out.println("Exiting decompressOneBits.");
			}
		}

		int number_of_bits = current_byte * 8;
		number_of_bits += current_bit;
		return(number_of_bits);
	}
	
	public static byte[] compressOneStrings(byte[] src, int bit_length)
	{
		byte[] dst = new byte[1];
		try
		{
			int amount = getCompressionAmount(src, bit_length, 1);
			if(amount > 0)
			{
				// 0 iteration
				int byte_length = bit_length / 8;
				if(bit_length % 8 != 0)
					byte_length++;
				dst = new byte[byte_length + 1];

				System.arraycopy(src, 0, dst, 0, byte_length);

				byte extra_bits = (byte) (bit_length % 8);
				if(extra_bits != 0)
					extra_bits = (byte) (8 - extra_bits);
				extra_bits <<= 5;

				dst[byte_length] = 16;

				dst[byte_length] |= extra_bits;

				return dst;
			} 
			else
			{
				byte[] buffer1 = new byte[src.length];
				byte[] buffer2 = new byte[src.length];
				int [] result = compressOneBits(src, bit_length, buffer1);
				int compressed_length = result[0];
				amount = getCompressionAmount(buffer1, compressed_length, 1);
				//if(amount >= 0 || result[1] != 0)
				if(amount >= 0)
				{
					// 1 iteration
					int byte_length = compressed_length / 8;
					if(compressed_length % 8 != 0)
						byte_length++;
					byte extra_bits = (byte) (compressed_length % 8);
					if(extra_bits != 0)
						extra_bits = (byte) (8 - extra_bits);
					extra_bits <<= 5;

					dst = new byte[byte_length + 1];
					System.arraycopy(buffer1, 0, dst, 0, byte_length);

					// Add 16 to indicate string type.
					dst[byte_length] = 1 + 16;
					dst[byte_length] |= extra_bits;
					amount = getCompressionAmount(dst, compressed_length, 1);
					if(amount < 0)
					{
						System.out.println("String can be compressed further 1.");
					}
					return dst;
				} 
				else
				{
					int iterations = 1;
					//while (amount < 0 && iterations < 15 && result[1] == 0)
					while (amount < 0 && iterations < 15)
					{
						int previous_length = compressed_length;
						if(iterations % 2 == 1)
						{
                            result = compressOneBits(buffer1, previous_length, buffer2);
							compressed_length = result[0];
							iterations++;
							amount = getCompressionAmount(buffer2, compressed_length, 1);
						} 
						else
						{
                            result = compressOneBits(buffer2, previous_length, buffer1);
							compressed_length = result[0];
							iterations++;
							amount = getCompressionAmount(buffer1, compressed_length, 1);
						}
					}

					int byte_length = compressed_length / 8;
					if(compressed_length % 8 != 0)
						byte_length++;
					dst = new byte[byte_length + 1];
					if(iterations % 2 == 0)
						System.arraycopy(buffer2, 0, dst, 0, byte_length);
					else
						System.arraycopy(buffer1, 0, dst, 0, byte_length);

					dst[byte_length] = (byte) (iterations + 16);
					byte extra_bits = (byte) (compressed_length % 8);
					if(extra_bits != 0)
						extra_bits = (byte) (8 - extra_bits);
					extra_bits <<= 5;
					dst[byte_length] |= extra_bits;
					amount = getCompressionAmount(dst, compressed_length, 1);
					if(amount < 0)
					{
						System.out.println("String can be compressed further 2.");
					}
					return dst;
				}
			}
		} 
		catch (Exception e)
		{
			System.out.println(e.toString());
			System.out.println("Exiting compressOneStrings with an exception.");
			return dst;
		}
	}

	/**
	 * This applies a bitwise substitution iteratively that will expand or contract
	 * a bit string.
	 * 
	 * @param src  input bytes with trailing byte containing bit length
	 * @return byte array containing the result
	 */
	public static byte[] compressOneStrings(byte[] src)
	{
		ArrayList bitlength_list   = new ArrayList();
		ArrayList zero_ratio_list  = new ArrayList();
		ArrayList zero_amount_list = new ArrayList();
		ArrayList one_amount_list  = new ArrayList();
		
		int    bit_length  = getBitlength(src);
		double zero_ratio  = getZeroRatio(src, bit_length);
		int    zero_amount = getCompressionAmount(src, bit_length, 0);
		int    one_amount  = getCompressionAmount(src, bit_length, 1);
	    
		bitlength_list.add(bit_length);
		zero_ratio_list.add(zero_ratio);
		zero_amount_list.add(zero_amount);
		one_amount_list.add(one_amount);
		
		
		if(one_amount > 0)
		{
			// This never seems to happen with one (or run) strings,
			// which is why there's a print statement, in case it does.
			if(zero_amount < 0)
			{
			   int iterations = getIterations(src);
			   System.out.println("String with ratio " + String.format("%.4f", zero_ratio) + " and length " + bit_length + " and " + iterations + " previous iterations did not compress.");
			   System.out.println("The amount expected from the zero transform is " + zero_amount);
			   System.out.println("The amount expected from the one transform is " + one_amount);
			   System.out.println();
			}
			
			byte [] dst    = new byte[src.length];
			System.arraycopy(src, 0, dst, 0, src.length);
			return dst;
		}
		else
		{
			byte[] buffer1 = new byte[src.length];
			byte[] buffer2 = new byte[src.length];
			
			// The result contains the length of the bit string contained in the buffer,
			// and a number signifying whether the original string was well formed or not.
			int [] result            = compressOneBits(src, bit_length, buffer1);
			int    compressed_length = result[0];
			
			/*
			if(compressed_length - bit_length != one_amount)
			{
				System.out.println("Actual amount " + (compressed_length - bit_length) + " was not equal to predicted amount " + one_amount + " after initial iteration.");
				System.out.println();
			}
			*/
			
			zero_ratio  = getZeroRatio(buffer1, compressed_length);
			zero_amount = getCompressionAmount(buffer1, compressed_length, 0);
			one_amount  = getCompressionAmount(buffer1, compressed_length, 1);
		    
			bitlength_list.add(compressed_length);
			zero_ratio_list.add(zero_ratio);
			zero_amount_list.add(zero_amount);
			one_amount_list.add(one_amount);
			
			// Might be useful to check if the string being compressed is well formed.
			// Doesn't seem to matter.
			//if(one_amount >= 0 || result[1] == 1)
			if(one_amount >= 0)
			{
				// See above.
				if(zero_amount < 0)
			    {
			      	System.out.println("Anomalous 1 string behavior:");
				    int size = zero_ratio_list.size();
				    for(int i = 0; i < size; i++)
				    {
				    	    int current_length      = (int)bitlength_list.get(i);
				    	    double current_ratio    = (double)zero_ratio_list.get(i);
				        	int current_zero_amount = (int)zero_amount_list.get(i);
				    	    int current_one_amount  = (int)one_amount_list.get(i);
				    	    System.out.println(current_length + "\t" + String.format("%.2f", current_ratio) + "\t" + current_zero_amount + "\t" + current_one_amount);
				    }
				    System.out.println();
			    }
				
				int byte_length = compressed_length / 8;
				if(compressed_length % 8 != 0)
					byte_length++;
				byte extra_bits = (byte) (compressed_length % 8);
				if(extra_bits != 0)
					extra_bits = (byte) (8 - extra_bits);
				extra_bits <<= 5;

				byte [] dst = new byte[byte_length + 1];
				System.arraycopy(buffer1, 0, dst, 0, byte_length);
				dst[byte_length] = 17;
				dst[byte_length] |= extra_bits;
				return dst;
			}	
			else
			{
				int iterations = 1;
				//while (one_amount < 0 && iterations < 15 && result[1] == 0)
				while (one_amount < 0 && iterations < 15)
				{
					int previous_length = compressed_length;
					if(iterations % 2 == 1)
					{
						result = compressOneBits(buffer1, previous_length, buffer2);
						compressed_length = result[0];
						iterations++;
						
						if(compressed_length - previous_length != one_amount)
						{
							System.out.println("Actual amount " + (compressed_length - previous_length) + " was not equal to predicted amount " + one_amount + " after  iteration " + iterations);
							System.out.println();
						}
						
						zero_ratio        = getZeroRatio(buffer2, compressed_length);
						zero_amount       = getCompressionAmount(buffer2, compressed_length, 0);
						one_amount        = getCompressionAmount(buffer2, compressed_length, 1);
						bitlength_list.add(compressed_length);
						zero_ratio_list.add(zero_ratio);
						zero_amount_list.add(zero_amount);
						one_amount_list.add(one_amount);
					} 
					else
					{
						result = compressOneBits(buffer2, previous_length, buffer1);
						compressed_length = result[0];
						iterations++;
						
						if(compressed_length - previous_length != one_amount)
						{
							System.out.println("Actual amount " + (compressed_length - previous_length) + " was not equal to predicted amount " + one_amount + " after  iteration " + iterations);
							System.out.println();
						}
						
						zero_ratio        = getZeroRatio(buffer1, compressed_length);
						zero_amount       = getCompressionAmount(buffer1, compressed_length, 0);
						one_amount        = getCompressionAmount(buffer1, compressed_length, 1);
						bitlength_list.add(compressed_length);
						zero_ratio_list.add(zero_ratio);
						zero_amount_list.add(zero_amount);
						one_amount_list.add(one_amount);
					}
				}

				if(zero_amount < 0)
			    {
					// See above.
					System.out.println("Anomalous 1 string behavior:");
				    int size = zero_ratio_list.size();
				    for(int i = 0; i < size; i++)
				    {
				    	    int current_length      = (int)bitlength_list.get(i);
				       	double current_ratio    = (double)zero_ratio_list.get(i);
				    	    int current_zero_amount = (int)zero_amount_list.get(i);
				    	    int current_one_amount  = (int)one_amount_list.get(i);
				    	    System.out.println(current_length + "\t" + String.format("%.2f", current_ratio) + "\t" + current_zero_amount + "\t" + current_one_amount);
				    }
				    System.out.println();
			    }
				
				int byte_length = compressed_length / 8;
				if(compressed_length % 8 != 0)
					byte_length++;
				
				byte [] dst = new byte[byte_length + 1];
				if(iterations % 2 == 0)
					System.arraycopy(buffer2, 0, dst, 0, byte_length);
				else
					System.arraycopy(buffer1, 0, dst, 0, byte_length);
				dst[byte_length] = (byte) (iterations + 16);
				byte extra_bits = (byte) (compressed_length % 8);
				if(extra_bits != 0)
					extra_bits = (byte) (8 - extra_bits);
				extra_bits <<= 5;
				dst[byte_length] |= extra_bits;
				return dst;   	
			}
		}
	}
	
	/**
	 * This applies a bitwise substitution iteratively that will expand or contract
	 * a bit string.
	 * It requires that the bit string length is contained in the trailing byte.
	 * 
	 * @param src  input bytes
	 * @return byte array containing the result with a trailing data byte.
	 */
	public static byte[] decompressOneStrings(byte[] src)
	{
		int last_byte = src.length - 1;
		int iterations = src[last_byte] & (byte) 31;
		byte extra_bits = (byte) (src[last_byte] >> 5);
		extra_bits &= 7;
		int bit_length = (src.length - 1) * 8 - extra_bits;

		// If it's not a one type string, we'll still process it.
		// This will compress a zero type string.
		if(iterations < 16)
			System.out.println("Not one type string."); 
		else
			iterations -= 16;

		// If it was not compressable, we copy src to dst.
		if(iterations == 0)
		{
			byte[] dst = new byte[src.length];
			System.arraycopy(src, 0, dst, 0, src.length);

			return dst;
		}

		if(iterations == 1)
		{
			int byte_length = bit_length / 8;
			if(bit_length % 8 != 0)
				byte_length++;
			byte_length *= 2;
			byte[] buffer = new byte[byte_length];
			int uncompressed_length = decompressOneBits(src, bit_length, buffer);
			byte_length = uncompressed_length / 8;
			if(uncompressed_length % 8 != 0)
				byte_length++;
			byte[] dst = new byte[byte_length + 1];
			System.arraycopy(buffer, 0, dst, 0, byte_length);

			extra_bits = (byte) (uncompressed_length % 8);
			if(extra_bits != 0)
			{
				extra_bits = (byte) (8 - extra_bits);
			    extra_bits <<= 5;
			    dst[byte_length] = extra_bits;
			}
            dst[byte_length] |= 16;
			return dst;
		} 
		else if(iterations % 2 == 0)
		{
			int byte_length = bit_length / 8;
			if(bit_length % 8 != 0)
				byte_length++;
			double factor = Math.pow(2, iterations);
			byte_length *= factor;

			byte[] buffer1 = new byte[byte_length * 2];
			byte[] buffer2 = new byte[byte_length * 2];

			int uncompressed_length = decompressOneBits(src, bit_length, buffer1);
			iterations--;
			while (iterations > 0)
			{
				int previous_length = uncompressed_length;
				if(iterations % 2 == 1)
					uncompressed_length = decompressOneBits(buffer1, previous_length, buffer2);
				else
					uncompressed_length = decompressOneBits(buffer2, previous_length, buffer1);
				iterations--;
			}
			byte_length = uncompressed_length / 8;
			if(uncompressed_length % 8 != 0)
				byte_length++;

			byte[] dst = new byte[byte_length + 1];
			System.arraycopy(buffer2, 0, dst, 0, byte_length);

			extra_bits = (byte) (uncompressed_length % 8);
			if(extra_bits != 0)
			{
				extra_bits = (byte) (8 - extra_bits);
			    extra_bits <<= 5;
			    dst[byte_length] = extra_bits;
			}
			dst[byte_length] |= 16;
			return dst;
		} 
		else
		{
			int byte_length = bit_length / 8;
			if(bit_length % 8 != 0)
				byte_length++;
			double factor = Math.pow(2, iterations);
			byte_length *= factor;

			byte[] buffer1 = new byte[byte_length * 2];
			byte[] buffer2 = new byte[byte_length * 2];

			int uncompressed_length = decompressOneBits(src, bit_length, buffer1);
			iterations--;
			while (iterations > 0)
			{
				int previous_length = uncompressed_length;
				if(iterations % 2 == 0)
				{
					uncompressed_length = decompressOneBits(buffer1, previous_length, buffer2);
				} 
				else
				{
					uncompressed_length = decompressOneBits(buffer2, previous_length, buffer1);
				}
				iterations--;
			}
			byte_length = uncompressed_length / 8;
			if(uncompressed_length % 8 != 0)
				byte_length++;

			byte[] dst = new byte[byte_length + 1];

			System.arraycopy(buffer1, 0, dst, 0, byte_length);
            
			extra_bits = (byte) (uncompressed_length % 8);
			if(extra_bits != 0)
			{
				extra_bits = (byte) (8 - extra_bits);
			    extra_bits <<= 5;
			    dst[byte_length] = extra_bits;
			}
			dst[byte_length] |= 16;
			return dst;
		}
	}
	
	public static byte[] compressStrings(byte[] string)
	{
		int type = getType(string);
		if(type == 0)
		{
			byte[] compressed_string = compressZeroStrings(string);
			return compressed_string;
		} 
		else if(type == 1)
		{
			byte[] compressed_string = compressOneStrings(string);
			return compressed_string;
		} 
		else
			return string;
	}


	public static byte[] decompressStrings(byte[] string)
	{
		int type = getType(string);
		if(type == 0)
		{
			byte[] decompressed_string = decompressZeroStrings(string);
			return decompressed_string;
		} 
		else if(type == 1)
		{
			byte[] decompressed_string = decompressOneStrings(string);
			return decompressed_string;
		} 
		else
			return string;
	}

	// Functions that get information about a string
	// from the trailing byte.
	public static int getBitlength(byte[] string)
	{
		byte last_byte = string[string.length - 1];
		byte extra_bits = (byte) (last_byte >> 5);
		extra_bits &= 7;
		int bitlength = (string.length - 1) * 8 - extra_bits;

		return bitlength;
	}

	public static byte getIterations(byte[] string)
	{
		byte last_byte = string[string.length - 1];
		byte iterations = (byte) (last_byte & 31);

		return iterations;
	}

	public static byte getType(byte[] string)
	{
		byte last_byte = string[string.length - 1];
		byte iterations = (byte) (last_byte & 31);
		byte type = 0;
		if(iterations > 15)
			type = 1;

		return type;
	}

	public static double getZeroRatio(byte[] string, int bit_length)
	{
		int byte_length = bit_length / 8;
		int zero_sum = 0;
		int one_sum = 0;
		byte mask = 1;

		int n = byte_length;

		for(int i = 0; i < n; i++)
		{
			for(int j = 0; j < 8; j++)
			{
				int k = string[i] & mask << j;
				if(k == 0)
					zero_sum++;
				else
					one_sum++;
			}
		}

		int remainder = bit_length % 8;
		if(remainder != 0)
		{
			for(int i = 0; i < remainder; i++)
			{
				int j = string[byte_length] & mask << i;
				if(j == 0)
					zero_sum++;
				else
					one_sum++;
			}
		}

		double ratio = zero_sum;
		ratio /= zero_sum + one_sum;
		return ratio;
	}
	
	public static int[] getBitTable()
	{
		int [] table = new int[256];
		
		byte value = 0;
		byte mask  = 1;
		int  sum   = 0;
		for(int i = 0; i < 256; i++)
		{
			for(int j = 0; j < 8; j++)
			{
			    int k = value & mask << j;
				if(k == 0)
					sum++;
			}
			table[i] = sum;
			sum      = 0;
			value++;
		}
		return table;
	}
	
	
	public static double getZeroRatio(byte[] string, int bit_length, int [] table)
	{
		int byte_length = bit_length / 8;
		int zero_sum = 0;
		int one_sum = 0;
		

		int n = byte_length;
		
		for(int i = 0; i < n; i++)
		{
			int j = (int)string[i];
			if(j < 0)
				j += 256;
			zero_sum += table[j];
			one_sum  += 8 - table[j];
		}

		byte mask = 1;
		int remainder = bit_length % 8;
		if(remainder != 0)
		{
			for(int i = 0; i < remainder; i++)
			{
				int j = string[byte_length] & mask << i;
				if(j == 0)
					zero_sum++;
				else
					one_sum++;
			}
		}

		double ratio = zero_sum;
		ratio       /= zero_sum + one_sum;
		
		return ratio;
	}
	
	
	
	public static double getZeroRatio2(byte[] string, int bit_length)
	{
		int byte_length = bit_length / 8;
		int zero_sum = 0;
		int one_sum = 0;
		byte mask = 1;

		int n = byte_length;
		
		int [] bit_table = getBitTable();
		
		for(int i = 0; i < n; i++)
		{
			int j = (int)string[i];
			if(j < 0)
				j += 256;
			zero_sum += bit_table[j];
			one_sum  += 8 - bit_table[j];
		}

		int remainder = bit_length % 8;
		if(remainder != 0)
		{
			for(int i = 0; i < remainder; i++)
			{
				int j = string[byte_length] & mask << i;
				if(j == 0)
					zero_sum++;
				else
					one_sum++;
			}
		}

		double ratio = zero_sum;
		ratio /= zero_sum + one_sum;
		return ratio;
	}
	
	public static double getZeroRatio(byte[] string, int bit_offset, int bit_length)
	{
		byte[] shifted_string = SegmentMapper.shiftRight(string, bit_offset);
	    double ratio          =  getZeroRatio(shifted_string, bit_length);
		return ratio;
	}
	

	public static ArrayList<Double> getRatioList(byte [] string, int segment_length)
	{
		ArrayList<Double> ratio_list  = new ArrayList<Double>();
		
		// Number of segments to check, assuming the last byte is meta-data.
		int number_of_segments  = (string.length - 1) * 8 - segment_length;
		int current_bit  = 0;
		int current_byte = 0;
		byte mask        = 1;
		
		for(int i = 0; i < number_of_segments; i++)
		{
			int zero_sum = 0;
			int one_sum  = 0;
			
			int segment_bit  = current_bit;
			int segment_byte = current_byte;
			
			for(int j = 0; j < segment_length; j++)
			{
				int k = string[segment_byte] & (mask <<segment_bit);	
				if(k == 0)
			        zero_sum++;
			    else
			    	one_sum++;
			    segment_bit++;
			    if(segment_bit == 8)
			    {
			    	    segment_bit = 0;
			    	    segment_byte++;
			    }
			}
			double ratio = zero_sum;
			ratio       /= (zero_sum + one_sum);
			ratio_list.add(ratio);
			
			current_bit++;
			if(current_bit == 8)
		    {
				current_bit = 0;
				current_byte++;
		    }
		}
		
		return ratio_list;
	}
	
	public static ArrayList<int []> getRatioDeltaList(ArrayList<Double> ratio_list, int segment_length, byte [] string)
	{
		ArrayList delta_list = new ArrayList<int []>();
		int ratio_list_size  = ratio_list.size();
		
		int string_bitlength = getBitlength(string);
		
		
		int ratio_remainder = ratio_list_size % segment_length;
		//System.out.println("Ratio remainder is " + ratio_remainder);
		
		
		int max_index        =  ratio_list_size - (ratio_remainder + 1);
		
		double bin_size      = .05;
		int lower_limit      = 7;
		int upper_limit      = 11;
		
		
		int current_index    = 0;
		int current_length   = segment_length;
		
		
		double ratio       = ratio_list.get(current_index);
		int current_number = SegmentMapper.getNumber(ratio, bin_size);
		
		int next_index    = current_index + current_length;
		ratio = ratio_list.get(next_index);
		int next_number = SegmentMapper.getNumber(ratio, bin_size);
		
		if(SegmentMapper.isSimilar(current_number, next_number, lower_limit, upper_limit))
		{
			int aggregated_number = (current_number * current_length + next_number * segment_length) / (current_length + segment_length);
			current_length += segment_length;
			
			//System.out.println("Searching forwards.");
			// Keep looking forward until a dSegmentMapper.isSimilar number is reached.
			while(SegmentMapper.isSimilar(current_number, next_number, lower_limit, upper_limit))
			{
				next_index    = current_index + current_length;
				if(next_index <= max_index)
				{
					ratio       = ratio_list.get(next_index);
					next_number = SegmentMapper.getNumber(ratio, bin_size);
					
					// Theoretically the aggregated number will always be in the same
					// category as the original current number.
					if(SegmentMapper.isSimilar(aggregated_number, next_number, lower_limit, upper_limit))
					{
						aggregated_number = (aggregated_number * current_length + next_number * segment_length) / (current_length + segment_length);
						current_length += segment_length;
					}
				}
				else
					break;
			}
			current_number = aggregated_number;	
		}
		
		int [] delta = new int[3];
		delta[0] = current_index;
		delta[1] = current_length;
		delta[2] = current_number;
		delta_list.add(delta);
		
		current_index += current_length;
		
		//System.out.println("Max index is " + max_index);
		//System.out.println("Current index is " + current_index);
		
		if(current_index >= max_index)
		{
		    if(current_index > max_index)
		    {
			    current_length = string.length - max_index;
			    ratio = getZeroRatio(string, current_index, current_length);
		        current_number = SegmentMapper.getNumber(ratio, bin_size);
		    }
		    else
		    {
		    	current_length = segment_length;
			    ratio = ratio_list.get(current_index);
			    current_number = SegmentMapper.getNumber(ratio, bin_size);
		    }
		    delta = new int[3];
			delta[0] = current_index;
			delta[1] = current_length;
			delta[2] = current_number;
			delta_list.add(delta);
		}
		else
		{
			boolean reached_end_of_string = false;
			current_length = segment_length;
			while(current_index < max_index - current_length)
			{
				
			    next_index = current_index + current_length;   
			    ratio = ratio_list.get(next_index);
				next_number = SegmentMapper.getNumber(ratio, bin_size);
				
				if(SegmentMapper.isSimilar(current_number, next_number, lower_limit, upper_limit))
				{
					if(next_index == max_index)
				    	reached_end_of_string = true;
					
					int aggregated_number = (current_number * current_length + next_number * segment_length) / (current_length + segment_length);
					current_length       += segment_length;
					
					//System.out.println("Searching forwards.");
					// Keep looking forward until a dSegmentMapper.isSimilar number is reached.
					while(SegmentMapper.isSimilar(current_number, next_number, lower_limit, upper_limit))
					{
						next_index    = current_index + current_length;
						if(next_index <= max_index)
						{
							ratio       = ratio_list.get(next_index);
							next_number = SegmentMapper.getNumber(ratio, bin_size);
							// Theoretically the aggregated number will always be in the same
							// category as the original current number.
							if(SegmentMapper.isSimilar(aggregated_number, next_number, lower_limit, upper_limit))
							{
								aggregated_number = (aggregated_number * current_length + next_number * segment_length) / (current_length + segment_length);
								current_length += segment_length;
							}
						}
						else
							break;
					}
					
					current_number = aggregated_number;	
					delta = new int[3];
					delta[0] = current_index;
					delta[1] = current_length;
					delta[2] = current_number;
					delta_list.add(delta);
					
					if(current_index < max_index - current_length)
					{
					    current_index += current_length;
					    current_length = segment_length;
					    ratio = ratio_list.get(current_index);
					    current_number = SegmentMapper.getNumber(ratio, bin_size);
					    //System.out.println("Current index is " + current_index);
						//System.out.println("Max index is " + current_index);
					}
				}
				else
				{
					delta = new int[3];
					delta[0] = current_index;
					delta[1] = current_length;
					delta[2] = current_number;
					delta_list.add(delta);
					
					
					current_index += current_length;
					//System.out.println("Current index is " + current_index);
				    //System.out.println("Max index is " + max_index);
					if(current_index < max_index - segment_length)
					{
					    current_length = segment_length;
					    ratio = ratio_list.get(current_index);
					    current_number = SegmentMapper.getNumber(ratio, bin_size);	
					}
				}
			}
			
			if(!reached_end_of_string)
			{
				System.out.println("Did not reach end of string during aggregating process.");
				System.out.println("Current index is " + current_index);
				System.out.println("Max index is " + max_index);
				System.out.println();
			}
		}
		
		return delta_list;
	}
	
	
	public static ArrayList<int []> getRatioDeltaList2(ArrayList<Double> ratio_list, int segment_length, byte [] string)
	{
		ArrayList delta_list = new ArrayList<int []>();
		int ratio_list_size  = ratio_list.size();
		int max_index        =  ratio_list_size - 1;
		int minimum_length   = 40;
		
		double bin_size      = .05;
		int lower_limit      = 7;
		int upper_limit      = 11;
		
		
		int current_index    = 0;
		int current_offset   = 0;
		int current_length   = segment_length;
		
		double ratio       = ratio_list.get(current_index);
		int current_number = SegmentMapper.getNumber(ratio, bin_size);
		
		int next_index    = current_offset + current_length;
		ratio = ratio_list.get(next_index);
		int next_number = SegmentMapper.getNumber(ratio, bin_size);
		
		if(SegmentMapper.isSimilar(current_number, next_number, lower_limit, upper_limit))
		{
			int aggregated_number = (current_number * current_length + next_number * segment_length) / (current_length + segment_length);
			current_length += segment_length;
			
			System.out.println("Searching forwards.");
			// Keep looking forward until a similar number is reached.
			while(SegmentMapper.isSimilar(current_number, next_number, lower_limit, upper_limit))
			{
				next_index    = current_offset + current_length;
				if(next_index <= max_index)
				{
					ratio       = ratio_list.get(next_index);
					next_number = SegmentMapper.getNumber(ratio, bin_size);
					
					// Theoretically the aggregated number will always be in the same
					// category as the original current number.
					if(SegmentMapper.isSimilar(aggregated_number, next_number, lower_limit, upper_limit))
					{
						aggregated_number = (aggregated_number * current_length + next_number * segment_length) / (current_length + segment_length);
						current_length += segment_length;
					}
				}
				else
					break;
			}
			
			current_number = aggregated_number;	
		}
		else
		{
		    // Look backwards for a similar number and decrement the current length until it reaches a minimum length.	
			boolean found_similar_number = false;
			int i = segment_length;
			
			while(i > minimum_length && !found_similar_number)
			{
				System.out.println("Searching backwards for similar number.");
				i--;
				ratio = ratio_list.get(i + current_offset);
				next_number = SegmentMapper.getNumber(ratio, bin_size);
				if(SegmentMapper.isSimilar(current_number, next_number, lower_limit, upper_limit))
				{
					// Now check if the current number has changed.
					current_length       = i;
				    ratio = getZeroRatio(string, current_offset, current_length);
				    current_number = SegmentMapper.getNumber(ratio, bin_size);
				   
				    if(SegmentMapper.isSimilar(current_number, next_number, lower_limit, upper_limit))
				    {
				    	    // The current number is still similar to the next number, so we aggregate the ratio numbers and lengths.
				      	int aggregated_number = (current_number * current_length + next_number * segment_length) / (current_length + segment_length);
						current_length += segment_length;  
						current_number = aggregated_number;
				    }
				    
				    // Even if the new current number is now similar, we've reached our base case.
				    found_similar_number = true;
				}
			}
		}
		
		int [] delta = new int[3];
		delta[0] = current_index;
		delta[1] = current_length;
		delta[2] = current_number;
		delta_list.add(delta);
		
		current_offset += current_length;
		current_index   = current_offset;
		if( current_index >= max_index)
		{
		    if(current_index > max_index)
		    {
			    current_length = string.length - max_index;
			    ratio = getZeroRatio(string, current_offset, current_length);
		        current_number = SegmentMapper.getNumber(ratio, bin_size);
		    }
		    else
		    {
		    	current_length = segment_length;
			    ratio = ratio_list.get(current_index);
			    current_number = SegmentMapper.getNumber(ratio, bin_size);
		    }
		    delta = new int[3];
			delta[0] = current_index;
			delta[1] = current_length;
			delta[2] = current_number;
			delta_list.add(delta);
		}
		else
		{
			boolean reached_end_of_string = false;
			current_length = segment_length;
			while(current_index < max_index)
			{
			    next_index = current_offset + current_length;   
			    if(next_index == max_index)
			    	reached_end_of_string = true;
			    ratio = ratio_list.get(next_index);
				next_number = SegmentMapper.getNumber(ratio, bin_size);
				
				if(SegmentMapper.isSimilar(current_number, next_number, lower_limit, upper_limit))
				{
					int aggregated_number = (current_number * current_length + next_number * segment_length) / (current_length + segment_length);
					current_length += segment_length;
					
					System.out.println("Searching forwards.");
					// Keep looking forward until a similar number is reached.
					while(SegmentMapper.isSimilar(current_number, next_number, lower_limit, upper_limit))
					{
						next_index    = current_offset + current_length;
						if(next_index <= max_index)
						{
							ratio       = ratio_list.get(next_index);
							next_number = SegmentMapper.getNumber(ratio, bin_size);
							
							// Theoretically the aggregated number will always be in the same
							// category as the original current number.
							if(SegmentMapper.isSimilar(aggregated_number, next_number, lower_limit, upper_limit))
							{
								aggregated_number = (aggregated_number * current_length + next_number * segment_length) / (current_length + segment_length);
								current_length += segment_length;
							}
						}
						else
							break;
					}
					
					current_number = aggregated_number;	
					delta = new int[3];
					delta[0] = current_index;
					delta[1] = current_length;
					delta[2] = current_number;
					delta_list.add(delta);
					
					current_index += current_length;
					current_length = segment_length;
					ratio = ratio_list.get(current_index);
					current_number = SegmentMapper.getNumber(ratio, bin_size);
				}
				else
				{
				    // Look backwards for a similar number and decrement the current length until it reaches a minimum length.	
					boolean found_similar_number = false;
					int i = segment_length;
					System.out.println("Searching backwards for similar number ");
					while(i > minimum_length && !found_similar_number)
					{
						//System.out.println("Searching backwards for similar number " + i);
						i--;
						ratio = ratio_list.get(i + current_offset);
						next_number = SegmentMapper.getNumber(ratio, bin_size);
						if(SegmentMapper.isSimilar(current_number, next_number, lower_limit, upper_limit))
						{
							// Now check if the current number has changed.
							current_length       = i;
						    ratio = getZeroRatio(string, current_index, current_length);
						    current_number = SegmentMapper.getNumber(ratio, bin_size);
						   
						    if(SegmentMapper.isSimilar(current_number, next_number, lower_limit, upper_limit))
						    {
						    	    // The current number is still similar to the next number, so we aggregate the ratio numbers and lengths.
						    	    int aggregated_number = (current_number * current_length + next_number * segment_length) / (current_length + segment_length);
								current_length += segment_length;  
								current_number = aggregated_number;
						    }
						    delta = new int[3];
							delta[0] = current_index;
							delta[1] = current_length;
							delta[2] = current_number;
							delta_list.add(delta);
							
							current_index += current_length;
							current_length = segment_length;
							ratio = ratio_list.get(current_index);
							current_number = SegmentMapper.getNumber(ratio, bin_size);
							
						    // Even if the new current number is now dSegmentMapper.isSimilar, we've reached our base case.
						    found_similar_number = true;
						}
					}
					if(found_similar_number)
						System.out.println("Found similar number.");
					//else
					//	System.out.println("Did not find similar number.");
					System.out.println();
				}
				
				
				
				
			}
			if(!reached_end_of_string)
			{
			    if(current_index > max_index)
		        {
			        current_length = string.length - max_index;
			        ratio = getZeroRatio(string, current_offset, current_length);
		            current_number = SegmentMapper.getNumber(ratio, bin_size);
		        }
		        else 
		        {
		    	    current_length = segment_length;
			        ratio = ratio_list.get(current_index);
			        current_number = SegmentMapper.getNumber(ratio, bin_size);
		        }
		        delta = new int[3];
			    delta[0] = current_index;
			    delta[1] = current_length;
			    delta[2] = current_number;
			    delta_list.add(delta);
			}
		}
		
		
		
		
		return delta_list;
	}
	
	public static ArrayList<int []> getRatioDeltaList3(ArrayList<Double> ratio_list, int segment_length)
	{
		ArrayList delta_list = new ArrayList<int []>();
		int       list_size  = ratio_list.size();
		int max_index        = ratio_list.size() - 1;
		int current_index    = 0;
		
		double ratio     = ratio_list.get(0);
		
		int current_number = 0;
		
		double limit = .05;
		while(ratio > limit)
		{
			current_number++;
			limit += .05;
		}
		
		int current_offset = 0;
		int current_length = segment_length;
		
	
		//System.out.println("Initial ratio number is " + current_number);
		//System.out.println("Initial segment length is " + current_length);
		
		int next_index = current_length + segment_length;
		if(next_index > max_index)
			next_index = max_index;
		//System.out.println("Next index is " + next_index);
		//double next_ratio = ratio_list.get(bit_offset + bit_length + segment_length);
		ratio = ratio_list.get(next_index);
		int    next_number = 0;
		
		limit = .05;
		while(ratio > limit)
		{
			next_number++;
			limit += .05;
		}
		
		if(current_number < 7 && next_number < 7)
		{
			int aggregated_number = current_number;
			if(next_index < max_index)
			{
		        aggregated_number = (current_number * current_length + next_number * segment_length) / (current_length + segment_length); 
		        current_number    = aggregated_number;
		        current_length   += segment_length;
			}
			else
			{
				int final_segment_length = next_index - current_index;
				aggregated_number        = (current_number * current_length + next_number * final_segment_length) / (current_length + final_segment_length);
				current_number           = aggregated_number;
				current_length          += final_segment_length;	
			}
			
		    boolean searching_backwards = false;
		    while(next_number < 7 && !searching_backwards && next_index < max_index)
		    {
		    	next_index = current_offset + current_length + segment_length;
		    	if(next_index > max_index)
		    		next_index = max_index;
		    	ratio  = ratio_list.get(next_index);
				next_number = 0;
				
				limit = .05;
				while(ratio > limit)
				{
					next_number++;
					limit += .05;
				}
				
				if(next_number < 7)
			    {
					aggregated_number = current_number;
					if(next_index < max_index)
					{
				        aggregated_number = (current_number * current_length + next_number * segment_length) / (current_length + segment_length); 
				        current_number    = aggregated_number;
				        current_length   += segment_length;
					}
					else
					{
						int final_segment_length = next_index - current_index;
						aggregated_number        = (current_number * current_length + next_number * final_segment_length) / (current_length + final_segment_length);
						current_number           = aggregated_number;
						current_length          += final_segment_length;	
					} 
			    }
			    else
			    {
			    	searching_backwards          = true;
			    	int current_segment_length   = segment_length / 2;
					boolean found_similar_number = false;
					
					while(current_segment_length % 2 == 0 && !found_similar_number)
					{
						next_index = current_offset + current_length + current_segment_length;
						ratio  = ratio_list.get(next_index);
						next_number = 0;
						
						limit = .05;
						while(ratio > limit)
						{
							next_number++;
							limit += .05;
						} 
						
						if(next_number < 7)
						{
							aggregated_number = (current_number * current_length + next_number * current_segment_length) / (current_length + current_segment_length); 
					        current_number    = aggregated_number;
					        current_length   += current_segment_length;
						    found_similar_number = true;
						}
						else
							current_segment_length /= 2;
					}
			    }
		    }
		}
		else if(current_number > 6 && current_number < 12 && next_number > 6 && next_number < 12)
		{
			int aggregated_number = current_number;
			if(next_index < max_index)
			{
		        aggregated_number = (current_number * current_length + next_number * segment_length) / (current_length + segment_length); 
		        current_number    = aggregated_number;
		        current_length   += segment_length;
			}
			else
			{
				int final_segment_length = next_index - current_index;
				aggregated_number        = (current_number * current_length + next_number * final_segment_length) / (current_length + final_segment_length);
				current_number           = aggregated_number;
				current_length          += final_segment_length;	
			}
			
		    boolean searching_backwards = false;
		    while(next_number > 6 && next_number < 12 && !searching_backwards && next_index < max_index)
		    {
		    	next_index = current_offset + current_length + segment_length;
		    	if(next_index > max_index)
		    		next_index = max_index;
		    	ratio  = ratio_list.get(next_index);
				next_number = 0;
				
				limit = .05;
				while(ratio > limit)
				{
					next_number++;
					limit += .05;
				}
				
				if(next_number > 6 && next_number < 12)
			    {
					aggregated_number = current_number;
					if(next_index < max_index)
					{
				        aggregated_number = (current_number * current_length + next_number * segment_length) / (current_length + segment_length); 
				        current_number    = aggregated_number;
				        current_length   += segment_length;
					}
					else
					{
						int final_segment_length = next_index - current_index;
						aggregated_number        = (current_number * current_length + next_number * final_segment_length) / (current_length + final_segment_length);
						current_number           = aggregated_number;
						current_length          += final_segment_length;	
					} 
			    }
			    else
			    {
			    	searching_backwards          = true;
			    	int current_segment_length   = segment_length / 2;
					boolean found_similar_number = false;
					
					while(current_segment_length % 2 == 0 && !found_similar_number)
					{
						next_index = current_offset + current_length + current_segment_length;
						ratio  = ratio_list.get(next_index);
						next_number = 0;
						
						limit = .05;
						while(ratio > limit)
						{
							next_number++;
							limit += .05;
						} 
						
						if(next_number > 6 && next_number < 12)
						{
							aggregated_number = (current_number * current_length + next_number * current_segment_length) / (current_length + current_segment_length); 
					        current_number    = aggregated_number;
					        current_length   += current_segment_length;
						    found_similar_number = true;
						}
						else
							current_segment_length /= 2;
					}
			    }
		    }
		}
		else if(current_number > 11 && next_number > 11)
		{
			int aggregated_number = current_number;
			if(next_index < max_index)
			{
		        aggregated_number = (current_number * current_length + next_number * segment_length) / (current_length + segment_length); 
		        current_number    = aggregated_number;
		        current_length   += segment_length;
			}
			else
			{
				int final_segment_length = next_index - current_index;
				aggregated_number        = (current_number * current_length + next_number * final_segment_length) / (current_length + final_segment_length);
				current_number           = aggregated_number;
				current_length          += final_segment_length;	
			}
			
		    boolean searching_backwards = false;
		    while(next_number > 11 && !searching_backwards && next_index < max_index)
		    {
		    	next_index = current_offset + current_length + segment_length;
		    	if(next_index > max_index)
		    		next_index = max_index;
		    	ratio  = ratio_list.get(next_index);
				next_number = 0;
				
				limit = .05;
				while(ratio > limit)
				{
					next_number++;
					limit += .05;
				}
				
				if(next_number > 11)
			    {
					aggregated_number = current_number;
					if(next_index < max_index)
					{
				        aggregated_number = (current_number * current_length + next_number * segment_length) / (current_length + segment_length); 
				        current_number    = aggregated_number;
				        current_length   += segment_length;
					}
					else
					{
						int final_segment_length = next_index - current_index;
						aggregated_number        = (current_number * current_length + next_number * final_segment_length) / (current_length + final_segment_length);
						current_number           = aggregated_number;
						current_length          += final_segment_length;	
					} 
			    }
			    else
			    {
			    	searching_backwards          = true;
			    	int current_segment_length   = segment_length / 2;
					boolean found_similar_number = false;
					
					while(current_segment_length % 2 == 0 && !found_similar_number)
					{
						next_index = current_offset + current_length + current_segment_length;
						ratio  = ratio_list.get(next_index);
						next_number = 0;
						
						limit = .05;
						while(ratio > limit)
						{
							next_number++;
							limit += .05;
						} 
						
						if(next_number > 11)
						{
							aggregated_number = (current_number * current_length + next_number * current_segment_length) / (current_length + current_segment_length); 
					        current_number    = aggregated_number;
					        current_length   += current_segment_length;
						    found_similar_number = true;
						}
						else
							current_segment_length /= 2;
					}
			    }
		    }
		}
		else
		{
			int current_segment_length = segment_length / 2;
			boolean found_similar_number = false;
			while(current_segment_length % 2 == 0 && !found_similar_number)
			{
			    next_index  = current_offset + current_length + current_segment_length;
			    ratio       = ratio_list.get(next_index);
                next_number = 0;
				
				limit = .05;
				while(ratio > limit)
				{
					next_number++;
					limit += .05;
				} 
				
				if(current_number < 7 && next_number < 7)
				{
					int aggregated_number = (current_number * current_length + next_number * current_segment_length) / (current_length + current_segment_length);
				    current_number        = aggregated_number; 	
				    current_length       += current_segment_length;
				    found_similar_number  = true;
				}
				else if(current_number > 6 && current_number < 12 && next_number > 6 && next_number < 12)
				{
					int aggregated_number = (current_number * current_length + next_number * current_segment_length) / (current_length + current_segment_length);
				    current_number        = aggregated_number; 	
				    current_length       += current_segment_length;
				    found_similar_number  = true;
				}
				else if(current_number > 11 && next_number > 11)
				{
					int aggregated_number = (current_number * current_length + next_number * current_segment_length) / (current_length + current_segment_length);
				    current_number        = aggregated_number; 	
				    current_length       += current_segment_length;
				    found_similar_number  = true;
				}
				else
					current_segment_length /= 2;
			}
		}
		

		int [] delta = new int[3];
		delta[0]     = current_number;
		delta[1]     = current_offset;
		delta[2]     = current_length;
		delta_list.add(delta);
	   
		current_index  = current_length;
		current_offset += current_length;
		
		while(current_index < max_index - 1)
		{
			next_index = current_offset + current_length + segment_length;
			if(next_index > max_index)
				next_index = max_index;
			ratio = ratio_list.get(next_index);
			next_number = 0;
			
			limit = .05;
			while(ratio > limit)
			{
				next_number++;
				limit += .05;
			}
			
			if(current_number < 7 && next_number < 7)
			{
				int aggregated_number = current_number;
				if(next_index < max_index)
				{
			        aggregated_number = (current_number * current_length + next_number * segment_length) / (current_length + segment_length); 
			        current_number    = aggregated_number;
			        current_length   += segment_length;
				}
				else
				{
					int final_segment_length = next_index - current_index;
					aggregated_number        = (current_number * current_length + next_number * final_segment_length) / (current_length + final_segment_length);
					current_number           = aggregated_number;
					current_length          += final_segment_length;	
				}
				
			    boolean searching_backwards = false;
			    while(next_number < 7 && !searching_backwards && next_index < max_index)
			    {
			    	next_index = current_offset + current_length + segment_length;
			    	if(next_index > max_index)
			    		next_index = max_index;
			    	ratio  = ratio_list.get(next_index);
					next_number = 0;
					
					limit = .05;
					while(ratio > limit)
					{
						next_number++;
						limit += .05;
					}
					
					if(next_number < 7)
				    {
						aggregated_number = current_number;
						if(next_index < max_index)
						{
					        aggregated_number = (current_number * current_length + next_number * segment_length) / (current_length + segment_length); 
					        current_number    = aggregated_number;
					        current_length   += segment_length;
						}
						else
						{
							int final_segment_length = next_index - current_index;
							aggregated_number        = (current_number * current_length + next_number * final_segment_length) / (current_length + final_segment_length);
							current_number           = aggregated_number;
							current_length          += final_segment_length;	
						} 
				    }
				    else
				    {
				    	searching_backwards          = true;
				    	int current_segment_length   = segment_length / 2;
						boolean found_similar_number = false;
						
						while(current_segment_length % 2 == 0 && !found_similar_number)
						{
							next_index = current_offset + current_length + current_segment_length;
							ratio  = ratio_list.get(next_index);
							next_number = 0;
							
							limit = .05;
							while(ratio > limit)
							{
								next_number++;
								limit += .05;
							} 
							
							if(next_number < 7)
							{
								aggregated_number = (current_number * current_length + next_number * current_segment_length) / (current_length + current_segment_length); 
						        current_number    = aggregated_number;
						        current_length   += current_segment_length;
							    found_similar_number = true;
							}
							else
								current_segment_length /= 2;
						}
				    }
			    }
			}
			else if(current_number > 6 && current_number < 12 && next_number > 6 && next_number < 12)
			{
				int aggregated_number = current_number;
				if(next_index < max_index)
				{
			        aggregated_number = (current_number * current_length + next_number * segment_length) / (current_length + segment_length); 
			        current_number    = aggregated_number;
			        current_length   += segment_length;
				}
				else
				{
					int final_segment_length = next_index - current_index;
					aggregated_number        = (current_number * current_length + next_number * final_segment_length) / (current_length + final_segment_length);
					current_number           = aggregated_number;
					current_length          += final_segment_length;	
				}
				
			    boolean searching_backwards = false;
			    while(next_number > 6 && next_number < 12 && !searching_backwards && next_index < max_index)
			    {
			    	next_index = current_offset + current_length + segment_length;
			    	if(next_index > max_index)
			    		next_index = max_index;
			    	ratio  = ratio_list.get(next_index);
					next_number = 0;
					
					limit = .05;
					while(ratio > limit)
					{
						next_number++;
						limit += .05;
					}
					
					if(next_number > 6 && next_number < 12)
				    {
						aggregated_number = current_number;
						if(next_index < max_index)
						{
					        aggregated_number = (current_number * current_length + next_number * segment_length) / (current_length + segment_length); 
					        current_number    = aggregated_number;
					        current_length   += segment_length;
						}
						else
						{
							int final_segment_length = next_index - current_index;
							aggregated_number        = (current_number * current_length + next_number * final_segment_length) / (current_length + final_segment_length);
							current_number           = aggregated_number;
							current_length          += final_segment_length;	
						} 
				    }
				    else
				    {
				    	searching_backwards          = true;
				    	int current_segment_length   = segment_length / 2;
						boolean found_similar_number = false;
						
						while(current_segment_length % 2 == 0 && !found_similar_number)
						{
							next_index = current_offset + current_segment_length;
							ratio  = ratio_list.get(next_index);
							next_number = 0;
							
							limit = .05;
							while(ratio > limit)
							{
								next_number++;
								limit += .05;
							} 
							
							if(next_number > 6 && next_number < 12)
							{
								aggregated_number = (current_number * current_length + next_number * current_segment_length) / (current_length + current_segment_length); 
						        current_number    = aggregated_number;
						        current_length   += current_segment_length;
							    found_similar_number = true;
							}
							else
								current_segment_length /= 2;
						}
				    }
			    }
			}
			else if(current_number > 11 && next_number > 11)
			{
				int aggregated_number = current_number;
				if(next_index < max_index)
				{
			        aggregated_number = (current_number * current_length + next_number * segment_length) / (current_length + segment_length); 
			        current_number    = aggregated_number;
			        current_length   += segment_length;
				}
				else
				{
					int final_segment_length = next_index - current_index;
					aggregated_number        = (current_number * current_length + next_number * final_segment_length) / (current_length + final_segment_length);
					current_number           = aggregated_number;
					current_length          += final_segment_length;	
				}
				
			    boolean searching_backwards = false;
			    while(next_number > 11 && !searching_backwards && next_index < max_index)
			    {
			    	next_index = current_offset + current_length + segment_length;
			    	if(next_index > max_index)
			    		next_index = max_index;
			    	ratio  = ratio_list.get(next_index);
					next_number = 0;
					
					limit = .05;
					while(ratio > limit)
					{
						next_number++;
						limit += .05;
					}
					
					if(next_number > 11)
				    {
						aggregated_number = current_number;
						if(next_index < max_index)
						{
					        aggregated_number = (current_number * current_length + next_number * segment_length) / (current_length + segment_length); 
					        current_number    = aggregated_number;
					        current_length   += segment_length;
						}
						else
						{
							int final_segment_length = next_index - current_index;
							aggregated_number        = (current_number * current_length + next_number * final_segment_length) / (current_length + final_segment_length);
							current_number           = aggregated_number;
							current_length          += final_segment_length;	
						} 
				    }
				    else
				    {
				    	searching_backwards          = true;
				    	int current_segment_length   = segment_length / 2;
						boolean found_similar_number = false;
						
						while(current_segment_length % 2 == 0 && !found_similar_number)
						{
							next_index = current_offset + current_segment_length;
							ratio  = ratio_list.get(next_index);
							next_number = 0;
							
							limit = .05;
							while(ratio > limit)
							{
								next_number++;
								limit += .05;
							} 
							
							if(next_number > 11)
							{
								aggregated_number = (current_number * current_length + next_number * current_segment_length) / (current_length + current_segment_length); 
						        current_number    = aggregated_number;
						        current_length   += current_segment_length;
							    found_similar_number = true;
							}
							else
								current_segment_length /= 2;
						}
				    }
			    }
			}
			else
			{
				int current_segment_length = segment_length / 2;
				boolean found_similar_number = false;
				while(current_segment_length % 2 == 0 && !found_similar_number)
				{
				    next_index  = current_offset + current_segment_length;
				    ratio       = ratio_list.get(next_index);
	                next_number = 0;
					
					limit = .05;
					while(ratio > limit)
					{
						next_number++;
						limit += .05;
					} 
					
					if(current_number < 7 && next_number < 7)
					{
						int aggregated_number = (current_number * current_length + next_number * current_segment_length) / (current_length + current_segment_length);
					    current_number        = aggregated_number; 	
					    current_length       += current_segment_length;
					    found_similar_number  = true;
					}
					else if(current_number > 6 && current_number < 12 && next_number > 6 && next_number < 12)
					{
						int aggregated_number = (current_number * current_length + next_number * current_segment_length) / (current_length + current_segment_length);
					    current_number        = aggregated_number; 	
					    current_length       += current_segment_length;
					    found_similar_number  = true;
					}
					else if(current_number > 11 && next_number > 11)
					{
						int aggregated_number = (current_number * current_length + next_number * current_segment_length) / (current_length + current_segment_length);
					    current_number        = aggregated_number; 	
					    current_length       += current_segment_length;
					    found_similar_number  = true;
					}
					else
						current_segment_length /= 2;
				}
			}
			

			delta    = new int[3];
			delta[0] = current_number;
			delta[1] = current_offset;
			delta[2] = current_length;
			delta_list.add(delta);
		   
			current_index  = current_offset + current_length;
			current_offset += current_length;	
		}
		
		System.out.println("Current index is " + current_index);
		System.out.println("List size is " + ratio_list.size());
	
		//System.out.println("Merged ratio number is " + current_number);
	    //System.out.println("Merged segment length is " + current_length);

		return delta_list;
	}
	
	
	public static ArrayList getStringList(int[] value)
	{
		ArrayList string_list = new ArrayList();

		ArrayList histogram_list = getHistogram(value);
		int min_value = (int) histogram_list.get(0);
		int[] histogram = (int[]) histogram_list.get(1);
		int value_range = (int) histogram_list.get(2);
		int[] string_table = getRankTable(histogram);

		// Get a string list from a set of deltas.
		value[0] = value_range / 2;
		for(int i = 1; i < value.length; i++)
			value[i] -= min_value;
		byte[] string = new byte[value.length * 16];
		int bit_length = packStrings2(value, string_table, string);

		string_list.add(min_value);
		string_list.add(bit_length);
		string_list.add(string_table);

		double zero_percentage = value.length;
		if(histogram.length > 1)
		{
			int min_histogram_value = Integer.MAX_VALUE;
			for(int k = 0; k < histogram.length; k++)
				if(histogram[k] < min_histogram_value)
					min_histogram_value = histogram[k];
			zero_percentage -= min_histogram_value;
		}
		zero_percentage /= bit_length;
		if(zero_percentage > .5)
		{
			// This is an unclipped string, so we do not expect the trailing byte.
			// This method does not look for a trailing byte.
			byte[] compression_string = compressZeroStrings(string, bit_length);
			string_list.add(compression_string);
		} 
		else
		{
			// See above.
			byte[] compression_string = compressOneStrings(string, bit_length);
			string_list.add(compression_string);
		}

		return string_list;
	}

	// Packing and compressing arrays where the first value
	// is not a code.
	public static ArrayList getStringList2(int[] value)
	{
		ArrayList string_list = new ArrayList();

		ArrayList histogram_list = getHistogram(value);
		int min_value = (int) histogram_list.get(0);
		int[] histogram = (int[]) histogram_list.get(1);
		int[] string_table = getRankTable(histogram);

		for(int i = 0; i < value.length; i++)
			value[i] -= min_value;
		byte[] string = new byte[value.length * 16];
		int bit_length = packStrings2(value, string_table, string);

		string_list.add(min_value);
		string_list.add(bit_length);
		string_list.add(string_table);

		double zero_percentage = value.length;
		if(histogram.length > 1)
		{
			int min_histogram_value = Integer.MAX_VALUE;
			for(int k = 0; k < histogram.length; k++)
				if(histogram[k] < min_histogram_value)
					min_histogram_value = histogram[k];
			zero_percentage -= min_histogram_value;
		}
		zero_percentage /= bit_length;
		if(zero_percentage > .5)
		{
			byte[] compression_string = compressZeroStrings(string, bit_length);
			string_list.add(compression_string);
		} else
		{
			byte[] compression_string = compressOneStrings(string, bit_length);
			string_list.add(compression_string);
		}

		return string_list;
	}

	public static ArrayList getStringList2(byte[] value)
	{
		ArrayList string_list = new ArrayList();

		ArrayList histogram_list = getHistogram(value);
		int min_value = (int) histogram_list.get(0);
		int[] histogram = (int[]) histogram_list.get(1);
		int[] string_table = getRankTable(histogram);

		for(int i = 0; i < value.length; i++)
			value[i] -= min_value;
		byte[] string = new byte[value.length * 16];
		int bit_length = packStrings2(value, string_table, string);

		string_list.add(min_value);
		string_list.add(bit_length);
		string_list.add(string_table);

		double zero_percentage = value.length;
		if(histogram.length > 1)
		{
			int min_histogram_value = Integer.MAX_VALUE;
			for(int k = 0; k < histogram.length; k++)
				if(histogram[k] < min_histogram_value)
					min_histogram_value = histogram[k];
			zero_percentage -= min_histogram_value;
		}
		zero_percentage /= bit_length;
		if(zero_percentage > .5)
		{
			byte[] compression_string = compressZeroStrings(string);
			string_list.add(compression_string);
		} else
		{
			byte[] compression_string = compressOneStrings(string);
			string_list.add(compression_string);
		}

		return string_list;
	}

	public static int getCompressionAmount(byte[] string, int bit_length, int transform_type)
	{
		int positive = 0;
		int negative = 0;
		byte mask = 1;

		int byte_length = bit_length / 8;
		int n = byte_length;
		if(transform_type == 0)
		{
			int previous = 1;
			for(int i = 0; i < n; i++)
			{
				for(int j = 0; j < 8; j++)
				{
					int k = string[i] & mask << j;

					if(k != 0 && previous != 0)
						positive++;
					else if(k != 0 && previous == 0)
						previous = 1;
					else if(k == 0 && previous != 0)
						previous = 0;
					else if(k == 0 && previous == 0)
					{
						negative++;
						previous = 1;
					}
				}
			}

			int remainder = bit_length % 8;
			if(remainder != 0)
			{
				for(int i = 0; i < remainder; i++)
				{
					int j = string[byte_length] & mask << i;

					if(j != 0 && previous != 0)
						positive++;
					else if(j != 0 && previous == 0)
						previous = 1;
					else if(j == 0 && previous != 0)
						previous = 0;
					else if(j == 0 && previous == 0)
					{
						negative++;
						previous = 1;
					}
				}
			}
		} 
		else
		{
			int previous = 0;
			for(int i = 0; i < n; i++)
			{
				for(int j = 0; j < 8; j++)
				{
					int k = string[i] & mask << j;

					if(k == 0 && previous == 0)
						positive++;
					else if(k == 0 && previous == 1)
						previous = 0;
					else if(k != 0 && previous == 0)
						previous = 1;
					else if(k != 0 && previous != 0)
					{
						negative++;
						previous = 0;
					}
				}
			}

			int remainder = bit_length % 8;
			if(remainder != 0)
			{
				for(int i = 0; i < remainder; i++)
				{
					int j = string[byte_length] & mask << i;

					if(j == 0 && previous == 0)
						positive++;
					else if(j == 0 && previous == 1)
						previous = 0;
					else if(j != 0 && previous == 0)
						previous = 1;
					else if(j != 0 && previous != 0)
					{
						negative++;
						previous = 0;
					}
				}
			}
		}
		int total = positive - negative;

		return total;
	}
}