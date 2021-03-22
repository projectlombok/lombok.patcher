/*
 * Copyright (C) 2009-2021 The Project Lombok Authors.
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

import java.util.ArrayList;
import java.util.List;

import lombok.patcher.Hook;
import lombok.patcher.MethodLogistics;
import lombok.patcher.TargetMatcher;
import lombok.patcher.TransplantMapper;

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
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
	private final boolean report;
	
	@Override public String getPatchScriptName() {
		return "set symbol " + symbol + " if " + callToWrap.getMethodName() + " is invoked in " + describeMatchers();
	}
	
	SetSymbolDuringMethodCallScript(List<TargetMatcher> matchers, Hook callToWrap, String symbol, boolean report) {
		super(matchers);
		if (callToWrap == null) throw new NullPointerException("callToWrap");
		if (symbol == null) throw new NullPointerException("symbol");
		this.callToWrap = callToWrap;
		this.symbol = symbol;
		this.report = report;
	}
	
	@Override protected MethodPatcher createPatcher(ClassWriter writer, final String classSpec, TransplantMapper transplantMapper) {
		final List<WrapperMethodDescriptor> descriptors = new ArrayList<WrapperMethodDescriptor>();
		
		final MethodPatcher patcher = new MethodPatcher(writer, transplantMapper, new MethodPatcherFactory() {
			public MethodVisitor createMethodVisitor(String name, String desc, MethodVisitor parent, MethodLogistics logistics) {
				return new WrapWithSymbol(name, parent, classSpec, descriptors);
			}
		}) {
			@Override public void visitEnd() {
				for (WrapperMethodDescriptor wmd : descriptors) {
					makeWrapperMethod(this, wmd);
				}
				super.visitEnd();
			}
		};
		
		return patcher;
	}
	
	private void makeWrapperMethod(ClassVisitor cv, WrapperMethodDescriptor wmd) {
		MethodVisitor mv = cv.visitMethod(Opcodes.ACC_SYNTHETIC | Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC, wmd.getWrapperName(), wmd.getWrapperDescriptor(), null, null);
		
		MethodLogistics logistics = new MethodLogistics(Opcodes.ACC_STATIC, wmd.getWrapperDescriptor());
		
		mv.visitCode();
		Label start = new Label();
		Label end = new Label();
		Label handler = new Label();
		mv.visitTryCatchBlock(start, end, handler, null);
		mv.visitLabel(start);
		mv.visitLdcInsn(symbol);
		mv.visitMethodInsn(Opcodes.INVOKESTATIC, "lombok/patcher/Symbols", "push", "(Ljava/lang/String;)V", false);
		for (int i = 0; i < logistics.getParamCount(); i++) {
			logistics.generateLoadOpcodeForParam(i, mv);
		}
		mv.visitMethodInsn(wmd.getOpcode(), wmd.getOwner(), wmd.getName(), wmd.getTargetDescriptor(), wmd.isItf());
		mv.visitLabel(end);
		mv.visitMethodInsn(Opcodes.INVOKESTATIC, "lombok/patcher/Symbols", "pop", "()V", false);
		logistics.generateReturnOpcode(mv);
		mv.visitLabel(handler);
		mv.visitFrame(Opcodes.F_FULL, 0, null, 1, new Object[] {"java/lang/Throwable"});
		mv.visitMethodInsn(Opcodes.INVOKESTATIC, "lombok/patcher/Symbols", "pop", "()V", false);
		mv.visitInsn(Opcodes.ATHROW);
		mv.visitMaxs(Math.max(1, logistics.getParamCount()), logistics.getParamCount());
		mv.visitEnd();
	}
	
	private class WrapWithSymbol extends MethodVisitor {
		private final String selfMethodName;
		private final String selfTypeName;
		private final List<WrapperMethodDescriptor> descriptors;
		
		public WrapWithSymbol(String selfMethodName, MethodVisitor mv, String selfTypeName, List<WrapperMethodDescriptor> descriptors) {
			super(Opcodes.ASM9, mv);
			this.selfMethodName = selfMethodName;
			this.selfTypeName = selfTypeName;
			this.descriptors = descriptors;
		}
		
		@Override public void visitMethodInsn(int opcode, String owner, String name, String desc, boolean itf) {
			boolean addOwner;
			if (opcode == Opcodes.INVOKEINTERFACE || opcode == Opcodes.INVOKEVIRTUAL) addOwner = true;
			else if (opcode == Opcodes.INVOKESTATIC) addOwner = false;
			else {
				super.visitMethodInsn(opcode, owner, name, desc, itf);
				return;
			}
			
			if (!callToWrap.getClassSpec().equals(owner) ||
					!callToWrap.getMethodName().equals(name) ||
					!callToWrap.getMethodDescriptor().equals(desc)) {
				
				super.visitMethodInsn(opcode, owner, name, desc, itf);
				return;
			}
			
			String fixedDesc;
			if (addOwner) {
				fixedDesc = "(L" + callToWrap.getClassSpec() + ";" + desc.substring(1);
			} else {
				fixedDesc = desc;
			}
			
			WrapperMethodDescriptor wmd = new WrapperMethodDescriptor(descriptors.size(), opcode, owner, name, fixedDesc, desc, itf);
			
			if (report) System.out.println("Changing method " + selfTypeName + "::" + selfMethodName + " wrapping call to " + owner + "::" + name + " to set symbol " + symbol);
			super.visitMethodInsn(Opcodes.INVOKESTATIC, selfTypeName, wmd.getWrapperName(), fixedDesc, false);
			descriptors.add(wmd);
		}
	}
}
