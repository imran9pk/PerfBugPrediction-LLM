package org.jkiss.dbeaver.model.app;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.jkiss.code.NotNull;
import org.jkiss.dbeaver.model.DBPObject;
import org.jkiss.dbeaver.model.auth.DBAAuthSpace;
import org.jkiss.dbeaver.model.auth.DBASessionContext;
import org.jkiss.dbeaver.model.task.DBTTaskManager;

import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;

public interface DBPProject extends DBPObject, DBAAuthSpace
{
    String METADATA_FOLDER = ".dbeaver";

    String PROP_SECURE_PROJECT = "secureProject";

    @NotNull
    DBPWorkspace getWorkspace();

    boolean isVirtual();

    boolean isInMemory();

    @NotNull
    String getName();

    UUID getProjectID();

    @NotNull
    Path getAbsolutePath();

    @NotNull
    IProject getEclipseProject();

    @NotNull
    Path getMetadataFolder(boolean create);

    boolean isOpen();

    void ensureOpen();

    boolean isRegistryLoaded();

    boolean isModernProject();

    @NotNull
    DBPDataSourceRegistry getDataSourceRegistry();

    @NotNull
    DBTTaskManager getTaskManager();

    @NotNull
    DBASecureStorage getSecureStorage();

    @NotNull
    DBASessionContext getSessionContext();

    Object getProjectProperty(String propName);

    void setProjectProperty(String propName, Object propValue);

    Object getResourceProperty(IResource resource, String propName);

    Map<String, Object> getResourceProperties(IResource resource);

    Map<String, Map<String, Object>> getResourceProperties();

    void setResourceProperty(IResource resource, String propName, Object propValue);

    void setResourceProperties(IResource resource, Map<String, Object> props);
}
