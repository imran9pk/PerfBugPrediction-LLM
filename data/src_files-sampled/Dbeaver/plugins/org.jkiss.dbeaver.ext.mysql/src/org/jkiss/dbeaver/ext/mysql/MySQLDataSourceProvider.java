package org.jkiss.dbeaver.ext.mysql;

import com.sun.jna.platform.win32.Advapi32Util;
import com.sun.jna.platform.win32.WinReg;
import org.jkiss.code.NotNull;
import org.jkiss.code.Nullable;
import org.jkiss.dbeaver.DBException;
import org.jkiss.dbeaver.Log;
import org.jkiss.dbeaver.ext.mysql.model.MySQLDataSource;
import org.jkiss.dbeaver.model.DBPDataSource;
import org.jkiss.dbeaver.model.DBPDataSourceContainer;
import org.jkiss.dbeaver.model.connection.*;
import org.jkiss.dbeaver.model.impl.jdbc.JDBCDataSourceProvider;
import org.jkiss.dbeaver.model.runtime.DBRProgressMonitor;
import org.jkiss.dbeaver.utils.GeneralUtils;
import org.jkiss.dbeaver.utils.RuntimeUtils;
import org.jkiss.utils.CommonUtils;
import org.jkiss.utils.IOUtils;
import org.jkiss.utils.StandardConstants;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.util.*;

public class MySQLDataSourceProvider extends JDBCDataSourceProvider implements DBPNativeClientLocationManager {
    private static final Log log = Log.getLog(MySQLDataSourceProvider.class);

    private static final String REGISTRY_ROOT_MYSQL_64 = "SOFTWARE\\Wow6432Node\\MYSQL AB";
    private static final String REGISTRY_ROOT_MARIADB = "SOFTWARE\\Monty Program AB";
    private static final String SERER_LOCATION_KEY = "Location";
    private static final String INSTALLDIR_KEY = "INSTALLDIR";
    private static Map<String,MySQLServerHome> localServers = null;
    private static Map<String,String> connectionsProps;

    static {
        connectionsProps = new HashMap<>();

        connectionsProps.put("characterEncoding", GeneralUtils.UTF8_ENCODING);
        connectionsProps.put("tinyInt1isBit", "false");
        connectionsProps.put("interactiveClient", "true");
        }

    public static Map<String,String> getConnectionsProps() {
        return connectionsProps;
    }

    public MySQLDataSourceProvider()
    {
    }

    @Override
    protected String getConnectionPropertyDefaultValue(String name, String value) {
        String ovrValue = connectionsProps.get(name);
        return ovrValue != null ? ovrValue : super.getConnectionPropertyDefaultValue(name, value);
    }

    @Override
    public long getFeatures()
    {
        return FEATURE_CATALOGS;
    }

    @Override
    public String getConnectionURL(DBPDriver driver, DBPConnectionConfiguration connectionInfo)
    {
StringBuilder url = new StringBuilder();
        url.append("jdbc:mysql://")
            .append(connectionInfo.getHostName());
        if (!CommonUtils.isEmpty(connectionInfo.getHostPort())) {
            url.append(":").append(connectionInfo.getHostPort());
        }
        url.append("/");
        if (!CommonUtils.isEmpty(connectionInfo.getDatabaseName())) {
            url.append(connectionInfo.getDatabaseName());
        }

        return url.toString();
    }

    @NotNull
    @Override
    public DBPDataSource openDataSource(
        @NotNull DBRProgressMonitor monitor, @NotNull DBPDataSourceContainer container)
        throws DBException
    {
        return new MySQLDataSource(monitor, container);
    }

    @Override
    public List<DBPNativeClientLocation> findLocalClientLocations()
    {
        findLocalClients();
        return new ArrayList<>(localServers.values());
    }

    @Override
    public DBPNativeClientLocation getDefaultLocalClientLocation()
    {
        findLocalClients();
        return localServers.isEmpty() ? null : localServers.values().iterator().next();
    }

    @Override
    public String getProductName(DBPNativeClientLocation location) {
        return "MySQL/MariaDB";
    }

    @Override
    public String getProductVersion(DBPNativeClientLocation location) {
        return getFullServerVersion(location.getPath());
    }

    public static MySQLServerHome getServerHome(String homeId)
    {
        findLocalClients();
        MySQLServerHome home = localServers.get(homeId);
        return home == null ? new MySQLServerHome(homeId, homeId) : home;
    }

    public synchronized static void findLocalClients() {
        if (localServers != null) {
            return;
        }
        localServers = new LinkedHashMap<>();
        String path = System.getenv("PATH");
        if (path != null && RuntimeUtils.isWindows()) {
            for (String token : path.split(System.getProperty(StandardConstants.ENV_PATH_SEPARATOR))) {
                token = CommonUtils.removeTrailingSlash(token);
                File mysqlFile = new File(token, MySQLUtils.getMySQLConsoleBinaryName());
                if (mysqlFile.exists()) {
                    File binFolder = mysqlFile.getAbsoluteFile().getParentFile();if (binFolder.getName().equalsIgnoreCase("bin")) {
                    	String homeId = CommonUtils.removeTrailingSlash(binFolder.getParentFile().getAbsolutePath());
                        localServers.put(homeId, new MySQLServerHome(homeId, null));
                    }
                }
            }
        }

        if (RuntimeUtils.isWindows()) {
            try {
                {
                    if (Advapi32Util.registryKeyExists(WinReg.HKEY_LOCAL_MACHINE, REGISTRY_ROOT_MYSQL_64)) {
                        String[] homeKeys = Advapi32Util.registryGetKeys(WinReg.HKEY_LOCAL_MACHINE, REGISTRY_ROOT_MYSQL_64);
                        if (homeKeys != null) {
                            for (String homeKey : homeKeys) {
                                Map<String, Object> valuesMap = Advapi32Util.registryGetValues(WinReg.HKEY_LOCAL_MACHINE, REGISTRY_ROOT_MYSQL_64 + "\\" + homeKey);
                                for (String key : valuesMap.keySet()) {
                                    if (SERER_LOCATION_KEY.equalsIgnoreCase(key)) {
                                        String serverPath = CommonUtils.removeTrailingSlash(CommonUtils.toString(valuesMap.get(key)));
                                        if (new File(serverPath, "bin").exists()) {
                                            localServers.put(serverPath, new MySQLServerHome(serverPath, homeKey));
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                if (Advapi32Util.registryKeyExists(WinReg.HKEY_LOCAL_MACHINE, REGISTRY_ROOT_MARIADB)) {
                    String[] homeKeys = Advapi32Util.registryGetKeys(WinReg.HKEY_LOCAL_MACHINE, REGISTRY_ROOT_MARIADB);
                    if (homeKeys != null) {
                        for (String homeKey : homeKeys) {
                            Map<String, Object> valuesMap = Advapi32Util.registryGetValues(WinReg.HKEY_LOCAL_MACHINE, REGISTRY_ROOT_MARIADB + "\\" + homeKey);
                            for (String key : valuesMap.keySet()) {
                                if (INSTALLDIR_KEY.equalsIgnoreCase(key)) {
                                    String serverPath = CommonUtils.removeTrailingSlash(CommonUtils.toString(valuesMap.get(key)));
                                    if (new File(serverPath, "bin").exists()) {
                                        localServers.put(serverPath, new MySQLServerHome(serverPath, homeKey));
                                    }
                                }
                            }
                        }
                    }
                }
            } catch (Throwable e) {
                log.warn("Error reading Windows registry", e);
            }
        } else if (RuntimeUtils.isMacOS()) {
            Collection<File> mysqlDirs = new ArrayList<>();
            Collections.addAll(
                mysqlDirs,
                NativeClientLocationUtils.getSubdirectoriesWithNamesStartingWith("mysql", new File(NativeClientLocationUtils.USR_LOCAL)) ;
            Collections.addAll(
                mysqlDirs,
                NativeClientLocationUtils.getSubdirectories(NativeClientLocationUtils.getSubdirectoriesWithNamesStartingWith("mysql", new File(NativeClientLocationUtils.HOMEBREW_FORMULAE_LOCATION)))
            );
            Collections.addAll(
                mysqlDirs,
                NativeClientLocationUtils.getSubdirectories(NativeClientLocationUtils.getSubdirectoriesWithNamesStartingWith("mariadb", new File(NativeClientLocationUtils.HOMEBREW_FORMULAE_LOCATION)))
            );
            for (File dir: mysqlDirs) {
                File bin = new File(dir, NativeClientLocationUtils.BIN);
                File binary = new File(bin, MySQLUtils.getMySQLConsoleBinaryName());
                if (!bin.exists() || !bin.isDirectory() || !binary.exists() || !binary.canExecute()) {
                    continue;
                }
                String version = getFullServerVersion(dir);
                if (version == null) {
                    continue;
                }
                String canonicalPath = NativeClientLocationUtils.getCanonicalPath(dir);
                if (canonicalPath.isEmpty()) {
                    continue;
                }
                MySQLServerHome home = new MySQLServerHome(canonicalPath, "MySQL " + version);
                localServers.put(canonicalPath, home);
            }
        }
    }

    @Nullable
    private static String getFullServerVersion(File path) {
        File binPath = path;
        File binSubfolder = new File(binPath, "bin");
        if (binSubfolder.exists()) {
            binPath = binSubfolder;
        }

        String cmd = new File(
            binPath,
            MySQLUtils.getMySQLConsoleBinaryName()).getAbsolutePath();

        try {
            Process p = Runtime.getRuntime().exec(new String[]{cmd, MySQLConstants.FLAG_VERSION});
            try {
                BufferedReader input = new BufferedReader(new InputStreamReader(p.getInputStream()));
                try {
                    String line;
                    while ((line = input.readLine()) != null) {
                        int pos = line.indexOf("Distrib ");
                        if (pos != -1) {
                            pos += 8;
                            int pos2 = line.indexOf(",", pos);
                            return line.substring(pos, pos2);
                        }
                        pos = line.indexOf("Ver ");
                        if (pos != -1) {
                            pos += 4;
                            int pos2 = line.indexOf(" for ", pos);
                            return line.substring(pos, pos2);
                        }
                    }
                } finally {
                    IOUtils.close(input);
                }
            } finally {
                p.destroy();
            }
        }
        catch (Exception ex) {
            log.warn("Error reading MySQL server version from " + cmd, ex);
        }
        return null;
    }
}
