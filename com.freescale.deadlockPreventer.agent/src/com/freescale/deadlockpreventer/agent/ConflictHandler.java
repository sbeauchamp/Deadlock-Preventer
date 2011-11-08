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


public class ConflictHandler implements Runnable {
	private final Conflict conflictItem;
	private LauncherView launcherView;
	public int result = 0;
	
	public ConflictHandler(LauncherView launcherView, Conflict conflictItem) {
		this.conflictItem = conflictItem;
		this.launcherView = launcherView;
	}

	@Override
	public void run() {
		launcherView.getViewer().setExpandedState(conflictItem.process, true);
		launcherView.getViewer().refresh();
		result = launcherView.handleConflict(conflictItem);
	}
}
