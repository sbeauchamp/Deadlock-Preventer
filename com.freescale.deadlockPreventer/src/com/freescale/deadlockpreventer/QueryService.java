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

public class QueryService {

	public static String VERSION_ID = "1.0.0";
	
	public static int DEFAULT_PORT = 43538;
	
	private static String QUERY_DUMP_LOCKS;
	
	public QueryService() {}
	
	public static class Client {
		Socket socket;
	    PrintStream output;
	    DataInputStream input;

	    String serverAddress;
		boolean connected = false;

	    public Client(String value) {
    		serverAddress = value;
    		String[] args = serverAddress.split(":");
    		int port = DEFAULT_PORT;
    		if (args.length > 1)
    			port = Integer.parseInt(args[1]);
    		try {
    			socket = new Socket(args[0], port);
    		} catch (UnknownHostException e) {
    			e.printStackTrace();
    		} catch (IOException e) {
    			e.printStackTrace();
    		}
    	    try {
    	       output = new PrintStream(socket.getOutputStream());
    	    }
    	    catch (IOException e) {
    	       System.out.println(e);
    	    }
    	    try {
    	       input = new DataInputStream(socket.getInputStream());
    	    }
    	    catch (IOException e) {
    	       System.out.println(e);
    	    }

           connected = true;
	    }
	    
	    public void start() {
			Thread thread = new Thread(new Runnable() {
				@Override
				public void run() {
					Thread clientThread = new Thread(new ClientConnection(socket));
					clientThread.start();
				}
			});
			thread.start();
	    }

	    public void stop() {
			try {
				socket.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}
	
	public static class Server {
		ServerConnection connection = null;
		ServerSocket server;
	    PrintStream output;
	    DataInputStream input;
	    Thread thread;

	    public void start(int port) {
			try {
		       server  = new ServerSocket(port);
			}
			catch (IOException e) {
			   System.out.println(e);
			}
			thread = new Thread(new Runnable() {
				@Override
				public void run() {
					while (true) {
						try {
							final Socket socket = server.accept();
					    	synchronized(Server.this) {
								if (connection != null)
									connection.stop();
								connection = new ServerConnection(socket);
					    	}
						} catch (SocketException e) {
							break;
						} catch (IOException e) {
							e.printStackTrace();
						}
					}
				}
			});
			thread.start();
	    }
	    
	    public void stop() {
	    	synchronized(this) {
		    	try {
					server.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
		    	if (connection != null)
		    		connection.stop();
	    	}
	    }
	    
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
	}
	
	private static ILock[] parseLocks(String[] strings) {
		return new Statistics(strings).locks();
	}

	private static class ServerConnection {

		Socket socket;
		DataInputStream input;
	    PrintStream output;
		
		public ServerConnection(Socket socket) {
			this.socket = socket;
			try {
				input = new DataInputStream(socket.getInputStream());
				output = new PrintStream(socket.getOutputStream());
			} catch (IOException e) {
				e.printStackTrace();
			}
		}

		public void stop() {
			try {
				socket.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}

		public Message request(Message msg) {
			try {
				return msg.request(output, input);
			} catch (SocketException e) {
			} catch (IOException e) {
			}
			return null;
		}
	}

	private static class ClientConnection implements Runnable{

		Socket socket;
		
		public ClientConnection(Socket socket) {
			this.socket = socket;
		}

		@Override
		public void run() {
			DataInputStream input;
		    PrintStream output;
		    try {
				input = new DataInputStream(socket.getInputStream());
				output = new PrintStream(socket.getOutputStream());
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
			} catch (IOException e) {
				e.printStackTrace();
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
			writeString(output, VERSION_ID);
			writeStringArray(output, strings);

			output.flush();
			String result = readString(input);
			if (!result.equals("OK"))
				throw new IOException("error returned from the server:" + result);
			Message content = new Message(readStringArray(input)); 
			return content;
		}

		public void readMessage(DataInputStream input) throws IOException {
			String version = readString(input);
			if (!version.equals(VERSION_ID)) {
				throw new IOException("version incompatible: server(" + VERSION_ID + ") , client(" + version + ")");
			}
			strings = readStringArray(input);
		}

		public void sendError(PrintStream output, String value) {
			writeString(output, value);
		}

		public void sendOK(PrintStream output) {
			writeString(output, "OK");
		}
		
		private static String[] readStringArray(DataInputStream input) throws IOException {
			String str = readString(input);
			int count = Integer.parseInt(str);
			String[] array = new String[count];
			for (int i = 0; i < count; i++)
				array[i] = readString(input);
			return array;
		}

		private static void writeStringArray(PrintStream output, String[] array) {
			writeString(output, Integer.toString(array.length));
			for (String item : array)
				writeString(output, item);
		}

		private static String readString(DataInputStream input) throws IOException {
			char c = readCharacter(input);
			while (c != '<')
				c = readCharacter(input);
			
			StringBuffer buffer = new StringBuffer();
			c = readCharacter(input);
			while (c != '>') {
				buffer.append(c);
				c = readCharacter(input);
			}
			int length = Integer.parseInt(buffer.toString());
			c = readCharacter(input);
			if (c != '<')
				throw new IOException("unexpected character: " + c);

			StringBuffer string = new StringBuffer();
			while (length--  > 0) {
				c = readCharacter(input);
				string.append(c);
			}

			c = readCharacter(input);
			if (c != '>')
				throw new IOException("unexpected character: " + c);
				
			return string.toString();
		}
		
		private static char readCharacter(DataInputStream input) throws IOException {
			int c = input.read();
			if (c == -1)
				throw new IOException("unexpected eos");
			return (char) c;
		}

		private static void writeString(PrintStream output, String string) {
			output.print("<" + Integer.toString(string.length()) + ">");
			output.print("<" + string + ">");
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
}
