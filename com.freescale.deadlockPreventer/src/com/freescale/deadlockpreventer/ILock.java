package com.freescale.deadlockpreventer;

public interface ILock {

	String getID();

	IContext[] getPrecedents();
	
	IContext[] getFollowers();
}
