package com.freescale.deadlockpreventer.stateeditor.figure;

import org.eclipse.draw2d.ColorConstants;
import org.eclipse.draw2d.LineBorder;
import org.eclipse.draw2d.ToolbarLayout;
import org.eclipse.draw2d.XYLayout;
import org.eclipse.draw2d.geometry.Rectangle;
import org.eclipse.swt.graphics.Color;

public class ComponentFigure extends StateAbstractFigure {

	private static final Color BG_COLOR = new Color(null, 145, 179, 197);

	public ComponentFigure() {
		XYLayout layout = new XYLayout();
		setLayoutManager(layout);
		labelName.setForegroundColor(ColorConstants.darkGray);
		add(labelName, ToolbarLayout.ALIGN_CENTER);
		setConstraint(labelName, new Rectangle(5, 5, -1, -1));
		/** Just for Fun :) **/
		setForegroundColor(ColorConstants.black);
		setBackgroundColor(BG_COLOR);
		setBorder(new LineBorder(1));
		setOpaque(true);
	}
}
