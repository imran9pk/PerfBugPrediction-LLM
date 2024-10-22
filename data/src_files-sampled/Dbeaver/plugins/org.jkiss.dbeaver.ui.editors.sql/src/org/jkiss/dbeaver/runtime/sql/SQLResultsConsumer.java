package org.jkiss.dbeaver.runtime.sql;

import org.jkiss.code.Nullable;
import org.jkiss.dbeaver.model.data.DBDDataReceiver;
import org.jkiss.dbeaver.model.sql.SQLQuery;

public interface SQLResultsConsumer
{
    @Nullable
    DBDDataReceiver getDataReceiver(SQLQuery statement, int resultSetNumber);

}
