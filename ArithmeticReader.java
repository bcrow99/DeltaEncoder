import java.awt.*;
import java.awt.image.*;
import java.io.*;
import java.awt.event.*;
import java.util.*;
import java.util.concurrent.*;
import java.math.*;
import java.util.zip.*;
import java.awt.geom.AffineTransform;
import javax.imageio.*;
import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

public class ArithmeticReader
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

    ArrayList table_list  = new ArrayList();
    ArrayList map_list    = new ArrayList();
    int[][] channel_array = new int[3][0];
    int  min[]                = new int[3];
    int  init[]               = new int[3];
    int  delta_min[]          = new int[3];
    byte type[]               = new byte[3];
    byte compressed[]         = new byte[3];
    int  length[]             = new int[3];
    int  compressed_length[]  = new int[3];
    byte channel_iterations[] = new byte[3];

    ArrayList <int [][]>        frequency_list = new ArrayList <int [][]> ();
    ArrayList <BigInteger [][]> offset_list    = new ArrayList <BigInteger [][]> ();
    ArrayList <byte [][]>       segment_list   = new ArrayList <byte [][]> ();

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
            System.out.println("Usage: java ArithmeticReader <filename>");
            System.exit(0);
        }
        ArithmeticReader reader = new ArithmeticReader(args[0]);
    }

    public ArithmeticReader(String filename)
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

            System.out.println("Compress type is " + compress_type);

            int[] channel_id = DeltaMapper.getChannels(set_id);

            if (delta_type > 8)
            {
                System.out.println("Delta type " + delta_type + " not supported.");
                System.exit(0);
            }
            System.out.println("Set id is " + set_id);

            byte[][] all_freq_zipped_data    = new byte[3][];
            int[]    all_freq_zipped_lengths = new int[3];
            int[]    all_number_of_bytes     = new int[3];
            int[]    all_length_types        = new int[3];
            int[]    all_number_of_segments  = new int[3];

            long start = System.nanoTime();

            for (int i = 0; i < 3; i++)
            {
                int j                 = channel_id[i];
                min[i]                = in.readInt();
                init[i]               = in.readInt();
                delta_min[i]          = in.readInt();
                length[i]             = in.readInt();
                compressed_length[i]  = in.readInt();
                channel_iterations[i] = in.readByte();

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

                if (delta_type == 5 || delta_type == 6 || delta_type == 7 || delta_type == 8)
                {
                    int map_length        = in.readInt();
                    int packed_map_length = in.readInt();
                    byte[] packed_map     = new byte[packed_map_length];
                    in.read(packed_map, 0, packed_map_length);
                    byte[] map            = SegmentMapper.unpackBits(packed_map, map_length, 2);
                    map_list.add(map);
                }

                int number_of_segments = in.readInt();
                int length_type        = in.readInt();
                int freq_zipped_length = in.readInt();

                byte[] freq_zipped_data = new byte[freq_zipped_length];
                in.read(freq_zipped_data, 0, freq_zipped_length);

                int number_of_bytes = 0;
                if      (length_type == 0) number_of_bytes = number_of_segments * 256;
                else if (length_type == 1) number_of_bytes = number_of_segments * 256 * 2;
                else if (length_type == 2) number_of_bytes = number_of_segments * 256 * 4;

                all_freq_zipped_data[i]    = freq_zipped_data;
                all_freq_zipped_lengths[i] = freq_zipped_length;
                all_number_of_bytes[i]     = number_of_bytes;
                all_length_types[i]        = length_type;
                all_number_of_segments[i]  = number_of_segments;

                BigInteger[][] offset = new BigInteger[number_of_segments][2];
                for (int k = 0; k < number_of_segments; k++)
                {
                    int len       = in.readInt();
                    byte[] bytes  = new byte[len];
                    in.read(bytes, 0, len);
                    offset[k][0] = new BigInteger(bytes);

                    len           = in.readInt();
                    bytes         = new byte[len];
                    in.read(bytes, 0, len);
                    offset[k][1] = new BigInteger(bytes);
                }

                offset_list.add(offset);

                byte[][] segment = new byte[number_of_segments][0];
                segment_list.add(segment);
            }

            long stop = System.nanoTime();
            long time = stop - start;
            System.out.println("It took " + (time / 1000000) + " ms to read file.");

            // ── Build the viewer window immediately so the user sees something ──
            buildViewer(filename);

            // ── Inflate all three channels' frequency tables in parallel ──────
            int[][][] all_frequencies = new int[3][][];
            Thread[]  inflater_thread = new Thread[3];

            for (int i = 0; i < 3; i++)
            {
                final int    fi                 = i;
                final byte[] freq_zipped_data   = all_freq_zipped_data[i];
                final int    freq_zipped_length = all_freq_zipped_lengths[i];
                final int    number_of_bytes    = all_number_of_bytes[i];
                final int    number_of_segments = all_number_of_segments[i];
                final int    length_type        = all_length_types[i];

                inflater_thread[i] = new Thread(() ->
                {
                    byte[] frequency_bytes = new byte[number_of_bytes];
                    Inflater freq_inflater = new Inflater();
                    freq_inflater.setInput(freq_zipped_data, 0, freq_zipped_length);
                    try { freq_inflater.inflate(frequency_bytes); }
                    catch (Exception e) { System.out.println("Inflater error: " + e); }
                    freq_inflater.end();

                    int[][] frequency = new int[number_of_segments][256];

                    if (length_type == 0)
                    {
                        for (int k = 0; k < number_of_segments; k++)
                            for (int m = 0; m < 256; m++)
                            {
                                frequency[k][m] = frequency_bytes[k * 256 + m];
                                if (frequency[k][m] < 0) frequency[k][m] += 256;
                            }
                    }
                    else if (length_type == 1)
                    {
                        for (int k = 0; k < number_of_segments; k++)
                            for (int m = 0; m < 256; m++)
                            {
                                int a = frequency_bytes[k * 512 + 2 * m];
                                if (a < 0) a += 256;
                                int b = frequency_bytes[k * 512 + 2 * m + 1];
                                if (b < 0) b += 256;
                                b <<= 8;
                                frequency[k][m] = a | b;
                            }
                    }
                    else if (length_type == 2)
                    {
                        for (int k = 0; k < number_of_segments; k++)
                            for (int m = 0; m < 256; m++)
                            {
                                int a = frequency_bytes[k * 1024 + 4 * m];
                                if (a < 0) a += 256;
                                int b = frequency_bytes[k * 1024 + 4 * m + 1];
                                if (b < 0) b += 256;
                                b <<= 8;
                                int c = frequency_bytes[k * 1024 + 4 * m + 2];
                                if (c < 0) c += 256;
                                c <<= 16;
                                int d = frequency_bytes[k * 1024 + 4 * m + 3];
                                if (d < 0) d += 256;
                                d <<= 24;
                                frequency[k][m] = a | b | c | d;
                            }
                    }

                    all_frequencies[fi] = frequency;
                });
                inflater_thread[i].start();
            }

            for (int i = 0; i < 3; i++)
                try { inflater_thread[i].join(); }
                catch (Exception e) { System.out.println("Inflater join: " + e); }

            for (int i = 0; i < 3; i++)
                frequency_list.add(all_frequencies[i]);

            start = System.nanoTime();

            // Compute string lengths and determine processing order.
            int[] string_length  = new int[3];
            int[] segment_length = new int[3];
            for (int i = 0; i < 3; i++)
            {
                if (compress_type > 0)
                    string_length[i] = StringMapper.getBytelength(compressed_length[i]);
                else
                {
                    if (pixel_quant == 0)
                        string_length[i] = xdim * ydim;
                    else
                    {
                        double factor     = pixel_quant;
                        factor           /= 10;
                        intermediate_xdim = xdim - (int)(factor * (xdim / 2 - 2));
                        intermediate_ydim = ydim - (int)(factor * (ydim / 2 - 2));
                        string_length[i]  = intermediate_xdim * intermediate_ydim;
                    }
                }
            }

            int[] process_order = new int[]{ 0, 1, 2 };
            if (compress_type > 0)
            {
                for (int i = 1; i < 3; i++)
                    for (int j = i; j > 0 && string_length[process_order[j-1]] < string_length[process_order[j]]; j--)
                    {
                        int tmp            = process_order[j];
                        process_order[j]   = process_order[j-1];
                        process_order[j-1] = tmp;
                    }
                System.out.println("Decoder channel order: "
                    + process_order[0] + ", " + process_order[1] + ", " + process_order[2]
                    + " (descending string length)");
            }

            // Start all decoder threads in process_order.
            ArrayList<Thread[]> thread_list = new ArrayList<Thread[]>();
            for (int pi = 0; pi < 3; pi++)
            {
                int            i              = process_order[pi];
                int[][]        frequency      = frequency_list.get(i);
                BigInteger[][] offset         = offset_list.get(i);
                int            number_of_segs = frequency.length;

                segment_length[i]      = string_length[i] / number_of_segs;
                int odd_segment_length = segment_length[i] + string_length[i] % number_of_segs;

                Thread[] decoder_thread = new Thread[number_of_segs];
                for (int j = 0; j < number_of_segs; j++)
                {
                    int n = (j == number_of_segs - 1) ? odd_segment_length : segment_length[i];
                    decoder_thread[j] = new Thread(new Decoder(offset[j], frequency[j], n, i, j));
                    decoder_thread[j].start();
                }
                thread_list.add(decoder_thread);
            }

            // Join, reassemble, and immediately start decompressor for each channel.
            byte[]   [] string_array        = new byte[3][];
            Thread[]    decompression_thread = new Thread[3];

            for (int pi = 0; pi < 3; pi++)
            {
                int      i              = process_order[pi];
                Thread[] decoder_thread = thread_list.get(pi);
                int      number_of_segs = decoder_thread.length;

                for (int j = 0; j < number_of_segs; j++)
                    try { decoder_thread[j].join(); }
                    catch (Exception e) { System.out.println("Decoder join: " + e); }

                byte[]   string  = new byte[string_length[i]];
                byte[][] segment = segment_list.get(i);
                for (int j = 0; j < segment.length; j++)
                    System.arraycopy(segment[j], 0, string, j * segment_length[i], segment[j].length);

                string_array[i] = string;

                decompression_thread[i] = new Thread(new Decompressor(i, string));
                decompression_thread[i].start();
            }

            for (int i = 0; i < 3; i++)
                try { decompression_thread[i].join(); }
                catch (Exception e) { System.out.println("Decompressor join: " + e); }

            stop = System.nanoTime();
            time = stop - start;
            System.out.println("It took " + (time / 1000000) + " ms to get arithmetic values and process data.");

            // ── Assemble the final RGB image ──────────────────────────────────
            BufferedImage image   = new BufferedImage(xdim, ydim, BufferedImage.TYPE_INT_RGB);
            int[][]       channel = new int[3][0];

            if (set_id == 0)
            {
                channel[0] = channel_array[0];
                channel[1] = channel_array[1];
                channel[2] = channel_array[2];
            }
            else if (set_id == 1)
            {
                channel[0] = channel_array[0];
                channel[1] = DeltaMapper.getDifference(channel_array[1], channel_array[2]);
                channel[2] = channel_array[1];
            }
            else if (set_id == 2)
            {
                channel[0] = channel_array[0];
                channel[1] = DeltaMapper.getDifference(channel_array[0], channel_array[2]);
                channel[2] = channel_array[1];
            }
            else if (set_id == 3)
            {
                channel[0] = channel_array[0];
                channel[1] = DeltaMapper.getDifference(channel_array[0], channel_array[1]);
                channel[2] = DeltaMapper.getSum(channel_array[2], channel[1]);
            }
            else if (set_id == 4)
            {
                channel[0] = channel_array[0];
                channel[1] = DeltaMapper.getDifference(channel_array[0], channel_array[1]);
                channel[2] = DeltaMapper.getSum(channel_array[0], channel_array[2]);
            }
            else if (set_id == 5)
            {
                channel[0] = DeltaMapper.getSum(channel_array[2], channel_array[0]);
                channel[1] = channel_array[0];
                channel[2] = channel_array[1];
            }
            else if (set_id == 6)
            {
                for (int i = 0; i < channel_array[2].length; i++)
                    channel_array[2][i] = -channel_array[2][i];
                channel[1] = DeltaMapper.getSum(channel_array[2], channel_array[0]);
                channel[0] = DeltaMapper.getSum(channel_array[1], channel[1]);
                channel[2] = channel_array[0];
            }
            else if (set_id == 7)
            {
                channel[0] = DeltaMapper.getSum(channel_array[0], channel_array[1]);
                channel[1] = channel_array[0];
                channel[2] = DeltaMapper.getSum(channel_array[0], channel_array[2]);
            }
            else if (set_id == 8)
            {
                channel[2] = DeltaMapper.getSum(channel_array[0], channel_array[1]);
                channel[0] = DeltaMapper.getDifference(channel[2], channel_array[2]);
                channel[1] = channel_array[0];
            }
            else if (set_id == 9)
            {
                channel[0] = DeltaMapper.getDifference(channel_array[0], channel_array[2]);
                channel[1] = DeltaMapper.getDifference(channel_array[0], channel_array[1]);
                channel[2] = channel_array[0];
            }

            start = System.nanoTime();

            if (pixel_quant == 0)
            {
                int[] pixel = DeltaMapper.getPixel(channel[0], channel[1], channel[2], xdim, pixel_shift);
                image.setRGB(0, 0, xdim, ydim, pixel, 0, xdim);
            }
            else
            {
                if (xdim > 600)
                {
                    Thread[] resize_thread = new Thread[3];
                    for (int i = 0; i < 3; i++)
                    {
                        resize_thread[i] = new Thread(new Resizer(channel[i], intermediate_xdim, xdim, ydim, i));
                        resize_thread[i].start();
                    }
                    for (int i = 0; i < 3; i++)
                        try { resize_thread[i].join(); }
                        catch (Exception e) { System.out.println("Resize join: " + e); }

                    int[] pixel = DeltaMapper.getPixel(resize_array[0], resize_array[1], resize_array[2], xdim, pixel_shift);
                    image.setRGB(0, 0, xdim, ydim, pixel, 0, xdim);
                }
                else
                {
                    int[] resized_blue  = ResizeMapper.resize(channel[0], intermediate_xdim, xdim, ydim);
                    int[] resized_green = ResizeMapper.resize(channel[1], intermediate_xdim, xdim, ydim);
                    int[] resized_red   = ResizeMapper.resize(channel[2], intermediate_xdim, xdim, ydim);
                    int[] pixel         = DeltaMapper.getPixel(resized_blue, resized_green, resized_red, xdim, pixel_shift);
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
    // Called once from the constructor before decoding starts.
    // -------------------------------------------------------------------------
    private void buildViewer(String filename)
    {
        Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
        int screen_xdim = (int) screenSize.getWidth();
        int screen_ydim = (int) screenSize.getHeight();

        // Compute a fit scale so the image starts fully visible inside a
        // reasonably-sized window (at most 70% of screen in each dimension).
        int max_canvas_w = (int)(screen_xdim * 0.70) - 40;
        int max_canvas_h = (int)(screen_ydim * 0.70) - 80;  // 80 accounts for menu + chrome
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
                    JViewport vp           = scroll_pane.getViewport();
                    Point     view_pos     = vp.getViewPosition();
                    Point     mouse_pt     = e.getPoint();
                    int       mouse_can_x  = mouse_pt.x + view_pos.x;
                    int       mouse_can_y  = mouse_pt.y + view_pos.y;

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

        frame = new JFrame("Arithmetic Reader  [decoding…]");
        frame.addWindowListener(new WindowAdapter()
        {
            public void windowClosing(WindowEvent event) { System.exit(0); }
        });

        // ── View / Zoom menu ────────────────────────────────────────────────
        JMenuBar menu_bar = new JMenuBar();
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
        int screen_w    = screen_xdim;
        int screen_h    = screen_ydim;
        int max_frame_w = (int)(screen_w * 0.70);
        int max_frame_h = (int)(screen_h * 0.70);
        frame.setSize(Math.min(xdim + 40,  max_frame_w),
                      Math.min(ydim + 80,  max_frame_h));
        frame.setLocation(5, 5);
        frame.setVisible(true);
    }

    // Called on the EDT once decoded_image is ready.
    private void showImage()
    {
        // Recompute fit scale now that we definitely have the image.
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
            AffineTransform t  = new AffineTransform();
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
        String status = (decoded_image == null) ? "decoding…" : (pct + "%");
        frame.setTitle("Arithmetic Reader  [" + status + "]");
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
    // Worker runnables (unchanged logic)
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
            resize_array[i] = ResizeMapper.resize(src, xdim, new_xdim, new_ydim);
        }
    }

    class Decoder implements Runnable
    {
        BigInteger [] offset;
        int []        frequency;
        int           n, i, j;

        public Decoder(BigInteger [] offset, int [] frequency, int n, int i, int j)
        {
            this.offset    = offset;
            this.frequency = frequency;
            this.n         = n;
            this.i         = i;
            this.j         = j;
        }

        public void run()
        {
            byte[][] segment = segment_list.get(i);
            segment[j] = ArithmeticMapper.getArithmeticValues(offset, frequency, n);
        }
    }

    class Decompressor implements Runnable
    {
        int    i;
        byte[] string;

        public Decompressor(int i, byte[] string)
        {
            this.i      = i;
            this.string = string;
        }

        public void run()
        {
            int[] channel_id = DeltaMapper.getChannels(set_id);

            int[] delta;
            int   current_xdim = 0;
            int   current_ydim = 0;

            if (compress_type > 0)
            {
                int iterations = StringMapper.getIterations(string);
                if (iterations != 0 && iterations != 16)
                    string = StringMapper.decompressStrings2(string);
                int bitlength = StringMapper.getBitlength(string);
                int[] table = (int[]) table_list.get(i);
                if (pixel_quant == 0)
                {
                    delta = StringMapper.unpackStrings(string, table, xdim * ydim, bitlength);
                    for (int j = 1; j < delta.length; j++)
                        delta[j] += delta_min[i];
                    current_xdim = xdim;
                    current_ydim = ydim;
                }
                else
                {
                    double factor     = pixel_quant;
                    factor           /= 10;
                    intermediate_xdim = xdim - (int)(factor * (xdim / 2 - 2));
                    intermediate_ydim = ydim - (int)(factor * (ydim / 2 - 2));
                    
                    delta = StringMapper.unpackStrings(string, table, intermediate_xdim * intermediate_ydim, bitlength);
                    for (int j = 1; j < delta.length; j++)
                        delta[j] += delta_min[i];
                    current_xdim = intermediate_xdim;
                    current_ydim = intermediate_ydim;
                }
            }
            else
            {
                if (pixel_quant == 0)
                {
                    delta = new int[xdim * ydim];
                    if (string.length != delta.length)
                    {
                        System.out.println("String length is " + string.length + ", delta length is " + delta.length);
                        System.exit(0);
                    }
                    for (int j = 0; j < delta.length; j++)
                        delta[j] = string[j];
                    current_xdim = xdim;
                    current_ydim = ydim;
                }
                else
                {
                    double factor     = pixel_quant;
                    factor           /= 10;
                    intermediate_xdim = xdim - (int)(factor * (xdim / 2 - 2));
                    intermediate_ydim = ydim - (int)(factor * (ydim / 2 - 2));
                    delta = new int[intermediate_xdim * intermediate_ydim];
                    if (string.length != delta.length)
                    {
                        System.out.println("String length is " + string.length + ", delta length is " + delta.length);
                        System.exit(0);
                    }
                    for (int j = 0; j < delta.length; j++)
                        delta[j] = string[j];
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
            else if (delta_type == 5) current_channel = DeltaMapper.getValuesFromMixedDeltas(delta, current_xdim, current_ydim, init[i], (byte[]) map_list.get(i));
            else if (delta_type == 6) current_channel = DeltaMapper.getValuesFromMixedDeltas2(delta, current_xdim, current_ydim, init[i], (byte[]) map_list.get(i));
            else if (delta_type == 7) current_channel = DeltaMapper.getValuesFromMixedDeltas4(delta, current_xdim, current_ydim, init[i], (byte[]) map_list.get(i));
            else if (delta_type == 8) current_channel = DeltaMapper.getValuesFromIdealDeltas(delta, current_xdim, current_ydim, init[i], (byte[]) map_list.get(i));

            if (channel_id[i] > 2)
                for (int j = 0; j < current_channel.length; j++)
                    current_channel[j] += min[i];

            channel_array[i] = current_channel;
        }
    }
}
