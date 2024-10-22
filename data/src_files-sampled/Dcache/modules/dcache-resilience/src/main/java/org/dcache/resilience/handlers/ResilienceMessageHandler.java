package org.dcache.resilience.handlers;

import diskCacheV111.util.CacheException;
import diskCacheV111.vehicles.Message;
import diskCacheV111.vehicles.PnfsAddCacheLocationMessage;
import diskCacheV111.vehicles.PnfsClearCacheLocationMessage;
import diskCacheV111.vehicles.PoolMgrSelectReadPoolMsg;
import dmg.cells.nucleus.CellMessage;
import dmg.cells.nucleus.CellMessageReceiver;
import java.util.concurrent.ExecutorService;
import org.dcache.pool.migration.PoolMigrationCopyFinishedMessage;
import org.dcache.resilience.data.FileUpdate;
import org.dcache.resilience.data.MessageType;
import org.dcache.resilience.data.PoolInfoMap;
import org.dcache.resilience.data.PoolStateUpdate;
import org.dcache.resilience.util.ExceptionMessage;
import org.dcache.resilience.util.MessageGuard;
import org.dcache.resilience.util.MessageGuard.Status;
import org.dcache.resilience.util.OperationStatistics;
import org.dcache.vehicles.CorruptFileMessage;
import org.dcache.vehicles.PnfsSetFileAttributes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ResilienceMessageHandler implements CellMessageReceiver {

    private static final Logger LOGGER = LoggerFactory.getLogger(
          ResilienceMessageHandler.class);
    private static final Logger ACTIVITY_LOGGER =
          LoggerFactory.getLogger("org.dcache.resilience-log");

    private MessageGuard messageGuard;
    private FileOperationHandler fileOperationHandler;
    private PoolOperationHandler poolOperationHandler;
    private PoolInfoMap poolInfoMap;
    private OperationStatistics counters;
    private ExecutorService updateService;

    public void handleInternalMessage(PoolStateUpdate update) {
        if (!messageGuard.isEnabled()) {
            LOGGER.trace("Ignoring pool state update "
                  + "because message guard is disabled.");
            return;
        }

        updateService.submit(() -> {
            poolInfoMap.updatePoolStatus(update);
            if (poolInfoMap.isInitialized(update.pool)) {
                counters.incrementMessage(update.getStatus().getMessageType().name());
                poolOperationHandler.handlePoolStatusChange(update);
            }
        });
    }

    public void messageArrived(CorruptFileMessage message) {
        ACTIVITY_LOGGER.info("Received notice that file {} on pool {} is corrupt.",
              message.getPnfsId(), message.getPool());
        if (messageGuard.getStatus("CorruptFileMessage", message)
              == Status.DISABLED) {
            return;
        }
        handleBrokenFile(message);
    }

    public void messageArrived(PnfsAddCacheLocationMessage message) {
        ACTIVITY_LOGGER.info("Received notice that pool {} received file {}.",
              message.getPoolName(), message.getPnfsId());
        if (messageGuard.getStatus("PnfsAddCacheLocationMessage", message)
              != Status.EXTERNAL) {
            return;
        }
        handleAddCacheLocation(message);
    }

    public void messageArrived(PnfsClearCacheLocationMessage message) {
        ACTIVITY_LOGGER.info("Received notice that pool {} cleared file {}.",
              message.getPoolName(), message.getPnfsId());
        if (messageGuard.getStatus("PnfsClearCacheLocationMessage", message)
              != Status.EXTERNAL) {
            return;
        }
        handleClearCacheLocation(message);
    }

    public void messageArrived(PoolMigrationCopyFinishedMessage message) {
        ACTIVITY_LOGGER.info("Received notice that transfer {} of file "
                    + "{} from {} has finished.",
              message.getUUID(), message.getPnfsId(), message.getPool());
        fileOperationHandler.handleMigrationCopyFinished(message);
    }

    public void messageArrived(CellMessage message, PoolMgrSelectReadPoolMsg reply) {
        ACTIVITY_LOGGER.info("Received notice that file {} has been staged to pool {}",
              reply.getPool(),
              reply.getPnfsId());
        if (messageGuard.getStatus("PoolMgrSelectReadPoolMsg", message)
              == Status.DISABLED) {
            return;
        }
        handleStagingRetry(reply);
    }

    public void messageArrived(PnfsSetFileAttributes message) {
        ACTIVITY_LOGGER.info("Received notice that qos for file {} has changed.",
              message.getPnfsId());
        if (messageGuard.getStatus("FileQoSMessage", message)
              == Status.DISABLED) {
            return;
        }
        handleQoSModification(message);
    }

    public void processBackloggedMessage(Message message) {
        if (message instanceof CorruptFileMessage) {
            handleBrokenFile((CorruptFileMessage) message);
        } else if (message instanceof PnfsClearCacheLocationMessage) {
            handleClearCacheLocation((PnfsClearCacheLocationMessage) message);
        } else if (message instanceof PnfsAddCacheLocationMessage) {
            handleAddCacheLocation((PnfsAddCacheLocationMessage) message);
        } else if (message instanceof PoolMgrSelectReadPoolMsg) {
            handleStagingRetry((PoolMgrSelectReadPoolMsg) message);
        } else if (message instanceof PnfsSetFileAttributes) {
            handleQoSModification((PnfsSetFileAttributes) message);
        }
    }

    public void setCounters(OperationStatistics counters) {
        this.counters = counters;
    }

    public void setMessageGuard(MessageGuard messageGuard) {
        this.messageGuard = messageGuard;
    }

    public void setFileOperationHandler(
          FileOperationHandler fileOperationHandler) {
        this.fileOperationHandler = fileOperationHandler;
    }

    public void setPoolInfoMap(PoolInfoMap poolInfoMap) {
        this.poolInfoMap = poolInfoMap;
    }

    public void setPoolOperationHandler(PoolOperationHandler poolOperationHandler) {
        this.poolOperationHandler = poolOperationHandler;
    }

    public void setUpdateService(ExecutorService updateService) {
        this.updateService = updateService;
    }

    private void handleAddCacheLocation(PnfsAddCacheLocationMessage message) {
        counters.incrementMessage(MessageType.ADD_CACHE_LOCATION.name());
        updatePnfsLocation(new FileUpdate(message.getPnfsId(), message.getPoolName(),
              MessageType.ADD_CACHE_LOCATION, true));
    }

    private void handleBrokenFile(CorruptFileMessage message) {
        counters.incrementMessage(MessageType.CORRUPT_FILE.name());
        updatePnfsLocation(new FileUpdate(message.getPnfsId(),
              message.getPool(),
              MessageType.CORRUPT_FILE,
              true));
    }

    private void handleClearCacheLocation(PnfsClearCacheLocationMessage message) {
        counters.incrementMessage(MessageType.CLEAR_CACHE_LOCATION.name());
        updatePnfsLocation(new FileUpdate(message.getPnfsId(),
              message.getPoolName(),
              MessageType.CLEAR_CACHE_LOCATION,
              false));
    }

    private void handleQoSModification(PnfsSetFileAttributes message) {
        counters.incrementMessage(MessageType.QOS_MODIFIED.name());
        updatePnfsLocation(new FileUpdate(message.getPnfsId(), null,
              MessageType.QOS_MODIFIED, true));
    }

    private void handleStagingRetry(PoolMgrSelectReadPoolMsg reply) {
        updateService.submit(() -> {
            fileOperationHandler.handleStagingReply(reply);
        });
    }

    private void updatePnfsLocation(FileUpdate data) {
        updateService.submit(() -> {
            try {
                fileOperationHandler.handleLocationUpdate(data);
            } catch (CacheException e) {
                LOGGER.error("Error in verification of location update data {}: {}",
                      data, new ExceptionMessage(e));
            }
        });
    }
}
