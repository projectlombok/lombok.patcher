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

import lombok.NonNull;
import lombok.patcher.Hook;
import lombok.patcher.MethodLogistics;
import lombok.patcher.StackRequest;
import lombok.patcher.TargetMatcher;

import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodAdapter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

/**
 * Receive (optional) 'this' reference as well as any parameters, then choose to return early with provided value, or let
 * the method continue.
 */
public class ExitFromMethodEarlyScript extends MethodLevelPatchScript {
	private final @NonNull Hook decisionWrapper, valueWrapper;
	private final Set<StackRequest> requests;
	private final boolean transplant, insert;
	private final boolean insertCallOnly;
	
	ExitFromMethodEarlyScript(List<TargetMatcher> matchers, Hook decisionWrapper, Hook valueWrapper, boolean transplant, boolean insert, Set<StackRequest> requests) {
		super(matchers);
		this.decisionWrapper = decisionWrapper;
		this.valueWrapper = valueWrapper;
		this.requests = requests;
		this.transplant = transplant;
		this.insert = insert;
		this.insertCallOnly = decisionWrapper != null && decisionWrapper.getMethodDescriptor().endsWith(")V");
		if (!this.insertCallOnly && decisionWrapper != null && !decisionWrapper.getMethodDescriptor().endsWith(")Z")) {
			throw new IllegalArgumentException("The decisionWrapper method must either return 'boolean' or return 'void'.");
		}
		assert !(insert && transplant);
	}
	
	@Override protected MethodPatcher createPatcher(ClassWriter writer, final String classSpec) {
		MethodPatcher patcher = new MethodPatcher(writer, new MethodPatcherFactory() {
			@Override public MethodVisitor createMethodVisitor(String name, String desc, MethodVisitor parent, MethodLogistics logistics) {
				if (valueWrapper == null && !insertCallOnly && logistics.getReturnOpcode() != Opcodes.RETURN) {
					throw new IllegalStateException("method " + name + desc + " must return something, but " +
							"you did not provide a value hook method.");
				}
				return new ExitEarly(parent, logistics, classSpec);
			}
		});
		
		if (transplant) {
			patcher.addTransplant(decisionWrapper);
			if (valueWrapper != null) patcher.addTransplant(valueWrapper);
		}
		return patcher;
	}
	
	private class ExitEarly extends MethodAdapter {
		private final MethodLogistics logistics;
		private final String ownClassSpec;
		
		public ExitEarly(MethodVisitor mv, MethodLogistics logistics, String ownClassSpec) {
			super(mv);
			this.logistics = logistics;
			this.ownClassSpec = ownClassSpec;
		}
		
		@Override public void visitCode() {
			if (decisionWrapper == null) {
				//Always return early.
				if (logistics.getReturnOpcode() == Opcodes.RETURN) {
					mv.visitInsn(Opcodes.RETURN);
					return;
				}
				
				insertValueWrapperCall();
				return;
			}
			
			if (requests.contains(StackRequest.THIS)) logistics.generateLoadOpcodeForThis(mv);
			for (StackRequest param : StackRequest.PARAMS_IN_ORDER) {
				if (!requests.contains(param)) continue;
				logistics.generateLoadOpcodeForParam(param.getParamPos(), mv);
			}
			
			if (insert) insertMethod(decisionWrapper, mv);
			else super.visitMethodInsn(Opcodes.INVOKESTATIC, transplant ? ownClassSpec : decisionWrapper.getClassSpec(),
					decisionWrapper.getMethodName(), decisionWrapper.getMethodDescriptor());
			
			if (insertCallOnly) {
				super.visitCode();
				return;
			}
			
			/* Inject:
			 * if ([result of decision hook]) {
			 *     //if method body is not VOID:
			 *         reload-on-stack-what-needs-reloading
			 *         invokeReturnValueHook
			 *     //end if
			 *     xRETURN;
			 * }
			 */
			
			Label l0 = new Label();
			mv.visitJumpInsn(Opcodes.IFEQ, l0);
			if (logistics.getReturnOpcode() == Opcodes.RETURN) {
				mv.visitInsn(Opcodes.RETURN);
			} else {
				if (requests.contains(StackRequest.THIS)) logistics.generateLoadOpcodeForThis(mv);
				for (StackRequest param : StackRequest.PARAMS_IN_ORDER) {
					if (!requests.contains(param)) continue;
					logistics.generateLoadOpcodeForParam(param.getParamPos(), mv);
				}
				if (insert) insertMethod(valueWrapper, mv);
				else super.visitMethodInsn(Opcodes.INVOKESTATIC, transplant ? ownClassSpec : valueWrapper.getClassSpec(),
						valueWrapper.getMethodName(), valueWrapper.getMethodDescriptor());
				logistics.generateReturnOpcode(mv);
			}
			mv.visitLabel(l0);
			mv.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
			super.visitCode();
		}
		
		private void insertValueWrapperCall() {
			if (requests.contains(StackRequest.THIS)) logistics.generateLoadOpcodeForThis(mv);
			for (StackRequest param : StackRequest.PARAMS_IN_ORDER) {
				if (!requests.contains(param)) continue;
				logistics.generateLoadOpcodeForParam(param.getParamPos(), mv);
			}
			if (insert) insertMethod(valueWrapper, mv);
			else super.visitMethodInsn(Opcodes.INVOKESTATIC, transplant ? ownClassSpec : valueWrapper.getClassSpec(),
					valueWrapper.getMethodName(), valueWrapper.getMethodDescriptor());
			logistics.generateReturnOpcode(mv);
		}
	}
}
