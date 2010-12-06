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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.instrument.Instrumentation;
import java.lang.instrument.UnmodifiableClassException;
import java.util.ArrayList;

public class PreMain {
	public static void premain(String agentArguments, Instrumentation instrumentation) {	
		Analyzer.instance().activate();
		Transformer transformer = new Transformer();
		instrumentation.addTransformer(transformer, true);
		InputStream stream = PreMain.class.getResourceAsStream("config.ini");
		if (stream != null) {
			ArrayList<String> classesToInstrument = new ArrayList<String>();
			BufferedReader reader = new BufferedReader(new InputStreamReader(stream));
			try {
				String line = reader.readLine();
				while (line != null) {
					line = line.trim();
					if (!line.startsWith("##")) {
						String arguments[] = line.split(" ");
						if (arguments.length < 4) {
							System.out.println("invalid arguments in config.ini: " + line);
							System.out.println("usage: mode classname methodname signature [unscoped]");
							System.out.println(" where 'mode' is one of: L U L(n) U(n)");
							System.out.println(" 'unscoped' indicates that the object is not used for locking a scope, but for signaling");
						}
						else {
							String mode = arguments[0];
							String className = arguments[1];
							String methodName = arguments[2];
							String signature = arguments[3];
							int modeInt = parseMode(mode);
							boolean unscoped = arguments.length > 4 && arguments[4].equals("unscoped"); 
							transformer.register(modeInt, className, methodName, signature, unscoped);
							if (!classesToInstrument.contains(className))
								classesToInstrument.add(className);
						}
					}
					line = reader.readLine();
				}
			} catch (IOException e) {
			} finally {
				try {
					stream.close();
				} catch (IOException e) {
				}
			}
			for (String classToInstrument: classesToInstrument) {
				try {
					Class<?> cls = Class.forName(classToInstrument.replace('/','.'));
					instrumentation.retransformClasses(cls);
				} catch (RuntimeException e) {
					// ignore since frozen classes return this error
				} catch (ClassNotFoundException e) {
					// ignore
				} catch (UnmodifiableClassException e) {
					e.printStackTrace();
				}
			}
		}
		Analyzer.instance().enable();
	}

	private static int parseMode(String mode) {
		if (mode.equals("L"))
			return Analyzer.LOCK_NORMAL;
		if (mode.equals("U"))
			return Analyzer.UNLOCK_NORMAL;
		if (mode.equals("L(n)"))
			return Analyzer.LOCK_COUNTED;
		if (mode.equals("U(n)"))
			return Analyzer.UNLOCK_COUNTED;
		return 0;
	}	
}
