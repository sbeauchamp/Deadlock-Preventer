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
package com.freescale.deadlockpreventer.agent.workbench;

import java.util.ArrayList;
import java.util.Hashtable;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.debug.core.DebugPlugin;
import org.eclipse.debug.core.ILaunch;
import org.eclipse.debug.core.ILaunchConfiguration;
import org.eclipse.debug.core.ILaunchConfigurationListener;
import org.eclipse.debug.core.ILaunchConfigurationWorkingCopy;
import org.eclipse.debug.core.ILaunchManager;
import org.eclipse.debug.core.ILaunchesListener2;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.progress.UIJob;

import com.freescale.deadlockpreventer.agent.IAgent;
import com.freescale.deadlockpreventer.agent.IConfigurator;

public class LaunchConfigurationConfigurator implements IConfigurator{

	private static final String DEBUG_PERSPECTIVE = "org.eclipse.debug.ui.DebugPerspective";
	private static final String PREF_COMBO_SELECTION = "com.freescale.deadlockpreventer.agent.workbench.comboSelection";
	private static final String SPACE = " ";
	private static final String ORG_ECLIPSE_JDT_LAUNCHING_VM_ARGUMENTS = "org.eclipse.jdt.launching.VM_ARGUMENTS";
	IAgent agent;
	private Button launch;
	private Combo combo;
	private Hashtable<String, String> oldLaunchSettings = new Hashtable<String, String>();
	
	
	@Override
	public void initialize(IAgent agent) {
		this.agent = agent;
		ILaunchManager manager = DebugPlugin.getDefault().getLaunchManager();
		manager.addLaunchConfigurationListener(new ILaunchConfigurationListener() {
			@Override
			public void launchConfigurationAdded(
					ILaunchConfiguration configuration) {
				if (!configuration.isWorkingCopy())
					setupComboContentAsync();
			}
			@Override
			public void launchConfigurationChanged(
					ILaunchConfiguration configuration) {
				if (!configuration.isWorkingCopy())
					setupComboContentAsync();
			}
			@Override
			public void launchConfigurationRemoved(
					ILaunchConfiguration configuration) {
				if (!configuration.isWorkingCopy())
					setupComboContentAsync();
			}
		});
		manager.addLaunchListener(new ILaunchesListener2() {
			@Override
			public void launchesRemoved(ILaunch[] launches) {}
			@Override
			public void launchesAdded(ILaunch[] launches) {}
			@Override
			public void launchesChanged(ILaunch[] launches) {}
			@Override
			public void launchesTerminated(ILaunch[] launches) {
				for (ILaunch launch : launches)
					handleChanged(launch);
			}
		});
	}

	protected void setupComboContentAsync() {
		Display.getDefault().asyncExec(new Runnable() {
			@Override
			public void run() {
				setupComboContent();
			}
		});
	}

	@Override
	public String getName() {
		return "Eclipse JDT Debugging";
	}

	@Override
	public void createPartControl(Composite parent) {
		parent.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
		GridLayout layout = new GridLayout(2, false);
		parent.setLayout(layout);
		

		combo = new Combo(parent, SWT.READ_ONLY | SWT.DROP_DOWN);
		combo.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
		combo.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				int index = combo.getSelectionIndex();
				if (index != -1 )
					agent.setPref(PREF_COMBO_SELECTION, combo.getItem(index));
			}
		});

		launch = new Button(parent, SWT.PUSH);
		launch.setText("Debug");
		launch.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, false, false));
		((GridData)launch.getLayoutData()).widthHint = 80;
		launch.addSelectionListener(new SelectionAdapter() {
			public void widgetSelected(SelectionEvent e) {
				doLaunch();
			}
		});
		launch.getShell().setDefaultButton(launch);	
		setupComboContent();
		launch.setEnabled(hasAValidLC());
		selectOldComboPref();
	}

	private void selectOldComboPref() {
		String selection = agent.getPref(PREF_COMBO_SELECTION, new String());
		if (selection.length() > 0) {
			String[] items = combo.getItems();
			for (int i = 0; i < items.length; i++) {
				if (items[i].equals(selection)) {
					combo.select(i);
					break;
				}
			}
		}
	}

	private void setupComboContent() {
		ILaunchManager manager = DebugPlugin.getDefault().getLaunchManager();
		try {
			ILaunchConfiguration[] lcs = manager.getLaunchConfigurations();
			ArrayList<ILaunchConfiguration> list = new ArrayList<ILaunchConfiguration>();
			for (ILaunchConfiguration lc : lcs) {
				String id = lc.getType().getIdentifier();
				if (id.equals("org.eclipse.pde.ui.RuntimeWorkbench") ||
						id.equals("org.eclipse.jdt.launching.localJavaApplication"))
					list.add(lc);
			}
			lcs = list.toArray(new ILaunchConfiguration[0]);
			String[] strings = new String[lcs.length];
			for (int i = 0; i < strings.length; i++) {
				strings[i] = lcs[i].getName();
			}
			int index = combo.getSelectionIndex();
			combo.setItems(strings);
			if (index == -1 || index > strings.length) {
				index = strings.length > 0? 0: -1;
			}
			combo.select(index);
		} catch (CoreException e) {
			e.printStackTrace();
		}
		launch.setEnabled(hasAValidLC());
	}

	private boolean hasAValidLC() {
		return combo.getSelectionIndex() != -1;
	}

	protected void doLaunch() {
		String name = combo.getItem(combo.getSelectionIndex());
		ILaunchManager manager = DebugPlugin.getDefault().getLaunchManager();
		try {
			ILaunchConfiguration[] lcs = manager.getLaunchConfigurations();
			for (ILaunchConfiguration lc : lcs) {
				if (lc.getName().equals(name)) {
					doLaunch(lc);
					break;
				}
			}
		} catch (CoreException e) {
			e.printStackTrace();
		}
	}

	private void doLaunch(final ILaunchConfiguration lc) {
		try {
			final String attribute = lc.getAttribute(ORG_ECLIPSE_JDT_LAUNCHING_VM_ARGUMENTS, new String());
			String[] attributes = attribute.split(SPACE);
			StringBuffer newAttributes = new StringBuffer();
			
			String prefixAgent = agent.getVMArg(IAgent.VM_ARG_AGENT).split(":")[0];
			String prefixBootClassPath = agent.getVMArg(IAgent.VM_ARG_BOOT_CLASSPATH).split(":")[0];
			String prefixServerPort = agent.getVMArg(IAgent.VM_ARG_BOOT_SERVER_PORT).split(":")[0];
			
			for (String attr : attributes) {
				if (attr.startsWith(prefixAgent) ||
						attr.startsWith(prefixBootClassPath) ||
						attr.startsWith(prefixServerPort))
					continue;
				newAttributes.append(attr + SPACE);
			}
			final String cleanedUpAttributes = newAttributes.toString();
			
			newAttributes.append(agent.getVMArg(IAgent.VM_ARG_AGENT) + SPACE);
			newAttributes.append(agent.getVMArg(IAgent.VM_ARG_BOOT_CLASSPATH) + SPACE);
			newAttributes.append(agent.getVMArg(IAgent.VM_ARG_BOOT_SERVER_PORT));
			String additional = agent.getVMArg(IAgent.VM_ADDITIONAL_ARGUMENTS);
			if (additional != null)
				newAttributes.append(SPACE + additional);
			
			ILaunchConfigurationWorkingCopy copy = lc.getWorkingCopy();
			copy.setAttribute(ORG_ECLIPSE_JDT_LAUNCHING_VM_ARGUMENTS, newAttributes.toString());
			final ILaunchConfiguration newlc = copy.doSave();
			oldLaunchSettings.put(newlc.getName(), cleanedUpAttributes);
			
			UIJob job = new UIJob("Debugging: " + newlc.getName()) {
				
				@Override
				public IStatus runInUIThread(IProgressMonitor monitor) {
					try {
						newlc.launch(ILaunchManager.DEBUG_MODE, monitor);
						PlatformUI.getWorkbench().showPerspective(DEBUG_PERSPECTIVE, agent.getSite().getWorkbenchWindow());
						agent.getSite().getWorkbenchWindow().getActivePage().showView(agent.getViewID());
					} catch (CoreException e) {
						e.printStackTrace();
						return new Status(IStatus.ERROR, Activator.PLUGIN_ID, "Cannot debug launch configuration: " + newlc.getName());
					}
					return Status.OK_STATUS;
				}
			};
			job.schedule();
		} catch (CoreException e) {
			e.printStackTrace();
		}
		
	}
	protected void handleChanged(ILaunch launch) {
		if (launch.isTerminated()) {
			ILaunchConfiguration lc = launch.getLaunchConfiguration();
			try {
				String cleanedUpAttributes = oldLaunchSettings.get(lc.getName());
				if (cleanedUpAttributes != null) {
					ILaunchConfigurationWorkingCopy copy = lc.getWorkingCopy();
					copy.setAttribute(ORG_ECLIPSE_JDT_LAUNCHING_VM_ARGUMENTS, cleanedUpAttributes);
					copy.doSave();
				}
			} catch (CoreException e) {
				e.printStackTrace();
			}
			
		}
		
	}
}
