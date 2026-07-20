import java.awt.*;
import java.awt.image.*;
import java.io.*;
import java.math.*;
import java.awt.event.*;
import java.awt.geom.AffineTransform;
import java.util.*;
import java.util.zip.*;
import javax.imageio.*;
import javax.swing.*;
import javax.swing.event.*;

public class SimpleWriter
{
	BufferedImage original_image, working_image, display_image;
	ImageCanvas image_canvas;
	JScrollPane scroll_pane;
	int image_xdim, image_ydim;
	int screen_xdim, screen_ydim;
	JMenuItem apply_item;
	String filename;
	int[] pixel;

	int pixel_quant = 4;
	int pixel_shift = 3;
	int correction  = 0;
	int min_set_id  = 0;
	int delta_type  = 5;
	int deflate_type = 0;

	double zoom_scale = 1.0;
	double fit_scale  = 1.0;

	int[] set_sum, channel_sum;
	String[] set_string, delta_type_string, channel_string;
	int[] channel_init, channel_min, channel_delta_min;
	int[] channel_length, channel_compressed_length;
	byte[] channel_iterations;

	long file_length;
	double file_compression_rate;
	JRadioButtonMenuItem[] delta_button;
	JRadioButtonMenuItem[] entropy_button;
	int entropy_type   = 0;
	int pixel_segment  = 0;
	JFrame frame = null;

	ArrayList<Object> channel_list, table_list, string_list, map_list, delta_list;
	boolean initialized   = false;
	boolean print_ranking = false;

	static int openWindowCount = 0;
	static final double ZOOM_FACTOR = 1.25, ZOOM_MIN = 0.05, ZOOM_MAX = 32.0;

	public static void main(String[] args)
	{
		if (args.length == 1) { new SimpleWriter(args[0]); }
		else
		{
			FileDialog fd = new FileDialog((java.awt.Frame)null, "Open Image", FileDialog.LOAD);
			fd.setVisible(true);
			if (fd.getFile() != null) new SimpleWriter(new File(fd.getDirectory(), fd.getFile()).getPath());
			else System.exit(0);
		}
	}

	public SimpleWriter(String _filename)
	{
		filename = _filename;
		try
		{
			File file = new File(filename);
			file_length = file.length();
			original_image = ImageIO.read(file);
			int raster_type = original_image.getType();
			image_xdim = original_image.getWidth();
			image_ydim = original_image.getHeight();

			channel_list = new ArrayList<Object>(); table_list = new ArrayList<Object>();
			string_list  = new ArrayList<Object>(); map_list   = new ArrayList<Object>();
			delta_list   = new ArrayList<Object>();

			channel_string = new String[]{"blue","green","red","blue-green","red-green","red-blue"};
			set_sum    = new int[10];
			set_string = new String[]{"blue, green, red","blue, red, red-green","blue, red, blue-green",
				"blue, blue-green, red-green","blue, blue-green, red-blue","green, red, blue-green",
				"red, blue-green, red-green","green, blue-green, red-green",
				"green, red-green, red-blue","red, red-green, red-blue"};
			delta_type_string = new String[]{"horizontal","vertical","average","med","gradient",
				"scanline (1)","scanline (2)","scanline (3)","frame map"};
			channel_init = new int[6]; channel_min = new int[6]; channel_delta_min = new int[6];
			channel_sum  = new int[6]; channel_length = new int[6];
			channel_compressed_length = new int[6]; channel_iterations = new byte[3];

			if (raster_type == BufferedImage.TYPE_3BYTE_BGR)
			{
				pixel = new int[image_xdim * image_ydim];
				PixelGrabber pg = new PixelGrabber(original_image, 0, 0, image_xdim, image_ydim, pixel, 0, image_xdim);
				try { pg.grabPixels(); } catch (InterruptedException e) { System.err.println(e); }
				int[] blue = new int[image_xdim*image_ydim], green = new int[image_xdim*image_ydim], red = new int[image_xdim*image_ydim];
				for (int i = 0; i < pixel.length; i++) { blue[i]=(pixel[i]>>16)&0xff; green[i]=(pixel[i]>>8)&0xff; red[i]=pixel[i]&0xff; }
				channel_list.add(blue); channel_list.add(green); channel_list.add(red);
				working_image = new BufferedImage(image_xdim, image_ydim, BufferedImage.TYPE_INT_RGB);
				for (int i=0;i<image_xdim;i++) for (int j=0;j<image_ydim;j++) working_image.setRGB(i,j,pixel[j*image_xdim+i]);

				Dimension sc = Toolkit.getDefaultToolkit().getScreenSize();
				screen_xdim=(int)sc.getWidth(); screen_ydim=(int)sc.getHeight();
				fit_scale = Math.min(1.0, Math.min((double)(screen_xdim*70/100-40)/image_xdim, (double)(screen_ydim*70/100-80)/image_ydim));
				zoom_scale = fit_scale;

				image_canvas = new ImageCanvas();
				scroll_pane = new JScrollPane(image_canvas, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
				scroll_pane.getVerticalScrollBar().setUnitIncrement(16);
				scroll_pane.getHorizontalScrollBar().setUnitIncrement(16);
				scroll_pane.addMouseWheelListener(e -> {
					if (e.isControlDown()) {
						JViewport vp=scroll_pane.getViewport(); Point vp_pos=vp.getViewPosition(); Point mp=e.getPoint();
						int mcx=mp.x+vp_pos.x, mcy=mp.y+vp_pos.y; double old=zoom_scale;
						zoom_scale=(e.getWheelRotation()<0)?Math.min(ZOOM_MAX,zoom_scale*ZOOM_FACTOR):Math.max(ZOOM_MIN,zoom_scale/ZOOM_FACTOR);
						if(zoom_scale==old)return; updateDisplayImage();
						image_canvas.setPreferredSize(new Dimension((int)(image_xdim*zoom_scale),(int)(image_ydim*zoom_scale)));
						image_canvas.revalidate(); image_canvas.repaint();
						double r=zoom_scale/old; vp.setViewPosition(new Point(Math.max(0,(int)(mcx*r)-mp.x),Math.max(0,(int)(mcy*r)-mp.y)));
						updateTitle();
					} else scroll_pane.dispatchEvent(e);
				});

				frame = new JFrame("Simple Writer " + filename);
				openWindowCount++;
				frame.addWindowListener(new WindowAdapter() { public void windowClosing(WindowEvent e) { frame.dispose(); if (--openWindowCount == 0) System.exit(0); } });
				frame.getContentPane().add(scroll_pane, BorderLayout.CENTER);

				JMenuBar menu_bar = new JMenuBar();
				JMenu file_menu = new JMenu("File");
				JMenuItem open_item = new JMenuItem("Open...");
				open_item.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_O, InputEvent.CTRL_DOWN_MASK));
				open_item.addActionListener(e -> {
					FileDialog fd = new FileDialog(frame, "Open Image", FileDialog.LOAD);
					fd.setVisible(true);
					if (fd.getFile() != null) new SimpleWriter(new File(fd.getDirectory(), fd.getFile()).getPath());
				});
				file_menu.add(open_item);
				file_menu.addSeparator();
				apply_item = new JMenuItem("Apply");
				apply_item.addActionListener(new ApplyHandler());
				file_menu.add(apply_item);
				JMenuItem save_item = new JMenuItem("Save");
				save_item.addActionListener(new SaveHandler());
				file_menu.add(save_item);

				JMenu view_menu = new JMenu("View");
				JMenuItem zi=new JMenuItem("Zoom In"); zi.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_EQUALS,InputEvent.CTRL_DOWN_MASK)); zi.addActionListener(e->zoomBy(ZOOM_FACTOR)); view_menu.add(zi);
				JMenuItem zo=new JMenuItem("Zoom Out"); zo.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_MINUS,InputEvent.CTRL_DOWN_MASK)); zo.addActionListener(e->zoomBy(1.0/ZOOM_FACTOR)); view_menu.add(zo);
				JMenuItem zf=new JMenuItem("Fit"); zf.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_0,InputEvent.CTRL_DOWN_MASK)); zf.addActionListener(e->{Dimension v=scroll_pane.getViewport().getSize();zoom_scale=Math.min((double)v.width/image_xdim,(double)v.height/image_ydim);updateDisplayImage();image_canvas.setPreferredSize(new Dimension((int)(image_xdim*zoom_scale),(int)(image_ydim*zoom_scale)));image_canvas.revalidate();image_canvas.repaint();updateTitle();}); view_menu.add(zf);
				JMenuItem za=new JMenuItem("100%"); za.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_1,InputEvent.CTRL_DOWN_MASK)); za.addActionListener(e->{zoom_scale=1.0;updateDisplayImage();image_canvas.setPreferredSize(new Dimension(image_xdim,image_ydim));image_canvas.revalidate();image_canvas.repaint();updateTitle();}); view_menu.add(za);

				JMenu quant_menu = new JMenu("Quantization");
				quant_menu.add(makeSlider("Pixel Resolution", 0, 10, pixel_quant, v -> { pixel_quant = v; print_ranking=true; apply_item.doClick(); }));
				quant_menu.add(makeSlider("Color Resolution", 0, 7,  pixel_shift, v -> { pixel_shift = v; print_ranking=true; apply_item.doClick(); }));
				quant_menu.add(makeSlider("Error Correction", 0, 10, correction,  v -> { correction  = v; print_ranking=true; apply_item.doClick(); }));
				quant_menu.add(makeSlider("Compress Threshold %", 0, 10, 1, v -> { StringMapper.compress_threshold = v / 10.0; print_ranking=true; apply_item.doClick(); }));

				JMenu delta_menu = new JMenu("Delta");
				String[] dnames = {"H","V","Average","Med","Adaptive","Directional","Scanline 1","Scanline 2","Scanline 3","Scanline 4","Map 1","Map 2"};
				delta_button = new JRadioButtonMenuItem[12];
				ButtonGroup dg = new ButtonGroup();
				for (int i=0;i<12;i++) {
					delta_button[i]=new JRadioButtonMenuItem(dnames[i]); dg.add(delta_button[i]); delta_menu.add(delta_button[i]);
					final int dt=i; delta_button[i].addActionListener(e->{ if(delta_type!=dt){delta_type=dt;print_ranking=true;apply_item.doClick();} });
				}
				delta_button[delta_type].setSelected(true);

				JMenu entropy_menu = new JMenu("Entropy");
				entropy_button = new JRadioButtonMenuItem[3];
				entropy_button[0] = new JRadioButtonMenuItem("LZ77");
				entropy_button[1] = new JRadioButtonMenuItem("Huffman");
				entropy_button[2] = new JRadioButtonMenuItem("Arithmetic");
				ButtonGroup eg = new ButtonGroup();
				for(int i=0;i<3;i++){eg.add(entropy_button[i]);entropy_menu.add(entropy_button[i]);}
				entropy_button[0].setSelected(true);
				entropy_button[0].addActionListener(e->{ entropy_type=0; });
				entropy_button[1].addActionListener(e->{ entropy_type=1; });

				// Arithmetic dialog: Fast/Slow + Segment Length
				{ JDialog arith_dlg = new JDialog(frame,"Arithmetic");
				  JRadioButtonMenuItem fast_btn = new JRadioButtonMenuItem("Fast");
				  JRadioButtonMenuItem slow_btn = new JRadioButtonMenuItem("Slow");
				  ButtonGroup ag = new ButtonGroup(); ag.add(fast_btn); ag.add(slow_btn);
				  fast_btn.setSelected(true);
				  fast_btn.addActionListener(e2->entropy_type=3);
				  slow_btn.addActionListener(e2->entropy_type=2);
				  JSlider seg_sl = new JSlider(0,10,pixel_segment);
				  seg_sl.setMajorTickSpacing(1); seg_sl.setPaintTicks(true); seg_sl.setSnapToTicks(true);
				  JTextField seg_tf = new JTextField(" 0 ",3);
				  seg_sl.addChangeListener(e2->{ seg_tf.setText(" "+seg_sl.getValue()+" "); pixel_segment=seg_sl.getValue(); });
				  JPanel rp=new JPanel(); rp.add(fast_btn); rp.add(slow_btn);
				  JPanel sp=new JPanel(new BorderLayout(4,4));
				  sp.add(new JLabel("Segment Length:"),BorderLayout.WEST);
				  sp.add(seg_sl,BorderLayout.CENTER); sp.add(seg_tf,BorderLayout.EAST);
				  JPanel ap=new JPanel(new java.awt.GridLayout(2,1,4,4)); ap.add(rp); ap.add(sp);
				  arith_dlg.add(ap);
				  entropy_button[2].addActionListener(e->{
				    if(entropy_type!=2&&entropy_type!=3) entropy_type=3;
				    fast_btn.setSelected(entropy_type==3); slow_btn.setSelected(entropy_type==2);
				    Point loc=frame.getLocation();
				    arith_dlg.setLocation((int)loc.getX(),(int)loc.getY()-80);
				    arith_dlg.pack(); arith_dlg.setVisible(true);
				  }); }

				menu_bar.add(file_menu); menu_bar.add(view_menu); menu_bar.add(quant_menu); menu_bar.add(delta_menu); menu_bar.add(entropy_menu);
				frame.setJMenuBar(menu_bar);
				display_image = original_image;
				image_canvas.setPreferredSize(new Dimension((int)(image_xdim*zoom_scale),(int)(image_ydim*zoom_scale)));
				updateTitle();
				int display_w = (int)(image_xdim * zoom_scale);
				int display_h = (int)(image_ydim * zoom_scale);
				frame.pack();
				int overhead_w = frame.getWidth()  - scroll_pane.getViewport().getWidth();
				int overhead_h = frame.getHeight() - scroll_pane.getViewport().getHeight();
				int dialog_clearance = 120;
				int avail_h = screen_ydim - dialog_clearance;
				int fw = display_w + overhead_w;
				int fh = Math.min(display_h + overhead_h, avail_h);
				frame.setSize(fw, fh);
				frame.setLocation(5, dialog_clearance);
				frame.setVisible(true);
				SwingUtilities.invokeLater(()->showInitialImage());
			}
		} catch (Exception e) { System.out.println(e.getMessage()); System.exit(1); }
	}

	private JMenuItem makeSlider(String title, int lo, int hi, int init, java.util.function.IntConsumer onChange)
	{
		JMenuItem item = new JMenuItem(title);
		JDialog dialog = new JDialog(frame, title);
		JSlider slider = new JSlider(lo, hi, init);
		slider.setMajorTickSpacing(1); slider.setPaintTicks(true); slider.setSnapToTicks(true);
		JTextField field = new JTextField(3); field.setText(" "+init+" ");
		slider.addChangeListener(e -> { int v=slider.getValue(); field.setText(" "+v+" "); if(!slider.getValueIsAdjusting()) onChange.accept(v); });
		JPanel p = new JPanel(new BorderLayout()); p.add(slider,BorderLayout.CENTER); p.add(field,BorderLayout.EAST); dialog.add(p);
		item.addActionListener(e -> { Point loc=frame.getLocation(); dialog.setLocation((int)loc.getX(),(int)loc.getY()-60); dialog.pack(); dialog.setVisible(true); });
		return item;
	}

	private void showInitialImage()
	{
		Dimension v=scroll_pane.getViewport().getSize();
		fit_scale=Math.min(1.0,Math.min(v.width>0?(double)v.width/image_xdim:1.0,v.height>0?(double)v.height/image_ydim:1.0));
		zoom_scale=fit_scale; updateDisplayImage();
		image_canvas.setPreferredSize(new Dimension((int)(image_xdim*zoom_scale),(int)(image_ydim*zoom_scale)));
		image_canvas.revalidate(); image_canvas.repaint(); updateTitle();
		apply_item.doClick();
		new javax.swing.SwingWorker<Void,Void>() {
			@Override protected Void doInBackground() { init(); return null; }
			@Override protected void done() {
				for(int k=0;k<12;k++) if(delta_button[k]!=null) delta_button[k].setSelected(k==delta_type);
				apply_item.doClick();
			}
		}.execute();
	}

	private void init()
	{
		// Build quantized channel list (same as ApplyHandler)
		ArrayList<int[]> qcl=new ArrayList<int[]>();
		int nx=image_xdim, ny=image_ydim;
		if(pixel_quant!=0){double f=pixel_quant/10.0;nx=image_xdim-(int)(f*(image_xdim/2-2));ny=image_ydim-(int)(f*(image_ydim/2-2));}
		for(int i=0;i<3;i++){
			int[] ch=(int[])channel_list.get(i);
			if(pixel_quant==0) qcl.add(pixel_shift==0?ch:DeltaMapper.shift(ch,-pixel_shift));
			else{int[] r=ResizeMapper.resize(ch,image_xdim,nx,ny);qcl.add(pixel_shift==0?r:DeltaMapper.shift(r,-pixel_shift));}
		}
		qcl.add(DeltaMapper.getDifference(qcl.get(0),qcl.get(1)));
		qcl.add(DeltaMapper.getDifference(qcl.get(2),qcl.get(1)));
		qcl.add(DeltaMapper.getDifference(qcl.get(2),qcl.get(0)));
		int[] cs=new int[6];
		String[] ch_name={"blue","green","red","blue-green","red-green","red-blue"};
		for(int i=0;i<6;i++){
			int[] qc=qcl.get(i); int min=256; for(int v:qc)if(v<min)min=v;
			if(i>2)for(int k=0;k<qc.length;k++)qc[k]-=min;
			qcl.set(i,qc);
			cs[i]=(int)Math.floor(CodeMapper.getShannonLimit(DeltaMapper.getIdealFrequency(qc,nx,ny)));
		}

		// Rank channel sets
		int[] ss=new int[10];
		ss[0]=cs[0]+cs[1]+cs[2]; ss[1]=cs[0]+cs[4]+cs[2]; ss[2]=cs[0]+cs[3]+cs[2];
		ss[3]=cs[0]+cs[1]+cs[4]; ss[4]=cs[0]+cs[3]+cs[5]; ss[5]=cs[3]+cs[1]+cs[2];
		ss[6]=cs[3]+cs[4]+cs[2]; ss[7]=cs[3]+cs[1]+cs[4]; ss[8]=cs[5]+cs[1]+cs[4]; ss[9]=cs[5]+cs[4]+cs[2];
		int[][] set_ch={{0,1,2},{0,4,2},{0,3,2},{0,1,4},{0,3,5},{3,1,2},{3,4,2},{3,1,4},{5,1,4},{5,4,2}};
		Integer[] so=new Integer[10]; for(int i=0;i<10;i++)so[i]=i;
		java.util.Arrays.sort(so,(a,b)->ss[a]-ss[b]);

		// Find best set and its channels
		int best_set=so[0];
		int[] cid=DeltaMapper.getChannels(best_set);

		// Rank delta types via collectData
		print_ranking = true;
		collectData(qcl, cid, nx, ny, true);

		// Print channel set rankings
		System.out.println("\n=== "+filename+" ===");
		String[][] set_disp={
			{"blue",  "green",       "red"},
			{"blue",  "red",         "red   -  green"},
			{"blue",  "red",         "blue  -  green"},
			{"blue",  "blue  -  green","red   -  green"},
			{"blue",  "blue  -  green","red   -  blue"},
			{"green", "red",         "blue  -  green"},
			{"red",   "blue  -  green","red   -  green"},
			{"green", "blue  -  green","red   -  green"},
			{"green", "red   -  green","red   -  blue"},
			{"red",   "red   -  green","red   -  blue"}
		};
		System.out.println("Channel sets (ranked):");
		for(int r=0;r<10;r++){
			int idx=so[r]; int[] c=DeltaMapper.getChannels(idx);
			System.out.println(String.format("  %2d. %-8s%-20s%-14s %10d %10d %10d %12d",
				r+1, set_disp[idx][0], set_disp[idx][1], set_disp[idx][2],
				cs[c[0]], cs[c[1]], cs[c[2]], ss[idx]));
		}
		System.out.println();
	}

	private void zoomBy(double factor)
	{
		double ns=Math.max(ZOOM_MIN,Math.min(ZOOM_MAX,zoom_scale*factor)); if(ns==zoom_scale)return;
		JViewport vp=scroll_pane.getViewport(); Point vp_pos=vp.getViewPosition(); Dimension vs=vp.getSize();
		double cx=vp_pos.x+vs.width/2.0, cy=vp_pos.y+vs.height/2.0, r=ns/zoom_scale; zoom_scale=ns;
		updateDisplayImage(); image_canvas.setPreferredSize(new Dimension((int)(image_xdim*zoom_scale),(int)(image_ydim*zoom_scale)));
		image_canvas.revalidate(); image_canvas.repaint();
		vp.setViewPosition(new Point(Math.max(0,(int)(cx*r-vs.width/2.0)),Math.max(0,(int)(cy*r-vs.height/2.0)))); updateTitle();
	}

	private void updateDisplayImage()
	{
		BufferedImage src=(working_image!=null&&initialized)?working_image:original_image;
		if(zoom_scale==1.0){display_image=src;return;}
		int w=Math.max(1,(int)(image_xdim*zoom_scale)),h=Math.max(1,(int)(image_ydim*zoom_scale));
		AffineTransform t=new AffineTransform(); t.scale(zoom_scale,zoom_scale);
		display_image=new AffineTransformOp(t,AffineTransformOp.TYPE_BILINEAR).filter(src,new BufferedImage(w,h,src.getType()));
	}

	private void updateTitle() { frame.setTitle("Simple Writer "+filename+"  ["+(int)Math.round(zoom_scale*100)+"%]"); }

	class ImageCanvas extends JPanel
	{
		public ImageCanvas(){setOpaque(true);}
		@Override public Dimension getPreferredSize(){return display_image!=null?new Dimension(display_image.getWidth(),display_image.getHeight()):new Dimension(Math.max(1,(int)(image_xdim*zoom_scale)),Math.max(1,(int)(image_ydim*zoom_scale)));}
		@Override protected synchronized void paintComponent(Graphics g){super.paintComponent(g);if(display_image!=null)g.drawImage(display_image,0,0,this);}
	}

	private static void writeTable(DataOutputStream out, int[] table) throws IOException
	{
		out.writeShort(table.length);
		int max=Byte.MAX_VALUE*2+1;
		if(table.length<=max) for(int v:table)out.writeByte(v); else for(int v:table)out.writeShort(v);
	}

	class ApplyHandler implements ActionListener
	{
		public void actionPerformed(ActionEvent event)
		{
			ArrayList<int[]> qcl=new ArrayList<int[]>(), dqcl=new ArrayList<int[]>();
			int new_xdim=image_xdim, new_ydim=image_ydim;
			if(pixel_quant!=0){double f=pixel_quant/10.0;new_xdim=image_xdim-(int)(f*(image_xdim/2-2));new_ydim=image_ydim-(int)(f*(image_ydim/2-2));}

			for(int i=0;i<3;i++){
				int[] ch=(int[])channel_list.get(i);
				if(pixel_quant==0) qcl.add(pixel_shift==0?ch:DeltaMapper.shift(ch,-pixel_shift));
				else{int[] r=ResizeMapper.resize(ch,image_xdim,new_xdim,new_ydim);qcl.add(pixel_shift==0?r:DeltaMapper.shift(r,-pixel_shift));}
			}

			qcl.add(DeltaMapper.getDifference(qcl.get(0),qcl.get(1)));
			qcl.add(DeltaMapper.getDifference(qcl.get(2),qcl.get(1)));
			qcl.add(DeltaMapper.getDifference(qcl.get(2),qcl.get(0)));

			for(int i=0;i<6;i++){
				int[] qc=qcl.get(i); int min=256;
				for(int v:qc)if(v<min)min=v; channel_min[i]=min;
				if(i>2)for(int k=0;k<qc.length;k++)qc[k]-=min;
				channel_init[i]=qc[0]; qcl.set(i,qc);
				channel_sum[i]=(int)Math.floor(CodeMapper.getShannonLimit(DeltaMapper.getIdealFrequency(qc,new_xdim,new_ydim)));
			}

			set_sum[0]=channel_sum[0]+channel_sum[1]+channel_sum[2]; set_sum[1]=channel_sum[0]+channel_sum[4]+channel_sum[2];
			set_sum[2]=channel_sum[0]+channel_sum[3]+channel_sum[2]; set_sum[3]=channel_sum[0]+channel_sum[1]+channel_sum[4];
			set_sum[4]=channel_sum[0]+channel_sum[3]+channel_sum[5]; set_sum[5]=channel_sum[3]+channel_sum[1]+channel_sum[2];
			set_sum[6]=channel_sum[3]+channel_sum[4]+channel_sum[2]; set_sum[7]=channel_sum[3]+channel_sum[1]+channel_sum[4];
			set_sum[8]=channel_sum[5]+channel_sum[1]+channel_sum[4]; set_sum[9]=channel_sum[5]+channel_sum[4]+channel_sum[2];

			int min_sum=Integer.MAX_VALUE,min_idx=0;
			for(int i=0;i<10;i++)if(set_sum[i]<min_sum){min_sum=set_sum[i];min_idx=i;} min_set_id=min_idx;
			file_compression_rate=(double)file_length/(image_xdim*image_ydim*3);

			int[] channel_id=DeltaMapper.getChannels(min_set_id);
			table_list.clear(); string_list.clear(); map_list.clear(); delta_list.clear();

			for(int i=0;i<3;i++){
				int j=channel_id[i]; int[] qc=qcl.get(j);
				ArrayList<Object> result=new ArrayList<Object>();
				if(delta_type==0)result=DeltaMapper.getHorizontalDeltasFromValues(qc,new_xdim,new_ydim);
				else if(delta_type==1)result=DeltaMapper.getVerticalDeltasFromValues(qc,new_xdim,new_ydim);
				else if(delta_type==2)result=DeltaMapper.getAverageDeltasFromValues(qc,new_xdim,new_ydim);
				else if(delta_type==3)result=DeltaMapper.getMedDeltasFromValues(qc,new_xdim,new_ydim);
				else if(delta_type==4)result=DeltaMapper.getAdaptiveDeltasFromValues(qc,new_xdim,new_ydim);
				else if(delta_type==5)result=DeltaMapper.getDirectionalDeltasFromValues(qc,new_xdim,new_ydim);
				else if(delta_type==6){result=DeltaMapper.getMixedDeltasFromValues(qc,new_xdim,new_ydim);map_list.add((byte[])result.get(2));}
				else if(delta_type==7){result=DeltaMapper.getMixedDeltasFromValues2(qc,new_xdim,new_ydim);map_list.add((byte[])result.get(2));}
				else if(delta_type==8){result=DeltaMapper.getMixedDeltasFromValues4(qc,new_xdim,new_ydim);map_list.add((byte[])result.get(2));}
				else if(delta_type==9){result=DeltaMapper.getMixedDeltasFromValues16Rows(qc,new_xdim,new_ydim);map_list.add((byte[])result.get(2));}
				else if(delta_type==10){result=DeltaMapper.getIdealDeltasFromValues8(qc,new_xdim,new_ydim);map_list.add((byte[])result.get(2));}
				else{result=DeltaMapper.getIdealDeltasFromValues16(qc,new_xdim,new_ydim);map_list.add((byte[])result.get(2));}

				int[] delta=(int[])result.get(1);
				// Always use String with threshold-controlled compression
				ArrayList dsl=StringMapper.getStringList(delta,true);
				channel_delta_min[j]=(int)dsl.get(0); channel_length[j]=(int)dsl.get(1);
				table_list.add((int[])dsl.get(2)); string_list.add((byte[])dsl.get(3));
				channel_compressed_length[j]=StringMapper.getBitlength((byte[])dsl.get(3));
				channel_iterations[i]=StringMapper.getIterations((byte[])dsl.get(3));
				for(int k=1;k<delta.length;k++) delta[k]+=channel_delta_min[j];
			}

			for(int i=0;i<3;i++){
				int j=channel_id[i]; int[] delta=new int[new_xdim*new_ydim];
				int[] tbl=(int[])table_list.get(i);
				byte[] str=StringMapper.decompressStrings((byte[])string_list.get(i));
				delta=StringMapper.unpackStrings(str,tbl,new_xdim*new_ydim,channel_length[j]);
				for(int k=1;k<delta.length;k++) delta[k]+=channel_delta_min[j];

				int[] ch=new int[0];
				if(delta_type==0)ch=DeltaMapper.getValuesFromHorizontalDeltas(delta,new_xdim,new_ydim,channel_init[j]);
				else if(delta_type==1)ch=DeltaMapper.getValuesFromVerticalDeltas(delta,new_xdim,new_ydim,channel_init[j]);
				else if(delta_type==2)ch=DeltaMapper.getValuesFromAverageDeltas(delta,new_xdim,new_ydim,channel_init[j]);
				else if(delta_type==3)ch=DeltaMapper.getValuesFromMedDeltas(delta,new_xdim,new_ydim,channel_init[j]);
				else if(delta_type==4)ch=DeltaMapper.getValuesFromAdaptiveDeltas(delta,new_xdim,new_ydim,channel_init[j]);
				else if(delta_type==5)ch=DeltaMapper.getValuesFromDirectionalDeltas(delta,new_xdim,new_ydim,channel_init[j]);
				else if(delta_type==6)ch=DeltaMapper.getValuesFromMixedDeltas(delta,new_xdim,new_ydim,channel_init[j],(byte[])map_list.get(i));
				else if(delta_type==7)ch=DeltaMapper.getValuesFromMixedDeltas2(delta,new_xdim,new_ydim,channel_init[j],(byte[])map_list.get(i));
				else if(delta_type==8)ch=DeltaMapper.getValuesFromMixedDeltas4(delta,new_xdim,new_ydim,channel_init[j],(byte[])map_list.get(i));
				else if(delta_type==9)ch=DeltaMapper.getValuesFromMixedDeltas16Rows(delta,new_xdim,new_ydim,channel_init[j],(byte[])map_list.get(i));
				else if(delta_type==10)ch=DeltaMapper.getValuesFromIdealDeltas8(delta,new_xdim,new_ydim,channel_init[j],(byte[])map_list.get(i));
				else ch=DeltaMapper.getValuesFromIdealDeltas16(delta,new_xdim,new_ydim,channel_init[j],(byte[])map_list.get(i));

				if(j>2)for(int k=0;k<ch.length;k++)ch[k]+=channel_min[j];
				if(pixel_shift==0)dqcl.add(pixel_quant==0?ch:ResizeMapper.resize(ch,new_xdim,image_xdim,image_ydim));
				else{int[]s=DeltaMapper.shift(ch,pixel_shift);dqcl.add(pixel_quant==0?s:ResizeMapper.resize(s,new_xdim,image_xdim,image_ydim));}
			}

			int[] blue=new int[image_xdim*image_ydim],green=new int[image_xdim*image_ydim],red=new int[image_xdim*image_ydim];
			if(min_set_id==0){blue=dqcl.get(0);green=dqcl.get(1);red=dqcl.get(2);}
			else if(min_set_id==1){blue=dqcl.get(0);red=dqcl.get(1);green=DeltaMapper.getDifference(red,dqcl.get(2));}
			else if(min_set_id==2){blue=dqcl.get(0);red=dqcl.get(1);green=DeltaMapper.getDifference(blue,dqcl.get(2));}
			else if(min_set_id==3){blue=dqcl.get(0);green=DeltaMapper.getDifference(blue,dqcl.get(1));red=DeltaMapper.getSum(dqcl.get(2),green);}
			else if(min_set_id==4){blue=dqcl.get(0);green=DeltaMapper.getDifference(blue,dqcl.get(1));red=DeltaMapper.getSum(blue,dqcl.get(2));}
			else if(min_set_id==5){green=dqcl.get(0);red=dqcl.get(1);blue=DeltaMapper.getSum(dqcl.get(2),green);}
			else if(min_set_id==6){red=dqcl.get(0);int[]bg=dqcl.get(1);int[]rg=dqcl.get(2);for(int i=0;i<rg.length;i++)rg[i]=-rg[i];green=DeltaMapper.getSum(rg,red);blue=DeltaMapper.getSum(bg,green);}
			else if(min_set_id==7){green=dqcl.get(0);blue=DeltaMapper.getSum(green,dqcl.get(1));red=DeltaMapper.getSum(green,dqcl.get(2));}
			else if(min_set_id==8){green=dqcl.get(0);red=DeltaMapper.getSum(green,dqcl.get(1));blue=DeltaMapper.getDifference(red,dqcl.get(2));}
			else{red=dqcl.get(0);green=DeltaMapper.getDifference(red,dqcl.get(1));blue=DeltaMapper.getDifference(red,dqcl.get(2));}

			int[] ob=(int[])channel_list.get(0),og=(int[])channel_list.get(1),or_=(int[])channel_list.get(2);
			int k=0;
			for(int i=0;i<image_ydim;i++) for(int j=0;j<image_xdim;j++){
				if(correction!=0){double f=correction/10.0;blue[k]+=(int)((ob[k]-blue[k])*f);green[k]+=(int)((og[k]-green[k])*f);red[k]+=(int)((or_[k]-red[k])*f);}
				working_image.setRGB(j,i,(blue[k]<<16)+(green[k]<<8)+red[k]); k++;
			}
			updateDisplayImage(); image_canvas.repaint(); initialized=true;
			SimpleWriter.this.collectData(qcl, DeltaMapper.getChannels(min_set_id), new_xdim, new_ydim, false);
		}
	}




	private void collectData(ArrayList<int[]> qcl, int[] cid, int nx, int ny, boolean set_delta_type)
	{
		if (!print_ranking && !set_delta_type) return;
		print_ranking = false;

		System.out.println("Starting data collection...");
		String[] dt_names={"H","V","Average","Med","Adaptive","Directional","Scanline 1","Scanline 2","Scanline 3","Scanline 4","Map 1","Map 2"};
		int[] dt_cost=new int[12], map_cost=new int[12];
		boolean[] dt_comp=new boolean[12], map_comp=new boolean[12];
		for(int ci=0;ci<3;ci++){
			int[] qc=qcl.get(cid[ci]);
			ArrayList[] enc={
				DeltaMapper.getHorizontalDeltasFromValues(qc,nx,ny),
				DeltaMapper.getVerticalDeltasFromValues(qc,nx,ny),
				DeltaMapper.getAverageDeltasFromValues(qc,nx,ny),
				DeltaMapper.getMedDeltasFromValues(qc,nx,ny),
				DeltaMapper.getAdaptiveDeltasFromValues(qc,nx,ny),
				DeltaMapper.getDirectionalDeltasFromValues(qc,nx,ny)
			};
			for(int t=0;t<6;t++){
				byte[] str=(byte[])StringMapper.getStringList((int[])enc[t].get(1),true).get(3);
				dt_cost[t]+=StringMapper.getBitlength(str);
				if((StringMapper.getIterations(str)&15)>0) dt_comp[t]=true;
			}
			ArrayList[] menc={
				DeltaMapper.getMixedDeltasFromValues(qc,nx,ny),
				DeltaMapper.getMixedDeltasFromValues2(qc,nx,ny),
				DeltaMapper.getMixedDeltasFromValues4(qc,nx,ny),
				DeltaMapper.getMixedDeltasFromValues16Rows(qc,nx,ny),
				DeltaMapper.getIdealDeltasFromValues8(qc,nx,ny),
				DeltaMapper.getIdealDeltasFromValues16(qc,nx,ny)
			};
			for(int t=0;t<6;t++){
				byte[] dstr=(byte[])StringMapper.getStringList((int[])menc[t].get(1),true).get(3);
				dt_cost[6+t]+=StringMapper.getBitlength(dstr);
				if((StringMapper.getIterations(dstr)&15)>0) dt_comp[6+t]=true;
				byte[] map=(byte[])menc[t].get(2); int[] mi=new int[map.length];
				for(int k=0;k<map.length;k++) mi[k]=map[k]&0xFF;
				byte[] mstr=(byte[])StringMapper.getStringList(mi,true).get(3);
				map_cost[6+t]+=StringMapper.getBitlength(mstr);
				if((StringMapper.getIterations(mstr)&15)>0) map_comp[6+t]=true;
			}
		}
		int[] total=new int[12]; for(int t=0;t<12;t++) total[t]=dt_cost[t]+map_cost[t];
		Integer[] dto=new Integer[12]; for(int i=0;i<12;i++)dto[i]=i;
		java.util.Arrays.sort(dto,(a,b)->total[a]-total[b]);
		if(set_delta_type) delta_type=dto[0];

		System.out.println("Delta types (ranked):");
		for(int r=0;r<12;r++){
			int idx=dto[r]; String sel=(idx==delta_type)?" **":"";
			String dc=dt_comp[idx]?"*":" ", mc=map_comp[idx]?"*":" ";
			if(map_cost[idx]>0)
				System.out.println(String.format("  %2d. %-12s delta: %12d%s      map: %12d%s      total: %12d%s",
					r+1,dt_names[idx],dt_cost[idx],dc,map_cost[idx],mc,total[idx],sel));
			else
				System.out.println(String.format("  %2d. %-12s delta: %12d%s                              total: %12d%s",
					r+1,dt_names[idx],dt_cost[idx],dc,total[idx],sel));
		}
		System.out.println("Done.");
		System.out.println();
	}


	class SaveHandler implements ActionListener
	{
		public void actionPerformed(ActionEvent event)
		{
			if(!initialized) apply_item.doClick();
			int[] channel_id=DeltaMapper.getChannels(min_set_id);
			try {
				DataOutputStream out=new DataOutputStream(new FileOutputStream(new File("foo")));
				out.writeShort(image_xdim); out.writeShort(image_ydim);
				out.writeByte(pixel_shift); out.writeByte(pixel_quant);
				out.writeByte(min_set_id);  out.writeByte(delta_type); out.writeByte(entropy_type);

				// Gather payloads and frequency data for arithmetic
				byte[][] payloads=new byte[3][];
				for(int i=0;i<3;i++) payloads[i]=(byte[])string_list.get(i);

				for(int i=0;i<3;i++){
					int j=channel_id[i];
					out.writeInt(channel_min[j]); out.writeInt(channel_init[j]); out.writeInt(channel_delta_min[j]);
					out.writeInt(channel_length[j]); out.writeInt(channel_compressed_length[j]); out.writeByte(channel_iterations[i]);

					if(delta_type>=6){
						byte[] map=(byte[])map_list.get(i); int[] map_int=new int[map.length];
						for(int k=0;k<map.length;k++) map_int[k]=map[k]&0xFF;
						int map0=map_int[0];
						ArrayList dsl=StringMapper.getStringList(map_int,true);
						int dmin=(int)dsl.get(0); int[] tbl=(int[])dsl.get(2);
						byte[] mstr=(byte[])dsl.get(3); int bl=StringMapper.getBitlength(mstr);
						out.writeInt(map0-dmin); out.writeInt(map.length);
						writeTable(out,tbl); out.writeInt(dmin); out.writeInt(bl);
						out.write(mstr,0,StringMapper.getBytelength(bl));
					}

					writeTable(out,(int[])table_list.get(i));
					byte[] payload=payloads[i];

					if(entropy_type==0)
					{
						// LZ77
						Deflater def=new Deflater(Deflater.BEST_COMPRESSION);
						byte[] zipped=new byte[2*payload.length]; def.setInput(payload); def.finish();
						int zl=def.deflate(zipped); def.end();
						out.writeInt(payload.length); out.writeInt(zl); out.write(zipped,0,zl);
					}
					else if(entropy_type==1)
					{
						// Huffman: treat each byte as a 32-bit int symbol
						int[] pi=new int[payload.length];
						for(int k=0;k<payload.length;k++){pi[k]=payload[k]&0xFF;}
						ArrayList hl=StringMapper.getHistogram(pi); int pmin=(int)hl.get(0); int[] hist=(int[])hl.get(1);
						int[] rt=StringMapper.getRankTable(hist);
						for(int k=0;k<pi.length;k++) pi[k]-=pmin;
						int n=hist.length;
						ArrayList<Integer> fl=new ArrayList<>(); for(int v:hist)fl.add(v);
						java.util.Collections.sort(fl,java.util.Comparator.reverseOrder());
						int[] freq=new int[n]; for(int k=0;k<n;k++)freq[k]=fl.get(k);
						byte[] hl2=CodeMapper.getHuffmanLength2(freq); int[] hc=CodeMapper.getCanonicalCode(hl2);
						ArrayList pl=CodeMapper.packCode(pi,rt,hc,hl2); byte[] pb=(byte[])pl.get(0); int hbl=(int)pl.get(1);
						writeTable(out,rt); out.writeInt(pmin);
						ArrayList ltl=CodeMapper.packLengthTable(hl2); int ltn=(int)ltl.get(0);
						byte ltinit=(byte)ltl.get(1); byte ltmax=(byte)ltl.get(2); byte[] ltd=(byte[])ltl.get(3);
						out.writeInt(ltn); out.writeByte(ltinit); out.writeByte(ltmax);
						out.writeByte(ltd.length); out.write(ltd,0,ltd.length);
						out.writeInt(hbl); out.writeInt(pb.length); out.write(pb,0,pb.length);
					}
					else
					{
						// Arithmetic (fast or slow) — bytes packed as-is
						int min_seg=500+pixel_segment*500;
						int n_segs=(pixel_segment>=10)?1:Math.max(1,payload.length/min_seg);
						int seg_len=payload.length/n_segs, odd_len=seg_len+payload.length%n_segs;
						byte[][] segs=new byte[n_segs][];
						int[][] freqs=new int[n_segs][256];
						for(int m=0;m<n_segs;m++) segs[m]=new byte[m<n_segs-1?seg_len:odd_len];
						int pos=0;
						for(int m=0;m<n_segs;m++) for(int nn=0;nn<segs[m].length;nn++){segs[m][nn]=payload[pos];freqs[m][payload[pos]&0xFF]++;pos++;}
						// deflate frequency tables
						int fmax=0; for(int[] row:freqs)for(int v:row)if(v>fmax)fmax=v;
						int lt=(fmax<256)?0:(fmax<65536)?1:2; int bpe=(lt==0)?1:(lt==1)?2:4;
						byte[] fb=new byte[n_segs*256*bpe];
						for(int m=0;m<n_segs;m++) for(int k=0;k<256;k++){int v=freqs[m][k];int base=m*256*bpe+k*bpe;for(int b=0;b<bpe;b++)fb[base+b]=(byte)(v>>(8*b));}
						Deflater def=new Deflater(Deflater.BEST_COMPRESSION);
						byte[] zf=new byte[fb.length+64]; def.setInput(fb); def.finish(); int zl=def.deflate(zf); def.end();
						out.writeInt(payload.length); out.writeInt(n_segs); out.writeInt(lt); out.writeInt(zl); out.write(zf,0,zl);
						if(entropy_type==2)
						{
							// Slow arithmetic (BigInteger)
							for(int m=0;m<n_segs;m++){
								java.math.BigInteger[] r=ArithmeticMapper.getIntervalValue(segs[m],freqs[m]);
								byte[] b0=r[0].toByteArray(); out.writeInt(b0.length); out.write(b0,0,b0.length);
								byte[] b1=r[1].toByteArray(); out.writeInt(b1.length); out.write(b1,0,b1.length);
							}
						}
						else
						{
							// Fast arithmetic
							for(int m=0;m<n_segs;m++){
								byte[] enc=ArithmeticMapper.getIntervalValueFast(segs[m],freqs[m]);
								out.writeInt(enc.length); out.write(enc,0,enc.length);
							}
						}
					}
				}
				out.flush(); out.close();
				double rate=(double)new File("foo").length()/(image_xdim*image_ydim*3);
				System.out.println("Original rate: "+String.format("%.4f",file_compression_rate));
				System.out.println("Output rate:   "+String.format("%.4f",rate));
			} catch(Exception e){System.out.println("Save error: "+e);}
		}
	}

}
