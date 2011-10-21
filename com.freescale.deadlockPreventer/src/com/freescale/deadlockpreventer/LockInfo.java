/*******************************************************************************
 * Copyright (c) 2010 Freescale Semiconductor.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     Serge Beauchamp (Freescale Semiconductor) - initial API and implementation
 *******************************************************************************/
package com.freescale.deadlockpreventer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map.Entry;

public class LockInfo {
	private Object lock;
	Integer lockKey;
	// This is not normally set, except from statistic gathering
	String lockID;
	
	public void setLock(Object lock) {
		this.lock = lock;
		lockKey = ObjectCache.getKey(lock);
		lockID = null;
	}
	
	public Object getLock() {
		return lock;
	}
	
	String threadId = null;
	int count = 0;
	StackTraceElement[] stackTrace;
	HashMap<String, StackTraceElement[]> contextsPerThread = new HashMap<String, StackTraceElement[]>();
	ArrayList<LockInfo> guards = null;
	ArrayList<LockInfo> phantomGuards = null;
	LockInfo acquiringContext = null;
	
	public void print(String header) {
		StringBuffer buffer = new StringBuffer();
		Logger.printStrackTrace(buffer, header, stackTrace, Logger.FIRST_STACK_TRACE_ELEMENT);
		System.out.print(buffer.toString());
	}
	
	public void print(StringBuffer buffer, String header) {
		Logger.printStrackTrace(buffer, header, stackTrace, Logger.FIRST_STACK_TRACE_ELEMENT);
	}
	
	public String toString() {
		return "LockInfo: " + Util.safeToString(lock);
	}
	
	public LockInfo copy() {
		LockInfo info = new LockInfo();
		info.lock = lock;
		info.threadId = threadId;
		info.lockKey = lockKey;
		info.stackTrace = stackTrace;
		return info;
	}
	public void registerContext(String threadID, StackTraceElement[] context, ArrayList<LockInfo> guardList) {
		if (!contextsPerThread.containsKey(threadID))
			contextsPerThread.put(threadID, context);
		mergeGuardList(guardList);
	}
	
	public boolean equals(Object o) {
		if (o instanceof LockInfo)
			return ((LockInfo)o).lock == lock;
		return false;
	}
	
	@SuppressWarnings("unchecked")
	private void mergeGuardList(ArrayList<LockInfo> guardList) {
		if (guards == null)
			guards = (ArrayList<LockInfo>) guardList.clone();
		else {
			ArrayList<LockInfo> tmp = null;
			for (LockInfo guard : guards) {
				if (!guardList.contains(guard)) {
					if (tmp == null)
						tmp = new ArrayList<LockInfo>();
					tmp.add(guard);
				}
			}
			if (tmp != null) {
				for (LockInfo toMove : tmp) {
					guards.remove(toMove);
					initPhantoms();
					phantomGuards.add(toMove);
				}
			}
			for (LockInfo guard : guardList) {
				if (!guards.contains(guard)) {
					initPhantoms();
					if (!phantomGuards.contains(guard))
						phantomGuards.add(guard);
				}
			}
		}
	}
	private void initPhantoms() {
		if (phantomGuards == null)
			phantomGuards = new ArrayList<LockInfo>();
	}
	public ArrayList<Entry<String, StackTraceElement[]>> getConflictingThreads(
			List<LockInfo> guards, String threadID) {
		ArrayList<Entry<String, StackTraceElement[]>> set = new ArrayList<Entry<String, StackTraceElement[]>>();
		Iterator<Entry<String, StackTraceElement[]>> iterator = contextsPerThread.entrySet().iterator();
		while (iterator.hasNext()) {
			Entry<String, StackTraceElement[]> entry = iterator.next();
			if (!entry.getKey().equals(threadID))
				set.add(entry);
		}
		return set;
	}
	public StackTraceElement[] getContext(String threadID) {
		return contextsPerThread.get(threadID);
	}
	public boolean containsCommonGuard(List<LockInfo> subList) {
		for (LockInfo lockInfo : subList) {
			if (guards.contains(lockInfo))
				return true;
		}
		return false;
	}

	public ArrayList<Entry<String, StackTraceElement[]>> findOtherContextThanThread(String threadID) {
		ArrayList<Entry<String, StackTraceElement[]>> set = new ArrayList<Entry<String, StackTraceElement[]>>();
		Iterator<Entry<String, StackTraceElement[]>> iterator = contextsPerThread.entrySet().iterator();
		while (iterator.hasNext()) {
			Entry<String, StackTraceElement[]> entry = iterator.next();
			if (!entry.getKey().equals(threadID))
				set.add(entry);
		}
		return set;
	}

	public void setAcquiringContext(LockInfo info) {
		acquiringContext = info;
	}

	public LockInfo getAquiringContext() {
		return acquiringContext;
	}
}
