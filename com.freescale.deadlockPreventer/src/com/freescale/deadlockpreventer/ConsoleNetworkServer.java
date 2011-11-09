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

import java.io.BufferedReader;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedList;

import com.freescale.deadlockpreventer.NetworkServer.Session;
import com.freescale.deadlockpreventer.QueryService.IBundleInfo;
import com.freescale.deadlockpreventer.QueryService.ITransaction;
import com.freescale.deadlockpreventer.ReportService.IListener;

@SuppressWarnings("unused")
public class ConsoleNetworkServer {
	
	static QueryService queryService;
	
	public static void main(String[] args) {
		int port = NetworkServer.DEFAULT_PORT;
		if (args.length > 0)
			port = Integer.parseInt(args[0]);
		NetworkServer networkServer = new NetworkServer();
		
		queryService = new QueryService() {
			public void handle(Session session) {
				System.out.println("New session started for: " + session.getKey());
				super.handle(session);
			}
		};
		String queryKey = networkServer.createNewSessionKey(QueryService.ID);
		networkServer.registerSevice(queryKey, queryService);
		
		String reportKey = networkServer.createNewSessionKey(ReportService.ID);
		ReportService reportService = new ReportService() {
			public void handle(Session session) {
				System.out.println("New session started for: " + session.getKey());
				super.handle(session);
			}
		};
		reportService.setListener(new IListener() {

			@Override
			public int report(String type, String threadID,
					String conflictThreadID, String lock, String[] lockStack,
					String precedent, String[] precedentStack, String conflict,
					String[] conflictStack, String conflictPrecedent,
					String[] conflictPrecedentStack, String message) {
				System.out.println(message);
				System.out.println("(C)ontinue, (E)xception, (A)bort?");
				if (alwaysContinue) {
					System.out.println(" -> always (C)ontinue");
					return IConflictListener.CONTINUE;
				}
				char c = readLine().charAt(0);
				if (Character.toLowerCase(c) == 'c')
					return IConflictListener.CONTINUE;
				if (Character.toLowerCase(c) == 'e')
					return IConflictListener.EXCEPTION;
				if (Character.toLowerCase(c) == 'a')
					return IConflictListener.ABORT;
				return IConflictListener.CONTINUE;
			} 
			
		});
		networkServer.registerSevice(reportKey, reportService);
		
		Thread thread = networkServer.start(port);
		startReadLine();
		
		System.out.println("Server started on port: " + port);
		System.out.println("services:");
		System.out.println("  report: " + reportKey);
		System.out.println("  query: " + queryKey);
		System.out.println("Type 'help' for a list of commands.");
		String line = readLine();
		while (line != null) {
			try {
				if (processLine(line) != 0)
					break;
			} catch (Throwable e) {
				e.printStackTrace();
			}
			line = readLine();
		}
		networkServer.stop();
		exiting = true;
		if (thread != null) {
			try {
				thread.join();
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
	}

	static LinkedList<Object> waitingList = new LinkedList<Object>();
	static volatile boolean exiting = false;
	static LinkedList<String> readLines = new LinkedList<String>();
	
	private static String readLine() {
		synchronized(readLines) {
			if (!readLines.isEmpty())
				return readLines.removeFirst();
		}

		Object wait = new Object();
		synchronized(waitingList) {
			waitingList.addFirst(wait);
		}
		try {
			synchronized(wait) {
				wait.wait();
			}
		} catch (InterruptedException e) {
		}
		synchronized(readLines) {
			return readLines.removeFirst();
		}
	}
	
	private static void startReadLine() {
		Thread thread = new Thread(new Runnable() {
			@Override
			public void run() {
				String line = null;
				BufferedReader stream = new BufferedReader(new InputStreamReader(System.in));
				try {
					while(!exiting) {
						line = stream.readLine();
						synchronized(readLines) {
							readLines.add(line);
						}
						Object wait = null;
						synchronized(waitingList) {
							if (!waitingList.isEmpty())
								wait = waitingList.removeFirst();
						}
						if (wait != null) {
							synchronized(wait) {
								wait.notify();
							}
						}
					}
				} catch (IOException e1) {
					e1.printStackTrace();
				}
			}
		});
		thread.start();
	}

	private static int processLine(String line) {
		String[] lines = line.split(" ");
		try {
			Method method = ConsoleNetworkServer.class.getDeclaredMethod("command_" + lines[0], String[].class);
			Object result = method.invoke(null, new Object[] {lines});
			if (result instanceof Integer)
				return ((Integer) result).intValue();
		} catch (NoSuchMethodException e) {
			System.out.println("Command not recognized: " + line);
		} catch (IllegalAccessException e) {
			e.printStackTrace();
		} catch (IllegalArgumentException e) {
			e.printStackTrace();
		} catch (InvocationTargetException e) {
			System.out.println("Command not recognized: " + line);
		}
		return 0;
	}

	private static int command_exit(String[] args) {
		return -1;
	}

	static boolean alwaysContinue = false;
	
	private static int command_alwaysContinue(String[] args) {
		alwaysContinue = !alwaysContinue;
		System.out.println(Boolean.toString(alwaysContinue));
		return 0;
	}

	private static int command_help(String[] args) {
		Method[] methods = ConsoleNetworkServer.class.getDeclaredMethods();
		for (Method method : methods) {
			if (method.getName().startsWith("command_")) {
				System.out.println(method.getName().split("_")[1]);
			}
		}
		return 0;
	}

	static ITransaction transaction = null;

	private static int command_getLockCount(String[] args) {
		if (!waitForConnection())
			return 0;
		
		if (transaction != null)
			transaction.close();
		transaction = queryService.createTransaction();
		System.out.println(transaction.getLockCount() + " locks");
		return 0;
	}

	private static int command_getLock(String[] args) {
		if (!waitForConnection())
			return 0;

		if (transaction == null)
			transaction = queryService.createTransaction();
		int index = Integer.parseInt(args[1]);
		ILock lock = transaction.getLocks(index, index + 1)[0];
		if (lock == null)
			System.out.println("lock index(" + index + ") not found");
		else {
			PrintWriter writer = new PrintWriter(System.out);
			Logger.dumpLockInformation(new ILock[] {lock}, writer);
			writer.flush();
		}
		return 0;
	}

	private static int command_newTransaction(String[] args) {
		if (!waitForConnection())
			return 0;
		if (transaction != null)
			transaction.close();
		transaction = queryService.createTransaction();
		System.out.println("Transaction created: " + transaction.toString());
		return 0;
	}
	
	private static int command_getDetails(String[] args) {
		if (!waitForConnection())
			return 0;

		if (transaction == null)
			transaction = queryService.createTransaction();
		int index = Integer.parseInt(args[1]);
		int end = index + 1;
		if (args.length > 2)
			end = Integer.parseInt(args[2]);
		ILock[] locks = transaction.getLocks(index, end);
		if (locks == null)
			System.out.println("lock index(" + index + ") not found");
		else {
			PrintWriter writer = new PrintWriter(System.out);
			Logger.dumpLockInformation(locks, writer);
			writer.flush();
		}
		return 0;
	}

	private static int command_getBundleInfo(String[] args) {
		if (!waitForConnection())
			return 0;

		if (transaction == null)
			transaction = queryService.createTransaction();
		int index = Integer.parseInt(args[1]);
		int end = index + 1;
		ILock[] locks = transaction.getLocks(index, end);
		if (locks == null)
			System.out.println("lock index(" + index + ") not found");
		else {
			IBundleInfo info = transaction.getBundleInfo(locks[0]);
			PrintWriter writer = new PrintWriter(System.out);
			Logger.dumpBundleInfo(info, writer);
			writer.flush();
		}
		return 0;
	}

	static int DEFAULT_PACKET_SIZE = 100;

	private static int command_getAll(String[] args) {
		if (!waitForConnection())
			return 0;

		if (transaction == null)
			transaction = queryService.createTransaction();
		int max = transaction.getLockCount();
		int index = 0;
		System.out.println("Listing all locks: ('q' for cancel)...");
		while (index < max) {
			ILock[] locks = transaction.getLocks(index, Math.min(index + DEFAULT_PACKET_SIZE, max));
			if (locks == null)
				System.out.println("lock index(" + index + ") not found");
			else {
				PrintWriter writer = new PrintWriter(System.out);
				Logger.dumpLockInformation(locks, writer);
				writer.flush();
			}
			index += DEFAULT_PACKET_SIZE;
		}
		System.out.println("done.");
		return 0;
	}

	private static int command_writeAll(String[] args) {
		if (!waitForConnection())
			return 0;

		if (transaction == null)
			transaction = queryService.createTransaction();
		int max = transaction.getLockCount();
		int index = 0;
		System.out.println("Writing all locks...");

		String file = args[1];

		long time = System.currentTimeMillis();
		File outputFile = new File(file);
		if (!outputFile.getParentFile().exists())
			outputFile.getParentFile().mkdirs();
		try {
			if (!outputFile.exists())
				outputFile.createNewFile();
			FileWriter writer = new FileWriter(outputFile);

			while (index < max) {
				System.out.println("fetching index " + index + " ...");
				ILock[] locks = transaction.getLocks(index, Math.min(index + DEFAULT_PACKET_SIZE, max));
				if (locks == null)
					System.out.println("lock index(" + index + ") not found");
				else {
					Logger.dumpLockInformation(locks, writer);
				}
				index += DEFAULT_PACKET_SIZE;
			}
			writer.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
		System.out.println("done in " + ((System.currentTimeMillis() - time) / 1000.0) + " seconds.");
		return 0;
	}

	private static boolean waitForConnection() {
		while (!queryService.isConnected()) {
			System.out.println("Waiting for connection...");
			try {
				Thread.sleep(500);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
		if (queryService.isClosed()) {
			System.out.println("Connection is closed");
			return false;
		}
		return true;
	}
}
