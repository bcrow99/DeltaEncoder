import java.util.*;
import java.util.zip.*;
import java.lang.Math.*;

public class ResizeMapper
{
	// These functions accept arbitrary dimensions.
	public static int [] resizeX(int src[], int xdim, int new_xdim)
	{
		int ydim = src.length / xdim;
		int [] dst = new int[new_xdim * ydim];
		
		if(new_xdim == xdim)
		    for(int i = 0; i < xdim * ydim; i++)	
		    	dst[i] = src[i];
		else if(new_xdim < xdim)
		{
		    int delta               = xdim - new_xdim;
		    int number_of_segments  = delta + 1;
		    int segment_length      = xdim / number_of_segments;
		    int last_segment_length = segment_length + xdim % number_of_segments;
		    
		    int m = 0;
		    for(int i = 0; i < ydim; i++)
		    {
		    	int start = i * xdim;
		    	int stop  = start + segment_length - 1;
		    	for(int j = 0; j < number_of_segments - 1; j++)
		    	{
		    	    for(int k = start; k < stop; k++)
		    	        dst[m++] = src[k];  
		    	    start   += segment_length;
	    	        stop     = start + segment_length - 1;
		    	}
		    	stop            = start + last_segment_length;
		    	for(int k = start; k < stop; k++)
	    	        dst[m++] = src[k];  
		    }
		}
		else if(new_xdim > xdim)
		{
			int delta                 = new_xdim - xdim;
		    int number_of_segments    = delta + 1;
		    int segment_length        = xdim / number_of_segments;
		    int last_segment_length   = segment_length + xdim % number_of_segments;
		    
		    int m = 0;
		    for(int i = 0; i < ydim; i++)
		    {
		    	int start = i * xdim;
		    	int stop  = start + segment_length;
		    	
		    	for(int j = 0; j < number_of_segments - 1; j++)
		    	{
		    	    for(int k = start; k < stop; k++)
		    	        dst[m++] = src[k];
		    	    dst[m++] = (src[stop] + src[stop - 1]) / 2;
		    	    start   += segment_length;
	    	        stop     = start + segment_length;
		    	}
		    	// Write the values from the last segment without adding a pixel.
		    	stop = start + last_segment_length;
		    	for(int k = start; k < stop; k++)
	    	        dst[m++] = src[k];
		    }
		}
		return dst;
	}
	
	public static int [] resizeY(int src[], int xdim, int new_ydim)
	{
		int ydim = src.length / xdim;
		int [] dst = new int[xdim * new_ydim];
		
		if(new_ydim == ydim)
			for(int i = 0; i < xdim * ydim; i++)	
		    	dst[i] = src[i];	
		else if(new_ydim < ydim)
		{
			int delta                 = ydim - new_ydim;
		    int number_of_segments    = delta + 1;
		    int segment_length        = ydim / number_of_segments;
		    int last_segment_length   = segment_length + ydim % number_of_segments;
		    
		    int m = 0;
		    for(int i = 0; i < xdim; i++)
		    { 
		    	m         = i;
		    	int start = i;
		    	int stop  = start + segment_length * xdim - xdim;	
		    	for(int j = 0; j < number_of_segments - 1; j++)
		    	{
		    	    for(int k = start; k < stop; k += xdim) 
		    	    {
		    	    	dst[m] = src[k];
		    	    	m += xdim;
		    	    }
		    	    
		    	    start = stop + xdim;
		    	    stop  = start + segment_length * xdim - xdim;
		    	}
		    	stop = start + last_segment_length * xdim;
		    	for(int k = start; k < stop; k += xdim) 
	    	    {
	    	    	dst[m] = src[k];
	    	    	m += xdim;
	    	    }
		    }
		}
		else if(new_ydim > ydim)
		{
			int delta                 = new_ydim - ydim;
		    int number_of_segments    = delta + 1;
		    int segment_length        = ydim / number_of_segments;
		    int last_segment_length   = segment_length + ydim % number_of_segments;
		    
		    int m = 0;
		    for(int i = 0; i < xdim; i++)
		    {
		    	m = i;
		    	int start = i;
		    	int stop  = start + segment_length * xdim;
		    	
		    	for(int j = 0; j < number_of_segments - 1; j++)
		    	{
		    	    for(int k = start; k < stop; k += xdim) 
		    	    {
		    	    	dst[m] = src[k];
		    	    	m += xdim;
		    	    }
		    	    // We add a pixel at the end of each segment.
		    	    dst[m] = (src[stop] + src[stop - xdim]) / 2;
		    	    m     += xdim;
		    	    start  = stop;
		    	    stop   = start + segment_length * xdim; 
		    	}
		    	
		    	// We write the last segment without adding a pixel.
		    	stop = start + last_segment_length * xdim;
		    	for(int k = start; k < stop; k += xdim) 
	    	    {
	    	    	dst[m] = src[k];
	    	    	m += xdim;
	    	    }
		    }
		}
		
		return dst;
	}
	
	public static ArrayList getColumnList(int [] src, int xdim, int ydim, int interval)
    {
    	ArrayList column_list = new ArrayList();
    	for(int j = interval - 1; j < (xdim - interval); j += interval)
        {
        	int [] column = new int[ydim];
        	int k = 0;
            for(int i = 0; i < ydim; i++)
                column[k++] = src[i * xdim + j]	;
            column_list.add(column);
        }
    	return column_list;
    }
	public static ArrayList getColumnListDifference(int [] src, int xdim, int ydim, int interval)
    {
    	ArrayList column_list = new ArrayList();
    	for(int j = interval - 1; j < (xdim - interval); j += interval)
        {
        	int [] column = new int[ydim];
            for(int i = 0; i < ydim; i++)
            {
            	int k      = i * xdim + j;
                column[i]  = src[k];
                column[i] -= (src[k] + src[k + 1]) / 2;
            }
            column_list.add(column);
        }
    	return column_list;
    }
   
	public static ArrayList getRowList(int [] src, int xdim, int ydim, int interval)
    {
    	ArrayList row_list = new ArrayList();
    	
    	for(int i = interval - 1; i < (ydim - interval); i += interval)
        {
    		int [] row = new int[xdim];
    		for(int j = 0; j < xdim; j++)
    		{
    			int k = i * xdim + j;
        	    row[j]  = src[k]; 
    		}
            row_list.add(row);
        }
    	return row_list;
    }
   
	public static ArrayList getRowListDifference(int [] src, int xdim, int ydim, int interval)
    {
    	ArrayList row_list = new ArrayList();
    	
    	for(int i = interval - 1; i < (ydim - interval); i += interval)
        {
    		int [] row = new int[xdim];
    		for(int j = 0; j < xdim; j++)
    		{
    			int k   = i * xdim + j;
        	    row[j]  = src[k]; 
        	    row[j] -= (src[k] + src[k + xdim]) / 2;
        	    
    		}
            row_list.add(row);
        }
    	return row_list;
    }
	
	public static ArrayList resizeDown(int src[], int xdim, int new_xdim, int new_ydim)
	{
		
		ArrayList resize_list = new ArrayList();
		
		
	    int ydim                = src.length / xdim;
	    
	    int delta               = xdim - new_xdim;
	    int number_of_segments  = delta + 1;
	    int interval            = xdim / number_of_segments;
	    ArrayList column_list = ResizeMapper.getColumnListDifference(src, xdim, ydim, interval);
		int [] tmp = resizeX(src, xdim, new_xdim);
		
		delta               = ydim - new_ydim;
	    number_of_segments  = delta + 1;
	    interval            = ydim / number_of_segments;
	    ArrayList row_list = ResizeMapper.getRowListDifference(tmp, new_xdim, ydim, interval);
		int [] dst = resizeY(tmp, new_xdim, new_ydim);
		
		resize_list.add(dst);
		resize_list.add(column_list);
		resize_list.add(row_list);
		
		return resize_list;
	}
	
	public static int [] resizeUp(ArrayList src_list, int xdim, int new_xdim, int new_ydim)
	{
		int [] src = (int [])src_list.get(0);
		int ydim   = src.length / xdim;
		int [] tmp = resizeY(src, xdim, new_ydim);
		int delta              = new_ydim - ydim;
		int number_of_segments = delta + 1;
		int interval           = new_ydim / number_of_segments;
		ArrayList row_list    = (ArrayList)src_list.get(2);
		int k = 0;
		for(int i = interval - 1; i < (new_ydim - interval); i += interval)
        {
    		int [] row = (int [])row_list.get(k);
    		k++;
    	
    		for(int j = 0; j < xdim; j++)
        	    tmp[i * xdim + j] += row[j];  
        }
		
		int [] dst = resizeX(tmp, xdim, new_xdim);
		delta                 = new_xdim - xdim;
		number_of_segments    = delta + 1;
		interval              = new_xdim / number_of_segments;
		ArrayList column_list = (ArrayList)src_list.get(1);
		k = 0;
		for(int j = interval - 1; j < (new_xdim - interval); j += interval)
        {
        	int [] column = (int [])column_list.get(k);
        	k++;
        	
            for(int i = 0; i < ydim; i++)
                dst[i * new_xdim + j] += column[i]; 
        }
		return dst;
	}
	
	public static int [] resize(int src[], int xdim, int new_xdim, int new_ydim)
	{
		int [] tmp = resizeX(src, xdim, new_xdim);
		int [] dst = resizeY(tmp, new_xdim, new_ydim);
		return dst;
	}
}