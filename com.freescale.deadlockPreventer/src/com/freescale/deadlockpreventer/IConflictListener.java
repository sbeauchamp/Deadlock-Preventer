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

public interface IConflictListener {
	
	public static int CONTINUE 			= 0;
	public static int ABORT 			= 1;
	public static int DEBUG 			= 2;
	public static int EXCEPTION			= 4;
	public static int LOG_TO_CONSOLE	= 8;

	public int report(int type, String threadID, String conflictThreadID, 
			Object lock, StackTraceElement[] lockStack, 
			Object precedent, StackTraceElement[] precedentStack,
			Object conflict, StackTraceElement[] conflictStack,
			Object conflictPrecedent, StackTraceElement[] conflictPrecedentStack, String message);
}
