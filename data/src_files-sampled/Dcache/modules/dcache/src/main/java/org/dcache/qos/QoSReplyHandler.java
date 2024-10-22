package org.dcache.qos;

import static com.google.common.util.concurrent.Uninterruptibles.getUninterruptibly;

import com.google.common.util.concurrent.ListenableFuture;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import org.dcache.pinmanager.PinManagerPinMessage;
import org.dcache.pool.migration.PoolMigrationMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class QoSReplyHandler {

    private static final Logger LOGGER
          = LoggerFactory.getLogger(QoSReplyHandler.class);

    private final Executor executor;

    protected PoolMigrationMessage migrationReply;
    protected PinManagerPinMessage pinReply;

    private ListenableFuture<PoolMigrationMessage> migrationFuture;
    private ListenableFuture<PinManagerPinMessage> pinFuture;

    protected QoSReplyHandler(Executor executor) {
        this.executor = executor;
    }

    public synchronized void cancel() {
        if (migrationFuture != null && migrationReply == null) {
            migrationFuture.cancel(true);
            LOGGER.debug("Cancelled migrationFuture.");
            migrationFuture = null;
        }

        if (pinFuture != null && pinReply == null) {
            pinFuture.cancel(true);
            LOGGER.debug("Cancelled pinFuture.");
            pinFuture = null;
        }
    }

    public String toString() {
        return "(migration " + migrationFuture + ", "
              + migrationReply + ")(pin "
              + pinFuture + ", " + pinReply + ")";
    }

    public synchronized boolean done() {
        LOGGER.trace("done called: {}.", this);
        return (migrationFuture == null || migrationReply != null) &&
              (pinFuture == null || pinReply != null);
    }

    public void listen() {
        if (migrationFuture != null) {
            migrationFuture.addListener(() -> handleMigrationReply(), executor);
        }

        LOGGER.trace("listen called: {}.", this);
    }

    public synchronized void setMigrationFuture(ListenableFuture<PoolMigrationMessage> future) {
        this.migrationFuture = future;
    }

    public synchronized void setPinFuture(ListenableFuture<PinManagerPinMessage> future) {
        this.pinFuture = future;
    }

    protected abstract void migrationFailure(Object error);

    protected abstract void migrationSuccess();

    protected abstract void pinFailure(Object error);

    protected abstract void pinSuccess();

    private synchronized void handleMigrationReply() {
        if (migrationFuture == null) {
            LOGGER.debug("No migration future set, no reply expected.");
            return;
        }

        Object error = null;
        try {
            migrationReply = getUninterruptibly(migrationFuture);
            if (migrationReply.getReturnCode() != 0) {
                error = migrationReply.getErrorObject();
            }
        } catch (CancellationException e) {
            } catch (ExecutionException e) {
            error = e.getCause();
        }

        if (error != null) {
            migrationFailure(error);
        } else {
            migrationSuccess();
        }
    }

    public synchronized void handlePinReply() {
        if (pinFuture == null) {
            LOGGER.debug("No pin future set, no reply expected.");
            return;
        }

        LOGGER.debug("poll, checking pin request future.isDone().");
        if (!pinFuture.isDone()) {
            return;
        }

        Object error = null;
        try {
            pinReply = getUninterruptibly(pinFuture);
            if (pinReply.getReturnCode() != 0) {
                error = pinReply.getErrorObject();
            }
        } catch (ExecutionException e) {
            error = e.getCause();
        }

        if (error != null) {
            pinFailure(error);
        } else {
            pinSuccess();
        }
    }
}
