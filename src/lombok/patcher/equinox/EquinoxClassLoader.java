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
package lombok.patcher.equinox;

public class EquinoxClassLoader {
	//Find equinox exclusion rule for "java." and add "lombok.patcher.EquinoxFixer" to it.
	//Use WrapReturnValuesScript to wrap osgi classloaders into a custom classloader which will use the local jar
	//to load X.* with yet another child loader that will prefer loading from local jar before reverting to original
	//return value. If it doesn't start with X., then load it with the direct child.
	
	//STEP 1:
	//  Find each occurrence of 'startsWith("java.")' in:
	//    org.eclipse.osgi.internal.loader.BundleLoader
	//  and replace it with '(startsWith("java.") || equals("lombok.patcher.equinox.InjectingClassLoader"))'.
	
	//STEP2:
	//  Patch the ClassLoaders to:
	//     wrap the original loader (loaderA) with a child loader named loaderB.
	
	//     loaderB first checks if the class-to-be-loaded matches the registered pattern. If not, load it yourself*,
	//     otherwise, load it with loaderC.
	
	//     loaderC first checks if the class is available in the local jar, and if so loads it*, else, delegates to B.
	
	//Loading it yourself means asking your parent (B for C, A for B) for the raw bytes, then running defineClass yourself.
	
	//To patch, fix calls of the OSGi framework into:
	
	//org.eclipse.osgi.internal.composite.CompositeClassLoader (extends ClassLoader)
	//org.eclipse.osgi.internal.baseadaptor.DefaultClassLoader (extends ClassLoader)
	//   org.eclipse.core.runtime.adaptor.EclipseClassLoader (extends DefaultClassLoader)
	//org.eclipse.osgi.framework.adaptor.core.AbstractClassLoader (extends ClassLoader)
	//   org.eclipse.osgi.framework.adaptor.core.DefaultClassLoader (extends AbstractClassLoader)
	
	//which can be found at:
	
	//org.eclipse.osgi.internal.composite.CompositeConfigurator#createClassLoader(*) [CompositeClassLoader]
	//org.eclipse.osgi.baseadaptor.BaseData#createClassLoader(*) [DefaultClassLoader]
	//org.eclipse.osgi.framework.internal.defaultadaptor.DefaultElementFactory#createClassLoader(*) [DefaultClassLoader]
	//org.eclipse.core.runtime.adaptor.EclipseElementFactory#createClassLoader(*) [EclipseClassLoader]
}
