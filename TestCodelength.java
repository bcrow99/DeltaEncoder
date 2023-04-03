public class TestCodelength
{
	public static void main(String[] args)
	{
		TestCodelength tester = new TestCodelength();
	}
    
	public TestCodelength()
	{
	    int [] weight = {20, 17, 6, 3, 2, 2, 2, 1, 1, 1};
	    
	    int [] length = DeltaMapper.getHuffmanLength(weight);
	    
	    //System.out.println("Got here.");
	    for(int i = 0; i < length.length; i++)
	    	System.out.print(length[i] + " ");
	    System.out.println();
	}
}