import java.util.*;
import java.util.zip.*;
import java.lang.Math.*;
import java.math.*;

public class CodeMapper
{
	public static int getHuffmanBitlength(byte [] src)
	{
		ArrayList histogram_list = StringMapper.getHistogram(src);
		int []    histogram      = (int[])histogram_list.get(1);
		
		int n = histogram.length;
		ArrayList <Integer> frequency_list = new ArrayList <Integer>();
	    for(int k = 0; k < n; k++)
	        frequency_list.add(histogram[k]);
	    Collections.sort(frequency_list, Comparator.reverseOrder());
	    int [] frequency = new int[n];
	    for(int k = 0; k < n; k++)
	    	    frequency[k] = frequency_list.get(k);
	    byte [] huffman_length = getHuffmanLength2(frequency);
	    int  [] huffman_code   = getCanonicalCode(huffman_length);
	    
	    int bitlength = getCost(huffman_length, frequency);
		return bitlength;
	}
	
	public static int getHuffmanBitlength(int [] src)
	{
		ArrayList histogram_list = StringMapper.getHistogram(src);
		int []    histogram      = (int[])histogram_list.get(1);
		
		int n = histogram.length;
		ArrayList <Integer> frequency_list = new ArrayList <Integer>();
	    for(int k = 0; k < n; k++)
	        frequency_list.add(histogram[k]);
	    Collections.sort(frequency_list, Comparator.reverseOrder());
	    int [] frequency = new int[n];
	    for(int k = 0; k < n; k++)
	    	    frequency[k] = frequency_list.get(k);
	    byte [] huffman_length = getHuffmanLength2(frequency);
	    int  [] huffman_code   = getCanonicalCode(huffman_length);
	    
	    int bitlength = getCost(huffman_length, frequency);
		return bitlength;
	}
	
	// Byte input, integer length codes--sufficient to do deflate-style huffman coding.
	// We need integer length code words (longer than original input) to support unary 
	// string encoding, or any coding that involves longer codes than the input.
	public static int packCode(byte src[], int table[], int[] code, byte[] length, byte dst[])
	{
		int k = 0;
		int current_bit = 0;

		for(int i = 0; i < src.length; i++)
		{
			int j = src[i];
			
			if(j < 0)
			    j += 256;

			k     = table[j];

			int code_word       = code[k];
			int code_length     = length[k];
			int offset          = current_bit % 8;
			code_word         <<= offset;
			int or_length       = code_length + offset;
			int number_of_bytes = or_length / 8;
			if(or_length % 8 != 0)
				number_of_bytes++;
			int current_byte    = current_bit / 8;
			if(number_of_bytes == 1)
				dst[current_byte] |= (byte) (code_word & 0x00ff);
			else
			{
				for(int m = 0; m < number_of_bytes - 1; m++)
				{
					dst[current_byte] |= (byte) (code_word & 0x00ff);
					current_byte++;
					code_word >>= 8;
				}
				if(current_byte < dst.length)
					dst[current_byte] = (byte) (code_word & 0x00ff);
			}
			current_bit += code_length;
		}

		int bit_length = current_bit;
		return bit_length;
	}

	public static ArrayList packCode(byte src[], int table[], int[] code, byte[] length)
	{
	    // Assume we are not expanding the source string.
		byte [] buffer = new byte[src.length * 4];
		
		int n = code.length;

		int current_bit = 0;
		for(int i = 0; i < src.length; i++)
		{
			int j = src[i];
			if(j < 0)
				j += 256;
			if(j > table.length - 1)
			{
				// Don't see how this can happen, but setting it to the least probable
				// value will prevent the program from halting.
				System.out.println("Index is larger than rank table length.");
				System.out.println("Index is " + j + ", rank table length is " + table.length);
				System.out.println("Source length is " + src.length + ", source index is " + i);
				j = table.length - 1;
			}
			
			int k = table[j];
			
			int code_word = code[k];
			int code_length = length[k];
			int offset = current_bit % 8;
			code_word <<= offset;
			int or_length = code_length + offset;
			int number_of_bytes = or_length / 8;
			if(or_length % 8 != 0)
				number_of_bytes++;
			int current_byte = current_bit / 8;
			if(number_of_bytes == 1)
				buffer[current_byte] |= (byte) (code_word & 0x00ff);	
			else
			{
				for(int m = 0; m < number_of_bytes - 1; m++)
				{
					buffer[current_byte] |= (byte) (code_word & 0x00ff);
					current_byte++;
					code_word >>= 8;
			    }
				buffer[current_byte] = (byte) (code_word & 0x00ff);
			}
			current_bit += code_length;
		}
		
        // We do an extra copy to clip the string,
		// but that's less expensive computationally than trying to 
		// do a bit by bit parse into an exactly sized buffer for 
		// the last source element and avoids a code-specific solution,
		// for example, something that only works with Huffman codes.
		int bitlength = current_bit;
		int number_of_bytes = bitlength / 8;
		if(bitlength % 8 != 0)
			number_of_bytes++;
		byte [] dst = new byte[number_of_bytes];
		for(int i = 0; i < dst.length; i++)
			dst[i] = buffer[i];
		
		// Adding code along with length to keep it non-code specific.
		// Most huffman coding schemes only need the code lengths.
		ArrayList result = new ArrayList();
		result.add(dst);
		result.add(bitlength);
		result.add(table);
		result.add(code);
		result.add(length);
		result.add(src.length);
	    
		return    result;
	}
	
	public static ArrayList packCode(int src[], int table[], int[] code, byte[] length)
	{
		byte [] buffer = new byte[src.length * 4];
		
		int n = code.length;

		int current_bit = 0;
		for(int i = 0; i < src.length; i++)
		{
			int j = src[i];
			int k = table[j];
			int code_word = code[k];
			int code_length = length[k];
			int offset = current_bit % 8;
			code_word <<= offset;
			int or_length = code_length + offset;
			int number_of_bytes = or_length / 8;
			if(or_length % 8 != 0)
				number_of_bytes++;
			int current_byte = current_bit / 8;
			if(number_of_bytes == 1)
			{
				buffer[current_byte] |= (byte) (code_word & 0x00ff);	
			}
			else
			{
				for(int m = 0; m < number_of_bytes - 1; m++)
				{
					buffer[current_byte] |= (byte) (code_word & 0x00ff);
					current_byte++;
					code_word >>= 8;
				}
				buffer[current_byte] = (byte) (code_word & 0x00ff);
			}
			current_bit += code_length;
		}
		
		// Clip the buffer data.
		int bitlength = current_bit;
		int number_of_bytes = bitlength / 8;
		if(bitlength % 8 != 0)
			number_of_bytes++;
		byte [] dst = new byte[number_of_bytes];
		for(int i = 0; i < dst.length; i++)
			dst[i] = buffer[i];
		
		ArrayList result = new ArrayList();
		result.add(dst);
		result.add(bitlength);
		result.add(table);
		result.add(code);
		result.add(length);
		result.add(src.length);
	    
		return    result;
	}
	
	// Accepts integer input.
	public static int packCode(int src[], int table[], int[] code, byte[] length, byte dst[])
	{
		int n = code.length;

		int current_bit = 0;
		for(int i = 0; i < src.length; i++)
		{
			int j = src[i];
			int k = table[j];
			int code_word = code[k];
			int code_length = length[k];
			int offset = current_bit % 8;
			code_word <<= offset;
			int or_length = code_length + offset;
			int number_of_bytes = or_length / 8;
			if(or_length % 8 != 0)
				number_of_bytes++;
			int current_byte = current_bit / 8;
			if(number_of_bytes == 1)
			{
				dst[current_byte] |= (byte) (code_word & 0x00ff);	
			}
			else
			{
				for(int m = 0; m < number_of_bytes - 1; m++)
				{
					dst[current_byte] |= (byte) (code_word & 0x00ff);
					current_byte++;
					code_word >>= 8;
				}
				dst[current_byte] = (byte) (code_word & 0x00ff);
			}
			current_bit += code_length;
		}

		int bit_length = current_bit;
		return bit_length;
	}

	// Method that supports longer lengths.
	public static int packCode(int src[], int table[], int[] code, int[] length, byte dst[])
	{
		int current_bit = 0;
		for(int i = 0; i < src.length; i++)
		{
			int j = src[i];
			int k = table[j];
			int code_word = code[k];
			int code_length = length[k];
			int offset = current_bit % 8;
			code_word <<= offset;
			int or_length = code_length + offset;
			int number_of_bytes = or_length / 8;
			if(or_length % 8 != 0)
				number_of_bytes++;
			int current_byte = current_bit / 8;

			if(number_of_bytes == 1)
				dst[current_byte] |= (byte) (code_word & 0x00ff);
			else
			{
				for(int m = 0; m < number_of_bytes - 1; m++)
				{
					dst[current_byte] |= (byte) (code_word & 0x00ff);
					current_byte++;
					code_word >>= 8;
				}
				dst[current_byte] = (byte) (code_word & 0x00ff);
			}
			current_bit += code_length;
		}

		int bit_length = current_bit;
		return bit_length;
	}

	/**************************************************************************************/
	// Methods that support longer codes.

	public static int packCode(int src[], int table[], long[] code, int[] length, byte dst[])
	{
		int current_bit = 0;
		for(int i = 0; i < src.length; i++)
		{
			int j = src[i];
			int k = table[j];
			long code_word = code[k];
			int code_length = length[k];
			int offset = current_bit % 8;
			code_word <<= offset;
			int or_length = code_length + offset;
			int number_of_bytes = or_length / 8;
			if(or_length % 8 != 0)
				number_of_bytes++;
			int current_byte = current_bit / 8;
			if(number_of_bytes == 1)
				dst[current_byte] |= (byte) (code_word & 0x00ff);
			else
			{
				for(int m = 0; m < number_of_bytes - 1; m++)
				{
					dst[current_byte] |= (byte) (code_word & 0x00ff);
					current_byte++;
					code_word >>= 8;
				}
				dst[current_byte] = (byte) (code_word & 0x00ff);
			}
			current_bit += code_length;
		}

		int bit_length = current_bit;
		return bit_length;
	}

	public static int packCode(int src[], int table[], BigInteger[] code, int[] length, byte dst[])
	{
		int current_bit = 0;
		for(int i = 0; i < src.length; i++)
		{
			int j = src[i];
			int k = table[j];

			BigInteger code_word = code[k];
			int code_length = length[k];

			int shift = current_bit % 8;

			code_word = code_word.shiftLeft(shift);

			int or_length = code_length + shift;

			int number_of_bytes = or_length / 8;
			if(or_length % 8 != 0)
				number_of_bytes++;

			int current_byte = current_bit / 8;
			shift = 0;

			for(int m = 0; m < number_of_bytes; m++)
			{
				BigInteger shifted_code_word = code_word.shiftRight(shift);

				long mask_value   = 255;
				BigInteger mask   = BigInteger.ONE;
				mask              = mask.valueOf(mask_value);
				shifted_code_word = shifted_code_word.and(mask);
				try
				{
					mask_value = 1;
					for(int n = 0; n < 8; n++)
					{
						mask = mask.valueOf(mask_value);
						BigInteger bit_value = shifted_code_word.and(mask);
						if(bit_value.compareTo(BigInteger.ZERO) != 0)
						{
							byte byte_mask = (byte) mask_value;
							dst[current_byte] |= byte_mask;
						}

						mask_value *= 2;
					}
					current_byte++;
					shift += 8;
				} 
				catch (Exception e)
				{
					System.out.println(e.toString());

					current_byte++;
					shift += 8;
				}
			}
			current_bit += code_length;
		}

		int bit_length = current_bit;
		return bit_length;
	}

	
	
	/********************************************************************************************************************/
	/* Unpacking routines.                                                                                              */
	/********************************************************************************************************************/
	
	
	// Basic byte input , byte output.
	public static int unpackCode(byte[] src, int[] table, int[] code, byte[] code_length, int bit_length, byte[] dst)
	{
		int[] inverse_table = new int[table.length];
		for(int i = 0; i < table.length; i++)
		{
			int j = table[i];
			inverse_table[j] = i;
		}

		int[] buffer = new int[dst.length];

		int max_length = code_length[code.length - 1];
		int max_bytes = max_length / 8;
		if(max_length % 8 != 0)
			max_bytes++;

		int current_bit = 0;
		int offset = 0;
		int current_byte = 0;
		int number_unpacked = 0;
		int dst_byte = 0;

		for(int i = 0; i < dst.length; i++)
		{
			int src_word = 0;

			for(int j = 0; j < max_bytes; j++)
			{
				int index = current_byte + j;

				if(index < src.length)
				{
					int src_byte = (int) src[index];
					if(src_byte < 0)
						src_byte += 256;
					if(j == 0)
						src_byte >>= offset;
					else
						src_byte <<= j * 8 - offset;
					src_word |= src_byte;
				}
			}

			if(offset != 0)
			{
				int index = current_byte + max_bytes;
				if(index < src.length)
				{
					int src_byte = (int) src[index];
					if(src_byte < 0)
						src_byte += 256;

					src_byte <<= max_bytes * 8 - offset;

					src_word |= src_byte;
				}
			}

			for(int j = 0; j < code.length; j++)
			{
				int code_word = code[j];
				int mask = -1;
				mask <<= code_length[j];
				mask = ~mask;

				int masked_src_word  = src_word & mask;
				int masked_code_word = code_word & mask;

				if(masked_src_word == masked_code_word)
				{
					buffer[dst_byte++] = inverse_table[j];
					number_unpacked++;
					current_bit += code_length[j];
					current_byte = current_bit / 8;
					offset = current_bit % 8;
					break;
				} 
				else if(j == code.length - 1)
					System.out.println("No match for prefix-free code at byte " + current_byte);
			}
		}
      
		for(int i = 0; i < dst.length; i++)
			dst[i] = (byte) (buffer[i]);
		return number_unpacked;
	}
	
	public static byte [] unpackCode(ArrayList pack_list)
	{
		byte [] src         = (byte [])pack_list.get(0);
		int     bitlength   = (int)    pack_list.get(1);
		int  [] table       = (int []) pack_list.get(2);
		int  [] code        = (int []) pack_list.get(3);
		byte [] code_length = (byte [])pack_list.get(4);
		int     n           = (int)    pack_list.get(5);
		
		System.out.println("Table length is " + table.length);
		System.out.println("Code length is " + code.length);
		System.out.println("Code length length is " + code_length.length);
		System.out.println("Number of output bytes is " + n);
		System.out.println();
		
		int[] inverse_table = new int[table.length];
		for(int i = 0; i < table.length; i++)
		{
			int j = table[i];
			inverse_table[j] = i;
		}
		
		int max_length = code_length[code.length - 1];
		int max_bytes = max_length / 8;
		if(max_length % 8 != 0)
			max_bytes++;
		
		int current_bit     = 0;
		int offset          = 0;
		int current_byte    = 0;
		int dst_byte        = 0;
		
		byte [] dst = new byte[n];
		for(int i = 0; i < n; i++)
		{
			int src_word = 0;

			for(int j = 0; j < max_bytes; j++)
			{
				int index = current_byte + j;

				if(index < src.length)
				{
					int src_byte = (int) src[index];
					if(src_byte < 0)
						src_byte += 256;
					if(j == 0)
						src_byte >>= offset;
					else
						src_byte <<= j * 8 - offset;
					src_word |= src_byte;
				}
			}

			if(offset != 0)
			{
				int index = current_byte + max_bytes;
				if(index < src.length)
				{
					int src_byte = (int) src[index];
					if(src_byte < 0)
						src_byte += 256;

					src_byte <<= max_bytes * 8 - offset;

					src_word |= src_byte;
				}
			}

			for(int j = 0; j < code.length; j++)
			{
				int code_word = code[j];
				int mask      = -1;
				mask        <<= code_length[j];
				mask          = ~mask;

				int masked_src_word  = src_word  & mask;
				int masked_code_word = code_word & mask;

				if(masked_src_word == masked_code_word)
				{
					dst[dst_byte++] = (byte)inverse_table[j];
					current_bit    += code_length[j];
					current_byte    = current_bit / 8;
					offset          = current_bit % 8;
					break;
				} 
				else if(j == code.length - 1)
			        	System.out.println("No match for prefix-free code at byte " + current_byte);
			}
		}

	    	return dst;
	}
	
	public static int [] unpackCode2(ArrayList pack_list)
	{
		byte [] src         = (byte [])pack_list.get(0);
		int     bitlength   = (int)    pack_list.get(1);
		int  [] table       = (int []) pack_list.get(2);
		int  [] code        = (int []) pack_list.get(3);
		byte [] code_length = (byte [])pack_list.get(4);
		int     n           = (int)    pack_list.get(5);
		
		int[] inverse_table = new int[table.length];
		for(int i = 0; i < table.length; i++)
		{
			int j = table[i];
			inverse_table[j] = i;
		}
		
		int max_length = code_length[code.length - 1];
		int max_bytes = max_length / 8;
		if(max_length % 8 != 0)
			max_bytes++;
		
		int current_bit     = 0;
		int offset          = 0;
		int current_byte    = 0;
		int dst_byte        = 0;
		
		int [] dst = new int[n];
		for(int i = 0; i < n; i++)
		{
			int src_word = 0;

			for(int j = 0; j < max_bytes; j++)
			{
				int index = current_byte + j;

				if(index < src.length)
				{
					int src_byte = (int) src[index];
					if(src_byte < 0)
						src_byte += 256;
					if(j == 0)
						src_byte >>= offset;
					else
						src_byte <<= j * 8 - offset;
					src_word |= src_byte;
				}
			}

			if(offset != 0)
			{
				int index = current_byte + max_bytes;
				if(index < src.length)
				{
					int src_byte = (int) src[index];
					if(src_byte < 0)
						src_byte += 256;

					src_byte <<= max_bytes * 8 - offset;

					src_word |= src_byte;
				}
			}

			for(int j = 0; j < code.length; j++)
			{
				int code_word = code[j];
				int mask      = -1;
				mask        <<= code_length[j];
				mask          = ~mask;

				int masked_src_word  = src_word  & mask;
				int masked_code_word = code_word & mask;

				if(masked_src_word == masked_code_word)
				{
					dst[dst_byte++] = inverse_table[j];
					current_bit    += code_length[j];
					current_byte    = current_bit / 8;
					offset          = current_bit % 8;
					break;
				} 
				else if(j == code.length - 1)
			        	System.out.println("No match for prefix-free code at byte " + current_byte);
			}
		}

	    	return dst;
	}
	
	
	
	// Byte input, integer output.
	// This is the method used in HuffmanWriter.
	public static int unpackCode(byte[] src, int[] table, int[] code, byte[] code_length, int bit_length, int[] dst)
	{
		boolean debug = false;
		
		int[] inverse_table = new int[table.length];
		for(int i = 0; i < table.length; i++)
		{
			int j = table[i];
			inverse_table[j] = i;
		}

		int max_length = code_length[code.length - 1];
		int max_bytes = max_length / 8;
		if(max_length % 8 != 0)
			max_bytes++;
		
		int current_bit     = 0;
		int offset          = 0;
		int current_byte    = 0;
		int number_unpacked = 0;
		int dst_byte        = 0;

		for(int i = 0; i < dst.length; i++)
		{
			int src_word = 0;

			for(int j = 0; j < max_bytes; j++)
			{
				int index = current_byte + j;

				if(index < src.length)
				{
					int src_byte = (int) src[index];
					if(src_byte < 0)
						src_byte += 256;
					if(j == 0)
						src_byte >>= offset;
					else
						src_byte <<= j * 8 - offset;
					src_word |= src_byte;
				}
			}

			if(offset != 0)
			{
				int index = current_byte + max_bytes;
				if(index < src.length)
				{
					int src_byte = (int) src[index];
					if(src_byte < 0)
						src_byte += 256;

					src_byte <<= max_bytes * 8 - offset;

					src_word |= src_byte;
				}
			}

			for(int j = 0; j < code.length; j++)
			{
				int code_word = code[j];
				int mask      = -1;
				mask        <<= code_length[j];
				mask          = ~mask;

				int masked_src_word  = src_word  & mask;
				int masked_code_word = code_word & mask;

				if(masked_src_word == masked_code_word)
				{
					dst[dst_byte++] = inverse_table[j];
					number_unpacked++;
					
					current_bit += code_length[j];
					current_byte = current_bit / 8;
					offset       = current_bit % 8;
					break;
				} 
				else if(j == code.length - 1)
			        	System.out.println("No match for prefix-free code at byte " + current_byte);
			}
		}

		if(debug)
		{
			// It might be that we have to take into account the data bit length, but
			// so far that does not seem to be the case.
			System.out.println("The bit length of the original data was " + bit_length);
			System.out.println("Bits unpacked was " + current_bit);
			int number_of_bytes = current_bit / 8;
			if(current_bit % 8 != 0)
				number_of_bytes++;
			System.out.println("Number of bytes scanned is " + number_of_bytes);
			System.out.println("Source length is " + src.length);
			System.out.println();
		}
		
		return number_unpacked;
	}

	/***************************************************************************************************************/
	// Methods that support longer codes.
	
	public static int unpackCode(byte src[], int table[], int[] code, int[] code_length, int bit_length, int dst[])
	{
		boolean debug = false;

		int number_of_different_values = table.length;
		int[] inverse_table = new int[number_of_different_values];
		for(int i = 0; i < number_of_different_values; i++)
		{
			int j = table[i];
			inverse_table[j] = i;
		}

		int number_of_codes = code.length;
		int max_length = code_length[code.length - 1];
		int max_bytes = max_length / 8;
		if(max_length % 8 != 0)
			max_bytes++;

		if(debug)
		{
			// Segment of the string bytes we want to debug.
			int start = 0;
			int stop = 10;

			System.out.println("Prefix free code:");
			for(int i = 0; i < code.length; i++)
			{
				for(int j = 0; j < code_length[i]; j++)
				{
					int mask = 1;
					mask <<= j;
					if((code[i] & mask) == 0)
						System.out.print(0);
					else
						System.out.print(1);
				}
				System.out.println();
			}
			System.out.println();

			System.out.println("Look-up table:");
			for(int i = 0; i < table.length; i++)
				System.out.println(i + " -> " + table[i]);
			;
			System.out.println();

			System.out.println("Bytes from delta string:");

			for(int i = start; i < stop; i++)
			{
				System.out.print(i + "  ");
				for(int j = 0; j < 8; j++)
				{
					byte src_mask = 1;
					src_mask <<= j;
					int src_bit = src_mask & src[i];
					if(src_bit == 0)
						System.out.print("0");
					else
						System.out.print("1");
				}
				System.out.println();
			}
			System.out.println();
		}

		int current_bit = 0;
		int offset = 0;
		int current_byte = 0;
		int number_unpacked = 0;
		int dst_byte = 0;

		boolean matched = true;

		for(int i = 0; i < dst.length; i++)
		{
			int src_word = 0;

			for(int j = 0; j < max_bytes; j++)
			{
				int index = current_byte + j;

				if(index < src.length)
				{
					int src_byte = (int) src[index];
					if(src_byte < 0)
						src_byte += 256;
					if(j == 0)
						src_byte >>= offset;
					else
						src_byte <<= j * 8 - offset;
					src_word |= src_byte;
				}
			}

			if(offset != 0)
			{
				int index = current_byte + max_bytes;
				if(index < src.length)
				{
					int src_byte = (int) src[index];
					if(src_byte < 0)
						src_byte += 256;

					src_byte <<= max_bytes * 8 - offset;

					src_word |= src_byte;
				}
			}

			for(int j = 0; j < code.length; j++)
			{
				int code_word = code[j];
				int mask = -1;
				mask <<= code_length[j];
				mask = ~mask;

				int masked_src_word = src_word & mask;
				int masked_code_word = code_word & mask;

				if(masked_src_word == masked_code_word)
				{
					dst[dst_byte++] = inverse_table[j];
					number_unpacked++;
					current_bit += code_length[j];
					current_byte = current_bit / 8;
					offset = current_bit % 8;
					break;
				} 
				else if(j == code.length - 1)
					System.out.println("No match for prefix-free code at byte " + current_byte);
			}
		}

		if(debug)
		{
			System.out.println("The bit length of the original data was " + bit_length);
			System.out.println("Bits unpacked was " + current_bit);
			System.out.println();
		}

		return number_unpacked;
	}

	public static int unpackCode(byte src[], int table[], long[] code, int[] code_length, int bit_length, int dst[])
	{
		int number_of_different_values = table.length;
		int[] inverse_table = new int[number_of_different_values];
		for(int i = 0; i < number_of_different_values; i++)
		{
			int j = table[i];
			inverse_table[j] = i;
		}

		int max_length = code_length[code.length - 1];
		int max_bytes = max_length / 8;
		if(max_length % 8 != 0)
			max_bytes++;

		int current_bit = 0;
		int offset = 0;
		int current_byte = 0;
		int number_unpacked = 0;
		int dst_byte = 0;

		for(int i = 0; i < dst.length; i++)
		{
			long src_word = 0;

			for(int j = 0; j < max_bytes; j++)
			{
				long src_byte = (long) src[current_byte + j];
				if(src_byte < 0)
					src_byte += 256;
				if(j == 0)
					src_byte >>= offset;
				else
					src_byte <<= j * 8 - offset;
				src_word |= src_byte;
			}

			if(offset != 0)
			{
				long src_byte = (long) src[current_byte + max_bytes];
				if(src_byte < 0)
					src_byte += 256;

				src_byte <<= max_bytes * 8 - offset;

				src_word |= src_byte;
			}

			for(int j = 0; j < code.length; j++)
			{
				long code_word = code[j];
				long mask = -1;
				mask <<= code_length[j];
				mask = ~mask;

				long masked_src_word = src_word & mask;
				long masked_code_word = code_word & mask;

				if(masked_src_word == masked_code_word)
				{
					dst[dst_byte++] = inverse_table[j];
					number_unpacked++;
					current_bit += code_length[j];
					current_byte = current_bit / 8;
					offset = current_bit % 8;
					break;
				} 
				else if(j == code.length - 1)
				{
					System.out.println("No match for prefix-free code at byte " + current_byte);
				}
			}
		}
		return number_unpacked;
	}

	public static int unpackCode(byte src[], int table[], BigInteger[] code, int[] code_length, int bit_length, int dst[])
	{
		boolean debug = false;

		int number_of_different_values = table.length;
		int[] inverse_table = new int[number_of_different_values];
		for(int i = 0; i < number_of_different_values; i++)
		{
			int j = table[i];
			inverse_table[j] = i;
		}

		int number_of_codes = code.length;
		int max_length = code_length[code.length - 1];
		int max_bytes = max_length / 8;
		if(max_length % 8 != 0)
			max_bytes++;

		if(debug)
		{
			// Segment of the string bytes we want to debug.
			int start = 0;
			int stop = 10;
			System.out.println("Bytes from delta string:");
			for(int i = start; i < stop; i++)
			{
				System.out.print(i + "  ");
				for(int j = 0; j < 8; j++)
				{
					byte src_mask = 1;
					src_mask <<= j;
					int src_bit = src_mask & src[i];
					if(src_bit == 0)
						System.out.print("0");
					else
						System.out.print("1");
				}
				System.out.println();
			}
			System.out.println();

		}

		int current_bit = 0;
		int offset = 0;
		int current_byte = 0;
		int number_unpacked = 0;
		int dst_byte = 0;

		for(int i = 0; i < dst.length; i++)
		{
			BigInteger src_word = BigInteger.ZERO;

			for(int j = 0; j < max_bytes; j++)
			{
				long src_byte = (long) src[current_byte + j];
				if(src_byte < 0)
					src_byte += 256;

				BigInteger addend = BigInteger.valueOf(src_byte);
				if(j == 0)
					addend = addend.shiftRight(offset);
				else
					addend = addend.shiftLeft(j * 8 - offset);
				src_word = src_word.add(addend);
			}

			if(offset != 0)
			{
				long src_byte = (long) src[current_byte + max_bytes];
				if(src_byte < 0)
					src_byte += 256;
				BigInteger addend = BigInteger.valueOf(src_byte);
				addend = addend.shiftLeft(max_bytes * 8 - offset);
				src_word = src_word.add(addend);
			}

			for(int j = 0; j < code.length; j++)
			{
				BigInteger code_word = code[j];
				BigInteger mask = BigInteger.ONE;
				BigInteger addend = BigInteger.TWO;
				for(int k = 1; k < code_length[j]; k++)
				{
					mask = mask.add(addend);
					addend = addend.multiply(BigInteger.TWO);
				}
				BigInteger masked_src_word = src_word.and(mask);

				if(code_word.compareTo(masked_src_word) == 0)
				{
					dst[dst_byte++] = inverse_table[j];
					number_unpacked++;
					current_bit += code_length[j];
					current_byte = current_bit / 8;
					offset = current_bit % 8;

					break;
				} else if(j == code.length - 1)
				{
					System.out.println("No match for prefix-free code.");
				}
			}
		}

		if(debug)
		{
			System.out.println("The length of the original bit string was " + bit_length);
			System.out.println("Bits unpacked was " + current_bit);
			System.out.println("Number of bytes was " + current_byte);
			System.out.println();
		}

		return number_unpacked;
	}

	public static long[] getUnaryCode(int n)
	{
		long[] code = new long[n];

		code[0] = 0;

		long addend = 1;
		for(int i = 1; i < n; i++)
		{
			code[i] = code[i - 1] + addend;
			addend *= 2;
		}
		return code;
	}

	public static BigInteger[] getBigUnaryCode(int n)
	{
		BigInteger[] code = new BigInteger[n];

		code[0] = BigInteger.ZERO;
		BigInteger addend = BigInteger.ONE;
		for(int i = 1; i < n; i++)
		{
			BigInteger value = code[i - 1];
			value = value.add(addend);
			code[i] = value;
			addend = addend.multiply(BigInteger.TWO);
		}
		return code;
	}

	public static int[] getUnaryLength(int n)
	{
		int[] length = new int[n];
		for(int i = 0; i < n; i++)
			length[i] = i + 1;
		length[n - 1]--;
		return length;
	}

	public static byte[] getHuffmanLength2(int[] frequency)
	{
		// The in-place processing is one of the
		// trickiest parts of this code, but we
		// don't want to modify the input so we'll
		// make a copy and work from that.
		int n = frequency.length;

		int[] w = new int[n];
		for(int i = 0; i < n; i++)
			w[i] = frequency[i];

		int leaf = n - 1;
		int root = n - 1;
		int next;

		// Create tree.
		for(next = n - 1; next > 0; next--)
		{
			// Find first child.
			if(leaf < 0 || (root > next && w[root] < w[leaf]))
			{
				// Use internal node and reassign w[next].
				w[next] = w[root];
				w[root] = next;
				root--;
			} else
			{
				// Use leaf node and reassign w[next].
				w[next] = w[leaf];
				leaf--;
			}

			// Find second child.
			if(leaf < 0 || (root > next && w[root] < w[leaf]))
			{
				// Use internal node and add to w[next].
				w[next] += w[root];
				w[root] = next;
				root--;
			} else
			{
				// Use leaf node and add to w[next].
				w[next] += w[leaf];
				leaf--;
			}
		}

		// Traverse tree from root down, converting parent pointers into
		// internal node depths.
		w[1] = 0;
		for(next = 2; next < n; next++)
			w[next] = w[w[next]] + 1;

		// Final pass to produce code lengths.
		int avail = 1;
		int used = 0;
		int depth = 0;

		root = 1;
		next = 0;

		while (avail > 0)
		{
			// Count internal nodes at each depth.
			while (root < n && w[root] == depth)
			{
				used++;
				root++;
			}

			// Assign as leaves any nodes that are not internal.
			while (avail > used)
			{
				w[next] = depth;
				next++;
				avail--;
			}

			// Reset variables.
			avail = 2 * used;
			used = 0;
			depth++;
		}

		// return w;

		byte[] code_length = new byte[n];
		for(int i = 0; i < n; i++)
			code_length[i] = (byte) w[i];

		return code_length;
	}

	public static int[] getHuffmanLength(int[] frequency)
	{
		// The in-place processing is one of the
		// trickiest parts of this code, but we
		// don't want to modify the input so we'll
		// make a copy and work from that.
		int n = frequency.length;

		int[] w = new int[n];
		for(int i = 0; i < n; i++)
			w[i] = frequency[i];

		int leaf = n - 1;
		int root = n - 1;
		int next;

		// Create tree.
		for(next = n - 1; next > 0; next--)
		{
			// Find first child.
			if(leaf < 0 || (root > next && w[root] < w[leaf]))
			{
				// Use internal node and reassign w[next].
				w[next] = w[root];
				w[root] = next;
				root--;
			} else
			{
				// Use leaf node and reassign w[next].
				w[next] = w[leaf];
				leaf--;
			}

			// Find second child.
			if(leaf < 0 || (root > next && w[root] < w[leaf]))
			{
				// Use internal node and add to w[next].
				w[next] += w[root];
				w[root] = next;
				root--;
			} else
			{
				// Use leaf node and add to w[next].
				w[next] += w[leaf];
				leaf--;
			}
		}

		// Traverse tree from root down, converting parent pointers into
		// internal node depths.
		w[1] = 0;
		for(next = 2; next < n; next++)
			w[next] = w[w[next]] + 1;

		// Final pass to produce code lengths.
		int avail = 1;
		int used  = 0;
		int depth = 0;

		root = 1;
		next = 0;

		while (avail > 0)
		{
			// Count internal nodes at each depth.
			while (root < n && w[root] == depth)
			{
				used++;
				root++;
			}

			// Assign as leaves any nodes that are not internal.
			while (avail > used)
			{
				w[next] = depth;
				next++;
				avail--;
			}

			// Reset variables.
			avail = 2 * used;
			used = 0;
			depth++;
		}

		return w;
	}

	public static int[] getCanonicalCode(byte[] length)
	{
		int n = length.length;

		int[] code = new int[n];
		int[] shifted_code = new int[n];
		int max_length = length[n - 1];

		code[0] = 0;
		shifted_code[0] = 0;
		for(int i = 1; i < n; i++)
		{
			code[i] = (int) (code[i - 1] + Math.pow(2, max_length - length[i - 1]));
			int shift = max_length - length[i];
			shifted_code[i] = code[i] >> shift;
		}

		int[] reversed_code = new int[n];
		reversed_code[0] = 0;
		for(int i = 1; i < n; i++)
		{
			int code_word = shifted_code[i];
			int code_length = length[i];
			int code_mask = 1;

			for(int j = 0; j < code_length; j++)
			{
				int result = code_word & (code_mask << j);
				if(result != 0)
				{
					int shift = (code_length - 1) - j;
					reversed_code[i] |= code_mask << shift;
				}
			}
		}
		return reversed_code;
	}

	public static int[] getCanonicalCode(int[] length)
	{
		int n = length.length;

		int[] code = new int[n];
		int[] shifted_code = new int[n];
		int max_length = length[n - 1];

		code[0] = 0;
		shifted_code[0] = 0;
		for(int i = 1; i < n; i++)
		{
			code[i] = (int) (code[i - 1] + Math.pow(2, max_length - length[i - 1]));
			int shift = max_length - length[i];
			shifted_code[i] = code[i] >> shift;
		}

		int[] reversed_code = new int[n];
		reversed_code[0] = 0;
		for(int i = 1; i < n; i++)
		{
			int code_word = shifted_code[i];
			int code_length = length[i];
			int code_mask = 1;

			for(int j = 0; j < code_length; j++)
			{
				int result = code_word & (code_mask << j);
				if(result != 0)
				{
					int shift = (code_length - 1) - j;
					reversed_code[i] |= code_mask << shift;
				}
			}
		}
		return reversed_code;
	}

	public static long[] getCanonicalCode2(int[] length)
	{
		int n = length.length;

		long[] code = new long[n];
		long[] shifted_code = new long[n];
		int max_length = length[n - 1];

		code[0] = 0;
		shifted_code[0] = 0;
		for(int i = 1; i < n; i++)
		{
			code[i] = (int) (code[i - 1] + Math.pow(2, max_length - length[i - 1]));
			int shift = max_length - length[i];
			shifted_code[i] = code[i] >> shift;
		}

		long[] reversed_code = new long[n];
		reversed_code[0] = 0;
		for(int i = 1; i < n; i++)
		{
			long code_word = shifted_code[i];
			int code_length = length[i];
			long code_mask = 1;

			for(int j = 0; j < code_length; j++)
			{
				long result = code_word & (code_mask << j);
				if(result != 0)
				{
					int shift = (code_length - 1) - j;
					reversed_code[i] |= code_mask << shift;
				}
			}
		}
		return reversed_code;
	}

	public static BigInteger[] getBigCanonicalCode(int[] length)
	{
		int n = length.length;

		BigInteger[] code = new BigInteger[n];
		BigInteger[] shifted_code = new BigInteger[n];
		int max_length = length[n - 1];

		code[0] = BigInteger.ZERO;
		shifted_code[0] = BigInteger.ZERO;
		for(int i = 1; i < n; i++)
		{
			code[i] = code[i - 1];
			int j = (int) (Math.pow(2, max_length - length[i - 1]));
			BigInteger addend = BigInteger.valueOf(j);
			code[i] = code[i].add(addend);

			int shift = max_length - length[i];
			shifted_code[i] = code[i].shiftRight(shift);
		}

		BigInteger[] reversed_code = new BigInteger[n];
		reversed_code[0] = BigInteger.ZERO;
		for(int i = 1; i < n; i++)
		{
			BigInteger code_word = shifted_code[i];
			int code_length = length[i];
			BigInteger code_mask = BigInteger.ONE;
			reversed_code[i] = BigInteger.ZERO;

			for(int j = 0; j < code_length; j++)
			{
				BigInteger result = code_word.and(code_mask.shiftLeft(j));
				if(result.compareTo(BigInteger.ZERO) != 0)
				{
					int shift = (code_length - 1) - j;
					reversed_code[i] = reversed_code[i].or(code_mask.shiftLeft(shift));
				}
			}
		}

		return reversed_code;
	}

	public static double getZeroRatio(int[] code, int[] length, int[] frequency)
	{
		int n = code.length;
		double ratio = 0;

		int number_of_zeros = 0;
		int number_of_ones = 0;

		for(int i = 0; i < n; i++)
		{
			int mask = 1;
			for(int j = 0; j < length[i]; j++)
			{
				int bit_mask = mask << j;
				int bit = code[i] & bit_mask;
				if(bit == 0)
					number_of_zeros++;
				else
					number_of_ones++;
			}
		}
		ratio = number_of_zeros;
		ratio /= number_of_zeros + number_of_ones;

		return ratio;
	}

	public static double log2(double value)
	{
		double result = (Math.log(value) / Math.log(2.));
		return result;
	}

	public static double getShannonLimit(int[] frequency)
	{
		int n = frequency.length;
		int sum = 0;
		for(int i = 0; i < n; i++)
			sum += frequency[i];
		double[] weight = new double[n];
		for(int i = 0; i < n; i++)
		{
			weight[i] = frequency[i];
			weight[i] /= sum;
		}

		double limit = 0;
		for(int i = 0; i < n; i++)
		{
			if(weight[i] != 0)
				limit -= frequency[i] * log2(weight[i]);
		}

		return limit;
	}

	public static int getCost(int[] length, int[] frequency)
	{
		int n = length.length;
		int cost = 0;

		for(int i = 0; i < n; i++)
		{
			cost += length[i] * frequency[i];
		}
		return cost;
	}

	public static int getCost(byte[] length, int[] frequency)
	{
		int n = length.length;
		int cost = 0;

		for(int i = 0; i < n; i++)
		{
			cost += length[i] * frequency[i];
		}
		return cost;
	}

	public static ArrayList packLengthTable(byte[] length)
	{
		ArrayList result = new ArrayList();
		int       n       = length.length;
		result.add(n);

		byte init_value = length[0];
		result.add(init_value);

		byte[] length_delta = new byte[n - 1];
		byte   max_delta    = 0;
		for(int i = 0; i < n - 1; i++)
		{
			length_delta[i] = (byte) (length[i + 1] - length[i]);
			if(length_delta[i] > max_delta)
				max_delta = length_delta[i];
		}
		result.add(max_delta);

		if(max_delta == 1)
		{
			int byte_length = (n - 1) / 8;
			if((n - 1) % 8 != 0)
				byte_length++;

			byte[] packed_length = new byte[byte_length];
			byte[] mask = SegmentMapper.getPositiveMask();

			int m = 0;
			outer: for(int k = 0; k < byte_length; k++)
			{
				for(n = 0; n < 8; n++)
				{
					if(m == length_delta.length)
						break outer;
					if(length_delta[m] == 1)
						packed_length[k] |= mask[n];
					m++;
				}
			}
			result.add(packed_length);
		} 
		else if(max_delta == 2)
		{
			int byte_length = (n - 1) / 4;
			if((n - 1) % 4 != 0)
				byte_length++;
			byte[] packed_length = new byte[byte_length];

			int m = 0;
			outer: for(int k = 0; k < byte_length; k++)
			{
				for(n = 0; n < 8; n += 2)
				{
					if(m == length_delta.length)
						break outer;

					byte value = length_delta[m];
					if(value > 0)
					{
						value <<= n;
						packed_length[k] |= value;
					}
					m++;
				}
			}
			result.add(packed_length);
		} 
		else if(max_delta == 3)
		{
			int byte_length = (n - 1) / 2;
			if((n - 1) % 2 != 0)
				byte_length++;
			byte[] packed_length = new byte[byte_length];

			int m = 0;
			outer: for(int k = 0; k < byte_length; k++)
			{
				for(n = 0; n < 8; n += 4)
				{
					if(m == length_delta.length)
						break outer;

					byte value = length_delta[m];
					if(value > 0)
					{
						value <<= n;
						packed_length[k] |= value;
					}
					m++;
				}
			}
			result.add(packed_length);
		} 
		else
		{
			result.add(length_delta);
		}
		return result;
	}

	public static byte[] unpackLengthTable(ArrayList length_list)
	{
		int n = (int) length_list.get(0);
		byte init_value = (byte) length_list.get(1);
		byte max_delta = (byte) length_list.get(2);

		byte[] packed_delta = (byte[]) length_list.get(3);

		byte[] length = new byte[n];

		if(max_delta > 4)
		{
			if(packed_delta.length != n - 1)
				System.out.println("Packed deltas are not the right length 1.");
			else
			{
				length[0] = init_value;
				for(int i = 1; i < n; i++)
					length[i] = (byte) (length[i - 1] + packed_delta[i - 1]);
			}
		} 
		else if(max_delta == 1)
		{
			int byte_length = (n - 1) / 8;
			if((n - 1) % 8 != 0)
				byte_length++;
			if(packed_delta.length != byte_length)
				System.out.println("Packed deltas are not the right length 2.");
			else
			{
				byte[] mask = SegmentMapper.getPositiveMask();

				length[0] = init_value;
				int k = 1;
				outer: for(int i = 0; i < byte_length; i++)
				{
					for(int j = 0; j < 8; j++)
					{
						if(k == n)
							break outer;
						if((packed_delta[i] & mask[j]) != 0)
							length[k] = (byte) (length[k - 1] + 1);
						else
							length[k] = length[k - 1];
						k++;
					}
				}
			}
		} 
		else if(max_delta == 2)
		{
			int byte_length = (n - 1) / 4;
			if((n - 1) % 4 != 0)
				byte_length++;
			if(packed_delta.length != byte_length)
				System.out.println("Packed deltas are not the right length 3.");
			else
			{
				byte[] mask = new byte[4];
				mask[0] = 3;
				for(int i = 1; i < 4; i++)
					mask[i] = (byte) (mask[i - 1] << 2);

				int k = 1;
				outer: for(int i = 0; i < byte_length; i++)
				{
					for(int j = 0; j < 8; j += 2)
					{
						if(k == n)
							break outer;

						byte value = (byte) (packed_delta[i] & mask[j / 2]);
						value >>= j;
						value &= 3;
						length[k] = (byte) (length[k - 1] + value);
						k++;
					}
				}
			}
		} 
		else
		{
			int byte_length = (n - 1) / 2;
			if((n - 1) % 2 != 0)
				byte_length++;
			if(packed_delta.length != byte_length)
				System.out.println("Packed deltas are not the right length 4.");
			else
			{
				byte[] mask = new byte[2];
				mask[0] = 15;
				mask[1] = (byte) (mask[0] << 4);

				length[0] = init_value;
				int k = 1;
				outer: for(int i = 0; i < byte_length; i++)
				{
					for(int j = 0; j < 8; j += 4)
					{
						if(k == n)
							break outer;

						byte value = (byte) (packed_delta[i] & mask[j / 4]);
						value >>= j;
						value &= 15;
						length[k] = (byte) (length[k - 1] + value);
						k++;
					}
				}
			}
		}
		return length;
	}

	public static byte[] unpackLengthTable(int n, byte init_value, byte max_delta, byte[] packed_delta)
	{
		byte[] length = new byte[n];
		length[0] = init_value;

		if(max_delta > 4)
		{
			if(packed_delta.length != n - 1)
				System.out.println("Packed deltas are not the right length 1.");
			else
			{
				for(int i = 1; i < n; i++)
					length[i] = (byte) (length[i - 1] + packed_delta[i - 1]);
			}
		} 
		else if(max_delta == 1)
		{
			int byte_length = (n - 1) / 8;
			if((n - 1) % 8 != 0)
				byte_length++;
			if(packed_delta.length != byte_length)
				System.out.println("Packed deltas are not the right length 2.");
			else
			{
				byte[] mask = SegmentMapper.getPositiveMask();
				int k = 1;
				outer: for(int i = 0; i < byte_length; i++)
				{
					for(int j = 0; j < 8; j++)
					{
						if(k == n)
							break outer;
						if((packed_delta[i] & mask[j]) != 0)
							length[k] = (byte) (length[k - 1] + 1);
						else
							length[k] = length[k - 1];
						k++;
					}
				}
			}
		} 
		else if(max_delta == 2)
		{
			int byte_length = (n - 1) / 4;
			if((n - 1) % 4 != 0)
				byte_length++;
			if(packed_delta.length != byte_length)
				System.out.println("Packed deltas are not the right length 3.");
			else
			{
				byte[] mask = new byte[4];
				mask[0] = 3;
				for(int i = 1; i < 4; i++)
					mask[i] = (byte) (mask[i - 1] << 2);

				int k = 1;
				outer: for(int i = 0; i < byte_length; i++)
				{
					for(int j = 0; j < 8; j += 2)
					{
						if(k == n)
							break outer;

						byte value = (byte) (packed_delta[i] & mask[j / 2]);
						value >>= j;
						value &= 3;
						length[k] = (byte) (length[k - 1] + value);
						k++;
					}
				}
			}
		} 
		else
		{
			System.out.println("Max delta is " + max_delta);
			int byte_length = (n - 1) / 2;
			if((n - 1) % 2 != 0)
				byte_length++;
			if(packed_delta.length != byte_length)
			{
				System.out.println("Packed deltas are not the right length 4.");
				System.out.println("Expected length is " + byte_length + ", actual length is " + packed_delta.length);
			}
			else
			{
				byte[] mask = new byte[2];
				mask[0] = 15;
				mask[1] = (byte) (mask[0] << 4);

				length[0] = init_value;
				int k = 1;
				outer: for(int i = 0; i < byte_length; i++)
				{
					for(int j = 0; j < 8; j += 4)
					{
						if(k == n)
							break outer;

						byte value = (byte) (packed_delta[i] & mask[j / 4]);
						value >>= j;
						value &= 15;
						length[k] = (byte) (length[k - 1] + value);
						k++;
					}
				}
			}
		}
		return length;
	}
	
	
	
	public static BigInteger [] getRangeQuotient(byte[] src, Hashtable <Integer, Integer> table, int [] f, int m)
	{
	    int [] s = new int[f.length];
		
		int sum = 0;
		for(int i = 0; i < f.length; i++)
		{
			s[i] = sum;
			sum    += f[i];
		}
		
		BigInteger [] offset = new BigInteger[2];
		offset[0] = BigInteger.ZERO;
		offset[1] = BigInteger.ONE;
		
		BigInteger [] range  = new BigInteger[2];
		range[0] = BigInteger.ONE;
		range[1] = BigInteger.ONE;
		
	
		int    n       = src.length;
	    
		for(int i = 0; i < n; i++)
	    {
	    	    int j = src[i];
	    	    if(j < 0)
	    	    	    j += 256;
	    	    j = table.get(j);
	    	   
	    	    BigInteger [] addend = new BigInteger[] {range[0], range[1]};
	    	    
	    	    BigInteger factor = BigInteger.ONE;
	    	    factor            = factor.valueOf(s[j]);
	    	    addend[0]         = addend[0].multiply(factor);
	    	    factor            = factor.valueOf(m);
	    	    addend[1]         = addend[1].multiply(factor);
	    	    
	    	    BigInteger gcd = addend[0].gcd(addend[1]);
	    	    if(gcd.compareTo(BigInteger.ONE) == 1)
	    	    {
	    	    	    //System.out.println("Factoring addend.");
	    	    	    addend[0] = addend[0].divide(gcd);
			    addend[1] = addend[1].divide(gcd);
	    	    }
	    	    
	    	    offset[0] = offset[0].multiply(addend[1]);
	    	    addend[0] = addend[0].multiply(offset[1]);
	    	    offset[1] = offset[1].multiply(addend[1]);
	    	    offset[0] = offset[0].add(addend[0]);
	    	    
	    	    gcd = offset[0].gcd(offset[1]);
	    	    if(gcd.compareTo(BigInteger.ONE) == 1)
	    	    {
	    	    	    //System.out.println("Factoring offset.");
	    	    	    offset[0] = offset[0].divide(gcd);
			    	offset[1] = offset[1].divide(gcd);
	    	    }
	    	   
			    
            factor   = factor.valueOf(f[j]);
	    	    range[0] = range[0].multiply(factor);
	    	    factor   = factor.valueOf(m);
	    	    range[1] = range[1].multiply(factor);
	    	    
	    	    gcd = range[0].gcd(range[1]);
	    	    if(gcd.compareTo(BigInteger.ONE) == 1)
	    	    {
	    	    	    //System.out.println("Factoring range.");
	    	    	    range[0] = range[0].divide(gcd);
			    	range[1] = range[1].divide(gcd);
	    	    }
	    }
	
		
		// Make sure range and offset have same denominator.
		if(offset[1].compareTo(range[1]) != 0)
		{
			BigInteger range_factor = range[1];
			BigInteger offset_factor = offset[1];
			
			offset[0] = offset[0].multiply(range_factor);
			offset[1] = offset[1].multiply(range_factor);
			
			offset[0] = offset[0].multiply(range_factor);
			offset[1] = offset[1].multiply(range_factor);	
		}
		
		BigInteger [] value = new BigInteger[] {offset[0], offset[1]};
		BigInteger gcd = value[0].gcd(value[1]);
		
        
        BigInteger max_gcd = gcd;  
        BigInteger largest_index = BigInteger.ZERO;
       
        int number_of_searches = 0;
        for(BigInteger index = BigInteger.ONE; index.compareTo(range[0]) == -1; index = index.add(BigInteger.ONE))
	    {
	    	    value[0] = value[0].add(BigInteger.ONE);
	    	    
	    	    gcd = value[0].gcd(value[1]);
	        if(gcd.compareTo(max_gcd) == 1)
	        {
        	        max_gcd = gcd;
        	        largest_index = index;
	        }
	    	    number_of_searches++;
	    }
        
        System.out.println("Number of searches for greatest divisor was " + number_of_searches);
        System.out.println();
        value[0] = offset[0].add(largest_index);
        value[1] = offset[1];
        value[0] = value[0].divide(max_gcd);
        value[1] = value[1].divide(max_gcd);
        
        return value;
		
	}

	// Requires a < b.
	public static int gcd(int a, int b) 
	{
		if (b == 0) 
			return a;
		return gcd(b, a % b);
	}
	
	
	public static long gcd(long a, long b) 
	{
		if (b == 0) 
			return a;
		return gcd(b, a % b);
	}
	
	

	
	// Adaptive probability.
	public static BigInteger [] getRangeQuotient2(byte[] src, Hashtable <Integer, Integer> table, int [] frequency, int sum_of_frequencies)
	{
		int [] f = frequency.clone();
		int    m = sum_of_frequencies;
		
	    int [] s = new int[f.length];
		
		int sum = 0;
		for(int i = 0; i < f.length; i++)
		{
			s[i] = sum;
			sum    += f[i];
		}
		
		BigInteger [] offset = new BigInteger[2];
		offset[0]            = BigInteger.ZERO;
		offset[1]            = BigInteger.ONE;
		
		BigInteger [] range  = new BigInteger[2];
		range[0]             = BigInteger.ONE;
		range[1]             = BigInteger.ONE;
		
		int    n       = src.length;
	    
		for(int i = 0; i < n; i++)
	    {
	    	    int j = src[i];
	    	    if(j < 0)
	    	    	    j += 256;
	    	    j = table.get(j);
	    	   
	    	    BigInteger [] addend = new BigInteger[] {range[0], range[1]};
	    	    
	    	    BigInteger factor = BigInteger.ONE;
	    	    factor            = factor.valueOf(s[j]);
	    	    addend[0]         = addend[0].multiply(factor);
	    	    factor            = factor.valueOf(m);
	    	    addend[1]         = addend[1].multiply(factor);
	    	    
	    	    BigInteger gcd = addend[0].gcd(addend[1]);
	    	    if(gcd.compareTo(BigInteger.ONE) == 1)
	    	    {
	    	    	    addend[0] = addend[0].divide(gcd);
			    addend[1] = addend[1].divide(gcd);
	    	    }
	    	    
	    	    offset[0] = offset[0].multiply(addend[1]);
	    	    addend[0] = addend[0].multiply(offset[1]);
	    	    offset[1] = offset[1].multiply(addend[1]);
	    	    offset[0] = offset[0].add(addend[0]);
	    	    
	    	    
	    	    gcd = offset[0].gcd(offset[1]);
	    	    if(gcd.compareTo(BigInteger.ONE) == 1)
	    	    {
	    	    	    offset[0] = offset[0].divide(gcd);
			    	offset[1] = offset[1].divide(gcd);
	    	    }
			    
            factor   = factor.valueOf(f[j]);
	    	    range[0] = range[0].multiply(factor);
	    	    factor   = factor.valueOf(m);
	    	    range[1] = range[1].multiply(factor);
	    	    
	    	    gcd = range[0].gcd(range[1]);
	    	    if(gcd.compareTo(BigInteger.ONE) == 1)
	    	    {
	    	    	    range[0] = range[0].divide(gcd);
			    	range[1] = range[1].divide(gcd);
	    	    }
	    	    f[j]--;
	    	    m--;
	    	    for(int k = j + 1; k < s.length; k++)
	    	    {
	    	    	    s[k]--;
	    	    }
	    }
	
		/*
		if(offset[1].compareTo(range[1]) != 0)
		{
			BigInteger range_factor = range[1];
			BigInteger offset_factor = offset[1];
			
			offset[0] = offset[0].multiply(range_factor);
			offset[1] = offset[1].multiply(range_factor);
			
			range[0] = offset[0].multiply(offset_factor);
			range[1] = offset[1].multiply(offset_factor);	
		}
		
		BigInteger [] value = new BigInteger[] {offset[0], offset[1]};
		BigInteger gcd = value[0].gcd(value[1]);
		
        BigInteger max_gcd = gcd;  
        BigInteger largest_index = BigInteger.ZERO;
        int number_of_searches = 0;
        
        System.out.println("Offset 0 is " + offset[0]);
        System.out.println("Offset 1 is " + offset[1]);
        System.out.println("Range 0 is " + range[0]);
        System.out.println("Range 1 is " + range[1]);
        for(BigInteger index = BigInteger.ONE; index.compareTo(range[0]) == -1; index = index.add(BigInteger.ONE))
	    {
	    	    value[0] = value[0].add(BigInteger.ONE);
	    	    gcd = value[0].gcd(value[1]);
	    	    
	    	    
	    	    if(gcd.compareTo(max_gcd) == 1)
	    	    {
	    	        max_gcd = gcd;
        	        largest_index = index;
	    	    }
	    	    number_of_searches++;
	    }
	    
        
        System.out.println("Number of searches for greatest divisor was " + number_of_searches);
        System.out.println();
		
        value[0] = offset[0].add(largest_index);
        value[1] = offset[1];
        value[0] = value[0].divide(max_gcd);
        value[1] = value[1].divide(max_gcd);
        */
		
		BigInteger []  value       = new BigInteger[] {offset[0], offset[1]};
		if(offset[1].compareTo(range[1]) != 0)
		{
			BigInteger range_factor  = range[1];
			BigInteger offset_factor = offset[1];
			
			offset[0]                = offset[0].multiply(range_factor);
			offset[1]                = offset[1].multiply(range_factor);
			
			range[0]                 = range[0].multiply(offset_factor);
			range[1]                 = range[1].multiply(offset_factor);
		    System.out.println("Cross multiplied offset and range.");
		    
		    BigInteger stop = offset[0].add(range[0]);
		    
		    if(offset_factor.compareTo(range_factor) >= 0)
		    {
		    	    value[0] = offset[0].divide(offset_factor);
		    	    value[1] = offset[1].divide(offset_factor);
		    	     
		    }
		    else
		    {
		    	    value[0] = offset[0].divide(range_factor);
	    	        value[1] = offset[1].divide(range_factor);
		    }
		    
		    
		}
		
		// If we want to establish a preponderance of zero bits in our
		// numerator, we would now look for the number in the interval 
		// that divides by two the most times.  For now we simply return the offset.
		
		//System.out.println("Offset 0 is " + offset[0] + ", offset 1 is " + offset[1]);
		//System.out.println("Range 0 is " + range[0] + ", range 1 is " + range[1]);
	    //System.out.println("Value 0 is " + value[0] + ", value 1 is " + value[1]);
		
        return value;	
	}
	
	

	// Adaptive probability.
	public static BigInteger [] getRangeQuotient5(byte[] src, Hashtable <Integer, Integer> table, int [] frequency, int sum_of_frequencies)
	{
		int [] f = frequency.clone();
		int    m = sum_of_frequencies;
		
	    int [] s = new int[f.length];
		
		int sum = 0;
		for(int i = 0; i < f.length; i++)
		{
			s[i] = sum;
			sum    += f[i];
		}
		
		BigInteger [] offset = new BigInteger[2];
		offset[0]            = BigInteger.ZERO;
		offset[1]            = BigInteger.ONE;
		
		BigInteger [] range  = new BigInteger[2];
		range[0]             = BigInteger.ONE;
		range[1]             = BigInteger.ONE;
		
		int    n       = src.length;
		
		ArrayList <BigInteger> factor_list = new ArrayList <BigInteger> ();
	    
		for(int i = 0; i < n; i++)
	    {
	    	    int j = src[i];
	    	    if(j < 0)
	    	    	    j += 256;
	    	    j = table.get(j);
	    	   
	    	    BigInteger [] addend = new BigInteger[] {range[0], range[1]};
	    	    
	    	    BigInteger factor = BigInteger.ZERO;
	    	    factor            = factor.valueOf(s[j]);
	    	    addend[0]         = addend[0].multiply(factor);
	    	    factor            = factor.valueOf(m);
	    	    addend[1]         = addend[1].multiply(factor);
	    	    
	    	    BigInteger gcd = addend[0].gcd(addend[1]);
	    	    if(gcd.compareTo(BigInteger.ONE) == 1)
	    	    {
	    	    	    addend[0] = addend[0].divide(gcd);
			    addend[1] = addend[1].divide(gcd);
	    	    }
	    	    
	    	    offset[0] = offset[0].multiply(addend[1]);
	    	    addend[0] = addend[0].multiply(offset[1]);
	    	    offset[1] = offset[1].multiply(addend[1]);
	    	    offset[0] = offset[0].add(addend[0]);
	    	    
	    	    factor_list.add(addend[1]);
	    	 
			    
            factor   = factor.valueOf(f[j]);
	    	    range[0] = range[0].multiply(factor);
	    	    factor   = factor.valueOf(m);
	    	    range[1] = range[1].multiply(factor);
	    	    
	    	    gcd = range[0].gcd(range[1]);
	    	    if(gcd.compareTo(BigInteger.ONE) == 1)
	    	    {
	    	    	    range[0] = range[0].divide(gcd);
			    	range[1] = range[1].divide(gcd);
	    	    }
	    	    
	    	    f[j]--;
	    	    m--;
	    	    for(int k = j + 1; k < s.length; k++)
	    	    {
	    	    	    s[k]--;
	    	    }
	    }
	
		if(offset[1].compareTo(range[1]) != 0)
		{
			BigInteger range_factor  = range[1];
			BigInteger offset_factor = offset[1];
			
			offset[0]                = offset[0].multiply(range_factor);
			offset[1]                = offset[1].multiply(range_factor);
			factor_list.add(range_factor);
			
			range[0]                 = range[0].multiply(offset_factor);
			range[1]                 = range[1].multiply(offset_factor);
		}
		
		BigInteger []  det       = new BigInteger[] {offset[0], offset[1]};
		det[0].add(range[0]);
		
		ArrayList <BigInteger> d_list = new ArrayList <BigInteger> ();
	
		BigInteger amount = BigInteger.ONE;
		
		//Collections.sort(factor_list);
		
		for(int i = 0; i < factor_list.size(); i++)
		{
			BigInteger current_factor  = factor_list.get(i);
			BigInteger previous_amount = amount;
			amount = amount.multiply(current_factor);
			if(amount.compareTo(det[0]) == 1)
				amount = previous_amount;
		}
		
		
		
		BigInteger []  value       = new BigInteger[] {amount, offset[1]};
		
		//System.out.println("Offset 0 is " + offset[0] + ", offset 1 is " + offset[1]);
		//System.out.println("Range 0 is " + range[0] + ", range 1 is " + range[1]);
	    //System.out.println("Value 0 is " + value[0] + ", value 1 is " + value[1]);
		
        return value;	
	}
	
	
	

	// Adaptive probability.
	public static BigInteger [] getRangeQuotient4(byte[] src, Hashtable <Integer, Integer> table, int [] frequency, int sum_of_frequencies)
	{
		int [] f = frequency.clone();
		int    m = sum_of_frequencies;
		
	    int [] s = new int[f.length];
		
		int sum = 0;
		for(int i = 0; i < f.length; i++)
		{
			s[i] = sum;
			sum    += f[i];
		}
		
		BigInteger [] offset = new BigInteger[2];
		offset[0]            = BigInteger.ZERO;
		offset[1]            = BigInteger.ONE;
		
		BigInteger [] range  = new BigInteger[2];
		range[0]             = BigInteger.ONE;
		range[1]             = BigInteger.ONE;
		
		int    n       = src.length;
	    
		for(int i = 0; i < n; i++)
	    {
	    	    int j = src[i];
	    	    if(j < 0)
	    	    	    j += 256;
	    	    j = table.get(j);
	    	    
	    	    BigInteger [] addend = new BigInteger[] {range[0], range[1]};
	    	    
	    	    BigInteger factor = BigInteger.ZERO;
	    	    factor            = factor.valueOf(s[j]);
	    	    addend[0]         = addend[0].multiply(factor);
	    	    factor            = factor.valueOf(m);
	    	    addend[1]         = addend[1].multiply(factor);
	    	    
	    	    BigInteger gcd = addend[0].gcd(addend[1]);
	    	    if(gcd.compareTo(BigInteger.ONE) == 1)
	    	    {
	    	    	    System.out.println("Factoring addend.");
	    	    	    addend[0] = addend[0].divide(gcd);
			    addend[1] = addend[1].divide(gcd);
	    	    }
	    	    
	    	    offset[0] = offset[0].multiply(addend[1]);
	    	    addend[0] = addend[0].multiply(offset[1]);
	    	    offset[1] = offset[1].multiply(addend[1]);
	    	    offset[0] = offset[0].add(addend[0]);
	    	    
	    	    
	    	    gcd = offset[0].gcd(offset[1]);
	    	    if(gcd.compareTo(BigInteger.ONE) == 1)
	    	    {
	    	    	    System.out.println("Factoring offset.");
	    	    	    offset[0] = offset[0].divide(gcd);
			    	offset[1] = offset[1].divide(gcd);
	    	    }
			    
            factor   = factor.valueOf(f[j]);
	    	    range[0] = range[0].multiply(factor);
	    	    factor   = factor.valueOf(m);
	    	    range[1] = range[1].multiply(factor);
	    	    
	    	    gcd = range[0].gcd(range[1]);
	    	    if(gcd.compareTo(BigInteger.ONE) == 1)
	    	    {
	    	    	    range[0] = range[0].divide(gcd);
			    	range[1] = range[1].divide(gcd);
			    	System.out.println("Factoring range.");
	    	    }
	    	    System.out.println();
	    	    f[j]--;
	    	    m--;
	    	    for(int k = j + 1; k < s.length; k++)
	    	    {
	    	    	    s[k]--;
	    	    }
	    }
	
		
		
		
		BigInteger []  value       = new BigInteger[] {offset[0], offset[1]};
		
		
		// If we want to establish a preponderance of zero bits in our
		// numerator, we would look for the number in the interval 
		// that divides by two the most times.  For now we simply return the offset.
		
		if(offset[1].compareTo(range[1]) != 0)
		{
			BigInteger range_factor  = range[1];
			BigInteger offset_factor = offset[1];
			
			offset[0]                = offset[0].multiply(range_factor);
			offset[1]                = offset[1].multiply(range_factor);
			
			range[0]                 = range[0].multiply(offset_factor);
			range[1]                 = range[1].multiply(offset_factor);
		    System.out.println("Cross multiplied offset and range.");
		    
		    BigInteger stop = offset[0].add(range[0]);
		    
		    if(offset_factor.compareTo(range_factor) >= 0)
		    {
		    	    value[0] = offset[0].divide(offset_factor);
		    	    value[1] = offset[1].divide(offset_factor);
		    	     
		    }
		    else
		    {
		    	    value[0] = offset[0].divide(range_factor);
	    	        value[1] = offset[1].divide(range_factor);
		    }    
		}
		

		//System.out.println("Offset 0 is " + offset[0] + ", offset 1 is " + offset[1]);
		//System.out.println("Range 0 is " + range[0] + ", range 1 is " + range[1]);
	    //System.out.println("Value 0 is " + value[0] + ", value 1 is " + value[1]);
        return value;	
	}
	
	

	
	
	// Adaptive probability.
	public static BigInteger [] getRangeQuotient3(byte[] src, Hashtable <Integer, Integer> table, int [] frequency, int sum_of_frequencies)
	{
		int [] f = frequency.clone();
		int    m = sum_of_frequencies;
		
	    int [] s = new int[f.length];
		
		int sum = 0;
		for(int i = 0; i < f.length; i++)
		{
			s[i] = sum;
			sum    += f[i];
		}
		
		BigInteger [] offset = new BigInteger[2];
		offset[0]            = BigInteger.ZERO;
		offset[1]            = BigInteger.ONE;
		
		ArrayList <BigInteger> offset_factors = new ArrayList <BigInteger>();
		
		BigInteger [] range  = new BigInteger[2];
		range[0]             = BigInteger.ONE;
		range[1]             = BigInteger.ONE;
		
		ArrayList <BigInteger> range_factors = new ArrayList <BigInteger>();
		
		BigInteger [] a_numerator   = new BigInteger[f.length];
		BigInteger [] a_denominator = new BigInteger[f.length];
		
		BigInteger [] f_numerator   = new BigInteger[f.length];
		BigInteger [] f_denominator = new BigInteger[f.length];
		
		BigInteger a = BigInteger.ZERO;
		BigInteger b = BigInteger.ZERO;
		BigInteger c = BigInteger.ZERO;
		
		for(int i = 0; i < f.length; i++)
		{
			a = a.valueOf(s[i]);
			b = b.valueOf(m);
			int gcd = gcd(s[i], m);
			if(gcd != s[i])
			{
				c            = c.valueOf(gcd);
			    a            = a.divide(c);
			    b            = b.divide(c);
			}
			a_numerator[i]   = a;
			a_denominator[i] = b;
			
			a = a.valueOf(f[i]);
			b = b.valueOf(m);
			gcd = gcd(f[i], m);
			if(gcd != f[i])
			{
				c            = c.valueOf(gcd);
			    a            = a.divide(c);
			    b            = b.divide(c);
			}
			f_numerator[i]   = a;
			f_denominator[i] = b;
		}
		
		int    n       = src.length;
	    
		for(int i = 0; i < n; i++)
	    {
	    	    int j = src[i];
	    	    if(j < 0)
	    	    	    j += 256;
	    	    j = table.get(j);
	    	   
	    	    BigInteger [] addend = new BigInteger[] {range[0], range[1]};
	    	    if(j == 0)
	    	    {
	    	    	    addend[0] = BigInteger.ZERO;
	    	    	    addend[1] = offset[1];
	    	    }
	    	    else
	    	    {
	    	    	    addend[0] = addend[0].multiply(a_numerator[j]);  
	    	    	    addend[1] = addend[1].multiply(a_denominator[j]);
	    	    }
	    	    /*
	    	    BigInteger factor = BigInteger.ONE;
	    	    factor            = factor.valueOf(s[j]);
	    	    addend[0]         = addend[0].multiply(factor);
	    	    factor            = factor.valueOf(m);
	    	    addend[1]         = addend[1].multiply(factor);
	    	    
	    	    
	    	    BigInteger gcd = addend[0].gcd(addend[1]);
	    	    if(gcd.compareTo(BigInteger.ONE) == 1)
	    	    {
	    	    	    addend[0] = addend[0].divide(gcd);
			    addend[1] = addend[1].divide(gcd);
	    	    }
	    	    */
	    	    
	    	    if(j > 0)
	    	    {
	    	    	    if(addend[1].compareTo(offset[1]) != 0)
	    	    	    {
	    	            offset[0] = offset[0].multiply(addend[1]);
	    	            addend[0] = addend[0].multiply(offset[1]);
	    	            offset[1] = offset[1].multiply(addend[1]);
	    	            offset[0] = offset[0].add(addend[0]);
	    	            
	    	            offset_factors.add(addend[1]);
	    	    	    }
	    	    	    else
	    	    	    {
	    	    	    	    offset[0] = offset[0].add(addend[0]);
	    	    	    }
	    	    }
	    	    
	    	    /*
	    	    gcd = offset[0].gcd(offset[1]);
	    	    if(gcd.compareTo(BigInteger.ONE) == 1)
	    	    {
	    	    	    offset[0] = offset[0].divide(gcd);
			    	offset[1] = offset[1].divide(gcd);
	    	    }
			*/
	    	    
	    	    /*
            factor   = factor.valueOf(f[j]);
	    	    range[0] = range[0].multiply(factor);
	    	    factor   = factor.valueOf(m);
	    	    range[1] = range[1].multiply(factor);
	    	    
	    	    gcd = range[0].gcd(range[1]);
	    	    if(gcd.compareTo(BigInteger.ONE) == 1)
	    	    {
	    	    	    range[0] = range[0].divide(gcd);
			    	range[1] = range[1].divide(gcd);
	    	    }
	    	    */
	    	    range[0] = range[0].multiply(f_numerator[j]);
	    	    range[1] = range[1].multiply(f_denominator[j]);
	    	    
	    	    f[j]--;
	    	    m--;
	    	    for(int k = j + 1; k < s.length; k++)
	    	    {
	    	    	    s[k]--;
	    	    }
	    }
	
		BigInteger []  value       = new BigInteger[] {offset[0], offset[1]};
		if(offset[1].compareTo(range[1]) != 0)
		{
			BigInteger range_factor  = range[1];
			BigInteger offset_factor = offset[1];
			
			offset[0]                = offset[0].multiply(range_factor);
			offset[1]                = offset[1].multiply(range_factor);
			
			range[0]                 = range[0].multiply(offset_factor);
			range[1]                 = range[1].multiply(offset_factor);
		    System.out.println("Cross multiplied offset and range.");
		    
		    BigInteger stop = offset[0].add(range[0]);
		    
		    if(offset_factor.compareTo(range_factor) >= 0)
		    {
		    	    value[0] = offset[0].divide(offset_factor);
		    	    value[1] = offset[1].divide(offset_factor);
		    	     
		    }
		    else
		    {
		    	    value[0] = offset[0].divide(range_factor);
	    	        value[1] = offset[1].divide(range_factor);
		    }
		    
		    
		}
		
		// If we want to establish a preponderance of zero bits in our
		// numerator, we would now look for the number in the interval 
		// that divides by two the most times.  For now we simply return the offset.
		
		//System.out.println("Offset 0 is " + offset[0] + ", offset 1 is " + offset[1]);
		//System.out.println("Range 0 is " + range[0] + ", range 1 is " + range[1]);
	    //System.out.println("Value 0 is " + value[0] + ", value 1 is " + value[1]);
		
        return value;	
	}
	
	
	public static byte [] getMessage(BigInteger [] v, Hashtable <Integer, Integer>table, int [] f, int m, int n)
	{
		int [] s   = new int[f.length];
		int    sum = 0;
		for(int i = 0; i < f.length; i++)
		{
			s[i] = sum;
			sum += f[i];
		}
		
		byte [] message = new byte[n];
		
		BigInteger [] offset = new BigInteger[2];
		offset[0]            = BigInteger.ZERO;
		offset[1]            = BigInteger.ONE;
		
		BigInteger [] range  = new BigInteger[2];
		range[0]             = BigInteger.ONE;
		range[1]             = BigInteger.ONE;
		
		for(int i = 0; i < n; i++)
		{
			BigInteger [] w = new BigInteger[] {v[0], v[1]};
			
			if(offset[0].compareTo(BigInteger.ZERO) != 0)
			{
			    w[0] = w[0].multiply(offset[1]);
			    w[0] = w[0].subtract(offset[0].multiply(w[1]));
			    w[1] = w[1].multiply(offset[1]);
			    
			    BigInteger gcd = w[0].gcd(w[1]);
	    	        if(gcd.compareTo(BigInteger.ONE) == 1)
	    	        {
	    	    	        w[0] = w[0].divide(gcd);
			       	w[1] = w[1].divide(gcd);
	    	        }
			}
			
			for(int j = 0; j < f.length; j++)
			{
				BigInteger [] lower       = new BigInteger [] {range[0], range[1]};
				int           current_sum = s[j];
				lower[0]                  = lower[0].multiply(BigInteger.valueOf(current_sum));
				lower[1]                  = lower[1].multiply(BigInteger.valueOf(m));
				
				BigInteger [] upper = new BigInteger [] {range[0], range[1]};
				upper[0]            = upper[0].multiply(BigInteger.valueOf(s[j] + f[j]));
				upper[1]            = upper[1].multiply(BigInteger.valueOf(m));
				
				
				BigInteger [] a = new BigInteger [] {lower[0], lower[1]};
    	            BigInteger [] b = new BigInteger [] {w[0], w[1]};
    	            BigInteger [] c = new BigInteger [] {upper[0], upper[1]};
				
				if(a[1].compareTo(c[1]) != 0)
				{
					BigInteger lower_factor = a[1];
					BigInteger upper_factor = c[1];
					
					a[0] = a[0].multiply(upper_factor);
					a[1] = a[1].multiply(upper_factor);
					c[0] = c[0].multiply(upper_factor);
					c[1] = c[1].multiply(upper_factor);
				}
				
				if(a[1].compareTo(b[1]) != 0)
				{
					BigInteger bound_factor = a[1];
					BigInteger value_factor = b[1];
					
					a[0] = a[0].multiply(value_factor);
					a[1] = a[1].multiply(value_factor);
					c[0] = c[0].multiply(value_factor);
					c[1] = c[1].multiply(value_factor);
					
					b[0] = b[0].multiply(bound_factor);
					b[1] = b[1].multiply(bound_factor);
				}
	    	        
				if((a[0].compareTo(b[0]) <= 0) && (c[0].compareTo(b[0]) > 0))
				{ 
					BigInteger [] addend = new BigInteger [] {range[0], range[1]};
					addend[0]            = addend[0].multiply(BigInteger.valueOf(s[j]));
					addend[1]            = addend[1].multiply(BigInteger.valueOf(m));
					
					offset[0]            = offset[0].multiply(addend[1]);
					offset[0]            = offset[0].add(addend[0].multiply(offset[1]));
					
				    offset[1]            = offset[1].multiply(addend[1]);
				    
				    BigInteger gcd = offset[0].gcd(offset[1]);
					if(gcd.compareTo(BigInteger.ONE) == 1)
					{
						offset[0] = offset[0].divide(gcd);
						offset[1] = offset[1].divide(gcd);;
					}
				  
                    int current_freq = f[j];
				    range[0]         = range[0].multiply(BigInteger.valueOf(current_freq));
				    range[1]         = range[1].multiply(BigInteger.valueOf(m));
				    
				    gcd = range[0].gcd(range[1]);
		    	        if(gcd.compareTo(BigInteger.ONE) == 1)
		    	        {
		    	    	        range[0] = range[0].divide(gcd);
				    	    range[1] = range[1].divide(gcd);
		    	        }
				   
				    j = table.get(j);
				    message[i]    = (byte)j;
				    break;
				}
			}
		}
		
		return message;
	}
	
	public static byte [] getMessage2(BigInteger [] v, Hashtable <Integer, Integer>table, int [] frequency, int sum_of_frequencies, int n)
	{
		int [] f = frequency.clone();
		int    m = sum_of_frequencies;
		
		int [] s   = new int[f.length];
		int    sum = 0;
		for(int i = 0; i < f.length; i++)
		{
			s[i] = sum;
			sum += f[i];
		}
		
		byte [] message = new byte[n];
		
		BigInteger [] offset = new BigInteger[2];
		offset[0]            = BigInteger.ZERO;
		offset[1]            = BigInteger.ONE;
		
		BigInteger [] range  = new BigInteger[2];
		range[0]             = BigInteger.ONE;
		range[1]             = BigInteger.ONE;
		
		for(int i = 0; i < n; i++)
		{
			BigInteger [] w = new BigInteger[] {v[0], v[1]};
			if(offset[0].compareTo(BigInteger.ZERO) != 0)
			{
			    w[0] = w[0].multiply(offset[1]);
			    w[0] = w[0].subtract(offset[0].multiply(w[1]));
			    w[1] = w[1].multiply(offset[1]);
			    
			    BigInteger gcd = w[0].gcd(w[1]);
	    	        if(gcd.compareTo(BigInteger.ONE) == 1)
	    	        {
	    	    	        w[0] = w[0].divide(gcd);
			       	w[1] = w[1].divide(gcd);
	    	        }
			}
			
			for(int j = 0; j < f.length; j++)
			{
				if(f[j] != 0)
				{
				    BigInteger [] lower       = new BigInteger [] {range[0], range[1]};
				    lower[0]                  = lower[0].multiply(BigInteger.valueOf(s[j]));
				    lower[1]                  = lower[1].multiply(BigInteger.valueOf(m));
				
				    BigInteger [] upper       = new BigInteger [] {range[0], range[1]};
				    upper[0]                  = upper[0].multiply(BigInteger.valueOf(s[j] + f[j]));
				    upper[1]                  = upper[1].multiply(BigInteger.valueOf(m));
				
				    BigInteger [] a           = new BigInteger [] {lower[0], lower[1]};
    	                BigInteger [] b           = new BigInteger [] {w[0], w[1]};
    	                BigInteger [] c           = new BigInteger [] {upper[0], upper[1]};
				
				    if(a[1].compareTo(c[1]) != 0)
				    {
					    BigInteger lower_factor = a[1];
					    BigInteger upper_factor = c[1];
					
					    a[0]                    = a[0].multiply(upper_factor);
					    a[1]                    = a[1].multiply(upper_factor);
					    c[0]                    = c[0].multiply(upper_factor);
					    c[1]                    = c[1].multiply(upper_factor);
				    }
				
				    if(a[1].compareTo(b[1]) != 0)
				    {
					    BigInteger bound_factor = a[1];
					    BigInteger value_factor = b[1];
					
					    a[0]                    = a[0].multiply(value_factor);
					    a[1]                    = a[1].multiply(value_factor);
					    c[0]                    = c[0].multiply(value_factor);
					    c[1]                    = c[1].multiply(value_factor);
					    b[0]                    = b[0].multiply(bound_factor);
					    b[1]                    = b[1].multiply(bound_factor);
				    }
	    	        
				    if((a[0].compareTo(b[0]) <= 0) && (c[0].compareTo(b[0]) > 0))
				    { 
					    BigInteger [] addend = new BigInteger [] {range[0], range[1]};
					    addend[0]            = addend[0].multiply(BigInteger.valueOf(s[j]));
					    addend[1]            = addend[1].multiply(BigInteger.valueOf(m));
					
					    offset[0]            = offset[0].multiply(addend[1]);
					    offset[0]            = offset[0].add(addend[0].multiply(offset[1]));
					
				        offset[1]            = offset[1].multiply(addend[1]);
				    
				        BigInteger gcd = offset[0].gcd(offset[1]);
					    if(gcd.compareTo(BigInteger.ONE) == 1)
					    {
						    offset[0] = offset[0].divide(gcd);
						    offset[1] = offset[1].divide(gcd);;
					    }
				  
				        range[0]         = range[0].multiply(BigInteger.valueOf(f[j]));
				        range[1]         = range[1].multiply(BigInteger.valueOf(m));
				    
				        gcd = range[0].gcd(range[1]);
		    	            if(gcd.compareTo(BigInteger.ONE) == 1)
		    	            {
		    	    	            range[0] = range[0].divide(gcd);
				    	        range[1] = range[1].divide(gcd);
		    	            }
				   
		    	            f[j]--;
			    	        m--;
			    	        for(int k = j + 1; k < s.length; k++)
			    	    	        s[k]--;
		    	        
				        j = table.get(j);
				        message[i]    = (byte)j;
				        break;
				    }
			    }
			}	
		}
		
		return message;
	}
	
	public static long getGCD(long a, long b)
	{
		if(a == b)
		{
			long divisor = a;
			return divisor;
		}
		
		if(a == 0 || b == 0)
		{
			long divisor = 0;
			return divisor;
		}
		
		ArrayList <Long> n_list = new ArrayList <Long>();
		ArrayList <Long> d_list = new ArrayList <Long>();
		
		ArrayList <Long> factor_list = new ArrayList <Long>();
		
		if(a < b)
		{
			
		    n_list = getPrimeFactors(a);
		    
		    if(n_list.size() == 0)
		    {
		    	    if(b % a == 0)
		    	    	    factor_list.add(a);
		    }
		    else
		    { 
		    	    d_list = getPrimeFactors(b);
		    }
		}
		else
		{
			n_list = getPrimeFactors(b); 	
		    if(n_list.size() == 0)
		    {
		    	    if(a % b == 0)
		    	    	    factor_list.add(b);
		    }
		}
		
	    if(n_list.size() != 0)
	    {   
	        for(int i = 0; i < n_list.size(); i++)
	        {
	        	    if(d_list.size() > 0)
	        	    {
	        	    	    long factor = n_list.get(i);
		    	        if(d_list.contains(factor))
		    	        {
		    	    	        int j = d_list.lastIndexOf(factor);
		    	            d_list.remove(j);
		    	            factor_list.add(factor);    
	        	        }
	        	    }
	        	    else
	        	    	    break;
	    	        
	      	}
	    }
	    
	    long divisor = 0;
	    int size = factor_list.size();
		if(size > 0)
		{
		    	divisor = factor_list.get(0);
		    	for(int j = 1; j < size; j++)
		    	{
		    	    	long factor = factor_list.get(j);
		    	    divisor     *= factor;
		    	}    
	    }
		
		return divisor;
	}
	
	public static long pow(long base, long exp)
	{
		long a = base;
		for(int i = 2; i <= exp; i++)
		    a *= base;
		
		return a;
	}
	
	public static long pow(long base, long exp, long mod)
	{
		long a = base;
		for(int i = 2; i <= exp; i++)
		    a *= base;
		a %= mod;
		return a;
	}
	
	public static boolean isProbablePrime(long n)
	{
		long b = 2;
		long d = n - 1;
		long s = 0;
		while((d & 1) == 0)
		{
			s++;
			d >>= 1;
		}
		
		long x = pow(b, d, n);
		if(x == 1 || x == n - 1)
			return true;
		
		for(int i = 1; i < s; i++)
		{
			x = (x * x) % n;
			if(x == 1)
				return false;
			else if(x == n - 1)	
				return true;
		}
	
		return false;
	}
	
	public static long nextPrime(long n)
	{
		long m = 0;
		if(n % 2 == 0)
			m = n + 1;
		else
			m = n + 2;
		boolean foundPrime = false;
		while(!foundPrime)
		{
		    boolean isPrime = isProbablePrime(m);
		    if(isPrime)
		    	    return m;
		    else
		    	    m += 2;
		}
		return m;
	}
	
	
	public static ArrayList<Long> getPrimeFactors(long n)
	{
		double root = Math.sqrt(n);
		
		long j = n;
		long k = (long)root;
		
		ArrayList <Long> list = new ArrayList<Long>();
	   
		for(long i = 2; i < j && i <= k; i = nextPrime(i))
	    {   
	    	    while(j % i == 0)
	    	    {
	    	    	    list.add(i);
	    	    	    j /= i;
	    	    }
	    }
	    if(j > 2)
	    	    list.add(j);
	    
	    return list;
	}
	
	public static ArrayList<BigInteger> getPrimeFactors(BigInteger n)
	{
		BigInteger j = n;
		BigInteger i = BigInteger.TWO;
	    BigInteger k = n.sqrt();
		
		ArrayList <BigInteger> list = new ArrayList<BigInteger>();
		
	    for(i = BigInteger.TWO; i.compareTo(j) < 0 && i.compareTo(k) < 0; i = i.add(BigInteger.ONE));
	    {
	    	    while(j.mod(i) == BigInteger.ZERO)
	    	    {
	    	    	    list.add(i);
	    	    	    j = j.divide(i);
	    	    }
	    }
	    if(j.compareTo(BigInteger.TWO) == 1)
	    	    list.add(j);
	    
	    return list;
	}
	
	public static ArrayList getHuffmanList(byte[] string)
	{
		ArrayList list = new ArrayList();

		ArrayList histogram_list = StringMapper.getHistogram(string);
		int string_min = (int) histogram_list.get(0);
		int[] string_histogram = (int[]) histogram_list.get(1);
		int value_range = (int) histogram_list.get(2);

		// This is the number of different values in the string;
		int n = string_histogram.length;

		// We produce a rank table from the histogram to use when encoding.
		int[] rank_table = StringMapper.getRankTable(string_histogram);

		// We produce a frequency table to produce huffman lengths.
		ArrayList frequency_list = new ArrayList();
		for(int k = 0; k < n; k++)
			frequency_list.add(string_histogram[k]);
		Collections.sort(frequency_list, Comparator.reverseOrder());
		int[] frequency = new int[n];
		for(int k = 0; k < n; k++)
			frequency[k] = (int) frequency_list.get(k);

		double shannon_limit = getShannonLimit(frequency);

		byte[] huffman_length = CodeMapper.getHuffmanLength2(frequency);

		// We produce a huffman code from the lengths.
		int[] huffman_code = CodeMapper.getCanonicalCode(huffman_length);

		// We produce the estimated bit length of the output.
		int estimated_bit_length = CodeMapper.getCost(huffman_length, frequency);

		list.add(estimated_bit_length);
		list.add(shannon_limit);
		list.add(rank_table);
		list.add(huffman_code);
		list.add(huffman_length);

		return list;
	}

	public static ArrayList getHuffmanList2(byte[] string)
	{
		ArrayList list = new ArrayList();

		ArrayList histogram_list = StringMapper.getHistogram(string);
		int string_min = (int) histogram_list.get(0);
		int[] string_histogram = (int[]) histogram_list.get(1);
		int value_range = (int) histogram_list.get(2);

		// This is the number of different values in the string;
		int n = string_histogram.length;

		// We produce a rank table from the histogram to use when encoding.
		int[] rank_table = StringMapper.getRankTable(string_histogram);
	
		// We produce a frequency table to produce huffman lengths.
		ArrayList frequency_list = new ArrayList();
		for(int k = 0; k < n; k++)
			frequency_list.add(string_histogram[k]);
		Collections.sort(frequency_list, Comparator.reverseOrder());
		int[] frequency = new int[n];
		for(int k = 0; k < n; k++)
			frequency[k] = (int) frequency_list.get(k);
		byte[] huffman_length = CodeMapper.getHuffmanLength2(frequency);

		// We produce a huffman code from the lengths.
		int [] huffman_code = CodeMapper.getCanonicalCode(huffman_length);

		// We produce the estimated bit length of the output.
		int estimated_bit_length = CodeMapper.getCost(huffman_length, frequency);

		int byte_length = estimated_bit_length / 8;
		if(estimated_bit_length % 8 != 0)
			byte_length++;
		// byte_length += 2;
		byte[] packed_string = new byte[byte_length];

		int huffman_bit_length = packCode(string, rank_table, huffman_code, huffman_length, packed_string);

		System.out.println("Estimated bit length was " + estimated_bit_length);
		System.out.println("Actual bit length was " + huffman_bit_length);

		ArrayList length_list = CodeMapper.packLengthTable(huffman_length);

		list.add(huffman_bit_length);
		list.add(rank_table);
		list.add(huffman_code);
		list.add(huffman_length);
		list.add(length_list);
		list.add(packed_string);

		return list;
	}
	
	
	
}