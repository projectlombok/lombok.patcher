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

import lombok.patcher.Hook;
import lombok.patcher.MethodLogistics;
import lombok.patcher.TargetMatcher;

import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodAdapter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

/**
 * Will wrap any invocation to a given method in another given method by setting a symbol for the duration
 * of the method, properly guarded with a try/finally block. See {@link lombok.patcher.Symbols} for how to
 * query symbols states.
 */
public class SetSymbolDuringMethodCallScript extends MethodLevelPatchScript {
	private final Hook callToWrap;
	private final String symbol;
	
	SetSymbolDuringMethodCallScript(List<TargetMatcher> matchers, Hook callToWrap, String symbol) {
		super(matchers);
		if (callToWrap == null) throw new NullPointerException("callToWrap");
		if (symbol == null) throw new NullPointerException("symbol");
		this.callToWrap = callToWrap;
		this.symbol = symbol;
	}
	
	@Override protected MethodPatcher createPatcher(ClassWriter writer, final String classSpec) {
		final MethodPatcher patcher = new MethodPatcher(writer, new MethodPatcherFactory() {
			@Override public MethodVisitor createMethodVisitor(String name, String desc, MethodVisitor parent, MethodLogistics logistics) {
				return new WrapWithSymbol(parent);
			}
		});
		
		return patcher;
	}
	
	private class WrapWithSymbol extends MethodAdapter {
		public WrapWithSymbol(MethodVisitor mv) {
			super(mv);
		}
		
		@Override public void visitMethodInsn(int opcode, String owner, String name, String desc) {
			if (callToWrap.getClassSpec().equals(owner) &&
					callToWrap.getMethodName().equals(name) &&
					callToWrap.getMethodDescriptor().equals(desc)) {
				createTryFinally(opcode, owner, name, desc);
			} else {
				super.visitMethodInsn(opcode, owner, name, desc);
			}
		}
		
		private void createTryFinally(int opcode, String owner, String name, String desc) {
			Label start = new Label();
			Label end = new Label();
			Label handler = new Label();
			Label restOfMethod = new Label();
			mv.visitTryCatchBlock(start, end, handler, null);
			mv.visitLabel(start);
			mv.visitLdcInsn(symbol);
			mv.visitMethodInsn(Opcodes.INVOKESTATIC, "lombok/patcher/Symbols", "push", "(Ljava/lang/String;)V");
			mv.visitMethodInsn(opcode, owner, name, desc);
			mv.visitLabel(end);
			mv.visitMethodInsn(Opcodes.INVOKESTATIC, "lombok/patcher/Symbols", "pop", "()V");
			mv.visitJumpInsn(Opcodes.GOTO, restOfMethod);
			mv.visitLabel(handler);
			mv.visitMethodInsn(Opcodes.INVOKESTATIC, "lombok/patcher/Symbols", "pop", "()V");
			mv.visitInsn(Opcodes.ATHROW);
			mv.visitLabel(restOfMethod);
		}
	}
}
