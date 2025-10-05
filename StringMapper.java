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
	 * @return unparameterized list with histogram and additional information.
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
	 * @return unparameterized list with histogram and additional information.
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
			} 
			else
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
				} else if(k > 7)
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
			} 
			else
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
		} 
		catch (Exception e)
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
	public static int[] compressZeroBits(byte src[], int size, byte dst[])
	{
		int[] result = new int[2];
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
	

	
	// One bit/one string functions.
	// One bits correspond to run bits in this implementation.
	public static int[] compressOneBits(byte src[], int size, byte dst[])
	{
		int[] result = new int[2];
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
				} else
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
			else if((src[k] & (mask << j)) != 0 && (i == size - 1)) // We're at the end of the string and we have an odd 1. // odd 1.
			{
				// Put a 0 down to signal that there is an odd 1.
				// This works for a single recursion but might fail in the other recursive
				// cases.
				// It actually fails more rarely than we expect.
				// Open question if it fails at all.
				current_bit++;
				if(current_bit == 8)
				{
					current_byte++;
					current_bit = 0;
				}
				// This is a flag to let us know there might be a problem later on.
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

	/*****************************************************************************/
	
	
	
	
	/**
	 * This applies a bitwise substitution iteratively that will expand or contract
	 * a bit string. It does not expect information in the trailing byte, which can
	 * is useful adding data bytes to raw data.
	 * 
	 * @param src  input bytes
	 * @param size original bit length
	 * @return byte array containing the result
	 */
	public static byte[] compressZeroStrings(byte[] src, int bitlength)
	{
		int limit = 15;
		try
		{
			int amount = getCompressionAmount(src, bitlength, 0);
			if(amount > 0)
			{
				// No compression, add a data byte.
				int bytelength = getBytelength(bitlength);
				byte[] dst     = new byte[bytelength];
				System.arraycopy(src, 0, dst, 0, bytelength - 1);
				setData(0, 0, bitlength, dst);
				return dst;
			} 
			else
			{
				byte[] buffer1 = new byte[src.length];
				byte[] buffer2 = new byte[src.length];

				int[] result            = compressZeroBits(src, bitlength, buffer1);
				int   compressed_length = result[0];
				amount                  = getCompressionAmount(buffer1, compressed_length, 0);
				if(amount >= 0)
				{
					// 1 iteration
					int    bytelength = getBytelength(compressed_length);
					byte[] dst        = new byte[bytelength];
					System.arraycopy(buffer1, 0, dst, 0, bytelength - 1);
					setData(0, 1, compressed_length, dst);
					
					return dst;
				} 
				else
				{
					int iterations = 1;
					while (amount < 0 && iterations < limit)
					{
						int previous_length = compressed_length;
						if(iterations % 2 == 1)
						{
							result            = compressZeroBits(buffer1, previous_length, buffer2);
							compressed_length = result[0];
							iterations++;
							amount = getCompressionAmount(buffer2, compressed_length, 0);
						} 
						else
						{
							result            = compressZeroBits(buffer2, previous_length, buffer1);
							compressed_length = result[0];
							iterations++;
							amount = getCompressionAmount(buffer1, compressed_length, 0);
						}
					}
					
					int    bytelength = getBytelength(compressed_length);
					byte[] dst        = new byte[bytelength];
					if(iterations % 2 == 0)
						System.arraycopy(buffer2, 0, dst, 0, bytelength - 1);
					else
						System.arraycopy(buffer1, 0, dst, 0, bytelength - 1);
					setData(0, iterations, compressed_length, dst);
					
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
	 * a bit string. It requires that the bit string length is contained in the
	 * trailing byte.
	 * 
	 * @param src input bytes
	 * @return byte array containing the result with a trailing data byte.
	 */
	public static byte[] compressZeroStrings(byte[] src)
	{
		int [] bit_table   = getBitTable();
		int    bit_length  = getBitlength(src);
		double zero_ratio  = getZeroRatio(src, bit_length, bit_table);
		int    zero_amount = getCompressionAmount(src, bit_length, 0);
		int    one_amount  = getCompressionAmount(src, bit_length, 1);

		int limit = 15;
		if(zero_amount >= 0)
		{
			if(one_amount < 0)
			{
				int iterations = getIterations(src);
				//System.out.println("compressZeroStrings: string with ratio " + String.format("%.4f", zero_ratio) + " and length " + bit_length + " and " + iterations + " previous iterations did not compress.");
				//System.out.println();
			}

			byte [] dst = src.clone();
			return dst;
		} 
		else
		{
			byte[] buffer1 = new byte[src.length];
			byte[] buffer2 = new byte[src.length];

			int[] result = compressZeroBits(src, bit_length, buffer1);
			int compressed_length = result[0];

			if(compressed_length - bit_length != zero_amount)
			{
				System.out.println("Actual amount " + (compressed_length - bit_length) + " was not equal to predicted amount " + zero_amount + " after initial iteration.");
				System.out.println();
			}

			zero_ratio  = getZeroRatio(buffer1, compressed_length, bit_table);
			zero_amount = getCompressionAmount(buffer1, compressed_length, 0);
			one_amount  = getCompressionAmount(buffer1, compressed_length, 1);

			if(zero_amount >= 0)
			{
				int    bytelength = getBytelength(compressed_length);
				byte[] dst        = new byte[bytelength];
				System.arraycopy(buffer1, 0, dst, 0, bytelength);
				setData(0, 1, compressed_length, dst);
				return dst;
			} 
			else
			{
				int iterations = 1;
				while(zero_amount < 0 && iterations < limit)
				{
					int previous_length = compressed_length;
					if(iterations % 2 == 1)
					{
						result = compressZeroBits(buffer1, previous_length, buffer2);
						compressed_length = result[0];

						iterations++;
						if(compressed_length - previous_length != zero_amount)
						{
							System.out.println("Actual amount " + (compressed_length - previous_length) + " was not equal to predicted amount " + zero_amount + " after  iteration " + iterations);
							System.out.println();
						}

						zero_ratio  = getZeroRatio(buffer2, compressed_length, bit_table);
						zero_amount = getCompressionAmount(buffer2, compressed_length, 0);
						one_amount  = getCompressionAmount(buffer2, compressed_length, 1);
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

						zero_ratio  = getZeroRatio(buffer1, compressed_length, bit_table);
						zero_amount = getCompressionAmount(buffer1, compressed_length, 0);
						one_amount  = getCompressionAmount(buffer1, compressed_length, 1);
					}
				}
				
				
				int    bytelength = getBytelength(compressed_length);
				byte[] dst        = new byte[bytelength];
				if(iterations % 2 == 0)
					System.arraycopy(buffer2, 0, dst, 0, bytelength - 1);
				else
					System.arraycopy(buffer1, 0, dst, 0, bytelength - 1);
				setData(0, iterations, compressed_length, dst);
				
				return dst;
			}
		}
	}

	// Version that doesn't take a chance on excess 0 bits in
	// recursive decompression.
	public static byte[] compressZeroStrings2(byte[] src)
	{
		int [] bit_table = getBitTable();

		int bit_length    = getBitlength(src);
		int zero_amount   = getCompressionAmount(src, bit_length, 0);
		

		int limit = 15;
		if(zero_amount >= 0)
		{
			byte[] dst = src.clone();
			return dst;
		} 
		else
		{
			byte[] buffer1 = new byte[src.length];
			byte[] buffer2 = new byte[src.length];

			int[] result = compressZeroBits(src, bit_length, buffer1);
			int compressed_length = result[0];

			if(compressed_length - bit_length != zero_amount)
			{
				System.out.println("Actual amount " + (compressed_length - bit_length) + " was not equal to predicted amount " + zero_amount + " after initial iteration.");
				System.out.println();
			}

			zero_amount = getCompressionAmount(buffer1, compressed_length, 0);
			
			if(zero_amount >= 0 || result[1] == 1)
			{
				int    bytelength = getBytelength(compressed_length);
				byte[] dst        = new byte[bytelength ];
				System.arraycopy(buffer1, 0, dst, 0, bytelength - 1);
				setData(0, 1, compressed_length, dst);
				return dst;
			} 
			else
			{
				int iterations = 1;
				while(zero_amount < 0 && iterations < limit && result[1] == 0)
				{
					int previous_length = compressed_length;
					if(iterations % 2 == 1)
					{
						result = compressZeroBits(buffer1, previous_length, buffer2);
						compressed_length = result[0];

						iterations++;
						if(compressed_length - previous_length != zero_amount)
						{
							System.out.println("Actual amount " + (compressed_length - previous_length) + " was not equal to predicted amount " + zero_amount + " after  iteration " + iterations);
							System.out.println();
						}
						zero_amount = getCompressionAmount(buffer2, compressed_length, 0);
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
					
						zero_amount = getCompressionAmount(buffer1, compressed_length, 0);
					}
				}
				
				int bytelength = getBytelength(compressed_length);
				byte[] dst = new byte[bytelength];
				if(iterations % 2 == 0)
					System.arraycopy(buffer2, 0, dst, 0, bytelength - 1);
				else
					System.arraycopy(buffer1, 0, dst, 0, bytelength - 1);
				setData(0, iterations, compressed_length, dst);
				return dst;
			}
		}
	}
	
	/**
	 * This applies a bitwise substitution iteratively that will expand or contract
	 * a bit string. It requires that the bit string length is contained in the
	 * trailing byte.
	 * 
	 * @param src input bytes
	 * @return byte array containing the result with a trailing data byte.
	 */
	public static byte[] compressZeroStrings2(byte[] src, int number_of_iterations)
	{
		int [] bit_table   = getBitTable();
		int    bit_length  = getBitlength(src);
		double zero_ratio  = getZeroRatio(src, bit_length, bit_table);
		int    zero_amount = getCompressionAmount(src, bit_length, 0);
		int    one_amount  = getCompressionAmount(src, bit_length, 1);

		int limit = number_of_iterations;
		if(zero_amount >= 0)
		{
			if(one_amount < 0)
			{
				int iterations = getIterations(src);
				//System.out.println("compressZeroStrings: string with ratio " + String.format("%.4f", zero_ratio) + " and length " + bit_length + " and " + iterations + " previous iterations did not compress.");
				//System.out.println();
			}

			byte [] dst = src.clone();
			return dst;
		} 
		else
		{
			byte[] buffer1 = new byte[src.length];
			byte[] buffer2 = new byte[src.length];

			int[] result = compressZeroBits(src, bit_length, buffer1);
			int compressed_length = result[0];

			if(compressed_length - bit_length != zero_amount)
			{
				System.out.println("Actual amount " + (compressed_length - bit_length) + " was not equal to predicted amount " + zero_amount + " after initial iteration.");
				System.out.println();
			}

			zero_ratio  = getZeroRatio(buffer1, compressed_length, bit_table);
			zero_amount = getCompressionAmount(buffer1, compressed_length, 0);
			one_amount  = getCompressionAmount(buffer1, compressed_length, 1);

			if(zero_amount >= 0)
			{
				int    bytelength = getBytelength(compressed_length);
				byte[] dst        = new byte[bytelength];
				System.arraycopy(buffer1, 0, dst, 0, bytelength);
				setData(0, 1, compressed_length, dst);
				return dst;
			} 
			else
			{
				int iterations = 1;
				while(zero_amount < 0 && iterations < limit)
				{
					int previous_length = compressed_length;
					if(iterations % 2 == 1)
					{
						result = compressZeroBits(buffer1, previous_length, buffer2);
						compressed_length = result[0];

						iterations++;
						if(compressed_length - previous_length != zero_amount)
						{
							System.out.println("Actual amount " + (compressed_length - previous_length) + " was not equal to predicted amount " + zero_amount + " after  iteration " + iterations);
							System.out.println();
						}

						zero_ratio  = getZeroRatio(buffer2, compressed_length, bit_table);
						zero_amount = getCompressionAmount(buffer2, compressed_length, 0);
						one_amount  = getCompressionAmount(buffer2, compressed_length, 1);
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

						zero_ratio  = getZeroRatio(buffer1, compressed_length, bit_table);
						zero_amount = getCompressionAmount(buffer1, compressed_length, 0);
						one_amount  = getCompressionAmount(buffer1, compressed_length, 1);
					}
				}
				
				
				int    bytelength = getBytelength(compressed_length);
				byte[] dst        = new byte[bytelength];
				if(iterations % 2 == 0)
					System.arraycopy(buffer2, 0, dst, 0, bytelength - 1);
				else
					System.arraycopy(buffer1, 0, dst, 0, bytelength - 1);
				setData(0, iterations, compressed_length, dst);
				
				return dst;
			}
		}
	}


	/**
	 * This applies a bitwise substitution iteratively that will expand or contract
	 * a bit string. It requires that the bit string length is contained in the
	 * trailing byte.
	 * 
	 * @param src input bytes
	 * @return byte array containing the result with a trailing data byte.
	 */
	public static byte[] decompressZeroStrings(byte[] src)
	{
		int iterations = getIterations(src);
		int bitlength  = getBitlength(src);
		int type       = getType(src);
		
		// If it's not a zero type string, we'll still process it.
		// This will compress most one type strings.
		if(type == 1)
		{
			System.out.println("decompressZeroStrings(): Not zero type string.");
			System.out.println();
			iterations -= 16;
		}

		// If it was not compressed, we clone dst from src.
		if(iterations == 0)
		{
			byte [] dst = src.clone();
			return dst;
		}

		if(iterations == 1)
		{
			int bytelength = getBytelength(bitlength);
			bytelength    *= 2;
			byte[] buffer  = new byte[bytelength];
			int uncompressed_length = decompressZeroBits(src, bitlength, buffer);
			bytelength = getBytelength(uncompressed_length);
			byte[] dst = new byte[bytelength];
			System.arraycopy(buffer, 0, dst, 0, bytelength - 1);
			setData(0, 0, uncompressed_length, dst);
			return dst;
		} 
		else if(iterations % 2 == 0)
		{
			// Create buffers large enough to handle
			// largest possible expansion.
			int bytelength = getBytelength(bitlength);
			double factor  = Math.pow(2, iterations);
			bytelength    *= factor;

			byte[] buffer1 = new byte[bytelength];
			byte[] buffer2 = new byte[bytelength];

			int uncompressed_length = decompressZeroBits(src, bitlength, buffer1);
			iterations--;
			while (iterations > 0)
			{
				int previous_length = uncompressed_length;
				if(iterations % 2 == 1)
					uncompressed_length = decompressZeroBits(buffer1, previous_length, buffer2); 
				else
					uncompressed_length = decompressZeroBits(buffer2, previous_length, buffer1);
				iterations--;
			}
			
			bytelength = getBytelength(uncompressed_length);
			byte[] dst = new byte[bytelength];
			System.arraycopy(buffer2, 0, dst, 0, bytelength - 1);
			setData(0, 0, uncompressed_length, dst);
			return dst;
		} 
		else
		{
			int bytelength = getBytelength(bitlength);
			double factor = Math.pow(2, iterations);
			bytelength   *= factor;

			byte[] buffer1 = new byte[bytelength];
			byte[] buffer2 = new byte[bytelength];

			int uncompressed_length = decompressZeroBits(src, bitlength, buffer1);
			iterations--;
			while (iterations > 0)
			{
				int previous_length = uncompressed_length;
				if(iterations % 2 == 0)
					uncompressed_length = decompressZeroBits(buffer1, previous_length, buffer2); 
			    else
					uncompressed_length = decompressZeroBits(buffer2, previous_length, buffer1);
				iterations--;
			}
			
			bytelength = getBytelength(uncompressed_length);
			byte[] dst = new byte[bytelength];
			System.arraycopy(buffer1, 0, dst, 0, bytelength - 1);
			setData(0, 0, uncompressed_length, dst);
	
			return dst;
		}
	}
	
	
	/***********************************************************************************/
	
	
	// This version does not expect a trailing data byte.
	// Useful for adding data bytes to raw data.
	public static byte[] compressOneStrings(byte[] src, int bitlength)
	{
		int limit = 16;
		
		try
		{
			int amount = getCompressionAmount(src, bitlength, 1);
			if(amount > 0)
			{
				byte [] dst = src.clone();
				return dst;
			} 
			else
			{
				byte[] buffer1        = new byte[src.length];
				byte[] buffer2        = new byte[src.length];
				int[] result          = compressOneBits(src, bitlength, buffer1);
				int compressed_length = result[0];
				amount                = getCompressionAmount(buffer1, compressed_length, 1);
				
				if(amount >= 0)
				{
					int bytelength = getBytelength(compressed_length);
					byte [] dst    = new byte[bytelength];
					System.arraycopy(buffer1, 0, dst, 0, bytelength - 1);
					setData(1, 1, compressed_length, dst);
					return dst;
				} 
				else
				{
					int iterations = 1;
					while (amount < 0 && iterations < limit)
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

					int bytelength = getBytelength(compressed_length);
					byte [] dst    = new byte[bytelength];
					if(iterations % 2 == 0)
						System.arraycopy(buffer2, 0, dst, 0, bytelength - 1);
					else
						System.arraycopy(buffer1, 0, dst, 0, bytelength - 1);
					setData(1, iterations, compressed_length, dst);
					return dst;
				}
			}
		} 
		catch (Exception e)
		{
			System.out.println(e.toString());
			System.out.println("Exiting compressOneStrings with an exception.");
			byte[] dst = new byte[0];
			return dst;
		}
	}

	
	
	/**
	 * This applies a bitwise substitution iteratively that will expand or contract
	 * a bit string, limited to 15 iterations of the transform.
	 * 
	 * @param src input bytes with trailing byte containing bit length
	 * @return byte array containing the result
	 */
	public static byte[] compressOneStrings(byte[] src)
	{
		int [] bit_table   = getBitTable();
		int    bit_length  = getBitlength(src);
		double zero_ratio  = getZeroRatio(src, bit_length, bit_table);
		int    zero_amount = getCompressionAmount(src, bit_length, 0);
		int    one_amount  = getCompressionAmount(src, bit_length, 1);
		
		int limit = 16;
		if(one_amount >= 0)
		{
			byte [] dst = src.clone();
			return dst;
		} 
		else
		{
			byte[] buffer1 = new byte[src.length];
			byte[] buffer2 = new byte[src.length];

			// The result contains the length of the bit string contained in the buffer,
			// and a number signifying whether the original string was well formed or not 
			// (all valid last inputs in recursive process).
			int[] result          = compressOneBits(src, bit_length, buffer1);
			int compressed_length = result[0];

			zero_ratio  = getZeroRatio(buffer1, compressed_length, bit_table);
			zero_amount = getCompressionAmount(buffer1, compressed_length, 0);
			one_amount  = getCompressionAmount(buffer1, compressed_length, 1);

			if(one_amount >= 0)
			{
				if(zero_amount < 0)
					System.out.println("compressOneStrings: anomalous string after 1 iteration.");
				int    bytelength = getBytelength(compressed_length);
                byte[] dst        = new byte[bytelength];
				System.arraycopy(buffer1, 0, dst, 0, bytelength - 1);
				setData(1, 1, compressed_length, dst);
				return dst;
			} 
			else
			{
				int iterations = 1;
				while(one_amount < 0 && iterations < limit)
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

						zero_ratio  = getZeroRatio(buffer2, compressed_length, bit_table);
						zero_amount = getCompressionAmount(buffer2, compressed_length, 0);
						one_amount  = getCompressionAmount(buffer2, compressed_length, 1);
					} 
					else
					{
						result            = compressOneBits(buffer2, previous_length, buffer1);
						compressed_length = result[0];
						iterations++;

						if(compressed_length - previous_length != one_amount)
						{
							System.out.println("Actual amount " + (compressed_length - previous_length) + " was not equal to predicted amount " + one_amount + " after  iteration " + iterations);
							System.out.println();
						}

						zero_ratio  = getZeroRatio(buffer1, compressed_length, bit_table);
						zero_amount = getCompressionAmount(buffer1, compressed_length, 0);
						one_amount  = getCompressionAmount(buffer1, compressed_length, 1);
					}
				}
				
				if(zero_amount < 0)
					System.out.println("compressOneStrings: anomalous string after " + iterations + " iterations.");	

				int    bytelength = getBytelength(compressed_length);
				byte[] dst        = new byte[bytelength];
				if(iterations % 2 == 0)
					System.arraycopy(buffer2, 0, dst, 0, bytelength - 1);
				else
					System.arraycopy(buffer1, 0, dst, 0, bytelength - 1);
				setData(1, iterations, compressed_length, dst);
				return dst;
			}
		}
	}

	/**
	 * This applies a bitwise substitution iteratively that will expand or contract
	 * a bit string.  It limits the possible number of iterations.
	 * 
	 * @param src input bytes with trailing byte containing bit length
	 * @return byte array containing the result
	 */
	public static byte[] compressOneStrings2(byte[] src, int number_of_iterations)
	{
		int    bitlength  = getBitlength(src);
		int    one_amount = getCompressionAmount(src, bitlength, 1);
		int    iterations = 0;
		
		int limit = number_of_iterations;
		if(one_amount >= 0)
		{
			byte [] dst = src.clone();
			return dst;
		} 
		else
		{
			byte[] buffer1 = new byte[src.length];
			byte[] buffer2 = new byte[src.length];

			int[] result          = compressOneBits(src, bitlength, buffer1);
			int compressed_length = result[0];
			one_amount            = getCompressionAmount(buffer1, compressed_length, 1);
            iterations++;
			if(one_amount >= 0 || iterations == limit)
			{
				int    bytelength = getBytelength(compressed_length);
                byte[] dst        = new byte[bytelength];
				System.arraycopy(buffer1, 0, dst, 0, bytelength - 1);
				setData(1, 1, compressed_length, dst);
				return dst;
			} 
			else
			{
				while(one_amount < 0 && iterations < limit)
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
						one_amount  = getCompressionAmount(buffer2, compressed_length, 1);
					} 
					else
					{
						result            = compressOneBits(buffer2, previous_length, buffer1);
						compressed_length = result[0];
						iterations++;

						if(compressed_length - previous_length != one_amount)
						{
							System.out.println("Actual amount " + (compressed_length - previous_length) + " was not equal to predicted amount " + one_amount + " after  iteration " + iterations);
							System.out.println();
						}
						one_amount  = getCompressionAmount(buffer1, compressed_length, 1);
					}
				}
				
				int    bytelength = getBytelength(compressed_length);
				byte[] dst        = new byte[bytelength];
				if(iterations % 2 == 0)
					System.arraycopy(buffer2, 0, dst, 0, bytelength - 1);
				else
					System.arraycopy(buffer1, 0, dst, 0, bytelength - 1);
				setData(1, iterations, compressed_length, dst);
				return dst;
			}
		}
	}
	
	/**
	 * This applies a bitwise substitution iteratively that will expand or contract
	 * a bit string. It requires that the bit string length is contained in the
	 * trailing byte.
	 * 
	 * @param src input bytes
	 * @return byte array containing the result with a trailing data byte.
	 */
	public static byte[] decompressOneStrings(byte[] src)
	{
		int iterations = getIterations(src);
		if(iterations <= 16)
		{
			byte [] dst = src.clone();
			return  dst;
		}
		
		int bitlength  = getBitlength(src);
		
		iterations -= 16;
        
		if(iterations == 1)
		{
			int bytelength = getBytelength(bitlength);
			bytelength    *= 2;
			byte[] buffer  = new byte[bytelength];
			
			int uncompressed_length = decompressOneBits(src, bitlength, buffer);
			bytelength              = getBytelength(uncompressed_length);
			byte[] dst              = new byte[bytelength];
			System.arraycopy(buffer, 0, dst, 0, bytelength - 1);
			setData(1, 0, uncompressed_length, dst);
			return dst;	
		} 
		else if(iterations % 2 == 0)
		{
			int bytelength = getBytelength(bitlength);
			
			double factor = Math.pow(2, iterations);
			bytelength *= factor;

			byte[] buffer1 = new byte[bytelength];
			byte[] buffer2 = new byte[bytelength];

			int uncompressed_length = decompressOneBits(src, bitlength, buffer1);
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
			
			bytelength = getBytelength(uncompressed_length);
			byte[] dst = new byte[bytelength];
			System.arraycopy(buffer2, 0, dst, 0, bytelength - 1);
			setData(1, 0, uncompressed_length, dst);
			return dst;
		} 
		else
		{
			int bytelength = getBytelength(bitlength);
			double factor  = Math.pow(2, iterations);
			bytelength    *= factor;
			byte[] buffer1 = new byte[bytelength];
			byte[] buffer2 = new byte[bytelength ];

			int uncompressed_length = decompressOneBits(src, bitlength, buffer1);
			iterations--;
			while (iterations > 0)
			{
				int previous_length = uncompressed_length;
				if(iterations % 2 == 0)
					uncompressed_length = decompressOneBits(buffer1, previous_length, buffer2); 
				else
					uncompressed_length = decompressOneBits(buffer2, previous_length, buffer1);
				iterations--;
			}
			
			bytelength = getBytelength(uncompressed_length);
			byte[] dst = new byte[bytelength];
			System.arraycopy(buffer1, 0, dst, 0, bytelength - 1);
			setData(1, 0, uncompressed_length, dst);
			return dst;
		}
	}	
	
	/******************************************************************************************/
	
	public static byte[] compressStrings(byte[] string)
	{
		int type        = getType(string);
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
		int iterations = getIterations(string);
		if(iterations == 0 || iterations == 16)
			return string;
		
		int type  = getType(string);
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
	
	public static byte[] compressStrings2(byte[] string)
	{
		int [] bit_table = getBitTable();
		
		int type        = getType(string);
		if(type == 0)
		{
			byte[] compressed_string = compressZeroStrings2(string, 7);
			int    iterations        = getIterations(compressed_string);
			
			if(iterations == 0)
				return compressed_string;
			
			int bitlength = getBitlength(compressed_string);
			int amount    = getCompressionAmount(compressed_string, bitlength, 1);
			if(amount < 0)
			{
				byte [] compressed_string2 = compressOneStrings2(compressed_string, 1);
				int one_iterations = getIterations(compressed_string2);
				
				
				int adjusted_iterations = iterations + 8;
				
				//System.out.println("compressStrings2: adjusting iterations to " + adjusted_iterations);
				//System.out.println("One iterations was " + one_iterations);
				setIterations(adjusted_iterations, compressed_string2);
				
				/*
				byte [] decompressed_string = decompressStrings2(compressed_string2);
				System.out.println("compressStrings2: Processing anomalous string.");
				if(string.length != decompressed_string.length)
				{
				    System.out.println("Decompressed string not same length as original string.");
				    System.out.println();
				}
				else
				{
					boolean same = true;
			    	    
			    	    for(int i = 0; i < string.length; i++)
			    	    {
			    	    	    if(string[i] != decompressed_string[i])
			    	    	    {
			    	    	    	    same = false;
			    	    	    	    System.out.println("String and decompressed string are the same length " + string.length + " but differ at index " + i);
			    	    	    	    System.out.println();
			    	    	    }
			    	    }
			    	        
			    	    if(same)
			    	    {
			    	    	    System.out.println("String and decompressed string are the same.");
			    	    	    System.out.println();
			    	    }
			    	     
				}
				*/
				
				return compressed_string2;
			}
			
			return compressed_string;
		} 
		else if(type == 1)
		{
			byte[] compressed_string = compressOneStrings2(string, 7);
			return compressed_string;
		} 
		else
			return string;
	}
	
	public static byte[] decompressStrings2(byte[] string)
	{
		byte [] original_string = string.clone();
		
		int iterations = getIterations(string);
		if(iterations == 0 || iterations == 16)
			return original_string;
		
		int type  = getType(string);
		if(type == 0)
		{
			if(iterations < 8)
			{
				byte [] decompressed_string = decompressZeroStrings(string);
				return  decompressed_string;
			}
			else
			{
				int one_iterations  = 17;
				int zero_iterations = iterations - 8;
				
				
				/*
				setIterations(17, string);
				byte [] decompressed_string = decompressOneStrings(string);
				
				setIterations(iterations - 8, decompressed_string);
			    byte [] decompressed_string2 = decompressZeroStrings(decompressed_string);
			    */
				setIterations(one_iterations, original_string);
				byte [] decompressed_string = decompressOneStrings(original_string);
				setIterations(zero_iterations, decompressed_string);
				byte [] decompressed_string2 = decompressZeroStrings(decompressed_string);
				
				System.out.println("decompressStrings2: processing anomalous string.");
				System.out.println("Zero iterations was " + zero_iterations);
				System.out.println();
			    return decompressed_string2;
				
			}
		}  
		else if(type == 1)
		{
			byte[] decompressed_string = decompressOneStrings(string);
			return decompressed_string;
		} 
		else
			return string;
	}
		
	
	
	/**************************************************************************************/
	
	
	public static void printBits(byte[] string, int bitlength)
	{
	    int bytelength = bitlength / 8;
	    if(bitlength % 8 != 0)
	    	    bytelength++;
	    
	    byte [] mask = SegmentMapper.getPositiveMask();
	    int i = bytelength - 1;
	    if(bitlength % 8 != 0)
	    {
	    	    for(int j = bitlength % 8 - 1; j >= 0; j--)
	    	    {
	    	    	    if((string[i] & mask[j]) != 0)
	    	    	    	    System.out.print("1");
	    	    	    else
	    	    	    	    System.out.print("0");	    	    
	    	    }
	    	    System.out.print(" ");
	    	    i--;
	    }
	    
	    while(i >= 0)
	    {
	    	    for(int j = 7; j >= 0; j--)
	    	    {
	    	    	    if((string[i] & mask[j]) != 0)
	    	    	        System.out.print("1");
	    	        else
	    	    	        System.out.print("0");	
	    	    }
	    	    System.out.print(" ");
	    	    i--;
	    }
	    
	    System.out.println();
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
	
	public static void setIterations(int iterations, byte[] string)
	{
		byte mask = SegmentMapper.getLeadingMask(3);
		string[string.length - 1] &= mask;
		
		byte _iterations = (byte)iterations;
		string[string.length - 1] |= _iterations;
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
	
	public static int getBytelength(int bitlength)
	{
		int bytelength = bitlength / 8;
		if(bitlength % 8 != 0)
			bytelength++;
		bytelength++;
		
		return bytelength;
	}
	
	// This method modifies the input string by setting the data byte.
	public static void setData(int type, int iterations, int bitlength, byte[] string)
	{
	    if(type == 1)
	    	    iterations += 16;
	    int odd_bits = bitlength % 8;
	    byte extra_bits = 0;
	    if(odd_bits != 0)
	    	    extra_bits = (byte)(8 - odd_bits);
	    extra_bits <<= 5;
	    
	    string[string.length - 1]  = (byte)iterations;
	    string[string.length - 1] |= (byte)extra_bits;
	}	
	
		

	/**
	 * Creates a table containing the number of 0 bits in any byte value.
	 * 
	 * @return int [] bit_table with the number of 0 bits for any byte value.
	 */
	public static int[] getBitTable()
	{
		int[] table = new int[256];

		byte value = 0;
		byte mask = 1;
		int sum = 0;
		for(int i = 0; i < 256; i++)
		{
			for(int j = 0; j < 8; j++)
			{
				int k = value & mask << j;
				if(k == 0)
					sum++;
			}
			table[i] = sum;
			sum = 0;
			value++;
		}
		return table;
	}

	public static double getZeroRatio(byte[] string, int bit_length, int[] table)
	{
		int byte_length = bit_length / 8;
		int zero_sum = 0;
		int one_sum = 0;

		int n = byte_length;

		for(int i = 0; i < n; i++)
		{
			int j = (int) string[i];
			if(j < 0)
				j += 256;
			zero_sum += table[j];
			one_sum += 8 - table[j];
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
		ratio /= zero_sum + one_sum;

		return ratio;
	}


	public static double getZeroRatio(byte[] string, int bit_offset, int bit_length, int [] bit_table)
	{
		byte[] shifted_string = SegmentMapper.shiftRight(string, bit_offset);
		double ratio          = getZeroRatio(shifted_string, bit_length, bit_table);
		
		return ratio;
	}

	public static ArrayList<Double> getRatioList(byte[] string, int segment_length)
	{
		ArrayList<Double> ratio_list = new ArrayList<Double>();

		// Number of segments to check, assuming the last byte is meta-data.
		int number_of_segments = (string.length - 1) * 8 - segment_length;
		int current_bit = 0;
		int current_byte = 0;
		byte mask = 1;

		for(int i = 0; i < number_of_segments; i++)
		{
			int zero_sum = 0;
			int one_sum = 0;

			int segment_bit = current_bit;
			int segment_byte = current_byte;

			for(int j = 0; j < segment_length; j++)
			{
				int k = string[segment_byte] & (mask << segment_bit);
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
			ratio /= (zero_sum + one_sum);
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
	
	public static ArrayList getStringList(int[] value, boolean compress)
	{
		ArrayList string_list    = new ArrayList();

		ArrayList histogram_list = getHistogram(value);
		int       min_value      = (int) histogram_list.get(0);
		int[]     histogram      = (int[]) histogram_list.get(1);
		int value_range          = (int) histogram_list.get(2);
		int[] string_table       = getRankTable(histogram);

		// Pack the delta values as unary strings.
		value[0] = value_range / 2;
		for(int i = 1; i < value.length; i++)
			value[i] -= min_value;
		byte[] string = new byte[value.length * 16];
		int bitlength = packStrings2(value, string_table, string);

		string_list.add(min_value);
		string_list.add(bitlength);
		string_list.add(string_table);

		// We can calculate the zero ratio of unmodified unary
		// strings from their histogram.
		double zero_ratio = value.length;
		if(histogram.length > 1)
		{
			int min_histogram_value = Integer.MAX_VALUE;
			for(int k = 0; k < histogram.length; k++)
				if(histogram[k] < min_histogram_value)
					min_histogram_value = histogram[k];
			zero_ratio -= min_histogram_value;
		}
		zero_ratio /= bitlength;
		
		if(compress)
		{
			if(zero_ratio >= .5)
			{
				// Since this is an unclipped string in a buffer,
				// we use the method that does not use a trailing byte.
				byte[] compression_string = compressZeroStrings(string, bitlength);
				string_list.add(compression_string);
			} 
			else
			{
				byte[] compression_string = compressOneStrings(string, bitlength);
				string_list.add(compression_string);
			}
		}
		else
		{
		    // Sometimes a compressable string will compress more 
			// when it's segmented if it isn't compressed to start with, so we
			// have an option where we do not attempt to compress the string.
			int bytelength             = getBytelength(bitlength);
			byte [] compression_string = new byte[bytelength];
			for(int i = 0; i < bytelength - 1; i++)
				compression_string[i] = string[i];
			if(zero_ratio >= .5)
				setData(0, 0, bitlength, compression_string);
			else
				setData(1, 0, bitlength, compression_string);
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
		} 
		else
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
		} 
		else
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