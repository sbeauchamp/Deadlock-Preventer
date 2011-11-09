package com.freescale.deadlockpreventer.stateeditor.figure;

import org.eclipse.draw2d.ColorConstants;
import org.eclipse.draw2d.Figure;
import org.eclipse.draw2d.Label;
import org.eclipse.draw2d.LineBorder;
import org.eclipse.draw2d.ToolbarLayout;
import org.eclipse.draw2d.geometry.Rectangle;

public class LockFigure extends Figure {

	private Label labelName = new Label();

	public LockFigure() {
		ToolbarLayout layout = new ToolbarLayout();
		setLayoutManager(layout);
		labelName.setForegroundColor(ColorConstants.black);
		add(labelName, ToolbarLayout.ALIGN_CENTER);
		setBackgroundColor(ColorConstants.lightGray);
		setBorder(new LineBorder(1));
		setOpaque(true);
	}

	public void setLayout(Rectangle rect) {
		getParent().setConstraint(this, rect);
	}

	public void setName(String text) {
		labelName.setText("Lock: " + text);
	}
}
