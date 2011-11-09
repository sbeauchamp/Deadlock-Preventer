package com.freescale.deadlockpreventer.stateeditor.figure;

import org.eclipse.draw2d.Figure;
import org.eclipse.draw2d.Label;
import org.eclipse.draw2d.geometry.Rectangle;

public class StateAbstractFigure extends Figure {

	protected Label labelName = new Label();

	public StateAbstractFigure() {
		super();
	}

	public void setName(String text) {
		labelName.setText(text);
	}

	public void setLayout(Rectangle rect) {
		getParent().setConstraint(this, rect);
	}

}