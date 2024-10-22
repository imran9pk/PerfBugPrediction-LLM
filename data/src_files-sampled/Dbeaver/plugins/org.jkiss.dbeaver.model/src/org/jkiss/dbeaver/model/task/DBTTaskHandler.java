package org.jkiss.dbeaver.model.task;

import org.jkiss.code.NotNull;
import org.jkiss.dbeaver.DBException;
import org.jkiss.dbeaver.Log;
import org.jkiss.dbeaver.model.runtime.DBRRunnableContext;

import java.io.PrintStream;
import java.util.Locale;

public interface DBTTaskHandler {

    void executeTask(
        @NotNull DBRRunnableContext runnableContext,
        @NotNull DBTTask task,
        @NotNull Locale locale,
        @NotNull Log log,
        @NotNull PrintStream logStream,
        @NotNull DBTTaskExecutionListener listener)
        throws DBException;

}
