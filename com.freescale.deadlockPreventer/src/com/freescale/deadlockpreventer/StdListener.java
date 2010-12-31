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

import java.io.IOException;
import java.io.PrintStream;

public class StdListener implements IConflictListener {

	PrintStream stream;
	public StdListener(PrintStream stream) {
		this.stream = stream;
		log("Freescale Semiconductor Deadlock Preventer Error log 1.0.0\n");
	}

	@Override
	public int report(int type, String threadID, String conflictThreadID,
			Object lock, StackTraceElement[] lockStack, Object precedent,
			StackTraceElement[] precedentStack, Object conflict,
			StackTraceElement[] conflictStack, Object conflictPrecedent,
			StackTraceElement[] conflictPrecedentStack, String message) {
		if (type != Analyzer.TYPE_WARNING)
			log(message + "\n");
		return CONTINUE;
	}

	private void log(String string) {
		try {
			stream.write(string.getBytes());
			stream.flush();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
}
