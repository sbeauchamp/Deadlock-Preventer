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

public class Util {
	
	static ObjectCache<Integer> uniqueIDSet = new ObjectCache<Integer>();
	static int uniqueIDCount = 0;
	
	public static String safeToString(Object obj) {
		if (obj != null) {
			String id = null;
			synchronized(uniqueIDSet) {
				Integer idValue = uniqueIDSet.get(obj);
				if (idValue == null) {
					uniqueIDCount++;
					uniqueIDSet.put(obj, new Integer(uniqueIDCount));
					id = Integer.toString(uniqueIDCount);
				}
				else
					id = idValue.toString();
			}
			Class<?> cls = obj.getClass();
			if (cls.equals(CustomLock.class))
				cls = ((CustomLock) obj).lock.getClass();
			return cls.getName() + " (id=" + id + ")";
		}
		return new String();
	}

}
