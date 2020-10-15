/*
 * Copyright (C) 2009-2020 The Project Lombok Authors.
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
import java.util.List;

import lombok.patcher.MethodTarget;
import lombok.patcher.PatchScript;
import lombok.patcher.TransplantMapper;

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.Opcodes;

/**
 * Adds a field to any class.
 */
public class AddFieldScript extends PatchScript {
	private final int accessFlags;
	private final List<String> targetClasses;
	private final String fieldName;
	private final String fieldType;
	private final Object value;
	
	@Override public String getPatchScriptName() {
		return "AddField: " + fieldType + " " + fieldName + "to "+ targetClasses;
	}
	
	/**
	 * @param targetClasses The class(es) to add the field to, separated with dots (e.g. java.lang.String).
	 * @param fieldName the name of the field to create.
	 * @param typeSpec the type of the field, in JVM spec (e.g. [I for an int array).
	 */
	AddFieldScript(List<String> targetClasses, int accessFlags, String fieldName, String fieldType, Object value) {
		if (targetClasses == null) throw new NullPointerException("targetClass");
		if (fieldName == null) throw new NullPointerException("fieldName");
		if (fieldType == null) throw new NullPointerException("typeSpec");
		this.accessFlags = accessFlags;
		this.targetClasses = targetClasses;
		this.fieldName = fieldName;
		this.fieldType = fieldType;
		this.value = value;
	}
	
	@Override public boolean wouldPatch(String className) {
		for (String tc : targetClasses) if (MethodTarget.typeMatches(className, tc)) return true;
		return false;
	}
	
	@Override public byte[] patch(String className, byte[] byteCode, TransplantMapper transplantMapper) {
		for (String tc : targetClasses) if (MethodTarget.typeMatches(className, tc)) return runASM(byteCode, false, transplantMapper);
		return null;
	}
	
	@Override protected ClassVisitor createClassVisitor(ClassWriter writer, String classSpec, TransplantMapper transplantMapper) {
		return new ClassVisitor(Opcodes.ASM7, writer) {
			private boolean alreadyAdded = false;
			
			@Override public FieldVisitor visitField(int access, String name, String desc, String signature, Object value) {
				if (name != null && name.equals(fieldName)) alreadyAdded = true;
				return super.visitField(access, name, desc, signature, value);
			}
			
			@Override public void visitEnd() {
				if (!alreadyAdded) {
					FieldVisitor fv = cv.visitField(accessFlags, fieldName, fieldType, null, value);
					fv.visitEnd();
				}
				super.visitEnd();
			}
		};
	}
	
	@Override public Collection<String> getClassesToReload() {
		return targetClasses;
	}
}
