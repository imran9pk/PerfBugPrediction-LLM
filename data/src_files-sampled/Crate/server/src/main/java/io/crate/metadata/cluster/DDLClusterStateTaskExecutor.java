package io.crate.metadata.cluster;

import org.elasticsearch.cluster.ClusterState;
import org.elasticsearch.cluster.ClusterStateTaskExecutor;

import java.util.List;

public abstract class DDLClusterStateTaskExecutor<Request> implements ClusterStateTaskExecutor<Request> {

    @Override
    public ClusterTasksResult<Request> execute(ClusterState currentState, List<Request> requests) throws Exception {
        ClusterTasksResult.Builder<Request> builder = ClusterTasksResult.builder();

        for (Request request : requests) {
            try {
                currentState = execute(currentState, request);
                builder.success(request);
            } catch (Exception e) {
                builder.failure(request, e);
            }
        }

        return builder.build(currentState);
    }

    protected abstract ClusterState execute(ClusterState currentState, Request request) throws Exception;
}
