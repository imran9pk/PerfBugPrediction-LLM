package org.elasticsearch.action.admin.cluster.snapshots.status;

import org.elasticsearch.action.support.master.MasterNodeOperationRequestBuilder;
import org.elasticsearch.client.ElasticsearchClient;
import org.elasticsearch.common.util.ArrayUtils;

public class SnapshotsStatusRequestBuilder extends MasterNodeOperationRequestBuilder<SnapshotsStatusRequest,
        SnapshotsStatusResponse, SnapshotsStatusRequestBuilder> {

    public SnapshotsStatusRequestBuilder(ElasticsearchClient client, SnapshotsStatusAction action) {
        super(client, action, new SnapshotsStatusRequest());
    }

    public SnapshotsStatusRequestBuilder(ElasticsearchClient client, SnapshotsStatusAction action, String repository) {
        super(client, action, new SnapshotsStatusRequest(repository));
    }

    public SnapshotsStatusRequestBuilder setRepository(String repository) {
        request.repository(repository);
        return this;
    }

    public SnapshotsStatusRequestBuilder setSnapshots(String... snapshots) {
        request.snapshots(snapshots);
        return this;
    }

    public SnapshotsStatusRequestBuilder addSnapshots(String... snapshots) {
        request.snapshots(ArrayUtils.concat(request.snapshots(), snapshots));
        return this;
    }

    public SnapshotsStatusRequestBuilder setIgnoreUnavailable(boolean ignoreUnavailable) {
        request.ignoreUnavailable(ignoreUnavailable);
        return this;
    }
}
