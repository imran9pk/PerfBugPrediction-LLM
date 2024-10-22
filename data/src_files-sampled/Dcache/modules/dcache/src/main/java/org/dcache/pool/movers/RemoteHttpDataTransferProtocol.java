package org.dcache.pool.movers;

import static com.google.common.collect.Maps.uniqueIndex;
import static diskCacheV111.util.ThirdPartyTransferFailedCacheException.checkThirdPartyTransferSuccessful;
import static dmg.util.Exceptions.getMessageWithCauses;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.dcache.namespace.FileAttribute.CHECKSUM;
import static org.dcache.util.ByteUnit.GiB;
import static org.dcache.util.ByteUnit.MiB;
import static org.dcache.util.Exceptions.genericCheck;
import static org.dcache.util.Exceptions.messageOrClassName;
import static org.dcache.util.Strings.describeSize;
import static org.dcache.util.Strings.toThreeSigFig;
import static org.dcache.util.TimeUtils.describeDuration;

import diskCacheV111.util.CacheException;
import diskCacheV111.util.ThirdPartyTransferFailedCacheException;
import diskCacheV111.vehicles.ProtocolInfo;
import diskCacheV111.vehicles.RemoteHttpDataTransferProtocolInfo;
import dmg.cells.nucleus.CellEndpoint;
import java.io.IOException;
import java.net.ConnectException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.nio.channels.Channels;
import java.nio.file.OpenOption;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import javax.annotation.concurrent.GuardedBy;
import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpInetConnection;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.ProtocolException;
import org.apache.http.StatusLine;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.RedirectStrategy;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpHead;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.DefaultRedirectStrategy;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.ConnectionShutdownException;
import org.apache.http.protocol.HttpContext;
import org.apache.http.protocol.HttpCoreContext;
import org.apache.http.protocol.HttpRequestExecutor;
import org.dcache.auth.OpenIdCredentialRefreshable;
import org.dcache.pool.repository.RepositoryChannel;
import org.dcache.util.Checksum;
import org.dcache.util.ChecksumType;
import org.dcache.util.Checksums;
import org.dcache.util.Version;
import org.dcache.vehicles.FileAttributes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RemoteHttpDataTransferProtocol implements MoverProtocol,
      ChecksumMover {

    private enum HeaderFlags {
        NO_AUTHORIZATION_HEADER
    }

    private static final Set<HeaderFlags> REDIRECTED_REQUEST
          = EnumSet.of(HeaderFlags.NO_AUTHORIZATION_HEADER);

    private static final Set<HeaderFlags> INITIAL_REQUEST
          = EnumSet.noneOf(HeaderFlags.class);

    private static final Logger LOGGER =
          LoggerFactory.getLogger(RemoteHttpDataTransferProtocol.class);

    private static final int CONNECTION_TIMEOUT = (int) TimeUnit.MINUTES.toMillis(1);

    private static final int SOCKET_TIMEOUT = (int) TimeUnit.MINUTES.toMillis(1);

    private static final long POST_PROCESSING_OFFSET = 60_000;

    private static final double POST_PROCESSING_BANDWIDTH = MiB.toBytes(10) / 1_000.0;

    private static final long DELAY_BETWEEN_REQUESTS = 5_000;

    private static final long GET_RETRY_DURATION = maxRetryDuration(GiB.toBytes(2L));
    private static final String GET_RETRY_DURATION_DESCRIPTION = describeDuration(
          GET_RETRY_DURATION, MILLISECONDS);

    private static final int MAX_REDIRECTIONS = 20;

    private static final String AUTH_BEARER = "Bearer ";

    private static final String WANT_DIGEST_VALUE = Checksums.buildGenericWantDigest();

    private static final Duration EXPECT_100_TIMEOUT = Duration.of(5, ChronoUnit.MINUTES);

    private static final RedirectStrategy DROP_AUTHORIZATION_HEADER = new DefaultRedirectStrategy() {

        @Override
        public HttpUriRequest getRedirect(final HttpRequest request,
              final HttpResponse response, final HttpContext context)
              throws ProtocolException {
            HttpUriRequest redirect = super.getRedirect(request, response, context);

            if (!redirect.headerIterator().hasNext()) {
                redirect.setHeaders(request.getAllHeaders());
            }

            redirect.removeHeaders("Authorization");
            return redirect;
        }
    };

    protected static final String USER_AGENT = "dCache/" +
          Version.of(RemoteHttpDataTransferProtocol.class).getVersion();

    private volatile MoverChannel<RemoteHttpDataTransferProtocolInfo> _channel;
    private Consumer<Checksum> _integrityChecker;

    private CloseableHttpClient _client;

    @GuardedBy("this")
    private HttpClientContext _context;

    private Long _expectedTransferSize;

    public RemoteHttpDataTransferProtocol(CellEndpoint cell) {
        }

    private static void checkThat(boolean isOk, String message) throws CacheException {
        genericCheck(isOk, CacheException::new, message);
    }

    @Override
    public void acceptIntegrityChecker(Consumer<Checksum> integrityChecker) {
        _integrityChecker = integrityChecker;
    }

    @Override
    public void runIO(FileAttributes attributes, RepositoryChannel channel,
          ProtocolInfo genericInfo, Set<? extends OpenOption> access)
          throws CacheException, IOException, InterruptedException {
        LOGGER.debug("info={}, attributes={},  access={}", genericInfo,
              attributes, access);
        RemoteHttpDataTransferProtocolInfo info =
              (RemoteHttpDataTransferProtocolInfo) genericInfo;
        _channel = new MoverChannel<>(access, attributes, info, channel);

        channel.optionallyAs(ChecksumChannel.class).ifPresent(c -> {
            info.getDesiredChecksum().ifPresent(t -> {
                try {
                    c.addType(t);
                } catch (IOException e) {
                    LOGGER.warn("Unable to calculate checksum {}: {}",
                          t, messageOrClassName(e));
                }
            });
        });

        _client = createHttpClient();
        try {
            if (access.contains(StandardOpenOption.WRITE)) {
                receiveFile(info);
            } else {
                checkThat(!info.isVerificationRequired() || attributes.isDefined(CHECKSUM),
                      "checksum verification failed: file has no checksum");
                sendAndCheckFile(info);
            }
        } finally {
            _client.close();
        }
    }

    protected CloseableHttpClient createHttpClient() throws CacheException {
        return customise(HttpClients.custom()).build();
    }

    protected HttpClientBuilder customise(HttpClientBuilder builder) throws CacheException {
        return builder
              .setUserAgent(USER_AGENT)
              .setRequestExecutor(new HttpRequestExecutor((int) EXPECT_100_TIMEOUT.toMillis()))
              .setRedirectStrategy(DROP_AUTHORIZATION_HEADER);
    }

    private synchronized HttpClientContext storeContext(HttpClientContext context) {
        _context = context;
        return context;
    }

    private synchronized HttpClientContext getContext() {
        return _context;
    }

    private void receiveFile(final RemoteHttpDataTransferProtocolInfo info)
          throws ThirdPartyTransferFailedCacheException {
        Set<Checksum> checksums;

        long deadline = System.currentTimeMillis() + GET_RETRY_DURATION;

        HttpClientContext context = storeContext(new HttpClientContext());
        try {
            try (CloseableHttpResponse response = doGet(info, context, deadline)) {
                String rfc3230 = headerValue(response, "Digest");
                checksums = Checksums.decodeRfc3230(rfc3230);
                checksums.forEach(_integrityChecker);

                HttpEntity entity = response.getEntity();
                if (entity == null) {
                    throw new ThirdPartyTransferFailedCacheException(
                          "GET response contains no content");
                }

                long length = entity.getContentLength();
                if (length > 0) {
                    _channel.truncate(length);
                }
                if (response.getStatusLine() != null
                      && response.getStatusLine().getStatusCode() < 300 && length > -1) {
                    _expectedTransferSize = length;
                }
                entity.writeTo(Channels.newOutputStream(_channel));
            } catch (SocketTimeoutException e) {
                String message = "socket timeout on GET (received "
                      + describeSize(_channel.getBytesTransferred()) + " of data; "
                      + describeSize(e.bytesTransferred) + " pending)";
                if (e.getMessage() != null) {
                    message += ": " + e.getMessage();
                }
                throw new ThirdPartyTransferFailedCacheException(message, e);
            } catch (IOException e) {
                throw new ThirdPartyTransferFailedCacheException(messageOrClassName(e), e);
            } catch (InterruptedException e) {
                throw new ThirdPartyTransferFailedCacheException("pool is shutting down", e);
            }
        } catch (ThirdPartyTransferFailedCacheException e) {
            List<URI> redirections = context.getRedirectLocations();
            if (redirections != null && !redirections.isEmpty()) {
                StringBuilder message = new StringBuilder(e.getMessage());
                message.append("; redirects ").append(redirections);
                throw new ThirdPartyTransferFailedCacheException(message.toString(), e.getCause());
            } else {
                throw e;
            }
        }

        if (checksums.isEmpty() && info.isVerificationRequired()) {
            HttpHead head = buildHeadRequest(info, deadline);
            head.addHeader("Want-Digest", WANT_DIGEST_VALUE);

            try {
                try (CloseableHttpResponse response = _client.execute(head)) {

                    String rfc3230 = headerValue(response, "Digest");

                    checkThirdPartyTransferSuccessful(rfc3230 != null,
                          "no checksums in HEAD response");

                    checksums = Checksums.decodeRfc3230(rfc3230);

                    checkThirdPartyTransferSuccessful(!checksums.isEmpty(),
                          "no useful checksums in HEAD response: %s", rfc3230);

                    checksums.forEach(_integrityChecker);
                }
            } catch (IOException e) {
                throw new ThirdPartyTransferFailedCacheException(
                      "HEAD request failed: " + messageOrClassName(e), e);
            }
        }
    }

    private Optional<InetSocketAddress> remoteAddress() {
        HttpContext context = getContext();
        if (context == null) {
            LOGGER.debug("No HttpContext value");
            return Optional.empty();
        }

        Object conn = context.getAttribute(HttpCoreContext.HTTP_CONNECTION);
        if (conn == null) {
            LOGGER.debug("HTTP_CONNECTION is null");
            return Optional.empty();
        }

        if (!(conn instanceof HttpInetConnection)) {
            throw new RuntimeException("HTTP_CONNECTION has unexpected type: "
                  + conn.getClass().getCanonicalName());
        }

        HttpInetConnection inetConn = (HttpInetConnection) conn;
        if (!inetConn.isOpen()) {
            LOGGER.debug("HttpConnection is no longer open");
            return Optional.empty();
        }

        try {
            InetAddress addr = inetConn.getRemoteAddress();
            if (addr == null) {
                LOGGER.debug("HttpInetConnection is not connected.");
                return Optional.empty();
            }

            int port = inetConn.getRemotePort();
            InetSocketAddress sockAddr = new InetSocketAddress(addr, port);
            return Optional.of(sockAddr);
        } catch (ConnectionShutdownException e) {
            LOGGER.warn("HTTP_CONNECTION is unexpectedly unconnected");
            return Optional.empty();
        }
    }

    private HttpGet buildGetRequest(RemoteHttpDataTransferProtocolInfo info,
          long deadline) {
        HttpGet get = new HttpGet(info.getUri());
        get.addHeader("Want-Digest", WANT_DIGEST_VALUE);
        addHeadersToRequest(info, get, INITIAL_REQUEST);

        int timeLeftBeforeDeadline = (int) (deadline - System.currentTimeMillis());
        int socketTimeout = Math.max(SOCKET_TIMEOUT, timeLeftBeforeDeadline);

        get.setConfig(RequestConfig.custom()
              .setConnectTimeout(CONNECTION_TIMEOUT)
              .setSocketTimeout(socketTimeout)
              .build());
        return get;
    }

    private CloseableHttpResponse doGet(final RemoteHttpDataTransferProtocolInfo info,
          HttpContext context, long deadline) throws IOException,
          ThirdPartyTransferFailedCacheException, InterruptedException {
        HttpGet get = buildGetRequest(info, deadline);
        CloseableHttpResponse response = _client.execute(get, context);

        boolean isSuccessful = false;
        try {
            while (shouldRetry(response) && System.currentTimeMillis() < deadline) {
                Thread.sleep(DELAY_BETWEEN_REQUESTS);

                response.close();
                get = buildGetRequest(info, deadline);
                response = _client.execute(get);
            }

            int statusCode = response.getStatusLine().getStatusCode();
            String reason = response.getStatusLine().getReasonPhrase();

            checkThirdPartyTransferSuccessful(!shouldRetry(response),
                  "remote server not ready for GET request after %s: %d %s",
                  GET_RETRY_DURATION_DESCRIPTION, statusCode, reason);

            checkThirdPartyTransferSuccessful(statusCode == HttpStatus.SC_OK,
                  "rejected GET: %d %s", statusCode, reason);

            isSuccessful = true;
        } finally {
            if (!isSuccessful) {
                response.close();
            }
        }

        return response;
    }


    private static boolean shouldRetry(HttpResponse response) {
        return response.getStatusLine().getStatusCode() == HttpStatus.SC_ACCEPTED;
    }

    private void sendAndCheckFile(RemoteHttpDataTransferProtocolInfo info)
          throws ThirdPartyTransferFailedCacheException {
        sendFile(info);

        try {
            verifyRemoteFile(info);
        } catch (ThirdPartyTransferFailedCacheException e) {
            deleteRemoteFile(e.getMessage(), info);
            throw new ThirdPartyTransferFailedCacheException("verification " +
                  "failed: " + e.getMessage());
        }
    }

    private void sendFile(RemoteHttpDataTransferProtocolInfo info)
          throws ThirdPartyTransferFailedCacheException {
        URI location = info.getUri();
        List<URI> redirections = null;
        HttpClientContext context = storeContext(new HttpClientContext());

        try {
            for (int redirectionCount = 0; redirectionCount < MAX_REDIRECTIONS;
                  redirectionCount++) {
                HttpPut put = buildPutRequest(info, location,
                      redirectionCount > 0 ? REDIRECTED_REQUEST : INITIAL_REQUEST);

                try (CloseableHttpResponse response = _client.execute(put, context)) {
                    StatusLine status = response.getStatusLine();
                    switch (status.getStatusCode()) {
                        case 200: case 201: case 204: case 205: return;

                        case 300: case 301: case 302: case 307: case 308: String locationHeader = response.getFirstHeader("Location").getValue();
                            if (locationHeader == null) {
                                throw new ThirdPartyTransferFailedCacheException(
                                      "missing Location in PUT response "
                                            + status.getStatusCode() + " "
                                            + status.getReasonPhrase());
                            }

                            try {
                                location = URI.create(locationHeader);
                            } catch (IllegalArgumentException e) {
                                throw new ThirdPartyTransferFailedCacheException(
                                      "invalid Location " +
                                            locationHeader + " in PUT response "
                                            + status.getStatusCode() + " "
                                            + status.getReasonPhrase()
                                            + ": " + e.getMessage());
                            }
                            if (redirections == null) {
                                redirections = new ArrayList<>();
                            }
                            redirections.add(location);
                            break;

                        default:
                            throw new ThirdPartyTransferFailedCacheException("rejected PUT: "
                                  + status.getStatusCode() + " " + status.getReasonPhrase());
                    }
                } catch (ConnectException e) {
                    throw new ThirdPartyTransferFailedCacheException("connection failed for PUT: "
                          + messageOrClassName(e), e);
                } catch (ClientProtocolException e) {
                    Throwable t = e.getMessage() == null && e.getCause() != null ? e.getCause() : e;
                    StringBuilder message = new StringBuilder("failed to send PUT request: ")
                          .append(getMessageWithCauses(t));
                    if (_channel.getBytesTransferred() != 0) {
                        message.append("; after sending ")
                              .append(describeSize(_channel.getBytesTransferred()));
                        try {
                            String percent = toThreeSigFig(
                                  100 * _channel.getBytesTransferred() / (double) _channel.size(),
                                  1000);
                            message.append(" (").append(percent).append("%)");
                        } catch (IOException io) {
                            LOGGER.warn("failed to discover file size: {}", messageOrClassName(io));
                        }
                    }
                    throw new ThirdPartyTransferFailedCacheException(message.toString(), e);
                } catch (IOException e) {
                    throw new ThirdPartyTransferFailedCacheException(
                          "problem sending data: " + messageOrClassName(e), e);
                }
            }
        } catch (ThirdPartyTransferFailedCacheException e) {
            if (redirections != null) {
                throw new ThirdPartyTransferFailedCacheException(e.getMessage()
                      + "; redirections " + redirections, e.getCause());
            } else {
                throw e;
            }
        }

        throw new ThirdPartyTransferFailedCacheException("exceeded maximum"
              + " number of redirections: " + redirections);
    }

    private HttpPut buildPutRequest(RemoteHttpDataTransferProtocolInfo info,
          URI location, Set<HeaderFlags> flags) {
        HttpPut put = new HttpPut(location);
        put.setConfig(RequestConfig.custom()
              .setConnectTimeout(CONNECTION_TIMEOUT)
              .setExpectContinueEnabled(true)
              .setSocketTimeout(0)
              .build());
        addHeadersToRequest(info, put, flags);
        put.setEntity(new RepositoryChannelEntity(_channel));

        long size = put.getEntity().getContentLength();
        if (size != -1) {
            _expectedTransferSize = size;
        }

        return put;
    }

    private static long maxRetryDuration(long fileSize) {
        return POST_PROCESSING_OFFSET + (long) (fileSize / POST_PROCESSING_BANDWIDTH);
    }

    private void verifyRemoteFile(RemoteHttpDataTransferProtocolInfo info)
          throws ThirdPartyTransferFailedCacheException {
        FileAttributes attributes = _channel.getFileAttributes();
        boolean isFirstAttempt = true;

        long t_max = maxRetryDuration(attributes.getSize());
        long deadline = System.currentTimeMillis() + t_max;

        try {
            while (System.currentTimeMillis() < deadline) {
                long sleepFor = Math.min(deadline - System.currentTimeMillis(),
                      DELAY_BETWEEN_REQUESTS);
                if (!isFirstAttempt && sleepFor > 0) {
                    Thread.sleep(sleepFor);
                }
                isFirstAttempt = false;

                HttpClientContext context = storeContext(new HttpClientContext());
                HttpHead head = buildHeadRequest(info, deadline);
                buildWantDigest().ifPresent(v -> head.addHeader("Want-Digest", v));

                try {
                    try (CloseableHttpResponse response = _client.execute(head, context)) {
                        StatusLine status = response.getStatusLine();

                        if (status.getStatusCode() >= 300) {
                            checkThirdPartyTransferSuccessful(!info.isVerificationRequired(),
                                  "rejected HEAD: %d %s", status.getStatusCode(),
                                  status.getReasonPhrase());
                            return;
                        }

                        if (shouldRetry(response)) {
                            continue;
                        }

                        OptionalLong contentLengthHeader = contentLength(response);

                        if (contentLengthHeader.isPresent()) {
                            long contentLength = contentLengthHeader.getAsLong();
                            long fileSize = attributes.getSize();
                            checkThirdPartyTransferSuccessful(contentLength == fileSize,
                                  "HEAD Content-Length (%d) does not match file size (%d)",
                                  contentLength, fileSize);
                        } else {
                            LOGGER.debug("HEAD response did not contain Content-Length");
                        }

                        String rfc3230 = headerValue(response, "Digest");
                        checkChecksums(info, rfc3230, attributes.getChecksumsIfPresent());
                        return;
                    } catch (IOException e) {
                        throw new ThirdPartyTransferFailedCacheException("failed to " +
                              "connect to server: " + e.toString(), e);
                    }
                } catch (ThirdPartyTransferFailedCacheException e) {
                    List<URI> redirections = context.getRedirectLocations();
                    if (redirections != null && !redirections.isEmpty()) {
                        throw new ThirdPartyTransferFailedCacheException(
                              e.getMessage() + "; redirections " + redirections, e.getCause());
                    } else {
                        throw e;
                    }

                }
            }
        } catch (InterruptedException e) {
            throw new ThirdPartyTransferFailedCacheException("pool is shutting down", e);
        }

        throw new ThirdPartyTransferFailedCacheException("remote server failed " +
              "to provide length after " + describeDuration(GET_RETRY_DURATION, MILLISECONDS));
    }

    private Optional<String> buildWantDigest() {
        Optional<Set<Checksum>> checksums = _channel.getFileAttributes().getChecksumsIfPresent();

        return checksums.map(Checksums::asWantDigest)
              .filter(Optional::isPresent)
              .map(Optional::get);
    }

    private HttpHead buildHeadRequest(RemoteHttpDataTransferProtocolInfo info,
          long deadline) {
        HttpHead head = new HttpHead(info.getUri());

        int timeLeftBeforeDeadline = (int) (deadline - System.currentTimeMillis());
        int socketTimeout = Math.max(SOCKET_TIMEOUT, timeLeftBeforeDeadline);

        head.setConfig(RequestConfig.custom()
              .setConnectTimeout(CONNECTION_TIMEOUT)
              .setSocketTimeout(socketTimeout)

              .setContentCompressionEnabled(false)
              .build());
        addHeadersToRequest(info, head, INITIAL_REQUEST);
        return head;
    }

    private void checkChecksums(RemoteHttpDataTransferProtocolInfo info,
          String rfc3230, Optional<Set<Checksum>> knownChecksums)
          throws ThirdPartyTransferFailedCacheException {
        Map<ChecksumType, Checksum> checksums =
              uniqueIndex(Checksums.decodeRfc3230(rfc3230), Checksum::getType);

        boolean verified = false;
        if (knownChecksums.isPresent()) {
            for (Checksum ourChecksum : knownChecksums.get()) {
                ChecksumType type = ourChecksum.getType();

                if (checksums.containsKey(type)) {
                    checkChecksumEqual(ourChecksum, checksums.get(type));
                    verified = true;
                }
            }
        }

        if (info.isVerificationRequired() && !verified) {
            throw new ThirdPartyTransferFailedCacheException(
                  "no useful checksum in HEAD response: " +
                        (rfc3230 == null ? "(none sent)" : rfc3230));
        }
    }

    private static String headerValue(HttpResponse response, String headerName) {
        Header header = response.getFirstHeader(headerName);
        return header != null ? header.getValue() : null;
    }

    private static OptionalLong contentLength(HttpResponse response)
          throws ThirdPartyTransferFailedCacheException {
        Header header = response.getLastHeader("Content-Length");

        if (header == null) {
            return OptionalLong.empty();
        }

        try {
            return OptionalLong.of(Long.parseLong(header.getValue()));
        } catch (NumberFormatException e) {
            throw new ThirdPartyTransferFailedCacheException(
                  "server sent malformed Content-Length header", e);
        }
    }

    private static void checkChecksumEqual(Checksum expected, Checksum actual)
          throws ThirdPartyTransferFailedCacheException {
        if (expected.getType() != actual.getType()) {
            throw new RuntimeException("internal error: checksum comparison " +
                  "between different types (" + expected.getType() + " != " +
                  actual.getType());
        }

        if (!expected.equals(actual)) {
            throw new ThirdPartyTransferFailedCacheException(expected.getType().getName() + " " +
                  actual.getValue() + " != " + expected.getValue());
        }
    }

    private void deleteRemoteFile(String why, RemoteHttpDataTransferProtocolInfo info)
          throws ThirdPartyTransferFailedCacheException {
        HttpDelete delete = buildDeleteRequest(info);

        try (CloseableHttpResponse response = _client.execute(delete)) {
            StatusLine status = response.getStatusLine();

            if (status.getStatusCode() >= 300) {
                throw new ThirdPartyTransferFailedCacheException("rejected DELETE: "
                      + status.getStatusCode() + " " + status.getReasonPhrase());
            }
        } catch (CacheException e) {
            throw new ThirdPartyTransferFailedCacheException("delete of " +
                  "remote file (triggered by " + why + ") failed: " + e.getMessage());
        } catch (IOException e) {
            throw new ThirdPartyTransferFailedCacheException("delete of " +
                  "remote file (triggered by " + why + ") failed: " + e.toString());
        }
    }

    private HttpDelete buildDeleteRequest(RemoteHttpDataTransferProtocolInfo info) {
        HttpDelete delete = new HttpDelete(info.getUri());
        delete.setConfig(RequestConfig.custom()
              .setConnectTimeout(CONNECTION_TIMEOUT)
              .setSocketTimeout(SOCKET_TIMEOUT)
              .build());
        addHeadersToRequest(info, delete, INITIAL_REQUEST);

        return delete;
    }

    private void addHeadersToRequest(RemoteHttpDataTransferProtocolInfo info,
          HttpRequest request,
          Set<HeaderFlags> flags) {
        boolean dropAuthorizationHeader = flags.contains(HeaderFlags.NO_AUTHORIZATION_HEADER);

        info.getHeaders().forEach(request::addHeader);

        if (info.hasTokenCredential() && !dropAuthorizationHeader) {
            request.addHeader("Authorization",
                  AUTH_BEARER +
                        new OpenIdCredentialRefreshable(info.getTokenCredential(),
                              _client).getBearerToken());
        }

        if (dropAuthorizationHeader) {
            request.removeHeaders("Authorization");
        }
    }

    @Override
    public long getLastTransferred() {
        MoverChannel<RemoteHttpDataTransferProtocolInfo> channel = _channel;
        return channel == null ? System.currentTimeMillis() : channel.getLastTransferred();
    }

    @Override
    public long getBytesTransferred() {
        MoverChannel<RemoteHttpDataTransferProtocolInfo> channel = _channel;
        return channel == null ? 0 : channel.getBytesTransferred();
    }

    @Override
    public long getTransferTime() {
        MoverChannel<RemoteHttpDataTransferProtocolInfo> channel = _channel;
        return channel == null ? 0 : channel.getTransferTime();
    }

    @Override
    public List<InetSocketAddress> remoteConnections() {
        return remoteAddress().stream().collect(Collectors.toList());
    }

    @Override
    public Long getBytesExpected() {
        return _expectedTransferSize;
    }
}
