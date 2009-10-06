package lombok.patcher;

import java.util.Collection;

public interface TargetMatcher {
	/**
	 * Return the classes you wish to reload, in binary naming (dots to separate packages and classname, $ for inner classes).
	 */
	public abstract Collection<String> getAffectedClasses();
	
	/**
	 * Returns true if the provided classSpec/methodName/methodDescriptor (as per the JVM Specification, and the way ASM
	 * provides them) fits this MethodTarget.
	 * 
	 * @param classSpec a Class Specification, JVM-style (e.g. {@code java/lang/String}).
	 * @param methodName The name of the method.
	 * @param descriptor A Method descriptor, ASM-style (e.g. {@code (II)V}.
	 */
	public abstract boolean matches(String classSpec, String methodName, String descriptor);
	
}