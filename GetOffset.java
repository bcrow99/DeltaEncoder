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
	private static final int [] numerator   = {1, 1, 1, 1, 1, 2, 1, 3, 2, 3, 1, 4, 3, 2, 5, 5, 3, 4, 5, 6, 7};
	private static final int [] denominator = {8, 7, 6, 5, 4, 7, 3, 8, 5, 7, 2, 7, 5, 3, 8, 7, 4, 5, 6, 7, 8};
	private static final String [] optimal  = {"1/8", "1/7", "1/6", "1/5", "1/4", "2/7", "1/3", "3/8", "2/5", "3/7", "1/2",
			                                   "4/7", "3/5", "2/3", "5/8", "5/7", "3/4", "4/5", "5/6", "6/7", "7/8"};
	
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
			byte [] blue_bytes = new byte[blue.length / 1000];
			for(int i = 0; i < blue.length / 1000; i++)
			{
				int j = red[i];
				frequency[j]++;
				blue_bytes[i] = (byte)j;
			}
			
		    int number_of_bytes = blue_bytes.length;
			
			ArrayList <byte []> series = ArithmeticMapper.getTableSeries3(blue_bytes, frequency);
			
			int number_of_tables = series.size();
			System.out.println("There are " + number_of_tables + " tables.");
			
			BigInteger [] result = ArithmeticMapper.getArithmeticOffsetAndRange(blue_bytes, frequency, series.get(0));
			
			BigInteger [] init_offset = {result[0], result[1]};
			BigInteger [] init_range  = {result[2], result[3]};
			BigInteger [] init_limit  = {result[0].add(result[2]), result[3]};
			
			BigDecimal a = new BigDecimal(init_offset[0]);
	     	BigDecimal b = new BigDecimal(init_offset[1]);
	     	    
	     	BigDecimal fraction = a.divide(b, 100, RoundingMode.HALF_EVEN);
			System.out.println("Inital offset is approximately " + String.format("%.8f", fraction));
			
     	    byte [] bytes = ArithmeticMapper.getArithmeticValues(init_offset, frequency, number_of_bytes, series.get(0));
     	   
     	    boolean isSame = true;
     	    for(int j = 0; j < number_of_bytes; j++)
     	    {
     		   if(bytes[j] != blue_bytes[j])
     			   isSame = false;
     	    }
     	   
     	    if(isSame)
     		    System.out.println("Decompressed bytes same as original bytes using initial offset.");
     	    else
     		    System.out.println("Decompressed bytes not the same as original bytes using initial offset.");
     	   
     	    ArrayList delta_list = new ArrayList();
     	    
			for(int i = 0; i < 21; i++)
			{
				BigInteger [] optimal_value = {BigInteger.valueOf(numerator[i]), BigInteger.valueOf(denominator[i])};	
				BigInteger c = optimal_value[0].multiply(init_offset[1]);
				BigInteger d = init_offset[0].multiply(optimal_value[1]);
				
				if(c.compareTo(d) >= 0)
				{
					BigInteger e = optimal_value[0].multiply(init_limit[1]);
					BigInteger f = init_limit[0].multiply(optimal_value[1]);
					if(e.compareTo(f) < 0)
					    System.out.println("Optimal value " + optimal[i] + " is more than initial offset and less than limit.");
					else
						System.out.println("Optimal value " + optimal[i] + " is more than initial offset and more than limit.");
					
					if(i > 0)
					{
						BigInteger [] current_delta  = {c.subtract(d), init_offset[1].multiply(optimal_value[0])};	
					    BigInteger [] previous_value = {BigInteger.valueOf(numerator[i - 1]), BigInteger.valueOf(denominator[i - 1])};	
					    c = previous_value[0].multiply(init_offset[1]);
						d = init_offset[0].multiply(previous_value[1]);
						BigInteger [] previous_delta  = {d.subtract(c), init_offset[1].multiply(previous_value[0])};
						
						
						e = previous_delta[0].multiply(current_delta[1]);
						f = current_delta[0].multiply(previous_delta[1]);
						
						if(e.compareTo(f) < 0)
						{
						    System.out.println("Previous optimal value " + optimal[i - 1] + " is closer to initial offset.");
						}
						else
						{
							
						}
						
					    
					}
					else
					{
					    BigInteger [] delta = {d.subtract(c), init_offset[1].multiply(optimal_value[0])};	
					}
					
					break;
				}
				if(i == 20)
				{
					System.out.println("No optimal value is more than initial offset.");
				}
			}
			
			for(int i = 1; i < number_of_tables; i++)
			{
				result = ArithmeticMapper.getArithmeticOffsetAndRange(blue_bytes, frequency, series.get(i));
				BigInteger [] current_offset = {result[0], result[1]};
				BigInteger [] current_range  = {result[2], result[3]};
				BigInteger [] current_limit  = {result[0].add(result[2]), result[3]};
				
				for(int j = 0; j < 21; j++)
				{
					BigInteger [] optimal_value = {BigInteger.valueOf(numerator[j]), BigInteger.valueOf(denominator[j])};	
					BigInteger c = optimal_value[0].multiply(current_offset[1]);
					BigInteger d = current_offset[0].multiply(optimal_value[1]);
					
					if(c.compareTo(d) >= 0)
					{
						BigInteger e = optimal_value[0].multiply(current_limit[1]);
						BigInteger f = current_limit[0].multiply(optimal_value[1]);
						
						if(e.compareTo(f) < 0)
						    System.out.println("Optimal value " + optimal[j] + " is more than current offset and less than limit.");
						else
							System.out.println("Optimal value " + optimal[j] + " is more than current offset and more than limit.");
						break;
					}
				}
			}
		
		}
		catch(Exception e)
		{
			System.out.println(e.toString());
		}
	}
}