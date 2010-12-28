package com.freescale.deadlockpreventer;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;

import com.freescale.deadlockpreventer.NetworkServer.Session;
import com.freescale.deadlockpreventer.ReportService.IListener;

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
				try {
					int c = System.in.read();
					if (Character.toLowerCase((char)c) == 'c')
						return IConflictListener.CONTINUE;
					if (Character.toLowerCase((char)c) == 'e')
						return IConflictListener.EXCEPTION;
					if (Character.toLowerCase((char)c) == 'a')
						return IConflictListener.ABORT;
				} catch (IOException e) {
					e.printStackTrace();
				}
				return IConflictListener.CONTINUE;
			} 
			
		});
		networkServer.registerSevice(reportKey, reportService);
		
		Thread thread = networkServer.start(port);
		
		System.out.println("Server started on port: " + port);
		System.out.println("services:");
		System.out.println("  report: " + reportKey);
		System.out.println("  query: " + queryKey);
		System.out.println("Type 'help' for a list of commands.");
		BufferedReader stream = new BufferedReader(new InputStreamReader(System.in));
		try {
			String line = stream.readLine();
			while (line != null) {
				if (processLine(line) != 0)
					break;
				line = stream.readLine();
			}
		} catch (IOException e1) {
			e1.printStackTrace();
		}
		networkServer.stop();
		if (thread != null) {
			try {
				thread.join();
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
	}

	private static int processLine(String line) {
		if (line.equals("exit"))
			return -1;
		else
		if (line.equals("help")) {
			System.out.println("exit              (exit the process)");
			System.out.println("dump              (dump all the locks from the connected process)");
		}
		else
		if (line.equals("dump")) {
			dumpLocks();
		}
		else
			System.out.println("Command not recognized: " + line);
		return 0;
	}

	private static void dumpLocks() {
		while (!queryService.isConnected()) {
			System.out.println("Waiting for connection ('c' for cancel)...");
			try {
				if (System.in.available() > 0) {
					int c = System.in.read();
					if (Character.toLowerCase(c) == 'c')
						return;
				}
			} catch (IOException e) {
				e.printStackTrace();
			}
			try {
				Thread.sleep(500);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
		
		if (queryService.isClosed()) {
			System.out.println("Connection is closed");
			return;
		}
		ILock[] locks = queryService.getLocks();
		System.out.println("Received " + locks.length + " locks:");
		Analyzer.dumpLockInformation(locks, new PrintWriter(System.out));
	}

}
