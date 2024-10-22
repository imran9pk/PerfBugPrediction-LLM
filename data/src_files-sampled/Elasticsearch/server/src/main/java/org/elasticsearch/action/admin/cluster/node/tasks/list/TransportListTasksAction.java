package org.elasticsearch.action.admin.cluster.node.tasks.list;

import org.elasticsearch.action.ActionListener;
import org.elasticsearch.action.FailedNodeException;
import org.elasticsearch.action.TaskOperationFailure;
import org.elasticsearch.action.support.ActionFilters;
import org.elasticsearch.action.support.tasks.TransportTasksAction;
import org.elasticsearch.cluster.metadata.IndexNameExpressionResolver;
import org.elasticsearch.cluster.service.ClusterService;
import org.elasticsearch.common.inject.Inject;
import org.elasticsearch.common.io.stream.StreamInput;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.unit.TimeValue;
import org.elasticsearch.tasks.Task;
import org.elasticsearch.tasks.TaskInfo;
import org.elasticsearch.threadpool.ThreadPool;
import org.elasticsearch.transport.TransportService;

import java.io.IOException;
import java.util.List;
import java.util.function.Consumer;

import static org.elasticsearch.common.unit.TimeValue.timeValueSeconds;

public class TransportListTasksAction extends TransportTasksAction<Task, ListTasksRequest, ListTasksResponse, TaskInfo> {
    public static long waitForCompletionTimeout(TimeValue timeout) {
        if (timeout == null) {
            timeout = DEFAULT_WAIT_FOR_COMPLETION_TIMEOUT;
        }
        return System.nanoTime() + timeout.nanos();
    }

    private static final TimeValue DEFAULT_WAIT_FOR_COMPLETION_TIMEOUT = timeValueSeconds(30);

    @Inject
    public TransportListTasksAction(Settings settings, ThreadPool threadPool, ClusterService clusterService,
            TransportService transportService, ActionFilters actionFilters, IndexNameExpressionResolver indexNameExpressionResolver) {
        super(settings, ListTasksAction.NAME, threadPool, clusterService, transportService, actionFilters, indexNameExpressionResolver,
                ListTasksRequest::new, ListTasksResponse::new, ThreadPool.Names.MANAGEMENT);
    }

    @Override
    protected ListTasksResponse newResponse(ListTasksRequest request, List<TaskInfo> tasks,
            List<TaskOperationFailure> taskOperationFailures, List<FailedNodeException> failedNodeExceptions) {
        return new ListTasksResponse(tasks, taskOperationFailures, failedNodeExceptions);
    }

    @Override
    protected TaskInfo readTaskResponse(StreamInput in) throws IOException {
        return new TaskInfo(in);
    }

    @Override
    protected void taskOperation(ListTasksRequest request, Task task, ActionListener<TaskInfo> listener) {
        listener.onResponse(task.taskInfo(clusterService.localNode().getId(), request.getDetailed()));
    }

    @Override
    protected void processTasks(ListTasksRequest request, Consumer<Task> operation) {
        if (request.getWaitForCompletion()) {
            long timeoutNanos = waitForCompletionTimeout(request.getTimeout());
            operation = operation.andThen(task -> {
                if (task.getAction().startsWith(ListTasksAction.NAME)) {
                    return;
                }
                taskManager.waitForTaskCompletion(task, timeoutNanos);
            });
        }
        super.processTasks(request, operation);
    }

}
