package com.freescale.deadlockpreventer.stateeditor.commands;


public class ComponentChangeLayoutCommand extends AbstractLayoutCommand {

	public void execute() {
		model.setLayout(layout);
	}

}