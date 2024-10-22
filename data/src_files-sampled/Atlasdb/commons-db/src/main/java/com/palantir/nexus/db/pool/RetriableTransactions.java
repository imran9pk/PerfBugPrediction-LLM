package com.palantir.nexus.db.pool;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.palantir.logsafe.Preconditions;
import com.palantir.logsafe.SafeArg;
import com.palantir.logsafe.UnsafeArg;
import com.palantir.logsafe.logger.SafeLogger;
import com.palantir.logsafe.logger.SafeLoggerFactory;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.annotation.Nullable;

public final class RetriableTransactions {
    private static final SafeLogger log = SafeLoggerFactory.get(RetriableTransactions.class);

    private RetriableTransactions() {
        }

    private static final long MAX_DELAY_MS = 3 * 60 * 1000;
    private static final String TABLE_NAME = "pt_retriable_txn_log_v1";

    private static <T> TransactionResult<T> runSimple(ConnectionManager cm, RetriableWriteTransaction<T> tx) {
        Connection c = null;
        try {
            c = cm.getConnection();
            c.setAutoCommit(false);
            T ret = tx.run(c);
            c.commit();
            return TransactionResult.success(ret);
        } catch (Throwable t) {
            return TransactionResult.failure(t);
        } finally {
            if (c != null) {
                try {
                    c.close();
                } catch (Throwable t) {
                    log.warn("A problem happened trying to close a connection.", t);
                }
            }
        }
    }

    public enum TransactionStatus {
        SUCCESSFUL,
        FAILED,
        UNKNOWN;
    }

    public static final class TransactionResult<T> {
        private final TransactionStatus status;
        private final @Nullable T resultValue;
        private final Optional<Throwable> error;

        private TransactionResult(TransactionStatus status, @Nullable T resultValue, Optional<Throwable> error) {
            this.status = status;
            this.resultValue = resultValue;
            this.error = error;
        }

        public static <T> TransactionResult<T> success(T resultValue) {
            return new TransactionResult<T>(TransactionStatus.SUCCESSFUL, resultValue, Optional.<Throwable>empty());
        }

        public static <T> TransactionResult<T> failure(Throwable error) {
            return new TransactionResult<T>(TransactionStatus.FAILED, null Optional.of(error));
        }

        public static <T> TransactionResult<T> unknown(Throwable error) {
            return new TransactionResult<T>(TransactionStatus.UNKNOWN, null Optional.of(error));
        }

        public TransactionStatus getStatus() {
            return status;
        }

        public @Nullable T getResultValue() {
            Preconditions.checkState(
                    status.equals(TransactionStatus.SUCCESSFUL),
                    "Trying to get result from a transaction which never succeeded");
            return resultValue;
        }

        public Throwable getError() {
            return error.get();
        }
    }
    public static <T> TransactionResult<T> run(final ConnectionManager cm, final RetriableWriteTransaction<T> tx) {
        switch (cm.getDbType()) {
            case ORACLE:
            case POSTGRESQL:
            case H2_MEMORY:
                break;

            default:
                return runSimple(cm, tx);
        }

        class LexicalHelper {
            long startTimeMs = System.currentTimeMillis();
            boolean pending = false;
            UUID id = null;
            T result = null;

            public TransactionResult<T> run() {
                boolean createdTxTable = false;
                while (true) {
                    long attemptTimeMs = System.currentTimeMillis();
                    try {
                        if (!createdTxTable) {
                            createTxTable(cm);
                            createdTxTable = true;
                        }

                        if (!pending) {
                            attemptTx();

                            return TransactionResult.success(result);
                        } else {
                            if (attemptVerify()) {
                                return TransactionResult.success(result);
                            }

                            pending = false;
                            id = null;
                            result = null;
                        }
                    } catch (SQLException e) {
                        long now = System.currentTimeMillis();
                        if (log.isTraceEnabled()) {
                            log.trace(
                                    "Got exception for retriable write transaction, startTimeMs = {}, attemptTimeMs ="
                                            + " {}, now = {}",
                                    SafeArg.of("startTimeMs", startTimeMs),
                                    SafeArg.of("attemptTimeMs", attemptTimeMs),
                                    SafeArg.of("now", now),
                                    e);
                        }
                        if (cm.isClosed()) {
                            log.warn("Aborting transaction retry, underlying connection manager is closed", e);
                            return TransactionResult.failure(e);
                        }
                        if (shouldStillRetry(startTimeMs, attemptTimeMs)) {
                            long attemptLengthMs = now - attemptTimeMs;
                            long totalLengthMs = now - startTimeMs;
                            log.info(
                                    "Swallowing possible transient exception for retriable transaction, last attempt"
                                            + " took {} ms, total attempts have taken {}",
                                    SafeArg.of("attemptLengthMs", attemptLengthMs),
                                    SafeArg.of("totalLengthMs", totalLengthMs),
                                    e);
                            continue;
                        }
                        if (pending) {
                            log.error(
                                    "Giving up on [verification of] retriable write transaction that might have"
                                            + " actually commited!",
                                    e);
                            return TransactionResult.unknown(e);
                        }
                        return TransactionResult.failure(e);
                    } catch (Throwable t) {
                        return TransactionResult.failure(t);
                    }
                }
            }

            private void attemptTx() throws SQLException {
                boolean ret = false;
                try {
                    Connection c = cm.getConnection();
                    try {
                        c.setAutoCommit(false);
                        T newResult = tx.run(c);
                        UUID newId = UUID.randomUUID();
                        addTxLog(c, newId);

                        pending = true;
                        id = newId;
                        result = newResult;
                        c.commit();

                        ret = true;

                        cleanTxLog(c, id);
                        c.commit();

                        return;
                    } finally {
                        c.close();
                    }
                } catch (SQLException e) {
                    if (ret) {
                        squelch(e);
                        return;
                    }
                    throw e;
                }
            }

            private boolean attemptVerify() throws SQLException {
                boolean ret = false;
                try {
                    Connection c = cm.getConnection();
                    try {
                        ret = checkTxLog(c, id);
                        if (ret) {
                            cleanTxLog(c, id);
                        }
                        return ret;
                    } finally {
                        c.close();
                    }
                } catch (SQLException e) {
                    if (ret) {
                        squelch(e);
                        return ret;
                    }
                    throw e;
                }
            }

            private void squelch(SQLException e) {
                log.warn(
                        "Squelching SQLException while trying to clean up retriable write transaction id {}",
                        UnsafeArg.of("id", id),
                        e);
            }
        }
        return new LexicalHelper().run();
    }

    private static final LoadingCache<ConnectionManager, AtomicBoolean> createdTxTables = CacheBuilder.newBuilder()
            .weakKeys()
            .build(new CacheLoader<ConnectionManager, AtomicBoolean>() {
                @Override
                public AtomicBoolean load(ConnectionManager cm) {
                    return new AtomicBoolean(false);
                }
            });

    private static void createTxTable(ConnectionManager cm) throws SQLException {
        AtomicBoolean createdTxTable = createdTxTables.getUnchecked(cm);
        if (createdTxTable.get()) {
            return;
        }

        String varcharType = null;
        switch (cm.getDbType()) {
            case ORACLE:
                varcharType = "VARCHAR2";
                break;

            case POSTGRESQL:
            case H2_MEMORY:
                varcharType = "VARCHAR";
                break;

            default:
                throw new IllegalStateException();
        }
        if (varcharType == null) {
            throw new IllegalStateException();
        }

        try {
            Connection c = cm.getConnection();
            try {
                c.setAutoCommit(false);
                addTxLog(c, UUID.randomUUID());
                c.rollback();
            } finally {
                c.close();
            }
            } catch (SQLException e) {
            log.info(
                    "The table {} has not been created yet, so we will try to create it.",
                    UnsafeArg.of("tableName", TABLE_NAME));
            log.debug(
                    "To check whether the table exists we tried to use it. This caused an exception indicating that it"
                            + " did not exist. The exception was: ",
                    e);
            Connection c = cm.getConnection();
            try {
                c.createStatement()
                        .execute("CREATE TABLE " + TABLE_NAME + " (id " + varcharType
                                + "(36) PRIMARY KEY, created TIMESTAMP DEFAULT CURRENT_TIMESTAMP)");
            } finally {
                c.close();
            }
            }

        createdTxTable.set(true);
    }

    private static void addTxLog(Connection c, UUID id) throws SQLException {
        PreparedStatement ps = c.prepareStatement("INSERT INTO " + TABLE_NAME + " (id) VALUES (?)");
        try {
            ps.setString(1, id.toString());
            ps.executeUpdate();
        } finally {
            ps.close();
        }
    }

    private static boolean checkTxLog(Connection c, UUID id) throws SQLException {
        PreparedStatement ps = c.prepareStatement("SELECT 1 FROM " + TABLE_NAME + " WHERE id = ?");
        try {
            ps.setString(1, id.toString());
            ResultSet rs = ps.executeQuery();
            try {
                return rs.next();
            } finally {
                rs.close();
            }
        } finally {
            ps.close();
        }
    }

    private static void cleanTxLog(Connection c, UUID id) throws SQLException {
        PreparedStatement ps = c.prepareStatement("DELETE FROM " + TABLE_NAME + " WHERE id = ?");
        try {
            ps.setString(1, id.toString());
            ps.executeUpdate();
        } finally {
            ps.close();
        }
    }

    private static boolean shouldStillRetry(long startTimeMs, long attemptTimeMs) {
        return attemptTimeMs <= startTimeMs + MAX_DELAY_MS;
    }
}
