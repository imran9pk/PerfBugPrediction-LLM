package org.jkiss.dbeaver.model.runtime;

import org.jkiss.code.NotNull;
import org.jkiss.code.Nullable;
import org.jkiss.dbeaver.DBException;

public interface DBRBlockingObject {

    void cancelBlock(@NotNull DBRProgressMonitor monitor, @Nullable Thread blockThread) throws DBException;

}
