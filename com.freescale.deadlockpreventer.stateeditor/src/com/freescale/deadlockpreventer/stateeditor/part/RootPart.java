package com.freescale.deadlockpreventer.stateeditor.part;

import java.util.List;

import org.eclipse.draw2d.IFigure;
import org.eclipse.gef.EditPolicy;

import com.freescale.deadlockpreventer.stateeditor.editpolicies.StateEditLayoutPolicy;
import com.freescale.deadlockpreventer.stateeditor.figure.RootFigure;
import com.freescale.deadlockpreventer.stateeditor.model.Node;
import com.freescale.deadlockpreventer.stateeditor.model.RootNode;


public class RootPart extends StateAbstractEditPart {
	@Override
	protected IFigure createFigure() {
		IFigure figure = new RootFigure();
		return figure;
	}

	@Override
	protected void createEditPolicies() {
		installEditPolicy(EditPolicy.LAYOUT_ROLE, new StateEditLayoutPolicy());
	}

	protected void refreshVisuals() {
		RootFigure figure = (RootFigure) getFigure();
		RootNode model = (RootNode) getModel();
		figure.setName(model.getName());
	}

	public List<Node> getModelChildren() {
		return ((RootNode)getModel()).getChildrenArray();
	}
}