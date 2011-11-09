package com.freescale.deadlockpreventer.stateeditor.figure;

import org.eclipse.draw2d.ColorConstants;
import org.eclipse.draw2d.Graphics;
import org.eclipse.draw2d.LineBorder;
import org.eclipse.draw2d.ToolbarLayout;
import org.eclipse.draw2d.XYLayout;
import org.eclipse.draw2d.geometry.Rectangle;

public class ReferenceLockFigure extends StateAbstractFigure {

	public ReferenceLockFigure() {
		XYLayout layout = new XYLayout();
		setLayoutManager(layout);
		labelName.setForegroundColor(ColorConstants.black);
		add(labelName, ToolbarLayout.ALIGN_CENTER);
		setConstraint(labelName, new Rectangle(5, 5, -1, -1));
		setBackgroundColor(ColorConstants.lightGray);
		setBorder(new LineBorder(ColorConstants.black, 1, Graphics.LINE_DOT));
		setOpaque(true);
	}
}
