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
package lombok.patcher.scripts;

import java.util.Collection;
import java.util.Collections;

import lombok.patcher.Hook;
import lombok.patcher.MethodLogistics;
import lombok.patcher.MethodTarget;
import lombok.patcher.PatchScript;

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodAdapter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

/**
 * Replaces all calls to a given method with another (static) method. It is your job to ensure both all parameters and the
 * return type are perfectly compatible. If you're replacing an instance method, make sure your static method's first
 * parameter is type-compatible with the LHS of the instance method. You must also return something that is compatible with
 * the method you're replacing.
 */
public class ReplaceMethodCallScript extends PatchScript {
	private final MethodTarget targetMethod;
	private final Hook wrapper;
	private final Hook methodToReplace;
	private final boolean transplant;

	ReplaceMethodCallScript(MethodTarget targetMethod, Hook callToReplace, Hook wrapper, boolean transplant) {
		if (targetMethod == null) throw new NullPointerException("targetMethod");
		if (callToReplace == null) throw new NullPointerException("callToReplace");
		if (wrapper == null) throw new NullPointerException("wrapper");
		this.targetMethod = targetMethod;
		this.methodToReplace = callToReplace;
		this.wrapper = wrapper;
		this.transplant = transplant;
	}
	
	@Override public Collection<String> getClassesToReload() {
		return Collections.singleton(targetMethod.getClassSpec());
	}
	
	@Override public byte[] patch(String className, byte[] byteCode) {
		if (!targetMethod.classMatches(className)) return null;
		return runASM(byteCode, true);
	}
	
	@Override protected ClassVisitor createClassVisitor(ClassWriter writer, final String classSpec) {
		final MethodPatcher patcher = new MethodPatcher(writer, new MethodPatcherFactory() {
			@Override public MethodVisitor createMethodVisitor(MethodTarget target, MethodVisitor parent, MethodLogistics logistics) {
				return new ReplaceMethodCall(parent, classSpec);
			}
		});
		
		if (transplant) patcher.addTransplant(wrapper);
		patcher.addMethodTarget(targetMethod);
		
		return patcher;
	}
	
	private class ReplaceMethodCall extends MethodAdapter {
		private final String ownClassSpec;
		
		public ReplaceMethodCall(MethodVisitor mv, String ownClassSpec) {
			super(mv);
			this.ownClassSpec = ownClassSpec;
		}
		
		@Override public void visitMethodInsn(int opcode, String owner, String name, String desc) {
			
			if (methodToReplace.getMethodName().equals(name) && methodToReplace.getMethodDescriptor().equals(desc)) {
				super.visitMethodInsn(Opcodes.INVOKESTATIC, transplant ? ownClassSpec : wrapper.getClassSpec(),
						wrapper.getMethodName(), wrapper.getMethodDescriptor());
			} else {
				super.visitMethodInsn(opcode, owner, name, desc);
			}
		}
	}
}
