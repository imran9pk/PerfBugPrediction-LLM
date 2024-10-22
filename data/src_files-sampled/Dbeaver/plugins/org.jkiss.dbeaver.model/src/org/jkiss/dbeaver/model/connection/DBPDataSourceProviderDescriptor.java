package org.jkiss.dbeaver.model.connection;

import org.jkiss.code.NotNull;
import org.jkiss.dbeaver.model.DBPImage;
import org.jkiss.dbeaver.model.DBPNamedObject;
import org.jkiss.dbeaver.model.navigator.meta.DBXTreeDescriptor;
import org.jkiss.dbeaver.model.sql.SQLDialectMetadata;

import java.util.List;

public interface DBPDataSourceProviderDescriptor extends DBPNamedObject {

    String getId();

    String getDescription();

    DBPImage getIcon();

    boolean isDriversManagable();

    List<? extends DBPDriver> getEnabledDrivers();

    String getPluginId();

    DBXTreeDescriptor getTreeDescriptor();

    @NotNull
    SQLDialectMetadata getScriptDialect();

    boolean isTemporary();

    List<? extends DBPDriver> getDrivers();

    DBPDataSourceProviderDescriptor getParentProvider();

}
