package org.jkiss.dbeaver.ext.db2.manager;

import org.jkiss.code.Nullable;
import org.jkiss.dbeaver.ext.db2.model.DB2DataType;
import org.jkiss.dbeaver.ext.db2.model.DB2Schema;
import org.jkiss.dbeaver.model.DBPEvaluationContext;
import org.jkiss.dbeaver.model.struct.cache.DBSObjectCache;

public class DB2DataTypeManager extends DB2AbstractDropOnlyManager<DB2DataType, DB2Schema> {

    private static final String SQL_DROP = "DROP TYPE %s RESTRICT";

    @Override
    public String buildDropStatement(DB2DataType db2DataType)
    {
        String fullyQualifiedName = db2DataType.getFullyQualifiedName(DBPEvaluationContext.DDL);
        return String.format(SQL_DROP, fullyQualifiedName);
    }

    @Nullable
    @Override
    public DBSObjectCache<DB2Schema, DB2DataType> getObjectsCache(DB2DataType db2DataType)
    {
        return db2DataType.getSchema().getUdtCache();
    }
}
