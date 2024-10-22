package org.jkiss.dbeaver.ext.postgresql.edit;

import org.jkiss.dbeaver.DBException;
import org.jkiss.dbeaver.Log;
import org.jkiss.dbeaver.ext.postgresql.model.PostgreDatabase;
import org.jkiss.dbeaver.ext.postgresql.model.PostgreTablespace;
import org.jkiss.dbeaver.model.DBPDataSource;
import org.jkiss.dbeaver.model.edit.DBECommandContext;
import org.jkiss.dbeaver.model.edit.DBEPersistAction;
import org.jkiss.dbeaver.model.exec.DBCExecutionContext;
import org.jkiss.dbeaver.model.impl.edit.SQLDatabasePersistActionAtomic;
import org.jkiss.dbeaver.model.impl.sql.edit.SQLObjectEditor;
import org.jkiss.dbeaver.model.runtime.DBRProgressMonitor;
import org.jkiss.dbeaver.model.struct.cache.DBSObjectCache;
import org.jkiss.dbeaver.runtime.DBWorkbench;

import java.util.*;


public class PostgreTablespaceManager extends SQLObjectEditor<PostgreTablespace, PostgreDatabase> {

    private final static Set<String> systemTablespaces = new HashSet<>(Arrays.asList("pg_default", "pg_global"));

    private static final Log log = Log.getLog(PostgreTablespaceManager.class);

    @Override
    public long getMakerOptions(DBPDataSource dataSource) {
        return FEATURE_SAVE_IMMEDIATELY;
    }

    @Override
    public DBSObjectCache<PostgreDatabase, PostgreTablespace> getObjectsCache(PostgreTablespace object) {
        return object.getDatabase().tablespaceCache;
    }

    @Override
    protected PostgreTablespace createDatabaseObject(
        DBRProgressMonitor monitor,
        DBECommandContext context,
        Object container,
        Object copyFrom,
        Map<String, Object> options) throws DBException
    {
        return new PostgreTablespace((PostgreDatabase) container);
    }

    @Override
    public void deleteObject(DBECommandContext commandContext, PostgreTablespace object, Map<String, Object> options)
        throws DBException {
        if (systemTablespaces.contains(object.getName().toLowerCase())) {
            DBWorkbench.getPlatformUI().showError("Drop tablespace", "Unable to drop system tablespace " + object.getName());
        } else {
            super.deleteObject(commandContext, object, options);
        }
    }

    @Override
    protected void addObjectCreateActions(
        DBRProgressMonitor monitor,
        DBCExecutionContext executionContext, List<DBEPersistAction> actions,
        ObjectCreateCommand command,
        Map<String, Object> options) {
        final PostgreTablespace tablespace = command.getObject();

        try {
            actions.add(
                new SQLDatabasePersistActionAtomic("Create tablespace", tablespace.getObjectDefinitionText(monitor, options)) ;
        } catch (DBException e) {
            log.error(e);
        }
    }

    @Override
    protected void addObjectDeleteActions(
        DBRProgressMonitor monitor, DBCExecutionContext executionContext, List<DBEPersistAction> actions,
        ObjectDeleteCommand command,
        Map<String, Object> options) {
        actions.add(
            new SQLDatabasePersistActionAtomic("Drop tablespace", "DROP TABLESPACE " + command.getObject().getName()) ;

    }

    @Override
    public boolean canCreateObject(Object container) {
        return true;
    }

    @Override
    public boolean canDeleteObject(PostgreTablespace object) {
        return true;
    }

    @Override
    public boolean canEditObject(PostgreTablespace object) {
        return false;
    }
}
