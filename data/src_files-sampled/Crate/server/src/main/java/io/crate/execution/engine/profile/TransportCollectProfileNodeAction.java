package io.crate.execution.engine.profile;

import io.crate.action.FutureActionListener;
import io.crate.execution.jobs.RootTask;
import io.crate.execution.jobs.TasksService;
import io.crate.execution.support.NodeAction;
import io.crate.execution.support.NodeActionRequestHandler;
import io.crate.execution.support.Transports;
import org.elasticsearch.action.ActionListenerResponseHandler;
import org.elasticsearch.common.inject.Inject;
import org.elasticsearch.common.inject.Singleton;
import org.elasticsearch.threadpool.ThreadPool;
import org.elasticsearch.transport.TransportService;

import java.util.Collections;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Singleton
public class TransportCollectProfileNodeAction implements NodeAction<NodeCollectProfileRequest, NodeCollectProfileResponse> {

    private static final String TRANSPORT_ACTION = "internal:crate:sql/node/profile/collect";
    private static final String EXECUTOR = ThreadPool.Names.SEARCH;

    private final Transports transports;
    private final TasksService tasksService;

    @Inject
    public TransportCollectProfileNodeAction(TransportService transportService,
                                             Transports transports,
                                             TasksService tasksService) {
        this.transports = transports;
        this.tasksService = tasksService;

        transportService.registerRequestHandler(
            TRANSPORT_ACTION,
            EXECUTOR,
            true,
            false,
            NodeCollectProfileRequest::new,
            new NodeActionRequestHandler<>(this)
        );
    }

    @Override
    public CompletableFuture<NodeCollectProfileResponse> nodeOperation(NodeCollectProfileRequest request) {
        return collectExecutionTimesAndFinishContext(request.jobId()).thenApply(NodeCollectProfileResponse::new);
    }

    public CompletableFuture<Map<String, Object>> collectExecutionTimesAndFinishContext(UUID jobId) {
        RootTask rootTask = tasksService.getTaskOrNull(jobId);
        if (rootTask == null) {
            return CompletableFuture.completedFuture(Collections.emptyMap());
        } else {
            return rootTask.finishProfiling();
        }
    }

    public void execute(String nodeId,
                        NodeCollectProfileRequest request,
                        FutureActionListener<NodeCollectProfileResponse, Map<String, Object>> listener) {
        transports.sendRequest(TRANSPORT_ACTION, nodeId, request, listener,
            new ActionListenerResponseHandler<>(listener, NodeCollectProfileResponse::new));
    }
}
