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

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import lombok.patcher.Hook;
import lombok.patcher.StackRequest;
import lombok.patcher.TargetMatcher;

import org.objectweb.asm.Opcodes;

public class ScriptBuilder {
	private ScriptBuilder() throws NoSuchMethodException {
		throw new NoSuchMethodException("ScriptBuilder cannot be instantiated - just use the static methods.");
	}
	
	private static void checkTypeSyntaxSlash(String spec) {
		if (spec.indexOf('.') > -1) throw new IllegalArgumentException(
				"Your type specification includes a dot, but this method wants a slash-separated type specification");
	}
	
	private static void checkTypeSyntaxDot(String spec) {
		if (spec.indexOf('/') > -1) throw new IllegalArgumentException(
				"Your type specification includes a slash, but this method wants a dot-separated type specification");
	}
	
	public static class AddFieldBuilder {
		private int accessFlags;
		private String targetClass;
		private String fieldName;
		private String fieldType;
		private Object value;
		
		private static final int NO_ACCESS_LEVELS = ~(Opcodes.ACC_PUBLIC | Opcodes.ACC_PRIVATE | Opcodes.ACC_PRIVATE);
		
		public AddFieldScript build() {
			if (targetClass == null) throw new IllegalStateException("You have to set a targetClass");
			if (fieldName == null) throw new IllegalStateException("You have to set a fieldName");
			if (fieldType == null) throw new IllegalStateException("You have to set the new field's type by calling fieldType");
			if (value != null) {
				setStatic();
				setFinal();
			}
			return new AddFieldScript(targetClass, accessFlags, fieldName, fieldType, value);
		}
		
		/**
		 * @param targetClass The class to add the field to, separated with dots (e.g. java.lang.String).
		 */
		public AddFieldBuilder targetClass(@SuppressWarnings("hiding") String targetClass) {
			checkTypeSyntaxDot(targetClass);
			this.targetClass = targetClass;
			return this;
		}
		
		public AddFieldBuilder value(@SuppressWarnings("hiding") Object value) {
			this.value = value;
			return this;
		}
		
		/**
		 * @param fieldName the name of the field to create.
		 */
		public AddFieldBuilder fieldName(@SuppressWarnings("hiding") String fieldName) {
			this.fieldName = fieldName;
			return this;
		}
		
		/**
		 * @param fieldType the type of the field, in JVM spec (e.g. [I for an int array).
		 */
		public AddFieldBuilder fieldType(@SuppressWarnings("hiding") String fieldType) {
			checkTypeSyntaxSlash(fieldType);
			this.fieldType = fieldType;
			return this;
		}
		
		public AddFieldBuilder setPublic() {
			accessFlags = (accessFlags & NO_ACCESS_LEVELS) | Opcodes.ACC_PUBLIC;
			return this;
		}
		
		public AddFieldBuilder setPrivate() {
			accessFlags = (accessFlags & NO_ACCESS_LEVELS) | Opcodes.ACC_PRIVATE;
			return this;
		}
		
		public AddFieldBuilder setProtected() {
			accessFlags = (accessFlags & NO_ACCESS_LEVELS) | Opcodes.ACC_PROTECTED;
			return this;
		}
		
		public AddFieldBuilder setPackageAccess() {
			accessFlags = (accessFlags & NO_ACCESS_LEVELS);
			return this;
		}
		
		public AddFieldBuilder setStatic() {
			accessFlags |= Opcodes.ACC_STATIC;
			return this;
		}
		
		public AddFieldBuilder setFinal() {
			accessFlags |= Opcodes.ACC_FINAL;
			return this;
		}
		
		public AddFieldBuilder setVolatile() {
			accessFlags |= Opcodes.ACC_VOLATILE;
			return this;
		}
		
		public AddFieldBuilder setTransient() {
			accessFlags |= Opcodes.ACC_TRANSIENT;
			return this;
		}
	}
	
	public static class ExitEarlyBuilder {
		private List<TargetMatcher> matchers = new ArrayList<TargetMatcher>();
		private Hook decisionMethod, valueMethod;
		private Set<StackRequest> requests = new HashSet<StackRequest>();
		private boolean transplant, insert;
		
		public ExitFromMethodEarlyScript build() {
			if (matchers.isEmpty()) throw new IllegalStateException("You have to set a target method matcher");
			if (decisionMethod == null) throw new IllegalStateException("You have to set a decision method");
			
			return new ExitFromMethodEarlyScript(matchers, decisionMethod, valueMethod, transplant, insert, requests);
		}
		
		public ExitEarlyBuilder target(TargetMatcher matcher) {
			this.matchers.add(matcher);
			return this;
		}
		
		public ExitEarlyBuilder decisionMethod(Hook hook) {
			this.decisionMethod = hook;
			return this;
		}
		
		public ExitEarlyBuilder valueMethod(Hook hook) {
			this.valueMethod = hook;
			return this;
		}
		
		public ExitEarlyBuilder transplant() {
			this.transplant = true;
			this.insert = false;
			return this;
		}
		
		public ExitEarlyBuilder insert() {
			this.transplant = false;
			this.insert = true;
			return this;
		}
		
		public ExitEarlyBuilder request(@SuppressWarnings("hiding") StackRequest... requests) {
			for (StackRequest r : requests) {
				if (r == StackRequest.RETURN_VALUE) throw new IllegalArgumentException(
						"You cannot ask for the tentative return value in ExitFromMethodEarlyScript.");
				this.requests.add(r);
			}
			
			return this;
		}
	}
	
	public static class ReplaceMethodCallBuilder {
		private List<TargetMatcher> matchers = new ArrayList<TargetMatcher>();
		private Hook replacementMethod;
		private Hook methodToReplace;
		private Set<StackRequest> extraRequests = new HashSet<StackRequest>();
		private boolean transplant, insert;
		
		public ReplaceMethodCallScript build() {
			if (matchers.isEmpty()) throw new IllegalStateException("You have to set a target method matcher");
			if (replacementMethod == null) throw new IllegalStateException("You have to set a replacement method");
			if (methodToReplace == null) throw new IllegalStateException("You have to set a method call to replace");
			
			return new ReplaceMethodCallScript(matchers, methodToReplace, replacementMethod, transplant, insert, extraRequests);
		}
		
		public ReplaceMethodCallBuilder target(TargetMatcher matcher) {
			this.matchers.add(matcher);
			return this;
		}
		
		public ReplaceMethodCallBuilder replacementMethod(Hook hook) {
			this.replacementMethod = hook;
			return this;
		}
		
		public ReplaceMethodCallBuilder methodToReplace(Hook hook) {
			this.methodToReplace = hook;
			return this;
		}
		
		public ReplaceMethodCallBuilder transplant() {
			this.transplant = true;
			this.insert = false;
			return this;
		}
		
		public ReplaceMethodCallBuilder insert() {
			this.transplant = false;
			this.insert = true;
			return this;
		}
		
		public ReplaceMethodCallBuilder requestExtra(StackRequest... requests) {
			for (StackRequest r : requests) {
				if (r == StackRequest.RETURN_VALUE) throw new IllegalArgumentException(
						"You cannot ask for the tentative return value in ReplaceMethodCallScript.");
				this.extraRequests.add(r);
			}
			
			return this;
		}
	}
	
	public static class WrapMethodCallBuilder {
		private List<TargetMatcher> matchers = new ArrayList<TargetMatcher>();
		private Hook wrapMethod;
		private Hook methodToWrap;
		private Set<StackRequest> extraRequests = new HashSet<StackRequest>();
		private boolean transplant, insert;
		
		public WrapMethodCallScript build() {
			if (matchers.isEmpty()) throw new IllegalStateException("You have to set a target method matcher");
			if (wrapMethod == null) throw new IllegalStateException("You have to set method to wrap with");
			if (methodToWrap == null) throw new IllegalStateException("You have to set a method call to wrap");
			
			return new WrapMethodCallScript(matchers, methodToWrap, wrapMethod, transplant, insert, extraRequests);
		}
		
		public WrapMethodCallBuilder target(TargetMatcher matcher) {
			this.matchers.add(matcher);
			return this;
		}
		
		public WrapMethodCallBuilder wrapMethod(Hook hook) {
			this.wrapMethod = hook;
			return this;
		}
		
		public WrapMethodCallBuilder methodToWrap(Hook hook) {
			this.methodToWrap = hook;
			return this;
		}
		
		public WrapMethodCallBuilder transplant() {
			this.transplant = true;
			this.insert = false;
			return this;
		}
		
		public WrapMethodCallBuilder insert() {
			this.transplant = false;
			this.insert = true;
			return this;
		}
		
		public WrapMethodCallBuilder requestExtra(StackRequest... requests) {
			for (StackRequest r : requests) {
				if (r == StackRequest.RETURN_VALUE) throw new IllegalArgumentException(
						"You cannot ask for the tentative return value in WrapMethodCallBuilder.");
				this.extraRequests.add(r);
			}
			
			return this;
		}
	}
	
	public static class WrapReturnValueBuilder {
		private List<TargetMatcher> matchers = new ArrayList<TargetMatcher>();
		private Hook wrapMethod;
		private Set<StackRequest> requests = new HashSet<StackRequest>();
		private boolean transplant, insert;
		
		public WrapReturnValuesScript build() {
			if (matchers.isEmpty()) throw new IllegalStateException("You have to set a target method matcher");
			if (wrapMethod == null) throw new IllegalStateException("You have to set a method you'd like to wrap the return values with");
			
			return new WrapReturnValuesScript(matchers, wrapMethod, transplant, insert, requests);
		}
		
		public WrapReturnValueBuilder target(TargetMatcher matcher) {
			this.matchers.add(matcher);
			return this;
		}
		
		public WrapReturnValueBuilder wrapMethod(Hook hook) {
			this.wrapMethod = hook;
			return this;
		}
		
		public WrapReturnValueBuilder transplant() {
			this.transplant = true;
			this.insert = false;
			return this;
		}
		
		public WrapReturnValueBuilder insert() {
			this.transplant = false;
			this.insert = true;
			return this;
		}
		
		public WrapReturnValueBuilder request(@SuppressWarnings("hiding") StackRequest... requests) {
			for (StackRequest r : requests) this.requests.add(r);
			return this;
		}
	}
	
	public static class SetSymbolDuringMethodCallBuilder {
		private List<TargetMatcher> matchers = new ArrayList<TargetMatcher>();
		private Hook callToWrap;
		private String symbol;
		
		public SetSymbolDuringMethodCallScript build() {
			if (matchers.isEmpty()) throw new IllegalStateException("You have to set a target method matcher");
			if (callToWrap == null) throw new IllegalStateException("You have to set a method that needs to set the symbol during its invocation");
			if (symbol == null) throw new IllegalStateException("You have to specify the symbol that is on the stack during callToWrap's invocation");
			
			return new SetSymbolDuringMethodCallScript(matchers, callToWrap, symbol);
		}
		
		public SetSymbolDuringMethodCallBuilder target(TargetMatcher matcher) {
			this.matchers.add(matcher);
			return this;
		}
		
		public SetSymbolDuringMethodCallBuilder callToWrap(@SuppressWarnings("hiding") Hook callToWrap) {
			this.callToWrap = callToWrap;
			return this;
		}
		
		public SetSymbolDuringMethodCallBuilder symbol(@SuppressWarnings("hiding") String symbol) {
			this.symbol = symbol;
			return this;
		}
	}
	
	/**
	 * Adds a field to any class.
	 */
	public static AddFieldBuilder addField() {
		return new AddFieldBuilder();
	}
	
	/**
	 * Allows you patch any method so that you get called first, and you can choose to take over entirely if you want.
	 */
	public static ExitEarlyBuilder exitEarly() {
		return new ExitEarlyBuilder();
	}
	
	/**
	 * Allows you to replace all calls to a given method in a given method with calls to a method of your choosing.
	 */
	public static ReplaceMethodCallBuilder replaceMethodCall() {
		return new ReplaceMethodCallBuilder();
	}
	
	/**
	 * Allows you to inspect and optionally replace the result of calls to a given method in a given method.
	 */
	public static WrapMethodCallBuilder wrapMethodCall() {
		return new WrapMethodCallBuilder();
	}
	
	/**
	 * Allows you to inspect every value right before it is returned, and, optionally, replace it with something else.
	 */
	public static WrapReturnValueBuilder wrapReturnValue() {
		return new WrapReturnValueBuilder();
	}
	
	/**
	 * Allows you to push a symbol for the duration of all calls to method A in method B.
	 */
	public static SetSymbolDuringMethodCallBuilder setSymbolDuringMethodCall() {
		return new SetSymbolDuringMethodCallBuilder();
	}
}
