package com.freescale.deadlockpreventer.stateeditor;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.ObjectInputStream;
import java.util.ArrayList;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IStorage;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.draw2d.geometry.Rectangle;
import org.eclipse.gef.DefaultEditDomain;
import org.eclipse.gef.GraphicalViewer;
import org.eclipse.gef.editparts.ScalableRootEditPart;
import org.eclipse.gef.editparts.ZoomManager;
import org.eclipse.gef.ui.actions.ZoomInAction;
import org.eclipse.gef.ui.actions.ZoomOutAction;
import org.eclipse.gef.ui.parts.GraphicalEditor;
import org.eclipse.ui.IEditorInput;
import org.eclipse.ui.IFileEditorInput;
import org.eclipse.ui.XMLMemento;

import com.freescale.deadlockpreventer.ILock;
import com.freescale.deadlockpreventer.agent.XMLUtil;
import com.freescale.deadlockpreventer.stateeditor.model.ComponentNode;
import com.freescale.deadlockpreventer.stateeditor.model.LockNode;
import com.freescale.deadlockpreventer.stateeditor.model.Node;
import com.freescale.deadlockpreventer.stateeditor.model.ReferenceLockNode;
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

		IEditorInput input = getEditorInput();
		IFile file = ((IFileEditorInput) input).getFile();
		InputStream contents = null;
		try {
			contents = file.getContents();
			XMLMemento memento = XMLMemento.createReadRoot(new InputStreamReader(contents));
			ILock[] locks = XMLUtil.readLocks(memento);
		} catch (CoreException e) {
			e.printStackTrace();
		} finally {
			if (contents != null)
				try {
					contents.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
		}

		ComponentNode component = new ComponentNode();
		component.setName("org.eclipse");
		component.setLayout(new Rectangle(30, 50, 250, 150));

		LockNode node = new LockNode();
		node.setName("foo lock");
		node.setLayout(new Rectangle(25, 40, 80, 40));

		ReferenceLockNode referenceNode = new ReferenceLockNode();
		referenceNode.setName("reference");
		referenceNode.setLayout(new Rectangle(5, 5, 35, 25));

		node.addChild(referenceNode);

		component.addChild(node);

		root.addChild(component);

		return root;
	}

	protected void configureGraphicalViewer() {
		super.configureGraphicalViewer();
		GraphicalViewer viewer = getGraphicalViewer();
		viewer.setEditPartFactory(new StateEditPartFactory());

		double[] zoomLevels;
		ArrayList<String> zoomContributions;
		ScalableRootEditPart rootEditPart = new ScalableRootEditPart();
		viewer.setRootEditPart(rootEditPart);
		ZoomManager manager = rootEditPart.getZoomManager();
		getActionRegistry().registerAction(new ZoomInAction(manager));
		getActionRegistry().registerAction(new ZoomOutAction(manager));
		zoomLevels = new double[] { 0.25, 0.5, 0.75, 1.0, 1.5, 2.0, 2.5, 3.0,
				4.0, 5.0, 10.0, 20.0 };
		manager.setZoomLevels(zoomLevels);

		zoomContributions = new ArrayList<String>();
		zoomContributions.add(ZoomManager.FIT_ALL);
		zoomContributions.add(ZoomManager.FIT_HEIGHT);
		zoomContributions.add(ZoomManager.FIT_WIDTH);
		manager.setZoomLevelContributions(zoomContributions);
	}

	public Object getAdapter(@SuppressWarnings("rawtypes") Class type) {
		if (type == ZoomManager.class)
			return ((ScalableRootEditPart) getGraphicalViewer()
					.getRootEditPart()).getZoomManager();
		else
			return super.getAdapter(type);
	}

	protected void initializeGraphicalViewer() {
		GraphicalViewer viewer = getGraphicalViewer();
		viewer.setContents(CreateContent());
	}

}
