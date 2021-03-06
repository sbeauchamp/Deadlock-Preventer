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

public class ConflictReport {
	String threadID;
	String conflictThreadID;
	Object lock;
	Object precedent;
	Object conflict;
	Object conflictPrecedent;
	
	public ConflictReport(String threadID, String conflictThreadID,
			Object lock, Object precedent, Object conflict,
			Object conflictPrecedent) {
		super();
		this.threadID = threadID;
		this.conflictThreadID = conflictThreadID;
		this.lock = lock;
		this.precedent = precedent;
		this.conflict = conflict;
		this.conflictPrecedent = conflictPrecedent;
	}

	public boolean equals(Object o) {
		if (o instanceof ConflictReport) {
			ConflictReport report = (ConflictReport) o;
			return report.threadID.equals(threadID) &&
				report.conflictThreadID.equals(conflictThreadID) &&
				(report.lock == lock &&
				report.precedent == precedent &&
				report.conflict == conflict &&
				report.conflictPrecedent == conflictPrecedent) || // objects can be swapped too
				(report.lock == precedent &&
						report.precedent == lock &&
						report.conflict == conflictPrecedent &&
						report.conflictPrecedent == conflict);
		}
		return false;
	}
}