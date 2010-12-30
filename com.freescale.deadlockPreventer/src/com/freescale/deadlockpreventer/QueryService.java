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
import java.util.ArrayList;
import java.util.Arrays;

import com.freescale.deadlockpreventer.NetworkServer.Session;

public class QueryService implements NetworkServer.IService {

	public static String ID = "query";

	public static String VERSION_ID = "1.0.0";
		
	private static String DUMP_LOCKS = "dump";
	private static String INIT_TRANSACTION = "init_transaction";
	private static String GET_LOCK = "get_lock";
	private static String GET_LOCK_DETAIL = "get_detail";
	private static String CLOSE_TRANSACTION = "close_transaction";
	
	public QueryService() {}
	
	public static class Client {
		NetworkServer.Session session;
		boolean connected = false;
		int lastTrasactionID = 0;

		class Transaction {
			public Transaction(int i) {
				id = Integer.toString(i);
				stats = new Statistics(true);
				locks = stats.locks();
			}
			String id;
			Statistics stats;
			ILock[] locks;
		}
		
		ArrayList<Transaction> transactions = new ArrayList<QueryService.Client.Transaction>();
		
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

		private synchronized Message processQuery(Message query) {
			ArrayList<String> strs = query.getStrings();
			if (strs.size() > 0) {
				if (strs.get(0).equals(DUMP_LOCKS)) {
					return new Message(new Statistics().serialize());
				}
				if (strs.get(0).equals(INIT_TRANSACTION)) {
					Transaction transaction = new Transaction(++lastTrasactionID);
					transactions.add(transaction);
					return new Message(new String[] {transaction.id, Integer.toString(transaction.locks.length)});
				}
				if (strs.get(0).equals(CLOSE_TRANSACTION)) {
					String transactionID = strs.get(1);
					Transaction transaction = find(transactionID);
					if (transaction == null)
						return null;
					transactions.remove(transaction);
					return new Message();
				}
				if (strs.get(0).equals(GET_LOCK)) {
					String transactionID = strs.get(1);
					Transaction transaction = find(transactionID);
					if (transaction == null)
						return null;
					int index = Integer.parseInt(strs.get(2));
					if (index < 0 || index >= transaction.locks.length)
						return null;
					ILock lock = transaction.locks[index];
					return new Message(new Statistics(lock).serialize());
				}
				if (strs.get(0).equals(GET_LOCK_DETAIL)) {
					String transactionID = strs.get(1);
					Transaction transaction = find(transactionID);
					if (transaction == null)
						return null;
					int index = Integer.parseInt(strs.get(2));
					if (index < 0 || index >= transaction.locks.length)
						return null;
					int end = Integer.parseInt(strs.get(3));
					if (end < 0 || end > transaction.locks.length || end < index)
						return null;
					ILock[] locks = transaction.stats.getDetails(Arrays.copyOfRange(transaction.locks, index, end));
					if (locks == null)
						return null;
					return new Message(new Statistics(locks).serialize());
				}
			}
			return null;
		}

		private Transaction find(String transactionID) {
			for (Transaction trans : transactions)  {
				if (trans.id.equals(transactionID))
					return trans;
			}
			return null;
		}

		class ClientConnection implements Runnable{

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
							result.respond(output);
						}
						else
							message.sendError(output, "error");
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
	}
	
	ServerConnection connection = null;
	    
    public boolean isConnected() {
    	synchronized(this) {
    		return connection != null;
    	}
    }

    public boolean isClosed() {
    	synchronized(this) {
    		return connection != null && connection.isClosed();
    	}
    }

    /**
     * Get the complete list of locks.  Can be very slow for large programs.
     * @return the complete list of locks. 
     */
    public ILock[] getLocks() {
    	Message msg = new Message(new ArrayList<String>(Arrays.asList(new String[] {DUMP_LOCKS})));
    	Message result = connection.request(msg);
    	if (result == null)
    		return null;
    	return parseLocks(result.getStrings());
    }

    public ITransaction createTransaction() {
    	Message msg = new Message(new ArrayList<String>(Arrays.asList(new String[] {INIT_TRANSACTION})));
    	Message result = connection.request(msg);
    	if (result == null)
    		return null;
    	return new Transaction(result.getStrings());
    }

    public interface ITransaction {
    	
    	public int getLockCount();
    	public ILock getLockSummary(int index);
    	public ILock[] getLocks(int start, int end);
    	
    	public void close();
    }
    
    private class Transaction implements ITransaction {
    	
    	public Transaction(ArrayList<String> arrayList) {
    		transactionID = arrayList.get(0);
    		count = Integer.parseInt(arrayList.get(1));
		}

    	String transactionID;
		int count;
		
		@Override
		public int getLockCount() {
			return count;
		}

		@Override
		public ILock getLockSummary(int index) {
	    	Message msg = new Message(new ArrayList<String>(Arrays.asList(new String[] {GET_LOCK, transactionID, Integer.toString(index)})));
	    	Message result = connection.request(msg);
	    	if (result == null)
	    		return null;
	    	return parseLock(result.getStrings());
		}

		@Override
		public ILock[] getLocks(int start, int end) {
	    	Message msg = new Message(new ArrayList<String>(Arrays.asList(new String[] {GET_LOCK_DETAIL, transactionID, Integer.toString(start), Integer.toString(end)})));
	    	Message result = connection.request(msg);
	    	if (result == null)
	    		return null;
	    	return parseLocks(result.getStrings());
		}

		@Override
		public void close() {
	    	Message msg = new Message(new ArrayList<String>(Arrays.asList(new String[] {CLOSE_TRANSACTION, transactionID})));
	    	connection.request(msg);
		}
    }
    
    private static ILock[] parseLocks(ArrayList<String> strings) {
		return new Statistics(strings).locks();
	}

    private static ILock parseLock(ArrayList<String> strings) {
		return new Statistics(strings).lock();
	}

	private static class ServerConnection {

		NetworkServer.Session session;
		
		public ServerConnection(NetworkServer.Session session) {
			this.session = session;
		}

		public boolean isClosed() {
			return session.isClosed();
		}

		public void stop() {
			session.close();
		}

		public Message request(Message msg) {
			try {
				return msg.request(session.getOutput(), session.getInput());
			} catch (SocketException e) {
				// socket is closed
			} catch (IOException e) {
				e.printStackTrace();
			}
			return null;
		}
	}

	public static class Message {
		public Message(ArrayList<String> strings) {
			this.strings = strings;
		}

		public Message(String[] strings) {
			this.strings = new ArrayList<String>(Arrays.asList(strings));
		}

		public Message() {
		}

		public ArrayList<String> getStrings() {
			return strings;
		}

		ArrayList<String> strings;
		
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

		public void respond(PrintStream output) throws IOException {
			NetworkUtil.writeStringArray(output, strings);
			output.flush();
		}
		
		public void readMessage(DataInputStream input) throws IOException {
			String version = NetworkUtil.readString(input);
			if (!version.equals(VERSION_ID)) {
				throw new IOException("version incompatible: server(" + VERSION_ID + ") , client(" + version + ")");
			}
			strings = NetworkUtil.readStringArray(input);
		}

		public void sendError(PrintStream output, String value) throws IOException {
			NetworkUtil.writeString(output, value);
		}

		public void sendOK(PrintStream output) throws IOException {
			NetworkUtil.writeString(output, "OK");
		}
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
