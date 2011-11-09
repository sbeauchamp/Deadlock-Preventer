package com.freescale.deadlockpreventer.stateeditor;

import org.eclipse.gef.EditPart;
import org.eclipse.gef.EditPartFactory;
import org.eclipse.gef.editparts.AbstractGraphicalEditPart;

import com.freescale.deadlockpreventer.stateeditor.model.ComponentNode;
import com.freescale.deadlockpreventer.stateeditor.model.LockNode;
import com.freescale.deadlockpreventer.stateeditor.model.ReferenceLockNode;
import com.freescale.deadlockpreventer.stateeditor.model.RootNode;
import com.freescale.deadlockpreventer.stateeditor.part.ComponentPart;
import com.freescale.deadlockpreventer.stateeditor.part.LockPart;
import com.freescale.deadlockpreventer.stateeditor.part.ReferenceLockPart;
import com.freescale.deadlockpreventer.stateeditor.part.RootPart;

public class StateEditPartFactory implements EditPartFactory {
	@Override
	public EditPart createEditPart(EditPart context, Object model) {
		AbstractGraphicalEditPart part = null;
		if (model instanceof LockNode) {
			part = new LockPart();
		} else if (model instanceof ComponentNode) {
			part = new ComponentPart();
		} else if (model instanceof RootNode) {
			part = new RootPart();
		} else if (model instanceof ReferenceLockNode) {
			part = new ReferenceLockPart();
		}
		part.setModel(model);
		return part;
	}
}