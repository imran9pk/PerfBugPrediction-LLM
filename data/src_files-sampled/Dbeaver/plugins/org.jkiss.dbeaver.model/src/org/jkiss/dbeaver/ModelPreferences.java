package org.jkiss.dbeaver;

import org.jkiss.dbeaver.bundle.ModelActivator;
import org.jkiss.dbeaver.model.DBConstants;
import org.jkiss.dbeaver.model.exec.DBCExecutionPurpose;
import org.jkiss.dbeaver.model.impl.preferences.BundlePreferenceStore;
import org.jkiss.dbeaver.model.preferences.DBPPreferenceStore;
import org.jkiss.dbeaver.model.qm.QMConstants;
import org.jkiss.dbeaver.model.qm.QMObjectType;
import org.jkiss.dbeaver.model.sql.SQLConstants;
import org.jkiss.dbeaver.registry.formatter.DataFormatterProfile;
import org.jkiss.dbeaver.utils.GeneralUtils;
import org.jkiss.dbeaver.utils.PrefUtils;
import org.osgi.framework.Bundle;

import java.util.Arrays;
import java.util.Locale;

public final class ModelPreferences
{
    public static final String PLUGIN_ID = "org.jkiss.dbeaver.model";

    public static final String NOTIFICATIONS_ENABLED = "notifications.enabled"; public static final String NOTIFICATIONS_CLOSE_DELAY_TIMEOUT = "notifications.closeDelay"; public static final String QUERY_ROLLBACK_ON_ERROR = "query.rollback-on-error"; public static final String EXECUTE_RECOVER_ENABLED = "execute.recover.enabled"; public static final String EXECUTE_RECOVER_RETRY_COUNT = "execute.recover.retryCount"; public static final String EXECUTE_CANCEL_CHECK_TIMEOUT = "execute.cancel.checkTimeout"; public static final String CONNECTION_OPEN_TIMEOUT = "connection.open.timeout"; public static final String CONNECTION_VALIDATION_TIMEOUT = "connection.validation.timeout"; public static final String CONNECTION_CLOSE_TIMEOUT = "connection.close.timeout"; public static final String SCRIPT_STATEMENT_DELIMITER = "script.sql.delimiter"; public static final String SCRIPT_IGNORE_NATIVE_DELIMITER = "script.sql.ignoreNativeDelimiter"; public static final String SCRIPT_STATEMENT_DELIMITER_BLANK = "script.sql.delimiter.blank"; public static final String QUERY_REMOVE_TRAILING_DELIMITER = "script.sql.query.remove.trailing.delimiter"; public static final String MEMORY_CONTENT_MAX_SIZE = "content.memory.maxsize"; public static final String CONTENT_HEX_ENCODING = "content.hex.encoding"; public static final String CONTENT_CACHE_CLOB = "content.cache.clob"; public static final String CONTENT_CACHE_BLOB = "content.cache.blob"; public static final String CONTENT_CACHE_MAX_SIZE = "content.cache.maxsize"; public static final String META_SEPARATE_CONNECTION = "database.meta.separate.connection"; public static final String META_CASE_SENSITIVE = "database.meta.casesensitive"; public static final String META_USE_SERVER_SIDE_FILTERS = "database.meta.server.side.filters"; public static final String META_CLIENT_NAME_DISABLE = "database.meta.client.name.disable"; public static final String META_CLIENT_NAME_OVERRIDE = "database.meta.client.name.override"; public static final String META_CLIENT_NAME_VALUE = "database.meta.client.name.value"; public static final String CONNECT_USE_ENV_VARS = "database.connect.processEnvVars"; public static final String RESULT_NATIVE_DATETIME_FORMAT = "resultset.format.datetime.native"; public static final String RESULT_NATIVE_NUMERIC_FORMAT = "resultset.format.numeric.native"; public static final String RESULT_SCIENTIFIC_NUMERIC_FORMAT = "resultset.format.numeric.scientific"; public static final String RESULT_TRANSFORM_COMPLEX_TYPES = "resultset.transform.complex.type"; public static final String NET_TUNNEL_PORT_MIN = "net.tunnel.port.min"; public static final String NET_TUNNEL_PORT_MAX = "net.tunnel.port.max"; public static final String RESULT_SET_USE_FETCH_SIZE = "resultset.fetch.size"; public static final String RESULT_SET_MAX_ROWS_USE_SQL = "resultset.maxrows.sql"; public static final String RESULT_SET_BINARY_PRESENTATION = "resultset.binary.representation"; public static final String RESULT_SET_BINARY_STRING_MAX_LEN = "resultset.binary.stringMaxLength"; public static final String RESULT_SET_IGNORE_COLUMN_LABEL = "resultset.column.label.ignore"; public static final String RESULT_SET_REREAD_ON_SCROLLING = "resultset.reread.on.scroll"; public static final String RESULT_SET_READ_METADATA = "resultset.read.metadata"; public static final String RESULT_SET_READ_REFERENCES = "resultset.read.references"; public static final String RESULT_SET_MAX_ROWS = "resultset.maxrows"; public static final String SQL_PARAMETERS_ENABLED = "sql.parameter.enabled"; public static final String SQL_PARAMETERS_IN_DDL_ENABLED = "sql.parameter.ddl.enabled"; public static final String SQL_ANONYMOUS_PARAMETERS_ENABLED = "sql.parameter.anonymous.enabled"; public static final String SQL_ANONYMOUS_PARAMETERS_MARK = "sql.parameter.mark"; public static final String SQL_NAMED_PARAMETERS_PREFIX = "sql.parameter.prefix"; public static final String SQL_CONTROL_COMMAND_PREFIX = "sql.command.prefix"; public static final String SQL_VARIABLES_ENABLED = "sql.variables.enabled"; public static final String SQL_FILTER_FORCE_SUBSELECT = "sql.query.filter.force.subselect"; public final static String SQL_FORMAT_KEYWORD_CASE = "sql.format.keywordCase";
    public final static String SQL_FORMAT_EXTERNAL_CMD = "sql.format.external.cmd";
    public final static String SQL_FORMAT_EXTERNAL_FILE = "sql.format.external.file";
    public final static String SQL_FORMAT_EXTERNAL_TIMEOUT = "sql.format.external.timeout";
    public final static String SQL_FORMAT_LF_BEFORE_COMMA = "sql.format.lf.before.comma";
    public static final String SQL_FORMAT_BREAK_BEFORE_CLOSE_BRACKET = "sql.format.break.before.close.bracket";
    public static final String SQL_FORMAT_INSERT_DELIMITERS_IN_EMPTY_LINES = "sql.format.insert.delimiters.in.empty_lines";

    public static final String READ_EXPENSIVE_PROPERTIES = "database.props.expensive"; public static final String READ_EXPENSIVE_STATISTICS = "database.stats.expensive"; public static final String UI_DRIVERS_VERSION_UPDATE = "ui.drivers.version.update"; public static final String UI_DRIVERS_HOME = "ui.drivers.home"; public static final String UI_PROXY_HOST = "ui.proxy.host"; public static final String UI_PROXY_PORT = "ui.proxy.port"; public static final String UI_PROXY_USER = "ui.proxy.user"; public static final String UI_PROXY_PASSWORD = "ui.proxy.password"; public static final String UI_DRIVERS_SOURCES = "ui.drivers.sources"; public static final String UI_DRIVERS_GLOBAL_LIBRARIES = "ui.drivers.global.libraries"; public static final String UI_MAVEN_REPOSITORIES = "ui.maven.repositories"; public static final String NAVIGATOR_SHOW_FOLDER_PLACEHOLDERS = "navigator.show.folder.placeholders"; public static final String NAVIGATOR_SORT_ALPHABETICALLY = "navigator.sort.case.insensitive"; public static final String NAVIGATOR_SORT_FOLDERS_FIRST = "navigator.sort.forlers.first"; public static final String PLATFORM_LANGUAGE = "platform.language"; public static final String TRANSACTIONS_SMART_COMMIT = "transaction.smart.commit"; public static final String TRANSACTIONS_SMART_COMMIT_RECOVER = "transaction.smart.commit.recover"; public static final String TRANSACTIONS_SHOW_NOTIFICATIONS = "transaction.show.notifications"; public static final String TRANSACTIONS_AUTO_CLOSE_ENABLED = "transaction.auto.close.enabled"; public static final String TRANSACTIONS_AUTO_CLOSE_TTL = "transaction.auto.close.ttl"; public static final String DICTIONARY_COLUMN_DIVIDER = "resultset.dictionary.columnDivider"; private static Bundle mainBundle;
    private static DBPPreferenceStore preferences;

    public static synchronized DBPPreferenceStore getPreferences() {
        if (preferences == null) {
            setMainBundle(ModelActivator.getInstance().getBundle());
        }
        return preferences;
    }

    public static void setPreferences(DBPPreferenceStore preferences) {
        ModelPreferences.preferences = preferences;
    }

    public static void setMainBundle(Bundle mainBundle) {
        ModelPreferences.mainBundle = mainBundle;
        ModelPreferences.preferences = new BundlePreferenceStore(mainBundle);
        initializeDefaultPreferences(ModelPreferences.preferences);
    }

    public static Bundle getMainBundle() {
        return mainBundle;
    }

    private static void initializeDefaultPreferences(DBPPreferenceStore store) {
        PrefUtils.setDefaultPreferenceValue(store, ModelPreferences.NOTIFICATIONS_ENABLED, true);
        PrefUtils.setDefaultPreferenceValue(store, ModelPreferences.NOTIFICATIONS_CLOSE_DELAY_TIMEOUT, 3000L);

        PrefUtils.setDefaultPreferenceValue(store, QUERY_ROLLBACK_ON_ERROR, false);
        PrefUtils.setDefaultPreferenceValue(store, EXECUTE_RECOVER_ENABLED, true);
        PrefUtils.setDefaultPreferenceValue(store, EXECUTE_RECOVER_RETRY_COUNT, 1);
        PrefUtils.setDefaultPreferenceValue(store, EXECUTE_CANCEL_CHECK_TIMEOUT, 0);

        PrefUtils.setDefaultPreferenceValue(store, CONNECTION_OPEN_TIMEOUT, 0);
        PrefUtils.setDefaultPreferenceValue(store, CONNECTION_VALIDATION_TIMEOUT, 10000);
        PrefUtils.setDefaultPreferenceValue(store, CONNECTION_CLOSE_TIMEOUT, 5000);

        PrefUtils.setDefaultPreferenceValue(store, SCRIPT_STATEMENT_DELIMITER, SQLConstants.DEFAULT_STATEMENT_DELIMITER);
        PrefUtils.setDefaultPreferenceValue(store, SCRIPT_IGNORE_NATIVE_DELIMITER, false);
        PrefUtils.setDefaultPreferenceValue(store, SCRIPT_STATEMENT_DELIMITER_BLANK, true);
        PrefUtils.setDefaultPreferenceValue(store, QUERY_REMOVE_TRAILING_DELIMITER, true);

        PrefUtils.setDefaultPreferenceValue(store, MEMORY_CONTENT_MAX_SIZE, 10000);
        PrefUtils.setDefaultPreferenceValue(store, META_SEPARATE_CONNECTION, true);
        PrefUtils.setDefaultPreferenceValue(store, META_CASE_SENSITIVE, false);
        PrefUtils.setDefaultPreferenceValue(store, META_USE_SERVER_SIDE_FILTERS, true);

        PrefUtils.setDefaultPreferenceValue(store, META_CLIENT_NAME_DISABLE, false);
        PrefUtils.setDefaultPreferenceValue(store, META_CLIENT_NAME_OVERRIDE, false);
        PrefUtils.setDefaultPreferenceValue(store, META_CLIENT_NAME_VALUE, "");

        PrefUtils.setDefaultPreferenceValue(store, CONNECT_USE_ENV_VARS, true);

        PrefUtils.setDefaultPreferenceValue(store, RESULT_NATIVE_DATETIME_FORMAT, false);
        PrefUtils.setDefaultPreferenceValue(store, RESULT_NATIVE_NUMERIC_FORMAT, false);
        PrefUtils.setDefaultPreferenceValue(store, RESULT_SCIENTIFIC_NUMERIC_FORMAT, false);
        PrefUtils.setDefaultPreferenceValue(store, RESULT_TRANSFORM_COMPLEX_TYPES, true);

        PrefUtils.setDefaultPreferenceValue(store, RESULT_SET_REREAD_ON_SCROLLING, true);
        PrefUtils.setDefaultPreferenceValue(store, RESULT_SET_READ_METADATA, true);
        PrefUtils.setDefaultPreferenceValue(store, RESULT_SET_READ_REFERENCES, true);
        PrefUtils.setDefaultPreferenceValue(store, RESULT_SET_MAX_ROWS, 200);

        PrefUtils.setDefaultPreferenceValue(store, CONTENT_HEX_ENCODING, GeneralUtils.getDefaultFileEncoding());
        PrefUtils.setDefaultPreferenceValue(store, CONTENT_CACHE_CLOB, true);
        PrefUtils.setDefaultPreferenceValue(store, CONTENT_CACHE_BLOB, false);
        PrefUtils.setDefaultPreferenceValue(store, CONTENT_CACHE_MAX_SIZE, 1000000);

        PrefUtils.setDefaultPreferenceValue(store, NET_TUNNEL_PORT_MIN, 10000);
        PrefUtils.setDefaultPreferenceValue(store, NET_TUNNEL_PORT_MAX, 60000);

        PrefUtils.setDefaultPreferenceValue(store, RESULT_SET_MAX_ROWS_USE_SQL, false);
        PrefUtils.setDefaultPreferenceValue(store, RESULT_SET_BINARY_PRESENTATION, DBConstants.BINARY_FORMATS[0].getId());
        PrefUtils.setDefaultPreferenceValue(store, RESULT_SET_BINARY_STRING_MAX_LEN, 32);
        PrefUtils.setDefaultPreferenceValue(store, RESULT_SET_USE_FETCH_SIZE, false);
        PrefUtils.setDefaultPreferenceValue(store, RESULT_SET_IGNORE_COLUMN_LABEL, false);

        PrefUtils.setDefaultPreferenceValue(store, QMConstants.PROP_HISTORY_DAYS, 90);
        PrefUtils.setDefaultPreferenceValue(store, QMConstants.PROP_ENTRIES_PER_PAGE, 200);
        PrefUtils.setDefaultPreferenceValue(store, QMConstants.PROP_OBJECT_TYPES,
            QMObjectType.toString(Arrays.asList(QMObjectType.txn, QMObjectType.query)));
        PrefUtils.setDefaultPreferenceValue(store, QMConstants.PROP_QUERY_TYPES, DBCExecutionPurpose.USER + "," + DBCExecutionPurpose.USER_FILTERED + "," + DBCExecutionPurpose.USER_SCRIPT);
        PrefUtils.setDefaultPreferenceValue(store, QMConstants.PROP_STORE_LOG_FILE, false);
        PrefUtils.setDefaultPreferenceValue(store, QMConstants.PROP_LOG_DIRECTORY, GeneralUtils.getMetadataFolder().toAbsolutePath().toString());

        PrefUtils.setDefaultPreferenceValue(store, SQL_PARAMETERS_ENABLED, true);
        PrefUtils.setDefaultPreferenceValue(store, SQL_PARAMETERS_IN_DDL_ENABLED, false);
        PrefUtils.setDefaultPreferenceValue(store, SQL_ANONYMOUS_PARAMETERS_ENABLED, false);
        PrefUtils.setDefaultPreferenceValue(store, SQL_ANONYMOUS_PARAMETERS_MARK, String.valueOf(SQLConstants.DEFAULT_PARAMETER_MARK));
        PrefUtils.setDefaultPreferenceValue(store, SQL_NAMED_PARAMETERS_PREFIX, String.valueOf(SQLConstants.DEFAULT_PARAMETER_PREFIX));
        PrefUtils.setDefaultPreferenceValue(store, SQL_CONTROL_COMMAND_PREFIX, String.valueOf(SQLConstants.DEFAULT_CONTROL_COMMAND_PREFIX));
        PrefUtils.setDefaultPreferenceValue(store, SQL_VARIABLES_ENABLED, true);
        PrefUtils.setDefaultPreferenceValue(store, SQL_FILTER_FORCE_SUBSELECT, false);

        PrefUtils.setDefaultPreferenceValue(store, SQL_FORMAT_KEYWORD_CASE, "");
        PrefUtils.setDefaultPreferenceValue(store, SQL_FORMAT_LF_BEFORE_COMMA, false);
        PrefUtils.setDefaultPreferenceValue(store, SQL_FORMAT_EXTERNAL_CMD, "");
        PrefUtils.setDefaultPreferenceValue(store, SQL_FORMAT_EXTERNAL_FILE, false);
        PrefUtils.setDefaultPreferenceValue(store, SQL_FORMAT_EXTERNAL_TIMEOUT, 2000);
        PrefUtils.setDefaultPreferenceValue(store, SQL_FORMAT_BREAK_BEFORE_CLOSE_BRACKET, false);
        PrefUtils.setDefaultPreferenceValue(store, SQL_FORMAT_INSERT_DELIMITERS_IN_EMPTY_LINES, false);

        PrefUtils.setDefaultPreferenceValue(store, READ_EXPENSIVE_PROPERTIES, false);
        PrefUtils.setDefaultPreferenceValue(store, READ_EXPENSIVE_STATISTICS, false);

        PrefUtils.setDefaultPreferenceValue(store, UI_PROXY_HOST, "");
        PrefUtils.setDefaultPreferenceValue(store, UI_PROXY_PORT, 1080);
        PrefUtils.setDefaultPreferenceValue(store, UI_PROXY_USER, "");
        PrefUtils.setDefaultPreferenceValue(store, UI_PROXY_PASSWORD, "");
        PrefUtils.setDefaultPreferenceValue(store, UI_DRIVERS_VERSION_UPDATE, false);
        PrefUtils.setDefaultPreferenceValue(store, UI_DRIVERS_HOME, "");
        PrefUtils.setDefaultPreferenceValue(store, UI_DRIVERS_SOURCES, "https://dbeaver.io/files/jdbc/");

        PrefUtils.setDefaultPreferenceValue(store, ModelPreferences.NAVIGATOR_SHOW_FOLDER_PLACEHOLDERS, true);
        PrefUtils.setDefaultPreferenceValue(store, ModelPreferences.NAVIGATOR_SORT_ALPHABETICALLY, false);
        PrefUtils.setDefaultPreferenceValue(store, ModelPreferences.NAVIGATOR_SORT_FOLDERS_FIRST, true);

        PrefUtils.setDefaultPreferenceValue(store, ModelPreferences.TRANSACTIONS_SMART_COMMIT, false);
        PrefUtils.setDefaultPreferenceValue(store, ModelPreferences.TRANSACTIONS_SMART_COMMIT_RECOVER, true);
        PrefUtils.setDefaultPreferenceValue(store, ModelPreferences.TRANSACTIONS_AUTO_CLOSE_ENABLED, false);
        PrefUtils.setDefaultPreferenceValue(store, ModelPreferences.TRANSACTIONS_AUTO_CLOSE_TTL, 15 * 60);
        PrefUtils.setDefaultPreferenceValue(store, ModelPreferences.TRANSACTIONS_SHOW_NOTIFICATIONS, true);

        PrefUtils.setDefaultPreferenceValue(store, ModelPreferences.DICTIONARY_COLUMN_DIVIDER, " ");

        DataFormatterProfile.initDefaultPreferences(store, Locale.getDefault());
    }
}
