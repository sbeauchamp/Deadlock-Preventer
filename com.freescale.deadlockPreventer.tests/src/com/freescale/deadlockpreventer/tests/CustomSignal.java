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
package com.freescale.deadlockpreventer.tests;

public class CustomSignal {

	volatile int count = 0;
	
	public synchronized boolean isLocked() {
		return count > 0;
	}
	
	public void signal_wait() {
		count--;
	}

	public void signal_notify() {
		count--;
	}
}
