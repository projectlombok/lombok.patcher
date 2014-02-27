/*
 * Copyright (C) 2009-2014 The Project Lombok Authors.
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
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.jar.JarFile;
import java.util.zip.ZipEntry;

import lombok.Cleanup;
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
	private static final ConcurrentMap<ClassLoader, EquinoxClassLoader> hostLoaders = new ConcurrentHashMap<ClassLoader, EquinoxClassLoader>();
	private static final EquinoxClassLoader coreLoader = new EquinoxClassLoader();
	private static final Method resolveMethod;
	private static final List<String> prefixes = new ArrayList<String>();
	private static final List<String> corePrefixes = new ArrayList<String>();
	
	private final List<File> classpath = new ArrayList<File>();
	private final List<ClassLoader> subLoaders = new ArrayList<ClassLoader>();
	private final ConcurrentHashMap<String, String> cantFind = new ConcurrentHashMap<String, String>();
	
	static {
		corePrefixes.add("lombok.patcher.");
		Method m = null;
		try {
			m = ClassLoader.class.getDeclaredMethod("resolveClass", Class.class);
			m.setAccessible(true);
		} catch (Exception ignore) {
			// This is bad, but other than just not resolving and hope it works out, there's nothing we can do.
		}
		resolveMethod = m;
	}
	
	private EquinoxClassLoader() {
		this.classpath.add(new File(LiveInjector.findPathJar(EquinoxClassLoader.class)));
	}
	
	/**
	 * Any classes whose name starts with a prefix will be searched in the patch classpath.
	 */
	public static void addPrefix(String... additionalPrefixes) {
		prefixes.addAll(Arrays.asList(additionalPrefixes));
	}
	
	/**
	 * Any classes whose name starts with a prefix will be searched in the patch classpath, *AND* will always be loaded by the core loader.
	 * Any classes here can NOT interface with the code you are patching, but, there will be no multiple versions of any Classes that start
	 * with a core prefix, even if the target project is using classloaders for dynamic modularization.
	 */
	public static void addCorePrefix(String... additionalPrefixes) {
		corePrefixes.addAll(Arrays.asList(additionalPrefixes));
	}
	
	public void addClasspath(String file) {
		classpath.add(new File(file));
	}
	
	public void addSubLoader(ClassLoader loader) {
		if (!subLoaders.contains(loader)) subLoaders.add(loader);
	}
	
	private static final String SELF_NAME = "lombok.patcher.equinox.EquinoxClassLoader";
	
	public static void registerScripts(ScriptManager manager) {
		manager.addScript(ScriptBuilder.exitEarly()
				.target(new MethodTarget("org.eclipse.osgi.internal.baseadaptor.DefaultClassLoader", "loadClass",
						"java.lang.Class", "java.lang.String", "boolean"))
				.target(new MethodTarget("org.eclipse.osgi.framework.adapter.core.AbstractClassLoader", "loadClass",
						"java.lang.Class", "java.lang.String", "boolean"))
				.target(new MethodTarget("org.eclipse.osgi.internal.loader.ModuleClassLoader", "loadClass",
						"java.lang.Class", "java.lang.String", "boolean"))
				.decisionMethod(new Hook(SELF_NAME, "overrideLoadDecide", "boolean", "java.lang.ClassLoader", "java.lang.String", "boolean"))
				.valueMethod(new Hook(SELF_NAME, "overrideLoadResult", "java.lang.Class", "java.lang.ClassLoader", "java.lang.String", "boolean"))
				.request(StackRequest.THIS, StackRequest.PARAM1, StackRequest.PARAM2).build());
	}
	
	private void logLoadError(Throwable t) {
		t.printStackTrace();
	}
	
	private final ConcurrentMap<String, WeakReference<Class<?>>> defineCache = new ConcurrentHashMap<String, WeakReference<Class<?>>>();
	
	/*
	 * Load order:
	 * First, try loading from the jar/dir that hosts EquinoxClassLoader
	 * Then, try any additional registered classpaths (jar/dir based).
	 * If this too fails, ask the system classloader.
	 * If the system classloader can't find it either, try loading via any registered subLoaders.
	 * If we still haven't found it, fail.
	 */
	@Override protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
		boolean controlLoad = false;
		ClassLoader resolver = resolve ? this : null;
		Class<?> alreadyLoaded = setCached(name, null, resolver);
		if (alreadyLoaded != null) return alreadyLoaded;
		
		for (String corePrefix : corePrefixes) {
			if (name.startsWith(corePrefix)) {
				if (this != coreLoader) {
					return setCached(name, coreLoader.loadClass(name, resolve), resolver);
				}
				controlLoad = true;
				break;
			}
		}
		
		for (String prefix : prefixes) {
			if (name.startsWith(prefix)) {
				controlLoad = true;
				break;
			}
		}
		
		
		for (File file : classpath) {
			if (file.isFile()) {
				try {
					@Cleanup JarFile jf = new JarFile(file);
					ZipEntry entry = jf.getEntry(name);
					if (entry == null) continue;
					byte[] classData = readStream(jf.getInputStream(entry));
					return setCached(name, defineClass(name, classData, 0, classData.length), resolver);
				} catch (IOException e) {
					logLoadError(e);
				}
			} else {
				File target = new File(file, name);
				if (!target.exists()) continue;
				try {
					byte[] classData = readStream(new FileInputStream(target));
					return setCached(name, defineClass(name, classData, 0, classData.length), resolver);
				} catch (IOException e) {
					logLoadError(e);
				}
			}
		}
		
		if (controlLoad) {
			try {
				byte[] classData = readStream(super.getResourceAsStream(name.replace(".", "/") + ".class"));
				return setCached(name, defineClass(name, classData, 0, classData.length), resolver);
			} catch (Exception ignore) {
			} catch (UnsupportedClassVersionError e) {
				System.err.println("BAD CLASS VERSION TRYING TO LOAD: " + name);
				throw e;
			}
		} else {
			try {
				return setCached(name, super.loadClass(name, resolve), resolver);
			} catch (ClassNotFoundException ignore) {}
		}
		
		cantFind.put(name, name);
		
		for (ClassLoader subLoader : subLoaders) {
			try {
				return setCached(name, subLoader.loadClass(name), resolve ? subLoader : null);
			} catch (ClassNotFoundException ignore) {}
		}
		
		throw new ClassNotFoundException(name);
	}
	
	private static Class<?> resolveClass_(Class<?> c, ClassLoader resolver) {
		if (resolver == null || resolveMethod == null) return c;
		try {
			resolveMethod.invoke(resolver, c);
		} catch (InvocationTargetException e) {
			throw sneakyThrow(e);
		} catch (IllegalAccessException e) {
			// intentional fallthrough
		} catch (IllegalArgumentException e) {
			// intentional fallthrough
		}
		return c;
	}
	
	public static RuntimeException sneakyThrow(Throwable t) {
		if (t == null) throw new NullPointerException("t");
		EquinoxClassLoader.<RuntimeException>sneakyThrow0(t);
		return null;
	}
	
	@SuppressWarnings("unchecked")
	private static <T extends Throwable> void sneakyThrow0(Throwable t) throws T {
		throw (T)t;
	}
	
	private Class<?> setCached(String name, Class<?> c, ClassLoader resolver) {
		if (c == null) {
			// setCached was called just as a check if the class is (now) available in cache.
			WeakReference<Class<?>> prevRef = defineCache.get(name);
			Class<?> prev = prevRef == null ? null : prevRef.get();
			if (prev == null) return null;
			return resolveClass_(prev, resolver);
		}
		
		WeakReference<Class<?>> prevRef = defineCache.putIfAbsent(name, new WeakReference<Class<?>>(c));
		if (prevRef == null) {
			// 'our' class object is the one that is now in the cache, so, return it.
			return resolveClass_(c, resolver);
		}
		
		Class<?> prev = prevRef.get();
		if (prev != null) {
			// A different thread was building at the same time and got there before we did, so use that one and toss ours.
			return resolveClass_(prev, resolver);
		}
		
		// We caught the cache in the middle of a garbage collect. Assuming nobody else wiped this ref from the cache, we wipe it, and start over.
		defineCache.remove(name, prevRef);
		return setCached(name, c, resolver);
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
	
	private static EquinoxClassLoader getHostLoader(ClassLoader original) {
		EquinoxClassLoader ecl = hostLoaders.get(original);
		if (ecl != null) return ecl;
		ecl = new EquinoxClassLoader();
		ecl.addSubLoader(original);
		hostLoaders.putIfAbsent(original, ecl);
		return hostLoaders.get(original);
	}
	
	/**
	 * Method used to patch equinox classloader.
	 * 
	 * @param original Parameter must be there for patching.
	 * @param name Parameter must be there for patching.
	 * @param resolve Parameter must be there for patching.
	 */
	public static boolean overrideLoadDecide(ClassLoader original, String name, boolean resolve) {
		EquinoxClassLoader hostLoader = getHostLoader(original);
		if (hostLoader.cantFind.containsKey(name)) return false;
		for (String prefix : prefixes) {
			if (name.startsWith(prefix)) return true;
		}
		
		return false;
	}
	
	public static Class<?> overrideLoadResult(ClassLoader original, String name, boolean resolve) throws ClassNotFoundException {
		EquinoxClassLoader hostLoader = getHostLoader(original);
		hostLoader.addSubLoader(original);
		return hostLoader.loadClass(name, resolve);
	}
}
