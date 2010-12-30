package com.freescale.deadlockpreventer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.ListIterator;

import com.freescale.deadlockpreventer.Analyzer.CustomLock;

// We use a custom object cache because we can't use a simple HashMap<Object>, since the object.hashCode() 
// can be overridden by clients and cause deadlocks. 
public class ObjectCache<T> {
	HashMap<String, ArrayList<ObjectCache.Entry<T>>> cache = new HashMap<String, ArrayList<ObjectCache.Entry<T>>>();
	
	public T get(Object obj) {
		return getFromKey(getKey(obj), obj);
	}
	
	public T getFromKey(Object key, Object obj) {
		ArrayList<ObjectCache.Entry<T>> cacheLine = cache.get(key);
		if (cacheLine != null) {
			ListIterator<ObjectCache.Entry<T>> iterator = cacheLine.listIterator(cacheLine.size());
			while (iterator.hasPrevious()) {
				ObjectCache.Entry<T> entry = iterator.previous();
				if (entry.object == obj)
					return entry.value;
			}
		}
		return null;
	}
	
	public T getOrCreate(Object obj, Class<T> cls) {
		String key = getKey(obj);
		ArrayList<ObjectCache.Entry<T>> cacheLine = cache.get(key);
		if (cacheLine != null) {
			ListIterator<ObjectCache.Entry<T>> iterator = cacheLine.listIterator(cacheLine.size());
			while (iterator.hasPrevious()) {
				ObjectCache.Entry<T> entry = iterator.previous();
				if (entry.object == obj)
					return entry.value;
			}
		} else {
			cacheLine = new ArrayList<ObjectCache.Entry<T>>();
			cache.put(key, cacheLine);
		}
		T value;
		try {
			value = cls.newInstance();
		} catch (Throwable e) {
			e.printStackTrace();
			return null;
		}
		cacheLine.add(new ObjectCache.Entry<T>(obj, value));
		return value;
	}

	public static String getKey(Object obj) {
		Class<?> cls = obj.getClass();
		if (cls.equals(CustomLock.class))
			cls = ((CustomLock) obj).lock.getClass();
		return cls.getSimpleName();
	}

	public void put(Object obj, T value) {
		String key = getKey(obj);
		ArrayList<ObjectCache.Entry<T>> cacheLine = cache.get(key);
		if (cacheLine == null) {
			cacheLine = new ArrayList<ObjectCache.Entry<T>>();
			cache.put(key, cacheLine);
		}
		cacheLine.add(new ObjectCache.Entry<T>(obj, value));
	}
	
	interface IVisitor<T> { 
		boolean visit(ObjectCache.Entry<T> obj);
	}
	
	static class Entry<E> {
		public Entry(Object obj, E value2) {
			object = obj;
			value = value2;
		}
		Object object;
		E value;
	}

	public void visitKeyEntries(String key, IVisitor visitor) {
		ArrayList<ObjectCache.Entry<T>> cacheLine = cache.get(key);
		if (cacheLine != null) {
			ListIterator<ObjectCache.Entry<T>> iterator = cacheLine.listIterator();
			while (iterator.hasNext()) {
				ObjectCache.Entry<T> entry = iterator.next();
				if (!visitor.visit(entry))
					break;
			}
		}
	}
}
