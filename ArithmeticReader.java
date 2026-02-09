import java.awt.*;
import java.awt.image.*;
import java.io.*;
import java.awt.event.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.zip.*;
import javax.imageio.*;
import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import java.math.*;
import java.lang.Math.*;

public class ArithmeticReader
{
	public static void main(String[] args)
	{
		if (args.length != 1)
		{
			System.out.println("Usage: java ArithmeticReader <filename>");
			System.exit(0);
		}

		ArithmeticReader reader = new ArithmeticReader(args[0]);
	}

	public ArithmeticReader(String filename)
	{
		try
		{
			File file          = new File(filename);
			DataInputStream in = new DataInputStream(new FileInputStream(file));
			
			int n = in.readInt();
			int number_of_segments = in.readInt();
			int segment_length = in.readInt();
			
			BigDecimal [][] offset = new BigDecimal[n][number_of_segments];
			for (int i = 0; i < n; i++)
			{
				for(int j = 0; j < number_of_segments; j++)
				{
				    int scale = in.readInt();
				    int size  = in.readInt();
				
				    byte [] byte_array = new byte[size];
			        in.read(byte_array, 0, size);
			    
			        BigInteger a = new BigInteger(byte_array);
			        BigDecimal b = new BigDecimal(a);
			        BigDecimal c = b.movePointLeft(scale);
			    
			        offset[i][j] = c;	
				}
			}
			
			int [][] frequency = new int[n][256];
			
			for(int i = 0; i < n; i++)
			{
				for(int j = 0; j < 256; j++)
					frequency[i][j] = in.readInt();
			}
			
			System.out.println("Offsets:");
		    for(int i = 0; i < n; i++)
		    {
		    	    for(int j = 0; j < number_of_segments; j++)
		    	    	    System.out.print(String.format("%.6f", offset[i][j]) + " ");
		    	    System.out.println();
		    }
		    
		}
		catch(Exception e)
		{
			System.out.println("Exception reading file:");
			
		}
	}
}