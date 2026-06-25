import java.awt.*;
import java.awt.image.*;
import java.io.*;
import java.awt.event.*;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.zip.*;
import java.awt.geom.AffineTransform;
import javax.imageio.*;
import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

public class HuffmanReader
{
	int xdim        = 0;
	int ydim        = 0;
	int pixel_shift = 0;
	int pixel_quant = 0;
	int set_id      = 0;
	byte delta_type    = 0;
	byte compress_type = 0;
	
	ArrayList huffman_string_list  = new ArrayList();
	ArrayList huffman_table_list   = new ArrayList();
	ArrayList string_table_list    = new ArrayList();
	ArrayList map_list             = new ArrayList();
	ArrayList code_list            = new ArrayList();
	ArrayList code_length_list     = new ArrayList();
	
	int [][] channel_array = new int[3][0];
	
	int  min[]        = new int[3];
	int  init[]       = new int[3];
	int  delta_min[]  = new int[3];
	byte type[]       = new byte[3];
	byte compressed[] = new byte[3];
	
	int channel_huffman_length[] = new int[3];
	int channel_string_length[]  = new int[3];

	// --- Viewer state ---
	BufferedImage decoded_image = null;
	BufferedImage display_image = null;
	ImageCanvas   image_canvas  = null;
	JScrollPane   scroll_pane   = null;
	JFrame        frame         = null;

	double zoom_scale = 1.0;
	double fit_scale  = 1.0;

	static final double ZOOM_FACTOR = 1.25;
	static final double ZOOM_MIN    = 0.05;
	static final double ZOOM_MAX    = 32.0;

	public static void main(String[] args)
	{
		if (args.length != 1)
		{
			System.out.println("Usage: java HuffmanReader <filename>");
			System.exit(0);
		}
		HuffmanReader reader = new HuffmanReader(args[0]);
	}
	
	public HuffmanReader(String filename)
	{
		try
		{
			File file          = new File(filename);
			DataInputStream in = new DataInputStream(new FileInputStream(file));
			xdim               = in.readShort();
			ydim               = in.readShort();
			pixel_shift        = in.readByte();
			pixel_quant        = in.readByte();
			set_id             = in.readByte();
			delta_type         = in.readByte();
			compress_type      = in.readByte();
			
			int [] channel_id  = DeltaMapper.getChannels(set_id);
			
			if (delta_type > 7)
			{
				System.out.println("Delta type not supported.");
				System.exit(0);
			}

			long start = System.nanoTime();
			for (int i = 0; i < 3; i++)
			{
				int j         = channel_id[i];
				min[i]        = in.readInt();
				init[i]       = in.readInt();
				delta_min[i]  = in.readInt();
				channel_huffman_length[i] = in.readInt();
				int table_length = in.readShort();
				int [] table = new int[table_length];
				
				if (table.length <= Byte.MAX_VALUE * 2 + 1)
				{
					byte [] buffer = new byte[table.length];
					in.read(buffer, 0, table.length);
					for (int k = 0; k < buffer.length; k++)
					{
						table[k] = buffer[k];
						if (table[k] < 0)
							table[k] += Byte.MAX_VALUE * 2 + 2;
					}
				}
				else
				{
					for (int k = 0; k < table_length; k++)
						table[k] = in.readShort();
				}
				huffman_table_list.add(table);
				
				if (delta_type == 5 || delta_type == 6 || delta_type == 7)
				{
					int map_length        = in.readInt();
					int packed_map_length = in.readInt();	
					byte [] packed_map    = new byte[packed_map_length];
					in.read(packed_map, 0, packed_map_length);
					byte [] map           = SegmentMapper.unpackBits(packed_map, map_length, 2);
					map_list.add(map);
				}
				
				if (compress_type == 0)
				{
					int n                      = in.readInt();
					byte init_value            = in.readByte();
					byte max_delta             = in.readByte();
					byte packed_delta_length   = in.readByte();
					byte [] packed_delta       = new byte[packed_delta_length];
					in.read(packed_delta, 0, packed_delta_length);
					
					byte [] code_length = CodeMapper.unpackLengthTable(n, init_value, max_delta, packed_delta);
					int  [] code        = CodeMapper.getCanonicalCode(code_length);
					code_list.add(code);
					code_length_list.add(code_length);
					
					int string_length  = in.readInt();
					byte [] string     = new byte[string_length];
					in.read(string, 0, string_length);
					huffman_string_list.add(string);
				}
				else
				{
					int n                      = in.readInt();
					byte init_value            = in.readByte();
					byte max_delta             = in.readByte();
					byte packed_delta_length   = in.readByte();
					byte [] packed_delta       = new byte[packed_delta_length];
					in.read(packed_delta, 0, packed_delta_length);
					
					byte [] code_length = CodeMapper.unpackLengthTable(n, init_value, max_delta, packed_delta);
					int  [] code        = CodeMapper.getCanonicalCode(code_length);
					code_list.add(code);
					code_length_list.add(code_length);
					
					int string_length  = in.readInt();
					byte [] string     = new byte[string_length];
					in.read(string, 0, string_length);
					huffman_string_list.add(string);
					
					channel_string_length[i]   = in.readInt();
					int string_table_length    = in.readShort();
					int [] string_table        = new int[string_table_length];
					byte [] buffer             = new byte[string_table.length];
					in.read(buffer, 0, string_table.length);
					for (int k = 0; k < buffer.length; k++)
					{
						string_table[k] = buffer[k];
						if (string_table[k] < 0)
							string_table[k] += Byte.MAX_VALUE * 2 + 2;
					}
					string_table_list.add(string_table);
				}
			}
			
			long stop = System.nanoTime();
			long time = stop - start;
			System.out.println("It took " + (time / 1000000) + " ms to read file.");

			// ── Build the viewer window immediately ──────────────────────────
			buildViewer(filename);

			// ── Decompress all three channels in parallel ────────────────────
			start = System.nanoTime();
			Thread [] decompression_thread = new Thread[3];
			for (int i = 0; i < 3; i++)
			{
				decompression_thread[i] = new Thread(new Decompressor(i));
				decompression_thread[i].start();
			}
			for (int i = 0; i < 3; i++)
				decompression_thread[i].join();
			stop = System.nanoTime();
			time = stop - start;
			System.out.println("It took " + (time / 1000000) + " ms to process data.");

			// ── Assemble the final RGB image ─────────────────────────────────
			start = System.nanoTime();

			int [] blue  = new int[xdim * ydim];
			int [] green = new int[xdim * ydim];
			int [] red   = new int[xdim * ydim];
			
			if (set_id == 0)
			{
				blue  = channel_array[0];
				green = channel_array[1];
				red   = channel_array[2];
			}
			else if (set_id == 1)
			{ 
				blue  = channel_array[0];
				red   = channel_array[1];
				green = DeltaMapper.getDifference(red, channel_array[2]);
			}
			else if (set_id == 2)
			{ 
				blue  = channel_array[0];
				red   = channel_array[1];
				green = DeltaMapper.getDifference(blue, channel_array[2]);
			}
			else if (set_id == 3)
			{ 
				blue  = channel_array[0];
				green = DeltaMapper.getDifference(blue, channel_array[1]);
				red   = DeltaMapper.getSum(channel_array[2], green);
			}
			else if (set_id == 4)
			{ 
				blue  = channel_array[0];
				green = DeltaMapper.getDifference(blue, channel_array[1]);
				red   = DeltaMapper.getSum(blue, channel_array[2]);
			}
			else if (set_id == 5)
			{
				green = channel_array[0];
				red   = channel_array[1];
				blue  = DeltaMapper.getSum(channel_array[2], green);
			}
			else if (set_id == 6)
			{
				red   = channel_array[0];
				for (int i = 0; i < channel_array[2].length; i++)
					channel_array[2][i] = -channel_array[2][i];
				green = DeltaMapper.getSum(channel_array[2], red);
				blue  = DeltaMapper.getSum(channel_array[1], green);
			}
			else if (set_id == 7)
			{
				green = channel_array[0];
				blue  = DeltaMapper.getSum(green, channel_array[1]);
				red   = DeltaMapper.getSum(green, channel_array[2]);
			}
			else if (set_id == 8)
			{
				green = channel_array[0];
				red   = DeltaMapper.getSum(green, channel_array[1]);
				blue  = DeltaMapper.getDifference(red, channel_array[2]);
			}
			else if (set_id == 9)
			{
				red   = channel_array[0];
				green = DeltaMapper.getDifference(red, channel_array[1]); 
				blue  = DeltaMapper.getDifference(red, channel_array[2]);
			}

			stop = System.nanoTime();
			time = stop - start;
			System.out.println("It took " + (time / 1000000) + " ms to assemble rgb files.");
			
			BufferedImage image = new BufferedImage(xdim, ydim, BufferedImage.TYPE_INT_RGB);

			start = System.nanoTime();
			// pixel_shift is already applied per-channel in Decompressor, so pass 0 here.
			int[] pixel = DeltaMapper.getPixel(blue, green, red, xdim, 0);
			image.setRGB(0, 0, xdim, ydim, pixel, 0, xdim);

			stop = System.nanoTime();
			time = stop - start;
			System.out.println("It took " + (time / 1000000) + " ms to load rgb files.");

			// ── Hand the finished image to the viewer on the EDT ─────────────
			decoded_image = image;
			SwingUtilities.invokeLater(() -> showImage());
		}
		catch (Exception e)
		{
			System.out.println(e.toString());
		}
	}

	// -------------------------------------------------------------------------
	// Build the JFrame with scroll pane and View menu.
	// Called once from the constructor right after reading the file header.
	// -------------------------------------------------------------------------
	private void buildViewer(String filename)
	{
		Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
		int screen_xdim = (int) screenSize.getWidth();
		int screen_ydim = (int) screenSize.getHeight();

		// Compute a fit scale so the image starts fully visible inside a
		// reasonably-sized window (at most 70% of screen in each dimension).
		int max_canvas_w = (int)(screen_xdim * 0.70) - 40;
		int max_canvas_h = (int)(screen_ydim * 0.70) - 80;
		double xscale = (xdim > 0) ? (double) max_canvas_w / xdim : 1.0;
		double yscale = (ydim > 0) ? (double) max_canvas_h / ydim : 1.0;
		fit_scale  = Math.min(1.0, Math.min(xscale, yscale));
		zoom_scale = fit_scale;

		image_canvas = new ImageCanvas();
		image_canvas.setPreferredSize(new Dimension(
				Math.max(1, (int)(xdim * zoom_scale)),
				Math.max(1, (int)(ydim * zoom_scale))));

		scroll_pane = new JScrollPane(image_canvas,
				JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
				JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
		scroll_pane.getVerticalScrollBar().setUnitIncrement(16);
		scroll_pane.getHorizontalScrollBar().setUnitIncrement(16);

		// Ctrl+wheel zooms; plain wheel scrolls.
		scroll_pane.addMouseWheelListener(new MouseWheelListener()
		{
			public void mouseWheelMoved(MouseWheelEvent e)
			{
				if (e.isControlDown())
				{
					JViewport vp          = scroll_pane.getViewport();
					Point     view_pos    = vp.getViewPosition();
					Point     mouse_pt    = e.getPoint();
					int       mouse_can_x = mouse_pt.x + view_pos.x;
					int       mouse_can_y = mouse_pt.y + view_pos.y;

					double old_scale = zoom_scale;
					if (e.getWheelRotation() < 0)
						zoom_scale = Math.min(ZOOM_MAX, zoom_scale * ZOOM_FACTOR);
					else
						zoom_scale = Math.max(ZOOM_MIN, zoom_scale / ZOOM_FACTOR);

					if (zoom_scale == old_scale) return;

					updateDisplayImage();
					image_canvas.setPreferredSize(new Dimension(
							(int)(xdim * zoom_scale),
							(int)(ydim * zoom_scale)));
					image_canvas.revalidate();
					image_canvas.repaint();

					double ratio = zoom_scale / old_scale;
					int new_vx   = (int)(mouse_can_x * ratio) - mouse_pt.x;
					int new_vy   = (int)(mouse_can_y * ratio) - mouse_pt.y;
					vp.setViewPosition(new Point(Math.max(0, new_vx), Math.max(0, new_vy)));

					updateTitle();
				}
				else
				{
					scroll_pane.dispatchEvent(e);
				}
			}
		});

		frame = new JFrame("Huffman Reader  [decoding\u2026]");
		frame.addWindowListener(new WindowAdapter()
		{
			public void windowClosing(WindowEvent event) { System.exit(0); }
		});

		// ── View / Zoom menu ────────────────────────────────────────────────
		JMenuBar menu_bar  = new JMenuBar();
		JMenu    view_menu = new JMenu("View");

		JMenuItem zoom_in_item = new JMenuItem("Zoom In (+)");
		zoom_in_item.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_EQUALS, InputEvent.CTRL_DOWN_MASK));
		zoom_in_item.addActionListener(e -> zoomBy(ZOOM_FACTOR));
		view_menu.add(zoom_in_item);

		JMenuItem zoom_out_item = new JMenuItem("Zoom Out (-)");
		zoom_out_item.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_MINUS, InputEvent.CTRL_DOWN_MASK));
		zoom_out_item.addActionListener(e -> zoomBy(1.0 / ZOOM_FACTOR));
		view_menu.add(zoom_out_item);

		JMenuItem zoom_fit_item = new JMenuItem("Fit to Window");
		zoom_fit_item.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_0, InputEvent.CTRL_DOWN_MASK));
		zoom_fit_item.addActionListener(e ->
		{
			Dimension vp_size = scroll_pane.getViewport().getSize();
			double xs = (double) vp_size.width  / xdim;
			double ys = (double) vp_size.height / ydim;
			zoom_scale = Math.min(xs, ys);
			updateDisplayImage();
			image_canvas.setPreferredSize(new Dimension(
					(int)(xdim * zoom_scale),
					(int)(ydim * zoom_scale)));
			image_canvas.revalidate();
			image_canvas.repaint();
			updateTitle();
		});
		view_menu.add(zoom_fit_item);

		JMenuItem zoom_actual_item = new JMenuItem("Actual Size (100%)");
		zoom_actual_item.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_1, InputEvent.CTRL_DOWN_MASK));
		zoom_actual_item.addActionListener(e ->
		{
			zoom_scale = 1.0;
			updateDisplayImage();
			image_canvas.setPreferredSize(new Dimension(xdim, ydim));
			image_canvas.revalidate();
			image_canvas.repaint();
			updateTitle();
		});
		view_menu.add(zoom_actual_item);

		menu_bar.add(view_menu);
		frame.setJMenuBar(menu_bar);

		frame.getContentPane().add(scroll_pane, BorderLayout.CENTER);

		// Size the frame to at most 70% of the screen in each dimension,
		// but never larger than the image itself (plus chrome).
		int max_frame_w = (int)(screen_xdim * 0.70);
		int max_frame_h = (int)(screen_ydim * 0.70);
		frame.setSize(Math.min(xdim + 40, max_frame_w),
		              Math.min(ydim + 80,  max_frame_h));
		frame.setLocation(5, 5);
		frame.setVisible(true);
	}

	// Called on the EDT once decoded_image is ready.
	private void showImage()
	{
		// Recompute fit scale now that we have a live viewport size.
		Dimension vp_size = scroll_pane.getViewport().getSize();
		double xs = (vp_size.width  > 0) ? (double) vp_size.width  / xdim : 1.0;
		double ys = (vp_size.height > 0) ? (double) vp_size.height / ydim : 1.0;
		fit_scale  = Math.min(1.0, Math.min(xs, ys));
		zoom_scale = fit_scale;

		updateDisplayImage();
		image_canvas.setPreferredSize(new Dimension(
				(int)(xdim * zoom_scale),
				(int)(ydim * zoom_scale)));
		image_canvas.revalidate();
		image_canvas.repaint();
		updateTitle();
	}

	// -------------------------------------------------------------------------
	// Zoom helpers
	// -------------------------------------------------------------------------
	private void zoomBy(double factor)
	{
		double new_scale = Math.max(ZOOM_MIN, Math.min(ZOOM_MAX, zoom_scale * factor));
		if (new_scale == zoom_scale) return;

		JViewport vp      = scroll_pane.getViewport();
		Point     vp_pos  = vp.getViewPosition();
		Dimension vp_size = vp.getSize();

		double centre_x = vp_pos.x + vp_size.width  / 2.0;
		double centre_y = vp_pos.y + vp_size.height / 2.0;
		double ratio    = new_scale / zoom_scale;
		zoom_scale      = new_scale;

		updateDisplayImage();
		image_canvas.setPreferredSize(new Dimension(
				(int)(xdim * zoom_scale),
				(int)(ydim * zoom_scale)));
		image_canvas.revalidate();
		image_canvas.repaint();

		int new_vx = (int)(centre_x * ratio - vp_size.width  / 2.0);
		int new_vy = (int)(centre_y * ratio - vp_size.height / 2.0);
		vp.setViewPosition(new Point(Math.max(0, new_vx), Math.max(0, new_vy)));

		updateTitle();
	}

	private void updateDisplayImage()
	{
		if (decoded_image == null) return;

		if (zoom_scale == 1.0)
		{
			display_image = decoded_image;
		}
		else
		{
			int w = Math.max(1, (int)(xdim * zoom_scale));
			int h = Math.max(1, (int)(ydim * zoom_scale));
			AffineTransform t    = new AffineTransform();
			t.scale(zoom_scale, zoom_scale);
			AffineTransformOp op = new AffineTransformOp(t, AffineTransformOp.TYPE_BILINEAR);
			display_image = new BufferedImage(w, h, decoded_image.getType());
			display_image = op.filter(decoded_image, display_image);
		}
	}

	private void updateTitle()
	{
		if (frame == null) return;
		int pct = (int) Math.round(zoom_scale * 100);
		String status = (decoded_image == null) ? "decoding\u2026" : (pct + "%");
		frame.setTitle("Huffman Reader  [" + status + "]");
	}

	// -------------------------------------------------------------------------
	// ImageCanvas
	// -------------------------------------------------------------------------
	class ImageCanvas extends JPanel
	{
		public ImageCanvas() { setOpaque(true); }

		@Override
		public Dimension getPreferredSize()
		{
			if (display_image != null)
				return new Dimension(display_image.getWidth(), display_image.getHeight());
			return new Dimension(
					Math.max(1, (int)(xdim * zoom_scale)),
					Math.max(1, (int)(ydim * zoom_scale)));
		}

		@Override
		protected synchronized void paintComponent(Graphics g)
		{
			super.paintComponent(g);
			if (display_image != null)
				g.drawImage(display_image, 0, 0, this);
		}
	}

	// -------------------------------------------------------------------------
	// Decompressor  (logic unchanged from original)
	// -------------------------------------------------------------------------
	class Decompressor implements Runnable 
	{ 
		int i;
		
		public Decompressor(int i)
		{
			this.i = i;	
		}
		
		public void run()
		{
			int [] channel_id = DeltaMapper.getChannels(set_id);
			int j = channel_id[i];
			
			if (delta_type < 8)
			{
				byte [] string      = (byte []) huffman_string_list.get(i);
				int  [] table       = (int [])  huffman_table_list.get(i);
				int  [] code        = (int [])  code_list.get(i);
				byte [] code_length = (byte []) code_length_list.get(i);
				
				int [] delta     = new int[1];
				int current_xdim = 0;
				int current_ydim = 0;
				
				if (compress_type == 0)
				{
					if (pixel_quant == 0)
					{
						delta = new int[xdim * ydim];
						CodeMapper.unpackCode(string, table, code, code_length, channel_huffman_length[i], delta);
						delta[0] = 0;
						for (int k = 1; k < delta.length; k++)
							delta[k] += delta_min[i];
						current_xdim = xdim;
						current_ydim = ydim;
					}
					else
					{
						double factor         = pixel_quant;
						factor               /= 10;
						int intermediate_xdim = xdim - (int)(factor * (xdim / 2 - 2));
						int intermediate_ydim = ydim - (int)(factor * (ydim / 2 - 2));
						delta = new int[intermediate_xdim * intermediate_ydim];
						CodeMapper.unpackCode(string, table, code, code_length, channel_huffman_length[i], delta);
						delta[0] = 0;
						for (int k = 1; k < delta.length; k++)
							delta[k] += delta_min[i];
						current_xdim = intermediate_xdim;
						current_ydim = intermediate_ydim;
					}
				}
				else
				{
					int bytelength = StringMapper.getBytelength(channel_string_length[i]);
					
					int [] delta_string_ints = new int[bytelength];
					int number_unpacked = CodeMapper.unpackCode(string, table, code, code_length, channel_huffman_length[i], delta_string_ints);
					if (number_unpacked != bytelength)
						System.out.println("Number unpacked not equal to bytelength.");
					
					byte [] delta_string = new byte[bytelength];
					for (int k = 0; k < bytelength; k++)
						delta_string[k] = (byte) delta_string_ints[k];
					
					int [] string_table = (int []) string_table_list.get(i);
					
					if (pixel_quant == 0)
					{
						delta = new int[xdim * ydim];
						int current_iterations = StringMapper.getIterations(delta_string);
						if (current_iterations == 0 || current_iterations == 16)
						{
							number_unpacked = StringMapper.unpackStrings2(delta_string, string_table, delta);
							if (number_unpacked != xdim * ydim)
								System.out.println("Number of values unpacked does not agree with image dimensions.");
						} 
						else
						{
							byte[] decompressed_string = StringMapper.decompressStrings2(delta_string);
							number_unpacked = StringMapper.unpackStrings2(decompressed_string, string_table, delta);
							if (number_unpacked != xdim * ydim)
								System.out.println("Number of values unpacked does not agree with image dimensions.");
						}
						delta[0] = 0;
						for (int k = 1; k < delta.length; k++)
							delta[k] += delta_min[i];
						current_xdim = xdim;
						current_ydim = ydim;
					}
					else
					{
						double factor         = pixel_quant;
						factor               /= 10;
						int intermediate_xdim = xdim - (int)(factor * (xdim / 2 - 2));
						int intermediate_ydim = ydim - (int)(factor * (ydim / 2 - 2));
						delta = new int[intermediate_xdim * intermediate_ydim];
						int current_iterations = StringMapper.getIterations(delta_string);
						if (current_iterations == 0 || current_iterations == 16)
						{
							number_unpacked = StringMapper.unpackStrings2(delta_string, string_table, delta);
							if (number_unpacked != intermediate_xdim * intermediate_ydim)
								System.out.println("Number of values unpacked does not agree with image dimensions.");
						} 
						else
						{
							byte[] decompressed_string = StringMapper.decompressStrings2(delta_string);
							number_unpacked = StringMapper.unpackStrings2(decompressed_string, string_table, delta);
							if (number_unpacked != intermediate_xdim * intermediate_ydim)
								System.out.println("Number of values unpacked does not agree with image dimensions.");
						}
						delta[0] = 0;
						for (int k = 1; k < delta.length; k++)
							delta[k] += delta_min[i];
						current_xdim = intermediate_xdim;
						current_ydim = intermediate_ydim;
					}
				}
				
				int[] current_channel = new int[1];
				if      (delta_type == 0) current_channel = DeltaMapper.getValuesFromHorizontalDeltas(delta, current_xdim, current_ydim, init[i]);
				else if (delta_type == 1) current_channel = DeltaMapper.getValuesFromVerticalDeltas(delta, current_xdim, current_ydim, init[i]);
				else if (delta_type == 2) current_channel = DeltaMapper.getValuesFromAverageDeltas(delta, current_xdim, current_ydim, init[i]);
				else if (delta_type == 3) current_channel = DeltaMapper.getValuesFromPaethDeltas(delta, current_xdim, current_ydim, init[i]);
				else if (delta_type == 4) current_channel = DeltaMapper.getValuesFromGradientDeltas(delta, current_xdim, current_ydim, init[i]);
				else if (delta_type == 5) current_channel = DeltaMapper.getValuesFromMixedDeltas(delta, current_xdim, current_ydim, init[i], (byte []) map_list.get(i));
				else if (delta_type == 6) current_channel = DeltaMapper.getValuesFromMixedDeltas2(delta, current_xdim, current_ydim, init[i], (byte []) map_list.get(i));
				else if (delta_type == 7) current_channel = DeltaMapper.getValuesFromIdealDeltas(delta, current_xdim, current_ydim, init[i], (byte []) map_list.get(i));
				
				if (channel_id[i] > 2)
					for (int k = 0; k < current_channel.length; k++)
						current_channel[k] += min[i];

				// Shift second (matching the new encode order: resize then shift).
				if (pixel_shift != 0)
					current_channel = DeltaMapper.shift(current_channel, pixel_shift);

				if (pixel_quant == 0)
					channel_array[i] = current_channel;
				else
					channel_array[i] = ResizeMapper.resize(current_channel, current_xdim, xdim, ydim);
			}
			else
			{
				System.out.println("Delta type " + delta_type + " not supported.");
				System.exit(0);
			}
		}
	} 
}
