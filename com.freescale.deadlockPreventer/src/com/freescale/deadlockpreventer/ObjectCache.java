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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.ListIterator;

// We use a custom object cache because we can't use a simple HashMap<Object>, since the object.hashCode() 
// can be overridden by clients and cause deadlocks. 
public class ObjectCache<T> {
	HashMap<Integer, ArrayList<ObjectCache.Entry<T>>> cache = new HashMap<Integer, ArrayList<ObjectCache.Entry<T>>>();
	
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
		Integer key = getKey(obj);
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

	public static Integer getKey(Object obj) {
		Class<?> cls = obj.getClass();
		if (cls.equals(CustomLock.class))
			cls = ((CustomLock) obj).lock.getClass();
		return System.identityHashCode(obj);
	}

	public void put(Object obj, T value) {
		Integer key = getKey(obj);
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
