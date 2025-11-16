import java.util.ArrayList;

public class TestRange
{
	public static void main(String[] args)
	{
		TestRange test = new TestRange();
	}	
	
	public TestRange()
	{
		/*
	    byte [] message = new byte[9];
	    message[0]      = 0;
	    message[1]      = 0;
	    message[2]      = 0;
	    message[3]      = 1;
	    message[4]      = 1;
	    message[5]      = 1;
	    message[3]      = 2;
	    message[4]      = 2;
	    message[5]      = 2;
	    
	    int [] freq = new int [] {1, 1, 1};
	    int    n    = 3;
		*/
		
	
		byte [] message = new byte[3];
		message[0] = 0;
		message[1] = 1;
		message[2] = 1;
	    
	    int [] freq = new int [] {1, 1};
	    int    n    = 2;
	    
	    
	    ArrayList list = CodeMapper.getRangeQuotient(message, freq, n);
	    long [] value = (long [])list.get(0);
	    double location = value[0];
	    location       /= value[1];
	    System.out.println("Location of message in probabilistic space is " + location);
	    System.out.println();
	   
	    byte [] decoded_message = CodeMapper.getMessage(value, freq, 2, message.length);
	    System.out.println("Decoded message:");
	    for(int i = 0; i < decoded_message.length; i++)
	    	    System.out.print(decoded_message[i] + " ");
	    System.out.println();
	    
	}
}