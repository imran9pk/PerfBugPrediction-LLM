package org.elasticsearch.action.admin.cluster.snapshots.delete;

import org.elasticsearch.action.support.master.AcknowledgedResponse;
import org.elasticsearch.action.support.master.MasterNodeOperationRequestBuilder;
import org.elasticsearch.client.ElasticsearchClient;

public class DeleteSnapshotRequestBuilder extends MasterNodeOperationRequestBuilder<DeleteSnapshotRequest,
        AcknowledgedResponse, DeleteSnapshotRequestBuilder> {

    public DeleteSnapshotRequestBuilder(ElasticsearchClient client, DeleteSnapshotAction action) {
        super(client, action, new DeleteSnapshotRequest());
    }

    public DeleteSnapshotRequestBuilder(ElasticsearchClient client, DeleteSnapshotAction action, String repository, String snapshot) {
        super(client, action, new DeleteSnapshotRequest(repository, snapshot));
    }

    public DeleteSnapshotRequestBuilder setRepository(String repository) {
        request.repository(repository);
        return this;
    }

    public DeleteSnapshotRequestBuilder setSnapshot(String snapshot) {
        request.snapshot(snapshot);
        return this;
    }
}
