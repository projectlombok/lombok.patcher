/*
 * Copyright (C) 2014 The Project Lombok Authors.
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

import java.io.UnsupportedEncodingException;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.charset.Charset;

public class HomeFinder {
	private static String urlDecode(String in) {
		try {
			return URLDecoder.decode(in, Charset.defaultCharset().name());
		} catch (UnsupportedEncodingException e) {
			try {
				return URLDecoder.decode(in, "UTF-8");
			} catch (UnsupportedEncodingException e1) {
				return in;
			}
		}
	}
	
	public static String findHomeOfSelf() {
		return findHomeOfClass(HomeFinder.class);
	}
	
	public static String findHomeOfClass(Class<?> context) {
		String name = context.getName();
		int idx = name.lastIndexOf('.');
		if (idx > -1) name = name.substring(idx + 1);
		URL selfURL = context.getResource(name + ".class");
		String self = selfURL.toString();
		if (self.startsWith("file:/"))  {
			self = urlDecode(self.substring(5));
			String suffix = "/" + context.getPackage().getName().replace('.', '/') + "/" + name + ".class";
			if (self.endsWith(suffix)) self = self.substring(0, self.length() - suffix.length());
			else throw new IllegalArgumentException("Unknown path structure: " + self);
		} else if (self.startsWith("jar:")) {
			int sep = self.indexOf('!');
			if (sep == -1) throw new IllegalArgumentException("No separator in jar protocol: " + self);
			String jarLoc = self.substring(4, sep);
			if (jarLoc.startsWith("file:/")) {
				jarLoc = urlDecode(jarLoc.substring(5));
				int lastSlash = jarLoc.lastIndexOf('/');
				if (lastSlash > 0) jarLoc = jarLoc.substring(0, lastSlash);
				self = jarLoc;
			} else throw new IllegalArgumentException("Unknown path structure: " + self);
		} else {
			throw new IllegalArgumentException("Unknown protocol: " + self);
		}
		
		if (self.isEmpty()) self = "/";
		
		return self;
	}
	
	public static void main(String[] args) {
		System.out.println(findHomeOfSelf());
	}
}
