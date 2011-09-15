package com.freescale.deadlockpreventer;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;

public class Logger {
	
	private boolean abortOnErrors = false;
	private boolean reportRecurentConflicts = false;
	private ArrayList<ConflictReport> alreadyReportedConflicts = new ArrayList<ConflictReport>();
	private String debugException = IllegalMonitorStateException.class.getCanonicalName();
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
			return IConflictListener.CONTINUE | IConflictListener.LOG_TO_CONSOLE;
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
			listener = new StdListener(value.equals("out")? System.out:System.err);

		reportRecurentConflicts = Boolean.getBoolean(Settings.REPORT_RECURENT_CONFLICTS);
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

	public boolean reportConflict(int type, String threadID, String conflictThreadID, LockInfo info, LockInfo precedent,
			LockInfo conflictLock, LockInfo conflictPrecedent) {
		if (!reportRecurentConflicts) {
			synchronized(alreadyReportedConflicts) {
				ConflictReport report = new ConflictReport(threadID, 
						conflictThreadID, 
						info != null ? info.getLock() : null, 
								precedent != null ? precedent.getLock() : null, 
										conflictLock != null ? conflictLock.getLock() : null, 
												conflictPrecedent != null ? conflictPrecedent.getLock() : null);
				if (alreadyReportedConflicts.contains(report))
					return false;
				alreadyReportedConflicts.add(report);
			}
		}
		boolean shouldThrow = false;
		StringBuffer buffer = new StringBuffer();
		buffer.append(getPrintOutHeader() + getMessageHeader(type, info.getLock()) + " in thread: " + threadID + "\n");
		String indent = "  ";
		if (type == Analyzer.TYPE_ERROR_SIGNAL) {
			info.print(buffer, getEmptyPrintOutHeader() + indent);
			
			buffer.append("\n");
			buffer.append(getEmptyPrintOutHeader() + "While holding lock: " + Util.safeToString(conflictPrecedent.getLock())  + "\n");
			conflictPrecedent.print(buffer, getEmptyPrintOutHeader() + indent);

			if (conflictLock != null) {
				buffer.append("\n");
				buffer.append(getEmptyPrintOutHeader() + "Previously acquired signal: " + Util.safeToString(conflictLock.getLock())  + "\n");
				conflictLock.print(buffer, getEmptyPrintOutHeader() + indent);
			}

			buffer.append("\n");
			buffer.append(getEmptyPrintOutHeader() + "Previously held lock: " + Util.safeToString(precedent.getLock())  + " in thread: " + conflictThreadID  + "\n");
			precedent.print(buffer, getEmptyPrintOutHeader() + indent);
		}
		else {
			info.print(buffer, getEmptyPrintOutHeader() + indent);
			buffer.append("\n");
			buffer.append(getEmptyPrintOutHeader() + "with predecent : " + Util.safeToString(precedent.getLock()) + "\n");
			precedent.print(buffer, getEmptyPrintOutHeader() + indent);
			
			buffer.append("\n");
			buffer.append(getEmptyPrintOutHeader() + "Previously acquired lock: " + Util.safeToString(conflictLock.getLock())  + " in thread: " + conflictThreadID  + "\n");
			conflictLock.print(buffer, getEmptyPrintOutHeader() + indent);

			buffer.append("\n");
			buffer.append(getEmptyPrintOutHeader() + "Previously acquired precedent: " + Util.safeToString(conflictPrecedent.getLock())  + "\n");
			conflictPrecedent.print(buffer, getEmptyPrintOutHeader() + indent);
		}

		int result = listener.report(type, 
				threadID, 
				conflictThreadID, 
				CustomLock.getExternal(info.getLock()), 
				info.stackTrace, 
				CustomLock.getExternal(precedent.getLock()), 
				precedent.stackTrace, 
				conflictLock != null ? CustomLock.getExternal(conflictLock.getLock()) : null, 
				conflictLock != null ? conflictLock.stackTrace : null, 
				conflictPrecedent != null ? CustomLock.getExternal(conflictPrecedent.getLock()) : null, 
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

	static String getPrintOutHeader() {
		return "***DEADLOCK PREVENTER*** ";
	}

	private String getMessageHeader(int type, Object lock) {
		String msg = "ERROR";
		switch (type)
		{
		case Analyzer.TYPE_ERROR:
			msg = "ERROR: Inconsistent locking order while acquiring lock: " + Util.safeToString(lock);;
			break;
		case Analyzer.TYPE_WARNING:
			msg = "WARNING: Inconsistent locking order while acquiring lock: " + Util.safeToString(lock);;
			break;
		case Analyzer.TYPE_ERROR_SIGNAL:
			msg = "ERROR: Inconsistent messaging order while signaling object: " + Util.safeToString(lock);;
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
				writer.write("lock: " + lock.getID() + ", precedents(" + lock.getPrecedents().length + ") followers(" + lock.getFollowers().length + ")\n");
				writeStack(writer, "  ", lock.getStackTrace());
				if (lock.getPrecedents().length > 0) {
					writer.write("  precedents:\n");
					for (IContext context : lock.getPrecedents()) {
						writer.write("    " + context.getLock().getID() + ", thread id(" + context.getThreadID() + ")\n");
						writeStack(writer, "     ", context.getStackTrace());
					}
				}
				if (lock.getFollowers().length > 0) {
					writer.write("  followers:\n");
					for (IContext context : lock.getFollowers()) {
						writer.write("    " + context.getLock().getID() + ", thread id(" + context.getThreadID() + ")\n");
						writeStack(writer, "     ", context.getStackTrace());
					}
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

	public void throwException(int type, LockInfo info) {
		String msg = Logger.getPrintOutHeader() + getMessageHeader(type, info.getLock());
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
	
	public static void printStrackTrace(StringBuffer buffer, String header, StackTraceElement[] stackTrace, int startOffset) {
		for (int i = startOffset; i < stackTrace.length; i++)
			buffer.append(header + stackTrace[i].toString() + "\n");
	}
}
