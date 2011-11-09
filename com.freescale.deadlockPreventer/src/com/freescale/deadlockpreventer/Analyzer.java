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

public class Analyzer {
	public static final int LOCK_NORMAL = 0;
	public static final int UNLOCK_NORMAL = 1;
	public static final int LOCK_COUNTED = 2;
	public static final int UNLOCK_COUNTED = 3;

	public static final int TYPE_ERROR 		= 1;
	public static final int TYPE_WARNING 		= 2;
	public static final int TYPE_ERROR_SIGNAL	= 3;

	private boolean trace = false;
	private boolean isDebug = false;
	
	private boolean reportWarningInSameThreadConflicts = false;
	private boolean throwsOnError = false;
	private boolean throwsOnWarning = false;
	private boolean isActive = false;
	private boolean writeInstrumentedClasses = false;
	private static int s_internalErrorCount = 0;
	private HashMap<String, Boolean> instrumentationRestrictions = new HashMap<String, Boolean>();
	private Logger logger = new Logger();
	
	ThreadLocal<Integer> enablementCount = new ThreadLocal<Integer>() {
		protected Integer initialValue() {
			return new Integer(1);
		}
		
	};
	static private Analyzer s_instance;
	
	public void setListener(IConflictListener listener) {
		logger.setListener(listener);
	}
	
	public boolean shouldWriteInstrumentedClasses() {
		return writeInstrumentedClasses;
	}
	
	public boolean isActive() {
		return isActive;
	}
	
	public void addRestriction(Class<?> cls, boolean shouldInstrument) {
		instrumentationRestrictions.put(cls.getCanonicalName().replace('.', '/'), shouldInstrument);
	}
	
	public void setReportWarningsInSameThread(boolean value) {
		reportWarningInSameThreadConflicts = value;
	}
	
	public void setThrowsOnWarning(boolean value) {
		throwsOnWarning = value;
	}
	
	public void setThrowsOnError(boolean value) {
		throwsOnError = value;
	}

	public static Analyzer instance() {
		synchronized(Analyzer.class) {
			if (s_instance == null)
				s_instance = new Analyzer();
		}
		return s_instance;
	}
	
	private Analyzer() {
		String value = System.getProperty(Settings.DUMP_LOCK_INFO);
		if (value != null)
			setupDumpOnExit(value);

		reportWarningInSameThreadConflicts = Boolean.getBoolean(Settings.REPORT_WARNINGS);
		throwsOnError = Boolean.getBoolean(Settings.THROWS_ON_ERROR);
		throwsOnWarning = Boolean.getBoolean(Settings.THROWS_ON_WARNING);
		trace = Boolean.getBoolean(Settings.TRACE);
		writeInstrumentedClasses = Boolean.getBoolean(Settings.WRITE_INSTRUMENTED_CLASSES);
		
		// separated by semicolons (;)
		value = System.getProperty(Settings.INSTRUMENT_ONLY_LIST);
		if (value != null) {
			String classes[] = value.split(";");
			for (String cls : classes)
				instrumentationRestrictions.put(cls.replace('.', '/'), true);
		}
	}
	
	private void setupDumpOnExit(final String file) {
		System.setSecurityManager(new SecurityManager() {
			@Override
			public void checkExit(int status) {
				Logger.dumpLockInformation(file);
				super.checkExit(status);
			}
		});
	}

	void activate() {
		isActive = true;
	}

	void enable() {
		Integer count = enablementCount.get();
		enablementCount.set(count + 1);
	}

	void disable() {
		Integer count = enablementCount.get();
		enablementCount.set(count - 1);
	}
	
	boolean isEnabled() {
		return enablementCount.get() > 0;
	}

	// last item is the latest acquired
	static public class AcquisitionOrder {
		ArrayList<LockInfo> order = new ArrayList<LockInfo>();
		StackTraceElement[] defaultStackTrace = null;

		public LockInfo find(LockInfo precedent) {
			for (int i = 0; i  < order.size(); i++) {
				LockInfo lockInfo = order.get(i);
				if (lockInfo.getLock() == precedent.getLock())
					return lockInfo;
			}
			return null;
		}
	}
	
	ObjectCache<CustomLock> customLocks = new ObjectCache<CustomLock>();

	private CustomLock createCustomLock(Object lock, boolean scoped) {
		CustomLock customLock;
		synchronized(customLocks) {
			customLock = customLocks.get(lock);
			if (customLock == null) {
				customLock = new CustomLock(lock, scoped);
				customLocks.put(lock, customLock);
			}
		}
		return customLock;
	}

	static class ThreadLocalEx<T> {

		protected HashMap<Long, T> map = new HashMap<Long, T>();
		
		public T get() {
			synchronized (this) {
				return map.get(Thread.currentThread().getId());
			}
		}
		
		public void set(T t) {
			synchronized (this) {
				map.put(Thread.currentThread().getId(), t);
			}
		}
		public ArrayList<T> getAll() {
			synchronized (this) {
				ArrayList<T> list = new ArrayList<T>(map.values());
				return list;
			}
		}
	}
	
	ThreadLocalEx<AcquisitionOrder> threadLocal = new ThreadLocalEx<AcquisitionOrder>();

	// The global order records each lock and their precedent.
	// So for example, if a given thread acquires A->B->D->C, then the global order will contain:
	// 		A:  
	// 		B: A
	// 		D: A B
	// 		C: A B D
	// So it means that for any given lock, we can know which locks were previously acquired
	// It also records the guards for each precedent, so that we know to avoid reporting incorrect
	// lock order when there's a master lock previously acquired.  In the example above, the (guards) were:
	// 		A:  
	// 		B: A
	// 		D: A B(A)
	// 		C: A B(A) D(A B)
	// If there's a new lock acquisition, for example A->D->C, the guards will be updated so contain the intersection
	// of all previous guards, for instance:
	// 		A:  
	// 		B: A
	// 		D: A B(A)
	// 		C: A B(A) D(A)
	// Phantom guards will be kept to record stack traces, so that if we have the following lock order: B->C->D
	// (conflict with C->D, D-C),we can know not to report the conflict with the stack trace of "A->B->D->C" 
	// (since B was an active guard then), but instead with "A->D-C", since B wasn't an active guard.
	
	ObjectCache<AcquisitionOrder> globalOrder = new ObjectCache<AcquisitionOrder>();
	
	static public void enterLockCustom(Object lock) {
		Analyzer instance = instance();
		CustomLock customLock = instance.createCustomLock(lock, true);
		instance.enterLock_(customLock, 1);
	}
	
	static public void enterLockCustomUnscoped(Object lock) {
		Analyzer instance = instance();
		CustomLock customLock = instance.createCustomLock(lock, false);
		instance.enterLock_(customLock, 1);
	}
	
	static public void enterLockCustom(Object lock, int count) {
		Analyzer instance = instance();
		CustomLock customLock = instance.createCustomLock(lock, true);
		instance.enterLock_(customLock, count);
	}

	static public void enterLockCustom(Object lock, long count) {
		Analyzer instance = instance();
		CustomLock customLock = instance.createCustomLock(lock, true);
		instance.enterLock_(customLock, (int) count);
	}

	static public void enterLock(Object lock) {
		instance().enterLock_(lock, 1);
	}

	private void enterLock_(Object lock, int count) {
		if (!isEnabled())
			return;
		AcquisitionOrder localOrder = threadLocal.get();
		if (localOrder == null) {
			localOrder = new AcquisitionOrder();
			threadLocal.set(localOrder);
		}
		
		boolean rollBack = false;
		LockInfo info = new LockInfo();
		try {
			synchronized(localOrder) {
				if (trace)
					System.out.println("enter lock(" + count + "): " + Util.getUniqueIdentifier(lock));
	
				Thread currentThread = Thread.currentThread();
	
				// registering lock
				info.setLock(lock);
				info.count = count;
				info.threadId = getThreadID(currentThread);
				info.stackTrace = currentThread.getStackTrace();
				localOrder.order.add(info);
		
				if ((lock instanceof CustomLock) && !((CustomLock) lock).scoped)
					rollBack = true; // we rollback the insertion of the lock in the local stack
					
				for (int localIndex = 0; localIndex  < localOrder.order.size() - 1; localIndex++) {
					LockInfo precedent = localOrder.order.get(localIndex);
					if (precedent.getLock() == lock) { 
						// If the precedent is the lock, then do not record nor verify the next precedent
						// as such, because they are effectively acquired after, not before.
						// For example:  
						// 			Locking A, B, A
						// Records only (A->B) as lock order, not (A->B, B->A) 
						return;
					}
				}
	
				// ensure none of the previously acquired locks were acquired in a different order
				for (int localIndex = 0; localIndex  < localOrder.order.size() - 1; localIndex++) {
					LockInfo precedent = localOrder.order.get(localIndex);
					if (precedent.getLock() != lock) {
						
						synchronized(globalOrder) {
							AcquisitionOrder precedentOrder = globalOrder.getFromKey(precedent.getLockKey(), precedent.getLock());
							if (precedentOrder != null) {
								LockInfo conflict = precedentOrder.find(info);
								List<LockInfo> subList = localIndex > 0? localOrder.order.subList(0, localIndex):new ArrayList<LockInfo>();
								if (conflict != null && !conflict.containsCommonGuard(subList)) {
									ArrayList<Entry<String, StackTraceElement[]>> threadConflicts = conflict.getConflictingThreads(subList, getThreadID());
									if (threadConflicts.isEmpty()) {
										if (reportWarningInSameThreadConflicts) {
											LockInfo precedentConflict = new LockInfo();
											precedentConflict.setLock(precedent.getLock());
											precedentConflict.stackTrace = conflict.getContext(getThreadID());
											boolean shouldThrow = logger.reportConflict(TYPE_WARNING, getThreadID(), getThreadID(), info, precedent, conflict, precedentConflict);
											if (throwsOnWarning || shouldThrow)
												logger.throwException(TYPE_WARNING, info); 
										}
									}
									else {
										boolean shouldThrow = false;
										Iterator<Entry<String, StackTraceElement[]>> iterator = threadConflicts.iterator();
										while (iterator.hasNext()) {
											Entry<String, StackTraceElement[]> entry = iterator.next();
											LockInfo precedentConflict = new LockInfo();
											precedentConflict.setLock(precedent.getLock());
											precedentConflict.stackTrace = entry.getValue();
											shouldThrow |= logger.reportConflict(TYPE_ERROR, getThreadID(), entry.getKey(), info, precedent, conflict, precedentConflict);
										}
										if (throwsOnError || shouldThrow)
											logger.throwException(TYPE_ERROR, info);
									}
								}
							}
						}
					}
				}
		
				// record the precedence
				synchronized(globalOrder) {
					AcquisitionOrder order = globalOrder.getOrCreate(lock, AcquisitionOrder.class);
					if (order.defaultStackTrace == null)
						order.defaultStackTrace = info.stackTrace;
					ArrayList<LockInfo> guardList = new ArrayList<LockInfo>();
					for (int localIndex = 0; localIndex  < localOrder.order.size() - 1; localIndex++) {
						LockInfo precedent = localOrder.order.get(localIndex);
						LockInfo found = order.find(precedent);
						if (found == null) {
							found = precedent.copy();
							found.setAcquiringContext(info);
							order.order.add(found);
						}
						found.registerContext(getThreadID(), info.stackTrace, guardList);
						guardList.add(precedent);
					}
				}
			}
		} finally {
			if (rollBack)
				leaveLock_(info.getLock(), count, false);
		}
	}
	
	private static String getThreadID() {
		Thread thread = Thread.currentThread();
		return Long.toString(thread.getId()) + " (" + thread.getName() + ")";
	}

	private static String getThreadID(Thread thread) {
		return Long.toString(thread.getId()) + " (" + thread.getName() + ")";
	}

	static public void leaveLockCustom(Object lock) {
		Analyzer instance = instance();
		CustomLock customLock = instance.createCustomLock(lock, true);
		instance.leaveLock_(customLock, 1, false);
	}

	static public void leaveLockCustom(Object lock, int count) {
		Analyzer instance = instance();
		CustomLock customLock = instance.createCustomLock(lock, true);
		instance.leaveLock_(customLock, count, false);
	}

	static public void leaveLockCustomUnscoped(Object lock) {
		Analyzer instance = instance();
		CustomLock customLock = instance.createCustomLock(lock, false);
		instance.leaveLock_(customLock, 1, false);
	}

	static public void leaveLockCustom(Object lock, long count) {
		Analyzer instance = instance();
		CustomLock customLock = instance.createCustomLock(lock, false);
		instance.leaveLock_(customLock, (int) count, false);
	}

	static public void leaveLock(Object lock) {
		instance().leaveLock_(lock, 1, false);
	}

	private void leaveLock_(Object lock, int count, boolean rollback) {
		if (!isEnabled())
			return;
		AcquisitionOrder localOrder = threadLocal.get();
		
		if (localOrder == null || (leaveLockInThread(localOrder, lock, count) == null)) {
			ArrayList<AcquisitionOrder> orders = threadLocal.getAll();
			LockInfo otherThreadLockAcquisitionInfo = null;
			for (AcquisitionOrder order : orders) {
				otherThreadLockAcquisitionInfo = leaveLockInThread(order, lock, count); 
				if (otherThreadLockAcquisitionInfo != null)
					break;
			}
			if (!rollback) {
				boolean isScoped = !(lock instanceof CustomLock) || ((CustomLock)lock).scoped;
				if ((otherThreadLockAcquisitionInfo == null) && isScoped) {
					synchronized(unkownLocks) {
						Boolean value = unkownLocks.get(lock);
						if (value == null) {
							unkownLocks.put(lock, true);
							error("ERROR: leaving unknown lock (" + Util.getUniqueIdentifier(lock) + ")");
						}
					}
				}
				if (localOrder != null) {
					synchronized(localOrder) {
						// if we release the lock on a different thread, we must verify that no common locks 
						// exist, since otherwise, it can cause a deadlock.
						// For example, if thread1 acquires A -> B -> C, then thread2 acquires A then releases C
						// this means that thread1 can be blocked on C while thread2 is block on A, never getting 
						// to release C.
						// To verify this, we must get the global precedents of C, and see if any match the current
						// list of precedents on the local thread list, and see if any of those precedents were 
						// acquired in a different thread.
						String currentThreadID = getThreadID();
						for (int localIndex = 0; localIndex  < localOrder.order.size(); localIndex++) {
							LockInfo precedent = localOrder.order.get(localIndex);
							if (precedent.getLock() != lock) {
								synchronized(globalOrder) {
									AcquisitionOrder precedentOrder = globalOrder.get(lock);
									if (precedentOrder != null) {
										LockInfo commonPredecent = precedentOrder.find(precedent);
										if (commonPredecent != null) {
											ArrayList<Entry<String, StackTraceElement[]>> otherThreads = commonPredecent.findOtherContextThanThread(currentThreadID);
											if (otherThreads.size() > 0) {
												LockInfo currentLock = new LockInfo();
												Thread currentThread = Thread.currentThread();
												currentLock.setLock(lock);
												currentLock.count = 1;
												currentLock.threadId = getThreadID(currentThread);
												currentLock.stackTrace = currentThread.getStackTrace();
	
												boolean shouldThrow = logger.reportConflict(TYPE_ERROR_SIGNAL, currentThreadID, currentThreadID, currentLock, commonPredecent, commonPredecent.getAquiringContext(), precedent);
												if (throwsOnError || shouldThrow)
													logger.throwException(TYPE_ERROR_SIGNAL, precedent);
											}
										}
									}
								}
							}
						}
					}
				}
			}
		}
	}
	private LockInfo leaveLockInThread(AcquisitionOrder localOrder, Object lock, int count) {
		synchronized(localOrder) {
			if (trace)
				System.out.println("leave lock(" + count + "): " + Util.getUniqueIdentifier(lock));
			for (int i = localOrder.order.size() - 1; i >= 0; i--) {
				LockInfo info = localOrder.order.get(i);
				if (info.getLock() == lock) {
					localOrder.order.remove(i);
					return info;
				}
			}
			return null;
		}
	}

	ObjectCache<Boolean> unkownLocks = new ObjectCache<Boolean>();
	
	public int getInternalErrorCount() {
		return s_internalErrorCount;
	}
	
	static void error(String str) {
		s_internalErrorCount++;
		System.out.println(Logger.getPrintOutHeader() + str);
		Logger.printStrackTrace(Thread.currentThread().getStackTrace());
	}

	public boolean shouldInstrument(Class<?> cls) {
		return shouldInstrument(cls.getCanonicalName().replace('.', '/'));
	}
	
	public boolean shouldInstrument(String className) {
		if (instrumentationRestrictions.isEmpty())
			return true;
		Boolean value = instrumentationRestrictions.get(className);
		if (value == null)
			return false;
		return value;
	}

	public boolean isDebug() {
		return isDebug;
	}
	
	public int getCurrentLockCount() {
		AcquisitionOrder localOrder = threadLocal.get();
		if (localOrder == null)
			return 0;
		return localOrder.order.size();
	}
}
