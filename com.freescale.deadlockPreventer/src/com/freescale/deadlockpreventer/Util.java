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
