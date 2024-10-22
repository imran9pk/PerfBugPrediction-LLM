package com.palantir.nexus.db.sql;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.palantir.exception.PalantirInterruptedException;
import com.palantir.exception.PalantirSqlException;
import com.palantir.nexus.db.DBType;
import com.palantir.nexus.db.sql.BasicSQL.AutoClose;
import com.palantir.nexus.db.sql.BasicSQLString.FinalSQLString;
import com.palantir.nexus.db.sql.SQLString.RegisteredSQLString;
import com.palantir.sql.PreparedStatements;
import java.sql.Connection;
import java.sql.PreparedStatement;
import javax.annotation.Nullable;

@SuppressWarnings("BadAssert") public final class SqlConnectionHelper {

    private final BasicSQL basicSql;

    public SqlConnectionHelper(BasicSQL basicSql) {
        this.basicSql = basicSql;
    }

    void executeUnregisteredQuery(Connection c, String sql, Object... vs) throws PalantirSqlException {
        basicSql.execute(c, SQLString.getUnregisteredQuery(sql), vs, AutoClose.TRUE);
    }

    int executeUnregisteredQueryCountRows(Connection c, String sql, Object... vs) throws PalantirSqlException {
        return executeCountRows(c, SQLString.getUnregisteredQuery(sql), vs);
    }

    PreparedStatement execute(Connection c, String key, Object... vs) throws PalantirSqlException {
        return basicSql.execute(c, SQLString.getByKey(key, c), vs, AutoClose.TRUE);
    }

    public int executeCountRows(Connection c, String key, Object... vs) throws PalantirSqlException {
        return executeCountRows(c, SQLString.getByKey(key, c), vs);
    }

    private int executeCountRows(Connection c, FinalSQLString sql, Object... vs) throws PalantirSqlException {
        PreparedStatement ps = null;
        try {
            ps = basicSql.execute(c, sql, vs, AutoClose.FALSE);
            return PreparedStatements.getUpdateCount(ps);
        } finally {
            BasicSQL.closeSilently(ps);
        }
    }

    long selectCount(Connection c, String tableName) throws PalantirSqlException, PalantirInterruptedException {
        return selectCount(c, tableName, null, new Object[] {});
    }

    long selectCount(Connection c, String tableName, String whereClause, Object... vs)
            throws PalantirSqlException, PalantirInterruptedException {
        String sql = "SELECT count(*) from " + tableName; if (whereClause != null) {
            sql += " WHERE " + whereClause; }
        return selectLongUnregisteredQuery(c, sql, vs);
    }

    boolean selectExistsUnregisteredQuery(Connection c, String sql, Object... vs)
            throws PalantirSqlException, PalantirInterruptedException {
        return basicSql.selectExistsInternal(c, SQLString.getUnregisteredQuery(sql), vs);
    }

    boolean selectExists(Connection c, String key, Object... vs)
            throws PalantirSqlException, PalantirInterruptedException {
        return basicSql.selectExistsInternal(c, SQLString.getByKey(key, c), vs);
    }

    int selectIntegerUnregisteredQuery(Connection c, String sql, Object... vs)
            throws PalantirSqlException, PalantirInterruptedException {
        return basicSql.selectIntegerInternal(c, SQLString.getUnregisteredQuery(sql), vs);
    }

    int selectInteger(Connection c, String key, Object... vs)
            throws PalantirSqlException, PalantirInterruptedException {
        return basicSql.selectIntegerInternal(c, SQLString.getByKey(key, c), vs);
    }

    long selectLongUnregisteredQuery(Connection c, String sql, Object... vs)
            throws PalantirSqlException, PalantirInterruptedException {
        return basicSql.selectLongInternal(c, SQLString.getUnregisteredQuery(sql), vs, null, true);
    }

    long selectLong(Connection c, String key, Object... vs) throws PalantirSqlException, PalantirInterruptedException {
        return basicSql.selectLongInternal(c, SQLString.getByKey(key, c), vs, null, true);
    }

    long selectLong(Connection c, RegisteredSQLString sql, Object... vs)
            throws PalantirSqlException, PalantirInterruptedException {
        return selectLong(c, sql.getKey(), vs);
    }

    Long selectLongWithDefaultUnregisteredQuery(Connection c, String sql, Long defaultVal, Object... vs)
            throws PalantirSqlException, PalantirInterruptedException {
        return basicSql.selectLongInternal(c, SQLString.getUnregisteredQuery(sql), vs, defaultVal, false);
    }

    Long selectLongWithDefault(Connection c, String key, Long defaultVal, Object... vs)
            throws PalantirSqlException, PalantirInterruptedException {
        return basicSql.selectLongInternal(c, SQLString.getByKey(key, c), vs, defaultVal, false);
    }

    AgnosticLightResultSet selectLightResultSetUnregisteredQuery(Connection c, String sql, Object... vs) {
        return selectLightResultSetUnregisteredQueryWithFetchSize(c, sql, null, vs);
    }

    AgnosticLightResultSet selectLightResultSetUnregisteredQueryWithFetchSize(
            Connection c, String sql, @Nullable Integer fetchSize, Object... vs) {
        return basicSql.selectLightResultSetSpecifyingDBType(
                c, SQLString.getUnregisteredQuery(sql), vs, DBType.getTypeFromConnection(c), fetchSize);
    }

    AgnosticLightResultSet selectLightResultSet(Connection c, String key, Object... vs)
            throws PalantirSqlException, PalantirInterruptedException {
        return selectLightResultSet(c, SQLString.getByKey(key, c), vs);
    }

    AgnosticLightResultSet selectLightResultSet(Connection c, FinalSQLString finalSql, Object... vs)
            throws PalantirSqlException {
        return basicSql.selectLightResultSetSpecifyingDBType(c, finalSql, vs, DBType.getTypeFromConnection(c), null);
    }

    AgnosticLightResultSet selectLightResultSet(Connection c, RegisteredSQLString sql, Object... vs)
            throws PalantirSqlException, PalantirInterruptedException {
        DBType dbType = DBType.getTypeFromConnection(c);
        return basicSql.selectLightResultSetSpecifyingDBType(
                c, SQLString.getByKey(sql.getKey(), dbType), vs, dbType, null);
    }

    AgnosticResultSet selectResultSetUnregisteredQuery(Connection c, String sql, Object... vs)
            throws PalantirSqlException, PalantirInterruptedException {
        return basicSql.selectResultSetSpecifyingDBType(
                c, SQLString.getUnregisteredQuery(sql), vs, DBType.getTypeFromConnection(c));
    }

    AgnosticResultSet selectResultSet(Connection c, String key, Object... vs)
            throws PalantirSqlException, PalantirInterruptedException {
        DBType dbType = DBType.getTypeFromConnection(c);
        return basicSql.selectResultSetSpecifyingDBType(c, SQLString.getByKey(key, dbType), vs, dbType);
    }

    AgnosticResultSet selectResultSet(Connection c, RegisteredSQLString sql, Object... vs)
            throws PalantirSqlException, PalantirInterruptedException {
        return selectResultSet(c, sql.getKey(), vs);
    }

    boolean updateUnregisteredQuery(Connection c, String sql, Object... vs) throws PalantirSqlException {
        basicSql.updateInternal(c, SQLString.getUnregisteredQuery(sql), vs, AutoClose.TRUE);
        return true;
    }

    boolean update(Connection c, String key, Object... vs) throws PalantirSqlException {
        basicSql.updateInternal(c, SQLString.getByKey(key, c), vs, AutoClose.TRUE);
        return true;
    }

    boolean update(Connection c, RegisteredSQLString sql, Object... vs) throws PalantirSqlException {
        return update(c, sql.getKey(), vs);
    }

    int updateCountRowsUnregisteredQuery(Connection c, String sql, Object... vs) throws PalantirSqlException {
        return basicSql.updateCountRowsInternal(c, SQLString.getUnregisteredQuery(sql), vs);
    }

    int updateCountRows(Connection c, String key, Object... vs) throws PalantirSqlException {
        return basicSql.updateCountRowsInternal(c, SQLString.getByKey(key, c), vs);
    }

    int updateCountRows(Connection c, RegisteredSQLString sql, Object... vs) throws PalantirSqlException {
        return updateCountRows(c, sql.getKey(), vs);
    }

    void updateManyUnregisteredQuery(Connection c, String sql, Iterable<Object[]> list) throws PalantirSqlException {
        basicSql.updateMany(c, SQLString.getUnregisteredQuery(sql), Iterables.toArray(list, Object[].class));
    }

    void updateMany(Connection c, String key) throws PalantirSqlException {
        updateMany(c, key, ImmutableList.<Object[]>of());
    }

    void updateMany(Connection c, String key, Iterable<Object[]> list) throws PalantirSqlException {
        basicSql.updateMany(c, SQLString.getByKey(key, c), Iterables.toArray(list, Object[].class));
    }

    void updateMany(Connection c, RegisteredSQLString sql) throws PalantirSqlException {
        updateMany(c, sql.getKey(), ImmutableList.<Object[]>of());
    }

    void updateMany(Connection c, RegisteredSQLString sql, Iterable<Object[]> list) throws PalantirSqlException {
        updateMany(c, sql.getKey(), list);
    }

    boolean insertOneUnregisteredQuery(Connection c, String sql, Object... vs) throws PalantirSqlException {
        final int updated = insertOneCountRowsUnregisteredQuery(c, sql, vs);
        assert updated == 1 : "expected 1 update, got : " + updated; return true;
    }

    boolean insertOne(Connection c, String key, Object... vs) throws PalantirSqlException {
        final int updated = insertOneCountRows(c, key, vs);
        assert updated == 1 : "expected 1 update, got : " + updated; return true;
    }

    boolean insertOne(Connection c, RegisteredSQLString sql, Object... vs) throws PalantirSqlException {
        return insertOne(c, sql.getKey(), vs);
    }

    int insertOneCountRowsUnregisteredQuery(Connection c, String sql, Object... vs) throws PalantirSqlException {
        return basicSql.insertOneCountRowsInternal(c, SQLString.getUnregisteredQuery(sql), vs);
    }

    int insertOneCountRows(Connection c, String key, Object... vs) throws PalantirSqlException {
        return basicSql.insertOneCountRowsInternal(c, SQLString.getByKey(key, c), vs);
    }

    int insertOneCountRows(Connection c, RegisteredSQLString sql, Object... vs) throws PalantirSqlException {
        return insertOneCountRows(c, sql.getKey(), vs);
    }

    boolean insertManyUnregisteredQuery(Connection c, String sql, Iterable<Object[]> list) throws PalantirSqlException {
        return basicSql.insertMany(c, SQLString.getUnregisteredQuery(sql), Iterables.toArray(list, Object[].class));
    }

    boolean insertMany(Connection c, String key, Iterable<Object[]> list) throws PalantirSqlException {
        return basicSql.insertMany(c, SQLString.getByKey(key, c), Iterables.toArray(list, Object[].class));
    }

    boolean insertMany(Connection c, RegisteredSQLString sql, Iterable<Object[]> list) throws PalantirSqlException {
        return insertMany(c, sql.getKey(), list);
    }
}
