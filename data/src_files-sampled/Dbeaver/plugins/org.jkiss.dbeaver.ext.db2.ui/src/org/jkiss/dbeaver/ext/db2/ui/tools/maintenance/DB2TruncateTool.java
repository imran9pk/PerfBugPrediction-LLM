package org.jkiss.dbeaver.ext.db2.ui.tools.maintenance;

import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.ui.IWorkbenchPart;
import org.eclipse.ui.IWorkbenchWindow;
import org.jkiss.dbeaver.DBException;
import org.jkiss.dbeaver.ext.db2.tasks.DB2SQLTasks;
import org.jkiss.dbeaver.model.struct.DBSObject;
import org.jkiss.dbeaver.tasks.ui.wizard.TaskConfigurationWizardDialog;
import org.jkiss.dbeaver.ui.navigator.NavigatorUtils;
import org.jkiss.dbeaver.ui.tools.IUserInterfaceTool;

import java.util.Collection;

public class DB2TruncateTool implements IUserInterfaceTool {

    @Override
    public void execute(IWorkbenchWindow window, IWorkbenchPart activePart, Collection<DBSObject> objects) throws DBException {
        TaskConfigurationWizardDialog.openNewTaskDialog(
                window,
                NavigatorUtils.getSelectedProject(),
                DB2SQLTasks.TASK_TABLE_TRUNCATE,
                new StructuredSelection(objects.toArray()));
    }

}
