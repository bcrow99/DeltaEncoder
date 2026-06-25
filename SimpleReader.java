import java.awt.*;
import java.awt.image.*;
import java.io.*;
import java.awt.event.*;
import java.util.*;
import java.util.zip.*;
import java.awt.geom.AffineTransform;
import javax.swing.*;

public class SimpleReader
{
	int  xdim              = 0;
	int  ydim              = 0;
	int  intermediate_xdim = 0;
	int  intermediate_ydim = 0;
	int  pixel_shift       = 0;
	int  pixel_quant       = 0;
	int  set_id            = 0;
	byte delta_type        = 0;
	byte compress_type     = 0;

	ArrayList string_list = new ArrayList();
	ArrayList table_list  = new ArrayList();
	ArrayList map_list    = new ArrayList();
	ArrayList delta_list  = new ArrayList();   // used for compress_type == 0
	int[][] channel_array     = new int[3][0];
	int  min[]                = new int[3];
	int  init[]               = new int[3];
	int  delta_min[]          = new int[3];
	byte type[]               = new byte[3];
	byte compressed[]         = new byte[3];
	int  length[]             = new int[3];
	int  compressed_length[]  = new int[3];
	byte channel_iterations[] = new byte[3];

	int [][] resize_array = new int[3][0];

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
			System.out.println("Usage: java SimpleReader <filename>");
			System.exit(0);
		}
		SimpleReader reader = new SimpleReader(args[0]);
	}

	public SimpleReader(String filename)
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

			if (delta_type > 7)
			{
				System.out.println("Delta type " + delta_type + " not supported.");
				System.exit(0);
			}
			System.out.println("Set id is " + set_id);
			System.out.println("Compress type is " + compress_type);

			long start = System.nanoTime();
			for (int i = 0; i < 3; i++)
			{
				min[i]                = in.readInt();
				init[i]               = in.readInt();
				delta_min[i]          = in.readInt();
				length[i]             = in.readInt();
				compressed_length[i]  = in.readInt();
				channel_iterations[i] = in.readByte();

				// Map is always written first (for delta_type 5/6/7), regardless
				// of compress_type — mirrors what SimpleWriter.SaveHandler writes.
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
					// Integer path: inflate the deflated delta bytes.
					int delta_length    = in.readInt();
					int zipped_length   = in.readInt();
					byte[] zipped_data  = new byte[zipped_length];
					in.read(zipped_data, 0, zipped_length);

					byte[] delta_bytes = new byte[delta_length];
					Inflater inflater  = new Inflater();
					inflater.setInput(zipped_data, 0, zipped_length);
					inflater.inflate(delta_bytes);
					inflater.end();
					delta_list.add(delta_bytes);
				}
				else
				{
					// String / String* path: table then deflated bit string.
					int table_length   = in.readShort();
					int[] table        = new int[table_length];
					int max_byte_value = Byte.MAX_VALUE * 2 + 1;
					if (table.length <= max_byte_value)
					{
						for (int k = 0; k < table_length; k++)
						{
							table[k] = in.readByte();
							if (table[k] < 0)
								table[k] = max_byte_value + 1 + table[k];
						}
					}
					else
					{
						for (int k = 0; k < table_length; k++)
							table[k] = in.readShort();
					}
					table_list.add(table);

					int string_length = compressed_length[i] / 8;
					if (compressed_length[i] % 8 != 0)
						string_length++;
					string_length++;
					byte[] string = new byte[string_length];

					int zipped_length   = in.readInt();
					byte[] zipped_data  = new byte[zipped_length];
					in.read(zipped_data, 0, zipped_length);

					Inflater inflater = new Inflater();
					inflater.setInput(zipped_data, 0, zipped_length);
					int unzipped_length = inflater.inflate(string);
					if (unzipped_length != string_length)
						System.out.println("Unzipped data not expected length.");
					inflater.end();
					string_list.add(string);
				}
			}
			in.close();

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

			int[][] channel = new int[3][0];
			BufferedImage image = new BufferedImage(xdim, ydim, BufferedImage.TYPE_INT_RGB);

			if (set_id == 0)
			{
				channel[0] = channel_array[0];
				channel[1] = channel_array[1];
				channel[2] = channel_array[2];
			}
			else if (set_id == 1)
			{
				channel[0] = channel_array[0];
				channel[1] = DeltaMapper2.getDifference(channel_array[1], channel_array[2]);
				channel[2] = channel_array[1];
			}
			else if (set_id == 2)
			{
				channel[0] = channel_array[0];
				channel[1] = DeltaMapper2.getDifference(channel_array[0], channel_array[2]);
				channel[2] = channel_array[1];
			}
			else if (set_id == 3)
			{
				channel[0] = channel_array[0];
				channel[1] = DeltaMapper2.getDifference(channel_array[0], channel_array[1]);
				channel[2] = DeltaMapper2.getSum(channel_array[2], channel[1]);
			}
			else if (set_id == 4)
			{
				channel[0] = channel_array[0];
				channel[1] = DeltaMapper2.getDifference(channel_array[0], channel_array[1]);
				channel[2] = DeltaMapper2.getSum(channel_array[0], channel_array[2]);
			}
			else if (set_id == 5)
			{
				channel[0] = DeltaMapper2.getSum(channel_array[2], channel_array[0]);
				channel[1] = channel_array[0];
				channel[2] = channel_array[1];
			}
			else if (set_id == 6)
			{
				for (int i = 0; i < channel_array[2].length; i++)
					channel_array[2][i] = -channel_array[2][i];
				channel[1] = DeltaMapper2.getSum(channel_array[2], channel_array[0]);
				channel[0] = DeltaMapper2.getSum(channel_array[1], channel[1]);
				channel[2] = channel_array[0];
			}
			else if (set_id == 7)
			{
				channel[0] = DeltaMapper2.getSum(channel_array[0], channel_array[1]);
				channel[1] = channel_array[0];
				channel[2] = DeltaMapper2.getSum(channel_array[0], channel_array[2]);
			}
			else if (set_id == 8)
			{
				channel[2] = DeltaMapper2.getSum(channel_array[0], channel_array[1]);
				channel[0] = DeltaMapper2.getDifference(channel[2], channel_array[2]);
				channel[1] = channel_array[0];
			}
			else if (set_id == 9)
			{
				channel[0] = DeltaMapper2.getDifference(channel_array[0], channel_array[2]);
				channel[1] = DeltaMapper2.getDifference(channel_array[0], channel_array[1]);
				channel[2] = channel_array[0];
			}

			if (pixel_quant == 0)
			{
				int[] pixel = DeltaMapper2.getPixel(channel[0], channel[1], channel[2], xdim, pixel_shift);
				image.setRGB(0, 0, xdim, ydim, pixel, 0, xdim);
			}
			else
			{
				if (xdim > 600)
				{
					Thread [] resize_thread = new Thread[3];
					for (int i = 0; i < 3; i++)
					{
						resize_thread[i] = new Thread(new Resizer(channel[i], intermediate_xdim, xdim, ydim, i));
						resize_thread[i].start();
					}
					for (int i = 0; i < 3; i++)
						resize_thread[i].join();

					int[] pixel = DeltaMapper2.getPixel(resize_array[0], resize_array[1], resize_array[2], xdim, pixel_shift);
					image.setRGB(0, 0, xdim, ydim, pixel, 0, xdim);
				}
				else
				{
					channel[0] = ResizeMapper.resize(channel[0], intermediate_xdim, xdim, ydim);
					channel[1] = ResizeMapper.resize(channel[1], intermediate_xdim, xdim, ydim);
					channel[2] = ResizeMapper.resize(channel[2], intermediate_xdim, xdim, ydim);

					int[] pixel = DeltaMapper2.getPixel(channel[0], channel[1], channel[2], xdim, pixel_shift);
					image.setRGB(0, 0, xdim, ydim, pixel, 0, xdim);
				}
			}

			stop = System.nanoTime();
			time = stop - start;
			System.out.println("It took " + (time / 1000000) + " ms to assemble and load rgb files.");

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
	// -------------------------------------------------------------------------
	private void buildViewer(String filename)
	{
		Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
		int screen_xdim = (int) screenSize.getWidth();
		int screen_ydim = (int) screenSize.getHeight();

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

		frame = new JFrame("Simple Reader  [decoding\u2026]");
		frame.addWindowListener(new WindowAdapter()
		{
			public void windowClosing(WindowEvent event) { System.exit(0); }
		});

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

		int max_frame_w = (int)(screen_xdim * 0.70);
		int max_frame_h = (int)(screen_ydim * 0.70);
		frame.setSize(Math.min(xdim + 40, max_frame_w),
		              Math.min(ydim + 80,  max_frame_h));
		frame.setLocation(5, 5);
		frame.setVisible(true);
	}

	private void showImage()
	{
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
		frame.setTitle("Simple Reader  [" + status + "]");
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
	// Worker runnables
	// -------------------------------------------------------------------------
	class Resizer implements Runnable
	{
		int [] src;
		int    xdim, new_xdim, new_ydim, i;

		public Resizer(int [] src, int xdim, int new_xdim, int new_ydim, int i)
		{
			this.src      = src;
			this.xdim     = xdim;
			this.new_xdim = new_xdim;
			this.new_ydim = new_ydim;
			this.i        = i;
		}

		public void run()
		{
			int[] dst       = ResizeMapper.resize(src, xdim, new_xdim, new_ydim);
			resize_array[i] = dst;
		}
	}

	class Decompressor implements Runnable
	{
		int i;

		public Decompressor(int i)
		{
			this.i = i;
		}

		public void run()
		{
			int[] channel_id = DeltaMapper2.getChannels(set_id);

			if (delta_type < 8)
			{
				int[] delta;
				int   current_xdim = 0;
				int   current_ydim = 0;

				int size;
				if (pixel_quant == 0)
				{
					size         = xdim * ydim;
					current_xdim = xdim;
					current_ydim = ydim;
				}
				else
				{
					double factor     = pixel_quant;
					factor           /= 10;
					intermediate_xdim = xdim - (int)(factor * (xdim / 2 - 2));
					intermediate_ydim = ydim - (int)(factor * (ydim / 2 - 2));
					size              = intermediate_xdim * intermediate_ydim;
					current_xdim      = intermediate_xdim;
					current_ydim      = intermediate_ydim;
				}

				if (compress_type == 0)
				{
					// Integer path: reconstruct delta directly from raw bytes.
					byte[] delta_bytes = (byte[]) delta_list.get(i);
					delta    = new int[size];
					delta[0] = 0;
					for (int j = 1; j < size; j++)
						delta[j] = delta_bytes[j] + delta_min[i];
				}
				else
				{
					// String / String* path: decompress bit string then unpack.
					byte[] string  = (byte[]) string_list.get(i);
					int[]  table   = (int[])  table_list.get(i);
					int iterations = StringMapper3.getIterations(string);
					int bitlength  = StringMapper3.getBitlength(string);
					string         = StringMapper3.decompressStrings(string);

					if (channel_iterations[i] != iterations)
						System.out.println("Iterations appended to string does not agree with channel " + i + " information.");
					if (compressed_length[i] != bitlength)
						System.out.println("Bit length appended to string does not agree with channel " + i + " information.");

					delta = StringMapper3.unpackStrings(string, table, size, bitlength);
					for (int j = 1; j < delta.length; j++)
						delta[j] += delta_min[i];
				}

				int[] current_channel = new int[1];
				if      (delta_type == 0) current_channel = DeltaMapper2.getValuesFromHorizontalDeltas(delta, current_xdim, current_ydim, init[i]);
				else if (delta_type == 1) current_channel = DeltaMapper2.getValuesFromVerticalDeltas(delta, current_xdim, current_ydim, init[i]);
				else if (delta_type == 2) current_channel = DeltaMapper2.getValuesFromAverageDeltas(delta, current_xdim, current_ydim, init[i]);
				else if (delta_type == 3) current_channel = DeltaMapper2.getValuesFromPaethDeltas(delta, current_xdim, current_ydim, init[i]);
				else if (delta_type == 4) current_channel = DeltaMapper2.getValuesFromGradientDeltas(delta, current_xdim, current_ydim, init[i]);
				else if (delta_type == 5)
				{
					byte[] map = (byte[]) map_list.get(i);
					current_channel = DeltaMapper2.getValuesFromMixedDeltas(delta, current_xdim, current_ydim, init[i], map);
				}
				else if (delta_type == 6)
				{
					byte[] map = (byte[]) map_list.get(i);
					current_channel = DeltaMapper2.getValuesFromMixedDeltas2(delta, current_xdim, current_ydim, init[i], map);
				}
				else if (delta_type == 7)
				{
					byte[] map = (byte[]) map_list.get(i);
					current_channel = DeltaMapper.getValuesFromIdealDeltas(delta, current_xdim, current_ydim, init[i], map);
				}

				if (channel_id[i] > 2)
					for (int j = 0; j < current_channel.length; j++)
						current_channel[j] += min[i];

				channel_array[i] = current_channel;
			}
			else
			{
				System.out.println("Delta type " + delta_type + " not supported.");
				System.exit(0);
			}
		}
	}
}
