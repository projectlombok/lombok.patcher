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
import java.lang.ref.WeakReference;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.jar.JarFile;
import java.util.zip.ZipEntry;

import lombok.patcher.Hook;
import lombok.patcher.MethodTarget;
import lombok.patcher.ScriptManager;
import lombok.patcher.StackRequest;
import lombok.patcher.inject.LiveInjector;
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
		this.classpath.add(new File(LiveInjector.findPathJar(EquinoxClassLoader.class)));
		this.prefixes.add("lombok.patcher.");
	}
	
	public static EquinoxClassLoader getInstance() {
		return hostLoader;
	}
	
	public void addPrefix(String... additionalPrefixes) {
		this.prefixes.addAll(Arrays.asList(additionalPrefixes));
	}
	
	public void addClasspath(String file) {
		classpath.add(new File(file));
	}
	
	public void addSubLoader(ClassLoader loader) {
		if (!subLoaders.contains(loader)) subLoaders.add(loader);
	}
	
	private static final String SELF_NAME = "lombok/patcher/equinox/EquinoxClassLoader";
	private static final String HOOK_DESC = "(Ljava/lang/ClassLoader;Ljava/lang/String;Z)";
	
	public static void registerScripts(ScriptManager manager) {
		manager.addScript(ScriptBuilder.exitEarly()
				.target(new MethodTarget("org.eclipse.osgi.internal.baseadaptor.DefaultClassLoader", "loadClass",
						"java.lang.Class", "java.lang.String", "boolean"))
				.target(new MethodTarget("org.eclipse.osgi.framework.adapter.core.AbstractClassLoader", "loadClass",
						"java.lang.Class", "java.lang.String", "boolean"))
				.decisionMethod(new Hook(SELF_NAME, "overrideLoadDecide", HOOK_DESC + "Z"))
				.valueMethod(new Hook(SELF_NAME, "overrideLoadResult", HOOK_DESC + "Ljava/lang/Class;"))
				.request(StackRequest.THIS, StackRequest.PARAM1, StackRequest.PARAM2).build());
	}
	
	private void logLoadError(Throwable t) {
		t.printStackTrace();
	}
	
	private final Map<String, WeakReference<Class<?>>> defineCache = new HashMap<String, WeakReference<Class<?>>>();
	
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
		
		WeakReference<Class<?>> ref = defineCache.get(name);
		if (ref != null) {
			Class<?> result = ref.get();
			if (result != null) return result;
			defineCache.remove(name);
		}
		
		for (String prefix : prefixes) {
			if (name.startsWith(prefix)) {
				controlLoad = true;
				break;
			}
		}
		
		Class<?> c = null;
		
		for (File file : classpath) {
			if (file.isFile()) {
				try {
					JarFile jf = new JarFile(file);
					ZipEntry entry = jf.getEntry(name);
					if (entry == null) continue;
					byte[] classData = readStream(jf.getInputStream(entry));
					c = defineClass(name, classData, 0, classData.length);
					defineCache.put(name, new WeakReference<Class<?>>(c));
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
					defineCache.put(name, new WeakReference<Class<?>>(c));
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
		
		if (controlLoad) {
			try {
				byte[] classData = readStream(super.getResourceAsStream(name.replace(".", "/") + ".class"));
				c = defineClass(name, classData, 0, classData.length);
				defineCache.put(name, new WeakReference<Class<?>>(c));
				if (resolve) resolveClass(c);
				return c;
			} catch (Exception ignore) {} catch (UnsupportedClassVersionError e) {
				System.out.println("BAD CLASS VERSION TRYING TO LOAD: " + name);
				throw e;
			}
		} else {
			try {
				return super.loadClass(name, resolve);
			} catch (ClassNotFoundException ignore) {}
		}
		
		cantFind.add(name);
		
		for (ClassLoader subLoader : subLoaders) {
			try {
				c = subLoader.loadClass(name);
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
	
	/**
	 * Method used to patch equinox classloader.
	 * 
	 * @param original Parameter must be there for patching.
	 * @param name Parameter must be there for patching.
	 * @param resolve Parameter must be there for patching.
	 */
	public static boolean overrideLoadDecide(ClassLoader original, String name, boolean resolve) {
		if (hostLoader.cantFind.contains(name)) return false;
		for (String prefix : hostLoader.prefixes) {
			if (name.startsWith(prefix)) return true;
		}
		
		return false;
	}
	
	public static Class<?> overrideLoadResult(ClassLoader original, String name, boolean resolve) throws ClassNotFoundException {
		hostLoader.addSubLoader(original);
		return hostLoader.loadClass(name, resolve);
	}
}
