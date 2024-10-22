package org.elasticsearch.client;


import org.elasticsearch.action.Action;
import org.elasticsearch.action.ActionFuture;
import org.elasticsearch.action.ActionListener;
import org.elasticsearch.action.ActionRequest;
import org.elasticsearch.action.ActionRequestBuilder;
import org.elasticsearch.action.ActionResponse;
import org.elasticsearch.threadpool.ThreadPool;

public interface ElasticsearchClient {

    <Request extends ActionRequest, Response extends ActionResponse,
        RequestBuilder extends ActionRequestBuilder<Request, Response, RequestBuilder>> ActionFuture<Response> execute(
            Action<Request, Response, RequestBuilder> action, Request request);

    <Request extends ActionRequest, Response extends ActionResponse,
        RequestBuilder extends ActionRequestBuilder<Request, Response, RequestBuilder>> void execute(
            Action<Request, Response, RequestBuilder> action, Request request, ActionListener<Response> listener);

    <Request extends ActionRequest, Response extends ActionResponse,
        RequestBuilder extends ActionRequestBuilder<Request, Response, RequestBuilder>> RequestBuilder prepareExecute(
            Action<Request, Response, RequestBuilder> action);

    ThreadPool threadPool();

}
