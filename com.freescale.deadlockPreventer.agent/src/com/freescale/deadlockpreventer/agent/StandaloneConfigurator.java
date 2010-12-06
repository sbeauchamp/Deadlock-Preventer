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

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;

import org.eclipse.core.runtime.Platform;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.ModifyEvent;
import org.eclipse.swt.events.ModifyListener;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.DirectoryDialog;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Text;

public class StandaloneConfigurator implements IConfigurator {

	IAgent agent;
	private Button launch;
	private Button browse;
	private Text executablePath;
	private boolean launching = false;
	private ArrayList<String> previousLines;

	@Override
	public void initialize(IAgent agent) {
		this.agent = agent;
	}

	@Override
	public String getName() {
		return "External Eclipse layout";
	}

	@Override
	public void createPartControl(Composite parent) {
		createLauncherPart(parent);
	}

	private void createLauncherPart(Composite group) {
		group.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
		GridLayout layout = new GridLayout(2, false);
		group.setLayout(layout);
		
		executablePath = new Text(group, SWT.BORDER);
		executablePath.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
		executablePath.setText(agent.getPref(IAgent.PREF_INSTALLATION_DIR, new String()));
		executablePath.addModifyListener(new ModifyListener() {
			public void modifyText(ModifyEvent e) {
				File eclipseFile = getEclipsePath();
				launch.setEnabled(eclipseFile.exists());
			}
		});
		
		browse = new Button(group, SWT.PUSH);
		browse.setText("Browse...");
		browse.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, false, false));
		((GridData)browse.getLayoutData()).widthHint = 80;
		browse.addSelectionListener(new SelectionAdapter() {
			public void widgetSelected(SelectionEvent e) {
				doBrowse();
			}
		});

		launch = new Button(group, SWT.PUSH);
		launch.setText("Launch");
		launch.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, false, false));
		((GridData)launch.getLayoutData()).widthHint = 80;
		launch.addSelectionListener(new SelectionAdapter() {
			public void widgetSelected(SelectionEvent e) {
				doLaunch();
			}
		});
		launch.setEnabled(getEclipsePath().exists());
		launch.getShell().setDefaultButton(launch);	
	}
	
	private void doLaunch() {
		if (launching == false) {
			File eclipseFile = getEclipsePath();
			if (!eclipseFile.exists()) {
				showFileLocationErrorDialog();
				return;
			}
			
			agent.resetOutput();
			if (!configureINIFile(eclipseFile))
				return;
			try {
				launch.setEnabled(false);
				launching = true;
				final Process process = Runtime.getRuntime().exec(eclipseFile.getAbsolutePath());
				
				Thread readerThread = new Thread(new ReaderThread(process.getInputStream()));
				readerThread.start();
				readerThread = new Thread(new ReaderThread(process.getErrorStream()));
				readerThread.start();
				
				Thread terminationThread = new Thread(new Runnable() {
					@Override
					public void run() {
						try {
							process.waitFor();
						} catch (InterruptedException e) {
							e.printStackTrace();
						}
						Display.getDefault().asyncExec(new Runnable() {
							public void run() {
								processCompleted();
							}
						});
					}
				});
				terminationThread.start();
			} catch (IOException e1) {
				launch.setEnabled(getEclipsePath().exists());
				launching = false;
				e1.printStackTrace();
			}
		}
	}

	private File getEclipsePath() {
		File directory = new File(executablePath.getText());
		File eclipseDirectory = new File(directory, "eclipse");
		File eclipseFile;
		if (Platform.getOS().equals(Platform.OS_WIN32))
			eclipseFile = new File(eclipseDirectory, "eclipse.exe");
		else
			eclipseFile = new File(eclipseDirectory, "eclipse");
		return eclipseFile;
	}

	protected void processCompleted() {
		if (!launch.isDisposed())
			launch.setEnabled(getEclipsePath().exists());
		launching = false;
		writeINIFile(previousLines);
	}

	private boolean configureINIFile(File eclipseFile) {
		previousLines = readINIFile();
		if (previousLines != null) {
			ArrayList<String> newContent = new ArrayList<String>();
			
			for (String line : previousLines) {
				if (line.startsWith("-javaagent:"))
					continue;
				if (line.startsWith("-Xbootclasspath/a"))
					continue;
				if (line.startsWith("-Dcom.freescale.deadlockpreventer."))
					continue;
				newContent.add(line);
				if (line.trim().equals("-vmargs")) {
					newContent.add(agent.getVMArg(IAgent.VM_ARG_AGENT));
					newContent.add(agent.getVMArg(IAgent.VM_ARG_BOOT_CLASSPATH));
					newContent.add(agent.getVMArg(IAgent.VM_ARG_BOOT_SERVER_PORT));
					String additional = agent.getVMArg(IAgent.VM_ADDITIONAL_ARGUMENTS);
					if (additional != null)
						newContent.add(additional);
				}
			}
			
			return writeINIFile(newContent);
		}
		MessageDialog.openError(agent.getSite().getShell(), "Error", "Deadlock preventer activation failed: eclipse.ini not found.");
		return false;
	}

	private boolean writeINIFile(ArrayList<String> newContent) {
		File eclipseINIFile = new File(getEclipsePath().getParent(), "eclipse.ini");
		if (eclipseINIFile.exists())
			eclipseINIFile.delete();
		FileOutputStream stream = null;
		try {
			String newLine = System.getProperty("line.separator");
			stream = new FileOutputStream(eclipseINIFile);
			StringBuffer output = new StringBuffer();
			for (String str : newContent)
				output.append(str + newLine);
			stream.write(output.toString().getBytes());
			return true;
		} catch (FileNotFoundException e) {
		} catch (IOException e) {
		} finally {
			if (stream != null) {
				try {
					stream.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}
		return false;
	}

	private ArrayList<String> readINIFile() {
		File eclipseINIFile = new File(getEclipsePath().getParent(), "eclipse.ini");
		try {
			FileReader reader = new FileReader(eclipseINIFile);
			try {
				ArrayList<String> content = new ArrayList<String>();
				BufferedReader buf = new BufferedReader(reader);
				String line = buf.readLine();
				while (line != null) {
					content.add(line);
					line = buf.readLine();
				}
				return content;
			} catch (IOException e) {
				e.printStackTrace();
			} finally {
				try {
					reader.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		} catch (FileNotFoundException e1) {
			e1.printStackTrace();
		}
		return null;
	}

	private void doBrowse() {
		DirectoryDialog dialog = new DirectoryDialog (agent.getSite().getShell());
		dialog.setMessage("Select the location of the CodeWarrior Studio installation");
		String path = dialog.open();
		if (path != null) {
			// massage the path
			File directory = new File(path);
			File eclipseDirectory = new File(directory, "eclipse");
			if (eclipseDirectory.exists())
				directory = directory.getParentFile();
			else {
				showFileLocationErrorDialog();
				return;
			}
			executablePath.setText(directory.getAbsolutePath());
			agent.setPref(IAgent.PREF_INSTALLATION_DIR, directory.getAbsolutePath());
		}
	}

	private void showFileLocationErrorDialog() {
		MessageDialog.openError(agent.getSite().getShell(), "Invalid location", "The CodeWarrior Studio installation directory must be selected (containing the 'eclipse' directory).");
	}

	class ReaderThread implements Runnable {
		
		InputStream stream;

		public ReaderThread(InputStream inputStream) {
			stream = inputStream;
		}

		public void run() {
			BufferedReader reader = new BufferedReader(new InputStreamReader(stream));
			try {
				String line = reader.readLine();
				while (line != null) {
					final String copy = line;
					Display.getDefault().asyncExec(new Runnable() {
						public void run() {
							agent.output("\n" + copy);
						}
					});
					reader.readLine();
				}
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}
}
