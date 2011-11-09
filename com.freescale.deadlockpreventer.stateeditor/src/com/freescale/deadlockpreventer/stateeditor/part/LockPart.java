package com.freescale.deadlockpreventer.stateeditor.part;

import java.util.List;

import org.eclipse.draw2d.IFigure;
import org.eclipse.gef.EditPolicy;

import com.freescale.deadlockpreventer.stateeditor.editpolicies.StateEditLayoutPolicy;
import com.freescale.deadlockpreventer.stateeditor.figure.LockFigure;
import com.freescale.deadlockpreventer.stateeditor.model.LockNode;
import com.freescale.deadlockpreventer.stateeditor.model.Node;


public class LockPart extends StateAbstractEditPart {
	@Override
	protected IFigure createFigure() {
		IFigure figure = new LockFigure();
		return figure;
	}

	@Override
	protected void createEditPolicies() {
		installEditPolicy(EditPolicy.LAYOUT_ROLE, new StateEditLayoutPolicy());
	}

	protected void refreshVisuals() {
		LockFigure figure = (LockFigure) getFigure();
		LockNode model = (LockNode) getModel();
		figure.setName(model.getName());
		figure.setLayout(model.getLayout());
	}

	public List<Node> getModelChildren() {
		return ((LockNode)getModel()).getChildrenArray();
	}
}