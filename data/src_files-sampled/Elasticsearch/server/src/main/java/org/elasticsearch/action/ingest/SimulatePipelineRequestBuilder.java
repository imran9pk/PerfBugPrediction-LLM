package org.elasticsearch.action.ingest;

import org.elasticsearch.action.ActionRequestBuilder;
import org.elasticsearch.client.ElasticsearchClient;
import org.elasticsearch.common.bytes.BytesReference;
import org.elasticsearch.common.xcontent.XContentType;

public class SimulatePipelineRequestBuilder 
        extends ActionRequestBuilder<SimulatePipelineRequest, SimulatePipelineResponse, SimulatePipelineRequestBuilder> {

    public SimulatePipelineRequestBuilder(ElasticsearchClient client, SimulatePipelineAction action) {
        super(client, action, new SimulatePipelineRequest());
    }

    @Deprecated
    public SimulatePipelineRequestBuilder(ElasticsearchClient client, SimulatePipelineAction action, BytesReference source) {
        super(client, action, new SimulatePipelineRequest(source));
    }

    public SimulatePipelineRequestBuilder(ElasticsearchClient client, SimulatePipelineAction action, BytesReference source,
                                          XContentType xContentType) {
        super(client, action, new SimulatePipelineRequest(source, xContentType));
    }

    public SimulatePipelineRequestBuilder setId(String id) {
        request.setId(id);
        return this;
    }

    public SimulatePipelineRequestBuilder setVerbose(boolean verbose) {
        request.setVerbose(verbose);
        return this;
    }

}
