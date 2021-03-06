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

public interface Settings {

	public static final String THROWING_CLASS = "com.freescale.deadlockpreventer.throwingClass";
	public static final String INSTRUMENT_ONLY_LIST = "com.freescale.deadlockpreventer.instrumentOnlyList";
	public static final String WRITE_INSTRUMENTED_CLASSES = "com.freescale.deadlockpreventer.writeInstrumentedClasses";
	public static final String TRACE = "com.freescale.deadlockpreventer.trace";
	public static final String THROWS_ON_WARNING = "com.freescale.deadlockpreventer.throwsOnWarning";
	public static final String REPORT_RECURENT_CONFLICTS = "com.freescale.deadlockpreventer.reportRecurentConflicts";
	public static final String THROWS_ON_ERROR = "com.freescale.deadlockpreventer.throwsOnError";
	public static final String REPORT_WARNINGS = "com.freescale.deadlockpreventer.reportWarnings";
	public static final String ABORT_ON_ERRORS = "com.freescale.deadlockpreventer.abortOnErrors";
	public static final String LOG_TO_FILE = "com.freescale.deadlockpreventer.logToFile";
	public static final String LOG_TO_STREAM = "com.freescale.deadlockpreventer.logToStream";
	public static final String QUERY_SERVICE = "com.freescale.deadlockpreventer.queryService";
	public static final String REPORT_SERVICE = "com.freescale.deadlockpreventer.reportService";
	public static final String DUMP_LOCK_INFO = "com.freescale.deadlockpreventer.dumpLocksToFile";
	public static final String BUNDLE_ADVISOR = "com.freescale.deadlockpreventer.bundleAdvisor";
}