package com.freescale.deadlockpreventer.stateeditor.commands;


public class LockChangeLayoutCommand extends AbstractLayoutCommand {

	public void execute() {
		model.setLayout(layout);
	}
}