/*
 * Copyright (C) 2015 The Project Lombok Authors.
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

/**
 * If you are transplanting code into classes which could have varying class file numbers, you probably need to supply at least 2 variants of the exact same class file
 * containing the same code; for example, pre 1.5 you can't use generics at all, and starting with 1.6 you need frame info.
 */
public interface TransplantMapper {
	/**
	 * By default, a transplant call will take the class of the 'hook' verbatim, but you can specify an alternate path.
	 * 
	 * For example, if the hook is from class com.foo.bar.Baz, then by default, the file "com/foo/bar/Baz.class" is read. However,
	 * if you return "Class50/com/foo/bar/Baz.class" here, the file "Class50/com/foo/bar/Baz.class" is used. Just return {@code resourceName} if no mapping is needed.
	 */
	String mapResourceName(int classFileFormatVersion, String resourceName);
	
	public static final TransplantMapper IDENTITY_MAPPER = new TransplantMapper() {
		public String mapResourceName(int classFileFormatVersion, String resourceName) {
			return resourceName;
		}
	};
}
