package org.elasticsearch.cluster.routing.allocation.decider;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.elasticsearch.cluster.routing.RecoverySource;
import org.elasticsearch.cluster.routing.RoutingNode;
import org.elasticsearch.cluster.routing.ShardRouting;
import org.elasticsearch.cluster.routing.UnassignedInfo;
import org.elasticsearch.cluster.routing.allocation.RoutingAllocation;
import org.elasticsearch.cluster.routing.ShardRoutingState;
import org.elasticsearch.common.settings.ClusterSettings;
import org.elasticsearch.common.settings.Setting;
import org.elasticsearch.common.settings.Setting.Property;

import io.crate.types.DataTypes;

import org.elasticsearch.common.settings.Settings;

import static org.elasticsearch.cluster.routing.allocation.decider.Decision.THROTTLE;
import static org.elasticsearch.cluster.routing.allocation.decider.Decision.YES;

public class ThrottlingAllocationDecider extends AllocationDecider {

    private static final Logger LOGGER = LogManager.getLogger(ThrottlingAllocationDecider.class);

    public static final int DEFAULT_CLUSTER_ROUTING_ALLOCATION_NODE_CONCURRENT_RECOVERIES = 2;
    public static final int DEFAULT_CLUSTER_ROUTING_ALLOCATION_NODE_INITIAL_PRIMARIES_RECOVERIES = 4;
    public static final String NAME = "throttling";
    public static final Setting<Integer> CLUSTER_ROUTING_ALLOCATION_NODE_CONCURRENT_RECOVERIES_SETTING =
        new Setting<>("cluster.routing.allocation.node_concurrent_recoveries",
            Integer.toString(DEFAULT_CLUSTER_ROUTING_ALLOCATION_NODE_CONCURRENT_RECOVERIES),
            (s) -> Setting.parseInt(s, 0, "cluster.routing.allocation.node_concurrent_recoveries"),
            DataTypes.INTEGER,
            Property.Dynamic, Property.NodeScope);
    public static final Setting<Integer> CLUSTER_ROUTING_ALLOCATION_NODE_INITIAL_PRIMARIES_RECOVERIES_SETTING =
        Setting.intSetting("cluster.routing.allocation.node_initial_primaries_recoveries",
            DEFAULT_CLUSTER_ROUTING_ALLOCATION_NODE_INITIAL_PRIMARIES_RECOVERIES, 0,
            Property.Dynamic, Property.NodeScope);
    public static final Setting<Integer> CLUSTER_ROUTING_ALLOCATION_NODE_CONCURRENT_INCOMING_RECOVERIES_SETTING =
        new Setting<>("cluster.routing.allocation.node_concurrent_incoming_recoveries",
            CLUSTER_ROUTING_ALLOCATION_NODE_CONCURRENT_RECOVERIES_SETTING::getRaw,
            (s) -> Setting.parseInt(s, 0, "cluster.routing.allocation.node_concurrent_incoming_recoveries"),
            DataTypes.INTEGER,
            Property.Dynamic, Property.NodeScope);
    public static final Setting<Integer> CLUSTER_ROUTING_ALLOCATION_NODE_CONCURRENT_OUTGOING_RECOVERIES_SETTING =
        new Setting<>("cluster.routing.allocation.node_concurrent_outgoing_recoveries",
            CLUSTER_ROUTING_ALLOCATION_NODE_CONCURRENT_RECOVERIES_SETTING::getRaw,
            (s) -> Setting.parseInt(s, 0, "cluster.routing.allocation.node_concurrent_outgoing_recoveries"),
            DataTypes.INTEGER,
            Property.Dynamic, Property.NodeScope);


    private volatile int primariesInitialRecoveries;
    private volatile int concurrentIncomingRecoveries;
    private volatile int concurrentOutgoingRecoveries;

    public ThrottlingAllocationDecider(Settings settings, ClusterSettings clusterSettings) {
        this.primariesInitialRecoveries = CLUSTER_ROUTING_ALLOCATION_NODE_INITIAL_PRIMARIES_RECOVERIES_SETTING.get(settings);
        concurrentIncomingRecoveries = CLUSTER_ROUTING_ALLOCATION_NODE_CONCURRENT_INCOMING_RECOVERIES_SETTING.get(settings);
        concurrentOutgoingRecoveries = CLUSTER_ROUTING_ALLOCATION_NODE_CONCURRENT_OUTGOING_RECOVERIES_SETTING.get(settings);

        clusterSettings.addSettingsUpdateConsumer(CLUSTER_ROUTING_ALLOCATION_NODE_INITIAL_PRIMARIES_RECOVERIES_SETTING,
                this::setPrimariesInitialRecoveries);
        clusterSettings.addSettingsUpdateConsumer(CLUSTER_ROUTING_ALLOCATION_NODE_CONCURRENT_INCOMING_RECOVERIES_SETTING,
                this::setConcurrentIncomingRecoverries);
        clusterSettings.addSettingsUpdateConsumer(CLUSTER_ROUTING_ALLOCATION_NODE_CONCURRENT_OUTGOING_RECOVERIES_SETTING,
                this::setConcurrentOutgoingRecoverries);

        LOGGER.debug("using node_concurrent_outgoing_recoveries [{}], node_concurrent_incoming_recoveries [{}], " +
                        "node_initial_primaries_recoveries [{}]",
                concurrentOutgoingRecoveries, concurrentIncomingRecoveries, primariesInitialRecoveries);
    }

    private void setConcurrentIncomingRecoverries(int concurrentIncomingRecoveries) {
        this.concurrentIncomingRecoveries = concurrentIncomingRecoveries;
    }

    private void setConcurrentOutgoingRecoverries(int concurrentOutgoingRecoveries) {
        this.concurrentOutgoingRecoveries = concurrentOutgoingRecoveries;
    }

    private void setPrimariesInitialRecoveries(int primariesInitialRecoveries) {
        this.primariesInitialRecoveries = primariesInitialRecoveries;
    }

    @Override
    public Decision canAllocate(ShardRouting shardRouting, RoutingNode node, RoutingAllocation allocation) {
        if (shardRouting.primary() && shardRouting.unassigned()) {
            assert initializingShard(shardRouting, node.nodeId()).recoverySource().getType() != RecoverySource.Type.PEER;
            int primariesInRecovery = 0;
            for (ShardRouting shard : node.shardsWithState(ShardRoutingState.INITIALIZING)) {
                if (shard.primary() && shard.relocatingNodeId() == null) {
                    primariesInRecovery++;
                }
            }
            if (primariesInRecovery >= primariesInitialRecoveries) {
                return allocation.decision(THROTTLE, NAME,
                    "reached the limit of ongoing initial primary recoveries [%d], cluster setting [%s=%d]",
                    primariesInRecovery, CLUSTER_ROUTING_ALLOCATION_NODE_INITIAL_PRIMARIES_RECOVERIES_SETTING.getKey(),
                    primariesInitialRecoveries);
            } else {
                return allocation.decision(YES, NAME, "below primary recovery limit of [%d]", primariesInitialRecoveries);
            }
        } else {
            assert initializingShard(shardRouting, node.nodeId()).recoverySource().getType() == RecoverySource.Type.PEER;

            int currentInRecoveries = allocation.routingNodes().getIncomingRecoveries(node.nodeId());
            if (currentInRecoveries >= concurrentIncomingRecoveries) {
                return allocation.decision(THROTTLE, NAME,
                    "reached the limit of incoming shard recoveries [%d], cluster setting [%s=%d] (can also be set via [%s])",
                    currentInRecoveries, CLUSTER_ROUTING_ALLOCATION_NODE_CONCURRENT_INCOMING_RECOVERIES_SETTING.getKey(),
                    concurrentIncomingRecoveries,
                    CLUSTER_ROUTING_ALLOCATION_NODE_CONCURRENT_RECOVERIES_SETTING.getKey());
            } else {
                ShardRouting primaryShard = allocation.routingNodes().activePrimary(shardRouting.shardId());
                if (primaryShard == null) {
                    return allocation.decision(Decision.NO, NAME, "primary shard for this replica is not yet active");
                }
                int primaryNodeOutRecoveries = allocation.routingNodes().getOutgoingRecoveries(primaryShard.currentNodeId());
                if (primaryNodeOutRecoveries >= concurrentOutgoingRecoveries) {
                    return allocation.decision(THROTTLE, NAME,
                        "reached the limit of outgoing shard recoveries [%d] on the node [%s] which holds the primary, " +
                        "cluster setting [%s=%d] (can also be set via [%s])",
                        primaryNodeOutRecoveries, primaryShard.currentNodeId(),
                        CLUSTER_ROUTING_ALLOCATION_NODE_CONCURRENT_OUTGOING_RECOVERIES_SETTING.getKey(),
                        concurrentOutgoingRecoveries,
                        CLUSTER_ROUTING_ALLOCATION_NODE_CONCURRENT_RECOVERIES_SETTING.getKey());
                } else {
                    return allocation.decision(YES, NAME, "below shard recovery limit of outgoing: [%d < %d] incoming: [%d < %d]",
                        primaryNodeOutRecoveries,
                        concurrentOutgoingRecoveries,
                        currentInRecoveries,
                        concurrentIncomingRecoveries);
                }
            }
        }
    }

    private ShardRouting initializingShard(ShardRouting shardRouting, String currentNodeId) {
        final ShardRouting initializingShard;
        if (shardRouting.unassigned()) {
            initializingShard = shardRouting.initialize(currentNodeId, null, ShardRouting.UNAVAILABLE_EXPECTED_SHARD_SIZE);
        } else if (shardRouting.initializing()) {
            UnassignedInfo unassignedInfo = shardRouting.unassignedInfo();
            if (unassignedInfo == null) {
                unassignedInfo = new UnassignedInfo(UnassignedInfo.Reason.ALLOCATION_FAILED, "fake");
            }
            initializingShard = shardRouting.moveToUnassigned(unassignedInfo)
                .initialize(currentNodeId, null, ShardRouting.UNAVAILABLE_EXPECTED_SHARD_SIZE);
        } else if (shardRouting.relocating()) {
            initializingShard = shardRouting.cancelRelocation()
                .relocate(currentNodeId, ShardRouting.UNAVAILABLE_EXPECTED_SHARD_SIZE)
                .getTargetRelocatingShard();
        } else {
            assert shardRouting.started();
            initializingShard = shardRouting.relocate(currentNodeId, ShardRouting.UNAVAILABLE_EXPECTED_SHARD_SIZE)
                .getTargetRelocatingShard();
        }
        assert initializingShard.initializing();
        return initializingShard;
    }
}
