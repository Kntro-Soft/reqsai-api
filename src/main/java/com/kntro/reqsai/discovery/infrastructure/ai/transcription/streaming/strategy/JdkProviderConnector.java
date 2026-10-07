package com.kntro.reqsai.discovery.infrastructure.ai.transcription.streaming.strategy;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * {@link ProviderConnector} on the JDK HTTP client (no extra dependency). One {@link HttpClient} per
 * live stream, reused across its reconnects.
 */
final class JdkProviderConnector implements ProviderConnector {

    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final Consumer<WebSocket.Builder> headers;

    JdkProviderConnector(Consumer<WebSocket.Builder> headers) {
        this.headers = headers;
    }

    @Override
    public CompletableFuture<WebSocket> connect(URI uri, WebSocket.Listener listener) {
        WebSocket.Builder builder = httpClient.newWebSocketBuilder();
        headers.accept(builder);
        return builder.buildAsync(uri, listener);
    }

    /**
     * {@code shutdownNow}, not {@code close}: {@code HttpClient.close()} blocks until every exchange has
     * ended, which can deadlock when called from one of the client's own threads (a provider callback).
     * The stream has already closed or aborted its sockets by the time it calls this.
     */
    @Override
    public void close() {
        httpClient.shutdownNow();
    }
}
