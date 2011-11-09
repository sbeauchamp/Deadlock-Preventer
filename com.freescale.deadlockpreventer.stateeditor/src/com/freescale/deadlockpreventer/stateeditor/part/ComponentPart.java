package com.freescale.deadlockpreventer.stateeditor.part;

import org.eclipse.draw2d.IFigure;

import com.freescale.deadlockpreventer.stateeditor.figure.ComponentFigure;


public class ComponentPart extends StateAbstractEditPart {
	@Override
	protected IFigure createFigure() {
		IFigure figure = new ComponentFigure();
		return figure;
	}
}