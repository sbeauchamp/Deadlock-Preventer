package com.freescale.deadlockpreventer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;

import com.freescale.deadlockpreventer.Analyzer.AcquisitionOrder;
import com.freescale.deadlockpreventer.Analyzer.CacheEntry;
import com.freescale.deadlockpreventer.Analyzer.LockInfo;

public class Statistics
{
	HashMap<String, LockImpl> locks = new HashMap<String, LockImpl>();
	
	public Statistics() {
		
		synchronized(Analyzer.instance().globalOrder) {
			for (ArrayList<CacheEntry<AcquisitionOrder>> list : Analyzer.instance().globalOrder.cache.values()) {
				for (CacheEntry<AcquisitionOrder> entry : list) {
					LockImpl lock = new LockImpl(entry.object);
					locks.put(lock.id, lock);
				}
			}
			for (ArrayList<CacheEntry<AcquisitionOrder>> list : Analyzer.instance().globalOrder.cache.values()) {
				for (CacheEntry<AcquisitionOrder> entry : list) {
					String id = Analyzer.safeToString(entry.object);
					LockImpl lock = locks.get(id);
					for (LockInfo info : entry.value.order) {
						String precedentId = Analyzer.safeToString(info.getLock());
						LockImpl precedent = locks.get(precedentId);
						if (precedent == null) {
							precedent = new LockImpl(info.getLock());
							locks.put(precedentId, precedent);
						}
						precedent.recordStackTrace(info.stackTrace);
						if (!lock.hasPrecedent(precedentId)) {
							ContextImpl precedentContext = new ContextImpl(precedent, info);
							lock.addPrecedent(precedentContext);
						}

						if (!precedent.hasFollower(id)) {
							ContextImpl followerContext = new ContextImpl(lock, info);
							precedent.addFollower(followerContext);
						}
					}
				}
			}
		}
	}

	public Statistics(String[] strings) {
		LinkedList<String> stack = new LinkedList<String>(Arrays.asList(strings));
		
		initialize(stack);
	}

	private void initialize(LinkedList<String> stack) {
		int count = Integer.parseInt(stack.removeFirst());
		
		while (count-- > 0) {
			LockImpl lock = new LockImpl(stack);
			locks.put(lock.getID(), lock);
		}
		for (LockImpl lock : locks.values()) {
			lock.completeInitialization(locks);
		}
	}

	public Statistics(ArrayList<String> strings) {
		LinkedList<String> stack = new LinkedList<String>(strings);
		
		initialize(stack);
	}

	public ArrayList<String> serialize() {
		ArrayList<String> result = new ArrayList<String>();
		
		Collection<LockImpl> collection = locks.values();
		// 1. serialize the lock count
		result.add(Integer.toString(collection.size()));
		// 2. serialize each lock 
		for (LockImpl lock : collection) {
			lock.serialize(result);
		}
		return result;
	}

	public ILock[] locks() {
		return locks.values().toArray(new ILock[0]);
	}

	static String[] convert(StackTraceElement[] st) {
		ArrayList<String> result = new ArrayList<String>();
		// always skip the first (in java.lang.Thread.getStackTrace), then the one in the analyzer
		for (int i = 1; i < st.length; i++) {
			String tmp = st[i].toString();
			if (tmp.startsWith(Analyzer.class.getName()) && result.size() == 0)
				continue;
			result.add(tmp);
		}
		return result.toArray(new String[0]);
	}
}

class LockImpl implements ILock
{
	String id;
	String[] stackTrace = new String[0];
	HashMap<String, ContextImpl> precedents = new HashMap<String, ContextImpl>();
	HashMap<String, ContextImpl> followers = new HashMap<String, ContextImpl>();
	
	public LockImpl(Object object) {
		id = Analyzer.safeToString(object);
	}
	
	public String toString() {
		return id;
	}

	public void recordStackTrace(StackTraceElement[] stackTrace2) {
		if (stackTrace.length == 0)
			stackTrace = Statistics.convert(stackTrace2);
	}

	public void completeInitialization(HashMap<String, LockImpl> locks) {
		for (ContextImpl context : precedents.values()) {
			context.completeInitialization(locks);
		}
		for (ContextImpl context : followers.values()) {
			context.completeInitialization(locks);
		}
	}

	public LockImpl(LinkedList<String> stack) {
		// 1. un-serialize the ID
		id = stack.removeFirst();
		// 2. un-serialize the precedent count
		int count = Integer.parseInt(stack.removeFirst());
		// 3. un serialize each precedent
		while (count-- > 0) {
			ContextImpl context = new ContextImpl(stack);
			precedents.put(context.getLock().getID(), context);
		}
		// 4. serialize the followers count
		count = Integer.parseInt(stack.removeFirst());
		// 5. serialize each follower
		while (count-- > 0) {
			ContextImpl context = new ContextImpl(stack);
			followers.put(context.getLock().getID(), context);
		}
		// 6. un-serialize the stack trace count
		count = Integer.parseInt(stack.removeFirst());
		// 7. un-serialize each stack trace
		stackTrace = new String[count];
		for (int i = 0; i < count; i++)
			stackTrace[i] = stack.removeFirst();
	}

	public void serialize(ArrayList<String> result) {
		// 1. serialize the ID
		result.add(id);
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
		for (String stack : stackTrace)
			result.add(stack);
	}

	public boolean hasPrecedent(String lockID) {
		return precedents.containsKey(lockID);
	}
	
	public boolean hasFollower(String lockID) {
		return followers.containsKey(lockID);
	}

	public void addPrecedent(ContextImpl context) {
		precedents.put(context.getLock().getID(), context);
	}

	public void addFollower(ContextImpl context) {
		followers.put(context.getLock().getID(), context);
	}

	@Override
	public String getID() {
		return id;
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
		return id.hashCode();
	}

	@Override
	public boolean equals(Object obj) {
		if (obj instanceof LockImpl)
			return id.equals(((LockImpl) obj).id);
		return false;
	}

	@Override
	public String[] getStackTrace() {
		return stackTrace;
	}
	
}

class ContextImpl implements IContext
{
	LockImpl lock;
	String pendingLockID;
	String threadId;
	String[] stackTrace;
	
	public ContextImpl(LockImpl lock, LockInfo info) {
		this.lock = lock;
		threadId = Long.toHexString(info.threadId);
		stackTrace = Statistics.convert(info.stackTrace);
	}

	public void completeInitialization(HashMap<String, LockImpl> locks) {
		lock = locks.get(pendingLockID);
	}

	public ContextImpl(LinkedList<String> stack) {
		// 1. un- serialize the lock id
		pendingLockID = stack.removeFirst();
		// 2. un-serialize the thread id
		threadId = stack.removeFirst();
		// 3. un-serialize the stack trace count
		int count = Integer.parseInt(stack.removeFirst());
		// 4. un-serialize each stack trace
		stackTrace = new String[count];
		for (int i = 0; i < count; i++)
			stackTrace[i] = stack.removeFirst();
	}

	public void serialize(ArrayList<String> result) {
		// 1. serialize the lock id
		result.add(lock.getID());
		// 2. serialize the thread id
		result.add(threadId);
		// 3. serialize the stack trace count
		result.add(Integer.toString(stackTrace.length));
		// 3. serialize each stack trace
		for (String stack : stackTrace)
			result.add(stack);
	}

	@Override
	public String getThreadID() {
		return threadId;
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