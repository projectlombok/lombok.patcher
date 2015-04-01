package lombok.patcher.scripts;

import lombok.Value;

@Value
public class WrapperMethodDescriptor {
	int count;
	int opcode;
	String owner;
	String name;
	String wrapperDescriptor;
	String targetDescriptor;
	boolean itf;
	
	public String getWrapperName() {
		return "$lombok$$wrapper$" + count + "$" + name;
	}
}
