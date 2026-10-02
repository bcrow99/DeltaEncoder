import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Launcher -- the Main-Class of DeltaEncoder.jar, which the GitHub Action
 * (.github/workflows/build.yml) builds on every push. It runs one of the
 * programs in the jar, chosen by name:
 *
 *   java -jar DeltaEncoder.jar                         lists the programs
 *   java -jar DeltaEncoder.jar DeltaWriter image.png   runs DeltaWriter with image.png
 *   java -jar DeltaEncoder.jar deltareader foo         (the name's case doesn't matter)
 *
 * Everything in the jar is compiled with --release 8, so it runs on any
 * Java 8 or later, and a runtime (JRE) is enough.
 */
public class Launcher
{
	public static void main(String[] args) throws Throwable
	{
		List<String> programs = findPrograms();
		if(args.length == 0 || args[0].equals("--help") || args[0].equals("-h"))
		{
			usage(programs);
			return;
		}

		String name = args[0].endsWith(".java") ? args[0].substring(0, args[0].length() - 5) : args[0];
		String program = null;
		for(String p : programs) if(p.equals(name)) program = p;
		if(program == null) for(String p : programs) if(p.equalsIgnoreCase(name)) program = p;
		if(program == null)
		{
			System.err.println("No program called " + name + " in this jar.");
			usage(programs);
			System.exit(1);
		}

		String[] rest = new String[args.length - 1];
		System.arraycopy(args, 1, rest, 0, rest.length);
		Method main = Class.forName(program).getMethod("main", String[].class);
		try { main.invoke(null, (Object) rest); }
		catch(InvocationTargetException e) { throw e.getCause(); }
	}

	static void usage(List<String> programs)
	{
		System.out.println("Usage: java -jar " + jarName() + " <program> [arguments...]");
		System.out.println("Programs:");
		StringBuilder line = new StringBuilder("  ");
		for(String p : programs)
		{
			if(line.length() + p.length() > 76) { System.out.println(line); line = new StringBuilder("  "); }
			line.append(' ').append(p);
		}
		System.out.println(line);
		String built = buildVersion();
		if(built != null) System.out.println("Build: " + built + ", running on Java " + System.getProperty("java.version"));
	}

	// Top-level classes in this jar that have public static void main(String[]).
	static List<String> findPrograms()
	{
		List<String> programs = new ArrayList<String>();
		File jar = ownJar();
		if(jar == null) return programs;
		try
		{
			ZipFile zip = new ZipFile(jar);
			try
			{
				for(Enumeration<? extends ZipEntry> en = zip.entries(); en.hasMoreElements(); )
				{
					String entry = en.nextElement().getName();
					if(!entry.endsWith(".class") || entry.indexOf('$') >= 0 || entry.startsWith("META-INF")) continue;
					String cls = entry.substring(0, entry.length() - 6).replace('/', '.');
					if(cls.equals("Launcher") || cls.equals("SourceLauncher")) continue;
					try
					{
						// Load without running any static initializers.
						Class<?> c = Class.forName(cls, false, Launcher.class.getClassLoader());
						Method m = c.getMethod("main", String[].class);
						if(Modifier.isStatic(m.getModifiers()) && m.getReturnType() == void.class) programs.add(cls);
					}
					catch(Throwable t) { }   // no main, or not loadable: not a program
				}
			}
			finally { zip.close(); }
		}
		catch(Exception e) { }
		Collections.sort(programs);
		return programs;
	}

	static File ownJar()
	{
		try
		{
			File f = new File(Launcher.class.getProtectionDomain().getCodeSource().getLocation().toURI());
			return f.isFile() ? f : null;
		}
		catch(Exception e) { return null; }
	}

	// The Implementation-Version line the workflow writes into the manifest.
	static String buildVersion()
	{
		File jar = ownJar();
		if(jar == null) return null;
		try
		{
			java.util.jar.JarFile j = new java.util.jar.JarFile(jar);
			try { return j.getManifest() == null ? null : j.getManifest().getMainAttributes().getValue("Implementation-Version"); }
			finally { j.close(); }
		}
		catch(Exception e) { return null; }
	}

	static String jarName()
	{
		File jar = ownJar();
		return (jar == null) ? "DeltaEncoder.jar" : jar.getName();
	}
}
