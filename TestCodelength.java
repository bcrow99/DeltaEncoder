public class TestCodelength
{
	public static void main(String[] args)
	{
		TestCodelength tester = new TestCodelength();
	}
    
	public TestCodelength()
	{
	    int [] weight = {29, 17, 3, 1, 1};
	    
	 
	    double limit     = DeltaMapper.getShannonLimit(weight);
	    System.out.println("The shannon limit is " + String.format("%.2f", limit));
	    System.out.println();
	    
	    int [] length = DeltaMapper.getHuffmanLength(weight);
	   
	    int [] code   = DeltaMapper.getCanonicalCode(length);
	   
	    int n = length.length; 
	   
	    n = code.length;
	    
	   
	    int cost      = DeltaMapper.getCost(length, weight);
	    
	    double ratio  = DeltaMapper.getZeroOneRatio(code, length, weight);
	    
	    System.out.println("The huffman cost is " + cost);
	    System.out.println("The zero one ratio for the huffman code is " + String.format("%.2f", ratio));
	    System.out.println();
	    
	    code[0]   = 0;
	    length[0] = 1;
	    code[1]   = 2;
	    length[1] = 2;
	    code[2]   = 6;
	    length[2] = 3;
	    code[3]   = 14;
	    length[3] = 4;
	    code[4]   = 15;
	    length[4] = 4;
	    
	    cost      = DeltaMapper.getCost(length, weight);
	    ratio  = DeltaMapper.getZeroOneRatio(code, length, weight);
	    
	    System.out.println("The unary string cost is " + cost);
	    System.out.println("The zero one ratio for the unary string code is " + String.format("%.2f", ratio));
	}
}