package com.freescale.deadlockpreventer.stateeditor.commands;


public class RootChangeLayoutCommand extends AbstractLayoutCommand {

	public void execute() {
		model.setLayout(layout);
	}
}