package com.freescale.deadlockpreventer.stateeditor.part;

import org.eclipse.draw2d.IFigure;

import com.freescale.deadlockpreventer.stateeditor.figure.ReferenceLockFigure;


public class ReferenceLockPart extends StateAbstractEditPart {
	@Override
	protected IFigure createFigure() {
		IFigure figure = new ReferenceLockFigure();
		return figure;
	}
}