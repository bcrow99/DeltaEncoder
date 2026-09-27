import java.awt.*;
import java.awt.event.*;
import java.awt.geom.AffineTransform;
import java.awt.image.*;
import java.io.*;
import javax.imageio.ImageIO;
import javax.swing.*;

// ViewerSupport version 1.0
//
// The window code shared by the writers and readers: an image window with
// zoom and scrolling, the HiDPI font fallback, image loading, slider
// dialogs and a few small helpers.
//
// One ViewerSupport is one window. The program exits when the last one
// closes.
public class ViewerSupport
{
	public static final double ZOOM_FACTOR = 1.25;
	public static final double ZOOM_MIN    = 0.05;
	public static final double ZOOM_MAX    = 32.0;

	// Largest dimension the file formats store (an unsigned short).
	public static final int MAX_DIM = 65535;

	// Set by applyHiDpiFontScaleIfNeeded() at startup (1.0 = no override).
	// Scales the allowances for window borders and sets the 100% zoom level.
	public static double hidpi_scale = 1.0;

	private static int open_windows = 0;
	private static int next_offset  = 0;

	public final JFrame      frame;
	public final JScrollPane scroll_pane;

	private final Canvas  canvas;
	private final String  title;
	private final int     xdim, ydim;
	private BufferedImage image;
	private BufferedImage scaled;   // image at zoom_scale, cached when zoom_scale < 1
	private String        status;   // shown in the title instead of the zoom level
	private double        zoom_scale;

	// ---- Startup ------------------------------------------------------------

	// HiDPI font-scale fallback. Java detects HiDPI scaling itself on Windows
	// (so this is a no-op there); on Linux/X11 it often misses it and renders
	// at an assumed 96 DPI. This detects that case and scales the Swing
	// fonts to match. Must run before any Swing component is created.
	public static void applyHiDpiFontScaleIfNeeded()
	{
		double scale = detectMissingUiScale();
		hidpi_scale  = scale;
		if(scale <= 1.01) return;

		UIDefaults defaults = UIManager.getLookAndFeelDefaults();
		for(Object key : new java.util.Vector<Object>(defaults.keySet()))
		{
			Object value = defaults.get(key);
			if(value instanceof Font)
			{
				Font scaled_font = ((Font) value).deriveFont((float)(((Font) value).getSize() * scale));
				defaults.put(key, scaled_font);
				UIManager.put(key, scaled_font);
			}
		}
	}

	// The extra UI scale to apply on top of Java's own, or 1.0 if none is
	// needed. Checked in order: Java's own transform (> 1 means Java already
	// handled it), GDK_SCALE, Xft.dpi, then Toolkit.getScreenResolution(),
	// which is least reliable (it can report a hardcoded 96 DPI).
	private static double detectMissingUiScale()
	{
		try
		{
			GraphicsConfiguration gc = GraphicsEnvironment.getLocalGraphicsEnvironment()
				.getDefaultScreenDevice().getDefaultConfiguration();
			if(gc.getDefaultTransform().getScaleX() > 1.01) return 1.0;

			String gdk_scale = System.getenv("GDK_SCALE");
			if(gdk_scale != null)
			{
				try { double s = Double.parseDouble(gdk_scale.trim()); if(s >= 1.25) return s; }
				catch(NumberFormatException e) { }
			}

			Object xft_dpi = Toolkit.getDefaultToolkit().getDesktopProperty("gnome.Xft/DPI");
			if(xft_dpi instanceof Integer)
			{
				double s = ((Integer) xft_dpi) / 1024.0 / 96.0;   // the value is DPI*1024
				if(s >= 1.25) return s;
			}

			double s = Toolkit.getDefaultToolkit().getScreenResolution() / 96.0;
			return (s >= 1.25) ? s : 1.0;
		}
		catch(Exception e)
		{
			return 1.0;   // never let detection break startup
		}
	}

	// Reads any image ImageIO can read, as TYPE_INT_RGB (alpha is dropped;
	// gray images keep their sample values). Throws IOException with a
	// message for the user if the file can't be used.
	public static BufferedImage readImage(String filename) throws IOException
	{
		BufferedImage src = ImageIO.read(new File(filename));
		if(src == null) throw new IOException(filename + " is not an image format Java can read.");
		int w = src.getWidth(), h = src.getHeight();
		if(w < DeltaMapper.MIN_DIM || h < DeltaMapper.MIN_DIM)
			throw new IOException(filename + " is " + w + " x " + h + "; images must be at least "
				+ DeltaMapper.MIN_DIM + " x " + DeltaMapper.MIN_DIM + ".");
		if(w > MAX_DIM || h > MAX_DIM)
			throw new IOException(filename + " is " + w + " x " + h + "; the largest supported dimension is " + MAX_DIM + ".");

		int[] pixel = new int[w * h];
		if(src.getType() == BufferedImage.TYPE_BYTE_GRAY)
		{
			Raster raster = src.getRaster();
			int[] gray = raster.getSamples(0, 0, w, h, 0, (int[]) null);
			for(int k = 0; k < pixel.length; k++) pixel[k] = (gray[k] << 16) | (gray[k] << 8) | gray[k];
		}
		else src.getRGB(0, 0, w, h, pixel, 0, w);

		BufferedImage rgb = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
		rgb.setRGB(0, 0, w, h, pixel, 0, w);
		return rgb;
	}

	// Prints the message and shows it in a dialog (when there is a screen).
	public static void showError(Component parent, String message)
	{
		System.out.println(message);
		if(!GraphicsEnvironment.isHeadless())
			JOptionPane.showMessageDialog(parent, message, "Error", JOptionPane.ERROR_MESSAGE);
	}

	// For a window that failed to open: exits if no other window is open.
	public static void exitIfNoWindows()
	{
		if(open_windows == 0) System.exit(1);
	}

	// ---- Small helpers --------------------------------------------------------

	// Formats a nanosecond duration with 3 decimals in the smallest unit that
	// keeps the whole part under 1000, e.g. "543.210 usecs".
	public static String formatDuration(long nanos)
	{
		String[] units    = { "ns", "usecs", "ms", "secs", "min" };
		double[] divisors = { 1.0, 1e3, 1e6, 1e9, 60e9 };

		int idx = units.length - 1;
		for(int i = 0; i < units.length; i++)
			if(nanos / divisors[i] < 1000.0) { idx = i; break; }

		double value = nanos / divisors[idx];
		if(value >= 999.9995 && idx < units.length - 1) value = nanos / divisors[++idx];
		return String.format("%.3f %s", value, units[idx]);
	}

	// Runs r on the Swing thread and waits for it (directly if already there).
	public static void runOnEdt(Runnable r)
	{
		if(SwingUtilities.isEventDispatchThread()) { r.run(); return; }
		try { SwingUtilities.invokeAndWait(r); }
		catch(java.lang.reflect.InvocationTargetException e) { throw new RuntimeException(e.getCause()); }
		catch(InterruptedException e) { Thread.currentThread().interrupt(); }
	}

	// Runs body(0..n-1) on the shared pool; exceptions reach the caller.
	public static void parallel(int n, java.util.function.IntConsumer body)
	{
		java.util.stream.IntStream.range(0, n).parallel().forEach(body);
	}

	// A menu item that opens a small dialog with a slider (lo..hi) and its
	// value. onChange gets every new value. If ref is given, ref[0] is set to
	// the slider so the caller can move it.
	public static JMenuItem makeSliderDialog(JFrame parent, String title, int lo, int hi, int init,
	                                         java.util.function.IntConsumer onChange, JSlider[] ref)
	{
		JMenuItem  item   = new JMenuItem(title);
		JDialog    dialog = new JDialog(parent, title);
		JSlider    slider = new JSlider(lo, hi, init);
		JTextField field  = new JTextField(3);
		if(ref != null) ref[0] = slider;
		field.setText(" " + init + " ");
		slider.addChangeListener(e -> { int v = slider.getValue(); field.setText(" " + v + " "); onChange.accept(v); });
		JPanel panel = new JPanel(new BorderLayout());
		panel.add(slider, BorderLayout.CENTER);
		panel.add(field, BorderLayout.EAST);
		dialog.add(panel);
		item.addActionListener(e ->
		{
			Point p = parent.getLocation();
			dialog.setLocation(p.x, p.y - 60);
			dialog.pack();
			dialog.setVisible(true);
		});
		return item;
	}

	// ---- The image window ---------------------------------------------------

	// A window titled `title` for an xdim x ydim image, sized to fit 70% of
	// the screen. Add menus to frame's menu bar (see makeViewMenu), then
	// call show().
	public ViewerSupport(String title, int xdim, int ydim)
	{
		this.title = title;
		this.xdim  = xdim;
		this.ydim  = ydim;

		Dimension screen = Toolkit.getDefaultToolkit().getScreenSize();
		int max_w = (int)(screen.width * 0.70) - (int)(40 * hidpi_scale);
		int max_h = (int)(screen.height * 0.70) - (int)(80 * hidpi_scale);
		zoom_scale = Math.min(hidpi_scale, Math.min((double) max_w / xdim, (double) max_h / ydim));

		canvas      = new Canvas();
		scroll_pane = new JScrollPane(canvas, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
		scroll_pane.getVerticalScrollBar().setUnitIncrement(16);
		scroll_pane.getHorizontalScrollBar().setUnitIncrement(16);
		scroll_pane.addMouseWheelListener(e ->
		{
			if(!e.isControlDown()) { scroll_pane.dispatchEvent(e); return; }
			Point mouse = e.getPoint();
			zoomAround(e.getWheelRotation() < 0 ? ZOOM_FACTOR : 1.0 / ZOOM_FACTOR, mouse.x, mouse.y);
		});

		frame = new JFrame();
		frame.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
		frame.addWindowListener(new WindowAdapter()
		{
			public void windowClosing(WindowEvent e) { frame.dispose(); if(--open_windows == 0) System.exit(0); }
		});
		frame.getContentPane().add(scroll_pane, BorderLayout.CENTER);
		frame.setJMenuBar(new JMenuBar());
		updateTitle();
	}

	// Sizes the window to the image, places it (each new window a little
	// offset from the last) and shows it.
	public void show()
	{
		Dimension screen = Toolkit.getDefaultToolkit().getScreenSize();
		frame.setSize(Math.min((int)(xdim * zoom_scale) + (int)(40 * hidpi_scale), (int)(screen.width * 0.70)),
		              Math.min((int)(ydim * zoom_scale) + (int)(80 * hidpi_scale), (int)(screen.height * 0.70)));
		int offset = next_offset;
		next_offset = (offset + 30) % 270;
		frame.setLocation((screen.width - frame.getWidth()) / 2 + offset, (screen.height - frame.getHeight()) / 2 + offset);
		open_windows++;
		frame.setVisible(true);
	}

	public JMenu makeViewMenu()
	{
		JMenu menu = new JMenu("View");
		menu.add(menuItem("Zoom In",            KeyEvent.VK_EQUALS, () -> zoomBy(ZOOM_FACTOR)));
		menu.add(menuItem("Zoom Out",           KeyEvent.VK_MINUS,  () -> zoomBy(1.0 / ZOOM_FACTOR)));
		menu.add(menuItem("Fit to Window",      KeyEvent.VK_0,      () -> fitToWindow()));
		menu.add(menuItem("Actual Size (100%)", KeyEvent.VK_1,      () -> setZoom(hidpi_scale)));
		return menu;
	}

	private static JMenuItem menuItem(String name, int key, Runnable action)
	{
		JMenuItem item = new JMenuItem(name);
		item.setAccelerator(KeyStroke.getKeyStroke(key, InputEvent.CTRL_DOWN_MASK));
		item.addActionListener(e -> action.run());
		return item;
	}

	// Enables or disables every menu except View (e.g. while a background
	// job runs that Apply or Save must not overlap).
	public void setMenusEnabled(boolean enabled)
	{
		JMenuBar bar = frame.getJMenuBar();
		for(int i = 0; i < bar.getMenuCount(); i++)
		{
			JMenu menu = bar.getMenu(i);
			if(menu != null && !menu.getText().equals("View")) menu.setEnabled(enabled);
		}
	}

	public void setImage(BufferedImage image)
	{
		this.image  = image;
		this.scaled = null;
		canvas.repaint();
	}

	// Shown in the title in place of the zoom level; null shows the zoom.
	public void setStatus(String status)
	{
		this.status = status;
		updateTitle();
	}

	// Zooms so the whole image fits the window (at most 100%), then shrinks
	// the window if it is larger than the image needs.
	public void fitAndShrink()
	{
		Dimension fit = scroll_pane.getViewport().getSize();
		if(fit.width > 0 && fit.height > 0)
			setZoom(Math.min(hidpi_scale, Math.min((double) fit.width / xdim, (double) fit.height / ydim)));
		Dimension view   = scroll_pane.getViewport().getSize();
		Dimension screen = Toolkit.getDefaultToolkit().getScreenSize();
		int extra_w = frame.getWidth() - view.width, extra_h = frame.getHeight() - view.height;
		frame.setSize(Math.min((int)(xdim * zoom_scale) + extra_w, (int)(screen.width * 0.70)),
		              Math.min((int)(ydim * zoom_scale) + extra_h, (int)(screen.height * 0.70)));
	}

	public void fitToWindow()
	{
		Dimension view = scroll_pane.getViewport().getSize();
		if(view.width > 0 && view.height > 0) setZoom(Math.min((double) view.width / xdim, (double) view.height / ydim));
	}

	public void zoomBy(double factor)
	{
		Dimension view = scroll_pane.getViewport().getSize();
		zoomAround(factor, view.width / 2, view.height / 2);
	}

	public void setZoom(double zoom)
	{
		zoom_scale = Math.max(ZOOM_MIN, Math.min(ZOOM_MAX, zoom));
		scaled     = null;
		canvas.revalidate();
		canvas.repaint();
		updateTitle();
	}

	// Zooms keeping the image point under (x, y) -- viewport coordinates --
	// in place.
	private void zoomAround(double factor, int x, int y)
	{
		double old  = zoom_scale;
		double zoom = Math.max(ZOOM_MIN, Math.min(ZOOM_MAX, zoom_scale * factor));
		if(zoom == old) return;
		JViewport viewport = scroll_pane.getViewport();
		Point     position = viewport.getViewPosition();
		double    r        = zoom / old;
		setZoom(zoom);
		scroll_pane.validate();
		viewport.setViewPosition(new Point(Math.max(0, (int)((position.x + x) * r) - x), Math.max(0, (int)((position.y + y) * r) - y)));
	}

	private void updateTitle()
	{
		frame.setTitle(title + "  [" + (status != null ? status : Math.round(zoom_scale * 100) + "%") + "]");
	}

	// Draws the image at zoom_scale. Below 100% a scaled copy is cached (it
	// is smaller than the image); above, the visible part is scaled while
	// drawing, so zooming in never allocates a huge image.
	private class Canvas extends JPanel
	{
		Canvas() { setOpaque(true); }

		@Override public Dimension getPreferredSize()
		{
			return new Dimension(Math.max(1, (int)(xdim * zoom_scale)), Math.max(1, (int)(ydim * zoom_scale)));
		}

		@Override protected void paintComponent(Graphics g)
		{
			super.paintComponent(g);
			BufferedImage src = image;
			if(src == null) return;
			int w = Math.max(1, (int)(xdim * zoom_scale)), h = Math.max(1, (int)(ydim * zoom_scale));
			if(zoom_scale == 1.0) { g.drawImage(src, 0, 0, null); return; }
			if(zoom_scale < 1.0)
			{
				if(scaled == null || scaled.getWidth() != w || scaled.getHeight() != h)
				{
					AffineTransform t = AffineTransform.getScaleInstance(zoom_scale, zoom_scale);
					scaled = new AffineTransformOp(t, AffineTransformOp.TYPE_BILINEAR).filter(src, new BufferedImage(w, h, src.getType()));
				}
				g.drawImage(scaled, 0, 0, null);
				return;
			}
			Graphics2D g2 = (Graphics2D) g.create();
			g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
			g2.drawImage(src, 0, 0, w, h, null);
			g2.dispose();
		}
	}
}
