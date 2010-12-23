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

import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.jface.viewers.CellLabelProvider;
import org.eclipse.jface.viewers.IStructuredContentProvider;
import org.eclipse.jface.viewers.ITreeContentProvider;
import org.eclipse.jface.viewers.TableViewer;
import org.eclipse.jface.viewers.TableViewerColumn;
import org.eclipse.jface.viewers.Viewer;
import org.eclipse.jface.viewers.ViewerCell;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.TableColumn;

import com.freescale.deadlockpreventer.ILock;

public class StatisticsDialog extends Dialog {
	ILock[] locks;
	TableViewer viewer;

	protected StatisticsDialog(Shell parentShell, ILock[] locks) {
		super(parentShell);
		this.locks = locks;
	}

	protected int getShellStyle() {
		return SWT.CLOSE | SWT.MIN | SWT.MAX | SWT.RESIZE;
	}

	@Override
	protected Point getInitialSize() {
		Point pt = super.getInitialSize();
		pt.x = Math.min(pt.x, 600);
		pt.y = Math.min(pt.y, 600);
		return pt;
	}

	protected void createButtonsForButtonBar(Composite parent) {
		// create OK and Cancel buttons by default
		createButton(parent, IDialogConstants.OK_ID, IDialogConstants.OK_LABEL,
				true);
	}

	@Override
	protected Control createDialogArea(Composite parent) {
		parent.setLayout(new GridLayout(1, false));

		viewer = new TableViewer(parent, SWT.BORDER);
		viewer.getTable().setLayoutData(
				new GridData(SWT.FILL, SWT.FILL, true, true));
		viewer.setContentProvider(new ViewContentProvider());
		viewer.setInput(locks);

		createTableViewerColumn("Lock", 200, 0);
		createTableViewerColumn("Followers", 70, 1);
		createTableViewerColumn("Precedents", 70, 2);
		createTableViewerColumn("Location", 250, 2);

		viewer.setLabelProvider(new CellLabelProvider() {
			@Override
			public void update(ViewerCell cell) {
				ILock element = (ILock) cell.getElement();
				if (cell.getColumnIndex() == 0)
					cell.setText(element.getID());
				if (cell.getColumnIndex() == 1)
					cell.setText(Integer.toString(element.getFollowers().length));
				if (cell.getColumnIndex() == 2)
					cell.setText(Integer.toString(element.getPrecedents().length));
				if (cell.getColumnIndex() == 3)
					cell.setText(element.getStackTrace().length > 0 ? element.getStackTrace()[0]:new String());
			}
		});
		viewer.getTable().setHeaderVisible(true);
		viewer.getTable().setLinesVisible(true);

		return super.createDialogArea(parent);
	}

	private TableViewerColumn createTableViewerColumn(String title, int bound,
			final int colNumber) {
		final TableViewerColumn viewerColumn = new TableViewerColumn(viewer,
				SWT.NONE);
		final TableColumn column = viewerColumn.getColumn();
		column.setText(title);
		column.setWidth(bound);
		column.setResizable(true);
		column.setMoveable(true);
		return viewerColumn;

	}

	class ViewContentProvider implements IStructuredContentProvider,
			ITreeContentProvider {

		public void inputChanged(Viewer v, Object oldInput, Object newInput) {
		}

		public void dispose() {
		}

		public Object[] getElements(Object parent) {
			return getChildren(parent);
		}

		public Object getParent(Object child) {
			if (child instanceof ILock)
				return locks;
			return null;
		}

		public Object[] getChildren(Object parent) {
			if (parent == locks)
				return locks;
			return new Object[0];
		}

		public boolean hasChildren(Object parent) {
			if (parent == locks)
				return true;
			return false;
		}
	}

}
