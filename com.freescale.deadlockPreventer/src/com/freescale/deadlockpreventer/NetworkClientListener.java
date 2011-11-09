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
import java.util.ArrayList;
import java.util.Arrays;

public class NetworkClientListener implements IConflictListener {

	public static String VERSION_ID = "1.0.0";
	
	NetworkServer.Session session;
	boolean connected = false;
	
	public NetworkClientListener(String value) {
		try {
			session = NetworkServer.connect(value);
			connected = true;
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	public void stop() {
		session.close();
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
			this.lock = Util.getUniqueIdentifier(lock);
			this.lockStack = convertToString(lockStack);
			this.precedent = Util.getUniqueIdentifier(precedent);
			this.precedentStack = convertToString(precedentStack);
			this.conflict = Util.getUniqueIdentifier(conflict);
			this.conflictStack = convertToString(conflictStack);
			this.conflictPrecedent = Util.getUniqueIdentifier(conflictPrecedent);
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
			NetworkUtil.writeString(output, VERSION_ID);
			NetworkUtil.writeString(output, Integer.toString(getMessageCount()));
			
			NetworkUtil.writeString(output, type);
			NetworkUtil.writeString(output, threadID);
			NetworkUtil.writeString(output, conflictThreadID);
			NetworkUtil.writeString(output, lock);
			NetworkUtil.writeStringArray(output, new ArrayList<String>(Arrays.asList(lockStack)));
			
			NetworkUtil.writeString(output, precedent);
			NetworkUtil.writeStringArray(output, new ArrayList<String>(Arrays.asList(precedentStack)));

			NetworkUtil.writeString(output, conflict);
			NetworkUtil.writeStringArray(output, new ArrayList<String>(Arrays.asList(conflictStack)));

			NetworkUtil.writeString(output, conflictPrecedent);
			NetworkUtil.writeStringArray(output, new ArrayList<String>(Arrays.asList(conflictPrecedentStack)));

			NetworkUtil.writeString(output, message);

			output.flush();
			String result = NetworkUtil.readString(input);
			if (!result.equals("OK"))
				throw new IOException("error returned from the server:" + result);
			result = NetworkUtil.readString(input);
			return Integer.parseInt(result);
		}

		private int getMessageCount() {
			return 8 + lockStack.length + precedentStack.length + conflictStack.length + conflictPrecedentStack.length;
		}

		public void readMessage(DataInputStream input) throws IOException {
			String version = NetworkUtil.readString(input);
			String stringCount = NetworkUtil.readString(input);
			int count = Integer.parseInt(stringCount);
			if (!version.equals(VERSION_ID)) {
				while (count-- > 0)
					NetworkUtil.readString(input);
				throw new IOException("version incompatible: server(" + VERSION_ID + ") , client(" + version + ")");
			}
			type = NetworkUtil.readString(input);
			threadID = NetworkUtil.readString(input);
			conflictThreadID = NetworkUtil.readString(input);

			lock = NetworkUtil.readString(input);
			lockStack = NetworkUtil.readStringArray(input).toArray(new String[0]);

			precedent = NetworkUtil.readString(input);
			precedentStack = NetworkUtil.readStringArray(input).toArray(new String[0]);

			conflict = NetworkUtil.readString(input);
			conflictStack = NetworkUtil.readStringArray(input).toArray(new String[0]);
		
			conflictPrecedent = NetworkUtil.readString(input);
			conflictPrecedentStack = NetworkUtil.readStringArray(input).toArray(new String[0]);

			message = NetworkUtil.readString(input);
		}

		public void sendError(PrintStream output, String value) throws IOException {
			NetworkUtil.writeString(output, value);
		}

		public void sendResponse(PrintStream output, int value) throws IOException {
			NetworkUtil.writeString(output, "OK");
			NetworkUtil.writeString(output,Integer.toString(value));
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
				return msg.sendTo(session.getOutput(), session.getInput());
			} catch (IOException e) {
				System.out.println(Logger.getPrintOutHeader() + "Error network communication: " + e.getMessage());
			}
		}
		return CONTINUE | LOG_TO_CONSOLE;
	}
}
