package org.elasticsearch.index.seqno;

import java.io.IOException;

import org.elasticsearch.action.ActionListener;
import org.elasticsearch.action.ActionType;
import org.elasticsearch.action.support.replication.ReplicationRequest;
import org.elasticsearch.action.support.replication.ReplicationResponse;
import org.elasticsearch.action.support.replication.TransportReplicationAction;
import org.elasticsearch.cluster.action.shard.ShardStateAction;
import org.elasticsearch.cluster.service.ClusterService;
import org.elasticsearch.common.inject.Inject;
import org.elasticsearch.common.io.stream.StreamInput;
import org.elasticsearch.index.shard.IndexShard;
import org.elasticsearch.index.shard.ShardId;
import org.elasticsearch.index.translog.Translog;
import org.elasticsearch.indices.IndicesService;
import org.elasticsearch.threadpool.ThreadPool;
import org.elasticsearch.transport.TransportService;

public class GlobalCheckpointSyncAction extends TransportReplicationAction<
        GlobalCheckpointSyncAction.Request,
        GlobalCheckpointSyncAction.Request,
        ReplicationResponse> {

    public static final String ACTION_NAME = "indices:admin/seq_no/global_checkpoint_sync";
    public static final ActionType<ReplicationResponse> TYPE = new ActionType<>(ACTION_NAME);

    @Inject
    public GlobalCheckpointSyncAction(
            final TransportService transportService,
            final ClusterService clusterService,
            final IndicesService indicesService,
            final ThreadPool threadPool,
            final ShardStateAction shardStateAction) {
        super(
                ACTION_NAME,
                transportService,
                clusterService,
                indicesService,
                threadPool,
                shardStateAction,
                Request::new,
                Request::new,
                ThreadPool.Names.MANAGEMENT);
    }

    @Override
    protected ReplicationResponse newResponseInstance(StreamInput in) throws IOException {
        return new ReplicationResponse(in);
    }

    @Override
    protected void shardOperationOnPrimary(Request request, IndexShard indexShard,
                                           ActionListener<PrimaryResult<Request, ReplicationResponse>> listener) {
        ActionListener.completeWith(listener, () -> {
            maybeSyncTranslog(indexShard);
            return new PrimaryResult<>(request, new ReplicationResponse());
        });
    }

    @Override
    protected ReplicaResult shardOperationOnReplica(final Request request, final IndexShard indexShard) throws Exception {
        maybeSyncTranslog(indexShard);
        return new ReplicaResult();
    }

    private void maybeSyncTranslog(final IndexShard indexShard) throws IOException {
        if (indexShard.getTranslogDurability() == Translog.Durability.REQUEST &&
            indexShard.getLastSyncedGlobalCheckpoint() < indexShard.getLastKnownGlobalCheckpoint()) {
            indexShard.sync();
        }
    }

    public static final class Request extends ReplicationRequest<Request> {

        public Request(final ShardId shardId) {
            super(shardId);
        }

        public Request(StreamInput in) throws IOException {
            super(in);
        }

        @Override
        public String toString() {
            return "GlobalCheckpointSyncAction.Request{" +
                    "shardId=" + shardId +
                    ", timeout=" + timeout +
                    ", index='" + index + '\'' +
                    ", waitForActiveShards=" + waitForActiveShards +
                    "}";
        }
    }

}
