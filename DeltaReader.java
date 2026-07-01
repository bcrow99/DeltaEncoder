import java.awt.*;
import java.awt.image.*;
import java.io.*;
import java.awt.event.*;
import java.awt.geom.AffineTransform;
import java.util.*;
import java.util.zip.*;
import java.math.*;
import javax.swing.*;

public class DeltaReader
{
	// ---- Image dimensions ---------------------------------------------------
	int  xdim              = 0;
	int  ydim              = 0;
	int  intermediate_xdim = 0;
	int  intermediate_ydim = 0;

	// ---- Compression parameters (read from file) ----------------------------
	int  pixel_shift   = 0;
	int  pixel_quant   = 0;
	int  set_id        = 0;
	byte delta_type    = 0;
	byte compress_type = 0;
	byte entropy_type  = 0;   // 0=LZ77, 1=Huffman, 2=Arithmetic, 3=Fast Arithmetic

	// ---- Per-channel scalars ------------------------------------------------
	int[]  min               = new int[3];
	int[]  init              = new int[3];
	int[]  delta_min         = new int[3];
	int[]  length            = new int[3];
	int[]  compressed_length = new int[3];
	byte[] channel_iterations = new byte[3];

	// ---- Unary-string decode tables (compress_type > 0) --------------------
	ArrayList<int[]> table_list = new ArrayList<int[]>();

	// ---- Delta-type maps (delta_type 5-9) -----------------------------------
	ArrayList<byte[]> map_list = new ArrayList<byte[]>();

	// ---- Decoded channel arrays ---------------------------------------------
	int[][] channel_array = new int[3][0];
	int[][] resize_array  = new int[3][0];

	// ---- LZ77 per-channel data ----------------------------------------------
	ArrayList<byte[]> lz77_data_list   = new ArrayList<byte[]>();
	int[]             lz77_orig_length = new int[3];

	// ---- Huffman per-channel data -------------------------------------------
	ArrayList<int[]>  huff_rank_list = new ArrayList<int[]>();   // rank table
	ArrayList<byte[]> huff_cl_list   = new ArrayList<byte[]>();  // code lengths
	ArrayList<byte[]> huff_pay_list  = new ArrayList<byte[]>();  // packed payload
	int[]             huff_bl        = new int[3];               // bit length
	int[]             huff_pay_min   = new int[3];               // payload_min

	// ---- Arithmetic per-channel data ----------------------------------------
	ArrayList<int[][]>        freq_list    = new ArrayList<int[][]>();
	ArrayList<BigInteger[][]> offset_list  = new ArrayList<BigInteger[][]>();
	ArrayList<byte[][]>       segment_list = new ArrayList<byte[][]>();

	// ---- Fast Arithmetic per-channel data (entropy_type == 3) ---------------
	// Each entry is an array of encoded byte[] segments from getIntervalValueFast.
	// Frequency tables are shared with the Arithmetic path (freq_list above).
	ArrayList<byte[][]>       fast_enc_list = new ArrayList<byte[][]>();

	// ---- Label strings (mirrors DeltaWriter) --------------------------------
	static final String[] set_string = {
		"blue, green, and red.",
		"blue, red, and red-green.",
		"blue, red, and blue-green.",
		"blue, blue-green, and red-green.",
		"blue, blue-green, and red-blue.",
		"green, red, and blue-green.",
		"red, blue-green, and red-green.",
		"green, blue-green, and red-green.",
		"green, red-green, and red-blue.",
		"red, red-green, red-blue."};

	static final String[] delta_type_string = {
		"horizontal","vertical","average","med","directional",
		"adaptive","scanline (1)","scanline (2)","scanline (3)","scanline (4)","frame map","frame map (2)"};

	static final String[] entropy_type_string = {
		"LZ77","Huffman","Arithmetic","Fast Arithmetic"};
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

	// =========================================================================
	public static void main(String[] args)
	{
		if (args.length != 1) { System.out.println("Usage: java DeltaReader <filename>"); System.exit(0); }
		new DeltaReader(args[0]);
	}

	// =========================================================================
	public DeltaReader(String filename)
	{
		try
		{
			File            file = new File(filename);
			DataInputStream in   = new DataInputStream(new FileInputStream(file));

			// ---- Header ----
			xdim         = in.readShort();
			ydim         = in.readShort();
			pixel_shift  = in.readByte();
			pixel_quant  = in.readByte();
			set_id       = in.readByte();
			delta_type   = in.readByte();
			compress_type = in.readByte();
			entropy_type  = in.readByte();

			System.out.println("Image:        " + xdim + " x " + ydim);
			System.out.println("Channel set:  " + set_string[set_id & 0xFF]);
			System.out.println("Delta type:   " + delta_type_string[delta_type & 0xFF]);
			System.out.println("Entropy type: " + entropy_type_string[entropy_type & 0xFF]);
			System.out.println();

			int[] channel_id = DeltaMapper.getChannels(set_id);

			long start = System.nanoTime();

			for (int i = 0; i < 3; i++)
			{
				System.out.println("Reading channel " + i);

				// Common scalars
				min[i]                = in.readInt();
				init[i]               = in.readInt();
				delta_min[i]          = in.readInt();
				length[i]             = in.readInt();
				compressed_length[i]  = in.readInt();
				channel_iterations[i] = in.readByte();

				// Map (delta_type 5-9)
				if (delta_type >= 6 && delta_type <= 8)
				{
					int    ml  = in.readInt();
					int    pml = in.readInt();
					byte[] pm  = new byte[pml];
					in.readFully(pm);
					byte[] map_raw = new byte[ml];
					for (int q = 0; q < ml; q++)
						map_raw[q] = (byte) ((pm[q >> 2] >> ((q & 3) << 1)) & 0x3);
					map_list.add(map_raw);
				}
				else if (delta_type == 9)
				{
					int    ml     = in.readInt();
					int    pm_len = in.readInt();
					byte[] pm     = new byte[pm_len];
					in.readFully(pm);
					byte[] map = new byte[ml];
					for (int q = 0; q < ml; q++) map[q] = (byte)((pm[q >> 1] >> ((q & 1) << 2)) & 0xF);
					map_list.add(map);
				}
				else if (delta_type == 10)
				{
					int    ml          = in.readInt();
					int    bit_count   = in.readInt();
					int    encoded_len = in.readInt();
					byte[] encoded     = new byte[encoded_len];
					in.readFully(encoded);
					map_list.add(DeltaMapper.decodeMapHuffman(encoded, 8, ml, bit_count));
				}
				else if (delta_type == 11)
				{
					int    ml          = in.readInt();
					int    bit_count   = in.readInt();
					int    encoded_len = in.readInt();
					byte[] encoded     = new byte[encoded_len];
					in.readFully(encoded);
					map_list.add(DeltaMapper.decodeMapHuffman(encoded, 16, ml, bit_count));
				}

				// String table (compress_type > 0)
				if (compress_type > 0)
					table_list.add(readTable(in));

				// Entropy-specific payload
				if (entropy_type == 0)
				{
					// LZ77
					int    orig_len   = in.readInt();
					int    zip_len    = in.readInt();
					byte[] zip_data   = new byte[zip_len];
					in.readFully(zip_data);
					lz77_orig_length[i] = orig_len;
					lz77_data_list.add(zip_data);
				}
				else if (entropy_type == 1)
				{
					// Huffman
					int[]  rank_table    = readTable(in);
					int    pay_min       = in.readInt();
					int    n             = in.readInt();
					byte   init_val      = in.readByte();
					byte   max_delta     = in.readByte();
					int    pdt_len       = in.readByte() & 0xFF;  // unsigned byte
					byte[] pdt           = new byte[pdt_len];
					in.readFully(pdt);
					byte[] code_length   = CodeMapper.unpackLengthTable(n, init_val, max_delta, pdt);
					int    bl            = in.readInt();
					int    pay_len       = in.readInt();
					byte[] pay_bytes     = new byte[pay_len];
					in.readFully(pay_bytes);

					huff_rank_list.add(rank_table);
					huff_cl_list.add(code_length);
					huff_pay_list.add(pay_bytes);
					huff_bl[i]      = bl;
					huff_pay_min[i] = pay_min;
				}
				else if (entropy_type == 2) // Arithmetic (BigInteger)
				{
					int n_segs  = in.readInt();
					int len_type = in.readInt();
					int zfl     = in.readInt();
					byte[] zfd  = new byte[zfl];
					in.readFully(zfd);

					// Compute inflated frequency buffer size
					int n_bytes = n_segs * 256 * ((len_type == 0) ? 1 : (len_type == 1) ? 2 : 4);
					byte[] fb   = new byte[n_bytes];
					Inflater inf = new Inflater();
					inf.setInput(zfd, 0, zfl);
					inf.inflate(fb);
					inf.end();

					int[][] freqs = new int[n_segs][256];
					if (len_type == 0)
						for (int k = 0; k < n_segs; k++)
							for (int m = 0; m < 256; m++) { freqs[k][m] = fb[k*256+m]; if (freqs[k][m]<0) freqs[k][m]+=256; }
					else if (len_type == 1)
						for (int k = 0; k < n_segs; k++)
							for (int m = 0; m < 256; m++)
							{
								int a = fb[k*512+2*m]; if(a<0)a+=256;
								int b = fb[k*512+2*m+1]; if(b<0)b+=256; b<<=8;
								freqs[k][m] = a|b;
							}
					else
						for (int k = 0; k < n_segs; k++)
							for (int m = 0; m < 256; m++)
							{
								int a=fb[k*1024+4*m];if(a<0)a+=256;
								int b=fb[k*1024+4*m+1];if(b<0)b+=256;b<<=8;
								int c=fb[k*1024+4*m+2];if(c<0)c+=256;c<<=16;
								int d=fb[k*1024+4*m+3];if(d<0)d+=256;d<<=24;
								freqs[k][m]=a|b|c|d;
							}

					BigInteger[][] offsets = new BigInteger[n_segs][2];
					for (int k = 0; k < n_segs; k++)
					{
						int    ll;
						byte[] bb;
						ll = in.readInt(); bb = new byte[ll]; in.readFully(bb); offsets[k][0] = new BigInteger(bb);
						ll = in.readInt(); bb = new byte[ll]; in.readFully(bb); offsets[k][1] = new BigInteger(bb);
					}

					freq_list.add(freqs);
					offset_list.add(offsets);
					segment_list.add(new byte[n_segs][0]);
				}
				else // entropy_type == 3 (Fast Arithmetic)
				{
					// Frequency tables use the same on-disk format as Arithmetic.
					int n_segs   = in.readInt();
					int len_type = in.readInt();
					int zfl      = in.readInt();
					byte[] zfd   = new byte[zfl];
					in.readFully(zfd);

					int n_bytes = n_segs * 256 * ((len_type == 0) ? 1 : (len_type == 1) ? 2 : 4);
					byte[] fb   = new byte[n_bytes];
					Inflater inf = new Inflater();
					inf.setInput(zfd, 0, zfl);
					inf.inflate(fb);
					inf.end();

					int[][] freqs = new int[n_segs][256];
					if (len_type == 0)
						for (int k = 0; k < n_segs; k++)
							for (int m = 0; m < 256; m++) { freqs[k][m] = fb[k*256+m]; if (freqs[k][m]<0) freqs[k][m]+=256; }
					else if (len_type == 1)
						for (int k = 0; k < n_segs; k++)
							for (int m = 0; m < 256; m++)
							{
								int a = fb[k*512+2*m]; if(a<0)a+=256;
								int b = fb[k*512+2*m+1]; if(b<0)b+=256; b<<=8;
								freqs[k][m] = a|b;
							}
					else
						for (int k = 0; k < n_segs; k++)
							for (int m = 0; m < 256; m++)
							{
								int a=fb[k*1024+4*m];if(a<0)a+=256;
								int b=fb[k*1024+4*m+1];if(b<0)b+=256;b<<=8;
								int c=fb[k*1024+4*m+2];if(c<0)c+=256;c<<=16;
								int d=fb[k*1024+4*m+3];if(d<0)d+=256;d<<=24;
								freqs[k][m]=a|b|c|d;
							}

					freq_list.add(freqs);

					// Per-segment data: a single encoded byte[] from getIntervalValueFast
					// (includes its own 4-byte bit-length header).
					byte[][] fast_enc = new byte[n_segs][];
					for (int k = 0; k < n_segs; k++)
					{
						int enc_len  = in.readInt();
						fast_enc[k]  = new byte[enc_len];
						in.readFully(fast_enc[k]);
					}
					fast_enc_list.add(fast_enc);
				}
			}

			in.close();
			System.out.println("File read in " + ((System.nanoTime()-start)/1_000_000) + " ms.");

			buildViewer(filename);

			// ---- Run Decompressor threads (handles all entropy types) --------
			start = System.nanoTime();
			Thread[] decompression_thread = new Thread[3];
			for (int i = 0; i < 3; i++)
			{
				decompression_thread[i] = new Thread(new Decompressor(i));
				decompression_thread[i].start();
			}
			for (int i = 0; i < 3; i++) decompression_thread[i].join();

			System.out.println("Channels processed in " + ((System.nanoTime()-start)/1_000_000) + " ms.");

			// ---- Assemble RGB -----------------------------------------------
			start = System.nanoTime();
			int[][] ch = new int[3][0];
			if      (set_id==0){ch[0]=channel_array[0];ch[1]=channel_array[1];ch[2]=channel_array[2];}
			else if (set_id==1){ch[0]=channel_array[0];ch[1]=DeltaMapper.getDifference(channel_array[1],channel_array[2]);ch[2]=channel_array[1];}
			else if (set_id==2){ch[0]=channel_array[0];ch[1]=DeltaMapper.getDifference(channel_array[0],channel_array[2]);ch[2]=channel_array[1];}
			else if (set_id==3){ch[0]=channel_array[0];ch[1]=DeltaMapper.getDifference(channel_array[0],channel_array[1]);ch[2]=DeltaMapper.getSum(channel_array[2],ch[1]);}
			else if (set_id==4){ch[0]=channel_array[0];ch[1]=DeltaMapper.getDifference(channel_array[0],channel_array[1]);ch[2]=DeltaMapper.getSum(channel_array[0],channel_array[2]);}
			else if (set_id==5){ch[0]=DeltaMapper.getSum(channel_array[2],channel_array[0]);ch[1]=channel_array[0];ch[2]=channel_array[1];}
			else if (set_id==6){for(int i=0;i<channel_array[2].length;i++)channel_array[2][i]=-channel_array[2][i];ch[1]=DeltaMapper.getSum(channel_array[2],channel_array[0]);ch[0]=DeltaMapper.getSum(channel_array[1],ch[1]);ch[2]=channel_array[0];}
			else if (set_id==7){ch[0]=DeltaMapper.getSum(channel_array[0],channel_array[1]);ch[1]=channel_array[0];ch[2]=DeltaMapper.getSum(channel_array[0],channel_array[2]);}
			else if (set_id==8){ch[2]=DeltaMapper.getSum(channel_array[0],channel_array[1]);ch[0]=DeltaMapper.getDifference(ch[2],channel_array[2]);ch[1]=channel_array[0];}
			else if (set_id==9){ch[0]=DeltaMapper.getDifference(channel_array[0],channel_array[2]);ch[1]=DeltaMapper.getDifference(channel_array[0],channel_array[1]);ch[2]=channel_array[0];}

			BufferedImage image = new BufferedImage(xdim, ydim, BufferedImage.TYPE_INT_RGB);
			if (pixel_quant == 0)
			{
				int[] px = DeltaMapper.getPixel(ch[0], ch[1], ch[2], xdim, pixel_shift);
				image.setRGB(0, 0, xdim, ydim, px, 0, xdim);
			}
			else
			{
				if (xdim > 600)
				{
					Thread[] rt = new Thread[3];
					for (int i = 0; i < 3; i++) { rt[i] = new Thread(new Resizer(ch[i], intermediate_xdim, xdim, ydim, i)); rt[i].start(); }
					for (int i = 0; i < 3; i++) rt[i].join();
					int[] px = DeltaMapper.getPixel(resize_array[0], resize_array[1], resize_array[2], xdim, pixel_shift);
					image.setRGB(0, 0, xdim, ydim, px, 0, xdim);
				}
				else
				{
					int[] px = DeltaMapper.getPixel(
						ResizeMapper.resize(ch[0], intermediate_xdim, xdim, ydim),
						ResizeMapper.resize(ch[1], intermediate_xdim, xdim, ydim),
						ResizeMapper.resize(ch[2], intermediate_xdim, xdim, ydim),
						xdim, pixel_shift);
					image.setRGB(0, 0, xdim, ydim, px, 0, xdim);
				}
			}
			System.out.println("RGB assembled in " + ((System.nanoTime()-start)/1_000_000) + " ms.");

			decoded_image = image;
			SwingUtilities.invokeLater(() -> showImage());
		}
		catch (Exception e) { System.out.println(e.toString()); }
	}

	// ---- table I/O helper ---------------------------------------------------
	private static int[] readTable(DataInputStream in) throws IOException
	{
		int    tl  = in.readShort();
		int[]  tbl = new int[tl];
		int    max = Byte.MAX_VALUE * 2 + 1;
		if (tl <= max)
			for (int k = 0; k < tl; k++) { tbl[k] = in.readByte(); if (tbl[k] < 0) tbl[k] = max + 1 + tbl[k]; }
		else
			for (int k = 0; k < tl; k++) tbl[k] = in.readShort();
		return tbl;
	}

	// =========================================================================
	// Viewer
	// =========================================================================
	private void buildViewer(String filename)
	{
		Dimension sc  = Toolkit.getDefaultToolkit().getScreenSize();
		int sw = (int)sc.getWidth(), sh = (int)sc.getHeight();
		fit_scale  = Math.min(1.0, Math.min((double)(sw*70/100-40)/xdim, (double)(sh*70/100-80)/ydim));
		zoom_scale = fit_scale;

		image_canvas = new ImageCanvas();
		image_canvas.setPreferredSize(new Dimension(Math.max(1,(int)(xdim*zoom_scale)), Math.max(1,(int)(ydim*zoom_scale))));

		scroll_pane = new JScrollPane(image_canvas, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
		scroll_pane.getVerticalScrollBar().setUnitIncrement(16);
		scroll_pane.getHorizontalScrollBar().setUnitIncrement(16);
		scroll_pane.addMouseWheelListener(new MouseWheelListener()
		{
			public void mouseWheelMoved(MouseWheelEvent e)
			{
				if (e.isControlDown())
				{
					JViewport vp = scroll_pane.getViewport();
					Point vpos = vp.getViewPosition(); Point mpt = e.getPoint();
					int mcx = mpt.x+vpos.x, mcy = mpt.y+vpos.y;
					double old = zoom_scale;
					zoom_scale = (e.getWheelRotation()<0) ? Math.min(ZOOM_MAX,zoom_scale*ZOOM_FACTOR) : Math.max(ZOOM_MIN,zoom_scale/ZOOM_FACTOR);
					if (zoom_scale==old) return;
					updateDisplayImage();
					image_canvas.setPreferredSize(new Dimension((int)(xdim*zoom_scale),(int)(ydim*zoom_scale)));
					image_canvas.revalidate(); image_canvas.repaint();
					double r = zoom_scale/old;
					vp.setViewPosition(new Point(Math.max(0,(int)(mcx*r)-mpt.x), Math.max(0,(int)(mcy*r)-mpt.y)));
					updateTitle();
				}
				else scroll_pane.dispatchEvent(e);
			}
		});

		frame = new JFrame("Delta Reader  [decoding\u2026]");
		frame.addWindowListener(new WindowAdapter(){ public void windowClosing(WindowEvent e){ System.exit(0); }});

		JMenuBar mb = new JMenuBar();
		JMenu vm    = new JMenu("View");
		JMenuItem zi = new JMenuItem("Zoom In (+)");
		zi.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_EQUALS, InputEvent.CTRL_DOWN_MASK));
		zi.addActionListener(e -> zoomBy(ZOOM_FACTOR)); vm.add(zi);
		JMenuItem zo = new JMenuItem("Zoom Out (-)");
		zo.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_MINUS, InputEvent.CTRL_DOWN_MASK));
		zo.addActionListener(e -> zoomBy(1.0/ZOOM_FACTOR)); vm.add(zo);
		JMenuItem zf = new JMenuItem("Fit to Window");
		zf.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_0, InputEvent.CTRL_DOWN_MASK));
		zf.addActionListener(e ->
		{
			Dimension vps = scroll_pane.getViewport().getSize();
			zoom_scale = Math.min((double)vps.width/xdim, (double)vps.height/ydim);
			updateDisplayImage(); image_canvas.setPreferredSize(new Dimension((int)(xdim*zoom_scale),(int)(ydim*zoom_scale)));
			image_canvas.revalidate(); image_canvas.repaint(); updateTitle();
		}); vm.add(zf);
		JMenuItem za = new JMenuItem("Actual Size (100%)");
		za.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_1, InputEvent.CTRL_DOWN_MASK));
		za.addActionListener(e ->
		{
			zoom_scale = 1.0; updateDisplayImage();
			image_canvas.setPreferredSize(new Dimension(xdim,ydim));
			image_canvas.revalidate(); image_canvas.repaint(); updateTitle();
		}); vm.add(za);
		mb.add(vm);
		frame.setJMenuBar(mb);
		frame.getContentPane().add(scroll_pane, BorderLayout.CENTER);
		frame.setSize(Math.min(xdim+40,(int)(sw*0.70)), Math.min(ydim+80,(int)(sh*0.70)));
		frame.setLocation(5,5); frame.setVisible(true);
	}

	private void showImage()
	{
		Dimension vps = scroll_pane.getViewport().getSize();
		fit_scale  = Math.min(1.0, Math.min(vps.width>0?(double)vps.width/xdim:1.0, vps.height>0?(double)vps.height/ydim:1.0));
		zoom_scale = fit_scale;
		updateDisplayImage();
		image_canvas.setPreferredSize(new Dimension((int)(xdim*zoom_scale),(int)(ydim*zoom_scale)));
		image_canvas.revalidate(); image_canvas.repaint(); updateTitle();
		int display_w  = (int)(xdim * zoom_scale);
		int display_h  = (int)(ydim * zoom_scale);
		int overhead_w = frame.getWidth()  - vps.width;
		int overhead_h = frame.getHeight() - vps.height;
		Dimension screen = Toolkit.getDefaultToolkit().getScreenSize();
		frame.setSize(Math.min(display_w + overhead_w, (int)(screen.width  * 0.70)),
		              Math.min(display_h + overhead_h, (int)(screen.height * 0.70)));
	}

	private void zoomBy(double factor)
	{
		double ns = Math.max(ZOOM_MIN, Math.min(ZOOM_MAX, zoom_scale*factor));
		if (ns==zoom_scale) return;
		JViewport vp = scroll_pane.getViewport(); Point vpos = vp.getViewPosition(); Dimension vs = vp.getSize();
		double cx=vpos.x+vs.width/2.0, cy=vpos.y+vs.height/2.0, r=ns/zoom_scale;
		zoom_scale = ns; updateDisplayImage();
		image_canvas.setPreferredSize(new Dimension((int)(xdim*zoom_scale),(int)(ydim*zoom_scale)));
		image_canvas.revalidate(); image_canvas.repaint();
		vp.setViewPosition(new Point(Math.max(0,(int)(cx*r-vs.width/2.0)), Math.max(0,(int)(cy*r-vs.height/2.0))));
		updateTitle();
	}

	private void updateDisplayImage()
	{
		if (decoded_image==null) return;
		if (zoom_scale==1.0) { display_image=decoded_image; return; }
		int w=Math.max(1,(int)(xdim*zoom_scale)), h=Math.max(1,(int)(ydim*zoom_scale));
		AffineTransform t = new AffineTransform(); t.scale(zoom_scale,zoom_scale);
		display_image = new AffineTransformOp(t,AffineTransformOp.TYPE_BILINEAR).filter(decoded_image, new BufferedImage(w,h,decoded_image.getType()));
	}

	private void updateTitle()
	{
		if (frame==null) return;
		int pct = (int)Math.round(zoom_scale*100);
		frame.setTitle("Delta Reader  [" + (decoded_image==null ? "decoding\u2026" : pct+"%") + "]");
	}

	// =========================================================================
	// ImageCanvas
	// =========================================================================
	class ImageCanvas extends JPanel
	{
		public ImageCanvas() { setOpaque(true); }
		@Override public Dimension getPreferredSize()
		{
			return (display_image!=null) ? new Dimension(display_image.getWidth(),display_image.getHeight())
				: new Dimension(Math.max(1,(int)(xdim*zoom_scale)), Math.max(1,(int)(ydim*zoom_scale)));
		}
		@Override protected synchronized void paintComponent(Graphics g) { super.paintComponent(g); if(display_image!=null) g.drawImage(display_image,0,0,this); }
	}

	// =========================================================================
	// Resizer
	// =========================================================================
	class Resizer implements Runnable
	{
		int[] src; int xdim,nx,ny,i;
		Resizer(int[] src,int xdim,int nx,int ny,int i){this.src=src;this.xdim=xdim;this.nx=nx;this.ny=ny;this.i=i;}
		public void run(){ resize_array[i]=ResizeMapper.resize(src,xdim,nx,ny); }
	}

	// =========================================================================
	// Decompressor — one per channel; handles all entropy types
	// =========================================================================
	class Decompressor implements Runnable
	{
		int i;
		Decompressor(int i){ this.i=i; }

		public void run()
		{
			try
			{
				int[] channel_id = DeltaMapper.getChannels(set_id);

				// ---- Compute current dimensions ----
				int cur_xdim, cur_ydim, size;
				if (pixel_quant == 0)
				{
					cur_xdim = xdim; cur_ydim = ydim;
				}
				else
				{
					double f = pixel_quant / 10.0;
					intermediate_xdim = xdim - (int)(f*(xdim/2-2));
					intermediate_ydim = ydim - (int)(f*(ydim/2-2));
					cur_xdim = intermediate_xdim; cur_ydim = intermediate_ydim;
				}
				size = cur_xdim * cur_ydim;

				// ---- Entropy decode → payload bytes ----
				byte[] payload;

				if (entropy_type == 0)
				{
					// LZ77
					byte[]   zip_data = lz77_data_list.get(i);
					int      orig_len = lz77_orig_length[i];
					byte[]   buf      = new byte[orig_len];
					Inflater inf      = new Inflater();
					inf.setInput(zip_data); inf.inflate(buf); inf.end();
					payload = buf;
				}
				else if (entropy_type == 1)
				{
					// Huffman
					int[]  rank_table = huff_rank_list.get(i);
					byte[] code_len   = huff_cl_list.get(i);
					byte[] packed     = huff_pay_list.get(i);
					int    bl         = huff_bl[i];
					int    pay_min    = huff_pay_min[i];
					int[]  hcode      = CodeMapper.getCanonicalCode(code_len);

					// Number of payload symbols = payload byte count
					int num_sym = (compress_type == 0) ? size : StringMapper.getBytelength(compressed_length[i]);

					int[] decoded = new int[num_sym];
					CodeMapper.unpackCode(packed, rank_table, hcode, code_len, bl, decoded);

					// Restore original unsigned byte values, then cast to signed byte
					payload = new byte[num_sym];
					for (int k = 0; k < num_sym; k++)
						payload[k] = (byte)(decoded[k] + pay_min);
				}
				else if (entropy_type == 2) // Arithmetic (BigInteger)
				{
					// Expected total payload bytes
					int expected = (compress_type == 0) ? size : StringMapper.getBytelength(compressed_length[i]);

					int[][]        freqs   = freq_list.get(i);
					BigInteger[][] offsets = offset_list.get(i);
					int            n_segs  = freqs.length;
					int            seg_len = expected / n_segs;
					int            odd_len = seg_len + expected % n_segs;

					byte[][] segs = segment_list.get(i);
					for (int m = 0; m < n_segs; m++) segs[m] = new byte[m < n_segs-1 ? seg_len : odd_len];

					// Decode segments in parallel
					Thread[] thr = new Thread[n_segs];
					for (int k = 0; k < n_segs; k++)
					{
						final int ki = k;
						thr[k] = new Thread(() -> segs[ki] = ArithmeticMapper.getArithmeticValues(offsets[ki], freqs[ki], segs[ki].length));
						thr[k].start();
					}
					for (Thread t : thr) t.join();

					// Reassemble
					byte[] buf = new byte[expected];
					int pos = 0;
					for (int m = 0; m < n_segs; m++)
					{ System.arraycopy(segs[m], 0, buf, pos, segs[m].length); pos += segs[m].length; }
					payload = buf;
				}
				else // entropy_type == 3 (Fast Arithmetic)
				{
					int expected  = (compress_type == 0) ? size : StringMapper.getBytelength(compressed_length[i]);

					int[][]  freqs    = freq_list.get(i);
					byte[][] fast_enc = fast_enc_list.get(i);
					int      n_segs   = freqs.length;
					int      seg_len  = expected / n_segs;
					int      odd_len  = seg_len + expected % n_segs;

					byte[][] segs = new byte[n_segs][];
					for (int m = 0; m < n_segs; m++) segs[m] = new byte[m < n_segs-1 ? seg_len : odd_len];

					// Decode segments in parallel using fast long-arithmetic decoder
					Thread[] thr = new Thread[n_segs];
					for (int k = 0; k < n_segs; k++)
					{
						final int    ki   = k;
						final byte[] enc  = fast_enc[k];
						final int[]  freq = freqs[k];
						final int    n    = segs[k].length;
						thr[k] = new Thread(() -> segs[ki] = ArithmeticMapper.getArithmeticValuesFast(enc, freq, n));
						thr[k].start();
					}
					for (Thread t : thr) t.join();

					// Reassemble
					byte[] buf = new byte[expected];
					int pos = 0;
					for (int m = 0; m < n_segs; m++)
					{ System.arraycopy(segs[m], 0, buf, pos, segs[m].length); pos += segs[m].length; }
					payload = buf;
				}

				// ---- Payload → delta values ----
				int[] delta;

				if (compress_type == 0)
				{
					// payload bytes are (delta[k] - delta_min) stored as signed bytes
					delta    = new int[size];
					delta[0] = 0;
					for (int k = 1; k < size; k++) delta[k] = payload[k] + delta_min[i];
				}
				else
				{
					// payload is the unary-string byte array
					int[]  tbl  = table_list.get(i);
					byte[] str  = payload;
					str         = StringMapper.decompressStrings(str);
					delta       = StringMapper.unpackStrings(str, tbl, size, length[i]);
					delta[0]    = 0;
					for (int k = 1; k < delta.length; k++) delta[k] += delta_min[i];
				}

				// ---- Delta → channel values ----
				int[] cur_ch;
				if      (delta_type == 0) cur_ch = DeltaMapper.getValuesFromHorizontalDeltas(delta, cur_xdim, cur_ydim, init[i]);
				else if (delta_type == 1) cur_ch = DeltaMapper.getValuesFromVerticalDeltas(delta, cur_xdim, cur_ydim, init[i]);
				else if (delta_type == 2) cur_ch = DeltaMapper.getValuesFromAverageDeltas(delta, cur_xdim, cur_ydim, init[i]);
				else if (delta_type == 3) cur_ch = DeltaMapper.getValuesFromMedDeltas(delta, cur_xdim, cur_ydim, init[i]);
				else if (delta_type == 4) cur_ch = DeltaMapper.getValuesFromDirectionalDeltas(delta, cur_xdim, cur_ydim, init[i]);
				else if (delta_type == 5)  cur_ch = DeltaMapper.getValuesFromAdaptiveDeltas(delta, cur_xdim, cur_ydim, init[i]);
				else if (delta_type == 6)  cur_ch = DeltaMapper.getValuesFromMixedDeltas(delta, cur_xdim, cur_ydim, init[i], map_list.get(i));
				else if (delta_type == 7)  cur_ch = DeltaMapper.getValuesFromMixedDeltas2(delta, cur_xdim, cur_ydim, init[i], map_list.get(i));
				else if (delta_type == 8)  cur_ch = DeltaMapper.getValuesFromMixedDeltas4(delta, cur_xdim, cur_ydim, init[i], map_list.get(i));
				else if (delta_type == 9)  cur_ch = DeltaMapper.getValuesFromMixedDeltas16Rows(delta, cur_xdim, cur_ydim, init[i], map_list.get(i));
				else if (delta_type == 10) cur_ch = DeltaMapper.getValuesFromIdealDeltas8(delta, cur_xdim, cur_ydim, init[i], map_list.get(i));
				else if (delta_type == 11) cur_ch = DeltaMapper.getValuesFromIdealDeltas16(delta, cur_xdim, cur_ydim, init[i], map_list.get(i));
				else                       cur_ch = DeltaMapper.getValuesFromHorizontalDeltas(delta, cur_xdim, cur_ydim, init[i]);

				// Restore difference-channel offset
				if (channel_id[i] > 2)
					for (int k = 0; k < cur_ch.length; k++) cur_ch[k] += min[i];

				channel_array[i] = cur_ch;
			}
			catch (Exception e) { System.out.println("Decompressor " + i + ": " + e); }
		}
	}
}
