package com.freescale.deadlockpreventer.stateeditor.commands;

import org.eclipse.draw2d.geometry.Rectangle;
import org.eclipse.gef.commands.Command;

import com.freescale.deadlockpreventer.stateeditor.model.Node;

public abstract class AbstractLayoutCommand extends Command {

	protected Rectangle layout;
	protected Rectangle oldLayout = null;
	protected Node model;

	public void setConstraint(Rectangle rect) {
		this.layout = rect;
	}

	public void setModel(Object model) {
		this.model = (Node) model;
		oldLayout = ((Node) model).getLayout();
	}

	public void undo() {
		if (oldLayout != null)
			model.setLayout(oldLayout);
	}
}