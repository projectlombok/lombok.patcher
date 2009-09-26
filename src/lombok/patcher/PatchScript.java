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

import java.util.ArrayList;
import java.util.List;

import org.objectweb.asm.ClassAdapter;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;

/**
 * Represents a patch script. Contains a convenience method to run ASM on the class you want to transform.
 * Usually you can find a subclass of this class that does what you want. If you need to do some novel transformation,
 * you should extend this class.
 */
public abstract class PatchScript {
	/**
	 * Defaults to the class name, but you can give a fancier name to your script if you want.
	 */
	public String getPatchScriptName() {
		return getClass().getSimpleName();
	}
	
	/**
	 * Transforms the class. You may return {@code null} if you have no interest in transforming this particular class.
	 */
	public abstract byte[] patch(String className, byte[] byteCode);
	
	/**
	 * Runs ASM on the provider byteCode, chaining a reader to a writer and using the {@code ClassVisitor} you yourself provide
	 * via the {@see #createClassVisitor(ClassWriter)} method as the filter.
	 */
	protected byte[] runASM(byte[] byteCode, boolean computeMaxS) {
		ClassReader reader = new ClassReader(byteCode);
		ClassWriter writer = new ClassWriter(reader, computeMaxS ? ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES : 0);
		
		ClassVisitor visitor = createClassVisitor(writer);
		reader.accept(visitor, 0);
		return writer.toByteArray();
	}
	
	/**
	 * You need to override this method if you want to call {@see #runASM(byte[])}.
	 */
	protected ClassVisitor createClassVisitor(ClassWriter writer) {
		throw new IllegalStateException("If you're going to call runASM, then you need to implement createClassVisitor");
	}
	
	/**
	 * If you want to use the {@see MethodPatcher} class, you need to supply an implementation of this factory.
	 */
	public interface MethodPatcherFactory {
		/**
		 * Supply a {@code MethodVisitor} that knows how to process the provided method.
		 */
		public MethodVisitor createMethodVisitor(MethodTarget target, MethodVisitor parent, MethodLogistics logistics);
	}
	
	/**
	 * Convenience implementation of the {@code ClassAdapter} that you can return for {@see #createClassVisitor(ClassWriter)};
	 * it will call into a custom {@code MethodVisitor} for specified methods, and pass through everything else. Perfect if you
	 * want to rewrite one or more methods.
	 */
	protected static class MethodPatcher extends ClassAdapter {
		private List<MethodTarget> targets = new ArrayList<MethodTarget>();
		private String classSpec;
		private final MethodPatcherFactory factory;
		
		public MethodPatcher(ClassVisitor cv, MethodPatcherFactory factory) {
			super(cv);
			this.factory = factory;
		}
		
		/**
		 * The {@code factory} will be called for any methods that match any added target.
		 */
		public void addMethodTarget(MethodTarget t) {
			targets.add(t);
		}
		
		@Override public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
			this.classSpec = name;
			super.visit(version, access, name, signature, superName, interfaces);
		}
		
		@Override public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
			MethodVisitor visitor = super.visitMethod(access, name, desc, signature, exceptions);
			
			for (MethodTarget t : targets) {
				if (t.matches(classSpec, name, desc)) {
					return factory.createMethodVisitor(t, visitor, new MethodLogistics(access, desc));
				}
			}
			
			return visitor;
		}
	}
}
