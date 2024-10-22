package org.elasticsearch.xpack.sql.jdbc;

import org.elasticsearch.xpack.sql.client.SuppressForbidden;

import java.io.OutputStreamWriter;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.ParameterMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.Statement;
import java.util.HashMap;
import java.util.Map;

import javax.sql.DataSource;

final class Debug {

    private static final Map<String, DebugLog> OUTPUT_CACHE = new HashMap<>();
    private static final Map<String, Integer> OUTPUT_REFS = new HashMap<>();
    private static final Map<PrintWriter, DebugLog> OUTPUT_MANAGED = new HashMap<>();

    private static volatile DebugLog ERR = null, OUT = null;
    private static volatile PrintStream SYS_ERR = null, SYS_OUT = null;

    static Connection proxy(JdbcConfiguration info, Connection connection, PrintWriter managedPrinter) {
        return createProxy(Connection.class, new ConnectionProxy(logger(info, managedPrinter), connection));
    }

    static DatabaseMetaData proxy(DatabaseMetadataProxy handler) {
        return createProxy(DatabaseMetaData.class, handler);
    }

    static ParameterMetaData proxy(ParameterMetaDataProxy handler) {
        return createProxy(ParameterMetaData.class, handler);
    }

    static ResultSet proxy(ResultSetProxy handler) {
        return createProxy(ResultSet.class, handler);
    }

    static ResultSetMetaData proxy(ResultSetMetaDataProxy handler) {
        return createProxy(ResultSetMetaData.class, handler);
    }

    static Statement proxy(Object statement, StatementProxy handler) {
        Class<? extends Statement> i = Statement.class;

        if (statement instanceof PreparedStatement) {
            i = PreparedStatement.class;
        }
        else if (statement instanceof CallableStatement) {
            i = CallableStatement.class;
        }

        return createProxy(i, handler);
    }

    @SuppressWarnings("unchecked")
    private static <P> P createProxy(Class<P> proxy, InvocationHandler handler) {
        return (P) Proxy.newProxyInstance(Debug.class.getClassLoader(), new Class<?>[] { DebugProxy.class, proxy }, handler);
    }

    private static DebugLog logger(JdbcConfiguration info, PrintWriter managedPrinter) {
        DebugLog log = null;

        if (managedPrinter != null) {
            synchronized (Debug.class) {
                log = OUTPUT_MANAGED.get(managedPrinter);
                if (log == null) {
                    log = new DebugLog(managedPrinter);
                    OUTPUT_MANAGED.put(managedPrinter, log);
                }
                return log;
            }
        }

        String out = info.debugOut();

        if ("err".equals(out)) {
            PrintStream sys = stderr();

            if (SYS_ERR == null) {
                SYS_ERR = sys;
            }
            if (SYS_ERR != sys) {
                SYS_ERR.flush();
                SYS_ERR = sys;
                ERR = null;
            }
            if (ERR == null) {
                ERR = new DebugLog(new PrintWriter(new OutputStreamWriter(sys, StandardCharsets.UTF_8)));
            }
            return ERR;
        }

        if ("out".equals(out)) {
            PrintStream sys = stdout();

            if (SYS_OUT == null) {
                SYS_OUT = sys;
            }

            if (SYS_OUT != sys) {
                SYS_OUT.flush();
                SYS_OUT = sys;
                OUT = null;
            }

            if (OUT == null) {
                OUT = new DebugLog(new PrintWriter(new OutputStreamWriter(sys, StandardCharsets.UTF_8)));
            }
            return OUT;
        }

        synchronized (Debug.class) {
            log = OUTPUT_CACHE.get(out);
            if (log == null) {
                try {
                    PrintWriter print = new PrintWriter(Files.newBufferedWriter(Paths.get("").resolve(out), StandardCharsets.UTF_8));
                    log = new DebugLog(print);
                    OUTPUT_CACHE.put(out, log);
                    OUTPUT_REFS.put(out, Integer.valueOf(0));
                } catch (Exception ex) {
                    throw new JdbcException(ex, "Cannot open debug output [" + out + "]");
                }
            }
            OUTPUT_REFS.put(out, Integer.valueOf(OUTPUT_REFS.get(out).intValue() + 1));
        }

        return log;
    }

    static void release(JdbcConfiguration info) {
        if (!info.debug()) {
            return;
        }

        String out = info.debugOut();
        synchronized (Debug.class) {
            Integer ref = OUTPUT_REFS.get(out);
            if (ref != null) {
                int r = ref.intValue();
                if (r < 2) {
                    OUTPUT_REFS.remove(out);
                    DebugLog d = OUTPUT_CACHE.remove(out);
                    if (d != null) {
                        if (d.print != null) {
                            d.print.close();
                        }
                    }
                }
                else {
                    OUTPUT_REFS.put(out, Integer.valueOf(r - 1));
                }
            }
        }
    }

    static synchronized void close() {
        OUTPUT_REFS.clear();

        for (DebugLog d : OUTPUT_CACHE.values()) {
            if (d.print != null) {
                d.print.close();
            }
        }
        OUTPUT_CACHE.clear();

        for (DebugLog d : OUTPUT_MANAGED.values()) {
            d.print.flush();
        }

        OUTPUT_MANAGED.clear();
    }

    @SuppressForbidden(reason = "JDBC drivers allows logging to Sys.out")
    private static PrintStream stdout() {
        return System.out;
    }

    @SuppressForbidden(reason = "JDBC drivers allows logging to Sys.err")
    private static PrintStream stderr() {
        return System.err;
    }
}