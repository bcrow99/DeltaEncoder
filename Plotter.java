import java.awt.*;
import java.awt.event.*;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.FileWriter;
import java.util.*;
import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.event.*;

public class Plotter
{
	public JFrame             frame;
	public static void main(String[] args)
	{
		Plotter window = new Plotter(args[0]);
		window.frame.setVisible(true);
	}
	
	public Plotter(String filename)
	{
		try
		{
			File file          = new File(filename);
			DataInputStream in = new DataInputStream(new FileInputStream(file));
			int length         = in.readInt();
			
			int [] value       = new int[length];
			for(int i = 0; i < length; i++)
				value[i] = in.readInt();
			in.close();
			frame = new JFrame("Plotter");
			WindowAdapter window_handler = new WindowAdapter()
		    {
		        public void windowClosing(WindowEvent event)
		        {   
		        	System.exit(0);	
		        }
		    };
		    frame.addWindowListener(window_handler);
		    
		    PlotCanvas canvas = new PlotCanvas(value);
		    frame.add(canvas);
		    Dimension d = canvas.getSize();
		    frame.setSize(d);
		    
		    canvas.repaint();
		}
		catch(Exception e)
		{
		    System.out.println(e.toString());
		    System.exit(0);
		}
	}

	class PlotCanvas extends Canvas
	{
	    int [] value;
	    
	    public PlotCanvas(int [] value)
	    {
	    	this.value = value;
	    	
	    	int max = 0;
	    	for(int i = 0; i < value.length; i++)
	    	{
	    		if(value[i] > max)
	    			max = value[i];
	    	}
	    	
	    	int xdim = value.length + 10;
	    	int ydim = max + 10;
	    	Dimension plot_dimension = new Dimension(xdim, ydim);
	    	this.setSize(plot_dimension);
	    }
	    
	    public void paint(Graphics g)
		{
			Rectangle visible_area = g.getClipBounds();

			int xdim = (int) visible_area.getWidth();
			int ydim = (int) visible_area.getHeight();
			
			Image buffered_image = new BufferedImage(xdim, ydim, BufferedImage.TYPE_INT_RGB);
			Graphics2D graphics_buffer = (Graphics2D) buffered_image.getGraphics();
			
			graphics_buffer.setColor(java.awt.Color.BLACK);
			//graphics_buffer.setColor(java.awt.Color.WHITE);
			graphics_buffer.fillRect(0, 0, xdim, ydim);
			//graphics_buffer.setColor(java.awt.Color.BLACK);
			
			/*
			graphics_buffer.drawLine(5, 5, xdim - 5, 5);
			graphics_buffer.drawLine(xdim - 5, 5, xdim - 5, ydim - 5);
			graphics_buffer.drawLine(5, 5, 5, ydim - 5);
			graphics_buffer.drawLine(5, ydim - 5, xdim - 5, ydim - 5);
			*/
			g.drawImage(buffered_image, 0, 0, null);
		}
	}
}