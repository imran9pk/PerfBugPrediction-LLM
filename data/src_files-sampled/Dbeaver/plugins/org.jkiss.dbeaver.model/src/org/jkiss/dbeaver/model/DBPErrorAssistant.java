package org.jkiss.dbeaver.model;

import org.jkiss.code.NotNull;
import org.jkiss.code.Nullable;
import org.jkiss.dbeaver.model.exec.DBCExecutionContext;
import org.jkiss.dbeaver.model.runtime.DBRProgressMonitor;

public interface DBPErrorAssistant
{
    enum ErrorType {
        NORMAL,
        CONNECTION_LOST,
        DRIVER_CLASS_MISSING,
        PERMISSION_DENIED,
        FEATURE_UNSUPPORTED,
        TRANSACTION_ABORTED,    RESULT_SET_MISSING      

    class ErrorPosition
    {
        public int line = -1;
        public int position = -1;
        public String info = null;

        @Override
        public String toString() {
            return line + ":" + position + (info == null ? "" : " (" + info + ")");
        }
    }

    ErrorType discoverErrorType(@NotNull Throwable error);

    @Nullable
    ErrorPosition[] getErrorPosition(@NotNull DBRProgressMonitor monitor, @NotNull DBCExecutionContext context, @NotNull String query, @NotNull Throwable error);

}