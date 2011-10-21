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

import java.io.DataInputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.net.SocketException;

import com.freescale.deadlockpreventer.NetworkServer.Session;

public class ReportService implements NetworkServer.IService {
	
	public static String ID = "report";
	
	public interface IListener {
		public int report(String type, String threadID, String conflictThreadID, 
				String lock, String[] lockStack, 
				String precedent, String[] precedentStack,
				String conflict, String[] conflictStack,
				String conflictPrecedent, String[] conflictPrecedentStack, String message);
	}

	public void setListener(IListener listener) {
		this.listener = listener;
	}
	
	private final class ClientConnection implements Runnable {
		private final NetworkServer.Session session;

		private ClientConnection(NetworkServer.Session session) {
			this.session = session;
		}

		@Override
		public void run() {
			DataInputStream input = session.getInput();
		    PrintStream output = session.getOutput();
			NetworkClientListener.Message message = new NetworkClientListener.Message();
			while(true) {
				try {
					message.readMessage(input);
					int result = listener.report(message.getType(), 
							message.getThreadID(), 
							message.getConflictThreadID(),
							message.getLock(),
							message.getLockStack(),
							message.getPrecedent(),
							message.getPrecedentStack(),
							message.getConflict(),
							message.getConflictStack(),
							message.getConflictPrecedent(),
							message.getConflictPrecedentStack(),
							message.getMessage());
					message.sendResponse(output, result);
				} catch (SocketException e) {
					break;
				} catch (IOException e) {
					try {
						message.sendError(output, e.toString());
					} catch (IOException e1) {
						e1.printStackTrace();
					}
				}
			}
		}
	}

	IListener listener = new ConsoleListener();

	public class ConsoleListener implements IListener {

		public int report(String type, String threadID,
				String conflictThreadID, String lock, String[] lockStack,
				String precedent, String[] precedentStack, String conflict,
				String[] conflictStack, String conflictPrecedent,
				String[] conflictPrecedentStack, String message) {
			System.out.println(message);
			try {
				while (true) {
					System.out.println("(c)ontinue? (a)bort? (e)xception? (d)ebug?");
					int c = System.in.read();
					boolean wrongEntry = false;
					while (c != -1 && !wrongEntry) {
						switch((char) c) {
						case 'c':
						case 'C':
							return IConflictListener.CONTINUE;
						case 'a':
						case 'A':
							return IConflictListener.ABORT;
						case 'e':
						case 'E':
							return IConflictListener.EXCEPTION;
						case 'd':
						case 'D':
							return IConflictListener.DEBUG;
						case '\n':
						case '\r':
							break;
						default:
							wrongEntry = true;
							break;
						}
						c = System.in.read();
					}
				}
			} catch (IOException e) {
				e.printStackTrace();
			}
			return 0;
		}
	}

	@Override
	public void handle(Session session) {
		Thread clientThread = new Thread(new ClientConnection(session));
		clientThread.start();
	}

	@Override
	public void close() {
		
	}
}
