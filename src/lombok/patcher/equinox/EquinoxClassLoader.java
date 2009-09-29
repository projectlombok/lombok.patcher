/*
 * Copyright © 2009 Reinier Zwitserloot and Roel Spilker.
 * 
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * 
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package lombok.patcher.equinox;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLDecoder;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.jar.JarFile;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;

import lombok.SneakyThrows;
import lombok.patcher.Hook;
import lombok.patcher.MethodTarget;
import lombok.patcher.ScriptManager;
import lombok.patcher.StackRequest;
import lombok.patcher.scripts.ScriptBuilder;

/**
 * "Fixes" the Equinox class loader so that classes that start with a certain package prefix are loaded in such a way that
 * these classes are found from alternative locations, but can still load the classes provided by the OSGi bundle seamlessly.
 * 
 * This is black voodoo magic.
 * 
 * To Use:<ul>
 * <li>Grab the instance via {@link #getInstance()}
 * <li>Add package names that contain your patches as well as all code called by your patches that needs to interop directly
 * with the thing you are patching (example: {@code "lombok."}). Note that {@code lombok.patcher.} is always included.
 * <li>Optionally add more class paths (jars and directories) to look for your patches and its dependencies. Whatever
 * jar or directory is providing the EquinoxClassLoader class is already available, and if the system classloader can find it,
 * it'll work out as well.
 * <li>Set up a {@link lombok.patcher.ScriptManager} and then call {@link #registerScripts(lombok.patcher.ScriptManager)} to
 * register the patches made to the equinox class loader system.
 * </ul>
 */
public class EquinoxClassLoader extends ClassLoader {
	private static EquinoxClassLoader hostLoader = new EquinoxClassLoader();
	private static Method resolveMethod;	//cache
	private final List<String> prefixes = new ArrayList<String>();
	private final List<File> classpath = new ArrayList<File>();
	private final List<ClassLoader> subLoaders = new ArrayList<ClassLoader>();
	private final Set<String> cantFind = new HashSet<String>();
	
	private EquinoxClassLoader() {
		this.classpath.add(findOurClassPath());
		this.prefixes.add("lombok.patcher.");
	}
	
	public static EquinoxClassLoader getInstance() {
		return hostLoader;
	}
	
	public void addPrefix(String... prefixes) {
		this.prefixes.addAll(Arrays.asList(prefixes));
	}
	
	public void addClasspath(String file) {
		classpath.add(new File(file));
	}
	
	public void addSubLoader(ClassLoader loader) {
		if (!subLoaders.contains(loader)) subLoaders.add(loader);
	}
	
	public void registerScripts(ScriptManager manager) {
		final String SELF_NAME = getClass().getName().replace(".", "/");
		final String HOOK_DESC = "(Ljava/lang/ClassLoader;Ljava/lang/String;Z)";
		
		manager.addScript(ScriptBuilder.exitEarly()
				.target(new MethodTarget("org.eclipse.osgi.framework.adapter.core.AbstractClassLoader", "loadClass",
						"java.lang.Class", "java.lang.String", "boolean"))
				.decisionMethod(new Hook(SELF_NAME, "overrideLoadDecide", HOOK_DESC + "Z"))
				.valueMethod(new Hook(SELF_NAME, "overrideLoadResult", HOOK_DESC + "Ljava/lang/Class;"))
				.request(StackRequest.THIS, StackRequest.PARAM1, StackRequest.PARAM2).build());
		
		manager.addScript(ScriptBuilder.exitEarly()
				.target(new MethodTarget("org.eclipse.osgi.internal.baseadaptor.DefaultClassLoader", "loadClass",
						"java.lang.Class", "java.lang.String", "boolean"))
				.decisionMethod(new Hook(SELF_NAME, "overrideLoadDecide", HOOK_DESC + "Z"))
				.valueMethod(new Hook(SELF_NAME, "overrideLoadResult", HOOK_DESC + "Ljava/lang/Class;"))
				.request(StackRequest.THIS, StackRequest.PARAM1, StackRequest.PARAM2).build());
	}
	
	@SneakyThrows({IOException.class, URISyntaxException.class})
	private static File findOurClassPath() {
		URI uri = EquinoxClassLoader.class.getResource("EquinoxClassLoader.class").toURI();
		/* Jar-based? */ {
			Pattern p = Pattern.compile("^jar:file:([^\\!]+)\\!.*\\.class$");
			Matcher m = p.matcher(uri.toString());
			if (m.matches()) {
				String rawUri = m.group(1);
				return new File(URLDecoder.decode(rawUri, Charset.defaultCharset().name()));
			}
		}
		
		/* File-based? */ {
			String fullName = "/" + EquinoxClassLoader.class.getName().replace(".", "/") + ".class";
			Pattern p = Pattern.compile("^file:(.*)" + Pattern.quote(fullName) + "$");
			Matcher m = p.matcher(uri.toString());
			if (m.matches()) {
				String rawUri = m.group(1);
				return new File(URLDecoder.decode(rawUri, Charset.defaultCharset().name()));
			}
		}
		
		/* Give up and default to current dir as a hail mary. */
		return new File(".");
	}
	
	private void logLoadError(Throwable t) {
		t.printStackTrace();
	}
	
	/*
	 * Load order:
	 * First, try loading from the jar/dir that hosts EquinoxClassLoader
	 * Then, try any additional registered classpaths (jar/dir based).
	 * If this too fails, ask the system classloader.
	 * If the system classloader can't find it either, try loading via any registered subLoaders.
	 * If we still haven't found it, fail.
	 */
	@Override protected synchronized Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
		boolean controlLoad = false;
		for (String prefix : prefixes) {
			if (name.startsWith(prefix)) {
				controlLoad = true;
				break;
			}
		}
		
		Class<?> c = null;
		
		System.out.printf("ATTEMPTING LOAD [forced: %b]: %s\n", controlLoad, name);
		
		for (File file : classpath) {
			if (file.isFile()) {
				try {
					JarFile jf = new JarFile(file);
					ZipEntry entry = jf.getEntry(name);
					if (entry == null) continue;
					byte[] classData = readStream(jf.getInputStream(entry));
					c = defineClass(name, classData, 0, classData.length);
					break;
				} catch (IOException e) {
					logLoadError(e);
				}
			} else {
				File target = new File(file, name);
				if (!target.exists()) continue;
				try {
					byte[] classData = readStream(new FileInputStream(target));
					c = defineClass(name, classData, 0, classData.length);
					break;
				} catch (IOException e) {
					logLoadError(e);
				}
			}
		}
		
		if (c != null) {
			if (resolve) resolveClass(c);
			return c;
		}
		
		System.out.println("NOT FOUND LOCALLY - trying super.");
		
		if (controlLoad) {
			try {
				byte[] classData = readStream(super.getResourceAsStream(name.replace(".", "/") + ".class"));
				c = defineClass(name, classData, 0, classData.length);
				if (resolve) resolveClass(c);
				return c;
			} catch (Exception ignore) {}
		} else {
			try {
				return super.loadClass(name, resolve);
			} catch (ClassNotFoundException ignore) {}
		}
		
		System.out.println("Still not found - marking this class as unfindable");
		cantFind.add(name);
		
		for (ClassLoader subLoader : subLoaders) {
			System.out.println("TRYING SUBLOADER: " + subLoader.getClass());
			try {
				c = subLoader.loadClass(name);
				System.out.println("SUBLOADER FOUND: " + c);
				if (resolve) {
					if (resolveMethod == null) {
						try {
							Method m = ClassLoader.class.getDeclaredMethod("resolveClass", Class.class);
							m.setAccessible(true);
							resolveMethod = m;
						} catch (Exception ignore) {}
					}
					
					if (resolveMethod != null) {
						try {
							resolveMethod.invoke(subLoader, c);
						} catch (Exception ignore) {}
					}
				}
				return c;
			} catch (ClassNotFoundException ignore) {}
		}
		
		throw new ClassNotFoundException(name);
	}
	
	private static byte[] readStream(InputStream in) throws IOException {
		try {
			byte[] b = new byte[4096];
			ByteArrayOutputStream baos = new ByteArrayOutputStream();
			while (true) {
				int r = in.read(b);
				if (r == -1) break;
				baos.write(b, 0, r);
			}
			return baos.toByteArray();
		} finally {
			in.close();
		}
	}
	
	public static boolean overrideLoadDecide(ClassLoader original, String name, boolean resolve) {
		if (hostLoader.cantFind.contains(name)) return false;
		for (String prefix : hostLoader.prefixes) {
			if (name.startsWith(prefix)) return true;
		}
		
		return false;
	}
	
	public static Class<?> overrideLoadResult(ClassLoader original, String name, boolean resolve) throws ClassNotFoundException {
		System.out.printf("OVERRIDING CLASS LOAD OF[%b]: %s\n", resolve, name);
		hostLoader.addSubLoader(original);
		return hostLoader.loadClass(name, resolve);
	}
	
}
