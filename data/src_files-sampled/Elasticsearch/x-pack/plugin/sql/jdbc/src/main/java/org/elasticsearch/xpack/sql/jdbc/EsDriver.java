package org.elasticsearch.xpack.sql.jdbc;

import org.elasticsearch.xpack.sql.client.Version;

import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.DriverPropertyInfo;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.Properties;
import java.util.logging.Logger;

public class EsDriver implements Driver {

    private static final EsDriver INSTANCE = new EsDriver();

    static {
        Version.CURRENT.toString();

        try {
            register();
        } catch (SQLException ex) {
            PrintWriter writer = DriverManager.getLogWriter();
            if (writer != null) {
                ex.printStackTrace(writer);
                writer.flush();
            }
            throw new ExceptionInInitializerError(ex);
        }
    }

    public static EsDriver register() throws SQLException {
        DriverManager.registerDriver(INSTANCE, INSTANCE::close);
        return INSTANCE;
    }

    public static void deregister() throws SQLException {
        try {
            DriverManager.deregisterDriver(INSTANCE);
        } catch (SQLException ex) {
            PrintWriter writer = DriverManager.getLogWriter();
            if (writer != null) {
                ex.printStackTrace(writer);
                writer.flush();
            }
            throw ex;
        }
    }

    @Override
    public Connection connect(String url, Properties props) throws SQLException {
        if (url == null) {
            throw new JdbcSQLException("Non-null url required");
        }
        if (!acceptsURL(url)) {
            return null;
        }

        JdbcConfiguration cfg = initCfg(url, props);
        JdbcConnection con = new JdbcConnection(cfg);
        return cfg.debug() ? Debug.proxy(cfg, con, DriverManager.getLogWriter()) : con;
    }

    private static JdbcConfiguration initCfg(String url, Properties props) throws JdbcSQLException {
        return JdbcConfiguration.create(url, props, DriverManager.getLoginTimeout());
    }

    @Override
    public boolean acceptsURL(String url) throws SQLException {
        return JdbcConfiguration.canAccept(url);
    }

    @Override
    public DriverPropertyInfo[] getPropertyInfo(String url, Properties info) throws SQLException {
        if (!acceptsURL(url)) {
            return new DriverPropertyInfo[0];
        }
        return JdbcConfiguration.create(url, info, DriverManager.getLoginTimeout()).driverPropertyInfo();
    }

    @Override
    public int getMajorVersion() {
        return Version.CURRENT.major;
    }

    @Override
    public int getMinorVersion() {
        return Version.CURRENT.minor;
    }

    @Override
    public boolean jdbcCompliant() {
        return false;
    }

    @Override
    public Logger getParentLogger() throws SQLFeatureNotSupportedException {
        throw new SQLFeatureNotSupportedException();
    }

    private void close() {
        Debug.close();
    }
}
