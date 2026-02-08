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
			BigDecimal [] offset = new BigDecimal[n];
			for (int i = 0; i < n; i++)
			{
				int scale = in.readInt();
				int size  = in.readInt();
				
				byte [] byte_array = new byte[size];
			    in.read(byte_array, 0, size);
			    
			    BigInteger a = new BigInteger(byte_array);
			    BigDecimal b = new BigDecimal(a);
			    BigDecimal c = b.movePointLeft(scale);
			    
			    offset[i] = c;			    
			}
			
			System.out.println("Offsets:");
		    for(int i = 0; i < n; i++)
		    	    System.out.print(String.format("%.6f", offset[i]) + " ");
		    System.out.println();
			
		}
		catch(Exception e)
		{
			System.out.println("Exception reading file:");
			
		}
	}
}