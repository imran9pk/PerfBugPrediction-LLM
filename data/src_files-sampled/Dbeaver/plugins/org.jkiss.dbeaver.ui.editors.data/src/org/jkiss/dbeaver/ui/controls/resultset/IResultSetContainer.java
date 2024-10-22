package org.jkiss.dbeaver.ui.controls.resultset;

import org.jkiss.code.NotNull;
import org.jkiss.code.Nullable;
import org.jkiss.dbeaver.model.DBPContextProvider;
import org.jkiss.dbeaver.model.app.DBPProject;
import org.jkiss.dbeaver.model.data.DBDDataFilter;
import org.jkiss.dbeaver.model.runtime.DBRProgressMonitor;
import org.jkiss.dbeaver.model.struct.DBSDataContainer;

public interface IResultSetContainer extends DBPContextProvider {

    @Nullable
    DBPProject getProject();

    @Nullable
    IResultSetController getResultSetController();

    @Nullable
    DBSDataContainer getDataContainer();

    boolean isReadyToRun();

    void openNewContainer(DBRProgressMonitor monitor, @NotNull DBSDataContainer dataContainer, @NotNull DBDDataFilter newFilter);

    IResultSetDecorator createResultSetDecorator();

}
