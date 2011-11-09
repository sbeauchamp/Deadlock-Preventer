package com.freescale.deadlockpreventer.stateeditor.editpolicies;

import org.eclipse.draw2d.geometry.Rectangle;
import org.eclipse.gef.EditPart;
import org.eclipse.gef.commands.Command;
import org.eclipse.gef.editpolicies.XYLayoutEditPolicy;
import org.eclipse.gef.requests.CreateRequest;

import com.freescale.deadlockpreventer.stateeditor.commands.AbstractLayoutCommand;
import com.freescale.deadlockpreventer.stateeditor.commands.ComponentChangeLayoutCommand;
import com.freescale.deadlockpreventer.stateeditor.commands.LockChangeLayoutCommand;
import com.freescale.deadlockpreventer.stateeditor.commands.ReferenceLockChangeLayoutCommand;
import com.freescale.deadlockpreventer.stateeditor.commands.RootChangeLayoutCommand;
import com.freescale.deadlockpreventer.stateeditor.part.ComponentPart;
import com.freescale.deadlockpreventer.stateeditor.part.LockPart;
import com.freescale.deadlockpreventer.stateeditor.part.ReferenceLockPart;
import com.freescale.deadlockpreventer.stateeditor.part.RootPart;

public class StateEditLayoutPolicy extends XYLayoutEditPolicy {
	@Override
	protected Command createChangeConstraintCommand(EditPart child,
			Object constraint) {
		AbstractLayoutCommand command = null;
		if (child instanceof LockPart) {
			command = new LockChangeLayoutCommand();
		} else if (child instanceof ComponentPart) {
			command = new ComponentChangeLayoutCommand();
		} else if (child instanceof RootPart) {
			command = new RootChangeLayoutCommand();
		} else if (child instanceof ReferenceLockPart) {
			command = new ReferenceLockChangeLayoutCommand();
		}
		command.setModel(child.getModel());
		command.setConstraint((Rectangle) constraint);
		return command;
	}

	@Override
	protected Command getCreateCommand(CreateRequest request) {
		// TODO Auto-generated method stub
		return null;
	}
}