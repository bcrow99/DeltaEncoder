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
	int  xdim=0, ydim=0, intermediate_xdim=0, intermediate_ydim=0;
	int  pixel_shift=0, pixel_quant=0, set_id=0;
	byte delta_type=0, entropy_type=0, data_type=0;

	ArrayList string_list  = new ArrayList();
	ArrayList table_list   = new ArrayList();
	ArrayList map_list     = new ArrayList();

	ArrayList<int[]>  huff_rank_list = new ArrayList<>();
	ArrayList<byte[]> huff_cl_list   = new ArrayList<>();
	ArrayList<byte[]> huff_pay_list  = new ArrayList<>();
	int[] huff_bl      = new int[3];
	int[] huff_pay_min = new int[3];

	ArrayList<int[][]>                  freq_list     = new ArrayList<>();
	ArrayList<java.math.BigInteger[][]> offset_list   = new ArrayList<>();
	ArrayList<byte[][]>                 fast_enc_list = new ArrayList<>();
	int[] n_segs_arr = new int[3];

	int[][] channel_array = new int[3][0];
	int[][] resize_array  = new int[3][0];
	int[]  min   = new int[3];
	int[]  init  = new int[3];
	int[]  delta_min          = new int[3];
	int[]  length             = new int[3];
	int[]  compressed_length  = new int[3];
	byte[] channel_iterations = new byte[3];

	BufferedImage decoded_image = null;
	BufferedImage display_image = null;
	ImageCanvas   image_canvas  = null;
	JScrollPane   scroll_pane   = null;
	JFrame        frame         = null;
	double zoom_scale = 1.0;
	double fit_scale  = 1.0;
	static final double ZOOM_FACTOR = 1.25, ZOOM_MIN = 0.05, ZOOM_MAX = 32.0;

	public static void main(String[] args)
	{
		if (args.length != 1) { System.out.println("Usage: java SimpleReader <filename>"); System.exit(0); }
		new SimpleReader(args[0]);
	}

	public SimpleReader(String filename)
	{
		try {
			File file = new File(filename);
			System.out.println("Opening: " + file.getAbsolutePath() + " (" + file.length() + " bytes)");
			DataInputStream in = new DataInputStream(new FileInputStream(file));

			xdim         = in.readShort();
			ydim         = in.readShort();
			pixel_shift  = in.readByte();
			pixel_quant  = in.readByte();
			set_id       = in.readByte();
			delta_type   = in.readByte();
			entropy_type = in.readByte();
			data_type    = in.readByte();

			System.out.println("Image: " + xdim + " x " + ydim);
			System.out.println("Set id: " + set_id + "  Delta type: " + delta_type + "  Entropy: " + entropy_type);

			long start = System.nanoTime();
			for (int i = 0; i < 3; i++)
			{
				System.out.println("Reading channel " + i);
				min[i]                = in.readInt();
				init[i]               = in.readInt();
				delta_min[i]          = in.readInt();
				length[i]             = in.readInt();
				compressed_length[i]  = in.readInt();
				channel_iterations[i] = in.readByte();

				if (delta_type >= 6)
				{
					int map0_adj = in.readInt();
					int ml       = in.readInt();
					int[] tbl    = readTable(in);
					int dmin     = in.readInt();
					int bl       = in.readInt();
					byte[] str   = new byte[StringMapper.getBytelength(bl)];
					in.readFully(str);
					byte[] decomp = StringMapper.decompressStrings(str);
					int[]  vals   = StringMapper.unpackStrings(decomp, tbl, ml, StringMapper.getBitlength(decomp));
					byte[] map    = new byte[ml];
					vals[0] = map0_adj;
					for (int k = 0; k < ml; k++) map[k] = (byte)(vals[k] + dmin);
					map_list.add(map);
				}

				table_list.add(readTable(in));

				if (entropy_type == 0)
				{
					// LZ77
					int    orig_len = in.readInt();
					int    zl       = in.readInt();
					byte[] zd       = new byte[zl];
					in.readFully(zd);
					byte[] str = new byte[orig_len];
					Inflater inf = new Inflater();
					inf.setInput(zd, 0, zl);
					inf.inflate(str);
					inf.end();
					string_list.add(str);
				}
				else if (entropy_type == 1)
				{
					// Huffman
					int[]  rt   = readTable(in);
					int    pmin = in.readInt();
					int    n    = in.readInt();
					byte   iv   = in.readByte();
					byte   mx   = in.readByte();
					int    pdtl = in.readByte() & 0xFF;
					byte[] pdt  = new byte[pdtl];
					in.readFully(pdt);
					byte[] cl   = CodeMapper.unpackLengthTable(n, iv, mx, pdt);
					int    hbl  = in.readInt();
					int    plen = in.readInt();
					byte[] pb   = new byte[plen];
					in.readFully(pb);
					huff_rank_list.add(rt);
					huff_cl_list.add(cl);
					huff_pay_list.add(pb);
					huff_bl[i]      = hbl;
					huff_pay_min[i] = pmin;
				}
				else
				{
					// Arithmetic (2=slow, 3=fast) — always String payload
					int orig_len = in.readInt();
					int n_segs = in.readInt(), lt = in.readInt(), zfl = in.readInt();
					byte[] zfd = new byte[zfl]; in.readFully(zfd);
					int bpe = (lt==0)?1:(lt==1)?2:4;
					byte[] fb = new byte[n_segs*256*bpe];
					Inflater inf = new Inflater(); inf.setInput(zfd,0,zfl); inf.inflate(fb); inf.end();
					int[][] freqs = new int[n_segs][256];
					for(int m=0;m<n_segs;m++) for(int k=0;k<256;k++){
						int base=m*256*bpe+k*bpe, v=0;
						for(int b=0;b<bpe;b++){int bv=fb[base+b]&0xFF; v|=bv<<(8*b);}
						freqs[m][k]=v;
					}
					freq_list.add(freqs); n_segs_arr[i]=n_segs;
					string_list.add(new byte[]{(byte)(orig_len>>24),(byte)(orig_len>>16),(byte)(orig_len>>8),(byte)orig_len});
					if (entropy_type == 2)
					{
						java.math.BigInteger[][] offsets = new java.math.BigInteger[n_segs][2];
						for(int m=0;m<n_segs;m++){
							int ll; byte[] bb;
							ll=in.readInt(); bb=new byte[ll]; in.readFully(bb); offsets[m][0]=new java.math.BigInteger(bb);
							ll=in.readInt(); bb=new byte[ll]; in.readFully(bb); offsets[m][1]=new java.math.BigInteger(bb);
						}
						offset_list.add(offsets);
					}
					else
					{
						byte[][] encs = new byte[n_segs][];
						for(int m=0;m<n_segs;m++){int el=in.readInt();encs[m]=new byte[el];in.readFully(encs[m]);}
						fast_enc_list.add(encs);
					}
				}
			}
			in.close();
			System.out.println("File read in " + ((System.nanoTime()-start)/1_000_000) + " ms.");

			buildViewer(filename);

			start = System.nanoTime();
			Thread[] decompression_thread = new Thread[3];
			for (int i = 0; i < 3; i++) { decompression_thread[i] = new Thread(new Decompressor(i)); decompression_thread[i].start(); }
			for (int i = 0; i < 3; i++) decompression_thread[i].join();
			System.out.println("Channels processed in " + ((System.nanoTime()-start)/1_000_000) + " ms.");

			start = System.nanoTime();
			int[][] ch = new int[3][0];
			if(set_id==0){ch[0]=channel_array[0];ch[1]=channel_array[1];ch[2]=channel_array[2];}
			else if(set_id==1){ch[0]=channel_array[0];ch[1]=DeltaMapper.getDifference(channel_array[1],channel_array[2]);ch[2]=channel_array[1];}
			else if(set_id==2){ch[0]=channel_array[0];ch[1]=DeltaMapper.getDifference(channel_array[0],channel_array[2]);ch[2]=channel_array[1];}
			else if(set_id==3){ch[0]=channel_array[0];ch[1]=DeltaMapper.getDifference(channel_array[0],channel_array[1]);ch[2]=DeltaMapper.getSum(channel_array[2],ch[1]);}
			else if(set_id==4){ch[0]=channel_array[0];ch[1]=DeltaMapper.getDifference(channel_array[0],channel_array[1]);ch[2]=DeltaMapper.getSum(channel_array[0],channel_array[2]);}
			else if(set_id==5){ch[0]=DeltaMapper.getSum(channel_array[2],channel_array[0]);ch[1]=channel_array[0];ch[2]=channel_array[1];}
			else if(set_id==6){for(int i=0;i<channel_array[2].length;i++)channel_array[2][i]=-channel_array[2][i];ch[1]=DeltaMapper.getSum(channel_array[2],channel_array[0]);ch[0]=DeltaMapper.getSum(channel_array[1],ch[1]);ch[2]=channel_array[0];}
			else if(set_id==7){ch[0]=DeltaMapper.getSum(channel_array[0],channel_array[1]);ch[1]=channel_array[0];ch[2]=DeltaMapper.getSum(channel_array[0],channel_array[2]);}
			else if(set_id==8){ch[2]=DeltaMapper.getSum(channel_array[0],channel_array[1]);ch[0]=DeltaMapper.getDifference(ch[2],channel_array[2]);ch[1]=channel_array[0];}
			else{ch[0]=DeltaMapper.getDifference(channel_array[0],channel_array[2]);ch[1]=DeltaMapper.getDifference(channel_array[0],channel_array[1]);ch[2]=channel_array[0];}

			BufferedImage image = new BufferedImage(xdim, ydim, BufferedImage.TYPE_INT_RGB);
			if (pixel_quant == 0)
			{
				image.setRGB(0, 0, xdim, ydim, DeltaMapper.getPixel(ch[0], ch[1], ch[2], xdim, pixel_shift), 0, xdim);
			}
			else if (xdim > 600)
			{
				Thread[] rt = new Thread[3];
				for (int i = 0; i < 3; i++) { rt[i] = new Thread(new Resizer(ch[i], intermediate_xdim, xdim, ydim, i)); rt[i].start(); }
				for (int i = 0; i < 3; i++) rt[i].join();
				image.setRGB(0, 0, xdim, ydim, DeltaMapper.getPixel(resize_array[0], resize_array[1], resize_array[2], xdim, pixel_shift), 0, xdim);
			}
			else
			{
				image.setRGB(0, 0, xdim, ydim, DeltaMapper.getPixel(
					ResizeMapper.resize(ch[0], intermediate_xdim, xdim, ydim),
					ResizeMapper.resize(ch[1], intermediate_xdim, xdim, ydim),
					ResizeMapper.resize(ch[2], intermediate_xdim, xdim, ydim),
					xdim, pixel_shift), 0, xdim);
			}
			System.out.println("RGB assembled in " + ((System.nanoTime()-start)/1_000_000) + " ms.");
			decoded_image = image;
			SwingUtilities.invokeLater(() -> showImage());
		}
		catch (Exception e) { System.out.println(e.toString()); }
	}

	private static int[] readTable(DataInputStream in) throws IOException
	{
		int tl = in.readShort();
		int[] tbl = new int[tl];
		int max = Byte.MAX_VALUE * 2 + 1;
		if (tl <= max) for (int k = 0; k < tl; k++) { tbl[k] = in.readByte(); if (tbl[k] < 0) tbl[k] = max + 1 + tbl[k]; }
		else           for (int k = 0; k < tl; k++) tbl[k] = in.readShort();
		return tbl;
	}

	private void buildViewer(String filename)
	{
		Dimension sc = Toolkit.getDefaultToolkit().getScreenSize();
		int sw = (int)sc.getWidth(), sh = (int)sc.getHeight();
		fit_scale = Math.min(1.0, Math.min((double)(sw*70/100-40)/xdim, (double)(sh*70/100-80)/ydim));
		zoom_scale = fit_scale;
		image_canvas = new ImageCanvas();
		image_canvas.setPreferredSize(new Dimension(Math.max(1,(int)(xdim*zoom_scale)), Math.max(1,(int)(ydim*zoom_scale))));
		scroll_pane = new JScrollPane(image_canvas, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
		scroll_pane.getVerticalScrollBar().setUnitIncrement(16);
		scroll_pane.getHorizontalScrollBar().setUnitIncrement(16);
		scroll_pane.addMouseWheelListener(e -> {
			if (e.isControlDown()) {
				JViewport vp=scroll_pane.getViewport(); Point vp_pos=vp.getViewPosition(); Point mp=e.getPoint();
				int mcx=mp.x+vp_pos.x, mcy=mp.y+vp_pos.y; double old=zoom_scale;
				zoom_scale=(e.getWheelRotation()<0)?Math.min(ZOOM_MAX,zoom_scale*ZOOM_FACTOR):Math.max(ZOOM_MIN,zoom_scale/ZOOM_FACTOR);
				if(zoom_scale==old)return; updateDisplayImage();
				image_canvas.setPreferredSize(new Dimension((int)(xdim*zoom_scale),(int)(ydim*zoom_scale)));
				image_canvas.revalidate(); image_canvas.repaint();
				double r=zoom_scale/old; vp.setViewPosition(new Point(Math.max(0,(int)(mcx*r)-mp.x),Math.max(0,(int)(mcy*r)-mp.y)));
				updateTitle();
			} else scroll_pane.dispatchEvent(e);
		});
		frame = new JFrame("Simple Reader  [decoding\u2026]");
		frame.addWindowListener(new WindowAdapter() { public void windowClosing(WindowEvent e) { System.exit(0); }});
		JMenuBar mb = new JMenuBar();
		JMenu vm = new JMenu("View");
		JMenuItem zi=new JMenuItem("Zoom In"); zi.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_EQUALS,InputEvent.CTRL_DOWN_MASK)); zi.addActionListener(e->zoomBy(ZOOM_FACTOR)); vm.add(zi);
		JMenuItem zo=new JMenuItem("Zoom Out"); zo.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_MINUS,InputEvent.CTRL_DOWN_MASK)); zo.addActionListener(e->zoomBy(1.0/ZOOM_FACTOR)); vm.add(zo);
		mb.add(vm);
		frame.setJMenuBar(mb);
		frame.getContentPane().add(scroll_pane, BorderLayout.CENTER);
		int display_w=(int)(xdim*zoom_scale), display_h=(int)(ydim*zoom_scale);
		frame.pack();
		int ow=frame.getWidth()-scroll_pane.getViewport().getWidth(), oh=frame.getHeight()-scroll_pane.getViewport().getHeight();
		frame.setSize(display_w+ow, display_h+oh);
		frame.setLocation(5, 5);
		frame.setVisible(true);
	}

	private void showImage()
	{
		Dimension v = scroll_pane.getViewport().getSize();
		fit_scale = Math.min(1.0, Math.min(v.width>0?(double)v.width/xdim:1.0, v.height>0?(double)v.height/ydim:1.0));
		zoom_scale = fit_scale;
		updateDisplayImage();
		image_canvas.setPreferredSize(new Dimension((int)(xdim*zoom_scale),(int)(ydim*zoom_scale)));
		image_canvas.revalidate(); image_canvas.repaint(); updateTitle();
	}

	private void zoomBy(double factor)
	{
		double ns=Math.max(ZOOM_MIN,Math.min(ZOOM_MAX,zoom_scale*factor)); if(ns==zoom_scale)return;
		JViewport vp=scroll_pane.getViewport(); Point p=vp.getViewPosition(); Dimension vs=vp.getSize();
		double cx=p.x+vs.width/2.0, cy=p.y+vs.height/2.0, r=ns/zoom_scale; zoom_scale=ns;
		updateDisplayImage();
		image_canvas.setPreferredSize(new Dimension((int)(xdim*zoom_scale),(int)(ydim*zoom_scale)));
		image_canvas.revalidate(); image_canvas.repaint();
		vp.setViewPosition(new Point(Math.max(0,(int)(cx*r-vs.width/2.0)), Math.max(0,(int)(cy*r-vs.height/2.0))));
		updateTitle();
	}

	private void updateDisplayImage()
	{
		if (decoded_image == null) return;
		if (zoom_scale == 1.0) { display_image = decoded_image; return; }
		int w=Math.max(1,(int)(xdim*zoom_scale)), h=Math.max(1,(int)(ydim*zoom_scale));
		AffineTransform t = new AffineTransform(); t.scale(zoom_scale, zoom_scale);
		display_image = new AffineTransformOp(t, AffineTransformOp.TYPE_BILINEAR).filter(decoded_image, new BufferedImage(w, h, decoded_image.getType()));
	}

	private void updateTitle()
	{
		if (frame != null) frame.setTitle("Simple Reader  [" + (decoded_image==null ? "decoding\u2026" : (int)Math.round(zoom_scale*100)+"%") + "]");
	}

	class ImageCanvas extends JPanel
	{
		public ImageCanvas() { setOpaque(true); }
		@Override public Dimension getPreferredSize() { return display_image!=null ? new Dimension(display_image.getWidth(),display_image.getHeight()) : new Dimension(Math.max(1,(int)(xdim*zoom_scale)),Math.max(1,(int)(ydim*zoom_scale))); }
		@Override protected synchronized void paintComponent(Graphics g) { super.paintComponent(g); if(display_image!=null) g.drawImage(display_image,0,0,this); }
	}

	class Resizer implements Runnable
	{
		int[] src; int xdim, nx, ny, i;
		Resizer(int[] src, int xdim, int nx, int ny, int i) { this.src=src; this.xdim=xdim; this.nx=nx; this.ny=ny; this.i=i; }
		public void run() { resize_array[i] = ResizeMapper.resize(src, xdim, nx, ny); }
	}

	class Decompressor implements Runnable
	{
		int i; Decompressor(int i) { this.i=i; }
		public void run()
		{
			try {
				int[] channel_id = DeltaMapper.getChannels(set_id);
				int cur_xdim, cur_ydim, size;
				if (pixel_quant == 0) { cur_xdim=xdim; cur_ydim=ydim; }
				else { double f=pixel_quant/10.0; intermediate_xdim=xdim-(int)(f*(xdim/2-2)); intermediate_ydim=ydim-(int)(f*(ydim/2-2)); cur_xdim=intermediate_xdim; cur_ydim=intermediate_ydim; }
				size = cur_xdim * cur_ydim;

				// Entropy decode -> payload bytes
				byte[] payload;
				if (entropy_type == 0)
				{
					payload = (byte[])string_list.get(i);
				}
				else if (entropy_type == 1)
				{
					int[]  rt   = huff_rank_list.get(i);
					byte[] cl   = huff_cl_list.get(i);
					byte[] pb   = huff_pay_list.get(i);
					int    hbl  = huff_bl[i];
					int    pmin = huff_pay_min[i];
					int[]  hc   = CodeMapper.getCanonicalCode(cl);
					int sl = compressed_length[i]/8; if(compressed_length[i]%8!=0)sl++; sl++;
					int[] dec = new int[sl];
					CodeMapper.unpackCode(pb, rt, hc, cl, hbl, dec);
					payload = new byte[sl];
					for (int k = 0; k < sl; k++) payload[k] = (byte)(dec[k] + pmin);
				}
				else
				{
					// Arithmetic — always String payload
					byte[] lb = (byte[])string_list.get(i);
					int expected = ((lb[0]&0xFF)<<24)|((lb[1]&0xFF)<<16)|((lb[2]&0xFF)<<8)|(lb[3]&0xFF);
					int n_segs = n_segs_arr[i];
					int seg_len = expected/n_segs, odd_len = seg_len+expected%n_segs;
					int[][] freqs = freq_list.get(i);
					byte[][] segs = new byte[n_segs][];
					for(int m=0;m<n_segs;m++) segs[m]=new byte[m<n_segs-1?seg_len:odd_len];
					if (entropy_type == 2)
					{
						java.math.BigInteger[][] offsets = offset_list.get(i);
						for(int m=0;m<n_segs;m++) segs[m]=ArithmeticMapper.getArithmeticValues(offsets[m],freqs[m],segs[m].length);
					}
					else
					{
						byte[][] encs = fast_enc_list.get(i);
						for(int m=0;m<n_segs;m++) segs[m]=ArithmeticMapper.getArithmeticValuesFast(encs[m],freqs[m],segs[m].length);
					}
					byte[] buf = new byte[expected]; int pos=0;
					for(int m=0;m<n_segs;m++){System.arraycopy(segs[m],0,buf,pos,segs[m].length);pos+=segs[m].length;}
					payload = buf;
				}

				// String decode -> delta values
				int[]  tbl   = (int[])table_list.get(i);
				byte[] str   = StringMapper.decompressStrings(payload);
				int[]  delta = StringMapper.unpackStrings(str, tbl, size, StringMapper.getBitlength(str));
				for (int j = 1; j < delta.length; j++) delta[j] += delta_min[i];

				// Delta -> channel values
				int[] cur_ch;
				if(delta_type==0)cur_ch=DeltaMapper.getValuesFromHorizontalDeltas(delta,cur_xdim,cur_ydim,init[i]);
				else if(delta_type==1)cur_ch=DeltaMapper.getValuesFromVerticalDeltas(delta,cur_xdim,cur_ydim,init[i]);
				else if(delta_type==2)cur_ch=DeltaMapper.getValuesFromAverageDeltas(delta,cur_xdim,cur_ydim,init[i]);
				else if(delta_type==3)cur_ch=DeltaMapper.getValuesFromMedDeltas(delta,cur_xdim,cur_ydim,init[i]);
				else if(delta_type==4)cur_ch=DeltaMapper.getValuesFromAdaptiveDeltas(delta,cur_xdim,cur_ydim,init[i]);
				else if(delta_type==5)cur_ch=DeltaMapper.getValuesFromDirectionalDeltas(delta,cur_xdim,cur_ydim,init[i]);
				else if(delta_type==6)cur_ch=DeltaMapper.getValuesFromMixedDeltas(delta,cur_xdim,cur_ydim,init[i],(byte[])map_list.get(i));
				else if(delta_type==7)cur_ch=DeltaMapper.getValuesFromMixedDeltas2(delta,cur_xdim,cur_ydim,init[i],(byte[])map_list.get(i));
				else if(delta_type==8)cur_ch=DeltaMapper.getValuesFromMixedDeltas4(delta,cur_xdim,cur_ydim,init[i],(byte[])map_list.get(i));
				else if(delta_type==9)cur_ch=DeltaMapper.getValuesFromMixedDeltas16Rows(delta,cur_xdim,cur_ydim,init[i],(byte[])map_list.get(i));
				else if(delta_type==10)cur_ch=DeltaMapper.getValuesFromIdealDeltas8(delta,cur_xdim,cur_ydim,init[i],(byte[])map_list.get(i));
				else cur_ch=DeltaMapper.getValuesFromIdealDeltas16(delta,cur_xdim,cur_ydim,init[i],(byte[])map_list.get(i));

				if (channel_id[i] > 2) for (int j = 0; j < cur_ch.length; j++) cur_ch[j] += min[i];
				channel_array[i] = cur_ch;
			}
			catch (Exception e) { System.out.println("Decompressor " + i + ": " + e); }
		}
	}
}
