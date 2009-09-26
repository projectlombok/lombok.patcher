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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

class ScriptTestUtils {
	static byte[] readFromStream(InputStream raw) throws IOException {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		try {
			byte[] b = new byte[4096];
			while (true) {
				int r = raw.read(b);
				if (r == -1) break;
				out.write(b, 0, r);
			}
			
			return out.toByteArray();
		} finally {
			raw.close();
		}
	}
	
	static Class<?> loadRaw(String className, byte[] classByteCode) throws ClassNotFoundException {
		return Class.forName(className, true, getRawLoader(className, classByteCode));
	}
	
	static ClassLoader getRawLoader(final String className, final byte[] classByteCode) {
		return new ClassLoader() {
			@Override protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
				if (name.equals(className)) {
					return defineClass(name, classByteCode, 0, classByteCode.length);
				}
				return super.loadClass(name, resolve);
			}
		};
	}
}
