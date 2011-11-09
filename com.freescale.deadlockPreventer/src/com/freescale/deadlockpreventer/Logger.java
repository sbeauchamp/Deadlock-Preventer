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
import java.io.PrintWriter;
import java.io.Writer;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;

import com.freescale.deadlockpreventer.QueryService.IBundleInfo;

public class Logger {

	private boolean abortOnErrors = false;
	private boolean reportRecurentConflicts = false;
	private ArrayList<ConflictReport> alreadyReportedConflicts = new ArrayList<ConflictReport>();
	private String debugException = IllegalMonitorStateException.class
			.getCanonicalName();
	private Class<?> classToThrow = OrderingException.class;
	private IConflictListener listener = new DefaultListener();

	public class DefaultListener implements IConflictListener {
		@Override
		public int report(int type, String threadID, String conflictThreadID,
				Object lock, StackTraceElement[] lockStack, Object precedent,
				StackTraceElement[] precedentStack, Object conflict,
				StackTraceElement[] conflictStack, Object conflictPrecedent,
				StackTraceElement[] conflictPrecedentStack, String message) {
			if (abortOnErrors && ((type & Analyzer.TYPE_ERROR) != 0))
				return IConflictListener.ABORT;
			return IConflictListener.CONTINUE
					| IConflictListener.LOG_TO_CONSOLE;
		}
	}

	public Logger() {
		String value = System.getProperty(Settings.REPORT_SERVICE);
		if (value != null)
			listener = new NetworkClientListener(value);

		value = System.getProperty(Settings.QUERY_SERVICE);
		if (value != null) {
			QueryService.Client client = new QueryService.Client(value);
			client.start();
		}

		value = System.getProperty(Settings.LOG_TO_FILE);
		if (value != null)
			listener = new FileListener(value);

		value = System.getProperty(Settings.LOG_TO_STREAM);
		if (value != null)
			listener = new StdListener(value.equals("out") ? System.out
					: System.err);

		reportRecurentConflicts = Boolean
				.getBoolean(Settings.REPORT_RECURENT_CONFLICTS);
		abortOnErrors = Boolean.getBoolean(Settings.ABORT_ON_ERRORS);

		value = System.getProperty(Settings.THROWING_CLASS);
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

	public void setListener(IConflictListener listener) {
		if (listener == null)
			listener = new DefaultListener();
		this.listener = listener;
	}

	public boolean reportConflict(int type, String threadID,
			String conflictThreadID, LockInfo info, LockInfo precedent,
			LockInfo conflictLock, LockInfo conflictPrecedent) {
		if (!reportRecurentConflicts) {
			synchronized (alreadyReportedConflicts) {
				ConflictReport report = new ConflictReport(threadID,
						conflictThreadID, info != null ? info.getLock() : null,
						precedent != null ? precedent.getLock() : null,
						conflictLock != null ? conflictLock.getLock() : null,
						conflictPrecedent != null ? conflictPrecedent.getLock()
								: null);
				if (alreadyReportedConflicts.contains(report))
					return false;
				alreadyReportedConflicts.add(report);
			}
		}
		boolean shouldThrow = false;
		StringBuffer buffer = new StringBuffer();
		buffer.append(getPrintOutHeader() + getMessageHeader(type, threadID)
				+ "\n");
		String indent = "  ";

		printMergedStacks(buffer, info, precedent, indent);
		buffer.append("\nand thread: " + conflictThreadID + "\n");
		printMergedStacks(buffer, conflictPrecedent, conflictLock, indent);

		int result = listener
				.report(type,
						threadID,
						conflictThreadID,
						CustomLock.getExternal(info.getLock()),
						info.stackTrace,
						CustomLock.getExternal(precedent.getLock()),
						precedent.stackTrace,
						conflictLock != null ? CustomLock
								.getExternal(conflictLock.getLock()) : null,
						conflictLock != null ? conflictLock.stackTrace : null,
						conflictPrecedent != null ? CustomLock
								.getExternal(conflictPrecedent.getLock())
								: null,
						conflictPrecedent != null ? conflictPrecedent.stackTrace
								: null, buffer.toString());

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

	/* The stack traces collected stack inside the Analyzer code, so in order
	 * to show only the relevant stack traces to the user, we must skip the first
	 * 3 stack trace elements, as shown below: 
		  java.lang.Thread.getStackTrace(Thread.java:1436)
		  com.freescale.deadlockpreventer.Analyzer.enterLock_(Analyzer.java:267)
		  com.freescale.deadlockpreventer.Analyzer.enterLock(Analyzer.java:242)
	 * 
	 */
	public static final int FIRST_STACK_TRACE_ELEMENT = 3;
	
	
	private void printMergedStacks(StringBuffer buffer, LockInfo lock,
			LockInfo precedent, String indent) {
		
		StackTraceElement[] elements = lock.stackTrace;
		StackTraceElement[] precedentElements = precedent.stackTrace;

		if (elements != null && elements.length > FIRST_STACK_TRACE_ELEMENT
				&& precedentElements != null && precedentElements.length > FIRST_STACK_TRACE_ELEMENT) {
			buffer.append(indent + elements[FIRST_STACK_TRACE_ELEMENT].toString() + "     <--  acquiring: "
					+ Util.getUniqueIdentifier(lock.getLock()));

			// Find how many elements are common to both stack traces.
			// If the lock stack trace is:
			// A
			// B
			// C
			// And the precedent stack trace is:
			// D
			// B
			// C
			// then the number of common stack trace elements is 2.

			int commonStackTraceElements = 0;
			int maximumCommonStackTraceElements = Math.min(elements.length - FIRST_STACK_TRACE_ELEMENT,
					precedentElements.length - FIRST_STACK_TRACE_ELEMENT);

			for (; commonStackTraceElements < maximumCommonStackTraceElements; commonStackTraceElements++) {
				String lastLine = elements[elements.length
						- commonStackTraceElements - 1].toString();
				String precedentLastLine = precedentElements[precedentElements.length
						- commonStackTraceElements - 1].toString();
				if (!lastLine.equals(precedentLastLine))
					break;
			}

			for (int i = FIRST_STACK_TRACE_ELEMENT + 1; i < (elements.length - maximumCommonStackTraceElements); i++) {
				buffer.append("\n" + indent + elements[i].toString());
			}

			buffer.append("\n" + indent + precedentElements[FIRST_STACK_TRACE_ELEMENT].toString()
					+ "     <--  acquiring: "
					+ Util.getUniqueIdentifier(precedent.getLock()));

			for (int i = FIRST_STACK_TRACE_ELEMENT + 1; i < precedentElements.length; i++) {
				buffer.append("\n" + indent + precedentElements[i].toString());
			}
		}
	}

	static String getPrintOutHeader() {
		return "***DEADLOCK PREVENTER*** ";
	}

	private String getMessageHeader(int type, String threadId) {
		String msg = "ERROR";
		switch (type) {
		case Analyzer.TYPE_ERROR:
			msg = "ERROR: Inconsistent locking order while acquiring locks in thread : "
					+ threadId;
			break;
		case Analyzer.TYPE_WARNING:
			msg = "WARNING: Inconsistent locking order while acquiring locks in thread  "
					+ threadId;
			break;
		case Analyzer.TYPE_ERROR_SIGNAL:
			msg = "ERROR: Inconsistent messaging order while signaling objects in thread  "
					+ threadId;
			break;
		}

		return msg;
	}

	private String getMessageHeader(int type, LockInfo info) {
		String msg = "ERROR";
		switch (type) {
		case Analyzer.TYPE_ERROR:
			msg = "ERROR: Inconsistent locking order while acquiring lock: "
					+ Util.getUniqueIdentifier(info.getLock());
			break;
		case Analyzer.TYPE_WARNING:
			msg = "WARNING: Inconsistent locking order while acquiring lock: "
					+ Util.getUniqueIdentifier(info.getLock());
			break;
		case Analyzer.TYPE_ERROR_SIGNAL:
			msg = "ERROR: Inconsistent messaging order while signaling objects: "
					+ Util.getUniqueIdentifier(info.getLock());
			break;
		}

		return msg;
	}

	public static void dumpLockInformation(String file) {
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
				writer.write("lock: " + lock.getID() + ", precedents("
						+ lock.getPrecedents().length + ") followers("
						+ lock.getFollowers().length + ")\n");
				writeStack(writer, "  ", lock.getStackTrace());
				if (lock.getPrecedents().length > 0) {
					writer.write("  precedents:\n");
					for (IContext context : lock.getPrecedents()) {
						writer.write("    " + context.getLock().getID()
								+ ", thread id(" + context.getThreadID()
								+ ")\n");
						writeStack(writer, "     ", context.getStackTrace());
					}
				}
				if (lock.getFollowers().length > 0) {
					writer.write("  followers:\n");
					for (IContext context : lock.getFollowers()) {
						writer.write("    " + context.getLock().getID()
								+ ", thread id(" + context.getThreadID()
								+ ")\n");
						writeStack(writer, "     ", context.getStackTrace());
					}
				}
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private static void writeStack(Writer writer, String prefix,
			String[] stackTrace) throws IOException {
		for (String stack : stackTrace) {
			writer.write(prefix + stack + "\n");
		}
	}

	public void throwException(int type, LockInfo info) {
		String msg = Logger.getPrintOutHeader() + getMessageHeader(type, info);
		try {
			try {
				Constructor<?> constr = classToThrow
						.getConstructor(String.class);
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

	public static void printStrackTrace(StackTraceElement[] stackTrace) {
		StringBuffer buffer = new StringBuffer();
		printStrackTrace(buffer, new String(), stackTrace, 0);
		System.out.print(buffer.toString());
	}

	public static void printStrackTrace(StringBuffer buffer, String header,
			StackTraceElement[] stackTrace, int startOffset) {
		for (int i = startOffset; i < stackTrace.length; i++)
			buffer.append(header + stackTrace[i].toString() + "\n");
	}

	public static void dumpBundleInfo(IBundleInfo info, PrintWriter writer) {
		try {
			writer.write("bundle info: " + info.getName());
			writeStack(writer, "  ", info.getPackages());
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
}
