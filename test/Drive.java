import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import javax.swing.JFrame;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.SwingUtilities;

/**
 * Regression harness for the writers: opens one, lets its analysis finish,
 * sets some of its fields, applies, and saves, as a person would from the menus.
 *
 * <pre>
 * xvfb-run -a java -cp &lt;classes&gt; Drive &lt;Writer&gt; &lt;image&gt; &lt;out&gt; &lt;wait_s&gt; field=value...
 * </pre>
 *
 * It waits {@code wait_s} seconds, then until every menu has been enabled for
 * two seconds (the writers disable their menus while the analysis runs). It
 * then sets each {@code field=value} (int, boolean or double fields), calls
 * the writer's {@code apply()}, clicks File &gt; Save, and renames the file
 * {@code foo} that Save writes in the current folder to {@code out}.
 * Run jobs one after another: they all write {@code foo}.
 *
 * <p>Exit status: 0 when saved, 2 when setting a field, applying or saving
 * failed, 3 when no {@code foo} appeared.
 */
public class Drive
{
	/** Not instantiated: everything is static. */
	private Drive() { }

	/** The longest wait for the analysis after {@code wait_s}, in milliseconds. */
	static final long MAX_ANALYSIS_MS = 1800_000L;

	/**
	 * Runs one job.
	 *
	 * @param args writer class, image file, output file, seconds to wait,
	 *             then any number of {@code field=value}
	 * @throws Exception when the writer can't be created, or its analysis
	 *                   doesn't finish within {@link #MAX_ANALYSIS_MS}
	 */
	public static void main(String[] args) throws Exception
	{
		if(args.length < 4)
		{
			System.err.println("usage: java Drive <Writer> <image> <out> <wait_s> field=value...");
			System.exit(1);
		}
		String cls = args[0], image = args[1], out = args[2];
		int wait_s = Integer.parseInt(args[3]);
		new File("foo").delete();

		final Object[] w = new Object[1];
		SwingUtilities.invokeAndWait(() ->
		{
			try { w[0] = Class.forName(cls).getConstructor(String.class).newInstance(image); }
			catch(Exception e) { throw new RuntimeException(e); }
		});
		Object writer = w[0];
		Object view = field(writer, "view").get(writer);
		JFrame frame = (JFrame) field(view, "frame").get(view);

		Thread.sleep(wait_s * 1000L);
		waitForMenus(frame);

		final Exception[] err = new Exception[1];
		SwingUtilities.invokeAndWait(() ->
		{
			try
			{
				for(int i = 4; i < args.length; i++) setField(writer, args[i]);
				Method apply = writer.getClass().getDeclaredMethod("apply");
				apply.setAccessible(true);
				apply.invoke(writer);
				JMenuItem save = findSave(frame.getJMenuBar());
				if(save == null) throw new RuntimeException("no Save item");
				save.doClick();
			}
			catch(Exception e) { err[0] = e; }
		});
		if(err[0] != null) { err[0].printStackTrace(); System.exit(2); }

		File foo = new File("foo"), dst = new File(out);
		dst.delete();
		if(!foo.exists() || !foo.renameTo(dst)) { System.err.println("no foo written"); System.exit(3); }
		System.out.println("DRIVE " + out + " " + dst.length());
		System.exit(0);
	}

	/**
	 * Waits until every menu in the frame's menu bar has been enabled at four
	 * checks in a row, half a second apart.
	 *
	 * @param frame the writer's window
	 * @throws Exception when interrupted, or after {@link #MAX_ANALYSIS_MS}
	 */
	static void waitForMenus(JFrame frame) throws Exception
	{
		long deadline = System.currentTimeMillis() + MAX_ANALYSIS_MS;
		int  stable   = 0;
		while(stable < 4)
		{
			if(System.currentTimeMillis() > deadline) throw new RuntimeException("analysis did not finish");
			final boolean[] on = { true };
			SwingUtilities.invokeAndWait(() ->
			{
				JMenuBar bar = frame.getJMenuBar();
				for(int i = 0; i < bar.getMenuCount(); i++) if(!bar.getMenu(i).isEnabled()) on[0] = false;
			});
			stable = on[0] ? stable + 1 : 0;
			Thread.sleep(500);
		}
	}

	/**
	 * Sets one field of the writer from {@code name=value}.
	 *
	 * @param writer the writer
	 * @param assignment {@code name=value}
	 * @throws Exception when the field doesn't exist, has a type other than
	 *                   int, boolean or double, or the value doesn't parse
	 */
	static void setField(Object writer, String assignment) throws Exception
	{
		String[] kv = assignment.split("=", 2);
		if(kv.length != 2) throw new IllegalArgumentException("expected field=value: " + assignment);
		Field f = field(writer, kv[0]);
		if(f.getType() == int.class)          f.setInt(writer, Integer.parseInt(kv[1]));
		else if(f.getType() == boolean.class) f.setBoolean(writer, Boolean.parseBoolean(kv[1]));
		else if(f.getType() == double.class)  f.setDouble(writer, Double.parseDouble(kv[1]));
		else throw new IllegalArgumentException("unsupported field type: " + f);
	}

	/**
	 * Finds a field by name in the object's class or a superclass, and makes
	 * it accessible.
	 *
	 * @param o the object
	 * @param name the field's name
	 * @return the field
	 * @throws NoSuchFieldException when no class declares it
	 */
	static Field field(Object o, String name) throws NoSuchFieldException
	{
		for(Class<?> c = o.getClass(); c != null; c = c.getSuperclass())
		{
			try { Field f = c.getDeclaredField(name); f.setAccessible(true); return f; }
			catch(NoSuchFieldException e) { /* try the superclass */ }
		}
		throw new NoSuchFieldException(name);
	}

	/**
	 * Finds the menu item labelled "Save".
	 *
	 * @param bar the writer's menu bar
	 * @return the item, or null if there is none
	 */
	static JMenuItem findSave(JMenuBar bar)
	{
		for(int i = 0; i < bar.getMenuCount(); i++)
		{
			JMenu m = bar.getMenu(i);
			for(int j = 0; j < m.getItemCount(); j++)
			{
				JMenuItem it = m.getItem(j);
				if(it != null && "Save".equals(it.getText())) return it;
			}
		}
		return null;
	}
}
