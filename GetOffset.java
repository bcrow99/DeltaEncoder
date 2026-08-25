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
	/*
	private static final int [] numerator   = {1, 1, 1, 1, 1, 2, 1, 3, 2, 3, 1, 4, 3, 2, 5, 5, 3, 4, 5, 6, 7};
	
	private static final String [] optimal  = {"1/8", "1/7", "1/6", "1/5", "1/4", "2/7", "1/3", "3/8", "2/5", "3/7", "1/2",
	"4/7", "3/5", "2/3", "5/8", "5/7", "3/4", "4/5", "5/6", "6/7", "7/8"};
	*/
	
	private static final int [] numerator   = {0,  1,  1,  3, 1, 1,  3,  7, 2,  9, 1, 11, 3, 13,  7, 3, 4, 17,  9, 19};
	private static final int [] denominator = {1, 20, 10, 20, 5, 4, 10, 20, 5, 20, 2, 20, 5, 20, 10, 4, 5, 20, 10, 20};
	private static final String [] optimal  = {".0", ".05", ".1", ".15", ".2", ".25", ".3", ".35", ".4", ".45", ".5",
	".55", ".6", ".65", ".7", ".75", ".8", ".85", ".9", ".95"};		
	
	
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
			
			ArrayList delta_list = new ArrayList();
			
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
     	   
     	    /*
     	    if(isSame)
     		    System.out.println("Decompressed bytes same as original bytes using initial offset.");
     	    else
     		    System.out.println("Decompressed bytes not the same as original bytes using initial offset.");
     	    */
     	    
			for(int i = 0; i < 20; i++)
			{
				BigInteger [] optimal_value = {BigInteger.valueOf(numerator[i]), BigInteger.valueOf(denominator[i])};	
				BigInteger c              = optimal_value[0].multiply(init_offset[1]);
				BigInteger d              = init_offset[0].multiply(optimal_value[1]);
				
				if(c.compareTo(d) < 0)
				{
					BigInteger e = optimal_value[0].multiply(init_limit[1]);
					BigInteger f = init_limit[0].multiply(optimal_value[1]);
					
					if(f.compareTo(e) < 0)
					    System.out.println("Optimal value " + optimal[i] + " is less than initial offset and initial offset plus limit is more than optimal value.");
					else
						System.out.println("Optimal value " + optimal[i] + " is less than initial offset and initial offset plus limit is less than optimal value.");	
				}
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
						BigInteger [] previous_delta  = {c.subtract(d), init_offset[1].multiply(previous_value[0])};
						
						
						e = previous_delta[0].multiply(current_delta[1]);
						e = e.negate();
						f = current_delta[0].multiply(previous_delta[1]);
						
						if(e.compareTo(f) < 0)
						{
						    System.out.println("Previous optimal value " + optimal[i - 1] + " is closer to initial offset.");
						    delta_list.add(previous_delta);
						    delta_list.add(i - 1);
						}
						else
						{
							delta_list.add(current_delta);
						    delta_list.add(i);	
						} 
					}
					else
					{
					    BigInteger [] delta = {c.subtract(d), init_offset[1].multiply(optimal_value[0])};	
					    delta_list.add(delta);
					    delta_list.add(i);
					}
					
					break;
				}
				if(i == 20)
				{
					System.out.println("No optimal value is more than initial offset.");
					BigInteger [] delta = {d.subtract(c), init_offset[1].multiply(optimal_value[i])};	
				    delta_list.add(delta);
				    delta_list.add(i);
				}
			}
			System.out.println();
			for(int i = 1; i < number_of_tables; i++)
			{
				result = ArithmeticMapper.getArithmeticOffsetAndRange(blue_bytes, frequency, series.get(i));
				BigInteger [] current_offset = {result[0], result[1]};
				BigInteger [] current_range  = {result[2], result[3]};
				BigInteger [] current_limit  = {result[0].add(result[2]), result[3]};
				
				a = new BigDecimal(current_offset[0]);
		     	b = new BigDecimal(current_offset[1]);
		     	    
		     	fraction = a.divide(b, 100, RoundingMode.HALF_EVEN);
				System.out.println("Current offset is approximately " + String.format("%.8f", fraction) + " from table " + i);
				
				for(int j = 0; j < 20; j++)
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
						
						if(j > 0)
						{
							BigInteger [] current_delta  = {c.subtract(d), current_offset[1].multiply(optimal_value[0])};	
						    BigInteger [] previous_value = {BigInteger.valueOf(numerator[j - 1]), BigInteger.valueOf(denominator[j - 1])};	
						    c = previous_value[0].multiply(current_offset[1]);
							d = current_offset[0].multiply(previous_value[1]);
							BigInteger [] previous_delta  = {c.subtract(d), current_offset[1].multiply(previous_value[0])};
							
							
							e = previous_delta[0].multiply(current_delta[1]);
							e = e.negate();
							f = current_delta[0].multiply(previous_delta[1]);
							
							if(e.compareTo(f) < 0)
							{
							    System.out.println("Previous optimal value " + optimal[j - 1] + " is closer to current offset.");
							    delta_list.add(previous_delta);
							    delta_list.add(j - 1);
							}
							else
							{
								delta_list.add(current_delta);
							    delta_list.add(j);	
							} 
						}
						else
						{
						    BigInteger [] delta = {c.subtract(d), current_offset[1].multiply(optimal_value[0])};	
						    delta_list.add(delta);
						    delta_list.add(j);
						}
						
						break;
					}
					
					if(j == 19)
					{
						System.out.println("No optimal value is more than current offset.");
						BigInteger [] delta = {d.subtract(c), init_offset[1].multiply(optimal_value[0])};	
					    delta_list.add(delta);
					    delta_list.add(j);
					    System.out.println("Got here.");
					}	
				}
				
				System.out.println();
			}
		
			System.out.println("Length of delta list is " + delta_list.size());
			
			BigInteger [] init_delta = (BigInteger[])delta_list.get(0);
			BigInteger [] delta_min  = init_delta;
			int value_index = (int)delta_list.get(1);
			int table_index = 0;
			
			System.out.println("Got here.");
			for(int i = 1; i < number_of_tables; i++)
			{
				int j = i * 2;
				
				BigInteger [] current_delta = (BigInteger[])delta_list.get(j);
				int           current_index = (int)delta_list.get(j + 1);
				
				if(current_delta[0].compareTo(BigInteger.ZERO) == - 1)
					current_delta[0] = current_delta[0].negate();
				BigInteger c              = delta_min[0].multiply(current_delta[1]);
				BigInteger d              = current_delta[0].multiply(delta_min[1]);
				
				if(c.compareTo(d) > 0)
				{
					delta_min   = (BigInteger[])delta_list.get(j);
					value_index = current_index;
					table_index = i;
				}
			}
			
			System.out.println("The table " + table_index + " produces an offset closet to an optimal value (" + value_index + ")");
			
			byte [] closest_table = series.get(table_index);
			
			result = ArithmeticMapper.getArithmeticOffsetAndRange(blue_bytes, frequency, closest_table);
			BigInteger [] current_offset = {result[0], result[1]};
			BigInteger [] current_range  = {result[2], result[3]};
			BigInteger [] current_limit  = {result[0].add(result[2]), result[3]};
			
			a = new BigDecimal(current_offset[0]);
	     	b = new BigDecimal(current_offset[1]);
	     	    
	     	fraction = a.divide(b, 100, RoundingMode.HALF_EVEN);
			System.out.println("The offset is approximately " + String.format("%.8f", fraction));
			
			a = new BigDecimal(delta_min[0]);
	     	b = new BigDecimal(delta_min[1]);
	     	    
	     	fraction = a.divide(b, 100, RoundingMode.HALF_EVEN);
			System.out.println("The delta is approximately " + String.format("%.8f", fraction));
			System.out.println();
			
			/*
			for(int i = 1; i < closest_table.length; i++)
			{
				byte [] rotated_table = ArithmeticMapper.rotateTable(closest_table, i);
				result = ArithmeticMapper.getArithmeticOffsetAndRange(blue_bytes, frequency, rotated_table);
				BigInteger [] rotated_offset = {result[0], result[1]};
				a = new BigDecimal(current_offset[0]);
		     	b = new BigDecimal(current_offset[1]);
		     	    
		     	fraction = a.divide(b, 100, RoundingMode.HALF_EVEN);
				System.out.println("The offset from rotated table " + i + " is approximately " + String.format("%.8f", fraction));
			}
			*/
			
		}
		catch(Exception e)
		{
			System.out.println(e.toString());
		}
	}
}