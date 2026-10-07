package com.kntro.reqsai.discovery.infrastructure.ai.transcription.streaming.strategy;

import com.fasterxml.jackson.databind.JsonNode;
import com.kntro.reqsai.discovery.application.port.StreamingTranscriptionPort;
import com.kntro.reqsai.discovery.infrastructure.exception.DiscoveryInfrastructureExceptions;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;

import java.net.URI;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Live STT via a WhisperLive server (faster-whisper backend). Supports both:
 * <ul>
 *   <li><b>Local</b> — self-hosted instance ({@code WHISPERLIVE_URL=ws://localhost:9090}, no API key).
 *   <li><b>Cloud</b> — a remotely deployed WhisperLive server
 *       ({@code WHISPERLIVE_URL=wss://whisper.example.com}, {@code WHISPERLIVE_API_KEY=...}).
 *       When {@code apiKey} is non-blank, it is sent as {@code Authorization: Bearer <key>}.
 * </ul>
 *
 * <p>WhisperLive sends <em>cumulative</em> {@code segments} arrays: each message re-includes all
 * previously completed segments. Finals are deduplicated per stream by {@code startMs} so the same
 * segment is never persisted twice. The offsets are already on the stream timeline (shifted after a
 * reconnect), so a new connection's segments never collide with the previous connection's.
 *
 * <p>Audio must be float32 (not int16); {@link #prepareAudioFrame} converts automatically.
 */
@Slf4j
public class WhisperLiveStreamingAdapter extends AbstractWebSocketStreamingAdapter {

    private final String url;
    private final String apiKey;
    private final String model;

    public WhisperLiveStreamingAdapter(String url, String apiKey, String model) {
        this(url, apiKey, model, StreamResilience.defaults());
    }

    WhisperLiveStreamingAdapter(String url, String apiKey, String model, StreamResilience resilience) {
        super(resilience);
        this.url = url;
        this.apiKey = apiKey;
        this.model = model;
    }

    /**
     * Wraps the caller's listener with per-session deduplication: a completed segment with the same
     * {@code startMs} is only forwarded once even though WhisperLive re-sends it in every subsequent frame.
     */
    @Override
    public Session open(Context context, Listener listener) {
        return super.open(context, new DeduplicatingListener(listener));
    }

    @Override
    protected String provider() {
        return "whisperlive";
    }

    @Override
    protected void guardConfig() {
        if (url == null || url.isBlank()) {
            log.error("WHISPERLIVE_URL is not set — cannot open a WhisperLive streaming session");
            throw DiscoveryInfrastructureExceptions.transcriptionUnavailable();
        }
    }

    @Override
    protected URI endpoint(Context context) {
        return URI.create(url);
    }

    @Override
    protected void applyHeaders(WebSocket.Builder builder) {
        if (apiKey != null && !apiKey.isBlank()) {
            builder.header("Authorization", "Bearer " + apiKey);
        }
    }

    /** The session config WhisperLive expects as the first message of every connection. */
    @Override
    protected @Nullable String handshakeMessage(Context context) {
        try {
            String language = context.language() != null ? context.language() : "es";
            return JSON.writeValueAsString(Map.of(
                    "uid", context.sessionId().toString(),
                    "language", language,
                    "task", "transcribe",
                    "use_vad", true,
                    "model", model));
        } catch (Exception e) {
            log.warn("Failed to build WhisperLive config handshake: {}", e.getMessage());
            return null;
        }
    }

    /**
     * WhisperLive expects to float32 audio (values in [-1.0, 1.0]), not int16 PCM.
     * Converts each pair of int16 LE bytes to one IEEE 754 float32 LE sample.
     */
    @Override
    protected byte[] prepareAudioFrame(byte[] frame) {
        ByteBuffer in = ByteBuffer.wrap(frame).order(ByteOrder.LITTLE_ENDIAN);
        ByteBuffer out = ByteBuffer.allocate(frame.length * 2).order(ByteOrder.LITTLE_ENDIAN);
        while (in.remaining() >= 2) {
            out.putFloat(in.getShort() / 32768.0f);
        }
        return out.array();
    }

    @Override
    protected void parseFrame(String json, Listener listener) throws Exception {
        JsonNode root = JSON.readTree(json);
        JsonNode segments = root.path("segments");
        if (!segments.isArray()) {
            return; // SERVER_READY / status messages — ignore
        }
        for (JsonNode seg : segments) {
            String text = seg.path("text").asText("").strip();
            if (text.isBlank()) {
                continue;
            }
            long startMs = Math.round(seg.path("start").asDouble(0) * 1000);
            long endMs = Math.round(seg.path("end").asDouble(0) * 1000);
            // WhisperLive omits "completed" for in-progress segments; explicit false = partial.
            boolean isFinal = seg.path("completed").asBoolean(false);
            listener.onTranscript(new TranscriptEvent(text, null, startMs, endMs, isFinal));
        }
    }

    /**
     * Per-stream deduplication: WhisperLive's cumulative protocol re-sends completed segments in
     * every subsequent message. This wrapper forwards each final segment only once (keyed by startMs).
     * Partials are always forwarded so the UI gets live preview updates.
     */
    private static final class DeduplicatingListener implements StreamingTranscriptionPort.Listener {
        private final Listener delegate;
        private final Set<Long> emittedFinalStartMs = ConcurrentHashMap.newKeySet();

        DeduplicatingListener(Listener delegate) {
            this.delegate = delegate;
        }

        @Override
        public void onTranscript(TranscriptEvent event) {
            if (event.isFinal()) {
                if (!emittedFinalStartMs.add(event.startMs())) {
                    return; // already emitted — skip duplicate
                }
            }
            delegate.onTranscript(event);
        }

        @Override
        public void onStreamLost(String reason) {
            delegate.onStreamLost(reason);
        }
    }
}
