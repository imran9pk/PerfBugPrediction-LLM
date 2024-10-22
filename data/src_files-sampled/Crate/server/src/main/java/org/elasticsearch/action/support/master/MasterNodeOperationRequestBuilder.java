package org.elasticsearch.action.support.master;

import org.elasticsearch.action.ActionType;
import org.elasticsearch.action.ActionRequestBuilder;
import org.elasticsearch.client.ElasticsearchClient;
import io.crate.common.unit.TimeValue;
import org.elasticsearch.transport.TransportResponse;

public abstract class MasterNodeOperationRequestBuilder<Request extends MasterNodeRequest<Request>, Response extends TransportResponse, RequestBuilder extends MasterNodeOperationRequestBuilder<Request, Response, RequestBuilder>>
        extends ActionRequestBuilder<Request, Response> {

    protected MasterNodeOperationRequestBuilder(ElasticsearchClient client, ActionType<Response> action, Request request) {
        super(client, action, request);
    }

    @SuppressWarnings("unchecked")
    public final RequestBuilder setMasterNodeTimeout(TimeValue timeout) {
        request.masterNodeTimeout(timeout);
        return (RequestBuilder) this;
    }

    @SuppressWarnings("unchecked")
    public final RequestBuilder setMasterNodeTimeout(String timeout) {
        request.masterNodeTimeout(timeout);
        return (RequestBuilder) this;
    }

}
