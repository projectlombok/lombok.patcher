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

import org.junit.Test;

public class TestSymbols {
	@Test
	public void testSymbols() throws Throwable {
		InputStream raw = TestSymbols.class.getResourceAsStream("/lombok/patcher/scripts/TestSymbolsEx1.class");
		byte[] pretransform = readFromStream(raw);
		byte[] posttransform = ScriptBuilder.setSymbolDuringMethodCall()
				.target(new MethodTarget("lombok.patcher.scripts.TestSymbolsEx1", "aMethod"))
				.callToWrap(new Hook("lombok/patcher/scripts/TestSymbolsEx1", "bMethod", "()V"))
				.symbol("Foobar").build().patch("lombok/patcher/scripts/TestSymbolsEx1", pretransform);
		
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
