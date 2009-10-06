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

import static org.junit.Assert.*;

import org.junit.Test;

public class TestMethodTarget {
	@Test
	public void arrayTypeMatching() {
		assertTrue("Matching String[][] failed.", MethodTarget.typeSpecMatch("[[Ljava/lang/String;", "java.lang.String[][]"));
		assertFalse("Matching String[][] failed.", MethodTarget.typeSpecMatch("[[Ljava/lang/String;", "java.lang.String[][][]"));
		assertFalse("Matching String[][] failed.", MethodTarget.typeSpecMatch("[[Ljava/lang/String;", "java.lang.String[]"));
	}
	
	@Test
	public void primitiveTypeMatching() {
		assertTrue("Primitive fail: int", MethodTarget.typeSpecMatch("I", "int"));
		assertTrue("Primitive fail: byte[]", MethodTarget.typeSpecMatch("[B", "byte[]"));
		assertTrue("Primitive fail: short", MethodTarget.typeSpecMatch("S", "short"));
		assertTrue("Primitive fail: long", MethodTarget.typeSpecMatch("J", "long"));
		assertTrue("Primitive fail: float", MethodTarget.typeSpecMatch("F", "float"));
		assertTrue("Primitive fail: double", MethodTarget.typeSpecMatch("D", "double"));
		assertTrue("Primitive fail: char", MethodTarget.typeSpecMatch("C", "char"));
		assertTrue("Primitive fail: boolean", MethodTarget.typeSpecMatch("Z", "boolean"));
		assertTrue("Primitive fail: void", MethodTarget.typeSpecMatch("V", "void"));
		
		assertFalse(MethodTarget.typeSpecMatch("[I", "int"));
		assertFalse(MethodTarget.typeSpecMatch("I", "Int"));
		assertFalse(MethodTarget.typeSpecMatch("J", "int"));
	}
	
	@Test
	public void innerClassTypeMatching() {
		assertTrue(MethodTarget.typeSpecMatch("[Ljava/util/Map$Entry;", "java.util.Map.Entry[]"));
	}
	
	@Test
	public void fullSpecMatch() {
		TargetMatcher toLowerCase = new MethodTarget("java.lang.String", "toLowerCase", "java.lang.String");
		TargetMatcher mapPut = new MethodTarget("java.util.Map", "put", "java.lang.Object", "java.lang.Object", "java.lang.Object");
		TargetMatcher listAdd = new MethodTarget("java.util.List", "add", "boolean", "java.lang.Object");
		TargetMatcher listToArray1 = new MethodTarget("java.util.ArrayList", "toArray", "java.lang.Object[]");
		TargetMatcher listToArray2 = new MethodTarget("java.util.ArrayList", "toArray", "java.lang.Object[]", "java.lang.Object[]");
		TargetMatcher threadSleep2 = new MethodTarget("java.lang.Thread", "sleep", "void", "long", "int");
		
		assertTrue(toLowerCase.matches("java/lang/String", "toLowerCase", "()Ljava/lang/String;"));
		assertTrue(mapPut.matches("java/util/Map", "put", "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;"));
		assertTrue(listAdd.matches("java/util/List", "add", "(Ljava/lang/Object;)Z"));
		assertTrue(listToArray1.matches("java/util/ArrayList", "toArray", "()[Ljava/lang/Object;"));
		assertTrue(listToArray2.matches("java/util/ArrayList", "toArray", "([Ljava/lang/Object;)[Ljava/lang/Object;"));
		assertTrue(threadSleep2.matches("java/lang/Thread", "sleep", "(JI)V"));
		
		assertFalse(threadSleep2.matches("java/lang/Thread", "sleep", "(J)V"));
		assertFalse(threadSleep2.matches("java/lang/Thread", "sleep", "(JIJ)V"));
	}
	
	@Test
	public void partialSpecMatch() {
		TargetMatcher toLowerCase = new MethodTarget("java.lang.String", "toLowerCase");
		TargetMatcher mapPut = new MethodTarget("java.util.Map", "put");
		TargetMatcher listAdd = new MethodTarget("java.util.List", "add");
		TargetMatcher listToArray = new MethodTarget("java.util.ArrayList", "toArray");
		TargetMatcher threadSleep = new MethodTarget("java.lang.Thread", "sleep");
		
		assertTrue(toLowerCase.matches("java/lang/String", "toLowerCase", "()Ljava/lang/String;"));
		assertTrue(mapPut.matches("java/util/Map", "put", "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;"));
		assertTrue(listAdd.matches("java/util/List", "add", "(Ljava/lang/Object;)Z"));
		assertTrue(listToArray.matches("java/util/ArrayList", "toArray", "()[Ljava/lang/Object;"));
		assertTrue(listToArray.matches("java/util/ArrayList", "toArray", "([Ljava/lang/Object;)[Ljava/lang/Object;"));
		assertTrue(threadSleep.matches("java/lang/Thread", "sleep", "(JI)V"));
		assertTrue(threadSleep.matches("java/lang/Thread", "sleep", "(J)V"));
		
		assertFalse(threadSleep.matches("java/lang/Thread", "sleep2", "(JI)V"));
		assertFalse(threadSleep.matches("java/lang/Thread", "slee", "(JI)V"));
	}
}
