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
import java.lang.reflect.Field;
import java.lang.reflect.Method;

import lombok.patcher.Hook;
import lombok.patcher.MethodTarget;
import lombok.patcher.StackRequest;

import org.junit.Test;

public class TestExitFromMethodEarlyScript {
	@Test
	public void testExitEarlyScript() throws Exception {
		InputStream raw = TestExitFromMethodEarlyScript.class.getResourceAsStream("/lombok/patcher/scripts/TestExitFromMethodEarlyScriptEx1.class");
		byte[] pretransform = readFromStream(raw);
		byte[] posttransform1 = ScriptBuilder.exitEarly()
				.target(new MethodTarget("lombok.patcher.scripts.TestExitFromMethodEarlyScriptEx1", "voidReturnMethod"))
				.decisionMethod(new Hook("lombok/patcher/scripts/TestExitFromMethodEarlyScript$TestExitFromMethodEarlyScriptEx2",
						"hook1", "(Ljava/lang/Object;ILjava/lang/String;)Z"))
				.request(StackRequest.THIS, StackRequest.PARAM1, StackRequest.PARAM2).build()
				.patch("lombok/patcher/scripts/TestExitFromMethodEarlyScriptEx1", pretransform);
		byte[] posttransform2 = ScriptBuilder.exitEarly()
				.target(new MethodTarget("lombok.patcher.scripts.TestExitFromMethodEarlyScriptEx1", "returnsSomething", "double"))
				.decisionMethod(new Hook("lombok/patcher/scripts/TestExitFromMethodEarlyScript$TestExitFromMethodEarlyScriptEx2",
						"hook2", "()Z"))
				.valueMethod(new Hook("lombok/patcher/scripts/TestExitFromMethodEarlyScript$TestExitFromMethodEarlyScriptEx2",
						"hook3", "()D"))
				.transplant().build()
				.patch("lombok/patcher/scripts/TestExitFromMethodEarlyScriptEx1", posttransform1);
		
		Class<?> ex1 = loadRaw("lombok.patcher.scripts.TestExitFromMethodEarlyScriptEx1", posttransform2);
		Method voidMethod = ex1.getMethod("voidReturnMethod", int.class, String.class);
		Method retMethod = ex1.getMethod("returnsSomething");
		Field markerField = ex1.getField("marker");
		Constructor<?> ex1Constructor = ex1.getDeclaredConstructor();
		voidMethod.setAccessible(true);
		retMethod.setAccessible(true);
		ex1Constructor.setAccessible(true);
		markerField.setAccessible(true);
		
		Object instance = ex1Constructor.newInstance();
		
		assertFalse("marker-preinvoke", (Boolean)markerField.get(instance));
		voidMethod.invoke(instance, 5, "foo");
		assertFalse("marker-interinvoke", (Boolean)markerField.get(instance));
		voidMethod.invoke(instance, 50, null);
		assertTrue("marker-postinvoke", (Boolean)markerField.get(instance));
		
		assertEquals("returnsSomething", Double.valueOf(Double.NaN), retMethod.invoke(instance));
	}
	
	public static class TestExitFromMethodEarlyScriptEx2 {
		public static boolean hook1(Object thisRef, int param1, String param2) {
			assertEquals("typeOf thisRef", "lombok.patcher.scripts.TestExitFromMethodEarlyScriptEx1", thisRef.getClass().getName());
			assertTrue("param1", param1 == 50 || param1 == 5);
			return param1 == 5;
		}
		
		public static boolean hook2() {
			return true;
		}
		
		public static double hook3() {
			return Double.NaN;
		}
	}
}

class TestExitFromMethodEarlyScriptEx1 {
	public boolean marker = false;
	public void voidReturnMethod(int a, String b) {
		if (a < 10) fail("I shouldn't run");
		marker = true;
	}
	
	public double returnsSomething() {
		return 5.0;
	}
}
