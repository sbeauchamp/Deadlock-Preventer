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

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.regex.Pattern;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.preferences.InstanceScope;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.dialogs.ProgressMonitorDialog;
import org.eclipse.jface.operation.IRunnableWithProgress;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.FileDialog;
import org.eclipse.ui.IMemento;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.XMLMemento;

import com.freescale.deadlockpreventer.ILock;
import com.freescale.deadlockpreventer.NetworkServer.IService;
import com.freescale.deadlockpreventer.QueryService;
import com.freescale.deadlockpreventer.QueryService.ITransaction;
import com.freescale.deadlockpreventer.ReportService;
import com.freescale.deadlockpreventer.agent.IAgent.IProcess;
import com.freescale.deadlockpreventer.agent.StatisticsDialog.Row;

public class InstrumentedProcess implements ReportService.IListener, IProcess
{
	private final class DownloadRunnable implements IRunnableWithProgress {
		private final ITransaction[] transactions;
		private final ArrayList<Row> locks;

		private DownloadRunnable(ITransaction[] transactions,
				ArrayList<Row> locks) {
			this.transactions = transactions;
			this.locks = locks;
		}

		@Override
		public void run(IProgressMonitor monitor)
				throws InvocationTargetException, InterruptedException {
			transactions[0] = queryService.createTransaction();
			int count = transactions[0].getLockCount();
			monitor.beginTask("Downloading statistics...", count);
			int index = 0;
			int interval = 100;
			while (index < count) {
				ILock[] tmp = transactions[0].getLocks(index,
						Math.min(index + interval, count));
				monitor.worked(tmp.length);
				locks.addAll(Arrays.asList(StatisticsDialog.convert(index,
						tmp)));
				index += interval;
				if (monitor.isCanceled())
					break;
			}
			monitor.done();
		}
	}

	private String label;
	private String reportKey;
	public String getLabel() {
		return label;
	}

	public void setLabel(String label) {
		this.label = label;
	}

	public String getReportKey() {
		return reportKey;
	}

	public String getQueryKey() {
		return queryKey;
	}

	public void setConflictList(ArrayList<Conflict> conflictList) {
		this.conflictList = conflictList;
	}

	private String queryKey;
	private QueryService queryService;
	private LauncherView launcherView;
	
	public InstrumentedProcess(LauncherView launcherView, String label) {
		this.label = label;
		this.launcherView = launcherView;
	}

	public String toString() {
		return label;
	}
	
	@Override
	public int report(String type, String threadID,
			String conflictThreadID, String lock, String[] lockStack,
			String precedent, String[] precedentStack, String conflict,
			String[] conflictStack, String conflictPrecedent,
			String[] conflictPrecedentStack, String message) {
		final Conflict conflictItem = new Conflict(this, type, threadID, conflictThreadID, 
			lock, lockStack, 
			precedent, precedentStack,
			conflict, conflictStack,
			conflictPrecedent, conflictPrecedentStack, message);
		conflictList.add(conflictItem);
		ConflictHandler handler = new ConflictHandler(launcherView, conflictItem);
		Display.getDefault().syncExec(handler);
		return handler.result;
	}
	
	protected Conflict[] getDisplayedConflicts() {
		String filtersString = InstanceScope.INSTANCE.getNode(Activator.PLUGIN_ID).get(IAgent.PREF_DISPLAY_FILTERS, launcherView.getDefaultFilters());
		
		String[] filters = filtersString.split(";");
		Pattern[] patterns = new Pattern[filters.length];
		for (int i = 0; i < filters.length; i++) {
			patterns[i] = Pattern.compile(filters[i]);
		}
		boolean showWarning = launcherView.shouldDisplayWarning();
		ArrayList<Conflict> list = new ArrayList<Conflict>();
		for (Conflict conflict : conflictList) {
			if (!showWarning && !conflict.isError())
				continue;
			
			boolean passFilters = true;
			for (Pattern pattern : patterns) {
				if (pattern.matcher(conflict.conflict).matches() ||
						pattern.matcher(conflict.precedent).matches()) {
					passFilters = false;
					break;
				}
			}
			if (passFilters)
				list.add(conflict);
		}
		return list.toArray(new Conflict[0]);
	}

	private ArrayList<Conflict> conflictList = new ArrayList<Conflict>();
	
	public void setReportKey(String reportKey) {
		this.reportKey = reportKey;
	}

	public void setQueryKey(String queryKey) {
		this.queryKey = queryKey;
	}

	public void downloadGlobalLockState() {
		FileDialog dialog = new FileDialog(PlatformUI.getWorkbench()
				.getActiveWorkbenchWindow().getShell(), SWT.SAVE);
		dialog.setFileName(label + ".lockState");
		final String filePath = dialog.open();
		if (filePath == null)
			return;
		try {
			new ProgressMonitorDialog(PlatformUI.getWorkbench()
					.getActiveWorkbenchWindow().getShell()).run(true, true,
					new IRunnableWithProgress() {
						@Override
						public void run(IProgressMonitor monitor)
								throws InvocationTargetException,
								InterruptedException {
							XMLMemento root = XMLMemento
									.createWriteRoot("locks");
							ITransaction transaction = null;
							try {
								
								IMemento locksRoot = root.createChild("root");

								HashMap<String, QueryService.IBundleInfo> plugins = new HashMap<String, QueryService.IBundleInfo>();
								
								transaction = queryService.createTransaction();
								if (transaction == null)
									return;
								int count = transaction.getLockCount();
								monitor.beginTask("Saving state...", count);
								int index = 0;
								int interval = 100;
								while (index < count) {
									ILock[] tmp = transaction.getLocks(index,
											Math.min(index + interval, count));
									monitor.worked(tmp.length);
									
									for (int i = 0; i < tmp.length; i++) {
										XMLUtil.write(locksRoot, tmp[i]);
										
										String[] stackTrace = tmp[i].getStackTrace();
										if (stackTrace.length > 0) {
											QueryService.IBundleInfo bundle = transaction.getBundleInfo(tmp[i]);
											if (!plugins.containsKey(bundle.getName()))
												plugins.put(bundle.getName(), bundle);
										}
									}
									index += interval;
									if (monitor.isCanceled())
										break;
								}
								XMLUtil.write(root, plugins.values());
							} finally {
								if (transaction != null)
									transaction.close();
							}
							File file = new File(filePath);
							if (file.exists())
								file.delete();
							try {
								FileWriter writer = new FileWriter(file);
								root.save(writer);
								writer.close();
							} catch (IOException e) {
								e.printStackTrace();
							}
							monitor.done();
						}
					});
		} catch (InvocationTargetException e) {
			e.printStackTrace();
		} catch (InterruptedException e) {
			e.printStackTrace();
		} finally {
		}
	}
	
	public void displayStatistics() {
		final ITransaction[] transactions = new ITransaction[1];
		final ArrayList<StatisticsDialog.Row> locks = downloadLocks(transactions);
		if (locks.size() != 0) {
			StatisticsDialog dialog = new StatisticsDialog(PlatformUI.getWorkbench().getActiveWorkbenchWindow().getShell(), locks.toArray(new StatisticsDialog.Row[0]), transactions[0]);
			dialog.open();
		}
	}

	private ArrayList<StatisticsDialog.Row> downloadLocks(final ITransaction[] transactions) {
		final ArrayList<StatisticsDialog.Row> locks = new ArrayList<StatisticsDialog.Row>();
		if (!queryService.isConnected()) {
			try {
				new ProgressMonitorDialog(PlatformUI.getWorkbench()
						.getActiveWorkbenchWindow().getShell()).run(true,
						true, new IRunnableWithProgress() {
							@Override
							public void run(IProgressMonitor monitor)
									throws InvocationTargetException,
									InterruptedException {
								while (!queryService.isConnected()) {
									if (Display.getDefault()
											.readAndDispatch())
										Thread.sleep(100);
									if (monitor.isCanceled())
										break;
								}

							}
						});
			} catch (InvocationTargetException e) {
				e.printStackTrace();
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
		if (queryService.isConnected()) {
			if (!queryService.isClosed()) {
				try {
					new ProgressMonitorDialog(PlatformUI.getWorkbench()
							.getActiveWorkbenchWindow().getShell()).run(
							true, true, new DownloadRunnable(transactions, locks));
				} catch (InvocationTargetException e) {
					e.printStackTrace();
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			}
			if (locks.size() == 0)
				MessageDialog
						.openError(PlatformUI.getWorkbench()
								.getActiveWorkbenchWindow().getShell(),
								"Can't retrieve process information",
								"Process need to be running to get lock information.");
			else {
				StatisticsDialog dialog = new StatisticsDialog(PlatformUI
						.getWorkbench().getActiveWorkbenchWindow()
						.getShell(),
						locks.toArray(new StatisticsDialog.Row[0]),
						transactions[0]);
				dialog.open();
			}
		}
		return locks;
	}

	public void setQueryService(QueryService queryService) {
		this.queryService = queryService;
	}

	public IService getQueryService() {
		return queryService;
	}

	public ArrayList<Conflict> getConflictList() {
		return conflictList;
	}
}