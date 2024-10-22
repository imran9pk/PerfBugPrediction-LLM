package org.jkiss.dbeaver.ui.editors.acl;

import org.jkiss.dbeaver.model.access.DBAPrivilege;
import org.jkiss.dbeaver.model.access.DBAPrivilegeOwner;
import org.jkiss.dbeaver.model.access.DBAPrivilegeType;
import org.jkiss.dbeaver.model.runtime.DBRProgressMonitor;
import org.jkiss.dbeaver.model.struct.DBSObject;

import java.util.Map;

public interface ObjectACLManager<PRIVILEGE extends DBAPrivilege, PRIVILEGE_TYPE extends DBAPrivilegeType> {

    PRIVILEGE_TYPE[] getPrivilegeTypes();

    PRIVILEGE createNewPrivilege(DBAPrivilegeOwner owner, DBSObject object, PRIVILEGE copyFrom);

    String getObjectUniqueName(DBSObject object);

    String generatePermissionChangeScript(
        DBRProgressMonitor monitor,
        DBAPrivilegeOwner object,
        boolean grant,
        PRIVILEGE privilege,
        PRIVILEGE_TYPE[] privilegeTypes,
        Map<String, Object> options);
}