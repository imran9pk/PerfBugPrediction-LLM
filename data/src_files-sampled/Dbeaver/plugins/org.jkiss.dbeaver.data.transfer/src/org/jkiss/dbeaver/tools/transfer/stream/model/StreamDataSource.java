package org.jkiss.dbeaver.tools.transfer.stream.model;

import org.jkiss.code.NotNull;
import org.jkiss.code.Nullable;
import org.jkiss.dbeaver.DBException;
import org.jkiss.dbeaver.model.DBPDataSource;
import org.jkiss.dbeaver.model.DBPDataSourceInfo;
import org.jkiss.dbeaver.model.data.DBDFormatSettings;
import org.jkiss.dbeaver.model.data.DBDValueHandler;
import org.jkiss.dbeaver.model.data.DBDValueHandlerProvider;
import org.jkiss.dbeaver.model.exec.DBCExecutionContext;
import org.jkiss.dbeaver.model.impl.AbstractSimpleDataSource;
import org.jkiss.dbeaver.model.impl.data.DefaultValueHandler;
import org.jkiss.dbeaver.model.runtime.DBRProgressMonitor;
import org.jkiss.dbeaver.model.sql.SQLDialect;
import org.jkiss.dbeaver.model.struct.DBSObject;
import org.jkiss.dbeaver.model.struct.DBSTypedObject;

import java.util.Collection;

public class StreamDataSource extends AbstractSimpleDataSource<StreamExecutionContext> implements DBDValueHandlerProvider {

    private final StreamDataSourceDialect dialect;

    public StreamDataSource(StreamDataSourceContainer container) {
        super(container);
        this.executionContext = new StreamExecutionContext(this, "Main");
        this.dialect = new StreamDataSourceDialect();
    }

    public StreamDataSource(String inputName) {
        this(new StreamDataSourceContainer(inputName));
    }

    @NotNull
    @Override
    public DBPDataSourceInfo getInfo() {
        return new StreamDataSourceInfo();
    }

    @Override
    public Object getDataSourceFeature(String featureId) {
        return null;
    }

    @Override
    public SQLDialect getSQLDialect() {
        return dialect;
    }

    @Override
    public void initialize(@NotNull DBRProgressMonitor monitor) throws DBException {

    }

    @NotNull
    @Override
    public StreamExecutionContext openIsolatedContext(@NotNull DBRProgressMonitor monitor, @NotNull String purpose, @Nullable DBCExecutionContext initFrom) throws DBException {
        return new StreamExecutionContext(this, purpose);
    }

    @Nullable
    @Override
    public DBDValueHandler getValueHandler(DBPDataSource dataSource, DBDFormatSettings preferences, DBSTypedObject typedObject) {
        return DefaultValueHandler.INSTANCE;
    }

    @Override
    public Collection<? extends DBSObject> getChildren(@NotNull DBRProgressMonitor monitor) throws DBException {
        return null;
    }

    @Nullable
    @Override
    public DBSObject getChild(@NotNull DBRProgressMonitor monitor, @NotNull String childName) throws DBException {
        return null;
    }

    @NotNull
    @Override
    public Class<? extends DBSObject> getPrimaryChildType(@Nullable DBRProgressMonitor monitor) throws DBException {
        return DBSObject.class;
    }

    @Override
    public void cacheStructure(@NotNull DBRProgressMonitor monitor, int scope) throws DBException {

    }

}
