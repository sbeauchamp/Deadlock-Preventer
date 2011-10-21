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

import java.util.ArrayList;

public interface ILock {

	String getID();

	String[] getStackTrace();
	
	IContext[] getPrecedents();
	
	IContext[] getFollowers();
	
	ArrayList<String> serialize();

	void serialize(ArrayList<String> result);
}
