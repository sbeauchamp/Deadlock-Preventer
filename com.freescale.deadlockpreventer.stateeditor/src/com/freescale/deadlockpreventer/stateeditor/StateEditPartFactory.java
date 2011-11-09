package com.freescale.deadlockpreventer.stateeditor;

import org.eclipse.gef.EditPart;
import org.eclipse.gef.EditPartFactory;
import org.eclipse.gef.editparts.AbstractGraphicalEditPart;

import com.freescale.deadlockpreventer.stateeditor.model.ComponentNode;
import com.freescale.deadlockpreventer.stateeditor.model.LockNode;
import com.freescale.deadlockpreventer.stateeditor.model.RootNode;
import com.freescale.deadlockpreventer.stateeditor.part.ComponentPart;
import com.freescale.deadlockpreventer.stateeditor.part.LockPart;
import com.freescale.deadlockpreventer.stateeditor.part.RootPart;

public class StateEditPartFactory implements EditPartFactory {
	@Override
	public EditPart createEditPart(EditPart context, Object model) {
		AbstractGraphicalEditPart part = null;
		if (model instanceof LockNode) {
			part = new LockPart();
		}
		if (model instanceof ComponentNode) {
			part = new ComponentPart();
		}
		if (model instanceof RootNode) {
			part = new RootPart();
		}
		part.setModel(model);
		return part;
	}
}