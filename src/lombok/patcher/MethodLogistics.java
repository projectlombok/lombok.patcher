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
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import lombok.Getter;

import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

/**
 * Method rewriting support class.
 * 
 * MethodLogistics tracks the return type as well as all parameter types, and can tell you exactly where a given parameter
 * exists in the local method space. This tracking is non-trivial, as doubles and longs take up 2 spaces, both on the stack
 * and in the local method space.
 */
public class MethodLogistics {
	private final int staticOffset;
	
	@Getter
	private final int returnOpcode;
	private final int returnSize;
	
	private final List<Integer> loadOpcodes;
	@SuppressWarnings("unused")
	private final List<Integer> paramSizes;
	private final List<Integer> paramIndices;
	
	/**
	 * Creates a new logistics support class given a method descriptor and the access flags.
	 * 
	 * These can be copied verbatim from what ASM gives you.
	 * 
	 * @see org.objectweb.asm.ClassVisitor#visitMethod(int, String, String, String, String[])
	 */
	public MethodLogistics(int accessFlags, String descriptor) {
		this.staticOffset = ((accessFlags & Opcodes.ACC_STATIC) != 0) ? 0 : 1;
		List<String> specs = MethodTarget.decomposeFullDesc(descriptor);
		Iterator<String> it = specs.iterator();
		String returnSpec = it.next();
		returnSize = sizeOf(returnSpec);
		returnOpcode = returnOpcodeFor(returnSpec);
		int index = staticOffset;
		
		List<Integer> paramSizes = new ArrayList<Integer>();
		List<Integer> paramIndices = new ArrayList<Integer>();
		List<Integer> loadOpcodes = new ArrayList<Integer>();
		
		while (it.hasNext()) {
			String spec = it.next();
			int size = sizeOf(spec);
			paramSizes.add(size);
			paramIndices.add(index);
			loadOpcodes.add(loadOpcodeFor(spec));
			index += size;
		}
		
		this.paramSizes = Collections.unmodifiableList(paramSizes);
		this.paramIndices = Collections.unmodifiableList(paramIndices);
		this.loadOpcodes = Collections.unmodifiableList(loadOpcodes);
	}
	
	/**
	 * @return {@code true} if the method is static.
	 */
	public boolean isStatic() {
		return staticOffset == 0;
	}
	
	/**
	 * Writes the opcode to load the i-th parameter to the supplied {@code MethodVisitor}.
	 * 
	 * @param index 0-based index into the method parameter (the index-th parameter will be loaded).
	 * @param mv The opcode will be generated in this MethodVisitor object.
	 */
	public void generateLoadOpcodeForParam(int index, MethodVisitor mv) {
		mv.visitVarInsn(loadOpcodes.get(index), paramIndices.get(index));
	}
	
	/**
	 * Writes the opcode to load 'this', or, for static methods, 'null'.
	 * 
	 * @param mv The opcode will be generated in this MethodVisitor object.
	 */
	public void generateLoadOpcodeForThis(MethodVisitor mv) {
		if (isStatic()) mv.visitInsn(Opcodes.ACONST_NULL);
		else mv.visitVarInsn(Opcodes.ALOAD, 0);
	}
	
	/**
	 * Writes the opcode to return from this method.
	 * Writes the appropriate opcode for this method's return type, so RETURN, ARETURN, IRETURN, LRETURN, FRETURN, or DRETURN.
	 * 
	 * @param mv The opcode will be generated in this MethodVisitor object.
	 */
	public void generateReturnOpcode(MethodVisitor mv) {
		mv.visitInsn(returnOpcode);
	}
	
	/**
	 * Generates either POP or POP2 depending on the size of the return type - if a value of the return type is on the stack,
	 * it'll be removed exactly by the generated POP.
	 * 
	 * @param mv The opcode will be generated in this MethodVisitor object.
	 */
	public void generatePopForReturn(MethodVisitor mv) {
		mv.visitInsn(returnSize == 2 ? Opcodes.POP2 : Opcodes.POP);
	}
	
	/**
	 * Generates either DUP or DUP2 depending on the size of the return type - if a value of the return type is on the stack,
	 * it'll be duplicated exactly by the generated POP.
	 * 
	 * @param mv The opcode will be generated in this MethodVisitor object.
	 */
	public void generateDupForReturn(MethodVisitor mv) {
		mv.visitInsn(returnSize == 2 ? Opcodes.DUP2 : Opcodes.DUP);
	}
	
	/**
	 * Generates an instruction to duplicate an object on the stack. The object is of the stated type, in JVM typespec, so
	 * {@code I} refers to an integer, and {@code Ljava/lang/Object;} would refer to an object.
	 * 
	 * @param type A type spec in JVM format.
	 * @param mv This visitor will be given the call to visit DUP2, DUP, or nothing dependent on the type name.
	 */
	public static void generateDupForType(String type, MethodVisitor mv) {
		switch (sizeOf(type)) {
		default:
		case 1:
			mv.visitInsn(Opcodes.DUP);
			break;
		case 2:
			mv.visitInsn(Opcodes.DUP2);
			break;
		case 0:
			//Do nothing
			break;
		}
	}
	
	private static int loadOpcodeFor(String spec) {
		switch (spec.charAt(0)) {
		case 'D':
			return Opcodes.DLOAD;
		case 'J':
			return Opcodes.LLOAD;
		case 'F':
			return Opcodes.FLOAD;
		case 'I':
		case 'S':
		case 'B':
		case 'Z':
			return Opcodes.ILOAD;
		case 'V':
			throw new IllegalArgumentException("There's no load opcode for 'void'");
		case 'L':
		case '[':
			return Opcodes.ALOAD;
		}
		
		throw new IllegalStateException("Uhoh - bug - unrecognized JVM type: " + spec);
	}
	
	private static int returnOpcodeFor(String returnSpec) {
		switch (returnSpec.charAt(0)) {
		case 'D':
			return Opcodes.DRETURN;
		case 'J':
			return Opcodes.LRETURN;
		case 'F':
			return Opcodes.FRETURN;
		case 'I':
		case 'S':
		case 'B':
		case 'Z':
			return Opcodes.IRETURN;
		case 'V':
			return Opcodes.RETURN;
		case 'L':
		case '[':
			return Opcodes.ARETURN;
		}
		
		throw new IllegalStateException("Uhoh - bug - unrecognized JVM type: " + returnSpec);
	}
	
	private static int sizeOf(String spec) {
		switch (spec.charAt(0)) {
		case 'D':
		case 'J':
			return 2;
		case 'V':
			return 0;
		default:
			return 1;
		}
	}
}
