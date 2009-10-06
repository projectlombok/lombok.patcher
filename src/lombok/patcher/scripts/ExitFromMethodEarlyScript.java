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
import java.util.Set;

import lombok.NonNull;
import lombok.patcher.Hook;
import lombok.patcher.MethodLogistics;
import lombok.patcher.PatchScript;
import lombok.patcher.StackRequest;
import lombok.patcher.TargetMatcher;

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodAdapter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

/**
 * Receive (optional) 'this' reference as well as any parameters, then choose to return early with provided value, or let
 * the method continue.
 */
public class ExitFromMethodEarlyScript extends PatchScript {
	private final @NonNull TargetMatcher matcher;
	private final @NonNull Hook decisionWrapper, valueWrapper;
	private final Set<StackRequest> requests;
	private final boolean transplant;
	
	ExitFromMethodEarlyScript(TargetMatcher matcher, Hook decisionWrapper, Hook valueWrapper, boolean transplant, Set<StackRequest> requests) {
		if (matcher == null) throw new NullPointerException("matcher");
		if (decisionWrapper == null) throw new NullPointerException("decisionWrapper");
		this.matcher = matcher;
		this.decisionWrapper = decisionWrapper;
		this.valueWrapper = valueWrapper;
		this.requests = requests;
		this.transplant = transplant;
	}
	
	@Override public Collection<String> getClassesToReload() {
		return matcher.getAffectedClasses();
	}
	
	@Override public byte[] patch(String className, byte[] byteCode) {
		if (!classMatches(className, matcher.getAffectedClasses())) return null;
		return runASM(byteCode, true);
	}
	
	@Override protected ClassVisitor createClassVisitor(ClassWriter writer, final String classSpec) {
		MethodPatcher patcher = new MethodPatcher(writer, new MethodPatcherFactory() {
			@Override public MethodVisitor createMethodVisitor(String name, String desc, MethodVisitor parent, MethodLogistics logistics) {
				if (logistics.getReturnOpcode() != Opcodes.RETURN && valueWrapper == null) {
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
		patcher.addTargetMatcher(matcher);
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
			if (requests.contains(StackRequest.THIS)) logistics.generateLoadOpcodeForThis(mv);
			for (StackRequest param : StackRequest.PARAMS_IN_ORDER) {
				if (!requests.contains(param)) continue;
				logistics.generateLoadOpcodeForParam(param.getParamPos(), mv);
			}
			super.visitMethodInsn(Opcodes.INVOKESTATIC, transplant ? ownClassSpec : decisionWrapper.getClassSpec(),
					decisionWrapper.getMethodName(), decisionWrapper.getMethodDescriptor());
			
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
				super.visitMethodInsn(Opcodes.INVOKESTATIC, transplant ? ownClassSpec : valueWrapper.getClassSpec(),
						valueWrapper.getMethodName(), valueWrapper.getMethodDescriptor());
				logistics.generateReturnOpcode(mv);
			}
			mv.visitLabel(l0);
			mv.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
			super.visitCode();
		}
		
		@Override public void visitInsn(int opcode) {
			if (opcode != logistics.getReturnOpcode()) {
				super.visitInsn(opcode);
				return;
			}
			
			
			super.visitInsn(opcode);
		}
	}
}
