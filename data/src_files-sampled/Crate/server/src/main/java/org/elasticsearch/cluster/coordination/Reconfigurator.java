package org.elasticsearch.cluster.coordination;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.elasticsearch.cluster.coordination.CoordinationMetadata.VotingConfiguration;
import org.elasticsearch.cluster.node.DiscoveryNode;
import org.elasticsearch.common.settings.ClusterSettings;
import org.elasticsearch.common.settings.Setting;
import org.elasticsearch.common.settings.Setting.Property;
import org.elasticsearch.common.settings.Settings;

import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

public class Reconfigurator {

    private static final Logger LOGGER = LogManager.getLogger(Reconfigurator.class);

    public static final Setting<Boolean> CLUSTER_AUTO_SHRINK_VOTING_CONFIGURATION =
        Setting.boolSetting("cluster.auto_shrink_voting_configuration", true, Property.NodeScope, Property.Dynamic);

    private volatile boolean autoShrinkVotingConfiguration;

    public Reconfigurator(Settings settings, ClusterSettings clusterSettings) {
        autoShrinkVotingConfiguration = CLUSTER_AUTO_SHRINK_VOTING_CONFIGURATION.get(settings);
        clusterSettings.addSettingsUpdateConsumer(CLUSTER_AUTO_SHRINK_VOTING_CONFIGURATION, this::setAutoShrinkVotingConfiguration);
    }

    public void setAutoShrinkVotingConfiguration(boolean autoShrinkVotingConfiguration) {
        this.autoShrinkVotingConfiguration = autoShrinkVotingConfiguration;
    }

    private static int roundDownToOdd(int size) {
        return size - (size % 2 == 0 ? 1 : 0);
    }

    @Override
    public String toString() {
        return "Reconfigurator{" +
            "autoShrinkVotingConfiguration=" + autoShrinkVotingConfiguration +
            '}';
    }

    public VotingConfiguration reconfigure(Set<DiscoveryNode> liveNodes, Set<String> retiredNodeIds, DiscoveryNode currentMaster,
                                           VotingConfiguration currentConfig) {
        assert liveNodes.contains(currentMaster) : "liveNodes = " + liveNodes + " master = " + currentMaster;
        LOGGER.trace("{} reconfiguring {} based on liveNodes={}, retiredNodeIds={}, currentMaster={}",
            this, currentConfig, liveNodes, retiredNodeIds, currentMaster);

        final Set<String> liveNodeIds = liveNodes.stream()
            .filter(DiscoveryNode::isMasterNode).map(DiscoveryNode::getId).collect(Collectors.toSet());
        final Set<String> currentConfigNodeIds = currentConfig.getNodeIds();

        final Set<VotingConfigNode> orderedCandidateNodes = new TreeSet<>();
        liveNodes.stream()
            .filter(DiscoveryNode::isMasterNode)
            .filter(n -> retiredNodeIds.contains(n.getId()) == false)
            .forEach(n -> orderedCandidateNodes.add(new VotingConfigNode(n.getId(), true,
                n.getId().equals(currentMaster.getId()), currentConfigNodeIds.contains(n.getId()))));
        currentConfigNodeIds.stream()
            .filter(nid -> liveNodeIds.contains(nid) == false)
            .filter(nid -> retiredNodeIds.contains(nid) == false)
            .forEach(nid -> orderedCandidateNodes.add(new VotingConfigNode(nid, false, false, true)));

        final int nonRetiredConfigSize = Math.toIntExact(orderedCandidateNodes.stream().filter(n -> n.inCurrentConfig).count());
        final int minimumConfigEnforcedSize = autoShrinkVotingConfiguration ? (nonRetiredConfigSize < 3 ? 1 : 3) : nonRetiredConfigSize;
        final int nonRetiredLiveNodeCount = Math.toIntExact(orderedCandidateNodes.stream().filter(n -> n.live).count());
        final int targetSize = Math.max(roundDownToOdd(nonRetiredLiveNodeCount), minimumConfigEnforcedSize);

        final VotingConfiguration newConfig = new VotingConfiguration(
            orderedCandidateNodes.stream()
                .limit(targetSize)
                .map(n -> n.id)
                .collect(Collectors.toSet()));

        if (newConfig.hasQuorum(liveNodeIds)) {
            return newConfig;
        } else {
            return currentConfig;
        }
    }

    static class VotingConfigNode implements Comparable<VotingConfigNode> {
        final String id;
        final boolean live;
        final boolean currentMaster;
        final boolean inCurrentConfig;

        VotingConfigNode(String id, boolean live, boolean currentMaster, boolean inCurrentConfig) {
            this.id = id;
            this.live = live;
            this.currentMaster = currentMaster;
            this.inCurrentConfig = inCurrentConfig;
        }

        @Override
        public int compareTo(VotingConfigNode other) {
            final int currentMasterComp = Boolean.compare(other.currentMaster, currentMaster);
            if (currentMasterComp != 0) {
                return currentMasterComp;
            }
            final int liveComp = Boolean.compare(other.live, live);
            if (liveComp != 0) {
                return liveComp;
            }
            final int inCurrentConfigComp = Boolean.compare(other.inCurrentConfig, inCurrentConfig);
            if (inCurrentConfigComp != 0) {
                return inCurrentConfigComp;
            }
            return id.compareTo(other.id);
        }

        @Override
        public String toString() {
            return "VotingConfigNode{" +
                "id='" + id + '\'' +
                ", live=" + live +
                ", currentMaster=" + currentMaster +
                ", inCurrentConfig=" + inCurrentConfig +
                '}';
        }
    }
}
