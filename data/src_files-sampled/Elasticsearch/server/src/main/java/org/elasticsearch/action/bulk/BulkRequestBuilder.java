package org.elasticsearch.action.bulk;

import org.elasticsearch.action.ActionRequestBuilder;
import org.elasticsearch.action.delete.DeleteRequest;
import org.elasticsearch.action.delete.DeleteRequestBuilder;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.index.IndexRequestBuilder;
import org.elasticsearch.action.support.ActiveShardCount;
import org.elasticsearch.action.support.WriteRequestBuilder;
import org.elasticsearch.action.support.replication.ReplicationRequest;
import org.elasticsearch.action.update.UpdateRequest;
import org.elasticsearch.action.update.UpdateRequestBuilder;
import org.elasticsearch.client.ElasticsearchClient;
import org.elasticsearch.common.Nullable;
import org.elasticsearch.common.unit.TimeValue;
import org.elasticsearch.common.xcontent.XContentType;

public class BulkRequestBuilder extends ActionRequestBuilder<BulkRequest, BulkResponse, BulkRequestBuilder>
        implements WriteRequestBuilder<BulkRequestBuilder> {

    public BulkRequestBuilder(ElasticsearchClient client, BulkAction action, @Nullable String globalIndex, @Nullable String globalType) {
        super(client, action, new BulkRequest(globalIndex, globalType));
    }

    public BulkRequestBuilder(ElasticsearchClient client, BulkAction action) {
        super(client, action, new BulkRequest());
    }

    public BulkRequestBuilder add(IndexRequest request) {
        super.request.add(request);
        return this;
    }

    public BulkRequestBuilder add(IndexRequestBuilder request) {
        super.request.add(request.request());
        return this;
    }

    public BulkRequestBuilder add(DeleteRequest request) {
        super.request.add(request);
        return this;
    }

    public BulkRequestBuilder add(DeleteRequestBuilder request) {
        super.request.add(request.request());
        return this;
    }


    public BulkRequestBuilder add(UpdateRequest request) {
        super.request.add(request);
        return this;
    }

    public BulkRequestBuilder add(UpdateRequestBuilder request) {
        super.request.add(request.request());
        return this;
    }

    public BulkRequestBuilder add(byte[] data, int from, int length, XContentType xContentType) throws Exception {
        request.add(data, from, length, null, null, xContentType);
        return this;
    }

    public BulkRequestBuilder add(byte[] data, int from, int length, @Nullable String defaultIndex, @Nullable String defaultType,
                                  XContentType xContentType) throws Exception {
        request.add(data, from, length, defaultIndex, defaultType, xContentType);
        return this;
    }

    public BulkRequestBuilder setWaitForActiveShards(ActiveShardCount waitForActiveShards) {
        request.waitForActiveShards(waitForActiveShards);
        return this;
    }

    public BulkRequestBuilder setWaitForActiveShards(final int waitForActiveShards) {
        return setWaitForActiveShards(ActiveShardCount.from(waitForActiveShards));
    }

    public final BulkRequestBuilder setTimeout(TimeValue timeout) {
        request.timeout(timeout);
        return this;
    }

    public final BulkRequestBuilder setTimeout(String timeout) {
        request.timeout(timeout);
        return this;
    }

    public int numberOfActions() {
        return request.numberOfActions();
    }

    public BulkRequestBuilder pipeline(String globalPipeline) {
        request.pipeline(globalPipeline);
        return this;
    }

    public BulkRequestBuilder routing(String globalRouting) {
        request.routing(globalRouting);
        return this;
    }
}
