import java.awt.*;
import java.awt.image.*;
import java.io.*;
import java.awt.event.*;
import java.util.*;
import java.util.zip.*;
import java.lang.Math.*;
import javax.imageio.*;
import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

public class ExpandTester
{
	BufferedImage original_image;
	BufferedImage image;
	JMenuItem     apply_item;
	JDialog       shift_dialog;
	JTextField    shift_value;
	ImageCanvas   image_canvas;
	boolean       adjust = true;
	
	int           xdim, ydim;
	String        filename;
	
	int [] original_pixel;
	int [] alpha;
	int [] red;
	int [] green;
	int [] blue;
	
	int pixel_shift  = 3;

	
	public static void main(String[] args)
	{
		if (args.length != 1)
		{
			System.out.println("Usage: java ExpandTester <filename>");
			System.exit(0);
		}
		String prefix       = new String("C:/Users/Brian Crowley/Desktop/");
		String filename     = new String(args[0]);
		String java_version = System.getProperty("java.version");
		String os           = System.getProperty("os.name");
		String os_version   = System.getProperty("os.version");
		String machine      = System.getProperty("os.arch");
		//System.out.println("Current java version is " + java_version);
		//System.out.println("Current os is " + os + " " + os_version + " on " + machine);
		//System.out.println("Image file is " + filename);
		ExpandTester processor = new ExpandTester(prefix + filename);
	}

	public ExpandTester(String _filename)
	{
		filename = _filename;
		try
		{
			File file = new File(filename);
			original_image = ImageIO.read(file);
			int raster_type = original_image.getType();
			ColorModel color_model = original_image.getColorModel();
			int number_of_channels = color_model.getNumColorComponents();
			int number_of_bits = color_model.getPixelSize();
			xdim = original_image.getWidth();
			ydim = original_image.getHeight();
			
			System.out.println("xdim = " + xdim + ", ydim = " + ydim);
			int pixel_length = xdim * ydim * 8;
		    
			
		    original_pixel = new int[xdim * ydim];
		    alpha          = new int[xdim * ydim];
			blue           = new int[xdim * ydim];
		    green          = new int[xdim * ydim];
		    red            = new int[xdim * ydim];
		    
			
			System.out.println();
			
			if(raster_type == BufferedImage.TYPE_3BYTE_BGR)
			{
				int[]          pixel = new int[xdim * ydim];
				PixelGrabber pixel_grabber = new PixelGrabber(original_image, 0, 0, xdim, ydim, original_pixel, 0, xdim);
		        try
		        {
		            pixel_grabber.grabPixels();
		        }
		        catch(InterruptedException e)
		        {
		            System.err.println(e.toString());
		        }
		        if((pixel_grabber.getStatus() & ImageObserver.ABORT) != 0)
		        {
		            System.err.println("Error grabbing pixels.");
		            System.exit(1);
		        }
			  
		        for(int i = 0; i < xdim * ydim; i++)
				{
			        alpha[i] = (original_pixel[i] >> 24) & 0xff;
				    blue[i]  = (original_pixel[i] >> 16) & 0xff;
				    green[i] = (original_pixel[i] >> 8) & 0xff; 
		            red[i]   = original_pixel[i] & 0xff; 
				}
		        
			    image = new BufferedImage(xdim, ydim, BufferedImage.TYPE_INT_RGB);
			    
			  
			    for(int i = 0; i < xdim; i++)
			    {
			    	for(int j = 0; j < ydim; j++)
			    	{
			    		image.setRGB(i, j, original_pixel[j * xdim + i]);
			    	}
			    }
               
			    JFrame frame = new JFrame("Expand Tester");
				WindowAdapter window_handler = new WindowAdapter()
			    {
			        public void windowClosing(WindowEvent event)
			        {
			            System.exit(0);
			        }
			    };
			    frame.addWindowListener(window_handler);
			    
				image_canvas = new ImageCanvas();
				image_canvas.setSize(xdim, ydim);
				frame.getContentPane().add(image_canvas, BorderLayout.CENTER);
				
				JMenuBar menu_bar = new JMenuBar();

				JMenu     file_menu  = new JMenu("File");
				
				apply_item                 = new JMenuItem("Apply");
				ApplyHandler apply_handler = new ApplyHandler();
				apply_item.addActionListener(apply_handler);
				file_menu.add(apply_item);
				
				JMenuItem reload_item        = new JMenuItem("Reload");
				ReloadHandler reload_handler = new ReloadHandler();
				reload_item.addActionListener(reload_handler);
				file_menu.add(reload_item);
				
				JMenu settings_menu = new JMenu("Settings");
				
				JMenuItem shift_item = new JMenuItem("Pixel Shift");
				ActionListener shift_handler = new ActionListener()
				{
					public void actionPerformed(ActionEvent e)
					{
						Point location_point = frame.getLocation();
						int x = (int) location_point.getX();
						int y = (int) location_point.getY();

						x += xdim;

						shift_dialog.setLocation(x, y);
						shift_dialog.pack();
						shift_dialog.setVisible(true);
					}
				};
				shift_item.addActionListener(shift_handler);
				shift_dialog = new JDialog(frame, "Pixel Shift");
				JPanel shift_panel = new JPanel(new BorderLayout());
				JSlider shift_slider = new JSlider();
				shift_slider.setMinimum(0);
				shift_slider.setMaximum(7);
				shift_slider.setValue(pixel_shift);
				shift_value = new JTextField(3);
				shift_value.setText(" " + pixel_shift + " ");
				ChangeListener shift_slider_handler = new ChangeListener()
				{
					public void stateChanged(ChangeEvent e)
					{
						JSlider slider = (JSlider) e.getSource();
						pixel_shift = slider.getValue();
						shift_value.setText(" " + pixel_shift + " ");
						if(slider.getValueIsAdjusting() == false)
						{
							apply_item.doClick();
						}
					}
				};
				shift_slider.addChangeListener(shift_slider_handler);
				shift_panel.add(shift_slider, BorderLayout.CENTER);
				shift_panel.add(shift_value, BorderLayout.EAST);
				shift_dialog.add(shift_panel);
				
				settings_menu.add(shift_item);
				
				JCheckBoxMenuItem set_adjust = new JCheckBoxMenuItem("Adjust");
				ActionListener adjust_handler = new ActionListener()
				{
					public void actionPerformed(ActionEvent e) 
		            {
		            	JCheckBoxMenuItem item = (JCheckBoxMenuItem) e.getSource();
		            	if(adjust == true)
						{
		            		adjust = false;
							item.setState(false);
						}
						else
						{
							adjust = true;
							item.setState(true);	
						}
		            }   	
				};
				set_adjust.addActionListener(adjust_handler);
				if(adjust)
					set_adjust.setState(true);
				else
					set_adjust.setState(false);
				settings_menu.add(set_adjust);
				
				menu_bar.add(file_menu);
				menu_bar.add(settings_menu);
				
				frame.setJMenuBar(menu_bar);
				
				frame.pack();
				frame.setLocation(400, 200);
				frame.setVisible(true);
			} 
		} 
		catch (Exception e)
		{
			System.out.println(e.getMessage());
			System.exit(1);
		}
	}
	
	class ImageCanvas extends Canvas
    {
        public synchronized void paint(Graphics g)
        {
            g.drawImage(image, 0, 0, this);
        }
    }
	
	class ApplyHandler implements ActionListener
	{
		public void actionPerformed(ActionEvent event)
		{
		    int [] shifted_blue  = new int[xdim * ydim];
		    int [] shifted_green = new int[xdim * ydim];
		    int [] shifted_red   = new int[xdim * ydim];
		    double [] shifted_green_d = new double[xdim * ydim];
		    
		    double shifted_green_max = Double.MIN_VALUE;
		    double shifted_green_min = Double.MAX_VALUE;
		    for(int i = 0; i < xdim * ydim; i++)
		    {
		        shifted_blue[i]  = blue[i]  >> pixel_shift;
		        shifted_green[i] = green[i] >> pixel_shift;
			    shifted_green_d[i] = shifted_green[i];
			    
			    if(shifted_green_max < shifted_green_d[i])
			    	shifted_green_max = shifted_green_d[i];
			    if(shifted_green_min > shifted_green_d[i])
			    	shifted_green_min = shifted_green_d[i];
			    
			    shifted_red[i]   = red[i]   >> pixel_shift; 
			    
		    }
		    
		    /*
		    double [] shrunken_green  = DeltaMapper.avg4(shifted_green_d, xdim, ydim);
		    double [] shrunken_green2 = DeltaMapper.shrink4(shifted_green_d, xdim, ydim);
		    
		    double [] expanded_green  = DeltaMapper.expandX(shrunken_green, xdim / 2, ydim / 2);
		    double [] expanded_green2 = DeltaMapper.expandY(expanded_green, xdim, ydim / 2);
		    
		    
		    int positive_difference = 0;
		    int negative_difference = 0;
		    
		    int _xdim = xdim / 2;
		    int _ydim = ydim / 2;
		    for(int i = 0; i < _ydim; i++)
		    {
		    	for(int j = 0; j < _xdim; j++)
		    	{
		    		if(shrunken_green[i * _xdim + j] != shrunken_green2[i * _xdim + j])
		    		{
		    			if (shrunken_green2[i * _xdim + j] < shrunken_green[i * _xdim + j])
		    			{
		    			    positive_difference++;
		    			}
		    			else
		    			{
		    				negative_difference++;
		    			}
		    		}
		    	}
		    }
		    System.out.println("The shrunken images have " + (_xdim * _ydim) + " pixel values.");
		    System.out.println("The number of positive different values was " + positive_difference);
		    System.out.println("The number of negative different values was " + negative_difference);
		    
		    */
		    
		    //double [] shrunken_green = DeltaMapper.avg4(shifted_green_d, xdim, ydim);
		    
		    // This function accumulates values without averaging them.
		    double [] shrunken_green = ExpandMapper.shrinkX(shifted_green_d, xdim, ydim); 
		    for(int i = 0; i < shrunken_green.length; i++)
		    	shrunken_green[i] /= 2;
		    
		    int max_value  = 255;
		    max_value    >>= pixel_shift;
		    double max_value_d = max_value;
		    
		    double [] expanded_green;
		    if(adjust)
		    {
		        double [] adjusted_green  = ExpandMapper.adjustX(shifted_green_d, xdim, shrunken_green, max_value_d);
		        double [] adjusted_green2 = ExpandMapper.adjustX(shifted_green_d, xdim, adjusted_green, max_value_d);
		        double [] adjusted_green3 = ExpandMapper.adjustX(shifted_green_d, xdim, adjusted_green2, max_value_d);
		        double [] adjusted_green4 = ExpandMapper.adjustX(shifted_green_d, xdim, adjusted_green3, max_value_d);
		        double [] adjusted_green5 = ExpandMapper.adjustX(shifted_green_d, xdim, adjusted_green4, max_value_d);
		        expanded_green  = ExpandMapper.expandX(adjusted_green5, xdim / 2, ydim);
		        
		        double expanded_green_min = Double.MAX_VALUE;
		        double expanded_green_max = Double.MIN_VALUE;
		        
		        for(int i = 0; i < expanded_green.length; i++)
		        {
		        	if(expanded_green_min > expanded_green[i])
		        		expanded_green_min = expanded_green[i];
		        	if(expanded_green_max < expanded_green[i])
		        		expanded_green_max = expanded_green[i];
		        }
		        System.out.println("Original min is " + String.format("%.4f", shifted_green_min));
	        	System.out.println("Original max is " + String.format("%.4f", shifted_green_max));
	        	System.out.println("Expanded min is " + String.format("%.4f", expanded_green_min));
	        	System.out.println("Expanded max is " + String.format("%.4f", expanded_green_max));	
		    }
		    else
		    {
		    	expanded_green  = ExpandMapper.expandX(shrunken_green, xdim / 2, ydim);	
		    }
		    
		   
		    
		    
		    int [] new_green = new int[xdim * ydim]; 
		    
		    for(int i = 0; i < xdim * ydim; i++)
		    	new_green[i] = (int)(expanded_green[i] + .5);
		    
		    //int [] error = DeltaMapper.getDifference(new_green, green);
	        
	        int [] shifted_blue_green = DeltaMapper.getDifference(shifted_blue, shifted_green);
	        int [] new_blue           = DeltaMapper.getSum(shifted_blue_green, shifted_green);
	       
	        int [] shifted_red_green = DeltaMapper.getDifference(shifted_red, shifted_green);
		    int [] new_red           = DeltaMapper.getSum(shifted_red_green, shifted_green);
		  
		    int [] new_pixel = new int[xdim * ydim];
		    for(int i = 0; i < xdim * ydim; i++)
		    {
	            new_blue[i]  <<= pixel_shift;
	            new_green[i] <<= pixel_shift;
	            new_red[i]   <<= pixel_shift;
	            
	            new_pixel[i]  = 0;
	            new_pixel[i] |= new_blue[i] << 16;
	            new_pixel[i] |= new_green[i] << 8;    
	            new_pixel[i] |= new_red[i];	
		    }
		     
			for(int i = 0; i < xdim; i++)
			{
			    for(int j = 0; j < ydim; j++)
			    {
			    	image.setRGB(i, j, new_pixel[j * xdim + i]);
			    }	
			} 
		    image_canvas.repaint();
		    
		    System.out.println();
		}
	}
	
	class ReloadHandler implements ActionListener
	{
		public void actionPerformed(ActionEvent event)
		{
			
			for(int i = 0; i < xdim; i++)
		    {
		    	for(int j = 0; j < ydim; j++)
		    	{
		    		image.setRGB(i, j, original_pixel[j * xdim + i]);
		    	}
		    	
		    } 
			System.out.println("Reloaded original image.");
			image_canvas.repaint();
		}
	}
}