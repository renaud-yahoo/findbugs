/*
 * FindBugs - Find bugs in Java programs
 * Copyright (C) 2003, University of Maryland
 * 
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 * 
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 * 
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */

package edu.umd.cs.findbugs;

import java.util.*;
import org.apache.bcel.Constants;
import org.apache.bcel.Repository;
import org.apache.bcel.classfile.*;
import org.apache.bcel.generic.*;
import edu.umd.cs.daveho.ba.*;
import edu.umd.cs.findbugs.*;

public abstract class ResourceTrackingDetector<Resource> implements Detector {
	private static final boolean DEBUG = Boolean.getBoolean("rtd.debug");

	protected BugReporter bugReporter;

	public ResourceTrackingDetector(BugReporter bugReporter) {
		this.bugReporter = bugReporter;
	}

	public abstract boolean prescreen(ClassContext classContext, Method method);
	public abstract ResourceTracker<Resource> getResourceTracker(ClassContext classContext, Method method)
		throws DataflowAnalysisException, CFGBuilderException;
	public abstract void inspectResult(JavaClass javaClass, MethodGen methodGen, CFG cfg,
		Dataflow<ResourceValueFrame, ResourceValueAnalysis<Resource>> dataflow, Resource resource);

	public void visitClassContext(ClassContext classContext) {

		try {
			final JavaClass jclass = classContext.getJavaClass();
			Method[] methodList = jclass.getMethods();
			for (int i = 0; i < methodList.length; ++i) {
				Method method = methodList[i];
				if (method.isAbstract() || method.isNative())
					continue;

				MethodGen methodGen = classContext.getMethodGen(method);
				if (methodGen == null)
					continue;

				if (!prescreen(classContext, method))
					continue;

				ResourceTracker<Resource> resourceTracker = getResourceTracker(classContext, method);
				analyzeMethod(classContext, method, resourceTracker);
			}
		} catch (CFGBuilderException e) {
			throw new AnalysisException(e.toString(), e);
		} catch (DataflowAnalysisException e) {
			throw new AnalysisException(e.toString(), e);
		}

	}

	public void analyzeMethod(final ClassContext classContext, Method method, final ResourceTracker<Resource> resourceTracker)
		throws CFGBuilderException, DataflowAnalysisException {

		final MethodGen methodGen = classContext.getMethodGen(method);
		final CFG cfg = classContext.getCFG(method);
		final DepthFirstSearch dfs = classContext.getDepthFirstSearch(method);

		if (DEBUG) System.out.println(SignatureConverter.convertMethodSignature(methodGen));

		new LocationScanner(cfg).scan(new LocationScanner.Callback() {
			public void visitLocation(Location location) {
				BasicBlock basicBlock = location.getBasicBlock();
				InstructionHandle handle = location.getHandle();

				try {
					Resource resource = resourceTracker.isResourceCreation(basicBlock, handle, methodGen.getConstantPool());
					if (resource != null) {
						if (DEBUG) System.out.println("Resource creation at " + handle.getPosition());
						ResourceValueAnalysis<Resource> analysis =
							new ResourceValueAnalysis<Resource>(methodGen, cfg, dfs, resourceTracker, resource, bugReporter);
						Dataflow<ResourceValueFrame, ResourceValueAnalysis<Resource>> dataflow =
							new Dataflow<ResourceValueFrame, ResourceValueAnalysis<Resource>>(cfg, analysis);
	
						dataflow.execute();
						inspectResult(classContext.getJavaClass(), methodGen, cfg, dataflow, resource);
					}
				} catch (DataflowAnalysisException e) {
					throw new AnalysisException("FindOpenResource caught exception: " + e.toString(), e);
				}
			}
		});
	}

	public void report() {
	}

}

// vim:ts=4
