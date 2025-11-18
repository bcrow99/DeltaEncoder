import java.util.ArrayList;
import java.util.*;
import java.util.zip.*;
import java.lang.Math.*;
import java.math.*;

public class TestRange
{
	public static void main(String[] args)
	{
		TestRange test = new TestRange();
	}	
	
	public TestRange()
	{
		
	    byte [] message = new byte[10];
	    message[0]      = 0;
	    message[1]      = 1;
	    message[2]      = 0;
	    message[3]      = 1;
	    message[4]      = 0;
	    message[5]      = 1;
	    message[6]      = 0;
	    message[7]      = 1;
	    message[8]      = 0;
	    message[9]      = 1;
	   
		
		/*
		byte [] message = new byte[2];
		message[0] = 0;
		message[1] = 1;
	    */
	   
	    int [] freq = new int [] {1, 1};
	    int    m    = 2;
	   
	    long [] value   = CodeMapper.getRangeQuotient(message, freq, m);;
	    double location = value[0];
	    location       /= value[1];
	    System.out.println("Location of message in probabilistic space returned by getRangeQuotient is " + location);
	    System.out.println("Decoded message from getMessage with longs:");
	    byte [] decoded_message = CodeMapper.getMessage(value, freq, m, message.length);
	    for(int i = 0; i < decoded_message.length; i++)
    	      System.out.print(decoded_message[i] + " ");
        System.out.println();
        System.out.println();
	    
	    BigInteger [] value2 = CodeMapper.getRangeQuotient2(message, freq, m);
	    BigDecimal location2 = new BigDecimal(value2[0]);
	    BigDecimal divisor   = new BigDecimal(value2[1]);
	    location2 = location2.divide(divisor);
	    System.out.println("Location of message in probabilistic space returned by getRangeQuotient2 is " + location2);
	    
	    decoded_message = CodeMapper.getMessage(value2, freq, m, message.length);
	    System.out.println("Decoded message from getMessage with BigIntegers:");
	    for(int i = 0; i < decoded_message.length; i++)
	    	    System.out.print(decoded_message[i] + " ");
	    System.out.println();
	}
}