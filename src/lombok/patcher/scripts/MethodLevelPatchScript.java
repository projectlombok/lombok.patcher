package lombok.patcher.scripts;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import lombok.patcher.PatchScript;
import lombok.patcher.TargetMatcher;

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;

public abstract class MethodLevelPatchScript extends PatchScript {
	private final Set<String> affectedClasses;
	private final Collection<TargetMatcher> matchers;
	
	public MethodLevelPatchScript(Collection<TargetMatcher> matchers) {
		this.matchers = matchers;
		Set<String> affectedClasses = new HashSet<String>();
		for (TargetMatcher t : matchers) affectedClasses.addAll(t.getAffectedClasses());
		this.affectedClasses = Collections.unmodifiableSet(affectedClasses);
	}
	
	@Override public Collection<String> getClassesToReload() {
		return affectedClasses;
	}
	
	@Override public byte[] patch(String className, byte[] byteCode) {
		if (!classMatches(className, affectedClasses)) return null;
		return runASM(byteCode, true);
	}
	
	@Override protected final ClassVisitor createClassVisitor(ClassWriter writer, final String classSpec) {
		MethodPatcher patcher = createPatcher(writer, classSpec);
		for (TargetMatcher matcher : matchers) patcher.addTargetMatcher(matcher);
		return patcher;
	}
	
	protected abstract MethodPatcher createPatcher(ClassWriter writer, String classSpec);
}
