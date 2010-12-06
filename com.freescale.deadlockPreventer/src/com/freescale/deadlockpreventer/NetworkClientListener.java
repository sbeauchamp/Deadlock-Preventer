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
import java.net.Socket;
import java.net.UnknownHostException;

public class NetworkClientListener implements IConflictListener {

	public static String VERSION_ID = "1.0.0";
	
	public static int DEFAULT_PORT = 43537;
	
	String serverAddress;
	Socket socket;
    PrintStream output;
    DataInputStream input;
	boolean connected = false;
	
	public NetworkClientListener(String value) {
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
	
	public void stop() {
		try {
			socket.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	public static class Message {
		public Message(int type, String threadID, String conflictThreadID,
				Object lock, StackTraceElement[] lockStack, Object precedent,
				StackTraceElement[] precedentStack, Object conflict,
				StackTraceElement[] conflictStack, Object conflictPrecedent,
				StackTraceElement[] conflictPrecedentStack, String message) {
			super();
			switch (type)
			{
			case Analyzer.TYPE_ERROR:
				this.type = "ERROR";
				break;
			case Analyzer.TYPE_WARNING:
				this.type = "WARNING";
				break;
			case Analyzer.TYPE_ERROR_SIGNAL:
				this.type = "ERROR_SIGNAL";
				break;
			}
			this.threadID = threadID;
			this.conflictThreadID = conflictThreadID;
			this.lock = Analyzer.safeToString(lock);
			this.lockStack = convertToString(lockStack);
			this.precedent = Analyzer.safeToString(precedent);
			this.precedentStack = convertToString(precedentStack);
			this.conflict = Analyzer.safeToString(conflict);
			this.conflictStack = convertToString(conflictStack);
			this.conflictPrecedent = Analyzer.safeToString(conflictPrecedent);
			this.conflictPrecedentStack = convertToString(conflictPrecedentStack);
			this.message = message;
		}

		public Message() {
		}

		private String[] convertToString(
				StackTraceElement[] input) {
			if (input != null) {
				String[] array = new String[input.length];
				for (int i = 0; i < input.length; i++)
					array[i] = input[i].toString();
				return array;
			}
			return new String[0];
		}

		String type;
		public String getThreadID() {
			return threadID;
		}

		public String getConflictThreadID() {
			return conflictThreadID;
		}

		public String getLock() {
			return lock;
		}

		public String[] getLockStack() {
			return lockStack;
		}

		public String getPrecedent() {
			return precedent;
		}

		public String[] getPrecedentStack() {
			return precedentStack;
		}

		public String getConflict() {
			return conflict;
		}

		public String[] getConflictStack() {
			return conflictStack;
		}

		public String getConflictPrecedent() {
			return conflictPrecedent;
		}

		public String[] getConflictPrecedentStack() {
			return conflictPrecedentStack;
		}

		public String getMessage() {
			return message;
		}

		public String getType() {
			return type;
		}

		String threadID;
		String conflictThreadID;
		String lock;
		String[] lockStack;
		String precedent;
		String[] precedentStack;
		String conflict;
		String[] conflictStack;
		String conflictPrecedent;
		String[] conflictPrecedentStack;
		String message;
		
		public int sendTo(PrintStream output, DataInputStream input) throws IOException {
			writeString(output, VERSION_ID);
			writeString(output, Integer.toString(getMessageCount()));
			
			writeString(output, type);
			writeString(output, threadID);
			writeString(output, conflictThreadID);
			writeString(output, lock);
			writeStringArray(output, lockStack);
			
			writeString(output, precedent);
			writeStringArray(output, precedentStack);

			writeString(output, conflict);
			writeStringArray(output, conflictStack);

			writeString(output, conflictPrecedent);
			writeStringArray(output, conflictPrecedentStack);

			writeString(output, message);

			output.flush();
			String result = readString(input);
			if (!result.equals("OK"))
				throw new IOException("error returned from the server:" + result);
			result = readString(input);
			return Integer.parseInt(result);
		}

		private int getMessageCount() {
			return 8 + lockStack.length + precedentStack.length + conflictStack.length + conflictPrecedentStack.length;
		}

		public void readMessage(DataInputStream input) throws IOException {
			String version = readString(input);
			String stringCount = readString(input);
			int count = Integer.parseInt(stringCount);
			if (!version.equals(VERSION_ID)) {
				while (count-- > 0)
					readString(input);
				throw new IOException("version incompatible: server(" + VERSION_ID + ") , client(" + version + ")");
			}
			type = readString(input);
			threadID = readString(input);
			conflictThreadID = readString(input);

			lock = readString(input);
			lockStack = readStringArray(input);;

			precedent = readString(input);
			precedentStack = readStringArray(input);;

			conflict = readString(input);
			conflictStack = readStringArray(input);;
			
			conflictPrecedent = readString(input);
			conflictPrecedentStack = readStringArray(input);;

			message = readString(input);
		}

		public void sendError(PrintStream output, String value) {
			writeString(output, value);
		}

		public void sendResponse(PrintStream output, int value) {
			writeString(output, "OK");
			writeString(output,Integer.toString(value));
		}
		
		private String[] readStringArray(DataInputStream input) throws IOException {
			String str = readString(input);
			int count = Integer.parseInt(str);
			String[] array = new String[count];
			for (int i = 0; i < count; i++)
				array[i] = readString(input);
			return array;
		}

		private void writeStringArray(PrintStream output, String[] array) {
			writeString(output, Integer.toString(array.length));
			for (String item : array)
				writeString(output, item);
		}

		private String readString(DataInputStream input) throws IOException {
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
		
		private char readCharacter(DataInputStream input) throws IOException {
			int c = input.read();
			if (c == -1)
				throw new IOException("unexpected eos");
			return (char) c;
		}

		private void writeString(PrintStream output, String string) {
			output.print("<" + Integer.toString(string.length()) + ">");
			output.print("<" + string + ">");
		}
		
	}

	@Override
	public int report(int type, String threadID, String conflictThreadID,
			Object lock, StackTraceElement[] lockStack, Object precedent,
			StackTraceElement[] precedentStack, Object conflict,
			StackTraceElement[] conflictStack, Object conflictPrecedent,
			StackTraceElement[] conflictPrecedentStack, String message) {
		Message msg = new Message(type, threadID, conflictThreadID,
			lock, lockStack, precedent,
			precedentStack, conflict,
			conflictStack, conflictPrecedent,
			conflictPrecedentStack, message);
		if (connected) {
			try {
				return msg.sendTo(output, input);
			} catch (IOException e) {
				System.out.println(Analyzer.getPrintOutHeader() + "Error network communication: " + e.getMessage());
			}
		}
		return CONTINUE | LOG_TO_CONSOLE;
	}
}
