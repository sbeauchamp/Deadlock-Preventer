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

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jface.dialogs.ProgressMonitorDialog;
import org.eclipse.jface.operation.IRunnableWithProgress;
import org.eclipse.swt.widgets.FileDialog;
import org.eclipse.ui.PlatformUI;

import com.freescale.deadlockpreventer.ILock;
import com.freescale.deadlockpreventer.Logger;
import com.freescale.deadlockpreventer.QueryService.ITransaction;

public class StatisticsUtil {

	public static void export(final ITransaction transaction) {
		FileDialog dialog = new FileDialog(PlatformUI.getWorkbench().getActiveWorkbenchWindow().getShell());
		String file = dialog.open();
		if (file != null) {
			File outputFile = new File(file);
			if (!outputFile.getParentFile().exists())
				outputFile.getParentFile().mkdirs();
			try {
				if (!outputFile.exists())
					outputFile.createNewFile();
				final FileWriter writer = new FileWriter(outputFile);
	
				new ProgressMonitorDialog(PlatformUI.getWorkbench().getActiveWorkbenchWindow().getShell()).run(true, true, new IRunnableWithProgress() {
					@Override
					public void run(IProgressMonitor monitor) throws InvocationTargetException,
							InterruptedException {
						int count = transaction.getLockCount();
						monitor.beginTask("Downloading statistics...", count);
						int index = 0;
						int interval = 100;
						while (index < count) {
							ILock[] tmp = transaction.getLocks(index, Math.min(index + interval, count));
							monitor.worked(tmp.length);
							Logger.dumpLockInformation(tmp, writer);
							index += interval;
							if (monitor.isCanceled())
								break;
						}
						monitor.done();
					}
				});
				writer.close();
			} catch (IOException e) {
				e.printStackTrace();
			} catch (InvocationTargetException e) {
				e.printStackTrace();
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
	}
}
