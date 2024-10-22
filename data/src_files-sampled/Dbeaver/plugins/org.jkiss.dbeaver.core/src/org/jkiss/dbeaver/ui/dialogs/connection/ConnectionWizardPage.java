package org.jkiss.dbeaver.ui.dialogs.connection;

import org.jkiss.dbeaver.model.DBPDataSourceContainer;
import org.jkiss.dbeaver.ui.dialogs.ActiveWizardPage;

public abstract class ConnectionWizardPage extends ActiveWizardPage<ConnectionWizard> {

    protected ConnectionWizardPage(String pageName) {
        super(pageName);
    }

    public abstract void saveSettings(DBPDataSourceContainer dataSourceDescriptor);

}