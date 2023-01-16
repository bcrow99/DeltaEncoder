import java.util.*;
import java.util.zip.*;
import java.lang.Math.*;

public class DeltaMapper
{
	public static int[] subsampleX(int src[], int xdim, int ydim, boolean even)
	{
		int size = xdim / 2;
		if(xdim % 2 != 0 && !even)
			size++;
	    int [] dst = new int[size * ydim];
	    
	    int start = 0;
	    if(even)
	    	start++;
	    int k = 0;
		for(int i = 0; i < ydim; i++)
		{
			for(int j = start; j < xdim; j += 2)
				dst[k++] = src[i * xdim + j];
		}
		return dst;
	}
	
	public static int[] subsampleY(int src[], int xdim, int ydim, boolean even)
	{
		int size = ydim / 2;
		if(ydim % 2 != 0 && !even)
			size++;
		int [] dst = new int[size * xdim];
		int start = 0;
	    if(even)
	    	start++;
	   
		for(int j = 0; j < xdim; j++)
		{
			 int k = j;
		     for(int i = start; i < ydim; i += 2)
		     {
				 dst[k] = src[i * xdim + j];
				 k     += xdim;
			 }
		}
		return(dst);  	
	}
	
	public static int[] subsample(int src[], int xdim, int ydim, boolean even)
	{
		int [] intermediate = subsampleX(src, xdim, ydim, even);
		int _xdim = xdim / 2;
		if(xdim % 2 != 0 && !even)
			_xdim++;
		int [] dst = subsampleY(intermediate, _xdim, ydim, even);
		
		int _ydim = ydim / 2;
		if(ydim % 2 != 0 && !even)
		    _ydim++;
		return dst;
	}
	
	// Separating the average 4 function into 2 directions
	// may offer a simpler approach to minimizing error
	// in inverse functions.
	public static double[] shrinkX(double src[], int xdim, int ydim)
	{
	    double[] dst = new double[xdim / 2 * ydim];
	    int k = 0;
	    for(int i = 0; i < ydim; i++)
	    {
	    	for(int j = 0; j < xdim; j += 2)
	    	{
	    		dst[k++] = (src[i * xdim + j] + src[i * xdim + j + 1]);
	    	}
	    }
	    return dst;
	}
	
	public static double[] shrinkY(double src[], int xdim, int ydim)
	{
	    double[] dst = new double[xdim * ydim / 2];
	     
	    for(int j = 0; j < xdim; j++)
	    {
	    	for(int i = 0; i < ydim; i += 2)
	    	{
	    		dst[i * xdim / 2 + j] = (src[i * xdim + j] + src[(i + 1) * xdim + j]);
	    	}
	    }
	    
	    return dst;
	}
	
	public static int[] shrink(int src[], int xdim, int ydim)
	{
		int [] dst = new int[(xdim - 1) * (ydim - 1)];
		
		int k = 0;
		for(int i = 0; i < ydim - 1; i++)
		{
			for(int j = 0; j < xdim - 1; j++)	
			{
				int w    = src[i * xdim + j];
				int x    = src[i * xdim + j + 1];
				int y    = src[(i + 1) * xdim + j];
				int z    = src[(i + 1) * xdim + j + 1];
			    
				// Leaving the error biased (instead of rounding up) means
			    // we are introducing less artifacts later if we
			    // adjust the contrast range.
				dst[k++] = (w + x + y + z) / 4;	
			}
		}
		return(dst);
	}
	
	public static double[] shrink(double src[], int xdim, int ydim)
	{
		double [] dst = new double[(xdim - 1) * (ydim - 1)];
		
		int k = 0;
		for(int i = 0; i < ydim - 1; i++)
		{
			for(int j = 0; j < xdim - 1; j++)	
			{
				double w = src[i * xdim + j];
				double x = src[i * xdim + j + 1];
				double y = src[(i + 1) * xdim + j];
				double z = src[(i + 1) * xdim + j + 1];
			    dst[k++] = (w + x + y + z) / 4.;	
			}
		}
		return(dst);
	}
	
	public static double[] avg4(double src[], int xdim, int ydim)
	{
		// These shrink functions accumulate values without averaging them,
		// so we can do the averaging just once.
		double [] intermediate = shrinkX(src, xdim, ydim);
		double [] dst          = shrinkY(intermediate, xdim / 2, ydim);
		
		for(int i = 0; i < dst.length; i++)
			dst[i] *= 0.25;
		return dst;
	}
	
	public static int[] shrink4(int src[], int xdim, int ydim)
	{
		int _xdim = xdim / 2;
		int _ydim = ydim / 2;
		int [] dst = new int[_xdim * _ydim];
		
		int k = 0;
		int m = 0;
		for(int i = 0; i < _ydim; i++)
		{
			int n = 0;
			for(int j = 0; j < _xdim; j++)	
			{
				int w    = src[m * xdim + n];
				int x    = src[m * xdim + n + 1];
				int y    = src[(m + 1) * xdim + n];
				int z    = src[(m + 1) * xdim + n + 1];
			    dst[k++] = (w + x + y + z) / 4;
			    n       += 2;
			}
			m += 2;
		}
		return(dst);
	}
	
	public static double[] shrink4(double src[], int xdim, int ydim)
	{
		int _xdim = xdim / 2;
		int _ydim = ydim / 2;
		double [] dst = new double[_xdim * _ydim];
		
		int k = 0;
		int m = 0;
		
		for(int i = 0; i < _ydim; i++)
		{
			int n = 0;
			for(int j = 0; j < _xdim; j++)	
			{
				double w = src[m * xdim + n];
				double x = src[m * xdim + n + 1];
				double y = src[(m + 1) * xdim + n];
				double z = src[(m + 1) * xdim + n + 1];
				
			    dst[k++] = (w + x + y + z) * 0.25;
			    n       += 2;
			}
			m += 2;
		}
		return(dst);
	}
	
	
	public static double getTotal(double src[])
	{
		int length = src.length;
		double total = 0;
		
		for(int i = 0; i < length; i++)
		{
			total += src[i];
		}
		return(total);
	}
	
	public static int getSum(int src[])
	{
		int length = src.length;
		int sum  = 0;
		
		for(int i = 0; i < length; i++)
		{
			sum += src[i];
		}
		return(sum);
	}
	
	public static int getAbsoluteSum(int src[])
	{
		int length = src.length;
		int sum  = 0;
		
		for(int i = 0; i < length; i++)
		{
			sum += Math.abs(src[i]);
		}
		return(sum);
	}
	
	public static double getAbsoluteTotal(double src[])
	{
		int length = src.length;
		double total = 0;
		
		for(int i = 0; i < length; i++)
		{
			total += Math.abs(src[i]);
		}
		return(total);
	}
	
	
	public static double[] getDifference(double src[], double src2[])
	{
		int length = src.length;
		double [] difference = new double[length];
		
		for(int i = 0; i < length; i++)
		{
			difference[i] = src[i] - src2[i];
		}
		return(difference);
	}
	
	public static int[] getDifference(int src1[], int src2[])
	{
		int length = src1.length;
		int [] difference = new int[length];
		
		// Could throw an exception here, but will
		// just return uninitialized array the same length
		// as src1.
		if(src2.length == length)
		{
		    for(int i = 0; i < length; i++)
		    {
			    difference[i] = src1[i] - src2[i];
		    }
		}
		return(difference);
	}
	
	public static int[] getSum(int src1[], int src2[])
	{
		int length = src1.length;
		int [] difference = new int[length];
		
		if(src2.length == length)  // else return uninitialzed array.
		{
		    for(int i = 0; i < length; i++)
		    {
			    difference[i] = src1[i] + src2[i];
		    }
		}
		return(difference);
	}
	
	public static int[] getHistogram(int value[], int value_range, int value_min)
	{
	    int [] histogram = new int[value_range];
	    for(int i = 0; i < value_range; i++)
	    	histogram[i] = 0;
	    for(int i = 0; i < value.length; i++)
	    {
	    	int j = value[i] - value_min;
	    	histogram[j]++;
	    }
	    return histogram;
	}
	
	public static ArrayList getHistogram(int value[])
	{  
	    int value_min = value[0];
	    int value_max = value[0];
	    for(int i = 0; i < value.length; i++)
	    {
	    	if(value[i] > value_max)
	    		value_max = value[i];
	    	if(value[i] < value_min)
	    		value_min = value[i];
	    }
	    int value_range = value_max - value_min + 1;
	    int [] histogram = new int[value_range];
	    for(int i = 0; i < value_range; i++)
	    	histogram[i] = 0;
	    for(int i = 0; i < value.length; i++)
	    {
	    	int j = value[i] - value_min;
	    	histogram[j]++;
	    }
	    
	    ArrayList histogram_list = new ArrayList();
	    histogram_list.add(value_min);
	    histogram_list.add(histogram);
	    return histogram_list;
	}
	
	public static int[] getRandomTable(int histogram[])
	{
		ArrayList key_list     = new ArrayList();
	    Hashtable rank_table   = new Hashtable();
	    for(int i = 0; i < histogram.length; i++)
	    {
	    	double key = histogram[i];
	    	while(rank_table.containsKey(key))
	    	{
	    		key +=.001;
	    	}
	    	rank_table.put(key, i);
	    	key_list.add(key);
	    }
	    Collections.sort(key_list);
	    //System.out.println("Sorted keys:");
	    int random_lut[] = new int[histogram.length];
	    int k     = -1;
	    for(int i = histogram.length - 1; i >= 0; i--)
	    {
	    	double key = (double)key_list.get(i);
	    	int    j   = (int)rank_table.get(key);
	    	random_lut[j]   = ++k;
	    }	
	    return random_lut;
	}
	
	public static int[] getModalTable(int histogram[])
	{
		int max_value = histogram[0];
		int mode      = 0;
		for(int i = 1; i < histogram.length; i++)
		{
			if(histogram[i] > max_value)
			{
				max_value = histogram[i];
				mode      = i;
			}
		}
		
		int[] modal_lut = new int[histogram.length];
		
		
		int lower_value = mode;
	    int upper_value = mode + 1;
	    
	    int index = 0;
	    modal_lut[lower_value] = index++;
	    if(upper_value < histogram.length)
	    	modal_lut[upper_value] = index++;
	    else
	    {
	    	lower_value--;
	    	modal_lut[lower_value] = index++;
	    }
	    
	    for(int i = 2; i < histogram.length; i += 2)
	    {
	        if(lower_value > 0)	
	        {
	        	lower_value--;
	        	modal_lut[lower_value] = index++;
	        	upper_value++;
	        	if(upper_value < histogram.length)
	        		modal_lut[upper_value] = index++;
	        	else
	        	{
	        		if(lower_value > 0)
	        		{
	        		    lower_value--;
	        		    modal_lut[lower_value] = index++;   
	        		}
	        	}
	        }
	        else
	        {
	        	upper_value++;
	        	modal_lut[upper_value] = index++;
	        	if(upper_value < histogram.length - 1)
	        	{
	        		upper_value++;
	                modal_lut[upper_value] = index++;
	        	}
	        }
	    }
	    
	    if(histogram.length % 2 == 1)
	    {
	    	if(lower_value > 0)
	    		modal_lut[0] = 0;	
	    	else
	    		modal_lut[histogram.length - 1] = histogram.length - 1;
	    }
		return modal_lut;
	}
	
	
	
	public static int[] getSymmetricTable(int size)
	{
		int[] symmetric_lut = new int[size];
		
		int i = 0;
	    int j = 0;
	        
	    if(size % 2 == 1)
	        j = size / 2;
	    else
	        j = size / 2 - 1;
	        
	    symmetric_lut[j--] = i;
	    i += 2;
	    while(j >= 0)
	    {
	        symmetric_lut[j--] = i;
	        i += 2;
	    }
	        
	    if(size % 2 == 1)
	        j = size / 2 + 1;
	    else
	        j = size / 2;
	        
	    i = 1;
	    symmetric_lut[j++] = i;
	    i += 2;
	    while(j < size)
	    {
	        symmetric_lut[j++] = i;
	        i += 2;
	    }
	    return symmetric_lut;
	}

	public static int[][] expandX(int src[][])
	{
		int ydim = src.length;
		int xdim = src[0].length;
		
		int _xdim = 2 * xdim - 1;
		
		int [][] dst = new int[ydim][_xdim];
		for(int i = 0; i < ydim; i++)
		{
			for(int j = 0; j < xdim - 1; j++)
			{
				dst[i][j * 2]     = src[i][j];
				dst[i][j * 2 + 1] = (src[i][j] + src[i][j + 1]) / 2;
			}
			dst[i][_xdim - 1] = src[i][xdim - 1];
		}
		return(dst);
	}
	
	public static int[][] expandX(int src[][], int expand)
	{
		int ydim = src.length;
		int xdim = src[0].length;
		
		int _xdim = (xdim - 1) * expand + xdim;
		
		int [][] dst = new int[ydim][_xdim];
		for(int i = 0; i < ydim; i++)
		{
			int k = 0;
			int end_value = 0;
			for(int j = 0; j < xdim - 1; j++)
			{
				int start_value  = src[i][j];
				end_value        = src[i][j + 1];
				dst[i][k++]      = start_value;
				double delta     = start_value - end_value;
				double increment = delta / (expand + 1);
				for(int m = 0; m < expand; m++)
				{
					start_value += increment;
					dst[i][k++]  = start_value;
				}
			}
			dst[i][k] = end_value;
		}
		return(dst);
	}
	
	public static int[] expandX(int src[], int xdim, int ydim, int expand)
	{
		int _xdim = (xdim - 1) * expand + xdim;
		
		int [] dst = new int[ydim * _xdim];
		for(int i = 0; i < ydim; i++)
		{
			int k = 0;
			int end_value = 0;
			for(int j = 0; j < xdim - 1; j++)
			{
				int start_value  = src[i * xdim + j];
				end_value        = src[i * xdim + j + 1];
				dst[k++]         = start_value;
				double delta     = start_value - end_value;
				double increment = delta / (expand + 1);
				for(int m = 0; m < expand; m++)
				{
					start_value += increment;
					dst[k++]     = start_value;
				}
			}
			dst[k++] = end_value;
		}
		return(dst);
	}
	
	/*
	public static double[] expandX(double src[], int xdim, int ydim)
	{
		int _xdim = (xdim - 1) * expand + xdim;
		
		int [] dst = new int[ydim * _xdim];
		for(int i = 0; i < ydim; i++)
		{
			int k = 0;
			int end_value = 0;
			for(int j = 0; j < xdim - 1; j++)
			{
				int start_value  = src[i * xdim + j];
				end_value        = src[i * xdim + j + 1];
				dst[k++]         = start_value;
				double delta     = start_value - end_value;
				double increment = delta / (expand + 1);
				for(int m = 0; m < expand; m++)
				{
					start_value += increment;
					dst[k++]     = start_value;
				}
			}
			dst[k++] = end_value;
		}
		return(dst);
	}
	*/
	
	public static int[][] expandY(int src[][])
	{
		int ydim = src.length;
		int xdim = src[0].length;
		
		int _ydim = 2 * ydim - 1;
		
		int [][] dst = new int[_ydim][xdim];
		
		for(int j = 0; j < xdim; j++)
		{
		     for(int i = 0; i < ydim - 1; i++)
		     {
				 dst[i * 2][j]     = src[i][j];
				 dst[i * 2 + 1][j] = (src[i][j] + src[i + 1][j]) / 2;
			 }
		     int i = _ydim - 1;
		     dst[i][j] = src[ydim - 1][j];
		}
		return(dst);
	}
	
	public static int[] expand(int src[], int xdim, int ydim)
	{
		int[][] _src = new int[ydim][xdim];
		int k = 0;
		for(int i = 0; i < ydim; i++)
		{
			for(int j = 0; j < xdim; j++)
			{
				_src[i][j] = src[k++];
			}
		}
	    int[][] intermediate = expandX(_src);
	    int[][] _dst         = expandY(intermediate);
	    
	    int [] dst = new int[(2 * xdim - 1)*(2 * ydim - 1)];
	    k = 0;
	    for(int i = 0; i < ydim * 2 - 1; i++)
		{
			for(int j = 0; j < xdim * 2 - 1; j++)
			{
				dst[k++] = _dst[i][j];
			}
		}
	    return dst;
	}
	
	public static int[] dilateX(int src[], int xdim, int ydim)
	{
		int [] dst = new int[(xdim + 1) * ydim];
		
		int k = 0;
		
		for(int i = 0; i < ydim; i++)
		{
			for(int j = 0; j < xdim; j++)	
			{
				dst[k++] = src[i * xdim + j];
				
				/*
				if(j == xdim - 1)
					dst[k++] = src[i * xdim + j];
				*/
				if(j == xdim / 2)
					dst[k++] = src[i * xdim + j];	
			}
		}
		return dst;
	}
	
	public static int[] dilateY(int src[], int xdim, int ydim)
	{
		int [] dst = new int[(ydim + 1) * xdim];
		
		
		for(int j = 0; j < xdim; j++)
		{
			int k = j;
			for(int i = 0; i < ydim; i++)	
			{
                dst[k] = src[i * xdim + j];	
                k      += xdim;
                if(i == ydim / 2)
                {
                	dst[k] = src[i * xdim + j];	
                    k      += xdim;	
                }
			}
		}
		
		return(dst);
	}
	
	
	public static int[] dilate(int src[], int xdim, int ydim)
	{
	    int[] intermediate = dilateX(src, xdim, ydim);
	    int[] dst          = dilateY(intermediate, xdim + 1, ydim);
	    return dst;
	}
	
	public static int getVerticalDeltaSum(int src[], int xdim, int ydim)
	{
		int sum = 0;
		for(int i = 1; i < ydim - 1; i++)
		{
			for(int j = 1; j < xdim - 1; j++)
			{
			    int delta = src[i * xdim + j] - src[i * xdim + j - 1];
			    sum += Math.abs(delta);
			}
		}
		return sum;
	}
	
	public static int getHorizontalDeltaSum(int src[], int xdim, int ydim)
	{
		int sum = 0;
		for(int i = 1; i < ydim - 1; i++)
		{
			for(int j = 1; j < xdim - 1; j++)
			{
				int delta = src[i * xdim + j] - src[(i - 1) * xdim + j - 1];
			    sum += Math.abs(delta);	
			}
		}
		return sum;
	}
	
	// These functions use the sums of the vertical deltas and horizontal deltas 
	// to decide which deltas to use in compression.
	// Those values work better than the sum of the absolute values as a heuristic
	// to decide which to use.
	public static int[] getDeltasFromValues(int src[], int xdim, int ydim, int init_value)
    {
		int [] delta1     = getDeltasFromValues1(src, xdim, ydim, init_value);
		int    delta_sum1 = getHorizontalDeltaSum(delta1, xdim, ydim);
    	int [] delta2     = getDeltasFromValues2(src, xdim, ydim, init_value);
		int    delta_sum2 = getVerticalDeltaSum(delta2, xdim, ydim);
    	
		if(delta_sum1 <= delta_sum2)
		{
			System.out.println("Using horizontal deltas.");
			return delta1;
		}
		else
		{
			System.out.println("Using vertical deltas.");
			return delta2;
		}
    }
	
    public static int[] getDeltasFromValues1(int src[], int xdim, int ydim, int init_value)
    {
        int[] dst = new int[xdim * ydim];
    	
        int k     = 0;
        int value = init_value;
        for(int i = 0; i < ydim; i++)
        {
        	// We set the first value to zero to mark the type of deltas as horizontal.
            if(i == 0)
        	    dst[k++] = 0;
            else
            {
            	int delta   = src[k] - init_value;
            	dst[k++]    = delta;
            	init_value += delta;
            	value = init_value;
            }
            for(int j = 1; j < xdim; j++)
            {
                int delta      = src[k]  - value;
                value += delta;
                dst[k++]       = delta;
            }
        }
        
        return dst;
    }
    
    public static int[] getValuesFromDeltas(int src[],int xdim, int ydim, int init_value)
    {
        if(src[0] == 0)
        {
        	int [] value = getValuesFromDeltas1(src, xdim, ydim, init_value);
        	return value;
        }
        else if(src[0] == 1)
        {
        	int [] value = getValuesFromDeltas2(src, xdim, ydim, init_value);
        	return value;   	
        }
        else
        {
        	// We could throw an exception here but will just return an 
        	// uninitialized array.
        	System.out.println("Type of delta undefined.");
        	int [] value = new int[xdim * ydim];
        	return value;
        }
        	
    }
    
    // These function use horizontal deltas.
    public static int[] getValuesFromDeltas1(int src[],int xdim, int ydim, int init_value)
    {
    	int[] dst = new int[xdim * ydim];
    	
        int k     = 0;
        int value = init_value;
        for(int i = 0; i < ydim; i++)
        {
            value            += src[k];
            int current_value = value;
            dst[k++]          = current_value;
            for(int j = 1; j < xdim; j++)
            {
                current_value += src[k];
                dst[k++]       = current_value;
            }
        }
        return dst;
    }
    
    
    // These functions use vertical deltas.
    public static int[] getDeltasFromValues2(int src[], int xdim, int ydim, int init_value)
    {
        int[] dst = new int[xdim * ydim];
    	
        // Setting the first value to 1 mark the type of deltas vertical.
        int k     = 0;
        int value = init_value;
        int delta = 0;
        for(int i = 0; i < ydim; i++)
        {
            for(int j = 0; j < xdim; j++)
            {
            	if(i == 0)
            	{
            		if(j == 0)
            			dst[k++] = 1;
            		else
            		{
            		    delta     = src[k] - value;
                        value     += delta;
                        dst[k++]  = delta;
            		}
            	}
            	else
            	{
                    delta          = src[k]  - src[k - xdim];
                    dst[k++]       = delta;
            	}
            }
        }
        return dst;
    }

    public static int[] getValuesFromDeltas2(int src[],int xdim, int ydim, int init_value)
    {
    	int[] dst = new int[xdim * ydim];
    	
    	// We know the first value in the source data is 0.
    	// We'll use that to mark the type of deltas.
        dst[0] = init_value;
    	int k     = 1;
        int value = init_value;
        
        for(int i = 1; i < xdim; i++)
        {
        	value   += src[i];
        	dst[k++] = value;
        }
        // Now we can use values from the destination data. 
        for(int i = 1; i < ydim; i++)
        {
            for(int j = 0; j < xdim; j++)
            {
            	dst[k] = dst[k - xdim] + src[k];
                k++;   
            }
        }
        return dst;
    }
    
    public static int[] getDeltasFromValues(int src[])
    {
    	int   length = src.length;
        int[] dst = new int[length];
    	
        int value  = src[0];
        dst[0]     = value;
        int j      = 0;
        for(int i = 1; i < length; i++)
        {
            int delta     = src[i] - value;
            value        += delta;
            dst[i]      = delta;
        }
        return dst;
    }

    public static int[] getValuesFromDeltas(int src[])
    {
    	int length = src.length;
    	int[] dst = new int[length];
    	
        int value = src[0];
        dst[0]    = value;
        for(int i = 1; i < length; i++)
        {
            value  = dst[i - 1];
            value += src[i];
            dst[i] = value;
        }
        return dst;
    }

    // These packing/unpacking functions represent int values
    // as unary strings.
    // This set of functions makes no assumptions about the 
    // the maxiumum length of an individual string.
    public static int packStrings(int src[], int table[], byte dst[])
    {
        int size             = src.length;
        for(int i = 0; i < dst.length; i++)
        	dst[i] = 0;
        int [] mask  = new int[8];
        
        mask[0] = 1;
        mask[1] = 3;
        mask[2] = 7;
        mask[3] = 15;
        mask[4] = 31;
        mask[5] = 63;
        mask[6] = 127;
        mask[7] = 255;
    
        int start_bit  = 0;
        int stop_bit   = 0;
        int p   = 0;
        dst[p]  = 0;
        
        for(int i = 0; i < size; i++)
        {
            int j = src[i];
            int k = table[j];
            if(k == 0)
            {
                start_bit++;
                if(start_bit == 8)
                {
                    dst[++p] = 0;
                    start_bit       = 0;
                }
            }
            else
            {
                stop_bit = (start_bit + k + 1) % 8;
                
                if(k <= 7)
                {
                    dst[p] |= (byte) (mask[k - 1] << start_bit);
                    if(stop_bit <= start_bit)
                    {
                        dst[++p] = 0;
                        if(stop_bit != 0)
                            dst[p] |= (byte)(mask[k - 1] >> (8 - start_bit));
                    }
                }
                else if(k > 7)
                {
                	dst[p] |= (byte)(mask[7] << start_bit);
            		int m = (k - 8) / 8;
                    for(int n = 0; n < m; n++)
                        dst[++p] = (byte)(mask[7]);
                    dst[++p] = 0;
                    if(start_bit != 0)
                        dst[p] |= (byte)(mask[7] >> (8 - start_bit));	
                    
                    if(k % 8 != 0)
                    {
                        m = k % 8 - 1;
                        dst[p] |= (byte)(mask[m] << start_bit);
                        if(stop_bit <= start_bit)
                        {
                            dst[++p] = 0;
                            if(stop_bit != 0)
                                dst[p] |= (byte)(mask[m] >> (8 - start_bit));
                        }
                    }
                    else if(stop_bit <= start_bit)
                            dst[++p] = 0;
                }
                start_bit = stop_bit;
            }
        }
        if(start_bit != 0)
            p++;
        int number_of_bits = p * 8;
        if(start_bit != 0)
            number_of_bits -= 8 - start_bit;
        return(number_of_bits);
    }
    
    public static int unpackStrings(byte src[], int table[], int dst[])
    {
        for(int i = 0; i < dst.length; i++)
        	dst[i] = 0;
    	
    	int size                       = dst.length;
        int number_of_different_values = table.length;
        int number_unpacked            = 0;
        
        
        int [] inverse_table = new int[number_of_different_values];
        for(int i = 0; i < number_of_different_values; i++)
        {
            int j            = table[i];
            inverse_table[j] = i;
        }
        
        int length   = 1;
        int src_byte = 0;
        int dst_byte = 0;
        byte mask    = 0x01;
        byte bit     = 0;
        
        while(dst_byte < size)
        {
            byte non_zero = (byte)(src[src_byte] & (byte)(mask << bit));
            if(non_zero != 0)
                length++;
            else
            {
                int k = length - 1;
                dst[dst_byte++] = inverse_table[k];
                number_unpacked++;
                length = 1;
            }
            bit++;
            if(bit == 8)
            {
                bit = 0;
                src_byte++;
            }
        }
        return(number_unpacked);
    }
    
    // These packing/unpacking functions use the maximum length
    // code to dispense with a stop bit in the longest string.
    // Not very significant until we are looking at binary and
    // low resolution images.
    public static int packStrings2(int src[], int table[], byte dst[])
    {
    	int size             = src.length;
    	int number_of_values = table.length;
    	
    	int maximum_length = number_of_values - 1;
    
        int [] mask  = new int[8];
        
        mask[0] = 1;
        mask[1] = 3;
        mask[2] = 7;
        mask[3] = 15;
        mask[4] = 31;
        mask[5] = 63;
        mask[6] = 127;
        mask[7] = 255;
        
    
        int start_bit  = 0;
        int stop_bit   = 0;
        int p   = 0;
        dst[p]  = 0;
        
        for(int i = 0; i < size; i++)
        {
            int j = src[i];
            int k = table[j];
            
            if(k == 0)
            {
                start_bit++;
                if(start_bit == 8)
                {
                    dst[++p] = 0;
                    start_bit       = 0;
                }
            }
            else
            {
                stop_bit = (start_bit + k + 1) % 8;
                if(k == maximum_length)
                {
                	stop_bit--;
                	if(stop_bit < 0)
                		stop_bit = 7;
                }
                
                if(k <= 7)
                {
                	dst[p] |= (byte) (mask[k - 1] << start_bit);
                	
                    if(stop_bit <= start_bit)
                    {
                        dst[++p] = 0;
                        if(stop_bit != 0)
                        {
                            dst[p] |= (byte)(mask[k - 1] >> (8 - start_bit));
                        }
                    }
                }
                else if(k > 7)
                {
                	dst[p] |= (byte)(mask[7] << start_bit);
            		int m = (k - 8) / 8;
                    for(int n = 0; n < m; n++)
                        dst[++p] = (byte)(mask[7]);
                    dst[++p] = 0;
                    
                    if(start_bit != 0)
                        dst[p] |= (byte)(mask[7] >> (8 - start_bit));	
                    
                    if(k % 8 != 0)
                    {
                        m = k % 8 - 1;
                        
                        dst[p] |= (byte)(mask[m] << start_bit);
                        
                        if(stop_bit <= start_bit)
                        {
                            dst[++p] = 0;
                            if(stop_bit != 0)
                            {
                                dst[p] |= (byte)(mask[m] >> (8 - start_bit));
                            }
                        }
                    }
                    // If this is the maximum_length index and it's a multiple of 8,
                    // then we already incremeted the index and then reset the stop bit.
                    // Don't want to do it twice.   Very tricky bug.
                    else if(stop_bit <= start_bit && k != maximum_length)
                            dst[++p] = 0;
                }
                start_bit = stop_bit;
            }
        }
        
        if(start_bit != 0)
            p++;
        int number_of_bits = p * 8;
        if(start_bit != 0)
            number_of_bits -= 8 - start_bit;
        return(number_of_bits);
    }
    
    
    public static int unpackStrings2(byte src[], int table[], int dst[])
    {
    	for(int i = 0; i < dst.length; i++)
        	dst[i] = 0;
        int size             = dst.length;
        int number_of_values = table.length;
        int number_unpacked  = 0;
        int maximum_length   = number_of_values - 1;
   
        int [] index = new int[number_of_values];
        
        int [] inverse_table = new int[number_of_values];
        for(int i = 0; i < number_of_values; i++)
        {
            int j            = table[i];
            inverse_table[j] = i;
            index[i]         = 0;
        }
        
        int length   = 1;
        int src_byte = 0;
        int dst_byte = 0;
        
        
        byte mask = 0x01;
        byte bit  = 0;
        
        while(dst_byte < size)
        {
            byte non_zero = (byte)(src[src_byte] & (byte)(mask << bit));
            if(non_zero != 0 && length < maximum_length)
                length++;
            else if(non_zero == 0)
            {
                int k = length - 1;
                dst[dst_byte++] = inverse_table[k]; 
               
                index[k]++;
                
                number_unpacked++;
                length = 1;
            }
            else if(length == maximum_length)
            {
            	int k = length;
            	dst[dst_byte++] = inverse_table[k];
            	
                index[k]++;
                
                number_unpacked++;
                length = 1;
            }
            bit++;
            if(bit == 8)
            {
                bit = 0;
                src_byte++;
            }
        }
        
        return(number_unpacked);
    }
    
    public static int compressZeroBits(byte src[], int size, byte dst[])
    {
    	// Not using this currently, but gives us a handle on the
    	// ratio of zero and one bits, which is a good predictor
    	// of whether or not the bit transform will increase or
    	// decrease the length of the bit string, instead of just
    	// going ahead and doing the transform and checking the 
    	// result.
        int number_of_zero_bits = 0;
        
        for(int i = 0; i < dst.length; i++)
        	dst[i] = 0;
        int current_byte        = 0;
        int current_bit         = 0;
        byte mask               = 0x01;
        dst[0]                  = 0;
        
        int i = 0;
        int j = 0;
        int k = 0;
        for(i = 0; i < size; i++)
        {
            if((src[k] & (mask << j)) == 0 && i < size - 1)
            {
               i++;
               j++;
                
               if(j == 8)
               {
                   j = 0;
                   k++;
               }
               if((src[k] & (mask << j)) == 0)
               {
                   // Current bit is a 0.
                   number_of_zero_bits++;
                   current_bit++;
                   if(current_bit == 8)
                   {
                       current_byte++;
                       current_bit = dst[current_byte] = 0;
                   }
               }
               else
               {
                   // Current bit is a 1.
                   dst[current_byte] |= (byte)mask << current_bit;
                   current_bit++;
                   if(current_bit == 8)
                   {
                       current_byte++;
                       current_bit = dst[current_byte] = 0;
                   }
                   dst[current_byte] |= (byte)mask << current_bit;
                   current_bit++;
                   if(current_bit == 8)
                   {
                       current_byte++;
                       current_bit = dst[current_byte] = 0;
                   }
               }
            }
            else if(i == size -1) // We're at the end of the string and we have an odd 0.
            {
                // Put a 1 down to signal that there is an odd 0.
            	// This works for single iterations but fails in the recursive case.
            	// Padding the input makes recursion work since the only values that
            	// get corrupted in the recursion are at the end of the string.
            	// There might be another way to preserve the values at the end of the
            	// string but it gets pretty complicated.  Adding a byte to the
            	// input is easy.
            	dst[current_byte] |= (byte)mask << current_bit;
                current_bit++;
                if(current_bit == 8)
                {
                    current_byte++;
                    current_bit = dst[current_byte] = 0;
                }
            }
            else
            {
            	// Current first bit is a 1.
                dst[current_byte] |= (byte)mask << current_bit;
                current_bit++;
                if(current_bit == 8)
                {
                    current_byte++;
                    current_bit = dst[current_byte] = 0;
                }
                number_of_zero_bits++;
                current_bit++;
                if(current_bit == 8)
                {
                    current_byte++;
                    current_bit = dst[current_byte] = 0;
                }
            }
            j++;
            if(j == 8)
            {
                j = 0;
                k++;
            }
        }    
        int number_of_bits = current_byte * 8;
        number_of_bits    += current_bit;
        
        return(number_of_bits);
    }

    public static int decompressZeroBits(byte src[], int size, byte dst[])
    {
    	for(int i = 0; i < dst.length; i++)
        	dst[i] = 0;
    	
        int  current_byte = 0;
        int  current_bit  = 0;
        byte mask         = 0x01;
        dst[0]            = 0;
        
        int j = 0;
        int k = 0;
      
        for(int i = 0; i < size; i++)
        {
            if(((src[k] & (mask << j)) != 0) && i < size - 1)
            {
                i++;
                j++;
                if(j == 8)
                {
                    j = 0;
                    k++;
                }
                if((src[k] & (mask << j)) != 0)
                {
                    current_bit++;
                    if(current_bit == 8)
                    {
                        current_byte++;
                        current_bit = dst[current_byte] = 0;
                    }
                    dst[current_byte] |= (byte)mask << current_bit;
                    current_bit++;
                    if(current_bit == 8)
                    {
                        current_byte++;
                        current_bit = dst[current_byte] = 0;
                    }
                }
                else
                {
                    dst[current_byte] |= (byte)mask << current_bit;
                    current_bit++;
                    if(current_bit == 8)
                    {
                        current_byte++;
                        current_bit = dst[current_byte] = 0;
                    }
                }
            }
            else if(((src[k] & (mask << j)) != 0) && i == size - 1)
            {
            	// Append an odd 0.
            	current_bit++;
                if(current_bit == 8)
                {
                    current_byte++;
                    current_bit = dst[current_byte] = 0;
                }	
            }
            
            else if((src[k] & (mask << j)) == 0)
            {
                current_bit++;
                if(current_bit == 8)
                {
                    current_byte++;
                    if(current_byte < dst.length)
                        current_bit = dst[current_byte] = 0;
                }
                current_bit++;
                if(current_bit == 8)
                {
                    current_byte++;
                    if(current_byte < dst.length)
                        current_bit = dst[current_byte] = 0;
                }
            }
            
            j++;
            if(j == 8)
            {
                j = 0;
                k++;
            }
        }    
       
        int number_of_bits = current_byte * 8;
        number_of_bits += current_bit;
        
        return(number_of_bits);
    }
   
    public static int compressStrings(byte src[], int size, byte dst[])
    {
        byte[]  temp = new byte[src.length * 10];
        
        int number_of_iterations = 1;
        int previous_size        = size;
        int current_size         = compressZeroBits(src, previous_size, dst);
        while(current_size < previous_size)
        {
            previous_size = current_size;
            if(number_of_iterations % 2 == 1)
            {
                current_size = compressZeroBits(dst, previous_size, temp);
            }
            else
            {
                current_size = compressZeroBits(temp, previous_size, dst);
            }
            number_of_iterations++;
        }
        if(number_of_iterations > 1)
        {
        	// This means our first pass did not expand the data,
        	// and we broke out of a loop when we finally did.
            current_size = previous_size;
            number_of_iterations--;
        }
        if(number_of_iterations % 2 == 0) 
        {
        	// The last recursion used temp as a source,
        	// which then produced a string longer than the previous one,
        	// so we need to copy the data from temp to dst.
            int byte_size = current_size / 8;
            if(current_size % 8 != 0)
                byte_size++; 
            for(int i = 0; i < byte_size; i++)
                dst[i] = temp[i];
        }   
        // else the result is already in dst.
    
        if(current_size % 8 == 0)
        {
            int byte_size      = current_size / 8;
            dst[byte_size] = (byte) number_of_iterations;
            dst[byte_size] &= 127;
        }
        else
        {
            int  remainder = current_size % 8;
            int byte_size = current_size / 8;

            dst[byte_size] |= (byte) (number_of_iterations << remainder);
            byte_size++;
            dst[byte_size] = 0;
            if(remainder > 1)
                dst[byte_size] = (byte) (number_of_iterations >> 8 - remainder);
        }
        return(current_size + 8);
    }
    
   
    
    public static int decompressStrings(byte src[], int size, byte dst[])
    {
        int  byte_index;
        int  number_of_iterations;
        int  addend;
        int  mask;
        int  remainder, i;
        int  previous_size, current_size, byte_size;
        byte[]  temp = new byte[dst.length];
        
        // Getting the number of iterations appended to
        // the end of the string.
        byte_index = size / 8 - 1;
        remainder  = size % 8;
        if(remainder != 0)
        {
            int value = 254;
            addend = 2;
            for(i = 1; i < remainder; i++)
            {
                value -= addend;
                addend <<= 1;
            } 
            mask = value;
            number_of_iterations = src[byte_index];
            if(number_of_iterations < 0)
                number_of_iterations += 256;
            number_of_iterations &= mask;
            number_of_iterations >>= remainder;
            byte_index++;
            if(remainder > 1)
            {
                mask = 1;
                for(i = 2; i < remainder; i++)
                {
                    mask <<= 1;
                    mask++;
                }
                addend = src[byte_index]; 
                if(addend < 0)
                    addend += 256;
                addend &= mask;
                addend <<= 8 - remainder;
                number_of_iterations += addend;
                mask++;
            }
            else
                mask = 1;
        }
        else
        {
           mask = 127;
           number_of_iterations = src[byte_index];
           if(number_of_iterations < 0)
               number_of_iterations += 256; 
           number_of_iterations &= mask;
           mask++;
        }
        
        //System.out.println("The number of iterations was " + number_of_iterations);
        
        current_size = 0;
        if(number_of_iterations == 1)
        {
            current_size = decompressZeroBits(src, size - 8, dst);
            number_of_iterations--;
        }
        else if(number_of_iterations % 2 == 0)
        {
            current_size = decompressZeroBits(src, size - 8, temp);
            number_of_iterations--;
            while(number_of_iterations > 0)
            {
                previous_size = current_size;
                if(number_of_iterations % 2 == 0)
                    current_size = decompressZeroBits(dst, previous_size, temp);
                else
                    current_size = decompressZeroBits(temp, previous_size, dst);
                number_of_iterations--;
            }
        }
        else
        {
            current_size = decompressZeroBits(src, size - 8, dst);
            number_of_iterations--;
            while(number_of_iterations > 0)
            {
                previous_size = current_size;
                if(number_of_iterations % 2 == 0)
                    current_size = decompressZeroBits(dst, previous_size, temp);
                else
                    current_size = decompressZeroBits(temp, previous_size, dst);
                number_of_iterations--;
            }
        }
        return(current_size);
    }
}