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

import java.util.List;
import java.util.Set;

import lombok.patcher.Hook;
import lombok.patcher.MethodLogistics;
import lombok.patcher.StackRequest;
import lombok.patcher.TargetMatcher;

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
public class ReplaceMethodCallScript extends MethodLevelPatchScript {
	private final Hook wrapper;
	private final Hook methodToReplace;
	private final boolean transplant;
	private final Set<StackRequest> extraRequests;

	ReplaceMethodCallScript(List<TargetMatcher> matchers, Hook callToReplace, Hook wrapper, boolean transplant, Set<StackRequest> extraRequests) {
		super(matchers);
		if (callToReplace == null) throw new NullPointerException("callToReplace");
		if (wrapper == null) throw new NullPointerException("wrapper");
		this.methodToReplace = callToReplace;
		this.wrapper = wrapper;
		this.transplant = transplant;
		this.extraRequests = extraRequests;
	}
	
	@Override protected MethodPatcher createPatcher(ClassWriter writer, final String classSpec) {
		final MethodPatcher patcher = new MethodPatcher(writer, new MethodPatcherFactory() {
			@Override public MethodVisitor createMethodVisitor(String name, String desc, MethodVisitor parent, MethodLogistics logistics) {
				return new ReplaceMethodCall(parent, classSpec, logistics);
			}
		});
		
		if (transplant) patcher.addTransplant(wrapper);
		
		return patcher;
	}
	
	private class ReplaceMethodCall extends MethodAdapter {
		private final String ownClassSpec;
		private final MethodLogistics logistics;
		
		public ReplaceMethodCall(MethodVisitor mv, String ownClassSpec, MethodLogistics logistics) {
			super(mv);
			this.ownClassSpec = ownClassSpec;
			this.logistics = logistics;
		}
		
		@Override public void visitMethodInsn(int opcode, String owner, String name, String desc) {
			if (methodToReplace.getClassSpec().equals(owner) &&
			    methodToReplace.getMethodName().equals(name) &&
			    methodToReplace.getMethodDescriptor().equals(desc)) {
				if (extraRequests.contains(StackRequest.THIS)) logistics.generateLoadOpcodeForThis(mv);
				for (StackRequest param : StackRequest.PARAMS_IN_ORDER) {
					if (!extraRequests.contains(param)) continue;
					logistics.generateLoadOpcodeForParam(param.getParamPos(), mv);
				}
				super.visitMethodInsn(Opcodes.INVOKESTATIC, transplant ? ownClassSpec : wrapper.getClassSpec(),
						wrapper.getMethodName(), wrapper.getMethodDescriptor());
			} else {
				super.visitMethodInsn(opcode, owner, name, desc);
			}
		}
	}
}
