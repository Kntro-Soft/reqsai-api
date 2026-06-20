package com.kntro.reqsai.discovery.infrastructure.ai.transcription.streaming.strategy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kntro.reqsai.discovery.application.port.StreamingTranscriptionPort;
import com.kntro.reqsai.discovery.infrastructure.exception.DiscoveryInfrastructureExceptions;
import lombok.extern.slf4j.Slf4j;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;

/**
 * Shared JDK-{@link WebSocket} plumbing for the live STT provider adapters (Deepgram, AssemblyAI,
 * WhisperLive): connecting, forwarding binary audio, accumulating fragmented text frames, and closing.
 * Subclasses only supply the provider specifics — endpoint URI, auth headers, an optional on-connect
 * message, and how to map an incoming JSON frame to a {@link TranscriptEvent}.
 *
 * <p>Uses only the JDK HTTP client (no extra dependency). The provider call paths are implemented to
 * the documented protocols but are <strong>not yet verified against a live service</strong> — that
 * requires real credentials / a running server </p>
 */
@Slf4j
abstract class AbstractWebSocketStreamingAdapter implements StreamingTranscriptionPort {

    /** Shared JSON parser for provider frames. */
    protected static final ObjectMapper JSON = new ObjectMapper();

    private static final long CONNECT_TIMEOUT_SECONDS = 10;

    @Override
    public Session open(Context context, Listener listener) {
        guardConfig();
        HttpClient httpClient = HttpClient.newHttpClient();
        WebSocket socket = connect(httpClient, endpoint(context), new FrameListener(listener));
        onConnected(socket, context);
        return new ProviderSession(socket, httpClient);
    }

    private WebSocket connect(HttpClient httpClient, URI uri, WebSocket.Listener frameListener) {
        WebSocket.Builder builder = httpClient.newWebSocketBuilder();
        applyHeaders(builder);
        try {
            return builder
                    .buildAsync(uri, frameListener)
                    .orTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                    .join();
        } catch (Exception e) {
            httpClient.close();
            log.error("Failed to open {} streaming session: {}", provider(), e.getMessage());
            throw DiscoveryInfrastructureExceptions.transcriptionUnavailable();
        }
    }

    // provider hooks

    /** Provider name for logging. */
    protected abstract String provider();

    /** Streaming endpoint, possibly parameterized by language/sample-rate. */
    protected abstract URI endpoint(Context context);

    /** Add auth (and any other) headers to the handshake. */
    protected abstract void applyHeaders(WebSocket.Builder builder);

    /** Parse one complete JSON frame and emit transcript events through {@code listener}. */
    protected abstract void parseFrame(String json, Listener listener) throws Exception;

    /** Optional initial message after connection. Default: none. */
    protected void onConnected(WebSocket socket, Context context) {
        // no-op by default
    }

    /**
     * Called just before the WebSocket close frame is sent. Providers that need to flush a pending
     * transcript (e.g. AssemblyAI {@code Terminate}) should send their finalization message here
     * and wait briefly for the server's last response before the connection is torn down.
     */
    protected void beforeClose(WebSocket socket) {
        // no-op by default
    }

    /** Throw {@code transcriptionUnavailable} when the adapter is selected without a required config. */
    protected void guardConfig() {
        // no-op by default
    }

    /**
     * Optional audio transformation applied before each binary frame is sent to the provider.
     * Default: pass through unchanged (int16 PCM, which Deepgram and AssemblyAI accept natively).
     * WhisperLive overrides this to convert int16 → float32.
     */
    protected byte[] prepareAudioFrame(byte[] frame) {
        return frame;
    }

    // internals

    private final class ProviderSession implements Session {
        private final WebSocket socket;
        private final HttpClient httpClient;

        ProviderSession(WebSocket socket, HttpClient httpClient) {
            this.socket = socket;
            this.httpClient = httpClient;
        }

        @Override
        public void sendAudio(byte[] frame) {
            socket.sendBinary(ByteBuffer.wrap(prepareAudioFrame(frame)), true);
        }

        @Override
        public void close() {
            try {
                beforeClose(socket);
                socket.sendClose(WebSocket.NORMAL_CLOSURE, "client closed");
            } catch (Exception e) {
                log.debug("Error closing {} stream: {}", provider(), e.getMessage());
            } finally {
                httpClient.close();
            }
        }
    }

    /** Accumulates fragmented text frames and delegates a complete JSON message to {@link #parseFrame}. */
    private final class FrameListener implements WebSocket.Listener {
        private final Listener listener;
        private final StringBuilder buffer = new StringBuilder();

        FrameListener(Listener listener) {
            this.listener = listener;
        }

        @Override
        public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
            buffer.append(data);
            if (last) {
                String message = buffer.toString();
                buffer.setLength(0);
                try {
                    parseFrame(message, listener);
                } catch (Exception e) {
                    log.warn("Failed to parse {} frame: {}", provider(), e.getMessage());
                }
            }
            webSocket.request(1);
            return null;
        }

        @Override
        public void onError(WebSocket webSocket, Throwable error) {
            log.warn("{} streaming error: {}", provider(), error.getMessage());
        }
    }
}
