package org.jkiss.dbeaver.ext.ui.locks.graph;

import org.eclipse.draw2dl.ColorConstants;
import org.eclipse.gef3.ContextMenuProvider;
import org.eclipse.gef3.DefaultEditDomain;
import org.eclipse.gef3.GraphicalViewer;
import org.eclipse.gef3.editparts.FreeformGraphicalRootEditPart;
import org.eclipse.gef3.ui.parts.ScrollingGraphicalViewer;
import org.eclipse.jface.action.IMenuManager;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.ui.part.ViewPart;
import org.jkiss.dbeaver.ext.ui.locks.manage.LockManagerViewer;
import org.jkiss.dbeaver.model.admin.locks.DBAServerLock;
import org.jkiss.dbeaver.model.impl.admin.locks.LockGraph;
import org.jkiss.dbeaver.model.impl.admin.locks.LockGraphManager;

public class LockGraphicalView extends ViewPart {

	private static final LockGraph EMPTY_GRAPH = new LockGraph(null);
	
	private DefaultEditDomain editDomain;

	private GraphicalViewer graphicalViewer;

	
	private final LockGraphManager graphManager;
	private final LockManagerViewer viewer;
	
	public LockGraphicalView(LockManagerViewer viewer) {
		super();
        this.viewer = viewer;
        this.graphManager = viewer.getGraphManager();
	}

	@Override
	public void createPartControl(Composite parent) {
		setEditDomain(new DefaultEditDomain(null));
		setGraphicalViewer(new ScrollingGraphicalViewer());
		getGraphicalViewer().createControl(parent);
		getGraphicalViewer().setRootEditPart(new FreeformGraphicalRootEditPart());
		getGraphicalViewer().setEditPartFactory(new LockGraphEditPartFactory());
		getGraphicalViewer().setContextMenu(new ContextMenuProvider(graphicalViewer){

			@Override
			public void buildContextMenu(IMenuManager menu) {
			    
				menu.add(viewer.getKillAction());
				
			}
			
		});
	}		

	public void drawGraf(DBAServerLock selection)
	{
		if (selection == null) return;
		
		LockGraph g = selection == null ? EMPTY_GRAPH : graphManager.getGraph(selection);
		
		if (g == null) return;
		
getGraphicalViewer().setContents(g);
		getGraphicalViewer().getControl().setBackground(ColorConstants.listBackground);
	}
	
	protected DefaultEditDomain getEditDomain() {
		return this.editDomain;
	}

	protected GraphicalViewer getGraphicalViewer() {
		return this.graphicalViewer;
	}

	protected void setEditDomain(DefaultEditDomain anEditDomain) {
		this.editDomain = anEditDomain;
	}

	@Override
	public void setFocus() {
		getGraphicalViewer().getControl().setFocus();
	}

	protected void setGraphicalViewer(GraphicalViewer viewer) {
		getEditDomain().addViewer(viewer);
		this.graphicalViewer = viewer;
	}

}
