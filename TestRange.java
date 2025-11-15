import java.util.ArrayList;

public class TestRange
{
	public static void main(String[] args)
	{
		TestRange test = new TestRange();
	}	
	
	public TestRange()
	{
	    byte [] message = new byte[3];
	    message[0]      = 1;
	    message[1]      = 1;
	    message[2]      = 1;
	   
	    double [] p = new double [] {.5, .5};
	    
	    ArrayList list = CodeMapper.getRangeList2(message, p);
	    
	    double location = (double)list.get(0);
	    System.out.println("Location of message in probabilistic space is " + location);
	    System.out.println();
	   
	    byte [] decoded_message = CodeMapper.getMessageFromRangeList2(list);
	    System.out.println("Decoded message:");
	    for(int i = 0; i < decoded_message.length; i++)
	    {
	    	    System.out.print(decoded_message[i] + " ");
	    }
	    System.out.println();
	}
}