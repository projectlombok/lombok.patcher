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

import static lombok.patcher.scripts.ScriptTestUtils.*;
import static org.junit.Assert.*;

import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

import lombok.patcher.Hook;
import lombok.patcher.MethodTarget;
import lombok.patcher.StackRequest;

import org.junit.Test;

public class TestWrapReturnValuesScript {
	@Test
	public void testWrapReturnValuesScript() throws Exception {
		InputStream raw = TestWrapReturnValuesScript.class.getResourceAsStream("/lombok/patcher/scripts/TestWrapReturnValuesScriptEx1.class");
		byte[] pretransform = readFromStream(raw);
		byte[] posttransform = ScriptBuilder.wrapReturnValue()
				.target(new MethodTarget("lombok.patcher.scripts.TestWrapReturnValuesScriptEx1", "foo",
						"int", "int", "java.lang.String[]"))
				.wrapMethod(new Hook("lombok/patcher/scripts/TestWrapReturnValuesScript$TestWrapReturnValuesScriptEx2",
						"hook1", "(ILjava/lang/Object;I[Ljava/lang/String;)I"))
				.transplant().request(StackRequest.THIS, StackRequest.RETURN_VALUE, StackRequest.PARAM1, StackRequest.PARAM2)
				.build().patch("lombok/patcher/scripts/TestWrapReturnValuesScriptEx1", pretransform);
		Class<?> ex1 = loadRaw("lombok.patcher.scripts.TestWrapReturnValuesScriptEx1", posttransform);
		Method fooMethod = ex1.getMethod("foo", int.class, String[].class);
		Constructor<?> ex1Constructor = ex1.getDeclaredConstructor();
		fooMethod.setAccessible(true);
		ex1Constructor.setAccessible(true);
		
		assertEquals("patched return value", 160, (int)(Integer)fooMethod.invoke(ex1Constructor.newInstance(), 50, new String[] { "foo", "bar"}));
		assertEquals("patched return value", 20, (int)(Integer)fooMethod.invoke(ex1Constructor.newInstance(), 5, null));
	}
	
	public static class TestWrapReturnValuesScriptEx2 {
		public static int hook1(int supposedReturnValue, Object thisRef, int param1, String[] param2) {
			assertEquals("supposedReturnValue", param1 < 10 ? 10 : 80, supposedReturnValue);
			assertEquals("typeOf thisRef", "lombok.patcher.scripts.TestWrapReturnValuesScriptEx1", thisRef.getClass().getName());
			assertTrue("param1", param1 == 50 || param1 == 5);
			if (param1 == 50)
				assertArrayEquals("param2", new String[] { "foo", "bar" }, param2);
			else
				assertNull("param2", param2);
			
			return supposedReturnValue *2;
		}
	}
}

@SuppressWarnings("all")
class TestWrapReturnValuesScriptEx1 {
	public int foo(int x, String[] y) {
		if (x < 10) return 10;
		return 80;
	}
}
