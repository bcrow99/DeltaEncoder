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
	   
	    int [] code = DeltaMapper.getCanonicalCode(length);
	   
	    for(int i = 0; i < code.length; i++)
	    	System.out.print(code[i] + " ");
	    System.out.println();
	}
}