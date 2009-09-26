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

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import lombok.Getter;

/**
 * Various patch scripts support various method parmater signatures in your hook methods; use StackRequest values to specify
 * which parameters you want. It doesn't matter how you specify your StackRequest values; you <em>MUST</em> order your parameters
 * in the order of the values in the enum (so, first the return value, then the this pointer, then any parameters you want).
 */
public enum StackRequest {
	RETURN_VALUE(-1), THIS(-1), PARAM1(0), PARAM2(1), PARAM3(2), PARAM4(3), PARAM5(4), PARAM6(5);
	
	@Getter
	private final int paramPos;

	StackRequest(int paramPos) {
		this.paramPos = paramPos;
	}
	
	public static final List<StackRequest> PARAMS_IN_ORDER = Collections.unmodifiableList(Arrays.asList(
			PARAM1, PARAM2, PARAM3, PARAM4, PARAM5, PARAM6));
}
