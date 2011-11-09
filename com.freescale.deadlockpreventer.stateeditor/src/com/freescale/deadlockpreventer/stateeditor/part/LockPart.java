package com.freescale.deadlockpreventer.stateeditor.part;

import org.eclipse.draw2d.IFigure;

import com.freescale.deadlockpreventer.stateeditor.figure.LockFigure;


public class LockPart extends StateAbstractEditPart {
	@Override
	protected IFigure createFigure() {
		IFigure figure = new LockFigure();
		return figure;
	}
}