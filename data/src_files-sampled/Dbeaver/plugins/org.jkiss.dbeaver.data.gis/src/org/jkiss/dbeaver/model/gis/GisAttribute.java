package org.jkiss.dbeaver.model.gis;

import org.jkiss.code.NotNull;
import org.jkiss.code.Nullable;
import org.jkiss.dbeaver.model.exec.DBCException;
import org.jkiss.dbeaver.model.runtime.DBRProgressMonitor;

public interface GisAttribute {
    int getAttributeGeometrySRID(DBRProgressMonitor monitor) throws DBCException;

    @NotNull
    DBGeometryDimension getAttributeGeometryDimension(DBRProgressMonitor monitor) throws DBCException;

    @Nullable
    String getAttributeGeometryType(DBRProgressMonitor monitor) throws DBCException;
}
