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

public class CustomLock {

	volatile int count = 0;
	Object primitive = new Object();
	
	public synchronized boolean isLocked() {
		return count > 0;
	}
	
	public void lock() {
		int local;
		synchronized(this) {
			count++;
			local = count;
		}
		if (local > 1) {
			try {
				primitive.wait();
			} catch (InterruptedException e) {
			}
		}
	}
	
	public synchronized void unlock() {
		int local;
		synchronized(this) {
			count--;
			local = count;
		}
		if (local > 0)
			primitive.notify();
	}
}
