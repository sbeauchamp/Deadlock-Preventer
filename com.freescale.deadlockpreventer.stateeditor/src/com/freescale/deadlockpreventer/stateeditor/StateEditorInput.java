package com.freescale.deadlockpreventer.stateeditor;

import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.ui.IEditorInput;
import org.eclipse.ui.IPersistableElement;

public class StateEditorInput implements IEditorInput {
	public String name = null;

	public StateEditorInput(String name) {
		this.name = name;
	}

	@Override
	public boolean exists() {
		return (this.name != null);
	}

	public boolean equals(Object o) {
		if (!(o instanceof StateEditorInput))
			return false;
		return ((StateEditorInput) o).getName().equals(getName());
	}

	@Override
	public ImageDescriptor getImageDescriptor() {
		return ImageDescriptor.getMissingImageDescriptor();
	}

	@Override
	public String getName() {
		return this.name;
	}

	@Override
	public IPersistableElement getPersistable() {
		return null;
	}

	@Override
	public Object getAdapter(Class adapter) {
		return null;
	}

	@Override
	public String getToolTipText() {
		return null;
	}
}