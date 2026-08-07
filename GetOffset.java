import java.awt.*;
import java.awt.image.*;
import java.io.*;
import java.math.*;
import java.awt.event.*;
import java.awt.geom.AffineTransform;
import java.util.*;
import java.util.zip.*;
import javax.imageio.*;
import javax.swing.*;
import javax.swing.event.*;

public class GetOffset
{
	public static void main(String[] args)
	{
		if(args.length != 1)
			System.out.println("Usage: GetOffset <filename>");
		else
		{
			new GetOffset(args[0]);
		}		
	}	
	
	public GetOffset(String filename)
	{
		try
		{
			File file = new File(filename);
			BufferedImage image = ImageIO.read(file);
			
			int raster_type = image.getType();
			int image_xdim  = image.getWidth();
			int image_ydim  = image.getHeight();
			
			int [] pixel = new int[image_xdim * image_ydim];
			PixelGrabber pg = new PixelGrabber(image, 0, 0, image_xdim, image_ydim, pixel, 0, image_xdim);
			try
			{
				pg.grabPixels();
			}
			catch (InterruptedException e)
			{
				System.err.println(e);
			}

			int[] blue  = new int[image_xdim * image_ydim];
			int[] green = new int[image_xdim * image_ydim];
			int[] red   = new int[image_xdim * image_ydim];
			for (int i = 0; i < pixel.length; i++)
			{
				blue[i]  = (pixel[i] >> 16) & 0xff;
				green[i] = (pixel[i] >> 8) & 0xff;
				red[i]   = pixel[i] & 0xff;
			}
			
			
			int []  frequency = new int[256];
			byte [] blue_bytes = new byte[blue.length / 100];
			for(int i = 0; i < blue.length / 100; i++)
			{
				int j = blue[i];
				frequency[j]++;
				blue_bytes[i] = (byte)j;
			}
			
		
			ArrayList <byte []> series = ArithmeticMapper.getTableSeries2(blue_bytes, frequency);
			
			int series_size = series.size();
			System.out.println("There are " + series_size + " tables.");
			
			/*
			BigInteger [] result = ArithmeticMapper.getArithmeticOffsetAndRange(blue_bytes, frequency, series.get(0));
			BigDecimal offset    = new BigDecimal(result[0]);
	        BigDecimal divisor   = new BigDecimal(result[1]);
	        offset               = offset.divide(divisor, 10, RoundingMode.HALF_EVEN);
	        System.out.println("Offset in probabilistic space from table 0 " + offset);
	        */
			
			for(int i = 0; i < series_size; i++)
			{
				BigInteger [] result = ArithmeticMapper.getArithmeticOffsetAndRange(blue_bytes, frequency, series.get(i));
				BigDecimal range    = new BigDecimal(result[2]);
		        BigDecimal divisor   = new BigDecimal(result[3]);
		        range               = range.divide(divisor, 10, RoundingMode.HALF_DOWN);
		     
		        System.out.println("Range of probabilistic space from table " + i + " is " + range);
			}
			
			
		}
		catch(Exception e)
		{
			System.out.println(e.toString());
		}   	
	}
}