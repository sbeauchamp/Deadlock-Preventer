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

public class CustomLock {
	Object lock;
	boolean scoped;
	
	public CustomLock(Object lock2, boolean scoped) {
		lock = lock2;
		this.scoped = scoped;
	}

	public String toString() {
		return "(Custom) " + Util.safeToString(lock);
	}
	
	static public Object getExternal(Object lock) {
		if (lock instanceof CustomLock)
			return ((CustomLock) lock).lock;
		return lock;
	}

	
}
