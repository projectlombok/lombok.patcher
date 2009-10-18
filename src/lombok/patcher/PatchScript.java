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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

import lombok.Getter;

import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.Attribute;
import org.objectweb.asm.ClassAdapter;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodAdapter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

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
	 * Each class name (as standard java class name, with dots) listed here will be reloaded if the JVM supports this
	 * and you start the ScriptManager while the JVM is already running instead of via the -javaagent parameter. Generally
	 * you want to list each class you patch.
	 */
	public abstract Collection<String> getClassesToReload();
	
	public static boolean classMatches(String className, Collection<String> classSpecs) {
		for (String classSpec : classSpecs) {
			if (MethodTarget.typeMatches(className, classSpec)) return true;
		}
		
		return false;
	}
	
	/**
	 * Transforms the class. You may return {@code null} if you have no interest in transforming this particular class.
	 */
	public abstract byte[] patch(String className, byte[] byteCode);
	
	/**
	 * Runs ASM on the provider byteCode, chaining a reader to a writer and using the {@code ClassVisitor} you yourself provide
	 * via the {@see #createClassVisitor(ClassWriter)} method as the filter.
	 */
	protected byte[] runASM(byte[] byteCode, boolean computeFrames) {
		ClassReader reader = new ClassReader(byteCode);
		ClassWriter writer = new ClassWriter(reader, computeFrames ? ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES : 0) {
			@Override protected String getCommonSuperClass(String type1, String type2) {
				//By default, ASM will attempt to live-load the class types, which will fail if meddling with classes in an
				//environment with custom classloaders, such as Equinox. It's just an optimization; returning Object is always legal.
				try {
					return super.getCommonSuperClass(type1, type2);
				} catch (Exception e) {
					return "java/lang/Object";
				}
			}
		};
		
		ClassVisitor visitor = createClassVisitor(writer, reader.getClassName());
		reader.accept(visitor, 0);
		return writer.toByteArray();
	}
	
	/**
	 * You need to override this method if you want to call {@see #runASM(byte[])}.
	 * 
	 * @param writer The parent writer.
	 * @param classSpec The name of the class you need to make a visitor for.
	 */
	protected ClassVisitor createClassVisitor(ClassWriter writer, String classSpec) {
		throw new IllegalStateException("If you're going to call runASM, then you need to implement createClassVisitor");
	}
	
	/**
	 * If you want to use the {@see MethodPatcher} class, you need to supply an implementation of this factory.
	 */
	public interface MethodPatcherFactory {
		/**
		 * Supply a {@code MethodVisitor} that knows how to process the provided method.
		 * 
		 * @param methodName the name of the method.
		 * @param methodDescription the description of the method, such as (II)V (method takes 2 ints as parameters and returns void).
		 * @param parent the visitor that will write to the actual class file.
		 * @param logistics contains useful methods for interacting with the method, such as generating the opcode to
		 *                  put a certain parameter on the stack.
		 */
		public MethodVisitor createMethodVisitor(String methodName, String methodDescription, MethodVisitor parent, MethodLogistics logistics);
	}
	
	private static byte[] readStream(String resourceName) {
		try {
			InputStream wrapStream = PatchScript.class.getResourceAsStream(resourceName);
			ByteArrayOutputStream baos = new ByteArrayOutputStream();
			byte[] b = new byte[65536];
			while (true) {
				int r = wrapStream.read(b);
				if (r == -1) break;
				baos.write(b, 0, r);
			}
			
			return baos.toByteArray();
		} catch (IOException e) {
			throw new IllegalArgumentException("resource " + resourceName + " does not exist.", e);
		}
	}
	
	private static abstract class NoopClassVisitor implements ClassVisitor {
		@Override public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {}
		@Override public void visitAttribute(Attribute attr) {}
		@Override public void visitEnd() {}
		@Override public void visitOuterClass(String owner, String name, String desc) {}
		@Override public void visitSource(String source, String debug) {}
		@Override public void visitInnerClass(String name, String outerName, String innerName, int access) {}
		@Override public AnnotationVisitor visitAnnotation(String desc, boolean visible) { return null; }
		@Override public FieldVisitor visitField(int access, String name, String desc, String signature, Object value) { return null; }
		@Override public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) { return null; }
	}
	
	protected static void insertMethod(final Hook methodToInsert, final MethodVisitor target) {
		byte[] classData = readStream("/" + methodToInsert.getClassSpec() + ".class");
		
		ClassReader reader = new ClassReader(classData);
		ClassVisitor methodFinder = new NoopClassVisitor() {
			@Override public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
				if (name.equals(methodToInsert.getMethodName()) && desc.equals(methodToInsert.getMethodDescriptor())) {
					return new InsertBodyOfMethodIntoAnotherVisitor(target);
				}
				return null;
			}
		};
		reader.accept(methodFinder, 0);
	}
	
	protected static void transplantMethod(final Hook methodToTransplant, final ClassVisitor target) {
		byte[] classData = readStream("/" + methodToTransplant.getClassSpec() + ".class");
		
		ClassReader reader = new ClassReader(classData);
		ClassVisitor methodFinder = new NoopClassVisitor() {
			@Override public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
				if (name.equals(methodToTransplant.getMethodName()) && desc.equals(methodToTransplant.getMethodDescriptor())) {
					return target.visitMethod(access, name, desc, signature, exceptions);
				}
				return null;
			}
		};
		reader.accept(methodFinder, 0);
	}

	private static final class InsertBodyOfMethodIntoAnotherVisitor extends MethodAdapter {
		private InsertBodyOfMethodIntoAnotherVisitor(MethodVisitor mv) {
			super(mv);
		}
		
		@Override public AnnotationVisitor visitParameterAnnotation(int parameter, String desc, boolean visible) { return null; }
		@Override public void visitMaxs(int maxStack, int maxLocals) {}
		@Override public void visitLineNumber(int line, Label start) {}
		@Override public void visitFrame(int type, int nLocal, Object[] local, int nStack, Object[] stack) {}
		@Override public void visitEnd() {}
		@Override public void visitCode() {}
		
		@Override public void visitInsn(int opcode) {
			if (opcode == Opcodes.RETURN || opcode == Opcodes.ARETURN || opcode == Opcodes.IRETURN
					|| opcode == Opcodes.DRETURN || opcode == Opcodes.FRETURN || opcode == Opcodes.LRETURN)
				/* do nothing */ return;
			
			super.visitInsn(opcode);
		}
		
		@Override public void visitAttribute(Attribute attr) {}
		@Override public AnnotationVisitor visitAnnotationDefault() { return null; }
		@Override public AnnotationVisitor visitAnnotation(String desc, boolean visible) { return null;}
	}
	
	/**
	 * Convenience implementation of the {@code ClassAdapter} that you can return for {@see #createClassVisitor(ClassWriter)};
	 * it will call into a custom {@code MethodVisitor} for specified methods, and pass through everything else. Perfect if you
	 * want to rewrite one or more methods.
	 */
	protected static class MethodPatcher extends ClassAdapter {
		private List<TargetMatcher> targets = new ArrayList<TargetMatcher>();
		private @Getter String ownClassSpec;
		private final MethodPatcherFactory factory;
		private List<Hook> transplants = new ArrayList<Hook>();
		
		public MethodPatcher(ClassVisitor cv, MethodPatcherFactory factory) {
			super(cv);
			this.factory = factory;
		}
		
		/**
		 * The {@code factory} will be called for any methods that match any added target.
		 */
		public void addTargetMatcher(TargetMatcher t) {
			targets.add(t);
		}
		
		@Override public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
			this.ownClassSpec = name;
			super.visit(version, access, name, signature, superName, interfaces);
		}
		
		public void addTransplant(Hook transplant) {
			if (transplant == null) throw new NullPointerException("transplant");
			transplants.add(transplant);
		}
		
		@Override public void visitEnd() {
			for (Hook transplant : transplants) {
				transplantMethod(transplant, cv);
			}
		}
		
		@Override public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
			MethodVisitor visitor = super.visitMethod(access, name, desc, signature, exceptions);
			
			/* Remove transplant jobs where the method already exists - probably because of an earlier patch script. */ {
				Iterator<Hook> it = transplants.iterator();
				while (it.hasNext()) {
					Hook h = it.next();
					if (h.getMethodName().equals(name) && h.getMethodDescriptor().equals(desc)) it.remove();
				}
			}
			
			for (TargetMatcher t : targets) {
				if (t.matches(ownClassSpec, name, desc)) {
					return factory.createMethodVisitor(name, desc, visitor, new MethodLogistics(access, desc));
				}
			}
			
			return visitor;
		}
	}
}
