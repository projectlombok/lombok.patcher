/*
 * Copyright (C) 2009-2017 The Project Lombok Authors.
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Represents a method you write yourself; calls to it will be inserted into
 * code-to-be-patched by a {@code PatchScript}.
 * 
 * For inner classes, use a dollar and not a dot to separate 
 * The {@code classSpec} property should be written in JVM style, such as
 * {@code java/lang/String}.
 * 
 * @see <a href="http://java.sun.com/docs/books/jvms/second_edition/html/ClassFile.doc.html#1169">JVM Spec on method names and descriptors</a>
 */
public class Hook {
	private final String className;
	private final String methodName;
	private final String returnType;
	private final List<String> parameterTypes;
	
	public Hook(String className, String methodName, String returnType, String... parameterTypes) {
		if (className == null) throw new NullPointerException("classSpec");
		if (methodName == null) throw new NullPointerException("methodName");
		if (returnType == null) throw new NullPointerException("returnType");
		if (parameterTypes == null) throw new NullPointerException("parameterTypes");
		
		this.className = className;
		this.methodName = methodName;
		this.returnType = returnType;
		List<String> params = new ArrayList<String>();
		for (String param : parameterTypes) params.add(param);
		this.parameterTypes = Collections.unmodifiableList(params);
	}
	
	public boolean isConstructor() {
		return "<init>".equals(methodName);
	}
	
	public String getClassName() {
		return className;
	}
	
	public String getMethodName() {
		return methodName;
	}
	
	public String getReturnType() {
		return returnType;
	}
	
	public List<String> getParameterTypes() {
		return parameterTypes;
	}
	
	public String getClassSpec() {
		return convertType(className);
	}
	
	public String getMethodDescriptor() {
		StringBuilder out = new StringBuilder();
		out.append("(");
		for (String p : parameterTypes) out.append(toSpec(p));
		out.append(")");
		out.append(toSpec(returnType));
		
		return out.toString();
	}
	
	private static final Map<String, String> PRIMITIVES; static {
		Map<String, String> m = new HashMap<String, String>();
		m.put("int", "I");
		m.put("long", "J");
		m.put("short", "S");
		m.put("byte", "B");
		m.put("char", "C");
		m.put("double", "D");
		m.put("float", "F");
		m.put("void", "V");
		m.put("boolean", "Z");
		PRIMITIVES = Collections.unmodifiableMap(m);
	}
	
	public static String toSpec(String type) {
		StringBuilder out = new StringBuilder();
		while (type.endsWith("[]")) {
			type = type.substring(0, type.length() - 2);
			out.append("[");
		}
		
		String p = PRIMITIVES.get(type);
		if (p != null) {
			out.append(p);
			return out.toString();
		}
		
		out.append("L");
		out.append(convertType(type));
		out.append(';');
		return out.toString();
	}
	
	public static String convertType(String type) {
		StringBuilder out = new StringBuilder();
		for (String part : type.split("\\.")) {
			if (out.length() > 0) out.append('/');
			out.append(part);
		}
		return out.toString();
	}
	
	@Override public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((className == null) ? 0 : className.hashCode());
		result = prime * result + ((methodName == null) ? 0 : methodName.hashCode());
		result = prime * result + ((parameterTypes == null) ? 0 : parameterTypes.hashCode());
		result = prime * result + ((returnType == null) ? 0 : returnType.hashCode());
		return result;
	}
	
	@Override public boolean equals(Object obj) {
		if (this == obj) return true;
		if (obj == null) return false;
		if (getClass() != obj.getClass()) return false;
		Hook other = (Hook) obj;
		if (className == null) {
			if (other.className != null) return false;
		} else if (!className.equals(other.className)) return false;
		if (methodName == null) {
			if (other.methodName != null) return false;
		} else if (!methodName.equals(other.methodName)) return false;
		if (parameterTypes == null) {
			if (other.parameterTypes != null) return false;
		} else if (!parameterTypes.equals(other.parameterTypes)) return false;
		if (returnType == null) {
			if (other.returnType != null) return false;
		} else if (!returnType.equals(other.returnType)) return false;
		return true;
	}
	
	@Override public String toString() {
		return "Hook [className=" + className + ", methodName=" + methodName + ", returnType=" + returnType + ", parameterTypes=" + parameterTypes + "]";
	}
}
