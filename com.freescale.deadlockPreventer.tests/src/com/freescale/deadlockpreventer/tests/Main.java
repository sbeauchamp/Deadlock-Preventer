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
package com.freescale.deadlockpreventer.tests;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.HashMap;
import java.util.concurrent.locks.ReentrantLock;

import org.eclipse.core.runtime.jobs.ILock;
import org.eclipse.core.runtime.jobs.Job;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.freescale.deadlockpreventer.Analyzer;
import com.freescale.deadlockpreventer.IConflictListener;
import com.freescale.deadlockpreventer.NetworkClientListener;
import com.freescale.deadlockpreventer.NetworkServer;
import com.freescale.deadlockpreventer.ReportService;

public class Main {

	@Test
	public void testProperty() {
		final Property property = new Property();
		fork(new Runnable() {
			@Override
			public void run() {
				property.get("foo");
			}
		});
		property.set("bar", "value");
		assertConflictError();
	}

	private Object lockA = new Object();
	private String lockB = new String();
	private Object lockC = new Object();
	private Object lockD = new Object();
	private Object lockE = new Object();
	private ILock lock_Eclipse_A = Job.getJobManager().newLock(); 
	private ILock lock_Eclipse_B = Job.getJobManager().newLock(); 
	ConflictReporter defaultReporter = new ConflictReporter();
	
	private void assertNoConflict() {
		assertTrue(defaultReporter.noReports);
		defaultReporter.reset();
	}

	private void assertConflictError() {
		assertTrue(defaultReporter.gotError);
		defaultReporter.reset();
	}

	private void assertConflictSignalError() {
		assertTrue(defaultReporter.gotErrorSignal);
		defaultReporter.reset();
	}

	private void assertConflictWarning() {
		assertTrue(defaultReporter.gotWarning);
		defaultReporter.reset();
	}

	@Before
	public void setUp() throws Exception {
		Analyzer.instance().setReportWarningsInSameThread(true);
		Analyzer.instance().setThrowsOnError(false);
		Analyzer.instance().setThrowsOnWarning(false);
		Analyzer.instance().setListener(defaultReporter);
		assertTrue(Analyzer.instance().isActive());
		assertTrue(Analyzer.instance().shouldInstrument(Main.class));
		assertTrue(Analyzer.instance().getCurrentLockCount() == 0);
	}
	
	@After
	public void cleanUp() {
		assertTrue(Analyzer.instance().getCurrentLockCount() == 0);
		Analyzer.instance().setReportWarningsInSameThread(false);
		Analyzer.instance().setThrowsOnError(false);
		Analyzer.instance().setThrowsOnWarning(false);
		Analyzer.instance().setListener(null);
		assertTrue(Analyzer.instance().getInternalErrorCount() == 0);
	}

	@Test
	public void test0() {
		assertTrue(Analyzer.instance().isActive());
	}

	@Test
	public void test1() {
		synchronized(this) {
			code();
		}
		assertNoConflict();
	}
	
	@Test
	public synchronized void test2() {
		code();
		assertNoConflict();
	}

	@Test
	public synchronized void test3() {
		synchronized(this) {
			code();
		}
		assertNoConflict();
	}

	@Test
	public synchronized void test4() {
		synchronized(lockA) {
			code();
		}
		assertNoConflict();
	}

	@Test
	public void generateWarning() {
		test_a();
		test_b();
		assertConflictWarning();
	}

	@Test
	public void generateError() {
		fork(new Runnable() {
			@Override
			public void run() {
				test_c();
			}
		});
		test_d();
		assertConflictError();
	}

	private String getLock_b() {
		return lockB;
	}
	
	private void test_a() {
		synchronized(lockB) {
			synchronized(this) {
				code();
			}
		}
	}

	private synchronized void test_b() {
		synchronized(getLock_b()) {
			code();
		}
	}

	private void test_c() {
		synchronized(lockC) {
			synchronized(this) {
				code();
			}
		}
	}

	private synchronized void test_d() {
		synchronized(lockC) {
			code();
		}
	}

	@Test
	public void testRequiresMultiLevelDependents() {
		test_e();
		test_f();
		assertConflictWarning();
	}

	private void test_e() {
		synchronized(lock_Eclipse_A) {
			synchronized(this) {
				code();
			}
		}
	}

	private synchronized void test_f() {
		synchronized(lock_Eclipse_A) {
			code();
		}
	}

	@Test
	public void generateEclipseWarning() {
		test_e1();
		test_f1();
		assertConflictWarning();
	}

	private void test_e1() {
		try {
			lock_Eclipse_A.acquire();
			synchronized(this) {
				code();
			}
		}
		finally {
			lock_Eclipse_A.release();
		}
	}

	private synchronized void test_f1() {
		try {
			lock_Eclipse_A.acquire();
			code();
		}
		finally {
			lock_Eclipse_A.release();
		}
	}

	@Test
	public void generateErrorMultiLevel() {
		fork(new Runnable() {
			@Override
			public void run() {
				test_c1();
			}
		});
		test_d1();
		assertConflictError();
	}

	private void test_c1() {
		synchronized(lockD) {
			synchronized(this) {
				synchronized(lockE) {
					code();
				}
			}
		}
	}

	private synchronized void test_d1() {
		synchronized(lockE) {
			synchronized(lockD) {
				code();
			}
		}
	}

	private void code() {
		System.out.println(Thread.currentThread().getStackTrace()[2]);		
	}

	@Test
	public void generateEclipseError() {
		test_e2();
		test_f2();
		assertConflictWarning();
	}

	private void test_e2() {
		try {
			lock_Eclipse_A.acquire();
			try {
				lock_Eclipse_B.acquire();
				code();
			}
			finally {
				lock_Eclipse_B.release();
			}
		}
		finally {
			lock_Eclipse_A.release();
		}
	}

	private void test_f2() {
		try {
			lock_Eclipse_B.acquire();
			try {
				lock_Eclipse_A.acquire();
				code();
			}
			finally {
				lock_Eclipse_A.release();
			}
		}
		finally {
			lock_Eclipse_B.release();
		}
	}
	
	Object slave1 = new Object();
	Object slave2 = new Object();
	Object master = new Object();
	
	@Test 
	public void testMasterLockNonLock() {
		synchronized(master) {
			synchronized(slave1) {
				synchronized(slave2) {
				}
			}
		}
		synchronized(slave2) {
			synchronized(slave1) {
				
			}
		}
		assertConflictWarning();
	}
	
	Object slave1a = new String("slave1a");
	Object slave2a = new String("slave2a");
	Object master_a = new String("master_a");;
	@Test 
	public void testMasterLock() {
		synchronized(master_a) {
			synchronized(slave1a) {
				synchronized(slave2a) {
				}
			}
		}
		// the following should work
		synchronized(master_a) {
			synchronized(slave2a) {
				synchronized(slave1a) {
				}
			}
		}
		assertNoConflict();
	}

	Object recursive1 = new Object();
	Object recursive2 = new Object();
	
	@Test 
	public void testRecursive() {
		synchronized(recursive1) {
			synchronized(recursive2) {
				synchronized(recursive1) {
				}
			}
		}
		// the order <recursive2, recursive1> should not have been recorded
		synchronized(recursive1) {
			synchronized(recursive2) {
			}
		}
		assertNoConflict();
	}
	
	private final class ServerListener implements ReportService.IListener {
		public boolean wasCalled = false;
		public int report(String type, String threadID,
				String conflictThreadID, String lock, String[] lockStack,
				String precedent, String[] precedentStack, String conflict,
				String[] conflictStack, String conflictPrecedent,
				String[] conflictPrecedentStack, String message) {
			wasCalled = true;
			return IConflictListener.CONTINUE;
		}
	}

	private final class ConflictReporter implements IConflictListener {
		@Override
		public int report(int type, String threadID, String conflictThreadID,
				Object lock, StackTraceElement[] lockStack, Object precedent,
				StackTraceElement[] precedentStack, Object conflict,
				StackTraceElement[] conflictStack, Object conflictPrecedent,
				StackTraceElement[] conflictPrecedentStack, String message) {
			noReports = false;
			if (type == Analyzer.TYPE_WARNING)
				gotWarning = true;
			if (type == Analyzer.TYPE_ERROR)
				gotError = true;
			if (type == Analyzer.TYPE_ERROR_SIGNAL)
				gotErrorSignal = true;
			this.lock = lock;
			this.precedent = precedent;
			this.conflict = conflict;
			this.conflictPrecedent = conflictPrecedent;
			return IConflictListener.CONTINUE | IConflictListener.LOG_TO_CONSOLE;
		}
		
		public void reset() {
			noReports = true;
			gotWarning = false;
			gotError = false;
			gotErrorSignal = false;
		}

		public boolean noReports = true;
		public boolean gotWarning = false;
		public boolean gotError = false;
		public boolean gotErrorSignal = false;
		public Object lock;
		public Object precedent;
		public Object conflict;
		public Object conflictPrecedent;

		public boolean hasNoErrors() {
			return gotWarning == false && gotError == false && gotErrorSignal == false;
		}
	}

	static class Entry {
		boolean gotWarning = false;
		boolean gotError = false;
	}
	
	@Test
	public void useJDKLocks() {
		Analyzer.instance().setThrowsOnWarning(false);

		ConflictReporter reporter = new ConflictReporter();
		Analyzer.instance().setListener(reporter);
		
		ReentrantLock lock = new ReentrantLock();
		ReentrantLock lock2 = new ReentrantLock();
		try {
			lock.lock();
			try {
				lock2.lock();
			}
			finally {
				lock2.unlock();
			}
		}
		finally {
			lock.unlock();
		}

		try {
			try {
				lock2.lock();
				try {
					lock.lock();
				}
				finally {
					lock.unlock();
				}
			}
			finally {
				lock2.unlock();
			}
		}
		finally {
			assertTrue(reporter.gotWarning);
			assertTrue(reporter.lock == lock);
			assertTrue(reporter.precedent == lock2);
			assertTrue(reporter.conflict == lock);
			assertTrue(reporter.conflictPrecedent == lock2);
		}
	}

	@Test
	public void useJDKLocksError() {
		Analyzer.instance().setThrowsOnWarning(false);
		Analyzer.instance().setThrowsOnError(false);

		ConflictReporter reporter = new ConflictReporter();
		Analyzer.instance().setListener(reporter);
		
		final ReentrantLock lock = new ReentrantLock();
		final ReentrantLock lock2 = new ReentrantLock();
		fork(new Runnable() {
			@Override
			public void run() {
				try {
					lock.lock();
					try {
						lock2.lock();
					}
					finally {
						lock2.unlock();
					}
				}
				finally {
					lock.unlock();
				}
			}
		});

		try {
			try {
				lock2.lock();
				try {
					lock.lock();
				}
				finally {
					lock.unlock();
				}
			}
			finally {
				lock2.unlock();
			}
		}
		finally {
			assertTrue(reporter.gotError);
		}
	}

	Object slave1b = new String("slave1b");
	Object slave2b = new String("slave2b");
	Object master_b = new String("master_b");;

	@Test 
	public void testMasterLockInThread() {
		Runnable runnable = new Runnable() {
			public void run() {
				synchronized(master_b) {
					synchronized(slave1b) {
						synchronized(slave2b) {
						}
					}
				}
			}
		};
		// the following should work
		synchronized(master_b) {
			synchronized(slave2b) {
				synchronized(slave1b) {
				}
			}
		}
		fork(runnable);
		assertNoConflict();
	}

	Object slave1c = new String("slave1c");
	Object slave2c = new String("slave2c");
	Object master_c = new String("master_c");;

	@Test 
	public void testMasterLockNotCovered() {
		synchronized(master_c) {
			synchronized(slave1c) {
				synchronized(slave2c) {
				}
			}
		}
		synchronized(master_c) {
			synchronized(slave2c) {
				synchronized(slave1c) {
				}
			}
		}
		// the following should fail
		synchronized(slave2c) {
			synchronized(slave1c) {
			}
		}
		assertConflictWarning();
	}
	
	@Test
	public void testReturnStatements() {
		returnVoid();
		boolean b = returnBoolean();
		assertTrue(b == true);
		int i = returnInt();
		assertTrue(i == 2);
		char c = returnChar();
		assertTrue(c == 's');
		long l = returnLong();
		assertTrue(l == 23);
		float f = returnFloat();
		assertTrue(f == 23.2f);
		double d = returnDouble();
		assertTrue(d == 23.2);
		int[] a = returnArray();
		assertTrue(a[0]== 2 && a[1] == 3);
		Object o = returnObject();
		assertTrue(o.equals("string"));
		Object[] oa = returnObjectArray();
		assertTrue(oa[0].equals("hi") && oa[1].equals("hello"));
	}
	
	private synchronized Object returnObject() {
		code();
		return "string";
	}
	private synchronized Object[] returnObjectArray() {
		code();
		return new Object[] {"hi", "hello"};
	}
	private synchronized void returnVoid() {
		code();
	}
	private synchronized boolean returnBoolean() {
		code();
		return true;
	}
	private synchronized int returnInt() {
		code();
		return 2;
	}
	private synchronized char returnChar() {
		code();
		return 's';
	}
	private synchronized long returnLong() {
		code();
		return 23;
	}
	private synchronized float returnFloat() {
		code();
		return 23.2f;
	}
	private synchronized double returnDouble() {
		code();
		return 23.2;
	}
	private synchronized int[] returnArray() {
		code();
		return new int[] {2, 3};
	}
	
	static class DeadlockingToString {
		boolean toStringCalled = false;
		
		public String toString() {
			toStringCalled = true;
			return super.toString();
		}
	}
	
	@Test
	public void testToStringIsNotCalled() {
		DeadlockingToString lock = new DeadlockingToString();
		synchronized(lock) {
			code();
		}
		assertTrue(lock.toStringCalled == false);
	}

	@Test
	public void testToStringIsNotCalledWithConflict() {
		final DeadlockingToString lock1 = new DeadlockingToString();
		final DeadlockingToString lock2 = new DeadlockingToString();
		fork(new Runnable() {
			public void run() {
				synchronized(lock1) {
					synchronized(lock2) {
						code();
					}
				}
			} 
		});
		synchronized(lock2) {
			synchronized(lock1) {
				code();
			}
		}
		assertConflictError();
		assertTrue(lock1.toStringCalled == false);
		assertTrue(lock2.toStringCalled == false);
	}

	@Test
	public void testToStringIsNotCalledWithConflictInNetworkClient() {
		NetworkServer server = new NetworkServer();
		server.start(0);
		String key = server.createNewSessionKey(ReportService.ID);
		ReportService service = new ReportService();
		server.registerSevice(key, service);
		ServerListener listener = new ServerListener();
		service.setListener(listener);
		NetworkClientListener client = new NetworkClientListener("localhost:" + server.getListeningPort() + ":" + key);
		Analyzer.instance().setListener(client);
		final DeadlockingToString lock1 = new DeadlockingToString();
		final DeadlockingToString lock2 = new DeadlockingToString();
		fork(new Runnable() {
			public void run() {
				synchronized(lock1) {
					synchronized(lock2) {
						code();
					}
				}
			} 
		});
		synchronized(lock2) {
			synchronized(lock1) {
				code();
			}
		}
		client.stop();
		server.stop();
		assertTrue(lock1.toStringCalled == false);
		assertTrue(lock2.toStringCalled == false);
		assertTrue(listener.wasCalled);
	}
	
	private void fork(Runnable runnable) {
		Thread thread = new Thread(runnable);
		thread.start();
		try {
			thread.join();
		} catch (InterruptedException e1) {
			e1.printStackTrace();
		}
	}

	@Test
	public void testLocalStackIsProper() {
		final ReentrantLock lock = new ReentrantLock();
		final ReentrantLock lock2 = new ReentrantLock();
		lock.lock();
		assertTrue(Analyzer.instance().getCurrentLockCount() == 1);
		
		fork(new Runnable() {
			@Override
			public void run() {
				assertTrue(Analyzer.instance().getCurrentLockCount() == 0);
				lock2.lock();
				assertTrue(Analyzer.instance().getCurrentLockCount() == 1);
			}
		});
		assertTrue(Analyzer.instance().getCurrentLockCount() == 1);
		lock.unlock();
		assertTrue(Analyzer.instance().getCurrentLockCount() == 0);
		assertNoConflict();
	}

	@Test
	public void testAcquiringOnOtherThreadIsSupported() {
		final CustomLock lock = new CustomLock();
		lock.lock();
		assertTrue(Analyzer.instance().getCurrentLockCount() == 1);
		
		fork(new Runnable() {
			@Override
			public void run() {
				assertTrue(Analyzer.instance().getCurrentLockCount() == 0);
				lock.unlock();
				assertTrue(Analyzer.instance().getCurrentLockCount() == 0);
			}
		});
		assertTrue(Analyzer.instance().getCurrentLockCount() == 0);
		
		final CustomLock lock2 = new CustomLock();
		assertTrue(Analyzer.instance().getCurrentLockCount() == 0);
		
		fork(new Runnable() {
			@Override
			public void run() {
				assertTrue(Analyzer.instance().getCurrentLockCount() == 0);
				lock2.lock();
				assertTrue(Analyzer.instance().getCurrentLockCount() == 1);
			}
		});
		assertTrue(Analyzer.instance().getCurrentLockCount() == 0);
		lock2.unlock();
		assertTrue(Analyzer.instance().getCurrentLockCount() == 0);
		assertTrue(Analyzer.instance().getInternalErrorCount() == 0);
		assertNoConflict();
	}

	@Test
	public void testSignaling() {
		Analyzer.instance().setThrowsOnError(false);
		ConflictReporter reporter = new ConflictReporter();
		Analyzer.instance().setListener(reporter);
		
		final CustomSignal signal = new CustomSignal();
		signal.signal_wait();
		assertTrue(Analyzer.instance().getCurrentLockCount() == 0);
		
		fork(new Runnable() {
			@Override
			public void run() {
				assertTrue(Analyzer.instance().getCurrentLockCount() == 0);
				signal.signal_notify();
				assertTrue(Analyzer.instance().getCurrentLockCount() == 0);
			}
		});
		assertTrue(Analyzer.instance().getCurrentLockCount() == 0);
		assertTrue(reporter.hasNoErrors());
	}
		
	@Test
	public void testSignalingWithLocks() {
		Analyzer.instance().setThrowsOnError(false);

		final CustomSignal signal = new CustomSignal();
		Object lock1 = new Object();
		final Object lock2 = new Object();
		synchronized(lock1) {
			signal.signal_wait();
		}
		
		fork(new Runnable() {
			@Override
			public void run() {
				synchronized(lock2) {
					signal.signal_notify();
				}
			}
		});
		assertNoConflict();
	}
	
	@Test
	public void testSignalingWithLocks2() {
		Analyzer.instance().setThrowsOnError(false);

		final CustomSignal signal = new CustomSignal();
		Object lock1 = new Object();
		synchronized(lock1) {
			signal.signal_wait();
		}
		
		synchronized(lock1) {
			signal.signal_notify();
		}
		assertNoConflict();
	}

	@Test
	public void testSignalingWithSameLock() {
		Analyzer.instance().setThrowsOnError(false);

		final CustomSignal signal = new CustomSignal();
		Object lock1 = new Object();
		final Object lock2 = new Object();
		final Object lock3 = new Object();
		synchronized(lock3) {
			synchronized(lock1) {
				signal.signal_wait();
			}
		}
		
		fork(new Runnable() {
			@Override
			public void run() {
				synchronized(lock3) {
					synchronized(lock2) {
						signal.signal_notify();
					}
				}
			}
		});
		assertConflictSignalError();
	}
	
	@Test
	public void testSignalingWithSameLock2() {
		Analyzer.instance().setThrowsOnError(false);

		final CustomSignal signal = new CustomSignal();
		final Object lock1 = new Object();
		final Object lock2 = new Object();
		final Object lock3 = new Object();
		synchronized(lock3) {
			synchronized(lock1) {
				signal.signal_wait();
			}
		}
		
		fork(new Runnable() {
			@Override
			public void run() {
				synchronized(lock1) {
					synchronized(lock2) {
						signal.signal_notify();
					}
				}
			}
		});
		assertConflictSignalError();
	}
	
	@Test
	public void testSignalingWithSameLock3() {
		Analyzer.instance().setThrowsOnError(false);

		final CustomSignal signal = new CustomSignal();
		final Object lock1 = new Object();
		final Object lock2 = new Object();
		final Object lock3 = new Object();
		synchronized(lock3) {
			synchronized(lock1) {
				signal.signal_wait();
			}
		}
		
		fork(new Runnable() {
			@Override
			public void run() {
				synchronized(lock2) {
					synchronized(lock1) {
						signal.signal_notify();
					}
				}
			}
		});
		assertConflictSignalError();
	}

	@Test
	public void testSignalingWithDeferredAcquisition() {
		Analyzer.instance().setThrowsOnError(false);
		ConflictReporter reporter = new ConflictReporter();
		Analyzer.instance().setListener(reporter);

		final CustomSignal signal = new CustomSignal();
		final Object lock1 = new Object();
		synchronized(lock1) {
			signal.signal_wait();
		}
		
		fork(new Runnable() {
			@Override
			public void run() {
				signal.signal_notify();
			}
		});
		assertFalse(reporter.gotErrorSignal);
		
		// second acquisition
		signal.signal_wait();

		fork(new Runnable() {
			@Override
			public void run() {
				synchronized(lock1) {
					signal.signal_notify();
				}
			}
		});
		assertTrue(reporter.gotErrorSignal);
		assertTrue(reporter.conflict == signal);
		assertTrue(reporter.lock == signal);
		assertTrue(reporter.precedent == lock1);
		assertTrue(reporter.conflictPrecedent == lock1);
	}

	@Test
	public void testPerformance() {
		long time = System.currentTimeMillis();
		long count = 0;
		for (int i = 0; i < 100000; i++) {
			String lock = new String("lock_" + i);
			synchronized(lock) {
				count += System.currentTimeMillis();
			}
		}
		System.out.println("time for " + (System.currentTimeMillis() - time) + ", count:" + count);
	}
}


class Property {
	static final public String DEFAULT_VALUE = "some default";
	HashMap<String, String> map = new HashMap<String, String>();
	
  synchronized void set(String key, String value) {
	  synchronized(map) {
		  map.put(key, value);
	  }
  }

  String get(String key) {
	  synchronized(map) {
		  String value = map.get(key);
		  if (value == null) {
			  value = DEFAULT_VALUE;
			  set(key, value);
		  }
		  return value;
	  }
  }
}