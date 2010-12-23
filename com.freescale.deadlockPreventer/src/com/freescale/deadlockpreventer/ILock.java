package com.freescale.deadlockpreventer;

public interface ILock {

	String getID();

	String[] getStackTrace();
	
	IContext[] getPrecedents();
	
	IContext[] getFollowers();
}
