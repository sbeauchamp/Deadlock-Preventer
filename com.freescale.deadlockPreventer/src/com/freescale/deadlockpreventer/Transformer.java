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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.IllegalClassFormatException;
import java.security.ProtectionDomain;
import java.util.ArrayList;

import javassist.CannotCompileException;
import javassist.ClassPool;
import javassist.CtBehavior;
import javassist.CtClass;
import javassist.CtMethod;
import javassist.Modifier;
import javassist.bytecode.BadBytecode;
import javassist.bytecode.Bytecode;
import javassist.bytecode.CodeAttribute;
import javassist.bytecode.CodeIterator;
import javassist.bytecode.ConstPool;
import javassist.bytecode.LineNumberAttribute;
import javassist.bytecode.MethodInfo;
import javassist.bytecode.Opcode;
import javassist.bytecode.StackMapTable;

public class Transformer implements ClassFileTransformer  {
	
	private static final String LEAVE_LOCK = "leaveLock";
	private static final String ENTER_LOCK = "enterLock";
	private static final String LEAVE_LOCK_CUSTOM = "leaveLockCustom";
	private static final String ENTER_LOCK_CUSTOM_UNSCOPED = "enterLockCustomUnscoped";
	private static final String LEAVE_LOCK_CUSTOM_UNSCOPED = "leaveLockCustomUnscoped";
	private static final String ENTER_LOCK_CUSTOM = "enterLockCustom";
	private static final String LJAVA_LANG_OBJECT_V = "(Ljava/lang/Object;)V";
	private static final String LJAVA_LANG_OBJECT__INT_V = "(Ljava/lang/Object;I)V";
	private static final String COM_FREESCALE_DEADLOCK_PREVENTER_ANALYZER = "com/freescale/deadlockpreventer/Analyzer";

	static boolean rebuildStackMap = false;
	
	public byte[] transform(ClassLoader loader, String className,
			Class<?> classBeingRedefined, ProtectionDomain protectionDomain,
			byte[] classfileBuffer) throws IllegalClassFormatException {
				
		ArrayList<RegisteredLock> registeredMethods = null;
		for (RegisteredLock registeredLock : registeredLocks) {
			if (registeredLock.className.equals(className)) {
				if (registeredMethods == null)
					registeredMethods = new ArrayList<Transformer.RegisteredLock>();
				registeredMethods.add(registeredLock);
			}
		}

		if (registeredMethods == null) {
			if (className.startsWith("java/"))
				return classfileBuffer;
			if (className.startsWith("sun/"))
				return classfileBuffer;
		}
			
		if (className.startsWith("com/freescale/deadlockpreventer/") && 
				!className.startsWith("com/freescale/deadlockpreventer/tests"))
			return classfileBuffer;
		
		if (className.startsWith("javassist/"))
			return classfileBuffer;

		try {
			Analyzer.instance().disable();
			ClassPool globalPool = ClassPool.getDefault();
			ClassPool.doPruning = true;
			CtClass currentClass = null;
			try {
				final CtClass cls = globalPool.makeClass(new ByteArrayInputStream(
						classfileBuffer));
				currentClass = cls;
				final CtMethod methods[] = cls.getDeclaredMethods();
				for (int i = 0; i < methods.length; i++) {
					boolean updateLineNumber = false;
					MonitorEditor editor = new MonitorEditor();
					final CtMethod ctMethod = methods[i];
					editor.edit(cls, ctMethod);
			        MethodInfo methodInfo = ctMethod.getMethodInfo();
					if (Modifier.isSynchronized(ctMethod.getModifiers())) {
						CodeAttribute codeAttribute = methodInfo.getCodeAttribute();
						if (codeAttribute != null) {
							try {
								ctMethod.insertBefore(new CtBehavior.CodeInserter() {
									public void insert(Bytecode code, int pos) {
										insertEnterLock(code, pos, Analyzer.LOCK_NORMAL, 
												Modifier.isStatic(ctMethod.getModifiers()), false, cls.getName(), false);
									}
								}, false);
								if (Analyzer.instance().shouldWriteInstrumentedClasses())
									cls.debugWriteFile();
								
								ctMethod.insertAfter(new CtBehavior.CodeInserter() {
											public void insert(Bytecode code, int pos) {
												insertEnterLock(code, pos, Analyzer.UNLOCK_NORMAL, 
														Modifier.isStatic(ctMethod.getModifiers()), false, cls.getName(), false);
											}
								}, true, false);

								if (Analyzer.instance().shouldWriteInstrumentedClasses())
									cls.debugWriteFile();
							} catch (CannotCompileException e) {
								e.printStackTrace();
								return classfileBuffer;
							}
							updateLineNumber = true;
						}
					}
					if (registeredMethods != null) {
						for (final RegisteredLock registeredLock : registeredMethods) {
							if (registeredLock.methodName.equals(ctMethod.getName()) && 
									registeredLock.signature.equals(ctMethod.getSignature())) {
								updateLineNumber = true;
								try {
									ctMethod.insertBefore(new CtBehavior.CodeInserter() {
										public void insert(Bytecode code, int pos) {
											insertEnterLock(code, pos, registeredLock.lockMode, 
													Modifier.isStatic(ctMethod.getModifiers()), true, cls.getName(), registeredLock.unscoped);
										}
									}, false);
								} catch (CannotCompileException e) {
									e.printStackTrace();
									return classfileBuffer;
								}
							}
						}
					}
					if (updateLineNumber) {
						CodeAttribute codeAttribute = methodInfo.getCodeAttribute();
						if (codeAttribute != null) {
							LineNumberAttribute ainfo = (LineNumberAttribute)codeAttribute.getAttribute(LineNumberAttribute.tag);
					        if (ainfo != null)
					        	ainfo.includeZeroPc();
						}
						if (rebuildStackMap)
							methodInfo.rebuildStackMapIf6(cls.getClassPool(), cls.getClassFile2());
						else
							codeAttribute.removeAttribute(StackMapTable.tag);
					}
				}
				if (Analyzer.instance().shouldWriteInstrumentedClasses())
					cls.debugWriteFile();
				if (Analyzer.instance().isDebug())
					System.out.println("transform: " + className);
				return cls.toBytecode();
			} catch (IOException e) {
				e.printStackTrace();
			} catch (RuntimeException e) {
				e.printStackTrace();
			} catch (Throwable e) {
				e.printStackTrace();
			} finally {
				if (currentClass != null)
					currentClass.detach();
			}
		}
		finally {
			Analyzer.instance().enable();
		}
		return classfileBuffer;
	}

	private void insertEnterLock(Bytecode b, int pos, int lockMode, boolean isStatic, boolean isCustom, String className, boolean unscoped) {
		b.setMaxStack(b.getMaxStack() + 1);
		String name = ENTER_LOCK;
		String signature = LJAVA_LANG_OBJECT_V;
		boolean isCounted = false;
		if (isCustom) {
			switch(lockMode) {
			case Analyzer.LOCK_NORMAL:
				if (unscoped)
					name = ENTER_LOCK_CUSTOM_UNSCOPED;
				else
					name = ENTER_LOCK_CUSTOM;
				signature = LJAVA_LANG_OBJECT_V;
				break;
			case Analyzer.UNLOCK_NORMAL:
				if (unscoped)
					name = LEAVE_LOCK_CUSTOM_UNSCOPED;
				else
					name = LEAVE_LOCK_CUSTOM;
				signature = LJAVA_LANG_OBJECT_V;
				break;
			case Analyzer.LOCK_COUNTED:
				name = ENTER_LOCK_CUSTOM;
				signature = LJAVA_LANG_OBJECT__INT_V;
				isCounted = true;
				break;
			case Analyzer.UNLOCK_COUNTED:
				name = LEAVE_LOCK_CUSTOM;
				signature = LJAVA_LANG_OBJECT__INT_V;
				isCounted = true;
				break;
			}
		}
		else {
			name = (lockMode == Analyzer.LOCK_NORMAL ? ENTER_LOCK: LEAVE_LOCK);
			signature = LJAVA_LANG_OBJECT_V;
		}
		if (isStatic) {
			int pc = b.currentPc();
	        b.addLdc(b.getConstPool().addStringInfo(className));
			b.addInvokestatic("java/lang/Class", "forName", "(Ljava/lang/String;)Ljava/lang/Class;");
			
			int endHandler = b.currentPc();
			Bytecode tmp = new Bytecode(b.getConstPool(), 1, 0);
			tmp.addInvokestatic("javassist/runtime/DotClass", "fail", "(Ljava/lang/ClassNotFoundException;)Ljava/lang/NoClassDefFoundError;");
			tmp.addAthrow();
			tmp.addGoto(b.getSize() + 4);

			b.addGoto(tmp.getSize());

			int handler = b.currentPc();
			b.addInvokestatic("javassist/runtime/DotClass", "fail", "(Ljava/lang/ClassNotFoundException;)Ljava/lang/NoClassDefFoundError;");
			b.addAthrow();
			if (isCounted)
				b.addIload(0);
			b.addInvokestatic(COM_FREESCALE_DEADLOCK_PREVENTER_ANALYZER, name, signature);
			
			b.addExceptionHandler(pc, endHandler, handler, "java.lang.ClassNotFoundException");
		}
		else {
			// 0 is 'this' for class methods
			b.addAload(0);
			if (isCounted)
				b.addIload(1);
			b.addInvokestatic(COM_FREESCALE_DEADLOCK_PREVENTER_ANALYZER, name, signature);
		}
	}

	public static void lock(Object lock) {
		System.out.println("lock: " + lock);
	}

	public static void unlock(Object lock) {
		System.out.println("unlock: " + lock);
	}

	static class MonitorEditor {
		public MonitorEditor() {
		}

		public void edit(CtClass cls, CtMethod ctMethod) throws CannotCompileException {
			MethodInfo methodInfo = ctMethod.getMethodInfo();
			CodeAttribute codeAttribute = methodInfo.getCodeAttribute();
			if (codeAttribute == null)
				return;
			CodeIterator iterator = codeAttribute.iterator();
			boolean changed = false;
			while (iterator.hasNext()) {
				try {
					int pos = iterator.next();
					int c = iterator.byteAt(pos);
					if (c == Opcode.MONITORENTER) {
						changed = true;
						ConstPool cp = methodInfo.getConstPool();

						CodeAttribute attr = methodInfo.getCodeAttribute();
						attr.setMaxStack(attr.getMaxStack() + 1);
						
						// insert invokestatic
						Bytecode b = new Bytecode(cp, 1, 0);
						b.addInvokestatic(COM_FREESCALE_DEADLOCK_PREVENTER_ANALYZER, ENTER_LOCK, LJAVA_LANG_OBJECT_V);
						byte[] code = b.get();
						iterator.insert(pos, code);

						// insert dup
						b = new Bytecode(cp, 1, 0);
						b.addDup();
						code = b.get();
						iterator.insert(pos, code);
					}
					if (c == Opcode.MONITOREXIT) {
						ConstPool cp = methodInfo.getConstPool();
						// insert invokestatic
						Bytecode b = new Bytecode(cp, 1, 0);
						b.addInvokestatic(COM_FREESCALE_DEADLOCK_PREVENTER_ANALYZER, LEAVE_LOCK, LJAVA_LANG_OBJECT_V);
						byte[] code = b.get();
						iterator.insert(pos, code);

						b = new Bytecode(cp, 1, 0);
						b.addDup();
						code = b.get();
						iterator.insert(pos, code);
					}
				} catch (BadBytecode e) {
					e.printStackTrace();
				}
			}
			if (changed) {
				try {
					if (Analyzer.instance().shouldWriteInstrumentedClasses())
						cls.debugWriteFile();
					if (rebuildStackMap)
						methodInfo.rebuildStackMapIf6(cls.getClassPool(), cls.getClassFile2());
				} catch (BadBytecode e) {
					e.printStackTrace();
				}
			}
		}
	}

	static class RegisteredLock {
		int lockMode;
		String className;
		String methodName;
		String signature;
		boolean unscoped;
		
		public RegisteredLock(int lockMode, String className,
				String methodName, String signature, boolean unscoped) {
			this.lockMode = lockMode;
			this.className = className;
			this.methodName = methodName;
			this.signature = signature;
			this.unscoped = unscoped;
		}
	}
	ArrayList<RegisteredLock> registeredLocks = new ArrayList<RegisteredLock>();
	
	public void register(int lockMode, String className, String methodName,
			String signature, boolean unscoped) {
		registeredLocks.add(new RegisteredLock(lockMode, className, methodName,
				signature, unscoped));
	}
}
