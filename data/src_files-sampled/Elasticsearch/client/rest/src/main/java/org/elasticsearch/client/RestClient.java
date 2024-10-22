package org.elasticsearch.client;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.http.ConnectionClosedException;
import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpHost;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.client.AuthCache;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.methods.HttpEntityEnclosingRequestBase;
import org.apache.http.client.methods.HttpHead;
import org.apache.http.client.methods.HttpOptions;
import org.apache.http.client.methods.HttpPatch;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.client.methods.HttpTrace;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.concurrent.FutureCallback;
import org.apache.http.conn.ConnectTimeoutException;
import org.apache.http.impl.auth.BasicScheme;
import org.apache.http.impl.client.BasicAuthCache;
import org.apache.http.impl.nio.client.CloseableHttpAsyncClient;
import org.apache.http.nio.client.methods.HttpAsyncMethods;
import org.apache.http.nio.protocol.HttpAsyncRequestProducer;
import org.apache.http.nio.protocol.HttpAsyncResponseConsumer;
import org.elasticsearch.client.DeadHostState.TimeSupplier;

import javax.net.ssl.SSLHandshakeException;

import java.io.Closeable;
import java.io.IOException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static java.util.Collections.singletonList;

public class RestClient implements Closeable {

    private static final Log logger = LogFactory.getLog(RestClient.class);

    private final CloseableHttpAsyncClient client;
    final List<Header> defaultHeaders;
    private final long maxRetryTimeoutMillis;
    private final String pathPrefix;
    private final AtomicInteger lastNodeIndex = new AtomicInteger(0);
    private final ConcurrentMap<HttpHost, DeadHostState> blacklist = new ConcurrentHashMap<>();
    private final FailureListener failureListener;
    private final NodeSelector nodeSelector;
    private volatile NodeTuple<List<Node>> nodeTuple;
    private final WarningsHandler warningsHandler;

    RestClient(CloseableHttpAsyncClient client, long maxRetryTimeoutMillis, Header[] defaultHeaders, List<Node> nodes, String pathPrefix,
            FailureListener failureListener, NodeSelector nodeSelector, boolean strictDeprecationMode) {
        this.client = client;
        this.maxRetryTimeoutMillis = maxRetryTimeoutMillis;
        this.defaultHeaders = Collections.unmodifiableList(Arrays.asList(defaultHeaders));
        this.failureListener = failureListener;
        this.pathPrefix = pathPrefix;
        this.nodeSelector = nodeSelector;
        this.warningsHandler = strictDeprecationMode ? WarningsHandler.STRICT : WarningsHandler.PERMISSIVE;
        setNodes(nodes);
        if (JavaVersion.current().compareTo(JavaVersion.parse("1.8.0")) < 0) {
            logger.warn("use of the low-level REST client on JDK 7 is deprecated and will be removed in version 7.0.0 of the client");
        }
    }

    public static RestClientBuilder builder(Node... nodes) {
        return new RestClientBuilder(nodes == null ? null : Arrays.asList(nodes));
    }

    public static RestClientBuilder builder(HttpHost... hosts) {
        return new RestClientBuilder(hostsToNodes(hosts));
    }

    @Deprecated
    public void setHosts(HttpHost... hosts) {
        setNodes(hostsToNodes(hosts));
    }

    public synchronized void setNodes(Collection<Node> nodes) {
        if (nodes == null || nodes.isEmpty()) {
            throw new IllegalArgumentException("nodes must not be null or empty");
        }
        AuthCache authCache = new BasicAuthCache();

        Map<HttpHost, Node> nodesByHost = new LinkedHashMap<>();
        for (Node node : nodes) {
            Objects.requireNonNull(node, "node cannot be null");
            nodesByHost.put(node.getHost(), node);
            authCache.put(node.getHost(), new BasicScheme());
        }
        this.nodeTuple = new NodeTuple<>(
                Collections.unmodifiableList(new ArrayList<>(nodesByHost.values())), authCache);
        this.blacklist.clear();
    }

    private static List<Node> hostsToNodes(HttpHost[] hosts) {
        if (hosts == null || hosts.length == 0) {
            throw new IllegalArgumentException("hosts must not be null nor empty");
        }
        List<Node> nodes = new ArrayList<>(hosts.length);
        for (HttpHost host : hosts) {
            nodes.add(new Node(host));
        }
        return nodes;
    }

    public List<Node> getNodes() {
        return nodeTuple.nodes;
    }

    public Response performRequest(Request request) throws IOException {
        SyncResponseListener listener = new SyncResponseListener(maxRetryTimeoutMillis);
        performRequestAsyncNoCatch(request, listener);
        return listener.get();
    }

    public void performRequestAsync(Request request, ResponseListener responseListener) {
        try {
            performRequestAsyncNoCatch(request, responseListener);
        } catch (Exception e) {
            responseListener.onFailure(e);
        }
    }

    @Deprecated
    public Response performRequest(String method, String endpoint, Header... headers) throws IOException {
        Request request = new Request(method, endpoint);
        addHeaders(request, headers);
        return performRequest(request);
    }

    @Deprecated
    public Response performRequest(String method, String endpoint, Map<String, String> params, Header... headers) throws IOException {
        Request request = new Request(method, endpoint);
        addParameters(request, params);
        addHeaders(request, headers);
        return performRequest(request);
    }

    @Deprecated
    public Response performRequest(String method, String endpoint, Map<String, String> params,
                                   HttpEntity entity, Header... headers) throws IOException {
        Request request = new Request(method, endpoint);
        addParameters(request, params);
        request.setEntity(entity);
        addHeaders(request, headers);
        return performRequest(request);
    }

    @Deprecated
    public Response performRequest(String method, String endpoint, Map<String, String> params,
                                   HttpEntity entity, HttpAsyncResponseConsumerFactory httpAsyncResponseConsumerFactory,
                                   Header... headers) throws IOException {
        Request request = new Request(method, endpoint);
        addParameters(request, params);
        request.setEntity(entity);
        setOptions(request, httpAsyncResponseConsumerFactory, headers);
        return performRequest(request);
    }

    @Deprecated
    public void performRequestAsync(String method, String endpoint, ResponseListener responseListener, Header... headers) {
        Request request;
        try {
            request = new Request(method, endpoint);
            addHeaders(request, headers);
        } catch (Exception e) {
            responseListener.onFailure(e);
            return;
        }
        performRequestAsync(request, responseListener);
    }

    @Deprecated
    public void performRequestAsync(String method, String endpoint, Map<String, String> params,
                                    ResponseListener responseListener, Header... headers) {
        Request request;
        try {
            request = new Request(method, endpoint);
            addParameters(request, params);
            addHeaders(request, headers);
        } catch (Exception e) {
            responseListener.onFailure(e);
            return;
        }
        performRequestAsync(request, responseListener);
    }

    @Deprecated
    public void performRequestAsync(String method, String endpoint, Map<String, String> params,
                                    HttpEntity entity, ResponseListener responseListener, Header... headers) {
        Request request;
        try {
            request = new Request(method, endpoint);
            addParameters(request, params);
            request.setEntity(entity);
            addHeaders(request, headers);
        } catch (Exception e) {
            responseListener.onFailure(e);
            return;
        }
        performRequestAsync(request, responseListener);
    }

    @Deprecated
    public void performRequestAsync(String method, String endpoint, Map<String, String> params,
                                    HttpEntity entity, HttpAsyncResponseConsumerFactory httpAsyncResponseConsumerFactory,
                                    ResponseListener responseListener, Header... headers) {
        Request request;
        try {
            request = new Request(method, endpoint);
            addParameters(request, params);
            request.setEntity(entity);
            setOptions(request, httpAsyncResponseConsumerFactory, headers);
        } catch (Exception e) {
            responseListener.onFailure(e);
            return;
        }
        performRequestAsync(request, responseListener);
    }

    void performRequestAsyncNoCatch(Request request, ResponseListener listener) throws IOException {
        Map<String, String> requestParams = new HashMap<>(request.getParameters());
        String ignoreString = requestParams.remove("ignore");
        Set<Integer> ignoreErrorCodes;
        if (ignoreString == null) {
            if (HttpHead.METHOD_NAME.equals(request.getMethod())) {
                ignoreErrorCodes = Collections.singleton(404);
            } else {
                ignoreErrorCodes = Collections.emptySet();
            }
        } else {
            String[] ignoresArray = ignoreString.split(",");
            ignoreErrorCodes = new HashSet<>();
            if (HttpHead.METHOD_NAME.equals(request.getMethod())) {
                ignoreErrorCodes.add(404);
            }
            for (String ignoreCode : ignoresArray) {
                try {
                    ignoreErrorCodes.add(Integer.valueOf(ignoreCode));
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException("ignore value should be a number, found [" + ignoreString + "] instead", e);
                }
            }
        }
        URI uri = buildUri(pathPrefix, request.getEndpoint(), requestParams);
        HttpRequestBase httpRequest = createHttpRequest(request.getMethod(), uri, request.getEntity());
        setHeaders(httpRequest, request.getOptions().getHeaders());
        FailureTrackingResponseListener failureTrackingResponseListener = new FailureTrackingResponseListener(listener);
        long startTime = System.nanoTime();
        performRequestAsync(startTime, nextNode(), httpRequest, ignoreErrorCodes,
                request.getOptions().getWarningsHandler() == null ? warningsHandler : request.getOptions().getWarningsHandler(),
                request.getOptions().getHttpAsyncResponseConsumerFactory(), failureTrackingResponseListener);
    }

    private void performRequestAsync(final long startTime, final NodeTuple<Iterator<Node>> nodeTuple, final HttpRequestBase request,
                                     final Set<Integer> ignoreErrorCodes,
                                     final WarningsHandler thisWarningsHandler,
                                     final HttpAsyncResponseConsumerFactory httpAsyncResponseConsumerFactory,
                                     final FailureTrackingResponseListener listener) {
        final Node node = nodeTuple.nodes.next();
        final HttpAsyncRequestProducer requestProducer = HttpAsyncMethods.create(node.getHost(), request);
        final HttpAsyncResponseConsumer<HttpResponse> asyncResponseConsumer =
            httpAsyncResponseConsumerFactory.createHttpAsyncResponseConsumer();
        final HttpClientContext context = HttpClientContext.create();
        context.setAuthCache(nodeTuple.authCache);
        client.execute(requestProducer, asyncResponseConsumer, context, new FutureCallback<HttpResponse>() {
            @Override
            public void completed(HttpResponse httpResponse) {
                try {
                    RequestLogger.logResponse(logger, request, node.getHost(), httpResponse);
                    int statusCode = httpResponse.getStatusLine().getStatusCode();
                    Response response = new Response(request.getRequestLine(), node.getHost(), httpResponse);
                    if (isSuccessfulResponse(statusCode) || ignoreErrorCodes.contains(response.getStatusLine().getStatusCode())) {
                        onResponse(node);
                        if (thisWarningsHandler.warningsShouldFailRequest(response.getWarnings())) {
                            listener.onDefinitiveFailure(new WarningFailureException(response));
                        } else {
                            listener.onSuccess(response);
                        }
                    } else {
                        ResponseException responseException = new ResponseException(response);
                        if (isRetryStatus(statusCode)) {
                            onFailure(node);
                            retryIfPossible(responseException);
                        } else {
                            onResponse(node);
                            listener.onDefinitiveFailure(responseException);
                        }
                    }
                } catch(Exception e) {
                    listener.onDefinitiveFailure(e);
                }
            }

            @Override
            public void failed(Exception failure) {
                try {
                    RequestLogger.logFailedRequest(logger, request, node, failure);
                    onFailure(node);
                    retryIfPossible(failure);
                } catch(Exception e) {
                    listener.onDefinitiveFailure(e);
                }
            }

            private void retryIfPossible(Exception exception) {
                if (nodeTuple.nodes.hasNext()) {
                    long timeElapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startTime);
                    long timeout = maxRetryTimeoutMillis - timeElapsedMillis;
                    if (timeout <= 0) {
                        IOException retryTimeoutException = new IOException(
                                "request retries exceeded max retry timeout [" + maxRetryTimeoutMillis + "]", exception);
                        listener.onDefinitiveFailure(retryTimeoutException);
                    } else {
                        listener.trackFailure(exception);
                        request.reset();
                        performRequestAsync(startTime, nodeTuple, request, ignoreErrorCodes,
                                thisWarningsHandler, httpAsyncResponseConsumerFactory, listener);
                    }
                } else {
                    listener.onDefinitiveFailure(exception);
                }
            }

            @Override
            public void cancelled() {
                listener.onDefinitiveFailure(new ExecutionException("request was cancelled", null));
            }
        });
    }

    private void setHeaders(HttpRequest httpRequest, Collection<Header> requestHeaders) {
        final Set<String> requestNames = new HashSet<>(requestHeaders.size());
        for (Header requestHeader : requestHeaders) {
            httpRequest.addHeader(requestHeader);
            requestNames.add(requestHeader.getName());
        }
        for (Header defaultHeader : defaultHeaders) {
            if (requestNames.contains(defaultHeader.getName()) == false) {
                httpRequest.addHeader(defaultHeader);
            }
        }
    }

    private NodeTuple<Iterator<Node>> nextNode() throws IOException {
        NodeTuple<List<Node>> nodeTuple = this.nodeTuple;
        Iterable<Node> hosts = selectNodes(nodeTuple, blacklist, lastNodeIndex, nodeSelector);
        return new NodeTuple<>(hosts.iterator(), nodeTuple.authCache);
    }

    static Iterable<Node> selectNodes(NodeTuple<List<Node>> nodeTuple, Map<HttpHost, DeadHostState> blacklist,
                                      AtomicInteger lastNodeIndex, NodeSelector nodeSelector) throws IOException {
        List<Node> livingNodes = new ArrayList<>(Math.max(0, nodeTuple.nodes.size() - blacklist.size()));
        List<DeadNode> deadNodes = new ArrayList<>(blacklist.size());
        for (Node node : nodeTuple.nodes) {
            DeadHostState deadness = blacklist.get(node.getHost());
            if (deadness == null) {
                livingNodes.add(node);
                continue;
            }
            if (deadness.shallBeRetried()) {
                livingNodes.add(node);
                continue;
            }
            deadNodes.add(new DeadNode(node, deadness));
        }

        if (false == livingNodes.isEmpty()) {
            List<Node> selectedLivingNodes = new ArrayList<>(livingNodes);
            nodeSelector.select(selectedLivingNodes);
            if (false == selectedLivingNodes.isEmpty()) {
                Collections.rotate(selectedLivingNodes, lastNodeIndex.getAndIncrement());
                return selectedLivingNodes;
            }
        }

        if (false == deadNodes.isEmpty()) {
            final List<DeadNode> selectedDeadNodes = new ArrayList<>(deadNodes);
            nodeSelector.select(new Iterable<Node>() {
                @Override
                public Iterator<Node> iterator() {
                    return new DeadNodeIteratorAdapter(selectedDeadNodes.iterator());
                }
            });
            if (false == selectedDeadNodes.isEmpty()) {
                return singletonList(Collections.min(selectedDeadNodes).node);
            }
        }
        throw new IOException("NodeSelector [" + nodeSelector + "] rejected all nodes, "
                + "living " + livingNodes + " and dead " + deadNodes);
    }

    private void onResponse(Node node) {
        DeadHostState removedHost = this.blacklist.remove(node.getHost());
        if (logger.isDebugEnabled() && removedHost != null) {
            logger.debug("removed [" + node + "] from blacklist");
        }
    }

    private void onFailure(Node node) {
        while(true) {
            DeadHostState previousDeadHostState =
                blacklist.putIfAbsent(node.getHost(), new DeadHostState(TimeSupplier.DEFAULT));
            if (previousDeadHostState == null) {
                if (logger.isDebugEnabled()) {
                    logger.debug("added [" + node + "] to blacklist");
                }
                break;
            }
            if (blacklist.replace(node.getHost(), previousDeadHostState,
                    new DeadHostState(previousDeadHostState))) {
                if (logger.isDebugEnabled()) {
                    logger.debug("updated [" + node + "] already in blacklist");
                }
                break;
            }
        }
        failureListener.onFailure(node);
    }

    @Override
    public void close() throws IOException {
        client.close();
    }

    private static boolean isSuccessfulResponse(int statusCode) {
        return statusCode < 300;
    }

    private static boolean isRetryStatus(int statusCode) {
        switch(statusCode) {
            case 502:
            case 503:
            case 504:
                return true;
        }
        return false;
    }

    private static Exception addSuppressedException(Exception suppressedException, Exception currentException) {
        if (suppressedException != null) {
            currentException.addSuppressed(suppressedException);
        }
        return currentException;
    }

    private static HttpRequestBase createHttpRequest(String method, URI uri, HttpEntity entity) {
        switch(method.toUpperCase(Locale.ROOT)) {
            case HttpDeleteWithEntity.METHOD_NAME:
                return addRequestBody(new HttpDeleteWithEntity(uri), entity);
            case HttpGetWithEntity.METHOD_NAME:
                return addRequestBody(new HttpGetWithEntity(uri), entity);
            case HttpHead.METHOD_NAME:
                return addRequestBody(new HttpHead(uri), entity);
            case HttpOptions.METHOD_NAME:
                return addRequestBody(new HttpOptions(uri), entity);
            case HttpPatch.METHOD_NAME:
                return addRequestBody(new HttpPatch(uri), entity);
            case HttpPost.METHOD_NAME:
                HttpPost httpPost = new HttpPost(uri);
                addRequestBody(httpPost, entity);
                return httpPost;
            case HttpPut.METHOD_NAME:
                return addRequestBody(new HttpPut(uri), entity);
            case HttpTrace.METHOD_NAME:
                return addRequestBody(new HttpTrace(uri), entity);
            default:
                throw new UnsupportedOperationException("http method not supported: " + method);
        }
    }

    private static HttpRequestBase addRequestBody(HttpRequestBase httpRequest, HttpEntity entity) {
        if (entity != null) {
            if (httpRequest instanceof HttpEntityEnclosingRequestBase) {
                ((HttpEntityEnclosingRequestBase)httpRequest).setEntity(entity);
            } else {
                throw new UnsupportedOperationException(httpRequest.getMethod() + " with body is not supported");
            }
        }
        return httpRequest;
    }

    static URI buildUri(String pathPrefix, String path, Map<String, String> params) {
        Objects.requireNonNull(path, "path must not be null");
        try {
            String fullPath;
            if (pathPrefix != null && pathPrefix.isEmpty() == false) {
                if (pathPrefix.endsWith("/") && path.startsWith("/")) {
                    fullPath = pathPrefix.substring(0, pathPrefix.length() - 1) + path;
                } else if (pathPrefix.endsWith("/") || path.startsWith("/")) {
                    fullPath = pathPrefix + path;
                } else {
                    fullPath = pathPrefix + "/" + path;
                }
            } else {
                fullPath = path;
            }

            URIBuilder uriBuilder = new URIBuilder(fullPath);
            for (Map.Entry<String, String> param : params.entrySet()) {
                uriBuilder.addParameter(param.getKey(), param.getValue());
            }
            return uriBuilder.build();
        } catch(URISyntaxException e) {
            throw new IllegalArgumentException(e.getMessage(), e);
        }
    }

    static class FailureTrackingResponseListener {
        private final ResponseListener responseListener;
        private volatile Exception exception;

        FailureTrackingResponseListener(ResponseListener responseListener) {
            this.responseListener = responseListener;
        }

        void onSuccess(Response response) {
            responseListener.onSuccess(response);
        }

        void onDefinitiveFailure(Exception exception) {
            trackFailure(exception);
            responseListener.onFailure(this.exception);
        }

        void trackFailure(Exception exception) {
            this.exception = addSuppressedException(this.exception, exception);
        }
    }

    static class SyncResponseListener implements ResponseListener {
        private final CountDownLatch latch = new CountDownLatch(1);
        private final AtomicReference<Response> response = new AtomicReference<>();
        private final AtomicReference<Exception> exception = new AtomicReference<>();

        private final long timeout;

        SyncResponseListener(long timeout) {
            assert timeout > 0;
            this.timeout = timeout;
        }

        @Override
        public void onSuccess(Response response) {
            Objects.requireNonNull(response, "response must not be null");
            boolean wasResponseNull = this.response.compareAndSet(null, response);
            if (wasResponseNull == false) {
                throw new IllegalStateException("response is already set");
            }

            latch.countDown();
        }

        @Override
        public void onFailure(Exception exception) {
            Objects.requireNonNull(exception, "exception must not be null");
            boolean wasExceptionNull = this.exception.compareAndSet(null, exception);
            if (wasExceptionNull == false) {
                throw new IllegalStateException("exception is already set");
            }
            latch.countDown();
        }

        Response get() throws IOException {
            try {
                if (latch.await(timeout, TimeUnit.MILLISECONDS) == false) {
                    throw new IOException("listener timeout after waiting for [" + timeout + "] ms");
                }
            } catch (InterruptedException e) {
                throw new RuntimeException("thread waiting for the response was interrupted", e);
            }

            Exception exception = this.exception.get();
            Response response = this.response.get();
            if (exception != null) {
                if (response != null) {
                    IllegalStateException e = new IllegalStateException("response and exception are unexpectedly set at the same time");
                    e.addSuppressed(exception);
                    throw e;
                }
                if (exception instanceof WarningFailureException) {
                    throw new WarningFailureException((WarningFailureException) exception);
                }
                if (exception instanceof ResponseException) {
                    throw new ResponseException((ResponseException) exception);
                }
                if (exception instanceof ConnectTimeoutException) {
                    ConnectTimeoutException e = new ConnectTimeoutException(exception.getMessage());
                    e.initCause(exception);
                    throw e;
                }
                if (exception instanceof SocketTimeoutException) {
                    SocketTimeoutException e = new SocketTimeoutException(exception.getMessage());
                    e.initCause(exception);
                    throw e;
                }
                if (exception instanceof ConnectionClosedException) {
                    ConnectionClosedException e = new ConnectionClosedException(exception.getMessage());
                    e.initCause(exception);
                    throw e;
                }
                if (exception instanceof SSLHandshakeException) {
                    SSLHandshakeException e = new SSLHandshakeException(exception.getMessage());
                    e.initCause(exception);
                    throw e;
                }
                if (exception instanceof ConnectException) {
                    ConnectException e = new ConnectException(exception.getMessage());
                    e.initCause(exception);
                    throw e;
                }
                if (exception instanceof IOException) {
                    throw new IOException(exception.getMessage(), exception);
                }
                if (exception instanceof RuntimeException){
                    throw new RuntimeException(exception.getMessage(), exception);
                }
                throw new RuntimeException("error while performing request", exception);
            }

            if (response == null) {
                throw new IllegalStateException("response not set and no exception caught either");
            }
            return response;
        }
    }

    public static class FailureListener {
        public void onFailure(Node node) {}
    }

    static class NodeTuple<T> {
        final T nodes;
        final AuthCache authCache;

        NodeTuple(final T nodes, final AuthCache authCache) {
            this.nodes = nodes;
            this.authCache = authCache;
        }
    }

    private static class DeadNode implements Comparable<DeadNode> {
        final Node node;
        final DeadHostState deadness;

        DeadNode(Node node, DeadHostState deadness) {
            this.node = node;
            this.deadness = deadness;
        }

        @Override
        public String toString() {
            return node.toString();
        }

        @Override
        public int compareTo(DeadNode rhs) {
            return deadness.compareTo(rhs.deadness);
        }
    }

    private static class DeadNodeIteratorAdapter implements Iterator<Node> {
        private final Iterator<DeadNode> itr;

        private DeadNodeIteratorAdapter(Iterator<DeadNode> itr) {
            this.itr = itr;
        }

        @Override
        public boolean hasNext() {
            return itr.hasNext();
        }

        @Override
        public Node next() {
            return itr.next().node;
        }

        @Override
        public void remove() {
            itr.remove();
        }
    }

    @Deprecated
    private static void addHeaders(Request request, Header... headers) {
        setOptions(request, RequestOptions.DEFAULT.getHttpAsyncResponseConsumerFactory(), headers);
    }

    @Deprecated
    private static void setOptions(Request request, HttpAsyncResponseConsumerFactory httpAsyncResponseConsumerFactory,
            Header... headers) {
        Objects.requireNonNull(headers, "headers cannot be null");
        RequestOptions.Builder options = request.getOptions().toBuilder();
        for (Header header : headers) {
            Objects.requireNonNull(header, "header cannot be null");
            options.addHeader(header.getName(), header.getValue());
        }
        options.setHttpAsyncResponseConsumerFactory(httpAsyncResponseConsumerFactory);
        request.setOptions(options);
    }

    @Deprecated
    private static void addParameters(Request request, Map<String, String> parameters) {
        Objects.requireNonNull(parameters, "parameters cannot be null");
        for (Map.Entry<String, String> entry : parameters.entrySet()) {
            request.addParameter(entry.getKey(), entry.getValue());
        }
    }
}
