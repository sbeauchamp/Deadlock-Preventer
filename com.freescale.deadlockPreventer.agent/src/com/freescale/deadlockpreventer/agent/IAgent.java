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
package com.freescale.deadlockpreventer.agent;

import org.eclipse.ui.IWorkbenchPartSite;

public interface IAgent {
	public static final String PREF_INSTALLATION_DIR = "install_dir";
	public static final String PREF_DEFAULT_HANDLING = "default_handling";
	public static final String PREF_DISPLAY_WARNINGS = "display_warnings";
	public static final String PREF_DISPLAY_FILTERS = "display_filters";
	public static final String PREF_PRINT_TO_STDOUT = "print_to_stdout";
	public static final String PREF_EXCEPTION_THROWS = "exception_thrown";

	public static final int VM_ARG_AGENT = 0;
	public static final int VM_ARG_BOOT_CLASSPATH = 1;
	public static final int VM_ARG_BOOT_SERVER_PORT = 2;
	public static final int VM_ADDITIONAL_ARGUMENTS = 3;

	public String getPref(String key, String defaultValue);
	public void setPref(String key, String newValue);

	public void output(String string);

	public void resetOutput();

	public static interface IProcess {}
	
	public IProcess createProcess(String label);
	
	public String getVMArg(IProcess process, int vmArgAgent);

	public IWorkbenchPartSite getSite();
	public String getViewID();
}
