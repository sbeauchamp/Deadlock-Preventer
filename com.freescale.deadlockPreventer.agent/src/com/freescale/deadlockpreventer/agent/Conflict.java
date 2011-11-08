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
package com.freescale.deadlockpreventer.agent;

public 	class Conflict {
	public Conflict(InstrumentedProcess process, String type, String threadID, String conflictThreadID,
			String lock, String[] lockStack, String precedent,
			String[] precedentStack, String conflict,
			String[] conflictStack, String conflictPrecedent,
			String[] conflictPrecedentStack, String message) {
		this.process = process;
		this.type = type;
		this.threadID = threadID;
		this.conflictThreadID = conflictThreadID;
		this.lock = lock;
		this.lockStack = lockStack;
		this.precedent = precedent;
		this.precedentStack = precedentStack;
		this.conflict = conflict;
		this.conflictStack = conflictStack;
		this.conflictPrecedent = conflictPrecedent;
		this.conflictPrecedentStack = conflictPrecedentStack;
		this.message = message;
	}
	InstrumentedProcess process;
	String type;
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
	
	public String toString() {
		return type + ": '" + lock + "' conflicts with '" + precedent + "' in thread '" + threadID  + "' and '" + conflictThreadID + "'";
	}


	public boolean isError() {
		return type.equals("ERROR");
	}


	public void remove() {
		process.getConflictList().remove(this);
	}
}