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

public class QueryService implements NetworkServer.IService {

	public static String ID = "report";

	public static String VERSION_ID = "1.0.0";
		
	private static String QUERY_DUMP_LOCKS;
	
	public QueryService() {}
	
	public static class Client {
		NetworkServer.Session session;
		boolean connected = false;

	    public Client(String value) {
			try {
				session = NetworkServer.connect(value);
				connected = true;
			} catch (IOException e) {
				e.printStackTrace();
			}
	    }
	    
	    public void start() {
			Thread thread = new Thread(new Runnable() {
				@Override
				public void run() {
					Thread clientThread = new Thread(new ClientConnection(session));
					clientThread.start();
				}
			});
			thread.start();
	    }

	    public void stop() {
			session.close();
		}
	}
	
	ServerConnection connection = null;
	    
    public boolean isConnected() {
    	synchronized(this) {
    		return connection != null;
    	}
    }
    
    public ILock[] getLocks() {
    	Message msg = new Message(new String[] {QUERY_DUMP_LOCKS});
    	Message result = connection.request(msg);
    	return parseLocks(result.getStrings());
    }

    private static ILock[] parseLocks(String[] strings) {
		return new Statistics(strings).locks();
	}

	private static class ServerConnection {

		NetworkServer.Session session;
		
		public ServerConnection(NetworkServer.Session session) {
			this.session = session;
		}

		public void stop() {
			session.close();
		}

		public Message request(Message msg) {
			try {
				return msg.request(session.getOutput(), session.getInput());
			} catch (SocketException e) {
			} catch (IOException e) {
			}
			return null;
		}
	}

	private static class ClientConnection implements Runnable{

		NetworkServer.Session session;
		
		public ClientConnection(NetworkServer.Session session) {
			this.session = session;
		}

		@Override
		public void run() {
			DataInputStream input = session.getInput();
		    PrintStream output = session.getOutput();
		    Message message = new Message();
			while(true) {
				try {
					message.readMessage(input);
					Message result = processQuery(message);
					if (result != null) {
						message.sendOK(output);
						result.request(output, input);
					}
					else
						message.sendError(output, "error");
				} catch (SocketException e) {
					break;
				} catch (IOException e) {
					message.sendError(output, e.toString());
				}
			}
		}
	}

	public static class Message {
		public Message(String[] strings) {
			this.strings = strings;
		}

		public Message() {
		}

		public String[] getStrings() {
			return strings;
		}

		String[] strings;
		
		public Message request(PrintStream output, DataInputStream input) throws IOException {
			NetworkUtil.writeString(output, VERSION_ID);
			NetworkUtil.writeStringArray(output, strings);

			output.flush();
			String result = NetworkUtil.readString(input);
			if (!result.equals("OK"))
				throw new IOException("error returned from the server:" + result);
			Message content = new Message(NetworkUtil.readStringArray(input)); 
			return content;
		}

		public void readMessage(DataInputStream input) throws IOException {
			String version = NetworkUtil.readString(input);
			if (!version.equals(VERSION_ID)) {
				throw new IOException("version incompatible: server(" + VERSION_ID + ") , client(" + version + ")");
			}
			strings = NetworkUtil.readStringArray(input);
		}

		public void sendError(PrintStream output, String value) {
			NetworkUtil.writeString(output, value);
		}

		public void sendOK(PrintStream output) {
			NetworkUtil.writeString(output, "OK");
		}
	}

	public static Message processQuery(Message query) {
		String[] strs = query.getStrings();
		if (strs.length > 0) {
			if (strs[0].equals(QUERY_DUMP_LOCKS)) {
				return new Message(new Statistics().serialize());
			}
		}
		return null;
	}

	@Override
	public void handle(Session session) {
    	synchronized(this) {
			if (connection != null)
				connection.stop();
			connection = new ServerConnection(session);
    	}
	}

	@Override
	public void close() {
    	if (connection != null)
    		connection.stop();
	}
}
