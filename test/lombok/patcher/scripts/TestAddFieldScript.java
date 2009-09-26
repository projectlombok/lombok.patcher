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

import static org.junit.Assert.*;
import static lombok.patcher.scripts.ScriptTestUtils.*;

import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

import org.junit.Test;
import org.objectweb.asm.Opcodes;

public class TestAddFieldScript {
	@Test
	public void testAddFieldScript() throws Exception {
		InputStream raw = TestAddFieldScript.class.getResourceAsStream("/lombok/patcher/scripts/TestAddFieldScriptEx1.class");
		byte[] pretransform = readFromStream(raw);
		byte[] posttransform = new AddFieldScript("lombok.patcher.scripts.TestAddFieldScriptEx1",
				Opcodes.ACC_STATIC | Opcodes.ACC_PROTECTED, "$test", "I")
				.patch("lombok/patcher/scripts/TestAddFieldScriptEx1", pretransform);
		Class<?> ex1 = loadRaw("lombok.patcher.scripts.TestAddFieldScriptEx1", posttransform);
		Method checkMethod = ex1.getMethod("check", String.class, int.class);
		checkMethod.setAccessible(true);
		assertTrue((Boolean)checkMethod.invoke(null, "$test", Modifier.STATIC | Modifier.PROTECTED));
		boolean pass = false;
		try {
			checkMethod.invoke(null, "$test2", Modifier.STATIC | Modifier.PROTECTED);
		} catch ( InvocationTargetException expected ) {
			pass = expected.getCause() instanceof NoSuchFieldException;
		}
		
		if (!pass) fail("$test2 was never added and should thus have thrown a NoSuchFieldException.");
	}
}

class TestAddFieldScriptEx1 {
	int x;
	
	public static boolean check(String fieldName, int modifiers) throws Exception {
		Field f = TestAddFieldScriptEx1.class.getDeclaredField(fieldName);
		return f.getModifiers() == modifiers;
	}
}
