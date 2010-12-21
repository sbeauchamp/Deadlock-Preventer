package com.freescale.deadlockpreventer;

public interface IContext {

	String getThreadID();
	
	String[] getStackTrace();
	
	ILock getLock();
}
