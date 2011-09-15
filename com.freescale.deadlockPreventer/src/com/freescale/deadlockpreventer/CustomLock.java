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
