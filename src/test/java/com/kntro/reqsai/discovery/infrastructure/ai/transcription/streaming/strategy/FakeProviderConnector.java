package com.kntro.reqsai.discovery.infrastructure.ai.transcription.streaming.strategy;

import java.io.IOException;
import java.net.URI;
import java.net.http.WebSocket;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * {@link ProviderConnector} that hands out {@link FakeProviderSocket}s instead of opening a network
 * connection. Connections succeed immediately unless the test asked the next ones to fail.
 */
final class FakeProviderConnector implements ProviderConnector {

    private final List<FakeProviderSocket> sockets = new ArrayList<>();
    private int connectAttempts;
    private int failuresToSimulate;
    private boolean closed;

    /** Connector factory for {@link StreamResilience} that always returns this connector. */
    ProviderConnector.Factory factory() {
        return headers -> this;
    }

    /** The next {@code count} connection attempts fail, as if the provider were unreachable. */
    void failNextConnects(int count) {
        this.failuresToSimulate = count;
    }

    @Override
    public CompletableFuture<WebSocket> connect(URI uri, WebSocket.Listener listener) {
        connectAttempts++;
        if (failuresToSimulate > 0) {
            failuresToSimulate--;
            return CompletableFuture.failedFuture(new IOException("connection refused"));
        }
        FakeProviderSocket socket = new FakeProviderSocket(listener);
        sockets.add(socket);
        listener.onOpen(socket);
        return CompletableFuture.completedFuture(socket);
    }

    @Override
    public void close() {
        closed = true;
    }

    FakeProviderSocket socket(int index) {
        return sockets.get(index);
    }

    FakeProviderSocket latest() {
        return sockets.getLast();
    }

    int connections() {
        return sockets.size();
    }

    int connectAttempts() {
        return connectAttempts;
    }

    boolean closed() {
        return closed;
    }
}
