/*
 * Copyright (C) 2009 The Project Lombok Authors.
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
import static junit.framework.Assert.*;

import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import lombok.patcher.Hook;
import lombok.patcher.MethodTarget;
import lombok.patcher.Symbols;
import lombok.patcher.TransplantMapper;

import org.junit.Test;

public class TestSymbols {
	@Test
	public void testSymbols() throws Throwable {
		InputStream raw = TestSymbols.class.getResourceAsStream("/lombok/patcher/scripts/TestSymbolsEx1.class");
		byte[] pretransform = readFromStream(raw);
		byte[] posttransform = ScriptBuilder.setSymbolDuringMethodCall()
				.target(new MethodTarget("lombok.patcher.scripts.TestSymbolsEx1", "aMethod"))
				.callToWrap(new Hook("lombok.patcher.scripts.TestSymbolsEx1", "bMethod", "void"))
				.symbol("Foobar").build().patch("lombok/patcher/scripts/TestSymbolsEx1", pretransform, TransplantMapper.IDENTITY_MAPPER);
		
		Class<?> ex1 = loadRaw("lombok.patcher.scripts.TestSymbolsEx1", posttransform);
		Method aMethod = ex1.getMethod("aMethod");
		Constructor<?> ex1Constructor = ex1.getDeclaredConstructor();
		aMethod.setAccessible(true);
		ex1Constructor.setAccessible(true);
		
		Object instance = ex1Constructor.newInstance();
		
		assertTrue("marker-preinvoke", Symbols.isEmpty());
		try {
			aMethod.invoke(instance);
		} catch (InvocationTargetException e) {
			throw e.getCause();
		}
		assertTrue("marker-postinvoke", Symbols.isEmpty());
	}
}

class TestSymbolsEx1 {
	public boolean marker = false;
	public void aMethod() {
		cMethod();
		bMethod();
	}
	
	public void cMethod() {
		assertFalse(Symbols.hasSymbol("Foobar"));
		assertFalse(Symbols.hasTail("Foobar"));
		assertEquals(0, Symbols.size());
	}
	
	public void bMethod() {
		assertTrue(Symbols.hasSymbol("Foobar"));
		assertTrue(Symbols.hasTail("Foobar"));
		assertEquals(1, Symbols.size());
	}
}
