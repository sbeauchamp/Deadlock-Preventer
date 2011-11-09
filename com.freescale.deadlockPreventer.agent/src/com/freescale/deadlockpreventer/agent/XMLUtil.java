/*******************************************************************************
 * Copyright (c) 2011 Freescale Semiconductor.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     Serge Beauchamp (Freescale Semiconductor) - initial API and implementation
 *******************************************************************************/
package com.freescale.deadlockpreventer.agent;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;

import org.eclipse.ui.IMemento;
import org.eclipse.ui.WorkbenchException;
import org.eclipse.ui.XMLMemento;

import com.freescale.deadlockpreventer.IContext;
import com.freescale.deadlockpreventer.ILock;
import com.freescale.deadlockpreventer.QueryService;
import com.freescale.deadlockpreventer.QueryService.IBundleInfo;

public class XMLUtil {

	private static final String THREAD = "thread";
	private static final String CONTEXT = "context";
	private static final String FOLLOWERS = "followers";
	private static final String PRECEDENTS = "precedents";
	private static final String VALUE = "value";
	private static final String FRAME = "frame";
	private static final String STACK = "stack";
	private static final String ID = "id";
	private static final String LOCK = "lock";
	private static final String LOCKS = "locks";
	private static final String BUNDLE_INFOS = "bundle-infos";
	private static final String BUNDLE_INFO = "bundle-info";
	private static final String PACKAGES = "packages";
	private static final String PACKAGE = "package";
	private static final String NAME = "name";

	static public void write(IMemento root, ILock lock) {
		IMemento child = root.createChild(LOCK);
		child.putString(ID, lock.getID());
		writeStackTrace(child, lock.getStackTrace());
		writeContext(child, lock.getPrecedents(), PRECEDENTS);
		writeContext(child, lock.getFollowers(), FOLLOWERS);
	}

	private static void writeContext(IMemento child, IContext[] contextes, String label) {
		IMemento precedents = child.createChild(label);
		for (IContext context : contextes) {
			IMemento precedent = precedents.createChild(CONTEXT);
			precedent.putString(THREAD, context.getThreadID());
			precedent.putString(ID, context.getLock().getID());
			writeStackTrace(precedent, context.getStackTrace());
		}
	}

	private static void writeStackTrace(IMemento child, String[] stackTrace) {
		IMemento stack = child.createChild(STACK);
		for (int i = 0; i < stackTrace.length; i++) {
			IMemento frame = stack.createChild(FRAME);
			frame.putString(VALUE, stackTrace[i]);
		}
	}

	public static ILock[] readLocks(IMemento memento) throws WorkbenchException {
		// We use a stringTable to make sure that the strings in the data structures
		// are all part of a string pool.  Otherwise, it will consume very large amount of 
		// memory.
		HashMap<String, String> stringTable = new HashMap<String, String>();

		HashMap<String, ILock> result = new HashMap<String, ILock>();
		
		IMemento locks = memento.getChild(LOCKS);
		
		for (IMemento lock : locks.getChildren(LOCK)) {
			String id = getString(stringTable, lock.getString(ID));
			String[] stackTrace = getStackTrace(stringTable, lock);
			IContext[] precedents = getContext(stringTable, lock, PRECEDENTS);
			IContext[] followers = getContext(stringTable, lock, FOLLOWERS);
			
			ILock newLock = new Lock(id, stackTrace, precedents, followers);
			result.put(id, newLock);
		}
		resolvePlaceHolders(result);
		return result.values().toArray(new ILock[0]);
	}

	private static void resolvePlaceHolders(HashMap<String, ILock> map) {
		for (ILock lock : map.values()) {
			resolvePlaceHolders(map, lock.getPrecedents());
			resolvePlaceHolders(map, lock.getFollowers());
		}
	}

	private static void resolvePlaceHolders(HashMap<String, ILock> map,
			IContext[] contexes) {
		for (IContext context : contexes) {
			ILock lock = map.get(context.getLock().getID());
			if (lock != null)
				((Context) context).setLock(lock);
		}
	}

	private static IContext[] getContext(HashMap<String, String> stringTable,
			IMemento lock, String label) {
		ArrayList<IContext> contexes = new ArrayList<IContext>();
		IMemento contextList = lock.getChild(label);
		for (IMemento context : contextList.getChildren(CONTEXT)) {
			String lockId = getString(stringTable, context.getString(ID));
			String thread = getString(stringTable, context.getString(THREAD));
			String[] stackTrace = getStackTrace(stringTable, context);

			contexes.add(new Context(thread, new Lock(lockId), stackTrace));
		}
		return contexes.toArray(new IContext[0]);
	}

	private static String[] getStackTrace(HashMap<String, String> stringTable,
			IMemento lock) {
		ArrayList<String> stackTrace = new ArrayList<String>();
		IMemento stack = lock.getChild(STACK);
		for (IMemento frame : stack.getChildren(FRAME)) {
			stackTrace.add(getString(stringTable, frame.getString(VALUE)));
		}
		return stackTrace.toArray(new String[0]);
	}

	private static String getString(HashMap<String, String> stringTable,
			String string) {
		String result = stringTable.get(string);
		if (result == null) {
			stringTable.put(string, string);
			result = string;
		}
		return result;
	}

	public static void write(XMLMemento root, Collection<IBundleInfo> values) {
		IMemento child = root.createChild(BUNDLE_INFOS);
		for (IBundleInfo info : values) {
			IMemento infoElement = child.createChild(BUNDLE_INFO);
			infoElement.putString(NAME, info.getName());
			IMemento packagesElement = infoElement.createChild(PACKAGES);
			for (String packag : info.getPackages()) {
				IMemento pakageElement = packagesElement.createChild(PACKAGES);
				pakageElement.putString(VALUE, packag);
			}
		}
	}
	public static IBundleInfo[] readBundleInfos(IMemento memento) throws WorkbenchException {
		// We use a stringTable to make sure that the strings in the data structures
		// are all part of a string pool.  Otherwise, it will consume very large amount of 
		// memory.

		ArrayList<QueryService.IBundleInfo> result = new ArrayList<QueryService.IBundleInfo>();
		
		IMemento locks = memento.getChild(BUNDLE_INFOS);
		
		for (IMemento bundleInfo : locks.getChildren(BUNDLE_INFO)) {
			String name =  bundleInfo.getString(NAME);
			
			ArrayList<String> packages = new ArrayList<String>();
			
			IMemento packagesElement = memento.getChild(PACKAGES);
			for (IMemento pkgElement : packagesElement.getChildren(PACKAGE)) {
				String value = pkgElement.getString(VALUE);
				packages.add(value);
			}
			IBundleInfo newBundleInfo = new BundleInfo(name, packages.toArray(new String[0]));
			result.add(newBundleInfo);
		}
		return result.toArray(new IBundleInfo[0]);
	}
}

class BundleInfo implements IBundleInfo {

	String name;
	String[] packages;
	
	public BundleInfo(String name, String[] packages) {
		this.name = name;
		this.packages = packages;
	}

	@Override
	public String getName() {
		return name;
	}

	@Override
	public String[] getPackages() {
		return packages;
	}
	
}

class Lock implements ILock {

	String id;
	String[] stackTrace;
	IContext[] precedents;
	IContext[] followers;

	public Lock(String id) {
		this.id = id;
	}

	public Lock(String id, String[] stackTrace, IContext[] precedents,
			IContext[] followers) {
		this.id = id;
		this.stackTrace = stackTrace;
		this.precedents = precedents;
		this.followers = followers;
	}

	@Override
	public String getID() {
		return id;
	}

	@Override
	public String[] getStackTrace() {
		return stackTrace;
	}

	@Override
	public IContext[] getPrecedents() {
		return precedents;
	}

	@Override
	public IContext[] getFollowers() {
		return followers;
	}

	@Override
	public ArrayList<String> serialize() {
		throw new RuntimeException();
	}

	@Override
	public void serialize(ArrayList<String> result) {
		throw new RuntimeException();
	}
}

class Context implements IContext {

	String thread;
	ILock lock;
	String[] stackTrace;
	
	public Context(String thread, ILock lock, String[] stackTrace) {
		this.thread = thread;
		this.lock = lock;
		this.stackTrace = stackTrace;
	}
	
	public void setLock(ILock lock) {
		this.lock = lock;
	}
	
	@Override
	public String getThreadID() {
		return thread;
	}

	@Override
	public String[] getStackTrace() {
		return stackTrace;
	}

	@Override
	public ILock getLock() {
		return lock;
	}
	
}