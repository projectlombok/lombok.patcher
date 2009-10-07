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
package lombok.patcher;

import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.IllegalClassFormatException;
import java.lang.instrument.Instrumentation;
import java.lang.reflect.InvocationTargetException;
import java.security.ProtectionDomain;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ScriptManager {
	private final List<PatchScript> scripts = new ArrayList<PatchScript>();
	
	public void addScript(PatchScript script) {
		scripts.add(script);
	}
	
	public void registerTransformer(Instrumentation instrumentation) {
		instrumentation.addTransformer(transformer, true);
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
							"You'll have to restart the application to patch it.");
				} catch ( Throwable t ) {
					throw new UnsupportedOperationException(
							"This appears to be a JVM v1.5, which cannot reload already loaded classes. " +
							"You'll have to restart the application to patch it.");
				}
			}
		}
	}
	
	private final ClassFileTransformer transformer = new ClassFileTransformer() {
		@Override public byte[] transform(ClassLoader loader, String className, Class<?> classBeingRedefined, ProtectionDomain protectionDomain, byte[] classfileBuffer) throws IllegalClassFormatException {
			byte[] byteCode = classfileBuffer;
			boolean patched = false;
			for (PatchScript script : scripts) {
				byte[] transformed = null;
				try {
					transformed = script.patch(className, byteCode);
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
			return patched ? byteCode : null;
		}
	};
}
