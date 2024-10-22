package org.elasticsearch.action.admin.cluster.reroute;

import org.elasticsearch.action.ActionType;

public class ClusterRerouteAction extends ActionType<ClusterRerouteResponse> {

    public static final ClusterRerouteAction INSTANCE = new ClusterRerouteAction();
    public static final String NAME = "cluster:admin/reroute";

    private ClusterRerouteAction() {
        super(NAME);
    }
}
