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
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;

import com.freescale.deadlockpreventer.agent.LauncherView.Conflict;

public class ConflictDialog extends Dialog {
	Conflict conflict;
	
	protected ConflictDialog(Shell parentShell, Conflict conflict) {
		super(parentShell);
		this.conflict = conflict;
	}

	protected int getShellStyle() {
		return SWT.CLOSE|SWT.MIN|SWT.MAX|SWT.RESIZE;
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
		parent.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));

		Text text = new Text(parent, SWT.READ_ONLY | SWT.BORDER | SWT.V_SCROLL | SWT.H_SCROLL);
		text.setBackground(Display.getDefault().getSystemColor(SWT.COLOR_WHITE));
		text.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
		text.setText(conflict.message);
		
		return super.createDialogArea(parent);
	}

}
