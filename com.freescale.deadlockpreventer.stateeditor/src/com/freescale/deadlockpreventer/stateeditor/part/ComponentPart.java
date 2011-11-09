package com.freescale.deadlockpreventer.stateeditor.part;

import java.util.List;

import org.eclipse.draw2d.IFigure;
import org.eclipse.gef.EditPolicy;

import com.freescale.deadlockpreventer.stateeditor.editpolicies.StateEditLayoutPolicy;
import com.freescale.deadlockpreventer.stateeditor.figure.ComponentFigure;
import com.freescale.deadlockpreventer.stateeditor.model.ComponentNode;
import com.freescale.deadlockpreventer.stateeditor.model.Node;


public class ComponentPart extends StateAbstractEditPart {
	@Override
	protected IFigure createFigure() {
		IFigure figure = new ComponentFigure();
		return figure;
	}

	@Override
	protected void createEditPolicies() {
		installEditPolicy(EditPolicy.LAYOUT_ROLE, new StateEditLayoutPolicy());
	}

	protected void refreshVisuals() {
		ComponentFigure figure = (ComponentFigure) getFigure();
		ComponentNode model = (ComponentNode) getModel();
		figure.setName(model.getName());
		figure.setLayout(model.getLayout());
	}

	public List<Node> getModelChildren() {
		return ((ComponentNode)getModel()).getChildrenArray();
	}
}