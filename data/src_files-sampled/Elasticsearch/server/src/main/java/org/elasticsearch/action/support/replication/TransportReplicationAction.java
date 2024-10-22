package org.elasticsearch.action.support.replication;

import org.apache.logging.log4j.message.ParameterizedMessage;
import org.apache.lucene.store.AlreadyClosedException;
import org.elasticsearch.ElasticsearchException;
import org.elasticsearch.ExceptionsHelper;
import org.elasticsearch.Version;
import org.elasticsearch.action.ActionListener;
import org.elasticsearch.action.ActionListenerResponseHandler;
import org.elasticsearch.action.ActionResponse;
import org.elasticsearch.action.UnavailableShardsException;
import org.elasticsearch.action.support.ActionFilters;
import org.elasticsearch.action.support.ActiveShardCount;
import org.elasticsearch.action.support.TransportAction;
import org.elasticsearch.action.support.TransportActions;
import org.elasticsearch.client.transport.NoNodeAvailableException;
import org.elasticsearch.cluster.ClusterState;
import org.elasticsearch.cluster.ClusterStateObserver;
import org.elasticsearch.cluster.action.shard.ShardStateAction;
import org.elasticsearch.cluster.block.ClusterBlockException;
import org.elasticsearch.cluster.block.ClusterBlockLevel;
import org.elasticsearch.cluster.metadata.IndexMetaData;
import org.elasticsearch.cluster.metadata.IndexNameExpressionResolver;
import org.elasticsearch.cluster.node.DiscoveryNode;
import org.elasticsearch.cluster.routing.AllocationId;
import org.elasticsearch.cluster.routing.IndexShardRoutingTable;
import org.elasticsearch.cluster.routing.ShardRouting;
import org.elasticsearch.cluster.service.ClusterService;
import org.elasticsearch.common.Nullable;
import org.elasticsearch.common.io.stream.StreamInput;
import org.elasticsearch.common.io.stream.StreamOutput;
import org.elasticsearch.common.io.stream.Writeable;
import org.elasticsearch.common.lease.Releasable;
import org.elasticsearch.common.lease.Releasables;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.unit.TimeValue;
import org.elasticsearch.common.util.concurrent.AbstractRunnable;
import org.elasticsearch.index.IndexNotFoundException;
import org.elasticsearch.index.IndexService;
import org.elasticsearch.index.seqno.SequenceNumbers;
import org.elasticsearch.index.shard.IndexShard;
import org.elasticsearch.index.shard.IndexShardClosedException;
import org.elasticsearch.index.shard.ReplicationGroup;
import org.elasticsearch.index.shard.ShardId;
import org.elasticsearch.index.shard.ShardNotFoundException;
import org.elasticsearch.index.shard.ShardNotInPrimaryModeException;
import org.elasticsearch.indices.IndexClosedException;
import org.elasticsearch.indices.IndicesService;
import org.elasticsearch.node.NodeClosedException;
import org.elasticsearch.tasks.Task;
import org.elasticsearch.tasks.TaskId;
import org.elasticsearch.threadpool.ThreadPool;
import org.elasticsearch.transport.ConnectTransportException;
import org.elasticsearch.transport.TransportChannel;
import org.elasticsearch.transport.TransportChannelResponseHandler;
import org.elasticsearch.transport.TransportException;
import org.elasticsearch.transport.TransportRequest;
import org.elasticsearch.transport.TransportRequestHandler;
import org.elasticsearch.transport.TransportRequestOptions;
import org.elasticsearch.transport.TransportResponse;
import org.elasticsearch.transport.TransportResponse.Empty;
import org.elasticsearch.transport.TransportResponseHandler;
import org.elasticsearch.transport.TransportService;

import java.io.IOException;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

import static org.elasticsearch.index.seqno.SequenceNumbers.UNASSIGNED_PRIMARY_TERM;

public abstract class TransportReplicationAction<
            Request extends ReplicationRequest<Request>,
            ReplicaRequest extends ReplicationRequest<ReplicaRequest>,
            Response extends ReplicationResponse
        > extends TransportAction<Request, Response> {

    protected final TransportService transportService;
    protected final ClusterService clusterService;
    protected final ShardStateAction shardStateAction;
    protected final IndicesService indicesService;
    protected final TransportRequestOptions transportOptions;
    protected final String executor;

    protected final String transportReplicaAction;
    protected final String transportPrimaryAction;

    private final boolean syncGlobalCheckpointAfterOperation;

    protected TransportReplicationAction(Settings settings, String actionName, TransportService transportService,
                                         ClusterService clusterService, IndicesService indicesService,
                                         ThreadPool threadPool, ShardStateAction shardStateAction,
                                         ActionFilters actionFilters,
                                         IndexNameExpressionResolver indexNameExpressionResolver, Supplier<Request> request,
                                         Supplier<ReplicaRequest> replicaRequest, String executor) {
        this(settings, actionName, transportService, clusterService, indicesService, threadPool, shardStateAction, actionFilters,
                indexNameExpressionResolver, request, replicaRequest, executor, false);
    }


    protected TransportReplicationAction(Settings settings, String actionName, TransportService transportService,
                                         ClusterService clusterService, IndicesService indicesService,
                                         ThreadPool threadPool, ShardStateAction shardStateAction,
                                         ActionFilters actionFilters,
                                         IndexNameExpressionResolver indexNameExpressionResolver, Supplier<Request> request,
                                         Supplier<ReplicaRequest> replicaRequest, String executor,
                                         boolean syncGlobalCheckpointAfterOperation) {
        super(settings, actionName, threadPool, actionFilters, indexNameExpressionResolver, transportService.getTaskManager());
        this.transportService = transportService;
        this.clusterService = clusterService;
        this.indicesService = indicesService;
        this.shardStateAction = shardStateAction;
        this.executor = executor;

        this.transportPrimaryAction = actionName + "[p]";
        this.transportReplicaAction = actionName + "[r]";
        registerRequestHandlers(actionName, transportService, request, replicaRequest, executor);

        this.transportOptions = transportOptions(settings);

        this.syncGlobalCheckpointAfterOperation = syncGlobalCheckpointAfterOperation;
    }

    protected void registerRequestHandlers(String actionName, TransportService transportService, Supplier<Request> request,
                                           Supplier<ReplicaRequest> replicaRequest, String executor) {
        transportService.registerRequestHandler(actionName, request, ThreadPool.Names.SAME, new OperationTransportHandler());
        transportService.registerRequestHandler(transportPrimaryAction, () -> new ConcreteShardRequest<>(request), executor,
            new PrimaryOperationTransportHandler());
        transportService.registerRequestHandler(transportReplicaAction,
            () -> new ConcreteReplicaRequest<>(replicaRequest),
            executor, true, true,
            new ReplicaOperationTransportHandler());
    }

    @Override
    protected final void doExecute(Request request, ActionListener<Response> listener) {
        throw new UnsupportedOperationException("the task parameter is required for this operation");
    }

    @Override
    protected void doExecute(Task task, Request request, ActionListener<Response> listener) {
        new ReroutePhase((ReplicationTask) task, request, listener).run();
    }

    protected ReplicationOperation.Replicas<ReplicaRequest> newReplicasProxy(long primaryTerm) {
        return new ReplicasProxy(primaryTerm);
    }

    protected abstract Response newResponseInstance();

    protected void resolveRequest(final IndexMetaData indexMetaData, final Request request) {
        if (request.waitForActiveShards() == ActiveShardCount.DEFAULT) {
            request.waitForActiveShards(indexMetaData.getWaitForActiveShards());
        }
    }

    protected abstract PrimaryResult<ReplicaRequest, Response> shardOperationOnPrimary(
            Request shardRequest, IndexShard primary) throws Exception;

    protected abstract ReplicaResult shardOperationOnReplica(ReplicaRequest shardRequest, IndexShard replica) throws Exception;

    @Nullable
    protected ClusterBlockLevel globalBlockLevel() {
        return null;
    }

    @Nullable
    public ClusterBlockLevel indexBlockLevel() {
        return null;
    }

    protected boolean resolveIndex() {
        return true;
    }

    protected TransportRequestOptions transportOptions(Settings settings) {
        return TransportRequestOptions.EMPTY;
    }

    private String concreteIndex(final ClusterState state, final ReplicationRequest request) {
        return resolveIndex() ? indexNameExpressionResolver.concreteSingleIndex(state, request).getName() : request.index();
    }

    private ClusterBlockException blockExceptions(final ClusterState state, final String indexName) {
        ClusterBlockLevel globalBlockLevel = globalBlockLevel();
        if (globalBlockLevel != null) {
            ClusterBlockException blockException = state.blocks().globalBlockedException(globalBlockLevel);
            if (blockException != null) {
                return blockException;
            }
        }
        ClusterBlockLevel indexBlockLevel = indexBlockLevel();
        if (indexBlockLevel != null) {
            ClusterBlockException blockException = state.blocks().indexBlockedException(indexBlockLevel, indexName);
            if (blockException != null) {
                return blockException;
            }
        }
        return null;
    }

    protected boolean retryPrimaryException(final Throwable e) {
        return e.getClass() == ReplicationOperation.RetryOnPrimaryException.class
                || TransportActions.isShardNotAvailableException(e)
                || isRetryableClusterBlockException(e);
    }

    boolean isRetryableClusterBlockException(final Throwable e) {
        if (e instanceof ClusterBlockException) {
            return ((ClusterBlockException) e).retryable();
        }
        return false;
    }

    protected class OperationTransportHandler implements TransportRequestHandler<Request> {

        public OperationTransportHandler() {

        }

        @Override
        public void messageReceived(final Request request, final TransportChannel channel, Task task) throws Exception {
            execute(task, request, new ActionListener<Response>() {
                @Override
                public void onResponse(Response result) {
                    try {
                        channel.sendResponse(result);
                    } catch (Exception e) {
                        onFailure(e);
                    }
                }

                @Override
                public void onFailure(Exception e) {
                    try {
                        channel.sendResponse(e);
                    } catch (Exception inner) {
                        inner.addSuppressed(e);
                        logger.warn(() -> new ParameterizedMessage("Failed to send response for {}", actionName), inner);
                    }
                }
            });
        }

        @Override
        public void messageReceived(Request request, TransportChannel channel) throws Exception {
            throw new UnsupportedOperationException("the task parameter is required for this operation");
        }
    }

    protected class PrimaryOperationTransportHandler implements TransportRequestHandler<ConcreteShardRequest<Request>> {

        public PrimaryOperationTransportHandler() {

        }

        @Override
        public void messageReceived(final ConcreteShardRequest<Request> request, final TransportChannel channel) throws Exception {
            throw new UnsupportedOperationException("the task parameter is required for this operation");
        }

        @Override
        public void messageReceived(ConcreteShardRequest<Request> request, TransportChannel channel, Task task) {
            new AsyncPrimaryAction(request.request, request.targetAllocationID, request.primaryTerm, channel, (ReplicationTask) task).run();
        }
    }

    class AsyncPrimaryAction extends AbstractRunnable {

        private final Request request;
        private final String targetAllocationID;
        private final long primaryTerm;
        private final TransportChannel channel;
        private final ReplicationTask replicationTask;

        AsyncPrimaryAction(Request request, String targetAllocationID, long primaryTerm, TransportChannel channel,
                           ReplicationTask replicationTask) {
            this.request = request;
            this.targetAllocationID = targetAllocationID;
            this.primaryTerm = primaryTerm;
            this.channel = channel;
            this.replicationTask = replicationTask;
        }

        @Override
        protected void doRun() throws Exception {
            final ShardId shardId = request.shardId();
            final IndexShard indexShard = getIndexShard(shardId);
            final ShardRouting shardRouting = indexShard.routingEntry();
            if (shardRouting.primary() == false) {
                throw new ReplicationOperation.RetryOnPrimaryException(shardId, "actual shard is not a primary " + shardRouting);
            }
            final String actualAllocationId = shardRouting.allocationId().getId();
            if (actualAllocationId.equals(targetAllocationID) == false) {
                throw new ShardNotFoundException(shardId, "expected allocation id [{}] but found [{}]", targetAllocationID,
                    actualAllocationId);
            }
            final long actualTerm = indexShard.getPendingPrimaryTerm();
            if (actualTerm != primaryTerm) {
                throw new ShardNotFoundException(shardId, "expected allocation id [{}] with term [{}] but found [{}]", targetAllocationID,
                    primaryTerm, actualTerm);
            }

            acquirePrimaryOperationPermit(
                    indexShard,
                    request,
                    ActionListener.wrap(
                            releasable -> runWithPrimaryShardReference(new PrimaryShardReference(indexShard, releasable)),
                            e -> {
                                if (e instanceof ShardNotInPrimaryModeException) {
                                    onFailure(new ReplicationOperation.RetryOnPrimaryException(shardId, "shard is not in primary mode", e));
                                } else {
                                    onFailure(e);
                                }
                            }));
        }

        void runWithPrimaryShardReference(final PrimaryShardReference primaryShardReference) {
            try {
                final ClusterState clusterState = clusterService.state();
                final IndexMetaData indexMetaData = clusterState.metaData().getIndexSafe(primaryShardReference.routingEntry().index());

                final ClusterBlockException blockException = blockExceptions(clusterState, indexMetaData.getIndex().getName());
                if (blockException != null) {
                    logger.trace("cluster is blocked, action failed on primary", blockException);
                    throw blockException;
                }

                if (primaryShardReference.isRelocated()) {
                    primaryShardReference.close(); setPhase(replicationTask, "primary_delegation");
                    final ShardRouting primary = primaryShardReference.routingEntry();
                    assert primary.relocating() : "indexShard is marked as relocated but routing isn't" + primary;
                    final Writeable.Reader<Response> reader = in -> {
                        Response response = TransportReplicationAction.this.newResponseInstance();
                        response.readFrom(in);
                        return response;
                    };
                    DiscoveryNode relocatingNode = clusterState.nodes().get(primary.relocatingNodeId());
                    transportService.sendRequest(relocatingNode, transportPrimaryAction,
                        new ConcreteShardRequest<>(request, primary.allocationId().getRelocationId(), primaryTerm),
                        transportOptions,
                        new TransportChannelResponseHandler<Response>(logger, channel, "rerouting indexing to target primary " + primary,
                            reader) {

                            @Override
                            public void handleResponse(Response response) {
                                setPhase(replicationTask, "finished");
                                super.handleResponse(response);
                            }

                            @Override
                            public void handleException(TransportException exp) {
                                setPhase(replicationTask, "finished");
                                super.handleException(exp);
                            }
                        });
                } else {
                    setPhase(replicationTask, "primary");
                    final ActionListener<Response> listener = createResponseListener(primaryShardReference);
                    createReplicatedOperation(request,
                            ActionListener.wrap(result -> result.respond(listener), listener::onFailure),
                            primaryShardReference)
                            .execute();
                }
            } catch (Exception e) {
                Releasables.closeWhileHandlingException(primaryShardReference); onFailure(e);
            }
        }

        @Override
        public void onFailure(Exception e) {
            setPhase(replicationTask, "finished");
            try {
                channel.sendResponse(e);
            } catch (IOException inner) {
                inner.addSuppressed(e);
                logger.warn("failed to send response", inner);
            }
        }

        private ActionListener<Response> createResponseListener(final PrimaryShardReference primaryShardReference) {
            return new ActionListener<Response>() {
                @Override
                public void onResponse(Response response) {
                    if (syncGlobalCheckpointAfterOperation) {
                        final IndexShard shard = primaryShardReference.indexShard;
                        try {
                            shard.maybeSyncGlobalCheckpoint("post-operation");
                        } catch (final Exception e) {
                            if (ExceptionsHelper.unwrap(e, AlreadyClosedException.class, IndexShardClosedException.class) == null) {
                                logger.info(
                                        new ParameterizedMessage(
                                                "{} failed to execute post-operation global checkpoint sync",
                                                shard.shardId()),
                                        e);
                                }
                        }
                    }
                    primaryShardReference.close(); setPhase(replicationTask, "finished");
                    try {
                        channel.sendResponse(response);
                    } catch (IOException e) {
                        onFailure(e);
                    }
                }

                @Override
                public void onFailure(Exception e) {
                    primaryShardReference.close(); setPhase(replicationTask, "finished");
                    try {
                        channel.sendResponse(e);
                    } catch (IOException e1) {
                        logger.warn("failed to send response", e);
                    }
                }
            };
        }

        protected ReplicationOperation<Request, ReplicaRequest, PrimaryResult<ReplicaRequest, Response>> createReplicatedOperation(
            Request request, ActionListener<PrimaryResult<ReplicaRequest, Response>> listener,
            PrimaryShardReference primaryShardReference) {
            return new ReplicationOperation<>(request, primaryShardReference, listener,
                    newReplicasProxy(primaryTerm), logger, actionName);
        }
    }

    protected static class PrimaryResult<ReplicaRequest extends ReplicationRequest<ReplicaRequest>,
            Response extends ReplicationResponse>
            implements ReplicationOperation.PrimaryResult<ReplicaRequest> {
        final ReplicaRequest replicaRequest;
        public final Response finalResponseIfSuccessful;
        public final Exception finalFailure;

        public PrimaryResult(ReplicaRequest replicaRequest, Response finalResponseIfSuccessful, Exception finalFailure) {
            assert finalFailure != null ^ finalResponseIfSuccessful != null
                    : "either a response or a failure has to be not null, " +
                    "found [" + finalFailure + "] failure and ["+ finalResponseIfSuccessful + "] response";
            this.replicaRequest = replicaRequest;
            this.finalResponseIfSuccessful = finalResponseIfSuccessful;
            this.finalFailure = finalFailure;
        }

        public PrimaryResult(ReplicaRequest replicaRequest, Response replicationResponse) {
            this(replicaRequest, replicationResponse, null);
        }

        @Override
        public ReplicaRequest replicaRequest() {
            return replicaRequest;
        }

        @Override
        public void setShardInfo(ReplicationResponse.ShardInfo shardInfo) {
            if (finalResponseIfSuccessful != null) {
                finalResponseIfSuccessful.setShardInfo(shardInfo);
            }
        }

        public void respond(ActionListener<Response> listener) {
            if (finalResponseIfSuccessful != null) {
                listener.onResponse(finalResponseIfSuccessful);
            } else {
                listener.onFailure(finalFailure);
            }
        }
    }

    public static class ReplicaResult {
        final Exception finalFailure;

        public ReplicaResult(Exception finalFailure) {
            this.finalFailure = finalFailure;
        }

        public ReplicaResult() {
            this(null);
        }

        public void respond(ActionListener<TransportResponse.Empty> listener) {
            if (finalFailure == null) {
                listener.onResponse(TransportResponse.Empty.INSTANCE);
            } else {
                listener.onFailure(finalFailure);
            }
        }
    }

    public class ReplicaOperationTransportHandler implements TransportRequestHandler<ConcreteReplicaRequest<ReplicaRequest>> {

        @Override
        public void messageReceived(
                final ConcreteReplicaRequest<ReplicaRequest> replicaRequest, final TransportChannel channel) throws Exception {
            throw new UnsupportedOperationException("the task parameter is required for this operation");
        }

        @Override
        public void messageReceived(
                final ConcreteReplicaRequest<ReplicaRequest> replicaRequest,
                final TransportChannel channel,
                final Task task)
            throws Exception {
            new AsyncReplicaAction(
                    replicaRequest.getRequest(),
                    replicaRequest.getTargetAllocationID(),
                    replicaRequest.getPrimaryTerm(),
                    replicaRequest.getGlobalCheckpoint(),
                    replicaRequest.getMaxSeqNoOfUpdatesOrDeletes(),
                    channel,
                    (ReplicationTask) task).run();
        }

    }

    public static class RetryOnReplicaException extends ElasticsearchException {

        public RetryOnReplicaException(ShardId shardId, String msg) {
            super(msg);
            setShard(shardId);
        }

        public RetryOnReplicaException(StreamInput in) throws IOException {
            super(in);
        }
    }

    private final class AsyncReplicaAction extends AbstractRunnable implements ActionListener<Releasable> {
        private final ReplicaRequest request;
        private final String targetAllocationID;
        private final long primaryTerm;
        private final long globalCheckpoint;
        private final long maxSeqNoOfUpdatesOrDeletes;
        private final TransportChannel channel;
        private final IndexShard replica;
        private final ReplicationTask task;
        private final ClusterStateObserver observer = new ClusterStateObserver(clusterService, null, logger, threadPool.getThreadContext());

        AsyncReplicaAction(
                ReplicaRequest request,
                String targetAllocationID,
                long primaryTerm,
                long globalCheckpoint,
                long maxSeqNoOfUpdatesOrDeletes,
                TransportChannel channel,
                ReplicationTask task) {
            this.request = request;
            this.channel = channel;
            this.task = task;
            this.targetAllocationID = targetAllocationID;
            this.primaryTerm = primaryTerm;
            this.globalCheckpoint = globalCheckpoint;
            this.maxSeqNoOfUpdatesOrDeletes = maxSeqNoOfUpdatesOrDeletes;
            final ShardId shardId = request.shardId();
            assert shardId != null : "request shardId must be set";
            this.replica = getIndexShard(shardId);
        }

        @Override
        public void onResponse(Releasable releasable) {
            try {
                final ReplicaResult replicaResult = shardOperationOnReplica(request, replica);
                releasable.close(); final TransportReplicationAction.ReplicaResponse response =
                        new ReplicaResponse(replica.getLocalCheckpoint(), replica.getGlobalCheckpoint());
                replicaResult.respond(new ResponseListener(response));
            } catch (final Exception e) {
                Releasables.closeWhileHandlingException(releasable); AsyncReplicaAction.this.onFailure(e);
            }
        }

        @Override
        public void onFailure(Exception e) {
            if (e instanceof RetryOnReplicaException) {
                logger.trace(
                        () -> new ParameterizedMessage(
                            "Retrying operation on replica, action [{}], request [{}]",
                            transportReplicaAction,
                            request),
                    e);
                request.onRetry();
                observer.waitForNextChange(new ClusterStateObserver.Listener() {
                    @Override
                    public void onNewClusterState(ClusterState state) {
                        String extraMessage = "action [" + transportReplicaAction + "], request[" + request + "]";
                        TransportChannelResponseHandler<TransportResponse.Empty> handler =
                            new TransportChannelResponseHandler<>(logger, channel, extraMessage,
                                (in) -> TransportResponse.Empty.INSTANCE);
                        transportService.sendRequest(clusterService.localNode(), transportReplicaAction,
                            new ConcreteReplicaRequest<>(request, targetAllocationID, primaryTerm,
                                globalCheckpoint, maxSeqNoOfUpdatesOrDeletes),
                            handler);
                    }

                    @Override
                    public void onClusterServiceClose() {
                        responseWithFailure(new NodeClosedException(clusterService.localNode()));
                    }

                    @Override
                    public void onTimeout(TimeValue timeout) {
                        throw new AssertionError("Cannot happen: there is not timeout");
                    }
                });
            } else {
                responseWithFailure(e);
            }
        }

        protected void responseWithFailure(Exception e) {
            try {
                setPhase(task, "finished");
                channel.sendResponse(e);
            } catch (IOException responseException) {
                responseException.addSuppressed(e);
                logger.warn(() -> new ParameterizedMessage(
                            "failed to send error message back to client for action [{}]", transportReplicaAction), responseException);
            }
        }

        @Override
        protected void doRun() throws Exception {
            setPhase(task, "replica");
            final String actualAllocationId = this.replica.routingEntry().allocationId().getId();
            if (actualAllocationId.equals(targetAllocationID) == false) {
                throw new ShardNotFoundException(this.replica.shardId(), "expected allocation id [{}] but found [{}]", targetAllocationID,
                    actualAllocationId);
            }
            acquireReplicaOperationPermit(replica, request, this, primaryTerm, globalCheckpoint, maxSeqNoOfUpdatesOrDeletes);
        }

        private class ResponseListener implements ActionListener<TransportResponse.Empty> {
            private final ReplicaResponse replicaResponse;

            ResponseListener(ReplicaResponse replicaResponse) {
                this.replicaResponse = replicaResponse;
            }

            @Override
            public void onResponse(Empty response) {
                if (logger.isTraceEnabled()) {
                    logger.trace("action [{}] completed on shard [{}] for request [{}]", transportReplicaAction, request.shardId(),
                            request);
                }
                setPhase(task, "finished");
                try {
                    channel.sendResponse(replicaResponse);
                } catch (Exception e) {
                    onFailure(e);
                }
            }

            @Override
            public void onFailure(Exception e) {
                responseWithFailure(e);
            }
        }
    }

    protected IndexShard getIndexShard(final ShardId shardId) {
        IndexService indexService = indicesService.indexServiceSafe(shardId.getIndex());
        return indexService.getShard(shardId.id());
    }

    final class ReroutePhase extends AbstractRunnable {
        private final ActionListener<Response> listener;
        private final Request request;
        private final ReplicationTask task;
        private final ClusterStateObserver observer;
        private final AtomicBoolean finished = new AtomicBoolean();

        ReroutePhase(ReplicationTask task, Request request, ActionListener<Response> listener) {
            this.request = request;
            if (task != null) {
                this.request.setParentTask(clusterService.localNode().getId(), task.getId());
            }
            this.listener = listener;
            this.task = task;
            this.observer = new ClusterStateObserver(clusterService, request.timeout(), logger, threadPool.getThreadContext());
        }

        @Override
        public void onFailure(Exception e) {
            finishWithUnexpectedFailure(e);
        }

        @Override
        protected void doRun() {
            setPhase(task, "routing");
            final ClusterState state = observer.setAndGetObservedState();
            final String concreteIndex = concreteIndex(state, request);
            final ClusterBlockException blockException = blockExceptions(state, concreteIndex);
            if (blockException != null) {
                if (blockException.retryable()) {
                    logger.trace("cluster is blocked, scheduling a retry", blockException);
                    retry(blockException);
                } else {
                    finishAsFailed(blockException);
                }
            } else {
                final IndexMetaData indexMetaData = state.metaData().index(concreteIndex);
                if (indexMetaData == null) {
                    retry(new IndexNotFoundException(concreteIndex));
                    return;
                }
                if (indexMetaData.getState() == IndexMetaData.State.CLOSE) {
                    throw new IndexClosedException(indexMetaData.getIndex());
                }

                resolveRequest(indexMetaData, request);
                assert request.shardId() != null : "request shardId must be set in resolveRequest";
                assert request.waitForActiveShards() != ActiveShardCount.DEFAULT :
                    "request waitForActiveShards must be set in resolveRequest";

                final ShardRouting primary = primary(state);
                if (retryIfUnavailable(state, primary)) {
                    return;
                }
                final DiscoveryNode node = state.nodes().get(primary.currentNodeId());
                if (primary.currentNodeId().equals(state.nodes().getLocalNodeId())) {
                    performLocalAction(state, primary, node, indexMetaData);
                } else {
                    performRemoteAction(state, primary, node);
                }
            }
        }

        private void performLocalAction(ClusterState state, ShardRouting primary, DiscoveryNode node, IndexMetaData indexMetaData) {
            setPhase(task, "waiting_on_primary");
            if (logger.isTraceEnabled()) {
                logger.trace("send action [{}] to local primary [{}] for request [{}] with cluster state version [{}] to [{}] ",
                    transportPrimaryAction, request.shardId(), request, state.version(), primary.currentNodeId());
            }
            performAction(node, transportPrimaryAction, true,
                new ConcreteShardRequest<>(request, primary.allocationId().getId(), indexMetaData.primaryTerm(primary.id())));
        }

        private void performRemoteAction(ClusterState state, ShardRouting primary, DiscoveryNode node) {
            if (state.version() < request.routedBasedOnClusterVersion()) {
                logger.trace("failed to find primary [{}] for request [{}] despite sender thinking it would be here. Local cluster state "
                        + "version [{}]] is older than on sending node (version [{}]), scheduling a retry...", request.shardId(), request,
                    state.version(), request.routedBasedOnClusterVersion());
                retryBecauseUnavailable(request.shardId(), "failed to find primary as current cluster state with version ["
                    + state.version() + "] is stale (expected at least [" + request.routedBasedOnClusterVersion() + "]");
                return;
            } else {
                request.routedBasedOnClusterVersion(state.version());
            }
            if (logger.isTraceEnabled()) {
                logger.trace("send action [{}] on primary [{}] for request [{}] with cluster state version [{}] to [{}]", actionName,
                    request.shardId(), request, state.version(), primary.currentNodeId());
            }
            setPhase(task, "rerouted");
            performAction(node, actionName, false, request);
        }

        private boolean retryIfUnavailable(ClusterState state, ShardRouting primary) {
            if (primary == null || primary.active() == false) {
                logger.trace("primary shard [{}] is not yet active, scheduling a retry: action [{}], request [{}], "
                    + "cluster state version [{}]", request.shardId(), actionName, request, state.version());
                retryBecauseUnavailable(request.shardId(), "primary shard is not active");
                return true;
            }
            if (state.nodes().nodeExists(primary.currentNodeId()) == false) {
                logger.trace("primary shard [{}] is assigned to an unknown node [{}], scheduling a retry: action [{}], request [{}], "
                    + "cluster state version [{}]", request.shardId(), primary.currentNodeId(), actionName, request, state.version());
                retryBecauseUnavailable(request.shardId(), "primary shard isn't assigned to a known node.");
                return true;
            }
            return false;
        }

        private ShardRouting primary(ClusterState state) {
            IndexShardRoutingTable indexShard = state.getRoutingTable().shardRoutingTable(request.shardId());
            return indexShard.primaryShard();
        }

        private void performAction(final DiscoveryNode node, final String action, final boolean isPrimaryAction,
                                   final TransportRequest requestToPerform) {
            transportService.sendRequest(node, action, requestToPerform, transportOptions, new TransportResponseHandler<Response>() {

                @Override
                public Response read(StreamInput in) throws IOException {
                    Response response = newResponseInstance();
                    response.readFrom(in);
                    return response;
                }

                @Override
                public String executor() {
                    return ThreadPool.Names.SAME;
                }

                @Override
                public void handleResponse(Response response) {
                    finishOnSuccess(response);
                }

                @Override
                public void handleException(TransportException exp) {
                    try {
                        final Throwable cause = exp.unwrapCause();
                        if (cause instanceof ConnectTransportException || cause instanceof NodeClosedException ||
                            (isPrimaryAction && retryPrimaryException(cause))) {
                            logger.trace(() -> new ParameterizedMessage(
                                    "received an error from node [{}] for request [{}], scheduling a retry",
                                    node.getId(), requestToPerform), exp);
                            retry(exp);
                        } else {
                            finishAsFailed(exp);
                        }
                    } catch (Exception e) {
                        e.addSuppressed(exp);
                        finishWithUnexpectedFailure(e);
                    }
                }
            });
        }

        void retry(Exception failure) {
            assert failure != null;
            if (observer.isTimedOut()) {
                finishAsFailed(failure);
                return;
            }
            setPhase(task, "waiting_for_retry");
            request.onRetry();
            observer.waitForNextChange(new ClusterStateObserver.Listener() {
                @Override
                public void onNewClusterState(ClusterState state) {
                    run();
                }

                @Override
                public void onClusterServiceClose() {
                    finishAsFailed(new NodeClosedException(clusterService.localNode()));
                }

                @Override
                public void onTimeout(TimeValue timeout) {
                    run();
                }
            });
        }

        void finishAsFailed(Exception failure) {
            if (finished.compareAndSet(false, true)) {
                setPhase(task, "failed");
                logger.trace(() -> new ParameterizedMessage("operation failed. action [{}], request [{}]", actionName, request), failure);
                listener.onFailure(failure);
            } else {
                assert false : "finishAsFailed called but operation is already finished";
            }
        }

        void finishWithUnexpectedFailure(Exception failure) {
            logger.warn(() -> new ParameterizedMessage(
                        "unexpected error during the primary phase for action [{}], request [{}]",
                        actionName, request), failure);
            if (finished.compareAndSet(false, true)) {
                setPhase(task, "failed");
                listener.onFailure(failure);
            } else {
                assert false : "finishWithUnexpectedFailure called but operation is already finished";
            }
        }

        void finishOnSuccess(Response response) {
            if (finished.compareAndSet(false, true)) {
                setPhase(task, "finished");
                if (logger.isTraceEnabled()) {
                    logger.trace("operation succeeded. action [{}],request [{}]", actionName, request);
                }
                listener.onResponse(response);
            } else {
                assert false : "finishOnSuccess called but operation is already finished";
            }
        }

        void retryBecauseUnavailable(ShardId shardId, String message) {
            retry(new UnavailableShardsException(shardId, "{} Timeout: [{}], request: [{}]", message, request.timeout(), request));
        }
    }

    protected void acquirePrimaryOperationPermit(final IndexShard primary,
                                                 final Request request,
                                                 final ActionListener<Releasable> onAcquired) {
        primary.acquirePrimaryOperationPermit(onAcquired, executor, request);
    }

    protected void acquireReplicaOperationPermit(final IndexShard replica,
                                                 final ReplicaRequest request,
                                                 final ActionListener<Releasable> onAcquired,
                                                 final long primaryTerm,
                                                 final long globalCheckpoint,
                                                 final long maxSeqNoOfUpdatesOrDeletes) {
        replica.acquireReplicaOperationPermit(primaryTerm, globalCheckpoint, maxSeqNoOfUpdatesOrDeletes, onAcquired, executor, request);
    }

    class ShardReference implements Releasable {

        protected final IndexShard indexShard;
        private final Releasable operationLock;

        ShardReference(IndexShard indexShard, Releasable operationLock) {
            this.indexShard = indexShard;
            this.operationLock = operationLock;
        }

        @Override
        public void close() {
            operationLock.close();
        }

        public long getLocalCheckpoint() {
            return indexShard.getLocalCheckpoint();
        }

        public ShardRouting routingEntry() {
            return indexShard.routingEntry();
        }

    }

    class PrimaryShardReference extends ShardReference
            implements ReplicationOperation.Primary<Request, ReplicaRequest, PrimaryResult<ReplicaRequest, Response>> {

        PrimaryShardReference(IndexShard indexShard, Releasable operationLock) {
            super(indexShard, operationLock);
        }

        public boolean isRelocated() {
            return indexShard.isRelocatedPrimary();
        }

        @Override
        public void failShard(String reason, Exception e) {
            try {
                indexShard.failShard(reason, e);
            } catch (Exception inner) {
                e.addSuppressed(inner);
            }
        }

        @Override
        public PrimaryResult perform(Request request) throws Exception {
            PrimaryResult result = shardOperationOnPrimary(request, indexShard);
            assert result.replicaRequest() == null || result.finalFailure == null : "a replica request [" + result.replicaRequest()
                + "] with a primary failure [" + result.finalFailure + "]";
            return result;
        }

        @Override
        public void updateLocalCheckpointForShard(String allocationId, long checkpoint) {
            indexShard.updateLocalCheckpointForShard(allocationId, checkpoint);
        }

        @Override
        public void updateGlobalCheckpointForShard(final String allocationId, final long globalCheckpoint) {
            indexShard.updateGlobalCheckpointForShard(allocationId, globalCheckpoint);
        }

        @Override
        public long localCheckpoint() {
            return indexShard.getLocalCheckpoint();
        }

        @Override
        public long globalCheckpoint() {
            return indexShard.getGlobalCheckpoint();
        }

        @Override
        public long maxSeqNoOfUpdatesOrDeletes() {
            return indexShard.getMaxSeqNoOfUpdatesOrDeletes();
        }

        @Override
        public ReplicationGroup getReplicationGroup() {
            return indexShard.getReplicationGroup();
        }
    }


    public static class ReplicaResponse extends ActionResponse implements ReplicationOperation.ReplicaResponse {
        private long localCheckpoint;
        private long globalCheckpoint;

        ReplicaResponse() {

        }

        public ReplicaResponse(long localCheckpoint, long globalCheckpoint) {
            assert localCheckpoint != SequenceNumbers.UNASSIGNED_SEQ_NO;
            this.localCheckpoint = localCheckpoint;
            this.globalCheckpoint = globalCheckpoint;
        }

        @Override
        public void readFrom(StreamInput in) throws IOException {
            super.readFrom(in);
            if (in.getVersion().onOrAfter(Version.V_6_0_0_alpha1)) {
                localCheckpoint = in.readZLong();
            } else {
                localCheckpoint = SequenceNumbers.PRE_60_NODE_CHECKPOINT;
            }
            if (in.getVersion().onOrAfter(Version.V_6_0_0_rc1)) {
                globalCheckpoint = in.readZLong();
            } else {
                globalCheckpoint = SequenceNumbers.PRE_60_NODE_CHECKPOINT;
            }
        }

        @Override
        public void writeTo(StreamOutput out) throws IOException {
            super.writeTo(out);
            if (out.getVersion().onOrAfter(Version.V_6_0_0_alpha1)) {
                out.writeZLong(localCheckpoint);
            }
            if (out.getVersion().onOrAfter(Version.V_6_0_0_rc1)) {
                out.writeZLong(globalCheckpoint);
            }
        }

        @Override
        public long localCheckpoint() {
            return localCheckpoint;
        }

        @Override
        public long globalCheckpoint() {
            return globalCheckpoint;
        }

    }

    protected class ReplicasProxy implements ReplicationOperation.Replicas<ReplicaRequest> {

        protected final long primaryTerm;

        public ReplicasProxy(long primaryTerm) {
            this.primaryTerm = primaryTerm;
        }

        @Override
        public void performOn(
                final ShardRouting replica,
                final ReplicaRequest request,
                final long globalCheckpoint,
                final long maxSeqNoOfUpdatesOrDeletes,
                final ActionListener<ReplicationOperation.ReplicaResponse> listener) {
            String nodeId = replica.currentNodeId();
            final DiscoveryNode node = clusterService.state().nodes().get(nodeId);
            if (node == null) {
                listener.onFailure(new NoNodeAvailableException("unknown node [" + nodeId + "]"));
                return;
            }
            final ConcreteReplicaRequest<ReplicaRequest> replicaRequest = new ConcreteReplicaRequest<>(
                request, replica.allocationId().getId(), primaryTerm, globalCheckpoint, maxSeqNoOfUpdatesOrDeletes);
            sendReplicaRequest(replicaRequest, node, listener);
        }

        @Override
        public void failShardIfNeeded(ShardRouting replica, String message, Exception exception, ActionListener<Void> listener) {
            listener.onResponse(null);
        }

        @Override
        public void markShardCopyAsStaleIfNeeded(ShardId shardId, String allocationId, ActionListener<Void> listener) {
            listener.onResponse(null);
        }
    }

    protected void sendReplicaRequest(
            final ConcreteReplicaRequest<ReplicaRequest> replicaRequest,
            final DiscoveryNode node,
            final ActionListener<ReplicationOperation.ReplicaResponse> listener) {
        final ActionListenerResponseHandler<ReplicaResponse> handler = new ActionListenerResponseHandler<>(listener, in -> {
            ReplicaResponse replicaResponse = new ReplicaResponse();
            replicaResponse.readFrom(in);
            return replicaResponse;
        });
        transportService.sendRequest(node, transportReplicaAction, replicaRequest, transportOptions, handler);
    }

    public static class ConcreteShardRequest<R extends TransportRequest> extends TransportRequest {

        private String targetAllocationID;

        private long primaryTerm;

        private R request;

        public ConcreteShardRequest(Supplier<R> requestSupplier) {
            request = requestSupplier.get();
            targetAllocationID = null;
            primaryTerm = UNASSIGNED_PRIMARY_TERM;
        }

        public ConcreteShardRequest(R request, String targetAllocationID, long primaryTerm) {
            Objects.requireNonNull(request);
            Objects.requireNonNull(targetAllocationID);
            this.request = request;
            this.targetAllocationID = targetAllocationID;
            this.primaryTerm = primaryTerm;
        }

        @Override
        public void setParentTask(String parentTaskNode, long parentTaskId) {
            request.setParentTask(parentTaskNode, parentTaskId);
        }

        @Override
        public void setParentTask(TaskId taskId) {
            request.setParentTask(taskId);
        }

        @Override
        public TaskId getParentTask() {
            return request.getParentTask();
        }
        @Override
        public Task createTask(long id, String type, String action, TaskId parentTaskId, Map<String, String> headers) {
            return request.createTask(id, type, action, parentTaskId, headers);
        }

        @Override
        public String getDescription() {
            return "[" + request.getDescription() + "] for aID [" + targetAllocationID + "] and term [" + primaryTerm + "]";
        }

        @Override
        public void readFrom(StreamInput in) throws IOException {
            targetAllocationID = in.readString();
            primaryTerm = in.readVLong();
            request.readFrom(in);
        }

        @Override
        public void writeTo(StreamOutput out) throws IOException {
            out.writeString(targetAllocationID);
            out.writeVLong(primaryTerm);
            request.writeTo(out);
        }

        public R getRequest() {
            return request;
        }

        public String getTargetAllocationID() {
            return targetAllocationID;
        }

        public long getPrimaryTerm() {
            return primaryTerm;
        }

        @Override
        public String toString() {
            return "request: " + request + ", target allocation id: " + targetAllocationID + ", primary term: " + primaryTerm;
        }
    }

    protected static final class ConcreteReplicaRequest<R extends TransportRequest> extends ConcreteShardRequest<R> {

        private long globalCheckpoint;
        private long maxSeqNoOfUpdatesOrDeletes;

        public ConcreteReplicaRequest(final Supplier<R> requestSupplier) {
            super(requestSupplier);
        }

        public ConcreteReplicaRequest(final R request, final String targetAllocationID, final long primaryTerm,
                                      final long globalCheckpoint, final long maxSeqNoOfUpdatesOrDeletes) {
            super(request, targetAllocationID, primaryTerm);
            this.globalCheckpoint = globalCheckpoint;
            this.maxSeqNoOfUpdatesOrDeletes = maxSeqNoOfUpdatesOrDeletes;
        }

        @Override
        public void readFrom(StreamInput in) throws IOException {
            super.readFrom(in);
            if (in.getVersion().onOrAfter(Version.V_6_0_0_alpha1)) {
                globalCheckpoint = in.readZLong();
            } else {
                globalCheckpoint = SequenceNumbers.UNASSIGNED_SEQ_NO;
            }
            if (in.getVersion().onOrAfter(Version.V_6_5_0)) {
                maxSeqNoOfUpdatesOrDeletes = in.readZLong();
            } else {
                maxSeqNoOfUpdatesOrDeletes = SequenceNumbers.UNASSIGNED_SEQ_NO;
            }
        }

        @Override
        public void writeTo(StreamOutput out) throws IOException {
            super.writeTo(out);
            if (out.getVersion().onOrAfter(Version.V_6_0_0_alpha1)) {
                out.writeZLong(globalCheckpoint);
            }
            if (out.getVersion().onOrAfter(Version.V_6_5_0)) {
                out.writeZLong(maxSeqNoOfUpdatesOrDeletes);
            }
        }

        public long getGlobalCheckpoint() {
            return globalCheckpoint;
        }

        public long getMaxSeqNoOfUpdatesOrDeletes() {
            return maxSeqNoOfUpdatesOrDeletes;
        }

        @Override
        public String toString() {
            return "ConcreteReplicaRequest{" +
                    "targetAllocationID='" + getTargetAllocationID() + '\'' +
                    ", primaryTerm='" + getPrimaryTerm() + '\'' +
                    ", request=" + getRequest() +
                    ", globalCheckpoint=" + globalCheckpoint +
                    ", maxSeqNoOfUpdatesOrDeletes=" + maxSeqNoOfUpdatesOrDeletes +
                    '}';
        }
    }

    static void setPhase(ReplicationTask task, String phase) {
        if (task != null) {
            task.setPhase(phase);
        }
    }
}
