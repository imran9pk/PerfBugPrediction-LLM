package org.jkiss.dbeaver.ext.oracle.oci;

import org.jkiss.dbeaver.Log;
import org.jkiss.dbeaver.ext.oracle.OracleDataSourceProvider;
import org.jkiss.dbeaver.model.connection.LocalNativeClientLocation;
import org.jkiss.utils.CommonUtils;

import java.util.ArrayList;
import java.util.List;

public class OracleHomeDescriptor extends LocalNativeClientLocation
{
    private static final Log log = Log.getLog(OracleHomeDescriptor.class);

    private Integer oraVersion; private String displayName;
    private List<String> tnsNames;

    public OracleHomeDescriptor(String oraHome)
    {
        super(CommonUtils.removeTrailingSlash(oraHome), oraHome);
        this.oraVersion = OracleDataSourceProvider.getOracleVersion(this);
        if (oraVersion == null) {
            log.debug("Unrecognized Oracle client version at " + oraHome);
        }
        this.displayName = OCIUtils.readWinRegistry(oraHome, OCIUtils.WIN_REG_ORA_HOME_NAME);
    }

    @Override
    public String getDisplayName()
    {
        if (displayName != null) {
            return displayName;
        }
        else {
            return getName();
        }
    }

    public List<String> getOraServiceNames()
    {
        if (tnsNames == null) {
            tnsNames = new ArrayList<>(OCIUtils.readTnsNames(getPath(), true).keySet());
        }
        return tnsNames;
    }

}
