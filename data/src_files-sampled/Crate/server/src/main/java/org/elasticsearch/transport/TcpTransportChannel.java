package org.elasticsearch.transport;

import org.elasticsearch.Version;
import org.elasticsearch.common.breaker.CircuitBreaker;
import org.elasticsearch.indices.breaker.CircuitBreakerService;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;

public final class TcpTransportChannel implements TransportChannel {

    private final OutboundHandler outboundHandler;
    private final TcpChannel channel;
    private final String action;
    private final long requestId;
    private final Version version;
    private final CircuitBreakerService breakerService;
    private final long reservedBytes;
    private final AtomicBoolean released = new AtomicBoolean();

    TcpTransportChannel(OutboundHandler outboundHandler,
                        TcpChannel channel,
                        String action,
                        long requestId,
                        Version version,
                        CircuitBreakerService breakerService,
                        long reservedBytes,
                        boolean compressResponse) {
        this.version = version;
        this.channel = channel;
        this.outboundHandler = outboundHandler;
        this.action = action;
        this.requestId = requestId;
        this.breakerService = breakerService;
        this.reservedBytes = reservedBytes;
    }

    @Override
    public String getProfileName() {
        return channel.getProfile();
    }

    @Override
    public void sendResponse(TransportResponse response) throws IOException {
        sendResponse(response, TransportResponseOptions.EMPTY);
    }

    @Override
    public void sendResponse(TransportResponse response, TransportResponseOptions options) throws IOException {
        try {
            outboundHandler.sendResponse(version, channel, requestId, action, response, options.compress(), false);
        } finally {
            release(false);
        }
    }

    @Override
    public void sendResponse(Exception exception) throws IOException {
        try {
            outboundHandler.sendErrorResponse(version, channel, requestId, action, exception);
        } finally {
            release(true);
        }
    }

    private Exception releaseBy;

    private void release(boolean isExceptionResponse) {
        if (released.compareAndSet(false, true)) {
            breakerService.getBreaker(CircuitBreaker.IN_FLIGHT_REQUESTS).addWithoutBreaking(-reservedBytes);
        } else if (isExceptionResponse == false) {
            throw new IllegalStateException("reserved bytes are already released", releaseBy);
        }
    }

    @Override
    public String getChannelType() {
        return "transport";
    }

    @Override
    public Version getVersion() {
        return version;
    }

    public TcpChannel getChannel() {
        return channel;
    }

}

