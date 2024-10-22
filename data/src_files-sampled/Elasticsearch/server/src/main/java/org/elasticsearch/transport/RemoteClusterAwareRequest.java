package org.elasticsearch.transport;

import org.elasticsearch.cluster.node.DiscoveryNode;

public interface RemoteClusterAwareRequest {

    DiscoveryNode getPreferredTargetNode();

}
