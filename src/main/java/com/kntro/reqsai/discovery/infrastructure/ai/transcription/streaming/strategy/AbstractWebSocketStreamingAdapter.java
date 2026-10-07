package com.kntro.reqsai.discovery.infrastructure.ai.transcription.streaming.strategy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kntro.reqsai.discovery.application.port.StreamingTranscriptionPort;
import com.kntro.reqsai.discovery.infrastructure.exception.DiscoveryInfrastructureExceptions;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;

import java.net.URI;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Shared JDK-{@link WebSocket} plumbing for the live STT provider adapters (Deepgram, AssemblyAI,
 * WhisperLive): connecting, forwarding binary audio, accumulating fragmented text frames, keeping the
 * provider stream alive, and closing. Subclasses only supply the provider specifics — endpoint URI, auth
 * headers, optional handshake and keepalive messages, and how to map an incoming JSON frame to a
 * {@link TranscriptEvent}.
 *
 * <h2>Resilience</h2>
 * A provider may drop the stream mid-session — Deepgram closes it after 10 s without audio, which a long
 * JVM pause is enough to trigger. Each {@link Session} therefore:
 * <ul>
 *   <li>sends the provider's keepalive frame every {@link StreamResilience#keepAliveInterval()} while the
 *       socket is idle;</li>
 *   <li>sends one frame at a time through a single queue — the JDK socket fails a send issued while
 *       another is pending — and treats a failed send as a broken stream;</li>
 *   <li>on an unexpected provider close, error or failed send, reconnects with a bounded exponential
 *       backoff, buffering at most {@link StreamResilience#maxBufferedAudioBytes()} of audio meanwhile
 *       (oldest dropped first) and replaying it on the new connection;</li>
 *   <li>shifts each new connection's transcript offsets by the audio that preceded it, so segments keep
 *       a continuous timeline;</li>
 *   <li>gives up after {@link StreamResilience#maxReconnectAttempts()} consecutive failed attempts and
 *       reports it through {@link Listener#onStreamLost}, so the client channel is closed instead of
 *       silently discarding audio. The attempt budget refills once a connection delivers a transcript.</li>
 * </ul>
 */
@Slf4j
abstract class AbstractWebSocketStreamingAdapter implements StreamingTranscriptionPort {

    /** Shared JSON parser for provider frames. */
    protected static final ObjectMapper JSON = new ObjectMapper();

    /** Client audio is 16 kHz, 16-bit, mono PCM: 32 bytes per millisecond. */
    static final int AUDIO_BYTES_PER_MS = 32;

    /** How long {@link Session#close()} lets queued audio reach the provider before finalizing. */
    private static final Duration DRAIN_TIMEOUT = Duration.ofSeconds(2);

    /** How long {@link Session#close()} waits for each step of the provider's close handshake. */
    private static final Duration CLOSE_TIMEOUT = Duration.ofSeconds(3);

    private final StreamResilience resilience;

    protected AbstractWebSocketStreamingAdapter() {
        this(StreamResilience.defaults());
    }

    protected AbstractWebSocketStreamingAdapter(StreamResilience resilience) {
        this.resilience = resilience;
    }

    @Override
    public Session open(Context context, Listener listener) {
        guardConfig();
        ProviderStream stream = new ProviderStream(context, listener, endpoint(context),
                resilience.connectors().create(this::applyHeaders));
        stream.connect();
        return stream;
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

    /**
     * Text message sent first on every connection, before any audio (also after a reconnect).
     * Default: none.
     */
    protected @Nullable String handshakeMessage(Context context) {
        return null;
    }

    /**
     * Text frame that keeps an idle provider stream open, or {@code null} when the provider needs none.
     * Sent every {@link StreamResilience#keepAliveInterval()} while nothing else is being sent.
     */
    protected @Nullable String keepAliveMessage() {
        return null;
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

    private enum State { CONNECTING, OPEN, RECONNECTING, CLOSING, CLOSED, LOST }

    /** One queued outgoing frame: audio (with its byte position in the client stream) or text. */
    private record Outgoing(byte @Nullable [] audio, @Nullable String text, long position) {

        static Outgoing audio(byte[] frame, long position) {
            return new Outgoing(frame, null, position);
        }

        static Outgoing text(String text) {
            return new Outgoing(null, text, -1);
        }

        boolean isAudio() {
            return audio != null;
        }

        int audioBytes() {
            return audio != null ? audio.length : 0;
        }
    }

    /**
     * One logical live stream over one or more provider connections. All state is guarded by
     * {@code lock}; the listener callbacks ({@link Listener#onStreamLost}) and the connector release run
     * after the lock is released.
     */
    private final class ProviderStream implements Session {

        private final Context context;
        private final Listener listener;
        private final URI uri;
        private final ProviderConnector connector;

        private final Object lock = new Object();
        private final Deque<Outgoing> queue = new ArrayDeque<>();
        private State state = State.CONNECTING;
        private @Nullable Connection current;
        private @Nullable Outgoing inFlight;
        private @Nullable Connection inFlightOn;
        private int queuedAudioBytes;
        private long receivedAudioBytes;
        private long droppedAudioBytes;
        private int connectionCount;
        private int failedAttempts;
        private StreamScheduler.@Nullable Cancellable keepAlive;
        private StreamScheduler.@Nullable Cancellable pendingReconnect;
        private @Nullable CompletableFuture<Void> drained;
        private boolean lostPendingNotification;

        ProviderStream(Context context, Listener listener, URI uri, ProviderConnector connector) {
            this.context = context;
            this.listener = listener;
            this.uri = uri;
            this.connector = connector;
        }

        /** Opens the first connection synchronously; failing here fails {@code open}. */
        void connect() {
            Connection conn;
            synchronized (lock) {
                conn = new Connection(++connectionCount);
            }
            WebSocket socket;
            try {
                socket = connector.connect(uri, conn)
                        .orTimeout(resilience.connectTimeout().toMillis(), TimeUnit.MILLISECONDS)
                        .join();
            } catch (RuntimeException e) {
                connector.close();
                log.error("Failed to open {} streaming session: {}", provider(), rootMessage(e));
                throw DiscoveryInfrastructureExceptions.transcriptionUnavailable();
            }
            runLocked(() -> attach(conn, socket));
        }

        @Override
        public void sendAudio(byte[] frame) {
            runLocked(() -> {
                if (state == State.CLOSING || state == State.CLOSED || state == State.LOST) {
                    return;
                }
                queue.addLast(Outgoing.audio(frame, receivedAudioBytes));
                receivedAudioBytes += frame.length;
                queuedAudioBytes += frame.length;
                trimBuffer();
                pump();
            });
        }

        @Override
        public void close() {
            Connection conn = null;
            CompletableFuture<Void> drainedOrNow = CompletableFuture.completedFuture(null);
            synchronized (lock) {
                switch (state) {
                    case CLOSING, CLOSED -> {
                        return;
                    }
                    case LOST -> {
                        state = State.CLOSED;
                        return;
                    }
                    case OPEN -> {
                        conn = current;
                        state = State.CLOSING;
                        drainedOrNow = drained();
                    }
                    default -> state = State.CLOSED;
                }
                cancelTimers();
            }
            if (conn != null) {
                await(drainedOrNow, DRAIN_TIMEOUT);
                synchronized (lock) {
                    state = State.CLOSED;
                    clearQueue();
                }
                finish(conn);
            } else {
                synchronized (lock) {
                    clearQueue();
                }
            }
            connector.close();
        }

        /** Provider-specific flush, then the close handshake, each step bounded. */
        private void finish(Connection conn) {
            WebSocket socket = conn.socket;
            if (socket == null) {
                return;
            }
            try {
                beforeClose(socket);
                await(socket.sendClose(WebSocket.NORMAL_CLOSURE, "client closed"), CLOSE_TIMEOUT);
                // Let the provider deliver its last results and answer the close before the client goes.
                await(conn.inputClosed, CLOSE_TIMEOUT);
            } catch (RuntimeException e) {
                log.debug("Error closing {} stream: {}", provider(), e.getMessage());
            }
        }

        // ----- connection lifecycle (lock held) -----

        private void attach(Connection conn, WebSocket socket) {
            conn.socket = socket;
            current = conn;
            state = State.OPEN;
            String handshake = handshakeMessage(context);
            if (handshake != null) {
                queue.addFirst(Outgoing.text(handshake));
            }
            startKeepAlive();
            pump();
        }

        private void connectionLost(Connection conn) {
            if (inFlight != null && inFlightOn == conn) {
                requeueFirst(inFlight);
            }
            inFlight = null;
            inFlightOn = null;
            current = null;
            state = State.RECONNECTING;
            cancel(keepAlive);
            keepAlive = null;
            if (conn.healthy) {
                failedAttempts = 0;
            }
            abortQuietly(conn.socket);
            scheduleReconnect();
        }

        private void scheduleReconnect() {
            if (failedAttempts >= resilience.maxReconnectAttempts()) {
                giveUp();
                return;
            }
            int attempt = ++failedAttempts;
            Duration delay = resilience.reconnectBackoff().multipliedBy(1L << (attempt - 1));
            pendingReconnect = resilience.scheduler().schedule(() -> reconnect(attempt), delay);
        }

        private void reconnect(int attempt) {
            Connection conn;
            synchronized (lock) {
                pendingReconnect = null;
                if (state != State.RECONNECTING) {
                    return;
                }
                conn = new Connection(++connectionCount);
            }
            log.debug("Reconnecting {} live stream for session {} (attempt {}/{})",
                    provider(), context.sessionId(), attempt, resilience.maxReconnectAttempts());
            CompletableFuture<WebSocket> connecting;
            try {
                connecting = connector.connect(uri, conn);
            } catch (RuntimeException e) {
                connecting = CompletableFuture.failedFuture(e);
            }
            connecting.orTimeout(resilience.connectTimeout().toMillis(), TimeUnit.MILLISECONDS)
                    .whenComplete((socket, error) -> runLocked(() -> reconnected(conn, attempt, socket, error)));
        }

        private void reconnected(Connection conn, int attempt, @Nullable WebSocket socket, @Nullable Throwable error) {
            if (state != State.RECONNECTING) {
                abortQuietly(socket);
                return;
            }
            if (error != null || socket == null) {
                log.warn("{} reconnect attempt {}/{} for session {} failed: {}", provider(), attempt,
                        resilience.maxReconnectAttempts(), context.sessionId(), rootMessage(error));
                scheduleReconnect();
                return;
            }
            log.info("{} live stream for session {} reconnected (attempt {}/{}{})", provider(), context.sessionId(),
                    attempt, resilience.maxReconnectAttempts(),
                    droppedAudioBytes > 0 ? ", " + droppedAudioBytes / AUDIO_BYTES_PER_MS + " ms of audio dropped" : "");
            droppedAudioBytes = 0;
            attach(conn, socket);
        }

        private void giveUp() {
            state = State.LOST;
            cancelTimers();
            clearQueue();
            lostPendingNotification = true;
            log.error("{} live stream for session {} lost: {} reconnect attempts failed; closing the client channel",
                    provider(), context.sessionId(), resilience.maxReconnectAttempts());
        }

        private void notifyLost() {
            connector.close();
            try {
                listener.onStreamLost("transcription provider unreachable after "
                        + resilience.maxReconnectAttempts() + " reconnect attempts");
            } catch (RuntimeException e) {
                log.warn("Stream-lost callback failed for session {}: {}", context.sessionId(), e.getMessage());
            }
        }

        // ----- provider callbacks -----

        private void providerClosed(Connection conn, int code, String reason) {
            runLocked(() -> {
                if (conn != current || state != State.OPEN) {
                    log.debug("{} connection #{} for session {} closed (code {})",
                            provider(), conn.number, context.sessionId(), code);
                    return;
                }
                log.warn("{} closed the live stream for session {} (code {}{}); reconnecting", provider(),
                        context.sessionId(), code, reason.isBlank() ? "" : ", reason '" + reason + "'");
                connectionLost(conn);
            });
        }

        private void providerFailed(Connection conn, Throwable error) {
            runLocked(() -> {
                if (conn != current || state != State.OPEN) {
                    log.debug("{} connection #{} for session {} failed: {}",
                            provider(), conn.number, context.sessionId(), rootMessage(error));
                    return;
                }
                log.warn("{} live stream error for session {}: {}; reconnecting",
                        provider(), context.sessionId(), rootMessage(error));
                connectionLost(conn);
            });
        }

        // ----- sending (lock held) -----

        /** Sends queued frames one at a time; completions continue the loop (no recursion per frame). */
        private void pump() {
            while (inFlight == null && current != null && (state == State.OPEN || state == State.CLOSING)) {
                Outgoing next = queue.pollFirst();
                if (next == null) {
                    break;
                }
                Connection conn = current;
                if (next.isAudio()) {
                    queuedAudioBytes -= next.audioBytes();
                    if (conn.baseMs < 0) {
                        conn.baseMs = next.position() / AUDIO_BYTES_PER_MS;
                    }
                }
                inFlight = next;
                inFlightOn = conn;
                CompletableFuture<WebSocket> sent = send(conn, next);
                if (!sent.isDone()) {
                    sent.whenComplete((ws, error) -> runLocked(() -> {
                        sendCompleted(conn, next, error);
                        pump();
                    }));
                    return;
                }
                sendCompleted(conn, next, failureOf(sent));
            }
            if (inFlight == null && queue.isEmpty() && drained != null) {
                drained.complete(null);
                drained = null;
            }
        }

        private CompletableFuture<WebSocket> send(Connection conn, Outgoing item) {
            WebSocket socket = conn.socket;
            try {
                return item.isAudio()
                        ? socket.sendBinary(ByteBuffer.wrap(prepareAudioFrame(item.audio())), true)
                        : socket.sendText(item.text(), true);
            } catch (RuntimeException e) {
                return CompletableFuture.failedFuture(e);
            }
        }

        private void sendCompleted(Connection conn, Outgoing item, @Nullable Throwable error) {
            if (item != inFlight || conn != inFlightOn) {
                return; // a send on a connection already replaced; its frame was requeued then
            }
            if (error == null) {
                inFlight = null;
                inFlightOn = null;
                return;
            }
            if (state == State.OPEN && conn == current) {
                log.warn("{} send failed on the live stream for session {}: {}; reconnecting",
                        provider(), context.sessionId(), rootMessage(error));
                connectionLost(conn);
            } else {
                inFlight = null;
                inFlightOn = null;
                log.debug("{} send failed while closing session {}: {}", provider(), context.sessionId(), rootMessage(error));
            }
        }

        private void sendKeepAlive(String message) {
            if (state == State.OPEN && inFlight == null && queue.isEmpty()) {
                queue.addLast(Outgoing.text(message));
                pump();
            }
        }

        private void startKeepAlive() {
            String message = keepAliveMessage();
            if (message == null) {
                return;
            }
            cancel(keepAlive);
            keepAlive = resilience.scheduler().scheduleAtFixedRate(
                    () -> runLocked(() -> sendKeepAlive(message)), resilience.keepAliveInterval());
        }

        // ----- buffer bookkeeping (lock held) -----

        /** Keeps the audio backlog bounded by dropping the oldest audio (text frames are kept). */
        private void trimBuffer() {
            if (queuedAudioBytes <= resilience.maxBufferedAudioBytes()) {
                return;
            }
            boolean firstDrop = droppedAudioBytes == 0;
            Iterator<Outgoing> it = queue.iterator();
            while (queuedAudioBytes > resilience.maxBufferedAudioBytes() && it.hasNext()) {
                Outgoing item = it.next();
                if (item.isAudio()) {
                    it.remove();
                    queuedAudioBytes -= item.audioBytes();
                    droppedAudioBytes += item.audioBytes();
                }
            }
            if (firstDrop) {
                log.warn("{} live stream for session {}: audio backlog exceeds {} ms; dropping the oldest audio",
                        provider(), context.sessionId(), resilience.maxBufferedAudioBytes() / AUDIO_BYTES_PER_MS);
            }
        }

        private void requeueFirst(Outgoing item) {
            if (item.isAudio()) {
                queue.addFirst(item);
                queuedAudioBytes += item.audioBytes();
            }
        }

        private void clearQueue() {
            queue.clear();
            queuedAudioBytes = 0;
            if (drained != null) {
                drained.complete(null);
                drained = null;
            }
        }

        private CompletableFuture<Void> drained() {
            if (inFlight == null && queue.isEmpty()) {
                return CompletableFuture.completedFuture(null);
            }
            if (drained == null) {
                drained = new CompletableFuture<>();
            }
            return drained;
        }

        private void cancelTimers() {
            cancel(keepAlive);
            keepAlive = null;
            cancel(pendingReconnect);
            pendingReconnect = null;
        }

        /** Runs {@code action} under the lock, then reports a lost stream (outside the lock) if it caused one. */
        private void runLocked(Runnable action) {
            boolean notify;
            synchronized (lock) {
                action.run();
                notify = lostPendingNotification;
                lostPendingNotification = false;
            }
            if (notify) {
                notifyLost();
            }
        }

        /** One provider WebSocket of this stream, and the listener of its incoming frames. */
        private final class Connection implements WebSocket.Listener {
            private final int number;
            private final StringBuilder buffer = new StringBuilder();
            private final CompletableFuture<Void> inputClosed = new CompletableFuture<>();
            /** Set (under the stream lock) once connected. */
            private volatile @Nullable WebSocket socket;
            /** Client-stream offset of the first audio sent on this connection; -1 until then. */
            private volatile long baseMs = -1;
            /** Whether this connection delivered a transcript, i.e. the provider accepted the stream. */
            private volatile boolean healthy;

            Connection(int number) {
                this.number = number;
            }

            @Override
            public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
                buffer.append(data);
                if (last) {
                    String message = buffer.toString();
                    buffer.setLength(0);
                    try {
                        parseFrame(message, this::emit);
                    } catch (Exception e) {
                        log.warn("Failed to parse {} frame: {}", provider(), e.getMessage());
                    }
                }
                webSocket.request(1);
                return null;
            }

            @Override
            public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
                inputClosed.complete(null);
                providerClosed(this, statusCode, reason);
                return null;
            }

            @Override
            public void onError(WebSocket webSocket, Throwable error) {
                inputClosed.complete(null);
                providerFailed(this, error);
            }

            /** Forwards a provider transcript on the client-stream timeline (offsets restart per connection). */
            private void emit(TranscriptEvent event) {
                healthy = true;
                long base = Math.max(baseMs, 0);
                listener.onTranscript(base == 0 ? event : new TranscriptEvent(event.text(), event.speaker(),
                        event.startMs() + base, event.endMs() + base, event.isFinal()));
            }
        }
    }

    // ----- helpers -----

    private static void cancel(StreamScheduler.@Nullable Cancellable task) {
        if (task != null) {
            task.cancel();
        }
    }

    private static void abortQuietly(@Nullable WebSocket socket) {
        if (socket != null) {
            try {
                socket.abort();
            } catch (RuntimeException e) {
                log.debug("Ignoring error aborting a provider socket: {}", e.getMessage());
            }
        }
    }

    private static @Nullable Throwable failureOf(CompletableFuture<?> done) {
        return switch (done.state()) {
            case FAILED -> done.exceptionNow();
            case CANCELLED -> new CancellationException();
            default -> null;
        };
    }

    /** Waits for {@code future} up to {@code timeout}, ignoring its outcome; keeps the interrupt flag. */
    private static void await(Future<?> future, Duration timeout) {
        try {
            future.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (ExecutionException | TimeoutException | CancellationException e) {
            log.debug("Stopped waiting on the provider stream: {}", rootMessage(e));
        }
    }

    private static String rootMessage(@Nullable Throwable error) {
        if (error == null) {
            return "no socket";
        }
        Throwable root = error;
        while ((root instanceof CompletionException || root instanceof ExecutionException) && root.getCause() != null) {
            root = root.getCause();
        }
        return root.getMessage() != null ? root.getClass().getSimpleName() + ": " + root.getMessage()
                : root.getClass().getSimpleName();
    }
}
