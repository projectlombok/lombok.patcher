/*
 * Copyright (C) 2009-2020 The Project Lombok Authors.
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
package lombok.patcher;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.IllegalClassFormatException;
import java.lang.instrument.Instrumentation;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.security.ProtectionDomain;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.jar.JarFile;

public class ScriptManager {
	private static final class WitnessAction {
		List<String[]> triggerGroups;
		boolean triggered;
		boolean ifWitnessRemove;
		PatchScript script;
	}
	
	private final List<PatchScript> scripts = new ArrayList<PatchScript>();
	private final Map<String, List<WitnessAction>> witnessActions = new HashMap<String, List<WitnessAction>>(); // maps FQNs to actions
	
	private TransplantMapper transplantMapper = TransplantMapper.IDENTITY_MAPPER;
	private Filter filter = Filter.ALWAYS;
	
	public void addScript(PatchScript script) {
		scripts.add(script);
	}
	
	/**
	 * Registers {@code script} as a script to apply, but delay adding the script until at least one class is witnessed from each group in {@code witnessGroups}.
	 * 
	 * @param witness A list of fully qualified type names to watch for. {@code script} is added when any of these types is loaded.
	 * @param script The script to add.
	 */
	public void addScriptIfComplexWitness(String[][] witnessGroups, PatchScript script) {
		WitnessAction wa = new WitnessAction();
		wa.ifWitnessRemove = false;
		wa.script = script;
		wa.triggerGroups = new ArrayList<String[]>();
		for (String[] group : witnessGroups) wa.triggerGroups.add(group);
		Set<String> seen = new HashSet<String>();
		for (String[] group : witnessGroups) for (String w : group) {
			if (!seen.add(w)) continue;
			List<WitnessAction> list = witnessActions.get(w);
			if (list == null) {
				list = new ArrayList<WitnessAction>();
				witnessActions.put(w, list);
			}
			list.add(wa);
		}
	}
	
	/**
	 * Registers {@code script} as a script to apply, but delay adding the script until a class is loaded that is listed in {@code witness}.
	 * 
	 * @param witness A list of fully qualified type names to watch for. {@code script} is added when any of these types is loaded.
	 * @param script The script to add.
	 */
	public void addScriptIfWitness(String[] witness, PatchScript script) {
		WitnessAction wa = new WitnessAction();
		wa.ifWitnessRemove = false;
		wa.script = script;
		for (String w : witness) {
			List<WitnessAction> list = witnessActions.get(w);
			if (list == null) {
				list = new ArrayList<WitnessAction>();
				witnessActions.put(w, list);
			}
			list.add(wa);
		}
	}
	
	public void addScriptIfNotWitness(String[] witness, PatchScript script) {
		WitnessAction wa = new WitnessAction();
		wa.ifWitnessRemove = true;
		wa.script = script;
		scripts.add(script);
		for (String w : witness) {
			List<WitnessAction> list = witnessActions.get(w);
			if (list == null) {
				list = new ArrayList<WitnessAction>();
				witnessActions.put(w, list);
			}
			list.add(wa);
		}
	}
	
	public void setFilter(Filter filter) {
		this.filter = filter == null ? Filter.ALWAYS : filter;
	}
	
	public void registerTransformer(Instrumentation instrumentation) {
		try {
			Method m = Instrumentation.class.getMethod("addTransformer", ClassFileTransformer.class, boolean.class);
			m.invoke(instrumentation, transformer, true);
		} catch (Throwable t) {
			//We're on java 1.5, or something even crazier happened. This one works in 1.5 as well:
			instrumentation.addTransformer(transformer);
		}
	}
	
	public void reloadClasses(Instrumentation instrumentation) {
		Set<String> toReload = new HashSet<String>();
		
		for (PatchScript s : scripts) toReload.addAll(s.getClassesToReload());
		
		for (Class<?> c : instrumentation.getAllLoadedClasses()) {
			if (toReload.contains(c.getName())) {
				try {
					//instrumentation.retransformClasses(c); - //not in java 1.5.
					Instrumentation.class.getMethod("retransformClasses", Class[].class).invoke(instrumentation,
							new Object[] { new Class[] {c }});
				} catch ( InvocationTargetException e ) {
					throw new UnsupportedOperationException(
							"The " + c.getName() + " class is already loaded and cannot be modified. " +
							"You'll have to restart the application to patch it. Reason: " + e.getCause());
				} catch ( Throwable t ) {
					throw new UnsupportedOperationException(
							"This appears to be a JVM v1.5, which cannot reload already loaded classes. " +
							"You'll have to restart the application to patch it.");
				}
			}
		}
	}
	
	private static final String DEBUG_PATCHING;
	private static final boolean LOG_TO_STANDARD_ERR = false;
	static {
		DEBUG_PATCHING = System.getProperty("lombok.patcher.patchDebugDir", null);
	}
	
	private final OurClassFileTransformer transformer = new OurClassFileTransformer();
	
	private class OurClassFileTransformer implements ClassFileTransformer {
		public byte[] transform(ClassLoader loader, String className, Class<?> classBeingRedefined, ProtectionDomain protectionDomain, byte[] classfileBuffer) throws IllegalClassFormatException {
			if (className == null) return null;
			
			List<WitnessAction> actions = witnessActions.get(className);
			if (actions != null) {
				for (WitnessAction wa : actions) {
					if (wa.triggered) continue;
					List<String[]> triggerGroups = wa.triggerGroups;
					if (triggerGroups != null) {
						// Remove each group that is triggered by this class...
						Iterator<String[]> it = triggerGroups.iterator();
						while (it.hasNext()) {
							String[] group = it.next();
							for (String c : group) if (className.equals(c)) {
								it.remove();
								break;
							}
						}
						// ... and if at least one group hasn't been eliminated yet, this action isn't ready to be added.
						if (!triggerGroups.isEmpty()) break;
					}
					wa.triggered = true;
					if (wa.ifWitnessRemove) {
						scripts.remove(wa.script);
						if (LOG_TO_STANDARD_ERR) System.err.println("Removed script: " + wa.script.getPatchScriptName() + " because I saw " + className);
					} else {
						scripts.add(wa.script);
						if (LOG_TO_STANDARD_ERR) System.err.println("Added script: " + wa.script.getPatchScriptName() + " because I saw " + className);
					}
				}
			}
			
			if (!filter.shouldTransform(loader, className, classBeingRedefined, protectionDomain, classfileBuffer)) return null;
			
			if (LOG_TO_STANDARD_ERR) for (List<WitnessAction> list : witnessActions.values()) {
				for (WitnessAction wa : list) {
					if (wa.triggered || wa.ifWitnessRemove) continue;
					if (wa.script.wouldPatch(className)) {
						System.err.println("[SKIPPING] " + wa.script.getPatchScriptName());
					}
				}
			}
			
			byte[] byteCode = classfileBuffer;
			boolean patched = false;
			
			for (PatchScript script : scripts) {
				byte[] transformed = null;
				try {
					if (LOG_TO_STANDARD_ERR) {
						if (script.wouldPatch(className)) {
							System.err.println("[RUNNING] " + script.getPatchScriptName());
							transformed = script.patch(className, byteCode, transplantMapper);
						}
					} else {
						transformed = script.patch(className, byteCode, transplantMapper);
					}
				} catch (Throwable t) {
					//Exceptions get silently swallowed by instrumentation, so this is a slight improvement.
					System.err.printf("Transformer %s failed on %s. Trace:\n", script.getPatchScriptName(), className);
					t.printStackTrace();
					transformed = null;
				}
				if (transformed != null) {
					patched = true;
					byteCode = transformed;
				}
			}
			if (patched && DEBUG_PATCHING != null) {
				try {
					writeArray(DEBUG_PATCHING, className + ".class", byteCode);
					writeArray(DEBUG_PATCHING, className + "_OLD.class", classfileBuffer);
				} catch (IOException e) {
					System.err.println("Can't log patch result.");
					e.printStackTrace();
				}
			}
			return patched ? byteCode : null;
		}
		
		private void writeArray(String dir, String fileName, byte[] bytes) throws IOException {
			File f = new File(dir, fileName);
			f.getParentFile().mkdirs();
			FileOutputStream fos = new FileOutputStream(f);
			fos.write(bytes);
			fos.close();
		}
	};
	
	private static boolean classpathContains(String property, String path) {
		String pathCanonical = new File(path).getAbsolutePath();
		try {
			pathCanonical = new File(path).getCanonicalPath();
		} catch (Exception ignore) {}
		
		for (String existingPath : System.getProperty(property, "").split(File.pathSeparator)) {
			String p = new File(existingPath).getAbsolutePath();
			try {
				p = new File(existingPath).getCanonicalPath();
			} catch (Throwable ignore) {}
			if (p.equals(pathCanonical)) return true;
		}
		return false;
	}
	
	/**
	 * Adds the provided path (must be to a jar file!) to the system classpath.
	 * 
	 * Will do nothing if the jar is already on either the system or the boot classpath.
	 * 
	 * @throws IllegalArgumentException If {@code pathToJar} doesn't exist or isn't a jar file.
	 * @throws IllegalStateException If you try this on a 1.5 VM - it requires 1.6 or up VM.
	 */
	public void addToSystemClasspath(Instrumentation instrumentation, String pathToJar) {
		if (pathToJar == null) throw new NullPointerException("pathToJar");
		if (classpathContains("sun.boot.class.path", pathToJar)) return;
		if (classpathContains("java.class.path", pathToJar)) return;
		
		try {
			Method m = instrumentation.getClass().getMethod("appendToSystemClassLoaderSearch", JarFile.class);
			m.invoke(instrumentation, new JarFile(pathToJar));
		} catch (NoSuchMethodException e) {
			throw new IllegalStateException("Adding to the classloader path is not possible on a v1.5 JVM");
		} catch (IOException e) {
			throw new IllegalArgumentException("not found or not a jar file: " + pathToJar, e);
		} catch (IllegalAccessException e) {
			throw new IllegalStateException("appendToSystemClassLoaderSearch isn't public? This isn't a JVM...");
		} catch (InvocationTargetException e) {
			Throwable cause = e.getCause();
			
			if (cause instanceof RuntimeException) throw (RuntimeException)cause;
			throw new IllegalArgumentException("Unknown issue: " + cause, cause);
		}
	}
	
	/**
	 * Adds the provided path (must be to a jar file!) to the system classpath.
	 * 
	 * Will do nothing if the jar is already on the boot classpath.
	 * 
	 * @throws IllegalArgumentException If {@code pathToJar} doesn't exist or isn't a jar file.
	 * @throws IllegalStateException If you try this on a 1.5 VM - it requires 1.6 or up VM.
	 */
	public void addToBootClasspath(Instrumentation instrumentation, String pathToJar) {
		if (pathToJar == null) throw new NullPointerException("pathToJar");
		if (classpathContains("sun.boot.class.path", pathToJar)) return;
		
		try {
			Method m = instrumentation.getClass().getMethod("appendToBootstrapClassLoaderSearch", JarFile.class);
			m.invoke(instrumentation, new JarFile(pathToJar));
		} catch (NoSuchMethodException e) {
			throw new IllegalStateException("Adding to the classloader path is not possible on a v1.5 JVM");
		} catch (IOException e) {
			throw new IllegalArgumentException("not found or not a jar file: " + pathToJar, e);
		} catch (IllegalAccessException e) {
			throw new IllegalStateException("appendToSystemClassLoaderSearch isn't public? This isn't a JVM...");
		} catch (InvocationTargetException e) {
			Throwable cause = e.getCause();
			
			if (cause instanceof RuntimeException) throw (RuntimeException)cause;
			throw new IllegalArgumentException("Unknown issue: " + cause, cause);
		}
	}
	
	public void setTransplantMapper(TransplantMapper transplantMapper) {
		this.transplantMapper = transplantMapper == null ? TransplantMapper.IDENTITY_MAPPER : transplantMapper;
	}
}
