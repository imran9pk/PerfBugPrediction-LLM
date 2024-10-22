package org.jkiss.dbeaver.ext.db2.manager;

import org.jkiss.code.Nullable;
import org.jkiss.dbeaver.ext.db2.model.DB2DataSource;
import org.jkiss.dbeaver.ext.db2.model.DB2StorageGroup;
import org.jkiss.dbeaver.model.struct.cache.DBSObjectCache;

public class DB2StorageGroupManager extends DB2AbstractDropOnlyManager<DB2StorageGroup, DB2DataSource> {

    private static final String SQL_DROP = "DROP STOGROUP %s RESTRICT";

    @Override
    public String buildDropStatement(DB2StorageGroup db2StorageGroup)
    {
        String name = db2StorageGroup.getName();
        return String.format(SQL_DROP, name);
    }

    @Nullable
    @Override
    public DBSObjectCache<DB2DataSource, DB2StorageGroup> getObjectsCache(DB2StorageGroup db2StorageGroup)
    {
        return db2StorageGroup.getDataSource().getStorageGroupCache();
    }
}
