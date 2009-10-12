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

import lombok.patcher.MethodTarget;
import lombok.patcher.PatchScript;

import org.objectweb.asm.ClassAdapter;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.FieldVisitor;

/**
 * Adds a field to any class.
 */
public class AddFieldScript extends PatchScript {
	private final int accessFlags;
	private final String targetClass;
	private final String fieldName;
	private final String fieldType;
	private final Object value;
	
	/**
	 * @param targetClass The class to add the field to, separated with dots (e.g. java.lang.String).
	 * @param fieldName the name of the field to create.
	 * @param typeSpec the type of the field, in JVM spec (e.g. [I for an int array).
	 */
	AddFieldScript(String targetClass, int accessFlags, String fieldName, String fieldType, Object value) {
		if (targetClass == null) throw new NullPointerException("targetClass");
		if (fieldName == null) throw new NullPointerException("fieldName");
		if (fieldType == null) throw new NullPointerException("typeSpec");
		this.accessFlags = accessFlags;
		this.targetClass = targetClass;
		this.fieldName = fieldName;
		this.fieldType = fieldType;
		this.value = value;
	}
	
	@Override public byte[] patch(String className, byte[] byteCode) {
		if (!MethodTarget.typeMatches(className, targetClass)) return null;
		return runASM(byteCode, false);
	}
	
	@Override protected ClassVisitor createClassVisitor(ClassWriter writer, String classSpec) {
		return new ClassAdapter(writer) {
			@Override public void visitEnd() {
				FieldVisitor fv = cv.visitField(accessFlags, fieldName, fieldType, null, value);
				fv.visitEnd();
				super.visitEnd();
			}
		};
	}
	
	@Override public Collection<String> getClassesToReload() {
		return Collections.singleton(targetClass);
	}
}
