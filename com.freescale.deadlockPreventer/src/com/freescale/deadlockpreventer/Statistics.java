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
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.LinkedList;
import java.util.Map.Entry;

import com.freescale.deadlockpreventer.Analyzer.AcquisitionOrder;

public class Statistics
{
	HashMap<String, LockImpl> locks = new HashMap<String, LockImpl>();
	StringTable stringTable = new StringTable();
	
	public Statistics() {
		synchronized(Analyzer.instance().globalOrder) {
			for (ArrayList<ObjectCache.Entry<AcquisitionOrder>> list : Analyzer.instance().globalOrder.cache.values()) {
				for (ObjectCache.Entry<AcquisitionOrder> entry : list) {
					IncompleteLockImpl lock = new IncompleteLockImpl(entry.object, stringTable);
					lock.order = entry.value;
					locks.put(lock.getID(), lock);
				}
			}
			for (ArrayList<ObjectCache.Entry<AcquisitionOrder>> list : Analyzer.instance().globalOrder.cache.values()) {
				for (ObjectCache.Entry<AcquisitionOrder> entry : list) {
					String id = Util.safeToString(entry.object);
					LockImpl lock = locks.get(id);
					for (LockInfo info : entry.value.order) {
						String precedentId = getLockID(info);
						LockImpl precedent = locks.get(precedentId);
						if (precedent == null) {
							precedent = new LockImpl(info.getLock(), stringTable);
							locks.put(precedentId, precedent);
						}
						precedent.recordStackTrace(info.stackTrace);
						if (!lock.hasPrecedent(precedentId)) {
							ContextImpl precedentContext = new ContextImpl(precedent, info, stringTable);
							lock.addPrecedent(precedentContext);
						}

						if (!precedent.hasFollower(id)) {
							ContextImpl followerContext;
							if (info.contextsPerThread.isEmpty())
								followerContext = new ContextImpl(lock, info, stringTable);
							else {
								Entry<String, StackTraceElement[]> context = info.contextsPerThread.entrySet().iterator().next();
								followerContext = new ContextImpl(lock, context.getKey(), context.getValue(), stringTable);
							}
							precedent.addFollower(followerContext);
						}
					}
				}
			}
		}
	}
	
	private String getLockID(LockInfo info) {
		if (info.lockID == null)
			info.lockID = Util.safeToString(info.getLock());
		return info.lockID;
	}

	public Statistics(String[] strings) {
		LinkedList<String> stack = new LinkedList<String>(Arrays.asList(strings));
		
		initialize(stack);
	}

	private void initialize(LinkedList<String> stack) {

		stringTable = new StringTable(stack);

		int count = Integer.parseInt(stack.removeFirst());
		
		while (count-- > 0) {
			LockImpl lock = new LockImpl(stack, stringTable);
			locks.put(lock.getID(), lock);
		}
		for (LockImpl lock : locks.values()) {
			lock.completeInitialization(locks);
		}
	}

	public Statistics(LinkedList<String> stack) {
		initialize(stack);
	}

	public ArrayList<String> serialize() {
		ArrayList<String> result = new ArrayList<String>();
		
		// 1. serialize the string table
		stringTable.serialize(result);
		
		Collection<LockImpl> collection = locks.values();
		// 2. serialize the lock count
		result.add(Integer.toString(collection.size()));
		// 3. serialize each lock 
		for (LockImpl lock : collection) {
			lock.serialize(result);
		}
		return result;
	}

	static public ArrayList<String> serializeLocks(ILock[] locks) {
		ArrayList<String> result = new ArrayList<String>();
		// 2. serialize the lock count
		result.add(Integer.toString(locks.length));
		// 3. serialize each lock 
		for (ILock lock : locks) {
			lock.serialize(result);
		}
		return result;
	}

	public ILock[] unSerializeLocks(LinkedList<String> stack) {
		ArrayList<ILock> result = new ArrayList<ILock>();
		
		int count = Integer.parseInt(stack.removeFirst());
		
		while (count-- > 0) {
			LockImpl lock = new LockImpl(stack, stringTable);
			result.add(lock);
		}
		for (LockImpl lock : locks.values()) {
			lock.completeInitialization(locks);
		}
		return result.toArray(new ILock[0]);
	}

	public ILock[] locks() {
		return locks.values().toArray(new ILock[0]);
	}

	public ILock lock() {
		if (locks.values().isEmpty())
			return null;
		return locks.values().iterator().next();
	}

	static int[] convert(StackTraceElement[] st, StringTable table) {
		// always skip the first (in java.lang.Thread.getStackTrace), then the one in the analyzer
		int i = 1;
		for (; i < st.length; i++) {
			String tmp = st[i].toString();
			if (!tmp.startsWith(Analyzer.class.getName()))
				break;
		}
		int offset = i;
		int[] result = new int[st.length - i];
		for (; i < st.length; i++) {
			result[i - offset] = table.get(st[i].toString());
		}
		return result;
	}

	static String[] convert(int[] stackTrace, StringTable table) {
		String[] result = new String[stackTrace.length];
		for (int i = 0; i < stackTrace.length; i++)
			result[i] = table.get(stackTrace[i]);
		return result;
	}

	public ArrayList<String> serializeStringTable() {
		ArrayList<String> result = new ArrayList<String>();
		stringTable.serialize(result);
		return result;
	}
}

class StringTable {
	Hashtable<String, Integer> map = new Hashtable<String, Integer>();
	ArrayList<String> table = new ArrayList<String>();
	
	public StringTable(LinkedList<String> stack) {
		int count = Integer.parseInt(stack.removeFirst());
		
		int total = 0;
		while (count-- > 0) {
			String tmp = stack.removeFirst();
			Integer index = new Integer(total++);
			map.put(tmp, index);
			table.add(tmp);
		}
	}

	public StringTable() {
	}

	public void serialize(ArrayList<String> result) {
		// 1. serialize the lock count
		result.add(Integer.toString(table.size()));
		for (String s: table)
			result.add(s);
	}

	public int get(String string) {
		Integer index = map.get(string);
		if (index == null) {
			index = new Integer(map.size());
			map.put(string, index);
			table.add(string);
		}
		return index.intValue();
	}
	
	public String get(int index) {
		try {
			return table.get(index);
		} catch (Exception e) {
			return null;
		}
	}
}

class IncompleteLockImpl extends LockImpl
{
	public IncompleteLockImpl(Object object, StringTable stringTable) {
		super(object, stringTable);
		key = ObjectCache.getKey(object);
		this.object = object;
	}
	
	Integer key;  
	Object object;
	AcquisitionOrder order;
}

class LockImpl implements ILock
{
	StringTable st;
	int id;
	int[] stackTrace = new int[0];
	HashMap<Integer, ContextImpl> precedents = new HashMap<Integer, ContextImpl>();
	HashMap<Integer, ContextImpl> followers = new HashMap<Integer, ContextImpl>();
	
	public LockImpl(Object object, StringTable stringTable) {
		this.st = stringTable;
		id = st.get(Util.safeToString(object));
	}

	public LockImpl(StringTable stringTable) {
		id = -1;
		this.st = stringTable;
	}
	
	public String toString() {
		return st.get(id);
	}

	public void recordStackTrace(StackTraceElement[] stackTrace2) {
		if (stackTrace.length == 0)
			stackTrace = Statistics.convert(stackTrace2, st);
	}

	public void completeInitialization(HashMap<String, LockImpl> locks) {
		for (ContextImpl context : precedents.values()) {
			context.completeInitialization(locks);
		}
		for (ContextImpl context : followers.values()) {
			context.completeInitialization(locks);
		}
	}

	public LockImpl(LinkedList<String> stack, StringTable stringTable) {
		this.st = stringTable;
		// 1. un-serialize the ID
		id = Integer.parseInt(stack.removeFirst());
		// 2. un-serialize the precedent count
		int count = Integer.parseInt(stack.removeFirst());
		// 3. un serialize each precedent
		while (count-- > 0) {
			ContextImpl context = new ContextImpl(stack, stringTable);
			precedents.put(context.pendingLockID, context);
		}
		// 4. serialize the followers count
		count = Integer.parseInt(stack.removeFirst());
		// 5. serialize each follower
		while (count-- > 0) {
			ContextImpl context = new ContextImpl(stack, stringTable);
			followers.put(context.pendingLockID, context);
		}
		// 6. un-serialize the stack trace count
		count = Integer.parseInt(stack.removeFirst());
		// 7. un-serialize each stack trace
		stackTrace = new int[count];
		for (int i = 0; i < count; i++)
			stackTrace[i] = Integer.parseInt(stack.removeFirst());
	}

	public void serialize(ArrayList<String> result) {
		// 1. serialize the ID
		result.add(Integer.toString(id));
		// 2. serialize the precedent count
		result.add(Integer.toString(precedents.size()));
		// 3. serialize each precedent
		for (ContextImpl context : precedents.values()) {
			context.serialize(result);
		}
		// 4. serialize the followers count
		result.add(Integer.toString(followers.size()));
		// 5. serialize each follower
		for (ContextImpl context : followers.values()) {
			context.serialize(result);
		}
		// 6. serialize the stack trace count
		result.add(Integer.toString(stackTrace.length));
		// 7. serialize each stack trace
		for (int stack : stackTrace)
			result.add(Integer.toString(stack));
	}

	public boolean hasPrecedent(String lockID) {
		return precedents.containsKey(lockID);
	}
	
	public boolean hasFollower(String lockID) {
		return followers.containsKey(lockID);
	}

	public void addPrecedent(ContextImpl context) {
		precedents.put(st.get(context.getLock().getID()), context);
	}

	public void addFollower(ContextImpl context) {
		followers.put(st.get(context.getLock().getID()), context);
	}

	@Override
	public String getID() {
		return st.get(id);
	}

	@Override
	public IContext[] getPrecedents() {
		return precedents.values().toArray(new IContext[0]);
	}

	@Override
	public IContext[] getFollowers() {
		return followers.values().toArray(new IContext[0]);
	}

	@Override
	public int hashCode() {
		return st.get(id).hashCode();
	}

	@Override
	public boolean equals(Object obj) {
		if (obj instanceof LockImpl)
			return id == ((LockImpl) obj).id;
		return false;
	}

	@Override
	public String[] getStackTrace() {
		return Statistics.convert(stackTrace, st);
	}

	@Override
	public ArrayList<String> serialize() {
		ArrayList<String> result = new ArrayList<String>();
		serialize(result);
		return result;
	}
	
}

class ContextImpl implements IContext
{
	StringTable st;
	LockImpl lock;
	int pendingLockID;
	int threadId;
	int[] stackTrace;
	
	public ContextImpl(LockImpl lock, LockInfo info, StringTable stringTable) {
		this.st = stringTable;
		this.lock = lock;
		threadId = st.get(info.threadId);
		stackTrace = Statistics.convert(info.stackTrace, st);
	}

	public ContextImpl(LockImpl lock, String threadId, StackTraceElement[] stackTrace, StringTable stringTable) {
		this.st = stringTable;
		this.lock = lock;
		this.threadId = st.get(threadId);
		this.stackTrace = Statistics.convert(stackTrace, st);
	}

	public void completeInitialization(HashMap<String, LockImpl> locks) {
		lock = locks.get(pendingLockID);
	}

	public ContextImpl(LinkedList<String> stack, StringTable stringTable) {
		st = stringTable;
		// 1. un- serialize the lock id
		pendingLockID = Integer.parseInt(stack.removeFirst());
		// 2. un-serialize the thread id
		threadId = Integer.parseInt(stack.removeFirst());
		// 3. un-serialize the stack trace count
		int count = Integer.parseInt(stack.removeFirst());
		// 4. un-serialize each stack trace
		stackTrace = new int[count];
		for (int i = 0; i < count; i++)
			stackTrace[i] = Integer.parseInt(stack.removeFirst());
	}

	public void serialize(ArrayList<String> result) {
		// 1. serialize the lock id
		result.add(Integer.toString(st.get(lock.getID())));
		// 2. serialize the thread id
		result.add(Integer.toString(threadId));
		// 3. serialize the stack trace count
		result.add(Integer.toString(stackTrace.length));
		// 3. serialize each stack trace
		for (int stack : stackTrace)
			result.add(Integer.toString(stack));
	}

	@Override
	public String getThreadID() {
		return st.get(threadId);
	}

	@Override
	public String[] getStackTrace() {
		return Statistics.convert(stackTrace, st);
	}

	@Override
	public ILock getLock() {
		if (lock == null) {
			LockImpl tmp = new LockImpl(st);
			tmp.id = pendingLockID;
			return tmp;
		}
		return lock;
	}
}