package com.freescale.deadlockpreventer.stateeditor;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.draw2d.geometry.Rectangle;
import org.eclipse.gef.DefaultEditDomain;
import org.eclipse.gef.GraphicalViewer;
import org.eclipse.gef.ui.parts.GraphicalEditor;

import com.freescale.deadlockpreventer.stateeditor.model.ComponentNode;
import com.freescale.deadlockpreventer.stateeditor.model.LockNode;
import com.freescale.deadlockpreventer.stateeditor.model.Node;
import com.freescale.deadlockpreventer.stateeditor.model.RootNode;

public class StateGraphicalEditor extends GraphicalEditor {

	public static final String ID = "com.freescale.deadlockpreventer.stateeditor";
	
	public StateGraphicalEditor() {
		setEditDomain(new DefaultEditDomain(this));
	}

	@Override
	public void doSave(IProgressMonitor monitor) {
	}

	public Node CreateContent() {
		RootNode root = new RootNode();
		root.setName("Root element");
		
		ComponentNode component = new ComponentNode();
		component.setName("org.eclipse");
		component.setLayout(new Rectangle(30, 50, 250, 150));
		
		LockNode node = new LockNode();
		node.setName("foo lock");
		node.setLayout(new Rectangle(25, 40, 60, 40));
		
		component.addChild(node);
		
		root.addChild(component);
		
		return root;
	}

	protected void configureGraphicalViewer() {
		super.configureGraphicalViewer();
		GraphicalViewer viewer = getGraphicalViewer();
		viewer.setEditPartFactory(new StateEditPartFactory());
	}

	protected void initializeGraphicalViewer() {
		GraphicalViewer viewer = getGraphicalViewer();
		viewer.setContents(CreateContent());
	}

}
