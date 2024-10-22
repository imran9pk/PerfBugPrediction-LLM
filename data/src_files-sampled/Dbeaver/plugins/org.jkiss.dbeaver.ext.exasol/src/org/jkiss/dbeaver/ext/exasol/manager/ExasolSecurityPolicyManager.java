package org.jkiss.dbeaver.ext.exasol.manager;

import org.jkiss.dbeaver.DBException;
import org.jkiss.dbeaver.ext.exasol.model.ExasolDataSource;
import org.jkiss.dbeaver.ext.exasol.model.security.ExasolSecurityPolicy;
import org.jkiss.dbeaver.model.DBPDataSource;
import org.jkiss.dbeaver.model.DBUtils;
import org.jkiss.dbeaver.model.edit.DBECommandContext;
import org.jkiss.dbeaver.model.edit.DBEPersistAction;
import org.jkiss.dbeaver.model.exec.DBCExecutionContext;
import org.jkiss.dbeaver.model.exec.DBCFeatureNotSupportedException;
import org.jkiss.dbeaver.model.impl.edit.SQLDatabasePersistAction;
import org.jkiss.dbeaver.model.impl.sql.edit.SQLObjectEditor;
import org.jkiss.dbeaver.model.runtime.DBRProgressMonitor;
import org.jkiss.dbeaver.model.struct.cache.DBSObjectCache;

import java.util.List;
import java.util.Map;


public class ExasolSecurityPolicyManager
        extends SQLObjectEditor<ExasolSecurityPolicy, ExasolDataSource>  {
    
    @Override
    public long getMakerOptions(DBPDataSource dataSource)
    {
        return FEATURE_SAVE_IMMEDIATELY;
    }
    
    
    @Override
    public DBSObjectCache<ExasolDataSource, ExasolSecurityPolicy> getObjectsCache(
            ExasolSecurityPolicy object)
    {
        ExasolDataSource source = (ExasolDataSource) object.getDataSource();
        return source.getSecurityPolicyCache();
    }
    
    @Override
    protected ExasolSecurityPolicy createDatabaseObject(
        DBRProgressMonitor monitor,
        DBECommandContext context, Object container, Object copyFrom, Map<String, Object> options)
        throws DBException
    {
        throw new DBCFeatureNotSupportedException();
    }
    
    
    @Override
    protected void addObjectRenameActions(DBRProgressMonitor monitor, DBCExecutionContext executionContext, List<DBEPersistAction> actions,
                                          ObjectRenameCommand command, Map<String, Object> options)
    {
        ExasolSecurityPolicy obj = command.getObject();
        actions.add(
            new SQLDatabasePersistAction(
                "Rename Connection",
                "RENAME CONNECTION " +  DBUtils.getQuotedIdentifier(obj.getDataSource(), command.getOldName()) + " to " +
                    DBUtils.getQuotedIdentifier(obj.getDataSource(), command.getNewName()))
        );
    }
    
    @Override
    protected void addObjectDeleteActions(DBRProgressMonitor monitor, DBCExecutionContext executionContext, List<DBEPersistAction> actions,
                                          ObjectDeleteCommand command, Map<String, Object> options)
    {
        final ExasolSecurityPolicy con = command.getObject();
        actions.add(
                new SQLDatabasePersistAction("Drop Connection","DROP CONNECTION " + DBUtils.getQuotedIdentifier(con))
                );
    }
    
    @Override
    protected void addObjectModifyActions(DBRProgressMonitor monitor, DBCExecutionContext executionContext, List<DBEPersistAction> actionList,
                                          ObjectChangeCommand command, Map<String, Object> options)
    {
    	
    	ExasolSecurityPolicy policy = command.getObject();
    	
    	if (policy.getEnabled())
    	{
    		String script = String.format("ALTER SYSTEM SET PASSWORD_SECURITY_POLICY='%s'", policy.getSecurityString());
    		actionList.add(new SQLDatabasePersistAction(script));
    	} else {
    		String script = String.format("ALTER SYSTEM SET PASSWORD_SECURITY_POLICY='OFF'", policy.getSecurityString());
    		actionList.add(new SQLDatabasePersistAction(script));
    	}

    }
    
    @Override
    public boolean canDeleteObject(ExasolSecurityPolicy object) {
    	return false;
    }

    @Override
    public boolean canCreateObject(Object container) {
    	return false;
    }


	@Override
	protected void addObjectCreateActions(DBRProgressMonitor monitor, DBCExecutionContext executionContext, List<DBEPersistAction> actions,
                                          ObjectCreateCommand command,
                                          Map<String, Object> options) {
	}
    
    
    

}
