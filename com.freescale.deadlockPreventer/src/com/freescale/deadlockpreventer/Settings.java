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

}