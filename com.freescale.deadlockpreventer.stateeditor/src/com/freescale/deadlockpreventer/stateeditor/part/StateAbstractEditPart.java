package com.freescale.deadlockpreventer.stateeditor.part;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.List;

import org.eclipse.gef.EditPolicy;
import org.eclipse.gef.editparts.AbstractGraphicalEditPart;

import com.freescale.deadlockpreventer.stateeditor.editpolicies.StateEditLayoutPolicy;
import com.freescale.deadlockpreventer.stateeditor.figure.StateAbstractFigure;
import com.freescale.deadlockpreventer.stateeditor.model.Node;

public abstract class StateAbstractEditPart extends AbstractGraphicalEditPart implements
		PropertyChangeListener {
	public void activate() {
		super.activate();
		((Node) getModel()).addPropertyChangeListener(this);
	}

	@Override
	protected void createEditPolicies() {
		installEditPolicy(EditPolicy.LAYOUT_ROLE, new StateEditLayoutPolicy());
	}

	public void deactivate() {
		super.deactivate();
		((Node) getModel()).removePropertyChangeListener(this);
	}

	@Override
	public void propertyChange(PropertyChangeEvent evt) {
		if (evt.getPropertyName().equals(Node.PROPERTY_LAYOUT))
			refreshVisuals();
	}

	protected void refreshVisuals() {
		StateAbstractFigure figure = (StateAbstractFigure) getFigure();
		Node model = (Node) getModel();
		figure.setName(model.getName());
		figure.setLayout(model.getLayout());
	}

	public List<Node> getModelChildren() {
		return ((Node)getModel()).getChildrenArray();
	}
}