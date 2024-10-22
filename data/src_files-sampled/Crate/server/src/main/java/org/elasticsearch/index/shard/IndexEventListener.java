package org.elasticsearch.index.shard;

import org.elasticsearch.cluster.routing.ShardRouting;
import javax.annotation.Nullable;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.index.Index;
import org.elasticsearch.index.IndexService;
import org.elasticsearch.index.IndexSettings;
import org.elasticsearch.indices.cluster.IndicesClusterStateService.AllocatedIndices.IndexRemovalReason;

public interface IndexEventListener {

    default void shardRoutingChanged(IndexShard indexShard, @Nullable ShardRouting oldRouting,
            ShardRouting newRouting) {
    }

    default void afterIndexShardCreated(IndexShard indexShard) {
    }

    default void afterIndexShardStarted(IndexShard indexShard) {
    }

    default void beforeIndexShardClosed(ShardId shardId, @Nullable IndexShard indexShard, Settings indexSettings) {
    }

    default void afterIndexShardClosed(ShardId shardId, @Nullable IndexShard indexShard, Settings indexSettings) {
    }

    default void indexShardStateChanged(IndexShard indexShard,
                                        @Nullable IndexShardState previousState,
                                        IndexShardState currentState,
                                        @Nullable String reason) {
    }

    default void onShardInactive(IndexShard indexShard) {
    }

    default void beforeIndexCreated(Index index, Settings indexSettings) {

    }

    default void afterIndexCreated(IndexService indexService) {

    }

    default void beforeIndexRemoved(IndexService indexService, IndexRemovalReason reason) {

    }

    default void afterIndexRemoved(Index index, IndexSettings indexSettings, IndexRemovalReason reason) {

    }

    default void beforeIndexShardCreated(ShardId shardId, Settings indexSettings) {
    }

    default void beforeIndexShardDeleted(ShardId shardId, Settings indexSettings) {
    }

    default void afterIndexShardDeleted(ShardId shardId, Settings indexSettings) {
    }

    default void beforeIndexAddedToCluster(Index index, Settings indexSettings) {
    }

    default void onStoreCreated(ShardId shardId) {
    }

    default void onStoreClosed(ShardId shardId) {
    }
}
