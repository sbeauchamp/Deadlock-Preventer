package com.freescale.deadlockpreventer.stateeditor.commands;


public class ReferenceLockChangeLayoutCommand extends AbstractLayoutCommand {

	public void execute() {
		model.setLayout(layout);
	}
}