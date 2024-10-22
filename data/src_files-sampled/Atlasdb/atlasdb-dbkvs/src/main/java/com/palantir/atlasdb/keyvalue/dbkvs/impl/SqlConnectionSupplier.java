package com.palantir.atlasdb.keyvalue.dbkvs.impl;

import com.palantir.exception.PalantirSqlException;
import com.palantir.nexus.db.sql.SqlConnection;
import java.io.Closeable;
import java.util.function.Supplier;

public interface SqlConnectionSupplier extends Supplier<SqlConnection>, Closeable {
    @Override
    SqlConnection get();

    @Override
    void close() throws PalantirSqlException;
}
