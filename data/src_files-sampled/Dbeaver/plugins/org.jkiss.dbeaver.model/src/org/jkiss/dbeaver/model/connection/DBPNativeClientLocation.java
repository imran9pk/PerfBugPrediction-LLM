package org.jkiss.dbeaver.model.connection;

import org.jkiss.code.NotNull;
import org.jkiss.dbeaver.DBException;
import org.jkiss.dbeaver.model.DBPNamedObject;
import org.jkiss.dbeaver.model.runtime.DBRProgressMonitor;

import java.io.File;

public interface DBPNativeClientLocation extends DBPNamedObject {

    @NotNull
    File getPath();

    @NotNull
    String getDisplayName();

    boolean validateFilesPresence(DBRProgressMonitor progressMonitor) throws DBException, InterruptedException;

}
