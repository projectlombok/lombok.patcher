/*
 * Copyright (C) 2009-2019 The Project Lombok Authors.
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

/**
 * Defines scripts to modify bytecode directly.
 * 
 * Some definitions:
 * 
 * <h2>transplant</h2>
 * 
 * Transplant means that the code you are trying to inject into another class (generally called the {@code hook}) is copied
 * into the target class. Specifically, you identify a method (the hook) that contains the code that must run as part of the
 * method targeted for manipulation (the target). Transplantation means that the entire hook method, signature and all, is copied
 * straight into the target class. You should definitely make the hook static unless you really know what you are doing.<br />
 * <ul>
 * <li>Advantage: Your hook is loaded by the same classloader as the target, which helps a lot with classloader issues</li>
 * <li>Advantage: Whilst you'd have to write it reflectively otherwise your hook can't be compiled, you still get free, unfettered access even to private members of the target</li>
 * <li>Disadvantage: Because your hook method is moved, you <em>cannot</em> reference <em>anything</em> else from the surroundings of where your hook method lives. Don't make helper methods!
 * </ul>
 * 
 * <h2>insert</h2>
 * 
 * Insert is like transplant but even more aggressive: The actual bytecode is dropped straight into the relevant place inside the target
 * method. insertion only works for extremely simple methods, because no effort is made to ensure that the target location's local space
 * and/or framesizes are sufficient, and in any case any usage of local slots by your injected code would simply overwrite. Generally,
 * for one-liners, especially if the one-liner is to replace something with a constant or wrap it with a single method call, you can use it.
 * 
 * <h2>cast</h2>
 * 
 * This lets you have the method that contains the code you wish to inject simply return {@code java.lang.Object}; this value will be
 * casted to the required type in the targeted method.
 */
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
		private List<String> targetClasses = new ArrayList<String>();
		private String fieldName;
		private String fieldType;
		private Object value;
		
		private static final int NO_ACCESS_LEVELS = ~(Opcodes.ACC_PUBLIC | Opcodes.ACC_PRIVATE | Opcodes.ACC_PRIVATE);
		
		public AddFieldScript build() {
			if (targetClasses.isEmpty()) throw new IllegalStateException("You have to set at least one targetClass.");
			if (fieldName == null) throw new IllegalStateException("You have to set a fieldName");
			if (fieldType == null) throw new IllegalStateException("You have to set the new field's type by calling fieldType");
			if (value != null) {
				setStatic();
				setFinal();
			}
			return new AddFieldScript(targetClasses, accessFlags, fieldName, fieldType, value);
		}
		
		/**
		 * @param targetClass The class to add the field to, separated with dots (e.g. java.lang.String).
		 */
		public AddFieldBuilder targetClass(String targetClass) {
			checkTypeSyntaxDot(targetClass);
			this.targetClasses.add(targetClass);
			return this;
		}
		
		/**
		 * @param value The value the field should be initialized to. Has to be a constant value of the appropriate type. Optional;
		 * if skipped, you get the default 0/false/null.
		 */
		public AddFieldBuilder value(Object value) {
			this.value = value;
			return this;
		}
		
		/**
		 * @param fieldName the name of the field to create.
		 */
		public AddFieldBuilder fieldName(String fieldName) {
			this.fieldName = fieldName;
			return this;
		}
		
		/**
		 * @param fieldType the type of the field, in JVM spec (e.g. [I for an int array).
		 */
		public AddFieldBuilder fieldType(String fieldType) {
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
			
			return new ExitFromMethodEarlyScript(matchers, decisionMethod, valueMethod, transplant, insert, requests);
		}
		
		/**
		 * The method that you want to modify.
		 */
		public ExitEarlyBuilder target(TargetMatcher matcher) {
			this.matchers.add(matcher);
			return this;
		}
		
		/**
		 * The code to be used to decide whether or not a given call should return immediately. If omitted, it's as if you hook
		 * in the code: {@code return true;}. {@code hook} must be a static method with a {@code boolean} return type.
		 */
		public ExitEarlyBuilder decisionMethod(Hook hook) {
			this.decisionMethod = hook;
			return this;
		}
		
		/**
		 * The code to be used to decide the return value. Cannot be specified if {@code target} is a method that returns {@code void},
		 * otherwise, mandatory. {@code hook} must be a static method with the same return value as {@code target}.
		 */
		public ExitEarlyBuilder valueMethod(Hook hook) {
			this.valueMethod = hook;
			return this;
		}
		
		/**
		 * See {@link ScriptBuilder} javadoc for details.
		 * Applies to both the {@code valueMethod} and the {@code decisionMethod}.
		 */
		public ExitEarlyBuilder transplant() {
			this.transplant = true;
			this.insert = false;
			return this;
		}
		
		/**
		 * See {@link ScriptBuilder} javadoc for details.
		 * Applies to both the {@code valueMethod} and the {@code decisionMethod}.
		 */
		public ExitEarlyBuilder insert() {
			this.transplant = false;
			this.insert = true;
			return this;
		}
		
		/**
		 * Defines the parameter(s) of your decision and value methods.
		 */
		public ExitEarlyBuilder request(StackRequest... requests) {
			for (StackRequest r : requests) {
				if (r == StackRequest.RETURN_VALUE) throw new IllegalArgumentException(
					"You cannot ask for the tentative return value in ExitFromMethodEarlyScript");
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
		
		/**
		 * The method that you want to modify.
		 */
		public ReplaceMethodCallBuilder target(TargetMatcher matcher) {
			this.matchers.add(matcher);
			return this;
		}
		
		/**
		 * The method you want to modify is scanned for any calls to {@code methodToReplace}; such calls are then replaced to be
		 * calls to this method instead. The {@code hook} should be static; its first argument should be the type of receiver
		 * and then all parameters of the {@code methodToReplace}, and then any further arguments you ask for via {@code requestExtra}.
		 * <br><br><br>
		 * Example:<br>
		 * You target instance method {@code bar} in class {@code Foo}.<br>
		 * Your methodToReplace is: java.lang.String charAt(int).<br>
		 * You added {@code StackRequest.THIS} via requestExtra.<br>
		 * Your replacement method signature should be: {@code public static char replacementCharAt(String receiver, int param1, Foo caller)}.
		 */
		public ReplaceMethodCallBuilder replacementMethod(Hook hook) {
			this.replacementMethod = hook;
			return this;
		}
		
		/**
		 * The method you want to modify is scanned for any calls to this specific method; such calls are then replaced to be
		 * calls to {@code replacementMethod}.
		 */
		public ReplaceMethodCallBuilder methodToReplace(Hook hook) {
			this.methodToReplace = hook;
			return this;
		}
		
		/**
		 * See {@link ScriptBuilder} javadoc for details.
		 * Applies to {@code replacementMethod}.
		 */
		public ReplaceMethodCallBuilder transplant() {
			this.transplant = true;
			this.insert = false;
			return this;
		}
		
		/**
		 * See {@link ScriptBuilder} javadoc for details.
		 * Applies to {@code replacementMethod}.
		 */
		public ReplaceMethodCallBuilder insert() {
			this.transplant = false;
			this.insert = true;
			return this;
		}
		
		public ReplaceMethodCallBuilder requestExtra(StackRequest... requests) {
			for (StackRequest r : requests) {
				if (r == StackRequest.RETURN_VALUE) throw new IllegalArgumentException(
					"You cannot ask for the tentative return value in ReplaceMethodCallScript");
				this.extraRequests.add(r);
			}
			
			return this;
		}
	}
	
	/**
	 * This script scans a target method (The <em>target</em>) for calls to a certain method (The {@code methodToWrap}) and
	 * adds a call to a third method (The {@code wrapMethod}), which can inspect and even modify the value.
	 */
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
		
		/**
		 * The method that you want to modify.
		 */
		public WrapMethodCallBuilder target(TargetMatcher matcher) {
			this.matchers.add(matcher);
			return this;
		}
		
		/**
		 * The method you want to modify is scanned for any calls to {@code methodToWrap}; a call to {@code wrapMethod}
		 * is injected immediately after every call to {@code methodToWrap}.
		 * The {@code hook} should be static; the returntype of the method you are wrapping should be replicated both as
		 * the hook's return type and as the type of its first argument. The hook should then have any further arguments
		 * you ask for via {@code requestExtra}.
		 * <br><br><br>
		 * Example:<br>
		 * You target instance method {@code bar} in class {@code Foo}.<br>
		 * Your methodToWrap is: java.lang.String charAt(int).<br>
		 * You added {@code StackRequest.THIS} via requestExtra.<br>
		 * Your wrap method signature should be: {@code public static char wrapCharAt(char result, Foo caller)}.
		 */
		public WrapMethodCallBuilder wrapMethod(Hook hook) {
			this.wrapMethod = hook;
			return this;
		}
		
		/**
		 * The method you want to modify is scanned for any calls to this specific method; calls to {@code wrapMethod}
		 * are injected immediately following any calls to this method.
		 */
		public WrapMethodCallBuilder methodToWrap(Hook hook) {
			this.methodToWrap = hook;
			return this;
		}
		
		/**
		 * See {@link ScriptBuilder} javadoc for details.
		 * Applies to {@code wrapMethod}.
		 */
		public WrapMethodCallBuilder transplant() {
			this.transplant = true;
			this.insert = false;
			return this;
		}
		
		/**
		 * See {@link ScriptBuilder} javadoc for details.
		 * Applies to {@code wrapMethod}.
		 */
		public WrapMethodCallBuilder insert() {
			this.transplant = false;
			this.insert = true;
			return this;
		}
		
		public WrapMethodCallBuilder requestExtra(StackRequest... requests) {
			for (StackRequest r : requests) {
				if (r == StackRequest.RETURN_VALUE) throw new IllegalArgumentException(
					"You cannot ask for the tentative return value in WrapMethodCallBuilder");
				this.extraRequests.add(r);
			}
			
			return this;
		}
	}
	
	/**
	 * This script lets you inspect (and replace) the returned value for any target method.
	 */
	public static class WrapReturnValueBuilder {
		private List<TargetMatcher> matchers = new ArrayList<TargetMatcher>();
		private Hook wrapMethod;
		private Set<StackRequest> requests = new HashSet<StackRequest>();
		private boolean transplant, insert, cast;
		
		public WrapReturnValuesScript build() {
			if (matchers.isEmpty()) throw new IllegalStateException("You have to set a target method matcher");
			if (wrapMethod == null) throw new IllegalStateException("You have to set a method you'd like to wrap the return values with");
			
			return new WrapReturnValuesScript(matchers, wrapMethod, transplant, insert, cast, requests);
		}
		
		/**
		 * The method that you want to modify.
		 */
		public WrapReturnValueBuilder target(TargetMatcher matcher) {
			this.matchers.add(matcher);
			return this;
		}
		
		/**
		 * All attempts to return in the {@code target} will be preceded by a call to this {@code hook} immediately prior to the return.
		 */
		public WrapReturnValueBuilder wrapMethod(Hook hook) {
			this.wrapMethod = hook;
			return this;
		}
		
		/**
		 * See {@link ScriptBuilder} javadoc for details.
		 * Applies to {@code wrapMethod}.
		 */
		public WrapReturnValueBuilder transplant() {
			this.transplant = true;
			this.insert = false;
			return this;
		}
		
		/**
		 * See {@link ScriptBuilder} javadoc for details.
		 * Applies to {@code wrapMethod}.
		 */
		public WrapReturnValueBuilder insert() {
			if (this.cast) throw new IllegalArgumentException("cast and insert are mutually exlusive");
			this.transplant = false;
			this.insert = true;
			return this;
		}
		
		/**
		 * See {@link ScriptBuilder} javadoc for details.
		 * Applies to {@code wrapMethod}.
		 */
		public WrapReturnValueBuilder cast() {
			if (this.insert) throw new IllegalArgumentException("insert and cast are mutually exlusive");
			this.cast = true;
			return this;
		}
		
		public WrapReturnValueBuilder request(StackRequest... requests) {
			for (StackRequest r : requests) this.requests.add(r);
			return this;
		}
	}
	
	public static class SetSymbolDuringMethodCallBuilder {
		private List<TargetMatcher> matchers = new ArrayList<TargetMatcher>();
		private Hook callToWrap;
		private String symbol;
		private boolean report;
		
		public SetSymbolDuringMethodCallScript build() {
			if (matchers.isEmpty()) throw new IllegalStateException("You have to set a target method matcher");
			if (callToWrap == null) throw new IllegalStateException("You have to set a method that needs to set the symbol during its invocation");
			if (symbol == null) throw new IllegalStateException("You have to specify the symbol that is on the stack during callToWrap's invocation");
			
			return new SetSymbolDuringMethodCallScript(matchers, callToWrap, symbol, report);
		}
		
		public SetSymbolDuringMethodCallBuilder target(TargetMatcher matcher) {
			this.matchers.add(matcher);
			return this;
		}
		
		public SetSymbolDuringMethodCallBuilder callToWrap(Hook callToWrap) {
			this.callToWrap = callToWrap;
			return this;
		}
		
		public SetSymbolDuringMethodCallBuilder symbol(String symbol) {
			this.symbol = symbol;
			return this;
		}
		
		public SetSymbolDuringMethodCallBuilder report() {
			this.report = true;
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
	 * This script lets you modify a target method to return immediately upon being invoked; this can also be used to replace
	 * entirely what it does.
	 * Your can control:<br />
	 * (optional) Have a 'decider' method which decides whether or not a call to the target should return immediately.<br />
	 * (mandatory) The value to be returned. For void methods this is irrelevant, of course.
	 */
	public static ExitEarlyBuilder exitEarly() {
		return new ExitEarlyBuilder();
	}
	
	/**
	 * This script scans a target method (The <em>target</em>) for calls to a certain method (The {@code methodToReplace}) and
	 * replaces these calls with a different call; usually the replacement is your own creation.
	 */
	public static ReplaceMethodCallBuilder replaceMethodCall() {
		return new ReplaceMethodCallBuilder();
	}
	
	/**
	 * This script scans a target method (The <em>target</em>) for calls to a certain method (The {@code methodToWrap}) and
	 * adds a call to a third method (The {@code wrapMethod}), which can inspect and even modify the value.
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
