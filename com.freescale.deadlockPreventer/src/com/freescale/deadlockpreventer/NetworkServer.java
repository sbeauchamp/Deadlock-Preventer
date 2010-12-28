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
import java.net.UnknownHostException;
import java.util.HashMap;

public class NetworkServer {
	
	ServerSocket server;
	
	public interface IService {
		void handle(Session session);

		void close();
	}
	
	public static class Session {
		public Session(Socket socket) {
			this.socket = socket;
			try {
				input = new DataInputStream(socket.getInputStream());
				output = new PrintStream(socket.getOutputStream());
				key = NetworkUtil.readString(input);
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		
		public Session(Socket socket, String key) throws IOException {
			this.socket = socket;
			this.key = key;
			String result = "ERROR";
			input = new DataInputStream(socket.getInputStream());
			output = new PrintStream(socket.getOutputStream());
			NetworkUtil.writeString(output, key);
			result = NetworkUtil.readString(input);
			if (!result.equals("OK"))
				throw new IOException("Error connecting to network server: " + result);
		}

		public void close() {
			try {
				socket.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		
		public void ok() {
			try {
				NetworkUtil.writeString(output, "OK");
			} catch (IOException e) {
				e.printStackTrace();
			}
		}

		public void error() {
			try {
				NetworkUtil.writeString(output, "ERROR, NO SERVICE");
			} catch (IOException e) {
				e.printStackTrace();
			}
			close();
		}
		
		public String getKey() {
			return key;
		}

		public DataInputStream getInput() {
			return input;
		}

		public PrintStream getOutput() {
			return output;
		}

		private String key;
		private Socket socket;
		private DataInputStream input;
		private PrintStream output;

		public boolean isClosed() {
			return socket.isClosed();
		}
	}
	
	// the key is serviceID.sessionID
	HashMap<String, IService> services = new HashMap<String, IService>();
	
	int actualPort = 0;

	int latestSessionID = 0;

	public static int DEFAULT_PORT = 43537;
	
	public String createNewSessionKey(String serviceID) {
		int sessionID = createNewSessionID();
		return createKey(serviceID, sessionID);
	}
	
	private int createNewSessionID() {
		synchronized(this) {
			return ++latestSessionID;
		}
	}
	
    public int getListeningPort() {
    	return server.getLocalPort();
    }

    
    public NetworkServer() {
    }
    
    public void registerSevice(String sessionKey, IService service)
    {
    	services.put(sessionKey, service);
    }
    
	private String createKey(String serviceID, int sessionID) {
		return serviceID + "." + Integer.toHexString(sessionID);
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
						Session session = new Session(socket);
						IService service = services.get(session.getKey());
						if (service != null) {
							session.ok();
							service.handle(session);
						}
						else
							session.error();
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
	
	/**
	 * Creates a new socket to the server from a client
	 * @param value formatted address:port:session
	 * @return
	 * @throws IOException 
	 */
	public static Session connect(String value) throws IOException {
		Socket socket;
		String serverAddress = value;
		String[] args = serverAddress.split(":");
		int port = NetworkServer.DEFAULT_PORT;
		if (args.length > 1)
			port = Integer.parseInt(args[1]);
		try {
			socket = new Socket(args[0], port);
		} catch (UnknownHostException e) {
			throw new IOException(e.getMessage());
		}
		String key = "0.0";
		if (args.length > 2)
			key = args[2];
	    return new Session(socket, key);
	}
	
	public void stop() {
		for (IService service : services.values()) {
			service.close();
		}
		services.clear();
		try {
			server.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
		server = null;
	}
}
