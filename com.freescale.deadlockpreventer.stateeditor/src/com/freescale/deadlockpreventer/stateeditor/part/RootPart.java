package com.freescale.deadlockpreventer.stateeditor.part;

import org.eclipse.draw2d.IFigure;

import com.freescale.deadlockpreventer.stateeditor.figure.RootFigure;


public class RootPart extends StateAbstractEditPart {
	@Override
	protected IFigure createFigure() {
		IFigure figure = new RootFigure();
		return figure;
	}
}