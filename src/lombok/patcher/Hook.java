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

import lombok.Data;
import lombok.NonNull;

/**
 * Represents a method you write yourself; calls to it will be inserted into code-to-be-patched by a {@code PatchScript}.
 * 
 * The {@code classSpec} property should be written in JVM style, such as {@code java/lang/String}.
 * 
 * @see http://java.sun.com/docs/books/jvms/second_edition/html/ClassFile.doc.html#1169
 */
@Data
public class Hook {
	private final @NonNull String classSpec;
	private final @NonNull String methodName;
	private final @NonNull String methodDescriptor;
	
	public boolean isConstructor() {
		return "<init>".equals(methodName);
	}
}
