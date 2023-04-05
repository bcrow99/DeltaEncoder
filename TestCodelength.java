public class TestCodelength
{
	public static void main(String[] args)
	{
		TestCodelength tester = new TestCodelength();
	}
    
	public TestCodelength()
	{
	    int [] weight = {20, 17, 6, 3, 2, 2, 2, 1, 1, 1};
	    
	 
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
	    for(int i = 1; i < 10; i++)
	    	code[i] = code[i - 1] + (int)Math.pow(2, i);
	    for(int i = 0; i < 9; i++)
	    	length[i] = i + 1;
	    length[9] = 9;
	    length[0] = 1;
	    code[1]   = 2;
	    length[1] = 2;
	    code[2]   = 6;
	    length[2] = 3;
	    code[3]   = 14;
	    length[3] = 4;
	    code[4]   = 30;
	    length[4] = 5;
	    code[5]   = 62;
	    length[5] = 6;
	    code[6]   = 126;
	    length[6] = 7;
	    code[7]   = 254;
	    length[7] = 8;
	    code[8]   = 510;
	    length[8] = 9;
	    code[9]   = 511;
	    length[9] = 9;
	    
	    cost      = DeltaMapper.getCost(length, weight);
	    ratio  = DeltaMapper.getZeroOneRatio(code, length, weight);
	    
	    System.out.println("The unary string cost is " + cost);
	    System.out.println("The zero one ratio for the unary string code is " + String.format("%.2f", ratio));
	}
}