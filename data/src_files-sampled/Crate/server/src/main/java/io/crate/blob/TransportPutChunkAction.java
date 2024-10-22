package io.crate.blob;

import org.elasticsearch.action.ActionListener;
import org.elasticsearch.action.support.replication.TransportReplicationAction;
import org.elasticsearch.cluster.action.shard.ShardStateAction;
import org.elasticsearch.cluster.service.ClusterService;
import org.elasticsearch.common.inject.Inject;
import org.elasticsearch.common.io.stream.StreamInput;
import org.elasticsearch.index.shard.IndexShard;
import org.elasticsearch.indices.IndicesService;
import org.elasticsearch.threadpool.ThreadPool;
import org.elasticsearch.transport.TransportService;

import java.io.IOException;

public class TransportPutChunkAction extends TransportReplicationAction<PutChunkRequest, PutChunkReplicaRequest, PutChunkResponse> {

    private final BlobTransferTarget transferTarget;

    @Inject
    public TransportPutChunkAction(TransportService transportService,
                                   ClusterService clusterService,
                                   IndicesService indicesService,
                                   ThreadPool threadPool,
                                   ShardStateAction shardStateAction,
                                   BlobTransferTarget transferTarget) {
        super(
            PutChunkAction.NAME,
            transportService,
            clusterService,
            indicesService,
            threadPool,
            shardStateAction,
            PutChunkRequest::new,
            PutChunkReplicaRequest::new,
            ThreadPool.Names.WRITE
        );
        this.transferTarget = transferTarget;
    }

    @Override
    protected PutChunkResponse newResponseInstance(StreamInput in) throws IOException {
        return new PutChunkResponse(in);
    }

    @Override
    protected void shardOperationOnPrimary(PutChunkRequest request,
                                           IndexShard primary,
                                           ActionListener<PrimaryResult<PutChunkReplicaRequest, PutChunkResponse>> listener) {
        ActionListener.completeWith(listener, () -> {
            PutChunkResponse response = new PutChunkResponse();
            transferTarget.continueTransfer(request, response);
            final PutChunkReplicaRequest replicaRequest = new PutChunkReplicaRequest(
                request.shardId(),
                clusterService.localNode().getId(),
                request.transferId(),
                request.currentPos(),
                request.content(),
                request.isLast()
            );
            replicaRequest.index(request.index());
            return new PrimaryResult<>(replicaRequest, response);
        });
    }

    @Override
    protected ReplicaResult shardOperationOnReplica(PutChunkReplicaRequest shardRequest, IndexShard replica) {
        PutChunkResponse response = new PutChunkResponse();
        transferTarget.continueTransfer(shardRequest, response);
        return new ReplicaResult();
    }
}
