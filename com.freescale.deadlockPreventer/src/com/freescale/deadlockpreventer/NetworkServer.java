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
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.ArrayList;

public class NetworkServer {
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
	
	IListener listener = new ConsoleListener();
	ServerSocket server;
	ArrayList<Socket> sockets = new ArrayList<Socket>();
	int actualPort = 0;

	private final class ClientConnection implements Runnable {
		private final Socket socket;

		private ClientConnection(Socket socket) {
			this.socket = socket;
		}

		@Override
		public void run() {
			DataInputStream input;
		    PrintStream output;
		    try {
				input = new DataInputStream(socket.getInputStream());
				output = new PrintStream(socket.getOutputStream());
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
						message.sendError(output, e.toString());
					}
				}
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}
		
    public int getListeningPort() {
    	return server.getLocalPort();
    }

	public Thread start(int port) {
		try {
	       server  = new ServerSocket(port);
		}
		catch (IOException e) {
		   System.out.println(e);
		   return null;
		}
		Thread thread = new Thread(new Runnable() {
			@Override
			public void run() {
				while (true) {
					try {
						final Socket socket = server.accept();
						sockets.add(socket);
						Thread clientThread = new Thread(new ClientConnection(socket));
						clientThread.start();
					} catch (SocketException e) {
						break;
					} catch (IOException e) {
						e.printStackTrace();
					}
				}
			}
		});
		thread.start();
		return thread;
    }
	
	public void stop() {
		for (Socket socket : sockets) {
			try {
				socket.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		sockets.clear();
		try {
			server.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
		server = null;
	}
	
	public static void main(String[] args) {
		int port = NetworkClientListener.DEFAULT_PORT;
		if (args.length > 0)
			port = Integer.parseInt(args[0]);
		System.out.println("Starting server on port: " + port);
		NetworkServer networkServer = new NetworkServer();
		Thread thread = networkServer.start(port);
		if (thread != null) {
			try {
				thread.join();
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
	}

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
}
