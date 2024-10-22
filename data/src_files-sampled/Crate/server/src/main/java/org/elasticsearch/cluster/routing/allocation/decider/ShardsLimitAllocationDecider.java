package org.elasticsearch.cluster.routing.allocation.decider;

import org.elasticsearch.cluster.metadata.IndexMetadata;
import org.elasticsearch.cluster.routing.RoutingNode;
import org.elasticsearch.cluster.routing.ShardRouting;
import org.elasticsearch.cluster.routing.ShardRoutingState;
import org.elasticsearch.cluster.routing.allocation.RoutingAllocation;
import org.elasticsearch.common.settings.ClusterSettings;
import org.elasticsearch.common.settings.Setting;
import org.elasticsearch.common.settings.Setting.Property;
import org.elasticsearch.common.settings.Settings;

import java.util.function.BiPredicate;

public class ShardsLimitAllocationDecider extends AllocationDecider {

    public static final String NAME = "shards_limit";
    private final Settings settings;

    private volatile int clusterShardLimit;

    public static final Setting<Integer> INDEX_TOTAL_SHARDS_PER_NODE_SETTING =
        Setting.intSetting(
            "index.routing.allocation.total_shards_per_node",
            -1,
            -1,
            Property.Dynamic,
            Property.IndexScope
        );

    public static final Setting<Integer> CLUSTER_TOTAL_SHARDS_PER_NODE_SETTING =
        Setting.intSetting(
            "cluster.routing.allocation.total_shards_per_node",
            -1,
            -1,
            Property.Dynamic,
            Property.NodeScope
        );

    public ShardsLimitAllocationDecider(Settings settings, ClusterSettings clusterSettings) {
        this.settings = settings;
        this.clusterShardLimit = CLUSTER_TOTAL_SHARDS_PER_NODE_SETTING.get(settings);
        clusterSettings.addSettingsUpdateConsumer(CLUSTER_TOTAL_SHARDS_PER_NODE_SETTING, this::setClusterShardLimit);
    }

    private void setClusterShardLimit(int clusterShardLimit) {
        this.clusterShardLimit = clusterShardLimit;
    }

    @Override
    public Decision canAllocate(ShardRouting shardRouting, RoutingNode node, RoutingAllocation allocation) {
        return doDecide(shardRouting, node, allocation, (count, limit) -> count >= limit);
    }

    @Override
    public Decision canRemain(ShardRouting shardRouting, RoutingNode node, RoutingAllocation allocation) {
        return doDecide(shardRouting, node, allocation, (count, limit) -> count > limit);

    }

    private Decision doDecide(ShardRouting shardRouting, RoutingNode node, RoutingAllocation allocation,
                              BiPredicate<Integer, Integer> decider) {
        IndexMetadata indexMd = allocation.metadata().getIndexSafe(shardRouting.index());
        final int indexShardLimit = INDEX_TOTAL_SHARDS_PER_NODE_SETTING.get(indexMd.getSettings(), settings);
        final int clusterShardLimit = this.clusterShardLimit;

        if (indexShardLimit <= 0 && clusterShardLimit <= 0) {
            return allocation.decision(Decision.YES, NAME, "total shard limits are disabled: [index: %d, cluster: %d] <= 0",
                    indexShardLimit, clusterShardLimit);
        }

        int indexShardCount = 0;
        int nodeShardCount = 0;
        for (ShardRouting nodeShard : node) {
            if (nodeShard.relocating()) {
                continue;
            }
            nodeShardCount++;
            if (nodeShard.index().equals(shardRouting.index())) {
                indexShardCount++;
            }
        }

        if (clusterShardLimit > 0 && decider.test(nodeShardCount, clusterShardLimit)) {
            return allocation.decision(Decision.NO, NAME,
                "too many shards [%d] allocated to this node, cluster setting [%s=%d]",
                nodeShardCount, CLUSTER_TOTAL_SHARDS_PER_NODE_SETTING.getKey(), clusterShardLimit);
        }
        if (indexShardLimit > 0 && decider.test(indexShardCount, indexShardLimit)) {
            return allocation.decision(Decision.NO, NAME,
                "too many shards [%d] allocated to this node for index [%s], index setting [%s=%d]",
                indexShardCount, shardRouting.getIndexName(), INDEX_TOTAL_SHARDS_PER_NODE_SETTING.getKey(), indexShardLimit);
        }
        return allocation.decision(Decision.YES, NAME,
            "the shard count [%d] for this node is under the index limit [%d] and cluster level node limit [%d]",
            nodeShardCount, indexShardLimit, clusterShardLimit);
    }

    @Override
    public Decision canAllocate(RoutingNode node, RoutingAllocation allocation) {
        final int clusterShardLimit = this.clusterShardLimit;

        if (clusterShardLimit <= 0) {
            return allocation.decision(Decision.YES, NAME, "total shard limits are disabled: [cluster: %d] <= 0",
                    clusterShardLimit);
        }

        int nodeShardCount = 0;
        for (ShardRouting nodeShard : node) {
            if (nodeShard.relocating()) {
                continue;
            }
            nodeShardCount++;
        }
        if (nodeShardCount >= clusterShardLimit) {
            return allocation.decision(Decision.NO, NAME,
                "too many shards [%d] allocated to this node, cluster setting [%s=%d]",
                nodeShardCount, CLUSTER_TOTAL_SHARDS_PER_NODE_SETTING.getKey(), clusterShardLimit);
        }
        return allocation.decision(Decision.YES, NAME,
            "the shard count [%d] for this node is under the cluster level node limit [%d]",
            nodeShardCount, clusterShardLimit);
    }
}
