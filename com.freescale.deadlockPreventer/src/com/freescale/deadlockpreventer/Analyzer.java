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

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Map.Entry;

public class Analyzer {
	public static final String PROPERTY_THROWING_CLASS = "com.freescale.deadlockpreventer.throwingClass";
	public static final String PROPERTY_INSTRUMENT_ONLY_LIST = "com.freescale.deadlockpreventer.instrumentOnlyList";
	public static final String PROPERTY_WRITE_INSTRUMENTED_CLASSES = "com.freescale.deadlockpreventer.writeInstrumentedClasses";
	public static final String PROPERTY_TRACE = "com.freescale.deadlockpreventer.trace";
	public static final String PROPERTY_THROWS_ON_WARNING = "com.freescale.deadlockpreventer.throwsOnWarning";
	public static final String PROPERTY_REPORT_RECURENT_CONFLICTS = "com.freescale.deadlockpreventer.reportRecurentConflicts";
	public static final String PROPERTY_THROWS_ON_ERROR = "com.freescale.deadlockpreventer.throwsOnError";
	public static final String PROPERTY_REPORT_WARNINGS = "com.freescale.deadlockpreventer.reportWarnings";
	public static final String PROPERTY_ABORT_ON_ERRORS = "com.freescale.deadlockpreventer.abortOnErrors";
	public static final String PROPERTY_LOG_TO_FILE = "com.freescale.deadlockpreventer.logToFile";
	public static final String PROPERTY_QUERY_SERVICE = "com.freescale.deadlockpreventer.queryService";
	public static final String PROPERTY_REPORT_SERVICE = "com.freescale.deadlockpreventer.reportService";
	public static final String PROPERTY_DUMP_LOCK_INFO = "com.freescale.deadlockpreventer.dumpLocksToFile";
	
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
	private boolean reportRecurentConflicts = false;
	private boolean isActive = false;
	private boolean writeInstrumentedClasses = false;
	private boolean abortOnErrors = false;
	private static int s_internalErrorCount = 0;
	private String debugException = IllegalMonitorStateException.class.getCanonicalName();
	private HashMap<String, Boolean> instrumentationRestrictions = new HashMap<String, Boolean>();
	private IConflictListener listener = new DefaultListener();
	private Class<?> classToThrow = OrderingException.class;
	
	ThreadLocal<Integer> enablementCount = new ThreadLocal<Integer>() {
		protected Integer initialValue() {
			return new Integer(1);
		}
		
	};
	static private Analyzer s_instance;
	
	public void setListener(IConflictListener listener) {
		if (listener == null)
			listener = new DefaultListener();
		this.listener = listener;
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
		String value = System.getProperty(PROPERTY_REPORT_SERVICE);
		if (value != null)
			listener = new NetworkClientListener(value);

		value = System.getProperty(PROPERTY_QUERY_SERVICE);
		if (value != null) {
			QueryService.Client client = new QueryService.Client(value);
			client.start();
		}

		value = System.getProperty(PROPERTY_LOG_TO_FILE);
		if (value != null)
			listener = new FileListener(value);
		
		value = System.getProperty(PROPERTY_DUMP_LOCK_INFO);
		if (value != null)
			setupDumpOnExit(value);

		abortOnErrors = Boolean.getBoolean(PROPERTY_ABORT_ON_ERRORS);
		reportWarningInSameThreadConflicts = Boolean.getBoolean(PROPERTY_REPORT_WARNINGS);
		throwsOnError = Boolean.getBoolean(PROPERTY_THROWS_ON_ERROR);
		reportRecurentConflicts = Boolean.getBoolean(PROPERTY_REPORT_RECURENT_CONFLICTS);
		throwsOnWarning = Boolean.getBoolean(PROPERTY_THROWS_ON_WARNING);
		trace = Boolean.getBoolean(PROPERTY_TRACE);
		writeInstrumentedClasses = Boolean.getBoolean(PROPERTY_WRITE_INSTRUMENTED_CLASSES);
		
		// separated by semicolons (;)
		value = System.getProperty(PROPERTY_INSTRUMENT_ONLY_LIST);
		if (value != null) {
			String classes[] = value.split(";");
			for (String cls : classes)
				instrumentationRestrictions.put(cls.replace('.', '/'), true);
		}

		value = System.getProperty(PROPERTY_THROWING_CLASS);
		if (value != null) {
			try {
				Class<?> cls = Class.forName(value);
				if (cls.isInstance(RuntimeException.class))
					classToThrow = cls;
			} catch (ClassNotFoundException e) {
				e.printStackTrace();
			}
		}
	}
	
	private void setupDumpOnExit(final String file) {
		System.setSecurityManager(new SecurityManager() {
			@Override
			public void checkExit(int status) {
				dumpLockInformation(file);
				super.checkExit(status);
			}
		});
	}

	private void dumpLockInformation(String file) {
		File outputFile = new File(file);
		if (!outputFile.getParentFile().exists())
			outputFile.getParentFile().mkdirs();
		try {
			if (!outputFile.exists())
				outputFile.createNewFile();
			FileWriter writer = new FileWriter(outputFile);
			dumpLockInformation(writer);
			writer.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public static void dumpLockInformation(Writer writer) {
		ILock[] locks = new Statistics().locks();
		dumpLockInformation(locks, writer);
	}
	
	public static void dumpLockInformation(ILock[] locks, Writer writer) {
		try {
			for (ILock lock : locks) {
				writer.write("lock: " + lock.getID() + ", precedents(" + lock.getPrecedents().length + " followers(" + lock.getFollowers().length + ")\n");
				writeStack(writer, "  ", lock.getStackTrace());
				writer.write("  precedents:\n");
				for (IContext context : lock.getPrecedents()) {
					writer.write("    " + context.getLock().getID() + ", thread id(" + context.getThreadID() + ")");
					writeStack(writer, "    ", context.getStackTrace());
				}
				writer.write("  followers:\n");
				for (IContext context : lock.getFollowers()) {
					writer.write("    " + context.getLock().getID() + ", thread id(" + context.getThreadID() + ")");
					writeStack(writer, "    ", context.getStackTrace());
				}
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private static void writeStack(Writer writer, String prefix, String[] stackTrace) throws IOException {
		for (String stack : stackTrace) {
			writer.write(prefix + stack + "\n");
		}
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

	static class LockInfo {
		private Object lock;
		String lockKey;
		
		public void setLock(Object lock) {
			this.lock = lock;
			lockKey = ObjectCache.getKey(lock);
		}
		
		public Object getLock() {
			return lock;
		}
		
		long threadId = -1;
		int count = 0;
		StackTraceElement[] stackTrace;
		HashMap<String, StackTraceElement[]> contextsPerThread = new HashMap<String, StackTraceElement[]>();
		ArrayList<LockInfo> guards = null;
		ArrayList<LockInfo> phantomGuards = null;
		LockInfo acquiringContext = null;
		
		public void print(String header) {
			StringBuffer buffer = new StringBuffer();
			printStrackTrace(buffer, header, stackTrace, 3);
			System.out.print(buffer.toString());
		}
		
		public void print(StringBuffer buffer, String header) {
			printStrackTrace(buffer, header, stackTrace, 3);
		}
		
		public String toString() {
			return "LockInfo: " + safeToString(lock);
		}
		
		public LockInfo copy() {
			LockInfo info = new LockInfo();
			info.lock = lock;
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
							tmp = new ArrayList<Analyzer.LockInfo>();
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
				phantomGuards = new ArrayList<Analyzer.LockInfo>();
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
	
	public static void printStrackTrace(StackTraceElement[] stackTrace) {
		StringBuffer buffer = new StringBuffer();
		printStrackTrace(buffer, new String(), stackTrace, 0);
		System.out.print(buffer.toString());
	}
	
	public static void printStrackTrace(StringBuffer buffer, String header, StackTraceElement[] stackTrace, int startOffset) {
		for (int i = startOffset; i < stackTrace.length; i++)
			buffer.append(header + stackTrace[i].toString() + "\n");
	}

	// last item is the latest acquired
	static class AcquisitionOrder {
		ArrayList<LockInfo> order = new ArrayList<LockInfo>();

		public LockInfo find(LockInfo precedent) {
			for (int i = 0; i  < order.size(); i++) {
				LockInfo lockInfo = order.get(i);
				if (lockInfo.lock == precedent.lock)
					return lockInfo;
			}
			return null;
		}
	}
	
	static class CustomLock {
		Object lock;
		boolean scoped;
		
		public CustomLock(Object lock2, boolean scoped) {
			lock = lock2;
			this.scoped = scoped;
		}

		public String toString() {
			return "(Custom) " + safeToString(lock);
		}
		
	}
	
	static class CacheEntry<E> {
		public CacheEntry(Object obj, E value2) {
			object = obj;
			value = value2;
		}
		Object object;
		E value;
	}
	
	// We use a custom object cache because we can't use a simple HashMap<Object>, since the object.hashCode() 
	// can be overridden by clients and cause deadlocks. 
	static class ObjectCache<T> {
		HashMap<String, ArrayList<CacheEntry<T>>> cache = new HashMap<String, ArrayList<CacheEntry<T>>>();
		
		public T get(Object obj) {
			return getFromKey(getKey(obj), obj);
		}
		
		public T getFromKey(Object key, Object obj) {
			ArrayList<CacheEntry<T>> cacheLine = cache.get(key);
			if (cacheLine != null) {
				ListIterator<CacheEntry<T>> iterator = cacheLine.listIterator(cacheLine.size());
				while (iterator.hasPrevious()) {
					CacheEntry<T> entry = iterator.previous();
					if (entry.object == obj)
						return entry.value;
				}
			}
			return null;
		}
		
		public T getOrCreate(Object obj, Class<T> cls) {
			String key = getKey(obj);
			ArrayList<CacheEntry<T>> cacheLine = cache.get(key);
			if (cacheLine != null) {
				ListIterator<CacheEntry<T>> iterator = cacheLine.listIterator(cacheLine.size());
				while (iterator.hasPrevious()) {
					CacheEntry<T> entry = iterator.previous();
					if (entry.object == obj)
						return entry.value;
				}
			} else {
				cacheLine = new ArrayList<CacheEntry<T>>();
				cache.put(key, cacheLine);
			}
			T value;
			try {
				value = cls.newInstance();
			} catch (Throwable e) {
				e.printStackTrace();
				return null;
			}
			cacheLine.add(new CacheEntry<T>(obj, value));
			return value;
		}

		public static String getKey(Object obj) {
			Class<?> cls = obj.getClass();
			if (cls.equals(CustomLock.class))
				cls = ((CustomLock) obj).lock.getClass();
			return cls.getSimpleName();
		}

		public void put(Object obj, T value) {
			String key = getKey(obj);
			ArrayList<CacheEntry<T>> cacheLine = cache.get(key);
			if (cacheLine == null) {
				cacheLine = new ArrayList<CacheEntry<T>>();
				cache.put(key, cacheLine);
			}
			cacheLine.add(new CacheEntry<T>(obj, value));
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
					System.out.println("enter lock(" + count + "): " + safeToString(lock));
	
				Thread currentThread = Thread.currentThread();
	
				// registering lock
				info.setLock(lock);
				info.count = count;
				info.threadId = currentThread.getId();
				info.stackTrace = currentThread.getStackTrace();
				localOrder.order.add(info);
		
				if ((lock instanceof CustomLock) && !((CustomLock) lock).scoped)
					rollBack = true; // we rollback the insertion of the lock in the local stack
					
				for (int localIndex = 0; localIndex  < localOrder.order.size() - 1; localIndex++) {
					LockInfo precedent = localOrder.order.get(localIndex);
					if (precedent.lock == lock) { 
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
					if (precedent.lock != lock) {
						
						synchronized(globalOrder) {
							AcquisitionOrder precedentOrder = globalOrder.getFromKey(precedent.lockKey, precedent.lock);
							if (precedentOrder != null) {
								LockInfo conflict = precedentOrder.find(info);
								List<LockInfo> subList = localIndex > 0? localOrder.order.subList(0, localIndex):new ArrayList<LockInfo>();
								if (conflict != null && !conflict.containsCommonGuard(subList)) {
									ArrayList<Entry<String, StackTraceElement[]>> threadConflicts = conflict.getConflictingThreads(subList, getThreadID());
									if (threadConflicts.isEmpty()) {
										if (reportWarningInSameThreadConflicts) {
											LockInfo precedentConflict = new LockInfo();
											precedentConflict.lock = precedent.lock;
											precedentConflict.stackTrace = conflict.getContext(getThreadID());
											boolean shouldThrow = reportConflict(TYPE_WARNING, getThreadID(), getThreadID(), info, precedent, conflict, precedentConflict);
											if (throwsOnWarning || shouldThrow)
												throwException(TYPE_WARNING, info); 
										}
									}
									else {
										boolean shouldThrow = false;
										Iterator<Entry<String, StackTraceElement[]>> iterator = threadConflicts.iterator();
										while (iterator.hasNext()) {
											Entry<String, StackTraceElement[]> entry = iterator.next();
											LockInfo precedentConflict = new LockInfo();
											precedentConflict.lock = precedent.lock;
											precedentConflict.stackTrace = entry.getValue();
											shouldThrow |= reportConflict(TYPE_ERROR, getThreadID(), entry.getKey(), info, precedent, conflict, precedentConflict);
										}
										if (throwsOnError || shouldThrow)
											throwException(TYPE_ERROR, info);
									}
								}
							}
						}
					}
				}
		
				// record the precedence
				synchronized(globalOrder) {
					AcquisitionOrder order = globalOrder.getOrCreate(lock, AcquisitionOrder.class);
					if (order.order.isEmpty()) {
						order = new AcquisitionOrder();
						globalOrder.put(lock, order);
						assert(localOrder.order.size() == 1);
					}
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
				leaveLock_(info.lock, count, false);
		}
	}
	
	private void throwException(int type, LockInfo info) {
		try {
			String msg = getPrintOutHeader() + getMessageHeader(type, info.lock);
			try {
				try {
					Constructor<?> constr = classToThrow.getConstructor(String.class);
					Object obj = constr.newInstance(msg);
					throw (RuntimeException) obj;
				} catch (IllegalArgumentException e) {
					throw (RuntimeException) classToThrow.newInstance();
				} catch (NoSuchMethodException e) {
					throw (RuntimeException) classToThrow.newInstance();
				} catch (InvocationTargetException e) {
					throw (RuntimeException) classToThrow.newInstance();
				}
			} catch (SecurityException e) {
				e.printStackTrace();
			} catch (InstantiationException e) {
				e.printStackTrace();
			} catch (IllegalAccessException e) {
				e.printStackTrace();
			}
			throw new RuntimeException(msg);
		} catch (Throwable t) {
			// nothing
		}
	}

	private static String getThreadID() {
		Thread thread = Thread.currentThread();
		return Long.toString(thread.getId()) + " (" + thread.getName() + ")";
	}
	
	static class ConflictReport {
		String threadID;
		String conflictThreadID;
		Object lock;
		Object precedent;
		Object conflict;
		Object conflictPrecedent;
		
		public ConflictReport(String threadID, String conflictThreadID,
				Object lock, Object precedent, Object conflict,
				Object conflictPrecedent) {
			super();
			this.threadID = threadID;
			this.conflictThreadID = conflictThreadID;
			this.lock = lock;
			this.precedent = precedent;
			this.conflict = conflict;
			this.conflictPrecedent = conflictPrecedent;
		}

		public boolean equals(Object o) {
			if (o instanceof ConflictReport) {
				ConflictReport report = (ConflictReport) o;
				return report.threadID.equals(threadID) &&
					report.conflictThreadID.equals(conflictThreadID) &&
					(report.lock == lock &&
					report.precedent == precedent &&
					report.conflict == conflict &&
					report.conflictPrecedent == conflictPrecedent) || // objects can be swapped too
					(report.lock == precedent &&
							report.precedent == lock &&
							report.conflict == conflictPrecedent &&
							report.conflictPrecedent == conflict);
			}
			return false;
		}
	}
	
	ArrayList<ConflictReport> alreadyReportedConflicts = new ArrayList<Analyzer.ConflictReport>();
	
	private boolean reportConflict(int type, String threadID, String conflictThreadID, LockInfo info, LockInfo precedent,
			LockInfo conflictLock, LockInfo conflictPrecedent) {
		if (!reportRecurentConflicts) {
			synchronized(alreadyReportedConflicts) {
				ConflictReport report = new ConflictReport(threadID, 
						conflictThreadID, 
						info != null ? info.lock : null, 
								precedent != null ? precedent.lock : null, 
										conflictLock != null ? conflictLock.lock : null, 
												conflictPrecedent != null ? conflictPrecedent.lock : null);
				if (alreadyReportedConflicts.contains(report))
					return false;
				alreadyReportedConflicts.add(report);
			}
		}
		boolean shouldThrow = false;
		StringBuffer buffer = new StringBuffer();
		buffer.append(getPrintOutHeader() + getMessageHeader(type, info.lock) + " in thread: " + threadID + "\n");
		String indent = "  ";
		if (type == Analyzer.TYPE_ERROR_SIGNAL) {
			info.print(buffer, getEmptyPrintOutHeader() + indent);
			
			buffer.append("\n");
			buffer.append(getEmptyPrintOutHeader() + "While holding lock: " + safeToString(conflictPrecedent.lock)  + "\n");
			conflictPrecedent.print(buffer, getEmptyPrintOutHeader() + indent);

			if (conflictLock != null) {
				buffer.append("\n");
				buffer.append(getEmptyPrintOutHeader() + "Previously acquired signal: " + safeToString(conflictLock.lock)  + "\n");
				conflictLock.print(buffer, getEmptyPrintOutHeader() + indent);
			}

			buffer.append("\n");
			buffer.append(getEmptyPrintOutHeader() + "\nPreviously held lock: " + safeToString(precedent.lock)  + " in thread: " + conflictThreadID  + "\n");
			precedent.print(buffer, getEmptyPrintOutHeader() + indent);
		}
		else {
			info.print(buffer, getEmptyPrintOutHeader() + indent);
			buffer.append("\n");
			buffer.append(getEmptyPrintOutHeader() + "\nwith predecent : " + safeToString(precedent.lock) + "\n");
			precedent.print(buffer, getEmptyPrintOutHeader() + indent);
			
			buffer.append("\n");
			buffer.append(getEmptyPrintOutHeader() + "\nPreviously acquired lock: " + safeToString(conflictLock.lock)  + " in thread: " + conflictThreadID  + "\n");
			conflictLock.print(buffer, getEmptyPrintOutHeader() + indent);

			buffer.append("\n");
			buffer.append(getEmptyPrintOutHeader() + "\nPreviously acquired precedent: " + safeToString(conflictPrecedent.lock)  + "\n");
			conflictPrecedent.print(buffer, getEmptyPrintOutHeader() + indent);
		}

		int result = listener.report(type, 
				threadID, 
				conflictThreadID, 
				getExternalObject(info.lock), 
				info.stackTrace, 
				getExternalObject(precedent.lock), 
				precedent.stackTrace, 
				conflictLock != null ? getExternalObject(conflictLock.lock) : null, 
				conflictLock != null ? conflictLock.stackTrace : null, 
				conflictPrecedent != null ? getExternalObject(conflictPrecedent.lock) : null, 
				conflictPrecedent != null ? conflictPrecedent.stackTrace : null, 
				buffer.toString());
		
		if ((result & IConflictListener.DEBUG) != 0) {
			debug();
		}
		if ((result & IConflictListener.EXCEPTION) != 0)
			shouldThrow = true;
		
		if ((result & IConflictListener.LOG_TO_CONSOLE) != 0)
			System.out.println(buffer.toString());

		if ((result & IConflictListener.ABORT) != 0)
			System.exit(-1);
		
		return shouldThrow;
	}

	private Object getExternalObject(Object lock) {
		if (lock instanceof CustomLock)
			return ((CustomLock) lock).lock;
		return lock;
	}

	private void debug() {
		try {
			Class<?> cls = Class.forName(debugException);
			Object obj = cls.newInstance();
			throw (Throwable) obj;
		} catch (ClassNotFoundException e) {
			e.printStackTrace();
			throw new IllegalMonitorStateException();
		} catch (InstantiationException e) {
			e.printStackTrace();
		} catch (IllegalAccessException e) {
			e.printStackTrace();
		} catch (Throwable e) {
			e.printStackTrace();
		}
	}

	private String getMessageHeader(int type, Object lock) {
		String msg = "ERROR";
		switch (type)
		{
		case Analyzer.TYPE_ERROR:
			msg = "ERROR: Inconsistent locking order while acquiring lock: " + safeToString(lock);;
			break;
		case Analyzer.TYPE_WARNING:
			msg = "WARNING: Inconsistent locking order while acquiring lock: " + safeToString(lock);;
			break;
		case Analyzer.TYPE_ERROR_SIGNAL:
			msg = "ERROR: Inconsistent messaging order while signaling object: " + safeToString(lock);;
			break;
		}
		
		return msg;
	}

	private String getEmptyPrintOutHeader() {
		int count = getPrintOutHeader().length();
		StringBuilder builder = new StringBuilder();
		while(count-- > 0)
			builder.append(" ");
		return builder.toString();
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
							error("ERROR: leaving unknown lock (" + safeToString(lock) + ")");
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
							if (precedent.lock != lock) {
								synchronized(globalOrder) {
									AcquisitionOrder precedentOrder = globalOrder.get(lock);
									if (precedentOrder != null) {
										LockInfo commonPredecent = precedentOrder.find(precedent);
										if (commonPredecent != null) {
											ArrayList<Entry<String, StackTraceElement[]>> otherThreads = commonPredecent.findOtherContextThanThread(currentThreadID);
											if (otherThreads.size() > 0) {
												LockInfo currentLock = new LockInfo();
												Thread currentThread = Thread.currentThread();
												currentLock.lock = lock;
												currentLock.count = 1;
												currentLock.threadId = currentThread.getId();
												currentLock.stackTrace = currentThread.getStackTrace();
	
												boolean shouldThrow = reportConflict(TYPE_ERROR_SIGNAL, currentThreadID, currentThreadID, currentLock, commonPredecent, commonPredecent.getAquiringContext(), precedent);
												if (throwsOnError || shouldThrow)
													throwException(TYPE_ERROR_SIGNAL, precedent);
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
				System.out.println("leave lock(" + count + "): " + safeToString(lock));
			for (int i = localOrder.order.size() - 1; i >= 0; i--) {
				LockInfo info = localOrder.order.get(i);
				if (info.lock == lock) {
					localOrder.order.remove(i);
					return info;
				}
			}
			return null;
		}
	}

	ObjectCache<Boolean> unkownLocks = new ObjectCache<Boolean>();
	
	static String getPrintOutHeader() {
		return "***DEADLOCK PREVENTER*** ";
	}
	
	public int getInternalErrorCount() {
		return s_internalErrorCount;
	}
	
	static void error(String str) {
		s_internalErrorCount++;
		System.out.println(getPrintOutHeader() + str);
		printStrackTrace(Thread.currentThread().getStackTrace());
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

	public class DefaultListener implements IConflictListener {
		@Override
		public int report(int type, String threadID, String conflictThreadID,
				Object lock, StackTraceElement[] lockStack, Object precedent,
				StackTraceElement[] precedentStack, Object conflict,
				StackTraceElement[] conflictStack, Object conflictPrecedent,
				StackTraceElement[] conflictPrecedentStack, String message) {
			if (abortOnErrors && ((type & TYPE_ERROR) != 0))
				return IConflictListener.ABORT;
			return IConflictListener.CONTINUE | IConflictListener.LOG_TO_CONSOLE;
		}
	}
	
	static ObjectCache<Integer> uniqueIDSet = new ObjectCache<Integer>();
	static int uniqueIDCount = 0;
	
	public static String safeToString(Object obj) {
		if (obj != null) {
			String id = null;
			synchronized(uniqueIDSet) {
				Integer idValue = uniqueIDSet.get(obj);
				if (idValue == null) {
					uniqueIDCount++;
					uniqueIDSet.put(obj, new Integer(uniqueIDCount));
					id = Integer.toString(uniqueIDCount);
				}
				else
					id = idValue.toString();
			}
			Class<?> cls = obj.getClass();
			if (cls.equals(CustomLock.class))
				cls = ((CustomLock) obj).lock.getClass();
			return cls.getName() + " (id=" + id + ")";
		}
		return new String();
	}
}
