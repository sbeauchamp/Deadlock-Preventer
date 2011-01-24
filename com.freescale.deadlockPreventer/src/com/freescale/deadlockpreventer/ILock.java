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
