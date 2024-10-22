package org.jkiss.dbeaver.ext.postgresql.tools.fdw;

import org.eclipse.ui.IWorkbenchPart;
import org.eclipse.ui.IWorkbenchWindow;
import org.jkiss.dbeaver.DBException;
import org.jkiss.dbeaver.ext.postgresql.model.PostgreDatabase;
import org.jkiss.dbeaver.ext.postgresql.model.PostgreObject;
import org.jkiss.dbeaver.model.struct.DBSObject;
import org.jkiss.dbeaver.ui.dialogs.ActiveWizardDialog;
import org.jkiss.dbeaver.ui.tools.IUserInterfaceTool;

import java.util.Collection;

public class PostgreFDWConfigTool implements IUserInterfaceTool
{
    @Override
    public void execute(IWorkbenchWindow window, IWorkbenchPart activePart, Collection<DBSObject> objects) throws DBException
    {
        for (DBSObject object : objects) {
            PostgreDatabase database;
            if (object instanceof PostgreObject) {
                database = ((PostgreObject) object).getDatabase();
            } else {
                continue;
            }
            ActiveWizardDialog dialog = new ActiveWizardDialog(
                window,
                new PostgreFDWConfigWizard(database));
            dialog.setFinishButtonLabel("Install");
            dialog.open();
        }
    }
}
