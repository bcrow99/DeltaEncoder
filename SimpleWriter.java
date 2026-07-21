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
	ImageCanvas   image_canvas;
	JScrollPane   scroll_pane;
	int image_xdim, image_ydim;
	int screen_xdim, screen_ydim;
	JMenuItem apply_item;
	String filename;
	int[] pixel;

	int pixel_quant   = 4;
	int pixel_shift   = 3;
	int correction    = 0;
	int smooth_level  = 0;
	int smooth2_level = 0;
	int min_set_id    = 0;
	int previous_min_set_id = -1;
	int delta_type    = 5;
	int entropy_type  = 0;
	int pixel_segment = 0;
	int data_type     = 0;   // 0=String, 1=byte, 2=short, 3=int

	double zoom_scale = 1.0;
	double fit_scale  = 1.0;
	static final double ZOOM_FACTOR = 1.25, ZOOM_MIN = 0.05, ZOOM_MAX = 32.0;
	static int openWindowCount = 0;

	int[] set_sum, channel_sum;
	String[] set_string, delta_type_string, channel_string;
	int[] channel_init, channel_min, channel_delta_min;
	int[] channel_length, channel_compressed_length;
	byte[] channel_iterations;

	long   file_length;
	double file_compression_rate;
	boolean initialized   = false;
	boolean print_ranking = false;

	// Saved statistics for the Statistics dialog
	int[]     saved_dt_cost  = new int[12];
	int[]     saved_map_cost = new int[12];
	boolean[] saved_dt_comp  = new boolean[12];
	boolean[] saved_map_comp = new boolean[12];
	Integer[] saved_dt_order = new Integer[12];
	int[]     saved_ss       = new int[10];
	int[]     saved_cs       = new int[6];
	Integer[] saved_set_order= new Integer[10];

	JRadioButtonMenuItem[] delta_button;
	JRadioButtonMenuItem[] entropy_button;
	JSlider quant_slider, shift_slider, corr_slider, smooth1_slider, smooth2_slider;
	JFrame frame = null;

	// encode lists
	ArrayList<Object> channel_list, table_list, string_list, map_list, delta_list;

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
			System.out.println("Loaded " + filename + "  (" + image_xdim + " x " + image_ydim + ")");

			channel_list = new ArrayList<>(); table_list = new ArrayList<>();
			string_list  = new ArrayList<>(); map_list   = new ArrayList<>();
			delta_list   = new ArrayList<>();

			channel_string    = new String[]{"blue","green","red","blue-green","red-green","red-blue"};
			set_sum    = new int[10];
			delta_type_string = new String[]{"H","V","Average","Med","Adaptive","Directional",
				"Scanline 1","Scanline 2","Scanline 3","Scanline 4","Map 1","Map 2"};
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

				openWindowCount++;
				frame = new JFrame("Simple Writer  " + filename);
				frame.addWindowListener(new WindowAdapter() { public void windowClosing(WindowEvent e) { frame.dispose(); if(--openWindowCount==0) System.exit(0); }});
				frame.getContentPane().add(scroll_pane, BorderLayout.CENTER);

				JMenuBar menu_bar = new JMenuBar();

				// File menu
				JMenu file_menu = new JMenu("File");
				JMenuItem open_item = new JMenuItem("Open...");
				open_item.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_O, InputEvent.CTRL_DOWN_MASK));
				open_item.addActionListener(e -> { FileDialog fd=new FileDialog(frame,"Open Image",FileDialog.LOAD); fd.setVisible(true); if(fd.getFile()!=null) new SimpleWriter(fd.getDirectory()+fd.getFile()); });
				file_menu.add(open_item); file_menu.addSeparator();
				apply_item = new JMenuItem("Apply"); apply_item.addActionListener(new ApplyHandler());
				JMenuItem reset_item = new JMenuItem("Reset");
				reset_item.addActionListener(e -> {
					// setValue triggers onChange which sets field + calls Apply
					quant_slider.setValue(0); shift_slider.setValue(0);
					corr_slider.setValue(0); smooth1_slider.setValue(0); smooth2_slider.setValue(0);
				});
				file_menu.add(reset_item);
				JMenuItem save_item = new JMenuItem("Save"); save_item.addActionListener(new SaveHandler()); file_menu.add(save_item);

				// View menu
				JMenu view_menu = new JMenu("View");
				JMenuItem zi=new JMenuItem("Zoom In"); zi.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_EQUALS,InputEvent.CTRL_DOWN_MASK)); zi.addActionListener(e->zoomBy(ZOOM_FACTOR)); view_menu.add(zi);
				JMenuItem zo=new JMenuItem("Zoom Out"); zo.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_MINUS,InputEvent.CTRL_DOWN_MASK)); zo.addActionListener(e->zoomBy(1.0/ZOOM_FACTOR)); view_menu.add(zo);
				JMenuItem zf=new JMenuItem("Fit"); zf.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_0,InputEvent.CTRL_DOWN_MASK)); zf.addActionListener(e->{ Dimension v=scroll_pane.getViewport().getSize(); zoom_scale=Math.min((double)v.width/image_xdim,(double)v.height/image_ydim); updateDisplayImage(); image_canvas.setPreferredSize(new Dimension((int)(image_xdim*zoom_scale),(int)(image_ydim*zoom_scale))); image_canvas.revalidate(); image_canvas.repaint(); updateTitle(); }); view_menu.add(zf);
				JMenuItem za=new JMenuItem("100%"); za.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_1,InputEvent.CTRL_DOWN_MASK)); za.addActionListener(e->{ zoom_scale=1.0; updateDisplayImage(); image_canvas.setPreferredSize(new Dimension(image_xdim,image_ydim)); image_canvas.revalidate(); image_canvas.repaint(); updateTitle(); }); view_menu.add(za);

				// Quantization menu
				JMenu quant_menu = new JMenu("Quantization");
				// Smooth dialog with bilateral + anisotropic sliders
				{ JDialog smooth_dlg = new JDialog(frame, "Smooth");
				  smooth1_slider=new JSlider(0,10,smooth_level); smooth2_slider=new JSlider(0,10,smooth2_level);
				  smooth1_slider.setMajorTickSpacing(1); smooth1_slider.setPaintTicks(true); smooth1_slider.setSnapToTicks(true);
				  smooth2_slider.setMajorTickSpacing(1); smooth2_slider.setPaintTicks(true); smooth2_slider.setSnapToTicks(true);
				  JTextField tf1=new JTextField(" 0 ",3), tf2=new JTextField(" 0 ",3);
				  smooth1_slider.addChangeListener(e->{tf1.setText(" "+smooth1_slider.getValue()+" ");if(!smooth1_slider.getValueIsAdjusting()){smooth_level=smooth1_slider.getValue();print_ranking=true;apply_item.doClick();}});
				  smooth2_slider.addChangeListener(e->{tf2.setText(" "+smooth2_slider.getValue()+" ");if(!smooth2_slider.getValueIsAdjusting()){smooth2_level=smooth2_slider.getValue();print_ranking=true;apply_item.doClick();}});
				  JPanel p1=new JPanel(new BorderLayout(4,4)); p1.add(new javax.swing.JLabel(" Bilateral:     "),BorderLayout.WEST); p1.add(smooth1_slider,BorderLayout.CENTER); p1.add(tf1,BorderLayout.EAST);
				  JPanel p2=new JPanel(new BorderLayout(4,4)); p2.add(new javax.swing.JLabel(" Anisotropic:"),BorderLayout.WEST); p2.add(smooth2_slider,BorderLayout.CENTER); p2.add(tf2,BorderLayout.EAST);
				  JPanel sp=new JPanel(new java.awt.GridLayout(2,1,4,4)); sp.add(p1); sp.add(p2);
				  smooth_dlg.add(sp);
				  JMenuItem smooth_mi=new JMenuItem("Smooth");
				  smooth_mi.addActionListener(e->{Point loc=frame.getLocation();smooth_dlg.setLocation((int)loc.getX(),(int)loc.getY()-80);smooth_dlg.pack();smooth_dlg.setVisible(true);});
				  quant_menu.add(smooth_mi); }
				quant_slider = new JSlider(0,10,pixel_quant);
				shift_slider = new JSlider(0,7, pixel_shift);
				corr_slider  = new JSlider(0,10,correction);
				quant_menu.add(makeSliderWithRef("Pixel Resolution", quant_slider, v -> { pixel_quant=v; print_ranking=true; apply_item.doClick(); }));
				quant_menu.add(makeSliderWithRef("Color Resolution", shift_slider, v -> { pixel_shift=v; print_ranking=true; apply_item.doClick(); }));
				quant_menu.add(makeSliderWithRef("Error Correction", corr_slider,  v -> { correction=v;  print_ranking=true; apply_item.doClick(); }));

				// Delta menu
				JMenu delta_menu = new JMenu("Delta");
				String[] dnames={"H","V","Average","Med","Adaptive","Directional","Scanline 1","Scanline 2","Scanline 3","Scanline 4","Map 1","Map 2"};
				delta_button = new JRadioButtonMenuItem[12];
				ButtonGroup dg = new ButtonGroup();
				for (int i=0;i<12;i++) {
					delta_button[i]=new JRadioButtonMenuItem(dnames[i]); dg.add(delta_button[i]); delta_menu.add(delta_button[i]);
					final int dt=i; delta_button[i].addActionListener(e->{ if(delta_type!=dt){delta_type=dt; print_ranking=true; apply_item.doClick();} });
				}
				delta_button[delta_type].setSelected(true);

				// Data menu
				JMenu data_menu = new JMenu("Data");
				// Type dialog: String vs Integer
				JDialog type_dlg = new JDialog(frame, "Type");
				JRadioButton str_r = new JRadioButton("String",  true);
				JRadioButton int_r = new JRadioButton("Integer", false);
				ButtonGroup type_grp = new ButtonGroup(); type_grp.add(str_r); type_grp.add(int_r);
				str_r.addActionListener(e -> { if(data_type!=0){data_type=0; apply_item.doClick();} });
				int_r.addActionListener(e -> { if(data_type==0){data_type=1; apply_item.doClick();} });
				JPanel type_pnl = new JPanel(); type_pnl.add(str_r); type_pnl.add(int_r);
				type_dlg.add(type_pnl);
				JMenuItem type_mi = new JMenuItem("Type");
				type_mi.addActionListener(e -> {
					str_r.setSelected(data_type==0); int_r.setSelected(data_type!=0);
					Point loc=frame.getLocation();
					type_dlg.setLocation((int)loc.getX(),(int)loc.getY()-80);
					type_dlg.pack(); type_dlg.setVisible(true);
				});
				data_menu.add(type_mi);
				data_menu.add(makeSlider("Threshold", 0, 10, 1, v -> StringMapper.compress_threshold = v / 10.0));

				// Entropy menu
				JMenu entropy_menu = new JMenu("Entropy");
				entropy_button = new JRadioButtonMenuItem[3];
				entropy_button[0] = new JRadioButtonMenuItem("LZ77");
				entropy_button[1] = new JRadioButtonMenuItem("Huffman");
				entropy_button[2] = new JRadioButtonMenuItem("Arithmetic");
				ButtonGroup eg = new ButtonGroup();
				for (int i=0;i<3;i++) { eg.add(entropy_button[i]); entropy_menu.add(entropy_button[i]); }
				entropy_button[0].setSelected(true);
				entropy_button[0].addActionListener(e -> entropy_type=0);
				entropy_button[1].addActionListener(e -> entropy_type=1);
				{ JDialog arith_dlg = new JDialog(frame,"Arithmetic");
				  JRadioButtonMenuItem fast_btn=new JRadioButtonMenuItem("Fast");
				  JRadioButtonMenuItem slow_btn=new JRadioButtonMenuItem("Slow");
				  ButtonGroup ag=new ButtonGroup(); ag.add(fast_btn); ag.add(slow_btn);
				  fast_btn.setSelected(true);
				  fast_btn.addActionListener(e2->entropy_type=3);
				  slow_btn.addActionListener(e2->entropy_type=2);
				  JSlider seg_sl=new JSlider(0,10,pixel_segment);
				  seg_sl.setMajorTickSpacing(1); seg_sl.setPaintTicks(true); seg_sl.setSnapToTicks(true);
				  JTextField seg_tf=new JTextField(" 0 ",3);
				  seg_sl.addChangeListener(e2->{ seg_tf.setText(" "+seg_sl.getValue()+" "); pixel_segment=seg_sl.getValue(); });
				  JPanel rp=new JPanel(); rp.add(fast_btn); rp.add(slow_btn);
				  JPanel sp=new JPanel(new BorderLayout(4,4));
				  sp.add(new javax.swing.JLabel("Segment Length:"),BorderLayout.WEST);
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

				// Statistics menu
				JMenu stats_menu = new JMenu("Statistics");
				JMenuItem stats_mi = new JMenuItem("Show...");
				stats_mi.addActionListener(e -> showStatistics());
				stats_menu.add(stats_mi);

				menu_bar.add(file_menu); menu_bar.add(view_menu); menu_bar.add(quant_menu);
				menu_bar.add(delta_menu); menu_bar.add(data_menu); menu_bar.add(entropy_menu); menu_bar.add(stats_menu);
				frame.setJMenuBar(menu_bar);

				display_image = original_image;
				image_canvas.setPreferredSize(new Dimension((int)(image_xdim*zoom_scale),(int)(image_ydim*zoom_scale)));
				updateTitle();
				int display_w=(int)(image_xdim*zoom_scale), display_h=(int)(image_ydim*zoom_scale);
				frame.pack();
				int ow=frame.getWidth()-scroll_pane.getViewport().getWidth(), oh=frame.getHeight()-scroll_pane.getViewport().getHeight();
				int dialog_clearance=120;
				frame.setSize(display_w+ow, Math.min(display_h+oh, screen_ydim-dialog_clearance));
				frame.setLocation(5, dialog_clearance);
				frame.setVisible(true);
				SwingUtilities.invokeLater(() -> showInitialImage());
			}
		}
		catch (Exception e) { System.out.println(e.getMessage()); System.exit(1); }
	}

	private JMenuItem makeSlider(String title, int lo, int hi, int init, java.util.function.IntConsumer onChange)
	{
		JSlider slider = new JSlider(lo, hi, init);
		return makeSliderWithRef(title, slider, onChange);
	}

	private JMenuItem makeSliderWithRef(String title, JSlider slider, java.util.function.IntConsumer onChange)
	{
		JMenuItem item = new JMenuItem(title);
		JDialog dialog = new JDialog(frame, title);
		slider.setMajorTickSpacing(1); slider.setPaintTicks(true); slider.setSnapToTicks(true);
		JTextField field = new JTextField(3); field.setText(" "+slider.getValue()+" ");
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

	// Prints the ranked channel-set table (rank, channel names, per-channel
	// entropy estimate, total). Shared by init() and by ApplyHandler when the
	// optimal set changes.
	private void printColorSetRanking(int[] ss, int[] cs, Integer[] so)
	{
		String[][] set_disp={
			{"blue",  "green",         "red"},
			{"blue",  "red",           "red   -  green"},
			{"blue",  "red",           "blue  -  green"},
			{"blue",  "blue  -  green","red   -  green"},
			{"blue",  "blue  -  green","red   -  blue"},
			{"green", "red",           "blue  -  green"},
			{"red",   "blue  -  green","red   -  green"},
			{"green", "blue  -  green","red   -  green"},
			{"green", "red   -  green","red   -  blue"},
			{"red",   "red   -  green","red   -  blue"}
		};
		for(int r=0;r<10;r++){
			int idx=so[r]; int[] c=DeltaMapper.getChannels(idx);
			System.out.println(String.format("  %2d. %-8s%-20s%-14s %10d %10d %10d %12d",
				r+1,set_disp[idx][0],set_disp[idx][1],set_disp[idx][2],cs[c[0]],cs[c[1]],cs[c[2]],ss[idx]));
		}
		System.out.println();
	}

	private void init()
	{
		ArrayList<int[]> qcl=new ArrayList<>();
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
		for(int i=0;i<6;i++){
			int[] qc=qcl.get(i); int min=256; for(int v:qc)if(v<min)min=v;
			if(i>2)for(int k=0;k<qc.length;k++)qc[k]-=min;
			qcl.set(i,qc);
			cs[i]=(int)Math.floor(CodeMapper.getShannonLimit(DeltaMapper.getIdealFrequency(qc,nx,ny)));
		}
		int[] ss=new int[10];
		ss[0]=cs[0]+cs[1]+cs[2]; ss[1]=cs[0]+cs[4]+cs[2]; ss[2]=cs[0]+cs[3]+cs[2];
		ss[3]=cs[0]+cs[1]+cs[4]; ss[4]=cs[0]+cs[3]+cs[5]; ss[5]=cs[3]+cs[1]+cs[2];
		ss[6]=cs[3]+cs[4]+cs[2]; ss[7]=cs[3]+cs[1]+cs[4]; ss[8]=cs[5]+cs[1]+cs[4]; ss[9]=cs[5]+cs[4]+cs[2];
		Integer[] so=new Integer[10]; for(int i=0;i<10;i++)so[i]=i;
		java.util.Arrays.sort(so,(a,b)->ss[a]-ss[b]);
		saved_ss=ss.clone(); saved_cs=cs.clone(); saved_set_order=so.clone();
		int best_set=so[0]; int[] cid=DeltaMapper.getChannels(best_set);

		print_ranking=true;
		System.out.println("Starting data collection...");
		collectData(qcl,cid,nx,ny,true);

		System.out.println("Channel sets (ranked):");
		printColorSetRanking(ss, cs, so);
	}

	private void collectData(ArrayList<int[]> qcl, int[] cid, int nx, int ny, boolean set_delta_type)
	{
		if(!print_ranking && !set_delta_type) return;
		print_ranking=false;
		String[] dt_names={"H","V","Average","Med","Adaptive","Directional","Scanline 1","Scanline 2","Scanline 3","Scanline 4","Map 1","Map 2"};
		int[] dt_cost=new int[12], map_cost=new int[12];
		boolean[] dt_comp=new boolean[12], map_comp=new boolean[12];

		Thread[] threads=new Thread[12];
		for(int t=0;t<12;t++){
			final int ft=t;
			threads[t]=new Thread(()->{
				int cost=0, mcost=0; boolean comp=false, mcomp=false;
				for(int ci=0;ci<3;ci++){
					int[] qc=qcl.get(cid[ci]);
					ArrayList result;
					if(ft==0)result=DeltaMapper.getHorizontalDeltasFromValues(qc,nx,ny);
					else if(ft==1)result=DeltaMapper.getVerticalDeltasFromValues(qc,nx,ny);
					else if(ft==2)result=DeltaMapper.getAverageDeltasFromValues(qc,nx,ny);
					else if(ft==3)result=DeltaMapper.getMedDeltasFromValues(qc,nx,ny);
					else if(ft==4)result=DeltaMapper.getAdaptiveDeltasFromValues(qc,nx,ny);
					else if(ft==5)result=DeltaMapper.getDirectionalDeltasFromValues(qc,nx,ny);
					else if(ft==6)result=DeltaMapper.getMixedDeltasFromValues(qc,nx,ny);
					else if(ft==7)result=DeltaMapper.getMixedDeltasFromValues2(qc,nx,ny);
					else if(ft==8)result=DeltaMapper.getMixedDeltasFromValues4(qc,nx,ny);
					else if(ft==9)result=DeltaMapper.getMixedDeltasFromValues16Rows(qc,nx,ny);
					else if(ft==10)result=DeltaMapper.getIdealDeltasFromValues8(qc,nx,ny);
					else result=DeltaMapper.getIdealDeltasFromValues16(qc,nx,ny);

					byte[] dstr=(byte[])StringMapper.getStringList((int[])result.get(1),true).get(3);
					cost+=StringMapper.getBitlength(dstr);
					if((StringMapper.getIterations(dstr)&15)>0) comp=true;

					if(ft>=6){
						byte[] map=(byte[])result.get(2); int[] mi=new int[map.length];
						for(int k=0;k<map.length;k++) mi[k]=map[k]&0xFF;
						byte[] mstr=(byte[])StringMapper.getStringList(mi,true).get(3);
						mcost+=StringMapper.getBitlength(mstr);
						if((StringMapper.getIterations(mstr)&15)>0) mcomp=true;
					}
				}
				dt_cost[ft]=cost; map_cost[ft]=mcost;
				dt_comp[ft]=comp; map_comp[ft]=mcomp;
			});
			threads[t].start();
		}
		try{ for(Thread t:threads) t.join(); } catch(InterruptedException e){ Thread.currentThread().interrupt(); }

		int[] total=new int[12]; for(int t=0;t<12;t++) total[t]=dt_cost[t]+map_cost[t];
		Integer[] dto=new Integer[12]; for(int i=0;i<12;i++)dto[i]=i;
		java.util.Arrays.sort(dto,(a,b)->total[a]-total[b]);
		if(set_delta_type) delta_type=dto[0];
		saved_dt_cost=dt_cost.clone(); saved_map_cost=map_cost.clone();
		saved_dt_comp=dt_comp.clone(); saved_map_comp=map_comp.clone();
		saved_dt_order=dto.clone();
		System.out.println("Delta types (ranked):");
		for(int r=0;r<12;r++){
			int idx=dto[r]; String sel=(idx==delta_type)?" **":"";
			String dc=dt_comp[idx]?"*":" ", mc=map_comp[idx]?"*":" ";
			if(map_cost[idx]>0 || idx>=6)
				System.out.println(String.format("  %2d. %-12s delta: %12d%s      map: %12d%s      total: %12d%s",
					r+1,dt_names[idx],dt_cost[idx],dc,map_cost[idx],mc,total[idx],sel));
			else
				System.out.println(String.format("  %2d. %-12s delta: %12d%s                              total: %12d%s",
					r+1,dt_names[idx],dt_cost[idx],dc,total[idx],sel));
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

	private void updateTitle() { frame.setTitle("Simple Writer  "+filename+"  ["+(int)Math.round(zoom_scale*100)+"%]"); }

	private static void writeTable(DataOutputStream out, int[] table) throws IOException
	{
		out.writeShort(table.length);
		int max=Byte.MAX_VALUE*2+1;
		if(table.length<=max) for(int v:table)out.writeByte(v); else for(int v:table)out.writeShort(v);
	}

	// Compute deltas for a given channel and delta type
	private ArrayList<Object> computeDeltas(int[] qc, int nx, int ny, int dt)
	{
		if(dt==0)  return DeltaMapper.getHorizontalDeltasFromValues(qc,nx,ny);
		if(dt==1)  return DeltaMapper.getVerticalDeltasFromValues(qc,nx,ny);
		if(dt==2)  return DeltaMapper.getAverageDeltasFromValues(qc,nx,ny);
		if(dt==3)  return DeltaMapper.getMedDeltasFromValues(qc,nx,ny);
		if(dt==4)  return DeltaMapper.getAdaptiveDeltasFromValues(qc,nx,ny);
		if(dt==5)  return DeltaMapper.getDirectionalDeltasFromValues(qc,nx,ny);
		if(dt==6)  return DeltaMapper.getMixedDeltasFromValues(qc,nx,ny);
		if(dt==7)  return DeltaMapper.getMixedDeltasFromValues2(qc,nx,ny);
		if(dt==8)  return DeltaMapper.getMixedDeltasFromValues4(qc,nx,ny);
		if(dt==9)  return DeltaMapper.getMixedDeltasFromValues16Rows(qc,nx,ny);
		if(dt==10) return DeltaMapper.getIdealDeltasFromValues8(qc,nx,ny);
		           return DeltaMapper.getIdealDeltasFromValues16(qc,nx,ny);
	}

	// Reconstruct channel values from deltas
	private int[] reconstructChannel(int[] delta, int nx, int ny, int init, int dt, int idx)
	{
		if(dt==0)  return DeltaMapper.getValuesFromHorizontalDeltas(delta,nx,ny,init);
		if(dt==1)  return DeltaMapper.getValuesFromVerticalDeltas(delta,nx,ny,init);
		if(dt==2)  return DeltaMapper.getValuesFromAverageDeltas(delta,nx,ny,init);
		if(dt==3)  return DeltaMapper.getValuesFromMedDeltas(delta,nx,ny,init);
		if(dt==4)  return DeltaMapper.getValuesFromAdaptiveDeltas(delta,nx,ny,init);
		if(dt==5)  return DeltaMapper.getValuesFromDirectionalDeltas(delta,nx,ny,init);
		if(dt==6)  return DeltaMapper.getValuesFromMixedDeltas(delta,nx,ny,init,(byte[])map_list.get(idx));
		if(dt==7)  return DeltaMapper.getValuesFromMixedDeltas2(delta,nx,ny,init,(byte[])map_list.get(idx));
		if(dt==8)  return DeltaMapper.getValuesFromMixedDeltas4(delta,nx,ny,init,(byte[])map_list.get(idx));
		if(dt==9)  return DeltaMapper.getValuesFromMixedDeltas16Rows(delta,nx,ny,init,(byte[])map_list.get(idx));
		if(dt==10) return DeltaMapper.getValuesFromIdealDeltas8(delta,nx,ny,init,(byte[])map_list.get(idx));
		           return DeltaMapper.getValuesFromIdealDeltas16(delta,nx,ny,init,(byte[])map_list.get(idx));
	}

	private void showStatistics()
	{
		if(saved_set_order==null || saved_set_order[0]==null ||
		   saved_dt_order==null  || saved_dt_order[0]==null)
		{
			JOptionPane.showMessageDialog(frame, "Data collection still in progress, please wait.");
			return;
		}

		JDialog dlg = new JDialog(frame, "Statistics");
		dlg.setLayout(new java.awt.GridLayout(2,1,8,8));

		// Channel sets table
		String[][] set_disp={
			{"blue",  "green",         "red"},
			{"blue",  "red",           "red - green"},
			{"blue",  "red",           "blue - green"},
			{"blue",  "blue - green",  "red - green"},
			{"blue",  "blue - green",  "red - blue"},
			{"green", "red",           "blue - green"},
			{"red",   "blue - green",  "red - green"},
			{"green", "blue - green",  "red - green"},
			{"green", "red - green",   "red - blue"},
			{"red",   "red - green",   "red - blue"}
		};
		String[] set_cols={"Rank","Ch 1","Ch 2","Ch 3","Entropy 1","Entropy 2","Entropy 3","Total"};
		Object[][] set_data=new Object[10][8];
		for(int r=0;r<10;r++){
			int idx=saved_set_order[r]; int[] c=DeltaMapper.getChannels(idx);
			set_data[r][0]=r+1; set_data[r][1]=set_disp[idx][0];
			set_data[r][2]=set_disp[idx][1]; set_data[r][3]=set_disp[idx][2];
			set_data[r][4]=saved_cs[c[0]]; set_data[r][5]=saved_cs[c[1]];
			set_data[r][6]=saved_cs[c[2]]; set_data[r][7]=saved_ss[idx];
		}
		javax.swing.JTable set_table=new javax.swing.JTable(set_data,set_cols);
		set_table.setEnabled(false);
		dlg.add(new JScrollPane(set_table));

		// Delta types table
		String[] dt_names={"H","V","Average","Med","Adaptive","Directional","Scanline 1","Scanline 2","Scanline 3","Scanline 4","Map 1","Map 2"};
		String[] dt_cols={"Rank","Type","Delta","*","Map","*","Total","**"};
		Object[][] dt_data=new Object[12][8];
		for(int r=0;r<12;r++){
			int idx=saved_dt_order[r];
			int total=saved_dt_cost[idx]+saved_map_cost[idx];
			dt_data[r][0]=r+1; dt_data[r][1]=dt_names[idx];
			dt_data[r][2]=saved_dt_cost[idx]; dt_data[r][3]=saved_dt_comp[idx]?"*":" ";
			dt_data[r][4]=saved_map_cost[idx]>0?saved_map_cost[idx]:null;
			dt_data[r][5]=saved_map_cost[idx]>0&&saved_map_comp[idx]?"*":" ";
			dt_data[r][6]=total; dt_data[r][7]=idx==delta_type?"**":" ";
		}
		javax.swing.JTable dt_table=new javax.swing.JTable(dt_data,dt_cols);
		dt_table.setEnabled(false);
		dlg.add(new JScrollPane(dt_table));

		dlg.setSize(700,500);
		Point loc=frame.getLocation();
		dlg.setLocation((int)loc.getX()+20,(int)loc.getY()+20);
		dlg.setVisible(true);
	}

	class ImageCanvas extends JPanel
	{
		public ImageCanvas(){setOpaque(true);}
		@Override public Dimension getPreferredSize(){return display_image!=null?new Dimension(display_image.getWidth(),display_image.getHeight()):new Dimension(Math.max(1,(int)(image_xdim*zoom_scale)),Math.max(1,(int)(image_ydim*zoom_scale)));}
		@Override protected synchronized void paintComponent(Graphics g){super.paintComponent(g);if(display_image!=null)g.drawImage(display_image,0,0,this);}
	}

	class ApplyHandler implements ActionListener
	{
		public void actionPerformed(ActionEvent event)
		{
			try { applyImpl(); } catch(Exception e){System.out.println("ApplyHandler: "+e); e.printStackTrace();}
		}

		private void applyImpl()
		{
			ArrayList<int[]> qcl=new ArrayList<>(), dqcl=new ArrayList<>();
			int new_xdim=image_xdim, new_ydim=image_ydim;
			if(pixel_quant!=0){double f=pixel_quant/10.0;new_xdim=image_xdim-(int)(f*(image_xdim/2-2));new_ydim=image_ydim-(int)(f*(image_ydim/2-2));}
			for(int i=0;i<3;i++){
				int[] ch=(int[])channel_list.get(i);
				if(smooth_level>0)  ch=DeltaMapper.bilateralSmooth(ch,image_xdim,image_ydim,smooth_level);
				if(smooth2_level>0) ch=DeltaMapper.anisotropicSmooth(ch,image_xdim,image_ydim,smooth2_level);
				if(pixel_quant==0) qcl.add(pixel_shift==0?ch:DeltaMapper.shift(ch,-pixel_shift));
				else{int[] r=ResizeMapper.resize(ch,image_xdim,new_xdim,new_ydim);qcl.add(pixel_shift==0?r:DeltaMapper.shift(r,-pixel_shift));}
			}
			qcl.add(DeltaMapper.getDifference(qcl.get(0),qcl.get(1)));
			qcl.add(DeltaMapper.getDifference(qcl.get(2),qcl.get(1)));
			qcl.add(DeltaMapper.getDifference(qcl.get(2),qcl.get(0)));
			for(int i=0;i<6;i++){
				int[] qc=qcl.get(i); int min=256; for(int v:qc)if(v<min)min=v;
				channel_min[i]=min; if(i>2)for(int k=0;k<qc.length;k++)qc[k]-=min;
				channel_init[i]=qc[0]; qcl.set(i,qc);
				channel_sum[i]=(int)Math.floor(CodeMapper.getShannonLimit(DeltaMapper.getIdealFrequency(qc,new_xdim,new_ydim)));
			}
			int[] ss=new int[10];
			ss[0]=channel_sum[0]+channel_sum[1]+channel_sum[2]; ss[1]=channel_sum[0]+channel_sum[4]+channel_sum[2];
			ss[2]=channel_sum[0]+channel_sum[3]+channel_sum[2]; ss[3]=channel_sum[0]+channel_sum[1]+channel_sum[4];
			ss[4]=channel_sum[0]+channel_sum[3]+channel_sum[5]; ss[5]=channel_sum[3]+channel_sum[1]+channel_sum[2];
			ss[6]=channel_sum[3]+channel_sum[4]+channel_sum[2]; ss[7]=channel_sum[3]+channel_sum[1]+channel_sum[4];
			ss[8]=channel_sum[5]+channel_sum[1]+channel_sum[4]; ss[9]=channel_sum[5]+channel_sum[4]+channel_sum[2];
			int min_sum=Integer.MAX_VALUE,min_idx=0;
			for(int i=0;i<10;i++)if(ss[i]<min_sum){min_sum=ss[i];min_idx=i;} min_set_id=min_idx;

			if(previous_min_set_id != -1 && previous_min_set_id != min_set_id)
			{
				Integer[] so=new Integer[10]; for(int i=0;i<10;i++)so[i]=i;
				java.util.Arrays.sort(so,(a,b)->ss[a]-ss[b]);
				System.out.println("Optimal color set changed:");
				printColorSetRanking(ss, channel_sum, so);
			}
			previous_min_set_id = min_set_id;

			file_compression_rate=(double)file_length/(image_xdim*image_ydim*3);
			int[] channel_id=DeltaMapper.getChannels(min_set_id);
			table_list.clear(); string_list.clear(); map_list.clear(); delta_list.clear();

			// ---- Encode pass (3 threads, one per channel) ----------------------
			int global_dmax = 0;
			int[][] raw_deltas = new int[3][];
			// Pre-allocate result slots so threads write by index, not add()
			int[][]  enc_tables  = new int[3][];
			byte[][] enc_strings = new byte[3][];
			byte[][] enc_maps    = new byte[3][];
			byte[][] enc_deltas  = new byte[3][];
			int[]    local_dmax  = new int[3];

			final int fnx=new_xdim, fny=new_ydim;
			Thread[] enc_threads = new Thread[3];
			for(int i=0;i<3;i++){
				final int fi=i;
				enc_threads[i]=new Thread(()->{
					int fj=channel_id[fi]; int[] qc=qcl.get(fj);
					ArrayList<Object> result=computeDeltas(qc,fnx,fny,delta_type);
					if(delta_type>=6) enc_maps[fi]=(byte[])result.get(2);
					int[] delta=(int[])result.get(1);
					if(data_type==0)
					{
						ArrayList dsl=StringMapper.getStringList(delta,true);
						channel_delta_min[fj]=(int)dsl.get(0); channel_length[fj]=(int)dsl.get(1);
						enc_tables[fi]=(int[])dsl.get(2); enc_strings[fi]=(byte[])dsl.get(3);
						channel_compressed_length[fj]=StringMapper.getBitlength(enc_strings[fi]);
						channel_iterations[fi]=StringMapper.getIterations(enc_strings[fi]);
						for(int k=1;k<delta.length;k++) delta[k]+=channel_delta_min[fj];
					}
					else
					{
						int dmin=delta[1]; for(int k=2;k<delta.length;k++) if(delta[k]<dmin) dmin=delta[k];
						channel_delta_min[fj]=dmin;
						int dmax=0; for(int k=1;k<delta.length;k++){int v=delta[k]-dmin;if(v>dmax)dmax=v;}
						local_dmax[fi]=dmax;
						channel_length[fj]=delta.length; channel_iterations[fi]=0;
						raw_deltas[fi]=delta; enc_tables[fi]=new int[0];
					}
				});
				enc_threads[i].start();
			}
			try{ for(Thread t:enc_threads) t.join(); } catch(InterruptedException e){ Thread.currentThread().interrupt(); }

			// Add results in order
			for(int i=0;i<3;i++){
				if(delta_type>=6) map_list.add(enc_maps[i]);
				table_list.add(enc_tables[i]);
				if(data_type==0) string_list.add(enc_strings[i]);
			}

			// For Integer: determine global width and pack all channels
			if(data_type!=0)
			{
				for(int i=0;i<3;i++) if(local_dmax[i]>global_dmax) global_dmax=local_dmax[i];
				data_type=(global_dmax<=255)?1:(global_dmax<=65535)?2:3;
				int width=(data_type==1)?1:(data_type==2)?2:4;
				for(int i=0;i<3;i++){
					int j=channel_id[i]; int[] delta=raw_deltas[i]; int dmin=channel_delta_min[j];
					channel_compressed_length[j]=width;
					byte[] db=new byte[delta.length*width]; db[0]=0;
					for(int k=1;k<delta.length;k++){
						int v=delta[k]-dmin; int base=k*width;
						for(int b=0;b<width;b++) db[base+b]=(byte)(v>>(8*b));
					}
					delta_list.add(db);
				}
			}

			// ---- Preview decode pass -----------------------------------------
			for(int i=0;i<3;i++){
				int j=channel_id[i];
				int[] delta;
				if(data_type==0)
				{
					int[] tbl=(int[])table_list.get(i);
					byte[] str=StringMapper.decompressStrings((byte[])string_list.get(i));
					delta=StringMapper.unpackStrings(str,tbl,new_xdim*new_ydim,channel_length[j]);
					for(int k=1;k<delta.length;k++) delta[k]+=channel_delta_min[j];
				}
				else
				{
					int width=channel_compressed_length[j];
					byte[] db=(byte[])delta_list.get(i);
					delta=new int[new_xdim*new_ydim]; delta[0]=0;
					for(int k=1;k<delta.length;k++){
						int base=k*width,v=0;
						for(int b=0;b<width;b++) v|=(db[base+b]&0xFF)<<(8*b);
						delta[k]=v+channel_delta_min[j];
					}
				}
				int[] ch=reconstructChannel(delta,new_xdim,new_ydim,channel_init[j],delta_type,i);
				if(j>2)for(int k=0;k<ch.length;k++)ch[k]+=channel_min[j];
				if(pixel_shift==0)dqcl.add(pixel_quant==0?ch:ResizeMapper.resize(ch,new_xdim,image_xdim,image_ydim));
				else{int[]s=DeltaMapper.shift(ch,pixel_shift);dqcl.add(pixel_quant==0?s:ResizeMapper.resize(s,new_xdim,image_xdim,image_ydim));}
			}

			// ---- RGB assembly -----------------------------------------------
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
			final ArrayList<int[]> fqcl=qcl; final int[] fcid=channel_id;
			final int cdnx=new_xdim, cdny=new_ydim;
			new Thread(()->SimpleWriter.this.collectData(fqcl,fcid,cdnx,cdny,false)).start();
		}
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
				out.writeByte(min_set_id);  out.writeByte(delta_type);
				out.writeByte(entropy_type); out.writeByte(data_type);

				for(int i=0;i<3;i++){
					int j=channel_id[i];
					out.writeInt(channel_min[j]); out.writeInt(channel_init[j]); out.writeInt(channel_delta_min[j]);
					out.writeInt(channel_length[j]); out.writeInt(channel_compressed_length[j]); out.writeByte(channel_iterations[i]);

					int map_bits=0;
					// Map (for scanline/map delta types)
					if(delta_type>=6){
						byte[] map=(byte[])map_list.get(i); int[] map_int=new int[map.length];
						for(int k=0;k<map.length;k++) map_int[k]=map[k]&0xFF;
						int map0=map_int[0];
						ArrayList dsl=StringMapper.getStringList(map_int,true);
						int dmin=(int)dsl.get(0); int[] tbl=(int[])dsl.get(2);
						byte[] mstr=(byte[])dsl.get(3); int bl=StringMapper.getBitlength(mstr);
						map_bits=bl;
						out.writeInt(map0-dmin); out.writeInt(map.length);
						writeTable(out,tbl); out.writeInt(dmin); out.writeInt(bl);
						out.write(mstr,0,StringMapper.getBytelength(bl));
					}

					// Table: always present (empty for Integer)
					writeTable(out,(int[])table_list.get(i));

					byte[] payload=(data_type==0)?(byte[])string_list.get(i):(byte[])delta_list.get(i);

					// Wrap to count bytes written for this channel's entropy payload
					java.io.ByteArrayOutputStream baos=new java.io.ByteArrayOutputStream();
					DataOutputStream dout=new DataOutputStream(baos);

					if(entropy_type==0)
					{
						// LZ77 — same for String and Integer
						Deflater def=new Deflater(Deflater.BEST_COMPRESSION);
						byte[] zipped=new byte[2*payload.length+64]; def.setInput(payload); def.finish();
						int zl=def.deflate(zipped); def.end();
						dout.writeInt(payload.length); dout.writeInt(zl); dout.write(zipped,0,zl);
					}
					else if(entropy_type==1)
					{
						// Huffman
						if(data_type==0)
						{
							// String: byte symbols (0-255)
							int[] pi=new int[payload.length];
							for(int k=0;k<payload.length;k++) pi[k]=payload[k]&0xFF;
							encodeHuffman(dout,pi);
						}
						else
						{
							// Integer: shifted delta values as int symbols directly
							int width=channel_compressed_length[j];
							int size=channel_length[j];
							int[] pi=new int[size]; pi[0]=0;
							for(int k=1;k<size;k++){
								int base=k*width,v=0;
								for(int b=0;b<width;b++) v|=(payload[base+b]&0xFF)<<(8*b);
								pi[k]=v;
							}
							encodeHuffman(dout,pi);
						}
					}
					else
					{
						// Arithmetic — same for String and Integer (bytes are bytes)
						int min_seg=500+pixel_segment*500;
						int n_segs=(pixel_segment>=10)?1:Math.max(1,payload.length/min_seg);
						int seg_len=payload.length/n_segs, odd_len=seg_len+payload.length%n_segs;
						byte[][] segs=new byte[n_segs][]; int[][] freqs=new int[n_segs][256];
						for(int m=0;m<n_segs;m++) segs[m]=new byte[m<n_segs-1?seg_len:odd_len];
						int pos=0;
						for(int m=0;m<n_segs;m++) for(int nn=0;nn<segs[m].length;nn++){segs[m][nn]=payload[pos];freqs[m][payload[pos]&0xFF]++;pos++;}
						int fmax=0; for(int[] row:freqs)for(int v:row)if(v>fmax)fmax=v;
						int lt=(fmax<256)?0:(fmax<65536)?1:2; int bpe=(lt==0)?1:(lt==1)?2:4;
						byte[] fb=new byte[n_segs*256*bpe];
						for(int m=0;m<n_segs;m++) for(int k=0;k<256;k++){int v=freqs[m][k];int base=m*256*bpe+k*bpe;for(int b=0;b<bpe;b++)fb[base+b]=(byte)(v>>(8*b));}
						Deflater def=new Deflater(Deflater.BEST_COMPRESSION);
						byte[] zf=new byte[fb.length+64]; def.setInput(fb); def.finish(); int zl=def.deflate(zf); def.end();
						dout.writeInt(payload.length); dout.writeInt(n_segs); dout.writeInt(lt); dout.writeInt(zl); dout.write(zf,0,zl);
						int n_procs=Math.max(1,Math.min(n_segs,Runtime.getRuntime().availableProcessors()));
						long arith_start=System.nanoTime();
						if(entropy_type==2)
						{
							// Slow arithmetic (Fenwick)
							BigInteger[][] slow_results=new BigInteger[n_segs][2];
							Thread[] at=new Thread[n_procs];
							for(int p=0;p<n_procs;p++){
								final int fp=p;
								at[p]=new Thread(()->{
									for(int m=fp;m<n_segs;m+=n_procs)
										slow_results[m]=ArithmeticMapper.getIntervalValueFenwick(segs[m],freqs[m]);
								});
								at[p].start();
							}
							try{for(Thread t:at)t.join();}catch(InterruptedException e){Thread.currentThread().interrupt();}
							long arith_ms=(System.nanoTime()-arith_start)/1_000_000;
							System.out.println(String.format("  Slow arithmetic encode: %d segs, %d threads, %d ms", n_segs, n_procs, arith_ms));
							for(int m=0;m<n_segs;m++){
								byte[] b0=slow_results[m][0].toByteArray(); dout.writeInt(b0.length); dout.write(b0,0,b0.length);
								byte[] b1=slow_results[m][1].toByteArray(); dout.writeInt(b1.length); dout.write(b1,0,b1.length);
							}
						}
						else
						{
							// Fast arithmetic (Fenwick)
							byte[][] fast_results=new byte[n_segs][];
							Thread[] at=new Thread[n_procs];
							for(int p=0;p<n_procs;p++){
								final int fp=p;
								at[p]=new Thread(()->{
									for(int m=fp;m<n_segs;m+=n_procs)
										fast_results[m]=ArithmeticMapper.getIntervalValueFastFenwick(segs[m],freqs[m]);
								});
								at[p].start();
							}
							try{for(Thread t:at)t.join();}catch(InterruptedException e){Thread.currentThread().interrupt();}
							long arith_ms=(System.nanoTime()-arith_start)/1_000_000;
							System.out.println(String.format("  Fast arithmetic encode: %d segs, %d threads, %d ms", n_segs, n_procs, arith_ms));
							for(int m=0;m<n_segs;m++){
								dout.writeInt(fast_results[m].length); dout.write(fast_results[m],0,fast_results[m].length);
							}
						}
					}
					dout.flush();
					byte[] chan_bytes=baos.toByteArray();
					out.write(chan_bytes);

					// Print per-channel stats
					int entropy_bits;
					if(data_type==0)
					{
						// String: channel_length is already the entropy-coded bit count
						entropy_bits=channel_length[j];
					}
					else
					{
						// Integer: compute Shannon entropy of the delta value distribution
						int[] freq=new int[256];
						for(int k=0;k<payload.length;k++) freq[payload[k]&0xFF]++;
						entropy_bits=(int)Math.floor(CodeMapper.getShannonLimit(freq));
					}
					int compressed_bits=chan_bytes.length*8;
					String ch_name=channel_string[channel_id[i]];
					ch_name = Character.toUpperCase(ch_name.charAt(0)) + ch_name.substring(1);
					if(map_bits>0)
						System.out.println(String.format("%-12s delta: %10d   map: %8d   actual: %10d",
							ch_name, entropy_bits, map_bits, compressed_bits));
					else
						System.out.println(String.format("%-12s delta: %10d                  actual: %10d",
							ch_name, entropy_bits, compressed_bits));
				}
				out.flush(); out.close();
				double rate=(double)new File("foo").length()/(image_xdim*image_ydim*3);
				System.out.println("Original rate: "+String.format("%.4f",file_compression_rate));
				System.out.println("Output rate:   "+String.format("%.4f",rate));
				System.out.println();
			} catch(Exception e){System.out.println("Save error: "+e); e.printStackTrace();}
		}

		private void encodeHuffman(DataOutputStream out, int[] symbols) throws IOException
		{
			ArrayList hl=StringMapper.getHistogram(symbols);
			int pmin=(int)hl.get(0); int[] hist=(int[])hl.get(1);
			int[] rt=StringMapper.getRankTable(hist);
			for(int k=0;k<symbols.length;k++) symbols[k]-=pmin;
			int n=hist.length;
			ArrayList<Integer> fl=new ArrayList<>(); for(int v:hist)fl.add(v);
			java.util.Collections.sort(fl,java.util.Comparator.reverseOrder());
			int[] freq=new int[n]; for(int k=0;k<n;k++)freq[k]=fl.get(k);
			byte[] hl2=CodeMapper.getHuffmanLength2(freq); int[] hc=CodeMapper.getCanonicalCode(hl2);
			ArrayList pl=CodeMapper.packCode(symbols,rt,hc,hl2); byte[] pb=(byte[])pl.get(0); int hbl=(int)pl.get(1);
			writeTable(out,rt); out.writeInt(pmin);
			ArrayList ltl=CodeMapper.packLengthTable(hl2); int ltn=(int)ltl.get(0);
			byte ltinit=(byte)ltl.get(1); byte ltmax=(byte)ltl.get(2); byte[] ltd=(byte[])ltl.get(3);
			out.writeInt(ltn); out.writeByte(ltinit); out.writeByte(ltmax);
			out.writeByte(ltd.length); out.write(ltd,0,ltd.length);
			out.writeInt(hbl); out.writeInt(pb.length); out.write(pb,0,pb.length);
		}
	}
}