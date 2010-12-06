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

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

public class FileListener implements IConflictListener {

	FileOutputStream outputStream;
	public FileListener(String filePath) {
		File outputFile = new File(filePath);
		if (!outputFile.getParentFile().exists())
			outputFile.getParentFile().mkdirs();
		try {
			if (!outputFile.exists())
				outputFile.createNewFile();
			outputStream = new FileOutputStream(outputFile, true);
			log("Freescale Semiconductor Deadlock Preventer Error log 1.0.0\n");
		} catch (IOException e) {
			e.printStackTrace();
		}
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
			outputStream.write(string.getBytes());
			outputStream.flush();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
}
