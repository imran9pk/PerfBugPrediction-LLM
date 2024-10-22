package org.elasticsearch.cluster.routing.allocation;

import org.elasticsearch.ExceptionsHelper;
import org.elasticsearch.cluster.routing.ShardRouting;
import org.elasticsearch.common.Nullable;

public class FailedShard {
    private final ShardRouting routingEntry;
    private final String message;
    private final Exception failure;
    private final boolean markAsStale;

    public FailedShard(ShardRouting routingEntry, String message, Exception failure, boolean markAsStale) {
        assert routingEntry.assignedToNode() : "only assigned shards can be failed " + routingEntry;
        this.routingEntry = routingEntry;
        this.message = message;
        this.failure = failure;
        this.markAsStale = markAsStale;
    }

    @Override
    public String toString() {
        return "failed shard, shard " + routingEntry + ", message [" + message + "], failure [" +
                   ExceptionsHelper.detailedMessage(failure) + "], markAsStale [" + markAsStale + "]";
    }

    public ShardRouting getRoutingEntry() {
        return routingEntry;
    }

    @Nullable
    public String getMessage() {
        return message;
    }

    @Nullable
    public Exception getFailure() {
        return failure;
    }

    public boolean markAsStale() {
        return markAsStale;
    }
}
