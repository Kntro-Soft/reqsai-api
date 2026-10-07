package com.kntro.reqsai.discovery.infrastructure.ai.transcription.streaming.strategy;

import java.net.URI;
import java.net.http.WebSocket;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * Opens the provider WebSocket connections of one live stream: the first one and every reconnect.
 * The production implementation is {@link JdkProviderConnector}; tests substitute a fake socket so the
 * keepalive and reconnect paths run without a network.
 */
interface ProviderConnector {

    /** Starts the opening handshake; the future completes with the connected socket or the failure. */
    CompletableFuture<WebSocket> connect(URI uri, WebSocket.Listener listener);

    /** Releases the underlying client. Non-blocking, safe from any thread, idempotent. */
    void close();

    /** Creates the connector of one stream; {@code headers} adds the provider's handshake headers. */
    @FunctionalInterface
    interface Factory {
        ProviderConnector create(Consumer<WebSocket.Builder> headers);
    }
}
