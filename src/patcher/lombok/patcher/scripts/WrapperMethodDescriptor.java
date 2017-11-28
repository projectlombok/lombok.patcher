/*
 * Copyright (C) 2017 The Project Lombok Authors.
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

public class WrapperMethodDescriptor {
	private final int count;
	private final int opcode;
	private final String owner;
	private final String name;
	private final String wrapperDescriptor;
	private final String targetDescriptor;
	private final boolean itf; // interface
	
	public WrapperMethodDescriptor(int count, int opcode, String owner, String name, String wrapperDescriptor, String targetDescriptor, boolean itf) {
		this.count = count;
		this.opcode = opcode;
		this.owner = owner;
		this.name = name;
		this.wrapperDescriptor = wrapperDescriptor;
		this.targetDescriptor = targetDescriptor;
		this.itf = itf;
	}
	
	public int getCount() {
		return count;
	}
	
	public int getOpcode() {
		return opcode;
	}
	
	public String getOwner() {
		return owner;
	}
	
	public String getName() {
		return name;
	}
	
	public String getWrapperDescriptor() {
		return wrapperDescriptor;
	}
	
	public String getTargetDescriptor() {
		return targetDescriptor;
	}
	
	public boolean isItf() {
		return itf;
	}
	
	public String getWrapperName() {
		return "$lombok$$wrapper$" + count + "$" + name;
	}
	
	@Override public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + count;
		result = prime * result + (itf ? 1231 : 1237);
		result = prime * result + ((name == null) ? 0 : name.hashCode());
		result = prime * result + opcode;
		result = prime * result + ((owner == null) ? 0 : owner.hashCode());
		result = prime * result + ((targetDescriptor == null) ? 0 : targetDescriptor.hashCode());
		result = prime * result + ((wrapperDescriptor == null) ? 0 : wrapperDescriptor.hashCode());
		return result;
	}
	
	@Override public boolean equals(Object obj) {
		if (this == obj) return true;
		if (obj == null) return false;
		if (getClass() != obj.getClass()) return false;
		WrapperMethodDescriptor other = (WrapperMethodDescriptor) obj;
		if (count != other.count) return false;
		if (itf != other.itf) return false;
		if (name == null) {
			if (other.name != null) return false;
		} else if (!name.equals(other.name)) return false;
		if (opcode != other.opcode) return false;
		if (owner == null) {
			if (other.owner != null) return false;
		} else if (!owner.equals(other.owner)) return false;
		if (targetDescriptor == null) {
			if (other.targetDescriptor != null) return false;
		} else if (!targetDescriptor.equals(other.targetDescriptor)) return false;
		if (wrapperDescriptor == null) {
			if (other.wrapperDescriptor != null) return false;
		} else if (!wrapperDescriptor.equals(other.wrapperDescriptor)) return false;
		return true;
	}
	
	@Override public String toString() {
		return "WrapperMethodDescriptor[count=" + count + ", opcode=" + opcode + ", owner=" + owner + ", name=" + name + ", wrapperDescriptor=" + wrapperDescriptor + ", targetDescriptor=" + targetDescriptor + ", itf=" + itf + "]";
	}
}
