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

import java.io.CharArrayWriter;
import java.io.IOException;

import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.jface.viewers.CellLabelProvider;
import org.eclipse.jface.viewers.ColumnViewerToolTipSupport;
import org.eclipse.jface.viewers.IStructuredContentProvider;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.ITreeContentProvider;
import org.eclipse.jface.viewers.TableViewer;
import org.eclipse.jface.viewers.TableViewerColumn;
import org.eclipse.jface.viewers.Viewer;
import org.eclipse.jface.viewers.ViewerCell;
import org.eclipse.jface.viewers.ViewerComparator;
import org.eclipse.jface.window.ToolTip;
import org.eclipse.swt.SWT;
import org.eclipse.swt.dnd.Clipboard;
import org.eclipse.swt.dnd.TextTransfer;
import org.eclipse.swt.dnd.Transfer;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.TableColumn;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.swt.IFocusService;

import com.freescale.deadlockpreventer.ILock;
import com.freescale.deadlockpreventer.Logger;
import com.freescale.deadlockpreventer.QueryService.ITransaction;

public class StatisticsDialog extends Dialog {

	static public class Row {
		
		public Row(int index, ILock lock) {
			this.index = index;
			id = lock.getID();
			precedentsCount = lock.getPrecedents().length;
			folllowersCount = lock.getFollowers().length;
			location = lock.getStackTrace().length > 0 ? lock.getStackTrace()[0]:"";
		}
		int index;
		String id;
		int precedentsCount;
		int folllowersCount;
		String location;
	}

	public static Row[] convert(int startIndex, ILock[] tmp) {
		Row[] result = new Row[tmp.length];
		for (int i = 0; i < tmp.length; i++)
			result[i] = new Row(startIndex + i, tmp[i]);
		return result;
	}

	Row[] locks;
	TableViewer viewer;
	TableViewerComparator comparator;
	ITransaction transaction;
	static StatisticsDialog sInstance = null;

	protected StatisticsDialog(Shell parentShell, Row[] locks, ITransaction transaction) {
		super(parentShell);
		this.locks = locks;
		this.transaction = transaction;
		sInstance = this;
	}

	@Override
	protected void configureShell(Shell newShell) {
		super.configureShell(newShell);
		newShell.setText("Synchronization Primitives");
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
	public boolean close() {
		transaction.close();
		sInstance = null;
		return super.close();
	}

	@Override
	protected Control createDialogArea(Composite parent) {
		parent.setLayout(new GridLayout(2, false));

		viewer = new TableViewer(parent, SWT.BORDER | SWT.MULTI | SWT.FULL_SELECTION);
		GridData layoutData = new GridData(SWT.FILL, SWT.FILL, true, true);
		layoutData.horizontalSpan = 2;
		viewer.getTable().setLayoutData(
				layoutData);
		viewer.setContentProvider(new ViewContentProvider());
        ColumnViewerToolTipSupport.enableFor(viewer, ToolTip.NO_RECREATE);
		viewer.setInput(locks);

		IFocusService service = (IFocusService) PlatformUI.getWorkbench().getService(IFocusService.class);

		service.addFocusTracker(viewer.getTable(), StatisticsDialog.class.getPackage().getName() + ".table");
		
		viewer.setLabelProvider(new CellLabelProvider() {
			@Override
			public void update(ViewerCell cell) {
				Row element = (Row) cell.getElement();
				if (cell.getColumnIndex() == 0)
					cell.setText(element.id);
				if (cell.getColumnIndex() == 1)
					cell.setText(Integer.toString(element.folllowersCount));
				if (cell.getColumnIndex() == 2)
					cell.setText(Integer.toString(element.precedentsCount));
				if (cell.getColumnIndex() == 3)
					cell.setText(element.location);
			}

			public String getToolTipText(Object element) {
				Row row = (Row) element;
				ILock[] locks = transaction.getLocks(row.index, row.index + 1);
				CharArrayWriter writer = new CharArrayWriter();
				Logger.dumpLockInformation(locks, writer);
				return writer.toString();
			}
            public Point getToolTipShift(Object object) {
            	return new Point(5, 5);
            }
            
            public int getToolTipDisplayDelayTime(Object object) {
            	return 2000;
            }
            	
            public int getToolTipTimeDisplayed(Object object) {
            	return 5000;
            }
		});

		createTableViewerColumn("Lock", 200, 0);
		createTableViewerColumn("Followers", 70, 1);
		createTableViewerColumn("Precedents", 70, 2);
		createTableViewerColumn("Location", 250, 3);

		viewer.getTable().setHeaderVisible(true);
		viewer.getTable().setLinesVisible(true);

		comparator = new TableViewerComparator();
		viewer.setComparator(comparator);
		
		Button button = new Button(parent, SWT.PUSH);
		button.setText("Export...");
		layoutData = new GridData(SWT.BEGINNING, SWT.TOP, false, false);
		layoutData.widthHint = 80;
		button.setLayoutData(layoutData);
		button.addSelectionListener( new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				StatisticsUtil.export(transaction);			
			}
		});
		
		Label label = new Label(parent, 0);
		label.setText("Total locks: " + locks.length);
		label.setLayoutData(new GridData(SWT.END, SWT.TOP, false, false));

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
		column.addSelectionListener(getSelectionAdapter(column, colNumber));
		
		viewerColumn.setLabelProvider(new CellLabelProvider() {
			@Override
			public void update(ViewerCell cell) {
				Row element = (Row) cell.getElement();
				switch(colNumber) {
				case 0:
					cell.setText(element.id);
					break;
				case 1:
					cell.setText(Integer.toString(element.folllowersCount));
					break;
				case 2:
					cell.setText(Integer.toString(element.precedentsCount));
					break;
				case 3:
					cell.setText(element.location); 
					break;
				}
			}
			
            public String getToolTipText(Object element) {
				Row row = (Row) element;
        		ILock[] locks = transaction.getLocks(row.index, row.index + 1);
        		CharArrayWriter writer = new CharArrayWriter(); 
        		try {
					for (String stack : locks[0].getStackTrace()) {
						writer.write(stack + "\n");
					}
				} catch (IOException e) {
				}
        		return writer.toString();
            }
		});
		return viewerColumn;

	}

	private SelectionAdapter getSelectionAdapter(final TableColumn column,
			final int index) {
		SelectionAdapter selectionAdapter = new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				comparator.setColumn(index);
				int dir = viewer.getTable().getSortDirection();
				if (viewer.getTable().getSortColumn() == column) {
					dir = dir == SWT.UP ? SWT.DOWN : SWT.UP;
				} else {

					dir = SWT.DOWN;
				}
				viewer.getTable().setSortDirection(dir);
				viewer.getTable().setSortColumn(column);
				viewer.refresh();
			}
		};
		return selectionAdapter;
	}

	public class TableViewerComparator extends ViewerComparator {
		private int propertyIndex;
		private static final int DESCENDING = 1;
		private int direction = DESCENDING;

		public TableViewerComparator() {
			this.propertyIndex = 0;
			direction = DESCENDING;
		}

		public void setColumn(int column) {
			if (column == this.propertyIndex) {
				// Same column as last sort; toggle the direction
				direction = 1 - direction;
			} else {
				// New column; do an ascending sort
				this.propertyIndex = column;
				direction = DESCENDING;
			}
		}

		@Override
		public int compare(Viewer viewer, Object e1, Object e2) {
			Row p1 = (Row) e1;
			Row p2 = (Row) e2;
			int rc = 0;
			switch (propertyIndex) {
			case 0:
				rc = p1.id.compareTo(p2.id);
				break;
			case 1:
				rc = p1.folllowersCount - p2.folllowersCount;
				break;
			case 2:
				rc = p1.precedentsCount - p2.precedentsCount;
				break;
			case 3:
				rc = p1.location.compareTo(p2.location);
				break;
			default:
				rc = 0;
			}
			// If descending order, flip the direction
			if (direction == DESCENDING) {
				rc = -rc;
			}
			return rc;
		}

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

	public static void copyCurrentRow() {
		if (sInstance != null) {
			sInstance.copySelection();
		}
	}

	private void copySelection() {
		CharArrayWriter writer = new CharArrayWriter(); 
   		for (Object selectedRow : ((IStructuredSelection) viewer.getSelection()).toList()) {
   			Row row = (Row) selectedRow;
   			ILock[] locks = transaction.getLocks(row.index, row.index + 1);
   			Logger.dumpLockInformation(locks, writer);
		}
		Clipboard cb = new Clipboard(Display.getDefault());
		TextTransfer textTransfer = TextTransfer.getInstance();
		cb.setContents(new Object[] { writer.toString().replace('\n', System.getProperty("line.separator").charAt(0)) },
				new Transfer[] { textTransfer });
	}
}
