package org.elasticsearch.persistent;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.message.ParameterizedMessage;
import org.elasticsearch.action.ActionListener;
import org.elasticsearch.action.admin.cluster.node.tasks.cancel.CancelTasksRequest;
import org.elasticsearch.common.Nullable;
import org.elasticsearch.common.unit.TimeValue;
import org.elasticsearch.tasks.CancellableTask;
import org.elasticsearch.tasks.TaskId;
import org.elasticsearch.tasks.TaskManager;

import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;

public class AllocatedPersistentTask extends CancellableTask {

    private final AtomicReference<State> state;

    private volatile String persistentTaskId;
    private volatile long allocationId;
    private volatile @Nullable Exception failure;
    private volatile PersistentTasksService persistentTasksService;
    private volatile Logger logger;
    private volatile TaskManager taskManager;

    public AllocatedPersistentTask(long id, String type, String action, String description, TaskId parentTask,
                                   Map<String, String> headers) {
        super(id, type, action, description, parentTask, headers);
        this.state = new AtomicReference<>(State.STARTED);
    }

    @Override
    public boolean shouldCancelChildrenOnCancellation() {
        return true;
    }

    @Override
    public final boolean cancelOnParentLeaving() {
        return false;
    }

    @Override
    public Status getStatus() {
        return new PersistentTasksNodeService.Status(state.get());
    }

    public void updatePersistentTaskState(final PersistentTaskState state,
                                          final ActionListener<PersistentTasksCustomMetaData.PersistentTask<?>> listener) {
        persistentTasksService.sendUpdateStateRequest(persistentTaskId, allocationId, state, listener);
    }

    public String getPersistentTaskId() {
        return persistentTaskId;
    }

    void init(PersistentTasksService persistentTasksService, TaskManager taskManager, Logger logger, String persistentTaskId, long
            allocationId) {
        this.persistentTasksService = persistentTasksService;
        this.logger = logger;
        this.taskManager = taskManager;
        this.persistentTaskId = persistentTaskId;
        this.allocationId = allocationId;
    }

    public Exception getFailure() {
        return failure;
    }

    public long getAllocationId() {
        return allocationId;
    }

    public void waitForPersistentTask(final Predicate<PersistentTasksCustomMetaData.PersistentTask<?>> predicate,
                                      final @Nullable TimeValue timeout,
                                      final PersistentTasksService.WaitForPersistentTaskListener<?> listener) {
        persistentTasksService.waitForPersistentTaskCondition(persistentTaskId, predicate, timeout, listener);
    }

    protected final boolean isCompleted() {
        return state.get() == State.COMPLETED;
    }

    boolean markAsCancelled() {
        return state.compareAndSet(State.STARTED, State.PENDING_CANCEL);
    }

    public void markAsCompleted() {
        completeAndNotifyIfNeeded(null);
    }

    public void markAsFailed(Exception e) {
        if (CancelTasksRequest.DEFAULT_REASON.equals(getReasonCancelled())) {
            completeAndNotifyIfNeeded(null);
        } else {
            completeAndNotifyIfNeeded(e);
        }
    }

    private void completeAndNotifyIfNeeded(@Nullable Exception failure) {
        final State prevState = state.getAndSet(State.COMPLETED);
        if (prevState == State.COMPLETED) {
            logger.warn("attempt to complete task [{}] with id [{}] in the [{}] state", getAction(), getPersistentTaskId(), prevState);
        } else {
            if (failure != null) {
                logger.warn(() -> new ParameterizedMessage("task {} failed with an exception", getPersistentTaskId()), failure);
            }
            try {
                this.failure = failure;
                if (prevState == State.STARTED) {
                    logger.trace("sending notification for completed task [{}] with id [{}]", getAction(), getPersistentTaskId());
                    persistentTasksService.sendCompletionRequest(getPersistentTaskId(), getAllocationId(), failure, new
                            ActionListener<PersistentTasksCustomMetaData.PersistentTask<?>>() {
                                @Override
                                public void onResponse(PersistentTasksCustomMetaData.PersistentTask<?> persistentTask) {
                                    logger.trace("notification for task [{}] with id [{}] was successful", getAction(),
                                            getPersistentTaskId());
                                }

                                @Override
                                public void onFailure(Exception e) {
                                    logger.warn(() -> new ParameterizedMessage(
                                        "notification for task [{}] with id [{}] failed", getAction(), getPersistentTaskId()), e);
                                }
                            });
                }
            } finally {
                taskManager.unregister(this);
            }
        }
    }

    public enum State {
        STARTED,  PENDING_CANCEL, COMPLETED     
}
